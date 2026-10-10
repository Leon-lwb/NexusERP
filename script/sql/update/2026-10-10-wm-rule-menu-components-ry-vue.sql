-- =====================================================
-- 脚本名称: 2026-10-10-wm-rule-menu-components-ry-vue.sql
-- 用途: 修复仓储策略中上架/拣货规则菜单组件目录大小写，避免动态路由加载空白。
-- 适用数据库: 系统数据源 ry-vue；执行前须核实当前连接与站点实际系统库一致。
-- 授权范围: 仅 sys_menu 的 2616、2621 两行 component；不改路由 path、层级、权限或业务数据。
-- 特性: ID/父菜单/类型/path/perms/旧 component 精确守卫，幂等，可重复执行。
-- 创建日期: 2026-10-10
-- 执行前: 备份两行旧 component，确认表为 InnoDB，预检两行属性匹配。
-- 执行后: 首次旧配置应共影响 2 行；回读确认其他字段未变，刷新页面或重新登录以重新获取路由。
--         不需要重启服务或清空 Redis；此处不包含服务启停、缓存全量清理或权限修改。
-- =====================================================

START TRANSACTION;

UPDATE `sys_menu`
SET `component` = 'erp/wm/putawayRule/index'
WHERE `menu_id` = 2616
  AND `parent_id` = 2966
  AND `menu_type` = 'C'
  AND BINARY `path` = 'putawayRule'
  AND BINARY `perms` = 'wm:putawayRule:list'
  AND BINARY `component` = 'erp/wm/putawayrule/index';

UPDATE `sys_menu`
SET `component` = 'erp/wm/pickingRule/index'
WHERE `menu_id` = 2621
  AND `parent_id` = 2966
  AND `menu_type` = 'C'
  AND BINARY `path` = 'pickingRule'
  AND BINARY `perms` = 'wm:pickingRule:list'
  AND BINARY `component` = 'erp/wm/pickingrule/index';

COMMIT;

SELECT `menu_id`, `menu_name`, `path`, `component`
FROM `sys_menu`
WHERE `menu_id` IN (2616, 2621)
ORDER BY `menu_id`;
