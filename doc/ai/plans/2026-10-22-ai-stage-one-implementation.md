# AI 助手第一阶段收尾与 LangChain4j 升级实施计划

> 基线：2026-10-22，plus / 3791aa7b。用户已授权开始评估报告中的第一阶段；沿用当前工作区，保留既有未提交评估报告，不提交、推送或执行存量库迁移。
> 工作流程：writing-plans、test-driven-development、dispatching-parallel-agents、verification-before-completion。独立文件集并行，整体验证后再交付。

**Goal:** 完成第一阶段权限、SSE与测试入口收尾，统一 LangChain4j 核心 1.22.0 / 扩展 1.22.0-beta32，补齐离线协议、Agentic、MCP、RAG和合成对话链路回归。

**Architecture:** 保留 POST /ai/chat/send 的 R<Long> 会话响应。PC在发送前创建 clientRequestId；服务端独立生成 requestId，事件携带两个标识、sessionId字符串、递增sequence以及可用的持久化messageId。业务查询复用已有ReportAuthorizationPort和受控实体目录，从服务端重读当前角色与数据范围，在JDBC执行之前保护行与字段；缺少身份、权限或可验证目录时拒绝。

**Tech Stack:** Java17 编译目标 / Spring Boot 3.5.12 / Jackson2 / LangChain4j 1.22.0 + 1.22.0-beta32 / Sa-Token / JSqlParser / JUnit / H2，Vue3 / TypeScript / Node test。

## 任务1：对话功能权限（本地实现）

文件：AiChatController、AiServiceFacade及其相关测试。

- [x] 添加真实拦截/授权反例，验证未授权发送不进入模型或消息写库；个人会话归属继续生效。
- [x] 发送、历史/会话列表、删除、改名、反馈分别要求ai:assistant:send/query/remove等已存在权限；不以菜单可见性作为后端授权。
- [x] 保留接口响应格式、租户文本与雪花ID，无效clientRequestId在调用模型前拒绝。
- [x] 重跑现有输入、会话归属、Agent/流式/RAG回归。

## 任务2：业务查询授权（独立文件集）

文件：ruoyi-ai/pom.xml，service/query中的新保护器、ErpQueryService及其测试。

- [x] 用合成H2资料覆盖同租户本部门/本人/全部、跨租户、角色撤权、无权限、无身份、禁止字段和JOIN/聚合/OR查询。
- [x] 复用common-report的ReportAuthorizationPort、ReportActor.scopeFor与ReportEntityCatalogProvider；权限只取服务端，不读请求传入的角色/范围。
- [x] 在executeQuery/executeParameterizedQuery统一保护，覆盖固定工具及Flow SQL路径；元数据查询同样过滤表与字段。
- [x] 不能可靠映射范围/字段的查询拒绝并给出受控错误，不回退租户全量。既有SQL安全检查继续生效。
- [x] 检查目录对常见库存、采购、销售工具的覆盖，并明确记录不支持的范围。

## 任务3：后端SSE关联（本地实现）

文件：AiChatRequest、AiChatSseLifecycle、AiSseMessageHelper、AiServiceFacade、新事件上下文及测试。

- [x] 先补真实发送DTO捕获测试，覆盖不同请求、回调线程、唯一终态、序号、消息ID和无损雪花ID。
- [x] 新增可选clientRequestId（UUID样式/受限长度），只作事件关联，不作认证或业务幂等键。
- [x] 生命周期持有独立服务端requestId和事件序号；progress/complete/fail显式绑定上下文，回调线程不得读取残留请求身份。
- [x] 普通聊天事件顶层结构：{type,content?,requestId,clientRequestId?,sessionId,sequence,messageId?}；所有ID用字符串。
- [x] 完成前保存消息，发布持久化messageId；保留旧客户端读取type/content和R<Long>的兼容性。

## 任务4：PC多会话隔离（独立文件集）

文件：src/utils/ai-chat-stream.ts（新建）、chat.ts、assistant/index.vue、AiChatDrawer.vue、聊天回归。

