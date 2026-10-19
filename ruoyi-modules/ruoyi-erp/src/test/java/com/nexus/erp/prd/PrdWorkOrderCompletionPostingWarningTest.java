package com.nexus.erp.prd;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.erp.prd.service.PrdWorkOrderTrackingPolicyService;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 完工事实与正常领料过账状态分离的回归测试，不构造库存、不推断仓库或 BOM 单位。
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class PrdWorkOrderCompletionPostingWarningTest {

    private static final Long WO_ID = 2104510684576272385L;
    private static final Long ISSUE_ID = 2104803728093540353L;
    private static final BigDecimal COMPLETION_QTY = new BigDecimal("10.0000");

    @Mock PrdWorkOrderMapper workOrderMapper;
    @Mock WmProductionIssueMapper issueMapper;
    @Mock WmProductionIssueLineMapper issueLineMapper;
    @Mock IWmStockOperationService stockOperationService;
    @Mock ReliableMessageProducer messageProducer;
    @Mock PrdWorkOrderTrackingPolicyService trackingPolicies;
    @InjectMocks PrdWorkOrderServiceImpl service;

    private final Logger logger = (Logger) LoggerFactory.getLogger(PrdWorkOrderServiceImpl.class);
    private ListAppender<ILoggingEvent> logs;
    private Level previousLevel;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
    }

    @BeforeEach
    void captureWarnings() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(logs);
        logs.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void normalCompletionWarnsForCreatedIssueWithoutInventingWarehouseOrPostingMaterials() {
        prepareWorkOrder(0);
        WmProductionIssue issue = pendingIssue("CREATED");
        when(issueMapper.selectList(any())).thenReturn(List.of(issue));

        complete();

        String warning = onlyWarning();
        assertTrue(warning.contains("生产已完工但领料尚未完成过账"), warning);
        assertTrue(warning.contains("woId=" + WO_ID), warning);
        assertTrue(warning.contains("issueId=" + ISSUE_ID), warning);
        assertTrue(warning.contains("issueCode=SCLL202609290001"), warning);
        assertTrue(warning.contains("issueStatus=CREATED"), warning);
        assertTrue(warning.contains("warehouseId=null"), warning);
        assertTrue(warning.contains("BOM单位、数量"), warning);
        assertTrue(warning.contains("不会自动倒冲"), warning);
        assertEquals("CREATED", issue.getStatus());
        assertNull(issue.getWarehouseId());
        verifyNoInteractions(issueLineMapper, stockOperationService);
        verify(issueMapper).selectList(any());
        verifyNoMoreInteractions(issueMapper);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<PrdWorkOrder>> update =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(workOrderMapper).update(isNull(), update.capture());
        assertTrue(
                update.getValue().getParamNameValuePairs().containsValue("COMPLETED"),
                "告警不应阻断生产完工事实回写");
        assertTrue(
                update.getValue().getSqlSet().contains("completed_qty = completed_qty + 10.0000"));

        ArgumentCaptor<ProductionCompleteMessage> event =
                ArgumentCaptor.forClass(ProductionCompleteMessage.class);
        verify(messageProducer)
                .send(
                        eq(ExchangeConstants.TOPIC_EXCHANGE),
                        eq(RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH),
                        event.capture());
        assertEquals(COMPLETION_QTY, event.getValue().getCompletionQty(), "告警不得改写 MES 完工数量");
        assertEquals(WO_ID, event.getValue().getWoId());
    }

    @Test
    void partiallyPostedIssueStillWarnsWithoutReopeningOrCompletingIt() {
        prepareWorkOrder(0);
        WmProductionIssue issue = pendingIssue("PARTIAL");
        when(issueMapper.selectList(any())).thenReturn(List.of(issue));

        complete();

        assertTrue(onlyWarning().contains("issueStatus=PARTIAL"));
        assertEquals("PARTIAL", issue.getStatus());
        verifyNoInteractions(issueLineMapper, stockOperationService);
        verify(issueMapper).selectList(any());
        verifyNoMoreInteractions(issueMapper);
    }

    @Test
    void missingBackflushFlagUsesTheSameNormalIssueWarning() {
        prepareWorkOrder(null);
        when(issueMapper.selectList(any())).thenReturn(List.of(pendingIssue("CREATED")));

        complete();

        assertTrue(onlyWarning().contains("issueId=" + ISSUE_ID));
        verifyNoInteractions(issueLineMapper, stockOperationService);
    }

    @Test
    void noPendingIssueDoesNotWarnAndQueryIsScopedToThisOrdersTenantAndIncompleteStatuses() {
        prepareWorkOrder(0);
        when(issueMapper.selectList(any())).thenReturn(List.of());

        complete();

        assertTrue(warnings().isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WmProductionIssue>> query =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(
                sql.contains("wo_id") && sql.contains("tenant_id") && sql.contains("status"), sql);
        var parameters = query.getValue().getParamNameValuePairs();
        assertTrue(parameters.containsValue(WO_ID));
        assertTrue(parameters.containsValue("000000"));
        assertTrue(parameters.containsValue("CREATED"));
        assertTrue(parameters.containsValue("PARTIAL"));
        assertEquals(4, parameters.size(), "已完成领料单不应被当作未完成告警候选");
        verifyNoInteractions(issueLineMapper, stockOperationService);
    }

    @Test
    void backflushOrderDoesNotQueryNormalIssuesAtCompletion() {
        prepareWorkOrder(1);

        complete();

        assertTrue(warnings().isEmpty());
        verifyNoInteractions(issueMapper, issueLineMapper, stockOperationService);
    }

    @Test
    void failedCompletionClaimDoesNotWarnOrEmitAnInventoryEvent() {
        prepareWorkOrder(0);
        when(workOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(
                RuntimeException.class,
                () -> {
                    service.complete(WO_ID, COMPLETION_QTY, BigDecimal.ZERO);
                });

        assertTrue(warnings().isEmpty());
        verifyNoInteractions(issueMapper, issueLineMapper, stockOperationService);
        verifyNoInteractions(messageProducer);
    }

    @Test
    void issueReadFailureOnlyWarnsAndStillWritesCompletionAndSendsMessage() {
        prepareWorkOrder(0);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("敏感数据库连接详情不得输出");
        when(issueMapper.selectList(any())).thenThrow(failure);

        assertDoesNotThrow(
                () -> {
                    complete();
                });

        String warning = assertCheckUnavailable("READ_CHECK_FAILED");
        assertTrue(warning.contains("errorType=DataAccessResourceFailureException"), warning);
        assertTrue(!warning.contains(failure.getMessage()), "无法核验告警不得带入原始异常正文");
        assertNull(
                logs.list.stream()
                        .filter(event -> event.getLevel() == Level.WARN)
                        .findFirst()
                        .orElseThrow()
                        .getThrowableProxy(),
                "固定告警不应附带可能泄露连接或 SQL 的原始异常");
        verifyCompletionWriteAndMessage();
        verifyNoInteractions(issueLineMapper, stockOperationService);
        verify(issueMapper).selectList(any());
        verifyNoMoreInteractions(issueMapper);
    }

    @Test
    void missingOrderTenantStillQueriesOnlyTheExplicitCurrentTenant() {
        PrdWorkOrder order = prepareWorkOrder(0);
        order.setTenantId(null);
        when(issueMapper.selectList(any())).thenReturn(List.of());

        assertDoesNotThrow(
                () -> {
                    complete("000123");
                });

        assertTrue(warnings().isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WmProductionIssue>> query =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"), "工单租户为空也必须保留显式租户条件: " + sql);
        var parameters = query.getValue().getParamNameValuePairs();
        assertTrue(parameters.containsValue("000123"), "仅使用已有 Tenant 上下文，不猜测或补写工单租户");
        assertTrue(!parameters.containsValue("000000"));
        assertEquals(4, parameters.size());
        assertNull(order.getTenantId());
        assertEquals(Long.valueOf(123L), verifyCompletionWriteAndMessage().getTenantId());
        verifyNoInteractions(issueLineMapper, stockOperationService);
    }

    @Test
    void missingTenantContextSkipsReadInsteadOfIssuingAnUnscopedQuery() {
        prepareWorkOrder(0);

        assertDoesNotThrow(
                () -> {
                    complete(null);
                });

        assertCheckUnavailable("TENANT_CONTEXT_MISSING");
        verifyCompletionWriteAndMessage();
        verifyNoInteractions(issueMapper, issueLineMapper, stockOperationService);
    }

    @Test
    void mismatchedOrderAndCurrentTenantsSkipTheAuxiliaryReadWithoutLosingCompletionMessage() {
        prepareWorkOrder(0);

        assertDoesNotThrow(
                () -> {
                    complete("000123");
                });

        assertCheckUnavailable("TENANT_MISMATCH");
        assertEquals(Long.valueOf(123L), verifyCompletionWriteAndMessage().getTenantId());
        verifyNoInteractions(issueMapper, issueLineMapper, stockOperationService);
    }

    @Test
    void primaryCompletionWriteFailureIsNotSwallowedByTheAuxiliaryWarning() {
        prepareWorkOrder(0);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("完工主写失败");
        when(workOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        DataAccessResourceFailureException.class,
                        () -> {
                            service.complete(WO_ID, COMPLETION_QTY, BigDecimal.ZERO);
                        }));

        assertTrue(warnings().isEmpty());
        verifyNoInteractions(issueMapper, issueLineMapper, stockOperationService, messageProducer);
    }

    @Test
    void completionMessageFailureIsNotSwallowedByTheAuxiliaryWarning() {
        prepareWorkOrder(0);
        when(issueMapper.selectList(any())).thenReturn(List.of());
        IllegalStateException failure = new IllegalStateException("完工消息发送失败");
        doThrow(failure)
                .when(messageProducer)
                .send(
                        eq(ExchangeConstants.TOPIC_EXCHANGE),
                        eq(RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH),
                        any(ProductionCompleteMessage.class));

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            complete();
                        }));

        assertTrue(warnings().isEmpty());
        verify(messageProducer)
                .send(
                        eq(ExchangeConstants.TOPIC_EXCHANGE),
                        eq(RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH),
                        any(ProductionCompleteMessage.class));
        verifyNoInteractions(issueLineMapper, stockOperationService);
    }

    private PrdWorkOrder prepareWorkOrder(Integer backflushFlag) {
        PrdWorkOrder order = new PrdWorkOrder();
        order.setWoId(WO_ID);
        order.setWoNo("SCGD202609280001");
        order.setWoStatus("IN_PROGRESS");
        order.setTenantId("000000");
        order.setPlannedQty(COMPLETION_QTY);
        order.setCompletedQty(BigDecimal.ZERO);
        order.setScrapQty(BigDecimal.ZERO);
        order.setBackflushFlag(backflushFlag);
        when(workOrderMapper.selectById(WO_ID)).thenReturn(order);
        return order;
    }

    private WmProductionIssue pendingIssue(String status) {
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueId(ISSUE_ID);
        issue.setIssueCode("SCLL202609290001");
        issue.setWoId(WO_ID);
        issue.setTenantId("000000");
        issue.setStatus(status);
        issue.setIssueType("NORMAL");
        return issue;
    }

    private void complete() {
        complete("000000");
    }

    /**
     * 仅供主写成功且需要读取租户的路径使用，前置失败测试直接调用被测服务。
     */
    private void complete(String currentTenantId) {
        when(workOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn(currentTenantId);
            service.complete(WO_ID, COMPLETION_QTY, BigDecimal.ZERO);
        }
    }

    private ProductionCompleteMessage verifyCompletionWriteAndMessage() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<PrdWorkOrder>> update =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(workOrderMapper).update(isNull(), update.capture());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("COMPLETED"));
        assertTrue(
                update.getValue().getSqlSet().contains("completed_qty = completed_qty + 10.0000"));
        ArgumentCaptor<ProductionCompleteMessage> event =
                ArgumentCaptor.forClass(ProductionCompleteMessage.class);
        verify(messageProducer)
                .send(
                        eq(ExchangeConstants.TOPIC_EXCHANGE),
                        eq(RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH),
                        event.capture());
        assertEquals(WO_ID, event.getValue().getWoId());
        assertEquals(COMPLETION_QTY, event.getValue().getCompletionQty());
        return event.getValue();
    }

    private String assertCheckUnavailable(String reason) {
        String warning = onlyWarning();
        assertTrue(warning.startsWith("PRODUCTION_MATERIAL_CHECK_UNAVAILABLE:"), warning);
        assertTrue(warning.contains("无法核验正常领料过账状态"), warning);
        assertTrue(warning.contains("reason=" + reason), warning);
        assertTrue(warning.contains("woId=" + WO_ID), warning);
        return warning;
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private String onlyWarning() {
        List<String> warnings = warnings();
        assertEquals(1, warnings.size(), "正常领料未过账时必须有明确告警，不能只有生产完工成功日志");
        return warnings.get(0);
    }
}
