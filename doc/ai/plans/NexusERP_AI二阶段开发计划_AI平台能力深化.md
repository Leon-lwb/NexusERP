# NexusERP AI 二阶段开发计划（AI 平台能力深化）

> **文档定位**：本计划是 `NexusERP_AI开发计划.md`（v6，Phase 0/1/2 已完成）的延续，定义**第二大阶段——AI 平台能力深化**。
>
> **与主计划的关系**：主计划中 Phase 3（MES）/4（IoT）/5（跨系统）/6（工作流）原排第 13 周起。本二阶段作为独立的"AI 平台深化"轨道插入，**原 Phase 3-6 顺延至第 23 周后**（详见第三节）。MES/IoT 的业务赋能依赖一个成熟的 AI 平台底座，因此先做平台深化再赋能业务更合理。
>
> **制定依据**：① 对标参照项目 `E:\ruoyi-ai` 的功能差异分析；② 当前 `NexusERP_AI开发计划.md` 已完成内容；③ 市面主流 AI 平台（Dify/FastGPT/Coze/RAGFlow 等）的通用能力演进趋势。

---

## 一、背景与问题

### 1.1 一阶段（Phase 0-2）已建成的能力

| 能力域 | 已实现 |
|--------|--------|
| 模型接入 | OpenAI/Ollama/DashScope/ZhiPu（DashScope/ZhiPu 走 OpenAI 兼容，未用原生 SDK） |
| 对话 | SSE 流式、会话管理、消息反馈、导出、上下文窗口 |
| Agent | SupervisorAgent + ErpQueryAgent + ErpReportAgent + OutputChannel 流式工具事件 |
| Text2SQL | 7 层安全校验 + 表结构缓存 + Few-Shot + 自动修复 |
| RAG | Tika 解析 + 句子边界分段 + Milvus/InMemory + 混合检索 + RRF + LLM-as-judge 重排 |
| MCP | `ai_tool` 统一表 + BuiltinToolRegistry 自动发现 + McpClientManager（STDIO/HTTP + 熔断）+ 按会话选工具 |
| 运营 | Prompt 模板、A/B 测试、Token 用量与成本、模型定价 |

### 1.2 对标参照项目与市面平台的差距（二阶段要补的）

| # | 缺失能力 | 严重度 | 参照项目状态 | 市面平台普遍情况 |
|---|---------|--------|------------|----------------|
| 1 | **知识图谱**（实体/关系抽取 + 图谱检索） | 高 | 仅脚手架（实体/CRUD 在，抽取/Neo4j/检索集成/DDL/前端全无） | RAGFlow/Dify 均有 GraphRAG |
| 2 | **推理(Thinking)模式**（推理 token 流 + 深度思考编排） | 高 | 有两套（`returnThinking` + Supervisor 深度思考），共用 `enableThinking` 开关 | DeepSeek-R1/o1 类推理模型标配 |
| 3 | **更多厂商 + 原生 SDK**（DeepSeek/MiniMax/MiMo/Atlas/custom_api + Qwen/智谱原生） | 高 | 9 个厂商 + 策略模式 | — |
| 4 | **专用 Rerank API**（替代 LLM-as-judge） | 高 | 3 个（百炼/智谱/硅基流动） | Cross-Encoder 重排是 RAG 标配 |
| 5 | **联网搜索**（WebSearchAgent） | 中 | MCP 方式（Playwright/bing），Bing 被注释 | Coze/Dify 联网搜索标配 |
| 6 | **MCP 市场**（工具发现/一键加载） | 中 | 完整（`mcp_market_*` 表 + Controller） | MCP 生态标配 |
| 7 | **多模态**（图像生成） | 中 | 通义万相（dashscope 原生 SDK） | 文生图标配 |

> **诚实说明**：知识图谱在参照项目里只是代码生成器产出的空壳，没有可复用的抽取逻辑。本计划需自行设计抽取管线，不能"照搬"。

### 1.3 二阶段三大优先级（用户确认）

- **优先级 1**：知识图谱
- **优先级 2**：推理模式 + 厂商增强（含专用 Rerank）
- **优先级 3**：联网搜索 + MCP 市场 + 多模态

---

## 二、总体目标与里程碑

### 2.1 总目标

把 NexusERP AI 从"能查 ERP 数据的对话助手"升级为"具备知识图谱推理、深度思考、多厂商、联网、多模态的通用 AI 平台"，对标市面主流 AI 平台能力底座，为 Phase 3+ 的 MES/IoT 业务赋能提供更强的平台支撑。

### 2.2 里程碑

| 里程碑 | 周期 | 交付 | 验收标准 |
|--------|------|------|---------|
| **M1 知识图谱可用** | 第 12-14 周 | 图谱构建 + 图谱增强检索 + 前端 | 上传文档→自动抽取实体关系→图谱检索显著提升关联问答召回 |
| **M2 推理 + 多厂商** | 第 15-17 周 | Thinking 流 + 策略模式厂商 + 专用 Rerank | DeepSeek-R1 类推理 token 实时展示；新增≥4 厂商；Rerank 命中率优于 LLM-as-judge |
| **M3 联网 + 市场 + 多模态** | 第 18-22 周 | WebSearchAgent + MCP 市场 + 文生图 | 联网问答带来源；市场一键加载工具；对话内文生图 |

---

## 三、与现有计划的关系（重排期）

```
一阶段（已完成）          二阶段（本计划，AI 平台深化）          三阶段（顺延，业务赋能）
┌──────────────┐    ┌──────────────────────────────┐    ┌──────────────────┐
│ Phase 0-2    │    │ S2-A 知识图谱      第12-14周  │    │ Phase 3 MES      │
│ 第 1-11 周   │ →  │ S2-B 推理+厂商     第15-17周  │ →  │ Phase 4 IoT      │  第 23 周起
│ ✅ 已完成    │    │ S2-C 联网+市场+多模态 第18-22周│    │ Phase 5 跨系统   │
└──────────────┘    └──────────────────────────────┘    │ Phase 6 工作流   │
                                                         └──────────────────┘
```

> **说明**：主计划原 Phase 6（AI 工作流/LangGraph4j）与二阶段 S2-C 的多模态、MCP 市场有交集。二阶段先做"工具市场"和"多模态生成"，**完整的 LangGraph4j 可视化工作流引擎仍留给三阶段 Phase 6**，避免本阶段摊子过大。本阶段产出的 `IImageGenerationService`、MCP 市场能力会被 Phase 6 工作流节点复用。

