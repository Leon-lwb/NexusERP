package org.dromara.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.bd.domain.BdBomDetail;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdBomDetail.class)
public class BdBomDetailVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("明细ID")
    private Long detailId;

    @ExcelProperty("BOM主表ID")
    private Long bomId;

    @ExcelProperty("父项物料ID")
    private Long parentMaterialId;

    @ExcelProperty("父项物料编码")
    private String parentMaterialCode;

    @ExcelProperty("父项物料名称")
    private String parentMaterialName;

    @ExcelProperty("子项物料ID")
    private Long childMaterialId;

    @ExcelProperty("子项物料编码")
    private String childMaterialCode;

    @ExcelProperty("子项物料名称")
    private String childMaterialName;

    @ExcelProperty("子项物料规格")
    private String childMaterialSpec;

    @ExcelProperty("基本单位ID")
    private Long baseUomId;

    @ExcelProperty("基本单位名称")
    private String baseUomName;

    @ExcelProperty("单位父项用量")
    private BigDecimal quantityPerParent;

    @ExcelProperty("损耗率(%)")
    private BigDecimal lossRate;

    @ExcelProperty("含损耗总用量")
    private BigDecimal totalQuantity;

    @ExcelProperty("工序步骤")
    private Integer operationStep;

    @ExcelProperty("工作中心ID")
    private Long workstationId;

    @ExcelProperty("工作中心名称")
    private String workstationName;

    @ExcelProperty("提前期(小时)")
    private Integer leadTime;

    @ExcelProperty("生效日期")
    private LocalDate effectiveDate;

    @ExcelProperty("失效日期")
    private LocalDate expiryDate;

    @ExcelProperty("显示排序")
    private Integer sortOrder;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("状态描述")
    private String statusDesc;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("创建人")
    private Long createBy;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @ExcelProperty("更新人")
    private Long updateBy;

    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;
}
