# NexusERP MES 刷写能力全链路操作流程（PCBA 下单到生产）

> **版本**: v1.0
> **日期**: 2026-08-17
> **场景**: 智能网关 GW-1000 PCBA — 客户下单 → 采购 → 销售 → 生产计划 → 工单 → 固件发布 → 产线刷写（固件 + MAC） → 完工门禁 → 追溯
> **配套脚本**: `doc/mes/scripts/flash-fullchain-datagen.js`（全链路正式数据生成器，调真实 API + 系统编码规则）
> **测试数据**: `doc/mes/scripts/flash-seed-data.sql`（ry-mes 库固定雪花ID测试数据）

---

## 一、全链路编号（系统编码规则，AutoCodeUtil.genSerialCode）

每项编号由后端按 `sys_auto_code_rule` 规则自动生成（前缀+日期+流水号），**前端不传编号**：

| 单据 | 编码规则 | 编号格式 | 示例 |
|------|---------|---------|------|
| 物料编码 | MATERIAL_CODE | IF + yyyyMMdd + 3位流水 | IF20260817009 |
| BOM | — | 名称+版本 | GW-1000-PCBA标准版BOM V1.0 |
| 固件编码 | MES_FW_CODE | FW + yyyyMMdd + 3位流水 | FW20260817008 |
| 资源池编码 | MES_POOL_CODE | PL + yyyyMMdd + 3位流水 | PL20260817010 |
| 配置文件编码 | MES_CONFIG_CODE | CF + yyyyMMdd + 3位流水 | CF20260817001 |
| 客户订单号 | SO_CO_NO | CO + yyyyMMdd + 3位流水 | CO20260817007 |
| 销售订单号 | SO_ORDER_CODE | XSDD + yyyyMMdd + 3位流水 | XSDD20260817007 |
| 采购订单号 | PU_ORDER_CODE | CGDD + yyyyMMdd + 4位流水 | CGDD20260817001 |
| 生产计划号 | PRD_PLAN_CODE | SCJH + yyyy + 4位流水 | SCJH20260022 |
| 工单号 | PRD_WORK_ORDER_CODE | SCGD + yyyyMMdd + 4位流水 | SCGD202608170007 |
| 刷写记录号 | MES_FLASH_NO | FL + yyyyMMdd + 4位流水 | FL20260817001 |

---

## 二、全链路流程总览

```
①物料建档 → ②BOM+工艺路线 → ③固件发布(审批) → ④MAC资源池导入
       ↓
⑤客户订单 → ⑥销售订单 → ⑦生产计划 → ⑧工单
       ↓
⑨ERP工单下发MQ → MES工单 + 工序生成(打标FLASH + 绑固件)
       ↓
⑩产线: 扫码SN → 刷写固件(固件类) → 刷写MAC(MAC类, 从池分配)
       ↓
⑪完工门禁(多类型全PASS) → ⑫追溯/召回
```

---

## 三、分步操作

### ① 物料建档（PCBA 成品）

**路径**: 基础数据 → 物料管理

- 物料名称：智能网关PCBA GW-1000
- 规格：GW-1000-PCBA V1.0
- 类别：MACHINE（整机，category_id=43）
- 单位：件（PIECE，uom_id=26）
- 类型：自制（materialType=1，sourceType=PROCESS）
- **启用 SN 管理**（isSerialManage=1，刷写需 SN）

**API**: `POST /erp/bd/material`（不传 materialCode，后端按 MATERIAL_CODE 生成）
**生成编号**: `IF20260817009`

> 子件物料（PCB主板、结构件、芯片等）同理建档，类别挂 PARTS（零部件）或 RAW（原材料）。

### ② BOM + 工艺路线

**BOM**: `POST /erp/bd/bom`（父项物料=PCBA成品，版本1.0，挂BOM明细子件）

**工艺路线**: `bd_routing` + `bd_routing_step`
- 工艺步骤含 **FLASH 步骤**（process_code 含 FLASH/BURN 关键字，由 `nexus.mes.flash.flash-keywords` 配置）
- FLASH 步骤可绑固件 `firmware_id`（ERP侧），下发MES时透传为 `content_ref_type=FIRMWARE`

