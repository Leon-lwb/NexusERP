# NexusERP AI 开发计划 — 第八周（业务 Tool 增强 + SQL Few-Shot 示例库 + 工具差异化注入 + 对话导出）

> **前置状态**：第七周已完成 Prompt 模板管理（CRUD + 渲染 + 缓存联动）、Schema 信息质量提升（外键/枚举/复合主键）、Agent 构建优化（动态上下文注入 + 低 temperature）、SQL 校验与自动修复（EXPLAIN 预校验 + 注释清理增强）。
>
> **第七周遗留**：工具差异化注入链路未打通（getCategories() 接口已定义但实现类未覆写、Registry 无过滤方法、Facade 未调用过滤），需在第八周优先完成。
>
> **本周目标**：完成工具差异化注入（补齐第七周遗留）、业务 Tool 查询增强（4 个 Tool 各新增 2-3 个分析方法）、SQL Few-Shot 示例库（新增表 + 动态注入）、对话导出功能。
>
> **参考项目**：`D:\ruoyi-ai`（ChatServiceFacade Agent 构建、chat_config 配置表、WorkflowUtil.renderTemplate 变量渲染）

---

## 现状与差距分析

### 已完成的核心能力

- ✅ Prompt 模板管理（数据库驱动 + 热更新 + 缓存联动）
- ✅ Schema 信息质量提升（外键/枚举/复合主键 + 结构化摘要）
- ✅ Agent 动态上下文注入（Schema + 工具列表 + 日期 → Supervisor → SubAgent）
- ✅ SQL 校验与自动修复（EXPLAIN 预校验 + 注释清理 + LIMIT 安全）
- ✅ Agent 低 temperature（0.1）+ 历史上下文截断优化

### 本周需解决的差距

| 差距 | 影响 | 优先级 |
|------|------|--------|
| 工具差异化注入链路未打通 | QueryAgent 和 ReportAgent 注入完全相同的工具集，ReportAgent 不需要 ExecuteSqlQueryTool，浪费 Token 且可能误导 LLM | P0（第七周遗留） |
| 4 个业务 Tool 只有基础查询方法 | 缺少分析类方法（趋势、排行、预测等），Agent 无法回答深度分析问题 | P0 |
| 无 SQL Few-Shot 示例库 | LLM 生成 SQL 时没有参考示例，复杂查询（多表 JOIN、子查询）准确率低 | P0 |
| 无对话导出功能 | 用户无法保存/分享 AI 对话记录 | P1 |
| Prompt 模板预览接口未实现 | 前端无法预览模板渲染结果 | P2 |

---

## Task 1：工具差异化注入（P0，补齐第七周遗留）

### 1.1 实现类覆写 getCategories()

为 7 个 BuiltinToolProvider 实现类声明正确的类别标签：

| 实现类 | getCategories() 返回值 | 说明 |
|--------|----------------------|------|
| QueryAllTablesToolProvider | `"QUERY"` | 数据查询类 |
| QueryTableSchemaToolProvider | `"QUERY"` | 数据查询类 |
| ExecuteSqlQueryToolProvider | `"QUERY"` | 数据查询类（报表 Agent 不需要直接执行 SQL） |
| ErpSalesToolProvider | `"ALL"` | 通用（查询和报表都可能用到） |
| ErpPurchaseToolProvider | `"ALL"` | 通用 |
| ErpProductionToolProvider | `"ALL"` | 通用 |
| ErpInventoryToolProvider | `"ALL"` | 通用 |

### 1.2 BuiltinToolRegistry 新增按类别过滤方法

```java
/**
 * 获取指定类别的已启用工具对象列表
 *
 * @param categories 类别标签（逗号分隔，如 "QUERY,ALL"）
 * @return 匹配的工具对象列表
 */
public List<Object> getEnabledBuiltinToolObjectsByCategories(String categories) {
    Set<String> categorySet = Arrays.stream(categories.split(","))
        .map(String::trim)
        .collect(Collectors.toSet());
    return getAllProviders().stream()
        .filter(p -> p.isEnabled())
        .filter(p -> categorySet.contains(p.getCategories()))
        .map(p -> applicationContext.getBean(p.getToolClass()))
        .collect(Collectors.toList());
}
```

### 1.3 ToolProviderFactory 新增按类别过滤方法

