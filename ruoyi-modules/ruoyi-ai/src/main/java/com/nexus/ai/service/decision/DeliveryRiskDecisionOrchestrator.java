package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 可信入口：分布式锁内重查权限、原子迁移为运行中、验证流程图后再启动。
 */
@Service
public class DeliveryRiskDecisionOrchestrator {
    private static final String ANALYSIS_FLOW = "system_delivery_risk_analysis";
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 120;

    private final AiProperties properties;
    private final IndustrialDecisionDataPort dataPort;
    private final DecisionWorkflowPort workflowPort;
    private final DecisionPersistenceService persistence;
    private final RedissonClient redisson;
    private final AiDecisionSseMessageHelper events;

    @Autowired
    public DeliveryRiskDecisionOrchestrator(AiProperties properties, IndustrialDecisionDataPort dataPort,
                                            @Nullable DecisionWorkflowPort workflowPort,
                                            DecisionPersistenceService persistence, RedissonClient redisson,
                                            AiDecisionSseMessageHelper events) {
        this.properties = properties;
        this.dataPort = dataPort;
        this.workflowPort = workflowPort;
        this.persistence = persistence;
        this.redisson = redisson;
        this.events = events;
    }

    public DeliveryRiskDecisionOrchestrator(AiProperties properties, IndustrialDecisionDataPort dataPort,
                                            DecisionWorkflowPort workflowPort,
                                            DecisionPersistenceService persistence, RedissonClient redisson) {
        this(properties, dataPort, workflowPort, persistence, redisson, null);
    }

    /**
     * 仅在独立重读持久化租户事实及 ERP 接口数据后定稿。
     */
    public void completeAnalysis(Long runId, DecisionResult result, String summaryMode, DecisionActor actor) {
        if (!TenantHelper.isEnable() || actor == null || actor.tenantId() == null || actor.tenantId().isBlank()
            || actor.userId() == null || actor.userId() <= 0 || actor.permissions() == null
            || (!actor.permissions().contains("ai:decision:run")
                && !actor.permissions().contains("*:*:*")) || runId == null || runId <= 0)
            throw new ServiceException("决策分析完成身份无效");
        TenantHelper.dynamic(actor.tenantId(), () -> {
            AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
            DeliveryContextSnapshot stored = persistence.requireSnapshot(runId, actor.tenantId());
            if (!DecisionRunStatus.RUNNING.name().equals(run.getStatus())
                || !stored.snapshotHash().equals(run.getInputSnapshotHash())
                || stored.rootBusinessRef() == null || result == null || result.businessRef() == null
                || !stored.rootBusinessRef().equals(result.businessRef()))
                throw new ServiceException("决策业务对象、状态或快照不匹配");
            DecisionBusinessRef ref = stored.rootBusinessRef();
            if (!dataPort.canAccess(ref, actor)) throw new ServiceException("决策业务访问权限已变化");
            DecisionFreshnessResult freshness = dataPort.checkFreshness(ref, stored.snapshotHash(), actor);
            if (freshness == null || !freshness.fresh()
                || !stored.snapshotHash().equals(freshness.currentHash()))
                throw new ServiceException("业务关键数据已变化，请重新分析");
            int horizonDays = persistence.requireHorizonDays(run);
            DeliveryContextSnapshot reread = dataPort.loadDeliveryContext(ref, horizonDays, actor);
            if (reread == null || !ref.equals(reread.rootBusinessRef())
                || !stored.snapshotHash().equals(reread.snapshotHash()))
                throw new ServiceException("ERP 重读事实与已持久化快照不一致");
            new DecisionSourceFactVerifier().assertProvenance(result, stored, reread, horizonDays);
            List<DecisionActionProposal> purchases = result.actions() == null ? List.of() : result.actions().stream()
                .filter(action -> action != null && action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT).toList();
            if (!purchases.isEmpty()) {
                if (!Integer.valueOf(2).equals(run.getDecisionContractVersion()))
                    throw new ServiceException("采购运行合同版本缺失或无效，须重新分析");
                DecisionActor analysisActor = persistence.trustedActor(runId, actor.tenantId());
                if (analysisActor == null || !actor.tenantId().equals(analysisActor.tenantId())
                    || !actor.userId().equals(analysisActor.userId()))
                    throw new ServiceException("采购定稿必须使用持久化分析身份");
                for (DecisionActionProposal action : purchases) {
                    var contract = DecisionPurchaseContractCodec.parse(action.payload(), runId);
                    if (!ref.equals(action.target()) || !DecisionPurchaseContractCodec.idempotencyKey(
                        run.getTenantId(), runId, ref, contract).equals(action.idempotencyKey()))
                        throw new ServiceException("采购动作业务根或永久键不匹配");
                    try { dataPort.requireFreshPurchaseContract(action, stored, horizonDays, analysisActor); }
                    catch (RuntimeException unavailable) {
                        throw new ServiceException("采购冻结来源无法通过定稿复核，须重新分析");
                    }
                }
            }
            persistence.completeAnalysis(runId, result, summaryMode);
        });
    }

