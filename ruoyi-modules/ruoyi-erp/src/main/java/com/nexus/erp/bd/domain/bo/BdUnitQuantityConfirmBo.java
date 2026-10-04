package com.nexus.erp.bd.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** 只接收实际单位数量资料；租户、操作者、核验时间与新版本由服务端决定。 */
public record BdUnitQuantityConfirmBo(
    @Size(max = 64) String expectedVersion,
    @NotNull @Positive Long primaryId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal conversionRate,
    @NotNull @Min(0) @Max(4) Integer quantityScale,
    @NotBlank @Size(max = 256) String reference
) {
    /** 不能借全局忽略未知属性的设置夹带核验身份或认证字段。 */
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("单位确认请求包含不允许的字段");
    }
}
