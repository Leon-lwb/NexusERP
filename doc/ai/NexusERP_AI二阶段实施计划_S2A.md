# AI 二阶段实施计划 — S2-A 知识图谱（Knowledge Graph）开发文档

> **制定日期**：2026-06-30
> **实施依据**：`doc/ai/NexusERP_AI二阶段开发计划_AI平台能力深化.md` §4（依赖）/§5（S2-A 全节）/§8（DB 变更）/§9（前端）/§10（排期）/§11.1（风险）/§12（参照项目评估）
> **参照项目**：`D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat`（仅有空脚手架 `KnowledgeGraphInstance`/`KnowledgeGraphSegment`，无表/无抽取/无检索/无 Neo4j 连接，**不可复用**，全部自研）
> **格式参照**：`doc/ai/NexusERP_AI二阶段实施计划_S2B_S2C.md`
> **范围**：S2-A 全量已代码实现（后端 + 前端 + SQL），重新激活（此前主计划标记"暂搁"）。
> **状态**：✅ 代码完成并编译通过（后端 `mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-common/ruoyi-common-ai -am compile` BUILD SUCCESS；前端 `vue-tsc` 新文件零类型错误）/ ⬜ 待运行时验证（需执行 SQL + 起应用 + 配置 LLM 跑端到端）。
> **GraphRAG 富化来源**：参考 Microsoft GraphRAG（2024）「实体抽取→社区检测→社区摘要→local/global search」与 LightRAG「增量更新+实体消歧」范式。社区检测 v1 采用**自实现加权标签传播**（无新依赖），Leiden/Louvain 与多跳遍历延后 v2（Neo4j）。

---

## 一、实施概要

| 维度 | 内容 | 状态 |
|------|------|------|
| 存储 | MySQL 四张新表 + `ai_knowledge_chunk` ALTER + `ai_kg_entity` 加 `community_id`；Neo4j 经 `GraphStoreStrategy` 预留 v2 | ✅ |
| 抽取 | `GraphExtractionAgent`（LangChain4j AgenticServices，严格 JSON）+ `KnowledgeGraphService` 异步批量构建 + 同名实体合并 + 增量可重跑 + token 自动采集 | ✅ |
| 社区 | `LabelPropagationClustering`（自实现加权标签传播）+ `CommunityService`（分簇 → `ai_kg_community` 落库 → 每社区 LLM 摘要） | ✅ |
| 检索-局部 | `RetrieverService.hybridRetrieve` 增第三路 `performGraphRecall`，并入现有 RRF(K=60) `scoreMap`/`contentMap` | ✅ |
| 检索-全局 | `GlobalSearchService`：社区摘要 map-reduce 合成答案（GraphRAG global search） | ✅ |
| Agent 工具 | `KnowledgeGraphTool`（关系遍历 + 全局摘要）+ `KnowledgeGraphToolProvider` 注册进 `ai_tool`，供 Supervisor Agent 调用 | ✅ |
| 控制器 | `AiKnowledgeGraphController`（10 端点），权限 `ai:knowledge:graph:*` | ✅ |
| 前端 | `views/ai/knowledge/graph.vue`（KB 详情页「知识图谱」Tab）+ `api/ai/knowledgeGraph.ts`，echarts graph 可视化（按社区着色） | ✅ |
| 配置 | `nexus.ai.knowledge-graph` 配置块 | ✅ |
| 依赖 | **v1 零新依赖**（社区检测自实现、echarts 已就绪、Agent 复用 langchain4j-agentic）；Neo4j 驱动 v2 再加 | ✅ |
| 编译 | 后端 BUILD SUCCESS / 前端 vue-tsc 新文件零错误 | ✅ |
| 运行时 | 待执行 `ai-stage2-a.sql` + 起应用 + 配 LLM 跑端到端 | ⬜ |

**与主计划草案的差异**（用户在规划阶段选定，超出原 v1 范围）：
- 原 v1 仅局部检索（18 人天）；实际纳入 **社区检测 + 社区摘要 + 全局检索**（GraphRAG 风格）+ **Agent @Tool**，约 26 人天。
- 社区检测用**自实现加权标签传播**替代 Leiden（无新依赖）；Leiden/多层级延后 v2。
- 存储用 MySQL 优先，Neo4j 经 `GraphStoreStrategy` 接口预留 v2。

---

## 二、架构总览

### 2.1 模块分层

