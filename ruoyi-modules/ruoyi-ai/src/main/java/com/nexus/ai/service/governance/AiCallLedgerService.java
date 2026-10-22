package com.nexus.ai.service.governance;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.governance.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

/** master库调用台账。租户行锁使预算配置、预占、终态和补账保持同一事务顺序。 */
@Service
@DS("master")
public class AiCallLedgerService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public AiCallLedgerService(DataSource source) {
        this(source, null);
    }

    public AiCallLedgerService(DataSource source, Clock clock) {
        source = AiMasterDataSource.resolve(source);
        jdbc = AiMasterDataSource.jdbc(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    private final ThreadLocal<Instant> transactionTime = new ThreadLocal<>();

    private Instant databaseTime() {
        return clock == null
                ? jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(3)", Timestamp.class).toInstant()
                : clock.instant();
    }

    private Instant instant() {
        Instant frozen = transactionTime.get();
        return frozen == null ? databaseTime() : frozen;
    }

    private Timestamp now() {
        return Timestamp.from(instant());
    }

    private LocalDate today() {
        return instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
    }

    private <T> T atomic(Function<TransactionStatus, T> action) {
        return tx.execute(
                status -> {
                    Instant previous = transactionTime.get();
                    transactionTime.set(databaseTime());
                    try {
                        return action.apply(status);
                    } finally {
                        if (previous == null) transactionTime.remove();
                        else transactionTime.set(previous);
                    }
                });
    }

    private void atomicVoid(Consumer<TransactionStatus> action) {
        atomic(
                status -> {
                    action.accept(status);
                    return null;
                });
    }

    private void lock(String tenant) {
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,20}"))
            throw new IllegalArgumentException("租户身份不合法");
        jdbc.update(
                "INSERT INTO ai_governance_lock(tenant_id) VALUES (?) ON DUPLICATE KEY UPDATE"
                        + " tenant_id=VALUES(tenant_id)",
                tenant);
        jdbc.queryForObject(
                "SELECT tenant_id FROM ai_governance_lock WHERE tenant_id=? FOR UPDATE",
                String.class,
                tenant);
        if (transactionTime.get() != null) transactionTime.set(databaseTime());
    }

    /** 调用开始必须先提交台账和额度，之后才能向厂商发送。bounded只由协议适配器给出。 */
    public String begin(AiCallContext context, AiPriceSnapshot price, boolean bounded) {
        return atomic(
                t -> {
                    lock(context.tenantId());
                    materialize(context.tenantId());
                    List<Map<String, Object>> budgets =
                            jdbc.queryForList(
                                    "SELECT * FROM ai_budget WHERE tenant_id=? AND enabled=1 AND"
                                            + " ((scope_type='TENANT_MONTH' AND period_start=?) OR"
                                            + " (scope_type='AB_RUN' AND scope_id=?)) ORDER BY"
                                            + " budget_id FOR UPDATE",
                                    context.tenantId(),
                                    today().withDayOfMonth(1),
                                    Objects.toString(context.abRunId(), ""));
                    if (!budgets.isEmpty() && !price.priced())
                        throw new IllegalStateException("预算已启用，调用缺少可信币种或价格");
                    var scopes =
                            budgets.stream()
                                    .map(b -> b.get("scope_type") + ":" + b.get("scope_id"))
                                    .collect(Collectors.toSet());
                    var matching =
                            budgets.stream()
                                    .filter(
                                            b ->
                                                    Objects.equals(
                                                            b.get("currency"), price.currency()))
                                    .toList();
                    var matchedScopes =
                            matching.stream()
                                    .map(b -> b.get("scope_type") + ":" + b.get("scope_id"))
                                    .collect(Collectors.toSet());
                    if (!matchedScopes.containsAll(scopes))
                        throw new IllegalStateException("该预算范围没有当前币种的额度");
                    budgets = matching;
                    BigDecimal quote = BigDecimal.ZERO;
                    if (!budgets.isEmpty()) {
                        if (!bounded
                                || price.contextWindow() == null
                                || price.contextWindow() <= 0
                                || price.outputLimit() == null
                                || price.outputLimit() <= 0)
                            throw new IllegalStateException("严格预算要求可信输入上界、可执行输出上限和受控重试");
                        quote =
                                price.inputPrice()
                                        .multiply(BigDecimal.valueOf(price.contextWindow()))
                                        .add(
                                                price.outputPrice()
                                                        .multiply(
                                                                BigDecimal.valueOf(
                                                                        price.outputLimit())))
                                        .divide(
                                                BigDecimal.valueOf(1000000),
                                                12,
                                                RoundingMode.CEILING);
                        for (var b : budgets) {
                            if (((Number) b.get("blocked")).intValue() != 0
                                    || decimal(b, "spent_amount")
                                                    .add(decimal(b, "held_amount"))
                                                    .add(quote)
                                                    .compareTo(decimal(b, "limit_amount"))
                                            > 0) throw new IllegalStateException("模型调用预算不足或待核对");
                        }
                    }
                    String id = UUID.randomUUID().toString();
                    jdbc.update(
                            "INSERT INTO"
                                    + " ai_model_call(call_id,tenant_id,user_id,user_name,request_id,session_id,flow_instance_id,decision_run_id,ab_run_id,source,model_id,model_name,protocol,currency,input_price,output_price,context_window,output_limit,status,usage_date,started_at)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RUNNING',?,?)",
                            id,
                            context.tenantId(),
                            context.userId(),
                            context.userName(),
                            context.requestId(),
                            context.sessionId(),
                            context.flowInstanceId(),
                            context.decisionRunId(),
                            context.abRunId(),
                            context.source(),
                            price.modelId(),
                            price.modelName(),
                            price.protocol(),
                            price.currency(),
                            price.inputPrice(),
                            price.outputPrice(),
                            price.contextWindow(),
                            price.outputLimit(),
                            today(),
                            now());
                    for (var b : budgets) {
                        jdbc.update(
                                "UPDATE ai_budget SET held_amount=held_amount+?,revision=revision+1"
                                        + " WHERE tenant_id=? AND budget_id=?",
                                quote,
                                context.tenantId(),
                                b.get("budget_id"));
                        jdbc.update(
                                "INSERT INTO"
                                        + " ai_budget_reservation(call_id,budget_id,tenant_id,amount,status)"
                                        + " VALUES (?,?,?,?,'HELD')",
                                id,
                                b.get("budget_id"),
                                context.tenantId(),
                                quote);
                    }
                    return id;
                });
    }

    /** 唯一终态和派生汇总在同一事务，回调重复不会增加统计。 */
    public void finish(
            String tenant, String id, String status, AiCallUsage usage, String errorCode) {
        if (!Set.of("SUCCEEDED", "FAILED", "CANCELLED", "RECOVERY_REQUIRED").contains(status))
            throw new IllegalArgumentException("调用终态不合法");
        Map<String, Object> late =
                atomic(
                        t -> {
                            lock(tenant);
                            var row = lockedCall(tenant, id);
                            if (!"RUNNING".equals(row.get("status")))
                                return row.get("cost") == null && cost(row, usage) != null
                                        ? row
                                        : null;
                            BigDecimal cost = cost(row, usage);
                            jdbc.update(
                                    "UPDATE ai_model_call SET"
                                            + " status=?,input_tokens=?,output_tokens=?,cached_tokens=?,reasoning_tokens=?,usage_source=?,cost=?,error_code=?,ended_at=?"
                                            + " WHERE tenant_id=? AND call_id=? AND status='RUNNING'",
                                    status,
                                    usage.inputTokens(),
                                    usage.outputTokens(),
                                    usage.cachedTokens(),
                                    usage.reasoningTokens(),
                                    usage.source(),
                                    cost,
                                    errorCode,
                                    now(),
                                    tenant,
                                    id);
                            daily(
                                    row,
                                    usage.inputTokens(),
                                    usage.outputTokens(),
                                    1,
                                    cost == null ? 1 : 0,
                                    cost == null ? BigDecimal.ZERO : cost);
                            if (cost != null) settle(tenant, id, cost);
                            return null;
                        });
        if (late != null)
            reconcileUsage(
                    tenant,
                    id,
                    cost(late, usage),
                    late.get("currency").toString(),
                    "厂商迟到用量确认",
                    ((Number) late.get("user_id")).longValue(),
                    "late-" + id,
                    usage);
    }

    /** 超过可信执行边界的调用保留预占，进入核对；不会猜测供应商是否扣费。 */
    public int recoverUnconfirmed() {
        Instant now = databaseTime();
        int changed = 0;
        for (var row :
                jdbc.queryForList(
                        "SELECT c.* FROM ai_model_call c WHERE c.status='RUNNING' AND"
                                + " (c.started_at<=? OR (c.started_at<=? AND EXISTS (SELECT 1 FROM"
                                + " ai_execution e WHERE e.tenant_id=c.tenant_id AND"
                                + " (e.execution_id=c.request_id OR (e.kind='FLOW' AND"
                                + " c.flow_instance_id=CASE WHEN e.kind='FLOW' THEN CAST(e.source_id AS"
                                + " DECIMAL(20,0)) ELSE NULL END) OR (e.kind='AB' AND EXISTS (SELECT 1"
                                + " FROM ai_ab_run r WHERE r.tenant_id=c.tenant_id AND"
                                + " r.run_id=c.ab_run_id AND r.execution_id=e.execution_id))) AND"
                                + " (e.status IN"
                                + " ('SUCCEEDED','FAILED','CANCELLED','RECOVERY_REQUIRED','PARTIAL_SUCCESS')"
                                + " OR e.lease_until<=?)))) ORDER BY c.started_at LIMIT 100",
                        Timestamp.from(now.minusSeconds(86400)),
                        Timestamp.from(now.minusSeconds(60)),
                        Timestamp.from(now))) {
            finish(
                    row.get("tenant_id").toString(),
                    row.get("call_id").toString(),
                    "RECOVERY_REQUIRED",
                    AiCallUsage.unknown(),
                    "OWNER_LOST_OR_CALLBACK_MISSING");
            changed++;
        }
        return changed;
    }

    private BigDecimal cost(Map<String, Object> row, AiCallUsage usage) {
        if (!usage.known()
                || row.get("input_price") == null
                || row.get("output_price") == null
                || row.get("currency") == null
                || !row.get("currency").toString().matches("[A-Z]{3}")
                || decimal(row, "input_price").signum() < 0
                || decimal(row, "output_price").signum() < 0) return null;
        return decimal(row, "input_price")
                .multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(
                        decimal(row, "output_price")
                                .multiply(BigDecimal.valueOf(usage.outputTokens())))
                .divide(BigDecimal.valueOf(1000000), 12, RoundingMode.HALF_UP);
    }

    private void daily(
            Map<String, Object> row,
            Long input,
            Long output,
            long count,
            long unknown,
            BigDecimal amount) {
        jdbc.update(
                "INSERT INTO"
                        + " ai_model_call_daily(tenant_id,user_id,model_id,currency,usage_date,input_tokens,output_tokens,request_count,unknown_calls,known_cost)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                        + " input_tokens=input_tokens+VALUES(input_tokens),output_tokens=output_tokens+VALUES(output_tokens),request_count=request_count+VALUES(request_count),unknown_calls=unknown_calls+VALUES(unknown_calls),known_cost=known_cost+VALUES(known_cost)",
                row.get("tenant_id"),
                row.get("user_id"),
                row.get("model_id"),
                Objects.toString(row.get("currency"), "UNSPECIFIED"),
                row.get("usage_date"),
                input == null ? 0 : input,
                output == null ? 0 : output,
                count,
                unknown,
                amount);
    }

    private void settle(String tenant, String id, BigDecimal amount) {
        for (var r :
                jdbc.queryForList(
                        "SELECT * FROM ai_budget_reservation WHERE tenant_id=? AND call_id=? AND"
                                + " status='HELD' FOR UPDATE",
                        tenant,
                        id)) {
            BigDecimal quote = decimal(r, "amount");
            jdbc.update(
                    "UPDATE ai_budget SET"
                            + " held_amount=held_amount-?,spent_amount=spent_amount+?,blocked=CASE WHEN"
                            + " ? > ? THEN 1 ELSE blocked END,revision=revision+1 WHERE tenant_id=? AND"
                            + " budget_id=?",
                    quote,
                    amount,
                    amount,
                    quote,
                    tenant,
                    r.get("budget_id"));
            jdbc.update(
                    "UPDATE ai_budget_reservation SET status='SETTLED' WHERE tenant_id=? AND"
                            + " call_id=? AND budget_id=? AND status='HELD'",
                    tenant,
                    id,
                    r.get("budget_id"));
        }
    }

    /** 对账修正追加审计，不修改冻结的厂商响应或原始价格。idempotencyKey由管理端独立生成。 */
    public void reconcile(
            String tenant,
            String id,
            BigDecimal amount,
            String currency,
            String reason,
            Long operator,
            String idempotencyKey) {
        reconcileUsage(tenant, id, amount, currency, reason, operator, idempotencyKey, null);
    }

    private void reconcileUsage(
            String tenant,
            String id,
            BigDecimal amount,
            String currency,
            String reason,
            Long operator,
            String idempotencyKey,
            AiCallUsage lateUsage) {
        if (amount == null
                || amount.signum() < 0
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || reason == null
                || reason.isBlank()
                || reason.length() > 500
                || operator == null
                || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("对账参数不合法");
        final BigDecimal confirmed = money(amount);
        atomicVoid(
                t -> {
                    lock(tenant);
                    var row = lockedCall(tenant, id);
                    if ("RUNNING".equals(row.get("status")))
                        throw new IllegalStateException("执行中的调用不能人工结算");
                    var old =
                            jdbc.queryForList(
                                    "SELECT * FROM ai_model_call_adjustment WHERE tenant_id=? AND"
                                            + " adjustment_id=?",
                                    tenant,
                                    idempotencyKey);
                    if (!old.isEmpty()) {
                        if (!id.equals(old.get(0).get("call_id"))
                                || confirmed.compareTo(decimal(old.get(0), "confirmed_amount")) != 0
                                || !currency.equals(old.get(0).get("currency"))
                                || !reason.equals(old.get(0).get("reason")))
                            throw new IllegalArgumentException("对账幂等键冲突");
                        return;
                    }
                    if (row.get("currency") != null && !currency.equals(row.get("currency")))
                        throw new IllegalArgumentException("禁止跨币种修改账单");
                    var adjustments =
                            jdbc.queryForList(
                                    "SELECT delta_amount,currency FROM ai_model_call_adjustment"
                                            + " WHERE tenant_id=? AND call_id=?",
                                    tenant,
                                    id);
                    if (lateUsage != null && !adjustments.isEmpty()) return;
                    if (!adjustments.isEmpty()
                            && !currency.equals(adjustments.get(0).get("currency")))
                        throw new IllegalArgumentException("禁止跨币种修改账单");
                    BigDecimal previous = decimal(row, "cost");
                    for (var a : adjustments) previous = previous.add(decimal(a, "delta_amount"));
                    BigDecimal delta = confirmed.subtract(previous);
                    boolean firstUnknown = row.get("cost") == null && adjustments.isEmpty();
                    jdbc.update(
                            "INSERT INTO"
                                    + " ai_model_call_adjustment(adjustment_id,call_id,tenant_id,currency,delta_amount,confirmed_amount,reason,operator_id,created_at,input_tokens,output_tokens,cached_tokens,reasoning_tokens,usage_source)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            idempotencyKey,
                            id,
                            tenant,
                            currency,
                            delta,
                            confirmed,
                            reason,
                            operator,
                            now(),
                            lateUsage == null ? null : lateUsage.inputTokens(),
                            lateUsage == null ? null : lateUsage.outputTokens(),
                            lateUsage == null ? null : lateUsage.cachedTokens(),
                            lateUsage == null ? null : lateUsage.reasoningTokens(),
                            lateUsage == null ? null : lateUsage.source());
                    if (row.get("currency") == null) {
                        var corrected = new LinkedCaseInsensitiveMap<Object>();
                        corrected.putAll(row);
                        corrected.put("currency", currency);
                        if (firstUnknown) {
                            Long in =
                                    row.get("input_tokens") == null
                                            ? 0L
                                            : ((Number) row.get("input_tokens")).longValue();
                            Long out =
                                    row.get("output_tokens") == null
                                            ? 0L
                                            : ((Number) row.get("output_tokens")).longValue();
                            daily(row, -in, -out, -1, -1, BigDecimal.ZERO);
                            daily(corrected, in, out, 1, 0, delta);
                        } else daily(corrected, null, null, 0, 0, delta);
                    } else
                        daily(
                                row,
                                lateUsage == null ? null : lateUsage.inputTokens(),
                                lateUsage == null ? null : lateUsage.outputTokens(),
                                0,
                                firstUnknown ? -1 : 0,
                                delta);
                    boolean held =
                            jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM ai_budget_reservation WHERE"
                                                    + " tenant_id=? AND call_id=? AND status='HELD'",
                                            Integer.class,
                                            tenant,
                                            id)
                                    > 0;
                    if (held) settle(tenant, id, confirmed);
                    else
                        jdbc.update(
                                "UPDATE ai_budget SET"
                                        + " spent_amount=spent_amount+?,revision=revision+1 WHERE"
                                        + " tenant_id=? AND currency=? AND ((scope_type='TENANT_MONTH'"
                                        + " AND period_start=?) OR (scope_type='AB_RUN' AND"
                                        + " scope_id=?))",
                                delta,
                                tenant,
                                currency,
                                ((Date) row.get("usage_date")).toLocalDate().withDayOfMonth(1),
                                Objects.toString(row.get("ab_run_id"), ""));
                });
    }

    /** 月度策略与每月账本分离，跨月自动创建额度，旧月份的预占仍在原账本结算。 */
    private void materialize(String tenant) {
        for (var policy :
                jdbc.queryForList(
                        "SELECT * FROM ai_budget_policy WHERE tenant_id=? AND enabled=1 ORDER BY"
                                + " policy_id",
                        tenant)) materializePolicy(tenant, policy);
    }

    private void materializePolicy(String tenant, Map<String, Object> policy) {
        String scope = policy.get("scope_type").toString(),
                scopeId = policy.get("scope_id").toString(),
                currency = policy.get("currency").toString();
        LocalDate period =
                "AB_RUN".equals(scope) ? LocalDate.of(1970, 1, 1) : today().withDayOfMonth(1);
        String id =
                UUID.nameUUIDFromBytes(
                                (policy.get("policy_id") + ":" + period)
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_budget WHERE tenant_id=? AND budget_id=?",
                        Integer.class,
                        tenant,
                        id)
                > 0) return;
        BigDecimal spent = periodSpend(tenant, currency, scope, scopeId);
        jdbc.update(
                "INSERT INTO"
                        + " ai_budget(budget_id,policy_id,tenant_id,currency,scope_type,scope_id,period_start,limit_amount,spent_amount,enabled,updated_by)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id,
                policy.get("policy_id"),
                tenant,
                currency,
                scope,
                scopeId,
                period,
                policy.get("limit_amount"),
                spent,
                policy.get("enabled"),
                policy.get("updated_by"));
    }

    private String dimension(String scope) {
        return "AB_RUN".equals(scope) ? "c.ab_run_id=?" : "c.usage_date>=?";
    }

    private Object dimensionValue(String scope, String scopeId) {
        return "AB_RUN".equals(scope) ? scopeId : today().withDayOfMonth(1);
    }

    private BigDecimal periodSpend(String tenant, String currency, String scope, String scopeId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(c.cost,0)+(SELECT COALESCE(SUM(a.delta_amount),0)"
                        + " FROM ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id AND"
                        + " a.call_id=c.call_id)),0) FROM ai_model_call c WHERE c.tenant_id=? AND"
                        + " COALESCE(c.currency,(SELECT MIN(a.currency) FROM ai_model_call_adjustment a"
                        + " WHERE a.tenant_id=c.tenant_id AND a.call_id=c.call_id))=? AND "
                        + dimension(scope),
                BigDecimal.class,
                tenant,
                currency,
                dimensionValue(scope, scopeId));
    }

    /** 配置时纳入已发生费用；未知币种和未知结果不能被过滤而清零。显式重配记录审计并解除已解决的封锁。 */
    public void configureBudget(
            String tenant,
            String id,
            String currency,
            String scope,
            String scopeId,
            BigDecimal limit,
            boolean enabled,
            Long operator) {
        if (id == null
                || !id.matches("[A-Za-z0-9_-]{1,64}")
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || !Set.of("TENANT_MONTH", "AB_RUN").contains(scope)
                || scopeId == null
                || scopeId.length() > 64
                || ("AB_RUN".equals(scope) && scopeId.isBlank())
                || ("TENANT_MONTH".equals(scope) && !scopeId.isEmpty())
                || limit == null
                || limit.signum() < 0
                || operator == null) throw new IllegalArgumentException("预算参数不合法");
        final BigDecimal normalized = money(limit);
        atomicVoid(
                t -> {
                    lock(tenant);
                    if (enabled
                            && jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM ai_model_call c WHERE"
                                                    + " c.tenant_id=? AND (c.currency=? OR c.currency"
                                                    + " IS NULL) AND "
                                                    + dimension(scope)
                                                    + " AND (c.status='RUNNING' OR (c.cost IS NULL"
                                                    + " AND NOT EXISTS (SELECT 1 FROM"
                                                    + " ai_model_call_adjustment a WHERE"
                                                    + " a.tenant_id=c.tenant_id AND"
                                                    + " a.call_id=c.call_id)))",
                                            Integer.class,
                                            tenant,
                                            currency,
                                            dimensionValue(scope, scopeId))
                                    > 0) throw new IllegalStateException("本期存在未确认调用，请先核对再启用预算");
                    var existing =
                            jdbc.queryForList(
                                    "SELECT * FROM ai_budget_policy WHERE policy_id=? FOR UPDATE",
                                    id);
                    if (existing.isEmpty())
                        jdbc.update(
                                "INSERT INTO"
                                        + " ai_budget_policy(policy_id,tenant_id,currency,scope_type,scope_id,limit_amount,enabled,updated_by)"
                                        + " VALUES (?,?,?,?,?,?,?,?)",
                                id,
                                tenant,
                                currency,
                                scope,
                                scopeId,
                                normalized,
                                enabled ? 1 : 0,
                                operator);
                    else {
                        var p = existing.get(0);
                        if (!tenant.equals(p.get("tenant_id"))
                                || !currency.equals(p.get("currency"))
                                || !scope.equals(p.get("scope_type"))
                                || !scopeId.equals(p.get("scope_id")))
                            throw new IllegalArgumentException("预算身份不能变更");
                        jdbc.update(
                                "UPDATE ai_budget_policy SET"
                                        + " limit_amount=?,enabled=?,updated_by=?,revision=revision+1"
                                        + " WHERE policy_id=? AND tenant_id=?",
                                normalized,
                                enabled ? 1 : 0,
                                operator,
                                id,
                                tenant);
                    }
                    var policy =
                            jdbc.queryForMap(
                                    "SELECT * FROM ai_budget_policy WHERE tenant_id=? AND"
                                            + " policy_id=?",
                                    tenant,
                                    id);
                    materializePolicy(tenant, policy);
                    LocalDate period =
                            "AB_RUN".equals(scope)
                                    ? LocalDate.of(1970, 1, 1)
                                    : today().withDayOfMonth(1);
                    BigDecimal spent = periodSpend(tenant, currency, scope, scopeId);
                    jdbc.update(
                            "UPDATE ai_budget SET"
                                    + " limit_amount=?,enabled=?,spent_amount=?,blocked=CASE WHEN"
                                    + " ?+held_amount<=? THEN 0 ELSE 1"
                                    + " END,revision=revision+1,updated_by=? WHERE tenant_id=? AND"
                                    + " policy_id=? AND period_start=?",
                            normalized,
                            enabled ? 1 : 0,
                            spent,
                            spent,
                            normalized,
                            operator,
                            tenant,
                            id,
                            period);
                    jdbc.update(
                            "INSERT INTO"
                                    + " ai_budget_policy_audit(audit_id,tenant_id,policy_id,limit_amount,enabled,operator_id,created_at)"
                                    + " VALUES (?,?,?,?,?,?,?)",
                            UUID.randomUUID().toString(),
                            tenant,
                            id,
                            normalized,
                            enabled ? 1 : 0,
                            operator,
                            now());
                });
    }

    private static BigDecimal money(BigDecimal value) {
        try {
            BigDecimal normalized = value.setScale(12, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 28) throw new IllegalArgumentException("金额超出支持范围");
            return normalized;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("金额精度不能超过12位小数");
        }
    }

    private Map<String, Object> lockedCall(String tenant, String id) {
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM ai_model_call WHERE tenant_id=? AND call_id=? FOR UPDATE",
                        tenant,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("调用不存在或无权访问");
        return rows.get(0);
    }

    private static final String CALL_VIEW =
            "SELECT c.*,(SELECT COUNT(*) FROM ai_model_call_adjustment a WHERE"
                    + " a.tenant_id=c.tenant_id AND a.call_id=c.call_id) adjustment_count,CASE WHEN"
                    + " EXISTS (SELECT 1 FROM ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id"
                    + " AND a.call_id=c.call_id) THEN COALESCE(c.cost,0)+(SELECT SUM(a.delta_amount)"
                    + " FROM ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id AND"
                    + " a.call_id=c.call_id) ELSE c.cost END effective_cost,COALESCE(c.currency,(SELECT"
                    + " MIN(a.currency) FROM ai_model_call_adjustment a WHERE a.tenant_id=c.tenant_id"
                    + " AND a.call_id=c.call_id)) settlement_currency FROM ai_model_call c ";

    public Map<String, Object> call(String tenant, String id) {
        var rows = jdbc.queryForList(CALL_VIEW + "WHERE c.tenant_id=? AND c.call_id=?", tenant, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("调用不存在或无权访问");
        return rows.get(0);
    }

    public List<Map<String, Object>> calls(String tenant, Long user, String requestId, int limit) {
        return jdbc.queryForList(
                CALL_VIEW
                        + "WHERE c.tenant_id=? AND (? IS NULL OR c.user_id=?) AND (? IS NULL OR"
                        + " c.request_id=?) ORDER BY c.started_at DESC,c.call_id DESC LIMIT ?",
                tenant,
                user,
                user,
                requestId,
                requestId,
                Math.max(1, Math.min(limit, 100)));
    }

    public List<Map<String, Object>> summary(String tenant) {
        return jdbc.queryForList(
                "SELECT currency,SUM(input_tokens) input_tokens,SUM(output_tokens)"
                        + " output_tokens,SUM(request_count) request_count,SUM(unknown_calls)"
                        + " unknown_calls,SUM(known_cost) known_cost FROM ai_model_call_daily WHERE"
                        + " tenant_id=? GROUP BY currency HAVING SUM(request_count)>0 OR"
                        + " SUM(known_cost)<>0 ORDER BY currency",
                tenant);
    }

    public List<Map<String, Object>> budgets(String tenant) {
        return jdbc.queryForList(
                "SELECT * FROM ai_budget WHERE tenant_id=? ORDER BY period_start DESC,budget_id",
                tenant);
    }

    private static BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
