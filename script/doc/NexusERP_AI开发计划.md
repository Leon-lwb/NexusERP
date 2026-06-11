# NexusERP AI 开发计划（优化版 v4）

> **优化说明**：本版在 v3 基础上，完成第四周开发任务（修复补齐 + 重排序集成），新增 9 处修正（含 1 处严重）。主要变更：Milvus 精确删除实现、RerankService 并行打分+ChatModelFactory复用、EmbeddingService 策略模式重构、可观测性监听器增强、前端检索测试面板参数化。

---

## 一、总体架构设计

### 1.1 AI 模块在 nexus-erp 中的定位

```
nexus-erp/
├── ruoyi-admin/                    # 启动入口（已有）
├── ruoyi-common/                   # 公共模块（已有）
│   ├── ruoyi-common-sse/           # SSE 基础设施（已有，直接使用，无需拷贝）
│   ├── ruoyi-common-core/
│   └── ...
├── ruoyi-modules/
│   ├── ruoyi-erp/                  # ERP 业务模块（已完成）
│   ├── ruoyi-system/               # 系统管理（已完成）
│   ├── ruoyi-flowable/             # 工作流（已完成）
│   ├── ruoyi-im/                   # 即时通讯（已完成）
│   ├── ruoyi-ai/                   # 【新增】AI 核心模块
│   ├── ruoyi-mes/                  # 【规划中】MES 模块
│   └── ruoyi-iot/                  # 【规划中】IoT 模块
└── plus-ui/                        # 前端（已有，需扩展）
```

### 1.2 新增模块规划

nexus-erp 需要新增 **2 个模块**（参考 ruoyi-ai 的 `ruoyi-common-chat` + `ruoyi-chat` 分层）：

```
ruoyi-common/
│   └── ruoyi-common-ai/            # 【新增】AI 公共模块（BO/VO/DTO/Entity/枚举）
│
ruoyi-modules/
│   └── ruoyi-ai/                   # 【新增】AI 核心业务模块
```

#### ruoyi-common-ai 内部结构

```
ruoyi-common-ai/
└── src/main/java/com/nexus/common/ai/
    ├── domain/
    │   ├── entity/
    │   │   ├── AiProvider.java          # 厂商配置实体
    │   │   ├── AiModel.java             # 模型配置实体
    │   │   ├── AiSession.java           # 会话实体
    │   │   └── AiMessage.java           # 消息实体
    │   ├── bo/                          # 业务对象
    │   │   ├── AiProviderBo.java
    │   │   ├── AiModelBo.java
    │   │   └── AiChatBo.java
    │   └── vo/                          # 视图对象
    │       ├── AiProviderVo.java
    │       ├── AiModelVo.java
    │       └── AiMessageVo.java
    ├── dto/
    │   └── AiChatRequest.java           # 聊天请求 DTO
    ├── enums/
    │   ├── ModelTypeEnum.java           # CHAT/EMBEDDING/RERANK
    │   └── MessageRoleEnum.java         # USER/ASSISTANT/SYSTEM
    └── service/
        └── IAiModelService.java         # 模型查询接口（供 ruoyi-ai 调用）
```

#### ruoyi-ai 内部结构

```
ruoyi-ai/
└── src/main/java/com/nexus/ai/
    ├── controller/
    │   ├── AiChatController.java            # 聊天主接口（SSE 流式）
    │   ├── AiKnowledgeController.java       # 知识库管理
    │   └── AiModelController.java           # 模型配置管理
    ├── service/
    │   ├── facade/
    │   │   └── AiServiceFacade.java         # 门面：路由 RAG/Agent/普通对话
    │   ├── chat/
    │   │   ├── ChatModelFactory.java        # 模型工厂（构建 ChatModel + StreamingChatModel，含缓存）
    │   │   └── PersistentChatMemory.java    # 会话记忆持久化
    │   ├── query/
    │   │   └── ErpQueryService.java         # 【关键新增】ERP 数据源 SQL 执行服务（@DS("erp")）
    │   └── rag/
    │       ├── DocumentParserService.java   # 文档解析（PDF/Word/Excel）
    │       ├── ChunkingService.java         # 文本分段
    │       ├── EmbeddingService.java        # 向量化
    │       ├── VectorStoreService.java      # 向量存储（Milvus）
    │       └── RetrieverService.java        # 检索+重排序
    ├── agent/                                # Agent 接口定义
    │   ├── ErpQueryAgent.java               # ERP 数据查询 Agent
    │   ├── ErpReportAgent.java              # ERP 报表生成 Agent（含 ECharts JSON 输出）
    │   ├── MesAnalysisAgent.java            # MES 生产分析 Agent
    │   └── IotMonitorAgent.java             # IoT 设备监控 Agent
    ├── tool/                                 # Agent 可调用的工具
    │   ├── QueryAllTablesTool.java          # 查询数据库表（注入 ErpQueryService）
    │   ├── QueryTableSchemaTool.java        # 查询表结构（注入 ErpQueryService）
    │   ├── ExecuteSqlQueryTool.java         # 执行 SQL（注入 ErpQueryService，含安全校验）
    │   ├── ErpInventoryTool.java            # 库存/补货相关工具
    │   ├── ErpPurchaseTool.java             # 采购/供应商相关工具
    │   ├── ErpSalesTool.java                # 销售/客户相关工具
    │   ├── ErpProductionTool.java           # 生产/排程相关工具
    │   ├── MesProductionTool.java           # MES 生产执行工具
    │   ├── MesQualityTool.java              # MES 质量管理工具
    │   ├── MesEquipmentTool.java            # MES 设备管理工具
    │   ├── IotDeviceTool.java               # IoT 设备监控工具
    │   └── IotAlertTool.java               # IoT 告警分析工具
    ├── mcp/
    │   └── registry/
    │       └── ErpBuiltinToolRegistry.java  # 内置工具注册中心
    ├── config/
    │   ├── LangChain4jConfig.java           # LangChain4j 自动配置
    │   ├── VectorStoreConfig.java           # 向量数据库配置
    │   ├── AiProperties.java               # AI 属性配置（前缀 nexus.ai）
    │   └── AiThreadPoolConfig.java          # 【新增】AI 专用线程池配置
    └── observability/
        ├── AiAgentListener.java
        └── AiModelListener.java
```

**关键修正说明（v2→v3）**：
- ~~`SupervisorAgent.java`~~ 已移除：SupervisorAgent 是 LangChain4j 内置类，通过 `AgenticServices.supervisorBuilder()` 构建
- ~~`EchartsTool.java`~~ 已移除：ECharts 图表由 LLM 直接生成 JSON 配置
- `ErpBusinessTool` 拆分为 4 个领域 Tool：库存、采购、销售、生产
- 新增 `ruoyi-common-ai`：存放共享实体和接口
- `ChatModelFactory` 同时构建 `ChatModel` + `StreamingChatModel`，并增加缓存
- **【v3新增】** `ErpQueryService`：所有 SQL Tool 不再直接获取 DataSource，而是注入此 Service（标注 `@DS("erp")`），解决数据源指向错误问题
- **【v3新增】** `AiThreadPoolConfig`：AI 专用线程池，避免 CompletableFuture.runAsync() 耗尽 ForkJoinPool
- **【v3新增】** 所有 SQL Tool（QueryAllTablesTool、QueryTableSchemaTool、ExecuteSqlQueryTool）均注入 `ErpQueryService`，不再直接使用 `SpringUtils.getBean(DataSource.class)`

### 1.3 前端 AI 页面规划

在 `plus-ui/` 中新增：

```
plus-ui/src/
├── api/ai/                          # AI 接口
│   ├── chat.ts                      # 聊天接口
│   ├── knowledge.ts                 # 知识库接口
│   └── model.ts                     # 模型配置接口
├── components/ai/                   # AI 通用组件
│   ├── AiChatDrawer.vue             # AI 聊天抽屉（全局浮窗）
│   ├── AiChatPanel.vue              # AI 聊天面板（嵌入页面）
│   └── EchartsRenderer.vue          # ECharts 渲染组件
└── views/ai/                        # AI 管理页面
    ├── assistant.vue                 # 智能助手主页面
    ├── knowledge/                    # 知识库管理
    │   ├── index.vue
    │   └── fragment.vue
    └── model/                        # 模型管理
        ├── index.vue
        └── provider.vue
```

---

## 二、技术栈选型

### 2.1 后端依赖（新增）

> **修正**：社区版依赖的版本号为 `${langchain4j.community.version}`（1.13.0-beta23），核心版为 `${langchain4j.version}`（1.13.0）。groupId 全部为 `dev.langchain4j`。

```xml
<!-- 版本属性（添加到 nexus-erp 主 pom.xml 的 <properties> 中） -->
<langchain4j.version>1.13.0</langchain4j.version>
<langchain4j.community.version>1.13.0-beta23</langchain4j.community.version>
<langgraph4j.version>1.5.3</langgraph4j.version>

<!-- ===== ruoyi-common-ai 依赖 ===== -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>${langchain4j.version}</version>
</dependency>

<!-- ===== ruoyi-ai 依赖 ===== -->

<!-- 核心模型（稳定版，使用 langchain4j.version） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>${langchain4j.version}</version>
</dependency>

<!-- 社区扩展（beta 版，使用 langchain4j.community.version） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-zhipu-ai</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mcp</artifactId>
    <version>${langchain4j.community.version}</version>
</dependency>
```

### 2.2 前端依赖（新增）

> **注意**：vue-element-plus-x 1.3.0 基于 Element Plus 2.10.x 开发，nexus-erp 使用 Element Plus 2.13.5。需在安装后验证兼容性，如有问题可锁定 Element Plus 版本或等 vue-element-plus-x 更新。

```bash
cd E:\Nexus\nexus-erp\plus-ui
npm install vue-element-plus-x@1.3.0 hook-fetch@2.0.4-beta.1 github-markdown-css
```

### 2.3 中间件（新增）

| 组件 | 版本 | 用途 | 部署方式 |
|------|------|------|----------|
| Milvus | 2.4+ | 向量数据库（RAG 知识库） | Docker |
| Redis | 已有 | SSE 连接管理 / 会话缓存 | 已有 |

