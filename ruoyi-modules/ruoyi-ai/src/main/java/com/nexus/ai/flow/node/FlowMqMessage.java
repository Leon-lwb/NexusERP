package com.nexus.ai.flow.node;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点发出的通用 MQ 消息
 * <p>mq_send 节点使用：payload 为渲染后的 JSON 字符串，消费端按自身消息结构反序列化
 * （跨系统消息契约由流程配置方保证，节点仅负责可靠投递）</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowMqMessage extends BaseMessage {

    /** 业务负载（渲染后的 JSON 字符串） */
    private String payload;

    /** 来源流程实例ID */
    private Long flowInstanceId;

    /** 来源节点ID */
    private String flowNodeId;
}
