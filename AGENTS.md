# AGENTS.md — Nexus-ERP 全工具 Agent 协作指南

> 核对日期：2026-09-28。适用于 Codex、Claude Code、Gemini CLI、Cursor 及其他参与本仓库工作的 Agent/自动化工具；不依赖某一工具的专有命令。

本文件是仓库级统一指导。工具若不会自动读取 `AGENTS.md`，请在任务上下文中显式提供它；不要维护内容相同但逐渐漂移的工具专属副本。先看 `git status` 与相关模块代码，尊重工作区已有修改；未经要求不清理他人的改动、不自动提交/推送、不运行会改写现有数据库的脚本。以**实际代码/配置及最新提交**为事实来源，历史方案和测试报告只代表其标注日期、分支与环境。资料入口见 [文档中心](doc/README.md) 和 [贡献规范](doc/CONTRIBUTING.md)；不要把本地通过当作生产已部署或人工已验收。

## 通用工作约定

1. **先确认现场**：核对 `git status`、实际文件/模块路径、目标分支和最新提交；不覆盖或清理既有未提交改动。历史开发计划、演示数据与生产运行分别核实。
2. **只改任务范围**：遵守现有分层、租户/权限、跨库事务及消息幂等边界；涉及 SQL、演示数据库、外部服务、密钥、真实业务数据时先确认适用环境，未经明确授权不执行写操作。
3. **留可复核证据**：修改后运行相关模块测试与前端类型检查/构建；测试必须有非零用例数，不能把跳过的测试、旧报告或本机数据库验证写成生产验收。报告改动文件、所跑命令、结果和未验证事项。

## 项目概述

Nexus-ERP 是一套面向制造业与贸易型企业的综合管理系统，以 **ERP、MES、IoT、AI** 为核心，扩展了 Flow 流程编排、IM、CV 视觉识别、Station 工位通道、电子签章及工业决策闭环。基于 **RuoYi-Vue-Plus v5.6.0** 二次开发而来，并针对分布式/集群部署进行了大量重写。采用 MIT 许可证。

## 构建与运行

### 后端 (Java / Maven)

- **构建**: `mvn clean package` (默认跳过测试 — 根 pom 中 `skipTests=true`)
- **运行测试**: `mvn test -DskipTests=false -Dprofiles.active=`；根 `pom.xml` 的 Surefire `groups=${profiles.active}` 会按环境 Tag 过滤，必须检查输出的 `Tests run` 大于 0，不能仅凭 `BUILD SUCCESS` 判定已测。限定模块时可使用 `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dprofiles.active=` 等。
- **本地运行**: 先完成模块构建，再在 `ruoyi-admin` 模块运行 `mvn spring-boot:run -P local`（或在 IDE 运行 `com.nexus.NexusApplication`）；不要假定根聚合 POM 直接运行应用。
- **激活的环境**: `local`、`dev`、`prod`（Maven 默认 `dev`；通过 `-P local` / `-P prod` 切换；测试时可显式设置空的 `profiles.active` 以免漏测）。
- 入口点: `ruoyi-admin/src/main/java/com/nexus/NexusApplication.java`（包名 `com.nexus`；勿再引用历史 `DromaraApplication`）。
- Web 服务器: Undertow，默认端口 8080，上下文路径 `/`

### 前端 (plus-ui / Vue 3)

- **开发服务器**: `npm run dev` (端口 80，代理 `/dev-api` → `http://localhost:8080`)
- **生产构建**: `npm run build:prod`
- **代码检查**: `npm run lint:eslint` (使用 `lint:eslint:fix` 自动修复)
- **类型检查/单测**: `npm run type-check`、`npm run test:unit`（在 `plus-ui/` 下执行）。`npm run prettier` 会批量改写全仓前端文件，只对实际修改文件运行定向格式化/检查，避免污染无关改动。
- 要求 Node >= 20.19.0

### 数据库

初始化 SQL 脚本位于 `script/sql/`；按系统库和各业务数据源分别导入对应数据库，具体库名及导入顺序以环境配置、[变更登记](doc/system/数据库增量变更记录.md)为准，切勿把 MES/IoT 表误导入 ERP 库：
- `ry-vue.sql` — 基础系统表 (系统库 `ry-vue`)
- `ry-erp.sql` — ERP 业务表
- `ry-mes.sql` — MES 业务表
- `ry-iot.sql` — IoT 业务表
- `app.sql` — 移动端 App 相关表

> **2026-10-08 用户授权清理**：`script/sql/update/` 下的历史增量及测试数据 SQL 已移除（80 个受跟踪文件、1 个当时未提交文件），现有主安装 SQL 保留不变；删除不代表存量环境已迁移。历史变更见 [数据库变更记录](doc/system/数据库增量变更记录.md)，已跟踪文件可从清理前 Git 历史追溯。未来新增结构仍须同步 `ry-*.sql` 并登记变更；存量库另行核对基线、备份和迁移授权，**绝不**导入含 `DROP TABLE` 的主初始化脚本，也不因本次授权自动删除后续新增迁移。

