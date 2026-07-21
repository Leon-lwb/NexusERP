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
    /** MBOMID */
    private Long mbomId;
    /** BOMID */
    private Long bomId;
    /** BOMDetailId */
    private Long bomDetailId;
    /** 子级MaterialId */
    private Long childMaterialId;
    /** quantityPerParent */
    private BigDecimal quantityPerParent;
    /** 比率 */
    private BigDecimal lossRate;
    /** 工艺路线StepId */
    private Long routingStepId;
    /** 工序ID */
    private Long processId;
    /** 工单rkstationId */
    private Long workstationId;
    /** operationContent */
    private String operationContent;
    /** 标志(0否 1是) */
    private Integer backflushFlag;
    /** sortOrder */
    private Integer sortOrder;
    /** remark */
    private String remark;
    @TableLogic
    private String delFlag;
}
