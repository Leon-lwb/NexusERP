package org.dromara.pay.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 发票业务对象
 *
 * @author geek
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayInvoiceBo extends BaseEntity {

    /** 发票id */
    private Long invoiceId;

    /** 订单号 */
    private String orderNumber;

    /** 发票类型 */
    private String invoiceType;

    /** 发票抬头 */
    private String invoiceHeader;

    /** 纳税人识别号 */
    private String invoiceNumber;

    /** 收票人手机号 */
    private String invoicePhone;

    /** 收票人邮箱 */
    private String invoiceEmail;

    /** 发票备注 */
    private String invoiceRemark;

    /** 批量操作ID数组 */
    private Long[] ids;

}
