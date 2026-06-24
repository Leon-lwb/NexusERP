package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.MesWorkOrderCompletedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

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

    /** ERP工单状态: 已完工 */
    private static final String WO_STATUS_COMPLETED = "COMPLETED";
    /** ERP工单状态: 已关闭 */
    private static final String WO_STATUS_CLOSED = "CLOSED";

    private final PrdWorkOrderMapper prdWorkOrderMapper;
    private final ReliableMessageProducer messageProducer;

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
        PrdWorkOrder wo = prdWorkOrderMapper.selectById(message.getErpWoId());
        if (wo == null) {
            log.warn("ERP工单不存在: erpWoId={}", message.getErpWoId());
            return;
        }
        // 幂等: 已完工/已关闭跳过
        if (WO_STATUS_COMPLETED.equals(wo.getWoStatus()) || WO_STATUS_CLOSED.equals(wo.getWoStatus())) {
            log.info("工单已完工/已关闭, 跳过回写: woNo={}, status={}", wo.getWoNo(), wo.getWoStatus());
            return;
        }

        // 更新工单状态 + 数量累加
        BigDecimal done = message.getCompletedQty() != null ? message.getCompletedQty() : BigDecimal.ZERO;
        wo.setWoStatus(WO_STATUS_COMPLETED);
        wo.setActualEndDate(new Date());
        wo.setCompletedQty(wo.getCompletedQty() != null ? wo.getCompletedQty().add(done) : done);
        if (message.getScrappedQty() != null) {
            wo.setScrapQty(wo.getScrapQty() != null ? wo.getScrapQty().add(message.getScrappedQty()) : message.getScrappedQty());
        }
        prdWorkOrderMapper.updateById(wo);
        log.info("MES完工回写ERP工单完成: woNo={}, completedQty={}, scrapQty={}",
            wo.getWoNo(), wo.getCompletedQty(), wo.getScrapQty());

        // 复用既有完工入库链路: 发 ProductionCompleteMessage -> ProductionBackflushConsumer(倒冲领料+成品入库)
        ProductionCompleteMessage pcm = new ProductionCompleteMessage();
        pcm.setWoId(wo.getWoId());
        pcm.setCompletionQty(done);
        pcm.setMaterialId(wo.getMaterialId());
        pcm.setMaterialCode(wo.getMaterialCode());
        pcm.setMaterialName(wo.getMaterialName());
        pcm.setWorkshopId(wo.getWorkshopId());
        // 携带租户ID，供下游消费者恢复租户上下文
        String tid = TenantHelper.getTenantId();
        if (tid != null) {
            pcm.setTenantId(Long.parseLong(tid));
        }
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH, pcm);
        log.info("触发完工倒冲入库链路: woNo={}", wo.getWoNo());
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
