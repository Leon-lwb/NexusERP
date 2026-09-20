package com.nexus.iot.alert.domain;

/**
 * IoT 告警来源 / 通知渠道 / 投递结果常量。
 *
 * @author nexus
 */
public final class IotAlertConstants {

    /** 来源：命中 iot_alert_rule 规则 */
    public static final String SOURCE_RULE = "RULE";
    /** 来源：库内无规则时的 YAML 兜底默认阈值 */
    public static final String SOURCE_DEFAULT = "DEFAULT";
    /** 来源：EWMA 统计异常检测 */
    public static final String SOURCE_ANOMALY = "ANOMALY";
    /** 来源：设备事件上报（event/post）中的告警类事件（IOT-PIPE-02 事件→告警联动） */
    public static final String SOURCE_EVENT = "EVENT";

    /** 渠道：SSE 实时推送 */
    public static final String CHANNEL_SSE = "SSE";
    /** 渠道：MES 安灯 */
    public static final String CHANNEL_MES_ANDON = "MES_ANDON";

    /** 投递结果：成功 */
    public static final String NOTIFY_SUCCESS = "SUCCESS";
    /** 投递结果：失败 */
    public static final String NOTIFY_FAILED = "FAILED";
    /** 投递结果：被去抖抑制/无目标 */
    public static final String NOTIFY_SKIPPED = "SKIPPED";

    /** 启用 */
    public static final String ENABLED_YES = "1";
    /** 停用 */
    public static final String ENABLED_NO = "0";

    private IotAlertConstants() {
    }

}
