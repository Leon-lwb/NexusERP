package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
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
     * 已下架数量(两段式移库, 整行下架时=调拨数量)
     */
    private BigDecimal takeoffQty;

    /**
     * 备注
     */
    private String remark;

    /** 来源仓库（非表字段，执行时从调拨单头填充，用于跨仓调拨正确扣减） */
    @TableField(exist = false)
    private Long fromWarehouseId;
    /** 来源仓库编码（非表字段） */
    @TableField(exist = false)
    private String fromWarehouseCode;
    /** 来源仓库名称（非表字段） */
    @TableField(exist = false)
    private String fromWarehouseName;
    /** 目标仓库（非表字段，执行时从调拨单头填充，目标库存维度取目标仓库） */
    @TableField(exist = false)
    private Long toWarehouseId;
    /** 目标仓库编码（非表字段） */
    @TableField(exist = false)
    private String toWarehouseCode;
    /** 目标仓库名称（非表字段） */
    @TableField(exist = false)
    private String toWarehouseName;
    /** 目标库区ID（非表字段） */
    @TableField(exist = false)
    private Long toAreaId;
    /** 目标库区编码（非表字段） */
    @TableField(exist = false)
    private String toAreaCode;
    /** 目标库区名称（非表字段） */
    @TableField(exist = false)
    private String toAreaName;
}
