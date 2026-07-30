package com.nexus.erp.so.event;

import org.springframework.context.ApplicationEvent;

/**
 * 销售发票已开票(ISSUED)事件。
 * <p>fin 的 GlPostingEventDispatcher 监听本事件(@TransactionalEventListener AFTER_COMMIT + financialExecutor),
 * 异步发 GlPostingMessage 到 GL_POSTING_QUEUE,由 SoInvoicePoster 制单。
 * so 不依赖 fin,通过事件解耦(方案 C:fin 单向依赖 so/pu)。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
public class SoInvoiceIssuedEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final String tenantId;

    /** 创建销售发票已开票事件 */
    public SoInvoiceIssuedEvent(Object source, Long invoiceId, String tenantId) {
        super(source);
        this.invoiceId = invoiceId;
        this.tenantId = tenantId;
    }

    /** 返回发票ID */
    public Long getInvoiceId() {
        return invoiceId;
    }

    /** 返回租户ID */
    public String getTenantId() {
        return tenantId;
    }
}
