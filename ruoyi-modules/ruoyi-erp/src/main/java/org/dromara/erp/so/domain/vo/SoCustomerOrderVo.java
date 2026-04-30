package org.dromara.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.so.domain.SoCustomerOrder;

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

    private Long coId;

    @ExcelProperty(value = "客户订单号")
    private String coNo;

    private Long clientId;

    @ExcelProperty(value = "客户名称")
    private String clientName;

    private String contactName;
    private String contactPhone;

    @ExcelProperty(value = "客户下单日期")
    private Date orderDate;

    private Date requestedDate;
    private String currency;

    @ExcelProperty(value = "订单总金额")
    private BigDecimal totalAmount;

    @ExcelProperty(value = "订单状态")
    private String status;

    @ExcelProperty(value = "订单优先级")
    private Long priority;

    @ExcelProperty(value = "客户采购订单号")
    private String customerPoNo;

    @ExcelProperty(value = "订单来源")
    private String sourceChannel;

    private String clientCode;
    private String attr2;
    private Long salesRepId;
    private String salesRepName;
    private String remark;
}
