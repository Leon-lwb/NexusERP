package com.nexus.core.wm.domain;

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
 * 库存清单对象 wm_material_stock
 *
 * @author nexus
 * @date 2025-12-03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmMaterialStockMapper.WmMaterialStockResult")
public class WmMaterialStock extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 库存ID */
    @TableId(value = "stock_id",type = IdType.AUTO)
    private Long stockId;
    /** 物料类型ID */
    @Excel(name = "物料类型ID")
    private Long categoryId;
    /** 产品物料ID */
    @Excel(name = "产品物料ID")
    private Long materialId;
    /** 产品物料编码 */
    @Excel(name = "产品物料编码")
    private String materialCode;
    /** 产品物料名称 */
    @Excel(name = "产品物料名称")
    private String materialName;
    /** 规格型号 */
    @Excel(name = "规格型号")
    private String materialSpec;
    /** 单位id */
    private String baseUomId;
    /** 单位名称 */
    @Excel(name = "单位名称")
    private String baseUomName;
    /** 批次ID */
    private Long batchId;
    /** 入库批次号 */
    @Excel(name = "入库批次号")
    private String batchCode;
    /** 仓库ID */
    private Long warehouseId;
    /** 仓库编码 */
    @Excel(name = "仓库编码")
    private String warehouseCode;
    /** 仓库名称 */
    @Excel(name = "仓库名称")
    private String warehouseName;
    /** 库区ID */
    private Long areaId;
    /** 库区编码 */
    @Excel(name = "库区编码")
    private String areaCode;
    /** 库区名称 */
    @Excel(name = "库区名称")
    private String areaName;
    /** 库位ID */
    private Long locationId;
    /** 库位编码 */
    @Excel(name = "库位编码")
    private String locationCode;
    /** 库位名称 */
    @Excel(name = "库位名称")
    private String locationName;
    /** 供应商ID */
    private Long vendorId;
    /** 供应商编号 */
    @Excel(name = "供应商编号")
    private String vendorCode;
    /** 供应商名称 */
    @Excel(name = "供应商名称")
    private String vendorName;
    /** 供应商简称 */
    @Excel(name = "供应商简称")
    private String vendorNick;
    /** 在库数量 */
    @Excel(name = "在库数量")
    private BigDecimal stockQuantity;
    /** 生产工单ID */
    @Excel(name = "生产工单ID")
    private Long woId;
    /** 生产工单编号 */
    @Excel(name = "生产工单编号")
    private String woNo;
    /** 生产日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "生产日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date productionDate;
    /** 入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "入库时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date recptDate;
    /** 库存有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "库存有效期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date expireDate;
    /** 冻结标志 */
    @Excel(name = "冻结标志")
    private Long frozenFlag;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}