---

## 三、Phase 0：AI 基础设施搭建（第 1-3 周）

> **目标**：搭建 AI 模块骨架，跑通"用户提问 → LLM 流式回答"的最小闭环。

### 3.1 后端工程搭建

#### 3.1.1 创建 Maven 模块

| 步骤 | 具体操作 |
|------|----------|
| 创建 `ruoyi-common-ai` | 在 `ruoyi-common/` 下新建，参考 `E:\ruoyi-ai\ruoyi-ai\ruoyi-common\ruoyi-common-chat\pom.xml` |
| 创建 `ruoyi-ai` | 在 `ruoyi-modules/` 下新建，参考 `E:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\pom.xml` |
| 注册到父 pom | `ruoyi-common/pom.xml` 添加 `<module>ruoyi-common-ai</module>`；`ruoyi-modules/pom.xml` 添加 `<module>ruoyi-ai</module>` |
| 注册到 admin | `ruoyi-admin/pom.xml` 添加 `ruoyi-ai` 依赖 |
| 依赖关系 | `ruoyi-ai` → `ruoyi-common-ai` → `ruoyi-common-core` + `ruoyi-common-sse` + `ruoyi-common-mybatis` + `ruoyi-common-tenant` |

#### 3.1.2 模型配置管理（数据库表）

> **修正**：初版 DDL 缺少 nexus-erp 框架要求的公共字段（`create_dept`、`create_by`、`update_by`、`del_flag`、`attr1-attr4`），导致无法与 BaseEntity、Mybatis-Plus 自动填充、逻辑删除等插件配合。以下 DDL 已补全。

新增表全部建在 **ry-vue** 库（系统管理库），不在 ry-erp 库：

```sql
-- ============================================================
-- AI 厂商配置
-- ============================================================
CREATE TABLE ai_provider (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    name            VARCHAR(64)  NOT NULL COMMENT '厂商名称',
    api_host        VARCHAR(256) NOT NULL COMMENT 'API 地址',
    api_key         VARCHAR(512) NOT NULL COMMENT 'API Key（Jasypt 加密存储）',
    provider_type   VARCHAR(32)  NOT NULL COMMENT '厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 厂商配置';

-- ============================================================
-- AI 模型配置
-- ============================================================
CREATE TABLE ai_model (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    provider_id     BIGINT       NOT NULL COMMENT '关联厂商ID',
    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称（如 deepseek-chat）',
    display_name    VARCHAR(128) DEFAULT NULL COMMENT '显示名称',
    model_type      VARCHAR(32)  NOT NULL COMMENT '类型: CHAT/EMBEDDING/RERANK',
    max_tokens      INT          DEFAULT 4096 COMMENT '最大输出 Token',
    temperature     DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数',
    is_default      CHAR(1)      DEFAULT '0' COMMENT '是否默认模型（1是 0否）',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_provider_id (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置';

-- ============================================================
-- AI 对话会话
-- ============================================================
CREATE TABLE ai_session (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    title           VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    model_name      VARCHAR(128) DEFAULT NULL COMMENT '使用的模型名称',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话';

-- ============================================================
-- AI 对话消息
-- ============================================================
CREATE TABLE ai_message (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    session_id      BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: USER/ASSISTANT/SYSTEM',
    content         TEXT         NOT NULL COMMENT '消息内容',
    token_usage     BIGINT       DEFAULT NULL COMMENT 'Token 消耗',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';
```

#### 3.1.3 ERP 数据源 SQL 执行服务（关键新增）

> **【v3 新增】** ruoyi-ai 的 ExecuteSqlQueryTool 使用 `SpringUtils.getBean(DataSource.class)` 获取数据源，这在 nexus-erp 中会拿到 **master 数据源**（ry-vue 库），而 ERP 业务表在 **erp 数据源**（ry-erp 库）。直接在 Tool 中使用 `DynamicDataSourceContextHolder.push("erp")` 又与 Mybatis-Plus 数据权限插件冲突。正确做法是创建一个专用 Service，通过 `@DS("erp")` 注解切换数据源。

```java
@Service
@DS("erp")  // 切换到 ERP 数据源（ry-erp 库）
public class ErpQueryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 执行 SELECT 查询（带超时和行数限制）
     */
    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeoutSeconds) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            return row;
        });
    }

    /**
     * 查询所有 ERP 业务表（只返回 bd_*/so_*/pu_*/prd_*/wm_*/sch_* 前缀的表）
     */
    public List<String> queryErpTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
            String.class
        ).stream()
         .filter(name -> name.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*"))
         .toList();
    }

    /**
     * 查询表结构（SHOW CREATE TABLE，间接含 COMMENT）
     */
    public String queryTableSchema(String tableName) {
        Map<String, Object> result = jdbcTemplate.queryForMap(
            "SHOW CREATE TABLE " + tableName
        );
        return (String) result.get("Create Table");
    }
}
```

#### 3.1.4 模型工厂（含缓存）

> **修正**：初版只提到 `ChatModel`（同步），缺少 `StreamingChatModel`（流式）。v3 新增缓存机制，避免每次请求重建模型实例。

```java
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final IAiModelService aiModelService;

    /** 缓存已构建的 ChatModel（key = modelId） */
    private final ConcurrentHashMap<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    /** 缓存已构建的 StreamingChatModel（key = modelId） */
    private final ConcurrentHashMap<Long, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    /**
     * 获取同步模型（带缓存）
     */
    public ChatModel getChatModel(Long modelId) {
        return chatModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectById(id);
            return buildChatModel(config);
        });
    }

    /**
     * 获取流式模型（带缓存）
     */
    public StreamingChatModel getStreamingChatModel(Long modelId) {
        return streamingModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectById(id);
            return buildStreamingChatModel(config);
        });
    }

    /**
     * 构建同步模型
     */
    private ChatModel buildChatModel(AiModel config) {
        return switch (config.getProviderType()) {
            case "OPENAI" -> OpenAiChatModel.builder()
                .baseUrl(config.getApiHost())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
            case "DASHSCOPE" -> DashScopeChatModel.builder()...build();
            case "ZHIPU" -> ZhipuAiChatModel.builder()...build();
            case "OLLAMA" -> OllamaChatModel.builder()...build();
            default -> throw new IllegalArgumentException("不支持的厂商类型: " + config.getProviderType());
        };
    }

    /**
     * 构建流式模型（SSE 流式对话必须使用）
     */
    private StreamingChatModel buildStreamingChatModel(AiModel config) {
        return switch (config.getProviderType()) {
            case "OPENAI" -> OpenAiStreamingChatModel.builder()
                .baseUrl(config.getApiHost())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
            // 其他厂商类似...
            default -> throw new IllegalArgumentException("不支持的厂商类型: " + config.getProviderType());
        };
    }

    /**
     * 清除缓存（模型配置变更时调用）
     */
    public void evictCache(Long modelId) {
        chatModelCache.remove(modelId);
        streamingModelCache.remove(modelId);
    }
}
```

#### 3.1.5 AI 专用线程池

> **【v3 新增】** `CompletableFuture.runAsync()` 不指定线程池时使用 ForkJoinPool.commonPool()，高并发下可能耗尽公共线程池影响其他业务。应创建专用线程池。

```java
@Configuration
public class AiThreadPoolConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

#### 3.1.6 SSE 流式接口

> **修正**：nexus-erp 已有完整的 `ruoyi-common-sse` 模块（含 SseEmitterManager、SseMessageUtils、SseController 等 7 个类），**无需拷贝，直接使用即可**。

nexus-erp 已有的 SSE 能力：

| 类 | 功能 | AI 场景用法 |
|----|------|------------|
| `SseEmitterManager` | 连接管理（userId → token → emitter 映射） | `connect(userId, token)` 建立连接 |
| `SseMessageUtils` | 静态消息发送 | 见下方精确 API |
| `SseController` | SSE 端点（`GET /sse`，路径可配置） | 前端通过此端点接收消息 |

**SseMessageUtils 精确 API**（4 个方法）：

| 方法签名 | 用途 | 适用场景 |
|----------|------|----------|
| `sendMessage(Long userId, String message)` | 向指定用户发送消息（**本机直发**） | 单节点部署 |
| `sendMessage(String message)` | 向本机所有用户群发（本机直发） | 单节点群发 |
| `publishMessage(SseMessageDto dto)` | 通过 Redis Pub/Sub **跨节点广播** | 多节点部署 |
| `publishAll(String message)` | 通过 Redis 向所有节点群发 | 多节点群发 |

**SseMessageDto 结构**（只有 2 个字段，**不要修改此类**）：

```java
@Data
public class SseMessageDto implements Serializable {
    private List<Long> userIds;  // 目标用户ID列表（null/空=群发）
    private String message;      // 消息正文
}
```

> **【v3 关键修正】**：v2 中错误地将 AI 消息类型作为新字段添加到 SseMessageDto，但 SseMessageDto 是框架通用类，不应修改。AI 消息类型应编码在 `message` 字段中（JSON 格式），前端解析 message 判断类型。

**AI 消息类型约定**（编码在 message 字段中）：

```java
/**
 * AI SSE 消息工具类
 * 将消息类型编码到 SseMessageDto.message 字段中（JSON 格式）
 * 前端解析 message JSON，根据 type 字段区分消息类型
 */