### Docker 部署 (双机)

仓库提供双机 Docker Compose 编排 (生产环境):
- `docker-compose-main.yml` — 主服务器: 当前镜像为 **`mysql:5.7`**（不是旧指南所写 8.0）、Redis、RabbitMQ、MinIO、Nginx+前端、NexusServer（主服务使用 host 网络）；仅仓库**根目录**编排是现行入口。
- `docker-compose-assist.yml` — 辅助服务器: MonitorAdmin、SnailJobServer；业务应用运行在主服务器。
- 生产部署前核对 `.env` 环境变量、服务地址、凭证与备份；部分配置存在仓库默认值，不得将其视为生产安全值。严禁将密钥、Token、业务备份和真实用户数据复制进 Agent 输出、文档或提交；不自动启停用户服务。

## 架构

### 后端模块结构图

```
ruoyi-admin/              — Spring Boot 入口 (NexusApplication)。组装所有模块。
ruoyi-common/             — 共享库 (当前 31 个子模块；具体依赖以各 pom 为准)。
  ruoyi-common-core/      —    基础层: 领域模型、枚举、异常、校验器、工具类
  ruoyi-common-web/       —    Web 层: 全局异常处理、响应包装、过滤器
  ruoyi-common-satoken/   —    认证 (Sa-Token + JWT)
  ruoyi-common-mybatis/   —    ORM 基础 (MyBatis-Plus，集成多租户、加密、脱敏等插件)
  ruoyi-common-redis/     —    Redis 客户端 (Redisson + Spring Cache 注解)
  ruoyi-common-tenant/    —    多租户插件
  ruoyi-common-sse/       —    用于推送通知的服务器发送事件 (Server-Sent Events)
  ruoyi-common-ai/        —    AI 公共层: 实体类 (AiProvider/AiModel/AiSession/AiMessage)、Mapper、DTO、Service 接口
  ruoyi-common-flow/      —    流程编排公共层: 实体类 (Flow/FlowInstance/FlowNodeRun/FlowVersion)、Mapper、BO/VO、Service
  ruoyi-common-cv/        —    视觉识别客户端 (VisionClient — 封装 Vision 服务: 人脸注册/1:1验证/1:N识别、物料OCR、探活)
  ruoyi-common-oss/       —    对象存储 (Minio / S3 协议)
  ruoyi-common-report/    —    报表公共契约: typed value、provider、租户/业务权限范围及安全 JDBC 查询
  ruoyi-common-flowable/  —    Flowable 工作流引擎集成
  ... 以及更多 (任务、日志、短信、邮件、社交、Excel、加密等)
ruoyi-modules/            — 业务模块 (当前 15 个子模块)
  ruoyi-system/           —    系统管理 (用户、角色、菜单、部门、租户、客户端、字典等)
  ruoyi-erp/              —    ERP 业务 (基础数据、销售、采购、排产、仓储)
  ruoyi-mes/              —    MES 制造执行 (工单、安灯、数据采集、设备、质量、SPC)
  ruoyi-iot/              —    IoT 物联网 (设备管理、MQTT、消息管道、实时数据)
  ruoyi-ai/               —    AI 智能助手 (Agent 编排、Tool、SSE 推送、对话管理)
  ruoyi-flow/             —    流程编排 (节点式流程引擎: AI/逻辑/集成/通知/控制节点，定时触发，SSE 推送)
  ruoyi-im/               —    即时通讯 (私聊、群聊、消息推送)
  ruoyi-flowable/         —    Flowable 工作流 REST API
  ruoyi-generator/        —    代码生成器
  ruoyi-job/              —    分布式任务调度 (SnailJob)
  ruoyi-demo/             —    演示/示例代码
  ruoyi-online/           —    在线低代码表单构建器
  ruoyi-report/           —    自研报表 (DSL v1/v2、TABLE/DOCUMENT/PIVOT、受控查询/打印/仪表盘/订阅、私有运行与资源 ACL)
  ruoyi-pay/              —    支付 (支付订单、发票、对账)
ruoyi-extend/             — 独立服务
  ruoyi-monitor-admin/    —    Spring Boot Admin 监控服务端
  ruoyi-snailjob-server/  —    SnailJob 管理控制台
```

### 分层架构 (每个业务模块)

每个业务模块在 `com.nexus.<module>` 下均遵循相同结构:

```
controller/               — REST 控制器
domain/                   — 实体类 (MyBatis-Plus 模型)
domain/bo/                — 业务对象 (请求 DTO)
domain/vo/                — 视图对象 (响应 DTO)
mapper/                   — MyBatis-Plus 映射器接口
service/                  — 服务接口
service/impl/             — 服务实现
```

入口 `com.nexus.NexusApplication` 的 Spring Boot 默认扫描覆盖 `com.nexus.*` 业务组件；部分 common 模块通过 `AutoConfiguration.imports` 等自动配置注册。新增 Bean 时检查实际包路径、扫描范围及配置注册，不能笼统假定“只靠自动配置”。

### ERP 模块子包

ERP 模块 (`ruoyi-erp`) 按业务领域组织:

