# AI 智能助手进度复核与三阶段优化路线图

> 修订日期：2026-10-22（Asia/Shanghai）。代码基线：`plus / 3791aa7b` 加当前未提交成果；接续第一阶段实施与中断的版本升级会话。保留原工作区，不提交、推送、部署或执行存量数据库迁移。
> 版本固定为 LangChain4j 核心 `1.22.0`、扩展 `1.22.0-beta32`；Java 17 编译目标、Spring Boot 3.5.12、Jackson 2、现有 Milvus 模块/向量维度/集合结构均保留。
> 本轮证据为合成数据、临时回环服务、Java STDIO 子进程及本地构建；未调用真实付费模型、读取业务库、启动用户服务或执行浏览器/生产验收。

## 1. 当前交付状态

第一阶段的发送与业务查询权限、SSE 请求关联、PC 会话隔离、菜单权限和标准测试入口已有实现；本轮完成依赖升级、兼容测试及集成自动化。此前评估中的权限、会话污染、缺失菜单和标准入口问题已进入有回归保护的实现状态。真实环境角色配置及发布验收仍需按环境进行。

| 范围 | 本轮结果 | 证据边界 |
|---|---|---|
| AI / 公共 AI / Flow | 公共 AI 190 + AI 1549 + Flow 274 = **2013 项通过**（148 个类） | 当前 1.22.0 / beta32 的同一轮集成回归，实际用例非零、零失败/错误/跳过 |
| 系统授权及 admin 装配 | 3 项授权测试通过；51 个 reactor 模块成功 | 当前服务端角色/租户授权适配及 Java 17 目标编译；没有启动 Spring 应用 |
| PC AI 定向 / 标准单测 | 687 / 2203 项通过 | 分别为两次命令的 Node 测试执行数，不能相加作为独立用例数；均零失败/跳过 |
| PC 类型与生产构建 | 非增量类型检查、生产构建通过 | 产物输出 `plus-ui/node_modules/.tmp/ai-upgrade-build-resumed`，没有部署 |
| Controller → async → SSE → 两组件 | 后端 1 项夹具 + 前端 38 项测试通过；6 场景、15 个序列化 SSE 事件 | 本轮后端合成产物驱动真实页面/抽屉处理函数；未经过 Redis、浏览器或真实模型 |
| 依赖解析 | 生效的 LangChain4j 版本只有 1.22.0 / 1.22.0-beta32 | `dependency:tree -Dverbose`，无生效的 1.18.0 混用 |

不以菜单文件覆盖率或测试总数推导整体完成百分比。页面与功能库存见下一节；第二、三阶段的可靠性、评测和人工验收未计为完成。

## 2. 各分组与子菜单

“主要链路已实现”表示本次读到了对应页面、API、控制器及服务实现；“基础版”表示可见能力已经存在，仍缺少重要的治理或交付能力；历史本机验证另行标注。

