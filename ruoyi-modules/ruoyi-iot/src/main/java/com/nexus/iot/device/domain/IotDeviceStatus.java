package com.nexus.iot.device.domain;

import java.util.Set;

/**
 * IoT 设备生命周期状态（IOT-DEV-01 / §4#7 功能缺失清单）。
 * <p>
 * 语义：
 * <ul>
 *   <li>{@link #ACTIVE}：正常在用，允许 MQTT 接入（{@code IotMqttAuthenticator} 仅放行该状态）；</li>
 *   <li>{@link #INACTIVE}：已禁用（停用/检修/暂时下线），MQTT 认证被拒，数据不可上报；</li>
 *   <li>{@link #DECOMMISSIONED}：已注销（退役，终态），MQTT 认证被拒，且不允许再回到在用状态。</li>
 * </ul>
 * 注销采用"状态置位"而非物理/逻辑删除：设备历史数据（{@code iot_device_data}）与
 * 告警/事件记录仍可按 device_id 追溯，符合制造现场的可追溯要求。
 *
 * @author nexus
 */
public final class IotDeviceStatus {

    /** 正常在用 */
    public static final String ACTIVE = "ACTIVE";
    /** 已禁用 */
    public static final String INACTIVE = "INACTIVE";
    /** 已注销（终态） */
    public static final String DECOMMISSIONED = "DECOMMISSIONED";

    /** 全部合法状态 */
    public static final Set<String> ALL = Set.of(ACTIVE, INACTIVE, DECOMMISSIONED);

    private IotDeviceStatus() {
    }

    /**
     * 是否为合法状态值。
     *
     * @param status 状态值
     * @return 合法返回 true
     */
    public static boolean isKnown(String status) {
        return status != null && ALL.contains(status);
    }

    /**
     * 是否允许 MQTT 接入（认证/授权共用口径）。
     *
     * @param status 设备状态
     * @return 仅 ACTIVE 允许接入
     */
    public static boolean canConnect(String status) {
        return ACTIVE.equals(status);
    }

    /**
     * 校验状态流转是否合法。
     * <p>
     * 允许：ACTIVE ⇄ INACTIVE、ACTIVE/INACTIVE → DECOMMISSIONED；
     * 拒绝：目标状态非法、流转到自身（无变化）、以及从 DECOMMISSIONED 复活（注销为终态）。
     *
     * @param from 当前状态（null 视为 ACTIVE，兼容历史存量行）
     * @param to   目标状态
     * @return 允许流转返回 true
     */
    public static boolean canTransition(String from, String to) {
        if (!isKnown(to)) {
            return false;
        }
        String current = from == null ? ACTIVE : from;
        if (current.equals(to)) {
            return false;
        }
        // 注销为终态：不允许从 DECOMMISSIONED 回到 ACTIVE/INACTIVE
        return !DECOMMISSIONED.equals(current);
    }
}
