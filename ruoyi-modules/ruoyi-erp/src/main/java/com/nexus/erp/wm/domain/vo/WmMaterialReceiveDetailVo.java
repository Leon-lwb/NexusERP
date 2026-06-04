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

    private Long detailId;
    private Long lineId;
    private Long receiveId;
    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    private String baseUomId;

    @ExcelProperty(value = "单位")
    private String baseUomName;

    @ExcelProperty(value = "入库数量")
    private BigDecimal quantity;

    private Long batchId;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    /** 序列号ID */
    private Long snId;

    /** 序列号编码 */
    @ExcelProperty(value = "序列号")
    private String snCode;

    private Long warehouseId;

    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    private Long areaId;

    @ExcelProperty(value = "库区编码")
    private String areaCode;

    @ExcelProperty(value = "库区名称")
    private String areaName;

    private Long locationId;

    @ExcelProperty(value = "库位编码")
    private String locationCode;

    @ExcelProperty(value = "库位名称")
    private String locationName;

    private String remark;
}
