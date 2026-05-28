package com.nexus.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchShift;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 班次定义视图对象 sch_shift
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchShift.class)
public class SchShiftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 班次ID
     */
    @ExcelProperty(value = "班次ID")
    private Long shiftId;

    /**
     * 班次编码
     */
    @ExcelProperty(value = "班次编码")
    private String shiftCode;

    /**
     * 班次名称
     */
    @ExcelProperty(value = "班次名称")
    private String shiftName;

    /**
     * 排班计划ID
     */
    @ExcelProperty(value = "排班计划ID")
    private Long planId;

    /**
     * 开始时间
     */
    @ExcelProperty(value = "开始时间")
    private Date startTime;

    /**
     * 结束时间
     */
    @ExcelProperty(value = "结束时间")
    private Date endTime;

    /**
     * 工时(小时)
     */
    @ExcelProperty(value = "工时")
    private BigDecimal workHours;

    /**
     * 是否跨天
     */
    @ExcelProperty(value = "是否跨天")
    private Integer isCrossDay;

    /**
     * 休息开始时间
     */
    @ExcelProperty(value = "休息开始时间")
    private Date restStartTime;

    /**
     * 休息结束时间
     */
    @ExcelProperty(value = "休息结束时间")
    private Date restEndTime;

    /**
     * 迟到阈值(分钟)
     */
    @ExcelProperty(value = "迟到阈值")
    private Long lateThreshold;

    /**
     * 早退阈值(分钟)
     */
    @ExcelProperty(value = "早退阈值")
    private Long earlyLeaveThreshold;

    /**
     * 班次类别
     */
    @ExcelProperty(value = "班次类别")
    private String shiftCategory;

    /**
     * 班次显示颜色
     */
    @ExcelProperty(value = "班次显示颜色")
    private String color;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sortOrder;
}
