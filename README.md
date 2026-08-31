# Nexus-ERP

Nexus-ERP 是一套面向制造业与贸易型企业的综合管理系统，涵盖 **ERP（企业资源计划）、MES（制造执行）、IoT（物联网数据采集）、AI 智能助手、视觉识别（OCR 人脸/物料）** 五大核心能力，基于 **RuoYi-Vue-Plus v5.6.0** 二次开发而来。围绕"基础数据、销售、采购、生产、仓储、质量、设备、IoT、财务、报表"构建企业业务中台，并在核心业务之上深度融合 **AI 智能助手** 与 **视觉识别能力**，目标是把分散的业务流程连接成可追踪、可协同、可落地、可智能决策的管理闭环。

项目早期基于 RuoYi-Vue 进行扩展，后升级到 **RuoYi-Vue-Plus 分布式多租户架构**，在保留成熟后台能力的基础上，更适合多组织、多角色、多业务线的企业场景。特别地，通过集成 LangChain4j Agent 架构，实现了自然语言驱动的 ERP 数据查询、智能对话与业务辅助决策能力。

- **作者**：王欢
- **许可证**：MIT

### 关联开源项目

| 项目                       | 说明                                | 地址 |
|--------------------------|-----------------------------------|------|
| **Nexus-ERP**            | 综合管理系统主仓库（本仓库，Web 端）              | [Gitee](https://gitee.com/NexusERP/nexus-erp)（分支 `plus` 为业务版，`dev` 为平台模板版） |
| **Nexus-Vision-Service** | 独立视觉识别服务（人脸识别 + OCR 物料识别，FastAPI） | [Gitee](https://gitee.com/NexusERP/nexus-vision-service) |
| **Nexus-App**            | 移动端 App（uni-app，支持人脸打卡、标签拍照识别等）   | [Gitee](https://gitee.com/NexusERP/nexus-app) |
| **Nexus-Station**        | 工位机 App（Electron，支持车间操作台登录、数据采集等）   | [Gitee](https://gitee.com/NexusERP/nexus-station) |

> 协作方式：Web / App / Station 通过 Nexus-ERP 后端 `/cv/*` 接口代理调用 Nexus-Vision-Service，实现人脸识别、物料标签 OCR 等视觉能力（详见下文"视觉识别（CV）"章节）。

---

## 项目定位

Nexus-ERP 不只是一个通用后台，而是一个以企业经营流程为核心的业务系统。当前重点聚焦于以下几个方向：

- **打通业务闭环**：从订单、采购、生产到仓储出入库，减少业务断点；并向上延伸至财务核算（总账、应收应付、固定资产、存货成本），打通"业务 → 财务"的完整链路。
- **车间执行管控（MES）**：覆盖工单下发、工序派工、工序报工、生产流转卡、进站出站、SN/批次追踪、安灯告警、SPC 统计、质量检验（IQC/IPQC/OQC）、不合格品（NCR）与返工、E-SOP、工装管理等制造执行核心场景，实现计划层与执行层的无缝衔接。
- **设备互联与数据采集（IoT）**：通过 MQTT 网关对接产线设备、传感器、PLC 等，实现设备状态实时采集、数据流管道处理、告警检测与推送、OEE 分析，为 MES 提供实时数据支撑。
- **统一主数据**：围绕物料、客户、供应商、BOM、工位、仓库等核心主数据建立统一管理体系。
- **AI 智能决策**：基于 LangChain4j Agent 架构，支持自然语言查询 ERP 数据、智能对话、知识图谱、联网搜索、报表生成、异常预警与业务辅助决策，让系统从"记录工具"升级为"智能决策平台"。
- **视觉识别落地**：独立视觉服务（Nexus-Vision-Service）提供人脸识别（考勤打卡、车间操作台登录）与物料标签 OCR 识别（收货上架自动回填），软硬件结合贴近车间实际场景。
- **专属大模型**：基于Qwen系列开源模型微调，创建NexusAI大模型，提升智能助手的回复质量与业务理解能力。
- **即时通讯**：内置 IM 模块，支持私聊、群聊、消息推送，打通企业内部沟通链路。
- **移动端 App**：配套 Nexus-App（uni-app），覆盖 Android / iOS / H5 / 微信小程序，支持人脸打卡、标签拍照识别、扫码作业等移动场景。
- **工位机 App**：配套 Nexus-Station（Electron），覆盖车间操作台登录、数据采集等场景。

---

## 演示地址

- 演示站点：http://nexuserp.website/index
- 账号：admin
- 密码：admin123

> 提示：演示环境仅用于体验功能，建议不要在其中录入真实业务数据。
> 演示服务器能力有限，部分功能可能无法正常使用（minio、定时任务、系统监控服务等等可能未启动），请本地部署后运行查看功能。</br>
> 需要使用演示系统，但系统处于崩溃状态，请私信通知我重启。 </br>
> NexusAI大模型部署服务器内存与网络环境限制，生成速度较慢，可能连接中断，仅做演示，需要使用功能请配置自己大模型，使用完成后删除自己的key，谨防泄露，本系统不承担任何责任。

---

## 业务功能

### AI 智能助手（AI）

集成 LangChain4j + Agent 架构，提供自然语言驱动的数据查询、智能对话与业务辅助决策能力：

- **多模型支持**：通过数据库配置厂商（DeepSeek / 通义千问 / 智谱 / Ollama / MiniMax / MiMo / 自定义 API 等）和模型，运行时动态构建 ChatModel，支持同步和流式两种模式。
- **数据查询 Agent**：基于 Supervisor + SubAgent 编排，Agent 自动调用工具链（查询表列表 → 查看表结构 → 生成并执行 SQL）回答业务问题。
- **安全防护**：SQL 执行仅允许 SELECT，内置危险关键词黑名单、表名前缀白名单（可配置），自动注入租户隔离条件，行数限制与超时控制。
- **SSE 实时推送**：AI 回复通过 Redis Pub/Sub + SSE 推送到前端，支持思考中/内容/完成/错误/图表/图片/工具调用/联网搜索来源等多种消息类型。
- **深度思考模式**：支持 reasoning 模型思维链流式推送（AI_THINKING_TOKEN）与持久化，前端以"思考过程"折叠展示。
- **联网搜索**：Tavily / Bing 双引擎，回答强制引用来源 URL；搜索结果以"信息来源"来源卡片（AI_WEB_SEARCH 事件）推送到前端。
- **知识图谱**：LLM 实体/关系抽取 + 标签传播社区发现，构建知识库图结构；图增强检索（1-2 跳邻居 + 社区全局搜索），ECharts 力导向图可视化，存储支持 MySQL / Neo4j。
- **MCP 市场与工具注册表**：ai_tool 统一注册内置/本地/远程工具，市场发现远程工具一键加载，支持 HTTP（SSE）/ STDIO 双传输与熔断降级。
- **RAG 知识库**：文档解析（Tika）、分块、向量化检索（混合检索 + RRF 融合）、专用 Rerank API（百炼/智谱/硅基流动）、Embedding 工厂；支持企业内部文档（业务文档、规范手册等）智能问答与 URL 导入。
- **文生图**：通义万相文生图，生成图片自动上传 OSS 并推送卡片（AI_IMAGE 事件）。
- **对话管理**：单条消息删除、消息反馈（点赞/点踩）、会话重命名、会话 Markdown 导出、Token 用量统计、业务上下文注入（移动端详情页可携带当前业务对象上下文，让 AI 知道用户正在看什么）。
- **Prompt 模板与 Text2SQL 示例**：动态模板注入、Few-Shot 示例库，持续提升 SQL 生成准确率。
- **A/B 测试**：AI 回复效果对比实验，支撑模型与 Prompt 迭代优化。
- **前端对话抽屉**：全局 AiChatDrawer 组件，支持 Markdown 渲染、会话管理、聊天历史，可在任意页面唤起。
- **多租户隔离**：异步线程通过 AiRequestContext 传播 tenantId/userId，确保数据查询遵守租户隔离规则。
- **专属大模型**：基于Qwen系列开源模型微调，创建NexusAI大模型，提升智能助手的回复质量与业务理解能力。
<br></br>
  ![NexusAI](doc/system/screenshots/NexusAI.png)

### AI 流程编排（AI Flow Orchestration）

可视化流程编排引擎，支持通过拖拽画布设计、执行和监控复杂的业务自动化流程。基于自定义 DAG 图模型引擎（非 Flowable BPMN），提供丰富的节点类型、灵活的触发方式和完整的运行监控能力：

- **可视化画布设计器**：基于 Vue Flow 的拖拽式画布，包含节点面板（5 分类，17+ 节点类型）、schema 驱动属性表单、变量选择器（NDV）、框选/复制粘贴/撤销重做/自动布局，支持常驻运行面板和宽松表达式求值
- **AI 智能节点**：LLM 调用（prompt 模板 + {{}} 变量注入）、Agent 智能体（多轮工具调用）、RAG 知识库检索
- **数据处理节点**：代码转换（JS 表达式字段转换）、变量设置（引用上游节点输出或运行入参）
- **流程控制节点**：条件分支（IF/ELSE，AND/OR 多条件）、循环分批（Split 数组分批 → Loop 循环体 → Aggregate 汇总 → Done 结束）、合并汇总（动态多入端口）、延迟等待
- **集成连接节点**：HTTP 请求（GET/POST/PUT/DELETE，支持 Header/Body/超时）、MQ 消息发送（RabbitMQ 路由键选择）、邮件发送（HTML 正文）、短信发送（模板参数）、IM 群机器人（企业微信/钉钉）、OSS 文件（上传/读取）
- **通知输出节点**：站内通知（顶部铃铛 + 实时推送，可跳转流程详情）
- **DAG 执行引擎**：自定义图模型引擎，支持条件分支分流、ERROR_PORT 错误处理端口、Split/Aggregate 循环执行、并行分支、执行级续跑（从失败节点重试）、SKIPPED 状态入度簿记
- **触发方式**：手动触发（一键运行 + 入参输入）、定时调度（SnailJob Cron 表达式）、Webhook 触发（入站 HTTP 请求）、设计器测试运行（无需发布，使用画布当前配置）
- **运行监控**：运行实例列表（状态/耗时/节点数/触发方式）、只读画布重放（逐节点时间线动画 + JSON 表格视图）、节点运行详情（输入/输出/错误信息/循环批次）、执行快照持久化（续跑用）
- **流程管理**：版本管理（Draft → Published → Offline 生命周期，每次发布版本+1）、版本历史查看、执行历史筛选、内置种子流程模板（库存预警通知、金额分级处理、HTTP 集成测试、SQL 查询示例等）
- **表达式与变量系统**：SpEL 表达式求值、{{}} 变量模板（引用上游节点输出或运行入参）、NDV 节点数据查看器，支持变量选择器自动补全

<br></br>
  ![AI 流程编排](doc/system/screenshots/AI流程编排.png)

### 移动端 App（Nexus-App）

配套移动端应用 [Nexus-App](https://gitee.com/helloworld_wh18/nexus-app)（uni-app，Vue 3 + Pinia），一套代码覆盖 Android / iOS / H5 / 微信小程序，通过主仓库后端接口联动：

- **人脸打卡**：移动端刷脸考勤，人脸验证通过后自动记录打卡。
- **标签拍照识别**：物料标签拍照 → OCR 识别料号/批次/SN，支持与单据行校验（验证模式）。
- **扫码作业**：仓库扫码、条码扫描等移动作业场景。
- 视觉调用统一走后端 `/cv/*` 代理，App 端不持有视觉服务密钥。

### 平台支撑能力

#### 工作流引擎（Flowable）

集成 Flowable 7.0 工作流引擎，支持复杂审批流程、转办、委派、加减签、会签、或签、票签等场景，可与各业务模块深度联动。

#### 即时通讯（IM）

即时消息模块，支持私聊、群聊、消息推送，与系统通知联动，提供企业内部沟通能力。

#### 积木报表（JimuReport）

内置积木报表引擎，支持在线设计报表模板、拖拽式报表配置和数据可视化展示，降低业务报表开发门槛。

#### 支付管理（Pay）

集成支付网关，支持多支付渠道接入，提供统一支付请求、回调处理、退款管理、账单对账等功能。

---

## 系统截图

### 登录与首页

| 登录页 | 首页 |
|--------|------|
| ![登录页](doc/system/screenshots/登录页.png) | ![首页](doc/system/screenshots/首页.png) |

### 核心亮点

| 3D 仓库可视化 | AI 知识图谱 |
|---------------|-------------|
| ![3D仓库可视化](doc/system/screenshots/3D仓库动图.gif) | ![AI知识图谱](doc/system/screenshots/知识图谱动图.gif) |

### 系统管理

| 用户管理 | 代码生成 |
|----------|----------|
| ![用户管理](doc/system/screenshots/用户管理.png) | ![代码生成](doc/system/screenshots/代码生成.png) |

### AI 智能助手

| 智能对话 | 知识图谱 |
|----------|----------|
| ![智能对话](doc/system/screenshots/智能对话.png) | ![知识图谱](doc/system/screenshots/知识图谱.png) |

### 工作流引擎

| 工作流设计 | 工作流审批 |
|------------|------------|
| ![工作流设计](doc/system/screenshots/工作流设计.png) | ![工作流审批](doc/system/screenshots/工作流审批.png) |

### 积木报表

| 报表填写 |
|----------|
| ![报表填写](doc/system/screenshots/报表填写.png) |

### 支付管理

| 支付管理 |
|----------|
| ![支付管理](doc/system/screenshots/支付管理.png) |

### 即时通讯

| 即时通讯 |
|----------|
| ![即时通讯](doc/system/screenshots/即时通讯.png) |

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5 + Maven |
| 前端框架 | Vue 3 + TypeScript + Vite 7 + Element Plus + Pinia |
| 权限认证 | Sa-Token + JWT 无状态认证 |
| ORM | MyBatis-Plus 3.5.x（多租户、分页、数据权限、加密、脱敏） |
| 数据库 | MySQL（主）+ Oracle / PostgreSQL / SQLServer（可选） |
| 缓存 | Redis（Redisson + Spring Cache） |
| 消息队列 | RabbitMQ（通用消息可靠投递：重试、补偿、死信） |
| 工作流 | Flowable 7.0 |
| 报表 | 积木报表（JimuReport） |
| 任务调度 | SnailJob |
| 文件存储 | Minio（S3 协议） |
| AI 框架 | LangChain4j 1.18.0 + LangChain4j Agentic 1.18.0-beta28（Supervisor Agent、MCP、知识图谱） |
| 移动端 | Nexus-App（uni-app：Vue 3 + Pinia，Android / iOS / H5 / 微信小程序） |
| SSE 推送 | Redis Pub/Sub + Server-Sent Events |
| 接口文档 | SpringDoc（基于 Javadoc） |

---

## 项目结构

```
nexus-erp/
├── ruoyi-admin/              # Spring Boot 入口 (DromaraApplication)
├── ruoyi-common/             # 共享库 (核心、Web、认证、ORM、Redis、多租户、AI、SSE、工作流、MQ 等)
├── ruoyi-modules/            # 功能模块（平台能力层；业务模块按需扩展）
│   ├── ruoyi-system/         #   系统管理（用户、角色、菜单、部门、租户、字典等）
│   ├── ruoyi-ai/             #   AI 智能助手（Agent、Tool、MCP 市场、知识图谱、SSE 推送、对话管理、知识库）
│   ├── ruoyi-flow/           #   AI 流程编排（可视化 DAG：LLM/Agent/RAG/HTTP/MQ/通知等节点）
│   ├── ruoyi-im/             #   即时通讯（私聊、群聊、消息推送）
│   ├── ruoyi-pay/            #   支付管理
│   ├── ruoyi-flowable/       #   Flowable 工作流 REST API
│   ├── ruoyi-generator/      #   代码生成器
│   ├── ruoyi-job/            #   分布式任务调度
│   └── ruoyi-demo/           #   框架使用示例
├── ruoyi-extend/             # 扩展服务（监控中心、SnailJob 控制台）
├── plus-ui/                  # 前端项目（Vue 3 + TS）
└── script/sql/               # 数据库初始化脚本
```

**兄弟项目**（独立仓库，与本仓库联动部署）：

```
nexus-vision-service/     # 视觉识别服务（Python FastAPI：人脸识别 + OCR + YOLO，单容器部署）
nexus-app/                # 移动端 App（uni-app：人脸打卡、标签拍照识别、扫码作业）
```

---

## AI 架构亮点

Nexus 平台的 AI 能力不只是"加一个聊天窗口"，而是从底层架构上实现了 AI 与业务数据深度融合的可扩展架构：

```
┌─────────────────────────────────────────────────────┐
│  前端: AiChatDrawer (Vue 3 + Markdown + SSE)        │
│  - 全局唤起，Markdown 渲染，会话管理                  │
└──────────────────────┬──────────────────────────────┘
                       │ SSE 流式推送
                       ▼
┌─────────────────────────────────────────────────────┐
│  后端: AiServiceFacade (Supervisor Agent 编排)       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  DataAgent   │  │ KnowledgeAgent│  │  ...       │ │
│  │ (按需扩展)    │  │ (知识问答)     │  │ (可插拔)    │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘ │
│         │               │                │          │
│  ┌──────▼───────────────▼────────────────▼──────┐  │
│  │          Tool Chain (LangChain4j)             │  │
│  │  WebSearch → RAG 检索 → 业务工具（按需注册）   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**核心特性**：
- **动态模型管理**：厂商和模型配置存储在数据库，ChatModelFactory 运行时构建 + 缓存，无需重启。
- **多租户安全**：SQL 自动注入 tenant_id 条件，仅允许白名单表前缀查询，异步线程传播租户上下文。
- **流式体验**：SSE 推送思考过程 + 内容 + 完成信号，前端实时渲染。
- **知识图谱**：LLM 抽取实体关系构建知识图谱，图增强检索支撑深度问答。
- **联网搜索**：Tavily / Bing 双引擎 + 来源卡片，回答可溯源。
- **MCP 工具生态**：统一工具注册表 + MCP 市场，远程工具一键接入。
- **可扩展 Agent**：基于 LangChain4j Agentic 的 Supervisor 架构，可轻松新增垂直场景 Agent（数据查询 / 联网搜索 / 知识图谱等）。

---

## 快速开始

### 环境要求

- JDK >= 17
- Maven >= 3.8
- Node >= 20.19.0
- MySQL >= 8.0
- Redis >= 6
- RabbitMQ（可选，用于消息可靠投递：重试、补偿、死信）

### 后端启动

```bash
# 克隆项目
git clone https://gitee.com/helloworld_wh18/nexus-erp.git
cd nexus-erp

# 执行初始化 SQL
# 1. script/sql/ry-vue.sql（框架库：系统管理 + 平台模块）
# 2. script/sql/app.sql（可选，小程序/App 客户端配置）
# 3. script/sql/update/*.sql（按需应用框架增量）

# 启动后端
mvn spring-boot:run -P dev
```

### 前端启动

```bash
cd plus-ui
npm install
npm run dev
```

前端开发服务器默认监听 `http://localhost:80`，通过 `/dev-api` 代理到后端 `http://localhost:8080`。

### 移动端 App 启动（可选）

```bash
git clone https://gitee.com/helloworld_wh18/nexus-app.git
cd nexus-app
npm install          # 或使用 HBuilderX 打开运行
```

App 请求后端接口（打卡、拍照识别、扫码等），需将请求地址指向本仓库后端服务。

### 后端打包

```bash
mvn clean package -D maven.test.skip=true -P prod
```

### 前端打包

```bash
cd plus-ui
npm run build:prod
```

---

---

## License

MIT License。保留原始 RuoYi-Vue-Plus 开源协议文件即可。
