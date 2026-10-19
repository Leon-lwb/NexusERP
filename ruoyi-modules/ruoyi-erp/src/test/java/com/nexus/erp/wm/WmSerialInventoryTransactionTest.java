package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 模拟连接只证明真实Spring事务边界和异常传播，不代表MySQL物理回滚验收。
 */
class WmSerialInventoryTransactionTest {
    @Test
    void mandatoryPostingRejectsCallWithoutAnActualTransaction() throws Exception {
        DataSource ds = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(ds);
        var repo = mock(WmSerialInventoryRepository.class);
        var factory = new ProxyFactory(new WmSerialInventoryService(repo));
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var service = (WmSerialInventoryService) factory.getProxy();
        assertThrows(
                IllegalTransactionStateException.class,
                () ->
                        service.post(
                                WmSerialPostingRequest.out(
                                        "WM_MISC_ISSUE",
                                        1L,
                                        2L,
                                        "MISC_ISSUE",
                                        3L,
                                        BigDecimal.ONE,
                                        4L,
                                        null,
                                        null)));
        verifyNoInteractions(repo);
    }

    @Test
    void outboxFailureRollsBackTheOuterInventoryTransaction() throws Exception {
        var f = new WmSerialInventoryServiceTest();
        f.setup();
        f.request =
                WmSerialPostingRequest.out(
                        "WM_MISC_ISSUE",
                        10L,
                        11L,
                        "MISC_ISSUE",
                        20L,
                        BigDecimal.ONE,
                        30L,
                        40L,
                        50L);
        f.frozen(List.of(new SnItem(1L, 101L, null, null, 50L)));
        f.available(1, 101);
        when(f.store.pool(200L))
                .thenReturn(
                        new WmSerialInventoryRepository.Pool(
                                200L,
                                20L,
                                30L,
                                50L,
                                999L,
                                BigDecimal.ONE,
                                new BigDecimal("0.3333"),
                                new BigDecimal("0.333333")));
        when(f.store.poolStocks(200L))
                .thenReturn(List.of(f.stock(101, 1, BigDecimal.ONE, BigDecimal.ZERO)));
        doThrow(new ServiceException("outbox failed"))
                .when(f.store)
                .enqueue(any(), anyString(), anyString());
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var manager = new DataSourceTransactionManager(ds);
        var factory = new ProxyFactory(f.service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (WmSerialInventoryService) factory.getProxy();
        assertThrows(
                ServiceException.class,
                () ->
                        new TransactionTemplate(manager)
                                .execute(
                                        status -> {
                                            proxy.post(f.request);
                                            return null;
                                        }));
        assertEquals(1, f.txs.size());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
