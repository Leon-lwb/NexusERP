package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 物料清单对象 prd_material_requirement
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_material_requirement")
public class PrdMaterialRequirement extends TenantEntity {

    /**
     * 物料需求单ID
     */
    @TableId(value = "requirement_id")
    private Long requirementId;

    /**
     * 物料需求单编号
     */
    @ExcelProperty("物料需求单编号")
    private String requirementNo;

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
     * 父级物料需求单ID
     */
    private Long parentRequirementId;

    /**
     * BOM ID
     */
    @ExcelProperty("BOM ID")
    private Long bomId;

    /**
     * 需求日期
     */
    @ExcelProperty("需求日期")
    private Date requirementDate;

    /**
     * 物料种类总数
     */
    @ExcelProperty("物料种类总数")
    private Long totalMaterialTypes;

    /**
     * 需求总量
     */
    @ExcelProperty("需求总量")
    private BigDecimal totalRequiredQuantity;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 计划员ID
     */
    @ExcelProperty("计划员ID")
    private Long plannerId;

    /**
     * 计划员
     */
    @ExcelProperty("计划员")
    private String plannerName;

    /**
     * 部门ID
     */
    @ExcelProperty("部门ID")
    private Long departmentId;

    /**
     * 部门
     */
    @ExcelProperty("部门")
    private String departmentName;

    /**
     * 确认时间
     */
    private Date confirmedTime;

    /**
     * 发料时间
     */
    private Date issuedTime;

    /**
     * 关闭时间
     */
    private Date closedTime;

    /**
     * 物料ID
     */
    @ExcelProperty("物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 物料规格
     */
    @ExcelProperty("物料规格")
    private String materialSpec;

    /**
     * 备注
     */
    private String remark;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    @ExcelProperty("来源类型")
    private String sourceType;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 物料需求单行列表
     */
    @TableField(exist = false)
    private List<PrdMaterialRequirementDetail> prdMaterialRequirementDetailList;
}
