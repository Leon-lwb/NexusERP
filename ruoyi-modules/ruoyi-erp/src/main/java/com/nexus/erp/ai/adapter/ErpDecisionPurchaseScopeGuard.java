package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec.SourceChangedException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** 加入已拥有的ERP RR，冻结与最终落账均须独立复核根归属；未知事实失败关闭。 */
@Component
@DS("erp")
public class ErpDecisionPurchaseScopeGuard {
    private static final Set<String> WORK_STATES=Set.of("CREATED","APPROVING","APPROVED","RELEASED","IN_PROGRESS","COMPLETED","CLOSED");
    private static final Set<String> HEAD_STATES=Set.of("DRAFT","CONFIRMED","PARTIAL_ISSUED","ISSUED","CLOSED");
    private final ErpDecisionPurchaseScopeMapper mapper;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    public ErpDecisionPurchaseScopeGuard(ErpDecisionPurchaseScopeMapper mapper,
        PuRequirementErpTransactionQualification qualification,PuRequirementMaterialLockService locks) {
        this.mapper=Objects.requireNonNull(mapper);this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Long selectVendorLocked(String tenantId,Long materialId) {
        return readSafely(()->{requireBoundary(tenantId,materialId);return selectVendor(tenantId,materialId);});
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void requireLocked(String tenantId,DecisionBusinessRef target,Long materialId,Long vendorId,
        Set<Long> detailIds,LocalDate windowEnd) {
        readSafely(()->{
        require(target!=null&&target.type()==DecisionBusinessType.WORK_ORDER&&positive(target.id()),"仅支持明确工单根的采购");
        require(positive(vendorId),"供应商身份未知");
        requireSources(tenantId,target.id(),materialId,detailIds,windowEnd);
        if(!vendorId.equals(selectVendor(tenantId,materialId)))throw changed();
            return null;
        });
    }

    /** writer的根ID来自持久化动作，不能从来源摘要反解或取客户端默认值。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireWorkOrderSourcesLocked(String tenantId,Long rootWorkOrderId,Long materialId,Long vendorId,
        Set<Long> detailIds,LocalDate windowEnd) {
        readSafely(()->{
        require(positive(rootWorkOrderId),"完整冻结合同缺少工单根");
        require(positive(vendorId),"供应商身份未知");
        requireSources(tenantId,rootWorkOrderId,materialId,detailIds,windowEnd);
        if(!vendorId.equals(selectVendor(tenantId,materialId)))throw changed();
            return null;
        });
    }

    /** 冻结先锁根与需求再选择供应商，使与审批、writer一致而不反向锁共享供应商。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireRootSourcesLocked(String tenantId,DecisionBusinessRef target,Long materialId,
        Set<Long> detailIds,LocalDate windowEnd) {
        readSafely(()->{
        require(target!=null&&target.type()==DecisionBusinessType.WORK_ORDER&&positive(target.id()),"冻结仅支持明确工单根");
        requireSources(tenantId,target.id(),materialId,detailIds,windowEnd);
            return null;
        });
    }

    private void requireBoundary(String tenantId,Long materialId) {
        require(TenantHelper.isEnable()&&tenantId!=null&&tenantId.matches("[0-9]{6,20}")
            &&tenantId.equals(TenantHelper.getTenantId())&&positive(materialId),"租户或物料身份不可核验");
        qualification.requireSourceChainTransaction();
        // 先锁既有物料guard；查询和复核都不得补建出生或竞争守卫。
        locks.lockExisting(tenantId,List.of(materialId));
    }

    private void requireSources(String tenantId,Long rootId,Long materialId,Set<Long> requested,LocalDate windowEnd) {
        require(requested!=null&&!requested.isEmpty()&&requested.size()<=1000
            &&requested.stream().allMatch(ErpDecisionPurchaseScopeGuard::positive)&&windowEnd!=null,"逐来源采购范围缺失");
        var detailIds=requested.stream().sorted().toList();
        requireBoundary(tenantId,materialId);
        var pin=mapper.pinScopeDefinition();
        require(pin!=null&&pin.isEmpty()&&"InnoDB".equalsIgnoreCase(mapper.scopeTableEngine()),"工单表定义或事务引擎不可核验");
        var locator=mapper.findRootWorkOrder(tenantId,rootId);
        require(locator!=null,"根工单定位来源无法读取");
        workIdentity(locator,tenantId);
        require(rootId.equals(locator.woId())&&positive(locator.planId()),"根工单或明确计划身份未知");
        var plan=mapper.lockPlanWorkOrders(tenantId,locator.planId());
        require(plan!=null&&!plan.isEmpty(),"完整计划工单来源无法读取");
        Map<Long,WorkSource> byId=new TreeMap<>();
        for(var work:plan) {
            workIdentity(work,tenantId);
            require(byId.putIfAbsent(work.woId(),work)==null,"工单来源身份重复");
            if(!locator.planId().equals(work.planId()))throw changed();
        }
        var root=byId.get(rootId);
        require(root!=null,"定位后的根工单来源无法读取");
        require(root.sourceType()!=null&&Set.of("MATERIAL_REQUIREMENT","PLAN").contains(root.sourceType())&&positive(root.sourceId()),
            "根工单明确来源类型或来源ID未知");
        if(!Objects.equals(locator.sourceType(),root.sourceType())||!Objects.equals(locator.sourceId(),root.sourceId())
            ||!Objects.equals(locator.delFlag(),root.delFlag()))throw changed();
        if(!"0".equals(root.delFlag())||Set.of("COMPLETED","CLOSED").contains(root.woStatus()))throw changed();
        var sources=mapper.lockRequirementSources(tenantId,materialId,detailIds,windowEnd);
        require(sources!=null&&sources.size()==detailIds.size(),"实际需求来源读取不完整");
        Set<Long> seen=new HashSet<>();Set<Long> headIds=new TreeSet<>();
        for(var source:sources) {
            require(source!=null&&positive(source.detailId())&&seen.add(source.detailId())&&positive(source.requirementId())
                &&source.unit()!=null&&source.unit().matches("[1-9][0-9]{0,18}")&&source.requiredDate()!=null
                &&source.detailStatus()!=null&&Set.of(0L,1L).contains(source.detailStatus())&&positive(source.planId())
                &&Set.of("0","1").contains(Objects.toString(source.detailDelFlag(),""))
                &&Set.of("0","1").contains(Objects.toString(source.headDelFlag(),""))
                &&source.headStatus()!=null&&HEAD_STATES.contains(source.headStatus()),"需求身份、原始单位或状态未知");
            require(tenantId.equals(source.tenantId()),"需求来源租户不一致，禁止跨租户认证");
            if(!materialId.equals(source.materialId())||!requested.contains(source.detailId())
                ||!"0".equals(source.detailDelFlag())||!"0".equals(source.headDelFlag())||!Long.valueOf(1).equals(source.detailStatus())
                ||Set.of("ISSUED","CLOSED").contains(source.headStatus())||source.requiredDate().isAfter(windowEnd)
                ||!locator.planId().equals(source.planId()))throw changed();
            headIds.add(source.requirementId());
        }
        require(seen.equals(requested),"需求来源集合不完整");
        var direct=mapper.lockDirectWorkOrders(tenantId,List.copyOf(headIds));
        require(direct!=null,"完整直接MRP归属无法读取");
        Set<Long> directIds=new HashSet<>();Map<Long,List<WorkSource>> byHead=new HashMap<>();
        for(var work:direct) {
            workIdentity(work,tenantId);
            require(directIds.add(work.woId())&&"MATERIAL_REQUIREMENT".equals(work.sourceType())&&headIds.contains(work.sourceId()),
                "直接MRP工单来源不完整或重复");
            byHead.computeIfAbsent(work.sourceId(),ignored->new ArrayList<>()).add(work);
        }
        for(Long headId:headIds) {
            var candidates=byHead.getOrDefault(headId,List.of());
            if(!candidates.isEmpty()) {
                if(candidates.size()!=1||!rootId.equals(candidates.get(0).woId())||!root.equals(candidates.get(0)))throw changed();
            } else if(plan.size()!=1||!"PLAN".equals(root.sourceType())||!locator.planId().equals(root.sourceId()))throw changed();
        }
        qualification.requireSourceChainTransaction();
    }

    private Long selectVendor(String tenantId,Long materialId) {
        var choices=mapper.lockVendorChoices(tenantId,materialId);
        require(choices!=null,"供应关系来源无法读取");
        List<VendorChoice> active=new ArrayList<>();Set<Long> relations=new HashSet<>(),vendors=new HashSet<>();
        for(var choice:choices) {
            require(choice!=null&&positive(choice.relationId())&&relations.add(choice.relationId())&&tenantId.equals(choice.tenantId())
                &&materialId.equals(choice.materialId())&&positive(choice.vendorId())&&choice.relationStatus()!=null
                &&Set.of(0,1).contains(choice.relationStatus())&&Set.of("0","1").contains(Objects.toString(choice.relationDelFlag(),"")),
                "供应关系身份或状态未知");
            if(choice.relationStatus()!=1||!"0".equals(choice.relationDelFlag()))continue;
            require(choice.vendorStatus()!=null&&Set.of("ACTIVE","FROZEN").contains(choice.vendorStatus())
                &&Set.of("0","1").contains(Objects.toString(choice.vendorDelFlag(),"")),"活跃供应关系缺少有效供应商状态来源");
            if(!"ACTIVE".equals(choice.vendorStatus())||!"0".equals(choice.vendorDelFlag()))continue;
            require(choice.isDefault()!=null&&Set.of(0,1).contains(choice.isDefault())&&vendors.add(choice.vendorId()),
                "供应商默认标记未知或活跃关系重复");
            active.add(choice);
        }
        require(!active.isEmpty(),"没有合法活跃供应关系");
        if(active.size()==1)return active.get(0).vendorId();
        var defaults=active.stream().filter(c->Integer.valueOf(1).equals(c.isDefault())).toList();
        require(defaults.size()==1,"供应商来源不唯一");
        return defaults.get(0).vendorId();
    }

    private static void workIdentity(WorkSource work,String tenantId) {
        require(work!=null&&positive(work.woId())&&tenantId.equals(work.tenantId())&&work.woStatus()!=null
            &&WORK_STATES.contains(work.woStatus())&&Set.of("0","1").contains(Objects.toString(work.delFlag(),"")),"工单来源身份或状态未知");
    }
    /** 未知数据库/映射故障不能被解释为来源变化，也不透出底层连接或SQL内容。 */
    private static <T> T readSafely(Supplier<T> read) {
        try {return read.get();}
        catch(SourceChangedException|ServiceException classified) {throw classified;}
        catch(RuntimeException unavailable) {throw new ServiceException("采购根来源读取无法核验，请稍后重试或人工核对");}
    }
    private static boolean positive(Long value) {return value!=null&&value>0;}
    private static void require(boolean valid,String message) {if(!valid)throw new ServiceException("采购根来源拒绝："+message);}
    private static SourceChangedException changed() {return new SourceChangedException();}
}
