package com.nexus.ai.service.governance;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.ai.domain.entity.AiMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.CancellationException;

import javax.sql.DataSource;

/** 持久化执行所有权。所有写入均携带租户、所有者与执行代次，不以本地Future代替事实。 */
@Repository
@DS("master")
public class AiExecutionRepository {
    public record Lease(
            String id,
            String tenant,
            Long userId,
            String kind,
            String sourceId,
            Long sessionId,
            String owner,
            long generation) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate completionTx;
    private final Clock clock;
    private static final String FENCE =
            "tenant_id=? AND execution_id=? AND owner_id=? AND generation=? AND lease_until>?";

    @Autowired
    public AiExecutionRepository(DataSource source) {
        this(source, null);
    }

    public AiExecutionRepository(DataSource source, Clock clock) {
        source = AiMasterDataSource.resolve(source);
        jdbc = AiMasterDataSource.jdbc(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        completionTx = new TransactionTemplate(new DataSourceTransactionManager(source));
        completionTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public Instant currentInstant() {
        return clock == null
                ? jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(3)", Timestamp.class).toInstant()
                : clock.instant();
    }

    private Timestamp now() {
        return Timestamp.from(currentInstant());
    }

    /** 创建后才允许调度；拒绝执行时必须显式失败。 */
    public String create(
            String tenant,
            Long user,
            String kind,
            String source,
            Long session,
            String payload,
            long timeoutSeconds) {
        if (tenant == null
                || !tenant.matches("[A-Za-z0-9_-]{1,20}")
                || user == null
                || !Set.of("CHAT", "FLOW", "AB").contains(kind)
                || timeoutSeconds < 1) throw new IllegalArgumentException("执行身份不合法");
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO"
                        + " ai_execution(execution_id,tenant_id,user_id,kind,source_id,session_id,status,payload,deadline_at,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,'QUEUED',?,?,?,?)",
                id,
                tenant,
                user,
                kind,
                source == null ? "" : source,
                session,
                payload,
                Timestamp.from(currentInstant().plusSeconds(timeoutSeconds)),
                now(),
                now());
        return id;
    }

    /** 行锁下认领；仅Flow允许由恢复器接管过期执行，恢复器必须先检查节点重放策略。 */
    public Lease claim(String tenant, String id, String owner) {
        return tx.execute(
                t -> {
                    var rows =
                            jdbc.queryForList(
                                    "SELECT * FROM ai_execution WHERE tenant_id=? AND"
                                            + " execution_id=? FOR UPDATE",
                                    tenant,
                                    id);
                    if (rows.isEmpty()) return null;
                    var r = rows.get(0);
                    String state = (String) r.get("status");
                    Instant instant = currentInstant();
                    if (((Number) r.get("cancel_requested")).intValue() != 0
                            || !((Timestamp) r.get("deadline_at")).toInstant().isAfter(instant))
                        return null;
                    boolean eligible =
                            "QUEUED".equals(state)
                                    || ("WAITING".equals(state)
                                            && r.get("wake_at") != null
                                            && !((Timestamp) r.get("wake_at"))
                                                    .toInstant()
                                                    .isAfter(instant))
                                    || ("FLOW".equals(r.get("kind"))
                                            && "RUNNING".equals(state)
                                            && r.get("lease_until") != null
                                            && !((Timestamp) r.get("lease_until"))
                                                    .toInstant()
                                                    .isAfter(instant));
                    if (!eligible) return null;
                    long generation = ((Number) r.get("generation")).longValue() + 1;
                    jdbc.update(
                            "UPDATE ai_execution SET"
                                    + " status='RUNNING',owner_id=?,generation=?,lease_until=?,updated_at=?"
                                    + " WHERE tenant_id=? AND execution_id=?",
                            owner,
                            generation,
                            Timestamp.from(instant.plusSeconds(30)),
                            now(),
                            tenant,
                            id);
                    return new Lease(
                            id,
                            tenant,
                            ((Number) r.get("user_id")).longValue(),
                            (String) r.get("kind"),
                            (String) r.get("source_id"),
                            r.get("session_id") == null
                                    ? null
                                    : ((Number) r.get("session_id")).longValue(),
                            owner,
                            generation);
                });
    }

    public Map<String, Object> get(String tenant, String id) {
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM ai_execution WHERE tenant_id=? AND execution_id=?",
                        tenant,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("执行不存在或无权访问");
        return rows.get(0);
    }

    public Map<String, Object> owned(String tenant, Long user, String id) {
        var r = get(tenant, id);
        if (user == null || !Objects.equals(((Number) r.get("user_id")).longValue(), user))
            throw new IllegalArgumentException("执行不存在或无权访问");
        return r;
    }

    public void check(Lease lease) {
        var r = get(lease.tenant(), lease.id());
        if (((Number) r.get("cancel_requested")).intValue() != 0)
            throw new CancellationException("执行已请求取消");
        if (!lease.owner().equals(r.get("owner_id"))
                || lease.generation() != ((Number) r.get("generation")).longValue()
                || !"RUNNING".equals(r.get("status"))
                || r.get("lease_until") == null
                || !((Timestamp) r.get("lease_until")).toInstant().isAfter(currentInstant()))
            throw new IllegalStateException("执行所有权已失效");
        if (!((Timestamp) r.get("deadline_at")).toInstant().isAfter(currentInstant()))
            throw new CancellationException("执行已超过截止时间");
    }

    public boolean heartbeat(Lease l) {
        return jdbc.update(
                        "UPDATE ai_execution SET lease_until=?,updated_at=? WHERE "
                                + FENCE
                                + " AND status IN ('RUNNING','CANCEL_REQUESTED')",
                        Timestamp.from(currentInstant().plusSeconds(30)),
                        now(),
                        l.tenant(),
                        l.id(),
                        l.owner(),
                        l.generation(),
                        now())
                == 1;
    }

    /** 请求取消不宣称工作已经退出；尚未认领或持久化等待可立即确认。 */
    public Map<String, Object> cancel(String tenant, Long user, String id) {
        return tx.execute(
                t -> {
                    var r = owned(tenant, user, id);
                    jdbc.update(
                            "UPDATE ai_execution SET cancel_requested=1,status=CASE WHEN status IN"
                                    + " ('QUEUED','WAITING') THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED'"
                                    + " END,updated_at=? WHERE tenant_id=? AND user_id=? AND"
                                    + " execution_id=? AND status IN"
                                    + " ('QUEUED','WAITING','RUNNING','CANCEL_REQUESTED')",
                            now(),
                            tenant,
                            user,
                            id);
                    return owned(tenant, user, id);
                });
    }

    public boolean finish(Lease l, String status, String error) {
        if (!Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "RECOVERY_REQUIRED")
                .contains(status)) throw new IllegalArgumentException("执行终态不合法");
        String deadline =
                Set.of("SUCCEEDED", "PARTIAL_SUCCESS").contains(status) ? " AND deadline_at>?" : "";
        String cancellation =
                "CANCELLED".equals(status) || "RECOVERY_REQUIRED".equals(status)
                        ? ""
                        : " AND cancel_requested=0";
        var parameters =
                new ArrayList<Object>(
                        Arrays.asList(
                                status,
                                error,
                                now(),
                                l.tenant(),
                                l.id(),
                                l.owner(),
                                l.generation(),
                                now()));
        if (!deadline.isEmpty()) parameters.add(now());
        return jdbc.update(
                        "UPDATE ai_execution SET"
                                + " status=?,error_code=?,lease_until=NULL,updated_at=? WHERE "
                                + FENCE
                                + " AND status IN ('RUNNING','CANCEL_REQUESTED')"
                                + cancellation
                                + deadline,
                        parameters.toArray())
                == 1;
    }

