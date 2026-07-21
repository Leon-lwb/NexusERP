package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqMaintenancePlan;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES设备保养计划视图对象 eq_maintenance_plan
 *
 * <p>属于MES 设备管理模块，保养计划视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqMaintenancePlan.class)
public class EqMaintenancePlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计划ID */
    @ExcelProperty("计划ID")
    private Long planId;

    /** 计划名称 */
    @ExcelProperty("计划名称")
    private String planName;

    /** 设备ID */
    private Long equipmentId;

    /** 保养类型 */
    @ExcelProperty("保养类型")
    private String maintenanceType;

    /** 周期(天) */
    @ExcelProperty("周期(天)")
    private Integer cycleDays;

    /** 下次保养日期 */
    @ExcelProperty("下次保养日期")
    private Date nextDate;

    /** 保养内容 */
    @ExcelProperty("保养内容")
    private String content;

    /** 负责人ID */
    private Long responsibleId;

    /** 负责人姓名 */
    @ExcelProperty("负责人")
    private String responsibleName;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}
