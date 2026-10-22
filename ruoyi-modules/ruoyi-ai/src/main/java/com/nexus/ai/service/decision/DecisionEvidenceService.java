package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.ai.decision.dto.RetrievalHit;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 稳定且可审计的业务及规范证据，数值陈述不得悄然丢失来源。
 */
@Service
public class DecisionEvidenceService {
    private static final Comparator<EvidenceItem> EVIDENCE_ORDER = Comparator
        .comparing(EvidenceItem::sourceDomain)
        .thenComparing(EvidenceItem::sourceType)
        .thenComparing(EvidenceItem::sourceId, Comparator.nullsLast(Long::compareTo))
        .thenComparing(EvidenceItem::sourceCode, Comparator.nullsFirst(String::compareTo))
        .thenComparing(EvidenceItem::metricCode);

    public List<EvidenceItem> normalize(Collection<EvidenceItem> items) {
        if (items == null) throw new IllegalArgumentException("证据不能为空");
        Map<String, EvidenceItem> byKey = new LinkedHashMap<>();
        for (EvidenceItem item : items) {
            EvidenceItem normalized = withStableKey(item);
            EvidenceItem existing = byKey.putIfAbsent(normalized.evidenceKey(), normalized);
            if (existing != null && !Objects.equals(existing, normalized)) {
                throw new IllegalArgumentException("同一证据键存在互相矛盾的事实: " + normalized.evidenceKey());
            }
        }
        return byKey.values().stream().sorted(EVIDENCE_ORDER).toList();
    }

    public Map<String, EvidenceItem> indexByKey(List<EvidenceItem> evidence) {
        Map<String, EvidenceItem> index = new LinkedHashMap<>();
        normalize(evidence).forEach(item -> index.put(item.evidenceKey(), item));
        return Collections.unmodifiableMap(index);
    }

    public EvidenceItem derivedNumber(String metricCode, String metricName, String value,
                                      String unit, List<String> parentKeys) {
        if (!hasText(metricCode) || !hasText(metricName) || parentKeys == null || parentKeys.isEmpty()
            || parentKeys.stream().anyMatch(key -> !hasText(key))) {
            throw new IllegalArgumentException("派生数值必须具备指标和来源证据键");
        }
        BigDecimal numeric;
        try { numeric = new BigDecimal(value); }
        catch (RuntimeException e) { throw new IllegalArgumentException("派生数值必须是有限十进制数", e); }
        return withStableKey(new EvidenceItem(null, null, "DERIVED", "CALC", null,
            metricCode, metricName, metricCode, metricName, "NUMBER", numeric.toPlainString(), unit,
            null, null, BigDecimal.ONE, true, false,
            List.copyOf(new LinkedHashSet<>(parentKeys)), null));
    }

    public List<EvidenceItem> fromRetrievalHits(List<RetrievalHit> hits) {
        if (hits == null) return List.of();
        List<EvidenceItem> evidence = new ArrayList<>(hits.size());
        for (RetrievalHit hit : hits) {
            if (hit == null || hit.knowledgeId() == null || hit.documentId() == null
                || hit.chunkId() == null || !hasText(hit.documentName()) || !hasText(hit.content())
                || !hasText(hit.version()) || "UNVERIFIED".equals(hit.sourceType())) {
                throw new IllegalArgumentException("RAG 命中缺少可核验的文档/片段来源");
            }
            Double rawScore = hit.rerankScore() != null ? hit.rerankScore() : hit.vectorScore();
            BigDecimal confidence = rawScore != null && Double.isFinite(rawScore) && rawScore >= 0 && rawScore <= 1
                ? BigDecimal.valueOf(rawScore) : null;
            evidence.add(withStableKey(new EvidenceItem(null, null, "RAG",
                hasText(hit.sourceType()) ? hit.sourceType() : "DOC", hit.chunkId(),
                hit.chunkId().toString(), hit.documentName(), "content", "文档摘录", "TEXT",
                hit.content(), null, hit.effectiveDate() == null ? null : hit.effectiveDate().atStartOfDay(),
                hit.version(), confidence, true, true, List.of(),
                "knowledge:" + hit.knowledgeId() + "/document:" + hit.documentId() + "/chunk:" + hit.chunkId())));
        }
        return normalize(evidence);
    }

