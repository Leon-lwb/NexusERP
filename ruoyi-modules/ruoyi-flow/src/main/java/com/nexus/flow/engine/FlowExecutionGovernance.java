package com.nexus.flow.engine;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.service.governance.*;
import com.nexus.common.ai.governance.*;
import com.nexus.common.flow.domain.entity.*;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.execution.*;
import com.nexus.flow.adapter.DecisionFlowAccessGuard;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.model.FlowNodeDef;
import com.nexus.flow.node.*;
import com.nexus.flow.state.FlowState;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import javax.sql.DataSource;

/** Flow的持久化调度、租约围栏、完整检查点与不可重放副作用隔离。 */
@Service
@DS("master")
@Slf4j
public class FlowExecutionGovernance {
    @Autowired
    private AiExecutionAuthorization authorization;

    @Autowired(required = false)
    private RedissonClient redis;

    private Integer stopListener;

    private static final String STOP_CHANNEL = "ai:flow:governance:stop";

    public record StopNotice(String tenant, Long instance) {}

    @EventListener(ApplicationReadyEvent.class)
    public void subscribeStops() {
        if (!enabled() || redis == null) return;
        try {
            stopListener =
                    redis.getTopic(STOP_CHANNEL)
                            .addListener(
                                    StopNotice.class,
                                    (channel, notice) -> {
                                        for (var session : active.values())
                                            if (session.lease.tenant().equals(notice.tenant())
                                                    && session.lease
                                                            .sourceId()
                                                            .equals(notice.instance().toString()))
                                                stops.requestStop(notice.instance());
                                    });
        } catch (RuntimeException error) {
            log.warn("Flow停止广播不可用，使用数据库轮询");
        }
    }

    @PreDestroy
    public void unsubscribeStops() {
        if (redis != null && stopListener != null)
            try {
                redis.getTopic(STOP_CHANNEL).removeListener(stopListener);
            } catch (RuntimeException ignored) {
                /* 关闭期间数据库仍保存停止意图。 */
            }
    }

    public static final class Suspended extends Error {}

    public static final class RecoveryRequired extends Error {
        public RecoveryRequired(String message) {
            super(message);
        }
    }

    public record Recovery(FlowRunSnapshot snapshot, Long instanceId, String tenant, Long user) {}

    private final AiExecutionRepository repository;
    private final AiGovernanceProperties properties;
    private final FlowNodeRegistry registry;
    private final FlowStopRegistry stops;
    private final Executor coordinator;
    private final ExecutorService nodes;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper();
    private final String owner = UUID.randomUUID().toString();
    private final ConcurrentMap<String, Session> active = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Runnable> initial = new ConcurrentHashMap<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private volatile Consumer<Recovery> runner;
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    static final class Session {
        final AiExecutionRepository.Lease lease;
        FlowExecutionCheckpoint restored;
        String waitingNode;
        Long wakeAt;
        Map<String, Long> waits = new LinkedHashMap<>();
        List<String> inFlight = List.of();
        Boolean parallel;

        Session(AiExecutionRepository.Lease lease) {
            this.lease = lease;
        }
    }

    public final class Scope implements AutoCloseable {
        private final Session previous;
        private final AiInvocationScope invocation;

        Scope(Session session) {
            previous = CURRENT.get();
            CURRENT.set(session);
            Long instance = Long.valueOf(session.lease.sourceId());
            invocation =
                    AiInvocationScope.open(
                            new AiCallContext(
                                    session.lease.tenant(),
                                    session.lease.userId(),
                                    null,
                                    session.lease.id(),
                                    null,
                                    instance,
                                    null,
                                    null,
                                    "FLOW"),
                            () -> repository.check(session.lease));
        }