**示例工艺步骤**（routing_id=1）：
```
10 CUT 切割 | 20 WELD 焊接 | 30 ASM 装配 | 40 PKG 包装 | 50 FLASH 固件刷写(绑固件)
```

### ③ 固件发布（审批流，firmware_file 内容源）

**路径**: 刷写管理 → 固件档案

1. 新增固件：`POST /mes/flash/firmware`（不传 firmwareCode，后端按 MES_FW_CODE 生成）
   - 绑物料（GW-1000 PCBA）、版本 1.0.0、上传固件文件（服务端算 MD5/大小）
2. 上传文件：`POST /mes/flash/firmware/upload`（multipart，返回 ossId/md5/size）
3. 提交审批：`POST /mes/flash/firmware/submit/{id}` → DRAFT→IN_REVIEW
4. 审批通过：`PUT /mes/flash/firmware/approve/{id}` → IN_REVIEW→RELEASED（记录发布人/时间）
5. 设为物料默认：`PUT /mes/flash/firmware/default/{id}`（物料内 is_default=1 唯一，工单生成时自动带出）

**生成编号**: `FW20260817008`
> 仅 RELEASED 固件可被刷写/绑定工单。归档(ARCHIVED)后不可再绑定。

### ④ MAC 资源池导入（mac 内容源）

**路径**: 刷写管理 → 资源池管理

1. 新建资源池：`POST /mes/flash/pool`（不传 poolCode，后端按 MES_POOL_CODE 生成）
   - 类型 MAC、来源 INTERNAL、策略 FIFO
2. 导入资源项：`POST /mes/flash/pool/import/{poolId}`（text/plain，换行/逗号分隔，池内去重）
3. 状态看板：总数/已用，资源项 AVAILABLE/ALLOCATED/USED

**生成编号**: `PL20260817010`，导入10个 MAC

> 刷写时 MacPoolFlashProvider 自动 allocate（CAS 原子分配，一SN绑一MAC）。

### ⑤ 客户订单

**路径**: 销售管理 → 客户订单

- 客户：小米科技（client_id=2）
- 订单日期、承诺交期、状态 CONFIRMED

**API**: `POST /erp/so/customerOrder`（不传 coNo，后端按 SO_CO_NO 生成）
**生成编号**: `CO20260817007`

### ⑥ 销售订单

**路径**: 销售管理 → 销售订单

- 关联客户订单、客户、金额、信用检查 CHECKED_OK、状态 CONFIRMED

**API**: `POST /erp/so/orderheader`（不传 soNo，后端按 SO_ORDER_CODE 生成）
**生成编号**: `XSDD20260817007`

### ⑦ 生产计划

**路径**: 生产计划 → 生产计划

- 计划名称 GW-1000-PCBA 量产计划、类型 MPS、关联销售订单 referenceNo
- 物料（GW-1000 PCBA）、BOM、计划数量100、优先级

**API**: `POST /erp/prd/plan`（不传 planNo，后端按 PRD_PLAN_CODE 生成）
**生成编号**: `SCJH20260022`

### ⑧ 工单

**路径**: 生产计划 → 工单

- 关联生产计划、物料、计划数量100、车间装配车间#1、BOM、状态 CREATED

**API**: `POST /erp/prd/work`（不传 woNo，后端按 PRD_WORK_ORDER_CODE 生成）
**生成编号**: `SCGD202608170007`

### ⑨ ERP 工单下发 → MES（MQ 异步）

ERP 工单发布（`submit` 审批/`release`）后，MQ 消息 `WorkOrderReleaseMessage` → MES `WorkOrderReleaseConsumer`：
- 生成 MES 工单 `pro_work_order` + 工序 `pro_work_order_step`
- `generateStepsFromRouting` 按 ERP 工艺路线步骤生成工序，按 process_code 关键字打标（FLASH/TEST/GENERAL）
- FLASH 工序自动带出物料默认 RELEASED 固件 → `content_ref_type=FIRMWARE, content_ref_id=固件ID`
- 多类型绑定走 `step_flash_config` JSON（如固件+MAC）

