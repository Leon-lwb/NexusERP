package com.nexus.iot.event.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT设备事件对象 iot_device_event
 * <p>
 * IOT-PIPE-02：设备主动上报的 {@code event/post}（故障/开关机/门禁/告警等）此前被静默丢弃，
 * 现落库并可查询、可联动告警。
 * <p>
 * 不继承 {@code TenantEntity}：本表仅含 {@code tenant_id + create_time}，
 * 无 create_by/create_dept/update_by/update_time 审计列（消费线程也无登录上下文），
 * 继承基类会触发不存在的列自动填充导致 INSERT 报错（与 {@code IotDeviceData} 同理）。
 * <p>
 * 幂等：唯一键 {@code uk_source_msg_id}（Redis Stream entry id），重投消息命中唯一键即视为已处理。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("iot_device_event")
public class IotDeviceEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    @TableId(value = "event_id")
    private Long eventId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(冗余)
     */
    private String deviceCode;

    /**
     * 产品ID(冗余)
     */
    private Long productId;

    /**
     * 事件编码
     */
    private String eventCode;

    /**
     * 事件名称(设备上报)
     */
    private String eventName;

    /**
     * 事件级别: INFO/WARN/ERROR/CRITICAL
     */
    private String eventLevel;

    /**
     * 事件发生时间(设备侧)
     */
    private Date eventTime;

    /**
     * 服务端接收时间
     */
    private Date receiveTime;

    /**
     * 事件原始载荷(JSON)
     */
    private String payload;

    /**
     * 数据质量: GOOD/BAD/UNCERTAIN
     */
    private String quality;

    /**
     * 处理状态: PENDING/PROCESSED/IGNORED/FAILED
     */
    private String processStatus;

    /**
     * 处理说明
     */
    private String processRemark;

    /**
     * 联动生成的告警ID(空=未联动)
     */
    private Long alertId;

    /**
     * 数据来源: MQTT/HTTP
     */
    private String source;

    /**
     * 来源消息ID(Redis Stream entry id,幂等去重键)
     */
    private String sourceMsgId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 判断是否为"告警类"事件（用于联动 IoT 告警，保持轻量、非完整规则引擎）。
     * <p>
     * 判定顺序：先看设备上报的 {@code eventLevel}（ERROR/CRITICAL/FATAL/ALARM/FAULT），
     * 再看事件编码是否含 ALARM/ALERT/FAULT/ERROR/FAIL/OVER 等语义片段。
     *
     * @param eventCode  事件编码
     * @param eventLevel 事件级别（可空）
     * @return 属告警类事件返回 true
     */
    public static boolean isAlarmEvent(String eventCode, String eventLevel) {
        if (eventLevel != null) {
            String lv = eventLevel.toUpperCase();
            if (lv.contains("ERROR") || lv.contains("CRITICAL") || lv.contains("FATAL")
                || lv.contains("ALARM") || lv.contains("FAULT")) {
                return true;
            }
        }
        if (eventCode == null) {
            return false;
        }
        String code = eventCode.toUpperCase();
        return code.contains("ALARM") || code.contains("ALERT") || code.contains("FAULT")
            || code.contains("ERROR") || code.contains("FAIL") || code.contains("OVER");
    }
}