---

## 四、技术栈与依赖增量

### 4.1 后端依赖（新增/启用）

> **关键事实**：`langchain4j` 版本当前项目与参照项目完全一致（核心 `1.13.0` / 社区 `1.13.0-beta23`），**无需改版本**。dashscope/智谱社区 SDK 已在 `ruoyi-ai/pom.xml` 声明，只是 `ChatModelFactory` 没用原生类。

```xml
<!-- ===== ruoyi-ai/pom.xml 新增 ===== -->

<!-- 知识图谱：Neo4j 驱动（可选，见 S2-A 决策） -->
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
    <version>${neo4j.version}</version>  <!-- 主 pom 新增 <neo4j.version>5.27.0</neo4j.version> -->
</dependency>

<!-- 文生图：原生 dashscope SDK 由 langchain4j-community-dashscope 传递引入 -->
<!-- com.alibaba.dashscope.aigc.imagesynthesis.* 已随下列依赖可用，无需单独声明 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<!-- 注：langchain4j-community-dashscope / langchain4j-community-zhipu-ai 当前已声明，本阶段开始真正使用 -->
```

### 4.2 中间件

| 组件 | 版本 | 用途 | 是否必需 |
|------|------|------|---------|
| Milvus | 2.4+ | 已有，RAG 向量库 | 已有 |
| Redis | 已有 | SSE/缓存 | 已有 |
| **Neo4j** | 5.x | 知识图谱存储（深度遍历） | **可选**，见 S2-A 决策，v1 可只用 MySQL |
| **Node.js** | 18+ | MCP 市场 STDIO 工具的 npx 拉起 | 联网搜索/Playwright 工具需要 |

### 4.3 配置增量（`application.yml`）

```yaml
nexus:
  ai:
    # 一阶段已有配置保留...
    thinking:
      enabled: true              # 是否开启推理模式
      default-on: false          # 默认不开启，用户主动选
    knowledge-graph:
      enabled: true
      storage: mysql             # mysql | neo4j（v1 用 mysql）
      batch-size: 5             # 每批抽取的 chunk 数
      max-entities-per-chunk: 20
      max-relations-per-chunk: 30
      confidence-threshold: 0.5
    web-search:
      enabled: true
      provider: tavily           # tavily | bing | mcp（推荐 tavily，Java HTTP 调用，不依赖 npx）
      api-key: ${WEB_SEARCH_API_KEY:}
    image:
      enabled: true
      default-provider: Tongyiwanx
```

---

## 五、S2-A：知识图谱（第 12-14 周）

> **目标**：为知识库自动抽取实体与关系，构建图谱，并在检索时用图谱增强召回（GraphRAG 思路），提升关联性问答质量。
>
> **关键决策：存储用 MySQL 还是 Neo4j？**
> - 参照项目声明了 `neo4j-java-driver` 但完全没用，说明落地有成本。
> - **v1 推荐 MySQL 存储**（`ai_kg_entity`/`ai_kg_relation` 表），实体邻接查询用 SQL 即可，无需新中间件，部署成本低，覆盖 80% 价值（实体扩展召回、关系事实问答）。
> - **v2（可选）引入 Neo4j** 做多跳深度遍历（如"找物料 A 的二级供应商"），作为 S2-A 的可选拓展，预留接口 `GraphStoreStrategy`。
> - 本计划按 MySQL 实现，Neo4j 作为接口预留。
>
> **实施状态（2026-06-30 更新）**：✅ 代码已完成并编译通过，详见 `doc/ai/NexusERP_AI二阶段实施计划_S2A.md`。实际实现超出本节原 v1 范围（用户规划阶段选定）：纳入**社区检测（自实现加权标签传播 `LabelPropagationClustering` 替代 Leiden）+ 社区摘要 + 全局检索（map-reduce `GlobalSearchService`）+ Agent 图谱查询 @Tool（`KnowledgeGraphTool`）**，工时由 18 上调至约 26 人天，周期延至第 12-16 周。新增第 4 张表 `ai_kg_community`。v1 零新依赖。⬜ 运行时验证待执行（需跑 `ai-stage2-a.sql` + 起应用 + 配 LLM）。

### 5.1 数据模型

新增 4 张表（含社区表 `ai_kg_community`）+ 给 `ai_knowledge_chunk` 加抽取跟踪列：

