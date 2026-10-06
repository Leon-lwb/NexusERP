package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 纯服务端大模型叙述及动作护栏，违反规则时拒绝处理。
 */
@Service
public class DecisionPolicyGuardService {
    private static final Pattern SECRETS = Pattern.compile("(?i)(?:Authorization\\s*[:=]\\s*Bearer|Bearer\\s+[A-Za-z0-9._-]{8,}|sk-[A-Za-z0-9_-]{5,}|jdbc:(?:mysql|postgresql)|api[_-]?key\\s*[:=])");
    private static final Pattern BARE_NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_])[0-9]+(?:\\.[0-9]+)?(?![\\p{L}\\p{N}_])");
    private static final Pattern COMMAND = Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:select|update|insert|delete|drop|truncate|alter)(?![A-Za-z0-9_])\\s+(?:[a-z_]+\\s+)?[a-z_]");
    private static final Pattern BUSINESS_CODE = Pattern.compile("(?<![A-Za-z0-9])(SO|WO|APS)-[A-Za-z0-9_-]+");
    private static final List<String> FALSE_STATUS = List.of("已发布", "已审批", "已过账", "已执行", "已扣减", "审批通过", "发布排产", "自动审批采购", "库存扣减", "自动过账");

    public PolicyResult validate(DecisionResult result, DecisionNarrative narrative,
                                 List<DecisionActionProposal> actions) {
        if (result == null || narrative == null || !Double.isFinite(result.riskScore())) {
            throw new IllegalArgumentException("工业决策及摘要不能为空，风险分数必须有效");
        }
        List<PolicyViolation> errors = new ArrayList<>();
        if (result.riskLevel() == null || result.riskLevel() != DecisionRiskLevel.fromScore(result.riskScore())) {
            errors.add(block("RISK_TAMPERED", "riskLevel", "风险等级与工业评分不一致"));
        }
        List<DecisionScenario> scenarios = result.scenarios() == null ? List.of() : result.scenarios();
        DecisionScenario best = scenarios.stream().filter(Objects::nonNull).filter(s -> s.feasible() && s.kpis() != null)
            .min(java.util.Comparator.comparingInt(DecisionScenario::rank)
                .thenComparing(DecisionScenario::scenarioCode)).orElse(null);
        if (best == null ? result.recommendedScenarioCode() != null
            : !best.scenarioCode().equals(result.recommendedScenarioCode())) {
            errors.add(block("RECOMMENDATION_TAMPERED", "recommendedScenarioCode", "推荐方案与确定性排名不一致"));
        }
        Set<String> evidenceKeys = result.evidence() == null ? Set.of() : result.evidence().stream()
            .filter(Objects::nonNull).map(EvidenceItem::evidenceKey).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (narrative.evidenceCitations() == null || narrative.evidenceCitations().stream()
            .anyMatch(key -> key == null || !evidenceKeys.contains(key))) {
            errors.add(block("MISSING_EVIDENCE", "evidenceCitations", "引用缺少可核对的证据键"));
        }
        Set<String> knownCodes = new HashSet<>();
        if (result.businessRef() != null && result.businessRef().code() != null) knownCodes.add(result.businessRef().code());
        if (result.evidence() != null) result.evidence().stream().filter(Objects::nonNull)
            .map(EvidenceItem::sourceCode).filter(Objects::nonNull).forEach(knownCodes::add);
        for (DecisionScenario scenario : scenarios) if (scenario != null) knownCodes.add(scenario.scenarioCode());
        String joined = String.join(" ", textFields(narrative));
        if (joined.length() > 5000) {
            errors.add(block("NARRATIVE_TOO_LONG", "narrative", "摘要超出最大字符限制"));
        }
        Matcher matcher = BUSINESS_CODE.matcher(joined);
        while (matcher.find()) {
            if (!knownCodes.contains(matcher.group())) {
                errors.add(block("UNKNOWN_BUSINESS_CODE", "narrative", "摘要提及不在业务证据范围内的编号"));
                break;
            }
        }
        if (narrative.rootCauseExplanations() != null && narrative.rootCauseExplanations().stream()
            .anyMatch(explanation -> explanation == null || explanation.code() == null
                || result.rootCauseCodes() == null || !result.rootCauseCodes().contains(explanation.code()))) {
            errors.add(block("ROOT_CAUSE_TAMPERED", "rootCauseExplanations", "模型增加了未计算的根因"));
        }
        Set<String> actualScenarioCodes = scenarios.stream().filter(Objects::nonNull)
            .map(DecisionScenario::scenarioCode).collect(Collectors.toSet());
        if (narrative.scenarioTradeoffs() != null && narrative.scenarioTradeoffs().stream()
            .anyMatch(tradeoff -> tradeoff == null || tradeoff.scenarioCode() == null
                || !actualScenarioCodes.contains(tradeoff.scenarioCode()))) {
            errors.add(block("SCENARIO_TAMPERED", "scenarioTradeoffs", "模型增加了未计算的方案"));
        }
        String withoutKnownCodes = joined;
        for (String known : knownCodes) if (known != null && !known.isBlank()) {
            withoutKnownCodes = withoutKnownCodes.replace(known, " ");
        }
        if (BARE_NUMBER.matcher(withoutKnownCodes).find()) {
            errors.add(block("UNSUPPORTED_NUMBER", "narrative", "摘要数值只允许引用已核验的证据键"));
        }
        if (COMMAND.matcher(joined).find()) {
            errors.add(block("UNSAFE_COMMAND", "narrative", "摘要中不能包含可执行数据库指令"));
        }
        if (SECRETS.matcher(joined).find()) {
            errors.add(block("SENSITIVE_CONTENT", "narrative", "摘要包含凭证或连接信息"));
        }
        if ((result.riskLevel() == DecisionRiskLevel.HIGH || result.riskLevel() == DecisionRiskLevel.CRITICAL)
            && (joined.contains("低风险") || joined.contains("无风险"))
            || result.riskLevel() == DecisionRiskLevel.LOW
                && (joined.contains("高风险") || joined.contains("严重风险"))) {
            errors.add(block("RISK_TAMPERED", "narrative", "摘要不能改写确定性风险等级"));
        }
        if ((result.riskLevel() == DecisionRiskLevel.HIGH || result.riskLevel() == DecisionRiskLevel.CRITICAL)
            && (joined.contains("无需审批") || joined.contains("不需要审批") || joined.contains("自动执行"))) {
            errors.add(block("APPROVAL_REQUIRED", "narrative", "高风险动作须经人工审批"));
        }
        if (FALSE_STATUS.stream().anyMatch(joined::contains)) {
            errors.add(block("FORBIDDEN_STATUS", "narrative", "尚未执行，不得宣称业务已处理"));
        }
        boolean forcedApproval = result.riskLevel() == DecisionRiskLevel.HIGH
            || result.riskLevel() == DecisionRiskLevel.CRITICAL;
        if (actions != null) {
            for (DecisionActionProposal action : actions) {
                if (action == null || action.actionType() == null
                    || action.target() == null || !action.target().equals(result.businessRef())
                    || !Objects.equals(action.permissionCode(), action.actionType().permissionCode())
                    || !Objects.equals(action.executorCode(), action.actionType().executorCode())
                    || !validPayload(action.actionType(), action.payload(), result.runId())
                    || !validPurchaseIdentity(action, result.runId())) {
                    errors.add(block("ACTION_NOT_ALLOWED", "actions", "决策动作/业务对象不属于白名单"));
                }
                if (action != null && action.riskLevel() != result.riskLevel()) {
                    errors.add(block("RISK_TAMPERED", "actions.riskLevel", "动作风险等级与工业评分不一致"));
                }
                if (action != null && action.actionType() != null
                    && (forcedApproval || action.actionType().approvalRequired()) && !action.approvalRequired()) {
                    errors.add(block("APPROVAL_REQUIRED", "actions", "高风险/业务草稿动作必须审批"));
                }
            }
        }
        DecisionNarrative sanitized = errors.isEmpty() ? narrative : redactedNarrative(narrative);
        return new PolicyResult(errors.isEmpty(), List.copyOf(errors), sanitized, !errors.isEmpty(), forcedApproval);
    }

    /**
     * 原始动作列表必须先通过白名单，再转换为枚举。
     */
    public PolicyResult validateActions(List<Map<String, Object>> actions) {
        List<PolicyViolation> violations = new ArrayList<>();
        if (actions == null) {
            violations.add(block("ACTION_NOT_ALLOWED", "actions", "动作列表不能为空"));
        } else for (Map<String, Object> action : actions) {
            Object type = action == null ? null : action.get("actionType");
            DecisionActionType known;
            try { known = DecisionActionType.valueOf((String) type); }
            catch (RuntimeException e) {
                violations.add(block("ACTION_NOT_ALLOWED", "actions.actionType", "动作类型不在白名单"));
                continue;
            }
            if (!validPayload(known, action.get("payload"), null)) {
                violations.add(block("ACTION_NOT_ALLOWED", "actions.payload", "动作载荷存在未授权字段"));
            }
            if (known.approvalRequired() && !Boolean.TRUE.equals(action.get("approvalRequired"))) {
                violations.add(block("APPROVAL_REQUIRED", "actions.approvalRequired", "业务草稿动作必须审批"));
            }
        }
        return new PolicyResult(violations.isEmpty(), List.copyOf(violations), null, !violations.isEmpty(), false);
    }

    private static boolean validPurchaseIdentity(DecisionActionProposal action, Long runId) {
        if (action.actionType() != DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) return true;
        try {
            var contract = DecisionPurchaseContractCodec.parse(action.payload(), runId);
            return DecisionPurchaseContractCodec.idempotencyKey(TenantHelper.getDynamic(), runId,
                action.target(), contract).equals(action.idempotencyKey());
        } catch (RuntimeException invalid) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static boolean validPayload(DecisionActionType type, Object candidate, Long runId) {
        if (!(candidate instanceof Map<?, ?> payload) || payload.isEmpty()) return false;
        if (type == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) {
            try {
                DecisionPurchaseContractCodec.parse((Map<String, Object>) payload, runId);
                return true;
            } catch (RuntimeException invalid) { return false; }
        }
        Set<String> allowed = switch (type) {
            case SEND_EXPEDITE_NOTICE -> Set.of("runId", "purchaseLineIds");
            case CREATE_PURCHASE_REQUISITION_DRAFT -> Set.of();
            case CREATE_SCHEDULE_PLAN_DRAFT -> Set.of("runId", "scenarioCode", "strategy", "startDate",
                "horizonDays", "workOrderIds", "workshopId");
            case SEND_MANAGER_NOTIFICATION -> Set.of("runId", "targetUserId");
        };
        if (!payload.keySet().stream().allMatch(allowed::contains)) return false;
        if (!positiveId(payload.get("runId"))) return false;
        if (runId != null && !String.valueOf(runId).equals(payload.get("runId"))) return false;
        switch (type) {
            case SEND_MANAGER_NOTIFICATION -> {
                return positiveId(payload.get("targetUserId"));
            }
            case SEND_EXPEDITE_NOTICE -> {
                return validIds(payload.get("purchaseLineIds"));
            }
            case CREATE_PURCHASE_REQUISITION_DRAFT -> { return false; }
            case CREATE_SCHEDULE_PLAN_DRAFT -> {
                Object code = payload.get("scenarioCode");
                String strategy = "APS_EDD".equals(code) ? "EDD"
                    : "APS_CR".equals(code) || "APS_CR_EXPEDITE".equals(code) ? "CR" : null;
                return strategy != null && strategy.equals(payload.get("strategy"))
                    && date(payload.get("startDate"))
                    && payload.get("horizonDays") instanceof Integer horizon && horizon >= 1 && horizon <= 90
                    && validIds(payload.get("workOrderIds"))
                    && (!payload.containsKey("workshopId") || positiveId(payload.get("workshopId")));
            }
        }
        return false;
    }

    private static boolean positiveId(Object raw) {
        if (!(raw instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) return false;
        try { Long.parseLong(text); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean validIds(Object raw) {
        if (!(raw instanceof List<?> ids) || ids.isEmpty() || ids.size() > 500
            || ids.stream().anyMatch(id -> !positiveId(id))) return false;
        return new HashSet<>(ids).size() == ids.size();
    }

    private static boolean date(Object raw) {
        if (!(raw instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try { LocalDate.parse(text); return true; }
        catch (RuntimeException e) { return false; }
    }

    private static List<String> textFields(DecisionNarrative n) {
        List<String> texts = new ArrayList<>();
        if (n.executiveSummary() != null) texts.add(n.executiveSummary());
        if (n.riskExplanation() != null) texts.add(n.riskExplanation());
        if (n.recommendationRationale() != null) texts.add(n.recommendationRationale());
        if (n.rootCauseExplanations() != null) n.rootCauseExplanations().stream().filter(Objects::nonNull)
            .map(DecisionSummaryService.RootCauseExplanation::explanation).filter(Objects::nonNull).forEach(texts::add);
        if (n.scenarioTradeoffs() != null) n.scenarioTradeoffs().stream().filter(Objects::nonNull)
            .map(DecisionSummaryService.ScenarioTradeoff::tradeoff).filter(Objects::nonNull).forEach(texts::add);
        if (n.uncertaintyNotes() != null) n.uncertaintyNotes().stream().filter(Objects::nonNull).forEach(texts::add);
        if (n.operatorChecklist() != null) n.operatorChecklist().stream().filter(Objects::nonNull).forEach(texts::add);
        return texts;
    }

    private static DecisionNarrative redactedNarrative(DecisionNarrative source) {
        return new DecisionNarrative("模型摘要未通过安全校验，请仅按业务事实人工审阅。", "使用已核验的业务证据。",
            List.of(), List.of(), "请人工评估推荐方案。", List.of(),
            List.of("原摘要已拦截。"), List.of("核查证据及审批状态"));
    }
    private static PolicyViolation block(String code, String field, String message) {
        return new PolicyViolation(code, "BLOCK", field, message);
    }

    public record DecisionNarrative(String executiveSummary, String riskExplanation,
        List<DecisionSummaryService.RootCauseExplanation> rootCauseExplanations,
        List<DecisionSummaryService.ScenarioTradeoff> scenarioTradeoffs, String recommendationRationale,
        List<String> evidenceCitations, List<String> uncertaintyNotes, List<String> operatorChecklist) { }
    public record PolicyViolation(String code, String severity, String fieldPath, String message) { }
    public record PolicyResult(boolean passed, List<PolicyViolation> violations,
                               DecisionNarrative sanitizedNarrative, boolean requiresRegeneration,
                               boolean forcedApproval) { }
}
