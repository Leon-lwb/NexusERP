package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.strategy.impl.ReceiveInStrategy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static com.nexus.erp.wm.WmStockOperationServiceImplTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证真实的旧库存写入策略；不连接数据库，也不调用认证过账执行器。 */
class ReceiveInStrategyTest {
    @Test void directCertifiedPurchaseStrategyCannotMutateStockOrUseItsOldReplayShortcut() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.sources.lockCertifiedOrderIdentities(TENANT, ORDER)).thenReturn(List.of(ORDER));
            assertThrows(ServiceException.class, f::execute);
            verifyNoInteractions(f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
            verify(f.connection).rollback();
        }
    }

    @Test void permanentPostingIsRejectedEvenWhenOldStockTransactionsAlreadyExist() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.sources.lockPosting(TENANT, RECEIVE)).thenReturn(posting());
            when(f.transactions.selectCount(any())).thenReturn(1L);
            assertThrows(ServiceException.class, f::execute);
            verifyNoInteractions(f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
    }

    @Test void directStrategyWithoutATransactionMustFailBeforeAnyStockAccess() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThrows(IllegalTransactionStateException.class, () -> f.strategy.execute(RECEIVE));
            verifyNoInteractions(f.sources, f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
    }

    @Test void absentCurrentSourceOrCrossTenantSourceCannotReachLegacyStockWriters() throws Exception {
        for (var bad : new WmCertifiedReceiptPostingMapper.Head[] {null, head("000008")}) {
            try (Fixture f = new Fixture()) {
                doReturn(bad).when(f.sources).lockHead(TENANT, RECEIVE);
                assertThrows(ServiceException.class, f::execute);
                verifyNoInteractions(f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
            }
        }
    }

    @Test void missingTenantAndDisabledTenancyCannotPostStock() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tenants.when(TenantHelper::getTenantId).thenReturn(null);
            assertThrows(ServiceException.class, f::execute);
            verifyNoInteractions(f.sources, f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
        try (Fixture f = new Fixture()) {
            f.tenants.when(TenantHelper::isEnable).thenReturn(false);
            assertThrows(ServiceException.class, f::execute);
            verifyNoInteractions(f.sources, f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
    }

    @Test void nullReceiptIdentityCannotUseUnscopedStockReads() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThrows(ServiceException.class, () -> f.transaction.executeWithoutResult(status -> f.strategy.execute((Long) null)));
            verifyNoInteractions(f.sources, f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
    }

    @Test void sourceLookupFailureCannotFallBackToAnUnverifiedReceipt() throws Exception {
        try (Fixture f = new Fixture()) {
            doThrow(new IllegalStateException("source unavailable")).when(f.sources).lockHead(TENANT, RECEIVE);
            assertThrows(ServiceException.class, f::execute);
            verifyNoInteractions(f.receive, f.details, f.stock, f.transactions, f.valuation, f.materials);
        }
    }

    @Test void legitimateUncertifiedLegacyStillWritesItsOriginalStockAndTransaction() throws Exception {
        try (Fixture f = new Fixture()) {
            assertDoesNotThrow(f::execute);
            var calls = inOrder(f.sources, f.receive, f.transactions, f.stock);
            calls.verify(f.sources).lockHead(TENANT, RECEIVE);
            calls.verify(f.sources).lockPosting(TENANT, RECEIVE);
            calls.verify(f.sources).lockCertifiedOrderIdentities(TENANT, ORDER);
            calls.verify(f.receive).selectById(RECEIVE);
            calls.verify(f.transactions).selectCount(any());
            calls.verify(f.stock).selectList(any());
            calls.verify(f.stock).addStock(801L, new BigDecimal("3"), 0);
            verify(f.valuation).updateStockCost(801L, new BigDecimal("3"), new BigDecimal("9"));
            var posted = ArgumentCaptor.forClass(WmMaterialStockTx.class);
            verify(f.transactions).insert(posted.capture());
            assertEquals(RECEIVE, posted.getValue().getSourceDocId());
            assertEquals(701L, posted.getValue().getSourceDocLineId());
            assertEquals("RECEIVE_IN", posted.getValue().getTxType());
            assertEquals(1, posted.getValue().getTxFlag());
            assertEquals(0, BigDecimal.ZERO.compareTo(posted.getValue().getBeforeQuantity()));
            assertEquals(0, new BigDecimal("3").compareTo(posted.getValue().getAfterQuantity()));
            verify(f.connection).commit();
        }
    }

    @Test void legitimateLegacyStockReplayStillReturnsWithoutMutatingOrCertifying() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.transactions.selectCount(any())).thenReturn(1L);
            assertDoesNotThrow(f::execute);
            verify(f.sources).lockPosting(TENANT, RECEIVE);
            verify(f.sources).lockCertifiedOrderIdentities(TENANT, ORDER);
            verifyNoInteractions(f.details, f.stock, f.valuation, f.materials);
            verify(f.transactions, never()).insert(any(WmMaterialStockTx.class));
        }
    }

    @Test void legacyWriteFailureRollsBackTheCallersExistingTransaction() throws Exception {
        try (Fixture f = new Fixture()) {
            doThrow(new ServiceException("valuation failed")).when(f.valuation)
                .updateStockCost(801L, new BigDecimal("3"), new BigDecimal("9"));
            assertThrows(ServiceException.class, f::execute);
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
            verify(f.transactions, never()).insert(any(WmMaterialStockTx.class));
        }
    }

    @Test void failedSpringSetupReleasesItsOwnTenantStaticMock() throws Exception {
        assertThrows(IllegalStateException.class, () -> new Fixture(context ->
            context.addBeanFactoryPostProcessor(factory -> {throw new IllegalStateException("fixture refresh failed");})));
        try (MockedStatic<TenantHelper> recovered = mockStatic(TenantHelper.class)) {
            recovered.when(TenantHelper::getTenantId).thenReturn("recovered");
            assertEquals("recovered", TenantHelper.getTenantId());
        }
        try (Fixture next = new Fixture()) {
            assertDoesNotThrow(next::execute);
        }
    }

    static class Fixture implements AutoCloseable {
        final MockedStatic<TenantHelper> tenants;
        final WmCertifiedReceiptPostingMapper sources = mock(WmCertifiedReceiptPostingMapper.class);
        final WmMaterialReceiveMapper receive = mock(WmMaterialReceiveMapper.class);
        final WmMaterialReceiveDetailMapper details = mock(WmMaterialReceiveDetailMapper.class);
        final WmMaterialStockMapper stock = mock(WmMaterialStockMapper.class);
        final WmMaterialStockTxMapper transactions = mock(WmMaterialStockTxMapper.class);
        final IInventoryValuationService valuation = mock(IInventoryValuationService.class);
        final BdMaterialMapper materials = mock(BdMaterialMapper.class);
        final DataSource dataSource = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final TransactionTemplate transaction;
        final ReceiveInStrategy strategy;

        Fixture() throws Exception {this(context -> { });}

        Fixture(java.util.function.Consumer<AnnotationConfigApplicationContext> beforeRefresh) throws Exception {
            tenants = mockStatic(TenantHelper.class);
            try {
                // 仅初始化元数据：真实 MyBatis 的 Lambda 条件封装必须在无数据库及启动扫描的情况下可用。
                var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "bounded-stockin-unit");
                TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
                TableInfoHelper.initTableInfo(assistant, WmMaterialStockTx.class);
                TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveDetail.class);
                tenants.when(TenantHelper::isEnable).thenReturn(true);
                tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.getAutoCommit()).thenReturn(true);
                var manager = new DataSourceTransactionManager(dataSource);
                transaction = new TransactionTemplate(manager);
                var guard = transactionProxy(new WmCertifiedReceiptMutationGuard(sources), manager);
                when(sources.lockHead(TENANT, RECEIVE)).thenAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return head(TENANT);
                });
                when(sources.lockCertifiedOrderIdentities(TENANT, ORDER)).thenReturn(List.of());
                var header = new WmMaterialReceive();
                header.setTenantId(TENANT); header.setReceiveId(RECEIVE); header.setReceiveCode("RK-101");
                header.setPurId(ORDER); header.setVendorId(401L);
                when(receive.selectById(RECEIVE)).thenReturn(header);
                var detail = new WmMaterialReceiveDetail();
                detail.setReceiveId(RECEIVE); detail.setLineId(701L); detail.setMaterialId(201L);
                detail.setMaterialName("Legacy material"); detail.setQuantity(new BigDecimal("3"));
                detail.setBaseUomId("501"); detail.setWarehouseId(501L);
                when(details.selectList(any())).thenReturn(List.of(detail));
                when(transactions.selectCount(any())).thenReturn(0L);
                var existing = new WmMaterialStock();
                existing.setStockId(801L); existing.setMaterialId(201L); existing.setBaseUomId("501");
                existing.setStockQuantity(BigDecimal.ZERO); existing.setVersion(0); existing.setUnitCost(new BigDecimal("9"));
                when(stock.selectList(any())).thenReturn(List.of(existing));
                when(stock.addStock(801L, new BigDecimal("3"), 0)).thenReturn(1);
                when(stock.selectById(801L)).thenReturn(existing);
                var material = new BdMaterial();material.setReferencePrice(new BigDecimal("9"));
                when(materials.selectById(201L)).thenReturn(material);
                context.registerBean(WmMaterialStockTxMapper.class, () -> transactions);
                context.registerBean(WmMaterialReceiveMapper.class, () -> receive);
                context.registerBean(WmMaterialReceiveDetailMapper.class, () -> details);
                context.registerBean(WmMaterialStockMapper.class, () -> stock);
                context.registerBean(IInventoryValuationService.class, () -> valuation);
                context.registerBean(BdMaterialMapper.class, () -> materials);
                context.registerBean(WmCertifiedReceiptMutationGuard.class, () -> guard);
                context.registerBean(com.nexus.erp.wm.service.WmSerialInventoryService.class, () -> mock(com.nexus.erp.wm.service.WmSerialInventoryService.class));
                context.registerBean(ReceiveInStrategy.class);
                beforeRefresh.accept(context);
                context.refresh();
                strategy = context.getBean(ReceiveInStrategy.class);
            } catch (Exception | Error failure) {
                try {close();} catch (Throwable cleanupFailure) {failure.addSuppressed(cleanupFailure);}
                throw failure;
            }
        }

        void execute() {transaction.executeWithoutResult(status -> strategy.execute(RECEIVE));}

        @Override public void close() {
            try {context.close();} finally {tenants.close();}
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        }
    }
}
