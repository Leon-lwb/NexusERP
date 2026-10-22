package com.nexus.mes.trace;

import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.*;
import com.nexus.mes.pro.domain.ProWorkOrder;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 最终交付清单不可变；单页仅是分页片段，整体核验必须读取全部分页。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesCompletionManifestService
        implements CompletionManifestPort, CompletionAllocationPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MesTraceContext context;
    private final MesTraceStore events;
    private final MaterialPolicyPort policies;
    private final MesProductLotOutputWriter productOutputs;
    private final SerialIdentityPort canonicalIdentities;
    private final MesPreparedWorkOrderPolicies preparedPolicies;

    @Value("${nexus.trace.relay.enabled:false}")
    private boolean traceRelayEnabled;

    /**
     * 工单状态抢占前确定唯一交付路径，追踪组件必须使用实际身份与用量。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean usesOutbox(ProWorkOrder wo) {
        context.requireWrite();
        need(
                wo != null && positive(wo.getMesWoId()) && positive(wo.getMaterialId()),
                "完工投递需要真实工单和产品物料");
        context.requireTenant(wo.getTenantId());
        if (traceRelayEnabled) {
            return true;
        }
        String tenant = context.tenant();
        var materials = new LinkedHashSet<Long>();
        materials.add(wo.getMaterialId());
        // 兼容检查只读取已有表；即使消耗记录被标记删除，其历史证据仍参与核对。
        var components =
                jdbc.queryForList(
                        """
                        SELECT child_material_id AS material_id FROM pro_work_order_bom_line
                          WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0'
                        UNION SELECT material_id FROM lt_wo_consumption WHERE tenant_id=? AND wo_id=?
                        UNION SELECT b.material_id FROM lt_sn_binding b JOIN pro_sn s
                          ON CAST(s.tenant_id AS BINARY(80))=CAST(b.tenant_id AS BINARY(80)) AND s.sn_id=b.sn_id
                          WHERE b.tenant_id=? AND s.mes_wo_id=? AND s.del_flag='0'
                        """,
                        tenant,
                        wo.getMesWoId(),
                        tenant,
                        wo.getMesWoId(),
                        tenant,
                        wo.getMesWoId());
        for (var component : components) {
            Long material = id(component.get("material_id"));
            need(positive(material), "实际组件或BOM缺少可信物料，禁止按普通数量回退");
            materials.add(material);
        }
        for (Long material : materials) {
            TrackingPolicy policy = policies.policy(tenant, material);
            need(policy != null, "缺少可信产品或组件追踪策略，禁止按普通数量回退");
            need(
                    !policy.serialManaged() && !policy.batchManaged(),
                    "产品或组件启用SN/批次追踪，最终完工需启用 nexus.trace.relay.enabled=true；禁止回退旧数量消息");
        }
        return false;
    }

    /**
     * 在工单父行事务内核对最终实际产出和用料，冻结一份不可变清单并同库追加交付事件。
     *
     * @return 指定租户与修订的不可变完工清单头。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CompletionManifest freeze(ProWorkOrder wo) {
        preparedPolicies.prepareWorkOrder(wo == null ? null : wo.getMesWoId());
        return freezeRevision(wo, null);
    }

    /**
     * 按当前实际生产事实重新冻结修订，原报工、汇总标记及已签档案保持不变。
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletionManifest refreeze(
            String commandId, Long woId, int cancelledRevision, String reason, Long actorId) {
        preparedPolicies.prepareWorkOrderForCommand(
                woId, "COMPLETION_MANIFEST_REFREEZE", commandId);
        context.requireWrite();
        need(
                positive(woId)
                        && cancelledRevision > 0
                        && positive(actorId)
                        && reason != null
                        && !reason.isBlank()
                        && reason.length() <= 500,
                "Revision requires its cancelled predecessor, reason and authenticated operator");
        var command =
                events.command(
                        commandId,
                        "COMPLETION_MANIFEST_REFREEZE",
                        Arrays.asList(woId, cancelledRevision, reason, actorId));
        if ("APPLIED".equals(command.get("status"))) {
            var result = events.result(command);
            return header(
                    frozenHeader(
                            context.tenant(),
                            MesTraceStore.number(result, "manifestId"),
                            result.path("revision").asInt()));
        }
        var row =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        context.tenant(),
                        woId);
        need(row != null, "Real work order does not exist");
        var wo = new ProWorkOrder();
        wo.setTenantId(context.tenant());
        wo.setMesWoId(woId);
        wo.setErpWoId(id(row.get("erp_wo_id")));
        wo.setMaterialId(id(row.get("material_id")));
        wo.setWoNo(text(row.get("wo_no")));
        wo.setCompletedQty(qty(row.get("completed_qty")));
        wo.setScrapQty(qty(row.get("scrap_qty")));
        wo.setWoStatus(text(row.get("wo_status")));
        var manifest = freezeRevision(wo, cancelledRevision);
        events.finish(
                command,
                Map.of("manifestId", manifest.manifestId(), "revision", manifest.revision()));
        return manifest;
    }

    private CompletionManifest freezeRevision(ProWorkOrder wo, Integer cancelledRevision) {
        context.requireWrite();
        String tenant = context.tenant();
        need(
                wo != null
                        && tenant.equals(wo.getTenantId())
                        && positive(wo.getMesWoId())
                        && positive(wo.getErpWoId()),
                "最终交付必须关联真实ERP/MES工单");
        var live =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        need(
                live != null
                        && "COMPLETED".equals(live.get("wo_status"))
                        && Objects.equals(wo.getMaterialId(), id(live.get("material_id")))
                        && Objects.equals(wo.getErpWoId(), id(live.get("erp_wo_id")))
                        && qty(live.get("completed_qty")).compareTo(wo.getCompletedQty()) == 0
                        && qty(live.get("scrap_qty")).compareTo(wo.getScrapQty()) == 0,
                "工单最终汇总与来源不一致");
        var prior =
                one(
                        jdbc,
                        "SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND mes_wo_id=?"
                                + " ORDER BY revision DESC LIMIT 1 FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        if (prior != null && cancelledRevision == null) {
            need(
                    !"CANCELLED".equals(prior.get("status")),
                    "Cancelled manifest requires an explicit verified revision command");
            need(
                    qty(prior.get("good_qty")).compareTo(wo.getCompletedQty()) == 0
                            && qty(prior.get("scrap_qty")).compareTo(wo.getScrapQty()) == 0,
                    "已冻结清单必须通过修订纠正，禁止覆盖");
            materialHeader(prior);
            return header(prior);
        }
        int revision = 1;
        String cancellationEventId = null;
        if (cancelledRevision != null) {
            need(
                    prior != null
                            && "CANCELLED".equals(prior.get("status"))
                            && id(prior.get("revision")) == cancelledRevision.longValue(),
                    "A new revision requires the latest predecessor to be cancelled");
            cancellationEventId =
                    requireCancelledForCorrection(
                            wo.getMesWoId(), id(prior.get("manifest_id")), cancelledRevision);
            revision = Math.addExact(cancelledRevision, 1);
        }
        if (cancelledRevision != null) {
            requireRevisionQuality(wo);
        }
        need(wo.getCompletedQty().signum() >= 0 && wo.getScrapQty().signum() >= 0, "最终工单数量无效");
        need(
                wo.getCompletedQty().add(wo.getScrapQty()).compareTo(qty(live.get("planned_qty")))
                                >= 0
                        && qty(live.get("planned_qty")).signum() > 0,
                "未达到工单最终完工数量，禁止生成部分入库清单");
        var cards =
                jdbc.queryForList(
                        "SELECT * FROM pro_flow_card WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' ORDER BY card_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        need(
                !cards.isEmpty()
                        && cards.stream()
                                .allMatch(
                                        c ->
                                                "COMPLETED".equals(c.get("card_status"))
                                                        && Long.valueOf(1)
                                                                .equals(
                                                                        id(
                                                                                c.get(
                                                                                        "aggregated_flag")))),
                "最终交付仍存在未完工或未聚合的流转卡");
        TrackingPolicy policy =
                preparedPolicies.enabled()
                        ? preparedPolicies.productPolicy(
                                wo.getMesWoId(), wo.getErpWoId(), wo.getMaterialId())
                        : policies.policy(tenant, wo.getMaterialId());
        need(policy != null, "缺少可信物料追溯策略");
        var sns =
                jdbc.queryForList(
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0'"
                                + " ORDER BY identity_id,sn_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        var lines = new ArrayList<CompletionManifestLine>();
        Set<Long> identities = new HashSet<>();
        long lineNo = 0;
        if (!sns.isEmpty() || policy.serialManaged()) {
            need(!sns.isEmpty(), "SN物料没有实际完工身份，禁止生成数量替代清单");
            for (var sn : sns) {
                need(
                        Objects.equals(wo.getMaterialId(), id(sn.get("material_id"))),
                        "工单SN集合存在物料冲突，禁止隐藏或截断错误来源");
                need(
                        positive(id(sn.get("identity_id")))
                                && identities.add(id(sn.get("identity_id")))
                                && Set.of("COMPLETED", "SCRAPPED")
                                        .contains(text(sn.get("sn_status"))),
                        "最终SN集合存在未完工、未映射或重复身份");
                need(
                        "NONE".equals(sn.get("handoff_status")),
                        "An identity still held by another delivery cannot be re-frozen");
                Long lot = id(sn.get("product_lot_id"));
                Long batch = productBatch(tenant, lot, wo.getMaterialId(), policy.batchManaged());
                var formal = canonicalIdentities.find(tenant, id(sn.get("identity_id")));
                need(
                        formal != null
                                && Objects.equals(formal.identityId(), id(sn.get("identity_id")))
                                && Objects.equals(formal.materialId(), wo.getMaterialId())
                                && formal.snCode() != null
                                && !formal.snCode().isBlank(),
                        "Final SN identity has no matching canonical ERP source");
                if (cancelledRevision != null && "COMPLETED".equals(sn.get("sn_status"))) {
                    var latestTest =
                            one(
                                    jdbc,
                                    "SELECT result FROM mes_test_record WHERE tenant_id=? AND"
                                        + " del_flag='0' AND (sn_id=? OR (sn_id IS NULL AND sn_code"
                                        + " IN (?,?))) ORDER BY test_time DESC,test_record_id DESC"
                                        + " LIMIT 1 FOR UPDATE",
                                    tenant,
                                    sn.get("sn_id"),
                                    sn.get("sn_code"),
                                    formal.snCode());
                    need(
                            latestTest == null || "PASS".equals(latestTest.get("result")),
                            "Revised good output requires the latest actual test to pass; complete"
                                    + " the existing NCR/rework flow first");
                }
                lines.add(
                        new CompletionManifestLine(
                                ++lineNo,
                                id(sn.get("identity_id")),
                                id(sn.get("sn_id")),
                                formal.snCode(),
                                batch,
                                lot,
                                wo.getMaterialId(),
                                BigDecimal.ONE,
                                "SCRAPPED".equals(sn.get("sn_status")) ? "SCRAP" : "GOOD",
                                id(sn.get("identity_version"))));
            }
        } else if (policy.batchManaged()) {
            lines.addAll(productOutputs.finalLines(wo));
        } else {
            if (wo.getCompletedQty().signum() > 0) {
                lines.add(
                        new CompletionManifestLine(
                                ++lineNo,
                                null,
                                null,
                                null,
                                null,
                                null,
                                wo.getMaterialId(),
                                wo.getCompletedQty(),
                                "GOOD",
                                1));
            }
            if (wo.getScrapQty().signum() > 0) {
                lines.add(
                        new CompletionManifestLine(
                                ++lineNo,
                                null,
                                null,
                                null,
                                null,
                                null,
                                wo.getMaterialId(),
                                wo.getScrapQty(),
                                "SCRAP",
                                1));
            }
        }
        BigDecimal good =
                lines.stream()
                        .filter(l -> "GOOD".equals(l.result()))
                        .map(CompletionManifestLine::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scrap =
                lines.stream()
                        .filter(l -> "SCRAP".equals(l.result()))
                        .map(CompletionManifestLine::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        need(
                good.compareTo(wo.getCompletedQty()) == 0 && scrap.compareTo(wo.getScrapQty()) == 0,
                "实际产出良品/报废集合与工单最终数量不守恒");
        var materials = freezeMaterials(wo, identities);
        String materialHash = CompletionAllocationIntegrity.hash(materials);
        Long manifestId = IdWorker.getId();
        String hash = hash(lines);
        jdbc.update(
                "INSERT INTO"
                    + " pro_completion_manifest(manifest_id,revision,tenant_id,mes_wo_id,erp_wo_id,material_id,line_count,good_qty,scrap_qty,content_hash,material_line_count,material_hash,status)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'FROZEN')",
                manifestId,
                revision,
                tenant,
                wo.getMesWoId(),
                wo.getErpWoId(),
                wo.getMaterialId(),
                lines.size(),
                good,
                scrap,
                hash,
                materials.size(),
                materialHash);
        for (var m : materials) {
            jdbc.update(
                    "INSERT INTO"
                        + " pro_completion_material_allocation(tenant_id,manifest_id,revision,allocation_id,material_id,reservation_id,reservation_line_id,posting_group_id,identity_id,source_stock_id,erp_batch_id,quantity,source_version,evidence_hash,source_event_id)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenant,
                    manifestId,
                    revision,
                    m.allocationId(),
                    m.materialId(),
                    m.reservationId(),
                    m.reservationLineId(),
                    m.postingGroupId(),
                    m.identityId(),
                    m.sourceStockId(),
                    m.erpBatchId(),
                    m.quantity(),
                    m.sourceVersion(),
                    m.evidenceHash(),
                    m.sourceEventId());
        }
        for (var l : lines) {
            jdbc.update(
                    "INSERT INTO"
                        + " pro_completion_manifest_line(tenant_id,manifest_id,revision,line_no,identity_id,local_sn_id,sn_code,batch_id,local_lot_id,material_id,quantity,result,source_version)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenant,
                    manifestId,
                    revision,
                    l.lineNo(),
                    l.identityId(),
                    l.localSnId(),
                    l.snCode(),
                    l.batchId(),
                    l.localLotId(),
                    l.materialId(),
                    l.quantity(),
                    l.result(),
                    l.sourceVersion());
        }
        jdbc.update(
                "UPDATE pro_sn SET handoff_status='FROZEN' WHERE tenant_id=? AND mes_wo_id=? AND"
                        + " del_flag='0'",
                tenant,
                wo.getMesWoId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manifestId", manifestId);
        payload.put("revision", revision);
        payload.put("manifestHash", hash);
        payload.put("lineCount", lines.size());
        payload.put("materialHash", materialHash);
        payload.put("materialLineCount", materials.size());
        payload.put("mesWoId", wo.getMesWoId());
        payload.put("erpWoId", wo.getErpWoId());
        payload.put("woNo", wo.getWoNo());
        payload.put("materialId", wo.getMaterialId());
        payload.put("completedQty", good);
        payload.put("scrapQty", scrap);
        if (prior != null) {
            payload.put("previousManifestId", prior.get("manifest_id"));
            payload.put("previousRevision", prior.get("revision"));
            payload.put("cancellationEventId", cancellationEventId);
        }
        events.append("COMPLETION_MANIFEST_FROZEN", "COMPLETION_MANIFEST", manifestId, 1, payload);
        return new CompletionManifest(
                manifestId,
                revision,
                tenant,
                wo.getMesWoId(),
                wo.getErpWoId(),
                wo.getMaterialId(),
                lines.size(),
                good,
                scrap,
                hash,
                "FROZEN");
    }

    /**
     * 复用现有不合格处置、派生返工及关键工序质量门禁证据，保留原有结论。
     */
    private void requireRevisionQuality(ProWorkOrder wo) {
        String tenant = context.tenant();
        var ncrs =
                jdbc.queryForList(
                        "SELECT * FROM ncr_order WHERE tenant_id=? AND del_flag='0' AND (wo_id=? OR"
                            + " card_id IN (SELECT card_id FROM pro_flow_card WHERE tenant_id=? AND"
                            + " mes_wo_id=? AND del_flag='0')) ORDER BY ncr_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId(),
                        tenant,
                        wo.getMesWoId());
        need(
                ncrs.stream().allMatch(n -> "CLOSED".equals(n.get("status"))),
                "Open NCR blocks a revised delivery; use the existing reviewed/signed disposition"
                        + " and actual rework workflow");
        var reworks =
                jdbc.queryForList(
                        "SELECT * FROM ncr_rework_order WHERE tenant_id=? AND original_wo_id=? AND"
                                + " del_flag='0' ORDER BY rework_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        for (var ncr : ncrs) {
            if ("REWORK".equals(ncr.get("disposition"))) {
                need(
                        reworks.stream()
                                .anyMatch(
                                        r ->
                                                Objects.equals(
                                                        id(r.get("ncr_id")),
                                                        id(ncr.get("ncr_id")))),
                        "Rework disposition is missing its real derived work order");
            }
        }
        for (var rework : reworks) {
            need(
                    "COMPLETED".equals(rework.get("status"))
                            && positive(id(rework.get("rework_wo_id"))),
                    "Actual derived rework has not completed");
            var child =
                    one(
                            jdbc,
                            "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant,
                            rework.get("rework_wo_id"));
            need(
                    child != null
                            && "COMPLETED".equals(child.get("wo_status"))
                            && Objects.equals(id(child.get("material_id")), wo.getMaterialId()),
                    "The real rework work order has not completed for this product");
        }
        var keys =
                jdbc.queryForList(
                        "SELECT process_id FROM pro_work_order_step WHERE tenant_id=? AND"
                            + " mes_wo_id=? AND is_key_process=1 AND del_flag='0' ORDER BY step_id"
                            + " FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        for (var step : keys) {
            var inspections =
                    jdbc.queryForList(
                            "SELECT inspect_result FROM qc_ipqc WHERE tenant_id=? AND mes_wo_id=?"
                                    + " AND process_id=? AND del_flag='0' FOR UPDATE",
                            tenant,
                            wo.getMesWoId(),
                            step.get("process_id"));
            need(
                    inspections.stream().anyMatch(i -> "QUALIFIED".equals(i.get("inspect_result"))),
                    "Actual key-process IPQC must be qualified before a revised final delivery");
        }
    }

    /**
     * 重新冻结与组件追加冲正共用同一取消屏障，避免修改已交付内容。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String requireCancelledForCorrection(Long woId, Long manifestId, int revision) {
        context.requireWrite();
        String tenant = context.tenant();
        need(
                positive(woId) && positive(manifestId) && revision > 0,
                "Exact cancelled work-order revision is required");
        var wo =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        woId);
        need(
                wo != null && "COMPLETED".equals(wo.get("wo_status")),
                "Correction requires the real completed parent work order");
        var prior =
                one(
                        jdbc,
                        "SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND mes_wo_id=?"
                                + " ORDER BY revision DESC LIMIT 1 FOR UPDATE",
                        tenant,
                        woId);
        need(
                prior != null
                        && Objects.equals(manifestId, id(prior.get("manifest_id")))
                        && id(prior.get("revision")) == revision
                        && "CANCELLED".equals(prior.get("status")),
                "Only the latest cancelled immutable manifest may be corrected");
        var source =
                one(
                        jdbc,
                        "SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND aggregate_type=? AND"
                            + " aggregate_id=? ORDER BY aggregate_version DESC LIMIT 1 FOR UPDATE",
                        tenant,
                        CompletionCancellationRequest.AGGREGATE_TYPE,
                        manifestId);
        need(
                source != null
                        && "APPLIED".equals(source.get("status"))
                        && source.get("result_payload") != null,
                "Correction requires the matching durable ERP cancellation acknowledgement");
        try {
            var request =
                    json.treeToValue(events.payload(source), CompletionCancellationRequest.class);
            var result =
                    json.treeToValue(events.result(source), CompletionCancellationResult.class);
            var original = request.originalEvent();
            need(
                    "CANCELLED".equals(result.outcome())
                            && Objects.equals(source.get("event_id"), result.sourceEventId())
                            && Objects.equals(source.get("event_id"), result.cancellationEventId())
                            && manifestId.equals(result.manifestId())
                            && revision == result.revision()
                            && Objects.equals(prior.get("content_hash"), result.manifestHash())
                            && manifestId.equals(request.manifestId())
                            && revision == request.revision()
                            && original.eventId().equals(result.originalEventId())
                            && original.contentHash().equals(result.originalEventHash())
                            && request.manifestHash().equals(result.manifestHash()),
                    "ERP cancellation does not release this exact source revision");
            var owner =
                    one(
                            jdbc,
                            "SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_id=? FOR"
                                    + " UPDATE",
                            tenant,
                            original.eventId());
            need(owner != null, "Original completion owner event is missing");
            var actual =
                    new MaterialTraceEvent(
                            text(owner.get("event_id")),
                            text(owner.get("tenant_id")),
                            text(owner.get("source_module")),
                            text(owner.get("event_type")),
                            text(owner.get("aggregate_type")),
                            id(owner.get("aggregate_id")),
                            id(owner.get("aggregate_version")),
                            Math.toIntExact(id(owner.get("schema_version"))),
                            text(owner.get("payload")),
                            text(owner.get("content_hash")));
            need(
                    original.equals(actual),
                    "Cancelled completion headers or payload differ from the actual owner outbox");
            var body = events.payload(owner);
            need(
                    Objects.equals(MesTraceStore.number(body, "mesWoId"), woId)
                            && Objects.equals(
                                    MesTraceStore.number(body, "erpWoId"), id(wo.get("erp_wo_id")))
                            && Objects.equals(
                                    MesTraceStore.number(body, "materialId"),
                                    id(wo.get("material_id")))
                            && Objects.equals(
                                    prior.get("material_hash"), body.path("materialHash").asText()),
                    "Cancelled completion belongs to a different work order or material snapshot");
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid durable cancellation evidence", error);
        }
        return text(source.get("event_id"));
    }

    /**
     * 冻结的实际组件消耗及原库存来源凭据。
     *
     * @param allocation 经原来源核实的批次或单件分配信息。
     * @param inbox 所属业务库的事件接收记录。
     * @param evidence 与该事实关联的原始来源凭据。
     * @param policy 来源业务已冻结的追踪策略。
     * @param version 当前事实或规则版本。
     * @param eventId 跨模块事件的稳定标识。
     */
    private record MaterialSource(
            Map<String, Object> allocation,
            Map<String, Object> inbox,
            JsonNode evidence,
            TrackingPolicy policy,
            long version,
            String eventId) {}

    /**
     * 工单父行已加锁，将实际消耗与对应的原库存证据一起冻结。
     */
    private List<CompletionMaterialAllocation> freezeMaterials(
            ProWorkOrder wo, Set<Long> productIdentities) {
        String tenant = context.tenant();
        var actual =
                jdbc.queryForList(
                        "SELECT * FROM lt_wo_consumption WHERE tenant_id=? AND wo_id=? ORDER BY"
                                + " consumption_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        var sources = new HashMap<Long, MaterialSource>();
        var rows = new ArrayList<CompletionMaterialAllocation>();
        var consumedByAllocation = new HashMap<Long, BigDecimal>();
        var actualById = new HashMap<Long, BigDecimal>();
        var serialConsumption = new HashSet<Long>();
        var componentIdentities = new HashSet<Long>();
        long previous = 0;
        for (var use : actual) {
            Long consumptionId = id(use.get("consumption_id")),
                    allocationId = id(use.get("source_allocation_id"));
            BigDecimal quantity = qty(use.get("consumed_qty"));
            need(
                    positive(consumptionId)
                            && consumptionId > previous
                            && "0".equals(use.get("del_flag"))
                            && positive(allocationId)
                            && quantity.signum() > 0,
                    "实际用料缺少原来源或已被删除，须审计纠正后再冻结");
            previous = consumptionId;
            MaterialSource source =
                    sources.computeIfAbsent(allocationId, key -> materialSource(wo, key));
            var allocation = source.allocation();
            var evidence = source.evidence();
            need(
                    Objects.equals(id(use.get("material_id")), id(allocation.get("material_id")))
                            && Objects.equals(id(use.get("lot_id")), id(allocation.get("lot_id")))
                            && positive(id(use.get("operator_id"))),
                    "实际消耗与来源物料、批次或操作人不一致");
            consumedByAllocation.merge(allocationId, quantity, BigDecimal::add);
            actualById.put(consumptionId, quantity);
            Long identity = MesTraceStore.number(evidence, "identityId"),
                    stock = MesTraceStore.number(evidence, "sourceStockId"),
                    batch = MesTraceStore.number(evidence, "erpBatchId");
            if (source.policy().serialManaged() || identity != null || batch == null) {
                need(
                        positive(identity)
                                && positive(stock)
                                && quantity.compareTo(BigDecimal.ONE) == 0
                                && componentIdentities.add(identity)
                                && MesMaterialTraceWriter.decimal(evidence, "quantity")
                                                .compareTo(BigDecimal.ONE)
                                        == 0,
                        "组件SN实际消耗必须为唯一整件1");
                serialConsumption.add(consumptionId);
            }
            boolean backflush =
                    "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED".equals(source.inbox().get("event_type"));
            Long reservation = backflush ? MesTraceStore.number(evidence, "sourceId") : null;
            Long reservationLine =
                    backflush ? MesTraceStore.number(evidence, "sourceLineId") : null;
            Long posting = backflush ? null : MesTraceStore.number(evidence, "postingGroupId");
            need(
                    backflush
                            ? positive(reservation) && positive(reservationLine)
                            : positive(posting),
                    "实际用料必须引用真实原预留行或已过账领料组");
            rows.add(
                    new CompletionMaterialAllocation(
                            consumptionId,
                            id(use.get("material_id")),
                            reservation,
                            reservationLine,
                            posting,
                            identity,
                            stock,
                            batch,
                            quantity,
                            source.version(),
                            text(allocation.get("evidence_hash")),
                            source.eventId()));
        }
        for (var entry : sources.entrySet()) {
            var allocation = entry.getValue().allocation();
            var allUse =
                    jdbc.queryForList(
                            "SELECT wo_id,consumed_qty,del_flag FROM lt_wo_consumption WHERE"
                                    + " tenant_id=? AND source_allocation_id=? FOR UPDATE",
                            tenant,
                            entry.getKey());
            need(
                    allUse.stream()
                            .allMatch(
                                    r ->
                                            "0".equals(r.get("del_flag"))
                                                    && Objects.equals(
                                                            id(r.get("wo_id")), wo.getMesWoId())),
                    "来源额度出现其他工单或删除的实际用料");
            BigDecimal total =
                    allUse.stream()
                            .map(r -> qty(r.get("consumed_qty")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            need(
                    total.compareTo(consumedByAllocation.get(entry.getKey())) == 0
                            && total.compareTo(qty(allocation.get("quantity"))) <= 0,
                    "实际消耗合计超过ERP来源子批次额度");
        }
        var entries =
                jdbc.queryForList(
                        "SELECT e.* FROM lt_sn_consumption e JOIN lt_wo_consumption c ON"
                            + " CAST(c.tenant_id AS BINARY(80))=CAST(e.tenant_id AS BINARY(80)) AND"
                            + " c.consumption_id=e.wo_consumption_id WHERE c.tenant_id=? AND"
                            + " c.wo_id=? ORDER BY e.entry_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        var assigned = new HashMap<Long, BigDecimal>();
        for (var entry : entries) {
            Long consumption = id(entry.get("wo_consumption_id"));
            BigDecimal quantity = qty(entry.get("quantity"));
            String type = text(entry.get("entry_type"));
            need(
                    actualById.containsKey(consumption)
                            && productIdentities.contains(id(entry.get("identity_id")))
                            && quantity.signum() > 0
                            && Set.of("CONSUME", "REVERSE").contains(type)
                            && positive(id(entry.get("process_id")))
                            && !text(entry.get("operation_ref")).isBlank(),
                    "逐件组件分录缺少实际工单身份或工序凭据");
            need(
                    !serialConsumption.contains(consumption)
                            || quantity.compareTo(BigDecimal.ONE) == 0,
                    "单件组件不能分数分配或分数冲正");
            assigned.merge(
                    consumption,
                    "CONSUME".equals(type) ? quantity : quantity.negate(),
                    BigDecimal::add);
        }
        for (var entry : assigned.entrySet()) {
            need(
                    entry.getValue().signum() >= 0
                            && entry.getValue().compareTo(actualById.get(entry.getKey())) <= 0,
                    "逐产品分配不守恒或超过实际消耗");
        }
        return List.copyOf(rows);
    }

    private MaterialSource materialSource(ProWorkOrder wo, Long allocationId) {
        String tenant = context.tenant();
        var allocation =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=? FOR"
                                + " UPDATE",
                        tenant,
                        allocationId);
        need(allocation != null, "实际消耗来源额度不存在");
        var proof = readProof(allocation);
        String sourceEvent = proof.path("sourceEventId").asText();
        var inbox = events.source(sourceEvent);
        var evidence = events.payload(inbox);
        TrackingPolicy policy = events.sourcePolicy(inbox);
        long version = events.sourceVersion(inbox);
        need(
                Set.of("ERP_MATERIAL_SOURCE_POSTED", "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED")
                                .contains(text(inbox.get("event_type")))
                        && proof.path("source").equals(evidence)
                        && Objects.equals(MesTraceStore.number(proof, "sourceVersion"), version)
                        && proof.path("policy").equals(events.read(events.encode(policy))),
                "消耗分配与ERP已确认原始凭据不一致");
        Long batch = MesTraceStore.number(evidence, "erpBatchId");
        need(
                Objects.equals(MesTraceStore.number(evidence, "mesWoId"), wo.getMesWoId())
                        && Objects.equals(
                                MesTraceStore.number(evidence, "erpWoId"), wo.getErpWoId())
                        && Objects.equals(
                                MesTraceStore.number(evidence, "materialId"),
                                id(allocation.get("material_id")))
                        && Objects.equals(batch, id(allocation.get("erp_batch_id")))
                        && (!policy.batchManaged() || positive(batch))
                        && Objects.equals(
                                evidence.path("sourceType").asText(), allocation.get("source_type"))
                        && Objects.equals(
                                MesTraceStore.number(evidence, "sourceId"),
                                id(allocation.get("source_id")))
                        && Objects.equals(
                                Optional.ofNullable(MesTraceStore.number(evidence, "sourceLineId"))
                                        .orElse(0L),
                                id(allocation.get("source_line_id"))),
                "来源归属、批次或工单不一致");
        var siblings =
                jdbc.queryForList(
                        "SELECT quantity FROM lt_lot_allocation WHERE tenant_id=? AND source_type=?"
                                + " AND source_id=? AND source_line_id=? FOR UPDATE",
                        tenant,
                        allocation.get("source_type"),
                        allocation.get("source_id"),
                        allocation.get("source_line_id"));
        BigDecimal allocated =
                siblings.stream()
                        .map(r -> qty(r.get("quantity")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        need(
                qty(allocation.get("quantity")).signum() > 0
                        && allocated.compareTo(MesMaterialTraceWriter.decimal(evidence, "quantity"))
                                <= 0,
                "跨MES子批次分配超过ERP来源额度");
        var lot =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        allocation.get("lot_id"));
        var mapped =
                jdbc.queryForList(
                        "SELECT quantity,erp_batch_id FROM lt_lot_allocation WHERE tenant_id=? AND"
                                + " lot_id=? FOR UPDATE",
                        tenant,
                        allocation.get("lot_id"));
        need(
                lot != null
                        && Objects.equals(
                                id(lot.get("material_id")), id(allocation.get("material_id")))
                        && mapped.stream()
                                .allMatch(r -> Objects.equals(batch, id(r.get("erp_batch_id"))))
                        && mapped.stream()
                                        .map(r -> qty(r.get("quantity")))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .compareTo(qty(lot.get("quantity")))
                                <= 0,
                "来源子批次物料、单一ERP批次或容量不一致");
        if (policy.serialManaged()
                || MesTraceStore.number(evidence, "identityId") != null
                || batch == null) {
            need(
                    qty(lot.get("quantity")).compareTo(BigDecimal.ONE) == 0
                            && qty(allocation.get("quantity")).compareTo(BigDecimal.ONE) == 0,
                    "组件SN来源必须为本地整件子批次");
        }
        return new MaterialSource(allocation, inbox, evidence, policy, version, sourceEvent);
    }

    private Long productBatch(String tenant, Long lotId, Long materialId, boolean required) {
        if (lotId == null) {
            need(!required, "批次SN缺少独立成品批次");
            return null;
        }
        var lot =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        lotId);
        need(lot != null && Objects.equals(materialId, id(lot.get("material_id"))), "成品批次与SN物料不一致");
        var allocations =
                jdbc.queryForList(
                        "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND lot_id=? AND"
                                + " material_id=? AND source_type='PRODUCTION_OUTPUT' ORDER BY"
                                + " allocation_id",
                        tenant,
                        lotId,
                        materialId);
        if (allocations.isEmpty()) {
            need(!required, "成品批次没有可信ERP批次映射");
            return null;
        }
        var batches =
                allocations.stream()
                        .map(r -> id(r.get("erp_batch_id")))
                        .collect(Collectors.toSet());
        need(batches.size() == 1, "同一成品批次存在多个ERP批次，须先按实物分配明确来源");
        BigDecimal quantity =
                allocations.stream()
                        .map(r -> qty(r.get("quantity")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long assigned =
                jdbc.queryForList(
                                "SELECT sn_id FROM pro_sn WHERE tenant_id=? AND product_lot_id=?"
                                        + " AND del_flag='0' FOR UPDATE",
                                tenant,
                                lotId)
                        .size();
        need(
                BigDecimal.valueOf(assigned).compareTo(quantity) <= 0
                        && BigDecimal.valueOf(assigned).compareTo(qty(lot.get("quantity"))) <= 0,
                "成品批次SN数超过映射或批次数量上限");
        return batches.iterator().next();
    }

    private JsonNode readProof(Map<String, Object> allocation) {
        try {
            String body = text(allocation.get("evidence_json"));
            need(TraceHashes.sha256(body).equals(allocation.get("evidence_hash")), "批次映射摘要不一致");
            return json.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("批次映射无效");
        }
    }

    /**
     * 读取与租户、清单标识及修订完全一致的冻结头。
     *
     * @param tenant 当前认证租户。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @return 指定租户与修订的不可变完工清单头。
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public CompletionManifest manifest(String tenant, Long manifestId, int revision) {
        context.requireTenant(tenant);
        var row =
                one(
                        jdbc,
                        "SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND manifest_id=?"
                                + " AND revision=? AND status IN"
                                + " ('FROZEN','APPLIED','RECEIVED','CANCEL_REQUESTED','CANCELLED')",
                        tenant,
                        manifestId,
                        revision);
        need(row != null, "冻结清单不存在");
        return header(row);
    }

    /**
     * 按稳定游标返回指定修订的产品明细，后续页保持同一份冻结快照。
     *
     * @param tenant 当前认证租户。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public TracePage<CompletionManifestLine> lines(
            String tenant, Long manifestId, int revision, String cursor, int limit) {
        context.requireTenant(tenant);
        need(limit >= 1 && limit <= 500, "清单每页上限为500");
        // 游标同时绑定租户、清单及修订，不能用于读取另一份快照。
        long after = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] fields = decoded.split(":", -1);
                need(
                        fields.length == 4
                                && fields[0].equals(tenant)
                                && fields[1].equals(manifestId.toString())
                                && fields[2].equals(Integer.toString(revision)),
                        "清单游标不属于此快照");
                after = Long.parseLong(fields[3]);
                need(after > 0, "清单游标无效");
            } catch (Exception e) {
                throw new IllegalArgumentException("清单游标无效");
            }
        }
        var header =
                one(
                        jdbc,
                        "SELECT manifest_id FROM pro_completion_manifest WHERE tenant_id=? AND"
                                + " manifest_id=? AND revision=? AND status IN"
                                + " ('FROZEN','APPLIED','RECEIVED','CANCEL_REQUESTED','CANCELLED')",
                        tenant,
                        manifestId,
                        revision);
        need(header != null, "冻结清单不存在");
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM pro_completion_manifest_line WHERE tenant_id=? AND"
                            + " manifest_id=? AND revision=? AND line_no>? ORDER BY line_no LIMIT"
                            + " ?",
                        tenant,
                        manifestId,
                        revision,
                        after,
                        limit + 1);
        boolean more = rows.size() > limit;
        var selected = rows.stream().limit(limit).map(MesCompletionManifestService::line).toList();
        String next =
                more
                        ? Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        (tenant
                                                        + ":"
                                                        + manifestId
                                                        + ":"
                                                        + revision
                                                        + ":"
                                                        + selected.get(selected.size() - 1)
                                                                .lineNo())
                                                .getBytes(StandardCharsets.UTF_8))
                        : null;
        return new TracePage<>(selected, next, more);
    }

    /**
     * 读取该修订的实际用料汇总及摘要。
     *
     * @param tenant 当前认证租户。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @return 指定修订的实际用料汇总及完整摘要。
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public CompletionAllocationManifest allocationManifest(
            String tenant, Long manifestId, int revision) {
        context.requireTenant(tenant);
        return materialHeader(frozenHeader(tenant, manifestId, revision));
    }

    /**
     * 分页返回该修订冻结的实际用料，调用方必须核验完整页集合。
     *
     * @param tenant 当前认证租户。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public TracePage<CompletionMaterialAllocation> allocations(
            String tenant, Long manifestId, int revision, String cursor, int limit) {
        context.requireTenant(tenant);
        need(limit >= 1 && limit <= 500, "组件清单每页上限为500");
        materialHeader(frozenHeader(tenant, manifestId, revision));
        long after = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] fields =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                                .split(":", -1);
                need(
                        fields.length == 5
                                && fields[0].equals(tenant)
                                && fields[1].equals(manifestId.toString())
                                && fields[2].equals(Integer.toString(revision))
                                && fields[3].equals("materials"),
                        "组件游标不属于此快照");
                after = Long.parseLong(fields[4]);
                need(after > 0, "组件游标无效");
            } catch (Exception error) {
                throw new IllegalArgumentException("组件清单游标无效");
            }
        }
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM pro_completion_material_allocation WHERE tenant_id=? AND"
                                + " manifest_id=? AND revision=? AND allocation_id>? ORDER BY"
                                + " allocation_id LIMIT ?",
                        tenant,
                        manifestId,
                        revision,
                        after,
                        limit + 1);
        boolean more = rows.size() > limit;
        var selected =
                rows.stream().limit(limit).map(MesCompletionManifestService::materialLine).toList();
        String next =
                more
                        ? Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        (tenant
                                                        + ":"
                                                        + manifestId
                                                        + ":"
                                                        + revision
                                                        + ":materials:"
                                                        + selected.get(selected.size() - 1)
                                                                .allocationId())
                                                .getBytes(StandardCharsets.UTF_8))
                        : null;
        return new TracePage<>(selected, next, more);
    }

    private Map<String, Object> frozenHeader(String tenant, Long manifestId, int revision) {
        var header =
                one(
                        jdbc,
                        "SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND manifest_id=?"
                                + " AND revision=? AND status IN"
                                + " ('FROZEN','APPLIED','RECEIVED','CANCEL_REQUESTED','CANCELLED')",
                        tenant,
                        manifestId,
                        revision);
        need(header != null, "冻结清单不存在");
        return header;
    }

    private static CompletionAllocationManifest materialHeader(Map<String, Object> row) {
        Long count = id(row.get("material_line_count"));
        String hash = text(row.get("material_hash"));
        need(count != null && count >= 0 && hash.matches("[0-9a-f]{64}"), "冻结组件清单元数据缺失，禁止从可变账补造");
        return new CompletionAllocationManifest(
                text(row.get("tenant_id")),
                id(row.get("manifest_id")),
                Math.toIntExact(id(row.get("revision"))),
                count,
                hash);
    }

    private static CompletionMaterialAllocation materialLine(Map<String, Object> r) {
        return new CompletionMaterialAllocation(
                id(r.get("allocation_id")),
                id(r.get("material_id")),
                id(r.get("reservation_id")),
                id(r.get("reservation_line_id")),
                id(r.get("posting_group_id")),
                id(r.get("identity_id")),
                id(r.get("source_stock_id")),
                id(r.get("erp_batch_id")),
                qty(r.get("quantity")),
                id(r.get("source_version")),
                text(r.get("evidence_hash")),
                text(r.get("source_event_id")));
    }

    public static String hash(List<CompletionManifestLine> lines) {
        return CompletionManifestIntegrity.hash(lines);
    }

    private static CompletionManifest header(Map<String, Object> r) {
        return new CompletionManifest(
                id(r.get("manifest_id")),
                Math.toIntExact(id(r.get("revision"))),
                text(r.get("tenant_id")),
                id(r.get("mes_wo_id")),
                id(r.get("erp_wo_id")),
                id(r.get("material_id")),
                id(r.get("line_count")),
                qty(r.get("good_qty")),
                qty(r.get("scrap_qty")),
                text(r.get("content_hash")),
                text(r.get("status")));
    }

    private static CompletionManifestLine line(Map<String, Object> r) {
        return new CompletionManifestLine(
                id(r.get("line_no")),
                id(r.get("identity_id")),
                id(r.get("local_sn_id")),
                text(r.get("sn_code")),
                id(r.get("batch_id")),
                id(r.get("local_lot_id")),
                id(r.get("material_id")),
                qty(r.get("quantity")),
                text(r.get("result")),
                id(r.get("source_version")));
    }
}
