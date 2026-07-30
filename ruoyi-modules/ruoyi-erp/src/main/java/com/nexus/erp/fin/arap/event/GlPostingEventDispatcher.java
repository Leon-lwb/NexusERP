package com.nexus.erp.fin.arap.event;

import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.mq.GlPostingMessage;
import com.nexus.erp.pu.invoice.event.PuInvoiceIssuedEvent;
import com.nexus.erp.so.event.SoInvoiceIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * GL 过账事件分发器:监听发票 ISSUED 事件 -> 异步发 GlPostingMessage 到 GL_POSTING_QUEUE。
 * <p>用 @TransactionalEventListener(AFTER_COMMIT) 确保发票状态落库后再发过账消息;
 * @Async("financialExecutor") 在独立线程池执行,租户上下文通过事件携带的 tenantId 恢复(写入消息 tenantIdStr)。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GlPostingEventDispatcher {

    private final ReliableMessageProducer messageProducer;

    /**
     * 监听销售发票 ISSUED 事件,事务提交后异步发 GlPostingMessage 到 GL_POSTING_SO_INVOICE 路由。
     * so 模块不依赖 fin 模块,通过事件解耦(方案C)。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("financialExecutor")
    public void onSoInvoiceIssued(SoInvoiceIssuedEvent event) {
        sendGlPosting(GlConstants.SOURCE_SO_INVOICE, event.getInvoiceId(),
            event.getTenantId(), RoutingKeyConstants.GL_POSTING_SO_INVOICE);
    }

    /**
     * 监听采购发票 ISSUED 事件,事务提交后异步发 GlPostingMessage 到 GL_POSTING_PU_INVOICE 路由。
     * pu 模块不依赖 fin 模块,通过事件解耦(方案C)。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("financialExecutor")
    public void onPuInvoiceIssued(PuInvoiceIssuedEvent event) {
        sendGlPosting(GlConstants.SOURCE_PU_INVOICE, event.getInvoiceId(),
            event.getTenantId(), RoutingKeyConstants.GL_POSTING_PU_INVOICE);
    }

    /** 构建 GlPostingMessage 并发送到 TOPIC_EXCHANGE,租户 ID 写入消息体供消费端恢复上下文 */
    private void sendGlPosting(String sourceType, Long sourceId, String tenantId, String routingKey) {
        GlPostingMessage msg = new GlPostingMessage();
        msg.setSourceType(sourceType);
        msg.setSourceId(sourceId);
        msg.setTenantIdStr(tenantId);
        msg.setSourceModule("fin");
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, routingKey, msg);
        log.info("发送GL过账消息: source={}, sourceId={}", sourceType, sourceId);
    }
}
