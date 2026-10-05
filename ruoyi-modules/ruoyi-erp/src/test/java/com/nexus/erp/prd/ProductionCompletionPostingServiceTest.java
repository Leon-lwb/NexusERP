package com.nexus.erp.prd;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.erp.mq.consumer.ProductionBackflushConsumer;
import com.rabbitmq.client.Channel;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.ProductionCompletionPostingService;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.impl.WmProductionRecptServiceImpl;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 完工持久守卫和真实 Spring 代理事务测试。
 * JDBC 连接与持久层均为模拟对象，不启动应用、不访问任何库/Redis/业务服务；
 * 回滚断言验证 Spring 事务边界，不冒充物理数据库回滚验收。
 */
class ProductionCompletionPostingServiceTest {
    private static final String TENANT = "000007";
    private static final BigDecimal TEN = new BigDecimal("10");
    private ProductionCompletionPostingMapper mapper;
    private PrdBackflushServiceImpl backflush;
    private WmProductionRecptServiceImpl receipt;
    private IPutawayStrategyService putaway;
    private ProductionCompletionPostingService target;
    private ProductionCompletionPostingService service;
    private PrdWorkOrder workOrder;
    private ProductionCompleteMessage message;
    private PutawayLocation location;
    private Connection connection;
    private DataSource dataSource;
    private MockedStatic<TenantHelper> tenant;
    private final List<WmProductionRecpt> receipts = new ArrayList<>();
    private final List<WmMaterialStockTx> transactions = new ArrayList<>();
    private WmMaterialStock stock;
    private int pendingDebits;
    private int committedDebits;
    private List<WmProductionRecpt> beforeReceipts;
    private List<WmMaterialStockTx> beforeTransactions;

