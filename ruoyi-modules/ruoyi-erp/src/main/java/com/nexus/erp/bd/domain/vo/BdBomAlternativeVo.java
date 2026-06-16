package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdBomAlternative;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BOM替代料视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdBomAlternative.class)
public class BdBomAlternativeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "替代料ID")
    private Long alternativeId;

    @ExcelProperty(value = "BOM明细ID")
    private Long detailId;

    @ExcelProperty(value = "BOM主表ID")
    private Long bomId;

    @ExcelProperty(value = "替代物料ID")
    private Long altMaterialId;

    @ExcelProperty(value = "替代物料编码")
    private String altMaterialCode;

    @ExcelProperty(value = "替代物料名称")
    private String altMaterialName;

    @ExcelProperty(value = "替代用量")
    private BigDecimal altQuantity;

    @ExcelProperty(value = "优先级")
    private Integer priority;

    @ExcelProperty(value = "替代类型")
    private String substitutionType;

    @ExcelProperty(value = "生效日期")
    private LocalDate effectiveDate;

    @ExcelProperty(value = "失效日期")
    private LocalDate expiryDate;

    @ExcelProperty(value = "状态")
    private Integer status;

    @ExcelProperty(value = "备注")
    private String remark;
}
