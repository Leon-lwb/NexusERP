package com.nexus.mes.eq.mq;

import com.nexus.common.mq.constant.QueueConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MES 备件 → ERP 仓储过账消息投递器（MES-EQ-04）
 * <p>
 * <b>为什么走 MQ 而不是直接调用 ERP 库存服务（方案②）</b>：
 * <ol>
 *   <li>{@code ruoyi-mes} 的 pom <b>不依赖</b> {@code ruoyi-erp}（MES 访问 ERP 数据一贯走
 *       {@code DynamicDataSourceContextHolder} + 只读 Mapper 或 MQ），直接注入 ERP 服务会引入
 *       模块环依赖；</li>
 *   <li>MES 侧事务绑定 {@code @DS("mes")} 连接，直接调用 ERP 写服务会跨数据源跨事务，无法保证
 *       「MES 台账 + ERP 库存」的一致性边界；</li>
 *   <li>MQ 天然给出重投语义，配合业务幂等键（{@code eq_spare_erp_sync.biz_key} +
 *       ERP 侧消费幂等）即可实现幂等与失败可见，且不扩大跨模块编译期耦合。</li>
 * </ol>
 * <p>
 * 队列由消费端（ERP 侧 {@code MesSpareErpConsumer}）用
 * {@code @RabbitListener(queuesToDeclare = @Queue(...))} 声明，因此这里发往<b>默认交换机</b>
 * （exchange 为空串，routingKey = 队列名），无需修改 {@code RabbitMqInitializer}（该文件不在本次改动所有权内）。
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EqSpareErpMqPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递备件过账消息（同步投递，失败抛异常由调用方记录为 FAILED，绝不静默）
     *
     * @param payload 消息体（Jackson 序列化的 Map，字段见 {@code MesSpareErpMessage}）
     */
    public void publish(Map<String, Object> payload) {
        rabbitTemplate.convertAndSend("", QueueConstants.MES_SPARE_ERP_QUEUE, payload);
        log.info("备件过账消息已投递: bizType={}, bizKey={}",
            payload.get("bizType"), payload.get("bizKey"));
    }
}
