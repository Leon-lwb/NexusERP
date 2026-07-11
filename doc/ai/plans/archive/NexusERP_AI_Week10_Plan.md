# NexusERP AI 模块 — 第十周开发计划

> **阶段**：Phase 2 — ERP 深度 AI 优化（第 7-12 周）
> **周次**：第 10 周（Phase 2 第 4 周）
> **日期**：2026-06-16 ~ 2026-06-20
> **前置依赖**：第九周全部 Task 已完成并编译通过

---

## 本周目标

1. 实现 MCP 工具按会话动态选择（用户在对话界面选择工具）
2. 实现多模型 A/B 测试（Phase 2 5.6 节）
3. 实现对话上下文窗口管理（Token 用量监控 + 自动压缩）
4. 前端补齐：MCP 工具选择器 + A/B 测试管理页面

---

## Task 1：MCP 工具按会话动态选择（P0）

> **背景**：当前 MCP 工具只有全局启用/禁用，所有对话加载相同工具集。主流 Agent（Cursor/Claude Code/ChatGPT）均支持按会话选择工具。

### 1.1 数据模型变更

#### ai_session 表新增字段

```sql
ALTER TABLE ai_session ADD COLUMN enabled_tool_ids VARCHAR(1024) DEFAULT NULL COMMENT '会话启用的工具ID列表（逗号分隔，空=全部）';
```

> 设计选择：用 VARCHAR 而非关联表，因为工具选择是会话级轻量配置，不需要复杂关联查询。

### 1.2 后端实现

#### 1.2.1 AiChatRequest 新增字段

```java
/**
 * 本次对话启用的工具ID列表
 * <p>null 表示使用会话默认工具集，空列表表示不使用任何 MCP 工具</p>
 */
private List<Long> enabledToolIds;
```

#### 1.2.2 AiServiceFacade 修改

- `invokeWithAgent()` 方法从 session 或 request 中获取 `enabledToolIds`
- 当 `enabledToolIds` 非空时，调用 `toolProviderFactory.getMcpToolProvidersByIds(ids)` 获取指定工具
- 当 `enabledToolIds` 为空时，使用当前行为（加载全部已启用工具）
- 首次设置后保存到 `ai_session.enabled_tool_ids`，后续对话复用

#### 1.2.3 ToolProviderFactory 新增方法

```java
/**
 * 按 ID 列表获取 MCP 工具 Provider
 * <p>仅返回指定 ID 且状态为 ENABLED 的 MCP 工具</p>
 *
 * @param toolIds 工具 ID 列表
 * @return MCP ToolProvider，无匹配时返回 null
 */
public ToolProvider getMcpToolProvidersByIds(List<Long> toolIds);
```

#### 1.2.4 AiToolController 新增端点

```java
/**
 * 获取当前可用的 MCP 工具列表（供前端选择器使用）
 * <p>返回所有状态为 ENABLED 的非内置工具，含 id/toolName/displayName/description</p>
 */
@SaCheckPermission("ai:tool:list")
@GetMapping("/mcp/available")
public R<List<AiToolVo>> getAvailableMcpTools() { ... }
```

### 1.3 前端实现

- 对话页面新增"工具选择"按钮（位于输入框上方工具栏）
- 点击弹出工具选择抽屉，显示所有可用 MCP 工具（Checkbox 多选）
- 选择后通过 `AiChatRequest.enabledToolIds` 传递到后端
- 会话创建后首次选择时自动保存到 session

---

## Task 2：多模型 A/B 测试（P1）

> **背景**：Phase 2 计划 5.6 节。支持同一问题对比不同模型回答质量，帮助管理员选择最优模型。

### 2.1 数据模型

#### ai_ab_test 表

```sql
CREATE TABLE IF NOT EXISTS ai_ab_test (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    test_name       VARCHAR(128) NOT NULL COMMENT '测试名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '测试描述',
    model_a_id      BIGINT       NOT NULL COMMENT '模型A的ID',
    model_b_id      BIGINT       NOT NULL COMMENT '模型B的ID',
    test_questions  TEXT         NOT NULL COMMENT '测试问题列表（JSON数组）',
    status          VARCHAR(20)  DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/COMPLETED',
    result_summary  TEXT         DEFAULT NULL COMMENT '结果摘要（JSON）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI A/B 测试';

CREATE TABLE IF NOT EXISTS ai_ab_test_result (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    test_id         BIGINT       NOT NULL COMMENT '测试ID',
    question        VARCHAR(512) NOT NULL COMMENT '测试问题',
    model_id        BIGINT       NOT NULL COMMENT '模型ID',
    model_name      VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    response        TEXT         DEFAULT NULL COMMENT '模型回答',
    token_usage     INT          DEFAULT NULL COMMENT 'Token 用量',
    latency_ms      INT          DEFAULT NULL COMMENT '响应延迟（毫秒）',
    score           INT          DEFAULT NULL COMMENT '人工评分（1-5）',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_test_id (test_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI A/B 测试结果';
```

### 2.2 后端实现

#### 2.2.1 Entity/Bo/Vo/Mapper/Service

- `AiAbTest` / `AiAbTestBo` / `AiAbTestVo` — 测试实体
- `AiAbTestResult` / `AiAbTestResultVo` — 测试结果实体
- `AiAbTestMapper` / `AiAbTestResultMapper`
- `IAiAbTestService` / `AiAbTestServiceImpl` — CRUD