| 包 | 领域 | 描述 |
|---------|--------|-------------|
| `bd` | 基础数据 | 物料、BOM、客户、供应商、车间、工位、计量单位、类别 |
| `so` | 销售管理 | 客户订单、订单头/行、全生命周期跟踪 |
| `pu` | 采购管理 | 采购申请、采购订单、到货通知、收货入库、采购退货 |
| `prd` | 生产计划 | 生产计划、工单、报工、物料需求 |
| `sch` | 排产调度 | 班次、班组、工人、考勤、假期、生产调度 |
| `wm` | 仓储管理 | 仓库、库区/储位、库存、收货、发货、到货通知 |
| `cv` | 视觉识别 | 人脸底库管理/1:1验证/1:N识别(CvFaceController)、物料OCR(CvDetectController)、标签拍照解析(CvLabelParseService)、批量注册(CvFaceBatchRegisterService) |

### MES 模块子包

MES 模块 (`ruoyi-mes`) 按制造执行领域组织:

| 包 | 领域 | 描述 |
|---------|--------|-------------|
| `pro` | 生产管理 | 工单(ProWorkOrder)、工序派工(ProDispatch)、流转卡(ProFlowCard)、工序报工、SN管理(ProSn)、批次追踪(ProTraceability)、MBOM(MesMbom)、安灯告警(ProAndon) |
| `dc` | 数据采集 | 采集点配置(DcCollectionPoint)、生产数据记录(DcDataRecord) |
| `eq` | 设备管理 | 设备档案(EqEquipment)、维护保养计划(EqMaintenancePlan)、维修记录(EqRepair) |
| `qc` | 质量管理 | 来料检验(QcIqc)、过程检验(QcIpqc)、出货检验(QcOqc) |
| `spc` | 统计过程控制 | 控制图(SpcControlChart)、数据点(SpcDataPoint) |
| `common` | 公共能力 | ERP桥接(MesErpBridgeService — 获取BOM/工艺路线/物料/车间)、MQ发送/消费(WorkOrderReleaseConsumer) |

MES 与 ERP 通过 `MesErpBridgeService` 交互，依赖 ERP 的基础数据（物料、BOM、车间、工位、计量单位）。

### IoT 模块子包

IoT 模块 (`ruoyi-iot`) 按物联网数据流组织:

| 包 | 领域 | 描述 |
|---------|--------|-------------|
| `device` | 设备管理 | 产品注册(IotProduct)、设备注册(IotDevice)、设备数据(IotDeviceData)，CRUD 接口 |
| `mqtt` | MQTT 接入 | MQTT Broker 配置、设备认证(IotMqttAuthenticator)、鉴权策略(IotMqttAuthorizatorPolicy)、消息拦截(IotMqttInterceptHandler)、设备状态缓存 |
| `pipeline` | 消息管道 | MQTT 消息流消费(IotStreamConsumer)、数据解析(IotPropertyPayload / IotMqttTopicParser / IotDataValueResolver)、告警检测(IotAlertChecker)、告警通知(IotAlertNotifier)、设备最新数据缓存 |
| `realtime` | 实时数据 | SSE 实时推送(IotRealtimeSseManager)，前端可查看设备实时运行状态 |
| `task` | 定时任务 | 设备心跳检测、历史数据清理(IotDataCleanupTask)、数据流裁剪(IotStreamTrimTask) |
| `config` | 配置 | IotConfiguration — 模块级自动配置 |
| `common` | 公共能力 | 设备数据查询服务、MES Andon 服务适配 |

### IM 模块子包

即时通讯模块 (`ruoyi-im`) 按功能组织:

| 包 | 领域 | 描述 |
|---------|--------|-------------|
| `controller` | 接口层 | 私聊/群聊发送(ImChatController)、系统通知(SysUserNotificationController)、App 端接口(AppImController) |
| `domain` | 实体类 | ImMessage(消息)、ImGroup(群组)、ImGroupMember(群成员)、ImFriend(好友)、SysUserNotification(系统通知) |
| `push` | 实时推送 | ImRealtimePush — 消息实时推送，ImEnvelopeHelper — 消息信封封装 |
| `listener` | 事件监听 | UserNotificationEventListener — 系统通知事件监听 |
| `service` | 服务层 | IImChatService — 聊天服务、ISysUserNotificationService — 通知服务 |

### AI 模块子包

AI 模块 (`ruoyi-ai`) 按 LangChain4j Agent 架构组织:

