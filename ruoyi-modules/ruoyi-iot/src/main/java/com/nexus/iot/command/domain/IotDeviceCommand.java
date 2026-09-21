package com.nexus.iot.command.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * IoT下行指令对象 iot_device_command（IOT-PIPE-02 下行通道 / §4#8）。
 * <p>
 * 状态机：PENDING（已登记未下发）→ SENT（已下发待回执）→ SUCCESS/FAILED（设备回执）；
 * 发布失败直接落 FAILED 并写明原因（例如 Broker 未启用），保证"每次下发都有留痕"。
 * <p>
 * 幂等：唯一键 {@code uk_ack_msg_id}（回执对应的 Stream entry id），同一回执重复到达不会二次改写。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device_command")
public class IotDeviceCommand extends TenantEntity {

    /**
     * 指令ID
     */
    @TableId(value = "command_id")
    private Long commandId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(冗余)
     */
    private String deviceCode;

    /**
     * 指令类型: PROPERTY_SET/SERVICE_INVOKE
     */
    private String commandType;

    /**
     * 下发Topic
     */
    private String topic;

    /**
     * 下发载荷(JSON)
     */
    private String payload;

    /**
     * 状态: PENDING/SENT/SUCCESS/FAILED
     */
    private String status;

    /**
     * 重发次数
     */
    private Integer retryCount;

    /**
     * 下发时间
     */
    private Date sentTime;

    /**
     * 设备回执时间
     */
    private Date ackTime;

    /**
     * 设备回执载荷
     */
    private String ackPayload;

    /**
     * 回执消息ID(幂等去重键)
     */
    private String ackMsgId;

    /**
     * 下发/回执结果说明
     */
    private String resultMessage;

    /**
     * 下发人
     */
    private Long operatorId;

    /**
     * 下发人名称
     */
    private String operatorName;
}
