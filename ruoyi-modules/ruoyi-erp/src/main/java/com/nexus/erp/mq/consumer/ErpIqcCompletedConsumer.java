package com.nexus.erp.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.service.IWmArrivalNoticeLineService;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * IQC检验完成消费者（ERP侧）
 * <p>
 * 消费MES发出的IQC检验完成消息，按物料行回写ERP到货通知行(wm_arrival_notice_line)的质检结果：
 * iqc_id / iqc_code / iqc_status(QUALIFIED|UNQUALIFIED)。
 * <p>
 * 收货单头仅回写 iqc_id/iqc_code(展示用)，不再强改收货单状态——上架门禁由
 * {@code WmMaterialReceiveCrudServiceImpl.confirmShelf} 按 iqc_status 校验，
 * 避免多需检料时单头状态在 QUALIFIED/REJECTED 间反复跳变。
 * <p>
 * 边界: 仅回写质检结果，不实现不良品入库/退货流程。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ErpIqcCompletedConsumer extends BaseMessageConsumer<IqcCompletedMessage> {

    private final IWmMaterialReceiveService materialReceiveService;
    private final IWmArrivalNoticeLineService arrivalNoticeLineService;
    private final IWmBatchService batchService;
    private final IWmMaterialStockService materialStockService;

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
        doUpdate(message);
    }

    /**
     * 按物料行回写IQC结果：
     * <ol>
     *   <li>收货单头 iqc_id/iqc_code（展示用）</li>
     *   <li>到货通知行 iqc_id/iqc_code/iqc_status（confirmShelf 门禁依据）</li>
     *   <li>批次 qc_status（按收货行批次号定位, 发料门禁依据）</li>
     * </ol>
     *
     * @param message IQC检验完成消息
     */
    private void doUpdate(IqcCompletedMessage message) {
        WmMaterialReceive receive = materialReceiveService.getById(message.getReceiveId());
        if (receive == null) {
            log.warn("收货单不存在: receiveId={}", message.getReceiveId());
            return;
        }

        // 1. 收货单头回写 iqc_id/iqc_code/iqc_status（展示用 + 无到货通知时的上架门禁依据）
        receive.setIqcId(message.getIqcId());
        receive.setIqcCode(message.getIqcCode());
        receive.setIqcStatus(message.getInspectResult());
        materialReceiveService.updateById(receive);

        // 2. 按物料行回写 wm_arrival_notice_line: iqc_id/iqc_code/iqc_status（门禁依据）
        if (receive.getNoticeId() != null && message.getMaterialId() != null) {
            int rows = arrivalNoticeLineService.updateIqcResult(
                receive.getNoticeId(), message.getMaterialId(),
                message.getIqcId(), message.getIqcCode(), message.getInspectResult());
            log.info("IQC按行回写: receiveId={}, materialId={}, result={}, 影响行数={}",
                message.getReceiveId(), message.getMaterialId(), message.getInspectResult(), rows);
        } else {
            log.warn("IQC回写缺少 noticeId/materialId，跳过按行回写: receiveId={}, materialId={}",
                message.getReceiveId(), message.getMaterialId());
        }

        // 3. 批次质检状态回写(按收货行批次号定位; 发料门禁依据, M3 批次 5)
        if (message.getMaterialId() != null) {
            batchService.markQcStatusByReceive(message.getReceiveId(), message.getMaterialId(),
                message.getInspectResult());
        }

        // 4. 不合格冻结库存: 定位该收货单对应批次并冻结库存行(无批次号时回退批次ID; 都定位不到则记 warn,
        //    由 confirmShelf 的单据级 iqc_status 门禁兜底)
        if ("UNQUALIFIED".equals(message.getInspectResult()) && message.getMaterialId() != null) {
            int frozen = materialStockService.freezeStockByReceiveBatches(message.getReceiveId(), message.getMaterialId());
            log.info("IQC不合格冻结库存: receiveId={}, materialId={}, 冻结行数={}",
                message.getReceiveId(), message.getMaterialId(), frozen);
        }

        // 5. 复检/让步后合格解冻: 冻结必须可解, 否则被 IQC 冻结的库存永久无法发料/出库(审计 MES-X-01)。
        //    复检合格或让步接收后重新判定合格时, 解除该收货单该物料的库存冻结。
        if ("QUALIFIED".equals(message.getInspectResult()) && message.getMaterialId() != null) {
            int unfrozen = materialStockService.unfreezeByReceive(message.getReceiveId(), message.getMaterialId(),
                "IQC合格解冻(" + message.getIqcCode() + ")");
            log.info("IQC合格解冻库存: receiveId={}, materialId={}, 解冻行数={}",
                message.getReceiveId(), message.getMaterialId(), unfrozen);
        }
        log.info("IQC回写收货单完成: receiveId={}, iqcCode={}, result={}",
            message.getReceiveId(), message.getIqcCode(), message.getInspectResult());
    }

    /**
     * 监听IQC检验完成队列消息
     *
     * @param message     IQC检验完成消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.ERP_IQC_COMPLETED_QUEUE)
    public void onMessage(IqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
