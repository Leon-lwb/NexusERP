package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.impl.MovingAverageValuationServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 生产领料金额与批次累计回归：使用真实策略、真实移动平均计价和无外部资源的事务代理。
 * <p>Mapper 用内存账本替代；回滚断言仅证明异常和事务边界，不替代真实 MySQL 验收。</p>
 */
class ProductionIssueCostAndBatchStrategyTest {

    private static final Long ISSUE_ID = 2106995561414438913L;
    private static final Long LINE_ID = 2106995561481547778L;
    private static final Long MATERIAL_ID = 100L;
    private static final String TENANT_ID = "000000";
    private static final Date NOW = new Date(1791172800000L);

    private WmMaterialStockMapper stockMapper;
    private WmMaterialStockTxMapper txMapper;
    private WmBatchMapper batchMapper;
    private Ledger ledger;
    private Long blockedStock;
    private Long blockedBatch;
    private Long throwingTxStock;
    private int txInsertResult;
    private final List<BatchCall> batchCalls = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
    }

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        txInsertResult = 1;
        stockMapper = mock(WmMaterialStockMapper.class);
        txMapper = mock(WmMaterialStockTxMapper.class);
        // 记录真实 Mapper 调用并模拟原子更新结果，不读取外部数据库。
        batchMapper = mock(WmBatchMapper.class, invocation -> {
            if (!"increaseIssuedQty".equals(invocation.getMethod().getName())) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            BatchCall call = new BatchCall(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3));
            batchCalls.add(call);
            if (Objects.equals(call.batchId(), blockedBatch)) {
                return 0;
            }
            BatchState batch = ledger.batches.get(call.batchId());
            if (batch == null || !Objects.equals(batch.materialId(), call.materialId())
                || !Objects.equals(batch.tenantId(), call.tenantId()) || call.quantity().signum() <= 0) {
                return 0;
            }
            BigDecimal issued = batch.issued().add(call.quantity());
            if (issued.compareTo(batch.received()) > 0) {
                return 0;
            }
            ledger.batches.put(call.batchId(), new BatchState(batch.materialId(), batch.tenantId(),
                batch.received(), issued));
            return 1;
        });
        when(stockMapper.selectById(anyLong())).thenAnswer(invocation -> ledger.stock(invocation.getArgument(0)));
        when(stockMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> ledger.stocks.keySet().stream()
            .map(ledger::stock).toList());
        when(stockMapper.deductStockAvailable(anyLong(), any(BigDecimal.class), anyInt())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            BigDecimal quantity = invocation.getArgument(1);
            Integer version = invocation.getArgument(2);
            StockState stock = ledger.stocks.get(id);
            if (Objects.equals(id, blockedStock) || !Objects.equals(stock.version(), version)
                || stock.quantity().compareTo(quantity) < 0) {
                return 0;
            }
            ledger.stocks.put(id, stock.withQuantityAndVersion(stock.quantity().subtract(quantity), version + 1));
            return 1;
        });
        when(stockMapper.issueStockCost(anyLong(), any(BigDecimal.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            BigDecimal quantity = invocation.getArgument(1);
            StockState stock = ledger.stocks.get(id);
            BigDecimal unitCost = stock.unitCost() == null ? BigDecimal.ZERO : stock.unitCost();
            BigDecimal totalCost = stock.totalCost().subtract(unitCost.multiply(quantity)).max(BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
            ledger.stocks.put(id, stock.withTotalCost(totalCost));
            return 1;
        });
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenAnswer(invocation -> {
            WmMaterialStockTx tx = invocation.getArgument(0);
            if (Objects.equals(tx.getMaterialStockId(), throwingTxStock)) {
                throw new ServiceException("模拟流水写入失败");
            }
            if (txInsertResult == 1) {
                ledger.transactions.add(tx);
            }
            return txInsertResult;
        });
    }

    @ParameterizedTest
    @CsvSource({"10.0000, 3.200000, 32.0000", "10.0000, 0.450000, 4.5000", "20.0000, 0.120000, 2.4000"})
    void fullIssuePostsObservedMovingAverageAmount(String quantity, String unitCost, String amount) {
        addStock(1L, null, quantity, unitCost, amount);

        strategy().executeAllocated(ISSUE_ID, LINE_ID, List.of(allocated(1L, quantity)), NOW);

        WmMaterialStockTx tx = ledger.transactions.get(0);
        assertEquals(decimal(unitCost), tx.getUnitCost());
        assertEquals(decimal(amount), tx.getTxAmount(), "整批扣减后 total_cost=0，流水仍须保留实际出库金额");
        assertEquals(BigDecimal.ZERO.setScale(4), ledger.stocks.get(1L).totalCost());
        assertEquals(0, ledger.stocks.get(1L).quantity().signum());
        assertEquals("PRODUCTION_ISSUE", tx.getTxType());
        assertEquals(-1, tx.getTxFlag());
        assertEquals(decimal(quantity), tx.getTxQuantity());
        assertEquals(decimal(quantity), tx.getBeforeQuantity());
        assertEquals(0, tx.getAfterQuantity().signum());
        assertEquals("WM_PRODUCTION_ISSUE", tx.getSourceDocType());
        assertEquals(ISSUE_ID, tx.getSourceDocId());
        assertEquals(LINE_ID, tx.getSourceDocLineId());
    }

    @ParameterizedTest
    @CsvSource({"1.0000, 0.000050, 0.0001"})
    void amountRoundsQuantityTimesUnitCostToFourCurrencyPlaces(String quantity, String unitCost, String amount) {
        addStock(1L, null, "10.0000", unitCost, "99.9999");

        strategy().executeAllocated(ISSUE_ID, LINE_ID, List.of(allocated(1L, quantity)), NOW);

        assertEquals(decimal(amount), ledger.transactions.get(0).getTxAmount());
        assertEquals(4, ledger.transactions.get(0).getTxAmount().scale());
    }

    @Test
    void fifoSplitAccumulatesEachActuallyDeductedBatch() {
        addStock(1L, 11L, "3.0000", "5.125001", "15.3750");
        addStock(2L, 12L, "4.0000", "0.500000", "2.0000");
        addBatch(11L, "3.0000", "0.0000");
        addBatch(12L, "4.0000", "0.0000");

        strategy().execute(line("5.0000"), NOW);

        assertEquals(decimal("3.0000"), ledger.batches.get(11L).issued());
        assertEquals(decimal("2.0000"), ledger.batches.get(12L).issued());
        assertEquals(List.of(new BatchCall(11L, MATERIAL_ID, TENANT_ID, decimal("3.0000")),
            new BatchCall(12L, MATERIAL_ID, TENANT_ID, decimal("2.0000"))), batchCalls);
        assertEquals(List.of(decimal("15.3750"), decimal("1.0000")),
            ledger.transactions.stream().map(WmMaterialStockTx::getTxAmount).toList());
    }

    @Test
    void allocatedIssueUsesStockBatchNotCallerBatch() {
        addStock(1L, 11L, "2.0000", "3.200000", "6.4000");
        addBatch(11L, "5.0000", "1.0000");
        AllocatedStock allocation = allocated(1L, "2.0000");
        allocation.setBatchId(999L);
        allocation.setMaterialId(999L);

        strategy().executeAllocated(ISSUE_ID, LINE_ID, List.of(allocation), NOW);

        assertEquals(List.of(new BatchCall(11L, MATERIAL_ID, TENANT_ID, decimal("2.0000"))), batchCalls);
        assertEquals(decimal("3.0000"), ledger.batches.get(11L).issued());
        assertEquals(11L, ledger.transactions.get(0).getBatchId());
    }

    @Test
    void nullBatchStockDoesNotRequireBatchUpdate() {
        addStock(1L, null, "2.0000", "0.450000", "0.9000");

        strategy().execute(line("2.0000"), NOW);

        assertTrue(batchCalls.isEmpty());
        verifyNoInteractions(batchMapper);
        assertNull(ledger.transactions.get(0).getBatchId());
        assertEquals(decimal("0.9000"), ledger.transactions.get(0).getTxAmount());
    }

    @Test
    void batchCapMissRollsBackWholeLineIncludingPreviousStock() {
        addTwoStocksAndBatches();
        ledger.batches.put(12L, new BatchState(MATERIAL_ID, TENANT_ID, decimal("1.0000"), decimal("0.0000")));
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);
        Snapshot before = ledger.snapshot();

        assertThrows(ServiceException.class, () -> transactionalStrategy(manager).executeAllocated(ISSUE_ID, LINE_ID,
            List.of(allocated(1L, "2.0000"), allocated(2L, "2.0000")), NOW));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }

    @Test
    void txInsertZeroRowsRollsBackStockCostAndBatch() {
        addStock(1L, 11L, "2.0000", "1.000000", "2.0000");
        addBatch(11L, "2.0000", "0.0000");
        txInsertResult = 0;
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);
        Snapshot before = ledger.snapshot();

        assertThrows(ServiceException.class, () -> transactionalStrategy(manager).execute(line("2.0000"), NOW));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
    }

    private ProductionIssueStrategy strategy() {
        return new ProductionIssueStrategy(txMapper, stockMapper, new MovingAverageValuationServiceImpl(stockMapper), batchMapper);
    }

    private ProductionIssueStrategy transactionalStrategy(SnapshotTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(strategy());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (ProductionIssueStrategy) factory.getProxy();
    }

    private void addStock(Long stockId, Long batchId, String quantity, String unitCost, String totalCost) {
        ledger.stocks.put(stockId, new StockState(batchId, decimal(quantity),
            unitCost == null ? null : decimal(unitCost), decimal(totalCost), 1));
    }

    private void addBatch(Long batchId, String received, String issued) {
        ledger.batches.put(batchId, new BatchState(MATERIAL_ID, TENANT_ID, decimal(received), decimal(issued)));
    }

    private void addTwoStocksAndBatches() {
        addStock(1L, 11L, "2.0000", "1.000000", "2.0000");
        addStock(2L, 12L, "2.0000", "2.000000", "4.0000");
        addBatch(11L, "2.0000", "0.0000");
        addBatch(12L, "2.0000", "0.0000");
    }

    private static WmProductionIssueLine line(String quantity) {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(ISSUE_ID);
        line.setLineId(LINE_ID);
        line.setMaterialId(MATERIAL_ID);
        line.setMaterialName("测试原料");
        line.setWarehouseId(1L);
        line.setQuantity(decimal(quantity));
        return line;
    }

    private static AllocatedStock allocated(Long stockId, String quantity) {
        AllocatedStock allocated = new AllocatedStock();
        allocated.setStockId(stockId);
        allocated.setDeductQty(decimal(quantity));
        return allocated;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record BatchCall(Long batchId, Long materialId, String tenantId, BigDecimal quantity) {}
    private record BatchState(Long materialId, String tenantId, BigDecimal received, BigDecimal issued) {}
    private record StockState(Long batchId, BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, Integer version) {
        StockState withQuantityAndVersion(BigDecimal quantity, Integer version) {
            return new StockState(batchId, quantity, unitCost, totalCost, version);
        }
        StockState withTotalCost(BigDecimal totalCost) {
            return new StockState(batchId, quantity, unitCost, totalCost, version);
        }
    }
    private record Snapshot(Map<Long, StockState> stocks, Map<Long, BatchState> batches, List<WmMaterialStockTx> transactions) {}

    /** 内存替身保存不可变行状态，避免把真实数据库访问混入回归测试。 */
    private static class Ledger {
        private final Map<Long, StockState> stocks = new LinkedHashMap<>();
        private final Map<Long, BatchState> batches = new LinkedHashMap<>();
        private final List<WmMaterialStockTx> transactions = new ArrayList<>();

        WmMaterialStock stock(Long id) {
            StockState state = stocks.get(id);
            if (state == null) return null;
            WmMaterialStock stock = new WmMaterialStock();
            stock.setStockId(id);
            stock.setBatchId(state.batchId());
            stock.setMaterialId(MATERIAL_ID);
            stock.setMaterialName("测试原料");
            stock.setTenantId(TENANT_ID);
            stock.setWarehouseId(1L);
            stock.setStockQuantity(state.quantity());
            stock.setReservedQuantity(BigDecimal.ZERO);
            stock.setUnitCost(state.unitCost());
            stock.setTotalCost(state.totalCost());
            stock.setVersion(state.version());
            stock.setFrozenFlag(0L);
            return stock;
        }

        Snapshot snapshot() {
            return new Snapshot(new LinkedHashMap<>(stocks), new LinkedHashMap<>(batches), new ArrayList<>(transactions));
        }

        void restore(Snapshot snapshot) {
            stocks.clear();
            stocks.putAll(snapshot.stocks());
            batches.clear();
            batches.putAll(snapshot.batches());
            transactions.clear();
            transactions.addAll(snapshot.transactions());
        }
    }

    /** 无数据源事务管理器：只验证 Spring 代理执行了回滚，不宣称真实库已验收。 */
    private static class SnapshotTransactionManager extends AbstractPlatformTransactionManager {
        private final Ledger ledger;
        private Snapshot before;
        private int rollbacks;
        private int commits;

        SnapshotTransactionManager(Ledger ledger) {
            this.ledger = ledger;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { before = ledger.snapshot(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { ledger.restore(before); rollbacks++; }
    }
}
