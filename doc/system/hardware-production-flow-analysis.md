# 硬件生产全流程 — Nexus-ERP 实现差异分析

> 本文档对比用户梳理的"硬件生产全流程"与 Nexus-ERP 的实际代码实现状态，标注**已实现**、**部分实现**、**未实现/待开发**，并补充关键设计细节。

---

## 一、PCBA 生产

### 方式一：外协加工

| 步骤 | 用户描述 | Nexus 实现状态 | 差异说明 |
|------|---------|---------------|---------|
| **a. 生成外协加工单** | MES 生成外协工单，提供 SN 列表、测试治具、烧录工具、SOP | ⚠️ **部分实现** | ERP 模块有 `PrdSubcontract`（委外订单）实体和完整 CRUD 服务，状态机 DRAFT→SUBMITTED→APPROVED→IN_PROGRESS→COMPLETED→CLOSED。但外协加工单仅停留在"订单"层面，**未实现**与 MES 工单的双向联动，**未实现**向外协厂推送 SN 列表、测试治具管理、烧录工具分发等功能。 |
| **a1. PCBA SN 列表提供** | 加工厂依据 SN 列表镭雕 | ❌ **未实现** | 外协场景下 MES 生成 SN 列表并下发加工厂的功能未开发。当前 `ProSn` 的 SN 生成仅支持内部工单流转卡场景。 |
| **a2. 测试治具提供** | 提供 FCT 治具给加工厂 | ❌ **未实现** | 系统无治具/夹具管理模块，`EqEquipment`（设备管理）仅涵盖设备档案，不包含治具生命周期管理。 |
| **a3. FCT 烧写与测试工具** | 加工厂使用分配账号登录工具，拉取固件→烧录→扫码→测试→回传 | ❌ **未实现** | 这是**上位机/产线工具**范畴，纯后端 MES 系统不提供桌面烧录工具。固件管理（`mes_firmware`）、刷写记录（`mes_flash_record`）、测试记录（`mes_test_record`）的数据模型已完整实现，但需要**独立的产线工具**（上位机）来对接这些 API 并执行实际烧录。外协专用版工具未开发。 |
| **a4. PCBA 生产 SOP** | 提供工艺流程、操作规范 | ❌ **未实现** | SOP 文档管理功能未实现。工艺路线（Routing）在 ERP `bd_routing` 中管理，可关联工序，但 SOP 文档附件上传、版本管理、外协厂可见性等功能欠缺。 |
| **b. 外协厂组织生产** | 镭雕 SN→SMT→DIP→烧录→FCT→回传 | ❌ **未实现** | 外协厂侧的生产执行完全在系统外，系统仅通过委外订单跟踪结果。 |
| **c. 收货与入库** | 扫描 PCBA SN，校验 FCT 记录，入半成品库 | ⚠️ **部分实现** | 委外入库有 MQ 消费者 `SubcontractStockConsumer`，但校验逻辑（扫描 SN 校验 FCT 回传记录）需依赖外协厂回传的测试数据，该数据链路尚未打通。半成品库的入库操作（`wm_stock`）框架已实现。 |
| **d. 外协加工单闭环** | MES 根据入库数量、良率、损耗自动关闭工单 | ⚠️ **部分实现** | 委外订单状态机已定义，但自动闭环逻辑（根据入库数量/良率/损耗自动计算并关闭）未实现，需人工操作。 |

**总结：外协加工目前仅完成了委外订单的 CRUD 基础框架（ERP 模块），与 MES 的深度集成（SN 下发、固件分发、测试数据回传、自动闭环）基本未开发，优先级较低，适合后期迭代。**

---

### 方式二：内部 SMT 自制

