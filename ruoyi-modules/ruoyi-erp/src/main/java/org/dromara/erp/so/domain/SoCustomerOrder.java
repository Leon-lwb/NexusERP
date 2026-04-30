package org.dromara.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户订单对象 so_customer_order
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_customer_order")
public class SoCustomerOrder extends TenantEntity {

    @TableId(value = "co_id")
    private Long coId;

    private String coNo;
    private Long clientId;
    private String clientName;
    private String contactName;
    private String contactPhone;
    private Date orderDate;
    private Date requestedDate;
    private String currency;
    private BigDecimal totalAmount;
    private String status;
    private Long priority;
    private String customerPoNo;
    private String sourceChannel;
    private String clientCode;
    private String attr2;
    private Long salesRepId;
    private String salesRepName;
    private String remark;

    @TableLogic
    private String delFlag;
}
