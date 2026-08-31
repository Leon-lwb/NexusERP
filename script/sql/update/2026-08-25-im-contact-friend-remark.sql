-- =============================================================
-- 2026-08-25 IM 通讯录优化：好友备注字段映射
-- 影响: im_friend 表 / ImFriend 实体
-- 背景: im_friend 表建表时已含 remark varchar(500) 列（BaseEntity 审计列），
--       但 Java 实体未映射，好友备注功能无法读写。
--       本脚本为已按旧结构建表的环境补齐该列；若列已存在会报重复列错误，可忽略。
-- =============================================================

ALTER TABLE `im_friend`
    ADD COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注' AFTER `update_time`;
