package com.nexus.iot.pipeline;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.iot.alert.domain.IotAlertCondition;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.alert.service.IIotAlertRuleService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private IIotDeviceService deviceService;
    private IotAlertChecker checker;

    @BeforeEach
    void setUp() {
        properties = new IotPipelineProperties();
        alertRuleService = mock(IIotAlertRuleService.class);
        alertRecordService = mock(IIotAlertRecordService.class);
        alertStateStore = mock(IotAlertStateStore.class);
        deviceService = mock(IIotDeviceService.class);
        checker = new IotAlertChecker(properties, alertRuleService, alertRecordService,
            deviceService, alertStateStore);
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

    /** 规则驱动的告警必须把可信 IoT 设备的 MES 关联传递给通知器。 */
    @Test
    void check_ruleBreached_includesTrustedMesEquipmentId() {
        IotDeviceData reading = data("temperature", "95");
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(linkedDevice());
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 0)));

        assertTrue(checker.check(reading));
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(any(), eq(reading), alertCaptor.capture(), eq(IotAlertConstants.SOURCE_RULE));
        assertEquals(1L, alertCaptor.getValue().getMesEquipmentId());
        assertEquals(TENANT, alertCaptor.getValue().getTenantId());
        assertEquals(reading.getDeviceId(), alertCaptor.getValue().getDeviceId());
        verify(deviceService).selectEntityByCode("DEV0001");
    }

    /** 即使 productId 已缓存，YAML 兜底也携带同一个可信 MES 关联。 */
    @Test
    void check_yamlFallback_includesTrustedMesEquipmentId() {
        IotDeviceData reading = data("temperature", "95");
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(linkedDevice());
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any())).thenReturn(List.of());
        properties.getAlertThresholds().put("temperature", new BigDecimal("80"));

        assertTrue(checker.check(reading));
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(eq(null), eq(reading), alertCaptor.capture(),
            eq(IotAlertConstants.SOURCE_DEFAULT));
        assertEquals(1L, alertCaptor.getValue().getMesEquipmentId());
        assertEquals(TENANT, alertCaptor.getValue().getTenantId());
        verify(deviceService).selectEntityByCode("DEV0001");
    }

    /** 没有 MES 关联时，告警保持未关联；缓存的产品 ID 不是设备 ID。 */
    @Test
    void check_noMesLink_keepsAlertUnlinked() {
        IotDevice device = linkedDevice();
        device.setMesEquipmentId(null);
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(device);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 0)));

        assertTrue(checker.check(data("temperature", "95")));
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(any(), any(), alertCaptor.capture(), eq(IotAlertConstants.SOURCE_RULE));
        assertNull(alertCaptor.getValue().getMesEquipmentId());
    }

    /** IoT 设备查询只能为租户、设备 ID 和设备编码均匹配的记录补充信息。 */
    @ParameterizedTest
    @ValueSource(strings = {"tenant", "id", "code"})
    void check_wrongDeviceIdentity_neverAttachesMesLink(String mismatch) {
        IotDevice device = linkedDevice();
        switch (mismatch) {
            case "tenant" -> device.setTenantId("999999");
            case "id" -> device.setDeviceId(99L);
            case "code" -> device.setDeviceCode("OTHER");
            default -> throw new IllegalArgumentException(mismatch);
        }
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(device);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any())).thenReturn(List.of());
        properties.getAlertThresholds().put("temperature", new BigDecimal("80"));

        assertTrue(checker.check(data("temperature", "95")));
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(eq(null), any(), alertCaptor.capture(),
            eq(IotAlertConstants.SOURCE_DEFAULT));
        assertNull(alertCaptor.getValue().getMesEquipmentId());
    }

    /** MES 关联查询属于尽力而为的补充；设备查询出错时，仍委托记录服务持久化。 */
    @Test
    void check_deviceLookupFails_stillTriggersRecord() {
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(deviceService.selectEntityByCode("DEV0001")).thenThrow(new IllegalStateException("lookup unavailable"));
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(rule(3L, "GT", "80", 0)));

        assertTrue(checker.check(data("temperature", "95")));
        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(any(), any(), alertCaptor.capture(), eq(IotAlertConstants.SOURCE_RULE));
        assertNull(alertCaptor.getValue().getMesEquipmentId());
    }

    /** 未匹配到已持久化设备的查询结果不得污染产品规则选择或缓存。 */
    @Test
    void check_wrongDeviceIdentity_doesNotCacheProductId() {
        IotDevice other = linkedDevice();
        other.setDeviceId(99L);
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(null);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(other);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any())).thenReturn(List.of());

        assertFalse(checker.check(data("temperature", "95")));
        verify(alertRuleService).selectEnabledRules(eq(TENANT), eq(1L), isNull(), eq("temperature"));
        verify(alertStateStore, never()).writeDeviceProduct(anyString(), any(), any());
    }

    /** 产品缓存不得将仅以编码为键的条目复用于不同的设备 ID 或租户。 */
    @Test
    void check_productCacheKey_separatesTenantAndDeviceId() {
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any())).thenReturn(List.of());
        IotDeviceData first = data("temperature", "50");
        IotDeviceData anotherId = data("temperature", "50");
        anotherId.setDeviceId(2L);
        IotDeviceData anotherTenant = data("temperature", "50");
        anotherTenant.setTenantId("999999");

        assertFalse(checker.check(first));
        assertFalse(checker.check(anotherId));
        assertFalse(checker.check(anotherTenant));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(alertStateStore, times(3)).readDeviceProduct(keys.capture());
        assertEquals(3L, keys.getAllValues().stream().distinct().count());
    }

    private IotDevice linkedDevice() {
        IotDevice device = new IotDevice();
        device.setTenantId(TENANT);
        device.setDeviceId(1L);
        device.setDeviceCode("DEV0001");
        device.setProductId(1L);
        device.setMesEquipmentId(1L);
        return device;
    }

    /** 产品专属温度规则优先于全局阈值；正常读数不得产生误报或回落到 YAML。 */
    @ParameterizedTest
    @ValueSource(strings = {"187.2", "187.3"})
    void check_productRuleNotBreached_doesNotFallThroughToGlobalOrYaml(String value) {
        IotDeviceData reading = data("temperature", value);
        reading.setDeviceCode("DEV0025");
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        IotAlertRule product = rule(8701L, "GT", "220", 0);
        product.setProductId(1L);
        IotAlertRule global = rule(7001L, "GT", "80", 0);
        when(alertRuleService.selectEnabledRules(eq(TENANT), eq(reading.getDeviceId()), eq(1L), eq("temperature")))
            .thenReturn(List.of(product, global));
        properties.getAlertThresholds().put("temperature", new BigDecimal("80"));

        assertFalse(checker.check(reading), "产品正常温度不能由低优先级全局规则触发");
        verify(alertRecordService, never()).trigger(any(), any(), any(), any());
        verify(alertRecordService).autoResolve(reading);
    }

    /** 设备级规则适用时，即使不越限，也不应下探产品级规则。 */
    @Test
    void check_deviceRuleNotBreached_doesNotFallThroughToProduct() {
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        IotAlertRule device = rule(8702L, "GT", "230", 0);
        device.setDeviceId(1L);
        IotAlertRule product = rule(8701L, "GT", "220", 0);
        product.setProductId(1L);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(device, product));

        IotDeviceData reading = data("temperature", "225");
        assertFalse(checker.check(reading));
        verify(alertRecordService, never()).trigger(any(), any(), any(), any());
        verify(alertRecordService).autoResolve(reading);
    }

    /** 已有专属规则但未越限时，YAML 不能越级触发。 */
    @Test
    void check_productRuleNotBreached_doesNotUseYamlFallback() {
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        IotAlertRule product = rule(8701L, "GT", "220", 0);
        product.setProductId(1L);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(product));
        properties.getAlertThresholds().put("temperature", new BigDecimal("80"));

        IotDeviceData reading = data("temperature", "187.2");
        assertFalse(checker.check(reading));
        verify(alertRecordService, never()).trigger(any(), any(), any(), any());
        verify(alertRecordService).autoResolve(reading);
    }

    /** 最具体的产品规则越限时，保留原有触发与阈值快照语义。 */
    @Test
    void check_productRuleBreached_triggersProductThreshold() {
        when(alertStateStore.readDeviceProduct(anyString())).thenReturn(1L);
        IotAlertRule product = rule(8701L, "GT", "220", 0);
        product.setProductId(1L);
        when(alertRuleService.selectEnabledRules(any(), any(), any(), any()))
            .thenReturn(List.of(product, rule(7001L, "GT", "80", 0)));

        assertTrue(checker.check(data("temperature", "225")));
        ArgumentCaptor<IotAlertRule> selected = ArgumentCaptor.forClass(IotAlertRule.class);
        verify(alertRecordService).trigger(selected.capture(), any(), any(), eq(IotAlertConstants.SOURCE_RULE));
        assertEquals(8701L, selected.getValue().getRuleId());
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
        /** 非法/空比较符回落 GT */
        assertTrue(IotAlertCondition.breached(null, new BigDecimal("81"), new BigDecimal("80")));
        assertFalse(IotAlertCondition.breached("XX", new BigDecimal("79"), new BigDecimal("80")));
        /** 阈值缺失不判定为越限（避免规则未配阈值时误报） */
        assertFalse(IotAlertCondition.breached("GT", new BigDecimal("99"), null));
    }

}