```java
/**
 * 获取指定类别的已启用内置工具对象列表
 *
 * @param categories 类别标签（逗号分隔）
 * @return 匹配的工具对象列表
 */
public List<Object> getEnabledBuiltinToolObjectsByCategories(String categories) {
    return builtinToolRegistry.getEnabledBuiltinToolObjectsByCategories(categories);
}
```

### 1.4 AiServiceFacade 差异化注入

```java
// QueryAgent：需要 QUERY + ALL 类别工具
List<Object> queryTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("QUERY,ALL");

// ReportAgent：只需要 ALL 类别工具（不需要 ExecuteSqlQueryTool 等查询专用工具）
List<Object> reportTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("ALL");

ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(agentChatModel)
    .tools(queryTools.toArray())
    .build();

ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
    .chatModel(agentChatModel)
    .tools(reportTools.toArray())
    .build();
```

---

## Task 2：业务 Tool 查询增强（P0）

> Phase 1 的 4 个业务 Tool 已实现基础查询，本 Task 为每个 Tool 新增 2-3 个分析方法，提升 Agent 的深度分析能力。

### 2.1 ErpInventoryTool 增强

| 新增方法 | @Tool 描述 | SQL 逻辑 |
|----------|-----------|----------|
| `queryMaterialOutboundTrend` | 查询指定物料的出库趋势（按月聚合） | `SELECT DATE_FORMAT(soh.order_date, '%Y-%m') AS month, SUM(sol.quantity) AS outbound_qty FROM so_sales_order_line sol JOIN so_sales_order_header soh ON sol.header_id = soh.id WHERE sol.material_id = ? AND soh.order_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) AND soh.status IN ('CONFIRMED','COMPLETED') GROUP BY month ORDER BY month` |
| `queryInventoryValue` | 查询库存总金额及按仓库分布 | `SELECT w.warehouse_name, SUM(s.quantity * s.avg_cost) AS total_value, SUM(s.quantity) AS total_qty FROM wm_material_stock s JOIN wm_warehouse w ON s.warehouse_id = w.id GROUP BY w.warehouse_name ORDER BY total_value DESC` |
| `queryInventoryAging` | 查询库龄分析（呆滞物料识别） | `SELECT m.material_code, m.material_name, w.warehouse_name, s.quantity, s.avg_cost, DATEDIFF(CURDATE(), s.last_in_date) AS aging_days FROM wm_material_stock s JOIN bd_material m ON s.material_id = m.id JOIN wm_warehouse w ON s.warehouse_id = w.id WHERE DATEDIFF(CURDATE(), s.last_in_date) > ? AND s.quantity > 0 ORDER BY aging_days DESC` |

### 2.2 ErpSalesTool 增强

| 新增方法 | @Tool 描述 | SQL 逻辑 |
|----------|-----------|----------|
| `queryMonthlySalesTrend` | 查询指定物料的月度销售趋势 | `SELECT DATE_FORMAT(soh.order_date, '%Y-%m') AS month, SUM(sol.quantity) AS sales_qty, SUM(sol.amount) AS sales_amount FROM so_sales_order_line sol JOIN so_sales_order_header soh ON sol.header_id = soh.id WHERE sol.material_id = ? AND soh.order_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) AND soh.status IN ('CONFIRMED','COMPLETED') GROUP BY month ORDER BY month` |
| `queryTopClientsBySales` | 查询客户销售金额排行 TOP N | `SELECT c.client_name, COUNT(DISTINCT soh.id) AS order_count, SUM(soh.total_amount) AS total_amount FROM so_sales_order_header soh JOIN bd_client c ON soh.client_id = c.id WHERE soh.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) AND soh.status IN ('CONFIRMED','COMPLETED') GROUP BY c.id, c.client_name ORDER BY total_amount DESC LIMIT ?` |
| `querySalesByCategory` | 查询各品类销售额分布 | `SELECT mc.category_name, SUM(sol.amount) AS sales_amount, SUM(sol.quantity) AS sales_qty FROM so_sales_order_line sol JOIN so_sales_order_header soh ON sol.header_id = soh.id JOIN bd_material m ON sol.material_id = m.id JOIN bd_material_category mc ON m.category_id = mc.id WHERE soh.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) AND soh.status IN ('CONFIRMED','COMPLETED') GROUP BY mc.id, mc.category_name ORDER BY sales_amount DESC` |

