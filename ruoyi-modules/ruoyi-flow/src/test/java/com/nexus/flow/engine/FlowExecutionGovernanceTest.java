package com.nexus.flow.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.governance.*;
import com.nexus.common.flow.domain.entity.*;
import com.nexus.common.flow.execution.*;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.model.FlowNodeDef;
import com.nexus.flow.node.*;
import com.nexus.flow.state.FlowState;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FlowExecutionGovernanceTest {
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    AiExecutionRepository repo;
    FlowExecutionGovernance gov;
    ExecutorService nodes;
    Clock clock;

    @BeforeEach
    void setup() throws Exception {
        ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
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
                        + " varchar(20),instance_id bigint,status varchar(32),error_msg"
                        + " varchar(2000),end_time timestamp)");
        jdbc.update(
                "INSERT INTO ai_flow_instance(id,tenant_id,status) VALUES (17,'000001','RUNNING')");
        repo = new AiExecutionRepository(ds, Clock.systemUTC());
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setFlowEnabled(true);
        nodes = Executors.newSingleThreadExecutor();
        gov =
                new FlowExecutionGovernance(
                        ds,
                        repo,
                        p,
                        mock(FlowNodeRegistry.class),
                        new FlowStopRegistry(),
                        Runnable::run,
                        nodes);
    }

    @Test
    void queuedProviderRechecksLeaseBeforeAnyExternalAction() throws Exception {
        var queued = new LinkedBlockingQueue<Runnable>();
        var holding =
                new AbstractExecutorService() {
                    public void shutdown() {}

                    public List<Runnable> shutdownNow() {
                        return List.of();
                    }

                    public boolean isShutdown() {
                        return false;
                    }

                    public boolean isTerminated() {
                        return false;
                    }

                    public boolean awaitTermination(long t, TimeUnit u) {
                        return true;
                    }

                    public void execute(Runnable r) {
                        queued.add(r);
                    }
                };
        var settings = new AiGovernanceProperties();
        settings.setEnabled(true);
        settings.setFlowEnabled(true);
        var control =
                new FlowExecutionGovernance(
                        ds,
                        repo,
                        settings,
                        mock(FlowNodeRegistry.class),
                        new FlowStopRegistry(),
                        Runnable::run,
                        holding);
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        var ctx = new FlowExecutionContext();
        ctx.setNodeId("external");
        ctx.setNodeType("http");
        var provider = mock(FlowNodeProvider.class);
        when(provider.replayPolicy(any())).thenReturn(FlowReplayPolicy.NON_REPLAYABLE);
        var effects = new AtomicInteger();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var outcome =
                    worker.submit(
                            () -> {
                                try (var scope = control.open(lease)) {
                                    return catchThrowable(
                                            () ->
                                                    control.execute(
                                                            provider,
                                                            ctx,
                                                            () -> {
                                                                effects.incrementAndGet();
                                                                return new ObjectMapper()
                                                                        .createObjectNode();
                                                            },
                                                            5000));
                                }
                            });
            Runnable pending = queued.poll(5, TimeUnit.SECONDS);
            assertThat(pending).isNotNull();
            jdbc.update(
                    "UPDATE ai_execution SET lease_until=? WHERE execution_id=?",
                    Timestamp.from(Instant.now().minusSeconds(1)),
                    id);
            pending.run();
            assertThat(outcome.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(effects).hasValue(0);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void expiredWaitAndLostCancellationReachRecoveryInsteadOfRemainingActive() {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        repo.park(lease, "{}", Instant.now().plusSeconds(60));
        jdbc.update(
                "UPDATE ai_execution SET deadline_at=? WHERE execution_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                id);
        gov.recover();
        assertThat(repo.get("000001", id).get("status")).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=17", String.class))
                .isEqualTo("FAILED");
        String next = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        repo.claim("000001", next, "two");
        repo.cancel("000001", 7L, next);
        jdbc.update(
                "UPDATE ai_execution SET lease_until=? WHERE execution_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                next);
        gov.recover();
        assertThat(repo.get("000001", next).get("status")).isEqualTo("RECOVERY_REQUIRED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=17", String.class))
                .isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    void parallelRecoveryErrorCannotBeOverwrittenByOrdinaryFailure() {
        var errors = new AtomicReference<Throwable>();
        FlowEngineImpl.recordFailure(
                errors, new FlowExecutionGovernance.RecoveryRequired("unknown"));
        FlowEngineImpl.recordFailure(errors, new IllegalStateException("ordinary"));
        assertThat(errors.get()).isInstanceOf(FlowExecutionGovernance.RecoveryRequired.class);
    }

    @Test
    void rejectedWaitRecoveryKeepsControlAndProjectionWaiting() {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        repo.park(lease, "{}", Instant.now().minusSeconds(1));
        jdbc.update("UPDATE ai_flow_instance SET status='WAITING' WHERE id=17");
        var settings = new AiGovernanceProperties();
        settings.setEnabled(true);
        settings.setFlowEnabled(true);
        var rejecting =
                new FlowExecutionGovernance(
                        ds,
                        repo,
                        settings,
                        mock(FlowNodeRegistry.class),
                        new FlowStopRegistry(),
                        r -> {
                            throw new RejectedExecutionException();
                        },
                        nodes);
        rejecting.recover();
        assertThat(repo.get("000001", id).get("status")).isEqualTo("WAITING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=17", String.class))
                .isEqualTo("WAITING");
    }

    @Test
    void interruptedExternalAttemptRemainsUncertainEvenAfterWorkerExit() throws Exception {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        var provider = mock(FlowNodeProvider.class);
        when(provider.replayPolicy(any())).thenReturn(FlowReplayPolicy.NON_REPLAYABLE);
        var ctx = new FlowExecutionContext();
        ctx.setNodeId("external");
        ctx.setNodeType("http");
        var entered = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var result = new AtomicReference<Throwable>();
        var worker =
                new Thread(
                        () -> {
                            try (var scope = gov.open(lease)) {
                                result.set(
                                        catchThrowable(
                                                () ->
                                                        gov.execute(
                                                                provider,
                                                                ctx,
                                                                () -> {
                                                                    entered.countDown();
                                                                    new CountDownLatch(1).await();
                                                                    return null;
                                                                },
                                                                30000)));
                            } finally {
                                Thread.interrupted();
                                finished.countDown();
                            }
                        });
        worker.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        worker.interrupt();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isInstanceOf(FlowExecutionGovernance.RecoveryRequired.class);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_flow_attempt", String.class))
                .isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    void twoIndependentLoopsUseTheirOwnCursorForOperationKeys() throws Exception {
        var json = new ObjectMapper();
        var graph =
                FlowGraph.parse(
                        "{\"nodes\":[{\"id\":\"A\",\"type\":\"split\"},{\"id\":\"a\",\"type\":\"http\"},{\"id\":\"B\",\"type\":\"split\"},{\"id\":\"b\",\"type\":\"http\"}],\"edges\":[{\"id\":\"a1\",\"source\":\"A\",\"target\":\"a\"},{\"id\":\"a2\",\"source\":\"a\",\"target\":\"A\",\"loop\":true},{\"id\":\"b1\",\"source\":\"B\",\"target\":\"b\"},{\"id\":\"b2\",\"source\":\"b\",\"target\":\"B\",\"loop\":true}]}");
        var state = new FlowState(json.createObjectNode());
        var loops = state.getRoot().putObject("loops");
        loops.putObject("B").put("index", 3);
        loops.putObject("A").put("index", 1);
        var engine = mock(FlowEngineImpl.class, Mockito.CALLS_REAL_METHODS);
        Integer first =
                ReflectionTestUtils.invokeMethod(engine, "readBatchIndex", state, "a", graph);
        loops.with("A").put("index", 2);
        Integer second =
                ReflectionTestUtils.invokeMethod(engine, "readBatchIndex", state, "a", graph);
        assertThat(first).isEqualTo(0);
        assertThat(second).isEqualTo(1);
    }

    @Test
    void interruptedTimeoutCleanupStillRecordsUnknownRunningWork() throws Exception {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        var provider = mock(FlowNodeProvider.class);
        when(provider.replayPolicy(any())).thenReturn(FlowReplayPolicy.NON_REPLAYABLE);
        var ctx = new FlowExecutionContext();
        ctx.setNodeId("external");
        ctx.setNodeType("http");
        var cancelled = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var done = new CountDownLatch(1);
        var result = new AtomicReference<Throwable>();
        var coordinator =
                new Thread(
                        () -> {
                            try (var scope = gov.open(lease)) {
                                result.set(
                                        catchThrowable(
                                                () ->
                                                        gov.execute(
                                                                provider,
                                                                ctx,
                                                                () -> {
                                                                    boolean exited = false;
                                                                    while (!exited)
                                                                        try {
                                                                            release.await();
                                                                            exited = true;
                                                                        } catch (
                                                                                InterruptedException
                                                                                        ignored) {
                                                                            cancelled.countDown();
                                                                        }
                                                                    return null;
                                                                },
                                                                30)));
                            } finally {
                                Thread.interrupted();
                                done.countDown();
                            }
                        });
        try {
            coordinator.start();
            assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
            coordinator.interrupt();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(result.get()).isInstanceOf(FlowExecutionGovernance.RecoveryRequired.class);
            assertThat(jdbc.queryForObject("SELECT status FROM ai_flow_attempt", String.class))
                    .isEqualTo("RECOVERY_REQUIRED");
        } finally {
            release.countDown();
            coordinator.join(5000);
        }
    }

    @AfterEach
    void close() {
        nodes.shutdownNow();
    }

    @Test
    void waitCheckpointsEntireLoopStateAndReleasesLease() throws Exception {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        var state = new FlowState(new ObjectMapper().createObjectNode());
        state.getRoot().putObject("loops").put("split", 3);
        var remaining = new HashMap<String, Integer>();
        remaining.put("wait", 0);
        var queue = new LinkedBlockingQueue<String>();
        queue.add("wait");
        var runs = new HashMap<String, FlowNodeRun>();
        var succeeded = new HashSet<String>();
        var def = new FlowNodeDef();
        def.setId("wait");
        def.setType("wait");
        def.setData(new ObjectMapper().readTree("{\"config\":{\"waitSeconds\":60}}"));
        try (var scope = gov.open(lease)) {
            assertThatThrownBy(
                            () -> gov.beforeNode(def, state, remaining, queue, runs, succeeded, 0))
                    .isInstanceOf(FlowExecutionGovernance.Suspended.class);
        }
        var row = repo.get("000001", id);
        assertThat(row.get("status")).isEqualTo("WAITING");
        assertThat(row.get("owner_id")).isNull();
        assertThat(repo.checkpoint(lease)).contains("loops").contains("split");
    }

    @Test
    void cancellingWaitConfirmsStopWithoutStartingAWorker() throws Exception {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        repo.park(lease, "{}", Instant.now().plusSeconds(60));
        assertThat(gov.stop("000001", 17L)).isTrue();
        assertThat(repo.get("000001", id).get("status")).isEqualTo("CANCELLED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM ai_flow_instance WHERE id=17", String.class))
                .isEqualTo("STOPPED");
    }

    @Test
    void nonCooperativeTimedOutNodeCannotBeRetriedOrPublishOutput() throws Exception {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var lease = repo.claim("000001", id, "one");
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        FlowNodeProvider provider = mock(FlowNodeProvider.class);
        when(provider.replayPolicy(any())).thenReturn(FlowReplayPolicy.NON_REPLAYABLE);
        var ctx = new FlowExecutionContext();
        ctx.setNodeId("external");
        ctx.setNodeType("http");
        ctx.setState(new FlowState(new ObjectMapper().createObjectNode()));
        try (var scope = gov.open(lease)) {
            assertThatThrownBy(
                            () ->
                                    gov.execute(
                                            provider,
                                            ctx,
                                            () -> {
                                                entered.countDown();
                                                boolean done = false;
                                                while (!done)
                                                    try {
                                                        release.await();
                                                        done = true;
                                                    } catch (InterruptedException ignored) {
                                                    }
                                                return new ObjectMapper()
                                                        .createObjectNode()
                                                        .put("late", true);
                                            },
                                            30))
                    .isInstanceOf(FlowExecutionGovernance.RecoveryRequired.class);
            assertThat(entered.getCount()).isZero();
            assertThat(ctx.getState().getRoot().has("late")).isFalse();
        } finally {
            release.countDown();
        }
    }
}
