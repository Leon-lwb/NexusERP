# NexusERP AI 开发计划 — 第六周（MCP 外部工具接入 + Agent 流式推送 + 对话质量评估）

> **前置状态**：第五周已全部完成（Schema 缓存 + SystemMessage 优化 + 业务 Tool 适配 + MCP 内置工具注册中心 + 二次审查修复），编译通过。
>
> **本周目标**：实现 MCP 外部工具动态接入（REMOTE/LOCAL）、Agent 流式推送升级（Token 级实时输出）、对话质量评估机制（用户反馈）、工具状态动态过滤。
>
> **参考项目**：`D:\ruoyi-ai`（ToolProviderFactory、LangChain4jMcpToolProviderService、OutputChannel、SupervisorStreamListener、StreamingOutputWrapper）

---

## 现状与差距分析

### 已完成的核心能力
- ✅ 3 路智能路由（Agent/RAG/流式对话）
- ✅ 10 个 Tool（3 SQL + 4 业务 + 3 通用）+ SQL 列名与 DDL 完全对齐
- ✅ MCP 内置工具注册中心（BuiltinToolRegistry + AiToolInitializer + McpToolController + 前端管理页面）
- ✅ SSE 推送基础设施（SseEmitterManager + Redis Pub/Sub + AiSseMessageHelper）
- ✅ RAG 完整流水线 + Milvus/InMemory 双模式向量存储

### 本周需解决的差距

| 差距 | 影响 | 优先级 |
|------|------|--------|
| MCP 仅支持 BUILTIN 类型，REMOTE/LOCAL 无实际消费逻辑 | 无法接入外部 MCP 服务器（如 Playwright、Filesystem 等），扩展性为零 | P0 |
| Agent 模式使用同步 ChatModel，结果一次性推送 | Agent 执行期间用户只能看到"思考中"，体验差，复杂查询可能超时 | P0 |
| AiServiceFacade 工具注入硬编码 | 新增工具需改代码+重启，无法通过数据库配置动态加载 | P1 |
| 无工具测试连接端点 | 前端有按钮但后端未实现，无法验证外部 MCP 服务器连通性 | P1 |
| Agent 构建未根据 ai_tool 表 status 过滤 | 数据库禁用的工具仍会被注入到 Agent，管理页面启停无效 | P1 |
| 无对话质量评估机制 | 无法收集用户反馈，无法量化 AI 回答质量 | P2 |
| 无工具执行过程可视化 | 用户无法看到 Agent 调用了哪些工具、执行了什么 SQL | P2 |

---

## Task 1：MCP 外部工具动态接入（P0）

> 参考：`D:\ruoyi-ai\...\mcp\service\core\LangChain4jMcpToolProviderService.java`
> 参考：`D:\ruoyi-ai\...\mcp\service\core\ToolProviderFactory.java`

### 1.1 创建 ToolProviderFactory（统一工具工厂）

**文件**：`com.nexus.ai.mcp.service.ToolProviderFactory`

```java
@Component
@RequiredArgsConstructor
public class ToolProviderFactory {
    private final BuiltinToolRegistry builtinToolRegistry;
    private final McpClientManager mcpClientManager;

    /** 获取所有已启用的内置工具对象（含 @Tool 注解，供 AgenticServices.tools() 使用） */
    public List<Object> getAllEnabledBuiltinToolObjects() {
        return builtinToolRegistry.getAllBuiltinToolObjects();
    }

    /** 获取所有已启用的 MCP 外部工具 Provider（供 AgenticServices.toolProvider() 使用） */
    public List<McpToolProvider> getAllEnabledMcpToolProviders() {
        return mcpClientManager.getAllEnabledToolProviders();
    }

    /** 按工具名称列表获取 MCP 工具 Provider */
    public List<McpToolProvider> getMcpToolProvidersByNames(List<String> toolNames) {
        return mcpClientManager.getToolProvidersByNames(toolNames);
    }
}
```

