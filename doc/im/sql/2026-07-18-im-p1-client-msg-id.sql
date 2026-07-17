-- ============================================================
-- P1-4: 消息重发幂等 client_msg_id
-- ============================================================
-- 依据: doc/im/2026-07-18-im-p1-development-plan.md P1-4
-- 日期: 2026-07-18
-- 影响: ImMessage 实体新增 clientMsgId
--       ImPrivateSendBo / ImGroupSendBo 新增 clientMsgId
--       ImChatServiceImpl.sendPrivate / sendGroup 幂等检查
--       Nexus_App sendPrivateMessage / sendGroupMessage / retryMessage
-- ============================================================
--
-- 背景:
--   移动端网络抖动或重发场景下，同一消息可能被提交多次，
--   后端会插入多条 im_message 记录，造成接收方看到重复消息。
--   通过客户端生成 UUID（client_msg_id）+ 后端唯一索引实现幂等：
--     1. 客户端每次发送生成新 UUID；重发复用同一 UUID
--     2. 后端 sendPrivate/sendGroup 入口先按 client_msg_id 查询
--     3. 命中则直接返回旧 messageId，不插入新记录、不重复推送
--
-- 唯一索引列顺序说明 (tenant_id, client_msg_id):
--   - tenant_id 在前：与多租户拦截器一致，便于按租户分区
--   - client_msg_id 次之：UUID 全局唯一，选择性最高
--   - 仅当 client_msg_id 非 NULL 时才参与唯一约束
--     （MySQL NULL 不参与唯一性比较，多行 NULL 允许共存）
-- ============================================================

ALTER TABLE `im_message`
    ADD COLUMN `client_msg_id` varchar(64) DEFAULT NULL COMMENT '客户端消息ID（重发幂等）' AFTER `forward_from`;

ALTER TABLE `im_message`
    ADD UNIQUE INDEX `uk_client_msg_id` (`tenant_id`, `client_msg_id`) USING BTREE;


-- ============================================================
-- 验证方法（在测试库执行）
-- ============================================================
-- 1. 字段已添加
--    SHOW COLUMNS FROM im_message LIKE 'client_msg_id';
--    期望: Field='client_msg_id', Type='varchar(64)', Null='YES'
--
-- 2. 唯一索引已建立
--    SHOW INDEX FROM im_message WHERE Key_name='uk_client_msg_id';
--    期望: 2 行（tenant_id, client_msg_id）
--
-- 3. 幂等行为验证
--    INSERT INTO im_message(message_id, tenant_id, from_user_id, to_user_id,
--                           content, msg_type, is_read, revoked, client_msg_id)
--    VALUES (1, '000000', 1, 2, 'hi', 'text', '0', '0', 'uuid-001');
--    -- 成功
--
--    INSERT INTO im_message(message_id, tenant_id, from_user_id, to_user_id,
--                           content, msg_type, is_read, revoked, client_msg_id)
--    VALUES (2, '000000', 1, 2, 'hi', 'text', '0', '0', 'uuid-001');
--    -- 失败：Duplicate entry '000000-uuid-001' for key 'uk_client_msg_id'
--
-- 4. NULL 不冲突
--    INSERT INTO im_message(message_id, tenant_id, from_user_id, to_user_id,
--                           content, msg_type, is_read, revoked, client_msg_id)
--    VALUES (3, '000000', 1, 2, 'hi', 'text', '0', '0', NULL);
--    INSERT INTO im_message(message_id, tenant_id, from_user_id, to_user_id,
--                           content, msg_type, is_read, revoked, client_msg_id)
--    VALUES (4, '000000', 1, 2, 'hi', 'text', '0', '0', NULL);
--    -- 两条都成功（NULL 不参与唯一性比较）
-- ============================================================
