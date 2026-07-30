package com.nexus.erp.pu.invoice.event;

import org.springframework.context.ApplicationEvent;

/**
 * 采购发票已收票(ISSUED)事件。
 * <p>fin 的 GlPostingEventDispatcher 监听本事件(@TransactionalEventListener AFTER_COMMIT + financialExecutor),
 * 异步发 GlPostingMessage 到 GL_POSTING_QUEUE,由 PuInvoicePoster 制单。
 * pu 不依赖 fin,通过事件解耦(方案 C:fin 单向依赖 so/pu)。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
public class PuInvoiceIssuedEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final String tenantId;

    /** 创建采购发票已收票事件 */
    public PuInvoiceIssuedEvent(Object source, Long invoiceId, String tenantId) {
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
