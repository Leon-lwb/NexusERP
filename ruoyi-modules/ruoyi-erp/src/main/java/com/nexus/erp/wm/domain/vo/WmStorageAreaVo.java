package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmStorageArea;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库区视图对象 wm_storage_area
 *
 * @author nexus
 * @date 2025-12-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmStorageArea.class)
public class WmStorageAreaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 仓库ID
     */
    @ExcelProperty(value = "仓库ID")
    private Long warehouseId;

    /**
     * 面积
     */
    @ExcelProperty(value = "面积")
    private BigDecimal area;

    /**
     * 是否开启库位管理
     */
    @ExcelProperty(value = "是否开启库位管理")
    private Long locationFlag;

    /**
     * 是否启用
     */
    @ExcelProperty(value = "是否启用")
    private Long enableFlag;

    /**
     * 是否冻结
     */
    @ExcelProperty(value = "是否冻结")
    private Long frozenFlag;

    /**
     * 布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货
     */
    @ExcelProperty(value = "布局类型")
    private String layoutType;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;
}
