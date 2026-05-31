package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 返工库存消息，用于异步通知返工订单库存操作完成
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReworkStockMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 返工订单ID */
    private Long reworkId;
    /** 操作类型: ISSUE/RECEIVE */
    private String operationType;
    /** 物料ID */
    private Long materialId;
    /** 数量 */
    private BigDecimal quantity;
}