### 1.2 创建 McpClientManager（MCP 客户端管理器）

**文件**：`com.nexus.ai.mcp.service.McpClientManager`

核心职责：
- 管理 MCP 客户端生命周期（创建/缓存/销毁）
- 支持 REMOTE（HTTP/SSE）和 LOCAL（STDIO）两种传输方式
- 健康检查 + 熔断降级（3 次失败后禁用 5 分钟）
- 按 ai_tool 表 status 字段动态过滤

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpClientManager {
    private final McpToolServiceImpl mcpToolService;

    // 客户端缓存：toolId -> McpClient
    private final ConcurrentHashMap<Long, McpClient> activeClients = new ConcurrentHashMap<>();
    // 健康状态：toolId -> boolean
    private final ConcurrentHashMap<Long, Boolean> toolHealthStatus = new ConcurrentHashMap<>();
    // 失败计数：toolId -> count
    private final ConcurrentHashMap<Long, AtomicInteger> toolFailureCount = new ConcurrentHashMap<>();
    // 禁用截止时间：toolId -> timestamp
    private final ConcurrentHashMap<Long, Long> toolDisabledUntil = new ConcurrentHashMap<>();

    private static final int MAX_FAILURE_COUNT = 3;
    private static final long DISABLE_DURATION_MS = 5 * 60 * 1000;

    /** 创建 REMOTE 类型客户端（HTTP/SSE） */
    private McpClient createRemoteClient(String baseUrl) { ... }

    /** 创建 LOCAL 类型客户端（STDIO） */
    private McpClient createStdioClient(String command, List<String> args) { ... }

    /** 获取或创建客户端（含健康检查和重试逻辑） */
    public McpClient getOrCreateClient(AiTool tool) { ... }

    /** 获取所有已启用工具的 McpToolProvider 列表 */
    public List<McpToolProvider> getAllEnabledToolProviders() { ... }

    /** 健康检查 */
    public boolean checkToolHealth(Long toolId) { ... }

    /** 处理工具失败（计数+熔断） */
    private void handleToolFailure(Long toolId) { ... }
}
```

### 1.3 AiTool 实体扩展

在 `AiTool` 实体中新增 `configJson` 字段（如果尚未添加），用于存储 REMOTE/LOCAL 类型的连接配置：

```json
// REMOTE 类型配置
{ "baseUrl": "http://localhost:3001/mcp" }

// LOCAL 类型配置
{ "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"] }
```

### 1.4 McpToolServiceImpl 扩展

- 新增 `testTool(Long toolId)` 方法：BUILTIN 验证注册表，REMOTE/LOCAL 验证连接
- 新增 `McpToolTestResult` DTO：`success`、`message`、`toolCount`

### 1.5 McpToolController 新增端点

```java
@SaCheckPermission("ai:mcp:test")
@PostMapping("/{id}/test")
public R<McpToolTestResult> test(@PathVariable Long id) {
    return R.ok(mcpToolService.testTool(id));
}
```

### 1.6 前端对接

- `mcp.ts` 新增 `testTool(id)` API
- `index.vue` 测试连接按钮绑定实际调用
- 新增/编辑对话框增加 `configJson` 编辑区（JSON 编辑器或表单）

---

## Task 2：Agent 流式推送升级（P0）

> 参考：`D:\ruoyi-ai\...\observability\OutputChannel.java`
> 参考：`D:\ruoyi-ai\...\observability\SupervisorStreamListener.java`
> 参考：`D:\ruoyi-ai\...\observability\StreamingOutputWrapper.java`

### 2.1 创建 OutputChannel（跨线程事件总线）

**文件**：`com.nexus.ai.observability.OutputChannel`

```java
public class OutputChannel {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(4096);
    private volatile boolean completed = false;

    /** 全局注册表：requestId -> OutputChannel */
    public static final ConcurrentHashMap<String, OutputChannel> REGISTRY = new ConcurrentHashMap<>();

