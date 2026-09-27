package com.nexus.ai.service.decision;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexus.common.ai.decision.dto.DeliveryRiskRunRequest;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户内有界只读模型，列表投影不读取原始快照。
 */
@Service
public class DecisionQueryService {
    private final DecisionPersistenceService persistence;
    private final AiDecisionRunMapper runs;
    private final FlowNodeRunMapper nodes;
    private final ObjectMapper mapper;
    private final IndustrialDecisionDataPort dataPort;
    private final PermissionService permissionService;

    @Autowired
    public DecisionQueryService(DecisionPersistenceService persistence, AiDecisionRunMapper runs,
                                FlowNodeRunMapper nodes, ObjectMapper mapper,
                                IndustrialDecisionDataPort dataPort, PermissionService permissionService) {
        this.persistence = persistence; this.runs = runs; this.nodes = nodes;
        this.mapper = mapper; this.dataPort = dataPort; this.permissionService = permissionService;
    }
    DecisionQueryService(DecisionPersistenceService persistence, AiDecisionRunMapper runs,
                         FlowNodeRunMapper nodes, ObjectMapper mapper) {
        this(persistence, runs, nodes, mapper, null, null);
    }

    public TableDataInfo<AiDecisionRunVo> list(DecisionActor actor, String status, String riskLevel, PageQuery page) {
        DecisionActor live = liveActor(actor, "ai:decision:query");
        List<String> visibleTypes = new ArrayList<>();
        for (DecisionBusinessType type : DecisionBusinessType.values()) {
            String permission = permissionFor(type);
            if (live.permissions().contains(permission) || live.permissions().contains("*:*:*"))
                visibleTypes.add(type.name());
        }
        if (visibleTypes.isEmpty()) return TableDataInfo.build(List.of());
        return listInternal(actor.tenantId(), status, riskLevel, page, visibleTypes);
    }
    public AiDecisionRunVo detail(Long runId, DecisionActor actor) {
        checkRead(runId, actor);
        return detail(runId, actor.tenantId());
    }
    public Map<String, Object> view(Long runId, DecisionActor actor) {
        checkRead(runId, actor);
        return view(runId, actor.tenantId());
    }
    public List<com.nexus.common.ai.domain.entity.AiDecisionEvidence> evidence(Long runId, DecisionActor actor) {
        checkRead(runId, actor);
        return evidence(runId, actor.tenantId());
    }
    public List<Map<String, Object>> timeline(Long runId, DecisionActor actor) {
        checkRead(runId, actor);
        return timeline(runId, actor.tenantId());
    }
    public String exportMarkdown(Long runId, DecisionActor actor) {
        checkRead(runId, actor);
        return exportMarkdown(runId, actor.tenantId());
    }
    public DeliveryRiskRunRequest recalculate(Long runId, DecisionActor actor) {
        DecisionActor live = liveActor(actor, "ai:decision:run");
        AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
        if (dataPort == null || !dataPort.canAccess(new DecisionBusinessRef(
            DecisionBusinessType.valueOf(run.getBizType()), run.getBizId(), run.getBizCode()), live))
            throw new ServiceException("无权重新分析该业务对象");
        return recalculate(runId, actor.tenantId());
    }

