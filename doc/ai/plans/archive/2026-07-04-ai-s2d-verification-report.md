# NexusERP AI — S2-D 平台稳定化与运行时验证报告

> **阶段**：二阶段收尾 S2-D
> **周期**：2026-06-30 ~ 2026-07-04
> **执行**：代码层审计 + Bug 修复（AI 助手完成）；落库/启动/端到端运行时验证（需在配齐 DB/Redis/LLM 的本机环境执行，见第四节待办）
> **编译状态**：✅ BUILD SUCCESS（所有修复均通过 `mvn -pl ruoyi-modules/ruoyi-ai -am compile`）

---

## 一、执行摘要

本周完成 S2-D 计划中可在代码层完成的两项核心工作：

| 工作 | 结论 | 详见 |
|------|------|------|
| **Task 5 异步写库租户上下文审计** | 3 个文件 5 处异步写库路径漏恢复租户上下文（`tenant_id` 落 0），已全部修复 | 第二节 |
| **Task 3 S2-A/B/C 代码层 Bug 审查** | 三路并行审查共发现 34 条潜在 Bug（S2-A 9 / S2-B 13 / S2-C 12），分级入清单 | 第三节 |
| **高优先级 Bug 修复** | 8 条高优先级 Bug 全部修复：E1 流式 token 统计 / S2-C-1 SSRF / C1+C2 厂商 listeners+temperature / R1 原子 upsert / B 凭证缓存失效 / S2-C-2 MCP command / S2-C-3 熔断重置 | 3.2 |

**累计修复 16 条**（图谱构建 3 + 异步租户 5 + 高优先级 8），全部 BUILD SUCCESS。剩余中低优先级 Bug 见 3.3/3.4 清单。

**未在本轮完成（需配齐环境）**：Task 1 落库与启动、Task 2 S2-A 端到端、Task 4 S2-B/C 回归、Task 6 基线采集——这些依赖数据库（密码 ENC 加密无法在此侧连接）、Redis、真实 LLM API Key，见第四节待办清单。

---

## 二、Task 5：异步写库租户上下文审计（✅ 已修复）

### 2.1 审计结论

`aiExecutor` 异步线程中 Sa-Token ThreadLocal 不传播，多租户拦截器 `PlusTenantLineHandler.getTenantId()` 拿不到上下文 → insert 时 `tenant_id` 列落 `0`/null（而非 `000000`）。以 `KnowledgeGraphService.buildGraph:79` 的正确处理为基准，审计全部 `aiExecutor`/`CompletableFuture` 异步写库路径：

| 文件 | 位置 | 写库表 | 修复前 | 修复后 |
|------|------|--------|--------|--------|
| `KnowledgeGraphService` | 79, 363 | kg_* | ✅ 已正确（正面范例） | — |
| `KnowledgeProcessService` | 87, 207 | `ai_knowledge_chunk`/`document` | ❌ 漏恢复 | ✅ 已修 |
| `AiAbTestController` | 157 | `ai_ab_test_result`/`ab_test` | ❌ 漏恢复 | ✅ 已修 |
| `AiServiceFacade` | 258/348/463/852（经 2 个 save 方法汇聚） | `ai_message` | ❌ 漏恢复 | ✅ 已修 |
| `RerankService` | 145 | 只读打分 | 不适用 | — |

### 2.2 修复要点

- **`KnowledgeProcessService`**（2 处）：`uploadAndProcess`/`retryProcess` 在请求线程捕获 `TenantHelper.getTenantId()`，异步闭包内 `TenantHelper.dynamic(tenantIdStr, () -> processDocument(...))` 恢复。
- **`AiAbTestController`**（1 处）：`execute` 方法捕获 tenantIdStr，整个 `executeTestInternal`（含错误处理路径的 update）包进 `TenantHelper.dynamic`。
- **`AiServiceFacade`**（核心对话路径）：发现双重 bug——
  1. 入口 `tenantIdStr = LoginHelper.getTenantId()`（"000000"）被 `Long.parseLong` 转成 `0L`，丢失前导零格式；
  2. `saveAiMessageWithTokens`/`saveAiMessageFromContext` 的 `Long tenantId` 参数是 **dead parameter**（从未使用），正是 bug 根因——开发者传了 tenantId 却没用于恢复租户。
  
  修复：入口保留 `tenantIdStr`（String）并透传给 4 个 invoke 方法 → 传到 2 个 save 方法 → insert 处 `TenantHelper.dynamic(tenantIdStr, () -> aiMessageMapper.insert(aiMsg))`。`Long tenantId` 保留给 `AiRequestContext`（token 统计表 `ai_token_usage.tenant_id` 是 bigint，需 Long）。