| 分组 | 子菜单 | 当前完成状态 | 已有能力与主要优化方向 |
|---|---|---|---|
| 对话与应用 | 智能对话 | 主要链路已实现，一阶段自动化通过 | 会话与历史、普通/RAG/Agent/自动路由、流式/思考、工具过程、联网、图表、反馈、导出与工程卡片均有实现。发送权限、SSE 请求隔离及完成消息 ID 回填已实现并回归；服务端取消仍属第二阶段。 |
| 对话与应用 | 文生图 | 基础版 | 模型能力检查、生成、OSS 保存、历史和删除已实现；实际图片厂商实现仅发现通义万相。模型页能配置 IMAGE 不等于该厂商已具备生图适配。建议能力联动、异步任务、受限重试、预算及资源访问策略。 |
| 对话与应用 / 工程设计 | 工程设备 | 主要链路已实现，历史有本机联调 | 配对/解绑、设备状态、连接检查、协议诊断与 Windows 连接器已实现。补齐环境标识、版本兼容、离线提示与配对/私桶的部署验收。 |
| 对话与应用 / 工程设计 | 工程任务 | 草稿生成与产物预览链路已实现 | 受控模板、任务租约/续约/恢复、私有产物、PNG/线路/OBJ/BOM/DRC 和下载已实现。2026-10-17 验证示例仍有 8 项 DRC 错误，保持 PARTIAL_SUCCESS；不能按可投产工程计算完成度。 |
| 模型配置 | 模型配置 | 主要链路已实现 | CRUD、默认模型、类型/输入输出能力、协议路由、草稿连接测试、价格等已有实现。建议按实际运行适配过滤候选，并增加持续健康、受限账户可用范围和版本兼容矩阵。 |
| 模型配置 | 厂商管理 | 主要链路已实现 | 多厂商配置、凭据保护、模型联动与协议策略存在。进一步做凭据轮换、模型/厂商健康与审计，逐环境记录实际连接结果。 |
| 模型配置 | 参数设置 | 主要链路已实现 | SQL 安全、向量存储、搜索、Flow、KG 与工业决策配置均有管理入口，已有缓存失效及集群广播。建议增加配置作用范围、修改前检查、变更差异和回退；明确平台级配置的管理授权。 |
| 知识与工具 | 知识库管理 | RAG 主链路已实现，KG 为 MySQL 版 | 上传/URL导入、解析分段、向量化、混合检索、重排、索引状态/重建、检索测试、图谱抽取与社区摘要均有实现。补来源引用的页面化、质量评测、文档有效期/版本和资源 ACL；Neo4j、Leiden、gleaning 不计已完成。 |
| 知识与工具 | 工具管理 | 主要链路已实现 | 内置/外部工具、启停、测试、同步、会话选择、地址策略和外部资料证据闸门存在。建议建立操作者/业务权限与工具准入的映射，补工具版本、执行审计和调用配额。 |
| 知识与工具 | MCP 市场 | 基础市场接入已实现 | 市场源 CRUD、同步、工具加载及批量加载已有实现，地址与配置保护已有测试。进一步加入来源可信度、安装审核、版本锁定和健康状态；本次未检查真实市场连通性。 |
| 提示工程 | Prompt 模板 | 模板管理基础版 | 编辑、渲染预览、租户缓存、版本号递增已实现。未形成不可变历史版本、发布/回退、差异审查和评测绑定；缓存清除主要在本进程，需核对多节点一致性。 |
| 提示工程 | SQL 示例 | 管理与注入链路已实现 | 分类、CRUD、启停、顺序、租户缓存及 Agent 示例注入已有实现。补 schema 变更检查、样本检索、固定业务问题回归与失败问题回流。 |
| 运营分析 | A/B 测试 | 基础比较版，工程化偏弱 | 两模型逐问题执行、结果对比、人工评分和报告接口存在。模型仍需手填 ID/问题 JSON，需增加任务批次、原子认领、失败状态、恢复/取消、测试集版本和用量归属。 |
| 运营分析 | Token 用量 | 统计版，尚非完整成本治理 | 日统计原子 upsert、趋势/排行、个人用量与历史回填存在。需要统一 Agent 多调用、A/B/Flow/嵌入/重排等口径；处理币种、价格快照、预算和统计失败补偿。 |
| 流程编排 | 流程列表（含隐藏流程设计） | 单机执行能力较完整 | DAG、节点配置、发布/版本、单节点/整图测试、条件/循环/并行、定时/Webhook 等有实现，工业系统图受保护。优先解决普通角色详情权限、超时任务继续运行、停止信号的集群传播与恢复。 |
| 流程编排 | 运行历史（含隐藏实例详情） | 持久化监控与续跑已实现 | 实例/节点状态、输入输出、错误、时长、失败续跑有实现。补长任务恢复、执行所有者/租约、统一调用追踪、历史保留与输出访问策略。 |
| 工业决策中心 | 交付风险看板（含隐藏决策详情） | 可信事实、分析、审批与草稿动作主线已实现 | 快照、确定性风险/KPI、方案、证据、模型定性摘要、人工审批、实时权限、合同版本、幂等动作与恢复已有实现和较多回归。建议围绕当前构建统一做真实来源、审批/恢复/多租户、摘要质量和 SLA 验收；现有三来源履约不能折算为 40 个独立案例。 |

知识图谱为知识库内页签，不是遗漏的独立菜单。隐藏路由包括流程设计、流程实例详情、决策详情，另有工程任务详情的前端动态路由。

菜单依据：`script/sql/ry-vue.sql:27852` 起的 AI 菜单段，以及 `:28975` 起的工程设计菜单段。工程详情路由在 `plus-ui/src/router/index.ts:118`。

