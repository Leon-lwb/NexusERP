package com.nexus.pay.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * 订单业务对象
 *
 * @author geek
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayOrderBo extends BaseEntity {

    /** 订单ID */
    private Long orderId;

    /** 商户订单号 */
    private String orderNumber;

    /** 第三方订单号 */
    private String thirdNumber;

    /** 订单状态 */
    private String orderStatus;

    /** 订单总金额 */
    private String totalAmount;

    /** 实际支付金额 */
    private String actualAmount;

    /** 订单内容 */
    private String orderContent;

    /** 负载信息 */
    private String orderMessage;

    /** 支付方式 */
    private String payType;

    /** 支付人 */
    private String payBy;

    /** 批量操作ID数组 */
    private Long[] ids;

}