### 2.3 类型一致性确认

- `ai_message.tenant_id` = `varchar(20) DEFAULT '000000'` → 需 String "000000"（修复用 dynamic 让插件填原始字符串）
- `ai_token_usage.tenant_id` = `bigint DEFAULT 0` → 需 Long（AiRequestContext 保留 Long 正确）
- `ai_ab_test_result.tenant_id` = `bigint DEFAULT 0` → dynamic(tenantIdStr) 经 MySQL 隐式转换亦正确

### 2.4 运行时验证待办

修复已编译通过，但 `tenant_id` 是否真落 "000000" 需起应用跑数据后查表确认：
```sql
SELECT tenant_id, COUNT(*) FROM ai_message GROUP BY tenant_id;        -- 期望 '000000'，无 0/null
SELECT tenant_id, COUNT(*) FROM ai_knowledge_chunk GROUP BY tenant_id; -- 期望 '000000'
SELECT tenant_id, COUNT(*) FROM ai_ab_test_result GROUP BY tenant_id;  -- 期望 0（bigint）
```

---

## 三、Task 3：S2-A/B/C 代码层 Bug 审查

三路并行审查（每路独立读码 + 反向核对"已确认正确"的点）。共 34 条发现，按严重度与修复状态分级。

### 3.1 已修复（✅ 本轮修复，BUILD SUCCESS）

| # | 文件:行 | Bug | 严重度 |
|---|---------|-----|--------|
| S2-A-1 | `KnowledgeGraphService:422` | `filterAndCap` 用 `Comparator.comparingDouble` 对 null confidence 自动拆箱 → NPE；且 417 行保留 null 元素进 filtered，`size()>max` 时必触发 | 高 |
| S2-A-2 | `KnowledgeGraphService:209-237` | `extractChunk` 的 try-catch 只包住 extract/parse，filterAndCap/mergeEntity/persistRelation/updateById 在 try 外，任一异常传播到 for-catch 仅 log+failedCount++，不重置状态 → chunk 永久卡 "1" 无法自愈 | 高 |
| S2-A-3 | `KnowledgeGraphService:263` | `mergeEntity` description 无限拼接 `existing + "；" + desc`，超 varchar(1024) 触发 DB 异常 → 叠加 #2 卡死 | 中 |
| Task5 | 见第二节 | 异步写库 tenant_id 落 0（3 文件 5 处） | 高 |

**修复方式**：
- #1：改 null 安全比较器（null 排最后，`Double.compare` 降序）
- #2：把后处理整体包进 try-catch，失败置 "3" + errorMessage（可重试）
- #3：拼接后超 1000 字符截断附 "..."

### 3.2 已修复 — 高优先级（✅ 本轮修复，BUILD SUCCESS）

