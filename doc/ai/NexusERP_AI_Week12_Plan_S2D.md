# NexusERP AI 模块 — 第十二周开发计划（S2-D 平台稳定化与运行时验证）

> **阶段**：二阶段收尾 — S2-D 平台稳定化与运行时验证
> **周次**：第 12 周
> **日期**：2026-06-30 ~ 2026-07-04
> **前置依赖**：S2-A / S2-B / S2-C 代码已全部完成并编译通过（后端 BUILD SUCCESS / 前端 vue-tsc 零新增错误）
> **产出形态**：本周以**验证 + 修复**为主，非新功能开发。产出为「S2-D 运行时验证报告 + 修复清单 + 成本/性能基线 + 文档收尾」。

---

## 一、本周目标与定位

### 1.1 为什么需要 S2-D

二阶段（S2-A/B/C）共 66 人天、加上一阶段累计约 204 人天的代码，截至本周**几乎全部只过了"编译关"，未过"运行关"**：

- S2-A 知识图谱：README 与二阶段计划均自标"⬜ 待运行时验证"，`doc/ai/sql/ai-stage2-a.sql` 至今未落库跑通。
- S2-B 推理 + 厂商 + Rerank：仅在 Chat/RAG 模式做过推理 token 流验证，Agent 模式明确不支持 thinking 流（设计限制），8 厂商切换与缓存 key 维度无回归记录。
- S2-C 联网 + 市场 + 文生图：代码审查修过 2 个 Bug（MCP 加载状态值、crawlWebpage 超时），无端到端回归记录。

本仓库已吃过两次"编译通过但运行有坑"的亏（`aiExecutor` 异步写库 `tenant_id` 落 0、JS 雪花 ID 精度截断）。在尚未跑稳的地基上直接叠 Phase 3（MES 跨库 `@DS("mes")` + 新 Agent），风险是被放大而非被控制。因此本周作为二阶段与 Phase 3 之间的**稳定化缓冲**，把已建成的 204 人天跑稳、跑出基线。

### 1.2 与现有计划的关系

- 本周**不新增任何功能表/实体**，不改变主计划 Phase 3-6 的排期（仍为第 23 周起）。
- 本周完成后，S2-A "待运行时验证"标记可闭合，二阶段正式收尾，再进入 Phase 3。
- 本周产出的成本/延迟基线，将作为 Phase 3 MES 赋能上线后的回归对比参照。

### 1.3 总体验收标准

| # | 验收项 | 通过标准 |
|---|--------|---------|
| 1 | 数据库落库 | 4 份二阶段 SQL 在干净库与已执行库上均能正确执行（幂等） |
| 2 | 应用启动 | `DromaraApplication` 正常启动，无 AI Bean 缺失/循环依赖报错 |
| 3 | S2-A 端到端 | 上传文档 → 抽取实体/关系 → 社区检测/摘要 → 局部/全局检索 → `KnowledgeGraphTool` 被 Agent 调用，全链路无异常且 `tenant_id` 正确 |
| 4 | S2-B 回归 | 8 厂商各跑通 1 轮对话；推理 token 流在 Chat/RAG 正常；缓存 key 含 T/F 维度不串；专用 Rerank 命中率 ≥ LLM-as-judge |
| 5 | S2-C 回归 | 联网搜索带来源卡；MCP 市场一键加载默认 DISABLED；文生图 OSS 持久化 + 历史记录可查 |
| 6 | 异步租户审计 | 所有 `aiExecutor` 异步写库路径 `tenant_id` 不丢失（以 `KnowledgeGraphService` 为正确范例） |
| 7 | 成本/性能基线 | `ai_token_usage` 采集到本轮验证的真实数据，输出成本与延迟基线表 |
| 8 | 文档收尾 | README「已知问题」6 条逐条处置；补 Phase 2 / 二阶段收尾说明 |

---

## 二、Task 1：数据库落库 + 应用启动（P0）

> **目标**：把 4 份二阶段 SQL 落到 `ry-vue` 库，启动应用确认 AI 模块 Bean 装配无误。

### 2.1 SQL 执行（顺序敏感）

在 `ry-vue` 库按以下顺序执行。**注意幂等性差异**：

