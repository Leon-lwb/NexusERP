package com.nexus.erp.fin.gl.mq;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.fin.gl.poster.AccountingPoster;
import com.nexus.erp.fin.gl.poster.AccountingPosterRegistry;
import com.nexus.erp.fin.gl.poster.PostingContext;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * GL 过账消费者:业务单据事件 -> 路由到对应 AccountingPoster 制单。
 * <p>租户上下文由消息体 tenantIdStr 恢复(参考 PurchaseReceiveWritebackConsumer)。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class GlPostingConsumer extends BaseMessageConsumer<GlPostingMessage> {

    private final AccountingPosterRegistry registry;

    @Override
    protected Class<GlPostingMessage> getMessageClass() {
        return GlPostingMessage.class;
    }

    @Override
    protected void doProcess(GlPostingMessage message) {
        log.info("处理 GL 过账消息: source={}, sourceId={}", message.getSourceType(), message.getSourceId());
        String tenantId = message.getTenantIdStr();
        if (tenantId != null) {
            TenantHelper.dynamic(tenantId, () -> doPost(message));
        } else {
            log.warn("GL 过账消息租户ID为空,无租户上下文执行: source={}, sourceId={}",
                message.getSourceType(), message.getSourceId());
            doPost(message);
        }
    }

    private void doPost(GlPostingMessage message) {
        AccountingPoster poster = registry.get(message.getSourceType());
        PostingContext ctx = PostingContext.builder()
            .sourceType(message.getSourceType())
            .sourceId(message.getSourceId())
            .sourceNo(message.getSourceNo())
            .voucherDate(message.getVoucherDate())
            .build();
        poster.post(ctx);
    }

    /**
     * 监听 GL 过账队列
     */
    @RabbitListener(queues = com.nexus.common.mq.constant.QueueConstants.GL_POSTING_QUEUE)
    public void onMessage(GlPostingMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