| # | 文件:行 | Bug | 严重度 | 修复 |
|---|---------|-----|--------|------|
| **S2-B-E1** | `AiChatModelListener:52-98` + `AiServiceFacade:saveAiMessageWithTokens` | 流式/RAG 模式 token 日统计**永不写入**：listener `onResponse` 在 OkHttp I/O 线程，`InheritableThreadLocal` 不从 aiExecutor 传播 → `userId==null` 跳过 `incrementDailyUsage` | 高 | `saveAiMessageWithTokens`（已持有闭包上下文 + `response.tokenUsage()`）补 `calculateCost` + `incrementDailyUsage`；Agent 路径走 `saveAiMessageFromContext`，listener 在 aiExecutor 线程已写，不双计 |
| **S2-C-1** | `WebSearchTool:48-57` | **SSRF**：`crawlWebpage` 无 URL 校验，LLM 可被诱导抓取内网（`169.254.169.254` 元数据、`localhost:8080/actuator`） | 高 | 新增 `sanitizeUrl`：仅允许 http/https 协议 + `InetAddress.getAllByName` 解析后拒绝 loopback/siteLocal/linkLocal/anyLocal/multicast（注：DNS rebinding 残余风险做基础防护） |
| **S2-B-C1** | `DashScopeServiceProvider:49-54` / `ZhiPuServiceProvider:51-57` | 原生 SDK 厂商同步 `buildChatModel` 未挂载 `chatModelListeners` → Agent/Rerank 兜底路径 token 不采集 | 中 | 两处补 `.listeners(chatModelListeners)` |
| **S2-B-C2** | `DashScopeServiceProvider` / `ZhiPuServiceProvider` | 忽略 temperature/maxTokens 配置；`buildChatModel(config, temperature)` 收到 temperature 后丢弃 → Agent 模式 SQL 准确性回退 | 中 | 补 `.temperature()`（Qwen=Float、ZhiPu=Double，经 javap 确认）；Qwen 补 `.maxTokens(Integer)`；**ZhiPu builder 无 maxTokens 方法**，不补 |
| **S2-B-R1** | `AiTokenUsageServiceImpl:47-88` + `AiTokenUsageMapper` | `incrementDailyUsage` 先 select 后 insert/update 无锁，同 (user,model,date) 并发 → 唯一索引冲突被吞，token 丢失 | 中 | `AiTokenUsageMapper` 新增 `@Insert` 原子 `INSERT ... ON DUPLICATE KEY UPDATE`（依赖 `uk_user_model_date` 唯一索引 + IFNULL 防御），Service 改调 `upsertDailyUsage` + `IdWorker.getId()` |
| **S2-B-B** | `ChatModelFactory` + `AiProviderServiceImpl` | `ai_provider` 凭证变更（apiKey/apiHost）不发事件，已缓存 ChatModel 持旧凭证直到重启 | 中 | `AiProviderServiceImpl` 新增 `AiProviderChangeEvent`（仿 `AiModelChangeEvent`），`updateByBo`/`deleteByIds` 发布；`AiModelChangeListener.onProviderChange` 监听调 `evictAllCache`（`ChatModelFactory` 已有此方法） |
| **S2-C-2** | `McpMarketServiceImpl:131-134` | LOCAL 工具 `loadToolToLocal` 直接把 metadata 塞 configJson，但 `McpClientManager` 只认 `command`/`args`，市场元数据通常只含 `package` → 启用即报错熔断 | 中 | 新增 `buildLocalConfig`：metadata 有 `command` 保留原值；有 `package` 无 `command` 时合成 `{command:"npx", args:["-y", package]}`；保留 env |
| **S2-C-3** | `McpClientManager:257-260` | `refreshClient` 不重置熔断状态，管理员改配置/重启工具仍被禁用 5 分钟 | 中 | `refreshClient` 中清 `toolHealthStatus`/`toolFailureCount`/`toolDisabledUntil` 三个 Map |

### 3.3 待修 — 中优先级

