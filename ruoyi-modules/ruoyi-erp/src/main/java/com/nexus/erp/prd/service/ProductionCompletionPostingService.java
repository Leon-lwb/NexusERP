package com.nexus.erp.prd.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 工单完工倒冲与成品入库的同 ERP 事务入口，由消费者调用 Spring 代理。
 * <p>按租户工单行锁串行，只以完整持久过账凭据确认成功，不依赖消息 ID 或缓存有效期。
 * 本入口沿用 complete 的一次终态、本次数量语义，不把消息改成累计数量，也不修复历史半过账。</p>
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ProductionCompletionPostingService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmBatchProductionReceiptProofService batchProof;

    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryProofService serialProof;

    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmProductionRecptWriteGuard serialReceiptGuard;

    private final ProductionCompletionPostingMapper postingMapper;
    private final PrdBackflushServiceImpl backflushService;
    private final IPutawayStrategyService putawayStrategyService;
    private final IWmProductionRecptService productionRecptService;

    /**
     * 原子执行本次完工倒冲及入库，异常经 Spring 回滚后交消费模板重试。
     *
     * @param message 完工消息，仅用于对照真实工单，不能作为完成事实或成功凭据
     */
    @Transactional(rollbackFor = Exception.class)
    public void postCompletion(ProductionCompleteMessage message) {
        require(
                TransactionSynchronizationManager.isActualTransactionActive(),
                "完工过账必须通过 ERP 事务代理调用");
        require(
                message != null
                        && message.getTenantId() != null
                        && message.getTenantId() >= 0
                        && message.getWoId() != null
                        && message.getWoId() > 0,
                "完工消息缺少有效租户或工单");
        String tenantId = String.format(Locale.ROOT, "%06d", message.getTenantId());
        require(tenantId.equals(TenantHelper.getTenantId()), "完工消息租户与消费上下文不一致");

        // 首次持久读取即锁住真实工单，后续验账、扣料和入库共用该锁及同一 ERP 事务。
        PrdWorkOrder wo = postingMapper.lockWorkOrder(tenantId, message.getWoId());
        BigDecimal quantity = validateCompletion(wo, message, tenantId);
        Long originalReceipt = verifiedReceiptId(wo, quantity, tenantId);
        if (originalReceipt != null) {
            // 原结果仍需经统一入口核物料根单位和完整库存/批次来源；该分支只验原凭据，不再倒冲或入库。
            productionRecptService.execute(originalReceipt);
            return;
        }
        require(
                postingMapper.countExistingBackflushIssues(tenantId, wo.getWoId()) == 0,
                "工单存在历史倒冲但缺少完整成品过账凭据，需对账后重试，禁止再次扣料");

        // 内层既有服务使用 REQUIRED；上架、单据或库存流水任一步失败都回滚本次倒冲。
        backflushService.backflushOnCompletion(wo.getWoId(), quantity);
        PutawayLocation putaway =
                putawayStrategyService.resolvePutaway(wo.getMaterialId(), quantity);
        require(
                putaway != null
                        && putaway.getWarehouseId() != null
                        && putaway.getLocationId() != null,
                "成品上架未解析到有效仓库和库位");
        Long receiptId =
                productionRecptService.createAndExecute(
                        wo.getWoId(),
                        wo.getWoNo(),
                        wo.getMaterialId(),
                        wo.getMaterialCode(),
                        wo.getMaterialName(),
                        wo.getMaterialSpec(),
                        quantity,
                        putaway.getWarehouseId(),
                        putaway.getWarehouseCode(),
                        putaway.getWarehouseName(),
                        putaway.getLocationId(),
                        putaway.getLocationCode(),
                        putaway.getLocationName(),
                        new Date());
        Long verifiedId = verifiedReceiptId(wo, quantity, tenantId);
        require(receiptId != null && receiptId.equals(verifiedId), "生产入库返回单据与完整有效过账凭据不一致，禁止确认消费成功");
    }

    /**
     * 校验真实工单及本次完工数量；报工审核累计量不能冒充本次完工消息量。
     */
    private BigDecimal validateCompletion(
            PrdWorkOrder wo, ProductionCompleteMessage message, String tenantId) {
        require(
                wo != null
                        && tenantId.equals(wo.getTenantId())
                        && "0".equals(wo.getDelFlag())
                        && Objects.equals(wo.getWoId(), message.getWoId()),
                "完工工单不存在、已删除或不属于当前租户");
        require(
                ("COMPLETED".equals(wo.getWoStatus()) || "CLOSED".equals(wo.getWoStatus()))
                        && wo.getActualEndDate() != null,
                "真实工单尚未完成，不能由消息伪造完成事实");
        require(
                wo.getMaterialId() != null
                        && Objects.equals(wo.getWoNo(), message.getWoNo())
                        && Objects.equals(wo.getMaterialId(), message.getMaterialId())
                        && Objects.equals(wo.getMaterialCode(), message.getMaterialCode())
                        && Objects.equals(wo.getMaterialName(), message.getMaterialName()),
                "完工消息工单或物料与真实工单不一致");
        BigDecimal quantity = message.getCompletionQty();
        require(
                quantity != null && quantity.signum() > 0 && wo.getCompletedQty() != null,
                "成品过账必须具有真实的正数完工数量");
        // 审核报工与 complete 是当前两条累计写入路径；报工逻辑删除没有冲减工单。
        // 不按 <= 累计值放行伪造的小数量，也不把本次量替换成工单累计值。
        BigDecimal reported = postingMapper.selectApprovedReportedQty(tenantId, wo.getWoId());
        require(
                reported != null
                        && reported.signum() >= 0
                        && sameQuantity(quantity, wo.getCompletedQty().subtract(reported)),
                "消息本次完工数量与真实工单扣除已审核报工后的数量不一致，需对账");
        return quantity;
    }

    /**
     * 读取完整凭据集合。无任何成品凭据时返回空；存在不完整、删除或冲突凭据时必须失败。
     * <p>既有 complete 只允许一次终态，单张生产单对应唯一库存流水；不进入旧累计补差额分支。</p>
     */
    private Long verifiedReceiptId(PrdWorkOrder wo, BigDecimal quantity, String tenantId) {
        List<WmProductionRecpt> allReceipts = postingMapper.selectReceipts(tenantId, wo.getWoId());
        List<WmMaterialStockTx> transactions =
                postingMapper.selectCompletionTransactions(tenantId, wo.getWoId());
        require(allReceipts != null && transactions != null, "无法读取工单成品过账凭据");
        List<WmProductionRecpt> receipts = new java.util.ArrayList<>();
        for (WmProductionRecpt row : allReceipts) {
            require(
                    row != null
                            && tenantId.equals(row.getTenantId())
                            && wo.getWoId().equals(row.getWoId()),
                    "工单入库凭据租户或父来源不一致");
            if ("0".equals(row.getDelFlag())) {
                receipts.add(row);
            } else {
                require(
                        ("1".equals(row.getDelFlag()) || "2".equals(row.getDelFlag()))
                                && "DRAFT".equals(row.getStatus())
                                && row.getBatchId() == null
                                && row.getBatchCode() == null,
                        "被删除生产入库不是明确零效果草稿，不能另造入库");
            }
        }
        if (receipts.isEmpty() && transactions.isEmpty()) {
            return null;
        }
        if (batchProof != null && batchProof.exists(wo.getWoId())) {
            require(receipts.size() == 1, "同工单必须只有一张最终生产入库凭据");
            WmProductionRecpt receipt = serialReceiptGuard.verifyExecuted(wo, quantity, null);
            batchProof.verify(wo, receipt, transactions);
            return receipt.getRecptId();
        }
        if (serialProof != null && serialProof.isV2(transactions)) {
            require(receipts.size() == 1, "同工单必须只有一张最终生产入库凭据");
            WmProductionRecpt receipt = serialReceiptGuard.verifyExecuted(wo, quantity, null);
            serialProof.verifyProduction(receipt, transactions);
            return receipt.getRecptId();
        }
        require(receipts.size() == 1 && transactions.size() == 1, "工单成品过账凭据缺失或重复，禁止重复倒冲和确认成功");
        WmProductionRecpt receipt = receipts.get(0);
        WmMaterialStockTx tx = transactions.get(0);
        require(
                receipt != null
                        && receipt.getRecptId() != null
                        && "0".equals(receipt.getDelFlag())
                        && "EXECUTED".equals(receipt.getStatus())
                        && tenantId.equals(receipt.getTenantId())
                        && Objects.equals(wo.getWoId(), receipt.getWoId())
                        && Objects.equals(wo.getWoNo(), receipt.getWoNo())
                        && Objects.equals(wo.getMaterialId(), receipt.getMaterialId())
                        && Objects.equals(wo.getMaterialCode(), receipt.getMaterialCode())
                        && sameQuantity(quantity, receipt.getQuantity())
                        && receipt.getBatchId() != null
                        && receipt.getBatchCode() != null
                        && receipt.getWarehouseId() != null
                        && receipt.getLocationId() != null,
                "生产入库单未完整执行或与真实工单数量、物料、批次、仓位冲突");
        validateTransaction(tx, receipt, wo, quantity, tenantId);
        WmMaterialStock stock = postingMapper.selectStock(tenantId, tx.getMaterialStockId());
        require(
                stock != null
                        && "0".equals(stock.getDelFlag())
                        && tenantId.equals(stock.getTenantId())
                        && Objects.equals(tx.getMaterialStockId(), stock.getStockId())
                        && Objects.equals(wo.getWoId(), stock.getWoId())
                        && Objects.equals(receipt.getMaterialId(), stock.getMaterialId())
                        && Objects.equals(receipt.getMaterialCode(), stock.getMaterialCode())
                        && Objects.equals(receipt.getBatchId(), stock.getBatchId())
                        && Objects.equals(receipt.getBatchCode(), stock.getBatchCode())
                        && Objects.equals(receipt.getWarehouseId(), stock.getWarehouseId())
                        && Objects.equals(receipt.getLocationId(), stock.getLocationId()),
                "生产入库流水缺少对应有效库存行或库存维度不一致");
        // 库存可在后续合法出库中变化；成本 posted 标志也不代表库存过账，均不能误作重放条件。
        return receipt.getRecptId();
    }

    /**
     * 校验有效正向库存流水及自身变动平衡，不能只凭工单级 hasPosted 就确认入库成功。
     */
    private void validateTransaction(
            WmMaterialStockTx tx,
            WmProductionRecpt receipt,
            PrdWorkOrder wo,
            BigDecimal quantity,
            String tenantId) {
        require(
                tx != null
                        && tx.getTxId() != null
                        && "0".equals(tx.getDelFlag())
                        && tenantId.equals(tx.getTenantId())
                        && "PRD_WORK_ORDER".equals(tx.getSourceDocType())
                        && Objects.equals(wo.getWoId(), tx.getSourceDocId())
                        && "PRODUCTION_IN".equals(tx.getTxType())
                        && Integer.valueOf(1).equals(tx.getTxFlag())
                        && Objects.equals(receipt.getMaterialId(), tx.getMaterialId())
                        && Objects.equals(receipt.getMaterialCode(), tx.getMaterialCode())
                        && Objects.equals(receipt.getBatchId(), tx.getBatchId())
                        && Objects.equals(receipt.getBatchCode(), tx.getBatchCode())
                        && Objects.equals(receipt.getWarehouseId(), tx.getWarehouseId())
                        && Objects.equals(receipt.getLocationId(), tx.getLocationId())
                        && sameQuantity(quantity, tx.getTxQuantity())
                        && tx.getMaterialStockId() != null
                        && tx.getTxDate() != null
                        && tx.getBeforeQuantity() != null
                        && tx.getAfterQuantity() != null
                        && tx.getBeforeQuantity().signum() >= 0
                        && sameQuantity(
                                quantity, tx.getAfterQuantity().subtract(tx.getBeforeQuantity())),
                "生产入库库存流水无效、数量不平衡或与单据维度冲突");
    }

    /**
     * 数量按数值而非字符串比较，10 与 10.0 是同一次完成事实。
     */
    private boolean sameQuantity(BigDecimal expected, BigDecimal actual) {
        return expected != null && actual != null && expected.compareTo(actual) == 0;
    }

    /**
     * 守卫失败不得吞异常返回成功，也不得凭消息补造库存凭据。
     */
    private void require(boolean condition, String description) {
        if (!condition) {
            throw new ServiceException(description);
        }
    }
}
