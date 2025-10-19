package com.nexus.core.bd.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "BOM明细VO")
public class BomDetailVO {
    
    @ApiModelProperty(value = "明细ID")
    private Long detailId;
    
    @ApiModelProperty(value = "BOM主表ID")
    private Long bomId;
    
    @ApiModelProperty(value = "父项物料ID")
    private Long parentMaterialId;
    
    @ApiModelProperty(value = "父项物料编码")
    private String parentMaterialCode;
    
    @ApiModelProperty(value = "父项物料名称")
    private String parentMaterialName;
    
    @ApiModelProperty(value = "子项物料ID")
    private Long childMaterialId;
    
    @ApiModelProperty(value = "子项物料编码")
    private String childMaterialCode;
    
    @ApiModelProperty(value = "子项物料名称")
    private String childMaterialName;
    
    @ApiModelProperty(value = "子项物料规格")
    private String childMaterialSpec;
    
    @ApiModelProperty(value = "基本单位ID")
    private Long baseUomId;
    
    @ApiModelProperty(value = "基本单位名称")
    private String baseUomName;
    
    @ApiModelProperty(value = "单位父项用量")
    private BigDecimal quantityPerParent;
    
    @ApiModelProperty(value = "损耗率(%)")
    private BigDecimal lossRate;
    
    @ApiModelProperty(value = "含损耗总用量")
    private BigDecimal totalQuantity;
    
    @ApiModelProperty(value = "工序步骤")
    private Integer operationStep;
    
    @ApiModelProperty(value = "工作中心ID")
    private Long workstationId;
    
    @ApiModelProperty(value = "工作中心名称")
    private String workstationName;
    
    @ApiModelProperty(value = "提前期(小时)")
    private Integer leadTime;
    
    @ApiModelProperty(value = "生效日期")
    private LocalDate effectiveDate;
    
    @ApiModelProperty(value = "失效日期")
    private LocalDate expiryDate;
    
    @ApiModelProperty(value = "显示排序")
    private Integer sortOrder;
    
    @ApiModelProperty(value = "状态 0:停用 1:启用")
    private Integer status;
    
    @ApiModelProperty(value = "状态描述")
    private String statusDesc;
    
    @ApiModelProperty(value = "备注")
    private String remark;
    
    @ApiModelProperty(value = "创建人")
    private String createBy;
    
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;
    
    @ApiModelProperty(value = "更新人")
    private String updateBy;
    
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;
}
