package com.nexus.mes.label.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelPrintTask;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 持久化任务仓储：冻结字段只 INSERT，状态 CAS 与日志分别在短 MES 事务中原子提交。
 */
@Service
public class LabelPrintTaskStore {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final MesProgressTransactionGuard connectionGuard;

    /**
     * 使用与JdbcTemplate相同的路由数据源建立短事务，避免误注入工作流等其他事务管理器。
     */
    public LabelPrintTaskStore(JdbcTemplate jdbc, MesProgressTransactionGuard connectionGuard) {
        this.jdbc = jdbc;
        this.transactions =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));
        this.connectionGuard = connectionGuard;
    }

    private static final BeanPropertyRowMapper<MesLabelPrintTask> TASK =
            new BeanPropertyRowMapper<>(MesLabelPrintTask.class);
    private static final BeanPropertyRowMapper<MesLabelPrintLog> LOG =
            new BeanPropertyRowMapper<>(MesLabelPrintLog.class);

    /**
     * 自动任务必须加入已存在的可写 MES 业务事务；手动任务则先独立提交后才能发送。
     */
    public MesLabelPrintTask create(MesLabelPrintLog input, String requestId, boolean automatic) {
        String tenant = tenant();
        if (input.getPrintSource() == null
                || !List.of("AUTO", "TEST", "REPRINT").contains(input.getPrintSource())) {
            throw new ServiceException("打印任务来源不合法");
        }
        if (automatic != "AUTO".equals(input.getPrintSource())) {
            throw new ServiceException("AUTO打印必须由同MES业务事务提交");
        }
        if (automatic && !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ServiceException("自动打印任务必须和SN业务在同一MES事务中保存");
        }
        return required(
                () -> {
                    MesLabelPrintLog frozen = LabelPrintSnapshot.copy(input);
                    if (StringUtils.isNotBlank(frozen.getTenantId())
                            && !tenant.equals(frozen.getTenantId())) {
                        throw new ServiceException("打印任务租户与认证上下文不符");
                    }
                    frozen.setTenantId(tenant);
                    if (frozen.getTemplateId() == null
                            || (automatic
                                    && (frozen.getSnId() == null
                                            || frozen.getTemplateRevision() == null))) {
                        throw new ServiceException("打印任务缺少SN或模板版本依据");
                    }
                    if ("REPRINT".equals(frozen.getPrintSource())
                            && (frozen.getOriginalPrintLogId() == null
                                    || StringUtils.isBlank(frozen.getReprintReason())
                                    || frozen.getReprintReason().trim().length() > 500)) {
                        throw new ServiceException("补打必须关联原打印任务并填写不超过500字符的原因");
                    }
                    if ("REPRINT".equals(frozen.getPrintSource())) {
                        frozen.setReprintReason(frozen.getReprintReason().trim());
                        MesLabelPrintLog original = findRecord(frozen.getOriginalPrintLogId());
                        if (original == null
                                || !Objects.equals(original.getSnId(), frozen.getSnId())
                                || !Objects.equals(
                                        original.getTemplateId(), frozen.getTemplateId())) {
                            throw new ServiceException("原打印任务不存在、无权访问或与本次SN模板不匹配");
                        }
                        if ("PENDING".equals(original.getResult())
                                || "SENDING".equals(original.getResult())) {
                            throw new ServiceException("原任务仍待发或发送中，不能另建补打任务");
                        }
                        if (StringUtils.isNotBlank(original.getRenderedPayload())
                                && StringUtils.isNotBlank(original.getPayloadCharset())) {
                            var oldBean = new org.springframework.beans.BeanWrapperImpl(original);
                            var newBean = new org.springframework.beans.BeanWrapperImpl(frozen);
                            for (String property :
                                    new String[] {
                                        "snCode",
                                        "templateCode",
                                        "templateRevision",
                                        "templateSnapshot",
                                        "fieldSnapshot",
                                        "contentHash",
                                        "payloadHash",
                                        "renderedPayload",
                                        "payloadCharset",
                                        "printerHost",
                                        "printerPort"
                                    }) {
                                Object value = oldBean.getPropertyValue(property);
                                if (value != null
                                        && !Objects.equals(
                                                value, newBean.getPropertyValue(property))) {
                                    throw new ServiceException("补打必须复用原任务冻结内容");
                                }
                            }
                            frozen.setSnapshotMode(
                                    StringUtils.isBlank(original.getSnapshotMode())
                                            ? "LEGACY_RECORD"
                                            : original.getSnapshotMode());
                        } else if (StringUtils.isNotBlank(original.getTemplateSnapshot())
                                || "FROZEN".equals(original.getSnapshotMode())) {
                            throw new ServiceException("新原任务没有可发送冻结指令，不得按当前模板重建补打");
                        } else {
                            frozen.setSnapshotMode("LEGACY_RECORD");
                            if (StringUtils.isBlank(frozen.getRemark())) {
                                frozen.setRemark("历史原记录缺少冻结内容，本次按当前模板生成，无法证明与原标签一致");
                            }
                        }
                    }
                    if (!"FAIL".equals(frozen.getResult())) {
                        LabelPrintSnapshot.payload(
                                frozen,
                                frozen.getRenderedPayload(),
                                java.nio.charset.Charset.forName(frozen.getPayloadCharset()));
                    }
                    String intent =
                            digest(
                                    tenant,
                                    frozen.getPrintSource(),
                                    frozen.getSnId(),
                                    frozen.getSnCode(),
                                    frozen.getTemplateId(),
                                    "REPRINT".equals(frozen.getPrintSource())
                                            ? null
                                            : frozen.getTemplateRevision(),
                                    frozen.getOriginalPrintLogId(),
                                    frozen.getReprintReason(),
                                    frozen.getOperatorId());
                    if (requestId != null && !requestId.matches("[A-Za-z0-9:_.-]{1,128}")) {
                        throw new ServiceException("打印请求ID格式不合法");
                    }
                    String key =
                            automatic
                                    ? digest(
                                            "SN_CREATED",
                                            tenant,
                                            frozen.getSnId(),
                                            frozen.getTemplateId(),
                                            frozen.getTemplateRevision())
                                    : requestId == null
                                            ? digest("LEGACY_MANUAL", intent)
                                            : digest("MANUAL", tenant, requestId);
                    MesLabelPrintTask existing = byKey(tenant, key, false);
                    if (existing != null) {
                        return sameIntent(existing, intent, automatic);
                    }
                    MesLabelPrintTask task = new MesLabelPrintTask();
                    task.setTaskId(IdWorker.getId());
                    task.setTenantId(tenant);
                    task.setTaskKey(key);
                    task.setRequestFingerprint(intent);
                    task.setStatus("FAIL".equals(frozen.getResult()) ? "FAIL" : "PENDING");
                    if (automatic) {
                        var old =
                                jdbc.query(
                                        "SELECT l.* FROM mes_label_print_log l WHERE l.tenant_id=?"
                                            + " AND l.sn_id=? AND l.template_id=? AND l.result IN"
                                            + " ('SENT','SUCCESS') AND NOT EXISTS (SELECT 1 FROM"
                                            + " mes_label_print_task t WHERE"
                                            + " t.tenant_id=l.tenant_id AND t.task_id=l.log_id)"
                                            + " ORDER BY l.log_id DESC LIMIT 1",
                                        LOG,
                                        tenant,
                                        frozen.getSnId(),
                                        frozen.getTemplateId());
                        if (!old.isEmpty()) {
                            frozen = old.get(0);
                            task.setStatus("LEGACY_SENT");
                            task.setLastError("已有历史发送留痕，本任务不发送；旧版模板及是否出纸未知");
                            frozen.setOriginalPrintLogId(frozen.getLogId());
                            if (StringUtils.isBlank(frozen.getSnapshotMode())) {
                                frozen.setSnapshotMode("LEGACY_RECORD");
                            }
                        }
                    }
                    frozen.setLogId(task.getTaskId());
                    if (!"LEGACY_SENT".equals(task.getStatus())) {
                        if (frozen.getSnId() == null) {
                            frozen.setRetryCount(0);
                        } else {
                            Long prior =
                                    jdbc.queryForObject(
                                            "SELECT (SELECT COUNT(*) FROM mes_label_print_task"
                                                + " WHERE tenant_id=? AND sn_id=? AND"
                                                + " template_id=?) + (SELECT COUNT(*) FROM"
                                                + " mes_label_print_log l WHERE l.tenant_id=? AND"
                                                + " l.sn_id=? AND l.template_id=? AND NOT EXISTS"
                                                + " (SELECT 1 FROM mes_label_print_task t WHERE"
                                                + " t.tenant_id=l.tenant_id AND"
                                                + " t.task_id=l.log_id))",
                                            Long.class,
                                            tenant,
                                            frozen.getSnId(),
                                            frozen.getTemplateId(),
                                            tenant,
                                            frozen.getSnId(),
                                            frozen.getTemplateId());
                            frozen.setRetryCount(Math.toIntExact(Objects.requireNonNull(prior)));
                        }
                    }
                    task.setSnId(frozen.getSnId());
                    task.setTemplateId(frozen.getTemplateId());
                    task.setTemplateRevision(frozen.getTemplateRevision());
                    task.setPrintSource(frozen.getPrintSource());
                    task.setOriginalPrintLogId(frozen.getOriginalPrintLogId());
                    task.setReprintReason(frozen.getReprintReason());
                    task.setFrozenRecord(LabelPrintSnapshot.recordJson(frozen));
                    task.setRecordHash(digestBytes(task.getFrozenRecord()));
                    task.setAttemptCount(0);
                    task.setCreatedAt(now());
                    task.setUpdatedAt(task.getCreatedAt());
                    if ("FAIL".equals(task.getStatus())) {
                        task.setLastError(shortError(frozen.getFailReason()));
                        task.setCompletedAt(task.getCreatedAt());
                    }
                    try {
                        new NamedParameterJdbcTemplate(jdbc)
                                .update(
                                        "INSERT INTO"
                                            + " mes_label_print_task(task_id,tenant_id,task_key,request_fingerprint,sn_id,template_id,template_revision,print_source,original_print_log_id,reprint_reason,frozen_record,record_hash,status,attempt_count,last_error,created_at,updated_at,completed_at)"
                                            + " VALUES(:taskId,:tenantId,:taskKey,:requestFingerprint,:snId,:templateId,:templateRevision,:printSource,:originalPrintLogId,:reprintReason,:frozenRecord,:recordHash,:status,:attemptCount,:lastError,:createdAt,:updatedAt,:completedAt)",
                                        new BeanPropertySqlParameterSource(task));
                    } catch (DuplicateKeyException duplicate) {
                        MesLabelPrintTask winner = byKey(tenant, key, true);
                        if (winner == null) {
                            throw duplicate;
                        }
                        return sameIntent(winner, intent, automatic);
                    }
                    if ("FAIL".equals(task.getStatus())) {
                        appendAudit(task);
                    }
                    return task;
                });
    }

    /**
     * 只领取尚未尝试的待发任务，SENDING 即使过期也永不重新领取。
     */
    public MesLabelPrintTask claim(Long taskId, String leaseToken, int leaseSeconds) {
        return shortTransaction(
                () -> {
                    Timestamp now = now();
                    Timestamp until =
                            Timestamp.from(now.toInstant().plusSeconds(Math.max(30, leaseSeconds)));
                    int changed =
                            jdbc.update(
                                    "UPDATE mes_label_print_task SET"
                                        + " status='SENDING',attempt_count=1,lease_token=?,lease_until=?,sending_started_at=?,updated_at=?"
                                        + " WHERE tenant_id=? AND task_id=? AND status='PENDING'"
                                        + " AND attempt_count=0",
                                    leaseToken,
                                    until,
                                    now,
                                    now,
                                    tenant(),
                                    taskId);
                    return changed == 1 ? rawFind(taskId) : null;
                });
    }

    /**
     * 发送前再次核验数据库租约，防止准备载荷时暂停的旧工作线程继续写网络。
     */
    public boolean hasCurrentLease(Long taskId, String token) {
        return read(
                () ->
                        jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM mes_label_print_task WHERE"
                                            + " tenant_id=? AND task_id=? AND status='SENDING' AND"
                                            + " lease_token=? AND lease_until>CURRENT_TIMESTAMP AND"
                                            + " attempt_count=1",
                                        Integer.class,
                                        tenant(),
                                        taskId,
                                        token)
                                == 1);
    }

    /**
     * 网络结束后以原租约条件落终态和一次审计；旧工作线程不能覆盖 UNKNOWN。
     */
    public void finish(Long taskId, String leaseToken, LabelPrinterTransport.Result result) {
        if (!List.of("SENT", "FAIL", "UNKNOWN").contains(result.status())) {
            throw new ServiceException("非法打印终态");
        }
        shortTransaction(
                () -> {
                    Timestamp now = now();
                    int changed =
                            jdbc.update(
                                    "UPDATE mes_label_print_task SET"
                                        + " status=?,last_error=?,completed_at=?,updated_at=? WHERE"
                                        + " tenant_id=? AND task_id=? AND status='SENDING' AND"
                                        + " lease_token=? AND lease_until>?",
                                    result.status(),
                                    shortError(result.error()),
                                    now,
                                    now,
                                    tenant(),
                                    taskId,
                                    leaseToken,
                                    now);
                    if (changed == 1) {
                        appendAudit(rawFind(taskId));
                    } else {
                        expire(taskId, now);
                    }
                    return null;
                });
    }

    /**
     * 回收发送中宕机任务仅标 UNKNOWN 并追加审计，不重置 PENDING、不增加重发次数。
     */
    public void recoverExpired() {
        shortTransaction(
                () -> {
                    Timestamp now = now();
                    List<Long> ids =
                            jdbc.queryForList(
                                    "SELECT task_id FROM mes_label_print_task WHERE tenant_id=? AND"
                                            + " status='SENDING' AND lease_until<=? ORDER BY"
                                            + " lease_until,task_id LIMIT 25",
                                    Long.class,
                                    tenant(),
                                    now);
                    for (Long id : ids) {
                        expire(id, now);
                    }
                    return null;
                });
    }

    /**
     * 轮询只读待发键，实际竞争由短领取事务中的条件更新裁决。
     */
    public List<Long> pendingIds() {
        return read(
                () ->
                        jdbc.queryForList(
                                "SELECT task_id FROM mes_label_print_task WHERE tenant_id=? AND"
                                        + " status='PENDING' AND attempt_count=0 ORDER BY"
                                        + " created_at,task_id LIMIT 10",
                                Long.class,
                                tenant()));
    }

    /**
     * 按认证租户读取任务，不允许调用方传入任意租户。
     */
    public MesLabelPrintTask find(Long taskId) {
        return read(() -> rawFind(taskId));
    }

    /**
     * 兼容原日志ID：新任务优先，旧日志只读且不补造快照。
     */
    public MesLabelPrintLog findRecord(Long id) {
        return read(
                () -> {
                    MesLabelPrintTask task = rawFind(id);
                    if (task != null) {
                        return record(task);
                    }
                    var rows =
                            jdbc.query(
                                    "SELECT * FROM mes_label_print_log WHERE tenant_id=? AND"
                                            + " log_id=?",
                                    LOG,
                                    tenant(),
                                    id);
                    return rows.isEmpty() ? null : rows.get(0);
                });
    }

    /**
     * 保留旧查询签名，按租户取最近记录；补打入口仍必须明确提交原任务及原因。
     */
    public MesLabelPrintLog findLatestRecord(Long snId, Long templateId) {
        return read(
                () -> {
                    var ids =
                            jdbc.queryForList(
                                    "SELECT task_id AS id FROM mes_label_print_task WHERE"
                                        + " tenant_id=? AND sn_id=? AND template_id=? UNION ALL"
                                        + " SELECT log_id AS id FROM mes_label_print_log WHERE"
                                        + " tenant_id=? AND sn_id=? AND template_id=? ORDER BY id"
                                        + " DESC LIMIT 1",
                                    Long.class,
                                    tenant(),
                                    snId,
                                    templateId,
                                    tenant(),
                                    snId,
                                    templateId);
                    return ids.isEmpty() ? null : findRecord(ids.get(0));
                });
    }

    /**
     * 先核验完整记录摘要，再恢复独立对象，状态及时间只来自任务行而非调用方。
     */
    public MesLabelPrintLog record(MesLabelPrintTask task) {
        if (!tenant().equals(task.getTenantId())
                || !digestBytes(task.getFrozenRecord()).equals(task.getRecordHash())) {
            throw new ServiceException("打印任务租户或冻结摘要不一致");
        }
        MesLabelPrintLog record = LabelPrintSnapshot.readRecord(task.getFrozenRecord());
        if (!Objects.equals(task.getTaskId(), record.getLogId())
                || !task.getTenantId().equals(record.getTenantId())) {
            throw new ServiceException("打印冻结记录与任务标识不一致");
        }
        record.setResult(task.getStatus());
        record.setFailReason(task.getLastError());
        record.setPrintTime(
                task.getSendingStartedAt() == null
                        ? task.getCreatedAt()
                        : task.getSendingStartedAt());
        return record;
    }

    /**
     * 终态写入与状态 CAS 共用一个短事务，绝不覆盖原任务或旧日志。
     */
    private void appendAudit(MesLabelPrintTask task) {
        MesLabelPrintLog record = record(task);
        new NamedParameterJdbcTemplate(jdbc)
                .update(
                        "INSERT INTO"
                            + " mes_label_print_log(log_id,tenant_id,sn_id,sn_code,template_id,template_code,template_revision,template_snapshot,field_snapshot,content_hash,payload_hash,rendered_payload,payload_charset,printer_host,printer_port,print_time,result,fail_reason,retry_count,print_source,original_print_log_id,reprint_reason,snapshot_mode,operator_id,operator_name,remark)"
                            + " VALUES(:logId,:tenantId,:snId,:snCode,:templateId,:templateCode,:templateRevision,:templateSnapshot,:fieldSnapshot,:contentHash,:payloadHash,:renderedPayload,:payloadCharset,:printerHost,:printerPort,:printTime,:result,:failReason,:retryCount,:printSource,:originalPrintLogId,:reprintReason,:snapshotMode,:operatorId,:operatorName,:remark)",
                        new BeanPropertySqlParameterSource(record));
    }

    /**
     * 过期 CAS 不触及冻结内容；UNKNOWN 不能被任何自动路径再次发送。
     */
    private void expire(Long taskId, Timestamp now) {
        if (jdbc.update(
                        "UPDATE mes_label_print_task SET"
                            + " status='UNKNOWN',last_error=?,completed_at=?,updated_at=? WHERE"
                            + " tenant_id=? AND task_id=? AND status='SENDING' AND lease_until<=?",
                        "发送租约到期，是否已到达打印机未知；核实后仅允许显式关联原任务补打",
                        now,
                        now,
                        tenant(),
                        taskId,
                        now)
                == 1) {
            appendAudit(rawFind(taskId));
        }
    }

    private MesLabelPrintTask rawFind(Long id) {
        var rows =
                jdbc.query(
                        "SELECT * FROM mes_label_print_task WHERE tenant_id=? AND task_id=?",
                        TASK,
                        tenant(),
                        id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MesLabelPrintTask byKey(String tenant, String key, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT * FROM mes_label_print_task WHERE tenant_id=? AND task_key=?"
                                + (lock ? " FOR UPDATE" : ""),
                        TASK,
                        tenant,
                        key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MesLabelPrintTask sameIntent(
            MesLabelPrintTask existing, String intent, boolean automatic) {
        if (!automatic && !intent.equals(existing.getRequestFingerprint())) {
            throw new ServiceException("同一打印请求ID不能改变原任务、原因或打印意图");
        }
        return existing;
    }

    private String tenant() {
        String tenant = TenantHelper.getTenantId();
        if (!TenantHelper.isEnable() || StringUtils.isBlank(tenant) || tenant.length() > 20) {
            throw new ServiceException("打印任务需要可信租户上下文");
        }
        return tenant;
    }

    private void requireMesTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !"mes".equals(DynamicDataSourceContextHolder.peek())) {
            throw new ServiceException("打印任务必须写入实际可写MES事务");
        }
        connectionGuard.requireMesConnection();
    }

    private <T> T required(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            requireMesTransaction();
            return work.get();
        }
        return shortTransaction(work);
    }

    private <T> T shortTransaction(Supplier<T> work) {
        DynamicDataSourceContextHolder.push("mes");
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setTimeout(5);
            return transaction.execute(
                    status -> {
                        requireMesTransaction();
                        return work.get();
                    });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private <T> T read(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            requireMesTransaction();
            return work.get();
        }
        DynamicDataSourceContextHolder.push("mes");
        try {
            return work.get();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private Timestamp now() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
    }

    private String shortError(String error) {
        return error == null ? null : error.substring(0, Math.min(500, error.length()));
    }

    private static String digest(Object... values) {
        return digestBytes(
                java.util.Arrays.stream(values)
                        .map(value -> value == null ? "" : value.toString())
                        .collect(java.util.stream.Collectors.joining("\u0000")));
    }

    private static String digestBytes(String value) {
        return LabelPrintSnapshot.hash(value.getBytes(StandardCharsets.UTF_8));
    }
}
