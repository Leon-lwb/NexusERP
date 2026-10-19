package com.nexus.mes.label.service;

import com.nexus.mes.label.domain.MesLabelPrintTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.Charset;
import java.util.UUID;

/** 领取提交后在事务外执行一次发送；任何发送中故障都不得退回待发队列。 */
@Service
@RequiredArgsConstructor
public class LabelPrintDispatcher {
    private final LabelPrintTaskStore tasks;
    private final LabelPrinterTransport transport;

    /** 自动调度真实入口：先封存过期任务，再逐条领取已提交待发任务。 */
    public void pollTenant() {
        requireOutsideTransaction();
        recoverTenant();
        for (Long id : tasks.pendingIds()) dispatch(id);
    }

    /** 独立回收入口不调用网络，发送线程阻塞时仍可在另一调度线程封存过期任务。 */
    public void recoverTenant() { requireOutsideTransaction(); tasks.recoverExpired(); }

    /** 一个任务只能通过一次 PENDING 领取；竞争失败及所有终态都不再次调用网络。 */
    public MesLabelPrintTask dispatch(Long taskId) {
        requireOutsideTransaction();
        String token = UUID.randomUUID().toString();
        MesLabelPrintTask claimed = tasks.claim(taskId, token, 120);
        if (claimed == null) return tasks.find(taskId);
        LabelPrinterTransport.Result result;
        try {
            var record = tasks.record(claimed);
            LabelPrintSnapshot.payload(record, record.getRenderedPayload(), Charset.forName(record.getPayloadCharset()));
            if (!tasks.hasCurrentLease(taskId, token)) {
                tasks.recoverExpired();
                return tasks.find(taskId);
            }
            result = transport.send(record);
        } catch (Exception unexpected) {
            result = new LabelPrinterTransport.Result("UNKNOWN", "发送工作线程异常，是否写出指令未知；任务不会自动重发");
        }
        tasks.finish(taskId, token, result);
        return tasks.find(taskId);
    }

    private void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("打印领取与网络发送必须在业务事务之外");
    }
}
