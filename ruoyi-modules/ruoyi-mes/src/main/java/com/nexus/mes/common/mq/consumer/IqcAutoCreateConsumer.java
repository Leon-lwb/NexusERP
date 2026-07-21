package com.nexus.mes.common.mq.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
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
 * 幂等：同一 receiveId 已创建 IQC 则跳过（查 qc_iqc.receive_id）。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IqcAutoCreateConsumer extends BaseMessageConsumer<ReceiveApprovedMessage> {

    private final IQcIqcService iqcService;
    private final QcIqcMapper iqcMapper;

    @Override
    protected Class<ReceiveApprovedMessage> getMessageClass() {
        return ReceiveApprovedMessage.class;
    }

    @Override
    protected void doProcess(ReceiveApprovedMessage message) {
        log.info("收到收货审批通过消息: receiveId={}, receiveCode={}, materialCode={}, quantity={}",
            message.getReceiveId(), message.getReceiveCode(),
            message.getMaterialCode(), message.getQuantity());

        // 消费者线程无登录态，从消息恢复租户上下文（参考 WorkOrderReleaseConsumer:42-50）
        // 租户ID格式: DDL默认 '000000'(6位零填充)，Long 0 -> "000000"
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doCreateIqc(message));
    }

    /**
     * 幂等创建 IQC：同 receiveId 已存在则跳过。
     */
    private void doCreateIqc(ReceiveApprovedMessage message) {
        Long existCount = iqcMapper.selectCount(Wrappers.<QcIqc>lambdaQuery()
            .eq(QcIqc::getReceiveId, message.getReceiveId()));
        if (existCount != null && existCount > 0) {
            log.info("[IqcAutoCreate] 收货单 {} 已存在 IQC，跳过自动创建", message.getReceiveId());
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
            log.info("[IqcAutoCreate] 收货单 {} 已自动创建 IQC", message.getReceiveId());
        } catch (Exception e) {
            log.error("[IqcAutoCreate] 收货单 {} 自动创建 IQC 失败: {}",
                message.getReceiveId(), e.getMessage(), e);
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
