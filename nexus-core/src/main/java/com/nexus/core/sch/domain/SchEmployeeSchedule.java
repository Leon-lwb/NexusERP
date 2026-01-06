package com.nexus.core.sch.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 员工排班明细对象 sch_employee_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchEmployeeScheduleMapper.SchEmployeeScheduleResult")
public class SchEmployeeSchedule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 员工排班ID
     */
    @TableId(value = "schedule_id", type = IdType.AUTO)
    private Long scheduleId;
    /**
     * 排班计划ID
     */
    private Long planId;
    /**
     * 人员ID
     */
    private Long workerId;
    /**
     * 人员姓名
     */
    @Excel(name = "人员姓名")
    private String workerName;
    /**
     * 班组ID
     */
    private Long teamId;
    /**
     * 班组名称
     */
    @Excel(name = "班组名称")
    private String teamName;
    /**
     * 班次ID
     */
    private Long shiftId;
    /**
     * 班次名称
     */
    @Excel(name = "班次名称")
    private String shiftName;
    /**
     * 排班日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "排班日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date scheduledDate;
    /**
     * 排班开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "排班开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    /**
     * 排班结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "排班结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
    /**
     * 实际班次ID（用于调班覆盖）
     */
    @Excel(name = "实际班次ID", readConverterExp = "用于调班覆盖")
    private Long actualShiftId;
    /**
     * 排班状态
     */
    @Excel(name = "排班状态")
    private String scheduleStatus;
    /**
     * 是否工作日
     */
    @Excel(name = "是否工作日")
    private Integer isWorkingDay;
}