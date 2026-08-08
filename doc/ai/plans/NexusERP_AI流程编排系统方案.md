# NexusERP 自研类 n8n AI 流程编排系统 — 方案设计（v1.0）

> **文档状态**: 待审批
> **创建日期**: 2026-08-05
> **决策来源**: 用户意图（参考 n8n 自研 AI 流程编排）+ 代码级探索（NexusERP ruoyi-ai 底座）+ n8n 源码分析（D:\n8n\n8n-master v2.34.0，三包深入）
> **预估工期**: 8-10 人周（MVP）
> **关联文档**: [NexusERP_AI开发计划.md](./NexusERP_AI开发计划.md)、[NexusERP_AI二阶段开发计划_AI平台能力深化.md](./NexusERP_AI二阶段开发计划_AI平台能力深化.md)

---

## 一、背景与目标

### 1.1 问题陈述

用户希望参考 n8n 开源项目，在 NexusERP 中构建**自有的 AI 流程编排能力**（可视化节点画布 + 图执行引擎），而非直接部署 n8n 本体。原因：

1. **技术栈隔离**：n8n 基于 Node.js + BullMQ，直接嵌入 Java 单体需双进程 + 双栈维护，构建链（mvn + npm workspace）明显变重，且 n8n 周更节奏不适合同仓发版
2. **定位差异**：NexusERP 已内嵌 Flowable 7.0.0 完成审批闭环（16 个 /workflow/* Controller、15 个业务单据接入、bpmn-js 设计器），n8n 的定位应为**跨系统集成 + AI 编排**，而非替代审批核心
3. **能力已有底座**：`ruoyi-ai` 模块已具备 n8n"AI 部分"的大部分能力（见 1.2），缺的只是"流程部分"

### 1.2 可行性结论（代码级验证）

**完全可行。** n8n 的 AI 编排能力拆解后与现有底座对比：

| n8n 能力 | NexusERP 现状（已代码级验证） | 差距 |
|---|---|---|
| 多厂商 LLM 节点 | ✅ `ChatModelFactory`（8 provider + Caffeine 缓存，DB 可配置） | 无 |
| Agent 节点 | ✅ `AgenticServices.agentBuilder`（langchain4j-agentic 1.18.0-beta28 已在依赖） | 无，但硬编码在 `AiServiceFacade.invokeWithAgent` |
| 工具节点库 | ✅ `BuiltinToolRegistry` + `ai_tool` 表 DB 可配置 + MCP 外部工具 | 无 |
| RAG / 记忆 / 多租户 / SSE | ✅ `RetrieverService`、`PersistentChatMemoryStore`、`AiRequestContext` + `TenantHelper.dynamic`、Redis 广播 SSE | 无 |
| **流程定义 + 图执行引擎 + 可视化画布 + 运行历史** | ❌ 完全没有 | **全部核心工作量** |

### 1.3 已确认决策

| 决策项 | 结论 |
|---|---|
| 路线 | **直接自研 MVP**（不部署 n8n，参考其设计模式） |
| 画布 | 引入 `@vue-flow/core`（MIT，n8n 同款库） |
| MVP 节点 | LLM / Agent / 条件(IF) / 代码(SpEL) / RAG 全选 |
| 执行模型 | 就绪队列 + MVP 串行（架构预留并行） |
| 触发方式 | 手动 + SnailJob 定时（Webhook 延后，flow_key 已预留） |
| 与 Flowable 关系 | 审批闭环不动；本系统为补充性 AI 编排层 |

### 1.4 定位说明

- **不替代 Flowable**：审批（发起/待办/审批/回调闭环）继续走 Flowable
- **补充性编排层**：AI 流程编排（LLM 调用、工具自主调用、RAG、条件分支、数据转换）
- **目标场景示例**：查库存 → 条件判断 → 生成报告 → 推送；定时巡检 → LLM 摘要 → 通知；知识库检索 → 摘要 → 分发给各业务角色

---

## 二、总体架构

```
plus-ui (Vue3 + @vue-flow/core 画布)
   │  API: /ai/flow/**           SSE: /resource/sse(已有) → ai-sse-message 总线(复用)
   ▼
ruoyi-admin 单体
 ├─ ruoyi-ai 模块 com.nexus.ai.flow.*
 │    ├─ FlowNodeRegistry        (NodeProvider 模式，类比 BuiltinToolRegistry)
 │    ├─ FlowEngineImpl          (BFS 拓扑就绪队列 + MVP 串行 + IF 按 sourceHandle 选边)
 │    ├─ 节点: Llm/Agent/Rag/If/Code NodeProvider
 │    ├─ FlowState               (JSON 全局状态 + {{path}} 模板渲染)
 │    ├─ AiFlowScheduleJob       (@JobExecutor, SnailJob 定时)
 │    └─ 复用: ChatModelFactory / ToolProviderFactory / RetrieverService / AgenticServices / AiRequestContext / AiSseMessageHelper
 └─ ruoyi-common-ai 实体/Mapper/Service 接口 (与 AiSession/AiMessage 同约定)
```

---

## 三、n8n 源码分析结论（设计依据）

对 `D:\n8n\n8n-master`（v2.34.0）三个包做了代码级分析：`packages/workflow`（数据模型+表达式）、`packages/core`（执行引擎）、`packages/@n8n/nodes-langchain` + `packages/frontend/editor-ui`（AI 节点+画布）。以下结论**直接修正/确认了本方案设计**。

### 3.1 关键确认（n8n 本体行为 = 本方案已定决策）

1. **执行内严格串行是 n8n 本体行为**：`WorkflowExecute.processRunExecutionData` 是单 while 循环、一次一个节点（workflow-execute.ts L1659）；"并行分支"只是栈 + waitingExecution 表实现的**分叉顺序执行**（v1 模式还按画布位置排序，左上优先）。→ 我们 MVP 串行完全对齐。真正的并行在 n8n 也是执行级（queue 模式多 worker），不在图内
2. **分支路由 = 输出端口 index**：`IConnections[源][type][输出index] = IConnection[]`，一个输出端口可连多下游；If 节点 `outputs:[Main, Main]` + `outputNames:['true','false']`，返回 `[trueItems, falseItems]` 数组下标即端口。→ 我们的 sourceHandle 方案一致（简化版）
3. **空分支自动截断**：`nodeSuccessData[outputIndex]` 为空则不扇出该分支（L2253）。→ 等价于我们的 SKIPPED 语义
4. **取消是协作式**：AbortController + 每轮循环检查 + 节点执行完后停下。→ 我们的 FlowStopRegistry 一致；同步 LLM 无法中断这点 n8n 也类似
5. **坐标直接存节点上**：`INode.position: [number, number]`，无单独序列化层。→ 我们的 config_json 已如此

### 3.2 设计修正（采纳 n8n 机制）

| # | n8n 机制（源码位置） | 对自研方案的修正 |
|---|---|---|
| A | **节点失败三态**：`onError: 'continueRegularOutput'(输入透传继续) \| 'continueErrorOutput'(错误端口) \| 'stopWorkflow'`（L988-993, L2055-2136） | **MVP 增加 onError 配置**：`STOP`(默认) \| `CONTINUE`(输入透传继续)。错误端口模式留 Phase 2 |
| B | **参数 schema 驱动表单**：`INodeProperties[] {name, type, default, options, required, displayOptions}` + `ParameterInputFull.vue` 按 type switch 渲染 | **/node-types 返回 properties 元数据，前端 1 个通用 `NodeParameterForm` 渲染全部节点配置**。5 个手写属性表单 → 1 个 schema 驱动渲染器 |
| C | **表达式缺失语义分层**：属性缺失 → 静默 undefined 渲染 `''`；数据源缺失 → 抛结构化 `ExpressionError`（type='no_execution_data' + messageTemplate） | `{{path}}` 渲染器对齐此分层：**节点不存在/未执行 → 抛错（带节点名与提示）**；**存在节点的属性缺失 → 渲染空串** |
| D | **执行事件带单调序号**：`nodeEventSequence` 防乱序覆盖（cli execution-lifecycle-hooks.ts L272） | SSE 事件 payload 加 `seq`（instance 内自增）；前端按 instanceId 过滤后只接受更大 seq |
| E | **重试参数**：`retryOnFail + maxTries(clamp 2-5, 默认3) + waitBetweenTries(clamp 0-5000, 默认1000)`（L1797-1809）；"返回含 error 的 item 也视为失败"（checkFailure L1791） | 节点级加 `retryOnFail`(默认 false) + `retryMaxTries` + `waitBetweenTries` |
| F | **handleId 编码**：`{mode}/{type}/{index}` 如 `outputs/main/0`、`inputs/ai_tool/1`（canvas.utils.ts） | config_json 的 sourceHandle/targetHandle 用 `outputs/main/0`、`outputs/if/true` 编码；前端 handle 组件解析此编码 |
| G | **前端受控模式 + slot 节点**：`apply-changes=false` 完全受控；节点通过 slot 注册；运行态 running = 旋转渐变边框（CSS @property + keyframes 1.5s）；边状态按 `['running','error','success']` 优先级着色 | 前端照此：store 单向下发 nodes/edges、slot 注册自定义节点、CSS 动画、边按 `['running','failed','succeeded','skipped']` 着色 |

