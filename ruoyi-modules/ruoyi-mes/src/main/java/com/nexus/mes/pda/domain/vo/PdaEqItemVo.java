package com.nexus.mes.pda.domain.vo;

import lombok.Data;

/**
 * PDA 设备点检待办列表项
 */
@Data
public class PdaEqItemVo {

    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    /** 设备状态 (点检不合格会被置为 FAULT) */
    private String status;
    /** 上次点检时间 (yyyy-MM-dd HH:mm, 无则空) */
    private String lastInspectionTime;
    /** 上次点检总判定 */
    private Boolean overallPass;
}
