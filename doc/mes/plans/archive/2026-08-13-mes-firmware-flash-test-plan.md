# NexusERP MES 智能硬件固件刷写与测试管理方案 v1.0

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **文档版本**: v1.0
> **编制日期**: 2026-08-13
> **编制依据**: [NexusERP_MES文档.md](../mes/NexusERP_MES文档.md) + [P0 开发计划书](./2026-07-24-mes-p0-development-plan-v1.0.md)（已落地的 pro/qc/eq/dc/lt 骨架）+ Flowable 业务审批模式（`WfCallBackServiceI`，范本 `SopDocumentServiceImpl`）+ OSS 文件管理（`ruoyi-common-oss` / `sys_oss`）
> **状态**: 待评审 → 待用户批准 → 进入执行

---

## 目录

- [一、项目概述](#一项目概述)
- [二、业务需求与流程设计](#二业务需求与流程设计)
- [三、数据模型设计](#三数据模型设计)
- [四、接口设计](#四接口设计)
- [五、技术实现方案](#五技术实现方案)
- [六、开发计划](#六开发计划)
- [七、验收标准](#七验收标准)
- [八、风险评估与应对策略](#八风险评估与应对策略)
- [附录 A 字典清单](#附录-a-字典清单)
- [附录 B 测试软件对接注意事项](#附录-b-测试软件对接注意事项)

---

## 一、项目概述

### 1.1 项目背景

NexusERP 面向制造业，其中 **MES 模块**已完成工单（`ProWorkOrder`）、流转卡（`ProFlowCard`）、派工（`ProDispatch`）、SN 管理（`ProSn`）、追溯（`ProTraceability` + `lt_sn_binding`）、三检（`qc_*`）、设备（`eq_equipment`）、数据采集（`dc_*`）等基础能力。

当生产对象为**智能硬件**（带软件固件的产品，如 IoT 网关、智能终端、采集器）时，生产链路存在三类行业特有需求，当前系统**完全空白**：

1. **固件发布管理**：固件版本需经审批后才能上线使用，发布后与生产订单绑定；
2. **固件刷写**：产线上给每台设备烧录指定版本固件，需防错（版本不匹配拦截）并留痕；
3. **整机测试（全检）**：每台设备逐台执行功能/射频/电压等测试项，实测值与判定按 SN 绑定，测试不合格不允许过站。

### 1.2 项目目标

| 维度 | 当前 | 目标 |
|------|------|------|
| 固件版本管理 | 无 | 固件档案 + **审批发布**（Flowable）闭环，仅 RELEASED 可上产线 |
| 固件-订单绑定 | 无 | 工单刷写工序绑定固件版本，自动带出物料默认版本，可多固件 |
| 刷写留痕 | 无 | SN 级刷写记录（版本/MD5/设备/结果），防错校验 |
| 测试全检 | 无 | 测试项标准主数据 + **SN 级测试记录** + 自动判定 + 过站门禁 |
| 测试数据来源 | 无 | **测试软件 API 自动上报**为主，人工录入兜底 |
| 追溯与召回 | SN 追溯已有 | 固件反查 SN 清单（召回），SN 详情含刷写/测试记录 |

### 1.3 需求决策确认表

| # | 决策点 | 确认结论 |
|---|--------|---------|
| 1 | 测试方式 | **逐台 SN 全检**（非抽检；抽检类项后续可走现有 `QcIpqc` 复用） |
| 2 | 测试数据上报 | **测试软件调用 MES API 自动上报**，人工录入兜底 |
| 3 | 刷写/测试设备 | **人工扫码确认即可**，本期不接 IoT 自动采集（`dc` 的 5 分钟归档链路也不参与测试判定） |
| 4 | 多固件 | **一个产品可能多个固件**（主控/通信模组分开刷），工单多个刷写工序各绑一个 |
| 5 | 固件文件 | **存 MinIO**（复用 `ruoyi-common-oss` / `sys_oss`），登记 MD5 校验值 |

### 1.4 项目干系人

| 角色 | 职责 |
|------|------|
| 产品负责人（PO） | 需求评审、阶段性验收 |
| 项目经理（PM） | 进度跟踪、风险管理 |
| 后端开发（BE） | 数据模型、Service/Controller、门禁、审批回调、上报 API |
| 前端开发（FE） | 固件管理/刷写作业/测试作业/追溯页面 |
| 测试工程师（QA） | E2E 场景验证（含测试软件上报协议联调） |
| AI Agent | 主力编码、文档生成、单元测试生成 |

---

## 二、业务需求与流程设计

### 2.1 总体业务场景

```
研发发布固件版本
    │ (审批流: DRAFT → IN_REVIEW → RELEASED)
    ▼
固件档案 (fw_firmware, 存 MinIO + MD5, 按物料可多版本)
    │
    ▼
ERP 工单下发 ──MQ──► MES 工单 (pro_work_order)
    │                │ 生成工序 (pro_work_order_step)
    │                │   └─ 刷写工序: step_type=FLASH + firmware_id (自动带出物料默认RELEASED版本, 可改绑)
    │                │   └─ 测试工序: step_type=TEST
    ▼                ▼
刷写作业: 扫码SN → 校验固件版本匹配 → 人工确认 PASS/FAIL → fw_flash_record
    │                FAIL → 重刷/转返修(NCR)
    ▼
测试作业: 扫码SN → 自动带出测试项标准 → 测试软件API上报 或 人工录入
    │       → 逐项自动判定 → 全部 PASS 才可过站 → fw_test_record
    ▼
完工报工门禁: 刷写/测试工序 SN 未全部 PASS → 拦截 completeStep/completeDispatch
    ▼
追溯: SN详情(固件+测试) / 固件反查SN清单(召回)
```

### 2.2 固件发布审批流程

完全复用现有 Flowable 业务审批模式（**不需要任何 Flowable 底层开发**，范本为 MES 的 `SopDocumentServiceImpl`，SOP 发布审批即为同构实现）：

```
1. 固件创建(草稿 DRAFT) → 上传固件文件(MinIO) → 保存 select_flow_id
2. 发起审批: POST /workflow/process/startByDataId/{firmwareId}/{selectFlowId}/fwFirmwareService
   └─ 引擎自动写 wf_my_business, 状态机回调: doing → IN_REVIEW
3. 审批节点(建议): 研发/测试负责人审批 → 生产与质量负责人会签
4. 审批结果回调 afterFlowHandle:
   ├─ pass     → RELEASED (记录发布人/时间; 若勾选默认版本则置为物料默认)
   ├─ reject/retrun/revoke/stop/recall → DRAFT
   └─ doing    → IN_REVIEW
5. 只有 RELEASED 固件可被工单绑定/刷写; 已发布固件可归档(ARCHIVED, 不再可新绑定)
```

**状态机**: `DRAFT → IN_REVIEW → RELEASED ⇄ ARCHIVED`，驳回任意环节回 `DRAFT`。

**管理兜底**: 提供管理员强制 `approve` / `refuse` 接口（绕过流程直接改状态，与 SOP 一致）。

### 2.3 固件绑定生产订单

绑定落点为 **MES 工单的刷写工序**（`pro_work_order_step`），向上经 `erpWoId` 可追溯 ERP 工单/计划/销售订单：

- 每个刷写工序绑定一个 `firmware_id` → **天然支持一工单多固件**（主控/通信模组各一个刷写工序，各绑一个固件）；
- 工单生成时刷写工序的 `firmware_id` **自动带出**：物料（工单 `materialId`）当前 `is_default=1` 且 `status=RELEASED` 的固件；计划员可在工单工序页改绑（下拉仅显示 RELEASED 固件）；
- 刷写执行时二次校验：**SN 物料 → 工单工序绑定固件 → 实际刷写固件** 三层一致，不一致直接拦截（防错刷）。

### 2.4 固件刷写流程

```
刷写工序(step_type=FLASH) → 扫码SN(pro_sn.current_step 定位刷写工序)
  → 前端展示: 工单绑定的固件版本 + 当前固件文件MD5
  → 人工确认 PASS / FAIL(填失败原因)
  → 写 fw_flash_record (固件版本+MD5+设备+操作人+时间+结果)
  → FAIL: 允许重刷(retry_count+1), 多次FAIL可转NCR返修
```

校验规则（服务端强制）：
1. SN 存在且在制（`sn_status = IN_PRODUCTION`）；
2. SN 当前工序（`current_step_seq`）为 FLASH 工序；
3. 固件 `status = RELEASED`，且 `firmware.materialId = sn.materialId`；
4. 若工单工序绑定了固件：固件必须与绑定一致；未绑定时以固件物料匹配为准并提示绑定缺失。

### 2.5 整机测试流程（全检）

**测试项标准主数据**：按物料 + 固件版本配置测试项清单（`fw_test_item`），含数据类型/单位/上下限/目标值/枚举合格值，服务端自动判定。

```
测试工序(step_type=TEST) → 扫码SN
  → 服务端按 SN 物料 + 测试时固件版本 返回测试项清单
  → 数据来源:
     ├─ AUTO(推荐): 测试软件批量上报 POST /mes/fw/test-record/report
     │    服务端逐项自动判定, 返回整体结果; 测试软件可展示
     └─ MANUAL(兜底): 测试员按项录入实测值, 服务端即时判定
  → 全部 PASS → 允许过站
  → 任一 FAIL → 锁 SN, 可重测(新 test_no 覆盖判定), 多次FAIL转NCR
```

**判定规则（服务端权威，测试软件不得覆盖）**：
- `NUMERIC`：`lower_limit ≤ 实测值 ≤ upper_limit`（限值可空则单边校验）；
- `ENUM`：实测文本 ∈ `pass_enum` 合格值列表；
- `TEXT`：由客户端传 `result`（目视/听音类人工判定项）。

**测试时固件版本** 取值优先级：上报显式传值 > SN 最近一条 PASS 刷写记录的版本 > 工单工序绑定版本。

### 2.6 追溯与召回

- **正向**：SN 详情页 → 刷写记录（版本/MD5/设备/操作人）+ 测试记录（逐项实测值/判定/标准快照）；
- **反向（召回）**：`fw_flash_record.firmware_id` 反查 → 所有刷过该版本的 SN 清单 → 联 `pro_sn.sn_status` 与出库状态定位**在库 / 已发运**，支撑固件缺陷召回；
- 测试记录对标准值做**快照冗余**（`upper_limit/lower_limit/pass_enum/item_name` 复制入库），标准后续修改不影响历史判定与追溯。

---

## 三、数据模型设计

> 全部落到 **ry-mes 库**，沿用 `ry-mes.sql` 既有表约定（`tenant_id`、`create_dept`、`create_by/create_time/update_by/update_time`、`del_flag` 逻辑删除、utf8mb4/utf8mb4_unicode_ci）。DDL 最终以 `script/sql/update/` 增量脚本落地。

### 3.1 `fw_firmware` — 固件档案

```sql
CREATE TABLE fw_firmware (
  firmware_id     BIGINT        NOT NULL COMMENT '固件ID',
  firmware_code   VARCHAR(64)   NOT NULL COMMENT '固件编码(唯一)',
  firmware_name   VARCHAR(128)  NOT NULL COMMENT '固件名称',
  material_id     BIGINT        NOT NULL COMMENT '适用物料ID(产品)',
  material_code   VARCHAR(64)   NOT NULL COMMENT '适用物料编码',
  material_name   VARCHAR(128)  NOT NULL COMMENT '适用物料名称',
  version         VARCHAR(32)   NOT NULL COMMENT '版本号(如 1.2.3)',
  build_no        VARCHAR(64)   NULL COMMENT '构建号',
  oss_id          BIGINT        NULL COMMENT '固件文件OSS文件ID(sys_oss)',
  file_md5        VARCHAR(64)   NULL COMMENT '固件文件MD5(刷写前校验)',
  file_size       BIGINT        NULL COMMENT '固件文件大小(字节)',
  release_note    TEXT          NULL COMMENT '发布说明(改动点/兼容性)',
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  is_default      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '是否物料默认版本(0否/1是)',
  select_flow_id  BIGINT        NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  published_by    BIGINT        NULL COMMENT '发布人ID',
  publish_time    DATETIME      NULL COMMENT '发布时间',
  tenant_id       BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID',
  create_dept     BIGINT        NULL COMMENT '创建部门',
  create_by       BIGINT        NULL COMMENT '创建人',
  create_time     DATETIME      NULL COMMENT '创建时间',
  update_by       BIGINT        NULL COMMENT '更新人',
  update_time     DATETIME      NULL COMMENT '更新时间',
  del_flag        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  remark          VARCHAR(500)  NULL COMMENT '备注',
  PRIMARY KEY (firmware_id),
  UNIQUE KEY uk_firmware_code (firmware_code),
  KEY idx_material (material_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='固件档案';
```

**说明**：
- 一个产品（物料）多行 = 多版本/多用途固件并存；`is_default` 按物料维度唯一（同一物料仅一行可为 1）；
- `oss_id` 关联 ry-vue 库 `sys_oss`（MinIO 文件记录），上传复用现有 OSS 上传接口；
- 多固件场景（主控+通信模组）：同一物料可建多行（如 `firmware_code` 区分 `FW-MAIN` / `FW-RF`），工单不同刷写工序各绑一行。

### 3.2 `pro_work_order_step` — 工单工序扩展

```sql
ALTER TABLE pro_work_order_step
  ADD COLUMN step_type   VARCHAR(16) NOT NULL DEFAULT 'GENERAL' COMMENT '工序类型: GENERAL/FLASH/TEST' AFTER sequence_num,
  ADD COLUMN firmware_id BIGINT      NULL COMMENT '固件ID(仅FLASH工序, 只可选RELEASED)' AFTER step_type;
```

**说明**：
- `step_type` 默认 GENERAL（普通工序），与现有工单/工艺路线完全兼容，旧数据无感；
- 刷写工序必须绑定 `firmware_id`（发布工单时校验），测试工序无需绑定（测试项按物料+固件匹配）；
- 执行层判断走 `pro_flow_card_step.wo_step_id → pro_work_order_step` 关联，**无需改流转卡表**。

### 3.3 `fw_flash_record` — 固件刷写记录（SN 级）

```sql
CREATE TABLE fw_flash_record (
  flash_record_id  BIGINT        NOT NULL COMMENT '刷写记录ID',
  flash_no         VARCHAR(64)   NOT NULL COMMENT '刷写记录号(自动编码)',
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
  result           VARCHAR(8)    NOT NULL COMMENT '结果: PASS/FAIL',
  fail_reason      VARCHAR(255)  NULL COMMENT '失败原因',
  retry_count      INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
  flash_mode       VARCHAR(8)    NOT NULL DEFAULT 'MANUAL' COMMENT '方式: AUTO/MANUAL',
  tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID',
  create_dept      BIGINT        NULL COMMENT '创建部门',
  create_by        BIGINT        NULL COMMENT '创建人',
  create_time      DATETIME      NULL COMMENT '创建时间',
  update_by        BIGINT        NULL COMMENT '更新人',
  update_time      DATETIME      NULL COMMENT '更新时间',
  del_flag         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  remark           VARCHAR(500)  NULL COMMENT '备注',
  PRIMARY KEY (flash_record_id),
  UNIQUE KEY uk_flash_no (flash_no),
  KEY idx_sn (sn_id),
  KEY idx_firmware (firmware_id),
  KEY idx_wo (mes_wo_id)
) ENGINE=InnoDB COMMENT='固件刷写记录(SN级)';
```

**说明**：允许同一 SN 多次记录（FAIL 重刷保留历史），判定取**最新一条**；`idx_firmware` 支撑召回反查。

### 3.4 `fw_test_item` — 测试项标准

```sql
CREATE TABLE fw_test_item (
  test_item_id  BIGINT         NOT NULL COMMENT '测试项ID',
  item_code     VARCHAR(64)    NOT NULL COMMENT '测试项编码(如 RF-TXPWR)',
  item_name     VARCHAR(128)   NOT NULL COMMENT '测试项名称(如 发射功率)',
  material_id   BIGINT         NOT NULL COMMENT '适用物料ID',
  firmware_id   BIGINT         NOT NULL DEFAULT 0 COMMENT '固件ID(0=适用该物料所有版本)',
  data_type     VARCHAR(8)     NOT NULL DEFAULT 'NUMERIC' COMMENT '类型: NUMERIC/TEXT/ENUM',
  unit          VARCHAR(16)    NULL COMMENT '单位',
  upper_limit   DECIMAL(18,4)  NULL COMMENT '上限(数值类)',
  lower_limit   DECIMAL(18,4)  NULL COMMENT '下限(数值类)',
  target_value  DECIMAL(18,4)  NULL COMMENT '目标值(数值类)',
  pass_enum     VARCHAR(500)   NULL COMMENT 'ENUM类合格值(JSON数组)',
  is_required   CHAR(1)        NOT NULL DEFAULT '1' COMMENT '是否必测(0否/1是)',
  sort_order    INT            NOT NULL DEFAULT 0 COMMENT '排序号',
  remark        VARCHAR(500)   NULL COMMENT '备注',
  tenant_id     BIGINT         NOT NULL DEFAULT 0 COMMENT '租户ID',
  create_dept   BIGINT         NULL COMMENT '创建部门',
  create_by     BIGINT         NULL COMMENT '创建人',
  create_time   DATETIME       NULL COMMENT '创建时间',
  update_by     BIGINT         NULL COMMENT '更新人',
  update_time   DATETIME       NULL COMMENT '更新时间',
  del_flag      CHAR(1)        NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  PRIMARY KEY (test_item_id),
  UNIQUE KEY uk_material_item (material_id, firmware_id, item_code),
  KEY idx_material (material_id)
) ENGINE=InnoDB COMMENT='测试项标准主数据';
```

**说明**：`firmware_id = 0` 表示"该物料所有固件版本通用"；特定版本项用真实固件 ID 覆盖（同名 `item_code` 按固件精确匹配，否则回退通用项）。

### 3.5 `fw_test_record` — 测试记录（SN 级全检）

```sql
CREATE TABLE fw_test_record (
  test_record_id   BIGINT         NOT NULL COMMENT '测试记录ID',
  test_no          VARCHAR(64)    NOT NULL COMMENT '测试单号(一次SN测试一条单号, 共享多行)',
  sn_id            BIGINT         NOT NULL COMMENT 'SN ID',
  sn_code          VARCHAR(64)    NOT NULL COMMENT 'SN编码',
  mes_wo_id        BIGINT         NOT NULL COMMENT 'MES工单ID',
  wo_no            VARCHAR(64)    NULL COMMENT '工单号',
  card_id          BIGINT         NULL COMMENT '流转卡ID',
  step_record_id   BIGINT         NULL COMMENT '流转卡工序记录ID(测试工序)',
  process_id       BIGINT         NULL COMMENT '工序ID',
  process_code     VARCHAR(64)    NULL COMMENT '工序编码',
  process_name     VARCHAR(128)   NULL COMMENT '工序名称',
  firmware_id      BIGINT         NULL COMMENT '测试时固件ID',
  firmware_version VARCHAR(32)    NULL COMMENT '测试时固件版本',
  test_item_id     BIGINT         NOT NULL COMMENT '测试项ID',
  item_code        VARCHAR(64)    NOT NULL COMMENT '测试项编码(快照)',
  item_name        VARCHAR(128)   NOT NULL COMMENT '测试项名称(快照)',
  data_type        VARCHAR(8)     NOT NULL COMMENT '类型(快照): NUMERIC/TEXT/ENUM',
  unit             VARCHAR(16)    NULL COMMENT '单位(快照)',
  numeric_value    DECIMAL(18,4)  NULL COMMENT '实测数值',
  text_value       VARCHAR(500)   NULL COMMENT '实测文本',
  upper_limit      DECIMAL(18,4)  NULL COMMENT '上限(快照)',
  lower_limit      DECIMAL(18,4)  NULL COMMENT '下限(快照)',
  target_value     DECIMAL(18,4)  NULL COMMENT '目标值(快照)',
  pass_enum        VARCHAR(500)   NULL COMMENT 'ENUM合格值(快照)',
  result           VARCHAR(8)     NOT NULL COMMENT '判定: PASS/FAIL',
  tester_id        BIGINT         NULL COMMENT '测试人ID',
  tester_name      VARCHAR(64)    NULL COMMENT '测试人姓名',
  equipment_id     BIGINT         NULL COMMENT '测试设备ID',
  equipment_code   VARCHAR(64)    NULL COMMENT '测试设备编码',
  test_time        DATETIME       NULL COMMENT '测试时间',
  test_mode        VARCHAR(8)     NOT NULL DEFAULT 'MANUAL' COMMENT '方式: AUTO/MANUAL',
  remark           VARCHAR(500)   NULL COMMENT '备注',
  tenant_id        BIGINT         NOT NULL DEFAULT 0 COMMENT '租户ID',
  create_dept      BIGINT         NULL COMMENT '创建部门',
  create_by        BIGINT         NULL COMMENT '创建人',
  create_time      DATETIME       NULL COMMENT '创建时间',
  update_by        BIGINT         NULL COMMENT '更新人',
  update_time      DATETIME       NULL COMMENT '更新时间',
  del_flag         CHAR(1)        NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  PRIMARY KEY (test_record_id),
  UNIQUE KEY uk_test_no_item (test_no, test_item_id),
  KEY idx_sn (sn_id),
  KEY idx_firmware (firmware_id),
  KEY idx_wo (mes_wo_id)
) ENGINE=InnoDB COMMENT='测试记录(SN级全检)';
```

**说明**：
- 一次测试（同一 `test_no`）多行：该 SN 在该测试工序的每个测试项一行；
- `uk_test_no_item` 保证同单号同项不重复（**上报幂等**：同一 `test_no` 重复上报不产生重复行）；
- 标准值全部快照冗余，历史判定与追溯不受标准修改影响；
- 判定口径：**同一 SN 同一测试工序，最新 `test_no` 全部 PASS 才算通过**。

### 3.6 字典与菜单

- 字典（`sys_dict_type` / `sys_dict_data`，ry-vue 库）：见 [附录 A](#附录-a-字典清单)；
- 菜单（`sys_menu`，MES 目录下新增「固件管理」子目录）：
  - 固件档案 `mes/fw/firmware/index`
  - 刷写作业 `mes/fw/flash/index`
  - 测试作业 `mes/fw/test/index`
  - 测试记录 `mes/fw/testRecord/index`
  - 工单详情工序页（现有页面扩展 step_type/firmware 列）
  - SN 追溯详情页（现有页面扩展刷写/测试记录 Tab）

---

## 四、接口设计

### 4.1 后端接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mes/fw/firmware/list` | 固件分页（状态/物料/关键字过滤） |
| GET | `/mes/fw/firmware/{id}` | 固件详情 |
| POST | `/mes/fw/firmware` | 新增固件（自动编码；仅 DRAFT 可编辑） |
| PUT | `/mes/fw/firmware` | 修改（仅 DRAFT） |
| DELETE | `/mes/fw/firmware/{ids}` | 删除（仅 DRAFT） |
| GET | `/mes/fw/firmware/available?materialId=` | 可选固件下拉（**仅 RELEASED**，工单绑定用） |
| POST | `/mes/fw/firmware/archive/{id}` | 固件归档（RELEASED → ARCHIVED） |
| POST | `/mes/fw/firmware/approve/{id}` | 管理员强制审批通过（兜底） |
| POST | `/mes/fw/firmware/refuse/{id}` | 管理员强制驳回（兜底） |
| GET | `/mes/fw/firmware/flows` | 该业务可选审批流（`getProcesss` 包装） |
| — | 审批发起/待办/已办 | 复用 `/workflow/process/startByDataId` 与现有 `/workflow/*` 页面 |
| GET | `/mes/fw/flash-record/list` | 刷写记录分页（SN/工单/固件/结果过滤） |
| POST | `/mes/fw/flash-record/confirm` | **人工扫码确认刷写**（PASS/FAIL，见 §4.3） |
| GET | `/mes/fw/test-item/list` | 测试项标准分页（物料/固件过滤） |
| POST/PUT/DELETE | `/mes/fw/test-item` | 测试项标准 CRUD |
| POST | `/mes/fw/test-record/report` | **测试软件自动上报**（AUTO，见 §4.2） |
| POST | `/mes/fw/test-record/manual` | 人工逐项录入（MANUAL，服务端即时判定） |
| GET | `/mes/fw/test-record/list` | 测试记录分页（SN/工单/固件/结果/时间过滤） |
| GET | `/mes/fw/test-record/summary?snCode=` | 某 SN 各测试工序最新判定汇总（过站门禁判定用） |
| GET | `/mes/fw/sn-trace/{snCode}` | SN 追溯：刷写记录 + 测试记录 |
| GET | `/mes/fw/recall/{firmwareId}` | **固件召回**：刷过该版本的全部 SN 清单（含在库/出库状态） |

### 4.2 测试软件上报协议（AUTO）

`POST /mes/fw/test-record/report`，`Content-Type: application/json`，请求头带现有 `Authorization`（测试电脑走系统账号，或后续加白名单 IP 限制）。

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

响应（服务端权威判定）：

```json
{
  "code": 200,
  "data": {
    "testNo": "FT2026081300001",
    "overallResult": "FAIL",
    "items": [
      { "itemCode": "RF-TXPWR", "result": "PASS" },
      { "itemCode": "VOLT-3V3", "result": "FAIL", "reason": "实测 3.29 超出范围 [3.30, 3.40]" },
      { "itemCode": "VISION",   "result": "PASS" }
    ]
  }
}
```

规则：
1. 服务端按 `snCode` 定位 SN（校验在制状态与当前工序），自动关联工单/流转卡/测试工序上下文；
2. 测试项清单 = 物料 + 固件版本匹配的 `fw_test_item`（特定版本优先，回退通用项）；上报中**缺失的必测项判定 FAIL**（不齐全即不合格，防测试软件漏测）；
3. `result` 服务端计算（NUMERIC 比较上下限 / ENUM 匹配合格值），客户端传的 `result` 仅 TEXT 类人工项有效；
4. 幂等：`test_no` 重复上报 → 返回已存在的结果，不重复落库；客户端未传 `test_no` 时服务端自动生成；
5. 单次上报最多 100 项，超限分批。

### 4.3 刷写确认校验规则（MANUAL）

`POST /mes/fw/flash-record/confirm`

```json
{
  "snCode": "SN202608130001",
  "firmwareId": 123456,
  "equipmentCode": "BURN-01",
  "result": "PASS",
  "failReason": ""
}
```

服务端强制校验（任一不满足返回 400 拦截）：
1. SN 存在、未报废、在制（`sn_status = IN_PRODUCTION`）；
2. SN 当前工序为 FLASH 工序（`current_step_seq` 对应 `step_type = FLASH`）；
3. 固件 `status = RELEASED` 且 `firmware.materialId = sn.materialId`；
4. 若工单工序已绑定固件：`firmwareId` 必须与绑定一致；
5. PASS 时若 SN 已有 FAIL 记录则正常追加（视为重刷成功，`retry_count` 继承）。

---

## 五、技术实现方案

### 5.1 后端（`ruoyi-mes` 新增 `fw` 子包）

```
ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/fw/
  controller/  FwFirmwareController / FwFlashRecordController
               FwTestItemController / FwTestRecordController / FwReportController
  domain/      FwFirmware / FwFlashRecord / FwTestItem / FwTestRecord
  domain/bo/   FwFlashConfirmBo / FwTestReportBo / FwTestReportItemBo / FwTestManualBo
  domain/vo/   FwSnTraceVo / FwRecallVo / FwTestSummaryVo
  mapper/      4 个 MyBatis-Plus Mapper
  service/     IFwFirmwareService / IFwFlashRecordService / IFwTestItemService
               IFwTestRecordService / IFwReportService
  service/impl/ 对应实现
```

关键实现点：

1. **固件审批回调**（复用 `WfCallBackServiceI`，范本 `SopDocumentServiceImpl`）：

```java
public class FwFirmwareServiceImpl extends ServiceImpl<FwFirmwareMapper, FwFirmware>
        implements IFwFirmwareService, WfCallBackServiceI {

    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        String actStatus = business.getActStatus();
        Long id = Long.valueOf(business.getDataId());
        if (ActStatus.pass.equals(actStatus)) {
            // → RELEASED: 置发布人/时间; is_default=1 的置为物料默认版本(唯一)
        } else if (ActStatus.doing.equals(actStatus)) {
            // → IN_REVIEW
        } else { // reject / retrun / revoke / stop / recall
            // → DRAFT
        }
    }
}
```

2. **上报判定服务**（`FwReportService.report`）：事务内完成 判定 → 校验必测项齐全 → 幂等落库 → 返回结果；`report` 幂等键 `test_no` 用 `InsertOrUpdate` + 唯一索引兜底；
3. **过站门禁钩子**：扩展 `ProFlowCardServiceImpl.completeStep` 与 `ProDispatchServiceImpl.completeDispatch`，当工序 `step_type = FLASH/TEST` 时校验：

```java
// FLASH 工序: 已生成 SN 中无 PASS 刷写记录的数量 > 0 → 拦截
// TEST  工序: 最新 test_no 判定存在 FAIL 或缺失的 SN 数量 > 0 → 拦截
// 提供配置开关(字典参数)可降级为警告, 默认强制
```

4. **工单工序扩展**：工单生成/工序维护逻辑中支持 `step_type`、`firmware_id`；发布校验 FLASH 工序必绑 RELEASED 固件；自动带出物料默认版本；
5. 自动编码（`flash_no`/`test_no`/`firmware_code`）接入现有编码生成规则服务；
6. 文件上传复用现有 OSS 接口（`sys_oss`），固件上传时计算 MD5 与大小回填。

### 5.2 审批流配置（一次性初始化）

1. BPMN 设计器（`/workflow/model`）绘制「固件发布审批」流程（建议：发起 → 研发/测试负责人审批 → 生产与质量负责人会签 → 结束；驳回回发起人）并部署；
2. `wf_custom_form`（ry-vue 库）注册一行：`business_service=fwFirmwareService`、`flow_name=固件发布审批`、`deploy_id=部署ID`、`route_name=mes/fw/firmware/index`；
3. 菜单授权：审批人可在现有「我的待办/已办」中处理，固件详情页复用流程查看组件。

### 5.3 前端（`plus-ui`）

| 页面 | 路径 | 要点 |
|------|------|------|
| 固件档案 | `src/views/mes/fw/firmware/index.vue` | 列表/表单/OSS 上传/MD5 展示/提交审批（`startByDataId`）/强制审批/归档/版本历史 |
| 刷写作业 | `src/views/mes/fw/flash/index.vue` | 扫码输入 → 展示工单绑定固件与 MD5 → PASS/FAIL 确认 → 记录列表 |
| 测试作业 | `src/views/mes/fw/test/index.vue` | 扫码 → 测试项清单展示 → 人工录入兜底（自动判定实时反馈）；AUTO 模式由测试软件上报后自动刷新 |
| 测试记录 | `src/views/mes/fw/testRecord/index.vue` | 分页查询/导出（按 SN/工单/固件/结果） |
| 工单工序 | 现有工单详情扩展 | 工序行加 step_type 标签与固件绑定下拉（仅 RELEASED） |
| SN 追溯 | 现有 SN 详情扩展 | 新增「刷写记录」「测试记录」Tab；召回页展示 SN 清单 |

样式规范：遵循 `useCrud` / `AutoGenCodeField` 既有标准模式；组件层面沿用现有 Element Plus + vxe-table 约定。

### 5.4 与现有模块集成点

| 现有能力 | 复用方式 |
|---------|---------|
| Flowable 审批（`WfCallBackServiceI` + `wf_my_business`） | 固件发布审批完整复用，业务表仅存 `select_flow_id` |
| OSS（`sys_oss` / MinIO） | 固件文件上传/下载/删除复用 |
| SN 体系（`pro_sn` + `lt_sn_binding` + `ProTraceability`） | SN 定位/状态/父子追溯不动，新表挂 `sn_id` 衔接 |
| 工序/流转卡（`pro_work_order_step` / `pro_flow_card_step`） | 刷写/测试作为工序类型嵌入，门禁钩子扩展 `completeStep` |
| 设备（`eq_equipment`） | 刷写/测试设备选填登记（code/name 快照） |
| NCR/返工 | 多次 FAIL 转 NCR 复用现有创建逻辑 |
| 字典/菜单/自动编码 | 新增字典、菜单、编码规则 |
| **不新增** | 测试数据**不走** `dc_data_record`（无 SN 列 + 5 分钟归档不满足即时判定）；**不新增** IoT 链路（本期人工扫码 + 测试软件 API） |

---

## 六、开发计划

> 预估 **16 人天**，可按里程碑分阶段交付、逐段验收。

| 阶段 | 内容 | 估算 | 里程碑 |
|------|------|------|--------|
| M1 数据模型 + 固件档案 + 审批发布 | 4 表 DDL 落 `script/sql/update/`；`fw` 包骨架；固件 CRUD + OSS 上传 + `WfCallBackServiceI` 回调 + `wf_custom_form` 注册 + BPMN 部署；固件档案前端页 | 4d | ✅ 固件可发布/驳回/归档，仅 RELEASED 可选 |
| M2 刷写流程 | `pro_work_order_step` 扩展 + 工单工序绑定 UI；`flash-record/confirm` + 校验规则；刷写作业页；`completeStep` 门禁钩子（FLASH） | 4d | ✅ 扫码确认刷写、防错拦截、完工门禁 |
| M3 测试流程 | `fw_test_item` CRUD + 测试项配置页；`report` 自动上报 + `manual` 手动录入 + 判定引擎 + 幂等；测试作业页；门禁钩子（TEST） | 5d | ✅ 测试软件 API 上报全链路 + 全检门禁 |
| M4 追溯/召回 + 前端完善 | SN 追溯页（刷写/测试 Tab）、召回页、测试记录查询/导出、E2E 测试脚本、验收 | 3d | ✅ 全场景 E2E 通过 |

---

## 七、验收标准

| # | 场景 | 验证点 |
|---|------|--------|
| 1 | 固件发布审批 | 创建固件（上传 MinIO）→ 提交审批 → 各节点通过 → RELEASED（记录发布人/时间）；驳回 → DRAFT；审批前不可被工单绑定 |
| 2 | 固件版本管理 | 同物料多版本并存；设默认版本后工单自动带出；归档后不再可选 |
| 3 | 多固件工单 | 一工单两个刷写工序各绑一个固件，各自扫码确认互不干扰 |
| 4 | 刷写防错 | 固件版本与工单工序绑定不一致 → 拦截；SN 当前工序非刷写工序 → 拦截；非 RELEASED 固件 → 拦截 |
| 5 | 刷写记录 | 扫码 PASS/FAIL 均落库（版本/MD5/设备/操作人）；FAIL 可重刷（retry_count 递增） |
| 6 | 测试标准 | 按物料配置数值/枚举/文本类测试项；固件特定项覆盖通用项 |
| 7 | 测试自动上报 | 测试软件调用 `report` → 逐项判定正确、必测项缺失判 FAIL、幂等（重复上报不重复落库）、超限报错 |
| 8 | 测试门禁 | 任一项 FAIL 或未测的 SN，`completeStep`/`completeDispatch` 拦截；全部 PASS 放行 |
| 9 | 人工兜底 | 手动录入模式逐项即时判定，与 AUTO 记录同表可查 |
| 10 | 追溯与召回 | SN 详情含刷写/测试记录；按固件反查 SN 清单（含在库/已发运状态） |

---

## 八、风险评估与应对策略

| 风险 | 影响 | 应对 |
|------|------|------|
| 现有报工为数量级，SN 级门禁存在口径磨合 | 完工被误拦 | 门禁提供配置开关（强制/警告），车间可按需降级；SN 缺失刷写记录时明确提示缺失数量 |
| 测试软件对接协议需现场联调 | 上线延迟 | M1 即输出上报协议文档与 mock 服务；E2E 用脚本模拟测试软件全链路验证 |
| 全检数据量大（每 SN × 每测试项一行） | 查询性能 | `idx_sn`/`idx_wo`/`idx_firmware` 索引 + 列表强制过滤条件；量大后可按月归档（沿用 `IotDataCleanupTask` 模式） |
| 固件版本与物料/工艺映射未来引入 ECN | 绑定规则扩展 | `pro_work_order_step` 扩展点已预留；后续可加生效时间/ECN 版本控制 |
| 测试软件上报账号权限过大 | 数据可信度 | 上报接口限 SN 在制状态；判定服务端权威；后续可加 IP 白名单/独立上报令牌 |

---

## 附录 A 字典清单

| 字典类型 | 字典项 | 说明 |
|---------|--------|------|
| `mes_step_type` | GENERAL 普通 / FLASH 固件刷写 / TEST 整机测试 | 工单工序类型（`pro_work_order_step.step_type`） |
| `mes_fw_status` | DRAFT 草稿 / IN_REVIEW 审批中 / RELEASED 已发布 / ARCHIVED 已归档 | 固件状态（`fw_firmware.status`） |
| `mes_fw_mode` | AUTO 自动 / MANUAL 人工 | 刷写与测试记录方式（`fw_flash_record.flash_mode` / `fw_test_record.test_mode` 共用） |
| `mes_fw_result` | PASS 合格 / FAIL 不合格 | 刷写/测试结果（`fw_flash_record.result` / `fw_test_record.result`） |
| `mes_test_data_type` | NUMERIC 数值 / TEXT 文本 / ENUM 枚举 | 测试项类型（`fw_test_item.data_type`） |
| `mes_sys_yes_no` | 复用现有 | `is_default` / `is_required` 布尔 |

## 附录 B 测试软件对接注意事项

1. **鉴权**：请求头携带 `Authorization`（Bearer Token）；建议为测试工位创建专用受限账号（仅授予测试上报相关权限）；
2. **时间**：`testTime` 不传由服务端落库；测试软件本地时钟不参与判定；
3. **幂等**：重试时复用同一 `testNo`，服务端返回首次结果；
4. **失败处理**：单次上报返回 4xx（如 SN 不在制/工序不匹配）时，测试软件应暂停并提示人工介入，不要自动重试风暴；
5. **接口地址**：内网直连 MES 服务（不走公网网关），Base URL 由测试软件配置文件维护。
