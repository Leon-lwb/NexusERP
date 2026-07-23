package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.OqcAutoCreateMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.service.IQcOqcService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 ERP 销售出库审批通过消息，自动创建 MES OQC 出货检验单。
 * <p>
 * 幂等：同一 (soId, materialId) 已创建 OQC 则跳过（查 qc_oqc.so_id + material_id）。
 * <p>
 * 数据源注意：幂等查询与新增均经 {@link IQcOqcService} 调用，走类级 {@code @DS("mes")}
 * 切换到 ry-mes 库（qc_oqc 表所在库）。切勿直调 QcOqcMapper--消费者线程无 {@code @DS}
 * 上下文，直调 Mapper 会落到默认 master 库（ry-vue）报 Table doesn't exist。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OqcAutoCreateConsumer extends BaseMessageConsumer<OqcAutoCreateMessage> {

    private final IQcOqcService oqcService;

    @Override
    protected Class<OqcAutoCreateMessage> getMessageClass() {
        return OqcAutoCreateMessage.class;
    }

    @Override
    protected void doProcess(OqcAutoCreateMessage message) {
        log.info("收到销售出库审批通过消息: soId={}, materialCode={}, quantity={}",
            message.getSoId(), message.getMaterialCode(), message.getQuantity());

        // 消费者线程无登录态，从消息恢复租户上下文（租户ID格式: Long 0 -> "000000"）
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doCreateOqc(message));
    }

    /**
     * 幂等创建 OQC：同 (soId, materialId) 已存在则跳过。
     * <p>
     * 幂等查询与新增都走 {@link IQcOqcService}（类级 @DS("mes")），确保落到 ry-mes 库。
     */
    private void doCreateOqc(OqcAutoCreateMessage message) {
        if (message.getSoId() == null || message.getMaterialId() == null) {
            log.warn("[OqcAutoCreate] 缺少 soId/materialId，跳过: soId={}, materialId={}",
                message.getSoId(), message.getMaterialId());
            return;
        }
        if (oqcService.existsBySoAndMaterial(message.getSoId(), message.getMaterialId())) {
            log.info("[OqcAutoCreate] 销售订单 {} 物料 {} 已存在 OQC，跳过自动创建",
                message.getSoId(), message.getMaterialId());
            return;
        }
        try {
            QcOqcBo bo = new QcOqcBo();
            bo.setSoId(message.getSoId());
            bo.setMaterialId(message.getMaterialId());
            bo.setMaterialCode(message.getMaterialCode());
            bo.setMaterialName(message.getMaterialName());
            bo.setQuantity(message.getQuantity());
            // oqcNo / status / inspectResult 由 QcOqcServiceImpl.insertByBo 内部生成
            oqcService.insertByBo(bo);
            log.info("[OqcAutoCreate] 销售订单 {} 物料 {} 已自动创建 OQC",
                message.getSoId(), message.getMaterialId());
        } catch (Exception e) {
            log.error("[OqcAutoCreate] 销售订单 {} 物料 {} 自动创建 OQC 失败: {}",
                message.getSoId(), message.getMaterialId(), e.getMessage(), e);
            throw new RuntimeException("OQC 自动创建失败", e);
        }
    }

    @RabbitListener(queues = QueueConstants.MES_OQC_CREATE_QUEUE)
    public void onMessage(OqcAutoCreateMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
