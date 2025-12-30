package com.nexus.core.sch.domain;

import java.math.BigDecimal;
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
 * 班次定义对象 sch_shift
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchShiftMapper.SchShiftResult")
public class SchShift extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 班次ID */
    @TableId(value = "shift_id",type = IdType.AUTO)
    private Long shiftId;
    /** 班次编码 */
    @Excel(name = "班次编码")
    private String shiftCode;
    /** 班次名称 */
    @Excel(name = "班次名称")
    private String shiftName;
    /** 排班计划ID */
    @Excel(name = "排班计划ID")
    private Long planId;
    /** 开始时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    @Excel(name = "开始时间", width = 30, dateFormat = "HH:mm:ss")
    private Date startTime;
    /** 结束时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    @Excel(name = "结束时间", width = 30, dateFormat = "HH:mm:ss")
    private Date endTime;
    /** 工时(小时) */
    @Excel(name = "工时(小时)")
    private BigDecimal workHours;
    /** 是否跨天(0否1是) */
    @Excel(name = "是否跨天(0否1是)")
    private Integer isCrossDay;
    /** 休息开始时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    @Excel(name = "休息开始时间", width = 30, dateFormat = "HH:mm:ss")
    private Date restStartTime;
    /** 休息结束时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    @Excel(name = "休息结束时间", width = 30, dateFormat = "HH:mm:ss")
    private Date restEndTime;
    /** 迟到阈值(分钟) */
    @Excel(name = "迟到阈值(分钟)")
    private Long lateThreshold;
    /** 早退阈值(分钟) */
    @Excel(name = "早退阈值(分钟)")
    private Long earlyLeaveThreshold;
    /** 班次类别 */
    @Excel(name = "班次类别")
    private String shiftCategory;
    /** 班次显示颜色 */
    @Excel(name = "班次显示颜色")
    private String color;
    /** 排序 */
    @Excel(name = "排序")
    private Long sortOrder;
}