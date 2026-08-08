package com.nexus.ai.flow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.flow.model.FlowEdgeDef;
import com.nexus.ai.flow.model.FlowGraph;
import com.nexus.ai.flow.model.FlowNodeDef;
import com.nexus.ai.flow.node.FlowExecutionContext;
import com.nexus.ai.flow.node.FlowNodeProvider;
import com.nexus.ai.flow.node.FlowNodeRegistry;
import com.nexus.ai.flow.state.FlowState;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.ai.domain.entity.AiFlowInstance;
import com.nexus.common.ai.domain.entity.AiFlowNodeRun;
import com.nexus.common.ai.mapper.AiFlowInstanceMapper;
import com.nexus.common.ai.mapper.AiFlowNodeRunMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 流程执行引擎实现
 * <p>调度模型（对齐 n8n WorkflowExecute.processRunExecutionData）：
 * BFS 就绪队列 + 单线程串行执行。节点成功 → 按 IF 结果/边路由扇出下游（入度-1，为 0 入队）；
 * 未选中分支节点标记 SKIPPED；任一节点最终失败（onError=STOP）→ 后续全部 SKIPPED，实例 FAILED</p>
 * <p>租户安全（本项目已知坑）：所有写库包 TenantHelper.dynamic；
 * AiRequestContext 流程级 set，节点内禁止 clear，finally 统一 clear</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEngineImpl implements FlowEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowNodeRegistry nodeRegistry;
    private final FlowStopRegistry stopRegistry;
    private final AiFlowInstanceMapper instanceMapper;
    private final AiFlowNodeRunMapper nodeRunMapper;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 实例内事件序号（SSE 防乱序，对齐 n8n nodeEventSequence） */
    private final Map<Long, AtomicLong> seqMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Long start(AiFlow flow, JsonNode input, String triggerType,
                      String tenantIdStr, Long userId, String userName) {
        // 1. 建实例（RUNNING）落库
        AiFlowInstance instance = new AiFlowInstance();
        instance.setFlowId(flow.getId());
        instance.setFlowVersion(flow.getVersion() == null ? 1 : flow.getVersion());
        instance.setTriggerType(triggerType);
        instance.setTriggerBy(userId);
        instance.setInputJson(input == null ? "{}" : input.toString());
        instance.setStatus("RUNNING");
        instance.setStartTime(new Date());
        Long instanceId = TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.insert(instance);
            return instance.getId();
        });

        // 2. 解析图 + 校验
        FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
        graph.validate(nodeRegistry::contains);

        // 3. 注册停止标志 + 事件序号
        stopRegistry.register(instanceId);
        seqMap.put(instanceId, new AtomicLong(0));

        // 4. 异步执行
        final Long userIdF = userId;
        aiExecutor.execute(() -> runFlow(flow, graph, instanceId, input, tenantIdStr, userIdF, userName));
        return instanceId;
    }

    @Override
    public boolean stop(Long instanceId) {
        return stopRegistry.requestStop(instanceId);
    }

    /**
     * 流程主循环（单线程串行）
     */
    private void runFlow(AiFlow flow, FlowGraph graph, Long instanceId, JsonNode input,
                         String tenantIdStr, Long userId, String userName) {
        // AiRequestContext 流程级设置（LLM 的 token 采集与工具租户隔离依赖它）
        AiRequestContext.set(Long.valueOf(tenantIdStr), userId, userName, null, null, null);
        boolean stopped = false;
        Throwable flowError = null;
        try {
            // 动态入度（初始化为静态入度；fanOut 时递减，为 0 时入队）
            Map<String, Integer> enqueued = new HashMap<>();
            for (String id : graph.getNodes().keySet()) {
                enqueued.put(id, graph.getInDegree(id));
            }

            // 就绪队列（入度 0 起点）
            Deque<String> queue = new ArrayDeque<>();
            for (String startId : graph.getStartNodeIds()) {
                queue.add(startId);
            }

            // 节点执行总次数护栏（防拓扑缺陷死循环，对齐 n8n endless loop 检测）
            int maxExecutions = graph.getNodeCount() * (flow.getMaxRetry() == null ? 1 : flow.getMaxRetry() + 1) + 1;
            AtomicInteger executedCount = new AtomicInteger(0);

            // 节点运行记录缓存（nodeId -> AiFlowNodeRun，用于状态落库）
            Map<String, AiFlowNodeRun> nodeRuns = new LinkedHashMap<>();

            // 已成功节点（用于输出）
            Set<String> succeededNodes = new HashSet<>();

            // 流程状态
            FlowState state = new FlowState(input);

            while (!queue.isEmpty()) {
                // 停止检查（协作式，节点边界）
                if (stopRegistry.isStopped(instanceId)) {
                    stopped = true;
                    markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                    break;
                }
                // 执行次数护栏
                if (executedCount.get() >= maxExecutions) {
                    throw new IllegalStateException("节点执行次数超过上限 " + maxExecutions + "，疑似拓扑缺陷或死循环");
                }

                String nodeId = queue.poll();
                FlowNodeDef nodeDef = graph.getNode(nodeId);
                FlowNodeProvider provider = nodeRegistry.getProvider(nodeDef.getType());
                if (provider == null) {
                    throw new IllegalStateException("未注册的节点类型: " + nodeDef.getType());
                }

                // 建节点运行记录（RUNNING）
                AiFlowNodeRun nodeRun = createNodeRun(instanceId, flow.getId(), nodeDef, tenantIdStr);
                nodeRuns.put(nodeId, nodeRun);
                sendNodeStart(instanceId, nodeDef, nodeRun, userId);

                try {
                    // 执行节点（带超时 + 重试）
                    JsonNode output = runNodeWithRetry(nodeDef, provider, state, instanceId, userId, flow, graph, executedCount);
                    // 成功
                    nodeRun.setStatus("SUCCEEDED");
                    nodeRun.setOutputJson(output == null ? "{}" : output.toString());
                    nodeRun.setErrorMsg(null);
                    updateNodeRun(nodeRun, tenantIdStr);
                    succeededNodes.add(nodeId);
                    state.putNodeOutput(nodeId, output);
                    sendNodeEnd(instanceId, nodeDef, nodeRun, output, userId);

                    // 扇出下游：按 IF 结果选边，选中边下游入度-1，为 0 入队
                    boolean ifResult = "if".equals(nodeDef.getType())
                        && output != null && output.has("result") ? output.get("result").asBoolean() : true;
                    fanOut(graph, nodeId, ifResult, enqueued, queue, nodeRuns, instanceId, flow.getId(), tenantIdStr);

                } catch (Exception e) {
                    // 节点失败
                    flowError = handleNodeFailure(nodeRun, nodeDef, e, tenantIdStr);
                    sendNodeEnd(instanceId, nodeDef, nodeRun, null, userId);

                    if ("CONTINUE".equals(nodeDef.getOnError())) {
                        // onError=CONTINUE: 输入透传作为输出（对齐 n8n continueRegularOutput），流程继续
                        com.fasterxml.jackson.databind.node.ObjectNode passthrough = MAPPER.createObjectNode();
                        passthrough.put("passthrough", true);
                        passthrough.put("error", e.getMessage());
                        nodeRun.setOutputJson(passthrough.toString());
                        updateNodeRun(nodeRun, tenantIdStr);
                        succeededNodes.add(nodeId);
                        state.putNodeOutput(nodeId, passthrough);
                        fanOut(graph, nodeId, true, enqueued, queue, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                        flowError = null; // 透传后不视为流程失败
                    } else {
                        // STOP: 未执行的全部节点标记 SKIPPED（含尚未入队的下游）
                        markAllRemainingSkipped(graph, nodeRuns, instanceId, flow.getId(), tenantIdStr);
                        break;
                    }
                }
            }

            // 终态落库
            finishInstance(instanceId, flow.getId(), state, stopped, flowError,
                nodeRuns, graph.getNodeCount(), tenantIdStr, userId);

        } catch (Throwable e) {
            log.error("[Flow] 流程执行异常 instanceId={}", instanceId, e);
            finishInstanceOnError(instanceId, flow.getId(), e, tenantIdStr, userId);
        } finally {
            stopRegistry.unregister(instanceId);
            seqMap.remove(instanceId);
            AiRequestContext.clear();
        }
    }

    /**
     * 执行节点（带重试 + 超时）
     */
    private JsonNode runNodeWithRetry(FlowNodeDef nodeDef, FlowNodeProvider provider, FlowState state,
                                      Long instanceId, Long userId, AiFlow flow, FlowGraph graph,
                                      AtomicInteger executedCount) throws Exception {
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
                ctx.setGraph(graph);

                // 带超时执行（Future.get）
                java.util.concurrent.Future<JsonNode> future = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return provider.execute(ctx);
                        } catch (Exception e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    });
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                lastError = new IllegalStateException("节点 " + nodeName + " 执行超时（" + timeoutSeconds + "s）");
            } catch (java.util.concurrent.ExecutionException e) {
                // 解包 CompletableFuture 的 CompletionException 嵌套，取底层 cause（否则 message 为 null 导致错误信息丢失）
                Throwable cause = e.getCause();
                while (cause instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) {
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
     * 扇出下游：按 IF 结果选边
     */
    private void fanOut(FlowGraph graph, String nodeId, boolean ifResult,
                        Map<String, Integer> enqueued, Deque<String> queue,
                        Map<String, AiFlowNodeRun> nodeRuns, Long instanceId, Long flowId, String tenantIdStr) {
        List<FlowEdgeDef> outEdges = graph.getOutEdges(nodeId);
        for (FlowEdgeDef edge : outEdges) {
            boolean pass = true;
            // IF 节点按分支选边
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
            String target = edge.getTarget();
            int remain = enqueued.merge(target, -1, Integer::sum);
            if (remain == 0) {
                queue.add(target);
            }
        }
    }

    /**
     * 标记分支未命中的下游节点为 SKIPPED（传入度簿记传播）
     * <p>语义：SKIPPED 节点视为"已结束无输出"——递减下游入度；归零时：
     * 下游为 merge → 入队真执行（只收集已到达的输入，对齐 n8n 空分支合并）；
     * 下游非 merge → 递归 SKIPPED（跳过链整段灰化）</p>
     */
    private void markBranchSkipped(String nodeId, FlowGraph graph,
                                   Map<String, Integer> enqueued, Deque<String> queue,
                                   Map<String, AiFlowNodeRun> nodeRuns, Long instanceId, Long flowId, String tenantIdStr) {
        if (nodeRuns.containsKey(nodeId)) {
            return;
        }
        FlowNodeDef def = graph.getNode(nodeId);
        AiFlowNodeRun nodeRun = createNodeRun(instanceId, flowId, def, tenantIdStr);
        nodeRun.setStatus("SKIPPED");
        nodeRun.setErrorMsg("上游分支未命中，跳过执行");
        nodeRun.setStartTime(new Date());
        nodeRun.setEndTime(new Date());
        nodeRun.setDurationMs(0L);
        TenantHelper.dynamic(tenantIdStr, () -> {
            nodeRunMapper.updateById(nodeRun);
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
    private void markAllRemainingSkipped(FlowGraph graph, Map<String, AiFlowNodeRun> nodeRuns,
                                         Long instanceId, Long flowId, String tenantIdStr) {
        for (String nodeId : graph.getNodes().keySet()) {
            if (nodeRuns.containsKey(nodeId)) {
                continue;
            }
            FlowNodeDef def = graph.getNode(nodeId);
            AiFlowNodeRun nodeRun = createNodeRun(instanceId, flowId, def, tenantIdStr);
            nodeRun.setStatus("SKIPPED");
            nodeRun.setErrorMsg("流程已停止或失败，跳过执行");
            nodeRun.setStartTime(new Date());
            nodeRun.setEndTime(new Date());
            nodeRun.setDurationMs(0L);
            TenantHelper.dynamic(tenantIdStr, () -> {
                nodeRunMapper.updateById(nodeRun);
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
    private Throwable handleNodeFailure(AiFlowNodeRun nodeRun, FlowNodeDef nodeDef,
                                        Exception e, String tenantIdStr) {
        nodeRun.setStatus("FAILED");
        nodeRun.setErrorMsg(truncate(safeMessage(e), 2000));
        nodeRun.setEndTime(new Date());
        if (nodeRun.getStartTime() != null) {
            nodeRun.setDurationMs(System.currentTimeMillis() - nodeRun.getStartTime().getTime());
        }
        TenantHelper.dynamic(tenantIdStr, () -> {
            nodeRunMapper.updateById(nodeRun);
            return null;
        });
        log.warn("[Flow] 节点 {} ({}) 执行失败: {}", nodeDef.getId(), nodeDef.getName(), e.getMessage(), e);
        return e;
    }

    /**
     * 建节点运行记录（PENDING→RUNNING，落库）
     */
    private AiFlowNodeRun createNodeRun(Long instanceId, Long flowId, FlowNodeDef nodeDef, String tenantIdStr) {
        AiFlowNodeRun nodeRun = new AiFlowNodeRun();
        nodeRun.setInstanceId(instanceId);
        nodeRun.setFlowId(flowId);
        nodeRun.setNodeId(nodeDef.getId());
        nodeRun.setNodeName(nodeDef.getName());
        nodeRun.setNodeType(nodeDef.getType().toUpperCase());
        nodeRun.setStatus("RUNNING");
        nodeRun.setRetryCount(0);
        nodeRun.setStartTime(new Date());
        TenantHelper.dynamic(tenantIdStr, () -> {
            nodeRunMapper.insert(nodeRun);
            return null;
        });
        return nodeRun;
    }

    /**
     * 更新节点运行记录（含耗时）
     */
    private void updateNodeRun(AiFlowNodeRun nodeRun, String tenantIdStr) {
        nodeRun.setEndTime(new Date());
        if (nodeRun.getStartTime() != null) {
            nodeRun.setDurationMs(System.currentTimeMillis() - nodeRun.getStartTime().getTime());
        }
        TenantHelper.dynamic(tenantIdStr, () -> {
            nodeRunMapper.updateById(nodeRun);
            return null;
        });
    }

    /**
     * 流程终态落库
     */
    private void finishInstance(Long instanceId, Long flowId, FlowState state, boolean stopped,
                                Throwable flowError, Map<String, AiFlowNodeRun> nodeRuns,
                                int nodeTotal, String tenantIdStr, Long userId) {
        // 读取实例（取 startTime 计算耗时）
        AiFlowInstance existing = TenantHelper.dynamic(tenantIdStr, () -> instanceMapper.selectById(instanceId));
        AiFlowInstance instance = new AiFlowInstance();
        instance.setId(instanceId);
        instance.setEndTime(new Date());
        if (existing != null && existing.getStartTime() != null) {
            instance.setDurationMs(System.currentTimeMillis() - existing.getStartTime().getTime());
        }

        int success = 0;
        int failed = 0;
        for (AiFlowNodeRun run : nodeRuns.values()) {
            if ("SUCCEEDED".equals(run.getStatus())) {
                success++;
            } else if ("FAILED".equals(run.getStatus())) {
                failed++;
            }
        }
        instance.setNodeTotal(nodeTotal);
        instance.setNodeSuccess(success);
        instance.setNodeFailed(failed);

        if (stopped) {
            instance.setStatus("STOPPED");
            instance.setErrorMsg("用户请求停止");
        } else if (flowError != null) {
            instance.setStatus("FAILED");
            instance.setErrorMsg(truncate(safeMessage(flowError), 2000));
        } else {
            instance.setStatus("SUCCEEDED");
            instance.setOutputJson(state.getFinalOutput().toString());
        }
        TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.updateById(instance);
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
        AiFlowInstance instance = new AiFlowInstance();
        instance.setId(instanceId);
        instance.setStatus("FAILED");
        instance.setErrorMsg(truncate(safeMessage(e), 2000));
        instance.setEndTime(new Date());
        TenantHelper.dynamic(tenantIdStr, () -> {
            instanceMapper.updateById(instance);
            return null;
        });
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("flowId", flowId);
        payload.put("status", "FAILED");
        payload.put("errorMsg", truncate(safeMessage(e), 2000));
        payload.put("seq", nextSeq(instanceId));
        AiSseMessageHelper.sendFlowStatus(userId, payload);
    }

    private void sendNodeStart(Long instanceId, FlowNodeDef nodeDef, AiFlowNodeRun nodeRun, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("nodeId", nodeDef.getId());
        payload.put("nodeType", nodeDef.getType());
        payload.put("nodeName", nodeDef.getName());
        payload.put("retryCount", nodeRun.getRetryCount());
        payload.put("seq", nextSeq(instanceId));
        AiSseMessageHelper.sendFlowNodeStart(userId, payload);
    }

    private void sendNodeEnd(Long instanceId, FlowNodeDef nodeDef, AiFlowNodeRun nodeRun,
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
}
