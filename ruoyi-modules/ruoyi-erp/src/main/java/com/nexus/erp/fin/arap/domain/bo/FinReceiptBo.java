package com.nexus.erp.fin.arap.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 收付款单业务对象 fin_receipt
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FinReceipt.class, reverseConvertGenerate = false)
public class FinReceiptBo extends BaseEntity {

    /** 收付款单ID */
    private Long receiptId;

    /** 收付款单号(自动生成,可空) */
    private String receiptNo;

    /** 收付款类型 CASH/BANK/ALIPAY/WECHAT/OTHER */
    private String receiptType;

    @NotBlank(message = "方向不能为空(AR/AP)")
    private String direction;

    /** 客户ID(direction=AR) */
    private Long clientId;

    /** 供应商ID(direction=AP) */
    private Long vendorId;

    /** 付款方名称 */
    private String payerName;

    @NotNull(message = "收付款金额不能为空")
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 第三方流水号 */
    private String thirdPartyNo;

    /** 支付订单号(跨库关联 pay_order) */
    private String payOrderNumber;

    /**
     * 来源 MANUAL/PAY_CALLBACK。
     * <p><b>只读（查询条件）</b>：新增/修改接口不接受该字段，服务端强制 MANUAL；
     * PAY_CALLBACK 仅由 {@code PayReceivedConsumer} 内部走 {@code save(entity)} 产生（FIN-ARAP-01）。</p>
     */
    private String source;

    /**
     * 状态 DRAFT/CONFIRMED/SETTLED/CANCELLED。
     * <p><b>只读（查询条件）</b>：新增接口只接受 DRAFT（服务端强制置 DRAFT），修改接口不允许变更状态；
     * DRAFT -&gt; CONFIRMED 必须走 {@code confirm()}（唯一会发出 GL 过账消息的入口），
     * 否则总账永久缺失凭证（FIN-ARAP-01）。</p>
     */
    private String status;

    /** 收付款日期(凭证日期) */
    private Date receiptDate;

    /** 备注 */
    private String remark;

    /** 批量操作ID */
    private Long[] ids;
}
