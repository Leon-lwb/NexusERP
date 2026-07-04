# Nexus-ERP

Nexus-ERP 是一套面向制造业与贸易型企业的综合管理系统，涵盖 **ERP（企业资源计划）、MES（制造执行）、IoT（物联网数据采集）、AI 智能助手** 四大核心能力，基于 **RuoYi-Vue-Plus v5.6.0** 二次开发而来。围绕"基础数据、销售、采购、生产、仓储、质量、设备、IoT、报表"构建企业业务中台，并在核心业务之上深度融合 **AI 智能助手**，目标是把分散的业务流程连接成可追踪、可协同、可落地、可智能决策的管理闭环。

项目早期基于 RuoYi-Vue 进行扩展，后升级到 **RuoYi-Vue-Plus 分布式多租户架构**，在保留成熟后台能力的基础上，更适合多组织、多角色、多业务线的企业场景。特别地，通过集成 LangChain4j Agent 架构，实现了自然语言驱动的 ERP 数据查询、智能对话与业务辅助决策能力。

- **作者**：王欢
- **仓库地址**：[Gitee](https://gitee.com/helloworld_wh18/nexus-erp/tree/plus/)
- **许可证**：MIT

---

## 项目定位

Nexus-ERP 不只是一个通用后台，而是一个以企业经营流程为核心的业务系统。当前重点聚焦于以下几个方向：

- **打通业务闭环**：从订单、采购、生产到仓储出入库，减少业务断点。
- **车间执行管控（MES）**：覆盖工单下发、工序派工、工序报工、生产流转卡、SN 追踪、安灯告警、SPC 统计、质量检验（IQC/PQC/OQC）、设备管理等制造执行核心场景，实现计划层与执行层的无缝衔接。
- **设备互联与数据采集（IoT）**：通过 MQTT 网关对接产线设备、传感器、PLC 等，实现设备状态实时采集、数据流管道处理、告警检测与推送、OEE 分析，为 MES 提供实时数据支撑。
- **统一主数据**：围绕物料、客户、供应商、BOM、工位、仓库等核心主数据建立统一管理体系。
- **AI 智能决策**：基于 LangChain4j Agent 架构，支持自然语言查询 ERP 数据、智能对话、报表生成、异常预警与业务辅助决策，让系统从"记录工具"升级为"智能决策平台"。
- **专属大模型**：基于Qwen系列开源模型微调，创建NexusAI大模型，提升智能助手的回复质量与业务理解能力。
- **即时通讯**：内置 IM 模块，支持私聊、群聊、消息推送，打通企业内部沟通链路。

---

## 演示地址

- 演示站点：http://nexuserp.website/index
- 账号：admin
- 密码：admin123

> 提示：演示环境仅用于体验功能，建议不要在其中录入真实业务数据。
> 演示服务器能力有限，部分功能可能无法正常使用（minio、定时任务、系统监控服务等等可能未启动），请本地部署后运行查看功能。</br>
> 需要使用演示系统，但系统崩了的话请私信通知我重启。 </br>
> NexusAI大模型部署服务器内存与网络环境限制，生成速度较慢，可能连接中断，仅做演示。

---

## 业务功能

### AI 智能助手（AI）

集成 LangChain4j + Agent 架构，提供自然语言驱动的 ERP 数据查询与智能对话能力：

- **多模型支持**：通过数据库配置厂商（DeepSeek / 通义千问 / 智谱 / Ollama）和模型，运行时动态构建 ChatModel，支持同步和流式两种模式。
- **ERP 数据查询 Agent**：基于 Supervisor + SubAgent 编排，Agent 自动调用工具链（查询表列表 → 查看表结构 → 生成并执行 SQL）回答业务问题。
- **安全防护**：SQL 执行仅允许 SELECT，内置危险关键词黑名单、表名前缀白名单（bd_/so_/pu_/prd_/wm_/sch_），自动注入租户隔离条件，行数限制与超时控制。
- **SSE 实时推送**：AI 回复通过 Redis Pub/Sub + SSE 推送到前端，支持思考中/内容/完成/错误/图表等多种消息类型。
- **前端对话抽屉**：全局 AiChatDrawer 组件，支持 Markdown 渲染、会话管理、聊天历史，可在任意页面唤起。
- **多租户隔离**：异步线程通过 AiRequestContext 传播 tenantId/userId，确保数据查询遵守租户隔离规则。
- **业务场景 Agent**：内置 ERP 数据查询工具链，可进一步扩展报表生成、异常预警、智能推荐等垂直场景 Agent。
- **RAG 知识库**：支持企业内部文档（SOP、工艺文件、设备手册）向量化检索，实现基于知识库的智能问答。
- **专属大模型**：基于Qwen系列开源模型微调，创建NexusAI大模型，提升智能助手的回复质量与业务理解能力。
<br></br>
  ![NexusAI](doc/system/screenshots/NexusAI.png)

### ERP 业务管理

统一管理企业核心业务数据与流程，涵盖销售、采购、仓储等传统 ERP 模块：

- **基础数据**：物料主数据、物料清单（BOM）、客户/供应商档案、车间、工位、计量单位、物料分类。
- **销售**：客户订单、订单头/行管理与全生命周期跟踪。
- **采购**：采购申请、采购订单、到货通知、收货入库、采购退货与供应商评估，打通"请购 → 采购 → 收货 → 入库"的供应链主链路。
- **排产调度**：班次与班组管理、工人档案、考勤记录、假期管理、生产调度排程。
- **仓储**：仓库定义、库区/储位管理、库存查询与盘点、收货与发货管理、到货通知、条码管理与库存追溯。

### MES 制造执行

独立的一级业务模块（`ruoyi-mes`），实现计划层到执行层的制造过程管控：

- **生产管理（pro）**：工单下发、工序派工、工序报工、生产流转卡、SN 号管理、批次追踪、MBOM（制造 BOM）管理。
- **安灯管理**：产线安灯（Andon）告警触发与处理，异常事件实时上报。
- **数据采集（dc）**：采集点配置、生产数据记录与回写，对接 IoT 实时数据。
- **设备管理（eq）**：设备档案、维护保养计划、维修记录、故障管理，并与 ERP 基础数据（物料、车间、工位）打通。
- **质量管理（qc）**：来料检验（IQC）、过程检验（PQC）、出货检验（OQC），不合格品处理与追溯。
- **统计过程控制（spc）**：控制图、数据点采集与分析，基于 SPC 方法监控生产过程稳定性。
- **ERP 桥接**：通过 MesErpBridgeService 与 `ruoyi-erp` 模块交互，获取 BOM、工艺路线、物料、车间等基础数据。

### IoT 物联网数据采集

独立的一级业务模块（`ruoyi-iot`），负责设备数据接入、处理与实时推送：

- **设备管理**：产品（Product）与设备（Device）注册管理，设备类型定义、数据上报模板配置。
- **MQTT 接入**：内置 MQTT Broker，支持设备认证、鉴权、主题订阅，设备通过 MQTT 协议上报数据。
- **消息管道（Pipeline）**：MQTT 消息解析（Topic 解析、属性 Payload 提取、数值类型转换）、数据流消费、告警检测（IotAlertChecker）与通知推送。
- **实时数据**：设备最新数据缓存、SSE 实时推送，前端可查看设备实时运行状态。
- **定时任务**：设备心跳检测、历史数据清理、数据流裁剪，保障系统运行稳定性。

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

### 系统管理

| 用户管理 | 代码生成 |
|----------|----------|
| ![用户管理](doc/system/screenshots/用户管理.png) | ![代码生成](doc/system/screenshots/代码生成.png) |

### 基础数据

| 物料管理 |
|----------|
| ![物料管理](doc/system/screenshots/物料管理.png) |

### 生产管理

| 生产计划 |
|----------|
| ![生产计划](doc/system/screenshots/生产计划.png) |

### 销售管理

| 销售订单 |
|----------|
| ![销售订单](doc/system/screenshots/销售订单.png) |

### 采购管理

| 采购订单 |
|----------|
| ![采购订单](doc/system/screenshots/采购订单.png) |

### 排产调度

| 排班计划 | 考勤记录 |
|----------|----------|
| ![排班计划](doc/system/screenshots/排班计划.png) | ![考勤记录](doc/system/screenshots/考勤记录.png) |

### 仓储管理

| 物料入库 | 库存清单 | 库存预警 |
|----------|----------|----------|
| ![物料入库](doc/system/screenshots/物料入库.png) | ![库存清单](doc/system/screenshots/库存清单.png) | ![库存预警](doc/system/screenshots/库存预警.png) |

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

### AI 智能助手

| 智能对话 |
|----------|
| ![智能对话](doc/system/screenshots/智能对话.png) |

### 设备与 IoT 管理

| 设备管理 | IoT 监控 |
|----------|----------|
| ![设备管理](doc/system/screenshots/设备管理.png) | ![IoT监控](doc/system/screenshots/IoT监控.png) |

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
| 消息队列 | RocketMQ（工单下发、数据采集） |
| MQTT | 内置 MQTT Broker（IoT 设备接入） |
| 工作流 | Flowable 7.0 |
| 报表 | 积木报表（JimuReport） |
| 任务调度 | SnailJob |
| 文件存储 | Minio（S3 协议） |
| AI 框架 | LangChain4j 1.13.0 + LangChain4j Agentic（Supervisor Agent） |
| SSE 推送 | Redis Pub/Sub + Server-Sent Events |
| 接口文档 | SpringDoc（基于 Javadoc） |

---

## 项目结构

```
nexus-erp/
├── ruoyi-admin/              # Spring Boot 入口 (DromaraApplication)
├── ruoyi-common/             # 共享库 (核心、Web、认证、ORM、Redis、多租户、AI、SSE、工作流等)
├── ruoyi-modules/            # 业务模块
│   ├── ruoyi-system/         #   系统管理（用户、角色、菜单、部门、租户、字典等）
│   ├── ruoyi-erp/            #   ERP 业务（基础数据、销售、采购、排产、仓储）
│   ├── ruoyi-mes/            #   MES 制造执行（工单、安灯、数据采集、设备、质量、SPC）
│   ├── ruoyi-iot/            #   IoT 物联网（设备管理、MQTT、消息管道、实时数据）
│   ├── ruoyi-ai/             #   AI 智能助手（Agent、Tool、SSE 推送、对话管理、知识库）
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

---

## AI 架构亮点

Nexus-ERP 的 AI 能力不只是"加一个聊天窗口"，而是从底层架构上实现了 AI 与业务数据的深度融合：

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
│  │ ErpQueryAgent│  │  ReportAgent │  │ Future...  │ │
│  │ (SQL查询)    │  │ (报表生成)    │  │ (智能推荐)  │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘ │
│         │               │                │          │
│  ┌──────▼───────────────▼────────────────▼──────┐  │
│  │          Tool Chain (LangChain4j)             │  │
│  │  QueryAllTables → QuerySchema → ExecuteSQL   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**核心特性**：
- **动态模型管理**：厂商和模型配置存储在数据库，ChatModelFactory 运行时构建 + 缓存，无需重启。
- **多租户安全**：SQL 自动注入 tenant_id 条件，仅允许白名单表前缀查询，异步线程传播租户上下文。
- **流式体验**：SSE 推送思考过程 + 内容 + 完成信号，前端实时渲染。
- **可扩展 Agent**：基于 LangChain4j Agentic 的 Supervisor 架构，可轻松新增垂直场景 Agent。

---

## 快速开始

### 环境要求

- JDK >= 17
- Maven >= 3.8
- Node >= 20.19.0
- MySQL >= 8.0
- Redis >= 6
- RocketMQ（可选，用于 MES 工单下发和数据采集消息）

### 后端启动

```bash
# 克隆项目
git clone https://gitee.com/helloworld_wh18/nexus-erp.git
cd nexus-erp

# 执行初始化 SQL（按顺序）
# 1. script/sql/ry_vue.sql
# 2. script/sql/ry-erp.sql
# 3. script/sql/ry-mes.sql
# 4. script/sql/ry-iot.sql
# 5. script/sql/app.sql(可选，添加app配置SQL)

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

## 未来期望

Nexus-ERP 的长期目标不只是"能用"，而是逐步成长为一个具备完整业务闭环和可扩展能力的企业级平台。

### 短期目标

- 补齐现有模块的关键业务流程，让销售、生产、仓储、基础数据形成稳定闭环。
- 完善工单状态流转、库存调拨、盘点、报价、退货等高频业务能力。
- 提升页面交互与业务联动体验，让核心单据真正可落地使用。
- 增强 AI 智能助手能力：支持流式 Token 推送、RAG 知识库检索、更多业务场景 Agent（如报表生成、异常预警）。

### 中期目标

- 完善 MES 模块：工序派工、Andon 安灯、SN 追踪、SPC 分析、IQC/PQC/OQC 全流程。
- 完善 IoT 模块：设备注册、MQTT 接入、数据管道、实时监控与告警闭环。
- 引入更多策略化能力，如库存估值、推荐上架/拣货、安全库存预警等。
- 构建 AI Agent 编排体系，实现多 Agent 协作处理复杂业务场景（如自动生成采购建议、库存异常诊断）。

### 长期目标

- 构建财务基础能力，逐步扩展到应收、应付、成本核算与经营分析。
- 建立更完善的报表与 BI 能力，支持管理层从数据视角查看经营状态。
- 持续增强工作流和自动化能力，让业务系统从"记录工具"升级为"流程驱动平台"。
- 沉淀出更适合中小制造企业的 ERP 实践方案，形成可复用、可演进的行业模板。
- 打造 AI 驱动的智能 ERP 平台，实现自然语言交互式业务操作、智能决策辅助与自动化流程执行。

---

## License

MIT License。保留原始 RuoYi-Vue-Plus 开源协议文件即可。
