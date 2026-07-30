package com.nexus.erp.fin.gl.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.gl.domain.GlCurrencyRate;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 币种汇率视图对象 gl_currency_rate
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GlCurrencyRate.class)
public class GlCurrencyRateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "汇率ID")
    private Long rateId;

    @ExcelProperty(value = "币种")
    private String currency;

    private String baseCurrency;

    @ExcelProperty(value = "汇率日期")
    private Date rateDate;

    @ExcelProperty(value = "汇率")
    private BigDecimal rate;

    private String rateType;

    private Integer version;
}
