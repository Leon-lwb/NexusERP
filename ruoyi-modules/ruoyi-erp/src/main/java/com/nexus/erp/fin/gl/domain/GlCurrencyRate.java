package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 币种汇率 gl_currency_rate
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_currency_rate")
public class GlCurrencyRate extends TenantEntity {

    @TableId(value = "rate_id")
    private Long rateId;

    /** 币种(如 USD) */
    private String currency;

    /** 本位币 */
    private String baseCurrency;

    /** 汇率日期 */
    private Date rateDate;

    /** 汇率(本币=原币×rate) */
    private BigDecimal rate;

    /** 类型 MIDDLE/BID/ASK */
    private String rateType;

    @Version
    private Integer version;

    @TableLogic
    private String delFlag;
}
