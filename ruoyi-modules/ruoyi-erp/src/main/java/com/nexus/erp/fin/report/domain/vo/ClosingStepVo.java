package com.nexus.erp.fin.report.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 月结向导步骤状态视图对象
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
public class ClosingStepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 期间ID */
    private Long periodId;

    /** 期间名称(如 2026-07) */
    private String periodName;

    /** 期间状态 OPEN/CLOSED/LOCKED */
    private String periodStatus;

    /** 是否已试算校验 */
    private Boolean trialChecked;

    /** 是否已损益结转 */
    private Boolean carried;

    /** 是否已外币重估 */
    private Boolean revaluated;

    /** 是否已关账 */
    private Boolean closed;

    /** 本月凭证数快照 */
    private Integer voucherCount;

    /** 损益结转凭证号(已结转时) */
    private String closingVoucherNo;

    /** 重估凭证号(已重估时) */
    private String revaluationVoucherNo;

    /** 关账日志 */
    private List<FinCloseLogVo> logs;
}
