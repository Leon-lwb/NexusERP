package com.nexus.mes.ncr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.ncr.domain.NcrDefect;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES NCR缺陷明细视图对象 ncr_defect
 * <p>属于 MES NCR 不合格品管理模块，作为 NCR 缺陷明细的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = NcrDefect.class)
public class NcrDefectVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 缺陷ID */
    @ExcelProperty("缺陷ID")
    private Long defectId;

    /** NCR单ID */
    private Long ncrId;

    /** 缺陷代码ID */
    private Long defectCodeId;

    /** 缺陷编码 */
    @ExcelProperty("缺陷编码")
    private String defectCode;

    /** 缺陷名称 */
    @ExcelProperty("缺陷名称")
    private String defectName;

    /** 缺陷数量 */
    @ExcelProperty("缺陷数量")
    private BigDecimal defectQty;

    /** 缺陷原因 */
    @ExcelProperty("缺陷原因")
    private String defectCause;

    /** 根本原因描述 */
    @ExcelProperty("根本原因")
    private String rootCauseDesc;
}