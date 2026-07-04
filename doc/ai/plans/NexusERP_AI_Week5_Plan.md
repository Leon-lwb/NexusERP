# NexusERP AI 开发计划 — 第五周（Schema 增强 + 业务 Tool 适配 + MCP 基础）

> **前置状态**：第四周已全部完成（RerankService 集成、API Key 加密、EmbeddingService 策略模式、可观测性增强），编译通过。
>
> **本周目标**：提升 Text2SQL 准确率（Schema 缓存+SystemMessage 优化）、业务 Tool 适配真实表结构、搭建 MCP 内置工具注册中心、导入预置知识库内容。
>
> **参考项目**：`D:\ruoyi-ai`（TableSchemaManager、BuiltinToolRegistry、SystemToolInitializer）

---

## 现状与差距分析

### 已完成的核心能力
- ✅ 3 路智能路由（Agent/RAG/流式对话）
- ✅ 7 个 Tool（3 SQL + 4 业务）框架完成
- ✅ RAG 完整流水线（解析→分段→向量化→检索→重排序）
- ✅ Milvus/InMemory 双模式向量存储
- ✅ EmbeddingService 策略模式
- ✅ 可观测性监听器
- ✅ 前端聊天/知识库/模型管理页面

### 本周需解决的差距

| 差距 | 影响 | 优先级 |
|------|------|--------|
| 无 TableSchemaManager 缓存机制 | Agent 每次查询都要调 SHOW CREATE TABLE，Token 消耗大、响应慢 | P0 |
| ErpQueryAgent SystemMessage 缺乏业务 Schema 注释 | LLM 不理解 ERP 表含义，Text2SQL 准确率低 | P0 |
| 4 个业务 Tool 部分方法缺失 + SQL 字段名需与实际表结构校验 + 重复代码需重构 | 部分场景无法查询、代码维护成本高 | P0 |
| MCP 内置工具注册中心完全缺失 | 无法统一管理 Tool，后续无法扩展外部 MCP 服务 | P1 |
| 知识库无预置 ERP 业务文档 | RAG 问答缺少 ERP 操作手册/业务流程知识 | P1 |
| 前端缺少 MCP 工具管理页面 | 无法通过 UI 管理工具配置 | P2 |

---

## Task 1（周一）：TableSchemaManager 表结构缓存

### 目标
参考 ruoyi-ai 的 `TableSchemaManager` + `TableSchemaInitializer`，为 nexus-erp 创建 ERP 表结构缓存管理器，应用启动时自动加载表结构，避免 Agent 每次动态查询消耗 Token。

### 参考文件
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\agent\manager\TableSchemaManager.java`
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\agent\manager\TableSchemaInitializer.java`

### 新建文件

**1. `ErpTableSchemaManager.java`** — `com.nexus.ai.service.query`

核心设计：
- **注入 `@DS("erp") DataSource erpDataSource`**（而非 JdbcTemplate），因为需要使用 JDBC `DatabaseMetaData` API 获取表结构、列信息、主键等
- `ConcurrentHashMap<String, TableStructure>` 缓存所有 ERP 业务表结构
- `TableStructure` 内部类包含：tableName、tableComment、columns（List<ColumnInfo>）、ddl（SHOW CREATE TABLE 结果）
- `ColumnInfo` 包含：columnName、columnType、columnComment、isPrimaryKey
- 使用 `@EventListener(ApplicationReadyEvent.class)` 在启动时自动加载（比 `@PostConstruct` 更晚，确保数据源就绪）
- 只加载 `bd_*`、`so_*`、`pu_*`、`prd_*`、`wm_*`、`sch_*` 前缀的表
- 提供 `getAllTableSchemas()`、`getTableSchema(tableName)`、`refreshTableSchema(tableName)`、`refreshAll()` 方法
- 生成简化版 Schema 摘要（表名+注释+核心字段+注释），用于嵌入 Agent SystemMessage
- **缓存失效策略**：
  - 提供手动刷新 API：`PUT /ai/schema/refresh`（开发期表结构变更时使用）
  - 可选 TTL 过期：配置项 `nexus.ai.schema-cache-ttl-hours`（默认 24 小时），在 `getTableSchema()` 时检查是否过期
  - 启动时强制刷新：`ErpTableSchemaInitializer` 调用 `refreshAll()`

**关键实现要点**：
```java
@Service
@DS("erp")
public class ErpTableSchemaManager {
    @Autowired
    private DataSource erpDataSource;  // 通过 @DS("erp") 注入 ERP 数据源
    
    public void initializeSchema() {
        try (Connection conn = erpDataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            // 使用 metaData.getTables()、getColumns()、getPrimaryKeys() 获取完整表结构
            // 参考 ruoyi-ai 的 TableSchemaManager.loadTableSchema() 实现
        }
    }
}
```

> **注意**：不使用 `JdbcTemplate` 是因为 `DatabaseMetaData` API 需要在同一个 `Connection` 上操作，`JdbcTemplate` 的每次操作可能获取不同的连接。

**2. `ErpTableSchemaInitializer.java`** — `com.nexus.ai.service.query`

- 监听 `ApplicationReadyEvent`（比 `ContextRefreshedEvent` 更晚，确保数据源就绪）
- 调用 `ErpTableSchemaManager.initializeSchema()` 触发缓存加载
- 日志输出加载的表数量和耗时

### 修改文件

