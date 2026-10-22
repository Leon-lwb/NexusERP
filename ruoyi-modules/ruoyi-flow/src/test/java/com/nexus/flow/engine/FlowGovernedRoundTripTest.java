package com.nexus.flow.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.governance.*;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.flow.domain.entity.*;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.execution.FlowReplayPolicy;
import com.nexus.common.flow.mapper.*;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.node.*;
import com.nexus.flow.node.control.SplitInBatchesProvider;
import com.nexus.flow.node.control.WaitNodeProvider;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** 真实引擎→控制表→等待→新引擎冷恢复→终态；仅租户框架和SSE出口使用替身。 */
class FlowGovernedRoundTripTest {
    final ObjectMapper json = new ObjectMapper();
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    MutableClock clock;
    AiExecutionRepository repo;
    FlowExecutionGovernance gov;
    FlowEngineImpl engine;
    AiGovernanceProperties settings;
    AiProperties properties;
    FlowInstanceMapper instances;
    FlowNodeRunMapper nodes;
    FlowMapper definitions;
    FlowNodeRegistry registry;
    FlowStopRegistry stops;
    AtomicInteger effects;
    AtomicLong ids;
    Map<Long, FlowInstance> saved;
    MockedStatic<TenantHelper> tenant;
    MockedStatic<AiSseMessageHelper> sse;
    InlinePool pool;

    @BeforeEach
    void setup() throws Exception {
        ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        clock = new MutableClock();
        repo = new AiExecutionRepository(ds, clock);
        for (String s :
                Files.readString(Path.of("../../script/sql/update/2026-10-22-ai-stage-two.sql"))
                        .replaceAll("(?m)^--.*$", "")
                        .split(";")) if (!s.isBlank()) jdbc.execute(s);
        jdbc.execute(
                "CREATE TABLE ai_flow_instance(id bigint PRIMARY KEY,tenant_id varchar(20),status"
                        + " varchar(32),error_msg varchar(2000),end_time timestamp,duration_ms"
                        + " bigint,node_total int,node_success int,node_failed int,output_json"
                        + " longtext)");
        jdbc.execute(
                "CREATE TABLE ai_flow_node_run(id bigint PRIMARY KEY,tenant_id"
                        + " varchar(20),instance_id bigint,status varchar(32),input_json"
                        + " longtext,output_json longtext,error_msg varchar(2000),end_time"
                        + " timestamp,duration_ms bigint,retry_count int)");
        tenant = mockStatic(TenantHelper.class);
        tenant.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                .thenAnswer(i -> i.getArgument(1, Supplier.class).get());
        tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
                .thenAnswer(
                        i -> {
                            i.getArgument(1, Runnable.class).run();
                            return null;
                        });
        tenant.when(TenantHelper::getTenantId).thenReturn("000001");
        sse = mockStatic(AiSseMessageHelper.class);
        effects = new AtomicInteger();
        ids = new AtomicLong(100);
        saved = new HashMap<>();
        instances = mock(FlowInstanceMapper.class);
        nodes = mock(FlowNodeRunMapper.class);
        definitions = mock(FlowMapper.class);
        when(instances.insert(any(FlowInstance.class)))
                .thenAnswer(
                        i -> {
                            FlowInstance value = i.getArgument(0);
                            value.setId(ids.incrementAndGet());
                            value.setTenantId("000001");
                            saved.put(value.getId(), value);
                            jdbc.update(
                                    "INSERT INTO ai_flow_instance(id,tenant_id,status) VALUES"
                                            + " (?,?,'RUNNING')",
                                    value.getId(),
                                    "000001");
                            return 1;
                        });
        when(instances.selectById(any()))
                .thenAnswer(
                        i -> {
                            Long id = i.getArgument(0);
                            FlowInstance value = saved.get(id);
                            if (value != null)
                                value.setStatus(
                                        jdbc.queryForObject(
                                                "SELECT status FROM ai_flow_instance WHERE id=?",
                                                String.class,
                                                id));
                            return value;
                        });
        when(nodes.insert(any(FlowNodeRun.class)))
                .thenAnswer(
                        i -> {
                            FlowNodeRun value = i.getArgument(0);
                            value.setId(ids.incrementAndGet());
                            value.setTenantId("000001");
                            jdbc.update(
                                    "INSERT INTO ai_flow_node_run(id,tenant_id,instance_id,status)"
                                            + " VALUES (?,?,?,?)",
                                    value.getId(),
                                    "000001",
                                    value.getInstanceId(),
                                    value.getStatus());
                            return 1;
                        });
        FlowNodeProvider provider =
                new FlowNodeProvider() {
                    public String getNodeType() {
                        return "record";
                    }

                    public String getDisplayName() {
                        return "记录";
                    }

                    public List<NodeParamSchema> getParamSchemas() {
                        return List.of();
                    }

                    public void validateConfig(JsonNode config) {
                        if (config == null || !config.isObject())
                            throw new IllegalArgumentException("record config");
                    }

                    public FlowReplayPolicy replayPolicy(JsonNode config) {
                        return FlowReplayPolicy.SAFE;
                    }

                    public JsonNode execute(FlowExecutionContext context) {
                        return json.createObjectNode().put("count", effects.incrementAndGet());
                    }
                };
        registry =
                new FlowNodeRegistry(
                        List.of(provider, new WaitNodeProvider(), new SplitInBatchesProvider()));
        registry.init();
        stops = new FlowStopRegistry();
        pool = new InlinePool();
        settings = new AiGovernanceProperties();
        settings.setEnabled(true);
        settings.setFlowEnabled(true);
        properties = new AiProperties();
        rebuild();
    }

