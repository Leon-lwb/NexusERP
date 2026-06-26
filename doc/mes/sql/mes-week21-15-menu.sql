-- =============================================
-- NexusERP - MES模块 Week 21 菜单权限清理SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 修正 Week17 遗留的权限字符串错配 + 删除冗余按钮
-- 创建时间: 2026-06-19
-- =============================================
USE `ry-vue`;

-- 1. 修正 MES工单页面(4010)权限字符串, 对齐 Controller mes:pro:work:list
UPDATE `sys_menu` SET `perms` = 'mes:pro:work:list'
WHERE `menu_id` = 4010 AND `perms` = 'mes:workorder:list';

-- 2. 删除 Week17 冗余的 mes:workorder:* 按钮权限(已被 Week18 的 4018~4023 替代)
DELETE FROM `sys_menu`
WHERE `menu_id` IN (4011, 4012, 4013, 4014, 4015, 4016, 4017);

-- 3. 同步清理角色-菜单关联表中 4011~4017 的残留分配
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (4011, 4012, 4013, 4014, 4015, 4016, 4017);

-- 4. 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4010 AND 4024 ORDER BY menu_id;
-- 预期: 4010(mes:pro:work:list) + 4018~4024(mes:pro:work:*) + 无 4011~4017
