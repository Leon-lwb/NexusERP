# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

Nexus-ERP 是一套面向制造业与贸易型企业的综合管理系统，涵盖 **ERP（企业资源计划）、MES（制造执行）、IoT（物联网数据采集）、AI 智能助手** 四大核心能力。基于 **RuoYi-Vue-Plus v5.6.0** 二次开发而来，并针对分布式/集群部署进行了大量重写。采用 MIT 许可证。

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

初始化 SQL 脚本位于 `script/sql/` 目录:
- `ry_vue.sql` — 基础系统表
- `ry-erp.sql` — ERP 业务表
- 同时包含 Oracle、PostgreSQL 和 SQLServer 版本

## 架构

### 后端模块结构图

```
ruoyi-admin/              — Spring Boot 入口 (DromaraApplication)。组装所有模块。
ruoyi-common/             — 共享库 (27 个子模块)。每个均为可自动配置的独立 jar。
  ruoyi-common-core/      —    基础层: 领域模型、枚举、异常、校验器、工具类
  ruoyi-common-web/       —    Web 层: 全局异常处理、响应包装、过滤器
  ruoyi-common-satoken/   —    认证 (Sa-Token + JWT)
  ruoyi-common-mybatis/   —    ORM 基础 (MyBatis-Plus，集成多租户、加密、脱敏等插件)
  ruoyi-common-redis/     —    Redis 客户端 (Redisson + Spring Cache 注解)
  ruoyi-common-tenant/    —    多租户插件
  ruoyi-common-sse/       —    用于推送通知的服务器发送事件 (Server-Sent Events)
  ruoyi-common-ai/        —    AI 公共层: 实体类 (AiProvider/AiModel/AiSession/AiMessage)、Mapper、DTO、Service 接口
  ruoyi-common-oss/       —    对象存储 (Minio / S3 协议)
  ruoyi-common-flowable/  —    Flowable 工作流引擎集成
  ... 以及更多 (任务、日志、短信、邮件、社交、Excel、加密等)
ruoyi-modules/            — 业务模块
  ruoyi-system/           —    系统管理 (用户、角色、菜单、部门、租户、客户端、字典等)
  ruoyi-erp/              —    ERP 业务 (基础数据、销售、采购、排产、仓储)
  ruoyi-mes/              —    MES 制造执行 (工单、安灯、数据采集、设备、质量、SPC)
  ruoyi-iot/              —    IoT 物联网 (设备管理、MQTT、消息管道、实时数据)
  ruoyi-ai/               —    AI 智能助手 (Agent 编排、Tool、SSE 推送、对话管理)
  ruoyi-im/               —    即时通讯 (私聊、群聊、消息推送)
  ruoyi-flowable/         —    Flowable 工作流 REST API
  ruoyi-generator/        —    代码生成器
  ruoyi-job/              —    分布式任务调度 (SnailJob)
  ruoyi-demo/             —    演示/示例代码
  ruoyi-online/           —    在线低代码表单构建器
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
| `agent` | Agent 接口定义 (ErpQueryAgent)，使用 `@Agent` / `@SystemMessage` 注解声明 |
| `config` | 配置类 (AiProperties — `nexus.ai` 前缀、AiThreadPoolConfig — 异步线程池) |
| `context` | AiRequestContext — 异步线程传播 tenantId/userId (Sa-Token ThreadLocal 不自动传播) |
| `controller` | AiChatController — REST 接口 (`/ai/chat/send`, `/ai/chat/history/{id}`, `/ai/chat/sessions`) |
| `service/chat` | ChatModelFactory — 根据数据库配置动态构建 ChatModel/StreamingChatModel (带缓存) |
| `service/facade` | AiServiceFacade — 统一入口: 路由对话 → 创建会话 → 构建 SupervisorAgent → 异步执行 → SSE 推送 |
| `service/query` | ErpQueryService — ERP 数据源 SQL 执行 (`@DS("erp")` 切换到 ry-erp 库) |
| `service/sse` | AiSseMessageHelper — SSE 消息编码 (JSON payload: type=AI_CONTENT/AI_THINKING/AI_DONE/AI_ERROR/AI_CHART) |
| `tool` | LangChain4j Tool 实现: QueryAllTablesTool、QueryTableSchemaTool、ExecuteSqlQueryTool |

AI 公共模块 (`ruoyi-common-ai`) 存放跨模块共享的实体和接口:

| 包 | 描述 |
|---------|-------------|
| `domain/entity` | AiProvider、AiModel、AiSession、AiMessage (对应 ai_provider/ai_model/ai_session/ai_message 表) |
| `mapper` | MyBatis-Plus Mapper 接口 |
| `dto` | AiChatRequest (聊天请求 DTO) |
| `service` | IAiModelService 及实现 (模型配置查询) |

### 前端结构 (plus-ui)

```
src/api/                  — API 层 (对应后端模块: system, erp/{bd,so,pu,prd,sch,wm}, mes/{pro,dc,eq,qc,spc}, iot/{device,product,realtime}, im/chat, ai/chat, workflow)
src/views/                — 页面组件 (结构对应)
src/views/ai/             —   AI 页面: model/ (厂商/模型配置)、knowledge/ (知识库)、assistant/ (助手)
src/views/mes/            —   MES 页面: pro/ (工单)、dc/ (数据采集)、eq/ (设备)、qc/ (质量)、spc/ (SPC)、md/ (MBOM)
src/views/iot/            —   IoT 页面: device/ (设备管理)、product/ (产品管理)
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
| 消息队列 | RocketMQ (MES 工单下发、数据采集消息) |
| MQTT | 内置 MQTT Broker (IoT 设备接入，基于 Vert.x MQTT Server) |
| 工作流 | Flowable 7.0 |
| 对象映射 | MapStruct Plus |
| API 文档 | SpringDoc (基于 javadoc，无需额外注解) |
| 序列化 | Jackson (内部工具使用 fastjson2) |
| Excel | FastExcel (阿里巴巴 EasyExcel 分支) |
| API 加密 | 动态 AES + RSA (每次请求生成唯一 AES 密钥，用 RSA 加密) |
| XSS 防护 | 自定义过滤器 (可配置排除 URL) |
| AI 框架 | LangChain4j 1.13.0 + LangChain4j Agentic 1.13.0-beta23 (Supervisor Agent 编排) |
| AI 模型接入 | OpenAI 兼容接口 (DeepSeek/通义千问/智谱/Ollama)，ChatModelFactory 动态构建+缓存 |
| SSE 推送 | Redis Pub/Sub + Server-Sent Events (AI 回复、IoT 实时数据) |
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
- AI 数据查询走 `@DS("erp")` 数据源 (ry-erp 库)，系统配置走默认 master 数据源 (ry-vue 库)
- SQL 执行安全: 仅允许 SELECT，内置危险关键词黑名单、表名前缀白名单 (bd_/so_/pu_/prd_/wm_/sch_)，自动注入 tenant_id 隔离条件
- 异步线程上下文: Sa-Token 的 ThreadLocal 不会传播到异步线程，必须通过 `AiRequestContext.set(tenantId, userId)` 手动传播，任务结束时调用 `AiRequestContext.clear()`
- SSE 推送: AI 回复通过 `AiSseMessageHelper` → `SseMessageUtils.publishMessage` (Redis Pub/Sub 广播) → 前端 SSE 连接接收
- AI 线程池: `AiThreadPoolConfig` 定义的 `aiExecutor` Bean，专用于 AI 异步任务
- AI 实体类 (AiProvider/AiModel/AiSession/AiMessage) 在 `ruoyi-common-ai` 中，不在 `ruoyi-ai` 中，以便跨模块引用

