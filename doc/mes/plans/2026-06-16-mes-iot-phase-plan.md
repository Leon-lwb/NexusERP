# MES Phase 1~3 + IoT 阶段一实施计划

> **面向代理执行者：** 必选子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用复选框语法 `- [ ]` 跟踪。

**目标：** 分三期完成 MES 执行主干建设，并在其后交付 IoT 最小可用设备数据链路，确保其能够与 ERP 清晰集成，并为后续 MES 联动预留稳定扩展点。

**架构：** MES 作为独立模块引入，拥有自己的 `mes` 数据源和业务库，同时通过跨数据源读取和 RabbitMQ 事件复用 ERP 主数据与库存能力。IoT 在后续作为独立模块引入，拥有自己的 `iot` 数据源，采用 `MQTT + Redis Streams + SSE` 构建最小可用数据链路，并为 MES 的设备关联和告警联动暴露稳定接口契约。

**技术栈：** Spring Boot 3.5.x、MyBatis-Plus、dynamic-datasource、RabbitMQ、Flowable、Redisson、SnailJob、SSE、Vue 3、Element Plus、ECharts、Moquette

---

## 交付节奏

| 阶段 | 周期 | 重点 | 输出物 |
|------|------|------|------|
| MES Phase 1 | Week 17-21 | 模块骨架、数据源、ERP 桥接、MES 工单 | `ruoyi-mes` 最小可运行主干 |
| MES Phase 2 | Week 22-27 | 流转卡、派工、工序执行、安灯、SN 追溯 | 车间执行闭环 |
| MES Phase 3 | Week 28-32 | 质量、设备、采集、SPC | MES 全业务闭环 |
| IoT 阶段一 | Week 33-36 | 设备接入、数据管道、实时看板 | 第一台真实设备全链路可见 |

## 约束原则

- ERP 仍然是物料、BOM、工艺路线、工序、仓库、库位和库存的唯一事实源。
- MES 启动后，不允许 ERP 继续通过占位监听器消费并丢弃未来的 MES 消息。
- 正式开发前先统一契约，包括 MQ 消息、桥接服务接口、MES/IoT 集成接口。
- 每一期都先保证“最小可运行路径”落地，再做可选增强。

## 分阶段排期

### MES Phase 1：基础框架 + ERP 桥接 + 工单

| 周次 | 工作流 | 关键任务 | 交付物 | 前置依赖 | 验收标准 |
|------|------|------|------|------|------|
| Week 17 | 模块基础 | 创建 `ruoyi-mes`；注册 Maven 模块；补自动装配；在 `application-dev.yml` 中增加 `mes` 数据源；建立 `com.nexus.mes` 基础包结构 | 模块可编译；数据源可启动；菜单和权限骨架已就绪 | ERP 现有 MQ 发送端 | `ruoyi-admin` 能在启用 `mes` 数据源后正常启动 |
| Week 17 | 契约清理 | 定义桥接与 MQ 契约：`IMesErpBridgeService`、`WorkOrderReleaseMessage` 消费契约、`MesWorkOrderCompletedMessage` 草案 DTO | 跨模块稳定接口 | ERP 文档与现有 MQ 拓扑 | 契约评审完成，接口命名不再含占位语义 |
| Week 18 | ERP 桥接 | 实现物料、BOM、工艺路线、工序、工作中心 ERP 只读桥接；将数据源切换封装到单一服务中 | 只读桥接服务 + API 包装层 | ERP `bd/prd/wm` API 与表结构 | MES 可读取 ERP 主数据且无需复制建表 |
| Week 18 | 只读前端 | 新增 MES 侧主数据只读页面，或直接复用 ERP 选择器 API | `plus-ui/src/views/mes/md/` 下的基础页面/组件 | 桥接服务或 ERP API 路由 | 用户可在 MES 上下文中浏览 ERP 主数据 |
| Week 19 | MES 工单后端 | 完成 `pro_work_order` 和 `pro_work_order_step` 的 Domain、Mapper、Service、Controller；消费 `WorkOrderReleaseMessage`；按 ERP 工艺路线自动生成工序 | MES 工单创建链路 | ERP 工单下达消息 | ERP 审批通过后能生成 MES 工单记录 |
| Week 20 | 审批流 | 为 MES 工单集成 Flowable 提交/审批/驳回流程；打通状态 `CREATED -> RELEASED` | `submit/proc` 接口与流程按钮 | ERP 现有 Flowable 模式 | 工单审批闭环完成且前端可见 |
| Week 21 | Phase 1 前端 | 完成工单列表、详情页、工序 Tab、审批操作、菜单权限和冒烟验证 | `mes/pro/workorder` 页面集 | 后端与流程已完成 | Phase 1 演示通过：ERP 审批工单后，MES 可看到并审批工单 |

**Phase 1 出口标准**

