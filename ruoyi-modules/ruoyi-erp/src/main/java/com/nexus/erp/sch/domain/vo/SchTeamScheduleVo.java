package com.nexus.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchTeamSchedule;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 班组排班（模板）视图对象 sch_team_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchTeamSchedule.class)
public class SchTeamScheduleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 班组排班ID
     */
    @ExcelProperty(value = "班组排班ID")
    private Long teamScheduleId;

    /**
     * 排班计划ID
     */
    @ExcelProperty(value = "排班计划ID")
    private Long planId;

    /**
     * 班组ID
     */
    @ExcelProperty(value = "班组ID")
    private Long teamId;

    /**
     * 班次ID
     */
    @ExcelProperty(value = "班次ID")
    private Long shiftId;

    /**
     * 排班日期
     */
    @ExcelProperty(value = "排班日期")
    private Date scheduledDate;

    /**
     * 星期几(1-7)
     */
    @ExcelProperty(value = "星期几")
    private Long weekDay;

    /**
     * 是否节假日
     */
    @ExcelProperty(value = "是否节假日")
    private Integer isHoliday;
}