### 2.3 ErpPurchaseTool 增强

| 新增方法 | @Tool 描述 | SQL 逻辑 |
|----------|-----------|----------|
| `queryPurchaseAmountByVendor` | 查询按供应商的采购金额分布 | `SELECT v.vendor_name, COUNT(DISTINCT poh.id) AS order_count, SUM(poh.total_amount) AS total_amount FROM pu_purchase_order_header poh JOIN bd_vendor v ON poh.vendor_id = v.id WHERE poh.order_date BETWEEN ? AND ? AND poh.status IN ('CONFIRMED','COMPLETED') GROUP BY v.id, v.vendor_name ORDER BY total_amount DESC` |
| `queryVendorDeliveryPerformance` | 查询供应商交货准时率 | `SELECT v.vendor_name, COUNT(*) AS total_orders, SUM(CASE WHEN poh.delivery_date <= poh.plan_delivery_date THEN 1 ELSE 0 END) AS on_time_orders, ROUND(SUM(CASE WHEN poh.delivery_date <= poh.plan_delivery_date THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS on_time_rate FROM pu_purchase_order_header poh JOIN bd_vendor v ON poh.vendor_id = v.id WHERE poh.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) AND poh.status = 'COMPLETED' GROUP BY v.id, v.vendor_name ORDER BY on_time_rate ASC` |

### 2.4 ErpProductionTool 增强

| 新增方法 | @Tool 描述 | SQL 逻辑 |
|----------|-----------|----------|
| `queryActiveWorkOrders` | 查询当前在产工单及进度 | `SELECT wo.work_order_code, m.material_name, wo.plan_quantity, wo.completed_quantity, ROUND(wo.completed_quantity / wo.plan_quantity * 100, 1) AS completion_rate, wo.status, wo.plan_start_date, wo.plan_end_date FROM prd_work_order wo JOIN bd_material m ON wo.material_id = m.id WHERE wo.status IN ('IN_PROGRESS','STARTED') ORDER BY wo.plan_end_date ASC` |
| `queryWorkstationUtilization` | 查询车间/工位产能利用率 | `SELECT ws.workstation_name, ws.workshop_name, COUNT(wo.id) AS work_order_count, SUM(wo.plan_quantity) AS planned_qty, SUM(wo.completed_quantity) AS completed_qty, ROUND(SUM(wo.completed_quantity) / NULLIF(SUM(wo.plan_quantity), 0) * 100, 1) AS utilization_rate FROM prd_work_order wo JOIN prd_workstation ws ON wo.workstation_id = ws.id WHERE wo.status IN ('IN_PROGRESS','COMPLETED') AND wo.plan_start_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY ws.id, ws.workstation_name, ws.workshop_name ORDER BY utilization_rate DESC` |

### 2.5 实现注意事项

- 所有新增方法均注入 `ErpQueryService`（@DS("erp") 数据源）
- 所有 SQL 自动注入 `tenant_id` 条件（通过 `ErpQueryService` 的租户拦截）
- 所有方法使用 `@Tool` + `@P` 注解，描述使用中文
- 查询结果使用 `ErpToolHelper.formatResult()` 格式化为 Markdown 表格
- 新增方法需要 Javadoc 格式注释

---

## Task 3：SQL Few-Shot 示例库（P0）

### 3.1 数据库表设计

```sql
CREATE TABLE ai_sql_example (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    question        VARCHAR(512) NOT NULL COMMENT '用户问题（自然语言）',
    sql_template    TEXT         NOT NULL COMMENT 'SQL 模板（可含 {{variable}} 占位符）',
    category        VARCHAR(32)  NOT NULL COMMENT '业务分类：INVENTORY/SALES/PURCHASE/PRODUCTION/GENERAL',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    sort_order      INT          DEFAULT 0 COMMENT '排序号',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    PRIMARY KEY (id),
    KEY idx_category (category),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI SQL Few-Shot 示例';
```

### 3.2 后端实现

#### 3.2.1 实体与 Mapper

