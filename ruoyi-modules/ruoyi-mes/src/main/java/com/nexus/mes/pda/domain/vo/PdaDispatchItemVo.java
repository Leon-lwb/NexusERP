package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 报工待办列表项 (派工单)
 */
@Data
public class PdaDispatchItemVo {

    private Long dispatchId;
    private String dispatchNo;
    private String woNo;
    private String processCode;
    private String processName;
    private String workcenterCode;
    private String operatorName;
    private BigDecimal plannedQty;
    /** 累计已报工合格数 */
    private BigDecimal completedQty;
    /** PENDING 待开工 / IN_PROGRESS 进行中 / COMPLETED 已完成 */
    private String dispatchStatus;
}
