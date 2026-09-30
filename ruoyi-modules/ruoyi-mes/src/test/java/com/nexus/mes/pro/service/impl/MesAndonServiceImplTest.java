package com.nexus.mes.pro.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProAndon;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IoT -> MES 安灯回归：真实适配器、安灯服务和动态租户助手；隔离数据库、Token 和 SSE。
 * 不连接 Spring 应用、数据库、Redis 或真实设备，也不修改业务数据。
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class MesAndonServiceImplTest {

    private static final String ALERT_TENANT = "100001";
    private static final Long ANDON_ID = 555L;

    @Mock
    private ProAndonMapper baseMapper;
    @Mock
    private ProFlowCardMapper flowCardMapper;
    @Mock
    private ProWorkOrderMapper workOrderMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProAndonServiceImpl service;
    private MesAndonServiceImpl adapter;
    private ProAndon inserted;
    private String tenantAtInsert;
    private MockedStatic<SpringUtil> spring;
    private MockedStatic<SpringUtils> aop;
    private MockedStatic<LoginHelper> login;
    private MockedStatic<StpUtil> tokens;
    private MockedStatic<SseMessageUtils> sse;

    @BeforeAll
    static void initializeStaticUtilities() throws ClassNotFoundException {
        SpringStaticBootstrap.ensureActive();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProAndon.class);
        /** 首次初始化 SSE 工具时禁用真实 SSE 发送器；各用例随后只模拟投递边界。 */
        try (MockedStatic<SpringUtil> properties = mockStatic(SpringUtil.class)) {
            properties.when(() -> SpringUtils.getProperty("sse.enabled", Boolean.class, true)).thenReturn(false);
            Class.forName(SseMessageUtils.class.getName());
        }
    }

    @BeforeEach
    void setUp() {
        service = new ProAndonServiceImpl(baseMapper, flowCardMapper, workOrderMapper, eventPublisher);
        adapter = new MesAndonServiceImpl(service);
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
        aop = mockStatic(SpringUtils.class);
        aop.when(() -> SpringUtils.getAopProxy(any())).thenReturn(service);
        login = mockStatic(LoginHelper.class);
        /** Mockito 对装箱 Long 的默认值是 0；显式 null 才是真实无登录场景。 */
        login.when(LoginHelper::getUserId).thenReturn(null);
        tokens = mockStatic(StpUtil.class);
        sse = mockStatic(SseMessageUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        /** 避免登录态测试清理时触发 Redis；这里只清理真实 ThreadLocal 动态租户。 */
        login.when(LoginHelper::isLogin).thenReturn(false);
        TenantHelper.clearDynamic();
        sse.close();
        tokens.close();
        login.close();
        aop.close();
        spring.close();
    }

    /**
     * Mapper 边界保存真实待插入实体并生成同字段 VO，不预设 source/caller/tenant。
     * 拒绝非法输入的用例允许这些桩不被使用，并显式 verifyNoInteractions。
     */
    private void stubPersistence(int notificationCasRows) {
        lenient().when(baseMapper.insert(any(ProAndon.class))).thenAnswer(invocation -> {
            ProAndon row = invocation.getArgument(0);
            tenantAtInsert = TenantHelper.getTenantId();
            row.setAndonId(ANDON_ID);
            inserted = BeanUtil.toBean(row, ProAndon.class);
            return 1;
        });
        lenient().when(baseMapper.selectVoById(any())).thenAnswer(invocation -> BeanUtil.toBean(inserted, ProAndonVo.class));
        lenient().when(baseMapper.update(any(), any())).thenReturn(notificationCasRows);
    }

    private IotAlert alert() {
        IotAlert alert = new IotAlert();
        alert.setTenantId(ALERT_TENANT);
        alert.setDeviceId(123L);
        alert.setDeviceCode("DEVICE-001");
        alert.setMesEquipmentId(456L);
        alert.setAlertType("TEMPERATURE");
        alert.setAlertMessage("温度超限");
        alert.setValue(new BigDecimal("187.3"));
        alert.setThreshold(new BigDecimal("80"));
        return alert;
    }

    private void stubOnlineUsers() {
        LoginUser sameTenant = new LoginUser();
        sameTenant.setTenantId(ALERT_TENANT);
        sameTenant.setUserId(7L);
        LoginUser otherTenant = new LoginUser();
        otherTenant.setTenantId("200002");
        otherTenant.setUserId(8L);
        tokens.when(() -> StpUtil.searchTokenValue("", 0, -1, false))
            .thenReturn(List.of("token:same", "token:other"));
        login.when(() -> LoginHelper.getLoginUser("same")).thenReturn(sameTenant);
        login.when(() -> LoginHelper.getLoginUser("other")).thenReturn(otherTenant);
        sse.when(SseMessageUtils::isEnable).thenReturn(true);
    }

    @Test
    void alert_firstInsertIsIotEquipmentInAlertTenantWithoutCaller() {
        stubPersistence(1);

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        assertEquals(IProAndonService.SOURCE_IOT, inserted.getSource());
        assertEquals(IProAndonService.TYPE_EQUIPMENT, inserted.getCallType());
        assertEquals(IProAndonService.STATUS_CALLED, inserted.getAndonStatus());
        assertEquals(ALERT_TENANT, inserted.getTenantId());
        assertEquals(ALERT_TENANT, tenantAtInsert);
        assertNull(inserted.getCallerId());
        assertNull(inserted.getCallerName());
        assertNull(inserted.getMesWoId());
        assertNull(inserted.getCardId());
        assertNull(inserted.getStepRecordId());
        assertNull(inserted.getStationCode());
        assertNull(TenantHelper.getDynamic());
        verify(baseMapper, times(1)).insert(any(ProAndon.class));
        verify(baseMapper, times(1)).update(any(), any());
        verifyNoInteractions(flowCardMapper, workOrderMapper, eventPublisher);
    }

    @Test
    void alert_reasonPreservesActualDeviceAndMesEquipmentIdentity() {
        stubPersistence(1);
        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        String reason = inserted.getCallReason();
        assertTrue(reason.contains("deviceCode=DEVICE-001"), reason);
        assertTrue(reason.contains("deviceId=123"), reason);
        assertTrue(reason.contains("mesEquipmentId=456"), reason);
        assertTrue(reason.contains("TEMPERATURE"), reason);
        assertTrue(reason.contains("温度超限"), reason);
        assertTrue(reason.contains("187.3"), reason);
        assertTrue(reason.contains("80"), reason);
    }

    @Test
    void alert_optionalMesEquipmentIsNotInventedAndReasonFitsExistingColumn() {
        stubPersistence(1);
        IotAlert alert = alert();
        alert.setMesEquipmentId(null);
        alert.setAlertMessage("真实告警描述".repeat(150));

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert));

        assertTrue(inserted.getCallReason().contains("deviceCode=DEVICE-001"));
        assertTrue(inserted.getCallReason().contains("deviceId=123"));
        assertFalse(inserted.getCallReason().contains("mesEquipmentId="));
        assertTrue(inserted.getCallReason().length() <= 500);
    }

    @Test
    void alert_doesNotBorrowCallerFromAnUnrelatedLoginContext() {
        stubPersistence(1);
        login.when(LoginHelper::isLogin).thenReturn(true);
        login.when(LoginHelper::getUserId).thenReturn(99L);
        login.when(LoginHelper::getUsername).thenReturn("unrelated-user");
        login.when(LoginHelper::getTenantId).thenReturn("200002");

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        assertNull(inserted.getCallerId());
        assertNull(inserted.getCallerName());
        assertEquals(ALERT_TENANT, tenantAtInsert);
    }

    @Test
    void alert_notifiesAndPushesOnlyUsersOfTheDynamicAlertTenant() {
        stubPersistence(1);
        stubOnlineUsers();

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        ArgumentCaptor<UserNotificationEvent> notification = ArgumentCaptor.forClass(UserNotificationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(notification.capture());
        assertEquals(ALERT_TENANT, notification.getValue().getTenantId());
        assertEquals(7L, notification.getValue().getTargetUserId());
        assertEquals("555", notification.getValue().getRefId());
        ArgumentCaptor<SseMessageDto> message = ArgumentCaptor.forClass(SseMessageDto.class);
        sse.verify(() -> SseMessageUtils.publishMessage(message.capture()), times(1));
        assertEquals(List.of(7L), message.getValue().getUserIds());
        assertTrue(message.getValue().getMessage().contains("DEVICE-001"));
    }

    @Test
    void alert_afterCommitNotificationKeepsAlertTenantAfterDynamicScopeRestores() {
        stubPersistence(1);
        stubOnlineUsers();
        TransactionSynchronizationManager.initSynchronization();

        TenantHelper.dynamic("outer-tenant", () -> {
            assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));
            assertEquals("outer-tenant", TenantHelper.getTenantId());
        });
        assertNull(TenantHelper.getDynamic());
        verifyNoInteractions(eventPublisher);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<UserNotificationEvent> notification = ArgumentCaptor.forClass(UserNotificationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(notification.capture());
        assertEquals(ALERT_TENANT, notification.getValue().getTenantId());
        assertEquals(7L, notification.getValue().getTargetUserId());
    }

    @Test
    void alert_notifyCasMissSuppressesDuplicateNotificationAndSse() {
        stubPersistence(0);
        stubOnlineUsers();

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        verify(baseMapper, times(1)).update(any(), any());
        verifyNoInteractions(eventPublisher);
        sse.verify(() -> SseMessageUtils.publishMessage(any(SseMessageDto.class)), never());
    }

    @Test
    void alert_offlineDoesNotInventANotificationRecipient() {
        stubPersistence(1);
        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        assertNull(inserted.getCallerId());
        verify(baseMapper, times(1)).update(any(), any());
        verifyNoInteractions(eventPublisher);
        sse.verify(() -> SseMessageUtils.publishMessage(any(SseMessageDto.class)), never());
    }

    @Test
    void alert_insertFailureRestoresOuterDynamicTenant() {
        when(baseMapper.insert(any(ProAndon.class))).thenThrow(new IllegalStateException("test insert failure"));

        TenantHelper.dynamic("outer-tenant", () -> {
            assertNull(adapter.createAndonFromAlert(alert()));
            assertEquals("outer-tenant", TenantHelper.getTenantId());
        });
        assertNull(TenantHelper.getDynamic());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void alert_disabledTenantPluginStillStoresAndNotifiesTheExplicitAlertTenant() {
        stubPersistence(1);
        stubOnlineUsers();
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("false");

        assertEquals(ANDON_ID, adapter.createAndonFromAlert(alert()));

        assertEquals(ALERT_TENANT, inserted.getTenantId());
        ArgumentCaptor<UserNotificationEvent> notification = ArgumentCaptor.forClass(UserNotificationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(notification.capture());
        assertEquals(ALERT_TENANT, notification.getValue().getTenantId());
        assertEquals(7L, notification.getValue().getTargetUserId());
    }

    @Test
    void nullAlert_isRejectedWithoutWritingOrNotifying() {
        assertNull(assertDoesNotThrow(() -> adapter.createAndonFromAlert(null)));
        verifyNoInteractions(baseMapper, eventPublisher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad tenant", "bad'tenant", "abcdefghijklmnopqrstu", "非法租户"})
    void invalidTenant_isRejectedWithoutWritingOrNotifying(String tenantId) {
        stubPersistence(1);
        IotAlert alert = alert();
        alert.setTenantId(tenantId);

        assertNull(adapter.createAndonFromAlert(alert));

        verifyNoInteractions(baseMapper, eventPublisher);
        assertNull(TenantHelper.getDynamic());
        tokens.verifyNoInteractions();
        sse.verifyNoInteractions();
    }

    static Stream<Arguments> invalidIdentities() {
        return Stream.of(
            Arguments.of(null, "DEVICE-001", 456L),
            Arguments.of(0L, "DEVICE-001", 456L),
            Arguments.of(-1L, "DEVICE-001", 456L),
            Arguments.of(123L, null, 456L),
            Arguments.of(123L, "", 456L),
            Arguments.of(123L, " ", 456L),
            Arguments.of(123L, "D".repeat(65), 456L),
            Arguments.of(123L, "DEVICE-001", 0L),
            Arguments.of(123L, "DEVICE-001", -1L)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void invalidIotIdentity_isRejectedWithoutWritingOrNotifying(Long deviceId, String deviceCode, Long mesEquipmentId) {
        stubPersistence(1);
        IotAlert alert = alert();
        alert.setDeviceId(deviceId);
        alert.setDeviceCode(deviceCode);
        alert.setMesEquipmentId(mesEquipmentId);

        assertNull(adapter.createAndonFromAlert(alert));

        verifyNoInteractions(baseMapper, eventPublisher);
        assertNull(TenantHelper.getDynamic());
    }

    @Test
    void manualCall_keepsManualSourceAndLoggedInCaller() {
        stubPersistence(1);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getUsername).thenReturn("operator");
        login.when(LoginHelper::getTenantId).thenReturn(ALERT_TENANT);

        ProAndonVo vo = service.callAndon(null, null, null, IProAndonService.TYPE_QUALITY, "人工质量呼叫");

        assertEquals(IProAndonService.SOURCE_MANUAL, inserted.getSource());
        assertEquals(7L, inserted.getCallerId());
        assertEquals("operator", inserted.getCallerName());
        assertEquals(ANDON_ID, vo.getAndonId());
        verify(eventPublisher, times(1)).publishEvent(any(UserNotificationEvent.class));
    }

    @Test
    void stationCall_keepsManualSourceStationAndActualOperatorWithoutLogin() {
        stubPersistence(1);

        TenantHelper.dynamic(ALERT_TENANT, () -> service.callAndon(null, null, null,
            IProAndonService.TYPE_EQUIPMENT, "设备通道呼叫", "STATION-001", "OP-001"));

        assertEquals(IProAndonService.SOURCE_MANUAL, inserted.getSource());
        assertEquals("STATION-001", inserted.getStationCode());
        assertEquals("OP-001", inserted.getCallerName());
        assertNull(inserted.getCallerId());
        assertEquals(ALERT_TENANT, tenantAtInsert);
    }

    @Test
    void stationCall_loginIdentityStillTakesPriorityOverOperatorCode() {
        stubPersistence(1);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getUsername).thenReturn("operator");
        login.when(LoginHelper::getTenantId).thenReturn(ALERT_TENANT);

        service.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT,
            "设备通道呼叫", "STATION-001", "OP-001");

        assertEquals(7L, inserted.getCallerId());
        assertEquals("operator", inserted.getCallerName());
        assertEquals("STATION-001", inserted.getStationCode());
        assertEquals(IProAndonService.SOURCE_MANUAL, inserted.getSource());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad tenant"})
    void notification_missingOrInvalidEffectiveTenantNeverBroadcasts(String tenantId) {
        stubPersistence(1);
        stubOnlineUsers();
        login.when(LoginHelper::getTenantId).thenReturn(tenantId);

        assertNotNull(service.callAndon(null, null, null, IProAndonService.TYPE_QUALITY, "人工呼叫"));

        verifyNoInteractions(eventPublisher);
        tokens.verifyNoInteractions();
        sse.verifyNoInteractions();
        verify(baseMapper, never()).update(any(), any());
    }
}
