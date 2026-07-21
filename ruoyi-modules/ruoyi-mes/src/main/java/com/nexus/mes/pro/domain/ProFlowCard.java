package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES流转卡对象 pro_flow_card
 * <p>属于 MES 生产管理模块，表达 流转卡 的领域模型。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_flow_card")
public class ProFlowCard extends TenantEntity {

    /** 流转卡ID(主键) */
    @TableId(value = "card_id")
    private Long cardId;

    /** 流转卡编号 */
    private String cardNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 已完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 当前工序步骤记录ID */
    private Long currentStepId;

    /** 当前工序序号 */
    private Integer currentStepSeq;

    /** 流转卡状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED */
    private String cardStatus;

    /** 流转卡条码 */
    private String barcode;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