```
ruoyi-common/ruoyi-common-ai（共享层，跨模块可引用）
├─ domain/entity    AiKnowledgeGraph / AiKgEntity / AiKgRelation / AiKgCommunity（+改 AiKnowledgeChunk）
├─ mapper           4 个 BaseMapperPlus
├─ domain/bo|vo     4 组 BO/VO（+改 chunk BO/VO）
└─ service[.impl]   4 组 IAiXxxService + impl（CRUD 骨架）

ruoyi-modules/ruoyi-ai（业务层）
├─ agent/           GraphExtractionAgent（LangChain4j AgenticServices 接口）
├─ service/kg/
│  ├─ dto/             GraphExtractionResult（抽取 JSON 反序列化 DTO）
│  ├─ GraphExtractionParser    （剥代码块 → Jackson → 重试 → 正则兜底）
│  ├─ GraphStoreStrategy       （存储策略接口）
│  ├─ MysqlGraphStoreStrategy  （MySQL 实现，默认）
│  ├─ Neo4jGraphStoreStrategy  （v2 stub，抛 UnsupportedOperationException）
│  ├─ LabelPropagationClustering（自实现加权标签传播，纯 Java）
│  ├─ KnowledgeGraphService    （构建主服务：buildGraph/rebuild/deleteGraph/getProgress）
│  ├─ CommunityService         （社区检测 + 摘要）
│  └─ GlobalSearchService      （全局检索 map-reduce）
├─ controller/      AiKnowledgeGraphController（10 端点）
├─ tool/            KnowledgeGraphTool（@Tool：queryGraphRelation + globalSummary）
├─ mcp/registry/    KnowledgeGraphToolProvider（注册进 ai_tool）
├─ service/rag/     RetrieverService（改：增 performGraphRecall 第三路 RRF）
├─ context/         AiRequestContext（改：增 knowledgeId ThreadLocal）
├─ service/facade/  AiServiceFacade（改：Agent 异步块内 setKnowledgeId）
└─ config/          AiProperties（改：增 KnowledgeGraphConfig）

plus-ui（前端）
├─ api/ai/knowledgeGraph.ts
└─ views/ai/knowledge/graph.vue（+ index.vue 改 el-tabs 加 Tab）

doc/ai/sql/ai-stage2-a.sql（4 表 + 2 ALTER + sys_menu 1820-1823）
```

### 2.2 构建数据流（两阶段，异步）

```
POST /ai/knowledge/graph/build/{knowledgeId}?modelId=
  └─ KnowledgeGraphService.buildGraph
       ├─ 调用线程预检查防重（status='1' 则跳过）
       └─ aiExecutor.execute ──────────────────────────────────┐
                                                              ▼
        buildGraphInternal（异步线程）：
        ┌─ findOrCreateGraph（status 置 '1' 构建中）
        ├─ AiRequestContext.set(tenantId,userId,userName,null,modelId,modelName)
        │   （sessionId 传 null，避免 AiChatModelListener 误改 ai_message）
        ├─ 取待处理 chunk（extraction_status='0'，增量可重跑）
        │
        ├─【阶段一：逐 chunk 抽取】串行
        │    for chunk in pendingChunks:
        │      chunk.status='1' → agent.extract(content) → parseExtraction
        │        → 置信度过滤(<0.5 丢) → 数量上限(实体20/关系30)
        │        → mergeEntity（normalize(name)+type 合并，mention_count++）
        │        → persistRelation（按 name 解析 src/tgt，去重取较大 strength）
        │        → token 从 AiRequestContext.lastInput/Output 采集
        │      chunk.status='2' + entity_count/relation_count/token_used
        │      （失败：status='3' + kg_error_message，不中断）
        │
        ├─【阶段二：社区】仅 pendingChunks 非空时执行
        │    communityService.detectCommunities
        │      → 加载实体+关系为加权邻接表
        │      → LabelPropagationClustering.cluster（标签传播迭代）
        │      → capCommunities（社区数上限保护，超出合并小社区）
        │      → 回填 ai_kg_entity.community_id + insert ai_kg_community（summary_status='0'）
        │    communityService.summarizeCommunities
        │      → 逐社区 LLM 摘要（串行）→ summary + summary_status='2' + token_used
        │
        └─ 收尾：node_count/relationship_count/community_count 回填，status='2'（全失败 '3'）
           AiRequestContext.clear()
```

### 2.3 检索数据流

**局部检索（被动，并入 RAG）**：用户问答 → `RetrieverService.retrieve` → `hybridRetrieve` → 三路召回（向量 + 全文 + **图谱**）→ RRF 融合 → 可选 rerank → 注入 prompt。

```
performGraphRecall(knowledgeId, query):
  findGraphByKnowledge → 非 status='2' 返回空
  listEntitiesByGraphId → 子串匹配 query 命中实体（v1；v1.5 升 embedding 相似度）
  findNeighborEntityIds（1 跳，限 maxNeighbors=20）
  收集命中实体+邻居+相关关系的 source_chunk_id
  按 mention_count 降序截 limit → findSourceChunks → List<AiKnowledgeChunk>
  并入 hybridRetrieve 的 scoreMap（graphWeight=0.5 * 1/(60+i+1)）+ contentMap
```

**全局检索（主动，map-reduce）**：`globalSearch(knowledgeId, query)` → 加载已摘要社区 → 每社区生成候选答案（map）→ 综合合成（reduce）。由 Controller `/globalSearch`（手动测试）或 `KnowledgeGraphTool.globalSummary`（Agent 调用）触发。

### 2.4 Agent @Tool 路径

`AiServiceFacade.invokeWithAgent` 异步块内 `AiRequestContext.setKnowledgeId(request.getKnowledgeId())` → Supervisor Agent 调用 `KnowledgeGraphTool`：
- `queryGraphRelation(entityName, hops)` → 经 `GraphStoreStrategy` 1-2 跳遍历 → Markdown 关系表
- `globalSummary(query)` → `GlobalSearchService.globalSearch` → 合成答案 + 引用社区

---

## 三、新建文件清单

### 3.1 ruoyi-common-ai（共享层）

