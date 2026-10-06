package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 同步读取持久化分析身份及冻结窗口，采购意图只从真实工业端口取得。
 * 不开启跨库事务、不接受流程或模型签发数量、供应商、单位及核验旗号。
 */
@Service
@RequiredArgsConstructor
public class DecisionPurchaseProposalService {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort dataPort;
    private final DecisionActionProposalService proposals;

    /** 冻结入口只能使用持久化运行的业务根、快照、窗口和分析身份。 */
    public List<DecisionPurchaseContractV2> freezeForRun(Long runId, DecisionActor actor) {
        ProposalContext context = requireContext(runId);
        if (actor == null || !Objects.equals(context.actor().tenantId(), actor.tenantId())
            || !Objects.equals(context.actor().userId(), actor.userId()))
            throw new ServiceException("采购冻结必须使用持久化分析身份");
        return freeze(context);
    }

    public List<DecisionActionProposal> propose(Long runId, DecisionActionProposalService.ProposalInput input) {
        ProposalContext context = requireContext(runId);
        var run = context.run(); var snapshot = context.snapshot();
        if (input == null || !Objects.equals(runId, input.runId()) || !run.getTenantId().equals(input.tenantId())
            || !snapshot.rootBusinessRef().equals(input.target()))
            throw new ServiceException("采购提案业务根或运行身份与持久化范围不一致");
        if (snapshot.workOrders().stream().anyMatch(work -> work == null || work.id() == null || work.id() <= 0))
            throw new ServiceException("持久化快照中的排程工单范围无效");
        List<String> workIds = snapshot.workOrders().stream().map(work -> work.id().toString()).toList();
        List<Long> workshops = snapshot.workOrders().stream().map(DeliveryContextSnapshot.WorkOrderFact::workshopId)
            .filter(Objects::nonNull).distinct().toList();
        Long workshop = workshops.size() == 1 ? workshops.get(0) : null;
        var trusted = new DecisionActionProposalService.ProposalInput(runId, run.getTenantId(), snapshot.rootBusinessRef(),
            input.riskLevel(), input.rootCauses(), input.recommendedScenarioCode(), input.inTransitCovers(),
            input.materialLines(), input.managerUserId(), workIds, snapshot.snapshotAt().toLocalDate(), context.horizon(), workshop);
        List<DecisionPurchaseContractV2> contracts = input.rootCauses() != null
            && input.rootCauses().contains("MATERIAL_SHORTAGE")
            && snapshot.rootBusinessRef().type() == DecisionBusinessType.WORK_ORDER ? freeze(context) : List.of();
        return proposals.propose(trusted, contracts);
    }

    private ProposalContext requireContext(Long runId) {
        String tenant = TenantHelper.getDynamic();
        if (!TenantHelper.isEnable() || tenant == null || tenant.isBlank() || runId == null || runId <= 0)
            throw new ServiceException("采购提案缺少可信运行或租户上下文");
        AiDecisionRun run = persistence.requireRun(runId, tenant);
        DeliveryContextSnapshot snapshot = persistence.requireSnapshot(runId, tenant);
        if (!runId.equals(run.getId()) || !tenant.equals(run.getTenantId()) || !"RUNNING".equals(run.getStatus())
            || snapshot == null || snapshot.rootBusinessRef() == null || snapshot.rootBusinessRef().type() == null
            || snapshot.snapshotAt() == null || snapshot.snapshotHash() == null
            || !snapshot.snapshotHash().equals(run.getInputSnapshotHash())
            || !snapshot.rootBusinessRef().type().name().equals(run.getBizType())
            || !Objects.equals(snapshot.rootBusinessRef().id(), run.getBizId()))
            throw new ServiceException("采购提案业务根、状态或持久化快照不匹配");
        int horizon = persistence.requireHorizonDays(run);
        DecisionActor actor = persistence.trustedActor(runId, tenant);
        if (horizon < 1 || horizon > 90 || actor == null || !tenant.equals(actor.tenantId())
            || actor.userId() == null || actor.userId() <= 0 || actor.permissions() == null
            || !actor.permissions().contains("ai:decision:run") && !actor.permissions().contains("*:*:*"))
            throw new ServiceException("采购提案缺少可信分析身份或冻结时段");
        return new ProposalContext(run, snapshot, horizon, actor);
    }

    private List<DecisionPurchaseContractV2> freeze(ProposalContext context) {
        if (!Integer.valueOf(2).equals(context.run().getDecisionContractVersion()))
            throw new ServiceException("采购运行合同版本缺失或无效，须重新分析");
        if (context.snapshot().rootBusinessRef().type() != DecisionBusinessType.WORK_ORDER) return List.of();
        try {
            List<DecisionPurchaseContractV2> contracts = dataPort.freezePurchaseContracts(
                context.snapshot(), context.horizon(), context.actor());
            if (contracts == null) throw new ServiceException("冻结端口未返回明确采购额度");
            Map<Long, DeliveryContextSnapshot.RequirementFact> sources = new HashMap<>();
            for (var requirement : context.snapshot().requirements()) {
                if (requirement != null && requirement.detailId() != null
                    && sources.putIfAbsent(requirement.detailId(), requirement) != null)
                    throw new ServiceException("持久化需求来源重复，不能冻结采购");
            }
            for (DecisionPurchaseContractV2 contract : contracts) {
                DecisionPurchaseContractCodec.toPayload(context.run().getId(), contract);
                if (!context.snapshot().snapshotAt().toLocalDate().plusDays(context.horizon() - 1L)
                    .equals(contract.windowEnd())) throw new ServiceException("采购合同窗口不属于持久化分析时段");
                for (var line : contract.frozenLines()) {
                    var source = sources.get(line.detailId());
                    if (source == null || !Objects.equals(contract.materialId(), source.materialId())
                        || !Objects.equals(context.snapshot().rootBusinessRef().id(), source.workOrderId())
                        || !Objects.equals(line.baseUomId(), source.baseUomId())
                        || !Objects.equals(line.requiredDate(), source.requiredDate()))
                        throw new ServiceException("采购逐来源合同不属于持久化工单需求");
                }
            }
            return List.copyOf(contracts);
        } catch (ServiceException rejected) {
            throw new ServiceException(DecisionPersistenceService.safeMessage(rejected.getMessage()));
        } catch (RuntimeException unavailable) {
            throw new ServiceException("服务端采购合同无法冻结，禁止使用旧缺口替代");
        }
    }

    private record ProposalContext(AiDecisionRun run, DeliveryContextSnapshot snapshot, int horizon, DecisionActor actor) { }
}