- [x] 先复现旧会话增量/完成污染当前会话。
- [x] 共享状态工具在HTTP之前begin(sessionId)，生成clientRequestId；仅接收当前clientRequestId、同一服务端requestId、有效sessionId及递增sequence的事件。
- [x] 新会话允许匹配nonce的事件先于HTTP ACK建立sessionId；ACK必须与既有绑定一致；无标识、旧事件、重复序号、错误session和终态后的进度均忽略。
- [x] 切会话、新建、关闭/卸载清空接收状态；HTTP迟到成功或失败不改写当前会话。
- [x] AI_DONE回填messageId，当前回答可以反馈；等待态以终态为准。
- [x] 两个真实组件都覆盖多会话、抽屉并用、ACK竞态和迟到终态。

## 任务5：菜单与测试入口（独立文件集）

文件：ry-vue.sql、新增2026-10-22-ai-stage-one-permissions.sql、数据库变更记录、package.json、单测入口脚本、仓储菜单解析测试、新契约测试。

- [x] 先用测试确认4个缺失权限与参数转发失败。
- [x] 新装SQL及独立增量补ai:flow:query、ai:model:export、ai:provider:export、ai:knowledge:export；核查ID无冲突，不自动扩大角色权限。
- [x] 增量仅写入仓库，不执行现有数据库；文档说明需要按环境核对和授权迁移。
- [x] 新增准确的test:ai入口；兼容npm run test:unit -- --ai-knowledge-mcp，让参数真正到达AI wrapper；默认全链仍覆盖原测试。
- [x] 仓储测试兼容当前完整INSERT与历史多行INSERT，继续严格比较菜单父级、path和组件大小写。

## 任务6：集成审查与验收

- [x] 复核全部修改及普通角色反例；独立代码审查后处理具体问题。
- [x] 后端：mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active=，采用明确测试包选择并确认非零用例。
- [x] 如改动报表目录或系统授权适配器，补对应ERP/system模块定向测试；admin编译检查装配。
- [x] 前端：test:ai、test:unit、type-check、build:prod（新临时输出目录），按结果记录失败/跳过。
- [x] 记录本次命令、实际计数、未执行存量库迁移和真实环境验收边界；更新计划复选框。

## 任务7：依赖统一与兼容适配（已验证）

- [x] 根版本属性升级至 `1.22.0` / `1.22.0-beta32`，保持统一属性管理；实际依赖树无生效旧版混用。
- [x] 核心、OpenAI、Anthropic、Gemini、Ollama 使用 1.22.0；Agentic、MCP、DashScope、智谱、Tika、Milvus 使用 beta32。
- [x] 保持 Java17 编译目标、Spring Boot 3.5.12、Jackson2 及原 Milvus/Embedding 调用；不新增业务表、不迁移向量数据。
- [x] 七协议路由及同步/流式构造保持兼容；当前编译及运行回归未要求修改生产模型 API，保留原超时和协议策略，原生输出参数限制另见评估报告；智谱单数 maxToken 自 beta28 已存在，当前模型/应用请求上限未映射属于既有限制，不把 SDK 直接请求覆盖测试当作 DTO 传播证明。

## 任务8：离线协议、传输与合成链路（已验证）

- [x] 真实 SDK + 临时回环 HTTP：OpenAI Chat Completions、Responses、Anthropic、Gemini、Ollama 的正文、思考、终态、错误、用量及请求参数；七协议工厂构造及路由，共 23 项。
- [x] DashScope/智谱使用生产 Provider + 受控底层客户端替身，覆盖同步/流式正文、分片工具参数、单次终态、错误回调及用量监听，共 8 项。
- [x] 确定性假模型驱动 Supervisor/ERP 子 Agent，多轮工具、数字/字符串大整数、金额字符串及线程上下文清理，共 3 项。未加引号的 JSON 小数不能获得额外精度承诺，沿用应用十进制字符串契约。
- [x] RAG 批量顺序/维度/检索元数据、聊天记忆/工具参数与应用雪花 ID/BigDecimal/时间序列化，共 4 项。
- [x] 真实 MCP HTTP/STDIO 初始化、发现、中文、错误、超时取消、重建会话、关闭及子进程清理，共 9 项；现有地址和凭据策略回归保持。
- [x] 新增 `npm run test:ai:roundtrip`：真实 Controller → 异步 Facade → 序列化 SSE → 两个真实 Vue 处理器；6 场景、15 个事件，Java 1 项 + Node 38 项通过。
- [x] 集成入口只使用当次产物，强制后端/前端非零用例、零失败/错误/跳过，使用有界进程和本轮临时目录；不接受跳过后端或旧产物的参数。

## 任务9：权限审查与文档收敛（已验证）