| 文件 | 用途 |
|------|------|
| `domain/entity/AiKnowledgeGraph.java` | 图谱实例（graphStatus 0/1/2/3、nodeCount/relationshipCount/communityCount、modelId、entityTypes/relationTypes、config、errorMessage） |
| `domain/entity/AiKgEntity.java` | 实体节点（entityName/entityType/description/confidence/sourceChunkId/mentionCount/communityId） |
| `domain/entity/AiKgRelation.java` | 关系（sourceEntityId/targetEntityId/relationType/description/strength/confidence/sourceChunkId） |
| `domain/entity/AiKgCommunity.java` | 社区（communityLevel/communityIndex/summary/summaryStatus 0/1/2/3/entityCount/relationCount/tokenUsed） |
| `mapper/AiKnowledgeGraphMapper.java` 等 4 个 | `BaseMapperPlus<Entity, Vo>` |
| `domain/bo/AiKnowledgeGraphBo` 等 4 个 | `@AutoMapper(reverseConvertGenerate=false)` |
| `domain/vo/AiKnowledgeGraphVo` 等 4 个 | `@AutoMapper` + `@ExcelProperty`（关系 VO 含 sourceEntityName/targetEntityName 联表字段） |
| `service/IAiKnowledgeGraphService` 等 4 个 + impl | CRUD 骨架（`buildQueryWrapper`+`selectVoPage/List`+`MapstructUtils.convert`） |

### 3.2 ruoyi-ai（业务层）

| 文件 | 用途 |
|------|------|
| `agent/GraphExtractionAgent.java` | LangChain4j AgenticServices 接口，`@SystemMessage`（ERP 实体/关系类型 + 严格 JSON schema）+ `@UserMessage` + `@Agent`，`String extract(@V("text") String)` |
| `service/kg/dto/GraphExtractionResult.java` | 抽取结果 DTO（`List<EntityDto>`/`List<RelationDto>`） |
| `service/kg/GraphExtractionParser.java` | `stripCodeFence` + `parse`（Jackson + 子串兜底）+ `regexFallback`（正则恢复实体/关系） |
| `service/kg/GraphStoreStrategy.java` | 存储策略接口（图谱/实体/关系/社区 CRUD + 邻居/2跳 + 实体链接 + 来源 chunk） |
| `service/kg/MysqlGraphStoreStrategy.java` | MySQL 实现（`@ConditionalOnProperty storage=mysql matchIfMissing=true`） |
| `service/kg/Neo4jGraphStoreStrategy.java` | v2 stub（`@ConditionalOnProperty storage=neo4j`，方法抛 `UnsupportedOperationException`） |
| `service/kg/LabelPropagationClustering.java` | 自实现加权标签传播（纯 Java 无依赖，输入邻接表输出 nodeId→communityLabel） |
| `service/kg/KnowledgeGraphService.java` | 构建主服务：`buildGraph`/`rebuild`/`deleteGraph`/`getProgress` + `buildGraphInternal`（两阶段）+ `extractChunk`/`mergeEntity`/`persistRelation` |
| `service/kg/CommunityService.java` | `detectCommunities`（标签传播分簇+落库）+ `summarizeCommunities`（LLM 摘要） |
| `service/kg/GlobalSearchService.java` | `globalSearch`（社区摘要 map-reduce 合成答案） |
| `controller/AiKnowledgeGraphController.java` | `/ai/knowledge/graph` 下 10 端点 |
| `tool/KnowledgeGraphTool.java` | `@Tool` `@Component`：`queryGraphRelation` + `globalSummary` |
| `mcp/registry/KnowledgeGraphToolProvider.java` | `implements BuiltinToolProvider`，自动注册进 `ai_tool` |

### 3.3 前端

| 文件 | 用途 |
|------|------|
| `src/api/ai/knowledgeGraph.ts` | 10 个 API + TS 接口（AiKnowledgeGraph/AiKgEntity/AiKgRelation/AiKgCommunity/GraphProgress） |
| `src/views/ai/knowledge/graph.vue` | 知识图谱 Tab：echarts 力导向图（按 communityId 着色）+ 构建按钮 + 进度轮询 + 实体/关系/社区表 + 点击高亮邻居 + 全局检索框 |

### 3.4 SQL

| 文件 | 用途 |
|------|------|
| `doc/ai/sql/ai-stage2-a.sql` | 4 表 CREATE + `ai_knowledge_chunk` ALTER（5 列）+ `ai_kg_entity` ALTER（community_id）+ sys_menu 1820-1823（幂等 DELETE+INSERT） |

---

## 四、修改文件清单

| 文件 | 改动摘要 |
|------|---------|
| `ruoyi-common-ai/.../entity/AiKnowledgeChunk.java` + BO + VO | +`extractionStatus`/`entityCount`/`relationCount`/`tokenUsed`/`kgErrorMessage` |
| `ruoyi-modules/ruoyi-ai/.../service/rag/RetrieverService.java` | 注入 `GraphStoreStrategy`+`AiProperties`；`hybridRetrieve` 全文打分后插入 `performGraphRecall` 第三路并入 RRF |
| `ruoyi-modules/ruoyi-ai/.../config/AiProperties.java` | +`KnowledgeGraphConfig` 内部类（12 字段） |
| `ruoyi-modules/ruoyi-ai/.../context/AiRequestContext.java` | +`knowledgeId` InheritableThreadLocal + getter/setter + clear |
| `ruoyi-modules/ruoyi-ai/.../service/facade/AiServiceFacade.java` | `invokeWithAgent` 异步块内 `AiRequestContext.setKnowledgeId(request.getKnowledgeId())` |
| `ruoyi-admin/.../application.yml` | `nexus.ai` 下 +`knowledge-graph` 配置块 |
| `plus-ui/src/views/ai/knowledge/index.vue` | 右侧两张并列 `el-card` 改 `el-tabs`，新增「知识图谱」Tab 懒加载 `./graph.vue` |
| `ruoyi-modules/ruoyi-ai/pom.xml` | **不改**（零新依赖） |
| `plus-ui/src/router/index.ts` | **不改**（图谱为 KB 详情页内 Tab） |

