package com.nexus.erp.fin.cost.poster;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.cost.constant.CostConstants;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存货成本过账 Poster。
 * <p>按库存流水(wm_material_stock_tx)驱动:采购入库借存货/贷应付暂估、
 * 出库(生产领料/销售出库/杂项出库)借成本/贷存货;
 * 凭证 sourceId 用雪花ID(幂等由流水先查后过保证),voucher_source=COST。</p>
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Component
public class CostPoster {

    private final WmMaterialStockTxMapper stockTxMapper;
    private final WmMaterialStockMapper stockMapper;
    private final com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper;
    private final GlAccountMapper accountMapper;
    private final GlPeriodMapper periodMapper;
    private final IGlPeriodService periodService;
    private final IGlVoucherService voucherService;

    /**
     * 按库存流水ID过账(先查流水是否已过账,幂等)。
     *
     * @param txId 库存流水ID
     */
    public void postTx(Long txId) {
        WmMaterialStockTx tx = stockTxMapper.selectById(txId);
        if (tx == null) {
            throw new ServiceException("库存流水不存在: {}", txId);
        }
        if (tx.getPosted() != null && tx.getPosted() == 1) {
            return;
        }
        // 虚拟转储/内部调整流水无成本意义,跳过
        if (isSkip(tx.getTxType())) {
            return;
        }
        BigDecimal amount = costAmount(tx);
        if (amount.signum() == 0) {
            return;
        }
        List<GlVoucherLine> lines = new ArrayList<>();
        String txType = tx.getTxType();
        String summary = tx.getRemark() + "(" + tx.getMaterialName() + ")";
        if (isIn(txType)) {
            // 入库:借 存货科目 / 贷 应付暂估
            lines.add(voucherLine(inventoryAccountId(tx), amount, null, summary));
            lines.add(voucherLine(apProvisionAccountId(), null, amount, summary));
        } else {
            // 出库:借 成本科目 / 贷 存货科目
            lines.add(voucherLine(costAccountId(txType), amount, null, summary));
            lines.add(voucherLine(inventoryAccountId(tx), null, amount, summary));
        }
        GlPeriod period = resolvePeriod(tx.getErpDate() != null ? tx.getErpDate() : tx.getTxDate());        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(tx.getErpDate() != null ? tx.getErpDate() : tx.getTxDate());
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_COST);
        req.setSourceId(txId);
        req.setSourceNo("COST-" + tx.getTxId());
        req.setSummary(summary);
        req.setLines(lines);
        voucherService.createAndPost(req);
        stockTxMapper.update(null, Wrappers.<WmMaterialStockTx>lambdaUpdate()
            .set(WmMaterialStockTx::getPosted, 1)
            .set(WmMaterialStockTx::getPostedVoucherNo, req.getSourceNo())
            .eq(WmMaterialStockTx::getTxId, txId));
        log.info("存货成本过账: txId={}, type={}, amount={}", txId, txType, amount);
    }

    /** 流水成本金额 = unitCost × txQuantity(未计价流水按 0 跳过) */
    private BigDecimal costAmount(WmMaterialStockTx tx) {
        BigDecimal unitCost = tx.getUnitCost() == null ? BigDecimal.ZERO : tx.getUnitCost();
        BigDecimal qty = tx.getTxQuantity() == null ? BigDecimal.ZERO : tx.getTxQuantity();
        return unitCost.multiply(qty).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /** 是否入库事务 */
    private boolean isIn(String txType) {
        return CostConstants.TX_RECEIVE_IN.equals(txType)
            || CostConstants.TX_MISC_IN.equals(txType)
            || "TARGET_IN".equals(txType);
    }

    /** 虚拟转储/内部调整流水(无成本意义,跳过) */
    private boolean isSkip(String txType) {
        return "VIRTUAL_OUT".equals(txType)
            || "TRANSFER_IN".equals(txType)
            || "TRANSFER_OUT".equals(txType);
    }

    /** 存货科目:按物料类型映射(1 原材料/4 辅料->1403,3 成品->1405,其余->1406 半成品) */
    private Long inventoryAccountId(WmMaterialStockTx tx) {
        WmMaterialStock stock = stockMapper.selectById(tx.getMaterialStockId());
        Long materialType = stock != null ? stock.getMaterialType() : null;
        if (materialType == null && stock != null && stock.getMaterialId() != null) {
            com.nexus.erp.bd.domain.BdMaterial material = materialMapper.selectById(stock.getMaterialId());
            materialType = material != null ? material.getMaterialType() : null;
        }
        long type = materialType == null ? 0L : materialType;
        String code;
        if (type == 3L) {
            code = CostConstants.ACCT_FINISHED_GOODS;
        } else if (type == 1L || type == 4L) {
            code = CostConstants.ACCT_RAW_MATERIAL;
        } else {
            code = CostConstants.ACCT_SEMI_FINISHED;
        }
        return findAccountIdByCode(code);
    }

    /** 成本科目:生产领料->5001 生产成本,销售出库/杂项出库->6401 主营业务成本 */
    private Long costAccountId(String txType) {
        String code = CostConstants.TX_PRODUCTION_ISSUE.equals(txType)
            ? CostConstants.ACCT_PRODUCTION_COST : CostConstants.ACCT_COST_OF_SALES;
        return findAccountIdByCode(code);
    }

    /** 应付暂估科目 2202 */
    private Long apProvisionAccountId() {
        return findAccountIdByCode(CostConstants.ACCT_AP_PROVISION);
    }

    /** 按凭证日期解析期间(不存在则派生 OPEN 期间) */
    private GlPeriod resolvePeriod(Date date) {
        return periodService.getOrDerivePeriod(date != null ? date : new Date());
    }

    /** 按科目编码查询科目ID */
    private Long findAccountIdByCode(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在: {}", code);
        }
        return acc.getAccountId();
    }

    /** 构建凭证行 */
    private GlVoucherLine voucherLine(Long accountId, BigDecimal debit, BigDecimal credit, String summary) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit);
        l.setCredit(credit);
        l.setSummary(summary);
        return l;
    }
}