#### 2.2.2 AiAbTestService 核心方法

```java
/**
 * 执行 A/B 测试
 * <p>对每个测试问题，分别用模型A和模型B生成回答，记录结果</p>
 *
 * @param testId 测试ID
 * @return 执行结果摘要
 */
public Map<String, Object> executeTest(Long testId);

/**
 * 提交人工评分
 * <p>对某条测试结果打分（1-5分）</p>
 *
 * @param resultId 结果ID
 * @param score    评分（1-5）
 */
public void scoreResult(Long resultId, Integer score);

/**
 * 生成测试报告
 * <p>汇总评分、Token 用量、延迟等指标</p>
 *
 * @param testId 测试ID
 * @return 报告数据
 */
public Map<String, Object> generateReport(Long testId);
```

#### 2.2.3 AiAbTestController

- CRUD 端点 + `POST /ai/ab-test/execute/{id}` 执行测试 + `PUT /ai/ab-test/result/{id}/score` 评分

### 2.3 前端实现

- A/B 测试管理页面（创建测试 + 查看结果 + 评分对比）
- 结果对比视图（左右分栏显示两个模型的回答）

---

## Task 3：对话上下文窗口管理（P1）

> **背景**：当前历史消息全量拼接，长对话时 Token 超限导致 API 报错或回答质量下降。

### 3.1 后端实现

#### 3.1.1 AiServiceFacade 修改

- `buildContextualQuery()` 增加字符数预算（默认 6000 字符）
- 超出预算时，保留最近 5 轮完整对话 + 更早消息只保留摘要
- 摘要生成：截取每条消息前 100 字符 + "..."

#### 3.1.2 Token 用量监控

- `AiMessage` 已有 `token_usage` 字段
- 新增 `AiChatController.getSessionTokenUsage(sessionId)` 端点，返回当前会话 Token 总用量
- 前端显示 Token 用量进度条，接近模型上下文窗口 80% 时警告

### 3.2 前端实现

- 对话页面底部显示 Token 用量（如 "1,234 / 8,192 tokens"）
- 接近阈值时显示黄色警告条

---

## Task 4：前端补齐（P1）

### 4.1 MCP 工具选择器

- 对话页面工具栏新增"🔧 工具"按钮
- 弹出抽屉显示可用 MCP 工具列表（Checkbox 多选）
- 选择结果通过 `enabledToolIds` 传递

### 4.2 A/B 测试管理页面

- `abTest/index.vue` — 测试列表 + 创建 + 结果对比
- `abTest.ts` — API 函数

### 4.3 Token 用量显示

- 对话页面底部显示当前会话 Token 用量

### 4.4 路由配置

- 新增 `/ai/abTest` 路由

---

## 风险评估

| 风险 | 影响 | 应对 |
|------|------|------|
| A/B 测试执行时间过长 | 页面超时 | 异步执行，WebSocket/SSE 推送进度 |
| Token 用量统计不准确 | 前端显示偏差 | 使用 LLM 返回的 usage 字段，不自行估算 |
| MCP 工具选择后切换会话丢失 | 用户体验差 | 保存到 ai_session.enabled_tool_ids |
| 前端工具选择器交互复杂 | 开发成本高 | 简化为 Checkbox 列表，不做拖拽排序 |

---

## 文件变更预估

| 类型 | 文件 | 变更 |
|------|------|------|
| **新增** | `AiAbTest.java` | A/B 测试实体 |
| **新增** | `AiAbTestBo.java` | A/B 测试 Bo |
| **新增** | `AiAbTestVo.java` | A/B 测试 Vo |
| **新增** | `AiAbTestResult.java` | 测试结果实体 |
| **新增** | `AiAbTestResultVo.java` | 测试结果 Vo |
| **新增** | `AiAbTestMapper.java` | Mapper |
| **新增** | `AiAbTestResultMapper.java` | Mapper |
| **新增** | `IAiAbTestService.java` | Service 接口 |
| **新增** | `AiAbTestServiceImpl.java` | Service 实现 |
| **新增** | `AiAbTestController.java` | Controller |
| **新增** | `abTest.ts` | 前端 API |
| **新增** | `abTest/index.vue` | A/B 测试管理页面 |
| **修改** | `AiServiceFacade.java` | MCP 工具选择 + 上下文窗口管理 |
| **修改** | `AiChatRequest.java` | 新增 enabledToolIds 字段 |
| **修改** | `AiSession.java` | 新增 enabledToolIds 字段 |
| **修改** | `ToolProviderFactory.java` | 新增按 ID 获取方法 |
| **修改** | `AiChatController.java` | Token 用量端点 |
| **修改** | `assistant/index.vue` | 工具选择器 + Token 显示 |
| **修改** | `router/index.ts` | 新增路由 |
| **SQL** | `ai_week10.sql` | ALTER TABLE + CREATE TABLE + INSERT |

---

## 建议执行顺序

| 天 | 任务 |
|----|------|
| Day 1 | Task 1（MCP 工具按会话选择 — 后端） |
| Day 2 | Task 1（前端工具选择器）+ Task 3（上下文窗口管理） |
| Day 3 | Task 2（A/B 测试 — 后端） |
| Day 4 | Task 2（A/B 测试 — 前端）+ Task 4（路由 + Token 显示） |
| Day 5 | 整体测试 + Bug 修复 |