```sql
-- ============================================================
-- 知识图谱实例（每个知识库一个图）
-- ============================================================
CREATE TABLE ai_knowledge_graph (
    id                  BIGINT       NOT NULL COMMENT '主键',
    tenant_id           VARCHAR(20)  DEFAULT '000000' COMMENT '租户编号',
    knowledge_id        BIGINT       NOT NULL COMMENT '关联知识库ID',
    graph_name          VARCHAR(128) DEFAULT NULL COMMENT '图谱名称',
    graph_status        CHAR(1)      DEFAULT '0' COMMENT '状态: 0未构建 1构建中 2已完成 3失败',
    node_count          INT          DEFAULT 0 COMMENT '实体节点数',
    relationship_count  INT          DEFAULT 0 COMMENT '关系数',
    model_id            BIGINT       DEFAULT NULL COMMENT '抽取用的LLM模型ID',
    entity_types        VARCHAR(512) DEFAULT NULL COMMENT '实体类型（逗号分隔，如 物料,客户,供应商,订单）',
    relation_types      VARCHAR(512) DEFAULT NULL COMMENT '关系类型（逗号分隔，如 供应,包含,生产）',
    config              TEXT         DEFAULT NULL COMMENT '图谱配置（JSON）',
    error_message       VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    create_dept         BIGINT       DEFAULT NULL,
    create_by           BIGINT       DEFAULT NULL,
    create_time         DATETIME     DEFAULT NULL,
    update_by           BIGINT       DEFAULT NULL,
    update_time         DATETIME     DEFAULT NULL,
    del_flag            CHAR(1)      DEFAULT '0',
    attr1               VARCHAR(256) DEFAULT NULL,
    attr2               VARCHAR(256) DEFAULT NULL,
    attr3               VARCHAR(256) DEFAULT NULL,
    attr4               VARCHAR(256) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识图谱实例';

-- ============================================================
-- 图谱实体节点
-- ============================================================
CREATE TABLE ai_kg_entity (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       VARCHAR(20)  DEFAULT '000000',
    graph_id        BIGINT       NOT NULL COMMENT '图谱ID',
    entity_name     VARCHAR(256) NOT NULL COMMENT '实体名称',
    entity_type     VARCHAR(64)  DEFAULT NULL COMMENT '实体类型',
    description     VARCHAR(1024) DEFAULT NULL COMMENT '实体描述',
    confidence      DECIMAL(3,2) DEFAULT NULL COMMENT '置信度 0-1',
    source_chunk_id BIGINT       DEFAULT NULL COMMENT '来源chunkID',
    mention_count   INT          DEFAULT 1 COMMENT '被提及次数（同名合并）',
    create_time     DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_graph_id (graph_id),
    KEY idx_entity_name (entity_name),
    KEY idx_entity_type (entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识图谱实体';

-- ============================================================
-- 图谱关系
-- ============================================================
CREATE TABLE ai_kg_relation (
    id                  BIGINT       NOT NULL COMMENT '主键',
    tenant_id           VARCHAR(20)  DEFAULT '000000',
    graph_id            BIGINT       NOT NULL COMMENT '图谱ID',
    source_entity_id    BIGINT       NOT NULL COMMENT '头实体ID',
    target_entity_id    BIGINT       NOT NULL COMMENT '尾实体ID',
    relation_type       VARCHAR(64)  DEFAULT NULL COMMENT '关系类型',
    description         VARCHAR(1024) DEFAULT NULL COMMENT '关系描述',
    strength            INT          DEFAULT NULL COMMENT '关系强度 0-10',
    confidence          DECIMAL(3,2) DEFAULT NULL COMMENT '置信度 0-1',
    source_chunk_id     BIGINT       DEFAULT NULL COMMENT '来源chunkID',
    create_time         DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_graph_id (graph_id),
    KEY idx_source (source_entity_id),
    KEY idx_target (target_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识图谱关系';

-- ============================================================
-- 给 ai_knowledge_chunk 增加抽取跟踪列（ALTER）
-- ============================================================
ALTER TABLE ai_knowledge_chunk
    ADD COLUMN extraction_status CHAR(1) DEFAULT '0' COMMENT '图谱抽取状态: 0待处理 1处理中 2已完成 3失败' AFTER vector_id,
    ADD COLUMN entity_count      INT     DEFAULT 0   COMMENT '抽取实体数' AFTER extraction_status,
    ADD COLUMN relation_count    INT     DEFAULT 0   COMMENT '抽取关系数' AFTER entity_count,
    ADD COLUMN token_used        BIGINT  DEFAULT 0   COMMENT '抽取消耗token' AFTER relation_count,
    ADD COLUMN kg_error_message  VARCHAR(1024) DEFAULT NULL COMMENT '抽取错误信息' AFTER token_used;
```

### 5.2 抽取管线设计（核心，参照项目没有，需自研）

#### 5.2.1 抽取 Agent 接口

```java
package com.nexus.ai.agent;

public interface GraphExtractionAgent {
    @SystemMessage("""
        你是一个知识图谱实体关系抽取专家。从给定的文本片段中抽取实体和关系。

        抽取规则：
        1. 实体类型聚焦 ERP 业务域：物料、客户、供应商、订单、工单、BOM、车间、工序、设备、仓库等
        2. 关系类型聚焦业务语义：供应、采购、包含(子件)、生产于、存储于、隶属于、关联等
        3. 只抽取文本明确提及的事实，不要臆造
        4. 每个实体给出 type 和简短 description
        5. 每个关系给出 relation_type 和 description
        6. 置信度低于 0.5 的不抽取

        严格按以下 JSON 格式输出，不要输出任何其他内容：
        {
          "entities": [{"name":"...","type":"...","description":"...","confidence":0.9}],
          "relations": [{"source":"头实体名","target":"尾实体名","type":"关系类型","description":"...","strength":8,"confidence":0.9}]
        }
        """)
    @UserMessage("请从以下文本中抽取实体和关系：\n\n{{text}}")
    @Agent("知识图谱抽取专家")
    String extract(@V("text") String text);
}
```

#### 5.2.2 抽取服务

```java
package com.nexus.ai.service.kg;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;
    private final AiKnowledgeGraphMapper graphMapper;
    private final AiKgEntityMapper entityMapper;
    private final AiKgRelationMapper relationMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    @Qualifier("aiExecutor") private final Executor aiExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 异步构建知识库的图谱
     */
    @Async("aiExecutor")
    public void buildGraph(Long knowledgeId, Long modelId) {
        AiKnowledgeGraph graph = createGraphInstance(knowledgeId, modelId);
        List<AiKnowledgeChunk> chunks = chunkMapper.selectByKnowledgeId(knowledgeId);
        for (AiKnowledgeChunk chunk : chunks) {
            extractChunk(graph, chunk, modelId);
        }
        finalizeGraph(graph);
    }

    private void extractChunk(AiKnowledgeGraph graph, AiKnowledgeChunk chunk, Long modelId) {
        try {
            chunk.setExtractionStatus("1"); chunkMapper.updateById(chunk);
            ChatModel model = chatModelFactory.getChatModel(modelId);
            GraphExtractionAgent agent = AgenticServices.agentBuilder(GraphExtractionAgent.class)
                .chatModel(model).build();
            String json = agent.extract(chunk.getContent());
            GraphExtractionResult result = objectMapper.readValue(json, GraphExtractionResult.class);
            // 同名实体合并（mention_count++），关系落库
            persistExtraction(graph, chunk, result);
            chunk.setExtractionStatus("2");
            chunk.setEntityCount(result.getEntities().size());
            chunk.setRelationCount(result.getRelations().size());
        } catch (Exception e) {
            log.error("chunk {} 抽取失败", chunk.getId(), e);
            chunk.setExtractionStatus("3");
            chunk.setKgErrorMessage(e.getMessage());
        }
        chunkMapper.updateById(chunk);
    }
}
```

#### 5.2.3 图谱增强检索（接入现有 RetrieverService）

在 `RetrieverService` 的召回阶段增加**第三路：图谱召回**，与向量召回、关键词召回一起进 RRF 融合：

