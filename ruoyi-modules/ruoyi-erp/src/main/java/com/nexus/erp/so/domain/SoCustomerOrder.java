package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户订单对象 so_customer_order
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_customer_order")
public class SoCustomerOrder extends TenantEntity {

    /** 客户订单ID */
    @TableId(value = "co_id")
    private Long coId;

    /** 客户订单号 */
    private String coNo;
    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 联系人名称 */
    private String contactName;
    /** 联系人电话 */
    private String contactPhone;
    /** 下单日期 */
    private Date orderDate;
    /** 要求交货日期 */
    private Date requestedDate;
    /** 币种 */
    private String currency;
    /** 订单总金额 */
    private BigDecimal totalAmount;
    /** 订单状态 */
    private String status;
    /** 优先级 */
    private Long priority;
    /** 客户采购订单号 */
    private String customerPoNo;
    /** 订单来源渠道 */
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
