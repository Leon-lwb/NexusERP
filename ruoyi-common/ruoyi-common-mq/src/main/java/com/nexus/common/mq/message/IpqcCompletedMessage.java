package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * IPQC检验完成消息，MES过程质检完成后发送到ERP
 * <p>
 * 位于 ruoyi-common-mq 供 MES(生产者) 与 ERP(消费者) 跨模块共享。
 * ERP 消费端按 erpWoId 定位工单，回写聚合质检状态(UNQUALIFIED 粘性)。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IpqcCompletedMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** IPQC检验ID */
    private Long ipqcId;
    /** IPQC检验编号 */
    private String ipqcNo;
    /** MES工单ID */
    private Long mesWoId;
    /** ERP工单ID（消费端定位 PrdWorkOrder 的关键） */
    private Long erpWoId;
    /** 工单编号 */
    private String woNo;
    /** 检验结果: QUALIFIED/UNQUALIFIED */
    private String inspectResult;
    /** 不良数量 */
    private BigDecimal defectQty;
    /** 不良率(%) */
    private BigDecimal defectRate;
    /** 检验员ID */
    private Long inspectorId;
    /** 检验员姓名 */
    private String inspectorName;
}