```java
// RetrieverService 新增图谱召回分支
private List<RetrievalChunk> performGraphRecall(Long knowledgeId, String query) {
    // 1. 从 query 中识别实体（名称模糊匹配 ai_kg_entity.entity_name）
    List<AiKgEntity> hitEntities = entityMapper.matchByNames(knowledgeId, extractKeywords(query));
    if (hitEntities.isEmpty()) return List.of();
    // 2. 找这些实体的邻居实体（1跳关系）
    Set<Long> neighborEntityIds = relationMapper.findNeighborEntityIds(hitEntities);
    // 3. 回溯到 source_chunk_id，取出关联 chunk
    return chunkMapper.selectByEntityIds(neighborEntityIds).stream()
        .map(c -> new RetrievalChunk(c.getContent(), c.getId(), "graph"))
        .toList();
}
```

> **检索增强效果**：例如问"物料 M001 的供应商还有哪些客户"，向量检索可能只命中含"M001"的 chunk，图谱检索能通过"供应商→客户"关系链找到关联 chunk。

### 5.3 前端

- `views/ai/knowledge/graph.vue`：知识库详情页新增"知识图谱"Tab
  - 图谱可视化（用 `vue-echarts` 的 graph 类型或 `antv/g6`，展示实体节点 + 关系边）
  - 构建按钮、进度条（基于 chunk 抽取状态）、实体/关系列表
  - 实体点击 → 高亮邻居 + 展示来源 chunk
- `api/ai/knowledgeGraph.ts`：构建、查询实体、查询关系、查询邻居

### 5.4 S2-A 交付清单

| # | 交付物 | 状态 |
|---|--------|------|
| 1 | 3 张新表 + `ai_knowledge_chunk` ALTER | ⬜ |
| 2 | `GraphExtractionAgent` 接口 + 抽取 Prompt | ⬜ |
| 3 | `KnowledgeGraphService`（异步构建 + 同名合并） | ⬜ |
| 4 | 图谱增强检索（RetrieverService 第三路召回 + RRF 融合） | ⬜ |
| 5 | `AiKnowledgeGraphController`（构建/查询/重抽） | ⬜ |
| 6 | 前端图谱可视化页面 | ⬜ |
| 7 | `GraphStoreStrategy` 接口（预留 Neo4j 实现） | ⬜ |

---

## 六、S2-B：推理模式 + 厂商增强（第 15-17 周）

> **目标**：① 支持推理模型（DeepSeek-R1 等）的思考 token 流式展示；② 把 `ChatModelFactory` 的单 switch 重构为厂商策略模式，接入原生 SDK + 新增厂商；③ 用专用 Rerank API 替代 LLM-as-judge。

### 6.1 推理（Thinking）模式

> **关键事实**：参照项目 `enableThinking` 有两套含义——推理 token 流（`returnThinking` + `onPartialThinking`）和 Supervisor 深度思考编排。当前 NexusERP 两套都没有。本阶段先做**推理 token 流**（更通用、更轻量），深度思考编排留给 Phase 6 工作流。

#### 6.1.1 请求 DTO 增字段

```java
// AiChatRequest 新增
private Boolean enableThinking = false;  // 是否开启推理模式（输出思考过程）
```

#### 6.1.2 ChatModelFactory 传 `returnThinking`

```java
// buildStreamingChatModel 增加 thinking 参数
private StreamingChatModel buildStreamingChatModel(AiModel config, boolean enableThinking) {
    return switch (providerType(config)) {
        case "OPENAI", "DASHSCOPE", "ZHIPU", "DEEPSEEK", "CUSTOM_API" ->
            OpenAiStreamingChatModel.builder()
                .baseUrl(config.getApiHost())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(...)
                .maxTokens(config.getMaxTokens())
                .listeners(chatModelListeners)
                .returnThinking(enableThinking)   // 关键：请求推理 token
                .build();
        // Ollama 原生 SDK 不支持 returnThinking，忽略
        case "OLLAMA" -> OllamaStreamingChatModel.builder()...build();
        ...
    };
}
```

#### 6.1.3 流式 Handler 接收思考 token

当前 `AiServiceFacade.invokeWithStreaming` 的 handler 只 override 了 `onPartialResponse(String)`，需新增 `onPartialThinking`：

```java
streamingModel.chat(ChatRequest.builder().messages(allMessages).build(),
    new StreamingChatResponseHandler() {
        @Override public void onPartialResponse(String token) { AiSseMessageHelper.sendContent(userId, token); }

        @Override
        public void onPartialThinking(PartialThinking thinking) {
            // 新事件类型 AI_THINKING_TOKEN，前端折叠展示思考过程
            AiSseMessageHelper.sendThinkingToken(userId, thinking.text());
        }

        @Override public void onCompleteResponse(ChatResponse r) { saveAiMessageWithTokens(...); sendDone(userId); }
        @Override public void onError(Throwable e) { sendError(userId, e.getMessage()); }
    });
```

#### 6.1.4 新增 SSE 事件类型

`AiSseMessageHelper` 增加 `AI_THINKING_TOKEN`（与现有 `AI_THINKING` 状态指示区分：前者是推理 token 内容，后者是"思考中"动画）。

#### 6.1.5 前端

- 对话输入框增加"深度思考"开关（参照项目 `DeepThinking` 组件）
- 思考过程折叠展示（`<details>` 或可展开区块，默认折叠，标注"思考过程"）
- 现有 `AI_THINKING` 与 `AI_THINKING_TOKEN` 区分渲染

### 6.2 厂商策略模式重构

> **当前问题**：`ChatModelFactory` 是单 `switch(providerType)`，新增厂商要改 switch；且 DASHSCOPE/ZHIPU 走 OpenAI 兼容，没用原生 SDK。

#### 6.2.1 引入 ChatServiceProvider 策略接口

```java
package com.nexus.ai.service.chat.provider;

public interface ChatServiceProvider {
    /** 厂商编码，对应 ai_provider.provider_type */
    String getProviderType();

    /** 构建流式模型 */
    StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request);

    /** 构建同步模型（Agent 用，默认 OpenAI 兼容） */
    default ChatModel buildChatModel(AiModel config) {
        return OpenAiChatModel.builder()
            .baseUrl(config.getApiHost()).apiKey(config.getApiKey())
            .modelName(config.getModelName()).build();
    }
}
```

