package com.nexus.ai.service.governance;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.model.AiModelCapabilitySelection;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.governance.*;
import com.nexus.common.ai.service.IAiModelService;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** A/B定义与执行分离：冻结批次、原子认领、逐题归属及显式续跑。 */
@Service
@DS("master")
public class AiAbRunService {
    @Autowired
    private AiExecutionAuthorization authorization;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AiExecutionRepository executions;
    private final IAiModelService models;
    private final ChatModelFactory factory;
    private final Executor executor;
    private final AiGovernanceProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    private final String owner = UUID.randomUUID().toString();
    private final ConcurrentMap<String, AiExecutionRepository.Lease> active =
            new ConcurrentHashMap<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();

    public AiAbRunService(
            DataSource source,
            AiExecutionRepository executions,
            IAiModelService models,
            ChatModelFactory factory,
            @Qualifier("aiAbExecutor") Executor executor,
            AiGovernanceProperties properties) {
        source = AiMasterDataSource.resolve(source);
        jdbc = AiMasterDataSource.jdbc(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executions = executions;
        this.models = models;
        this.factory = factory;
        this.executor = executor;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.abActive();
    }

    private void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("A/B批次执行未启用，请先完成第二阶段迁移");
    }

    public String start(String tenant, Long user, Long definition, String startKey) {
        requireEnabled();
        if (user == null || startKey == null || !startKey.matches("[A-Za-z0-9_-]{16,80}"))
            throw new IllegalArgumentException("批次启动标识不合法");
        String run =
                tx.execute(
                        t -> {
                            var previous =
                                    jdbc.queryForList(
                                            "SELECT * FROM ai_ab_run WHERE tenant_id=? AND"
                                                    + " user_id=? AND start_key=?",
                                            tenant,
                                            user,
                                            startKey);
                            if (!previous.isEmpty()) {
                                var old = previous.get(0);
                                if (((Number) old.get("definition_id")).longValue() != definition)
                                    throw new IllegalArgumentException("批次幂等标识冲突");
                                return old.get("run_id").toString();
                            }
                            var definitions =
                                    jdbc.queryForList(
                                            "SELECT * FROM ai_ab_test WHERE tenant_id=? AND id=?"
                                                    + " FOR UPDATE",
                                            tenant,
                                            definition);
                            if (definitions.isEmpty())
                                throw new IllegalArgumentException("测试定义不存在或无权访问");
                            var duplicate =
                                    jdbc.queryForList(
                                            "SELECT * FROM ai_ab_run WHERE tenant_id=? AND"
                                                    + " user_id=? AND start_key=? FOR UPDATE",
                                            tenant,
                                            user,
                                            startKey);
                            if (!duplicate.isEmpty())
                                return duplicate.get(0).get("run_id").toString();
                            var d = definitions.get(0);
                            if ("RUNNING".equals(d.get("status")))
                                throw new IllegalStateException("该测试已有运行批次");
                            List<String> questions;
                            try {
                                questions =
                                        json.readValue(
                                                d.get("test_questions").toString(),
                                                new TypeReference<List<String>>() {});
                            } catch (Exception e) {
                                throw new IllegalArgumentException("测试问题必须为JSON字符串数组");
                            }
                            if (questions.isEmpty()
                                    || questions.size() > 200
                                    || questions.stream()
                                            .anyMatch(
                                                    q ->
                                                            q == null
                                                                    || q.isBlank()
                                                                    || q.length() > 2000))
                                throw new IllegalArgumentException("每批1到200题，每题1到2000字");
                            var a = freeze(((Number) d.get("model_a_id")).longValue());
                            var b = freeze(((Number) d.get("model_b_id")).longValue());
                            var snapshot =
                                    new AiAbRunSnapshot(
                                            List.copyOf(questions), a, b, hash(encode(questions)));
                            String id = UUID.randomUUID().toString();
                            String execution =
                                    executions.create(tenant, user, "AB", id, null, "{}", 86400);
                            jdbc.update(
                                    "INSERT INTO"
                                            + " ai_ab_run(run_id,tenant_id,user_id,definition_id,execution_id,start_key,test_name,snapshot,created_at)"
                                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                                    id,
                                    tenant,
                                    user,
                                    definition,
                                    execution,
                                    startKey,
                                    d.get("test_name"),
                                    encode(snapshot),
                                    Timestamp.from(Instant.now()));
                            for (int i = 0; i < questions.size(); i++)
                                for (String slot : List.of("A", "B")) {
                                    var model = "A".equals(slot) ? a : b;
                                    jdbc.update(
                                            "INSERT INTO"
                                                    + " ai_ab_run_item(item_id,run_id,tenant_id,question_index,model_slot,model_id,model_name,question,status,updated_at)"
                                                    + " VALUES (?,?,?,?,?,?,?,?,'QUEUED',?)",
                                            UUID.randomUUID().toString(),
                                            id,
                                            tenant,
                                            i,
                                            slot,
                                            model.pricing().modelId(),
                                            model.pricing().modelName(),
                                            questions.get(i),
                                            Timestamp.from(Instant.now()));
                                }
                            jdbc.update(
                                    "UPDATE ai_ab_test SET status='RUNNING' WHERE tenant_id=? AND"
                                            + " id=?",
                                    tenant,
                                    definition);
                            return id;
                        });
        dispatch(tenant, run);
        return run;
    }

