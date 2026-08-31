-- =============================================
-- NexusERP - AI 流程编排 P11-D 流程管理: 版本历史 + 模板
-- 目标库: ry-vue
-- 创建时间: 2026-08-11
-- 所属: AI 流程编排系统 P11-D 流程管理
-- 幂等: DROP TABLE IF EXISTS + INSERT IGNORE
-- 内容:
--   1) ai_flow_version 版本快照表（发布时落快照，供版本列表/恢复/只读预览）
--   2) 内置流程模板不入库（Java 静态注册表返回 config_json），无模板 DDL
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 版本快照表（发布时写一行：config_json + 版本号 + 流程名快照） =====
DROP TABLE IF EXISTS `ai_flow_version`;
CREATE TABLE `ai_flow_version`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `flow_id` bigint(20) NOT NULL COMMENT '流程ID',
  `flow_version` int(11) NOT NULL COMMENT '发布后的流程版本号',
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流程名称(发布时快照)',
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '画布配置快照(JSON: nodes+edges)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布说明',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `attr1` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id_version` (`flow_id`, `flow_version`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程版本快照' ROW_FORMAT = DYNAMIC;

-- 验证
-- SELECT id, flow_id, flow_version, flow_name FROM `ai_flow_version` ORDER BY create_time DESC;
