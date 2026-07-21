package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmProductionRecpt;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产入库单视图对象 wm_production_recpt
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmProductionRecpt.class)
public class WmProductionRecptVo implements Serializable {

    /**
     * 生产入库单ID
     */
    @ExcelProperty("生产入库ID")
    private Long recptId;

    /**
     * 入库单号
     */
    @ExcelProperty("入库单号")
    private String recptCode;

    /**
     * 工单ID
     */
    @ExcelProperty("工单ID")
    private Long woId;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 物料ID
     */
    @ExcelProperty("物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 规格
     */
    @ExcelProperty("规格")
    private String materialSpec;

    /**
     * 入库数量
     */
    @ExcelProperty("入库数量")
    private BigDecimal quantity;

    /**
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 库位ID
     */
    @ExcelProperty("库位ID")
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty("库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty("库位名称")
    private String locationName;

    /**
     * 入库日期
     */
    @ExcelProperty("入库日期")
    private Date recptDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;
}
