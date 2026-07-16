package com.nexus.erp.so.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 销售订单履行完成事件
 * <p>
 * 销售订单 fulfill 成功后发布，供下游订阅（如自动开票、IM 通知、报表刷新等）。
 * 当前仅发布事件，监听者按需后续扩展。
 *
 * @author nexus
 */
@Getter
public class SalesOrderFulfilledEvent extends ApplicationEvent {

    private final Long soId;
    private final String soNo;
    /**
     * 租户ID(异步监听线程不传播 Sa-Token ThreadLocal,显式携带以供 TenantHelper.dynamic 恢复)
     */
    private final String tenantId;

    public SalesOrderFulfilledEvent(Object source, Long soId, String soNo, String tenantId) {
        super(source);
        this.soId = soId;
        this.soNo = soNo;
        this.tenantId = tenantId;
    }
}
