package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec.SourceChangedException;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 只从服务端已持久化快照选择候选，采购数量始终来自完整认证的真实正额度。
 * 每个物料占用独立短ERP RR；不调用登录preview，不生成编号、guard、出生或业务行。
 */
@Service
public class ErpDecisionPurchaseContractService {
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final ErpPurchaseSourceCertificationService certification;
    private final ErpDecisionPurchaseScopeGuard scopeGuard;
    private final ErpDecisionShortageSimulation shortage;

    public ErpDecisionPurchaseContractService(PuRequirementErpTransactionExecutor executor,
        PuRequirementErpTransactionQualification qualification,PuRequirementMaterialLockService locks,
        ErpPurchaseSourceCertificationService certification,ErpDecisionPurchaseScopeGuard scopeGuard,
        ErpDecisionShortageSimulation shortage) {
        this.executor=Objects.requireNonNull(executor);this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks);this.certification=Objects.requireNonNull(certification);
        this.scopeGuard=Objects.requireNonNull(scopeGuard);this.shortage=Objects.requireNonNull(shortage);
    }

    public List<DecisionPurchaseContractV2> freeze(DeliveryContextSnapshot snapshot,int horizonDays,DecisionActor actor) {
        LocalDate end=requireSnapshot(snapshot,horizonDays,actor);
        if(snapshot.rootBusinessRef().type()!=DecisionBusinessType.WORK_ORDER)return List.of();
        require(permission(actor,"ai:decision:run")||permission(actor,"ai:decision:execute"),"分析身份权限无效");
        var risk=shortage.simulate(snapshot,horizonDays,Map.of());
        require(risk!=null&&risk.items()!=null,"可信缺料物料选择不可核验");
        if(!risk.hasRisk())return List.of();
        Set<Long> materials=new TreeSet<>();
        for(var item:risk.items()) {
            require(item!=null&&positive(item.materialId())&&item.maxGap()!=null,"风险物料身份或风险状态未知");
            // gap仅选择风险物料，绝不进入冻结申请数量。
            if(item.maxGap().signum()>0)materials.add(item.materialId());
        }
        List<DecisionPurchaseContractV2> contracts=new ArrayList<>();
        for(Long materialId:materials) {
            var candidates=candidates(snapshot,materialId,end);
            require(!candidates.isEmpty(),"风险物料缺少本工单非零MRP来源");
            var frozen=executor.executeRepeatableRead(actor.tenantId(),()->{
                qualification.requireSourceChainTransaction();
                locks.lockExisting(actor.tenantId(),List.of(materialId));
                Set<Long> requested=Set.copyOf(candidates.keySet());
                scopeGuard.requireRootSourcesLocked(actor.tenantId(),snapshot.rootBusinessRef(),materialId,requested,end);
                Long vendorId=scopeGuard.selectVendorLocked(actor.tenantId(),materialId);
                scopeGuard.requireLocked(actor.tenantId(),snapshot.rootBusinessRef(),materialId,vendorId,requested,end);
                var first=previewLocked(actor.tenantId(),materialId,vendorId,end,requested);
                List<CertifiedLine> initial=requireScope(first,actor.tenantId(),materialId,vendorId,end,requested);
                require(first.snapshotDate()!=null&&first.snapshotDate().equals(snapshot.snapshotAt().toLocalDate()),
                    "冻结期间数据库业务日与原快照不一致");
                if(initial.isEmpty()) {qualification.requireSourceChainTransaction();return null;}
                for(var line:initial) {
                    var original=candidates.get(line.detailId());
                    if(!Objects.equals(original.baseUomId(),line.baseUomId())||!Objects.equals(original.requiredDate(),line.requiredDate())) {
                        throw new SourceChangedException();
                    }
                }
                Set<Long> positiveIds=new TreeSet<>();initial.forEach(line->positiveIds.add(line.detailId()));
                CertifiedPurchaseScope selected=first;
                if(!positiveIds.equals(requested)) {
                    // 不能先认证全集指纹再删零行；正子集须在同一RR重新完整认证。
                    selected=previewLocked(actor.tenantId(),materialId,vendorId,end,Set.copyOf(positiveIds));
                    var again=requireScope(selected,actor.tenantId(),materialId,vendorId,end,positiveIds);
                    require(first.snapshotDate().equals(selected.snapshotDate()),"正子集重新认证跨过原冻结业务日");
                    require(sameLines(initial,again),"确切正子集重新认证后逐行额度或单位改变");
                }
                var contract=toContract(selected);
                qualification.requireSourceChainTransaction();
                return contract;
            });
            if(frozen!=null)contracts.add(frozen);
        }
        return List.copyOf(contracts);
    }

    /** 审批/创建前只复读原物料、原正来源集合和原窗口，不受兄弟采购的普通全局hash影响。 */
    public void requireFresh(DecisionActionProposal action,DeliveryContextSnapshot snapshot,int horizonDays,DecisionActor actor) {
        LocalDate end=requireSnapshot(snapshot,horizonDays,actor);
        require(permission(actor,"ai:decision:approve")||permission(actor,"ai:decision:execute")||permission(actor,"ai:decision:run"),
            "采购复核身份权限无效");
        require(action!=null&&positive(action.actionId())&&action.actionType()==DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            &&action.target()!=null&&action.target().type()==DecisionBusinessType.WORK_ORDER
            &&snapshot.rootBusinessRef().type()==DecisionBusinessType.WORK_ORDER
            &&Objects.equals(action.target().id(),snapshot.rootBusinessRef().id())
            &&action.approvalRequired()&&DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode().equals(action.executorCode())
            &&DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.permissionCode().equals(action.permissionCode()),"采购动作与原工单根或固定元数据不一致");
        var contract=DecisionPurchaseContractCodec.parse(action.payload(),null);
        Long runId=Long.valueOf((String)action.payload().get("runId"));
        require(DecisionPurchaseContractCodec.idempotencyKey(actor.tenantId(),runId,action.target(),contract).equals(action.idempotencyKey()),
            "采购动作永久键与完整合同身份不一致");
        require(end.equals(contract.windowEnd()),"采购合同不能改变已持久化快照窗口");
        var candidates=candidates(snapshot,contract.materialId(),end);
        require(candidates.keySet().containsAll(contract.requirementDetailIds()),"冻结采购来源不属于原快照候选");
        for(var line:contract.frozenLines()) {
            var fact=candidates.get(line.detailId());
            require(fact!=null&&Objects.equals(fact.baseUomId(),line.baseUomId())&&Objects.equals(fact.requiredDate(),line.requiredDate()),
                "冻结采购单位或日期与原候选不一致");
        }
        executor.executeRepeatableRead(actor.tenantId(),()->{
            qualification.requireSourceChainTransaction();
            locks.lockExisting(actor.tenantId(),List.of(contract.materialId()));
            var requested=Set.copyOf(contract.requirementDetailIds());
            scopeGuard.requireLocked(actor.tenantId(),action.target(),contract.materialId(),contract.vendorId(),requested,end);
            var current=previewLocked(actor.tenantId(),contract.materialId(),contract.vendorId(),end,requested);
            var lines=requireScope(current,actor.tenantId(),contract.materialId(),contract.vendorId(),end,requested);
            // 只有取得完整认证结果后才比较；读取失败、未知单位/权限不会被捕获成STALE。
            if(!contract.expectedSourceFingerprint().equals(current.sourceFingerprint())||lines.size()!=requested.size())throw new SourceChangedException();
            var actual=toContract(current);
            if(!DecisionPurchaseContractCodec.contractMetadata(contract).equals(DecisionPurchaseContractCodec.contractMetadata(actual))
                ||!sameFrozenLines(contract.frozenLines(),actual.frozenLines()))throw new SourceChangedException();
            qualification.requireSourceChainTransaction();return null;
        });
    }

    /** 来源不可读取和明确已认证差异分开；不按错误文字推断STALE。 */
    private CertifiedPurchaseScope previewLocked(String tenantId,Long materialId,Long vendorId,LocalDate end,Set<Long> requested) {
        try {return certification.previewLocked(tenantId,materialId,vendorId,end,requested);}
        catch(SourceChangedException|ServiceException classified) {throw classified;}
        catch(RuntimeException unavailable) {throw new ServiceException("采购认证来源读取无法核验，请稍后重试或人工核对");}
    }

    private static LocalDate requireSnapshot(DeliveryContextSnapshot snapshot,int horizonDays,DecisionActor actor) {
        require(actor!=null&&TenantHelper.isEnable()&&actor.tenantId()!=null&&actor.tenantId().matches("[0-9]{6,20}")
            &&actor.tenantId().equals(TenantHelper.getTenantId())&&positive(actor.userId())&&actor.userName()!=null
            &&!actor.userName().isBlank()&&actor.userName().length()<=64&&actor.permissions()!=null,"采购冻结可信身份或租户未知");
        require(snapshot!=null&&snapshot.rootBusinessRef()!=null&&snapshot.rootBusinessRef().type()!=null
            &&snapshot.snapshotAt()!=null&&snapshot.snapshotHash()!=null&&!snapshot.snapshotHash().isBlank()
            &&horizonDays>=1&&horizonDays<=90,"采购冻结原快照或窗口未知");
        require(snapshot.rootBusinessRef().type()!=DecisionBusinessType.WORK_ORDER
            ||positive(snapshot.rootBusinessRef().id())&&permission(actor,"prd:work:query"),"采购根身份或业务读取权限无效");
        LocalDate start=snapshot.snapshotAt().toLocalDate();
        require(start.getYear()>=1000&&start.getYear()<=9999,"原快照业务日超出范围");
        LocalDate end=start.plusDays(horizonDays-1L);
        require(end.getYear()<=9999,"冻结业务窗口超出范围");return end;
    }

    private static Map<Long,RequirementFact> candidates(DeliveryContextSnapshot snapshot,Long materialId,LocalDate end) {
        require(snapshot.requirements()!=null,"原快照缺少MRP来源");
        Map<Long,RequirementFact> result=new TreeMap<>();
        for(var fact:snapshot.requirements()) {
            require(fact!=null,"原快照MRP行缺失");
            if(!materialId.equals(fact.materialId()))continue;
            require(fact.requiredQty()!=null&&fact.requiredQty().signum()>=0&&fact.requiredDate()!=null,"候选MRP数量或日期未知");
            if(fact.requiredQty().signum()==0||fact.requiredDate().isBefore(snapshot.snapshotAt().toLocalDate())||fact.requiredDate().isAfter(end))continue;
            require(positive(fact.detailId())&&positive(fact.baseUomId())&&Objects.equals(snapshot.rootBusinessRef().id(),fact.workOrderId())
                &&result.putIfAbsent(fact.detailId(),fact)==null,"候选明细、根单位或工单归属未知/重复");
        }
        require(result.size()<=1000,"单物料候选明细超限");return result;
    }

    private static List<CertifiedLine> requireScope(CertifiedPurchaseScope scope,String tenantId,Long materialId,Long vendorId,
        LocalDate end,Set<Long> requested) {
        require(scope!=null&&tenantId.equals(scope.tenantId())&&materialId.equals(scope.materialId())&&vendorId.equals(scope.vendorId())
            &&end.equals(scope.windowEnd())&&scope.catalog()!=null&&scope.lines()!=null&&scope.sourceFingerprint()!=null
            &&scope.sourceFingerprint().matches("[0-9a-f]{64}"),"完整认证来源、身份或指纹不可核验");
        Set<Long> seen=new HashSet<>();
        for(var line:scope.lines()) {
            require(line!=null&&positive(line.detailId())&&seen.add(line.detailId())&&requested.contains(line.detailId())
                &&positive(line.baseUomId())&&positive(line.orderUomId())&&decimal(line.coveredDemandBase())&&decimal(line.orderQuantity())
                &&line.requiredDate()!=null&&!line.requiredDate().isAfter(end),"认证行数量、单位或请求来源未知");
        }
        return scope.lines().stream().sorted(Comparator.comparing(CertifiedLine::detailId)).toList();
    }

    private static DecisionPurchaseContractV2 toContract(CertifiedPurchaseScope scope) {
        var catalog=scope.catalog();
        require(catalog.terms()!=null&&catalog.baseUnit()!=null&&catalog.orderUnit()!=null&&catalog.sourceVersions()!=null,
            "完整认证缺少真实条款、单位或目录版本");
        Long base=id(catalog.baseUnit().unitId()),order=id(catalog.orderUnit().unitId());
        require(base.equals(order)&&Objects.equals(catalog.baseUnit().conversionVersion(),catalog.orderUnit().conversionVersion()),
            "首包仅支持基本单位等于订购单位及相同换算版本");
        var lines=scope.lines().stream().sorted(Comparator.comparing(CertifiedLine::detailId)).map(line->{
            require(base.equals(line.baseUomId())&&order.equals(line.orderUomId())&&line.coveredDemandBase().compareTo(line.orderQuantity())==0,
                "逐来源覆盖量与订购量必须无超额且使用同一根单位");
            return new DecisionPurchaseContractV2.Line(line.detailId(),base,line.coveredDemandBase(),order,line.orderQuantity(),line.requiredDate());
        }).toList();
        var contract=new DecisionPurchaseContractV2(scope.materialId(),scope.vendorId(),scope.windowEnd(),
            catalog.sourceVersions().get("material:"+scope.materialId()),catalog.sourceVersions().get("vendor:"+scope.vendorId()),
            id(catalog.terms().termsId()),catalog.terms().termsVersion(),base,catalog.baseUnit().conversionVersion(),
            order,catalog.orderUnit().conversionVersion(),catalog.orderUnit().conversionVersion(),
            lines.stream().map(DecisionPurchaseContractV2.Line::detailId).toList(),lines,scope.sourceFingerprint());
        // 借唯一codec验证全部wire元数据；此合成runId只作纯内存字段验证，不进入任何业务键或持久化。
        DecisionPurchaseContractCodec.toPayload(1L,contract);
        return contract;
    }

    private static boolean sameLines(List<CertifiedLine> expected,List<CertifiedLine> actual) {
        if(expected.size()!=actual.size())return false;
        for(int i=0;i<expected.size();i++) {
            var a=expected.get(i);var b=actual.get(i);
            if(!Objects.equals(a.detailId(),b.detailId())||!Objects.equals(a.baseUomId(),b.baseUomId())||!Objects.equals(a.orderUomId(),b.orderUomId())
                ||!Objects.equals(a.requiredDate(),b.requiredDate())||a.coveredDemandBase().compareTo(b.coveredDemandBase())!=0
                ||a.orderQuantity().compareTo(b.orderQuantity())!=0)return false;
        }
        return true;
    }
    private static boolean sameFrozenLines(List<DecisionPurchaseContractV2.Line> expected,List<DecisionPurchaseContractV2.Line> actual) {
        return sameLines(expected.stream().map(l->new CertifiedLine(l.detailId(),l.requiredDate(),l.coveredDemandBase(),l.requestedOrderQty(),l.baseUomId(),l.orderUomId())).toList(),
            actual.stream().map(l->new CertifiedLine(l.detailId(),l.requiredDate(),l.coveredDemandBase(),l.requestedOrderQty(),l.baseUomId(),l.orderUomId())).toList());
    }
    private static Long id(String value) {
        require(value!=null&&value.matches("[1-9][0-9]{0,18}"),"实际目录必须提供正单位或条款ID");
        try{return Long.valueOf(value);}catch(NumberFormatException invalid){throw new ServiceException("采购目录标识超出Long范围");}
    }
    private static boolean positive(Long value) {return value!=null&&value>0;}
    private static boolean decimal(BigDecimal value) {return value!=null&&value.signum()>0&&value.stripTrailingZeros().scale()<=4
        &&(long)value.stripTrailingZeros().precision()-value.stripTrailingZeros().scale()<=14;}
    private static boolean permission(DecisionActor actor,String required) {return actor.permissions().contains("*:*:*")||actor.permissions().contains(required);}
    private static void require(boolean valid,String message) {if(!valid)throw new ServiceException("采购合同冻结拒绝："+message);}
}
