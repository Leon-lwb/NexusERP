package org.dromara.pay.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 发票对象 pay_invoice
 *
 * @author geek
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_invoice")
public class PayInvoice extends TenantEntity {

    /** 发票id */
    @TableId(value = "invoice_id", type = IdType.AUTO)
    private Long invoiceId;

    /** 订单号 */
    @ExcelProperty("订单号")
    private String orderNumber;

    /** 发票类型 */
    @ExcelProperty("发票类型")
    private String invoiceType;

    /** 发票抬头 */
    @ExcelProperty("发票抬头")
    private String invoiceHeader;

    /** 纳税人识别号 */
    @ExcelProperty("纳税人识别号")
    private String invoiceNumber;

    /** 收票人手机号 */
    @ExcelProperty("收票人手机号")
    private String invoicePhone;

    /** 收票人邮箱 */
    @ExcelProperty("收票人邮箱")
    private String invoiceEmail;

    /** 发票备注 */
    @ExcelProperty("发票备注")
    private String invoiceRemark;

    /** 备注 */
    private String remark;

}