| 包 | 描述 |
|---------|-------------|
| `agent` | Agent 接口定义 (ErpQueryAgent/ErpReportAgent/GraphExtractionAgent/WebSearchAgent)，使用 `@Agent` / `@SystemMessage` 注解声明 |
| `config` | 配置类 (AiProperties — `nexus.ai` 前缀、AiThreadPoolConfig — 异步线程池、AiPromptTemplateInitializer、AiSqlExampleInitializer) |
| `service/decision` | 工业交付风险分析、证据/策略守卫、审批状态机、幂等执行、结构化摘要；入口 `AiDecisionController` (`/ai/decision/**`) |
| `context` | AiRequestContext — 异步线程传播 tenantId/userId (Sa-Token ThreadLocal 不自动传播)；SearchSourceHolder — 搜索源上下文 |
| `controller` | 对话、模型、知识库、MCP、参数配置及 `AiDecisionController` 等 REST 控制器；以该目录实际类为准 |
| `domain/dto` | 共享 DTO (ImageGenerateRequest、McpToolTestResult) |
| `listener` | AiModelChangeListener — 模型变更事件监听 |
| `mcp/service` | MCP 服务层 (McpClientManager、ToolProviderFactory、McpToolServiceImpl、IMcpMarketService) |
| `observability` | 可观测性 (LangChain4j 监听器: ChatModel/EmbeddingModel/Tool/Agent 监听，AgentStreamHelper、OutputChannel) |
| `service/chat` | ChatModelFactory — 根据数据库配置动态构建 ChatModel/StreamingChatModel (带缓存)；PersistentChatMemoryStore；provider/ — 多厂商适配 (OpenAI/DeepSeek/通义千问/智谱/Ollama/MiniMax/MiMo/CustomApi) |
| `service/facade` | AiServiceFacade — 统一入口: 路由对话 → 创建会话 → 构建 SupervisorAgent → 异步执行 → SSE 推送 |
| `service/image` | 图片生成服务 (TongYiWanxImageService) |
| `service/kg` | 知识图谱 (GraphExtractionParser、KnowledgeGraphService、CommunityService、GlobalSearchService、GraphStoreStrategy、LabelPropagationClustering) |
| `service/prompt` | PromptTemplateService — 提示词模板管理 |
| `service/query` | ErpQueryService — ERP 数据源 SQL 执行 (`@DS("erp")`)、SqlSafetyValidator — SQL 安全检查、SqlExampleService、ErpTableSchemaManager |
| `service/rag` | RAG 检索增强 (RetrieverService、DocumentParserService、EmbeddingService、ChunkingService、KnowledgeProcessService、KnowledgeImportService、VectorStoreService、RerankService) |
| `service/sse` | AiSseMessageHelper — SSE 消息编码 (JSON payload: type=AI_CONTENT/AI_THINKING/AI_DONE/AI_ERROR/AI_CHART) |
| `service/token` | AiTokenUsageServiceImpl — Token 用量统计 |
| `tool` | 统一工具包: LangChain4j `@Tool` 实现 (QueryAllTablesTool、QueryTableSchemaTool、ExecuteSqlQueryTool、ErpInventoryTool、ErpProductionTool、ErpPurchaseTool、ErpSalesTool、KnowledgeGraphTool、WebSearchTool、ErpToolHelper) + BuiltinToolProvider 包装器 + BuiltinToolRegistry + AiToolInitializer |

AI 公共模块 (`ruoyi-common-ai`) 存放跨模块共享的实体和接口:

| 包 | 描述 |
|---------|-------------|
| `domain/entity` | AiProvider/AiModel/AiSession/AiMessage、AiConfig 及 AiDecisionRun/AiDecisionEvidence/AiDecisionAction 等共享持久化实体 |
| `decision` | 工业决策 DTO、枚举及 `IndustrialDecisionDataPort` / `DecisionWorkflowPort` 等跨模块契约；数值与业务事实不可由模型自行编造 |
| `mapper` | MyBatis-Plus Mapper 接口 |
| `dto` | AiChatRequest (聊天请求 DTO) |
| `service` | IAiModelService 及实现 (模型配置查询) |

### Flow 模块子包

流程编排模块 (`ruoyi-flow`) 独立于 AI 模块，提供节点式流程编排引擎:

| 包 | 描述 |
|---------|-------------|
| `controller` | FlowController — 流程定义 CRUD、发布/下线、运行/测试、版本管理、模板 (`/ai/flow/**`)；FlowInstanceController — 实例查询/重试 (`/ai/flow/instance/**`) |
| `engine` | FlowEngine — 流程执行引擎 (DAG 拓扑排序、节点调度、异步执行、重试/超时)；FlowStopRegistry — 停止信号；FlowNodeStatus — 节点状态枚举 |
| `expr` | FlowConditionEvaluator — 条件表达式评估；SpelFlowExpressionEvaluator — SpEL 表达式实现 |
| `model` | FlowGraph (DAG 图)、FlowEdgeDef (边定义)、FlowNodeDef (节点定义) |
| `node` | 核心接口: FlowNodeProvider (节点提供者 SPI)、FlowNodeRegistry (节点注册中心)、FlowExecutionContext (执行上下文)、NodeParamSchema (参数 Schema) |
| `node/ai` | AI 节点: LlmNodeProvider (LLM 调用)、AgentNodeProvider (Agent 编排)、RagNodeProvider (RAG 检索)、ErpToolNodeProvider (ERP 工具) |
| `node/industrial` | 工业决策节点（交付上下文、APS/缺料推演、方案排序/建议、审批策略及执行）；受保护图不能由通用画布任意替换 |
| `node/logic` | 逻辑节点: IfNodeProvider (条件分支)、SetNodeProvider (变量赋值)、CodeNodeProvider (代码执行)、SqlNodeProvider (SQL 查询) |
| `node/integration` | 集成节点: HttpNodeProvider (HTTP 请求 + SSRF 防护)、MqSendNodeProvider (MQ 消息发送)、OssNodeProvider (OSS 文件操作) |
| `node/notify` | 通知节点: MailNodeProvider (邮件)、SmsNodeProvider (短信)、ImWebhookNodeProvider (IM Webhook)、NotifyNodeProvider (系统通知) |
| `node/control` | 控制节点: WaitNodeProvider (等待)、MergeNodeProvider (合并)、SplitInBatchesProvider (分批)、AggregateNodeProvider (聚合) |
| `schedule` | FlowScheduleJob — 定时触发流程 (SnailJob) |
| `state` | FlowState — 流程状态枚举；FlowTemplateRenderer — 模板渲染引擎 |
| `template` | FlowTemplateCatalog — 节点模板目录 |

