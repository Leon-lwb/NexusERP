# NexusERP AI 开发计划 — 第九周（Schema Token 优化 + Prompt 模板预览 + 知识库自动导入 + 前端补齐）

> **前置状态**：第八周已完成工具差异化注入（7 个 Provider 覆写 + Registry 过滤 + Facade 差异化）、业务 Tool 增强（8 个新分析方法）、SQL Few-Shot 示例库（ai_sql_example 表 + SqlExampleService + Prompt 注入）、对话导出（Markdown 下载）。
>
> **本周目标**：Schema 摘要 Token 优化（按表优先级动态裁剪）、Prompt 模板预览接口、知识库自动导入（URL 批量导入 + 目录扫描）、前端页面补齐（SQL 示例管理 + 对话导出按钮 + 路由）。
>
> **Phase 2 进度**：第 7-8 周已完成 5.1（Text2SQL 准确率优化）、5.2（业务 Tool 增强）、5.3（Prompt 模板管理）、5.4（对话导出）。本周完成 5.1.1（Schema Token 优化）、5.3 补充（预览接口）、5.5（知识库自动导入），并补齐第七/八周遗留的前端页面。

---

## 现状与差距分析

### 已完成的核心能力

- ✅ Text2SQL 准确率优化（Schema 增强 + SQL Few-Shot + EXPLAIN 校验 + 工具差异化注入）
- ✅ 业务 Tool 查询增强（4 个 Tool 共 18 个方法）
- ✅ Prompt 模板管理（CRUD + 渲染 + 缓存联动 + SQL 示例注入）
- ✅ 对话导出（Markdown 下载）

### 本周需解决的差距

| 差距 | 影响 | 优先级 |
|------|------|--------|
| Schema 摘要全量注入，Token 浪费严重 | `getFullSchemaSummary()` 返回全部 6 类前缀 30+ 张表的完整摘要，占用 3000-5000 Token，大部分查询只涉及 2-3 张表 | P0 |
| Prompt 模板无法预览渲染结果 | 管理员编辑模板后无法看到实际渲染效果，只能通过 Agent 对话间接验证 | P1 |
| 知识库只能手动上传文档 | 管理员需要逐个上传 ERP 操作文档，无法批量导入 | P1 |
| 第七/八周前端页面未实现 | SQL 示例管理页面、对话导出按钮、SQL 示例路由均未创建 | P1 |

---

## Task 1：Schema 摘要 Token 优化（P0）

### 1.1 问题分析

当前 `ErpTableSchemaManager.getFullSchemaSummary()` 返回所有表的摘要，但实际查询通常只涉及 2-3 张表。例如：
- "查询库存低于安全库存的物料" → 只需要 `wm_material_stock` + `bd_material`
- "本月销售总额" → 只需要 `so_sales_order_header`

全量注入导致：
1. 浪费 3000-5000 Token（约占总上下文 10-15%）
2. 表越多 LLM 越容易混淆，反而降低 SQL 准确率

### 1.2 解决方案：按优先级动态裁剪

#### 1.2.1 表优先级定义

```java
/**
 * 表优先级配置
 * <p>CORE=核心表（始终包含）、IMPORTANT=重要表（空间允许时包含）、AUXILIARY=辅助表（按需包含）</p>
 */
public enum TablePriority {
    CORE,       // 核心表：高频查询必用，始终包含
    IMPORTANT,  // 重要表：常用查询涉及，空间允许时包含
    AUXILIARY   // 辅助表：低频查询涉及，按需包含
}
```

| 优先级 | 表 | 说明 |
|--------|-----|------|
| CORE | bd_material, bd_material_category, bd_client, bd_vendor | 基础主数据，几乎所有查询都 JOIN |
| CORE | wm_material_stock, wm_warehouse | 库存核心表 |
| CORE | so_sales_order_header, so_sales_order_line | 销售核心表 |
| IMPORTANT | pu_purchase_order, pu_purchase_order_line, pu_vendor_evaluation | 采购核心表 |
| IMPORTANT | prd_work_order, bd_workstation, bd_workshop | 生产核心表 |
| IMPORTANT | wm_material_stock_tx | 库存事务表 |
| AUXILIARY | so_return, so_return_line | 销售退货 |
| AUXILIARY | bd_bom, bd_bom_detail | BOM |
| AUXILIARY | bd_unit_of_measure | 单位 |
| AUXILIARY | 其他表 | 低频查询 |

#### 1.2.2 ErpTableSchemaManager 新增方法

```java
/**
 * 获取按优先级裁剪的 Schema 摘要
 * <p>
 * 优先级策略：
 * 1. 始终包含 CORE 级别表
 * 2. 空间允许时包含 IMPORTANT 级别表
 * 3. AUXILIARY 级别表仅列出表名和字段数，不展开字段详情
 * </p>
 *
 * @param maxTokens 最大 Token 预算（估算：1 个中文字 ≈ 2 Token）
 * @return 裁剪后的 Schema 摘要
 */
public String getOptimizedSchemaSummary(int maxTokens) { ... }

/**
 * 获取表优先级
 *
 * @param tableName 表名
 * @return 优先级
 */
public TablePriority getTablePriority(String tableName) { ... }
```

