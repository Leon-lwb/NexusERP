-- ============================================================================
-- 简易金属工具架(RACK-001) 测试数据初始化脚本
-- 目标数据库: ry-erp (ERP业务库) + ry-mes (MES库)
-- 租户: 000000
-- 适用场景: 跑通采购→生产→销售全流程
-- ============================================================================

-- ============================================================================
-- PART 1: ry-erp 库 — 基础数据
-- ============================================================================

-- 1. 物料 BdMaterial
--  钢板(STEEL-PLATE) 已存在 ID=1，直接复用
--  新增: 侧板组件(RACK-SIDE), 连接件包(RACK-FIT), 简易金属工具架(RACK-001)
INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `material_spec`, `material_desc`, `category_id`, `base_uom_id`, `purchase_uom_id`, `inventory_uom_id`, `sale_uom_id`, `material_type`, `source_type`, `is_phantom`, `is_batch_manage`, `is_serial_manage`, `safety_stock`, `min_stock`, `max_stock`, `standard_cost`, `reference_price`, `status`, `quality_status`, `shelf_life`, `weight`, `volume`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(8001, '000000', 'RACK-SIDE', '侧板组件', '300mm*200mm*2mm', '工具架金属侧板，经切割焊接制成', 2, 26, NULL, 26, NULL, 3, 'PROCESS', 0, 0, 0, 0.0000, 0.0000, 0.0000, 15.0000, NULL, 1, 'QUALIFIED', 0, 1.5000, 0.0010, '工具架侧板，每台需2件', 103, 1, NOW(), 1, NOW(), '0'),
(8002, '000000', 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '包含安装所需全套五金件', 10, 28, 28, 28, 28, 3, 'PROCUREMENT', 0, 0, 0, 10.0000, 0.0000, 0.0000, 8.0000, 12.0000, 1, 'QUALIFIED', 0, 0.2000, 0.0005, '五金连接件套装', 103, 1, NOW(), 1, NOW(), '0'),
(8003, '000000', 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', '简易金属置物架，承重30kg', 3, 26, NULL, 26, 26, 3, 'PROCESS', 0, 0, 0, 5.0000, 0.0000, 0.0000, 80.0000, 200.0000, 1, 'QUALIFIED', 0, 5.0000, 0.0500, '测试用五金产品', 103, 1, NOW(), 1, NOW(), '0');

-- 2. 工艺路线 BdRouting
INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `routing_desc`, `material_id`, `material_code`, `material_name`, `routing_version`, `is_current`, `total_steps`, `total_hours`, `effective_date`, `expiry_date`, `status`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(3, '000000', 'RT-RACK-001', '简易金属工具架装配工艺路线V1.0', '标准工具架装配流程: 切割→焊接→装配→包装', 8003, 'RACK-001', '简易金属工具架', '1.0', 1, 4, 1.2500, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1, '标准工艺路线', 103, 1, NOW(), 1, NOW(), '0');

-- 3. 工艺路线步骤 BdRoutingStep
-- 关联工序: CUT(1), WELD(2), ASM(3), PKG(5)
-- 关联工作站: GZZ0002(1), GZZ0003(2), GZZ0004(3)
INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `process_name`, `step_name`, `workstation_id`, `workstation_code`, `workstation_name`, `standard_hours`, `setup_hours`, `labor_count`, `machine_count`, `labor_rate`, `machine_rate`, `is_key_process`, `is_outsource`, `quality_required`, `wait_time`, `transfer_time`, `description`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
(9,  '000000', 3, 10, 1, 'CUT',  '切割', '钢板切割', 1, 'GZZ0002', '工作站1', 0.3000, 0.2000, 1, 1, 30.0000, 20.0000, 0, 0, 0, 0.1000, 0.1000, '将钢板切割为侧板所需尺寸（2片）', NULL, 103, 1, NOW(), 1, NOW()),
(10, '000000', 3, 20, 2, 'WELD', '焊接', '侧板焊接', 2, 'GZZ0003', '工作站2', 0.5000, 0.3000, 1, 1, 40.0000, 30.0000, 1, 0, 1, 0.2000, 0.1000, '将切割件焊接为侧板组件（关键工序，需IPQC质检）', NULL, 103, 1, NOW(), 1, NOW()),
(11, '000000', 3, 30, 3, 'ASM',  '装配', '总成装配', 1, 'GZZ0002', '工作站1', 0.3000, 0.2000, 1, 0, 25.0000, 0.0000, 0, 0, 0, 0.1000, 0.1000, '将侧板组件+连接件包装配为成品工具架', NULL, 103, 1, NOW(), 1, NOW()),
(12, '000000', 3, 40, 5, 'PKG',  '包装', '成品包装', 3, 'GZZ0004', '工作站3', 0.1500, 0.1000, 1, 0, 20.0000, 0.0000, 0, 0, 0, 0.0500, 0.0500, '成品检测外观后包装入库', NULL, 103, 1, NOW(), 1, NOW());

-- 4. BOM头 BdBom
INSERT INTO `bd_bom` (`bom_id`, `tenant_id`, `parent_material_id`, `bom_version`, `bom_name`, `effective_date`, `expiry_date`, `is_current`, `status`, `remark`, `source_type`, `bom_type`, `routing_id`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(20, '000000', 8003, '1.0', '简易金属工具架标准版BOM', '2026-01-01', '2026-12-31', 1, 1, '工具架BOM: 侧板×2 + 连接件包×1', 'PROCESS', 'MBOM', 3, 103, 1, NOW(), 1, NOW(), '0'),
(21, '000000', 8001, '1.0', '侧板组件标准版BOM', '2026-01-01', '2026-12-31', 1, 1, '侧板BOM: 钢板×1.5kg', 'PROCESS', 'MBOM', NULL, 103, 1, NOW(), 1, NOW(), '0');

-- 5. BOM明细 BdBomDetail
INSERT INTO `bd_bom_detail` (`detail_id`, `tenant_id`, `bom_id`, `child_material_id`, `quantity_per_parent`, `loss_rate`, `operation_step`, `lead_time`, `effective_date`, `expiry_date`, `sort_order`, `status`, `remark`, `source_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(100, '000000', 20, 8001, 2.0000, 0.00, 30, 1, '2026-01-01', '2026-12-31', 10, 1, '每台工具架需要2件侧板', 'PROCESS', 103, 1, NOW(), 1, NOW(), '0'),
(101, '000000', 20, 8002, 1.0000, 0.00, 30, 1, '2026-01-01', '2026-12-31', 20, 1, '每台工具架需要1套连接件', 'PROCUREMENT', 103, 1, NOW(), 1, NOW(), '0'),
(102, '000000', 21, 1,    1.5000, 2.00, 10, 2, '2026-01-01', '2026-12-31', 10, 1, '每件侧板需要1.5kg钢板（损耗2%）', 'PROCUREMENT', 103, 1, NOW(), 1, NOW(), '0');

-- 6. 客户 BdClient (ID=11, 编码=C00012)
INSERT INTO `bd_client` (`client_id`, `tenant_id`, `client_code`, `client_name`, `client_nick`, `client_en`, `client_desc`, `client_type`, `client_logo`, `address`, `web_site`, `email`, `tel`, `contact1`, `contact1_tel`, `contact1_emal`, `contact2`, `contact2_tel`, `contact2_emal`, `credit_code`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `province_area_id`, `city_area_id`, `district_area_id`, `street_id`, `address_lat`, `address_lng`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(11, '000000', 'C00012', '测试五金贸易公司', '五金贸易', 'Test Hardware Trading Co., Ltd', '进货测试客户', 'ENTERPRISE', NULL, '浙江省杭州市滨江区测试路100号', NULL, 'test@hardware.com', '0571-88886666', '张三', '13800138001', 'zhangsan@hardware.com', NULL, NULL, NULL, '91330100MA8TEST001', '工具架销售测试客户', NULL, NULL, NULL, NULL, 330000, 330100, 330108, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');

-- 7. 客户信用 BdClientCredit
INSERT INTO `bd_client_credit` (`credit_id`, `tenant_id`, `client_id`, `client_code`, `client_name`, `client_credit`, `client_time`, `client_level`, `client_score`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(6, '000000', 11, 'C00012', '测试五金贸易公司', 50000, 30, 'NORMAL', 80, 103, 1, NOW(), 1, NOW(), '0');

-- 8. 供应商 BdVendor (使用新ID，避免冲突)
INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `tax_id`, `address`, `contact_person`, `phone`, `bank_account`, `industry`, `region`, `category`, `status`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `province_area_id`, `city_area_id`, `district_area_id`, `street_id`, `address_lat`, `address_lng`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(2062098763051089925, '000000', 'V00005', '宏达五金有限公司', '91330100MA8TEST002', '浙江省杭州市萧山区五金工业园', '李四', '13800138002', '6228480000009876543', '五金制造', '华东', '紧固件供应商', 'ACTIVE', '连接件包供应商', NULL, NULL, NULL, NULL, 330000, 330100, 330109, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');

-- 9. 物料-供应商关系 BdMaterialVendor
-- 钢板已有供应商V00001(ID=2062098763051089921)，单价4.5元/kg
INSERT INTO `bd_material_vendor` (`relation_id`, `tenant_id`, `material_id`, `vendor_id`, `vendor_code`, `vendor_name`, `lead_time`, `min_order_qty`, `price`, `currency`, `is_default`, `priority`, `effective_date`, `expiry_date`, `status`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(40007, '000000', 8002, 2062098763051089925, 'V00005', '宏达五金有限公司', 3, 1.0000, 12.0000, 'CNY', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1, '连接件包默认供应商', 103, 1, NOW(), 1, NOW(), '0');

-- ============================================================================
-- PART 2: ry-mes 库 — MES基础数据
-- ============================================================================

-- 10. MBOM MesMbom (MES制造BOM)
INSERT INTO `mes_mbom` (`mbom_id`, `tenant_id`, `bom_id`, `routing_id`, `mbom_version`, `mbom_status`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(30, '000000', 20, 3, '1.0', 'ACTIVE', '工具架MBOM，关联工艺路线和BOM', 103, 1, NOW(), 1, NOW(), '0');

-- 11. MBOM明细 MesMbomLine (物料分配到工序)
INSERT INTO `mes_mbom_line` (`mbom_line_id`, `tenant_id`, `mbom_id`, `bom_id`, `bom_detail_id`, `child_material_id`, `quantity_per_parent`, `loss_rate`, `routing_step_id`, `process_id`, `workstation_id`, `operation_content`, `backflush_flag`, `sort_order`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
(100, '000000', 30, 20, 100, 8001, 2.0000, 0.00, 11, 3, 1, '取侧板组件2件，装配至工具架', 0, 10, NULL, 103, 1, NOW(), 1, NOW(), '0'),
(101, '000000', 30, 20, 101, 8002, 1.0000, 0.00, 11, 3, 1, '取连接件包1套，装配至工具架', 0, 20, NULL, 103, 1, NOW(), 1, NOW(), '0'),
(102, '000000', 30, 21, 102, 1,    1.5000, 2.00, 9,  1, 1, '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, 103, 1, NOW(), 1, NOW(), '0');
