package org.dromara.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_vendor_evaluation")
public class PuVendorEvaluation extends TenantEntity {

    @TableId(value = "evaluation_id")
    private Long evaluationId;

    private Long vendorId;

    private String vendorCode;

    private String vendorName;

    private String evalPeriod;

    private BigDecimal deliveryRate;

    private BigDecimal qualityRate;

    private BigDecimal priceScore;

    private BigDecimal totalScore;

    private String evalLevel;

    private Integer totalOrders;

    private Integer onTimeOrders;

    private BigDecimal totalReceivedQty;

    private BigDecimal qualifiedQty;

    private BigDecimal returnQty;

    private String remark;

    private String attr1;

    private String attr2;

    private Long attr3;

    private Long attr4;

    @TableLogic
    private String delFlag;
}
