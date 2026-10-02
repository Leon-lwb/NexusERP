-- 2026-10-02 库存精度候选与历史隔离合同（ry-erp；MySQL 5.7；一次性、不可整包重放）
-- 状态：仅仓库方案，未执行。此DDL不授权切换数量读取、不认证任何库存、不启停服务。
-- 日期是批准规划基线，不是执行时间；未来实际执行须另记主机实测时区时间。
-- 新装收货行quantity_received与上架明细quantity均为DECIMAL(18,4)；存量旧float不改。
-- 本包受控新producer未接线：现有实际Executor仍写无来源行的TARGET_IN/VIRTUAL_OUT，
-- 虚拟余额兜底不能升级为原始收货证据；本结构/候选列不授权，A须读完整原链及原生类型。
-- 先决：人工确认目标ERP库及租户范围，独立只读审计历史单位/来源业务/流水/预留所有权，
-- 保存库外私有备份并验证恢复；核对旧表/原值/索引、新增列及新表均不存在。
-- MySQL DDL不可视作整包事务；部分失败保留现场，不重放、不自动删除已加列。
-- 拒绝方案A：直接把DOUBLE MODIFY成DECIMAL或CAST旧值，无法恢复已丢失的历史精度。
-- 拒绝方案B：按当前物料单位/单位版本回填历史或贴verified，无法证明发生时单位。
-- 采用方案C：只ADD未知合同和NULL精确候选，旧DOUBLE及DECIMAL(12,2)事实完全不变。
-- UNPROVEN逻辑隔离：保留历史库存/流水/成本/单据/版本；不DELETE、不填零、不猜单位。
-- 新装原数量列已为DECIMAL(18,4)；此存量脚本故意不改原数量列，因此仍BLOCKED。
-- 即使人工把候选列填满/状态改NATIVE_V1，也不能凭状态或摘要取得业务认证。
-- 只读脚本：doc/ai/full-flow/scripts/inventory_certification_audit.py；无自动修复SQL。
--
-- 主线程接线合同（超出本DDL实施范围，未实现前必须继续拒绝）：
-- 1. 专用新写服务以物料guard+库存CAS持有ERP物理事务，重读单位/仓储/全部历史链。
-- 2. 入库/出库业务行、库存精确量、连续quantity_version、末端tx及原请求键同事务；
--    新装原精确量与候选量必须等值，不能由旧通用CRUD单独写一侧而仍维持认证。
-- 3. RESERVE/RELEASE/CONSUME事件与所有者业务状态及库存预留同事务；同stock的
--    reservation_version连续，租户+原请求幂等；CONSUME必须链接真实出库tx。
--    SO_LINE须核销售头/行；WO_MRP_DETAIL须核工单/MRP头/明细完整父链及净需求。
-- 4. 历史独立证据不足：任何余额（含0）、旧事务或所有者不能证明则整个物料BLOCKED，
--    不丢掉历史行后只合计新库存。旧事实无证时保持UNPROVEN并仅允许历史只读。
-- 5. 可证明的历史须另行批准逐行重建（原始业务量/发生时单位/完整流水/预留所有者），
--    保存私有审计证据并独立对账；不是从浮点原值填候选，也不声称恢复早年丢失精度。
-- 6. 原生精度切换为独立后续迁移：用户确认无活动写者、恢复演练、全租户来源完整与
--    verifier+新写服务全入口门禁后审查。此脚本不包含切换/回填/猜测/关闭拒绝的SQL。
--    不能用采购入口已拒绝代替仓库其他旧写入口已封口；旧写入口接线由主线程协调。

USE `ry-erp`;

ALTER TABLE `wm_material_stock`
  ADD COLUMN `stock_quantity_exact` decimal(18, 4) NULL DEFAULT NULL COMMENT '精确候选在库数量;不得由旧DOUBLE自动转换',
  ADD COLUMN `reserved_quantity_exact` decimal(18, 4) NULL DEFAULT NULL COMMENT '精确候选预留数量;缺失不是零',
  ADD COLUMN `quantity_contract_version` int(11) NULL DEFAULT NULL COMMENT '数量来源合同版本;缺失不认证',
  ADD COLUMN `quantity_origin` varchar(32) NOT NULL DEFAULT 'UNPROVEN' COMMENT 'UNPROVEN/NATIVE_V1/RECONCILED_V1;状态不是认证证据',
  ADD COLUMN `quantity_uom_version` varchar(64) NULL DEFAULT NULL COMMENT '过账时可信基本单位版本;不可用当前版本回填历史',
  ADD COLUMN `quantity_evidence_hash` char(64) NULL DEFAULT NULL COMMENT '独立业务来源证据摘要;非调用方verified标志',
  ADD COLUMN `quantity_version` bigint(20) NULL DEFAULT NULL COMMENT '精确数量流水连续版本;独立于原乐观锁version',
  ADD COLUMN `reservation_version` bigint(20) NULL DEFAULT NULL COMMENT '预留事件连续版本;无事件初始零也须来源证明',
  ADD COLUMN `quantity_source_head_tx_id` bigint(20) NULL DEFAULT NULL COMMENT '精确数量链末端库存流水ID',
  ADD COLUMN `reservation_head_event_id` bigint(20) NULL DEFAULT NULL COMMENT '预留所有权链末端事件ID',
  ADD COLUMN `quantity_source_at` datetime(6) NULL DEFAULT NULL COMMENT '独立来源重建时间;不自动写NOW()升级旧行';