| 顺序 | 脚本 | 内容 | 幂等性 |
|------|------|------|--------|
| 1 | `doc/ai/sql/ai-stage2-a.sql` | 4 张表（`ai_knowledge_graph`/`ai_kg_entity`/`ai_kg_relation`/`ai_kg_community`）+ `ai_knowledge_chunk` ALTER 5 列 + 图谱菜单 4 条 | ✅ 表 `IF NOT EXISTS`；⚠️ **ALTER 非幂等**；菜单 `DELETE+INSERT` 幂等 |
| 2 | `doc/ai/sql/ai-stage2-b.sql` | `ai_message` ALTER `thinking_content` 1 列 | ⚠️ **ALTER 非幂等** |
| 3 | `doc/ai/sql/ai-stage2-c.sql` | 2 张表（`ai_mcp_market`/`ai_mcp_market_tool`）+ MCP 市场菜单 5 条 | ✅ 表 `IF NOT EXISTS`；⚠️ 菜单 INSERT **非幂等**（无 DELETE 前置） |
| 4 | `doc/ai/sql/ai-stage2-image.sql` | 1 张表（`ai_image_record`）+ 文生图菜单 4 条 | ✅ 表 `IF NOT EXISTS`；⚠️ 菜单 INSERT **非幂等** |

**执行前必查**（避免重复执行报错）：

```sql
-- 若库已执行过，先确认列是否已存在
SELECT COLUMN_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_knowledge_chunk'
    AND COLUMN_NAME IN ('extraction_status','entity_count','relation_count','token_used','kg_error_message');

SELECT COLUMN_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_message' AND COLUMN_NAME = 'thinking_content';

-- 菜单幂等检查（S2-C/S2-image 的 INSERT 无 DELETE 前置，重复执行会主键冲突）
SELECT menu_id FROM sys_menu WHERE menu_id IN (1800,1801,1802,1803,1804,1810,1811,1812,1813);
```

> **执行注意**：
> - 若 `ai_knowledge_chunk` / `ai_message` 的 ALTER 列已存在，跳过对应 ALTER 语句即可，不影响其余脚本。
> - S2-C / S2-image 的菜单 INSERT 若已存在 `menu_id`，先 `DELETE FROM sys_menu WHERE menu_id IN (...)` 再执行，或直接跳过菜单部分。
> - 全部脚本默认 `tenant_id='000000'`，多租户环境下表结构与租户无关，无需按租户执行。

### 2.2 落库后校验

```sql
-- 期望：8 张二阶段新表 + 2 处 ALTER 全部就位
SHOW TABLES LIKE 'ai_kg_%';          -- 期望 ai_kg_community / ai_kg_entity / ai_kg_relation / ai_knowledge_graph
SHOW TABLES LIKE 'ai_mcp_market%';  -- 期望 ai_mcp_market / ai_mcp_market_tool
SHOW TABLES LIKE 'ai_image_record';  -- 期望 ai_image_record
DESC ai_knowledge_chunk;             -- 期望含 extraction_status / entity_count / relation_count / token_used / kg_error_message
DESC ai_message;                      -- 期望含 thinking_content
```

### 2.3 应用启动校验

```bash
mvn spring-boot:run -P local
# 或 IDE 运行 DromaraApplication
```

**通过标准**：
- 启动日志无 `BeanCreationException`、`UnsatisfiedDependencyException`、`NoSuchBeanDefinitionException`。
- `AiProperties` 绑定 `nexus.ai.*` 成功（日志可见 `AiThreadPoolConfig` / `AiConfiguration` 装配）。
- `Neo4jGraphStoreStrategy` 因 `@ConditionalOnProperty storage=neo4j` **不被加载**（默认 `mysql`），确认其桩实现不干扰启动。
- 前端 `npm run dev` 启动后，AI 菜单（知识库/图谱/MCP 市场/文生图/模型管理）均可见。

**常见问题预案**：

| 现象 | 排查方向 |
|------|---------|
| 启动报 `Neo4jGraphStoreStrategy` 相关异常 | 确认 `nexus.ai.knowledge-graph.storage=mysql`（默认值），该 Bean 不应装配 |
| 表/列不存在异常 | 回到 2.2 逐项核对，确认 ALTER 是否真执行 |
| 菜单不显示 | 检查 `sys_menu` 的 `menu_id` 是否与 `sys_role_menu` 关联；图谱按钮为知识库详情页内 Tab，不单独占菜单 |
| 前端 404 `/ai/...` | 确认 `AiChatController` 等 Controller 被 Sa-Token 放行（白名单或鉴权） |

