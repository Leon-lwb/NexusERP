# NexusERP AI 开发计划 — 第二周（Phase 1 核心功能）

> **前置状态**：Phase 0 最小闭环已完成，Day 1 厂商/模型管理后端 CRUD 已编译通过。
>
> **本周目标**：完成前端对接 + 流式推送升级 + ErpReportAgent + ECharts 渲染 + ERP 业务 Tool，实现 **"自然语言查询 ERP 数据 → 流式回答 + 图表可视化"** 的完整闭环。

---

## 现状盘点

### 已完成

| 模块 | 内容 | 状态 |
|------|------|------|
| 后端 | ruoyi-common-ai + ruoyi-ai Maven 模块骨架 | ✅ |
| 后端 | AiProvider/AiModel Entity + Mapper + BO/VO + Service CRUD | ✅ |
| 后端 | AiProviderController（6端点）+ AiModelController（7端点） | ✅ |
| 后端 | AiChatController（send/history/sessions） | ✅ |
| 后端 | AiServiceFacade（SupervisorAgent + ErpQueryAgent 编排） | ✅ |
| 后端 | ChatModelFactory（ChatModel + StreamingChatModel 双缓存） | ✅ |
| 后端 | ErpQueryService（@DS("erp") 数据源切换） | ✅ |
| 后端 | 3个 SQL Tool（QueryAllTables/QueryTableSchema/ExecuteSqlQuery） | ✅ |
| 后端 | AiSseMessageHelper + AiThreadPoolConfig + AiRequestContext | ✅ |
| 前端 | assistant/index.vue（聊天主页面，已接入真实 API） | ✅ |
| 前端 | AiChatDrawer.vue（侧边抽屉聊天组件） | ✅ |
| 前端 | api/ai/chat.ts（3个 API） | ✅ |

### 待完成（本周重点）

| 差距 | 影响 | 优先级 |
|------|------|--------|
| 前端 model/index.vue、provider.vue 使用 Mock 数据 | 厂商/模型无法管理 | P0 |
| AiServiceFacade 使用同步 ChatModel，非流式 | AI 回复一次性推送，体验差 | P0 |
| 无会话管理端点（删除/重命名） | 无法管理历史会话 | P1 |
| 无 ErpReportAgent | 无法生成 ECharts 图表 | P1 |
| 无 ECharts 渲染组件 | AI 返回的图表 JSON 无法可视化 | P1 |
| 无 PersistentChatMemory | 多轮对话无上下文记忆 | P1 |
| 无 ERP 业务 Tool（库存/采购/销售/生产） | AI 只能做通用 SQL 查询 | P2 |
| API Key 未加密存储 | 安全隐患 | P2 |

---

## Day 1（周一）：前端厂商/模型管理对接 + 会话管理

### 目标
前端 model/provider 页面对接真实后端 API，会话管理功能闭环。

### 后端任务

#### 1.1 AiChatController 新增会话管理端点

在 `AiChatController.java` 中新增：

```java
/** 删除会话（级联删除消息） */
@DeleteMapping("/session/{ids}")
public R<Void> deleteSession(@PathVariable Long[] ids)

/** 重命名会话 */
@PutMapping("/session")
public R<Void> renameSession(@RequestBody AiSession session)
```

实现逻辑：
- 删除会话时级联删除 `ai_message` 表中对应消息
- 重命名时只更新 `title` 字段

#### 1.2 IAiSessionService / AiSessionServiceImpl

在 `ruoyi-common-ai` 中新建：
- `IAiSessionService` 接口：selectByUserId、selectById、insert、updateTitle、deleteByIds
- `AiSessionServiceImpl` 实现：标准 CRUD，删除时级联删除消息

### 前端任务

#### 1.3 创建 api/ai/model.ts

```typescript
// 厂商管理 API
export function listProvider(query)           // GET /ai/provider/list
export function getProvider(id)               // GET /ai/provider/{id}
export function addProvider(data)             // POST /ai/provider
export function updateProvider(data)          // PUT /ai/provider
export function delProvider(ids)              // DELETE /ai/provider/{ids}

// 模型管理 API
export function listModel(query)              // GET /ai/model/list
export function getModel(id)                  // GET /ai/model/{id}
export function addModel(data)                // POST /ai/model
export function updateModel(data)             // PUT /ai/model
export function delModel(ids)                 // DELETE /ai/model/{ids}
export function setDefaultModel(id)           // PUT /ai/model/setDefault/{id}
```