### ⑩ 产线刷写作业

**路径**: 刷写管理 → 刷写作业（或产线上报）

#### 刷写固件（firmware_file）
1. 扫码 SN
2. 选刷写类型 = 刷写固件
3. 选固件下拉（仅 RELEASED，与SN物料一致）
4. PASS/FAIL 确认 → `POST /mes/flash/flash/confirm`
   - **三层防错**：SN 在制 + 固件 RELEASED + 固件物料=SN物料 + MD5匹配
   - FirmwareFileFlashProvider.resolveContent 校验 + 落 mes_flash_record（content_ref_type=FIRMWARE, content_version, content_checksum）

#### 刷写 MAC（mac, 从池分配）
1. 扫码 SN
2. 选刷写类型 = 刷写MAC
3. 显示"从MAC池自动分配"（无需选内容）
4. PASS 确认 → `POST /mes/flash/flash/confirm`
   - MacPoolFlashProvider.resolveContent → allocate 原子分配一 MAC（一SN绑一MAC）
   - 落 mes_flash_record（content_ref_type=MAC_POOL, content_ref_id=资源项ID, content_payload={"mac":"00:1A:..."})

### ⑪ 完工门禁

流转卡工序完工（completeStep / 派工聚合 completeDispatch→checkAndCompleteStep）触发 `checkFlashStepGate`：
1. 解析工序刷写绑定（step_flash_config 多类型优先，否则单类型 content_ref）
2. 对每个绑定项 × 卡上每个在制 SN：查 mes_flash_record 最新一条
3. 任一未 PASS → 按 `mes.flash.gate.mode`：
   - **FORCE**（默认）：抛异常拦截完工
   - **WARN**：log 告警放行

> 多类型工序（固件+MAC）必须**两者都 PASS** 才放行。

### ⑫ 追溯与召回

- **正向**：SN 详情 → 刷写记录（flash_type_code + content_ref + content_payload，如 {"mac":"00:1A:..."}）
- **反向召回**：按 mes_flash_record.content_ref_id 反查 SN 清单（固件类=固件ID，MAC类=MAC资源项ID），联 pro_sn.sn_status（COMPLETED在库/IN_PRODUCTION在制/SHIPPED已发运/SCRAPPED报废）

---

## 四、产线工具对接

### 上报接口（report/flash/auto）
```http
POST /mes/flash/report/flash/auto
Header: X-API-KEY: nexus-mes-api-2026
[
  {"snCode":"SN...","flashTypeCode":"firmware_file","contentRefId":固件ID,"result":"PASS"},
  {"snCode":"SN...","flashTypeCode":"mac","result":"PASS"}
]
```
响应：`{success: N, failed: [{snCode, reason}]}`

### 工位编排接口（station/pending）
```http
GET /mes/flash/report/station/pending?snCode=SN...
Header: X-API-KEY: nexus-mes-api-2026
```
返回该 SN 工单刷写工序待刷清单（含 latestResult）。

---

## 五、冒烟测试

```bash
# 全链路正式数据生成(调真实API+系统编码规则)
node doc/mes/scripts/flash-fullchain-datagen.js   # 14-15/15 通过

# P0-P3冒烟(MAC全链路)
node doc/mes/scripts/flash-smoke-test.js          # 10/10

# 工位编排验证
curl "http://localhost:8080/mes/flash/report/station/pending?snCode=SN202606200001" -H "X-API-KEY: nexus-mes-api-2026"
```

---

## 六、配置项

| 键 | 默认 | 说明 |
|----|------|------|
| mes.flash.gate.mode | FORCE | 门禁 FORCE/WARN/关闭 |
| mes.api-key | nexus-mes-api-2026 | 产线上报/工位接口 X-API-KEY |
| nexus.mes.flash.flash-keywords | FLASH,BURN | 工序打标刷写关键字 |
| nexus.mes.flash.test-keywords | FCT,TEST,FUNC | 工序打标测试关键字 |
| security.excludes | /mes/flash/report/** | 上报/工位接口放行 |
