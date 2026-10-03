-- 仅ERP库，经目标身份/适用基线/备份审查后执行；本轮未执行。
-- 永久出生登记只能来自真实新检验创建入口；不INSERT SELECT回填历史，不从当前单位认证旧IQC。
CREATE TABLE wm_receipt_iqc_birth (
  birth_id bigint NOT NULL COMMENT 'ERP服务器生成的永久出生身份',
  tenant_id varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实租户',
  receive_id bigint NOT NULL COMMENT '真实收货头',
  material_id bigint NOT NULL COMMENT '真实物料范围',
  base_uom_id bigint NOT NULL COMMENT '创建新检验前冻结的真实原数量单位',
  quantity_scale tinyint unsigned NOT NULL COMMENT '出生时已核原单位精度',
  quantity decimal(14,4) NOT NULL COMMENT '出生时完整同物料原生收货量',
  receipt_scope_sha256 char(64) NOT NULL COMMENT '出生时真实头和全部行范围摘要',
  birth_sha256 char(64) NOT NULL COMMENT '全部登记事实完整性摘要，不是DTO授权',
  captured_at bigint NOT NULL COMMENT '实际ERP捕获时点毫秒，非客户端时间',
  receive_code varchar(64) NOT NULL COMMENT '真实收货编号',
  material_code varchar(64) NOT NULL COMMENT '真实物料编码',
  material_name varchar(255) NOT NULL COMMENT '真实物料名称',
  vendor_id bigint NOT NULL COMMENT '真实供应商身份',
  vendor_name varchar(255) NOT NULL COMMENT '真实供应商名称',
  PRIMARY KEY(birth_id),
  UNIQUE KEY uk_iqc_birth_scope(tenant_id,receive_id,material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='永久IQC创建时单位与范围准备事实';

-- Nullable只保留历史兼容；旧V1或空出生引用不升级为V2，更不具放行授权。
ALTER TABLE wm_certified_iqc_evidence
  ADD COLUMN birth_id bigint NULL COMMENT '真实新建IQC的ERP永久出生登记，历史NULL不认证',
  ADD COLUMN birth_sha256 char(64) NULL COMMENT '原生出生登记摘要，历史NULL不授权';

-- 仅同步兼容字段说明；类型/可空性/字符集保持原合同，不更改任何历史行或认证状态。
ALTER TABLE wm_certified_iqc_evidence
  MODIFY COLUMN base_uom_id bigint(20) NOT NULL COMMENT 'V1观察单位不授权；V2仅取实际创建时原生出生单位',
  MODIFY COLUMN source_kind varchar(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'V1无原单位证明；V2须重读原生出生登记与绑定';
