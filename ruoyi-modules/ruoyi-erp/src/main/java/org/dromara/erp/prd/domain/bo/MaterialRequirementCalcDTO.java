package org.dromara.erp.prd.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 物料需求计算DTO
 *
 * @author ruoyi
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MaterialRequirementCalcDTO {
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String categoryName;
    private String categoryPath;
    private BigDecimal totalRequiredQuantity;
    private Integer bomLevel;
    private String materialPath;
    private BigDecimal quantityPerParent;
    private BigDecimal lossRate;
    private Long operationStep;
    private Long workstationId;
    private String workstationName;
    private String sourceType;
}
