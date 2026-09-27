package com.nexus.ai.service.decision;

import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionQueryServiceTest {
    @org.junit.jupiter.api.BeforeAll static void initNodeMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
            com.nexus.common.flow.domain.entity.FlowNodeRun.class);
    }
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final FlowNodeRunMapper nodes = mock(FlowNodeRunMapper.class);
    private final DecisionQueryService query = new DecisionQueryService(store, runs, nodes,
        new com.fasterxml.jackson.databind.ObjectMapper());

    @Test void recalculateKeepsPersistedHorizonAndScenarioScope() {
        var run = new com.nexus.common.ai.domain.entity.AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setBizType("WORK_ORDER"); run.setBizId(11L);
        run.setScoreConfigJson("{\"horizonDays\":14,\"scenarioCodes\":[\"BASELINE_CURRENT\",\"APS_CR\"]}");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.requireHorizonDays(run)).thenReturn(14);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            var form = com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A",
                () -> query.recalculate(99L, "TENANT-A"));
            assertEquals(14, form.getHorizonDays());
            assertEquals(java.util.List.of("BASELINE_CURRENT", "APS_CR"), form.getScenarioCodes());
            assertTrue(form.getForceRestart());
        }
    }

    @Test void timelineBeforeFlowIdsAreAttachedRemainsReadable() {
        var run = new com.nexus.common.ai.domain.entity.AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setStatus("CREATED");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.listActions(99L)).thenReturn(java.util.List.of());
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            var events = com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A",
                () -> query.timeline(99L, "TENANT-A"));
            assertTrue(events.isEmpty());
            verifyNoInteractions(nodes);
        }
    }

    @Test void emptyFilterValuesAreNotMistakenForUnknownRiskOrStatus() {
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            doAnswer(call -> new com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.nexus.common.ai.domain.vo.AiDecisionRunVo>(1, 20))
                .when(runs).selectSummaryPage(any(), any());
            com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A", () -> {
                assertDoesNotThrow(() -> query.list("TENANT-A", "", "", new PageQuery(20, 1)));
            });
        }
    }

    @Test void markdownExportRedactsSecretsWithoutTruncationException() {
        var row = new com.nexus.common.ai.domain.entity.AiDecisionRun();
        row.setId(99L); row.setTenantId("TENANT-A");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(row);
        var vo = new com.nexus.common.ai.domain.vo.AiDecisionRunVo();
        vo.setTenantId("TENANT-A"); vo.setSceneCode("DELIVERY_RISK");
        vo.setBizType("WORK_ORDER"); vo.setBizId(11L);
        when(runs.selectVoById(99L)).thenReturn(vo);
        var evidence = new com.nexus.common.ai.domain.entity.AiDecisionEvidence();
        evidence.setSourceDomain("ERP"); evidence.setSourceType("STOCK");
        evidence.setMetricCode("stock_qty"); evidence.setMetricValue("Bearer secretLongText1234567890");
        when(store.listEvidence(99L)).thenReturn(java.util.List.of(evidence));
        when(store.listActions(99L)).thenReturn(java.util.List.of());
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            String markdown = com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A",
                () -> query.exportMarkdown(99L, "TENANT-A"));
            assertFalse(markdown.contains("secretLongText1234567890"));
            assertTrue(markdown.contains("[REDACTED]"));
        }
    }

    @Test void timelineProjectsOnlyNodeStatusInsteadOfLoadingMegabyteFlowPayloads() {
        var run = new com.nexus.common.ai.domain.entity.AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setAnalysisFlowInstanceId(101L);
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.listActions(99L)).thenReturn(java.util.List.of());
        var node = new com.nexus.common.flow.domain.entity.FlowNodeRun();
        node.setNodeId("llm"); node.setStatus("RUNNING");
        when(nodes.selectList(any())).thenReturn(java.util.List.of(node));
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            var result = com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A",
                () -> query.timeline(99L, "TENANT-A"));
            assertEquals("llm", result.get(0).get("code"));
        }
        var captured = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(nodes).selectList(captured.capture());
        String columns = ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) captured.getValue()).getSqlSelect();
        assertNotNull(columns);
        assertTrue(columns.contains("node_id"));
        assertTrue(columns.contains("status"));
        assertFalse(columns.contains("input_json"));
        assertFalse(columns.contains("output_json"));
    }

    @Test void sameTenantWithoutObjectReadPermissionCannotSeeDecisionDetail() {
        var port = mock(com.nexus.common.ai.decision.port.IndustrialDecisionDataPort.class);
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var guarded = new DecisionQueryService(store, runs, nodes,
            new com.fasterxml.jackson.databind.ObjectMapper(), port, rights);
        var actor = new com.nexus.common.ai.decision.dto.DecisionActor("TENANT-A", 7L, "viewer",
            java.util.Set.of("ai:decision:query", "prd:work:query"));
        var run = new com.nexus.common.ai.domain.entity.AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setBizType("WORK_ORDER"); run.setBizId(11L);
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(rights.getMenuPermission(7L)).thenReturn(actor.permissions());
        when(port.canReadDecision(any(), any())).thenReturn(false);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            assertThrows(ServiceException.class, () -> com.nexus.common.tenant.helper.TenantHelper.dynamic("TENANT-A",
                () -> guarded.detail(99L, actor)));
        }
        verify(runs, never()).selectVoById(any());
    }

    @Test void wrongTenantIsRejectedBeforeReadingAnyReportOrFlowOutput() {
        when(store.requireRun(99L, "TENANT-A")).thenThrow(new ServiceException("not found"));
        assertThrows(ServiceException.class, () -> query.detail(99L, "TENANT-A"));
        verifyNoInteractions(runs, nodes);
    }
}
