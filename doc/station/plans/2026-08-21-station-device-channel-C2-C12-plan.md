# NexusERP 工位终端设备通道开发计划（C2-C12 + M1 遗留）

> 承接 `doc/station/plans/2026-08-21-station-device-channel-plan.md`（C1 已交付，本计划为其 C2-C12/M1 的详细执行方案）
> 契约权威定义：nexus-station M2 计划表 1（已与客户端 `src/main/mes/client.ts`、`types.ts` 逐字段核对）

## 一、总体设计

### 1.1 已确认的架构决策

| 决策点 | 结论 |
|---|---|
| 模块复用方式 | **ruoyi-station 依赖 ruoyi-mes**（pom 加依赖），直接注入 IProAndonService/IProFlowCardService/ProSnMapper 等复用既有逻辑，不再"物理独立" |
| QUALITY_HOLD 判定 | 综合判定：最新综测记录 FAIL **或** 存在未关闭 NCR **或** SN 状态非 IN_PRODUCTION |
| 缺陷代码源 | `ncr_defect_code` 表（level=3 且 enabled=1 的代码项） |
| 安灯工位 | `pro_andon` 表 **新增 station_code 列** |

### 1.2 模块结构（ruoyi-station 扩展后）

```
ruoyi-station/com.nexus.station/
├── controller/
│   ├── StationDeviceController.java      # 既有 C1，扩展 C2-C9 端点
│   └── StationHeartbeatController.java   # C10（URL /mes/flash/report/station/heartbeat，不在 /mes/station 前缀下）
├── domain/
│   ├── MesStationDevice.java             # 设备台账实体
│   ├── MesStationReport.java             # 设备通道报工记录（幂等承载表）
│   └── MesSnBindLog.java                 # 关键件绑定/解绑留痕表
├── mapper/
│   ├── StationDeviceMapper.java          # @DS("mes") 新表 CRUD
│   ├── StationReportMapper.java          # @DS("mes") 报工记录
│   ├── StationSnBindLogMapper.java       # @DS("mes") 绑定日志
│   ├── StationAndonMapper.java           # @DS("mes") pro_andon 按 station_code 查询
│   ├── StationDefectCodeMapper.java      # @DS("mes") ncr_defect_code 只读
│   └── StationSchWorkerMapper.java       # 既有
└── service/
    ├── StationPassService.java           # C2/C3/C4 过站校验与报工
    ├── StationSnService.java             # C5/C6 关键件绑定/解绑
    └── StationAndonService.java          # C7/C8 安灯包装
```

**pom 变更**：`ruoyi-station/pom.xml` 增加 `ruoyi-mes` 依赖（无循环依赖：ruoyi-mes 不依赖 ruoyi-station）。

### 1.3 统一约定（每个端点遵守）

- 鉴权：复用现有 `checkApiKey` fail-closed 私有方法（两份逐字相同，抽到 `StationApiKeyChecker` 工具类或保留私有复制，实现时取小改动方案）
- 租户：所有查询/写入 `TenantHelper.dynamic("000000", ...)` 包裹
- 多库：新表在 ry-mes 库，Mapper `@DS("mes")`；sch_worker `@DS("erp")`；sys_user 走 master 默认
- 客户端兼容：请求新字段一律可空容忍；响应新增字段向后兼容
- 工位码→工序解析链：① `mes_station_device` 心跳注册的 process_code 绑定 → ② 解析工位码格式 `STN|车间|产线|工序|形态` 取 parts[3] → ③ 均失败时 STATION_MATCH 校验返回 ok=false 并给 message

## 二、数据库变更

新增增量脚本 `script/sql/update/2026-08-21-station-device-channel.sql`（并同步表定义到 `script/sql/ry-mes.sql`）：

