-- ----------------------------
-- 视觉识别接入 Phase 1a:sch_attendance 人脸验证字段
-- 配套方案: doc/erp/specs/2026-08-02-vision-integration-impl-spec.md
-- 执行方式: 与 script/sql/update/ 下既有增量脚本一致,手工执行
-- ----------------------------

ALTER TABLE `sch_attendance`
    ADD COLUMN `face_verify_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人脸验证状态: NONE未验证, PASS通过, FAIL失败' AFTER `device_name`,
    ADD COLUMN `face_verify_time` datetime NULL DEFAULT NULL COMMENT '人脸验证时间' AFTER `face_verify_status`,
    ADD COLUMN `face_similarity` decimal(5, 4) NULL DEFAULT NULL COMMENT '人脸相似度' AFTER `face_verify_time`,
    ADD COLUMN `face_image_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '打卡人脸照片ossId(MinIO存证)' AFTER `face_similarity`;