- [x] 逐项审查 AST、CTE、JOIN、子查询、字段与参数顺序；修复 Column 数组下标未遍历检查缺口，普通/管理员均在 JDBC 前拒绝。
- [x] 整理九实体的业务权限、行范围、字段及固定工具矩阵，不扩大目录；29 固定方法中 4 个可供具备对应权限和范围的普通角色使用，其余 25 个拒绝原因已登记。
- [x] 核查服务端实时撤权、元数据缓存重新授权、文本租户 ID 和管理员约束；物理存在缓存与报表聚合语义不等同于本轮访问授权，限制已记录。
- [x] 两份计划/评估文档区分旧版基线、新版同轮结果及真实环境待验事项，更新三阶段路线图。

## 本轮集成验收（2026-10-22，1.22.0 / beta32）

| 检查 | 实际结果 | 原始证据 |
|---|---|---|
| AI / 公共 AI / Flow | 190 + 1549 + 274 = **2013** 项，148 类，失败/错误/跳过均 0 | `resumed-backend.log`、`resumed-backend-summary.json`、对应 Surefire XML |
| 系统授权 + admin 装配 | SystemReportAuthorizationAdapterTest 3 项，51 reactor 模块成功 | `resumed-system-authorization.log` |
| LangChain4j 解析依赖 | 只有 1.22.0 / 1.22.0-beta32 生效 | `resumed-dependency-tree.log` |
| 前端 AI 定向 | 687 项，失败/跳过 0 | `resumed-frontend-ai.log` |
| 标准前端链 | 2203 项 Node 测试，失败/跳过 0，所有步骤退出 0 | `resumed-frontend-unit.log` |
| 旧参数 AI 入口 | 141 项，失败/跳过 0 | `resumed-frontend-legacy-ai.log` |
| 类型检查与生产构建 | 均退出 0；4733 模块，临时目录产物 | `resumed-frontend-typecheck.log`、`resumed-frontend-build.log` |
| 后端到两组件合成链路 | Java 1 + Node 38，全部实际执行，失败/跳过 0 | `resumed-roundtrip.log` |
| 独立审查 | 权限/SSE/菜单/入口未发现阻断问题；查询 AST 缺口修复后回归通过 | 相关代码、查询反例与矩阵 |
| 定向格式检查 | package、聊天 TS/Vue、合成链路脚本通过 Prettier | `resumed-frontend-format.log` |

后端采用本计划修订中的明确包选择：

```powershell
mvn -pl ruoyi-modules/ruoyi-ai -am dependency:tree "-Dincludes=dev.langchain4j:*" "-Dverbose"
mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=com.nexus.ai.**.*Test,com.nexus.flow.**.*Test,com.nexus.common.ai.**.*Test" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl ruoyi-admin -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=SystemReportAuthorizationAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

前端在 `plus-ui` 执行：

```powershell
npm run test:ai
npm run test:unit
npm run test:unit -- --ai-knowledge-mcp
npm run type-check -- --incremental false
npm run build:prod -- --outDir node_modules/.tmp/ai-upgrade-build-resumed
npm run test:ai:roundtrip
```

证据目录为仓库内 `ruoyi-modules/ruoyi-ai/target/ai-upgrade-evidence/`。旧版最初评估 1793、第一阶段历史报告 1885、定向 86 与本轮 2013 属于不同代码/依赖状态，不合并；前端不同命令存在重复覆盖，也不相加。

## 后续与发布边界

第一阶段完成代码与本地自动化交付。第二阶段同时统一原生 Provider 对模型/应用请求输出上限的传播，按“模型调用明细/分币种对账 → 服务端取消、Flow 有界执行/所有者/租约/跨节点停止/持久化等待 → A/B 定义/批次/逐题结果、原子认领/恢复及预算”的顺序细化。第三阶段处理固定题集、可信引用、Prompt 发布回退及工业决策/工程的当前构建人工验收。

`POST /ai/chat/send` 的 `R<Long>`、可选关联 nonce、Flow/工程事件协议均保留；前端关闭接收不代表服务端取消。四菜单权限增量仅存于仓库，需按环境核对基线、备份、幂等性及角色权限后另行授权。真实厂商、MCP TLS/HTTP2、Redis/SSE、Milvus、业务数据库、浏览器和生产未验收；不自动提交、推送、部署或调用付费模型。
