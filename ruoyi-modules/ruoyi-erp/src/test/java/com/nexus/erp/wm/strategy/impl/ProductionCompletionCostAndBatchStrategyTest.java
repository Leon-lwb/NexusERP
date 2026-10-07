package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 成品入库成本与批次收量回归：运行真实策略，使用内存账本和真实 Spring 事务代理。
 * <p>SQL 仅验证静态契约及参数绑定；内存回滚不替代真实 MySQL、租户插件或消息链路验收。</p>
 */
class ProductionCompletionCostAndBatchStrategyTest {

    private static final Long WO_ID = 2106995561414438913L;
    private static final Long OTHER_WO_ID = 2106995561481547778L;
    private static final Long STOCK_ID = 101L;
    private static final Long MATERIAL_ID = 100L;
    private static final Long BATCH_ID = 201L;
    private static final String TENANT_ID = "000000";
    private static final String BATCH_METHOD = "increaseProductionReceivedQty";
    private static final Date NOW = new Date(1791172800000L);

    private WmMaterialStockMapper stockMapper;
    private WmMaterialStockTxMapper txMapper;
    private WmBatchMapper batchMapper;
    private BdMaterialMapper materialMapper;
    private BdUnitOfMeasureMapper uomMapper;
    private WmWarehouseMapper warehouseMapper;
    private WmStorageLocationMapper locationMapper;
    private WmStorageAreaMapper areaMapper;
    private BdMaterial material;
    private Ledger ledger;
    private WmMaterialStock createdStock;
    private BigDecimal refreshedUnitCost;
    private int stockRows;
    private int batchRows;
    private int txRows;
    private Long postedCount;
    private RuntimeException batchFailure;
    private RuntimeException txFailure;
    private final List<BatchCall> batchCalls = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStockTx.class);
    }

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.stocks.put(STOCK_ID, new StockState(BATCH_ID, TENANT_ID, decimal("7.0000"),
            decimal("3.890000"), decimal("999.9900"), 3));
        ledger.batches.put(BATCH_ID, new BatchState(MATERIAL_ID, TENANT_ID, "PRD_WORK_ORDER", WO_ID,
            "0", decimal("100.0000"), decimal("2.0000"), decimal("1.0000"), "ACTIVE"));
        stockRows = batchRows = txRows = 1;
        material = new BdMaterial();
        material.setStandardCost(decimal("99.990000"));
        materialMapper = mock(BdMaterialMapper.class);
        uomMapper = mock(BdUnitOfMeasureMapper.class);
        warehouseMapper = mock(WmWarehouseMapper.class);
        locationMapper = mock(WmStorageLocationMapper.class);
        areaMapper = mock(WmStorageAreaMapper.class);
        stockMapper = mock(WmMaterialStockMapper.class);
        txMapper = mock(WmMaterialStockTxMapper.class);
        // 按名称记录新增方法，保证原实现能编译并暴露真实成本及收量断言失败。
        batchMapper = mock(WmBatchMapper.class, invocation -> {
            if (!BATCH_METHOD.equals(invocation.getMethod().getName())) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            BatchCall call = new BatchCall(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
            batchCalls.add(call);
            if (batchFailure != null) throw batchFailure;
            if (batchRows != 1) return batchRows;
            BatchState batch = ledger.batches.get(call.batchId());
            if (batch == null || !Objects.equals(batch.materialId(), call.materialId())
                || !Objects.equals(batch.tenantId(), call.tenantId()) || !"PRD_WORK_ORDER".equals(batch.sourceType())
                || !Objects.equals(batch.sourceId(), call.woId()) || !"0".equals(batch.deleted())
                || call.quantity().signum() <= 0) {
                return 0;
            }
            BigDecimal received = batch.received() == null ? BigDecimal.ZERO : batch.received();
            ledger.batches.put(call.batchId(), batch.withReceived(received.add(call.quantity())));
            return 1;
        });
        when(materialMapper.selectById(anyLong())).thenAnswer(invocation -> material);
        when(txMapper.selectCount(any(Wrapper.class))).thenAnswer(invocation ->
            postedCount != null ? postedCount : (long) ledger.transactions.size());
        when(stockMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> ledger.stock(STOCK_ID));
        when(stockMapper.selectById(anyLong())).thenAnswer(invocation -> ledger.stock(invocation.getArgument(0)));
        when(stockMapper.insert(any(WmMaterialStock.class))).thenAnswer(invocation -> {
            createdStock = invocation.getArgument(0);
            createdStock.setStockId(STOCK_ID);
            createdStock.setTenantId(TENANT_ID);
            ledger.stocks.put(STOCK_ID, new StockState(createdStock.getBatchId(), createdStock.getTenantId(),
                createdStock.getStockQuantity(), createdStock.getUnitCost(), createdStock.getTotalCost(), createdStock.getVersion()));
            return 1;
        });
        when(stockMapper.addStock(anyLong(), any(BigDecimal.class), anyInt())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            BigDecimal quantity = invocation.getArgument(1);
            Integer version = invocation.getArgument(2);
            StockState stock = ledger.stocks.get(id);
            if (stockRows == 0 || stock == null || !Objects.equals(stock.version(), version)) return 0;
            ledger.stocks.put(id, new StockState(stock.batchId(), stock.tenantId(), stock.quantity().add(quantity),
                refreshedUnitCost != null ? refreshedUnitCost : stock.unitCost(), stock.totalCost(), version + 1));
            return stockRows;
        });
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenAnswer(invocation -> {
            if (txFailure != null) throw txFailure;
            if (txRows == 1) ledger.transactions.add(invocation.getArgument(0));
            return txRows;
        });
    }

    @Test
    void tenUnitsPostActualStockCostAndAmountInsteadOfMasterPriceOrTotalCost() {
        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        WmMaterialStockTx tx = ledger.transactions.get(0);
        assertEquals(decimal("3.890000"), tx.getUnitCost(), "不能取主数据假价或从库存总成本反推单价");
        assertEquals(decimal("38.9000"), tx.getTxAmount());
        assertEquals("PRODUCTION_IN", tx.getTxType());
        assertEquals(1, tx.getTxFlag());
        assertEquals(decimal("10.0000"), tx.getTxQuantity());
        assertEquals(decimal("7.0000"), tx.getBeforeQuantity());
        assertEquals(decimal("17.0000"), tx.getAfterQuantity());
        assertEquals("PRD_WORK_ORDER", tx.getSourceDocType());
        assertEquals(WO_ID, tx.getSourceDocId());
        assertEquals(STOCK_ID, tx.getMaterialStockId());
        verifyNoInteractions(materialMapper);
    }

    @ParameterizedTest
    @CsvSource({"1.0000, 1.23455, 1.2346", "0.5000, 2.4691, 1.2346", "3.3333, 0.333333, 1.1111", "1.0000, 1.23454, 1.2345"})
    void amountUsesActualQuantityAndFourDecimalHalfUp(String quantity, String unitCost, String amount) {
        refreshedUnitCost = decimal(unitCost);

        strategy().execute(arguments(decimal(quantity), BATCH_ID));

        WmMaterialStockTx tx = ledger.transactions.get(0);
        assertEquals(decimal(unitCost), tx.getUnitCost(), "单位成本不能预先舍入");
        assertEquals(decimal(amount), tx.getTxAmount());
        assertEquals(4, tx.getTxAmount().scale());
    }

    @Test
    void existingStockUsesCostReadAfterAddRatherThanEarlierStockSnapshot() {
        refreshedUnitCost = decimal("4.567891");

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(decimal("4.567891"), ledger.transactions.get(0).getUnitCost());
        assertEquals(decimal("45.6789"), ledger.transactions.get(0).getTxAmount());
        verify(stockMapper).selectById(STOCK_ID);
    }

    @Test
    void newStockKeepsStandardCostInitializationAndPostsObservedCost() {
        ledger.stocks.clear();
        material.setStandardCost(decimal("3.890000"));

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(decimal("3.890000"), createdStock.getUnitCost());
        assertEquals(decimal("38.9000000000"), createdStock.getTotalCost());
        assertEquals(decimal("3.890000"), ledger.transactions.get(0).getUnitCost());
        assertEquals(decimal("38.9000"), ledger.transactions.get(0).getTxAmount());
        assertEquals(BigDecimal.ZERO.setScale(4), ledger.transactions.get(0).getBeforeQuantity());
        verify(materialMapper).selectById(MATERIAL_ID);
    }

    @Test
    void newStockAlsoUsesRefreshedCostInsteadOfInitialMasterPrice() {
        ledger.stocks.clear();
        refreshedUnitCost = decimal("3.890000");

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(decimal("99.990000"), createdStock.getUnitCost());
        assertEquals(decimal("3.890000"), ledger.transactions.get(0).getUnitCost());
        assertEquals(decimal("38.9000"), ledger.transactions.get(0).getTxAmount());
    }

    @Test
    void existingNullUnitCostUsesZeroCompatibilityWithoutChangingStockPrice() {
        StockState before = ledger.stocks.get(STOCK_ID);
        ledger.stocks.put(STOCK_ID, new StockState(before.batchId(), before.tenantId(), before.quantity(),
            null, before.totalCost(), before.version()));

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(BigDecimal.ZERO, ledger.transactions.get(0).getUnitCost());
        assertEquals(decimal("0.0000"), ledger.transactions.get(0).getTxAmount());
        assertNull(ledger.stocks.get(STOCK_ID).unitCost());
    }

    @Test
    void newStockWithNullStandardCostRetainsZeroCompatibility() {
        ledger.stocks.clear();
        material.setStandardCost(null);

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(BigDecimal.ZERO, createdStock.getUnitCost());
        assertEquals(BigDecimal.ZERO, ledger.transactions.get(0).getUnitCost());
        assertEquals(decimal("0.0000"), ledger.transactions.get(0).getTxAmount());
    }

    @Test
    void batchAccumulatesReceivedOnlyAndUsesOriginalWorkOrderNotStockWorkOrder() {
        BatchState before = ledger.batches.get(BATCH_ID);

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(before.withReceived(decimal("12.0000")), ledger.batches.get(BATCH_ID),
            "只累计实际收量，不改批次数量、发量、来源或状态");
        assertEquals(List.of(new BatchCall(BATCH_ID, MATERIAL_ID, TENANT_ID, WO_ID, decimal("10.0000"))), batchCalls);
        assertNotEquals(OTHER_WO_ID, batchCalls.get(0).woId());
    }

    @Test
    void legacyNullReceivedQuantityAccumulatesFromZero() {
        ledger.batches.put(BATCH_ID, ledger.batches.get(BATCH_ID).withReceived(null));

        strategy().execute(arguments(decimal("10.0000"), BATCH_ID));

        assertEquals(decimal("10.0000"), ledger.batches.get(BATCH_ID).received());
    }

    @Test
    void nullBatchKeepsLegacyPostingWithoutBatchMapperCall() {
        StockState before = ledger.stocks.get(STOCK_ID);
        ledger.stocks.put(STOCK_ID, new StockState(null, before.tenantId(), before.quantity(),
            before.unitCost(), before.totalCost(), before.version()));

        strategy().execute(arguments(decimal("10.0000"), null));

        assertEquals(decimal("38.9000"), ledger.transactions.get(0).getTxAmount());
        assertNull(ledger.transactions.get(0).getBatchId());
        verifyNoInteractions(batchMapper);
    }

    @Test
    void absentBatchArgumentDoesNotRetrospectivelyChangeMatchedHistoricalBatch() {
        BatchState before = ledger.batches.get(BATCH_ID);

        strategy().execute(arguments(decimal("10.0000"), null));

        assertEquals(BATCH_ID, ledger.transactions.get(0).getBatchId());
        assertEquals(before, ledger.batches.get(BATCH_ID));
        assertEquals(decimal("38.9000"), ledger.transactions.get(0).getTxAmount());
        verifyNoInteractions(batchMapper);
    }

    @Test
    void repeatedWorkOrderRejectsLargerLegacyIncrementWithoutNewEffects() {
        ProductionCompletionStrategy strategy = strategy();
        strategy.execute(arguments(decimal("10.0000"), BATCH_ID));
        Snapshot posted = ledger.snapshot();

        assertThrows(ServiceException.class, () -> strategy.execute(arguments(decimal("20.0000"), BATCH_ID)),
            "旧工单级幂等不能给更大累计量返回伪成功");

        assertEquals(posted, ledger.snapshot(), "保持原 hasPosted 工单语义，不新增旧增量支持");
        assertEquals(1, batchCalls.size());
        assertEquals(decimal("12.0000"), ledger.batches.get(BATCH_ID).received());
        verify(stockMapper, times(1)).addStock(STOCK_ID, decimal("10.0000"), 3);
        verify(txMapper, times(1)).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void alreadyPostedWorkOrderDoesNotTouchAnyProjection() {
        postedCount = 1L;
        Snapshot before = ledger.snapshot();

        assertThrows(ServiceException.class, () -> strategy().execute(arguments(decimal("10.0000"), BATCH_ID)),
            "已有流水须交由原单完整回执恢复，不能静默成功");

        assertEquals(before, ledger.snapshot());
        verifyNoInteractions(stockMapper, batchMapper, materialMapper);
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
        var count = mockingDetails(txMapper).getInvocations().iterator().next();
        Wrapper<?> wrapper = count.getArgument(0);
        assertTrue(wrapper.getSqlSegment().contains("source_doc_type"));
        assertTrue(wrapper.getSqlSegment().contains("source_doc_id"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void batchUpdateMustAffectExactlyOneRowAndRollBackStock(int rows) {
        batchRows = rows;
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertThrows(ServiceException.class, () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID)));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void batchExceptionPropagatesAndRollsBackThroughSpringProxy() {
        batchFailure = new ServiceException("模拟批次写入失败");
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertSame(batchFailure, assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID))));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void transactionInsertMustAffectExactlyOneRowAndRollBackBothProjections(int rows) {
        txRows = rows;
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertThrows(ServiceException.class, () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID)));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, batchCalls.size(), "批次应在同一事务内写入后被回滚");
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }

    @Test
    void transactionExceptionPropagatesAndRollsBackStockAndBatch() {
        txFailure = new ServiceException("模拟流水写入失败");
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertSame(txFailure, assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID))));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, batchCalls.size());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void originalStockCasConflictStillThrowsBeforeBatchOrTransaction() {
        stockRows = 0;
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        ServiceException failure = assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID)));

        assertTrue(failure.getMessage().contains("库存并发冲突"));
        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
        verifyNoInteractions(batchMapper);
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void newlyInsertedStockIsAlsoRolledBackWhenTransactionPostingFails() {
        ledger.stocks.clear();
        txRows = 0;
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID)));

        assertNotNull(createdStock, "必须实际走过新库存创建路径");
        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"material", "tenant", "sourceType", "sourceId", "deleted"})
    void mismatchedProductionBatchIdentityFailsAndRollsBack(String mismatch) {
        BatchState batch = ledger.batches.get(BATCH_ID);
        ledger.batches.put(BATCH_ID, new BatchState("material".equals(mismatch) ? MATERIAL_ID + 1 : batch.materialId(),
            "tenant".equals(mismatch) ? "100001" : batch.tenantId(),
            "sourceType".equals(mismatch) ? "PRD_PRODUCTION_RECPT" : batch.sourceType(),
            "sourceId".equals(mismatch) ? OTHER_WO_ID : batch.sourceId(),
            "deleted".equals(mismatch) ? "2" : batch.deleted(), batch.quantity(), batch.received(), batch.issued(), batch.status()));
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID)));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0000", "-1.0000"})
    void productionBatchRejectsNonPositiveReceiptQuantity(String quantity) {
        Snapshot before = ledger.snapshot();
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);

        assertThrows(ServiceException.class,
            () -> transactionalStrategy(manager).execute(arguments(decimal(quantity), BATCH_ID)));

        assertEquals(before, ledger.snapshot());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void atomicSqlBindsProductionIdentityPositiveQuantityAndUpdatesReceivedOnly() {
        Method method = batchMethod();
        Update update = method.getAnnotation(Update.class);
        assertNotNull(update, "必须使用单条相对原子更新，不允许先读后写覆盖收量");
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String set = sql.substring(0, sql.indexOf(" where "));
        String where = sql.substring(sql.indexOf(" where "));
        assertEquals("update wm_batch set received_qty = ifnull(received_qty, 0) + #{receiveqty}, update_time = now()", set);
        assertTrue(where.contains("batch_id = #{batchid}"), sql);
        assertTrue(where.contains("material_id = #{materialid}"), sql);
        assertTrue(where.contains("tenant_id = #{tenantid}"), sql);
        assertTrue(where.contains("source_doc_type = 'prd_work_order'"), sql);
        assertTrue(where.contains("source_doc_id = #{woid}"), sql);
        assertTrue(where.contains("del_flag = '0'"), sql);
        assertTrue(where.contains("#{receiveqty} > 0"), sql);
        assertEquals(List.of("batchId", "materialId", "tenantId", "woId", "receiveQty"),
            Arrays.stream(method.getParameters()).map(parameter -> parameter.getAnnotation(Param.class).value()).toList());
    }

    @Test
    void annotatedSqlResolvesOriginalWorkOrderAndQuantityAsBoundParametersWithoutDatabase() {
        batchMethod();
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(WmBatchMapper.class);
        Map<String, Object> parameters = Map.of("batchId", BATCH_ID, "materialId", MATERIAL_ID,
            "tenantId", TENANT_ID, "woId", WO_ID, "receiveQty", decimal("10.0000"));
        var statement = configuration.getMappedStatement(WmBatchMapper.class.getName() + "." + BATCH_METHOD);
        var bound = statement.getBoundSql(parameters);
        assertEquals(List.of("receiveQty", "batchId", "materialId", "tenantId", "woId", "receiveQty"),
            bound.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
        assertEquals(WO_ID, ((Map<?, ?>) bound.getParameterObject()).get("woId"));
        assertTrue(bound.getSql().contains("source_doc_type = 'PRD_WORK_ORDER'"));
        assertTrue(bound.getSql().contains("AND ? > 0"));
    }

    @Test
    void originalTransactionBoundaryAndSupportedOperationRemainUnchanged() throws Exception {
        Transactional annotation = ProductionCompletionStrategy.class.getMethod("execute", Object[].class)
            .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.rollbackFor()).contains(Exception.class));
        assertEquals(StockOperationTypeEnum.PRODUCTION_IN, strategy().getSupportedType());
        SnapshotTransactionManager manager = new SnapshotTransactionManager(ledger);
        transactionalStrategy(manager).execute(arguments(decimal("10.0000"), BATCH_ID));
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
    }

    private ProductionCompletionStrategy strategy() {
        // 反射注入兼容新增构造依赖前后的实现，RED 必须来自业务断言而非编译错误。
        Map<Class<?>, Object> dependencies = Map.of(WmMaterialStockTxMapper.class, txMapper,
            WmMaterialStockMapper.class, stockMapper, BdMaterialMapper.class, materialMapper,
            WmWarehouseMapper.class, warehouseMapper, WmStorageLocationMapper.class, locationMapper,
            WmStorageAreaMapper.class, areaMapper, BdUnitOfMeasureMapper.class, uomMapper,
            WmBatchMapper.class, batchMapper);
        Constructor<?> constructor = ProductionCompletionStrategy.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(dependencies::get).toArray();
        try {
            return (ProductionCompletionStrategy) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("策略构造依赖不匹配", exception);
        }
    }

    private ProductionCompletionStrategy transactionalStrategy(SnapshotTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(strategy());
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (ProductionCompletionStrategy) factory.getProxy();
    }

    private static Object[] arguments(BigDecimal quantity, Long batchId) {
        return new Object[]{WO_ID, "原工单", MATERIAL_ID, "成品编码", "测试成品", quantity,
            1L, null, batchId, batchId == null ? null : "测试生产批次", NOW};
    }

    private static Method batchMethod() {
        Method method = Arrays.stream(WmBatchMapper.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(BATCH_METHOD)).findFirst().orElse(null);
        assertNotNull(method, "完工入库缺少生产批次 received_qty 原生原子累计方法");
        return method;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record BatchCall(Long batchId, Long materialId, String tenantId, Long woId, BigDecimal quantity) {}
    private record StockState(Long batchId, String tenantId, BigDecimal quantity, BigDecimal unitCost,
                              BigDecimal totalCost, Integer version) {}
    private record BatchState(Long materialId, String tenantId, String sourceType, Long sourceId, String deleted,
                              BigDecimal quantity, BigDecimal received, BigDecimal issued, String status) {
        BatchState withReceived(BigDecimal received) {
            return new BatchState(materialId, tenantId, sourceType, sourceId, deleted, quantity, received, issued, status);
        }
    }
    private record Snapshot(Map<Long, StockState> stocks, Map<Long, BatchState> batches, List<WmMaterialStockTx> transactions) {}

    /** 不可变内存行替代数据库，调用记录保留以区分未执行与执行后回滚。 */
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
            stock.setMaterialCode("成品编码");
            stock.setMaterialName("测试成品");
            stock.setTenantId(state.tenantId());
            stock.setWarehouseId(1L);
            stock.setWoId(OTHER_WO_ID);
            stock.setStockQuantity(state.quantity());
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

    /** 无数据源事务管理器，仅证明真实 Spring 代理传播异常并回滚内存快照。 */
    private static class SnapshotTransactionManager extends AbstractPlatformTransactionManager {
        private final Ledger ledger;
        private Snapshot before;
        private int rollbacks;
        private int commits;

        SnapshotTransactionManager(Ledger ledger) { this.ledger = ledger; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { before = ledger.snapshot(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { ledger.restore(before); rollbacks++; }
    }
}
