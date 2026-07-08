package com.nexus.erp.so.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * 销售订单行生产完成事件
 * <p>
 * 当 SO 行的已投产数量（production_qty）达到订单数量（ordered_qty）时发布，
 * 供下游订阅（如自动创建销售出库单、IM 通知、报表刷新等）。
 * 当前仅发布事件，监听者按需后续扩展。
 *
 * @author nexus
 */
@Getter
public class SoLineProducedEvent extends ApplicationEvent {

    private final Long soId;
    private final Long lineId;
    private final String materialCode;
    private final BigDecimal producedQty;

    public SoLineProducedEvent(Object source, Long soId, Long lineId, String materialCode, BigDecimal producedQty) {
        super(source);
        this.soId = soId;
        this.lineId = lineId;
        this.materialCode = materialCode;
        this.producedQty = producedQty;
    }
}
