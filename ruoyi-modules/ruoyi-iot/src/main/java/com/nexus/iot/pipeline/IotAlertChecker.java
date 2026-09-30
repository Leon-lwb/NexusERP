package com.nexus.iot.pipeline;

import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertCondition;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.alert.service.IIotAlertRuleService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * IoT 告警检查器（规则引擎 + 落库，IOT-ALERT-01/02）。
 * <p>
 * 与第一版的区别：
 * <ol>
 *   <li><b>规则来自库</b>：{@code iot_alert_rule}（租户隔离、产品/设备范围、比较符、持续时长、去抖、级别）；
 *       {@code nexus.iot.pipeline.alert-thresholds}（YAML）**仅在库内无匹配规则时兜底**，
 *       优先级：设备规则 &gt; 产品规则 &gt; 全局规则 &gt; YAML 默认；</li>
 *   <li><b>持续时长</b>：首次越限起在 Redis 计时（键见 {@link IotAlertStateStore#durationKey}），
 *       持续达到 {@code duration_sec} 才真正触发，抑制单点瞬时尖峰误报；</li>
 *   <li><b>落库</b>：触发即交 {@link IIotAlertRecordService#trigger} 持久化（同一未恢复告警合并更新），
 *       不再依赖在线用户，离线期间告警不会丢失；</li>
 *   <li><b>恢复</b>：未越限时调用 {@link IIotAlertRecordService#autoResolve}，
 *       属性值回到正常范围即把未恢复告警置 RESOLVED，之后可再次触发产生新记录。</li>
 * </ol>
 * {@link #check} 仍返回"本次是否触发阈值告警"，供 {@code IotStreamConsumer#afterPersist}
 * 对同一数据点的 EWMA ANOMALY 告警做互斥抑制。
 * <p>
 * Redis 访问统一走 {@link IotAlertStateStore}（缓存故障时降级为"立即触发 + 不去抖"，不阻断数据管道）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAlertChecker {

    /** 设备→产品 缓存时长（避免每个数据点都查设备表） */
    private static final Duration DEVICE_PRODUCT_TTL = Duration.ofMinutes(10);
    /** 持续时长计时键额外 TTL（秒），保证持续越限期间计时键不过期 */
    private static final long DURATION_KEY_EXTRA_SEC = 60L;
    /** YAML 兜底默认对应的规则键 */
    private static final String DEFAULT_RULE_KEY = "default";

    /** 管道配置属性，提供 YAML 兜底告警阈值。 */
    private final IotPipelineProperties properties;
    /** 告警规则服务（库内规则，含租户/范围匹配）。 */
    private final IIotAlertRuleService alertRuleService;
    /** 告警记录服务（落库 + 去抖通知 + 投递留痕）。 */
    private final IIotAlertRecordService alertRecordService;
    /** 在同租户内查询 IoT 设备，用于产品规则匹配和 MES 关联。 */
    private final IIotDeviceService deviceService;
    /** 告警运行态存储（持续时长计时 / 设备→产品缓存）。 */
    private final IotAlertStateStore alertStateStore;

    /**
     * 检查单条设备数据是否触发告警，并完成落库/恢复判定。
     *
     * @param data 已落库的设备数据
     * @return true 表示该数据点触发了阈值告警（已落库）
     */
    public boolean check(IotDeviceData data) {
        if (data == null || data.getNumericValue() == null || StringUtils.isBlank(data.getPropertyCode())) {
            return false;
        }
        boolean fired = false;
        try {
            fired = evaluateAndTrigger(data);
        } catch (Exception e) {
            log.warn("告警规则判定/落库异常：device={}, property={}", data.getDeviceCode(), data.getPropertyCode(), e);
        }
        if (!fired) {
            try {
                alertRecordService.autoResolve(data);
            } catch (Exception e) {
                log.warn("告警自动恢复判定异常：device={}, property={}", data.getDeviceCode(), data.getPropertyCode(), e);
            }
        }
        return fired;
    }

    /**
     * 规则匹配 + 越限判定 + 持续时长 + 落库。
     *
     * @param data 设备数据
     * @return 是否触发
     */
    private boolean evaluateAndTrigger(IotDeviceData data) {
        BigDecimal value = data.getNumericValue();
        Long productId = resolveProductId(data);
        List<IotAlertRule> rules = alertRuleService.selectEnabledRules(
            data.getTenantId(), data.getDeviceId(), productId, data.getPropertyCode());
        if (!rules.isEmpty()) {
            /** 规则已按具体度排序；只判定最具体的一条，正常值不得下探低优先级规则或 YAML。 */
            IotAlertRule rule = rules.get(0);
            if (!IotAlertCondition.breached(rule.getOperator(), value, rule.getThreshold())) {
                return false;
            }
            if (!durationSatisfied(rule, data)) {
                return false;
            }
            alertRecordService.trigger(rule, data, buildAlert(rule, data), IotAlertConstants.SOURCE_RULE);
            return true;
        }
        /** 库内无匹配规则 → YAML 兜底默认 */
        BigDecimal threshold = resolveDefaultThreshold(data.getPropertyCode());
        if (threshold == null || value.compareTo(threshold) <= 0) {
            clearDurationKey(data, DEFAULT_RULE_KEY);
            return false;
        }
        alertRecordService.trigger(null, data, buildDefaultAlert(data, threshold), IotAlertConstants.SOURCE_DEFAULT);
        return true;
    }

    /**
     * 持续时长判定：首次越限开始计时，达到 {@code duration_sec} 才返回 true。
     * <p>
     * 计时键 TTL 为 {@code duration_sec + 60s}，并在持续越限期间不断刷新，
     * 避免"计时键先过期、重新计时"导致长时间越限反而永不触发。
     *
     * @param rule 规则
     * @param data 设备数据
     * @return 达到持续时长返回 true（{@code duration_sec<=0} 直接返回 true）
     */
    private boolean durationSatisfied(IotAlertRule rule, IotDeviceData data) {
        int durationSec = rule.getDurationSec() == null ? 0 : rule.getDurationSec();
        if (durationSec <= 0) {
            return true;
        }
        String ruleKey = rule.getRuleId() == null ? DEFAULT_RULE_KEY : String.valueOf(rule.getRuleId());
        String key = IotAlertStateStore.durationKey(
            data.getTenantId(), data.getDeviceId(), data.getPropertyCode(), ruleKey);
        Duration ttl = Duration.ofSeconds(durationSec + DURATION_KEY_EXTRA_SEC);
        long now = System.currentTimeMillis();
        String first = alertStateStore.readDurationStart(key);
        if (StringUtils.isBlank(first)) {
            alertStateStore.writeDurationStart(key, String.valueOf(now), ttl);
            log.debug("属性首次越限，开始计时：device={}, property={}, durationSec={}",
                data.getDeviceCode(), data.getPropertyCode(), durationSec);
            return false;
        }
        long firstMs;
        try {
            firstMs = Long.parseLong(first);
        } catch (NumberFormatException e) {
            firstMs = now;
        }
        if (now - firstMs >= durationSec * 1000L) {
            return true;
        }
        alertStateStore.writeDurationStart(key, String.valueOf(firstMs), ttl);
        return false;
    }

    /**
     * 清理持续时长计时键（未越限时调用，使下次越限重新计时）。
     */
    private void clearDurationKey(IotDeviceData data, String ruleKey) {
        alertStateStore.clearDuration(IotAlertStateStore.durationKey(
            data.getTenantId(), data.getDeviceId(), data.getPropertyCode(), ruleKey));
    }

    /**
     * 解析设备所属产品ID（产品级规则匹配需要），带 10 分钟缓存，避免每个数据点查库。
     *
     * @param data 设备数据
     * @return 产品ID，无法解析返回 null（此时仅设备级/全局规则参与匹配）
     */
    private Long resolveProductId(IotDeviceData data) {
        if (data.getDeviceId() == null || StringUtils.isBlank(data.getTenantId())
            || StringUtils.isBlank(data.getDeviceCode())) {
            return null;
        }
        /** 纳入可信数据记录的设备 ID：复用设备编码时，不得继承其他设备的产品关联。 */
        String key = IotAlertStateStore.deviceProductKey(data.getTenantId(), data.getDeviceCode())
            + ":" + data.getDeviceId();
        Long cached = alertStateStore.readDeviceProduct(key);
        if (cached != null) {
            return cached;
        }
        IotDevice device = resolveTrustedDevice(data);
        if (device == null) {
            return null;
        }
        Long productId = device.getProductId() == null ? 0L : device.getProductId();
        alertStateStore.writeDeviceProduct(key, productId, DEVICE_PRODUCT_TTL);
        return productId;
    }

    /** 当前 MES 关联只可取自同租户内身份匹配的设备记录，绝不取自 MQTT 字段或缓存。 */
    private Long resolveMesEquipmentId(IotDeviceData data) {
        IotDevice device = resolveTrustedDevice(data);
        if (device == null || device.getMesEquipmentId() == null || device.getMesEquipmentId() <= 0) {
            return null;
        }
        return device.getMesEquipmentId();
    }

    private IotDevice resolveTrustedDevice(IotDeviceData data) {
        if (data.getDeviceId() == null || StringUtils.isBlank(data.getTenantId())
            || StringUtils.isBlank(data.getDeviceCode())) {
            return null;
        }
        try {
            IotDevice device = TenantHelper.dynamic(data.getTenantId(),
                () -> deviceService.selectEntityByCode(data.getDeviceCode()));
            if (device == null || !data.getDeviceId().equals(device.getDeviceId())
                || !data.getTenantId().equals(device.getTenantId())
                || !data.getDeviceCode().equals(device.getDeviceCode())) {
                return null;
            }
            return device;
        } catch (Exception e) {
            log.debug("Device lookup failed for alert: device={}", data.getDeviceCode(), e);
            return null;
        }
    }

    /**
     * YAML 兜底阈值查找（大小写不敏感，修正原实现"首字母大写属性永不触发"的问题）。
     *
     * @param propertyCode 属性编码
     * @return 阈值，未配置返回 null
     */
    private BigDecimal resolveDefaultThreshold(String propertyCode) {
        Map<String, BigDecimal> thresholds = properties.getAlertThresholds();
        if (thresholds == null || thresholds.isEmpty() || propertyCode == null) {
            return null;
        }
        BigDecimal exact = thresholds.get(propertyCode);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, BigDecimal> entry : thresholds.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(propertyCode)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 按库内规则构造告警内容。
     */
    private IotAlert buildAlert(IotAlertRule rule, IotDeviceData data) {
        IotAlert alert = new IotAlert();
        alert.setDeviceId(data.getDeviceId());
        alert.setDeviceCode(data.getDeviceCode());
        alert.setMesEquipmentId(resolveMesEquipmentId(data));
        alert.setAlertType(StringUtils.isNotBlank(rule.getAlertType())
            ? rule.getAlertType() : resolveAlertType(data.getPropertyCode()));
        alert.setAlertLevel(StringUtils.isNotBlank(rule.getAlertLevel()) ? rule.getAlertLevel() : "MEDIUM");
        alert.setAlertMessage(String.format("设备 %s 属性 %s 越限：当前 %s %s 阈值 %s（规则：%s）",
            data.getDeviceCode(), data.getPropertyCode(), data.getNumericValue(),
            operatorSymbol(rule.getOperator()), rule.getThreshold(), rule.getRuleName()));
        alert.setValue(data.getNumericValue());
        alert.setThreshold(rule.getThreshold());
        alert.setTenantId(data.getTenantId());
        return alert;
    }

    /**
     * 按 YAML 兜底阈值构造告警内容（保持与原实现一致的 GT 语义与 HIGH 级别）。
     */
    private IotAlert buildDefaultAlert(IotDeviceData data, BigDecimal threshold) {
        IotAlert alert = new IotAlert();
        alert.setDeviceId(data.getDeviceId());
        alert.setDeviceCode(data.getDeviceCode());
        alert.setMesEquipmentId(resolveMesEquipmentId(data));
        alert.setAlertType(resolveAlertType(data.getPropertyCode()));
        alert.setAlertLevel("HIGH");
        alert.setAlertMessage(String.format("设备 %s 属性 %s 超阈值：当前 %s > 阈值 %s（来源：YAML 兜底默认）",
            data.getDeviceCode(), data.getPropertyCode(), data.getNumericValue(), threshold));
        alert.setValue(data.getNumericValue());
        alert.setThreshold(threshold);
        alert.setTenantId(data.getTenantId());
        return alert;
    }

    /**
     * 按属性编码推断告警类型：含 temp→温度、vib→振动、press→压力，否则归为 OTHER。
     * <p>仅在规则未显式指定 alert_type 时作为兜底（原第一版的唯一来源）。
     *
     * @param propertyCode 属性编码
     * @return 告警类型枚举字符串
     */
    private String resolveAlertType(String propertyCode) {
        String lower = propertyCode == null ? "" : propertyCode.toLowerCase();
        if (lower.contains("temp")) {
            return "TEMPERATURE";
        }
        if (lower.contains("vib")) {
            return "VIBRATION";
        }
        if (lower.contains("press")) {
            return "PRESSURE";
        }
        return "OTHER";
    }

    /**
     * 比较符的展示符号。
     */
    private String operatorSymbol(String operator) {
        return switch (IotAlertCondition.normalizeOperator(operator)) {
            case IotAlertCondition.GE -> ">=";
            case IotAlertCondition.LT -> "<";
            case IotAlertCondition.LE -> "<=";
            case IotAlertCondition.EQ -> "=";
            case IotAlertCondition.NE -> "!=";
            default -> ">";
        };
    }

}
