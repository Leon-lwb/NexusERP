package com.nexus.erp.wm.service.impl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.pda.PdaIdempotent;
import com.nexus.erp.wm.controller.app.AppPdaIssueController;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 事务拦截器搭配模拟连接，只证明事务边界和回滚决策，不接触数据库。 */
@Tag("dev")
@Tag("local")
class PdaIssuePickTransactionTest {
    private final PdaIssueServiceImplTest fixture = new PdaIssueServiceImplTest();
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private PdaIssueServiceImpl service;
    private DataSourceTransactionManager manager;
    private final AtomicInteger physicalIsolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);

    @BeforeEach void setUp() throws Exception {
        fixture.setUp();
        fixture.releaseDirectTransaction();
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(invocation -> autoCommit.get());
        doAnswer(invocation -> { autoCommit.set(invocation.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        when(connection.getTransactionIsolation()).thenAnswer(invocation -> physicalIsolation.get());
        doAnswer(invocation -> { physicalIsolation.set(invocation.getArgument(0)); return null; })
            .when(connection).setTransactionIsolation(anyInt());
        manager = new DataSourceTransactionManager(dataSource);
        PdaIssueSourceGuard guarded = transactional(new PdaIssueSourceGuard(fixture.sourceMapper, dataSource), manager);
        service = transactional(new PdaIssueServiceImpl(fixture.issueService, fixture.lineMapper, fixture.configService,
            fixture.sourceMapper, guarded), manager);
    }
    @AfterEach void tearDown() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "调用结束后不得残留事务上下文");
        assertNull(TransactionSynchronizationManager.getResource(dataSource), "原事务连接资源应在调用结束后解绑");
        fixture.tearDown();
    }

    @Test void executionExceptionRollsBackOriginalTransactionAfterBinding() throws Exception {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "执行扣库必须仍处于原事务中");
            assertEquals(PdaIssueServiceImplTest.BATCH_ID, fixture.target.getBatchId(), "执行前必须已经绑定真实批次标识");
            assertEquals(PdaIssueServiceImplTest.LOCATION_ID, fixture.target.getLocationId(), "执行前必须已经绑定真实库位标识");
            throw new ServiceException("模拟扣库失败");
        }).when(fixture.issueService).execute(PdaIssueServiceImplTest.ISSUE_ID);
        assertThrows(ServiceException.class, () -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()),
            "扣库异常必须传播到原事务拦截器");
        verify(connection, times(1).description("已写明细后的扣库异常必须回滚原连接")).rollback();
        verify(connection, never().description("失败事务不得提交部分身份绑定")).commit();
        verify(dataSource, times(1).description("来源门禁与服务必须共用同一事务连接")).getConnection();
    }
    @Test void laterLineWriteFailureRollsBackEarlierWriteAndDoesNotExecute() throws Exception {
        WmProductionIssueLine other = new WmProductionIssueLine();
        other.setLineId(2L); other.setIssueId(PdaIssueServiceImplTest.ISSUE_ID);
        other.setTenantId(PdaIssueServiceImplTest.TENANT); other.setDelFlag("0"); other.setCurrentQty(new BigDecimal("7"));
        when(fixture.sourceMapper.lockLines(PdaIssueServiceImplTest.TENANT, PdaIssueServiceImplTest.ISSUE_ID))
            .thenReturn(List.of(fixture.target, other));
        when(fixture.lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1, 0);
        assertThrows(ServiceException.class, () -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()),
            "第二行写入失败必须整体回滚已写的目标行");
        verify(connection, description("部分写行失败必须回滚原事务")).rollback();
        verify(connection, never().description("不允许提交部分明细写入")).commit();
        verify(fixture.issueService, never().description("写行失败不得扣库")).execute(anyLong());
    }
    @Test void readOnlySourceRefusalReturnsFailureWithoutUnexpectedRollback() throws Exception {
        fixture.stock.setFrozenFlag(1L);
        var result = assertDoesNotThrow(() -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()),
            "只读来源拒绝应返回失败，不能因内层事务标记导致意外回滚异常");
        assertEquals(Boolean.FALSE, result.getOk(), "冻结来源必须拒绝");
        verify(fixture.lineMapper, never().description("来源拒绝不得写行")).updateById(any(WmProductionIssueLine.class));
        verify(fixture.issueService, never().description("来源拒绝不得扣库")).execute(anyLong());
    }
    @Test void mapperFailurePropagatesAndRollsBack() throws Exception {
        when(fixture.sourceMapper.lockStocks(anyString(), anyLong(), anyLong(), anyLong(), any()))
            .thenThrow(new IllegalStateException("模拟锁定读取故障"));
        assertThrows(IllegalStateException.class, () -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()),
            "真实持久化故障不能伪装成普通来源拒绝");
        verify(connection, description("锁定读取故障必须回滚原事务")).rollback();
        verify(fixture.lineMapper, never().description("读取故障前不应写行")).updateById(any(WmProductionIssueLine.class));
    }
    @Test void successfulPickKeepsSingleOriginalTransaction() throws Exception {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "实际执行必须参与外层事务"); return null;
        }).when(fixture.issueService).execute(PdaIssueServiceImplTest.ISSUE_ID);
        assertEquals(Boolean.TRUE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()).getOk(), "有效来源应成功");
        verify(connection, description("成功路径在全部验证和执行后提交")).commit();
        verify(connection, never().description("成功路径不应回滚")).rollback();
        verify(dataSource, description("锁定来源与执行只能借用一次原事务连接")).getConnection();
    }
    @Test void nonBatchSourceUsesOriginalRepeatableReadConnectionWithoutInventingIdentity() throws Exception {
        fixture.material.setIsBatchManage(0L); fixture.stock.setBatchId(null); fixture.stock.setBatchCode(null);
        PdaPickBo request = fixture.bo(); request.setBatchCode(null);
        doAnswer(invocation -> {
            assertEquals(Isolation.REPEATABLE_READ.value(), TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                "非批次实际扣库必须仍在明确的原可重复读事务内");
            assertNull(fixture.target.getBatchId(), "不能伪造零批次标识以限制执行范围"); return null;
        }).when(fixture.issueService).execute(PdaIssueServiceImplTest.ISSUE_ID);
        assertEquals(Boolean.TRUE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, request).getOk(), "明确原事务内真实非批次库存可领料");
        verify(connection, description("事务管理器必须在原连接上设置可重复读")).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        verify(dataSource, description("不得为非批次兼容另开数据库事务")).getConnection();
    }
    @Test void inheritedReadCommittedTransactionCannotExpandNullBatchSourceScope() {
        fixture.material.setIsBatchManage(0L); fixture.stock.setBatchId(null); fixture.stock.setBatchCode(null);
        PdaPickBo request = fixture.bo(); request.setBatchCode(null);
        TransactionTemplate outer = new TransactionTemplate(manager); outer.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        var result = assertDoesNotThrow(() -> outer.execute(status -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, request)),
            "加入已有非可重复读事务时应拒绝未知空批次范围，而非悄悄开启新事务");
        assertNotNull(result, "必须返回明确来源拒绝");
        assertEquals(Boolean.FALSE, result.getOk(), "空批次完整执行范围不能在较弱事务条件下放行");
        verify(fixture.issueService, never().description("原事务隔离条件不符不得扣库")).execute(anyLong());
        verify(fixture.lineMapper, never().description("原事务隔离条件不符不得绑定来源")).updateById(any(WmProductionIssueLine.class));
    }
    @Test void inheritedReadCommittedBatchSourceCannotReachBindingOrExecution() {
        TransactionTemplate outer = new TransactionTemplate(manager);
        outer.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        var result = outer.execute(status -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()));
        assertNotNull(result, "必须明确返回原事务隔离不足的业务拒绝");
        assertEquals(Boolean.FALSE, result.getOk(), "有批次来源同样不能在较弱事务下依赖范围锁");
        verify(fixture.issueService, never().description("较弱原事务不能扣库")).execute(anyLong());
        verify(fixture.lineMapper, never().description("较弱原事务不能绑定来源")).updateById(any(WmProductionIssueLine.class));
    }
    @Test void inheritedDefaultRepeatableReadNonBatchUsesPhysicalOriginalConnection() throws Exception {
        physicalIsolation.set(Connection.TRANSACTION_REPEATABLE_READ);
        fixture.material.setIsBatchManage(0L); fixture.stock.setBatchId(null); fixture.stock.setBatchCode(null);
        PdaPickBo request = fixture.bo(); request.setBatchCode(null);
        TransactionTemplate outer = new TransactionTemplate(manager);
        var result = outer.execute(status -> {
            assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                "默认隔离的真实 Spring 事务元数据应为空，不能伪造显式隔离");
            return service.pick(PdaIssueServiceImplTest.ISSUE_ID, request);
        });
        assertNotNull(result, "默认隔离事务应返回明确结果");
        assertEquals(Boolean.TRUE, result.getOk(), "原连接实际可重复读的合法非批次来源应通过");
        verify(fixture.issueService, description("真实非批次来源仍沿原事务扣库")).execute(PdaIssueServiceImplTest.ISSUE_ID);
        verify(dataSource, times(1).description("核验实际隔离不能另开连接")).getConnection();
        verify(connection, description("完整执行后提交原连接")).commit();
    }
    @Test void inheritedDefaultReadCommittedBatchSourceIsRejected() {
        physicalIsolation.set(Connection.TRANSACTION_READ_COMMITTED);
        TransactionTemplate outer = new TransactionTemplate(manager);
        var result = outer.execute(status -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()));
        assertNotNull(result, "默认事务也须返回明确隔离拒绝");
        assertEquals(Boolean.FALSE, result.getOk(), "元数据为空不能掩盖原连接实际隔离不足");
        verify(fixture.issueService, never().description("实际较弱连接不得扣库")).execute(anyLong());
        verify(fixture.lineMapper, never().description("实际较弱连接不得绑定来源")).updateById(any(WmProductionIssueLine.class));
    }
    @Test void inheritedDefaultRepeatableReadBatchSourceStillSucceeds() throws Exception {
        physicalIsolation.set(Connection.TRANSACTION_REPEATABLE_READ);
        TransactionTemplate outer = new TransactionTemplate(manager);
        var result = outer.execute(status -> service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()));
        assertNotNull(result, "默认事务的批次来源应返回明确结果");
        assertEquals(Boolean.TRUE, result.getOk(), "真实可重复读连接的合法有批次来源应通过");
        verify(fixture.issueService, description("合法有批次来源沿原事务扣库")).execute(PdaIssueServiceImplTest.ISSUE_ID);
        verify(dataSource, times(1).description("来源核验不能拆分原事务")).getConnection();
    }
    @Test void databaseDateReadIsInsideSameOriginalTransactionConnection() {
        when(fixture.sourceMapper.currentDatabaseDate()).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "数据库日期读取必须参与来源锁所在原事务");
            ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
            assertNotNull(holder, "日期查询不能脱离原事务数据源");
            assertSame(connection, holder.getConnection(), "数据库日期与来源锁必须使用同一原连接");
            return fixture.databaseToday;
        });
        assertEquals(Boolean.TRUE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()).getOk(), "同一事务中来源与数据库日期都有效应放行");
    }
    @Test void existingPermissionAndIdempotencyAnnotationsRemainUnchanged() throws Exception {
        var pick = AppPdaIssueController.class.getMethod("pick", Long.class, PdaPickBo.class);
        assertArrayEquals(new String[]{"wm:productionissue:edit"}, pick.getAnnotation(SaCheckPermission.class).value(),
            "不得改动既有领料编辑授权边界");
        assertNotNull(pick.getAnnotation(PdaIdempotent.class), "既有 PDA 幂等入口必须保留");
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactional(T target, DataSourceTransactionManager manager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }
}
