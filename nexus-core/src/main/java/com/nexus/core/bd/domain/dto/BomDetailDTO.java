package com.nexus.core.bd.domain.dto;

import com.nexus.common.core.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "BOM明细DTO")
public class BomDetailDTO extends BaseEntity {
    
    @ApiModelProperty(value = "明细ID，新增时为空，修改时必填")
    private Long detailId;
    
    @ApiModelProperty(value = "BOM主表ID", required = true)
    @NotNull(message = "BOM主表ID不能为空")
    private Long bomId;
    
    @ApiModelProperty(value = "子项物料ID", required = true)
    @NotNull(message = "子项物料ID不能为空")
    private Long childMaterialId;
    
    @ApiModelProperty(value = "子项物料编码")
    private String childMaterialCode;
    
    @ApiModelProperty(value = "子项物料名称")
    private String childMaterialName;
    
    @ApiModelProperty(value = "单位父项用量", required = true)
    @NotNull(message = "单位父项用量不能为空")
    @DecimalMin(value = "0.000001", message = "用量必须大于0")
    @Digits(integer = 12, fraction = 6, message = "用量格式不正确")
    private BigDecimal quantityPerParent;
    
    @ApiModelProperty(value = "损耗率(%)")
    @DecimalMin(value = "0", message = "损耗率不能小于0")
    @DecimalMax(value = "100", message = "损耗率不能大于100")
    private BigDecimal lossRate = BigDecimal.ZERO;
    
    @ApiModelProperty(value = "工序步骤")
    @NotNull(message = "工序步骤不能为空")
    private Integer operationStep;
    
    @ApiModelProperty(value = "工作中心ID")
    private Long workstationId;
    
    @ApiModelProperty(value = "工作中心名称")
    private String workstationName;
    
    @ApiModelProperty(value = "提前期(小时)")
    private Integer leadTime = 0;
    
    @ApiModelProperty(value = "生效日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;
    
    @ApiModelProperty(value = "失效日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;
    
    @ApiModelProperty(value = "显示排序")
    private Integer sortOrder = 0;
    
    @ApiModelProperty(value = "状态 0:停用 1:启用")
    private Integer status = 1;
    
    @ApiModelProperty(value = "备注")
    private String remark;
    
    @ApiModelProperty(value = "预留字段1")
    private String attr1;
    
    @ApiModelProperty(value = "预留字段2")
    private String attr2;
    
    @ApiModelProperty(value = "预留字段3")
    private Integer attr3;
    
    @ApiModelProperty(value = "预留字段4")
    private Integer attr4;
}