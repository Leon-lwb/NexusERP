package com.nexus.erp.bd.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 明确提交新供应资料，不从旧起订量、旧有效期或供应商名称推断认证事实。 */
public record BdMaterialVendorTermsConfirmBo(
    @Size(max = 64) String expectedVersion,
    @NotNull @Positive Long materialId,
    @NotNull @Positive Long vendorId,
    @NotNull @Positive Long orderUomId,
    @NotNull @Positive Long moqUomId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal verifiedMoqQty,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal orderMultiple,
    @NotNull Boolean allowSplit,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal unitsPerPack,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime termsEffectiveAt,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime termsExpiresAt,
    @NotBlank @Size(max = 256) String reference
) {
    /** 日期是上海业务墙上时间；核验时间不接受客户端声明。 */
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("供应条款确认请求包含不允许的字段");
    }
}