- **AiSqlExample.java**（ruoyi-common-ai/domain/entity）：实体类
- **AiSqlExampleBo.java**（ruoyi-common-ai/domain/bo）：BO
- **AiSqlExampleVo.java**（ruoyi-common-ai/domain/vo）：VO
- **AiSqlExampleMapper.java**（ruoyi-common-ai/mapper）：Mapper
- **IAiSqlExampleService.java**（ruoyi-common-ai/service）：Service 接口
- **AiSqlExampleServiceImpl.java**（ruoyi-common-ai/service/impl）：Service 实现

#### 3.2.2 SqlExampleService（核心匹配服务）

**文件**：`com.nexus.ai.service.query.SqlExampleService`

```java
/**
 * SQL Few-Shot 示例匹配服务
 * <p>根据用户问题语义匹配最相关的 SQL 示例，注入到 Agent 上下文中</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqlExampleService {

    private final IAiSqlExampleService sqlExampleService;

    /** 示例缓存：category -> List<AiSqlExampleVo> */
    private final ConcurrentHashMap<String, List<AiSqlExampleVo>> exampleCache = new ConcurrentHashMap<>();

    /**
     * 获取指定分类的 SQL 示例
     *
     * @param category 业务分类（INVENTORY/SALES/PURCHASE/PRODUCTION/GENERAL）
     * @return SQL 示例列表
     */
    public List<AiSqlExampleVo> getExamplesByCategory(String category) { ... }

    /**
     * 获取所有已启用的 SQL 示例
     *
     * @return SQL 示例列表
     */
    public List<AiSqlExampleVo> getAllEnabledExamples() { ... }

    /**
     * 格式化 SQL 示例为 Prompt 注入文本
     * <p>将示例格式化为 LLM 可理解的参考格式</p>
     *
     * @param examples SQL 示例列表
     * @return 格式化后的文本
     */
    public String formatExamplesForPrompt(List<AiSqlExampleVo> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是常见的查询示例，供参考：\n\n");
        for (AiSqlExampleVo example : examples) {
            sb.append("问题：").append(example.getQuestion()).append("\n");
            sb.append("SQL：").append(example.getSqlTemplate()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 清除缓存
     */
    public void evictCache() {
        exampleCache.clear();
    }
}
```

#### 3.2.3 AiSqlExampleController

**文件**：`com.nexus.ai.controller.AiSqlExampleController`

```java
@RestController
@RequestMapping("/ai/sql-example")
@RequiredArgsConstructor
public class AiSqlExampleController {

    /** 查询示例列表（分页） */
    @SaCheckPermission("ai:sqlExample:list")
    @GetMapping("/list")
    public TableDataInfo<AiSqlExampleVo> list(AiSqlExampleBo bo, PageQuery pageQuery) { ... }

    /** 查询全部示例（不分页） */
    @SaCheckPermission("ai:sqlExample:list")
    @GetMapping("/listAll")
    public List<AiSqlExampleVo> listAll(AiSqlExampleBo bo) { ... }

    /** 获取示例详情 */
    @SaCheckPermission("ai:sqlExample:query")
    @GetMapping("/{id}")
    public R<AiSqlExampleVo> getInfo(@PathVariable Long id) { ... }

    /** 新增示例 */
    @SaCheckPermission("ai:sqlExample:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiSqlExampleBo bo) { ... }

    /** 修改示例 */
    @SaCheckPermission("ai:sqlExample:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiSqlExampleBo bo) { ... }

    /** 删除示例 */
    @SaCheckPermission("ai:sqlExample:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) { ... }
}
```

### 3.3 PromptTemplateService 集成

在 `renderQueryAgentContext()` 中注入 SQL Few-Shot 示例：

```java
public String renderQueryAgentContext() {
    Map<String, String> variables = new HashMap<>();
    variables.put("schemaSummary", schemaManager.getFullSchemaSummary());
    variables.put("date", LocalDate.now().toString());
    variables.put("availableTools", getAvailableToolsDescription());
    // 新增：注入 SQL Few-Shot 示例
    List<AiSqlExampleVo> examples = sqlExampleService.getAllEnabledExamples();
    variables.put("sqlExamples", sqlExampleService.formatExamplesForPrompt(examples));
    return render("erp_query_agent", variables);
}
```

### 3.4 初始示例数据

在应用启动时（AiSqlExampleInitializer），自动插入默认示例（如果不存在）：

