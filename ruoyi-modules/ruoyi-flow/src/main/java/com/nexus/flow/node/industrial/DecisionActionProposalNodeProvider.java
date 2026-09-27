package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionActionProposalService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.decision.DecisionPolicyGuardService;
import com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 最终比较更新前，持久化精确通过护栏的叙述、数值证据及安全动作草稿。
 */
@Component
public class DecisionActionProposalNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    private final DecisionActionProposalService proposals;
    private final DecisionPolicyGuardService guard;
    private final ObjectProvider<DeliveryRiskDecisionOrchestrator> orchestrator;
    private final AiDecisionSseMessageHelper events;
    public DecisionActionProposalNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence,
                                              DecisionActionProposalService proposals,
                                              DecisionPolicyGuardService guard,
                                              ObjectProvider<DeliveryRiskDecisionOrchestrator> orchestrator,
                                              AiDecisionSseMessageHelper events) {
        super(support); this.persistence = persistence; this.proposals = proposals;
        this.guard = guard; this.orchestrator = orchestrator; this.events = events;
    }
    @Override public String getNodeType() { return "industrial.actionProposal"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long id = support.requireRunId(ctx);
        var result = support.read(ctx.getState().get("nodes.guard.output"), DecisionResult.class);
        if (!java.util.Objects.equals(id, result.runId())) throw new ServiceException("决策结果运行ID不匹配");
        var snapshot = persistence.requireSnapshot(id, actor.tenantId());
        var workIds = snapshot.workOrders() == null ? List.<String>of() : snapshot.workOrders().stream()
            .map(work -> String.valueOf(work.id())).toList();
        var actionCauses = new java.util.HashSet<>(result.rootCauseCodes());
        if (actionCauses.contains("DELIVERY_DELAY")) actionCauses.add("SCHEDULE_DELAY");
        var shortage = support.read(ctx.getState().get("nodes.shortage.output"), ShortageSimulationResult.class);
        if (actionCauses.contains("MATERIAL_SHORTAGE") != shortage.hasRisk())
            throw new ServiceException("Verified shortage disagrees with scored risk");
        ProposalScope scope = verifiedProposalScope(snapshot, shortage);
        if (scope.hasConfirmedLateArrival()) actionCauses.add("PURCHASE_DELAY");
        var workshops = snapshot.workOrders() == null ? List.<Long>of() : snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::workshopId).filter(Objects::nonNull).distinct().toList();
        Long workshopId = workshops.size() == 1 ? workshops.get(0) : null;
        var planned = proposals.propose(new DecisionActionProposalService.ProposalInput(id, actor.tenantId(),
            result.businessRef(), result.riskLevel(), Set.copyOf(actionCauses),
            result.recommendedScenarioCode(), scope.hasConfirmedLateArrival(), scope.materialLines(), null,
            workIds, snapshot.snapshotAt().toLocalDate(), persistence.requireHorizonDays(
                persistence.requireRun(id, actor.tenantId())), workshopId));
        var narrative = support.readNarrative(support.write(result.narrative()));
        var prepared = new DecisionResult(result.runId(), result.businessRef(), result.riskScore(), result.riskLevel(),
            result.confidenceLevel(), result.rootCauseCodes(), result.baselineKpis(), result.scenarios(),
            result.recommendedScenarioCode(), result.narrative(), result.evidence(), planned, result.warnings());
        if (!guard.validate(prepared, narrative, planned).passed())
            throw new ServiceException("拟执行动作未通过决策策略校验");
        persistence.replaceEvidence(id, prepared.evidence(), actor);
        var stored = persistence.saveActions(id, planned, actor);
        var withIds = java.util.stream.IntStream.range(0, planned.size()).mapToObj(i -> {
            DecisionActionProposal a = planned.get(i);
            return new DecisionActionProposal(stored.get(i).getId(), a.actionType(), a.title(), a.displayReason(),
                a.target(), a.payload(), a.riskLevel(), a.approvalRequired(), a.permissionCode(),
                a.idempotencyKey(), a.executorCode(), a.sequence(), a.dependencyIds());
        }).toList();
        var completed = new DecisionResult(prepared.runId(), prepared.businessRef(), prepared.riskScore(),
            prepared.riskLevel(), prepared.confidenceLevel(), prepared.rootCauseCodes(), prepared.baselineKpis(),
            prepared.scenarios(), prepared.recommendedScenarioCode(), prepared.narrative(),
            prepared.evidence(), withIds, prepared.warnings());
        orchestrator.getObject().completeAnalysis(id, completed, String.valueOf(result.narrative().get("summaryMode")), actor);
        events.sendReady(actor.userId(), id, result.riskLevel().name(), result.recommendedScenarioCode(), stored.size());
        return completed;
    }

    /**
     * 采购草稿或催交仅能来自限定范围的 ERP 缺料及真实延期采购行。
     */
    private ProposalScope verifiedProposalScope(DeliveryContextSnapshot snapshot, ShortageSimulationResult shortage) {
        if (!shortage.hasRisk()) return new ProposalScope(false, List.of());
        if (snapshot.requirements() == null || snapshot.workOrders() == null
            || shortage.items() == null || shortage.items().isEmpty())
            throw new ServiceException("Verified shortage is missing scoped ERP requirements");
        List<Map<String, Object>> lines = new ArrayList<>();
        Set<Long> seenMaterials = new java.util.HashSet<>();
        boolean confirmedLate = false;
        for (ShortageSimulationResult.Item item : shortage.items()) {
            if (item == null || item.materialId() == null || item.materialId() <= 0
                || item.maxGap() == null || item.maxGap().signum() <= 0 || item.firstShortageDate() == null
                || !seenMaterials.add(item.materialId()))
                throw new ServiceException("Verified shortage item is incomplete or duplicated");
            boolean scoped = snapshot.requirements().stream().anyMatch(requirement -> requirement != null
                && item.materialId().equals(requirement.materialId())
                && requirement.workOrderId() != null
                && item.affectedWorkOrderIds() != null
                && item.affectedWorkOrderIds().contains(requirement.workOrderId())
                && snapshot.workOrders().stream().anyMatch(work -> work != null
                    && Objects.equals(work.id(), requirement.workOrderId())));
            if (!scoped) throw new ServiceException("Shortage item is not rooted in scoped ERP requirements");
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialId", String.valueOf(item.materialId()));
            line.put("gap", item.maxGap().stripTrailingZeros().toPlainString());
            line.put("requiredDate", item.firstShortageDate().toString());
            var delayed = snapshot.arrivals() == null ? java.util.Optional.<DeliveryContextSnapshot.ArrivalFact>empty()
                : snapshot.arrivals().stream().filter(arrival -> arrival != null
                    && item.materialId().equals(arrival.materialId()) && arrival.purchaseLineId() != null
                    && arrival.purchaseLineId() > 0 && arrival.expectedDate() != null
                    && arrival.expectedDate().isAfter(item.firstShortageDate())
                    && arrival.remainingQty() != null && arrival.remainingQty().compareTo(item.maxGap()) >= 0
                    && item.onOrderQty() != null && item.onOrderQty().compareTo(item.maxGap()) >= 0)
                .min(Comparator.comparing(DeliveryContextSnapshot.ArrivalFact::expectedDate)
                    .thenComparing(DeliveryContextSnapshot.ArrivalFact::purchaseLineId));
            if (delayed.isPresent()) {
                confirmedLate = true;
                line.put("purchaseLineId", String.valueOf(delayed.orElseThrow().purchaseLineId()));
            }
            lines.add(Map.copyOf(line));
        }
        return new ProposalScope(confirmedLate, List.copyOf(lines));
    }

    private record ProposalScope(boolean hasConfirmedLateArrival, List<Map<String, Object>> materialLines) { }
}
