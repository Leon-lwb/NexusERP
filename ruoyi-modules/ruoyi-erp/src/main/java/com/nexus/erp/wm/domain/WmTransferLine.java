package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 库存调拨行对象 wm_transfer_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_transfer_line")
public class WmTransferLine extends TenantEntity {

    /**
     * 行ID
     */
    @TableId(value = "line_id")
    private Long lineId;

    /**
     * 调拨单ID
     */
    private Long transferId;

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
     * 物料规格
     */
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 基本单位名称
     */
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 来源库位ID
     */
    private Long fromLocationId;

    /**
     * 来源库位编码
     */
    private String fromLocationCode;

    /**
     * 来源库位名称
     */
    private String fromLocationName;

    /**
     * 目标库位ID
     */
    private Long toLocationId;

    /**
     * 目标库位编码
     */
    private String toLocationCode;

    /**
     * 目标库位名称
     */
    private String toLocationName;

    /**
     * 调拨数量
     */
    private BigDecimal quantity;

    /**
     * 备注
     */
    private String remark;
}
