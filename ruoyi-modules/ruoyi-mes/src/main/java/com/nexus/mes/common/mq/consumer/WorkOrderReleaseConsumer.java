package com.nexus.mes.common.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工单下达消费者（MES侧）
 * <p>
 * 消费ERP审批通过的工单下达消息，在MES中创建工单记录。
 * Week 17 仅实现消息接收和日志打印，Week 19 实现完整的工单创建逻辑。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("mes")
public class WorkOrderReleaseConsumer extends BaseMessageConsumer<WorkOrderReleaseMessage> {

    private final IMesErpBridgeService mesErpBridgeService;

    @Override
    protected Class<WorkOrderReleaseMessage> getMessageClass() {
        return WorkOrderReleaseMessage.class;
    }

    @Override
    protected void doProcess(WorkOrderReleaseMessage message) {
        log.info("收到工单下达消息: woId={}, woNo={}, materialCode={}, plannedQty={}",
            message.getWoId(), message.getWoNo(), message.getMaterialCode(), message.getPlannedQty());

        // Week 17: 验证桥接服务可用性，打印工艺路线信息
        // Week 19 将实现完整的MES工单创建逻辑
        Long routingId = message.getRoutingId();
        if (routingId == null) {
            // ERP消息未携带routingId，通过桥接服务查询默认工艺路线
            routingId = mesErpBridgeService.getDefaultRoutingId(message.getMaterialId());
            log.info("通过桥接服务查询默认工艺路线: materialId={}, routingId={}", message.getMaterialId(), routingId);
        }

        if (routingId != null) {
            List<Map<String, Object>> steps = mesErpBridgeService.getRoutingSteps(routingId);
            log.info("工艺路线步骤数: routingId={}, steps={}", routingId, steps.size());
        } else {
            log.warn("未找到物料对应的工艺路线: materialId={}, woNo={}", message.getMaterialId(), message.getWoNo());
        }

        log.info("Week 17 占位处理完成，Week 19 将实现完整工单创建逻辑: woNo={}", message.getWoNo());
    }

    @RabbitListener(queues = QueueConstants.MES_WORKORDER_QUEUE)
    public void onMessage(WorkOrderReleaseMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