#### 6.2.2 厂商实现（新增/原生 SDK 化）

| 厂商实现类 | providerType | SDK | returnThinking | 说明 |
|-----------|-------------|-----|---------------|------|
| `OpenAiServiceProvider` | OPENAI | `OpenAiStreamingChatModel` | ✅ | 已有，抽成策略 |
| `DeepSeekServiceProvider` | DEEPSEEK | `OpenAiStreamingChatModel` | ✅ | **新增**，OpenAI 兼容 + thinking |
| `DashScopeServiceProvider` | DASHSCOPE | `QwenStreamingChatModel` | ❌ | **改用原生 SDK**（`langchain4j-community-dashscope`，已声明） |
| `ZhiPuServiceProvider` | ZHIPU | `ZhipuAiStreamingChatModel` | ❌ | **改用原生 SDK**，注意用 `.model()` 不是 `.modelName()` |
| `OllamaServiceProvider` | OLLAMA | `OllamaStreamingChatModel` | ❌ | 已有，抽成策略 |
| `CustomApiServiceProvider` | CUSTOM_API | `OpenAiStreamingChatModel` | ✅ | **新增**，OpenAI 兼容 + URL 规范化 |
| `MinimaxServiceProvider` | MINIMAX | `OpenAiStreamingChatModel` | ✅ | **新增** |
| `MiMoServiceProvider` | XIAOMI | `OpenAiStreamingChatModel` | ✅ | **新增**（小米 MiMo） |

> **关键坑**：`ZhipuAiStreamingChatModel.builder().model(...)` 用 `model`，其他厂商用 `modelName`。

```java
@Component
public class ZhiPuServiceProvider implements ChatServiceProvider {
    @Override public String getProviderType() { return "ZHIPU"; }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        return ZhipuAiStreamingChatModel.builder()
            .apiKey(config.getApiKey())
            .model(config.getModelName())        // 注意：model 不是 modelName
            .listeners(chatModelListeners)
            .build();
    }
}
```

#### 6.2.3 工厂自动发现（替代 switch）

```java
@Component
public class ChatServiceProviderFactory implements ApplicationContextAware {
    private final Map<String, ChatServiceProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        ctx.getBeansOfType(ChatServiceProvider.class).values()
           .forEach(p -> providers.put(p.getProviderType().toUpperCase(), p));
    }

    public ChatServiceProvider get(String providerType) {
        return providers.getOrDefault(providerType.toUpperCase(),
            providers.get("OPENAI"));  // 兜底 OpenAI 兼容
    }
}
```

#### 6.2.4 ChatModelFactory 改造

`ChatModelFactory` 保留缓存职责，构建委托给 `ChatServiceProviderFactory`：

```java
public StreamingChatModel getStreamingChatModel(Long modelId, AiChatRequest request) {
    // 缓存 key 需含 enableThinking，避免 thinking/非thinking 串用
    String key = modelId + ":" + (Boolean.TRUE.equals(request.getEnableThinking()) ? "T" : "F");
    return streamingModelCache.computeIfAbsent(key, k -> {
        AiModel config = aiModelService.selectById(modelId);
        return providerFactory.get(config.getProviderType())
            .buildStreamingChatModel(config, request);
    });
}
```

### 6.3 专用 Rerank API（替代 LLM-as-judge）

> **当前问题**：`RerankService` 用 ChatModel 让 LLM 打 0-10 分，慢且贵、不够准。专用 Rerank（Cross-Encoder）更快更准。

#### 6.3.1 RerankProvider 策略接口

```java
package com.nexus.ai.service.rag.rerank;

public interface RerankProvider {
    String getProviderType();  // BAILIAN / ZHIPU / SILICONFLOW
    List<RerankResult> rerank(String query, List<String> documents, int topN);
}
```

#### 6.3.2 三个实现（HTTP 调各自 Rerank API）

| 实现类 | API 端点 | 鉴权 |
|--------|---------|------|
| `BailianRerankProvider` | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` | API Key |
| `ZhiPuRerankProvider` | `https://open.bigmodel.cn/api/paas/v4/rerank` | JWT（API Key 拆 id/secret） |
| `SiliconFlowRerankProvider` | `https://api.siliconflow.cn/v1/rerank` | Bearer Token |

#### 6.3.3 RerankService 改造

```java
// 原 rerankWithLLM 保留作为兜底（无配置 rerank 模型时用）
// 新增 rerankWithApi 优先走专用 API
public List<RetrievalChunk> rerank(String query, List<RetrievalChunk> candidates, AiKnowledge kb) {
    if (kb.getEnableRerank() && kb.getRerankModel() != null) {
        AiModel rerankModel = aiModelService.selectById(kb.getRerankModel());
        RerankProvider provider = rerankProviderFactory.get(rerankModel.getProviderType());
        if (provider != null) {
            return rerankWithApi(provider, query, candidates, kb.getRetrieveLimit());
        }
    }
    return rerankWithLLM(query, candidates);  // 兜底
}
```

#### 6.3.4 模型管理

`ai_model.model_type` 已支持 `RERANK`，在模型管理页面新增 Rerank 模型配置（选厂商 + 填 API Key）。

### 6.4 S2-B 交付清单

| # | 交付物 | 状态 |
|---|--------|------|
| 1 | `AiChatRequest.enableThinking` + `enableWebSearch` 前端深度思考开关 | ✅ |
| 2 | `returnThinking` + `onPartialThinking` + `AI_THINKING_TOKEN` 事件 | ✅ 已验证（Chat/RAG 模式推理 token 流正常） |
| 3 | `ChatServiceProvider` 策略接口 + `ChatServiceProviderFactory` | ✅ |
| 4 | 8 个厂商实现（DeepSeek/CustomApi/Minimax/MiMo 新增 + DashScope/ZhiPu 原生 SDK） | ✅ 含 `AbstractOpenAiCompatibleProvider` 基类 |
| 5 | `ChatModelFactory` 重构（缓存含 thinking 维度） | ✅ key=`modelId:T/F`；`AiModelChangeListener` 已接 evictCache |
| 6 | `RerankProvider` + 3 个专用 Rerank 实现 | ✅ Bailian/ZhiPu(手写JWT)/SiliconFlow |
| 7 | `RerankService` 改造（API 优先 + LLM 兜底） | ✅ `RetrieverService` 无需改 |
| 8 | 前端思考过程折叠展示 | ✅ `<details>` + `reasoning` 字段 + history 加载映射 |

