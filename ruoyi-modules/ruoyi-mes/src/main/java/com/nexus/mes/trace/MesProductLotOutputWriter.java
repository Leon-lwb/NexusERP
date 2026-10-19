package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import com.nexus.mes.pro.domain.ProWorkOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;
import static com.nexus.mes.trace.MesTraceStore.number;

/**
 * 登记实际批次型产出；库存生产授权只规定额度上限，良品结论仍取自实际生产。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesProductLotOutputWriter {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final MesTraceStore events;
    private final MaterialPolicyPort policies;
    private final com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;

    /**
     * 按实际产品批次登记的一行合格或报废产出。
     *
     * @param lotId 制造模块本地子批次标识。
     * @param sourceAllocationId 原来源额度分配的稳定标识。
     * @param quantity 本行实际完成的产品批次数量。
     * @param result 本次业务执行的稳定结果。
     */
    public record OutputLine(
            Long lotId, Long sourceAllocationId, BigDecimal quantity, String result) {
        public OutputLine {
            if (quantity != null) {
                quantity = quantity.stripTrailingZeros();
            }
        }
    }

    /**
     * 原产品批次的生产授权与实际来源，用于核对可登记产出上限。
     *
     * @param lot 制造子批次及原分配来源。
     * @param allocation 经原来源核实的批次或单件分配信息。
     * @param eventId 跨模块事件的稳定标识。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     */
    private record Source(
            Map<String, Object> lot,
            Map<String, Object> allocation,
            String eventId,
            Long batchId) {}

    /**
     * 可加入末道过站或报工事务，也可在实际报工前单独声明实物产出。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> record(
            String commandId, Long cardId, Long stepId, List<OutputLine> lines, Long actorId) {
        preparedPolicies.prepareCardForCommand(cardId, "PRODUCT_LOT_OUTPUT", commandId);
        context.requireWrite();
        need(
                positive(cardId) && positive(stepId) && positive(actorId),
                "Actual product output requires a card, operation and authenticated operator");
        need(lines != null && !lines.isEmpty(), "Actual product lot lines are required");
        var request = List.copyOf(lines);
        var command =
                events.command(
                        commandId,
                        "PRODUCT_LOT_OUTPUT",
                        Arrays.asList(cardId, stepId, request, actorId));
        if ("APPLIED".equals(command.get("status"))) {
            var ids = new ArrayList<Long>();
            events.result(command).path("outputIds").forEach(v -> ids.add(v.asLong()));
            return List.copyOf(ids);
        }
        String tenant = context.tenant();
        var hint =
                one(
                        jdbc,
                        "SELECT mes_wo_id FROM pro_flow_card WHERE tenant_id=? AND card_id=? AND"
                                + " del_flag='0'",
                        tenant,
                        cardId);
        need(hint != null, "Actual product card does not exist");
        var wo =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        hint.get("mes_wo_id"));
        need(
                wo != null
                        && positive(id(wo.get("erp_wo_id")))
                        && Set.of("RELEASED", "IN_PROGRESS", "PARTIAL_COMPLETED")
                                .contains(text(wo.get("wo_status"))),
                "Product output requires an active real ERP/MES work order");
        need(
                one(
                                jdbc,
                                "SELECT manifest_id FROM pro_completion_manifest WHERE tenant_id=?"
                                        + " AND mes_wo_id=? LIMIT 1 FOR UPDATE",
                                tenant,
                                wo.get("mes_wo_id"))
                        == null,
                "Frozen production results require an audited correction");
        TrackingPolicy policy =
                preparedPolicies.enabled()
                        ? preparedPolicies.productPolicy(
                                id(wo.get("mes_wo_id")),
                                id(wo.get("erp_wo_id")),
                                id(wo.get("material_id")))
                        : policies.policy(tenant, id(wo.get("material_id")));
        need(
                policy != null && policy.batchManaged() && !policy.serialManaged(),
                "Lot output classification is only for batch-only products");
        var card =
                one(
                        jdbc,
                        "SELECT * FROM pro_flow_card WHERE tenant_id=? AND card_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        cardId);
        need(
                card != null
                        && Objects.equals(id(card.get("mes_wo_id")), id(wo.get("mes_wo_id")))
                        && Objects.equals(id(card.get("material_id")), id(wo.get("material_id")))
                        && Set.of("IN_PROGRESS", "COMPLETED")
                                .contains(text(card.get("card_status"))),
                "Product output card does not belong to this active production");
        var steps =
                jdbc.queryForList(
                        "SELECT * FROM pro_flow_card_step WHERE tenant_id=? AND card_id=? AND"
                                + " del_flag='0' ORDER BY sequence_num,step_record_id FOR UPDATE",
                        tenant,
                        cardId);
        var step =
                steps.stream()
                        .filter(s -> Objects.equals(stepId, id(s.get("step_record_id"))))
                        .findFirst()
                        .orElse(null);
        need(
                step != null
                        && Objects.equals(id(step.get("mes_wo_id")), id(wo.get("mes_wo_id")))
                        && Set.of("IN_PROGRESS", "COMPLETED")
                                .contains(text(step.get("step_status"))),
                "Output must reference an actual running or completed operation");
        Long last = lastExecutable(steps);
        var ids = new ArrayList<Long>();
        var lots = new HashSet<Long>();
        int lineNo = 0;
        for (var line : request) {
            need(
                    line != null
                            && positive(line.lotId())
                            && positive(line.sourceAllocationId())
                            && lots.add(line.lotId())
                            && Set.of("GOOD", "SCRAP").contains(line.result()),
                    "Product output lot, source or classification is invalid/duplicated");
            quantity(line.quantity());
            need(
                    !"GOOD".equals(line.result()) || stepId.equals(last),
                    "Only the actual final operation can produce deliverable good output");
            Source source =
                    source(
                            id(wo.get("mes_wo_id")),
                            id(wo.get("erp_wo_id")),
                            id(wo.get("material_id")),
                            line.lotId(),
                            line.sourceAllocationId(),
                            line.quantity(),
                            line.result());
            need(
                    jdbc.queryForList(
                                    "SELECT output_id FROM pro_product_lot_output WHERE tenant_id=?"
                                            + " AND lot_id=? FOR UPDATE",
                                    tenant,
                                    line.lotId())
                            .isEmpty(),
                    "A physical product lot can only be reported once; use an audited correction");
            long outputId = IdWorker.getId();
            jdbc.update(
                    "INSERT INTO"
                        + " pro_product_lot_output(output_id,tenant_id,command_id,line_no,mes_wo_id,card_id,step_record_id,lot_id,source_allocation_id,material_id,quantity,result,source_version,created_by)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1,?)",
                    outputId,
                    tenant,
                    commandId,
                    ++lineNo,
                    wo.get("mes_wo_id"),
                    cardId,
                    stepId,
                    line.lotId(),
                    line.sourceAllocationId(),
                    wo.get("material_id"),
                    line.quantity(),
                    line.result(),
                    actorId);
            checkAllocationUsage(source.allocation());
            var payload = new LinkedHashMap<String, Object>();
            payload.put("outputId", outputId);
            payload.put("mesWoId", wo.get("mes_wo_id"));
            payload.put("erpWoId", wo.get("erp_wo_id"));
            payload.put("cardId", cardId);
            payload.put("stepRecordId", stepId);
            payload.put("localLotId", line.lotId());
            payload.put("sourceAllocationId", line.sourceAllocationId());
            payload.put("sourceEventId", source.eventId());
            payload.put("materialId", wo.get("material_id"));
            payload.put("erpBatchId", source.batchId());
            payload.put("quantity", line.quantity());
            payload.put("result", line.result());
            payload.put("sourceVersion", 1L);
            events.append(
                    "PRODUCT_LOT_OUTPUT_RECORDED", "PRODUCT_LOT_OUTPUT", outputId, 1, payload);
            ids.add(outputId);
        }
        var recorded =
                jdbc.queryForList(
                        "SELECT quantity,result FROM pro_product_lot_output WHERE tenant_id=? AND"
                                + " step_record_id=? FOR UPDATE",
                        tenant,
                        stepId);
        BigDecimal good = total(recorded, "GOOD"), scrap = total(recorded, "SCRAP");
        if ("COMPLETED".equals(step.get("step_status"))) {
            need(
                    good.compareTo(qty(step.get("good_qty"))) <= 0
                            && scrap.compareTo(qty(step.get("scrap_qty"))) <= 0,
                    "Product results exceed actual operation report");
        } else {
            need(
                    good.add(scrap).compareTo(qty(step.get("planned_qty"))) <= 0,
                    "Declared physical output exceeds operation capacity");
        }
        events.finish(command, Map.of("outputIds", ids));
        return List.copyOf(ids);
    }

    /**
     * 仅在最终工单父行已锁定、完工清单尚未写入时调用。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<CompletionManifestLine> finalLines(ProWorkOrder wo) {
        context.requireWrite();
        String tenant = context.tenant();
        context.requireTenant(wo.getTenantId());
        var outputs =
                jdbc.queryForList(
                        "SELECT * FROM pro_product_lot_output WHERE tenant_id=? AND mes_wo_id=?"
                                + " ORDER BY card_id,step_record_id,lot_id,output_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        need(!outputs.isEmpty(), "Batch-only completion requires actual MES product lot results");
        var cards =
                jdbc.queryForList(
                        "SELECT * FROM pro_flow_card WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' ORDER BY card_id FOR UPDATE",
                        tenant,
                        wo.getMesWoId());
        var stepsByCard = new HashMap<Long, List<Map<String, Object>>>();
        var knownCards = new HashMap<Long, Map<String, Object>>();
        for (var card : cards) {
            Long cardId = id(card.get("card_id"));
            knownCards.put(cardId, card);
            need(
                    "COMPLETED".equals(card.get("card_status"))
                            && Objects.equals(id(card.get("material_id")), wo.getMaterialId()),
                    "Actual product source card is not complete");
            var steps =
                    jdbc.queryForList(
                            "SELECT * FROM pro_flow_card_step WHERE tenant_id=? AND card_id=? AND"
                                + " del_flag='0' ORDER BY sequence_num,step_record_id FOR UPDATE",
                            tenant,
                            cardId);
            need(
                    !steps.isEmpty()
                            && steps.stream()
                                    .allMatch(
                                            s ->
                                                    Objects.equals(
                                                                    id(s.get("mes_wo_id")),
                                                                    wo.getMesWoId())
                                                            && Set.of("COMPLETED", "SKIPPED")
                                                                    .contains(
                                                                            text(
                                                                                    s.get(
                                                                                            "step_status")))),
                    "Product lot source operations are not complete");
            stepsByCard.put(cardId, steps);
        }
        var result = new ArrayList<CompletionManifestLine>();
        var lots = new HashSet<Long>();
        long lineNo = 0;
        for (var output : outputs) {
            Long lotId = id(output.get("lot_id")),
                    cardId = id(output.get("card_id")),
                    stepId = id(output.get("step_record_id"));
            String classification = text(output.get("result"));
            need(
                    lots.add(lotId)
                            && positive(id(output.get("created_by")))
                            && positive(id(output.get("source_version")))
                            && Objects.equals(id(output.get("material_id")), wo.getMaterialId())
                            && knownCards.containsKey(cardId),
                    "Actual product output source is missing, duplicated or belongs to another"
                            + " card/material");
            var steps = stepsByCard.get(cardId);
            var step =
                    steps.stream()
                            .filter(s -> Objects.equals(id(s.get("step_record_id")), stepId))
                            .findFirst()
                            .orElse(null);
            need(
                    step != null
                            && "COMPLETED".equals(step.get("step_status"))
                            && (!"GOOD".equals(classification)
                                    || Objects.equals(stepId, lastExecutable(steps))),
                    "Product result lacks an actual completed source operation");
            Source source =
                    source(
                            wo.getMesWoId(),
                            wo.getErpWoId(),
                            wo.getMaterialId(),
                            lotId,
                            id(output.get("source_allocation_id")),
                            qty(output.get("quantity")),
                            classification);
            checkAllocationUsage(source.allocation());
            result.add(
                    new CompletionManifestLine(
                            ++lineNo,
                            null,
                            null,
                            null,
                            source.batchId(),
                            lotId,
                            wo.getMaterialId(),
                            qty(output.get("quantity")),
                            classification,
                            id(output.get("source_version"))));
        }
        for (var card : cards) {
            Long cardId = id(card.get("card_id"));
            var cardOutputs =
                    outputs.stream()
                            .filter(o -> Objects.equals(cardId, id(o.get("card_id"))))
                            .toList();
            need(
                    total(cardOutputs, "GOOD").compareTo(qty(card.get("completed_qty"))) == 0
                            && total(cardOutputs, "SCRAP").compareTo(qty(card.get("scrap_qty")))
                                    == 0,
                    "Actual product lot results do not reconcile with card good/scrap quantities");
            for (var step : stepsByCard.get(cardId)) {
                Long stepId = id(step.get("step_record_id"));
                var stepOutputs =
                        cardOutputs.stream()
                                .filter(o -> Objects.equals(stepId, id(o.get("step_record_id"))))
                                .toList();
                need(
                        total(stepOutputs, "SCRAP").compareTo(qty(step.get("scrap_qty"))) == 0,
                        "Actual scrap lots do not reconcile with the source operation");
                if (Objects.equals(stepId, lastExecutable(stepsByCard.get(cardId)))) {
                    need(
                            total(stepOutputs, "GOOD").compareTo(qty(step.get("good_qty"))) == 0,
                            "Actual good lots do not reconcile with the final operation");
                }
            }
        }
        return List.copyOf(result);
    }

    private Source source(
            Long woId,
            Long erpWoId,
            Long materialId,
            Long lotId,
            Long allocationId,
            BigDecimal actual,
            String result) {
        String tenant = context.tenant();
        quantity(actual);
        need(Set.of("GOOD", "SCRAP").contains(result), "Actual product result is invalid");
        var allocation =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=? FOR"
                                + " UPDATE",
                        tenant,
                        allocationId);
        need(
                allocation != null
                        && "PRODUCTION_OUTPUT".equals(allocation.get("source_type"))
                        && Objects.equals(id(allocation.get("lot_id")), lotId)
                        && Objects.equals(id(allocation.get("material_id")), materialId),
                "Actual product lot lacks its production source allocation");
        String proofText = text(allocation.get("evidence_json"));
        need(
                proofText != null
                        && TraceHashes.sha256(proofText).equals(allocation.get("evidence_hash")),
                "Production lot allocation evidence changed");
        var proof = events.read(proofText);
        String eventId = proof.path("sourceEventId").asText();
        var inbox = events.source(eventId);
        var evidence = events.payload(inbox);
        var policy = events.sourcePolicy(inbox);
        need(
                "ERP_PRODUCTION_LOT_AUTHORIZED".equals(inbox.get("event_type"))
                        && policy.batchManaged()
                        && !policy.serialManaged()
                        && "PRODUCTION_OUTPUT".equals(evidence.path("sourceType").asText())
                        && Objects.equals(number(evidence, "sourceId"), erpWoId)
                        && Objects.equals(number(evidence, "erpWoId"), erpWoId)
                        && Objects.equals(number(evidence, "mesWoId"), woId)
                        && Objects.equals(number(evidence, "materialId"), materialId),
                "Production quota does not belong to the actual ERP/MES product work order");
        Long batch = number(evidence, "erpBatchId"), line = number(evidence, "sourceLineId");
        need(
                positive(batch)
                        && positive(line)
                        && Objects.equals(id(allocation.get("erp_batch_id")), batch)
                        && Objects.equals(id(allocation.get("source_id")), erpWoId)
                        && Objects.equals(id(allocation.get("source_line_id")), line)
                        && proof.path("source").equals(evidence)
                        && Objects.equals(
                                number(proof, "sourceVersion"), events.sourceVersion(inbox))
                        && proof.path("policy").equals(events.read(events.encode(policy))),
                "Production allocation differs from its verified immutable ERP quota");
        var siblings =
                jdbc.queryForList(
                        "SELECT quantity FROM lt_lot_allocation WHERE tenant_id=? AND"
                                + " source_type='PRODUCTION_OUTPUT' AND source_id=? AND"
                                + " source_line_id=? FOR UPDATE",
                        tenant,
                        erpWoId,
                        line);
        need(
                sum(siblings).compareTo(MesMaterialTraceWriter.decimal(evidence, "quantity")) <= 0
                        && actual.compareTo(qty(allocation.get("quantity"))) <= 0,
                "Actual output or child lot allocations exceed ERP source quota");
        var lot =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        lotId);
        need(
                lot != null
                        && Objects.equals(id(lot.get("material_id")), materialId)
                        && actual.compareTo(qty(lot.get("quantity"))) == 0
                        && qty(lot.get("consumed_qty")).signum() == 0,
                "Actual product lot material/quantity changed or the output was already consumed");
        if ("GOOD".equals(result)) {
            need(
                    "PASS".equals(lot.get("qc_status")) && "IN_STOCK".equals(lot.get("status")),
                    "Good output requires a QC-passed available product lot");
        } else {
            need(
                    "SCRAPPED".equals(lot.get("status"))
                            && Set.of("PASS", "FAIL").contains(text(lot.get("qc_status"))),
                    "Scrap output requires a physically scrapped product lot with a decided QC"
                            + " result");
        }
        var mapped =
                jdbc.queryForList(
                        "SELECT quantity,erp_batch_id FROM lt_lot_allocation WHERE tenant_id=? AND"
                                + " lot_id=? FOR UPDATE",
                        tenant,
                        lotId);
        need(
                mapped.stream().allMatch(a -> Objects.equals(batch, id(a.get("erp_batch_id"))))
                        && sum(mapped).compareTo(qty(lot.get("quantity"))) <= 0,
                "A product child lot must have exactly one evidenced ERP batch within its"
                        + " quantity");
        need(
                jdbc.queryForList(
                                "SELECT sn_id FROM pro_sn WHERE tenant_id=? AND product_lot_id=?"
                                        + " AND del_flag='0' FOR UPDATE",
                                tenant,
                                lotId)
                        .isEmpty(),
                "A batch-only product lot cannot also be reported as serial output");
        return new Source(lot, allocation, eventId, batch);
    }

    private void checkAllocationUsage(Map<String, Object> allocation) {
        var outputs =
                jdbc.queryForList(
                        "SELECT quantity FROM pro_product_lot_output WHERE tenant_id=? AND"
                                + " source_allocation_id=? FOR UPDATE",
                        context.tenant(),
                        allocation.get("allocation_id"));
        need(
                sum(outputs).compareTo(qty(allocation.get("quantity"))) <= 0,
                "Actual product output exceeds its allocated ERP quota");
    }

    private static Long lastExecutable(List<Map<String, Object>> steps) {
        Long last = null;
        for (var step : steps) {
            if (!"SKIPPED".equals(step.get("step_status"))) {
                last = id(step.get("step_record_id"));
            }
        }
        return last;
    }

    private static BigDecimal total(List<Map<String, Object>> rows, String result) {
        return rows.stream()
                .filter(r -> result.equals(r.get("result")))
                .map(r -> qty(r.get("quantity")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(r -> qty(r.get("quantity")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