## 3. 一阶段权限与查询覆盖

`AiChatController` 与 `AiServiceFacade` 在模型调用及消息写入前检查发送权限；个人会话归属继续生效。ERP 查询复用 `ReportAuthorizationPort`、`ReportActor.scopeFor` 和固定实体目录，服务端重新读取身份、角色、租户及业务范围，在 JDBC 执行前注入行范围并限制字段；元数据即使命中缓存也重新授权。

来源：`ErpReportEntityCatalog` 的实际九实体目录及本轮捕获的 29 个固定工具方法 SQL。下表 R 表示以 `create_dept` 限制部门、`create_by` 限制本人；普通角色同时强制可信文本 `tenant_id` 与 `del_flag='0'`。表内列名为物理 SQL 字段。

| 实体 / 物理表 | 业务权限 | 行范围 | 允许字段（额外字段权限单列） | 可用固定工具 |
|---|---|---|---|---|
| stock / `wm_material_stock` | `wm:stock:list` | R | `stock_id, material_id, material_code, material_name, warehouse_id, warehouse_name, base_uom_name, stock_quantity, reserved_quantity`；`unit_cost` 另需 `wm:stock:query` | IV |
| material / `bd_material` | `bd:material:list` | R | `material_id, material_code, material_name, base_uom_id` | 无完整固定方法 |
| warehouse / `wm_warehouse` | `wm:warehouse:list` | R | `warehouse_id, warehouse_code, warehouse_name` | IV |
| client / `bd_client` | `bd:client:list` | R | `client_id, client_code, client_name` | SC |
| vendor / `bd_vendor` | `bd:vendor:list` | R | `vendor_id, vendor_code, vendor_name` | PV |
| purchase / `pu_purchase_order` | `pu:order:list` | R | `order_id, order_no, vendor_id, order_date, currency, status`；`total_amount` 另需 `pu:order:query` | PV、PT |
| purchase_line / `pu_purchase_order_line` | `pu:order:list` | `order_id` → 已授权采购头 | `line_id, order_id, material_id, material_code, unit, order_quantity`；`line_amount` 另需 `pu:order:query` | 无完整固定方法 |
| sales / `so_sales_order_header` | `so:orderheader:list` | R | `so_id, so_no, client_id, order_date, currency, order_status`；`total_amount` 另需 `so:orderheader:query` | SC |
| sales_line / `so_sales_order_line` | `so:orderheader:list` | `so_id` → 已授权销售头 | `line_id, so_id, material_code, unit, ordered_qty`；`line_amount` 另需 `so:orderheader:query` | 无完整固定方法 |

“可用”表示 SQL 在拥有所需权限及全部数据范围的合成普通角色下通过保护器；仍须满足当前角色行范围，不表示已验证真实业务结果。

| 标记 | 完整固定方法 | 所需业务权限 |
|---|---|---|
| IV | `ErpInventoryTool.queryInventoryValue` | `wm:stock:list`、`wm:stock:query`、`wm:warehouse:list` |
| PV | `ErpPurchaseTool.queryPurchaseAmountByVendor` | `pu:order:list`、`pu:order:query`、`bd:vendor:list` |
| PT | `ErpPurchaseTool.queryPurchaseTrend` | `pu:order:list`、`pu:order:query` |
| SC | `ErpSalesTool.queryTopClientsBySales` | `so:orderheader:list`、`so:orderheader:query`、`bd:client:list` |

29 个固定方法中普通角色可完整通过 4 个，其余 25 个保持拒绝：

