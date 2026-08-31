-- ============================================================
-- NexusERP AI 二阶段 S2-C 扩展：文生图独立菜单页
-- 独立文生图入口 + 持久化历史记录
-- ============================================================

-- 1. 文生图历史记录表
CREATE TABLE IF NOT EXISTS `ai_image_record` (
    `id`            bigint(20)   NOT NULL COMMENT '主键',
    `tenant_id`     varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `user_id`       bigint(20)   NOT NULL COMMENT '生成用户ID',
    `prompt`        text         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词',
    `image_url`     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片URL（OSS）',
    `size`          varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1280*1280' COMMENT '尺寸',
    `model_id`      bigint(20)   NULL DEFAULT NULL COMMENT '使用的模型ID',
    `model_name`    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型名称',
    `create_dept`   bigint(20)   NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`     bigint(20)   NULL DEFAULT NULL COMMENT '创建者',
    `create_time`   datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`     bigint(20)   NULL DEFAULT NULL COMMENT '更新者',
    `update_time`   datetime     NULL DEFAULT NULL COMMENT '更新时间',
    `del_flag`      char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id`(`user_id`) USING BTREE,
    INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 文生图记录' ROW_FORMAT = DYNAMIC;

-- 2. 菜单（parent=1700 AI 智能助手目录，menu_id 1810 段未被占用）
INSERT INTO `sys_menu` VALUES (1810, '文生图', 1700, 11, 'imageGen', 'ai/imageGen/index', NULL, 1, 0, 'C', '0', '0', 'ai:imageGen:list', 'picture', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, 'AI 文生图菜单');
INSERT INTO `sys_menu` VALUES (1811, '图片生成', 1810, 1, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:image:generate', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1812, '历史查询', 1810, 2, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:image:list', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1813, '历史删除', 1810, 3, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:image:remove', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '');

-- ============================================================
-- 说明（无需 DDL，仅文档）：
-- 1. 文生图依赖 ai_model.model_type=IMAGE 的默认模型（通义万相 + dashscope API Key）。
--    使用前需在「模型管理」页配置一条 modelType=IMAGE 的模型并设为默认。
-- 2. 图片持久化到 OSS 由 TongYiWanxImageService 处理；OSS 失败时兜底返回通义万相临时 URL（可能有时效）。
-- 3. 原智能对话页的图片关键词自动路由已移除，文生图功能全部收敛到本菜单页。
-- ============================================================
