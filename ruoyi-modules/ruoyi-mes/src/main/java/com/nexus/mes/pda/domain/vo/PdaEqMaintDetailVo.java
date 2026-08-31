package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * PDA 保养执行详情 (计划 + 设备 + 最近保养记录)
 */
@Data
public class PdaEqMaintDetailVo {

    /** 保养计划ID */
    private Long planId;
    /** 计划名称 */
    private String planName;
    /** 保养类型 DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY */
    private String maintenanceType;
    /** 周期(天) */
    private Integer cycleDays;
    /** 下次保养日期 */
    private Date nextDate;
    /** 保养内容 */
    private String content;
    /** 负责人 */
    private String responsibleName;

    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String equipmentStatus;
    private String workcenterCode;

    /** 最近保养记录(倒序取前5条) */
    private List<PdaMaintRecordVo> lastRecords;

    /**
     * 保养记录摘要
     */
    @Data
    public static class PdaMaintRecordVo {
        private Date executeTime;
        private String executorName;
        /** NORMAL/ABNORMAL */
        private String result;
        private String content;
        private String remark;
    }
}
