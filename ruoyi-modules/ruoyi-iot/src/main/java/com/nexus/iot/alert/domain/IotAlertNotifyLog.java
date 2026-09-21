package com.nexus.iot.alert.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * IoT告警通知投递留痕对象 iot_alert_notify_log
 * <p>
 * 每次告警推送（SSE / MES 安灯）成功、失败或被去抖抑制都写一条，
 * 用于回答"告警到底有没有发出去、发给了谁、为什么没发"。
 * 告警本身已由 {@link IotAlertRecord} 持久化，本表只记录投递过程，不承担可靠性兜底。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_alert_notify_log")
public class IotAlertNotifyLog extends TenantEntity {

    /**
     * 日志ID
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 告警ID
     */
    private Long alertId;

    /**
     * 通知渠道: SSE/MES_ANDON
     */
    private String channel;

    /**
     * 投递目标(在线用户ID列表/安灯ID)
     */
    private String target;

    /**
     * 投递结果: SUCCESS/FAILED/SKIPPED
     */
    private String status;

    /**
     * 结果说明
     */
    private String message;

}
