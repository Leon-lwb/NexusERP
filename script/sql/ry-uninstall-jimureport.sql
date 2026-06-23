-- ===================================================================
-- 卸载脚本：移除 JimuReport（积木报表）模块
--
-- 说明：
--   因后端移除 ruoyi-report 模块后，JimuReport 自带的
--   org.jeecg.modules.jmreport.common.interceptor.JimuGlobalExceptionHandler
--   （@RestControllerAdvice，无 basePackages 限制，
--    并注册 @ExceptionHandler(Exception.class)）
--   会随依赖一并消失，避免其劫持全局异常响应。
--
--   本脚本负责清理数据库侧残留：
--     1) 删除 sys_menu 中积木报表目录与其子菜单（3020/3021/3022）
--     2) 删除 sys_role_menu 中对应权限关联（防止孤立引用）
--     3) 删除所有 jimu_* 物理表
--
-- 执行顺序：先删 sys_role_menu / sys_menu，再 DROP TABLE，避免外键依赖问题。
-- 幂等性：所有 DROP 均加 IF EXISTS，可重复执行。
-- ===================================================================

-- 1. 删除积木报表菜单及其子菜单的角色授权
DELETE FROM sys_role_menu WHERE menu_id IN (3020, 3021, 3022);

-- 2. 删除积木报表目录与子菜单
DELETE FROM sys_menu WHERE menu_id IN (3020, 3021, 3022);

-- 3. 删除积木报表相关物理表
DROP TABLE IF EXISTS `jimu_report_share`;
DROP TABLE IF EXISTS `jimu_report_sheet`;
DROP TABLE IF EXISTS `jimu_report_map`;
DROP TABLE IF EXISTS `jimu_report_link`;
DROP TABLE IF EXISTS `jimu_report_icon_lib`;
DROP TABLE IF EXISTS `jimu_report_ext_data`;
DROP TABLE IF EXISTS `jimu_report_export_log`;
DROP TABLE IF EXISTS `jimu_report_export_job`;
DROP TABLE IF EXISTS `jimu_report_db_param`;
DROP TABLE IF EXISTS `jimu_report_db_field`;
DROP TABLE IF EXISTS `jimu_report_db`;
DROP TABLE IF EXISTS `jimu_report_data_source`;
DROP TABLE IF EXISTS `jimu_report_category`;
DROP TABLE IF EXISTS `jimu_report`;
DROP TABLE IF EXISTS `jimu_dict_item`;
DROP TABLE IF EXISTS `jimu_dict`;