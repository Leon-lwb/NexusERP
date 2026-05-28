package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料需求单明细视图对象 prd_material_requirement_detail
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdMaterialRequirementDetail.class)
public class PrdMaterialRequirementDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求明细ID
     */
    @ExcelProperty("需求明细ID")
    private Long detailId;

    /**
     * 物料需求单ID
     */
    @ExcelProperty("物料需求单ID")
    private Long requirementId;

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
     * 单位
     */
    private String unit;

    /**
     * BOM标准用量
     */
    @ExcelProperty("BOM标准用量")
    private BigDecimal bomQuantity;

    /**
     * 损耗率(%)
     */
    @ExcelProperty("损耗率(%)")
    private BigDecimal lossRate;

    /**
     * 需求数量
     */
    @ExcelProperty("需求数量")
    private BigDecimal requiredQuantity;

    /**
     * 已发料数量
     */
    @ExcelProperty("已发料数量")
    private BigDecimal issuedQuantity;

    /**
     * 当前库存数量
     */
    @ExcelProperty("当前库存数量")
    private BigDecimal stockQuantity;

    /**
     * 缺料数量
     */
    @ExcelProperty("缺料数量")
    private BigDecimal shortageQuantity;

    /**
     * 工序步骤
     */
    private Long operationStep;

    /**
     * 工作中心ID
     */
    private Long workstationId;

    /**
     * 工作中心名称
     */
    private String workstationName;

    /**
     * 提前期(小时)
     */
    private Long leadTime;

    /**
     * 需求日期
     */
    private Date requiredDate;

    /**
     * 状态
     */
    private Long status;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    @ExcelProperty("来源类型")
    private String sourceType;
}