| 工具 / 方法 | 尚未覆盖的表、字段或语法 |
|---|---|
| Inventory：`queryLowStockMaterials` | `bd_material.safety_stock` |
| Inventory：`queryMaterialOutboundTrend`、`queryInventoryTurnover` | 目录外 `wm_material_stock_tx` |
| Inventory：`queryStockByWarehouse` | `wm_material_stock.base_uom_id` |
| Inventory：`queryInventoryAging` | `wm_material_stock.recpt_date, total_cost` |
| Production：`queryActiveWorkOrders`、`queryProductionCompletionRate`、`queryProductionDelayAnalysis`、`queryWorkshopOutput` | 目录外 `prd_work_order` |
| Production：`queryWorkstationUtilization` | 目录外 `prd_work_order, bd_workstation` |
| Production：`queryBomExplosion` | 递归 CTE / `UNION ALL`；目录外 `bd_bom, bd_bom_detail` |
| Purchase：`queryVendorRiskAssessment`、`queryVendorDeliveryPerformance` | 目录外 `pu_vendor_evaluation` |
| Purchase：`queryPendingPurchaseOrders` | `pu_purchase_order.expected_arrival_date` |
| Purchase：`queryPurchasePriceHistory` | `pu_purchase_order_line.unit_price` |
| Sales：`queryMonthlySalesTrend`、`queryTopMaterialsBySales` | `so_sales_order_line.material_id` |
| Sales：`queryRecentReturns` | 目录外 `so_return` |
| Sales：`querySalesOrderFulfillmentRate` | `so_sales_order_line.delivered_qty` |
| Sales：`querySalesByCategory` | `so_sales_order_line.material_id`、`bd_material.category_id`；目录外 `bd_material_category` |
| Shortage：`queryShortageRisk` | CTE；目录外 `prd_material_requirement_detail`；未开放 `received_quantity, expected_arrival_date, safety_stock` |
| APS：`querySchedulePlans`、`queryResourceUtilization`、`queryDelayedWorkOrders`、`queryWorkOrderSchedule` | 目录外 `prd_schedule_plan / prd_schedule_task`；延迟工单还需 `prd_work_order` |

其他边界与审查结果：

- 普通角色的 CTE（含嵌套）全部拒绝；管理员可通过受支持的 CTE/递归体 AST，29 个固定方法均通过管理员 AST。根 Compose 当前是 MySQL 5.7，CTE 的数据库运行能力必须按实际环境另验。
- `scopeFor(permission)` 只合并真正授予该权限的角色范围；引用额外字段权限时进一步取交集。明细通过 `EXISTS` 关联同租户、未删除且已授权的主表，不受无关全部数据角色扩大。
- JOIN、投影、条件、分组、HAVING、排序、派生表及受支持子查询均逐节点校验；无法可靠验证的括号包装子句、分组 JOIN 和数组下标拒绝。当前只复用目录访问规则，不等同于报表编译器的聚合键、可聚合性及关系基数规则。
- 本轮修复 `Column.getArrayConstructor()` 隐藏字段/子查询的 AST 检查缺口；新增普通/管理员反例确认在 JDBC 调用前拒绝，不将其表述为已验证的数据库可执行绕过。
- 参数化投影、JOIN、子查询、父表策略、CASE 排序、GROUP_CONCAT、LIMIT 两种写法及管理员 CTE 的七组探针保留原参数顺序；授权谓词不增加占位符。
- 撤权作用于后续授权调用，已开始执行的查询使用该轮身份快照。元数据公共读取及旧快照格式化均重新授权；普通快照不暴露原始 DDL、物理备注、默认值、枚举和外键。结构缓存 TTL 为 24 小时；另一个物理存在性缓存没有失效机制，建/删表信息可能滞后，此项不使已撤权限恢复。
- 仅服务端持久化用户 ID 1 获得超级管理员身份；普通通配权限仍受目录约束。管理员仍需要有效身份、激活租户和每个物理来源的可信租户过滤；其分支不附加普通角色的软删除、部门和本人范围。

普通受限角色按上表的九类实体验收。目录外实体及无法可靠验证的 SQL、字段或行范围拒绝，不降级到租户全量。超级管理员仍须通过只读 SQL、表前缀及租户检查。固定工具存在并不代表其所有方法都在普通角色支持范围内。

SSE 保留 `POST /ai/chat/send` 的 `R<Long>` 会话响应；普通聊天新增 `requestId/clientRequestId/sessionId/sequence/messageId` 信封，ID 使用字符串，终态前持久化消息。`clientRequestId` 可选，只作关联。Flow 与工程事件保留原协议；页面及抽屉切会话或关闭时清理接收状态，终态后、乱序、重复及其他请求的事件不会改写当前回答。关闭前端接收不等于服务端取消。

