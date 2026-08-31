# NexusERP AI 模块 — 第十一周开发计划

> **阶段**：Phase 2+ — AI 运营与成本分析
> **周次**：第 11 周
> **日期**：2026-06-23 ~ 2026-06-27
> **前置依赖**：第十周全部 Task 已完成并编译通过

---

## 本周目标

1. 实现用户 Token 用量统计（按用户 + 模型维度聚合）
2. 实现模型定价配置与成本计算
3. 实现 Token 用量管理后台（统计看板 + 用量明细）
4. 前端补齐：Token 用量统计页面 + 成本分析看板

---

## Task 1：Token 用量数据模型与采集（P0）

> **背景**：当前 `ai_message` 表仅存储单条消息的 `token_usage`（总量），缺少 input/output 拆分和模型关联。`AiAgentListener` 的内存统计重启即丢失。需要建立持久化的按用户+模型维度的 Token 用量统计体系。

### 1.1 数据模型变更

#### ai_message 表新增字段

```sql
ALTER TABLE ai_message ADD COLUMN model_id BIGINT DEFAULT NULL COMMENT '使用的模型ID';
ALTER TABLE ai_message ADD COLUMN input_tokens INT DEFAULT NULL COMMENT '输入 Token 数';
ALTER TABLE ai_message ADD COLUMN output_tokens INT DEFAULT NULL COMMENT '输出 Token 数';
```

> 设计说明：在 `ai_message` 上增加 `model_id` + `input_tokens` + `output_tokens`，使每条消息可精确追溯到模型并拆分 Token 类型。保留原有 `token_usage` 字段兼容历史数据。

#### ai_model 表新增定价字段

```sql
ALTER TABLE ai_model ADD COLUMN input_price_per_million DECIMAL(10,6) DEFAULT NULL COMMENT '输入价格（元/百万Token）';
ALTER TABLE ai_model ADD COLUMN output_price_per_million DECIMAL(10,6) DEFAULT NULL COMMENT '输出价格（元/百万Token）';
ALTER TABLE ai_model ADD COLUMN currency VARCHAR(16) DEFAULT 'CNY' COMMENT '币种：CNY/USD';
```

> 设计说明：定价采用"每百万 Token"单位，与主流 AI 厂商（OpenAI/DeepSeek/通义千问）的计价方式一致。

#### 新建 ai_token_usage 日统计表

```sql
CREATE TABLE IF NOT EXISTS ai_token_usage (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    user_name       VARCHAR(64)  DEFAULT NULL COMMENT '用户名称（冗余，避免关联查询）',
    model_id        BIGINT       NOT NULL COMMENT '模型ID',
    model_name      VARCHAR(128) DEFAULT NULL COMMENT '模型名称（冗余）',
    usage_date      DATE         NOT NULL COMMENT '统计日期',
    total_input_tokens  BIGINT   DEFAULT 0 COMMENT '当日输入 Token 总量',
    total_output_tokens BIGINT   DEFAULT 0 COMMENT '当日输出 Token 总量',
    total_tokens    BIGINT       DEFAULT 0 COMMENT '当日 Token 总量（input+output）',
    request_count   INT          DEFAULT 0 COMMENT '当日请求次数',
    total_cost      DECIMAL(12,4) DEFAULT 0 COMMENT '当日总成本（元）',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_model_date (tenant_id, user_id, model_id, usage_date),
    KEY idx_user_date (tenant_id, user_id, usage_date),
    KEY idx_model_date (tenant_id, model_id, usage_date),
    KEY idx_date (usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Token 用量日统计';
```

> 设计说明：日统计表通过唯一索引 `(tenant_id, user_id, model_id, usage_date)` 保证幂等，支持增量更新。冗余 `user_name` 和 `model_name` 避免高频查询时的多表 JOIN。

### 1.2 后端实现

#### 1.2.1 AiMessage 实体新增字段

```java
/**
 * 使用的模型ID
 */
private Long modelId;

/**
 * 输入 Token 数
 */
private Integer inputTokens;

/**
 * 输出 Token 数
 */
private Integer outputTokens;
```

#### 1.2.2 AiModel 实体新增字段