    @BeforeEach
    void setUp() throws Exception {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        mapper = mock(ProductionCompletionPostingMapper.class);
        backflush = mock(PrdBackflushServiceImpl.class);
        receipt = mock(WmProductionRecptServiceImpl.class);
        putaway = mock(IPutawayStrategyService.class);
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        // 内层也使用既有方法注解的真实代理，断言 REQUIRED 共享同一个模拟 JDBC 连接。
        target = new ProductionCompletionPostingService(mapper,
            proxy(backflush, manager), putaway, proxy(receipt, manager));
        service = proxy(target, manager);
        doAnswer(inv -> {
            if (!inv.getArgument(0, Boolean.class)) {
                beforeReceipts = new ArrayList<>(receipts);
                beforeTransactions = new ArrayList<>(transactions);
            }
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        doAnswer(inv -> {
            committedDebits += pendingDebits;
            pendingDebits = 0;
            return null;
        }).when(connection).commit();
        doAnswer(inv -> {
            pendingDebits = 0;
            receipts.clear();
            receipts.addAll(beforeReceipts);
            transactions.clear();
            transactions.addAll(beforeTransactions);
            return null;
        }).when(connection).rollback();

        workOrder = new PrdWorkOrder();
        workOrder.setTenantId(TENANT);
        workOrder.setDelFlag("0");
        workOrder.setWoId(100L);
        workOrder.setWoNo("WO-100");
        workOrder.setMaterialId(200L);
        workOrder.setMaterialCode("FG-200");
        workOrder.setMaterialName("测试成品");
        workOrder.setMaterialSpec("真实规格");
        workOrder.setPlannedQty(TEN);
        workOrder.setCompletedQty(TEN);
        workOrder.setWoStatus("COMPLETED");
        workOrder.setActualEndDate(new Date());
        workOrder.setBackflushFlag(1);
        message = new ProductionCompleteMessage();
        message.setTenantId(7L);
        message.setMessageId("completion-original");
        message.setWoId(100L);
        message.setWoNo("WO-100");
        message.setMaterialId(200L);
        message.setMaterialCode("FG-200");
        message.setMaterialName("测试成品");
        message.setCompletionQty(TEN);
        location = new PutawayLocation();
        location.setWarehouseId(300L);
        location.setWarehouseCode("FG-WH");
        location.setWarehouseName("成品仓");
        location.setLocationId(400L);
        location.setLocationCode("FG-LOC");
        location.setLocationName("成品位");
        when(mapper.lockWorkOrder(TENANT, 100L)).thenReturn(workOrder);
        when(mapper.selectApprovedReportedQty(TENANT, 100L)).thenReturn(BigDecimal.ZERO);
        when(mapper.selectReceipts(TENANT, 100L)).thenAnswer(inv -> new ArrayList<>(receipts));
        when(mapper.selectCompletionTransactions(TENANT, 100L)).thenAnswer(inv -> new ArrayList<>(transactions));
        when(mapper.selectStock(TENANT, 902L)).thenAnswer(inv -> stock);
        when(putaway.resolvePutaway(200L, TEN)).thenReturn(location);
        doAnswer(inv -> {
            assertSameConnection();
            pendingDebits += 10;
            return null;
        }).when(backflush).backflushOnCompletion(100L, TEN);
        when(receipt.createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            assertSameConnection();
            evidence(inv.getArgument(6));
            return 900L;
        });
    }

    @AfterEach
    void tearDown() {
        tenant.close();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
    }

    /** 此方法只构建代理，没有启动 Spring 容器或 JDBC 服务。 */
    @SuppressWarnings("unchecked")
    private <T> T proxy(T bean, DataSourceTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private void assertSameConnection() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        assertNotNull(holder);
        assertSame(connection, holder.getConnection());
    }

    /** 构造本次事务内真实形状的生产单、流水和库存维度，不接触持久化设施。 */
    private void evidence(BigDecimal quantity) {
        WmProductionRecpt r = new WmProductionRecpt();
        r.setTenantId(TENANT); r.setDelFlag("0"); r.setRecptId(900L); r.setWoId(100L); r.setWoNo("WO-100");
        r.setMaterialId(200L); r.setMaterialCode("FG-200"); r.setQuantity(quantity); r.setStatus("EXECUTED");
        r.setBatchId(500L); r.setBatchCode("BATCH-500"); r.setWarehouseId(300L); r.setLocationId(400L);
        receipts.add(r);
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTenantId(TENANT); tx.setDelFlag("0"); tx.setTxId(901L);
        tx.setSourceDocType("PRD_WORK_ORDER"); tx.setSourceDocId(100L); tx.setTxType("PRODUCTION_IN");
        tx.setMaterialId(200L); tx.setMaterialCode("FG-200"); tx.setTxFlag(1); tx.setTxQuantity(quantity);
        tx.setBatchId(500L); tx.setBatchCode("BATCH-500"); tx.setWarehouseId(300L); tx.setLocationId(400L);
        tx.setMaterialStockId(902L); tx.setBeforeQuantity(BigDecimal.ZERO); tx.setAfterQuantity(quantity);
        tx.setTxDate(new Date()); tx.setPosted(0);
        transactions.add(tx);
        stock = new WmMaterialStock();
        stock.setTenantId(TENANT); stock.setDelFlag("0"); stock.setStockId(902L);
        stock.setWoId(100L); stock.setMaterialId(200L); stock.setMaterialCode("FG-200");
        stock.setBatchId(500L); stock.setBatchCode("BATCH-500"); stock.setWarehouseId(300L); stock.setLocationId(400L);
        stock.setStockQuantity(quantity);
    }

    @Test
    void receiptFailureMustRollbackBeforeRetryAndOnlyCommitBackflushOnce() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(inv -> {
            assertSameConnection();
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("入库失败");
            evidence(TEN); return 900L;
        }).when(receipt).createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.postCompletion(message));
        assertEquals(0, committedDebits);
        assertEquals(0, pendingDebits);
        assertTrue(receipts.isEmpty());
        verify(connection).rollback();
        verify(connection, never()).commit();
        service.postCompletion(message);
        assertEquals(10, committedDebits, "两次调用只允许提交一次倒冲");
        assertEquals(2, attempts.get());
        assertEquals(1, receipts.size());
        assertEquals(1, transactions.size());
        verify(connection).commit();
        verify(backflush, times(2)).backflushOnCompletion(100L, TEN);
    }

    @Test
    void putawayFailureMustRollbackAndRemainRetryable() throws Exception {
        when(putaway.resolvePutaway(200L, TEN)).thenThrow(new IllegalStateException("无成品上架规则")).thenReturn(location);
        assertThrows(IllegalStateException.class, () -> service.postCompletion(message));
        assertEquals(0, committedDebits);
        assertEquals(0, pendingDebits);
        verifyNoInteractions(receipt);
        service.postCompletion(message);
        assertEquals(10, committedDebits);
        verify(connection).rollback();
        verify(connection).commit();
    }

    @Test
    void successfulReplayWithDifferentMessageIdAndDecimalScaleMustUseDurableGuard() {
        service.postCompletion(message);
        message.setMessageId("completion-another");
        message.setCompletionQty(new BigDecimal("10.0"));
        service.postCompletion(message);
        assertEquals(10, committedDebits);
        assertEquals(1, receipts.size());
        verify(backflush).backflushOnCompletion(100L, TEN);
        verify(receipt).createAndExecute(eq(100L), eq("WO-100"), eq(200L), eq("FG-200"), eq("测试成品"), eq("真实规格"), eq(TEN),
            eq(300L), eq("FG-WH"), eq("成品仓"), eq(400L), eq("FG-LOC"), eq("成品位"), any(Date.class));
    }

    @Test
    void reportContributionsMustNotTurnThisCompletionQuantityIntoCumulativeQuantity() {
        workOrder.setCompletedQty(new BigDecimal("17"));
        when(mapper.selectApprovedReportedQty(TENANT, 100L)).thenReturn(new BigDecimal("7"));
        service.postCompletion(message);
        assertEquals(0, receipts.get(0).getQuantity().compareTo(TEN));
        verify(backflush).backflushOnCompletion(100L, TEN);
    }

    @Test
    void forgedPartialQuantityCannotCompleteARealTenUnitWorkOrder() {
        message.setCompletionQty(new BigDecimal("5"));
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void oldIncrementalReceiptCannotSkipHasPostedAndReturnFalseSuccess() {
        evidence(new BigDecimal("5"));
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void legacyBackflushWithoutProductionReceiptMustStopBeforeSecondDebit() {
        when(mapper.countExistingBackflushIssues(TENANT, 100L)).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void noOpReceiptOrSilentlySkippedStrategyCannotCommitSuccess() throws Exception {
        doReturn(900L).when(receipt).createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        assertEquals(0, committedDebits);
        verify(connection).rollback();
    }

    @Test
    void returnedReceiptIdMustIdentifyVerifiedReceipt() {
        doAnswer(inv -> { evidence(TEN); return 999L; }).when(receipt).createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        assertEquals(0, committedDebits);
    }

    @Test
    void receiptOnlyStateMustNotBeAcceptedOrBackflushedAgain() {
        evidence(TEN); transactions.clear();
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void transactionOnlyStateMustNotBeAcceptedOrBackflushedAgain() {
        evidence(TEN); receipts.clear();
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void currentStockCanBeConsumedLaterAndCostPostingZeroIsNotAnInventoryFailure() {
        evidence(TEN); stock.setStockQuantity(new BigDecimal("2"));
        assertDoesNotThrow(() -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void directCallWithoutSpringTransactionMustBeRejected() {
        assertThrows(ServiceException.class, () -> target.postCompletion(message));
        verifyNoInteractions(mapper, backflush, receipt, putaway);
    }

    @Test
    void invalidPutawayDimensionsMustNotCreateReceipt() {
        location.setLocationId(null);
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(receipt);
        assertEquals(0, committedDebits);
    }

    enum InvalidMessage {
        MISSING_TENANT, NEGATIVE_TENANT, OTHER_TENANT, WRONG_CONTEXT, WRONG_WORK_ORDER, WRONG_WORK_ORDER_NUMBER,
        WRONG_MATERIAL, WRONG_MATERIAL_CODE, WRONG_MATERIAL_NAME, ZERO_QTY, NEGATIVE_QTY, MISSING_QTY,
        UNCOMPLETED_WORK_ORDER, NO_COMPLETION_DATE, DELETED_WORK_ORDER, WRONG_REAL_TENANT, NULL_COMPLETED_QTY, EXCESS_REPORT_QTY
    }

    @ParameterizedTest
    @EnumSource(InvalidMessage.class)
    void messageMustMatchRealCompletedWorkOrder(InvalidMessage invalid) {
        switch (invalid) {
            case MISSING_TENANT -> message.setTenantId(null);
            case NEGATIVE_TENANT -> message.setTenantId(-7L);
            case OTHER_TENANT -> message.setTenantId(8L);
            case WRONG_CONTEXT -> tenant.when(TenantHelper::getTenantId).thenReturn("000008");
            case WRONG_WORK_ORDER -> message.setWoId(101L);
            case WRONG_WORK_ORDER_NUMBER -> message.setWoNo("FORGED");
            case WRONG_MATERIAL -> message.setMaterialId(201L);
            case WRONG_MATERIAL_CODE -> message.setMaterialCode("FORGED");
            case WRONG_MATERIAL_NAME -> message.setMaterialName("伪造成品");
            case ZERO_QTY -> message.setCompletionQty(BigDecimal.ZERO);
            case NEGATIVE_QTY -> message.setCompletionQty(BigDecimal.ONE.negate());
            case MISSING_QTY -> message.setCompletionQty(null);
            case UNCOMPLETED_WORK_ORDER -> workOrder.setWoStatus("RELEASED");
            case NO_COMPLETION_DATE -> workOrder.setActualEndDate(null);
            case DELETED_WORK_ORDER -> workOrder.setDelFlag("2");
            case WRONG_REAL_TENANT -> workOrder.setTenantId("000008");
            case NULL_COMPLETED_QTY -> workOrder.setCompletedQty(null);
            case EXCESS_REPORT_QTY -> when(mapper.selectApprovedReportedQty(TENANT, 100L)).thenReturn(new BigDecimal("11"));
        }
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    enum InvalidEvidence {
        RECEIPT_NOT_EXECUTED, RECEIPT_DELETED, RECEIPT_TENANT, RECEIPT_MATERIAL, RECEIPT_WORK_ORDER, RECEIPT_QTY,
        TX_DELETED, TX_TENANT, TX_TYPE, TX_FLAG, TX_QTY, TX_BALANCE, TX_STOCK_MISSING, TX_BATCH,
        TX_BATCH_CODE, TX_WAREHOUSE, TX_LOCATION, TX_DATE_MISSING, TX_SOURCE, TX_MATERIAL,
        STOCK_DELETED, STOCK_TENANT, STOCK_BATCH, STOCK_LOCATION, MULTIPLE_RECEIPTS, MULTIPLE_TRANSACTIONS
    }

    @ParameterizedTest
    @EnumSource(InvalidEvidence.class)
    void halfPostingOrConflictingEvidenceMustNeverReturnSuccess(InvalidEvidence invalid) {
        evidence(TEN);
        WmProductionRecpt r = receipts.get(0); WmMaterialStockTx tx = transactions.get(0);
        switch (invalid) {
            case RECEIPT_NOT_EXECUTED -> r.setStatus("DRAFT");
            case RECEIPT_DELETED -> r.setDelFlag("2");
            case RECEIPT_TENANT -> r.setTenantId("000008");
            case RECEIPT_MATERIAL -> r.setMaterialId(201L);
            case RECEIPT_WORK_ORDER -> r.setWoId(101L);
            case RECEIPT_QTY -> r.setQuantity(new BigDecimal("9"));
            case TX_DELETED -> tx.setDelFlag("2");
            case TX_TENANT -> tx.setTenantId("000008");
            case TX_TYPE -> tx.setTxType("PRODUCTION_OUT");
            case TX_FLAG -> tx.setTxFlag(-1);
            case TX_QTY -> tx.setTxQuantity(new BigDecimal("9"));
            case TX_BALANCE -> tx.setAfterQuantity(new BigDecimal("9"));
            case TX_STOCK_MISSING -> stock = null;
            case TX_BATCH -> tx.setBatchId(501L);
            case TX_BATCH_CODE -> tx.setBatchCode("FORGED");
            case TX_WAREHOUSE -> tx.setWarehouseId(301L);
            case TX_LOCATION -> tx.setLocationId(401L);
            case TX_DATE_MISSING -> tx.setTxDate(null);
            case TX_SOURCE -> tx.setSourceDocId(101L);
            case TX_MATERIAL -> tx.setMaterialId(201L);
            case STOCK_DELETED -> stock.setDelFlag("2");
            case STOCK_TENANT -> stock.setTenantId("000008");
            case STOCK_BATCH -> stock.setBatchId(501L);
            case STOCK_LOCATION -> stock.setLocationId(401L);
            case MULTIPLE_RECEIPTS -> receipts.add(r);
            case MULTIPLE_TRANSACTIONS -> transactions.add(tx);
        }
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        verifyNoInteractions(backflush, receipt, putaway);
    }

    @Test
    void conflictingReplayMustNotEraseCommittedSuccessOrTriggerAnotherBackflush() {
        service.postCompletion(message);
        message.setCompletionQty(new BigDecimal("5"));
        assertThrows(ServiceException.class, () -> service.postCompletion(message));
        assertEquals(1, receipts.size(), "后续冲突事务回滚不能抹掉先前已提交的成功凭据");
        message.setCompletionQty(new BigDecimal("10.0"));
        service.postCompletion(message);
        assertEquals(10, committedDebits);
        verify(backflush).backflushOnCompletion(100L, TEN);
    }

    @Test
    void realConsumerMustNackOnlyAfterRollbackAndAckOnlyAfterVerifiedCommit() throws Exception {
        // 静态工具初始化只注入模拟客户端，绝不创建 Redis 连接。
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            Class.forName(RedisUtils.class.getName());
        }
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(inv -> {
            assertSameConnection();
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("模拟入库失败");
            evidence(TEN); return 900L;
        }).when(receipt).createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        Map<String, Object> cache = new HashMap<>();
        Channel channel = mock(Channel.class);
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, committedDebits);
            assertTrue(receipts.isEmpty());
            verify(connection).rollback();
            return null;
        }).when(channel).basicNack(11L, false, true);
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(10, committedDebits);
            assertEquals(1, receipts.size());
            assertEquals(1, transactions.size());
            return null;
        }).when(channel).basicAck(11L, false);
        tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        MessageProperties properties = new MessageProperties(); properties.setDeliveryTag(11L);
        Message amqp = new Message(new byte[0], properties);
        ProductionBackflushConsumer consumer = new ProductionBackflushConsumer(service);
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(anyString())).thenAnswer(inv -> cache.get(inv.getArgument(0)));
            redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class)))
                .thenAnswer(inv -> cache.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
            redis.when(() -> RedisUtils.setCacheObject(anyString(), any(), any(Duration.class)))
                .thenAnswer(inv -> { cache.put(inv.getArgument(0), inv.getArgument(1)); return null; });
            redis.when(() -> RedisUtils.deleteObject(anyString())).thenAnswer(inv -> cache.remove(inv.getArgument(0)) != null);
            redis.when(() -> RedisUtils.incrAtomicValue(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                long n = ((Number) cache.getOrDefault(key, 0L)).longValue() + 1;
                cache.put(key, n); return n;
            });
            consumer.onMessage(message, channel, amqp);
            verify(channel).basicNack(11L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            assertFalse(cache.containsValue("DONE"));
            consumer.onMessage(message, channel, amqp);
            assertTrue(cache.containsValue("DONE"));
            cache.clear();
            message.setMessageId("completion-cache-expired");
            message.setCompletionQty(new BigDecimal("10.0"));
            consumer.onMessage(message, channel, amqp);
            verify(channel, times(2)).basicAck(11L, false);
            verify(backflush, times(2)).backflushOnCompletion(100L, TEN);
            assertEquals(2, attempts.get(), "缓存失效与不同消息 ID 不能再次调用实际入库");
        }
    }

    @Test
    void mapperMustLockTenantWorkOrderAndNotHideDeletedOrReversePostingEvidence() throws Exception {
        String lock = sql("lockWorkOrder", String.class, Long.class);
        assertTrue(lock.contains("tenant_id = #{tenantid}"));
        assertTrue(lock.contains("wo_id = #{woid}"));
        assertTrue(lock.contains("for update"));
        for (String name : List.of("selectReceipts", "selectCompletionTransactions", "countExistingBackflushIssues", "selectApprovedReportedQty")) {
            String query = sql(name, String.class, Long.class);
            assertTrue(query.contains("tenant_id = #{tenantid}"));
            assertFalse(query.contains("del_flag"), "证据与报工不能过滤逻辑删除后假装从未过账或从未累加");
        }
        assertFalse(sql("selectCompletionTransactions", String.class, Long.class).contains("tx_flag = 1"));
        assertEquals("erp", ProductionCompletionPostingService.class.getAnnotation(DS.class).value());
    }

    private String sql(String method, Class<?>... types) throws Exception {
        return String.join(" ", ProductionCompletionPostingMapper.class.getMethod(method, types).getAnnotation(Select.class).value())
            .toLowerCase(java.util.Locale.ROOT);
    }
}