public class AiSseMessageHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 发送 AI 内容（流式 Token） */
    public static void sendContent(Long userId, String content) {
        send(userId, "AI_CONTENT", content);
    }

    /** 发送 AI 思考中状态 */
    public static void sendThinking(Long userId) {
        send(userId, "AI_THINKING", "AI 正在思考...");
    }

    /** 发送 AI 完成信号 */
    public static void sendDone(Long userId) {
        send(userId, "AI_DONE", null);
    }

    /** 发送 AI 错误 */
    public static void sendError(Long userId, String error) {
        send(userId, "AI_ERROR", error);
    }

    /** 发送 ECharts 图表配置 */
    public static void sendChart(Long userId, String chartJson) {
        send(userId, "AI_CHART", chartJson);
    }

    private static void send(Long userId, String type, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            if (content != null) {
                payload.put("content", content);
            }
            String json = MAPPER.writeValueAsString(payload);
            // 使用 publishMessage 支持多节点部署
            SseMessageDto dto = new SseMessageDto();
            dto.setUserIds(List.of(userId));
            dto.setMessage(json);
            SseMessageUtils.publishMessage(dto);
        } catch (Exception e) {
            log.error("发送 AI SSE 消息失败", e);
        }
    }
}
```

#### 3.1.7 最小闭环接口

```java
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiServiceFacade aiServiceFacade;

    /**
     * 发送消息（SSE 流式返回）
     * 前端通过 nexus-erp 已有的 SSE 连接接收流式响应
     */
    @PostMapping("/send")
    public R<Void> send(@RequestBody AiChatRequest request) {
        aiServiceFacade.chat(request);
        return R.ok();
    }

    @GetMapping("/history/{sessionId}")
    public R<List<AiMessageVo>> history(@PathVariable Long sessionId) { ... }

    @PostMapping("/session")
    public R<AiSessionVo> createSession(@RequestBody AiSessionBo bo) { ... }
}
```

### 3.2 前端 AI 组件搭建

#### 3.2.1 安装前端依赖

```bash
cd E:\Nexus\nexus-erp\plus-ui
npm install vue-element-plus-x@1.3.0 hook-fetch@2.0.4-beta.1 github-markdown-css
```

> 安装后需验证 vue-element-plus-x 与 Element Plus 2.13.5 的兼容性。

#### 3.2.2 创建 AI 聊天抽屉组件

参考 `E:\ruoyi-ai\ruoyi-web\src\pages\chat\layouts\chatWithId\index.vue`，在 `plus-ui/src/components/ai/AiChatDrawer.vue` 中实现：

- 全局浮窗（`el-drawer`），从右上角 AI 图标打开
- 使用 `vue-element-plus-x` 的 `Bubble`、`BubbleList`、`Sender`、`Thinking` 组件
- **SSE 接收方式**：复用 nexus-erp 已有的 `useEventSource`（来自 `@vueuse/core`，见 `plus-ui/src/utils/sse.ts`），在消息回调中解析 JSON 判断 AI 消息类型：

```typescript
// SSE 消息处理逻辑（在 AiChatDrawer.vue 中）
const onSseMessage = (event: MessageEvent) => {
  try {
    const data = JSON.parse(event.data)
    switch (data.type) {
      case 'AI_THINKING':
        // 显示 Thinking 动画
        break
      case 'AI_CONTENT':
        // 追加内容到当前消息
        break
      case 'AI_CHART':
        // 渲染 ECharts 图表
        break
      case 'AI_DONE':
        // 标记消息完成
        break
      case 'AI_ERROR':
        // 显示错误提示
        break
    }
  } catch (e) {
    // 非 AI 消息，忽略
  }
}
```

#### 3.2.3 复用 ECharts 渲染

将 `E:\ruoyi-ai\ruoyi-web\src\utils\markdownRenderers.ts` 拷贝到 `plus-ui/src/utils/aiRenderers.ts`，实现：
- 识别 ````echarts` 代码块并渲染为 ECharts 图表
- 识别 ````json` 代码块中的 ECharts 配置并渲染
- nexus-erp 已有 `echarts@6.0.0` 依赖，无需额外安装

> **安全提示**：ECharts 的 `setOption()` 会执行配置中的 JavaScript 函数（如 formatter 回调），LLM 生成的配置可能包含恶意代码。建议在渲染前对 ECharts JSON 做白名单过滤，禁止 `formatter`、`function` 等字段包含函数体，或使用 `echarts.getInstanceByDom().setOption()` 的 `notMerge` 模式并禁用 `script` 类型组件。

---

## 四、Phase 1：ERP 智能查询与报表（第 4-8 周）

> **目标**：实现 ERP 数据的自然语言查询、智能报表生成、业务问答三大核心场景。

### 4.1 场景 1：自然语言查询 ERP 数据

#### 4.1.1 Agent 定义

> **注解位置说明**：`@SystemMessage`、`@UserMessage`、`@Agent` 均放在**接口方法**上（非类级别），与 ruoyi-ai 的 SqlAgent.java 一致。

```java
public interface ErpQueryAgent {
    @SystemMessage("""
        你是一个 ERP 数据查询助手。你负责：
        1. 查询数据库表结构，理解 NexusERP 的业务数据模型
        2. 根据用户的自然语言问题，生成并执行 SQL 查询
        3. 用清晰的中文回答用户的问题，必要时用表格展示数据

        重要规则：
        - 你只能查询 SELECT 语句，严禁执行 INSERT/UPDATE/DELETE
        - 你只能访问以下业务表：bd_*, so_*, pu_*, prd_*, wm_*, sch_*
        - 涉及金额数据时，保留2位小数
        - 涉及日期数据时，使用 YYYY-MM-DD 格式
        - 所有查询必须包含 tenant_id 条件以遵守数据隔离

        可用工具：
        - queryAllTables: 查询数据库中所有可用的表
        - queryTableSchema: 查询指定表的结构和字段说明
        - executeSqlQuery: 执行 SELECT SQL 查询
        """)
    @UserMessage("Answer the following question: {{query}}")
    @Agent("ERP 数据查询助手")
    String query(@V("query") String query);
}
```

#### 4.1.2 Tool 改造（核心：数据源 + 安全校验）

从 ruoyi-ai 拷贝三个 Tool，做 ERP 适配改造：

| 源文件 | 改造要点 |
|--------|----------|
| `ruoyi-ai/.../agent/tool/QueryAllTablesTool.java` | 改为注入 `ErpQueryService`，不再使用 `SpringUtils.getBean(DataSource.class)`；增加表白名单过滤 |
| `ruoyi-ai/.../agent/tool/QueryTableSchemaTool.java` | 改为注入 `ErpQueryService`；ruoyi-ai 已使用 `SHOW CREATE TABLE`（间接含 COMMENT），无需额外改造 |
| `ruoyi-ai/.../agent/tool/ExecuteSqlQueryTool.java` | **关键改造**，见下方 |

> **【v3 关键修正】**：ruoyi-ai 的三个 SQL Tool 均使用 `SpringUtils.getBean(DataSource.class)` 获取数据源，这在 nexus-erp 中会拿到 master 数据源（ry-vue 库），查不到 ERP 业务表。所有 SQL Tool 必须改为注入 `ErpQueryService`（标注 `@DS("erp")`）。

**ExecuteSqlQueryTool 增强版**：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteSqlQueryTool {

    private final ErpQueryService erpQueryService;

    /** 禁止出现的 SQL 关键词（大小写不敏感） */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "LOAD DATA",
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "GRANT", "REVOKE", "EXECUTE", "INFORMATION_SCHEMA",
        "SLEEP", "BENCHMARK", "WAITFOR"
    );

    /** 允许查询的表名前缀 */
    @Value("${nexus.ai.allowed-table-prefixes:bd_,so_,pu_,prd_,wm_,sch_}")
    private List<String> allowedTablePrefixes;

    /** 表名提取正则（匹配 FROM/JOIN 后的表名） */
    private static final Pattern TABLE_NAME_PATTERN =
        Pattern.compile("(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

    @Tool("执行 ERP 数据库查询（只读）")
    public String executeSqlQuery(@P("SQL 查询语句") String sql) {
        String upperSql = sql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!upperSql.startsWith("SELECT")) {
            return "错误：只允许执行 SELECT 查询";
        }

        // 2. 禁止危险关键词
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                return "错误：SQL 中包含不允许的关键词: " + keyword;
            }
        }

        // 3. 表名白名单校验
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            boolean allowed = allowedTablePrefixes.stream()
                .anyMatch(tableName::startsWith);
            if (!allowed) {
                return "错误：不允许查询表 " + tableName + "，只允许查询业务表";
            }
        }

        // 4. 注入租户隔离条件
        Long tenantId = LoginHelper.getTenantId();
        // 在 WHERE 条件中追加 AND tenant_id = #{tenantId}
        sql = injectTenantCondition(sql, tenantId);

        // 5. 追加行数限制（如果 SQL 中没有 LIMIT）
        if (!upperSql.contains("LIMIT")) {
            sql = sql + " LIMIT 1000";
        }

        // 6. 通过 ErpQueryService 执行（走 @DS("erp") 数据源）
        try {
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 1000, 5);
            return formatResults(results);
        } catch (Exception e) {
            log.error("执行 SQL 查询失败: {}", sql, e);
            return "错误：查询执行失败 - " + e.getMessage();
        }
    }

    /**
     * 注入租户隔离条件
     * 简单实现：在 WHERE 后追加 AND tenant_id = ?
     * 复杂 SQL（含子查询、UNION）需更精细的解析
     */
    private String injectTenantCondition(String sql, Long tenantId) {
        // 如果 SQL 中已包含 tenant_id 条件，不再重复注入
        if (sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
        // 在 WHERE 子句后追加条件
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("WHERE")) {
            // 找到 WHERE 位置，在其后插入
            int whereIndex = upperSql.indexOf("WHERE");
            sql = sql.substring(0, whereIndex + 5) +
                  " tenant_id = " + tenantId + " AND" +
                  sql.substring(whereIndex + 5);
        } else {
            // 没有 WHERE，在 LIMIT 前追加
            if (upperSql.contains("LIMIT")) {
                int limitIndex = upperSql.indexOf("LIMIT");
                sql = sql.substring(0, limitIndex) +
                      " WHERE tenant_id = " + tenantId + " " +
                      sql.substring(limitIndex);
            } else {
                sql = sql + " WHERE tenant_id = " + tenantId;
            }
        }
        return sql;
    }

    private String formatResults(List<Map<String, Object>> results) {
        // 复用 ruoyi-ai 的格式化逻辑（Markdown 表格）
        // 最多显示 8 列、10 行
        ...
    }
}
```