| 步骤 | 用户描述 | Nexus 实现状态 | 差异说明 |
|------|---------|---------------|---------|
| **a. 镭雕 SN** | 产线工具获取工单对应的 PCBA SN 列表，激光雕刻到 PCB | ⚠️ **部分实现** | **MES 已实现：** `ProSn` 实体 + `ProSnServiceImpl` 支持批量生成 SN（`source=BATCH`），SN 关联工单（`mes_wo_id`）、物料、流转卡。SN 编码通过 `AutoCodeUtil` 自动生成，格式如 `SN-FC-{工单号}-{流水号}`。<br><br>**未实现：** 镭雕需要**上位机**（产线工具）调用 MES API 获取 SN 列表并驱动激光雕刻机。MES 本身不提供设备控制能力。 |
| **b. SMT 贴片** | 锡膏印刷→贴片→回流焊→AOI 检测 | ⚠️ **部分实现** | **MES 已实现：** 工艺路线（`bd_routing`）可定义工序（如 SMT、DIP、AOI），工单工序步骤（`ProWorkOrderStep`）可编排各工序，流转卡（`ProFlowCard`）将工序下发给操作工，派工（`ProDispatch`）将具体操作指派到人，报工完成数量记录。<br><br>**未实现：** 与 SMT 产线设备（贴片机、回流焊炉、AOI 检测仪）的**实时数据采集**。虽然 IoT 模块有 `DcDataRecord` 采集框架和 `IotMqtt` 接入能力，但 SMT 设备协议适配未开发。 |
| **c. 烧录与 FCT 测试** | 拉取固件→烧录→扫码→FCT 测试→回传 | ⚠️ **部分实现（数据模型完整，产线工具缺失）** | **MES 已实现（数据层）：** <br>• `mes_firmware`（固件档案）：按物料多版本，状态 DRAFT→IN_REVIEW→RELEASED→ARCHIVED，文件存 MinIO（OSS）<br>• `mes_flash_type`（刷写类型）：firmware_file / config_file / mac / sn 等<br>• `mes_flash_record`（刷写记录）：SN 级，支持 PASS/FAIL，AUTO/MANUAL 模式，重刷保留历史<br>• `FlashTypeProvider` SPI 体系：`FirmwareFileFlashProvider` / `MacPoolFlashProvider` / `SnPoolFlashProvider` / `ConfigFileFlashProvider` / `ExternalRestFlashProvider`<br>• `mes_test_item`（测试项标准）：按物料配置，NUMERIC/TEXT/ENUM 数据类型，上下限/目标值/合格值<br>• `mes_test_record`（测试记录）：SN 级全检，自动判定 PASS/FAIL<br>• `TestRecordServiceImpl`：`beginTest` 开单 → `reportTestRows` 填报判定流程<br>• `FlashRecordServiceImpl`：`confirmFlash` 工位确认 / `reportFlashBatch` 自动上报<br><br>**未实现（产线工具层）：** <br>• 烧录和测试需要**产线上位机工具**（桌面应用或工控机程序）调用 MES API 执行实际烧录和测试，该工具未开发<br>• IoT 数据回传：`IotMqtt` 模块（内置 MQTT Broker）已提供基础 IoT 接入能力，但烧录工具侧的 MQTT 上报客户端未实现 |
| **d. PCBA 入半成品库** | 测试通过的 PCBA 统一入库 | ✅ **已实现** | 通过创建 PCBA 的 BOM（`bd_bom`），将原材料和工序组装，通过流转卡（`ProFlowCard`）下达给操作工。完成后通过 `wm_stock` 入库（半成品库），可被更高级 BOM 的成品生产领用。ERP 仓储模块（`ruoyi-erp/wm`）功能完整。 |

---

## 二、成品装配