| 分类 | 问题 | SQL 模板 |
|------|------|----------|
| INVENTORY | 查询库存低于安全库存的物料 | `SELECT m.material_code, m.material_name, s.quantity, m.safety_stock FROM wm_material_stock s JOIN bd_material m ON s.material_id = m.id WHERE s.quantity < m.safety_stock` |
| INVENTORY | 查询各仓库的库存总金额 | `SELECT w.warehouse_name, SUM(s.quantity * s.avg_cost) AS total_value FROM wm_material_stock s JOIN wm_warehouse w ON s.warehouse_id = w.id GROUP BY w.warehouse_name` |
| SALES | 查询本月销售总额 | `SELECT SUM(total_amount) AS total_sales FROM so_sales_order_header WHERE DATE_FORMAT(order_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') AND status IN ('CONFIRMED','COMPLETED')` |
| SALES | 查询客户销售排行 TOP10 | `SELECT c.client_name, SUM(soh.total_amount) AS total_amount FROM so_sales_order_header soh JOIN bd_client c ON soh.client_id = c.id WHERE soh.status IN ('CONFIRMED','COMPLETED') GROUP BY c.id ORDER BY total_amount DESC LIMIT 10` |
| PURCHASE | 查询本月采购总额 | `SELECT SUM(total_amount) AS total_purchase FROM pu_purchase_order_header WHERE DATE_FORMAT(order_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') AND status IN ('CONFIRMED','COMPLETED')` |
| PURCHASE | 查询供应商交货准时率 | `SELECT v.vendor_name, ROUND(SUM(CASE WHEN poh.delivery_date <= poh.plan_delivery_date THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS on_time_rate FROM pu_purchase_order_header poh JOIN bd_vendor v ON poh.vendor_id = v.id WHERE poh.status = 'COMPLETED' GROUP BY v.id` |
| PRODUCTION | 查询当前在产工单 | `SELECT wo.work_order_code, m.material_name, wo.plan_quantity, wo.completed_quantity, wo.status FROM prd_work_order wo JOIN bd_material m ON wo.material_id = m.id WHERE wo.status IN ('IN_PROGRESS','STARTED')` |
| GENERAL | 查询指定物料的详细信息 | `SELECT m.material_code, m.material_name, mc.category_name, m.specification, u.unit_name, m.safety_stock FROM bd_material m LEFT JOIN bd_material_category mc ON m.category_id = mc.id LEFT JOIN bd_unit u ON m.unit_id = u.id WHERE m.material_code = ?` |

### 3.5 前端实现

- **api/ai/sqlExample.ts**：SQL 示例 CRUD API
- **views/ai/sqlExample/index.vue**：SQL 示例管理页面（列表 + 编辑对话框 + SQL 预览）
- 菜单权限：`ai:sqlExample:list/query/add/edit/remove`

### 3.6 Prompt 模板更新

更新 `erp_query_agent` 模板，增加 `{{sqlExamples}}` 占位符：

```
...
【SQL 查询示例】
{{sqlExamples}}

请参考以上示例生成 SQL，但不要照搬，需要根据实际问题灵活调整。
...
```

---

## Task 4：对话导出（P1）

### 4.1 后端实现

#### 4.1.1 AiChatController 新增导出端点

```java
/**
 * 导出会话聊天记录为 Markdown 文件
 *
 * @param sessionId 会话ID
 * @return Markdown 文件流
 */
@SaCheckPermission("ai:chat:export")
@GetMapping("/export/{sessionId}")
public void exportSession(@PathVariable Long sessionId, HttpServletResponse response) {
    // 1. 查询会话信息
    AiSessionVo session = aiSessionService.selectById(sessionId);
    // 2. 查询所有消息
    List<AiMessageVo> messages = aiMessageService.selectBySessionId(sessionId);
    // 3. 格式化为 Markdown
    String markdown = formatSessionToMarkdown(session, messages);
    // 4. 写入响应流
    response.setContentType("text/markdown;charset=utf-8");
    response.setHeader("Content-Disposition",
        "attachment;filename=" + URLEncoder.encode(session.getTitle() + ".md", "UTF-8"));
    response.getWriter().write(markdown);
    response.getWriter().flush();
}
```

#### 4.1.2 Markdown 格式化方法

