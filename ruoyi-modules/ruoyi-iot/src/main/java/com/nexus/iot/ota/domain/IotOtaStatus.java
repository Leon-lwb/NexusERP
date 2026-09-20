package com.nexus.iot.ota.domain;

import java.util.Set;

/**
 * IoT OTA 升级任务状态机。
 * <p>
 * <pre>
 *   PENDING ──下发成功──▶ SENT ──设备回执进度──▶ UPGRADING ──回执结果──▶ SUCCESS
 *      │                    │                        │                  FAILED
 *      │                    │                        └──超时兜底──▶ TIMEOUT
 *      └──下发失败──▶ FAILED └──人工取消──▶ CANCELLED
 * </pre>
 * <ul>
 *   <li>{@link #PENDING}：已登记、指令尚未确认下发；</li>
 *   <li>{@link #SENT}：指令已提交 MQTT Broker，等待设备回执；</li>
 *   <li>{@link #UPGRADING}：设备已开始下载/刷写；</li>
 *   <li>{@link #SUCCESS}/{@link #FAILED}：设备回执的最终结果；</li>
 *   <li>{@link #TIMEOUT}：{@code SENT}/{@code UPGRADING} 超过超时窗口未回执，由
 *       {@code IotOtaTimeoutTask} 兜底置位；</li>
 *   <li>{@link #CANCELLED}：人工取消（审计状态机未列，但"取消"端点必须有终态承载，
 *       否则无法与"设备升级失败"区分，故显式新增并在此说明）。</li>
 * </ul>
 * 终态（SUCCESS/FAILED/TIMEOUT/CANCELLED）**不可再流转**：重复回执由 CAS 拒绝，
 * 保证"回执幂等"与"结果不可被设备反复改写"。
 *
 * @author nexus
 */
public final class IotOtaStatus {

    /** 已登记未下发 */
    public static final String PENDING = "PENDING";
    /** 已下发待回执 */
    public static final String SENT = "SENT";
    /** 设备升级中 */
    public static final String UPGRADING = "UPGRADING";
    /** 升级成功 */
    public static final String SUCCESS = "SUCCESS";
    /** 升级失败 */
    public static final String FAILED = "FAILED";
    /** 超时未回执 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 人工取消 */
    public static final String CANCELLED = "CANCELLED";

    /** 全部合法状态 */
    public static final Set<String> ALL =
        Set.of(PENDING, SENT, UPGRADING, SUCCESS, FAILED, TIMEOUT, CANCELLED);

    /** 进行中（未到终态）状态：同设备+同固件不允许并存两个 */
    public static final Set<String> ACTIVE = Set.of(PENDING, SENT, UPGRADING);

    /** 终态：不可再流转 */
    public static final Set<String> TERMINAL = Set.of(SUCCESS, FAILED, TIMEOUT, CANCELLED);

    /** 设备回执允许声明的目标状态（PENDING/SENT/TIMEOUT/CANCELLED 不允许由设备声明） */
    public static final Set<String> REPORT_TARGETS = Set.of(UPGRADING, SUCCESS, FAILED);

    private IotOtaStatus() {
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
     * 是否为终态。
     *
     * @param status 状态值
     * @return 终态返回 true
     */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL.contains(status);
    }

    /**
     * 是否为进行中状态。
     *
     * @param status 状态值
     * @return 进行中返回 true
     */
    public static boolean isActive(String status) {
        return status != null && ACTIVE.contains(status);
    }

    /**
     * 校验状态流转是否合法（设备回执 / 系统流转共用口径）。
     *
     * @param from 当前状态（null 视为 PENDING，兼容历史存量行）
     * @param to   目标状态
     * @return 允许流转返回 true
     */
    public static boolean canTransition(String from, String to) {
        if (!isKnown(to)) {
            return false;
        }
        String current = from == null ? PENDING : from;
        if (current.equals(to)) {
            return false;
        }
        return switch (current) {
            case PENDING -> SENT.equals(to) || FAILED.equals(to) || CANCELLED.equals(to);
            case SENT -> UPGRADING.equals(to) || SUCCESS.equals(to) || FAILED.equals(to)
                || TIMEOUT.equals(to) || CANCELLED.equals(to);
            case UPGRADING -> SUCCESS.equals(to) || FAILED.equals(to)
                || TIMEOUT.equals(to) || CANCELLED.equals(to);
            // SUCCESS/FAILED/TIMEOUT/CANCELLED：终态，不再流转
            default -> false;
        };
    }

    /**
     * 校验"设备回执"是否被接受。
     * <p>与 {@link #canTransition} 的差异：允许 {@code UPGRADING → UPGRADING}
     * （设备分批上报进度时状态不变，只推进 progress），其余口径一致 ——
     * 终态一律拒绝，且设备不得自行声明 PENDING/SENT/TIMEOUT/CANCELLED。
     *
     * @param from 当前状态
     * @param to   回执声明的目标状态（UPGRADING/SUCCESS/FAILED）
     * @return 接受返回 true
     */
    public static boolean canReport(String from, String to) {
        if (to == null || !REPORT_TARGETS.contains(to)) {
            return false;
        }
        if (UPGRADING.equals(to) && (SENT.equals(from) || UPGRADING.equals(from))) {
            return true;
        }
        return canTransition(from, to);
    }

    /**
     * 是否允许人工重试（仅失败/超时可重试；已成功、已取消、进行中不可）。
     *
     * @param status 当前状态
     * @return 可重试返回 true
     */
    public static boolean canRetry(String status) {
        return FAILED.equals(status) || TIMEOUT.equals(status);
    }

    /**
     * 是否允许人工取消（仅进行中状态可取消）。
     *
     * @param status 当前状态
     * @return 可取消返回 true
     */
    public static boolean canCancel(String status) {
        return isActive(status);
    }
}