**`QueryAllTablesTool.java`** — 改为从 `ErpTableSchemaManager` 缓存获取，不再每次查 `INFORMATION_SCHEMA`：
```java
@Tool("查询数据库中所有可用的 ERP 业务表")
public String queryAllTables() {
    return erpTableSchemaManager.getAllTableSchemas()
        .stream()
        .map(t -> "- " + t.getTableName() + ": " + t.getTableComment())
        .collect(Collectors.joining("\n"));
}
```

**`QueryTableSchemaTool.java`** — 优先从缓存获取 DDL：
```java
@Tool("查询指定表的结构和字段说明")
public String queryTableSchema(@P("表名") String tableName) {
    TableStructure schema = erpTableSchemaManager.getTableSchema(tableName);
    if (schema != null) return schema.getDdl();
    return erpQueryService.queryTableSchema(tableName); // fallback
}
```

### 交付物
- [ ] `ErpTableSchemaManager.java` — 表结构缓存管理器
- [ ] `ErpTableSchemaInitializer.java` — 启动初始化触发器
- [ ] `QueryAllTablesTool.java` / `QueryTableSchemaTool.java` — 改为缓存优先

---

## Task 2（周二）：ErpQueryAgent SystemMessage 优化

### 目标
嵌入 ERP 业务 Schema 摘要到 Agent SystemMessage，让 LLM 直接理解 ERP 数据模型，大幅提升 Text2SQL 准确率。

### 参考
- ruoyi-ai 的 SqlAgent 通过 Tool 动态获取表结构（不在 SystemMessage 中嵌入）
- 但 nexus-erp 的 ERP 表结构是已知且固定的，**静态嵌入核心 Schema 摘要** + **动态查询细节** 结合效果最佳

### 修改文件

**`ErpQueryAgent.java`** — 重写 SystemMessage：

关键改进点：
1. **嵌入核心表 Schema 摘要**（从 ErpTableSchemaManager 获取精简版）：
   - 表名 + 中文注释
   - 核心字段 + 中文注释 + 数据类型
   - 状态枚举值含义（如 order_status: DRAFT/CONFIRMED/FULFILLED/CLOSED/CANCELLED）
2. **明确工具使用策略**：业务专用 Tool 优先，通用 SQL Tool 兜底
3. **增加业务规则提示**：
   - "查询库存时使用 wm_material_stock 表的 stock_quantity 字段"
   - "采购订单状态流转：DRAFT→SUBMITTED→APPROVING→APPROVED→PARTIALLY_RECEIVED→RECEIVED→CLOSED"
   - "销售订单关联关系：so_sales_order_header → so_sales_order_line → bd_material"
4. **SQL 生成规范**：
   - 强制使用表别名提升可读性
   - 金额字段使用 `ROUND(x, 2)` 保留2位小数
   - 日期比较使用 `DATE_FORMAT` 或 `>=` / `<` 范围查询
   - JOIN 时务必使用正确的关联字段

**Token 预算分级策略**：
- **核心表（约 20 张）**：`bd_*`、`so_*`、`pu_*` 前缀的表，摘要嵌入 SystemMessage（约 1500 Token）
- **非核心表（约 44 张）**：`prd_*`、`wm_*`、`sch_*` 前缀的表，通过 `queryAllTables` / `queryTableSchema` 动态获取
- **总预算控制**：SystemMessage 中 Schema 摘要部分不超过 2000 Token（含工具策略说明、业务规则等）
- **实现方式**：`ErpTableSchemaManager.getCoreSchemaSummary()` 只返回核心表摘要，非核心表通过 Tool 动态查询

**`ErpReportAgent.java`** — 同步优化 SystemMessage：
1. 增加"先通过 ErpQueryAgent 的 Tool 获取数据，再生成 ECharts 配置"的工作流说明
2. 嵌入 ERP 核心表的关联关系图（文本形式）

### Schema 摘要格式（嵌入 SystemMessage 的部分内容示例）

```
=== ERP 核心业务表 ===

【基础数据 bd_*】
bd_material: 物料主数据(material_id PK, material_code 编码, material_name 名称, material_spec 规格, category_id 分类FK, base_uom_id 单位FK, safety_stock 安全库存, standard_cost 标准成本, status 状态)
bd_vendor: 供应商(vendor_id PK, vendor_code 编码, vendor_name 名称, vendor_type 类型, contact_name 联系人, status 状态)
bd_material_category: 物料分类(category_id PK, category_name 名称, parent_id 父分类)
bd_bom: BOM物料清单(bom_id PK, bom_code 编码, material_id 成品物料FK, version 版本, status 状态)
bd_bom_detail: BOM明细(detail_id PK, bom_id FK, material_id 子件物料FK, quantity 用量, unit_id 单位)

【销售管理 so_*】
so_sales_order_header: 销售订单头(so_id PK, so_no 编号, client_id 客户FK, order_date 订单日期, promise_date 承诺交期, total_amount 总金额, order_status 状态[DRAFT/CONFIRMED/FULFILLED/CLOSED/CANCELLED])
so_sales_order_line: 销售订单行(line_id PK, so_id FK, material_id 物料FK, ordered_qty 订单数量, delivered_qty 已发货数量, unit_price 单价, line_status 行状态)
...
```

### 交付物
- [ ] `ErpQueryAgent.java` — SystemMessage 嵌入完整 Schema 摘要
- [ ] `ErpReportAgent.java` — SystemMessage 增加数据关联说明

---

## Task 3（周三）：业务 Tool 补充 + 代码重构 + SQL 参数化