    public DecisionRunAcceptedVo start(DeliveryRiskRunRequest request, DecisionActor actor) {
        validate(request, actor);
        return TenantHelper.dynamic(actor.tenantId(), () -> startInTenant(request, actor));
    }

    private DecisionRunAcceptedVo startInTenant(DeliveryRiskRunRequest request, DecisionActor actor) {
        DecisionBusinessRef ref = new DecisionBusinessRef(request.getBizType(), request.getBizId(), null);
        if (!dataPort.canAccess(ref, actor)) throw new ServiceException("无权访问决策业务对象");
        AiDecisionRun active = persistence.findActiveRun(ref, actor.tenantId());
        if (active != null && !Boolean.TRUE.equals(request.getForceRestart())) return accepted(active);
        // Task 12 提供工业流程适配器；在此之前新运行一律拒绝。
        if (workflowPort == null) throw new ServiceException("工业决策分析流程尚未安装");
        String key = "decision:delivery_risk:" + actor.tenantId() + ":" + ref.type()
            + ":" + (ref.id() == null ? "HORIZON" : ref.id());
        RLock lock;
        boolean acquired;
        try {
            lock = redisson.getLock(key);
            if (lock == null) throw new ServiceException("无法取得工业决策分布式锁");
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("等待工业决策锁被中断");
        } catch (RuntimeException ex) {
            throw new ServiceException("工业决策分布式锁不可用");
        }
        if (!acquired) throw new ServiceException("同一业务对象正在启动决策，请稍后重试");
        try {
            // 此前无锁查询仅作优化，此处才是权威检查。
            if (!dataPort.canAccess(ref, actor)) throw new ServiceException("决策业务访问权限已变化");
            active = persistence.findActiveRun(ref, actor.tenantId());
            if (active != null && !Boolean.TRUE.equals(request.getForceRestart())) return accepted(active);
            if (active != null) supersede(active, actor);
            if (persistence.countActiveRuns(actor.tenantId()) >= properties.getDecision().getMaxConcurrentRuns())
                throw new ServiceException("活跃决策数量已达运行上限");
            AiDecisionRun created = persistence.createRun(request, actor);
            Long runId = created.getId();
            if (runId == null || runId <= 0) throw new ServiceException("决策运行创建后未返回ID");
            if (active != null) persistence.linkReplacement(active.getId(), runId);
            DecisionWorkflowStartCommand command = new DecisionWorkflowStartCommand(runId, ANALYSIS_FLOW,
                actor.tenantId(), actor.userId(), actor.userName(), Map.of("runId", String.valueOf(runId)));
            Long flowId = null;
            try {
                workflowPort.validateAnalysis(command); // 模板缺失、为空或不可信时拒绝启动。
                persistence.transition(runId, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
                if (events != null) events.sendStarted(actor.userId(), runId);
                flowId = workflowPort.startAnalysis(command);
                if (flowId == null || flowId <= 0) throw new ServiceException("分析流程未返回有效实例ID");
                persistence.attachAnalysisFlow(runId, flowId);
            } catch (RuntimeException failed) {
                // 停止未绑定实例；无法确认停止时在审计失败中保留实例标识。
                boolean stopped = false;
                if (flowId != null && flowId > 0) {
                    try { stopped = workflowPort.stop(flowId, actor); }
                    catch (RuntimeException ignored) { /* 不公开模型或 Redis 诊断信息。 */ }
                }
                try {
                    AiDecisionRun latest = flowId == null ? null : persistence.requireRun(runId, actor.tenantId());
                    if (latest != null && DecisionRunStatus.WAITING_APPROVAL.name().equals(latest.getStatus())) {
                        // 快速完成但未绑定的分析不得进入审批。
                        persistence.transition(runId, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
                    } else {
                        persistence.fail(runId, flowId != null && !stopped ? "ORPHAN_FLOW" : "FLOW_START_FAILED",
                            flowId != null && !stopped ? "未确认停止的分析 Flow 实例ID: " + flowId
                                : "工业分析流程启动或关联失败");
                    }
                } catch (RuntimeException ignored) { /* 其他工作线程可能已经将运行置为终态。 */ }
                if (events != null) events.sendError(actor.userId(), runId, "FLOW_START_FAILED", "工业分析流程启动失败");
                throw new ServiceException("工业分析流程启动失败");
            }
            AiDecisionRun newest = persistence.requireRun(runId, actor.tenantId());
            return newest == null ? new DecisionRunAcceptedVo(runId, DecisionRunStatus.RUNNING,
                DecisionPersistenceService.SCENE_CODE, LocalDateTime.now()) : accepted(newest);
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            } catch (RuntimeException ignored) {
                // 有界锁租约会到期，不得将已成功启动的运行误报为失败。
            }
        }
    }

    private void supersede(AiDecisionRun old, DecisionActor actor) {
        DecisionRunStatus status;
        try { status = DecisionRunStatus.valueOf(old.getStatus()); }
        catch (RuntimeException e) { throw new ServiceException("旧决策运行状态无效"); }
        if (status == DecisionRunStatus.WAITING_APPROVAL) {
            persistence.transition(old.getId(), DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
            persistence.transition(old.getId(), DecisionRunStatus.STALE, DecisionRunStatus.SUPERSEDED);
            return;
        }
        if (status == DecisionRunStatus.RUNNING) {
            if (old.getAnalysisFlowInstanceId() == null
                || !workflowPort.stop(old.getAnalysisFlowInstanceId(), actor))
                throw new ServiceException("旧分析流程仍在执行且无法安全停止");
            persistence.transition(old.getId(), DecisionRunStatus.RUNNING, DecisionRunStatus.CANCELLED);
            return;
        }
        if (status == DecisionRunStatus.CREATED) {
            persistence.transition(old.getId(), DecisionRunStatus.CREATED, DecisionRunStatus.CANCELLED);
            return;
        }
        throw new ServiceException("旧决策已进入审批或执行阶段，禁止强制重启");
    }

    private static DecisionRunAcceptedVo accepted(AiDecisionRun row) {
        DecisionRunStatus status;
        try { status = DecisionRunStatus.valueOf(row.getStatus()); }
        catch (RuntimeException invalid) { throw new ServiceException("决策运行状态无效"); }
        return new DecisionRunAcceptedVo(row.getId(), status, row.getSceneCode(),
            row.getCreateTime() == null ? LocalDateTime.now() :
                row.getCreateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    }

    private void validate(DeliveryRiskRunRequest request, DecisionActor actor) {
        if (!TenantHelper.isEnable() || actor == null || actor.tenantId() == null
            || !actor.tenantId().matches("[A-Za-z0-9_-]{1,20}") || actor.userId() == null
            || actor.userId() <= 0 || actor.userName() == null || actor.userName().isBlank()
            || actor.permissions() == null || !actor.permissions().contains("ai:decision:run")
                && !actor.permissions().contains("*:*:*")
            || request == null || request.getBizType() == null
            || !request.isBusinessScopeValid() || request.getHorizonDays() == null
            || !properties.getDecision().isEnabled()) throw new ServiceException("决策功能未启用或请求/身份无效");
        if (request.getBizType() == DecisionBusinessType.SHORTAGE_HORIZON && request.getBizId() != null
            || request.getBizType() != DecisionBusinessType.SHORTAGE_HORIZON
                && (request.getBizId() == null || request.getBizId() <= 0))
            throw new ServiceException("决策业务范围无效");
        var cfg = properties.getDecision();
        if (request.getHorizonDays() < 1 || request.getHorizonDays() > cfg.getMaxHorizonDays()
            || request.getScenarioCodes() == null || request.getScenarioCodes().isEmpty()
            || request.getScenarioCodes().size() > cfg.getMaxScenarios()
            || request.getScenarioCodes().stream().anyMatch(s -> s == null || s.isBlank())
            || Set.copyOf(request.getScenarioCodes()).size() != request.getScenarioCodes().size())
            throw new ServiceException("决策时段或候选方案数量无效");
    }
}
