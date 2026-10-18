package com.nexus.report.subscription;

import cn.hutool.core.util.IdUtil;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.mapper.TimestampColumnMapRowMapper;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** 持久订阅按固定版本和每个接收人的当前权限生成独立任务。 */
public final class ReportSubscriptionService {
    /**
     * 调度状态只包含主库时间和最小错误，不暴露原始参数。
     *
     * @param subscriptionId 当前契约的subscriptionId配置或状态
     * @param versionId 不可变发布版本标识
     * @param enabled 当前契约的enabled配置或状态
     * @param nextAt 当前契约的nextAt配置或状态
     * @param error 当前契约的error配置或状态
     */
    public record Schedule(
            String subscriptionId,
            String versionId,
            boolean enabled,
            String nextAt,
            String error) {}

    /**
     * 执行记录保留单接收人、固定订阅版本和独立导出任务。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param recipient 当前契约的recipient配置或状态
     * @param job 当前契约的job配置或状态
     * @param state 任务或资源状态
     * @param error 当前契约的error配置或状态
     * @param scheduledAt 当前契约的scheduledAt配置或状态
     * @param eventType 当前契约的eventType配置或状态
     */
    public record Execution(
            String id,
            String recipient,
            String job,
            String state,
            String error,
            String scheduledAt,
            String eventType) {}

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前store状态或配置。 */
    private final JdbcReportStore store;

    /** 保存当前definitions状态或配置。 */
    private final ReportDefinitionService definitions;

    /** 保存当前runtime状态或配置。 */
    private final ReportRuntimeService runtime;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /** 保存当前properties状态或配置。 */
    private final ReportSubscriptionProperties properties;

    /** 参数冻结和计划默认时点使用的时钟，隔离测试可固定恢复时刻。 */
    private final Clock clock;

    /** 保存当前time状态或配置。 */
    private final SubscriptionTimePolicy time = new SubscriptionTimePolicy();

