package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdProductionPlan;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产计划业务对象 prd_production_plan
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdProductionPlan.class, reverseConvertGenerate = false)
public class PrdProductionPlanBo extends BaseEntity {

    /**
     * 生产计划ID
     */
    private Long planId;

    /**
     * 生产计划编号
     */
    @NotBlank(message = "生产计划编号不能为空")
    private String planNo;

    /**
     * 计划名称
     */
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /**
     * 版本号
     */
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
    private String planType;

    /**
     * 计划日期
     */
    private Date planDate;

    /**
     * 计划开始日期
     */
    private Date startDate;

    /**
     * 计划结束日期
     */
    private Date endDate;

    /**
     * 状态
     */
    private String status;

    /**
     * 优先级
     */
    private Long priority;

    /**
     * 计划员ID
     */
    private Long plannerId;

    /**
     * 计划员
     */
    private String plannerName;

    /**
     * 生产部门ID
     */
    private Long departmentId;

    /**
     * 生产部门
     */
    private String departmentName;

    /**
     * 计划总数量
     */
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
    private String bomName;

    /**
     * bomVersion
     */
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

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