**QueryAllTablesTool 改造版**：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAllTablesTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询数据库中所有可用的 ERP 业务表")
    public String queryAllTables() {
        List<String> tables = erpQueryService.queryErpTables();
        // ErpQueryService 已过滤只返回 bd_*/so_*/pu_*/prd_*/wm_*/sch_* 前缀的表
        return String.join("\n", tables);
    }
}
```

**QueryTableSchemaTool 改造版**：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTableSchemaTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询指定表的结构和字段说明")
    public String queryTableSchema(@P("表名") String tableName) {
        // 安全校验：只允许查询业务表
        if (!tableName.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*")) {
            return "错误：只允许查询业务表（bd_*/so_*/pu_*/prd_*/wm_*/sch_*）";
        }
        return erpQueryService.queryTableSchema(tableName);
    }
}
```

#### 4.1.3 ERP Schema 注释增强

为 AI 准备一份**简化版业务 Schema 说明**，让 AI 理解 ERP 业务含义：

```sql
-- 存储在配置文件或作为 SystemMessage 的一部分
-- 示例：
-- bd_material: 物料主数据（material_code=物料编码, material_name=物料名称, category_id=分类,
--   specification=规格型号, unit_id=计量单位, safety_stock=安全库存）
-- so_sales_order_header: 销售订单头（order_no=订单编号, client_id=客户, order_date=下单日期,
--   status=状态[DRAFT/CONFIRMED/COMPLETED], total_amount=总金额）
-- wm_material_stock: 库存台账（material_id=物料, warehouse_id=仓库, location_id=库位,
--   quantity=当前数量, locked_qty=冻结数量, avg_cost=移动加权平均成本）
```

#### 4.1.4 典型查询示例

用户可以说：
- "查询本月所有采购订单的总金额"
- "库存低于安全库存的物料有哪些？"
- "最近30天哪些客户退货最多？"
- "查询 BOM 编号为 BOM-001 的所有子件"

### 4.2 场景 2：智能报表与图表

#### 4.2.1 Agent 定义

> ECharts 图表由 LLM 直接生成 JSON 配置，不需要 Java Tool。ErpReportAgent 同时具备 SQL 查询和图表生成能力。

```java
public interface ErpReportAgent {
    @SystemMessage("""
        你是一个 ERP 数据可视化专家。你的工作流程：
        1. 理解用户想要的报表类型
        2. 使用数据库查询工具获取数据
        3. 将数据转换为 ECharts 图表配置
        4. 以 markdown 代码块格式返回 ECharts 配置

        输出格式（必须严格遵守）：
        ```echarts
        {
          "title": {"text": "图表标题", "left": "center"},
          "tooltip": {"trigger": "axis"},
          "xAxis": {"type": "category", "data": [...]},
          "yAxis": {"type": "value"},
          "series": [{"name": "系列名", "type": "bar", "data": [...]}]
        }
        ```

        图表类型选择规则：
        - 趋势分析 → 折线图（line）
        - 分类对比 → 柱状图（bar）
        - 占比分析 → 饼图（pie）
        - 多维关联 → 散点图（scatter）
        - 仪表盘 → 仪表盘（gauge）

        安全规则：
        - ECharts 配置中禁止使用 JavaScript 函数（formatter 回调等）
        - 只使用静态配置，数据值必须为字面量
        """)
    @UserMessage("Generate an Echarts chart for: {{query}}")
    @Agent("ERP 数据可视化专家")
    String generateReport(@V("query") String query);
}
```

#### 4.2.2 报表示例

| 用户输入 | AI 行为 | 图表类型 |
|----------|---------|----------|
| "本月各品类销售额占比" | 查 so_sales_order_line + bd_material_category → 生成饼图 | 饼图 |
| "最近6个月采购金额趋势" | 查 pu_purchase_order 按月聚合 → 折线图 | 折线图 |
| "仓库库存 TOP10 物料" | 查 wm_material_stock 按 quantity 排序 → 柱状图 | 柱状图 |
| "供应商交货准时率排行" | 查 pu_vendor_evaluation → 柱状图 | 柱状图 |
| "生产工单完成率仪表盘" | 查 prd_work_order 按 status 统计 → 仪表盘 | 仪表盘 |

### 4.3 场景 3：ERP 业务问答（RAG）

#### 4.3.1 知识库表结构

```sql
CREATE TABLE ai_knowledge (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    knowledge_name  VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    embedding_model VARCHAR(64)  DEFAULT NULL COMMENT '使用的 Embedding 模型',
    vector_store    VARCHAR(32)  DEFAULT 'milvus' COMMENT '向量存储类型',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库';

CREATE TABLE ai_knowledge_attach (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    knowledge_id    BIGINT       NOT NULL COMMENT '知识库ID',
    file_name       VARCHAR(256) NOT NULL COMMENT '文件名',
    file_path       VARCHAR(512) DEFAULT NULL COMMENT 'OSS 文件路径',
    file_size       BIGINT       DEFAULT NULL COMMENT '文件大小（字节）',
    chunk_count     INT          DEFAULT 0 COMMENT '分段数量',
    status          CHAR(1)      DEFAULT '0' COMMENT '状态（0待解析 1解析中 2已完成 3失败）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库附件';

CREATE TABLE ai_knowledge_fragment (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    attach_id       BIGINT       NOT NULL COMMENT '附件ID',
    knowledge_id    BIGINT       NOT NULL COMMENT '知识库ID',
    content         TEXT         NOT NULL COMMENT '片段内容',
    chunk_index     INT          DEFAULT NULL COMMENT '片段序号',
    vector_id       VARCHAR(64)  DEFAULT NULL COMMENT '向量数据库中的ID',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_knowledge_id (knowledge_id),
    KEY idx_attach_id (attach_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识片段';
```

#### 4.3.2 预置知识库内容

| 知识库 | 文档来源 | 用途 |
|--------|----------|------|
| ERP 操作手册 | 用户自行上传 ERP 使用文档 | "如何创建采购订单？" |
| 业务流程说明 | `NexusERP_业务流转分析.md` | "销售订单到发货的完整流程？" |
| 编码规则说明 | `NexusERP_编码生成规则设计方案.md` | "批次号是怎么生成的？" |
| 公司制度文档 | 财务/采购/仓储制度 | "采购审批流程是什么？" |

#### 4.3.3 RAG 流水线

从 ruoyi-ai 复用以下文件：

| 源文件 | 说明 |
|--------|------|
| `ruoyi-ai/.../knowledge/impl/KnowledgeAttachServiceImpl.java` | 文档上传、解析、分段 |
| `ruoyi-ai/.../knowledge/impl/KnowledgeInfoServiceImpl.java` | 知识库管理 |
| `ruoyi-ai/.../retrieval/impl/KnowledgeRetrievalServiceImpl.java` | 检索链（向量召回+关键词召回+RRF融合+重排序） |
| `ruoyi-ai/.../service/knowledge/retriever/CustomVectorRetriever.java` | 自定义向量检索器 |
| `ruoyi-ai/.../service/vector/impl/MilvusVectorStoreStrategy.java` | Milvus 向量存储 |

### 4.4 场景 4：Agent 编排（Supervisor 模式）

> **【v3 关键修正】**：v2 中 SSE 消息发送代码完全错误：
> - 错误：`SseMessageUtils.sendMessage(userId, new SseMessageDto(null, JSON.toJSONString(...)))`
> - 实际 API：`SseMessageUtils.sendMessage(Long userId, String message)` — 第二个参数是 String，不是 SseMessageDto
> - 跨节点场景应使用 `SseMessageUtils.publishMessage(SseMessageDto)` — 通过 Redis Pub/Sub 广播
> - SseMessageDto 字段是 `userIds`(List<Long>) + `message`(String)，不是构造函数传两个参数

参考 `E:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\service\chat\impl\ChatServiceFacade.java` 中的 SupervisorAgent 构建逻辑：

```java
@Service
@RequiredArgsConstructor
public class AiServiceFacade {

    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    public void chat(AiChatRequest request) {
        Long userId = LoginHelper.getUserId();
        AiModelVo modelConfig = aiModelService.selectModelByName(request.getModel());

        // 1. 构建模型（同步用于 Agent 内部，流式用于直接对话）
        ChatModel chatModel = chatModelFactory.getChatModel(modelConfig.getId());
        StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId());

        // 2. 构建子 Agent
        ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel)
            .tools(new QueryAllTablesTool(), new QueryTableSchemaTool(), new ExecuteSqlQueryTool())
            .build();

        ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel)
            .tools(new QueryAllTablesTool(), new ExecuteSqlQueryTool())
            .build();

        // 3. 构建 Supervisor（使用 AgenticServices.supervisorBuilder()）
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(queryAgent, reportAgent)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .build();

        // 4. 发送"思考中"状态（使用 AiSseMessageHelper，支持多节点）
        AiSseMessageHelper.sendThinking(userId);

        // 5. 异步执行 + SSE 推送结果（使用专用线程池）
        CompletableFuture.runAsync(() -> {
            try {
                String result = supervisor.invoke(request.getContent());
                // 推送 AI 回复内容
                AiSseMessageHelper.sendContent(userId, result);
                // 推送完成信号
                AiSseMessageHelper.sendDone(userId);
            } catch (Exception e) {
                AiSseMessageHelper.sendError(userId, e.getMessage());
            }
        }, aiExecutor);  // 使用专用线程池，而非 ForkJoinPool.commonPool()
    }
}
```

**后续优化方向**：LangChain4j 的 `langchain4j-agentic` 模块正在完善流式支持，待其 `SupervisorAgent` 支持流式回调后，可改为真正的 Token 级流式推送。ruoyi-ai 的 ChatServiceFacade 中已有被注释的 `SupervisorStreamListener`，说明此功能正在开发中。

### 4.5 Phase 1 交付清单

