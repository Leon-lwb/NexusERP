package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.common.ai.decision.util.SavedScheduleCapacity;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务端确定性终局分析；大模型仅提供文字，不参与数值计算。
 */
@Service
@RequiredArgsConstructor
public class DecisionAnalysisAssemblyService {
    private final DeliveryRiskScoringService scoring;
    private final DecisionEvidenceService evidenceService;
    private final DecisionSourceFactVerifier verifier;
    private final DecisionSummaryService summaries;
    private final DecisionPolicyGuardService guard;

    public DecisionResult prepare(AiDecisionRun run, DeliveryContextSnapshot snapshot,
                                  ShortageSimulationResult shortage, List<DecisionScenario> ranked,
                                  List<RetrievalHit> hits, String llmText) {
        if (run == null || run.getId() == null || snapshot == null || snapshot.snapshotAt() == null
            || shortage == null || ranked == null || ranked.isEmpty())
            throw new ServiceException("缺少经过 ERP 推演的基线、缺料或方案事实");
        // 新比例只使用服务端已冻结配置；无比例的旧结论不补写窗口或升级 KPI。
        int horizonDays = ranked.stream().anyMatch(s -> s != null && s.kpis() != null
            && s.kpis().shortageGapRatio() != null) ? DecisionFrozenHorizon.requireHorizonDays(run) : 0;
        DecisionScenario baseline = ranked.stream().filter(s -> s != null && "BASELINE_CURRENT".equals(s.scenarioCode()))
            .findFirst().orElse(null);
        if (baseline == null || snapshot.workOrders() == null
            || snapshot.stocks() == null || snapshot.requirements() == null || snapshot.capacityByDate() == null
            || snapshot.routingByMaterial() == null || snapshot.currentSchedule() == null
            || ranked.stream().anyMatch(s -> s == null || s.inputHash() == null || s.inputHash().isBlank()
                || s.evidenceKeys() == null || !s.evidenceKeys().contains("snapshot:" + snapshot.snapshotHash())))
            throw new ServiceException("基础业务数据或推演方案缺失，不能发布风险结论");
        /**
         * 仅排程方案在终态过滤后没有工单时使用专用错误，不能用历史排程替代当前分析对象。
         * 门禁以快照根类型为事实，不依赖运行行可能缺失的业务类型；其他根保持原有证据门禁。
         */
        if (snapshot.rootBusinessRef() != null
            && snapshot.rootBusinessRef().type() == DecisionBusinessType.SCHEDULE_PLAN
            && snapshot.workOrders().isEmpty())
            throw new DecisionEvidenceException(DecisionEvidenceException.Reason.NO_ANALYZABLE_WORK_ORDER);
        // 仅对明确缺失的来源证据公开领域原因；已存在但无效的值、篡改指标、
        // SQL 或模型失败及未知异常均保持通用拒绝。
        if (snapshot.currentSchedule().isEmpty())
            throw new DecisionEvidenceException(DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING);
        if (snapshot.capacityByDate().isEmpty())
            throw new DecisionEvidenceException(DecisionEvidenceException.Reason.CAPACITY_EVIDENCE_MISSING);
        for (var work : snapshot.workOrders()) {
            if (work == null || work.materialId() == null) throw new ServiceException("工单源事实不完整");
            var steps = snapshot.routingByMaterial().get(work.materialId());
            if (steps == null || steps.isEmpty())
                throw new DecisionEvidenceException(DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING);
        }
        if (baseline.kpis() == null)
            throw new ServiceException("基础业务推演 KPI 缺失，不能发布风险结论");
        // 基线产能为观测到的 ERP 日历事实，不使用模型陈述或 APS 假设方案指标。
        var observedLoad = SavedScheduleCapacity.measure(snapshot.currentSchedule());
        if (observedLoad == null || baseline.kpis().maxResourceUtilization() == null
            || baseline.kpis().maxResourceUtilization().compareTo(observedLoad.maxUtilization()) != 0
            || baseline.kpis().overloadedResourceCount() != observedLoad.overloadedResources())
            throw new ServiceException("已保存排程利用率缺少可信产能日历，或与 ERP 源事实不一致");        Double utilization = baseline.kpis().maxResourceUtilization() == null ? null
            : baseline.kpis().maxResourceUtilization().doubleValue();
        var supplier = snapshot.supplierMetrics() == null ? List.<DeliveryContextSnapshot.SupplierMetricFact>of()
            : snapshot.supplierMetrics().stream().filter(s -> s != null && s.onTimeRate() != null
                && s.observedAt() != null).toList();
        double rate = supplier.stream().map(DeliveryContextSnapshot.SupplierMetricFact::onTimeRate)
            .mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        int sample = supplier.stream().mapToInt(DeliveryContextSnapshot.SupplierMetricFact::sampleCount).sum();
        Integer firstShortageDays =
                shortage.hasRisk() && shortage.earliestShortageDate() != null
                        ? (int)
                                Math.max(
                                        0,
                                        ChronoUnit.DAYS.between(
                                                snapshot.snapshotAt().toLocalDate(),
                                                shortage.earliestShortageDate()))
                        : shortage.hasRisk() ? null : Integer.MAX_VALUE;
        var risk = scoring.score(new DeliveryRiskScoringService.RiskInput(
            baseline.kpis().totalTardinessDays(), firstShortageDays, utilization, rate, sample,
            !snapshot.currentSchedule().isEmpty(), snapshot.workOrders().stream().allMatch(work ->
                snapshot.routingByMaterial().get(work.materialId()) != null
                    && !snapshot.routingByMaterial().get(work.materialId()).isEmpty()),
            !snapshot.stocks().isEmpty(), !snapshot.requirements().isEmpty(),
            snapshot.arrivals() != null, !snapshot.capacityByDate().isEmpty()));
        if (risk.score() == null || risk.level() == null) throw new ServiceException("关键源事实不足，不能发布无风险结论");
        List<EvidenceItem> roots = verifier.numericFacts(run.getId(), snapshot);
        if (roots.isEmpty()) throw new ServiceException("缺少可复核的业务数值事实");
        List<EvidenceItem> evidence = new ArrayList<>(roots);
        List<String> parentKeys = roots.stream().map(EvidenceItem::evidenceKey).toList();
        evidence.add(derived(run, snapshot, "RISK", "risk_score", BigDecimal.valueOf(risk.score()), parentKeys));
        List<DecisionScenario> linked = new ArrayList<>();
        for (DecisionScenario scenario : ranked) {
            List<String> keys = new ArrayList<>();
            if (scenario.kpis() != null) {
                DecisionKpiSet k = scenario.kpis();
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "impacted_order_count", BigDecimal.valueOf(k.impactedOrderCount()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "delayed_work_order_count", BigDecimal.valueOf(k.delayedWorkOrderCount()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "total_tardiness_days", BigDecimal.valueOf(k.totalTardinessDays()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "max_tardiness_days", BigDecimal.valueOf(k.maxTardinessDays()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "shortage_material_count", BigDecimal.valueOf(k.shortageMaterialCount()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "overloaded_resource_count", BigDecimal.valueOf(k.overloadedResourceCount()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "changed_work_order_count", BigDecimal.valueOf(k.changedWorkOrderCount()), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "total_shortage_gap", k.totalShortageGap(), parentKeys);
                shortageRatioEvidence(evidence, keys, run, snapshot, scenario.scenarioCode(), k, parentKeys, horizonDays);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "max_resource_utilization", k.maxResourceUtilization(), parentKeys);
                metric(evidence, keys, run, snapshot, scenario.scenarioCode(), "action_cost", k.actionCost(), parentKeys);
            }
            linked.add(new DecisionScenario(scenario.scenarioCode(), scenario.scenarioName(), scenario.strategy(),
                scenario.feasible(), scenario.infeasibleReasons(), scenario.kpis(), scenario.objectiveScore(),
                scenario.rank(), scenario.overrides(), List.copyOf(keys), scenario.schedulePreview(),
                scenario.inputHash(), scenario.delayedOrders(), scenario.bottleneckResources()));
        }
        var normalized = evidenceService.normalize(evidence);
        String recommended =
                linked.stream()
                        .filter(s -> s.feasible() && s.kpis() != null)
                        .min(Comparator.comparingInt(DecisionScenario::rank))
                        .map(DecisionScenario::scenarioCode)
                        .orElse(null);
        DecisionBusinessRef ref = snapshot.rootBusinessRef();
        var summary = summaries.summarize(new DecisionSummaryService.SummaryInput(run.getId(), ref,
            risk.level(), risk.score(), risk.rootCauseCodes(), List.copyOf(linked), recommended, normalized,
            hits == null ? List.of() : hits, null, llmText));
        var result = new DecisionResult(run.getId(), ref, risk.score(), risk.level(), risk.confidence(),
            risk.rootCauseCodes(), baseline.kpis(), List.copyOf(linked), recommended,
            summaryText(summary), normalized, List.of(),
            snapshot.warnings() == null ? List.of() : snapshot.warnings());
        evidenceService.assertNumericEvidenceCoverage(result);
        var checked = guard.validate(result, narrative(summary), List.of());
        if (!checked.passed() && !"TEMPLATE_FALLBACK".equals(summary.mode())) {
            // 仅替换不可信文字，数值事实、排序及证据完全不变。
            var safe = summaries.summarize(new DecisionSummaryService.SummaryInput(run.getId(), ref,
                risk.level(), risk.score(), risk.rootCauseCodes(), List.copyOf(linked), recommended, normalized,
                List.of(), null, null));
            List<String> warnings = new ArrayList<>(result.warnings());
            warnings.add(
                    "SUMMARY_GUARD_REJECTED:"
                            + checked.violations().stream()
                                    .map(DecisionPolicyGuardService.PolicyViolation::code)
                                    .distinct()
                                    .sorted()
                                    .collect(Collectors.joining(",")));
            result = new DecisionResult(result.runId(), result.businessRef(), result.riskScore(), result.riskLevel(),
                result.confidenceLevel(), result.rootCauseCodes(), result.baselineKpis(), result.scenarios(),
                result.recommendedScenarioCode(), summaryText(safe), result.evidence(), result.actions(), List.copyOf(warnings));
            checked = guard.validate(result, narrative(safe), List.of());
        }
        // 安全叙述不能修复缺失或篡改的业务证据，也不能修复无效推荐。
        if (!checked.passed()) throw new ServiceException("分析摘要未通过业务策略校验");
        return result;
    }

    private static DecisionPolicyGuardService.DecisionNarrative narrative(DecisionSummaryService.DecisionSummary summary) {
        return new DecisionPolicyGuardService.DecisionNarrative(summary.executiveSummary(),
            summary.riskExplanation(), summary.rootCauseExplanations(), summary.scenarioTradeoffs(),
            summary.recommendationRationale(), summary.evidenceCitations(), summary.uncertaintyNotes(),
            summary.operatorChecklist());
    }

    private static Map<String, Object> summaryText(DecisionSummaryService.DecisionSummary summary) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("executiveSummary", summary.executiveSummary());
        text.put("riskExplanation", summary.riskExplanation());
        text.put("rootCauseExplanations", summary.rootCauseExplanations());
        text.put("scenarioTradeoffs", summary.scenarioTradeoffs());
        text.put("recommendationRationale", summary.recommendationRationale());
        text.put("evidenceCitations", summary.evidenceCitations());
        text.put("uncertaintyNotes", summary.uncertaintyNotes());
        text.put("operatorChecklist", summary.operatorChecklist());
        text.put("summaryMode", summary.mode());
        return Collections.unmodifiableMap(text);
    }

    /** 比例只引用逐物料的缺口、需求分母和已核验基础单位，不引用混单位总和。 */
    private void shortageRatioEvidence(List<EvidenceItem> evidence, List<String> keys, AiDecisionRun run,
                                      DeliveryContextSnapshot snapshot, String scope, DecisionKpiSet k,
                                      List<String> rootKeys, int horizonDays) {
        if (k.shortageGapRatio() == null) return; // 历史口径保持原状，不回填新比例。
        verifier.assertShortageBasis(run.getId(), k, snapshot, horizonDays);
        var basis = k.shortageBasis();
        List<String> parents = new ArrayList<>();
        for (var row : basis.requirements()) {
            parents.add("ERP:REQUIREMENT:" + row.detailId() + ":required_qty");
            parents.add("ERP:REQUIREMENT:" + row.detailId() + ":base_uom_id");
        }
        for (var gap : basis.gaps()) {
            String sourceCode = scope + "_" + gap.materialId();
            String key = "DERIVED:SHORTAGE:" + sourceCode + ":max_gap";
            if (!key.equals(gap.sourceEvidenceKey())) throw new ServiceException("物料缺口证据归属与方案不符");
            var item = new EvidenceItem(run.getId(), key, "DERIVED", "SHORTAGE", null, sourceCode,
                null, "max_gap", "物料峰值缺口", "NUMBER", gap.maxGap().toPlainString(),
                "uom:" + gap.baseUomId(), snapshot.snapshotAt(), snapshot.snapshotHash(),
                BigDecimal.ONE, true, false, rootKeys, null);
            evidence.add(item); keys.add(key); parents.add(key);
        }
        // 完整且明确为空的需求集合仍须绑定已核验快照，不能拿缺失清单当零缺口。
        if (parents.isEmpty()) parents.addAll(rootKeys);
        String ratioKey = "DERIVED:CALC:" + scope + ":shortage_gap_ratio";
        evidence.add(
                new EvidenceItem(
                        run.getId(),
                        ratioKey,
                        "DERIVED",
                        "CALC",
                        null,
                        scope,
                        null,
                        "shortage_gap_ratio",
                        "逐物料等权缺口比例",
                        "NUMBER",
                        k.shortageGapRatio().toPlainString(),
                        "ratio",
                        snapshot.snapshotAt(),
                        snapshot.snapshotHash(),
                        BigDecimal.ONE,
                        true,
                        false,
                        List.copyOf(new LinkedHashSet<>(parents)),
                        null));
        keys.add(ratioKey);
    }

    private void metric(List<EvidenceItem> evidence, List<String> keys, AiDecisionRun run,
                        DeliveryContextSnapshot snapshot, String scope, String code,
                        BigDecimal value, List<String> parents) {
        if (value == null) return;
        EvidenceItem item = derived(run, snapshot, scope, code, value, parents);
        evidence.add(item); keys.add(item.evidenceKey());
    }

    private EvidenceItem derived(AiDecisionRun run, DeliveryContextSnapshot snapshot,
                                 String scope, String code, BigDecimal value, List<String> parents) {
        return new EvidenceItem(run.getId(), "DERIVED:CALC:" + scope + ":" + code,
            "DERIVED", "CALC", null, scope, null, code, code, "NUMBER",
            value.stripTrailingZeros().toPlainString(), null, snapshot.snapshotAt(), snapshot.snapshotHash(),
            BigDecimal.ONE, true, false, parents, null);
    }
}
