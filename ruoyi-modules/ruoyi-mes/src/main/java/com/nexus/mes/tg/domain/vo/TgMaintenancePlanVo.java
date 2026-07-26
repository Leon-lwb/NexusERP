package com.nexus.mes.tg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.tg.domain.TgMaintenancePlan;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养计划视图对象 tg_maintenance_plan
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TgMaintenancePlan.class)
public class TgMaintenancePlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计划ID */
    @ExcelProperty("计划ID")
    private Long planId;

    /** 工装ID */
    private Long toolingId;

    /** 计划名称 */
    @ExcelProperty("计划名称")
    private String planName;

    /** 触发类型 */
    @ExcelProperty("触发类型")
    private String triggerType;

    /** 触发阈值 */
    @ExcelProperty("触发阈值")
    private Integer triggerValue;

    /** 上次保养时间 */
    private Date lastMaintainTime;

    /** 下次保养时间 */
    private Date nextMaintainTime;

    /** 下次保养次数阈值 */
    private Integer nextMaintainCount;

    /** 下次保养时长阈值 */
    private BigDecimal nextMaintainHours;

    /** 是否启用 */
    @ExcelProperty("启用")
    private String enabled;

    /** 备注 */
    private String remark;
}
