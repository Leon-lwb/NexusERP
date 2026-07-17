-- ============================================================
-- P0-4: IM 消息已读/未读查询性能优化
-- ============================================================
-- 依据: doc/im/2026-07-17-vim-vs-nexus-im-analysis.md M2
-- 任务: doc/im/2026-07-17-im-p0-stability-fix-plan.md P0-4
-- 日期: 2026-07-17
-- 影响: ImChatServiceImpl.markPrivateRead / markGroupRead
--       / countUnreadPrivate / countUnreadGroup
--       AppImController.unreadMessages / unreadCount
-- ============================================================
--
-- 背景:
--   im_message 现有 3 个索引（script/sql/ry-vue.sql#L4113-L4116）均不含 is_read：
--     PRIMARY KEY (message_id)
--     idx_im_msg_private (tenant_id, from_user_id, to_user_id, create_time)
--     idx_im_msg_to       (tenant_id, to_user_id, create_time)
--     idx_im_msg_group    (tenant_id, group_id, create_time)
--   高频"已读/未读"查询因 is_read 不在任何索引中而退化为全表扫描 +
--   is_read 行过滤，随着消息量增长会显著拖慢会话列表与未读数接口。
--
-- 改造原则:
--   1. 不删除既有索引（避免影响 history 等其他查询）
--   2. 列顺序遵循"等值条件优先 + 高选择性优先"原则
--   3. is_read 放在 from_user_id 之前，让无 from_user_id 条件的查询
--      (unreadMessages/unreadCount) 也能用到 (tenant_id, to_user_id, is_read) 前缀
-- ============================================================


-- ------------------------------------------------------------
-- 索引 1: 私聊未读消息查询
-- ------------------------------------------------------------
-- 覆盖查询:
--   ① markPrivateRead (ImChatServiceImpl#L330-L340):
--        UPDATE ... WHERE tenant_id=? AND to_user_id=? AND from_user_id=? AND is_read='0' AND group_id IS NULL
--   ② countUnreadPrivate (ImChatServiceImpl#L468-L474):
--        SELECT count(*) WHERE tenant_id=? AND to_user_id=? AND from_user_id=? AND is_read='0' AND group_id IS NULL
--   ③ unreadMessages (AppImController#L548-L555):
--        SELECT * WHERE tenant_id=? AND to_user_id=? AND is_read='0' AND group_id IS NULL ORDER BY create_time DESC
--   ④ unreadCount (AppImController#L535-L542):
--        SELECT count(*) WHERE tenant_id=? AND to_user_id=? AND is_read='0' AND group_id IS NULL
--
-- 列顺序说明 (tenant_id, to_user_id, is_read, from_user_id, create_time):
--   - tenant_id 在前：多租户拦截器强制注入，所有查询必带
--   - to_user_id 次之：高选择性等值条件，所有 4 个查询都带
--   - is_read 第三：仅 2 个枚举值，但查询 ③④ 不带 from_user_id，
--                   is_read 在前能让 ③④ 用到 3 列前缀；查询 ①② 带全 4 列等值，列顺序不影响
--   - from_user_id 第四：查询 ①② 等值匹配；查询 ③④ 不使用，作为索引末尾列不影响前缀匹配
--   - create_time 末尾：查询 ③ 的 ORDER BY create_time DESC 可走索引反向扫描避免 filesort
-- ------------------------------------------------------------
ALTER TABLE `im_message`
    ADD INDEX `idx_private_unread` (`tenant_id`, `to_user_id`, `is_read`, `from_user_id`, `create_time`) USING BTREE;


-- ------------------------------------------------------------
-- 索引 2: 群聊未读消息查询
-- ------------------------------------------------------------
-- 覆盖查询:
--   ① markGroupRead (ImChatServiceImpl#L344-L356):
--        UPDATE ... WHERE tenant_id=? AND group_id=? AND from_user_id != ? AND is_read='0'
--   ② countUnreadGroup (ImChatServiceImpl#L461-L466):
--        SELECT count(*) WHERE tenant_id=? AND group_id=? AND from_user_id != ? AND is_read='0'
--
-- 列顺序说明 (tenant_id, group_id, is_read):
--   - from_user_id 是不等值条件 (!=)，无法纳入索引等值匹配，作为后置行过滤
--   - 群成员规模通常 10-100，过滤成本可接受
-- ------------------------------------------------------------
ALTER TABLE `im_message`
    ADD INDEX `idx_group_unread` (`tenant_id`, `group_id`, `is_read`) USING BTREE;


-- ============================================================
-- 验证方法（在测试库执行）
-- ============================================================
-- 1. 私聊未读数查询命中 idx_private_unread
--    EXPLAIN SELECT count(*) FROM im_message
--      WHERE tenant_id='000000' AND to_user_id=1
--        AND from_user_id=3 AND is_read='0' AND group_id IS NULL;
--    期望: key = idx_private_unread, key_len 覆盖前 4 列
--
-- 2. 私聊未读列表查询命中 idx_private_unread（无 from_user_id）
--    EXPLAIN SELECT * FROM im_message
--      WHERE tenant_id='000000' AND to_user_id=1
--        AND is_read='0' AND group_id IS NULL
--      ORDER BY create_time DESC;
--    期望: key = idx_private_unread, key_len 覆盖前 3 列, Extra 无 filesort
--
-- 3. 群聊未读数查询命中 idx_group_unread
--    EXPLAIN SELECT count(*) FROM im_message
--      WHERE tenant_id='000000' AND group_id=2052257328328011777
--        AND from_user_id != 1 AND is_read='0';
--    期望: key = idx_group_unread, key_len 覆盖全部 3 列
-- ============================================================
