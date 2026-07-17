-- ============================================================
-- P1-5: @提及能力 mention_user_ids
-- ============================================================
-- 依据: doc/im/2026-07-18-im-p1-development-plan.md P1-5
-- 日期: 2026-07-18
-- 影响: ImMessage 实体新增 mentionUserIds
--       ImGroupSendBo 新增 mentionUserIds
--       ImChatServiceImpl.sendGroup 校验 mention 用户均为群成员
--       推送 payload 携带 mentionUserIds
-- ============================================================
--
-- 背景:
--   群聊场景下用户需要 @ 特定成员，被 @ 的成员应收到特殊提示。
--   通过 im_message.mention_user_ids 字段存储被 @ 的用户 ID 列表
--   （JSON 数组字符串，如 "[101,102,103]"），推送时携带该字段，
--   客户端据此显示 "@你" 提示与高亮。
--
-- 设计选择:
--   - 用 varchar(500) + JSON 数组字符串：兼容现有 IM 消息表设计风格
--     （content/text 字段已为 JSON 字符串），无需引入额外表
--   - 500 字符约可容纳 50 个 9 位用户 ID（含逗号），足够覆盖常见群规模
--   - 不为 mention_user_ids 单独建索引：
--     ① 查询场景为推送时读取，无 WHERE mention_user_ids=? 检索
--     ② 若未来需要"我被 @ 的消息列表"查询，可再加 GIN 索引或独立关系表
-- ============================================================

ALTER TABLE `im_message`
    ADD COLUMN `mention_user_ids` varchar(500) DEFAULT NULL COMMENT '被@的用户ID列表（JSON数组字符串）' AFTER `client_msg_id`;


-- ============================================================
-- 验证方法（在测试库执行）
-- ============================================================
-- 1. 字段已添加
--    SHOW COLUMNS FROM im_message LIKE 'mention_user_ids';
--    期望: Field='mention_user_ids', Type='varchar(500)', Null='YES'
--
-- 2. 写入与读取
--    INSERT INTO im_message(message_id, tenant_id, from_user_id, group_id,
--                           content, msg_type, is_read, revoked, mention_user_ids)
--    VALUES (1, '000000', 1, 100, 'hello @102', 'text', '0', '0', '[102]');
--    SELECT mention_user_ids FROM im_message WHERE message_id = 1;
--    期望: mention_user_ids='[102]'
-- ============================================================
