# NexusERP AI 开发计划 — 第七周（Text2SQL 准确率优化 + Prompt 模板管理 + Agent 构建优化）

> **前置状态**：第六周已全部完成（MCP 外部工具动态接入 + Agent 流式推送 + 对话质量评估 + 工具状态动态过滤 + 工具执行过程可视化），Phase 0 + Phase 1 已全部完成。
>
> **本周目标**：进入 Phase 2，聚焦 Text2SQL 准确率优化（Schema 信息质量提升 + SQL 校验自动修复）、Prompt 模板管理（数据库驱动 + 热更新）、Agent 构建优化（工具差异化注入 + Supervisor 路由引导 + temperature 差异化）。
>
> **参考项目**：`D:\ruoyi-ai`（ChatServiceFacade Agent 构建、chat_config 配置表、WorkflowUtil.renderTemplate 变量渲染）

---

## 现状与差距分析

### 已完成的核心能力

- ✅ 3 路智能路由（Agent/RAG/流式对话）+ SupervisorAgent 双子 Agent 编排
- ✅ 7 个内置 Tool（3 SQL + 4 业务）+ 16 个 @Tool 方法 + MCP 外部工具动态接入
- ✅ ErpTableSchemaManager 缓存（ConcurrentHashMap + 24h TTL + JDBC 元数据）
- ✅ Agent 流式推送（OutputChannel + 9 种 SSE 事件类型 + 前端工具调用可视化）
- ✅ 对话质量评估（消息反馈 + 前端点赞/点踩）

### 本周需解决的差距

| 差距 | 影响 | 优先级 |
|------|------|--------|
| SystemMessage 硬编码在 @SystemMessage 注解中 | 修改需改代码+重启，无法根据 schema 变更动态调整 | P0 |
| ErpTableSchemaManager.getCoreSchemaSummary() 只覆盖 bd_/so_/pu_，且从未被调用 | Agent 缺少 wm_/prd_/sch_ 前缀表的 schema 信息，SQL 生成准确率低 | P0 |
| Schema 信息缺少外键关系、枚举值、业务语义 | LLM 无法自动推断表关联，多表 JOIN 查询经常出错 | P0 |
| 两个 SubAgent 注入完全相同的工具集 | ReportAgent 不需要 ExecuteSqlQueryTool 等工具，浪费 Token 且可能误导 LLM | P1 |
| Supervisor 无自定义 SystemMessage | 路由策略不明确，可能将报表请求分发给 QueryAgent | P1 |
| Agent 模式复用用户配置的 temperature | Agent 模式应使用低 temperature（0.0-0.2）提高 SQL 准确性，当前可能用 0.7 | P1 |
| QueryTableSchemaTool 返回原始 DDL（截断 3000 字符） | 信息密度低（含 ENGINE/CHARSET 等无关信息），截断可能丢失关键定义 | P1 |
| 无 SQL 语法校验和自动修复 | LLM 生成的 SQL 可能有语法错误，直接执行失败，无重试机制 | P2 |
| ExecuteSqlQueryTool 的 @Tool 描述为英文 | 与其他中文描述不一致，可能导致 LLM 理解偏差 | P2 |
| 复合主键只取第一列 | getPrimaryKeys() 只取 rs.next() 第一行，复合主键场景丢失 | P2 |

---

## Task 1：Prompt 模板管理（P0）

> 参考：`D:\ruoyi-ai\ruoyi-modules\ruoyi-aiflow\...\util\WorkflowUtil.java`（renderTemplate 变量渲染）
> 参考：`D:\ruoyi-ai\ruoyi-modules\ruoyi-system\...\domain\ChatConfig.java`（chat_config 配置表）

### 1.1 数据库表设计

```sql
CREATE TABLE ai_prompt_template (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    template_name   VARCHAR(128) NOT NULL COMMENT '模板名称',
    template_key    VARCHAR(64)  NOT NULL COMMENT '模板标识（如 erp_query_agent/erp_report_agent/supervisor）',
    content         TEXT         NOT NULL COMMENT '模板内容（支持 {{variable}} 占位符）',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    is_default      CHAR(1)      DEFAULT '0' COMMENT '是否默认模板（1是 0否）',
    version         INT          DEFAULT 1 COMMENT '版本号',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_key (template_key, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt 模板';
```

### 1.2 后端实现

#### 1.2.1 实体与 Mapper