安装 SQL 与独立增量补齐 `ai:flow:query`、`ai:model:export`、`ai:provider:export`、`ai:knowledge:export`，不自动向角色授予权限。报表 Schema 测试仅适配等价的 CREATE/INSERT/UNIQUE KEY/INDEX 语法，继续验证唯一索引列、菜单关系及禁止破坏性 SQL；回归包含反例。

## 4. 版本兼容验证与保留边界

- 七类生产协议适配器均验证路由及同步/流式构造。OpenAI Chat Completions、Responses、Anthropic、Gemini、Ollama 使用临时 HTTP 合成响应；DashScope、智谱使用受控原生客户端替身，验证正文、工具、终态、错误与用量映射。兼容测试不等于厂商网络实测。
- 确定性假模型驱动真实 Supervisor 和 ERP 子 Agent，覆盖多轮工具、数字/字符串形式的雪花 ID、十进制字符串金额、失败传播和复用线程的请求身份清理。SDK 对未加引号的小数会先按浮点数解码；金额沿用应用既有字符串契约，未全局替换 Jackson 或扩大数值精度承诺。
- RAG 使用原 EmbeddingModel/EmbeddingStore 调用方式，覆盖批量顺序、维度、检索结果与元数据；聊天记忆、工具参数原文、雪花 ID、BigDecimal 和时间字段均有往返测试，没有迁移向量数据。
- MCP 使用真实 SDK 的 HTTP/1.1 回环及 Java UTF-8 STDIO，覆盖初始化/发现/中文调用、错误结果、超时取消、404 会话重建、关闭和子进程清理；现有数字 IP、精确端点、地址审批、重定向与凭据保护策略保留。未验证真实服务商、TLS/HTTP2 或 JVM 全局连接池关闭。
- 保留生产协议路由、推理开关、现有输出参数映射与显式超时。原生 SDK 的端点/超时限制不被本轮测试宣称为新增支持。Flow AI 节点随目标模块回归。
- 原生上限的既有限制：DashScope 传递 `AiModel.maxTokens`，未传递应用 DTO 的 `AiChatRequest.maxOutputTokens`；智谱两种 Builder 在 beta28/beta32 都有单数 `maxToken(Integer)`，但现有 Provider 都未设置，流式也未映射应用 DTO 上限。工厂仅把该 DTO 字段放入缓存键，不能等同实际生效。测试里的 11 上限是直接传给 SDK `ChatRequest.parameters()` 的覆盖，不能当作应用 DTO 已打通。此项在升级前即存在，本轮记录并修正误导性注释，统一应用上限传播归入第二阶段调用成本治理。

## 5. 后续路线图与依赖顺序

| 阶段 | 范围 | 验收交付物 |
|---|---|---|
| 第一阶段：安全与版本基线 | 当前权限、SSE、菜单/测试入口与 1.22.0 / beta32 兼容 | 本轮自动化证据、九实体矩阵、依赖树；发布环境另验 |
| 第二阶段：执行与成本可靠性 | 调用台账与分币种统计 → 服务端取消和 Flow 执行治理 → A/B 任务化及预算 | 可解释对账；取消/超时/崩溃矩阵；外部副作用幂等证明；A/B 原子认领、恢复与失败状态 |
| 第三阶段：场景质量与发布治理 | RAG/SQL 固定题集、可信引用展示、Prompt 发布回退、工业决策和工程验收 | 固定版本评测、引用正确性及拒答报告、P50/P95、人工签收 |

第二阶段首先记录不可变模型调用明细，关联 `request/session/flow/decision/ab-run`、实际模型、输入/输出/缓存/推理 Token、币种及计价版本；默认分币种汇总。随后补对话服务端取消、Flow 有界执行器、执行所有者/租约、跨节点停止和持久化等待，明确“请求取消”与“确认停止”。A/B 再拆分定义、批次和逐题结果，接入台账、预算、原子认领、断点恢复及 FAILED/PARTIAL_SUCCESS 状态。

非阻塞 API、DecisionModel、BDI、Milvus V2 混合检索、Neo4j/Leiden 和更多多模态继续作为独立需求。当前本地自动化不替代工业决策 40 例、CONTROL、真实模型/向量库连接、模板审核或工程 DRC 的当前构建验收。

## 6. 本轮复现与原始证据

在仓库根执行：

