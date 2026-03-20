-- =============================================
-- IM模块数据库表结构
-- 适用于MySQL 8.0+
-- 移植自 nbmade 项目
-- =============================================

-- =============================================
-- 第一部分：sys_user 表扩展IM字段
-- =============================================

-- 为 sys_user 表添加IM相关字段
-- 使用存储过程来检查字段是否存在，避免重复添加

DELIMITER $$

CREATE PROCEDURE AddImColumnsIfNotExists()
BEGIN
    -- 检查并添加 im_status 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_status') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_status` varchar(20) DEFAULT 'offline' COMMENT 'IM在线状态：online, offline, busy, away';
    END IF;
    
    -- 检查并添加 im_last_online_time 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_last_online_time') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_last_online_time` datetime DEFAULT NULL COMMENT 'IM最后在线时间';
    END IF;
    
    -- 检查并添加 im_signature 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_signature') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_signature` varchar(255) DEFAULT NULL COMMENT 'IM个性签名';
    END IF;
    
    -- 检查并添加 im_gender 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_gender') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_gender` varchar(10) DEFAULT 'unknown' COMMENT 'IM性别：male, female, unknown';
    END IF;
    
    -- 检查并添加 im_birthday 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_birthday') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_birthday` date DEFAULT NULL COMMENT 'IM生日';
    END IF;
    
    -- 检查并添加 im_region 字段
    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'im_region') THEN
        ALTER TABLE `sys_user` ADD COLUMN `im_region` varchar(100) DEFAULT NULL COMMENT 'IM地区';
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL AddImColumnsIfNotExists();

-- 删除存储过程
DROP PROCEDURE IF EXISTS AddImColumnsIfNotExists;

-- 为 sys_user 表添加IM相关索引（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND INDEX_NAME = 'idx_sys_user_im_status') = 0,
    'CREATE INDEX `idx_sys_user_im_status` ON `sys_user` (`im_status`)',
    'SELECT "Index idx_sys_user_im_status already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并创建 im_last_online_time 索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND INDEX_NAME = 'idx_sys_user_im_last_online') = 0,
    'CREATE INDEX `idx_sys_user_im_last_online` ON `sys_user` (`im_last_online_time`)',
    'SELECT "Index idx_sys_user_im_last_online already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================
-- 第二部分：IM核心表创建
-- =============================================