        public void close() {
            invocation.close();
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public FlowExecutionGovernance(
            DataSource source,
            AiExecutionRepository repository,
            AiGovernanceProperties properties,
            FlowNodeRegistry registry,
            FlowStopRegistry stops,
            @Qualifier("flowCoordinatorExecutor") Executor coordinator,
            @Qualifier("flowNodeExecutor") ExecutorService nodes) {
        source = AiMasterDataSource.resolve(source);
        jdbc = AiMasterDataSource.jdbc(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        this.repository = repository;
        this.properties = properties;
        this.registry = registry;
        this.stops = stops;
        this.coordinator = coordinator;
        this.nodes = nodes;
    }

    public boolean enabled() {
        return properties.flowActive();
    }

    public boolean attached() {
        return CURRENT.get() != null;
    }

    public void setRunner(Consumer<Recovery> runner) {
        this.runner = runner;
    }

    public Scope open(AiExecutionRepository.Lease lease) {
        var session = new Session(lease);
        String checkpoint = repository.checkpoint(lease);
        if (checkpoint != null && !checkpoint.isBlank()) {
            try {
                session.restored = json.readValue(checkpoint, FlowExecutionCheckpoint.class);
                session.waitingNode = session.restored.waitingNode();
                session.wakeAt = session.restored.wakeAt();
                if (session.restored.waits() != null)
                    session.waits.putAll(session.restored.waits());
                if (session.restored.inFlightNodes() != null)
                    session.inFlight = session.restored.inFlightNodes();
            } catch (Exception e) {
                throw new IllegalStateException("流程检查点无法读取");
            }
        }
        return new Scope(session);
    }

    public Runnable wrap(Runnable task) {
        var session = CURRENT.get();
        return session == null
                ? task
                : () -> {
                    try (var scope = new Scope(session)) {
                        task.run();
                    }
                };
    }

    public void check() {
        var s = CURRENT.get();
        if (s != null) {
            repository.check(s.lease);
            if (authorization != null) {
                String permission = "ai:flow:run";
                try {
                    var row = repository.get(s.lease.tenant(), s.lease.id());
                    var snapshot =
                            json.readValue(row.get("payload").toString(), FlowRunSnapshot.class);
                    if (DecisionFlowAccessGuard.protectedConfig(snapshot.config()))
                        permission = "ai:decision:run";
                } catch (JsonProcessingException invalid) {
                    throw new IllegalStateException("运行快照不合法");
                }
                authorization.require(s.lease.tenant(), s.lease.userId(), permission);
            }
        }
    }

    /** 运行模式只在首次执行时冻结；恢复不读取变化后的节点配置。 */
    public boolean parallel(boolean configured) {
        var s = CURRENT.get();
        if (s == null) return configured;
        if (s.parallel != null) return s.parallel;
        tx.executeWithoutResult(
                t -> {
                    var row = lock(s.lease);
                    repository.check(s.lease);
                    try {
                        var payload = (ObjectNode) json.readTree(row.get("payload").toString());
                        s.parallel =
                                payload.hasNonNull("parallel")
                                        ? payload.path("parallel").asBoolean()
                                        : configured;
                        payload.put("parallel", s.parallel);
                        jdbc.update(
                                "UPDATE ai_execution SET payload=? WHERE tenant_id=? AND"
                                        + " execution_id=?",
                                encode(payload),
                                s.lease.tenant(),
                                s.lease.id());
                    } catch (JsonProcessingException invalid) {
                        throw new IllegalStateException("运行快照无法读取");
                    }
                });
        return s.parallel;
    }

    private Map<String, Object> lock(AiExecutionRepository.Lease lease) {
        return jdbc.queryForMap(
                "SELECT * FROM ai_execution WHERE tenant_id=? AND execution_id=? FOR UPDATE",
                lease.tenant(),
                lease.id());
    }

    /** 创建投影也必须在执行行锁和有效执行权校验之内。 */
    public void createNode(Runnable insert) {
        var s = CURRENT.get();
        if (s == null) throw new IllegalStateException("缺少执行身份");
        tx.executeWithoutResult(
                t -> {
                    lock(s.lease);
                    repository.check(s.lease);
                    insert.run();
                });
    }

    private boolean park(Session s, String checkpoint, Instant wake) {
        return Boolean.TRUE.equals(
                tx.execute(
                        t -> {
                            lock(s.lease);
                            if (!repository.park(s.lease, checkpoint, wake)) return false;
                            jdbc.update(
                                    "UPDATE ai_flow_instance SET status='WAITING' WHERE tenant_id=?"
                                            + " AND id=?",
                                    s.lease.tenant(),
                                    Long.valueOf(s.lease.sourceId()));
                            return true;
                        }));
    }

    public FlowExecutionCheckpoint restored() {
        var s = CURRENT.get();
        return s == null ? null : s.restored;
    }

    public String executionKey(String node, int batch) {
        var s = CURRENT.get();
        return s == null ? null : s.lease.id() + ":" + node + ":" + batch;
    }

    public long eventBase() {
        var s = CURRENT.get();
        return s == null ? 0 : s.lease.generation() * 1000000000L;
    }

    public void linkRetry(String tenant, Long next, Long previous) {
        jdbc.update(
                "INSERT INTO"
                        + " ai_flow_retry_link(tenant_id,instance_id,retry_of_instance_id,created_at)"
                        + " VALUES (?,?,?,?)",
                tenant,
                next,
                previous,
                Timestamp.from(repository.currentInstant()));
    }

    public void launch(
            Flow flow,
            Long instance,
            JsonNode input,
            String tenant,
            Long user,
            String name,
            Runnable task) {
        launch(flow, instance, input, tenant, user, name, null, null, task);
    }

    public void launch(
            Flow flow,
            Long instance,
            JsonNode input,
            String tenant,
            Long user,
            String name,
            String destination,
            Long retryOf,
            Runnable task) {
        var snapshot =
                new FlowRunSnapshot(
                        flow.getId(),
                        flow.getVersion(),
                        flow.getFlowKey(),
                        flow.getConfigJson(),
                        flow.getMaxRetry(),
                        input == null ? json.createObjectNode() : input.deepCopy(),
                        name,
                        flow.getTriggerType(),
                        destination,
                        retryOf);
        String id =
                repository.create(
                        tenant, user, "FLOW", instance.toString(), null, encode(snapshot), 86400);
        initial.put(id, task);
        jdbc.update(
                "UPDATE ai_flow_instance SET status='QUEUED' WHERE tenant_id=? AND id=?",
                tenant,
                instance);
        dispatch(repository.get(tenant, id));
    }

    private void dispatch(Map<String, Object> row) {
        String id = row.get("execution_id").toString(), tenant = row.get("tenant_id").toString();
        if (!queued.add(id)) return;
        try {
            coordinator.execute(
                    () -> {
                        try {
                            var lease = repository.claim(tenant, id, owner);
                            if (lease == null) return;
                            try (var scope = open(lease)) {
                                var session = CURRENT.get();
                                active.put(id, session);
                                tx.executeWithoutResult(
                                        t -> {
                                            lock(lease);
                                            repository.check(lease);
                                            jdbc.update(
                                                    "UPDATE ai_flow_instance SET status='RUNNING'"
                                                            + " WHERE tenant_id=? AND id=?",
                                                    tenant,
                                                    Long.valueOf(lease.sourceId()));
                                        });
                                Runnable first = initial.remove(id);
                                if (first != null) first.run();
                                else {
                                    if (runner == null)
                                        throw new IllegalStateException("恢复执行器尚未注册");
                                    FlowRunSnapshot snapshot;
                                    try {
                                        snapshot =
                                                json.readValue(
                                                        row.get("payload").toString(),
                                                        FlowRunSnapshot.class);
                                    } catch (Exception e) {
                                        throw new IllegalStateException("运行快照无法读取");
                                    }
                                    runner.accept(
                                            new Recovery(
                                                    snapshot,
                                                    Long.valueOf(lease.sourceId()),
                                                    tenant,
                                                    lease.userId()));
                                }
                            } catch (Suspended parked) {
                                /* 租约已持久化释放，唤醒由扫描器认领。 */
                            } catch (Throwable error) {
                                try (var scope = open(lease)) {
                                    var instance = new FlowInstance();
                                    instance.setId(Long.valueOf(lease.sourceId()));
                                    instance.setStatus(
                                            error instanceof RecoveryRequired
                                                    ? "RECOVERY_REQUIRED"
                                                    : "FAILED");
                                    instance.setErrorMsg("执行未能确认完成，请核对运行记录");
                                    instance.setEndTime(new Date());
                                    commitInstance(instance);
                                } catch (RuntimeException ignored) {
                                    /* 旧所有者不得写新代次。 */
                                }
                            } finally {
                                active.remove(id);
                            }
                        } finally {
                            queued.remove(id);
                        }
                    });
        } catch (RejectedExecutionException busy) {
            queued.remove(id);
            initial.remove(id);
            tx.executeWithoutResult(
                    t -> {
                        lockRow(tenant, id);
                        if (repository.rejected(tenant, id)) {
                            jdbc.update(
                                    "UPDATE ai_flow_instance SET status='FAILED',error_msg='执行队列已满'"
                                            + " WHERE tenant_id=? AND id=?",
                                    tenant,
                                    Long.valueOf(row.get("source_id").toString()));
                            stops.unregister(Long.valueOf(row.get("source_id").toString()));
                        }
                    });
        }
    }

    /** 每次出队前保存整个根状态、动态入度、就绪队列与循环批次，不靠重跑重建。 */
    public void beforeNode(
            FlowNodeDef def,
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed) {
        if (!attached()) return;
        check();
        var s = CURRENT.get();
        if ("wait".equals(def.getType())) {
            int seconds = def.getConfig().path("waitSeconds").asInt();
            if (seconds > 0) {
                if (!def.getId().equals(s.waitingNode)) {
                    s.waitingNode = def.getId();
                    s.wakeAt = repository.currentInstant().plusSeconds(seconds).toEpochMilli();
                    s.waits.put(def.getId(), s.wakeAt);
                }
                if (s.wakeAt > repository.currentInstant().toEpochMilli()) {
                    var checkpoint =
                            checkpoint(
                                    state,
                                    remaining,
                                    queue,
                                    runs,
                                    succeeded,
                                    executed,
                                    def.getId());
                    if (!park(s, encode(checkpoint), Instant.ofEpochMilli(s.wakeAt)))
                        throw new IllegalStateException("等待节点已失去执行权");
                    throw new Suspended();
                }
            }
        }
        if (!repository.saveCheckpoint(
                s.lease,
                encode(
                        checkpoint(
                                state, remaining, queue, runs, succeeded, executed, def.getId()))))
            throw new IllegalStateException("检查点写入失去执行权");
    }

    public void afterStep(
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed) {
        if (!attached()) return;
        var s = CURRENT.get();
        if (s.waitingNode != null) s.waits.remove(s.waitingNode);
        s.waitingNode = null;
        s.wakeAt = null;
        s.inFlight = List.of();
        check();
        if (!repository.saveCheckpoint(
                s.lease,
                encode(checkpoint(state, remaining, queue, runs, succeeded, executed, null))))
            throw new IllegalStateException("检查点写入失去执行权");
    }

    private FlowExecutionCheckpoint checkpoint(
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed,
            String node) {
        var s = CURRENT.get();
        return new FlowExecutionCheckpoint(
                state.getRoot().deepCopy(),
                new HashMap<>(remaining),
                new ArrayList<>(queue),
                new HashMap<>(runs),
                new HashSet<>(succeeded),
                executed,
                node,
                s.waitingNode,
                s.wakeAt,
                new LinkedHashMap<>(s.waits),
                new ArrayList<>(s.inFlight));
    }

    /** 并行等待只挂起当前分支；存在其他就绪分支时继续执行，全部等待时才释放实例租约。 */
    public void prepareWave(
            FlowGraph graph,
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed) {
        if (!attached()) return;
        check();
        var s = CURRENT.get();
        long now = repository.currentInstant().toEpochMilli();
        for (var wait : s.waits.entrySet())
            if (wait.getValue() <= now
                    && !queue.contains(wait.getKey())
                    && !succeeded.contains(wait.getKey())) queue.add(wait.getKey());
        for (String node : new ArrayList<>(queue)) {
            var def = graph.getNode(node);
            if (!"wait".equals(def.getType())) continue;
            int seconds = def.getConfig().path("waitSeconds").asInt();
            if (seconds <= 0) continue;
            s.waits.putIfAbsent(node, now + seconds * 1000L);
            if (s.waits.get(node) > now) queue.remove(node);
        }
        if (queue.isEmpty() && !s.waits.isEmpty()) {
            s.wakeAt = Collections.min(s.waits.values());
            s.waitingNode =
                    s.waits.entrySet().stream()
                            .filter(e -> Objects.equals(e.getValue(), s.wakeAt))
                            .findFirst()
                            .orElseThrow()
                            .getKey();
            s.inFlight = List.of();
            var checkpoint = checkpoint(state, remaining, queue, runs, succeeded, executed, null);
            if (!park(s, encode(checkpoint), Instant.ofEpochMilli(s.wakeAt)))
                throw new IllegalStateException("并行等待失去执行权");
            throw new Suspended();
        }
    }

    public void beforeWave(
            List<String> wave,
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed) {
        if (!attached()) return;
        check();
        var s = CURRENT.get();
        s.inFlight = List.copyOf(wave);
        var ready = new LinkedBlockingQueue<String>(wave);
        ready.addAll(queue);
        if (!repository.saveCheckpoint(
                s.lease,
                encode(checkpoint(state, remaining, ready, runs, succeeded, executed, null))))
            throw new IllegalStateException("并行检查点失去执行权");
    }

    public void afterWave(
            FlowState state,
            Map<String, Integer> remaining,
            BlockingQueue<String> queue,
            Map<String, FlowNodeRun> runs,
            Set<String> succeeded,
            int executed) {
        if (!attached()) return;
        var s = CURRENT.get();
        s.waits.keySet().removeIf(succeeded::contains);
        s.waitingNode = null;
        s.wakeAt = null;
        s.inFlight = List.of();
        check();
        if (!repository.saveCheckpoint(
                s.lease,
                encode(checkpoint(state, remaining, queue, runs, succeeded, executed, null))))
            throw new IllegalStateException("并行检查点失去执行权");
    }

    /** 节点在独立有界池运行；超时不能证明退出时不重试，更不能合并迟到状态。 */
    public JsonNode execute(
            FlowNodeProvider provider,
            FlowExecutionContext context,
            Callable<JsonNode> action,
            long timeoutMillis)
            throws Exception {
        check();
        var s = CURRENT.get();
        if (s == null) throw new IllegalStateException("受治理节点缺少执行身份");
        if ("wait".equals(context.getNodeType()))
            return json.createObjectNode()
                    .put("waitedSeconds", context.getConfig().path("waitSeconds").asInt());
        String attempt = UUID.randomUUID().toString();
        FlowReplayPolicy policy = provider.replayPolicy(context.getConfig());
        if (policy == null) policy = FlowReplayPolicy.NON_REPLAYABLE;
        jdbc.update(
                "INSERT INTO"
                        + " ai_flow_attempt(attempt_id,tenant_id,execution_id,instance_id,generation,node_id,execution_key,replay_policy,status,started_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,'RUNNING',?)",
                attempt,
                s.lease.tenant(),
                s.lease.id(),
                Long.valueOf(s.lease.sourceId()),
                s.lease.generation(),
                context.getNodeId(),
                Objects.toString(
                        context.getExecutionKey(), s.lease.id() + ":" + context.getNodeId()),
                policy.name(),
                Timestamp.from(repository.currentInstant()));
        var exited = new CountDownLatch(1);
        var started = new AtomicBoolean();
        var aborted = new AtomicBoolean();
        var admission = new Object();
        Future<JsonNode> future;
        try {
            future =
                    nodes.submit(
                            () -> {
                                try {
                                    synchronized (admission) {
                                        if (aborted.get())
                                            throw new CancellationException("节点尝试已取消");
                                        started.set(true);
                                    }
                                    try (var scope = new Scope(s)) {
                                        check();
                                        return action.call();
                                    }
                                } finally {
                                    exited.countDown();
                                }
                            });
        } catch (RejectedExecutionException busy) {
            attempt(s, attempt, "FAILED", "EXECUTOR_BUSY");
            throw busy;
        }
        try {
            JsonNode result = future.get(Math.max(1, timeoutMillis), TimeUnit.MILLISECONDS);
            check();
            attempt(s, attempt, "SUCCEEDED", null);
            return result;
        } catch (TimeoutException timeout) {
            var exit = cancelAndAwait(future, admission, aborted, started, exited);
            boolean unknown =
                    !exit.gone() || (started.get() && policy == FlowReplayPolicy.NON_REPLAYABLE);
            try {
                attempt(s, attempt, unknown ? "RECOVERY_REQUIRED" : "FAILED", "TIMEOUT");
            } finally {
                if (exit.interrupted()) Thread.currentThread().interrupt();
            }
            if (unknown) throw new RecoveryRequired("节点超时，外部结果待核对");
            throw timeout;
        } catch (InterruptedException interrupted) {
            var exit = cancelAndAwait(future, admission, aborted, started, exited);
            boolean unknown =
                    !exit.gone() || (started.get() && policy == FlowReplayPolicy.NON_REPLAYABLE);
            try {
                attempt(s, attempt, unknown ? "RECOVERY_REQUIRED" : "CANCELLED", "INTERRUPTED");
            } finally {
                Thread.currentThread().interrupt();
            }
            if (unknown) throw new RecoveryRequired("中断后外部结果未确认");
            throw new CancellationException("流程执行被中断");
        } catch (ExecutionException failed) {
            attempt(
                    s,
                    attempt,
                    policy == FlowReplayPolicy.NON_REPLAYABLE ? "RECOVERY_REQUIRED" : "FAILED",
                    "NODE_ERROR");
            Throwable cause = failed.getCause();
            while (cause instanceof CompletionException wrapped && wrapped.getCause() != null)
                cause = wrapped.getCause();
            if (policy == FlowReplayPolicy.NON_REPLAYABLE)
                throw new RecoveryRequired("外部节点失败，结果须核对后再执行");
            if (cause instanceof CancellationException cancelled) throw cancelled;
            if (cause instanceof Exception error) throw error;
            throw new RecoveryRequired("节点未确认退出");
        }
    }

    private record ExitObservation(boolean gone, boolean interrupted) {}

    /** 取消与启动在同一临界区竞争；退出清理被中断也必须完成不确定性分类。 */
    private ExitObservation cancelAndAwait(
            Future<?> future,
            Object admission,
            AtomicBoolean aborted,
            AtomicBoolean started,
            CountDownLatch exited) {
        synchronized (admission) {
            aborted.set(true);
        }
        future.cancel(true);
        boolean interrupted = Thread.interrupted();
        if (!started.get()) return new ExitObservation(true, interrupted);
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (exited.getCount() != 0) {
            long remaining = end - System.nanoTime();
            if (remaining <= 0) break;
            try {
                if (exited.await(remaining, TimeUnit.NANOSECONDS)) break;
            } catch (InterruptedException again) {
                interrupted = true;
            }
        }
        return new ExitObservation(exited.getCount() == 0, interrupted);
    }

    private void attempt(Session s, String id, String status, String error) {
        jdbc.update(
                "UPDATE ai_flow_attempt SET status=?,error_code=?,ended_at=? WHERE tenant_id=? AND"
                        + " attempt_id=? AND generation=? AND EXISTS (SELECT 1 FROM ai_execution e"
                        + " WHERE e.execution_id=? AND e.owner_id=? AND e.generation=? AND"
                        + " e.lease_until>? AND e.status IN ('RUNNING','CANCEL_REQUESTED'))",
                status,
                error,
                Timestamp.from(repository.currentInstant()),
                s.lease.tenant(),
                id,
                s.lease.generation(),
                s.lease.id(),
                s.lease.owner(),
                s.lease.generation(),
                Timestamp.from(repository.currentInstant()));
    }

    /** 节点projection只由当前执行代次提交。 */
    public void updateNode(FlowNodeRun node) {
        var s = CURRENT.get();
        if (s == null) throw new IllegalStateException("缺少执行身份");
        int changed =
                jdbc.update(
                        "UPDATE ai_flow_node_run SET"
                                + " status=?,input_json=?,output_json=?,error_msg=?,end_time=?,duration_ms=?,retry_count=?"
                                + " WHERE tenant_id=? AND id=? AND EXISTS (SELECT 1 FROM ai_execution e"
                                + " WHERE e.execution_id=? AND e.tenant_id=? AND e.owner_id=? AND"
                                + " e.generation=? AND e.lease_until>? AND e.status IN"
                                + " ('RUNNING','CANCEL_REQUESTED'))",
                        node.getStatus(),
                        node.getInputJson(),
                        node.getOutputJson(),
                        node.getErrorMsg(),
                        node.getEndTime(),
                        node.getDurationMs(),
                        node.getRetryCount(),
                        s.lease.tenant(),
                        node.getId(),
                        s.lease.id(),
                        s.lease.tenant(),
                        s.lease.owner(),
                        s.lease.generation(),
                        Timestamp.from(repository.currentInstant()));
        if (changed != 1) throw new IllegalStateException("节点提交失去执行权");
    }

    /** 控制行锁与projection终态在同一事务内，取消竞争不会产生两个终态。 */
    public void commitInstance(FlowInstance instance) {
        var s = CURRENT.get();
        if (s == null) throw new IllegalStateException("缺少执行身份");
        tx.executeWithoutResult(
                t -> {
                    var r =
                            jdbc.queryForMap(
                                    "SELECT * FROM ai_execution WHERE tenant_id=? AND"
                                            + " execution_id=? FOR UPDATE",
                                    s.lease.tenant(),
                                    s.lease.id());
                    if (!s.lease.owner().equals(r.get("owner_id"))
                            || s.lease.generation() != ((Number) r.get("generation")).longValue()
                            || r.get("lease_until") == null
                            || !((Timestamp) r.get("lease_until"))
                                    .toInstant()
                                    .isAfter(repository.currentInstant()))
                        throw new IllegalStateException("实例提交失去执行权");
                    boolean cancelled = ((Number) r.get("cancel_requested")).intValue() != 0;
                    String status = instance.getStatus();
                    if (cancelled && !"RECOVERY_REQUIRED".equals(status)) status = "STOPPED";
                    instance.setStatus(status);
                    if ("SUCCEEDED".equals(status)
                            && !((Timestamp) r.get("deadline_at"))
                                    .toInstant()
                                    .isAfter(repository.currentInstant())) {
                        status = "FAILED";
                        instance.setStatus(status);
                        instance.setErrorMsg("流程超过截止时间");
                    }
                    String execution = "STOPPED".equals(status) ? "CANCELLED" : status;
                    String unfinished =
                            "RECOVERY_REQUIRED".equals(status)
                                    ? "RECOVERY_REQUIRED"
                                    : "STOPPED".equals(status) ? "CANCELLED" : "FAILED";
                    jdbc.update(
                            "UPDATE ai_flow_attempt SET"
                                    + " status=?,error_code='EXECUTION_ENDED',ended_at=? WHERE"
                                    + " tenant_id=? AND execution_id=? AND status='RUNNING'",
                            unfinished,
                            Timestamp.from(repository.currentInstant()),
                            s.lease.tenant(),
                            s.lease.id());
                    jdbc.update(
                            "UPDATE ai_flow_node_run SET"
                                    + " status=?,error_msg='执行已结束，请查看尝试记录',end_time=? WHERE tenant_id=?"
                                    + " AND instance_id=? AND status IN ('RUNNING','PENDING')",
                            unfinished,
                            new Date(),
                            s.lease.tenant(),
                            instance.getId());
                    if (!repository.finish(
                            s.lease,
                            execution,
                            instance.getErrorMsg() == null ? null : "FLOW_ERROR"))
                        throw new IllegalStateException("实例终态竞争失败");
                    jdbc.update(
                            "UPDATE ai_flow_instance SET"
                                    + " status=?,error_msg=?,end_time=?,duration_ms=COALESCE(?,duration_ms),node_total=COALESCE(?,node_total),node_success=COALESCE(?,node_success),node_failed=COALESCE(?,node_failed),output_json=COALESCE(?,output_json)"
                                    + " WHERE tenant_id=? AND id=?",
                            status,
                            instance.getErrorMsg(),
                            instance.getEndTime(),
                            instance.getDurationMs(),
                            instance.getNodeTotal(),
                            instance.getNodeSuccess(),
                            instance.getNodeFailed(),
                            instance.getOutputJson(),
                            s.lease.tenant(),
                            instance.getId());
                });
    }

    public boolean stop(String tenant, Long instance) {
        var rows = repository.bySource(tenant, "FLOW", instance.toString());
        if (rows.isEmpty()) return false;
        var r = rows.get(0);
        String status =
                tx.execute(
                        t -> {
                            lockRow(tenant, r.get("execution_id").toString());
                            var result =
                                    repository.cancel(
                                            tenant,
                                            ((Number) r.get("user_id")).longValue(),
                                            r.get("execution_id").toString());
                            String projected =
                                    "CANCELLED".equals(result.get("status"))
                                            ? "STOPPED"
                                            : "CANCEL_REQUESTED".equals(result.get("status"))
                                                    ? "STOP_REQUESTED"
                                                    : null;
                            if (projected != null)
                                jdbc.update(
                                        "UPDATE ai_flow_instance SET status=? WHERE tenant_id=? AND"
                                                + " id=?",
                                        projected,
                                        tenant,
                                        instance);
                            return projected;
                        });
        if (status != null) {
            stops.requestStop(instance);
            try {
                if (redis != null)
                    redis.getTopic(STOP_CHANNEL).publish(new StopNotice(tenant, instance));
            } catch (RuntimeException unavailable) {
                log.debug("停止请求已持久化，广播暂不可用");
            }
            return true;
        }
        return false;
    }

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        if (!enabled()) return;
        for (var s : active.values())
            try {
                repository.heartbeat(s.lease);
            } catch (RuntimeException ignored) {
                /* 围栏阻止租约到期后的提交。 */
            }
    }

    @Scheduled(fixedDelay = 5000)
    public void recover() {
        if (!enabled()) return;
        for (var row : repository.expired("FLOW")) {
            String prior = row.get("status").toString();
            recoverRow(
                    row,
                    Set.of("QUEUED", "WAITING").contains(prior) ? "FAILED" : "RECOVERY_REQUIRED",
                    "OWNER_LOST_OR_DEADLINE");
        }
        for (var row : repository.due("FLOW")) {
            String checkpoint = Objects.toString(row.get("checkpoint"), "");
            boolean safe = true;
            try {
                if ("RUNNING".equals(row.get("status"))) {
                    if (checkpoint.isBlank()) safe = false;
                    else {
                        var cp = json.readValue(checkpoint, FlowExecutionCheckpoint.class);
                        var pending = new ArrayList<String>();
                        if (cp.currentNode() != null) pending.add(cp.currentNode());
                        if (cp.inFlightNodes() != null) pending.addAll(cp.inFlightNodes());
                        for (String pendingNode : pending) {
                            var snap =
                                    json.readValue(
                                            row.get("payload").toString(), FlowRunSnapshot.class);
                            var graph = FlowGraph.parse(snap.config());
                            var def = graph.getNode(pendingNode);
                            var provider = registry.getProvider(def.getType());
                            safe =
                                    safe
                                            && provider != null
                                            && provider.replayPolicy(def.getConfig()) != null
                                            && provider.replayPolicy(def.getConfig())
                                                    != FlowReplayPolicy.NON_REPLAYABLE;
                        }
                    }
                }
            } catch (Exception error) {
                safe = false;
            }
            if (safe) dispatch(row);
            else {
                recoverRow(row, "RECOVERY_REQUIRED", "UNCONFIRMED_NODE");
            }
        }
    }

    private Map<String, Object> lockRow(String tenant, String id) {
        return jdbc.queryForMap(
                "SELECT * FROM ai_execution WHERE tenant_id=? AND execution_id=? FOR UPDATE",
                tenant,
                id);
    }

    private void recoverRow(Map<String, Object> observed, String target, String reason) {
        tx.executeWithoutResult(
                t -> {
                    String tenant = observed.get("tenant_id").toString(),
                            id = observed.get("execution_id").toString();
                    var current = lockRow(tenant, id);
                    if (!Objects.equals(current.get("status"), observed.get("status"))
                            || !Objects.equals(
                                    current.get("generation"), observed.get("generation"))) return;
                    Instant instant = repository.currentInstant();
                    boolean expired =
                            !((Timestamp) current.get("deadline_at")).toInstant().isAfter(instant);
                    boolean lost =
                            current.get("lease_until") != null
                                    && !((Timestamp) current.get("lease_until"))
                                            .toInstant()
                                            .isAfter(instant);
                    if (!expired && !lost) return;
                    if (jdbc.update(
                                    "UPDATE ai_execution SET"
                                            + " status=?,error_code=?,lease_until=NULL,updated_at=?"
                                            + " WHERE tenant_id=? AND execution_id=? AND status=? AND"
                                            + " generation=?",
                                    target,
                                    reason,
                                    Timestamp.from(instant),
                                    tenant,
                                    id,
                                    current.get("status"),
                                    current.get("generation"))
                            != 1) return;
                    Long instance = Long.valueOf(current.get("source_id").toString());
                    jdbc.update(
                            "UPDATE ai_flow_instance SET"
                                    + " status=?,error_msg='执行者丢失或超过截止时间，请核对运行记录',end_time=? WHERE"
                                    + " tenant_id=? AND id=?",
                            target,
                            Timestamp.from(instant),
                            tenant,
                            instance);
                    jdbc.update(
                            "UPDATE ai_flow_attempt SET"
                                    + " status='RECOVERY_REQUIRED',error_code=?,ended_at=? WHERE"
                                    + " tenant_id=? AND execution_id=? AND status='RUNNING'",
                            reason,
                            Timestamp.from(instant),
                            tenant,
                            id);
                    jdbc.update(
                            "UPDATE ai_flow_node_run SET"
                                    + " status=?,error_msg='执行者丢失，请核对尝试记录',end_time=? WHERE tenant_id=?"
                                    + " AND instance_id=? AND status IN ('RUNNING','PENDING')",
                            target,
                            Timestamp.from(instant),
                            tenant,
                            instance);
                });
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("执行快照编码失败");
        }
    }
}
