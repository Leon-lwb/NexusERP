package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 委外库存消息，用于异步通知委外订单库存操作完成
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubcontractStockMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 委外订单ID */
    private Long subcontractId;
    /** 操作类型: ISSUE/RECEIVE */
    private String operationType;
    /** 物料ID */
    private Long materialId;
    /** 数量 */
    private BigDecimal quantity;
}
