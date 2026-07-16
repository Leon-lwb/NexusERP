package com.nexus.erp.so.event;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.service.ISoInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 销售订单履行完成事件监听器:在订单事务提交后异步生成发票草稿。
 * <p>订单 fulfill 后,按"已发货未开票"自动生成 so_invoice 草稿
 * (详见 {@link ISoInvoiceService#generateDraftFromOrder(Long)})。</p>
 *
 * <p><b>事务后触发</b>:用 {@link TransactionalEventListener}(AFTER_COMMIT) 而非普通 @EventListener,
 * 否则异步监听线程会在 fulfill 事务提交前读到旧的 delivered_qty,导致可开票量计算错误。</p>
 *
 * <p><b>异步租户上下文</b>:{@code financialExecutor} 线程不传播 Sa-Token ThreadLocal,
 * 必须 {@link TenantHelper#dynamic} 恢复租户,否则 tenant_id 落 0(见 AI 异步租户上下文坑)。
 * 异常吞掉+告警,不影响订单履约主流程。</p>
 *
 * @author nexus
 * @since 2026-07-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderFulfilledEventListener {

    private final ISoInvoiceService soInvoiceService;

    @Async("financialExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFulfilled(SalesOrderFulfilledEvent event) {
        try {
            TenantHelper.dynamic(event.getTenantId(), () -> {
                Long invoiceId = soInvoiceService.generateDraftFromOrder(event.getSoId());
                if (invoiceId == null) {
                    log.info("销售订单履约后未生成发票草稿(无可开票行或订单不存在), soId={}, soNo={}",
                        event.getSoId(), event.getSoNo());
                }
            });
        } catch (Exception e) {
            // 异步生成失败不影响订单履约主流程,告警即可
            log.error("销售订单履约后自动生成发票草稿失败, soId={}, soNo={}", event.getSoId(), event.getSoNo(), e);
        }
    }
}
