package com.nexus.erp.pu;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceSchemaVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 离线契约：使用模拟 Mapper，仅设置事务线程本地状态，绝不是真实数据库锁。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuRequirementMaterialLockServiceTest {

    private static final String TENANT = "t-100";

    @Mock
    private PuRequirementMaterialGuardMapper mapper;

    @Mock
    private ErpPurchaseSourceSchemaVerifier schemaVerifier;

    @AfterEach
    void clearTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void entryRequiresExistingErpProxyTransaction() throws NoSuchMethodException {
        assertNotNull(PuRequirementMaterialLockService.class.getAnnotation(Service.class));
        Method method = PuRequirementMaterialLockService.class.getMethod("lock", String.class, Collection.class);
        assertEquals("erp", method.getAnnotation(DS.class).value());
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertEquals(Propagation.MANDATORY, transaction.propagation());
    }

    @Test
    void disabledTenantIsolationDeniesBeforeMapper() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(false);
            assertThrows(ServiceException.class, () -> service().lock(TENANT, List.of(1L)));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void missingOrDifferentTenantDeniesWithoutAcquiringAnyRow() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        for (String current : Arrays.asList(null, "", " ", "other", TENANT)) {
            try (MockedStatic<TenantHelper> tenant = tenant(current)) {
                Collection<String> requests = TENANT.equals(current)
                    ? Arrays.asList(null, "", " ", "T-100", "t-100 ") : List.of(TENANT);
                for (String request : requests) {
                    assertThrows(ServiceException.class, () -> service().lock(request, List.of(1L)));
                }
                verifyNoInteractions(mapper);
            }
        }
    }

    @Test
    void noActiveTransactionDeniesBeforeMapper() {
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            assertThrows(ServiceException.class, () -> service().lock(TENANT, List.of(1L)));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void nullEmptyAndInvalidIdsDenyWholeBatchBeforeMapper() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            List<Collection<Long>> invalid = Arrays.asList(null, List.of(), List.of(0L),
                List.of(-1L), Arrays.asList(3L, null, 2L));
            for (Collection<Long> ids : invalid) {
                assertThrows(ServiceException.class, () -> service().lock(TENANT, ids));
            }
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void duplicateAndUnsortedMaterialsLockInAscendingOrderWithEnsureBeforeEachSelect() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            when(mapper.lockVersion(TENANT, 2L)).thenReturn(0);
            when(mapper.lockVersion(TENANT, 5L)).thenReturn(7);
            when(mapper.lockVersion(TENANT, 9L)).thenReturn(11);
            Map<Long, Integer> versions = service().lock(TENANT, List.of(9L, 5L, 2L, 9L, 5L));

            assertEquals(List.of(2L, 5L, 9L), new ArrayList<>(versions.keySet()));
            assertEquals(Map.of(2L, 0, 5L, 7, 9L, 11), versions);
            InOrder order = inOrder(schemaVerifier, mapper);
            order.verify(schemaVerifier).requireTransactionalSources();
            order.verify(mapper).ensureGuard(TENANT, 2L);
            order.verify(mapper).lockVersion(TENANT, 2L);
            order.verify(mapper).ensureGuard(TENANT, 5L);
            order.verify(mapper).lockVersion(TENANT, 5L);
            order.verify(mapper).ensureGuard(TENANT, 9L);
            order.verify(mapper).lockVersion(TENANT, 9L);
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void singleMaterialAcceptsExistingGuardWithoutInventingOtherLocks() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            /** guard 行已经存在时，INSERT IGNORE 返回 0。 */
            when(mapper.ensureGuard(TENANT, 7L)).thenReturn(0);
            when(mapper.lockVersion(TENANT, 7L)).thenReturn(0);
            assertEquals(Map.of(7L, 0), service().lock(TENANT, List.of(7L)));
            InOrder order = inOrder(schemaVerifier, mapper);
            order.verify(schemaVerifier).requireTransactionalSources();
            order.verify(mapper).ensureGuard(TENANT, 7L);
            order.verify(mapper).lockVersion(TENANT, 7L);
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void missingOrNullVersionRejectsAndStopsRemainingMaterials() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            when(mapper.lockVersion(TENANT, 2L)).thenReturn(0);
            /** Mockito 默认将未设桩的 Integer 返回为 0；必须显式模拟行缺失或 SQL NULL。 */
            when(mapper.lockVersion(TENANT, 5L)).thenReturn(null);
            assertThrows(ServiceException.class, () -> service().lock(TENANT, List.of(8L, 5L, 2L)));
            InOrder order = inOrder(schemaVerifier, mapper);
            order.verify(schemaVerifier).requireTransactionalSources();
            order.verify(mapper).ensureGuard(TENANT, 2L);
            order.verify(mapper).lockVersion(TENANT, 2L);
            order.verify(mapper).ensureGuard(TENANT, 5L);
            order.verify(mapper).lockVersion(TENANT, 5L);
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void ungatedConstructorCannotWriteGuardEvenWithActiveThreadLocalTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            assertThrows(ServiceException.class, () -> new PuRequirementMaterialLockService(mapper).lock(TENANT,List.of(1L)));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void schemaRejectionOccursAtRealGuardWriteBoundary() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            org.mockito.Mockito.doThrow(new ServiceException("unproved schema")).when(schemaVerifier).requireTransactionalSources();
            assertThrows(ServiceException.class, () -> service().lock(TENANT,List.of(1L)));
            org.mockito.Mockito.verify(schemaVerifier).requireTransactionalSources();
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void activeThreadLocalCannotGrantRealSourceChainOwnership() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var schemaMapper = org.mockito.Mockito.mock(com.nexus.erp.pu.mapper.ErpPurchaseSourceSchemaMapper.class);
        var qualification = new com.nexus.erp.pu.service.PuRequirementErpTransactionQualification(
            org.mockito.Mockito.mock(org.apache.ibatis.session.SqlSessionFactory.class));
        var realVerifier = new ErpPurchaseSourceSchemaVerifier(schemaMapper,qualification);
        try (MockedStatic<TenantHelper> ignored = tenant(TENANT)) {
            assertThrows(ServiceException.class, () -> new PuRequirementMaterialLockService(mapper,realVerifier).lock(TENANT,List.of(1L)));
            verifyNoInteractions(mapper,schemaMapper);
        }
    }

    private PuRequirementMaterialLockService service() {
        return new PuRequirementMaterialLockService(mapper, schemaVerifier);
    }

    private MockedStatic<TenantHelper> tenant(String current) {
        MockedStatic<TenantHelper> mocked = mockStatic(TenantHelper.class);
        mocked.when(TenantHelper::isEnable).thenReturn(true);
        mocked.when(TenantHelper::getTenantId).thenReturn(current);
        return mocked;
    }
}