---

## 三、Task 2：S2-A 知识图谱端到端验证（P0）

> **目标**：验证二阶段工时最大（26 人天）、创新性最高、最无参照可抄的 S2-A 全链路。这是本周重点。

### 3.1 前置配置

```yaml
nexus:
  ai:
    knowledge-graph:
      enabled: true
      storage: mysql              # 确认非 neo4j
      batch-size: 5
      max-entities-per-chunk: 20
      max-relations-per-chunk: 30
      confidence-threshold: 0.5
      extraction-model-id: 1      # 确认该 model_id 指向一个可用的 ChatModel
      max-neighbors: 20
      label-propagation-max-iter: 8
      max-communities: 50
      gleaning-enabled: false     # v1.5 预留，保持 false
```

- **抽取模型**：在「模型管理」页确认 `extraction-model-id` 指向的 `ai_model` 记录可用（API Key 有效、`model_type=CHAT`）。建议用便宜模型（如 `deepseek-chat`）控制抽取成本。
- **向量库**：`nexus.ai.vector-store.type` 默认 `inmemory`。**注意**：inmemory 重启即丢，且图谱检索依赖 chunk 的向量召回。验证 S2-A 时**务必在同一次应用会话内**完成"导入文档 → 构建图谱 → 检索问答"全流程；若需跨会话持久化，配置 Milvus（`type: milvus` + 可达的 `milvus.url`）。

### 3.2 全链路验证步骤

**Step 1 — 导入文档**（RAG 基础链路）：
- 上传 1 份含明确实体关系的 ERP 业务文档（如物料/供应商/客户清单、BOM 说明）。
- 确认 `ai_knowledge_document` / `ai_knowledge_chunk` 生成记录，chunk 的 `vector_id` 非空（inmemory 模式下向量在内存）。

**Step 2 — 构建图谱**：
- 知识库详情页「知识图谱」Tab → 点击「构建」。
- 观察 `ai_knowledge_graph.graph_status`：`0未构建 → 1构建中 → 2已完成`（失败为 `3`，查 `error_message`）。
- 观察 `ai_knowledge_chunk.extraction_status`：逐 chunk 从 `0 → 1 → 2`，失败的为 `3`（查 `kg_error_message`）。

**Step 3 — 验证抽取结果**：

```sql
-- 实体/关系是否落库，且 tenant_id 正确（不为 0 / 不为 null）
SELECT graph_id, COUNT(*) AS entity_cnt FROM ai_kg_entity WHERE tenant_id='000000' GROUP BY graph_id;
SELECT graph_id, COUNT(*) AS relation_cnt FROM ai_kg_relation WHERE tenant_id='000000' GROUP BY graph_id;

-- 同名实体是否合并（mention_count > 1 的记录应存在）
SELECT entity_name, mention_count FROM ai_kg_entity WHERE mention_count > 1;

-- 社区检测是否产出
SELECT graph_id, COUNT(*) AS community_cnt FROM ai_kg_community WHERE summary_status='2' GROUP BY graph_id;
```

**通过标准**：
- `ai_kg_entity` / `ai_kg_relation` 有数据，且 `tenant_id` 等于当前租户（**不为 0**——这是异步租户坑的观测点）。
- 同名实体合并生效（`mention_count > 1`）。
- 社区表有记录且 `summary_status='2'`（社区摘要生成成功）。

**Step 4 — 检索增强验证**：
- 向该知识库提问一个**需要关系链才能回答**的问题（如"物料 M001 的供应商还有哪些客户"）。
- 确认 `RetrieverService` 三路召回（向量 + 关键词 + 图谱）均触发，日志可见 `performGraphRecall` 命中实体。

**Step 5 — Agent 图谱查询验证**：
- Agent 模式下提问，确认 `KnowledgeGraphTool` 被调用（前端工具调用卡片可见，或日志可见 `KnowledgeGraphTool` 执行）。
- 确认 `AiRequestContext.getKnowledgeId()` 在 Agent 线程中可读（限定图谱范围）。

### 3.3 S2-A 风险点重点验证

