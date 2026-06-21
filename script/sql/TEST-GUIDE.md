# ERP / MES / IoT 业务流程测试指南

## 一、环境要求

- **数据库**: MySQL/MariaDB，须先执行 `ry_vue.sql` + `ry-erp.sql` + `ry-mes.sql` + `ry-iot.sql`
- **后端**: Spring Boot (Undertow :8080)，配置 RabbitMQ + Redis
- **前端**: Vue 3 (端口80)，代理 `/dev-api` → `:8080`
- **IoT**: MQTT Broker (内置Moquette，默认端口1883)

## 二、测试数据导入顺序

```bash
# 按顺序执行：
# 第1步: ERP补充主数据 (ry-erp库)
mysql -u root -p ry-erp < script/sql/test-data-erp-master.sql

# 第2步: 排班主数据 (ry-erp库)
mysql -u root -p ry-erp < script/sql/test-data-sch.sql

# 第3步: MES主数据 (ry-mes库)
mysql -u root -p ry-mes < script/sql/test-data-mes-master.sql

# 第4步: IoT主数据 (ry-iot库)
mysql -u root -p ry-iot < script/sql/test-data-iot-master.sql

# 第5步: ERP业务流转数据 (ry-erp库)
mysql -u root -p ry-erp < script/sql/test-flow-erp.sql

# 第6步: MES业务流转数据 (ry-mes库)
mysql -u root -p ry-mes < script/sql/test-flow-mes.sql

# 第7步: IoT模拟数据 (ry-iot库)
mysql -u root -p ry-iot < script/sql/test-flow-iot.sql
```

## 三、数据概览

| 数据层 | 记录数 | 关键内容 |
|--------|--------|----------|
| 物料 | 27 (已有) + 补充 | 钢板、电机、减速机、自行车系列(车架/车轮/车把/轮圈/轮胎/辐条/把管/把套) |
| 供应商 | 4 | 钢板产出商、华东轴承、深圳精密电机、天津化工 |
| 客户 | 9 (已有) + 信用 | 小明科技、王芳贸易、陈氏机械、沃尔玛、好市多等 |
| BOM | 8套 | 减速机2版、电机、齿轮组、自行车、车轮、车把、车架、辐条 |
| 工艺路线 | 2条 | 自行车装配4步、减速机装配4步 |
| 仓库 | 3个 | 成品仓库、原料仓库、半成品仓库 |
| 库区/库位 | 6区/15位 | 成品区、半成品区、金属库区、辅料库区、加工区等 |
| 初始库存 | 9条 | 钢板500吨、轮圈200件、自行车50辆等 |
| 工人/班组 | 6人/2组 | 装配A组(3人, ID 30001-30003)、装配B组(3人, ID 30004-30006) |
| 班次 | 3个 | 白班(8-16)、中班(16-24)、夜班(0-8) |
| MES设备 | 5台 | CNC-001~002、WELD-001、ASM-001、PKG-001 |
| 保养计划 | 5个 | 日保/周保/月保/季度保/年保 |
| 采集点 | 5个 | 温度、振动、尺寸、焊接温度、包装计数 |
| SPC控制图 | 3个 | 温度XBarR、尺寸XBarR、焊接温度XBarR |
| IoT产品 | 3个 | 温度传感器、振动传感器、环境网关 |
| IoT设备 | 5个 | 各MES设备对应的探头/计数器 |
| IoT模拟数据 | 50条 | 24h设备数据(温度/振动/尺寸/计数) |
| 业务流转 | 20+条 | SO→PO→PRD→WM→MES完整链条 |

## 四、按场景测试

### ERP 场景测试

#### E1: 销售-生产-出货闭环
```sql
-- 查看数据
SELECT * FROM so_customer_order WHERE co_no='CO-TEST-001';
SELECT * FROM so_sales_order_header WHERE so_no='SO-TEST-001';
SELECT * FROM so_sales_order_line WHERE so_id=10001;
SELECT * FROM prd_production_plan WHERE plan_no='PP-TEST-001';
SELECT * FROM prd_work_order WHERE wo_no='WO-TEST-001';
SELECT * FROM wm_production_recpt WHERE recpt_code='PR-TEST-001';
SELECT * FROM wm_sales_issue WHERE issue_code='SI-TEST-001';
```

**前端操作验证:**
1. 打开销售 → 销售订单 → 查看SO-TEST-001 (状态=CONFIRMED)
2. 打开生产 → 生产计划 → 查看PP-TEST-001 (关联SO行)
3. 打开生产 → 工单 → 查看WO-TEST-001 (状态=RELEASED)
4. 执行 `PUT /erp/so/orderheader/confirm/10001` 确认SO
5. 在API中创建生产计划 → BOM展开 → 生成工单 → 报工 → 完工

