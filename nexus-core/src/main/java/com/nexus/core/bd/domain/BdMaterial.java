package com.nexus.core.bd.domain;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 物料产品主对象 bd_material
 *
 * @author nexus
 * @date 2025-10-04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdMaterialMapper.BdMaterialResult")
public class BdMaterial extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 物料ID */
    @TableId(value = "material_id",type = IdType.AUTO)
    private Long materialId;
    /** 物料编码 */
    @Excel(name = "物料编码")
    private String materialCode;
    /** 物料名称 */
    @Excel(name = "物料名称")
    private String materialName;
    /** 规格型号 */
    @Excel(name = "规格型号")
    private String materialSpec;
    /** 物料描述 */
    private String materialDesc;
    /** 物料分类ID */
    private Long categoryId;
    /** 基本计量单位ID */
    private Long baseUomId;
    /** 采购单位ID */
    private Long purchaseUomId;
    /** 库存单位ID */
    private Long inventoryUomId;
    /** 销售单位ID */
    private Long saleUomId;
    /** 物料类型 */
    @Excel(name = "物料类型")
    private Long materialType;
    /** 来源类型 */
    @Excel(name = "来源类型")
    private Long sourceType;
    /** 是否批次管理 */
    @Excel(name = "是否批次管理")
    private Long isBatchManage;
    /** 是否序列号管理 */
    @Excel(name = "是否序列号管理")
    private Long isSerialManage;
    /** 安全库存 */
    private BigDecimal safetyStock;
    /** 最低库存 */
    private BigDecimal minStock;
    /** 最高库存 */
    private BigDecimal maxStock;
    /** 标准成本 */
    private BigDecimal standardCost;
    /** 参考价格 */
    private BigDecimal referencePrice;
    /** 状态 */
    @Excel(name = "状态")
    private Long status;
    /** 质量状态 */
    @Excel(name = "质量状态")
    private Long qualityStatus;
    /** 保质期(天) */
    private Long shelfLife;
    /** 重量(kg) */
    private BigDecimal weight;
    /** 体积(m³) */
    private BigDecimal volume;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}