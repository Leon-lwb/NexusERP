package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.erp.pu.controller.PuCertifiedPurchaseController;
import com.nexus.erp.pu.service.PuCertifiedPurchaseAuthorizer;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

/** 共享登录变化时的只读身份复核，不调用会清动态租户的通用getInfo。 */
class PuCertifiedPurchaseContextTest {
    @Test void endpointUsesLiveProtectedContextInsteadOfCachedClientIdentity() throws Exception {
        var method = PuCertifiedPurchaseController.class.getMethod("context");
        assertArrayEquals(new String[]{"/context"}, method.getAnnotation(GetMapping.class).value());
        assertArrayEquals(
                new String[] {"pu:requisition:add"},
                method.getAnnotation(SaCheckPermission.class).value());
    }
    @Test void contextReadOnlyConsultsTheCurrentAuthorizerAndNeverInitializesBusinessState() throws Exception {
        var constructor = PuCertifiedPurchaseWriteService.class.getConstructors()[0];
        var authorizer = mock(PuCertifiedPurchaseAuthorizer.class);
        var actor = new DecisionActor("000000", 7L, "测试操作人", Set.of("pu:requisition:add"));
        when(authorizer.requireUser()).thenReturn(actor);
        var others = new ArrayList<Object>();
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type -> {
            if (type == PuCertifiedPurchaseAuthorizer.class) return authorizer;
            Object value = mock(type); others.add(value); return value;
        }).toArray();
        var service = (PuCertifiedPurchaseWriteService) constructor.newInstance(args);
        assertEquals(actor, service.getClass().getMethod("currentActorContext").invoke(service));
        verify(authorizer).requireUser(); verifyNoMoreInteractions(authorizer);
        verifyNoInteractions(others.toArray());
    }
}
