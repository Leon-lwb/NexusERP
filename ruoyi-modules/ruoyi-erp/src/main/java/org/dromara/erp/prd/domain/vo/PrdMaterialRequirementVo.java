package org.dromara.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 物料清单视图对象 prd_material_requirement
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdMaterialRequirement.class)
public class PrdMaterialRequirementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 物料需求单ID
     */
    @ExcelProperty("物料需求单ID")
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
    private Long plannerId;

    /**
     * 计划员
     */
    private String plannerName;

    /**
     * 部门ID
     */
    private Long departmentId;

    /**
     * 部门
     */
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
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    private String sourceType;

    /**
     * 物料需求单行列表
     */
    private List<PrdMaterialRequirementDetail> prdMaterialRequirementDetailList;
}
