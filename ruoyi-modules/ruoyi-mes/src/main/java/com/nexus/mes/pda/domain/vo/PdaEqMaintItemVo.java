package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * PDA 保养待办列表项 (到期保养计划)
 */
@Data
public class PdaEqMaintItemVo {

    /** 保养计划ID */
    private Long id;
    /** 设备编码 (扫码定位键) */
    private String equipmentCode;
    /** 设备名称 */
    private String equipmentName;
    /** 计划名称 */
    private String planName;
    /** 保养类型 DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY */
    private String maintenanceType;
    /** 下次保养日期(本次到期日) */
    private Date nextDate;
    /** 逾期天数(0=今日到期) */
    private Integer overdueDays;
    /** 保养内容 */
    private String content;
    /** 负责人 */
    private String responsibleName;
    /** 设备状态 */
    private String equipmentStatus;
}
