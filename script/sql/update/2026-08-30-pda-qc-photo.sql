-- M3 批次3: 质量三检 PDA 化 — 检验单拍照附件字段
-- PDA 检验提交时可附现场照片: /resource/oss/upload 上传后以 ossId 串(逗号分隔)随检验结果落库。
-- 目标库: mes 库 (qc_* 表所在库, 即 ry-mes)。

ALTER TABLE qc_iqc ADD COLUMN photo_oss_ids VARCHAR(1000) NULL COMMENT '检验照片附件ossId串(逗号分隔)';
ALTER TABLE qc_ipqc ADD COLUMN photo_oss_ids VARCHAR(1000) NULL COMMENT '检验照片附件ossId串(逗号分隔)';
ALTER TABLE qc_oqc ADD COLUMN photo_oss_ids VARCHAR(1000) NULL COMMENT '检验照片附件ossId串(逗号分隔)';