---

## 七、S2-C：联网搜索 + MCP 市场 + 多模态（第 18-22 周）

### 7.1 联网搜索（WebSearchAgent）

> **关键决策：Java HTTP 工具 vs MCP STDIO 工具**
> - 参照项目用 `npx` 拉 `bing-cn-mcp`/`@playwright/mcp`，**Bing 还被注释掉了**，生产靠 npx 不稳。
> - **推荐主路径用 Java HTTP 搜索工具**（Tavily/Bing Search API），不依赖 npx，稳定可控。
> - **可选辅路径**：网页深度抓取/浏览器自动化用 Playwright MCP（通过现有 `McpClientManager` 配 LOCAL 工具）。

#### 7.1.1 搜索工具（Java 实现）

```java
package com.nexus.ai.tool;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    @Value("${nexus.ai.web-search.provider:tavily}")
    private String provider;
    @Value("${nexus.ai.web-search.api-key:}")
    private String apiKey;

    @Tool("联网搜索最新信息，返回相关网页摘要和链接")
    public String webSearch(@P("搜索关键词") String query) {
        return switch (provider) {
            case "tavily" -> searchByTavily(query);   // POST https://api.tavily.com/search
            case "bing"   -> searchByBing(query);     // GET https://api.bing.microsoft.com/v7.0/search
            default -> throw new IllegalStateException("不支持的搜索厂商: " + provider);
        };
    }

    @Tool("抓取指定URL网页正文内容")
    public String crawlWebpage(@P("网页URL") String url) { /* Jsoup 抽取正文 */ }
}
```

#### 7.1.2 WebSearchAgent

```java
public interface WebSearchAgent {
    @SystemMessage("""
        你是一个联网搜索助手。当用户问题需要最新信息或你无法从 ERP 数据回答时，使用 web_search 搜索，
        必要时用 crawl_webpage 抓取网页详情。回答中必须标注信息来源链接。
        除非用户明确要求联网，否则优先用 ERP 数据工具回答。
        """)
    @UserMessage("{{query}}")
    @Agent("联网搜索助手")
    String search(@V("query") String query);
}
```

#### 7.1.3 接入 Supervisor

`AiServiceFacade` 的 auto 路由增加联网判断（用户勾选"联网"或问题含"最新/今天/新闻/股价"等），构建含 `WebSearchAgent` 的 Supervisor。

#### 7.1.4 前端

- 输入框增加"联网"开关
- 搜索结果带来源链接卡片（`<a>` 可点击）

### 7.2 MCP 市场

> **参照项目可复用**：`McpMarketController` + `McpMarketServiceImpl` 的 refresh/load 逻辑可直接移植，**只需把"加载到本地"的目标从参照的 `mcp_tool_info` 改为当前 `ai_tool` 表**（当前已统一）。

#### 7.2.1 数据表（2 张）

```sql
-- MCP 市场源
CREATE TABLE ai_mcp_market (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     VARCHAR(20)  DEFAULT '000000',
    name          VARCHAR(200) NOT NULL COMMENT '市场名称',
    url           VARCHAR(500) NOT NULL COMMENT '市场URL（返回工具列表JSON）',
    description   VARCHAR(512) DEFAULT NULL,
    auth_config   TEXT         DEFAULT NULL COMMENT '认证配置（JSON）',
    status        VARCHAR(16)  DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    create_dept   BIGINT       DEFAULT NULL, create_by BIGINT DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL, update_by BIGINT DEFAULT NULL, update_time DATETIME DEFAULT NULL,
    del_flag      CHAR(1)      DEFAULT '0',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI MCP 市场源';

-- 市场工具
CREATE TABLE ai_mcp_market_tool (
    id              BIGINT       NOT NULL COMMENT '主键',
    market_id       BIGINT       NOT NULL COMMENT '市场ID',
    tool_name       VARCHAR(200) NOT NULL,
    tool_description TEXT        DEFAULT NULL,
    tool_version    VARCHAR(50)  DEFAULT NULL,
    tool_metadata   TEXT         DEFAULT NULL COMMENT '工具元数据JSON',
    is_loaded       CHAR(1)      DEFAULT '0' COMMENT '是否已加载到本地ai_tool',
    local_tool_id   BIGINT       DEFAULT NULL COMMENT '关联ai_tool.id',
    create_time     DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_market_id (market_id),
    KEY idx_tool_name (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI MCP 市场工具';
```

#### 7.2.2 服务（移植参照项目，改写入目标）

```java
@Service
@RequiredArgsConstructor
public class McpMarketService {
    private final AiMcpMarketMapper marketMapper;
    private final AiMcpMarketToolMapper marketToolMapper;
    private final AiToolMapper toolMapper;        // 当前项目统一表
    private final McpClientManager mcpClientManager;

    /** 刷新市场工具列表：HTTP GET market.url，解析 JSON 工具列表 */
    public McpMarketRefreshResult refresh(Long marketId) { /* hutool HttpRequest */ }

    /** 一键加载：从 tool_metadata 物化一条 ai_tool 记录（LOCAL/REMOTE） */
    public Long loadToolToLocal(Long marketToolId) {
        AiMcpMarketTool mt = marketToolMapper.selectById(marketToolId);
        AiTool tool = new AiTool();
        tool.setToolName(mt.getToolName());
        // metadata 有 baseUrl → REMOTE；否则 LOCAL（command/args）
        tool.setToolType(hasBaseUrl(mt) ? "REMOTE" : "LOCAL");
        tool.setConfigJson(buildConfig(mt));
        tool.setStatus("ENABLED");
        toolMapper.insert(tool);
        mt.setIsLoaded("1"); mt.setLocalToolId(tool.getId());
        marketToolMapper.updateById(mt);
        return tool.getId();
    }
}
```

#### 7.2.3 前端

- `views/ai/mcp/market.vue`：市场源 CRUD + 工具列表 + 一键加载 + 批量加载
- 加载后的工具自动出现在现有 MCP 工具管理页（复用 `mcp/index.vue`）

### 7.3 多模态：文生图

> **依赖已就绪**：`langchain4j-community-dashscope` 已声明，`com.alibaba.dashscope.aigc.imagesynthesis.*` 随传递依赖可用，无需额外加依赖。

