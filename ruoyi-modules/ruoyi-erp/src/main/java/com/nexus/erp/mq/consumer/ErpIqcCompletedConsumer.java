package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * IQC检验完成消费者（ERP侧）
 * <p>
 * 消费MES发出的IQC检验完成消息，按物料行回写ERP到货通知行(wm_arrival_notice_line)的质检结果：
 * iqc_id / iqc_code / iqc_status(QUALIFIED|UNQUALIFIED)。
 * <p>
 * 收货单头仅回写 iqc_id/iqc_code(展示用)，不再强改收货单状态——上架门禁由
 * {@code WmMaterialReceiveCrudServiceImpl.confirmShelf} 按 iqc_status 校验，
 * 避免多需检料时单头状态在 QUALIFIED/REJECTED 间反复跳变。
 * <p>
 * 边界: 仅回写质检结果，不实现不良品入库/退货流程。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ErpIqcCompletedConsumer extends BaseMessageConsumer<IqcCompletedMessage> {

    private final WmMaterialReceiveMapper materialReceiveMapper;
    private final WmArrivalNoticeLineMapper arrivalNoticeLineMapper;

    /**
     * 获取消息类型
     *
     * @return IqcCompletedMessage类型
     */
    @Override
    protected Class<IqcCompletedMessage> getMessageClass() {
        return IqcCompletedMessage.class;
    }

    /**
     * 处理IQC检验完成消息，按物料行回写ERP质检结果
     *
     * @param message IQC检验完成消息
     */
    @Override
    protected void doProcess(IqcCompletedMessage message) {
        // MQ消费者线程无登录态，需从消息恢复租户上下文，否则多租户拦截器加tenant_id条件查不到数据
        // 租户ID格式: 默认'000000'(6位零填充)，Long 0 -> "000000"
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doUpdate(message));
    }

    /**
     * 按物料行回写IQC结果：
     * <ol>
     *   <li>收货单头 iqc_id/iqc_code（展示用）</li>
     *   <li>到货通知行 iqc_id/iqc_code/iqc_status（confirmShelf 门禁依据）</li>
     * </ol>
     *
     * @param message IQC检验完成消息
     */
    private void doUpdate(IqcCompletedMessage message) {
        WmMaterialReceive receive = materialReceiveMapper.selectById(message.getReceiveId());
        if (receive == null) {
            log.warn("收货单不存在: receiveId={}", message.getReceiveId());
            return;
        }

        // 1. 收货单头回写 iqc_id/iqc_code（展示用）
        receive.setIqcId(message.getIqcId());
        receive.setIqcCode(message.getIqcCode());
        materialReceiveMapper.updateById(receive);

        // 2. 按物料行回写 wm_arrival_notice_line: iqc_id/iqc_code/iqc_status（门禁依据）
        if (receive.getNoticeId() != null && message.getMaterialId() != null) {
            WmArrivalNoticeLine lineUpdate = new WmArrivalNoticeLine();
            lineUpdate.setIqcId(message.getIqcId());
            lineUpdate.setIqcCode(message.getIqcCode());
            lineUpdate.setIqcStatus(message.getInspectResult());
            int rows = arrivalNoticeLineMapper.update(lineUpdate, new LambdaQueryWrapper<WmArrivalNoticeLine>()
                .eq(WmArrivalNoticeLine::getNoticeId, receive.getNoticeId())
                .eq(WmArrivalNoticeLine::getMaterialId, message.getMaterialId()));
            log.info("IQC按行回写: receiveId={}, materialId={}, result={}, 影响行数={}",
                message.getReceiveId(), message.getMaterialId(), message.getInspectResult(), rows);
        } else {
            log.warn("IQC回写缺少 noticeId/materialId，跳过按行回写: receiveId={}, materialId={}",
                message.getReceiveId(), message.getMaterialId());
        }
        log.info("IQC回写收货单完成: receiveId={}, iqcCode={}, result={}",
            message.getReceiveId(), message.getIqcCode(), message.getInspectResult());
    }

    /**
     * 监听IQC检验完成队列消息
     *
     * @param message     IQC检验完成消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.ERP_IQC_COMPLETED_QUEUE)
    public void onMessage(IqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
