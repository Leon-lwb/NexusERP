package com.nexus.erp.wm.trace;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.SerialIdentityPort;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 正式SN集中登记；独立ERP事务预留号码，MES失败或重试不回收号码。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpSerialIdentityService implements SerialIdentityPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ErpTraceContext context;
    private final PlatformTransactionManager transactionManager;
    private static final int MAX_TRANSACTION_ATTEMPTS = 3;
    private static final int MAX_CANDIDATE_WINDOWS = 32;

    /**
     * 无业务事务的编排边界；每次锁冲突都先完整回滚，再进入新的ERP事务。
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public SerialReservation reserve(String tenantId, SerialReservationCommand command) {
        need(
                !TransactionSynchronizationManager.isActualTransactionActive(),
                "发号必须在无外层业务事务和行锁的编排边界调用");
        context.requireTenant(tenantId);
        need(command != null, "发号请求不能为空");
        TransactionTemplate attempt = new TransactionTemplate(transactionManager);
        attempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int index = 1; index <= MAX_TRANSACTION_ATTEMPTS; index++) {
            try {
                return Objects.requireNonNull(
                        attempt.execute(
                                transaction -> reserveOnce(tenantId, command, transaction)));
            } catch (RuntimeException failure) {
                if (!retryableLockFailure(failure)) {
                    throw failure;
                }
                if (index == MAX_TRANSACTION_ATTEMPTS) {
                    throw new ServiceException("发号锁冲突重试已耗尽，请沿用原commandId稍后重试");
                }
                try {
                    Thread.sleep(10L * index + ThreadLocalRandom.current().nextLong(10));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("发号重试被中断，请沿用原commandId稍后重试");
                }
            }
        }
        throw new IllegalStateException("发号重试边界无效");
    }

    /**
     * PC及旧维护URL仅可注册独立ERP手工来源；业务/MES来源必须由其受控编排登记。
     */
    @Transactional(propagation = Propagation.NEVER)
    public SerialReservation reserveManual(String tenantId, SerialReservationCommand command) {
        StpUtil.checkPermission("wm:sn:add");
        need(
                command != null
                        && "ERP_MANUAL".equals(command.sourceType())
                        && command.sourceId() == null,
                "手工注册仅允许ERP_MANUAL且不绑定业务来源；MES或业务单据请使用对应注册编排");
        return reserve(tenantId, command);
    }

    /**
     * 未使用手工身份单向退休；身份、预留和不可变命令审计在同一真实ERP事务提交。
     */
    @Transactional(propagation = Propagation.NEVER)
    public ManualRetirement retireUnusedManual(
            String tenantId,
            Long identityId,
            String commandId,
            Long expectedVersion,
            String reason) {
        StpUtil.checkPermission("wm:sn:add");
        StpUtil.checkPermission("wm:sn:remove");
        Long actor = LoginHelper.getUserId();
        need(actor != null && actor > 0, "退休需要当前认证操作人");
        need(
                identityId != null
                        && identityId > 0
                        && expectedVersion != null
                        && expectedVersion > 0
                        && expectedVersion < Long.MAX_VALUE,
                "退休身份或版本无效");
        need(
                commandId != null && !commandId.isBlank() && commandId.length() <= 128,
                "退休必须携带有效commandId");
        need(reason != null && !reason.isBlank() && reason.length() <= 500, "退休原因不能为空或超过500位");
        need(!TransactionSynchronizationManager.isActualTransactionActive(), "退休必须在无外层业务事务的编排边界调用");
        context.requireTenant(tenantId);
        String fingerprint =
                TraceHashes.fingerprint(
                        "RETIRE_UNUSED_MANUAL_V1",
                        tenantId,
                        identityId,
                        actor,
                        expectedVersion,
                        reason);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return Objects.requireNonNull(
                    transaction.execute(
                            status ->
                                    retireManualOnce(
                                            tenantId,
                                            identityId,
                                            commandId,
                                            expectedVersion,
                                            reason,
                                            actor,
                                            fingerprint)));
        } catch (BadSqlGrammarException missingStructure) {
            throw new ServiceException("身份退休审计结构尚未就绪，请先应用对应ERP增量");
        } catch (DuplicateKeyException concurrentCommand) {
            throw new ServiceException("退休命令发生并发或内容冲突，请核对并沿用原commandId重试");
        }
    }

    private ManualRetirement retireManualOnce(
            String tenant,
            Long identityId,
            String command,
            Long expectedVersion,
            String reason,
            Long actor,
            String fingerprint) {
        context.requireWrite(tenant);
        var prior =
                one(
                        "SELECT * FROM wm_serial_lifecycle_command WHERE tenant_id=? AND"
                                + " command_id=?",
                        tenant,
                        command);
        if (prior != null) {
            return retirementReplay(prior, fingerprint, actor, identityId);
        }
        var hint =
                one(
                        "SELECT material_id,registration_command FROM wm_sn WHERE tenant_id=? AND"
                                + " sn_id=? AND del_flag='0'",
                        tenant,
                        identityId);
        need(hint != null && hint.get("registration_command") != null, "身份不存在或缺少原注册凭据，禁止推定退休来源");
        need(
                one(
                                "SELECT material_id FROM bd_material WHERE tenant_id=? AND"
                                        + " material_id=? AND del_flag='0' FOR UPDATE",
                                tenant,
                                hint.get("material_id"))
                        != null,
                "原注册物料不存在");
        var reservation =
                one(
                        "SELECT * FROM wm_serial_reservation WHERE tenant_id=? AND command_id=? FOR"
                                + " UPDATE",
                        tenant,
                        hint.get("registration_command"));
        need(
                reservation != null
                        && "ERP_MANUAL".equals(reservation.get("source_type"))
                        && reservation.get("source_id") == null,
                "仅允许退休ERP手工来源，MES或业务单据身份须走原业务流程");
        var units =
                jdbc.queryForList(
                        "SELECT * FROM wm_sn WHERE tenant_id=? AND registration_command=? ORDER BY"
                                + " sn_id FOR UPDATE",
                        tenant,
                        hint.get("registration_command"));
        var concurrent =
                one(
                        "SELECT * FROM wm_serial_lifecycle_command WHERE tenant_id=? AND"
                                + " command_id=? FOR UPDATE",
                        tenant,
                        command);
        if (concurrent != null) {
            return retirementReplay(concurrent, fingerprint, actor, identityId);
        }
        need(
                units.size() == number(reservation.get("quantity"))
                        && units.stream()
                                .allMatch(
                                        unit ->
                                                "0".equals(unit.get("del_flag"))
                                                        && Objects.equals(
                                                                number(unit.get("material_id")),
                                                                number(
                                                                        reservation.get(
                                                                                "material_id")))),
                "原注册身份记录不完整，禁止退休掩盖差异");
        var serial =
                units.stream()
                        .filter(unit -> identityId.equals(number(unit.get("sn_id"))))
                        .findFirst()
                        .orElse(null);
        need(
                serial != null
                        && "ERP_MANUAL".equals(serial.get("source_doc_type"))
                        && serial.get("source_doc_id") == null,
                "身份与手工来源凭据不一致");
        need(
                Set.of("RESERVED", "CONFIRMED").contains(text(reservation.get("status"))),
                "原预留已取消或存在冲突");
        need(
                "REGISTERED".equals(serial.get("status"))
                        && expectedVersion.equals(number(serial.get("identity_version"))),
                "身份已使用、已退休或版本已变化");
        need(
                serial.get("stock_id") == null
                        && serial.get("warehouse_id") == null
                        && serial.get("location_id") == null,
                "已绑定库存维度的身份不能直接退休");
        // 使用锁定当前读检查全部历史；零库存、逻辑删除流水也不能推定为从未使用。
        String[][] usage = {
            {"wm_material_stock", "sn_id"},
            {"wm_material_stock_tx", "sn_id"},
            {"wm_document_sn", "identity_id"},
            {"wm_trace_material_reservation_line", "identity_id"},
            {"wm_trace_material_consumption", "identity_id"}
        };
        for (String[] table : usage) {
            need(
                    jdbc.queryForList(
                                    "SELECT "
                                            + table[1]
                                            + " FROM "
                                            + table[0]
                                            + " WHERE tenant_id=? AND "
                                            + table[1]
                                            + "=? LIMIT 1 FOR UPDATE",
                                    tenant,
                                    identityId)
                            .isEmpty(),
                    "身份已有库存、流水、单据或组件使用证据，请通过原业务流程处理");
        }
        String before = encodeLifecycle(serial);
        long version = Math.addExact(expectedVersion, 1);
        need(
                jdbc.update(
                                "UPDATE wm_sn SET"
                                    + " status='RETIRED',identity_version=?,update_by=?,update_time=CURRENT_TIMESTAMP"
                                    + " WHERE tenant_id=? AND sn_id=? AND status='REGISTERED' AND"
                                    + " identity_version=? AND stock_id IS NULL AND del_flag='0'",
                                version,
                                actor,
                                tenant,
                                identityId,
                                expectedVersion)
                        == 1,
                "身份退休版本已变化");
        String reservationStatus = text(reservation.get("status"));
        boolean allRetired =
                units.stream()
                        .allMatch(
                                unit ->
                                        identityId.equals(number(unit.get("sn_id")))
                                                || "RETIRED".equals(unit.get("status")));
        if (allRetired) {
            need(
                    jdbc.update(
                                    "UPDATE wm_serial_reservation SET"
                                        + " status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE"
                                        + " tenant_id=? AND reservation_id=? AND status=?",
                                    tenant,
                                    reservation.get("reservation_id"),
                                    reservationStatus)
                            == 1,
                    "原预留状态已变化");
            reservationStatus = "CANCELLED";
        }
        var after = one("SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=?", tenant, identityId);
        long operation = IdWorker.getId();
        var result =
                new ManualRetirement(
                        command,
                        identityId,
                        number(reservation.get("reservation_id")),
                        reservationStatus,
                        "RETIRED",
                        version,
                        operation);
        jdbc.update(
                "INSERT INTO"
                    + " wm_serial_lifecycle_command(operation_id,tenant_id,command_id,fingerprint,action,identity_id,reservation_id,actor_id,reason,before_version,after_version,before_status,after_status,reservation_status,source_type,source_id,before_json,after_json,result_json,created_at)"
                    + " VALUES(?,?,?,?,'RETIRE_UNUSED_MANUAL',?,?,?,?,?,?,'REGISTERED','RETIRED',?,'ERP_MANUAL',NULL,?,?,?,CURRENT_TIMESTAMP)",
                operation,
                tenant,
                command,
                fingerprint,
                identityId,
                result.reservationId(),
                actor,
                reason,
                expectedVersion,
                version,
                reservationStatus,
                before,
                encodeLifecycle(after),
                encodeLifecycle(result));
        return result;
    }

    private ManualRetirement retirementReplay(
            Map<String, Object> row, String fingerprint, Long actor, Long identity) {
        need(
                "RETIRE_UNUSED_MANUAL".equals(row.get("action"))
                        && fingerprint.equals(row.get("fingerprint"))
                        && actor.equals(number(row.get("actor_id")))
                        && identity.equals(number(row.get("identity_id"))),
                "相同退休命令不能修改身份、操作人、原因或版本");
        try {
            Object body = row.get("result_json");
            String value =
                    body instanceof Clob clob
                            ? clob.getSubString(1, Math.toIntExact(clob.length()))
                            : text(body);
            var result = json.readValue(value, ManualRetirement.class);
            need(
                    result != null
                            && Objects.equals(result.commandId(), row.get("command_id"))
                            && identity.equals(result.identityId())
                            && Objects.equals(
                                    result.reservationId(), number(row.get("reservation_id")))
                            && Objects.equals(result.operationId(), number(row.get("operation_id")))
                            && result.operationId() != null
                            && result.operationId() > 0
                            && "RETIRED".equals(result.status())
                            && "RETIRED".equals(row.get("after_status"))
                            && Objects.equals(
                                    result.reservationStatus(), row.get("reservation_status"))
                            && result.version() == number(row.get("after_version"))
                            && "ERP_MANUAL".equals(row.get("source_type"))
                            && row.get("source_id") == null,
                    "原退休审计结果不一致");
            return result;
        } catch (Exception invalidAudit) {
            throw new ServiceException("原退休审计结果不完整，禁止重新执行");
        }
    }

    private String encodeLifecycle(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ServiceException("退休审计内容无法冻结");
        }
    }

    /**
     * 退休命令的稳定结果；重放返回原结果，版本、号码和身份不会重新生成。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param reservationId 正式身份预留单标识。
     * @param reservationStatus 正式身份预留的确认状态。
     * @param status 解析或业务处理的明确状态。
     * @param version 当前事实或规则版本。
     * @param operationId 原作业事实的稳定标识。
     */
    public record ManualRetirement(
            String commandId,
            Long identityId,
            Long reservationId,
            String reservationStatus,
            String status,
            long version,
            Long operationId) {}

    private boolean retryableLockFailure(RuntimeException failure) {
        if (failure instanceof PessimisticLockingFailureException) {
            return true;
        }
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == 1213
                            || sql.getErrorCode() == 1205
                            || "40001".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单次事务尝试不吞死锁；事务模板负责回滚后才允许下一次尝试。
     */
    private SerialReservation reserveOnce(
            String tenantId, SerialReservationCommand command, TransactionStatus transaction) {
        context.requireWrite(tenantId);
        try {
            var previous =
                    one(
                            "SELECT * FROM wm_serial_reservation WHERE tenant_id=? AND command_id=?"
                                    + " FOR UPDATE",
                            tenantId,
                            command.commandId());
            if (previous != null) {
                return existing(tenantId, command, previous);
            }
            var material =
                    one(
                            "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenantId,
                            command.materialId());
            need(material != null && number(material.get("status")) == 1L, "物料不存在或未启用");
            Map<String, Object> batch = null;
            if (command.batchId() != null) {
                batch =
                        one(
                                "SELECT batch_id,batch_code,material_id FROM wm_batch WHERE"
                                        + " tenant_id=? AND batch_id=? AND del_flag='0' FOR UPDATE",
                                tenantId,
                                command.batchId());
                need(batch != null, "关联批次不存在或不属于当前租户");
                need(command.materialId().equals(number(batch.get("material_id"))), "关联批次不属于该物料");
            }
            Rule rule = rule(tenantId, command.ruleId());
            LocalDate date = context.today();
            String cycle = rule.cycle(date);
            String snapshot;
            try {
                snapshot = json.writeValueAsString(rule);
            } catch (Exception e) {
                throw new ServiceException("发号规则无法冻结");
            }
            long reservationId = IdWorker.getId();
            jdbc.update(
                    "INSERT INTO"
                        + " wm_serial_reservation(reservation_id,tenant_id,command_id,fingerprint,material_id,quantity,rule_id,rule_version,rule_snapshot,source_type,source_id,cycle_key,biz_date,status,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'RESERVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
                        + " ON DUPLICATE KEY UPDATE command_id=command_id",
                    reservationId,
                    tenantId,
                    command.commandId(),
                    command.fingerprint(),
                    command.materialId(),
                    command.quantity(),
                    command.ruleId(),
                    rule.version(),
                    snapshot,
                    command.sourceType(),
                    command.sourceId(),
                    cycle,
                    Date.valueOf(date));
            var locked =
                    one(
                            "SELECT * FROM wm_serial_reservation WHERE tenant_id=? AND command_id=?"
                                    + " FOR UPDATE",
                            tenantId,
                            command.commandId());
            need(locked != null, "发号请求未成功登记");
            if (number(locked.get("reservation_id")) != reservationId) {
                return existing(tenantId, command, locked);
            }
            String sequenceKey =
                    TraceHashes.fingerprint(
                            "SERIAL_OUTPUT_V1",
                            rule.prefix(),
                            rule.dateFormat(),
                            rule.length(),
                            rule.resetCycle(),
                            rule.check());
            var priorSequence =
                    one(
                            "SELECT * FROM wm_serial_sequence WHERE tenant_id=? AND"
                                    + " namespace='SERIAL' AND rule_key=? AND cycle_key=?",
                            tenantId,
                            sequenceKey,
                            cycle);
            long initial =
                    priorSequence == null
                            ? historicalMaximum(tenantId, rule, date)
                            : number(priorSequence.get("current_value"));
            jdbc.update(
                    "INSERT INTO"
                        + " wm_serial_sequence(sequence_id,tenant_id,namespace,rule_key,cycle_key,current_value,version,created_at,updated_at)"
                        + " VALUES(?,?,'SERIAL',?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON"
                        + " DUPLICATE KEY UPDATE rule_key=rule_key",
                    IdWorker.getId(),
                    tenantId,
                    sequenceKey,
                    cycle,
                    initial);
            var sequence =
                    one(
                            "SELECT * FROM wm_serial_sequence WHERE tenant_id=? AND"
                                + " namespace='SERIAL' AND rule_key=? AND cycle_key=? FOR UPDATE",
                            tenantId,
                            sequenceKey,
                            cycle);
            need(sequence != null, "发号序列不存在");
            allocateWindow(tenantId, command, material, batch, rule, date, sequence, transaction);
            return existing(tenantId, command, locked);
        } catch (BadSqlGrammarException e) {
            throw new ServiceException("物料身份结构尚未就绪，请先应用对应ERP增量");
        }
    }

    /**
     * 同格式规则共享计数器；冲突整段留空，不覆盖或回收任何历史号码。
     */
    private void allocateWindow(
            String tenant,
            SerialReservationCommand command,
            Map<String, Object> material,
            Map<String, Object> batch,
            Rule rule,
            LocalDate date,
            Map<String, Object> sequence,
            TransactionStatus transaction) {
        long current = number(sequence.get("current_value"));
        for (int window = 0; window < MAX_CANDIDATE_WINDOWS; window++) {
            long end;
            try {
                end = Math.addExact(current, command.quantity());
            } catch (ArithmeticException exhausted) {
                throw new ServiceException("发号序列已耗尽");
            }
            need(current >= 0 && end > current, "发号序列无效");
            // 先校验整段尾号，不能写入半批后才发现流水位数或64位上限越界。
            rule.code(date, end);
            List<String> codes = new ArrayList<>(command.quantity());
            for (int offset = 1; offset <= command.quantity(); offset++) {
                codes.add(rule.code(date, current + offset));
            }
            advanceSequence(tenant, sequence, current, end);
            if (historicalCollision(tenant, codes)) {
                current = skipConflictingWindow(tenant, sequence, rule, date, end);
                continue;
            }
            Object savepoint = transaction.createSavepoint();
            try {
                for (String code : codes) {
                    jdbc.update(
                            "INSERT INTO"
                                + " wm_sn(sn_id,tenant_id,sn_code,material_id,material_code,status,source_doc_type,source_doc_id,registration_command,identity_version,rule_id,batch_id,batch_code,del_flag,create_time)"
                                + " VALUES(?,?,?,?,?,'REGISTERED',?,?,?,1,?,?,?,'0',CURRENT_TIMESTAMP)",
                            IdWorker.getId(),
                            tenant,
                            code,
                            command.materialId(),
                            material.get("material_code"),
                            command.sourceType(),
                            command.sourceId(),
                            command.commandId(),
                            command.ruleId(),
                            command.batchId(),
                            batch == null ? null : batch.get("batch_code"));
                }
                transaction.releaseSavepoint(savepoint);
                return;
            } catch (DuplicateKeyException collision) {
                // 唯一键是并发兜底；撤销本窗口新插入的半批，计数器的整段占用保留在保存点外。
                transaction.rollbackToSavepoint(savepoint);
                transaction.releaseSavepoint(savepoint);
                if (!historicalCollision(tenant, codes)) {
                    throw collision;
                }
                current = skipConflictingWindow(tenant, sequence, rule, date, end);
            }
        }
        throw new ServiceException("发号期间持续存在号码冲突，请沿用原commandId稍后重试");
    }

    private void advanceSequence(
            String tenant, Map<String, Object> sequence, long expected, long target) {
        need(
                jdbc.update(
                                "UPDATE wm_serial_sequence SET"
                                    + " current_value=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                                    + " WHERE sequence_id=? AND tenant_id=? AND current_value=?",
                                target,
                                sequence.get("sequence_id"),
                                tenant,
                                expected)
                        == 1,
                "发号序列并发变化");
    }

    /**
     * 检查完整历史，逻辑删除的号码同样永久占用；锁定读取防止快照漏掉已提交碰撞。
     */
    private boolean historicalCollision(String tenant, List<String> codes) {
        List<Object> parameters = new ArrayList<>(codes.size() + 1);
        parameters.add(tenant);
        parameters.addAll(codes);
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        return !jdbc.queryForList(
                        "SELECT sn_code FROM wm_sn WHERE tenant_id=? AND sn_code IN ("
                                + placeholders
                                + ") ORDER BY sn_code FOR UPDATE",
                        parameters.toArray())
                .isEmpty();
    }

    private long skipConflictingWindow(
            String tenant,
            Map<String, Object> sequence,
            Rule rule,
            LocalDate date,
            long burnedEnd) {
        // 正常已初始化请求不扫描历史；仅首次初始化或已证实冲突时读取当前历史最大值。
        long next = Math.max(burnedEnd, historicalMaximum(tenant, rule, date));
        if (next > burnedEnd) {
            advanceSequence(tenant, sequence, burnedEnd, next);
        }
        return next;
    }

    /**
     * 在所属库存事务中确认原预留的本地创建结果，命令与指纹必须保持一致。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void confirm(String tenantId, String commandId, String fingerprint) {
        confirmLocked(tenantId, null, commandId, fingerprint);
    }

    /**
     * 在库存接收事务中确认原预留，确认记录与接收结果一起提交。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param reservationId 正式身份预留单标识。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void confirmWithinInbox(
            String tenantId, Long reservationId, String commandId, String fingerprint) {
        need(reservationId != null && reservationId > 0, "发号确认缺少原预留身份");
        confirmLocked(tenantId, reservationId, commandId, fingerprint);
    }

    private void confirmLocked(
            String tenantId, Long reservationId, String commandId, String fingerprint) {
        context.requireWrite(tenantId);
        var row =
                one(
                        "SELECT * FROM wm_serial_reservation WHERE tenant_id=? AND command_id=? FOR"
                                + " UPDATE",
                        tenantId,
                        commandId);
        need(
                row != null
                        && Objects.equals(row.get("fingerprint"), fingerprint)
                        && (reservationId == null
                                || reservationId.equals(number(row.get("reservation_id")))),
                "发号确认与原请求不一致");
        need(Set.of("RESERVED", "CONFIRMED").contains(text(row.get("status"))), "发号请求状态不允许确认");
        jdbc.update(
                "UPDATE wm_serial_reservation SET status='CONFIRMED',updated_at=CURRENT_TIMESTAMP"
                        + " WHERE tenant_id=? AND command_id=? AND status='RESERVED'",
                tenantId,
                commandId);
    }

    /**
     * 按认证租户读取稳定正式身份，缺失结果不借用其它租户或模块的本地主键。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @return 本租户正式身份及其正式编码；没有记录时返回空。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public SerialIdentity find(String tenantId, Long identityId) {
        context.requireTenant(tenantId);
        var row =
                one(
                        "SELECT sn_id,sn_code,material_id,batch_id,batch_code FROM wm_sn WHERE"
                                + " tenant_id=? AND sn_id=? AND del_flag='0'",
                        tenantId,
                        identityId);
        return row == null ? null : identity(row);
    }

    /**
     * 外部号码是有上下文的别名，不改变正式身份；允许重码并由解析端显式返回歧义。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void addAlias(
            String tenantId,
            Long identityId,
            String commandId,
            String sourceType,
            Long sourceId,
            String code) {
        context.requireWrite(tenantId);
        need(commandId != null && !commandId.isBlank() && commandId.length() <= 128, "别名命令号不能为空");
        need(
                sourceType != null
                        && Set.of("SUPPLIER", "CUSTOMER", "LEGACY_ERP", "LEGACY_MES")
                                .contains(sourceType),
                "别名来源无效");
        need(code != null && !code.isBlank() && code.length() <= 128, "别名为空或超过128位");
        need(
                !Set.of("SUPPLIER", "CUSTOMER").contains(sourceType)
                        || sourceId != null && sourceId > 0,
                "外部别名必须有来源主体");
        var sn =
                one(
                        "SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenantId,
                        identityId);
        need(sn != null, "正式SN不存在");
        long source = sourceId == null ? 0L : sourceId;
        var prior =
                one(
                        "SELECT * FROM wm_sn_alias WHERE tenant_id=? AND command_id=? FOR UPDATE",
                        tenantId,
                        commandId);
        if (prior != null) {
            need(
                    number(prior.get("identity_id")).equals(identityId)
                            && sourceType.equals(prior.get("source_type"))
                            && number(prior.get("source_id")) == source
                            && code.equals(prior.get("alias_code")),
                    "同一别名命令不能修改内容");
            return;
        }
        jdbc.update(
                "INSERT INTO"
                    + " wm_sn_alias(alias_id,tenant_id,identity_id,source_type,source_id,material_id,alias_code,command_id,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                IdWorker.getId(),
                tenantId,
                identityId,
                sourceType,
                source,
                sn.get("material_id"),
                code,
                commandId);
    }

    private SerialReservation existing(
            String tenant, SerialReservationCommand command, Map<String, Object> row) {
        need(command.fingerprint().equals(row.get("fingerprint")), "相同发号命令不能修改物料、数量、规则、来源或批次");
        need(Set.of("RESERVED", "CONFIRMED").contains(text(row.get("status"))), "发号请求已取消或存在冲突");
        var registered =
                jdbc.queryForList(
                        "SELECT sn_id,sn_code,material_id,batch_id,batch_code,status FROM wm_sn"
                            + " WHERE tenant_id=? AND registration_command=? AND del_flag='0' ORDER"
                            + " BY sn_code,sn_id",
                        tenant,
                        command.commandId());
        need(
                registered.stream().noneMatch(identity -> "RETIRED".equals(identity.get("status"))),
                "原注册命令包含已退休身份，禁止重开或补号");
        var identities = registered.stream().map(this::identity).toList();
        need(identities.size() == command.quantity(), "原发号身份记录不完整，禁止补号掩盖差异");
        return new SerialReservation(
                number(row.get("reservation_id")),
                command.commandId(),
                command.fingerprint(),
                identities,
                text(row.get("status")));
    }

    private SerialIdentity identity(Map<String, Object> row) {
        return new SerialIdentity(
                number(row.get("sn_id")),
                text(row.get("sn_code")),
                number(row.get("material_id")),
                number(row.get("batch_id")),
                text(row.get("batch_code")));
    }

    private Rule rule(String tenant, Long ruleId) {
        if (ruleId == null) {
            return new Rule("SN", "yyyyMMdd", 6, "DAY", "NONE", 1L);
        }
        var row =
                one(
                        "SELECT * FROM bd_serial_number_rule WHERE tenant_id=? AND rule_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        ruleId);
        need(
                row != null
                        && "ACTIVE".equals(row.get("status"))
                        && "PRODUCT_SN".equals(row.get("rule_type")),
                "SN规则不存在、类型错误或未启用");
        return new Rule(
                text(row.get("prefix")),
                text(row.get("date_format")),
                ((Number) row.get("seq_length")).intValue(),
                text(row.get("seq_cycle")),
                text(row.get("check_digit_rule")),
                row.get("rule_version") == null ? 1L : number(row.get("rule_version")));
    }

    private long historicalMaximum(String tenant, Rule rule, LocalDate date) {
        String start = rule.prefix() + rule.date(date);
        long maximum = 0;
        for (var row :
                jdbc.queryForList(
                        "SELECT sn_code FROM wm_sn WHERE tenant_id=? AND sn_code LIKE ? FOR UPDATE",
                        tenant,
                        start + "%")) {
            String code = text(row.get("sn_code"));
            if (code == null || !code.startsWith(start)) {
                continue;
            }
            String suffix = code.substring(start.length());
            if (!"NONE".equals(rule.check())) {
                if (suffix.length() < 2) {
                    continue;
                }
                suffix = suffix.substring(0, suffix.length() - 1);
            }
            if (!suffix.matches("[0-9]+")) {
                continue;
            }
            try {
                maximum = Math.max(maximum, Long.parseLong(suffix));
            } catch (NumberFormatException e) {
                throw new ServiceException("历史SN序号超出范围，需先对账");
            }
        }
        return maximum;
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        need(rows.size() <= 1, "身份来源不唯一");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void need(boolean valid, String message) {
        if (!valid) {
            throw new ServiceException(message);
        }
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 保存进预留记录的不可变规则，重放只读取原身份，不重新发号。
     *
     * @param prefix 冻结发号规则的正式编码前缀。
     * @param dateFormat 正式编码的业务日期格式。
     * @param length 冻结规则规定的序号宽度。
     * @param resetCycle 发号序列周期，按冻结规则确定。
     * @param check 规则要求的校验位配置。
     * @param version 当前事实或规则版本。
     */
    public record Rule(
            String prefix,
            String dateFormat,
            int length,
            String resetCycle,
            String check,
            long version) {
        public Rule {
            prefix = prefix == null ? "" : prefix;
            dateFormat = dateFormat == null ? "" : dateFormat;
            resetCycle = resetCycle == null ? "NONE" : resetCycle;
            check = check == null ? "NONE" : check;
            need(
                    prefix.matches("[A-Z0-9_-]*")
                            && prefix.length() <= 32
                            && length >= 1
                            && length <= 18,
                    "SN规则前缀或流水长度无效");
            need(
                    Set.of("DAY", "MONTH", "YEAR", "NONE").contains(resetCycle)
                            && Set.of("NONE", "MOD10", "MOD11").contains(check),
                    "SN周期或校验位规则无效");
            need(
                    "NONE".equals(resetCycle)
                            || dateFormat.contains("y")
                                    && ("YEAR".equals(resetCycle) || dateFormat.contains("M"))
                                    && (!"DAY".equals(resetCycle) || dateFormat.contains("d")),
                    "周期重置必须在编码中包含对应日期粒度");
            try {
                if (!dateFormat.isEmpty()) {
                    LocalDate.of(2026, 10, 18).format(DateTimeFormatter.ofPattern(dateFormat));
                }
            } catch (IllegalArgumentException e) {
                throw new ServiceException("SN日期格式无效");
            }
        }

        String date(LocalDate date) {
            return dateFormat.isEmpty() ? "" : date.format(DateTimeFormatter.ofPattern(dateFormat));
        }

        String cycle(LocalDate date) {
            return switch (resetCycle) {
                case "DAY" -> date.toString();
                case "MONTH" -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
                case "YEAR" -> Integer.toString(date.getYear());
                default -> "NONE";
            };
        }

        String code(LocalDate date, long sequence) {
            need(
                    sequence > 0 && Long.toString(sequence).length() <= length,
                    "SN规则流水位数已耗尽，请使用新的输出规则");
            String base =
                    prefix + date(date) + String.format(Locale.ROOT, "%0" + length + "d", sequence);
            if ("NONE".equals(check)) {
                need(base.length() <= 64, "新SN超过64位，请调整规则");
                return base;
            }
            int sum = 0;
            for (int i = 0; i < base.length(); i++) {
                if (Character.isDigit(base.charAt(i))) {
                    sum += (base.charAt(i) - '0') * (i % 2 == 0 ? 3 : 1);
                }
            }
            int digit = "MOD10".equals(check) ? (10 - sum % 10) % 10 : (11 - sum % 11) % 11;
            String code = base + (digit == 10 ? "X" : Integer.toString(digit));
            need(code.length() <= 64, "新SN超过64位，请调整规则");
            return code;
        }
    }
}
