-- =====================================================
-- 脚本名称: erp-wm-3d-demo-seed.sql
-- 用途: 3D 仓库可视化 MVP 演示数据（仓库/库区/库位/库存）
-- 对应文档: doc/erp/plans/2026-06-30-erp-wm-3d-warehouse-plan.md（NexusERP-PLAN-2026-003）
-- 适用数据库: ry-erp
-- 特性: 幂等可重复执行（INSERT ... SELECT ... WHERE NOT EXISTS）
-- 租户: 000000；ID 段 9900000001+（与雪花 ID 不冲突）
-- 创建日期: 2026-06-30
-- 作者: AI Agent
-- =====================================================
-- 坐标说明（前端渲染：posX/posZ 为世界坐标中心，posY 为底面，前端对 y 补 h/2）：
--   A区（RACK 货架）：库区中心(0,0,0) 尺寸 6×4×3（宽×长×高）
--       → x∈[-3,3]  y∈[0,3]  z∈[-2,2]
--       库位 1×1×1，4列×3层 = 12 个：
--       列中心 x = -1.5 / -0.5 / 0.5 / 1.5（库位宽1，居中排，占 x∈[-2,2]）
--       层底面 y = 0 / 1 / 2（层高1，顶=3 贴合库区高度）
--       单排纵深 z = 0
--   B区（GRID 网格）：库区中心(8,0,0) 尺寸 6×4×3
--       → x∈[5,11]  y∈[0,3]  z∈[-2,2]
--       库位 1×1×1，3列×2行 = 6 个（单层）：
--       列中心 x = 7 / 8 / 9（库位宽1，居中排，占 x∈[6.5,9.5]）
--       行中心 z = -0.5 / 0.5（库位深1，占 z∈[-1,1]）
--       单层 y = 0
--   两库区间隙：A区右边界 x=3，B区左边界 x=5，间隙 2 米
-- =====================================================

-- ---------------------------------------------------
-- 1. 演示仓库
-- ---------------------------------------------------
INSERT INTO `wm_warehouse`
  (warehouse_id, warehouse_code, warehouse_name, location, area, manager, status, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000001, 'WH-3D-DEMO', '3D演示仓库', '厂区3D可视化演示仓', 500.00, 'admin', 1, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_warehouse` WHERE warehouse_id = 9900000001);

-- ---------------------------------------------------
-- 2. 演示库区（2 个：A区货架 / B区网格）
--    尺寸均为 6(宽) × 4(长) × 3(高) 米，A区中心(0,0,0)，B区中心(8,0,0)
-- ---------------------------------------------------
INSERT INTO `wm_storage_area`
  (area_id, area_code, area_name, warehouse_id, area, location_flag, enable_flag, frozen_flag, layout_type, pos_x, pos_y, pos_z, area_width, area_length, area_height, rotation_x, rotation_y, rotation_z, color, opacity, model_type, model_url, show_walls, show_label, grid_visible, config_json, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000011, 'A3D01', '原料货架区', 9900000001, 24.00, 1, 1, 0, 'RACK', 0.00, 0.00, 0.00, 6.00, 4.00, 3.00, 0.00, 0.00, 0.00, '#409eff', 0.12, 'CUBE', NULL, 1, 1, 1, NULL, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_area` WHERE area_id = 9900000011);

INSERT INTO `wm_storage_area`
  (area_id, area_code, area_name, warehouse_id, area, location_flag, enable_flag, frozen_flag, layout_type, pos_x, pos_y, pos_z, area_width, area_length, area_height, rotation_x, rotation_y, rotation_z, color, opacity, model_type, model_url, show_walls, show_label, grid_visible, config_json, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000012, 'B3D02', '成品网格区', 9900000001, 24.00, 1, 1, 0, 'GRID', 8.00, 0.00, 0.00, 6.00, 4.00, 3.00, 0.00, 0.00, 0.00, '#67c23a', 0.12, 'CUBE', NULL, 1, 1, 1, NULL, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_area` WHERE area_id = 9900000012);

-- ---------------------------------------------------
-- 3. 演示库位（库位尺寸 1×1×1）
--    A区：4列(x=-1.5/-0.5/0.5/1.5) × 3层(y=0/1/2)，z=0，共 12 个
--    B区：3列(x=7/8/9) × 2行(z=-0.5/0.5)，y=0，共 6 个
-- ---------------------------------------------------
-- A区 - 第1层（y=0）
INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000101, 'A-L1C1', 'A区1层1列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 1, 1, 1, -1.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000101);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000102, 'A-L1C2', 'A区1层2列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 1, 2, 1, -0.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000102);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000103, 'A-L1C3', 'A区1层3列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 1, 3, 1, 0.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000103);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000104, 'A-L1C4', 'A区1层4列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 1, 4, 1, 1.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000104);

-- A区 - 第2层（y=1）
INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000105, 'A-L2C1', 'A区2层1列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 2, 1, 1, -1.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000105);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000106, 'A-L2C2', 'A区2层2列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 2, 2, 1, -0.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000106);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000107, 'A-L2C3', 'A区2层3列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 2, 3, 1, 0.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000107);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000108, 'A-L2C4', 'A区2层4列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 2, 4, 1, 1.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000108);

-- A区 - 第3层（y=2，顶=3 贴合库区高度）
INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000109, 'A-L3C1', 'A区3层1列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 3, 1, 1, -1.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000109);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000110, 'A-L3C2', 'A区3层2列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 3, 2, 1, -0.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000110);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000111, 'A-L3C3', 'A区3层3列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 3, 3, 1, 0.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000111);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000112, 'A-L3C4', 'A区3层4列', 9900000001, 9900000011, 1, 1, 1, 0, 'STORAGE', 'RACK-A', 3, 4, 1, 1.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000112);

-- B区 - 3列(x=7/8/9) × 2行(z=-0.5/0.5)，单层 y=0
INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000201, 'B-R1C1', 'B区1行1列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 1, 1, 7.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000201);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000202, 'B-R1C2', 'B区1行2列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 2, 1, 8.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000202);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000203, 'B-R1C3', 'B区1行3列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 3, 1, 9.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000203);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000204, 'B-R2C1', 'B区2行1列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 1, 2, 7.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000204);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000205, 'B-R2C2', 'B区2行2列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 2, 2, 8.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000205);

INSERT INTO `wm_storage_location` (location_id, location_code, location_name, warehouse_id, area_id, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)
SELECT 9900000206, 'B-R2C3', 'B区2行3列', 9900000001, 9900000012, 1, 1, 1, 0, 'STORAGE', NULL, 1, 3, 2, 9.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, '000000', '0', 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_storage_location` WHERE location_id = 9900000206);