    public boolean park(Lease l, String checkpoint, Instant wake) {
        return jdbc.update(
                        "UPDATE ai_execution SET"
                                + " status='WAITING',checkpoint=?,wake_at=?,owner_id=NULL,lease_until=NULL,updated_at=?"
                                + " WHERE "
                                + FENCE
                                + " AND status='RUNNING' AND cancel_requested=0",
                        checkpoint,
                        Timestamp.from(wake),
                        now(),
                        l.tenant(),
                        l.id(),
                        l.owner(),
                        l.generation(),
                        now())
                == 1;
    }

    public boolean saveCheckpoint(Lease l, String checkpoint) {
        return jdbc.update(
                        "UPDATE ai_execution SET checkpoint=?,updated_at=? WHERE "
                                + FENCE
                                + " AND status='RUNNING' AND cancel_requested=0",
                        checkpoint,
                        now(),
                        l.tenant(),
                        l.id(),
                        l.owner(),
                        l.generation(),
                        now())
                == 1;
    }

    public String checkpoint(Lease l) {
        Object value = get(l.tenant(), l.id()).get("checkpoint");
        return value == null ? null : value.toString();
    }

    public void partial(Lease l, String content, Long message) {
        jdbc.update(
                "UPDATE ai_execution SET"
                        + " partial_content=?,message_id=COALESCE(?,message_id),updated_at=? WHERE "
                        + FENCE
                        + " AND status IN ('RUNNING','CANCEL_REQUESTED')",
                content,
                message,
                now(),
                l.tenant(),
                l.id(),
                l.owner(),
                l.generation(),
                now());
    }