-- IM消息表
CREATE TABLE IF NOT EXISTS `im_message` (
    `message_id` varchar(64) NOT NULL COMMENT '消息ID',
    `from_user_id` bigint(20) NOT NULL COMMENT '发送者用户ID',
    `to_user_id` bigint(20) DEFAULT NULL COMMENT '接收者用户ID（私聊）',
    `group_id` varchar(64) DEFAULT NULL COMMENT '群组ID（群聊）',
    `content` text NOT NULL COMMENT '消息内容',
    `message_type` varchar(20) DEFAULT 'text' COMMENT '消息类型：text, image, file, voice, video, system',
    `status` varchar(20) DEFAULT 'sent' COMMENT '消息状态：sending, sent, delivered, read, failed',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_read` tinyint(1) DEFAULT 0 COMMENT '是否已读',
    `extra` text DEFAULT NULL COMMENT '扩展数据',
    `source` varchar(20) DEFAULT 'user' COMMENT '消息来源：user, system, bot',
    `is_revoked` tinyint(1) DEFAULT 0 COMMENT '是否撤回',
    `revoke_time` datetime DEFAULT NULL COMMENT '撤回时间',
    PRIMARY KEY (`message_id`),
    KEY `idx_from_user` (`from_user_id`),
    KEY `idx_to_user` (`to_user_id`),
    KEY `idx_group` (`group_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM消息表';

-- IM群组表
CREATE TABLE IF NOT EXISTS `im_group` (
    `group_id` varchar(64) NOT NULL COMMENT '群组ID',
    `group_name` varchar(100) NOT NULL COMMENT '群组名称',
    `avatar` varchar(255) DEFAULT NULL COMMENT '群组头像',
    `description` varchar(500) DEFAULT NULL COMMENT '群组描述',
    `owner_id` bigint(20) NOT NULL COMMENT '群主用户ID',
    `group_type` varchar(20) DEFAULT 'normal' COMMENT '群组类型：normal, super',
    `max_members` int(11) DEFAULT 500 COMMENT '最大成员数',
    `member_count` int(11) DEFAULT 0 COMMENT '当前成员数',
    `status` varchar(20) DEFAULT 'active' COMMENT '群组状态：active, inactive, disbanded',
    `allow_invite` tinyint(1) DEFAULT 1 COMMENT '是否允许邀请',
    `allow_member_modify` tinyint(1) DEFAULT 0 COMMENT '是否允许群成员修改群信息',
    `allow_member_invite` tinyint(1) DEFAULT 1 COMMENT '是否允许群成员邀请',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`group_id`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_group_type` (`group_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM群组表';

-- IM群组成员表
CREATE TABLE IF NOT EXISTS `im_group_member` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id` varchar(64) NOT NULL COMMENT '群组ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `group_nickname` varchar(50) DEFAULT NULL COMMENT '用户在群组中的昵称',
    `role` varchar(20) DEFAULT 'member' COMMENT '群组角色：owner, admin, member',
    `join_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `last_speak_time` datetime DEFAULT NULL COMMENT '最后发言时间',
    `is_muted` tinyint(1) DEFAULT 0 COMMENT '是否被禁言',
    `mute_end_time` datetime DEFAULT NULL COMMENT '禁言结束时间',
    `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_group` (`group_id`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM群组成员表';

-- IM好友表
CREATE TABLE IF NOT EXISTS `im_friend` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `friend_user_id` bigint(20) NOT NULL COMMENT '好友用户ID',
    `remark` varchar(50) DEFAULT NULL COMMENT '好友备注',
    `group_name` varchar(50) DEFAULT '我的好友' COMMENT '好友分组',
    `add_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `status` varchar(20) DEFAULT 'accepted' COMMENT '好友状态：pending, accepted, blocked, deleted',
    `is_top` tinyint(1) DEFAULT 0 COMMENT '是否置顶',
    `is_muted` tinyint(1) DEFAULT 0 COMMENT '是否免打扰',
    `last_chat_time` datetime DEFAULT NULL COMMENT '最后聊天时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_user_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_friend` (`friend_user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM好友表';

-- =============================================
-- 第三部分：性能优化索引
-- =============================================

-- 消息表优化索引
CREATE INDEX IF NOT EXISTS `idx_message_private` ON `im_message` (`from_user_id`, `to_user_id`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_message_group` ON `im_message` (`group_id`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_message_unread` ON `im_message` (`to_user_id`, `is_read`, `create_time`);

-- 群组成员表优化索引
CREATE INDEX IF NOT EXISTS `idx_group_member_user` ON `im_group_member` (`user_id`, `enabled`);

-- 好友表优化索引
CREATE INDEX IF NOT EXISTS `idx_friend_user` ON `im_friend` (`user_id`, `status`);

-- =============================================
-- 第四部分：菜单数据初始化
-- =============================================

-- 插入IM菜单
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('即时通讯', 0, 6, 'im', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'message', 'admin', NOW(), '', NULL, '即时通讯目录');

-- 获取刚插入的菜单ID
SET @im_menu_id = LAST_INSERT_ID();

-- 聊天菜单
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('聊天', @im_menu_id, 1, 'chat', 'im/chat/index', NULL, '', 1, 0, 'C', '0', '0', 'im:chat:list', 'chat-dot-round', 'admin', NOW(), '', NULL, '聊天菜单');

SET @chat_menu_id = LAST_INSERT_ID();

-- 通讯录菜单
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('通讯录', @im_menu_id, 2, 'contact', 'im/contact/index', NULL, '', 1, 0, 'C', '0', '0', 'im:contact:list', 'user', 'admin', NOW(), '', NULL, '通讯录菜单');

SET @contact_menu_id = LAST_INSERT_ID();

-- 群组菜单
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('群组', @im_menu_id, 3, 'group', 'im/group/index', NULL, '', 1, 0, 'C', '0', '0', 'im:group:list', 'peoples', 'admin', NOW(), '', NULL, '群组菜单');

SET @group_menu_id = LAST_INSERT_ID();

-- 聊天按钮权限
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('发送消息', @chat_menu_id, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:message:send', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('查看历史', @chat_menu_id, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:message:history', '#', 'admin', NOW(), '', NULL, '');

-- 好友按钮权限
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('添加好友', @contact_menu_id, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:friend:add', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('删除好友', @contact_menu_id, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:friend:remove', '#', 'admin', NOW(), '', NULL, '');

-- 群组按钮权限
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('创建群组', @group_menu_id, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:group:add', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('解散群组', @group_menu_id, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:group:remove', '#', 'admin', NOW(), '', NULL, '');

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) 
VALUES ('邀请成员', @group_menu_id, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'im:group:invite', '#', 'admin', NOW(), '', NULL, '');
