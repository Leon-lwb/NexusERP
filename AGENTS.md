# AGENTS.md

本文件为 Codex (Codex.ai/code) 在本仓库中工作时提供指导。

## 项目概述

Nexus 平台模板工程（dev 分支）是剥离 ERP/MES/IoT 业务模块后的可复用平台基座：保留系统管理、AI 智能助手、AI 流程编排、IM、支付、报表、工作流等平台能力，基于 **RuoYi-Vue-Plus v5.6.0** 二次开发而来，并针对分布式/集群部署进行了大量重写。采用 MIT 许可证。新业务域按模块方式以 `ruoyi-demo` 为模板扩展。

## 构建与运行

### 后端 (Java / Maven)

- **构建**: `mvn clean package` (默认跳过测试 — 根 pom 中 `skipTests=true`)
- **运行测试**: `mvn test -DskipTests=false`
- **本地运行**: `mvn spring-boot:run -P local` (或者将 `ruoyi-admin` 模块导入 IDE 并运行 `DromaraApplication`)
- **激活的环境**: `local`、`dev`、`prod` (通过 `<profiles.active>` 属性设置)
- 入口点: `ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java`
- Web 服务器: Undertow，默认端口 8080，上下文路径 `/`

### 前端 (plus-ui / Vue 3)

- **开发服务器**: `npm run dev` (端口 80，代理 `/dev-api` → `http://localhost:8080`)
- **生产构建**: `npm run build:prod`
- **代码检查**: `npm run lint:eslint` (使用 `lint:eslint:fix` 自动修复)
- **代码格式化**: `npm run prettier`
- 要求 Node >= 20.19.0

### 数据库

初始化 SQL 脚本位于 `script/sql/` 目录 (按顺序导入 `ry-vue` 系统库):
- `ry-vue.sql` — 框架库 (系统管理 + 平台模块表/菜单/权限)
- `app.sql` — 移动端 App 客户端配置 (可选)
- `update/` — 框架增量升级脚本 (按需应用)

### Docker 部署 (双机)

仓库提供双机 Docker Compose 编排 (生产环境):
- `docker-compose-main.yml` — 主服务器: MySQL 8.0、Redis、RabbitMQ、MinIO、Nginx+前端、NexusServer (均为 `network_mode: host`)
- `docker-compose-assist.yml` — 辅助服务器: 应用服务、监控等
- 凭证与主机 IP 内嵌于文件中，部署前需核对替换

## 架构

### 后端模块结构图

