package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.mapper.WmProductionRecptGuardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/** 原工单父锁下核验生产入库来源与完整结果，不更新历史数量、状态或库存。 */
@Service
@RequiredArgsConstructor
public class WmProductionRecptWriteGuard {
    private final WmProductionRecptGuardMapper mapper;
    public record LockedReceipt(PrdWorkOrder workOrder, WmProductionRecpt receipt) { }

    public String tenant() {
        String tenant = TenantHelper.getTenantId();
        require(TenantHelper.isEnable() && StringUtils.isNotBlank(tenant), "生产入库缺少可信租户上下文");
        require(TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            && "erp".equals(DynamicDataSourceContextHolder.peek()), "生产入库必须在实际 ERP 写事务中执行");
        return tenant;
    }

    public PrdWorkOrder lockWorkOrder(Long id) {
        require(positive(id), "生产入库必须关联真实工单");
        String tenant = tenant();
        PrdWorkOrder wo = io(() -> mapper.lockWorkOrder(tenant, id));
        require(wo != null && tenant.equals(wo.getTenantId()) && id.equals(wo.getWoId())
            && "0".equals(wo.getDelFlag()) && positive(wo.getMaterialId()) && positive(wo.getUnitId())
            && StringUtils.isNotBlank(wo.getWoNo()), "生产入库工单来源不存在、已删除或归属不符");
        return wo;
    }

    public LockedReceipt lockReceipt(Long id) {
        require(positive(id), "生产入库单编号无效");
        String tenant = tenant();
        Long parent = io(() -> mapper.findWorkOrder(tenant, id));
        PrdWorkOrder wo = lockWorkOrder(parent);
        List<WmProductionRecpt> rows = receipts(wo);
        require(rows.size() == 1, "同工单生产入库凭据重复或缺失，不能编辑或执行");
        WmProductionRecpt receipt = rows.get(0);
        require(receipt != null && id.equals(receipt.getRecptId()) && parent.equals(receipt.getWoId())
            && tenant.equals(receipt.getTenantId()) && "0".equals(receipt.getDelFlag()), "原生产入库单已删除或父来源漂移");
        return new LockedReceipt(wo, receipt);
    }

    public List<WmProductionRecpt> receipts(PrdWorkOrder wo) {
        List<WmProductionRecpt> rows = io(() -> mapper.lockReceipts(tenant(), wo.getWoId()));
        require(rows != null && rows.stream().allMatch(r -> r != null && tenant().equals(r.getTenantId())
            && wo.getWoId().equals(r.getWoId())), "无法核对完整生产入库凭据或发现错误父来源");
        List<WmProductionRecpt> active = new ArrayList<>();
        for (WmProductionRecpt row : rows) {
            if ("0".equals(row.getDelFlag())) {
                active.add(row);
            } else {
                // 兼容 Mapper 默认删除值 1 与历史 2；已执行、未知状态或已有批次的删除历史仍然阻断。
                require(("1".equals(row.getDelFlag()) || "2".equals(row.getDelFlag())) && "DRAFT".equals(row.getStatus())
                    && row.getBatchId() == null && row.getBatchCode() == null,
                    "被删除生产入库凭据不是明确零效果草稿，不能隐藏后另造入库");
            }
        }
        return active;
    }

    public void requireNoEffects(PrdWorkOrder wo) {
        List<WmMaterialStockTx> tx = io(() -> mapper.lockTransactions(tenant(), wo.getWoId()));
        require(tx != null && tx.isEmpty(), "工单已有入库流水，禁止新建、修改或伪造第二次执行");
    }

    public void requireDraft(WmProductionRecpt receipt) {
        require(receipt != null && "DRAFT".equals(receipt.getStatus()), "仅原草稿生产入库单允许修改、删除或首次执行");
    }