```java
/**
 * 输入价格（元/百万Token）
 */
private BigDecimal inputPricePerMillion;

/**
 * 输出价格（元/百万Token）
 */
private BigDecimal outputPricePerMillion;

/**
 * 币种：CNY/USD
 */
private String currency;
```

#### 1.2.3 AiTokenUsage 实体

- 新增 `AiTokenUsage.java` 实体（字段同上表）
- 新增 `AiTokenUsageVo.java`（含计算字段：avgTokensPerRequest、costPerDay）
- 新增 `AiTokenUsageMapper.java`

#### 1.2.4 Token 用量采集 — 修改 AiChatModelListener

当前 `AiChatModelListener` 仅做日志记录，需增强为持久化采集：

```java
/**
 * LLM 响应回调：记录 input/output tokens 到消息和日统计
 * <p>在 onResponse 中：</p>
 * <ol>
 *   <li>更新当前 ai_message 的 model_id / input_tokens / output_tokens</li>
 *   <li>异步更新 ai_token_usage 日统计表</li>
 * </ol>
 */
@Override
public void onResponse(ChatModelResponseContext responseContext) {
    // 1. 从上下文获取 sessionId、userId、modelId
    // 2. 更新 ai_message 的 token 明细
    // 3. 异步更新 ai_token_usage 日统计
}
```

> 关键设计：通过 `ChatModelRequestContext` 的 `metadata()` 传递 `sessionId`、`userId`、`modelId`，在 `AiServiceFacade.invokeWithAgent()` 构建请求时注入。

#### 1.2.5 AiTokenUsageService — 日统计聚合

```java
/**
 * Token 用量日统计服务
 */
public interface IAiTokenUsageService {

    /**
     * 增量更新日统计（每次 LLM 调用后触发）
     * <p>按 (userId, modelId, date) 聚合，存在则累加，不存在则插入</p>
     *
     * @param userId       用户ID
     * @param userName     用户名称
     * @param modelId      模型ID
     * @param modelName    模型名称
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param cost         本次成本
     */
    void incrementDailyUsage(Long userId, String userName, Long modelId,
                             String modelName, int inputTokens, int outputTokens, BigDecimal cost);

    /**
     * 手动触发历史数据回填
     * <p>从 ai_message 表聚合历史数据，填充 ai_token_usage 表</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     */
    void backfillUsage(LocalDate startDate, LocalDate endDate);

    /**
     * 查询用户 Token 用量汇总
     *
     * @param userId    用户ID（null=全部用户）
     * @param modelId   模型ID（null=全部模型）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 用量汇总列表
     */
    List<AiTokenUsageVo> queryUsage(Long userId, Long modelId,
                                     LocalDate startDate, LocalDate endDate);

    /**
     * 查询 Token 用量趋势（按日聚合）
     *
     * @param userId    用户ID（null=全部用户）
     * @param modelId   模型ID（null=全部模型）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按日聚合的趋势数据
     */
    List<Map<String, Object>> queryUsageTrend(Long userId, Long modelId,
                                               LocalDate startDate, LocalDate endDate);

    /**
     * 计算单次调用的成本
     *
     * @param modelId      模型ID
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @return 成本金额
     */
    BigDecimal calculateCost(Long modelId, int inputTokens, int outputTokens);
}
```

#### 1.2.6 成本计算逻辑

```java
/**
 * 计算单次调用成本
 * <p>成本 = (inputTokens / 1,000,000) * inputPrice + (outputTokens / 1,000,000) * outputPrice</p>
 * <p>若模型未配置价格，返回 BigDecimal.ZERO</p>
 */
public BigDecimal calculateCost(Long modelId, int inputTokens, int outputTokens) {
    AiModel model = aiModelMapper.selectById(modelId);
    if (model == null || model.getInputPricePerMillion() == null) {
        return BigDecimal.ZERO;
    }
    BigDecimal inputCost = model.getInputPricePerMillion()
        .multiply(BigDecimal.valueOf(inputTokens))
        .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    BigDecimal outputCost = model.getOutputPricePerMillion()
        .multiply(BigDecimal.valueOf(outputTokens))
        .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    return inputCost.add(outputCost);
}
```

---

## Task 2：Token 用量管理 API（P0）

### 2.1 AiTokenUsageController

