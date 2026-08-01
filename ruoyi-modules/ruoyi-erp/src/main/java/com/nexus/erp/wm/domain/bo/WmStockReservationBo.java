package com.nexus.erp.wm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 库存预留业务对象（手动预留/释放请求参数）
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmStockReservationBo extends BaseEntity {

    /**
     * 物料ID
     */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /**
     * 仓库ID（可为空，表示不限仓库）
     */
    private Long warehouseId;

    /**
     * 预留/释放数量（仅预留时需要）
     */
    private BigDecimal reserveQty;

    /**
     * 来源单据类型（SALES_ORDER / WORK_ORDER 等）
     */
    @NotBlank(message = "来源单据类型不能为空")
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    @NotNull(message = "来源单据ID不能为空")
    private Long sourceDocId;

    /**
     * 来源单据行ID（可为空）
     */
    private Long sourceDocLineId;
}
