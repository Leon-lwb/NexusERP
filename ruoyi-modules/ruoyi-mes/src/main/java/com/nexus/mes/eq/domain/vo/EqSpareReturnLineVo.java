package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.mes.eq.domain.EqSpareReturnLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES备件退库单明细视图对象 eq_spare_return_line（MES-EQ-04 收口）
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqSpareReturnLine.class)
public class EqSpareReturnLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细行ID */
    private Long lineId;

    /** 退库单ID */
    private Long returnId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @ExcelProperty("备件编码")
    private String spareCode;

    /** 备件名称 */
    @ExcelProperty("备件名称")
    private String spareName;

    /** 退库数量 */
    @ExcelProperty("退库数量")
    private BigDecimal returnQty;

    /** 备件参考单价快照 */
    @ExcelProperty("参考单价")
    private BigDecimal unitCost;

    /** 备注 */
    private String remark;
}