---

## 五、数据库变更

详见 `doc/ai/sql/ai-stage2-a.sql`。要点：

- **4 张新表**（均含 BaseEntity 审计列 + tenant_id + del_flag + attr1-4，utf8mb4_unicode_ci，ROW_FORMAT=DYNAMIC）：
  - `ai_knowledge_graph`：图谱实例。idx(knowledge_id)。
  - `ai_kg_entity`：实体。idx(graph_id/entity_name/entity_type/source_chunk_id/community_id)。
  - `ai_kg_relation`：关系。idx(graph_id/source_entity_id/target_entity_id/source_chunk_id)。
  - `ai_kg_community`：社区。idx(graph_id/community_level)。
- **2 处 ALTER**：
  - `ai_knowledge_chunk` 在 `vector_id` 之后加 `extraction_status`(char 0/1/2/3)/`entity_count`/`relation_count`/`token_used`/`kg_error_message`。
  - `ai_kg_entity` 加 `community_id` bigint NULL + idx。
- **菜单**（sys_menu 1820-1823，parent=1704 知识库管理，F 按钮权限 order 5-8）：`ai:knowledge:graph:{build,query,edit,remove}`。**已合并进 `ry-vue.sql` 主脚本**，迁移脚本用 `DELETE+INSERT` 保证幂等。图谱为 KB 详情页内 Tab，不新增 C 菜单/不改路由。
- **菜单结构对齐**：当前菜单已重组为三级（1700 AI 智能助手 → 1706/1707/1708/1709/1713 分组目录 → 菜单），`1704 知识库管理` 挂在 `1708 知识与工具` 目录下，menu_id 未变，图谱按钮 parent=1704 仍正确。

---

## 六、核心类设计

### 6.1 GraphExtractionAgent（`agent/`）

LangChain4j AgenticServices 接口，注解置于接口方法上（仿 `ErpQueryAgent`）。`@SystemMessage` 定义：
- **实体类型**：物料/客户/供应商/订单/采购订单/工单/BOM/车间/工序/设备/仓库/产品/其他
- **关系类型**：供应/采购/包含/生产于/存储于/隶属于/关联
- **抽取规则**：只抽明确事实、规范简称可回溯、置信度<0.5 不输出
- **严格 JSON 输出**：`{"entities":[{name,type,description,confidence}], "relations":[{source,target,type,description,strength,confidence}]}`，无实体时 `{"entities":[],"relations":[]}`

构建方式（`KnowledgeGraphService`）：
```java
ChatModel model = chatModelFactory.getChatModel(extractModelId);  // 已挂 AiChatModelListener 自动采 token
GraphExtractionAgent agent = AgenticServices.agentBuilder(GraphExtractionAgent.class).chatModel(model).build();
String json = agent.extract(chunk.getContent());
```
- **模型选择**：用 `getChatModel`（非 `getAgentChatModel`，后者固定 temperature=0.1 且独立缓存，为 SQL Agent 设计）。抽取建议配便宜 chat 模型（如 deepseek-chat）。
- **底座模型类型**：必须是 `model_type=CHAT`（EMBEDDING/RERANK/IMAGE 不可用）。配置 `nexus.ai.knowledge-graph.extraction-model-id` 指向一条 CHAT 模型记录，留空回退默认 chat 模型。

### 6.2 GraphExtractionParser（`service/kg/`）

纯静态工具，无 Spring 依赖：
1. `stripCodeFence(json)`：剥离 ```json … ``` 包裹。
2. `parse(json)`：stripCodeFence → Jackson 解析 → 失败则取首个`{`到末尾`}`子串再解析。
3. `regexFallback(json)`：正则抽 `"name"/"type"`（实体）与 `"source"/"target"/"type"`（关系），尽力恢复。

`KnowledgeGraphService.extractChunk` 调用顺序：`parse` → 失败重试 1 次（追加"请严格只输出JSON"）→ `regexFallback` → 三次均失败标 chunk `extraction_status='3'`，不中断整图。

### 6.3 GraphStoreStrategy（`service/kg/`）

存储策略接口，抽象图谱/实体/关系/社区的 CRUD + 邻居查询 + 实体链接 + 来源 chunk 回溯。`MysqlGraphStoreStrategy` 为默认实现（`matchIfMissing=true`），用 LambdaQueryWrapper 查询，租户隔离由多租户拦截器自动注入。`Neo4jGraphStoreStrategy` 为 v2 stub。切换 `nexus.ai.knowledge-graph.storage=neo4j` 自动换 bean。

**关键方法**：
- `findOrCreateGraph`：构建入口，幂等（已存在则置 '1'，否则新建）
- `findNeighborEntityIds`/`findTwoHopEntityIds`：1 跳/2 跳邻居（内存算，避免复杂 SQL）
- `matchEntities`：v1 子串匹配（`query.contains(name)`）；v1.5 升 embedding 相似度
- `assignCommunityIds`：社区检测后批量回填 `entity.community_id`
- `deleteGraphData`/`deleteCommunitiesByGraphId`：重建/重算社区用

### 6.4 KnowledgeGraphService（`service/kg/`，核心）