    /** 非阻塞写入（100ms 超时） */
    public boolean send(String event) { ... }

    /** 阻塞式消费（200ms 轮询超时） */
    public void drain(Consumer<String> consumer) { ... }

    /** 标记完成 */
    public void complete() { ... }

    public static OutputChannel create(String requestId) { ... }
    public static OutputChannel get(String requestId) { ... }
    public static void remove(String requestId) { ... }
}
```

### 2.2 创建 AgentStreamListener（Agent 生命周期监听）

**文件**：`com.nexus.ai.observability.AgentStreamListener`

```java
public class AgentStreamListener implements AgentListener {
    private final OutputChannel channel;
    private final Long userId;

    // Agent 开始/完成/错误 → 推送状态事件
    // 工具执行前/后 → 推送工具调用事件（名称、参数、结果摘要）
    // 继承到子 Agent（inheritedBySubagents = true）
}
```

### 2.3 创建 McpToolExecutionListener（MCP 工具执行监听）

**文件**：`com.nexus.ai.observability.McpToolExecutionListener`

```java
public class McpToolExecutionListener implements McpClientListener {
    private final OutputChannel channel;
    private final Long userId;

    // MCP 工具执行前/后/错误 → 推送事件
}
```

### 2.4 AiSseMessageHelper 新增消息类型

```java
/** Agent 工具调用开始 */
public static final String TYPE_TOOL_START = "AI_TOOL_START";
/** Agent 工具调用完成 */
public static final String TYPE_TOOL_END = "AI_TOOL_END";
/** Agent 思考过程（中间推理） */
public static final String TYPE_AGENT_THINKING = "AI_AGENT_THINKING";
```

### 2.5 AiChatController 新增 SSE 流式端点

```java
/**
 * Agent 流式对话端点
 * 前端通过 EventSource 连接，实时接收 Agent 执行过程
 */
@GetMapping(value = "/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamAgent(@PathVariable String requestId) { ... }
```

### 2.6 AiServiceFacade 重构 Agent 调用方式

将 `invokeWithAgent()` 从同步阻塞改为：
1. 创建 OutputChannel + 注册到 REGISTRY
2. 返回 requestId 给前端
3. 前端建立 SSE 连接 `/ai/chat/stream/{requestId}`
4. 后台线程执行 SupervisorAgent.invoke()
5. AgentStreamListener 通过 OutputChannel 推送事件
6. drain 线程从 OutputChannel 消费 → SseEmitter 推送前端

### 2.7 前端适配

- 聊天组件监听新的 SSE 消息类型（`AI_TOOL_START`/`AI_TOOL_END`/`AI_AGENT_THINKING`）
- 渲染工具调用过程（折叠面板：工具名 → 参数 → 结果）
- Agent 模式支持 Token 级流式输出

---

## Task 3：工具状态动态过滤（P1）

### 3.1 AiServiceFacade 工具注入改造

将硬编码的工具注入改为从 ToolProviderFactory 动态获取：

```java
// Before（硬编码）
.tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
    erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)

// After（动态加载）
// 1. 获取所有已启用的内置工具
List<Object> builtinTools = toolProviderFactory.getAllEnabledBuiltinToolObjects();
// 2. 获取所有已启用的 MCP 外部工具 Provider
List<McpToolProvider> mcpProviders = toolProviderFactory.getAllEnabledMcpToolProviders();
// 3. 构建 Agent
AgenticServices.agentBuilder(ErpQueryAgent.class)
    .chatModel(chatModel)
    .tools(builtinTools.toArray())
    .toolProviders(mcpProviders.toArray(new McpToolProvider[0]))
    .build();
