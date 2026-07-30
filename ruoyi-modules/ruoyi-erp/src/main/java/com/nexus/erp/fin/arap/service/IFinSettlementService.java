package com.nexus.erp.fin.arap.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.arap.domain.bo.FinSettlementBo;
import com.nexus.erp.fin.arap.domain.vo.FinSettlementVo;
import com.nexus.erp.fin.arap.domain.vo.OpenItemVo;

import java.util.List;

/**
 * 应收应付核销 Service
 *
 * @author nexus
 * @date 2026-07-30
 */
public interface IFinSettlementService {

    /**
     * 核销:发票 <-> 收付款单。
     * <p>写 fin_settlement -> 调发票 verifyInvoice(累加 verified_amount + 状态) ->
     * 累加 fin_receipt.settled_amount + 状态 -> 全额核销时清账 gl_voucher_line(openItem=1->cleared=1)。</p>
     */
    void settle(FinSettlementBo bo);

    /** 核销记录分页 */
    TableDataInfo<FinSettlementVo> queryPageList(FinSettlementBo bo, PageQuery pageQuery);

    /** 核销记录列表 */
    List<FinSettlementVo> queryList(FinSettlementBo bo);

    /**
     * 查未清项(应收应付报表):未清发票(verified<total)+ 未清收付款(settled<amount)。
     *
     * @param direction AR/AP
     * @param partnerId 客户ID(AR)或供应商ID(AP),null=全部
     */
    List<OpenItemVo> queryOpenItems(String direction, Long partnerId);
}