```java
/**
 * 将会话格式化为 Markdown
 *
 * @param session  会话信息
 * @param messages 消息列表
 * @return Markdown 文本
 */
private String formatSessionToMarkdown(AiSessionVo session, List<AiMessageVo> messages) {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(session.getTitle() != null ? session.getTitle() : "AI 对话记录").append("\n\n");
    sb.append("> 导出时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
    sb.append("> 模型：").append(session.getModelName() != null ? session.getModelName() : "未知").append("\n\n");
    sb.append("---\n\n");

    for (AiMessageVo msg : messages) {
        String role = "USER".equals(msg.getRole()) ? "👤 用户" : "🤖 AI";
        sb.append("### ").append(role).append("\n\n");
        sb.append(msg.getContent()).append("\n\n");
        if (msg.getTokenUsage() != null) {
            sb.append("> Token 消耗：").append(msg.getTokenUsage()).append("\n\n");
        }
    }
    return sb.toString();
}
```

### 4.2 前端实现

在 `assistant/index.vue` 会话列表中增加"导出"按钮：

```typescript
const handleExport = async (sessionId: number) => {
  const res = await exportSession(sessionId)
  const blob = new Blob([res], { type: 'text/markdown;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `AI对话_${sessionId}.md`
  link.click()
  window.URL.revokeObjectURL(url)
}
```

### 4.3 菜单权限

新增按钮权限 `ai:chat:export`（挂在"智能对话"菜单下）。

---

## 文件变更清单

### 新增文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `AiSqlExample.java` | ruoyi-common-ai/domain/entity | SQL 示例实体 |
| `AiSqlExampleBo.java` | ruoyi-common-ai/domain/bo | SQL 示例 BO |
| `AiSqlExampleVo.java` | ruoyi-common-ai/domain/vo | SQL 示例 VO |
| `AiSqlExampleMapper.java` | ruoyi-common-ai/mapper | SQL 示例 Mapper |
| `IAiSqlExampleService.java` | ruoyi-common-ai/service | SQL 示例 Service 接口 |
| `AiSqlExampleServiceImpl.java` | ruoyi-common-ai/service/impl | SQL 示例 Service 实现 |
| `SqlExampleService.java` | ruoyi-ai/service/query | SQL 示例匹配服务（核心） |
| `AiSqlExampleInitializer.java` | ruoyi-ai/config | 默认示例初始化 |
| `AiSqlExampleController.java` | ruoyi-ai/controller | SQL 示例管理 API |
| `sqlExample.ts` | plus-ui/src/api/ai | SQL 示例前端 API |
| `index.vue` | plus-ui/src/views/ai/sqlExample | SQL 示例管理页面 |

### 修改文件

| 文件 | 变更内容 |
|------|----------|
| `7 个 ToolProvider 实现类` | 覆写 getCategories() 方法，返回正确的类别标签 |
| `BuiltinToolRegistry.java` | 新增 getEnabledBuiltinToolObjectsByCategories() 方法 |
| `ToolProviderFactory.java` | 新增 getEnabledBuiltinToolObjectsByCategories() 方法 |
| `AiServiceFacade.java` | queryAgent 和 reportAgent 差异化注入工具 |
| `ErpInventoryTool.java` | 新增 3 个分析方法（出库趋势/库存金额/库龄分析） |
| `ErpSalesTool.java` | 新增 3 个分析方法（月度趋势/客户排行/品类分布） |
| `ErpPurchaseTool.java` | 新增 2 个分析方法（供应商金额/交货准时率） |
| `ErpProductionTool.java` | 新增 2 个分析方法（在产工单/产能利用率） |
| `PromptTemplateService.java` | renderQueryAgentContext() 注入 SQL Few-Shot 示例 |
| `AiChatController.java` | 新增 exportSession() 导出端点 |
| `chat.ts` | 新增 exportSession API |
| `assistant/index.vue` | 会话列表增加"导出"按钮 |
| `router/index.ts` | 新增 /ai/sqlExample 路由 |

### SQL 变更

| 文件 | 说明 |
|------|------|
| `ai_week8.sql`（独立脚本） | ai_sql_example 建表 + 默认示例数据 + SQL 示例管理菜单 + 对话导出权限 |

---

## 依赖关系与执行顺序