流程公共模块 (`ruoyi-common-flow`) 存放跨模块共享的实体和接口:

| 包 | 描述 |
|---------|-------------|
| `domain/entity` | Flow、FlowInstance、FlowNodeRun、FlowVersion (对应 ai_flow/ai_flow_instance/ai_flow_node_run/ai_flow_version 表) |
| `domain/bo` | FlowBo、FlowInstanceBo、FlowNodeRunBo、FlowVersionBo (请求 DTO) |
| `domain/vo` | FlowVo、FlowInstanceVo、FlowNodeRunVo、FlowVersionVo (响应 DTO) |
| `mapper` | MyBatis-Plus Mapper 接口 (FlowMapper、FlowInstanceMapper、FlowNodeRunMapper、FlowVersionMapper) |
| `service` | IFlowService、IFlowInstanceService、IFlowVersionService 及实现 |

> **注意**: API 路径 `/ai/flow/**` 保持不变，数据库表名 `ai_flow*` 保持不变。`ruoyi-flow` 依赖 `ruoyi-ai` (AI 节点需要 AiRequestContext/ChatModelFactory/RetrieverService 等)。

### Station 工位与电子签章

- `ruoyi-modules/ruoyi-station` 提供 `/mes/station/**` 设备通道（工位过站、SN、安灯、心跳、SOP、上料等）；配套 Electron 客户端在独立仓库，后端提交不等于现场真机验收。设备通道必须保留密钥、租户边界和幂等校验。
- `ruoyi-common/ruoyi-common-sign` 提供 `ESignatureService`、印章/授权及签署留痕；电子签章 P1 代码任务 1–16 已实施，**Task 17 人工端到端验收未完成**，P2 PDF 盖章为独立范围。见 [签章规格](doc/system/specs/2026-09-15-esignature-impl-spec.md)。

### 工业决策跨模块边界

`ruoyi-common/ruoyi-common-ai/src/main/java/com/nexus/common/ai/decision` 存契约，决策实体在同模块 `domain/entity`；`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/ai/adapter` 提供可信 ERP 快照/推演与草稿动作；`ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision` 管状态机/证据/策略；`ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/adapter` 与 `node/industrial` 编排受保护图；PC 位于 `plus-ui/src/views/ai/decision`。模型仅生成定性说明，不得自行计算工业 KPI、构造证据或直接写业务数据；审批/执行受权限、租户、版本及幂等约束。2026-09-28 本机 `plus` 数据库的增量已经执行并有 [本地迁移记录](doc/ai/merge/2026-09-28-plus本地数据库迁移与合并记录.md)，**不能**据此宣称生产部署、全部 40 案例单构建 SLA 或竞赛正式提交已完成。

### 前端结构 (plus-ui)

```
src/api/                  — API 层 (对应后端模块: system, cv, erp/{bd,so,pu,prd,sch,wm}, mes/{pro,dc,eq,qc,spc}, iot/{device,product,realtime}, im/chat, ai/chat, workflow)
src/views/                — 页面组件 (结构对应)
src/views/ai/             —   AI 页面: model/、knowledge/、assistant/、config/、decision/、flow/ 等
src/views/cv/             —   CV 页面: detect/ (物料OCR)、face/ (人脸底库)、face-review/ (FAIL 复查)
src/views/mes/            —   MES 页面: pro/、dc/、eq/、qc/、spc/、md/，以及 station/、dhr/、flash/、sop/ 等
src/views/iot/            —   IoT 页面: device/ (设备管理)、product/ (产品管理)
src/views/im/             —   IM 页面: chat/ (即时通讯)
src/components/ai/        —   对话抽屉及 decision/ 结构化风险、方案、证据、审批组件
src/components/cv/        —   CV 组件: LabelCapture.vue (标签拍照识别组件)
src/router/               — Vue Router 配置 (index.ts)
src/store/modules/        — Pinia 状态库 (app, user, permission, dict, tagsView, settings, notice)
src/components/           — 共享组件
src/layout/               — 布局框架 (侧边栏、顶栏、标签页)
src/utils/                — Axios 拦截器、认证工具等
src/package/              — BPMN 流程设计器 (Flowable 集成)
```

