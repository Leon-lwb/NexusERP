package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmArrivalNoticeLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 到货通知单行视图对象 wm_arrival_notice_line
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmArrivalNoticeLine.class)
public class WmArrivalNoticeLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long lineId;
    private Long noticeId;
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

    @ExcelProperty(value = "到货数量")
    private BigDecimal quantityArrival;

    @ExcelProperty(value = "合格数量")
    private BigDecimal quantityQuanlified;

    @ExcelProperty(value = "是否来料检验")
    private String iqcCheck;

    private Long iqcId;

    @ExcelProperty(value = "来料检验单编号")
    private String iqcCode;

    private String remark;
}
