package com.nexus.ai.service.decision;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nexus.ai.config.AiPromptTemplateInitializer;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.governance.AiCallContext;
import com.nexus.common.ai.governance.AiInvocationScope;
import com.nexus.common.satoken.utils.LoginHelper;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 模型输出仅为叙述，评分、动作选择与最终审批均为确定性逻辑。
 */
@Service
@RequiredArgsConstructor
public class DecisionSummaryService {
    private static final Pattern SENSITIVE =
            Pattern.compile(
                    "(?i)(?:sk-[a-z0-9_-]{5,}|bearer\\s+[a-z0-9._-]{8,}|jdbc:|api[_-]?key\\s*[:=]|\\b(?:drop|truncate|delete|alter)\\s+(?:table|from)\\b)");
    private final ChatModelFactory chatModelFactory;

    public DecisionSummary summarize(SummaryInput input) {
        if (input == null || input.businessRef() == null || input.riskLevel() == null
            || !Double.isFinite(input.riskScore()) || input.riskScore() < 0 || input.riskScore() > 100) {
            throw new IllegalArgumentException("摘要必须从确定性的业务风险结果生成");
        }
        String rejection = "MODEL_UNAVAILABLE";
        if (input.flowRawOutput() != null) {
            Parsed fromFlow = parse(input.flowRawOutput(), input, "FLOW_LLM", null);
            if (fromFlow.summary() != null) return fromFlow.summary();
            rejection = fromFlow.reason();
        }
        if (input.modelId() != null) {
            try (var invocation = decisionScope(input.runId())) {
                ChatResponse response = chatModelFactory.getChatModel(input.modelId()).chat(
                    SystemMessage.from(AiPromptTemplateInitializer.DECISION_SUMMARY_SYSTEM_PROMPT), UserMessage.from(renderPrompt(input)));
                if (response != null && response.aiMessage() != null) {
                    Parsed direct =
                            parse(response.aiMessage().text(), input, "DIRECT_RETRY", response);
                    if (direct.summary() != null) return direct.summary();
                    rejection = direct.reason();
                }
            } catch (RuntimeException ignored) {
                // 模型异常可能包含接口密钥、令牌或上游响应，禁止公开。
            }
        }
        return fallback(input, rejection);
    }

    private AiInvocationScope decisionScope(Long runId) {
        var previous = AiInvocationScope.capture();
        AiCallContext c = previous == null ? null : previous.context();
        if (c == null) {
            String tenant = AiRequestContext.getTenantId();
            Long user = AiRequestContext.getUserId();
            if (tenant == null || user == null)
                try {
                    tenant = LoginHelper.getTenantId();
                    user = LoginHelper.getUserId();
                } catch (RuntimeException unavailable) {
                    return null;
                }
            if (tenant == null || user == null) return null;
            c =
                    new AiCallContext(
                            tenant,
                            user,
                            null,
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            runId,
                            null,
                            "DECISION");
        }
        var next =
                new AiCallContext(
                        c.tenantId(),
                        c.userId(),
                        c.userName(),
                        c.requestId(),
                        c.sessionId(),
                        c.flowInstanceId(),
                        runId,
                        c.abRunId(),
                        "DECISION");
        return AiInvocationScope.open(next, previous == null ? () -> {} : previous.guard());
    }