使用 UnoCSS 作为工具样式库，Element Plus 作为 UI 组件库，`vxe-table` 作为数据表格组件。

### 关键技术栈

| 关注点 | 技术 |
|---------|------------|
| 认证 | Sa-Token + JWT (无状态)，密钥: `Authorization` 请求头 |
| ORM | MyBatis-Plus 3.5.16（以根 pom 为准），ID 策略: ASSIGN_ID (雪花算法) |
| 数据库连接池 | HikariCP |
| 缓存 | Redis (通过 Redisson 和 Spring Cache 注解) |
| 多租户 | 自定义 MyBatis-Plus 多租户插件 |
| 多数据源 | dynamic-datasource |
| 分布式锁 | Lock4j (基于 Redisson) |
| 任务调度 | SnailJob |
| 消息队列 | RabbitMQ (`spring-boot-starter-amqp`，队列名统一定义在 `ruoyi-common-mq` 的 `QueueConstants`) |
| MQTT | 内置 MQTT Broker (IoT 设备接入，基于 Vert.x MQTT Server) |
| 工作流 | Flowable 7.0 |
| 对象映射 | MapStruct Plus |
| API 文档 | SpringDoc (基于 javadoc，无需额外注解) |
| 序列化 | Jackson (内部工具使用 fastjson2) |
| Excel | FastExcel (阿里巴巴 EasyExcel 分支) |
| API 加密 | 动态 AES + RSA (每次请求生成唯一 AES 密钥，用 RSA 加密) |
| XSS 防护 | 自定义过滤器 (可配置排除 URL) |
| AI 框架 | LangChain4j 1.22.0 + LangChain4j Agentic 1.22.0-beta32 (Supervisor Agent 编排) |
| AI 模型接入 | OpenAI 兼容接口 (DeepSeek/通义千问/智谱/Ollama)，ChatModelFactory 动态构建+缓存 |
| 视觉识别 | 独立 Vision 服务 (HTTP，`nexus.vision` 配置)，VisionClient 封装人脸/OCR/探活 |
| SSE 推送 | Redis Pub/Sub + Server-Sent Events (AI 回复、IoT 实时数据) |
| 前端技术栈 | Vue 3.5 + Vite 7 + TypeScript + Pinia + Element Plus + UnoCSS |

### 配置注意事项

- `application.yml` 使用 Maven 资源过滤 (`@property@` 语法) 来设置环境相关值。
- 2026-10-16初始化配置：`application-dev.yml`为无个人密码/密文的共享配置，`application-local.yml`导入共享契约但实际profile仍为local；默认只监听回环且关闭可选支付、监控/调度、MQTT、搜索/视觉等。短信厂商额外激活sms，第三方登录额外激活social。真实prod/secret与两个example文件均由admin资源/JAR规则排除；生产按示例准备外部`config/`，根Compose只读挂载，不得因示例变更覆盖现有私有文件。Maven运行目录为仓库根，IDE需同样设置；其他目录使用明确的配置路径。
- 多租户默认开启 (`tenant.enable: true`)，特定表被排除 (例如 `sys_menu`、`sys_tenant`)
- API 加密默认开启 — 如果不需要，必须前后端同时禁用
- 默认通知机制为 SSE 推送；WebSocket 默认禁用
- Flowable 异步执行器和 IDM 引擎已禁用 — 仅运行核心流程引擎
- Logback 配置: `logback-plus.xml`，日志写入 `./logs/` 目录
- 环境敏感项涉及 `application-dev.yml` / `application-prod.yml`、`application-secret.yml` 及 `.env`；不得输出原文、提交新真实密钥或将历史默认值当成安全配置。
- `application-decision-demo.yml` 只用于显式隔离演示环境；既有 `dev`/生产库禁止因演示准备被自动清理或重导。

### AI 模块配置与约束

- AI 配置前缀: `nexus.ai`（避免与 Spring AI 的 `spring.ai` 冲突）；`AiProperties` 当前承载 `allowedTablePrefixes`、`sqlTimeout`、`sqlMaxRows`、向量/搜索/Flow/KG/工业决策参数。默认厂商与模型从 `ai_provider`/`ai_model` 管理，不再由 `defaultProvider/defaultModel` yml 字段决定。
- AI 厂商/模型配置存于系统库 `ai_provider` / `ai_model`；运行参数还涉及 `ai_config`（后台页面管理）与 `nexus.ai` 的配置兜底，运行时通过工厂/服务读取。变更时检查缓存广播、租户权限及旧配置兼容。
- AI 数据查询走 `@DS("erp")` 数据源 (ry-erp 库)，系统配置走默认 master 数据源 (ry-vue 库)
- SQL 执行安全: 仅允许 SELECT，内置危险关键词黑名单、表名前缀白名单 (bd_/so_/pu_/prd_/wm_/sch_)，自动注入 tenant_id 隔离条件
- 异步线程上下文: Sa-Token 的 ThreadLocal 不会传播到异步线程，必须通过 `AiRequestContext.set(tenantId, userId)` 手动传播，任务结束时调用 `AiRequestContext.clear()`
- SSE 推送: AI 回复通过 `AiSseMessageHelper` → `SseMessageUtils.publishMessage` (Redis Pub/Sub 广播) → 前端 SSE 连接接收
- AI 线程池: `AiThreadPoolConfig` 定义的 `aiExecutor` Bean，专用于 AI 异步任务
- AI 提供者、模型、会话、配置及决策持久化实体位于 `ruoyi-common-ai`，不要在业务模块复制并产生不同字段口径。

