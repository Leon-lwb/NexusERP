package com.nexus.erp.prd.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 物料需求计算DTO
 *
 * @author nexus
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MaterialRequirementCalcDTO {
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 分类名称 */
    private String categoryName;
    /** 分类路径 */
    private String categoryPath;
    /** 总需求数量 */
    private BigDecimal totalRequiredQuantity;
    /** BOM层级 */
    private Integer bomLevel;
    /** 物料路径 */
    private String materialPath;
    /** 单位用量 */
    private BigDecimal quantityPerParent;
    /** 损耗率 */
    private BigDecimal lossRate;
    /** 工序步骤 */
    private Long operationStep;
    /** 工位ID */
    private Long workstationId;
    /** 工位名称 */
    private String workstationName;
    /** 提前期（天） */
    private Long leadTime;
    /** 来源类型 */
    private String sourceType;
}