| 步骤 | 用户描述 | Nexus 实现状态 | 差异说明 |
|------|---------|---------------|---------|
| **a. 烧录正式固件** | 根据订单烧录对应版本固件，扫码记录 PCBA SN 与固件版本 | ⚠️ **部分实现** | 数据模型完整（`mes_flash_record` 可记录 SN→固件版本映射），但需要产线工具执行实际烧录。`ProWorkOrderStep` 的 `stepFlashConfig` 字段支持工序级多类型绑定（固件/配置/MAC 池等），`ProFlowCardStep` 关联刷写记录。固件版本与客户订单的关联逻辑（根据 `so_order` 自动匹配固件版本）未实现。 |
| **b. 分配成品 SN** | MES 预生成成品 SN，建立虚拟档案 | ✅ **已实现** | `ProSn` 实体支持成品 SN 生成，`parent_sn_id` 字段已预留用于父子追溯（成品 SN 作为父节点）。但"预生成成品 SN 并建立虚拟档案"的前端流程可能需补充。 |
| **c. 关键件父子绑定** | 合盖前扫码绑定 PCBA/屏幕/电池/摄像头 ↔ 成品 SN | ⚠️ **部分实现** | **数据模型已实现：** `ProSn.parent_sn_id` / `parent_sn_code` 字段专门用于组件 SN→成品 SN 绑定追溯。<br><br>**追溯服务已实现：** `ProTraceabilityServiceImpl` 提供 `backward`（成品→组件逆向追溯）和 `forward`（组件→成品正向追溯）两种查询。<br><br>**未实现：** 绑定操作的**工位 UI**（扫码枪输入界面），以及**合盖前强制校验**的业务规则引擎。当前需通过 API 或定制前端实现绑定操作。 |
| **d. 整机装配** | 合盖、锁螺丝、贴标等 | ✅ **已实现** | 作为普通工序纳入流转卡管理，通过 `ProDispatch` 派工到人，`ProFlowCardStep` 记录完成情况。 |
| **e. 整机测试** | 写入成品 SN、烧录 MAC/蓝牙地址、射频校准、ATE 测试 | ⚠️ **部分实现** | **数据模型已实现：** `mes_flash_record` 支持 `flashTypeCode=mac/sn` 等参数刷写记录，`contentPayload` 字段存储 JSON 格式的实际写入值（如 `{"mac":"00:1A:2B:3C:4D:5E"}`）。`MacPoolFlashProvider` 和 `SnPoolFlashProvider` 已实现 MAC 地址池和 SN 池的资源分配逻辑。测试记录（`mes_test_record`）支持 ATE 测试结果回传。<br><br>**未实现：** 写入成品 SN 和 MAC 地址到设备存储器的**实际烧录动作**需要产线工具；射频校准等复杂测试需专用测试软件。 |
| **f. 成品入库** | 扫码校验父子绑定及整机测试结果 | ✅ **已实现** | `wm_stock` 入库操作支持校验逻辑。ERP 仓储模块有 `StockInConsumer` 处理入库消息。

---

## 三、SN 追溯与返修管理

| 步骤 | 用户描述 | Nexus 实现状态 | 差异说明 |
|------|---------|---------------|---------|
| **1. 全流程扫码留痕** | 各工位扫码记录 SN 流转节点 | ⚠️ **部分实现** | **已实现：** `ProSn` 记录 SN 全生命周期状态（IN_PRODUCTION / COMPLETED / SCRAPPED / SHIPPED），`ProFlowCardStep` 记录每个工序的完成情况，`mes_flash_record` 记录固件/MAC 等刷写信息，`mes_test_record` 记录测试结果。<br><br>**未实现：** 各工位扫码操作的前端 UI（PDA/工位平板界面），当前需通过 API 或定制开发。 |
| **2. 典型追溯查询** | 成品 SN → PCBA → 入库时间 → 固件版本 → 绑定整机 | ✅ **已实现** | `ProTraceabilityServiceImpl` 提供完整的双向追溯：<br>• `backward(mesWoId, snCode)`：成品工单/SN → 所需组件清单 + 实际装配的组件 SN<br>• `forward(componentMaterialId, componentSnCode)`：组件物料/SN → 消费该组件的成品工单 + 父 SN 链<br>• 追溯结果包含工单信息、BOM 快照（`ProWorkOrderBomLine`）、装配 SN 列表 |
| **3. 返修换板追溯** | 更换 PCBA → 解绑旧 SN → 绑定新 SN → 历史保留 | ⚠️ **部分实现** | **数据模型已支持：** `ProSn.parent_sn_id` 可重新绑定，`ProSn.sn_status` 支持 SCRAPPED 状态标记旧板，历史记录通过 `del_flag=0` 永久保留（逻辑删除）。<br><br>**未实现：** 专门的"更换子件"业务流程（一键解绑+重新绑定），以及更换原因记录的业务字段在前端尚不可用。 |

---

## 四、核心原则对照

| 原则 | 用户描述 | Nexus 实现情况 |
|------|---------|---------------|
| **SN 先行** | PCBA SN 在镭雕工序生成，早于所有贴片和测试 | ✅ 已实现：`ProSn` 可在工单下发时预生成，`source=BATCH` 批量生成 |
| **先绑后盖** | 关键件父子绑定必须在合盖前完成 | ⚠️ 数据模型已支持（`parent_sn_id`），但工位强制校验规则未实现 |
| **先测后入** | FCT 测试通过方可入库，整机测试通过方可出货 | ⚠️ 数据模型已支持（`mes_test_record.result=PASS`），但入库/出货的自动校验门禁未实现 |
| **解绑留痕** | 返修换板解绑旧 SN，历史记录不删除 | ✅ 已实现：逻辑删除（`del_flag`），且 `ProSn` 采用 ASSIGN_ID 雪花 ID，历史记录永久保留 |

