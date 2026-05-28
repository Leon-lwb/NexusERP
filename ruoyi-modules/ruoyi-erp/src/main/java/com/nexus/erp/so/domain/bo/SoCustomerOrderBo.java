package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoCustomerOrder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户订单业务对象 so_customer_order
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoCustomerOrder.class, reverseConvertGenerate = false)
public class SoCustomerOrderBo extends BaseEntity {

    private Long coId;

    @NotBlank(message = "客户订单号不能为空")
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

    private Long[] ids;
}