### 目标
1. 校验现有 4 个业务 Tool 的 SQL 字段名是否与实际 ERP 表结构匹配（已实现 3 个方法/Tool，但字段名可能不一致）
2. 补充缺失的业务查询方法（每个 Tool 新增 1 个方法，达到 4 个/Tool）
3. 提取公共代码为 `ErpToolHelper` 基类，消除 5 个 Tool 中重复的 `injectTenantCondition()` 和 `formatResults()`（约 250 行重复代码）
4. 将 `String.format()` 拼接 SQL 改为参数化查询，消除 SQL 注入风险

### 现状分析
当前 4 个业务 Tool 已实现真实 SQL（非占位代码），但存在以下问题：
- SQL 中使用 `String.format()` 拼接用户参数（如 `materialCode`），虽有单引号转义但模式脆弱
- `injectTenantCondition()` 和 `formatResults()` 在 5 个 Tool 中完全重复（各约 50 行）
- 部分方法缺失：如采购价格历史、销售履行率、生产完成率等

### 实际 ERP 表结构参考（来自 `script/sql/ry-erp.sql`）

**核心表关系**：
- `bd_material` → `bd_material_category`（分类）→ `bd_unit_of_measure`（单位）
- `bd_material_vendor` → `bd_vendor`（供应商）+ `bd_material`（物料）
- `so_sales_order_header` → `so_sales_order_line` → `bd_material`
- `pu_purchase_order` → `pu_purchase_order_line` → `bd_material` + `bd_vendor`
- `wm_material_stock` → `bd_material` + `wm_warehouse` + `wm_storage_location`
- `wm_material_stock_tx` → 库存事务流水
- `prd_work_order` → `bd_material` + `bd_bom` + `bd_workshop` + `bd_workstation`

### 新建文件

**`ErpToolHelper.java`** — `com.nexus.ai.tool`（公共工具基类）

提取所有 Tool 的公共逻辑：
```java
@Slf4j
public abstract class ErpToolHelper {
    protected final ErpQueryService erpQueryService;
    
    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);
    
    /** 注入租户隔离条件（从 AiRequestContext 获取 tenantId） */
    protected String injectTenantCondition(String sql) { ... }
    
    /** 格式化查询结果为 Markdown 表格（最多 8 列 × 10 行） */
    protected String formatResults(List<Map<String, Object>> results) { ... }
    
    /** 执行查询并格式化（统一异常处理） */
    protected String executeAndFormat(String sql, String toolName) {
        try {
            sql = injectTenantCondition(sql);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-{}] 查询失败", toolName, e);
            return "查询失败：" + e.getMessage();
        }
    }
}
```

### 修改文件

**1. `ErpQueryService.java`** — 新增参数化查询方法：
```java
/**
 * 执行参数化 SELECT 查询（防止 SQL 注入）
 */
public List<Map<String, Object>> executeParameterizedQuery(String sql, Object[] params, int maxRows, int timeoutSeconds) {
    return jdbcTemplate.query(
        con -> {
            var ps = con.prepareStatement(sql);
            ps.setQueryTimeout(timeoutSeconds);
            ps.setMaxRows(maxRows);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        },
        (rs, rowNum) -> { ... }
    );
}
```

**2. `ErpInventoryTool.java`**（库存工具）— 继承 `ErpToolHelper` + 新增方法 + 参数化：

| @Tool 方法 | 状态 | 改动 |
|-----------|------|------|
| `queryLowStockMaterials()` | 已有 | 改为参数化（移除 String.format） |
| `queryMaterialOutboundTrend(materialCode, months)` | 已有 | 改为参数化 |
| `queryInventoryValue()` | 已有 | 改为参数化 |
| `queryStockByWarehouse()` | **新增** | 按仓库维度统计库存数量和金额 |

**3. `ErpPurchaseTool.java`**（采购工具）— 继承 `ErpToolHelper` + 新增方法 + 参数化：

| @Tool 方法 | 状态 | 改动 |
|-----------|------|------|
| `queryPurchaseAmountByVendor(startDate, endDate)` | 已有 | 改为参数化 |
| `queryVendorRiskAssessment(vendorId)` | 已有 | 改为参数化 |
| `queryPendingPurchaseOrders(days)` | 已有 | 改为参数化 |
| `queryPurchasePriceHistory(materialCode, months)` | **新增** | 查采购订单明细中的历史价格趋势 |

**4. `ErpSalesTool.java`**（销售工具）— 继承 `ErpToolHelper` + 新增方法 + 参数化：

| @Tool 方法 | 状态 | 改动 |
|-----------|------|------|
| `queryMonthlySalesTrend(materialCode, months)` | 已有 | 改为参数化 |
| `queryTopClientsBySales(days, topN)` | 已有 | 改为参数化 |
| `queryRecentReturns(days)` | 已有 | 改为参数化 |
| `querySalesOrderFulfillmentRate()` | **新增** | 查销售订单行的 delivered_qty / ordered_qty 计算履行率 |

**5. `ErpProductionTool.java`**（生产工具）— 继承 `ErpToolHelper` + 新增方法 + 参数化：

| @Tool 方法 | 状态 | 改动 |
|-----------|------|------|
| `queryActiveWorkOrders()` | 已有 | 改为参数化 |
| `queryWorkstationUtilization(workshopId)` | 已有 | 改为参数化 |
| `queryBomExplosion(materialCode)` | 已有 | 改为参数化 |
| `queryProductionCompletionRate(days)` | **新增** | 按时间段统计 completed_qty / planned_qty |

