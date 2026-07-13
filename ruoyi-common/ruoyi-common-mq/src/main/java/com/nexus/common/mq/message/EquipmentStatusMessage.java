package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * MES设备状态变更消息
 * <p>
 * 设备状态变更时(NORMAL/MAINTENANCE/FAULT/SCRAPPED)发布到 MQ,
 * 供排产/工单/安灯等模块异步感知(如设备故障触发受影响工单标记异常待重排)。
 * <p>
 * 位于 ruoyi-common-mq 供跨模块共享, 暂无消费者(P1 排产联动时接入)。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentStatusMessage extends BaseMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long equipmentId;
    /** 设备编码 */
    private String equipmentCode;
    /** 设备名称 */
    private String equipmentName;
    /** 变更前状态 */
    private String oldStatus;
    /** 变更后状态 */
    private String newStatus;
    /** 变更原因 */
    private String reason;
}
