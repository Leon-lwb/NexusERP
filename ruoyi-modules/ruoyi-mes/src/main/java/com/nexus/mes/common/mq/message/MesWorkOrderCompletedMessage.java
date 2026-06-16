package com.nexus.mes.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * MES工单完工消息，MES工单完成后发送到ERP
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesWorkOrderCompletedMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** MES工单ID */
    private Long mesWoId;
    /** ERP工单ID */
    private Long erpWoId;
    /** 工单编号 */
    private String woNo;
    /** 完工数量 */
    private BigDecimal completedQty;
    /** 报废数量 */
    private BigDecimal scrappedQty;
}