#### E3: 采购-入库流程
```sql
-- 查看数据
SELECT * FROM pu_purchase_requisition WHERE requisition_no='PR-TEST-001';
SELECT * FROM pu_purchase_order WHERE order_no='PO-TEST-001';
SELECT * FROM wm_arrival_notice WHERE notice_code='AN-TEST-001';
SELECT * FROM wm_material_receive WHERE receive_code='RCV-TEST-001';
```

**前端操作验证:**
1. 打开采购 → 采购订单 → 查看PO-TEST-001 (状态=APPROVED)
2. 执行收货: `PUT /erp/wm/receive/execute/{id}` → `PUT /erp/wm/receive/submit/{id}`
3. 验证库存: `GET /erp/wm/stock/list?materialId=1` 钢板库存变化

#### E5: BOM展开/成本卷积
```sql
-- BOM树展开
SELECT * FROM bd_bom WHERE bom_id=5;  -- 自行车BOM
SELECT * FROM bd_bom_detail WHERE bom_id=5;  -- 子项: 车架/车轮/车把/润滑油
```

**API验证:**
- `GET /erp/bd/bom/tree/5` — BOM树
- `GET /erp/bd/bom/explode/5` — 展算
- `GET /erp/bd/bom/costRollup/5` — 成本卷积
- `GET /erp/bd/bom/whereUsed/1001` — 自行车用途追溯

#### E6: 工艺路线
```sql
SELECT * FROM bd_routing WHERE routing_code='RT-BIKE-001';
SELECT * FROM bd_routing_step WHERE routing_id=1 ORDER BY sequence_num;
```

**API验证:**
- `GET /erp/bd/routing/steps/1` — 查看步骤
- `PUT /erp/bd/routing/verify/1` — 校验路线

#### E7: 工单生命周期
**API验证:**
1. `PUT /erp/prd/work/release/10001` → woStatus=RELEASED
2. `PUT /erp/prd/work/start/10001` → woStatus=STARTED
3. `POST /erp/prd/report` 创建报工 → 48良品+2报废
4. `PUT /erp/prd/work/complete/10001` → woStatus=COMPLETED
5. `PUT /erp/prd/work/close/10001` → woStatus=CLOSED

#### E9: 库存调拨与盘点
```sql
SELECT * FROM wm_transfer WHERE transfer_no='TR-TEST-001';
```

**API验证:**
- `PUT /erp/wm/transfer/execute/10001` — 执行调拨
- 验证源仓库库存减少、目标仓库库存增加

#### E10: 批次/SN/条码追溯
**API验证:**
- `GET /erp/wm/trace/forward?materialId=1` — 从钢板正向追溯
- `GET /erp/wm/trace/backward?materialId=1001` — 从自行车反向追溯

#### E11: 排班-考勤
```sql
SELECT * FROM sch_team WHERE team_code='TEAM-A';
SELECT * FROM sch_team_member WHERE team_id=1;
SELECT * FROM sch_attendance WHERE record_date='2026-06-01';
```

**前端操作验证:**
1. 排班管理 → 班组管理 → 查看装配A组3名成员
2. 考勤管理 → 查看6/1考勤(张三正常/王五迟到/钱七早退)

#### E12: 库存预警
```sql
-- 查看物料安全库存设置
SELECT material_code, material_name, safety_stock, min_stock, max_stock FROM bd_material WHERE material_id=1;
-- 查看当前库存
SELECT material_id, stock_quantity FROM wm_material_stock WHERE material_id=1;
```

---

### MES 场景测试

#### M1: 流转卡 → 派工 → 报工
**数据状态**: FC-001(RELEASED)，切割完成，焊接进行中

**API验证:**
1. `GET /mes/pro/flow-card/1` — 查看流转卡详情
2. `PUT /mes/pro/flow-card/start-step` — 开始装配工序 {cardId:1, stepRecordId:3, operatorId:1}
   - body: `{"cardId": "1", "stepRecordId": "3", "operatorId": "1"}`
3. `PUT /mes/pro/flow-card/complete-step` — 完成装配工序
   - body: `{"cardId": "1", "stepRecordId": "3", "goodQty": "48", "defectQty": "2", "scrapQty": "0"}`
4. `PUT /mes/pro/flow-card/complete/1` — 完成整张流转卡

