package com.nexus.erp.prd;

import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.PrdWorkOrderTrackingPolicyService;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.erp.wm.service.WmSerialInventoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 在真实的轻量事务容器中验证必需依赖注入，构造或执行失败均关闭资源。
 */
class PrdWorkOrderTrackingPolicyContextTest {
    @BeforeAll
    static void metadata() {
        PrdWorkOrderServiceImplTest.initTableInfo();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {}

    @Test
    void requiredBankIsInjectedIntoTheRealReleaseTransactionContext() throws Exception {
        try (var f = new PrdWorkOrderTrackingPolicyReleaseTest.Fixture(true);
                var context = context(f, true)) {
            var service = context.getBean(PrdWorkOrderServiceImpl.class);
            PrdWorkOrderTrackingPolicyService bank =
                    AopTestUtils.getUltimateTargetObject(
                            context.getBean(PrdWorkOrderTrackingPolicyService.class));
            doAnswer(
                            a -> {
                                assertTrue(
                                        TransactionSynchronizationManager
                                                .isActualTransactionActive());
                                verifyNoInteractions(f.messages);
                                return "a".repeat(64);
                            })
                    .when(bank)
                    .freezeForRelease(any(PrdWorkOrder.class));
            service.release(1L);
            verify(bank).freezeForRelease(any(PrdWorkOrder.class));
            verify(f.messages).send(anyString(), anyString(), any());
            assertEquals("RELEASED", f.status());
        }
    }

    @Test
    void missingBankRemainsAStartupFailureAndAFollowingContextStillStarts() throws Exception {
        try (var f = new PrdWorkOrderTrackingPolicyReleaseTest.Fixture(true)) {
            var failure = assertThrows(BeansException.class, () -> context(f, false));
            assertTrue(failure.getMessage().contains("trackingPolicies"));
            try (var next = context(f, true)) {
                assertNotNull(next.getBean(PrdWorkOrderServiceImpl.class));
            }
        }
    }

    private AnnotationConfigApplicationContext context(
            PrdWorkOrderTrackingPolicyReleaseTest.Fixture f, boolean includeBank) {
        var result = new AnnotationConfigApplicationContext();
        try {
            result.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "trace", Map.of("nexus.trace.relay.enabled", "true")));
            result.register(TransactionConfig.class);
            result.registerBean(
                    PlatformTransactionManager.class, () -> f.db.tx.getTransactionManager());
            result.registerBean(
                    WmSerialInventoryService.class, () -> mock(WmSerialInventoryService.class));
            result.registerBean(
                    ProductionCompletionPostingMapper.class,
                    () -> mock(ProductionCompletionPostingMapper.class));
            if (includeBank) {
                result.registerBean(
                        PrdWorkOrderTrackingPolicyService.class,
                        () -> mock(PrdWorkOrderTrackingPolicyService.class));
            }
            result.registerBean(
                    "prdWorkOrderService", PrdWorkOrderServiceImpl.class, () -> f.orders);
            result.refresh();
            return result;
        } catch (RuntimeException | Error failure) {
            result.close();
            throw failure;
        }
    }
}