#### 1.2.3 PromptTemplateService 集成

将 `renderQueryAgentContext()` 和 `renderReportAgentContext()` 中的 `getFullSchemaSummary()` 替换为 `getOptimizedSchemaSummary(4000)`（按字符数控制，约 2000 中文字）。

### 1.3 实现细节

- `TablePriority` 枚举定义在 `ErpTableSchemaManager` 内部
- 表优先级映射使用 `Map<String, TablePriority>` 静态初始化
- `getOptimizedSchemaSummary()` 先输出 CORE 表完整摘要，再输出 IMPORTANT 表摘要，AUXILIARY 表只输出一行（表名 + 字段数 + 用途）
- 按字符数控制总量（默认 4000 字符），比 Token 估算更可靠

---

## Task 2：Prompt 模板预览接口（P1）

### 2.1 后端实现

在 `AiPromptTemplateController` 中新增预览端点：

```java
/**
 * 预览 Prompt 模板渲染结果
 * <p>使用当前系统数据渲染模板，返回实际注入 Agent 的文本内容</p>
 *
 * @param templateKey 模板标识
 * @return 渲染后的完整文本
 */
@SaCheckPermission("ai:prompt:query")
@GetMapping("/preview/{templateKey}")
public R<String> preview(@PathVariable String templateKey) {
    String rendered;
    switch (templateKey) {
        case "erp_query_agent" -> rendered = promptTemplateService.renderQueryAgentContext();
        case "erp_report_agent" -> rendered = promptTemplateService.renderReportAgentContext();
        case "supervisor" -> rendered = promptTemplateService.renderSupervisorContext();
        default -> rendered = promptTemplateService.render(templateKey, Map.of());
    }
    return R.ok(rendered);
}
```

### 2.2 前端实现

在 Prompt 模板管理页面增加"预览"按钮，点击后弹窗显示渲染结果。

---

## Task 3：知识库自动导入（P1）

### 3.1 后端实现

#### 3.1.1 AiKnowledgeController 新增批量导入端点

```java
/**
 * 批量导入 URL 文档到知识库
 * <p>支持从指定 URL 列表批量下载文档并导入</p>
 *
 * @param knowledgeId 知识库ID
 * @param urls        文档 URL 列表
 * @return 导入结果
 */
@SaCheckPermission("ai:knowledge:add")
@PostMapping("/document/importUrls/{knowledgeId}")
public R<Map<String, Object>> importUrls(
    @PathVariable Long knowledgeId,
    @RequestBody List<String> urls) { ... }
```

> **注意**：移除了 `importDirectory` 端点。本地目录扫描在容器化部署中不可用，且存在任意文件读取安全风险。

#### 3.1.2 KnowledgeImportService

```java
/**
 * 知识库文档导入服务
 * <p>支持 URL 批量导入，复用已有的文档处理流程</p>
 */
@Component
public class KnowledgeImportService {

    /**
     * 从 URL 列表批量导入文档
     * <p>
     * 流程：下载 URL → 上传到 OSS → 创建文档记录 → 异步解析→分段→向量化
     * </p>
     *
     * @param knowledgeId 知识库ID
     * @param urls        文档 URL 列表
     * @return 导入结果（成功数/失败数/失败详情）
     */
    public Map<String, Object> importFromUrls(Long knowledgeId, List<String> urls) {
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (String url : urls) {
            try {
                // 1. 从 URL 下载文件到临时目录
                // 2. 构造 MultipartFile（MockMultipartFile）
                // 3. 调用已有的 knowledgeProcessService.uploadAndProcess()
                // 4. 清理临时文件
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(url + ": " + e.getMessage());
            }
        }
        return Map.of("success", success, "failed", failed, "errors", errors);
    }
}
```

### 3.2 前端实现

在知识库管理页面增加"批量导入"按钮，支持 URL 列表输入（多行文本框）。

---

## Task 4：前端页面补齐（P1）

### 4.1 SQL 示例管理前端

> 第八周后端已完成，前端未创建。

#### 4.1.1 API 文件

**文件**：`plus-ui/src/api/ai/sqlExample.ts`

```typescript
// 6 个 API 函数：listSqlExample, listAllSqlExample, getSqlExample,
//                addSqlExample, updateSqlExample, deleteSqlExample
```

#### 4.1.2 页面

**文件**：`plus-ui/src/views/ai/sqlExample/index.vue`

- 列表：表格展示（问题、SQL 模板、分类、状态、排序号）
- 编辑对话框：问题输入、SQL 模板编辑（代码编辑器）、分类选择、排序号
- 搜索：按分类筛选、按问题模糊搜索