### 通用改造点
- 所有 Tool 继承 `ErpToolHelper`，消除重复的 `injectTenantCondition()` 和 `formatResults()`
- 所有 SQL 参数使用 `?` 占位符 + `Object[]` 参数，通过 `ErpQueryService.executeParameterizedQuery()` 执行
- 新增方法自动注入 `tenant_id` 过滤条件
- 结果格式化为 Markdown 表格，最多显示 8 列 × 10 行
- 异常处理：SQL 执行失败时返回友好错误信息

### 交付物
- [ ] `ErpToolHelper.java` — 公共工具基类（消除 250 行重复代码）
- [ ] `ErpQueryService.java` — 新增 `executeParameterizedQuery()` 方法
- [ ] `ErpInventoryTool.java` — 继承基类 + 新增 1 个方法 + 参数化
- [ ] `ErpPurchaseTool.java` — 继承基类 + 新增 1 个方法 + 参数化
- [ ] `ErpSalesTool.java` — 继承基类 + 新增 1 个方法 + 参数化
- [ ] `ErpProductionTool.java` — 继承基类 + 新增 1 个方法 + 参数化

---

## Task 4（周四）：MCP 内置工具注册中心

### 目标
参考 ruoyi-ai 的 MCP 模块架构，为 nexus-erp 搭建内置工具注册中心的基础骨架，实现工具的自动发现、数据库同步和 UI 管理。

### 参考文件
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\mcp\service\core\BuiltinToolProvider.java`
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\mcp\service\core\BuiltinToolRegistry.java`
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\mcp\service\core\ToolProviderFactory.java`
- `D:\ruoyi-ai\ruoyi-ai\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\config\mcp\SystemToolInitializer.java`

### 前置检查
> **重要**：当前代码中 `com.nexus.ai.tool` 包下已存在 `BuiltinToolProvider.java`、`BuiltinToolRegistry.java`、`ToolProviderFactory.java`、`SystemToolInitializer.java` 等文件（可能是之前周创建的占位文件）。开发前需先检查这些文件内容，确认是否为空壳或已有实现，避免重复创建或包路径冲突。
> - 如果已有文件为空壳：直接在原文件中补充实现
> - 如果已有文件有完整实现：直接使用，无需新建
> - 如果包路径与计划不一致（如在 `com.nexus.ai.tool` 而非 `com.nexus.ai.mcp`）：统一迁移到 `com.nexus.ai.mcp` 包下

### 关键设计决策：工具实例化策略

> **⚠️ 严重问题**：ruoyi-ai 的 `BuiltinToolRegistry.getAllBuiltinToolObjects()` 通过 `newInstance()` 无参构造创建工具实例。但 nexus-erp 的 Tool 类通过 `@RequiredArgsConstructor` 注入依赖（如 `ErpQueryService`、`AiProperties`），**没有无参构造函数**。`newInstance()` 创建的实例中依赖为 null，调用会 NPE。
>
> ruoyi-ai 能工作是因为它的 Tool 内部通过 `SpringUtils.getBean()` 静态获取依赖，而 nexus-erp 在 v3 改造时已弃用此方式。

**解决方案（二选一，推荐方案 A）**：

**方案 A：BuiltinToolRegistry 仅用于元数据管理，Agent 构建仍使用 Spring Bean**
```java
// BuiltinToolRegistry 只存储工具元数据（名称、描述、显示名），用于数据库同步和 UI 展示
// 不通过 newInstance() 创建工具实例

// AiServiceFacade 中 Agent 构建保持当前方式（直接注入 Spring Bean）：
ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
        erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)
    .build();
