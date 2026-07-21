package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 物料入库单行视图对象 wm_material_receive_line
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialReceiveLine.class)
public class WmMaterialReceiveLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行ID
     */
    private Long lineId;

    /**
     * 入库单ID
     */
    private Long receiveId;

    /**
     * 来源单据行ID
     */
    private Long sourceDocLineId;

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
    private BigDecimal quantityReceived;

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

    /**
     * 入库行明细列表
     */
    private List<WmMaterialReceiveDetailVo> wmMaterialReceiveLineDetailList;
}
