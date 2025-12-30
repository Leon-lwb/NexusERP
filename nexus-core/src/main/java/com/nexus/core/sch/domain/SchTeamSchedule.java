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
 * 班组排班（模板）对象 sch_team_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchTeamScheduleMapper.SchTeamScheduleResult")
public class SchTeamSchedule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 班组排班ID
     */
    @TableId(value = "team_schedule_id", type = IdType.AUTO)
    private Long teamScheduleId;
    /**
     * 排班计划ID
     */
    @Excel(name = "排班计划ID")
    private Long planId;
    /**
     * 班组ID
     */
    @Excel(name = "班组ID")
    private Long teamId;
    /**
     * 班次ID
     */
    @Excel(name = "班次ID")
    private Long shiftId;
    /**
     * 排班日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "排班日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date scheduledDate;
    /**
     * 星期几(1-7)
     */
    @Excel(name = "星期几(1-7)")
    private Long weekDay;
    /**
     * 是否节假日
     */
    @Excel(name = "是否节假日")
    private Integer isHoliday;
}