```
ruoyi-admin/              — Spring Boot 入口 (DromaraApplication)。组装所有模块。
ruoyi-common/             — 共享库 (28 个子模块)。每个均为可自动配置的独立 jar。
  ruoyi-common-core/      —    基础层: 领域模型、枚举、异常、校验器、工具类
  ruoyi-common-web/       —    Web 层: 全局异常处理、响应包装、过滤器
  ruoyi-common-satoken/   —    认证 (Sa-Token + JWT)
  ruoyi-common-mybatis/   —    ORM 基础 (MyBatis-Plus，集成多租户、加密、脱敏等插件)
  ruoyi-common-redis/     —    Redis 客户端 (Redisson + Spring Cache 注解)
  ruoyi-common-tenant/    —    多租户插件
  ruoyi-common-sse/       —    用于推送通知的服务器发送事件 (Server-Sent Events)
  ruoyi-common-ai/        —    AI 公共层: 实体类 (AiProvider/AiModel/AiSession/AiMessage)、Mapper、DTO、Service 接口
  ruoyi-common-flow/      —    流程编排公共层: 实体类 (Flow/FlowInstance/FlowNodeRun/FlowVersion)、Mapper、BO/VO、Service
  ruoyi-common-oss/       —    对象存储 (Minio / S3 协议)
  ruoyi-common-flowable/  —    Flowable 工作流引擎集成
  ... 以及更多 (任务、日志、短信、邮件、社交、Excel、加密等)
ruoyi-modules/            — 业务模块
  ruoyi-system/           —    系统管理 (用户、角色、菜单、部门、租户、客户端、字典等)
  ruoyi-ai/               —    AI 智能助手 (Agent 编排、Tool、SSE 推送、对话管理)
  ruoyi-flow/             —    流程编排 (节点式流程引擎: AI/逻辑/集成/通知/控制节点，定时触发，SSE 推送)
  ruoyi-im/               —    即时通讯 (私聊、群聊、消息推送)
  ruoyi-flowable/         —    Flowable 工作流 REST API
  ruoyi-generator/        —    代码生成器
  ruoyi-job/              —    分布式任务调度 (SnailJob)
  ruoyi-demo/             —    演示/示例代码
  ruoyi-online/           —    在线低代码表单构建器
  ruoyi-report/           —    报表 (JimuReport 积木报表，独立数据源 + Token 鉴权)
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

模块通过 Spring Boot 自动配置 (`spring.factories` / `AutoConfiguration.imports`) 进行注册，而非组件扫描。

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
| `context` | AiRequestContext — 异步线程传播 tenantId/userId (Sa-Token ThreadLocal 不自动传播)；SearchSourceHolder — 搜索源上下文 |
| `controller` | 所有 REST 控制器 (AiChatController、AiModelController、AiProviderController、AiKnowledgeController、AiKnowledgeGraphController、AiPromptTemplateController、AiSqlExampleController、AiTokenUsageController、AiImageController、AiAbTestController、McpMarketController、McpToolController) |
| `domain/dto` | 共享 DTO (ImageGenerateRequest、McpToolTestResult) |
| `listener` | AiModelChangeListener — 模型变更事件监听 |
| `mcp/service` | MCP 服务层 (McpClientManager、ToolProviderFactory、McpToolServiceImpl、IMcpMarketService) |
| `observability` | 可观测性 (LangChain4j 监听器: ChatModel/EmbeddingModel/Tool/Agent 监听，AgentStreamHelper、OutputChannel) |
| `service/chat` | ChatModelFactory — 根据数据库配置动态构建 ChatModel/StreamingChatModel (带缓存)；PersistentChatMemoryStore；provider/ — 多厂商适配 (OpenAI/DeepSeek/通义千问/智谱/Ollama/MiniMax/MiMo/CustomApi) |
| `service/facade` | AiServiceFacade — 统一入口: 路由对话 → 创建会话 → 构建 SupervisorAgent → 异步执行 → SSE 推送 |
| `service/image` | 图片生成服务 (TongYiWanxImageService) |
| `service/kg` | 知识图谱 (GraphExtractionParser、KnowledgeGraphService、CommunityService、GlobalSearchService、GraphStoreStrategy、LabelPropagationClustering) |
| `service/prompt` | PromptTemplateService — 提示词模板管理 |
| `service/rag` | RAG 检索增强 (RetrieverService、DocumentParserService、EmbeddingService、ChunkingService、KnowledgeProcessService、KnowledgeImportService、VectorStoreService、RerankService) |
| `service/sse` | AiSseMessageHelper — SSE 消息编码 (JSON payload: type=AI_CONTENT/AI_THINKING/AI_DONE/AI_ERROR/AI_CHART) |
| `service/token` | AiTokenUsageServiceImpl — Token 用量统计 |
| `tool` | 统一工具包: LangChain4j `@Tool` 实现 (KnowledgeGraphTool、WebSearchTool) + BuiltinToolProvider 包装器 + BuiltinToolRegistry + AiToolInitializer |

AI 公共模块 (`ruoyi-common-ai`) 存放跨模块共享的实体和接口:

| 包 | 描述 |
|---------|-------------|
| `domain/entity` | AiProvider、AiModel、AiSession、AiMessage (对应 ai_provider/ai_model/ai_session/ai_message 表) |
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

### 前端结构 (plus-ui)

```
src/api/                  — API 层 (对应后端模块: system, im/chat, ai/chat, workflow 等)
src/views/                — 页面组件 (结构对应)
src/views/ai/             —   AI 页面: model/ (厂商/模型配置)、knowledge/ (知识库)、assistant/ (助手)
src/views/im/             —   IM 页面: chat/ (即时通讯)
src/components/ai/        —   AI 组件: AiChatDrawer.vue (全局对话抽屉，Markdown 渲染，SSE 消息接收)
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
| ORM | MyBatis-Plus 3.5.x，ID 策略: ASSIGN_ID (雪花算法) |
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
| AI 框架 | LangChain4j 1.18.0 + LangChain4j Agentic 1.18.0-beta28 (Supervisor Agent 编排) |
| AI 模型接入 | OpenAI 兼容接口 (DeepSeek/通义千问/智谱/Ollama)，ChatModelFactory 动态构建+缓存 |
| SSE 推送 | Redis Pub/Sub + Server-Sent Events (AI 回复等实时推送) |
| 前端技术栈 | Vue 3.5 + Vite 7 + TypeScript + Pinia + Element Plus + UnoCSS |

### 配置注意事项