    public boolean rejected(String tenant, String id) {
        return jdbc.update(
                        "UPDATE ai_execution SET"
                                + " status='FAILED',error_code='EXECUTOR_BUSY',updated_at=? WHERE"
                                + " tenant_id=? AND execution_id=? AND status='QUEUED'",
                        now(),
                        tenant,
                        id)
                == 1;
    }

    public List<Map<String, Object>> due(String kind) {
        return jdbc.queryForList(
                "SELECT * FROM ai_execution WHERE kind=? AND cancel_requested=0 AND deadline_at>?"
                        + " AND (status='QUEUED' OR (status='WAITING' AND wake_at<=?) OR"
                        + " (status='RUNNING' AND lease_until<=?)) ORDER BY created_at LIMIT 100",
                kind,
                now(),
                now(),
                now());
    }

    /** 已失去工作者的取消，和超过截止时间的等待，也必须有确定维护路径。 */
    public List<Map<String, Object>> expired(String kind) {
        return jdbc.queryForList(
                "SELECT * FROM ai_execution WHERE kind=? AND status IN"
                        + " ('QUEUED','WAITING','RUNNING','CANCEL_REQUESTED') AND (deadline_at<=? OR"
                        + " (status='CANCEL_REQUESTED' AND lease_until<=?)) ORDER BY updated_at LIMIT"
                        + " 100",
                kind,
                now(),
                now());
    }

    /** 无法确认外部工作结果时保留核对状态，不自动重发。 */
    public int recoverAbandoned(String kind) {
        return jdbc.update(
                "UPDATE ai_execution SET status=CASE WHEN status='QUEUED' THEN 'FAILED' ELSE"
                        + " 'RECOVERY_REQUIRED' END,error_code='OWNER_LOST_OR_DEADLINE',updated_at=?"
                        + " WHERE kind=? AND status IN ('QUEUED','RUNNING','CANCEL_REQUESTED') AND"
                        + " (deadline_at<=? OR (lease_until IS NOT NULL AND lease_until<=?))",
                now(),
                kind,
                now(),
                now());
    }

    public List<Map<String, Object>> bySource(String tenant, String kind, String source) {
        return jdbc.queryForList(
                "SELECT * FROM ai_execution WHERE tenant_id=? AND kind=? AND source_id=? ORDER BY"
                        + " created_at DESC",
                tenant,
                kind,
                source);
    }

    /** 人工续跑只能重新排队已停止的失败批次，取消不会复活。 */
    public void requeueOwned(String tenant, Long user, String id) {
        owned(tenant, user, id);
        if (jdbc.update(
                        "UPDATE ai_execution SET"
                                + " status='QUEUED',owner_id=NULL,lease_until=NULL,deadline_at=?,error_code=NULL,updated_at=?"
                                + " WHERE tenant_id=? AND user_id=? AND execution_id=? AND"
                                + " cancel_requested=0 AND status IN"
                                + " ('FAILED','PARTIAL_SUCCESS','RECOVERY_REQUIRED')",
                        Timestamp.from(currentInstant().plusSeconds(86400)),
                        now(),
                        tenant,
                        user,
                        id)
                != 1) throw new IllegalStateException("此执行状态不能续跑");
    }

