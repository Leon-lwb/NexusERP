package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMaterialStockTx;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存事务视图对象 wm_material_stock_tx
 *
 * @author nexus
 * @date 2025-12-12
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialStockTx.class)
public class WmMaterialStockTxVo implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * 事务ID
     */
    @ExcelProperty(value = "事务ID")
    private Long txId;

    /**
     * 事务类型
     */
    @ExcelProperty(value = "事务类型")
    private String txType;

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
     * 批次号
     */
    @ExcelProperty(value = "批次号")
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
     * 容器编号
     */
    @ExcelProperty(value = "容器编号")
    private String packageCode;

    /**
     * 单据类型
     */
    @ExcelProperty(value = "单据类型")
    private String sourceDocType;

    /**
     * 单据编号
     */
    @ExcelProperty(value = "单据编号")
    private String sourceDocCode;

    /**
     * 库存方向
     */
    @ExcelProperty(value = "库存方向")
    private Integer txFlag;

    /**
     * 事务数量
     */
    @ExcelProperty(value = "事务数量")
    private BigDecimal txQuantity;

    /**
     * 变动前数量
     */
    @ExcelProperty(value = "变动前数量")
    private BigDecimal beforeQuantity;

    /**
     * 变动后数量
     */
    @ExcelProperty(value = "变动后数量")
    private BigDecimal afterQuantity;

    /**
     * 事务日期
     */
    @ExcelProperty(value = "事务日期")
    private Date txDate;

    /**
     * ERP账期
     */
    @ExcelProperty(value = "ERP账期")
    private Date erpDate;

    /**
     * 接收日期
     */
    @ExcelProperty(value = "接收日期")
    private Date recptDate;

    private Long trackingVersion;
    private Long postingGroupId;
}