| 方法 | 职责 |
|------|------|
| `buildGraph` | 异步触发：调用线程预检查防重 → `aiExecutor.execute(buildGraphInternal)` |
| `buildGraphInternal` | 两阶段构建主流程（见 §2.2）。防重（status='1' 跳过）→ `findOrCreateGraph` → `AiRequestContext.set` → 阶段一抽取 → 阶段二社区（仅 pendingChunks 非空）→ 收尾 |
| `extractChunk` | 单 chunk：置'1' → extract → parseExtraction → 过滤/上限 → mergeEntity/persistRelation → token 采集 → 置'2' |
| `mergeEntity` | 同名合并：键 = `normalize(name)+"|"+type`（normalize 去空格/全角半角/大小写/常见后缀如"公司"）；命中则 mention_count++/desc 拼接/confidence 取 max，否则 insert。同名但 type 不同（如"华东科技"既是客户又是供应商）作两条独立实体 |
| `persistRelation` | 按 name 解析 src/tgtId（悬空丢弃）；`(srcId,tgtId,type)` 已存在取较大 strength/confidence 更新，否则 insert |
| `getProgress` | 聚合 `ai_knowledge_chunk.extraction_status` 计数 + graph 状态/计数，供前端轮询 |
| `rebuild` | **异步**全量重抽：清实体/关系/社区 + 强制置 status='0'（避免防重）+ 重置 chunk + `buildGraphInternal` |
| `deleteGraph` | 删图谱全部产物 + 重置 chunk extraction_status='0'（保留 chunk 内容） |

**token 采集**：复用 `AiChatModelListener.onResponse` 机制——监听器在每次同步 LLM 调用后自动 `AiRequestContext.setLastTokenUsage` + `aiTokenUsageService.incrementDailyUsage`（userId/modelId 非空时）。`extractChunk`/`summarizeCommunities` 从 `AiRequestContext.getLastInputTokens/OutputTokens` 读取写入 chunk/community 的 `token_used`，无需手动调统计。sessionId 传 null，避免监听器误改 `ai_message`。

### 6.5 LabelPropagationClustering（`service/kg/`）

自实现加权标签传播，纯 Java 无依赖：
- 输入：无向加权邻接表 `Map<Long, Map<Long, Integer>>`（边权 = relation.strength）
- 初始：每节点标签 = 自身 id
- 迭代（≤`labelPropagationMaxIter` 默认 8）：每节点取邻居标签加权众数更新；并列时保留当前标签避免振荡
- 收敛或达上限停止；输出 `Map<entityId, communityLabel>`

`CommunityService.detectCommunities` 调用后用 `capCommunities` 保护：社区数 > `maxCommunities`(50) 时按大小合并小社区到最大社区。

### 6.6 CommunityService（`service/kg/`）

- `detectCommunities(graphId)`：`deleteCommunitiesByGraphId`（清旧）→ 加载实体+关系建邻接表 → `LabelPropagationClustering.cluster` → `capCommunities` → insert `ai_kg_community`（summary_status='0'）+ `assignCommunityIds` 回填。返回社区数。
- `summarizeCommunities(graphId, modelId)`：遍历 `summary_status='0'/'3'` 社区 → 构建 prompt（社区内实体列表 + 关系列表，截断控 token：实体≤30/关系≤30）→ `chatModel.chat` → 存 summary + status='2' + token_used。**串行**（v1 简单；v1.5 可并行）。失败标 '3' 不中断。

### 6.7 GlobalSearchService（`service/kg/`）

`globalSearch(knowledgeId, query)`：
1. 门禁：图谱 status='2' 且有已摘要社区，否则返回提示。
2. 限社区数：`maxCommunitiesForGlobal`(50)，按 entity_count 取 topN。
3. **Map**：每社区摘要 → LLM「基于此社区摘要回答问题，无关则回复"无关"」→ 候选答案（截 400 字）。
4. **Reduce**：所有候选答案 → LLM「综合多社区候选合成最终答案」→ 返回 `{answer, communityCount, communities[]}`。
- 复用 `ChatModelFactory.getChatModel`；串行 map（v1）。不回溯 chunk（global search 本质：基于摘要推理整体主题）。
- **token 统计局限**：由 Controller 直接调时（HTTP 线程，AiRequestContext 未 set），token 不计入日统计；由 `KnowledgeGraphTool.globalSummary`（Agent 异步线程，context 已 set）调时正常统计。v1 可接受。

### 6.8 RetrieverService.performGraphRecall（`service/rag/`，改）

`hybridRetrieve`（L180-227）原两路（向量 `(1-alpha)` + 全文 `alpha`，RRF K=60）后插入第三路：
```java
if (graphCfg.isEnabled()) {
    List<AiKnowledgeChunk> graphChunks = performGraphRecall(knowledgeId, query, maxResults, graphCfg);
    double gw = graphCfg.getGraphWeight();  // 默认 0.5
    for (int i=0; i<graphChunks.size(); i++) {
        scoreMap.merge(c.getId(), gw*(1.0/(RRF_K+i+1)), Double::sum);
        contentMap.putIfAbsent(c.getId(), c.getContent());
    }
}
```
`performGraphRecall`：实体链接（子串匹配）→ 1 跳邻居（限 maxNeighbors）→ 回溯来源 chunk（命中实体+邻居+相关关系的 source_chunk_id）→ 按 mention_count 降序截 limit。失败回退空列表，不阻断 RRF。

**门禁**：图谱召回嵌在 `hybridRetrieve`，仅 `enableHybrid=1` 生效（纯向量 KB 不走图谱，已知限制）。

### 6.9 KnowledgeGraphTool + Provider（`tool/` + `mcp/registry/`）

