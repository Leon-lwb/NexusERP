package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqRepairSpare;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES维修领用备件明细视图对象 eq_repair_spare
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqRepairSpare.class)
public class EqRepairSpareVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 维修单ID */
    private Long repairId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @ExcelProperty("备件编码")
    private String spareCode;

    /** 备件名称 */
    @ExcelProperty("备件名称")
    private String spareName;

    /** 规格 */
    @ExcelProperty("规格")
    private String spec;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 领用数量 */
    @ExcelProperty("领用数量")
    private BigDecimal quantity;

    /** 备注 */
    private String remark;
}