### 3.3 明确不采纳（有充分理由）

- **supplyData 槽位连接**（LLM/工具/记忆/向量库作"供应节点"，`getInputConnectionData` 拉取注入）：n8n AI 编排的精髓，但**我们的模型/知识库是 DB 配置引用（modelId 配置项），不是画布节点**——self-contained 节点 + 配置引用更贴合现有底座。若将来要"画布内多模型切换/记忆节点"，Phase 2 再引入槽位连接（`INodeInputConfiguration {type, required, maxConnections}` 概念已记录）
- **Task Runner / node:vm 沙箱执行 Code**：n8n 用独立进程 + `node:vm`（js-task-runner.ts），Java 侧无对应物；SpEL 只读决策不变
- **waiting/恢复机制**（waitTill + 快照续跑 + 签名恢复 URL）：无等待节点需求，node_run 表记录已够
- **flatted 快照持久化**：n8n 整份 runExecutionData 序列化——我们按行存 ai_flow_instance + ai_flow_node_run 更符合 Java/MyBatis 生态
- **JMESPath / 表达式编译缓存**：JS 生态产物；Java 侧 SpEL 求值即可

### 3.4 记录在案的边界能力（MVP 不做，将来参考）

- 多输入 join 节点（Merge）的 waitingExecution 表（L430-841）
- 节点 `alwaysOutputData`（无输出时输出 `[{json:{}}]` 保持分支不断）
- 执行级重试 = 用保存的栈快照从失败节点续跑（execution.service.ts retry()）
- 执行数据展示：NDV 双击节点看 runData——我们的 monitor 节点表已覆盖等价能力
- 数据保留：软删 + 定时硬删（pruneDataMaxAge 336h / maxCount 10000）——ai_flow_instance 将来加清理任务时对齐

---

## 四、数据模型

### 4.1 建表 SQL（落地文件：`script/sql/update/2026-08-xx-ai-flow.sql`）

遵循项目表风格：雪花 ID（Java 侧赋值）、`tenant_id varchar(20) NOT NULL DEFAULT '000000'`、`del_flag char(1) DEFAULT '0'`、create_dept/create_by/create_time/update_by/update_time、attr1-4 预留、utf8mb4_unicode_ci。

