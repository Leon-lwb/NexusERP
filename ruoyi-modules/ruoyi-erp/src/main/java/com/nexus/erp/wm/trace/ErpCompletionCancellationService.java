package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.JdbcTraceJournal;
import com.nexus.common.trace.port.CompletionCancellationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.sql.Clob;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 与正式完工过账竞争同一工单锁；取消仅冻结终态，不伪造入库或冲销效果。
 */
@Service
@DS("erp")
public class ErpCompletionCancellationService implements CompletionCancellationPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ErpTraceContext context;

    public ErpCompletionCancellationService(
            JdbcTemplate jdbc, ObjectMapper json, ErpTraceContext context) {
        this.jdbc = jdbc;
        this.json = json;
        this.context = context;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CompletionCancellationResult cancel(CompletionCancellationProof proof) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("完工取消需要实际 ERP 写事务");
        }
        if (proof == null) {
            throw new IllegalArgumentException("完工取消需要已验证的来源凭据");
        }
        context.requireWrite(proof.cancellationEvent().tenantId());
        var journal = new JdbcTraceJournal(jdbc, json, "ERP");
        var result = journal.preemptCompletion(proof, state -> decide(proof, state));
        String payload = encode(result),
                id =
                        "CCR_"
                                + TraceHashes.sha256(proof.cancellationEvent().eventId())
                                        .substring(0, 48);
        journal.append(
                new MaterialTraceEvent(
                        id,
                        proof.cancellationEvent().tenantId(),
                        "ERP",
                        CompletionCancellationResult.EVENT_TYPE,
                        CompletionCancellationResult.AGGREGATE_TYPE,
                        proof.request().manifestId(),
                        proof.cancellationEvent().aggregateVersion(),
                        1,
                        payload,
                        TraceHashes.sha256(payload)));
        return result;
    }

    private CompletionCancellationResult decide(
            CompletionCancellationProof proof, TraceDeliveryResult state) {
        String tenant = proof.cancellationEvent().tenantId();
        // 与 WmProductionRecptWriteGuard.lockWorkOrder 使用相同的真实父工单行锁。
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        proof.erpWoId());
        if (rows.size() != 1
                || !tenant.equals(text(rows.get(0).get("tenant_id")))
                || !Objects.equals(proof.materialId(), id(rows.get(0).get("material_id")))) {
            throw new IllegalStateException("取消来源与实际 ERP 工单物料不一致或工单已删除");
        }
        var wo = rows.get(0);
        boolean cancelled = false;
        if (state.applied()) {
            JsonNode applied = parse(state.resultPayload());
            require(
                    Objects.equals(
                                    proof.originalEvent().eventId(),
                                    applied.path("sourceEventId").asText())
                            && Objects.equals(
                                    proof.request().manifestId(), nodeId(applied, "manifestId"))
                            && proof.request().revision() == nodeId(applied, "revision")
                            && proof.request()
                                    .manifestHash()
                                    .equals(applied.path("manifestHash").asText()),
                    "原完工应用结果与取消清单不一致");
            String outcome = applied.path("outcome").asText();
            if ("CANCELLED".equals(outcome)) {
                CompletionCancellationResult previous;
                try {
                    previous = json.treeToValue(applied, CompletionCancellationResult.class);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("原取消终态无效", e);
                }
                require(
                        proof.originalEvent().eventId().equals(previous.originalEventId())
                                && proof.originalEvent()
                                        .contentHash()
                                        .equals(previous.originalEventHash()),
                        "原取消终态来源不一致");
                cancelled = true;
            } else {
                // 使用既有收货契约校验 POSTED/NO_GOOD_OUTPUT；无良品不产生伪收货号。
                new CompletionReceipt(
                        optionalId(applied, "receiptId"),
                        optionalId(applied, "postingGroupId"),
                        proof.request().manifestId(),
                        proof.request().revision(),
                        proof.request().manifestHash(),
                        outcome);
                return result(proof, "COMPENSATION_REQUIRED", "ORIGINAL_ALREADY_APPLIED");
            }
        }
        String effect = existingEffect(proof, wo);
        if (effect != null) {
            return result(proof, "COMPENSATION_REQUIRED", effect);
        }
        return result(
                proof,
                "CANCELLED",
                cancelled ? "ORIGINAL_ALREADY_CANCELLED" : "CANCELLED_BEFORE_APPLICATION");
    }

    private String existingEffect(CompletionCancellationProof proof, Map<String, Object> wo) {
        String tenant = proof.cancellationEvent().tenantId();
        // 当前读避免外层 inbox 版本检查建立的 MySQL RR 快照隐藏刚提交的入库。
        var receipts =
                jdbc.queryForList(
                        "SELECT * FROM wm_production_recpt WHERE tenant_id=? AND wo_id=? FOR"
                                + " UPDATE",
                        tenant,
                        proof.erpWoId());
        if (receipts.stream().anyMatch(r -> !"DRAFT".equals(text(r.get("status"))))) {
            return "EXISTING_PRODUCTION_RECEIPT";
        }
        if (!jdbc.queryForList(
                        "SELECT tx_id FROM wm_material_stock_tx WHERE tenant_id=? AND"
                            + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? FOR UPDATE",
                        tenant,
                        proof.erpWoId())
                .isEmpty()) {
            return "EXISTING_INVENTORY_EFFECT";
        }
        if (!jdbc.queryForList(
                        "SELECT posting_group_id FROM wm_trace_posting_group WHERE tenant_id=? AND"
                            + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? FOR UPDATE",
                        tenant,
                        proof.erpWoId())
                .isEmpty()) {
            return "EXISTING_POSTING_GROUP";
        }
        if (!jdbc.queryForList(
                        "SELECT consumption_id FROM wm_trace_material_consumption WHERE tenant_id=?"
                                + " AND manifest_id=? AND manifest_revision=? FOR UPDATE",
                        tenant,
                        proof.request().manifestId(),
                        proof.request().revision())
                .isEmpty()) {
            return "EXISTING_COMPONENT_CONSUMPTION";
        }
        var noGood =
                jdbc.queryForList(
                        "SELECT * FROM wm_trace_outbox WHERE tenant_id=? AND event_id=? FOR UPDATE",
                        tenant,
                        "ERP-NO-GOOD-"
                                + proof.request().manifestId()
                                + "-"
                                + proof.request().revision());
        if (!noGood.isEmpty()) {
            require(noGood.size() == 1, "零良品完成证明不唯一");
            var row = noGood.get(0);
            String payload = text(row.get("payload"));
            var evidence = parse(payload);
            require(
                    "ERP".equals(text(row.get("source_module")))
                            && "ERP_INVENTORY_POSTED".equals(text(row.get("event_type")))
                            && "COMPLETION_RESULT".equals(text(row.get("aggregate_type")))
                            && Objects.equals(
                                    proof.request().manifestId(), id(row.get("aggregate_id")))
                            && TraceHashes.sha256(payload).equals(text(row.get("content_hash")))
                            && "NO_GOOD_OUTPUT".equals(evidence.path("outcome").asText())
                            && Objects.equals(
                                    proof.request().manifestId(), nodeId(evidence, "manifestId"))
                            && proof.request().revision() == nodeId(evidence, "revision")
                            && proof.request()
                                    .manifestHash()
                                    .equals(evidence.path("manifestHash").asText())
                            && Objects.equals(proof.erpWoId(), nodeId(evidence, "erpWoId"))
                            && Objects.equals(proof.mesWoId(), nodeId(evidence, "mesWoId"))
                            && Objects.equals(proof.materialId(), nodeId(evidence, "materialId")),
                    "零良品原完成证明与取消来源不一致");
            return "EXISTING_NO_GOOD_OUTPUT";
        }
        String status = text(wo.get("wo_status"));
        if (status == null
                || Set.of("COMPLETED", "CLOSED", "CANCELLED").contains(status)
                || !zero(wo.get("completed_qty"))
                || !zero(wo.get("scrap_qty"))) {
            return "WORK_ORDER_ALREADY_CONSUMED";
        }
        return null;
    }

    private CompletionCancellationResult result(
            CompletionCancellationProof proof, String outcome, String reason) {
        var event = proof.cancellationEvent();
        var command = proof.request();
        var original = proof.originalEvent();
        return new CompletionCancellationResult(
                event.eventId(),
                event.eventId(),
                original.eventId(),
                original.contentHash(),
                command.manifestId(),
                command.revision(),
                command.manifestHash(),
                outcome,
                reason);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("取消结果无法冻结", e);
        }
    }

    private JsonNode parse(String value) {
        try {
            JsonNode node = json.readTree(value);
            require(node != null && node.isObject(), "取消证明必须是对象");
            return node;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new IllegalStateException("取消证明载荷无效", e);
        }
    }

    private static Long optionalId(JsonNode node, String key) {
        return node.path(key).isMissingNode() || node.path(key).isNull() ? null : nodeId(node, key);
    }

    private static Long nodeId(JsonNode node, String key) {
        return id(node.path(key).asText());
    }

    private static Long id(Object value) {
        try {
            long number = new BigDecimal(Objects.toString(value, "")).longValueExact();
            require(number > 0, "取消证明身份无效");
            return number;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalStateException("取消证明身份无效", e);
        }
    }

    private static boolean zero(Object value) {
        return value != null && new BigDecimal(value.toString()).signum() == 0;
    }

    private static String text(Object value) {
        if (value instanceof Clob clob) {
            try {
                return clob.getSubString(1, Math.toIntExact(clob.length()));
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("取消证明不可读", e);
            }
        }
        return Objects.toString(value, null);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new IllegalStateException(message);
        }
    }
}