```

### 3.2 BuiltinToolRegistry 扩展

- 新增 `getEnabledBuiltinToolObjects()` 方法：根据 ai_tool 表 status 过滤
- 新增 `getEnabledBuiltinTools()` 方法：返回已启用的工具元数据列表

### 3.3 AiToolInitializer 同步逻辑增强

启动同步时，新增的工具默认 status=ENABLED，已存在的工具保留原 status。

---

## Task 4：对话质量评估（P2）

### 4.1 数据库表设计

采用在 `ai_message` 表新增字段的方式（而非独立表），简化实现：

```sql
ALTER TABLE ai_message ADD COLUMN feedback VARCHAR(16) DEFAULT NULL COMMENT '用户反馈: LIKE/DISLIKE';
ALTER TABLE ai_message ADD COLUMN feedback_content VARCHAR(512) DEFAULT NULL COMMENT '反馈内容';
```

### 4.2 后端实现

- **实体**：`AiMessage` 新增 `feedback` 和 `feedbackContent` 字段
- **Controller**：`AiChatController` 新增端点

```java
@PutMapping("/message/{messageId}/feedback")
public R<Void> feedback(@PathVariable Long messageId, @RequestParam String feedback) {
    // feedback: LIKE / DISLIKE / NONE（取消反馈）
    // 校验消息归属后更新
}
```

### 4.3 前端实现

- 每条 AI 消息下方显示 👍/👎 按钮（hover 时显示，点击切换）
- 点击已选中的反馈可取消
- `chat.ts` 新增 `messageFeedback(messageId, feedback)` API

---

## Task 5：工具执行过程可视化（P2）

### 5.1 实现方式

采用内联方式在聊天页面 `index.vue` 中直接渲染工具调用过程（而非独立组件），与 Task 2.7 前端适配合并实现。

### 5.2 SSE 消息格式

```json
// AI_TOOL_START
{ "type": "AI_TOOL_START", "content": "{\"toolName\":\"queryLowStockMaterials\",\"arguments\":\"{}\"}" }

// AI_TOOL_END
{ "type": "AI_TOOL_END", "content": "{\"toolName\":\"queryLowStockMaterials\",\"duration\":1200,\"resultSummary\":\"3 条记录\"}" }