ALTER TABLE `wm_material_stock_tx`
  ADD COLUMN `tx_quantity_exact` decimal(18, 4) NULL DEFAULT NULL COMMENT '精确候选发生量;旧流水不自动放大精度',
  ADD COLUMN `before_quantity_exact` decimal(18, 4) NULL DEFAULT NULL COMMENT '精确候选变动前数量;无来源保持NULL',
  ADD COLUMN `after_quantity_exact` decimal(18, 4) NULL DEFAULT NULL COMMENT '精确候选变动后数量;无来源保持NULL',
  ADD COLUMN `quantity_contract_version` int(11) NULL DEFAULT NULL COMMENT '数量来源合同版本;缺失不认证',
  ADD COLUMN `quantity_origin` varchar(32) NOT NULL DEFAULT 'UNPROVEN' COMMENT 'UNPROVEN/NATIVE_V1/RECONCILED_V1;状态不是认证证据',
  ADD COLUMN `quantity_uom_version` varchar(64) NULL DEFAULT NULL COMMENT '过账时可信基本单位版本;不可用当前版本回填历史',
  ADD COLUMN `quantity_evidence_hash` char(64) NULL DEFAULT NULL COMMENT '独立业务来源证据摘要;非调用方verified标志',
  ADD COLUMN `quantity_version_before` bigint(20) NULL DEFAULT NULL COMMENT '精确数量链前版本',
  ADD COLUMN `quantity_version_after` bigint(20) NULL DEFAULT NULL COMMENT '精确数量链后版本;必须前版本+1',
  ADD COLUMN `source_event_key` varchar(64) NULL DEFAULT NULL COMMENT '租户内业务过账原请求幂等键;历史不补键',
  ADD UNIQUE INDEX `uk_stock_tx_source_event`(`tenant_id`, `source_event_key`) USING BTREE,
  ADD UNIQUE INDEX `uk_stock_tx_quantity_version`(`tenant_id`, `material_stock_id`, `quantity_version_after`) USING BTREE;

CREATE TABLE `wm_stock_reservation_event`  (
  `event_id` bigint(20) NOT NULL COMMENT '不可变预留事件ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID;不默认租户',
  `stock_id` bigint(20) NOT NULL COMMENT '归属库存ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `base_uom_id` varchar(64) NOT NULL COMMENT '物料基本单位规范ID',
  `quantity_uom_version` varchar(64) NOT NULL COMMENT '事件发生时可信基本单位版本',
  `owner_doc_type` varchar(64) NOT NULL COMMENT 'SO_LINE/WO_MRP_DETAIL;未知类型拒绝',
  `owner_doc_id` bigint(20) NOT NULL COMMENT '所有者业务单据头ID',
  `owner_doc_line_id` bigint(20) NOT NULL COMMENT '所有者业务单据行ID',
  `owner_mrp_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '工单所有者MRP明细ID;销售归属不得猜MRP',
  `event_type` varchar(32) NOT NULL COMMENT 'RESERVE/RELEASE/CONSUME;不以余额猜类型',
  `reserved_delta` decimal(18, 4) NOT NULL COMMENT '有符号基本单位预留变化量',
  `before_reserved_quantity` decimal(18, 4) NOT NULL COMMENT '库存全体所有者预留变动前总量',
  `after_reserved_quantity` decimal(18, 4) NOT NULL COMMENT '库存全体所有者预留变动后总量',
  `reservation_version_before` bigint(20) NOT NULL COMMENT '连续预留链前版本',
  `reservation_version_after` bigint(20) NOT NULL COMMENT '连续预留链后版本;须前版本+1',
  `stock_version_before` int(11) NOT NULL COMMENT '原库存乐观锁CAS前版本',
  `stock_version_after` int(11) NOT NULL COMMENT '同ERP事务库存CAS后版本',
  `source_doc_type` varchar(64) NOT NULL COMMENT '预留/释放/消费来源业务类型',
  `source_doc_id` bigint(20) NOT NULL COMMENT '来源业务单据头ID',
  `source_doc_line_id` bigint(20) NOT NULL COMMENT '来源业务单据行ID',
  `source_event_key` varchar(64) NOT NULL COMMENT '租户内原请求幂等键;重试不再占用',
  `related_stock_tx_id` bigint(20) NULL DEFAULT NULL COMMENT 'CONSUME对应库存出库流水ID;其他事件不猜流水',
  `quantity_contract_version` int(11) NULL DEFAULT NULL COMMENT '数量来源合同版本;缺失不认证',
  `quantity_origin` varchar(32) NOT NULL DEFAULT 'UNPROVEN' COMMENT '无默认认证;与库存及业务来源独立对账',
  `quantity_evidence_hash` char(64) NULL DEFAULT NULL COMMENT '独立来源证据摘要;不等于认证',
  `create_by` bigint(20) NOT NULL COMMENT '受控业务执行人',
  `create_time` datetime(6) NOT NULL COMMENT '受控数据库业务时间;禁止补造历史',
  PRIMARY KEY (`event_id`) USING BTREE,
  UNIQUE INDEX `uk_reservation_source_event`(`tenant_id`, `source_event_key`) USING BTREE,
  UNIQUE INDEX `uk_reservation_stock_version`(`tenant_id`, `stock_id`, `reservation_version_after`) USING BTREE,
  INDEX `idx_reservation_owner`(`tenant_id`, `owner_doc_type`, `owner_doc_id`, `owner_doc_line_id`) USING BTREE,
  INDEX `idx_reservation_mrp`(`tenant_id`, `owner_mrp_detail_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存预留所有权不可变事件;旧余额无记录不认证' ROW_FORMAT = Dynamic;
