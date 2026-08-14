package com.nexus.erp.fin.gl.mq;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
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
 * <p>租户上下文由 BaseMessageConsumer 自动恢复。</p>
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

    /** 返回消息类型(GlPostingMessage) */
    @Override
    protected Class<GlPostingMessage> getMessageClass() {
        return GlPostingMessage.class;
    }

    /** 消费 GL 过账消息:按 sourceType 从 Registry 取 Poster 调用 post();继承 BaseMessageConsumer 的幂等/重试机制 */
    @Override
    protected void doProcess(GlPostingMessage message) {
        log.info("处理 GL 过账消息: source={}, sourceId={}", message.getSourceType(), message.getSourceId());
        doPost(message);
    }

    /**
     * 解析租户ID：使用 tenantIdStr 避免 Long 精度丢失（"000000" → 0 → "0" 不匹配数据库）
     */
    @Override
    protected String resolveTenantId(GlPostingMessage message) {
        String tid = message.getTenantIdStr();
        return tid != null && !tid.isEmpty() ? tid : "000000";
    }

    /** 执行过账:从 Registry 获取对应 AccountingPoster 并构建 PostingContext 调用 post() */
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