    public void assertNumericEvidenceCoverage(DecisionResult result) {
        if (result == null) throw new IllegalArgumentException("决策结果不能为空");
        Map<String, EvidenceItem> evidence = indexByKey(result.evidence());
        for (EvidenceItem item : evidence.values()) {
            if (!"NUMBER".equals(item.valueType())) continue;
            if (!item.verifiable() || !hasText(item.value())) {
                throw new IllegalArgumentException("数值证据必须有可复核的事实来源");
            }
            try { new BigDecimal(item.value()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("数值证据不是有效十进制数", e); }
            if ("DERIVED".equals(item.sourceDomain())) {
                if (item.derivedFromEvidenceKeys() == null || item.derivedFromEvidenceKeys().isEmpty()
                    || !evidence.keySet().containsAll(item.derivedFromEvidenceKeys())) {
                    throw new IllegalArgumentException("派生数值缺少有效的上游证据");
                }
            }
        }
        if (!Double.isFinite(result.riskScore())) {
            throw new IllegalArgumentException("风险分数不是有限数值");
        }
        requireMetric(evidence, "RISK", "risk_score", BigDecimal.valueOf(result.riskScore()), null);
        requireKpiEvidence(result.baselineKpis(), "BASELINE_CURRENT", evidence, null);
        if (result.scenarios() != null) {
            for (DecisionScenario scenario : result.scenarios()) {
                if (scenario != null && scenario.kpis() != null) {
                    if (!hasText(scenario.scenarioCode())) {
                        throw new IllegalArgumentException("方案缺少可绑定证据的编号");
                    }
                    requireKpiEvidence(scenario.kpis(), scenario.scenarioCode(), evidence,
                        scenario.evidenceKeys() == null ? Set.of() : Set.copyOf(scenario.evidenceKeys()));
                }
            }
        }
    }

    private static void requireKpiEvidence(DecisionKpiSet kpi, String scope,
                                           Map<String, EvidenceItem> evidence, Set<String> linkedKeys) {
        if (kpi == null) return;
        requireMetric(evidence, scope, "impacted_order_count", BigDecimal.valueOf(kpi.impactedOrderCount()), linkedKeys);
        requireMetric(evidence, scope, "delayed_work_order_count", BigDecimal.valueOf(kpi.delayedWorkOrderCount()), linkedKeys);
        requireMetric(evidence, scope, "total_tardiness_days", BigDecimal.valueOf(kpi.totalTardinessDays()), linkedKeys);
        requireMetric(evidence, scope, "max_tardiness_days", BigDecimal.valueOf(kpi.maxTardinessDays()), linkedKeys);
        requireMetric(evidence, scope, "shortage_material_count", BigDecimal.valueOf(kpi.shortageMaterialCount()), linkedKeys);
        requireMetric(evidence, scope, "overloaded_resource_count", BigDecimal.valueOf(kpi.overloadedResourceCount()), linkedKeys);
        requireMetric(evidence, scope, "changed_work_order_count", BigDecimal.valueOf(kpi.changedWorkOrderCount()), linkedKeys);
        if (kpi.totalShortageGap() != null) {
            requireMetric(evidence, scope, "total_shortage_gap", kpi.totalShortageGap(), linkedKeys);
        }
        if (kpi.shortageGapRatio() != null) {
            if (kpi.shortageBasis() == null || kpi.shortageGapRatio().signum() < 0
                || kpi.shortageGapRatio().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("缺口比例缺少逐物料来源依据");
            requireMetric(evidence, scope, "shortage_gap_ratio", kpi.shortageGapRatio(), linkedKeys);
            String ratioKey = "DERIVED:CALC:" + scope + ":shortage_gap_ratio";
            var ratio = evidence.get(ratioKey);
            if (ratio == null || !"ratio".equals(ratio.unit()))
                throw new IllegalArgumentException("缺口比例必须具有无量纲证据");
            for (var row : kpi.shortageBasis().requirements()) {
                for (String metric : List.of("required_qty", "base_uom_id")) {
                    String key = "ERP:REQUIREMENT:" + row.detailId() + ":" + metric;
                    if (!evidence.containsKey(key) || !ratio.derivedFromEvidenceKeys().contains(key))
                        throw new IllegalArgumentException("缺口比例未引用需求分母和基础单位来源");
                }
            }
            for (var gap : kpi.shortageBasis().gaps()) {
                var source = evidence.get(gap.sourceEvidenceKey());
                if (source == null || !"uom:".concat(String.valueOf(gap.baseUomId())).equals(source.unit())
                    || new BigDecimal(source.value()).compareTo(gap.maxGap()) != 0
                    || !ratio.derivedFromEvidenceKeys().contains(source.evidenceKey()))
                    throw new IllegalArgumentException("缺口比例未引用对应物料、基础单位的缺口证据");
            }
        }
        if (kpi.maxResourceUtilization() != null) {
            requireMetric(evidence, scope, "max_resource_utilization", kpi.maxResourceUtilization(), linkedKeys);
        }
        if (kpi.actionCost() != null) requireMetric(evidence, scope, "action_cost", kpi.actionCost(), linkedKeys);
    }

    private static void requireMetric(Map<String, EvidenceItem> evidence, String scope,
                                      String metric, BigDecimal expectedValue, Set<String> linkedKeys) {
        if (evidence.values().stream().noneMatch(item -> metric.equals(item.metricCode())
            && scope.equals(item.sourceCode()) && "NUMBER".equals(item.valueType())
            && item.verifiable() && !item.normative()
            && (linkedKeys == null || linkedKeys.contains(item.evidenceKey()))
            && new BigDecimal(item.value()).compareTo(expectedValue) == 0)) {
            throw new IllegalArgumentException("数值结果缺少同范围、同数值的业务证据: " + scope + "/" + metric);
        }
    }

    private static EvidenceItem withStableKey(EvidenceItem item) {
        if (item == null || !hasText(item.sourceDomain()) || !hasText(item.sourceType())
            || !hasText(item.metricCode()) || item.sourceId() == null && !hasText(item.sourceCode())) {
            throw new IllegalArgumentException("证据缺少稳定来源或指标");
        }
        String key = item.sourceDomain() + ":" + item.sourceType() + ":"
            + (item.sourceId() != null ? item.sourceId() : item.sourceCode()) + ":" + item.metricCode();
        if (item.evidenceKey() != null && !item.evidenceKey().equals(key)) {
            throw new IllegalArgumentException("证据键与来源事实不一致: " + item.evidenceKey());
        }
        String value = item.value();
        if ("NUMBER".equals(item.valueType())) {
            if (!hasText(value)) throw new IllegalArgumentException("数值证据不能为空");
            try { value = new BigDecimal(value).stripTrailingZeros().toPlainString(); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("数值证据格式无效", e); }
        }
        return new EvidenceItem(item.runId(), key, item.sourceDomain(), item.sourceType(), item.sourceId(),
            item.sourceCode(), item.sourceName(), item.metricCode(),
            hasText(item.metricName()) ? item.metricName() : item.metricCode(), item.valueType(),
            value, canonicalUnit(item.unit()), item.observedAt(), item.dataVersion(), item.confidence(),
            item.verifiable(), item.normative(), item.derivedFromEvidenceKeys() == null ? List.of()
            : List.copyOf(item.derivedFromEvidenceKeys()), item.displayPath());
    }

    private static String canonicalUnit(String unit) {
        if (unit == null) return null;
        return switch (unit.trim()) {
            case "天", "天数", "days", "d", "day" -> "day";
            case "小时", "h", "hours", "hour" -> "hour";
            case "%", "百分比", "percent" -> "percent";
            case "件", "pcs", "piece" -> "piece";
            case "元", "人民币", "¥", "RMB", "CNY" -> "CNY";
            default -> unit.trim();
        };
    }

    private static boolean hasText(String text) { return text != null && !text.isBlank(); }
}
