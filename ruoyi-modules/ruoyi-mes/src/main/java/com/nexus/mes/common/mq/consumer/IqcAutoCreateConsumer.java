package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.service.IQcIqcService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 ERP 收货审批通过消息，自动创建 MES IQC 检验单。
 * <p>
 * 幂等：同一 (receiveId, materialId) 已创建 IQC 则跳过（查 qc_iqc.receive_id + material_id）。
 * 同一收货单多个需检物料时，ERP 按物料逐条发消息，此处逐条建独立 IQC。
 * <p>
 * 数据源注意：幂等查询与新增均经 {@link IQcIqcService} 调用，走类级 {@code @DS("mes")}
 * 切换到 ry-mes 库（qc_iqc 表所在库）。切勿在此直调 QcIqcMapper——消费者线程无 {@code @DS}
 * 上下文，直调 Mapper 会落到默认 master 库（ry-vue）报 Table doesn't exist。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IqcAutoCreateConsumer extends BaseMessageConsumer<ReceiveApprovedMessage> {

    private final IQcIqcService iqcService;

    @Override
    protected Class<ReceiveApprovedMessage> getMessageClass() {
        return ReceiveApprovedMessage.class;
    }

    @Override
    protected void doProcess(ReceiveApprovedMessage message) {
        log.info("收到收货审批通过消息: receiveId={}, receiveCode={}, materialCode={}, quantity={}",
            message.getReceiveId(), message.getReceiveCode(),
            message.getMaterialCode(), message.getQuantity());

        doCreateIqc(message);
    }

    /**
     * 幂等创建 IQC：同 (receiveId, materialId) 已存在则跳过。
     * <p>
     * 幂等查询与新增都走 {@link IQcIqcService}（类级 {@code @DS("mes")}），确保落到 ry-mes 库。
     */
    private void doCreateIqc(ReceiveApprovedMessage message) {
        if (iqcService.existsByReceiveAndMaterial(message.getReceiveId(), message.getMaterialId())) {
            log.info("[IqcAutoCreate] 收货单 {} 物料 {} 已存在 IQC，跳过自动创建",
                message.getReceiveId(), message.getMaterialId());
            return;
        }

        try {
            QcIqcBo bo = new QcIqcBo();
            bo.setReceiveId(message.getReceiveId());
            bo.setMaterialId(message.getMaterialId());
            bo.setMaterialCode(message.getMaterialCode());
            bo.setMaterialName(message.getMaterialName());
            bo.setVendorId(message.getVendorId());
            bo.setVendorName(message.getVendorName());
            bo.setQuantity(message.getQuantity());
            // iqcNo / status / inspectResult 由 QcIqcServiceImpl.insertByBo 内部生成
            iqcService.insertByBo(bo);
            log.info("[IqcAutoCreate] 收货单 {} 物料 {} 已自动创建 IQC",
                message.getReceiveId(), message.getMaterialId());
        } catch (Exception e) {
            log.error("[IqcAutoCreate] 收货单 {} 物料 {} 自动创建 IQC 失败: {}",
                message.getReceiveId(), message.getMaterialId(), e.getMessage(), e);
            throw new RuntimeException("IQC 自动创建失败", e);
        }
    }

    @RabbitListener(queues = QueueConstants.MES_IQC_CREATE_QUEUE)
    /**
     * onMessage 操作
     * @param message message
     * @param channel channel
     * @param amqpMessage amqpMessage
     */
    public void onMessage(ReceiveApprovedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