| 序号 | 交付物 | 说明 |
|------|--------|------|
| 1 | `ruoyi-common-ai` + `ruoyi-ai` Maven 模块 | AI 公共模块 + 核心业务模块 |
| 2 | 7 张数据库表 | ai_provider, ai_model, ai_session, ai_message（Phase 0）+ ai_knowledge, ai_knowledge_attach, ai_knowledge_fragment（Phase 1） |
| 3 | 2 个 Agent 接口 | ErpQueryAgent, ErpReportAgent |
| 4 | 3 个 SQL Tool + 4 个业务 Tool | QueryAllTablesTool, QueryTableSchemaTool, ExecuteSqlQueryTool + ErpInventoryTool 等 |
| 5 | ErpQueryService | ERP 数据源 SQL 执行服务（@DS("erp")） |
| 6 | AiSseMessageHelper | AI SSE 消息工具类（编码消息类型到 message JSON） |
| 7 | AI 聊天前端页面 | AiChatDrawer + EchartsRenderer |
| 8 | 模型管理页面 | 配置厂商/模型 |
| 9 | 知识库管理页面 | 上传文档/管理知识库 |

---

## 五、Phase 2：ERP 深度 AI 场景（第 9-14 周）

> **目标**：在 Phase 1 基础上，深入供应链、财务、生产计划等 ERP 核心场景。

### 5.1 智能补货建议

#### 5.1.1 实现方式

```java
@Component
@RequiredArgsConstructor
public class ErpInventoryTool {

    private final WmMaterialStockMapper stockMapper;
    private final BdMaterialMapper materialMapper;

    @Tool("查询低于安全库存的物料列表及其当前库存、安全库存、最近供应商信息")
    public String queryLowStockMaterials() {
        // 联查 wm_material_stock + bd_material + bd_material_vendor + bd_vendor
        // 返回：物料编码、名称、当前库存、安全库存、缺口数量、建议供应商
    }

    @Tool("根据物料ID查询最近N个月的出库趋势")
    public String queryMaterialOutboundTrend(
        @P("物料ID") Long materialId,
        @P("查询月数") int months) {
        // 联查 wm_material_stock_tx
        // 按月聚合出库数量
    }

    @Tool("查询库存总金额及按仓库分布")
    public String queryInventoryValue() {
        // 查 wm_material_stock，quantity * avg_cost
    }
}
```

#### 5.1.2 AI 补货对话示例

```
用户：帮我看看哪些物料需要补货了？

AI（调用 ErpInventoryTool.queryLowStockMaterials）：
根据当前库存分析，以下物料已低于安全库存：

| 物料编码 | 物料名称 | 当前库存 | 安全库存 | 缺口 | 建议供应商 |
|----------|----------|----------|----------|------|-----------|
| M-001    | 螺丝M6  | 500      | 2000     | 1500 | 供应商A   |
| M-003    | 垫片φ8  | 100      | 800      | 700  | 供应商B   |

建议立即对 M-001 下采购订单 2000 件（含 500 件缓冲），供应商 A 上月交货准时率 95%。
需要我生成采购申请单草稿吗？
```

### 5.2 销售预测与需求感知

```java
@Component
public class ErpSalesTool {
    @Tool("查询指定物料近N个月的月度销售数据，用于趋势分析")
    public String queryMonthlySalesTrend(
        @P("物料ID或编码") String materialCode,
        @P("查询月数") int months) { ... }

    @Tool("查询按客户维度的销售金额排行（Top N）")
    public String queryTopClientsBySales(
        @P("时间范围（天数）") int days,
        @P("Top N") int topN) { ... }
}
```

### 5.3 智能财务分析

```java
@Component
public class ErpPurchaseTool {
    @Tool("查询指定时间段的采购总金额及按供应商分布")
    public String queryPurchaseAmountByVendor(...) { ... }

    @Tool("查询供应商综合评估数据，包括交货准时率、退货率、质量评分")
    public String queryVendorRiskAssessment(@P("供应商ID") Long vendorId) { ... }
}
```

### 5.4 排程辅助建议

```java
@Component
public class ErpProductionTool {
    @Tool("查询当前在产工单及其进度")
    public String queryActiveWorkOrders() { ... }

    @Tool("查询指定车间/工位的产能利用率")
    public String queryWorkstationUtilization(...) { ... }
}
```

---

## 六、Phase 3：MES 智能赋能（第 15-22 周，与 MES 开发同步）

> **目标**：MES 模块开发过程中同步嵌入 AI 能力，让 MES 从"记录型"升级为"预测型"。

### 6.1 MES 数据接入 AI 的前提

MES 模块使用独立数据源 `@DS("mes")`，对应 `ry-mes` 数据库。AI 需要跨数据源查询：
- ERP 数据源（`@DS("erp")`）：订单、物料、BOM、库存
- MES 数据源（`@DS("mes")`）：工单执行、流转卡、质量检测、设备数据

> **修正**：不在 Tool 中直接用 `DynamicDataSourceContextHolder.push("mes")` 切换数据源，这与 Mybatis-Plus 数据权限插件冲突。正确做法是为 MES 专属 Tool 注入 MES 模块的 Service，由这些 Service 内部通过 `@DS("mes")` 注解访问数据。

### 6.2 场景 1：生产进度实时监控

```java
@Component
@RequiredArgsConstructor
public class MesProductionTool {

    // 注入 MES 模块的 Service（内部通过 @DS("mes") 访问 MES 数据源）
    private final MesWorkOrderService mesWorkOrderService;
    private final MesFlowCardService mesFlowCardService;

    @Tool("查询指定工单的工序执行进度")
    public String queryWorkOrderProgress(@P("工单ID") Long workOrderId) {
        // 调用 mesWorkOrderService，内部走 @DS("mes") 数据源
    }

    @Tool("查询指定车间的在制品统计")
    public String queryWorkInProgress(@P("车间ID") Long workshopId) { ... }

    @Tool("查询今日各产线产量达成率")
    public String queryDailyProductionRate() { ... }
}
```

**AI 对话示例**：

```
用户：工单 WO-2026-0156 现在做到哪了？

AI（调用 MesProductionTool.queryWorkOrderProgress）：
工单 WO-2026-0156 当前进度：

| 工序 | 状态 | 完成数/计划数 | 开始时间 | 结束时间 |
|------|------|-------------|----------|----------|
| 下料 | ✅完成 | 1000/1000 | 06-01 08:00 | 06-01 16:30 |
| 冲压 | ✅完成 | 1000/1000 | 06-02 08:00 | 06-02 17:00 |
| 焊接 | 🔄进行中 | 650/1000 | 06-03 08:00 | - |
| 喷涂 | ⏳待开始 | 0/1000 | - | - |
| 总装 | ⏳待开始 | 0/1000 | - | - |

当前在焊接工序，完成率 65%，预计今日 18:00 可完成焊接转入喷涂。
```

### 6.3 场景 2：质量缺陷趋势与根因分析

```java
@Component
@RequiredArgsConstructor
public class MesQualityTool {

    private final MesInspectionRecordService inspectionService;
    private final MesDefectService defectService;

    @Tool("查询指定时间段的质量缺陷分布（按缺陷类型/工序/产品）")
    public String queryDefectDistribution(
        @P("开始日期") String startDate,
        @P("结束日期") String endDate,
        @P("维度") String dimension) { ... }

    @Tool("查询指定产品的 SPC 控制图数据")
    public String querySpcData(
        @P("产品ID") Long productId,
        @P("检测项") String inspectionItem) { ... }

    @Tool("查询不良品 TOP N 排行")
    public String queryTopDefects(@P("Top N") int topN) { ... }
}
```

### 6.4 场景 3：设备健康监控与 OEE 分析

```java
@Component
@RequiredArgsConstructor
public class MesEquipmentTool {

    private final MesEquipmentService equipmentService;
    private final MesMaintenanceService maintenanceService;

    @Tool("查询指定设备的运行状态和 OEE")
    public String queryEquipmentOee(@P("设备ID") Long equipmentId) { ... }

    @Tool("查询设备故障历史和维护记录")
    public String queryEquipmentMaintenanceHistory(
        @P("设备ID") Long equipmentId,
        @P("查询天数") int days) { ... }

    @Tool("查询全厂设备综合效率排行")
    public String queryEquipmentOeeRanking() { ... }
}
```

### 6.5 场景 4：产品 SN 全链路追溯

```java
@Component
@RequiredArgsConstructor
public class MesTraceTool {

    private final MesTraceabilityService traceService;
    private final ErpQueryService erpQueryService;  // 跨数据源：查 ERP 的物料/BOM

    @Tool("根据产品 SN 查询全链路追溯信息（原材料→工序→质检→入库）")
    public String queryProductTrace(@P("产品序列号") String serialNumber) { ... }

    @Tool("反向追溯：根据原材料批次号查询使用了该批次的所有产品")
    public String queryBatchTrace(@P("批次号") String batchNo) { ... }
}
```

### 6.6 MES Agent 定义

```java
public interface MesAnalysisAgent {
    @SystemMessage("""
        你是一个 MES 生产执行分析助手。你可以：
        1. 查询生产工单进度和完成情况
        2. 分析质量缺陷趋势和根因
        3. 监控设备运行状态和 OEE
        4. 查询 SPC 过程控制状态
        5. 进行产品 SN 全链路追溯

        你可以同时访问 ERP 和 MES 数据，进行跨系统分析。
        当需要 ERP 数据（如物料、BOM、库存）时，使用 ERP 查询工具；
        当需要 MES 数据（如工序进度、质量检测、设备状态）时，使用 MES 查询工具。
        """)
    @UserMessage("Answer the following question: {{query}}")
    @Agent("MES 生产分析助手")
    String analyze(@V("query") String query);
}
```

### 6.7 MES 知识库

| 知识库 | 文档来源 | 用途 |
|--------|----------|------|
| MES 操作手册 | 用户上传 | "如何报工？" |
| 工艺路线文档 | 工艺部门提供 | "产品 A 的工艺路线是什么？" |
| 质量标准文档 | 质量部门提供 | "这个检测项的合格标准是什么？" |
| 设备操作规程 | 设备部门提供 | "CNC-001 的保养周期是多久？" |

---

## 七、Phase 4：IoT 智能赋能（第 23-30 周，与 IoT 开发同步）

> **目标**：IoT 模块开发过程中同步嵌入 AI 能力，实现设备实时监控、预测性维护、智能告警。

### 7.1 IoT 数据接入 AI 的前提

