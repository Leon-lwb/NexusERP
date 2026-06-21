package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.pro.domain.MesMbomLine;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesMbomLine.class)
public class MesMbomLineVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "明细ID")
    private Long mbomLineId;

    private Long mbomId;
    private Long bomDetailId;

    @ExcelProperty(value = "子项物料ID")
    private Long childMaterialId;

    @ExcelProperty(value = "子项物料编码")
    private String childMaterialCode;

    @ExcelProperty(value = "子项物料名称")
    private String childMaterialName;

    @ExcelProperty(value = "规格型号")
    private String childMaterialSpec;

    @ExcelProperty(value = "单位父项用量")
    private BigDecimal quantityPerParent;

    @ExcelProperty(value = "损耗率(%)")
    private BigDecimal lossRate;

    /** 工序步骤ID */
    private Long routingStepId;

    /** 工序步骤序号(富集) */
    @ExcelProperty(value = "工序步骤序号")
    private Integer routingStepSequence;

    /** 工序ID */
    private Long processId;

    @ExcelProperty(value = "工序编码")
    private String processCode;

    @ExcelProperty(value = "工序名称")
    private String processName;

    /** 工位ID */
    private Long workstationId;

    @ExcelProperty(value = "工位编码")
    private String workstationCode;

    @ExcelProperty(value = "工位名称")
    private String workstationName;

    @ExcelProperty(value = "作业内容")
    private String operationContent;

    @ExcelProperty(value = "是否倒冲")
    private Integer backflushFlag;

    @ExcelProperty(value = "排序")
    private Integer sortOrder;

    private String remark;
}
