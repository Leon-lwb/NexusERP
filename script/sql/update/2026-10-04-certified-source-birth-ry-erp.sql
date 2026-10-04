-- 2026-10-04：新物料与初始空库存的不可补登记出生事实，适用已核 ERP 基线。
-- 仅新增空表；须先人工确认目标库、备份、结构基线及写入维护窗口，不自动执行。
-- 只由专属新物料 API 在同一 ERP 物理事务内写入；没有旧物料回填、默认核验或历史 DML。
-- 本增量不可盲目重放；主安装定义及变更登记由主线程整合，不导入含删表的主脚本。
CREATE TABLE `erp_certified_source_birth` (
  `birth_id` bigint(20) NOT NULL COMMENT '服务端生成出生主键',
  `tenant_id` varchar(20) NOT NULL COMMENT '创建事务租户',
  `material_id` bigint(20) NOT NULL COMMENT '同事务服务端新建物料主键，禁止旧物料补登记',
  `material_code` varchar(64) NOT NULL COMMENT '新物料原始编码',
  `category_id` bigint(20) NOT NULL COMMENT '创建时分类主键',
  `base_uom_id` bigint(20) NOT NULL COMMENT '已正式确认的根基本单位主键',
  `quantity_scale` tinyint(3) unsigned NOT NULL COMMENT '创建时已核基本单位精度',
  `conversion_version` varchar(64) NOT NULL COMMENT '创建时正式单位版本',
  `unit_verified_at` datetime NOT NULL COMMENT '创建时单位正式核验时点，不能由客户端出生请求提供',
  `unit_fingerprint` char(64) NOT NULL COMMENT '服务端完整单位原始事实摘要',
  `warehouse_id` bigint(20) NOT NULL COMMENT '出生明确的仓库主键，不缩小物料全局库存池',
  `area_id` bigint(20) NOT NULL COMMENT '出生明确的库区主键',
  `location_id` bigint(20) NOT NULL COMMENT '出生明确的储位主键',
  `storage_fingerprint` char(64) NOT NULL COMMENT '服务端完整仓储父链摘要',
  `born_at` datetime NOT NULL COMMENT '与物料插入同事务的数据库时点',
  `actor_id` bigint(20) NOT NULL COMMENT '服务端当前登录操作人',
  `scenario` varchar(500) NOT NULL COMMENT '明确本地测试模拟场景，不冒充现场实绩',
  `initial_state` varchar(32) NOT NULL COMMENT '原始出生状态 EMPTY_AT_BIRTH；无记录不是可靠零',
  `birth_fingerprint` char(64) NOT NULL COMMENT '服务端固定版本出生摘要，不充当完整采购来源摘要',
  PRIMARY KEY (`birth_id`),
  UNIQUE KEY `uk_source_birth_material` (`tenant_id`, `material_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '新测试物料初始空范围永久出生事实' ROW_FORMAT = DYNAMIC;
