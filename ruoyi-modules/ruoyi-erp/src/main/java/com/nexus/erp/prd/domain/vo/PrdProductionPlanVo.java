package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.prd.domain.PrdProductionPlan;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产计划视图对象 prd_production_plan
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdProductionPlan.class)
public class PrdProductionPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 生产计划ID
     */
    @ExcelProperty("生产计划ID")
    private Long planId;

    /**
     * 生产计划编号
     */
    @ExcelProperty("生产计划编号")
    private String planNo;

    /**
     * 计划名称
     */
    @ExcelProperty("计划名称")
    private String planName;

    /**
     * 版本号
     */
    @ExcelProperty("版本号")
    private Long version;

    /**
     * 原始计划ID
     */
    private Long originalPlanId;

    /**
     * 参考单据号
     */
    private String referenceNo;

    /**
     * 计划类型
     */
    @ExcelProperty("计划类型")
    private String planType;

    /**
     * 计划日期
     */
    @ExcelProperty("计划日期")
    private Date planDate;

    /**
     * 计划开始日期
     */
    @ExcelProperty("计划开始日期")
    private Date startDate;

    /**
     * 计划结束日期
     */
    @ExcelProperty("计划结束日期")
    private Date endDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 优先级
     */
    private Long priority;

    /**
     * 计划员
     */
    @ExcelProperty("计划员")
    private String plannerName;

    /**
     * 生产部门
     */
    @ExcelProperty("生产部门")
    private String departmentName;

    /**
     * 计划总数量
     */
    @ExcelProperty("计划总数量")
    private BigDecimal totalQuantity;

    /**
     * 确认时间
     */
    private Date confirmedTime;

    /**
     * 下达时间
     */
    private Date releasedTime;

    /**
     * 关闭时间
     */
    private Date closedTime;

    /**
     * bomName
     */
    @ExcelProperty("bomName")
    private String bomName;

    /**
     * bomVersion
     */
    @ExcelProperty("bomVersion")
    private String bomVersion;

    /**
     * 是否当前版本
     */
    private Long isCurrent;

    /**
     * bomId
     */
    private Long bomId;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 物料需求单ID
     */
    private Long materialRequirementId;

    /**
     * 物料需求单编号
     */
    private String materialRequirementNo;

    /**
     * 销售订单子表id
     */
    private Long orderLineId;

    /**
     * 销售订单子表行号
     */
    private String orderLineNo;
}
