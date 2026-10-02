package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** ERP 的唯一工业决策 Port 入口；受控身份与租户边界不能由可编辑流程输入替代。 */
@Component
@RequiredArgsConstructor
@DS("erp")
public class ErpIndustrialDecisionDataAdapter implements IndustrialDecisionDataPort {

    private static final String RUN_PERMISSION = "ai:decision:run";
    private static final String ADMIN_PERMISSION = "*:*:*";

    private final DeliveryContextAssembler contextAssembler;
    private final ErpDecisionShortageSimulation shortageSimulation;
    private final ErpDecisionApsSimulation apsSimulation;
    private final ErpDecisionFreshnessChecker freshnessChecker;
    private final ErpDecisionActionExecutor actionExecutor;
    private final ErpCertifiedPurchaseDecisionAdapter certifiedPurchaseAdapter;

    @Override
    public boolean canAccess(DecisionBusinessRef ref, DecisionActor actor) {
        if (!validActor(actor) || !validRef(ref) || !canRead(ref, actor) || !TenantHelper.isEnable()) {
            return false;
        }
        return inTenant(actor, () -> contextAssembler.exists(ref, actor));
    }

    @Override
    public boolean canReadDecision(DecisionBusinessRef ref, DecisionActor actor) {
        if (!validActor(actor) || !validRef(ref) || !TenantHelper.isEnable()
            || !hasPermission(actor.permissions(), "ai:decision:query") || !businessRead(ref, actor)) return false;
        return inTenant(actor, () -> contextAssembler.exists(ref, actor));
    }

    @Override
    public DeliveryContextSnapshot loadDeliveryContext(DecisionBusinessRef ref, int horizonDays, DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireAccess(ref, actor);
            return contextAssembler.assemble(ref, horizonDays, actor);
        });
    }

    @Override
    public ShortageSimulationResult simulateShortage(DeliveryContextSnapshot snapshot, int horizonDays,
                                                     Map<String, Object> overrides, DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireFreshSnapshot(snapshot, actor);
            return shortageSimulation.simulate(snapshot, horizonDays, overrides);
        });
    }

    @Override
    public ApsScenarioCompareResult simulateSchedules(DeliveryContextSnapshot snapshot,
                                                      List<String> scenarioCodes, int horizonDays,
                                                      DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireFreshSnapshot(snapshot, actor);
            return apsSimulation.compare(snapshot, scenarioCodes, horizonDays);
        });
    }

    @Override
    public DecisionFreshnessResult checkFreshness(DecisionBusinessRef ref, String expectedHash, DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireAccess(ref, actor);
            return freshnessChecker.check(ref, expectedHash, actor);
        });
    }

    @Override
    public DecisionActionExecutionResult executeBusinessAction(DecisionActionProposal action, DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireActionAccess(action, actor);
            // 采购必须直达无外层事务的专属 writer；排程继续保留既有 executor。
            return action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
                ? certifiedPurchaseAdapter.execute(action, actor) : actionExecutor.execute(action, actor);
        });
    }

    @Override
    public DecisionActionExecutionResult verifyBusinessAction(DecisionActionProposal action,
                                                               DecisionActionExecutionResult result,
                                                               DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireActionAccess(action, actor);
            return action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
                ? certifiedPurchaseAdapter.verify(action, result, actor) : actionExecutor.verify(action, result, actor);
        });
    }

    @Override
    public DecisionActionExecutionResult compensateBusinessAction(DecisionActionProposal action,
                                                                   DecisionActionExecutionResult result,
                                                                   DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor, () -> {
            requireActionAccess(action, actor);
            return action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
                ? certifiedPurchaseAdapter.compensate(action, result, actor) : actionExecutor.compensate(action, result, actor);
        });
    }

    private void requireFreshSnapshot(DeliveryContextSnapshot snapshot, DecisionActor actor) {
        if (snapshot == null || snapshot.rootBusinessRef() == null
            || snapshot.snapshotHash() == null || snapshot.snapshotHash().isBlank()) {
            throw new ServiceException("决策快照缺少可复核的业务对象或关键指纹");
        }
        DecisionBusinessRef ref = snapshot.rootBusinessRef();
        requireAccess(ref, actor);
        DecisionFreshnessResult result = freshnessChecker.check(ref, snapshot.snapshotHash(), actor);
        if (result == null || !result.fresh() || !snapshot.snapshotHash().equals(result.currentHash())) {
            throw new ServiceException("业务关键数据已变化，请重新运行决策分析");
        }
    }

    private void requireActionAccess(DecisionActionProposal action, DecisionActor actor) {
        if (action == null || action.actionType() == null || action.target() == null) {
            throw new ServiceException("决策动作或目标不能为空");
        }
        DecisionActionType type = action.actionType();
        if (type != DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            && type != DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT) {
            throw new ServiceException("不支持的 ERP 决策业务动作");
        }
        if (!Objects.equals(type.permissionCode(), action.permissionCode())
            || !Objects.equals(type.executorCode(), action.executorCode())
            || type.approvalRequired() != action.approvalRequired()
            || !hasPermission(actor.permissions(), type.permissionCode())) {
            throw new ServiceException("决策动作元数据或业务权限无效");
        }
        requireAccess(action.target(), actor);
    }

    private void requireAccess(DecisionBusinessRef ref, DecisionActor actor) {
        if (!validRef(ref) || !canRead(ref, actor) || !contextAssembler.exists(ref, actor)) {
            throw new ServiceException("无权访问或无法找到决策业务对象");
        }
    }

    private boolean canRead(DecisionBusinessRef ref, DecisionActor actor) {
        return (hasPermission(actor.permissions(), RUN_PERMISSION)
            || hasPermission(actor.permissions(), "ai:decision:execute")) && businessRead(ref, actor);
    }

    private boolean businessRead(DecisionBusinessRef ref, DecisionActor actor) {
        String permission = switch (ref.type()) {
            case SALES_ORDER -> "so:orderheader:query";
            case WORK_ORDER -> "prd:work:query";
            case SCHEDULE_PLAN -> "prd:schedule:query";
            case SHORTAGE_HORIZON -> "prd:shortage:query";
        };
        return hasPermission(actor.permissions(), permission);
    }

    private static boolean hasPermission(Set<String> permissions, String required) {
        return permissions.stream().filter(Objects::nonNull).map(String::trim)
            .anyMatch(p -> ADMIN_PERMISSION.equals(p) || required.equals(p));
    }

    private static boolean validRef(DecisionBusinessRef ref) {
        if (ref == null || ref.type() == null) return false;
        return ref.type() == DecisionBusinessType.SHORTAGE_HORIZON
            ? ref.id() == null : ref.id() != null && ref.id() > 0;
    }

    private static boolean validActor(DecisionActor actor) {
        return actor != null && actor.tenantId() != null && !actor.tenantId().isBlank()
            && actor.userId() != null && actor.userId() > 0 && actor.permissions() != null;
    }

    private static void requireActor(DecisionActor actor) {
        if (!validActor(actor)) throw new ServiceException("决策执行人租户、身份或权限无效");
    }

    private static <T> T inTenant(DecisionActor actor, Supplier<T> operation) {
        if (!TenantHelper.isEnable()) throw new ServiceException("工业决策必须启用租户隔离");
        return TenantHelper.dynamic(actor.tenantId(), operation);
    }
}
