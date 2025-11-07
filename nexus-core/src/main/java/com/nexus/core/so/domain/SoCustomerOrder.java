package com.nexus.core.so.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 客户订单对象 so_customer_order
 *
 * @author nexus
 * @date 2025-10-21
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.so.mapper.SoCustomerOrderMapper.SoCustomerOrderResult")
public class SoCustomerOrder extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 客户订单ID */
    @TableId(value = "co_id",type = IdType.AUTO)
    private Long coId;
    /** 客户订单号 */
    @Excel(name = "客户订单号")
    private String coNo;
    /** 客户ID */
    @Excel(name = "客户ID")
    private Long clientId;
    
    /** 客户名称 */
    @Excel(name = "客户名称")
    private String clientName;
    
    /** 联系人 */
    private String contactName;
    /** 联系人电话 */
    private String contactPhone;
    /** 客户下单日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "客户下单日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date orderDate;
    /** 客户要求交货日 */
    private Date requestedDate;
    /** 货币 */
    private String currency;
    /** 订单总金额（未税） */
    @Excel(name = "订单总金额", readConverterExp = "未=税")
    private BigDecimal totalAmount;
    /** 订单状态 */
    @Excel(name = "订单状态")
    private String status;
    /** 订单优先级 */
    @Excel(name = "订单优先级")
    private Long priority;
    /** 客户采购订单号 */
    @Excel(name = "客户采购订单号")
    private String customerPoNo;
    /** 订单来源 */
    @Excel(name = "订单来源")
    private String sourceChannel;
    /** 预留字段1 */
    private String clientCode;
    /** 预留字段2 */
    private String attr2;
    /** 销售代表id */
    private Long salesRepId;
    /** 销售代表 */
    private String salesRepName;
}