```java
/**
 * Token 用量统计控制器
 */
@RestController
@RequestMapping("/ai/token-usage")
public class AiTokenUsageController {

    /**
     * 查询 Token 用量汇总
     * <p>支持按用户、模型、时间范围筛选</p>
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/summary")
    public TableDataInfo<AiTokenUsageVo> summary(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long modelId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    /**
     * 查询 Token 用量趋势
     * <p>按日聚合，返回时间序列数据，供前端图表展示</p>
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long modelId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    /**
     * 查询当前用户的 Token 用量概览
     * <p>普通用户查看自己的用量，无需管理员权限</p>
     */
    @GetMapping("/my-usage")
    public R<Map<String, Object>> myUsage(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    /**
     * 查询模型用量排行
     * <p>按模型聚合，返回各模型的 Token 用量和成本排行</p>
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/model-ranking")
    public R<List<Map<String, Object>>> modelRanking(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    /**
     * 查询用户用量排行
     * <p>按用户聚合，返回各用户的 Token 用量和成本排行</p>
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/user-ranking")
    public R<List<Map<String, Object>>> userRanking(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    /**
     * 手动触发历史数据回填
     * <p>从 ai_message 表聚合历史数据填充 ai_token_usage</p>
     */
    @SaCheckPermission("ai:tokenUsage:backfill")
    @PostMapping("/backfill")
    public R<Void> backfill(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);
}
```

### 2.2 AiModelController 扩展

- 模型管理页面增加"定价配置"字段（inputPricePerMillion / outputPricePerMillion / currency）
- AiModelBo / AiModelVo 同步新增定价字段

---

## Task 3：前端 Token 用量统计页面（P1）

### 3.1 API 函数

新增 `plus-ui/src/api/ai/tokenUsage.ts`：

```typescript
// 查询 Token 用量汇总
export function getTokenUsageSummary(params) { ... }
// 查询 Token 用量趋势
export function getTokenUsageTrend(params) { ... }
// 查询当前用户用量
export function getMyTokenUsage(params) { ... }
// 模型用量排行
export function getModelRanking(params) { ... }
// 用户用量排行
export function getUserRanking(params) { ... }
// 历史数据回填
export function backfillTokenUsage(params) { ... }
```

### 3.2 Token 用量统计页面

新增 `plus-ui/src/views/ai/tokenUsage/index.vue`：

- **顶部筛选栏**：时间范围选择器 + 用户选择 + 模型选择
- **统计卡片**：总 Token 数 / 总输入 Token / 总输出 Token / 总成本 / 总请求次数
- **趋势图表**：ECharts 折线图展示每日 Token 用量趋势（双 Y 轴：Token 数 + 成本）
- **模型排行**：按模型聚合的 Token 用量和成本排行表格
- **用户排行**：按用户聚合的 Token 用量和成本排行表格
- **用量明细**：分页表格展示每日每用户每模型的详细用量

### 3.3 模型管理页面扩展

- 模型编辑对话框增加"定价配置"区域：
  - 输入价格（元/百万 Token）
  - 输出价格（元/百万 Token）
  - 币种选择（CNY/USD）

### 3.4 个人用量概览

- 对话页面右上角增加"我的用量"入口
- 弹窗显示当前用户的 Token 用量概览（本月用量 / 今日用量 / 常用模型）

### 3.5 路由配置

- 新增 `/ai/tokenUsage` 路由

---

## Task 4：AiServiceFacade 修改 — 传递 metadata（P0）

> **背景**：当前 `AiChatModelListener` 无法获取当前请求的 `sessionId`、`userId`、`modelId`，需要通过 metadata 传递。

### 4.1 修改 AiServiceFacade.invokeWithAgent()

在构建 ChatMessage 时注入 metadata：

```java
// 在构建 UserMessage 时注入 metadata，供 ChatModelListener 读取
UserMessage userMessage = UserMessage.from(query);
userMessage.metadata().put("sessionId", sessionId.toString());
userMessage.metadata().put("userId", LoginHelper.getUserId().toString());
userMessage.metadata().put("modelId", modelId.toString());
userMessage.metadata().put("userName", LoginHelper.getUsername());
userMessage.metadata().put("modelName", model.getModelName());
```

### 4.2 修改 AiChatModelListener

从 metadata 中提取信息并持久化：

