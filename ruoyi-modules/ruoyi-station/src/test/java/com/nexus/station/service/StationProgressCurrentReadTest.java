package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationPassGuardMapper;
import com.nexus.station.mapper.StationReportMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 单件报工经真实动态数据源/事务代理执行，私有 H2 仅承载事务，业务 Mapper 均为替身。
 * 数量重读和剩余工序计数区分快照与当前状态，验证自动收口及幂等唯一卡完工。
 * 本类不连接任何业务库，不证明 MySQL 的范围锁、租户插件 SQL 或并发吞吐。
 */
@ExtendWith(MockitoExtension.class)
class StationProgressCurrentReadTest {
    private static final String TENANT = "000000";
    private static final String SN = "SN-CURRENT-1";
    private static final String STATION = "STN|JZ|L1|ASM|GENERAL";
    private static final long CARD_ID = 10L;
    private static final long WORK_ORDER_ID = 20L;
    private static final long STEP_ID = 200L;
    private static final StationPassService.OperatorInfo OPERATOR =
        new StationPassService.OperatorInfo(105L, "EMP-1", "操作员");

    @Mock private StationReportMapper reportMapper;
    @Mock private StationSchWorkerMapper schWorkerMapper;
    @Mock private StationDeviceMapper deviceMapper;
    @Mock private ProSnMapper proSnMapper;
    @Mock private ProFlowCardMapper flowCardMapper;
    @Mock private ProFlowCardStepMapper stepMapper;
    @Mock private TestRecordMapper testRecordMapper;
    @Mock private IProFlowCardService flowCardService;
    @Mock private IProSnService proSnService;
    @Mock private NcrOrderMapper ncrOrderMapper;
    @Mock private StationPassGuardMapper guard;
    @Mock private ProWorkOrderStepProgressService progressService;
    @Mock private com.nexus.mes.trace.MesSerialReferenceService serialReferences;
    @Mock private com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;
    @InjectMocks private StationPassService target;

    private StationPassService service;
    private MockedStatic<TenantHelper> tenant;
    private JdbcDataSource dataSource;
    private DataSourceTransactionManager transactionManager;
    private ProSn sn;
    private ProFlowCard card;
    private ProFlowCardStep lockedStep;
    private ProFlowCardStep freshStep;
    private ProFlowCardStep snapshotStep;
    private List<ProFlowCardStep> cardSteps;
    private List<StationPassGuardMapper.StoredReport> history;
    private StationPassReportDto request;
    private boolean freshVisible;
    private String statusAfterIncrement;
    private int incrementRows;
    private int completionRows;
    private int completionAttempts;

