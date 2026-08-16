# NexusERP MES 智能硬件固件刷写与测试管理方案 v2.0（代码核查定稿版）

> **文档版本**: v2.0（相对 v1.0 按当前代码逐项核查修订）
> **修订日期**: 2026-08-16
> **状态**: 已核查定稿 → 待用户批准 → 进入执行
> **v1.0 出处**: `./2026-08-13-mes-firmware-flash-test-plan.md`
> **核查对象**: `D:\workspace\nexus-erp` 当前工作区代码（`ruoyi-mes` / `ruoyi-flowable` / `ruoyi-system` / `plus-ui` / `script/sql`）

---

## 目录

- [〇、v1.0 代码核查结论（本次修订依据）](#〇v10-代码核查结论本次修订依据)
- [一、项目概述](#一项目概述)
- [二、业务需求与流程设计](#二业务需求与流程设计)
- [三、数据模型设计（已修正）](#三数据模型设计已修正)
- [四、接口设计（已修正）](#四接口设计已修正)
- [五、技术实现方案（已修正）](#五技术实现方案已修正)
- [六、开发计划（最终版）](#六开发计划最终版)
- [七、验收标准](#七验收标准)
- [八、风险评估与应对策略](#八风险评估与应对策略)
- [附录 A 字典/菜单/编码规则/配置清单](#附录-a-字典菜单编码规则配置清单)
- [附录 B 测试软件对接注意事项](#附录-b-测试软件对接注意事项)

---

## 〇、v1.0 代码核查结论（本次修订依据）

对 v1.0 全部假设逐项核对当前工作区，结论如下：

| # | v1.0 假设 | 核查结果 | 处置 |
|---|-----------|---------|------|
| 1 | Flowable 业务审批可完整复用（`WfCallBackServiceI` + 范本 `SopDocumentServiceImpl`） | ✅ 属实。`WfCallBackServiceI` 位于 `ruoyi-modules/ruoyi-flowable/.../workflow/service`；`SopDocumentServiceImpl` 位于 `ruoyi-mes/.../sop/service/impl`，含 `submitApproval`→`processService.startProcessByDataId(id, selectFlowId, "sopDocumentService", vars)`、`afterFlowHandle`（`ActStatus.doing/pass/reject/retrun/revoke/stop/recall` 中文常量）、管理员兜底 `approve/reject/archive`、Flowable 异常降级 IN_REVIEW | 维持；仅兜底接口命名统一为 `approve`/`reject`（非 `refuse`，见修正 #4） |
| 2 | `pro_flow_card_step.wo_step_id → pro_work_order_step` 关联成立 | ✅ 属实。`pro_flow_card_step.wo_step_id` 注释即"关联工单工序步骤ID(pro_work_order_step.step_id)" | 维持 |
| 3 | `completeStep` / `completeDispatch` 作为门禁钩子 | ✅ 属实。`ProFlowCardServiceImpl.completeStep(cardId, stepRecordId, goodQty, defectQty, scrapQty)`、`ProDispatchServiceImpl.completeDispatch(dispatchId, ...)` 均存在；`completeStep` 同被出站(P0-3)校验路径调用，门禁放其方法开头即可覆盖 | 维持；补充 `dispatch` 侧映射口径（见修正 #6） |
| 4 | 手动编码接入"现有编码生成规则服务" | ✅ 属实且服务可跨模块用：`SysAutoCodeController`(`/system/autocode/get|preview/{ruleCode}`) + `AutoCodeUtil`(`ruoyi-system/.../strategy`)，**`SopDocumentServiceImpl` 已在 ruoyi-mes 直接注入 `AutoCodeUtil.genSerialCode("MES_SOP_CODE", null)`**，说明 ruoyi-mes 依赖 ruoyi-system 成立 | 维持：新增 `MES_FW_CODE`/`MES_FLASH_NO`/`MES_TEST_NO` 三条规则落 `sys_auto_code_rule` + `sys_auto_code_part` |
| 5 | 新表沿用"ry-mes.sql 既有约定" | ⚠️ **v1.0 DDL 出错**：当前 `ry-mes.sql` 全部 MES 表 `tenant_id` 均为 `varchar(20) NOT NULL DEFAULT '000000'`，实体基类 `TenantEntity.tenantId` 为 `String`；v1.0 DDL 用 `BIGINT ... DEFAULT 0` 与多租户插件/既有表 JOIN 冲突 | **必改**：4 张新表 tenant_id 一律 `varchar(20) NOT NULL DEFAULT '000000'`，唯一键含 tenant_id，另加 `idx_tenant_id`（见第三章） |
| 6 | 工序如何判定 FLASH/TEST（step_type 来源） | ⚠️ **v1.0 未定义**：`generateStepsFromRouting`（`ProWorkOrderServiceImpl`）只落 `sequence_num/process_id/process_code/process_name/workstation_*/is_key_process`，ERP 工艺路线无 step_type——v1.0 §2.3"刷写工序自动带出固件"缺前置条件 | **补决策**：下发生成时按 `process_code` 关键字映射自动打标（配置化），并允许计划员在工单工序页改标（见 §2.3 决策 6） |
| 7 | 前端"遵循 `useCrud` / `AutoGenCodeField` 既有标准模式" | ⚠️ **部分不实**：`useCrud` 存在（`plus-ui/src/composables/useCrud.ts`，ERP 列表页广泛使用）；**仓库内无 `AutoGenCodeField` 组件**；SOP 页实际用 `previewCode('MES_SOP_CODE')`（`@/api/system/autocode/rule`）+ `file-upload` 组件 | 修正：列表页用 `useCrud`，审批/上传页仿 SOP 页，自动编码用 `previewCode`/`genCode` |
| 8 | `is_default` 物料维度唯一、`status` 状态机、admin 兜底命名 `approve/refuse` | ⚠️ 命名不一致：SOP 兜底方法为 `approve`/`reject`（非 refuse） | 修正：`approve/{id}`、`reject/{id}` |
| 9 | 测试软件上报 `POST /mes/fw/test-record/report` 幂等 | ✅ 设计合理；幂等键 `test_no` + 唯一索引 `uk_test_no_item` | 维持；重复上报返回已存结果 |
| 10 | 召回"联 pro_sn.sn_status 与出库状态" | ✅ `pro_sn.sn_status` 已含 `IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED`，`SHIPPED` 即可表"已发运" | 维持；明确映射：在库=COMPLETED、在制=IN_PRODUCTION、已发运=SHIPPED、报废=SCRAPPED |
| 11 | 自动编码/固件文件 MD5 回填 | ⚠️ 现 OSS 上传接口返回项不含 MD5，需自行计算 | 补充：新增专用上传接口 `/mes/fw/firmware/upload` 服务端算 MD5（Spring `DigestUtils`）与大小（见 §4.1） |

> 除上表外，v1.0 的 API 路径（`/workflow/process/startByDataId/{dataId}/{selectFlowId}/{serviceName}`、`/workflow/process/getProcesss?serviceName=`、`/workflow/task/complete|reject|stopProcess|recallProcess` 等）与 Flowable 控制器实际一致，予以维持。

---

## 一、项目概述

### 1.1 项目背景

NexusERP 的 MES 模块已完成工单（`ProWorkOrder` + `pro_work_order_step`）、流转卡（`ProFlowCard` + `pro_flow_card_step`，经 `wo_step_id` 关联工序）、派工（`ProDispatch`）、SN（`ProSn`，含 `sn_status`/`current_step_seq`）、追溯（`ProTraceability` + `lt_sn_binding`）、三检（`qc_*`）、设备（`eq_equipment`）、数据采集（`dc_*`）、SOP（`sop_*` 审批范式）、NCR/返工、工装（`tg_*`）等能力。

当生产对象为智能硬件（IoT 网关、智能终端、采集器）时，存在三类当前空白的需求：**固件发布管理**、**固件刷写防错留痕**、**整机全检测试**。

### 1.2 项目目标

| 维度 | 当前 | 目标 |
|------|------|------|
| 固件版本管理 | 无 | 固件档案 + **审批发布**（Flowable，复用 SOP 范式），仅 RELEASED 可上产线 |
| 固件-订单绑定 | 无 | 工单刷写工序绑定固件版本，自动带出物料默认版本，可多固件 |
| 刷写留痕 | 无 | SN 级刷写记录（版本/MD5/设备/结果），防错校验 |
| 测试全检 | 无 | 测试项标准主数据 + **SN 级测试记录** + 自动判定 + 过站门禁 |
| 测试数据来源 | 无 | **测试软件 API 自动上报为主**，人工录入兜底 |
| 追溯与召回 | SN 追溯已有 | 固件反查 SN 清单（召回），SN 详情含刷写/测试记录 |

### 1.3 需求决策确认表（含本次新增决策）

| # | 决策点 | 确认结论 |
|---|--------|---------|
| 1 | 测试方式 | **逐台 SN 全检**（抽检类后续可复用 `QcIpqc`） |
| 2 | 测试数据上报 | **测试软件调用 MES API 自动上报**为主，人工录入兜底 |
| 3 | 刷写/测试设备 | 人工扫码确认即可，本期不接 IoT 自动采集 |
| 4 | 多固件 | 一个产品可多固件（主控/通信模组分刷写工序各绑一个），固件行均挂在**产品物料**下以 `firmware_code` 区分（如 `FW-MAIN`/`FW-RF`） |
| 5 | 固件文件 | 存 MinIO（复用 OSS），服务端算 MD5 + 大小回填 |
| 6 | **工序打标（新增）** | 下发生成时按 `process_code` 关键字自动标 `step_type`（`nexus.mes.fw` 配置，默认 FLASH∈[FLASH,BURN]，TEST∈[FCT,TEST,FUNC]），计划员可在工单工序页改标；旧数据默认 GENERAL 无感 |
| 7 | **门禁开关（新增）** | `sys_config` 键 `mes.fw.gate.mode`：`FORCE`（默认，强制拦截）/ `WARN`（仅告警放行）；无 SN 的工单门禁视为通过 |
| 8 | 编码规则（新增） | `firmware_code`→`MES_FW_CODE`、`flash_no`→`MES_FLASH_NO`、`test_no`→`MES_TEST_NO`，走 `AutoCodeUtil.genSerialCode`（同 `MES_SOP_CODE`） |

### 1.4 项目干系人

产品负责人/项目经理/后端/前端/QA/AI Agent（同 v1.0）。

---

## 二、业务需求与流程设计

### 2.1 总体业务场景

```
研发发布固件版本
    │ (审批流: DRAFT → IN_REVIEW → RELEASED，范本 SOP)
    ▼
固件档案 (fw_firmware, MinIO + MD5, 按物料多版本)
    │
    ▼
ERP 工单下发 ──MQ──► MES 工单 (pro_work_order)
    │                  generateStepsFromRouting: process_code 关键字 → step_type 打标
    │                  FLASH 工序: firmware_id 自动带出(物料默认 RELEASED, 可改绑)
    ▼                ▼
刷写作业: 扫码SN → 三层一致性校验 → 人工确认 PASS/FAIL → fw_flash_record
    │                FAIL → 可重刷(retry_count+1) / 多次FAIL转NCR
    ▼
测试作业: 扫码SN → 服务端按(物料+固件版本)出测试项清单
    │       ├─ AUTO: 测试软件 POST /mes/fw/test-record/report（服务端权威判定）
    │       └─ MANUAL: 测试员逐项录入，即时判定
    ▼
完工报工门禁: completeStep/completeDispatch 开头校验 FLASH/TEST 工序 SN 全 PASS，否则拦截
    ▼
追溯: SN详情(固件+测试) / 固件反查SN清单(召回, 含在库/已发运)
```

### 2.2 固件发布审批流程

完全复用 SOP 审批模式（**零 Flowable 底层开发**，代码模板 `SopDocumentServiceImpl`）：

```
1. 固件创建(DRAFT) → 上传固件文件(/mes/fw/firmware/upload, 服务端算MD5) → 保存 select_flow_id
2. 发起审批: POST /workflow/process/startByDataId/{firmwareId}/{selectFlowId}/fwFirmwareService
   └─ 引擎写 wf_my_business, afterFlowHandle(doing) → IN_REVIEW；Flowable 异常降级 IN_REVIEW
3. 审批节点(建议): 研发/测试负责人审批 → 生产与质量负责人会签 → 结束
4. afterFlowHandle(ActStatus):
   ├─ pass   → RELEASED(记录发布人/时间; 勾选默认则置物料默认版本, is_default 物料内唯一)
   ├─ doing  → IN_REVIEW
   └─ 其余( reject/retrun/revoke/stop/recall ) → DRAFT
5. 仅 RELEASED 可被工单绑定/刷写; RELEASED → ARCHIVED(归档, 不再可新绑定)
```

**管理兜底**（与 SOP 同名）：`POST /mes/fw/firmware/approve/{id}`、`POST /mes/fw/firmware/reject/{id}`（CAS 状态机守卫，非 DRAFT/IN_REVIEW 拒绝）。

### 2.3 固件绑定生产订单与工序打标

- 绑定落点为 **MES 工单工序** `pro_work_order_step`（`step_type` + `firmware_id`），向上经 `mes_wo_id → erpWoId` 可追溯 ERP 工单/计划/销售订单；
- **工序打标（决策 6）**：`ProWorkOrderServiceImpl.generateStepsFromRouting` 扩展——按 `process_code` 匹配 `nexus.mes.fw.flash-keywords`/`test-keywords` 落 `step_type`；工单工序编辑接口允许计划员改标；**FLASH 工序必绑 RELEASED 固件**（发布/编辑校验），TEST 工序无需绑定；
- **固件自动带出**：FLASH 工序生成后自动刷 `firmware_id` = 物料(`materialId`) 当前 `is_default=1 且 status=RELEASED` 的固件，计划员可改绑（下拉仅 RELEASED）；
- **刷写二次校验**：SN 物料 → 工单工序绑定固件 → 实际刷写固件 三层一致，不一致拦截。

### 2.4 固件刷写流程

```
刷写工序(step_type=FLASH) → 扫码SN(pro_sn.current_step_seq 定位)
  → 前端展示: 工单绑定固件 + 固件文件MD5
  → 人工确认 PASS/FAIL(填失败原因)
  → 写 fw_flash_record(版本+MD5+设备+操作人+时间+结果)
  → FAIL: 允许重刷(retry_count+1); 多次FAIL可转NCR返工
```

服务端强制校验（`POST /mes/fw/flash-record/confirm`，任一不满足 400）：
1. SN 存在、未报废、在制（`sn_status = IN_PRODUCTION`）；
2. SN 当前工序为 FLASH 工序（`current_step_seq` → `pro_work_order_step.step_type = FLASH`，经 `pro_flow_card_step.wo_step_id` 或 `sequence_num` 关联）；
3. 固件 `status = RELEASED` 且 `firmware.materialId = sn.materialId`；
4. 工单工序已绑固件 → 必须一致；未绑定时以物料匹配为准并在响应提示"绑定缺失"。

### 2.5 整机测试流程（全检）

```
测试工序(step_type=TEST) → 扫码SN
  → 服务端按 SN 物料 + 测试时固件版本 查 fw_test_item(特定版本优先, 回退通用 firmware_id=0)
  → AUTO: POST /mes/fw/test-record/report 批量上报(服务端权威判定, ≤100项)
  → MANUAL: 测试员逐项录入, 即时判定
  → 全部 PASS → 允许过站; 任一 FAIL → 锁 SN, 可重测(新 test_no 覆盖判定), 多次FAIL转NCR
```

**判定规则（服务端权威）**：
- `NUMERIC`：`lower_limit ≤ 实测值 ≤ upper_limit`（限值可空则单边）；
- `ENUM`：实测文本 ∈ `pass_enum`；
- `TEXT`：以客户端传 `result` 为准（目视/听音类人工项）。

**测试时固件版本优先级**：上报显式 `firmwareVersion` > SN 最近一条 PASS 刷写记录版本 > 工单工序绑定版本。

**判定口径**：同一 SN 同一测试工序，**最新 `test_no` 的全部必测项 PASS 才算通过**；上报缺失必测项直接判 FAIL。

### 2.6 追溯与召回

- 正向：SN 详情页 → 刷写记录 + 测试记录（逐项实测/判定/标准快照）；
- 反向（召回）：`fw_flash_record.firmware_id` 反查 SN 清单 → 联 `pro_sn.sn_status`：`SHIPPED`=已发运、`COMPLETED`=在库、`IN_PRODUCTION`=在制、`SCRAPPED`=报废；
- 测试记录标准值全快照冗余，历史判定与追溯不受标准修改影响。

---

## 三、数据模型设计（已修正）

> 全部落 **ry-mes 库**。**修正要点（核查结论 #5）**：`tenant_id` 一律 `varchar(20) NOT NULL DEFAULT '000000'`（与 `ry-mes.sql` 全部 MES 表及 `TenantEntity.tenantId = String` 一致），唯一键与 `idx_tenant_id` 含 tenant_id。其余字段约定（`create_dept/create_by BIGINT`、`del_flag CHAR(1) '0'`、utf8mb4/utf8mb4_unicode_ci）维持。DDL 以 `script/sql/update/` 增量脚本落地（含建表 + 字典 + 菜单 + 编码规则，见附录 A）。

### 3.1 `fw_firmware` — 固件档案

```sql
CREATE TABLE fw_firmware (
  firmware_id     BIGINT        NOT NULL COMMENT '固件ID',
  firmware_code   VARCHAR(64)   NOT NULL COMMENT '固件编码(自动编码 MES_FW_CODE)',
  firmware_name   VARCHAR(128)  NOT NULL COMMENT '固件名称',
  material_id     BIGINT        NOT NULL COMMENT '适用物料ID(产品)',
  material_code   VARCHAR(64)   NOT NULL COMMENT '适用物料编码',
  material_name   VARCHAR(128)  NOT NULL COMMENT '适用物料名称',
  version         VARCHAR(32)   NOT NULL COMMENT '版本号(如 1.2.3)',
  build_no        VARCHAR(64)   NULL COMMENT '构建号',
  oss_id          BIGINT        NULL COMMENT '固件文件OSS文件ID(sys_oss)',
  file_md5        VARCHAR(64)   NULL COMMENT '固件文件MD5',
  file_size       BIGINT        NULL COMMENT '固件文件大小(字节)',
  release_note    TEXT          NULL COMMENT '发布说明',
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  is_default      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '是否物料默认版本(0否/1是, 物料内唯一)',
  select_flow_id  BIGINT        NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  published_by    BIGINT        NULL COMMENT '发布人ID',
  publish_time    DATETIME      NULL COMMENT '发布时间',
  tenant_id       varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_dept     BIGINT        NULL COMMENT '创建部门',
  create_by       BIGINT        NULL COMMENT '创建人',
  create_time     DATETIME      NULL COMMENT '创建时间',
  update_by       BIGINT        NULL COMMENT '更新人',
  update_time     DATETIME      NULL COMMENT '更新时间',
  del_flag        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  remark          VARCHAR(500)  NULL COMMENT '备注',
  PRIMARY KEY (firmware_id),
  UNIQUE KEY uk_tenant_firmware_code (tenant_id, firmware_code),
  KEY idx_tenant_id (tenant_id),
  KEY idx_material (material_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='固件档案';
```

### 3.2 `pro_work_order_step` — 工单工序扩展

```sql
ALTER TABLE pro_work_order_step
  ADD COLUMN step_type   VARCHAR(16) NOT NULL DEFAULT 'GENERAL' COMMENT '工序类型: GENERAL/FLASH/TEST' AFTER sequence_num,
  ADD COLUMN firmware_id BIGINT      NULL COMMENT '固件ID(仅FLASH工序, 只可选RELEASED)' AFTER step_type,
  ADD KEY idx_step_type (tenant_id, step_type),
  ADD KEY idx_firmware (firmware_id);
```

实体 `ProWorkOrderStep` 同步加 `stepType`/`firmwareId` 字段，Bo/Vo 相应扩展；执行层判断走 `pro_flow_card_step.wo_step_id → pro_work_order_step`（流转卡表不改）。

### 3.3 `fw_flash_record` — 固件刷写记录（SN 级）

```sql
CREATE TABLE fw_flash_record (
  flash_record_id  BIGINT        NOT NULL COMMENT '刷写记录ID',
  flash_no         VARCHAR(64)   NOT NULL COMMENT '刷写记录号(自动编码 MES_FLASH_NO)',
  sn_id            BIGINT        NOT NULL COMMENT 'SN ID',
  sn_code          VARCHAR(64)   NOT NULL COMMENT 'SN编码',
  mes_wo_id        BIGINT        NOT NULL COMMENT 'MES工单ID',
  wo_no            VARCHAR(64)   NULL COMMENT '工单号',
  card_id          BIGINT        NULL COMMENT '流转卡ID',
  step_record_id   BIGINT        NULL COMMENT '流转卡工序记录ID(pro_flow_card_step)',
  process_id       BIGINT        NULL COMMENT '工序ID',
  process_code     VARCHAR(64)   NULL COMMENT '工序编码',
  process_name     VARCHAR(128)  NULL COMMENT '工序名称',
  firmware_id      BIGINT        NOT NULL COMMENT '固件ID',
  firmware_version VARCHAR(32)   NOT NULL COMMENT '固件版本',
  file_md5         VARCHAR(64)   NULL COMMENT '实际刷写文件MD5',
  equipment_id     BIGINT        NULL COMMENT '刷写设备ID(eq_equipment)',
  equipment_code   VARCHAR(64)   NULL COMMENT '刷写设备编码',
  operator_id      BIGINT        NULL COMMENT '操作人ID',
  operator_name    VARCHAR(64)   NULL COMMENT '操作人姓名',
  flash_time       DATETIME      NULL COMMENT '刷写时间',
  result           VARCHAR(8)    NOT NULL COMMENT 'PASS/FAIL',
  fail_reason      VARCHAR(255)  NULL COMMENT '失败原因',
  retry_count      INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
  flash_mode       VARCHAR(8)    NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO/MANUAL',
  tenant_id        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_dept      BIGINT        NULL,
  create_by        BIGINT        NULL,
  create_time      DATETIME      NULL,
  update_by        BIGINT        NULL,
  update_time      DATETIME      NULL,
  del_flag         CHAR(1)       NOT NULL DEFAULT '0',
  remark           VARCHAR(500)  NULL COMMENT '备注',
  PRIMARY KEY (flash_record_id),
  UNIQUE KEY uk_tenant_flash_no (tenant_id, flash_no),
  KEY idx_tenant_id (tenant_id),
  KEY idx_sn (sn_id),
  KEY idx_firmware (firmware_id),
  KEY idx_wo (mes_wo_id)
) ENGINE=InnoDB COMMENT='固件刷写记录(SN级)';
```

同一 SN 允许多条（FAIL 重刷保留历史），判定取**最新一条**；`idx_firmware` 支撑召回。

### 3.4 `fw_test_item` — 测试项标准

```sql
CREATE TABLE fw_test_item (
  test_item_id  BIGINT         NOT NULL COMMENT '测试项ID',
  item_code     VARCHAR(64)    NOT NULL COMMENT '测试项编码(如 RF-TXPWR)',
  item_name     VARCHAR(128)   NOT NULL COMMENT '测试项名称',
  material_id   BIGINT         NOT NULL COMMENT '适用物料ID',
  firmware_id   BIGINT         NOT NULL DEFAULT 0 COMMENT '固件ID(0=该物料所有版本通用)',
  data_type     VARCHAR(8)     NOT NULL DEFAULT 'NUMERIC' COMMENT 'NUMERIC/TEXT/ENUM',
  unit          VARCHAR(16)    NULL COMMENT '单位',
  upper_limit   DECIMAL(18,4)  NULL COMMENT '上限',
  lower_limit   DECIMAL(18,4)  NULL COMMENT '下限',
  target_value  DECIMAL(18,4)  NULL COMMENT '目标值',
  pass_enum     VARCHAR(500)   NULL COMMENT 'ENUM合格值(JSON数组)',
  is_required   CHAR(1)        NOT NULL DEFAULT '1' COMMENT '是否必测(0否/1是)',
  sort_order    INT            NOT NULL DEFAULT 0,
  remark        VARCHAR(500)   NULL,
  tenant_id     varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_dept   BIGINT         NULL,
  create_by     BIGINT         NULL,
  create_time   DATETIME       NULL,
  update_by     BIGINT         NULL,
  update_time   DATETIME       NULL,
  del_flag      CHAR(1)        NOT NULL DEFAULT '0',
  PRIMARY KEY (test_item_id),
  UNIQUE KEY uk_tenant_material_item (tenant_id, material_id, firmware_id, item_code),
  KEY idx_tenant_id (tenant_id),
  KEY idx_material (material_id)
) ENGINE=InnoDB COMMENT='测试项标准主数据';
```

`firmware_id=0` 通用项；特定版本项用真实固件 ID，取数按固件精确匹配，未命中回退通用项。

### 3.5 `fw_test_record` — 测试记录（SN 级全检）

```sql
CREATE TABLE fw_test_record (
  test_record_id   BIGINT         NOT NULL COMMENT '测试记录ID',
  test_no          VARCHAR(64)    NOT NULL COMMENT '测试单号(一次SN测试一条单号, 共享多行)',
  sn_id            BIGINT         NOT NULL COMMENT 'SN ID',
  sn_code          VARCHAR(64)    NOT NULL COMMENT 'SN编码',
  mes_wo_id        BIGINT         NOT NULL COMMENT 'MES工单ID',
  wo_no            VARCHAR(64)    NULL,
  card_id          BIGINT         NULL,
  step_record_id   BIGINT         NULL COMMENT '流转卡工序记录ID(测试工序)',
  process_id       BIGINT         NULL,
  process_code     VARCHAR(64)    NULL,
  process_name     VARCHAR(128)   NULL,
  firmware_id      BIGINT         NULL COMMENT '测试时固件ID',
  firmware_version VARCHAR(32)    NULL COMMENT '测试时固件版本',
  test_item_id     BIGINT         NOT NULL COMMENT '测试项ID',
  item_code        VARCHAR(64)    NOT NULL COMMENT '测试项编码(快照)',
  item_name        VARCHAR(128)   NOT NULL COMMENT '测试项名称(快照)',
  data_type        VARCHAR(8)     NOT NULL COMMENT '类型(快照)',
  unit             VARCHAR(16)    NULL,
  numeric_value    DECIMAL(18,4)  NULL COMMENT '实测数值',
  text_value       VARCHAR(500)   NULL COMMENT '实测文本',
  upper_limit      DECIMAL(18,4)  NULL COMMENT '上限(快照)',
  lower_limit      DECIMAL(18,4)  NULL COMMENT '下限(快照)',
  target_value     DECIMAL(18,4)  NULL COMMENT '目标值(快照)',
  pass_enum        VARCHAR(500)   NULL COMMENT 'ENUM合格值(快照)',
  result           VARCHAR(8)     NOT NULL COMMENT 'PASS/FAIL',
  tester_id        BIGINT         NULL,
  tester_name      VARCHAR(64)    NULL,
  equipment_id     BIGINT         NULL,
  equipment_code   VARCHAR(64)    NULL,
  test_time        DATETIME       NULL,
  test_mode        VARCHAR(8)     NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO/MANUAL',
  remark           VARCHAR(500)   NULL,
  tenant_id        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  create_dept      BIGINT         NULL,
  create_by        BIGINT         NULL,
  create_time      DATETIME       NULL,
  update_by        BIGINT         NULL,
  update_time      DATETIME       NULL,
  del_flag         CHAR(1)        NOT NULL DEFAULT '0',
  PRIMARY KEY (test_record_id),
  UNIQUE KEY uk_tenant_test_no_item (tenant_id, test_no, test_item_id),
  KEY idx_tenant_id (tenant_id),
  KEY idx_sn (sn_id),
  KEY idx_firmware (firmware_id),
  KEY idx_wo (mes_wo_id)
) ENGINE=InnoDB COMMENT='测试记录(SN级全检)';
```

- 一次测试（同一 `test_no`）多行：该 SN 该测试工序每个必测项一行；
- `uk_tenant_test_no_item` 保证同单号同项不重复（**上报幂等**）；
- 标准值快照冗余；判定口径：同一 SN 同一测试工序最新 `test_no` 全部 PASS 才算通过。

### 3.6 字典 / 菜单 / 编码规则 / 配置

见 [附录 A](#附录-a-字典菜单编码规则配置清单)。菜单挂在 MES 目录下「固件管理」子目录，含固件档案/刷写作业/测试作业/测试记录/测试项标准五页 + 工单详情/SN 追溯扩展。

---

## 四、接口设计（已修正）

### 4.1 后端接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mes/fw/firmware/list` | 固件分页（状态/物料/关键字） |
| GET | `/mes/fw/firmware/{id}` | 固件详情 |
| POST | `/mes/fw/firmware` | 新增（自动编码 `MES_FW_CODE`；仅 DRAFT 可编辑/删除） |
| PUT | `/mes/fw/firmware` | 修改（仅 DRAFT；改默认版本时物料内唯一） |
| DELETE | `/mes/fw/firmware/{ids}` | 删除（仅 DRAFT） |
| POST | `/mes/fw/firmware/upload` | **专用上传**：multipart `file` → OSS 存储 + 服务端算 MD5/大小 → 返回 `{ossId,md5,size,url}` |
| GET | `/mes/fw/firmware/available?materialId=` | 可选固件下拉（仅 RELEASED，工单绑定用） |
| POST | `/mes/fw/firmware/archive/{id}` | 归档（RELEASED → ARCHIVED） |
| POST | `/mes/fw/firmware/approve/{id}` | 管理员强制通过（兜底，同 SOP `approve` 语义） |
| POST | `/mes/fw/firmware/reject/{id}` | 管理员强制驳回（兜底，同 SOP `reject` 语义） |
| GET | `/mes/fw/firmware/flows` | 该业务可选审批流（包装 `GET /workflow/process/getProcesss?serviceName=fwFirmwareService`） |
| — | 审批发起/待办/已办 | 复用 `/workflow/process/startByDataId/{firmwareId}/{selectFlowId}/fwFirmwareService` 与现有 `/workflow/*` 页面 |
| GET | `/mes/fw/flash-record/list` | 刷写记录分页（SN/工单/固件/结果过滤） |
| POST | `/mes/fw/flash-record/confirm` | 人工扫码确认刷写（PASS/FAIL，校验见 §2.4） |
| GET | `/mes/fw/test-item/list` | 测试项标准分页（物料/固件过滤） |
| POST/PUT/DELETE | `/mes/fw/test-item` | 测试项标准 CRUD |
| POST | `/mes/fw/test-record/report` | **测试软件自动上报**（AUTO，协议见 §4.2） |
| POST | `/mes/fw/test-record/manual` | 人工逐项录入（MANUAL，服务端即时判定） |
| GET | `/mes/fw/test-record/list` | 测试记录分页 |
| GET | `/mes/fw/test-record/summary?snCode=` | 某 SN 各测试工序最新判定汇总（门禁判定用） |
| GET | `/mes/fw/sn-trace/{snCode}` | SN 追溯：刷写 + 测试记录 |
| GET | `/mes/fw/recall/{firmwareId}` | 固件召回：刷过该版本全部 SN（含在库/已发运状态） |

> 勾稽 v1.0 → v2.0：`refuse` 更名 `reject`（#4）；新增 `/upload`（#11）；其余维持。权限注解统一 `mes:fw:*`（见附录 A）。

### 4.2 测试软件上报协议（AUTO）

`POST /mes/fw/test-record/report`，`Content-Type: application/json`，请求头带现有 `Authorization`（测试电脑走专用受限账号，见附录 B）。

```json
{
  "snCode": "SN202608130001",
  "testNo": "FT2026081300001",
  "equipmentCode": "T-001",
  "testerName": "张三",
  "firmwareVersion": "1.2.3",
  "items": [
    { "itemCode": "RF-TXPWR", "numericValue": 18.5 },
    { "itemCode": "VOLT-3V3", "numericValue": 3.29 },
    { "itemCode": "VISION",   "textValue": "OK" },
    { "itemCode": "VIBRATE",  "result": "PASS" }
  ]
}
```

响应（服务端权威判定）：`{ testNo, overallResult: PASS|FAIL, items:[{itemCode, result, reason?}] }`。

规则：
1. 服务端按 `snCode` 定位 SN（校验在制与当前工序为 TEST），自动关联工单/流转卡/测试工序上下文；未传 `test_no` 服务端以 `MES_TEST_NO` 生成；
2. 测试项清单 = 物料 + 固件版本匹配（特定版本优先，回退通用）；**缺失必测项判 FAIL**；
3. `result` 服务端计算（NUMERIC 比较 / ENUM 匹配），客户端 `result` 仅 TEXT 类有效；
4. **幂等**：`test_no` 已存在 → 返回已存结果，不重复落库；
5. 单次 ≤ 100 项，超限返回 400 提示分批。

### 4.3 刷写确认校验规则（MANUAL）

`POST /mes/fw/flash-record/confirm`（请求体见 v1.0 §4.3，字段维持 `snCode/firmwareId/equipmentCode/result/failReason`）。服务端强制校验（任一不满足返回 400 拦截）：SN 在制且当前工序为 FLASH；固件 RELEASED 且物料匹配；与工单工序绑定一致（未绑定以物料匹配为准并提示）；PASS 重刷正常追加（`retry_count` 继承历史条数）。

### 4.4 过站门禁（FLASH / TEST）

在 `ProFlowCardServiceImpl.completeStep` 与 `ProDispatchServiceImpl.completeDispatch` **方法开头**（状态 CAS 之前）插入门禁，仅当目标工序 `step_type ∈ {FLASH, TEST}` 时执行：

- **FLASH**：该工序作用域内（cardId + stepRecordId / dispatchId → 工序）已生成 SN 中，无最新一条 PASS 刷写记录的 SN 数量 > 0 → 拦截（提示缺失 SN 清单数量）。
- **TEST**：该工序作用域内 SN 中，最新 `test_no` 存在 FAIL 或必测项缺失的 SN 数量 > 0 → 拦截。
- 量**末次判定口径**：FLASH 取最新一条 `fw_flash_record.result`；TEST 取最新 `test_no` 全项。
- 门禁模式读 `sys_config` 键 `mes.fw.gate.mode`：`FORCE` 拦截 / `WARN` 仅告警放行（默认 `FORCE`）；无 SN 的工单视为通过。
- 映射口径（核查结论 #3/#6 补充）：
  - `completeStep`：`stepRecordId → pro_flow_card_step → wo_step_id → pro_work_order_step.step_type/firmware_id`；
  - `completeDispatch`：`ProDispatch.stepSeq → pro_work_order_step(tenant_id=?, mes_wo_id, sequence_num=stepSeq) → step_type`。

---

## 五、技术实现方案（已修正）

### 5.1 后端（`ruoyi-mes` 新增 `fw` 子包）

```
ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/fw/
  controller/  FwFirmwareController / FwFlashRecordController / FwTestItemController
               FwTestRecordController / FwReportController
  domain/      FwFirmware / FwFlashRecord / FwTestItem / FwTestRecord
  domain/bo/   FwFlashConfirmBo / FwTestReportBo / FwTestReportItemBo / FwTestManualBo / FwFirmwareUploadVo
  domain/vo/   FwSnTraceVo / FwRecallVo / FwTestSummaryVo / FwFirmwareVo
  mapper/      4 个 MyBatis-Plus Mapper
  service/     IFwFirmwareService / IFwFlashRecordService / IFwTestItemService
               IFwTestRecordService / IFwReportService
  service/impl/ 对应实现
```

关键实现点（含修正）：

1. **固件审批回调**：`FwFirmwareServiceImpl extends ServiceImpl<FwFirmwareMapper, FwFirmware> implements IFwFirmwareService, WfCallBackServiceI`，`afterFlowHandle(WfMyBusiness)` 按 `ActStatus`（中文常量）驱动状态机；`getBusinessDataById/flowValuesOfTask/flowCandidateUsernamesOfTask` 依 `SopDocumentServiceImpl` 同构补齐；`submitApproval` 走 `processService.startProcessByDataId(firmwareId, selectFlowId, "fwFirmwareService", vars)`，异常降级 IN_REVIEW。
2. **上报判定服务**：`FwReportService.report` 单事务：判定 → 必测项齐全校验 → 幂等落库（duplicate `test_no` 返回已存结果）→ 返回；唯一索引兜底。
3. **门禁钩子**：见 §4.4（插桩位置 + FORCE/WARN 开关）。
4. **工序扩展**：`ProWorkOrderServiceImpl.generateStepsFromRouting` 按关键字打标 + 自动带出默认 firmware_id；`ProWorkOrderStep` 实体/Bo/Vo 加 `stepType/firmwareId`；工单发布/工序编辑校验 FLASH 必绑 RELEASED。
5. **自动编码**：`AutoCodeUtil.genSerialCode("MES_FW_CODE"|"MES_FLASH_NO"|"MES_TEST_NO", null)`（同 `MES_SOP_CODE` 用法；`AutoCodeUtil` 已有 ruoyi-mes 依赖先例）。
6. **固件上传**：`/mes/fw/firmware/upload` 复用 OSS（`SysOssController` 同套 OSS 组件），服务端 `DigestUtils.md5DigestAsHex` 算 MD5 + 大小回填，返回 `ossId/md5/size/url`；前端 `file-upload` 组件换专用上传地址。
7. **配置**：`nexus.mes.fw.flash-keywords` / `test-keywords`（spring 绑定，默认 `[FLASH,BURN]` / `[FCT,TEST,FUNC]`）。

### 5.2 审批流配置（一次性初始化，M1 内完成）

1. BPMN 设计器（`/workflow/model`）绘制「固件发布审批」（发起 → 研发/测试负责人审批 → 生产与质量负责人会签 → 结束；驳回回发起人）并部署；
2. `wf_custom_form` 注册一行：`business_service=fwFirmwareService`、`flow_name=固件发布审批`、`deploy_id=部署ID`、`route_name=mes/fw/firmware/index`；
3. 菜单授权：审批人走现有「我的待办/已办」；固件详情页复用流程查看组件。

### 5.3 前端（`plus-ui`）

| 页面 | 路径 | 要点 |
|------|------|------|
| 固件档案 | `src/views/mes/fw/firmware/index.vue` | `useCrud` 列表 + 表单（`previewCode('MES_FW_CODE')` 自动编码开关 + `file-upload` 上传 + MD5 展示）+ 提交审批（`startByDataId`）+ 强制 approve/reject/归档 + 版本历史 |
| 刷写作业 | `src/views/mes/fw/flash/index.vue` | 扫码输入 → 展示工单绑定固件与 MD5 → PASS/FAIL 确认 → 记录列表 |
| 测试作业 | `src/views/mes/fw/test/index.vue` | 扫码 → 测试项清单 → 人工录入兜底（即时判定）；AUTO 由上报后刷新 |
| 测试记录 | `src/views/mes/fw/testRecord/index.vue` | 分页查询/导出（按 SN/工单/固件/结果/时间） |
| 测试项标准 | `src/views/mes/fw/testItem/index.vue` | `useCrud` CRUD（NUMERIC/TEXT/ENUM 条件表单） |
| 工单工序 | 现有工单详情扩展 | 工序行加 step_type 标签 + 固件绑定下拉（仅 RELEASED）+ 改标操作 |
| SN 追溯 | 现有 `pro/sn/trace.vue` 扩展 | 新增「刷写记录」「测试记录」Tab；固件召回页 |

**样式规范（修正）**：标准 CRUD 列表页用 `@/composables/useCrud`（ERP 现行标准）；审批/上传页仿 `sop/document/index.vue`（`proxy.useDict` + `$modal` + `file-upload` + `previewCode`）。仓库**不存在** `AutoGenCodeField` 组件，勿引用。API 层新增 `src/api/mes/fw/{firmware,flash-record,test-item,test-record}.ts`。

### 5.4 与现有模块集成点

| 现有能力 | 复用方式 |
|---------|---------|
| Flowable 审批（`WfCallBackServiceI` + `wf_my_business`，范本 SOP） | 固件发布审批完整复用，业务表仅存 `select_flow_id` |
| OSS（`sys_oss` / MinIO） | `/mes/fw/firmware/upload` 复用 OSS 存储，服务端算 MD5 |
| 自动编码（`AutoCodeUtil` / `sys_auto_code_*`） | `MES_FW_CODE`/`MES_FLASH_NO`/`MES_TEST_NO` 规则，ruoyi-mes 已有依赖先例 |
| SN 体系（`pro_sn` + `lt_sn_binding` + `ProTraceability`） | SN 定位/状态/父子追溯不动，新表挂 `sn_id` 衔接 |
| 工序/流转卡 | 刷写/测试作为工序类型嵌入（`step_type`），门禁钩子扩展 `completeStep`/`completeDispatch` |
| 设备（`eq_equipment`） | 刷写/测试设备选填登记（code/name 快照） |
| NCR/返工 | 多次 FAIL 转 NCR 复用现有创建逻辑 |
| 字典/菜单 | 新字典 + MES 下「固件管理」菜单/按钮权限 |
| **不新增** | 测试数据**不走** `dc_data_record`（无 SN 列 + 5 分钟归档不满足即时判定）；**不新增** IoT 链路（本期人工扫码 + 测试软件 API） |

---

## 六、开发计划（最终版）

> 估算 **18 人天**（v1.0 的 16d + 工序打标/上传 MD5/权限菜单等修正项 2d），按里程碑分阶段交付、逐段验收。

| 阶段 | 内容 | 估算 | 里程碑 |
|------|------|------|--------|
| **M1 数据模型 + 固件档案 + 审批发布** | ① 增量 SQL：4 表（tenant_id=varchar 修正版）+ `pro_work_order_step` 扩展 + 字典 + **菜单/按钮权限** + **编码规则 3 条**（`script/sql/update/2026-08-13-mes-firmware.sql`）；② `fw` 包骨架（4 实体/Mapper）；③ 固件 CRUD + `/upload`（MD5/大小）+ `WfCallBackServiceI` 回调 + `submitApproval`/`approve`/`reject`/`archive`/`flows`；④ `wf_custom_form` 注册 + BPMN 部署；⑤ 固件档案前端页（useCrud + upload + 审批按钮组） | 5d | ✅ 固件可创建/上传/发布/驳回/归档，仅 RELEASED 可选 |
| **M2 刷写流程** | ① `ProWorkOrderStep` 实体/Bo/Vo + DDL 同步；② `generateStepsFromRouting` 关键字打标 + 默认固件自动带出 + 工单工序页改标/绑定 UI（available 下拉）；③ `flash-record/confirm` + 校验规则 + 刷写记录查询；④ 刷写作业前端页（扫码 → 确认）；⑤ `completeStep`/`completeDispatch` 门禁钩子（FLASH，FORCE/WARN 开关） | 4.5d | ✅ 扫码确认刷写、三层防错拦截、完工门禁 |
| **M3 测试流程** | ① `fw_test_item` CRUD + 测试项标准页；② 判定引擎 + `report` 自动上报（幂等/必测项齐全/≤100 项）+ `manual` 手动录入；③ 测试作业页；④ 测试记录查询/导出；⑤ 门禁钩子（TEST） | 5.5d | ✅ 测试软件 API 上报全链路 + 全检门禁 |
| **M4 追溯/召回 + 前端完善** | ① SN 追溯页（刷写/测试 Tab）；② 召回页（sn_status 映射在库/已发运）；③ 测试软件 mock 脚本 + E2E 场景脚本；④ 全量验收（对照第七章） | 3d | ✅ 全场景 E2E 通过 |

> 里程碑间依赖：M2/M3 门禁依赖 M1 的 `fw` 实体与工序扩展；M4 依赖 M2/M3 记录数据。每阶段结束即走内部 demo 验收，M3 的测试软件对接协议文档在 M1 交付时同步输出（附 mock 服务）。

### M1 交付记录（2026-08-16 ✅ 已交付）

- 后端：`com.nexus.mes.fw` 包（4 实体/4 Mapper/BO/4 VO）+ `FwFirmwareServiceImpl`（CRUD、`/upload` MD5/大小回填、`WfCallBackServiceI` 回调、`submitForApproval`/`approve`/`reject`/`archive`/`setDefault`/`flows`、自动编码 `MES_FW_CODE`、`is_default` 物料内唯一、归档双守卫）+ `FwFirmwareController`（权限 `mes:fw:firmware:*`）。
- SQL：`script/sql/update/2026-08-13-mes-firmware.sql`（4 表 + `pro_work_order_step` 扩展 + 字典 6009xx + 菜单 4280-4295 + 编码规则 289-291 + `mes.fw.gate.mode` 配置 + `wf_custom_form` 注册）。✅ `mvn compile` 通过（0 error）。
- 前端：`plus-ui/src/api/mes/fw/firmware.ts` + `src/views/mes/fw/firmware/index.vue`（列表/上传/审批按钮组/MaterialSelectDialog/提交弹窗选流程）。
- ⚠️ 与方案差异：⑤ 前端未用 useCrud（仓库无该封装，SOP 即范本，按 SOP 风格手写组合式 API）；BPMN 流程本体按 SOP/工单同款走「流程设计」UI 创建发布，M1 仅完成 `wf_custom_form` 注册；前端 ⑤ 未含导出（验收未要求）。
- 🔧 顺带修复既存编译坏点（阻断 mvn compile）：`BaseMessageConsumer.log` private→protected（4 个 MQ 消费者引用）、`plus-ui` 前端未动。
- 待办（M2 前置）：BPMN 审批流在「流程设计」创建并发布后，前端提交弹窗即可选流程走 Flowable 回调（未发布时走管理员兜底）。

---

## 七、验收标准

| # | 场景 | 验证点 |
|---|------|--------|
| 1 | 固件发布审批 | 创建固件（上传 MinIO，MD5/大小正确回填）→ 提交审批 → 各节点通过 → RELEASED（记录发布人/时间）；驳回 → DRAFT；审批前不可被工单绑定 |
| 2 | 固件版本管理 | 同物料多版本并存；设默认版本后工单自动带出；归档后不再可选；`is_default` 物料内唯一 |
| 3 | 多固件工单 | 一工单两个 FLASH 工序各绑一个固件，各自扫码确认互不干扰 |
| 4 | 刷写防错 | 固件与工单工序绑定不一致 → 拦截；SN 当前工序非 FLASH → 拦截；非 RELEASED → 拦截；fail_reason 必填 |
| 5 | 刷写记录 | PASS/FAIL 均落库（版本/MD5/设备/操作人）；FAIL 可重刷（retry_count 递增） |
| 6 | 工序打标 | 下发生成 FLASH/TEST 工序自动打标；计划员可改标；旧工单/路由无感（默认 GENERAL） |
| 7 | 测试标准 | 按物料配置 NUMERIC/TEXT/ENUM；固件特定项覆盖通用项；快照字段落库 |
| 8 | 测试自动上报 | `report` 逐项判定正确、必测项缺失判 FAIL、幂等（重复 test_no 不重复落库）、>100 项拒绝 |
| 9 | 测试门禁 | 任一 FAIL 或未测 SN：FORCE 拦截 / WARN 告警放行（sys_config 切换）；全 PASS 放行 |
| 10 | 人工兜底 | MANUAL 逐项即时判定，与 AUTO 同表可查 |
| 11 | 追溯与召回 | SN 详情含刷写/测试记录；按固件反查 SN 清单（在库=COMPLETED/在制=IN_PRODUCTION/已发运=SHIPPED/报废=SCRAPPED） |

---

## 八、风险评估与应对策略

| 风险 | 影响 | 应对 |
|------|------|------|
| 现有报工为数量级，SN 级门禁存在口径磨合 | 完工被误拦 | FORCE/WARN 开关（`mes.fw.gate.mode`）车间可降级；拦截信息明确缺失 SN 数量；无 SN 工单放行 |
| 测试软件对接协议需现场联调 | 上线延迟 | M1 即输出上报协议文档 + mock 服务；E2E 用脚本模拟测试软件全链路 |
| 全检数据量大（每 SN × 每测试项一行） | 查询性能 | `idx_sn/idx_wo/idx_firmware` 索引 + 列表强制过滤条件；量大后按月归档（沿用 `IotDataCleanupTask` 模式） |
| `process_code` 关键字打标误判（新工艺未覆盖） | 工序类型错误 | 打标可配置（`nexus.mes.fw`）+ 计划员改标兜底；未命中默认 GENERAL 不阻塞 |
| 固件版本与物料/工艺映射未来引入 ECN | 绑定规则扩展 | `pro_work_order_step` 扩展点已预留；后续可加生效时间/ECN 版本控制 |
| 测试软件上报账号权限过大 | 数据可信度 | 上报接口限 SN 在制；判定服务端权威；后续可加 IP 白名单/独立上报令牌（附录 B） |
| 历史 tenant_id bigint 残留（个别模块已 vchar 化，MES 全 vchar） | 跨表 JOIN 失败 | 新表统一 vchar(20) '000000'；不引入新不一致 |

---

## 附录 A 字典 / 菜单 / 编码规则 / 配置清单

### A.1 字典（`sys_dict_type`/`sys_dict_data`，ry-vue 库）

| 类型编码 | 字典项 |
|---------|--------|
| `mes_step_type` | GENERAL 普通 / FLASH 固件刷写 / TEST 整机测试 |
| `mes_fw_status` | DRAFT 草稿 / IN_REVIEW 审批中 / RELEASED 已发布 / ARCHIVED 已归档 |
| `mes_fw_mode` | AUTO 自动 / MANUAL 人工 |
| `mes_fw_result` | PASS 合格 / FAIL 不合格 |
| `mes_test_data_type` | NUMERIC 数值 / TEXT 文本 / ENUM 枚举 |
| `mes_sys_yes_no` | 复用现有（`is_default`/`is_required`） |

### A.2 菜单与权限（`sys_menu`，MES 目录下「固件管理」）

| 菜单 | 组件路径（路由） | 按钮权限 |
|------|-----------------|---------|
| 固件档案 | `mes/fw/firmware/index` | `mes:fw:firmware:list/query/add/edit/remove/submit/approve/reject/archive/upload` |
| 刷写作业 | `mes/fw/flash/index` | `mes:fw:flash:list/confirm` |
| 测试作业 | `mes/fw/test/index` | `mes:fw:test:list/manual` |
| 测试记录 | `mes/fw/testRecord/index` | `mes:fw:testRecord:list/export` |
| 测试项标准 | `mes/fw/testItem/index` | `mes:fw:testItem:list/query/add/edit/remove` |
| 固件召回 | 挂在固件档案下（召回页） | `mes:fw:firmware:recall` |

### A.3 编码规则（`sys_auto_code_rule` + `sys_auto_code_part`）

| rule_code | 说明 | 建议格式 |
|-----------|------|---------|
| `MES_FW_CODE` | 固件编码 | `FW` + yyyyMMdd + 3 位流水（仿 `MES_SOP_CODE`） |
| `MES_FLASH_NO` | 刷写记录号 | `FL` + yyyyMMdd + 4 位流水 |
| `MES_TEST_NO` | 测试单号 | `FT` + yyyyMMdd + 4 位流水 |

### A.4 配置（ry-vue 库 `sys_config` / MES `application.yml`）

| 键 | 位置 | 默认值 |
|----|------|--------|
| `mes.fw.gate.mode` | `sys_config` | `FORCE`（`WARN` 降级告警） |
| `nexus.mes.fw.flash-keywords` | application.yml | `FLASH,BURN` |
| `nexus.mes.fw.test-keywords` | application.yml | `FCT,TEST,FUNC` |

### A.5 增量 SQL 文件

- `script/sql/update/2026-08-13-mes-firmware.sql`：4 表建表（varchar(20) tenant_id）+ `pro_work_order_step` ALTER + 字典 + 菜单/按钮权限 + 编码规则 + `sys_config` 种子。

---

## 附录 B 测试软件对接注意事项

1. **鉴权**：请求头 `Authorization`（Bearer Token）；建议为测试工位创建专用受限账号（仅授予 `mes:fw:testRecord:report` 等上报权限）；
2. **时间**：`testTime` 不传由服务端落库；测试软件本地时钟不参与判定；
3. **幂等**：重试复用同一 `testNo`，服务端返回首次结果；
4. **失败处理**：单次上报 4xx（SN 不在制/工序不匹配/超 100 项）时暂停并提示人工介入，不要自动重试风暴；
5. **接口地址**：内网直连 MES 服务（不走公网网关），Base URL 由测试软件配置文件维护；M1 交付协议文档 + mock 服务供联调。