package com.nexus.mes.qc.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.bo.QcInspectEntryBo;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.service.IQcInspectDetailService;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * IPQC 原通知离线回归：真实服务与真实事务同步回调，所有外部端口均为替身。
 * 不启动容器、不接触真实 API、数据库、Redis 或 MQ。
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class QcIpqcNotificationTest {
    private static final Long QID = 2107086753833562114L;
    private static final Long MES_WO_ID = 2106995561900978178L;
    private static final Long ERP_WO_ID = 2106995346720600065L;
    private static final String TENANT = "000000";
    private static final String IPQC_NO = "IPQC-回归原检验";
    private static final String WO_NO = "原MES工单";

    @Mock
    private QcIpqcMapper baseMapper;
    @Mock
    private ProWorkOrderMapper proWorkOrderMapper;
    // 保留旧依赖替身，RED 可准确暴露旧富工单/桥接读取；GREEN 必须零调用。
    @Mock
    private IProWorkOrderService proWorkOrderService;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private INcrOrderService ncrOrderService;
    @Mock
    private IQcInspectDetailService inspectDetailService;
    @InjectMocks
    private QcIpqcServiceImpl service;

    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;
    private boolean committed;

    @BeforeAll
    static void initializeColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcIpqc.class);
    }

    @AfterAll
    static void removeColumns() {
        TableInfoHelper.remove(QcIpqc.class);
    }

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class);
        tenant = mockStatic(TenantHelper.class);
        login.when(LoginHelper::getUserId).thenAnswer(inv -> {
            assertFalse(committed, "提交后禁止重新读取登录信息");
            return 1L;
        });
        login.when(LoginHelper::getUsername).thenAnswer(inv -> {
            assertFalse(committed, "提交后禁止重新读取检验员");
            return "原检验员";
        });
        tenant.when(TenantHelper::getTenantId).thenAnswer(inv -> {
            assertFalse(committed, "提交后禁止重新读取租户/Redis上下文");
            return TENANT;
        });
        TransactionSynchronizationManager.initSynchronization();
        // 模拟提交回调仍处于资源清理之前，不绑定任何真实连接。
        TransactionSynchronizationManager.bindResource("IPQC离线MES资源", new Object());
        lenient().when(proWorkOrderService.selectById(anyLong())).thenThrow(
            new IllegalStateException("模拟富工单ERP桥接失败: Table 'ry-mes.bd_routing' doesn't exist"));
    }

    @AfterEach
    void tearDown() {
        login.close();
        tenant.close();
        TransactionSynchronizationManager.unbindResourceIfPossible("IPQC离线MES资源");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void inspectFreezesOriginalMappingInsideTransaction() {
        QcIpqc ipqc = pending();
        ProWorkOrder wo = mapped();
        prepareInspect(ipqc, wo);

        service.inspect(QID, "QUALIFIED", null, new BigDecimal("10"), BigDecimal.ZERO, null);

        verify(proWorkOrderMapper).selectById(MES_WO_ID);
        verifyNoInteractions(proWorkOrderService, mesMqSendService);
        // 提交之前修改替身对象，回调仍须发送冻结的原映射和编号。
        wo.setErpWoId(999L);
        wo.setWoNo("被改写的编号");
        ipqc.setIpqcNo("被改写的检验号");
        clearInvocations(baseMapper, proWorkOrderMapper);
        assertDoesNotThrow(this::afterCommit);
        verifyMessage("QUALIFIED", BigDecimal.ZERO, new BigDecimal("0.00"));
        assertNoReadsOrQualityWrites();
    }

    @Test
    void afterCommitNeverCallsRichWorkOrderOrBridgeEvenWithMesResourceBound() {
        prepareInspect(pending(), mapped());
        service.inspect(QID, "QUALIFIED", null, null, null, null);
        clearInvocations(baseMapper, proWorkOrderMapper);

        assertDoesNotThrow(this::afterCommit, "已提交检验不能再因富工单桥接读取返回API错误");

        verifyMessage("QUALIFIED", null, null);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void pureMesWorkOrderWithoutErpMappingKeepsOriginalSkipBehavior() {
        ProWorkOrder wo = mapped();
        wo.setErpWoId(null);
        prepareInspect(pending(), wo);
        service.inspect(QID, "QUALIFIED", null, null, null, null);
        clearInvocations(baseMapper, proWorkOrderMapper);

        assertDoesNotThrow(this::afterCommit);

        verifyNoInteractions(mesMqSendService);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void inspectionWithoutMesWorkOrderKeepsOriginalSkipBehavior() {
        QcIpqc ipqc = pending();
        ipqc.setMesWoId(null);
        when(baseMapper.selectById(QID)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(QID, "QUALIFIED", null, null, null, null);
        clearInvocations(baseMapper);
        assertDoesNotThrow(this::afterCommit);

        verifyNoInteractions(proWorkOrderMapper, mesMqSendService);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void inspectionMissingWorkOrderKeepsOriginalSkipBehavior() {
        prepareInspect(pending(), null);
        service.inspect(QID, "QUALIFIED", null, null, null, null);
        clearInvocations(baseMapper, proWorkOrderMapper);

        assertDoesNotThrow(this::afterCommit);

        verifyNoInteractions(mesMqSendService);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void inspectionSendFailurePropagatesSameExceptionAfterCommit() {
        prepareInspect(pending(), mapped());
        IllegalStateException failure = new IllegalStateException("模拟原MQ投递失败");
        lenient().doThrow(failure).when(mesMqSendService).sendIpqcCompleted(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        service.inspect(QID, "QUALIFIED", null, null, null, null);
        clearInvocations(baseMapper, proWorkOrderMapper);

        assertSame(failure, assertThrows(IllegalStateException.class, this::afterCommit));

        assertNoReadsOrQualityWrites();
    }

    @Test
    void unqualifiedInspectionPreservesNcrAndOriginalQualityMessage() {
        QcIpqc ipqc = pending();
        ipqc.setCardId(11L);
        ipqc.setStepRecordId(12L);
        ipqc.setProcessId(13L);
        prepareInspect(ipqc, mapped());

        service.inspect(QID, "UNQUALIFIED", "原不良事实", new BigDecimal("10"), new BigDecimal("2"), "原备注");

        verify(ncrOrderService).autoCreateFromIpqc(QID, MES_WO_ID, 11L, 12L, 13L,
            new BigDecimal("2"), "原不良事实");
        clearInvocations(baseMapper, proWorkOrderMapper, ncrOrderService);
        assertDoesNotThrow(this::afterCommit);
        verifyMessage("UNQUALIFIED", new BigDecimal("2"), new BigDecimal("20.00"));
        assertNoReadsOrQualityWrites();
    }

    @Test
    void detailsInspectionUsesSameFrozenNotificationWithoutChangingJudgement() {
        prepareInspect(pending(), mapped());
        QcInspectDetail detail = new QcInspectDetail();
        detail.setDetailId(22L);
        detail.setDataType("QUALITATIVE");
        detail.setCriticality("CRITICAL");
        when(inspectDetailService.selectByCheck("IPQC", QID)).thenReturn(List.of(detail));
        QcInspectEntryBo entry = new QcInspectEntryBo();
        entry.setDetailId(22L);
        entry.setMeasuredValue("OK");
        entry.setSampleQty(new BigDecimal("10"));

        service.inspectWithDetails(QID, List.of(entry), null, "原明细判定");

        assertEquals("PASS", detail.getJudgeResult());
        verify(inspectDetailService).updateDetails(List.of(detail));
        clearInvocations(baseMapper, proWorkOrderMapper, inspectDetailService);
        assertDoesNotThrow(this::afterCommit);
        // 原明细链路不按项目数推断不良件数，消息中的 null 必须保持。
        verifyMessage("QUALIFIED", null, null);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void completedInspectionCannotBeReinspectedOrReset() {
        when(baseMapper.selectById(QID)).thenReturn(completed());

        assertThrows(RuntimeException.class, () -> service.inspect(QID, "QUALIFIED", null, null, null, null));

        verify(baseMapper).selectById(QID);
        verifyNoMoreInteractions(baseMapper);
        verifyNoInteractions(proWorkOrderMapper, ncrOrderService, inspectDetailService, mesMqSendService);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void casFailureDoesNotReadMappingOrPublish() {
        when(baseMapper.selectById(QID)).thenReturn(pending());
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.inspect(QID, "QUALIFIED", null, null, null, null));

        verifyNoInteractions(proWorkOrderMapper, ncrOrderService, mesMqSendService);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUALIFIED", "UNQUALIFIED"})
    void recoveryOnlyReplaysPersistedFactsAndNeverReinspectsOrCreatesNcr(String result) {
        QcIpqc ipqc = completed();
        ipqc.setInspectResult(result);
        String before = ipqc.toString();
        prepareRecovery(ipqc, mapped());

        assertEquals("ENQUEUED", recover(QID));

        assertEquals(before, ipqc.toString(), "恢复不得改变结果、数量、状态或检验时间");
        verify(baseMapper).selectById(QID);
        verify(proWorkOrderMapper).selectById(MES_WO_ID);
        verifyNoMoreInteractions(baseMapper, proWorkOrderMapper);
        verifyNoInteractions(ncrOrderService, inspectDetailService, mesMqSendService, proWorkOrderService);
        clearInvocations(baseMapper, proWorkOrderMapper);
        ipqc.setInspectResult("PENDING");
        ipqc.setDefectQty(new BigDecimal("99"));
        assertDoesNotThrow(this::afterCommit);
        verifyMessage(result, new BigDecimal("2.0000"), new BigDecimal("20.00"));
        assertNoReadsOrQualityWrites();
    }

    @Test
    void recoveryPreservesNullOriginalDefectRateWithoutRecalculation() {
        QcIpqc ipqc = completed();
        ipqc.setDefectQty(BigDecimal.ZERO);
        ipqc.setDefectRate(null);
        prepareRecovery(ipqc, mapped());

        assertEquals("ENQUEUED", recover(QID));
        clearInvocations(baseMapper, proWorkOrderMapper);
        afterCommit();

        verifyMessage("QUALIFIED", BigDecimal.ZERO, null);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void pureMesRecoveryExplicitlySkipsInsteadOfClaimingEnqueued() {
        ProWorkOrder wo = mapped();
        wo.setErpWoId(null);
        prepareRecovery(completed(), wo);

        assertEquals("SKIPPED_NO_ERP", recover(QID));
        clearInvocations(baseMapper, proWorkOrderMapper);
        afterCommit();

        verifyNoInteractions(mesMqSendService);
        assertNoReadsOrQualityWrites();
    }

    @Test
    void recoverySendFailurePropagatesWithoutChangingInspection() {
        QcIpqc ipqc = completed();
        String before = ipqc.toString();
        prepareRecovery(ipqc, mapped());
        IllegalStateException failure = new IllegalStateException("模拟恢复MQ失败");
        lenient().doThrow(failure).when(mesMqSendService).sendIpqcCompleted(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        recover(QID);
        clearInvocations(baseMapper, proWorkOrderMapper);

        assertSame(failure, assertThrows(IllegalStateException.class, this::afterCommit));

        assertEquals(before, ipqc.toString());
        assertNoReadsOrQualityWrites();
    }

    @Test
    void recoveryRejectsMissingInspection() {
        assertRejectedRecovery(QID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PENDING", "INSPECTING", "CANCELLED"})
    void recoveryRejectsNotCompleted(String status) {
        QcIpqc ipqc = completed();
        ipqc.setStatus(status);
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PENDING", "PARTIAL", "FORGED"})
    void recoveryRejectsInvalidPersistedJudgement(String result) {
        QcIpqc ipqc = completed();
        ipqc.setInspectResult(result);
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsMissingRealInspectionTime() {
        QcIpqc ipqc = completed();
        ipqc.setInspectTime(null);
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsFutureInspectionTime() {
        QcIpqc ipqc = completed();
        ipqc.setInspectTime(new Date(System.currentTimeMillis() + 86_400_000));
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsDeletedInspection() {
        QcIpqc ipqc = completed();
        ipqc.setDelFlag("1");
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsWrongInspectionTenantEvenIfMapperReturnedIt() {
        QcIpqc ipqc = completed();
        ipqc.setTenantId("100001");
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"租户非法", "9223372036854775808"})
    void recoveryRejectsMissingOrInvalidTenantContext(String tenantId) {
        tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
        assertRejectedRecovery(QID);
        verifyNoInteractions(baseMapper, proWorkOrderMapper);
    }

    @Test
    void recoveryRejectsMissingOriginalMesMapping() {
        QcIpqc ipqc = completed();
        ipqc.setMesWoId(null);
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsMissingOriginalWorkOrder() {
        prepareRecovery(completed(), null);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsWrongWorkOrderTenant() {
        ProWorkOrder wo = mapped();
        wo.setTenantId("100001");
        prepareRecovery(completed(), wo);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsDeletedOriginalWorkOrder() {
        ProWorkOrder wo = mapped();
        wo.setDelFlag("1");
        prepareRecovery(completed(), wo);
        assertRejectedRecovery(QID);
    }

    @Test
    void recoveryRejectsDifferentOriginalWorkOrderId() {
        ProWorkOrder wo = mapped();
        wo.setMesWoId(999L);
        prepareRecovery(completed(), wo);
        assertRejectedRecovery(QID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void recoveryRejectsInvalidOriginalId(Long id) {
        assertRejectedRecovery(id);
        verifyNoInteractions(baseMapper, proWorkOrderMapper);
    }

    @Test
    void springTransactionProxyReturnsEnqueuedOnlyAfterCommitNotification() {
        prepareRecovery(completed(), mapped());
        service = recoveryTransactionProxy();

        assertEquals("ENQUEUED", recover(QID));

        assertTrue(committed, "正常代理调用返回之前必须完成实际事务提交回调");
        verifyMessage("QUALIFIED", new BigDecimal("2.0000"), new BigDecimal("20.00"));
        verifyNoInteractions(ncrOrderService, inspectDetailService, proWorkOrderService);
    }

    @Test
    void springTransactionProxyCannotReturnSuccessWhenAfterCommitSendFails() {
        QcIpqc ipqc = completed();
        String before = ipqc.toString();
        prepareRecovery(ipqc, mapped());
        IllegalStateException failure = new IllegalStateException("模拟实际提交后发送失败");
        lenient().doThrow(failure).when(mesMqSendService).sendIpqcCompleted(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        service = recoveryTransactionProxy();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> recover(QID)));

        assertTrue(committed, "检验事实已提交，不得回滚或重检");
        assertEquals(before, ipqc.toString());
        verify(baseMapper, never()).update(any(), any());
        verifyNoInteractions(ncrOrderService, inspectDetailService, proWorkOrderService);
    }

    /** 使用真实 Spring 事务拦截器与无数据库事务管理器验证 API 返回时序。 */
    private QcIpqcServiceImpl recoveryTransactionProxy() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.unbindResource("IPQC离线MES资源");
        var manager =
                new AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() {
                        return new Object();
                    }

                    @Override
                    protected void doBegin(Object tx, TransactionDefinition definition) {
                        assertTrue(definition.isReadOnly(), "恢复只读MES事实，不改检验");
                        TransactionSynchronizationManager.bindResource("IPQC离线MES资源", tx);
                    }

                    @Override
                    protected void doCommit(DefaultTransactionStatus status) {
                        committed = true;
                    }

                    @Override
                    protected void doRollback(DefaultTransactionStatus status) {
                        fail("提交后通知失败不应触发检验回滚");
                    }

                    @Override
                    protected void doCleanupAfterCompletion(Object tx) {
                        TransactionSynchronizationManager.unbindResourceIfPossible("IPQC离线MES资源");
                    }
                };
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (QcIpqcServiceImpl) factory.getProxy();
    }

    private void prepareInspect(QcIpqc ipqc, ProWorkOrder wo) {
        when(baseMapper.selectById(QID)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);
        lenient().when(proWorkOrderMapper.selectById(MES_WO_ID)).thenAnswer(inv -> {
            assertFalse(committed, "映射必须在实际MES事务内读取");
            return wo;
        });
    }

    private void prepareRecovery(QcIpqc ipqc, ProWorkOrder wo) {
        lenient().when(baseMapper.selectById(QID)).thenReturn(ipqc);
        lenient().when(proWorkOrderMapper.selectById(MES_WO_ID)).thenAnswer(inv -> {
            assertFalse(committed, "恢复映射也必须在提交之前冻结");
            return wo;
        });
    }

    private void assertRejectedRecovery(Long id) {
        assertThrows(ServiceException.class, () -> recover(id));
        verify(baseMapper, never()).update(any(), any());
        verifyNoInteractions(mesMqSendService, ncrOrderService, inspectDetailService, proWorkOrderService);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    /** 反射让 RED 在旧接口上可编译，方法缺失仍是明确的失败断言。 */
    private String recover(Long id) {
        try {
            return (String) service.getClass().getMethod("recoverNotification", Long.class).invoke(service, id);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("原已完成检验缺少安全通知恢复入口", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void afterCommit() {
        committed = true;
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void assertNoReadsOrQualityWrites() {
        verifyNoInteractions(baseMapper, proWorkOrderMapper, proWorkOrderService, inspectDetailService, ncrOrderService);
    }

    private void verifyMessage(String result, BigDecimal qty, BigDecimal rate) {
        verify(mesMqSendService).sendIpqcCompleted(eq(QID), eq(IPQC_NO), eq(MES_WO_ID), eq(ERP_WO_ID), eq(WO_NO),
            eq(result), eq(qty), eq(rate), eq(1L), eq("原检验员"), any(Date.class), eq(TENANT));
    }

    private static QcIpqc pending() {
        QcIpqc ipqc = completed();
        ipqc.setStatus("PENDING");
        ipqc.setInspectResult("PENDING");
        ipqc.setInspectTime(null);
        return ipqc;
    }

    private static QcIpqc completed() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(QID);
        ipqc.setIpqcNo(IPQC_NO);
        ipqc.setMesWoId(MES_WO_ID);
        ipqc.setTenantId(TENANT);
        ipqc.setDelFlag("0");
        ipqc.setStatus("COMPLETED");
        ipqc.setInspectResult("QUALIFIED");
        ipqc.setInspectTime(new Date(System.currentTimeMillis() - 60_000));
        ipqc.setInspectorId(1L);
        ipqc.setInspectorName("原检验员");
        ipqc.setSampleQty(new BigDecimal("10.0000"));
        ipqc.setDefectQty(new BigDecimal("2.0000"));
        ipqc.setDefectRate(new BigDecimal("20.00"));
        ipqc.setRemark("仅离线回归，非真实业务操作");
        return ipqc;
    }

    private static ProWorkOrder mapped() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(MES_WO_ID);
        wo.setErpWoId(ERP_WO_ID);
        wo.setWoNo(WO_NO);
        wo.setTenantId(TENANT);
        wo.setDelFlag("0");
        return wo;
    }
}