    private AiAbRunSnapshot.Model freeze(Long id) {
        var model = models.selectModelById(id);
        if (!AiModelCapabilitySelection.supportsChat(model) || "1".equals(model.getStatus()))
            throw new IllegalArgumentException("A/B只能使用具备文本输入输出能力的启用模型");
        return new AiAbRunSnapshot.Model(
                new AiPriceSnapshot(
                        id,
                        model.getModelName(),
                        model.getApiProtocol(),
                        model.getCurrency(),
                        model.getInputPricePerMillion(),
                        model.getOutputPricePerMillion(),
                        model.getContextWindow(),
                        model.getMaxTokens()),
                fingerprint(model));
    }

    private String fingerprint(AiModel model) {
        var values = new TreeMap<String, Object>();
        values.put("id", model.getId());
        values.put("provider", model.getProviderId());
        values.put("endpointHash", hash(Objects.toString(model.getApiHost(), "")));
        values.put("name", model.getModelName());
        values.put("type", model.getModelType());
        values.put("protocol", model.getApiProtocol());
        values.put("temperature", model.getTemperature());
        values.put("output", model.getMaxTokens());
        values.put("context", model.getContextWindow());
        values.put("inputPrice", model.getInputPricePerMillion());
        values.put("outputPrice", model.getOutputPricePerMillion());
        values.put("currency", model.getCurrency());
        values.put("inputCapabilities", model.getInputCapabilities());
        values.put("outputCapabilities", model.getOutputCapabilities());
        return hash(encode(values));
    }

    private void dispatch(String tenant, String run) {
        if (!queued.add(run)) return;
        try {
            executor.execute(
                    () -> {
                        try {
                            execute(tenant, run);
                        } finally {
                            queued.remove(run);
                        }
                    });
        } catch (RejectedExecutionException busy) {
            queued.remove(run);
            var r = run(tenant, run);
            tx.executeWithoutResult(
                    t -> {
                        lockExecution(tenant, r.get("execution_id").toString());
                        if (executions.rejected(tenant, r.get("execution_id").toString()))
                            jdbc.update(
                                    "UPDATE ai_ab_test SET status='FAILED' WHERE tenant_id=? AND"
                                            + " id=?",
                                    tenant,
                                    r.get("definition_id"));
                    });
        }
    }

