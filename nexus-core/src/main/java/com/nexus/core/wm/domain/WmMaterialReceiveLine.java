package com.nexus.core.wm.domain;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 物料入库单行对象 wm_material_receive_line
 *
 * @author nexus
 * @date 2025-12-08
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "wm_material_receive_line", excludeProperty = {"params", "searchValue"})
public class WmMaterialReceiveLine extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 物料入库单行ID
     */
    @TableId(value = "line_id", type = IdType.AUTO)
    private Long lineId;

    /**
     * 物料入库单ID
     */
    @Excel(name = "物料入库单ID")
    private Long receiveId;

    /**
     * 来源单据行ID
     */
    @Excel(name = "来源单据行ID")
    private Long sourceDocLineId;

    /**
     * 产品物料ID
     */
    @Excel(name = "产品物料ID")
    private Long materialId;

    /**
     * 产品物料编码
     */
    @Excel(name = "产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @Excel(name = "产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @Excel(name = "规格型号")
    private String materialSpec;

    /**
     * 单位
     */
    @Excel(name = "单位")
    private String baseUomId;

    /**
     * 单位名称
     */
    @Excel(name = "单位名称")
    private String baseUomName;

    /**
     * 入库数量
     */
    @Excel(name = "入库数量")
    private BigDecimal quantityReceived;

    /**
     * 批次ID
     */
    @Excel(name = "批次ID")
    private Long batchId;

    /**
     * 批次号
     */
    @Excel(name = "批次号")
    private String batchCode;

    /**
     * 仓库ID
     */
    @Excel(name = "仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @Excel(name = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @Excel(name = "仓库名称")
    private String warehouseName;

    /**
     * 库区ID
     */
    @Excel(name = "库区ID")
    private Long areaId;

    /**
     * 库区编码
     */
    @Excel(name = "库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @Excel(name = "库区名称")
    private String areaName;

    /**
     * 库位ID
     */
    @Excel(name = "库位ID")
    private Long locationId;

    /**
     * 库位编码
     */
    @Excel(name = "库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @Excel(name = "库位名称")
    private String locationName;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;
    @TableField(exist = false)
    private List<WmMaterialReceiveDetail> wmMaterialReceiveLineDetailList;
}