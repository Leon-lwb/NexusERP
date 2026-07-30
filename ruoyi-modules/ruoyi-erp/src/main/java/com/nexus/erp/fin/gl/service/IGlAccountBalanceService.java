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

    /** 过账:原子累加本期发生额 */
    void applyPosting(Long periodId, List<GlVoucherLine> lines);

    /** 反过账:原子扣减本期发生额 */
    void reversePosting(Long periodId, List<GlVoucherLine> lines);

    /** 试算平衡表(期初 ± 本期发生 = 期末) */
    List<TrialBalanceVo> trialBalance(Long periodId, String accountType);
}
