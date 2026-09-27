package com.nexus.ai.controller;

import com.nexus.ai.service.decision.*;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.satoken.utils.LoginHelper;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiDecisionControllerTest {
    @Test void runUsesServerSideIdentityAndReturnsSnowflakeIdWithoutRequestActor() {
        var workflow = mock(DeliveryRiskDecisionOrchestrator.class);
        var controller = new AiDecisionController(workflow, mock(DecisionApprovalService.class),
            mock(DecisionActionExecutionService.class), mock(DecisionQueryService.class));
        var identity = new LoginUser();
        identity.setMenuPermission(Set.of("ai:decision:run"));
        var request = new DeliveryRiskRunRequest();
        request.setBizType(DecisionBusinessType.WORK_ORDER); request.setBizId(9007199254740993L);
        var accepted = new DecisionRunAcceptedVo(9007199254740994L, DecisionRunStatus.RUNNING,
            "DELIVERY_RISK", LocalDateTime.of(2026, 9, 26, 12, 0));
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getLoginUser).thenReturn(identity);
            login.when(LoginHelper::getTenantId).thenReturn("TENANT-A");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getUsername).thenReturn("human");
            when(workflow.start(eq(request), any())).thenReturn(accepted);
            assertEquals(9007199254740994L, controller.run(request).getData().runId());
            var trusted = org.mockito.ArgumentCaptor.forClass(DecisionActor.class);
            verify(workflow).start(eq(request), trusted.capture());
            assertEquals("TENANT-A", trusted.getValue().tenantId());
            assertEquals(7L, trusted.getValue().userId());
        }
    }

    @Test void retryAndExportNeverShareQueryPermission() throws Exception {
        assertEquals("/ai/decision", AiDecisionController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertArrayEquals(new String[] { "ai:decision:execute" },
            AiDecisionController.class.getMethod("retry", Long.class).getAnnotation(SaCheckPermission.class).value());
        assertArrayEquals(new String[] { "ai:decision:export" },
            AiDecisionController.class.getMethod("export", Long.class).getAnnotation(SaCheckPermission.class).value());
    }
}
