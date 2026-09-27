package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;

/**
 * 人工授权护栏：显式动作分区、版本检查及最新 ERP 快照。
 */
@Service
@Slf4j
public class DecisionApprovalService {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort dataPort;
    private final DecisionWorkflowPort workflow;
    private final AiDecisionSseMessageHelper events;
    private final TransactionTemplate transaction;
    private final PermissionService permissionService;

    @Autowired
    public DecisionApprovalService(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                                   DecisionWorkflowPort workflow, AiDecisionSseMessageHelper events,
                                   PlatformTransactionManager manager, PermissionService permissionService) {
        this(persistence, dataPort, workflow, events, new TransactionTemplate(manager), permissionService);
    }
    public DecisionApprovalService(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                                   DecisionWorkflowPort workflow, AiDecisionSseMessageHelper events,
                                   TransactionTemplate transaction, PermissionService permissionService) {
        this.persistence = persistence; this.dataPort = dataPort; this.workflow = workflow;
        this.events = events; this.transaction = transaction; this.permissionService = permissionService;
    }
    public DecisionApprovalService(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                                   DecisionWorkflowPort workflow, AiDecisionSseMessageHelper events,
                                   TransactionTemplate transaction) {
        this(persistence, dataPort, workflow, events, transaction, null);
    }
    public DecisionApprovalService(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                                   DecisionWorkflowPort workflow, AiDecisionSseMessageHelper events) {
        this(persistence, dataPort, workflow, events, (TransactionTemplate) null);
    }
    public DecisionApprovalService(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                                   DecisionWorkflowPort workflow) {
        this(persistence, dataPort, workflow, null, (TransactionTemplate) null);
    }

    public void approve(Long runId, DecisionApprovalRequest request, DecisionActor actor) {
        checkActor(actor);
        requirePermission(actor, "ai:decision:execute");
        TenantHelper.dynamic(actor.tenantId(), () -> approveInTenant(runId, request, actor));
    }

    private void approveInTenant(Long id, DecisionApprovalRequest request, DecisionActor actor) {
        AiDecisionRun run = persistence.requireRun(id, actor.tenantId());
        if (request == null || request.getRunVersion() == null || !request.getRunVersion().equals(run.getVersion())
            || !DecisionRunStatus.WAITING_APPROVAL.name().equals(run.getStatus())
            || !Objects.equals(request.getExpectedSnapshotHash(), run.getInputSnapshotHash())
            || request.getApprovedActionIds() == null || request.getRejectedActionIds() == null)
            throw new ServiceException("审批版本、决策状态或快照不匹配");
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.valueOf(run.getBizType()),
            run.getBizId(), run.getBizCode());
        if (!dataPort.canAccess(ref, actor)) throw new ServiceException("业务访问权限已失效");
        var freshness = dataPort.checkFreshness(ref, run.getInputSnapshotHash(), actor);
        if (freshness == null || !freshness.fresh()
            || !run.getInputSnapshotHash().equals(freshness.currentHash())) {
            persistence.transition(id, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
            throw new ServiceException("源业务数据已变化，须重新分析后审批");
        }
        List<AiDecisionAction> actions = persistence.listActions(id);
        Set<Long> approved = new HashSet<>(request.getApprovedActionIds());
        Set<Long> rejected = new HashSet<>(request.getRejectedActionIds());
        if (approved.size() != request.getApprovedActionIds().size()
            || rejected.size() != request.getRejectedActionIds().size() || approved.remove(null)
            || rejected.remove(null) || approved.stream().anyMatch(rejected::contains)
            || approved.isEmpty()) throw new ServiceException("必须明确选择至少一项不重复的已批准动作");
        Set<Long> all = new HashSet<>(approved); all.addAll(rejected);
        if (all.size() != actions.size() || actions.stream().anyMatch(action ->
            !all.contains(action.getId()) || !Objects.equals(id, action.getRunId())
            || !Set.of("PROPOSED", "PENDING_APPROVAL").contains(action.getApprovalStatus())
            || !"NOT_STARTED".equals(action.getExecutionStatus())
            || approved.contains(action.getId()) && !actor.permissions().contains(action.getPermissionCode())
                && !actor.permissions().contains("*:*:*")))
            throw new ServiceException("审批动作归属、权限或选择范围无效");
        for (AiDecisionAction action : actions) {
            if (approved.contains(action.getId())) requireTrustedAction(action);
        }
        atomic(() -> {
            persistence.bindExecutionActor(id, actor);
            for (AiDecisionAction action : actions) {
                persistence.setActionApproval(action.getId(), action.getApprovalStatus(),
                    approved.contains(action.getId()) ? "APPROVED" : "REJECTED", actor, request.getComment());
            }
            persistence.transition(id, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.APPROVED);
        }); // 异步执行线程读取运行前，必须先提交审批事务。
        DecisionWorkflowStartCommand command = new DecisionWorkflowStartCommand(id,
            "system_delivery_risk_execution", actor.tenantId(), actor.userId(), actor.userName(),
            Map.of("runId", String.valueOf(id)));
        Long flowId = null;
        try {
            flowId = workflow.startExecution(command);
            if (flowId == null || flowId <= 0) throw new ServiceException("执行 Flow 未返回有效实例");
            persistence.attachExecutionFlow(id, flowId);
        } catch (RuntimeException failed) {
            boolean stopped = false;
            if (flowId != null && flowId > 0) {
                try { stopped = workflow.stop(flowId, actor); }
                catch (RuntimeException stopError) {
                    log.warn("Execution Flow stop not confirmed: runId={}, flowId={}", id, flowId, stopError);
                }
                if (!stopped) {
                    try { persistence.recordUnlinkedExecutionFlow(id, flowId); }
                    catch (RuntimeException auditError) {
                        log.error("Unable to persist unlinked execution Flow: runId={}, flowId={}", id, flowId, auditError);
                    }
                }
            }
            try { persistence.transition(id, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE); }
            catch (RuntimeException concurrent) {
                log.warn("Execution Flow failed after approval state advanced: runId={}, flowId={}", id, flowId, concurrent);
            }
            if (events != null) {
                try { events.sendError(actor.userId(), id, "FLOW_START_FAILED", "执行流程启动或关联失败"); }
                catch (RuntimeException notifyError) { log.warn("Failed to publish execution failure: runId={}", id, notifyError); }
            }
            throw new ServiceException("已批准但执行流程启动失败，禁止自动继续");
        }
        if (events != null) {
            try { events.sendApprovalStatus(actor.userId(), id, "APPROVED"); }
            catch (RuntimeException notifyError) { log.warn("Failed to publish execution approval: runId={}", id, notifyError); }
        }
    }

