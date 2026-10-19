package com.nexus.erp.prd;

import com.nexus.erp.prd.controller.PrdWorkOrderPolicyAdoptionController;
import com.nexus.erp.prd.service.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrdWorkOrderPolicyAdoptionEntryTest {
    @Test
    void callableControllerEntryStartsTheErpTransactionAndReturnsTheFrozenResult()
            throws Exception {
        try (var f = new PrdWorkOrderPolicyAdoptionServiceTest.Fixture()) {
            f.authorize();
            var controller = new PrdWorkOrderPolicyAdoptionController(entry(f));
            var response =
                    controller.apply(
                            new PrdWorkOrderPolicyAdoptionController.Command(700L, "adopt-700", 1));
            assertNotNull(response.getData().snapshotHash());
            assertEquals("APPLIED", f.status());
            assertEquals(3, f.db.count());
            assertEquals(
                    response.getData().snapshotHash(),
                    controller
                            .apply(
                                    new PrdWorkOrderPolicyAdoptionController.Command(
                                            700L, "adopt-700", 1))
                            .getData()
                            .snapshotHash());
        }
    }

    @Test
    void callablePreviewProvidesOnlyRealSourceCasAndNeverCreatesAnApprovalOrBank()
            throws Exception {
        try (var f = new PrdWorkOrderPolicyAdoptionServiceTest.Fixture()) {
            var result = entry(f).preview(1L);
            assertEquals(java.util.List.of(10L, 20L, 30L), result.materialIds());
            assertEquals(0, f.db.count());
            assertEquals(
                    0,
                    f.db.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_work_order_policy_adoption", Integer.class));
        }
    }

    @Test
    void anotherAuthenticatedActorCannotApplySomeoneElsesIndependentApproval() throws Exception {
        try (var f = new PrdWorkOrderPolicyAdoptionServiceTest.Fixture()) {
            f.authorize();
            when(f.authorizer.requireApprover()).thenReturn(33L);
            assertThrows(RuntimeException.class, () -> entry(f).apply(700L, "adopt-700", 1));
            assertEquals(0, f.db.count());
            assertEquals("APPROVED", f.status());
        }
    }

    @Test
    void aPermissionFailureCannotReachBankWritesThroughTheCallableEntry() throws Exception {
        try (var f = new PrdWorkOrderPolicyAdoptionServiceTest.Fixture()) {
            f.authorize();
            when(f.authorizer.requireApprover())
                    .thenThrow(new com.nexus.common.core.exception.ServiceException("denied"));
            assertThrows(RuntimeException.class, () -> entry(f).apply(700L, "adopt-700", 1));
            assertEquals(0, f.db.count());
            assertEquals(1, f.version());
        }
    }

    static PrdWorkOrderPolicyAdoptionEntry entry(PrdWorkOrderPolicyAdoptionServiceTest.Fixture f) {
        var proxy =
                new ProxyFactory(
                        new PrdWorkOrderPolicyAdoptionEntry(f.service, f.db.service, f.authorizer));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(
                new TransactionInterceptor(
                        f.db.tx.getTransactionManager(),
                        new AnnotationTransactionAttributeSource()));
        return (PrdWorkOrderPolicyAdoptionEntry) proxy.getProxy();
    }
}
