# NexusERP — ERP/MES/IoT 三模块共同升级方案

> **编制日期**: 2026-06-25
> **基于**: 三模块主文档更新至 2026-06-25 交付状态后制定
> **前置文档**: `NexusERP_ERP文档.md`、`NexusERP_MES文档.md`、`NexusERP_IoT文档.md`

---

## 一、背景与现状（截至 2026-06-25）

### 1.1 三模块交付现状

| 模块 | 阶段 | 时间线 | 状态 | 关键交付 |
|------|------|--------|------|---------|
| ERP | 核心+补全+增强 | Week 1-16 | ✅ 完成 | 主数据/采购/仓储/生产/销售/排班/MQ基础设施 |
| MES | Phase 1-3 | Week 17-27（领先原计划5周） | ✅ 完成 | 工单/流转卡/派工/安灯/SN/质量/设备/采集/SPC（~220后端+~85前端文件） |
| IoT | 阶段一 | Week 33-36 | ✅ 完成+验收 | MQTT接入/Redis管道/SSE仪表盘/告警/SnailJob/租户访问控制 |

### 1.2 预留集成点现状

| 集成点 | 路径 | 状态 | 说明 |
|--------|------|------|------|
| ERP→MES 工单下达 MQ | ERP prd → MES pro | ✅ 已通 | Week 16 |
| MES→ERP 工单完工 MQ | MES pro → ERP prd/wm | ✅ 已通 | Week 25（修复exchange bug+ErpWorkOrderCompletedConsumer） |
| MES→ERP IQC检验完成 MQ | MES qc → ERP wm | ✅ 已通 | Week 25（ErpIqcCompletedConsumer） |
| IoT→MES 安灯联动 | IoT alert → MES andon | ✅ 已通 | Week 23（IMesAndonService @Primary + IotAlertNotifier） |
| MES→IoT 设备数据查询 | MES eq → IoT device REST | ⏳ 契约就绪 | IotDeviceDataQueryService 已实现，MES侧未调用 |
| 设备双向软关联 | iot_device.mes_equipment_id ↔ eq_equipment.iot_device_id | ⏳ 字段已建 | 双向查询API未实现 |
| IoT自动采集→MES数据记录 | IoT pipeline → dc_data_record | ⏳ 常量预留 | point_type=AUTO + source=IoT 已预留，写入逻辑未实现 |
| ERP收货单→IQC自动创建 | ERP wm_receive → MES qc | ❌ 未建 | 收货单审批通过后MQ通知MES创建IQC |

### 1.3 升级目标

从**"三模块各自可用"**升级到**"三模块协同闭环"**：
- 集成联动：打通所有预留集成点，实现ERP→MES→IoT数据流闭环
- 功能补齐：各模块独立功能增强（ERP Phase B/C/D、IoT阶段二、MES运行时补齐）
- 可观测性：统一链路追踪、健康检查、数据归档、租户隔离巡检

---

## 二、升级路线图总览（三波次）

| 波次 | 主题 | 周次估算 | 人天 | 依赖 |
|------|------|---------|------|------|
| **第一波** | 集成联动深化 | Week 37-40 | ~12 | 无（打通已有预留点） |
| **第二波** | 各模块功能补齐 | Week 41-46 | ~30 | 第一波部分 |
| **第三波** | 可观测性与运维 | Week 47-49 | ~8 | 第一波traceId基础 |

**总计**: ~50 人天 / ~13 周（按需模块不计入固定工期）

---

## 三、第一波：集成联动深化（优先级最高，打通预留集成点）

> **目标**: 打通所有预留集成点，实现 ERP↔MES↔IoT 数据流闭环。
> **先例模式**: 沿用 `IMesAndonService`（契约接口+@Primary 实现）、`IotDeviceDataQueryService`（common-iot 契约）、`BaseMessageConsumer`（MQ 消费者）已有模式，无新契约设计风险。

### 3.1 MES 设备 ↔ IoT 设备软关联打通

**现状**: `eq_equipment.iot_device_id`（MES Week 26 建）+ `iot_device.mes_equipment_id`（IoT Week 33 建）字段已建，但双向查询未实现。

**任务**:
- MES 设备台账页(`eq/equipment/index.vue`)增加"IoT在线状态"列：调 `IotDeviceDataQueryService.getOnlineStatus(deviceId)` 显示 ONLINE/OFFLINE
- MES 设备台账页增加"IoT最新数据"按钮：调 `IotDeviceDataQueryService.getLatestData(deviceId)` 弹窗显示
- IoT 设备页(`iot/device/index.vue`)增加"MES设备"链接：按 `mes_equipment_id` 跳转 MES 设备详情
- 双向关联设置：MES 设备编辑页可设置 `iotDeviceId`；IoT 设备编辑页可设置 `mesEquipmentId`

