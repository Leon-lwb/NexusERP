package com.nexus.ai.service.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.executor.DecisionActionExecutor;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按依赖顺序和有界重试策略执行已批准且租户范围内的动作。
 */
@Service
public class DecisionActionExecutionService {
    private final DecisionPersistenceService persistence;
    private final Map<DecisionActionType, DecisionActionExecutor> executors;
    private final ObjectMapper mapper;
    private final AiDecisionSseMessageHelper events;
    private final PermissionService permissionService;
    @Autowired
    public DecisionActionExecutionService(DecisionPersistenceService persistence,
                                          List<DecisionActionExecutor> implementations, ObjectMapper mapper,
                                          AiDecisionSseMessageHelper events, PermissionService permissionService) {
        this.persistence = persistence; this.mapper = mapper; this.events = events;
        this.permissionService = permissionService;
        this.executors = new EnumMap<>(DecisionActionType.class);
        for (DecisionActionExecutor executor : implementations) {
            if (executors.putIfAbsent(executor.type(), executor) != null)
                throw new IllegalArgumentException("决策动作执行器类型重复: " + executor.type());
        }
    }

    public DecisionActionExecutionService(DecisionPersistenceService persistence,
                                          List<DecisionActionExecutor> implementations, ObjectMapper mapper,
                                          AiDecisionSseMessageHelper events) {
        this(persistence, implementations, mapper, events, null);
    }
    public DecisionActionExecutionService(DecisionPersistenceService persistence,
                                          List<DecisionActionExecutor> implementations, ObjectMapper mapper) {
        this(persistence, implementations, mapper, null, null);
    }

    public void retry(Long actionId, DecisionActor actor) {
        validate(actor);
        TenantHelper.dynamic(actor.tenantId(), () -> {
            AiDecisionAction action = persistence.requireAction(actionId, actor.tenantId());
            if ("SUCCEEDED".equals(action.getExecutionStatus())) return;
            if (!"APPROVED".equals(action.getApprovalStatus()) || !"FAILED".equals(action.getExecutionStatus()))
                throw new ServiceException("只有已经批准且失败的动作可重试");
            var run = persistence.requireRun(action.getRunId(), actor.tenantId());
            // 终态运行保持不可变；终态上的动作失败必须重新分析，
            // 不得原地改写，否则会伪造运行与结果状态。
            if (!"EXECUTING".equals(run.getStatus()))
                throw new ServiceException("运行已结束；请重新分析后执行，不可原地重试动作");
            executeOne(action, actor, true);
        });
    }

    public void executeApproved(Long runId, DecisionActor actor) {
        validate(actor);
        TenantHelper.dynamic(actor.tenantId(), () -> {
            var run = persistence.requireRun(runId, actor.tenantId());
            if (!DecisionRunStatus.APPROVED.name().equals(run.getStatus()))
                throw new ServiceException("只有审批通过的运行可执行");
            List<AiDecisionAction> approved = persistence.listActions(runId).stream()
                .filter(a -> "APPROVED".equals(a.getApprovalStatus())).toList();
            if (approved.isEmpty()) throw new ServiceException("运行没有已批准的动作");
            List<AiDecisionAction> ordered = order(approved);
            for (AiDecisionAction action : ordered) validateAction(action, actor);
            persistence.transition(runId, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING);
            Set<Long> succeeded = new HashSet<>();
            int failures = 0;
            for (AiDecisionAction action : ordered) {
                List<Long> dependencies = dependencies(action);
                if (!succeeded.containsAll(dependencies)) {
                    persistence.transitionAction(action.getId(), "APPROVED", "NOT_STARTED", "SKIPPED");
                    failures++; continue;
                }
                ExecutionOutcome outcome = executeOne(action, actor, false);
                if (outcome == ExecutionOutcome.SUCCESS) succeeded.add(action.getId());
                else if (outcome == ExecutionOutcome.FAILED) failures++;
                else {
                    // 不得将此前成功动作误称为已回滚。
                    boolean allCompensated = outcome == ExecutionOutcome.COMPENSATED && succeeded.isEmpty();
                    for (AiDecisionAction later : ordered.subList(ordered.indexOf(action) + 1, ordered.size()))
                        persistence.transitionAction(later.getId(), "APPROVED", "NOT_STARTED", "SKIPPED");
                    persistence.transition(runId, DecisionRunStatus.ROLLING_BACK,
                        allCompensated ? DecisionRunStatus.ROLLED_BACK : DecisionRunStatus.FAILED);
                    return;
                }
            }
            DecisionRunStatus target = failures == 0 ? DecisionRunStatus.SUCCEEDED
                : succeeded.isEmpty() ? DecisionRunStatus.FAILED : DecisionRunStatus.PARTIAL_SUCCESS;
            persistence.transition(runId, DecisionRunStatus.EXECUTING, target);
        });
    }

