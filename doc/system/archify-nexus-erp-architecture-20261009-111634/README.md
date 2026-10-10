# Nexus-ERP 系统总体架构图 — 四端重绘版

日期：2026-10-09（Asia/Shanghai）。类型：Archify `architecture`，中文逻辑总览，默认静态。

本版落实已确认的设计：PC、APP、PDA、Station 四类客户端各自独立；MQTT 工业设备与工位外设另列；保留 Milvus，并区分客户端 `nexus-station` 与后端模块 `ruoyi-station`。
前两版文件夹保持不变，其路径与哈希记录在 `revision-notes.json`。

## 打开

直接用桌面浏览器打开 `nexus-erp-architecture.html`，不需要启动后端。
支持切换深浅主题、缩放及来源信息查看。小屏幕向下滚动可查看下方数据设施和职责说明卡片。
`nexus-erp-architecture-preview.png` 从 2048×1320 浅色浏览器截图裁去周围留白，只是预览，不是整页导出。

## 四类客户端

| 客户端 | 技术与工程 | 业务定位 | 与后端的主要边界 |
|---|---|---|---|
| PC | Vue 3 / plus-ui | 业务管理、系统配置、AI 与流程管理 | 登录态 API / SSE 订阅 |
| APP | uni-app + Vue 3 / Nexus-App | 移动管理与协同：考勤、IM、移动业务、AI | 登录态 HTTP API；具体推送按客户端与后端配置核对 |
| PDA | uni-app + Vue 3 / nexus-pda | 仓储、产线、质检与设备人员的现场扫码作业 | /app/pda/** 登录态接口；写操作权限与幂等 |
| Station | Electron + Vue 3 / nexus-station | 固定工位烧录、综测、过站、SOP 与上料 | X-API-KEY HTTP 设备通道；配置的本地硬件驱动 |

四端保持同一抽象层级，人的角色写入职责卡片，不额外叠加一个指向所有前端的重复人物节点。
APP 的实际 API 不统一限定为 /app/**：考勤、IM、AI、ERP 移动业务分别按代码中的路由调用。
Station 客户端不是后端 `ruoyi-station` 模块，也不是 MQTT Broker；本地串口/CLI 是它的执行能力，不由 Web 管理端直接控制。
工位工程含 mock / 串口 / CLI 驱动。图中真实外设连接有配置条件，不把 mock 注册或单元测试当作真机验收。

## 后端与数据设施

- `ruoyi-admin` 将 ERP / MES / IoT / AI / Flow 及 `ruoyi-station` 等组装在 NexusServer 内，不画成独立微服务。
- MQTT 工业设备单列，由内嵌 MQTT 接入、认证及 Topic 授权处理。
- MySQL：业务数据、知识文档与分块元数据；镜像 5.7 是根 Compose 的代码基线，不是线上探测结果。
- MinIO / S3：原始文件、图片及附件；Redis：缓存、Stream 与实时广播；RabbitMQ：跨模块异步事件。
- 模型服务：按配置使用对话 / Agent / Embedding 模型；后端拥有实际调用和向量写入职责。
- Milvus：向量及相似检索，按 knowledgeId 分 Collection，条件为选用 milvus 模式；同时存在 inmemory 分支。
- Vision：启用且配置密钥时通过 HTTP 调用，不能当作已连接或已经生产验收。
- Nginx 只画根 Compose 能证明的 PC 静态目录关系，外置 API 代理配置不在本图推断范围。
- 辅助监控与调度服务未展开成双机部署拓扑；本图也不是完整模块依赖矩阵、数据库 ER 图或业务状态机。

## 四仓库证据

| 仓库 | 固定提交 |
|---|---|
| Nexus-ERP | 785f70d08a59e4c6115afae61a59da0173d7afa9 |
| Nexus-App | 807e6a050166a9c5e1abe4191480b6d665521609 |
| nexus-pda | 6f75c4891e696bf7b9b1487020d1ddaea3d9f9a8 |
| nexus-station | 0d8ebcb461ae40f196fabfc72608f429db1bd8dc |

原生 Archify 来源结构只绑定一个仓库。图中 40 条引用核验 ERP 代码，客户端与外设节点的来源标签明确为后端契约；不伪造客户端文件属于 ERP。
客户端实现、实际请求发送、扫码和硬件驱动在 `client-evidence.json` 另存 23 条已提交源码范围及文件/片段哈希。
`repository-snapshot.json` 记录四个工作区状态，未提交文件不用于固定提交证据。

## 校验与复核

- 最终 `review-2/` 回执：validate / deliver / check / browser-check 全部通过。
- Showcase **9/9 检查，0 错误，0 警告**。
- Edge 浏览器覆盖 1440×900、1600×1000、1920×1080、2048×1320，无横向溢出；小视口按 Reader 合约纵向滚动。
- 四张浅色/深色端点尺寸截图捕获成功。
- Agent 已查看 1440×900 浅色及 2048×1320 浅色/深色截图；四端、物理接入、存储分工及条件标签可辨。
- 仍有 **2 处已处理跨越**（PC/模型调用、Station/MQTT），不表示连接节点；不宣称全图零交叉或所有线路都是直线。
- 验证前对 Redis 文字进行了两次有测量依据的标签诊断，最终只有其 labelAt 被调整。一次后续位置修订仅移动 SQL 与 MQ 节点，未改变任何节点/关系/事实。
- 这是 Agent 图像复核，不是用户人工业务验收。未连接真实数据库、模型、向量服务、PDA 真机或工位硬件。

## 文件

| 文件 | 用途 |
|---|---|
| nexus-erp-architecture.html | 最终交互图 |
| nexus-erp-architecture-preview.png | 浅色预览 |
| candidate.json | 最终可编辑图定义 |
| client-evidence.json | APP / PDA / Station 三仓库客户端与硬件证据 |
| handoff.json | 当前规格/产物哈希及校验状态 |
| visual-review.json | 独立截图复核记录 |
| preview-derivation.json | 预览来源、裁切范围与哈希 |
| repository-snapshot.json | 四个仓库的固定基线与工作区状态 |
| revision-notes.json | 已批准范围及两份旧版哈希 |
| review-2/ | **当前轮次**回执、四张截图与联系表 |
| candidate.before-label.json / candidate.before-placement.json / diagnostic-layout.json | 有界诊断与备份，不是最终图定义 |
| 根目录旧 finalize/browser 记录 | 之前轮次记录，不能用于当前 HTML 的通过声明 |

## 复核命令

执行目录：`E:\Nexus\nexus-erp`。

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'doc/system/archify-nexus-erp-architecture-20261009-111634/candidate.json' 'doc/system/archify-nexus-erp-architecture-20261009-111634/nexus-erp-architecture.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'doc/system/archify-nexus-erp-architecture-20261009-111634/review-2' --json
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'doc/system/archify-nexus-erp-architecture-20261009-111634/nexus-erp-architecture.html' --summary --require-provenance --out-dir 'doc/system/archify-nexus-erp-architecture-20261009-111634/review-2'
```

后续修改 candidate 时使用新的 `--out-dir` 完整重跑 finalize，再捕获和复核新截图。
本次仅新增本目录，没有修改四个项目的业务代码、配置或数据库，没有提交、推送或启停业务服务。
没有运行 Maven、客户端测试或应用构建；不存在应用测试通过、生产部署或真机验收完成的声明。
