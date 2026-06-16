package com.nexus.mes.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * IQC检验完成消息，MES质检完成后发送到ERP
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
    /** ERP收货单ID */
    private Long receiveId;
    /** 检验结果: QUALIFIED/UNQUALIFIED */
    private String inspectResult;
    /** 检验数量 */
    private BigDecimal quantity;
}
