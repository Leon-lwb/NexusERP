package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 物料产品主对象 bd_material
 *
 * @author nexus
 * @date 2025-10-04
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_material")
public class BdMaterial extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 物料ID
     */
    @TableId(value = "material_id")
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
     * 规格型号
     */
    @ExcelProperty("规格型号")
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
     * 基本计量单位ID
     */
    private Long baseUomId;

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
    @ExcelProperty("物料类型")
    private Long materialType;

    /**
     * 来源类型: PROCESS-加工 PROCUREMENT-采购 SUBCONTRACT-委外 PHANTOM-虚拟件 CFM-客户供料 TRANSFER-调拨
     */
    @ExcelProperty("来源类型")
    private String sourceType;

    /**
     * 是否虚拟件: 0-否 1-是（虚拟件展开时跳层，不入库不生成工单）
     */
    @ExcelProperty("是否虚拟件")
    private Integer isPhantom;

    /**
     * 是否批次管理
     */
    @ExcelProperty("是否批次管理")
    private Long isBatchManage;

    /**
     * 是否序列号管理
     */
    @ExcelProperty("是否序列号管理")
    private Long isSerialManage;

    /** 同一产品SN的同种组件是否允许来自多个批次，0否1是。 */
    private Integer allowMixedLot;

    /** 服务端管理的追踪策略版本，编辑策略时作为预期版本。 */
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long trackingVersion;

    /** AUDIT或STRICT，严格切换必须通过库存及在制就绪检查。 */
    private String trackingStage;


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
    @ExcelProperty("状态")
    private Long status;

    /**
     * 质量状态
     */
    @ExcelProperty("质量状态")
    private String qualityStatus;

    /**
     * 是否需要来料检验(0否/1是)
     */
    @ExcelProperty("是否需要来料检验")
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
     * 体积(m3)
     */
    private BigDecimal volume;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
