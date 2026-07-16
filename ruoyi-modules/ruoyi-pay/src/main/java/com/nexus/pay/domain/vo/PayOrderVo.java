package com.nexus.pay.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.pay.domain.PayOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单视图对象
 *
 * @author geek
 */
@Data
@AutoMapper(target = PayOrder.class)
@ExcelIgnoreUnannotated
public class PayOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    @ExcelProperty(value = "订单ID")
    private Long orderId;

    /** 商户订单号 */
    @ExcelProperty(value = "商户订单号")
    private String orderNumber;

    /** 第三方订单号 */
    @ExcelProperty(value = "第三方订单号")
    private String thirdNumber;

    /** 订单状态 */
    @ExcelProperty(value = "订单状态")
    private String orderStatus;

    /** 订单总金额（单位：元） */
    @ExcelProperty(value = "订单总金额")
    private BigDecimal totalAmount;

    /** 实际支付金额（单位：元） */
    @ExcelProperty(value = "实际支付金额")
    private BigDecimal actualAmount;

    /** 订单内容 */
    @ExcelProperty(value = "订单内容")
    private String orderContent;

    /** 负载信息 */
    @ExcelProperty(value = "负载信息")
    private String orderMessage;

    /** 支付方式 */
    @ExcelProperty(value = "支付方式")
    private String payType;

    /** 支付时间 */
    @ExcelProperty(value = "支付时间")
    private Date payTime;

    /** 支付人 */
    @ExcelProperty(value = "支付人")
    private String payBy;

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