| # | 文件:行 | Bug | 严重度 |
|---|---------|-----|--------|
| S2-A-4 | `MysqlGraphStoreStrategy:99-103` 等 | `deleteGraphData`/`rebuild`/`deleteGraph` 无 `@Transactional`，部分失败留不一致数据 | 中 |
| S2-A-5 | `KnowledgeGraphService:290-291` | `persistRelation` 按 name 查实体传 type=null，同名不同类型实体消歧失败，关系连错 | 中 |
| S2-A-6 | `KnowledgeGraphService:72-282` | 并发构建竞态，双击致 mention_count 翻倍（无锁/无唯一约束） | 中 |
| S2-B-D | `AiChatModelListener:79` + `AiServiceFacade:657-668` | Agent 多轮 LLM 调用 `setLastTokenUsage` 覆盖写，消息 tokenUsage 仅记最后一次 | 中 |
| S2-B-A | `RerankService:86` | 专用 Rerank API 失败兜底误用 RERANK 模型当 ChatModel，污染缓存且排序退化 | 中 |
| S2-C-4 | `McpClientManager:229-252` | `checkToolHealth` 不持 per-toolId 锁，与 `getOrCreateClient` 并发会关闭正在使用的客户端 | 中 |
| S2-C-5 | `TongYiWanxImageService:65-67` | InputStream 未 try-with-resources，异常泄漏 | 中 |
| S2-C-6 | `TongYiWanxImageService:42,73-76` + `AiImageController:70-81` | tempUrl 为 null 时静默返回，Controller 仍落库返回"成功" | 中 |
| S2-C-7 | `AiImageController:100-105` | `remove` 无用户归属校验，同租户内可删他人记录 | 中 |
| S2-C-8 | `WebSearchAgent:20-25` | 系统提示要求"优先用 ERP 数据工具"但该 Agent 未注入 ERP 工具 | 中 |
| S2-C-9 | `McpMarketController:93-101` | `getMarketTools` 缺 `@SaCheckPermission`，任意登录用户可查敏感 tool_metadata | 中 |

### 3.4 待修 — 低优先级

S2-A-7（regexFallback 字段顺序）、S2-A-8（assignCommunityIds N+1）、S2-A-9（KnowledgeGraphTool 2 跳查询无上限）、S2-B-F（updateLatestAssistantMessage 跨轮污染）、S2-B-G（Rerank URL 尾斜杠）、S2-B-H（ZhiPu JWT apiKey 切分脆弱）、S2-B-I（HttpResponse 未关闭）、S2-B-J（ThreadLocal 跨请求残留）、S2-B-K（MiMo providerType 取值）、S2-C-10（refreshMarketTools 无大小上限）、S2-C-11（WebSearchTool 每次 new ObjectMapper）、S2-C-12（String.intern 锁）。

### 3.5 已确认正确的点（非 Bug，反向核对）

- ZhiPu 用 `.model()` 非 `.modelName()`（符合 langchain4j-community-zhipu-ai builder API）
- `ChatModelFactory` 缓存 key 含 T/F 维度，evict 前缀清理不误伤（`"10:T".startsWith("1:")` 为 false）
- `AbstractHttpRerankProvider:93` 有 index 越界检查
- ZhiPu Rerank 手写 JWT 签名算法/header/payload/毫秒级 exp/base64url 与官方规范一致
- 思考 token 计入 output_tokens 成本
- `AI_THINKING` vs `AI_THINKING_TOKEN` 事件区分正确
- `thinking_content` 持久化正确
- KG 社区/全局检索的空结果/边界均有兜底
- `KnowledgeGraphTool` 按 graphId/knowledgeId 全局唯一 ID 查询，无跨租户泄漏

---

## 四、运行时验证待办（需配齐环境执行）

以下 Task 依赖数据库/Redis/真实 LLM，无法在纯代码层完成，需在本机配齐环境后执行：

### 4.1 Task 1：落库 + 启动

1. 按顺序执行 4 份 SQL（注意 ALTER 非幂等，执行前查 `information_schema`，详见 `Week12_Plan_S2D.md` 2.1）：
   - `doc/ai/sql/archive/ai-stage2-a.sql`（4 表 + chunk ALTER 5 列 + 图谱菜单）
   - `doc/ai/sql/archive/ai-stage2-b.sql`（ai_message.thinking_content ALTER）
   - `doc/ai/sql/archive/ai-stage2-c.sql`（2 表 + MCP 市场菜单）
   - `doc/ai/sql/archive/ai-stage2-image.sql`（1 表 + 文生图菜单）
2. 落库后 `SHOW TABLES LIKE` / `DESC` 校验 8 张新表 + 2 处 ALTER 就位。
3. `mvn spring-boot:run -P local` 启动，确认无 Bean 装配异常、`Neo4jGraphStoreStrategy` 桩不被加载、前端 AI 菜单可见。

