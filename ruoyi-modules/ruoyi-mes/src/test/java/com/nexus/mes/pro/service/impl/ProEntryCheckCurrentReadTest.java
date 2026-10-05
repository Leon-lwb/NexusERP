package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProStepCheckRule;
import com.nexus.mes.pro.domain.bo.ProEntryBo;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.domain.vo.ProCheckResultVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProStepCheckRuleMapper;
import com.nexus.mes.pro.service.IProEntryCheckService;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import com.nexus.mes.sop.service.ISopViewLogService;
import com.nexus.mes.tg.service.ITgToolingService;
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
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 校验入口的当前读回归：私有 H2 只提供真实 Spring 事务，Mapper 替身区分快照与当前状态。
 * 门禁、数量及所属卡的业务结果必须随读法变化；不把替身视作 MySQL 的并发锁证明。
 */
@ExtendWith(MockitoExtension.class)
class ProEntryCheckCurrentReadTest {
    private static final long STEP_ID = 300L;
    private static final long CARD_ID = 100L;
    private static final long PROCESS_ID = 200L;

    @Mock private ProFlowCardStepMapper stepMapper;
    @Mock private ProStepCheckRuleMapper ruleMapper;
    @Mock private ProAndonMapper andonMapper;
    @Mock private QcIpqcMapper qcIpqcMapper;
    @Mock private EqEquipmentMapper eqEquipmentMapper;
    @Mock private ISopProcessBindingService sopBindingService;
    @Mock private ISopViewLogService sopViewLogService;
    @Mock private ITgToolingService tgToolingService;
    @Mock private ILtLotService ltLotService;
    @InjectMocks private ProEntryCheckServiceImpl service;

    private MockedStatic<TenantHelper> tenant;
    private JdbcDataSource dataSource;
    private DataSourceTransactionManager transactionManager;
    private ProFlowCardStep currentStep;
    private ProFlowCardStep snapshotStep;
    private List<ProFlowCardStep> currentPrevious;
    private List<ProFlowCardStep> snapshotPrevious;
    private List<ProStepCheckRule> rules;

