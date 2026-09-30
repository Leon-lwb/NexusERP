package com.nexus.mes.pro.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.pro.domain.ProAndon;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProAndonServiceImpl 安灯通知单元测试(MES-PRO-08)
 * <p>
 * 守护点: 无同租户在线用户(离线)时不再"只落一条 debug 日志就返回"——
 * ① 呼叫通知仍落库(pro_andon.notify_time/notify_user_ids CAS)
 * ② 仍产生站内通知记录(UserNotificationEvent -> ruoyi-im 落 sys_user_notification, 离线兜底通知呼叫人)
 * ③ 日志级别为 warn 且带 andonId 上下文
 * ④ 同一 andonId 的呼叫通知幂等(CAS 未命中则不再通知)。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class ProAndonServiceImplTest {

    private static Logger andonLogger;
    private static ListAppender<ILoggingEvent> logAppender;

    @BeforeAll
    static void initAll() {
        /** JsonUtils 静态初始化依赖 SpringUtils, 先激活静态桩(站内通知 extJson 走真实 Jackson) */
        SpringStaticBootstrap.ensureActive();
        /** lambdaUpdate.set(...) 立即解析实体列缓存, 单测环境须手动初始化表元数据 */
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProAndon.class);
        andonLogger = (Logger) LoggerFactory.getLogger(ProAndonServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        andonLogger.addAppender(logAppender);
        andonLogger.setLevel(Level.WARN);
    }

    @AfterAll
    static void tearDownAll() {
        if (andonLogger != null && logAppender != null) {
            andonLogger.detachAppender(logAppender);
        }
    }

    @Mock
    private ProAndonMapper baseMapper;
    @Mock
    private ProFlowCardMapper flowCardMapper;
    @Mock
    private ProWorkOrderMapper workOrderMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ProAndonServiceImpl service;

    private MockedStatic<SpringUtil> propertyMock;
    private MockedStatic<SpringUtils> aopProxyMock;
    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        propertyMock = mockStatic(SpringUtil.class);
        propertyMock.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
        /** publishNotificationsAfterCommit 经 SpringUtils.getAopProxy 调用事务方法, 纯单测无容器 */
        aopProxyMock = mockStatic(SpringUtils.class, withSettings().lenient());
        aopProxyMock.when(() -> SpringUtils.getAopProxy(any())).thenReturn(service);
        /** 无 Sa-Token 请求上下文, 登录态相关静态方法必须打桩 */
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(7L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("operator");
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn("000000");
        logAppender.list.clear();
    }

    @AfterEach
    void tearDown() {
        if (aopProxyMock != null) {
            aopProxyMock.close();
        }
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
        if (propertyMock != null) {
            propertyMock.close();
        }
    }

    private boolean hasWarn(String keyword) {
        return logAppender.list.stream()
            .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(keyword));
    }

    /** 桩: 工单存在 + 插入回填 + 回读 VO(呼叫人 7) */
    private void stubCallAndon() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoNo("WO-001");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(baseMapper.insert(any(ProAndon.class))).thenReturn(1);
        ProAndonVo vo = new ProAndonVo();
        vo.setAndonId(555L);
        vo.setAndonNo("AND-20260830-0001");
        vo.setCallerId(7L);
        vo.setCallType("QUALITY");
        vo.setAndonStatus("CALLED");
        vo.setMesWoId(1L);
        vo.setWoNo("WO-001");
        when(baseMapper.selectVoById(any())).thenReturn(vo);
    }

    /**
     * 离线(无同租户在线用户): 仍落库通知记录 + warn 日志
     */
    @Test
    void callAndon_withoutOnlineUsers_stillPersistsNotificationAndWarns() {
        stubCallAndon();
        /** 呼叫通知落库 CAS 命中(首次通知) */
        when(baseMapper.update(any(), any())).thenReturn(1);

        ProAndonVo result = service.callAndon(1L, null, null, "QUALITY", "物料短缺");
        assertNotNull(result);

        /** ① 呼叫通知落库(notify_time/notify_user_ids CAS) */
        verify(baseMapper).update(any(), any());
        /** ② 站内通知事件仍产生(离线兜底通知呼叫人 7), 经 ruoyi-im 落 sys_user_notification */
        ArgumentCaptor<UserNotificationEvent> captor = ArgumentCaptor.forClass(UserNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        UserNotificationEvent event = captor.getValue();
        assertEquals(7L, event.getTargetUserId());
        assertEquals("mes_andon", event.getCategory());
        assertEquals("pro_andon", event.getRefType());
        assertEquals("555", event.getRefId());
        assertEquals("000000", event.getTenantId());
        assertTrue(event.getTitle().contains("呼叫"));
        /** ③ warn 日志(原实现为 debug 且直接 return) */
        assertTrue(hasWarn("无同租户在线用户"), "无在线用户时必须记录 warn 日志");
        assertTrue(hasWarn("andonId=555"), "warn 日志须带 andonId 上下文");
    }

    /**
     * 幂等: 同一 andonId 的呼叫通知只发一次(CAS 未命中则跳过通知)
     */
    @Test
    void callAndon_duplicateCallNotification_isIdempotent() {
        stubCallAndon();
        /** notify_time 已非空 -> CAS 未命中 */
        when(baseMapper.update(any(), any())).thenReturn(0);

        service.callAndon(1L, null, null, "QUALITY", "物料短缺");

        verify(baseMapper).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any(UserNotificationEvent.class));
        assertTrue(hasWarn("无同租户在线用户"));
    }

    /**
     * 通知落库异常不影响安灯主流程(呼叫仍成功)
     */
    @Test
    void callAndon_notificationFailure_doesNotBreakCall() {
        stubCallAndon();
        when(baseMapper.update(any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("通知库不可用"))
            .when(eventPublisher).publishEvent(any(UserNotificationEvent.class));

        ProAndonVo result = assertDoesNotThrow(() -> service.callAndon(1L, null, null, "MATERIAL", "缺料"));
        assertNotNull(result);
        assertTrue(hasWarn("无同租户在线用户"));
    }
}