    @BeforeAll
    static void prepareTableMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "当前读报工测试");
        for (Class<?> entity : List.of(ProSn.class, ProFlowCardStep.class, MesStationReport.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        lenient().when(serialReferences.submissionStatus(any())).thenReturn(java.util.Map.of("submissionState","SERVER_COMMITTED","handoffStatus","NONE"));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:station_current_read_" + UUID.randomUUID());
        transactionManager = new DataSourceTransactionManager(dataSource);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
            new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(transactionManager);
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(transaction);
        service = (StationPassService) factory.getProxy();
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenant.when(TenantHelper::isEnable).thenReturn(true);

        sn = new ProSn();
        sn.setSnId(1L);
        sn.setIdentityId(10001L);
        sn.setTenantId(TENANT);
        sn.setDelFlag("0");
        sn.setSnCode(SN);
        sn.setCardId(CARD_ID);
        sn.setMesWoId(WORK_ORDER_ID);
        sn.setSnStatus("IN_PRODUCTION");
        sn.setCurrentStepSeq(20);
        card = new ProFlowCard();
        card.setCardId(CARD_ID);
        card.setTenantId(TENANT);
        card.setDelFlag("0");
        card.setMesWoId(WORK_ORDER_ID);
        card.setCardStatus("IN_PROGRESS");
        lockedStep = step(STEP_ID, 20, "IN_PROGRESS", "ASM");
        freshStep = copy(lockedStep);
        snapshotStep = copy(lockedStep);
        cardSteps = new ArrayList<>(List.of(step(100L, 10, "COMPLETED", "PREV"), lockedStep));
        history = new ArrayList<>();
        request = new StationPassReportDto();
        request.setSnCode(SN);
        request.setStationCode(STATION);
        request.setClientTs(1787295823000L);
        request.setResult("PASS");
        request.setOperatorId(OPERATOR.userId());
        request.setOperatorCode(OPERATOR.code());
        freshVisible = true;
        statusAfterIncrement = null;
        completionAttempts = 0;
        incrementRows = 1;
        completionRows = 1;

        var device = new MesStationDevice();
        device.setTenantId(TENANT);
        device.setDelFlag("0");
        device.setStationCode(STATION);
        device.setProcessCode("ASM");
        // 早期拒绝分支不会使用后续来源；共享替身保留所有路径而不降低结果断言。
        lenient().when(serialReferences.resolve(SN)).thenAnswer(invocation ->
            new com.nexus.mes.trace.MesSerialReferenceService.Resolved(TENANT,10001L,1L,SN,SN,sn.getMaterialId(),sn));
        lenient().when(progressService.lockForSn(SN)).thenReturn(WORK_ORDER_ID);
        lenient().when(guard.lockSn(TENANT, SN)).thenAnswer(invocation -> List.of(sn));
        lenient().when(guard.lockReports(TENANT, SN)).thenAnswer(invocation -> new ArrayList<>(history));
        lenient().when(guard.lockCard(TENANT, CARD_ID)).thenAnswer(invocation -> card);
        lenient().when(guard.lockSteps(TENANT, CARD_ID)).thenAnswer(invocation -> cardSteps);
        lenient().when(guard.lockDevices(TENANT, STATION)).thenReturn(List.of(device));
        lenient().when(guard.lockTests(TENANT, SN)).thenReturn(List.of());
        lenient().when(guard.lockNcrs(TENANT, CARD_ID)).thenReturn(List.of());
        lenient().when(reportMapper.insert(any(MesStationReport.class))).thenAnswer(invocation -> {
            assertWriteContext();
            var stored = new StationPassGuardMapper.StoredReport();
            BeanUtils.copyProperties(invocation.getArgument(0), stored);
            stored.setDelFlag("0");
            history.add(stored);
            return 1;
        });
        lenient().when(stepMapper.selectOne(any())).thenAnswer(invocation -> {
            assertWriteContext();
            Wrapper<?> query = invocation.getArgument(0);
            assertEquals(TENANT, parameter(query, "tenant_id", "="));
            assertEquals(CARD_ID, parameter(query, "card_id", "="));
            assertEquals(STEP_ID, parameter(query, "step_record_id", "="));
            return query.getSqlSegment().contains("FOR UPDATE")
                ? (freshVisible ? freshStep : null) : snapshotStep;
        });
        lenient().when(stepMapper.update(isNull(), any())).thenAnswer(invocation -> updateStep(invocation.getArgument(1)));
        lenient().when(stepMapper.selectCount(any())).thenAnswer(invocation -> {
            assertWriteContext();
            Wrapper<?> query = invocation.getArgument(0);
            assertEquals(CARD_ID, parameter(query, "card_id", "="));
            assertTrue(query.getSqlSegment().contains("step_status NOT IN"));
            var values = ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().values();
            assertTrue(values.containsAll(List.of("COMPLETED", "SKIPPED")));
            boolean currentRead = query.getSqlSegment().contains("FOR UPDATE");
            // 计数的租户插件边界在此显式建模；生产插件的注入 SQL 不属于本夹具证明范围。
            return cardSteps.stream().map(row -> currentRead && row.getStepRecordId().equals(STEP_ID) ? freshStep : row)
                .filter(row -> TENANT.equals(row.getTenantId()) && row.getCardId().equals(CARD_ID))
                .filter(row -> !List.of("COMPLETED", "SKIPPED").contains(row.getStepStatus())).count();
        });
        lenient().doAnswer(invocation -> {
            assertWriteContext();
            assertEquals(CARD_ID, invocation.<Long>getArgument(0));
            card.setCardStatus("COMPLETED");
            return null;
        }).when(flowCardService).completeCard(anyLong());
        lenient().doAnswer(invocation -> {
            assertWriteContext();
            assertEquals(1L, invocation.<Long>getArgument(0));
            sn.setSnStatus("COMPLETED");
            return null;
        }).when(proSnService).completeSn(anyLong());
        lenient().doAnswer(invocation -> {
            assertWriteContext();
            assertEquals(CARD_ID, invocation.<Long>getArgument(0));
            assertEquals(STEP_ID, invocation.<Long>getArgument(1));
            assertEquals(OPERATOR.userId(), invocation.getArgument(2));
            freshStep.setStepStatus("IN_PROGRESS");
            return null;
        }).when(flowCardService).startStep(anyLong(), anyLong(), anyLong());
        lenient().when(proSnMapper.update(isNull(), any())).thenAnswer(invocation -> {
            Wrapper<?> update = invocation.getArgument(1);
            assertEquals(TENANT, parameter(update, "tenant_id", "="));
            assertEquals(1L, parameter(update, "sn_id", "="));
            assertEquals("IN_PRODUCTION", parameter(update, "sn_status", "="));
            sn.setCurrentStepSeq(((Number) assignment(update, "current_step_seq")).intValue());
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        tenant.close();
        DynamicDataSourceContextHolder.clear();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
    }

    @Test
    void lastPassUsesPostIncrementQuantitiesAndCompletesCardExactlyOnce() {
        assertEquals("SERVER_COMMITTED",service.reportSingle(request, OPERATOR).get("submissionState"));

        assertEquals(new BigDecimal("2"), freshStep.getGoodQty());
        assertEquals("COMPLETED", freshStep.getStepStatus(), "快照数量 1 不能阻止当前累计量 2 触发收口");
        assertEquals(1, completionAttempts);
        verify(flowCardService).completeCard(CARD_ID);
        verify(proSnService).completeSn(1L);
        verify(progressService).rebuildForCard(CARD_ID);
        verify(stepMapper, never()).selectById(anyLong());
        var report = ArgumentCaptor.forClass(MesStationReport.class);
        verify(reportMapper).insert(report.capture());
        assertEquals(TENANT, report.getValue().getTenantId());
        assertEquals(CARD_ID, report.getValue().getCardId());
        assertEquals(STEP_ID, report.getValue().getStepRecordId());
        assertEquals(BigDecimal.ONE, report.getValue().getGoodQty());
        var order = inOrder(progressService, reportMapper, stepMapper, flowCardService, proSnService);
        order.verify(progressService).lockForSn(SN);
        order.verify(reportMapper).insert(any(MesStationReport.class));
        order.verify(stepMapper).update(isNull(), any());
        order.verify(proSnService).completeSn(1L);
        order.verify(stepMapper).selectOne(any());
        order.verify(stepMapper).update(isNull(), any());
        order.verify(stepMapper).selectCount(any());
        order.verify(flowCardService).completeCard(CARD_ID);
        order.verify(progressService).rebuildForCard(CARD_ID);
    }

    @Test
    void pendingCountMustSeeCompletionCasInsteadOfOldInProgressSnapshot() {
        quantities("1", "1", "0", "0");

        service.reportSingle(request, OPERATOR);

        assertEquals("COMPLETED", freshStep.getStepStatus());
        assertEquals("IN_PROGRESS", lockedStep.getStepStatus(), "保留的旧快照仍有一条未完工工序");
        verify(stepMapper).selectCount(any());
        verify(flowCardService).completeCard(CARD_ID);
    }

    @ParameterizedTest
    @CsvSource({
        "5, 1, 0, 0, false",
        "2, 1, 0, 0, true",
        "1, 1, 0, 0, true",
        "4, 1, 1, 1, true"
    })
    void completionUsesGoodDefectAndScrapTotalAgainstPlan(
        String planned, String good, String defect, String scrap, boolean reached) {
        quantities(planned, good, defect, scrap);

        service.reportSingle(request, OPERATOR);

        assertEquals(reached ? "COMPLETED" : "IN_PROGRESS", freshStep.getStepStatus());
        assertEquals(reached ? 1 : 0, completionAttempts);
        verify(flowCardService, times(reached ? 1 : 0)).completeCard(CARD_ID);
        verify(stepMapper, times(reached ? 1 : 0)).selectCount(any());
    }

    @Test
    void unfinishedFutureStepPreventsCardCompletionAndAdvancesToActualNextSequence() {
        cardSteps.add(step(400L, 40, "PENDING", "PKG"));

        service.reportSingle(request, OPERATOR);

        assertEquals("COMPLETED", freshStep.getStepStatus());
        assertEquals(40, sn.getCurrentStepSeq());
        verify(flowCardService, never()).completeCard(anyLong());
        verify(proSnService, never()).completeSn(anyLong());
        verify(proSnMapper).update(isNull(), any());
        verify(progressService).rebuildForCard(CARD_ID);
    }

    @Test
    void skippedPreviousStepDoesNotKeepCompletedCardOpen() {
        cardSteps.get(0).setStepStatus("SKIPPED");

        service.reportSingle(request, OPERATOR);

        verify(flowCardService).completeCard(CARD_ID);
        assertEquals("COMPLETED", card.getCardStatus());
    }

    @Test
    void completionCasLosingRaceDoesNotCountOrCompleteCard() {
        completionRows = 0;

        service.reportSingle(request, OPERATOR);

        assertEquals(1, completionAttempts);
        assertEquals("IN_PROGRESS", freshStep.getStepStatus());
        verify(stepMapper, never()).selectCount(any());
        verify(flowCardService, never()).completeCard(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "SKIPPED", "PENDING"})
    void nonRunningFreshStepDoesNotAttemptAnotherCompletion(String status) {
        statusAfterIncrement = status;

        service.reportSingle(request, OPERATOR);

        assertEquals(status, freshStep.getStepStatus());
        assertEquals(0, completionAttempts);
        verify(stepMapper, never()).selectCount(any());
        verify(flowCardService, never()).completeCard(anyLong());
    }

    @Test
    void absentFreshStepDoesNotAttemptCompletion() {
        freshVisible = false;

        service.reportSingle(request, OPERATOR);

        assertEquals(0, completionAttempts);
        verify(stepMapper, never()).selectCount(any());
        verify(flowCardService, never()).completeCard(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonPositiveReloadedPlanDoesNotCloseStep(String planned) {
        freshStep.setPlannedQty(new BigDecimal(planned));

        service.reportSingle(request, OPERATOR);

        assertEquals("IN_PROGRESS", freshStep.getStepStatus());
        assertEquals(0, completionAttempts);
        verify(flowCardService, never()).completeCard(anyLong());
    }

    @Test
    void pendingStepStartsBeforeIncrementAndCanCloseOnSamePass() {
        lockedStep.setStepStatus("PENDING");
        freshStep.setStepStatus("PENDING");
        snapshotStep.setStepStatus("PENDING");

        service.reportSingle(request, OPERATOR);

        assertEquals("COMPLETED", freshStep.getStepStatus());
        var order = inOrder(flowCardService, stepMapper);
        order.verify(flowCardService).startStep(CARD_ID, STEP_ID, OPERATOR.userId());
        order.verify(stepMapper).update(isNull(), any());
        order.verify(stepMapper).selectOne(any());
        order.verify(stepMapper).update(isNull(), any());
        order.verify(stepMapper).selectCount(any());
        order.verify(flowCardService).completeCard(CARD_ID);
    }

    @Test
    void failureReportAddsDefectButDoesNotAdvanceOrComplete() {
        request.setResult("FAIL");
        request.setDefectCodes(List.of("D1"));

        service.reportSingle(request, OPERATOR);

        assertEquals(BigDecimal.ONE, freshStep.getDefectQty());
        assertEquals(BigDecimal.ONE, freshStep.getGoodQty());
        assertEquals("IN_PROGRESS", freshStep.getStepStatus());
        verify(stepMapper, never()).selectOne(any());
        verify(stepMapper, never()).selectCount(any());
        verifyNoInteractions(flowCardService, proSnService);
        verify(proSnMapper, never()).update(isNull(), any());
        verify(progressService).rebuildForCard(CARD_ID);
    }

    @Test
    void committedReplayCannotIncrementOrCompleteCardTwice() {
        assertEquals("SERVER_COMMITTED",service.reportSingle(request, OPERATOR).get("submissionState"));
        assertEquals("SERVER_COMMITTED",service.reportSingle(request, OPERATOR).get("submissionState"));

        assertEquals(1, history.size());
        assertEquals(new BigDecimal("2"), freshStep.getGoodQty());
        assertEquals(1, completionAttempts);
        verify(reportMapper).insert(any(MesStationReport.class));
        verify(stepMapper, times(2)).update(isNull(), any());
        verify(stepMapper).selectOne(any());
        verify(stepMapper).selectCount(any());
        verify(flowCardService).completeCard(CARD_ID);
        verify(proSnService).completeSn(1L);
        verify(progressService).rebuildForCard(CARD_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"snTenant", "cardTenant", "stepTenant", "cardId", "stepCard", "stepWorkOrder", "identityId", "localSnId"})
    void mismatchedTenantOrCardSourceIsRejectedBeforeBusinessWrites(String mismatch) {
        switch (mismatch) {
            case "snTenant" -> sn.setTenantId("OTHER");
            case "cardTenant" -> card.setTenantId("OTHER");
            case "stepTenant" -> lockedStep.setTenantId("OTHER");
            case "cardId" -> card.setCardId(999L);
            case "stepCard" -> lockedStep.setCardId(999L);
            case "stepWorkOrder" -> lockedStep.setMesWoId(999L);
            case "identityId" -> sn.setIdentityId(20002L);
            case "localSnId" -> sn.setSnId(2L);
            default -> fail("未定义的来源反例");
        }

        assertThrows(ServiceException.class, () -> service.reportSingle(request, OPERATOR));

        assertNoBusinessWrites();
    }

    @ParameterizedTest
    @CsvSource({"mes, false, false", "mes, true, true", "erp, true, false"})
    void invalidWriteContextCannotTakeProgressLocksOrWrite(
        String source, boolean actualTransaction, boolean readOnly) {
        DynamicDataSourceContextHolder.push(source);
        try {
            ServiceException error = assertThrows(ServiceException.class, () -> {
                if (actualTransaction) {
                    var transaction = new TransactionTemplate(transactionManager);
                    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                    transaction.setReadOnly(readOnly);
                    transaction.execute(status -> target.reportSingle(request, OPERATOR));
                } else {
                    target.reportSingle(request, OPERATOR);
                }
            });
            assertTrue(error.getMessage().contains("实际 MES RR 写事务"));
            assertNoBusinessWrites();
            verifyNoInteractions(progressService, guard);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    void failedCounterIncrementStopsBeforeFreshReadAndSnCompletion() {
        incrementRows = 0;

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.reportSingle(request, OPERATOR));

        assertTrue(error.getMessage().contains("数量未写入"));
        verify(stepMapper, never()).selectOne(any());
        verify(stepMapper, never()).selectCount(any());
        verifyNoInteractions(flowCardService, proSnService);
        verify(progressService, never()).rebuildForCard(anyLong());
    }

    private int updateStep(Wrapper<?> update) {
        assertWriteContext();
        String assignment = update.getSqlSet();
        if (assignment.contains("good_qty = good_qty + 1") || assignment.contains("defect_qty = defect_qty + 1")) {
            assertEquals(TENANT, parameter(update, "tenant_id", "="));
            assertEquals(CARD_ID, parameter(update, "card_id", "="));
            assertEquals(STEP_ID, parameter(update, "step_record_id", "="));
            assertEquals("IN_PROGRESS", parameter(update, "step_status", "="));
            if (incrementRows == 1) {
                assertEquals("IN_PROGRESS", freshStep.getStepStatus());
                if (assignment.contains("good_qty")) {
                    freshStep.setGoodQty(freshStep.getGoodQty().add(BigDecimal.ONE));
                } else {
                    freshStep.setDefectQty(freshStep.getDefectQty().add(BigDecimal.ONE));
                }
                if (statusAfterIncrement != null) {
                    freshStep.setStepStatus(statusAfterIncrement);
                }
            }
            return incrementRows;
        }
        assertEquals("COMPLETED", assignment(update, "step_status"));
        assertNotNull(assignment(update, "actual_end_time"));
        assertEquals(STEP_ID, parameter(update, "step_record_id", "="));
        assertEquals("IN_PROGRESS", parameter(update, "step_status", "="));
        completionAttempts++;
        if (completionRows == 1) {
            freshStep.setStepStatus("COMPLETED");
        }
        return completionRows;
    }

    private void assertNoBusinessWrites() {
        verify(reportMapper, never()).insert(any(MesStationReport.class));
        verify(stepMapper, never()).update(isNull(), any());
        verify(stepMapper, never()).selectOne(any());
        verify(stepMapper, never()).selectCount(any());
        verify(proSnMapper, never()).update(isNull(), any());
        verifyNoInteractions(flowCardService, proSnService);
        verify(progressService, never()).rebuildForCard(anyLong());
    }

    private void quantities(String planned, String good, String defect, String scrap) {
        for (ProFlowCardStep step : List.of(lockedStep, freshStep, snapshotStep)) {
            step.setPlannedQty(new BigDecimal(planned));
            step.setGoodQty(new BigDecimal(good));
            step.setDefectQty(new BigDecimal(defect));
            step.setScrapQty(new BigDecimal(scrap));
        }
    }

    private static void assertWriteContext() {
        assertEquals("mes", DynamicDataSourceContextHolder.peek());
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
            TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
    }

    private static ProFlowCardStep step(long id, int sequence, String status, String process) {
        var step = new ProFlowCardStep();
        step.setStepRecordId(id);
        step.setTenantId(TENANT);
        step.setDelFlag("0");
        step.setCardId(CARD_ID);
        step.setMesWoId(WORK_ORDER_ID);
        step.setSequenceNum(sequence);
        step.setProcessCode(process);
        step.setProcessName("工序-" + sequence);
        step.setStepStatus(status);
        step.setOperatorId(OPERATOR.userId());
        step.setPlannedQty(new BigDecimal("2"));
        step.setGoodQty(BigDecimal.ONE);
        step.setDefectQty(BigDecimal.ZERO);
        step.setScrapQty(BigDecimal.ZERO);
        return step;
    }

    private static ProFlowCardStep copy(ProFlowCardStep source) {
        var target = new ProFlowCardStep();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private static Object parameter(Wrapper<?> wrapper, String column, String operator) {
        return boundValue(wrapper, wrapper.getSqlSegment(), column, operator);
    }

    private static Object assignment(Wrapper<?> wrapper, String column) {
        return boundValue(wrapper, wrapper.getSqlSet(), column, "=");
    }

    /** 对完整约束及真实绑定值作断言，锁选择同时驱动不同数据返回和最终业务结果。 */
    private static Object boundValue(Wrapper<?> wrapper, String sql, String column, String operator) {
        var matcher = Pattern.compile("\\b" + Pattern.quote(column) + "\\s*" + Pattern.quote(operator)
            + "\\s*#\\{ew\\.paramNameValuePairs\\.([^},]+)(?:,[^}]+)?}").matcher(sql);
        assertTrue(matcher.find(), "缺少业务约束: " + column + " " + operator + ": " + sql);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().get(matcher.group(1));
    }
}
