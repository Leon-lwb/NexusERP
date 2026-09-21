package com.nexus.iot.pipeline;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.iot.alert.domain.IotAlertCondition;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.alert.service.IIotAlertRuleService;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警检查器单元测试（IOT-ALERT-01 / IOT-ALERT-02）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>库内规则命中 → 触发并交记录服务落库（source=RULE）；</li>
 *   <li>持续时长未满足 → 不触发（持续时长/去抖）；</li>
 *   <li>库内无规则 → YAML 兜底默认（且属性名大小写不敏感）；</li>
 *   <li>未越限 → 不触发并调用自动恢复判定；</li>
 *   <li>比较符语义（触发与恢复共用同一判定函数）。</li>
 * </ul>
 * Redis 访问已收敛到 {@link IotAlertStateStore}，测试直接打桩该组件（无需静态打桩）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotAlertCheckerTest {

    private static final String TENANT = "000000";

    private IotPipelineProperties properties;
    private IIotAlertRuleService alertRuleService;
    private IIotAlertRecordService alertRecordService;
    private IotAlertStateStore alertStateStore;
    private IotAlertChecker checker;

    @BeforeEach
    void setUp() {
        properties = new IotPipelineProperties();
        alertRuleService = mock(IIotAlertRuleService.class);
        alertRecordService = mock(IIotAlertRecordService.class);
        alertStateStore = mock(IotAlertStateStore.class);
        checker = new IotAlertChecker(properties, alertRuleService, alertRecordService,
            mock(IIotDeviceService.class), alertStateStore);
    }

    private IotDeviceData data(String propertyCode, String value) {
        IotDeviceData d = new IotDeviceData();
        d.setTenantId(TENANT);
        d.setDeviceId(1L);
        d.setDeviceCode("DEV0001");
        d.setPropertyCode(propertyCode);
        d.setNumericValue(new BigDecimal(value));
        return d;
    }

    private IotAlertRule rule(Long ruleId, String operator, String threshold, int durationSec) {
        IotAlertRule r = new IotAlertRule();
        r.setRuleId(ruleId);
        r.setTenantId(TENANT);
        r.setRuleName("温度上限");
        r.setProductId(0L);
        r.setDeviceId(0L);
        r.setPropertyCode("temperature");
        r.setOperator(operator);
        r.setThreshold(new BigDecimal(threshold));
        r.setDurationSec(durationSec);
        r.setDebounceSec(60);
        r.setAlertType("TEMPERATURE");
        r.setAlertLevel("HIGH");
        r.setEnabled(IotAlertConstants.ENABLED_YES);
        return r;
    }

    /** 规则命中 → 触发并落库（source=RULE），且不再走自动恢复。 */
    @Test
    void check_ruleBreached_triggersAndPersists() {
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 0)));

        boolean fired = checker.check(data("temperature", "95"));

        assertTrue(fired);
        ArgumentCaptor<IotAlertRule> ruleCaptor = ArgumentCaptor.forClass(IotAlertRule.class);
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(ruleCaptor.capture(), any(), alertCaptor.capture(),
            eq(IotAlertConstants.SOURCE_RULE));
        assertEquals(3L, ruleCaptor.getValue().getRuleId());
        assertEquals(0, alertCaptor.getValue().getThreshold().compareTo(new BigDecimal("80")));
        assertEquals("HIGH", alertCaptor.getValue().getAlertLevel());
        assertEquals("TEMPERATURE", alertCaptor.getValue().getAlertType());
        verify(alertRecordService, never()).autoResolve(any());
    }

    /** 持续时长未到（计时键未命中 → 首次越限）→ 不触发、不落库。 */
    @Test
    void check_durationNotSatisfied_notTriggered() {
        when(alertStateStore.readDurationStart(anyString())).thenReturn(null);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 30)));

        boolean fired = checker.check(data("temperature", "95"));

        assertFalse(fired);
        verify(alertRecordService, never()).trigger(any(), any(), any(), any());
    }

    /** 持续越限达到 duration_sec → 触发。 */
    @Test
    void check_durationSatisfied_triggered() {
        when(alertStateStore.readDurationStart(anyString()))
            .thenReturn(String.valueOf(System.currentTimeMillis() - 31_000L));
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 30)));

        boolean fired = checker.check(data("temperature", "95"));

        assertTrue(fired);
        verify(alertRecordService).trigger(any(), any(), any(), eq(IotAlertConstants.SOURCE_RULE));
    }

    /** 库内无规则 → YAML 兜底默认（且属性名大小写不敏感）。 */
    @Test
    void check_noRule_yamlFallbackCaseInsensitive() {
        properties.getAlertThresholds().put("temperature", new BigDecimal("80"));
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any())).thenReturn(List.of());

        boolean fired = checker.check(data("Temperature", "95"));

        assertTrue(fired);
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(eq(null), any(), alertCaptor.capture(),
            eq(IotAlertConstants.SOURCE_DEFAULT));
        assertEquals(0, alertCaptor.getValue().getThreshold().compareTo(new BigDecimal("80")));
    }

    /** 未越限 → 不触发，但必须执行自动恢复判定（属性回到正常范围 → RESOLVED）。 */
    @Test
    void check_notBreached_callsAutoResolve() {
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 0)));

        boolean fired = checker.check(data("temperature", "50"));

        assertFalse(fired);
        verify(alertRecordService, never()).trigger(any(), any(), any(), any());
        verify(alertRecordService).autoResolve(any());
    }

    /** 比较符语义：触发与恢复共用同一函数，边界必须一致。 */
    @Test
    void condition_operatorSemantics() {
        assertTrue(IotAlertCondition.breached("GT", new BigDecimal("81"), new BigDecimal("80")));
        assertFalse(IotAlertCondition.breached("GT", new BigDecimal("80"), new BigDecimal("80")));
        assertTrue(IotAlertCondition.breached("GE", new BigDecimal("80"), new BigDecimal("80")));
        assertTrue(IotAlertCondition.breached("LE", new BigDecimal("80"), new BigDecimal("80")));
        assertFalse(IotAlertCondition.breached("LT", new BigDecimal("80"), new BigDecimal("80")));
        assertTrue(IotAlertCondition.breached("NE", new BigDecimal("81"), new BigDecimal("80")));
        // 非法/空比较符回落 GT
        assertTrue(IotAlertCondition.breached(null, new BigDecimal("81"), new BigDecimal("80")));
        assertFalse(IotAlertCondition.breached("XX", new BigDecimal("79"), new BigDecimal("80")));
        // 阈值缺失不判定为越限（避免规则未配阈值时误报）
        assertFalse(IotAlertCondition.breached("GT", new BigDecimal("99"), null));
    }

}