    private void validateAction(AiDecisionAction row, DecisionActor actor) {
        DecisionActionType type;
        try { type = DecisionActionType.valueOf(row.getActionType()); }
        catch (RuntimeException invalid) { throw new ServiceException("动作类型未受支持"); }
        if (!executors.containsKey(type) || !type.executorCode().equals(row.getExecutorCode())
            || !type.permissionCode().equals(row.getPermissionCode())
            || !hasLivePermission(actor, type.permissionCode()))
            throw new ServiceException("动作执行器或当前操作人权限无效");
        proposal(row, type); // 修改运行前先反序列化并验证冻结载荷。
    }

    private enum ExecutionOutcome { SUCCESS, FAILED, COMPENSATED, COMPENSATION_FAILED }

    private ExecutionOutcome executeOne(AiDecisionAction row, DecisionActor actor, boolean retryFailed) {
        DecisionActionType type;
        try { type = DecisionActionType.valueOf(row.getActionType()); }
        catch (RuntimeException e) { throw new ServiceException("动作类型未受支持"); }
        DecisionActionExecutor executor = executors.get(type);
        if (executor == null || !type.executorCode().equals(row.getExecutorCode())
            || !type.permissionCode().equals(row.getPermissionCode()))
            throw new ServiceException("动作缺少可信执行器或权限配置");
        if (!hasLivePermission(actor, type.permissionCode()))
            throw new ServiceException("执行人缺少此动作所需权限");
        var action = proposal(row, type);
        persistence.transitionAction(row.getId(), "APPROVED", retryFailed ? "FAILED" : "NOT_STARTED", "EXECUTING");
        DecisionActionExecutionResult result = null;
        DecisionActionExecutionResult createdButUnverified = null;
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            try {
                executor.precheck(action, actor);
                var performed = executor.execute(action, actor);
                if (performed != null && performed.success()) {
                    createdButUnverified = performed;
                    try { result = executor.verify(action, performed, actor); }
                    catch (RuntimeException unsafe) {
                        result = new DecisionActionExecutionResult(false, false, performed.resultBizType(),
                            performed.resultBizId(), performed.resultBizCode(), Map.of(), "VERIFY_FAILED",
                            "业务草稿独立核验失败");
                    }
                    if (result == null || !result.success()) break; // 成功写入后不得再次创建草稿。
                    createdButUnverified = null;
                } else result = performed;
                if (result != null && result.success()) break;
                if (result == null || !result.retryable() || attempts == 3) break;
            } catch (RuntimeException error) {
                if (attempts == 3 || !executor.isRetryable(error)) {
                    result = new DecisionActionExecutionResult(false, false, null, null, null,
                        Map.of(), "ACTION_FAILED", "动作处理失败，请查看审计日志");
                    break;
                }
            }
            try { Thread.sleep(attempts == 1 ? 500 : 1500); }
            catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                result = new DecisionActionExecutionResult(false, false, null, null, null,
                    Map.of(), "INTERRUPTED", "动作重试被中断");
                break;
            }
        }
        if (result == null) result = new DecisionActionExecutionResult(false, false, null, null, null,
            Map.of(), "EMPTY_RESULT", "执行器未返回可验证结果");
        persistence.saveActionResult(row.getId(), result, attempts);
        if (events != null) events.sendActionStatus(actor.userId(), row.getRunId(), row.getId(),
            result.success() ? "SUCCEEDED" : "FAILED");
        if (createdButUnverified == null) return result.success() ? ExecutionOutcome.SUCCESS : ExecutionOutcome.FAILED;
        // ERP 草稿已创建但独立核验失败时，停止所有后续动作，
        // 进入可审计回滚状态，并交由 ERP 仅限草稿的补偿护栏处理。
        persistence.transition(row.getRunId(), DecisionRunStatus.EXECUTING, DecisionRunStatus.ROLLING_BACK);
        persistence.transitionAction(row.getId(), "APPROVED", "FAILED", "COMPENSATING");
        DecisionActionExecutionResult compensation;
        try { compensation = executor.compensate(action, createdButUnverified, actor); }
        catch (RuntimeException unsafe) {
            compensation = new DecisionActionExecutionResult(false, false, createdButUnverified.resultBizType(),
                createdButUnverified.resultBizId(), createdButUnverified.resultBizCode(), Map.of(),
                "COMPENSATION_FAILED", "业务草稿无法安全补偿，须人工核对");
        }
        if (compensation == null) compensation = new DecisionActionExecutionResult(false, false,
            createdButUnverified.resultBizType(), createdButUnverified.resultBizId(),
            createdButUnverified.resultBizCode(), Map.of(), "COMPENSATION_FAILED", "补偿执行器未返回结果");
        persistence.saveCompensationResult(row.getId(), compensation);
        if (events != null) events.sendActionStatus(actor.userId(), row.getRunId(), row.getId(),
            compensation.success() ? "COMPENSATED" : "COMPENSATE_FAILED");
        return compensation.success() ? ExecutionOutcome.COMPENSATED : ExecutionOutcome.COMPENSATION_FAILED;
    }

    private List<AiDecisionAction> order(List<AiDecisionAction> actions) {
        Map<Long, AiDecisionAction> pending = new HashMap<>();
        for (AiDecisionAction action : actions) pending.put(action.getId(), action);
        List<AiDecisionAction> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            AiDecisionAction candidate = pending.values().stream().filter(a -> visited.containsAll(dependencies(a)))
                .min(java.util.Comparator.comparing(AiDecisionAction::getSequenceNo,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                    .thenComparing(AiDecisionAction::getId)).orElse(null);
            if (candidate == null) throw new ServiceException("动作依赖无效或包含循环");
            for (Long id : dependencies(candidate)) if (!visited.contains(id) && !pending.containsKey(id))
                throw new ServiceException("动作依赖跨越审批范围");
            result.add(candidate); visited.add(candidate.getId()); pending.remove(candidate.getId());
        }
        return result;
    }

    private List<Long> dependencies(AiDecisionAction action) {
        if (action.getDependencyIdsJson() == null) return List.of();
        try { return mapper.readValue(action.getDependencyIdsJson(), new TypeReference<List<Long>>() {}); }
        catch (Exception e) { throw new ServiceException("动作依赖无法解析"); }
    }

    private DecisionActionProposal proposal(AiDecisionAction row, DecisionActionType type) {
        try {
            var payload = mapper.readValue(row.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            return new DecisionActionProposal(row.getId(), type, row.getTitle(), row.getDisplayReason(),
                new DecisionBusinessRef(DecisionBusinessType.valueOf(row.getTargetBizType()),
                    row.getTargetBizId(), row.getTargetBizCode()), payload,
                DecisionRiskLevel.valueOf(row.getRiskLevel()), "Y".equals(row.getApprovalRequired()),
                row.getPermissionCode(), row.getIdempotencyKey(), row.getExecutorCode(), row.getSequenceNo(),
                dependencies(row));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException bad) {
            throw new ServiceException("决策动作载荷无效");
        }
    }

    private void validate(DecisionActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || actor.tenantId() == null
            || actor.permissions() == null || !hasLivePermission(actor, "ai:decision:execute"))
            throw new ServiceException("决策执行身份无效");
    }

    private boolean hasLivePermission(DecisionActor actor, String code) {
        if (!actor.permissions().contains(code) && !actor.permissions().contains("*:*:*")) return false;
        if (permissionService == null) return true; // 仅供隔离单元测试使用的构造器。
        try {
            Set<String> current = TenantHelper.dynamic(actor.tenantId(),
                () -> permissionService.getMenuPermission(actor.userId()));
            return current != null && (current.contains(code) || current.contains("*:*:*"));
        } catch (RuntimeException ignored) { return false; }
    }
}
