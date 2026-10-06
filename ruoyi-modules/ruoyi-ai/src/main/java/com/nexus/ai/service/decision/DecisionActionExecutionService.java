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
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import java.util.Objects;
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
    private final IndustrialDecisionDataPort dataPort;
    private final DecisionFinalizeService finalizeService;

    @Autowired
    public DecisionActionExecutionService(DecisionPersistenceService persistence,
                                          List<DecisionActionExecutor> implementations, ObjectMapper mapper,
                                          AiDecisionSseMessageHelper events, PermissionService permissionService,
                                          IndustrialDecisionDataPort dataPort, DecisionFinalizeService finalizeService) {
        this.dataPort = dataPort; this.finalizeService = finalizeService;
        this.persistence = persistence; this.mapper = mapper; this.events = events;
        this.permissionService = permissionService;
        this.executors = new EnumMap<>(DecisionActionType.class);
        for (DecisionActionExecutor executor : implementations) {
            if (executors.putIfAbsent(executor.type(), executor) != null)
                throw new IllegalArgumentException("决策动作执行器类型重复: " + executor.type());
        }
    }

    /** 旧非采购隔离测试重载；新版采购必须显式提供生产依赖。 */
    public DecisionActionExecutionService(DecisionPersistenceService persistence,
                                          List<DecisionActionExecutor> implementations, ObjectMapper mapper,
                                          AiDecisionSseMessageHelper events, PermissionService permissionService) {
        this(persistence, implementations, mapper, events, permissionService, null, null);
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

    /** 仅采购执行或恢复的待确认断点；Flow 可失败，但不得把业务运行关闭为终态。 */
    public static final class PurchaseExecutionPendingException extends RuntimeException {
        public PurchaseExecutionPendingException() { super("采购执行或恢复尚未确认，保留原运行与动作断点"); }
    }

    /** 初次新版执行的读取或权限未知，不是已经证明业务来源变化。 */
    public static final class InitialExecutionCheckUnavailableException extends RuntimeException {
        public InitialExecutionCheckUnavailableException() { super("无法确认执行的业务快照或权限，运行状态保持不变"); }
    }

    public void retry(Long actionId, DecisionActor actor) {
        validate(actor);
        TenantHelper.dynamic(actor.tenantId(), () -> {
            AiDecisionAction row = persistence.requireAction(actionId, actor.tenantId());
            boolean purchase = isPurchase(row);
            // 旧非采购的幂等短路不依赖新增采购端口，保留既有隔离测试与执行语义。
            if (!purchase && "SUCCEEDED".equals(row.getExecutionStatus()) && finalizeService == null) return;
            AiDecisionRun run = persistence.requireRun(row.getRunId(), actor.tenantId());
            DecisionPurchaseContractGuard.requireExecutable(run, row);
            if (purchase) {
                if (!"APPROVED".equals(row.getApprovalStatus())) throw new ServiceException("采购动作尚未批准");
                if (!"EXECUTING".equals(run.getStatus())) {
                    if ("SUCCEEDED".equals(row.getExecutionStatus()) && "SUCCEEDED".equals(run.getStatus())) {
                        DecisionActor bound = requireBoundPurchaseActor(run, actor);
                        validateAction(row, bound); verifyStoredResult(row, bound);
                        return; // 已终态仅核验，不改动作、运行或历史业务引用。
                    }
                    throw new ServiceException("运行已结束；请重新分析后执行，不可原地重试动作");
                }
                DecisionActor bound = requireBoundPurchaseActor(run, actor);
                validateAction(row, bound);
                if ("SUCCEEDED".equals(row.getExecutionStatus())) verifyStoredResult(row, bound);
                else if ("EXECUTING".equals(row.getExecutionStatus()) || "FAILED".equals(row.getExecutionStatus())) {
                    var receipt = dataPort.recoverPurchaseAction(proposal(row, purchaseType()), bound);
                    if (receipt != null) saveRecovered(row, verifyPurchaseResult(row, receipt, bound), bound);
                    else if ("EXECUTING".equals(row.getExecutionStatus())) throw uncertainPurchase();
                    else if (executePurchase(row, bound, true, true) != ExecutionOutcome.SUCCESS)
                        throw new ServiceException("采购重试未成功，保留原动作等待复核");
                } else throw new ServiceException("只有已批准且执行中、失败或成功待收敛的采购动作可恢复");
                resumeApproved(run.getId(), bound, Set.of(row.getId()));
                return;
            }
            if (!"APPROVED".equals(row.getApprovalStatus())) throw new ServiceException("动作尚未批准");
            if ("SUCCEEDED".equals(row.getExecutionStatus())) {
                if ("EXECUTING".equals(run.getStatus())) {
                    validateAction(row, actor); verifyStoredResult(row, actor);
                    resumeApproved(run.getId(), actor, Set.of(row.getId()));
                }
                return;
            }
            if (!"FAILED".equals(row.getExecutionStatus())) throw new ServiceException("只有已经批准且失败的动作可重试");
            if (!"EXECUTING".equals(run.getStatus()))
                throw new ServiceException("运行已结束；请重新分析后执行，不可原地重试动作");
            executeOne(row, actor, true);
        });
    }

    public void executeApproved(Long runId, DecisionActor actor) {
        try { validate(actor); }
        catch (RuntimeException unavailable) {
            if (actor != null && actor.tenantId() != null && !actor.tenantId().isBlank()
                && actor.userId() != null && actor.userId() > 0 && actor.permissions() != null)
                throw new InitialExecutionCheckUnavailableException(); // 鉴权未确认前不读取 master；Flow 只在受信任新版状态下保留断点。
            throw unavailable;
        }
        TenantHelper.dynamic(actor.tenantId(), () -> {
            var run = persistence.requireRun(runId, actor.tenantId());
            boolean resuming = DecisionRunStatus.EXECUTING.name().equals(run.getStatus());
            if (!resuming && !DecisionRunStatus.APPROVED.name().equals(run.getStatus()))
                throw new ServiceException("只有审批通过或尚未终态的执行中运行可执行");
            List<AiDecisionAction> rows = persistence.listActions(runId);
            if (rows == null || rows.stream().anyMatch(Objects::isNull))
                throw new ServiceException("执行动作列表不可证明");
            List<AiDecisionAction> approved = rows.stream().filter(a -> "APPROVED".equals(a.getApprovalStatus())).toList();
            if (approved.isEmpty()) throw new ServiceException("运行没有已批准的动作");
            for (AiDecisionAction action : approved)
                DecisionPurchaseContractGuard.requireExecutable(run, action);
            List<AiDecisionAction> ordered = order(approved);
            for (AiDecisionAction action : ordered) validateAction(action, actor);
            boolean hasPurchase = ordered.stream().anyMatch(this::isPurchase);
            DecisionActor executionActor = hasPurchase ? requireBoundPurchaseActor(run, actor) : actor;
            if (resuming) {
                requireRecoveryDependencies();
                resumeApproved(runId, executionActor, Set.of());
                return;
            }
            requireInitialSnapshotFreshness(run, executionActor);
            if (hasPurchase) {
                persistence.transition(runId, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING);
                resumeApproved(runId, executionActor, Set.of());
                return;
            }
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
                    /** 不得将此前成功动作误称为已回滚。 */
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

    /** 已提交新版采购的载荷/权限读取未确认只保留断点，不进入通用失败关闭。 */
    private void validateAction(AiDecisionAction row, DecisionActor actor) {
        try { validateActionFields(row, actor); }
        catch (RuntimeException unavailable) {
            if (isSucceededV2Purchase(row)) throw new PurchaseExecutionPendingException();
            throw unavailable;
        }
    }

    private void validateActionFields(AiDecisionAction row, DecisionActor actor) {
        DecisionActionType type;
        try { type = DecisionActionType.valueOf(row.getActionType()); }
        catch (RuntimeException invalid) { throw new ServiceException("动作类型未受支持"); }
        if (!executors.containsKey(type) || !type.executorCode().equals(row.getExecutorCode())
            || !type.permissionCode().equals(row.getPermissionCode()))
            throw new ServiceException("动作执行器或权限配置无效");
        if (!hasLivePermission(actor, type.permissionCode())) throw new InitialExecutionCheckUnavailableException();
        proposal(row, type); /** 修改运行前先反序列化并验证冻结载荷。 */
    }

    private enum ExecutionOutcome { SUCCESS, FAILED, COMPENSATED, COMPENSATION_FAILED }

    private ExecutionOutcome executeOne(AiDecisionAction row, DecisionActor actor, boolean retryFailed) {
        if (isPurchase(row)) {
            try { return executePurchase(row, actor, retryFailed, false); }
            catch (RuntimeException pending) { throw new PurchaseExecutionPendingException(); }
        }
        DecisionActionType type;
        try { type = DecisionActionType.valueOf(row.getActionType()); }
        catch (RuntimeException e) { throw new ServiceException("动作类型未受支持"); }
        DecisionActionExecutor executor = executors.get(type);
        if (executor == null || !type.executorCode().equals(row.getExecutorCode())
            || !type.permissionCode().equals(row.getPermissionCode()))
            throw new ServiceException("动作缺少可信执行器或权限配置");
        if (!hasLivePermission(actor, type.permissionCode()))
            throw new ServiceException("执行人缺少此动作所需权限");
        if (type == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT)
            DecisionPurchaseContractGuard.requireExecutable(
                persistence.requireRun(row.getRunId(), actor.tenantId()), row);
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
                    if (result == null || !result.success()) break; /** 成功写入后不得再次创建草稿。 */
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
        /**
         * ERP 草稿已创建但独立核验失败时，停止所有后续动作，
         * 进入可审计回滚状态，并交由 ERP 仅限草稿的补偿护栏处理。
         */
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

    /** 只用于首次新版 APPROVED 入口；在途恢复不得受自身落账改变的全局快照阻断。 */
    private void requireInitialSnapshotFreshness(AiDecisionRun run, DecisionActor actor) {
        if (!Integer.valueOf(2).equals(run.getDecisionContractVersion())) return;
        DecisionFreshnessResult freshness;
        try {
            if (dataPort == null || run.getInputSnapshotHash() == null || run.getInputSnapshotHash().isBlank())
                throw new InitialExecutionCheckUnavailableException();
            var target = new DecisionBusinessRef(DecisionBusinessType.valueOf(run.getBizType()), run.getBizId(), run.getBizCode());
            if (!dataPort.canAccess(target, actor)) throw new InitialExecutionCheckUnavailableException();
            freshness = dataPort.checkFreshness(target, run.getInputSnapshotHash(), actor);
            if (freshness == null || freshness.currentHash() == null || freshness.currentHash().isBlank())
                throw new InitialExecutionCheckUnavailableException();
        } catch (RuntimeException unavailable) { throw new InitialExecutionCheckUnavailableException(); }
        if (!freshness.fresh() || !run.getInputSnapshotHash().equals(freshness.currentHash())) {
            persistence.transition(run.getId(), DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
            throw new ServiceException("源业务数据已变化，须重新分析后执行");
        }
    }

    private DecisionActionType purchaseType() { return DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT; }

    private boolean isPurchase(AiDecisionAction row) {
        return row != null && (purchaseType().name().equals(row.getActionType())
            || purchaseType().executorCode().equals(row.getExecutorCode()));
    }

    private void requireRecoveryDependencies() {
        if (dataPort == null || permissionService == null || finalizeService == null)
            throw new ServiceException("采购恢复缺少可信端口、实时权限或复盘依赖");
    }

    private DecisionActor requireBoundPurchaseActor(AiDecisionRun run, DecisionActor caller) {
        requireRecoveryDependencies();
        DecisionActor bound = persistence.trustedExecutionActor(run.getId(), caller.tenantId());
        if (bound == null || !Objects.equals(run.getTenantId(), caller.tenantId())
            || !Objects.equals(bound.tenantId(), caller.tenantId()) || !Objects.equals(bound.userId(), caller.userId())
            || bound.permissions() == null)
            throw new ServiceException("必须由同租户的原绑定执行人恢复采购");
        if (!hasLivePermission(bound, "ai:decision:execute") || !hasLivePermission(caller, purchaseType().permissionCode())
            || !hasLivePermission(bound, purchaseType().permissionCode()))
            throw new InitialExecutionCheckUnavailableException();
        return bound;
    }

    /** 先独立核验已成功引用，再恢复所有在途采购，最后只执行依赖已满足的未开始动作。 */
    private void resumeApproved(Long runId, DecisionActor actor, Set<Long> alreadyVerified) {
        requireRecoveryDependencies();
        AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
        if (!"EXECUTING".equals(run.getStatus())) throw new ServiceException("只有未终态的执行中运行可续跑");
        List<AiDecisionAction> rows = persistence.listActions(runId);
        if (rows == null || rows.stream().anyMatch(Objects::isNull))
            throw new ServiceException("执行动作列表不可证明");
        List<AiDecisionAction> approved = rows.stream().filter(a -> "APPROVED".equals(a.getApprovalStatus())).toList();
        if (approved.isEmpty()) throw new ServiceException("运行没有已批准的动作");
        for (AiDecisionAction row : approved) {
            DecisionPurchaseContractGuard.requireExecutable(run, row);
            validateAction(row, actor);
        }
        if (approved.stream().anyMatch(this::isPurchase)) actor = requireBoundPurchaseActor(run, actor);
        List<AiDecisionAction> ordered = order(approved);
        Set<Long> verified = new HashSet<>();
        for (AiDecisionAction row : ordered) {
            if ("SUCCEEDED".equals(row.getExecutionStatus())) {
                if (!alreadyVerified.contains(row.getId())) verifyStoredResult(row, actor);
                verified.add(row.getId());
            }
        }
        for (AiDecisionAction row : ordered) {
            if ("EXECUTING".equals(row.getExecutionStatus()) || "FAILED".equals(row.getExecutionStatus())) {
                if (!isPurchase(row)) throw new ServiceException("非采购在途动作尚未确认，禁止猜测完成或自动重建");
                try {
                    var receipt = dataPort.recoverPurchaseAction(proposal(row, purchaseType()), actor);
                    if (receipt == null) throw uncertainPurchase();
                    saveRecovered(row, verifyPurchaseResult(row, receipt, actor), actor);
                } catch (RuntimeException pending) { throw new PurchaseExecutionPendingException(); }
                verified.add(row.getId());
            } else if (!"SUCCEEDED".equals(row.getExecutionStatus()) && !"NOT_STARTED".equals(row.getExecutionStatus())) {
                throw new ServiceException("动作存在未决状态，运行保持执行中");
            }
        }
        for (AiDecisionAction row : ordered) {
            if (!"NOT_STARTED".equals(row.getExecutionStatus())) continue;
            if (!verified.containsAll(dependencies(row))) throw new ServiceException("动作依赖尚未成功，运行保持执行中");
            ExecutionOutcome outcome = executeOne(row, actor, false);
            if (outcome == ExecutionOutcome.COMPENSATED || outcome == ExecutionOutcome.COMPENSATION_FAILED) {
                // 继续原非采购补偿审计，只跳过尚未开始动作，不触碰已提交采购或其他历史结果。
                for (AiDecisionAction later : ordered.subList(ordered.indexOf(row) + 1, ordered.size()))
                    if ("NOT_STARTED".equals(later.getExecutionStatus()))
                        persistence.transitionAction(later.getId(), "APPROVED", "NOT_STARTED", "SKIPPED");
                boolean allCompensated = outcome == ExecutionOutcome.COMPENSATED && verified.isEmpty();
                persistence.transition(runId, DecisionRunStatus.ROLLING_BACK,
                    allCompensated ? DecisionRunStatus.ROLLED_BACK : DecisionRunStatus.FAILED);
                finalizeService.finalizeRun(runId, actor);
                return;
            }
            if (outcome != ExecutionOutcome.SUCCESS) {
                if (isPurchase(row)) throw new PurchaseExecutionPendingException();
                throw new ServiceException("续跑动作尚未实际成功，不得收敛运行");
            }
            verified.add(row.getId());
        }
        // 重读 master 版本；最终原子方法再次检查全部已批准动作，不能仅按本地成功集合写终态。
        AiDecisionRun current = persistence.requireRun(runId, actor.tenantId());
        if (current.getVersion() == null) throw new ServiceException("运行缺少可复核版本");
        if (Integer.valueOf(2).equals(current.getDecisionContractVersion())) {
            boolean finished;
            try { finished = persistence.finishRecoveredExecution(runId, current.getVersion()); }
            catch (RuntimeException concurrent) {
                if (ordered.stream().anyMatch(this::isPurchase)) throw new PurchaseExecutionPendingException();
                throw concurrent;
            }
            if (finished) finalizeService.finalizeRun(runId, actor);
        } else if (ordered.stream().noneMatch(this::isPurchase)) {
            // 旧非采购运行不补写采购版本，沿用原 master 版本 CAS 迁移与复盘。
            persistence.transition(runId, DecisionRunStatus.EXECUTING, DecisionRunStatus.SUCCEEDED);
            finalizeService.finalizeRun(runId, actor);
        }
    }

    private ServiceException uncertainPurchase() {
        return new ServiceException("未发现已提交结果，执行状态尚不能确定；须使用原动作恢复");
    }

    /** 新建前先查永久提交；不确定执行或已提交但未核验的结果永不转 FAILED，也不自动补偿。 */
    private ExecutionOutcome executePurchase(AiDecisionAction row, DecisionActor caller,
                                              boolean retryFailed, boolean confirmedMiss) {
        AiDecisionRun run = persistence.requireRun(row.getRunId(), caller.tenantId());
        DecisionPurchaseContractGuard.requireExecutable(run, row);
        DecisionActor actor = requireBoundPurchaseActor(run, caller);
        validateAction(row, actor);
        var action = proposal(row, purchaseType());
        var executor = executors.get(purchaseType());
        if (!confirmedMiss) {
            var committed = dataPort.recoverPurchaseAction(action, actor);
            if (committed != null) {
                var verified = verifyPurchaseResult(row, committed, actor);
                persistence.transitionAction(row.getId(), "APPROVED", retryFailed ? "FAILED" : "NOT_STARTED", "EXECUTING");
                saveRecovered(persistence.requireAction(row.getId(), actor.tenantId()), verified, actor);
                return ExecutionOutcome.SUCCESS;
            }
        }
        var snapshot = persistence.requireSnapshot(row.getRunId(), actor.tenantId());
        if (snapshot == null) throw new ServiceException("缺少可信冻结业务快照");
        dataPort.requireFreshPurchaseContract(action, snapshot, DecisionFrozenHorizon.requireHorizonDays(run), actor);
        executor.precheck(action, actor);
        persistence.transitionAction(row.getId(), "APPROVED", retryFailed ? "FAILED" : "NOT_STARTED", "EXECUTING");
        DecisionActionExecutionResult performed;
        try { performed = executor.execute(action, actor); }
        catch (RuntimeException unknown) {
            recoverAfterUncertainWrite(row, actor);
            return ExecutionOutcome.SUCCESS;
        }
        if (performed != null && performed.success()) {
            DecisionActionExecutionResult verified;
            try { verified = verifyPurchaseResult(row, performed, actor); }
            catch (RuntimeException unverified) {
                recoverAfterUncertainWrite(row, actor);
                return ExecutionOutcome.SUCCESS;
            }
            // ERP 已真实提交；master 或 SSE 失败不得被捕获为业务执行失败、释放额度或补偿原单。
            persistence.saveActionResult(row.getId(), verified, 1);
            publishPurchaseStatus(row, actor);
            return ExecutionOutcome.SUCCESS;
        }
        var committed = dataPort.recoverPurchaseAction(action, actor);
        if (committed != null) {
            saveRecovered(persistence.requireAction(row.getId(), actor.tenantId()), verifyPurchaseResult(row, committed, actor), actor);
            return ExecutionOutcome.SUCCESS;
        }
        if (performed == null || performed.resultBizId() != null || performed.retryable()) throw uncertainPurchase();
        // 只有执行器明确失败且永久记录确定 MISS 才保存失败；运行留 EXECUTING，既有 retry 使用原键。
        persistence.saveActionResult(row.getId(), performed, 1);
        return ExecutionOutcome.FAILED;
    }

    private void recoverAfterUncertainWrite(AiDecisionAction row, DecisionActor actor) {
        var committed = dataPort.recoverPurchaseAction(proposal(row, purchaseType()), actor);
        if (committed == null) throw uncertainPurchase();
        var verified = verifyPurchaseResult(row, committed, actor);
        saveRecovered(persistence.requireAction(row.getId(), actor.tenantId()), verified, actor);
    }

    private DecisionActionExecutionResult verifyPurchaseResult(AiDecisionAction row,
                                                               DecisionActionExecutionResult receipt, DecisionActor actor) {
        validateAction(row, actor);
        requirePurchaseDraft(receipt);
        var verified = executors.get(purchaseType()).verify(proposal(row, purchaseType()), receipt, actor);
        requirePurchaseDraft(verified);
        if (!sameStableResult(receipt, verified)) throw new ServiceException("采购独立核验返回了不同的原单引用或冻结结果");
        requireExistingReference(row, verified);
        return verified;
    }

    private void requirePurchaseDraft(DecisionActionExecutionResult result) {
        if (result == null || !result.success() || result.retryable() || result.errorCode() != null || result.errorMessage() != null
            || !"PU_PURCHASE_REQUISITION".equals(result.resultBizType()) || result.resultBizId() == null || result.resultBizId() <= 0
            || result.resultBizCode() == null || result.resultBizCode().isBlank() || result.result() == null
            || !"DRAFT".equals(result.result().get("status")) || !"v0".equals(result.result().get("versionMarker"))
            || !"DRAFT".equals(result.result().get("afterState")))
            throw new ServiceException("采购提交不能证明为原稿 DRAFT/version0；保留原状态与历史引用，禁止重建或补偿");
    }

    private void requireExistingReference(AiDecisionAction row, DecisionActionExecutionResult result) {
        if (row.getResultBizType() != null && !Objects.equals(row.getResultBizType(), result.resultBizType())
            || row.getResultBizId() != null && !Objects.equals(row.getResultBizId(), result.resultBizId())
            || row.getResultBizCode() != null && !Objects.equals(row.getResultBizCode(), result.resultBizCode()))
            throw new ServiceException("采购历史业务引用与原永久提交冲突，禁止覆写");
    }

    private void saveRecovered(AiDecisionAction row, DecisionActionExecutionResult verified, DecisionActor actor) {
        if (row.getVersion() == null || row.getVersion() < 0) throw new ServiceException("采购动作缺少可复核版本");
        try { persistence.saveRecoveredPurchaseResult(row.getId(), row.getVersion(), verified); }
        catch (RuntimeException conflict) {
            AiDecisionAction current = persistence.requireAction(row.getId(), actor.tenantId());
            DecisionPurchaseContractGuard.requireExecutable(persistence.requireRun(row.getRunId(), actor.tenantId()), current);
            if (!"SUCCEEDED".equals(current.getExecutionStatus())
                || !sameStableResult(verified, verifyStoredResult(current, actor))) throw conflict;
            // 另一执行者已写入同一真实结果，只核验，不覆盖其版本、重试次数或重放观察字段。
        }
        publishPurchaseStatus(row, actor);
    }

    /** 核验既有成功引用，不允许一次未知读取把已经提交的采购运行关闭。 */
    private DecisionActionExecutionResult verifyStoredResult(AiDecisionAction row, DecisionActor actor) {
        try { return verifyStoredResultValue(row, actor); }
        catch (RuntimeException unavailable) {
            if (isSucceededV2Purchase(row)) throw new PurchaseExecutionPendingException();
            throw unavailable;
        }
    }

    private boolean isSucceededV2Purchase(AiDecisionAction row) {
        return row != null && isPurchase(row) && Integer.valueOf(2).equals(row.getPurchasePayloadVersion())
            && "SUCCEEDED".equals(row.getExecutionStatus());
    }

    private DecisionActionExecutionResult verifyStoredResultValue(AiDecisionAction row, DecisionActor actor) {
        DecisionActionExecutionResult stored;
        try { stored = mapper.readValue(row.getResultJson(), DecisionActionExecutionResult.class); }
        catch (Exception invalid) { throw new ServiceException("成功动作缺少可独立核验的结果信封"); }
        if (isPurchase(row)) {
            if (stored == null || row.getResultBizId() == null || row.getResultBizType() == null || row.getResultBizCode() == null)
                throw new ServiceException("成功采购动作缺少持久化业务引用");
            requireExistingReference(row, stored);
            return verifyPurchaseResult(row, stored, actor);
        }
        if (stored == null || !stored.success()) throw new ServiceException("成功动作结果信封无效");
        var verified = executors.get(DecisionActionType.valueOf(row.getActionType()))
            .verify(proposal(row, DecisionActionType.valueOf(row.getActionType())), stored, actor);
        if (verified == null || !verified.success() || !sameStableResult(stored, verified))
            throw new ServiceException("已成功动作的原业务结果无法独立核验");
        return verified;
    }

    private boolean sameStableResult(DecisionActionExecutionResult left, DecisionActionExecutionResult right) {
        if (left == null || right == null) return false;
        return Objects.equals(left.resultBizType(), right.resultBizType()) && Objects.equals(left.resultBizId(), right.resultBizId())
            && Objects.equals(left.resultBizCode(), right.resultBizCode()) && stableDetails(left.result()).equals(stableDetails(right.result()));
    }

    private Map<String, Object> stableDetails(Map<String, Object> details) {
        Map<String, Object> stable = new HashMap<>();
        if (details != null) stable.putAll(details);
        stable.remove("replayed"); stable.remove("beforeState");
        return stable;
    }

    private void publishPurchaseStatus(AiDecisionAction row, DecisionActor actor) {
        if (events == null) return;
        try { events.sendActionStatus(actor.userId(), row.getRunId(), row.getId(), "SUCCEEDED"); }
        catch (RuntimeException unavailable) { /* SSE 只是通知，不否认已提交并回写的采购事实。 */ }
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
        if (permissionService == null) return true; /** 仅供隔离单元测试使用的构造器。 */
        try {
            Set<String> current = TenantHelper.dynamic(actor.tenantId(),
                () -> permissionService.getMenuPermission(actor.userId()));
            return current != null && (current.contains(code) || current.contains("*:*:*"));
        } catch (RuntimeException ignored) { return false; }
    }
}
