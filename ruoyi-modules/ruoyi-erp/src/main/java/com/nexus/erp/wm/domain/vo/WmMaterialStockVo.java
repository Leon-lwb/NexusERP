package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMaterialStock;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存清单视图对象 wm_material_stock
 *
 * @author ruoyi
 * @date 2025-12-03
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialStock.class)
public class WmMaterialStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库存ID
     */
    @ExcelProperty(value = "库存ID")
    private Long stockId;

    /**
     * 物料类型ID
     */
    private Long categoryId;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品物料编码
     */
    @ExcelProperty(value = "产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @ExcelProperty(value = "产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /**
     * 单位名称
     */
    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    /**
     * 入库批次号
     */
    @ExcelProperty(value = "入库批次号")
    private String batchCode;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 库区编码
     */
    @ExcelProperty(value = "库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @ExcelProperty(value = "库区名称")
    private String areaName;

    /**
     * 库位编码
     */
    @ExcelProperty(value = "库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty(value = "库位名称")
    private String locationName;

    /**
     * 供应商编号
     */
    @ExcelProperty(value = "供应商编号")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 供应商简称
     */
    private String vendorNick;

    /**
     * 在库数量
     */
    @ExcelProperty(value = "在库数量")
    private BigDecimal stockQuantity;

    /**
     * 生产工单ID
     */
    private Long woId;

    /**
     * 生产工单编号
     */
    @ExcelProperty(value = "生产工单编号")
    private String woNo;

    /**
     * 生产日期
     */
    @ExcelProperty(value = "生产日期")
    private Date productionDate;

    /**
     * 入库时间
     */
    @ExcelProperty(value = "入库时间")
    private Date recptDate;

    /**
     * 库存有效期
     */
    @ExcelProperty(value = "库存有效期")
    private Date expireDate;

    /**
     * 冻结标志
     */
    private Long frozenFlag;
}
