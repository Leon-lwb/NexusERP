package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/**
 * batch-only沿用原单批次收货与计价；新组绑定整个冻结清单及全部原流水，不读取当前库存位置。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmBatchProductionReceiptProofService {
    private final WmSerialInventoryRepository store;
    private final WmProductionRecptMapper receipts;

    public static SortedMap<Long, BigDecimal> frozenBatches(
            CompletionManifest manifest, List<CompletionManifestLine> lines) {
        SortedMap<Long, BigDecimal> result = new TreeMap<>();
        for (var line : lines) {
            need(line.identityId() == null, "batch-only清单不得混入SN行");
            if ("GOOD".equals(line.result())) {
                need(
                        line.batchId() != null
                                && line.batchId() > 0
                                && line.quantity() != null
                                && line.quantity().signum() > 0
                                && line.quantity().stripTrailingZeros().scale() <= 4,
                        "良品子lot缺少原ERP批次或数量无效");
                result.merge(line.batchId(), line.quantity(), BigDecimal::add);
            }
        }
        need(
                result.size() == 1
                        && result.values().stream()
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .compareTo(manifest.goodQty())
                                == 0,
                "原batch-only工单为单批次收货来源：多个MES子lot须使用同一授权ERP成品批次");
        return result;
    }

    static String batchHash(Map<Long, BigDecimal> batches) {
        var canonical = new TreeMap<Long, String>();
        batches.forEach((id, qty) -> canonical.put(id, qty.stripTrailingZeros().toPlainString()));
        return TraceHashes.fingerprint(canonical);
    }

    private Map<String, Object> group(Long wo) {
        var groups =
                store.jdbc.queryForList(
                        "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                            + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND"
                            + " tx_type='PRODUCTION_IN_BATCH' ORDER BY detail_version FOR UPDATE",
                        store.tenant(),
                        wo);
        need(groups.size() <= 1, "原batch-only工单存在重复版本化收货组");
        return groups.isEmpty() ? null : groups.get(0);
    }

    public boolean exists(Long wo) {
        return group(wo) != null;
    }

    private List<WmMaterialStockTx> transactions(Long wo) {
        return store.jdbc.query(
                "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND"
                        + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? ORDER BY tx_id FOR"
                        + " UPDATE",
                BeanPropertyRowMapper.newInstance(WmMaterialStockTx.class),
                store.tenant(),
                wo);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long record(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            PrdWorkOrder wo,
            Long receiptId,
            long policyVersion) {
        store.requireWrite();
        var batches = frozenBatches(manifest, lines);
        var receipt = receipts.selectById(receiptId);
        need(
                receipt != null
                        && "EXECUTED".equals(receipt.getStatus())
                        && manifest.erpWoId().equals(receipt.getWoId())
                        && manifest.materialId().equals(receipt.getMaterialId())
                        && manifest.goodQty().compareTo(receipt.getQuantity()) == 0,
                "原batch-only收货未完整执行");
        if (exists(wo.getWoId())) {
            return replay(manifest, lines, wo, receipt);
        }
        var facts = transactions(wo.getWoId());
        var actual = verifyFacts(wo, receipt, facts, null);
        need(batchHash(batches).equals(batchHash(actual)), "实际生产收货流水未覆盖冻结GOOD子lot全集");
        long id = IdWorker.getId();
        String hash = batchHash(batches), command = command(manifest);
        BigDecimal amount =
                facts.stream()
                        .map(WmMaterialStockTx::getTxAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        var request = request(wo, receipt);
        String sourceHash = store.sourceHash(request);
        String fingerprint =
                TraceHashes.fingerprint(2, policyVersion, receiptId, command, hash, sourceHash);
        WmSerialInventoryRepository.one(
                store.jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_posting_group(posting_group_id,tenant_id,command_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type,material_id,quantity,amount,fingerprint,content_hash,source_hash,policy_version,status)"
                            + " VALUES(?,?,?,'PRD_WORK_ORDER',?,0,?,'PRODUCTION_IN_BATCH',?,?,?,?,?,?,?,'POSTED')",
                        id,
                        store.tenant(),
                        command,
                        wo.getWoId(),
                        manifest.revision(),
                        wo.getMaterialId(),
                        manifest.goodQty(),
                        amount,
                        fingerprint,
                        hash,
                        sourceHash,
                        policyVersion));
        for (var fact : facts) {
            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "UPDATE wm_material_stock_tx SET posting_group_id=? WHERE tenant_id=?"
                                + " AND tx_id=? AND source_doc_type='PRD_WORK_ORDER' AND"
                                + " source_doc_id=? AND posting_group_id IS NULL AND sn_id IS NULL"
                                + " AND tracking_version=1 AND del_flag='0'",
                            id,
                            store.tenant(),
                            fact.getTxId(),
                            wo.getWoId()));
        }
        verify(wo, receipt, transactions(wo.getWoId()));
        store.enqueue(
                id,
                store.encode(
                        Map.of(
                                "postingGroupId",
                                Long.toString(id),
                                "receiptId",
                                receiptId.toString(),
                                "manifestId",
                                manifest.manifestId().toString(),
                                "manifestHash",
                                manifest.contentHash(),
                                "proofVersion",
                                "2",
                                "trackingVersion",
                                "1",
                                "batchQuantities",
                                batches)),
                hash);
        return id;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long replay(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            PrdWorkOrder wo,
            WmProductionRecpt receipt) {
        var batches = frozenBatches(manifest, lines);
        var original = group(wo.getWoId());
        need(
                original != null
                        && command(manifest).equals(original.get("command_id"))
                        && batchHash(batches).equals(original.get("content_hash")),
                "原batch-only收货不属于同一完整冻结清单");
        return verify(wo, receipt, transactions(wo.getWoId()));
    }

    public Long verify(PrdWorkOrder wo, WmProductionRecpt receipt, List<WmMaterialStockTx> facts) {
        var original = group(wo.getWoId());
        need(original != null && "POSTED".equals(original.get("status")), "batch-only原过账组不完整");
        Long id = WmSerialInventoryRepository.id(original.get("posting_group_id")),
                policy = WmSerialInventoryRepository.id(original.get("policy_version"));
        need(
                id != null
                        && id > 0
                        && policy != null
                        && policy > 0
                        && store.tenant().equals(original.get("tenant_id"))
                        && "PRD_WORK_ORDER".equals(original.get("source_doc_type"))
                        && wo.getWoId()
                                .equals(
                                        WmSerialInventoryRepository.id(
                                                original.get("source_doc_id")))
                        && Long.valueOf(0)
                                .equals(
                                        WmSerialInventoryRepository.id(
                                                original.get("source_doc_line_id")))
                        && "PRODUCTION_IN_BATCH".equals(original.get("tx_type"))
                        && wo.getMaterialId()
                                .equals(WmSerialInventoryRepository.id(original.get("material_id")))
                        && store.sourceHash(request(wo, receipt))
                                .equals(original.get("source_hash")),
                "batch-only过账组不属于原始工单、成品或冻结来源摘要");
        var batches = verifyFacts(wo, receipt, facts, id);
        BigDecimal quantity = batches.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                amount =
                        facts.stream()
                                .map(WmMaterialStockTx::getTxAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String[] command = String.valueOf(original.get("command_id")).split("/", -1);
        need(
                command.length == 4
                        && "MES_FINAL".equals(command[0])
                        && command[1].matches("[1-9][0-9]*")
                        && command[2].matches("[1-9][0-9]*")
                        && command[3].matches("[0-9a-f]{64}")
                        && command[2].equals(String.valueOf(original.get("detail_version")))
                        && batchHash(batches).equals(original.get("content_hash"))
                        && quantity.compareTo(
                                        WmSerialInventoryRepository.decimal(
                                                original.get("quantity")))
                                == 0
                        && amount.compareTo(
                                        WmSerialInventoryRepository.decimal(original.get("amount")))
                                == 0
                        && TraceHashes.fingerprint(
                                        2,
                                        policy,
                                        receipt.getRecptId(),
                                        original.get("command_id"),
                                        batchHash(batches),
                                        original.get("source_hash"))
                                .equals(original.get("fingerprint")),
                "batch-only原数量、成本、版本或完整批次摘要不符");
        for (Long batch : batches.keySet()) {
            var source =
                    store.row(
                            "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            store.tenant(),
                            batch);
            need(
                    "PRD_WORK_ORDER".equals(source.get("source_doc_type"))
                            && wo.getWoId()
                                    .equals(
                                            WmSerialInventoryRepository.id(
                                                    source.get("source_doc_id")))
                            && wo.getMaterialId()
                                    .equals(
                                            WmSerialInventoryRepository.id(
                                                    source.get("material_id")))
                            && Objects.equals(wo.getWoNo(), source.get("source_doc_no"))
                            && Objects.equals(receipt.getBatchCode(), source.get("batch_code"))
                            && receipt.getWarehouseId()
                                    .equals(
                                            WmSerialInventoryRepository.id(
                                                    source.get("warehouse_id")))
                            && Set.of("ACTIVE", "EXPIRED", "LOCKED", "INACTIVE", "CLOSED")
                                    .contains(String.valueOf(source.get("status"))),
                    "原批次不是该工单的真实成品来源");
            var ledger =
                    store.row(
                            "SELECT COALESCE(SUM(CASE WHEN tx_flag=1 THEN tx_quantity ELSE 0"
                                + " END),0) AS received,COALESCE(SUM(CASE WHEN tx_flag=-1 THEN"
                                + " tx_quantity ELSE 0 END),0) AS issued FROM wm_material_stock_tx"
                                + " WHERE tenant_id=? AND batch_id=? AND tx_type<>'TRANSFER' AND"
                                + " del_flag='0'",
                            store.tenant(),
                            batch);
            need(
                    WmSerialInventoryRepository.decimal(source.get("received_qty"))
                                            .compareTo(
                                                    WmSerialInventoryRepository.decimal(
                                                            ledger.get("received")))
                                    == 0
                            && WmSerialInventoryRepository.decimal(source.get("issued_qty"))
                                            .compareTo(
                                                    WmSerialInventoryRepository.decimal(
                                                            ledger.get("issued")))
                                    == 0,
                    "原生产批次累计量与完整库存流水不守恒");
        }
        return id;
    }

    private SortedMap<Long, BigDecimal> verifyFacts(
            PrdWorkOrder wo, WmProductionRecpt receipt, List<WmMaterialStockTx> facts, Long group) {
        need(facts != null && !facts.isEmpty(), "batch-only原生产收货流水缺失");
        SortedMap<Long, BigDecimal> batches = new TreeMap<>();
        Set<Long> ids = new HashSet<>();
        for (var fact : facts) {
            need(
                    fact != null
                            && fact.getTxId() != null
                            && ids.add(fact.getTxId())
                            && store.tenant().equals(fact.getTenantId())
                            && "0".equals(fact.getDelFlag())
                            && Objects.equals(group, fact.getPostingGroupId())
                            && Long.valueOf(1).equals(fact.getTrackingVersion())
                            && fact.getSnId() == null
                            && "PRD_WORK_ORDER".equals(fact.getSourceDocType())
                            && wo.getWoId().equals(fact.getSourceDocId())
                            && "PRODUCTION_IN".equals(fact.getTxType())
                            && Integer.valueOf(1).equals(fact.getTxFlag())
                            && wo.getMaterialId().equals(fact.getMaterialId())
                            && Objects.equals(wo.getMaterialCode(), fact.getMaterialCode())
                            && Objects.equals(wo.getUnitId().toString(), fact.getBaseUomId())
                            && Objects.equals(receipt.getWarehouseId(), fact.getWarehouseId())
                            && Objects.equals(receipt.getLocationId(), fact.getLocationId())
                            && fact.getBatchId() != null
                            && Objects.equals(receipt.getBatchCode(), fact.getBatchCode())
                            && fact.getTxQuantity() != null
                            && fact.getTxQuantity().signum() > 0
                            && fact.getTxDate() != null
                            && fact.getMaterialStockId() != null
                            && fact.getBeforeQuantity() != null
                            && fact.getAfterQuantity() != null
                            && fact.getBeforeQuantity().signum() >= 0
                            && fact.getAfterQuantity().signum() >= 0
                            && fact.getTxQuantity()
                                            .compareTo(
                                                    fact.getAfterQuantity()
                                                            .subtract(fact.getBeforeQuantity()))
                                    == 0
                            && fact.getUnitCost() != null
                            && fact.getUnitCost().signum() >= 0
                            && fact.getTxAmount() != null
                            && fact.getTxQuantity()
                                            .multiply(fact.getUnitCost())
                                            .setScale(4, RoundingMode.HALF_UP)
                                            .compareTo(fact.getTxAmount())
                                    == 0,
                    "batch-only原完整流水的来源、数量、单位、成本或维度不符");
            need(batches.put(fact.getBatchId(), fact.getTxQuantity()) == null, "同批次出现额外生产收货流水");
        }
        need(
                batches.size() == 1
                        && batches.containsKey(receipt.getBatchId())
                        && batches.values().stream()
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .compareTo(receipt.getQuantity())
                                == 0,
                "原单批次收货行与完整批次流水集合不一致");
        return batches;
    }

    private static WmSerialPostingRequest request(PrdWorkOrder wo, WmProductionRecpt receipt) {
        return WmSerialPostingRequest.in(
                "PRD_WORK_ORDER",
                wo.getWoId(),
                null,
                "PRODUCTION_IN_BATCH",
                wo.getMaterialId(),
                receipt.getQuantity(),
                receipt.getWarehouseId(),
                receipt.getLocationId(),
                receipt.getBatchId(),
                null);
    }

    private static String command(CompletionManifest manifest) {
        return "MES_FINAL/"
                + manifest.manifestId()
                + "/"
                + manifest.revision()
                + "/"
                + manifest.contentHash();
    }
}
