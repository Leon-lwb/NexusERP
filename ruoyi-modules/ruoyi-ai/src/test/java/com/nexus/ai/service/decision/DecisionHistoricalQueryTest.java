package com.nexus.ai.service.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 历史读取只使用查询授权，不借用重新分析权限，不修改批准、草稿回执或运行状态。 */
class DecisionHistoricalQueryTest {
    private static final String TENANT = "TENANT-B";
    private static final DecisionActor VIEWER = actor(Set.of("ai:decision:query", "prd:work:query"));
    private static final DecisionBusinessRef ROOT =
        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private final DecisionPersistenceService persistence = mock(DecisionPersistenceService.class);
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final FlowNodeRunMapper nodes = mock(FlowNodeRunMapper.class);
    private final IndustrialDecisionDataPort data = mock(IndustrialDecisionDataPort.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final DecisionQueryService queries = new DecisionQueryService(persistence, runs, nodes,
        new ObjectMapper(), data, permissions);
    private AiDecisionRun run;
    private AiDecisionRunVo report;
    private AiDecisionAction draft;
    private AiDecisionEvidence evidence;
    private MockedStatic<SpringUtil> spring;
    private MockedStatic<LoginHelper> login;

    @BeforeEach
    void setup() {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(false);
        run = new AiDecisionRun();
        run.setId(99L); run.setTenantId(TENANT); run.setSceneCode("DELIVERY_RISK");
        run.setBizType("WORK_ORDER"); run.setBizId(11L); run.setBizCode("WO-11");
        run.setStatus("SUCCEEDED"); run.setFinishedAt(LocalDateTime.of(2026, 10, 6, 15, 0));
        report = new AiDecisionRunVo();
        report.setId(99L); report.setTenantId(TENANT); report.setSceneCode("DELIVERY_RISK");
        report.setBizType("WORK_ORDER"); report.setBizId(11L); report.setBizCode("WO-11");
        report.setStatus("SUCCEEDED");
        draft = new AiDecisionAction();
        draft.setId(200L); draft.setRunId(99L); draft.setTenantId(TENANT);
        draft.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
        draft.setExecutorCode("purchaseRequisitionDraft"); draft.setTitle("合成历史采购草稿");
        draft.setApprovalStatus("APPROVED"); draft.setExecutionStatus("SUCCEEDED");
        draft.setResultBizId(300L); draft.setResultBizCode("PR-SYNTHETIC");
        evidence = new AiDecisionEvidence();
        evidence.setId(400L); evidence.setRunId(99L); evidence.setTenantId(TENANT);
        evidence.setMetricCode("synthetic_evidence"); evidence.setMetricValue("1");
        when(persistence.requireRun(99L, TENANT)).thenReturn(run);
        when(persistence.listActions(99L)).thenReturn(List.of(draft));
        when(persistence.listEvidence(99L)).thenReturn(List.of(evidence));
        when(runs.selectVoById(99L)).thenReturn(report);
        when(permissions.getMenuPermission(7L)).thenReturn(VIEWER.permissions());
        when(data.canReadDecision(ROOT, VIEWER)).thenReturn(true);
    }

    @AfterEach
    void close() {
        TenantHelper.clearDynamic();
        login.close();
        spring.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "view", "evidence", "timeline", "export"})
    void historicalResultsUseReadPermissionWithoutReanalysisOrWrites(String operation) {
        Object result = TenantHelper.dynamic(TENANT, () -> read(operation, VIEWER));
        assertNotNull(result);
        if ("detail".equals(operation)) assertSame(report, result);
        if ("view".equals(operation)) {
            var view = (Map<?, ?>) result;
            assertSame(report, view.get("run"));
            assertEquals(List.of(draft), view.get("actions"));
            assertEquals(List.of(evidence), view.get("evidence"));
        }
        if ("evidence".equals(operation)) assertEquals(List.of(evidence), result);
        if ("timeline".equals(operation)) assertTrue(result.toString().contains("SUCCEEDED"));
        if ("export".equals(operation)) {
            assertTrue(result.toString().contains("APPROVED"));
            assertTrue(result.toString().contains("SUCCEEDED"));
        }
        assertEquals("SUCCEEDED", run.getStatus());
        assertEquals("APPROVED", draft.getApprovalStatus());
        assertEquals("SUCCEEDED", draft.getExecutionStatus());
        assertEquals(300L, draft.getResultBizId());
        verify(data).canReadDecision(ROOT, VIEWER);
        verifyNoMoreInteractions(data);
        verifyNoInteractions(nodes);
        verifyNoWrites();
    }

