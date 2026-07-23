package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * IQC检验完成消息，MES质检完成后发送到ERP
 * <p>
 * 位于 ruoyi-common-mq 供 MES(生产者) 与 ERP(消费者) 跨模块共享。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IqcCompletedMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** IQC检验ID */
    private Long iqcId;
    /** IQC检验编号 */
    private String iqcCode;
    /** ERP收货单ID */
    private Long receiveId;
    /** 物料ID(ERP 据此按行回写 wm_arrival_notice_line) */
    private Long materialId;
    /** 检验结果: QUALIFIED/UNQUALIFIED */
    private String inspectResult;
    /** 检验数量 */
    private BigDecimal quantity;
}