#### 1.4 改造 provider.vue

- 移除 Mock 数据，调用 `listProvider` API
- 新增/编辑弹窗（el-dialog）：name、apiHost、apiKey、providerType（下拉）、status
- 删除确认 + 调用 `delProvider`
- apiKey 显示脱敏（后端已处理）

#### 1.5 改造 model/index.vue

- 移除 Mock 数据，调用 `listModel` API
- 新增/编辑弹窗：providerId（下拉选厂商）、modelName、displayName、modelType、maxTokens、temperature、isDefault、status
- 删除确认 + 调用 `delModel`
- "设为默认"按钮调用 `setDefaultModel`

#### 1.6 chat.ts 新增会话管理 API

```typescript
export function deleteSession(ids: Array<number | string>)   // DELETE /ai/chat/session/{ids}
export function renameSession(data: { id: number; title: string })  // PUT /ai/chat/session
```

#### 1.7 assistant/index.vue 增加右键菜单

- 会话列表项右键菜单：重命名、删除
- 重命名：el-popover 输入框
- 删除：确认后调用 `deleteSession`

### 交付物
- [x] AiChatController 新增 2 个端点
- [x] IAiSessionService + AiSessionServiceImpl
- [x] api/ai/model.ts（11个 API）
- [x] provider.vue 对接真实 API + CRUD 弹窗
- [x] model/index.vue 对接真实 API + CRUD 弹窗
- [x] chat.ts 新增 2 个会话管理 API
- [x] assistant/index.vue 右键菜单

---

## Day 2（周二）：流式 Token 推送 + PersistentChatMemory

### 目标
AI 回复从"一次性推送"升级为"逐 Token 流式推送"，多轮对话具备上下文记忆。

### 后端任务

#### 2.1 AiServiceFacade 改造为流式推送

当前问题：`chatModel.chat()` 是同步调用，结果一次性通过 `AiSseMessageHelper.sendContent()` 推送。

改造方案：使用 `StreamingChatModel.chat()` + `StreamingChatResponseHandler`：

```java
StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId());

// 对于普通对话（非 Agent），使用流式模型
streamingModel.chat(ChatRequest.builder()
    .messages(messages)
    .build(),
    new StreamingChatResponseHandler() {
        @Override
        public void onPartialResponse(String partialToken) {
            // 逐 Token 推送
            AiSseMessageHelper.sendContent(userId, partialToken);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            // 保存完整 AI 回复到数据库
            String fullContent = response.aiMessage().text();
            saveAiMessage(finalSessionId, fullContent);
            AiSseMessageHelper.sendDone(userId);
        }

        @Override
        public void onError(Throwable error) {
            AiSseMessageHelper.sendError(userId, "AI 对话失败: " + error.getMessage());
        }
    });
```

#### 2.2 AiSseMessageHelper 新增增量模式

当前 `sendContent()` 是完整内容替换。新增增量模式：

```java
/** 发送增量内容（流式 Token，追加模式） */
public static void sendContentIncrement(Long userId, String token) {
    send(userId, TYPE_CONTENT_INCREMENT, token);
}

/** 发送完整内容（替换模式，用于 Agent 一次性结果） */
public static void sendContent(Long userId, String content) {
    send(userId, TYPE_CONTENT, content);
}
```

前端 SSE 处理对应修改：
- `AI_CONTENT_INCREMENT` → 追加到当前 AI 消息末尾
- `AI_CONTENT` → 替换当前 AI 消息（Agent 模式）

#### 2.3 PersistentChatMemory 实现

参考 `E:\ruoyi-ai\...\memory\PersistentChatMemoryStore.java`，在 `ruoyi-ai` 中创建：

```java
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final AiMessageMapper aiMessageMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 从 ai_message 表加载历史消息，转换为 LangChain4j ChatMessage
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 更新消息（增量保存 AI 回复）
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 删除会话消息
    }
}
```