#### M3: 设备管理-保养-维修
```sql
SELECT * FROM eq_equipment WHERE equipment_code='CNC-001';
SELECT * FROM eq_maintenance_plan WHERE equipment_id=1;
SELECT * FROM eq_repair WHERE repair_no='RP-001';  -- 已完成
SELECT * FROM eq_repair WHERE repair_no='RP-002';  -- 待维修
```

**API验证:**
1. `GET /mes/eq/equipment/1` — 设备详情 (含inspection_items)
2. `PUT /mes/eq/repair/start/2` — 开始维修RP-002 (状态→REPAIRING)
3. `PUT /mes/eq/repair/complete/2` — 完成维修 (状态→COMPLETED，设备自动恢复NORMAL)
4. 验证设备状态自动切换: `GET /mes/eq/equipment/3` (WELD-001 NORMAL→REPAIRING→NORMAL)

#### M4: IQC来料检验
```sql
SELECT * FROM qc_iqc WHERE iqc_no='IQC-001';
```

**API验证:**
- `PUT /mes/qc/iqc/inspect/1` — 执行检验
  - body: `{"inspectResult": "QUALIFIED", "defectInfo": "[...]"}`
- 验证MQ回传ERP (RabbitMQ消息发送)

#### M5: IPQC过程检验 + OQC出厂检验
```sql
SELECT * FROM qc_ipqc WHERE ipqc_no='IPQC-001';
SELECT * FROM qc_oqc WHERE oqc_no='OQC-001';
```

**API验证:**
- `PUT /mes/qc/ipqc/inspect/1` — 执行IPQC (自动计算不良率)
- `PUT /mes/qc/oqc/inspect/1` — 执行OQC

#### M6: 安灯呼叫系统
```sql
SELECT * FROM pro_andon ORDER BY andon_id;
-- ANDON-001: 物料短缺(人工→已解决)
-- ANDON-002: 质量问题(人工→待响应)
-- ANDON-003: 设备故障(IoT触发→已响应)
```

**API验证:**
1. `GET /mes/pro/andon/stats` — 安灯统计面板
2. `PUT /mes/pro/andon/respond/2` — 响应ANDON-002
3. `PUT /mes/pro/andon/resolve/2` — 解决ANDON-002
4. `POST /mes/pro/andon/call` — 手动发起新安灯
   - body: `{"andonNo":"ANDON-004","mesWoId":"1","cardId":"1","stepRecordId":"2","callType":"EQUIPMENT","callReason":"测试安灯呼叫"}`

#### M7: SN序列号追溯
```sql
SELECT * FROM pro_sn WHERE sn_code IN ('SN202606200001','SN202606200002','SN202606200003');
```

**API验证:**
1. `GET /mes/pro/sn/trace/3` — SN003追溯树(已完成的自行车)
2. `GET /mes/pro/sn/by-code/SN202606200001` — 按码查找
3. `PUT /mes/pro/sn/complete/3` → `PUT /mes/pro/sn/ship/3` — 完成→发货

#### M8: MBOM+SPC分析
```sql
SELECT * FROM mes_mbom WHERE mbom_id=1;
SELECT * FROM mes_mbom_line WHERE mbom_id=1;
SELECT * FROM spc_control_chart WHERE chart_id=1;
```

**API验证:**
- `GET /mes/spc/control-chart/analyze/1` — SPC分析(计算控制限+OOC检测)
- `GET /mes/spc/data-point/by-chart/1` — 图表数据点

---

### IoT 场景测试

#### I1: 设备管理
```sql
SELECT p.product_name, d.device_name, d.mes_equipment_id
FROM iot_product p JOIN iot_device d ON p.product_id=d.product_id;
```

**前端操作验证:**
1. IoT → 产品管理 → 查看3个产品
2. IoT → 设备管理 → 查看5个设备
3. 检查设备绑定MES: CNC001-TEMP → CNC-001

#### I2: MQTT模拟数据上报
```bash
# 使用mosquitto_pub模拟设备上报
# Topic: iot/{tenantId}/{deviceCode}/property/post
# 认证: username=deviceCode, password=deviceSecret

mosquitto_pub -h localhost -p 1883 \
  -u CNC001-TEMP -P test_secret_cnc001_temp_001 \
  -t "iot/000000/CNC001-TEMP/property/post" \
  -m '{"property":"temperature","value":73.5,"dataType":"NUMERIC","unit":"℃","timestamp":"2026-06-21T10:00:00","quality":"GOOD"}'
```

**验证点:**
- Redis: `HGETALL iot:latest:1` (查看CNC001-TEMP最新属性)
- 数据库: `SELECT * FROM iot_device_data WHERE device_id=1 ORDER BY receive_time DESC LIMIT 5`
- 设备状态: `GET /iot/device/1/status`

