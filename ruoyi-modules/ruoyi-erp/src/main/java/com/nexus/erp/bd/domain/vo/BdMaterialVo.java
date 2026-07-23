package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdMaterial;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料产品主视图对象 bd_material
 *
 * @author nexus
 * @date 2025-10-04
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdMaterial.class)
public class BdMaterialVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 物料ID
     */
    @ExcelProperty(value = "物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /**
     * 物料描述
     */
    private String materialDesc;

    /**
     * 物料分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    @ExcelProperty(value = "分类名称")
    private String categoryName;

    /**
     * 基本计量单位ID
     */
    private Long baseUomId;

    /**
     * 基本计量单位名称
     */
    @ExcelProperty(value = "基本计量单位")
    private String baseUomName;

    /**
     * 采购单位ID
     */
    private Long purchaseUomId;

    /**
     * 库存单位ID
     */
    private Long inventoryUomId;

    /**
     * 销售单位ID
     */
    private Long saleUomId;

    /**
     * 物料类型
     */
    @ExcelProperty(value = "物料类型")
    private Long materialType;

    /**
     * 来源类型: PROCESS-加工 PROCUREMENT-采购 SUBCONTRACT-委外 PHANTOM-虚拟件 CFM-客户供料 TRANSFER-调拨
     */
    @ExcelProperty(value = "来源类型")
    private String sourceType;

    /**
     * 是否虚拟件: 0-否 1-是
     */
    @ExcelProperty(value = "是否虚拟件")
    private Integer isPhantom;

    /**
     * 是否批次管理
     */
    @ExcelProperty(value = "是否批次管理")
    private Long isBatchManage;

    /**
     * 是否序列号管理
     */
    @ExcelProperty(value = "是否序列号管理")
    private Long isSerialManage;

    /**
     * 安全库存
     */
    private BigDecimal safetyStock;

    /**
     * 最低库存
     */
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    private BigDecimal maxStock;

    /**
     * 标准成本
     */
    private BigDecimal standardCost;

    /**
     * 参考价格
     */
    private BigDecimal referencePrice;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 质量状态
     */
    @ExcelProperty(value = "质量状态")
    private String qualityStatus;

    /**
     * 是否需要来料检验(0否/1是)
     */
    @ExcelProperty(value = "是否需要来料检验")
    private Integer inspectionRequired;

    /**
     * 保质期(天)
     */
    private Long shelfLife;

    /**
     * 重量(kg)
     */
    private BigDecimal weight;

    /**
     * 体积(m³)
     */
    private BigDecimal volume;

}