`@Component`，两个 `@Tool`：
- `queryGraphRelation(entityName, hops)`：读 `AiRequestContext.getKnowledgeId()` → 1-2 跳遍历 → Markdown 关系表。`hops` 限 1-2 防爆炸。
- `globalSummary(query)`：调 `GlobalSearchService.globalSearch` → 合成答案 + 引用社区。

`KnowledgeGraphToolProvider implements BuiltinToolProvider`（name=`query_knowledge_graph`，categories=`QUERY`），`BuiltinToolRegistry.@PostConstruct` 自动发现并同步进 `ai_tool` 表，`ToolProviderFactory` 按类别注入 Supervisor Agent。无需改 factory。

**与 RRF 的关系**：RRF 被动补 chunk（用户无感），@Tool 主动给关系结构/全局摘要（Agent 显式调用）。两者互补不重复。

### 6.10 AiKnowledgeGraphController（`controller/`）

`/ai/knowledge/graph`，10 端点，`@SaCheckPermission("ai:knowledge:graph:*")`，extends BaseController：

| 方法 | 路径 | 权限 | 响应 |
|------|------|------|------|
| POST | `/build/{knowledgeId}?modelId=` | build | `R<Void>`（异步触发） |
| GET | `/{knowledgeId}` | query | `R<AiKnowledgeGraphVo>` |
| GET | `/progress/{knowledgeId}` | query | `R<Map>` |
| GET | `/entity/list` | query | `TableDataInfo<AiKgEntityVo>` |
| GET | `/relation/list` | query | `TableDataInfo<AiKgRelationVo>` |
| GET | `/community/list` | query | `TableDataInfo<AiKgCommunityVo>` |
| GET | `/neighbors/{entityId}` | query | `R<Map>`（entity+neighbors+relations） |
| POST | `/globalSearch/{knowledgeId}` | query | `R<Map>`（answer+communities） |
| POST | `/rebuild/{knowledgeId}?modelId=` | edit | `R<Void>`（异步） |
| DELETE | `/{knowledgeId}` | remove | `R<Void>` |

---

## 七、配置（`AiProperties.KnowledgeGraphConfig`，前缀 `nexus.ai.knowledge-graph`）

| 字段 | 默认值 | 说明 |
|------|--------|------|
| enabled | true | 是否开启知识图谱 |
| storage | mysql | mysql \| neo4j（v1 用 mysql，Neo4j 经 GraphStoreStrategy 预留） |
| batch-size | 5 | 每批抽取 chunk 数 |
| max-entities-per-chunk | 20 | 每个 chunk 最多抽取实体数 |
| max-relations-per-chunk | 30 | 每个 chunk 最多抽取关系数 |
| confidence-threshold | 0.5 | 置信度阈值（低于丢弃） |
| extraction-model-id | null | 抽取用 LLM 模型ID（null 用默认 chat 模型，建议配便宜模型） |
| graph-weight | 0.5 | 图谱召回在 RRF 中的权重 |
| max-neighbors | 20 | 单次局部召回最大邻居实体数 |
| label-propagation-max-iter | 8 | 标签传播迭代上限 |
| max-communities | 50 | 社区数上限（超出合并小社区） |
| max-communities-for-global | 50 | 全局检索参与 map 的社区上限 |
| gleaning-enabled | false | v1.5 预留：实体 gleaning 二次抽取 |

`application.yml` 示例见 `ai-stage2-a.sql` 同级配置块。

---

## 八、依赖增量

| 依赖 | 状态 | 说明 |
|------|------|------|
| MySQL 存储 | **无需新依赖** | 现有 MyBatis-Plus + JdbcTemplate |
| 社区检测 | **无需新依赖** | `LabelPropagationClustering` 自实现 |
| `neo4j-java-driver` 5.27.0 + `langchain4j-neo4j` | **v2 预留** | 根 pom 加 `<neo4j.version>`，v1 不引入 |
| echarts | **已就绪** | `package.json` 已有 `echarts@6.0.0`（无 `vue-echarts`，直接用 echarts 仿 `EchartsRenderer.vue`） |
| LangChain4j AgenticServices | **已就绪** | `ruoyi-ai/pom.xml` 已有 `langchain4j-agentic` |
| @Tool 注册 | **已就绪** | `BuiltinToolProvider`+`BuiltinToolRegistry`+`ToolProviderFactory` 已存在 |

> **结论：v1 零新依赖**。

---

## 九、前端

### 9.1 `api/ai/knowledgeGraph.ts`
10 个 API（buildGraph/getGraph/getGraphProgress/listGraphEntity/listGraphRelation/listGraphCommunity/getEntityNeighbors/globalSearch/rebuildGraph/deleteGraph）+ TS 接口。

### 9.2 `views/ai/knowledge/graph.vue`
- props：`knowledgeId: number`
- 顶部工具条：状态 Tag + 统计 + 构建/重建/删除/刷新按钮
- 进度条：构建中每 2s 轮询 `getGraphProgress`，status='2' 停止并加载图谱
- echarts 力导向图：`series.type='graph'`、`layout:'force'`、`roam:true`，**节点按 communityId 着色**，点击节点 → `getEntityNeighbors` → `emphasis.focus='adjacency'` 高亮 1 跳邻居 + 切到「邻居」Tab
- 右侧 `el-tabs`：实体表 / 关系表 / 社区摘要表 / 全局检索框（输入问题 → `globalSearch` → 展示合成答案 + 引用社区）
- 直接 `import * as echarts from 'echarts'`（仿 `EchartsRenderer.vue` 的 `echarts.init`+`setOption`+ResizeObserver，零新依赖）

