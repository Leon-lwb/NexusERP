package com.nexus.erp.pu;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceSchemaVerifier;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 永久结果读回仅锁既有guard；此为离线调用链测试，不证明真实数据库行锁。 */
class PuRequirementExistingMaterialLockTest {
    static final String TENANT="000000";
    PuRequirementMaterialGuardMapper mapper;
    ErpPurchaseSourceSchemaVerifier schema;
    PuRequirementMaterialLockService service;
    MockedStatic<TenantHelper> tenants;
    @BeforeEach void setup() {
        mapper=mock(PuRequirementMaterialGuardMapper.class);schema=mock(ErpPurchaseSourceSchemaVerifier.class);
        service=new PuRequirementMaterialLockService(mapper,schema);
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    @AfterEach void close() { tenants.close();TransactionSynchronizationManager.setActualTransactionActive(false); }
    Method entry() {
        return Arrays.stream(PuRequirementMaterialLockService.class.getMethods())
            .filter(m->m.getName().equals("lockExisting")).findFirst()
            .orElseGet(()->{fail("缺少仅锁既有guard的永久恢复入口");return null;});
    }
    @SuppressWarnings("unchecked") Map<Long,Integer> read(String tenant,Collection<Long> ids) {
        try { return (Map<Long,Integer>)entry().invoke(service,tenant,ids); }
        catch(InvocationTargetException e) { throw (RuntimeException)e.getCause(); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Test void entryMustJoinExistingErpTransaction() {
        var method=entry();assertEquals("erp",method.getAnnotation(DS.class).value());
        assertEquals(Propagation.MANDATORY,method.getAnnotation(Transactional.class).propagation());
    }
    @Test void existingGuardLocksAreSortedDeduplicatedAndNeverProvisioned() {
        when(mapper.lockVersion(TENANT,2L)).thenReturn(3);when(mapper.lockVersion(TENANT,5L)).thenReturn(7);
        var found=read(TENANT,List.of(5L,2L,5L));assertEquals(List.of(2L,5L),new ArrayList<>(found.keySet()));
        assertEquals(Map.of(2L,3,5L,7),found);assertThrows(UnsupportedOperationException.class,()->found.put(9L,0));
        var order=inOrder(schema,mapper);order.verify(schema).requireTransactionalSources();
        order.verify(mapper).lockVersion(TENANT,2L);order.verify(mapper).lockVersion(TENANT,5L);verifyNoMoreInteractions(mapper);
    }
    @Test void missingGuardIsNotCreatedForARecoveryQuery() {
        when(mapper.lockVersion(TENANT,1L)).thenReturn(null);
        assertThrows(ServiceException.class,()->read(TENANT,List.of(1L,2L)));
        verify(mapper,never()).ensureGuard(anyString(),anyLong());verify(mapper,never()).lockVersion(TENANT,2L);
    }
    @Test void unknownOrExhaustedGuardVersionDoesNotBecomeAReadableResult() {
        for(int bad:new int[]{-1,Integer.MAX_VALUE}) {
            when(mapper.lockVersion(TENANT,1L)).thenReturn(bad);
            assertThrows(ServiceException.class,()->read(TENANT,List.of(1L)));
        }
        verify(mapper,never()).ensureGuard(anyString(),anyLong());
    }
    @Test void missingTenantTransactionOrInvalidIdentityDeniesBeforeAnyMapper() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThrows(ServiceException.class,()->read(TENANT,List.of(1L)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(ServiceException.class,()->read("000001",List.of(1L)));
        assertThrows(ServiceException.class,()->read(TENANT,Arrays.asList(1L,null)));
        tenants.when(TenantHelper::isEnable).thenReturn(false);
        assertThrows(ServiceException.class,()->read(TENANT,List.of(1L)));verifyNoInteractions(mapper);
    }
    @Test void schemaFailureNeverEnsuresOrLocksARow() {
        doThrow(new ServiceException("未认证结构")).when(schema).requireTransactionalSources();
        assertThrows(ServiceException.class,()->read(TENANT,List.of(1L)));verifyNoInteractions(mapper);
    }
}
