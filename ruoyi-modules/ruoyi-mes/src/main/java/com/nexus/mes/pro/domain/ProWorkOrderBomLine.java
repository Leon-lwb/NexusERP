package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order_bom_line")
public class ProWorkOrderBomLine extends TenantEntity {
    @TableId(value = "wo_bom_line_id")
    private Long woBomLineId;
    private Long mesWoId;
    private Long mbomLineId;
    private Long bomId;
    private Long bomDetailId;
    private Long childMaterialId;
    private String childMaterialCode;
    private String childMaterialName;
    private BigDecimal quantityPerParent;
    private BigDecimal lossRate;
    private BigDecimal totalQuantity;
    private Long routingStepId;
    private Integer sequenceNum;
    private Long processId;
    private String processCode;
    private String processName;
    private Long workstationId;
    private String workstationCode;
    private String workstationName;
    private String operationContent;
    private Integer backflushFlag;
    private Integer sortOrder;
    private String remark;
    @TableLogic
    private String delFlag;
}
