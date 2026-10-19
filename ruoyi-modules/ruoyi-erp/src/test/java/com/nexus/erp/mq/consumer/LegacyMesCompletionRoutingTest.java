package com.nexus.erp.mq.consumer;

import com.nexus.erp.prd.service.IPrdWorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyMesCompletionRoutingTest {
    private int consumers(String enabled) {
        try (var context = new AnnotationConfigApplicationContext()) {
            if (enabled != null) {
                context.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource(
                                        "trace-test",
                                        Map.of("nexus.trace.relay.enabled", enabled)));
            }
            context.registerBean(
                    IPrdWorkOrderService.class, () -> mock(IPrdWorkOrderService.class));
            context.register(ErpWorkOrderCompletedConsumer.class);
            context.refresh();
            return context.getBeansOfType(ErpWorkOrderCompletedConsumer.class).size();
        }
    }

    @Test
    void compatibilityDeploymentRetainsTheLegacyQuantityCompletionListener() {
        assertEquals(1, consumers(null));
        assertEquals(1, consumers("false"));
    }

    @Test
    void enablingNewProtocolRemovesTheLegacyMesCompletionEffectPath() {
        assertEquals(0, consumers("true"));
    }
}
