package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoPriceListLine;

import java.math.BigDecimal;

/**
 * 销售价格表行业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoPriceListLine.class, reverseConvertGenerate = false)
public class SoPriceListLineBo extends BaseEntity {

    /** 行ID */
    private Long lineId;

    /** 价格表ID */
    private Long priceListId;
    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;
    /** 单价 */
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;
    /** 最小数量 */
    private BigDecimal minQty;
    /** 最大数量 */
    private BigDecimal maxQty;
    /** 折扣率 */
    private BigDecimal discountRate;
}