    @BeforeAll
    static void prepareTableMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "当前读校验测试");
        TableInfoHelper.initTableInfo(assistant, ProFlowCardStep.class);
        TableInfoHelper.initTableInfo(assistant, ProStepCheckRule.class);
    }

    @BeforeEach
    void setUp() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:entry_current_read_" + UUID.randomUUID());
        transactionManager = new DataSourceTransactionManager(dataSource);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        currentStep = step(STEP_ID, CARD_ID, 30, "IN_PROGRESS");
        snapshotStep = copy(currentStep);
        currentPrevious = new ArrayList<>(List.of(step(200L, CARD_ID, 20, "COMPLETED")));
        snapshotPrevious = new ArrayList<>(List.of(step(200L, CARD_ID, 20, "IN_PROGRESS")));
        rules = List.of();

        // 两条互斥读路径共用夹具，未选择的路径允许不被调用；断言仍要求入口实际走正确路径。
        lenient().when(stepMapper.selectById(anyLong())).thenAnswer(invocation -> {
            assertEquals(STEP_ID, invocation.<Long>getArgument(0));
            return snapshotStep;
        });
        lenient().when(stepMapper.selectOne(any())).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            boolean currentRead = sql.contains("FOR UPDATE");
            if (sql.contains("step_record_id")) {
                assertEquals(STEP_ID, parameter(query, "step_record_id", "="));
                return currentRead ? currentStep : snapshotStep;
            }
            long card = ((Number) parameter(query, "card_id", "=")).longValue();
            int before = ((Number) parameter(query, "sequence_num", "<")).intValue();
            assertTrue(sql.contains("ORDER BY sequence_num DESC"), sql);
            assertTrue(sql.contains("LIMIT 1"), sql);
            return (currentRead ? currentPrevious : snapshotPrevious).stream()
                .filter(row -> row.getCardId().equals(card) && row.getSequenceNum() < before)
                .max(Comparator.comparing(ProFlowCardStep::getSequenceNum)).orElse(null);
        });
        lenient().when(ruleMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            Object stage = parameter(query, "check_stage", "=");
            assertEquals(PROCESS_ID, parameter(query, "process_id", "="));
            return rules.stream().filter(rule -> rule.getCheckStage().equals(stage)).toList();
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
    void writeExitUsesCurrentPlannedQuantityRatherThanSnapshot() {
        snapshotStep.setPlannedQty(new BigDecimal("9"));
        quantityRule();

        ProCheckResultVo result = inContext("mes", true, false,
            () -> service.runExitChecks(STEP_ID, exit("10")));

        assertTrue(result.getPassed(), "写事务必须按当前计划量 10 校验，不能沿用快照计划量 9");
        assertTrue(result.getFailures().isEmpty());
        verify(stepMapper).selectById(STEP_ID);
        verify(stepMapper).selectOne(any());
    }

    @Test
    void writeExitRejectsQuantityOnlyBalancedAgainstOldPlan() {
        snapshotStep.setPlannedQty(new BigDecimal("9"));
        quantityRule();

        ProCheckResultVo result = inContext("mes", true, false,
            () -> service.runExitChecks(STEP_ID, exit("9")));

        assertFalse(result.getPassed(), "只满足旧计划量的出站请求必须被当前计划量拦截");
        assertEquals(IProEntryCheckService.TYPE_QUANTITY_REACHED,
            result.getFailures().get(0).getCheckType());
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLETED, IN_PROGRESS, true",
        "SKIPPED, PENDING, true",
        "IN_PROGRESS, COMPLETED, false",
        "PENDING, SKIPPED, false"
    })
    void writeSerialGateUsesFreshPreviousStatus(String freshStatus, String oldStatus, boolean passed) {
        currentPrevious.get(0).setStepStatus(freshStatus);
        snapshotPrevious.get(0).setStepStatus(oldStatus);

        ProCheckResultVo result = inContext("mes", true, false,
            () -> service.runEntryChecks(STEP_ID, entry()));

        assertEquals(passed, result.getPassed(), "串行门禁必须根据前序当前状态决定放行或拒绝");
        if (passed) {
            assertTrue(result.getFailures().isEmpty());
        } else {
            assertEquals(1, result.getFailures().size());
            assertEquals(IProEntryCheckService.TYPE_SERIAL_GATE, result.getFailures().get(0).getCheckType());
        }
        verify(stepMapper).selectById(STEP_ID);
        verify(stepMapper, times(2)).selectOne(any());
    }

    @Test
    void changedParentReferenceRejectsWriteBeforeAnyLockingStepRead() {
        snapshotStep.setCardId(999L);
        assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> inContext("mes", true, false, () -> service.runEntryChecks(STEP_ID, entry())));
        verify(stepMapper).selectById(STEP_ID);
        verify(stepMapper, never()).selectOne(any());
        verifyNoInteractions(ruleMapper);
    }

    @Test
    void writeEntryUsesFreshSequenceWithinVerifiedParent() {
        // 父引用不变；仅执行序号/状态的旧视图不能被用作业务门禁事实。
        snapshotStep.setSequenceNum(10);
        currentPrevious = new ArrayList<>(List.of(
            step(101L, CARD_ID, 10, "COMPLETED"),
            step(202L, CARD_ID, 20, "IN_PROGRESS"),
            step(999L, 999L, 25, "COMPLETED"),
            step(998L, 999L, 5, "COMPLETED")));

        ProCheckResultVo result = inContext("mes", true, false,
            () -> service.runEntryChecks(STEP_ID, entry()));

        assertFalse(result.getPassed(), "不能使用旧卡归属、其他卡的前序或序号 10 的已完工记录绕过序号 20");
        assertTrue(result.getFailures().get(0).getErrorMsg().contains("工序-20"));
        ArgumentCaptor<Wrapper<ProFlowCardStep>> queries = stepQueries();
        assertEquals(CARD_ID, parameter(queries.getAllValues().get(1), "card_id", "="));
        assertEquals(30, parameter(queries.getAllValues().get(1), "sequence_num", "<"));
    }

    @ParameterizedTest
    @CsvSource({
        "mes, false, false",
        "mes, true, true",
        "erp, true, false",
        "NONE, true, false"
    })
    void queryAndReadOnlyContextsKeepSnapshotReadsWithoutWriteLocks(
        String source, boolean actualTransaction, boolean readOnly) {
        snapshotStep.setPlannedQty(new BigDecimal("9"));
        quantityRule();

        inContext(source, actualTransaction, readOnly, () -> {
            ProCheckResultVo entry = service.runEntryChecks(STEP_ID, entry());
            ProCheckResultVo exit = service.runExitChecks(STEP_ID, exit("9"));
            assertFalse(entry.getPassed(), "查询路径保留普通前序读，不能因校验取得写锁");
            assertTrue(exit.getPassed(), "查询路径保留按快照计划量校验");
            return null;
        });

        verify(stepMapper, times(2)).selectById(STEP_ID);
        ArgumentCaptor<Wrapper<ProFlowCardStep>> queries = stepQueries();
        assertEquals(1, queries.getAllValues().size());
        assertFalse(queries.getValue().getSqlSegment().contains("FOR UPDATE"));
    }

    @Test
    void synchronizationWithoutPhysicalTransactionDoesNotEnableWriteLocks() {
        DynamicDataSourceContextHolder.push("mes");
        TransactionSynchronizationManager.initSynchronization();
        try {
            ProCheckResultVo result = service.runEntryChecks(STEP_ID, entry());
            assertFalse(result.getPassed());
            verify(stepMapper).selectById(STEP_ID);
            assertFalse(stepQueries().getValue().getSqlSegment().contains("FOR UPDATE"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    void firstStepWithoutEarlierSequencePassesInWriteTransaction() {
        currentStep.setSequenceNum(10);
        currentPrevious.clear();

        ProCheckResultVo result = inContext("mes", true, false,
            () -> service.runEntryChecks(STEP_ID, entry()));

        assertTrue(result.getPassed());
        verify(stepMapper, times(2)).selectOne(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"card", "sequence"})
    void queryWithMissingSerialCoordinatesDoesNotReadUnrelatedPreviousSteps(String missing) {
        if ("card".equals(missing)) {
            currentStep.setCardId(null);
        } else {
            currentStep.setSequenceNum(null);
        }

        snapshotStep = copy(currentStep);
        var queryContext = entry();
        // 此例验证无父卡输入的旧查询兼容；显式指定父卡时应由独立来源绑定回归拒绝错配。
        if ("card".equals(missing)) queryContext.setCardId(null);
        ProCheckResultVo result = inContext("mes", false, false,
            () -> service.runEntryChecks(STEP_ID, queryContext));

        assertTrue(result.getPassed());
        verify(stepMapper).selectById(STEP_ID);
        verify(stepMapper, never()).selectOne(any());
        verifyNoInteractions(andonMapper, qcIpqcMapper);
    }

    @Test
    void missingCurrentStepFailsBeforeRulesAreEvaluated() {
        currentStep = null;

        assertThrows(RuntimeException.class, () -> inContext("mes", true, false,
            () -> service.runExitChecks(STEP_ID, exit("10"))));

        verifyNoInteractions(ruleMapper);
    }

    private <T> T inContext(String source, boolean actualTransaction, boolean readOnly, Supplier<T> action) {
        boolean selected = !"NONE".equals(source);
        if (selected) {
            DynamicDataSourceContextHolder.push(source);
        }
        try {
            if (!actualTransaction) {
                return action.get();
            }
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setReadOnly(readOnly);
            return transaction.execute(status -> action.get());
        } finally {
            if (selected) {
                DynamicDataSourceContextHolder.poll();
            }
        }
    }

    private void quantityRule() {
        var rule = new ProStepCheckRule();
        rule.setProcessId(PROCESS_ID);
        rule.setCheckStage(IProEntryCheckService.STAGE_EXIT);
        rule.setCheckType(IProEntryCheckService.TYPE_QUANTITY_REACHED);
        rules = List.of(rule);
    }

    private ProEntryBo entry() {
        var context = new ProEntryBo();
        context.setCardId(CARD_ID);
        return context;
    }
    private ProExitBo exit(String good) {
        var context = new ProExitBo();
        context.setCardId(CARD_ID);
        context.setGoodQty(new BigDecimal(good));
        return context;
    }

    private static ProFlowCardStep step(long id, long card, int sequence, String status) {
        var step = new ProFlowCardStep();
        step.setStepRecordId(id);
        step.setTenantId("000000");
        step.setDelFlag("0");
        step.setMesWoId(500L);
        step.setCardId(card);
        step.setSequenceNum(sequence);
        step.setProcessId(PROCESS_ID);
        step.setProcessName("工序-" + sequence);
        step.setStepStatus(status);
        step.setPlannedQty(BigDecimal.TEN);
        return step;
    }

    private static ProFlowCardStep copy(ProFlowCardStep source) {
        var target = new ProFlowCardStep();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Wrapper<ProFlowCardStep>> stepQueries() {
        ArgumentCaptor<Wrapper<ProFlowCardStep>> queries = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(stepMapper, atLeastOnce()).selectOne(queries.capture());
        return queries;
    }

    /** 同时核对列、操作符和真实绑定值，避免只检查 SQL 中是否出现某个单词。 */
    private static Object parameter(Wrapper<?> wrapper, String column, String operator) {
        String sql = wrapper.getSqlSegment();
        var matcher = Pattern.compile("\\b" + Pattern.quote(column) + "\\s*" + Pattern.quote(operator)
            + "\\s*#\\{ew\\.paramNameValuePairs\\.([^},]+)(?:,[^}]+)?}").matcher(sql);
        assertTrue(matcher.find(), "缺少查询约束: " + column + " " + operator + ": " + sql);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().get(matcher.group(1));
    }
}