---

## 五、模块实现完整度总览

### MES 模块（`ruoyi-mes`）

| 子模块 | 包路径 | 实现程度 | 关键说明 |
|--------|--------|---------|---------|
| 工单管理 | `pro` | ✅ 完整 | `ProWorkOrder` + `ProWorkOrderStep` + `ProWorkOrderBomLine`，含 MQ 异步下发（`WorkOrderReleaseConsumer`） |
| 流转卡 | `pro` | ✅ 完整 | `ProFlowCard` + `ProFlowCardStep`，工序流转卡全生命周期（PENDING→IN_PROGRESS→COMPLETED） |
| 派工管理 | `pro` | ✅ 完整 | `ProDispatch`，工序派工到人，含数量/良品/不良/报废统计 |
| SN 管理 | `pro` | ✅ 完整 | `ProSn`，支持批量/手动生成，父子 SN 追溯，SN 状态机 |
| 追溯服务 | `pro` | ✅ 完整 | `ProTraceabilityServiceImpl`，双向追溯（forward/backward） |
| 安灯告警 | `pro` | ✅ 完整 | `ProAndon` 实体及服务 |
| 固件管理 | `flash` | ✅ 完整 | `Firmware`，DRAFT→RELEASED→ARCHIVED，MinIO 存储，审批流 |
| 刷写记录 | `flash` | ✅ 完整 | `FlashRecord`，SN 级多类型刷写（固件/MAC/SN/配置），FlashTypeProvider SPI |
| 测试记录 | `flash` | ✅ 完整 | `TestItem` + `TestRecord`，SN 级全检，自动判定，模板化测试项 |
| 资源池 | `flash` | ✅ 完整 | `MesResourcePool` + `MesResourceItem`，MAC 地址池/SN 池管理 |
| 数据采集 | `dc` | ✅ 基础完整 | `DcCollectionPoint` + `DcDataRecord`，支持 MANUAL/IoT 来源 |
| 设备管理 | `eq` | ✅ 基础完整 | `EqEquipment` 档案 + 维护保养 + 维修记录 |
| 质量管理 | `qc` | ✅ 基础完整 | IQC/IPQC/OQC 检验流程 |
| SPC | `spc` | ✅ 基础完整 | 控制图 + 数据点 |

### IoT 模块（`ruoyi-iot`）

| 子模块 | 实现程度 | 关键说明 |
|--------|---------|---------|
| MQTT Broker | ✅ 完整 | 内置 Vert.x MQTT Server，设备认证（`IotMqttAuthenticator`）+ 鉴权（`IotMqttAuthorizatorPolicy`） |
| 消息管道 | ✅ 完整 | `IotStreamConsumer` → `IotMqttTopicParser` → `IotPropertyPayload` → `IotDataValueResolver` → 数据入库 |
| 告警检测 | ✅ 完整 | `IotAlertChecker` + `IotAlertNotifier` |
| SSE 实时推送 | ✅ 完整 | `IotRealtimeSseManager`，设备实时数据推送到前端 |
| **产线烧录工具 → MQTT 上报** | ❌ 未实现 | 需要产线工具侧实现 MQTT 客户端，将烧录/测试结果通过 MQTT 上报到 IoT 模块 |

### ERP 模块（`ruoyi-erp`）— 相关部分

| 子模块 | 实现程度 | 关键说明 |
|--------|---------|---------|
| BOM 管理 | ✅ 完整 | `bd_bom` + `bd_bom_material`，支持多级 BOM |
| 工艺路线 | ✅ 完整 | `bd_routing` + `bd_routing_process`，工序定义 |
| 工单管理 | ✅ 完整 | `PrdWorkOrder`，生产计划排产 |
| 委外订单 | ✅ 基础 | `PrdSubcontract` CRUD + 状态机，**但缺少 MES 深度集成** |
| 仓储管理 | ✅ 完整 | `wm_stock` + `wm_warehouse` + `wm_location`，入库/出库/盘点 |
| 物料管理 | ✅ 完整 | `bd_material`，物料主数据 |

---

## 六、关键差距与待开发项（按优先级排序）

### P0 — 核心缺失（影响产线运行）