### IoT 模块配置与约束

- IoT 设备数据接入基于 MQTT 协议，内置 MQTT Broker 配置在 `IotMqttProperties` / `MqttBrokerConfig`
- 设备认证通过 `IotMqttAuthenticator` 实现，鉴权通过 `IotMqttAuthorizatorPolicy` 控制主题订阅权限
- 消息管道解析流程: MQTT 消息 → `IotStreamConsumer` → `IotMqttTopicParser` (解析 Topic) → `IotPropertyPayload` (提取属性) → `IotDataValueResolver` (类型转换) → 数据入库
- 告警检测: `IotAlertChecker` 基于配置规则实时检测异常数据，通过 `IotAlertNotifier` 推送通知
- 实时推送: `IotRealtimeSseManager` 通过 SSE 推送设备最新数据到前端，基于 `IotDeviceLatestCache` 缓存
- 定时任务: 设备心跳检测(`IotDeviceHeartbeatTask`)、历史数据清理(`IotDataCleanupTask`)、数据流裁剪(`IotStreamTrimTask`)

### MES 模块配置与约束

- MES 通过 `MesErpBridgeService` 与 `ruoyi-erp` 模块交互，获取 BOM、工艺路线(Routing)、物料、车间、工位等基础数据
- MES 工单下发通过 RocketMQ 消息队列异步处理 (`WorkOrderReleaseConsumer`)
- MES 与 IoT 通过数据采集模块(`dc`)对接，IoT 采集的数据可回写到 MES 生产数据记录中

### 国际化 (i18n)

资源文件位于 `ruoyi-admin/src/main/resources/i18n/messages*.properties`。通过请求头进行语言切换。
