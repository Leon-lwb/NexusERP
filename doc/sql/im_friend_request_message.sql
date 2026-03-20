-- ========================================
-- IM好友申请功能 - 数据库脚本
-- 采用纯消息式方案
-- ========================================

-- 1. 修改 im_message 表，添加扩展数据字段
ALTER TABLE `im_message` 
ADD COLUMN `extra_data` TEXT DEFAULT NULL COMMENT '扩展数据（JSON格式，用于存储好友申请等特殊消息的附加信息）' AFTER `extra`;

-- 2. 添加索引优化查询性能
ALTER TABLE `im_message` 
ADD INDEX `idx_message_type` (`message_type`),
ADD INDEX `idx_status` (`status`);

-- 3. 更新消息类型说明
-- messageType 新增类型：
-- - friend_request: 好友申请消息
-- - friend_request_accepted: 好友申请已同意通知
-- - friend_request_rejected: 好友申请已拒绝通知
-- - system: 系统消息

-- 4. status 字段用于好友申请消息的状态：
-- - pending: 待处理
-- - accepted: 已同意
-- - rejected: 已拒绝

-- 5. extra_data 字段格式示例（好友申请消息）
-- {
--   "fromUserInfo": {
--     "userId": 1001,
--     "userName": "zhangsan",
--     "nickName": "张三",
--     "avatar": "http://..."
--   },
--   "toUserInfo": {
--     "userId": 1002,
--     "userName": "lisi",
--     "nickName": "李四",
--     "avatar": "http://..."
--   },
--   "requestMessage": "你好，我是张三，想添加你为好友",
--   "requestTime": "2024-01-01 12:00:00",
--   "handleTime": null,
--   "remark": "同事",
--   "groupName": "我的好友"
-- }

-- 6. 查询好友申请消息示例
-- SELECT * FROM im_message 
-- WHERE message_type = 'friend_request' 
-- AND to_user_id = #{userId}
-- ORDER BY create_time DESC;

-- 7. 统计未处理的好友申请数量
-- SELECT COUNT(*) FROM im_message 
-- WHERE message_type = 'friend_request' 
-- AND to_user_id = #{userId}
-- AND status = 'pending';