    private void execute(String tenant, String runId) {
        var run = run(tenant, runId);
        var lease = executions.claim(tenant, run.get("execution_id").toString(), owner);
        if (lease == null) return;
        active.put(runId, lease);
        try {
            AiAbRunSnapshot snapshot;
            try {
                snapshot = json.readValue(run.get("snapshot").toString(), AiAbRunSnapshot.class);
            } catch (Exception e) {
                throw new IllegalStateException("冻结批次无法读取");
            }
            for (var item :
                    jdbc.queryForList(
                            "SELECT * FROM ai_ab_run_item WHERE tenant_id=? AND run_id=? AND"
                                    + " status='QUEUED' ORDER BY question_index,model_slot",
                            tenant,
                            runId)) {
                executions.check(lease);
                if (authorization != null)
                    authorization.require(tenant, lease.userId(), "ai:abtest:edit");
                String itemId = item.get("item_id").toString();
                if (jdbc.update(
                                "UPDATE ai_ab_run_item SET"
                                        + " status='RUNNING',attempt=attempt+1,generation=?,updated_at=?"
                                        + " WHERE tenant_id=? AND item_id=? AND status='QUEUED'",
                                lease.generation(),
                                Timestamp.from(Instant.now()),
                                tenant,
                                itemId)
                        != 1) continue;
                var frozen =
                        "A".equals(item.get("model_slot")) ? snapshot.modelA() : snapshot.modelB();
                long start = System.nanoTime();
                var context =
                        new AiCallContext(
                                tenant,
                                lease.userId(),
                                null,
                                itemId,
                                null,
                                null,
                                null,
                                runId,
                                "AB");
                try (var scope = AiInvocationScope.open(context, () -> executions.check(lease))) {
                    var current = models.selectModelById(frozen.pricing().modelId());
                    if (current == null
                            || "1".equals(current.getStatus())
                            || !AiModelCapabilitySelection.supportsChat(current)
                            || !frozen.configurationFingerprint().equals(fingerprint(current)))
                        throw new IllegalStateException("模型配置已变更，请建立新批次");
                    var request = new AiChatRequest();
                    request.setMaxOutputTokens(frozen.pricing().outputLimit());
                    var response =
                            factory.getChatModel(frozen.pricing().modelId(), request)
                                    .chat(
                                            ChatRequest.builder()
                                                    .messages(
                                                            UserMessage.from(
                                                                    item.get("question")
                                                                            .toString()))
                                                    .build());
                    executions.check(lease);
                    if (response == null
                            || response.aiMessage() == null
                            || response.aiMessage().text() == null)
                        throw new IllegalStateException("模型没有返回有效文本");
                    saveItem(
                            lease,
                            itemId,
                            "SUCCEEDED",
                            response.aiMessage().text(),
                            null,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                } catch (CancellationException cancelled) {
                    saveItem(
                            lease,
                            itemId,
                            "CANCELLED",
                            null,
                            "CANCELLED",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                    break;
                } catch (RuntimeException error) {
                    boolean uncertain =
                            jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM ai_model_call c WHERE"
                                                    + " c.tenant_id=? AND c.request_id=? AND"
                                                    + " (c.status='RUNNING' OR (c.cost IS NULL AND NOT"
                                                    + " EXISTS (SELECT 1 FROM ai_model_call_adjustment"
                                                    + " a WHERE a.tenant_id=c.tenant_id AND"
                                                    + " a.call_id=c.call_id)))",
                                            Integer.class,
                                            tenant,
                                            itemId)
                                    > 0;
                    saveItem(
                            lease,
                            itemId,
                            uncertain ? "RECOVERY_REQUIRED" : "FAILED",
                            null,
                            uncertain ? "CALL_UNCONFIRMED" : "MODEL_ERROR",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }
            }
            finishRun(lease, run);
        } catch (CancellationException cancelled) {
            finishRun(lease, run);
        } catch (RuntimeException error) {
            tx.executeWithoutResult(
                    t -> {
                        lockExecution(tenant, lease.id());
                        if (executions.finish(lease, "RECOVERY_REQUIRED", "EXECUTION_ERROR"))
                            jdbc.update(
                                    "UPDATE ai_ab_test SET status='RECOVERY_REQUIRED' WHERE"
                                            + " tenant_id=? AND id=?",
                                    tenant,
                                    run.get("definition_id"));
                    });
        } finally {
            active.remove(runId);
        }
    }

    private void saveItem(
            AiExecutionRepository.Lease lease,
            String item,
            String status,
            String response,
            String error,
            long latency) {
        jdbc.update(
                "UPDATE ai_ab_run_item SET"
                        + " status=?,response=?,error_code=?,latency_ms=?,updated_at=? WHERE"
                        + " tenant_id=? AND item_id=? AND generation=? AND status='RUNNING' AND EXISTS"
                        + " (SELECT 1 FROM ai_execution e WHERE e.execution_id=? AND e.tenant_id=? AND"
                        + " e.owner_id=? AND e.generation=? AND e.lease_until>?)",
                status,
                response,
                error,
                latency,
                Timestamp.from(Instant.now()),
                lease.tenant(),
                item,
                lease.generation(),
                lease.id(),
                lease.tenant(),
                lease.owner(),
                lease.generation(),
                Timestamp.from(executions.currentInstant()));
    }

    private void finishRun(AiExecutionRepository.Lease lease, Map<String, Object> run) {
        tx.executeWithoutResult(
                t -> {
                    lockExecution(lease.tenant(), lease.id());
                    var state = executions.get(lease.tenant(), lease.id());
                    boolean cancelled = ((Number) state.get("cancel_requested")).intValue() != 0;
                    if (cancelled)
                        jdbc.update(
                                "UPDATE ai_ab_run_item SET status='CANCELLED',updated_at=? WHERE"
                                        + " tenant_id=? AND run_id=? AND status='QUEUED'",
                                Timestamp.from(Instant.now()),
                                lease.tenant(),
                                run.get("run_id"));
                    var counts =
                            jdbc.queryForList(
                                    "SELECT status,COUNT(*) amount FROM ai_ab_run_item WHERE"
                                            + " tenant_id=? AND run_id=? GROUP BY status",
                                    lease.tenant(),
                                    run.get("run_id"));
                    long success = 0, failed = 0, unknown = 0, total = 0;
                    for (var c : counts) {
                        long n = ((Number) c.get("amount")).longValue();
                        total += n;
                        if ("SUCCEEDED".equals(c.get("status"))) success += n;
                        else if ("FAILED".equals(c.get("status"))) failed += n;
                        else if (!"CANCELLED".equals(c.get("status"))) unknown += n;
                    }
                    String result =
                            unknown > 0
                                    ? "RECOVERY_REQUIRED"
                                    : cancelled
                                            ? "CANCELLED"
                                            : !((Timestamp) state.get("deadline_at"))
                                                            .toInstant()
                                                            .isAfter(executions.currentInstant())
                                                    ? "FAILED"
                                                    : unknown > 0
                                                            ? "RECOVERY_REQUIRED"
                                                            : success == total
                                                                    ? "SUCCEEDED"
                                                                    : success > 0
                                                                            ? "PARTIAL_SUCCESS"
                                                                            : "FAILED";
                    if (executions.finish(lease, result, null))
                        jdbc.update(
                                "UPDATE ai_ab_test SET status=? WHERE tenant_id=? AND id=?",
                                result,
                                lease.tenant(),
                                run.get("definition_id"));
                });
    }

    private void lockExecution(String tenant, String id) {
        jdbc.queryForMap(
                "SELECT * FROM ai_execution WHERE tenant_id=? AND execution_id=? FOR UPDATE",
                tenant,
                id);
    }

    public boolean hasUnconfirmed(String tenant, Long definition) {
        if (!enabled()) return false;
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_ab_run r JOIN ai_execution e ON"
                                + " e.execution_id=r.execution_id AND e.tenant_id=r.tenant_id WHERE"
                                + " r.tenant_id=? AND r.definition_id=? AND e.status IN"
                                + " ('QUEUED','RUNNING','CANCEL_REQUESTED','WAITING','RECOVERY_REQUIRED')",
                        Integer.class,
                        tenant,
                        definition)
                > 0;
    }

    private Map<String, Object> run(String tenant, String id) {
        var list =
                jdbc.queryForList(
                        "SELECT * FROM ai_ab_run WHERE tenant_id=? AND run_id=?", tenant, id);
        if (list.isEmpty()) throw new IllegalArgumentException("批次不存在或无权访问");
        return list.get(0);
    }

    private Map<String, Object> owned(String tenant, Long user, String id) {
        var r = run(tenant, id);
        if (user == null || ((Number) r.get("user_id")).longValue() != user)
            throw new IllegalArgumentException("批次不存在或无权访问");
        return r;
    }

    public Map<String, Object> info(String tenant, Long user, String id) {
        var r = owned(tenant, user, id);
        var out = new LinkedCaseInsensitiveMap<Object>();
        out.putAll(r);
        out.put(
                "status",
                executions.owned(tenant, user, r.get("execution_id").toString()).get("status"));
        return out;
    }

    public List<Map<String, Object>> list(String tenant, Long user, Long definition) {
        requireEnabled();
        return jdbc.queryForList(
                "SELECT r.*,e.status FROM ai_ab_run r JOIN ai_execution e ON"
                        + " e.execution_id=r.execution_id AND e.tenant_id=r.tenant_id WHERE"
                        + " r.tenant_id=? AND r.user_id=? AND (? IS NULL OR r.definition_id=?) ORDER BY"
                        + " r.created_at DESC LIMIT 100",
                tenant,
                user,
                definition,
                definition);
    }

    public List<Map<String, Object>> items(String tenant, Long user, String id) {
        owned(tenant, user, id);
        return jdbc.queryForList(
                "SELECT * FROM ai_ab_run_item WHERE tenant_id=? AND run_id=? ORDER BY"
                        + " question_index,model_slot",
                tenant,
                id);
    }

    public Map<String, Object> cancel(String tenant, Long user, String id) {
        requireEnabled();
        var r = owned(tenant, user, id);
        executions.cancel(tenant, user, r.get("execution_id").toString());
        return info(tenant, user, id);
    }

    public void score(String tenant, Long user, String run, String item, Integer score) {
        owned(tenant, user, run);
        if (score == null || score < 1 || score > 5)
            throw new IllegalArgumentException("评分必须在1到5之间");
        if (jdbc.update(
                        "UPDATE ai_ab_run_item SET score=? WHERE tenant_id=? AND run_id=? AND"
                                + " item_id=? AND status='SUCCEEDED'",
                        score,
                        tenant,
                        run,
                        item)
                != 1) throw new IllegalArgumentException("只能评分本批次成功结果");
    }

    public void resume(String tenant, Long user, String id) {
        requireEnabled();
        var r = owned(tenant, user, id);
        tx.executeWithoutResult(
                t -> {
                    if (jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM ai_model_call c WHERE tenant_id=? AND"
                                            + " ab_run_id=? AND (status='RUNNING' OR (cost IS NULL AND"
                                            + " NOT EXISTS (SELECT 1 FROM ai_model_call_adjustment a"
                                            + " WHERE a.tenant_id=c.tenant_id AND"
                                            + " a.call_id=c.call_id)))",
                                    Integer.class,
                                    tenant,
                                    id)
                            > 0) throw new IllegalStateException("存在未确认调用，必须先核对费用和外部结果");
                    executions.requeueOwned(tenant, user, r.get("execution_id").toString());
                    jdbc.update(
                            "UPDATE ai_ab_run_item SET status='QUEUED',error_code=NULL,updated_at=?"
                                    + " WHERE tenant_id=? AND run_id=? AND status IN"
                                    + " ('FAILED','RECOVERY_REQUIRED')",
                            Timestamp.from(Instant.now()),
                            tenant,
                            id);
                    jdbc.update(
                            "UPDATE ai_ab_test SET status='RUNNING' WHERE tenant_id=? AND id=?",
                            tenant,
                            r.get("definition_id"));
                });
        dispatch(tenant, id);
    }

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        if (!enabled()) return;
        for (var lease : active.values())
            try {
                executions.heartbeat(lease);
            } catch (RuntimeException ignored) {
                /* 租约过期后禁止提交。 */
            }
    }

    @Scheduled(fixedDelay = 5000)
    public void recover() {
        if (!enabled()) return;
        executions.recoverAbandoned("AB");
        jdbc.update(
                "UPDATE ai_ab_run_item SET status='RECOVERY_REQUIRED',error_code='OWNER_LOST' WHERE"
                        + " status='RUNNING' AND EXISTS (SELECT 1 FROM ai_ab_run r JOIN ai_execution e"
                        + " ON e.execution_id=r.execution_id WHERE r.run_id=ai_ab_run_item.run_id AND"
                        + " r.tenant_id=ai_ab_run_item.tenant_id AND e.status='RECOVERY_REQUIRED')");
        for (var e : executions.due("AB"))
            if ("QUEUED".equals(e.get("status")))
                dispatch(e.get("tenant_id").toString(), e.get("source_id").toString());
    }

    public Map<String, Object> report(String tenant, Long user, String id) {
        var output = new LinkedHashMap<String, Object>();
        output.put("run", info(tenant, user, id));
        output.put(
                "models",
                jdbc.queryForList(
                        "SELECT model_slot,COUNT(*) total_questions,SUM(CASE WHEN"
                                + " status='SUCCEEDED' THEN 1 ELSE 0 END) succeeded,SUM(CASE WHEN"
                                + " status='FAILED' THEN 1 ELSE 0 END) failed,SUM(CASE WHEN"
                                + " status='RECOVERY_REQUIRED' THEN 1 ELSE 0 END) unconfirmed,AVG(CASE"
                                + " WHEN status='SUCCEEDED' THEN latency_ms ELSE NULL END)"
                                + " average_latency_ms,AVG(CASE WHEN status='SUCCEEDED' THEN score ELSE"
                                + " NULL END) average_score FROM ai_ab_run_item WHERE tenant_id=? AND"
                                + " run_id=? GROUP BY model_slot ORDER BY model_slot",
                        tenant,
                        id));
        output.put(
                "costs",
                jdbc.queryForList(
                        "SELECT i.model_slot,COALESCE(c.currency,(SELECT MIN(a.currency) FROM"
                                + " ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id AND"
                                + " a.call_id=c.call_id),'UNSPECIFIED')"
                                + " currency,SUM(COALESCE(c.input_tokens,0))"
                                + " input_tokens,SUM(COALESCE(c.output_tokens,0))"
                                + " output_tokens,SUM(COALESCE(c.cost,0)+(SELECT"
                                + " COALESCE(SUM(a.delta_amount),0) FROM ai_model_call_adjustment a"
                                + " WHERE a.tenant_id=c.tenant_id AND a.call_id=c.call_id))"
                                + " known_cost,SUM(CASE WHEN c.cost IS NULL AND NOT EXISTS (SELECT 1"
                                + " FROM ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id AND"
                                + " a.call_id=c.call_id) THEN 1 ELSE 0 END) unknown_calls,COUNT(*)"
                                + " call_count FROM ai_model_call c JOIN ai_ab_run_item i ON"
                                + " i.item_id=c.request_id AND i.tenant_id=c.tenant_id WHERE"
                                + " c.tenant_id=? AND c.ab_run_id=? GROUP BY i.model_slot,currency"
                                + " ORDER BY i.model_slot,currency",
                        tenant,
                        id));
        return output;
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("批次快照编码失败");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
