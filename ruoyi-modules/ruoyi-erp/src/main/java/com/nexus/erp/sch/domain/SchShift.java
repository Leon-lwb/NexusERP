package com.nexus.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 班次定义对象 sch_shift
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_shift")
public class SchShift extends TenantEntity {

    /**
     * 班次ID
     */
    @TableId(value = "shift_id")
    private Long shiftId;
    /**
     * 班次编码
     */
    @ExcelProperty("班次编码")
    private String shiftCode;
    /**
     * 班次名称
     */
    @ExcelProperty("班次名称")
    private String shiftName;
    /**
     * 排班计划ID
     */
    @ExcelProperty("排班计划ID")
    private Long planId;
    /**
     * 开始时间
     */
    @ExcelProperty("开始时间")
    private Date startTime;
    /**
     * 结束时间
     */
    @ExcelProperty("结束时间")
    private Date endTime;
    /**
     * 工时(小时)
     */
    @ExcelProperty("工时(小时)")
    private BigDecimal workHours;
    /**
     * 是否跨天(0否1是)
     */
    @ExcelProperty("是否跨天")
    private Integer isCrossDay;
    /**
     * 休息开始时间
     */
    @ExcelProperty("休息开始时间")
    private Date restStartTime;
    /**
     * 休息结束时间
     */
    @ExcelProperty("休息结束时间")
    private Date restEndTime;
    /**
     * 迟到阈值(分钟)
     */
    @ExcelProperty("迟到阈值")
    private Long lateThreshold;
    /**
     * 早退阈值(分钟)
     */
    @ExcelProperty("早退阈值")
    private Long earlyLeaveThreshold;
    /**
     * 班次类别
     */
    @ExcelProperty("班次类别")
    private String shiftCategory;
    /**
     * 班次显示颜色
     */
    @ExcelProperty("班次显示颜色")
    private String color;
    /**
     * 排序
     */
    @ExcelProperty("排序")
    private Long sortOrder;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
