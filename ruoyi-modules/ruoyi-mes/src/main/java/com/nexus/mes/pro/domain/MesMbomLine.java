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
@TableName("mes_mbom_line")
public class MesMbomLine extends TenantEntity {
    @TableId(value = "mbom_line_id")
    private Long mbomLineId;
    private Long mbomId;
    private Long bomId;
    private Long bomDetailId;
    private Long childMaterialId;
    private BigDecimal quantityPerParent;
    private BigDecimal lossRate;
    private Long routingStepId;
    private Long processId;
    private Long workstationId;
    private String operationContent;
    private Integer backflushFlag;
    private Integer sortOrder;
    private String remark;
    @TableLogic
    private String delFlag;
}