- `application.yml` 使用 Maven 资源过滤 (`@property@` 语法) 来设置环境相关值
- 多租户默认开启 (`tenant.enable: true`)，特定表被排除 (例如 `sys_menu`、`sys_tenant`)
- API 加密默认开启 — 如果不需要，必须前后端同时禁用
- 默认通知机制为 SSE 推送；WebSocket 默认禁用
- Flowable 异步执行器和 IDM 引擎已禁用 — 仅运行核心流程引擎
- Logback 配置: `logback-plus.xml`，日志写入 `./logs/` 目录
- 敏感凭证位于 `application-dev.yml` / `application-prod.yml` 中 (不在基础 `application.yml` 中)
- `application-dev.yml`中的一些支付宝支付配置、微信小程序配置等在`application-secret.yml`中，需要自行更换配置

### AI 模块配置与约束

- AI 配置前缀: `nexus.ai` (避免与 Spring AI 的 `spring.ai` 冲突)，关键属性: `defaultProvider`、`defaultModel`、`allowedTablePrefixes`、`sqlTimeout`、`sqlMaxRows`
- AI 厂商和模型配置存储在 `ry-vue` 库的 `ai_provider` / `ai_model` 表中，运行时由 ChatModelFactory 动态构建并缓存
- AI 数据查询走默认 master 数据源（ry-vue 库）
- SQL 执行安全: 仅允许 SELECT，内置危险关键词黑名单、表名前缀白名单 (可配置 `nexus.ai.allowed-table-prefixes`)，自动注入 tenant_id 隔离条件
- 异步线程上下文: Sa-Token 的 ThreadLocal 不会传播到异步线程，必须通过 `AiRequestContext.set(tenantId, userId)` 手动传播，任务结束时调用 `AiRequestContext.clear()`
- SSE 推送: AI 回复通过 `AiSseMessageHelper` → `SseMessageUtils.publishMessage` (Redis Pub/Sub 广播) → 前端 SSE 连接接收
- AI 线程池: `AiThreadPoolConfig` 定义的 `aiExecutor` Bean，专用于 AI 异步任务
- AI 实体类 (AiProvider/AiModel/AiSession/AiMessage) 在 `ruoyi-common-ai` 中，不在 `ruoyi-ai` 中，以便跨模块引用

### 国际化 (i18n)

资源文件位于 `ruoyi-admin/src/main/resources/i18n/messages*.properties`。通过请求头进行语言切换。

### 常见陷阱 (Gotchas)

- **雪花 ID 在前端的精度丢失**: ID 策略为 `ASSIGN_ID` (雪花算法)，64 位 long 超出 JS 安全整数会被截断。项目已通过全局 `JacksonConfig` 注册 `BigNumberSerializer` 将 long 序列化为字符串根治此问题 —— 新增返回雪花 ID 的接口无需额外处理，但**切勿**在局部 `ObjectMapper` 或 fastjson2 配置中绕过该序列化器。
- **AI 异步线程的租户上下文丢失**: Sa-Token 的 ThreadLocal 不会传播到 `aiExecutor` 异步线程，导致异步写库时 `tenant_id` 落 0。任何 AI 异步写库都必须通过 `AiRequestContext.set(tenantId, userId)` 手动传播，并在 `TenantHelper.dynamic` 中执行写库，任务结束时调用 `AiRequestContext.clear()`。其他自定义异步线程(如 `@Async`、MQ 消费者)写多租户表时同理。
- **API 加密默认开启**: 前后端必须同时启用或同时禁用，否则请求无法解密。本地调试若遇到 400/解密失败，先确认两端 `api-decrypt` 配置一致。
- **Sa-Token 认证异常被通用处理器抢先捕获**: `NotLoginException`/`NotPermissionException`/`NotRoleException` 均继承自 `RuntimeException`。全局有多个 `@RestControllerAdvice`，其中 `GlobalExceptionHandler` 标了 `@Order(1)` 且含 `@ExceptionHandler(RuntimeException.class)`，若不提升 `SaTokenExceptionHandler` 优先级，认证异常会被它抢先捕获，执行 `R.fail(e.getMessage())` 返回 500 且把 "token 已被冻结：eyJ…" 等 JWT 明文暴露给前端。`SaTokenExceptionHandler` 已加 `@Order(Ordered.HIGHEST_PRECEDENCE)` 确保认证/鉴权异常优先返回 401/403。新增其它 `@RestControllerAdvice` 时，若其处理 `RuntimeException` 子类，务必用 `@Order` 控制优先级，否则同样会被 `GlobalExceptionHandler` 抢先。