**交付物**: 4 个前端改动 + 2 个后端查询方法（MES eq Service 加 IoT 状态查询 helper）

**人天**: 2

**验收**: MES 设备台账显示 IoT 在线状态灯；IoT 设备页有 MES 设备链接

### 3.2 IoT 自动采集归档到 dc_data_record（AUTO 采集点）

**现状**: `dc_collection_point.point_type=AUTO` + `dc_data_record.source=IoT` 常量已预留（MES Week 27），无写入逻辑。

**任务**:
- 新建 SnailJob 定时任务 `IotDataArchiveTask`（或在 MES 侧新建 `DcAutoCollectTask`）：
  - 查 `dc_collection_point` 中 `point_type=AUTO` 且关联 `iot_device_id` 的采集点
  - 调 `IotDeviceDataQueryService.getHistoryData(deviceId, start, end)` 拉 IoT 历史数据
  - 写入 `dc_data_record`（`source=IoT`，`numeric_value`/`text_value`，按 `collect_time+propertyCode` 去重）
  - 频率：每 5 分钟拉一次（可配置）
- MES 设备台账 `iotDeviceId` 字段与 `dc_collection_point.equipmentId` 关联（通过设备表间接关联 IoT 设备）

**交付物**: 归档定时任务 + 去重逻辑 + source=IoT 写入

**人天**: 3

**依赖**: 3.1（设备软关联，AUTO 点需关联 IoT 设备 ID）

**验收**: AUTO 采集点定时归档 dc_data_record（source=IoT），SPC analyze 可消费这些数据

### 3.3 SPC OOC 触发安灯

**现状**: SPC analyze 判定 OOC（Week 27，4 条规则）但无联动；安灯系统（Week 23）已支持 EQUIPMENT 类型和 QUALITY 类型。

**任务**:
- `SpcControlChartServiceImpl.analyze` 方法中，当 OOC 命中 RULE1（1 点超限，最严重）时，调 `IProAndonService.callAndon` 创建 QUALITY 类型安灯
- 限频防风暴：同一控制图 10 分钟内仅触发一次安灯（参考 `IotAlertNotifier` 限频模式，用 Redis key `spc:andon:throttle:{chartId}` TTL=600s）
- 安灯 reason 包含：控制图名称 + OOC 规则 + 超限值

**交付物**: `SpcControlChartServiceImpl.analyze` 内 OOC→安灯联动 + 限频逻辑

**人天**: 2

**依赖**: Week 23 安灯 + Week 27 SPC（均已就绪）

**验收**: SPC 分析后 OOC 点自动创建安灯（看板可见），重复分析 10 分钟内不重复创建

### 3.4 ERP 收货单驱动 IQC 自动创建

**现状**: IQC 手动新建（Week 25）；ERP 收货单 `WmMaterialReceive` 审批通过后无联动到 MES。

**任务**:
- 新建 MQ 消息 DTO `ReceiveApprovedMessage`（在 `ruoyi-common-mq`，字段：receiveId, receiveCode, materialId, materialCode, materialName, vendorId, vendorName, quantity, tenantId）
- 在 `ruoyi-common-mq` 声明队列常量 `MES_IQC_CREATE_QUEUE` + 路由键 `mes.iqc.create` + 队列 `erp.mes.iqc.create.queue`（绑定 TOPIC_EXCHANGE）
- **ERP 侧**：`WmMaterialReceiveCrudServiceImpl` 审批通过（`STATUS_APPROVED`）后，`afterCommit` 发 `ReceiveApprovedMessage`（参考 `PrdWorkOrderServiceImpl` 的 `sendWorkOrderReleaseMessage` 模式）
- **MES 侧**：新建 `IqcAutoCreateConsumer extends BaseMessageConsumer<ReceiveApprovedMessage>`，`@RabbitListener(MES_IQC_CREATE_QUEUE)`，`doProcess` 调 `IQcIqcService.insertByBo`（设 receiveId/vendorId/quantity 等，自动生成 IQC 编号）
- 去重：同一 receiveId 已创建 IQC 则跳过（查 `qc_iqc.receive_id`）

**交付物**: DTO + 队列常量 + ERP 生产者 + MES 消费者

**人天**: 3

