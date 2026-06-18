package com.nexus.iot.pipeline;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IoT 告警通知器。
 * <p>
 * 命中阈值后：
 * <ol>
 *   <li>Redis 限频：每 (deviceId, alertType) 每分钟最多推送一次，避免高频上报刷屏与在线用户扫描过频；</li>
 *   <li>扫描 Sa-Token 在线 Token，过滤同租户在线用户作为推送目标（多租户隔离）；</li>
 *   <li>无在线用户 → 仅落日志；有 → {@code SseMessageUtils.publishMessage} 推送 {@code type=IOT_ALERT} 消息。</li>
 * </ol>
 * MES 安灯联动（{@link IMesAndonService#createAndonFromAlert}）：限频通过后触发 EQUIPMENT 类型安灯呼叫，
 * 失败仅告警日志不阻断主流程。
 * <p>
 * 注意：在线 Token 全量扫描在用户量大时较重，故前置限频；规则引擎阶段应改为订阅式推送。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAlertNotifier {

    private static final String RATE_LIMIT_PREFIX = "iot:alert:rl:";
    private static final Duration RATE_LIMIT_TTL = Duration.ofSeconds(60);

    private final IMesAndonService mesAndonService;

    /**
     * 推送告警（带限频与多租户隔离）。
     *
     * @param alert 告警信息
     */
    public void notify(IotAlert alert) {
        log.warn("IoT 告警：device={}, type={}, level={}, msg={}",
            alert.getDeviceCode(), alert.getAlertType(), alert.getAlertLevel(), alert.getAlertMessage());

        // 1. 限频：每设备每告警类型每分钟最多推送一次
        String rlKey = RATE_LIMIT_PREFIX + alert.getDeviceId() + ":" + alert.getAlertType();
        if (!RedisUtils.setObjectIfAbsent(rlKey, "1", RATE_LIMIT_TTL)) {
            log.debug("告警推送限频中，跳过：device={}, type={}", alert.getDeviceCode(), alert.getAlertType());
            return;
        }

        // 2. MES 安灯联动：限频通过后触发安灯呼叫(EQUIPMENT 类型)。
        //    MES 侧 MesAndonServiceImpl(@Primary) 提供真实实现；未接入时由 Noop 兜底返回 null。
        //    try-catch 隔离，安灯创建失败不阻断 IoT 告警主流程。
        try {
            Long andonId = mesAndonService.createAndonFromAlert(alert);
            if (andonId != null) {
                log.info("IoT 告警已联动 MES 安灯：device={}, andonId={}", alert.getDeviceCode(), andonId);
            }
        } catch (Exception e) {
            log.warn("IoT 告警联动 MES 安灯失败，不阻断告警推送：device={}, err={}", alert.getDeviceCode(), e.getMessage());
        }

        if (!SseMessageUtils.isEnable()) {
            log.debug("SSE 未启用，告警仅落日志：device={}", alert.getDeviceCode());
            return;
        }

        // 2. 解析同租户在线用户
        Set<Long> targetUserIds = collectOnlineUsersByTenant(alert.getTenantId());
        if (CollUtil.isEmpty(targetUserIds)) {
            log.info("无同租户在线用户，告警仅落日志：device={}, tenant={}", alert.getDeviceCode(), alert.getTenantId());
            return;
        }

        // 3. SSE 推送
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
            log.warn("查询在线 Token 失败，跳过告警推送：{}", e.getMessage());
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

}
