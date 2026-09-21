-- =====================================================================================
-- 用途     ：IoT 运维前端页面（设备分组 / 设备标签 / 设备事件 / 下行指令 / 数据转发）
--            所需的 sys_dict 字典种子。
-- 对应报告 ：doc/audit/2026-09-20-fe-iot-ops.md
-- 执行前提 ：无。纯 DML（INSERT IGNORE），不 ALTER 任何存量表，无需停机窗口。
-- 影响库   ：ry-vue（sys_dict_type / sys_dict_data）
-- 可重复执行：是（全部 INSERT IGNORE，主键固定；重复执行不报错、不产生脏数据）
-- 需清 Redis ：是。RuoYi 的字典数据走 @Cacheable 缓存，插入后需刷新字典缓存，
--             否则前端 useDict 仍拿到旧值（表现为新字典项不显示）。
--             清理方式任选其一：
--               a) 前端「系统管理 → 字典管理 → 刷新缓存」；
--               b) redis-cli --scan --pattern 'sys_dict*' | xargs redis-cli DEL
-- 备注     ：菜单/权限码（menu_id 6050-6093、6075）不属本脚本，已由后端批次
--             script/sql/update/2026-09-20-iot-device-ops.sql §9 提供（25 条 INSERT IGNORE），
--             合并 ry-vue.sql 时请以该脚本为准（见报告「主脚本合并清单」）。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. 字典类型（sys_dict_type）：新增 5 个 IoT 运维字典
--    dict_id 600204-600208 已核对空闲（ry-vue.sql 中 IoT 段为 600200-600203，下一段为 600301+）
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_type` VALUES (600204, '000000', 'IoT事件级别', 'iot_event_level', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT设备事件级别: INFO/WARN/ERROR/CRITICAL');
INSERT IGNORE INTO `sys_dict_type` VALUES (600205, '000000', 'IoT事件处理状态', 'iot_event_process_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT设备事件处理状态: PENDING/PROCESSED/IGNORED/FAILED');
INSERT IGNORE INTO `sys_dict_type` VALUES (600206, '000000', 'IoT指令类型', 'iot_command_type', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT下行指令类型: PROPERTY_SET/SERVICE_INVOKE');
INSERT IGNORE INTO `sys_dict_type` VALUES (600207, '000000', 'IoT指令状态', 'iot_command_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT下行指令状态: PENDING/SENT/SUCCESS/FAILED/TIMEOUT');
INSERT IGNORE INTO `sys_dict_type` VALUES (600208, '000000', 'IoT转发投递状态', 'iot_forward_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT数据转发投递留痕状态: PENDING/SUCCESS/FAILED');

-- -------------------------------------------------------------------------------------
-- 2. 字典数据（sys_dict_data）：dict_code 600210-600228 已核对空闲
--    取值与后端白名单严格一致：
--      IotDeviceEvent.eventLevel / processStatus、IotDeviceCommand.commandType / status、
--      IotForwardLog.status、IotDeviceStatus(ACTIVE/INACTIVE/DECOMMISSIONED)
-- -------------------------------------------------------------------------------------

-- 2.1 IoT 事件级别 iot_event_level
INSERT IGNORE INTO `sys_dict_data` VALUES (600210, '000000', 1, '信息', 'INFO', 'iot_event_level', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '常规信息事件');
INSERT IGNORE INTO `sys_dict_data` VALUES (600211, '000000', 2, '警告', 'WARN', 'iot_event_level', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '警告级事件');
INSERT IGNORE INTO `sys_dict_data` VALUES (600212, '000000', 3, '错误', 'ERROR', 'iot_event_level', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '错误级事件(会联动告警记录)');
INSERT IGNORE INTO `sys_dict_data` VALUES (600213, '000000', 4, '严重', 'CRITICAL', 'iot_event_level', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '严重级事件(会联动告警记录)');

-- 2.2 IoT 事件处理状态 iot_event_process_status
INSERT IGNORE INTO `sys_dict_data` VALUES (600214, '000000', 1, '待处理', 'PENDING', 'iot_event_process_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '入库初始状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (600215, '000000', 2, '已处理', 'PROCESSED', 'iot_event_process_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '人工标记已处理');
INSERT IGNORE INTO `sys_dict_data` VALUES (600216, '000000', 3, '已忽略', 'IGNORED', 'iot_event_process_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '人工标记忽略');
INSERT IGNORE INTO `sys_dict_data` VALUES (600217, '000000', 4, '处理失败', 'FAILED', 'iot_event_process_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '处理未能完成');

-- 2.3 IoT 指令类型 iot_command_type
INSERT IGNORE INTO `sys_dict_data` VALUES (600218, '000000', 1, '属性设置', 'PROPERTY_SET', 'iot_command_type', '', 'primary', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'property/set 下行');
INSERT IGNORE INTO `sys_dict_data` VALUES (600219, '000000', 2, '服务调用', 'SERVICE_INVOKE', 'iot_command_type', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'event/set 下行');

-- 2.4 IoT 指令状态 iot_command_status
INSERT IGNORE INTO `sys_dict_data` VALUES (600220, '000000', 1, '下发中', 'PENDING', 'iot_command_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已登记未下发');
INSERT IGNORE INTO `sys_dict_data` VALUES (600221, '000000', 2, '已下发', 'SENT', 'iot_command_status', '', 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已提交 Broker，等待设备回执');
INSERT IGNORE INTO `sys_dict_data` VALUES (600222, '000000', 3, '成功', 'SUCCESS', 'iot_command_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备回执成功');
INSERT IGNORE INTO `sys_dict_data` VALUES (600223, '000000', 4, '失败', 'FAILED', 'iot_command_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '发布失败或设备回执失败');
INSERT IGNORE INTO `sys_dict_data` VALUES (600224, '000000', 5, '超时', 'TIMEOUT', 'iot_command_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '预留：后端暂未自动置位 TIMEOUT(需定时扫描未回执指令)');

-- 2.5 IoT 转发投递状态 iot_forward_status
INSERT IGNORE INTO `sys_dict_data` VALUES (600225, '000000', 1, '待投递', 'PENDING', 'iot_forward_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '留痕已创建待投递');
INSERT IGNORE INTO `sys_dict_data` VALUES (600226, '000000', 2, '成功', 'SUCCESS', 'iot_forward_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '投递成功(2xx)');
INSERT IGNORE INTO `sys_dict_data` VALUES (600227, '000000', 3, '失败', 'FAILED', 'iot_forward_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '投递失败，按退避重试');

-- -------------------------------------------------------------------------------------
-- 3. 扩展现有字典 iot_device_status：补 DECOMMISSIONED（设备注销，IOT-DEV-01 新增的终态）
--    不新建 iot_device_lifecycle：设备生命周期与「设备状态」是同一字段(status)，
--    分两个字典会让同一列出现两套取值口径（前端设备列表/表单/运维详情统一用本字典）。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_data` VALUES (600228, '000000', 3, '已注销', 'DECOMMISSIONED', 'iot_device_status', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备注销(终态,不可恢复,MQTT 认证被拒)');

-- 同步修正字典类型备注（原备注只写了"启用/停用"，与新增的注销态不符）
UPDATE `sys_dict_type`
   SET remark = 'IoT设备状态: 启用(ACTIVE)/停用(INACTIVE)/已注销(DECOMMISSIONED,终态)'
 WHERE dict_type = 'iot_device_status'
   AND remark <> 'IoT设备状态: 启用(ACTIVE)/停用(INACTIVE)/已注销(DECOMMISSIONED,终态)';

-- =====================================================================================
-- 4. 校验 SQL（人工核对，非执行语句）
-- =====================================================================================
-- 4.1 字典类型（期望 5 行）
-- SELECT dict_id, dict_name, dict_type FROM `ry-vue`.`sys_dict_type`
--  WHERE dict_type IN ('iot_event_level','iot_event_process_status','iot_command_type','iot_command_status','iot_forward_status')
--  ORDER BY dict_id;
--
-- 4.2 字典数据（期望 19 行 = 4+4+2+5+3+1）
-- SELECT dict_code, dict_type, dict_label, dict_value, list_class, is_default FROM `ry-vue`.`sys_dict_data`
--  WHERE dict_code BETWEEN 600210 AND 600228 ORDER BY dict_code;
--
-- 4.3 设备状态字典（期望 3 行：ACTIVE/INACTIVE/DECOMMISSIONED）
-- SELECT dict_label, dict_value FROM `ry-vue`.`sys_dict_data`
--  WHERE dict_type = 'iot_device_status' ORDER BY dict_sort;
--
-- 4.4 前端页面所需权限码（期望 25 行；若为 0 行，说明后端批次脚本 §9 未执行，
--     非超管访问新页面会 403）
-- SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu`
--  WHERE menu_id BETWEEN 6050 AND 6093 OR menu_id = 6075 ORDER BY menu_id;
--
-- 4.5 前端组件路径与菜单 component 字段一致性校验（期望 5 行）
-- SELECT menu_id, path, component FROM `ry-vue`.`sys_menu`
--  WHERE component IN ('iot/deviceGroup/index','iot/deviceTag/index','iot/event/index',
--                      'iot/command/index','iot/forwardRule/index');
