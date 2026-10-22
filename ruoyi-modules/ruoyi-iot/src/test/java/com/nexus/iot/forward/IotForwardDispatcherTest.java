package com.nexus.iot.forward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.forward.domain.IotForwardLog;
import com.nexus.iot.forward.domain.IotForwardRule;
import com.nexus.iot.forward.mapper.IotForwardLogMapper;
import com.nexus.iot.forward.mapper.IotForwardRuleMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * IoT 数据转发单元测试（§4#6）。
 * <p>
 * 覆盖：SSRF 校验（内网/保留地址/非 http(s)/userinfo 一律拒绝）、规则匹配（设备范围 + 属性白名单）、
 * 失败留痕 + 退避重试时间、成功留痕、投递前二次 SSRF 校验（防 DNS rebinding）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotForwardDispatcherTest {

    private static final String TENANT = "000000";
    /** 公网字面量地址：无需 DNS 解析，避免离线环境测试不稳定 */
    private static final String PUBLIC_URL = "http://93.184.216.34/hook";

    private IotForwardRuleMapper ruleMapper;
    private IotForwardLogMapper logMapper;
    private IotForwardHttpClient httpClient;
    private IIotDeviceService deviceService;
    private IotForwardDispatcher dispatcher;
    private MockedStatic<TenantHelper> tenantMock;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotForwardLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotForwardRule.class);
        ruleMapper = mock(IotForwardRuleMapper.class);
        logMapper = mock(IotForwardLogMapper.class);
        httpClient = mock(IotForwardHttpClient.class);
        deviceService = mock(IIotDeviceService.class);
        dispatcher =
                new IotForwardDispatcher(
                        ruleMapper, logMapper, httpClient, deviceService, new ObjectMapper());

        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return null;
            });
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    private IotForwardRule rule(String url, String propertyCodes) {
        IotForwardRule rule = new IotForwardRule();
        rule.setRuleId(7L);
        rule.setTenantId(TENANT);
        rule.setRuleName("MES回传");
        rule.setDeviceId(0L);
        rule.setProductId(0L);
        rule.setPropertyCodes(propertyCodes);
        rule.setTargetType("HTTP");
        rule.setTargetUrl(url);
        rule.setHttpMethod("POST");
        rule.setTimeoutMs(2000);
        rule.setMaxRetry(3);
        rule.setEnabled("1");
        return rule;
    }

    private IotForwardLog logRow(int retryCount, String url) {
        IotForwardLog row = new IotForwardLog();
        row.setLogId(55L);
        row.setTenantId(TENANT);
        row.setRuleId(7L);
        row.setDeviceId(1L);
        row.setDeviceCode("DEV0001");
        row.setPropertyCode("temperature");
        row.setTargetUrl(url);
        row.setPayload("{}");
        row.setStatus(IotForwardDispatcher.STATUS_PENDING);
        row.setRetryCount(retryCount);
        return row;
    }

    private IotDeviceData data() {
        IotDeviceData d = new IotDeviceData();
        d.setTenantId(TENANT);
        d.setDeviceId(1L);
        d.setDeviceCode("DEV0001");
        d.setPropertyCode("temperature");
        d.setDataType("NUMERIC");
        d.setNumericValue(new BigDecimal("75.5"));
        d.setCollectTime(new Date());
        d.setReceiveTime(new Date());
        d.setQuality("GOOD");
        d.setSource("MQTT");
        return d;
    }

    /** SSRF：内网/回环/链路本地/非 http(s)/userinfo 一律拒绝。 */
    @Test
    void sanitizeUrl_blocksInternalAndInvalidTargets() {
        assertNull(IotForwardUrlValidator.sanitize("http://127.0.0.1/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://localhost/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://192.168.1.10/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://10.0.0.5/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://172.16.3.4/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://169.254.169.254/latest/meta-data"));
        assertNull(IotForwardUrlValidator.sanitize("http://100.64.1.1/hook"), "CGNAT 段必须封堵");
        assertNull(IotForwardUrlValidator.sanitize("ftp://example.com/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://user:pass@8.8.8.8/hook"));
        assertNull(IotForwardUrlValidator.sanitize("http://0.0.0.0/hook"));
    }

    /** SSRF：公网 http/https 放行（不能"一刀切全拒"）。 */
    @Test
    void sanitizeUrl_allowsPublicHttp() {
        assertEquals(PUBLIC_URL, IotForwardUrlValidator.sanitize(PUBLIC_URL));
        assertTrue(IotForwardUrlValidator.sanitize("https://8.8.8.8/hook").startsWith("https://"));
    }

    /** 规则命中 + 属性匹配 → 落 PENDING 留痕并提交投递。 */
    @Test
    void dispatch_matchedRule_createsLog() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(PUBLIC_URL, "temperature,humidity")));
        when(logMapper.insert(any(IotForwardLog.class))).thenReturn(1);

        int created = dispatcher.dispatch(data());

        assertEquals(1, created);
        ArgumentCaptor<IotForwardLog> captor = ArgumentCaptor.forClass(IotForwardLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(IotForwardDispatcher.STATUS_PENDING, captor.getValue().getStatus());
        assertEquals(TENANT, captor.getValue().getTenantId());
        assertTrue(captor.getValue().getPayload().contains("temperature"));
    }

    /** 属性不在白名单 → 不转发（避免无关数据外发）。 */
    @Test
    void dispatch_propertyNotInWhitelist_skipped() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(PUBLIC_URL, "vibration")));

        assertEquals(0, dispatcher.dispatch(data()));
        verify(logMapper, never()).insert(any(IotForwardLog.class));
    }

    /** 规则 URL 为内网地址（历史脏数据）→ 直接落 FAILED 留痕且不发起请求。 */
    @Test
    void dispatch_internalUrl_recordsFailureWithoutRequest() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule("http://192.168.1.9/hook", null)));
        when(logMapper.insert(any(IotForwardLog.class))).thenReturn(1);

        dispatcher.dispatch(data());

        ArgumentCaptor<IotForwardLog> captor = ArgumentCaptor.forClass(IotForwardLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(IotForwardDispatcher.STATUS_FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getErrorMsg().contains("SSRF"));
        verify(httpClient, never()).post(anyString(), anyString(), any(), anyString(), anyInt());
    }

    /** 投递成功 → 留痕 SUCCESS，清空重试时间。 */
    @Test
    @SuppressWarnings("unchecked")
    void deliver_success_marksSuccess() {
        when(logMapper.selectById(55L)).thenReturn(logRow(0, PUBLIC_URL));
        when(ruleMapper.selectById(7L)).thenReturn(rule(PUBLIC_URL, null));
        when(httpClient.post(anyString(), anyString(), any(), anyString(), anyInt()))
            .thenReturn(new IotForwardHttpClient.DeliveryResult(true, 200, "ok"));
        when(logMapper.update(any(), any())).thenReturn(1);

        assertTrue(dispatcher.deliver(55L));

        ArgumentCaptor<Wrapper<IotForwardLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotForwardLog> wrapper = (LambdaUpdateWrapper<IotForwardLog>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotForwardDispatcher.STATUS_SUCCESS));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(200));
    }

    /** 投递失败（未达重试上限）→ FAILED + next_retry_time（可被重试任务捞回）。 */
    @Test
    @SuppressWarnings("unchecked")
    void deliver_failure_schedulesRetry() {
        when(logMapper.selectById(55L)).thenReturn(logRow(0, PUBLIC_URL));
        when(ruleMapper.selectById(7L)).thenReturn(rule(PUBLIC_URL, null));
        when(httpClient.post(anyString(), anyString(), any(), anyString(), anyInt()))
            .thenReturn(new IotForwardHttpClient.DeliveryResult(false, 500, "boom"));
        when(logMapper.update(any(), any())).thenReturn(1);

        assertFalse(dispatcher.deliver(55L));

        ArgumentCaptor<Wrapper<IotForwardLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotForwardLog> wrapper = (LambdaUpdateWrapper<IotForwardLog>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotForwardDispatcher.STATUS_FAILED));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(v -> v instanceof Date), "失败且未达上限必须写 next_retry_time（Date 参数）");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("boom"));
    }

    /** 投递失败且已达重试上限 → 不再安排重试（next_retry_time 置空）。 */
    @Test
    @SuppressWarnings("unchecked")
    void deliver_failureExhausted_noRetryScheduled() {
        when(logMapper.selectById(55L)).thenReturn(logRow(3, PUBLIC_URL));
        when(ruleMapper.selectById(7L)).thenReturn(rule(PUBLIC_URL, null));
        when(httpClient.post(anyString(), anyString(), any(), anyString(), anyInt()))
            .thenReturn(new IotForwardHttpClient.DeliveryResult(false, 500, "still down"));
        when(logMapper.update(any(), any())).thenReturn(1);

        assertFalse(dispatcher.deliver(55L));

        ArgumentCaptor<Wrapper<IotForwardLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotForwardLog> wrapper = (LambdaUpdateWrapper<IotForwardLog>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null), "重试次数用尽应写 null 结束重试");
    }

    /** 投递前二次校验：留痕 URL 被改解析到内网 → 拒绝投递（防 DNS rebinding）。 */
    @Test
    void deliver_revalidatesUrlBeforeRequest() {
        when(logMapper.selectById(55L)).thenReturn(logRow(0, "http://127.0.0.1/hook"));
        when(ruleMapper.selectById(7L)).thenReturn(rule("http://127.0.0.1/hook", null));
        when(logMapper.update(any(), any())).thenReturn(1);

        assertFalse(dispatcher.deliver(55L));

        verify(httpClient, never()).post(anyString(), anyString(), any(), anyString(), anyInt());
    }

    /** 规则被删除 → 留痕 FAILED 且不再重试。 */
    @Test
    void deliver_ruleDeleted_marksFailed() {
        when(logMapper.selectById(55L)).thenReturn(logRow(0, PUBLIC_URL));
        when(ruleMapper.selectById(7L)).thenReturn(null);
        when(logMapper.update(any(), any())).thenReturn(1);

        assertFalse(dispatcher.deliver(55L));

        verify(httpClient, never()).post(anyString(), anyString(), any(), anyString(), anyInt());
        verify(logMapper).update(any(), any());
    }

    /** 产品范围规则：设备产品ID匹配才转发。 */
    @Test
    void dispatch_productScope_matchedByDeviceProduct() {
        IotForwardRule rule = rule(PUBLIC_URL, null);
        rule.setProductId(4L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        IotDeviceVo vo = new IotDeviceVo();
        vo.setDeviceId(1L);
        vo.setProductId(4L);
        when(deviceService.selectById(1L)).thenReturn(vo);
        when(logMapper.insert(any(IotForwardLog.class))).thenReturn(1);

        assertEquals(1, dispatcher.dispatch(data()));
    }

    /** 产品范围规则：产品不匹配 → 不转发。 */
    @Test
    void dispatch_productScopeMismatch_skipped() {
        IotForwardRule rule = rule(PUBLIC_URL, null);
        rule.setProductId(9L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        IotDeviceVo vo = new IotDeviceVo();
        vo.setDeviceId(1L);
        vo.setProductId(4L);
        when(deviceService.selectById(1L)).thenReturn(vo);

        assertEquals(0, dispatcher.dispatch(data()));
    }

    /** 无租户的数据不转发（避免 tenant_id 落 0 / 跨租户外发）。 */
    @Test
    void dispatch_missingTenant_skipped() {
        IotDeviceData d = data();
        d.setTenantId(null);
        assertEquals(0, dispatcher.dispatch(d));
        verify(ruleMapper, never()).selectList(any());
    }

    /** 重试扫描：CAS 领取失败留痕后重新投递（多实例安全）。 */
    @Test
    void retryFailed_claimsAndResubmits() {
        IotForwardLog row = logRow(1, PUBLIC_URL);
        row.setStatus(IotForwardDispatcher.STATUS_FAILED);
        row.setNextRetryTime(new Date(System.currentTimeMillis() - 1000));
        when(logMapper.selectList(any())).thenReturn(List.of(row));
        when(logMapper.update(any(), any())).thenReturn(1);

        int claimed = dispatcher.retryFailed();

        assertEquals(1, claimed);
        verify(logMapper, Mockito.atLeastOnce()).update(any(), any());
    }

    /** 重试扫描：CAS 未领取到（其他实例已处理）→ 不投递。 */
    @Test
    void retryFailed_casConflict_skips() {
        IotForwardLog row = logRow(1, PUBLIC_URL);
        row.setStatus(IotForwardDispatcher.STATUS_FAILED);
        row.setNextRetryTime(new Date(System.currentTimeMillis() - 1000));
        when(logMapper.selectList(any())).thenReturn(List.of(row));
        when(logMapper.update(any(), any())).thenReturn(0);

        assertEquals(0, dispatcher.retryFailed());
    }

    /** 无命中规则 → 不产生留痕。 */
    @Test
    void dispatch_noRule_returnsZero() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, dispatcher.dispatch(data()));
        verify(logMapper, never()).insert(any(IotForwardLog.class));
    }
}