### 4.2 Task 2：S2-A 端到端

- 配 `nexus.ai.knowledge-graph.extraction-model-id` 指向可用 ChatModel
- 上传文档 → 构建图谱 → 验证 `ai_kg_entity`/`ai_kg_relation`/`ai_kg_community` 落库且 `tenant_id='000000'`（**重点验证本轮 Task 5 修复**）
- 验证本轮修复的 #1/#2/#3：构造含 null confidence 的抽取结果、超长 description 文档，确认不再卡 "1"

### 4.3 Task 4：S2-B/C 回归

- 8 厂商各跑 1 轮（重点验 DashScope/ZhiPu 原生 SDK）
- 推理 token 流（Chat/RAG）
- 专用 Rerank vs LLM-as-judge
- 联网搜索（需配 `web-search.api-key`）+ MCP 市场一键加载 + 文生图（需配 IMAGE 模型 + OSS）

### 4.4 Task 6：基线采集

验证过程中填 `Week12_Plan_S2D.md` 7.1 的基线表（单 chunk 抽取 token、社区摘要 token、各模式对话 token、构建耗时、邻居查询延迟、Rerank 延迟）。

---

## 五、修复汇总

| 文件 | 修改 | 编译 |
|------|------|------|
| `KnowledgeProcessService.java` | 2 处异步闭包加 `TenantHelper.dynamic` + import | ✅ |
| `AiAbTestController.java` | execute 异步闭包加 `TenantHelper.dynamic` + import | ✅ |
| `AiServiceFacade.java` | 入口透传 tenantIdStr + 4 invoke 方法签名 + 2 save 方法 + 2 insert 包 dynamic + 流式 token 日统计补写 + import | ✅ |
| `KnowledgeGraphService.java` | filterAndCap null 安全排序 + extractChunk 后处理 try-catch + description 截断 | ✅ |
| `WebSearchTool.java` | crawlWebpage 新增 `sanitizeUrl` SSRF 防护（协议白名单 + 拒绝私网段） | ✅ |
| `DashScopeServiceProvider.java` | streaming + 同步补 `.listeners`/`.temperature(Float)`/`.maxTokens` | ✅ |
| `ZhiPuServiceProvider.java` | streaming + 同步补 `.listeners`/`.temperature(Double)`（ZhiPu 无 maxTokens） | ✅ |
| `AiTokenUsageMapper.java` | 新增 `@Insert ... ON DUPLICATE KEY UPDATE` 原子 upsert | ✅ |
| `AiTokenUsageServiceImpl.java` | `incrementDailyUsage` 改调原子 upsert + `IdWorker.getId()` | ✅ |
| `AiProviderServiceImpl.java` | 新增 `AiProviderChangeEvent`，update/delete 发布事件 | ✅ |
| `AiModelChangeListener.java` | 新增 `onProviderChange` 监听调 `evictAllCache` | ✅ |
| `McpMarketServiceImpl.java` | LOCAL 分支改用 `buildLocalConfig`（package→npx 合成） | ✅ |
| `McpClientManager.java` | `refreshClient` 清三个熔断状态 Map | ✅ |

**累计**：13 文件，全部 BUILD SUCCESS。

---

## 六、进入 Phase 3 的前置条件

1. ⬜ 第四节运行时验证待办至少跑通 Task 1（落库+启动）+ Task 2（S2-A 端到端，确认 tenant_id 正确 + 图谱构建不再静默失败）
2. ✅ 第三节 3.2 高优先级 Bug 已全部修复（含 S2-B-E1 token 统计、S2-C-1 SSRF 安全）——可带修复进 Phase 3，剩余中低优先级 Bug 见 3.3/3.4，非阻塞
3. Phase 3 引入 MES 跨库 `@DS("mes")`，主计划 6.1 已明确**不在 Tool 里 `DynamicDataSourceContextHolder.push`**，注入 MES Service 由其内部 `@DS` 访问；本轮 Task 5 的 `TenantHelper.dynamic` 范式对 Phase 3 跨库异步路径同样适用