| 风险点 | 验证方法 | 通过标准 |
|--------|---------|---------|
| LLM 抽取 JSON 格式不稳 | 故意上传含表格/格式混乱的文档 | JSON 解析失败重试 1 次后成功，或兜底正则提取；chunk 不卡在 `1处理中` |
| 同名实体合并误判 | 上传含同名不同类型实体（如"客户A"与"供应商A"） | type + description 相似度判断，不确定时保留独立实体 |
| 抽取 token 成本高 | 观察 `ai_knowledge_chunk.token_used` 与 `ai_token_usage` | 单 chunk 抽取 token 在预期范围；批次处理生效 |
| MySQL 图查询性能 | 大图谱（实体 > 1000）下做邻居查询 | 1 跳邻居查询 < 500ms；超时则评估是否触发 Neo4j v2 需求 |
| 异步 `tenant_id` 丢失 | 见 Task 5，此处先观测 `ai_kg_entity.tenant_id` | 全部 = 当前租户，无 0 值 |

### 3.4 Neo4j v2 评估（非必做）

在验证过程中，若 MySQL 图谱邻居查询性能不达预期（实体规模 > 1000 且 1 跳查询 > 500ms），记录数据量与延迟，作为 `Neo4jGraphStoreStrategy` v2 实现的优先级依据。否则 v2 维持预留状态。

---

## 四、Task 3：S2-B 推理 + 厂商 + Rerank 回归（P0）

### 4.1 前置配置

- 「模型管理」页配置至少 4 个厂商的可用模型（建议 DeepSeek + DashScope + ZhiPu + OpenAI 兼容的任一），填入真实 API Key 与定价（`input_price_per_million` / `output_price_per_million`）。
- 若验证专用 Rerank，配置 1 条 `model_type=RERANK` 的模型（`provider_type` 为 DASHSCOPE/ZHIPU/SILICONFLOW 之一，如 `BAAI/bge-reranker-v2-m3`）。

### 4.2 验证步骤

**Step 1 — 8 厂商切换**：

| 厂商 | providerType | 验证点 |
|------|-------------|--------|
| OpenAI | OPENAI | 基线，流式正常 |
| DeepSeek | DEEPSEEK | OpenAI 兼容 + thinking |
| DashScope | DASHSCOPE | **原生 SDK**（`QwenStreamingChatModel`） |
| ZhiPu | ZHIPU | **原生 SDK**，注意 `.model()` 非 `.modelName()` |
| Minimax | MINIMAX | OpenAI 兼容 |
| MiMo | XIAOMI | OpenAI 兼容 |
| CustomApi | CUSTOM_API | URL 规范化生效 |
| Ollama | OLLAMA | 本地模型（若有） |

逐个切换默认模型，各发 1 条普通对话，确认流式回复正常、无厂商 builder 误用报错。

**通过标准**：8 厂商（或有 API Key 的子集）全部流式回复成功，`AiChatModelListener` 采集到 `input_tokens`/`output_tokens`。

**Step 2 — 推理 token 流**：
- 对话输入框开启「深度思考」开关（`enableThinking=true`）。
- Chat 模式：确认 `AI_THINKING_TOKEN` 事件到达前端，思考过程折叠展示。
- RAG 模式：同上。
- Agent 模式：确认**不支持** thinking 流（设计限制，使用同步 ChatModel），记录此约束。

**通过标准**：Chat/RAG 模式思考 token 实时展示；`ai_message.thinking_content` 持久化思考过程。

**Step 3 — 缓存 key 维度**：
- 同一模型先以 `enableThinking=false` 对话，再以 `enableThinking=true` 对话。
- 确认 `ChatModelFactory` 缓存 key 为 `modelId:F` 与 `modelId:T`，两实例不串用。
- 验证 `AiModelChangeListener` 在模型配置变更时 `evictCache` 生效（修改模型 API Key 后，新对话使用新配置）。

**Step 4 — 专用 Rerank vs LLM-as-judge**：
- 对同一知识库同一问题，分别用 LLM-as-judge（无 Rerank 模型配置）与专用 Rerank（配置 Rerank 模型）检索。
- 对比 Top-K 命中率与延迟。

**通过标准**：专用 Rerank 延迟 < LLM-as-judge，且命中率 ≥ LLM-as-judge；无配置 Rerank 模型时自动兜底 LLM-as-judge。

