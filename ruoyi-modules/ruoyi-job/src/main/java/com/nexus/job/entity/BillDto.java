package com.nexus.job.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账单DTO
 *
 * @author wanghuan
 */
@Data
public class BillDto {

    /**
     * 账单ID
     */
    private Long billId;

    /**
     * 账单渠道
     */
    private String billChannel;

    /**
     * 账单日期
     */
    private String billDate;

    /**
     * 账单金额
     */
    private BigDecimal billAmount;

}