```
- 优点：简单可靠，不需要处理依赖注入问题
- 缺点：工具列表在代码中硬编码，新增 Tool 需改代码

**方案 B：BuiltinToolRegistry 通过 Spring 容器获取工具 Bean**
```java
// BuiltinToolRegistry 存储工具 Bean 名称，通过 ApplicationContext 获取 Spring 代理
public List<Object> getAllBuiltinToolObjects() {
    return registeredToolBeans.entrySet().stream()
        .map(entry -> applicationContext.getBean(entry.getValue()))
        .toList();
}
```
- 优点：工具列表动态管理，新增 Tool 只需注册
- 缺点：返回的是 Spring CGLIB 代理对象，需实测验证 LangChain4j `@Tool` 注解是否能被代理正确透传

### 新建文件

**1. `BuiltinToolProvider.java`** — `com.nexus.ai.mcp`（接口）
```java
public interface BuiltinToolProvider {
    String getToolName();       // snake_case 唯一标识
    String getDisplayName();    // UI 显示名
    String getDescription();    // AI 理解的描述
}
```

**2. `BuiltinToolRegistry.java`** — `com.nexus.ai.mcp`（注册中心）
- Spring DI 自动发现所有 `BuiltinToolProvider` 实现
- `ConcurrentHashMap<String, BuiltinToolProvider>` 存储工具元数据（**不存储 Class，避免 newInstance NPE**）
- `getAllBuiltinTools()` 返回工具定义列表（用于数据库同步）
- `getToolProvider(String toolName)` 按名称获取元数据

**3. `ErpBuiltinToolProvider.java`** — `com.nexus.ai.mcp.registry`（7 个适配器）
- 为 7 个 Tool 分别创建 `BuiltinToolProvider` 实现（内部类或独立类均可）
- 每个适配器只提供 toolName、displayName、description 元数据
- 示例：
```java
@Component
public class ErpInventoryToolProvider implements BuiltinToolProvider {
    @Override public String getToolName() { return "erp_inventory"; }
    @Override public String getDisplayName() { return "库存查询工具"; }
    @Override public String getDescription() { return "查询库存预警、出库趋势、库存金额等"; }
}
```

**4. `AiToolEntity.java`** + **`AiToolMapper.java`** — `com.nexus.ai.mcp.domain`
- 对应数据库 `ai_tool` 表（DDL 已在开发计划 Phase 5 中定义，本周提前建表）
- Entity: id, tenantId, toolName, displayName, description, toolType(BUILTIN/MCP/REMOTE), config(JSON), status
- 包含框架标准字段：create_dept, create_by, create_time, update_by, update_time, del_flag, attr1-attr4

**5. `IMcpToolService.java`** + **`McpToolServiceImpl.java`** — `com.nexus.ai.mcp.service`
- 工具 CRUD
- `getAllEnabledTools()` — 查询所有启用的工具
- `syncBuiltinTools()` — 将注册中心的内置工具同步到数据库

**6. `McpToolController.java`** — `com.nexus.ai.mcp.controller`
- GET `/ai/mcp/tool/list` — 工具列表
- POST `/ai/mcp/tool` — 新增工具
- PUT `/ai/mcp/tool` — 修改工具
- DELETE `/ai/mcp/tool/{ids}` — 删除工具
- PUT `/ai/mcp/tool/{id}/status` — 启用/停用

**7. `AiToolInitializer.java`** — `com.nexus.ai.mcp`
- 实现 `ApplicationRunner`，启动时将 BuiltinToolRegistry 中的工具同步到 ai_tool 表
- 只同步新增工具，不覆盖已有状态（参考 ruoyi-ai 的 SystemToolInitializer）

### 修改文件

**`AiServiceFacade.java`** — **不修改工具传递方式**（保持当前 Spring Bean 直接注入）：
```java
// 保持当前方式，不做改动
ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
        erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)
    .build();
