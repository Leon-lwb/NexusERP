package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 到货通知单行视图对象 wm_arrival_notice_line
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmArrivalNoticeLine.class)
public class WmArrivalNoticeLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行ID
     */
    private Long lineId;

    /**
     * 到货通知ID
     */
    private Long noticeId;

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
     * 到货数量
     */
    @ExcelProperty(value = "到货数量")
    private BigDecimal quantityArrival;

    /**
     * 合格数量
     */
    @ExcelProperty(value = "合格数量")
    private BigDecimal quantityQuanlified;

    /**
     * 是否来料检验
     */
    @ExcelProperty(value = "是否来料检验")
    private String iqcCheck;

    /**
     * 来料检验单ID
     */
    private Long iqcId;

    /**
     * 来料检验单编号
     */
    @ExcelProperty(value = "来料检验单编号")
    private String iqcCode;

    /**
     * 备注
     */
    private String remark;
}