**验收**: ERP 收货单审批通过 → MQ → MES 自动创建 IQC 检验单（receiveId 关联）；重复审批不重复创建

### 3.5 第一波验收标准

| 验收项 | 验收标准 |
|--------|----------|
| 设备软关联 | MES 设备台账显示 IoT 在线状态 + IoT 设备页有 MES 链接 |
| IoT 自动采集归档 | AUTO 采集点定时归档 dc_data_record（source=IoT） |
| SPC OOC→安灯 | SPC 分析 OOC 自动创建安灯（看板可见，10分钟限频） |
| 收货单驱动 IQC | ERP 收货单审批 → MES 自动创建 IQC 检验单（幂等） |

---

## 四、第二波：各模块功能补齐（按需启动，优先级中）

> **目标**: 各模块独立功能增强，按业务优先级和触发条件驱动。
> **注**: 以下子项可独立启动，无统一截止日期。

### 4.1 ERP Phase B/C/D + P1 补全（~49人天）

> 来源：`NexusERP_ERP文档.md` §四、§三

| 阶段 | 内容 | 人天 |
|------|------|------|
| P1 补全 | 物料-供应商关系管理 + 部门请购单 + 采购合同管理 | ~8 |
| Phase B 结构优化 | 报工自动回写工单 + 库存预留机制 + 多级BOM展开优化 + 采购订单状态机完善 | ~18 |
| Phase C 流程增强 | 生产排程甘特图 + 物料齐套检查 + 产能负荷分析 + 质量成本归集 | ~20 |
| Phase D 智能化 | 需求预测模型 + 智能补货建议 + 异常检测 + 决策看板 | ~11 |

### 4.2 IoT 阶段二按需扩展（~18人天，触发条件驱动）

> 来源：`NexusERP_IoT文档.md` §八

| 任务 | 人天 | 触发条件 |
|------|------|---------|
| OPC UA / Modbus 协议适配 | 4 | 需要连接 PLC/SCADA 设备时 |
| 规则引擎 | 5 | 硬编码告警不满足需求时 |
| 告警系统（规则表+日志表） | 4 | 需要完整告警生命周期管理时 |
| 时序数据库迁移（TDengine） | 3 | iot_device_data > 1000万行时 |
| 边缘网关 | 2 | 设备超100台或需协议转换时 |

### 4.3 MES 运行时补齐（~8人天）

> 来源：Week 25/26/27 实施计划的排除项

| 任务 | 人天 | 说明 |
|------|------|------|
| 不良品入库/退货流程 | 3 | IQC UNQUALIFIED 联动 ERP 不良品入库（WmStockOperation / PuPurchaseReturn）；依赖 4.1 ERP wm 模块 |
| 保养自动到期提醒 | 2 | SnailJob 定时扫描 `eq_maintenance_plan.next_date` 到期，SSE 推送提醒 |
| 点检执行流程 | 3 | 设备点检按 `inspection_items` JSON 标准执行 + 记录 `last_inspection` 结果 |

### 4.4 第二波验收标准

各子项按触发条件启动，独立验收。无统一截止日期。

---

## 五、第三波：可观测性与运维（平台化，优先级低但基础）

> **目标**: 统一链路追踪、健康检查、数据归档、租户隔离巡检。
> **前置**: 第一波 traceId 贯穿基础（BaseMessage 已有 traceId，REST 层需透传）。

### 5.1 统一链路追踪（3人天）

**现状**: MQ 消息有 `traceId`（`BaseMessage.traceId`）+ `messageId`，但 REST 请求/跨库查询无贯穿。

**任务**:
- REST 层：在 `ruoyi-common-web` 的请求拦截器中生成 `traceId`（UUID）并放入 MDC + 响应头 `X-Trace-Id`
- MQ 层：`ReliableMessageProducer.send` 已设 `x-trace-id` header；消费者 `BaseMessageConsumer.processMessage` 从 header 恢复到 MDC
- 跨库查询：`@DS("erp")` 切换时 MDC 保持（ThreadLocal 不变，MDC 自动跟随）
- 日志格式：`logback-plus.xml` pattern 加 `%X{traceId}`

**验收**: 一次 ERP→MQ→MES→跨库 查询请求，所有日志共享同一 traceId

### 5.2 健康检查聚合（2人天）

**任务**:
- 三模块 actuator `/actuator/health` 端点聚合
- MQ 队列积压监控：`erp.workorder.completed.queue` / `erp.iqc.completed.queue` 消息数 < 阈值
- Redis 连接监控：ping 延迟 < 阈值
- 可选：聚合仪表盘（Spring Boot Admin 或自建简单页面）

