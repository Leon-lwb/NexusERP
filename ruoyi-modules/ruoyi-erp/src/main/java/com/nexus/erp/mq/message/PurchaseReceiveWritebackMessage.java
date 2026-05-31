package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 采购收货回写消息，用于异步回写采购订单已收货数量
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseReceiveWritebackMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库单ID */
    private Long receiveId;
}