```powershell
mvn -pl ruoyi-modules/ruoyi-ai -am dependency:tree "-Dincludes=dev.langchain4j:*" "-Dverbose"
mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=com.nexus.ai.**.*Test,com.nexus.flow.**.*Test,com.nexus.common.ai.**.*Test" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl ruoyi-admin -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=SystemReportAuthorizationAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

在 `plus-ui` 执行：

```powershell
npm run test:ai
npm run test:unit
npm run type-check -- --incremental false
npm run build:prod -- --outDir node_modules/.tmp/ai-upgrade-build-resumed
npm run test:ai:roundtrip
```

另行实际运行旧参数入口，141 项通过、零跳过；定向 Prettier 检查通过。独立审查核对权限/SSE/菜单/测试入口，相关 70 + 25 项回归通过，已包含或交叉覆盖主回归，不另累加。查询审查原始矩阵及参数探针位于 `target/ai-query-review-20261022-guard/`。

`test:unit` 现在保留原标准全链，`test:ai` 为独立 AI 组合；兼容 `npm run test:unit -- --ai-knowledge-mcp`。合成链路入口单独执行后端夹具，再使用当次 JSON 产物运行两组件测试；缺产物、零用例、超时或任一步失败均不能作为通过。

本机原始证据在 `ruoyi-modules/ruoyi-ai/target/ai-upgrade-evidence/`：`resumed-dependency-tree.log`、`resumed-backend.log`、`resumed-system-authorization.log`、`resumed-frontend-*.log`、`resumed-frontend-results.json`、`resumed-backend-summary.json`、`resumed-roundtrip.log` 及本轮 Surefire XML。临时产物不作为生产配置或提交内容。

## 7. 历史进度与本轮事实区分

- 2026-09-27：黄金 v2 报告记录隔离环境 40/40 业务契约通过，31 次 FLOW_LLM、3 次 TEMPLATE_FALLBACK；它既不是原始 v1 全通过，也不是所有模型摘要全通过，更不是当前构建的生产 SLA。
- 2026-10-09 至 10-10：本机业务线报告已推进三份原来源、九个成功 AI 动作及采购/制造/发货履约。应计为三条业务来源闭环，不能把申请、订单、重算或重放扩充为独立案例。
- 2026-10-17：工程合并报告记录真实本机链路、私有产物、线路/三维预览和浏览器检查，示例 DRC 仍有 8 项错误。
- 2026-10-19：工程配对诊断与连接器发布记录继续修复 API 前缀、失败提示、退出与身份保存，相关代码已经在当前分支。
- 本次没有重跑真实黄金集、CONTROL HTTP、付费模型、存量库迁移、人工工程审核或生产部署，历史测试数字不并入本次计数。

参考资料：

- `doc/ai/README.md`
- `doc/ai/benchmark/delivery-risk/reports/2026-09-27-golden-v2-40-regression.md`
- `doc/ai/full-flow/reports/2026-10-10-ai-原报价显式处置与三原单全履约闭环验证.md`
- `doc/ai/merge/2026-10-17-engineering-plus-integration.md`
- `doc/ai/plans/2026-10-19-engineering-pairing-api-diagnosis.md`

升级前证据单列：最初评估在 1.18.0 / beta28 记录公共 AI 190 + AI 1329 + Flow 274 = 1793 项通过；首轮阶段一会话报告 1885 项通过，后续定向 86 项通过。它们分别属于不同代码状态，均未合并到本轮升级后的计数。最初标准前端链的两条仓储 SQL 格式匹配失败已由兼容解析及反例回归收敛，当前标准链结果见第 1 节。

## 8. 待真实环境核验

- 按目标环境确认菜单基线、备份、增量幂等性及角色授权，再决定是否执行存量库迁移；本轮仅写入 SQL 文件。
- 部署后验证当前制品的普通角色矩阵、厂商协议、MCP 连接、Redis/SSE、向量库和业务数据范围；观察请求失败、超时、MCP 重连、SSE 终态与用量异常。
- 回退以升级前后端制品及配套前端为单位，并保留已实施权限保护。真实取消、Flow 分布式执行治理、调用台账与 A/B 可靠性属于第二阶段。

本轮合成入口的临时产物清理验证通过；另一个离线调试临时目录因自动审批拒绝删除而保留，最终交付说明列出具体路径。