| 待开发项 | 涉及模块 | 说明 |
|---------|---------|------|
| **产线烧录上位机工具** | MES `flash` + IoT | 桌面/工控机应用，调用 MES API 获取固件，执行实际烧录，通过 MQTT 回传结果。这是当前**最大的断点**，固件管理和测试记录的数据模型再完整，没有上位机工具就无法在产线落地。 |
| **工位扫码 UI（PDA/工位平板）** | MES `pro` + 前端 | 各工位的扫码操作界面，用于 SN 绑定/解绑、工序报工、关键件绑定等操作。当前需通过 API 调用，产线工人无法直接使用。 |

### P1 — 重要增强

| 待开发项 | 涉及模块 | 说明 |
|---------|---------|------|
| **入库/出货校验门禁** | MES `flash` + ERP `wm` | 入库时自动校验 FCT 测试结果（`mes_test_record`），出货时校验整机测试结果，未通过不允许操作 |
| **合盖前强制绑定校验** | MES `pro` | 在"合盖"工序开始前，检查该工单所有关键件是否已完成父子 SN 绑定 |
| **返修换板业务流程** | MES `pro` | 一键解绑旧 SN + 重新绑定新 SN + 记录更换原因 + 追溯链完整 |
| **外协厂数据协同** | ERP `prd` + MES `pro` | 向外协厂推送 SN 列表、固件版本、测试项标准；接收外协厂回传的测试结果 |

### P2 — 持续优化

| 待开发项 | 涉及模块 | 说明 |
|---------|---------|------|
| 固件版本与客户订单自动匹配 | MES `flash` + ERP `so` | 根据销售订单自动选择对应固件版本 |
| 治具/夹具管理 | MES `eq` | 测试治具的生命周期管理（领用、归还、校准） |
| SOP 文档管理 | MES `pro` | 工序级 SOP 附件上传、版本管理、工位查看 |
| SMT 产线设备数据采集 | IoT + MES `dc` | 贴片机、回流焊炉、AOI 检测仪的实时数据采集 |
| 委外订单自动闭环 | ERP `prd` | 根据入库数量和良率自动计算损耗，自动关闭委外订单 |

---

## 七、数据流架构图（当前实现 vs 理想状态）

### 当前已实现的数据流

```
[固件上传] → mes_firmware (MinIO OSS)
    ↓
[ProWorkOrder 工单下发] → ProFlowCard 流转卡 → ProDispatch 派工
    ↓
[ProSn SN 生成] → 关联工单/物料/流转卡
    ↓
[ProFlowCardStep 工序报工] → 完成数量/良品/不良
    ↓
[mes_flash_record 刷写记录] ← API 手动录入或 AUTO 上报
    ↓
[mes_test_record 测试记录] ← API 手动录入或 AUTO 上报
    ↓
[ProTraceability 追溯] → forward/backward 双向查询
    ↓
[wm_stock 入库] → 半成品/成品入库
```

### 缺失的产线工具层

```
[产线工控机/上位机]
    ├── 获取 SN 列表 → MES API → ProSn
    ├── 拉取固件 → MES API → mes_firmware → MinIO 下载
    ├── 执行烧录 → 烧录器硬件
    ├── 执行测试 → 测试仪器硬件
    ├── 上报刷写结果 → MQTT → IoT 模块 → mes_flash_record
    └── 上报测试结果 → MQTT → IoT 模块 → mes_test_record
```

---

## 八、核心结论

1. **后端数据模型非常完整**：MES 模块的 `flash` 子包（固件/刷写/测试/资源池）设计完善，覆盖了硬件生产全流程的数据层需求，SPI 扩展体系（`FlashTypeProvider`）支持未来扩展。

2. **最大断点是产线工具层**：所有烧录、测试、扫码操作都需要**上位机/产线工具**来执行实际硬件操作。当前 MES 提供了完整的 API 和数据模型，但缺少桌面端或工控机端的工具来实现"烧录固件到设备""扫码绑定 SN""自动回传测试结果"等物理操作。

3. **IoT 模块已就绪**：内置 MQTT Broker 和数据管道可以接收产线设备上报的数据，只需要产线工具侧实现 MQTT 客户端即可完成数据闭环。

4. **外协功能是短板**：委外订单仅有基础 CRUD，与 MES 的深度集成（SN 下发、固件分发、测试回传、自动闭环）基本未开发。

5. **前端工位 UI 待补充**：PDA/工位平板扫码界面需要开发，当前仅可通过 PC 端 Web 页面操作，不适合产线环境。