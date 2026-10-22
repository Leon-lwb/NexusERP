package com.nexus.erp.wm;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

class WmCertifiedReceiptMutationGuardPresenceTest {
    @Test void ordinaryHeadLineAndDetailMutationsMustHaveCertifiedSourceGuard() throws Exception {
        Class<?> guard=assertDoesNotThrow(()->Class.forName("com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard"));
        for(String name:new String[]{"WmMaterialReceiveCrudServiceImpl","WmMaterialReceiveLineServiceImpl","WmMaterialReceiveDetailServiceImpl","WmMaterialReceiveExecutorServiceImpl"}) {
            Class<?> service=Class.forName("com.nexus.erp.wm.service.impl."+name);
            assertTrue(
                    Arrays.stream(service.getDeclaredFields())
                            .anyMatch(
                                    f ->
                                            f.getType() == guard
                                                    && Modifier.isFinal(f.getModifiers())),
                    name + " must not mutate certified source without the permanent guard");
        }
    }
}