- `ruoyi-mes` 模块存在且已完成 Maven 注册。
- `mes` 数据源已配置并完成启动验证。
- ERP 工单下达消息由 MES 正式消费，不再走 ERP 占位消费路径。
- MES 工单与工序已可查询，并具备审批能力。

### MES Phase 2：流转卡 + 派工 + 安灯 + SN

| 周次 | 工作流 | 关键任务 | 交付物 | 前置依赖 | 验收标准 |
|------|------|------|------|------|------|
| Week 22 | 流转卡引擎 | 创建 `pro_flow_card` 和 `pro_flow_card_step`；实现创建、下达、开工、完工、关闭、取消状态机 | 流转卡服务与控制器 | Phase 1 工单基础完成 | 一张 MES 工单可生成流转卡及工序步骤 |
| Week 23 | 工序推进 | 实现自动推进下一工序、数量汇总、工单完工聚合与异常状态保护 | 稳定的工序完工逻辑 | 流转卡状态机 | 所有工序状态切换符合设计规则 |
| Week 24 | 派工 | 创建 `pro_dispatch`；实现操作员/工作中心派工；支持派工查询与完成跟踪 | 派工管理后端 | 流转卡工序记录 | 可创建派工并由操作员领取执行 |
| Week 25 | 车间执行 | 增加扫码开工/完工接口、操作员动作、良品/不良/报废数量记录及简单锁控制 | 执行接口与页面动作 | 派工能力已完成 | 操作员可完成从流转卡到工序的真实执行闭环 |
| Week 26 | 安灯 | 创建 `pro_andon`；实现呼叫/响应/解决流程；复用现有 SSE 基础设施实时推送；定义 `IMesAndonService` 供未来 IoT 告警接入 | 实时安灯看板 | SSE 基础设施 | 安灯看板无需刷新即可更新 |
| Week 27 | SN 追溯 | 创建 `pro_sn`；支持 SN 生成、绑定、状态变更、父子关系；完成 SN 查询和追溯树前端 | SN 追溯页面与接口 | 执行链路和流转卡已完成 | 用户可以按 SN 追溯产品在 MES 中的执行过程 |

**Phase 2 出口标准**

- MES 已具备可用的车间执行闭环。
- 流转卡、派工和操作员执行数据保持一致。
- 安灯能够实时推送事件。
- SN 追溯已在前端可视化展示。

### MES Phase 3：质量 + 设备 + 采集 + SPC

| 周次 | 工作流 | 关键任务 | 交付物 | 前置依赖 | 验收标准 |
|------|------|------|------|------|------|
| Week 28 | IQC/IPQC/OQC 落地 | 创建 `qc_iqc`、`qc_ipqc`、`qc_oqc`；实现 CRUD、状态机和不良 JSON 结构；定义 ERP 回传消息契约 | 质量模块后端基础 | Phase 2 执行数据 | 三种检验类型均可创建并完结 |
| Week 29 | ERP 质量联动 | 实现 `IQC -> ERP` 回传收货质检状态；将 IPQC/OQC 与 MES 执行和出货前校验规则打通 | ERP/MES 质量事件闭环 | MQ 契约 | IQC 完成后能更新 ERP 收货质检状态 |
| Week 30 | 设备 | 创建 `eq_equipment`、`eq_maintenance_plan`、`eq_repair`；实现台账、保养、维修流程；预留 `iot_device_id` 关联字段 | 设备管理页面与接口 | 稳定的工作中心与工序数据 | 设备台账、保养、维修均可运行 |
| Week 31 | 数据采集 | 创建 `dc_collection_point` 和 `dc_data_record`；实现人工采集路径和 IoT 来源导入契约；统一采集归属口径 | 人工采集能力 | 设备与工序数据 | MES 可按采集点存储人工采集记录 |
| Week 32 | SPC + 集成 | 创建 `spc_control_chart` 和 `spc_data_point`；完成控制图计算与前端页面；执行 Phase 1-3 集成冒烟测试 | SPC 图表页面与集成报告 | 数据采集已完成 | 端到端演示通过：工单 -> 执行 -> 质量 -> 设备 -> 采集 -> SPC |

**Phase 3 出口标准**

- 质量闭环可用，IQC 可通知 ERP。
- 设备模块可用，且已为未来 IoT 关联预留接入点。
- MES 人工采集与 SPC 已完整落地。
- 已产出集成测试报告。

### IoT 阶段一：最小设备数据链路