#### 2.4 AiServiceFacade 集成 PersistentChatMemory

```java
// 替换当前的 MessageWindowChatMemory.withMaxMessages(40)
MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
    .id(sessionId)
    .maxMessages(20)
    .chatMemoryStore(persistentChatMemoryStore)
    .build();
```

#### 2.5 智能路由：普通对话 vs Agent

在 `AiServiceFacade.chat()` 中增加意图判断：

```java
// 简单规则：包含关键词走 Agent，否则走普通流式对话
boolean needAgent = containsDataQueryKeywords(request.getContent());

if (needAgent) {
    // Agent 模式：SupervisorAgent + 同步 ChatModel → 一次性推送结果
    // （SupervisorAgent 暂不支持流式回调）
    invokeWithAgent(chatModel, request, userId, finalSessionId);
} else {
    // 普通对话：StreamingChatModel → 逐 Token 流式推送
    invokeWithStreaming(streamingModel, messages, userId, finalSessionId);
}
```

### 前端任务

#### 2.6 assistant/index.vue 适配增量推送

修改 SSE 消息处理逻辑：

```typescript
case 'AI_CONTENT_INCREMENT':
  // 追加 Token 到当前 AI 消息
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg.role === 'assistant') {
    lastMsg.content += detail.content  // 追加而非替换
  } else {
    messages.value.push({ role: 'assistant', content: detail.content })
  }
  break
case 'AI_CONTENT':
  // Agent 模式完整替换
  const msg = messages.value[messages.value.length - 1]
  if (msg && msg.role === 'assistant') {
    msg.content = detail.content  // 替换
  } else {
    messages.value.push({ role: 'assistant', content: detail.content })
  }
  break
```

### 交付物
- [x] AiServiceFacade 流式推送改造（StreamingChatModel + onPartialResponse）
- [x] AiSseMessageHelper 新增 AI_CONTENT_INCREMENT 类型
- [x] PersistentChatMemoryStore 实现
- [x] AiServiceFacade 智能路由（普通对话 vs Agent）
- [x] 前端适配增量推送

---

## Day 3（周三）：ErpReportAgent + ECharts 渲染

### 目标
AI 能根据数据生成 ECharts 图表，前端自动渲染可视化图表。

### 后端任务

#### 3.1 创建 ErpReportAgent

参考 `E:\ruoyi-ai\...\agent\EchartsAgent.java`，在 `ruoyi-ai` 中创建：

