package com.nexus.iot.alert.domain;

/**
 * IoT 告警状态机常量与流转规则（IOT-ALERT-01）。
 * <p>
 * 合法流转（其余一律拒绝，服务端以 CAS 条件更新落地）：
 * <pre>
 *   ACTIVE --确认--&gt; ACKNOWLEDGED --恢复--&gt; RESOLVED
 *      |                                    ^
 *      +------------------恢复--------------+
 * </pre>
 * RESOLVED 为终态；同一设备+属性的异常再次出现时产生**新**记录（见
 * {@link IotAlertRecord} 的 {@code uk_active_alert} 幂等设计）。
 *
 * @author nexus
 */
public final class IotAlertStatus {

    /** 已触发未处理 */
    public static final String ACTIVE = "ACTIVE";

    /** 已确认、异常仍在持续 */
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    /** 已恢复（终态） */
    public static final String RESOLVED = "RESOLVED";

    private IotAlertStatus() {
    }

    /**
     * 是否允许确认：仅 ACTIVE 可确认。
     *
     * @param current 当前状态
     * @return 允许返回 true
     */
    public static boolean canAcknowledge(String current) {
        return ACTIVE.equals(current);
    }

    /**
     * 是否允许恢复：ACTIVE / ACKNOWLEDGED 可恢复。
     *
     * @param current 当前状态
     * @return 允许返回 true
     */
    public static boolean canResolve(String current) {
        return ACTIVE.equals(current) || ACKNOWLEDGED.equals(current);
    }

    /**
     * 是否为未恢复状态（即 {@code active_flag = 1}）。
     *
     * @param current 当前状态
     * @return 未恢复返回 true
     */
    public static boolean isUnresolved(String current) {
        return canResolve(current);
    }

}
