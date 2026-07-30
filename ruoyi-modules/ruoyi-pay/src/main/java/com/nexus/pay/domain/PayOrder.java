package com.nexus.pay.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单对象 pay_order
 *
 * @author geek
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_order")
public class PayOrder extends TenantEntity {

    /** 订单ID */
    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    /** 商户订单号 */
    @ExcelProperty("商户订单号")
    private String orderNumber;

    /** 第三方订单号 */
    @ExcelProperty("第三方订单号")
    private String thirdNumber;

    /** 订单状态 */
    @ExcelProperty("订单状态")
    private String orderStatus;

    /** 业务类型 SO_ORDER/AR_RECEIPT(跨库关联业务单据,F1 AR/AP 用) */
    private String bizType;

    /** 业务单据ID(销售订单ID等) */
    private Long bizId;

    /** 客户ID(AR 收款核销用) */
    private Long clientId;

    /** 订单总金额（单位：元） */
    @ExcelProperty("订单总金额")
    private BigDecimal totalAmount;

    /** 实际支付金额（单位：元） */
    @ExcelProperty("实际支付金额")
    private BigDecimal actualAmount;

    /** 订单内容 */
    @ExcelProperty("订单内容")
    private String orderContent;

    /** 负载信息 */
    @ExcelProperty("负载信息")
    private String orderMessage;

    /** 支付方式 */
    @ExcelProperty("支付方式")
    private String payType;

    /** 支付时间 */
    @ExcelProperty("支付时间")
    private Date payTime;

    /** 支付人 */
    @ExcelProperty("支付人")
    private String payBy;

    /** 备注 */
    private String remark;

}
