package com.nexus.erp.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 入库消息，用于异步触发库存入库过账操作
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockInMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库单ID */
    private Long receiveId;
    /** 仓库ID */
    private Long warehouseId;
    /** 库位ID */
    private Long locationId;
    /** 来源类型: PURCHASE/PRODUCTION/REWORK */
    private String sourceType;
}