### 9.3 `views/ai/knowledge/index.vue`（改）
右侧（选中 KB 时）两张并列 `el-card`（文档管理 + 检索测试）改为 `el-tabs`，新增「知识图谱」Tab，`defineAsyncComponent(() => import('./graph.vue'))` 懒加载（避免首屏加载 echarts）。

---

## 十、实施工时

> 用户纳入「社区/全局检索 + Agent @Tool」，较主计划原 18 人天上调至约 26 人天。

| 阶段 | 任务 | 人天 |
|------|------|------|
| W12 | DDL + 4 实体/Mapper/BO/VO + 改 AiKnowledgeChunk + 4 Service 骨架 + 编译 | 5 |
| W13 | GraphExtractionAgent + Parser + GraphStoreStrategy(MySQL/Neo4j) + KnowledgeGraphService 阶段一 + 端到端抽取 | 5 |
| W14 | LabelPropagationClustering + CommunityService + performGraphRecall+RRF + AiProperties+yml | 5 |
| W15 | GlobalSearchService + Controller 10 端点 + KnowledgeGraphTool+Provider + AiRequestContext+Facade | 5 |
| W16 | 前端 graph.vue + knowledgeGraph.ts + index.vue Tab + vue-tsc + 集成测试 | 5 |
| 缓冲 | JSON 兜底调优、社区质量评估、token 成本评估、@Tool 联调 | 1 |
| | **合计** | **26** |

---

## 十一、已知限制与注意事项

| # | 内容 | 影响 | 应对 |
|---|------|------|------|
| 1 | LLM 抽取 JSON 不稳 | 部分 chunk 失败 | 严格 Prompt + Jackson 失败重试1次 + 正则兜底 + 失败标'3'不中断 |
| 2 | 同名实体合并误判 | 不同实体被误并 | 按 (normalize(name)+type) 合并，type 不同保留独立；v1.5 高提及实体 embedding 消歧 |
| 3 | 抽取/摘要 token 成本高 | 大文档构建费用 | batch + 每 chunk 实体/关系上限 + 便宜抽取模型 + 逐 chunk 记 token_used 并入 ai_token_usage；社区数上限 |
| 4 | MySQL 1-2 跳查询性能 | 大图谱邻居查询慢 | 实体/关系表加索引；邻居限 1 跳+maxNeighbors；2 跳仅 @Tool 显式调用；大图谱预留 Neo4j v2 |
| 5 | 图谱局部召回仅 enableHybrid=1 生效 | 纯向量 KB 不走图谱 | v1 限制（嵌在 hybridRetrieve）；v1.5 可在 retrieve() 层始终并入 |
| 6 | 增量重跑不清理旧实体 | 重复构建残留 | rebuild 显式清实体/关系/社区+重置 chunk；普通 buildGraph 只处理 extraction_status='0' |
| 7 | 标签传播社区质量 | 分簇可能不理想 | 固定顺序迭代+收敛判定+社区数上下限保护；v1.5 换 JGraphT/Leiden |
| 8 | 全局检索社区过多 | map token 爆炸 | `maxCommunitiesForGlobal` 取 entity_count topN；社区摘要串行生成 |
| 9 | @Tool 需 knowledgeId 上下文 | Agent 模式未选 KB 时失效 | AiRequestContext 增 knowledgeId；未选时工具返回提示而非报错 |
| 10 | 进度仅 REST 轮询 | 无实时推送 | 构建由 KB 管理页触发（非对话流），REST 轮询(2s) 最简；v1.5 可加 `AI_GRAPH_PROGRESS` SSE |
| 11 | 租户隔离 | 多租户数据串 | 实体/关系/社区/图谱表均带 tenant_id，受拦截器过滤；构建时 AiRequestContext.set(tenantId,...) |
| 12 | 全局检索 token 统计局限 | Controller 直接调 globalSearch 不计入日统计 | Agent 经 @Tool 调用时正常统计；v1 可接受，v1.5 可在 globalSearch 内 set context |
| 13 | 并发构建竞态 | 双击 build 可能重复提交 | buildGraph 调用线程预检查 + 异步内防重双保险；未加分布式锁，极端并发仍可能重复（v1 可接受） |
| 14 | **异步线程租户上下文丢失** | `buildGraph`/`rebuild` 在 `aiExecutor` 异步线程写库，多租户拦截器（Sa-Token ThreadLocal）不跨线程 → `tenant_id` 落成 `0` 而非 `000000` → 前端查不到（`data:null`） | **已修复**：异步内用 `TenantHelper.dynamic(tenantIdStr, () -> {...})` 包裹构建逻辑恢复租户上下文；controller 用 `currentTenantIdStr()` 传字符串租户ID（保留前导零，勿 parse 成 Long）。**通用规则**：任何在 aiExecutor/CompletableFuture 异步线程写库的 AI 代码都要遵守此模式 |
| 15 | **雪花ID前端精度丢失**（项目级，非 KG 独有） | 雪花ID超 JS `Number.MAX_SAFE_INTEGER`(2^53-1)，前端 `JSON.parse` 解析裸数字时末尾被截断（`...6082`→`...6000`），按 ID 查询报"不存在" | **已修复**：`ruoyi-common-web` 新增 `JacksonConfig` + `BigNumberSerializer`（移植 RuoYi 官方），仅超安全范围的 Long 序列化为 String，普通 Long 保持数字；注册到 `AutoConfiguration.imports` 全局生效。前端 KG 点击实体改 `String(params.data.id)` 不再 `Number()` 转换 |

---

## 十二、验证清单

