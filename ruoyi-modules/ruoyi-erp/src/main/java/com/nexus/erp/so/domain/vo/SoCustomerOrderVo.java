package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoCustomerOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户订单视图对象 so_customer_order
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoCustomerOrder.class)
public class SoCustomerOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户订单ID */
    private Long coId;

    /** 客户订单号 */
    @ExcelProperty(value = "客户订单号")
    private String coNo;

    /** 客户ID */
    private Long clientId;

    /** 客户名称 */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /** 联系人名称 */
    private String contactName;
    /** 联系人电话 */
    private String contactPhone;

    /** 客户下单日期 */
    @ExcelProperty(value = "客户下单日期")
    private Date orderDate;

    /** 要求交货日期 */
    private Date requestedDate;
    /** 币种 */
    private String currency;

    /** 订单总金额 */
    @ExcelProperty(value = "订单总金额")
    private BigDecimal totalAmount;

    /** 订单状态 */
    @ExcelProperty(value = "订单状态")
    private String status;

    /** 订单优先级 */
    @ExcelProperty(value = "订单优先级")
    private Long priority;

    /** 客户采购订单号 */
    @ExcelProperty(value = "客户采购订单号")
    private String customerPoNo;

    /** 订单来源 */
    @ExcelProperty(value = "订单来源")
    private String sourceChannel;

    /** 客户编码 */
    private String clientCode;
    /** 扩展字段2 */
    private String attr2;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 备注 */
    private String remark;
}
