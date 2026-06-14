package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 价格对比视图对象
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
public class PriceComparisonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    private String vendorName;

    /**
     * 单价
     */
    private BigDecimal price;

    /**
     * 币种
     */
    private String currency;

    /**
     * 供货周期(天)
     */
    private Integer leadTime;

    /**
     * 最小订货量
     */
    private BigDecimal minOrderQty;

    /**
     * 是否默认供应商
     */
    private Long isDefault;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 生效日期
     */
    private Date effectiveDate;

    /**
     * 失效日期
     */
    private Date expiryDate;

    /**
     * 状态
     */
    private Long status;
}