    void rebuild() {
        gov = new FlowExecutionGovernance(ds, repo, settings, registry, stops, Runnable::run, pool);
        engine =
                new FlowEngineImpl(
                        registry,
                        stops,
                        instances,
                        nodes,
                        definitions,
                        properties,
                        Runnable::run,
                        pool);
        ReflectionTestUtils.setField(engine, "governance", gov);
        engine.registerRecovery();
    }

    Flow flow(boolean parallel) throws Exception {
        properties.getFlow().setParallel(parallel);
        Flow flow = new Flow();
        flow.setId(11L);
        flow.setVersion(1);
        flow.setMaxRetry(1);
        String edges =
                parallel
                        ? "[{\"id\":\"e2\",\"source\":\"wait\",\"target\":\"after\"}]"
                        : "[{\"id\":\"e1\",\"source\":\"before\",\"target\":\"wait\"},{\"id\":\"e2\",\"source\":\"wait\",\"target\":\"after\"}]";
        flow.setConfigJson(
                "{\"nodes\":[{\"id\":\"before\",\"type\":\"record\",\"data\":{\"config\":{}}},{\"id\":\"wait\",\"type\":\"wait\",\"data\":{\"config\":{\"waitSeconds\":60}}},{\"id\":\"after\",\"type\":\"record\",\"data\":{\"config\":{}}}],\"edges\":"
                        + edges
                        + "}");
        return flow;
    }

    @Test
    void serialWaitSurvivesColdRestartWithoutRepeatingCompletedNode() throws Exception {
        exercise(false);
    }

    @Test
    void parallelWaitRunsIndependentBranchAndSurvivesColdRestart() throws Exception {
        exercise(true);
    }

    void exercise(boolean parallel) throws Exception {
        Long id =
                engine.start(
                        flow(parallel), json.createObjectNode(), "MANUAL", "000001", 7L, "fixture");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=?", String.class, id))
                .isEqualTo("WAITING");
        assertThat(effects).hasValue(1);
        clock.advance(61);
        rebuild();
        properties.getFlow().setParallel(false);
        gov.recover();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=?", String.class, id))
                .isEqualTo("SUCCEEDED");
        assertThat(effects).hasValue(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ai_flow_node_run WHERE instance_id=? AND"
                                        + " status='SUCCEEDED'",
                                Integer.class,
                                id))
                .isEqualTo(3);
    }

    @Test
    void loopWaitRestoresBatchCursorAcrossTwoColdRestarts() throws Exception {
        Flow loop = new Flow();
        loop.setId(11L);
        loop.setVersion(1);
        loop.setMaxRetry(1);
        loop.setConfigJson(
                """
{"nodes":[{"id":"split","type":"split","data":{"config":{"arrayPath":"input.items","batchSize":1}}},{"id":"wait","type":"wait","data":{"config":{"waitSeconds":60}}},{"id":"body","type":"record","data":{"config":{}}},{"id":"done","type":"record","data":{"config":{}}}],"edges":[{"id":"a","source":"split","target":"wait","sourceHandle":"outputs/loop/main"},{"id":"b","source":"wait","target":"body"},{"id":"c","source":"body","target":"split","loop":true},{"id":"d","source":"split","target":"done","sourceHandle":"outputs/loop/done"}]}
""");
        Long id =
                engine.start(
                        loop,
                        json.readTree("{\"items\":[1,2]}"),
                        "MANUAL",
                        "000001",
                        7L,
                        "fixture");
        assertThat(effects).hasValue(0);
        clock.advance(61);
        rebuild();
        gov.recover();
        assertThat(effects).hasValue(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=?", String.class, id))
                .isEqualTo("WAITING");
        clock.advance(61);
        rebuild();
        gov.recover();
        assertThat(effects).hasValue(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=?", String.class, id))
                .isEqualTo("SUCCEEDED");
    }

    @AfterEach
    void cleanup() {
        if (tenant != null) tenant.close();
        if (sse != null) sse.close();
        pool.shutdownNow();
    }

    static class MutableClock extends Clock {
        Instant value = Instant.parse("2026-10-22T12:00:00Z");

        void advance(long s) {
            value = value.plusSeconds(s);
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return value;
        }
    }

    static class InlinePool extends AbstractExecutorService {
        boolean stopped;

        public void shutdown() {
            stopped = true;
        }

        public List<Runnable> shutdownNow() {
            stopped = true;
            return List.of();
        }

        public boolean isShutdown() {
            return stopped;
        }

        public boolean isTerminated() {
            return stopped;
        }

        public boolean awaitTermination(long t, TimeUnit u) {
            return stopped;
        }

        public void execute(Runnable task) {
            if (stopped) throw new RejectedExecutionException();
            task.run();
        }
    }
}
