package com.nexus.iot.pipeline;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.iot.alert.domain.IotAlertConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IoT 告警通知器。
 * <p>
 * 职责已收窄为"投递"：告警的持久化、幂等合并、去抖抑制均由
 * {@code IIotAlertRecordService} 负责（IOT-ALERT-01/02），本类只做：
 * <ol>
 *   <li>SSE 推送：扫描 Sa-Token 在线 Token，按租户过滤后推送 {@code type=IOT_ALERT}；</li>
 *   <li>MES 安灯联动（{@link IMesAndonService#createAndonFromAlert}）；</li>
 *   <li>逐渠道返回投递结果（成功/失败/跳过 + 目标），供调用方写投递留痕。</li>
 * </ol>
 * <b>无同租户在线用户时不再"告警到此为止"</b>：告警已落库，这里只返回
 * {@code SKIPPED}，投递留痕同样落库，事后可查、可确认、可恢复。
 * <p>
 * 原"每 (deviceId, alertType) 每分钟限频"已移除——限频语义由规则上的
 * {@code debounce_sec}（去抖窗口）承担，避免两层限频叠加导致 MES 安灯被误抑制。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAlertNotifier {

    /** MES 安灯服务，告警触发设备安灯呼叫（未接入时由 Noop 兜底）。 */
    private final IMesAndonService mesAndonService;

    /**
     * 投递告警（SSE + MES 安灯），返回逐渠道结果。
     *
     * @param alert 告警信息
     * @return 逐渠道投递结果（顺序：SSE、MES_ANDON）
     */
    public List<ChannelOutcome> notify(IotAlert alert) {
        log.warn("IoT 告警：device={}, type={}, level={}, msg={}",
            alert.getDeviceCode(), alert.getAlertType(), alert.getAlertLevel(), alert.getAlertMessage());
        List<ChannelOutcome> outcomes = new ArrayList<>(2);
        outcomes.add(notifySse(alert));
        outcomes.add(notifyAndon(alert));
        return outcomes;
    }

    /**
     * SSE 推送：无在线用户/未启用时返回 SKIPPED（告警已落库，不再丢失）。
     */
    private ChannelOutcome notifySse(IotAlert alert) {
        if (!SseMessageUtils.isEnable()) {
            log.debug("SSE 未启用，告警已落库并留痕：device={}", alert.getDeviceCode());
            return ChannelOutcome.skipped(IotAlertConstants.CHANNEL_SSE, "SSE 未启用，告警已落库待查");
        }
        Set<Long> targetUserIds = collectOnlineUsersByTenant(alert.getTenantId());
        if (CollUtil.isEmpty(targetUserIds)) {
            log.info("无同租户在线用户，告警已落库并留痕（可事后查询/确认/恢复）：device={}, tenant={}",
                alert.getDeviceCode(), alert.getTenantId());
            return ChannelOutcome.skipped(IotAlertConstants.CHANNEL_SSE, "无同租户在线用户，告警已落库待查");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "IOT_ALERT");
        payload.put("deviceId", alert.getDeviceId());
        payload.put("deviceCode", alert.getDeviceCode());
        payload.put("alertType", alert.getAlertType());
        payload.put("alertLevel", alert.getAlertLevel());
        payload.put("alertMessage", alert.getAlertMessage());
        payload.put("value", alert.getValue());
        payload.put("threshold", alert.getThreshold());

        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(targetUserIds));
        dto.setMessage(JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
        log.info("IoT 告警已推送：device={}, targetUsers={}", alert.getDeviceCode(), targetUserIds.size());
        return ChannelOutcome.success(IotAlertConstants.CHANNEL_SSE, CollUtil.join(targetUserIds, ","),
            "已推送给 " + targetUserIds.size() + " 个在线用户");
    }

    /**
     * MES 安灯联动：失败仅记录结果，不阻断其它渠道。
     */
    private ChannelOutcome notifyAndon(IotAlert alert) {
        try {
            Long andonId = mesAndonService.createAndonFromAlert(alert);
            if (andonId == null) {
                return ChannelOutcome.skipped(IotAlertConstants.CHANNEL_MES_ANDON, "MES 未接入或无安灯实现");
            }
            log.info("IoT 告警已联动 MES 安灯：device={}, andonId={}", alert.getDeviceCode(), andonId);
            return ChannelOutcome.success(IotAlertConstants.CHANNEL_MES_ANDON, String.valueOf(andonId), "已联动 MES 安灯");
        } catch (Exception e) {
            log.warn("IoT 告警联动 MES 安灯失败：device={}, err={}", alert.getDeviceCode(), e.getMessage());
            return ChannelOutcome.failed(IotAlertConstants.CHANNEL_MES_ANDON, "联动失败：" + e.getMessage());
        }
    }

    /**
     * 扫描 Sa-Token 在线 Token，过滤指定租户的在线用户ID集合。
     */
    private Set<Long> collectOnlineUsersByTenant(String tenantId) {
        Set<Long> userIds = new LinkedHashSet<>();
        List<String> keys;
        try {
            keys = StpUtil.searchTokenValue("", 0, -1, false);
        } catch (Exception e) {
            log.warn("查询在线 Token 失败，跳过 SSE 推送：{}", e.getMessage());
            return userIds;
        }
        if (CollUtil.isEmpty(keys)) {
            return userIds;
        }
        for (String key : keys) {
            int idx = key.lastIndexOf(':');
            String token = idx >= 0 ? key.substring(idx + 1) : key;
            try {
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                if (loginUser == null || loginUser.getUserId() == null) {
                    continue;
                }
                if (tenantId == null || tenantId.equals(loginUser.getTenantId())) {
                    userIds.add(loginUser.getUserId());
                }
            } catch (Exception ignore) {
                // 单个 token 解析失败不影响整体
            }
        }
        return userIds;
    }

    /**
     * 单渠道投递结果。
     *
     * @param channel 渠道: SSE/MES_ANDON
     * @param status  结果: SUCCESS/FAILED/SKIPPED
     * @param target  目标（在线用户ID列表/安灯ID），无目标为 null
     * @param message 结果说明
     */
    public record ChannelOutcome(String channel, String status, String target, String message) {

        /**
         * 投递成功。
         */
        public static ChannelOutcome success(String channel, String target, String message) {
            return new ChannelOutcome(channel, IotAlertConstants.NOTIFY_SUCCESS, target, message);
        }

        /**
         * 投递失败。
         */
        public static ChannelOutcome failed(String channel, String message) {
            return new ChannelOutcome(channel, IotAlertConstants.NOTIFY_FAILED, null, message);
        }

        /**
         * 跳过（未启用/无目标/被去抖抑制）。
         */
        public static ChannelOutcome skipped(String channel, String message) {
            return new ChannelOutcome(channel, IotAlertConstants.NOTIFY_SKIPPED, null, message);
        }
    }

}