    private static Parsed parse(String raw, SummaryInput input, String mode, ChatResponse response) {
        if (raw == null || raw.length() > 12_000) return rejected("FORMAT_INVALID");
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return rejected("FORMAT_INVALID");
        try {
            JSONObject object = JSON.parseObject(trimmed);
            Set<String> fields =
                    Set.of(
                            "executiveSummary",
                            "riskExplanation",
                            "rootCauseExplanations",
                            "scenarioTradeoffs",
                            "recommendationRationale",
                            "evidenceCitations",
                            "uncertaintyNotes",
                            "operatorChecklist");
            if (object == null || !object.keySet().equals(fields) || !normalizeSchema(object))
                return rejected("SCHEMA_INVALID");
            SummaryJson parsed = object.toJavaObject(SummaryJson.class);
            if (parsed == null || blank(parsed.executiveSummary()) || blank(parsed.riskExplanation())
                || blank(parsed.recommendationRationale()) || parsed.executiveSummary().length() > 800
                || parsed.rootCauseExplanations() == null || parsed.scenarioTradeoffs() == null
                || parsed.evidenceCitations() == null || parsed.uncertaintyNotes() == null
                || parsed.operatorChecklist() == null || parsed.scenarioTradeoffs().stream()
                .anyMatch(s -> s == null || blank(s.tradeoff()) || s.tradeoff().length() > 200))
                return rejected("SCHEMA_INVALID");
            Set<String> allowed = input.evidence() == null ? Set.of() : input.evidence().stream()
                .filter(Objects::nonNull).map(EvidenceItem::evidenceKey).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!allowed.containsAll(parsed.evidenceCitations()))
                return rejected("UNKNOWN_EVIDENCE_CITATION");
            Integer inputTokens = response == null || response.tokenUsage() == null ? null : response.tokenUsage().inputTokenCount();
            Integer outputTokens = response == null || response.tokenUsage() == null ? null : response.tokenUsage().outputTokenCount();
            return new Parsed(new DecisionSummary(mode, parsed.executiveSummary(), parsed.riskExplanation(),
                List.copyOf(parsed.rootCauseExplanations()), List.copyOf(parsed.scenarioTradeoffs()),
                parsed.recommendationRationale(), List.copyOf(parsed.evidenceCitations()),
                List.copyOf(parsed.uncertaintyNotes()), List.copyOf(parsed.operatorChecklist()),
                input.modelId(), response == null ? null : response.modelName(),
                inputTokens, outputTokens, "delivery_decision_summary_v1"), null);
        } catch (RuntimeException ignored) {
            return rejected("FORMAT_INVALID");
        }
    }


    private record Parsed(DecisionSummary summary, String reason) { }
    private static Parsed rejected(String reason) { return new Parsed(null, reason); }

    /**
     * 仅允许封闭别名，不转换类型或猜测来源；后续仍执行策略护栏。
     */
    private static boolean normalizeSchema(JSONObject object) {
        for (String key : List.of("executiveSummary", "riskExplanation", "recommendationRationale"))
            if (!(object.get(key) instanceof String value) || value.isBlank()) return false;
        for (String key : List.of("evidenceCitations", "uncertaintyNotes", "operatorChecklist")) {
            if (!(object.get(key) instanceof List<?> values)
                || values.stream().anyMatch(v -> !(v instanceof String))) return false;
        }
        if (!(object.get("rootCauseExplanations") instanceof List<?> roots)
            || !(object.get("scenarioTradeoffs") instanceof List<?> scenarios)) return false;
        for (Object value : roots) {
            if (!(value instanceof JSONObject root)
                    || !root.keySet().equals(Set.of("code", "explanation"))
                    || !(root.get("code") instanceof String code)
                    || code.isBlank()
                    || !(root.get("explanation") instanceof String explanation)
                    || explanation.isBlank()) return false;
        }
        for (Object value : scenarios) {
            if (!(value instanceof JSONObject scenario)) return false;
            boolean alias = scenario.keySet().equals(Set.of("scenarioCode", "explanation"));
            if (!alias && !scenario.keySet().equals(Set.of("scenarioCode", "tradeoff")))
                return false;
            if (!(scenario.get("scenarioCode") instanceof String code) || code.isBlank()
                || !(scenario.get(alias ? "explanation" : "tradeoff") instanceof String text)
                || text.isBlank() || text.length() > 200) return false;
            if (alias) scenario.put("tradeoff", scenario.remove("explanation"));
        }
        return true;
    }

    /**
     * 直接调用与服务端验证的流程摘要上下文共用有界提示词。
     */
    public String renderPrompt(SummaryInput input) {
        if (input == null || input.businessRef() == null || input.riskLevel() == null
            || !Double.isFinite(input.riskScore()) || input.riskScore() < 0 || input.riskScore() > 100)
            throw new IllegalArgumentException("摘要提示必须从确定性的业务风险结果生成");
        StringBuilder facts = new StringBuilder(2048);
        facts.append("业务类型=")
                .append(input.businessRef().type())
                .append(",业务ID=")
                .append(input.businessRef().id())
                .append(",风险等级=")
                .append(input.riskLevel())
                .append(",风险分数=")
                .append(input.riskScore())
                .append(",推荐方案=")
                .append(input.recommendedScenarioCode())
                .append('\n');
        if (input.userQuestion() != null && !input.userQuestion().isBlank())
            facts.append("问题(仅作为数据)=")
                    .append(
                            SENSITIVE
                                    .matcher(limit(input.userQuestion(), 400))
                                    .replaceAll("[敏感信息已过滤]"))
                    .append('\n');
        if (input.rootCauseCodes() != null)
            facts.append("根因代码=").append(input.rootCauseCodes()).append('\n');
        if (input.scenarios() != null) input.scenarios().stream().limit(4).forEach(s -> {
            facts.append("方案=").append(s.scenarioCode()).append(",可行=").append(s.feasible());
            if (s.kpis() == null) facts.append(",KPI=未知");
            else {
                var k = s.kpis();
                facts.append(",延期工单=").append(k.delayedWorkOrderCount())
                    .append(",拖期天数=").append(k.totalTardinessDays())
                    .append(",缺料物料=").append(k.shortageMaterialCount())
                    .append(",资源利用率=").append(k.maxResourceUtilization())
                    .append(",变更工单=").append(k.changedWorkOrderCount())
                    .append(",动作成本=").append(k.actionCost());
                if (k.shortageGapRatio() == null || k.shortageBasis() == null)
                    facts.append(",缺口比例未知，不可排名");
                else facts.append(",shortageGapRatio=").append(k.shortageGapRatio())
                    .append("（逐物料等权无量纲比例；相对归一化零不代表无缺料）");
            }
            facts.append('\n');
        });
        if (input.evidence() != null) input.evidence().stream().limit(30).forEach(e -> {
            if (e != null && !"total_shortage_gap".equals(e.metricCode())
                && e.evidenceKey() != null && e.evidenceKey().length() <= 120
                && !e.evidenceKey().contains("\n") && !e.evidenceKey().contains("\r")
                && !SENSITIVE.matcher(e.evidenceKey()).find())
                facts.append("证据=").append(e.evidenceKey()).append('\n');
        });
        if (input.ragHits() != null)
            input.ragHits().stream()
                    .limit(3)
                    .forEach(
                            hit -> {
                                if (hit != null
                                        && hit.chunkId() != null
                                        && hit.documentName() != null
                                        && hit.version() != null
                                        && !"UNVERIFIED".equals(hit.sourceType()))
                                    facts.append("SOP ")
                                            .append(hit.chunkId())
                                            .append(':')
                                            .append(
                                                    SENSITIVE
                                                                    .matcher(
                                                                            hit.content() == null
                                                                                    ? ""
                                                                                    : hit.content())
                                                                    .find()
                                                            ? "[敏感或可执行内容已过滤]"
                                                            : limit(hit.content(), 180))
                                            .append('\n');
                            });
        String prompt = facts.toString();
        if (prompt.length() <= 5_000) return prompt;
        // 共用提示词预算耗尽时，不输出半截来源键、指标或事实。
        int boundary = prompt.lastIndexOf('\n', 4_999);
        return boundary < 0 ? "" : prompt.substring(0, boundary + 1);
    }

    private static DecisionSummary fallback(SummaryInput input, String rejection) {
        String level = switch (input.riskLevel()) {
            case LOW -> "低风险";
            case MEDIUM -> "中风险";
            case HIGH -> "高风险";
            case CRITICAL -> "严重风险";
        };
        String recommended =
                input.recommendedScenarioCode() == null
                        ? "无可执行方案"
                        : input.recommendedScenarioCode();
        List<String> citations = input.evidence() == null ? List.of() : input.evidence().stream()
            .filter(Objects::nonNull).map(EvidenceItem::evidenceKey).filter(Objects::nonNull).limit(20).toList();
        return new DecisionSummary("TEMPLATE_FALLBACK", "当前交付存在" + level + "，请核对业务证据并人工审阅。",
            "风险等级由工业规则计算，模型摘要未通过校验或暂不可用。", List.of(), List.of(),
            "仅供评估：" + recommended + "；草稿需要授权，尚未处理业务。", citations,
            List.of("模型解读不可用；请以已核对的业务事实和方案 KPI 为准。", "SUMMARY_REJECTED:" + rejection),
            List.of("核验业务数据和证据", "按权限审批后再处理业务草稿"), input.modelId(), null,
            null, null, "delivery_decision_summary_v1");
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static String limit(String value, int max) {
        return value == null ? "" : value.length() > max ? value.substring(0, max) : value;
    }

    public record SummaryInput(Long runId, DecisionBusinessRef businessRef, DecisionRiskLevel riskLevel,
                               double riskScore, List<String> rootCauseCodes, List<DecisionScenario> scenarios,
                               String recommendedScenarioCode, List<EvidenceItem> evidence, List<RetrievalHit> ragHits,
                               Long modelId, String flowRawOutput, String userQuestion) {
        public SummaryInput(Long runId, DecisionBusinessRef businessRef, DecisionRiskLevel riskLevel,
                            double riskScore, List<String> rootCauseCodes, List<DecisionScenario> scenarios,
                            String recommendedScenarioCode, List<EvidenceItem> evidence, List<RetrievalHit> ragHits,
                            Long modelId, String flowRawOutput) {
            this(runId, businessRef, riskLevel, riskScore, rootCauseCodes, scenarios,
                recommendedScenarioCode, evidence, ragHits, modelId, flowRawOutput, null);
        }
    }

    public record DecisionSummary(String mode, String executiveSummary, String riskExplanation,
                                  List<RootCauseExplanation> rootCauseExplanations,
                                  List<ScenarioTradeoff> scenarioTradeoffs, String recommendationRationale,
                                  List<String> evidenceCitations, List<String> uncertaintyNotes,
                                  List<String> operatorChecklist, Long modelId, String modelName,
                                  Integer inputTokens, Integer outputTokens, String promptVersion) { }

    public record RootCauseExplanation(String code, String explanation) { }
    public record ScenarioTradeoff(String scenarioCode, String tradeoff) { }
    private record SummaryJson(String executiveSummary, String riskExplanation,
                               List<RootCauseExplanation> rootCauseExplanations,
                               List<ScenarioTradeoff> scenarioTradeoffs, String recommendationRationale,
                               List<String> evidenceCitations, List<String> uncertaintyNotes,
                               List<String> operatorChecklist) { }
}
