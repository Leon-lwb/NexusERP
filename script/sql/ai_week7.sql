-- =====================================================
-- NexusERP AI 第七周 SQL 变更脚本
-- 包含：ai_prompt_template 表 + Prompt 模板管理菜单
-- 执行顺序：先建表，再插菜单
-- 安全措施：IF NOT EXISTS / INSERT IGNORE 防重复执行
-- =====================================================

-- 1. 创建 ai_prompt_template 表
CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `tenant_id`       BIGINT       DEFAULT 0 COMMENT '租户编号',
    `template_name`   VARCHAR(128) NOT NULL COMMENT '模板名称',
    `template_key`    VARCHAR(64)  NOT NULL COMMENT '模板标识（如 erp_query_agent/erp_report_agent/supervisor）',
    `content`         TEXT         NOT NULL COMMENT '模板内容（支持 {{variable}} 占位符）',
    `description`     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `is_default`      CHAR(1)      DEFAULT '0' COMMENT '是否默认模板（1是 0否）',
    `version`         INT          DEFAULT 1 COMMENT '版本号',
    `status`          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    `create_dept`     BIGINT       DEFAULT NULL COMMENT '创建部门',
    `create_by`       BIGINT       DEFAULT NULL COMMENT '创建者',
    `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`       BIGINT       DEFAULT NULL COMMENT '更新者',
    `update_time`     DATETIME     DEFAULT NULL COMMENT '更新时间',
    `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_key` (`template_key`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt 模板';

-- 2. 新增 Prompt 模板管理菜单
-- menu_id 从 1760 开始，现有 AI 菜单最大为 1753（工具删除），不会冲突
-- 使用 INSERT IGNORE 防止重复执行时主键冲突

-- 页面菜单
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1760, 'Prompt模板', 1700, 6, 'prompt', 'ai/prompt/index', NULL, 1, 0, 'C', '0', '0', 'ai:prompt:list', 'documentation', NULL, 1, NOW(), NULL, NULL, 'Prompt模板管理菜单');

-- 按钮权限
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1761, '模板查询', 1760, 1, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:prompt:query', '#', NULL, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1762, '模板新增', 1760, 2, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:prompt:add', '#', NULL, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1763, '模板修改', 1760, 3, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:prompt:edit', '#', NULL, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1764, '模板删除', 1760, 4, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:prompt:remove', '#', NULL, 1, NOW(), NULL, NULL, '');
