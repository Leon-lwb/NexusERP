package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** ERP 的唯一工业决策 Port 入口；受控身份与租户边界不能由可编辑流程输入替代。 */
@Component
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
    private final ErpDecisionPurchaseContractService purchaseContracts;

    /** 保留旧Java装配路径；新冻结端口没有依赖时仍失败关闭。 */
    public ErpIndustrialDecisionDataAdapter(DeliveryContextAssembler contextAssembler,ErpDecisionShortageSimulation shortageSimulation,
        ErpDecisionApsSimulation apsSimulation,ErpDecisionFreshnessChecker freshnessChecker,ErpDecisionActionExecutor actionExecutor,
        ErpCertifiedPurchaseDecisionAdapter certifiedPurchaseAdapter) {
        this(contextAssembler,shortageSimulation,apsSimulation,freshnessChecker,actionExecutor,certifiedPurchaseAdapter,null);
    }

    @Autowired
    public ErpIndustrialDecisionDataAdapter(DeliveryContextAssembler contextAssembler,ErpDecisionShortageSimulation shortageSimulation,
        ErpDecisionApsSimulation apsSimulation,ErpDecisionFreshnessChecker freshnessChecker,ErpDecisionActionExecutor actionExecutor,
        ErpCertifiedPurchaseDecisionAdapter certifiedPurchaseAdapter,ErpDecisionPurchaseContractService purchaseContracts) {
        this.contextAssembler=contextAssembler;this.shortageSimulation=shortageSimulation;this.apsSimulation=apsSimulation;
        this.freshnessChecker=freshnessChecker;this.actionExecutor=actionExecutor;
        this.certifiedPurchaseAdapter=certifiedPurchaseAdapter;this.purchaseContracts=purchaseContracts;
    }

    @Override
    public List<DecisionPurchaseContractV2> freezePurchaseContracts(DeliveryContextSnapshot frozenSnapshot,int horizonDays,DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor,()->{
            requireFreshSnapshot(frozenSnapshot,actor);
            requireContracts();
            return purchaseContracts.freeze(frozenSnapshot,horizonDays,actor);
        });
    }

    @Override
    public void requireFreshPurchaseContract(DecisionActionProposal action,DeliveryContextSnapshot frozenSnapshot,int horizonDays,DecisionActor actor) {
        requireActor(actor);
        inTenant(actor,()->{
            requirePurchaseReadAccess(action,frozenSnapshot,actor);requireContracts();
            purchaseContracts.requireFresh(action,frozenSnapshot,horizonDays,actor);return null;
        });
    }

    @Override
    public DecisionActionExecutionResult recoverPurchaseAction(DecisionActionProposal action,DecisionActor actor) {
        requireActor(actor);
        return inTenant(actor,()->{
            if(!hasPermission(actor.permissions(),"ai:decision:execute")||action==null
                ||action.actionType()!=DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) {
                throw new ServiceException("采购恢复的实时执行权限或动作类型无效");
            }
            requirePurchaseRecoveryAccess(action,actor);
            // 已提交的恢复先于来源新鲜度；这里不仿真、不重新分析，也不创建业务单据。
            return certifiedPurchaseAdapter.recover(action,actor);
        });
    }

    private void requireContracts() {
        if(purchaseContracts==null)throw new ServiceException("服务端采购合同冻结依赖尚未装配");
    }

    private void requirePurchaseReadAccess(DecisionActionProposal action,DeliveryContextSnapshot snapshot,DecisionActor actor) {
        if(action==null||action.actionType()!=DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT||action.target()==null
            ||snapshot==null||snapshot.rootBusinessRef()==null||action.target().type()!=snapshot.rootBusinessRef().type()
            ||!Objects.equals(action.target().id(),snapshot.rootBusinessRef().id())||!validRef(action.target())
            ||!action.approvalRequired()||!DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.permissionCode().equals(action.permissionCode())
            ||!DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode().equals(action.executorCode())) {
            throw new ServiceException("采购复核动作与持久化快照根或固定元数据不一致");
        }
        // 审批只沿既有query/run/execute来源读取权复读，不虚授pu写权限或改装配器权限源。
        boolean phaseRight=hasPermission(actor.permissions(),"ai:decision:approve")||hasPermission(actor.permissions(),RUN_PERMISSION)
            ||hasPermission(actor.permissions(),"ai:decision:execute");
        boolean existingRead=canRead(action.target(),actor)
            ||hasPermission(actor.permissions(),"ai:decision:query")&&businessRead(action.target(),actor);
        if(!phaseRight||!existingRead||!contextAssembler.exists(action.target(),actor)) {
            throw new ServiceException("无权访问或无法找到采购复核业务对象");
        }
    }

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
            if (action != null && action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) {
                requirePurchaseRecoveryAccess(action, actor);
            } else {
                requireActionAccess(action, actor);
            }
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
        requireActionMetadata(action, actor);
        requireAccess(action.target(), actor);
    }

    /** 恢复及其结果核验只放开根后来终态，不放开租户对象可见性或原身份/实时权限。 */
    private void requirePurchaseRecoveryAccess(DecisionActionProposal action, DecisionActor actor) {
        requireActionMetadata(action, actor);
        if (action.actionType() != DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            || action.target().type() != DecisionBusinessType.WORK_ORDER || !validRef(action.target())
            || !hasPermission(actor.permissions(), "ai:decision:execute") || !canRead(action.target(), actor)
            || !contextAssembler.existsForPurchaseRecovery(action.target(), actor)) {
            throw new ServiceException("无权访问或无法找到采购恢复业务对象");
        }
    }

    private void requireActionMetadata(DecisionActionProposal action, DecisionActor actor) {
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
