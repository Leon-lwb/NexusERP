package com.nexus.erp.wm;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.impl.WmMaterialReceiveCrudServiceImpl;
import com.nexus.workflow.service.IWfProcessService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 事务代理与两连接内存锁回归，不访问现有业务数据库或模拟生产签发。 */
class WmMaterialReceiveSubmitTransactionTest {
    private JdbcDataSource dataSource;
    private DataSourceTransactionManager manager;
    private TransactionTemplate preparation;
    private WmMaterialReceiveMapper mapper;
    private WmCertifiedReceiptMutationGuard guard;
    private IWfProcessService process;
    private PuRequirementErpTransactionExecutor executor;
    private WmMaterialReceiveCrudServiceImpl proxy;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:receipt_submit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=300");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE wm_material_receive(receive_id BIGINT PRIMARY KEY,status VARCHAR(32))");
            statement.execute("INSERT INTO wm_material_receive VALUES(21,'DRAFT')");
        }
        manager = new DataSourceTransactionManager(dataSource);
        preparation = new TransactionTemplate(manager);
        mapper = mock(WmMaterialReceiveMapper.class);
        guard = mock(WmCertifiedReceiptMutationGuard.class);
        process = mock(IWfProcessService.class);
        executor = mock(PuRequirementErpTransactionExecutor.class);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        doAnswer(invocation -> {
            Supplier<?> callback = invocation.getArgument(1);
            return preparation.execute(status -> callback.get());
        }).when(executor).executeRepeatableRead(eq("000000"), any());
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "来源保护必须处于短 ERP 事务内");
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try (Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT receive_id FROM wm_material_receive WHERE receive_id=21 FOR UPDATE");
            }
            return null;
        }).when(guard).requireUnposted(21L);
        when(mapper.selectById(21L)).thenReturn(head("DRAFT", null));
        WmMaterialReceiveCrudServiceImpl target = mock(WmMaterialReceiveCrudServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(target,"baseMapper",mapper);
        ReflectionTestUtils.setField(target,"certifiedReceiptGuard",guard);
        ReflectionTestUtils.setField(target,"processService",process);
        // 红阶段字段还不存在，仍可运行旧实际入口以证明旧事务边界错误。
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(),"receiptSubmissionTransactions") != null) {
            ReflectionTestUtils.setField(target,"receiptSubmissionTransactions",executor);
        }
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        proxy = (WmMaterialReceiveCrudServiceImpl) factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        tenant.close();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    }

    @Test
    void preparationCommitsAndReleasesHeadBeforeStartingMasterWorkflow() throws Exception {
        when(process.startProcessByDataId(eq("21"),eq("42"),eq("wmMaterialReceiveCrudService"),anyMap())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "发起 master 流程时不能携带 ERP 事务及单据头锁");
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
            // 独立回调连接必须能立即锁定同一行，不能等待已挂起的外层提交事务。
            try (Connection callback = dataSource.getConnection(); Statement statement = callback.createStatement()) {
                callback.setAutoCommit(false);
                assertEquals(1,statement.executeUpdate("UPDATE wm_material_receive SET status='APPROVING' WHERE receive_id=21"));
                callback.commit();
            }
            Map<String,Object> variables = invocation.getArgument(3);
            assertEquals("21",variables.get("dataId"));
            assertEquals(Boolean.TRUE,variables.get("requireTaskOnStart"));
            assertFalse(variables.containsKey("needSign"));
            return R.ok();
        });
        assertTrue(proxy.submit("21","42"));
        verify(guard).requireUnposted(21L);
        verify(executor).executeRepeatableRead(eq("000000"),any());
        verify(mapper,never()).updateById(any(WmMaterialReceive.class));
    }

    @Test
    void existingCallerTransactionIsRejectedBeforeAnyGuardOrWorkflow() {
        assertThrows(IllegalTransactionStateException.class,()->preparation.execute(status->proxy.submit("21","42")));
        verifyNoInteractions(guard,mapper,process,executor);
    }

    @Test
    void approvedReceiptCannotBeResubmitted() {
        when(mapper.selectById(21L)).thenReturn(head("APPROVED",42L));
        assertThrows(ServiceException.class,()->proxy.submit("21","42"));
        verifyNoInteractions(process);
    }

    @Test
    void missingTemplateCannotCreateWorkflow() {
        assertThrows(ServiceException.class,()->proxy.submit("21",null));
        verifyNoInteractions(process);
    }

    @Test
    void savedTemplateRemainsValidAndStillRequiresRealTask() {
        when(mapper.selectById(21L)).thenReturn(head("REJECTED",43L));
        when(process.startProcessByDataId(eq("21"),eq("43"),eq("wmMaterialReceiveCrudService"),anyMap())).thenReturn(R.ok());
        assertTrue(proxy.submit("21",null));
        verify(process).startProcessByDataId(eq("21"),eq("43"),eq("wmMaterialReceiveCrudService"),argThat(vars->Boolean.TRUE.equals(vars.get("requireTaskOnStart"))));
    }

    @Test
    void permanentSourceFailureStopsWorkflowAndKeepsExistingHead() {
        doThrow(new ServiceException("收货来源已冻结")).when(guard).requireUnposted(21L);
        assertThrows(ServiceException.class,()->proxy.submit("21","42"));
        verifyNoInteractions(mapper,process);
    }

    @Test
    void workflowFailureCannotBeClaimedAsReceiptApproval() {
        when(process.startProcessByDataId(anyString(),anyString(),anyString(),anyMap())).thenReturn(R.fail("流程未创建有效待办"));
        assertThrows(ServiceException.class,()->proxy.submit("21","42"));
        verify(mapper,never()).updateById(any(WmMaterialReceive.class));
    }

    @Test
    void actionableValidationErrorsRemainOutsideNativeExceptionRedaction() {
        doAnswer(invocation -> {
            Supplier<?> callback = invocation.getArgument(1);
            try {
                return preparation.execute(status -> callback.get());
            } catch (ServiceException validation) {
                throw new ServiceException("ERP 专属准备拒绝");
            }
        }).when(executor).executeRepeatableRead(eq("000000"), any());
        when(mapper.selectById(21L)).thenReturn(head("APPROVED",42L));
        ServiceException status = assertThrows(ServiceException.class,()->proxy.submit("21","42"));
        assertTrue(status.getMessage().contains("当前状态不允许提交审批"),"不能让执行器掩盖可操作的单据状态提示");
        when(mapper.selectById(21L)).thenReturn(head("DRAFT",null));
        ServiceException flow = assertThrows(ServiceException.class,()->proxy.submit("21",null));
        assertTrue(flow.getMessage().contains("选择审批流程"),"缺少模板的正常提示不应变为物理事务故障");
        verifyNoInteractions(process);
    }

    @Test
    void existingSynchronizationWithoutPhysicalTransactionIsRejected() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalTransactionStateException.class,()->proxy.submit("21","42"));
            verifyNoInteractions(guard,mapper,process,executor);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void unrelatedBoundResourceIsNotTouchedOrBorrowed() {
        Object key = new Object();Object holder = new Object();
        TransactionSynchronizationManager.bindResource(key,holder);
        try {
            assertThrows(IllegalTransactionStateException.class,()->proxy.submit("21","42"));
            assertSame(holder,TransactionSynchronizationManager.getResource(key));
            verifyNoInteractions(guard,mapper,process,executor);
        } finally {
            TransactionSynchronizationManager.unbindResource(key);
        }
    }

    @Test
    void preparationCommitOrCleanupFailureNeverStartsWorkflow() {
        doThrow(new ServiceException("ERP 准备提交或清理结果未知"))
            .when(executor).executeRepeatableRead(eq("000000"), any());
        assertThrows(ServiceException.class,()->proxy.submit("21","42"));
        verifyNoInteractions(guard,mapper,process);
    }

    private WmMaterialReceive head(String status,Long selected) {
        WmMaterialReceive head = new WmMaterialReceive();head.setReceiveId(21L);head.setReceiveCode("CGRK20261004001");
        head.setReceiveName("镀锌螺栓采购入库");head.setVendorId(2L);head.setVendorName("江苏精密紧固件制造厂");
        head.setStatus(status);head.setSelectFlowId(selected);return head;
    }
}
