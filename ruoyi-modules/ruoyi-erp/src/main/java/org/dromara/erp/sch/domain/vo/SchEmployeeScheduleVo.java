package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchEmployeeSchedule;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 员工排班明细视图对象 sch_employee_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchEmployeeSchedule.class)
public class SchEmployeeScheduleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 员工排班ID
     */
    @ExcelProperty(value = "员工排班ID")
    private Long scheduleId;

    /**
     * 排班计划ID
     */
    @ExcelProperty(value = "排班计划ID")
    private Long planId;

    /**
     * 人员ID
     */
    @ExcelProperty(value = "人员ID")
    private Long workerId;

    /**
     * 人员姓名
     */
    @ExcelProperty(value = "人员姓名")
    private String workerName;

    /**
     * 班组ID
     */
    @ExcelProperty(value = "班组ID")
    private Long teamId;

    /**
     * 班组名称
     */
    @ExcelProperty(value = "班组名称")
    private String teamName;

    /**
     * 班次ID
     */
    @ExcelProperty(value = "班次ID")
    private Long shiftId;

    /**
     * 班次名称
     */
    @ExcelProperty(value = "班次名称")
    private String shiftName;

    /**
     * 排班日期
     */
    @ExcelProperty(value = "排班日期")
    private Date scheduledDate;

    /**
     * 排班开始时间
     */
    @ExcelProperty(value = "排班开始时间")
    private Date startTime;

    /**
     * 排班结束时间
     */
    @ExcelProperty(value = "排班结束时间")
    private Date endTime;

    /**
     * 实际班次ID
     */
    @ExcelProperty(value = "实际班次ID")
    private Long actualShiftId;

    /**
     * 排班状态
     */
    @ExcelProperty(value = "排班状态")
    private String scheduleStatus;

    /**
     * 是否工作日
     */
    @ExcelProperty(value = "是否工作日")
    private Integer isWorkingDay;
}
