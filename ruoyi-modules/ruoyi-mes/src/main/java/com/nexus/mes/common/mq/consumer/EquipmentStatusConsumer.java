package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.EquipmentStatusMessage;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.service.IProAndonService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备状态变更消费者（MES侧, P0-5 收尾 / P1-6 设备-工单联动）
 * <p>
 * 消费 {@link EquipmentStatusMessage}, 当设备转为 FAULT 时:
 * <ol>
 *   <li>查询该设备进行中(RELEASED/IN_PROGRESS)的工单;</li>
 *   <li>若存在受影响工单, 触发 EQUIPMENT 类型安灯呼叫(工单异常提示), 每设备 1 小时限频 1 条防抖动刷屏;</li>
 *   <li>无受影响工单则仅记日志, 不建安灯。</li>
 * </ol>
 * 消费者线程无 Sa-Token 上下文, 租户ID 从消息恢复(TenantHelper.dynamic);
 * DB 访问通过 @DS("mes") 的 Service 方法切换数据源。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class EquipmentStatusConsumer extends BaseMessageConsumer<EquipmentStatusMessage> {

    /** 设备故障态 */
    private static final String STATUS_FAULT = "FAULT";
    /** 故障安灯限频 key 前缀: 每设备 1h 最多 1 条影响工单的安灯 */
    private static final String THROTTLE_PREFIX = "mes:equipment:fault:rl:";
    private static final Duration THROTTLE_TTL = Duration.ofHours(1);

    private final IEqEquipmentService equipmentService;
    private final IProAndonService andonService;

    @Override
    protected Class<EquipmentStatusMessage> getMessageClass() {
        return EquipmentStatusMessage.class;
    }

    @Override
    protected void doProcess(EquipmentStatusMessage message) {
        log.info("收到设备状态变更消息: equipmentId={}, {} -> {}, reason={}",
            message.getEquipmentId(), message.getOldStatus(), message.getNewStatus(), message.getReason());

        // 仅故障态需联动工单提示; 其他状态变更暂不处理
        if (!STATUS_FAULT.equals(message.getNewStatus())) {
            return;
        }

        // 消费者线程无登录态, 从消息恢复租户上下文(与 WorkOrderReleaseConsumer 一致)
        String tenantId = resolveTenantId(message.getTenantId());
        TenantHelper.dynamic(tenantId, () -> handleFault(message));
    }

    /**
     * 故障处理: 查受影响工单 -> 限频 -> 触发 EQUIPMENT 安灯
     */
    private void handleFault(EquipmentStatusMessage message) {
        Long equipmentId = message.getEquipmentId();
        List<ProWorkOrderVo> activeOrders = equipmentService.currentWorkOrders(equipmentId);
        if (activeOrders == null || activeOrders.isEmpty()) {
            log.info("设备故障无进行中工单受影响, 跳过安灯: equipmentId={}", equipmentId);
            return;
        }

        // 限频: 设备状态抖动(FAULT<->NORMAL)时避免重复安灯
        String throttleKey = THROTTLE_PREFIX + equipmentId;
        if (!RedisUtils.setObjectIfAbsent(throttleKey, "1", THROTTLE_TTL)) {
            log.debug("设备故障安灯限频中, 跳过: equipmentId={}", equipmentId);
            return;
        }

        String woNos = activeOrders.stream()
            .map(ProWorkOrderVo::getWoNo)
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.joining(","));
        String reason = String.format("设备[%s(%s)]故障, 影响 %d 个进行中工单: %s",
            message.getEquipmentName(), message.getEquipmentCode(), activeOrders.size(), woNos);
        try {
            andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
            log.info("设备故障已触发 EQUIPMENT 安灯(影响 {} 个工单): equipmentId={}", activeOrders.size(), equipmentId);
        } catch (Exception e) {
            log.warn("设备故障触发安灯失败: equipmentId={}, error={}", equipmentId, e.getMessage());
        }
    }

    /**
     * 租户ID 格式: BaseMessage.tenantId 为 Long, 0/null -> 默认 "000000", 否则 6 位零填充
     */
    private String resolveTenantId(Long tenantId) {
        if (tenantId == null || Long.valueOf(0L).equals(tenantId)) {
            return "000000";
        }
        return String.format("%06d", tenantId);
    }

    @RabbitListener(queues = QueueConstants.MES_EQUIPMENT_STATUS_QUEUE)
    /**
     * onMessage 操作
     * @param message message
     * @param channel channel
     * @param amqpMessage amqpMessage
     */
    public void onMessage(EquipmentStatusMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
