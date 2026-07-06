package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.MesWorkOrderCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.enums.WoStatusEnum;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * MES工单完工消费者（ERP侧）
 * <p>
 * 消费MES发出的工单完工消息，回写ERP工单(PrdWorkOrder)状态：
 * woStatus=COMPLETED + completedQty/scrapQty累加 + actualEndDate，
 * 然后复用既有完工入库链路(发ProductionCompleteMessage -> ProductionBackflushConsumer)
 * 触发倒冲领料 + 成品入库。
 * <p>
 * 幂等: 工单已 COMPLETED/CLOSED 时跳过。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ErpWorkOrderCompletedConsumer extends BaseMessageConsumer<MesWorkOrderCompletedMessage> {

    private final IPrdWorkOrderService workOrderService;

    /**
     * 获取消息类型
     *
     * @return MesWorkOrderCompletedMessage类型
     */
    @Override
    protected Class<MesWorkOrderCompletedMessage> getMessageClass() {
        return MesWorkOrderCompletedMessage.class;
    }

    /**
     * 处理MES工单完工消息，更新ERP工单并触发完工入库
     *
     * @param message MES工单完工消息
     */
    @Override
    protected void doProcess(MesWorkOrderCompletedMessage message) {
        // MQ消费者线程无登录态，需从消息恢复租户上下文
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doComplete(message));
    }

    /**
     * 执行工单完工回写 + 触发完工入库链路
     *
     * @param message MES工单完工消息
     */
    private void doComplete(MesWorkOrderCompletedMessage message) {
        Long erpWoId = message.getErpWoId();
        if (erpWoId == null) {
            log.warn("MES完工消息缺少erpWoId, 跳过");
            return;
        }
        PrdWorkOrder wo = workOrderService.selectPrdWorkOrderByWoId(erpWoId);
        if (wo == null) {
            log.warn("ERP工单不存在: erpWoId={}", erpWoId);
            return;
        }
        // 幂等: 已完工/已关闭跳过
        WoStatusEnum currentStatus = WoStatusEnum.fromCode(wo.getWoStatus());
        if (currentStatus == null) {
            log.warn("工单状态为空, 跳过回写: woNo={}", wo.getWoNo());
            return;
        }
        if (currentStatus == WoStatusEnum.COMPLETED || currentStatus.isTerminal()) {
            log.info("工单已完工/已关闭, 跳过回写: woNo={}, status={}", wo.getWoNo(), wo.getWoStatus());
            return;
        }

        // MES完工是权威信号: 若ERP工单尚在"已下达"(未开工), 先置为生产中, 使状态机合法
        // (RELEASED → IN_PROGRESS → COMPLETED), 避免因状态偏差导致回写永久失败
        if (currentStatus == WoStatusEnum.RELEASED) {
            log.info("ERP工单尚在已下达, 先标记生产中再完工回写: woNo={}", wo.getWoNo());
            workOrderService.markInProgress(erpWoId);
        }

        // 使用消息中的实际完工数量, 调用service原子更新(内部触发完工倒冲入库链路)
        BigDecimal completedQty = message.getCompletedQty() != null ? message.getCompletedQty() : BigDecimal.ZERO;
        BigDecimal scrappedQty = message.getScrappedQty() != null ? message.getScrappedQty() : BigDecimal.ZERO;
        workOrderService.complete(erpWoId, completedQty, scrappedQty);
        log.info("MES完工回写ERP工单完成: woNo={}, completedQty={}, scrapQty={}",
            wo.getWoNo(), completedQty, scrappedQty);
    }

    /**
     * 监听MES工单完工队列消息
     *
     * @param message     MES工单完工消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.ERP_WORKORDER_COMPLETED_QUEUE)
    public void onMessage(MesWorkOrderCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
