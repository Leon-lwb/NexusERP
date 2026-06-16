package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdBomAlternative;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BOM替代料业务对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdBomAlternative.class, reverseConvertGenerate = false)
public class BdBomAlternativeBo extends BaseEntity {

    private Long alternativeId;

    @NotNull(message = "BOM明细ID不能为空")
    private Long detailId;

    @NotNull(message = "BOM主表ID不能为空")
    private Long bomId;

    @NotNull(message = "替代物料ID不能为空")
    private Long altMaterialId;

    private String altMaterialCode;

    private String altMaterialName;

    @NotNull(message = "替代用量不能为空")
    private BigDecimal altQuantity;

    private Integer priority;

    private String substitutionType;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;

    private Integer status;

    private String remark;
}
