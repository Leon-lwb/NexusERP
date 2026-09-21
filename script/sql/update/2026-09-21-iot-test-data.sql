-- =============================================================================
-- 2026-09-21-iot-test-data.sql  —  IoT 模块真实可用测试数据（演示/联调）
-- -----------------------------------------------------------------------------
-- 用途  : 为 IoT 各页面补齐可用的演示数据，使 设备分组/标签/固件/升级任务/告警记录/
--         告警规则/通知留痕/设备事件/下行指令/数据转发/实时数据 等页面开箱即有内容。
-- 影响库: ry-iot（业务数据）、ry-vue（product_type 字典扩展）
-- 幂等性: 是。全部 INSERT IGNORE + 固定主键；数据订正带 WHERE 守卫。
-- 执行前提: 需先执行 ry-iot.sql（含 iot_product 5 行 / iot_device 24 行 / iot_alert_rule
--         7001-7002 两行），本脚本的分组/标签/固件/任务均以这些既有数据为引用基础。
-- Redis : 无需清理（不涉及字典类型变更；product_type 新增值建议清 sys_dict:* 或刷新字典）
-- 说明  : 本脚本是**演示/联调数据**，按仓库既有约定（见 doc/system/数据库增量变更记录.md
--         备注：工位机 QC 测试数据同样未并入主脚本）**不并入 ry-iot.sql 主初始化脚本**，
--         仅在需要演示环境时执行。生产环境请勿执行。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 0. 既有数据订正：iot_product.status 值域非法
-- -----------------------------------------------------------------------------
-- 问题: 主脚本 ry-iot.sql:138-142 的 5 行产品种子 status 写的是 '1'，而
--       iot_product.status 的值域是 ACTIVE/INACTIVE（列注释、IotProduct/IotProductVo/
--       IotProductBo 的 javadoc 一致），前端产品页用 iot_device_status 字典渲染、
--       表单默认值也是 'ACTIVE' → 列表「状态」列显示原始码 1，无标签、无法筛选。
-- 处置: 订正为 ACTIVE（主脚本 ry-iot.sql 已同步修正）。
-- =============================================================================
UPDATE `iot_product` SET `status` = 'ACTIVE' WHERE `status` = '1';

-- =============================================================================
-- 0.1 本脚本历史版本写错的值域订正（幂等；INSERT IGNORE 不会更新已存在的行，故必须显式 UPDATE）
-- -----------------------------------------------------------------------------
-- 缺陷① 告警级别用了 iot_event_level 字典的 CRITICAL：
--        iot_alert_rule.alert_level / iot_alert_record.alert_level 的合法域是
--        HIGH/MEDIUM/LOW（IotAlertRuleBo:94 的 @Pattern、前端告警页 ALERT_LEVEL_OPTIONS 一致），
--        **不含 CRITICAL**（CRITICAL 属「设备事件级别」iot_event_level 的值域，两者不可混用）。
--        影响: 规则 8704、告警记录 8802（DEV0013 熔炼炉超温）。
--        处置: CRITICAL → HIGH（该规则本就是最高优先级，映射到告警域的最高级别）。
-- 缺陷② 告警类型误填 'THRESHOLD'（把「触发机制」当成字段语义）：
--        alert_type 的合法域是 TEMPERATURE/VIBRATION/PRESSURE/OTHER（被测物理量类别），
--        影响全部 6 条规则与 10 条告警记录。处置: 按 property_code 映射
--        temperature→TEMPERATURE、vibration→VIBRATION、power/speed/fail_rate→OTHER。
-- 注意: 这两条订正只针对本脚本产生的行（rule_id 8701-8706 / alert_id 8801-8810），
--       不会触碰既有 7001/7002 与业务真实产生的告警。
-- =============================================================================
UPDATE `iot_alert_rule` SET `alert_level` = 'HIGH'
 WHERE `rule_id` = 8704 AND `alert_level` = 'CRITICAL';
UPDATE `iot_alert_record` SET `alert_level` = 'HIGH'
 WHERE `alert_id` = 8802 AND `alert_level` = 'CRITICAL';

UPDATE `iot_alert_rule` SET `alert_type` = CASE `property_code`
         WHEN 'temperature' THEN 'TEMPERATURE'
         WHEN 'vibration'   THEN 'VIBRATION'
         ELSE 'OTHER' END
 WHERE `rule_id` BETWEEN 8701 AND 8706 AND `alert_type` NOT IN ('TEMPERATURE','VIBRATION','PRESSURE','OTHER');
UPDATE `iot_alert_record` SET `alert_type` = CASE `property_code`
         WHEN 'temperature' THEN 'TEMPERATURE'
         WHEN 'vibration'   THEN 'VIBRATION'
         ELSE 'OTHER' END
 WHERE `alert_id` BETWEEN 8801 AND 8810 AND `alert_type` NOT IN ('TEMPERATURE','VIBRATION','PRESSURE','OTHER');

-- 兜底自检（执行后应返回 0 行；非 0 说明仍有值域外的行）
-- SELECT 'bad_alert_level' AS chk, rule_id AS id FROM iot_alert_rule   WHERE alert_level NOT IN ('HIGH','MEDIUM','LOW')
-- UNION ALL SELECT 'bad_alert_level', alert_id FROM iot_alert_record WHERE alert_level NOT IN ('HIGH','MEDIUM','LOW')
-- UNION ALL SELECT 'bad_alert_type',  rule_id FROM iot_alert_rule    WHERE alert_type  NOT IN ('TEMPERATURE','VIBRATION','PRESSURE','OTHER')
-- UNION ALL SELECT 'bad_alert_type',  alert_id FROM iot_alert_record WHERE alert_type  NOT IN ('TEMPERATURE','VIBRATION','PRESSURE','OTHER');

-- 缺陷③ 通知渠道用了不存在的 EMAIL：
--        IotAlertNotifier 只实现了 **SSE 与 MES_ANDON 两个渠道**（IotAlertConstants.CHANNEL_*、
--        ChannelOutcome javadoc「渠道: SSE/MES_ANDON」、notify() 只调 notifySse/notifyAndon），
--        投递结果也只有 SUCCESS/FAILED/SKIPPED 三态。
--        影响: 规则 8702/8705 的 notify_channels 含 EMAIL；留痕 8908/8910 的 channel='EMAIL'。
--        处置: 渠道收敛为 SSE/MES_ANDON；原「邮件失败」示例改用 MES 安灯失败（真实可发生的失败路径），
--              并补 SKIPPED 态示例（无同租户在线用户 / MES 未接入），使三态齐备。
--        另注: notify_channels 字段目前在服务端**仅被默认赋值、未被消费**
--              （IotAlertRuleServiceImpl:250 仅在为空时置 'SSE,MES_ANDON'，通知器始终走 SSE+MES_ANDON），
--              属"声明未实现"字段，已在报告中登记，本次不改生产行为。
-- =============================================================================
UPDATE `iot_alert_rule` SET `notify_channels` = 'SSE,MES_ANDON'
 WHERE `rule_id` IN (8702, 8705) AND `notify_channels` <> 'SSE,MES_ANDON';
UPDATE `iot_alert_notify_log` SET `channel` = 'MES_ANDON', `target` = NULL,
       `message` = '联动失败：MES 安灯服务不可达'
 WHERE `log_id` IN (8908, 8910) AND `channel` = 'EMAIL';
UPDATE `iot_alert_notify_log` SET `message` = '已生成安灯呼叫(最高级别)'
 WHERE `log_id` = 8904 AND `message` LIKE '%CRITICAL%';

-- =============================================================================
-- 1. product_type 字典扩展（库 ry-vue）
-- -----------------------------------------------------------------------------
-- 问题: iot_product.product_type 既有数据是 SENSOR/METER/CAMERA，而 product_type
--       字典只有 DEVICE/GATEWAY → 产品页「产品类型」列显示原始码。
--       产品类型字段无任何代码逻辑依赖（仅存储+展示），故采用**扩展字典**而非改写数据
--       （保留传感器/仪表/相机这类更有信息量的分类）。
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600280, '000000', 3, '传感器', 'SENSOR', 'product_type', '', 'primary', 'N', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT产品类型: 传感器类采集模组');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600281, '000000', 4, '仪表', 'METER', 'product_type', '', 'success', 'N', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT产品类型: 电能表等计量仪表');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600282, '000000', 5, '相机', 'CAMERA', 'product_type', '', 'warning', 'N', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT产品类型: 工业视觉相机');

