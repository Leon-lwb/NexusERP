package com.nexus.report.subscription;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.mapper.TimestampColumnMapRowMapper;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** 交付记录只提供当前接收人授权领取入口，通知不携带文件或业务明细。 */
public final class ReportDeliveryService {
    /**
     * 接收人目录不包含生成者参数或其他接收人的文件。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param subscriptionId 当前契约的subscriptionId配置或状态
     * @param jobId 当前契约的jobId配置或状态
     * @param state 任务或资源状态
     * @param notification 当前契约的notification配置或状态
     * @param expires 当前契约的expires配置或状态
     * @param error 当前契约的error配置或状态
     */
    public record Delivery(
            String id,
            String subscriptionId,
            String jobId,
            String state,
            String notification,
            String expires,
            String error) {}

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

    /** 保存当前notifier状态或配置。 */
    private final ReportDeliveryNotificationPort notifier;

    /** 不可变交付ID游标，满批推进并在遍历末尾回卷。 */
    private long recoveryCursor;

    /**
     * 通知端口默认关闭，隔离测试注入合成端口。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param json 固定版本的契约正文
     * @param properties 当前契约的properties配置或状态
     * @param notifier 当前契约的notifier配置或状态
     */
    public ReportDeliveryService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler json,
            ReportSubscriptionProperties properties,
            ReportDeliveryNotificationPort notifier) {
        this.resources = resources;
        this.jdbc = jdbc;
        this.store = store;
        this.definitions = definitions;
        this.runtime = runtime;
        this.json = json;
        this.properties = properties;
        this.notifier = notifier;
    }

    /** 有界同步任务状态并对每个接收人重新鉴权，权限失败不触发通知。 */
    public void reconcile() {
        var deliveries =
                jdbc.queryForList(
                        "SELECT tenant_id,delivery_id,execution_id FROM rpt_delivery WHERE delivery_id>? AND"
                                + " (state IN ('PREPARING','FAILED','BLOCKED') OR state IN"
                                + " ('READY','CLAIMED') AND (notification_state='PENDING' OR"
                                + " expires_at<=CURRENT_TIMESTAMP)) ORDER BY delivery_id LIMIT 200",
                        recoveryCursor);
        if (deliveries.isEmpty() || deliveries.size() < 200) recoveryCursor = 0;
        else
            recoveryCursor =
                    ((Number) deliveries.get(deliveries.size() - 1).get("delivery_id")).longValue();
        for (var row : deliveries) {
            String tenant = row.get("tenant_id").toString(), id = row.get("delivery_id").toString();
            try {
                resources.atomic(
                        () -> {
                            var executionStates = jdbc.queryForList(
                                    "SELECT state FROM rpt_subscription_run WHERE tenant_id=? AND execution_id=? FOR UPDATE",
                                    String.class, tenant, ((Number) row.get("execution_id")).longValue());
                            if (executionStates.size() != 1) throw ReportFault.denied();
                            var delivery =
                                    jdbc.queryForObject(
                                            "SELECT * FROM rpt_delivery WHERE tenant_id=? AND"
                                                    + " delivery_id=? FOR UPDATE",
                                            TimestampColumnMapRowMapper.INSTANCE,
                                            tenant,
                                            Long.parseLong(id));
                            if ("CANCELLED".equals(executionStates.get(0))) {
                                update(tenant, id, "CANCELLED", "CANCELLED");
                                return null;
                            }
                            if (!((Timestamp) delivery.get("expires_at"))
                                    .toInstant()
                                    .isAfter(Instant.now())) {
                                update(tenant, id, "EXPIRED", "SNAPSHOT_EXPIRED");
                                return null;
                            }
                            var recipient =
                                    definitions.resolve(
                                            tenant,
                                            ((Number) delivery.get("recipient_id")).longValue());
                            var job = runtime.job(recipient, delivery.get("job_id").toString());
                            String state =
                                    switch (job.state()) {
                                        case "SUCCEEDED" ->
                                                delivery.get("claimed_at") == null
                                                        ? "READY"
                                                        : "CLAIMED";
                                        case "FAILED" -> "FAILED";
                                        case "CANCELLED", "CANCEL_REQUESTED" -> "CANCELLED";
                                        case "EXPIRED" -> "EXPIRED";
                                        default -> "PREPARING";
                                    };
                            update(tenant, id, state, job.error());
                            jdbc.update(
                                    "UPDATE rpt_subscription_run SET state=?,error_code=? WHERE"
                                            + " tenant_id=? AND execution_id=? AND state<>'CANCELLED'",
                                    state.equals("CLAIMED") ? "SUCCEEDED" : state,
                                    job.error(),
                                    tenant,
                                    ((Number) delivery.get("execution_id")).longValue());
                            if (Set.of("READY", "CLAIMED").contains(state)
                                    && properties.isNotificationsEnabled()
                                    && "PENDING".equals(delivery.get("notification_state"))) {
                                if (notifier == null)
                                    throw new ReportFault(
                                            "NOTIFICATION_PORT_UNAVAILABLE", "站内通知适配未安装", 409);
                                runtime.job(recipient, job.id());
                                var draft =
                                        resources
                                                .read(
                                                        "SUBSCRIPTION",
                                                        tenant,
                                                        delivery.get("subscription_id").toString())
                                                .orElseThrow();
                                var model =
                                        json.read(
                                                resources
                                                        .version(
                                                                "SUBSCRIPTION",
                                                                tenant,
                                                                delivery.get(
                                                                                "definition_version_id")
                                                                        .toString())
                                                        .orElseThrow()
                                                        .json(),
                                                SubscriptionDefinition.class);
                                if (model.notifyOnReady()) {
                                    notifier.available(
                                            tenant, recipient.userId(), id, draft.title());
                                    jdbc.update(
                                            "UPDATE rpt_delivery SET"
                                                + " notification_state='SENT',notification_attempts=notification_attempts+1"
                                                + " WHERE tenant_id=? AND delivery_id=?",
                                            tenant,
                                            Long.parseLong(id));
                                }
                            }
                            return null;
                        });
            } catch (ReportFault | DatasetAccessException denied) {
                update(tenant, id, "BLOCKED", ReportRuntimeService.code(denied));
            } catch (RuntimeException transientFailure) {
                jdbc.update(
                        "UPDATE rpt_delivery SET"
                            + " notification_attempts=notification_attempts+1,error_code='NOTIFICATION_RETRY'"
                            + " WHERE tenant_id=? AND delivery_id=? AND"
                            + " notification_state='PENDING'",
                        tenant,
                        Long.parseLong(id));
            }
        }
    }

    /**
     * 当前用户只能读取自己的仍受权交付，失效文件入口被隐藏。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<Delivery> list(ReportActor actor) {
        var current = current(actor);
        var result = new ArrayList<Delivery>();
        for (var row :
                jdbc.query(
                        "SELECT * FROM rpt_delivery WHERE tenant_id=? AND recipient_id=? ORDER BY"
                                + " delivery_id DESC LIMIT 200",
                        TimestampColumnMapRowMapper.INSTANCE,
                        current.tenantId(),
                        current.userId())) {
            String job = row.get("job_id").toString(),
                    state = row.get("state").toString(),
                    error = Objects.toString(row.get("error_code"), null);
            try {
                var actual = runtime.job(current, job);
                state =
                        switch (actual.state()) {
                            case "SUCCEEDED" -> row.get("claimed_at") == null ? "READY" : "CLAIMED";
                            case "FAILED" -> "FAILED";
                            case "CANCELLED", "CANCEL_REQUESTED" -> "CANCELLED";
                            case "EXPIRED" -> "EXPIRED";
                            default -> "PREPARING";
                        };
                error = actual.error();
            } catch (ReportFault denied) {
                job = null;
                state = "EXECUTION_CANCELLED".equals(denied.code()) ? "CANCELLED" : "BLOCKED";
                error = denied.code();
            }
            result.add(
                    new Delivery(
                            row.get("delivery_id").toString(),
                            row.get("subscription_id").toString(),
                            job,
                            state,
                            row.get("notification_state").toString(),
                            ((Timestamp) row.get("expires_at")).toInstant().toString(),
                            error));
        }
        return List.copyOf(result);
    }

    /**
     * 领取时再次核对来源、报告、业务字段、范围、对象完成状态和有效期。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public String claim(ReportActor actor, String id) {
        var current = current(actor);
        return resources.atomic(
                () -> {
                    var row =
                            jdbc
                                    .query(
                                            "SELECT * FROM rpt_delivery WHERE tenant_id=? AND"
                                                + " delivery_id=? AND recipient_id=? FOR UPDATE",
                                            TimestampColumnMapRowMapper.INSTANCE,
                                            current.tenantId(),
                                            Long.parseLong(id),
                                            current.userId())
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(ReportFault::denied);
                    if (!((Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now()))
                        throw new ReportFault("SNAPSHOT_EXPIRED", "交付已经过期", 410);
                    var job = runtime.job(current, row.get("job_id").toString());
                    if (!job.state().equals("SUCCEEDED") || job.file() == null)
                        throw new ReportFault("RUN_NOT_READY", "交付文件尚未完成", 409);
                    jdbc.update(
                            "UPDATE rpt_delivery SET state='CLAIMED',claimed_at=CURRENT_TIMESTAMP"
                                    + " WHERE tenant_id=? AND delivery_id=?",
                            current.tenantId(),
                            Long.parseLong(id));
                    store.audit(current, job.id(), "SUBSCRIPTION_CLAIM", "SUCCEEDED", null);
                    return job.id();
                });
    }

    /**
     * 目录和领取都要求接收人的普通导出权限。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, "report:runtime:export");
        return current;
    }

    /**
     * 最小交付状态，不复制SDK或业务异常消息。
     *
     * @param tenant 租户隔离标识
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param state 任务或资源状态
     * @param error 当前契约的error配置或状态
     */
    private void update(String tenant, String id, String state, String error) {
        jdbc.update(
                "UPDATE rpt_delivery SET state=?,error_code=? WHERE tenant_id=? AND delivery_id=?",
                state,
                error,
                tenant,
                Long.parseLong(id));
    }
}