### 4.3 常见问题预案

| 现象 | 排查方向 |
|------|---------|
| ZhiPu 报模型名无效 | 确认 `ZhiPuServiceProvider` 用 `.model()` 而非 `.modelName()`（已知坑） |
| thinking 模式串用非 thinking 回复 | 缓存 key 未含 T/F 维度，查 `ChatModelFactory.getStreamingChatModel` |
| 思考 token 未计费 | 确认 `onPartialThinking` 的 token 计入 `ai_token_usage`（input_tokens） |
| 厂商切换后旧模型仍被调用 | `AiModelChangeListener` 的 evict 未覆盖 streaming + 同步两套缓存 |

---

## 五、Task 4：S2-C 联网 + 市场 + 文生图回归（P0）

### 5.1 联网搜索

**前置**：`nexus.ai.web-search.api-key` 默认为空 `${WEB_SEARCH_API_KEY:}`，**必须配真实 Tavily API Key**（或切 `provider: bing` 配 Bing key），否则联网开关无效。

```yaml
nexus:
  ai:
    web-search:
      enabled: true
      provider: tavily
      api-key: <真实 key>      # 验证前必填
      top-n: 5
      timeout-ms: 15000
```

**验证步骤**：
- 对话开启「联网」开关，提问一个需最新信息的问题（如"今天的新闻"或某实时数据）。
- 确认 `WebSearchAgent` 被路由（auto 模式或显式 `enableWebSearch=true`）。
- 确认 `AI_WEB_SEARCH` 来源卡到达前端，含可点击链接。
- 验证 `crawlWebpage` 抓取正文：超时防护生效（`timeout-ms`），白名单域名限制生效。

**通过标准**：联网问答带来源链接；非联网问题不被误路由到 WebSearchAgent（auto 路由关键词判断合理）。

### 5.2 MCP 市场

**验证步骤**：
- 「MCP 市场」页新增一个市场源（URL 返回工具列表 JSON）。
- 点击「刷新」拉取工具列表，确认 `ai_mcp_market_tool` 生成记录。
- 点击「一键加载」，确认物化一条 `ai_tool` 记录，`ai_mcp_market_tool.is_loaded='1'`、`local_tool_id` 回填。
- **确认加载后的工具默认 `status=DISABLED`**（安全设计，需管理员手动启用 + 测试连接）。

**通过标准**：市场源 CRUD + 刷新 + 一键加载全通；加载物化的工具出现在 MCP 工具管理页（`mcp/index.vue`）；默认 DISABLED 生效。

**安全验证**：刷新不可信 URL 时，超时 30s + 响应大小上限 + JSON 校验生效，不抛未捕获异常。

### 5.3 文生图

**前置**：
- 「模型管理」页配置 1 条 `model_type=IMAGE` 的模型（厂商通义万相 + dashscope API Key）。
- 确认 OSS 配置可用（`ruoyi-common-oss`），图片需持久化到 OSS。

**验证步骤**：
- 「文生图」菜单页输入提示词，生成图片。
- 确认 `ai_image_record` 生成记录，`image_url` 指向 OSS（非通义万相临时 URL）。
- 确认 OSS 失败时兜底返回通义万相临时 URL（`TongYiWanxImageService` 兜底逻辑）。
- 历史查询、删除功能可用。

**通过标准**：图片生成 + OSS 持久化 + 历史记录 CRUD 全通。

---

## 六、Task 5：异步写库租户上下文审计（P0）

> **背景**：`aiExecutor` 异步线程中 Sa-Token ThreadLocal 不传播，多租户拦截器需 `TenantHelper.dynamic` 恢复，否则 insert 时 `tenant_id` 不被填充（落 0）。
>
> **正面范例**：`KnowledgeGraphService.buildGraph` 已正确处理（`KnowledgeGraphService.java:79-80`）：
> ```java
> // 异步线程恢复租户上下文（Sa-Token ThreadLocal 不跨线程，多租户拦截器需 TenantHelper.dynamic 恢复，
> // 否则 insert 时 tenant_id 不被填充）
> TenantHelper.dynamic(tenantId, () -> { ... });
> ```
> 本 Task 以此为基准，审计其余 `aiExecutor` 异步**写库**路径是否同样恢复了租户上下文。

