package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class DecisionQueryServiceTest {
    @BeforeAll
    static void initNodeMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlowNodeRun.class);
    }
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final FlowNodeRunMapper nodes = mock(FlowNodeRunMapper.class);
    private final DecisionQueryService query =
            new DecisionQueryService(store, runs, nodes, new ObjectMapper());

    @Test void completeV2ViewHasNoBlockReasonAndDoesNotWriteOrReadErp() {
        var run = DecisionPurchaseContractGuardTest.v2Run();
        var purchase = DecisionPurchaseContractGuardTest.v2Action();
        when(store.requireRun(10L, "000000")).thenReturn(run);
        when(store.listActions(10L)).thenReturn(List.of(purchase));
        when(store.listEvidence(10L)).thenReturn(List.of());
        var vo = new AiDecisionRunVo();
        vo.setId(10L);
        vo.setTenantId("000000");
        vo.setSceneCode("DELIVERY_RISK");
        when(runs.selectVoById(10L)).thenReturn(vo);
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var value = TenantHelper.dynamic("000000", () -> query.view(10L, "000000"));
            assertTrue(value.containsKey("actionBlockReasons"), "新合同必须收到服务端明确完成的结构检查结果");
            assertEquals(Map.of(), value.get("actionBlockReasons"), "完整新版合同应返回明确空阻断表，不访问ERP或补版本");
        }
        verify(store, never()).transition(anyLong(), any(), any());
        verify(store, never()).setActionApproval(any(), any(), any(), any(), any());
        verify(store, never()).saveActionResult(any(), any(), anyInt());
    }

    @Test void viewMarksHistoricalPurchaseAsUnapprovableWithoutLeakingPayload() {
        var run = viewRun();
        var purchase = purchaseAction("{\"runId\":\"99\",\"note\":\"sensitive-payload-marker\"}");
        var result = viewFor(run, List.of(purchase));

        assertEquals(Set.of("run", "actions", "evidence", "actionBlockReasons"), result.keySet());
        var reasons = (Map<?, ?>) result.get("actionBlockReasons");
        assertEquals(Set.of("21"), reasons.keySet());
        String reason = (String) reasons.get("21");
        assertTrue(reason.contains("不可批准"));
        assertTrue(reason.contains("重新分析"));
        assertTrue(reason.length() <= 100);
        assertFalse(reason.contains("sensitive-payload-marker"));
        assertFalse(reason.contains("runId"));
        verify(store, never()).setActionApproval(any(), any(), any(), any(), any());
    }

    @Test void viewStillBlocksPersistedPurchaseClaimingV2WhileP0IsDisabled() {
        var run = viewRun();
        run.setDecisionContractVersion(2);
        var purchase = purchaseAction("{\"runId\":\"99\",\"purchase_payload_version\":2,"
            + "\"materialLines\":[{\"base_uom_id\":\"1\",\"requirement_detail_ids\":[\"500\"]}]}");
        purchase.setPurchasePayloadVersion(2);

        var reasons = (Map<?, ?>) viewFor(run, List.of(purchase)).get("actionBlockReasons");
        assertNotNull(reasons);
        assertTrue(((String) reasons.get("21")).contains("不可批准"));
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run, purchase));
    }

    @Test void viewDoesNotFlagNonPurchaseActionsIncludingAlongsidePurchase() {
        var run = viewRun();
        var notice = new AiDecisionAction();
        notice.setId(22L); notice.setRunId(99L); notice.setTenantId("TENANT-A");
        notice.setActionType("SEND_MANAGER_NOTIFICATION"); notice.setExecutorCode("managerNotification");
        notice.setPayloadJson("not-json");
        var onlyNotice = viewFor(run, List.of(notice));
        assertEquals(
                Set.of("run", "actions", "evidence", "actionBlockReasons"), onlyNotice.keySet());
        assertEquals(Map.of(), onlyNotice.get("actionBlockReasons"), "非采购也返回明确已检查空表，原动作本身不变");
        assertEquals(List.of(notice), onlyNotice.get("actions"));

        var mixed = viewFor(run, List.of(notice, purchaseAction("{}")));
        var reasons = (Map<?, ?>) mixed.get("actionBlockReasons");
        assertEquals(Set.of("21"), reasons.keySet());
        assertFalse(reasons.containsKey("22"));
    }

    @Test void viewDoesNotExposeActionsOrReasonsForAnotherTenantRun() {
        when(store.requireRun(99L, "TENANT-A")).thenThrow(new ServiceException("决策运行不存在或无权限访问"));
        var actor =
                new DecisionActor(
                        "TENANT-A", 7L, "viewer", Set.of("ai:decision:query", "prd:work:query"));
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            assertThrows(
                    ServiceException.class,
                    () -> TenantHelper.dynamic("TENANT-A", () -> query.view(99L, actor)));
        }
        verifyNoInteractions(runs, nodes);
        verify(store, never()).listActions(any());
        verify(store, never()).listEvidence(any());
    }

    private AiDecisionRun viewRun() {
        var run = new AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setSceneCode("DELIVERY_RISK");
        return run;
    }

    private AiDecisionAction purchaseAction(String payload) {
        var action = new AiDecisionAction();
        action.setId(21L); action.setRunId(99L); action.setTenantId("TENANT-A");
        action.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
        action.setExecutorCode("purchaseRequisitionDraft"); action.setPayloadJson(payload);
        return action;
    }

    private Map<String, Object> viewFor(AiDecisionRun run, List<AiDecisionAction> actions) {
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        var vo = new AiDecisionRunVo();
        vo.setId(99L); vo.setTenantId("TENANT-A"); vo.setSceneCode("DELIVERY_RISK");
        when(runs.selectVoById(99L)).thenReturn(vo);
        when(store.listActions(99L)).thenReturn(actions);
        var evidences = List.<AiDecisionEvidence>of();
        when(store.listEvidence(99L)).thenReturn(evidences);
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var result = TenantHelper.dynamic("TENANT-A", () -> query.view(99L, "TENANT-A"));
            assertSame(vo, result.get("run"));
            assertSame(actions, result.get("actions"));
            assertSame(evidences, result.get("evidence"));
            return result;
        }
    }
    @Test void recalculateKeepsPersistedHorizonAndScenarioScope() {
        var run = new AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setBizType("WORK_ORDER"); run.setBizId(11L);
        run.setScoreConfigJson("{\"horizonDays\":14,\"scenarioCodes\":[\"BASELINE_CURRENT\",\"APS_CR\"]}");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.requireHorizonDays(run)).thenReturn(14);
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var form = TenantHelper.dynamic("TENANT-A", () -> query.recalculate(99L, "TENANT-A"));
            assertEquals(14, form.getHorizonDays());
            assertEquals(List.of("BASELINE_CURRENT", "APS_CR"), form.getScenarioCodes());
            assertTrue(form.getForceRestart());
        }
    }

    @Test void timelineBeforeFlowIdsAreAttachedRemainsReadable() {
        var run = new AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setStatus("CREATED");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.listActions(99L)).thenReturn(List.of());
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var events = TenantHelper.dynamic("TENANT-A", () -> query.timeline(99L, "TENANT-A"));
            assertTrue(events.isEmpty());
            verifyNoInteractions(nodes);
        }
    }

    @Test void emptyFilterValuesAreNotMistakenForUnknownRiskOrStatus() {
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            doAnswer(call -> new Page<AiDecisionRunVo>(1, 20))
                    .when(runs)
                    .selectSummaryPage(any(), any());
            TenantHelper.dynamic(
                    "TENANT-A",
                    () -> {
                        assertDoesNotThrow(
                                () -> query.list("TENANT-A", "", "", new PageQuery(20, 1)));
                    });
        }
    }

    @Test void markdownExportRedactsSecretsWithoutTruncationException() {
        var row = new AiDecisionRun();
        row.setId(99L); row.setTenantId("TENANT-A");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(row);
        var vo = new AiDecisionRunVo();
        vo.setTenantId("TENANT-A"); vo.setSceneCode("DELIVERY_RISK");
        vo.setBizType("WORK_ORDER"); vo.setBizId(11L);
        when(runs.selectVoById(99L)).thenReturn(vo);
        var evidence = new AiDecisionEvidence();
        evidence.setSourceDomain("ERP"); evidence.setSourceType("STOCK");
        evidence.setMetricCode("stock_qty"); evidence.setMetricValue("Bearer secretLongText1234567890");
        when(store.listEvidence(99L)).thenReturn(List.of(evidence));
        when(store.listActions(99L)).thenReturn(List.of());
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            String markdown =
                    TenantHelper.dynamic("TENANT-A", () -> query.exportMarkdown(99L, "TENANT-A"));
            assertFalse(markdown.contains("secretLongText1234567890"));
            assertTrue(markdown.contains("[REDACTED]"));
        }
    }

    @Test void markdownExportWarnsOnlyWhenRawTotalShortageGapIsInEvidence() {
        String markdown = exportMarkdownWithEvidence("total_shortage_gap", "38.2762");
        assertTrue(markdown.contains("|ERP/SHORTAGE|total_shortage_gap|38.2762|v1|"));
        assertTrue(markdown.contains("未验证单位一致性"));
        assertTrue(markdown.contains("不同单位累加"));
        assertTrue(markdown.contains("不得直接作为同单位采购量、收益或方案排名证据"));
        assertTrue(markdown.contains("原始 metric/value"));
        assertTrue(markdown.contains("同值场景归属须在受控详情页核对"));
        assertTrue(markdown.indexOf("未验证单位一致性") < markdown.indexOf("|来源|指标|数值|版本|"));
    }

    @Test void markdownExportWithoutTotalShortageGapKeepsEvidenceTableUnchanged() {
        String markdown = exportMarkdownWithEvidence("stock_qty", "5.0000");
        assertTrue(markdown.contains("## 证据清单\n\n|来源|指标|数值|版本|\n|---|---|---|---|\n"));
        assertTrue(markdown.contains("|ERP/SHORTAGE|stock_qty|5.0000|v1|"));
        assertFalse(markdown.contains("未验证单位一致性"));
        assertFalse(markdown.contains("total_shortage_gap"));
    }

    @Test void markdownExportKeepsFourNullUnitGapRowsAndWarnsOnceWithoutInferringUnits() {
        var evidences = new ArrayList<AiDecisionEvidence>();
        for (String scenario :
                List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE")) {
            var evidence = new AiDecisionEvidence();
            evidence.setEvidenceKey(scenario);
            evidence.setSourceDomain("ERP"); evidence.setSourceType("SHORTAGE");
            evidence.setMetricCode("total_shortage_gap"); evidence.setMetricValue("38.2762");
            evidence.setUnit(null); evidence.setDataVersion("v1");
            evidences.add(evidence);
        }
        String markdown = exportMarkdownWithEvidence(evidences);
        String row = "|ERP/SHORTAGE|total_shortage_gap|38.2762|v1|";
        assertEquals(4, markdown.split(Pattern.quote(row), -1).length - 1);
        assertEquals(1, markdown.split("未验证单位一致性", -1).length - 1);
        assertFalse(markdown.contains("kg、个、升"), "NULL unit must not be treated as a confirmed unit");
    }

    private String exportMarkdownWithEvidence(String metricCode, String metricValue) {
        var evidence = new AiDecisionEvidence();
        evidence.setSourceDomain("ERP"); evidence.setSourceType("SHORTAGE");
        evidence.setMetricCode(metricCode); evidence.setMetricValue(metricValue);
        evidence.setDataVersion("v1");
        return exportMarkdownWithEvidence(List.of(evidence));
    }

    private String exportMarkdownWithEvidence(List<AiDecisionEvidence> evidences) {
        var row = new AiDecisionRun();
        row.setId(99L); row.setTenantId("TENANT-A");
        when(store.requireRun(99L, "TENANT-A")).thenReturn(row);
        var vo = new AiDecisionRunVo();
        vo.setTenantId("TENANT-A"); vo.setSceneCode("DELIVERY_RISK");
        vo.setBizType("WORK_ORDER"); vo.setBizId(11L);
        when(runs.selectVoById(99L)).thenReturn(vo);
        when(store.listEvidence(99L)).thenReturn(evidences);
        when(store.listActions(99L)).thenReturn(List.of());
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            return TenantHelper.dynamic("TENANT-A", () -> query.exportMarkdown(99L, "TENANT-A"));
        }
    }

    @Test void timelineProjectsOnlyNodeStatusInsteadOfLoadingMegabyteFlowPayloads() {
        var run = new AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setAnalysisFlowInstanceId(101L);
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(store.listActions(99L)).thenReturn(List.of());
        var node = new FlowNodeRun();
        node.setNodeId("llm"); node.setStatus("RUNNING");
        when(nodes.selectList(any())).thenReturn(List.of(node));
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var result = TenantHelper.dynamic("TENANT-A", () -> query.timeline(99L, "TENANT-A"));
            assertEquals("llm", result.get(0).get("code"));
        }
        var captured = ArgumentCaptor.forClass(Wrapper.class);
        verify(nodes).selectList(captured.capture());
        String columns = ((LambdaQueryWrapper<?>) captured.getValue()).getSqlSelect();
        assertNotNull(columns);
        assertTrue(columns.contains("node_id"));
        assertTrue(columns.contains("status"));
        assertFalse(columns.contains("input_json"));
        assertFalse(columns.contains("output_json"));
    }

    @Test void sameTenantWithoutObjectReadPermissionCannotSeeDecisionDetail() {
        var port = mock(IndustrialDecisionDataPort.class);
        var rights = mock(PermissionService.class);
        var guarded =
                new DecisionQueryService(store, runs, nodes, new ObjectMapper(), port, rights);
        var actor =
                new DecisionActor(
                        "TENANT-A", 7L, "viewer", Set.of("ai:decision:query", "prd:work:query"));
        var run = new AiDecisionRun();
        run.setId(99L); run.setTenantId("TENANT-A"); run.setBizType("WORK_ORDER"); run.setBizId(11L);
        when(store.requireRun(99L, "TENANT-A")).thenReturn(run);
        when(rights.getMenuPermission(7L)).thenReturn(actor.permissions());
        when(port.canReadDecision(any(), any())).thenReturn(false);
        try (var spring = mockStatic(SpringUtil.class);
                var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            assertThrows(
                    ServiceException.class,
                    () -> TenantHelper.dynamic("TENANT-A", () -> guarded.detail(99L, actor)));
        }
        verify(runs, never()).selectVoById(any());
    }

    @Test void wrongTenantIsRejectedBeforeReadingAnyReportOrFlowOutput() {
        when(store.requireRun(99L, "TENANT-A")).thenThrow(new ServiceException("not found"));
        assertThrows(ServiceException.class, () -> query.detail(99L, "TENANT-A"));
        verifyNoInteractions(runs, nodes);
    }
}
