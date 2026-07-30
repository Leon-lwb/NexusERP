package com.nexus.erp.fin.gl.service;

import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.domain.vo.TrialBalanceVo;

import java.util.List;

/**
 * 科目余额 Service
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlAccountBalanceService {

    /**
     * 过账:原子累加本期发生额
     * <p>按 accountId 聚合借贷金额,setSql 原地累加 period_debit/period_credit,并发安全。</p>
     *
     * @param periodId 会计期间ID
     * @param lines    凭证分录行列表
     */
    void applyPosting(Long periodId, List<GlVoucherLine> lines);

    /**
     * 反过账:原子扣减本期发生额
     * <p>按 accountId 聚合借贷金额,setSql 原地扣减 period_debit/period_credit,并发安全。</p>
     *
     * @param periodId 会计期间ID
     * @param lines    凭证分录行列表
     */
    void reversePosting(Long periodId, List<GlVoucherLine> lines);

    /**
     * 试算平衡表:期初余额 +/- 本期发生额 = 期末余额
     * <p>Java 层聚合,取期初借方/贷方和本期借方/贷方,按借贷方向计算期末净额。</p>
     *
     * @param periodId    会计期间ID
     * @param accountType 科目类型(可选过滤,null 或空则查询全部)
     * @return 试算平衡表列表(按科目编码排序)
     */
    List<TrialBalanceVo> trialBalance(Long periodId, String accountType);
}