#### I3: 告警触发
```bash
# 发送超阈值温度 (>85℃)
mosquitto_pub -h localhost -p 1883 \
  -u CNC001-TEMP -P test_secret_cnc001_temp_001 \
  -t "iot/000000/CNC001-TEMP/property/post" \
  -m '{"property":"temperature","value":89.5,"dataType":"NUMERIC","unit":"℃","timestamp":"2026-06-21T10:01:00","quality":"BAD"}'
```

**验证点:**
- MES数据库: `SELECT * FROM pro_andon WHERE source='IoT' ORDER BY create_time DESC LIMIT 1` — 应自动创建设备安灯
- Redis频率限制: `GET iot:alert:rl:1:TEMPERATURE` — 60秒内不会重复告警
- SSE推送: 前端收到IOT_ALERT类型的SSE消息

#### I4: 实时监控SSE
**前端操作验证:**
1. IoT → 设备管理 → 选择CNC001-TEMP → 点击"实时监控"
2. 建立SSE连接 `GET /iot/device/1/realtime`
3. 发送MQTT数据，观察前端每3秒刷新
4. SSE推送JSON: `{"deviceId":1,"timestamp":"...","connectionStatus":"ONLINE","properties":{"temperature":"73.5"}}`

---

### 跨系统集成测试

#### X1: ERP→MES→IoT 完整生产链
```
步骤1 (ERP): 确认SO → 生产计划 → 展开BOM → 释放工单 [MQ→MES]
步骤2 (MES): 创建流转卡 → 释放 → 开始切割工序
步骤3 (IoT): MQTT上报温度数据 → 某时刻超阈值
步骤4 (IoT→MES): 告警触发MES安灯 → 设备状态变FAULT
步骤5 (MES): 维修后恢复 → 继续加工 → 完工报工 [MQ→ERP]
步骤6 (ERP): 成品入库 → 销售出货
```

#### X2: 采购入库→IQC→上架
```
步骤1 (ERP): PO-TEST-001确认 → 到货通知 → 创建收货单
步骤2 (MES): 收货后创建IQC → 执行检验
步骤3 (MES→ERP): IQC合格/不合格MQ回传
步骤4 (ERP): 合格→上架入库; 不合格→触发退货流程
```

#### X3: 设备故障→维修→影响生产
```
步骤1 (IoT): MQTT振动超阈值 → 告警 → MES安灯(ANDON-003)
步骤2 (MES): 设备状态 FAULT → 创建维修单(RP-001/RP-002)
步骤3 (MES): 维修完成 → 设备恢复NORMAL
步骤4 (MES): 受影响工单工序恢复 → 补充报工
```

---

## 五、数据清理