```sql
-- 1. 设备台账（C10）
CREATE TABLE mes_station_device (
  device_id bigint NOT NULL, tenant_id varchar(20) DEFAULT '000000',
  device_code varchar(64) NOT NULL, station_code varchar(64) NULL,
  process_code varchar(64) NULL, app_version varchar(32) NULL,
  last_heartbeat datetime NULL,
  create_dept bigint, create_by bigint, create_time datetime DEFAULT CURRENT_TIMESTAMP,
  update_by bigint, update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag char(1) DEFAULT '0',
  PRIMARY KEY (device_id), UNIQUE KEY uk_device_code (device_code)
) COMMENT '工位终端设备台账';

-- 2. 设备通道报工记录（C3/C4 幂等承载，唯一索引兜底离线重放去重）
CREATE TABLE mes_station_report (
  report_id bigint NOT NULL, tenant_id varchar(20) DEFAULT '000000',
  report_type varchar(16) NOT NULL COMMENT 'SINGLE/BATCH',
  sn_code varchar(64) NULL, station_code varchar(64) NOT NULL,
  card_id bigint, step_record_id bigint, mes_wo_id bigint, wo_no varchar(64),
  operator_id bigint, operator_code varchar(32), operator_name varchar(64),
  result varchar(8) NULL COMMENT 'PASS/FAIL(SINGLE)',
  defect_codes varchar(512) NULL COMMENT '不良代码逗号分隔',
  good_qty decimal(18,4) DEFAULT 0, defect_qty decimal(18,4) DEFAULT 0,
  client_ts bigint NOT NULL, client_key varchar(160) NOT NULL COMMENT '幂等键: SINGLE|sn|station|ts 或 BATCH|station|ts',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (report_id), UNIQUE KEY uk_tenant_client_key (tenant_id, client_key),
  KEY idx_sn_code (sn_code), KEY idx_station_code (station_code)
) COMMENT '工位终端报工记录(设备通道)';

-- 3. 关键件绑定日志（C5/C6 留痕）
CREATE TABLE mes_sn_bind_log (
  log_id bigint NOT NULL, tenant_id varchar(20) DEFAULT '000000',
  action varchar(16) NOT NULL COMMENT 'BIND/UNBIND/REBIND',
  parent_sn_id bigint, parent_sn_code varchar(64),
  component_sn_id bigint, component_sn_code varchar(64), new_component_sn_code varchar(64),
  reason varchar(255), operator_code varchar(32),
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id), KEY idx_parent (parent_sn_id), KEY idx_component (component_sn_id)
) COMMENT '关键件绑定操作日志';

-- 4. 安灯增加工位（C7/C8）
ALTER TABLE pro_andon ADD COLUMN station_code varchar(64) NULL COMMENT '工位编码(设备通道呼叫)' AFTER step_record_id;
ALTER TABLE pro_andon ADD INDEX idx_station_code (station_code);
```

## 三、分批任务明细（沿用文档排期批次）

### 批次1：C7 + C8 安灯（1d）

**ruoyi-mes 改动**：
- `ProAndon.java` 实体加 `stationCode` 字段；`ProAndonBo` 同步
- `IProAndonService`/`ProAndonServiceImpl`：新增重载 `callAndon(mesWoId, cardId, stepRecordId, callType, callReason, stationCode, operatorCode)` —— 现方法内部调用它保持兼容；caller 取值改为"operatorCode 优先，LoginHelper 判空兜底"（对齐 MesAndonServiceImpl 无登录态先例），caller_name 回填工号或 sch_worker 档案名

**ruoyi-station**：`StationAndonService`
- C7 `POST /mes/station/andon/call`：callType 映射 MATERIAL→MATERIAL、REPAIR→EQUIPMENT、PERSON→OTHER、QUALITY→QUALITY；woNo/cardNo 可选传入时反查 mesWoId/cardId 冗余；返回 `{andonNo}`。既有 SSE 推送（publishAndonSse）自动生效
- C8 `GET /mes/station/andon/latest?stationCode=`：按 station_code 查最新一条（状态任意），返回 `{andonNo, callType, andonStatus, callTime, responderName}`，无记录 data=null。注意 callType 回传时做逆映射（EQUIPMENT→REPAIR、OTHER→PERSON）以匹配客户端四键枚举

### 批次2：C2 + C3 过站校验/单件报工（3-4d，核心）