    @ParameterizedTest
    @MethodSource("revokedPermissions")
    void revokedRealtimePermissionsBlockAllHistoricalReadRoutes(Set<String> current) {
        when(permissions.getMenuPermission(7L)).thenReturn(current);
        for (String operation : List.of("detail", "view", "evidence", "timeline", "export")) {
            assertThrows(ServiceException.class, () -> TenantHelper.dynamic(TENANT, () -> read(operation, VIEWER)));
        }
        verifyNoInteractions(runs, nodes);
        verify(persistence, never()).listActions(anyLong());
        verify(persistence, never()).listEvidence(anyLong());
        verifyNoWrites();
    }

    static Stream<Set<String>> revokedPermissions() {
        return Stream.of(Set.of(), Set.of("ai:decision:query"), Set.of("prd:work:query"),
            Set.of("ai:decision:query", "so:orderheader:query"));
    }

    @Test
    void realtimePermissionsCannotAddBusinessRightsAbsentFromTrustedSession() {
        var limited = actor(Set.of("ai:decision:query"));
        assertThrows(ServiceException.class, () -> TenantHelper.dynamic(TENANT, () -> queries.view(99L, limited)));
        verify(data).canReadDecision(ROOT, limited);
        verifyNoInteractions(runs, nodes);
        verifyNoWrites();
    }

    @Test
    void wrongTenantContextBlocksHistoryBeforeAnyRunOrReportRead() {
        assertThrows(ServiceException.class, () -> TenantHelper.dynamic("TENANT-A", () -> queries.view(99L, VIEWER)));
        assertNull(TenantHelper.getDynamic());
        verifyNoInteractions(persistence, runs, nodes, data, permissions);
    }

    @Test
    void foreignOrMissingPersistedRunCannotReachReportActionsEvidenceOrBusinessPort() {
        when(persistence.requireRun(99L, TENANT)).thenThrow(new ServiceException("决策运行不存在或无权限访问"));
        assertThrows(ServiceException.class, () -> TenantHelper.dynamic(TENANT, () -> queries.view(99L, VIEWER)));
        verifyNoInteractions(runs, nodes, data);
        verify(persistence, never()).listActions(anyLong());
        verify(persistence, never()).listEvidence(anyLong());
        verifyNoWrites();
    }

    @Test
    void historicalReadGrantCannotBeUsedToRecalculateTerminalBusinessRoot() {
        var analyst = actor(Set.of("ai:decision:query", "ai:decision:run", "prd:work:query"));
        when(permissions.getMenuPermission(7L)).thenReturn(analyst.permissions());
        when(data.canReadDecision(ROOT, analyst)).thenReturn(true);
        when(data.canAccess(ROOT, analyst)).thenReturn(false);
        assertNotNull(TenantHelper.dynamic(TENANT, () -> queries.detail(99L, analyst)));

        var failure = assertThrows(ServiceException.class,
            () -> TenantHelper.dynamic(TENANT, () -> queries.recalculate(99L, analyst)));
        assertEquals("无权重新分析该业务对象", failure.getMessage());
        verify(data).canAccess(ROOT, analyst);
        verify(persistence, never()).requireHorizonDays(any());
        verifyNoWrites();
    }

    @Test
    void queryOnlyViewerStillCannotRecalculateHistory() {
        assertThrows(ServiceException.class, () -> TenantHelper.dynamic(TENANT, () -> queries.recalculate(99L, VIEWER)));
        verifyNoInteractions(persistence, runs, nodes, data);
    }

    private Object read(String operation, DecisionActor actor) {
        return switch (operation) {
            case "detail" -> queries.detail(99L, actor);
            case "view" -> queries.view(99L, actor);
            case "evidence" -> queries.evidence(99L, actor);
            case "timeline" -> queries.timeline(99L, actor);
            case "export" -> queries.exportMarkdown(99L, actor);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private void verifyNoWrites() {
        verify(persistence, never()).transition(anyLong(), any(), any());
        verify(persistence, never()).setActionApproval(any(), any(), any(), any(), any());
        verify(persistence, never()).saveActionResult(any(), any(), anyInt());
    }

    private static DecisionActor actor(Set<String> permissions) {
        return new DecisionActor(TENANT, 7L, "viewer", permissions);
    }
}
