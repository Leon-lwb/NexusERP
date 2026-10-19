package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.mapper.*;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.alibaba.fastjson2.JSON;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/**
 * 仅消费MES事务外已读取的冻结实际分配；不查询MES，不计算BOM，不重新拣货。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmTraceMaterialConsumptionService {
    private final WmSerialInventoryRepository store;
    private final WmSerialInventoryService serial;
    private final WmProductionRecptWriteGuard source;
    private final WmProductionIssueMapper issues;
    private final WmProductionIssueLineMapper lines;
    private final IWmStockOperationService stockOperations;
    private final AutoCodeUtil codes;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Set<Long> consume(
            CompletionManifest manifest, List<CompletionMaterialAllocation> frozen) {
        store.requireWrite();
        var wo = source.lockWorkOrder(manifest.erpWoId());
        need(store.tenant().equals(manifest.tenantId()), "实际消耗清单租户不符");
        Set<Long> actualMaterials = new HashSet<>(),
                ids = new HashSet<>(),
                serials = new HashSet<>();
        Map<Long, List<CompletionMaterialAllocation>> reservations = new TreeMap<>();
        Map<String, BigDecimal> usedSources = new HashMap<>();
        for (var item : frozen) {
            need(
                    item != null
                            && item.allocationId() != null
                            && ids.add(item.allocationId())
                            && item.quantity() != null
                            && item.quantity().signum() > 0
                            && item.quantity().stripTrailingZeros().scale() <= 4
                            && item.sourceVersion() > 0
                            && item.evidenceHash() != null
                            && item.evidenceHash().matches("[0-9a-f]{64}")
                            && item.sourceEventId() != null,
                    "实际消耗分配身份、数量、版本或证据摘要缺失");
            var policy = store.policy(item.materialId());
            if (policy.stage() != TrackingStage.STRICT
                    || !policy.batchManaged() && !policy.serialManaged()) {
                continue;
            }
            actualMaterials.add(item.materialId());
            if (policy.serialManaged()) {
                need(
                        item.identityId() != null
                                && serials.add(item.identityId())
                                && BigDecimal.ONE.compareTo(item.quantity()) == 0,
                        "实际组件SN不能重复或以数量替代身份");
            }
            need(
                    (item.reservationId() != null) != (item.postingGroupId() != null),
                    "实际分配必须且只能引用原预留或真实预领料组");
            var event =
                    store.row(
                            "SELECT * FROM wm_trace_outbox WHERE tenant_id=? AND event_id=? AND"
                                    + " source_module='ERP' FOR UPDATE",
                            store.tenant(),
                            item.sourceEventId());
            String payload = event.get("payload").toString();
            need(TraceHashes.sha256(payload).equals(event.get("content_hash")), "原ERP来源事件摘要不符");
            var proof = JSON.parseObject(payload);
            need(
                    Objects.equals(manifest.mesWoId(), proof.getLong("mesWoId"))
                            && Objects.equals(manifest.erpWoId(), proof.getLong("erpWoId"))
                            && Objects.equals(item.materialId(), proof.getLong("materialId"))
                            && Objects.equals(item.erpBatchId(), proof.getLong("erpBatchId")),
                    "实际消耗不属于原工单/物料/ERP批次授权");
            if (policy.serialManaged()) {
                need(
                        Objects.equals(item.identityId(), proof.getLong("identityId"))
                                && Objects.equals(
                                        item.sourceStockId(), proof.getLong("sourceStockId")),
                        "实际组件SN或库存不能借同组其他单件事件替换");
            }
            BigDecimal used =
                    usedSources.merge(item.sourceEventId(), item.quantity(), BigDecimal::add);
            need(used.compareTo(proof.getBigDecimal("quantity")) <= 0, "实际消耗合计超过原ERP授权数量");
            if (item.reservationId() != null) {
                need(
                        "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED".equals(event.get("event_type"))
                                && Objects.equals(
                                        item.reservationId(), proof.getLong("reservationId"))
                                && Objects.equals(
                                        item.reservationLineId(),
                                        proof.getLong("reservationLineId")),
                        "实际倒冲未引用原真实预留授权");
                reservations
                        .computeIfAbsent(item.reservationLineId(), k -> new ArrayList<>())
                        .add(item);
            } else {
                need(
                        "ERP_MATERIAL_SOURCE_POSTED".equals(event.get("event_type"))
                                && Objects.equals(
                                        item.postingGroupId(), proof.getLong("postingGroupId")),
                        "实际预领料缺少原已过账来源");
                var group =
                        store.row(
                                "SELECT g.*,h.wo_id,l.requirement_detail_id FROM"
                                    + " wm_trace_posting_group g JOIN wm_production_issue h ON"
                                    + " h.tenant_id=g.tenant_id AND h.issue_id=g.source_doc_id JOIN"
                                    + " wm_production_issue_line l ON l.tenant_id=g.tenant_id AND"
                                    + " l.issue_id=h.issue_id AND l.line_id=g.source_doc_line_id"
                                    + " WHERE g.tenant_id=? AND g.posting_group_id=? AND"
                                    + " g.source_doc_type='WM_PRODUCTION_ISSUE' AND"
                                    + " g.status='POSTED' AND h.del_flag='0' AND l.del_flag='0' FOR"
                                    + " UPDATE",
                                store.tenant(),
                                item.postingGroupId());
                need(
                        manifest.erpWoId()
                                .equals(WmSerialInventoryRepository.id(group.get("wo_id"))),
                        "预领料组不属于原工单");
                need(
                        Objects.equals(
                                item.sourceVersion(),
                                WmSerialInventoryRepository.id(group.get("detail_version"))),
                        "冻结实际分配的原预领料事实版本不符");
                var txs =
                        store.jdbc.queryForList(
                                "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND"
                                    + " posting_group_id=? AND tx_flag=-1 AND material_id=? AND"
                                    + " batch_id <=> ? AND sn_id <=> ? AND del_flag='0' FOR UPDATE",
                                store.tenant(),
                                item.postingGroupId(),
                                item.materialId(),
                                item.erpBatchId(),
                                item.identityId());
                need(
                        !txs.isEmpty()
                                && (!policy.serialManaged()
                                        || txs.size() == 1
                                                && Objects.equals(
                                                        item.sourceStockId(),
                                                        WmSerialInventoryRepository.id(
                                                                txs.get(0)
                                                                        .get(
                                                                                "material_stock_id")))),
                        "实际消耗正式身份不属于原预领料实物");
                BigDecimal available = BigDecimal.ZERO;
                for (var tx : txs) {
                    BigDecimal corrected =
                            store.jdbc.queryForObject(
                                    "SELECT COALESCE(SUM(tx_quantity),0) FROM wm_material_stock_tx"
                                        + " WHERE tenant_id=? AND related_tx_id=? AND (tx_flag=1 OR"
                                        + " tx_type IN ('SCRAP','ISSUED_SCRAP')) AND del_flag='0'"
                                        + " FOR UPDATE",
                                    BigDecimal.class,
                                    store.tenant(),
                                    tx.get("tx_id"));
                    need(corrected != null && corrected.signum() >= 0, "原预领料冲正/退料证明缺失");
                    available =
                            available.add(
                                    WmSerialInventoryRepository.decimal(tx.get("tx_quantity"))
                                            .subtract(corrected));
                }
                need(used.compareTo(available) <= 0, "原预领料已经退回或报废，不能再消费旧来源");
                Long requirement =
                        WmSerialInventoryRepository.id(group.get("requirement_detail_id"));
                if (requirement != null) {
                    WmSerialInventoryRepository.one(
                            store.jdbc.update(
                                    "UPDATE prd_material_requirement_detail SET"
                                        + " actual_consumed_qty=IFNULL(actual_consumed_qty,0)+?"
                                        + " WHERE tenant_id=? AND detail_id=? AND material_id=? AND"
                                        + " del_flag='0'",
                                    item.quantity(),
                                    store.tenant(),
                                    requirement,
                                    item.materialId()));
                }
                save(manifest, item, item.postingGroupId());
            }
        }
        if (wo.getPlanId() != null) {
            var requirements =
                    store.jdbc.queryForList(
                            "SELECT d.material_id FROM prd_material_requirement_detail d JOIN"
                                + " prd_material_requirement h ON h.tenant_id=d.tenant_id AND"
                                + " h.requirement_id=d.requirement_id WHERE h.tenant_id=? AND"
                                + " h.plan_id=? AND d.required_quantity>0 AND h.del_flag='0' AND"
                                + " d.del_flag='0' ORDER BY d.material_id FOR UPDATE",
                            store.tenant(),
                            wo.getPlanId());
            for (var required : requirements) {
                Long material = WmSerialInventoryRepository.id(required.get("material_id"));
                if (serial.isStrictTracked(material)) {
                    need(actualMaterials.contains(material), "原物料需求的STRICT追踪组件缺少冻结实际消耗，不能按BOM补造");
                }
            }
        }
        for (var entry : reservations.entrySet()) {
            var sample = entry.getValue().get(0);
            var header =
                    store.row(
                            "SELECT * FROM wm_trace_material_reservation WHERE tenant_id=? AND"
                                    + " reservation_id=? FOR UPDATE",
                            store.tenant(),
                            sample.reservationId());
            need(
                    manifest.erpWoId()
                                    .equals(WmSerialInventoryRepository.id(header.get("erp_wo_id")))
                            && manifest.mesWoId()
                                    .equals(WmSerialInventoryRepository.id(header.get("mes_wo_id")))
                            && sample.materialId()
                                    .equals(
                                            WmSerialInventoryRepository.id(
                                                    header.get("material_id")))
                            && "RESERVED".equals(header.get("status")),
                    "原预留归属或状态不符");
            var row =
                    store.row(
                            "SELECT * FROM wm_trace_material_reservation_line WHERE tenant_id=? AND"
                                    + " reservation_id=? AND reservation_line_id=? FOR UPDATE",
                            store.tenant(),
                            sample.reservationId(),
                            entry.getKey());
            BigDecimal
                    qty =
                            entry.getValue().stream()
                                    .map(CompletionMaterialAllocation::quantity)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                    remaining =
                            WmSerialInventoryRepository.decimal(row.get("quantity"))
                                    .subtract(
                                            WmSerialInventoryRepository.decimal(
                                                    row.get("consumed_quantity")));
            need(
                    qty.compareTo(remaining) <= 0
                            && WmSerialInventoryRepository.decimal(row.get("consumed_quantity"))
                                            .signum()
                                    == 0,
                    "原预留已消费或实际数量超过预留");
            for (var item : entry.getValue()) {
                need(
                        Objects.equals(sample.reservationId(), item.reservationId())
                                && Objects.equals(
                                        item.sourceVersion(),
                                        WmSerialInventoryRepository.id(row.get("version")))
                                && Objects.equals(
                                        item.sourceStockId(),
                                        WmSerialInventoryRepository.id(row.get("source_stock_id")))
                                && Objects.equals(
                                        item.identityId(),
                                        WmSerialInventoryRepository.id(row.get("identity_id")))
                                && Objects.equals(
                                        item.erpBatchId(),
                                        WmSerialInventoryRepository.id(row.get("erp_batch_id"))),
                        "实际消耗改换了原预留库存、SN或批次");
            }
            var policy = store.policy(sample.materialId());
            need(
                    policy.version()
                            .equals(WmSerialInventoryRepository.id(header.get("policy_version"))),
                    "原预留策略版本已改变");
            var stock = store.stock(sample.sourceStockId());
            need(
                    sample.materialId().equals(stock.getMaterialId())
                            && Objects.equals(sample.identityId(), stock.getSnId())
                            && Objects.equals(sample.erpBatchId(), stock.getBatchId())
                            && (row.get("valuation_group_id") == null
                                    || Objects.equals(
                                            WmSerialInventoryRepository.id(
                                                    row.get("valuation_group_id")),
                                            stock.getValuationGroupId())),
                    "原实际预留库存的物料、正式SN、批次或计价组发生漂移");
            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "UPDATE wm_material_stock SET"
                                + " reserved_quantity=reserved_quantity-?,version=version+1 WHERE"
                                + " tenant_id=? AND stock_id=? AND reserved_quantity>=? AND"
                                + " stock_quantity>=? AND del_flag='0'",
                            qty,
                            store.tenant(),
                            stock.getStockId(),
                            qty,
                            qty));
            WmProductionIssue issue = new WmProductionIssue();
            issue.setWoId(wo.getWoId());
            issue.setWoNo(wo.getWoNo());
            issue.setIssueCode(codes.genSerialCode("WM_PROD_ISSUE_CODE", null));
            issue.setStatus("BACKFLUSH");
            issue.setIssueType("BACKFLUSH");
            issue.setIssueDate(new Date());
            WmSerialInventoryRepository.one(issues.insert(issue));
            WmProductionIssueLine line = new WmProductionIssueLine();
            line.setIssueId(issue.getIssueId());
            line.setMaterialId(stock.getMaterialId());
            line.setMaterialCode(stock.getMaterialCode());
            line.setMaterialName(stock.getMaterialName());
            line.setBaseUomId(stock.getBaseUomId());
            line.setQuantity(qty);
            line.setCurrentQty(qty);
            line.setWarehouseId(stock.getWarehouseId());
            line.setBatchId(stock.getBatchId());
            line.setLocationId(stock.getLocationId());
            line.setRequirementDetailId(
                    WmSerialInventoryRepository.id(header.get("requirement_detail_id")));
            WmSerialInventoryRepository.one(lines.insert(line));
            var request =
                    WmSerialPostingRequest.out(
                            "WM_PRODUCTION_ISSUE",
                            issue.getIssueId(),
                            line.getLineId(),
                            "PRODUCTION_ISSUE",
                            stock.getMaterialId(),
                            qty,
                            stock.getWarehouseId(),
                            stock.getLocationId(),
                            stock.getBatchId());
            if (policy.serialManaged()) {
                serial.freeze(
                        request,
                        "MES_ACTUAL/"
                                + manifest.manifestId()
                                + "/"
                                + manifest.revision()
                                + "/"
                                + entry.getKey(),
                        1,
                        policy.version(),
                        List.of(
                                new SnItem(
                                        stock.getSnId(),
                                        stock.getStockId(),
                                        null,
                                        null,
                                        stock.getBatchId())));
            }
            AllocatedStock picked = new AllocatedStock();
            picked.setStockId(stock.getStockId());
            picked.setDeductQty(qty);
            stockOperations.postProductionIssueAllocated(
                    issue.getIssueId(), line.getLineId(), List.of(picked), new Date());
            Long group =
                    policy.serialManaged()
                            ? store.group(request).id()
                            : store.bindActualLegacyIssue(request);
            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "UPDATE wm_trace_material_reservation_line SET"
                                    + " consumed_quantity=?,version=version+1 WHERE tenant_id=? AND"
                                    + " reservation_line_id=? AND consumed_quantity=0",
                            qty,
                            store.tenant(),
                            entry.getKey()));

            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "UPDATE prd_material_requirement_detail SET"
                                + " actual_consumed_qty=IFNULL(actual_consumed_qty,0)+?,issued_quantity=IFNULL(issued_quantity,0)+?"
                                + " WHERE tenant_id=? AND detail_id=? AND material_id=? AND"
                                + " del_flag='0'",
                            qty,
                            qty,
                            store.tenant(),
                            header.get("requirement_detail_id"),
                            sample.materialId()));
            store.jdbc.update(
                    "UPDATE wm_production_issue SET status='COMPLETED' WHERE tenant_id=? AND"
                            + " issue_id=?",
                    store.tenant(),
                    issue.getIssueId());
            for (var item : entry.getValue()) {
                save(manifest, item, group);
            }
        }
        var unused =
                store.jdbc.queryForList(
                        "SELECT l.* FROM wm_trace_material_reservation_line l JOIN"
                                + " wm_trace_material_reservation h ON h.tenant_id=l.tenant_id AND"
                                + " h.reservation_id=l.reservation_id WHERE h.tenant_id=? AND"
                                + " h.erp_wo_id=? AND h.status='RESERVED' ORDER BY"
                                + " l.reservation_line_id FOR UPDATE",
                        store.tenant(),
                        manifest.erpWoId());
        for (var line : unused) {
            BigDecimal remaining =
                    WmSerialInventoryRepository.decimal(line.get("quantity"))
                            .subtract(
                                    WmSerialInventoryRepository.decimal(
                                            line.get("consumed_quantity")));
            if (remaining.signum() > 0) {
                WmSerialInventoryRepository.one(
                        store.jdbc.update(
                                "UPDATE wm_material_stock SET"
                                    + " reserved_quantity=reserved_quantity-?,version=version+1"
                                    + " WHERE tenant_id=? AND stock_id=? AND reserved_quantity>=?",
                                remaining,
                                store.tenant(),
                                line.get("source_stock_id"),
                                remaining));
            }
        }
        store.jdbc.update(
                "UPDATE wm_trace_material_reservation SET status='CONSUMED' WHERE tenant_id=? AND"
                        + " erp_wo_id=? AND status='RESERVED'",
                store.tenant(),
                manifest.erpWoId());
        return actualMaterials;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void verifyReplay(CompletionManifest m, List<CompletionMaterialAllocation> allocations) {
        store.requireWrite();
        need(
                m != null && store.tenant().equals(m.tenantId()) && allocations != null,
                "实际消耗重放租户或冻结清单缺失");
        var stored =
                store.jdbc.queryForList(
                        "SELECT * FROM wm_trace_material_consumption WHERE tenant_id=? AND"
                            + " manifest_id=? AND manifest_revision=? ORDER BY allocation_id FOR"
                            + " UPDATE",
                        store.tenant(),
                        m.manifestId(),
                        m.revision());
        // 原库存预留与过账引用不因后续策略变化而改变，历史凭据保持原分类。
        need(allocations.stream().allMatch(Objects::nonNull), "重放实际分配存在空行");
        var expected =
                allocations.stream()
                        .filter(a -> a.reservationId() != null || a.postingGroupId() != null)
                        .toList();
        need(
                expected.stream()
                                .allMatch(
                                        a ->
                                                (a.reservationId() != null)
                                                        != (a.postingGroupId() != null))
                        && expected.stream()
                                        .map(CompletionMaterialAllocation::allocationId)
                                        .distinct()
                                        .count()
                                == expected.size()
                        && stored.size() == expected.size(),
                "重放实际消耗分配全集不符");
        for (var a : expected) {
            var row =
                    stored.stream()
                            .filter(
                                    r ->
                                            Objects.equals(
                                                    a.allocationId(),
                                                    WmSerialInventoryRepository.id(
                                                            r.get("allocation_id"))))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("原实际分配缺失"));
            need(
                    Objects.equals(
                                    m.erpWoId(),
                                    WmSerialInventoryRepository.id(row.get("erp_wo_id")))
                            && Objects.equals(
                                    a.materialId(),
                                    WmSerialInventoryRepository.id(row.get("material_id")))
                            && Objects.equals(
                                    a.identityId(),
                                    WmSerialInventoryRepository.id(row.get("identity_id")))
                            && Objects.equals(
                                    a.sourceStockId(),
                                    WmSerialInventoryRepository.id(row.get("source_stock_id")))
                            && Objects.equals(
                                    a.reservationId(),
                                    WmSerialInventoryRepository.id(row.get("reservation_id")))
                            && Objects.equals(
                                    a.reservationLineId(),
                                    WmSerialInventoryRepository.id(row.get("reservation_line_id")))
                            && (a.postingGroupId() == null
                                    || Objects.equals(
                                            a.postingGroupId(),
                                            WmSerialInventoryRepository.id(
                                                    row.get("posting_group_id"))))
                            && Objects.equals(
                                    a.erpBatchId(),
                                    WmSerialInventoryRepository.id(row.get("erp_batch_id")))
                            && a.quantity()
                                            .compareTo(
                                                    WmSerialInventoryRepository.decimal(
                                                            row.get("quantity")))
                                    == 0
                            && a.sourceVersion()
                                    == WmSerialInventoryRepository.id(row.get("source_version"))
                            && a.evidenceHash().equals(row.get("evidence_hash"))
                            && a.sourceEventId().equals(row.get("source_event_id")),
                    "重放原实际分配的来源或数量已改变");
        }
    }

    private void save(CompletionManifest m, CompletionMaterialAllocation a, Long group) {
        WmSerialInventoryRepository.one(
                store.jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_material_consumption(consumption_id,tenant_id,manifest_id,manifest_revision,allocation_id,erp_wo_id,material_id,reservation_id,reservation_line_id,posting_group_id,identity_id,source_stock_id,erp_batch_id,quantity,source_version,evidence_hash,source_event_id)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        IdWorker.getId(),
                        store.tenant(),
                        m.manifestId(),
                        m.revision(),
                        a.allocationId(),
                        m.erpWoId(),
                        a.materialId(),
                        a.reservationId(),
                        a.reservationLineId(),
                        group,
                        a.identityId(),
                        a.sourceStockId(),
                        a.erpBatchId(),
                        a.quantity(),
                        a.sourceVersion(),
                        a.evidenceHash(),
                        a.sourceEventId()));
    }
}