```java
@Override
public void onResponse(ChatModelResponseContext responseContext) {
    var chatRequest = responseContext.chatRequest();
    var lastMessage = chatRequest.messages().get(chatRequest.messages().size() - 1);

    // 从 metadata 提取
    Long sessionId = Long.valueOf(lastMessage.metadata().get("sessionId"));
    Long userId = Long.valueOf(lastMessage.metadata().get("userId"));
    Long modelId = Long.valueOf(lastMessage.metadata().get("modelId"));
    String userName = lastMessage.metadata().get("userName");
    String modelName = lastMessage.metadata().get("modelName");

    // Token 明细
    var usage = responseContext.chatResponse().tokenUsage();
    int inputTokens = usage != null ? usage.inputTokenCount() : 0;
    int outputTokens = usage != null ? usage.outputTokenCount() : 0;

    // 1. 更新 ai_message 的 token 明细
    aiMessageService.updateTokenDetail(sessionId, modelId, inputTokens, outputTokens);

    // 2. 异步更新日统计
    BigDecimal cost = aiTokenUsageService.calculateCost(modelId, inputTokens, outputTokens);
    aiTokenUsageService.incrementDailyUsage(userId, userName, modelId, modelName,
                                             inputTokens, outputTokens, cost);
}
```

---

## 风险评估

| 风险 | 影响 | 应对 |
|------|------|------|
| ChatModelListener 中 metadata 传递可能丢失 | Token 统计不完整 | 降级方案：从 ai_session 关联查询 modelId |
| 高并发下日统计表更新竞争 | 数据不一致 | 使用 INSERT ... ON DUPLICATE KEY UPDATE 原子操作 |
| 历史数据无 input/output 拆分 | 回填数据不完整 | 回填时 input_tokens = token_usage, output_tokens = 0 |
| 模型未配置价格时成本为 0 | 成本统计不准确 | 前端提示"未配置价格"，管理员可后续补充 |
| ai_message 表数据量大时回填慢 | 执行超时 | 分批回填（每次处理 1 个月），异步执行 |

---

## 文件变更预估

| 类型 | 文件 | 变更 |
|------|------|------|
| **新增** | `AiTokenUsage.java` | Token 用量日统计实体 |
| **新增** | `AiTokenUsageVo.java` | Token 用量 Vo |
| **新增** | `AiTokenUsageMapper.java` | Mapper |
| **新增** | `IAiTokenUsageService.java` | Service 接口 |
| **新增** | `AiTokenUsageServiceImpl.java` | Service 实现 |
| **新增** | `AiTokenUsageController.java` | Controller |
| **新增** | `tokenUsage.ts` | 前端 API |
| **新增** | `tokenUsage/index.vue` | Token 用量统计页面 |
| **修改** | `AiMessage.java` | 新增 modelId/inputTokens/outputTokens 字段 |
| **修改** | `AiModel.java` | 新增定价字段 |
| **修改** | `AiModelBo.java` | 新增定价字段 |
| **修改** | `AiModelVo.java` | 新增定价字段 |
| **修改** | `AiChatModelListener.java` | 增强为持久化采集 |
| **修改** | `AiServiceFacade.java` | 注入 metadata |
| **修改** | `AiChatController.java` | 修改 getTokenUsage 返回明细 |
| **修改** | `model/index.vue` | 模型编辑增加定价配置 |
| **修改** | `assistant/index.vue` | 个人用量概览入口 |
| **修改** | `router/index.ts` | 新增路由 |
| **SQL** | `ai_week11.sql` | ALTER TABLE + CREATE TABLE + INSERT |

---

## 建议执行顺序

| 天 | 任务 |
|----|------|
| Day 1 | Task 1（数据模型变更 + AiMessage/AiModel 实体修改 + AiTokenUsage 实体/Mapper） |
| Day 2 | Task 1（AiTokenUsageService + 成本计算 + 日统计聚合）+ Task 4（metadata 传递） |
| Day 3 | Task 2（AiTokenUsageController 全部端点）+ AiChatModelListener 持久化改造 |
| Day 4 | Task 3（前端 API + Token 用量统计页面 + 模型定价配置 + 个人用量概览） |
| Day 5 | 整体测试 + 历史数据回填验证 + Bug 修复 |
