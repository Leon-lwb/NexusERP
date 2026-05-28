package com.nexus.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuVendorEvaluation;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuVendorEvaluation.class, reverseConvertGenerate = false)
public class PuVendorEvaluationBo extends BaseEntity {

    @NotNull(message = "评估ID不能为空", groups = {com.nexus.common.core.validate.EditGroup.class})
    private Long evaluationId;

    @NotNull(message = "供应商ID不能为空")
    private Long vendorId;

    private String vendorCode;

    private String vendorName;

    @NotBlank(message = "评估周期不能为空")
    private String evalPeriod;

    private BigDecimal deliveryRate;

    private BigDecimal qualityRate;

    private BigDecimal priceScore;

    private BigDecimal totalScore;

    private String evalLevel;

    private Integer totalOrders;

    private Integer onTimeOrders;

    private BigDecimal totalReceivedQty;

    private BigDecimal qualifiedQty;

    private BigDecimal returnQty;

    private String remark;

    private String attr1;

    private String attr2;

    private Long attr3;

    private Long attr4;
}
