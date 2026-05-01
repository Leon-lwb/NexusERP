-- =============================================
-- ERP Business Tables - Extracted from ry-vue-erp.sql
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for bd_bom
-- ----------------------------
DROP TABLE IF EXISTS `bd_bom`;
CREATE TABLE `bd_bom`  (
  `bom_id` bigint(20) NOT NULL COMMENT 'BOM主表ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_material_id` bigint(20) NOT NULL COMMENT '父项物料ID',
  `bom_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1.0' COMMENT 'BOM版本号',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BOM名称',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `is_current` tinyint(4) NULL DEFAULT 1 COMMENT '是否当前版本 0:否 1:是',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`bom_id`) USING BTREE,
  UNIQUE INDEX `uk_parent_version`(`parent_material_id`, `bom_version`) USING BTREE,
  INDEX `idx_parent_material`(`parent_material_id`) USING BTREE,
  INDEX `idx_current`(`is_current`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_bom
-- ----------------------------

-- Table structure for bd_bom_detail
-- ----------------------------
DROP TABLE IF EXISTS `bd_bom_detail`;
CREATE TABLE `bd_bom_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT 'BOM明细ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `bom_id` bigint(20) NOT NULL COMMENT 'BOM主表ID',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `operation_step` int(11) NULL DEFAULT NULL COMMENT '工序步骤',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `lead_time` int(11) NULL DEFAULT 0 COMMENT '提前期(小时)',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_child_material`(`child_material_id`) USING BTREE,
  INDEX `idx_operation_step`(`operation_step`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_bom_detail
-- ----------------------------

-- Table structure for bd_client
-- ----------------------------
DROP TABLE IF EXISTS `bd_client`;
CREATE TABLE `bd_client`  (
  `client_id` bigint(20) NOT NULL COMMENT '客户ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `client_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户简写',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户简介',
  `client_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ENTERPRISE' COMMENT '客户类型',
  `client_logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户LOGO地址',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户默认送货地点',
  `web_site` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户官网地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户邮箱',
  `tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户电话',
  `contact1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1',
  `contact1_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1电话',
  `contact1_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1邮箱',
  `contact2` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2',
  `contact2_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2电话',
  `contact2_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2邮箱',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '统一社会信用代码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划ID',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`client_id`) USING BTREE,
  INDEX `idx_province_area`(`province_area_id`) USING BTREE,
  INDEX `idx_city_area`(`city_area_id`) USING BTREE,
  INDEX `idx_district_area`(`district_area_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_client
-- ----------------------------

-- Table structure for bd_client_credit
-- ----------------------------
DROP TABLE IF EXISTS `bd_client_credit`;
CREATE TABLE `bd_client_credit`  (
  `credit_id` bigint(20) NOT NULL COMMENT '客户信用ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `client_id` bigint(20) NOT NULL COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_credit` int(11) NULL DEFAULT NULL COMMENT '信用额度',
  `client_time` int(11) NULL DEFAULT NULL COMMENT '账期',
  `client_level` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT '客户等级',
  `client_score` int(11) NOT NULL DEFAULT 60 COMMENT '客户评分',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`credit_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_client_code`(`client_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户信用表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for bd_material
-- ----------------------------
DROP TABLE IF EXISTS `bd_material`;
CREATE TABLE `bd_material`  (
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `material_desc` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料描述',
  `category_id` bigint(20) NOT NULL COMMENT '物料分类ID',
  `base_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '基本计量单位ID',
  `purchase_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '采购单位ID',
  `inventory_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '库存单位ID',
  `sale_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '销售单位ID',
  `material_type` tinyint(4) NOT NULL COMMENT '物料类型 1:原材料 2:半成品 3:成品 4:辅料',
  `source_type` tinyint(4) NULL DEFAULT 1 COMMENT '来源类型 1:自制 2:外购 3:委外',
  `is_batch_manage` tinyint(4) NULL DEFAULT 0 COMMENT '是否批次管理 0:否 1:是',
  `is_serial_manage` tinyint(4) NULL DEFAULT 0 COMMENT '是否序列号管理 0:否 1:是',
  `safety_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '安全库存',
  `min_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最低库存',
  `max_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最高库存',
  `standard_cost` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '标准成本',
  `reference_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '参考价格',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `quality_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质量状态',
  `shelf_life` int(11) NULL DEFAULT 0 COMMENT '保质期(天)',
  `weight` decimal(12, 4) NULL DEFAULT 0.0000 COMMENT '重量(kg)',
  `volume` decimal(12, 4) NULL DEFAULT 0.0000 COMMENT '体积(m³)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`material_id`) USING BTREE,
  UNIQUE INDEX `uk_material_code`(`material_code`) USING BTREE,
  INDEX `idx_category_id`(`category_id`) USING BTREE,
  INDEX `idx_material_type`(`material_type`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_base_uom`(`base_uom_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料产品主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_material
-- ----------------------------

-- Table structure for bd_material_category
-- ----------------------------
DROP TABLE IF EXISTS `bd_material_category`;
CREATE TABLE `bd_material_category`  (
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_id` bigint(20) NULL DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
  `category_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `category_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类描述',
  `level_num` int(11) NULL DEFAULT 1 COMMENT '层级深度，1为顶级',
  `path_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路径ID串，格式：1,5,23',
  `path_names` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路径名称串，格式：原材料,金属材料,钢材',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `uk_category_code`(`category_code`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_level_sort`(`level_num`, `sort_order`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料产品分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_material_category
-- ----------------------------

-- Table structure for bd_material_vendor
-- ----------------------------
DROP TABLE IF EXISTS `bd_material_vendor`;
CREATE TABLE `bd_material_vendor`  (
  `relation_id` bigint(20) NOT NULL COMMENT '关系ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `vendor_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `vendor_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `vendor_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `lead_time` int(11) NULL DEFAULT 0 COMMENT '供货周期(天)',
  `min_order_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最小订货量',
  `price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `is_default` tinyint(4) NULL DEFAULT 0 COMMENT '是否默认供应商 0:否 1:是',
  `priority` int(11) NULL DEFAULT 0 COMMENT '优先级',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`relation_id`) USING BTREE,
  UNIQUE INDEX `uk_material_supplier`(`material_id`, `vendor_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_supplier_id`(`vendor_id`) USING BTREE,
  INDEX `idx_default`(`is_default`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料供应商关系表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for bd_unit_of_measure
-- ----------------------------
DROP TABLE IF EXISTS `bd_unit_of_measure`;
CREATE TABLE `bd_unit_of_measure`  (
  `uom_id` bigint(20) NOT NULL COMMENT '单位ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `uom_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位编码',
  `uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位名称',
  `uom_symbol` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位符号(kg、m、pcs等)',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位类别(重量、长度、数量等)',
  `is_base` tinyint(4) NULL DEFAULT 0 COMMENT '是否基本单位 0:否 1:是',
  `primary_id` bigint(20) NULL DEFAULT NULL COMMENT '基本单位ID',
  `primary_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '基本单位名称',
  `conversion_rate` decimal(18, 6) NULL DEFAULT 1.000000 COMMENT '转换率(相对于基本单位)',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`uom_id`) USING BTREE,
  UNIQUE INDEX `uk_uom_code`(`uom_code`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计量单位表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_unit_of_measure
-- ----------------------------

-- Table structure for bd_vendor
-- ----------------------------
DROP TABLE IF EXISTS `bd_vendor`;
CREATE TABLE `bd_vendor`  (
  `vendor_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `tax_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '税号',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '详细地址',
  `contact_person` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人',
  `phone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '电话',
  `bank_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '银行账户',
  `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行业',
  `region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地区',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '合作状态 ACTIVE/FROZEN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划ID',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`vendor_id`) USING BTREE,
  UNIQUE INDEX `uk_vendor_code`(`vendor_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for bd_workshop
-- ----------------------------
DROP TABLE IF EXISTS `bd_workshop`;
CREATE TABLE `bd_workshop`  (
  `workshop_id` bigint(20) NOT NULL COMMENT '车间ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `workshop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间编码',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间名称',
  `workshop_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间描述',
  `manager_id` bigint(20) NOT NULL COMMENT '车间主任ID',
  `manager_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间主任',
  `capacity` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '产能（标准工时/天）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`workshop_id`) USING BTREE,
  UNIQUE INDEX `uk_workshop_code`(`workshop_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_workshop
-- ----------------------------

-- Table structure for bd_workstation
-- ----------------------------
DROP TABLE IF EXISTS `bd_workstation`;
CREATE TABLE `bd_workstation`  (
  `workstation_id` bigint(20) NOT NULL COMMENT '工作站ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `workstation_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站编码',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站名称',
  `workshop_id` bigint(20) NOT NULL COMMENT '所属车间ID',
  `workshop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间编码',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `workstation_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心描述',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '设备ID',
  `operator_count` int(11) NULL DEFAULT 1 COMMENT '标准操作工人数',
  `standard_capacity` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '标准产能（件/小时）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`workstation_id`) USING BTREE,
  UNIQUE INDEX `uk_workstation_code`(`workstation_code`) USING BTREE,
  INDEX `idx_workshop_id`(`workshop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作站表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_workstation
-- ----------------------------

-- Table structure for prd_material_requirement
-- ----------------------------
DROP TABLE IF EXISTS `prd_material_requirement`;
CREATE TABLE `prd_material_requirement`  (
  `requirement_id` bigint(20) NOT NULL COMMENT '物料需求单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `requirement_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料需求单编号',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '生产计划ID',
  `plan_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产计划编号',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `requirement_date` datetime(0) NULL DEFAULT NULL COMMENT '需求日期',
  `total_material_types` bigint(20) NULL DEFAULT 0 COMMENT '物料种类总数',
  `total_required_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '需求总量',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `planner_id` bigint(20) NULL DEFAULT NULL COMMENT '计划员ID',
  `planner_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划员',
  `department_id` bigint(20) NULL DEFAULT NULL COMMENT '部门ID',
  `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `confirmed_time` datetime(0) NULL DEFAULT NULL COMMENT '确认时间',
  `issued_time` datetime(0) NULL DEFAULT NULL COMMENT '发料时间',
  `closed_time` datetime(0) NULL DEFAULT NULL COMMENT '关闭时间',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`requirement_id`) USING BTREE,
  UNIQUE INDEX `uk_requirement_no`(`requirement_no`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料需求单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prd_material_requirement
-- ----------------------------

-- Table structure for prd_material_requirement_detail
-- ----------------------------
DROP TABLE IF EXISTS `prd_material_requirement_detail`;
CREATE TABLE `prd_material_requirement_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '需求明细ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `requirement_id` bigint(20) NOT NULL COMMENT '物料需求单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `bom_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT 'BOM标准用量',
  `loss_rate` decimal(18, 6) NULL DEFAULT 0.000000 COMMENT '损耗率(%)',
  `required_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '需求数量',
  `issued_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发料数量',
  `stock_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '当前库存数量',
  `shortage_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '缺料数量',
  `operation_step` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心名称',
  `lead_time` bigint(20) NULL DEFAULT NULL COMMENT '提前期(小时)',
  `required_date` datetime(0) NULL DEFAULT NULL COMMENT '需求日期',
  `status` bigint(20) NULL DEFAULT NULL COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_requirement_id`(`requirement_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料需求单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prd_material_requirement_detail
-- ----------------------------

-- Table structure for prd_production_plan
-- ----------------------------
DROP TABLE IF EXISTS `prd_production_plan`;
CREATE TABLE `prd_production_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '生产计划ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生产计划编号',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划名称',
  `version` bigint(20) NULL DEFAULT 1 COMMENT '版本号',
  `original_plan_id` bigint(20) NULL DEFAULT NULL COMMENT '原始计划ID',
  `reference_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '参考单据号',
  `plan_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划类型',
  `plan_date` datetime(0) NULL DEFAULT NULL COMMENT '计划日期',
  `start_date` datetime(0) NULL DEFAULT NULL COMMENT '计划开始日期',
  `end_date` datetime(0) NULL DEFAULT NULL COMMENT '计划结束日期',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '优先级',
  `planner_id` bigint(20) NULL DEFAULT NULL COMMENT '计划员ID',
  `planner_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划员',
  `department_id` bigint(20) NULL DEFAULT NULL COMMENT '生产部门ID',
  `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产部门',
  `total_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划总数量',
  `confirmed_time` datetime(0) NULL DEFAULT NULL COMMENT '确认时间',
  `released_time` datetime(0) NULL DEFAULT NULL COMMENT '下达时间',
  `closed_time` datetime(0) NULL DEFAULT NULL COMMENT '关闭时间',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'bom名称',
  `bom_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'bom版本',
  `is_current` bigint(20) NULL DEFAULT 0 COMMENT '是否当前版本',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `material_requirement_id` bigint(20) NULL DEFAULT NULL COMMENT '物料需求单ID',
  `material_requirement_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料需求单编号',
  `order_line_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单子表id',
  `order_line_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单子表行号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  UNIQUE INDEX `uk_plan_no`(`plan_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_plan_date`(`plan_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产计划表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prd_production_plan
-- ----------------------------

-- Table structure for prd_work_order
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_order`;
CREATE TABLE `prd_work_order`  (
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编号',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '来源计划ID',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划名称',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单编号',
  `source_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品规格',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位id',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `plan_start_date` datetime(0) NULL DEFAULT NULL COMMENT '计划开始日期',
  `plan_end_date` datetime(0) NULL DEFAULT NULL COMMENT '计划结束日期',
  `actual_start_date` datetime(0) NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_date` datetime(0) NULL DEFAULT NULL COMMENT '实际结束时间',
  `wo_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单状态',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作站ID',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站名称',
  `production_line_id` bigint(20) NULL DEFAULT NULL COMMENT '生产线ID',
  `responsible_id` bigint(20) NULL DEFAULT NULL COMMENT '负责人ID',
  `responsible_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '优先级',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOMid',
  `bom_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BOM版本',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BOM名称',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prd_work_order
-- ----------------------------

-- Table structure for prd_work_order_resource
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_order_resource`;
CREATE TABLE `prd_work_order_resource`  (
  `id` bigint(20) NOT NULL COMMENT '资源分配ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型：WORKSHOP-车间, WORKSTATION-工作站, EQUIPMENT-设备, TEAM-班组, PRODUCTION_LINE-生产线',
  `resource_id` bigint(20) NULL DEFAULT NULL COMMENT '资源ID',
  `resource_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '资源编码',
  `resource_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '资源名称',
  `seq` int(11) NULL DEFAULT 0 COMMENT '资源顺序/工序顺序',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父资源ID（如工作站所属车间ID）',
  `parent_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父资源类型',
  `plan_start_time` datetime(0) NULL DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_time` datetime(0) NULL DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` datetime(0) NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime(0) NULL DEFAULT NULL COMMENT '实际结束时间',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待执行, PROCESSING-执行中, COMPLETED-已完成',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_resource_type`(`resource_type`) USING BTREE,
  INDEX `idx_resource_id`(`resource_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工单资源分配表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of prd_work_order_resource
-- ----------------------------

-- Table structure for prd_work_report
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_report`;
CREATE TABLE `prd_work_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报工记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `report_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报工单编号',
  `report_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工类型：NORMAL-正常报工，REWORK-返工，REPAIR-返修，OVERTIME-加班',
  `report_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工状态：DRAFT-草稿，SUBMITTED-已提交，APPROVED-已审核，REJECTED-已驳回',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号',
  `wo_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单名称',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位ID',
  `unit_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位编码',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `route_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺流程ID',
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工艺流程编号',
  `route_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工艺流程名称',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `operation_step` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤序号',
  `operation_seq` bigint(20) NULL DEFAULT NULL COMMENT '当前工序报工次数（用于多次报工）',
  `task_id` bigint(20) NULL DEFAULT NULL COMMENT '生产任务ID',
  `task_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产任务编号',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作站ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站编码',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站名称',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `lot_number` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产批号/批次号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编号',
  `expire_date` datetime(0) NULL DEFAULT NULL COMMENT '过期日期/有效期至',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量（工序计划数量）',
  `reported_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '本次报工数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '合格数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `labor_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '工废数量（人为原因报废）',
  `material_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '料废数量（材料原因报废）',
  `other_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '其他原因报废数量',
  `uncheck_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '待检测数量',
  `cumulative_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '累计报工数量（到本次为止）',
  `remaining_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '剩余数量',
  `report_date` datetime(0) NULL DEFAULT NULL COMMENT '报工日期',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `labor_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '人工工时（小时）',
  `machine_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '机器工时（小时）',
  `setup_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '准备工时（小时）',
  `wait_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '等待工时（小时）',
  `pause_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '暂停工时（小时）',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '操作工ID（主操作工）',
  `worker_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作工编码',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作工姓名',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组编码',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `helper_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '辅助人员ID列表（逗号分隔）',
  `helper_names` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '辅助人员姓名列表',
  `quality_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质量状态：PENDING-待检，QUALIFIED-合格，UNQUALIFIED-不合格，PARTIAL-部分合格',
  `quality_check_id` bigint(20) NULL DEFAULT NULL COMMENT '质检记录ID',
  `quality_check_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质检单号',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `report_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工途径：PC-电脑端，APP-移动端，PDA-手持设备，AUTO-自动采集',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型：MANUAL-手工录入，SCAN-扫码，IMPORT-导入，INTERFACE-接口同步',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据编号',
  `barcode` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '条码信息',
  `submit_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '提交人',
  `submit_time` datetime(0) NULL DEFAULT NULL COMMENT '提交时间',
  `audit_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核意见',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `deleted` tinyint(4) NULL DEFAULT 0 COMMENT '删除标志（0-未删除，1-已删除）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`report_id`) USING BTREE,
  UNIQUE INDEX `uk_report_no`(`report_no`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_report_status`(`report_status`) USING BTREE,
  INDEX `idx_quality_status`(`quality_status`) USING BTREE,
  INDEX `idx_report_date`(`report_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产报工表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for sch_attendance
-- ----------------------------
DROP TABLE IF EXISTS `sch_attendance`;
CREATE TABLE `sch_attendance`  (
  `attendance_id` bigint(20) NOT NULL COMMENT '考勤记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `record_date` datetime(0) NULL DEFAULT NULL COMMENT '考勤日期',
  `schedule_id` bigint(20) NULL DEFAULT NULL COMMENT '关联排班ID',
  `clock_in_time` datetime(0) NULL DEFAULT NULL COMMENT '上班打卡时间',
  `clock_out_time` datetime(0) NULL DEFAULT NULL COMMENT '下班打卡时间',
  `late_minutes` bigint(20) NULL DEFAULT 0 COMMENT '迟到分钟数',
  `leave_early_minutes` bigint(20) NULL DEFAULT 0 COMMENT '早退分钟数',
  `overtime_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '加班时长',
  `attendance_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '考勤结果: normal正常, late迟到, early_leave早退, absent旷工, leave请假',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据来源: manual手动, device设备同步',
  `device_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打卡设备ID',
  `device_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`attendance_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_record_date`(`record_date`) USING BTREE,
  INDEX `idx_attendance_status`(`attendance_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_attendance
-- ----------------------------

-- Table structure for sch_employee_schedule
-- ----------------------------
DROP TABLE IF EXISTS `sch_employee_schedule`;
CREATE TABLE `sch_employee_schedule`  (
  `schedule_id` bigint(20) NOT NULL COMMENT '员工排班ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '排班计划ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `scheduled_date` datetime(0) NULL DEFAULT NULL COMMENT '排班日期',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '排班开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '排班结束时间',
  `actual_shift_id` bigint(20) NULL DEFAULT NULL COMMENT '实际班次ID（用于调班覆盖）',
  `schedule_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '排班状态',
  `is_working_day` tinyint(4) NULL DEFAULT 1 COMMENT '是否工作日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`schedule_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_scheduled_date`(`scheduled_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工排班明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_employee_schedule
-- ----------------------------

-- Table structure for sch_holiday
-- ----------------------------
DROP TABLE IF EXISTS `sch_holiday`;
CREATE TABLE `sch_holiday`  (
  `holiday_id` bigint(20) NOT NULL COMMENT '节假日ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `holiday_date` datetime(0) NOT NULL COMMENT '日期',
  `holiday_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '节假日名称',
  `holiday_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类型',
  `year` bigint(20) NULL DEFAULT NULL COMMENT '年份',
  `is_working_day` tinyint(4) NULL DEFAULT 0 COMMENT '是否调休工作日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`holiday_id`) USING BTREE,
  UNIQUE INDEX `uk_holiday_date`(`holiday_date`) USING BTREE,
  INDEX `idx_year`(`year`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节假日设置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_holiday
-- ----------------------------

-- Table structure for sch_plan
-- ----------------------------
DROP TABLE IF EXISTS `sch_plan`;
CREATE TABLE `sch_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划编码',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `start_date` datetime(0) NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` datetime(0) NULL DEFAULT NULL COMMENT '结束日期',
  `period_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '周期类型',
  `rotation_type` int(11) NULL DEFAULT NULL COMMENT '轮班方式',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布人',
  `published_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  UNIQUE INDEX `uk_plan_code`(`plan_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '排班计划表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_plan
-- ----------------------------

-- Table structure for sch_plan_team
-- ----------------------------
DROP TABLE IF EXISTS `sch_plan_team`;
CREATE TABLE `sch_plan_team`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排班计划ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计划班组关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_plan_team
-- ----------------------------

-- Table structure for sch_punch_rule
-- ----------------------------
DROP TABLE IF EXISTS `sch_punch_rule`;
CREATE TABLE `sch_punch_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '适用班次ID（NULL表示所有班次）',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `work_start_time` time(0) NULL DEFAULT NULL COMMENT '上班时间',
  `work_end_time` time(0) NULL DEFAULT NULL COMMENT '下班时间',
  `allow_clock_in_start` time(0) NULL DEFAULT NULL COMMENT '允许上班打卡开始时间',
  `allow_clock_in_end` time(0) NULL DEFAULT NULL COMMENT '允许上班打卡结束时间',
  `allow_clock_out_start` time(0) NULL DEFAULT NULL COMMENT '允许下班打卡开始时间',
  `allow_clock_out_end` time(0) NULL DEFAULT NULL COMMENT '允许下班打卡结束时间',
  `location_lat` decimal(18, 8) NULL DEFAULT NULL COMMENT '打卡中心点纬度',
  `location_lng` decimal(18, 8) NULL DEFAULT NULL COMMENT '打卡中心点经度',
  `location_radius` bigint(20) NULL DEFAULT NULL COMMENT '打卡半径（米）',
  `location_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打卡地点描述',
  `allow_wifi_ssids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '允许的Wi-Fi网络SSID列表（JSON数组）',
  `allow_bluetooth_devices` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '允许的蓝牙设备列表（JSON数组）',
  `allow_outdoor_punch` tinyint(4) NULL DEFAULT 0 COMMENT '是否允许外勤打卡 0:否 1:是',
  `outdoor_require_photo` tinyint(4) NULL DEFAULT 0 COMMENT '外勤打卡是否要求拍照 0:否 1:是',
  `allow_supplement` tinyint(4) NULL DEFAULT 1 COMMENT '是否允许补卡 0:否 1:是',
  `supplement_deadline` bigint(20) NULL DEFAULT 7 COMMENT '补卡申请截止天数',
  `late_threshold` bigint(20) NULL DEFAULT 0 COMMENT '迟到阈值（分钟）',
  `early_leave_threshold` bigint(20) NULL DEFAULT 0 COMMENT '早退阈值（分钟）',
  `absent_threshold` bigint(20) NULL DEFAULT 30 COMMENT '旷工阈值（分钟）',
  `is_enabled` tinyint(4) NULL DEFAULT 1 COMMENT '是否启用 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_is_enabled`(`is_enabled`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '打卡规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_punch_rule
-- ----------------------------

-- Table structure for sch_shift
-- ----------------------------
DROP TABLE IF EXISTS `sch_shift`;
CREATE TABLE `sch_shift`  (
  `shift_id` bigint(20) NOT NULL COMMENT '班次ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `shift_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班次编码',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班次名称',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '排班计划ID',
  `start_time` time(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` time(0) NULL DEFAULT NULL COMMENT '结束时间',
  `work_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '工时(小时)',
  `is_cross_day` tinyint(4) NULL DEFAULT 0 COMMENT '是否跨天 0:否 1:是',
  `rest_start_time` time(0) NULL DEFAULT NULL COMMENT '休息开始时间',
  `rest_end_time` time(0) NULL DEFAULT NULL COMMENT '休息结束时间',
  `late_threshold` bigint(20) NULL DEFAULT NULL COMMENT '迟到阈值(分钟)',
  `early_leave_threshold` bigint(20) NULL DEFAULT NULL COMMENT '早退阈值(分钟)',
  `shift_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次类别',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次显示颜色',
  `sort_order` bigint(20) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`shift_id`) USING BTREE,
  UNIQUE INDEX `uk_shift_code`(`shift_code`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班次定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_shift
-- ----------------------------

-- Table structure for sch_team
-- ----------------------------
DROP TABLE IF EXISTS `sch_team`;
CREATE TABLE `sch_team`  (
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `team_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组编码',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组名称',
  `team_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组类别',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属车间名称',
  `leader_id` bigint(20) NULL DEFAULT NULL COMMENT '班组长ID',
  `leader_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组长姓名',
  `member_count` bigint(20) NULL DEFAULT 0 COMMENT '成员数量',
  `status` bigint(20) NULL DEFAULT 1 COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`team_id`) USING BTREE,
  UNIQUE INDEX `uk_team_code`(`team_code`) USING BTREE,
  INDEX `idx_workshop_id`(`workshop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组设置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_team
-- ----------------------------

-- Table structure for sch_team_member
-- ----------------------------
DROP TABLE IF EXISTS `sch_team_member`;
CREATE TABLE `sch_team_member`  (
  `member_id` bigint(20) NOT NULL COMMENT '成员ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `worker_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工号',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色',
  `effective_date` datetime(0) NULL DEFAULT NULL COMMENT '生效日期',
  `expire_date` datetime(0) NULL DEFAULT NULL COMMENT '失效日期',
  `status` bigint(20) NULL DEFAULT 1 COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`member_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组成员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_team_member
-- ----------------------------

-- Table structure for sch_team_schedule
-- ----------------------------
DROP TABLE IF EXISTS `sch_team_schedule`;
CREATE TABLE `sch_team_schedule`  (
  `team_schedule_id` bigint(20) NOT NULL COMMENT '班组排班ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排班计划ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `scheduled_date` datetime(0) NULL DEFAULT NULL COMMENT '排班日期',
  `week_day` bigint(20) NULL DEFAULT NULL COMMENT '星期几(1-7)',
  `is_holiday` tinyint(4) NULL DEFAULT 0 COMMENT '是否节假日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`team_schedule_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_scheduled_date`(`scheduled_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组排班（模板）表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_team_schedule
-- ----------------------------

-- Table structure for sch_worker
-- ----------------------------
DROP TABLE IF EXISTS `sch_worker`;
CREATE TABLE `sch_worker`  (
  `worker_id` bigint(20) NOT NULL COMMENT '工人档案ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的系统用户ID',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工姓名',
  `employee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工工号',
  `phonenumber` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '性别',
  `work_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工种',
  `skill_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '技能等级',
  `work_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '在岗状态',
  `entry_date` datetime(0) NULL DEFAULT NULL COMMENT '入职日期',
  `regularization_date` datetime(0) NULL DEFAULT NULL COMMENT '转正日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`worker_id`) USING BTREE,
  UNIQUE INDEX `uk_employee_id`(`employee_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_work_status`(`work_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工人档案表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sch_worker
-- ----------------------------

-- Table structure for so_customer_order
-- ----------------------------
DROP TABLE IF EXISTS `so_customer_order`;
CREATE TABLE `so_customer_order`  (
  `co_id` bigint(20) NOT NULL COMMENT '客户订单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `co_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户订单号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人电话',
  `order_date` datetime(0) NULL DEFAULT NULL COMMENT '客户下单日期',
  `requested_date` datetime(0) NULL DEFAULT NULL COMMENT '客户要求交货日',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '货币',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '订单总金额（未税）',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单状态',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '订单优先级',
  `customer_po_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户采购订单号',
  `source_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单来源',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表id',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`co_id`) USING BTREE,
  UNIQUE INDEX `uk_co_no`(`co_no`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_order_date`(`order_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of so_customer_order
-- ----------------------------

-- Table structure for so_sales_order_header
-- ----------------------------
DROP TABLE IF EXISTS `so_sales_order_header`;
CREATE TABLE `so_sales_order_header`  (
  `so_id` bigint(20) NOT NULL COMMENT '销售订单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `so_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '销售订单编号',
  `co_id` bigint(20) NULL DEFAULT NULL COMMENT '来源客户订单',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '来源客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表名称',
  `order_date` datetime(0) NULL DEFAULT NULL COMMENT '订单生成日期',
  `promise_date` datetime(0) NULL DEFAULT NULL COMMENT '承诺交货日',
  `requested_date` datetime(0) NULL DEFAULT NULL COMMENT '客户要求交货日',
  `delivery_address_id` bigint(20) NULL DEFAULT NULL COMMENT '发货地址',
  `invoice_address_id` bigint(20) NULL DEFAULT NULL COMMENT '开票地址ID',
  `delivery_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发货地址详情',
  `invoice_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '开票地址详情',
  `payment_terms` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '付款条款',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '货币',
  `exchange_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '汇率',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '含税总金额',
  `tax_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '税款',
  `discount_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '整单折扣',
  `discount_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '整单折扣率',
  `credit_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '信用状态',
  `credit_check_time` datetime(0) NULL DEFAULT NULL COMMENT '信用检查时间',
  `credit_blocked_by` bigint(20) NULL DEFAULT NULL COMMENT '信用拦截人',
  `order_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单状态',
  `project_id` bigint(20) NULL DEFAULT NULL COMMENT '项目ID',
  `is_pa_enabled` tinyint(4) NULL DEFAULT 0 COMMENT '是否启用项目配套发料 0:否 1:是',
  `confirmed_time` datetime(0) NULL DEFAULT NULL COMMENT '确认时间',
  `fulfilled_time` datetime(0) NULL DEFAULT NULL COMMENT '完全履行时间',
  `closed_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `cancelled_time` datetime(0) NULL DEFAULT NULL COMMENT '取消时间',
  `cancelled_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '取消原因',
  `sales_org_id` bigint(20) NULL DEFAULT NULL COMMENT '销售组织ID',
  `distribution_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分销渠道',
  `customer_ref_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户参考号',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`so_id`) USING BTREE,
  UNIQUE INDEX `uk_so_no`(`so_no`) USING BTREE,
  INDEX `idx_co_id`(`co_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_order_status`(`order_status`) USING BTREE,
  INDEX `idx_order_date`(`order_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of so_sales_order_header
-- ----------------------------

-- Table structure for so_sales_order_line
-- ----------------------------
DROP TABLE IF EXISTS `so_sales_order_line`;
CREATE TABLE `so_sales_order_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '销售订单行表id',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `so_id` bigint(20) NOT NULL COMMENT '销售订单ID',
  `line_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号（0010,0020）',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称（冗余）',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格（冗余）',
  `ordered_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '订单数量',
  `delivered_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发货数量',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '单价',
  `line_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行金额 = qty * price',
  `tax_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行税额',
  `discount_percent` decimal(18, 6) NULL DEFAULT NULL COMMENT '行折扣率',
  `discount_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行折扣额',
  `requested_date` datetime(0) NULL DEFAULT NULL COMMENT '行要求交期',
  `promise_date` datetime(0) NULL DEFAULT NULL COMMENT '行承诺交期',
  `fulfilled_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已履行数量（已发货）',
  `production_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已投产数量',
  `purchase_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已采购数量',
  `reserved_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已预留库存数量',
  `line_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消',
  `linked_mo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联生产工单ID',
  `linked_po_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采购订单ID',
  `reserved_batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留批次号',
  `reserved_serials` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '预留序列号列表（JSON）',
  `pa_group_id` bigint(20) NULL DEFAULT NULL COMMENT 'PA配套组ID',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '默认仓库ID',
  `delivery_priority` bigint(20) NULL DEFAULT NULL COMMENT '交付优先级',
  `customer_line_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户行参考号',
  `sort_order` bigint(20) NULL DEFAULT NULL COMMENT '排序号',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_line_status`(`line_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单行表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for wm_arrival_notice
-- ----------------------------
DROP TABLE IF EXISTS `wm_arrival_notice`;
CREATE TABLE `wm_arrival_notice`  (
  `notice_id` bigint(20) NOT NULL COMMENT '通知单ID',
  `notice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知单编号',
  `notice_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知单名称',
  `pur_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购订单编号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `arrival_date` datetime(0) NULL DEFAULT NULL COMMENT '到货日期',
  `contact` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系方式',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PREPARE' COMMENT '单据状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`notice_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '到货通知单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_arrival_notice
-- ----------------------------

-- Table structure for wm_arrival_notice_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_arrival_notice_line`;
CREATE TABLE `wm_arrival_notice_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `notice_id` bigint(20) NULL DEFAULT NULL COMMENT '通知单ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity_arrival` double(12, 2) NOT NULL COMMENT '到货数量',
  `quantity_quanlified` double(12, 2) NULL DEFAULT NULL COMMENT '合格数量',
  `iqc_check` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否来料检验',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT '来料检验单ID',
  `iqc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来料检验单编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '到货通知单行表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_arrival_notice_line
-- ----------------------------

-- Table structure for wm_material_receive
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive`;
CREATE TABLE `wm_material_receive`  (
  `receive_id` bigint(20) NOT NULL COMMENT '入库单ID',
  `receive_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单编号',
  `receive_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单名称',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT '来料检验单ID',
  `iqc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来料检验单编号',
  `notice_id` bigint(20) NULL DEFAULT NULL COMMENT '到货通知单ID',
  `notice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '到货通知单编号',
  `pur_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单ID',
  `pur_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购订单编号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `receive_date` datetime(0) NULL DEFAULT NULL COMMENT '入库日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PREPARE' COMMENT '单据状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板（wf_custom_form.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`receive_id`) USING BTREE,
  INDEX `idx_receive_status`(`status`) USING BTREE,
  INDEX `idx_receive_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_material_receive
-- ----------------------------

-- Table structure for wm_material_receive_detail
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive_detail`;
CREATE TABLE `wm_material_receive_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '明细ID',
  `line_id` bigint(20) NULL DEFAULT NULL COMMENT '行ID',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT '入库单ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity` double(12, 2) NOT NULL COMMENT '入库数量',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_material_receive_detail
-- ----------------------------

-- Table structure for wm_material_receive_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive_line`;
CREATE TABLE `wm_material_receive_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '物料入库单行ID',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT '物料入库单ID',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity_received` double(12, 2) NOT NULL COMMENT '入库数量',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单行表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_material_receive_line
-- ----------------------------

-- Table structure for wm_material_stock
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_stock`;
CREATE TABLE `wm_material_stock`  (
  `stock_id` bigint(20) NOT NULL COMMENT '库存ID',
  `category_id` bigint(20) NULL DEFAULT NULL COMMENT '物料类型ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位id',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '入库批次号',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编号',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `stock_quantity` double(12, 2) NULL DEFAULT NULL COMMENT '在库数量',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '生产工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产工单编号',
  `production_date` datetime(0) NULL DEFAULT NULL COMMENT '生产日期',
  `recpt_date` datetime(0) NULL DEFAULT NULL COMMENT '入库时间',
  `expire_date` datetime(0) NULL DEFAULT NULL COMMENT '库存有效期',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '冻结标志',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`stock_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存物料记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_material_stock
-- ----------------------------

-- Table structure for wm_material_stock_tx
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_stock_tx`;
CREATE TABLE `wm_material_stock_tx`  (
  `tx_id` bigint(20) NOT NULL COMMENT '事务ID',
  `tx_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事务类型',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `package_id` bigint(20) NULL DEFAULT NULL COMMENT '容器ID',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '容器编号',
  `source_doc_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单据类型',
  `source_doc_id` bigint(20) NULL DEFAULT NULL COMMENT '单据ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单据编号',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '单据行ID',
  `material_stock_id` bigint(20) NOT NULL COMMENT '库存记录ID',
  `tx_flag` int(11) NULL DEFAULT 1 COMMENT '库存方向',
  `tx_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '事务数量',
  `before_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '变动前数量',
  `after_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '变动后数量',
  `tx_date` datetime(0) NULL DEFAULT NULL COMMENT '事务日期',
  `related_tx_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的事务ID',
  `erp_date` datetime(0) NULL DEFAULT NULL COMMENT 'ERP账期',
  `recpt_date` datetime(0) NULL DEFAULT NULL COMMENT '接收日期',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`tx_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存事务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_material_stock_tx
-- ----------------------------

-- Table structure for wm_sales_issue
-- ----------------------------
DROP TABLE IF EXISTS `wm_sales_issue`;
CREATE TABLE `wm_sales_issue`  (
  `issue_id` bigint(20) NOT NULL COMMENT '出库单ID',
  `issue_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单编号',
  `issue_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出库单名称',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单ID',
  `so_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单编号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `issue_date` date NULL DEFAULT NULL COMMENT '出库日期',
  `total_quantity` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '出库总数量',
  `total_amount` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '出库总金额',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PREPARE' COMMENT '单据状态(PREPARE-准备中，CONFIRMED-已确认，COMPLETED-已完成，CANCELLED-已取消)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `attr1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`issue_id`) USING BTREE,
  UNIQUE INDEX `uk_issue_code`(`issue_code`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_issue_date`(`issue_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库主表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for wm_sales_issue_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_sales_issue_line`;
CREATE TABLE `wm_sales_issue_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '出库单行ID',
  `issue_id` bigint(20) NOT NULL COMMENT '出库单ID',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID（销售订单行ID）',
  `line_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `quantity_issue` decimal(16, 6) NOT NULL DEFAULT 0.000000 COMMENT '出库数量',
  `quantity_ordered` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '订单数量（冗余）',
  `quantity_delivered` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '已交付数量（冗余）',
  `unit_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `line_amount` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '行金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_issue_id`(`issue_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_batch_id`(`batch_id`) USING BTREE,
  INDEX `idx_location_id`(`location_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库明细表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- Table structure for wm_storage_area
-- ----------------------------
DROP TABLE IF EXISTS `wm_storage_area`;
CREATE TABLE `wm_storage_area`  (
  `area_id` bigint(20) NOT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区名称',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `area` double(12, 2) NULL DEFAULT NULL COMMENT '面积',
  `location_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否开启库位管理',
  `enable_flag` tinyint(4) NOT NULL COMMENT '是否启用',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `layout_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GRID' COMMENT '布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货',
  `pos_x` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标X(米)',
  `pos_y` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Y(米)',
  `pos_z` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Z(米)',
  `area_width` decimal(10, 2) NULL DEFAULT NULL COMMENT '库区宽度(米)',
  `area_length` decimal(10, 2) NULL DEFAULT NULL COMMENT '库区长度(米)',
  `area_height` decimal(10, 2) NULL DEFAULT 3.00 COMMENT '库区高度(米)',
  `rotation_x` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'X轴旋转角度',
  `rotation_y` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Y轴旋转角度',
  `rotation_z` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Z轴旋转角度',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#4CAF50' COMMENT '显示颜色',
  `opacity` decimal(3, 2) NULL DEFAULT 0.80 COMMENT '透明度',
  `model_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CUBE' COMMENT '模型类型: CUBE-立方体, CUSTOM-自定义',
  `model_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '自定义模型URL',
  `show_walls` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示边界墙',
  `show_label` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示标签',
  `grid_visible` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示网格',
  `config_json` json NULL COMMENT '3D配置JSON',
  PRIMARY KEY (`area_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库区表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_storage_area
-- ----------------------------

-- Table structure for wm_storage_location
-- ----------------------------
DROP TABLE IF EXISTS `wm_storage_location`;
CREATE TABLE `wm_storage_location`  (
  `location_id` bigint(20) NOT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位名称',
  `area_id` bigint(20) NOT NULL COMMENT '库区ID',
  `area` double(8, 2) NULL DEFAULT NULL COMMENT '面积',
  `max_loa` double(8, 2) NULL DEFAULT NULL COMMENT '最大载重量',
  `position_x` int(11) NULL DEFAULT NULL COMMENT '库位位置X',
  `position_y` int(11) NULL DEFAULT NULL COMMENT '库位位置y',
  `position_z` int(11) NULL DEFAULT NULL COMMENT '库位位置z',
  `product_mixing` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否允许产品混放',
  `batch_mixing` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否允许批次混放',
  `enable_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `location_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STORAGE' COMMENT '库位类型: STORAGE-存储, PICKING-拣货, TRANSIT-中转',
  `rack_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属货架ID',
  `layer` int(11) NULL DEFAULT 1 COMMENT '层数(从下往上)',
  `col` int(11) NULL DEFAULT 1 COMMENT '列数(从左往右)',
  `row_num` int(11) NULL DEFAULT 1 COMMENT '行数(从前到后)',
  `pos_x` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标X(米)',
  `pos_y` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Y(米)',
  `pos_z` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Z(米)',
  `width` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位宽度(米)',
  `height` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位高度(米)',
  `location_depth` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位深度(米)',
  `rotation_x` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'X轴旋转角度',
  `rotation_y` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Y轴旋转角度',
  `rotation_z` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Z轴旋转角度',
  `color_empty` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#4CAF50' COMMENT '空置时颜色',
  `color_occupied` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#2196F3' COMMENT '占用时颜色',
  `color_locked` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#F44336' COMMENT '锁定时颜色',
  `color_warning` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#FF9800' COMMENT '预警时颜色',
  `opacity` decimal(3, 2) NULL DEFAULT 0.85 COMMENT '透明度',
  `show_border` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示边框',
  `border_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#000000' COMMENT '边框颜色',
  `border_width` decimal(3, 2) NULL DEFAULT 0.02 COMMENT '边框宽度',
  `show_label` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示标签',
  `label_position` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'TOP' COMMENT '标签位置: TOP-顶部, FRONT-前面',
  `label_size` decimal(3, 2) NULL DEFAULT 0.30 COMMENT '标签大小',
  `status` int(11) NULL DEFAULT 0 COMMENT '库位状态: 0-空置, 1-占用, 2-锁定, 3-预警',
  `occupancy_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '占用率(0-100)',
  `last_inbound_time` datetime(0) NULL DEFAULT NULL COMMENT '最后入库时间',
  `last_outbound_time` datetime(0) NULL DEFAULT NULL COMMENT '最后出库时间',
  `config_json` json NULL COMMENT '3D配置JSON数据',
  PRIMARY KEY (`location_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库位表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_storage_location
-- ----------------------------

-- Table structure for wm_warehouse
-- ----------------------------
DROP TABLE IF EXISTS `wm_warehouse`;
CREATE TABLE `wm_warehouse`  (
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '位置',
  `area` double(12, 2) NULL DEFAULT NULL COMMENT '面积',
  `manager` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人',
  `manager_id` bigint(20) NULL DEFAULT NULL COMMENT 'user_id',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `status` int(11) NULL DEFAULT NULL COMMENT '仓库状态',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划id',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  PRIMARY KEY (`warehouse_id`) USING BTREE,
  UNIQUE INDEX `uk_warehouse_code`(`warehouse_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仓库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wm_warehouse
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;