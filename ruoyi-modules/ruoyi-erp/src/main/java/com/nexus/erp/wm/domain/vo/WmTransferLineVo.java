package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存调拨单行视图对象 wm_transfer_line
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmTransferLine.class)
public class WmTransferLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行ID
     */
    @ExcelProperty(value = "行ID")
    private Long lineId;

    /**
     * 调拨单ID
     */
    private Long transferId;

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
     * 批次号
     */
    @ExcelProperty(value = "批次号")
    private String batchCode;

    /**
     * 来源库位
     */
    @ExcelProperty(value = "来源库位")
    private String fromLocationName;

    /**
     * 目标库位
     */
    @ExcelProperty(value = "目标库位")
    private String toLocationName;

    /**
     * 数量
     */
    @ExcelProperty(value = "数量")
    private BigDecimal quantity;

    /**
     * 备注
     */
    private String remark;
}
