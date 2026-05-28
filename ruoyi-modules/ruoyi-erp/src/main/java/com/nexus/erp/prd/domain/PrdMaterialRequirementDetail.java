package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料需求单明细对象 prd_material_requirement_detail
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_material_requirement_detail")
public class PrdMaterialRequirementDetail extends TenantEntity {

    /**
     * 需求明细ID
     */
    @TableId(value = "detail_id")
    private Long detailId;

    /**
     * 物料需求单ID
     */
    @ExcelProperty("物料需求单ID")
    private Long requirementId;

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
     * 单位
     */
    @ExcelProperty("单位")
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
    @ExcelProperty("工序步骤")
    private Long operationStep;

    /**
     * 工作中心ID
     */
    @ExcelProperty("工作中心ID")
    private Long workstationId;

    /**
     * 工作中心名称
     */
    @ExcelProperty("工作中心名称")
    private String workstationName;

    /**
     * 提前期(小时)
     */
    @ExcelProperty("提前期(小时)")
    private Long leadTime;

    /**
     * 需求日期
     */
    @ExcelProperty("需求日期")
    private Date requiredDate;

    /**
     * 状态
     */
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    private String sourceType;
}