### 6.1 审计清单（全部 aiExecutor 调用点）

经 grep，`aiExecutor.execute(...)` / `CompletableFuture.supplyAsync(..., aiExecutor)` 共 7 个文件约 12 处。按"是否写库 + 是否已恢复租户"分类审计：

| 文件 | 行号 | 是否写库 | 已恢复租户? | 审计结论 |
|------|------|---------|------------|---------|
| `KnowledgeGraphService` | 77, 363 | 是（kg 表） | ✅ 已恢复 | 正面范例，无需改 |
| `KnowledgeProcessService` | 87, 207 | 是（document/chunk/vector） | ⬜ 待审计 | **重点**：知识库导入/分块/embedding 异步写库 |
| `AiServiceFacade` | 275, 365, 481, 525, 860 | 是（ai_message/ai_token_usage） | ⬜ 待审计 | **重点**：5 条对话路径的 token 落库 |
| `AiAbTestController` | 166 | 是（ai_ab_test_result） | ⬜ 待审计 | AB 测试结果异步落库 |
| `RerankService` | 145 | 否（只读打分） | 不适用 | 租户无关，跳过 |

### 6.2 审计方法

对每个写库调用点：

1. **代码审查**：检查 `aiExecutor.execute(() -> {...})` 闭包内，是否在写库操作外包了 `TenantHelper.dynamic(tenantId, () -> {...})`。
2. **运行时验证**：在对应功能触发异步写库后，查表确认 `tenant_id`：
   ```sql
   -- 异步写入的记录 tenant_id 应等于当前租户，不应为 0 / null
   SELECT tenant_id, COUNT(*) FROM ai_knowledge_chunk   GROUP BY tenant_id;  -- KnowledgeProcessService
   SELECT tenant_id, COUNT(*) FROM ai_message            GROUP BY tenant_id;  -- AiServiceFacade
   SELECT tenant_id, COUNT(*) FROM ai_token_usage        GROUP BY tenant_id;  -- AiServiceFacade
   SELECT tenant_id, COUNT(*) FROM ai_ab_test_result     GROUP BY tenant_id;  -- AiAbTestController
   ```

### 6.3 修复规范

发现 `tenant_id` 落 0 / null 的路径，按 `KnowledgeGraphService` 范式修复：

```java
aiExecutor.execute(() -> {
    try {
        // 从请求线程捕获 tenantId（AiRequestContext 或 LoginHelper 在提交前读取）
        Long tenantId = AiRequestContext.getTenantId();
        TenantHelper.dynamic(tenantId, () -> {
            // 原异步写库逻辑
            return null;
        });
    } finally {
        AiRequestContext.clear();  // 避免内存泄漏
    }
});
```

> **关键**：
> - `tenantId` 必须在**提交异步任务前**（请求线程中）捕获，提交到 `AiRequestContext`，异步线程内读取——不能在异步线程内调 `LoginHelper.getTenantId()`（已丢失）。
> - `AiRequestContext` 用 `InheritableThreadLocal`，但 `aiExecutor` 是线程池复用线程，**继承不保证跨任务**，仍需显式 `set` + `clear`。
> - 任务结束必须 `AiRequestContext.clear()`，防止线程复用导致上下文串用。

### 6.4 通过标准

- 4 条写库路径（KnowledgeProcessService / AiServiceFacade / AiAbTestController，KnowledgeGraphService 已通过）的异步写入记录 `tenant_id` 全部正确。
- 若发现未恢复，修复后回归对应功能，确认 `tenant_id` 正确。

---

## 七、Task 6：成本与性能基线采集（P1）

> **目标**：利用 Week 11 已建好的 `ai_token_usage` 体系，在验证过程中采真实数据，定二阶段成本/延迟基线，供 Phase 3 对比。

### 7.1 采集内容

在 Task 2-5 验证过程中，记录以下基线（同一抽取/对话模型、同一文档规模下）：

