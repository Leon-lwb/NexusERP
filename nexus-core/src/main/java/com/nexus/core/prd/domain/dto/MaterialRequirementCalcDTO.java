package com.nexus.core.prd.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}