    public void enrichHistory(String tenant, Long user, Long session, List<AiMessage> messages) {
        var statusByMessage = new HashMap<Long, Map<String, Object>>();
        for (var row :
                jdbc.queryForList(
                        "SELECT execution_id,message_id,status FROM ai_execution WHERE tenant_id=?"
                                + " AND user_id=? AND session_id=? AND kind='CHAT' AND message_id IS"
                                + " NOT NULL ORDER BY created_at",
                        tenant,
                        user,
                        session))
            statusByMessage.put(((Number) row.get("message_id")).longValue(), row);
        for (var message : messages) {
            var row = statusByMessage.get(message.getId());
            if (row != null) {
                message.setExecutionRequestId(row.get("execution_id").toString());
                message.setCompletionStatus(row.get("status").toString());
            }
        }
    }

    private final ThreadLocal<Lease> completing = new ThreadLocal<>();

    /** 执行行锁、消息与终态共用同一物理master事务，围栏校验在任何消息回调之前。 */
    public String completeAtomically(
            Lease lease, String desired, Runnable successful, Runnable cancelled) {
        if (!Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(desired))
            throw new IllegalArgumentException("完成状态不合法");
        return completionTx.execute(
                status -> {
                    var row =
                            jdbc.queryForMap(
                                    "SELECT * FROM ai_execution WHERE tenant_id=? AND"
                                            + " execution_id=? FOR UPDATE",
                                    lease.tenant(),
                                    lease.id());
                    if (!lease.owner().equals(row.get("owner_id"))
                            || lease.generation() != ((Number) row.get("generation")).longValue()
                            || row.get("lease_until") == null
                            || !((Timestamp) row.get("lease_until"))
                                    .toInstant()
                                    .isAfter(currentInstant())
                            || !Set.of("RUNNING", "CANCEL_REQUESTED").contains(row.get("status")))
                        throw new IllegalStateException("执行所有权失效，拒绝消息写入");
                    String terminal =
                            ((Number) row.get("cancel_requested")).intValue() != 0
                                    ? "CANCELLED"
                                    : !((Timestamp) row.get("deadline_at"))
                                                    .toInstant()
                                                    .isAfter(currentInstant())
                                            ? "FAILED"
                                            : desired;
                    Lease previous = completing.get();
                    completing.set(lease);
                    try {
                        if ("CANCELLED".equals(terminal) || "FAILED".equals(terminal))
                            cancelled.run();
                        else if ("SUCCEEDED".equals(terminal)) successful.run();
                    } finally {
                        if (previous == null) completing.remove();
                        else completing.set(previous);
                    }
                    if (jdbc.update(
                                    "UPDATE ai_execution SET"
                                            + " status=?,lease_until=NULL,error_code=?,updated_at=?"
                                            + " WHERE tenant_id=? AND execution_id=? AND owner_id=? AND"
                                            + " generation=? AND status IN"
                                            + " ('RUNNING','CANCEL_REQUESTED')",
                                    terminal,
                                    "FAILED".equals(terminal) ? "EXECUTION_ERROR" : null,
                                    now(),
                                    lease.tenant(),
                                    lease.id(),
                                    lease.owner(),
                                    lease.generation())
                            != 1) throw new IllegalStateException("执行终态竞争失败");
                    return terminal;
                });
    }

    /** 仅允许在上面的围栏事务中插入助手消息；不经动态数据源另开自动提交连接。 */
    public void insertAssistant(Lease lease, AiMessage message) {
        if (!lease.equals(completing.get())
                || !"ASSISTANT".equals(message.getRole())
                || !Objects.equals(lease.sessionId(), message.getSessionId()))
            throw new IllegalStateException("消息必须由当前请求终态事务持久化");
        Long id = IdWorker.getId();
        message.setId(id);
        message.setTenantId(lease.tenant());
        message.setCreateBy(lease.userId());
        jdbc.update(
                "INSERT INTO"
                        + " ai_message(id,tenant_id,session_id,role,content,thinking_content,model_id,input_tokens,output_tokens,token_usage,create_by,create_time,update_by,update_time)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                lease.tenant(),
                lease.sessionId(),
                message.getRole(),
                message.getContent(),
                message.getThinkingContent(),
                message.getModelId(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getTokenUsage(),
                lease.userId(),
                now(),
                lease.userId(),
                now());
        partial(lease, message.getContent(), id);
    }
}