| 指标 | 采集来源 | 基线值 |
|------|---------|--------|
| 单 chunk 图谱抽取 token | `ai_knowledge_chunk.token_used` 均值 | ___ |
| 单社区摘要 token | `ai_kg_community.token_used` 均值 | ___ |
| 单轮普通对话 token | `ai_token_usage`（CHAT 模型） | ___ |
| 单轮 RAG 对话 token（含检索+生成） | `ai_token_usage` | ___ |
| 单轮 Agent 对话 token（含工具调用） | `ai_token_usage` | ___ |
| 推理模式 token 增量 | thinking on/off 的 input_tokens 差 | ___ |
| 图谱构建总耗时（100 chunk 规模） | `graph_status` 时间戳 | ___ |
| MySQL 图谱邻居查询延迟 | 日志 / SQL 执行计划 | ___ |
| 专用 Rerank vs LLM-as-judge 延迟 | `RerankService` 日志 | ___ |

### 7.2 成本核算

在「模型管理」页为验证所用模型配置定价后，访问「Token 用量统计」页（`/ai/tokenUsage`），确认：
- `summary` / `trend` / `model-ranking` / `user-ranking` 端点返回正确数据。
- 成本计算 `calculateCost` 与厂商账单量级一致。

**通过标准**：`ai_token_usage` 采集到本轮验证全部调用数据；输出一张基线表（填入 7.1 的 `___`），作为 Phase 3 回归对比基准。

---

## 八、Task 7：文档收尾与状态对齐（P1）

> **目标**：闭合 README「已知问题」6 条，补 Phase 2 / 二阶段收尾说明，使文档状态与实际一致。

### 8.1 README「已知问题」处置

README 第 35-42 行列出 6 条已知问题，本周逐条处置：

| # | 问题 | 处置 |
|---|------|------|
| 1 | Week 1 计划缺失 | 标注 Week 1 = QuickStart_Phase0（已在 README 阅读顺序隐含，显式补关联） |
| 2 | 路径不一致 | 统一为 `D:\workspace\nexus-erp`，删除 `E:\Nexus` / `E:\ruoyi-ai` 历史路径引用（或标注为参照项目旧路径） |
| 3 | 状态标记不统一 | Week 2-9 补齐状态标记与日期（按实际完成情况标注 ✅/—） |
| 4 | 内容重复 | QuickStart 与主计划 Phase 0 重叠，标注 QuickStart 为"快速上手版"，主计划 Phase 0 为"完整版"，消除双重维护 |
| 5 | 跨周问题不闭环 | Week 5 二次审查 6 个 Critical 处置记录补到 Week 6；Week 7/8 前端欠债由 Week 9 闭合的记录补齐 |
| 6 | Phase 2 收尾缺失 | 本周（Week 12）即 Phase 2 + 二阶段收尾，产出本计划 + 验证报告 |

### 8.2 二阶段收尾文档

- 在 README「二阶段实施记录」下新增 S2-D 条目，记录验证结论与修复清单。
- 更新二阶段计划 `NexusERP_AI二阶段开发计划_AI平台能力深化.md` 第十节排期表：S2-A "待运行时验证" → "✅ 已验证"；新增 S2-D 行。
- 更新主计划 `NexusERP_AI开发计划.md` 累计人天（+S2-D 验证人天）与 Phase 3 起始周确认。

### 8.3 内存更新

- 更新 `ai-s2a-knowledge-graph.md`：S2-A 运行时验证结论（待验证 → 已验证/已修复）；`ai-stage2-a.sql` 落库状态。
- 更新 `ai-async-tenant-context.md`：审计结论（哪些路径已正确、哪些本周修复）。
- 新增 `ai-s2d-stabilization.md`：本周验证基线与修复清单索引。

---

## 九、风险评估

| 风险 | 影响 | 应对 |
|------|------|------|
| ALTER 非幂等，重复执行报错 | 阻塞落库 | 执行前 `information_schema` 查列是否存在，已存在则跳过（见 2.1） |
| inmemory 向量库重启丢失 | S2-A 跨会话验证失败 | 同一会话内完成全流程；或切 Milvus（见 3.1） |
| 联网搜索 API Key 缺失 | S2-C 联网验证无法进行 | 验证前必填 Tavily/Bing key；无 key 则该项标注"待配置后验证" |
| 文生图 OSS 不可用 | 图片持久化失败 | 确认 `ruoyi-common-oss` 配置；失败时验证兜底临时 URL 逻辑 |
| LLM 抽取 JSON 不稳 | 图谱构建卡 chunk | Prompt 重试 1 次 + 兜底正则；失败的 chunk 标 `3失败` 不阻塞整体 |
| 异步租户未恢复导致 `tenant_id` 落 0 | 多租户数据串库 | Task 5 重点审计，按 `KnowledgeGraphService` 范式修复 |
| 8 厂商无可用 API Key | 厂商回归不全 | 有 key 的子集验证，无 key 的标注"待配置" |
| 验证发现 Bug 数超预期 | 本周 5 天不够 | P0 阻塞性 Bug 本周修；非阻塞 Bug 记录入 Phase 3 前置清单，不阻塞收尾 |

