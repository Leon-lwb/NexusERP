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
    /** MES工单ID */
    private Long mesWoId;
    /** MBOMLineId */
    private Long mbomLineId;
    /** BOMID */
    private Long bomId;
    /** BOMDetailId */
    private Long bomDetailId;
    /** 子级MaterialId */
    private Long childMaterialId;
    /** 子级MaterialCode */
    private String childMaterialCode;
    /** 子级MaterialName */
    private String childMaterialName;
    /** quantityPerParent */
    private BigDecimal quantityPerParent;
    /** 比率 */
    private BigDecimal lossRate;
    /** 合计Quantity */
    private BigDecimal totalQuantity;
    /** 工艺路线StepId */
    private Long routingStepId;
    /** 序号 */
    private Integer sequenceNum;
    /** 工序ID */
    private Long processId;
    /** 工序Code */
    private String processCode;
    /** 工序Name */
    private String processName;
    /** 工单rkstationId */
    private Long workstationId;
    /** 工单rkstationCode */
    private String workstationCode;
    /** 工单rkstationName */
    private String workstationName;
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