```

> **说明**：当前 Spring Bean 直接注入方式在 nexus-erp 中是正确且可靠的做法。BuiltinToolRegistry 仅用于数据库同步和前端 UI 管理，不参与 Agent 构建时的工具传递。后续如需动态工具管理，可在第六周评估方案 B（Spring 容器获取 Bean）的可行性。

### 数据库
- 使用开发计划中已定义的 `ai_tool` 建表 DDL（Phase 5），本周提前建表

### 交付物
- [ ] `BuiltinToolProvider.java` + `BuiltinToolRegistry.java` — 工具注册基础设施（元数据管理）
- [ ] `ErpBuiltinToolProvider.java` — 7 个 ERP 内置工具元数据适配器
- [ ] `AiToolEntity` + `AiToolMapper` + `IMcpToolService` — 工具持久层
- [ ] `McpToolController.java` — 工具管理 API
- [ ] `AiToolInitializer.java` — 启动同步
- [ ] `AiServiceFacade.java` — **不修改**（保持当前 Spring Bean 注入方式）

---

## Task 5（周五）：知识库预置内容 + 前端 MCP 页面 + 测试

### 目标
导入 ERP 业务文档到知识库、搭建前端 MCP 工具管理页面、执行全链路测试。

### 任务 5.1：知识库预置内容准备

**新建文件**：`ruoyi-modules/ruoyi-ai/src/main/resources/knowledge/erp-schema-summary.md`

内容为一份结构化的 ERP 业务知识文档，包含：
- 所有 64 张表的中文说明和关联关系
- 核心业务流程说明（销售→生产→采购→仓储→发货）
- 编码规则说明（订单编号、物料编码、批次号生成规则）
- 状态机说明（订单状态流转、工单状态流转、采购状态流转）
- 常见查询场景及对应 SQL 示例

这份文档通过知识库管理页面手动导入，或在后续实现自动导入接口。

### 任务 5.2：前端 MCP 工具管理页面

**新建文件**：`plus-ui/src/views/ai/mcp/index.vue`

参考 `views/ai/model/index.vue` 的页面结构，实现：
- 工具列表（表格展示：工具名、类型、描述、状态、操作）
- 内置工具标识（BUILTIN 类型不可删除，只能启用/停用）
- 新增 MCP 工具弹窗（type=REMOTE/MCP，填写 configJson）
- 测试连接按钮（调用 `/mcp/tool/{id}/test`）

**新建文件**：`plus-ui/src/api/ai/mcp.ts`

```typescript
export function listTools(params: any) { return request({ url: '/mcp/tool/list', method: 'get', params }) }
export function addTool(data: any) { return request({ url: '/mcp/tool', method: 'post', data }) }
export function updateTool(data: any) { return request({ url: '/mcp/tool', method: 'put', data }) }
export function deleteTool(ids: string) { return request({ url: '/mcp/tool/' + ids, method: 'delete' }) }
export function toggleToolStatus(id: number, status: string) { return request({ url: '/mcp/tool/' + id + '/status', method: 'put', data: { status } }) }
export function testToolConnection(id: number) { return request({ url: '/mcp/tool/' + id + '/test', method: 'post' }) }
```

**修改文件**：`plus-ui/src/router/index.ts` — 在 AI 路由下新增 MCP 工具管理子路由：
```typescript
// 嵌套在 /ai 路由下，确保路径为 /ai/mcp
{
  path: 'ai',
  children: [
    // ... 已有路由
    {
      path: 'mcp',
      component: () => import('@/views/ai/mcp/index.vue'),
      name: 'AiMcp',
      meta: { title: '工具管理', icon: 'SetUp' }
    }
  ]
}
```

### 任务 5.3：全链路集成测试

| 序号 | 测试项 | 验证点 |
|------|--------|--------|
| 1 | TableSchemaManager 启动加载 | 应用启动日志显示"已加载 64 张 ERP 表结构" |
| 2 | QueryAllTablesTool 缓存命中 | Agent 调用时不再执行 INFORMATION_SCHEMA 查询 |
| 3 | Text2SQL 准确率 | 测试 5 个典型查询（库存查询、采购统计、销售排行等），检查 SQL 正确率 |
| 4 | ErpInventoryTool 真实数据 | 查询低库存物料返回真实数据 |
| 5 | ErpPurchaseTool 真实数据 | 查询采购金额统计返回真实数据 |
| 6 | ErpSalesTool 真实数据 | 查询月度销售趋势返回真实数据 |
| 7 | ErpProductionTool 真实数据 | 查询在产工单返回真实数据 |
| 8 | MCP 工具注册 | 应用启动后 ai_tool 表自动写入 7 条内置工具记录 |
| 9 | MCP 工具管理 UI | 前端可正常查看/启用/停用工具 |
| 10 | Agent 使用业务 Tool | 发送"哪些物料需要补货"，Agent 自动调用 ErpInventoryTool |
| 11 | 报表生成 | 发送"本月采购金额按供应商分布"，生成正确的 ECharts 图表 |
| 12 | RAG 预置知识 | 导入文档后，提问"销售订单到发货的流程"能从知识库检索到答案 |

### 交付物
- [ ] `erp-schema-summary.md` — ERP 业务知识文档
- [ ] `views/ai/mcp/index.vue` — MCP 工具管理页面
- [ ] `api/ai/mcp.ts` — MCP API 接口
- [ ] `router/index.ts` — 新增路由
- [ ] 集成测试报告

---

## 文件变更总览

### 后端新增文件（~13 个）

| 文件 | 模块 | 说明 |
|------|------|------|
| `ErpTableSchemaManager.java` | ruoyi-ai | ERP 表结构缓存管理器（使用 DataSource） |
| `ErpTableSchemaInitializer.java` | ruoyi-ai | 启动初始化触发器 |
| `ErpToolHelper.java` | ruoyi-ai | 公共工具基类（消除重复代码） |
| `BuiltinToolProvider.java` | ruoyi-ai | MCP 内置工具接口 |
| `BuiltinToolRegistry.java` | ruoyi-ai | 工具注册中心（元数据管理） |
| `ErpBuiltinToolProvider.java` | ruoyi-ai | 7 个 ERP 工具元数据适配器 |
| `AiToolEntity.java` | ruoyi-ai | AI 工具实体 |
| `AiToolMapper.java` | ruoyi-ai | AI 工具 Mapper |
| `IMcpToolService.java` | ruoyi-ai | MCP 工具服务接口 |
| `McpToolServiceImpl.java` | ruoyi-ai | MCP 工具服务实现 |
| `McpToolController.java` | ruoyi-ai | MCP 工具管理 API |
| `AiToolInitializer.java` | ruoyi-ai | 启动同步工具到数据库 |
| `erp-schema-summary.md` | resources | ERP 业务知识文档 |

### 后端修改文件（9 个）

| 文件 | 修改内容 |
|------|----------|
| `ErpQueryAgent.java` | SystemMessage 嵌入核心表 Schema 摘要（分级策略） |
| `ErpReportAgent.java` | SystemMessage 增加数据关联说明 |
| `ErpQueryService.java` | 新增 `executeParameterizedQuery()` 参数化查询方法 |
| `QueryAllTablesTool.java` | 改为从 TableSchemaManager 缓存获取 |
| `QueryTableSchemaTool.java` | 优先从缓存获取 DDL |
| `ErpInventoryTool.java` | 继承基类 + 新增 1 个方法 + 参数化 |
| `ErpPurchaseTool.java` | 继承基类 + 新增 1 个方法 + 参数化 |
| `ErpSalesTool.java` | 继承基类 + 新增 1 个方法 + 参数化 |
| `ErpProductionTool.java` | 继承基类 + 新增 1 个方法 + 参数化 |

### 前端新增文件（3 个）

| 文件 | 说明 |
|------|------|
| `views/ai/mcp/index.vue` | MCP 工具管理页面 |
| `api/ai/mcp.ts` | MCP API 接口 |
| `erp-schema-summary.md` | 预置知识库文档（可手动导入） |

### 前端修改文件（1 个）

| 文件 | 修改内容 |
|------|----------|
| `router/index.ts` | 在 AI 路由下新增 MCP 工具管理子路由（/ai/mcp） |

---

## 风险与应对

| 风险 | 应对措施 |
|------|----------|
| **TableSchemaManager 启动慢** | 使用异步加载（`@Async` + `aiExecutor`），不阻塞应用启动；首次 Agent 调用时兜底同步加载 |
| **SystemMessage 过长导致 Token 消耗** | 采用分级策略：核心表（bd_/so_/pu_）摘要嵌入 SystemMessage（约 1500 Token），非核心表通过 Tool 动态查询，总预算控制在 2000 Token 以内 |
| **业务 Tool SQL 执行失败** | 所有 SQL 方法 try-catch 包裹，返回友好错误信息；SQL 超时统一 5 秒 |
| **业务 Tool 重复代码** | 提取 `ErpToolHelper` 基类，消除 5 个 Tool 中重复的 `injectTenantCondition()` 和 `formatResults()`（约 250 行） |
| **SQL 注入风险** | 将 `String.format()` 拼接 SQL 改为参数化查询（`?` 占位符 + `Object[]` 参数），通过 `ErpQueryService.executeParameterizedQuery()` 执行 |
| **MCP 工具实例化 NPE** | BuiltinToolRegistry 仅存储工具元数据，不通过 `newInstance()` 创建实例；Agent 构建保持当前 Spring Bean 直接注入方式 |
| **MCP 工具包路径冲突** | 开发前检查 `com.nexus.ai.tool` 包下是否已存在相关文件，避免重复创建或包路径不一致 |
| **ai_tool 表提前建表** | 本周建表不影响其他模块，DDL 已在计划中定义 |
| **前端 MCP 页面兼容性** | 使用 Element Plus 原生组件，不依赖 vue-element-plus-x |
| **前端路由路径错误** | MCP 路由嵌套在 `/ai` 路由下，确保路径为 `/ai/mcp` 而非独立的 `/mcp` |

---

## 第六周展望

| 任务 | 说明 |
|------|------|
| MCP 外部工具接入 | 支持 REMOTE/HTTP 方式的 MCP 服务器连接 |
| Text2SQL 准确率优化 | 根据测试结果调整 Schema 摘要和 SystemMessage |
| Agent 流式推送升级 | 跟踪 langchain4j-agentic 的 SupervisorStreamListener 进展 |
| 知识库自动导入接口 | 实现 API 自动导入预置文档 |
| 对话质量评估 | 增加用户反馈（👍/👎）机制 |
| Weaviate/Qdrant 向量存储 | 扩展 VectorStoreService 策略模式 |

---

## 第五周代码审查修复记录（2026-06-13）

### 审查发现

代码审查发现 **13 个问题**（7 Critical + 4 Major + 2 Minor），其中 7 个 Critical 问题全部集中在**业务 Tool 的 SQL 列名与实际数据库 Schema 不匹配**，会导致所有 4 个业务 Tool 的 16 个查询方法在运行时直接报错。

**根本原因**：编写 SQL 时使用了"想当然"的简写列名（如 `code`、`name`、`quantity`），而非参照实际数据库 DDL 中的完整列名（如 `material_code`、`material_name`、`stock_quantity`）。

### 修复清单

| No. | 级别 | 问题 | 修复内容 | 涉及文件 |
|-----|------|------|----------|----------|
| 1 | Critical | ErpInventoryTool 列名不匹配 | `m.code`→`m.material_code`、`m.name`→`m.material_name`、`s.quantity`→`s.stock_quantity`、`s.avg_cost`→`s.unit_cost`、`m.id`→`m.material_id` | ErpInventoryTool.java |
| 2 | Critical | 出库趋势 tx_type/列名不匹配 | `tx.quantity`→`tx.tx_quantity`、`tx.unit_price`→`tx.unit_cost`、`tx.tx_type='OUT'`→`tx.tx_flag=-1` | ErpInventoryTool.java |
| 3 | Critical | ErpSalesTool FK/列名不匹配 | `l.header_id`→`l.so_id`、`l.quantity`→`l.ordered_qty`、`l.amount`→`l.line_amount`、`h.id`→`h.so_id`、`c.name`→`c.client_name` | ErpSalesTool.java |
| 4 | Critical | 退货表名不存在 | `so_sales_return`→`so_return`、`r.id`→`r.return_id` | ErpSalesTool.java |
| 5 | Critical | 供应商评估列名不存在 | `e.return_orders`→`e.return_qty`、`e.composite_score`→`e.total_score` | ErpPurchaseTool.java |
| 6 | Critical | 生产工单列名全面不匹配 | `wo.status`→`wo.wo_status`、`wo.order_no`→`wo.wo_no`、`wo.quantity`→`wo.planned_qty`、`wo.completed_quantity`→`wo.completed_qty`、`wo.planned_start_date`→`wo.plan_start_date`、`wo.planned_end_date`→`wo.plan_end_date` | ErpProductionTool.java |
| 7 | Critical | 产能利用率引用不存在的表 | `sch_production_schedule` 表不存在，改用 `prd_work_order` + `bd_workstation` 重写查询 | ErpProductionTool.java |
| 8 | Major | 采购在途订单状态值错误 | `o.status='CONFIRMED'`→`o.status='APPROVED'`、`o.expected_date`→`o.expected_arrival_date` | ErpPurchaseTool.java |
| 9 | Major | McpToolController 缺少权限注解 | 所有端点添加 `@SaCheckPermission("ai:mcp:xxx")` | McpToolController.java |
| 10 | Major | McpToolController 未使用 R\<T\> 响应包装 | 返回类型改为 `R<List<AiTool>>`、`R<Void>` 等 | McpToolController.java |
| 11 | Major | SystemMessage 缺少 APPROVING 状态 | 采购订单状态流转补充 APPROVING，列名修正为 `expected_arrival_date`、`order_quantity`、`received_quantity` | ErpQueryAgent.java |
| 12 | Minor | injectTenantCondition 字符串拼接 | 添加注释说明 tenantId 为服务端 Long 值无注入风险 | ErpToolHelper.java |
| 13 | Minor | syncBuiltinTools 潜在 NPE | `provider.getDescription().equals()`→`Objects.equals()` | McpToolServiceImpl.java |

### 经验教训

1. **SQL 列名必须参照实际 DDL**：编写业务 Tool SQL 时，必须以 `script/sql/ry-erp.sql` 中的 `CREATE TABLE` 语句为准，不能使用"想当然"的简写列名
2. **表名必须与数据库一致**：实际表名可能与设计文档中的命名不同（如 `so_return` vs `so_return_order`），需以 DDL 为准
3. **Controller 必须添加权限注解**：所有管理类 API 必须添加 `@SaCheckPermission` 注解，防止越权访问
4. **Controller 必须使用标准响应包装**：返回类型统一使用 `R<T>`，与项目其他 Controller 保持一致
5. **SystemMessage 中的列名也需与实际一致**：AI Agent 的 SystemMessage 中嵌入的 Schema 摘要必须反映真实列名，否则 LLM 生成的 SQL 也会出错

---

## 第五周二次审查修复记录（2026-06-13）

### 审查发现

二次审查发现首次修复**未完全生效**，仍有 6 个 Critical 问题（ErpInventoryTool 3 个方法 + ErpSalesTool 3 个方法的 SQL 列名仍为错误值），以及 2 个 Major + 3 个 Minor 问题。

**根本原因**：首次审查修复时，仅修复了 ErpPurchaseTool、ErpProductionTool 和各 Tool 的新增方法，遗漏了 ErpInventoryTool 的前 3 个已有方法和 ErpSalesTool 的前 3 个已有方法。

### 修复清单

| No. | 级别 | 问题 | 修复内容 | 涉及文件 |
|-----|------|------|----------|----------|
| 1 | Critical | queryLowStockMaterials 列名未修复 | `m.code`→`m.material_code`、`m.name`→`m.material_name`、`s.quantity`→`s.stock_quantity`、`s.material_id=m.id`→`s.material_id=m.material_id` | ErpInventoryTool.java |
| 2 | Critical | queryMaterialOutboundTrend 列名未修复 | `tx.quantity`→`tx.tx_quantity`、`tx.unit_price`→`tx.unit_cost`、`tx.tx_type='OUT'`→`tx.tx_flag=-1`、`m.code`→`m.material_code`、`tx.material_id=m.id`→`tx.material_id=m.material_id` | ErpInventoryTool.java |
| 3 | Critical | queryInventoryValue 列名未修复 | `w.name`→`w.warehouse_name`、`s.quantity`→`s.stock_quantity`、`s.avg_cost`→`s.unit_cost`、`s.warehouse_id=w.id`→`s.warehouse_id=w.warehouse_id` | ErpInventoryTool.java |
| 4 | Critical | queryMonthlySalesTrend 列名未修复 | `l.quantity`→`l.ordered_qty`、`l.amount`→`l.line_amount`、`l.header_id`→`l.so_id`、`h.id`→`h.so_id`、`m.code`→`m.material_code`、`l.material_id=m.id`→`l.material_id=m.material_id` | ErpSalesTool.java |
| 5 | Critical | queryRecentReturns 表名/列名未修复 | `so_sales_return`→`so_return`、`r.id`→`r.return_id` | ErpSalesTool.java |
| 6 | Critical | querySalesOrderFulfillmentRate 列名未修复 | `h.order_no`→`h.so_no`、`c.name`→`c.client_name`、`h.id`→`h.so_id`、`l.header_id`→`l.so_id`、`h.client_id=c.id`→`h.client_id=c.client_id` | ErpSalesTool.java |
| 7 | Major | SystemMessage Schema 摘要列名错误 | `bd_vendor.vendor_type`→`category`、`contact_name`→`contact_person`；`bd_client` 去掉不存在的 `status`，`contact_name`→`contact1`；`bd_bom.bom_code`→删除、`material_id`→`parent_material_id`、`version`→`bom_version`；`bd_bom_detail.material_id`→`child_material_id`、`quantity`→`quantity_per_parent`、删除 `unit_id`；`so_sales_order_line` 删除不存在的 `line_status`；新增 `so_return` 和 `pu_vendor_evaluation` 表摘要 | ErpQueryAgent.java |
| 8 | Major | ErpReportAgent 缺少关联关系 | 增加 ERP 核心表关联关系说明（销售→采购→库存→生产→BOM），包含正确的 FK 列名 | ErpReportAgent.java |
| 9 | Minor | injectTenantCondition 对 CTE 查询处理不当 | 重写方法，对 WITH RECURSIVE 开头的 SQL 先跳过 CTE 定义部分，再在主查询中注入租户条件 | ErpToolHelper.java |
| 10 | Minor | QueryAllTablesTool @Tool 描述为英文 | 改为中文"查询数据库中所有可用的 ERP 业务表" | QueryAllTablesTool.java |
| 11 | Minor | QueryTableSchemaTool @Tool/@P 描述为英文 | 改为中文"查询指定 ERP 表的建表语句（DDL）和字段说明"、@P("表名") | QueryTableSchemaTool.java |

### 经验教训（补充）

6. **修复后必须验证**：首次修复后未逐文件验证修复是否生效，导致部分文件仍保留错误代码。修复后应重新读取文件确认变更已写入
7. **CTE 查询需特殊处理**：WITH RECURSIVE 开头的 SQL，租户条件不能注入到 CTE 内部的 FROM，必须注入到主查询的 WHERE 中
8. **@Tool 描述语言应统一**：所有 Tool 的 `@Tool` 和 `@P` 注解描述应统一使用中文，与 SystemMessage 中的工具策略说明保持一致
