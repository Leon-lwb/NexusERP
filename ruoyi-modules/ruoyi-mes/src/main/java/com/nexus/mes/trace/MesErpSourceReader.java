package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

/**
 * 预留正式身份或开启本地制造写事务前，先读取真实委外凭据。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class MesErpSourceReader {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final MaterialPolicyPort policies;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MaterialTraceEvent subcontract(
            Long sourceId, Long materialId, String sourceNo, int quantity) {
        String tenant = context.tenant();
        var source =
                one(
                        jdbc,
                        "SELECT * FROM prd_subcontract WHERE tenant_id=? AND subcontract_id=? AND"
                                + " del_flag='0'",
                        tenant,
                        sourceId);
        need(
                source != null
                        && Objects.equals(materialId, id(source.get("material_id")))
                        && Objects.equals(sourceNo, text(source.get("subcontract_no")))
                        && Set.of("RELEASED", "IN_PROGRESS").contains(text(source.get("status")))
                        && qty(source.get("planned_qty"))
                                        .compareTo(java.math.BigDecimal.valueOf(quantity))
                                >= 0,
                "发号必须有匹配且已下达的真实委外来源");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("subcontractId", sourceId);
        payload.put("subcontractNo", sourceNo);
        payload.put("materialId", materialId);
        payload.put("materialCode", source.get("material_code"));
        payload.put("materialName", source.get("material_name"));
        payload.put("quantity", qty(source.get("planned_qty")));
        try {
            String body = json.writeValueAsString(payload);
            return new MaterialTraceEvent(
                    TraceHashes.fingerprint(tenant, "SUBCONTRACT_SOURCE", sourceId),
                    tenant,
                    "ERP",
                    "ERP_SUBCONTRACT_SOURCE_CONFIRMED",
                    "SUBCONTRACT_SOURCE",
                    sourceId,
                    1,
                    1,
                    body,
                    TraceHashes.sha256(body));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("委外来源快照无法冻结");
        }
    }

    /**
     * 已完成来源实证的跨模块事件及业务快照。
     *
     * @param eventId 跨模块事件的稳定标识。
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     * @param policy 来源业务已冻结的追踪策略。
     * @param sourceVersion 原来源事实版本。
     */
    public record VerifiedEvent(
            String eventId,
            String tenantId,
            String contentHash,
            TrackingPolicy policy,
            long sourceVersion) {}

    /**
     * 业务来源证明在目标事务前读取，传输来源校验由事件路由层负责。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public VerifiedEvent verifyEvent(MaterialTraceEvent event) {
        context.requireTenant(event.tenantId());
        need("ERP".equals(event.sourceModule()), "来源模块不是ERP");
        var original =
                one(
                        jdbc,
                        "SELECT * FROM wm_trace_outbox WHERE tenant_id=? AND event_id=?",
                        event.tenantId(),
                        event.eventId());
        need(
                original != null
                        && "ERP".equals(original.get("source_module"))
                        && event.eventType().equals(original.get("event_type"))
                        && event.aggregateType().equals(original.get("aggregate_type"))
                        && Objects.equals(event.aggregateId(), id(original.get("aggregate_id")))
                        && event.aggregateVersion() == id(original.get("aggregate_version"))
                        && event.contentHash().equals(text(original.get("content_hash")))
                        && event.payload().equals(text(original.get("payload")))
                        && Set.of("NEW", "BROKER_SENT", "APPLIED", "CONFLICT")
                                .contains(text(original.get("status"))),
                "缺少匹配的ERP原始事务发件凭据，不能人工补造MES授权");
        com.fasterxml.jackson.databind.JsonNode payload;
        try {
            payload = json.readTree(event.payload());
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("ERP来源载荷无效");
        }
        TrackingPolicy policy = null;
        long version = 0;
        if (Set.of(
                        "ERP_MATERIAL_SOURCE_POSTED",
                        "ERP_PRODUCTION_LOT_AUTHORIZED",
                        "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED")
                .contains(event.eventType())) {
            Long material = MesTraceStore.number(payload, "materialId");
            need(positive(material), "来源缺少物料");
            policy = policies.policy(event.tenantId(), material);
            need(policy != null, "来源缺少可信物料策略");
            validateTracking(
                    payload, policy, !"ERP_PRODUCTION_LOT_AUTHORIZED".equals(event.eventType()));
        }
        if (Set.of("ERP_MATERIAL_SOURCE_POSTED", "ERP_SHIPMENT_POSTED")
                .contains(event.eventType())) {
            version = verifyPosted(event, payload);
        }
        if (Set.of("ERP_PRODUCTION_LOT_AUTHORIZED", "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED")
                .contains(event.eventType())) {
            version = verifyAuthorization(event, payload);
        }
        return new VerifiedEvent(
                event.eventId(), event.tenantId(), event.contentHash(), policy, version);
    }

    private void validateTracking(
            com.fasterxml.jackson.databind.JsonNode payload,
            TrackingPolicy policy,
            boolean component) {
        Long batch = MesTraceStore.number(payload, "erpBatchId"),
                identity = MesTraceStore.number(payload, "identityId");
        need(batch == null || positive(batch), "ERP批次身份无效");
        need(!policy.batchManaged() || positive(batch), "批次管理物料必须有真实ERP批次");
        if (component && (policy.serialManaged() || identity != null || batch == null)) {
            need(
                    positive(identity)
                            && positive(MesTraceStore.number(payload, "sourceStockId"))
                            && BigDecimal.ONE.compareTo(
                                            MesMaterialTraceWriter.decimal(payload, "quantity"))
                                    == 0,
                    "单件组件来源必须包含真实身份、库存和整件数量1");
        }
    }

    private long verifyPosted(
            MaterialTraceEvent event, com.fasterxml.jackson.databind.JsonNode payload) {
        Long group = MesTraceStore.number(payload, "postingGroupId");
        need(positive(group), "ERP来源缺少过账组");
        var posting =
                one(
                        jdbc,
                        "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                + " posting_group_id=? AND status='POSTED'",
                        event.tenantId(),
                        group);
        need(
                posting != null && positive(id(posting.get("detail_version"))),
                "ERP来源过账尚未真实完成或缺少事实版本");
        if ("ERP_MATERIAL_SOURCE_POSTED".equals(event.eventType())) {
            need(
                    "WM_PRODUCTION_ISSUE".equals(posting.get("source_doc_type"))
                            && Objects.equals(
                                    id(posting.get("material_id")),
                                    MesTraceStore.number(payload, "materialId")),
                    "ERP组件来源不是真实生产领料");
            Long batch = MesTraceStore.number(payload, "erpBatchId"),
                    identity = MesTraceStore.number(payload, "identityId");
            String sql =
                    "SELECT COALESCE(SUM(tx_quantity),0) FROM wm_material_stock_tx WHERE"
                        + " tenant_id=? AND posting_group_id=? AND (batch_id=? OR (batch_id IS NULL"
                        + " AND ? IS NULL)) AND material_id=? AND tx_flag=-1";
            var args =
                    new ArrayList<Object>(
                            Arrays.asList(
                                    event.tenantId(),
                                    group,
                                    batch,
                                    batch,
                                    MesTraceStore.number(payload, "materialId")));
            if (identity != null) {
                sql += " AND sn_id=? AND material_stock_id=?";
                args.add(identity);
                args.add(MesTraceStore.number(payload, "sourceStockId"));
            }
            BigDecimal quantity = jdbc.queryForObject(sql, BigDecimal.class, args.toArray());
            need(
                    quantity != null
                            && quantity.compareTo(
                                            MesMaterialTraceWriter.decimal(payload, "quantity"))
                                    == 0,
                    "ERP组件来源身份、库存、批次与实际过账数量不一致");
        } else {
            need(
                    "WM_SALES_ISSUE".equals(posting.get("source_doc_type"))
                            && Objects.equals(
                                    text(posting.get("source_doc_type")),
                                    payload.path("sourceDocType").asText())
                            && Objects.equals(
                                    id(posting.get("source_doc_id")),
                                    MesTraceStore.number(payload, "sourceDocId")),
                    "MES发货投影必须引用真实销售出库");
            Set<Long> posted = new HashSet<>();
            for (var row :
                    jdbc.queryForList(
                            "SELECT identity_id FROM wm_document_sn WHERE tenant_id=? AND"
                                    + " posting_group_id=?",
                            event.tenantId(),
                            group)) {
                posted.add(id(row.get("identity_id")));
            }
            need(payload.path("identityIds").isArray(), "发货缺少身份集合");
            for (var value : payload.path("identityIds")) {
                need(posted.contains(MesTraceStore.numberValue(value)), "发货身份不属于真实ERP过账组");
            }
        }
        return id(posting.get("detail_version"));
    }

    private long verifyAuthorization(
            MaterialTraceEvent event, com.fasterxml.jackson.databind.JsonNode payload) {
        Long woId = MesTraceStore.number(payload, "erpWoId");
        need(positive(woId), "授权缺少真实ERP工单");
        var wo =
                one(
                        jdbc,
                        "SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND"
                                + " del_flag='0'",
                        event.tenantId(),
                        woId);
        need(
                wo != null
                        && Set.of(
                                        "RELEASED",
                                        "IN_PROGRESS",
                                        "PARTIAL_COMPLETED",
                                        "COMPLETED",
                                        "CLOSED")
                                .contains(text(wo.get("wo_status"))),
                "授权ERP工单不存在或已取消");
        Long batch = MesTraceStore.number(payload, "erpBatchId"),
                material = MesTraceStore.number(payload, "materialId");
        need(
                positive(material)
                        && (batch == null
                                || one(
                                                jdbc,
                                                "SELECT batch_id FROM wm_batch WHERE tenant_id=?"
                                                        + " AND batch_id=? AND material_id=? AND"
                                                        + " del_flag='0'",
                                                event.tenantId(),
                                                batch,
                                                material)
                                        != null),
                "授权缺少真实匹配的ERP批次");
        if ("ERP_PRODUCTION_LOT_AUTHORIZED".equals(event.eventType())) {
            need(
                    Objects.equals(material, id(wo.get("material_id")))
                            && Objects.equals(woId, MesTraceStore.number(payload, "sourceId"))
                            && MesMaterialTraceWriter.decimal(payload, "quantity")
                                            .compareTo(qty(wo.get("planned_qty")))
                                    <= 0,
                    "生产批次授权与原工单不一致");
            return 1; // 本次授权形成新的不可变来源事件，不复用组件过账的聚合版本。
        }
        var line =
                one(
                        jdbc,
                        "SELECT l.*,h.material_id,h.erp_wo_id,h.mes_wo_id,h.status FROM"
                                + " wm_trace_material_reservation_line l JOIN"
                                + " wm_trace_material_reservation h ON CAST(h.tenant_id AS"
                                + " BINARY(80))=CAST(l.tenant_id AS BINARY(80)) AND"
                                + " h.reservation_id=l.reservation_id WHERE l.tenant_id=? AND"
                                + " l.reservation_line_id=? AND l.reservation_id=?",
                        event.tenantId(),
                        MesTraceStore.number(payload, "sourceLineId"),
                        MesTraceStore.number(payload, "sourceId"));
        need(
                line != null
                        && Objects.equals(material, id(line.get("material_id")))
                        && Objects.equals(woId, id(line.get("erp_wo_id")))
                        && Objects.equals(
                                MesTraceStore.number(payload, "mesWoId"), id(line.get("mes_wo_id")))
                        && Objects.equals(batch, id(line.get("erp_batch_id")))
                        && Objects.equals(
                                MesTraceStore.number(payload, "identityId"),
                                id(line.get("identity_id")))
                        && (MesTraceStore.number(payload, "sourceStockId") == null
                                || Objects.equals(
                                        MesTraceStore.number(payload, "sourceStockId"),
                                        id(line.get("source_stock_id"))))
                        && qty(line.get("quantity"))
                                        .compareTo(
                                                MesMaterialTraceWriter.decimal(payload, "quantity"))
                                == 0
                        && positive(id(line.get("version")))
                        && Set.of("RESERVED", "PARTIAL", "CONSUMED", "COMPLETED")
                                .contains(text(line.get("status"))),
                "倒冲授权不匹配真实库存预留行");
        return id(line.get("version"));
    }
}