-- =============================================================================
-- 2. 设备分组树（3 层，与 iot_device.location 的实际车间对应）
--    ancestors 语义: 祖级列表逗号分隔、不含自身、根为 '0'
-- =============================================================================
INSERT IGNORE INTO `iot_device_group`
  (`group_id`,`tenant_id`,`parent_id`,`ancestors`,`group_code`,`group_name`,`order_num`,`status`,`remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (8101,'000000',0,'0','GRP-PLANT1','第一制造部',1,'0','机加与焊接车间',103,1,NOW()),
 (8102,'000000',8101,'0,8101','GRP-WS-MACHINING','机加车间',1,'0','数控车床/加工中心/铣床/磨床',103,1,NOW()),
 (8103,'000000',8101,'0,8101','GRP-WS-WELDING','焊接车间',2,'0','氩弧焊/二保焊/点焊/打磨',103,1,NOW()),
 (8104,'000000',0,'0','GRP-PLANT2','第二制造部',2,'0','注塑/铸造/热处理/电子/装配/包装',103,1,NOW()),
 (8105,'000000',8104,'0,8104','GRP-WS-INJECTION','注塑车间',1,'0','注塑机 A/B/C',103,1,NOW()),
 (8106,'000000',8104,'0,8104','GRP-WS-CASTHEAT','铸造与热处理车间',2,'0','含铸造、热处理两个工段',103,1,NOW()),
 (8107,'000000',8106,'0,8104,8106','GRP-SEC-CASTING','铸造工段',1,'0','熔炼炉',103,1,NOW()),
 (8108,'000000',8106,'0,8104,8106','GRP-SEC-HEAT','热处理工段',2,'0','淬火/回火/渗碳炉',103,1,NOW()),
 (8109,'000000',8104,'0,8104','GRP-WS-ELECTRONIC','电子车间',3,'0','SMT 贴片线',103,1,NOW()),
 (8110,'000000',8104,'0,8104','GRP-WS-ASSEMBLY','装配车间',4,'0','装配线 A/B',103,1,NOW()),
 (8111,'000000',8104,'0,8104','GRP-WS-TESTPACK','测试与包装车间',5,'0','含测试、包装两个工段',103,1,NOW()),
 (8112,'000000',8111,'0,8104,8111','GRP-SEC-TEST','测试工段',1,'0','功能测试/气密测试',103,1,NOW()),
 (8113,'000000',8111,'0,8104,8111','GRP-SEC-PACK','包装工段',2,'0','包装线 A/B/打包',103,1,NOW());

-- =============================================================================
-- 3. 设备标签
-- =============================================================================
INSERT IGNORE INTO `iot_device_tag`
  (`tag_id`,`tenant_id`,`tag_name`,`tag_color`,`remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (8201,'000000','关键设备','#F56C6C','停机会影响交付，优先保障',103,1,NOW()),
 (8202,'000000','计量设备','#E6A23C','需按期检定/校准',103,1,NOW()),
 (8203,'000000','安全监控','#409EFF','涉及安全联锁，禁止随意断连',103,1,NOW()),
 (8204,'000000','高温工位','#F56C6C','存在高温风险，巡检重点',103,1,NOW()),
 (8205,'000000','待检修','#909399','已提报检修计划',103,1,NOW()),
 (8206,'000000','新投产','#67C23A','近 30 天内投用',103,1,NOW());

-- =============================================================================
-- 4. 设备-分组关系（按 iot_device.location 映射到对应工段/车间）
-- =============================================================================
INSERT IGNORE INTO `iot_device_group_rel` (`rel_id`,`tenant_id`,`device_id`,`group_id`,`create_time`) VALUES
 (8301,'000000', 1,8102,NOW()),(8302,'000000', 2,8102,NOW()),(8303,'000000', 3,8102,NOW()),
 (8304,'000000', 4,8102,NOW()),(8305,'000000', 5,8102,NOW()),
 (8306,'000000', 6,8103,NOW()),(8307,'000000', 7,8103,NOW()),(8308,'000000', 8,8103,NOW()),
 (8309,'000000', 9,8103,NOW()),
 (8310,'000000',10,8105,NOW()),(8311,'000000',11,8105,NOW()),(8312,'000000',12,8105,NOW()),
 (8313,'000000',13,8107,NOW()),
 (8314,'000000',14,8108,NOW()),(8315,'000000',15,8108,NOW()),(8316,'000000',16,8108,NOW()),
 (8317,'000000',17,8109,NOW()),
 (8318,'000000',18,8110,NOW()),(8319,'000000',19,8110,NOW()),
 (8320,'000000',20,8112,NOW()),(8321,'000000',21,8112,NOW()),
 (8322,'000000',22,8113,NOW()),(8323,'000000',23,8113,NOW()),(8324,'000000',24,8113,NOW());

-- =============================================================================
-- 5. 设备-标签关系
-- =============================================================================
INSERT IGNORE INTO `iot_device_tag_rel` (`rel_id`,`tenant_id`,`device_id`,`tag_id`,`create_time`) VALUES
 -- 关键设备
 (8401,'000000', 1,8201,NOW()),(8402,'000000', 2,8201,NOW()),(8403,'000000', 3,8201,NOW()),
 (8404,'000000',10,8201,NOW()),(8405,'000000',11,8201,NOW()),(8406,'000000',13,8201,NOW()),
 (8407,'000000',17,8201,NOW()),(8408,'000000',18,8201,NOW()),(8409,'000000',19,8201,NOW()),
 -- 计量设备
 (8410,'000000',17,8202,NOW()),(8411,'000000',20,8202,NOW()),(8412,'000000',21,8202,NOW()),
 (8413,'000000',22,8202,NOW()),(8414,'000000',23,8202,NOW()),(8415,'000000',24,8202,NOW()),
 -- 安全监控
 (8416,'000000', 6,8203,NOW()),(8417,'000000', 7,8203,NOW()),(8418,'000000', 8,8203,NOW()),
 (8419,'000000',13,8203,NOW()),(8420,'000000',14,8203,NOW()),(8421,'000000',15,8203,NOW()),
 (8422,'000000',16,8203,NOW()),
 -- 高温工位
 (8423,'000000',13,8204,NOW()),(8424,'000000',14,8204,NOW()),(8425,'000000',15,8204,NOW()),
 (8426,'000000',16,8204,NOW()),
 -- 待检修
 (8427,'000000', 9,8205,NOW()),(8428,'000000',12,8205,NOW()),
 -- 新投产
 (8429,'000000',22,8206,NOW()),(8430,'000000',23,8206,NOW()),(8431,'000000',24,8206,NOW());

-- =============================================================================
-- 6. 固件登记（product_key 必须等于 iot_product.product_code；唯一键含 product_key+version）
-- =============================================================================
INSERT IGNORE INTO `iot_firmware`
  (`firmware_id`,`tenant_id`,`firmware_name`,`product_key`,`hardware_model`,`version`,`file_oss_id`,`file_name`,`file_size`,`checksum_type`,`checksum`,`status`,`release_time`,`change_log`,`create_dept`,`create_by`,`create_time`) VALUES
 (8501,'000000','转速传感器固件','SPEED-SENSOR','HW-SPD-01','V1.1.0','oss-fw-1001','speed-sensor-v1.1.0.bin',524288,'MD5','9f2c4b7e1a6d3850c4e7b91f2a6d3850','RELEASED',NOW() - INTERVAL 12 DAY,'增加转速突变告警阈值配置项',103,1,NOW() - INTERVAL 15 DAY),
 (8502,'000000','转速传感器固件','SPEED-SENSOR','HW-SPD-01','V1.2.0','oss-fw-1002','speed-sensor-v1.2.0.bin',536576,'MD5','3a7e5c9b2f8d1460e5a8c02b3f8d1460','DRAFT',NULL,'优化采样滤波算法(待评审)',103,1,NOW() - INTERVAL 3 DAY),
 (8503,'000000','温度传感器固件','TEMP-SENSOR','HW-TMP-02','V2.0.0','oss-fw-1003','temp-sensor-v2.0.0.bin',786432,'SHA256','5c1d8e3f7a2b94061f4d7c0a5e2b94061f4d7c0a5e2b94061f4d7c0a5e2b9406','RELEASED',NOW() - INTERVAL 20 DAY,'支持 -40~1500℃ 宽量程与断线自检',103,1,NOW() - INTERVAL 25 DAY),
 (8504,'000000','振动传感器固件','VIB-SENSOR','HW-VIB-01','V1.1.0','oss-fw-1004','vib-sensor-v1.1.0.bin',458752,'MD5','7d3f6a1c8e4b25039d6f8a1c7e4b2503','RELEASED',NOW() - INTERVAL 8 DAY,'提升高频振动采样率至 20kHz',103,1,NOW() - INTERVAL 10 DAY),
 (8505,'000000','电能表固件','POWER-METER','HW-PWR-03','V1.1.0','oss-fw-1005','power-meter-v1.1.0.bin',393216,'MD5','2b8e4d7a1f5c36902e7b9d4a8f5c3690','RELEASED',NOW() - INTERVAL 6 DAY,'支持分时电量统计与需量记录',103,1,NOW() - INTERVAL 7 DAY),
 (8506,'000000','工业视觉相机固件','VISION-CAM','HW-CAM-05','V2.1.0','oss-fw-1006','vision-cam-v2.1.0.bin',3145728,'SHA256','a4f7c2e9b6d18305f2a9c6e3b7d18305f2a9c6e3b7d18305f2a9c6e3b7d18305','DISABLED',NOW() - INTERVAL 30 DAY,'新增 OCR 模板匹配(因稳定性问题已停用)',103,1,NOW() - INTERVAL 32 DAY),
 (8507,'000000','转速传感器固件','SPEED-SENSOR','HW-SPD-01','V1.0.0','oss-fw-1000','speed-sensor-v1.0.0.bin',516096,'MD5','1e6b3a8d5c2f97041e6b3a8d5c2f9704','DISABLED',NOW() - INTERVAL 40 DAY,'首版固件(已由 V1.1.0 替代)',103,1,NOW() - INTERVAL 45 DAY);

-- =============================================================================
-- 7. 下行指令（OTA 指令 + 属性设置；uk_ack_msg_id 唯一）
-- =============================================================================
INSERT IGNORE INTO `iot_device_command`
  (`command_id`,`tenant_id`,`device_id`,`device_code`,`command_type`,`topic`,`payload`,`status`,`retry_count`,`sent_time`,`ack_time`,`ack_payload`,`ack_msg_id`,`result_message`,`operator_id`,`operator_name`,`create_dept`,`create_by`,`create_time`) VALUES
 (9101,'000000', 1,'DEV0001','SERVICE_INVOKE','iot/000000/DEV0001/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','SUCCESS',0,NOW() - INTERVAL 200 MINUTE,NOW() - INTERVAL 199 MINUTE,'{"success":true}','msg-9101','设备已收到升级指令',1,'admin',103,1,NOW() - INTERVAL 200 MINUTE),
 (9102,'000000', 2,'DEV0002','SERVICE_INVOKE','iot/000000/DEV0002/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','FAILED',1,NOW() - INTERVAL 180 MINUTE,NULL,NULL,NULL,'设备回执升级失败: 校验值不匹配',1,'admin',103,1,NOW() - INTERVAL 180 MINUTE),
 (9103,'000000', 3,'DEV0003','SERVICE_INVOKE','iot/000000/DEV0003/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','SENT',0,NOW() - INTERVAL 25 MINUTE,NOW() - INTERVAL 24 MINUTE,'{"success":true}','msg-9103','设备已收到升级指令',1,'admin',103,1,NOW() - INTERVAL 25 MINUTE),
 (9104,'000000', 4,'DEV0004','SERVICE_INVOKE','iot/000000/DEV0004/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','PENDING',0,NULL,NULL,NULL,NULL,NULL,1,'admin',103,1,NOW() - INTERVAL 10 MINUTE),
 (9105,'000000',10,'DEV0010','PROPERTY_SET','iot/000000/DEV0010/property/set','{"temperature_limit":220}','SENT',0,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 59 MINUTE,'{"success":true}','msg-9105','参数已下发',1,'admin',103,1,NOW() - INTERVAL 60 MINUTE),
 (9106,'000000',17,'DEV0017','PROPERTY_SET','iot/000000/DEV0017/property/set','{"power_limit":95}','SUCCESS',0,NOW() - INTERVAL 150 MINUTE,NOW() - INTERVAL 149 MINUTE,'{"success":true}','msg-9106','参数已生效',1,'admin',103,1,NOW() - INTERVAL 150 MINUTE),
 (9107,'000000',20,'DEV0020','PROPERTY_SET','iot/000000/DEV0020/property/set','{"sample_interval":5}','SUCCESS',0,NOW() - INTERVAL 90 MINUTE,NOW() - INTERVAL 89 MINUTE,'{"success":true}','msg-9107','采样周期已设为 5s',1,'admin',103,1,NOW() - INTERVAL 90 MINUTE),
 (9108,'000000',21,'DEV0021','PROPERTY_SET','iot/000000/DEV0021/property/set','{"sample_interval":5}','SENT',0,NOW() - INTERVAL 88 MINUTE,NULL,NULL,NULL,NULL,1,'admin',103,1,NOW() - INTERVAL 88 MINUTE),
 (9109,'000000',22,'DEV0022','PROPERTY_SET','iot/000000/DEV0022/property/set','{"sample_interval":10}','PENDING',0,NULL,NULL,NULL,NULL,NULL,1,'admin',103,1,NOW() - INTERVAL 5 MINUTE),
 (9110,'000000',20,'DEV0020','SERVICE_INVOKE','iot/000000/DEV0020/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','SUCCESS',0,NOW() - INTERVAL 120 MINUTE,NOW() - INTERVAL 119 MINUTE,'{"success":true}','msg-9110','设备已收到升级指令',1,'admin',103,1,NOW() - INTERVAL 120 MINUTE),
 (9111,'000000',21,'DEV0021','SERVICE_INVOKE','iot/000000/DEV0021/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','SENT',0,NOW() - INTERVAL 70 MINUTE,NOW() - INTERVAL 69 MINUTE,'{"success":true}','msg-9111','设备已收到升级指令',1,'admin',103,1,NOW() - INTERVAL 70 MINUTE),
 (9112,'000000',22,'DEV0022','SERVICE_INVOKE','iot/000000/DEV0022/event/set','{"service":"otaUpgrade","params":{"version":"V1.1.0"}}','PENDING',0,NULL,NULL,NULL,NULL,NULL,1,'admin',103,1,NOW() - INTERVAL 4 MINUTE),
 (9113,'000000', 5,'DEV0005','PROPERTY_SET','iot/000000/DEV0005/property/set','{"sample_rate":20000}','TIMEOUT',3,NOW() - INTERVAL 300 MINUTE,NULL,NULL,NULL,'超时未收到设备回执',1,'admin',103,1,NOW() - INTERVAL 300 MINUTE),
 (9114,'000000',18,'DEV0018','SERVICE_INVOKE','iot/000000/DEV0018/event/set','{"service":"reboot"}','SUCCESS',0,NOW() - INTERVAL 240 MINUTE,NOW() - INTERVAL 239 MINUTE,'{"success":true}','msg-9114','相机已重启',1,'admin',103,1,NOW() - INTERVAL 240 MINUTE),
 (9115,'000000', 9,'DEV0009','PROPERTY_SET','iot/000000/DEV0009/property/set','{"sample_rate":20000}','SUCCESS',0,NOW() - INTERVAL 200 MINUTE,NOW() - INTERVAL 199 MINUTE,'{"success":true}','msg-9115','参数已生效',1,'admin',103,1,NOW() - INTERVAL 200 MINUTE);

-- =============================================================================
-- 8. OTA 升级任务（覆盖 7 个状态；同设备+同固件不并存进行中任务）
-- =============================================================================
INSERT IGNORE INTO `iot_ota_task`
  (`task_id`,`tenant_id`,`firmware_id`,`target_version`,`current_version`,`device_id`,`device_code`,`group_id`,`batch_no`,`command_id`,`status`,`progress`,`retry_count`,`sent_time`,`start_time`,`finish_time`,`fail_reason`,`report_msg_id`,`operator_id`,`operator_name`,`remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (8601,'000000',8501,'V1.1.0','V1.0.0', 1,'DEV0001',8102,NULL,9101,'SUCCESS',100,0,NOW() - INTERVAL 200 MINUTE,NOW() - INTERVAL 198 MINUTE,NOW() - INTERVAL 190 MINUTE,NULL,'rep-8601',1,'admin','数控车床A 转速采集点升级',103,1,NOW() - INTERVAL 200 MINUTE),
 (8602,'000000',8501,'V1.1.0','V1.0.0', 2,'DEV0002',8102,NULL,9102,'FAILED',45,1,NOW() - INTERVAL 180 MINUTE,NOW() - INTERVAL 178 MINUTE,NOW() - INTERVAL 170 MINUTE,'固件包校验值不匹配，设备拒绝刷写','rep-8602',1,'admin','数控车床B 升级失败',103,1,NOW() - INTERVAL 180 MINUTE),
 (8603,'000000',8501,'V1.1.0','V1.0.0', 3,'DEV0003',8102,NULL,9103,'UPGRADING',60,0,NOW() - INTERVAL 25 MINUTE,NOW() - INTERVAL 20 MINUTE,NULL,NULL,'rep-8603',1,'admin','加工中心A 升级中',103,1,NOW() - INTERVAL 25 MINUTE),
 (8604,'000000',8501,'V1.1.0','V1.0.0', 4,'DEV0004',8102,NULL,9104,'SENT',0,0,NOW() - INTERVAL 10 MINUTE,NULL,NULL,NULL,NULL,1,'admin','铣床采集点 已下发待回执',103,1,NOW() - INTERVAL 10 MINUTE),
 (8605,'000000',8504,'V1.1.0','V1.0.0', 5,'DEV0005',8102,NULL,NULL,'TIMEOUT',0,0,NOW() - INTERVAL 400 MINUTE,NULL,NULL,'超时窗口内未收到任何回执','rep-8605',1,'admin','磨床采集点 升级超时(可重试)',103,1,NOW() - INTERVAL 400 MINUTE),
 (8606,'000000',8503,'V2.0.0','V1.0.0', 6,'DEV0006',8103,NULL,NULL,'SUCCESS',100,0,NOW() - INTERVAL 500 MINUTE,NOW() - INTERVAL 498 MINUTE,NOW() - INTERVAL 480 MINUTE,NULL,'rep-8606',1,'admin','氩弧焊工位 温度模组升级',103,1,NOW() - INTERVAL 500 MINUTE),
 (8607,'000000',8503,'V2.0.0','V1.0.0', 7,'DEV0007',8103,NULL,NULL,'CANCELLED',0,0,NOW() - INTERVAL 460 MINUTE,NULL,NOW() - INTERVAL 455 MINUTE,'人工取消: 该工位当日有生产任务','rep-8607',1,'admin','二保焊工位 升级已取消',103,1,NOW() - INTERVAL 460 MINUTE),
 (8608,'000000',8505,'V1.1.0','V1.0.0',17,'DEV0017',8109,NULL,NULL,'PENDING',0,0,NULL,NULL,NULL,NULL,NULL,1,'admin','SMT贴片线 电能表待下发',103,1,NOW() - INTERVAL 30 MINUTE),
 (8609,'000000',8506,'V2.1.0','V1.0.0',18,'DEV0018',8110,NULL,NULL,'FAILED',30,2,NOW() - INTERVAL 240 MINUTE,NOW() - INTERVAL 238 MINUTE,NOW() - INTERVAL 230 MINUTE,'相机固件已停用，刷写后自检不通过','rep-8609',1,'admin','装配线A 相机升级失败(已重试2次)',103,1,NOW() - INTERVAL 240 MINUTE),
 (8610,'000000',8505,'V1.1.0','V1.0.0',20,'DEV0020',8112,'OTA-B20260921-001',9110,'SUCCESS',100,0,NOW() - INTERVAL 120 MINUTE,NOW() - INTERVAL 118 MINUTE,NOW() - INTERVAL 110 MINUTE,NULL,'rep-8610',1,'admin','批量下发: 测试工段',103,1,NOW() - INTERVAL 120 MINUTE),
 (8611,'000000',8505,'V1.1.0','V1.0.0',21,'DEV0021',8112,'OTA-B20260921-001',9111,'UPGRADING',35,0,NOW() - INTERVAL 70 MINUTE,NOW() - INTERVAL 65 MINUTE,NULL,NULL,'rep-8611',1,'admin','批量下发: 测试工段',103,1,NOW() - INTERVAL 70 MINUTE),
 (8612,'000000',8505,'V1.1.0','V1.0.0',22,'DEV0022',8112,'OTA-B20260921-001',9112,'SENT',0,0,NOW() - INTERVAL 4 MINUTE,NULL,NULL,NULL,NULL,1,'admin','批量下发: 测试工段',103,1,NOW() - INTERVAL 4 MINUTE),
 (8613,'000000',8505,'V1.1.0','V1.0.0',23,'DEV0023',8112,'OTA-B20260921-001',NULL,'PENDING',0,0,NULL,NULL,NULL,NULL,NULL,1,'admin','批量下发: 测试工段(部分成功语义下待下发)',103,1,NOW() - INTERVAL 4 MINUTE);

-- 升级成功的设备回写固件版本（与「升级成功回写 iot_device.firmware_version」语义一致）
UPDATE `iot_device` SET `firmware_version` = 'V1.1.0' WHERE `device_id` IN (1, 20);
UPDATE `iot_device` SET `firmware_version` = 'V2.0.0' WHERE `device_id` = 6;

-- =============================================================================
-- 9. 告警规则（避开既有 7001/7002 与 uk_rule_scope(tenant_id,product_id,device_id,property_code)）
-- =============================================================================
INSERT IGNORE INTO `iot_alert_rule`
  (`rule_id`,`tenant_id`,`rule_name`,`product_id`,`device_id`,`property_code`,`compare_operator`,`threshold`,`duration_sec`,`debounce_sec`,`alert_type`,`alert_level`,`notify_channels`,`enabled`,`remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (8701,'000000','注塑机料筒温度超上限',1,0,'temperature','GT',220.0000,30,60,'TEMPERATURE','HIGH','SSE,MES_ANDON',1,'温度模组作用于注塑车间',103,1,NOW()),
 (8702,'000000','电能表功率超载',3,0,'power','GT',95.0000,60,120,'OTHER','HIGH','SSE,MES_ANDON',1,'持续 60s 才触发，避免瞬时冲击误报',103,1,NOW()),
 (8703,'000000','转速传感器转速过低',4,0,'speed','LT',500.0000,120,300,'OTHER','MEDIUM','SSE',1,'疑似停机或传动打滑',103,1,NOW()),
 (8704,'000000','熔炼炉温度超限',1,13,'temperature','GT',1500.0000,10,30,'TEMPERATURE','HIGH','SSE,MES_ANDON',1,'设备级规则，优先级高于全局规则',103,1,NOW()),
 (8705,'000000','视觉相机识别失败率超限',5,0,'fail_rate','GT',5.0000,300,300,'OTHER','MEDIUM','SSE,MES_ANDON',1,'按 5 分钟窗口统计',103,1,NOW()),
 (8706,'000000','机加车间振动烈度预警',2,0,'vibration','GT',3.5000,60,120,'VIBRATION','LOW','SSE',1,'低于全局阈值 4.5，用于提前预警',103,1,NOW());

-- =============================================================================
-- 10. 告警记录（遵守 uk_active_alert(tenant_id,device_id,property_code,active_flag)：
--     同一设备+属性只允许一条 active_flag=1；已恢复记录 active_flag 置 NULL）
-- =============================================================================
INSERT IGNORE INTO `iot_alert_record`
  (`alert_id`,`tenant_id`,`rule_id`,`source`,`device_id`,`device_code`,`product_id`,`property_code`,`alert_type`,`alert_level`,`compare_operator`,`threshold`,`trigger_value`,`alert_message`,`status`,`active_flag`,`trigger_time`,`last_trigger_time`,`ack_time`,`ack_by`,`ack_remark`,`resolve_time`,`resolve_by`,`resolve_value`,`resolve_remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (8801,'000000',8701,'RULE', 6,'DEV0006',1,'temperature','TEMPERATURE','HIGH','GT',220.0000,235.0000,'氩弧焊工位温度 235℃ 超过阈值 220℃','ACTIVE',1,NOW() - INTERVAL 35 MINUTE,NOW() - INTERVAL 5 MINUTE,NULL,NULL,NULL,NULL,NULL,NULL,NULL,103,1,NOW() - INTERVAL 35 MINUTE),
 (8802,'000000',8704,'RULE',13,'DEV0013',1,'temperature','TEMPERATURE','HIGH','GT',1500.0000,1582.0000,'熔炼炉温度 1582℃ 超过阈值 1500℃','ACTIVE',1,NOW() - INTERVAL 18 MINUTE,NOW() - INTERVAL 2 MINUTE,NULL,NULL,NULL,NULL,NULL,NULL,NULL,103,1,NOW() - INTERVAL 18 MINUTE),
 (8803,'000000',8706,'RULE', 9,'DEV0009',2,'vibration','VIBRATION','LOW','GT',3.5000,4.1000,'打磨工位振动烈度 4.1 超过预警阈值 3.5','ACKNOWLEDGED',1,NOW() - INTERVAL 150 MINUTE,NOW() - INTERVAL 140 MINUTE,NOW() - INTERVAL 130 MINUTE,1,'已通知设备科现场确认，轴承待更换',NULL,NULL,NULL,NULL,103,1,NOW() - INTERVAL 150 MINUTE),
 (8804,'000000',7002,'RULE', 5,'DEV0005',2,'vibration','VIBRATION','MEDIUM','GT',4.5000,5.2000,'磨床振动烈度 5.2 超过阈值 4.5','RESOLVED',NULL,NOW() - INTERVAL 26 HOUR,NOW() - INTERVAL 25 HOUR,NOW() - INTERVAL 24 HOUR,1,'已停机检查',NOW() - INTERVAL 23 HOUR,1,2.8000,'更换主轴轴承后恢复',103,1,NOW() - INTERVAL 26 HOUR),
 (8805,'000000',8702,'RULE',17,'DEV0017',3,'power','OTHER','HIGH','GT',95.0000,102.5000,'SMT贴片线功率 102.5kW 超过阈值 95kW','RESOLVED',NULL,NOW() - INTERVAL 30 HOUR,NOW() - INTERVAL 29 HOUR,NOW() - INTERVAL 28 HOUR,1,'调整产线负载',NOW() - INTERVAL 27 HOUR,1,88.0000,'分流部分设备后恢复',103,1,NOW() - INTERVAL 30 HOUR),
 (8806,'000000',8702,'RULE',20,'DEV0020',3,'power','OTHER','HIGH','GT',95.0000,99.8000,'功能测试工位功率 99.8kW 超过阈值 95kW','ACTIVE',1,NOW() - INTERVAL 8 MINUTE,NOW() - INTERVAL 3 MINUTE,NULL,NULL,NULL,NULL,NULL,NULL,NULL,103,1,NOW() - INTERVAL 8 MINUTE),
 (8807,'000000',8705,'RULE',18,'DEV0018',5,'fail_rate','OTHER','MEDIUM','GT',5.0000,7.3000,'装配线A 相机识别失败率 7.3% 超过阈值 5%','RESOLVED',NULL,NOW() - INTERVAL 50 HOUR,NOW() - INTERVAL 49 HOUR,NOW() - INTERVAL 48 HOUR,1,'清洁镜头并重标定',NOW() - INTERVAL 47 HOUR,1,1.2000,'重新标定后失败率下降',103,1,NOW() - INTERVAL 50 HOUR),
 (8808,'000000',7001,'RULE',14,'DEV0014',1,'temperature','TEMPERATURE','HIGH','GT',80.0000,96.4000,'淬火炉温度 96.4℃ 超过阈值 80℃','RESOLVED',NULL,NOW() - INTERVAL 60 HOUR,NOW() - INTERVAL 59 HOUR,NOW() - INTERVAL 58 HOUR,1,'确认工艺设定',NOW() - INTERVAL 57 HOUR,1,72.0000,'属工艺正常波动，已调整阈值',103,1,NOW() - INTERVAL 60 HOUR),
 (8809,'000000',8703,'RULE', 1,'DEV0001',4,'speed','OTHER','MEDIUM','LT',500.0000,320.0000,'数控车床A 转速 320rpm 低于阈值 500rpm','RESOLVED',NULL,NOW() - INTERVAL 70 HOUR,NOW() - INTERVAL 69 HOUR,NOW() - INTERVAL 68 HOUR,1,'确认为计划停机',NOW() - INTERVAL 67 HOUR,1,1450.0000,'换刀结束已恢复',103,1,NOW() - INTERVAL 70 HOUR),
 (8810,'000000',8702,'RULE',21,'DEV0021',3,'power','OTHER','HIGH','GT',95.0000,97.2000,'气密测试台功率 97.2kW 超过阈值 95kW','ACKNOWLEDGED',1,NOW() - INTERVAL 45 MINUTE,NOW() - INTERVAL 40 MINUTE,NOW() - INTERVAL 38 MINUTE,1,'已确认，等待批次结束再处理',NULL,NULL,NULL,NULL,103,1,NOW() - INTERVAL 45 MINUTE);

-- =============================================================================
-- 11. 告警通知留痕
-- =============================================================================
INSERT IGNORE INTO `iot_alert_notify_log`
  (`log_id`,`tenant_id`,`alert_id`,`channel`,`target`,`status`,`message`,`create_dept`,`create_by`,`create_time`) VALUES
 (8901,'000000',8801,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 35 MINUTE),
 (8902,'000000',8801,'MES_ANDON','焊接车间看板','SUCCESS','已生成安灯呼叫',103,1,NOW() - INTERVAL 35 MINUTE),
 (8903,'000000',8802,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 18 MINUTE),
 (8904,'000000',8802,'MES_ANDON','铸造车间看板','SUCCESS','已生成安灯呼叫(最高级别)',103,1,NOW() - INTERVAL 18 MINUTE),
 (8905,'000000',8803,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 150 MINUTE),
 (8906,'000000',8804,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 26 HOUR),
 (8907,'000000',8805,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 30 HOUR),
 (8908,'000000',8805,'MES_ANDON',NULL,'FAILED','联动失败：MES 安灯服务不可达',103,1,NOW() - INTERVAL 30 HOUR),
 (8909,'000000',8806,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 8 MINUTE),
 (8910,'000000',8806,'MES_ANDON',NULL,'FAILED','联动失败：MES 安灯服务不可达',103,1,NOW() - INTERVAL 8 MINUTE),
 (8911,'000000',8807,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 50 HOUR),
 (8912,'000000',8808,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 60 HOUR),
 (8913,'000000',8809,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 70 HOUR),
 (8914,'000000',8810,'SSE','在线用户','SUCCESS','已推送至在线运维看板',103,1,NOW() - INTERVAL 45 MINUTE),
 -- SKIPPED 态（无同租户在线用户时告警仍已落库，留痕说明"待查"，不是丢失）
 (8915,'000000',8810,'SSE',NULL,'SKIPPED','无同租户在线用户，告警已落库待查',103,1,NOW() - INTERVAL 44 MINUTE),
 (8916,'000000',8803,'MES_ANDON',NULL,'SKIPPED','MES 未接入或无安灯实现',103,1,NOW() - INTERVAL 150 MINUTE);

-- =============================================================================
-- 12. 设备事件（覆盖 PENDING/PROCESSED/IGNORED/FAILED；uk_source_msg_id 唯一）
-- =============================================================================
INSERT IGNORE INTO `iot_device_event`
  (`event_id`,`tenant_id`,`device_id`,`device_code`,`product_id`,`event_code`,`event_name`,`event_level`,`event_time`,`receive_time`,`payload`,`quality`,`process_status`,`process_remark`,`alert_id`,`source`,`source_msg_id`,`create_time`) VALUES
 (9001,'000000', 6,'DEV0006',1,'TEMP_HIGH','温度超上限','WARN',NOW() - INTERVAL 35 MINUTE,NOW() - INTERVAL 35 MINUTE,'{"temperature":235}','GOOD','PENDING',NULL,8801,'MQTT','seed-ev-9001',NOW() - INTERVAL 35 MINUTE),
 (9002,'000000',13,'DEV0013',1,'FURNACE_OVERHEAT','熔炼炉超温','CRITICAL',NOW() - INTERVAL 18 MINUTE,NOW() - INTERVAL 18 MINUTE,'{"temperature":1582}','GOOD','PENDING',NULL,8802,'MQTT','seed-ev-9002',NOW() - INTERVAL 18 MINUTE),
 (9003,'000000', 9,'DEV0009',2,'VIBRATION_WARN','振动预警','WARN',NOW() - INTERVAL 150 MINUTE,NOW() - INTERVAL 150 MINUTE,'{"vibration":4.1}','GOOD','PROCESSED','已转维修工单 WO-20260921-003',8803,'MQTT','seed-ev-9003',NOW() - INTERVAL 150 MINUTE),
 (9004,'000000',17,'DEV0017',3,'POWER_OVERLOAD','功率超载','WARN',NOW() - INTERVAL 30 HOUR,NOW() - INTERVAL 30 HOUR,'{"power":102.5}','GOOD','IGNORED','生产高峰期短时冲击，已知悉',8805,'MQTT','seed-ev-9004',NOW() - INTERVAL 30 HOUR),
 (9005,'000000',20,'DEV0020',3,'POWER_OVERLOAD','功率超载','WARN',NOW() - INTERVAL 8 MINUTE,NOW() - INTERVAL 8 MINUTE,'{"power":99.8}','GOOD','PENDING',NULL,8806,'MQTT','seed-ev-9005',NOW() - INTERVAL 8 MINUTE),
 (9006,'000000',18,'DEV0018',5,'VISION_FAIL_RATE','识别失败率超限','ERROR',NOW() - INTERVAL 50 HOUR,NOW() - INTERVAL 50 HOUR,'{"failRate":7.3}','GOOD','PROCESSED','已清洁镜头并重标定',8807,'MQTT','seed-ev-9006',NOW() - INTERVAL 50 HOUR),
 (9007,'000000', 1,'DEV0001',4,'OTA_PROGRESS','固件升级进度','INFO',NOW() - INTERVAL 195 MINUTE,NOW() - INTERVAL 195 MINUTE,'{"otaTaskId":"8601","progress":60,"version":"V1.1.0"}','GOOD','PROCESSED','升级进度已记录',NULL,'MQTT','seed-ev-9007',NOW() - INTERVAL 195 MINUTE),
 (9008,'000000', 2,'DEV0002',4,'OTA_RESULT','固件升级结果','ERROR',NOW() - INTERVAL 170 MINUTE,NOW() - INTERVAL 170 MINUTE,'{"otaTaskId":"8602","success":false,"version":"V1.1.0"}','GOOD','PROCESSED','升级失败，已记录失败原因',NULL,'MQTT','seed-ev-9008',NOW() - INTERVAL 170 MINUTE),
 (9009,'000000', 5,'DEV0005',2,'DEVICE_REBOOT','设备重启','INFO',NOW() - INTERVAL 320 MINUTE,NOW() - INTERVAL 320 MINUTE,'{"reason":"watchdog"}','GOOD','PROCESSED','看门狗触发重启，属正常保护',NULL,'MQTT','seed-ev-9009',NOW() - INTERVAL 320 MINUTE),
 (9010,'000000',10,'DEV0010',1,'MATERIAL_LOW','料位低','WARN',NOW() - INTERVAL 90 MINUTE,NOW() - INTERVAL 90 MINUTE,'{"level":12}','GOOD','FAILED','处理失败: 关联的 MES 工单不存在',NULL,'MQTT','seed-ev-9010',NOW() - INTERVAL 90 MINUTE),
 (9011,'000000',14,'DEV0014',1,'TEMP_OVER','温度超上限','WARN',NOW() - INTERVAL 60 HOUR,NOW() - INTERVAL 60 HOUR,'{"temperature":96.4}','GOOD','PROCESSED','工艺正常波动',8808,'MQTT','seed-ev-9011',NOW() - INTERVAL 60 HOUR),
 (9012,'000000',22,'DEV0022',3,'PRINT_DONE','标签打印完成','INFO',NOW() - INTERVAL 3 MINUTE,NOW() - INTERVAL 3 MINUTE,'{"sn":"SN2026092100012","template":"TPL-A"}','GOOD','PENDING',NULL,NULL,'MQTT','seed-ev-9012',NOW() - INTERVAL 3 MINUTE);

-- =============================================================================
-- 13. 数据转发规则（uk_rule_scope(tenant_id,product_id,device_id,rule_name)）
-- =============================================================================
INSERT IGNORE INTO `iot_forward_rule`
  (`rule_id`,`tenant_id`,`rule_name`,`product_id`,`device_id`,`property_codes`,`target_type`,`target_url`,`http_method`,`headers`,`timeout_ms`,`max_retry`,`enabled`,`remark`,`create_dept`,`create_by`,`create_time`) VALUES
 (9201,'000000','机加车间数据转发至MES',4,0,'speed,vibration','HTTP','http://127.0.0.1:8080/mes/iot/callback','POST','{"Content-Type":"application/json"}',5000,3,'1','转速/振动数据实时回写 MES 采集记录',103,1,NOW()),
 (9202,'000000','高温告警转发至企业微信',0,0,'temperature','HTTP','https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo-key','POST','{"Content-Type":"application/json"}',3000,2,'1','温度类属性推送至设备科群机器人',103,1,NOW()),
 (9203,'000000','电能数据转发至能源平台',3,0,'power,energy','HTTP','https://energy.example.com/api/iot/push','POST','{"Content-Type":"application/json","X-Token":"demo-token"}',8000,5,'0','能源管理系统对接(暂未启用，用于演示停用态)',103,1,NOW());

-- =============================================================================
-- 14. 转发投递留痕（覆盖 SUCCESS/FAILED/PENDING）
-- =============================================================================
INSERT IGNORE INTO `iot_forward_log`
  (`log_id`,`tenant_id`,`rule_id`,`device_id`,`device_code`,`property_code`,`target_url`,`payload`,`status`,`retry_count`,`http_status`,`error_msg`,`next_retry_time`,`last_attempt_time`,`create_time`,`update_time`) VALUES
 (9301,'000000',9201, 1,'DEV0001','speed','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0001","speed":1450.0}','SUCCESS',0,200,NULL,NULL,NOW() - INTERVAL 12 MINUTE,NOW() - INTERVAL 12 MINUTE,NOW() - INTERVAL 12 MINUTE),
 (9302,'000000',9201, 2,'DEV0002','speed','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0002","speed":1420.0}','SUCCESS',0,200,NULL,NULL,NOW() - INTERVAL 11 MINUTE,NOW() - INTERVAL 11 MINUTE,NOW() - INTERVAL 11 MINUTE),
 (9303,'000000',9201, 3,'DEV0003','vibration','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0003","vibration":2.4}','SUCCESS',0,200,NULL,NULL,NOW() - INTERVAL 10 MINUTE,NOW() - INTERVAL 10 MINUTE,NOW() - INTERVAL 10 MINUTE),
 (9304,'000000',9202, 6,'DEV0006','temperature','https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo-key','{"deviceCode":"DEV0006","temperature":235.0}','SUCCESS',0,200,NULL,NULL,NOW() - INTERVAL 35 MINUTE,NOW() - INTERVAL 35 MINUTE,NOW() - INTERVAL 35 MINUTE),
 (9305,'000000',9202,13,'DEV0013','temperature','https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo-key','{"deviceCode":"DEV0013","temperature":1582.0}','SUCCESS',0,200,NULL,NULL,NOW() - INTERVAL 18 MINUTE,NOW() - INTERVAL 18 MINUTE,NOW() - INTERVAL 18 MINUTE),
 (9306,'000000',9202,14,'DEV0014','temperature','https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo-key','{"deviceCode":"DEV0014","temperature":96.4}','SUCCESS',1,200,NULL,NULL,NOW() - INTERVAL 59 HOUR,NOW() - INTERVAL 60 HOUR,NOW() - INTERVAL 59 HOUR),
 (9307,'000000',9201, 5,'DEV0005','vibration','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0005","vibration":5.2}','FAILED',3,NULL,'connect timed out',NOW() + INTERVAL 30 MINUTE,NOW() - INTERVAL 20 MINUTE,NOW() - INTERVAL 20 MINUTE,NOW() - INTERVAL 20 MINUTE),
 (9308,'000000',9201, 9,'DEV0009','vibration','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0009","vibration":4.1}','FAILED',1,NULL,'HTTP 502 Bad Gateway',NOW() + INTERVAL 10 MINUTE,NOW() - INTERVAL 8 MINUTE,NOW() - INTERVAL 8 MINUTE,NOW() - INTERVAL 8 MINUTE),
 (9309,'000000',9201,20,'DEV0020','speed','http://127.0.0.1:8080/mes/iot/callback','{"deviceCode":"DEV0020","speed":0.0}','PENDING',0,NULL,NULL,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE),
 (9310,'000000',9202,21,'DEV0021','temperature','https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo-key','{"deviceCode":"DEV0021","temperature":78.2}','PENDING',0,NULL,NULL,NULL,NULL,NOW() - INTERVAL 1 MINUTE,NOW() - INTERVAL 1 MINUTE);

-- =============================================================================
-- 15. 设备时序数据（趋势图/实时页用；uk_source_msg_id 唯一）
-- =============================================================================
INSERT IGNORE INTO `iot_device_data`
  (`data_id`,`tenant_id`,`device_id`,`device_code`,`property_code`,`property_name`,`data_type`,`numeric_value`,`text_value`,`bool_value`,`collect_time`,`receive_time`,`quality`,`source`,`source_msg_id`,`create_time`) VALUES
 -- DEV0001 转速（数控车床A）
 (9401,'000000',1,'DEV0001','speed','转速','NUMERIC',1448.0000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9401',NOW() - INTERVAL 60 MINUTE),
 (9402,'000000',1,'DEV0001','speed','转速','NUMERIC',1452.0000,NULL,NULL,NOW() - INTERVAL 45 MINUTE,NOW() - INTERVAL 45 MINUTE,'GOOD','MQTT','seed-dt-9402',NOW() - INTERVAL 45 MINUTE),
 (9403,'000000',1,'DEV0001','speed','转速','NUMERIC',1439.0000,NULL,NULL,NOW() - INTERVAL 30 MINUTE,NOW() - INTERVAL 30 MINUTE,'GOOD','MQTT','seed-dt-9403',NOW() - INTERVAL 30 MINUTE),
 (9404,'000000',1,'DEV0001','speed','转速','NUMERIC',1461.0000,NULL,NULL,NOW() - INTERVAL 15 MINUTE,NOW() - INTERVAL 15 MINUTE,'GOOD','MQTT','seed-dt-9404',NOW() - INTERVAL 15 MINUTE),
 (9405,'000000',1,'DEV0001','speed','转速','NUMERIC',1450.0000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9405',NOW() - INTERVAL 2 MINUTE),
 -- DEV0005 振动（磨床）
 (9406,'000000',5,'DEV0005','vibration','振动烈度','NUMERIC',4.9000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9406',NOW() - INTERVAL 60 MINUTE),
 (9407,'000000',5,'DEV0005','vibration','振动烈度','NUMERIC',5.1000,NULL,NULL,NOW() - INTERVAL 45 MINUTE,NOW() - INTERVAL 45 MINUTE,'GOOD','MQTT','seed-dt-9407',NOW() - INTERVAL 45 MINUTE),
 (9408,'000000',5,'DEV0005','vibration','振动烈度','NUMERIC',5.4000,NULL,NULL,NOW() - INTERVAL 30 MINUTE,NOW() - INTERVAL 30 MINUTE,'GOOD','MQTT','seed-dt-9408',NOW() - INTERVAL 30 MINUTE),
 (9409,'000000',5,'DEV0005','vibration','振动烈度','NUMERIC',5.6000,NULL,NULL,NOW() - INTERVAL 15 MINUTE,NOW() - INTERVAL 15 MINUTE,'GOOD','MQTT','seed-dt-9409',NOW() - INTERVAL 15 MINUTE),
 (9410,'000000',5,'DEV0005','vibration','振动烈度','NUMERIC',5.2000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9410',NOW() - INTERVAL 2 MINUTE),
 -- DEV0006 温度（氩弧焊工位）
 (9411,'000000',6,'DEV0006','temperature','温度','NUMERIC',196.0000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9411',NOW() - INTERVAL 60 MINUTE),
 (9412,'000000',6,'DEV0006','temperature','温度','NUMERIC',208.0000,NULL,NULL,NOW() - INTERVAL 45 MINUTE,NOW() - INTERVAL 45 MINUTE,'GOOD','MQTT','seed-dt-9412',NOW() - INTERVAL 45 MINUTE),
 (9413,'000000',6,'DEV0006','temperature','温度','NUMERIC',221.0000,NULL,NULL,NOW() - INTERVAL 30 MINUTE,NOW() - INTERVAL 30 MINUTE,'GOOD','MQTT','seed-dt-9413',NOW() - INTERVAL 30 MINUTE),
 (9414,'000000',6,'DEV0006','temperature','温度','NUMERIC',235.0000,NULL,NULL,NOW() - INTERVAL 10 MINUTE,NOW() - INTERVAL 10 MINUTE,'GOOD','MQTT','seed-dt-9414',NOW() - INTERVAL 10 MINUTE),
 (9415,'000000',6,'DEV0006','temperature','温度','NUMERIC',232.0000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9415',NOW() - INTERVAL 2 MINUTE),
 -- DEV0013 温度（熔炼炉）
 (9416,'000000',13,'DEV0013','temperature','温度','NUMERIC',1462.0000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9416',NOW() - INTERVAL 60 MINUTE),
 (9417,'000000',13,'DEV0013','temperature','温度','NUMERIC',1498.0000,NULL,NULL,NOW() - INTERVAL 40 MINUTE,NOW() - INTERVAL 40 MINUTE,'GOOD','MQTT','seed-dt-9417',NOW() - INTERVAL 40 MINUTE),
 (9418,'000000',13,'DEV0013','temperature','温度','NUMERIC',1531.0000,NULL,NULL,NOW() - INTERVAL 25 MINUTE,NOW() - INTERVAL 25 MINUTE,'GOOD','MQTT','seed-dt-9418',NOW() - INTERVAL 25 MINUTE),
 (9419,'000000',13,'DEV0013','temperature','温度','NUMERIC',1582.0000,NULL,NULL,NOW() - INTERVAL 18 MINUTE,NOW() - INTERVAL 18 MINUTE,'GOOD','MQTT','seed-dt-9419',NOW() - INTERVAL 18 MINUTE),
 (9420,'000000',13,'DEV0013','temperature','温度','NUMERIC',1567.0000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9420',NOW() - INTERVAL 2 MINUTE),
 -- DEV0017 功率（SMT贴片线）
 (9421,'000000',17,'DEV0017','power','有功功率','NUMERIC',86.4000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9421',NOW() - INTERVAL 60 MINUTE),
 (9422,'000000',17,'DEV0017','power','有功功率','NUMERIC',92.1000,NULL,NULL,NOW() - INTERVAL 40 MINUTE,NOW() - INTERVAL 40 MINUTE,'GOOD','MQTT','seed-dt-9422',NOW() - INTERVAL 40 MINUTE),
 (9423,'000000',17,'DEV0017','power','有功功率','NUMERIC',97.8000,NULL,NULL,NOW() - INTERVAL 20 MINUTE,NOW() - INTERVAL 20 MINUTE,'GOOD','MQTT','seed-dt-9423',NOW() - INTERVAL 20 MINUTE),
 (9424,'000000',17,'DEV0017','power','有功功率','NUMERIC',88.0000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9424',NOW() - INTERVAL 2 MINUTE),
 -- DEV0020 功率（功能测试工位）
 (9425,'000000',20,'DEV0020','power','有功功率','NUMERIC',78.5000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','MQTT','seed-dt-9425',NOW() - INTERVAL 60 MINUTE),
 (9426,'000000',20,'DEV0020','power','有功功率','NUMERIC',90.2000,NULL,NULL,NOW() - INTERVAL 30 MINUTE,NOW() - INTERVAL 30 MINUTE,'GOOD','MQTT','seed-dt-9426',NOW() - INTERVAL 30 MINUTE),
 (9427,'000000',20,'DEV0020','power','有功功率','NUMERIC',99.8000,NULL,NULL,NOW() - INTERVAL 8 MINUTE,NOW() - INTERVAL 8 MINUTE,'GOOD','MQTT','seed-dt-9427',NOW() - INTERVAL 8 MINUTE),
 (9428,'000000',20,'DEV0020','power','有功功率','NUMERIC',95.1000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9428',NOW() - INTERVAL 2 MINUTE),
 -- DEV0018 识别失败率（装配线A 相机）
 (9429,'000000',18,'DEV0018','fail_rate','识别失败率','NUMERIC',1.1000,NULL,NULL,NOW() - INTERVAL 60 MINUTE,NOW() - INTERVAL 60 MINUTE,'GOOD','HTTP','seed-dt-9429',NOW() - INTERVAL 60 MINUTE),
 (9430,'000000',18,'DEV0018','fail_rate','识别失败率','NUMERIC',2.3000,NULL,NULL,NOW() - INTERVAL 30 MINUTE,NOW() - INTERVAL 30 MINUTE,'GOOD','HTTP','seed-dt-9430',NOW() - INTERVAL 30 MINUTE),
 (9431,'000000',18,'DEV0018','fail_rate','识别失败率','NUMERIC',1.8000,NULL,NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','HTTP','seed-dt-9431',NOW() - INTERVAL 2 MINUTE),
 -- 文本型属性（覆盖 text_value 展示分支）
 (9432,'000000',18,'DEV0018','last_result','最近识别结果','TEXT',NULL,'OK',NULL,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','HTTP','seed-dt-9432',NOW() - INTERVAL 2 MINUTE),
 (9433,'000000',17,'DEV0017','meter_mode','计量模式','TEXT',NULL,'FORWARD',NULL,NOW() - INTERVAL 5 MINUTE,NOW() - INTERVAL 5 MINUTE,'GOOD','MQTT','seed-dt-9433',NOW() - INTERVAL 5 MINUTE),
 -- 布尔型属性
 (9434,'000000',10,'DEV0010','running','运行状态','BOOL',NULL,NULL,1,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9434',NOW() - INTERVAL 2 MINUTE),
 (9435,'000000',12,'DEV0012','running','运行状态','BOOL',NULL,NULL,0,NOW() - INTERVAL 2 MINUTE,NOW() - INTERVAL 2 MINUTE,'GOOD','MQTT','seed-dt-9435',NOW() - INTERVAL 2 MINUTE);

-- =============================================================================
-- 16. 设备在线状态（部分设备置 ONLINE 并刷新最后在线时间，便于运维详情/实时页展示）
--     注意: IotDeviceHeartbeatTask 每 5 分钟会把超时未上报的设备置 OFFLINE，
--           因此本步骤只影响执行后的短暂展示窗口，属预期行为。
-- =============================================================================
UPDATE `iot_device` SET `connection_status` = 'ONLINE', `last_online_time` = NOW() - INTERVAL 1 MINUTE
 WHERE `device_id` IN (1, 3, 6, 10, 13, 17, 20, 22);

-- =============================================================================
-- 校验（执行后人工核对）
-- =============================================================================
-- SELECT 'product_status' k, status, COUNT(*) FROM iot_product GROUP BY status;
-- SELECT 'groups' k, COUNT(*) FROM iot_device_group
--  UNION ALL SELECT 'tags', COUNT(*) FROM iot_device_tag
--  UNION ALL SELECT 'group_rel', COUNT(*) FROM iot_device_group_rel
--  UNION ALL SELECT 'tag_rel', COUNT(*) FROM iot_device_tag_rel
--  UNION ALL SELECT 'firmware', COUNT(*) FROM iot_firmware
--  UNION ALL SELECT 'ota_task', COUNT(*) FROM iot_ota_task
--  UNION ALL SELECT 'alert_rule', COUNT(*) FROM iot_alert_rule
--  UNION ALL SELECT 'alert_record', COUNT(*) FROM iot_alert_record
--  UNION ALL SELECT 'notify_log', COUNT(*) FROM iot_alert_notify_log
--  UNION ALL SELECT 'event', COUNT(*) FROM iot_device_event
--  UNION ALL SELECT 'command', COUNT(*) FROM iot_device_command
--  UNION ALL SELECT 'forward_rule', COUNT(*) FROM iot_forward_rule
--  UNION ALL SELECT 'forward_log', COUNT(*) FROM iot_forward_log
--  UNION ALL SELECT 'device_data', COUNT(*) FROM iot_device_data;
-- -- OTA 状态覆盖: 期望 SUCCESS/FAILED/UPGRADING/SENT/PENDING/TIMEOUT/CANCELLED 全部出现
-- SELECT status, COUNT(*) FROM iot_ota_task GROUP BY status;
-- -- 告警状态覆盖 + active_flag 语义: ACTIVE/ACKNOWLEDGED 应 active_flag=1, RESOLVED 应 NULL
-- SELECT status, active_flag, COUNT(*) FROM iot_alert_record GROUP BY status, active_flag;
-- -- 固件版本一致性核对（应能查出落后设备）
-- SELECT d.device_code, d.firmware_version, f.version AS target_version
--   FROM iot_device d JOIN iot_firmware f ON f.product_key = (SELECT product_code FROM iot_product WHERE product_id = d.product_id)
--  WHERE f.status = 'RELEASED' AND d.firmware_version <> f.version LIMIT 20;

-- =============================================================================
-- 回滚语句
-- =============================================================================
-- DELETE FROM `iot_device_data`       WHERE `data_id`   BETWEEN 9401 AND 9435;
-- DELETE FROM `iot_forward_log`       WHERE `log_id`    BETWEEN 9301 AND 9310;
-- DELETE FROM `iot_forward_rule`      WHERE `rule_id`   BETWEEN 9201 AND 9203;
-- DELETE FROM `iot_device_event`      WHERE `event_id`  BETWEEN 9001 AND 9012;
-- DELETE FROM `iot_alert_notify_log`  WHERE `log_id`    BETWEEN 8901 AND 8916;
-- DELETE FROM `iot_alert_record`      WHERE `alert_id`  BETWEEN 8801 AND 8810;
-- DELETE FROM `iot_alert_rule`        WHERE `rule_id`   BETWEEN 8701 AND 8706;
-- DELETE FROM `iot_ota_task`          WHERE `task_id`   BETWEEN 8601 AND 8613;
-- DELETE FROM `iot_firmware`          WHERE `firmware_id` BETWEEN 8501 AND 8507;
-- DELETE FROM `iot_device_tag_rel`    WHERE `rel_id`    BETWEEN 8401 AND 8431;
-- DELETE FROM `iot_device_group_rel`  WHERE `rel_id`    BETWEEN 8301 AND 8324;
-- DELETE FROM `iot_device_tag`        WHERE `tag_id`    BETWEEN 8201 AND 8206;
-- DELETE FROM `iot_device_group`      WHERE `group_id`  BETWEEN 8101 AND 8113;
-- DELETE FROM `ry-vue`.`sys_dict_data` WHERE `dict_code` BETWEEN 600280 AND 600282;
-- UPDATE `iot_product` SET `status` = '1' WHERE `status` = 'ACTIVE';   -- 谨慎: 会把人工改过的也回退
-- UPDATE `iot_device` SET `firmware_version` = 'V1.0.0', `connection_status` = 'OFFLINE', `last_online_time` = NULL
--  WHERE `device_id` IN (1,3,6,10,13,17,20,22);
