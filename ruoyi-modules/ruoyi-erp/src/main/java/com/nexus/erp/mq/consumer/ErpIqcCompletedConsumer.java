package com.nexus.erp.mq.consumer;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.service.IWmArrivalNoticeLineService;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService.Completion;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * MES 的实际 UNQUALIFIED 结论保留既有基于身份的库存和批次隔离。数量单位或
 * 创建时来源未知时，绝不生成证据或释放库存。不合格消息重放的保护投影在
 * Redis 的 DONE/PENDING 快捷分支之前执行；绝不修改已入账的收货单头。
 * 先读取并关闭 MES 快照，再进入 ERP 独立掌控的可重复读事务，不宣称跨库 ACID。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ErpIqcCompletedConsumer extends BaseMessageConsumer<IqcCompletedMessage> {

    private final IWmMaterialReceiveService materialReceiveService;
    private final IWmArrivalNoticeLineService arrivalNoticeLineService;
    private final IWmBatchService batchService;
    private final IWmMaterialStockService materialStockService;
    private final WmReceiptIqcEvidenceService iqcEvidenceService;

    /**
     * 获取消息类型
     *
     * @return IqcCompletedMessage类型
     */
    @Override
    protected Class<IqcCompletedMessage> getMessageClass() {
        return IqcCompletedMessage.class;
    }

    /**
     * 处理IQC检验完成消息，按物料行回写ERP质检结果
     *
     * @param message IQC检验完成消息
     */
    @Override
    protected void doProcess(IqcCompletedMessage message) {
        iqcEvidenceService.consumeCompleted(message, this::doUpdate);
    }

    /**
     * 按物料行回写IQC结果：
     * <ol>
     *   <li>收货单头 iqc_id/iqc_code（展示用）</li>
     *   <li>到货通知行 iqc_id/iqc_code/iqc_status（confirmShelf 门禁依据）</li>
     *   <li>批次 qc_status（按收货行批次号定位, 发料门禁依据）</li>
     * </ol>
     *
     * @param completed MES来源读取后的实际完成事实
     */
    private void doUpdate(Completion completed) {
        if (!"UNQUALIFIED".equals(completed.result())) throw new ServiceException("UNIT_ORIGIN_UNPROVEN");
        WmMaterialReceive receive = materialReceiveService.getById(completed.receiveId());
        if (receive == null) throw new ServiceException("IQC_RECEIPT_PROJECTION_MISSING");
        // 真实 DETAIL/RECEIVE_IN 库存标识已在本次投影前于服务独立掌控的可重复读事务内
        // 完成锁定和冻结，并已标记原始批次。绝不调用仅依据 LINE 的写入方法。
        if (receive.getNoticeId() != null) {
            arrivalNoticeLineService.updateIqcResult(receive.getNoticeId(), completed.materialId(),
                completed.inspectionId(), completed.inspectionCode(), "UNQUALIFIED");
        }
        log.info("真实IQC不合格精确隔离完成: receiveId={}, materialId={}", completed.receiveId(), completed.materialId());
    }

    @RabbitListener(queues = QueueConstants.ERP_IQC_COMPLETED_QUEUE)
    public void onMessage(IqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        try {
            // 基类在 doProcess 中恢复同一规范化租户。也必须在 Redis 的
            // DONE/PENDING 快捷分支之前核验，否则实际数据主体已变化的消息也可能被确认。
            String tenantId = resolveTenantId(message);
            TenantHelper.dynamic(tenantId, () -> iqcEvidenceService.consumeCompleted(message, this::doUpdate));
        } catch (RuntimeException unsafeReplay) {
            // 此路径不得调用基类的重复消息确认逻辑。来源冲突或证据不可用时应隔离消息，而非
            // 无限重新入队；运维可在修复后重新投递。
            log.warn("IQC真实结果/隔离预检失败，消息隔离: messageId={}", message.getMessageId());
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, false);
            return;
        }
        processMessage(message, channel, amqpMessage);
    }
}