IoT 模块使用独立数据源 `@DS("iot")`，对应 `ry-iot` 数据库。数据特征：
- 时序数据量大（设备传感器数据高频采集）
- 需要时序数据库（如 TDengine/TimescaleDB）或 MySQL 分表存储
- AI Tool 注入 IoT 模块的 Service，内部通过 `@DS("iot")` 访问

### 7.2 场景 1：设备实时监控

```java
@Component
@RequiredArgsConstructor
public class IotDeviceTool {

    private final IotDeviceService deviceService;
    private final IotTelemetryService telemetryService;

    @Tool("查询指定设备的实时状态和最新传感器数据")
    public String queryDeviceStatus(@P("设备ID") Long deviceId) { ... }

    @Tool("查询指定设备的传感器历史数据（指定时间范围和指标）")
    public String queryDeviceTelemetry(
        @P("设备ID") Long deviceId,
        @P("指标名称") String metric,
        @P("开始时间") String startTime,
        @P("结束时间") String endTime) { ... }

    @Tool("查询全厂设备在线率统计")
    public String queryDeviceOnlineRate() { ... }
}
```

**AI 对话示例**：

```
用户：CNC-001 现在的运行状态怎么样？

AI（调用 IotDeviceTool.queryDeviceStatus）：
设备 CNC-001 当前状态：

| 指标 | 当前值 | 正常范围 | 状态 |
|------|--------|----------|------|
| 主轴转速 | 3200 RPM | 3000-3500 | ✅正常 |
| 冷却液温度 | 28.5°C | 20-30°C | ⚠️偏高 |
| 振动幅值 | 0.12mm | <0.15mm | ✅正常 |
| 运行时长 | 6.5h | - | - |

冷却液温度接近上限，建议关注。过去 24 小时冷却液温度呈缓慢上升趋势，可能需要更换冷却液。
```

### 7.3 场景 2：预测性维护

```java
@Component
@RequiredArgsConstructor
public class IotAlertTool {

    private final IotAlertService alertService;
    private final IotTelemetryService telemetryService;

    @Tool("查询当前活跃告警列表")
    public String queryActiveAlerts() { ... }

    @Tool("查询指定设备的告警历史和趋势")
    public String queryAlertHistory(
        @P("设备ID") Long deviceId,
        @P("查询天数") int days) { ... }

    @Tool("分析设备传感器数据趋势，预测潜在故障")
    public String predictDeviceFailure(@P("设备ID") Long deviceId) {
        // 查询近期传感器数据
        // 分析温度/振动/电流等指标的趋势
        // 返回预测性维护建议
    }
}
```

### 7.4 场景 3：智能告警分析

AI 可以：
- 自动关联告警与设备历史维护记录
- 分析告警根因（如"温度告警"→"冷却系统故障"→"上次维护未更换冷却液"）
- 生成告警处理建议

### 7.5 IoT Agent 定义

```java
public interface IotMonitorAgent {
    @SystemMessage("""
        你是一个 IoT 设备监控助手。你可以：
        1. 查询设备实时状态和传感器数据
        2. 分析设备运行趋势和异常
        3. 查询和管理告警
        4. 提供预测性维护建议
        5. 关联 IoT 数据与 MES/ERP 数据进行综合分析

        当需要设备实时数据时，使用 IoT 工具；
        当需要设备维护记录时，使用 MES 设备工具；
        当需要物料/BOM 信息时，使用 ERP 查询工具。
        """)
    @UserMessage("Answer the following question: {{query}}")
    @Agent("IoT 设备监控助手")
    String monitor(@V("query") String query);
}
```

### 7.6 IoT 知识库

| 知识库 | 文档来源 | 用途 |
|--------|----------|------|
| 设备手册 | 设备供应商提供 | "CNC-001 的报警代码 E-003 是什么意思？" |
| 维护规程 | 设备部门提供 | "液压系统的保养周期和步骤？" |
| 传感器规格 | IoT 部门提供 | "温度传感器的量程和精度？" |

---

## 八、Phase 5：跨系统融合决策（第 31-36 周）

> **目标**：打通 ERP/MES/IoT 三系统数据壁垒，实现跨系统 AI 决策能力。

### 8.1 场景 1：CTP 可承诺量查询

**业务场景**：客户询问"500 件产品 A 最早什么时候能交货？"，AI 需要综合：
- ERP 库存（wm_material_stock）
- ERP 在途采购（pu_purchase_order）
- MES 在产工单（mes_work_order）
- MES 排程计划（mes_production_schedule）

```java
@Component
@RequiredArgsConstructor
public class CrossSystemTool {

    private final ErpInventoryTool erpInventoryTool;
    private final ErpSalesTool erpSalesTool;
    private final MesProductionTool mesProductionTool;
    private final IotDeviceTool iotDeviceTool;

    @Tool("CTP 可承诺量计算：综合库存、在途、在产、排程，计算指定物料的可承诺量和预计交期")
    public String calculateCTP(
        @P("物料ID") Long materialId,
        @P("需求数量") int quantity) {
        // 1. 查 ERP 当前库存
        // 2. 查 ERP 在途采购（预计到货日期和数量）
        // 3. 查 MES 在产工单（预计完工日期和数量）
        // 4. 综合计算可承诺量和预计交期
    }

    @Tool("ATP 可用量检查：检查指定物料在指定日期的可用量")
    public String checkATP(
        @P("物料ID") Long materialId,
        @P("检查日期") String date) { ... }
}
```

### 8.2 场景 2：动态成本分析

**业务场景**："这个订单的实际成本是多少？"，AI 需要综合：
- ERP 采购成本（pu_purchase_order）
- MES 生产成本（人工、能耗、废品率）
- IoT 能耗数据（设备耗电/耗气）

### 8.3 场景 3：质量闭环追溯

**业务场景**："客户反馈产品有缺陷，帮我追溯原因"，AI 需要：
- ERP 查销售订单和发货记录
- MES 查生产工序和质检记录
- IoT 查生产时的设备状态数据

### 8.4 场景 4：供应链风险预警

**业务场景**：AI 主动分析供应链风险：
- 供应商交货延迟趋势（ERP 采购数据）
- 原材料价格波动（ERP 采购价格趋势）
- 关键设备故障风险（IoT 预测性维护）
- 产能瓶颈预警（MES OEE + IoT 设备利用率）

### 8.5 MCP 工具配置表

```sql
CREATE TABLE ai_tool (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    tool_name       VARCHAR(128) NOT NULL COMMENT '工具名称',
    display_name    VARCHAR(128) DEFAULT NULL COMMENT '显示名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '工具描述',
    tool_type       VARCHAR(32)  NOT NULL COMMENT '类型: BUILTIN/MCP/REMOTE',
    config          TEXT         DEFAULT NULL COMMENT '工具配置（JSON）',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 工具配置';
```

### 8.6 Supervisor 总调度（最终形态）

```java
// 构建完整的多 Agent 协作体系
SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
    .chatModel(chatModel)
    .subAgents(
        queryAgent,      // ERP 数据查询
        reportAgent,     // 报表生成
        mesAgent,        // MES 生产分析
        iotAgent         // IoT 设备监控
    )
    .responseStrategy(SupervisorResponseStrategy.LAST)
    .build();
```

---

## 九、Phase 6：AI 工作流与智能体演进（第 37 周+）

> **目标**：从单轮对话升级为多步工作流，实现自主决策的智能体。

### 9.1 AI 工作流引擎

引入 `langgraph4j`（版本 1.5.3）实现复杂工作流：

```sql
CREATE TABLE ai_workflow (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    workflow_name   VARCHAR(128) NOT NULL COMMENT '工作流名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    graph_config    TEXT         NOT NULL COMMENT 'LangGraph 图配置（JSON）',
    trigger_type    VARCHAR(32)  DEFAULT 'MANUAL' COMMENT '触发类型: MANUAL/SCHEDULED/EVENT',
    trigger_config  VARCHAR(512) DEFAULT NULL COMMENT '触发配置（Cron 表达式或事件条件）',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 工作流定义';
```

### 9.2 典型工作流场景

| 工作流 | 步骤 | 触发方式 |
|--------|------|----------|
| 自动补货 | 库存预警 → 分析需求 → 生成采购申请 → 审批 | 事件触发（库存低于安全线） |
| 质量异常处理 | 检测不合格 → 根因分析 → 生成返工单 → 通知责任人 | 事件触发（质检不合格） |
| 设备故障响应 | 告警触发 → 诊断分析 → 生成维修工单 → 调度维修人员 | 事件触发（IoT 告警） |
| 月度经营分析 | 数据采集 → 生成报表 → 分析趋势 → 生成建议 | 定时触发（每月1日） |

### 9.3 MCP 工具市场

基于 `langchain4j-mcp` 实现：
- 内置工具注册中心（ErpBuiltinToolRegistry）
- 外部 MCP 服务器连接（如企业知识图谱、外部 API）
- 工具动态发现和调用

### 9.4 可解释性

- Agent 调用链路追踪（AiAgentListener）
- Token 消耗统计和成本分析
- 工具调用日志和结果审计

---

## 十、数据库变更总览

### 10.1 AI 模块新增表（全部在 ry-vue 库）

| 表名 | 用途 | Phase |
|------|------|-------|
| `ai_provider` | AI 厂商配置 | Phase 0 |
| `ai_model` | AI 模型配置 | Phase 0 |
| `ai_session` | 对话会话 | Phase 0 |
| `ai_message` | 对话消息 | Phase 0 |
| `ai_knowledge` | 知识库 | Phase 1 |
| `ai_knowledge_attach` | 知识库附件 | Phase 1 |
| `ai_knowledge_fragment` | 知识片段 | Phase 1 |
| `ai_tool` | MCP 工具配置 | Phase 5 |
| `ai_workflow` | AI 工作流定义 | Phase 6 |

### 10.2 数据源配置

在 `application.yml` 中新增 AI 相关配置（前缀 `nexus.ai`，避免与 Spring AI 的 `spring.ai` 冲突）：

```yaml
# NexusERP AI 配置
nexus:
  ai:
    default-provider: deepseek
    default-model: deepseek-chat
    allowed-table-prefixes: bd_,so_,pu_,prd_,wm_,sch_
    sql-timeout: 5000
    sql-max-rows: 1000
    vector-store: milvus
    milvus:
      host: localhost
      port: 19530
      collection: nexus_ai_vectors
```