```
Task 1 (工具差异化注入) ←── P0 遗留，优先完成
  ├── 1.1 实现类覆写 getCategories()
  ├── 1.2 BuiltinToolRegistry 新增过滤方法
  ├── 1.3 ToolProviderFactory 新增过滤方法
  └── 1.4 AiServiceFacade 差异化注入

Task 2 (业务 Tool 增强) ←── 可与 Task 1 并行
  ├── 2.1 ErpInventoryTool 增强（3 个方法）
  ├── 2.2 ErpSalesTool 增强（3 个方法）
  ├── 2.3 ErpPurchaseTool 增强（2 个方法）
  └── 2.4 ErpProductionTool 增强（2 个方法）

Task 3 (SQL Few-Shot 示例库) ←── 依赖 Task 1（工具差异化注入完成后，新增的 Tool 方法才能被正确分类）
  ├── 3.1 数据库表设计
  ├── 3.2 后端实体/Mapper/Service
  ├── 3.3 SqlExampleService（核心匹配服务）
  ├── 3.4 AiSqlExampleController
  ├── 3.5 初始示例数据
  ├── 3.6 PromptTemplateService 集成
  └── 3.7 前端实现

Task 4 (对话导出) ←── 独立，可与 Task 2/3 并行
  ├── 4.1 后端导出端点
  └── 4.2 前端导出按钮
```

**建议执行顺序**：
1. Day 1：Task 1（工具差异化注入）+ Task 4（对话导出）并行
2. Day 2-3：Task 2（业务 Tool 增强，10 个新方法）
3. Day 4：Task 3（SQL Few-Shot 示例库）后端
4. Day 5：Task 3 前端 + 整体测试

---

## 验收标准

| Task | 验收标准 |
|------|----------|
| Task 1 | 1) 7 个 ToolProvider 实现类均覆写 getCategories() 返回正确类别<br>2) QueryAgent 注入 QUERY+ALL 类别工具，ReportAgent 只注入 ALL 类别工具<br>3) BuiltinToolRegistry.getEnabledBuiltinToolObjectsByCategories() 按类别正确过滤 |
| Task 2 | 1) ErpInventoryTool 新增 3 个分析方法，返回格式化 Markdown 表格<br>2) ErpSalesTool 新增 3 个分析方法<br>3) ErpPurchaseTool 新增 2 个分析方法<br>4) ErpProductionTool 新增 2 个分析方法<br>5) 所有新方法有 Javadoc 注释 + @Tool 中文描述 |
| Task 3 | 1) ai_sql_example 表创建成功，默认示例数据初始化<br>2) 前端可管理 SQL 示例（CRUD）<br>3) PromptTemplateService 渲染 QueryAgent 上下文时注入 SQL 示例<br>4) Agent 生成 SQL 时能参考 Few-Shot 示例 |
| Task 4 | 1) 导出端点返回 Markdown 格式文件<br>2) 前端点击"导出"按钮可下载 .md 文件<br>3) 导出内容包含会话标题、时间、模型、完整对话记录 |

---

## 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 业务 Tool 新增方法的 SQL 依赖的表/字段不存在 | 编译通过但运行时报错 | 先通过 ErpTableSchemaManager 确认表结构，SQL 中使用 LEFT JOIN 容错 |
| SQL Few-Shot 示例注入后 Token 超限 | Schema 摘要 + SQL 示例 + 工具列表可能超出上下文窗口 | 限制示例数量（最多 5 个），动态裁剪 |
| 工具差异化注入后 ReportAgent 无法执行 SQL | ReportAgent 没有 ExecuteSqlQueryTool，无法直接查数据 | ReportAgent 仍有 ALL 类别的业务 Tool（ErpSalesTool 等内部直接执行 SQL），且可通过 Supervisor 协调 QueryAgent |
| 导出大量消息时内存溢出 | 单次导出可能包含数百条消息 | 限制单次导出最大消息数（如 500 条），或改为流式写入 |

---

## 第九周展望

1. **知识库自动导入**：实现 API 接口自动导入预置 ERP 操作文档，支持从文件系统目录批量扫描导入
2. **多模型 A/B 测试**：支持同一问题对比不同模型回答质量，新增 ai_ab_test 表
3. **Prompt 模板预览接口**：实现 POST /ai/prompt/preview 预览渲染结果
4. **Schema 摘要 Token 优化**：按表优先级动态裁剪，保证核心表完整、边缘表省略