// AI_AGENT_THINKING
{ "type": "AI_AGENT_THINKING", "content": "{\"agentName\":\"SupervisorAgent\",\"message\":\"开始分析您的问题...\"}" }
```

### 5.3 前端渲染

在 AI 消息气泡中展示：
- **Agent 思考过程**：黄色背景提示条，显示 Agent 当前思考内容
- **工具调用卡片**：
  - 运行中：蓝色边框 + Loading 图标 + 工具名
  - 已完成：绿色边框 + Check 图标 + 工具名 + 耗时 + 结果摘要
- **消息内容**：Markdown 渲染的 AI 最终回答

---

## 文件变更清单

### 新增文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `McpClientManager.java` | ruoyi-ai/mcp/service | MCP 客户端管理器（创建/缓存/熔断） |
| `ToolProviderFactory.java` | ruoyi-ai/mcp/service | 统一工具工厂（BUILTIN + MCP） |
| `McpToolTestResult.java` | ruoyi-ai/mcp/dto | 工具测试结果 DTO |
| `OutputChannel.java` | ruoyi-ai/observability | 跨线程事件总线 |
| `AgentStreamHelper.java` | ruoyi-ai/observability | Agent 流式事件推送工具 |
| `StreamingToolExecutedListener.java` | ruoyi-ai/observability | 带 OutputChannel 的 Tool 执行监听器 |

### 修改文件

| 文件 | 变更内容 |
|------|----------|
| `AiMessage.java` | 新增 feedback、feedbackContent 字段 |
| `AiTool.java` | 新增 configJson 字段（第五周已完成） |
| `McpToolServiceImpl.java` | 新增 testTool() + mcpClientManager 集成 |
| `McpToolController.java` | 新增 /{id}/test 端点 |
| `BuiltinToolProvider.java` | 新增 getToolClass() 方法 |
| `BuiltinToolRegistry.java` | 新增 getEnabledBuiltinToolObjects() + AiToolMapper 依赖 |
| `7个 ToolProvider 实现` | 全部添加 getToolClass() 方法 |
| `AiServiceFacade.java` | 工具注入改为动态加载 + OutputChannel 流式推送 + drain 事件路由 + AiToolExecutedListener ThreadLocal |
| `AiToolExecutedListener.java` | 新增 ThreadLocal OutputChannel 支持，工具执行事件推送 |
| `AiSseMessageHelper.java` | 新增 TYPE_TOOL_START/TYPE_TOOL_END/TYPE_AGENT_THINKING 及发送方法 |
| `AiChatController.java` | 新增消息反馈端点 PUT /message/{id}/feedback |
| `pom.xml`（ruoyi-ai） | 新增 langchain4j-mcp 依赖 |
| `mcp.ts` | 新增 testTool API |
| `chat.ts` | 新增 messageFeedback API |
| `index.vue`（MCP 管理） | 测试连接按钮 + configJson 编辑 + LOCAL/REMOTE 类型 |
| `index.vue`（聊天页面） | SSE 新类型处理 + 工具调用渲染 + Agent 思考过程 + 反馈按钮 |

### SQL 变更

| 文件 | 说明 |
|------|------|
| `ry-vue.sql` | ai_message 表新增 feedback、feedback_content 字段 |

---

## 实际完成记录

### Task 1：MCP 外部工具动态接入 ✅

- 1.1 ToolProviderFactory 创建完成
- 1.2 McpClientManager 创建完成（REMOTE/LOCAL 两种传输 + 熔断降级 + Windows 命令兼容）
- 1.3 AiTool.configJson 已在第五周完成
- 1.4 McpToolServiceImpl 扩展完成（testTool + refreshClient）
- 1.5 McpToolController 新增 /{id}/test 端点
- 1.6 前端对接完成（测试连接按钮 + configJson 编辑 + LOCAL/REMOTE 类型）

### Task 2：Agent 流式推送 ✅

- 2.1 OutputChannel 创建完成（BlockingQueue 4096 + 非阻塞写入 + 阻塞消费）
- 2.2 AgentStreamHelper 创建完成（sendToolStart/sendToolEnd/sendThinking/sendError）
- 2.3 StreamingToolExecutedListener 创建完成（ToolExecutedEventListener + OutputChannel）
- 2.4 AiSseMessageHelper 扩展完成（3 个新消息类型 + 发送方法）
- 2.5 AiChatController SSE 流式端点：采用已有 SSE 基础设施（Redis Pub/Sub），无需新建端点
- 2.6 AiServiceFacade 重构完成（动态工具加载 + OutputChannel + drain 事件路由 + ThreadLocal）
- 2.7 前端适配完成（AI_TOOL_START/AI_TOOL_END/AI_AGENT_THINKING 处理 + 工具调用卡片渲染）

### Task 3：工具状态动态过滤 ✅

- 3.1 AiServiceFacade 工具注入改为 ToolProviderFactory 动态加载
- 3.2 BuiltinToolRegistry 扩展完成（getEnabledBuiltinToolObjects 根据 ai_tool 表 status 过滤）
- 3.3 AiToolInitializer 同步逻辑：保留已有工具 status

### Task 4：对话质量评估 ✅

- 4.1 ai_message 表新增 feedback/feedback_content 字段
- 4.2 后端：AiMessage 实体扩展 + AiChatController 反馈 API
- 4.3 前端：👍/👎 按钮（hover 显示 + 点击切换 + 取消反馈）

### Task 5：工具执行过程可视化 ✅

- 与 Task 2.7 合并实现，内联渲染在聊天页面中
- Agent 思考过程（黄色提示条）+ 工具调用卡片（运行中/已完成状态）

---

## 依赖关系与执行顺序

```
Task 1 (MCP 外部工具接入)
  ├── 1.1 ToolProviderFactory ←── 依赖 1.2 McpClientManager
  ├── 1.2 McpClientManager ←── 依赖 AiTool.configJson
  ├── 1.3 AiTool 扩展
  ├── 1.4 McpToolServiceImpl 扩展
  ├── 1.5 McpToolController 扩展
  └── 1.6 前端对接

