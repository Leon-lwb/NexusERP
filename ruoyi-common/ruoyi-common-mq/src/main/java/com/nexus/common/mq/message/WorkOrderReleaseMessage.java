package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 工单下达消息，ERP审批通过后发送到MES系统
 * <p>
 * 迁移到ruoyi-common-mq共享模块，供ERP生产者和MES消费者共同引用。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderReleaseMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** ERP工单ID */
    private Long woId;
    /** 工单编号 */
    private String woNo;
    /** 产品物料ID */
    private Long materialId;
    /** 产品编码 */
    private String materialCode;
    /** 产品名称 */
    private String materialName;
    /** 计划生产数量 */
    private BigDecimal plannedQty;
    /** 计划开工日期 */
    private Date plannedStartDate;
    /** 计划完工日期 */
    private Date plannedEndDate;
    /** 车间ID */
    private Long workshopId;
    /** BOM ID */
    private Long bomId;
    /**
     * 工艺路线ID（可空）
     * <p>
     * ERP prd_work_order 表暂无routing_id字段，此值由MES桥接服务根据materialId查询默认工艺路线填充。
     * 若未来ERP工单增加routing_id字段，则直接使用该字段值。
     */
    private Long routingId;
}
