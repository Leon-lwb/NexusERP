# 工业交付决策超时与 PC 进度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 增加工业决策 LLM 等待时间，并将真实工作流进度、方案预览和证据动作以可读形式呈现在 PC 端。

**Architecture:** 流程工厂读取有界配置构造当前可信图，适配器只升级字节匹配的旧 30 秒图；PC 以 SSE 节点事件结合服务端状态/时间线构建真实阶段视图，无模型剩余时间伪百分比。结构化只读字段表代替默认 JSON 预览，原文仅折叠核验。

**Tech Stack:** Spring Boot/Flow Engine、JUnit5/Mockito、Vue 3/TypeScript、Element Plus、Node 单测。

---

### Task 1: 有界 LLM 超时与可信旧模板迁移

**Files:**
- Modify: `ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/config/AiProperties.java`
- Modify: `ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/adapter/DecisionFlowDefinitionFactory.java`
- Modify: `ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/adapter/AiDecisionWorkflowAdapter.java`
- Test: `ruoyi-modules/ruoyi-flow/src/test/java/com/nexus/flow/adapter/DecisionFlowDefinitionFactoryTest.java`
- Test: `ruoyi-modules/ruoyi-flow/src/test/java/com/nexus/flow/adapter/AiDecisionWorkflowAdapterTest.java`
- Test: `ruoyi-modules/ruoyi-flow/src/test/java/com/nexus/flow/node/industrial/IndustrialDecisionProvidersSmokeTest.java`（构造器跟进）

- [x] 写失败测试：默认 LLM 节点 `timeoutSeconds=90`；模拟旧图的 30 秒配置在版本 CAS 下迁移；在旧图混入 `userId` 后仍拒绝。
```java
assertEquals(90, FlowGraph.parse(factory.analysisFlow(22L, null).getConfigJson())
    .getNode("llm").getTimeoutSeconds());
verify(flows).update(isNull(), any()); // 仅可信旧图
```
- [x] 运行 `mvn -q -pl ruoyi-modules/ruoyi-flow -am '-DskipTests=false' '-Dtest=DecisionFlowDefinitionFactoryTest,AiDecisionWorkflowAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，确认新断言失败。
- [x] 实现 `nexus.ai.flow.industrial-decision-llm-timeout-seconds` 默认 90、边界 30–120；`analysisFlow` 使用配置、`legacyAnalysisFlow` 固定 30 且其他字节完全相同；适配器匹配当前、30 秒旧版或已发布默认 90 秒可信配置，否则失败关闭。
```java
if (!Objects.equals(current.getConfigJson(), stored.getConfigJson())
    && !Objects.equals(legacy.getConfigJson(), stored.getConfigJson()))
    throw new ServiceException("工业系统流程模板存在未授权修改");
```
- [x] 重跑定向 Java 测试及包含 Flow 的所有测试；提交后端任务范围改动。

### Task 2: 真实阶段进度与连接中断恢复

**Files:**
- Modify: `plus-ui/src/api/ai/decision.ts`
- Modify: `ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision/DecisionQueryService.java`
- Test: `ruoyi-modules/ruoyi-ai/src/test/java/com/nexus/ai/service/decision/DecisionQueryServiceTest.java`
- Modify: `plus-ui/src/views/ai/decision/decisionViewModel.ts`
- Modify: `plus-ui/src/views/ai/decision/detail.vue`
- Create: `plus-ui/src/components/ai/decision/DecisionProgressPanel.vue`
- Test: `plus-ui/scripts/test-decision-view-model.mjs` 和 `plus-ui/scripts/test-decision-ui-entries.mjs`

- [x] 先加失败测试：仅接受匹配 `analysisFlowInstanceId` 的 `AI_FLOW_NODE_START/END`；处理乱序/缺失事件；RUNNING 显示未知剩余时间而不是百分比；终态从服务端字段确定。
```js
assert.equal(flowProgressEventId({ type: 'AI_FLOW_NODE_START', content: '{"instanceId":12,"nodeId":"llm"}' }, '12')?.nodeId, 'llm');
assert.equal(flowProgressEventId({ type: 'AI_FLOW_NODE_START', content: '{"instanceId":13,"nodeId":"llm"}' }, '12'), null);
```
- [x] 运行 `npm run test:unit` 看到新测试失败。
- [x] 实现独立进度组件：运行中 `el-progress` 不确定动画、当前节点与耗时、已完成节点数；SSE 驱动即时刷新及五秒只读轮询兜底；证据仅终态或按需获取；离开页面清理计时器。 时间线后端查询仅投影节点 ID、状态、时间，避免拉取大型节点输入/输出。
- [x] 再运行前端单测、类型检查、ESLint 并提交任务范围改动。

### Task 3: 业务可读的方案、动作与证据预览

**Files:**
- Modify: `plus-ui/src/views/ai/decision/detail.vue`
- Create: `plus-ui/src/components/ai/decision/DecisionScenarioPreview.vue`
- Modify: `plus-ui/src/components/ai/decision/DecisionScenarioTable.vue`
- Modify: `plus-ui/src/components/ai/decision/DecisionEvidenceDrawer.vue`
- Modify: `plus-ui/src/views/ai/decision/decisionViewModel.ts`
- Test: `plus-ui/scripts/test-decision-view-model.mjs` 和 `plus-ui/scripts/test-decision-ui-entries.mjs`

- [x] 先加失败测试：排程白名单字段映射、空/非法值的占位、雪花 ID 保持字符串、未知字段不进入主表格、原始 JSON 不作为可执行动作来源。
```js
assert.deepEqual(previewScheduleRows([{woId:'9007199254745006', startTime:'2026-09-27', unexpected:'secret'}])[0].woId, '9007199254745006');
```
- [x] 运行前端单测确认失败；实现白名单字段表（前 20 条和总数提示）、不可行原因、折叠原始 JSON、动作与证据中文名称；保持原审批白名单和二次确认。
- [x] 前端单测、`npm run type-check`、定向 ESLint、`npm run build:prod` 全绿后提交。

### Task 4: 集成核验与交付

- [x] 执行 `mvn -q -pl ruoyi-modules/ruoyi-flow -am '-DskipTests=false' test`，再执行 `mvn -q -pl ruoyi-admin -am '-DskipTests=true' package`。
- [x] 执行 `git diff --check` 并检查提交不含连接配置和密钥；不自动重启用户服务、不推送。
- [x] 说明 IDEA 重启后用新运行验证 90 秒模型完成或模板降级，旧运行记录不改；Benchmark 结果不能凭静态测试宣称通过。