#### 7.3.1 抽象接口（放 ruoyi-common-ai）

```java
package com.nexus.common.ai.service.image;

public interface IImageGenerationService {
    String generateImage(ImageContext ctx);   // 返回图片URL
    String getProviderName();
}

@Data @Builder
public class ImageContext {
    private AiModel modelConfig;
    private String prompt;
    private String size;       // 如 "1280*1280"
    private Integer seed;
}
```

#### 7.3.2 通义万相实现

```java
@Service
public class TongYiWanxImageService implements IImageGenerationService {
    @Override
    public String generateImage(ImageContext ctx) {
        ImageSynthesisParam param = ImageSynthesisParam.builder()
            .apiKey(ctx.getModelConfig().getApiKey())
            .model(ctx.getModelConfig().getModelName())
            .prompt(ctx.getPrompt())
            .size(StringUtils.isBlank(ctx.getSize()) ? "1280*1280" : ctx.getSize())
            .n(1).build();
        ImageSynthesisResult result = new ImageSynthesis().call(param);
        return result.getOutput().getResults().get(0).get("url");
    }
    @Override public String getProviderName() { return "Tongyiwanx"; }
}
```

#### 7.3.3 ModelType 枚举扩展

`ModelTypeEnum` 增加 `IMAGE` 类型，模型管理页支持配置 IMAGE 类型的模型（选厂商为通义万相）。

#### 7.3.4 调用入口

- **聊天内**：新增 `AI_IMAGE` SSE 事件，用户说"画一个..."时路由到图像生成（可作为 ErpReportAgent 之外的新能力，或独立 ImageGenerationAgent）
- **预留工作流**：`IImageGenerationService` 设计为可被 Phase 6 工作流的 ImageNode 复用（参照 `WorkflowUtil.buildTextToImage`）

#### 7.3.5 前端

- 对话中图片结果渲染（`<img>` + 点击放大）
- 模型管理支持 IMAGE 类型

### 7.4 S2-C 交付清单

| # | 交付物 | 状态 |
|---|--------|------|
| 1 | `WebSearchTool`（Tavily/Bing Java 实现）+ `crawlWebpage` | ✅ 含 Jsoup 超时防护 |
| 2 | `WebSearchAgent` + 联网路由（独立 invokeWithWebSearch） | ✅ auto 模式路由 |
| 3 | 前端联网开关 + 来源链接卡片 | ✅ `WebSearchToggle.vue` + `AI_WEB_SEARCH` 来源卡 |
| 4 | `ai_mcp_market` + `ai_mcp_market_tool` 表 | ✅ `doc/ai/sql/archive/ai-stage2-c.sql` |
| 5 | `McpMarketService` + `McpMarketController`（移植参照，改写 ai_tool，默认 DISABLED） | ✅ |
| 6 | 前端 MCP 市场页 | ✅ `api/ai/market.ts` + `views/ai/mcp/market.vue` + 菜单 |
| 7 | `IImageGenerationService` + 通义万相实现（含 OSS 持久化） | ✅ `TongYiWanxImageService` |
| 8 | `AiModelTypeEnum.IMAGE` + `selectDefaultImageModel()` + 模型管理 | ✅ 前端 IMAGE option 已加 |
| 9 | `AI_IMAGE` SSE 事件 + 聊天内文生图（facade invokeWithImageGeneration） | ✅ 前端 `AI_IMAGE` 接线完成 |
| 10 | 前端图片渲染 | ✅ `components/ai/ImageRenderer.vue` + markdown 图片渲染 |

---

## 八、数据库变更总览（二阶段）

| 表名 | 用途 | 子阶段 | 状态 |
|------|------|--------|------|
| `ai_knowledge_graph` | 知识图谱实例 | S2-A | ✅ `doc/ai/sql/archive/ai-stage2-a.sql` |
| `ai_kg_entity` | 图谱实体（含 community_id） | S2-A | ✅ `doc/ai/sql/archive/ai-stage2-a.sql` |
| `ai_kg_relation` | 图谱关系 | S2-A | ✅ `doc/ai/sql/archive/ai-stage2-a.sql` |
| `ai_kg_community` | 图谱社区（GraphRAG 社区检测+摘要） | S2-A | ✅ `doc/ai/sql/archive/ai-stage2-a.sql` |
| `ai_knowledge_chunk`（ALTER） | 增抽取跟踪列 | S2-A | ✅ `doc/ai/sql/archive/ai-stage2-a.sql` |
| `ai_mcp_market` | MCP 市场源 | S2-C | ✅ `doc/ai/sql/archive/ai-stage2-c.sql` |
| `ai_mcp_market_tool` | 市场工具 | S2-C | ✅ `doc/ai/sql/archive/ai-stage2-c.sql` |
| `ai_message`（ALTER） | 增 `thinking_content` 列（存思考过程） | S2-B | ✅ `doc/ai/sql/archive/ai-stage2-b.sql` |

> 脚本位置：`doc/ai/sql/archive/ai-stage2-a.sql`（S2-A）、`doc/ai/sql/archive/ai-stage2-b.sql`（S2-B）、`doc/ai/sql/archive/ai-stage2-c.sql`（S2-C）。

---

## 九、前端变更总览（二阶段）

| 文件 | 说明 | 子阶段 | 状态 |
|------|------|--------|------|
| `api/ai/knowledgeGraph.ts` | 图谱接口 | S2-A | ✅ |
| `views/ai/knowledge/graph.vue` | 图谱可视化（echarts 按社区着色 + 进度轮询 + 全局检索） | S2-A | ✅ |
| `views/ai/knowledge/index.vue`（改） | 右侧改 el-tabs，新增「知识图谱」Tab | S2-A | ✅ |
| `components/ai/DeepThinking.vue` | 深度思考开关 + 折叠展示 | S2-B | ✅ |
| `api/ai/chat.ts`（改） | `sendAiMessage` 加 `enableThinking`/`enableWebSearch` | S2-B | ✅ |
| `views/ai/assistant/index.vue`（改） | 思考折叠展示 + `AI_THINKING_TOKEN` | S2-B | ✅ |
| `components/ai/AiChatDrawer.vue`（改） | 思考折叠展示 + `AI_THINKING_TOKEN` | S2-B | ✅ |
| `api/ai/market.ts` | MCP 市场接口 | S2-C | ⬜ 待实现 |
| `views/ai/mcp/market.vue` | MCP 市场页 | S2-C | ⬜ 待实现 |
| `components/ai/ImageRenderer.vue` | 图片结果渲染 | S2-C | ⬜ 待实现 |
| `views/ai/model/index.vue`（改） | 支持 IMAGE 类型 | S2-C | ⬜ 待实现 |
| `views/ai/assistant/index.vue`（改） | 联网开关 + `AI_IMAGE`/`AI_WEB_SEARCH` 接线 | S2-C | ⬜ 待实现 |

