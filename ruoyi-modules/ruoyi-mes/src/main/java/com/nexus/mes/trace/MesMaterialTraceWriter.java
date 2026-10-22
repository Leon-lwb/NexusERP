package com.nexus.mes.trace;

import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;
import static com.nexus.mes.trace.MesTraceStore.number;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 管理实际消耗和逐身份分配；仓库扣减由库存业务事务负责。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesMaterialTraceWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MesTraceContext context;
    private final MesTraceStore events;
    private final MesCompletionManifestService manifests;

    /**
     * 一笔实际批次、数量及原来源额度选择。
     *
     * @param lotId 制造模块本地子批次标识。
     * @param quantity 本来源批次本次实际用料数量。
     * @param allocationId 原来源分配额度标识。
     */
    public record PickLine(Long lotId, BigDecimal quantity, Long allocationId) {
        public PickLine {
            if (quantity != null) {
                quantity = quantity.stripTrailingZeros();
            }
        }
    }

    /**
     * 带原命令与工序依据的实际用料选择集合。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param woId 制造模块本地工单主键。
     * @param materialId 当前业务行实际物料标识。
     * @param snId 制造模块本地单件主键；跨模块关联另取稳定身份。
     * @param processId 实际发生用料或过站的工序标识。
     * @param operationRef 原作业记录的稳定引用。
     * @param lines 本次实际用料的批次、数量及来源额度选择集合。
     * @param actorId 本次业务操作的认证人员标识。
     */
    public record Pick(
            String commandId,
            Long woId,
            Long materialId,
            Long snId,
            Long processId,
            String operationRef,
            List<PickLine> lines,
            Long actorId) {
        public Pick {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * 原分配分录及本次追加冲正数量。
     *
     * @param originalEntryId 本次冲正引用的原用料分录。
     * @param quantity 本次追加冲正的用料数量。
     */
    public record ReverseLine(Long originalEntryId, BigDecimal quantity) {
        public ReverseLine {
            if (quantity != null) {
                quantity = quantity.stripTrailingZeros();
            }
        }
    }

    /**
     * 更正后重新分配的实际消耗及工序来源。
     *
     * @param consumptionId 已经发生的工单实际消耗记录。
     * @param quantity 更正后重新分配给产品单件的组件数量。
     * @param stepRecordId 原工序执行或过站记录。
     * @param operationRef 原作业记录的稳定引用。
     */
    public record CorrectedAllocation(
            Long consumptionId, BigDecimal quantity, Long stepRecordId, String operationRef) {
        public CorrectedAllocation {
            if (quantity != null) {
                quantity = quantity.stripTrailingZeros();
            }
        }
    }

    /**
     * 不可变的组件冲正与重录请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param manifestId 不可变完工清单标识。
     * @param cancelledRevision 已经通过取消握手释放的旧清单修订。
     * @param snId 制造模块本地单件主键；跨模块关联另取稳定身份。
     * @param componentMaterialId 实际消耗的组件物料标识。
     * @param expectedVersion 调用方预期的原记录版本，用于并发条件校验。
     * @param reversals 按原分录追加的冲正集合。
     * @param allocations 完整的实际组件用料分配集合。
     * @param reason 与原任务或原事实关联的明确操作原因。
     * @param actorId 本次业务操作的认证人员标识。
     */
    public record Correction(
            String commandId,
            Long manifestId,
            int cancelledRevision,
            Long snId,
            Long componentMaterialId,
            long expectedVersion,
            List<ReverseLine> reversals,
            List<CorrectedAllocation> allocations,
            String reason,
            Long actorId) {
        public Correction {
            reversals = reversals == null ? List.of() : List.copyOf(reversals);
            allocations = allocations == null ? List.of() : List.copyOf(allocations);
        }
    }

    /**
     * 本次组件更正产生的冲正及分配分录标识。
     *
     * @param reversalEntryIds 本次新建冲正分录标识集合。
     * @param allocationEntryIds 本次新建实际分配分录标识集合。
     * @param version 当前事实或规则版本。
     */
    public record CorrectionResult(
            List<Long> reversalEntryIds, List<Long> allocationEntryIds, long version) {}

    /**
     * 仅在清单取消握手完成后追加组件冲正与重录，保留旧分录并核对净用量和版本。
     *
     * @param request 本次业务的原始请求，具体校验要求见方法说明。
     * @param policy 来源业务已冻结的追踪策略。
     * @return 本次追加的冲正及重录分录主键集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public CorrectionResult correct(Correction request, TrackingPolicy policy) {
        context.requireWrite();
        need(
                request != null
                        && positive(request.manifestId())
                        && request.cancelledRevision() > 0
                        && positive(request.snId())
                        && positive(request.componentMaterialId())
                        && request.expectedVersion() > 0
                        && positive(request.actorId())
                        && request.reason() != null
                        && !request.reason().isBlank()
                        && request.reason().length() <= 500
                        && (!request.reversals().isEmpty() || !request.allocations().isEmpty()),
                "Correction requires its exact cancelled manifest, reason, operator and expected"
                        + " local SN version");
        var command = events.command(request.commandId(), "SN_COMPONENT_CORRECTION", request);
        if ("APPLIED".equals(command.get("status"))) {
            var result = events.result(command);
            var reversals = new ArrayList<Long>();
            var allocations = new ArrayList<Long>();
            result.path("reversalEntryIds").forEach(v -> reversals.add(v.asLong()));
            result.path("allocationEntryIds").forEach(v -> allocations.add(v.asLong()));
            return new CorrectionResult(
                    List.copyOf(reversals),
                    List.copyOf(allocations),
                    result.path("version").asLong());
        }
        need(policy != null, "New correction requires a verified frozen work-order policy");
        String tenant = context.tenant();
        var hint =
                one(
                        jdbc,
                        "SELECT mes_wo_id FROM pro_sn WHERE tenant_id=? AND sn_id=? AND"
                                + " del_flag='0'",
                        tenant,
                        request.snId());
        need(hint != null, "Correction SN does not exist");
        Long woId = id(hint.get("mes_wo_id"));
        manifests.requireCancelledForCorrection(
                woId, request.manifestId(), request.cancelledRevision());
        var sn =
                one(
                        jdbc,
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        request.snId());
        need(
                sn != null
                        && allocatable(sn, true)
                        && positive(id(sn.get("identity_id")))
                        && Objects.equals(
                                id(sn.get("identity_version")), request.expectedVersion()),
                "Correction SN version/state changed or its delivery is still held");
        need(
                one(
                                jdbc,
                                "SELECT line_no FROM pro_completion_manifest_line WHERE tenant_id=?"
                                        + " AND manifest_id=? AND revision=? AND local_sn_id=? AND"
                                        + " identity_id=?",
                                tenant,
                                request.manifestId(),
                                request.cancelledRevision(),
                                request.snId(),
                                sn.get("identity_id"))
                        != null,
                "Correction identity was not part of the cancelled manifest");
        var reverseIds = new ArrayList<Long>();
        var allocationIds = new ArrayList<Long>();
        long lineNo = 0, nextVersion = Math.addExact(request.expectedVersion(), 1);
        var audit =
                Map.of(
                        "commandId",
                        request.commandId(),
                        "cancelledManifestId",
                        request.manifestId(),
                        "cancelledRevision",
                        request.cancelledRevision(),
                        "expectedVersion",
                        request.expectedVersion(),
                        "sourceVersion",
                        nextVersion,
                        "reason",
                        request.reason(),
                        "actorId",
                        request.actorId());
        for (var line : request.reversals()) {
            quantity(line.quantity());
            var original =
                    one(
                            jdbc,
                            "SELECT * FROM lt_sn_consumption WHERE tenant_id=? AND entry_id=? FOR"
                                    + " UPDATE",
                            tenant,
                            line.originalEntryId());
            need(
                    original != null
                            && Objects.equals(id(original.get("local_sn_id")), request.snId())
                            && Objects.equals(
                                    id(original.get("identity_id")), id(sn.get("identity_id")))
                            && Objects.equals(
                                    id(original.get("component_material_id")),
                                    request.componentMaterialId()),
                    "Correction reversal must reference this exact SN/component allocation");
            Long entry =
                    reverseEntry(
                            request.commandId(),
                            ++lineNo,
                            line.originalEntryId(),
                            line.quantity(),
                            request.actorId(),
                            true);
            reverseIds.add(entry);
            events.append(
                    "SN_CONSUMPTION_REVERSED",
                    "SN_CONSUMPTION",
                    entry,
                    1,
                    Map.of(
                            "originalEntryId",
                            line.originalEntryId(),
                            "entryId",
                            entry,
                            "quantity",
                            line.quantity(),
                            "reason",
                            request.reason(),
                            "correction",
                            audit));
        }
        for (var line : request.allocations()) {
            quantity(line.quantity());
            var use =
                    one(
                            jdbc,
                            "SELECT * FROM lt_wo_consumption WHERE tenant_id=? AND consumption_id=?"
                                    + " AND del_flag='0' FOR UPDATE",
                            tenant,
                            line.consumptionId());
            need(
                    use != null
                            && Objects.equals(id(use.get("wo_id")), woId)
                            && Objects.equals(
                                    id(use.get("material_id")), request.componentMaterialId()),
                    "Corrected allocation requires this work order's real component consumption");
            var step =
                    one(
                            jdbc,
                            "SELECT * FROM pro_flow_card_step WHERE tenant_id=? AND"
                                + " step_record_id=? AND card_id=? AND mes_wo_id=? AND del_flag='0'"
                                + " FOR UPDATE",
                            tenant,
                            line.stepRecordId(),
                            sn.get("card_id"),
                            woId);
            need(
                    step != null
                            && "COMPLETED".equals(step.get("step_status"))
                            && positive(id(step.get("process_id"))),
                    "Corrected allocation must reference this product's actual completed"
                            + " operation");
            Long entry =
                    allocate(
                            request.commandId(),
                            ++lineNo,
                            line.consumptionId(),
                            request.snId(),
                            line.quantity(),
                            id(step.get("process_id")),
                            line.operationRef(),
                            request.actorId(),
                            policy,
                            true);
            allocationIds.add(entry);
            events.append(
                    "SN_CONSUMPTION_ALLOCATED",
                    "SN_CONSUMPTION",
                    entry,
                    1,
                    Map.of(
                            "entryId",
                            entry,
                            "sourceConsumptionId",
                            line.consumptionId(),
                            "localSnId",
                            request.snId(),
                            "quantity",
                            line.quantity(),
                            "processId",
                            step.get("process_id"),
                            "operationRef",
                            line.operationRef(),
                            "policyVersion",
                            entryPolicyVersion(entry),
                            "correction",
                            audit));
        }
        need(
                jdbc.update(
                                "UPDATE pro_sn SET identity_version=identity_version+1 WHERE"
                                        + " tenant_id=? AND sn_id=? AND identity_version=? AND"
                                        + " handoff_status='NONE' AND sn_status IN"
                                        + " ('COMPLETED','SCRAPPED') AND del_flag='0'",
                                tenant,
                                request.snId(),
                                request.expectedVersion())
                        == 1,
                "Correction lost its expected SN version");
        var result =
                new CorrectionResult(
                        List.copyOf(reverseIds), List.copyOf(allocationIds), nextVersion);
        events.finish(command, result);
        return result;
    }

    /**
     * 读取原实际消耗的组件物料，用于事务前准备对应冻结策略。
     *
     * @param consumptionId 已经发生的工单实际消耗记录。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Long consumptionMaterial(Long consumptionId) {
        var row =
                one(
                        jdbc,
                        "SELECT material_id FROM lt_wo_consumption WHERE tenant_id=? AND"
                                + " consumption_id=? AND del_flag='0'",
                        context.tenant(),
                        consumptionId);
        need(row != null, "实际消耗来源不存在");
        return id(row.get("material_id"));
    }

    /**
     * 按制造本地单件读取实际组件分配事实，不以产品自身批次替代组件来源。
     *
     * @return 该制造单件的实际组件用料分录。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> entriesForSn(Long snId) {
        return jdbc.queryForList(
                "SELECT * FROM lt_sn_consumption WHERE tenant_id=? AND local_sn_id=? ORDER BY"
                        + " created_at,entry_id",
                context.tenant(),
                snId);
    }

    /**
     * 把已核验的库存来源额度分配给制造子批次，条件更新同时约束有效期与剩余额度。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param sourceEventId 已提交的原来源事件标识。
     * @param lotId 制造模块本地子批次标识。
     * @param quantity 本次划入制造子批次的来源额度。
     * @param actorId 本次业务操作的认证人员标识。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long allocateLot(
            String commandId, String sourceEventId, Long lotId, BigDecimal quantity, Long actorId) {
        context.requireWrite();
        MesTraceSql.quantity(quantity);
        need(positive(actorId), "批次分配缺少可信操作人");
        var command =
                events.command(
                        commandId,
                        "LOT_ALLOCATION",
                        Arrays.asList(
                                sourceEventId,
                                lotId,
                                quantity.stripTrailingZeros().toPlainString(),
                                actorId));
        if ("APPLIED".equals(command.get("status"))) {
            return number(events.result(command), "allocationId");
        }
        var sourceHint =
                one(
                        jdbc,
                        "SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=?",
                        context.tenant(),
                        sourceEventId);
        need(sourceHint != null, "必须提供已确认的ERP来源凭据");
        lockWo(number(events.payload(sourceHint), "mesWoId"));
        var source = events.source(sourceEventId);
        String type = text(source.get("event_type"));
        need(
                Set.of(
                                "ERP_MATERIAL_SOURCE_POSTED",
                                "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",
                                "ERP_PRODUCTION_LOT_AUTHORIZED")
                        .contains(type),
                "来源事件不授权批次分配");
        var evidence = events.payload(source);
        String tenant = context.tenant();
        var lot =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        lotId);
        need(
                lot != null
                        && Objects.equals(
                                id(lot.get("material_id")), number(evidence, "materialId")),
                "MES批次不存在或来源物料不匹配");
        boolean serial = validateSource(source, evidence, quantity);
        if (serial) {
            need(BigDecimal.ONE.compareTo(qty(lot.get("quantity"))) == 0, "正式组件SN必须映射本地单件批次");
        }
        Long batch = number(evidence, "erpBatchId");
        need(
                lot.get("erp_batch_id") == null
                        || Objects.equals(id(lot.get("erp_batch_id")), batch),
                "一个MES来源子批次只能关联一个ERP批次");
        var mappedBatches =
                jdbc.queryForList(
                        "SELECT erp_batch_id FROM lt_lot_allocation WHERE tenant_id=? AND lot_id=?"
                                + " FOR UPDATE",
                        tenant,
                        lotId);
        need(
                mappedBatches.stream()
                        .allMatch(a -> Objects.equals(id(a.get("erp_batch_id")), batch)),
                "一个MES来源子批次不能混合多个ERP批次，即使还有余量");
        BigDecimal sourceUsed =
                sum(
                        jdbc.queryForList(
                                "SELECT quantity FROM lt_lot_allocation WHERE tenant_id=? AND"
                                        + " source_type=? AND source_id=? AND source_line_id=? FOR"
                                        + " UPDATE",
                                tenant,
                                evidence.path("sourceType").asText(),
                                number(evidence, "sourceId"),
                                Optional.ofNullable(number(evidence, "sourceLineId")).orElse(0L)),
                        "quantity");
        need(
                sourceUsed.add(quantity).compareTo(decimal(evidence, "quantity")) <= 0,
                "ERP来源批次的跨MES批次分配超出凭据数量");
        BigDecimal lotUsed =
                sum(
                        jdbc.queryForList(
                                "SELECT quantity FROM lt_lot_allocation WHERE tenant_id=? AND"
                                        + " lot_id=? FOR UPDATE",
                                tenant,
                                lotId),
                        "quantity");
        need(lotUsed.add(quantity).compareTo(qty(lot.get("quantity"))) <= 0, "MES批次分配超过批次数量");
        Long allocation = IdWorker.getId();
        String proof =
                events.encode(
                        Map.of(
                                "sourceEventId",
                                sourceEventId,
                                "source",
                                evidence,
                                "actorId",
                                actorId,
                                "policy",
                                events.sourcePolicy(source),
                                "sourceVersion",
                                events.sourceVersion(source)));
        jdbc.update(
                "INSERT INTO"
                    + " lt_lot_allocation(allocation_id,tenant_id,command_id,line_no,lot_id,erp_batch_id,material_id,quantity,source_type,source_id,source_line_id,allocation_version,evidence_json,evidence_hash)"
                    + " VALUES(?,?,?,1,?,?,?,?,?,?,?,?,?,?)",
                allocation,
                tenant,
                commandId,
                lotId,
                number(evidence, "erpBatchId"),
                number(evidence, "materialId"),
                quantity,
                evidence.path("sourceType").asText(),
                number(evidence, "sourceId"),
                Optional.ofNullable(number(evidence, "sourceLineId")).orElse(0L),
                id(lot.get("allocation_version")),
                proof,
                TraceHashes.sha256(proof));
        // 只有整个制造子批次均有同一个库存批次的充分来源证据时，才填写直接批次关联。
        var batches =
                jdbc.queryForList(
                        "SELECT erp_batch_id FROM lt_lot_allocation WHERE tenant_id=? AND lot_id=?"
                                + " FOR UPDATE",
                        tenant,
                        lotId);
        if (batches.stream().map(b -> id(b.get("erp_batch_id"))).distinct().count() == 1
                && lotUsed.add(quantity).compareTo(qty(lot.get("quantity"))) == 0) {
            jdbc.update(
                    "UPDATE lt_lot SET erp_batch_id=? WHERE tenant_id=? AND lot_id=?",
                    number(evidence, "erpBatchId"),
                    tenant,
                    lotId);
        }
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("allocationId", allocation);
        fact.put("sourceEventId", sourceEventId);
        fact.put("localLotId", lotId);
        fact.put("erpBatchId", batch);
        fact.put("quantity", quantity);
        events.append("LOT_ALLOCATION_RECORDED", "LOT_ALLOCATION", allocation, 1, fact);
        events.finish(command, Map.of("allocationId", allocation));
        return allocation;
    }

    /**
     * 进入制造写代理前，必须为真实库存工单与物料准备好冻结策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> pick(Pick pick, TrackingPolicy policy) {
        context.requireWrite();
        need(
                positive(pick.woId())
                        && positive(pick.materialId())
                        && positive(pick.actorId())
                        && !pick.lines().isEmpty(),
                "领用来源、操作人或明细缺失");
        need(
                pick.operationRef() != null
                        && !pick.operationRef().isBlank()
                        && pick.operationRef().length() <= 128,
                "实际消耗必须有操作凭据");
        for (var line : pick.lines()) {
            quantity(line.quantity());
            need(positive(line.lotId()) && positive(line.allocationId()), "实际消耗必须关联真实批次分配");
        }
        var command = events.command(pick.commandId(), "MATERIAL_PICK", pick);
        if ("APPLIED".equals(command.get("status"))) {
            var result = new ArrayList<Long>();
            events.result(command)
                    .path("consumptionIds")
                    .forEach(n -> result.add(new BigDecimal(n.asText()).longValueExact()));
            return result;
        }
        need(policy != null, "New consumption requires a verified frozen work-order policy");
        String tenant = context.tenant();
        var wo = lockWo(pick.woId());
        var bom =
                jdbc.queryForList(
                        "SELECT * FROM pro_work_order_bom_line WHERE tenant_id=? AND mes_wo_id=?"
                            + " AND child_material_id=? AND del_flag='0' ORDER BY wo_bom_line_id"
                            + " FOR UPDATE",
                        tenant,
                        pick.woId(),
                        pick.materialId());
        need(!bom.isEmpty(), "缺少可信工单BOM需求快照，禁止猜测领用上限");
        BigDecimal demand =
                bom.stream()
                        .map(r -> qty(r.get("total_quantity")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used =
                sum(
                        jdbc.queryForList(
                                "SELECT consumed_qty FROM lt_wo_consumption WHERE tenant_id=? AND"
                                        + " wo_id=? AND material_id=? AND del_flag='0' FOR UPDATE",
                                tenant,
                                pick.woId(),
                                pick.materialId()),
                        "consumed_qty");
        BigDecimal requested =
                pick.lines().stream()
                        .map(PickLine::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        need(used.add(requested).compareTo(demand) <= 0, "工单物料累计实际消耗超过BOM需求上限");
        var consumptions = new ArrayList<Long>();
        int lineNo = 0;
        Long appliedPolicyVersion = policy.version();
        for (var line :
                pick.lines().stream()
                        .sorted(
                                Comparator.comparing(PickLine::lotId)
                                        .thenComparing(PickLine::allocationId))
                        .toList()) {
            var allocation =
                    one(
                            jdbc,
                            "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=?"
                                    + " FOR UPDATE",
                            tenant,
                            line.allocationId());
            need(
                    allocation != null
                            && Objects.equals(id(allocation.get("lot_id")), line.lotId())
                            && Objects.equals(id(allocation.get("material_id")), pick.materialId()),
                    "领用分配与批次或物料不一致");
            var source = sourceForAllocation(allocation);
            var evidence = events.payload(source);
            boolean serial = validateSource(source, evidence, line.quantity());
            need(!policy.serialManaged() || serial, "单件物料不能使用无正式身份的来源消耗");
            need(
                    Objects.equals(number(evidence, "mesWoId"), pick.woId())
                            && Objects.equals(number(evidence, "erpWoId"), id(wo.get("erp_wo_id"))),
                    "ERP来源不属于实际工单");
            var matched =
                    bom.stream()
                            .filter(
                                    r ->
                                            pick.processId() == null
                                                    || Objects.equals(
                                                            id(r.get("process_id")),
                                                            pick.processId()))
                            .toList();
            need(!matched.isEmpty(), "实际消耗工序与BOM不一致");
            int backflush = evidence.path("backflushFlag").asInt(-1);
            need(
                    matched.stream()
                                    .allMatch(
                                            r ->
                                                    qty(r.get("backflush_flag")).intValueExact()
                                                            == backflush)
                            && (backflush == 0
                                            && "ERP_MATERIAL_SOURCE_POSTED"
                                                    .equals(source.get("event_type"))
                                    || backflush == 1
                                            && "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED"
                                                    .equals(source.get("event_type"))),
                    "预发料与倒冲来源不能混用或重复扣ERP库存");
            BigDecimal allocatedUsed =
                    sum(
                            jdbc.queryForList(
                                    "SELECT consumed_qty FROM lt_wo_consumption WHERE tenant_id=?"
                                        + " AND source_allocation_id=? AND del_flag='0' FOR UPDATE",
                                    tenant,
                                    line.allocationId()),
                            "consumed_qty");
            need(
                    allocatedUsed.add(line.quantity()).compareTo(qty(allocation.get("quantity")))
                            <= 0,
                    "实际消耗超过来源分配数量");
            var lot =
                    one(
                            jdbc,
                            "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0'"
                                    + " FOR UPDATE",
                            tenant,
                            line.lotId());
            need(
                    lot != null
                            && Objects.equals(id(lot.get("material_id")), pick.materialId())
                            && Set.of("IN_STOCK", "IN_USE").contains(text(lot.get("status")))
                            && "PASS".equals(lot.get("qc_status")),
                    "批次不可继续领用或QC未放行");
            // 写入语句再次校验有效期和额度上限，避免先读后写之间发生并发超用。
            need(
                    jdbc.update(
                                    "UPDATE lt_lot SET consumed_qty=consumed_qty+? WHERE"
                                        + " tenant_id=? AND lot_id=? AND del_flag='0' AND status IN"
                                        + " ('IN_STOCK','IN_USE') AND qc_status='PASS' AND"
                                        + " (expiry_date IS NULL OR expiry_date>=CURRENT_DATE) AND"
                                        + " consumed_qty+?<=quantity",
                                    line.quantity(),
                                    tenant,
                                    line.lotId(),
                                    line.quantity())
                            == 1,
                    "批次已过期、剩余不足或状态已变更");
            need(
                    jdbc.update(
                                    "UPDATE lt_lot SET status=CASE WHEN consumed_qty>=quantity THEN"
                                            + " 'CONSUMED' ELSE 'IN_USE' END WHERE tenant_id=? AND"
                                            + " lot_id=? AND del_flag='0'",
                                    tenant,
                                    line.lotId())
                            == 1,
                    "批次状态写入失败");
            Long consumption = IdWorker.getId();
            jdbc.update(
                    "INSERT INTO"
                        + " lt_wo_consumption(consumption_id,tenant_id,wo_id,wo_no,lot_id,lot_code,material_id,material_code,consumed_qty,consumed_time,operator_id,source_allocation_id,del_flag)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,'0')",
                    consumption,
                    tenant,
                    pick.woId(),
                    wo.get("wo_no"),
                    line.lotId(),
                    lot.get("lot_code"),
                    pick.materialId(),
                    lot.get("material_code"),
                    line.quantity(),
                    pick.actorId(),
                    line.allocationId());
            consumptions.add(consumption);
            if (pick.snId() != null) {
                Long entry =
                        allocate(
                                pick.commandId(),
                                ++lineNo,
                                consumption,
                                pick.snId(),
                                line.quantity(),
                                pick.processId(),
                                pick.operationRef(),
                                pick.actorId(),
                                policy);
                appliedPolicyVersion = entryPolicyVersion(entry);
            }
        }
        events.append(
                "MATERIAL_CONSUMPTION_RECORDED",
                "MATERIAL_CONSUMPTION",
                consumptions.get(0),
                1,
                Map.of(
                        "commandId",
                        pick.commandId(),
                        "mesWoId",
                        pick.woId(),
                        "consumptionIds",
                        consumptions,
                        "request",
                        pick,
                        "policyVersion",
                        appliedPolicyVersion));
        events.finish(command, Map.of("consumptionIds", consumptions));
        return consumptions;
    }

    /**
     * 把已经发生的工单消耗分配到具体产品；分配不再次扣仓库，净分配量不能超过实际消耗。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param consumptionId 已经发生的工单实际消耗记录。
     * @param quantity 本次从原实际消耗分配给产品单件的组件数量。
     * @param processId 实际发生用料或过站的工序标识。
     * @param operationRef 原作业记录的稳定引用。
     * @param actorId 本次业务操作的认证人员标识。
     * @param policy 来源业务已冻结的追踪策略。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long allocateToSn(
            String commandId,
            Long consumptionId,
            Long snId,
            BigDecimal quantity,
            Long processId,
            String operationRef,
            Long actorId,
            TrackingPolicy policy) {
        context.requireWrite();
        quantity(quantity);
        var command =
                events.command(
                        commandId,
                        "SN_CONSUMPTION",
                        Arrays.asList(
                                consumptionId,
                                snId,
                                quantity.stripTrailingZeros().toPlainString(),
                                processId,
                                operationRef,
                                actorId));
        if ("APPLIED".equals(command.get("status"))) {
            return number(events.result(command), "entryId");
        }
        Long id =
                allocate(
                        commandId,
                        1,
                        consumptionId,
                        snId,
                        quantity,
                        processId,
                        operationRef,
                        actorId,
                        policy);
        events.append(
                "SN_CONSUMPTION_ALLOCATED",
                "SN_CONSUMPTION",
                id,
                1,
                Map.of(
                        "entryId",
                        id,
                        "sourceConsumptionId",
                        consumptionId,
                        "localSnId",
                        snId,
                        "quantity",
                        quantity,
                        "processId",
                        processId,
                        "operationRef",
                        operationRef,
                        "policyVersion",
                        entryPolicyVersion(id)));
        events.finish(command, Map.of("entryId", id));
        return id;
    }

    private Long allocate(
            String commandId,
            long lineNo,
            Long consumptionId,
            Long snId,
            BigDecimal quantity,
            Long processId,
            String operationRef,
            Long actorId,
            TrackingPolicy policy) {
        return allocate(
                commandId,
                lineNo,
                consumptionId,
                snId,
                quantity,
                processId,
                operationRef,
                actorId,
                policy,
                false);
    }

    private Long allocate(
            String commandId,
            long lineNo,
            Long consumptionId,
            Long snId,
            BigDecimal quantity,
            Long processId,
            String operationRef,
            Long actorId,
            TrackingPolicy policy,
            boolean correction) {
        need(
                policy != null
                        && positive(actorId)
                        && positive(processId)
                        && operationRef != null
                        && !operationRef.isBlank()
                        && operationRef.length() <= 128,
                "组件分配必须有实际工序、操作及操作人");
        String tenant = context.tenant();
        var hint =
                one(
                        jdbc,
                        "SELECT wo_id FROM lt_wo_consumption WHERE tenant_id=? AND consumption_id=?"
                                + " AND del_flag='0'",
                        tenant,
                        consumptionId);
        need(hint != null, "实际消耗来源不存在");
        lockWo(id(hint.get("wo_id")), correction);
        var consumption =
                one(
                        jdbc,
                        "SELECT * FROM lt_wo_consumption WHERE tenant_id=? AND consumption_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant,
                        consumptionId);
        var sn =
                one(
                        jdbc,
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        snId);
        need(
                sn != null
                        && positive(id(sn.get("identity_id")))
                        && Objects.equals(id(sn.get("mes_wo_id")), id(consumption.get("wo_id")))
                        && allocatable(sn, correction),
                "组件分配必须属于实际在制工单SN");
        var allocation =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=? FOR"
                                + " UPDATE",
                        tenant,
                        consumption.get("source_allocation_id"));
        need(allocation != null, "逐件分配缺少真实来源分配");
        var source = sourceForAllocation(allocation);
        boolean serial = validateSource(source, events.payload(source), quantity);
        need(!policy.serialManaged() || serial, "单件组件必须使用真实正式身份来源");
        if (serial) {
            need(
                    BigDecimal.ONE.compareTo(qty(consumption.get("consumed_qty"))) == 0,
                    "单件实际消耗必须是整件1");
        }
        BigDecimal assigned =
                net(
                        jdbc.queryForList(
                                "SELECT quantity,entry_type FROM lt_sn_consumption WHERE"
                                        + " tenant_id=? AND wo_consumption_id=? FOR UPDATE",
                                tenant,
                                consumptionId));
        need(
                assigned.add(quantity).compareTo(qty(consumption.get("consumed_qty"))) <= 0,
                "SN组件分配合计超过实际消耗");
        Long identity = id(sn.get("identity_id")),
                material = id(consumption.get("material_id")),
                lot = id(consumption.get("lot_id"));
        jdbc.update(
                "INSERT INTO"
                    + " lt_sn_material_guard(tenant_id,identity_id,component_material_id,selected_lot_id,allow_mixed_lot,policy_version,version)"
                    + " VALUES(?,?,?,?,?,?,1) ON DUPLICATE KEY UPDATE version=version",
                tenant,
                identity,
                material,
                lot,
                policy.allowMixedLot(),
                policy.version());
        var guard =
                one(
                        jdbc,
                        "SELECT * FROM lt_sn_material_guard WHERE tenant_id=? AND identity_id=? AND"
                                + " component_material_id=? FOR UPDATE",
                        tenant,
                        identity,
                        material);
        need(guard != null, "SN组件策略守卫缺失");
        String frozenMix = text(guard.get("allow_mixed_lot"));
        Long frozenPolicyVersion = id(guard.get("policy_version"));
        need(
                frozenMix != null
                        && Set.of("true", "false", "1", "0")
                                .contains(frozenMix.toLowerCase(Locale.ROOT))
                        && positive(frozenPolicyVersion),
                "SN组件原策略或版本未知，禁止用当前配置补造历史");
        boolean allow = "true".equalsIgnoreCase(frozenMix) || "1".equals(frozenMix);
        if (!allow) {
            var facts =
                    jdbc.queryForList(
                            "SELECT component_lot_id,quantity,entry_type FROM lt_sn_consumption"
                                + " WHERE tenant_id=? AND identity_id=? AND component_material_id=?"
                                + " FOR UPDATE",
                            tenant,
                            identity,
                            material);
            var active = new HashMap<Long, BigDecimal>();
            for (var fact : facts) {
                active.merge(
                        id(fact.get("component_lot_id")),
                        "CONSUME".equals(fact.get("entry_type"))
                                ? qty(fact.get("quantity"))
                                : qty(fact.get("quantity")).negate(),
                        BigDecimal::add);
            }
            need(
                    active.entrySet().stream()
                            .allMatch(e -> e.getValue().signum() <= 0 || lot.equals(e.getKey())),
                    "该物料禁止同件混批");
        }
        // 只更新占用量与版本；首次策略快照继续约束该身份及组件。
        need(
                jdbc.update(
                                "UPDATE lt_sn_material_guard SET"
                                    + " selected_lot_id=?,version=version+1 WHERE tenant_id=? AND"
                                    + " identity_id=? AND component_material_id=? AND"
                                    + " policy_version=?",
                                lot,
                                tenant,
                                identity,
                                material,
                                frozenPolicyVersion)
                        == 1,
                "SN组件原策略守卫已变化");
        Long entry = IdWorker.getId();
        jdbc.update(
                "INSERT INTO"
                    + " lt_sn_consumption(entry_id,tenant_id,command_id,line_no,identity_id,local_sn_id,component_material_id,component_lot_id,quantity,wo_consumption_id,process_id,operation_ref,entry_type,policy_version,created_by)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'CONSUME',?,?)",
                entry,
                tenant,
                commandId,
                lineNo,
                identity,
                snId,
                material,
                lot,
                quantity,
                consumptionId,
                processId,
                operationRef,
                frozenPolicyVersion,
                actorId);
        return entry;
    }

    private Long entryPolicyVersion(Long entryId) {
        Long version =
                jdbc.queryForObject(
                        "SELECT policy_version FROM lt_sn_consumption WHERE tenant_id=? AND"
                                + " entry_id=?",
                        Long.class,
                        context.tenant(),
                        entryId);
        need(positive(version), "实际逐件分配缺少原策略版本");
        return version;
    }

    /**
     * 按原用料分录追加冲正，保留原记录，避免删除消耗事实。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param originalEntryId 本次冲正引用的原用料分录。
     * @param quantity 本次追加冲正的原用料数量。
     * @param reason 与原任务或原事实关联的明确操作原因。
     * @param actorId 本次业务操作的认证人员标识。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reverse(
            String commandId,
            Long originalEntryId,
            BigDecimal quantity,
            String reason,
            Long actorId) {
        context.requireWrite();
        quantity(quantity);
        need(
                positive(actorId) && reason != null && !reason.isBlank() && reason.length() <= 500,
                "Component reversal requires an authenticated operator and reason");
        var command =
                events.command(
                        commandId,
                        "SN_CONSUMPTION_REVERSAL",
                        List.of(
                                originalEntryId,
                                quantity.stripTrailingZeros().toPlainString(),
                                reason,
                                actorId));
        if ("APPLIED".equals(command.get("status"))) {
            return number(events.result(command), "entryId");
        }
        Long id = reverseEntry(commandId, 1, originalEntryId, quantity, actorId, false);
        events.append(
                "SN_CONSUMPTION_REVERSED",
                "SN_CONSUMPTION",
                id,
                1,
                Map.of(
                        "originalEntryId",
                        originalEntryId,
                        "entryId",
                        id,
                        "quantity",
                        quantity,
                        "reason",
                        reason));
        events.finish(command, Map.of("entryId", id));
        return id;
    }

    private Long reverseEntry(
            String commandId,
            long lineNo,
            Long originalEntryId,
            BigDecimal quantity,
            Long actorId,
            boolean correction) {
        String tenant = context.tenant();
        var hint =
                one(
                        jdbc,
                        "SELECT c.wo_id FROM lt_sn_consumption e JOIN lt_wo_consumption c ON"
                            + " CAST(c.tenant_id AS BINARY(80))=CAST(e.tenant_id AS BINARY(80)) AND"
                            + " c.consumption_id=e.wo_consumption_id WHERE e.tenant_id=? AND"
                            + " e.entry_id=?",
                        tenant,
                        originalEntryId);
        need(hint != null, "冲正原分录不存在");
        lockWo(id(hint.get("wo_id")), correction);
        var original =
                one(
                        jdbc,
                        "SELECT * FROM lt_sn_consumption WHERE tenant_id=? AND entry_id=? FOR"
                                + " UPDATE",
                        tenant,
                        originalEntryId);
        need(original != null && "CONSUME".equals(original.get("entry_type")), "冲正必须引用原消耗分录");
        var consumption =
                one(
                        jdbc,
                        "SELECT * FROM lt_wo_consumption WHERE tenant_id=? AND consumption_id=? FOR"
                                + " UPDATE",
                        tenant,
                        original.get("wo_consumption_id"));
        need(consumption != null, "冲正缺少实际消耗来源");
        var allocation =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=? FOR"
                                + " UPDATE",
                        tenant,
                        consumption.get("source_allocation_id"));
        need(allocation != null, "冲正缺少真实来源分配");
        var source = sourceForAllocation(allocation);
        validateSource(source, events.payload(source), quantity);
        var sn =
                one(
                        jdbc,
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        original.get("local_sn_id"));
        need(
                sn != null
                        && allocatable(sn, correction)
                        && Objects.equals(id(sn.get("mes_wo_id")), id(consumption.get("wo_id")))
                        && Objects.equals(
                                id(sn.get("identity_id")), id(original.get("identity_id"))),
                "已冻结交付的SN必须经返工修订，不可改写原组件账");
        one(
                jdbc,
                "SELECT * FROM lt_sn_material_guard WHERE tenant_id=? AND identity_id=? AND"
                        + " component_material_id=? FOR UPDATE",
                tenant,
                original.get("identity_id"),
                original.get("component_material_id"));
        BigDecimal reversed =
                sum(
                        jdbc.queryForList(
                                "SELECT quantity FROM lt_sn_consumption WHERE tenant_id=? AND"
                                    + " original_entry_id=? AND entry_type='REVERSE' FOR UPDATE",
                                tenant,
                                originalEntryId),
                        "quantity");
        need(reversed.add(quantity).compareTo(qty(original.get("quantity"))) <= 0, "冲正数量超过原组件实耗");
        Long id = IdWorker.getId();
        jdbc.update(
                "INSERT INTO"
                    + " lt_sn_consumption(entry_id,tenant_id,command_id,line_no,identity_id,local_sn_id,component_material_id,component_lot_id,quantity,wo_consumption_id,process_id,operation_ref,entry_type,original_entry_id,policy_version,created_by)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'REVERSE',?,?,?)",
                id,
                tenant,
                commandId,
                lineNo,
                original.get("identity_id"),
                original.get("local_sn_id"),
                original.get("component_material_id"),
                original.get("component_lot_id"),
                quantity,
                original.get("wo_consumption_id"),
                original.get("process_id"),
                "reversal:" + originalEntryId,
                originalEntryId,
                original.get("policy_version"),
                actorId);
        need(
                jdbc.update(
                                "UPDATE lt_sn_material_guard SET version=version+1 WHERE"
                                    + " tenant_id=? AND identity_id=? AND component_material_id=?",
                                tenant,
                                original.get("identity_id"),
                                original.get("component_material_id"))
                        == 1,
                "Original component guard is missing");
        return id;
    }

    /**
     * 复用包含库存事实版本及策略的不可变证据，单件正式编码不能从批次码推导。
     */
    private Map<String, Object> sourceForAllocation(Map<String, Object> allocation) {
        var proof = readProof(allocation);
        var source = events.source(proof.path("sourceEventId").asText());
        need(
                proof.path("source").equals(events.payload(source))
                        && Objects.equals(
                                number(proof, "sourceVersion"), events.sourceVersion(source))
                        && proof.path("policy")
                                .equals(events.read(events.encode(events.sourcePolicy(source)))),
                "批次分配与原ERP凭据不一致");
        return source;
    }

    private boolean validateSource(
            Map<String, Object> source, JsonNode evidence, BigDecimal quantity) {
        var policy = events.sourcePolicy(source);
        events.sourceVersion(source);
        Long batch = number(evidence, "erpBatchId"), identity = number(evidence, "identityId");
        need(!policy.batchManaged() || positive(batch), "批次管理物料缺少真实ERP批次");
        boolean component = !"ERP_PRODUCTION_LOT_AUTHORIZED".equals(source.get("event_type"));
        boolean serial = component && (policy.serialManaged() || identity != null || batch == null);
        if (serial) {
            need(
                    positive(identity)
                            && positive(number(evidence, "sourceStockId"))
                            && BigDecimal.ONE.compareTo(decimal(evidence, "quantity")) == 0
                            && BigDecimal.ONE.compareTo(quantity) == 0,
                    "单件组件来源、实际消耗、分配和冲正必须为整件1");
        }
        return serial;
    }

    private boolean allocatable(Map<String, Object> sn, boolean correction) {
        return "NONE".equals(sn.get("handoff_status"))
                && (correction
                        ? Set.of("COMPLETED", "SCRAPPED")
                                .contains(Objects.toString(sn.get("sn_status"), ""))
                        : "IN_PRODUCTION".equals(sn.get("sn_status")));
    }

    private Map<String, Object> lockWo(Long woId) {
        return lockWo(woId, false);
    }

    private Map<String, Object> lockWo(Long woId, boolean correction) {
        var wo =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        context.tenant(),
                        woId);
        need(
                wo != null
                        && (correction
                                ? "COMPLETED".equals(wo.get("wo_status"))
                                : Set.of("RELEASED", "IN_PROGRESS", "PARTIAL_COMPLETED")
                                        .contains(text(wo.get("wo_status")))),
                "实际工单不存在或已不允许物料消耗");
        return wo;
    }

    private JsonNode readProof(Map<String, Object> allocation) {
        String proof = text(allocation.get("evidence_json"));
        need(TraceHashes.sha256(proof).equals(allocation.get("evidence_hash")), "批次分配来源摘要不一致");
        try {
            return json.readTree(proof);
        } catch (Exception e) {
            throw new IllegalStateException("批次分配凭据无效");
        }
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String column) {
        return rows.stream().map(r -> qty(r.get(column))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal net(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(
                        r ->
                                "CONSUME".equals(r.get("entry_type"))
                                        ? qty(r.get("quantity"))
                                        : qty(r.get("quantity")).negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal decimal(JsonNode node, String key) {
        try {
            return new BigDecimal(node.path(key).asText());
        } catch (Exception e) {
            throw new IllegalArgumentException("来源数量无效");
        }
    }
}
