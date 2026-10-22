package com.nexus.flow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.flow.node.NodeParamSchema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * P1-6: 流程引擎调度逻辑测试（mock 依赖，验证 BFS 顺序/IF 分支/SKIPPED/onError）
 * <p>注意：FlowNodeRun 由引擎 new 后插入 mapper（mock 不落库），
 * 测试通过捕获 insert 参数断言状态流转</p>
 *
 * @author NexusERP
 */
class FlowEngineSchedulingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<AiSseMessageHelper> sseHelperMock;
    private FlowInstanceMapper instanceMapper;
    private FlowNodeRunMapper nodeRunMapper;
    private List<FlowNodeRun> insertedRuns;
    private List<FlowInstance> insertedInstances;

    @BeforeEach
    void setUp() {
        // 捕获 mapper 写入参数
        insertedRuns = new ArrayList<>();
        insertedInstances = new ArrayList<>();

        // 模拟 TenantHelper.dynamic 直接执行 handle（不切租户）
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> {
                Runnable r = inv.getArgument(1);
                r.run();
                return null;
            });
        tenantHelperMock
                .when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> inv.getArgument(1, Supplier.class).get());

        // 模拟 SSE 推送（单测环境无 Spring/Redis，SseMessageUtils 静态初始化会失败）
        sseHelperMock = Mockito.mockStatic(AiSseMessageHelper.class);
        sseHelperMock
                .when(() -> AiSseMessageHelper.sendFlowStatus(any(), any()))
                .thenAnswer(inv -> null);
        sseHelperMock
                .when(() -> AiSseMessageHelper.sendFlowNodeStart(any(), any()))
                .thenAnswer(inv -> null);
        sseHelperMock
                .when(() -> AiSseMessageHelper.sendFlowNodeEnd(any(), any()))
                .thenAnswer(inv -> null);

        instanceMapper = mock(FlowInstanceMapper.class);
        nodeRunMapper = mock(FlowNodeRunMapper.class);

        when(instanceMapper.insert(any(FlowInstance.class))).thenAnswer(inv -> {
            FlowInstance inst = inv.getArgument(0);
            inst.setId(1L); // 模拟雪花 ID
            insertedInstances.add(inst);
            return 1;
        });
        // selectById 返回最新引用（同 insert 的对象）
        when(instanceMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return insertedInstances.stream().filter(i -> id.equals(i.getId())).findFirst().orElse(null);
        });
        // updateById 模拟更新：替换实例引用（保证终态落库后引用一致）
        when(instanceMapper.updateById(any(FlowInstance.class))).thenAnswer(inv -> {
            FlowInstance upd = inv.getArgument(0);
            for (int i = 0; i < insertedInstances.size(); i++) {
                if (insertedInstances.get(i).getId().equals(upd.getId())) {
                    insertedInstances.set(i, upd);
                    break;
                }
            }
            return 1;
        });
        // 节点运行记录 updateById 更新引用
        when(nodeRunMapper.updateById(any(FlowNodeRun.class))).thenAnswer(inv -> {
            FlowNodeRun upd = inv.getArgument(0);
            for (int i = 0; i < insertedRuns.size(); i++) {
                if (insertedRuns.get(i).getId().equals(upd.getId())) {
                    insertedRuns.set(i, upd);
                    break;
                }
            }
            return 1;
        });
        when(nodeRunMapper.insert(any(FlowNodeRun.class))).thenAnswer(inv -> {
            FlowNodeRun run = inv.getArgument(0);
            run.setId((long) (insertedRuns.size() + 1));
            insertedRuns.add(run);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
        sseHelperMock.close();
    }

    /**
     * 同步执行器（测试直接在当前线程跑）
     */
    private Executor syncExecutor() {
        return Runnable::run;
    }

    /**
     * 构造可编程节点（每类节点一个实例，execute 按 nodeId 分派）
     */
    private FlowNodeProvider providerWith(String nodeId, JsonNode output, boolean fail) {
        return new FlowNodeProvider() {
            @Override
            public String getNodeType() {
                return "code";
            }

            @Override
            public String getDisplayName() {
                return "Code";
            }

            @Override
            public List<NodeParamSchema> getParamSchemas() {
                return List.of();
            }

            @Override
            public void validateConfig(JsonNode config) {
            }

            @Override
            public JsonNode execute(FlowExecutionContext ctx) {
                if (fail) {
                    throw new IllegalStateException(nodeId + " 故意失败");
                }
                return output;
            }
        };
    }

    /**
     * 构造流程定义
     */
    private Flow flow(String configJson) {
        Flow flow = new Flow();
        flow.setId(10L);
        flow.setVersion(1);
        flow.setFlowName("测试流程");
        flow.setMaxRetry(1);
        flow.setConfigJson(configJson);
        return flow;
    }

    /**
     * 构造引擎（注入 mock 依赖）
     */
    private FlowEngineImpl engine(FlowNodeProvider provider) {
        // 用简单 registry 直接注入（绕过 Spring）
        FlowNodeRegistry registry = mock(FlowNodeRegistry.class);
        when(registry.getProvider(anyString())).thenReturn(provider);
        when(registry.contains(anyString())).thenReturn(true);

        FlowStopRegistry stopRegistry = new FlowStopRegistry();
        FlowMapper flowMapper = mock(FlowMapper.class);
        AiProperties aiProperties = new AiProperties();
        ExecutorService flowExecutor = Executors.newSingleThreadExecutor();
        return new FlowEngineImpl(registry, stopRegistry, instanceMapper, nodeRunMapper, flowMapper, aiProperties,
            syncExecutor(), flowExecutor);
    }

    @Test
    void genericEngineEntryCannotLaunchForgedIndustrialNodeWithRunId() {
        var engine = engine(providerWith("steal", MAPPER.createObjectNode(), false));
        var malicious = flow("{\"nodes\":[{\"id\":\"steal\",\"type\":\"industrial.executeApprovedActions\",\"data\":{\"config\":{}}}],\"edges\":[]}");
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.start(
                                malicious,
                                MAPPER.createObjectNode().put("runId", "99"),
                                "MANUAL",
                                "000000",
                                1L,
                                "admin"));
        assertTrue(insertedInstances.isEmpty());
    }

    @Test
    void invalidGraphNeverLeavesOrphanRunningInstance() {
        var engine = engine(providerWith("a", MAPPER.createObjectNode(), false));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.start(
                                flow("{}"),
                                MAPPER.createObjectNode(),
                                "MANUAL",
                                "000000",
                                1L,
                                "admin"));
        assertTrue(insertedInstances.isEmpty());
    }

    @Test
    @DisplayName("P1-6: 串行 BFS 执行顺序正确（A→B→C）")
    void linear_flow_executesInOrder() throws Exception {
        String configJson = """
            {
              "nodes": [
                {"id": "a", "type": "code", "position": [0, 0], "data": {"label": "A"}},
                {"id": "b", "type": "code", "position": [0, 1], "data": {"label": "B"}},
                {"id": "c", "type": "code", "position": [0, 2], "data": {"label": "C"}}
              ],
              "edges": [
                {"id": "e1", "source": "a", "target": "b", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"},
                {"id": "e2", "source": "b", "target": "c", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"}
              ]
            }
            """;

        // 记录执行顺序
        List<String> execOrder = new ArrayList<>();
        FlowNodeProvider provider = new FlowNodeProvider() {
            @Override
            public String getNodeType() {
                return "code";
            }

            @Override
            public String getDisplayName() {
                return "Code";
            }

            @Override
            public List<NodeParamSchema> getParamSchemas() {
                return List.of();
            }

            @Override
            public void validateConfig(JsonNode config) {
            }

            @Override
            public JsonNode execute(FlowExecutionContext ctx) {
                execOrder.add(ctx.getNodeId());
                return MAPPER.createObjectNode().put("ok", true);
            }
        };

        FlowEngineImpl engine = engine(provider);
        Long instanceId = engine.start(flow(configJson), MAPPER.createObjectNode(), "MANUAL", "000000", 1L, "admin");

        // 同步执行器下 start 已执行完
        assertEquals(List.of("a", "b", "c"), execOrder);
        FlowInstance instance = insertedInstances.stream()
            .filter(i -> i.getId().equals(instanceId)).findFirst().orElseThrow();
        assertEquals("SUCCEEDED", instance.getStatus());
        assertEquals(3, instance.getNodeSuccess());
        // 3 个节点运行记录全 SUCCEEDED
        assertEquals(3, insertedRuns.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count());
    }

    @Test
    @DisplayName("P1-6: IF 分支选边，未命中分支 SKIPPED")
    void ifBranch_unmatchedBranchSkipped() throws Exception {
        // IF 输出 result=false → false 分支节点执行，true 分支 SKIPPED
        String configJson = """
            {
              "nodes": [
                {"id": "if1", "type": "if", "position": [0, 0], "data": {"label": "判断"}},
                {"id": "true_node", "type": "code", "position": [0, 1], "data": {"label": "真分支"}},
                {"id": "false_node", "type": "code", "position": [0, 2], "data": {"label": "假分支"}}
              ],
              "edges": [
                {"id": "e1", "source": "if1", "target": "true_node", "sourceHandle": "outputs/if/true", "targetHandle": "inputs/main/0"},
                {"id": "e2", "source": "if1", "target": "false_node", "sourceHandle": "outputs/if/false", "targetHandle": "inputs/main/0"}
              ]
            }
            """;

        List<String> execOrder = new ArrayList<>();
        FlowNodeProvider provider = new FlowNodeProvider() {
            @Override
            public String getNodeType() {
                return "code";
            }

            @Override
            public String getDisplayName() {
                return "Code";
            }

            @Override
            public List<NodeParamSchema> getParamSchemas() {
                return List.of();
            }

            @Override
            public void validateConfig(JsonNode config) {
            }

            @Override
            public JsonNode execute(FlowExecutionContext ctx) {
                if ("if1".equals(ctx.getNodeId())) {
                    // IF 节点输出 result=false
                    return MAPPER.createObjectNode().put("result", false);
                }
                execOrder.add(ctx.getNodeId());
                return MAPPER.createObjectNode().put("ok", true);
            }
        };

        FlowEngineImpl engine = engine(provider);
        engine.start(flow(configJson), MAPPER.createObjectNode(), "MANUAL", "000000", 1L, "admin");

        // false 分支执行，true 分支 SKIPPED
        assertEquals(List.of("false_node"), execOrder);
        assertTrue(insertedRuns.stream().anyMatch(r -> "false_node".equals(r.getNodeId()) && "SUCCEEDED".equals(r.getStatus())));
        assertTrue(insertedRuns.stream().anyMatch(r -> "true_node".equals(r.getNodeId()) && "SKIPPED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("P1-6: 节点失败 onError=STOP → 后续 SKIPPED，实例 FAILED")
    void nodeFailure_stop_marksRemainingSkipped() {
        String configJson = """
            {
              "nodes": [
                {"id": "a", "type": "code", "position": [0, 0], "data": {"label": "A"}},
                {"id": "b", "type": "code", "position": [0, 1], "data": {"label": "B"}}
              ],
              "edges": [
                {"id": "e1", "source": "a", "target": "b", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"}
              ]
            }
            """;

        FlowNodeProvider provider = new FlowNodeProvider() {
            @Override
            public String getNodeType() {
                return "code";
            }

            @Override
            public String getDisplayName() {
                return "Code";
            }

            @Override
            public List<NodeParamSchema> getParamSchemas() {
                return List.of();
            }

            @Override
            public void validateConfig(JsonNode config) {
            }

            @Override
            public JsonNode execute(FlowExecutionContext ctx) {
                if ("a".equals(ctx.getNodeId())) {
                    throw new IllegalStateException("A 故意失败");
                }
                return MAPPER.createObjectNode().put("ok", true);
            }
        };

        FlowEngineImpl engine = engine(provider);
        engine.start(flow(configJson), MAPPER.createObjectNode(), "MANUAL", "000000", 1L, "admin");

        FlowInstance instance = insertedInstances.get(0);
        assertEquals("FAILED", instance.getStatus());
        assertTrue(instance.getErrorMsg().contains("A 故意失败"));
        assertTrue(insertedRuns.stream().anyMatch(r -> "a".equals(r.getNodeId()) && "FAILED".equals(r.getStatus())));
        assertTrue(insertedRuns.stream().anyMatch(r -> "b".equals(r.getNodeId()) && "SKIPPED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("P1-6: 节点失败 onError=CONTINUE → 流程继续，实例 SUCCEEDED")
    void nodeFailure_continue_flowProceeds() {
        String configJson = """
            {
              "nodes": [
                {"id": "a", "type": "code", "position": [0, 0], "data": {"label": "A", "onError": "CONTINUE"}},
                {"id": "b", "type": "code", "position": [0, 1], "data": {"label": "B"}}
              ],
              "edges": [
                {"id": "e1", "source": "a", "target": "b", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"}
              ]
            }
            """;

        List<String> execOrder = new ArrayList<>();
        FlowNodeProvider provider = new FlowNodeProvider() {
            @Override
            public String getNodeType() {
                return "code";
            }

            @Override
            public String getDisplayName() {
                return "Code";
            }

            @Override
            public List<NodeParamSchema> getParamSchemas() {
                return List.of();
            }

            @Override
            public void validateConfig(JsonNode config) {
            }

            @Override
            public JsonNode execute(FlowExecutionContext ctx) {
                if ("a".equals(ctx.getNodeId())) {
                    throw new IllegalStateException("A 故意失败");
                }
                execOrder.add(ctx.getNodeId());
                return MAPPER.createObjectNode().put("ok", true);
            }
        };

        FlowEngineImpl engine = engine(provider);
        engine.start(flow(configJson), MAPPER.createObjectNode(), "MANUAL", "000000", 1L, "admin");

        // B 仍执行，实例 SUCCEEDED
        assertEquals(List.of("b"), execOrder);
        FlowInstance instance = insertedInstances.get(0);
        assertEquals("SUCCEEDED", instance.getStatus());
        // A 的 FAILED 状态保留（run 记录仍标记失败）
        assertTrue(insertedRuns.stream().anyMatch(r -> "a".equals(r.getNodeId()) && "FAILED".equals(r.getStatus())));
    }
}
