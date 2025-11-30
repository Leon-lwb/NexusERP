package com.nexus.core.prd.domain;

import java.math.BigDecimal;
import java.util.List;
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
 * 物料清单对象 prd_material_requirement
 *
 * @author nexus
 * @date 2025-11-21
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.prd.mapper.PrdMaterialRequirementMapper.PrdMaterialRequirementResult")
public class PrdMaterialRequirement extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 物料需求单ID */
    @TableId(value = "requirement_id",type = IdType.AUTO)
    private Long requirementId;
    /** 物料需求单编号 */
    @Excel(name = "物料需求单编号")
    private String requirementNo;
    /** 生产计划ID */
    @Excel(name = "生产计划ID")
    private Long planId;
    /** 生产计划编号 */
    @Excel(name = "生产计划编号")
    private String planNo;
    /** BOM ID */
    @Excel(name = "BOM ID")
    private Long bomId;
    /** 需求日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "需求日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date requirementDate;
    /** 物料种类总数 */
    @Excel(name = "物料种类总数")
    private Long totalMaterialTypes;
    /** 需求总量 */
    @Excel(name = "需求总量")
    private BigDecimal totalRequiredQuantity;
    /** 状态 */
    @Excel(name = "状态")
    private String status;
    /** 计划员ID */
    @Excel(name = "计划员ID")
    private Long plannerId;
    /** 计划员 */
    @Excel(name = "计划员")
    private String plannerName;
    /** 部门ID */
    @Excel(name = "部门ID")
    private Long departmentId;
    /** 部门 */
    @Excel(name = "部门")
    private String departmentName;
    /** 确认时间 */
    private Date confirmedTime;
    /** 发料时间 */
    private Date issuedTime;
    /** 关闭时间 */
    private Date closedTime;
    
    /** 物料ID */
    @Excel(name = "物料ID")
    private Long materialId;
    /** 物料编码 */
    @Excel(name = "物料编码")
    private String materialCode;
    /** 物料名称 */
    @Excel(name = "物料名称")
    private String materialName;
    /** 物料规格 */
    @Excel(name = "物料规格")
    private String materialSpec;

    /** 物料需求单行列表 */
    private List<PrdMaterialRequirementDetail> prdMaterialRequirementDetailList;
}