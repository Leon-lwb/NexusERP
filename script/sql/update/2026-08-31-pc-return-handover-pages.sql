-- PC 建单页接入: 退料单/上料交接单 plus-ui 页面上线
-- 前置: 2026-08-30-pda-material-return-menu.sql(退料菜单 20210~20215, 此前未执行则先执行)、
--       2026-08-30-pda-material-handover-menu.sql(交接菜单 20216~20220, 已执行)。
-- 本脚本: 菜单从隐藏(1)改为可见(0)。目标库: 系统库 (sys_menu 所在库, 即 ry-vue)。
-- 页面组件: erp/wm/return/index + form、erp/wm/handover/index + form (plus-ui 已提交)。

-- ========== ry-vue ==========
UPDATE sys_menu SET visible = '0', remark = 'PC建单页已接入' WHERE menu_id = 20210; -- 生产退料
UPDATE sys_menu SET visible = '0', remark = 'PC建单页已接入' WHERE menu_id = 20216; -- 上料交接

-- 提示: 若 2026-08-30-pda-material-return-menu.sql 尚未执行, 先执行该脚本种子 20210~20215 菜单。
-- 菜单可见后需在 [系统管理-角色管理] 为相关角色勾选, 方可显示入口。
