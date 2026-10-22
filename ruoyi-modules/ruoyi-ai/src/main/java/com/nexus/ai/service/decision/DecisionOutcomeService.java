package com.nexus.ai.service.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 创建草稿仅表示预测；实际指标需要后续独立的 ERP 状态变化。
 */
@Service
@RequiredArgsConstructor
public class DecisionOutcomeService {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort port;
    private final ObjectMapper mapper;

    public DecisionOutcome measure(Long runId, DecisionActor actor) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null) throw new ServiceException("复盘身份无效");
        return TenantHelper.dynamic(
                actor.tenantId(),
                () -> {
                    var run = persistence.requireRun(runId, actor.tenantId());
                    if (!"SUCCEEDED".equals(run.getStatus())
                                    && !"PARTIAL_SUCCESS".equals(run.getStatus())
                                    && !"FAILED".equals(run.getStatus())
                                    && !"ROLLED_BACK".equals(run.getStatus())
                            || run.getResultJson() == null)
                        throw new ServiceException("决策尚未进入可评估的终态");
                    DecisionResult result;
                    try {
                        result = mapper.readValue(run.getResultJson(), DecisionResult.class);
                    } catch (Exception badResult) {
                        throw new ServiceException("历史决策结果无法解码");
                    }
                    if (!Objects.equals(result.runId(), runId)
                            || !port.canAccess(result.businessRef(), actor))
                        throw new ServiceException("无权访问该业务结果");
                    DecisionKpiSet projected =
                            result.scenarios() == null
                                    ? null
                                    : result.scenarios().stream()
                                            .filter(
                                                    s ->
                                                            s != null
                                                                    && Objects.equals(
                                                                            s.scenarioCode(),
                                                                            result
                                                                                    .recommendedScenarioCode()))
                                            .findFirst()
                                            .map(DecisionScenario::kpis)
                                            .orElse(null);
                    var actions = persistence.listActions(runId);
                    List<Long> effective =
                            actions.stream()
                                    .filter(a -> "SUCCEEDED".equals(a.getExecutionStatus()))
                                    .map(AiDecisionAction::getId)
                                    .toList();
                    List<Long> pending =
                            actions.stream()
                                    .filter(
                                            a ->
                                                    !"SUCCEEDED".equals(a.getExecutionStatus())
                                                            && !"REJECTED"
                                                                    .equals(a.getApprovalStatus()))
                                    .map(AiDecisionAction::getId)
                                    .toList();
                    DecisionKpiSet actual = null;
                    var freshness =
                            port.checkFreshness(
                                    result.businessRef(), run.getInputSnapshotHash(), actor);
                    if (freshness != null
                            && freshness.currentHash() != null
                            && !freshness.currentHash().equals(run.getInputSnapshotHash())) {
                        int horizon = persistence.requireHorizonDays(run);
                        var snapshot =
                                port.loadDeliveryContext(result.businessRef(), horizon, actor);
                        if (snapshot != null
                                && snapshot.snapshotHash() != null
                                && snapshot.snapshotHash().equals(freshness.currentHash())) {
                            var compared =
                                    port.simulateSchedules(
                                            snapshot, List.of("BASELINE_CURRENT"), horizon, actor);
                            if (compared != null && compared.scenarios() != null) {
                                actual =
                                        compared.scenarios().stream()
                                                .filter(
                                                        s ->
                                                                "BASELINE_CURRENT"
                                                                        .equals(s.scenarioCode()))
                                                .findFirst()
                                                .map(DecisionScenario::kpis)
                                                .orElse(null);
                            }
                        }
                    }
                    Map<String, Object> delta = new LinkedHashMap<>();
                    if (result.baselineKpis() != null && projected != null) {
                        delta.put(
                                "projectedDelayedWorkOrders",
                                projected.delayedWorkOrderCount()
                                        - result.baselineKpis().delayedWorkOrderCount());
                        delta.put(
                                "projectedTardinessDays",
                                projected.totalTardinessDays()
                                        - result.baselineKpis().totalTardinessDays());
                    }
                    return new DecisionOutcome(
                            result.baselineKpis(),
                            projected,
                            actual,
                            Map.copyOf(delta),
                            effective,
                            pending,
                            LocalDateTime.now(),
                            actual == null ? "PROJECTED_ONLY" : "MEASURED");
                });
    }
}
