package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmWarehouse;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仓库设置视图对象 wm_warehouse
 *
 * @author ruoyi
 * @date 2025-09-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmWarehouse.class)
public class WmWarehouseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 位置
     */
    @ExcelProperty(value = "位置")
    private String location;

    /**
     * 面积
     */
    @ExcelProperty(value = "面积")
    private BigDecimal area;

    /**
     * 负责人
     */
    @ExcelProperty(value = "负责人")
    private String manager;

    /**
     * 仓库状态
     */
    @ExcelProperty(value = "仓库状态")
    private Long status;
}
