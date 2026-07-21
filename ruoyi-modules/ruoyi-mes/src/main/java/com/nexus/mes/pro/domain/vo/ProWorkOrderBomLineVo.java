package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES工单BOM快照明细视图对象
 * <p>属于 MES 生产管理模块，作为 工单 的查询/导出返回结构。</p>
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrderBomLine.class)
public class ProWorkOrderBomLineVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "快照明细ID")
    private Long woBomLineId;

    /** MES工单ID */
    private Long mesWoId;
    /** BOMID */
    private Long bomId;
    /** BOMDetailId */
    private Long bomDetailId;
    /** MBOMLineId */
    private Long mbomLineId;

    @ExcelProperty(value = "子项物料ID")
    private Long childMaterialId;

    @ExcelProperty(value = "子项物料编码")
    private String childMaterialCode;

    @ExcelProperty(value = "子项物料名称")
    private String childMaterialName;

    @ExcelProperty(value = "单位父项用量")
    private BigDecimal quantityPerParent;

    @ExcelProperty(value = "损耗率(%)")
    private BigDecimal lossRate;

    @ExcelProperty(value = "含损耗总用量")
    private BigDecimal totalQuantity;

    @ExcelProperty(value = "工序步骤序号")
    private Integer sequenceNum;

    @ExcelProperty(value = "工序编码")
    private String processCode;

    @ExcelProperty(value = "工序名称")
    private String processName;

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

    /** remark */
    private String remark;
}