| 周次 | 工作流 | 关键任务 | 交付物 | 前置依赖 | 验收标准 |
|------|------|------|------|------|------|
| Week 33 | 模块基础 | 创建 `ruoyi-iot`；注册 Maven 模块；补 `iot` 数据源与 DDL；建立 `device/mqtt/pipeline/realtime/task` 包结构；完成产品和设备 CRUD | IoT 模块基础 + 主数据 | 更推荐在 MES Phase 3 设备模型稳定后联动，但不是硬依赖 | IoT 模块可启动且 CRUD 可用 |
| Week 33 | 集成契约 | 定义 MES 查询设备最新数据的 REST 契约，以及 `IMesAndonService.createAndonFromAlert()` 接口契约 | 稳定的 MES/IoT 边界 | MES 设备与安灯概念已明确 | 在数据管道开发前完成契约评审 |
| Week 34 | MQTT 接入 | 集成内嵌 Moquette；补认证与消息监听；解析 Topic 规范；维护设备在线/离线状态 | MQTT 入站链路 | 产品/设备主数据 | 测试设备可连接并成功发布消息 |
| Week 34 | 数据管道 | 实现 Redis Stream 写入、消费者组处理、MySQL 持久化、最新值缓存、在线状态缓存、硬编码阈值告警 | 核心采集链路 | Redis + MySQL + MQTT 接入 | 上报数据可持久化，最新值缓存实时更新 |
| Week 35 | 实时前端 | 完成产品页面、设备列表、实时仪表盘、SSE 接口、ECharts 卡片与趋势图 | `plus-ui/src/views/iot/` 基础页面 | 数据管道与缓存已完成 | 用户可在浏览器中看到实时设备数据 |
| Week 36 | 定时任务 + 加固 | 增加数据清理、心跳检测、Stream 修剪、重放处理、基础可观测能力，并完成一台真实设备联调 | 最小运行保障能力 | 数据管道已跑通 | 一台真实设备可稳定运行并完成全天演示 |

**IoT 阶段一出口标准**

- 一台真实设备可通过 MQTT 上报，并在前端可见。
- 最新值缓存和在线状态可查询。
- 基础告警可对外展示，且已为未来 MES 安灯联动预留接口。
- 已具备数据清理、心跳检测和 Stream 修剪等运行任务。

## 跨阶段依赖表

| 依赖项 | 使用阶段 | 作用说明 |
|------|------|------|
| ERP MQ 发送端 + 常量 | MES Phase 1 | MES 工单创建起点来自 ERP 审批事件 |
| `mes` 数据源 | MES Phase 1 | MES 模块没有独立库则无法启动 |
| ERP 主数据桥接服务 | MES Phase 1-3 | MES 通过桥接读取物料/BOM/工艺/工序，避免复制建表 |
| 流转卡执行数据 | MES Phase 2-3 | 质量、SN、SPC 都依赖真实执行数据 |
| MES 设备模型 | IoT 阶段一集成契约 | 未来设备关联需要统一设备主模型 |
| `iot` 数据源 | IoT 阶段一 | IoT 产品、设备、采集数据位于独立库 |

## 资源建议

| 角色 | MES Phase 1 | MES Phase 2 | MES Phase 3 | IoT 阶段一 |
|------|------|------|------|------|
| 后端 | 1.0 | 1.0 | 1.0 | 1.0 |
| 前端 | 0.5 | 0.5 | 0.5 | 0.5 |
| QA / UAT | 0.2 | 0.2 | 0.3 | 0.3 |
| 业务负责人 | 0.1 | 0.1 | 0.2 | 0.1 |

## 周里程碑

- Week 17：`ruoyi-mes` 模块与 `mes` 数据源可用
- Week 19：ERP 工单可创建 MES 工单
- Week 21：MES Phase 1 演示完成
- Week 23：流转卡引擎可用
- Week 26：安灯实时看板可用
- Week 27：SN 追溯演示完成
- Week 29：`IQC -> ERP` 回传完成
- Week 32：MES 全链路集成演示完成
- Week 34：`MQTT -> Redis Streams -> MySQL` 链路打通
- Week 35：实时仪表盘可用
- Week 36：IoT 阶段一真实设备稳定演示完成

## 高风险项

| 风险 | 影响阶段 | 应对措施 |
|------|------|------|
| ERP 占位消费者吞掉 MES 消息 | Phase 1 | 一旦 MES 正式消费者上线，立即禁用或移除占位消费者 |
| MQ 发送与 ERP 状态更新不在同一提交点 | Phase 1 | 改为事务提交后发送或采用 outbox 模式 |
| MES 与 IoT 的采集归属逐步漂移 | Phase 3 / IoT | 冻结规则：MES 负责人工采集，IoT 负责自动采集，SPC 消费 MES 归档记录 |
| 前端页面范围膨胀导致排期失控 | 全阶段 | 先交付列表/详情主链路，再延后高级看板 |
| IoT 真实设备协议细节拖慢演示 | IoT | 先用标准 MQTT 模拟器打通，再替换一台真实设备 |

## 交接说明

- 如果当前人力紧张，MES Phase 1 和 IoT 阶段一必须严格控制在最小范围内，非关键看板统一后置。
- 在 `IMesAndonService` 和设备关联字段稳定之前，不要提前启动 IoT 告警转 MES 安灯联动开发。
- 所有消息和接口契约都应先落入共享、可评审的文档，再开始消费者和调用方实现。