    private void checkRead(Long runId, DecisionActor actor) {
        DecisionActor live = liveActor(actor, "ai:decision:query");
        AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
        if (dataPort == null || !dataPort.canReadDecision(new DecisionBusinessRef(
            DecisionBusinessType.valueOf(run.getBizType()), run.getBizId(), run.getBizCode()), live))
            throw new ServiceException("决策业务对象不存在或当前用户无权查看");
    }
    private DecisionActor liveActor(DecisionActor actor, String permission) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0
            || actor.permissions() == null) throw new ServiceException("决策查询身份无效");
        checkedTenant(actor.tenantId());
        Set<String> current;
        try { current = permissionService == null ? actor.permissions()
            : permissionService.getMenuPermission(actor.userId()); }
        catch (RuntimeException invalid) { throw new ServiceException("无法验证当前决策读取权限"); }
        if (current == null) throw new ServiceException("决策读取权限已失效");
        Set<String> effective = new HashSet<>();
        if (actor.permissions().contains("*:*:*")) effective.addAll(current);
        else if (current.contains("*:*:*")) effective.addAll(actor.permissions());
        else { effective.addAll(actor.permissions()); effective.retainAll(current); }
        if (!effective.contains(permission) && !effective.contains("*:*:*"))
            throw new ServiceException("当前用户没有决策读取权限");
        return new DecisionActor(actor.tenantId(), actor.userId(), actor.userName(), Set.copyOf(effective));
    }
    private String permissionFor(DecisionBusinessType type) {
        return switch (type) {
            case WORK_ORDER -> "prd:work:query";
            case SALES_ORDER -> "so:orderheader:query";
            case SCHEDULE_PLAN -> "prd:schedule:query";
            case SHORTAGE_HORIZON -> "prd:shortage:query";
        };
    }

    TableDataInfo<AiDecisionRunVo> list(String tenant, String status, String riskLevel, PageQuery query) {
        return listInternal(tenant, status, riskLevel, query,
            java.util.Arrays.stream(DecisionBusinessType.values()).map(Enum::name).toList());
    }
    private TableDataInfo<AiDecisionRunVo> listInternal(String tenant, String status,
                                                        String riskLevel, PageQuery query,
                                                        List<String> visibleTypes) {
        checkedTenant(tenant);
        String statusFilter = status == null || status.isBlank() ? null : status;
        String riskFilter = riskLevel == null || riskLevel.isBlank() ? null : riskLevel;
        if (statusFilter != null && !statusFilter.matches("[A-Z_]{2,32}")
            || riskFilter != null && !riskFilter.matches("[A-Z_]{2,16}"))
            throw new ServiceException("决策查询筛选条件无效");
        PageQuery bounded = query == null ? new PageQuery(20, 1) : query;
        if (bounded.getPageSize() == null || bounded.getPageSize() > 100) bounded.setPageSize(100);
        if (bounded.getPageNum() == null || bounded.getPageNum() < 1) bounded.setPageNum(1);
        // 映射器投影刻意排除 input_snapshot_json 与 result_json。
        QueryWrapper<AiDecisionRun> conditions = new QueryWrapper<AiDecisionRun>()
            .eq("tenant_id", tenant).eq("scene_code", DecisionPersistenceService.SCENE_CODE)
            .in("biz_type", visibleTypes).eq(statusFilter != null, "status", statusFilter).eq(riskFilter != null, "risk_level", riskFilter)
            .orderByDesc("create_time").orderByDesc("id");
        com.baomidou.mybatisplus.core.metadata.IPage<AiDecisionRunVo> selected =
            runs.selectSummaryPage(bounded.<AiDecisionRun>build(), conditions);
        return TableDataInfo.build(selected);
    }

    public AiDecisionRunVo detail(Long runId, String tenant) {
        AiDecisionRun run = persistence.requireRun(runId, tenant);
        checkedTenant(tenant);
        AiDecisionRunVo vo = runs.selectVoById(runId);
        if (vo == null || !tenant.equals(vo.getTenantId()) || !DecisionPersistenceService.SCENE_CODE.equals(vo.getSceneCode()))
            throw new ServiceException("决策运行不存在或无权限访问");
        return vo;
    }

    public Map<String, Object> view(Long runId, String tenant) {
        AiDecisionRunVo run = detail(runId, tenant);
        return Map.of("run", run, "actions", persistence.listActions(runId),
            "evidence", persistence.listEvidence(runId));
    }

    public List<com.nexus.common.ai.domain.entity.AiDecisionEvidence> evidence(Long runId, String tenant) {
        persistence.requireRun(runId, tenant);
        checkedTenant(tenant);
        return persistence.listEvidence(runId);
    }

    public List<Map<String, Object>> timeline(Long runId, String tenant) {
        AiDecisionRun run = persistence.requireRun(runId, tenant);
        checkedTenant(tenant);
        List<Map<String, Object>> timeline = new ArrayList<>();
        if (run.getStartedAt() != null) timeline.add(event("RUN", "RUNNING", null, run.getStartedAt()));
        if (run.getAnalysisFinishedAt() != null)
            timeline.add(event("ANALYSIS", "READY", null, run.getAnalysisFinishedAt()));
        for (Long flowId : java.util.Arrays.asList(run.getAnalysisFlowInstanceId(), run.getExecutionFlowInstanceId())) {
            if (flowId == null) continue;
            List<FlowNodeRun> trace = nodes.selectList(Wrappers.<FlowNodeRun>lambdaQuery()
                .select(FlowNodeRun::getNodeId, FlowNodeRun::getStatus, FlowNodeRun::getStartTime)
                .eq(FlowNodeRun::getTenantId, tenant).eq(FlowNodeRun::getInstanceId, flowId)
                .orderByAsc(FlowNodeRun::getStartTime));
            for (FlowNodeRun node : trace) timeline.add(event("NODE", node.getStatus(), node.getNodeId(), node.getStartTime()));
        }
        persistence.listActions(runId).forEach(action -> timeline.add(event("ACTION",
            action.getExecutionStatus(), action.getActionType(), action.getCreateTime())));
        if (run.getFinishedAt() != null) timeline.add(event("RUN", run.getStatus(), null, run.getFinishedAt()));
        timeline.sort(Comparator.comparing(item -> (Long) item.get("time"), Comparator.nullsLast(Long::compareTo)));
        return List.copyOf(timeline);
    }

    private Map<String, Object> event(String type, String status, String code, java.time.LocalDateTime at) {
        return event(type, status, code, at == null ? null : Date.from(at.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }
    private Map<String, Object> event(String type, String status, String code, Date at) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type); body.put("status", status); body.put("code", code);
        body.put("time", at == null ? null : at.getTime());
        return java.util.Collections.unmodifiableMap(body);
    }

    public String exportMarkdown(Long runId, String tenant) {
        var vo = detail(runId, tenant);
        var evidences = persistence.listEvidence(runId);
        var actions = persistence.listActions(runId);
        StringBuilder output = new StringBuilder("# 工业交付风险决策报告\n\n");
        output.append("## 基本信息\n\n- 运行ID：").append(runId)
            .append("\n- 业务：").append(escape(vo.getBizType()))
            .append(" / ").append(vo.getBizId() == null ? "全局时段" : vo.getBizId())
            .append("\n- 状态：").append(escape(vo.getStatus()))
            .append("\n- 风险：").append(escape(vo.getRiskLevel()))
            .append(" / ").append(vo.getRiskScore() == null ? "待计算" : vo.getRiskScore())
            .append("\n\n## 风险摘要与根因\n\n");
        if (vo.getResultJson() != null) {
            try {
                DecisionResult result = mapper.readValue(vo.getResultJson(), DecisionResult.class);
                output.append(escape(String.valueOf(result.narrative().get("executiveSummary")))).append("\n\n");
                output.append("根因：").append(escape(String.valueOf(result.rootCauseCodes()))).append("\n\n");
                output.append("## 方案对比表\n\n|方案|可行|延期工单|拖期天数|\n|---|---|---:|---:|\n");
                if (result.scenarios() != null) result.scenarios().forEach(s -> output.append('|')
                    .append(escape(s.scenarioCode())).append('|').append(s.feasible() ? "是" : "否").append('|')
                    .append(s.kpis() == null ? "-" : s.kpis().delayedWorkOrderCount()).append('|')
                    .append(s.kpis() == null ? "-" : s.kpis().totalTardinessDays()).append("|\n"));
            } catch (Exception invalid) { throw new ServiceException("报告结果无法解码"); }
        }
        output.append("\n## 推荐方案\n\n").append(escape(vo.getRecommendedScenarioCode()))
            .append("\n\n## 证据清单\n\n|来源|指标|数值|版本|\n|---|---|---|---|\n");
        evidences.forEach(e -> output.append('|').append(escape(e.getSourceDomain() + "/" + e.getSourceType()))
            .append('|').append(escape(e.getMetricCode())).append('|').append(escape(e.getMetricValue()))
            .append('|').append(escape(e.getDataVersion())).append("|\n"));
        output.append("\n## 动作与审批\n\n|动作|审批|执行|\n|---|---|---|\n");
        actions.forEach(a -> output.append('|').append(escape(a.getTitle()))
            .append('|').append(escape(a.getApprovalStatus())).append('|')
            .append(escape(a.getExecutionStatus())).append("|\n"));
        output.append("\n## 执行结果\n\n").append(vo.getOutcomeJson() == null ? "尚无实际收益。" : "已生成投影/实际指标，请在受控详情页查看。")
            .append("\n\n## 模型/Prompt/耗时/Token\n\n")
            .append(escape(vo.getModelName())).append(" / ").append(escape(vo.getPromptVersion()))
            .append(" / ").append(vo.getInputTokens() == null ? "-" : vo.getInputTokens())
            .append(" / ").append(vo.getOutputTokens() == null ? "-" : vo.getOutputTokens()).append('\n');
        if (output.length() > 500_000) throw new ServiceException("报告体积超出导出限制");
        return output.toString();
    }

    public DeliveryRiskRunRequest recalculate(Long runId, String tenant) {
        var run = persistence.requireRun(runId, tenant);
        checkedTenant(tenant);
        var request = new DeliveryRiskRunRequest();
        request.setBizType(DecisionBusinessType.valueOf(run.getBizType()));
        request.setBizId(run.getBizId());
        request.setHorizonDays(persistence.requireHorizonDays(run));
        try {
            var codes = mapper.readTree(run.getScoreConfigJson()).path("scenarioCodes");
            if (!codes.isArray() || codes.isEmpty() || codes.size() > 4)
                throw new ServiceException("旧决策运行的场景范围无效");
            List<String> selected = new ArrayList<>();
            codes.forEach(code -> {
                if (!code.isTextual() || code.asText().isBlank())
                    throw new ServiceException("旧决策运行的方案编码无效");
                selected.add(code.asText());
            });
            request.setScenarioCodes(List.copyOf(selected));
        } catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
            throw new ServiceException("旧决策运行的分析范围无法解码");
        }
        request.setForceRestart(true);
        return request;
    }

    private void checkedTenant(String tenant) {
        if (tenant == null || tenant.isBlank() || !tenant.equals(TenantHelper.getDynamic()))
            throw new ServiceException("决策查询租户上下文无效");
    }

    private String escape(String input) {
        if (input == null) return "-";
        String safe = input.replaceAll("(?i)(?:bearer\\s+\\S+|api[_-]?key\\s*[:=]\\s*\\S+|jdbc:(?:mysql|postgresql):\\S+)", "[REDACTED]")
            .replace("|", "\\|").replace("\r", " ").replace("\n", " ").replace("<", "&lt;");
        return safe.substring(0, Math.min(250, safe.length()));
    }
}
