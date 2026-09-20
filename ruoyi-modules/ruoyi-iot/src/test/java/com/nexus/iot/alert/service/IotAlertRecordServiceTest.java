package com.nexus.iot.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertNotifyLog;
import com.nexus.iot.alert.domain.IotAlertRecord;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.domain.IotAlertStatus;
import com.nexus.iot.alert.mapper.IotAlertNotifyLogMapper;
import com.nexus.iot.alert.mapper.IotAlertRecordMapper;
import com.nexus.iot.alert.service.impl.IotAlertRecordServiceImpl;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.pipeline.IotAlertNotifier;
import com.nexus.iot.pipeline.IotAlertStateStore;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警记录服务单元测试（IOT-ALERT-01）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>规则命中触发落库（含"无在线用户"时仍然落库 + 投递留痕 SKIPPED）；</li>
 *   <li>同一未恢复告警不重复产生（已有未恢复 → 合并更新；并发唯一键冲突 → 退化为合并）；</li>
 *   <li>确认/恢复的 CAS（0 行 = 状态被并发变更 → 抛异常）；</li>
 *   <li>状态机非法流转被拒（RESOLVED 不可再确认/恢复）；</li>
 *   <li>属性恢复正常 → 自动 RESOLVED（清 active_flag）。</li>
 * </ul>
 * Redis 访问已收敛到 {@link IotAlertStateStore}（{@code RedisUtils} 静态初始化依赖 Spring 容器，无法静态打桩）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotAlertRecordServiceTest {

    private static final String TENANT = "000000";

    private IotAlertRecordMapper baseMapper;
    private IotAlertNotifyLogMapper notifyLogMapper;
    private IotAlertNotifier notifier;
    private IotAlertStateStore alertStateStore;
    private IotAlertRecordServiceImpl service;
    private MockedStatic<TenantHelper> tenantMock;
    private MockedStatic<LoginHelper> loginMock;

    @BeforeEach
    void setUp() {
        // LambdaWrapper 需要实体的列缓存（否则 LambdaUtils.extract 抛 can not find lambda cache）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotAlertRecord.class);
        baseMapper = mock(IotAlertRecordMapper.class);
        notifyLogMapper = mock(IotAlertNotifyLogMapper.class);
        notifier = mock(IotAlertNotifier.class);
        alertStateStore = mock(IotAlertStateStore.class);
        service = new IotAlertRecordServiceImpl(baseMapper, notifyLogMapper, notifier, alertStateStore);

        // 消费线程无登录上下文：dynamic 直接执行回调体
        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return null;
            });
        loginMock = mockStatic(LoginHelper.class);
        loginMock.when(LoginHelper::getUserId).thenReturn(9L);
        // 去抖窗口默认放行
        when(alertStateStore.acquireDebounce(anyString(), any(Duration.class))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loginMock.close();
        tenantMock.close();
    }

    private IotDeviceData data(String value) {
        IotDeviceData d = new IotDeviceData();
        d.setTenantId(TENANT);
        d.setDeviceId(1L);
        d.setDeviceCode("DEV0001");
        d.setPropertyCode("temperature");
        d.setNumericValue(new BigDecimal(value));
        return d;
    }

    private IotAlert alert(String value, String threshold) {
        IotAlert a = new IotAlert();
        a.setTenantId(TENANT);
        a.setDeviceId(1L);
        a.setDeviceCode("DEV0001");
        a.setAlertType("TEMPERATURE");
        a.setAlertLevel("HIGH");
        a.setAlertMessage("设备 DEV0001 属性 temperature 越限");
        a.setValue(new BigDecimal(value));
        a.setThreshold(new BigDecimal(threshold));
        return a;
    }

    private IotAlertRule rule() {
        IotAlertRule r = new IotAlertRule();
        r.setRuleId(3L);
        r.setTenantId(TENANT);
        r.setRuleName("温度上限");
        r.setProductId(0L);
        r.setDeviceId(0L);
        r.setPropertyCode("temperature");
        r.setOperator("GT");
        r.setThreshold(new BigDecimal("80"));
        r.setDurationSec(0);
        r.setDebounceSec(60);
        r.setEnabled(IotAlertConstants.ENABLED_YES);
        return r;
    }

    private IotAlertRecord activeRecord(Long alertId, String status) {
        IotAlertRecord r = new IotAlertRecord();
        r.setAlertId(alertId);
        r.setTenantId(TENANT);
        r.setDeviceId(1L);
        r.setDeviceCode("DEV0001");
        r.setPropertyCode("temperature");
        r.setOperator("GT");
        r.setThreshold(new BigDecimal("80"));
        r.setStatus(status);
        r.setActiveFlag(IotAlertStatus.isUnresolved(status) ? 1 : null);
        return r;
    }

    /** 触发落库：无未恢复告警 → insert，且即使"无在线用户"也必须有落库与投递留痕。 */
    @Test
    void trigger_noActiveRecord_insertsAndLogsSkippedNotify() {
        when(baseMapper.selectOne(any())).thenReturn(null);
        when(baseMapper.insert(any(IotAlertRecord.class))).thenReturn(1);
        when(notifier.notify(any())).thenReturn(List.of(
            IotAlertNotifier.ChannelOutcome.skipped(IotAlertConstants.CHANNEL_SSE, "无同租户在线用户，告警已落库待查")));

        service.trigger(rule(), data("95"), alert("95", "80"), IotAlertConstants.SOURCE_RULE);

        ArgumentCaptor<IotAlertRecord> recordCaptor = ArgumentCaptor.forClass(IotAlertRecord.class);
        verify(baseMapper).insert(recordCaptor.capture());
        IotAlertRecord saved = recordCaptor.getValue();
        assertEquals(TENANT, saved.getTenantId());
        assertEquals(1L, saved.getDeviceId());
        assertEquals("temperature", saved.getPropertyCode());
        assertEquals(IotAlertStatus.ACTIVE, saved.getStatus());
        assertEquals(1, saved.getActiveFlag());
        assertEquals(3L, saved.getRuleId());
        assertEquals(0, saved.getThreshold().compareTo(new BigDecimal("80")));
        assertEquals(0, saved.getTriggerValue().compareTo(new BigDecimal("95")));

        ArgumentCaptor<IotAlertNotifyLog> logCaptor = ArgumentCaptor.forClass(IotAlertNotifyLog.class);
        verify(notifyLogMapper).insert(logCaptor.capture());
        assertEquals(IotAlertConstants.NOTIFY_SKIPPED, logCaptor.getValue().getStatus());
        assertEquals(IotAlertConstants.CHANNEL_SSE, logCaptor.getValue().getChannel());
    }

    /** 幂等：已存在未恢复告警 → 只合并更新，不新增（同一设备+属性仅一条未恢复）。 */
    @Test
    void trigger_existingActive_mergesWithoutInsert() {
        when(baseMapper.selectOne(any())).thenReturn(activeRecord(11L, IotAlertStatus.ACTIVE));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(notifier.notify(any())).thenReturn(List.of());

        Long alertId = service.trigger(rule(), data("95"), alert("95", "80"), IotAlertConstants.SOURCE_RULE);

        assertEquals(11L, alertId);
        verify(baseMapper, never()).insert(any(IotAlertRecord.class));
        verify(baseMapper, times(1)).update(any(), any());
    }

    /** 幂等（并发兜底）：唯一键冲突 → 退化为合并更新，不抛异常、不产生第二条未恢复告警。 */
    @Test
    void trigger_duplicateKey_degradesToMerge() {
        when(baseMapper.selectOne(any())).thenReturn(null, activeRecord(11L, IotAlertStatus.ACTIVE));
        when(baseMapper.insert(any(IotAlertRecord.class)))
            .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_active_alert'"));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(notifier.notify(any())).thenReturn(List.of());

        Long alertId = service.trigger(rule(), data("95"), alert("95", "80"), IotAlertConstants.SOURCE_RULE);

        assertEquals(11L, alertId);
        verify(baseMapper, times(1)).insert(any(IotAlertRecord.class));
        verify(baseMapper, times(1)).update(any(), any());
    }

    /** 确认：ACTIVE → ACKNOWLEDGED，CAS 成功。 */
    @Test
    @SuppressWarnings("unchecked")
    void acknowledge_active_casSuccess() {
        when(baseMapper.selectById(7L)).thenReturn(activeRecord(7L, IotAlertStatus.ACTIVE));
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.acknowledge(7L, "已知悉"));

        ArgumentCaptor<Wrapper<IotAlertRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        String sqlSet = ((LambdaUpdateWrapper<IotAlertRecord>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("status"), "确认必须写入 status: " + sqlSet);
        assertTrue(sqlSet.contains("ack_time"), "确认必须写入确认时间: " + sqlSet);
    }

    /** 确认：CAS 影响 0 行（状态被并发变更）→ 抛业务异常。 */
    @Test
    void acknowledge_casConflict_throws() {
        when(baseMapper.selectById(7L)).thenReturn(activeRecord(7L, IotAlertStatus.ACTIVE));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.acknowledge(7L, null));
        assertTrue(ex.getMessage().contains("状态已被其他操作变更"));
    }

    /** 状态机：RESOLVED 为终态，不允许再确认。 */
    @Test
    void acknowledge_resolvedState_rejected() {
        when(baseMapper.selectById(7L)).thenReturn(activeRecord(7L, IotAlertStatus.RESOLVED));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.acknowledge(7L, null));
        assertTrue(ex.getMessage().contains("非法流转"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 状态机：RESOLVED 不允许再恢复。 */
    @Test
    void resolve_resolvedState_rejected() {
        when(baseMapper.selectById(7L)).thenReturn(activeRecord(7L, IotAlertStatus.RESOLVED));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.resolve(7L, null, null));
        assertTrue(ex.getMessage().contains("非法流转"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 恢复：ACKNOWLEDGED → RESOLVED，写恢复值并清 active_flag（允许再次触发新记录）。 */
    @Test
    @SuppressWarnings("unchecked")
    void resolve_acknowledged_casSuccessAndClearsActiveFlag() {
        when(baseMapper.selectById(7L)).thenReturn(activeRecord(7L, IotAlertStatus.ACKNOWLEDGED));
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.resolve(7L, new BigDecimal("60"), "现场已处理"));

        ArgumentCaptor<Wrapper<IotAlertRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        String sqlSet = ((LambdaUpdateWrapper<IotAlertRecord>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("status"), "恢复必须写入 status: " + sqlSet);
        assertTrue(sqlSet.contains("active_flag = NULL"), "恢复必须清 active_flag: " + sqlSet);
        assertTrue(sqlSet.contains("resolve_value"), "恢复必须写恢复值: " + sqlSet);
    }

    /** 属性恢复正常 → 自动 RESOLVED。 */
    @Test
    @SuppressWarnings("unchecked")
    void autoResolve_valueBackToNormal_resolves() {
        when(baseMapper.selectOne(any())).thenReturn(activeRecord(11L, IotAlertStatus.ACTIVE));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.autoResolve(data("50"));

        ArgumentCaptor<Wrapper<IotAlertRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        String sqlSet = ((LambdaUpdateWrapper<IotAlertRecord>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("active_flag = NULL"), "自动恢复必须清 active_flag: " + sqlSet);
        assertTrue(sqlSet.contains("resolve_value"), "自动恢复必须写恢复值: " + sqlSet);
    }

    /** 仍处越限状态 → 不自动恢复。 */
    @Test
    void autoResolve_stillBreached_keepsActive() {
        when(baseMapper.selectOne(any())).thenReturn(activeRecord(11L, IotAlertStatus.ACTIVE));

        service.autoResolve(data("95"));

        verify(baseMapper, never()).update(any(), any());
    }

}
