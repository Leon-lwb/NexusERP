package org.dromara.pay.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.pay.domain.PayInvoice;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 发票视图对象
 *
 * @author geek
 */
@Data
@AutoMapper(target = PayInvoice.class)
@ExcelIgnoreUnannotated
public class PayInvoiceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发票id */
    @ExcelProperty(value = "发票ID")
    private Long invoiceId;

    /** 订单号 */
    @ExcelProperty(value = "订单号")
    private String orderNumber;

    /** 发票类型 */
    @ExcelProperty(value = "发票类型")
    private String invoiceType;

    /** 发票抬头 */
    @ExcelProperty(value = "发票抬头")
    private String invoiceHeader;

    /** 纳税人识别号 */
    @ExcelProperty(value = "纳税人识别号")
    private String invoiceNumber;

    /** 收票人手机号 */
    @ExcelProperty(value = "收票人手机号")
    private String invoicePhone;

    /** 收票人邮箱 */
    @ExcelProperty(value = "收票人邮箱")
    private String invoiceEmail;

    /** 发票备注 */
    @ExcelProperty(value = "发票备注")
    private String invoiceRemark;

    /** 创建者 */
    @ExcelProperty(value = "创建者")
    private Long createBy;

    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @ExcelProperty(value = "更新者")
    private Long updateBy;

    /** 更新时间 */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @ExcelProperty(value = "备注")
    private String remark;

}
