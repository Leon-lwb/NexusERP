package com.nexus.erp.fin.report.service;

import com.nexus.erp.fin.report.domain.vo.ClosingStepVo;
import com.nexus.erp.fin.report.domain.vo.FinCloseLogVo;

import java.util.List;

/**
 * 期末关账 Service(损益结转/外币重估/月结年结向导)
 *
 * @author nexus
 * @date 2026-07-31
 */
public interface IFinClosingService {

    /**
     * 试算校验:当期借贷合计相等 + 无草稿凭证
     * <p>通过则记录日志并返回 true,不通过抛 ServiceException。</p>
     *
     * @param periodId 会计期间ID
     * @return true
     */
    Boolean trialCheck(Long periodId);

    /**
     * 损益结转:损益类科目余额 -> 本年利润 4103
     * <p>幂等:createAndPost 按 (ADJUSTMENT, periodId*100+1) 去重,重复执行返回既有凭证。收入科目(CREDIT 方向)净额结转贷本年利润,费用科目(DEBIT 方向)净额结转借本年利润。</p>
     *
     * @param periodId 会计期间ID
     * @return 结转凭证ID
     */
    Long carryPnl(Long periodId);

    /**
     * 外币期末重估:外币科目按期末汇率重估,差额入财务费用-汇兑损益 6603.02
     * <p>幂等:createAndPost 按 (ADJUSTMENT, periodId*100+2) 去重。无期末汇率或无需重估时跳过并记日志。</p>
     *
     * @param periodId 会计期间ID
     * @return 重估凭证ID(无差额返回 null)
     */
    Long revaluate(Long periodId);

    /**
     * 月结整体执行:试算校验 -> 损益结转 -> 外币重估 -> 关账
     * <p>步骤独立事务,失败中止并记日志;每步幂等,可重跑。</p>
     *
     * @param periodId 会计期间ID
     * @return true
     */
    Boolean monthlyClosing(Long periodId);

    /**
     * 年结:12 月月结 -> 本年利润 4103 -> 未分配利润 4104 -> 资产/负债/权益期初结转至次年
     * <p>幂等:4103->4104 凭证按 (ADJUSTMENT, periodId*100+3) 去重;期初结转按 source_period_id 判定已结转跳过。</p>
     *
     * @param fiscalYear 会计年度
     * @return true
     */
    Boolean yearlyClosing(Integer fiscalYear);

    /**
     * 月结向导步骤状态
     *
     * @param periodId 会计期间ID
     * @return 步骤状态 VO(含日志)
     */
    ClosingStepVo steps(Long periodId);

    /**
     * 关账日志列表
     *
     * @param periodId 会计期间ID
     * @return 日志列表(按时间倒序)
     */
    List<FinCloseLogVo> closeLogs(Long periodId);
}
