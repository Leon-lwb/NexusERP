package com.nexus.mes.common.mq;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.mes.common.mq.message.MesWorkOrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * MES模块MQ消息发送服务
 * <p>
 * 使用 @Async 在独立线程中发送MQ消息，脱离 @DS("mes") 的数据源上下文。
 * 因为 mq_message_record 表位于 ry-vue(master) 库而非 ry-mes 业务库，
 * 异步线程中手动 push("master") 确保消息记录写入正确库。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MesMqSendService {

    private final ReliableMessageProducer messageProducer;

    /**
     * 异步发送MES工单完工消息到ERP
     *
     * @param mesWoId      MES工单ID
     * @param erpWoId      ERP工单ID
     * @param woNo         工单编号
     * @param completedQty 完工数量
     * @param scrappedQty  报废数量
     * @param tenantId     租户ID
     */
    @Async
    public void sendWorkOrderCompleted(Long mesWoId, Long erpWoId, String woNo,
                                       BigDecimal completedQty,
                                       BigDecimal scrappedQty,
                                       String tenantId) {
        log.info("异步发送MES工单完工消息: mesWoId={}, erpWoId={}, woNo={}", mesWoId, erpWoId, woNo);
        MesWorkOrderCompletedMessage msg = new MesWorkOrderCompletedMessage();
        msg.setMesWoId(mesWoId);
        msg.setErpWoId(erpWoId);
        msg.setWoNo(woNo);
        msg.setCompletedQty(completedQty);
        msg.setScrappedQty(scrappedQty);
        // 设置租户ID，供ERP消费端恢复租户上下文
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.DIRECT_EXCHANGE,
                RoutingKeyConstants.ERP_WORKORDER_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