    private void requireTrustedAction(AiDecisionAction action) {
        DecisionActionType type;
        try { type = DecisionActionType.valueOf(action.getActionType()); }
        catch (RuntimeException invalid) { throw new ServiceException("审批动作类型未受支持"); }
        if (!Objects.equals(type.executorCode(), action.getExecutorCode())
            || !Objects.equals(type.permissionCode(), action.getPermissionCode())
            || !("Y".equals(action.getApprovalRequired())
                || !type.approvalRequired() && "N".equals(action.getApprovalRequired())))
            throw new ServiceException("审批动作缺少可信的执行器、权限或审批配置");
    }

    public void reject(Long runId, String reason, DecisionActor actor) {
        checkActor(actor);
        if (reason == null || reason.trim().length() < 2 || reason.length() > 500)
            throw new ServiceException("拒绝理由须为 2 到 500 字符");
        TenantHelper.dynamic(actor.tenantId(), () -> {
            AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
            if (!DecisionRunStatus.WAITING_APPROVAL.name().equals(run.getStatus()))
                throw new ServiceException("决策当前不可拒绝");
            atomic(() -> {
                for (AiDecisionAction action : persistence.listActions(runId))
                    persistence.setActionApproval(action.getId(), action.getApprovalStatus(), "REJECTED", actor, reason);
                persistence.transition(runId, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.REJECTED);
            });
            if (events != null) events.sendApprovalStatus(actor.userId(), runId, "REJECTED");
        });
    }

    private void atomic(Runnable body) {
        if (transaction == null) body.run(); // 仅供隔离单元测试使用的构造器。
        else transaction.executeWithoutResult(ignored -> body.run());
    }

    private void checkActor(DecisionActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || actor.tenantId() == null
            || actor.permissions() == null) throw new ServiceException("没有决策审批权限");
        requirePermission(actor, "ai:decision:approve");
    }

    private void requirePermission(DecisionActor actor, String code) {
        if (!actor.permissions().contains(code) && !actor.permissions().contains("*:*:*"))
            throw new ServiceException("决策操作权限不足");
        if (permissionService == null) return; // 仅供隔离单元测试使用的构造器。
        try {
            Set<String> live = TenantHelper.dynamic(actor.tenantId(),
                () -> permissionService.getMenuPermission(actor.userId()));
            if (live == null || !live.contains(code) && !live.contains("*:*:*"))
                throw new ServiceException("当前决策操作权限已撤销");
        } catch (RuntimeException invalid) { throw new ServiceException("无法确认当前决策操作权限"); }
    }
}
