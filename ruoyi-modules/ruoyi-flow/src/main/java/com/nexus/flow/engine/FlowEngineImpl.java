package com.nexus.flow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.adapter.DecisionFlowAccessGuard;
import com.nexus.flow.model.FlowEdgeDef;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.model.FlowNodeDef;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.flow.state.FlowState;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 流程执行引擎实现
 * <p>调度模型（对齐 n8n WorkflowExecute.processRunExecutionData）：
 * BFS 就绪队列 + 单线程串行执行。节点成功 → 按 IF 结果/边路由扇出下游（入度-1，为 0 入队）；
 * 未选中分支节点标记 SKIPPED；任一节点最终失败（onError=STOP）→ 后续全部 SKIPPED，实例 FAILED</p>
 * <p>租户安全（本项目已知坑）：所有写库包 TenantHelper.dynamic；
 * 调度线程与节点工作线程分别绑定 AiRequestContext，实际执行结束时分别清理</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEngineImpl implements FlowEngine {
    @Autowired(required = false)
    private FlowExecutionGovernance governance;

    @Autowired(required = false)
    @Qualifier("flowNodeExecutor")
    private ExecutorService nodeExecutor;

    private static final ExecutorService TEST_NODE_EXECUTOR =
            new ThreadPoolExecutor(
                    4,
                    16,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(128),
                    task -> {
                        Thread thread = new Thread(task, "flow-node-fallback");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());

    @PostConstruct
    void registerRecovery() {
        if (governance != null) governance.setRunner(this::resumeGoverned);
    }

    void resumeGoverned(FlowExecutionGovernance.Recovery recovery) {
        var snapshot = recovery.snapshot();
        Flow flow = new Flow();
        flow.setId(snapshot.flowId());
        flow.setVersion(snapshot.version());
        flow.setFlowKey(snapshot.flowKey());
        flow.setConfigJson(snapshot.config());
        flow.setMaxRetry(snapshot.maxRetry());
        FlowGraph graph = validatedGraph(flow);
        stopRegistry.register(recovery.instanceId());
        seqMap.put(recovery.instanceId(), new AtomicLong(governance.eventBase()));
        if (snapshot.destinationNodeId() == null)
            runFlow(
                    flow,
                    graph,
                    recovery.instanceId(),
                    snapshot.input(),
                    recovery.tenant(),
                    recovery.user(),
                    snapshot.userName());
        else {
            Set<String> closure = new LinkedHashSet<>();
            var stack = new ArrayDeque<String>();
            stack.push(snapshot.destinationNodeId());
            while (!stack.isEmpty()) {
                String id = stack.pop();
                if (closure.add(id))
                    for (var edge : graph.getInEdges(id))
                        if (!edge.isLoopEdge()) stack.push(edge.getSource());
            }
            runFlowToNode(
                    flow,
                    graph,
                    recovery.instanceId(),
                    snapshot.input(),
                    recovery.tenant(),
                    recovery.user(),
                    snapshot.userName(),
                    snapshot.destinationNodeId(),
                    closure);
        }
    }

    private void restoreGoverned(
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            AtomicInteger executed) {
        if (governance == null || !governance.attached() || governance.restored() == null) return;
        var checkpoint = governance.restored();
        state.getRoot().removeAll();
        state.getRoot().setAll(checkpoint.state().deepCopy());
        remaining.clear();
        remaining.putAll(checkpoint.remaining());
        queue.clear();
        queue.addAll(checkpoint.queue());
        runs.clear();
        runs.putAll(checkpoint.nodeRuns());
        succeeded.clear();
        succeeded.addAll(checkpoint.succeeded());
        executed.set(checkpoint.executed());
    }

    private int writeNodeProjection(FlowNodeRun node) {
        if (governance != null && governance.attached()) {
            governance.updateNode(node);
            return 1;
        }
        return nodeRunMapper.updateById(node);
    }

    private int writeInstanceProjection(FlowInstance instance) {
        if (governance != null && governance.attached()) {
            governance.commitInstance(instance);
            return 1;
        }
        return instanceMapper.updateById(instance);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowNodeRegistry nodeRegistry;
    private final FlowStopRegistry stopRegistry;
    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeRunMapper nodeRunMapper;
    private final FlowMapper flowMapper;
    private final AiProperties aiProperties;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 并行波次执行专用线程池（独立于 aiExecutor，防 CallerRunsPolicy 死锁） */
    @Qualifier("flowExecutor")
    private final ExecutorService flowExecutor;

    /** 实例内事件序号（SSE 防乱序，对齐 n8n nodeEventSequence） */
    private final Map<Long, AtomicLong> seqMap = new ConcurrentHashMap<>();

    @Override
    public Long start(Flow flow, JsonNode input, String triggerType,
                      String tenantIdStr, Long userId, String userName) {
        // 无效或不可信图不得遗留运行中的实例。
        FlowGraph graph = validatedGraph(flow);
        if ("DECISION".equals(triggerType)) {
            if (flow.getFlowKey() == null || !flow.getFlowKey().startsWith("system_delivery_risk_"))
                throw new IllegalArgumentException("决策执行必须使用持久化的系统流程");
        } else {
            DecisionFlowAccessGuard.requireGeneral(flow);
        }
        // 1. 建实例（RUNNING）落库
        FlowInstance instance = new FlowInstance();
        instance.setFlowId(flow.getId());
        instance.setFlowVersion(flow.getVersion() == null ? 1 : flow.getVersion());
        instance.setTriggerType(triggerType);
        instance.setTriggerBy(userId);
        instance.setInputJson(input == null ? "{}" : input.toString());
        instance.setConfigSnapshot(flow.getConfigJson());
        instance.setStatus("RUNNING");
        instance.setStartTime(new Date());
        Long instanceId = TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.insert(instance);
            return instance.getId();
        });

        // 3. 注册停止标志 + 事件序号
        stopRegistry.register(instanceId);
        seqMap.put(instanceId, new AtomicLong(0));

        // 4. 异步执行
        final Long userIdF = userId;
        if (governance != null && governance.enabled())
            governance.launch(
                    flow,
                    instanceId,
                    input,
                    tenantIdStr,
                    userIdF,
                    userName,
                    () -> runFlow(flow, graph, instanceId, input, tenantIdStr, userIdF, userName));
        else
            try {
                aiExecutor.execute(
                        () ->
                                runFlow(
                                        flow,
                                        graph,
                                        instanceId,
                                        input,
                                        tenantIdStr,
                                        userIdF,
                                        userName));
            } catch (RejectedExecutionException busy) {
                finishInstanceOnError(instanceId, flow.getId(), busy, tenantIdStr, userIdF);
                stopRegistry.unregister(instanceId);
                throw busy;
            }
        return instanceId;
    }

    private FlowGraph validatedGraph(Flow flow) {
        if (flow == null || flow.getConfigJson() == null || flow.getConfigJson().isBlank())
            throw new IllegalArgumentException("流程缺少可信的完整配置");
        FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
        graph.validate(nodeRegistry::contains);
        for (FlowNodeDef node : graph.getNodes().values()) {
            FlowNodeProvider provider = nodeRegistry.getProvider(node.getType());
            if (provider == null) throw new IllegalArgumentException("未注册节点: " + node.getType());
            provider.validateConfig(node.getConfig());
        }
        return graph;
    }

    @Override
    public Long startToNode(Flow flow, JsonNode input, String triggerType,
                            String tenantIdStr, Long userId, String userName, String destinationNodeId) {
        // 1. 解析图 + 校验 + 目标节点存在性（先于建实例，避免非法请求留孤儿 RUNNING 实例）
        FlowGraph graph = validatedGraph(flow);
        DecisionFlowAccessGuard.requireGeneral(flow);
        if (!graph.getNodes().containsKey(destinationNodeId)) {
            throw new IllegalArgumentException("目标节点不存在: " + destinationNodeId);
        }

        // 2. 建实例（RUNNING）落库
        FlowInstance instance = new FlowInstance();
        instance.setFlowId(flow.getId());
        instance.setFlowVersion(flow.getVersion() == null ? 1 : flow.getVersion());
        instance.setTriggerType(triggerType);
        instance.setTriggerBy(userId);
        instance.setInputJson(input == null ? "{}" : input.toString());
        instance.setConfigSnapshot(flow.getConfigJson());
        instance.setStatus("RUNNING");
        instance.setStartTime(new Date());
        Long instanceId = TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.insert(instance);
            return instance.getId();
        });

        // 3. 计算可执行闭包：目标节点 + 全部传递上游（沿入边回溯）
        Set<String> closure = new LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(destinationNodeId);
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (!closure.add(id)) {
                continue;
            }
            for (FlowEdgeDef edge : graph.getInEdges(id)) {
                if (!edge.isLoopEdge()) {
                    stack.push(edge.getSource());
                }
            }
        }

        // 4. 注册停止标志 + 事件序号
        stopRegistry.register(instanceId);
        seqMap.put(instanceId, new AtomicLong(0));

        // 5. 异步执行（仅闭包子图）
        final Long userIdF = userId;
        if (governance != null && governance.enabled())
            governance.launch(
                    flow,
                    instanceId,
                    input,
                    tenantIdStr,
                    userIdF,
                    userName,
                    destinationNodeId,
                    null,
                    () ->
                            runFlowToNode(
                                    flow,
                                    graph,
                                    instanceId,
                                    input,
                                    tenantIdStr,
                                    userIdF,
                                    userName,
                                    destinationNodeId,
                                    closure));
        else
            try {
                aiExecutor.execute(
                        () ->
                                runFlowToNode(
                                        flow,
                                        graph,
                                        instanceId,
                                        input,
                                        tenantIdStr,
                                        userIdF,
                                        userName,
                                        destinationNodeId,
                                        closure));
            } catch (RejectedExecutionException busy) {
                finishInstanceOnError(instanceId, flow.getId(), busy, tenantIdStr, userIdF);
                stopRegistry.unregister(instanceId);
                throw busy;
            }
        return instanceId;
    }

    /**
     * 部分执行主循环：仅调度闭包子图，目标节点执行完即终态
     * <p>闭包内节点按拓扑序执行；fanOut 只对闭包内目标递减入度（闭包外节点不入队）。
     * 目标节点成功 → SUCCEEDED；失败 → FAILED（与全量执行语义一致）。</p>
     */
    private void runFlowToNode(Flow flow, FlowGraph graph, Long instanceId, JsonNode input,
                               String tenantIdStr, Long userId, String userName,
                               String destinationNodeId, Set<String> closure) {
        AiRequestContext.set(tenantIdStr, userId, userName, null, null, null);
        if (governance != null && governance.attached())
            seqMap.put(instanceId, new AtomicLong(governance.eventBase()));
        try {
            // 闭包子图入度（以闭包内入边为准）
            Map<String, Integer> enqueued = new ConcurrentHashMap<>();
            for (String id : closure) {
                int deg = 0;
                for (FlowEdgeDef edge : graph.getInEdges(id)) {
                    if (!edge.isLoopEdge() && closure.contains(edge.getSource())) {
                        deg++;
                    }
                }
                enqueued.put(id, deg);
            }
            // 就绪队列：闭包内入度 0 的节点
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            for (var e : enqueued.entrySet()) {
                if (e.getValue() == 0) {
                    queue.add(e.getKey());
                }
            }

            Map<String, FlowNodeRun> nodeRuns = new ConcurrentHashMap<>();
            Set<String> succeededNodes = ConcurrentHashMap.newKeySet();
            FlowState state = new FlowState(input);

            AtomicBoolean stoppedHolder = new AtomicBoolean(false);
            AtomicReference<Throwable> errorHolder = new AtomicReference<>();
            AtomicInteger executedCount = new AtomicInteger(0);

            // 执行次数护栏（防拓扑缺陷死循环，对齐全量执行的 maxExecutions 语义）。
            // 需按节点级重试上限放大：retryOnFail 节点单次执行最多 retryMaxTries(2-5) 次尝试。
            int retryFactor = flow.getMaxRetry() == null ? 1 : flow.getMaxRetry() + 1;
            int maxClosureExecutions = closure.size() * retryFactor + 1;

            restoreGoverned(state, enqueued, queue, nodeRuns, succeededNodes, executedCount);

            // 串行执行闭包；目标节点执行后即停
            while (!queue.isEmpty()) {
                if (stopRegistry.isStopped(instanceId)) {
                    stoppedHolder.set(true);
                    break;
                }
                if (executedCount.get() >= maxClosureExecutions) {
                    throw new IllegalStateException("部分执行超出闭包执行次数上限 " + maxClosureExecutions);
                }
                if (governance != null && governance.attached() && queue.peek() != null)
                    governance.beforeNode(
                            graph.getNode(queue.peek()),
                            state,
                            enqueued,
                            queue,
                            nodeRuns,
                            succeededNodes,
                            executedCount.get());
                String nodeId = queue.poll();
                if (nodeId == null) {
                    break;
                }
                boolean ok = executeClosureNode(nodeId, graph, instanceId, state, enqueued, queue,
                    nodeRuns, succeededNodes, flow, executedCount, tenantIdStr, userId, errorHolder, closure);
                if (governance != null && governance.attached() && ok)
                    governance.afterStep(
                            state, enqueued, queue, nodeRuns, succeededNodes, executedCount.get());
                if (!ok || nodeId.equals(destinationNodeId)) {
                    break;
                }
            }

            // 提前结束（停止/失败/未到达目标）时，闭包内未执行节点标记 SKIPPED（对齐全量执行的 markAllRemainingSkipped）
            if (stoppedHolder.get() || errorHolder.get() != null || !nodeRuns.containsKey(destinationNodeId)) {
                markClosureRemainingSkipped(closure, nodeRuns, instanceId, flow.getId(), tenantIdStr);
            }

            // 终态：目标节点执行结果决定实例状态
            FlowInstance existing = TenantHelper.dynamic(tenantIdStr, () -> instanceMapper.selectById(instanceId));
            FlowInstance inst = new FlowInstance();
            inst.setId(instanceId);
            inst.setEndTime(new Date());
            if (existing != null && existing.getStartTime() != null) {
                inst.setDurationMs(System.currentTimeMillis() - existing.getStartTime().getTime());
            }
            int success = 0;
            int failed = 0;
            for (FlowNodeRun run : nodeRuns.values()) {
                if ("SUCCEEDED".equals(run.getStatus())) {
                    success++;
                } else if ("FAILED".equals(run.getStatus())) {
                    failed++;
                }
            }
            inst.setNodeTotal(closure.size());
            inst.setNodeSuccess(success);
            inst.setNodeFailed(failed);
            if (stoppedHolder.get()) {
                inst.setStatus("STOPPED");
                inst.setErrorMsg("用户请求停止");
            } else if (errorHolder.get() != null) {
                inst.setStatus("FAILED");
                inst.setErrorMsg(truncate(safeMessage(errorHolder.get()), 2000));
            } else {
                FlowNodeRun destRun = nodeRuns.get(destinationNodeId);
                if (destRun != null && "SUCCEEDED".equals(destRun.getStatus())) {
                    inst.setStatus("SUCCEEDED");
                    inst.setOutputJson(destRun.getOutputJson());
                } else {
                    inst.setStatus("FAILED");
                    inst.setErrorMsg(
                            destRun != null && destRun.getErrorMsg() != null
                                    ? destRun.getErrorMsg()
                                    : "目标节点未成功执行");
                }
            }
            TenantHelper.dynamic(
                    tenantIdStr,
                    () -> {
                        writeInstanceProjection(inst);
                        return null;
                    });
            Map<String, Object> payload = new HashMap<>();
            payload.put("instanceId", instanceId);
            payload.put("flowId", flow.getId());
            payload.put("status", inst.getStatus());
            payload.put("seq", nextSeq(instanceId));
            if (inst.getErrorMsg() != null) {
                payload.put("errorMsg", inst.getErrorMsg());
            }
            payload.put("durationMs", inst.getDurationMs());
            AiSseMessageHelper.sendFlowStatus(userId, payload);
        } catch (FlowExecutionGovernance.Suspended waiting) {
            // 部分图等待同样由恢复扫描器唤醒。
        } catch (Throwable e) {
            log.error("[Flow] 部分执行异常 instanceId={} dest={}", instanceId, destinationNodeId, e);
            finishInstanceOnError(instanceId, flow.getId(), e, tenantIdStr, userId);
        } finally {
            stopRegistry.unregister(instanceId);
            seqMap.remove(instanceId);
            AiRequestContext.clear();
        }
    }

    /**
     * 闭包内节点执行 + 扇出（仅入队闭包内目标）
     *
     * <p>复用 executeNodeAndFanOut 的执行逻辑，但 fanOut 用闭包受限版本，避免闭包外节点被调度。
     */
    private boolean executeClosureNode(
            String nodeId,
            FlowGraph graph,
            Long instanceId,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Set<String> succeededNodes,
            Flow flow,
            AtomicInteger executedCount,
            String tenantIdStr,
            Long userId,
            AtomicReference<Throwable> errorHolder,
            Set<String> closure) {
        FlowNodeDef nodeDef = graph.getNode(nodeId);
        FlowNodeProvider provider = nodeRegistry.getProvider(nodeDef.getType());
        if (provider == null) {
            throw new IllegalStateException("未注册的节点类型: " + nodeDef.getType());
        }
        int batchIndex = readBatchIndex(state, nodeId, graph);
        FlowNodeRun nodeRun = createNodeRun(instanceId, flow.getId(), nodeDef, batchIndex, tenantIdStr);
        nodeRuns.put(nodeId, nodeRun);
        sendNodeStart(instanceId, nodeDef, nodeRun, userId);
        nodeRun.setInputJson(buildInputSnapshot(graph, state, nodeId));
        try {
            JsonNode output = runNodeWithRetry(nodeDef, provider, state, instanceId, userId, flow, graph, executedCount, tenantIdStr);
            nodeRun.setStatus("SUCCEEDED");
            nodeRun.setOutputJson(output == null ? "{}" : output.toString());
            nodeRun.setErrorMsg(null);
            updateNodeRun(nodeRun, tenantIdStr);
            succeededNodes.add(nodeId);
            state.putNodeOutput(nodeId, output);
            sendNodeEnd(instanceId, nodeDef, nodeRun, output, userId);
            boolean ifResult = "if".equals(nodeDef.getType())
                && output != null && output.has("result") ? output.get("result").asBoolean() : true;
            fanOutClosure(graph, nodeId, ifResult, state, enqueued, queue, closure);
            return true;
        } catch (Exception e) {
            handleNodeFailure(nodeRun, nodeDef, e, tenantIdStr);
            sendNodeEnd(instanceId, nodeDef, nodeRun, null, userId);
            if ("CONTINUE".equals(nodeDef.getOnError())) {
                ObjectNode passthrough = MAPPER.createObjectNode();
                passthrough.put("passthrough", true);
                passthrough.put("error", e.getMessage());
                nodeRun.setOutputJson(passthrough.toString());
                updateNodeRun(nodeRun, tenantIdStr);
                succeededNodes.add(nodeId);
                state.putNodeOutput(nodeId, passthrough);
                fanOutClosure(graph, nodeId, true, state, enqueued, queue, closure);
                return true;
            } else if ("ERROR_PORT".equals(nodeDef.getOnError())) {
                ObjectNode errorOutput = MAPPER.createObjectNode();
                errorOutput.put("error", safeMessage(e));
                errorOutput.put("nodeName", nodeDef.getName());
                errorOutput.put("nodeId", nodeDef.getId());
                state.putNodeOutput(nodeId, errorOutput);
                boolean hasErrorEdge = graph.getOutEdges(nodeId).stream().anyMatch(FlowEdgeDef::isErrorBranch);
                if (hasErrorEdge) {
                    fanOutClosureError(graph, nodeId, enqueued, queue, closure);
                    return true;
                }
                recordFailure(errorHolder, e);
                return false;
            } else {
                recordFailure(errorHolder, e);
                return false;
            }
        }
    }

    /** 闭包受限扇出：只对闭包内目标递减入度并入队（闭包外直接忽略） */
    private void fanOutClosure(
            FlowGraph graph,
            String nodeId,
            boolean ifResult,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Set<String> closure) {
        for (FlowEdgeDef edge : graph.getOutEdges(nodeId)) {
            if (edge.isLoopEdge()) {
                if (closure.contains(edge.getTarget())) {
                    queue.add(edge.getTarget());
                }
                continue;
            }
            String target = edge.getTarget();
            if (!closure.contains(target)) {
                continue;
            }
            boolean pass = true;
            if (edge.isTrueBranch()) {
                pass = ifResult;
            } else if (edge.isFalseBranch()) {
                pass = !ifResult;
            }
            if (!pass) {
                continue;
            }
            int remain = enqueued.merge(target, -1, Integer::sum);
            if (remain == 0) {
                queue.add(target);
            }
        }
    }

    /** 闭包受限错误分支扇出 */
    private void fanOutClosureError(
            FlowGraph graph,
            String nodeId,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Set<String> closure) {
        for (FlowEdgeDef edge : graph.getOutEdges(nodeId)) {
            if (!edge.isErrorBranch()) {
                continue;
            }
            String target = edge.getTarget();
            if (!closure.contains(target)) {
                continue;
            }
            int remain = enqueued.merge(target, -1, Integer::sum);
            if (remain == 0) {
                queue.add(target);
            }
        }
    }

    @Override
    public boolean stop(Long instanceId) {
        if (governance != null && governance.enabled())
            return governance.stop(TenantHelper.getTenantId(), instanceId);
        return stopRegistry.requestStop(instanceId);
    }

    @Override
    public Long retry(Long instanceId, String tenantIdStr, Long userId) {
        // 1. 取实例（校验状态）
        FlowInstance instance = TenantHelper.dynamic(tenantIdStr, () -> instanceMapper.selectById(instanceId));
        if (instance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }
        if (DecisionFlowAccessGuard.protectedConfig(instance.getConfigSnapshot()))
            throw new IllegalArgumentException("工业决策流程不允许通过通用入口续跑");
        if (!"FAILED".equals(instance.getStatus()) && !"STOPPED".equals(instance.getStatus())) {
            throw new IllegalArgumentException(
                    "仅 FAILED/STOPPED 状态的实例可续跑（当前: " + instance.getStatus() + "）");
        }

        // 2. 取配置（对齐 n8n：优先当前流程配置——用户修复后 retry 生效；无当前配置时降级快照）
        //    快照仍保留在实例上供审计，续跑执行用当前配置（n8n retry 语义）
        Flow current = TenantHelper.dynamic(tenantIdStr, () -> flowMapper.selectById(instance.getFlowId()));
        if (DecisionFlowAccessGuard.protectedFlow(current))
            throw new IllegalArgumentException("工业决策流程必须由受控入口重试");
        String configJson = current != null ? current.getConfigJson() : null;
        if (configJson == null || configJson.isBlank()) {
            configJson = instance.getConfigSnapshot();
            log.warn("[Flow] 实例 {} 当前流程配置缺失，使用实例快照续跑", instanceId);
        }
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("无法获取流程配置（当前配置与快照均缺失）");
        }

        if (governance != null && governance.enabled()) {
            if (current == null) throw new IllegalArgumentException("新配置重试需要仍存在的流程定义");
            Long next =
                    start(
                            current,
                            parseInput(instance.getInputJson()),
                            "RETRY",
                            tenantIdStr,
                            userId,
                            null);
            governance.linkRetry(tenantIdStr, next, instanceId);
            return next;
        }
        // 3. 解析图 + 校验
        FlowGraph graph = FlowGraph.parse(configJson);
        graph.validate(nodeRegistry::contains);

        // 3.5 循环场景（含 split 锚点）：MVP 整批重跑——删除全部 node_run 后从起点重跑
        if (!graph.getLoopBodyNodes().isEmpty()) {
            if (current == null) throw new IllegalArgumentException("循环重试需要可用的流程定义");
            return start(
                    current,
                    parseInput(instance.getInputJson()),
                    "RETRY",
                    tenantIdStr,
                    userId,
                    null);
        }

        // 4. 取节点运行记录（按 start_time 升序）
        List<FlowNodeRun> runs =
                TenantHelper.dynamic(
                        tenantIdStr,
                        () ->
                                nodeRunMapper.selectList(
                                        new LambdaQueryWrapper<FlowNodeRun>()
                                                .eq(FlowNodeRun::getInstanceId, instanceId)
                                                .orderByAsc(FlowNodeRun::getStartTime)));
        if (runs == null || runs.isEmpty()) {
            throw new IllegalArgumentException("实例没有节点运行记录，无法续跑");
        }

        // 5. 找最早 FAILED 节点（续跑点）；无 FAILED → 实例已成功但状态异常，拒绝
        FlowNodeRun resumePoint = null;
        for (FlowNodeRun run : runs) {
            if ("FAILED".equals(run.getStatus())) {
                resumePoint = run;
                break;
            }
        }
        if (resumePoint == null) {
            throw new IllegalArgumentException("实例没有失败的节点，无需续跑");
        }
        String resumeNodeId = resumePoint.getNodeId();
        int resumeBatch = resumePoint.getBatchIndex() == null ? 0 : resumePoint.getBatchIndex();
        log.info("[Flow] 实例 {} 续跑: 从节点 {} (batch={}) 继续", instanceId, resumeNodeId, resumeBatch);

        // 6. 删除 FAILED/SKIPPED 的 node_run 行（重跑重新生成）；SUCCEEDED 且 batch < 失败批次的保留
        final Date resumeStartTime = resumePoint.getStartTime() == null ? new Date(0) : resumePoint.getStartTime();
        int deleted =
                TenantHelper.dynamic(
                        tenantIdStr,
                        () ->
                                nodeRunMapper.delete(
                                        new LambdaQueryWrapper<FlowNodeRun>()
                                                .eq(FlowNodeRun::getInstanceId, instanceId)
                                                .and(
                                                        w ->
                                                                w.eq(
                                                                                FlowNodeRun
                                                                                        ::getStatus,
                                                                                "FAILED")
                                                                        .or()
                                                                        .eq(
                                                                                FlowNodeRun
                                                                                        ::getStatus,
                                                                                "SKIPPED"))
                                                .or(
                                                        w ->
                                                                w.eq(
                                                                                FlowNodeRun
                                                                                        ::getInstanceId,
                                                                                instanceId)
                                                                        .eq(
                                                                                FlowNodeRun
                                                                                        ::getBatchIndex,
                                                                                resumeBatch)
                                                                        .gt(
                                                                                FlowNodeRun
                                                                                        ::getStartTime,
                                                                                resumeStartTime))));
        log.info("[Flow] 实例 {} 续跑: 清理 {} 条失败/跳过记录", instanceId, deleted);

        // 7. 重建调度簿记 + 状态
        // 动态入度
        Map<String, Integer> enqueued = new ConcurrentHashMap<>();
        for (String id : graph.getNodes().keySet()) {
            enqueued.put(id, graph.getInDegree(id));
        }
        // 就绪队列
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        for (String startId : graph.getStartNodeIds()) {
            queue.add(startId);
        }
        // 节点运行记录缓存（预载 SUCCEEDED 行；SKIPPED 守卫自动生效）
        Map<String, FlowNodeRun> nodeRuns = new ConcurrentHashMap<>();
        Set<String> succeededNodes = ConcurrentHashMap.newKeySet();
        // 流程状态（从 SUCCEEDED 快照重建）
        FlowState state = new FlowState(parseInput(instance.getInputJson()));
        for (FlowNodeRun run : runs) {
            if ("SUCCEEDED".equals(run.getStatus())) {
                // 循环场景只保留失败批次之前的最终轮（batch < resumeBatch 或非循环）
                if (resumeBatch > 0 && (run.getBatchIndex() == null || run.getBatchIndex() >= resumeBatch)) {
                    continue;
                }
                nodeRuns.put(run.getNodeId(), run);
                succeededNodes.add(run.getNodeId());
                if (run.getOutputJson() != null && !run.getOutputJson().isBlank()) {
                    try {
                        state.putNodeOutput(run.getNodeId(), MAPPER.readTree(run.getOutputJson()));
                    } catch (Exception ignore) {
                        // 快照输出解析失败，忽略该节点输出
                    }
                }
            }
        }

        // 8. 模拟传播：对每个已 SUCCEEDED 节点按拓扑序 fanOut（重建入度簿记，使 FAILED 节点入度归零入队）
        for (String nodeId : graph.getTopologicalOrder()) {
            if (!succeededNodes.contains(nodeId)) {
                continue;
            }
            JsonNode output = null;
            FlowNodeRun run = nodeRuns.get(nodeId);
            if (run != null && run.getOutputJson() != null && !run.getOutputJson().isBlank()) {
                try {
                    output = MAPPER.readTree(run.getOutputJson());
                } catch (Exception ignore) {
                    // 忽略
                }
            }
            boolean ifResult = "if".equals(graph.getNode(nodeId).getType())
                && output != null && output.has("result") ? output.get("result").asBoolean() : true;
            // 模拟 fanOut（仅递减入度/入队，不重复执行）
            for (FlowEdgeDef edge : graph.getOutEdges(nodeId)) {
                if (edge.isLoopEdge()) {
                    continue;
                }
                if (graph.isLoopBodyEdge(edge.getId())) {
                    queue.add(edge.getTarget());
                    continue;
                }
                boolean pass = true;
                if (edge.isTrueBranch()) {
                    pass = ifResult;
                } else if (edge.isFalseBranch()) {
                    pass = !ifResult;
                }
                if (!pass) {
                    continue;
                }
                int remain = enqueued.merge(edge.getTarget(), -1, Integer::sum);
                if (remain == 0) {
                    queue.add(edge.getTarget());
                }
            }
        }

        // 9. 重置实例状态为 RUNNING，重新执行
        FlowInstance update = new FlowInstance();
        update.setId(instanceId);
        update.setStatus("RUNNING");
        update.setErrorMsg(null);
        update.setEndTime(null);
        update.setStartTime(new Date());
        update.setDurationMs(null);
        TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.updateById(update);
            return null;
        });

        stopRegistry.register(instanceId);
        seqMap.put(instanceId, new AtomicLong(0));

        // 10. 异步续跑：复用 runFlow 主流程（nodeRuns 预载 SUCCEEDED，引擎 poll 到已成功节点跳过执行）
        Flow flowDef = new Flow();
        flowDef.setId(instance.getFlowId());
        flowDef.setConfigJson(configJson);
        flowDef.setMaxRetry(1);
        asyncResume(instanceId, graph, configJson, tenantIdStr, userId, state, enqueued, queue, nodeRuns, succeededNodes);
        return instanceId;
    }

    /**
     * 循环场景整批重跑（续跑降级：从起点重新执行，删除全部旧 node_run）
     */
    private Long rerunFromStart(Long instanceId, FlowGraph graph, String configJson,
                                String tenantIdStr, Long userId) {
        // 重置实例状态
        FlowInstance update = new FlowInstance();
        update.setId(instanceId);
        update.setStatus("RUNNING");
        update.setErrorMsg(null);
        update.setEndTime(null);
        update.setStartTime(new Date());
        update.setDurationMs(null);
        TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.updateById(update);
            return null;
        });

        // 重建调度簿记（空状态，从起点开始）
        Map<String, Integer> enqueued = new ConcurrentHashMap<>();
        for (String id : graph.getNodes().keySet()) {
            enqueued.put(id, graph.getInDegree(id));
        }
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        for (String startId : graph.getStartNodeIds()) {
            queue.add(startId);
        }
        Map<String, FlowNodeRun> nodeRuns = new ConcurrentHashMap<>();
        Set<String> succeededNodes = ConcurrentHashMap.newKeySet();

        // 重建入参
        FlowInstance instance = TenantHelper.dynamic(tenantIdStr, () -> instanceMapper.selectById(instanceId));
        FlowState state = new FlowState(parseInput(instance == null ? null : instance.getInputJson()));

        stopRegistry.register(instanceId);
        seqMap.put(instanceId, new AtomicLong(0));
        Flow flowDef = new Flow();
        flowDef.setId(instance == null ? null : instance.getFlowId());
        flowDef.setConfigJson(configJson);
        flowDef.setMaxRetry(1);
        asyncResume(instanceId, graph, configJson, tenantIdStr, userId, state, enqueued, queue, nodeRuns, succeededNodes);
        return instanceId;
    }

    /** 异步续跑执行（复用 runFlow 主流程） */
    private void asyncResume(
            Long instanceId,
            FlowGraph graph,
            String configJson,
            String tenantIdStr,
            Long userId,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Set<String> succeededNodes) {
        Flow flowDef = new Flow();
        flowDef.setId(instanceId);
        flowDef.setConfigJson(configJson);
        flowDef.setMaxRetry(1);
        final Long userIdF = userId;
        aiExecutor.execute(
                () -> {
                    try {
                        AiRequestContext.set(tenantIdStr, userIdF, null, null, null, null);
                        AtomicBoolean stoppedHolder = new AtomicBoolean(false);
                        AtomicReference<Throwable> errorHolder = new AtomicReference<>();
                        // 循环场景强制串行
                        boolean parallel =
                                aiProperties.getFlow().isParallel()
                                        && graph.getLoopBodyNodes().isEmpty();
                        if (parallel) {
                            runFlowParallel(
                                    graph,
                                    instanceId,
                                    state,
                                    enqueued,
                                    queue,
                                    nodeRuns,
                                    succeededNodes,
                                    flowDef,
                                    100_000,
                                    new AtomicInteger(0),
                                    tenantIdStr,
                                    userIdF,
                                    null,
                                    stoppedHolder,
                                    errorHolder);
                        } else {
                            runFlowSerial(
                                    graph,
                                    instanceId,
                                    state,
                                    enqueued,
                                    queue,
                                    nodeRuns,
                                    succeededNodes,
                                    flowDef,
                                    100_000,
                                    new AtomicInteger(0),
                                    tenantIdStr,
                                    userIdF,
                                    null,
                                    stoppedHolder,
                                    errorHolder);
                        }
                        finishInstance(
                                instanceId,
                                flowDef.getId(),
                                state,
                                stoppedHolder.get(),
                                errorHolder.get(),
                                nodeRuns,
                                graph.getNodeCount(),
                                tenantIdStr,
                                userIdF);
                    } catch (Throwable e) {
                        log.error("[Flow] 续跑异常 instanceId={}", instanceId, e);
                        finishInstanceOnError(instanceId, flowDef.getId(), e, tenantIdStr, userIdF);
                    } finally {
                        stopRegistry.unregister(instanceId);
                        seqMap.remove(instanceId);
                        AiRequestContext.clear();
                    }
                });
    }

    /**
     * 解析实例入参 JSON（宽松）
     */
    private JsonNode parseInput(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(inputJson);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 流程主循环（串行/并行双模式）
     * <p>并行模式（nexus.ai.flow.parallel=true）：波次执行——每波整队取出并行执行，波内用独立
     * flowExecutor（不用 aiExecutor：其 CallerRunsPolicy 会导致波次 await 死锁）。含循环的图强制串行。</p>
     */
    private void runFlow(Flow flow, FlowGraph graph, Long instanceId, JsonNode input,
                         String tenantIdStr, Long userId, String userName) {
        // AiRequestContext 流程级设置（LLM 的 token 采集与工具租户隔离依赖它）
        AiRequestContext.set(tenantIdStr, userId, userName, null, null, null);
        try {
            // 动态入度（初始化为静态入度；fanOut 时递减，为 0 时入队）
            Map<String, Integer> enqueued = new ConcurrentHashMap<>();
            for (String id : graph.getNodes().keySet()) {
                enqueued.put(id, graph.getInDegree(id));
            }

            // 就绪队列（入度 0 起点；并发安全容器供并行模式使用）
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            for (String startId : graph.getStartNodeIds()) {
                queue.add(startId);
            }

            // 节点执行总次数护栏（防拓扑缺陷死循环，对齐 n8n 无限循环 检测）
            // 循环场景: 非循环体节点×(maxRetry+1) + 循环体节点×迭代上限(1000) + 1
            int loopBodyCount = graph.getLoopBodyNodes().size();
            int normalCount = graph.getNodeCount() - loopBodyCount;
            int retryFactor = flow.getMaxRetry() == null ? 1 : flow.getMaxRetry() + 1;
            int maxExecutions = normalCount * retryFactor + loopBodyCount * 1000 + 1;
            AtomicInteger executedCount = new AtomicInteger(0);

            // 节点运行记录缓存（nodeId -> FlowNodeRun，用于状态落库）
            Map<String, FlowNodeRun> nodeRuns = new ConcurrentHashMap<>();

            // 已成功节点（用于输出）
            Set<String> succeededNodes = ConcurrentHashMap.newKeySet();

            // 流程状态
            FlowState state = new FlowState(input);

            restoreGoverned(state, enqueued, queue, nodeRuns, succeededNodes, executedCount);

            // 并行开关：含循环的图强制串行（波次语义与循环 tick 纠缠）
            boolean parallel =
                    (governance != null && governance.attached()
                                    ? governance.parallel(aiProperties.getFlow().isParallel())
                                    : aiProperties.getFlow().isParallel())
                            && loopBodyCount == 0;

            // 执行结果 holder（stopped 由执行器置位，供 finishInstance 判定终态）
            AtomicBoolean stoppedHolder = new AtomicBoolean(false);
            AtomicReference<Throwable> errorHolder = new AtomicReference<>();

            if (parallel) {
                runFlowParallel(graph, instanceId, state, enqueued, queue, nodeRuns,
                    succeededNodes, flow, maxExecutions, executedCount, tenantIdStr, userId, userName,
                    stoppedHolder, errorHolder);
            } else {
                runFlowSerial(graph, instanceId, state, enqueued, queue, nodeRuns,
                    succeededNodes, flow, maxExecutions, executedCount, tenantIdStr, userId, userName,
                    stoppedHolder, errorHolder);
            }

            // 终态落库
            finishInstance(instanceId, flow.getId(), state, stoppedHolder.get(), errorHolder.get(),
                nodeRuns, graph.getNodeCount(), tenantIdStr, userId);

        } catch (FlowExecutionGovernance.Suspended waiting) {
            // 已持久化等待并释放租约，不能写失败终态。
        } catch (Throwable e) {
            log.error("[Flow] 流程执行异常 instanceId={}", instanceId, e);
            finishInstanceOnError(instanceId, flow.getId(), e, tenantIdStr, userId);
        } finally {
            stopRegistry.unregister(instanceId);
            seqMap.remove(instanceId);
            AiRequestContext.clear();
        }
    }

    /** 串行执行（原主循环，行为不变） */
    private void runFlowSerial(
            FlowGraph graph,
            Long instanceId,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Set<String> succeededNodes,
            Flow flow,
            int maxExecutions,
            AtomicInteger executedCount,
            String tenantIdStr,
            Long userId,
            String userName,
            AtomicBoolean stoppedHolder,
            AtomicReference<Throwable> errorHolder) {
        while (!queue.isEmpty()) {
            // 停止检查（协作式，节点边界）
            if (stopRegistry.isStopped(instanceId)) {
                stoppedHolder.set(true);
                markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                break;
            }
            // 执行次数护栏
            if (executedCount.get() >= maxExecutions) {
                throw new IllegalStateException("节点执行次数超过上限 " + maxExecutions + "，疑似拓扑缺陷或死循环");
            }
            if (governance != null && governance.attached())
                governance.beforeNode(
                        graph.getNode(queue.peek()),
                        state,
                        enqueued,
                        queue,
                        nodeRuns,
                        succeededNodes,
                        executedCount.get());
            String nodeId = queue.poll();
            if (nodeId == null) {
                break;
            }
            boolean continueFlow = executeNodeAndFanOut(nodeId, graph, instanceId, state, enqueued, queue,
                nodeRuns, succeededNodes, flow, executedCount, tenantIdStr, userId, errorHolder);
            if (governance != null && governance.attached() && continueFlow)
                governance.afterStep(
                        state, enqueued, queue, nodeRuns, succeededNodes, executedCount.get());
            if (!continueFlow) {
                break;
            }
        }
    }

    /** 并行波次执行：每波整队取出，并行执行后等待整波完成，再处理下一波 */
    private void runFlowParallel(
            FlowGraph graph,
            Long instanceId,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Set<String> succeededNodes,
            Flow flow,
            int maxExecutions,
            AtomicInteger executedCount,
            String tenantIdStr,
            Long userId,
            String userName,
            AtomicBoolean stoppedHolder,
            AtomicReference<Throwable> errorHolder) {
        while (true) {
            // 停止检查
            if (stopRegistry.isStopped(instanceId)) {
                stoppedHolder.set(true);
                markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                return;
            }
            if (governance != null && governance.attached())
                governance.prepareWave(
                        graph,
                        state,
                        enqueued,
                        queue,
                        nodeRuns,
                        succeededNodes,
                        executedCount.get());
            // 取整波
            List<String> wave = new ArrayList<>();
            queue.drainTo(wave);
            if (wave.isEmpty()) {
                break;
            }
            if (governance != null && governance.attached())
                governance.beforeWave(
                        wave,
                        state,
                        enqueued,
                        queue,
                        nodeRuns,
                        succeededNodes,
                        executedCount.get());
            // 护栏
            if (executedCount.get() >= maxExecutions) {
                throw new IllegalStateException("节点执行次数超过上限 " + maxExecutions + "，疑似拓扑缺陷或死循环");
            }
            // 并行提交本波（flowExecutor 独立池，任务排队不拒绝，避免 await 死锁）
            List<Future<Boolean>> futures = new ArrayList<>();
            for (String nodeId : wave) {
                Callable<Boolean> action =
                        () ->
                                executeNodeAndFanOut(
                                        nodeId,
                                        graph,
                                        instanceId,
                                        state,
                                        enqueued,
                                        queue,
                                        nodeRuns,
                                        succeededNodes,
                                        flow,
                                        executedCount,
                                        tenantIdStr,
                                        userId,
                                        errorHolder);
                if (governance != null && governance.attached()) {
                    var result = new FutureTask<Boolean>(action);
                    try {
                        flowExecutor.execute(governance.wrap(result));
                    } catch (RejectedExecutionException rejected) {
                        if (!futures.isEmpty())
                            throw new FlowExecutionGovernance.RecoveryRequired(
                                    "部分并行分支已提交，队列拒绝后的执行结果须核对");
                        throw rejected;
                    }
                    futures.add(result);
                } else futures.add(flowExecutor.submit(action));
            }
            // 等待整波完成
            boolean waveContinue = true;
            for (Future<Boolean> f : futures) {
                try {
                    boolean nodeContinue = f.get(600, TimeUnit.SECONDS);
                    if (!nodeContinue) {
                        waveContinue = false;
                    }
                } catch (TimeoutException e) {
                    log.error("[Flow] 并行波次执行超时 instanceId={}", instanceId);
                    f.cancel(true);
                    recordFailure(
                            errorHolder,
                            new FlowExecutionGovernance.RecoveryRequired("并行节点尚未确认退出"));
                    waveContinue = false;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    recordFailure(errorHolder, cause != null ? cause : e);
                    waveContinue = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordFailure(
                            errorHolder,
                            governance != null && governance.attached()
                                    ? new FlowExecutionGovernance.RecoveryRequired("并行协调中断，节点退出待核对")
                                    : e);
                    waveContinue = false;
                }
            }
            if (governance != null && governance.attached() && waveContinue)
                governance.afterWave(
                        state, enqueued, queue, nodeRuns, succeededNodes, executedCount.get());
            if (!waveContinue) {
                // STOP 失败或异常：剩余节点 SKIPPED（executeNodeAndFanOut 已标记未执行部分）
                break;
            }
        }
    }

    /**
     * 执行单个节点并扇出
     *
     * @return false 表示应立即终止（STOP 失败 / 停止请求）
     */
    private boolean executeNodeAndFanOut(
            String nodeId,
            FlowGraph graph,
            Long instanceId,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Set<String> succeededNodes,
            Flow flow,
            AtomicInteger executedCount,
            String tenantIdStr,
            Long userId,
            AtomicReference<Throwable> errorHolder) {
        FlowNodeDef nodeDef = graph.getNode(nodeId);
        FlowNodeProvider provider = nodeRegistry.getProvider(nodeDef.getType());
        if (provider == null) {
            throw new IllegalStateException("未注册的节点类型: " + nodeDef.getType());
        }

        // 建节点运行记录（RUNNING）；循环场景读 loops 状态取批次索引
        int batchIndex = readBatchIndex(state, nodeId, graph);
        FlowNodeRun nodeRun = createNodeRun(instanceId, flow.getId(), nodeDef, batchIndex, tenantIdStr);
        nodeRuns.put(nodeId, nodeRun);
        sendNodeStart(instanceId, nodeDef, nodeRun, userId);
        // 输入快照（渲染后入参：流程入参 + 直接上游输出），供 NDV 输入视图
        nodeRun.setInputJson(buildInputSnapshot(graph, state, nodeId));

        try {
            // 执行节点（带超时 + 重试）
            JsonNode output = runNodeWithRetry(nodeDef, provider, state, instanceId, userId, flow, graph, executedCount, tenantIdStr);
            // 成功
            nodeRun.setStatus("SUCCEEDED");
            nodeRun.setOutputJson(output == null ? "{}" : output.toString());
            nodeRun.setErrorMsg(null);
            updateNodeRun(nodeRun, tenantIdStr);
            succeededNodes.add(nodeId);
            state.putNodeOutput(nodeId, output);
            // 追加批次历史输出（aggregate 节点收集每轮输出用）
            appendBatchOutput(state, nodeId, output);
            sendNodeEnd(instanceId, nodeDef, nodeRun, output, userId);

            // 扇出下游：按 IF 结果选边，选中边下游入度-1，为 0 入队
            boolean ifResult = "if".equals(nodeDef.getType())
                && output != null && output.has("result") ? output.get("result").asBoolean() : true;
            fanOut(graph, nodeId, ifResult, state, enqueued, queue, nodeRuns, instanceId, flow.getId(), tenantIdStr);
            return true;

        } catch (Exception e) {
            // 节点失败
            handleNodeFailure(nodeRun, nodeDef, e, tenantIdStr);
            sendNodeEnd(instanceId, nodeDef, nodeRun, null, userId);

            if ("CONTINUE".equals(nodeDef.getOnError())) {
                // onError=CONTINUE: 输入透传作为输出（对齐 n8n continueRegularOutput），流程继续
                ObjectNode passthrough = MAPPER.createObjectNode();
                passthrough.put("passthrough", true);
                passthrough.put("error", e.getMessage());
                nodeRun.setOutputJson(passthrough.toString());
                updateNodeRun(nodeRun, tenantIdStr);
                succeededNodes.add(nodeId);
                state.putNodeOutput(nodeId, passthrough);
                fanOut(graph, nodeId, true, state, enqueued, queue, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                return true; // 透传后不视为流程失败
            } else if ("ERROR_PORT".equals(nodeDef.getOnError())) {
                // onError=ERROR_PORT: 节点 FAILED（保留真实状态），错误对象写入 state，fanOut 只走 error 出边（对齐 n8n
                // continueErrorOutput）
                ObjectNode errorOutput = MAPPER.createObjectNode();
                errorOutput.put("error", safeMessage(e));
                errorOutput.put("nodeName", nodeDef.getName());
                errorOutput.put("nodeId", nodeDef.getId());
                state.putNodeOutput(nodeId, errorOutput);
                // 检查是否存在 error 出边：有则走错误分支，无则降级 STOP
                boolean hasErrorEdge = graph.getOutEdges(nodeId).stream()
                    .anyMatch(FlowEdgeDef::isErrorBranch);
                if (hasErrorEdge) {
                    fanOutErrorBranch(graph, nodeId, enqueued, queue, nodeRuns,
                        instanceId, flow.getId(), tenantIdStr);
                    return true; // 错误分支已消费，流程继续
                }
                // 无 error 出边 → 降级 STOP
                recordFailure(errorHolder, e);
                markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                return false;
            } else {
                // STOP: 记录失败原因 + 未执行的全部节点标记 SKIPPED（含尚未入队的下游）
                recordFailure(errorHolder, e);
                markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                return false;
            }
        }
    }

    /**
     * 每次节点尝试独立绑定AI与数据库租户上下文，并在成功、异常和超时后的实际退出时清理。
     * <p>本方法只在节点工作线程执行，不能清理调度线程的流程上下文；不继承旧会话、知识库和用量。</p>
     */
    JsonNode executeNodeInRequestScope(FlowNodeProvider provider, FlowExecutionContext ctx, String userName) {
        AiRequestContext.clear();
        String tenantId = ctx.getTenantId();
        if (tenantId == null || !tenantId.matches("[a-zA-Z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("节点租户上下文缺失或无效，拒绝执行");
        }
        AiRequestContext.set(tenantId, ctx.getUserId(), userName, null, null, null);
        try {
            return TenantHelper.dynamic(
                    tenantId,
                    () -> {
                        try {
                            return provider.execute(ctx);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    });
        } finally {
            AiRequestContext.clear();
        }
    }

    /**
     * 执行节点（带重试 + 超时）
     */
    private JsonNode runNodeWithRetry(FlowNodeDef nodeDef, FlowNodeProvider provider, FlowState state,
                                      Long instanceId, Long userId, Flow flow, FlowGraph graph,
                                      AtomicInteger executedCount, String tenantIdStr) throws Exception {
        int maxTries = nodeDef.isRetryOnFail() ? nodeDef.getRetryMaxTries() : 1;
        long waitBetweenTries = nodeDef.getWaitBetweenTries();
        long timeoutSeconds = nodeDef.getTimeoutSeconds();
        String nodeName = nodeDef.getName();

        Exception lastError = null;
        for (int tryIndex = 0; tryIndex < maxTries; tryIndex++) {
            executedCount.incrementAndGet();
            try {
                // 构建执行上下文（每个 try 重建，保证 config 不变、state 实时）
                FlowExecutionContext ctx = new FlowExecutionContext();
                ctx.setNodeId(nodeDef.getId());
                ctx.setNodeName(nodeName);
                ctx.setNodeType(nodeDef.getType());
                ctx.setConfig(nodeDef.getConfig());
                ctx.setState(state);
                ctx.setInstanceId(instanceId);
                ctx.setFlowId(flow.getId());
                ctx.setUserId(userId);
                // 租户经 ctx 传递（节点在 ForkJoinPool 线程执行，AiRequestContext 线程上下文 不可达）
                ctx.setTenantId(tenantIdStr);
                ctx.setGraph(graph);

                // 在真正执行节点的工作线程绑定身份；线程创建时继承的上下文可能来自其他请求。
                // 此调用链仅显式提供租户与用户ID，用户名保持未知，不能采信并行调度线程继承的旧名称。
                if (governance != null && governance.attached()) {
                    FlowState child;
                    ObjectNode before;
                    synchronized (state) {
                        before = state.getRoot().deepCopy();
                        child = new FlowState(state.getInput());
                        child.getRoot().removeAll();
                        child.getRoot().setAll(before.deepCopy());
                    }
                    ctx.setState(child);
                    ctx.setExecutionKey(
                            governance.executionKey(
                                    nodeDef.getId(),
                                    readBatchIndex(state, nodeDef.getId(), graph)));
                    JsonNode result =
                            governance.execute(
                                    provider,
                                    ctx,
                                    () -> executeNodeInRequestScope(provider, ctx, null),
                                    TimeUnit.SECONDS.toMillis(timeoutSeconds));
                    governance.check();
                    synchronized (state) {
                        mergeNodeState(before, child.getRoot(), state.getRoot());
                    }
                    return result;
                }
                Future<JsonNode> future =
                        (nodeExecutor == null ? TEST_NODE_EXECUTOR : nodeExecutor)
                                .submit(() -> executeNodeInRequestScope(provider, ctx, null));
                try {
                    return future.get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    future.cancel(true);
                    throw timeout;
                }
            } catch (TimeoutException e) {
                lastError =
                        new IllegalStateException(
                                "节点 " + nodeName + " 执行超时（" + timeoutSeconds + "s）");
            } catch (ExecutionException e) {
                // 解包 CompletableFuture 的 CompletionException 嵌套，取底层 cause（否则 message 为 null 导致错误信息丢失）
                Throwable cause = e.getCause();
                while (cause instanceof CompletionException ce && ce.getCause() != null) {
                    cause = ce.getCause();
                }
                lastError = cause instanceof Exception ex ? ex : new Exception(cause);
            } catch (Exception e) {
                lastError = e;
            }
            if (tryIndex < maxTries - 1) {
                try {
                    Thread.sleep(waitBetweenTries);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("节点 " + nodeName + " 重试等待被中断", ie);
                }
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("节点 " + nodeName + " 执行失败");
    }

    /**
     * 扇出下游：按边类型路由
     *
     * <ul>
     *   <li>loop 返回边 → 读 split 锚点 output.done：false 重新入队锚点 / true 忽略
     *   <li>split 锚点出边 → loop/main 仅 !done 时触发（体内边直入队），loop/done 仅 done 时触发（普通入度）
     *   <li>循环体内边（非 split 出边）→ 直接入队下游（线性链每轮唯一触发，不走入度）
     *   <li>IF 分支边 → 按 ifResult 选边，未选中走 SKIPPED 传播
     *   <li>其余普通边 → 入度递减，归零入队
     * </ul>
     */
    private void fanOut(
            FlowGraph graph,
            String nodeId,
            boolean ifResult,
            FlowState state,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Long instanceId,
            Long flowId,
            String tenantIdStr) {
        boolean isSplit = "split".equals(graph.getNode(nodeId).getType());
        JsonNode splitOutput = null;
        boolean splitDone = false;
        if (isSplit) {
            try {
                splitOutput = state.get("nodes." + nodeId + ".output");
            } catch (IllegalArgumentException ignore) {
                // 忽略
            }
            splitDone =
                    splitOutput != null
                            && splitOutput.has("done")
                            && splitOutput.get("done").asBoolean();
        }

        List<FlowEdgeDef> outEdges = graph.getOutEdges(nodeId);
        for (FlowEdgeDef edge : outEdges) {
            // 1. loop 返回边：按锚点 done 决定重入队或忽略（edge.source=循环体尾节点, target=split 锚点）
            if (edge.isLoopEdge()) {
                JsonNode anchorOutput = null;
                try {
                    anchorOutput = state.get("nodes." + edge.getTarget() + ".output");
                } catch (IllegalArgumentException ignore) {
                    // split 未执行（不应发生），忽略
                }
                boolean done =
                        anchorOutput != null
                                && anchorOutput.has("done")
                                && anchorOutput.get("done").asBoolean();
                if (!done) {
                    queue.add(edge.getTarget()); // 重新入队 split 锚点取下一批
                }
                continue;
            }
            // 2. split 锚点出边：按 done 路由（loop/main 边 !done 触发；loop/done 边 done 触发）
            if (isSplit) {
                boolean isDoneEdge = edge.isDoneBranch();
                boolean pass = isDoneEdge ? splitDone : !splitDone;
                if (!pass) {
                    // 未选中分支：SKIPPED 传播（done=false 时 done 边下游跳过；done=true 时 loop/main 边下游跳过）
                    markBranchSkipped(edge.getTarget(), graph, enqueued, queue, nodeRuns, instanceId, flowId, tenantIdStr);
                    continue;
                }
                // 选中边：loop/main 边（体内边）直接入队；loop/done 边普通入度递减
                if (graph.isLoopBodyEdge(edge.getId())) {
                    queue.add(edge.getTarget());
                } else {
                    String target = edge.getTarget();
                    int remain = enqueued.merge(target, -1, Integer::sum);
                    if (remain == 0) {
                        queue.add(target);
                    }
                }
                continue;
            }
            // 3. 循环体内边（非 split 出边）：直接入队下游
            if (graph.isLoopBodyEdge(edge.getId())) {
                queue.add(edge.getTarget());
                continue;
            }
            // 4. IF 分支边选边
            boolean pass = true;
            if (edge.isTrueBranch()) {
                pass = ifResult;
            } else if (edge.isFalseBranch()) {
                pass = !ifResult;
            }
            if (!pass) {
                // 未选中分支的下游节点：SKIPPED 传播（走入度簿记，merge 节点豁免——收集已到达输入后真执行）
                markBranchSkipped(edge.getTarget(), graph, enqueued, queue, nodeRuns, instanceId, flowId, tenantIdStr);
                continue;
            }
            // 5. 普通边：入度递减
            String target = edge.getTarget();
            int remain = enqueued.merge(target, -1, Integer::sum);
            if (remain == 0) {
                queue.add(target);
            }
        }
    }

    /**
     * 错误分支扇出（ERROR_PORT 节点失败后）
     *
     * <p>只递减 isErrorBranch() 出边下游入度；其余出边（main/true/false）的目标标记 SKIPPED
     */
    private void fanOutErrorBranch(
            FlowGraph graph,
            String nodeId,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Long instanceId,
            Long flowId,
            String tenantIdStr) {
        List<FlowEdgeDef> outEdges = graph.getOutEdges(nodeId);
        for (FlowEdgeDef edge : outEdges) {
            if (edge.isErrorBranch()) {
                // 错误分支：正常递减入队
                String target = edge.getTarget();
                int remain = enqueued.merge(target, -1, Integer::sum);
                if (remain == 0) {
                    queue.add(target);
                }
            } else {
                // 非错误分支（main/true/false）：标记 SKIPPED（递归传播，merge 豁免）
                markBranchSkipped(edge.getTarget(), graph, enqueued, queue, nodeRuns, instanceId, flowId, tenantIdStr);
            }
        }
    }

    /**
     * 标记分支未命中的下游节点为 SKIPPED（传入度簿记传播）
     *
     * <p>语义：SKIPPED 节点视为"已结束无输出"——递减下游入度；归零时： 下游为 merge → 入队真执行（只收集已到达的输入，对齐 n8n 空分支合并）； 下游非 merge
     * → 递归 SKIPPED（跳过链整段灰化）
     */
    private void markBranchSkipped(
            String nodeId,
            FlowGraph graph,
            Map<String, Integer> enqueued,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> nodeRuns,
            Long instanceId,
            Long flowId,
            String tenantIdStr) {
        if (nodeRuns.containsKey(nodeId)) {
            return;
        }
        FlowNodeDef def = graph.getNode(nodeId);
        FlowNodeRun nodeRun = createNodeRun(instanceId, flowId, def, tenantIdStr);
        nodeRun.setStatus("SKIPPED");
        nodeRun.setErrorMsg("上游分支未命中，跳过执行");
        nodeRun.setStartTime(new Date());
        nodeRun.setEndTime(new Date());
        nodeRun.setDurationMs(0L);
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    writeNodeProjection(nodeRun);
                    return null;
                });
        nodeRuns.put(nodeId, nodeRun);
        // 递减下游入度（SKIPPED 参与簿记，保证 merge 等汇聚节点能归零入队）
        for (FlowEdgeDef edge : graph.getOutEdges(nodeId)) {
            String target = edge.getTarget();
            int remain = enqueued.merge(target, -1, Integer::sum);
            if (remain == 0) {
                FlowNodeDef targetDef = graph.getNode(target);
                if (targetDef != null && "merge".equals(targetDef.getType())) {
                    // merge：入队真执行（收集已到达输入）
                    queue.add(target);
                } else {
                    // 非 merge：跳过链递归传播
                    markBranchSkipped(target, graph, enqueued, queue, nodeRuns, instanceId, flowId, tenantIdStr);
                }
            }
        }
    }

    /**
     * 标记全部未执行节点为 SKIPPED（流程失败/停止时，含尚未入队的下游节点）
     */
    private void markAllRemainingSkipped(FlowGraph graph, Map<String, FlowNodeRun> nodeRuns,
                                         Long instanceId, Long flowId, String tenantIdStr) {
        for (String nodeId : graph.getNodes().keySet()) {
            if (nodeRuns.containsKey(nodeId)) {
                continue;
            }
            FlowNodeDef def = graph.getNode(nodeId);
            FlowNodeRun nodeRun = createNodeRun(instanceId, flowId, def, tenantIdStr);
            nodeRun.setStatus("SKIPPED");
            nodeRun.setErrorMsg("流程已停止或失败，跳过执行");
            nodeRun.setStartTime(new Date());
            nodeRun.setEndTime(new Date());
            nodeRun.setDurationMs(0L);
            TenantHelper.dynamic(
                    tenantIdStr,
                    () -> {
                        writeNodeProjection(nodeRun);
                        return null;
                    });
            nodeRuns.put(nodeId, nodeRun);
        }
    }

    /**
     * 部分执行：标记闭包内未执行节点为 SKIPPED（提前停止/失败/未到达目标时）
     * <p>与 {@link #markAllRemainingSkipped} 的区别：只处理闭包内节点（闭包外节点
     * 本就不在部分执行范围内，不应产生运行记录）</p>
     */
    private void markClosureRemainingSkipped(Set<String> closure, Map<String, FlowNodeRun> nodeRuns,
                                             Long instanceId, Long flowId, String tenantIdStr) {
        for (String nodeId : closure) {
            if (nodeRuns.containsKey(nodeId)) {
                continue;
            }
            // 闭包节点必然存在于图中；node_run 记录需要 nodeName，缺失时降级用节点ID
            FlowNodeDef nodeDef = new FlowNodeDef();
            nodeDef.setId(nodeId);
            FlowNodeRun nodeRun = createNodeRun(instanceId, flowId, nodeDef, tenantIdStr);
            nodeRun.setStatus("SKIPPED");
            nodeRun.setErrorMsg("部分执行提前结束，跳过执行");
            nodeRun.setStartTime(new Date());
            nodeRun.setEndTime(new Date());
            nodeRun.setDurationMs(0L);
            TenantHelper.dynamic(
                    tenantIdStr,
                    () -> {
                        writeNodeProjection(nodeRun);
                        return null;
                    });
            nodeRuns.put(nodeId, nodeRun);
        }
    }

    /**
     * 节点失败处理
     *
     * @return 失败原因（供流程终态使用）
     */
    private Throwable handleNodeFailure(FlowNodeRun nodeRun, FlowNodeDef nodeDef,
                                        Exception e, String tenantIdStr) {
        nodeRun.setStatus("FAILED");
        nodeRun.setErrorMsg(truncate(safeMessage(e), 2000));
        nodeRun.setEndTime(new Date());
        if (nodeRun.getStartTime() != null) {
            nodeRun.setDurationMs(System.currentTimeMillis() - nodeRun.getStartTime().getTime());
        }
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    writeNodeProjection(nodeRun);
                    return null;
                });
        log.warn(
                "[Flow] 节点 {} ({}) 执行失败: {}",
                nodeDef.getId(),
                nodeDef.getName(),
                e.getMessage(),
                e);
        return e;
    }

    /**
     * 追加节点批次历史输出（aggregate 节点收集用）
     * <p>root.batchOutputs.&lt;nodeId&gt; = [第1轮输出, 第2轮输出, ...]；非循环节点仅 1 条</p>
     */
    private void appendBatchOutput(FlowState state, String nodeId, JsonNode output) {
        if (output == null || state.getRoot() == null) {
            return;
        }
        ArrayNode list = state.getRoot().withArray("/batchOutputs/" + nodeId);
        list.add(output);
    }

    /**
     * 构建节点输入快照（NDV 输入视图用）：流程入参 + 直接上游节点输出
     */
    private String buildInputSnapshot(FlowGraph graph, FlowState state, String nodeId) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        JsonNode flowInput;
        try {
            flowInput = state.get("input");
        } catch (IllegalArgumentException ignore) {
            flowInput = null;
        }
        snapshot.set("input", flowInput == null ? MAPPER.createObjectNode() : flowInput);
        ObjectNode upstream = MAPPER.createObjectNode();
        for (FlowEdgeDef edge : graph.getInEdges(nodeId)) {
            try {
                upstream.set(edge.getSource(), state.get("nodes." + edge.getSource() + ".output"));
            } catch (IllegalArgumentException ignore) {
                // 上游尚未输出（不应发生），忽略
            }
        }
        snapshot.set("upstream", upstream);
        return snapshot.toString();
    }

    /**
     * 建节点运行记录（PENDING→RUNNING，落库）
     *
     * @param batchIndex 循环批次索引（split 循环场景，非循环=0）
     */
    private FlowNodeRun createNodeRun(Long instanceId, Long flowId, FlowNodeDef nodeDef, String tenantIdStr) {
        return createNodeRun(instanceId, flowId, nodeDef, 0, tenantIdStr);
    }

    private FlowNodeRun createNodeRun(Long instanceId, Long flowId, FlowNodeDef nodeDef,
                                        int batchIndex, String tenantIdStr) {
        FlowNodeRun nodeRun = new FlowNodeRun();
        nodeRun.setInstanceId(instanceId);
        nodeRun.setFlowId(flowId);
        nodeRun.setNodeId(nodeDef.getId());
        nodeRun.setNodeName(nodeDef.getName());
        nodeRun.setNodeType(nodeDef.getType().toUpperCase());
        nodeRun.setStatus("RUNNING");
        nodeRun.setRetryCount(0);
        nodeRun.setBatchIndex(batchIndex);
        nodeRun.setStartTime(new Date());
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    if (governance != null && governance.attached())
                        governance.createNode(() -> nodeRunMapper.insert(nodeRun));
                    else nodeRunMapper.insert(nodeRun);
                    return null;
                });
        return nodeRun;
    }

    /**
     * 更新节点运行记录（含耗时）
     */
    private void updateNodeRun(FlowNodeRun nodeRun, String tenantIdStr) {
        nodeRun.setEndTime(new Date());
        if (nodeRun.getStartTime() != null) {
            nodeRun.setDurationMs(System.currentTimeMillis() - nodeRun.getStartTime().getTime());
        }
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    writeNodeProjection(nodeRun);
                    return null;
                });
    }

    /**
     * 流程终态落库
     */
    private void finishInstance(Long instanceId, Long flowId, FlowState state, boolean stopped,
                                Throwable flowError, Map<String, FlowNodeRun> nodeRuns,
                                int nodeTotal, String tenantIdStr, Long userId) {
        // 读取实例（取 startTime 计算耗时）
        FlowInstance existing = TenantHelper.dynamic(tenantIdStr, () -> instanceMapper.selectById(instanceId));
        FlowInstance instance = new FlowInstance();
        instance.setId(instanceId);
        instance.setEndTime(new Date());
        if (existing != null && existing.getStartTime() != null) {
            instance.setDurationMs(System.currentTimeMillis() - existing.getStartTime().getTime());
        }

        int success = 0;
        int failed = 0;
        for (FlowNodeRun run : nodeRuns.values()) {
            if ("SUCCEEDED".equals(run.getStatus())) {
                success++;
            } else if ("FAILED".equals(run.getStatus())) {
                failed++;
            }
        }
        instance.setNodeTotal(nodeTotal);
        instance.setNodeSuccess(success);
        instance.setNodeFailed(failed);

        if (flowError instanceof FlowExecutionGovernance.RecoveryRequired) {
            instance.setStatus("RECOVERY_REQUIRED");
            instance.setErrorMsg(truncate(safeMessage(flowError), 2000));
        } else if (stopped) {
            instance.setStatus("STOPPED");
            instance.setErrorMsg("用户请求停止");
        } else if (flowError != null) {
            instance.setStatus(
                    flowError instanceof FlowExecutionGovernance.RecoveryRequired
                            ? "RECOVERY_REQUIRED"
                            : "FAILED");
            instance.setErrorMsg(truncate(safeMessage(flowError), 2000));
        } else {
            instance.setStatus("SUCCEEDED");
            instance.setOutputJson(state.getFinalOutput().toString());
        }
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    writeInstanceProjection(instance);
                    return null;
                });

        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("flowId", flowId);
        payload.put("status", instance.getStatus());
        payload.put("seq", nextSeq(instanceId));
        if (instance.getErrorMsg() != null) {
            payload.put("errorMsg", instance.getErrorMsg());
        }
        payload.put("durationMs", instance.getDurationMs());
        AiSseMessageHelper.sendFlowStatus(userId, payload);
    }

    /**
     * 流程异常终态（非节点失败，如图解析/未知异常）
     */
    private void finishInstanceOnError(Long instanceId, Long flowId, Throwable e, String tenantIdStr, Long userId) {
        FlowInstance instance = new FlowInstance();
        instance.setId(instanceId);
        instance.setStatus(
                e instanceof FlowExecutionGovernance.RecoveryRequired
                        ? "RECOVERY_REQUIRED"
                        : "FAILED");
        instance.setErrorMsg(truncate(safeMessage(e), 2000));
        instance.setEndTime(new Date());
        TenantHelper.dynamic(
                tenantIdStr,
                () -> {
                    writeInstanceProjection(instance);
                    return null;
                });
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("flowId", flowId);
        payload.put("status", instance.getStatus());
        payload.put("errorMsg", truncate(safeMessage(e), 2000));
        payload.put("seq", nextSeq(instanceId));
        AiSseMessageHelper.sendFlowStatus(userId, payload);
    }

    private void sendNodeStart(Long instanceId, FlowNodeDef nodeDef, FlowNodeRun nodeRun, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("nodeId", nodeDef.getId());
        payload.put("nodeType", nodeDef.getType());
        payload.put("nodeName", nodeDef.getName());
        payload.put("retryCount", nodeRun.getRetryCount());
        payload.put("seq", nextSeq(instanceId));
        AiSseMessageHelper.sendFlowNodeStart(userId, payload);
    }

    private void sendNodeEnd(Long instanceId, FlowNodeDef nodeDef, FlowNodeRun nodeRun,
                             JsonNode output, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("nodeId", nodeDef.getId());
        payload.put("status", nodeRun.getStatus());
        payload.put("durationMs", nodeRun.getDurationMs());
        if (output != null) {
            payload.put("outputSummary", truncate(output.toString(), 500));
        }
        if (nodeRun.getErrorMsg() != null) {
            payload.put("errorMsg", nodeRun.getErrorMsg());
        }
        payload.put("seq", nextSeq(instanceId));
        AiSseMessageHelper.sendFlowNodeEnd(userId, payload);
    }
    private long nextSeq(Long instanceId) {
        AtomicLong seq = seqMap.get(instanceId);
        return seq == null ? 0 : seq.incrementAndGet();
    }

    /** 读取节点所属循环批次索引（split 场景：该节点上游 split 的当前 index；非循环返回 0） */
    private int readBatchIndex(FlowState state, String nodeId, FlowGraph graph) {
        JsonNode loops = state.getRoot().path("loops");
        if (loops.has(nodeId)) return Math.max(0, loops.path(nodeId).path("index").asInt());
        String anchor = graph.loopOwner(nodeId);
        return anchor == null ? 0 : Math.max(0, loops.path(anchor).path("index").asInt() - 1);
    }

    static void recordFailure(AtomicReference<Throwable> errors, Throwable error) {
        errors.getAndUpdate(
                previous ->
                        previous instanceof FlowExecutionGovernance.RecoveryRequired
                                ? previous
                                : error);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /**
     * 提取异常可读信息（message 为 null 时降级为类名，保证节点错误原因不丢失）
     */
    private String safeMessage(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            // CompletionException/部分包装异常的 message 为 null，展开 cause 链找首个非空信息
            Throwable cause = e.getCause();
            if (cause != null && cause != e) {
                return safeMessage(cause);
            }
            return e.getClass().getSimpleName();
        }
        return msg;
    }

    /** 只合并节点实际改动，保留并行兄弟节点已提交的状态。 */
    private static void mergeNodeState(ObjectNode before, ObjectNode after, ObjectNode target) {
        var fields = after.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode old = before.get(field.getKey()), value = field.getValue();
            if (Objects.equals(old, value)) continue;
            if (old instanceof ObjectNode a
                    && value instanceof ObjectNode b
                    && target.get(field.getKey()) instanceof ObjectNode c) mergeNodeState(a, b, c);
            else target.set(field.getKey(), value.deepCopy());
        }
        var names = before.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!after.has(key)) target.remove(key);
        }
    }
}
