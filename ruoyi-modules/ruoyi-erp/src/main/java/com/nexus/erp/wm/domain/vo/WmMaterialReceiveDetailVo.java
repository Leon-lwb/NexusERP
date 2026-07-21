package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料入库单明细视图对象 wm_material_receive_detail
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialReceiveDetail.class)
public class WmMaterialReceiveDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    private Long detailId;

    /**
     * 入库单行ID
     */
    private Long lineId;

    /**
     * 入库单ID
     */
    private Long receiveId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 单位
     */
    @ExcelProperty(value = "单位")
    private String baseUomName;

    /**
     * 入库数量
     */
    @ExcelProperty(value = "入库数量")
    private BigDecimal quantity;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次号
     */
    @ExcelProperty(value = "批次号")
    private String batchCode;

    /**
     * 序列号ID
     */
    private Long snId;

    /**
     * 序列号编码
     */
    @ExcelProperty(value = "序列号")
    private String snCode;

    /**
     * 仓库ID
     */
    private Long warehouseId;

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
     * 库区ID
     */
    private Long areaId;

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
     * 库位ID
     */
    private Long locationId;

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
     * 备注
     */
    private String remark;
}
