package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.OqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * OQC检验完成消费者（ERP侧）
 * <p>
 * 消费MES发出的OQC检验完成消息，回写ERP销售订单行(so_sales_order_line)OQC状态：
 * QUALIFIED -> oqcStatus=QUALIFIED; UNQUALIFIED -> oqcStatus=UNQUALIFIED。
 * 按 soId + materialId 定位行(排除已取消行)。
 * <p>
 * 边界: 仅回写 OQC 状态字段，不实现发货拦截；发货硬门禁由 SoSalesOrderHeaderServiceImpl.fulfill() 负责。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ErpOqcCompletedConsumer extends BaseMessageConsumer<OqcCompletedMessage> {

    /** 销售订单行状态: 已取消 */
    private static final String LINE_STATUS_CANCELLED = "CANCELLED";
    /** OQC检验状态: 合格 */
    private static final String OQC_QUALIFIED = "QUALIFIED";
    /** OQC检验状态: 不合格 */
    private static final String OQC_UNQUALIFIED = "UNQUALIFIED";

    private final SoSalesOrderLineMapper salesOrderLineMapper;

    /**
     * 获取消息类型
     *
     * @return OqcCompletedMessage类型
     */
    @Override
    protected Class<OqcCompletedMessage> getMessageClass() {
        return OqcCompletedMessage.class;
    }

    /**
     * 处理OQC检验完成消息，更新ERP销售订单行OQC状态
     *
     * @param message OQC检验完成消息
     */
    @Override
    protected void doProcess(OqcCompletedMessage message) {
        // MQ消费者线程无登录态，需从消息恢复租户上下文，否则多租户拦截器加tenant_id条件查不到数据
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doUpdate(message));
    }

    /**
     * 执行销售订单行OQC状态回写
     *
     * @param message OQC检验完成消息
     */
    private void doUpdate(OqcCompletedMessage message) {
        // 按 soId + materialId 定位行(排除已取消行)
        List<SoSalesOrderLine> lines = salesOrderLineMapper.selectList(Wrappers.<SoSalesOrderLine>lambdaQuery()
            .eq(SoSalesOrderLine::getSoId, message.getSoId())
            .eq(SoSalesOrderLine::getMaterialId, message.getMaterialId())
            .ne(SoSalesOrderLine::getLineStatus, LINE_STATUS_CANCELLED));
        if (lines.isEmpty()) {
            log.warn("未找到匹配的销售订单行: soId={}, materialId={}", message.getSoId(), message.getMaterialId());
            return;
        }

        String oqcStatus = OQC_QUALIFIED.equals(message.getInspectResult()) ? OQC_QUALIFIED : OQC_UNQUALIFIED;
        int rows = salesOrderLineMapper.update(null, Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .set(SoSalesOrderLine::getOqcStatus, oqcStatus)
            .set(SoSalesOrderLine::getOqcId, message.getOqcId())
            .set(SoSalesOrderLine::getOqcCode, message.getOqcCode())
            .set(SoSalesOrderLine::getOqcTime, new Date())
            .eq(SoSalesOrderLine::getSoId, message.getSoId())
            .eq(SoSalesOrderLine::getMaterialId, message.getMaterialId())
            .ne(SoSalesOrderLine::getLineStatus, LINE_STATUS_CANCELLED));
        log.info("OQC回写销售订单行完成: soId={}, materialId={}, oqcCode={}, result={}, affectedRows={}",
            message.getSoId(), message.getMaterialId(), message.getOqcCode(), oqcStatus, rows);
    }

    /**
     * 监听OQC检验完成队列消息
     *
     * @param message     OQC检验完成消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.ERP_OQC_COMPLETED_QUEUE)
    public void onMessage(OqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