-- ---------------------------------------------------
-- 4. 演示库存（覆盖四种状态）
--    OCCUPIED: stock>0, reserved 低          → 101/105/108/201/206（5 个）
--    WARNING : reserved/stock >= 0.8         → 102/106/203（3 个）
--    LOCKED  : frozen_flag=1                 → 103/204（2 个）
--    EMPTY   : 无库存记录                     → 104/107/109/110/111/112/202/205（8 个）
-- ---------------------------------------------------
INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003001, 9900000001, 'DEMO-M001', '演示物料A', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000101, 'A-L1C1', 'A区1层1列', 100.00, 0.00, 0, 10.00, 1000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003001);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003002, 9900000001, 'DEMO-M001', '演示物料A', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000102, 'A-L1C2', 'A区1层2列', 100.00, 85.00, 0, 10.00, 1000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003002);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003003, 9900000002, 'DEMO-M002', '演示物料B', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000103, 'A-L1C3', 'A区1层3列', 50.00, 0.00, 1, 20.00, 1000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003003);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003004, 9900000001, 'DEMO-M001', '演示物料A', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000105, 'A-L2C1', 'A区2层1列', 200.00, 0.00, 0, 10.00, 2000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003004);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003005, 9900000002, 'DEMO-M002', '演示物料B', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000106, 'A-L2C2', 'A区2层2列', 80.00, 70.00, 0, 20.00, 1600.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003005);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003006, 9900000001, 'DEMO-M001', '演示物料A', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000108, 'A-L2C4', 'A区2层4列', 30.00, 0.00, 0, 10.00, 300.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003006);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003007, 9900000003, 'DEMO-P001', '演示成品P', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000201, 'B-R1C1', 'B区1行1列', 500.00, 0.00, 0, 50.00, 25000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003007);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003008, 9900000003, 'DEMO-P001', '演示成品P', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000203, 'B-R1C3', 'B区1行3列', 60.00, 55.00, 0, 50.00, 3000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003008);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003009, 9900000003, 'DEMO-P001', '演示成品P', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000204, 'B-R2C1', 'B区2行1列', 10.00, 0.00, 1, 50.00, 500.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003009);

INSERT INTO `wm_material_stock` (stock_id, material_id, material_code, material_name, base_uom_name, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)
SELECT 9900003010, 9900000003, 'DEMO-P001', '演示成品P', '件', 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000206, 'B-R2C3', 'B区2行3列', 40.00, 0.00, 0, 50.00, 2000.00, '000000', '0', 1, 1, NOW(), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE stock_id = 9900003010);

-- =====================================================
-- 验证查询（执行后人工核对，预期 18 库位 / 10 库存 / 四色齐全，库位均落在所属库区内）
-- 核对库位是否落入库区范围：
--   SELECT l.location_id, l.location_code, l.area_id, l.pos_x, l.pos_y, l.pos_z,
--          a.pos_x AS area_x, a.area_width, a.area_height
--   FROM wm_storage_location l JOIN wm_storage_area a ON l.area_id = a.area_id
--   WHERE l.warehouse_id = 9900000001 ORDER BY l.area_id, l.location_id;
-- 库存状态聚合：
--   SELECT location_id, SUM(stock_quantity) qty, SUM(reserved_quantity) rsv, MAX(frozen_flag) frz
--   FROM wm_material_stock WHERE warehouse_id = 9900000001 GROUP BY location_id;
-- =====================================================
