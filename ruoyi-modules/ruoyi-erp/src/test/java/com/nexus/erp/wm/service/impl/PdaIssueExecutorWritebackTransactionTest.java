package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import com.nexus.erp.wm.strategy.impl.ProductionIssueStrategy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PDA 来源绑定至真实执行器的联合回归；连接与持久层为替身，不作为实库回滚验收。 */
@Tag("dev")
@Tag("local")
class PdaIssueExecutorWritebackTransactionTest {
    private final PdaIssueServiceImplTest fixture = new PdaIssueServiceImplTest();
    private final WmProductionIssueMapper headMapper = mock(WmProductionIssueMapper.class);
    private final WmMaterialStockMapper stockMapper = mock(WmMaterialStockMapper.class);
    private final WmWarehouseMapper warehouseMapper = mock(WmWarehouseMapper.class);
    private final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
    private final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
    private final IInventoryValuationService valuation = mock(IInventoryValuationService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final AtomicReference<WmProductionIssueLine> persistedLine = new AtomicReference<>();
    private final AtomicInteger lineWrites = new AtomicInteger();
    private String failurePoint = "无失败";
    private DataSourceTransactionManager transactions;
    private PdaIssueServiceImpl pda;

    @BeforeAll static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(WmProductionIssue.class, WmProductionIssueLine.class, WmMaterialStock.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach void setup() throws Exception {
        fixture.setUp();
        fixture.releaseDirectTransaction();
        fixture.stock.setVersion(1);
        fixture.target.setUnitPrice(new BigDecimal("2"));
        fixture.target.setAmount(new BigDecimal("4"));
        persistedLine.set(copy(fixture.target));
        when(fixture.lineMapper.updateById(any(WmProductionIssueLine.class))).thenAnswer(invocation -> {
            int count = lineWrites.incrementAndGet();
            if (("首次绑定".equals(failurePoint) && count == 1) || ("累计明细".equals(failurePoint) && count == 2)) return 0;
            persistedLine.set(copy(invocation.getArgument(0)));
            return 1;
        });
        when(fixture.lineMapper.selectList(any())).thenAnswer(invocation -> List.of(copy(persistedLine.get())));
        when(headMapper.selectById(PdaIssueServiceImplTest.ISSUE_ID)).thenAnswer(invocation -> {
            WmProductionIssue head = new WmProductionIssue();
            BeanUtils.copyProperties(fixture.head, head);
            return head;
        });
        when(headMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> update = invocation.getArgument(1);
            return "空金额汇总".equals(failurePoint) && update.getSqlSet().contains("total_amount") ? 0 : 1;
        });
        when(headMapper.updateById(any(WmProductionIssue.class))).thenAnswer(invocation -> {
            WmProductionIssue update = invocation.getArgument(0);
            if ("头状态".equals(failurePoint) && update.getStatus() != null) return 0;
            if ("普通汇总".equals(failurePoint) && update.getTotalQty() != null) return 0;
            return 1;
        });
        when(warehouseMapper.selectById(PdaIssueServiceImplTest.WAREHOUSE_ID)).thenReturn(fixture.warehouse);
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fixture.stock));
        when(stockMapper.deductStockAvailable(anyLong(), any(BigDecimal.class), eq(1))).thenReturn(1);
        when(valuation.issueStockCost(anyLong(), any(BigDecimal.class))).thenReturn(BigDecimal.ONE);
        when(batchMapper.increaseIssuedQty(anyLong(), anyLong(), anyString(), any(BigDecimal.class))).thenReturn(1);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenAnswer(invocation -> "库存流水".equals(failurePoint) ? 0 : 1);
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(invocation -> autoCommit.get());
        doAnswer(invocation -> { autoCommit.set(invocation.getArgument(0)); return null; })
            .when(connection).setAutoCommit(anyBoolean());
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        transactions = new DataSourceTransactionManager(dataSource);
        ProductionIssueStrategy strategy = proxy(new ProductionIssueStrategy(txMapper, stockMapper, valuation, batchMapper));
        WmStockOperationServiceImpl stock = proxy(new WmStockOperationServiceImpl(new StockOperationStrategyFactory(List.of(strategy)), strategy, null));
        WmProductionIssueServiceImpl executor = proxy(new WmProductionIssueServiceImpl(headMapper, fixture.lineMapper,
            stockMapper, warehouseMapper, stock, null, null, null, null, null));
        PdaIssueSourceGuard guard = proxy(new PdaIssueSourceGuard(fixture.sourceMapper, dataSource));
        pda = proxy(new PdaIssueServiceImpl(executor, fixture.lineMapper, fixture.configService, fixture.sourceMapper, guard));
    }

    @AfterEach void cleanup() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "联合调用后不能残留原事务上下文");
        assertNull(TransactionSynchronizationManager.getResource(dataSource), "原事务连接必须解除绑定");
        fixture.tearDown();
    }

    @Test void validPickSharesOriginalConnectionAndUpdatesActualSource() throws Exception {
        assertEquals(Boolean.TRUE, pda.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()).getOk(), "完整来源和回写可正常拣货");
        assertEquals(new BigDecimal("2"), persistedLine.get().getIssuedQty(), "实际执行累计本次两件");
        assertEquals(BigDecimal.ZERO, persistedLine.get().getCurrentQty(), "实际执行清零本次量");
        verify(stockMapper).deductStockAvailable(fixture.stock.getStockId(), new BigDecimal("2"), 1);
        verify(batchMapper).increaseIssuedQty(PdaIssueServiceImplTest.BATCH_ID, PdaIssueServiceImplTest.MATERIAL_ID,
            PdaIssueServiceImplTest.TENANT, new BigDecimal("2"));
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(dataSource, times(1).description("来源门禁、身份绑定与实际扣库必须共用原连接")).getConnection();
    }

    @ParameterizedTest @ValueSource(strings = {"累计明细", "头状态", "普通汇总", "空金额汇总"})
    void criticalExecutorZeroRowRollsBackAfterActualDebit(String point) throws Exception {
        failurePoint = point;
        if ("空金额汇总".equals(point)) {
            fixture.target.setUnitPrice(null); fixture.target.setAmount(null);
            persistedLine.set(copy(fixture.target));
        }
        assertThrows(ServiceException.class, () -> pda.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()),
            "原执行器关键零行回写不能被 PDA 返回成功掩盖");
        verify(stockMapper).deductStockAvailable(fixture.stock.getStockId(), new BigDecimal("2"), 1);
        verify(txMapper).insert(any(WmMaterialStockTx.class));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(dataSource, times(1).description("回滚必须发生在原来源绑定与扣库连接")).getConnection();
    }

    @Test void bindingZeroRowRejectsBeforeAnyActualDebit() throws Exception {
        failurePoint = "首次绑定";
        assertThrows(ServiceException.class, () -> pda.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()), "初次身份写入失败必须回滚");
        verifyNoInteractions(stockMapper, batchMapper, txMapper, valuation);
        verify(connection).rollback(); verify(connection, never()).commit();
    }

    @Test void stockTransactionZeroRowPropagatesAcrossWholePdaChain() throws Exception {
        failurePoint = "库存流水";
        assertThrows(ServiceException.class, () -> pda.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()), "库存流水零行仍须向外传播");
        verify(stockMapper).deductStockAvailable(fixture.stock.getStockId(), new BigDecimal("2"), 1);
        verify(connection).rollback(); verify(connection, never()).commit();
    }

    private static WmProductionIssueLine copy(WmProductionIssueLine source) {
        WmProductionIssueLine target = new WmProductionIssueLine(); BeanUtils.copyProperties(source, target); return target;
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
