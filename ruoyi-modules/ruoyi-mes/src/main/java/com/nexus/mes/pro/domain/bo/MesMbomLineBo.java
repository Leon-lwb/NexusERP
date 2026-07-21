package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.nexus.mes.pro.domain.MesMbomLine;

import java.math.BigDecimal;

@Data
@AutoMapper(target = MesMbomLine.class, reverseConvertGenerate = false)
public class MesMbomLineBo {
    /** MBOMLineId */
    private Long mbomLineId;
    /** MBOMID */
    private Long mbomId;
    /** BOMDetailId */
    private Long bomDetailId;

    @NotNull(message = "子项物料ID不能为空")
    private Long childMaterialId;

    @NotNull(message = "单位父项用量不能为空")
    @DecimalMin(value = "0.000001", message = "用量必须大于0")
    private BigDecimal quantityPerParent;

    @DecimalMin(value = "0", message = "损耗率不能小于0")
    private BigDecimal lossRate;

    /** 工艺路线StepId */
    private Long routingStepId;
    /** 工序ID */
    private Long processId;
    /** 工单rkstationId */
    private Long workstationId;
    /** operationContent */
    private String operationContent;
    /** 标志(0否 1是) */
    private Integer backflushFlag;
    /** sortOrder */
    private Integer sortOrder;
    /** remark */
    private String remark;
}
