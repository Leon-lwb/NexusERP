package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import com.nexus.erp.wm.service.impl.WmStockOperationServiceImpl;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import com.nexus.erp.wm.strategy.impl.ProductionIssueStrategy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实执行器、库存策略及 Spring 事务代理的零行回写反例；持久层与连接均模拟，不连接数据库或 PDA。 */
class WmProductionIssueExecutorWritebackRollbackTest {
    private static final Long ISSUE_ID = 41L;
    private static final Long LINE_ID = 42L;
    private static final Long STOCK_ID = 55L;
    private final WmProductionIssueMapper headMapper = mock(WmProductionIssueMapper.class);
    private final WmProductionIssueLineMapper lineMapper = mock(WmProductionIssueLineMapper.class);
    private final WmMaterialStockMapper stockMapper = mock(WmMaterialStockMapper.class);
    private final WmWarehouseMapper warehouseMapper = mock(WmWarehouseMapper.class);
    private final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
    private final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
    private final IInventoryValuationService valuation = mock(IInventoryValuationService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private WmProductionIssue storedHead;
    private WmProductionIssueLine storedLine;
    private String failedWrite = "无失败";
    private DataSourceTransactionManager transactions;
    private WmProductionIssueServiceImpl executor;

    @BeforeAll static void initMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    @BeforeEach void setup() throws Exception {
        storedHead = new WmProductionIssue();
        storedHead.setIssueId(ISSUE_ID);
        storedHead.setWarehouseId(7L);
        storedHead.setStatus("CREATED");
        storedHead.setTotalAmount(new BigDecimal("999"));
        storedLine = new WmProductionIssueLine();
        storedLine.setIssueId(ISSUE_ID);
        storedLine.setLineId(LINE_ID);
        storedLine.setMaterialId(23L);
        storedLine.setMaterialName("离线物料");
        storedLine.setRequiredQty(new BigDecimal("2"));
        storedLine.setIssuedQty(BigDecimal.ZERO);
        storedLine.setCurrentQty(new BigDecimal("2"));
        storedLine.setShortQty(new BigDecimal("2"));
        storedLine.setUnitPrice(new BigDecimal("2"));
        storedLine.setAmount(new BigDecimal("4"));
        storedLine.setLineStatus("PENDING");
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(7L);
        warehouse.setStatus(1L);
        warehouse.setWarehouseName("离线仓库");
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(STOCK_ID);
        stock.setMaterialId(23L);
        stock.setWarehouseId(7L);
        stock.setStockQuantity(BigDecimal.TEN);
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setUnitCost(BigDecimal.ONE);
        stock.setFrozenFlag(0L);
        stock.setDelFlag("0");
        stock.setVersion(1);
        when(headMapper.selectById(ISSUE_ID)).thenAnswer(invocation -> copy(storedHead, new WmProductionIssue()));
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> List.of(copy(storedLine, new WmProductionIssueLine())));
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenAnswer(invocation -> {
            if ("明细回写".equals(failedWrite)) return 0;
            storedLine = copy(invocation.getArgument(0), new WmProductionIssueLine());
            return 1;
        });
        when(headMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<WmProductionIssue> update = invocation.getArgument(1);
            if (update.getSqlSet().contains("total_amount=")) {
                if ("未计价汇总".equals(failedWrite)) return 0;
                storedHead.setTotalAmount(null);
            } else {
                storedHead.setStatus("EXECUTING");
            }
            return 1;
        });
        when(headMapper.updateById(any(WmProductionIssue.class))).thenAnswer(invocation -> {
            WmProductionIssue value = invocation.getArgument(0);
            if (value.getStatus() != null) {
                if ("单头状态".equals(failedWrite)) return 0;
                storedHead.setStatus(value.getStatus());
            } else {
                if ("汇总金额".equals(failedWrite)) return 0;
                storedHead.setTotalQty(value.getTotalQty());
                if (value.getTotalAmount() != null) storedHead.setTotalAmount(value.getTotalAmount());
            }
            return 1;
        });
        when(warehouseMapper.selectById(7L)).thenReturn(warehouse);
        when(stockMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> List.of(copy(stock, new WmMaterialStock())));
        when(stockMapper.deductStockAvailable(STOCK_ID, new BigDecimal("2"), 1)).thenReturn(1);
        when(valuation.issueStockCost(STOCK_ID, new BigDecimal("2"))).thenReturn(BigDecimal.ONE);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(invocation -> autoCommit.get());
        doAnswer(invocation -> { autoCommit.set(invocation.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        transactions = new DataSourceTransactionManager(dataSource);
        ProductionIssueStrategy strategy = proxy(new ProductionIssueStrategy(txMapper, stockMapper, valuation, batchMapper));
        WmStockOperationServiceImpl stockOperations = proxy(new WmStockOperationServiceImpl(
            new StockOperationStrategyFactory(List.of(strategy)), strategy, null));
        executor = proxy(new WmProductionIssueServiceImpl(headMapper, lineMapper, stockMapper, warehouseMapper,
            stockOperations, null, null, null, null, null));
    }

    @AfterEach void cleanup() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "事务代理调用结束后不得泄漏事务状态");
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource), "模拟连接必须从当前线程释放");
    }

    @Test void completePersistenceCommitsTheOriginalTransactionOnce() throws Exception {
        executor.execute(ISSUE_ID);
        assertEquals(new BigDecimal("2"), storedLine.getIssuedQty(), "合法领料应完整回写累计已领量");
        assertEquals(BigDecimal.ZERO, storedLine.getCurrentQty(), "合法领料应持久清零本次量");
        assertEquals("COMPLETED", storedHead.getStatus(), "合法领料应完整回写单头状态");
        assertEquals(new BigDecimal("4"), storedHead.getTotalAmount(), "参考总额应由完整已知明细汇总");
        verify(stockMapper).deductStockAvailable(STOCK_ID, new BigDecimal("2"), 1);
        verify(txMapper).insert(any(WmMaterialStockTx.class));
        verify(connection, times(1)).commit();
        verify(connection, never()).rollback();
    }

    @ParameterizedTest(name = "扣库后的{0}零行必须使原事务回滚")
    @ValueSource(strings = {"明细回写", "单头状态", "汇总金额", "未计价汇总"})
    void zeroRowWritebackAfterDebitCannotCommitAsSuccess(String failure) throws Exception {
        failedWrite = failure;
        if ("未计价汇总".equals(failure)) {
            storedLine.setUnitPrice(null);
            storedLine.setAmount(null);
        }
        assertAll("扣库后的关键持久化失败必须完整拒绝",
            () -> assertThrows(RuntimeException.class, () -> executor.execute(ISSUE_ID), "关键回写零行必须抛出异常，不能返回领料成功"),
            () -> verify(stockMapper).deductStockAvailable(STOCK_ID, new BigDecimal("2"), 1),
            () -> verify(txMapper).insert(any(WmMaterialStockTx.class)),
            () -> verify(connection).rollback(),
            () -> verify(connection, never()).commit());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
