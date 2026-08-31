# Nexus 平台模板工程（dev 分支）

> **分支说明**：本仓库 `dev` 分支是**平台模板工程**——剥离 ERP / MES / IoT / Station / CV 全部业务域后的可复用平台基座，用于快速初始化新项目。完整业务版请使用 [`plus` 分支](https://gitee.com/helloworld_wh18/nexus-erp)。

基于 **RuoYi-Vue-Plus v5.6.0**（Spring Boot 3.5 + Vue 3）二次开发，保留并增强了以下平台能力：**系统管理、AI 智能助手、AI 流程编排、工作流引擎、即时通讯、支付、积木报表、任务调度、代码生成**。特别地，通过集成 LangChain4j Agent 架构，提供自然语言驱动的智能对话、知识库问答与业务辅助决策能力，让新项目开箱即拥有 AI 能力。

- **作者**：王欢
- **许可证**：MIT

---

## 平台能力

### AI 智能助手（AI）

集成 LangChain4j + Agent 架构，提供自然语言驱动的智能对话与业务辅助决策能力：

- **多模型支持**：通过数据库配置厂商（DeepSeek / 通义千问 / 智谱 / Anthropic / Gemini / Ollama / MiniMax / MiMo / 自定义 API 等）和模型，运行时按协议（OpenAI 兼容 / Anthropic Messages / Gemini / OpenAI Responses）动态路由构建 ChatModel，支持同步和流式两种模式。
- **模型管理**：模型能力（输入/输出模态）、上下文窗口、协议、向量维度等参数化管理；草稿模型真实连接测试（`ai:model:test`）；默认模型按能力（TEXT/VISION）智能选择。
- **SSE 实时推送**：AI 回复通过 Redis Pub/Sub + SSE 推送到前端，支持思考中/内容/完成/错误/图表/图片/工具调用/联网搜索来源等多种消息类型。
- **深度思考模式**：支持 reasoning 模型思维链流式推送与持久化，前端以"思考过程"折叠展示。
- **联网搜索**：Tavily / Bing 双引擎，回答强制引用来源 URL，搜索结果以来源卡片推送到前端。
- **知识图谱**：LLM 实体/关系抽取 + 标签传播社区发现，构建知识库图结构；图增强检索（1-2 跳邻居 + 社区全局搜索），ECharts 力导向图可视化，存储支持 MySQL / Neo4j。
- **MCP 市场与工具注册表**：ai_tool 统一注册内置/本地/远程工具，市场发现远程工具一键加载，支持 HTTP（SSE）/ STDIO 双传输与熔断降级；新业务模块的工具通过 `BuiltinToolProvider` 接口自动注册。
- **RAG 知识库**：文档解析（Tika）、分块、向量化检索（混合检索 + RRF 融合）、专用 Rerank API（百炼/智谱/硅基流动）、Embedding 工厂；支持企业内部文档智能问答与 URL 导入；向量索引安全重建（独立线程池、进度跟踪、失败可重试，`ai:knowledge:rebuild`）。
- **文生图**：通义万相文生图，生成图片自动上传 OSS 并推送卡片。
- **对话管理**：消息反馈、会话重命名、会话 Markdown 导出、Token 用量统计、业务上下文注入。
- **Prompt 模板与 Text2SQL 示例**：动态模板注入、Few-Shot 示例库。
- **A/B 测试**：AI 回复效果对比实验，支撑模型与 Prompt 迭代优化。
- **多租户隔离**：异步线程通过 AiRequestContext 传播 tenantId/userId，确保 AI 操作遵守租户隔离规则。

### AI 流程编排（AI Flow Orchestration）

可视化流程编排引擎，支持通过拖拽画布设计、执行和监控业务自动化流程。基于自定义 DAG 图模型引擎（非 Flowable BPMN）：

- **可视化画布设计器**：基于 Vue Flow 的拖拽式画布，节点面板（4 分类，15+ 节点类型）、schema 驱动属性表单、变量选择器（NDV）、框选/复制粘贴/撤销重做/自动布局
- **AI 智能节点**：LLM 调用（prompt 模板 + {{}} 变量注入）、Agent 智能体（多轮工具调用）、RAG 知识库检索
- **数据处理节点**：代码转换（JS 表达式字段转换）、变量设置（引用上游节点输出或运行入参）
- **流程控制节点**：条件分支（IF/ELSE，AND/OR 多条件）、循环分批（Split → Loop → Aggregate）、合并汇总、延迟等待
- **集成连接节点**：HTTP 请求、MQ 消息发送（RabbitMQ）、邮件、短信、IM 群机器人（企业微信/钉钉）、OSS 文件
- **通知输出节点**：站内通知（顶部铃铛 + 实时推送）
- **DAG 执行引擎**：条件分支分流、ERROR_PORT 错误处理端口、并行分支、执行级续跑（从失败节点重试）
- **触发方式**：手动触发、定时调度（SnailJob Cron）、Webhook、设计器测试运行
- **运行监控**：运行实例列表、只读画布重放（逐节点时间线动画）、节点运行详情、执行快照持久化
- **流程管理**：版本管理（Draft → Published → Offline）、执行历史、内置种子流程模板

### 系统与支撑能力

- **系统管理（System）**：用户、角色、菜单、部门、岗位、租户、字典、参数、通知公告、操作日志、登录日志、文件 OSS 等完整后台能力，多租户体系默认开启。
- **工作流引擎（Flowable）**：集成 Flowable 7.0，支持复杂审批流程、转办、委派、加减签、会签、或签、票签等场景。
- **即时通讯（IM）**：私聊、群聊、消息推送，与系统通知联动。
- **支付管理（Pay）**：支付宝 / 微信 / 收钱吧等渠道接入，统一支付请求、回调处理、退款管理、对账。
- **积木报表（JimuReport）**：在线设计报表模板、拖拽式配置和数据可视化。
- **代码生成（Generator）**：读表生成前后端代码，新业务模块开发效率工具。
- **任务调度（SnailJob）**：分布式任务调度，支持 Cron、重试、分片。
- **在线表单（Online）**：低代码表单构建器。
- **消息队列（RabbitMQ）**：通用可靠投递框架（重试、补偿、死信），业务模块按需接入。

---

## 系统截图

| 登录页 | 首页 |
|--------|------|
| ![登录页](doc/system/screenshots/登录页.png) | ![首页](doc/system/screenshots/首页.png) |

| AI 智能对话 | AI 知识图谱 |
|-------------|-------------|
| ![智能对话](doc/system/screenshots/智能对话.png) | ![知识图谱](doc/system/screenshots/知识图谱.png) |

| 用户管理 | 代码生成 | 工作流设计 |
|----------|----------|------------|
| ![用户管理](doc/system/screenshots/用户管理.png) | ![代码生成](doc/system/screenshots/代码生成.png) | ![工作流设计](doc/system/screenshots/工作流设计.png) |

| 工作流审批 | 积木报表 | 即时通讯 |
|------------|----------|----------|
| ![工作流审批](doc/system/screenshots/工作流审批.png) | ![报表填写](doc/system/screenshots/报表填写.png) | ![即时通讯](doc/system/screenshots/即时通讯.png) |

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5 + Maven（JDK 17） |
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
| SSE 推送 | Redis Pub/Sub + Server-Sent Events |
| 接口文档 | SpringDoc（基于 Javadoc） |

---

## 项目结构

```
nexus-erp/
├── ruoyi-admin/              # Spring Boot 入口 (NexusApplication)
├── ruoyi-common/             # 共享库（核心、Web、认证、ORM、Redis、多租户、AI、SSE、工作流、MQ 等 24 个子模块）
├── ruoyi-modules/            # 功能模块（平台能力层）
│   ├── ruoyi-system/         #   系统管理（用户、角色、菜单、部门、租户、字典等）
│   ├── ruoyi-ai/             #   AI 智能助手（Agent、Tool、MCP 市场、知识图谱、SSE 推送、知识库）
│   ├── ruoyi-flow/           #   AI 流程编排（可视化 DAG：LLM/Agent/RAG/HTTP/MQ/通知等节点）
│   ├── ruoyi-im/             #   即时通讯（私聊、群聊、消息推送）
│   ├── ruoyi-pay/            #   支付管理
│   ├── ruoyi-flowable/       #   Flowable 工作流 REST API
│   ├── ruoyi-generator/      #   代码生成器
│   ├── ruoyi-job/            #   分布式任务调度
│   ├── ruoyi-online/         #   在线低代码表单
│   ├── ruoyi-report/         #   积木报表
│   └── ruoyi-demo/           #   框架使用示例（新模块开发参考）
├── ruoyi-extend/             # 扩展服务（监控中心、SnailJob 控制台）
├── plus-ui/                  # 前端项目（Vue 3 + TS）
└── script/sql/               # 数据库初始化脚本
```

---

## 用此模板开发新项目

1. **初始化仓库**：以 dev 分支为起点克隆/复制，改 `pom.xml` 的 `artifactId` 与 `name` 为新项目名。
2. **初始化数据库**：执行 `script/sql/ry-vue.sql`（框架库，含系统管理与平台模块的表/菜单/权限）；如需小程序/App 客户端再执行 `script/sql/app.sql`。
3. **新建业务模块**：参照 `ruoyi-modules/ruoyi-demo` 的结构与 `CLAUDE.md` 的分层架构说明（controller / domain/bo / domain/vo / mapper / service），在 `ruoyi-modules/pom.xml` 与 `ruoyi-admin/pom.xml` 注册新模块。
4. **业务菜单与权限**：通过代码生成器或手工编写增量 SQL（放 `script/sql/update/`），只追加新业务菜单，不改动框架菜单。
5. **可选接入**：
   - AI 工具：实现 `BuiltinToolProvider` 接口即可把业务工具注册进 AI 助手与流程编排。
   - 消息队列：队列常量与声明按 `ruoyi-common-mq` 的模板方式在业务模块内自行声明。
   - 移动端：配套 [Nexus-App](https://gitee.com/helloworld_wh18/nexus-app)（uni-app，多端适配）可对接本后端。

> **与 plus 分支的关系**：dev 与 plus 无共同 git 历史，**不要 `git merge plus`**。plus 上的平台级改进（框架修复、AI/工作流/IM 等模块）通过 `git fetch origin plus` 后按需 `git cherry-pick` 同步。

---

## 快速开始

### 环境要求

- JDK >= 17
- Maven >= 3.8
- Node >= 20.19.0
- MySQL >= 8.0
- Redis >= 6
- RabbitMQ（可选，用于消息可靠投递：重试、补偿、死信）
- Milvus（可选，AI 知识库向量存储；未部署时可用 inmemory 模式演示）

### 后端启动

```bash
# 克隆项目（dev 分支）
git clone -b dev https://gitee.com/helloworld_wh18/nexus-erp.git
cd nexus-erp

# 初始化数据库：执行 script/sql/ry-vue.sql
# 可选：script/sql/app.sql（小程序/App 客户端配置）
# 敏感配置：参照 application-dev.yml 中的提示补齐 application-secret.yml

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

### 打包

```bash
# 后端
mvn clean package -D maven.test.skip=true -P prod

# 前端
cd plus-ui && npm run build:prod
```

### Docker 部署

仓库提供双机 Docker Compose 编排（生产参考）：复制 `.env.example` 为 `.env` 填入真实密码后使用 `docker-compose-main.yml`（主服务）与 `docker-compose-assist.yml`（SnailJob 控制台）部署。

---

## License

MIT License。保留原始 RuoYi-Vue-Plus 开源协议文件即可。
