package com.nexus.core.prd.domain;

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
 * 生产计划对象 prd_production_plan
 *
 * @author nexus
 * @date 2025-11-13
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.prd.mapper.PrdProductionPlanMapper.PrdProductionPlanResult")
public class PrdProductionPlan extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 生产计划ID */
    @TableId(value = "plan_id",type = IdType.AUTO)
    private Long planId;
    /** 生产计划编号 */
    @Excel(name = "生产计划编号")
    private String planNo;
    /** 计划名称 */
    @Excel(name = "计划名称")
    private String planName;
    /** 版本号 */
    @Excel(name = "版本号")
    private Long version;
    /** 原始计划ID */
    @Excel(name = "原始计划ID")
    private Long originalPlanId;
    /** 参考单据号 */
    @Excel(name = "参考单据号")
    private String referenceNo;
    /** 计划类型 */
    @Excel(name = "计划类型")
    private String planType;
    /** 计划日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "计划日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date planDate;
    /** 计划开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "计划开始日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startDate;
    /** 计划结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "计划结束日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date endDate;
    /** 状态 */
    @Excel(name = "状态")
    private String status;
    /** 优先级 */
    @Excel(name = "优先级")
    private Long priority;
    /** 计划员ID */
    private Long plannerId;
    /** 计划员 */
    @Excel(name = "计划员")
    private String plannerName;
    /** 生产部门ID */
    private Long departmentId;
    /** 生产部门 */
    @Excel(name = "生产部门")
    private String departmentName;
    /** 计划总数量 */
    @Excel(name = "计划总数量")
    private BigDecimal totalQuantity;
    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "确认时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date confirmedTime;
    /** 下达时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "下达时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date releasedTime;
    /** 关闭时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "关闭时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date closedTime;
    /** 预留字段1 */
    @Excel(name = "预留字段1")
    private String attr1;
    /** 预留字段2 */
    @Excel(name = "预留字段2")
    private String attr2;
    /** 预留字段3 */
    @Excel(name = "预留字段3")
    private Long attr3;
    /** 预留字段4 */
    @Excel(name = "预留字段4")
    private Long attr4;
}