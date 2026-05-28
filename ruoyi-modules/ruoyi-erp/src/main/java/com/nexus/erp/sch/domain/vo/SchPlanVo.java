package com.nexus.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchPlan;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 排班计划视图对象 sch_plan
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchPlan.class)
public class SchPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    @ExcelProperty(value = "计划ID")
    private Long planId;

    /**
     * 计划编码
     */
    @ExcelProperty(value = "计划编码")
    private String planCode;

    /**
     * 计划名称
     */
    @ExcelProperty(value = "计划名称")
    private String planName;

    /**
     * 开始日期
     */
    @ExcelProperty(value = "开始日期")
    private Date startDate;

    /**
     * 结束日期
     */
    @ExcelProperty(value = "结束日期")
    private Date endDate;

    /**
     * 周期类型
     */
    @ExcelProperty(value = "周期类型")
    private String periodType;

    /**
     * 轮班方式
     */
    @ExcelProperty(value = "轮班方式")
    private Integer rotationType;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 发布人
     */
    @ExcelProperty(value = "发布人")
    private String publishedBy;

    /**
     * 发布时间
     */
    @ExcelProperty(value = "发布时间")
    private Date publishedTime;
}
