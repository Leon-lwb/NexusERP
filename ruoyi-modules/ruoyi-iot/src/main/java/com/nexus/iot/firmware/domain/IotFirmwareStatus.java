package com.nexus.iot.firmware.domain;

import java.util.Set;

/**
 * IoT 固件发布状态机（OTA MVP）。
 * <p>
 * 语义：
 * <ul>
 *   <li>{@link #DRAFT}：草稿（刚登记，可编辑/删除，**不可下发**）；</li>
 *   <li>{@link #RELEASED}：已发布（可下发升级任务；版本号与固件包**冻结**，保证
 *       "设备装的是哪个包"可追溯）；</li>
 *   <li>{@link #DISABLED}：已停用（不再允许新建升级任务，但已下发任务的
 *       {@code iot_ota_task.target_version} 冗余字段仍可追溯；可重新发布）。</li>
 * </ul>
 * 合法流转：DRAFT → RELEASED、DRAFT → DISABLED（作废草稿）、RELEASED → DISABLED、
 * DISABLED → RELEASED。**不允许**回退到 DRAFT（避免"已发布的版本号被改写"）。
 *
 * @author nexus
 */
public final class IotFirmwareStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";
    /** 已发布 */
    public static final String RELEASED = "RELEASED";
    /** 已停用 */
    public static final String DISABLED = "DISABLED";

    /** 全部合法状态 */
    public static final Set<String> ALL = Set.of(DRAFT, RELEASED, DISABLED);

    /** 支持的校验算法 */
    public static final Set<String> CHECKSUM_TYPES = Set.of("MD5", "SHA256");

    private IotFirmwareStatus() {
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
     * 校验发布状态流转是否合法。
     *
     * @param from 当前状态（null 视为 DRAFT，兼容历史存量行）
     * @param to   目标状态
     * @return 允许流转返回 true
     */
    public static boolean canTransition(String from, String to) {
        if (!isKnown(to)) {
            return false;
        }
        String current = from == null ? DRAFT : from;
        if (current.equals(to)) {
            return false;
        }
        return switch (current) {
            case DRAFT -> RELEASED.equals(to) || DISABLED.equals(to);
            case RELEASED -> DISABLED.equals(to);
            case DISABLED -> RELEASED.equals(to);
            default -> false;
        };
    }

    /**
     * 是否可编辑（版本号/固件包/校验值）。
     * <p>仅草稿可编辑：已发布固件改包会让"设备升级到该版本"的含义失真。
     *
     * @param status 当前状态
     * @return 可编辑返回 true
     */
    public static boolean isEditable(String status) {
        return status == null || DRAFT.equals(status);
    }

    /**
     * 是否可用于下发升级任务（仅 RELEASED）。
     *
     * @param status 当前状态
     * @return 可下发返回 true
     */
    public static boolean isReleasable(String status) {
        return RELEASED.equals(status);
    }
}
