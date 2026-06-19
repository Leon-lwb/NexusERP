package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * IQC检验完成消费者（ERP侧）
 * <p>
 * 消费MES发出的IQC检验完成消息，回写ERP收货单(WmMaterialReceive)质检状态：
 * QUALIFIED -> APPROVED(允许上架入库); UNQUALIFIED -> REJECTED(标记不合格)。
 * <p>
 * 边界: 仅标记收货单状态 + 回写 iqcId/iqcCode，不实现不良品入库/退货流程。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ErpIqcCompletedConsumer extends BaseMessageConsumer<IqcCompletedMessage> {

    /** ERP收货单状态: 审批通过(待上架) */
    private static final String RECEIVE_STATUS_APPROVED = "APPROVED";
    /** ERP收货单状态: 已上架(待入库) */
    private static final String RECEIVE_STATUS_SHELVED = "SHELVED";
    /** ERP收货单状态: 已完成 */
    private static final String RECEIVE_STATUS_FINISHED = "FINISHED";
    /** ERP收货单状态: 已拒绝(不合格) */
    private static final String RECEIVE_STATUS_REJECTED = "REJECTED";

    private final WmMaterialReceiveMapper materialReceiveMapper;

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
     * 处理IQC检验完成消息，更新ERP收货单质检状态
     *
     * @param message IQC检验完成消息
     */
    @Override
    protected void doProcess(IqcCompletedMessage message) {
        // MQ消费者线程无登录态，需从消息恢复租户上下文，否则多租户拦截器加tenant_id条件查不到数据
        // 租户ID格式: 默认'000000'(6位零填充)，Long 0 -> "000000"
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doUpdate(message));
    }

    /**
     * 执行收货单质检状态回写
     *
     * @param message IQC检验完成消息
     */
    private void doUpdate(IqcCompletedMessage message) {
        WmMaterialReceive receive = materialReceiveMapper.selectById(message.getReceiveId());
        if (receive == null) {
            log.warn("收货单不存在: receiveId={}", message.getReceiveId());
            return;
        }

        // 回写IQC关联
        receive.setIqcId(message.getIqcId());
        receive.setIqcCode(message.getIqcCode());

        // 仅标记状态: QUALIFIED -> APPROVED(允许上架); UNQUALIFIED -> REJECTED
        // 已 APPROVED/SHELVED/FINISHED 的单据不回退(避免影响已上架/已入库流程)
        String status = receive.getStatus();
        if ("QUALIFIED".equals(message.getInspectResult())) {
            if (!RECEIVE_STATUS_APPROVED.equals(status)
                && !RECEIVE_STATUS_SHELVED.equals(status)
                && !RECEIVE_STATUS_FINISHED.equals(status)) {
                receive.setStatus(RECEIVE_STATUS_APPROVED);
            }
        } else if ("UNQUALIFIED".equals(message.getInspectResult())) {
            receive.setStatus(RECEIVE_STATUS_REJECTED);
            receive.setRemark("IQC检验不合格: " + message.getIqcCode());
        }
        materialReceiveMapper.updateById(receive);
        log.info("IQC回写收货单完成: receiveId={}, iqcCode={}, result={}, newStatus={}",
            message.getReceiveId(), message.getIqcCode(), message.getInspectResult(), receive.getStatus());
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