**C2 `POST /mes/station/pass/validate`**（StationPassService.validate）：
1. 按 snCode 查 pro_sn（不存在 → pass=false, checks=[{code:STATION_MATCH,ok:false,message:'SN不存在'}]）
2. 解析 stationCode → processCode（按 1.3 解析链），定位 SN 卡上对应 pro_flow_card_step
3. 四重校验一次返回：
   - PREV_STEP：该 step 之前所有工序 step_status ∈ {COMPLETED, SKIPPED}
   - STATION_MATCH：SN current_step_seq 对应工序 = 工位工序，且 SN 状态 IN_PRODUCTION
   - DUPLICATE：mes_station_report 中已存在该 sn+station+result=PASS 记录
   - QUALITY_HOLD：最新 mes_test_record（按 sn_code 查 result 非 null 的最新一条）=FAIL，或该 SN 的 card 存在未关闭 NCR 单，或 SN 状态非 IN_PRODUCTION
4. snInfo 从 pro_sn 冗余字段回填 {woNo, cardNo, materialName, currentProcessName}

**C3 `POST /mes/station/pass/report`**（StationPassService.reportSingle）：
1. 组装 client_key=`SINGLE|snCode|stationCode|clientTs`，**先 insert mes_station_report**；捕获 DuplicateKeyException → 直接返回成功（幂等重放，冒烟#6 验收点）
2. insert 成功后再执行业务报工（同事务）：定位 step → PENDING 则 CAS 置 IN_PROGRESS（自动开工，写 operator/actual_start_time）→ PASS: step.good_qty+1、SN current_step_seq 前移；FAIL: step.defect_qty+1、defect_codes 落 mes_station_report
3. SN 到达末道工序 PASS 时调 IProSnService.completeSn；step 累计 good+defect+scrap ≥ planned_qty 时 CAS step → COMPLETED 并复用 ProFlowCardServiceImpl 的卡完工聚合逻辑（实现时若 completeCard 为私有则提为 public 或经现有公开方法触发，不重写状态机）
4. operatorCode 可选：经 StationSchWorkerMapper 反查 worker/userId 回填 operator 字段；缺失容忍

### 批次3：C4 + C12 批量报工 + BO 字段（1-2d）

**C4 `POST /mes/station/pass/report-batch`**：client_key=`BATCH|stationCode|clientTs` 幂等先 insert；按 stationCode→processCode 定位当前唯一 IN_PROGRESS 的 pro_flow_card_step（0 个或多个均报错提示"工位无在制工序或存在多张在制卡"）；goodQty 累加 good_qty，defects 数组按 code 汇总 defectQty 累加 defect_qty、defect_codes 逗号拼接落库

**C12**：`FlashRecordBo` 加 `operatorCode` 字段（operatorId 已有）、`TestReportBo` 加 `operatorId/operatorCode`；FlashRecordServiceImpl.buildValidatedRecord 与 TestRecordServiceImpl.reportTestAuto 中改为"BO 携带值优先、LoginHelper 判空兜底"（对齐现有 isLogin 判空模式），均缺失容忍

### 批次4：C5 + C6 关键件绑定/解绑（2d）

复用 pro_sn.parent_sn_id 父子链（ruoyi-mes 的 ProSnMapper 直接注入），新建 StationSnService：
- C5 `POST /mes/station/sn/bind`：批量绑定——校验 parent 与全部 component 均存在、状态非 SCRAPPED/SHIPPED、component 无既有 parent（已绑定则报错）；**防环校验**：沿 parent 向上遍历祖先链（深度上限 10，对齐 trace 的 MAX_PARENT_DEPTH），若链上出现任一 component SN 则拒绝；逐个 update parent_sn_id/parent_sn_code；写 mes_sn_bind_log(action=BIND)；返回 {boundQty}
- C6 `POST /mes/station/sn/unbind`：解绑（parent_sn_id 置空）→ 若传 newComponentSnCode 则对新件执行同样校验+绑定（换板一步完成，action=REBIND）；reason 必填落 bind_log

### 批次5：C10 心跳 + 建表（1d）

