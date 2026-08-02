-- =============================================
-- NexusERP - 视觉识别 CV 模块: Phase 2 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-08-02
-- 所属: NexusERP 视觉识别接入 Phase 2 (物料 OCR 调试页)
-- 幂等: 使用 INSERT IGNORE
-- 菜单段位: 6190-6192 (避让 6182-6187 视觉识别 Phase 1 / 6100-6178 财务 / 4241-4260 NCR)
-- 挂载: 视觉识别(6182) 目录下
-- =============================================

USE `ry-vue`;

-- ===== 菜单 =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 检测调试页(OCR/YOLO,管理/验收用)
(6190, '物料检测调试', 6182, 3, 'detect', 'cv/detect/index', NULL, 1, 0, 'C', '0', '0', 'cv:detect:query', 'search', 103, 1, NOW(), NULL, NULL, '物料标签 OCR/YOLO 检测调试(Phase 2)');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `menu_id` BETWEEN 6182 AND 6200 ORDER BY menu_id;