    /**
     * 复用主库版本和持久导出队列，不使用登录线程的上下文。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param json 固定版本的契约正文
     * @param properties 当前契约的properties配置或状态
     */
    public ReportSubscriptionService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler json,
            ReportSubscriptionProperties properties) {
        this(resources, jdbc, store, definitions, runtime, json, properties, Clock.systemUTC());
    }

    /**
     * 注入明确时钟，用于跨时区补跑及重启时点的隔离验证。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param json 固定版本的契约正文
     * @param properties 当前契约的properties配置或状态
     * @param clock 当前契约的clock配置或状态
     */
    public ReportSubscriptionService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler json,
            ReportSubscriptionProperties properties,
            Clock clock) {
        this.resources = resources;
        this.jdbc = jdbc;
        this.store = store;
        this.definitions = definitions;
        this.runtime = runtime;
        this.json = json;
        this.properties = properties;
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 始终刷新操作主体。
     *
     * @param actor 执行用户
     * @param permission 当前契约的permission配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor, String permission) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, permission);
        return current;
    }

    /**
     * 目录移除参数和接收人正文。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Draft> list(ReportActor actor) {
        return resources.list("SUBSCRIPTION", current(actor, "report:subscription:list")).stream()
                .map(
                        d ->
                                new ReportResourceStore.Draft(
                                        d.id(),
                                        d.tenant(),
                                        d.code(),
                                        d.title(),
                                        null,
                                        d.revision(),
                                        d.publishedVersion(),
                                        d.status(),
                                        d.owner()))
                .toList();
    }

    /**
     * 编辑读取仅允许资源编辑人员。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft detail(ReportActor actor, String id) {
        return resources.access(
                "SUBSCRIPTION", current(actor, "report:subscription:manage"), id, "EDIT");
    }

    /**
     * 初始化草稿时逐接收人核对固定报告及参数权限。
     *
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft create(
            ReportActor actor, String code, String title, SubscriptionDefinition model) {
        var current = current(actor, "report:subscription:manage");
        validate(current, model, true);
        return resources.create("SUBSCRIPTION", current, code, title, json.json(model));
    }

    /**
     * revision保存不更换已有调度的固定发布版本。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param title 当前契约的title配置或状态
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft save(
            ReportActor actor,
            String id,
            long revision,
            String title,
            SubscriptionDefinition model) {
        var current = current(actor, "report:subscription:manage");
        validate(current, model, true);
        return resources.save("SUBSCRIPTION", current, id, revision, json.json(model), title);
    }

    /**
     * 发布和nextAt登记在同一事务内，两个节点不会各自建立调度。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Version publish(ReportActor actor, String id, long revision) {
        var current = current(actor, "report:subscription:publish");
        return resources.atomic(
                () -> {
                    var version =
                            resources.publish(
                                    "SUBSCRIPTION",
                                    current,
                                    id,
                                    revision,
                                    (draft, number) -> {
                                        var refreshed =
                                                current(current, "report:subscription:publish");
                                        var model = parse(draft.json());
                                        validate(refreshed, model, true);
                                        var creator =
                                                definitions.resolve(
                                                        refreshed.tenantId(), draft.owner());
                                        ReportDefinitionService.require(
                                                creator, "report:subscription:publish");
                                        resources.access(
                                                "SUBSCRIPTION", creator, draft.id(), "PUBLISH");
                                        var creatorBinding = binding(creator, model);
                                        var report =
                                                store.version(
                                                                refreshed.tenantId(),
                                                                model.reportVersionId())
                                                        .orElseThrow();
                                        return json.json(
                                                Map.of(
                                                        "reportVersion",
                                                        report.id(),
                                                        "reportChecksum",
                                                        report.checksum(),
                                                        "datasetVersion",
                                                        report.datasetVersion(),
                                                        "ownerFingerprint",
                                                        definitions.fingerprint(
                                                                creator, creatorBinding)));
                                    },
                                    () ->
                                            resources.access(
                                                    "SUBSCRIPTION",
                                                    current(current, "report:subscription:publish"),
                                                    id,
                                                    "PUBLISH"));
                    var model = parse(version.json());
                    Instant next =
                            time.next(
                                    model.cron(),
                                    model.timezone(),
                                    model.dstPolicy(),
                                    clock.instant());
                    int changed =
                            jdbc.update(
                                    "UPDATE rpt_subscription_schedule SET"
                                        + " definition_version_id=?,enabled=1,next_at=?,last_error=NULL"
                                        + " WHERE tenant_id=? AND subscription_id=?",
                                    Long.parseLong(version.id()),
                                    Timestamp.from(next),
                                    current.tenantId(),
                                    Long.parseLong(id));
                    if (changed == 0)
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_subscription_schedule(tenant_id,subscription_id,definition_version_id,next_at)"
                                    + " VALUES(?,?,?,?)",
                                current.tenantId(),
                                Long.parseLong(id),
                                Long.parseLong(version.id()),
                                Timestamp.from(next));
                    return version;
                });
    }

    /**
     * 下线立即使通用导出中心的来源约束失效。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft state(
            ReportActor actor, String id, long revision, String state) {
        var current = current(actor, "report:subscription:publish");
        return resources.atomic(
                () -> {
                    var draft = resources.state("SUBSCRIPTION", current, id, revision, state);
                    if (state.equals("PUBLISHED")) {
                        var model =
                                parse(
                                        resources
                                                .version(
                                                        "SUBSCRIPTION",
                                                        current.tenantId(),
                                                        draft.publishedVersion())
                                                .orElseThrow()
                                                .json());
                        jdbc.update(
                                "UPDATE rpt_subscription_schedule SET"
                                    + " enabled=1,next_at=?,last_error=NULL WHERE tenant_id=? AND"
                                    + " subscription_id=?",
                                Timestamp.from(
                                        time.next(
                                                model.cron(),
                                                model.timezone(),
                                                model.dstPolicy(),
                                                clock.instant())),
                                current.tenantId(),
                                Long.parseLong(id));
                    } else
                        jdbc.update(
                                "UPDATE rpt_subscription_schedule SET enabled=0 WHERE tenant_id=?"
                                        + " AND subscription_id=?",
                                current.tenantId(),
                                Long.parseLong(id));
                    return draft;
                });
    }

    /**
     * 手动运行有独立幂等键，不冒充计划时刻。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param requestKey 当前契约的requestKey配置或状态
     */
    public void manual(ReportActor actor, String id, String requestKey) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{1,80}"))
            throw ReportFault.invalid("手动运行请求标识无效");
        var current = current(actor, "report:subscription:manage");
        var draft = resources.access("SUBSCRIPTION", current, id, "PUBLISH");
        if (!draft.status().equals("PUBLISHED"))
            throw new ReportFault("SUBSCRIPTION_OFFLINE", "订阅尚未发布或已停用", 409);
        var version =
                resources
                        .version("SUBSCRIPTION", current.tenantId(), draft.publishedVersion())
                        .orElseThrow();
        var model = parse(version.json());
        validate(current, model, false);
        enqueue(
                current,
                draft,
                version,
                model,
                clock.instant(),
                "M" + ReportDefinitionService.hash(requestKey).substring(0, 24),
                "MANUAL");
        drain();
    }

    /**
     * 仅显式启用的SnailJob适配会调用；每次有界扫描并在行锁内补跑。
     *
     * @param now 当前契约的now配置或状态
     * @return 当前受控处理的固定结果
     */
    public int dispatchDue(Instant now) {
        if (!properties.isSchedulingEnabled()) return 0;
        if (properties.getMaxCatchupOccurrences() < 1
                || properties.getMaxCatchupOccurrences() > 100)
            throw ReportFault.invalid("补跑批次范围无效");
        var due =
                jdbc.queryForList(
                        "SELECT tenant_id,subscription_id FROM rpt_subscription_schedule WHERE"
                            + " enabled=1 AND next_at<=? ORDER BY next_at,subscription_id LIMIT 50",
                        Timestamp.from(now));
        int[] fired = {0};
        for (var entry : due) {
            String tenant = entry.get("tenant_id").toString(),
                    id = entry.get("subscription_id").toString();
            try {
                resources.atomic(
                        () -> {
                            var row =
                                    jdbc.queryForObject(
                                            "SELECT * FROM rpt_subscription_schedule WHERE"
                                                + " tenant_id=? AND subscription_id=? FOR UPDATE",
                                            TimestampColumnMapRowMapper.INSTANCE,
                                            tenant,
                                            Long.parseLong(id));
                            if (((Number) row.get("enabled")).intValue() != 1) return null;
                            var draft = resources.read("SUBSCRIPTION", tenant, id).orElseThrow();
                            var owner = definitions.resolve(tenant, draft.owner());
                            ReportDefinitionService.require(owner, "report:subscription:publish");
                            resources.access("SUBSCRIPTION", owner, id, "PUBLISH");
                            if (!draft.status().equals("PUBLISHED"))
                                throw new ReportFault("SUBSCRIPTION_OFFLINE", "订阅已停用", 409);
                            var version =
                                    resources
                                            .version(
                                                    "SUBSCRIPTION",
                                                    tenant,
                                                    row.get("definition_version_id").toString())
                                            .orElseThrow();
                            if (!version.id().equals(draft.publishedVersion()))
                                throw ReportFault.invalid("调度版本与当前发布不一致");
                            var model = parse(version.json());
                            validate(owner, model, false);
                            Instant next = ((Timestamp) row.get("next_at")).toInstant(),
                                    earliest =
                                            now.minusSeconds(
                                                    model.catchupSeconds() == 0
                                                            ? 120
                                                            : model.catchupSeconds());
                            if (next.isBefore(earliest))
                                next =
                                        time.next(
                                                model.cron(),
                                                model.timezone(),
                                                model.dstPolicy(),
                                                earliest.minusMillis(1));
                            int count = 0;
                            while (!next.isAfter(now)
                                    && count++ < properties.getMaxCatchupOccurrences()) {
                                enqueue(
                                        owner,
                                        draft,
                                        version,
                                        model,
                                        next,
                                        "S" + next.toEpochMilli(),
                                        "SCHEDULED");
                                fired[0]++;
                                next =
                                        time.next(
                                                model.cron(),
                                                model.timezone(),
                                                model.dstPolicy(),
                                                next);
                            }
                            jdbc.update(
                                    "UPDATE rpt_subscription_schedule SET next_at=?,last_error=NULL"
                                            + " WHERE tenant_id=? AND subscription_id=?",
                                    Timestamp.from(next),
                                    tenant,
                                    Long.parseLong(id));
                            return null;
                        });
            } catch (ReportFault | DatasetAccessException failure) {
                jdbc.update(
                        "UPDATE rpt_subscription_schedule SET enabled=0,last_error=? WHERE"
                                + " tenant_id=? AND subscription_id=?",
                        ReportRuntimeService.code(failure),
                        tenant,
                        Long.parseLong(id));
            }
        }
        drain();
        return fired[0];
    }

    /**
     * 每个接收人的执行登记、入队和领取记录在同一事务内固定。
     *
     * @param owner 资源创建者或当前尝试持有者
     * @param draft 当前契约的draft配置或状态
     * @param version 当前契约的version配置或状态
     * @param model 当前契约的model配置或状态
     * @param scheduled 当前契约的scheduled配置或状态
     * @param key 当前契约的key配置或状态
     * @param event 当前契约的event配置或状态
     */
    private void enqueue(
            ReportActor owner,
            ReportResourceStore.Draft draft,
            ReportResourceStore.Version version,
            SubscriptionDefinition model,
            Instant scheduled,
            String key,
            String event) {
        resources.atomic(
                () -> {
                    int created =
                            jdbc.update(
                                    "INSERT IGNORE INTO"
                                        + " rpt_subscription_occurrence(tenant_id,subscription_id,scheduled_key,definition_version_id,scheduled_instant,event_type)"
                                        + " VALUES(?,?,?,?,?,?)",
                                    owner.tenantId(),
                                    Long.parseLong(draft.id()),
                                    key,
                                    Long.parseLong(version.id()),
                                    Timestamp.from(scheduled),
                                    event);
                    if (created == 0) return null;
                    for (String recipient : model.recipients()) {
                        String id = IdUtil.getSnowflakeNextIdStr();
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_subscription_run(execution_id,tenant_id,subscription_id,definition_version_id,scheduled_key,scheduled_instant,event_type,recipient_id,state)"
                                    + " VALUES(?,?,?,?,?,?,?,?, 'PENDING')",
                                Long.parseLong(id),
                                owner.tenantId(),
                                Long.parseLong(draft.id()),
                                Long.parseLong(version.id()),
                                key,
                                Timestamp.from(scheduled),
                                event,
                                Long.parseLong(recipient));
                    }
                    return null;
                });
    }

    /** 计算任务属于持久outbox，调度推进提交后再逐接收人独立处理。 */
    public void drain() {
        var pending =
                jdbc.queryForList(
                        "SELECT tenant_id,execution_id FROM rpt_subscription_run WHERE"
                                + " state='PENDING' OR state='RETRY_WAIT' AND attempts<3 AND"
                                + " next_retry_at<=CURRENT_TIMESTAMP ORDER BY"
                                + " scheduled_instant,execution_id LIMIT 100");
        for (var row : pending)
            prepare(row.get("tenant_id").toString(), row.get("execution_id").toString());
    }

    /**
     * 一个队列失败只回滚该接收人的任务，失败记录在事务外保留。
     *
     * @param tenant 租户隔离标识
     * @param execution 当前契约的execution配置或状态
     */
    private void prepare(String tenant, String execution) {
        try {
            resources.atomic(
                    () -> {
                        var row =
                                jdbc.queryForObject(
                                        "SELECT * FROM rpt_subscription_run WHERE tenant_id=? AND"
                                                + " execution_id=? FOR UPDATE",
                                        TimestampColumnMapRowMapper.INSTANCE,
                                        tenant,
                                        Long.parseLong(execution));
                        if (row.get("job_id") != null
                                || !Set.of("PENDING", "RETRY_WAIT")
                                        .contains(row.get("state").toString())) return null;
                        String subscription = row.get("subscription_id").toString();
                        var draft =
                                resources.read("SUBSCRIPTION", tenant, subscription).orElseThrow();
                        var owner = definitions.resolve(tenant, draft.owner());
                        ReportDefinitionService.require(owner, "report:subscription:publish");
                        resources.access("SUBSCRIPTION", owner, subscription, "PUBLISH");
                        var version =
                                resources
                                        .version(
                                                "SUBSCRIPTION",
                                                tenant,
                                                row.get("definition_version_id").toString())
                                        .orElseThrow();
                        var model = parse(version.json());
                        var recipient =
                                definitions.resolve(
                                        tenant, ((Number) row.get("recipient_id")).longValue());
                        var binding = binding(recipient, model);
                        var parameters =
                                parameters(
                                        binding,
                                        model,
                                        ((Timestamp) row.get("scheduled_instant")).toInstant(),
                                        recipient.userId());
                        String key = row.get("scheduled_key").toString();
                        var source =
                                new ReportRuntimeService.SourceConstraint(
                                        "SUBSCRIPTION", subscription, version.id());
                        var job =
                                runtime.enqueueFixed(
                                        recipient,
                                        model.reportVersionId(),
                                        model.format(),
                                        parameters,
                                        "sub_"
                                                + subscription
                                                + "_"
                                                + recipient.userId()
                                                + "_"
                                                + key,
                                        source);
                        jdbc.update(
                                "UPDATE rpt_subscription_run SET"
                                    + " job_id=?,state='QUEUED',attempts=attempts+1,error_code=NULL,next_retry_at=NULL"
                                    + " WHERE tenant_id=? AND execution_id=?",
                                Long.parseLong(job.id()),
                                tenant,
                                Long.parseLong(execution));
                        String delivery = IdUtil.getSnowflakeNextIdStr();
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_delivery(delivery_id,tenant_id,execution_id,subscription_id,definition_version_id,recipient_id,job_id,notification_state,expires_at)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                                Long.parseLong(delivery),
                                tenant,
                                Long.parseLong(execution),
                                Long.parseLong(subscription),
                                Long.parseLong(version.id()),
                                recipient.userId(),
                                Long.parseLong(job.id()),
                                model.notifyOnReady() && properties.isNotificationsEnabled()
                                        ? "PENDING"
                                        : "DISABLED",
                                Timestamp.from(job.expires()));
                        return null;
                    });
        } catch (ReportFault | DatasetAccessException failure) {
            String error = ReportRuntimeService.code(failure);
            boolean retry =
                    Set.of(
                                    "QUEUE_FULL",
                                    "QUERY_SOURCE_UNAVAILABLE",
                                    "ARTIFACT_STORE_UNAVAILABLE",
                                    "QUERY_EXECUTION_FAILED")
                            .contains(error);
            jdbc.update(
                    "UPDATE rpt_subscription_run SET"
                            + " state=?,error_code=?,attempts=attempts+1,next_retry_at=? WHERE"
                            + " tenant_id=? AND execution_id=? AND job_id IS NULL AND state IN"
                            + " ('PENDING','RETRY_WAIT')",
                    retry ? "RETRY_WAIT" : "BLOCKED",
                    error,
                    retry ? Timestamp.from(clock.instant().plusSeconds(60)) : null,
                    tenant,
                    Long.parseLong(execution));
        }
    }

    /**
     * 定义只允许白名单时间/参数规则；发布可检查所有接收人，运行分别检查。
     *
     * @param owner 资源创建者或当前尝试持有者
     * @param model 当前契约的model配置或状态
     * @param recipients 独立生成和领取文件的接收人列表
     */
    private void validate(ReportActor owner, SubscriptionDefinition model, boolean recipients) {
        if (model == null
                || model.formatVersion() != 1
                || !Set.of("PDF", "XLSX_DATA", "XLSX_LAYOUT").contains(model.format())
                || model.recipients().isEmpty()
                || model.recipients().size() > 20
                || new HashSet<>(model.recipients()).size() != model.recipients().size()
                || model.parameterRules().size() > 30
                || model.catchupSeconds() < 0
                || model.catchupSeconds() > 86400) throw ReportFault.invalid("订阅格式、接收人、补跑或通知开关无效");
        time.validate(model.cron(), model.timezone(), model.dstPolicy());
        var ownerBinding = binding(owner, model);
        for (var entry : model.parameterRules().entrySet()) {
            var parameter =
                    ownerBinding.compiled().dsl().parameters().stream()
                            .filter(p -> p.name().equals(entry.getKey()))
                            .findFirst()
                            .orElseThrow(() -> ReportFault.invalid("订阅参数未在固定报告声明"));
            var rule = entry.getValue();
            if (rule == null
                    || !Set.of("FIXED", "DATE_RELATIVE", "RECIPIENT_ID").contains(rule.kind())
                    || rule.value() != null && rule.value().length() > 500
                    || rule.kind().equals("RECIPIENT_ID") && !parameter.type().equals("ID")
                    || rule.kind().equals("DATE_RELATIVE")
                            && (!parameter.type().equals("DATE")
                                    || !Set.of(
                                                    "TODAY",
                                                    "YESTERDAY",
                                                    "MONTH_START",
                                                    "PREVIOUS_MONTH_START",
                                                    "PREVIOUS_MONTH_END")
                                            .contains(rule.value())))
                throw ReportFault.invalid("订阅参数规则或类型无效");
        }
        if (recipients) parameters(ownerBinding, model, clock.instant(), owner.userId());
        for (String recipient : model.recipients()) {
            try {
                if (!recipient.matches("[1-9][0-9]{0,18}")) throw ReportFault.invalid("接收人标识无效");
                long id = Long.parseLong(recipient);
                if (recipients) {
                    var actor = definitions.resolve(owner.tenantId(), id);
                    parameters(binding(actor, model), model, clock.instant(), id);
                }
            } catch (NumberFormatException invalid) {
                throw ReportFault.invalid("接收人标识无效");
            }
        }
    }

    /**
     * 固定报告、数据集和所有业务字段仍要求当前预览和导出权限。
     *
     * @param actor 执行用户
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportDefinitionService.Binding binding(
            ReportActor actor, SubscriptionDefinition model) {
        var report =
                store.version(actor.tenantId(), model.reportVersionId())
                        .orElseThrow(() -> new ReportFault("NOT_FOUND", "订阅报告版本不存在", 404));
        var definition =
                definitions.definition(actor, report.reportId(), "VIEW", "report:runtime:preview");
        definitions.definition(actor, report.reportId(), "EXPORT", "report:runtime:export");
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "订阅报告已下线", 409);
        var binding = definitions.bindVersion(actor, report);
        definitions.requireDatasetExport(actor, binding);
        return binding;
    }

    /**
     * 相对日期锚定计划Instant及显式时区，重试不会重新用TODAY覆盖。
     *
     * @param binding 当前契约的binding配置或状态
     * @param model 当前契约的model配置或状态
     * @param scheduled 当前契约的scheduled配置或状态
     * @param recipient 当前契约的recipient配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, Object> parameters(
            ReportDefinitionService.Binding binding,
            SubscriptionDefinition model,
            Instant scheduled,
            long recipient) {
        var input = new LinkedHashMap<String, Object>();
        LocalDate date = scheduled.atZone(ZoneId.of(model.timezone())).toLocalDate();
        for (var entry : model.parameterRules().entrySet()) {
            var rule = entry.getValue();
            Object value =
                    switch (rule.kind()) {
                        case "FIXED" -> rule.value();
                        case "RECIPIENT_ID" -> Long.toString(recipient);
                        case "DATE_RELATIVE" ->
                                switch (rule.value()) {
                                    case "TODAY" -> date.toString();
                                    case "YESTERDAY" -> date.minusDays(1).toString();
                                    case "MONTH_START" -> date.withDayOfMonth(1).toString();
                                    case "PREVIOUS_MONTH_START" ->
                                            date.minusMonths(1).withDayOfMonth(1).toString();
                                    default -> date.withDayOfMonth(1).minusDays(1).toString();
                                };
                        default -> throw ReportFault.invalid("参数规则未安装");
                    };
            input.put(entry.getKey(), value);
        }
        /** 先填充声明默认值及完整provider日期，再进行一次最终规范化和区间校验。 */
        for (var parameter : binding.compiled().dsl().parameters())
            if (!input.containsKey(parameter.name())
                    && parameter.defaultValue() != null
                    && !parameter.defaultValue().isBlank())
                input.put(parameter.name(), parameter.defaultValue());
        for (var parameter : binding.compiled().descriptor().parameters()) {
            if (parameter.type() != ValueType.DATE) continue;
            Object supplied = input.get(parameter.name());
            String effective =
                    supplied == null || supplied.toString().isBlank()
                            ? parameter.defaultValue()
                            : supplied.toString();
            if ("TODAY".equals(effective)) input.put(parameter.name(), date.toString());
            else if ("MONTHS_AGO_6".equals(effective))
                input.put(parameter.name(), date.minusMonths(6).toString());
            else if (effective != null && !effective.isBlank())
                input.put(parameter.name(), effective);
        }
        return ReportRuntimeService.parametersForStorage(
                DatasetQueryValidator.parameters(binding.compiled().descriptor(), input));
    }

    /**
     * 读取严格式的不可变订阅正文。
     *
     * @param text 当前契约的text配置或状态
     * @return 当前受控处理的固定结果
     */
    private SubscriptionDefinition parse(String text) {
        return json.read(text, SubscriptionDefinition.class);
    }

    /**
     * 编辑器读取明确的历史报告参数，不能隐式升级报告指针。
     *
     * @param versionId 不可变发布版本标识
     * @param parameters 固定参数契约
     */
    public record Source(String versionId, List<ReportDsl.Parameter> parameters) {}

    /**
     * 参数目录仍经过当前预览、导出及数据集资源权限。
     *
     * @param actor 执行用户
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public Source source(ReportActor actor, String version) {
        var current = current(actor, "report:subscription:manage");
        var model =
                new SubscriptionDefinition(
                        1,
                        version,
                        "XLSX_DATA",
                        Map.of(),
                        List.of(Long.toString(current.userId())),
                        "0 0 9 * * *",
                        "UTC",
                        "EARLIER_OFFSET",
                        3600,
                        false);
        return new Source(version, binding(current, model).compiled().dsl().parameters());
    }

    /**
     * 执行目录只给订阅管理人员，不作为接收人授权来源。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public List<Execution> executions(ReportActor actor, String id) {
        var current = current(actor, "report:subscription:list");
        resources.access("SUBSCRIPTION", current, id, "VIEW");
        return jdbc.query(
                "SELECT * FROM rpt_subscription_run WHERE tenant_id=? AND subscription_id=? ORDER"
                        + " BY scheduled_instant DESC,recipient_id LIMIT 200",
                (r, i) ->
                        new Execution(
                                Long.toString(r.getLong("execution_id")),
                                Long.toString(r.getLong("recipient_id")),
                                r.getObject("job_id") == null
                                        ? null
                                        : Long.toString(r.getLong("job_id")),
                                r.getString("state"),
                                r.getString("error_code"),
                                r.getTimestamp("scheduled_instant").toInstant().toString(),
                                r.getString("event_type")),
                current.tenantId(),
                Long.parseLong(id));
    }

    /**
     * 调度状态保留停用或错误原因，可复核断点补跑。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public Schedule schedule(ReportActor actor, String id) {
        var current = current(actor, "report:subscription:list");
        resources.access("SUBSCRIPTION", current, id, "VIEW");
        return jdbc
                .query(
                        "SELECT * FROM rpt_subscription_schedule WHERE tenant_id=? AND"
                                + " subscription_id=?",
                        (r, i) ->
                                new Schedule(
                                        id,
                                        Long.toString(r.getLong("definition_version_id")),
                                        r.getInt("enabled") == 1,
                                        r.getTimestamp("next_at").toInstant().toString(),
                                        r.getString("last_error")),
                        current.tenantId(),
                        Long.parseLong(id))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 管理人员重试已有接收人任务，固定版本、时刻和范围不能替换。
     *
     * @param actor 执行用户
     * @param executionId 当前契约的executionId配置或状态
     */
    public void retry(ReportActor actor, String executionId) {
        var current = current(actor, "report:subscription:manage");
        boolean needsPreparation = resources.atomic(() -> {
            var row = jdbc.queryForMap(
                    "SELECT * FROM rpt_subscription_run WHERE tenant_id=? AND execution_id=? FOR UPDATE",
                    current.tenantId(), Long.parseLong(executionId));
            resources.access("SUBSCRIPTION", current, row.get("subscription_id").toString(), "PUBLISH");
            if ("CANCELLED".equals(row.get("state")))
                throw new ReportFault("RETRY_NOT_ALLOWED", "已取消的订阅执行不能重试", 409);
            if (row.get("job_id") == null) {
                jdbc.update(
                        "UPDATE rpt_subscription_run SET"
                                + " state='PENDING',next_retry_at=NULL,error_code=NULL WHERE tenant_id=?"
                                + " AND execution_id=?",
                        current.tenantId(), Long.parseLong(executionId));
                return true;
            }
            var recipient = definitions.resolve(
                    current.tenantId(), ((Number) row.get("recipient_id")).longValue());
            var job = runtime.job(recipient, row.get("job_id").toString());
            runtime.retry(recipient, job.id(), job.retryRound());
            jdbc.update(
                    "UPDATE rpt_subscription_run SET state='QUEUED',error_code=NULL,attempts=attempts+1"
                            + " WHERE tenant_id=? AND execution_id=?",
                    current.tenantId(), Long.parseLong(executionId));
            return false;
        });
        if (needsPreparation) prepare(current.tenantId(), executionId);
    }

    /**
     * 取消只作用于选定接收人的已有任务，不重复发通知。
     *
     * @param actor 执行用户
     * @param executionId 当前契约的executionId配置或状态
     */
    public void cancel(ReportActor actor, String executionId) {
        var current = current(actor, "report:subscription:manage");
        resources.atomic(
                () -> {
                    var row =
                            jdbc.queryForMap(
                                    "SELECT * FROM rpt_subscription_run WHERE tenant_id=? AND"
                                            + " execution_id=? FOR UPDATE",
                                    current.tenantId(),
                                    Long.parseLong(executionId));
                    resources.access(
                            "SUBSCRIPTION",
                            current,
                            row.get("subscription_id").toString(),
                            "PUBLISH");
                    if (row.get("job_id") != null) {
                        var job =
                                store.job(current.tenantId(), row.get("job_id").toString())
                                        .orElseThrow(ReportFault::denied);
                        if (job.actor() != ((Number) row.get("recipient_id")).longValue())
                            throw ReportFault.denied();
                        var run = store.run(current.tenantId(), job.runId()).orElseThrow();
                        var source = runtime.payload(run).constraint();
                        if (source == null
                                || !source.resourceId()
                                        .equals(row.get("subscription_id").toString()))
                            throw ReportFault.denied();
                        var recipient =
                                new ReportActor(
                                        current.tenantId(),
                                        job.actor(),
                                        current.departmentId(),
                                        false,
                                        List.of());
                        store.cancel(recipient, job.id());
                        var cancelled = store.job(current.tenantId(), job.id()).orElseThrow(ReportFault::denied);
                        if (!Set.of("CANCELLED", "CANCEL_REQUESTED").contains(cancelled.state()))
                            throw new ReportFault("CANCEL_NOT_ALLOWED", "执行已结束，不能取消，请刷新记录", 409);
                    }
                    jdbc.update(
                            "UPDATE rpt_subscription_run SET state='CANCELLED' WHERE tenant_id=?"
                                    + " AND execution_id=?",
                            current.tenantId(),
                            Long.parseLong(executionId));
                    return null;
                });
    }
}