```sql
USE `ry-vue`;

-- 流程定义表
DROP TABLE IF EXISTS `ai_flow`;
CREATE TABLE `ai_flow` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `flow_name` varchar(100) NOT NULL COMMENT '流程名称',
  `flow_key` varchar(64) NOT NULL COMMENT '流程标识(唯一, 定时/Webhook 引用)',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/OFFLINE',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '版本号(每次发布+1)',
  `config_json` longtext NULL COMMENT '画布配置(JSON: nodes+edges)',
  `trigger_type` varchar(50) DEFAULT 'MANUAL' COMMENT '触发方式: MANUAL/SCHEDULE (逗号分隔可多选)',
  `cron_expr` varchar(50) DEFAULT NULL COMMENT '定时 Cron 表达式',
  `max_retry` int(11) NOT NULL DEFAULT 1 COMMENT '节点失败最大重试次数',
  `create_dept` bigint(20) DEFAULT NULL,
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `attr1` varchar(256) DEFAULT NULL, `attr2` varchar(256) DEFAULT NULL,
  `attr3` varchar(256) DEFAULT NULL, `attr4` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_flow_key` (`flow_key`) USING BTREE,
  INDEX `idx_tenant_status` (`tenant_id`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程定义' ROW_FORMAT = DYNAMIC;

-- 运行实例表
DROP TABLE IF EXISTS `ai_flow_instance`;
CREATE TABLE `ai_flow_instance` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `flow_id` bigint(20) NOT NULL COMMENT '流程ID',
  `flow_version` int(11) NOT NULL DEFAULT 1 COMMENT '运行时的流程版本',
  `trigger_type` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式: MANUAL/SCHEDULE',
  `trigger_by` bigint(20) DEFAULT NULL COMMENT '触发人(定时任务=流程创建人, 用于SSE通知)',
  `input_json` longtext NULL COMMENT '输入(JSON)',
  `output_json` longtext NULL COMMENT '输出(JSON, 末节点输出)',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCEEDED/FAILED/STOPPED',
  `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '总耗时(毫秒)',
  `node_total` int(11) DEFAULT 0 COMMENT '节点总数',
  `node_success` int(11) DEFAULT 0 COMMENT '成功节点数',
  `node_failed` int(11) DEFAULT 0 COMMENT '失败节点数',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_dept` bigint(20) DEFAULT NULL, `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL, `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL, `del_flag` char(1) DEFAULT '0',
  `attr1` varchar(256) DEFAULT NULL, `attr2` varchar(256) DEFAULT NULL,
  `attr3` varchar(256) DEFAULT NULL, `attr4` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id` (`flow_id`, `id`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程运行实例' ROW_FORMAT = DYNAMIC;

-- 节点运行记录表
DROP TABLE IF EXISTS `ai_flow_node_run`;
CREATE TABLE `ai_flow_node_run` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `instance_id` bigint(20) NOT NULL COMMENT '实例ID',
  `flow_id` bigint(20) NOT NULL COMMENT '流程ID(冗余, 便于查询)',
  `node_id` varchar(64) NOT NULL COMMENT '画布节点ID(如 node_1)',
  `node_name` varchar(100) DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(30) NOT NULL COMMENT '节点类型: LLM/AGENT/RAG/IF/CODE',
  `status` varchar(20) NOT NULL COMMENT '状态: PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED',
  `input_json` longtext NULL COMMENT '输入(JSON, 渲染后的最终入参)',
  `output_json` longtext NULL COMMENT '输出(JSON)',
  `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时(毫秒)',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_dept` bigint(20) DEFAULT NULL, `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL, `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL, `del_flag` char(1) DEFAULT '0',
  `attr1` varchar(256) DEFAULT NULL, `attr2` varchar(256) DEFAULT NULL,
  `attr3` varchar(256) DEFAULT NULL, `attr4` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_instance_id` (`instance_id`) USING BTREE,
  INDEX `idx_flow_id` (`flow_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程节点运行记录' ROW_FORMAT = DYNAMIC;
```

### 4.2 config_json 契约（前端 vue-flow 与后端引擎的协议）

```json
{
  "nodes": [
    {
      "id": "node_llm_1",
      "type": "llm",                       // llm | agent | rag | if | code
      "position": [120, 80],               // vue-flow 坐标（对齐 n8n INode.position，零序列化成本）
      "data": {
        "label": "生成缺货摘要",
        "onError": "STOP",                 // STOP(默认) | CONTINUE(输入透传继续)
        "retryOnFail": false,              // 节点级重试开关（默认 false）
        "retryMaxTries": 3,                // clamp 2-5，默认 3
        "waitBetweenTries": 1000,          // clamp 0-5000ms，默认 1000
        "config": { "modelId": 123, "promptTemplate": "请总结：{{nodes.node_rag_1.output.content}}" }
      }
    }
  ],
  "edges": [
    {
      "id": "edge_1",
      "source": "node_rag_1",
      "target": "node_llm_1",
      "sourceHandle": "outputs/main/0",    // 对齐 n8n {mode}/{type}/{index} 编码；IF 节点用 "outputs/if/true"|"outputs/if/false"
      "targetHandle": "inputs/main/0"
    }
  ]
}
```

### 4.3 变量引用语法（全节点统一）

`{{path}}`，path 支持点号 + 数组下标：
- `{{input.xxx}}` — 本次运行入参
- `{{nodes.<nodeId>.output.xxx}}` / `{{<nodeId>.output.xxx}}`（简写）— 前序节点输出

**缺失语义分层（对齐 n8n §3.2-C）**：
- 路径中**节点不存在/未执行** → 抛结构化错误（带节点名 + messageTemplate，如"节点 X 未执行，请检查前置节点或连线"）
- **已存在节点的属性缺失** → 渲染空串（不报错）

---

## 五、后端执行引擎（com.nexus.ai.flow.*）

### 5.1 包结构

```
ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/flow/
├── model/          FlowGraph.java, FlowNodeDef.java, FlowEdgeDef.java
├── state/          FlowState.java, FlowTemplateRenderer.java
├── node/           FlowNodeProvider.java, FlowNodeRegistry.java,
│                   LlmNodeProvider.java, AgentNodeProvider.java, RagNodeProvider.java,
│                   IfNodeProvider.java, CodeNodeProvider.java
├── expr/           FlowConditionEvaluator.java (IF/边条件 DSL), SpelFlowExpressionEvaluator.java (CODE 节点)
├── engine/         FlowEngine.java(接口), FlowEngineImpl.java, FlowNodeRunner.java, FlowStopRegistry.java
├── schedule/       AiFlowScheduleJob.java
├── controller/     AiFlowController.java, AiFlowInstanceController.java
└── dto/            FlowRunRequest.java
```

实体/Mapper/Service 接口放 `ruoyi-common-ai`（与 AiSession/AiMessage 同约定）：
`com.nexus.common.ai.domain.entity.{AiFlow, AiFlowInstance, AiFlowNodeRun}`、`domain.bo.AiFlowBo`、`domain.vo.{AiFlowVo, AiFlowInstanceVo, AiFlowNodeRunVo}`、`mapper.{AiFlowMapper, AiFlowInstanceMapper, AiFlowNodeRunMapper}`、`service.{IAiFlowService, IAiFlowInstanceService}`。

### 5.2 NodeProvider 模式（照抄 BuiltinToolRegistry）

```java
public interface FlowNodeProvider {
    /** 节点类型标识: "llm"/"agent"/"rag"/"if"/"code" */
    String getNodeType();
    /** 节点显示名（前端面板用） */
    String getDisplayName();
    /** 参数 schema（对齐 n8n INodeProperties），前端表单驱动 + 发布校验 */
    List<NodeParamSchema> getParamSchemas();
    /** 发布/保存时校验配置, 非法抛 IllegalArgumentException */
    void validateConfig(JsonNode config);
    /** 执行节点, 返回输出 JSON; 抛异常则节点 FAILED */
    JsonNode execute(FlowExecutionContext ctx);
}

/** 执行上下文: 节点读写状态 + 访问实例信息 */
public class FlowExecutionContext {
    AiFlowInstance instance;
    String nodeId; String nodeName;
    JsonNode config;
    FlowState state;                     // 读写全局状态
    AiFlowNodeRun nodeRun;               // 当前节点运行记录(引擎已置 RUNNING)
    Map<String,Object> extension;        // 预留
}

/** 参数 schema（对齐 n8n INodeProperties） */
public class NodeParamSchema {
    String name;                         // 参数名
    String label;                        // 显示名
    String type;                         // string | number | boolean | options | textarea | json
    String defaultValue;
    List<Map<String,String>> options;    // options 类型的候选 {name, value}
    boolean required;
    String placeholder;
}
```

```java
/** 注册中心: @PostConstruct 收集所有 FlowNodeProvider Bean, 类比 BuiltinToolRegistry.init() */
@Component
public class FlowNodeRegistry {
    private final List<FlowNodeProvider> providers;
    private final Map<String, FlowNodeProvider> registry = new ConcurrentHashMap<>();
    @PostConstruct public void init() { /* name 冲突告警 + 注册 */ }
    public FlowNodeProvider getProvider(String nodeType) { ... }
    public Collection<FlowNodeProvider> getAll() { ... }   // 供 GET /ai/flow/node-types
}
```

### 5.3 图解析与执行调度

```java
/** 不可变图模型: 由 config_json 解析, 发布时校验 */
public class FlowGraph {
    Map<String, FlowNodeDef> nodes;
    Map<String, FlowEdgeDef> edges;
    Map<String, List<FlowEdgeDef>> outEdges;     // nodeId -> 出边(有序)
    Map<String, Integer> inDegree;               // 静态入度
    List<String> startNodeIds;                   // 入度 0 的节点
    /** 拓扑排序(含环检测), 有环抛 IllegalStateException */
    List<String> topologicalOrder();
    /** 校验: JSON合法 / 节点类型已注册 / 边两端节点存在 / 至少1个起点 / 无环 / 各节点config通过validateConfig */
    void validate();
}

public enum FlowNodeStatus { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }
```

**引擎调度（FlowEngineImpl）**：

```java
public interface FlowEngine {
    /** 异步启动流程(aiExecutor), 返回 instanceId; input 为运行入参 */
    Long start(AiFlow flow, JsonNode input, String triggerType, String tenantIdStr, Long userId, String userName);
    /** 置停止标志; 引擎在节点边界检查 */
    void stop(Long instanceId);
}
```

`FlowEngineImpl.start` 步骤：

1. 建 instance(RUNNING) 落库（写库包 `TenantHelper.dynamic`，本项目已知坑）
2. 解析 FlowGraph + validate()（JSON 合法/节点类型已注册/边两端节点存在/至少 1 起点/无环/各 config 通过 validateConfig）
3. `CompletableFuture.runAsync(aiExecutor)`：`AiRequestContext.set(tenantId, userId, userName, ...)`（流程级 set，**节点内禁止 clear**，LLM 的 token 采集依赖 AiChatModelListener）→ **单 while 循环出队（对齐 n8n processRunExecutionData L1659）**：
   - 队首出队 → `runNodeWithRetry` → 写 node_run（SUCCEEDED/FAILED）+ SSE 事件
   - 按 IF 结果 / onError 选边：选中边的下游入度 -1，为 0 入队；未选中分支节点标记 SKIPPED
   - stop 标志在每次出队时检查 → 剩余节点 SKIPPED，instance STOPPED
4. 终态落库（SUCCEEDED/FAILED/STOPPED + 汇总计数 + duration_ms）→ `finally AiRequestContext.clear()`

**onError 三态（对齐 n8n §3.2-A）**：

| 配置 | 行为 | 说明 |
|---|---|---|
| `STOP`（默认） | 节点 FAILED → 后续节点全 SKIPPED，instance FAILED | n8n 默认 stopWorkflow |
| `CONTINUE` | 节点 FAILED → **输入数据透传作为输出**，流程继续 | n8n continueRegularOutput（L2081-2095） |
| ERROR_PORT | 错误数据走最后一个输出端口 | 留 Phase 2（n8n continueErrorOutput） |

**重试（对齐 n8n §3.2-E）**：节点级 `retryOnFail`（默认 false）+ `retryMaxTries`（clamp 2-5，默认 3）+ `waitBetweenTries`（clamp 0-5000ms，默认 1000ms）；flow 级 `max_retry` 保留作兜底。重试在同一节点 run 记录内（retry_count 递增）。

**护栏**：
- 节点执行总次数上限 `节点数 × (maxRetry + 1) + 1`（防拓扑缺陷死循环，对齐 n8n L1754 "endless loop" 检测）
- 每节点 `Future.get(timeoutSeconds)`（默认 600s）
- 未闭合分支（IF 未连边）不阻塞，直接截断
- 不可达节点（无入边且非起点）直接 SKIPPED

### 5.4 数据流模型（FlowState + 模板渲染）

```java
/** 全局状态: 结构: {input:..., nodes:{nodeId:{output:...}}} */
public class FlowState {
    private final ObjectNode root;                       // Jackson
    public JsonNode get(String path);                    // "nodes.node_1.output.content", 支持 arr[0]
    public void putNodeOutput(String nodeId, JsonNode output);
    public JsonNode getInput();
    public JsonNode getFinalOutput();                    // 末个 SUCCEEDED 节点的 output
}

/** 模板渲染: 正则匹配 {{...}}, 缺失语义分层见 §4.3 */
public class FlowTemplateRenderer {
    public static String render(String template, FlowState state, String contextLabel);
}
```

**循环**：MVP 不支持显式循环节点；"循环"由外部触发（多次 run / 定时调度）承担。

### 5.5 执行上下文与多租户（本项目已知坑，必须照做）

- `start()` 入口捕获 `LoginHelper.getTenantId()`（String）与 userId/userName；异步线程内 `AiRequestContext.set(...)`，`finally` 中 `clear()`。**所有写库一律 `TenantHelper.dynamic(tenantIdStr, () -> mapper.insert(...))`**（参考 `AiServiceFacade.saveAiMessageFromContext` L712 的既有写法）
- **节点执行在 aiExecutor 线程内同步进行**，`AiRequestContext` 对整条流程有效（LLM 节点的 AiChatModelListener token 采集依赖它），**不允许在节点内部 clear()**，只在流程级 finally 清理
- **定时触发时无登录态**：`AiRequestContext.set(instance.getTriggerBy(), ...)`（trigger_by 存流程创建人）；写库同样 dynamic

### 5.6 停止/取消（对齐 n8n 协作式取消）

```java
/** 内存停表: instanceId -> AtomicBoolean; 引擎出队时与节点执行前检查 */
@Component public class FlowStopRegistry {
    private final ConcurrentHashMap<Long, AtomicBoolean> flags;
    public void register(Long instanceId); public void unregister(Long instanceId);
    public boolean requestStop(Long instanceId);
    public boolean isStopped(Long instanceId);
}
```

`POST /stop` → 置标志 + 更新 instance 状态为 STOPPING（终态 STOPPED 由引擎落定）；LLM 调用无法中断（同步 ChatModel），实际是**节点边界停止**，文档与 UI 如实说明。实例结束（无论结局）后 unregister 防泄漏（参照 AiThreadPoolConfig 定时清理 OutputChannel 的做法，可加 @Scheduled 兜底清理）。

### 5.7 SSE 事件（扩展 AiSseMessageHelper，复用既有总线）

`AiSseMessageHelper` 新增 3 个方法/常量（保持静态风格）：

| 常量 | payload |
|---|---|
| `AI_FLOW_STATUS` | `{instanceId, flowId, status, seq, errorMsg?, durationMs?}` |
| `AI_FLOW_NODE_START` | `{instanceId, nodeId, nodeType, nodeName, retryCount, seq}` |
| `AI_FLOW_NODE_END` | `{instanceId, nodeId, status, durationMs, outputSummary(截断500), errorMsg?, seq}` |

- **seq**：instance 内单调递增（对齐 n8n nodeEventSequence，防 Redis Pub/Sub 多节点乱序）
- 前端 `realtimeDispatch.ts` 已对所有 `AI_` 前缀类型派发 `ai-sse-message` CustomEvent，**前端总线零改动**

### 5.8 触发方式

| 触发 | MVP | 说明 |
|---|---|---|
| 手动 POST /run | ✅ | 主路径 |
| SnailJob 定时 | ✅ | `ruoyi-ai/pom.xml` 加 `ruoyi-common-job` 依赖（snail-job-client 在其中）；`AiFlowScheduleJob` 用 `@JobExecutor(name="aiFlowJob")` + `jobExecute(JobArgs)` 从 `jobArgs.getJobParams()` 解析 `flowId` 与入参；SnailJob 控制台配 cron，MVP 不做 cron 管理 UI；实例 trigger_type=SCHEDULE |
| Webhook | ❌ 延后 | 需公网端点 + 鉴权签名 + 调用方管理 UI + 防重放；`flow_key` 已预留，未来 `/ai/flow/webhook/{flowKey}` 一行接入，无 schema 迁移成本 |

---

## 六、节点类型详细契约

统一约定：节点输入 = `config`（静态配置）+ 运行态渲染（`{{path}}` 模板）；输出 = JsonNode，引擎写入 `state.nodes.<nodeId>.output`。

### 6.1 LLM 节点（type=llm）

```json
// config
{
  "modelId": 123,
  "promptTemplate": "请分析：{{nodes.node_rag_1.output.content}}",
  "systemPrompt": "你是一个 ERP 分析助手",     // 可选
  "temperature": 0.7,                          // 可选, 缺省走 ChatModelFactory 默认
  "timeoutSeconds": 300                        // 可选
}
// 输出
{
  "content": "…LLM 回复全文…",
  "tokenUsage": { "inputTokens": 128, "outputTokens": 320 },
  "modelId": 123
}
```

实现：`chatModelFactory.getChatModel(modelId)`（同步、Caffeine 缓存、1h 过期，ChatModelFactory L68-76）→ `ChatRequest`（system + user 渲染模板）→ 同步 invoke。token 从 `response.tokenUsage()` 取；如需 AiRequestContext token 统计，可同时读 `AiRequestContext.getLastInputTokens()`。**不用流式模型**：流程引擎是同步执行模型，SSE 只推节点级事件，流式 token 推送留 Phase 2。

### 6.2 Agent 节点（type=agent）

```json
// config
{
  "modelId": 123,
  "queryTemplate": "查询 {{input.warehouse}} 的库存并给出分析",
  "toolCategories": "QUERY,ALL",   // 默认 "QUERY,ALL"; 复用 ToolProviderFactory
  "mcpToolIds": [1, 5],            // 可选, 复用 getMcpToolProvidersByIds
  "maxAgentInvocations": 20
}
// 输出
{ "content": "…Agent 最终回答…", "tokenUsage": {"inputTokens":..,"outputTokens":..} }
```

**建议：复用 `AgenticServices.agentBuilder(ErpQueryAgent.class)`，不自研 tool loop。** 理由：
- 现有 `AiServiceFacade.invokeWithAgent`（L451-478）是生产验证过的代码路径：动态代理接口 + `@SystemMessage` 规则（含 tenant_id 强制、SQL 重试、业务规则）+ tools 注入 + 错误恢复，全部免费继承
- beta28 的 Supervisor 不需要——流程本身就是编排层，一个 Agent 节点 = 一个 agent 实例即可（`ErpQueryAgent.query(query)` 单 agent 直调）
- 风险最小化：与现有代码同 API、同版本（`langchain4j-agentic` 1.18.0-beta28 已在依赖）

执行：`chatModelFactory.getAgentChatModel(modelId)`（低 temperature 0.1）→ `agentBuilder(ErpQueryAgent.class).chatModel(m).tools(toolProviderFactory.getEnabledBuiltinToolObjectsByCategories(categories)).toolProvider(mcpProvider?).build()` → `agent.query(渲染后的 queryTemplate)`。工具级流式（OutputChannel + AI_TOOL_START/END）MVP 不做，Phase 2 可原样搬 AiServiceFacade L528-568 的 drain 模式（事件改挂 instanceId）。

### 6.3 RAG 节点（type=rag）

```json
// config
{
  "knowledgeId": 789,
  "queryTemplate": "{{input.question}}",
  "topK": 5, "minScore": 0.5, "enableRerank": true, "rerankModel": "…"   // 可选, 全部走 retrieve() 的 overrides
}
// 输出
{
  "chunks": ["片段1…", "片段2…"],
  "count": 2,
  "augmentedPrompt": "请根据以下参考资料回答问题…"
}
```

实现：`retrieverService.retrieve(knowledgeId, 渲染query, overrides)`（RetrieverService L78-114，支持混合检索/重排/图谱召回三路召回）。`augmentedPrompt` 字段需要新增公开方法或在节点内拼接（`RetrieverService.buildAugmentedPrompt` 目前是 private L308）——**最小改动**：节点内按相同格式拼（10 行），不动 RetrieverService。

### 6.4 IF 节点（type=if）

```json
// config —— 结构化比较 DSL（安全、零代码、可表单化）
{
  "logic": "AND",                       // AND | OR
  "conditions": [
    { "variable": "nodes.node_llm_1.output.content", "operator": "CONTAINS", "value": "缺货" }
    // operator: EQ/NE/GT/GTE/LT/LTE/CONTAINS/STARTS_WITH/ENDS_WITH/IS_EMPTY/IS_NOT_EMPTY
  ]
}
// 输出
{ "result": true, "nodeName": "缺货判断", "matchedCount": 1 }
```

双出边 `sourceHandle: "outputs/if/true" / "outputs/if/false"`；`FlowConditionEvaluator` 支持 AND/OR 递归组合。实现约 80 行，无依赖。

### 6.5 CODE 节点（type=code）—— 脚本引擎选型

**结论：MVP 用 Spring SpEL（spring-expression 已在 classpath，零新依赖）安全只读模式；GraalJS 明确延后。**

| 方案 | 体积/依赖 | 安全性 | 能力 | 结论 |
|---|---|---|---|---|
| GraalJS (polyglot) | 重：js 引擎 jar ~50-70MB，JVM 启动/内存开销显著 | 需显式禁 IO/网络/反射/类加载，沙箱易漏 | 完整 JS | ❌ Phase 2 视真实需求再上 |
| SpEL 只读模式 | 零（spring-expression 已在 web 依赖树） | `SimpleEvaluationContext.forReadOnlyDataBinding()` 无 MethodResolver/TypeLocator，不能调方法、不能 new/静态引用；再加长度+超时限制 | 字符串/数学/比较/三元/集合索引 | ✅ 本项目最优解 |
| 纯 JSONPath | 需新增 ~320KB | 高（只读） | 只读投影，无计算 | 能力太弱，仅作 Phase 2 增强 |

```json
// config —— 一组 {目标字段: SpEL 表达式}, root = FlowState 的 Map 视图
{
  "expressions": {
    "summary": "('库存不足: ' + nodes['node_rag_1']['output']['chunks'][0])",
    "level": "input.amount > 1000 ? 'HIGH' : 'LOW'",
    "raw": "nodes.node_llm_1.output.content"
  },
  "expressionLimit": 2000   // 字符上限(默认 2000)
}
// 输出
{ "summary": "…", "level": "HIGH", "raw": "…" }
```

执行器 `SpelFlowExpressionEvaluator`：`SimpleEvaluationContext` + root 为 state Map + 每条表达式 `ExecutorService.submit().get(500ms)` 超时 + 捕获 `SpelEvaluationException` 转中文报错。表达式语法对使用者可见（属性面板给提示 + 现场校验按钮）。

### 6.6 节点类型汇总（node-types 端点输出）

| type | 名称 | 输入 handles | 输出 handles | 关键复用 |
|---|---|---|---|---|
| llm | LLM 调用 | inputs/main/0 | outputs/main/0 | ChatModelFactory.getChatModel |
| agent | Agent 智能体 | inputs/main/0 | outputs/main/0 | agentBuilder + ToolProviderFactory |
| rag | RAG 检索 | inputs/main/0 | outputs/main/0 | RetrieverService.retrieve |
| if | 条件分支 | inputs/main/0 | outputs/if/true, outputs/if/false | FlowConditionEvaluator |
| code | 代码转换 | inputs/main/0 | outputs/main/0 | SpEL 只读 |

---

## 七、后端 Controller / API 清单

遵循项目风格：`BaseController` + `@SaCheckPermission` + `TableDataInfo` + `PageQuery` + `R` 包装 + `@Validated`（参考 AiModelController）。注意项目坑：**@RestControllerAdvice 全局异常兜底，节点校验错误需抛具体异常类型（如 ServiceException）以得到友好 message**。

### AiFlowController — `@RequestMapping("/ai/flow")`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/list` | `ai:flow:list` | 分页（flowName/status 过滤）→ TableDataInfo\<AiFlowVo\> |
| GET | `/{flowId}` | `ai:flow:query` | 详情（含 configJson） |
| GET | `/node-types` | 仅登录 | 节点类型元数据（registry + paramSchemas，供前端面板与表单） |
| POST | `/` | `ai:flow:add` | 新建/存草稿（flowKey 自动生成 `flow_` + 雪花后缀） |
| PUT | `/` | `ai:flow:edit` | 更新（仅 DRAFT/OFFLINE 可编辑） |
| POST | `/publish` | `ai:flow:publish` | DRAFT→PUBLISHED：先 `FlowGraph.validate()`，version+1 |
| POST | `/offline` | `ai:flow:edit` | PUBLISHED→OFFLINE |
| DELETE | `/{flowIds}` | `ai:flow:remove` | 逻辑删除（del_flag=2），有运行中实例拒绝 |
| POST | `/run` | `ai:flow:run` | `{flowId, input}` → `R<Long>` instanceId |
| POST | `/stop` | `ai:flow:run` | `{instanceId}` → 置停止标志 |

### AiFlowInstanceController — `@RequestMapping("/ai/flow/instance")`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/list` | `ai:flow:list` | 分页（flowId/status/triggerType 过滤） |
| GET | `/{instanceId}` | `ai:flow:list` | 实例详情（input/output/status/耗时/错误） |
| GET | `/{instanceId}/nodes` | `ai:flow:list` | 节点运行记录列表（按 start_time 升序）→ 含 input/output JSON 快照 |

---

## 八、前端方案（plus-ui）

### 8.1 依赖

```
npm i @vue-flow/core @vue-flow/background @vue-flow/controls @vue-flow/minimap
```

- 均为 MIT；`@vue-flow/core@^1.4x` peer 依赖 `vue ^3.3`，与 Vue 3.5.30 兼容（n8n 同款库：core 1.48.0 / background 1.3.2 / controls 1.1.3 / minimap 1.5.4）
- 无 Vuex/Pinia 冲突（项目已有 pinia 3.0.4）
- Vite 构建体积：四个包 + core 约 +150KB gzip，可控

### 8.2 页面结构（src/views/ai/flow/）

```
flow/
├── index.vue                 # 列表页: vxe-table + 搜索(名称/状态) + 新建/编辑/发布/下线/删除/运行/历史
├── designer/
│   ├── index.vue             # 设计器页: 顶栏(返回/保存/校验/发布/运行/停止) + 左面板 + 画布 + 右属性面板
│   ├── FlowCanvas.vue        # @vue-flow 封装: 受控模式 + 节点渲染 + 连线 + 拖拽 + 缩放 + 小地图
│   ├── NodePalette.vue       # 左侧节点面板: 从 /ai/flow/node-types 渲染可拖拽卡片(HTML5 draggable)
│   ├── NodeParameterForm.vue # 通用参数表单: 按 NodeParamSchema.type 渲染（对齐 n8n ParameterInputFull）
│   ├── CanvasHandle.vue      # handle 组件: 解析 {mode}/{type}/{index} 编码 → 位置/类型/连线校验
│   ├── FlowNode.vue          # 自定义节点组件（slot 注册 + 类型图标 + 运行态高亮样式）
│   └── useFlowStore.ts       # Pinia: 当前画布 nodes/edges、选中节点、运行态 nodeStatusMap
├── monitor/
│   ├── index.vue             # 运行历史: 实例分页 + 详情抽屉
│   └── InstanceDetail.vue    # 只读画布(回放 config_json + 按 node_run 状态着色) + 节点表 + JSON 快照
└── nodeTypes.ts              # type → 组件/默认配置注册表(与后端 node-types 对齐)
```

**设计器与监控分离**：设计器内嵌"运行预览"（保存→POST /run→画布高亮 + 底部输出面板），监控页只管历史与重放。职责单一，符合 8-10 人周预算；n8n 的合并式体验是打磨项。

### 8.3 画布交互要点（对齐 n8n editor-ui §3.2-G）

- **受控模式**：`<VueFlow :nodes :edges :apply-changes="false">`，store 单向下发（对齐 Canvas.vue L1763）
- **节点拖入**：监听 drop，`position = project({x: e.clientX, y: e.clientY})`，生成 `node_llm_1` 风格 id + 默认 config
- **连线约束**：只允许 `outputs/* → inputs/*` 同 type 反 mode（对齐 isValidConnection：source 与 target type 相同且 mode 相反）
- **slot 注册节点**：`<template #node-flow-node>` 自定义节点，非 nodeTypes 对象（对齐 n8n Canvas.vue）
- **属性面板**：选中节点 → `NodeParameterForm` 绑定 `data.config`；`{{` 输入时弹出变量选择器（遍历当前状态 schema：input.* 与已连前序节点输出字段）
- **保存**：`config_json = {nodes, edges}`（剔除运行态字段）POST 存库；加载即回显
- **坐标**：拖拽停止直接写回 `node.position`（对齐 n8n useWorkflowDocumentNodes.setNodePositionById）
- **运行态高亮**：`useFlowStore.nodeStatusMap[nodeId] = RUNNING|SUCCEEDED|FAILED|SKIPPED`；`FlowNode.vue` 用 class 区分：running = 旋转渐变边框（CSS `@property` + keyframes 1.5s，对齐 CanvasNodeDefault.vue L82-105）、SUCCEEDED 绿框、FAILED 红框、SKIPPED 灰化；**边状态按 `['running','failed','succeeded','skipped']` 优先级着色**（对齐 getConnectionStatus）
- **SSE 消费**（designer 与 monitor 各自监听）：

```ts
window.addEventListener('ai-sse-message', onSse)
const onSse = (e: CustomEvent) => {
  const d = e.detail
  if (d.type === 'AI_FLOW_NODE_START' && d.instanceId === currentInstanceId) { ... }
  if (d.type === 'AI_FLOW_NODE_END') { ... }
  if (d.type === 'AI_FLOW_STATUS') { instance 终态 → 停高亮/弹结果 }
}
```

`realtimeDispatch.ts` 无需改动（AI_ 前缀自动派发）。content 为 JSON 字符串需 `JSON.parse(d.content)`，并按 seq 递增过滤。

- **输入面板**：运行前可编辑 `input` JSON（vue-json-pretty + 简单表单）

### 8.4 API 封装（src/api/ai/flow.ts，风格同 chat.ts）

```ts
export interface AiFlow { id: number; flowName: string; flowKey: string; status: string; version: number; configJson?: string; triggerType: string; cronExpr?: string }
export const listFlow = (params) => request({ url: '/ai/flow/list', method: 'get', params })
export const getFlow = (flowId) => request({ url: `/ai/flow/${flowId}`, method: 'get' })
export const saveFlow = (data) => request({ url: '/ai/flow', method: data.id ? 'put' : 'post', data })
export const publishFlow = (flowId) => request({ url: '/ai/flow/publish', method: 'post', params: { flowId } })
export const runFlow = (flowId, input) => request({ url: '/ai/flow/run', method: 'post', data: { flowId, input } })
export const stopFlowRun = (instanceId) => ...
export const listInstance / getInstance / listNodeRuns ...
```

### 8.5 菜单 SQL（增量脚本内，menu_id 段 1830-1845，落地前 grep 确认段内无占用）

```sql
INSERT IGNORE INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_dept`,`create_by`,`create_time`,`remark`) VALUES
(1830, '流程编排', 1700, 6, 'flow', NULL, 'M', '0', '0', NULL, 's-opportunity', 103, 1, NOW(), 'AI 流程编排目录'),
(1831, '流程列表', 1830, 1, 'index', 'ai/flow/index', 'C', '0', '0', 'ai:flow:list', 'list', 103, 1, NOW(), NULL),
(1832, '流程设计', 1830, 2, 'designer', 'ai/flow/designer/index', 'C', '1', '0', 'ai:flow:edit', 'edit', 103, 1, NOW(), '设计器(侧边栏隐藏, 路由可达)'),
(1833, '运行历史', 1830, 3, 'monitor', 'ai/flow/monitor/index', 'C', '0', '0', 'ai:flow:list', 'monitor', 103, 1, NOW(), NULL),
(1834, '流程新增', 1831, 1, '', '', 'F', '0', '0', 'ai:flow:add', '#', 103, 1, NOW(), NULL),
(1835, '流程修改', 1831, 2, '', '', 'F', '0', '0', 'ai:flow:edit', '#', 103, 1, NOW(), NULL),
(1836, '流程删除', 1831, 3, '', '', 'F', '0', '0', 'ai:flow:remove', '#', 103, 1, NOW(), NULL),
(1837, '流程发布', 1831, 4, '', '', 'F', '0', '0', 'ai:flow:publish', '#', 103, 1, NOW(), NULL),
(1838, '流程运行', 1831, 5, '', '', 'F', '0', '0', 'ai:flow:run', '#', 103, 1, NOW(), NULL);
```

（设计器页 `visible='1'` 隐藏但保留路由——项目 filterAsyncRouter 对 hidden 菜单仍生成路由，此模式已在 CV 等模块使用过；`ai/flow/designer/index` 需对应 `import.meta.glob` 可加载的物理文件。）

---

## 九、实施步骤（8-10 人周）

| 阶段 | 内容 | 里程碑 |
|---|---|---|
| **P0 数据层** (0.5-1周) | DDL + 菜单 SQL；ruoyi-common-ai 实体/Mapper/Service | SQL 执行成功，mapper 连通 |
| **P1 引擎核心** (2-3周) | FlowGraph/FlowState/表达式 → 5 个 Provider（先 LLM+IF+CODE，再 RAG、Agent）→ FlowEngineImpl + StopRegistry + 重试 + onError 三态 + SSE 事件 | **无 UI 全链路**：集成测试（LLM→IF→CODE 图）跑通 SUCCEEDED + node_run 落库 + SSE 事件 |
| **P2 API+定时** (1-1.5周) | 两个 Controller 全端点 + 发布校验链；ruoyi-common-job + AiFlowScheduleJob | Postman 全端点验收；SnailJob 1 分钟 cron 出 SCHEDULE 实例 |
| **P3 前端设计器** (2-3周) | vue-flow 装包冒烟 → nodeTypes + FlowNode(slot) + FlowCanvas(受控) + Palette + NodeParameterForm(schema 驱动) + 变量选择器 | 5 类节点可编排、保存加载回显（config_json 往返一致） |
| **P4 运行态+监控** (1-1.5周) | 设计器运行预览（SSE 高亮+输出面板+停止按钮）；monitor 历史+详情重放 | **端到端演示流程**：RAG→LLM→IF→Agent→CODE 画布实时高亮跑完 |
| **P5 打磨** (0.5-1周) | 错误体验（节点报错 toast + 失败原因展示）、flowKey 重名校验、OFFLINE 后禁止 run、删除保护、示例流程种子数据（1-2 条 PUBLISHED 演示流程 SQL）、文档（README + 本文档收尾） | 验收 |

### 里程碑演示流程（P4）

RAG 检索知识库 → LLM 摘要 → IF 判断 → Agent 查询 ERP 库存 → CODE 拼接 → 输出。画布节点实时高亮（RUNNING 旋转渐变边框 → 绿/红/灰）。

---

## 十、风险与规避

| 风险 | 等级 | 规避 |
|---|---|---|
| `langchain4j-agentic` 1.18.0-beta28（依赖 L82） | 中 | Agent 节点**只搬已生产验证的调用方式**（invokeWithAgent 同款），不引入 supervisor 新 API；升级依赖必须回归 AiServiceFacade 全路径 |
| GraalJS 引入重依赖/沙箱漏洞 | 中 | 已决策：MVP 用 SpEL 只读模式；代码中预留 `SpelFlowExpressionEvaluator` 接口，未来换 JS 引擎只动一个类 |
| 定时触发无登录态 → tenant_id 落 0 | 高（本项目已知坑） | trigger_by=flow.create_by；所有写库 `TenantHelper.dynamic(tenantIdStr, ...)`（CLAUDE.md 已记录）；AiRequestContext 流程级 set/clear 成对 |
| SSE 在定时任务场景可能无人在线 | 低 | SSE 尽力而为；历史页可查实例状态（Poll/刷新兜底），文档说明 |
| 同步 ChatModel 无法中断（stop 只停节点边界） | 低 | UI 文案如实说明"运行中 LLM 调用不打断"；可接受（n8n 早期同款行为） |
| SpEL 表达式安全 | 中 | SimpleEvaluationContext（无方法/类型解析）+ 2000 字符 + 500ms 超时 + 异常转中文；**禁止**用 StandardEvaluationContext |
| @vue-flow/core 与 Vue 3.5 兼容 | 低 | core ^1.4x peer vue ^3.3，无需降级；装包后立即冒烟（渲染一个节点） |
| config_json 大文本 + 多人并发编辑 | 低 | MVP 不加乐观锁（update_time 覆盖提示）；版本号仅在 publish 时 +1 |
| IF 分支图裁剪错误导致死锁（入度不归零） | 中 | 引擎内置**总执行次数护栏** + 拓扑序先验；节点入队时校验 startNodeIds 可达性（不可达节点直接 SKIPPED） |
| aiExecutor(4/16/200) 被长流程占满 | 中 | 每实例一个任务、节点内 `Future.get(timeout)`；CallerRunsPolicy 兜底；监控页可 stop |
| 菜单 ID 冲突 | 低 | 用 1830-1845 段（已核对）；落地前 `grep "VALUES (183" ry-vue.sql` 再确认 |

**并行执行取舍说明**：MVP 就绪队列按 FIFO 串行出队。理由：(1) 模型并发受 token/限流约束，串行更可控；(2) 输出顺序稳定（节点 run 记录时间线清晰）；(3) 省去共享状态并发安全（FlowState 写合并）、失败原子性等复杂设计；(4) 架构未锁死——inDegree 就绪队列即并行调度器骨架，Phase 2 改 `while` 为并发取队即可，改动集中在 FlowEngineImpl.runFlow 一处。**且 n8n 本体执行内同样严格串行**（见 §3.1-1）。

**Webhook 延后理由**：需要公网入口、签名鉴权、调用统计、防重放；`flow_key` 唯一键已预留路由标识，后续加一个 Controller 端点 + 安全校验即可，不产生 schema 迁移。

---

## 十一、验证方案（端到端）

1. **引擎集成测试**（P1 里程碑）：JSON 图（LLM→IF→CODE，真实 DeepSeek 或 mock provider）→ run → 断言 instance SUCCEEDED、node_run 状态序列正确、SSE 事件落 Redis 日志
2. **API 验收**（P2）：Postman 全端点 + 发布校验链（含非法图拒绝发布）+ SnailJob cron 触发
3. **演示流程**（P4 里程碑）：RAG 检索知识库 → LLM 摘要 → IF 判断 → Agent 查询 ERP 库存 → CODE 拼接 → 输出；画布节点实时高亮
4. **回归**：现有 AI 对话（assistant/抽屉）不受影响（SSE 事件类型新增不改既有）；`mvn test -DskipTests=false` 单测；前端 `npm run lint:eslint`

---

## 十二、参考文件

### NexusERP 复用与照抄点

- `ruoyi-modules/ruoyi-ai/.../service/facade/AiServiceFacade.java` — Agent/RAG/租户上下文模式权威参考（invokeWithAgent L411-569、invokeWithRag L317-402、saveAiMessageFromContext L689-716）
- `ruoyi-modules/ruoyi-ai/.../mcp/BuiltinToolRegistry.java` — NodeProvider 注册模式模板（@PostConstruct + ConcurrentHashMap）
- `ruoyi-modules/ruoyi-ai/.../service/chat/ChatModelFactory.java` — getChatModel L68 / getAgentChatModel L114
- `ruoyi-modules/ruoyi-ai/.../service/sse/AiSseMessageHelper.java` — 新增 3 个 SSE 事件的位置
- `plus-ui/src/utils/realtimeDispatch.ts` — 前端 SSE 总线（新事件零改动接入）
- `plus-ui/src/components/DesignerForm/` — HTML5 draggable 拖拽先例；`src/components/ai/EchartsRenderer.vue`、`vue-json-pretty` 已装

### n8n 源码参考（D:\n8n\n8n-master，按需翻阅）

- `packages/workflow/src/Interfaces.ts` — IConnections 图模型（L445-463）、INodeParameters（L1736）、FilterValue 条件 DSL（L4106-4140）、NodeConnectionTypes（L2789）
- `packages/core/src/execution-engine/workflow-execute.ts` — 主循环 processRunExecutionData（L1577）、重试（L1797-1809）、onError 三态（L988-993, L2055-2136）、空分支截断（L2253）
- `packages/workflow/src/workflow-data-proxy.ts` — 表达式缺失语义分层（属性缺失→''，数据源缺失→ExpressionError）
- `packages/@n8n/nodes-langchain/nodes/agents/` — Agent 节点 tools 绑定（getInputConnectionData + 工具循环）
- `packages/frontend/editor-ui/src/features/workflows/canvas/` — Canvas.vue（受控模式 L1763）、CanvasNodeDefault.vue（运行态样式 L82-105）、features/ndv/parameters/ParameterInputFull.vue（schema 驱动表单）
- `packages/@n8n/task-runner/src/js-task-runner/` — Code 节点沙箱（node:vm，证明非 vm2）

---

## 十三、差距审查（v1.1，2026-08-07 追加）

> 审查对象：本文档 v1.0 设计与已落地实现（P0-P4 完成，`ruoyi-modules/ruoyi-ai/.../flow/` 23 个类 2774 行 + plus-ui 画布）
> 对照基准：n8n v2.34.0 源码现状（307 个基础节点 + 20 类 AI 节点，packages/nodes-base + packages/@n8n/nodes-langchain）
> 结论先行：**框架层面差距已很小（引擎、错误模型、重试、SSE、画布五维度基本对齐）；真正的差距在"节点生态"——这是定位差异，不是工程差距。** 自研是"AI 编排专用引擎"，n8n 是"通用集成平台 + AI 编排"。

### 13.1 分维度差距总表

| 维度 | 自研现状（已实现） | n8n v2.34.0 | 差距 |
|---|---|---|---|
| **节点生态** | 5 个（LLM/Agent/RAG/IF/CODE） | **307 基础节点 + 20 类 AI 节点**（agents/chains/code/embeddings/llms/mcp/memory/output_parser/retrievers/text_splitters/tools/vector_store/vendors…） | **~95%** |
| **执行引擎** | BFS 就绪队列 + 串行、onError 二态、节点级重试、FlowStopRegistry 协作式停止 | 单 while 循环 + waitingExecution 分叉顺序执行（§3.1 确认 n8n 本体同样串行）、onError **三态**、节点级重试 + **执行级重试（快照续跑）** | **~15%**（缺 ERROR_PORT + 执行级续跑） |
| **表达式/Code** | `{{path}}` 分层缺失语义（已对齐）+ SpEL 只读（禁方法/循环/import） | `{{}}` + **JMESPath + tournament 编译缓存** + Code 节点**完整 JS + node:vm 沙箱 + Task Runner 独立进程** | **~60%** |
| **前端画布** | vue-flow 受控模式 + schema 驱动表单 + 运行态高亮/边着色 + 实例详情重放 | 同款画布库 + **变量选择器（`{{` 弹出）** + **NDV 双击节点看 runData** + 多分支连线 UI | **~25%** |
| **触发方式** | 手动 + SnailJob 定时（控制台配 cron，无 UI） | 手动 + **Schedule 触发节点（画布内可配）** + **Webhook（签名+防重放+UI 管理）** + 邮件/IoT/消息队列事件触发 | **~50%** |
| **运维平台** | 运行历史 + 详情重放、菜单权限、**多租户内置** | 执行数据展示、数据保留（软删+336h/10000 硬删）、共享工作流、监控告警 | **~40%**（自研多租户反而超越） |
| **数据/集成** | 无外部系统节点（只能通过 Agent 工具间接查 ERP） | HTTP/MySQL/Postgres/RabbitMQ/Kafka/MinIO/邮件/IM（钉钉企微飞书）等 ~250 个连接器 | **~95%** |

### 13.2 三个关键差距详析

#### 13.2.1 节点生态（最大差距，但符合定位）

自研 5 个节点全部是 **"AI 编排内部"节点**——输入输出都在流程状态里流转，没有与外部世界交互的能力。而 n8n 的价值主体是 307 个基础节点：HTTP Request、数据库、文件、邮件、IM 通知、云服务。

**关键判断**：这部分差距是**有意的定位差异**，不是落后。Nexus 是 ERP 内部系统，外部数据源是 `@DS("erp")` 多数据源（走 Agent 工具），而非通用 HTTP 集成。但有两个高价值缺口值得 P5 补：

- **HTTP 请求节点**（调用外部 API，如天气/物流/第三方平台）——n8n 里使用率最高的节点
- **通知节点**（企微/飞书/钉钉 webhook 消息）——ERP 场景刚需（定时巡检 → LLM 摘要 → 推送）

#### 13.2.2 Code 节点能力（SpEL 只读 vs 完整 JS）

这是**能力差距最大的单点**。SpEL 只读模式不能写循环、不能调函数、不能 import——方案文档已明确这是 MVP 安全决策（GraalJS 延后）。n8n 用独立 Task Runner 进程 + node:vm 沙箱实现，Java 侧无对应物。

但注意：**n8n 的 JS 沙箱同样不是完全自由的**（有 vm 边界限制），且用户对 Code 的需求在 ERP 场景多为简单转换——SpEL 三元/比较/集合索引已覆盖 80% 场景。真要补，Phase 2 引入 GraalJS 时只需改 `SpelFlowExpressionEvaluator` 一个类（接口已预留）。

#### 13.2.3 执行引擎（差距已收敛到 3 个点）

n8n 本体执行内严格串行（§3.1 已代码级确认），所以**自研串行模型没有实质落后**。剩余差距：

| 差距点 | 自研现状 | n8n | 影响 |
|---|---|---|---|
| ERROR_PORT（onError 第三态） | 二态 STOP/CONTINUE | `continueErrorOutput` 错误走最后一个输出端口 | 低——CONTINUE 透传已覆盖 90% 容错场景 |
| 执行级重试（快照续跑） | 无（只能整流程重跑） | `execution.service.ts retry()` 从失败节点续跑 | 中——长流程 LLM 失败后整跑成本高 |
| waiting/恢复（waitTill） | 无 | 快照续跑 + 签名恢复 URL | 低——自研无等待节点需求（§3.4 已记录） |

**多分支并行执行不算差距**：n8n 的 waitingExecution 也只是"分叉顺序执行"，真正的并行在 queue 模式多 worker（执行级，不在图内）——自研 inDegree 就绪队列即并行骨架，Phase 2 改并发取队即可（§10 已声明）。

### 13.3 量化结论

| 维度 | 完成度 |
|---|---|
| 执行引擎 + 错误模型 + 重试 | ✅ ~85% |
| 前端画布 + 运行态 + 历史 | ✅ ~75%（缺变量选择器/NDV 式详情） |
| 表达式缺失语义 | ✅ 100%（LenientMapAccessor 已对齐） |
| 触发方式 | 🟡 ~50%（Webhook 缺，flow_key 已预留） |
| 表达式/Code 能力 | 🟡 ~40%（SpEL 只读 vs JS 沙箱） |
| **节点生态** | 🔴 ~5%（5/400+） |

**一句话**：作为"ERP 内嵌的 AI 编排层"，**功能差距已基本闭合**——n8n 的核心编排机制（执行模型、分支路由、错误传播、重试、协作式停止、schema 驱动画布）全部对齐，且多租户/SSE 实时推送/菜单权限是超越项。剩余的差距集中在**通用集成节点生态**，这是通用平台 vs 内部系统的定位差异；真正值得补的只有 4 项：HTTP 节点、通知节点、变量选择器、Webhook 触发（各约 0.5-1 人天，可并入 P5）。

---

## 十四、审批清单

- [ ] 总体路线（直接自研 MVP，不部署 n8n）
- [ ] 引入 @vue-flow/core 画布依赖
- [ ] 五类节点范围（LLM / Agent / IF / CODE(SpEL) / RAG）
- [ ] 执行模型（串行 + onError 三态 + 节点级重试）
- [ ] 数据模型（3 张表：ai_flow / ai_flow_instance / ai_flow_node_run）
- [ ] 触发方式（手动 + SnailJob 定时，Webhook 延后）
- [ ] 实施排期（P0-P5，8-10 人周）