### IoT 模块配置与约束

- IoT 设备数据接入基于 MQTT 协议，内置 MQTT Broker 配置在 `IotMqttProperties` / `MqttBrokerConfig`
- 设备认证通过 `IotMqttAuthenticator` 实现，鉴权通过 `IotMqttAuthorizatorPolicy` 控制主题订阅权限
- 消息管道解析流程: MQTT 消息 → `IotStreamConsumer` → `IotMqttTopicParser` (解析 Topic) → `IotPropertyPayload` (提取属性) → `IotDataValueResolver` (类型转换) → 数据入库
- 告警检测: `IotAlertChecker` 基于配置规则实时检测异常数据，通过 `IotAlertNotifier` 推送通知
- 实时推送: `IotRealtimeSseManager` 通过 SSE 推送设备最新数据到前端，基于 `IotDeviceLatestCache` 缓存
- 定时任务: 设备心跳检测(`IotDeviceHeartbeatTask`)、历史数据清理(`IotDataCleanupTask`)、数据流裁剪(`IotStreamTrimTask`)

### CV 视觉识别模块配置与约束

- 视觉识别为独立 Vision 服务 (HTTP)，客户端统一走 `ruoyi-common-cv` 的 `VisionClient`（`VisionClientImpl` 用 Hutool Http 调用，需直连内网时用 `Proxy.NO_PROXY` 而非 null）
- 配置前缀 `nexus.vision` (`CvProperties`)：`baseUrl`(默认 `http://localhost:5001`)、`enabled`；客户端通过 `CvAutoConfiguration` 自动装配
- 调用点: 人脸注册/1:1验证/1:N识别(`CvFaceController` `/cv/face`)、物料OCR(`CvDetectController` `/cv`)、标签拍照解析(`CvLabelParseService`)、批次注册(`CvFaceBatchRegisterService`)、考勤人脸打卡验证(`SchAttendanceServiceImpl.asyncFaceVerify`，异步+降级补跑任务 `FaceVerifyBackfillTask`)
- multipart 上传字段名为 `file`，切勿改名为 image 等，否则 HTTP 422
- 视觉服务不可用时调用方需降级（如考勤置 NONE 由补跑任务兜底），不应抛异常阻断主流程
- 前端: `src/views/cv/`(detect/face/face-review)、`src/components/cv/LabelCapture.vue`(标签拍照识别组件，示范接入见 `erp/wm/receive/form.vue`)

### MES 模块配置与约束

- MES 通过 `MesErpBridgeService` 与 `ruoyi-erp` 模块交互，获取 BOM、工艺路线(Routing)、物料、车间、工位等基础数据
- MES 工单下发通过 RabbitMQ 消息队列异步处理 (`WorkOrderReleaseConsumer`，`@RabbitListener` 监听 `QueueConstants.MES_WORKORDER_QUEUE`)
- MES 与 IoT 通过数据采集模块 (`dc`) 对接；`IotDataArchiveTask` 负责拉取并归档 IoT 历史数据（已有逐租户修复），生产定时任务是否实际运行需另验。

### ERP 模块配置与约束

- ERP 同样通过 RabbitMQ 异步消费跨模块回写: `ruoyi-erp/.../mq/consumer/` 下含入库(`StockInConsumer`)、生产倒冲(`ProductionBackflushConsumer`)、采购收货回写(`PurchaseReceiveWritebackConsumer`)、工单完工(`ErpWorkOrderCompletedConsumer`)、IQC 完成(`ErpIqcCompletedConsumer`)、返工入库(`ReworkStockConsumer`)、委外入库(`SubcontractStockConsumer`) 等
- 队列/交换机常量集中在 `com.nexus.common.mq.constant.QueueConstants`，新增 MQ 通道时在此声明

### 国际化 (i18n)

资源文件位于 `ruoyi-admin/src/main/resources/i18n/messages*.properties`。通过请求头进行语言切换。

### 常见陷阱 (Gotchas)

