package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * IPQC检验完成消费者（ERP侧）
 * <p>
 * 消费MES发出的IPQC检验完成消息，回写ERP工单(prd_work_order)聚合质检状态。
 * <p>
 * UNQUALIFIED 粘性：一旦工单质检状态为 UNQUALIFIED，后续工序合格的 IPQC 不会覆盖
 * （防止前序不合格被后序合格掩盖；恢复需人工介入或重新检验流程）。
 * QUALIFIED 仅在当前状态为 null/PENDING/QUALIFIED 时写入。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ErpIpqcCompletedConsumer extends BaseMessageConsumer<IpqcCompletedMessage> {

    /** 质检状态: 合格 */
    private static final String QUALITY_QUALIFIED = "QUALIFIED";
    /** 质检状态: 不合格 */
    private static final String QUALITY_UNQUALIFIED = "UNQUALIFIED";
    /** 质检状态: 待检 */
    private static final String QUALITY_PENDING = "PENDING";

    private final PrdWorkOrderMapper workOrderMapper;

    /**
     * 获取消息类型
     *
     * @return IpqcCompletedMessage类型
     */
    @Override
    protected Class<IpqcCompletedMessage> getMessageClass() {
        return IpqcCompletedMessage.class;
    }

    /**
     * 处理IPQC检验完成消息，更新ERP工单聚合质检状态
     *
     * @param message IPQC检验完成消息
     */
    @Override
    protected void doProcess(IpqcCompletedMessage message) {
        // MQ消费者线程无登录态，需从消息恢复租户上下文
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doUpdate(message));
    }

    /**
     * 执行工单聚合质检状态回写
     *
     * @param message IPQC检验完成消息
     */
    private void doUpdate(IpqcCompletedMessage message) {
        PrdWorkOrder wo = workOrderMapper.selectById(message.getErpWoId());
        if (wo == null) {
            log.warn("ERP工单不存在: erpWoId={}", message.getErpWoId());
            return;
        }

        String currentStatus = wo.getQualityStatus();
        String newStatus;
        if (QUALITY_UNQUALIFIED.equals(message.getInspectResult())) {
            // 不合格: 直接置为 UNQUALIFIED(已 UNQUALIFIED 不变)
            newStatus = QUALITY_UNQUALIFIED;
        } else {
            // 合格: UNQUALIFIED 粘性, 不覆盖已存在的不合格
            if (QUALITY_UNQUALIFIED.equals(currentStatus)) {
                log.info("工单已为UNQUALIFIED, 跳过合格回写(粘性): woNo={}, ipqcNo={}",
                    wo.getWoNo(), message.getIpqcNo());
                return;
            }
            newStatus = QUALITY_QUALIFIED;
        }

        int rows = workOrderMapper.update(null, Wrappers.<PrdWorkOrder>lambdaUpdate()
            .set(PrdWorkOrder::getQualityStatus, newStatus)
            .set(PrdWorkOrder::getQualityCheckId, message.getIpqcId())
            .set(PrdWorkOrder::getQualityCheckNo, message.getIpqcNo())
            .set(PrdWorkOrder::getInspectorId, message.getInspectorId())
            .set(PrdWorkOrder::getInspectorName, message.getInspectorName())
            .set(PrdWorkOrder::getInspectTime, new Date())
            .eq(PrdWorkOrder::getWoId, message.getErpWoId()));
        log.info("IPQC回写工单完成: erpWoId={}, woNo={}, ipqcNo={}, result={}, newStatus={}, affectedRows={}",
            message.getErpWoId(), wo.getWoNo(), message.getIpqcNo(), message.getInspectResult(), newStatus, rows);
    }

    /**
     * 监听IPQC检验完成队列消息
     *
     * @param message     IPQC检验完成消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.ERP_IPQC_COMPLETED_QUEUE)
    public void onMessage(IpqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
