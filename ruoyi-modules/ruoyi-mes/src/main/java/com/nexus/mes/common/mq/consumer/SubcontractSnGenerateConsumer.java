package com.nexus.mes.common.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.SubcontractSnGenerateMessage;
import com.nexus.mes.pro.service.IProSnService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 委外SN生成消费者 (PCBA 委外闭环, ERP 发料完成触发)
 * <p>
 * 收到 SubcontractSnGenerateMessage 后按委外单批量生成 SN
 * (source=SUBCONTRACT, 冗余委外单号直达追溯), 租户上下文由
 * BaseMessageConsumer 按消息 tenantId 自动恢复。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("mes")
public class SubcontractSnGenerateConsumer extends BaseMessageConsumer<SubcontractSnGenerateMessage> {

    private final IProSnService proSnService;

    @Override
    protected Class<SubcontractSnGenerateMessage> getMessageClass() {
        return SubcontractSnGenerateMessage.class;
    }

    @Override
    protected void doProcess(SubcontractSnGenerateMessage message) {
        log.info("收到委外SN生成消息: subcontractId={}, subcontractNo={}, quantity={}",
            message.getSubcontractId(), message.getSubcontractNo(), message.getQuantity());
        final int quantity;
        try { quantity=message.getQuantity()==null?0:message.getQuantity().intValueExact(); }
        catch(ArithmeticException error) {throw new com.nexus.common.core.exception.ServiceException("委外SN数量必须为32位正整数，不能截断或溢出");}
        if(quantity<=0) throw new com.nexus.common.core.exception.ServiceException("委外SN数量必须为正整数，不能将无效消息当作已完成");
        String commandId="MES:SUBCONTRACT:"+message.getSubcontractId();
        if(message.getMessageId()!=null && !message.getMessageId().isBlank()) commandId+=":"+message.getMessageId();
        proSnService.generateSnForSubcontract(commandId,message.getSubcontractId(),message.getSubcontractNo(),
            message.getMaterialId(),message.getMaterialCode(),message.getMaterialName(),quantity);
    }

    /**
     * RabbitMQ 监听入口
     */
    @RabbitListener(queues = QueueConstants.MES_SUBCONTRACT_SN_QUEUE)
    public void onMessage(SubcontractSnGenerateMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
