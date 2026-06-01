package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;
import com.nexus.erp.so.domain.vo.SoPriceListLineVo;

import java.math.BigDecimal;

/**
 * 销售价格表行对象 so_price_list_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_price_list_line")
@AutoMapper(target = SoPriceListLineVo.class)
public class SoPriceListLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id", type = IdType.ASSIGN_ID)
    private Long lineId;

    /** 价格表ID */
    private Long priceListId;
    /** 物料ID */
    private Long materialId;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 最小数量 */
    private BigDecimal minQty;
    /** 最大数量 */
    private BigDecimal maxQty;
    /** 折扣率 */
    private BigDecimal discountRate;
}
