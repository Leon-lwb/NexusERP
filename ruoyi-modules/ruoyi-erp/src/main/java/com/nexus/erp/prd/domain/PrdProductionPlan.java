package com.nexus.erp.prd.domain;

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
 * 生产计划对象 prd_production_plan
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_production_plan")
public class PrdProductionPlan extends TenantEntity {

    /**
     * 生产计划ID
     */
    @TableId(value = "plan_id")
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
    @ExcelProperty("原始计划ID")
    private Long originalPlanId;

    /**
     * 参考单据号
     */
    @ExcelProperty("参考单据号")
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
    @ExcelProperty("优先级")
    private Long priority;

    /**
     * 计划员ID
     */
    private Long plannerId;

    /**
     * 计划员
     */
    @ExcelProperty("计划员")
    private String plannerName;

    /**
     * 生产部门ID
     */
    private Long departmentId;

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
    @ExcelProperty("确认时间")
    private Date confirmedTime;

    /**
     * 下达时间
     */
    @ExcelProperty("下达时间")
    private Date releasedTime;

    /**
     * 关闭时间
     */
    @ExcelProperty("关闭时间")
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
    @ExcelProperty("是否当前版本")
    private Long isCurrent;

    /**
     * bomId
     */
    @ExcelProperty("bomId")
    private Long bomId;

    /**
     * 预留字段4
     */
    @ExcelProperty("预留字段4")
    private Long attr4;

    /**
     * 物料需求单ID
     */
    @ExcelProperty("物料需求单ID")
    private Long materialRequirementId;

    /**
     * 物料需求单编号
     */
    @ExcelProperty("物料需求单编号")
    private String materialRequirementNo;

    /**
     * 销售订单子表id
     */
    @ExcelProperty("销售订单子表id")
    private Long orderLineId;

    /**
     * 销售订单子表行号
     */
    @ExcelProperty("销售订单子表行号")
    private String orderLineNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