- [ ] `ai-stage2-a.sql` 干净库执行无错；4 表+2 ALTER+4 菜单生效；菜单幂等（重复执行不报主键冲突）
- [ ] 上传 ERP 业务文档（含物料/供应商/订单）→ 触发 buildGraph → graph_status 流转 0→1→2；阶段二社区检测+摘要完成
- [ ] 实体/关系正确抽取（ERP 域类型）；同名实体 mention_count 累加；置信度<0.5 过滤
- [ ] JSON 解析失败重试+正则兜底生效；彻底失败 chunk 标'3' 不中断
- [ ] 社区检测：实体被分到合理社区（community_id 回填）；社区摘要生成（summary_status='2'）
- [ ] `performGraphRecall` 对实体名命中的查询返回关联 chunk 并入 RRF scoreMap/contentMap
- [ ] 检索面板：开 enableHybrid+图谱，问"物料 X 的供应商还有哪些客户"，召回含图谱回溯 chunk；与关图谱对比召回数
- [ ] `globalSearch`：问"这批文档主要涉及哪些业务关系"类问题，返回社区摘要合成答案+引用社区
- [ ] `KnowledgeGraphTool`：Agent 模式下 `queryGraphRelation` 返回关系表、`globalSummary` 返回合成答案；未选 KB 时返回提示
- [ ] 前端 graph.vue echarts 图谱按社区着色渲染；构建→进度轮询→完成自动加载；点节点高亮 1 跳邻居+展示来源 chunk；社区表/全局检索框可用
- [ ] 增量：新传文档后 buildGraph 仅处理 extraction_status='0' 新 chunk；无新文档时不重算社区（不浪费 token）
- [ ] rebuild 异步执行（HTTP 立即返回），清空旧实体/关系/社区并全量重抽
- [ ] deleteGraph 删图谱产物但保留 chunk 内容
- [ ] token 统计：chunk.token_used 累计；ai_token_usage 日统计含抽取+社区摘要 token（按触发用户/模型）
- [ ] 租户隔离：A 租户图谱在 B 租户检索不可见
- [ ] `mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-common/ruoyi-common-ai -am compile -DskipTests` BUILD SUCCESS
- [ ] `vue-tsc` AI 文件无新增 TS 类型错误

---

## 十三、后续升级路径

| 版本 | 升级项 | 说明 | 影响 |
|------|--------|------|------|
| v1.5 | 实体 gleaning | 二次抽取问"是否遗漏"，提升 recall | `gleaningEnabled` 开关已预留；token 翻倍 |
| v1.5 | Embedding 相似度实体链接 | 替代子串匹配，提升召回精度 | 改 `MysqlGraphStoreStrategy.matchEntities` |
| v1.5 | Embedding 相似度实体消歧 | 高提及实体二轮消歧 | 改 `KnowledgeGraphService.mergeEntity` |
| v1.5 | 社区摘要并行 | 加速大图谱 | 改 `CommunityService.summarizeCommunities` 用 aiExecutor 并行 |
| v1.5 | 图谱召回始终并入 | 纯向量 KB 也走图谱 | 在 `retrieve()` 层而非 `hybridRetrieve` 接入 |
| v1.5 | 构建进度 SSE 推送 | 替代 REST 轮询 | 加 `AI_GRAPH_PROGRESS` 事件 + `AiSseMessageHelper` |
| v1.5 | JSON mode | 提升 JSON 稳定性 | provider 加 `responseFormat` 开关 |
| v2 | Neo4j 存储 | 多跳遍历 + Leiden 社区 | 实现 `Neo4jGraphStoreStrategy`（引入 `neo4j-java-driver`+`langchain4j-neo4j`） |
| v2 | Leiden 多层级社区 | hierarchical communities | Neo4j GDS Leiden（Cypher `gds.leiden`） |
| v2 | 多跳遍历 | "二级供应商"类问题 | @Tool 升级支持 >2 跳 + Neo4j Cypher |
| v2 | 全局检索 token 统计 | Controller 直接调也计入 | `GlobalSearchService` 内 `AiRequestContext.set` |

---

## 十四、关键文件索引

- 构建核心：`ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/kg/KnowledgeGraphService.java`
- 抽取 Agent：`.../agent/GraphExtractionAgent.java` + `.../service/kg/GraphExtractionParser.java`
- 存储：`.../service/kg/GraphStoreStrategy.java` + `MysqlGraphStoreStrategy.java`
- 社区：`.../service/kg/LabelPropagationClustering.java` + `CommunityService.java`
- 全局检索：`.../service/kg/GlobalSearchService.java`
- 局部检索接入：`.../service/rag/RetrieverService.java`（`hybridRetrieve` + `performGraphRecall`）
- Agent 工具：`.../tool/KnowledgeGraphTool.java` + `.../mcp/registry/KnowledgeGraphToolProvider.java`
- 控制器：`.../controller/AiKnowledgeGraphController.java`
- 配置：`.../config/AiProperties.java`（`KnowledgeGraphConfig`）+ `ruoyi-admin/.../application.yml`
- 上下文：`.../context/AiRequestContext.java`（`knowledgeId`）+ `.../service/facade/AiServiceFacade.java`
- 前端：`plus-ui/src/views/ai/knowledge/graph.vue` + `api/ai/knowledgeGraph.ts` + `views/ai/knowledge/index.vue`
- SQL：`doc/ai/sql/ai-stage2-a.sql`
- 实施计划：`C:\Users\admin\.claude\plans\doc-ai-s2-a-d-ruoyi-ai-snazzy-riddle.md`
