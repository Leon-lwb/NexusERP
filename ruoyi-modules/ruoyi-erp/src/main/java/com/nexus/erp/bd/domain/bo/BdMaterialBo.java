package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdMaterial;

import java.math.BigDecimal;

/**
 * 物料产品主业务对象 bd_material
 *
 * @author nexus
 * @date 2025-10-04
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdMaterial.class, reverseConvertGenerate = false)
public class BdMaterialBo extends BaseEntity {

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     * <p>
     * 自动生成开关打开时由前端清空、后端兜底生成（避免预览消耗流水号），故不强制非空。
     */
    private String materialCode;

    /**
     * 物料名称
     */
    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    /**
     * 规格型号
     */
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
     * 分类ID集合（仅用于查询过滤：按所选分类及其子孙分类过滤物料，与 categoryId 同时传时取交集）
     */
    private Long[] categoryIds;

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
    private Long materialType;

    /**
     * 来源类型: PROCESS-加工 PROCUREMENT-采购 SUBCONTRACT-委外 PHANTOM-虚拟件 CFM-客户供料 TRANSFER-调拨
     */
    private String sourceType;

    /**
     * 是否虚拟件: 0-否 1-是
     */
    private Integer isPhantom;

    /**
     * 是否批次管理
     */
    private Long isBatchManage;

    /**
     * 是否序列号管理
     */
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
    private Long status;

    /**
     * 质量状态
     */
    private String qualityStatus;

    /**
     * 是否需要来料检验(0否/1是)
     */
    private Integer inspectionRequired;

    /**
     * 仓库ID（仅用于查询过滤：仅返回在该仓库存在库存记录的物料）
     */
    private Long warehouseId;

    /**
     * 库区ID（仅用于查询过滤：仅返回在该库区存在库存记录的物料）
     */
    private Long areaId;

    /**
     * 库位ID（仅用于查询过滤：仅返回在该库位存在库存记录的物料）
     */
    private Long locationId;

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
     * 批量删除ID集合
     */
    private Long[] ids;

}
