package com.nexus.erp.bd.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.bd.domain.BdBomDetail;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdBomDetail.class, reverseConvertGenerate = false)
public class BdBomDetailBo extends BaseEntity {

    private Long detailId;

    @NotNull(message = "BOM主表ID不能为空")
    private Long bomId;

    @NotNull(message = "子项物料ID不能为空")
    private Long childMaterialId;

    private String childMaterialCode;

    private String childMaterialName;

    @NotNull(message = "单位父项用量不能为空")
    @DecimalMin(value = "0.000001", message = "用量必须大于0")
    @Digits(integer = 12, fraction = 6, message = "用量格式不正确")
    private BigDecimal quantityPerParent;

    @DecimalMin(value = "0", message = "损耗率不能小于0")
    @DecimalMax(value = "100", message = "损耗率不能大于100")
    private BigDecimal lossRate;

    @NotNull(message = "工序步骤不能为空")
    private Integer operationStep;

    private Long workstationId;

    private String workstationName;

    /**
     * 工序ID(关联bd_process)
     */
    private Long processId;

    /**
     * 工艺路线步骤ID(关联bd_routing_step)
     */
    private Long routingStepId;

    private Integer leadTime;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;

    private Integer sortOrder;

    private Integer status;

    private String remark;

    private String sourceType;

    private String attr1;
    private String attr2;
    private Integer attr3;
    private Integer attr4;
}