- 实体/Mapper 就绪后实现 `StationHeartbeatController`：`@RequestMapping("/mes/flash/report/station")` + `@PostMapping("/heartbeat")`（该前缀已在 security.excludes 的 `/mes/flash/report/**` 内）
- upsert：按 device_code 查 mes_station_device → 存在则 update station_code/process_code/app_version/last_heartbeat，不存在则 insert（雪花 ID）；@Lock4j(keys="deviceCode") 防并发重插
- 附带收益：心跳注册即完成 工位码↔工序码 绑定，反哺 1.3 解析链第①级

### 批次6：C9 缺陷代码 + M1-a/M1-b（1-2d）

- **C9** `GET /mes/station/defect-codes`：查 ncr_defect_code（level=3 且 enabled=1，按 sort_order）映射 `[{code, label}]`；空表时返回空数组（客户端有静态兜底）
- **M1-a** `POST /mes/flash/report/station/prepare`：`{snCode, flashTypeCode}` → 复用 selectStationPending/parseStationBindings 定位待刷项，FlashTypeRegistry.getProvider().resolveContent() 预取内容；幂等（重复调用返回同一结果）；固件类返回 firmwareUrl 指向 M1-b 端点 + contentChecksum/file_md5 + contentVersion
- **M1-b** `GET /mes/flash/report/firmware/{id}/download`：X-API-KEY 鉴权 → 查 mes_firmware（status=RELEASED）→ 按 FirmwareServiceImpl 跨库先例（`DynamicDataSourceContextHolder.push("master")` 读 sys_oss）→ 从 MinIO 取流以 `StreamingResponseBody` 代理输出（不走签名 URL，因客户端下载器带 X-API-KEY 头直连本服务）；设置 Content-Disposition/Content-Length，客户端自行 MD5 校验

### 收尾：文档同步（0.5d）

- 更新 `doc/station/README.md` 契约状态表、`doc/station/plans/2026-08-21-station-device-channel-plan.md` 排期勾选；若 C2 降级语义与 nexus-station 实现有偏差，同步备注

## 四、风险与对策

| 风险 | 对策 |
|---|---|
| completeCard 为私有，C3 卡完工聚合无法直接复用 | 实现时优先在 IProFlowCardService 提公开方法；不行则复制聚合段（限定在 completeCard 单点，工作量可控） |
| C3 SN 级增量报工与既有"卡级全数分流 completeStep"语义并存 | 设备通道报工只走 mes_station_report + step 增量累加，不触碰 completeStep 全数校验路径；PC 端操作不受影响 |
| 幂等 insert-first 与业务写库需同事务 | mes_station_report 与业务表同库（ry-mes），单事务内先插幂等行再累加，DuplicateKeyException 时整体返回成功 |
| pro_andon 加列需生产库同步执行 ALTER | 增量 SQL 独立成文件，部署时按顺序执行；列可空不影响既有功能 |
| 多租户插件对新表自动注入 tenant_id | 新表带 tenant_id 列 + TenantHelper.dynamic("000000") 包裹，沿用 C1 已验证模式 |

## 五、验收标准

1. 每批次：`mvn clean package` 编译通过（对应模块），增量 SQL 可重复执行不破坏既有数据（建表/加列前 IF NOT EXISTS 或部署说明）
2. C3/C4 幂等：同一 clientTs 重复上报，mes_station_report 仅一条、step 数量仅累加一次（冒烟#6 后端验收项）
3. C5 防环：A 绑 B 后，B 再绑 A 被拒绝并返回明确 message
4. C7：呼叫后 pro_andon 落 station_code，Web 端 SSE 收到 ANDON 推送
5. 全部交付后：nexus-station 10 项冒烟清单脱离降级模式（真机验证由 nexus-station 侧执行，本计划负责后端就绪）

## 六、执行顺序与提交粒度

批次1→6 顺序执行（安灯最先解锁冒烟、过站是核心最长路径），每批次一个独立 commit：`feat(station): C7/C8 安灯设备通道` 等，SQL 变更随批次2/5 的 commit 一并入库。