```sql
-- ry-erp 清理
DELETE FROM so_customer_order WHERE co_id >= 10001;
DELETE FROM so_sales_order_header WHERE so_id >= 10001;
DELETE FROM so_sales_order_line WHERE line_id >= 10001;
DELETE FROM pu_purchase_requisition WHERE requisition_id >= 10001;
DELETE FROM pu_purchase_requisition_line WHERE line_id >= 10001;
DELETE FROM pu_purchase_order WHERE order_id >= 10001;
DELETE FROM pu_purchase_order_line WHERE line_id >= 10001;
DELETE FROM wm_arrival_notice WHERE notice_id >= 10001;
DELETE FROM wm_arrival_notice_line WHERE line_id >= 10001;
DELETE FROM wm_material_receive WHERE receive_id >= 10001;
DELETE FROM wm_material_receive_line WHERE line_id >= 10001;
DELETE FROM wm_transfer WHERE transfer_id >= 10001;
DELETE FROM wm_transfer_line WHERE line_id >= 10001;
DELETE FROM wm_production_recpt WHERE recpt_id >= 10001;
DELETE FROM wm_sales_issue WHERE issue_id >= 10001;
DELETE FROM wm_sales_issue_line WHERE line_id >= 10001;
DELETE FROM prd_production_plan WHERE plan_id >= 10001;
DELETE FROM prd_work_order WHERE wo_id >= 10001;
DELETE FROM prd_work_report WHERE report_id >= 10001;
DELETE FROM bd_vendor WHERE vendor_id BETWEEN 2062098763051089922 AND 2062098763051089924;
DELETE FROM bd_material_vendor WHERE relation_id BETWEEN 40001 AND 40006;
DELETE FROM bd_client_credit WHERE credit_id BETWEEN 1 AND 5;
DELETE FROM bd_routing WHERE routing_id IN (1,2);
DELETE FROM bd_routing_step WHERE step_id BETWEEN 1 AND 8;
DELETE FROM bd_serial_number_rule WHERE rule_id IN (1,2,3);
DELETE FROM wm_warehouse WHERE warehouse_id=3;
DELETE FROM wm_storage_area WHERE area_id=6;
DELETE FROM wm_storage_location WHERE location_id BETWEEN 13 AND 15;
DELETE FROM wm_material_stock WHERE stock_id BETWEEN 1001 AND 1009;
DELETE FROM sch_plan WHERE plan_id=1;
DELETE FROM sch_shift WHERE shift_id BETWEEN 1 AND 3;
DELETE FROM sch_worker WHERE worker_id BETWEEN 1 AND 6;
DELETE FROM sch_team WHERE team_id IN (1,2);
DELETE FROM sch_team_member WHERE member_id BETWEEN 1 AND 6;
DELETE FROM sch_plan_team WHERE record_id IN (1,2);
DELETE FROM sch_team_schedule WHERE team_schedule_id BETWEEN 1 AND 10;
DELETE FROM sch_employee_schedule WHERE schedule_id BETWEEN 1 AND 18;
DELETE FROM sch_attendance WHERE attendance_id BETWEEN 1 AND 6;

-- ry-mes 清理
DELETE FROM pro_work_order WHERE mes_wo_id=1;
DELETE FROM pro_work_order_step WHERE step_id BETWEEN 1 AND 4;
DELETE FROM pro_flow_card WHERE card_id IN (1,2);
DELETE FROM pro_flow_card_step WHERE step_record_id BETWEEN 1 AND 4;
DELETE FROM pro_dispatch WHERE dispatch_id IN (1,2);
DELETE FROM pro_sn WHERE sn_id BETWEEN 1 AND 3;
DELETE FROM pro_andon WHERE andon_id BETWEEN 1 AND 3;
DELETE FROM eq_equipment WHERE equipment_id BETWEEN 1 AND 5;
DELETE FROM eq_maintenance_plan WHERE plan_id BETWEEN 1 AND 5;
DELETE FROM eq_repair WHERE repair_id IN (1,2);
DELETE FROM mes_mbom WHERE mbom_id IN (1,2);
DELETE FROM mes_mbom_line WHERE mbom_line_id BETWEEN 1 AND 9;
DELETE FROM dc_collection_point WHERE point_id BETWEEN 1 AND 5;
DELETE FROM dc_data_record WHERE record_id BETWEEN 1 AND 12;
DELETE FROM spc_control_chart WHERE chart_id BETWEEN 1 AND 3;
DELETE FROM qc_iqc WHERE iqc_id=1;
DELETE FROM qc_ipqc WHERE ipqc_id=1;
DELETE FROM qc_oqc WHERE oqc_id=1;

-- ry-iot 清理
DELETE FROM iot_product WHERE product_id BETWEEN 1 AND 3;
DELETE FROM iot_device WHERE device_id BETWEEN 1 AND 5;
DELETE FROM iot_device_data WHERE data_id BETWEEN 1 AND 50;
```

---

## 六、关键API索引

| 系统 | API路径 | 方法 | 功能 |
|------|---------|------|------|
| ERP | `/erp/bd/bom/costRollup/{id}` | GET | BOM成本卷积 |
| ERP | `/erp/bd/bom/explode/{id}` | GET | BOM展算 |
| ERP | `/erp/bd/routing/steps/{id}` | GET | 查看路线步骤 |
| ERP | `/erp/prd/work/release/{id}` | PUT | 释放工单 |
| ERP | `/erp/prd/work/{id}/submit` | POST | 提交工单审批 |
| ERP | `/erp/wm/trace/forward` | GET | 正向追溯 |
| ERP | `/erp/wm/trace/backward` | GET | 反向追溯 |
| MES | `/mes/pro/flow-card/1` | GET | 流转卡详情 |
| MES | `/mes/pro/flow-card/start-step` | PUT | 开始工序 |
| MES | `/mes/pro/flow-card/complete-step` | PUT | 完成工序 |
| MES | `/mes/pro/sn/trace/{id}` | GET | SN追溯树 |
| MES | `/mes/eq/repair/start/{id}` | PUT | 开始维修 |
| MES | `/mes/eq/repair/complete/{id}` | PUT | 完成维修 |
| MES | `/mes/pro/andon/stats` | GET | 安灯统计 |
| MES | `/mes/spc/control-chart/analyze/{id}` | GET | SPC分析 |
| IoT | `/iot/device/{id}/status` | GET | 设备在线状态 |
| IoT | `/iot/device/{id}/data` | GET | 历史数据 |
| IoT | `/iot/device/{id}/realtime` | GET | SSE实时监控 |
