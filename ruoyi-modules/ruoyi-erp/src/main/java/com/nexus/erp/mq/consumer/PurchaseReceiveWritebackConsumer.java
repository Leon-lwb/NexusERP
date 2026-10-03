package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/**
 * 强类型收货消息传输。每次投递都重新读取持久化收货/生命周期结果，即使 messageId 重复也如此：
 * BaseMessageConsumer 的 Redis DONE/PENDING 快捷路径不等于业务回执。
 * 此消费者不创建单据、不作为数量的权威来源、不执行库存过账，也不释放来源占用。
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class PurchaseReceiveWritebackConsumer extends BaseMessageConsumer<PurchaseReceiveWritebackMessage> {
    private final IPuPurchaseReceiveWritebackService purchaseReceiveWritebackService;

    @Override protected Class<PurchaseReceiveWritebackMessage> getMessageClass() {
        return PurchaseReceiveWritebackMessage.class;
    }

    @Override protected void doProcess(PurchaseReceiveWritebackMessage message) {
        String tenant=resolveTenantId(message);
        if(!positive(message.getReceiveId())||!TenantHelper.isEnable()||!tenant.equals(TenantHelper.getTenantId())) {
            throw new ServiceException("采购收货消息身份或当前租户不一致，拒绝回写");
        }
        // 由调度器在ERP独占可重复读事务中解析真实订单，而非通过未加锁的仓储 getById 查询。
        purchaseReceiveWritebackService.writeBackReceivedQty(message.getReceiveId(),null);
    }

    @Override protected String resolveTenantId(PurchaseReceiveWritebackMessage message) {
        if(message==null) throw new ServiceException("采购收货消息缺失");
        String tenant=message.getTenantIdStr();Long numeric=message.getTenantId();
        if(numeric!=null&&numeric<0) throw new ServiceException("采购收货消息租户标识无效");
        String encoded=numeric==null?null:String.format(java.util.Locale.ROOT,"%06d",numeric);
        if(tenant==null) tenant=encoded;
        // 不得裁剪租户值、默认使用系统租户，或让两个不一致的标签相互覆盖。
        if(!text(tenant,20)||!tenant.matches("[A-Za-z0-9_-]+")||(encoded!=null&&!tenant.equals(encoded))) {
            throw new ServiceException("采购收货消息租户缺失、格式无效或双标识冲突");
        }
        return tenant;
    }

    /** 保留基类的租户恢复逻辑，但有意绕过其可能丢失业务处理的 Redis ACK 快捷路径。 */
    @Override public void processMessage(PurchaseReceiveWritebackMessage message,Channel channel,Message amqpMessage) throws IOException {
        long tag=amqpMessage.getMessageProperties().getDeliveryTag();String tenant;
        if(message==null||!positive(message.getReceiveId())||!text(message.getMessageId(),128)) {
            channel.basicNack(tag,false,false);return;
        }
        try {tenant=resolveTenantId(message);}
        catch(RuntimeException malformed) {channel.basicNack(tag,false,false);return;}
        String retryKey="mq:retry:purchase-receipt:"+hash(tenant,message.getReceiveId().toString(),message.getMessageId());
        try {
            TenantHelper.dynamic(tenant,()->doProcess(message));
        } catch(RuntimeException unconfirmed) {
            // 提交失败时结果可能不确定。只能重试同一个真实来源；绝不另建 DOC 单据
            // 或对其执行补偿/释放。原始基础设施诊断信息不得泄露到 MQ 日志中。
            log.warn("采购收货回写未确认，保留持久来源等待重试: receiveId={}",message.getReceiveId());
            boolean requeue=true;
            try {
                long retries=RedisUtils.incrAtomicValue(retryKey);RedisUtils.expire(retryKey,Duration.ofHours(24));
                requeue=retries<3;
            } catch(RuntimeException retryStoreUnavailable) {
                log.warn("采购收货重试计数暂不可用，保留原消息");
            }
            channel.basicNack(tag,false,requeue);return;
        }
        // Redis 仅用于传输记账；业务提交后 Redis 故障不得使业务提交失效。
        try {RedisUtils.deleteObject(retryKey);}
        catch(RuntimeException retryStoreUnavailable) {log.warn("采购收货重试计数清理暂不可用");}
        channel.basicAck(tag,false);
    }

    @RabbitListener(queues=QueueConstants.PURCHASE_RECEIVE_QUEUE)
    public void onMessage(PurchaseReceiveWritebackMessage message,Channel channel,Message amqpMessage) throws Exception {
        processMessage(message,channel,amqpMessage);
    }
}