Task 2 (Agent 流式推送) ←── 可与 Task 1 并行
  ├── 2.1 OutputChannel
  ├── 2.2 AgentStreamListener
  ├── 2.3 McpToolExecutionListener
  ├── 2.4 AiSseMessageHelper 扩展
  ├── 2.5 AiChatController SSE 端点
  ├── 2.6 AiServiceFacade 重构 ←── 依赖 2.1 + 2.2
  └── 2.7 前端适配

Task 3 (工具状态动态过滤) ←── 依赖 Task 1.1 ToolProviderFactory
  ├── 3.1 AiServiceFacade 改造 ←── 与 Task 2.6 合并
  ├── 3.2 BuiltinToolRegistry 扩展
  └── 3.3 AiToolInitializer 增强

Task 4 (对话质量评估) ←── 独立，可并行
  ├── 4.1 数据库表
  ├── 4.2 后端实现
  └── 4.3 前端实现

Task 5 (工具执行可视化) ←── 依赖 Task 2
  ├── 5.1 消息气泡增强
  ├── 5.2 SSE 消息格式
  └── 5.3 前端组件
```

**建议执行顺序**：
1. Day 1-2：Task 1（MCP 外部工具接入）+ Task 4（对话质量评估）并行
2. Day 3-4：Task 2（Agent 流式推送）
3. Day 5：Task 3（工具状态动态过滤，与 Task 2.6 合并）+ Task 5（工具执行可视化）

---

## 验收标准

| Task | 验收标准 |
|------|----------|
| Task 1 | 1) 前端新增 REMOTE 类型 MCP 工具，填写 baseUrl，测试连接成功<br>2) Agent 对话时能调用外部 MCP 工具<br>3) 熔断机制：连续 3 次失败后自动禁用 5 分钟 |
| Task 2 | 1) Agent 模式下前端实时看到工具调用过程<br>2) Agent 最终回答以 Token 级流式输出<br>3) 工具调用超时/失败时前端收到错误提示 |
| Task 3 | 1) MCP 管理页面禁用某工具后，Agent 不再调用该工具<br>2) 新增外部 MCP 工具后无需重启即可被 Agent 使用 |
| Task 4 | 1) 每条 AI 消息下方有点赞/点踩按钮<br>2) 点踩可填写原因<br>3) 管理端可查看反馈统计 |
| Task 5 | 1) Agent 回复中展示工具调用过程（可折叠）<br>2) 显示工具名、耗时、结果摘要 |

---

## 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| LangChain4j AgenticServices 不支持 McpToolProvider 动态注入 | Task 1 核心功能受阻 | 查阅 langchain4j-agentic 最新 API，必要时降级为手动构建 ToolSpecification |
| SupervisorAgent 不支持 AgentListener | Task 2 流式推送受阻 | 参考 ruoyi-ai 的 SupervisorStreamListener 实现，确认 langchain4j 版本兼容性 |
| STDIO 方式在 Windows 下命令兼容性问题 | LOCAL 类型 MCP 工具无法启动 | 参考 ruoyi-ai 的 resolveCommand() 方法，自动追加 .cmd 后缀 |
| OutputChannel 阻塞队列溢出 | 事件丢失 | 队列容量 4096 + 非阻塞写入（offer 超时）+ 溢出日志告警 |

---

## 第七周展望

1. **Text2SQL 准确率优化**：根据第六周实际测试结果，调整 Schema 摘要策略和 SystemMessage
2. **知识库自动导入接口**：实现 API 自动导入预置 ERP 操作文档
3. **多模型 A/B 测试**：支持同一问题对比不同模型回答质量
4. **对话导出**：支持导出聊天记录为 Markdown/PDF
5. **Prompt 模板管理**：可配置的 SystemMessage 模板，不同场景使用不同提示词
