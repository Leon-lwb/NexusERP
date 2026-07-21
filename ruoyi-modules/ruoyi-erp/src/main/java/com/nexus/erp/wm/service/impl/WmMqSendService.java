package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 仓储模块MQ消息发送服务
 * <p>
 * 使用 @Async 在独立线程中发送MQ消息，彻底脱离 @DS("erp") 的数据源上下文。
 * 因为 mq_message_record 表位于 ry-vue 库而非 ry-erp 业务库，
 * 如果在 @DS("erp") 的调用链中发送消息，messageRecordMapper.insert() 会使用 erp 数据源导致表不存在。
 * <p>
 * 异步线程没有 @DS 注解上下文，push("master") 可以正确生效。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class WmMqSendService {

    /** 消息提供器 */
    private final ReliableMessageProducer messageProducer;

    /**
     * 异步发送采购收货回写消息
     * <p>
     * 使用 @Async 在新线程中执行，避免 @DS("erp") 上下文干扰。
     * 新线程中手动切换到 master 数据源，确保 mq_message_record 记录写入 ry-vue 库。
     * </p>
     *
     * @param receiveId 入库单ID
     * @param tenantId  租户ID（需在请求线程中提前获取，异步线程中无法获取）
     */
    @Async
    public void sendPurchaseReceiveWriteback(Long receiveId, String tenantId) {
        log.info("异步发送采购收货回写消息: receiveId={}, tenantId={}", receiveId, tenantId);
        PurchaseReceiveWritebackMessage writebackMsg = new PurchaseReceiveWritebackMessage();
        writebackMsg.setReceiveId(receiveId);
        writebackMsg.setSourceModule("wm");
        // 携带租户ID，消费者需要据此设置租户上下文
        // 使用 String 字段传递，避免 Long 类型转换丢失前导零（"000000" → 0 → "0"）
        writebackMsg.setTenantIdStr(tenantId);
        // 异步线程无@DS上下文，push("master")可正确生效
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.PURCHASE_RECEIVE_WRITEBACK, writebackMsg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
