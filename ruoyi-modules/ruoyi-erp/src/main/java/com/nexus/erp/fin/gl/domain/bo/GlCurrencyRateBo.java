package com.nexus.erp.fin.gl.domain.bo;

import com.nexus.erp.fin.gl.domain.GlCurrencyRate;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 币种汇率业务对象 gl_currency_rate
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = GlCurrencyRate.class, reverseConvertGenerate = false)
public class GlCurrencyRateBo extends BaseEntity {

    private Long rateId;

    @NotBlank(message = "币种不能为空")
    private String currency;

    private String baseCurrency;

    @NotNull(message = "汇率日期不能为空")
    private Date rateDate;

    @NotNull(message = "汇率不能为空")
    private BigDecimal rate;

    private String rateType;
}
