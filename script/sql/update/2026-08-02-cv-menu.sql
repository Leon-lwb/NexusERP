-- =============================================
-- NexusERP - 视觉识别 CV 模块: 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-08-02
-- 所属: NexusERP 视觉识别接入 Phase 1b
-- 幂等: 使用 INSERT IGNORE
-- 菜单段位: 6182-6195 (避让 6100-6178 财务 / 6181 模拟收款 / 4241-4260 NCR / 26xx 条码)
-- 目录: 视觉识别(6182) 挂在 ERP 根(2000) 下
-- 页面: 底库管理(6183) + FAIL 复查(6184)
-- 权限: cv:face:* (register/list/remove/review)
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 菜单 =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录:视觉识别
(6182, '视觉识别', 2000, 30, 'cv', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'monitor', 103, 1, NOW(), NULL, NULL, '人脸底库/FAIL复查(nexus-vision-service)'),
-- 页面
(6183, '人脸底库', 6182, 1, 'face', 'cv/face/index', NULL, 1, 0, 'C', '0', '0', 'cv:face:list', 'avatar', 103, 1, NOW(), NULL, NULL, '底库列表/注册/重新注册/批量注册'),
(6184, 'FAIL 复查', 6182, 2, 'face-review', 'cv/face-review', NULL, 1, 0, 'C', '0', '0', 'cv:face:review', 'form', 103, 1, NOW(), NULL, NULL, '考勤人脸 FAIL 记录 24h 复核'),
-- 底库按钮
(6185, '底库注册', 6183, 1, '', '', '', 1, 0, 'F', '0', '0', 'cv:face:register', '#', 103, 1, NOW(), NULL, NULL, '注册/重新注册/批量注册'),
(6186, '底库删除', 6183, 2, '', '', '', 1, 0, 'F', '0', '0', 'cv:face:remove', '#', 103, 1, NOW(), NULL, NULL, '删除底库条目'),
-- FAIL 复查按钮
(6187, '复查误判', 6184, 1, '', '', '', 1, 0, 'F', '0', '0', 'sch:attendance:review', '#', 103, 1, NOW(), NULL, NULL, '标记误判(PASS)/代打卡(FAIL)');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'cv:%' OR menu_id BETWEEN 6182 AND 6190 ORDER BY menu_id;