- **雪花 ID 在前端的精度丢失**: ID 策略为 `ASSIGN_ID` (雪花算法)，64 位 long 超出 JS 安全整数会被截断。项目已通过全局 `JacksonConfig` 注册 `BigNumberSerializer` 将 long 序列化为字符串根治此问题 —— 新增返回雪花 ID 的接口无需额外处理，但**切勿**在局部 `ObjectMapper` 或 fastjson2 配置中绕过该序列化器。
- **AI 异步线程的租户上下文丢失**: Sa-Token 的 ThreadLocal 不会传播到 `aiExecutor` 异步线程，导致异步写库时 `tenant_id` 落 0。任何 AI 异步写库都必须通过 `AiRequestContext.set(tenantId, userId)` 手动传播，并在 `TenantHelper.dynamic` 中执行写库，任务结束时调用 `AiRequestContext.clear()`。其他自定义异步线程(如 `@Async`、MQ 消费者)写多租户表时同理。
- **多数据源切换**: AI 数据查询走 `@DS("erp")` (ry-erp 库)，系统配置走默认 master (ry-vue 库)；跨库调用注意 `@DS` 注解必须放在被实际调用的 Service 方法上，同类内部方法调用不会触发 AOP 切换。
- **API 加密默认开启但不替代 HTTPS**: 前后端开关与密钥需一致；调试解密失败先核对配置。仓库可能含演示用密钥/默认值，生产必须替换，绝不在日志、文档、回复中打印私钥、Token 或完整配置。
- **Sa-Token 认证异常被通用处理器抢先捕获**: `NotLoginException`/`NotPermissionException`/`NotRoleException` 均继承自 `RuntimeException`。全局有多个 `@RestControllerAdvice`，其中 `GlobalExceptionHandler` 标了 `@Order(1)` 且含 `@ExceptionHandler(RuntimeException.class)`，若不提升 `SaTokenExceptionHandler` 优先级，认证异常会被它抢先捕获，执行 `R.fail(e.getMessage())` 返回 500 且把 "token 已被冻结：eyJ…" 等 JWT 明文暴露给前端。`SaTokenExceptionHandler` 已加 `@Order(Ordered.HIGHEST_PRECEDENCE)` 确保认证/鉴权异常优先返回 401/403。新增其它 `@RestControllerAdvice` 时，若其处理 `RuntimeException` 子类，务必用 `@Order` 控制优先级，否则同样会被 `GlobalExceptionHandler` 抢先。

### 自研报表模块（2026-10-16）

- 继续使用ruoyi-report；P0/E0/E1原生能力保留，新增E2受控QueryModel目录、E3交叉聚合/同快照钻取、E4私有图片/条码/分组/批次、E5固定仪表盘、E6S3/硬配额/索引/字体共享/公平执行、E7订阅/outbox/接收人私有交付。原积木清理是历史已授权范围，不因扩展重跑。
- 元数据只走master：核心rpt九表加[E2–E7增量](script/sql/update/2026-10-15-report-e2-e7.sql)25表，共34。2026-10-16已按用户明确授权在本机localhost:3306/ry-vue（MySQL 5.7.20-log）无备份执行P0/E0/E1/E2–E7，删除19张旧积木及样例表，登记33条菜单/管理员授权，并通过正常bootstrap初始化14个数据集、17份已发布基础报表；见[本地迁移记录](doc/system/plans/2026-10-16-report-local-migration-verification.md)。其他环境及生产未据此迁移，后续只补实际缺失增量，不重跑P0清理。
- 业务取数走common-report SPI及ERP/MES代码固定字段；QueryModel只能用批准的逻辑目录，规划语义版本进入固定依赖/范围指纹。独立查询账户默认禁用且须验证SELECT-only授权，禁止复用写账户。身份、资源ACL、业务范围和字段权限全部生效。
- schema1/engine1.0.0与schema2/engine2.0.0显式注册；发布不可变、草稿revision、ID/DECIMAL字符串，浏览器不重算业务值。原生菜单根23000，新扩展23030–23090；授权入口支持REPORT/DATASET/QUERY/DASHBOARD/ASSET/BATCH/SUBSCRIPTION。
- LOCAL默认私有./data/report；storage.manifestEnabled默认false以兼容旧结构。S3必须启用清单、专用私有未版本桶/无自动生命周期、同一storageId、独立节点暂存。不得公开URL或预签名交付。删除先提交DELETE_PENDING再物理封存确认释放配额；永久.fences/retired是一次性引用围栏元数据，不可被外部清理。IO不跨持全局配额锁，暂存只由稳定原节点回收。
- 新计划格式2使用按租户checksum共享字体及读取租约/计划引用；格式1仍可读。快照索引校验真实块，S3 Range无需整份物化。字体路径和受控候选仍遵循nexus.report.font-path。
- 订阅调度/真实通知默认false；已有授权outbox/交付维护独立运行。参数在校验前按scheduled Instant/ZoneId冻结，手动occurrence固定全接收人集合；取消不可复活；领取及通用导出实时复核创建者和接收人权限。只使用合成通知测试，不因维护自动创建订阅或真实通知。
- 所有本次报表Java源码使用中文Javadoc，TS/Vue使用中文JSDoc；后续变更遵循相同注释和非零回归要求。详情见[使用指南](doc/system/guides/NexusERP_自研报表模块使用指南.md)、[E2–E7验证报告](doc/system/plans/2026-10-15-system-report-e2-e7-verification-report.md)。