#### 4.1.3 路由

在 `router/index.ts` 中新增 `/ai/sqlExample` 路由。

### 4.2 对话导出前端

> 第八周后端已完成，前端未创建。

在 `assistant/index.vue` 会话列表中增加"导出"按钮。

#### 4.2.1 API

在 `chat.ts` 中新增 `exportSession(sessionId)` 函数。

#### 4.2.2 导出按钮

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

---

## 文件变更清单

### 新增文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `KnowledgeImportService.java` | ruoyi-ai/service/knowledge | 知识库导入服务 |
| `sqlExample.ts` | plus-ui/src/api/ai | SQL 示例前端 API |
| `index.vue` | plus-ui/src/views/ai/sqlExample | SQL 示例管理页面 |

### 修改文件

| 文件 | 变更内容 |
|------|----------|
| `ErpTableSchemaManager.java` | 新增 `TablePriority` 枚举、`getTablePriority()`、`getOptimizedSchemaSummary()` |
| `PromptTemplateService.java` | `renderQueryAgentContext()` 和 `renderReportAgentContext()` 改用 `getOptimizedSchemaSummary()` |
| `AiPromptTemplateController.java` | 新增 `preview()` 预览端点 |
| `AiKnowledgeController.java` | 新增 `importUrls()` 和 `importDirectory()` 端点 |
| `chat.ts` | 新增 `exportSession()` API |
| `assistant/index.vue` | 会话列表增加"导出"按钮 |
| `router/index.ts` | 新增 `/ai/sqlExample` 路由 |

### SQL 变更

| 文件 | 说明 |
|------|------|
| `ai_week9.sql`（独立脚本） | 知识库导入权限按钮 + SQL 示例管理前端路由（如需） |

---

## 依赖关系与执行顺序

```
Task 1 (Schema Token 优化) ←── P0，后端独立
  ├── 1.1 TablePriority 枚举 + 优先级映射
  ├── 1.2 getOptimizedSchemaSummary() 方法
  └── 1.3 PromptTemplateService 集成

Task 2 (Prompt 预览接口) ←── P1，后端独立
  └── 2.1 Controller 新增 preview 端点

Task 3 (知识库自动导入) ←── P1，后端独立
  ├── 3.1 KnowledgeImportService
  └── 3.2 Controller 新增端点

Task 4 (前端补齐) ←── P1，依赖 Task 1-3 后端完成
  ├── 4.1 SQL 示例管理页面
  ├── 4.2 对话导出按钮
  └── 4.3 路由配置
```

**建议执行顺序**：
1. Day 1：Task 1（Schema Token 优化）+ Task 2（Prompt 预览）
2. Day 2-3：Task 3（知识库自动导入）
3. Day 4-5：Task 4（前端补齐）+ 整体测试

---

## 验收标准

| Task | 验收标准 |
|------|----------|
| Task 1 | 1) `getOptimizedSchemaSummary(2000)` 返回的摘要 Token 数显著少于 `getFullSchemaSummary()`<br>2) CORE 表始终完整展示，IMPORTANT 表空间允许时展示，AUXILIARY 表只列出表名<br>3) Agent 对话功能不受影响 |
| Task 2 | 1) `GET /ai/prompt/preview/erp_query_agent` 返回渲染后的完整文本<br>2) 前端可预览模板渲染结果 |
| Task 3 | 1) `POST /ai/knowledge/document/importUrls/{id}` 支持批量 URL 导入<br>2) `POST /ai/knowledge/document/importDirectory/{id}` 支持目录扫描导入<br>3) 导入结果返回成功/失败统计 |
| Task 4 | 1) SQL 示例管理页面可 CRUD<br>2) 对话导出按钮可下载 Markdown 文件<br>3) 路由正确跳转 |

---

## 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| Schema 裁剪过度导致 LLM 生成 SQL 引用不存在的表 | SQL 报错 | CORE 表包含所有常用 JOIN 表，AUXILIARY 表仍列出表名供 LLM 参考 |
| URL 下载文件格式不支持 | 导入失败 | 支持 .md/.txt/.pdf/.docx/.csv/.html，其他格式返回明确错误 |
| URL 下载超时或不可达 | 导入失败 | 设置 30 秒连接超时 + 60 秒读取超时，失败时返回明确错误 |
| 前端 SQL 编辑器体验差 | 管理员编辑 SQL 不方便 | 使用 textarea + 等宽字体，不做复杂代码编辑器 |

---

## 第十周展望

1. **多模型 A/B 测试**：新增 ai_ab_test 表，支持同一问题对比不同模型回答质量
2. **对话质量自动评估**：基于 LLM-as-Judge 自动评估 Agent 回答质量
3. **Agent 执行链路追踪**：记录完整的 Agent 调用链（Supervisor → SubAgent → Tool → SQL），便于排查问题
4. **前端 SQL 示例与 Prompt 模板预览增强**：支持实时渲染预览、语法高亮