```java
public interface ErpReportAgent {

    @SystemMessage("""
        你是一个 ERP 数据可视化专家。你的工作流程：
        1. 理解用户想要的报表类型
        2. 使用数据库查询工具获取数据
        3. 将数据转换为 ECharts 图表配置
        4. 以 ```echarts 代码块格式返回 ECharts 配置

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

        安全规则：
        - ECharts 配置中禁止使用 JavaScript 函数
        - 只使用静态配置，数据值必须为字面量
        """)
    @UserMessage("Generate an Echarts chart for: {{query}}")
    @Agent("ERP 数据可视化专家")
    String generateReport(@V("query") String query);
}
```

#### 3.2 AiServiceFacade 注册 ErpReportAgent

```java
ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
    .chatModel(chatModel)
    .tools(queryAllTablesTool, executeSqlQueryTool)
    .build();

SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
    .chatModel(chatModel)
    .subAgents(queryAgent, reportAgent)  // 新增 reportAgent
    .responseStrategy(SupervisorResponseStrategy.LAST)
    .maxAgentsInvocations(10)
    .build();
```

### 前端任务

#### 3.3 创建 EchartsRenderer.vue

参考 `E:\ruoyi-ai\ruoyi-web\src\components\EchartsRenderer\index.vue`：

```vue
<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  option: string
  height?: number
}>()

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

onMounted(() => {
  renderChart()
  // ResizeObserver 监听容器变化
})

watch(() => props.option, () => {
  renderChart()
})

function renderChart() {
  if (!chartRef.value) return
  try {
    const option = JSON.parse(props.option)
    if (!chartInstance) {
      chartInstance = echarts.init(chartRef.value)
    }
    chartInstance.setOption(option, true)
  } catch (e) {
    console.error('ECharts 渲染失败:', e)
  }
}

onUnmounted(() => {
  chartInstance?.dispose()
})
</script>
```

#### 3.4 创建 aiRenderers.ts

参考 `E:\ruoyi-ai\ruoyi-web\src\utils\markdownRenderers.ts`，在 `plus-ui/src/utils/aiRenderers.ts` 中实现：

- 检测 Markdown 中的 ````echarts` 代码块
- 检测 ````json` 代码块中是否为 ECharts 配置（含 xAxis/yAxis/series/title/legend 关键字段）
- 自动渲染为 ECharts 图表
- 安全过滤：禁止 formatter/function 等可执行字段

#### 3.5 assistant/index.vue 集成 ECharts 渲染

修改 Markdown 渲染逻辑，使用自定义 renderer 处理 echarts/json 代码块：

```typescript
import { marked } from 'marked'
import { renderEchartsBlock } from '@/utils/aiRenderers'

const renderer = new marked.Renderer()
renderer.code = function(code, language) {
  if (language === 'echarts' || isEchartsConfig(code, language)) {
    return `<div class="echarts-container" data-option="${encodeURIComponent(code)}"></div>`
  }
  return `<pre><code class="language-${language}">${code}</code></pre>`
}
```

### 交付物
- [x] ErpReportAgent 接口定义
- [x] AiServiceFacade 注册 ErpReportAgent
- [x] EchartsRenderer.vue 组件
- [x] aiRenderers.ts（ECharts Markdown 渲染器）
- [x] assistant/index.vue 集成 ECharts 渲染

---

## Day 4（周四）：ERP 业务 Tool 开发

### 目标
开发 4 个 ERP 领域业务 Tool，让 AI 能回答"库存不足的物料"、"供应商风险"、"销售趋势"等业务问题。

### 后端任务

#### 4.1 ErpInventoryTool（库存/补货工具）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpInventoryTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询低于安全库存的物料列表及其当前库存、安全库存、缺口数量")
    public String queryLowStockMaterials() {
        // SQL: 联查 wm_material_stock + bd_material
        // 返回：物料编码、名称、当前库存、安全库存、缺口数量
    }

    @Tool("根据物料编码查询最近N个月的出库趋势")
    public String queryMaterialOutboundTrend(
        @P("物料编码") String materialCode,
        @P("查询月数") int months) {
        // SQL: 查 wm_material_stock_tx 按月聚合出库数量
    }

    @Tool("查询库存总金额及按仓库分布")
    public String queryInventoryValue() {
        // SQL: 查 wm_material_stock，quantity * avg_cost 按仓库分组
    }
}
```

#### 4.2 ErpPurchaseTool（采购/供应商工具）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPurchaseTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询指定时间段的采购总金额及按供应商分布")
    public String queryPurchaseAmountByVendor(
        @P("开始日期(YYYY-MM-DD)") String startDate,
        @P("结束日期(YYYY-MM-DD)") String endDate) {
        // SQL: 查 pu_purchase_order 按供应商分组统计
    }

    @Tool("查询供应商综合评估数据，包括交货准时率、退货率")
    public String queryVendorRiskAssessment(
        @P("供应商ID") Long vendorId) {
        // SQL: 查 pu_vendor_evaluation 综合评分
    }

    @Tool("查询最近N天未到货的在途采购订单")
    public String queryPendingPurchaseOrders(
        @P("查询天数") int days) {
        // SQL: 查 pu_purchase_order 状态为已确认但未入库
    }
}
```

#### 4.3 ErpSalesTool（销售/客户工具）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpSalesTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询指定物料近N个月的月度销售数据，用于趋势分析")
    public String queryMonthlySalesTrend(
        @P("物料编码") String materialCode,
        @P("查询月数") int months) {
        // SQL: 查 so_sales_order_line + so_sales_order_header 按月聚合
    }

    @Tool("查询按客户维度的销售金额排行（Top N）")
    public String queryTopClientsBySales(
        @P("时间范围（天数）") int days,
        @P("Top N") int topN) {
        // SQL: 查 so_sales_order_header + bd_client 按客户分组排序
    }

    @Tool("查询最近N天的退货统计")
    public String queryRecentReturns(
        @P("查询天数") int days) {
        // SQL: 查 so_sales_return 按原因分组统计
    }
}
```

#### 4.4 ErpProductionTool（生产/排程工具）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpProductionTool {

    private final ErpQueryService erpQueryService;

    @Tool("查询当前在产工单及其进度")
    public String queryActiveWorkOrders() {
        // SQL: 查 prd_work_order 状态为进行中
    }

    @Tool("查询指定车间/工位的产能利用率")
    public String queryWorkstationUtilization(
        @P("车间ID") Long workshopId) {
        // SQL: 查 prd_work_order + sch_production_schedule 计算利用率
    }

    @Tool("查询BOM展开（指定产品的所有子件及用量）")
    public String queryBomExplosion(
        @P("产品物料编码") String materialCode) {
        // SQL: 递归查 bd_bom + bd_bom_detail
    }
}
```

#### 4.5 AiServiceFacade 注册业务 Tool

将 4 个业务 Tool 注入到 ErpQueryAgent：

```java
ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
        .id(memoryId)
        .maxMessages(20)
        .chatMemoryStore(persistentChatMemoryStore)
        .build())
    .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
           erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)
    .build();
```

同时更新 ErpQueryAgent 的 SystemMessage，增加业务 Tool 说明。

### 交付物
- [x] ErpInventoryTool（3个 @Tool 方法）
- [x] ErpPurchaseTool（3个 @Tool 方法）
- [x] ErpSalesTool（3个 @Tool 方法）
- [x] ErpProductionTool（3个 @Tool 方法）
- [x] AiServiceFacade 注册业务 Tool
- [x] ErpQueryAgent SystemMessage 更新

---

## Day 5（周五）：集成测试 + 优化 + 文档

### 目标
全链路集成测试，修复 Bug，优化体验。

### 测试场景

#### 5.1 基础功能测试

| 序号 | 测试项 | 预期结果 |
|------|--------|----------|
| 1 | 厂商管理 CRUD | 新增/编辑/删除/列表正常 |
| 2 | 模型管理 CRUD | 新增/编辑/删除/设默认正常 |
| 3 | 模型配置变更 | ChatModelFactory 缓存自动清除 |
| 4 | API Key 显示 | 列表中仅显示最后4位 |

#### 5.2 聊天功能测试

| 序号 | 测试项 | 预期结果 |
|------|--------|----------|
| 5 | 普通对话 | "你好" → 逐 Token 流式回复 |
| 6 | 数据查询 | "查询本月采购订单总金额" → Agent 调用 Tool → 返回结果 |
| 7 | 图表生成 | "本月各品类销售额占比" → ErpReportAgent → ECharts 饼图 |
| 8 | 多轮对话 | 追问"按月趋势呢" → 上下文记忆正常 |
| 9 | 会话管理 | 右键重命名/删除会话正常 |
| 10 | 错误处理 | API Key 错误 → AI_ERROR 消息 |

#### 5.3 业务 Tool 测试

| 序号 | 测试项 | 预期结果 |
|------|--------|----------|
| 11 | 库存查询 | "哪些物料需要补货" → ErpInventoryTool.queryLowStockMaterials |
| 12 | 采购分析 | "最近30天采购金额按供应商分布" → ErpPurchaseTool |
| 13 | 销售排行 | "销售金额TOP10客户" → ErpSalesTool |
| 14 | 生产进度 | "当前在产工单" → ErpProductionTool |
| 15 | 安全校验 | "DROP TABLE" → 被 ExecuteSqlQueryTool 拦截 |

### 优化任务

#### 5.4 API Key 加密存储

- 使用 Jasypt 加密 API Key（nexus-erp 已集成 jasypt-spring-boot-starter）
- AiProviderServiceImpl.insertByBo() 中加密 apiKey
- AiProviderServiceImpl.selectById() 中解密 apiKey
- AiProviderVo 中 apiKey 脱敏（仅显示最后4位）

#### 5.5 ChatModelFactory 多厂商路由

当前所有厂商均走 OpenAI 兼容接口。增加按 providerType 分支：

```java
private ChatModel buildChatModel(AiModel config) {
    return switch (config.getProviderType()) {
        case "OPENAI" -> OpenAiChatModel.builder()...build();
        case "OLLAMA" -> OllamaChatModel.builder()...build();
        // DASHSCOPE/ZHIPU 暂走 OpenAI 兼容接口
        default -> OpenAiChatModel.builder()...build();
    };
}
```

#### 5.6 性能优化

- SQL 查询超时控制（ErpQueryService 已有 timeout 参数）
- 大结果集截断（ExecuteSqlQueryTool 已有 maxRows 限制）
- ECharts JSON 安全校验（过滤 formatter/function 字段）

### 交付物
- [x] 全链路集成测试通过
- [x] API Key Jasypt 加密存储
- [x] ChatModelFactory 多厂商路由
- [x] ECharts JSON 安全校验
- [x] Bug 修复

---

## 文件变更总览

### 后端新增文件（8个）

| 文件 | 模块 | 说明 |
|------|------|------|
| `IAiSessionService.java` | ruoyi-common-ai | 会话 Service 接口 |
| `AiSessionServiceImpl.java` | ruoyi-common-ai | 会话 Service 实现 |
| `PersistentChatMemoryStore.java` | ruoyi-ai | 会话记忆持久化 |
| `ErpReportAgent.java` | ruoyi-ai | 报表生成 Agent 接口 |
| `ErpInventoryTool.java` | ruoyi-ai | 库存业务 Tool |
| `ErpPurchaseTool.java` | ruoyi-ai | 采购业务 Tool |
| `ErpSalesTool.java` | ruoyi-ai | 销售业务 Tool |
| `ErpProductionTool.java` | ruoyi-ai | 生产业务 Tool |

### 后端修改文件（4个）

| 文件 | 修改内容 |
|------|----------|
| `AiChatController.java` | 新增 deleteSession、renameSession 端点 |
| `AiServiceFacade.java` | 流式推送 + PersistentChatMemory + 智能路由 + ErpReportAgent + 业务 Tool |
| `AiSseMessageHelper.java` | 新增 AI_CONTENT_INCREMENT 类型 |
| `ChatModelFactory.java` | 多厂商路由（Ollama 分支） |

### 前端新增文件（2个）

| 文件 | 说明 |
|------|------|
| `api/ai/model.ts` | 厂商/模型管理 API（11个） |
| `utils/aiRenderers.ts` | ECharts Markdown 渲染器 |

### 前端修改文件（4个）

| 文件 | 修改内容 |
|------|----------|
| `model/provider.vue` | 移除 Mock，对接真实 API + CRUD 弹窗 |
| `model/index.vue` | 移除 Mock，对接真实 API + CRUD 弹窗 |
| `api/ai/chat.ts` | 新增 deleteSession、renameSession |
| `views/ai/assistant/index.vue` | 增量推送适配 + 右键菜单 + ECharts 渲染 |

### 前端新增组件（1个）

| 文件 | 说明 |
|------|------|
| `components/ai/EchartsRenderer.vue` | ECharts 图表渲染组件 |

---

## 风险与应对

| 风险 | 应对措施 |
|------|----------|
| StreamingChatModel + SupervisorAgent 不兼容 | SupervisorAgent 暂不支持流式回调，Agent 模式仍用同步 ChatModel + 一次性推送；仅普通对话走流式 |
| ERP 业务表结构不确定 | Tool 中 SQL 先用通用查询，根据实际表结构调整；ErpQueryService.queryErpTables() 可动态发现表 |
| ECharts JSON 安全风险 | 渲染前过滤 formatter/function 等可执行字段，只允许静态配置 |
| vue-element-plus-x 兼容性 | 本周不使用 vue-element-plus-x，直接用 Element Plus 原生组件 + marked 渲染 |
| Jasypt 加密配置 | 确认 jasypt.encryptor.password 已在 application.yml 中配置 |