---

## 十、开发排期总览（二阶段）

| 子阶段 | 周期 | 核心交付 | 人天估算 | 状态 |
|--------|------|----------|----------|------|
| **S2-A** 知识图谱 | 第 12-16 周 | 图谱构建 + 抽取 + 社区/全局检索 + Agent @Tool + 前端 | 26 人天 | ✅ 代码完成（待运行时验证） |
| **S2-B** 推理 + 厂商增强 | 第 15-17 周 | Thinking 流 + 策略模式厂商 + 专用 Rerank | 16 人天 | ✅ 后端+前端完成 |
| **S2-C** 联网 + 市场 + 多模态 | 第 18-22 周 | WebSearchAgent + MCP 市场 + 文生图 | 24 人天 | ✅ 后端+前端完成 |
| **合计** | 12 周 | | **66 人天** | S2-A 26✅(待验证) / S2-B 16✅ / S2-C 24✅ |

> 加上一阶段已完成 138 人天，截至目前累计完成约 **204 人天**（S2-A 代码完成 26 人天，待运行时验证）。

---

## 十一、风险与注意事项

### 11.1 知识图谱

| 风险 | 应对 |
|------|------|
| LLM 抽取 JSON 格式不稳 | Prompt 强约束 + JSON 解析失败重试 1 次 + 兜底正则提取 |
| 同名实体合并误判 | 合并前做实体消歧（type + description 相似度），不确定时保留为独立实体 |
| 抽取 token 成本高 | 按 batch 处理 chunk、限制每 chunk 实体/关系数、抽取模型选便宜的 |
| MySQL 图查询性能 | 实体/关系表加好索引；邻居查询限制 1 跳；大图谱预留 Neo4j 迁移接口 |

### 11.2 推理模式

| 风险 | 应对 |
|------|------|
| `returnThinking` 部分厂商不支持 | 策略接口按厂商区分，不支持的厂商忽略 thinking 参数 |
| 思考 token 计费 | 思考 token 也计入 `ai_token_usage`（input_tokens），成本展示给用户 |
| 缓存串用 | 缓存 key 含 `enableThinking` 维度，避免 thinking/非 thinking 模型实例复用 |
| ZhipuAi builder 用 `.model()` | 代码注释标注，单元测试覆盖 |

### 11.3 厂商重构

| 风险 | 应对 |
|------|------|
| 重构影响现有 4 厂商 | 策略模式向后兼容，OPENAI/OLLAMA 行为不变；灰度切换 |
| 原生 SDK 与 OpenAI 兼容行为差异 | DashScope/ZhiPu 切原生后回归测试流式/工具调用/Token 统计 |

### 11.4 联网搜索

| 风险 | 应对 |
|------|------|
| 搜索 API key 成本 | 默认关闭，用户主动开；Tavily 有免费额度 |
| 搜索结果质量 | Rerank 环节对搜索结果也重排；限制 topN |
| 网页抓取安全 | crawl_webpage 限制白名单域名、超时、大小上限 |

### 11.5 MCP 市场

| 风险 | 应对 |
|------|------|
| 市场源 URL 不可信 | refresh 超时 30s、响应大小上限、JSON 校验 |
| 加载恶意工具 | 加载后默认 DISABLED，需管理员手动启用 + 测试连接 |

### 11.6 多模态

| 风险 | 应对 |
|------|------|
| 图片内容安全 | 接入内容安全审核（可选），prompt 黑名单 |
| 图片 URL 时效 | 通义万相返回的 URL 有时效，下载存 OSS 长期保存 |

---

## 十二、参照项目可复用性评估（诚实说明）

| 能力 | 参照项目状态 | 本计划做法 |
|------|------------|-----------|
| 知识图谱 | **仅脚手架**（实体/CRUD 在，抽取/Neo4j/检索/DDL/前端全无，neo4j 依赖未用） | 自研抽取管线，v1 用 MySQL，预留 Neo4j 接口 |
| 推理 token 流 | 有（`returnThinking` + `onPartialThinking`） | 直接借鉴实现 |
| 推理深度编排（Supervisor） | 有（`handleThinkingMode`） | 留给 Phase 6 工作流，本阶段只做 token 流 |
| 厂商策略模式 | 有（`AbstractChatService` + 9 厂商） | 借鉴架构，实现 8 个 |
| 专用 Rerank | 有（百炼/智谱/硅基） | 移植 3 个 API 实现 |
| 联网搜索 | MCP 方式（Playwright/bing），Bing 注释掉 | 改 Java HTTP 工具（Tavily/Bing）更稳，Playwright 作可选 |
| MCP 市场 | 完整 | 移植，加载目标改 `ai_tool` |
| 文生图 | 通义万相（dashscope 原生） | 移植，依赖已就绪 |
| 知识图谱 Neo4j 接线 | 无 | 自研（v2 可选） |

> **结论**：S2-B（推理+厂商+Rerank）和 S2-C 的 MCP 市场、文生图可大量借鉴参照项目；**S2-A 知识图谱需自研**，是本阶段工作量最大、创新性最高的部分。

---

## 附录：与主计划的衔接建议

1. 本计划完成后，建议在 `NexusERP_AI开发计划.md` 主文档新增"第二阶段：AI 平台能力深化"章节，并把原 Phase 3-6 周期整体后移至第 23 周起。
2. 主文档"十、数据库变更总览"补充本阶段 6 张表/变更。
3. 主文档"十二、开发排期总览"更新累计人天与周期。
4. 本阶段产出的 `IImageGenerationService`、MCP 市场能力、`GraphExtractionAgent` 将被 Phase 6 工作流引擎复用，设计时需预留可被工作流节点调用的接口。