**验收**: 健康检查端点返回三模块状态 + MQ 积压 + Redis 延迟

### 5.3 数据归档策略（3人天）

**任务**:
- `dc_data_record` 归档：SnailJob 定时将 90 天前数据导出到归档表/文件，主表仅保留热数据
- `iot_device_data` 归档：已有 `IotDataCleanupTask` 清理 90 天数据；配合 IoT 阶段二 TDengine 迁移
- `mq_message_record` 清理：30 天前已消费消息定期清理

**验收**: 归档任务运行正常，主表行数可控

### 5.4 租户隔离巡检（1人天）

**任务**:
- 定时校验三模块 tenant_id 隔离正确性：
  - MQ 消费者租户恢复：检查 `TenantHelper.dynamic` 是否覆盖所有消费者
  - 跨库查询租户条件：检查 `TenantLineInnerInterceptor` 是否在 @DS("erp") 查询时正确注入 tenant_id
- 参考：IoT Week 34 MQTT 租户访问控制模式（`cc3781a`）

**验收**: 巡检脚本/任务运行正常，无隔离遗漏

### 5.5 第三波验收标准

| 验收项 | 验收标准 |
|--------|----------|
| 链路追踪 | 一次请求 traceId 贯穿 ERP→MQ→MES→跨库 |
| 健康检查 | 聚合端点返回三模块状态 |
| 数据归档 | 归档任务运行 + 热表行数可控 |
| 租户隔离 | 巡检无遗漏 |

---

## 六、优先级与依赖关系图

```
第一波 (Week 37-40, ~12人天)
  ├── 3.1 设备软关联 ──────────────────── 3.2 IoT自动采集归档 (依赖3.1)
  ├── 3.3 SPC OOC→安灯 ────────────────── 无依赖
  └── 3.4 收货单驱动IQC ────────────────── 无依赖

第二波 (Week 41-46+, ~30人天, 按需)
  ├── 4.1 ERP Phase B/C/D + P1 补全 ────── 独立
  ├── 4.2 IoT 阶段二（按需触发）─────────── 独立
  └── 4.3 MES 运行时补齐 ──────────────── 4.3.1 依赖 4.1 ERP wm

第三波 (Week 47-49, ~8人天)
  ├── 5.1 统一 traceId ────────────────── 无依赖（可提前）
  ├── 5.2 健康检查 ───────────────────── 依赖 5.1
  ├── 5.3 数据归档 ───────────────────── 独立
  └── 5.4 租户隔离巡检 ────────────────── 依赖 5.1

第一波 是闭环关键，建议优先；
第二波 按业务优先级和触发条件独立启动；
第三波 平台稳定后推进。
```

---

## 七、风险与注意事项

| 风险 | 应对 |
|------|------|
| 集成联动引入跨模块依赖 | 沿用已有契约接口模式（IMesAndonService / IotDeviceDataQueryService），不引入新耦合 |
| IoT 自动采集归档数据量大 | 去重+频率控制（5分钟一次）+ 归档策略（5.3） |
| SPC OOC→安灯需限频防风暴 | 参考 IotAlertNotifier 限频模式（Redis key TTL） |
| 收货单驱动 IQC 幂等 | 同一 receiveId 查 qc_iqc 去重 |
| ERP Phase B/C/D 工作量大（~49人天） | 按优先级分拆，P1 补全优先，Phase D 智能化最后 |
| IoT 阶段二触发条件未满足 | 保持"按需"机制，条件满足时再启动，不强制排期 |
| 三文档时间线再次漂移 | 本文档为统一路线图，各波次实施时出周计划（沿用 doc/mes/plans/ 模式），主文档只记录已交付事实 |

---

## 八、与主文档的关系

| 文档 | 定位 |
|------|------|
| `NexusERP_ERP文档.md` | 记录 ERP 已交付事实（Week 1-16），§十四 进度更新 |
| `NexusERP_MES文档.md` | 记录 MES 已交付事实（Week 17-27），§十九 进度更新 |
| `NexusERP_IoT文档.md` | 记录 IoT 已交付事实（Week 33-36），§二十一 进度更新 |
| **本文档** | 规划未交付升级路线图（三波次），主文档记录已交付事实 |

各波次实施时再出周计划（沿用 `doc/mes/plans/` 归档模式），本文档为总体路线图，不做详细任务分解。

---

**编制日期**: 2026-06-25
**执行者**: AI Agent
**审核状态**: 待用户审查