    public void validateDraft(PrdWorkOrder wo, WmProductionRecpt receipt) {
        requireDraft(receipt);
        require(Set.of("CREATED", "APPROVING", "APPROVED", "RELEASED", "IN_PROGRESS", "COMPLETED").contains(Objects.toString(wo.getWoStatus(), "")),
            "已关闭、取消或未知状态工单不能新建或编辑生产入库承诺");
        require(wo.getWoId().equals(receipt.getWoId()) && wo.getMaterialId().equals(receipt.getMaterialId())
            && Objects.equals(wo.getWoNo(), receipt.getWoNo()) && Objects.equals(wo.getMaterialCode(), receipt.getMaterialCode()),
            "生产入库工单与成品物料来源不一致");
        require(quantity(receipt.getQuantity()) && quantity(wo.getPlannedQty())
            && receipt.getQuantity().compareTo(wo.getPlannedQty()) <= 0, "生产入库数量未知、精度无效或超过工单计划");
        var material = io(() -> mapper.lockMaterial(tenant(), wo.getMaterialId()));
        require(material != null && wo.getMaterialId().equals(material.materialId())
            && Objects.equals(wo.getMaterialCode(), material.materialCode()) && "1".equals(material.materialStatus())
            && "0".equals(material.materialDelFlag()) && wo.getUnitId().equals(material.baseUomId())
            && material.baseUomId().equals(material.primaryId()) && Long.valueOf(1).equals(material.isBase())
            && equal(BigDecimal.ONE, material.conversionRate()) && "1".equals(material.unitStatus())
            && "0".equals(material.unitDelFlag()) && material.quantityScale() != null
            && material.quantityScale() >= 0 && material.quantityScale() <= 4
            && receipt.getQuantity().stripTrailingZeros().scale() <= material.quantityScale()
            && StringUtils.isNotBlank(material.conversionVersion()) && material.conversionVerifiedAt() != null
            && material.standardCost() != null && material.standardCost().signum() >= 0,
            "生产入库物料、基本单位、数量精度或材料成本来源未知");
        require(positive(receipt.getWarehouseId()) && positive(receipt.getLocationId()), "生产入库必须选择真实仓库与储位");
        var storage = io(() -> mapper.lockStorage(tenant(), receipt.getWarehouseId(), receipt.getLocationId()));
        require(storage != null && receipt.getWarehouseId().equals(storage.warehouseId())
            && receipt.getLocationId().equals(storage.locationId()) && "1".equals(storage.warehouseStatus())
            && "0".equals(storage.warehouseDelFlag()) && "0".equals(storage.areaDelFlag())
            && "0".equals(storage.locationDelFlag()) && Long.valueOf(1).equals(storage.areaEnabled())
            && Long.valueOf(0).equals(storage.areaFrozen()) && Long.valueOf(1).equals(storage.locationEnabled())
            && Long.valueOf(0).equals(storage.locationFrozen()), "生产入库仓库、库区与储位未启用或已冻结");
        require(receipt.getRecptDate() != null, "生产入库日期不能为空");
        LocalDate today;
        try { today = LocalDate.parse(io(mapper::databaseDate)); }
        catch (RuntimeException invalid) { throw new ServiceException("生产入库数据库业务日期未知"); }
        LocalDate date = java.time.Instant.ofEpochMilli(receipt.getRecptDate().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        require(!date.isAfter(today), "生产入库日期不能在未来");
        receipt.setTenantId(tenant()); receipt.setDelFlag("0");
        receipt.setMaterialName(material.materialName()); receipt.setMaterialSpec(material.materialSpec());
        receipt.setWarehouseCode(storage.warehouseCode()); receipt.setWarehouseName(storage.warehouseName());
        receipt.setLocationCode(storage.locationCode()); receipt.setLocationName(storage.locationName());
        require((receipt.getBatchId() == null && receipt.getBatchCode() == null)
            || positive(receipt.getBatchId()) && StringUtils.isNotBlank(receipt.getBatchCode()), "生产入库批次必须有完整身份");
        if (receipt.getBatchId() != null) validateBatch(wo, receipt, false);
    }

    public BigDecimal readyQuantity(PrdWorkOrder wo) {
        require(Set.of("COMPLETED", "CLOSED").contains(Objects.toString(wo.getWoStatus(), "")) && wo.getActualEndDate() != null
            && !"UNQUALIFIED".equals(wo.getQualityStatus()) && quantity(wo.getCompletedQty())
            && quantity(wo.getPlannedQty()),
            "生产入库必须有原工单真实完工事实和有效完成量");
        // 报工合计来自父锁后的逐行当前锁读，不能与此前普通 SELECT 的旧 RR 快照混用。
        BigDecimal reported = io(() -> mapper.approvedReportedQuantity(tenant(), wo.getWoId()));
        require(reported != null && reported.signum() >= 0 && reported.compareTo(wo.getCompletedQty()) <= 0,
            "工单已审核报工数量未知或超过真实完成量");
        BigDecimal quantity = wo.getCompletedQty().subtract(reported);
        // 累计量包括既有审核报工，应先减出本次量再核计划，不能把合法一次完成误认为累计补差额。
        require(quantity(quantity) && quantity.compareTo(wo.getPlannedQty()) <= 0, "工单没有明确可入库的本次完成量或本次量超过计划");
        return quantity;
    }

    public void validateReady(PrdWorkOrder wo, WmProductionRecpt receipt) {
        validateDraft(wo, receipt);
        require(equal(readyQuantity(wo), receipt.getQuantity()), "生产入库数量必须等于原工单本次真实待入库量");
        requireNoEffects(wo);
    }

    /** 验证新落账或历史重放的唯一原凭据；当前库存可因合法销售出库变化，不要求仍存原数量。 */
    public WmProductionRecpt verifyExecuted(PrdWorkOrder wo, BigDecimal expected, WmProductionRecpt requested) {
        List<WmProductionRecpt> rows = receipts(wo);
        require(rows.size() == 1, "生产入库原结果缺失或重复，不能确认成功");
        WmProductionRecpt receipt = rows.get(0);
        require(receipt != null && positive(receipt.getRecptId()) && tenant().equals(receipt.getTenantId())
            && "0".equals(receipt.getDelFlag()) && "EXECUTED".equals(receipt.getStatus())
            && wo.getWoId().equals(receipt.getWoId()) && wo.getMaterialId().equals(receipt.getMaterialId())
            && Objects.equals(wo.getWoNo(), receipt.getWoNo()) && Objects.equals(wo.getMaterialCode(), receipt.getMaterialCode())
            && equal(expected, receipt.getQuantity()), "生产入库原单终态、来源或数量不一致");
        var material = io(() -> mapper.lockMaterial(tenant(), wo.getMaterialId()));
        require(material != null && wo.getMaterialId().equals(material.materialId())
            && Objects.equals(wo.getMaterialCode(), material.materialCode()) && positive(material.baseUomId())
            && material.baseUomId().equals(material.primaryId()) && Long.valueOf(1).equals(material.isBase())
            && equal(BigDecimal.ONE, material.conversionRate()) && wo.getUnitId().equals(material.baseUomId())
            && material.quantityScale() != null && material.quantityScale() >= 0 && material.quantityScale() <= 4
            && receipt.getQuantity().stripTrailingZeros().scale() <= material.quantityScale()
            && StringUtils.isNotBlank(material.conversionVersion()) && material.conversionVerifiedAt() != null,
            "生产入库历史根单位、物料或精度不属于实际来源");
        require(requested == null || Objects.equals(requested.getRecptId(), receipt.getRecptId())
            && Objects.equals(requested.getWarehouseId(), receipt.getWarehouseId())
            && Objects.equals(requested.getLocationId(), receipt.getLocationId())
            && Objects.equals(requested.getBatchId(), receipt.getBatchId())
            && Objects.equals(requested.getBatchCode(), receipt.getBatchCode()), "生产入库请求与原结果仓储或批次不一致");
        List<WmMaterialStockTx> txs = io(() -> mapper.lockTransactions(tenant(), wo.getWoId()));
        require(txs != null && txs.size() == 1, "生产入库必须有唯一完整库存流水，不能凭已执行标签确认成功");
        WmMaterialStockTx tx = txs.get(0);
        require(tx != null && positive(tx.getTxId()) && "0".equals(tx.getDelFlag()) && tenant().equals(tx.getTenantId())
            && "PRD_WORK_ORDER".equals(tx.getSourceDocType()) && wo.getWoId().equals(tx.getSourceDocId())
            && "PRODUCTION_IN".equals(tx.getTxType()) && Integer.valueOf(1).equals(tx.getTxFlag())
            && wo.getMaterialId().equals(tx.getMaterialId()) && Objects.equals(receipt.getMaterialCode(), tx.getMaterialCode())
            && Objects.equals(wo.getUnitId().toString(), tx.getBaseUomId())
            && Objects.equals(receipt.getWarehouseId(), tx.getWarehouseId())
            && Objects.equals(receipt.getLocationId(), tx.getLocationId()) && positive(receipt.getBatchId())
            && Objects.equals(receipt.getBatchId(), tx.getBatchId()) && Objects.equals(receipt.getBatchCode(), tx.getBatchCode())
            && equal(expected, tx.getTxQuantity()) && tx.getTxDate() != null && positive(tx.getMaterialStockId())
            && tx.getBeforeQuantity() != null && tx.getBeforeQuantity().signum() >= 0 && tx.getAfterQuantity() != null
            && equal(expected, tx.getAfterQuantity().subtract(tx.getBeforeQuantity()))
            && tx.getUnitCost() != null && tx.getUnitCost().signum() >= 0 && tx.getTxAmount() != null
            && equal(expected.multiply(tx.getUnitCost()).setScale(4, java.math.RoundingMode.HALF_UP), tx.getTxAmount()),
            "生产入库库存流水数量、单位、成本或来源维度不符");
        WmMaterialStock stock = io(() -> mapper.lockStock(tenant(), tx.getMaterialStockId()));
        require(stock != null && tenant().equals(stock.getTenantId()) && "0".equals(stock.getDelFlag())
            && tx.getMaterialStockId().equals(stock.getStockId()) && wo.getWoId().equals(stock.getWoId())
            && wo.getMaterialId().equals(stock.getMaterialId()) && Objects.equals(receipt.getMaterialCode(), stock.getMaterialCode())
            && Objects.equals(tx.getBaseUomId(), stock.getBaseUomId())
            && Objects.equals(tx.getWarehouseId(), stock.getWarehouseId()) && Objects.equals(tx.getAreaId(), stock.getAreaId())
            && Objects.equals(tx.getLocationId(), stock.getLocationId()) && Objects.equals(tx.getBatchId(), stock.getBatchId())
            && Objects.equals(tx.getBatchCode(), stock.getBatchCode()), "生产入库缺少对应原库存维度");
        validateBatch(wo, receipt, true);
        return receipt;
    }

    private void validateBatch(PrdWorkOrder wo, WmProductionRecpt receipt, boolean posted) {
        WmBatch b = io(() -> mapper.lockBatch(tenant(), receipt.getBatchId()));
        require(b != null && tenant().equals(b.getTenantId()) && "0".equals(b.getDelFlag())
            && (posted ? Set.of("ACTIVE", "EXPIRED", "LOCKED", "CLOSED").contains(Objects.toString(b.getStatus(), ""))
                : "ACTIVE".equals(b.getStatus())) && receipt.getBatchId().equals(b.getBatchId())
            && receipt.getBatchCode().equals(b.getBatchCode()) && wo.getMaterialId().equals(b.getMaterialId())
            && receipt.getWarehouseId().equals(b.getWarehouseId()) && "PRD_WORK_ORDER".equals(b.getSourceDocType())
            && wo.getWoId().equals(b.getSourceDocId()) && Objects.equals(wo.getWoNo(), b.getSourceDocNo())
            && b.getReceivedQty() != null && b.getIssuedQty() != null && b.getIssuedQty().signum() >= 0
            && b.getReceivedQty().compareTo(b.getIssuedQty()) >= 0
            && (posted ? equal(receipt.getQuantity(), b.getReceivedQty())
                : b.getReceivedQty().signum() == 0 && b.getIssuedQty().signum() == 0), "生产入库批次来源或累计收发数量不一致");
    }

    /** 批量删除先锁所有父工单，再校验全部原单；禁止在中途删掉可删部分。 */
    public void validateDelete(Long[] ids) {
        require(ids != null && ids.length > 0 && ids.length <= 1000 && Arrays.stream(ids).allMatch(WmProductionRecptWriteGuard::positive)
            && new HashSet<>(Arrays.asList(ids)).size() == ids.length, "生产入库删除编号无效或重复");
        Map<Long, Long> parent = new HashMap<>();
        for (Long id : ids) {
            Long wo = io(() -> mapper.findWorkOrder(tenant(), id));
            require(positive(wo), "生产入库删除来源不存在"); parent.put(id, wo);
        }
        for (Long wo : new TreeSet<>(parent.values())) lockWorkOrder(wo);
        for (Long id : ids) {
            LockedReceipt locked = lockReceipt(id); requireDraft(locked.receipt()); requireNoEffects(locked.workOrder());
        }
    }

    private static boolean positive(Long id) { return id != null && id > 0; }
    private static boolean quantity(BigDecimal q) {
        return q != null && q.signum() > 0 && q.stripTrailingZeros().scale() <= 4
            && (long) q.stripTrailingZeros().precision() - q.stripTrailingZeros().scale() <= 14;
    }
    private static boolean equal(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private static void require(boolean valid, String message) { if (!valid) throw new ServiceException(message); }
    private static <T> T io(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failed) { throw new ServiceException("生产入库固定来源读取失败，不能确认结果"); }
    }
}