- **AiPromptTemplate.java**（ruoyi-common-ai）：实体类，字段同上表
- **AiPromptTemplateMapper.java**（ruoyi-common-ai）：Mybatis-Plus Mapper
- **IAiPromptTemplateService.java**（ruoyi-common-ai）：Service 接口
- **AiPromptTemplateServiceImpl.java**（ruoyi-common-ai）：Service 实现

#### 1.2.2 PromptTemplateService（核心渲染服务）

**文件**：`com.nexus.ai.service.prompt.PromptTemplateService`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateService {
    private final IAiPromptTemplateService promptTemplateService;
    private final ErpTableSchemaManager schemaManager;

    // 模板缓存：templateKey -> content（启动时加载，变更时清除）
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 渲染 Prompt 模板
     * @param templateKey 模板标识
     * @param variables 变量映射
     * @return 渲染后的内容
     */
    public String render(String templateKey, Map<String, String> variables) {
        String template = getTemplate(templateKey);
        if (template == null) {
            log.warn("Prompt 模板 {} 不存在，返回空", templateKey);
            return "";
        }
        // 替换 {{variable}} 占位符
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    /**
     * 渲染 ErpQueryAgent 的 SystemMessage
     * 自动注入：schemaSummary、date、availableTools
     */
    public String renderQueryAgentSystemMessage() {
        Map<String, String> variables = new HashMap<>();
        variables.put("schemaSummary", schemaManager.getFullSchemaSummary());
        variables.put("date", LocalDate.now().toString());
        variables.put("availableTools", getAvailableToolsDescription());
        return render("erp_query_agent", variables);
    }

    /**
     * 渲染 ErpReportAgent 的 SystemMessage
     */
    public String renderReportAgentSystemMessage() {
        Map<String, String> variables = new HashMap<>();
        variables.put("schemaSummary", schemaManager.getFullSchemaSummary());
        variables.put("date", LocalDate.now().toString());
        return render("erp_report_agent", variables);
    }

    /**
     * 渲染 Supervisor 的 SystemMessage
     */
    public String renderSupervisorSystemMessage() {
        Map<String, String> variables = new HashMap<>();
        variables.put("date", LocalDate.now().toString());
        return render("supervisor", variables);
    }

    /** 获取模板内容（优先缓存） */
    private String getTemplate(String templateKey) { ... }

    /** 清除缓存（模板变更时调用） */
    public void evictCache(String templateKey) { ... }
}
```

#### 1.2.3 AiPromptTemplateController

**文件**：`com.nexus.ai.controller.AiPromptTemplateController`

```java
@RestController
@RequestMapping("/ai/prompt")
@RequiredArgsConstructor
public class AiPromptTemplateController {

    /** 查询模板列表 */
    @SaCheckPermission("ai:prompt:list")
    @GetMapping("/list")
    public TableDataInfo<AiPromptTemplateVo> list(AiPromptTemplateBo bo) { ... }

    /** 获取模板详情 */
    @SaCheckPermission("ai:prompt:query")
    @GetMapping("/{id}")
    public R<AiPromptTemplateVo> getInfo(@PathVariable Long id) { ... }

    /** 新增模板 */
    @SaCheckPermission("ai:prompt:add")
    @PostMapping
    public R<Void> add(@RequestBody AiPromptTemplateBo bo) { ... }

    /** 修改模板 */
    @SaCheckPermission("ai:prompt:edit")
    @PutMapping
    public R<Void> edit(@RequestBody AiPromptTemplateBo bo) { ... }

    /** 删除模板 */
    @SaCheckPermission("ai:prompt:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) { ... }

    /** 预览渲染结果 */
    @SaCheckPermission("ai:prompt:query")
    @PostMapping("/preview")
    public R<String> preview(@RequestBody AiPromptTemplateBo bo) { ... }
}
```

### 1.3 初始模板数据

在应用启动时（AiPromptTemplateInitializer），自动插入默认模板（如果不存在）：

- `erp_query_agent`：从当前 ErpQueryAgent 的 @SystemMessage 迁移，增加 `{{schemaSummary}}` 和 `{{date}}` 占位符
- `erp_report_agent`：从当前 ErpReportAgent 的 @SystemMessage 迁移
- `supervisor`：新增 Supervisor 路由引导模板

### 1.4 AiServiceFacade 集成

将 Agent 构建从硬编码 @SystemMessage 改为 PromptTemplateService 动态渲染：

```java
// Before：硬编码 @SystemMessage
AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .tools(...)
    .build();

// After：动态 SystemMessage
String querySystemMessage = promptTemplateService.renderQueryAgentSystemMessage();
AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .systemMessage(querySystemMessage)  // 动态注入
    .tools(...)
    .build();
```

### 1.5 前端实现

- **api/ai/prompt.ts**：模板 CRUD + 预览 API
- **views/ai/prompt/index.vue**：模板管理页面（列表 + 编辑对话框 + 变量预览）
- 菜单权限：`ai:prompt:list/query/add/edit/remove`

---

## Task 2：Schema 信息质量提升（P0）

### 2.1 ErpTableSchemaManager 扩展

#### 2.1.1 TableStructure 扩展

在 `TableStructure` 内部类中新增字段：

```java
@Data
public static class TableStructure {
    private String tableName;
    private String tableComment;
    private List<ColumnInfo> columns;
    private List<ForeignKeyRelation> foreignKeys;  // 【新增】外键关系
    private List<String> primaryKeyColumns;         // 【新增】复合主键支持
}

@Data
public static class ColumnInfo {
    private String name;
    private String type;
    private String comment;
    private boolean nullable;
    private boolean primaryKey;
    private String defaultValue;
    private String enumValues;   // 【新增】枚举值（从字段注释中提取，如 "状态[DRAFT/CONFIRMED/DONE]"）
}

@Data
public static class ForeignKeyRelation {
    private String columnName;       // 本表字段
    private String referencedTable;  // 引用表
    private String referencedColumn; // 引用字段
}
```

#### 2.1.2 外键关系自动发现

```java
/** 获取外键关系 */
private List<ForeignKeyRelation> discoverForeignKeys(Connection conn, String tableName) throws SQLException {
    List<ForeignKeyRelation> relations = new ArrayList<>();
    try (ResultSet rs = conn.getMetaData().getImportedKeys(conn.getCatalog(), null, tableName)) {
        while (rs.next()) {
            ForeignKeyRelation fk = new ForeignKeyRelation();
            fk.setColumnName(rs.getString("FKCOLUMN_NAME"));
            fk.setReferencedTable(rs.getString("PKTABLE_NAME"));
            fk.setReferencedColumn(rs.getString("PKCOLUMN_NAME"));
            relations.add(fk);
        }
    }
    return relations;
}
```

#### 2.1.3 复合主键支持

```java
/** 获取主键列（支持复合主键） */
private List<String> discoverPrimaryKeys(Connection conn, String tableName) throws SQLException {
    List<String> pkColumns = new ArrayList<>();
    try (ResultSet rs = conn.getMetaData().getPrimaryKeys(conn.getCatalog(), null, tableName)) {
        while (rs.next()) {
            pkColumns.add(rs.getString("COLUMN_NAME"));
        }
    }
    return pkColumns;
}
```

#### 2.1.4 枚举值提取

从字段注释中自动提取枚举值（格式：`状态[DRAFT/CONFIRMED/DONE]` 或 `类型[1-采购/2-销售/3-生产]`）：

```java
/** 从字段注释中提取枚举值 */
private String extractEnumValues(String comment) {
    if (comment == null) return null;
    // 匹配 [...] 中的枚举定义
    Matcher matcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(comment);
    if (matcher.find()) {
        return matcher.group(1);
    }
    return null;
}
```

#### 2.1.5 getFullSchemaSummary() 新方法

替代当前只覆盖 bd_/so_/pu_ 的 `getCoreSchemaSummary()`：

```java
/** 获取全量 Schema 摘要（覆盖所有允许前缀的表） */
public String getFullSchemaSummary() {
    return schemaCache.values().stream()
        .filter(t -> allowedTablePrefixes.stream().anyMatch(t.getTableName()::startsWith))
        .sorted(Comparator.comparing(TableStructure::getTableName))
        .map(this::formatEnhancedTableSummary)
        .collect(Collectors.joining("\n\n"));
}

/** 增强版表摘要格式 */
private String formatEnhancedTableSummary(TableStructure t) {
    StringBuilder sb = new StringBuilder();
    sb.append("表 ").append(t.getTableName());
    if (t.getTableComment() != null) sb.append("（").append(t.getTableComment()).append("）");
    sb.append(":\n");

    // 字段信息（含类型、枚举值）
    for (ColumnInfo col : t.getColumns()) {
        sb.append("  ").append(col.getName());
        sb.append(" ").append(col.getType());
        if (col.isPrimaryKey()) sb.append(" PK");
        if (col.getEnumValues() != null) sb.append(" [").append(col.getEnumValues()).append("]");
        if (col.getComment() != null && !col.getComment().isEmpty()) sb.append(" -- ").append(col.getComment());
        sb.append("\n");
    }

    // 外键关系
    if (t.getForeignKeys() != null && !t.getForeignKeys().isEmpty()) {
        sb.append("  关联: ");
        for (ForeignKeyRelation fk : t.getForeignKeys()) {
            sb.append(fk.getColumnName()).append("->").append(fk.getReferencedTable())
              .append(".").append(fk.getReferencedColumn()).append("  ");
        }
        sb.append("\n");
    }

    return sb.toString();
}
```

### 2.2 QueryTableSchemaTool 优化

将返回格式从原始 DDL 改为结构化摘要：

```java
@Tool("查询指定表的结构信息，包含字段类型、注释、枚举值和外键关系")
public String queryTableSchema(@P("表名") String tableName) {
    // 安全校验
    if (!tableName.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*")) {
        return "错误：只允许查询业务表";
    }
    TableStructure structure = schemaManager.getTableStructure(tableName);
    if (structure == null) {
        return "错误：未找到表 " + tableName;
    }
    return schemaManager.formatEnhancedTableSummary(structure);
}
```

### 2.3 ExecuteSqlQueryTool 描述修正

```java
// Before（英文）
@Tool("Execute a SELECT SQL query on ERP database and return the results")

// After（中文，与其他 Tool 一致）
@Tool("执行 ERP 数据库查询（只读），返回查询结果")
```

---

## Task 3：Agent 构建优化（P1）

### 3.1 工具差异化注入

当前两个 SubAgent 注入完全相同的工具集，优化为按需注入：

```java
// ErpQueryAgent：需要 SQL 工具 + 业务工具
List<Object> queryTools = builtinTools.stream()
    .filter(t -> isQueryRelevantTool(t))  // 所有业务工具 + 3个SQL工具
    .collect(Collectors.toList());

// ErpReportAgent：只需要业务工具 + Schema 工具（不需要 ExecuteSqlQueryTool）
List<Object> reportTools = builtinTools.stream()
    .filter(t -> isReportRelevantTool(t))  // 业务工具 + QueryAllTablesTool + QueryTableSchemaTool
    .collect(Collectors.toList());
```

实现方式：在 BuiltinToolProvider 接口中新增 `getCategories()` 方法，返回工具类别标签（如 `QUERY`/`REPORT`/`ALL`），ToolProviderFactory 按类别过滤。

### 3.2 Supervisor SystemMessage

新增 Supervisor 路由引导模板，通过 PromptTemplateService 动态注入：

```
你是 NexusERP 智能助手调度中心。根据用户问题，选择最合适的子 Agent 处理：

1. **数据查询助手**（erp_query_agent）：适用于需要查询具体数据、执行 SQL、回答事实性问题的场景
   - 例："本月采购总额是多少？"、"库存低于安全线的物料有哪些？"

2. **报表生成助手**（erp_report_agent）：适用于需要生成图表、可视化数据趋势的场景
   - 例："展示最近6个月销售趋势图"、"各品类采购金额占比饼图"

路由规则：
- 如果用户只问数据 → 分发给数据查询助手
- 如果用户要求图表/可视化 → 分发给报表生成助手
- 如果用户同时要数据和图表 → 先让查询助手获取数据，再让报表助手生成图表
- 如果不确定 → 优先分发给数据查询助手

当前日期：{{date}}
```

### 3.3 Agent 模式 temperature 差异化

在 ChatModelFactory 中新增 Agent 专用模型构建方法：

```java
/**
 * 获取 Agent 专用 ChatModel（低 temperature，提高 SQL 生成准确性）
 */
public ChatModel getAgentChatModel(Long modelId) {
    return agentModelCache.computeIfAbsent(modelId, id -> {
        AiModel config = aiModelService.selectById(id);
        return buildChatModel(config, 0.1);  // Agent 模式强制 temperature=0.1
    });
}
```

AiServiceFacade 中 Agent 构建改用 `getAgentChatModel()`。

### 3.4 历史上下文优化

当前 `buildContextualQuery` 将 20 条历史消息全量拼接，优化为摘要压缩：

```java
/** 构建上下文（最近 3 轮完整对话 + 更早对话的摘要） */
private String buildContextualQuery(Long sessionId, String currentQuery) {
    List<AiMessage> history = getRecentMessages(sessionId, 10);  // 最近10条（5轮）
    if (history.isEmpty()) {
        return currentQuery;
    }
    StringBuilder context = new StringBuilder();
    context.append("【对话历史】\n");
    for (AiMessage msg : history) {
        String role = "USER".equals(msg.getRole()) ? "用户" : "AI";
        String content = msg.getContent();
        // 截断过长消息（保留前 200 字符）
        if (content.length() > 200) {
            content = content.substring(0, 200) + "...";
        }
        context.append(role).append(": ").append(content).append("\n");
    }
    context.append("\n【当前问题】\n").append(currentQuery);
    return context.toString();
}
```

---

## Task 4：SQL 校验与自动修复（P2）

### 4.1 SQL 语法预校验

在 ExecuteSqlQueryTool 中新增 EXPLAIN 预校验：

```java
/** SQL 语法预校验（通过 EXPLAIN 验证） */
private boolean validateSqlSyntax(String sql) {
    try {
        erpQueryService.executeQuery("EXPLAIN " + sql, 1, 2);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

### 4.2 Agent 自动重试机制

在 AiServiceFacade 的 Agent 调用中，当 SQL 执行失败时自动重试：

```java
// 在 AgentStreamHelper 中增加 SQL 错误事件
public void sendSqlError(String requestId, String sql, String errorMessage) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("toolName", "executeSqlQuery");
    payload.put("sql", truncate(sql, 500));
    payload.put("error", truncate(errorMessage, 300));
    payload.put("retryable", true);
    send(requestId, "AI_SQL_ERROR", toJson(payload));
}
```

LangChain4j Agentic 框架本身支持工具返回错误信息后 LLM 自动重试（重新生成 SQL），只需确保 ExecuteSqlQueryTool 返回清晰的错误信息即可，无需额外实现重试循环。

---

## 文件变更清单

### 新增文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `AiPromptTemplate.java` | ruoyi-common-ai/domain/entity | Prompt 模板实体 |
| `AiPromptTemplateBo.java` | ruoyi-common-ai/domain/bo | Prompt 模板 BO |
| `AiPromptTemplateVo.java` | ruoyi-common-ai/domain/vo | Prompt 模板 VO |
| `AiPromptTemplateMapper.java` | ruoyi-common-ai/mapper | Prompt 模板 Mapper |
| `IAiPromptTemplateService.java` | ruoyi-common-ai/service | Prompt 模板 Service 接口 |
| `AiPromptTemplateServiceImpl.java` | ruoyi-common-ai/service/impl | Prompt 模板 Service 实现 |
| `PromptTemplateService.java` | ruoyi-ai/service/prompt | Prompt 渲染服务（核心） |
| `AiPromptTemplateInitializer.java` | ruoyi-ai/config | 默认模板初始化 |
| `AiPromptTemplateController.java` | ruoyi-ai/controller | Prompt 模板管理 API |
| `prompt.ts` | plus-ui/src/api/ai | Prompt 模板前端 API |
| `index.vue` | plus-ui/src/views/ai/prompt | Prompt 模板管理页面 |

### 修改文件

| 文件 | 变更内容 |
|------|----------|
| `ErpTableSchemaManager.java` | 新增 ForeignKeyRelation/ColumnInfo 扩展 + getFullSchemaSummary() + formatEnhancedTableSummary() + 外键发现 + 复合主键 + 枚举值提取 |
| `QueryTableSchemaTool.java` | 返回格式从原始 DDL 改为结构化摘要 |
| `ExecuteSqlQueryTool.java` | @Tool 描述改为中文 + 新增 EXPLAIN 预校验 |
| `AiServiceFacade.java` | Agent 构建改用 PromptTemplateService 动态 SystemMessage + 工具差异化注入 + Agent 专用 ChatModel + 历史上下文优化 |
| `ChatModelFactory.java` | 新增 getAgentChatModel()（低 temperature） |
| `BuiltinToolProvider.java` | 新增 getCategories() 方法 |
| `7 个 ToolProvider 实现` | 实现 getCategories() 方法 |
| `ToolProviderFactory.java` | 新增按类别过滤工具方法 |
| `AiSseMessageHelper.java` | 新增 AI_SQL_ERROR 事件类型 |
| `AgentStreamHelper.java` | 新增 sendSqlError() 方法 |
| `router/index.ts` | 新增 /ai/prompt 路由 |

### SQL 变更

| 文件 | 说明 |
|------|------|
| `ry-vue.sql` | 新增 ai_prompt_template 表 |

---

## 依赖关系与执行顺序

```
Task 1 (Prompt 模板管理)
  ├── 1.1 数据库表
  ├── 1.2 后端实体/Mapper/Service
  ├── 1.2.2 PromptTemplateService（核心渲染） ←── 依赖 2.1.5 getFullSchemaSummary()
  ├── 1.2.3 Controller
  ├── 1.3 初始模板数据初始化
  ├── 1.4 AiServiceFacade 集成 ←── 依赖 1.2.2 + 3.1 + 3.2 + 3.3
  └── 1.5 前端实现

Task 2 (Schema 信息质量提升) ←── 可与 Task 1 并行
  ├── 2.1 ErpTableSchemaManager 扩展
  │   ├── 2.1.1 TableStructure 扩展（ForeignKeyRelation/ColumnInfo）
  │   ├── 2.1.2 外键关系自动发现
  │   ├── 2.1.3 复合主键支持
  │   ├── 2.1.4 枚举值提取
  │   └── 2.1.5 getFullSchemaSummary() 新方法
  ├── 2.2 QueryTableSchemaTool 优化
  └── 2.3 ExecuteSqlQueryTool 描述修正

Task 3 (Agent 构建优化) ←── 依赖 Task 1.2.2 + Task 2.1.5
  ├── 3.1 工具差异化注入
  ├── 3.2 Supervisor SystemMessage
  ├── 3.3 Agent 模式 temperature 差异化
  └── 3.4 历史上下文优化

Task 4 (SQL 校验与自动修复) ←── 独立，可与 Task 2 并行
  ├── 4.1 SQL 语法预校验
  └── 4.2 Agent 自动重试机制
```

**建议执行顺序**：
1. Day 1：Task 2（Schema 信息质量提升）+ Task 4（SQL 校验）并行
2. Day 2-3：Task 1（Prompt 模板管理）后端
3. Day 4：Task 3（Agent 构建优化）+ Task 1.4（AiServiceFacade 集成）
4. Day 5：Task 1.5（前端）+ 整体测试

---

## 验收标准

| Task | 验收标准 |
|------|----------|
| Task 1 | 1) 前端可管理 Prompt 模板（CRUD + 预览渲染结果）<br>2) 修改模板后 Agent 立即使用新模板（无需重启）<br>3) 模板支持 {{schemaSummary}}/{{date}} 等变量自动替换<br>4) 默认模板在首次启动时自动初始化 |
| Task 2 | 1) Schema 摘要覆盖所有 6 类前缀表（bd_/so_/pu_/prd_/wm_/sch_）<br>2) 字段信息包含类型、枚举值、外键关系<br>3) QueryTableSchemaTool 返回结构化摘要而非原始 DDL<br>4) 复合主键正确识别 |
| Task 3 | 1) QueryAgent 和 ReportAgent 注入不同的工具集<br>2) Supervisor 有明确的路由引导 SystemMessage<br>3) Agent 模式使用 temperature=0.1<br>4) 历史上下文截断到 200 字符/条，不再全量拼接 |
| Task 4 | 1) SQL 语法错误时返回清晰的错误信息（含 EXPLAIN 错误原因）<br>2) LLM 能根据错误信息自动修正 SQL 并重试 |

---

## 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| LangChain4j AgenticServices.agentBuilder() 不支持动态 systemMessage | Task 1 核心受阻 | 查阅 API 确认是否支持 .systemMessage() 参数；如不支持，降级为在 @UserMessage 中拼接上下文 |
| MySQL 外键元数据可能不完整（很多项目不建物理外键） | 外键关系发现效果差 | 补充基于命名约定的软外键推断（如 xxx_id 字段关联 bd_xxx 表） |
| Prompt 模板渲染后 Token 超限 | Schema 摘要过长导致超出模型上下文窗口 | 动态裁剪：按表优先级截断，保证核心表完整、边缘表省略 |
| getFullSchemaSummary() 性能 | 表多时生成摘要耗时 | 缓存摘要结果，schema 变更时才重新生成 |

---

## 第八周展望

1. **业务 Tool 查询增强**：为 4 个业务 Tool 各新增 2-3 个分析方法（库存周转率、销售毛利率、供应商价格对比等）
2. **SQL Few-Shot 示例库**：新增 ai_sql_example 表，存储典型查询示例，Agent 调用时动态注入 Top-K 示例
3. **对话导出**：支持导出聊天记录为 Markdown 文件