### 10.3 多数据源配置扩展

当 MES 和 IoT 模块开发后，在 `application-dev.yml` 的 `spring.datasource.dynamic.datasource` 下新增：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:           # 系统管理库（ry-vue）
          url: jdbc:mysql://localhost:3306/ry-vue?...
        erp:              # ERP 业务库（ry-erp）
          url: jdbc:mysql://localhost:3306/ry-erp?...
        mes:              # 【规划中】MES 制造执行库（ry-mes）
          url: jdbc:mysql://localhost:3306/ry-mes?...
        iot:              # 【规划中】IoT 设备采集库（ry-iot）
          url: jdbc:mysql://localhost:3306/ry-iot?...
```

---

## 十一、前端变更总览

### 11.1 新增前端文件

| 文件 | 说明 | Phase |
|------|------|-------|
| `api/ai/chat.ts` | 聊天接口 | Phase 0 |
| `api/ai/model.ts` | 模型配置接口 | Phase 0 |
| `api/ai/knowledge.ts` | 知识库接口 | Phase 1 |
| `components/ai/AiChatDrawer.vue` | AI 聊天抽屉 | Phase 0 |
| `components/ai/EchartsRenderer.vue` | ECharts 渲染 | Phase 1 |
| `utils/aiRenderers.ts` | ECharts Markdown 渲染器 | Phase 1 |
| `views/ai/assistant.vue` | 智能助手页面 | Phase 0 |
| `views/ai/model/index.vue` | 模型管理 | Phase 0 |
| `views/ai/model/provider.vue` | 厂商管理 | Phase 0 |
| `views/ai/knowledge/index.vue` | 知识库管理 | Phase 1 |
| `views/ai/knowledge/fragment.vue` | 知识片段 | Phase 1 |

### 11.2 修改前端文件

| 文件 | 修改内容 |
|------|----------|
| `utils/sse.ts` | 新增 AI 消息类型（AI_CONTENT/AI_THINKING/AI_DONE/AI_ERROR/AI_CHART）的处理逻辑 |
| `layout/components/Navbar.vue` | 添加 AI 助手入口图标 |
| `router/index.ts` | 添加 AI 管理页面路由 |

---

## 十二、开发排期总览

| 阶段 | 周期 | 核心交付 | 人天估算 |
|------|------|----------|----------|
| **Phase 0** AI 基础设施 | 第 1-3 周 | ruoyi-common-ai + ruoyi-ai 模块骨架 + SSE 流式对话 + 模型管理 + ErpQueryService | 18 人天 |
| **Phase 1** ERP 智能查询与报表 | 第 4-8 周 | Text2SQL + ECharts 报表 + RAG 知识库 + 前端 | 25 人天 |
| **Phase 2** ERP 深度场景 | 第 9-14 周 | 补货建议/销售预测/财务分析/供应商风险 | 20 人天 |
| **Phase 3** MES 智能赋能 | 第 15-22 周 | 生产监控/质量根因/设备健康/SPC/追溯（与 MES 同步开发） | 25 人天 |
| **Phase 4** IoT 智能赋能 | 第 23-30 周 | 设备监控/预测性维护/智能告警（与 IoT 同步开发） | 20 人天 |
| **Phase 5** 跨系统融合 | 第 31-36 周 | CTP/ATP/动态成本/质量闭环/MCP 工具 | 15 人天 |
| **Phase 6** 工作流与智能体 | 第 37 周+ | AI 工作流/MCP 工具市场/可解释性 | 15 人天 |
| **合计** | 约 36 周 | | **138 人天** |

---

## 十三、ruoyi-ai 代码复用清单（完整）

### 13.1 后端可直接复用

| 源文件（ruoyi-ai） | 目标位置（nexus-erp） | 复用方式 |
|---------------------|----------------------|----------|
| `pom.xml` 中 langchain4j 依赖版本 | `ruoyi-ai/pom.xml` | 拷贝版本属性（注意区分 .version 和 .community.version） |
| `agent/SqlAgent.java` | `agent/ErpQueryAgent.java` | 参考 + 改写 SystemMessage |
| `agent/EchartsAgent.java` | `agent/ErpReportAgent.java` | 参考 + 改写 |
| `agent/ChartGenerationAgent.java` | 合并到 ErpReportAgent | 参考 |
| `agent/tool/QueryAllTablesTool.java` | `tool/QueryAllTablesTool.java` | 拷贝 + 改为注入 ErpQueryService（不再用 SpringUtils.getBean(DataSource.class)） |
| `agent/tool/QueryTableSchemaTool.java` | `tool/QueryTableSchemaTool.java` | 拷贝 + 改为注入 ErpQueryService |
| `agent/tool/ExecuteSqlQueryTool.java` | `tool/ExecuteSqlQueryTool.java` | 拷贝 + 改为注入 ErpQueryService + 增强安全校验 + 加权限 |
| `service/chat/impl/ChatServiceFacade.java` | `service/facade/AiServiceFacade.java` | 参考 Supervisor 组装 |
| `service/retrieval/impl/KnowledgeRetrievalServiceImpl.java` | `service/rag/RetrieverService.java` | 拷贝 + 适配 |
| `service/knowledge/impl/KnowledgeAttachServiceImpl.java` | `service/rag/DocumentParserService.java` | 拷贝 |
| `service/vector/impl/MilvusVectorStoreStrategy.java` | `service/rag/VectorStoreService.java` | 拷贝 |
| `service/knowledge/retriever/CustomVectorRetriever.java` | `service/rag/CustomVectorRetriever.java` | 拷贝 |
| `service/chat/impl/memory/PersistentChatMemoryStore.java` | `service/chat/PersistentChatMemory.java` | 拷贝 |
| `mcp/service/core/BuiltinToolProvider.java` | `mcp/registry/BuiltinToolProvider.java` | 拷贝 |
| `mcp/service/core/ToolProviderFactory.java` | `mcp/registry/ToolProviderFactory.java` | 拷贝 |
| `observability/*.java` | `observability/` | 拷贝 |

### 13.2 前端可直接复用

| 源文件（ruoyi-web） | 目标位置（nexus-erp plus-ui） | 复用方式 |
|----------------------|------------------------------|----------|
| `utils/markdownRenderers.ts` | `utils/aiRenderers.ts` | 拷贝 + 适配 + 添加 ECharts 安全过滤 |
| `styles/markdown.scss` | `styles/ai-markdown.scss` | 拷贝 |
| `api/chat/index.ts` | `api/ai/chat.ts` | 参考 |
| `api/chat/types.ts` | `api/ai/types.ts` | 参考 |
| `pages/chat/layouts/chatWithId/index.vue` | `components/ai/AiChatDrawer.vue` | 参考 + 改造为 Drawer |

### 13.3 不需要复用的

| 源文件（ruoyi-ai） | 原因 |
|---------------------|------|
| `ruoyi-common-sse/*` | nexus-erp 已有完整 SSE 模块，直接使用 |
| `agent/WebSearchAgent.java` | ERP 场景暂不需要网络搜索 |
| `agent/SkillsAgent.java` | ERP 场景暂不需要 Shell 技能 |
| `langchain4j-skills` / `langchain4j-experimental-skills-shell` | 不需要 |
| `dify-java-client` | 不使用 Dify 平台 |
| `neo4j-java-driver` | 知识图谱功能暂不实现 |

---

## 十四、风险与注意事项

### 14.1 数据安全

| 风险 | 应对措施 |
|------|----------|
| SQL 注入 | ExecuteSqlQueryTool 增强校验：1) 只允许 SELECT；2) 禁止危险关键词（INTO OUTFILE/LOAD_FILE 等）；3) 表名白名单（正则提取 FROM/JOIN 后的表名）；4) 禁止 INFORMATION_SCHEMA |
| 数据泄露 | AI 只能访问当前租户数据，通过 tenant_id 强制过滤（injectTenantCondition） |
| 敏感信息 | 查询结果中金额、手机号等走脱敏注解 |
| 模型 API Key | 使用 Jasypt 加密存储（nexus-erp 已集成 jasypt-spring-boot-starter 3.0.5） |
| ECharts XSS | LLM 生成的 ECharts JSON 可能包含恶意 JavaScript 函数，渲染前需白名单过滤 |

### 14.2 性能

| 风险 | 应对措施 |
|------|----------|
| LLM 响应慢 | 1) 使用 SSE 流式返回；2) Supervisor 调用前发送"思考中"状态；3) 后续升级为 Token 级流式 |
| SQL 超时 | 设置 5 秒超时 + 1000 行限制（ErpQueryService.executeQuery 参数控制） |
| Token 消耗 | 控制 SystemMessage 长度，使用 Schema 注释而非完整 DDL |
| 并发压力 | 利用 RuoYi-Vue-Plus 的限流机制（Redisson + @RateLimiter） |
| 线程池耗尽 | AI 专用线程池（AiThreadPoolConfig），避免 CompletableFuture.runAsync() 使用 ForkJoinPool |
| 模型重复构建 | ChatModelFactory 缓存已构建的 ChatModel/StreamingChatModel 实例 |

### 14.3 多租户

| 风险 | 应对措施 |
|------|----------|
| 跨租户数据 | AI 工具中自动注入 `tenant_id` 条件（ExecuteSqlQueryTool.injectTenantCondition） |
| 模型配置隔离 | ai_model/ai_provider 表含 tenant_id 字段 |
| 知识库隔离 | ai_knowledge 表含 tenant_id 字段 |
| SSE 连接隔离 | SseEmitterManager 已按 userId 隔离，无需额外处理 |

### 14.4 跨数据源（Phase 3+ 新增）

| 风险 | 应对措施 |
|------|----------|
| MES/IoT 数据源切换 | 不在 Tool 中直接切换数据源，而是注入对应模块的 Service（内部通过 @DS 注解访问） |
| ERP 数据源访问 | 所有 SQL Tool 注入 ErpQueryService（@DS("erp")），不再使用 SpringUtils.getBean(DataSource.class) |
| 跨数据源事务 | AI 查询均为只读，不涉及跨数据源事务 |
| 租户隔离跨数据源 | 各数据源独立维护 tenant_id 字段，Tool 中分别注入 |

### 14.5 SSE 多节点部署

| 风险 | 应对措施 |
|------|----------|
| 单节点消息丢失 | 使用 `SseMessageUtils.publishMessage()` 而非 `sendMessage()`，通过 Redis Pub/Sub 跨节点广播 |
| 消息类型识别 | AI 消息类型编码在 SseMessageDto.message 字段中（JSON 格式），不修改框架 SseMessageDto 类 |

---

## 十五、Quick Start：Phase 0 最小可运行版本

如果想在 **1 周内** 跑通最小闭环，按以下步骤操作：

### Day 1：创建 Maven 模块
1. 在 `ruoyi-common/` 下创建 `ruoyi-common-ai/pom.xml`（依赖 ruoyi-common-core + langchain4j）
2. 在 `ruoyi-modules/` 下创建 `ruoyi-ai/pom.xml`（依赖 ruoyi-common-ai + ruoyi-common-sse + langchain4j-*）
3. 注册到父 pom 和 admin pom

### Day 2：实现模型配置
1. 执行 `ai_provider` 和 `ai_model` 建表 SQL
2. 实现 `ChatModelFactory`（含 ChatModel + StreamingChatModel + 缓存）
3. 配置一个 DeepSeek API Key

### Day 3：实现 ERP 数据源服务 + SSE 聊天接口
1. 实现 `ErpQueryService`（标注 `@DS("erp")`，使用 JdbcTemplate）
2. **不拷贝** SSE 工具类（nexus-erp 已有），实现 `AiSseMessageHelper`（编码消息类型到 message JSON）
3. 实现 `AiChatController` + `AiServiceFacade`
4. 配置 AI 专用线程池（`AiThreadPoolConfig`）
5. 跑通 "用户提问 → DeepSeek 流式回答" 的最小闭环

### Day 4：接入 Text2SQL
1. 拷贝三个 SQL Tool（QueryAllTablesTool、QueryTableSchemaTool、ExecuteSqlQueryTool）
2. **改造数据源注入**：所有 SQL Tool 注入 `ErpQueryService`，不再使用 `SpringUtils.getBean(DataSource.class)`
3. 对 ExecuteSqlQueryTool 增强安全校验（表名白名单 + 危险关键词过滤 + tenant_id 注入）
4. 实现 `ErpQueryAgent`
5. 跑通 "查询本月采购订单" → AI 生成 SQL → 返回结果

### Day 5：前端聊天页面
1. 安装 `vue-element-plus-x` + `hook-fetch`
2. 创建 `AiChatDrawer.vue`（复用 nexus-erp 已有的 SSE 连接，解析 AI 消息类型 JSON）
3. 跑通完整闭环：前端提问 → SSE 流式接收 → Markdown 渲染

---

## 附录 A：修订记录（v1→v2）

| 修订项 | 初版内容 | 修正后 | 严重度 |
|--------|----------|--------|--------|
| SSE 模块 | 从 ruoyi-ai 拷贝 SSE 类 | nexus-erp 已有完整 SSE 模块，直接使用 | 严重 |
| LangChain4j 版本 | 社区版依赖用 `${langchain4j.version}` 1.13.0 | 社区版用 `${langchain4j.community.version}` 1.13.0-beta23 | 严重 |
| 建表 DDL | 缺少 `create_dept`/`create_by`/`update_by`/`del_flag`/`attr1-4` | 补全所有框架标准字段 | 严重 |
| SupervisorAgent | 作为自定义类 `SupervisorAgent.java` | 是内置类，通过 `AgenticServices.supervisorBuilder()` 构建 | 严重 |
| SQL 注入校验 | 仅 `startsWith("SELECT")` | 增加危险关键词黑名单 + 表名白名单 + INFORMATION_SCHEMA 禁止 | 严重 |
| ruoyi-common-ai | 缺失 | 新增公共模块（参考 ruoyi-ai 的 ruoyi-common-chat） | 中等 |
| 流式推送 | `supervisor.invoke()` 同步 + 一次性发送 | 发送"思考中"状态 + 完成后推送结果，后续升级为 Token 级流式 | 中等 |
| EchartsTool | 作为 Java Tool | 移除，图表由 LLM 直接生成 ECharts JSON | 中等 |
| StreamingChatModel | 缺失 | ChatModelFactory 同时构建 ChatModel + StreamingChatModel | 中等 |
| 配置前缀 | `ai:` | `nexus.ai:` 避免与 Spring AI 冲突 | 中等 |
| 跨数据源 | Tool 中用 `DynamicDataSourceContextHolder.push()` | 注入对应模块的 Service（内部通过 @DS 注解访问） | 中等 |
| 交付清单 | "5 个 Agent 接口" | 修正为 2 个（Phase 1 只定义 ErpQueryAgent + ErpReportAgent） | 低 |
| ai_session/ai_message DDL | 缺失 | 补全建表语句 | 低 |
| QueryTableSchemaTool | 说需"增加字段中文注释" | ruoyi-ai 已使用 `SHOW CREATE TABLE`（间接含 COMMENT），无需额外改造 | 低 |
| 前端依赖兼容性 | 未提及 | 新增 vue-element-plus-x 与 Element Plus 2.13.5 兼容性验证提示 | 低 |

## 附录 B：修订记录（v2→v3）

| 修订项 | v2 内容 | v3 修正 | 严重度 |
|--------|---------|---------|--------|
| SSE API 调用方式 | `SseMessageUtils.sendMessage(userId, new SseMessageDto(null, JSON.toJSONString(...)))` | 实际 API 为 `sendMessage(Long, String)`，跨节点应用 `publishMessage(SseMessageDto)`。新增 `AiSseMessageHelper` 封装 | 严重 |
| ExecuteSqlQueryTool 数据源 | 使用 `SpringUtils.getBean(DataSource.class)` 获取数据源 | 在 nexus-erp 中会拿到 master 数据源（ry-vue），查不到 ERP 表。新增 `ErpQueryService`（@DS("erp")），所有 SQL Tool 注入此 Service | 严重 |
| SseMessageDto 修改 | 在 SseMessageDto 中新增 AI 消息类型字段 | SseMessageDto 是框架通用类，不应修改。AI 消息类型编码在 message 字段中（JSON 格式） | 中等 |
| CompletableFuture 线程池 | `CompletableFuture.runAsync()` 不指定线程池 | 使用 ForkJoinPool.commonPool() 可能耗尽公共线程池。新增 `AiThreadPoolConfig` 专用线程池 | 中等 |
| ChatModelFactory 缓存 | 每次请求重建 ChatModel/StreamingChatModel | 新增 ConcurrentHashMap 缓存，避免重复构建 | 中等 |
| Phase 1 交付清单 | "8 张数据库表" | 实际 7 张（4 Phase0 + 3 Phase1） | 低 |
| DDL attr1-attr4 不一致 | ai_provider/ai_model 有 attr1-4，其他表没有 | 所有表统一补全 attr1-attr4 | 低 |
| Phase 3-6 内容 | "与初版相同，此处省略重复内容" | 补全完整内容（MES 场景、IoT 场景、跨系统融合、工作流） | 低 |
| ai_tool/ai_workflow DDL | 仅在总览表中列出，无建表语句 | 补全完整 DDL | 低 |
| ECharts 安全 | 未提及 | LLM 生成的 ECharts JSON 可能包含恶意 JS，新增安全过滤提示 | 低 |
| 表名白名单实现 | 注释"使用正则提取"但无代码 | 提供完整正则 `TABLE_NAME_PATTERN` + 校验逻辑 | 低 |
| ai_message.token_usage | INT 类型 | 改为 BIGINT，避免长对话 Token 统计溢出 | 低 |

## 附录 C：修订记录（v3→v4）

| 修订项 | v3 内容 | v4 修正 | 严重度 |
|--------|---------|---------|--------|
| Milvus 精确删除 | `deleteByDocumentId()` 仅打印日志，注释"不支持按元数据条件删除" | 参考 ruoyi-ai 的 `MilvusVectorStoreStrategy.removeByDocId()`，使用 `MetadataFilterBuilder.metadataKey("documentId").isEqualTo()` + `store.removeAll(filter)` 实现精确删除 | 严重 |
| RerankService 每次新建 ChatModel | 计划中 `rerankWithLLM()` 每次调用 `OpenAiChatModel.builder().build()` | 改为复用 `ChatModelFactory.getChatModel(modelId)` 的缓存机制 | 中等 |
| RerankService 无并发控制 | 计划中串行调用 LLM 打分 | 改为并行打分（CompletableFuture + aiExecutor）+ orTimeout(5s) + 候选数限制15 | 中等 |
| 前端检索结果渲染类型不匹配 | 模板使用 `item.score`/`item.content`，但后端返回 `List<String>` | 修复为 `typeof item === 'string' ? item : item.content`，移除不存在的 score 显示 | 中等 |
| RetrieverService 重复代码 | `retrieve(knowledgeId, query)` 和 `retrieve(knowledgeId, query, overrides)` 逻辑完全重复 | 提取公共方法，`retrieve(knowledgeId, query)` 委托给 `retrieve(knowledgeId, query, null)` | 低 |
| EmbeddingService 策略模式 | switch 分支路由，扩展新厂商需改代码 | 重构为 EmbeddingProvider 接口 + EmbeddingProviderFactory 工厂，扩展只需新增实现类 | 低 |
| 可观测性监听器 | 仅有 AiAgentListener 内存统计（手动调用） | 新增 AiChatModelListener/AiEmbeddingModelListener/AiToolExecutedListener，通过 LangChain4j 原生接口自动回调 | 低 |
| ChatModelFactory 缺少监听器 | 构建 ChatModel 时未注入 ChatModelListener | 注入 `List<ChatModelListener>`，OpenAI 模型构建器传入 `.listeners()` | 低 |
| 第四周计划 Milvus API 用错 | 计划用 `Filter.builder().eq()` | 实际应用 `MetadataFilterBuilder.metadataKey().isEqualTo()` | 低 |