---

## 十、文件变更预估

> 本周以验证为主，文件变更以"修复 Bug + 文档"为主。以下为预估，实际以验证发现为准。

| 类型 | 文件 | 变更 | 触发条件 |
|------|------|------|---------|
| **SQL** | `doc/ai/sql/` | 4 份脚本落库执行 | Task 1 |
| **修复** | `KnowledgeProcessService.java` | 异步写库补 `TenantHelper.dynamic` | Task 5 若发现 tenant 落 0 |
| **修复** | `AiServiceFacade.java` | 5 处异步路径补租户恢复 | Task 5 若发现 |
| **修复** | `AiAbTestController.java` | AB 结果异步落库补租户恢复 | Task 5 若发现 |
| **修复** | S2-A/B/C 各 Service | 验证发现的 Bug | Task 2-4 |
| **文档** | `doc/ai/README.md` | 已知问题 6 条处置 + S2-D 记录 | Task 7 |
| **文档** | `NexusERP_AI二阶段开发计划_AI平台能力深化.md` | 排期表更新 | Task 7 |
| **文档** | `NexusERP_AI开发计划.md` | 累计人天 + Phase 3 起始确认 | Task 7 |
| **报告** | `doc/ai/NexusERP_AI_S2D_验证报告.md`（新增） | 验证结论 + 基线 + 修复清单 | 全周 |
| **内存** | `~/.claude/.../memory/ai-s2a-knowledge-graph.md` | 验证结论更新 | Task 7 |
| **内存** | `~/.claude/.../memory/ai-async-tenant-context.md` | 审计结论更新 | Task 7 |
| **内存** | `~/.claude/.../memory/ai-s2d-stabilization.md`（新增） | 基线索引 | Task 7 |

---

## 十一、建议执行顺序

| 天 | 任务 | 产出 |
|----|------|------|
| Day 1 | Task 1（SQL 落库 + 启动校验）+ Task 5 代码审计（先静态审查 4 条写库路径） | 库就位、应用启动、审计初判 |
| Day 2 | Task 2（S2-A 全链路，重点）+ Task 5 运行时验证（观测 `tenant_id`） | S2-A 验证结论 + 异步租户修复 |
| Day 3 | Task 3（S2-B 厂商 + 推理 + Rerank 回归） | S2-B 回归结论 |
| Day 4 | Task 4（S2-C 联网 + 市场 + 文生图回归）+ Task 6（基线采集） | S2-C 回归结论 + 基线表 |
| Day 5 | Task 7（文档收尾）+ 验证报告整理 + 修复 Bug 回归 | S2-D 验证报告 + 文档闭合 |

---

## 十二、本周完成定义（Definition of Done）

1. 4 份二阶段 SQL 落库且应用正常启动。
2. S2-A 端到端验证通过（或发现的 Bug 已修且回归通过）。
3. S2-B / S2-C 回归通过（有 API Key 的子集）。
4. 异步写库租户审计完成，发现的 `tenant_id` 落 0 路径已修复。
5. 成本/性能基线表产出。
6. README「已知问题」6 条逐条处置，二阶段收尾文档就位。
7. 输出 `doc/ai/NexusERP_AI_S2D_验证报告.md`。

满足以上 7 条 → 二阶段正式闭合，可进入 Phase 3（MES 智能赋能，第 23 周起）。

> **进入 Phase 3 前的提醒**：Phase 3 引入 MES 独立数据源 `@DS("mes")`，主计划 6.1 已明确——**不要在 Tool 里 `DynamicDataSourceContextHolder.push("mes")`**（与多租户插件冲突），应注入 MES 模块 Service 由其内部 `@DS("mes")` 访问。本周 Task 5 确认的租户恢复范式，Phase 3 的跨库异步路径同样适用。
