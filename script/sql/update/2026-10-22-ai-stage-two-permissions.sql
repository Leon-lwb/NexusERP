-- AI第二阶段权限；仅系统master，未执行本地业务库，不自动授权角色。
START TRANSACTION;
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,create_dept,create_by,create_time,update_by,update_time,remark)
SELECT 1793,'预算管理',1790,3,'','','',1,0,'F','0','0','ai:tokenUsage:budget','#',103,1,NOW(),NULL,NULL,'AI第二阶段独立权限'
FROM sys_menu p WHERE p.menu_id=1790 AND p.parent_id=1713 AND p.menu_type='C' AND p.status='0'
AND BINARY p.path=BINARY 'tokenUsage' AND BINARY p.component=BINARY 'ai/tokenUsage/index' AND BINARY p.perms=BINARY 'ai:tokenUsage:list'
AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id=1793 OR BINARY m.perms=BINARY 'ai:tokenUsage:budget');
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,create_dept,create_by,create_time,update_by,update_time,remark)
SELECT 1794,'调用对账',1790,4,'','','',1,0,'F','0','0','ai:tokenUsage:reconcile','#',103,1,NOW(),NULL,NULL,'AI第二阶段独立权限'
FROM sys_menu p WHERE p.menu_id=1790 AND p.parent_id=1713 AND p.menu_type='C' AND p.status='0'
AND BINARY p.path=BINARY 'tokenUsage' AND BINARY p.component=BINARY 'ai/tokenUsage/index' AND BINARY p.perms=BINARY 'ai:tokenUsage:list'
AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id=1794 OR BINARY m.perms=BINARY 'ai:tokenUsage:reconcile');
COMMIT;
SELECT menu_id,parent_id,menu_type,status,perms FROM sys_menu WHERE menu_id IN (1793,1794);
