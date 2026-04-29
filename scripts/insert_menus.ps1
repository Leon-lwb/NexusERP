# Flowable菜单数据插入脚本
$mysqlCmd = "mysql -u root -proot ry-vue"

$menuData = @(
    # 流程案例目录
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20200, '流程案例', 0, 10, 'flowdemo', NULL, '', 1, 0, 'M', '0', '0', '', 'menu-example', 103, 1, NOW(), 1, NOW(), NULL);",

    # 流程分类
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20001, '流程分类', 20000, 0, 'category', 'workflow/category/index', '', 1, 0, 'C', '0', '0', 'workflow:category:list', 'nested', 103, 1, NOW(), 1, NOW(), '流程分类菜单');",

    # 流程模型
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20002, '流程模型', 20000, 3, 'model', 'workflow/model/index', '', 1, 0, 'C', '0', '0', 'workflow:model:list', 'component', 103, 1, NOW(), NULL, NULL, '流程模型菜单');",

    # 新建流程
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20003, '新建流程', 20000, 4, 'create', 'workflow/work/index', '', 1, 0, 'C', '0', '0', 'workflow:process:startList', 'guide', 103, 1, NOW(), NULL, NULL, '新建流程菜单');",

    # 我的流程
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20004, '我的流程', 20000, 5, 'own', 'workflow/work/own', '', 1, 0, 'C', '0', '0', 'workflow:process:ownList', 'cascader', 103, 1, NOW(), NULL, NULL, '我的流程菜单');",

    # 待办任务
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20005, '待办任务', 20000, 6, 'todo', 'workflow/work/todo', '', 1, 0, 'C', '0', '0', 'workflow:process:todoList', 'time-range', 103, 1, NOW(), NULL, NULL, '待办任务菜单');",

    # 待签任务
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20006, '待签任务', 20000, 7, 'claim', 'workflow/work/claim', '', 1, 0, 'C', '0', '0', 'workflow:process:claimList', 'checkbox', 103, 1, NOW(), NULL, NULL, '待签任务菜单');",

    # 已办任务
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20007, '已办任务', 20000, 8, 'finished', 'workflow/work/finished', '', 1, 0, 'C', '0', '0', 'workflow:process:finishedList', 'documentation', 103, 1, NOW(), NULL, NULL, '已办任务菜单');",

    # 抄送任务
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20008, '抄送任务', 20000, 9, 'copy', 'workflow/work/copy', '', 1, 0, 'C', '0', '0', 'workflow:process:copyList', 'peoples', 103, 1, NOW(), NULL, NULL, '抄送任务菜单');",

    # 流程部署按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20009, '流程部署', 20002, 9, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:deploy', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 发起流程按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20010, '发起流程', 20003, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:start', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 新建流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20011, '新建流程导出', 20003, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:startExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程详情按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20012, '流程详情', 20004, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:query', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程删除按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20013, '流程删除', 20004, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:remove', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程取消按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20014, '流程取消', 20004, 3, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:cancel', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 我的流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20015, '我的流程导出', 20004, 4, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:ownExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程办理按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20016, '流程办理', 20005, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:approval', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 待办流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20017, '待办流程导出', 20005, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:todoExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程签收按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20018, '流程签收', 20006, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:claim', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 待签流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20019, '待签流程导出', 20006, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:claimExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 流程撤回按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20020, '流程撤回', 20007, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:revoke', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 已办流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20021, '已办流程导出', 20007, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:finishedExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 抄送流程导出按钮
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20022, '抄送流程导出', 20008, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:copyExport', '#', 103, 1, NOW(), NULL, NULL, '');",

    # 单表流程示例
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20201, '单表流程', 20200, 1, 'demo', 'workflow/demo/index', '', 1, 0, 'C', '0', '0', 'workflow:demo:list', 'tree', 103, 1, NOW(), 1, NOW(), NULL);",

    # Online设计页面
    "INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark) VALUES (20101, 'Online设计', 20100, 2, 'design', 'online/design/index', '', 1, 0, 'C', '0', '0', NULL, 'log', 103, 1, NOW(), 1, NOW(), NULL);"
)

$count = 0
foreach ($sql in $menuData) {
    try {
        $output = Invoke-Expression "$mysqlCmd -e `"$sql`"" 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) {
            $count++
            Write-Host "Inserted record $($count + 2)"
        }
    } catch {
        Write-Host "Error inserting record: $_"
    }
}

Write-Host "`nTotal inserted: $count records"
