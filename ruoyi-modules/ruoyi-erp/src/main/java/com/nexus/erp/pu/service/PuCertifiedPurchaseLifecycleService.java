package com.nexus.erp.pu.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/**
 * 逐来源账本/订单计数/永久事件的原子服务，不接 HTTP、AI 或旧业务门禁。
 * public 自启专属 ERP RR，Locked 仅供同一 owned RR 的统一 writer/路由使用。
 * 审批/转单/关闭要求真实单据已由业务状态机变更；路由必须在同一回调先写真实单据
 * 再调用 Locked，不能先提交再落账。收货/退货根据真实库存流水自行更新订单计数，
 * 不可先走旧累加代码。只支持根基本单位、逐申请行一条 ledger、一条精确订单承接。
 * 不能证明的状态明确拒绝；重放返回持久 occupied 事件，而非伪造完整历史余额。
 */
@Service
@DS("erp")
public class PuCertifiedPurchaseLifecycleService {
    public enum Operation { APPROVE, TRANSFER_TO_ORDER, CLOSE_REQUISITION, CANCEL_REQUISITION,
        REJECT_REQUISITION, CLOSE_ORDER, CANCEL_ORDER, RECEIPT_POSTED, RETURN_POSTED, COMPENSATE_DRAFT }
    /** IDs/expected version only: no quantity, status, permissions or verified flag. */
    public record Command(String tenantId, Long actorId, Long materialId, String allocationKey,
        String requestKey, Integer expectedVersion, Operation operation, Long businessLineId) { }
    /** Internal MQ entry: actor and quantity are obtained from the persisted physical result. */
    public record ReceiptCommand(String tenantId, Long materialId, String allocationKey,
        String requestKey, Integer expectedVersion, Long receiveLineId) { }
    public record EventReceipt(Long eventId, Long allocationId, Operation operation, String fromStatus,
        String toStatus, BigDecimal occupiedBefore, BigDecimal occupiedAfter,
        Long requisitionLineId, Long orderLineId, Long actorId, boolean replayed) { }
    /** Full-document compensation: expected versions describe identities, never authoritative quantities. */
    public record CompensationCommand(String tenantId,Long actorId,Long materialId,String requestKey,
        Long requisitionId,Integer expectedDocumentVersion,Map<String,Integer> expectedAllocationVersions) {
        public CompensationCommand {
            if(expectedAllocationVersions!=null) expectedAllocationVersions=Collections.unmodifiableMap(new LinkedHashMap<>(expectedAllocationVersions));
        }
    }
    public record CompensationReceipt(Long requisitionId,String requestKey,List<EventReceipt> allocations,boolean replayed) {
        public CompensationReceipt {allocations=List.copyOf(allocations);}
    }
    public CompensationReceipt compensateDraft(CompensationCommand command) {
        validateCompensation(command);
        return executor.executeRepeatableRead(command.tenantId(),()->compensateDraftLocked(command));
    }
    /** Full-document soft compensation; no command, ledger or event is ever deleted. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CompensationReceipt compensateDraftLocked(CompensationCommand command) {
        validateCompensation(command); qualification.requireSourceChainTransaction();
        Map<Long,Integer> guard=io(()->locks.lock(command.tenantId(),List.of(command.materialId())));
        require(guard!=null&&guard.size()==1&&guard.get(command.materialId())!=null
            &&guard.get(command.materialId())>=0&&guard.get(command.materialId())<Integer.MAX_VALUE,"补偿guard版本未知");
        require("InnoDB".equalsIgnoreCase(io(mapper::commandTableEngine))&&io(mapper::pinCommandDefinition)!=null,"补偿永久command事务定义未核验");
        sourceGate.requireAllocationDefinitions();
        CommandSource source=io(()->mapper.lockCommand(command.tenantId(),command.requestKey()));
        require(source!=null&&command.tenantId().equals(source.tenantId())&&command.requestKey().equals(source.requestKey())
            &&command.requisitionId().equals(source.requisitionId())&&command.materialId().equals(source.materialId())
            &&command.actorId().equals(source.actorId())&&digest(source.requestHash())&&digest(source.sourceFingerprint())
            &&digest(source.resultFingerprint())&&ENTRIES.contains(Objects.toString(source.entryType(),"")),"补偿永久command归属未知");
        RequisitionHead head=io(()->mapper.lockRequisitionHead(command.tenantId(),command.requisitionId()));
        require(head!=null&&command.tenantId().equals(head.tenantId())&&command.requisitionId().equals(head.requisitionId())
            &&command.requestKey().equals(head.requestKey())&&source.entryType().equals(head.sourceType())
            &&Objects.equals(source.requisitionNo(),head.requisitionNo())&&head.version()!=null,"补偿真实草稿头未知");
        List<PuRequirementAllocationLedger> rows=io(()->mapper.lockCommandAllocations(command.tenantId(),command.requestKey()));
        List<RequisitionLine> lines=io(()->mapper.lockRequisitionLines(command.tenantId(),command.requisitionId()));
        require(rows!=null&&!rows.isEmpty()&&rows.size()<=1000&&rows.stream().allMatch(Objects::nonNull)
            &&lines!=null&&lines.size()==rows.size()&&lines.stream().allMatch(Objects::nonNull),"整单补偿来源不完整");
        Set<String> keys=new HashSet<>();Set<Long> lineIds=new HashSet<>(),sourceLineIds=new HashSet<>();
        for(PuRequirementAllocationLedger row:rows) require(command.tenantId().equals(row.getTenantId())
            &&command.materialId().equals(row.getMaterialId())&&command.requestKey().equals(row.getDecisionIdempotencyKey())
            &&text(row.getAllocationKey(),128)&&keys.add(row.getAllocationKey())&&positive(row.getRequisitionLineId())
            &&lineIds.add(row.getRequisitionLineId()),"补偿账本有重复/错归属");
        for(RequisitionLine line:lines) require(command.tenantId().equals(line.tenantId())&&command.requisitionId().equals(line.requisitionId())
            &&command.materialId().equals(line.materialId())&&positive(line.lineId())&&sourceLineIds.add(line.lineId()),"整单补偿申请行归属未知");
        require(keys.equals(command.expectedAllocationVersions().keySet())&&lineIds.equals(sourceLineIds),"整单补偿版本集合缺失/存在脱落账本");
        if("COMPENSATED".equals(source.commandStatus())) return replayCompensation(command,source,head,rows,lines);
        require("COMMITTED".equals(source.commandStatus())&&"DRAFT".equals(head.status())&&"0".equals(head.delFlag())
            &&head.version().equals(command.expectedDocumentVersion()),"仅未变更的真实草稿可补偿");
        List<Context> contexts=new ArrayList<>();List<PuRequirementAllocationLedger> changes=new ArrayList<>();
        List<PuRequirementAllocationEvent> appended=new ArrayList<>();List<EventReceipt> receipts=new ArrayList<>();
        for(PuRequirementAllocationLedger row:rows) {
            Context c=readContext(command.tenantId(),command.materialId(),row.getAllocationKey(),command.requestKey());
            require(command.expectedAllocationVersions().get(row.getAllocationKey()).equals(c.row().getVersion())
                &&c.guardVersion().equals(guard.get(command.materialId()))&&c.orderLine()==null&&c.row().getOrderLineId()==null,
                "草稿已变更或存在真实下游订单");
            PuRequirementAllocationLedger after;
            try {after=PuRequirementAllocationPolicy.releaseRequestIntent(c.row(),c.now(),"DRAFT_COMPENSATED").ledger();}
            catch(IllegalArgumentException invalid) {throw failed("草稿占用状态不满足补偿条件");}
            Command internal=new Command(command.tenantId(),command.actorId(),command.materialId(),row.getAllocationKey(),command.requestKey(),
                row.getVersion(),Operation.COMPENSATE_DRAFT,null);
            String eventKey=eventKey(c,Operation.COMPENSATE_DRAFT,null);
            require(io(()->events.lockByKey(command.tenantId(),eventKey))==null,"COMMITTED草稿已存在补偿事件，拒绝未知中间态");
            PuRequirementAllocationEvent e=event(c,internal,eventKey,after);
            contexts.add(c);changes.add(after);appended.add(e);receipts.add(result(e,Operation.COMPENSATE_DRAFT,false));
        }
        for(int i=0;i<contexts.size();i++) {
            Context c=contexts.get(i);PuRequirementAllocationLedger after=changes.get(i);PuRequirementAllocationEvent e=appended.get(i);
            require(io(()->ledgers.updateWithVersion(c.tenantId(),after,c.row().getVersion()))==1,"整单补偿账本CAS失败");
            require(io(()->events.insert(e))==1,"整单補偿永久事件追加失败");
        }
        require(io(()->mapper.compensateDraft(command.tenantId(),command.requisitionId(),command.requestKey(),
            command.expectedDocumentVersion(),command.actorId()))==1,"草稿头补偿CAS失败");
        require(io(()->mapper.compensateDraftLines(command.tenantId(),command.requisitionId(),command.actorId()))==lines.size(),"草稿行补偿范围不一致");
        require(io(()->mapper.markCommandCompensated(command.tenantId(),command.requestKey(),command.requisitionId()))==1,"永久command补偿CAS失败");
        require(io(()->guards.advanceVersion(command.tenantId(),command.materialId(),guard.get(command.materialId())))==1,"整单补偿guard CAS失败");
        qualification.requireSourceChainTransaction();
        return new CompensationReceipt(command.requisitionId(),command.requestKey(),receipts,false);
    }

    private CompensationReceipt replayCompensation(CompensationCommand command,CommandSource source,RequisitionHead head,
        List<PuRequirementAllocationLedger> rows,List<RequisitionLine> lines) {
        require("CANCELLED".equals(head.status())&&"1".equals(head.delFlag())&&head.version()==command.expectedDocumentVersion()+1,
            "永久补偿结果与草稿终态不一致");
        Map<Long,RequisitionLine> byId=new HashMap<>();List<String> frozen=new ArrayList<>();
        require(lines.stream().allMatch(l->positive(l.sourceId())),"永久补偿来源明细未知");
        for(RequisitionLine line:lines.stream().sorted(Comparator.comparing(RequisitionLine::sourceId)).toList()) {
            require("1".equals(line.delFlag())&&"MRP".equals(line.sourceType())&&decimal(line.applyQuantity())
                &&line.applyQuantity().signum()>0&&line.unit()!=null&&line.requiredDate()!=null,"永久补偿申请行被改写");
            String day;try {day=LocalDate.parse(line.requiredDate()).toString();}catch(RuntimeException invalid) {throw failed("永久补偿日期未知");}
            frozen.add(hash(line.lineId().toString(),line.sourceId().toString(),line.materialId().toString(),line.unit(),
                line.applyQuantity().stripTrailingZeros().toPlainString(),day));byId.put(line.lineId(),line);
        }
        require(hash(frozen.toArray(String[]::new)).equals(source.resultFingerprint()),"永久补偿冻结结果不一致");
        List<EventReceipt> results=new ArrayList<>();
        for(PuRequirementAllocationLedger row:rows) {
            RequisitionLine line=byId.get(row.getRequisitionLineId());
            require(positive(row.getAllocationId())&&"MRP_DETAIL".equals(row.getSourceType())&&Integer.valueOf(1).equals(row.getAllocationNo())
                &&positive(row.getDetailId())&&positive(row.getBaseUomId())&&line!=null&&row.getDetailId().equals(line.sourceId())
                &&row.getBaseUomId().toString().equals(line.unit())&&decimal(row.getCoveredDemandBaseQty())
                &&row.getCoveredDemandBaseQty().compareTo(line.applyQuantity())==0&&decimal(row.getOccupiedBaseQty())&&row.getOccupiedBaseQty().signum()==0
                &&decimal(row.getFulfilledBaseQty())&&row.getFulfilledBaseQty().signum()==0&&decimal(row.getReleasedBaseQty())
                &&row.getReleasedBaseQty().compareTo(row.getCoveredDemandBaseQty())==0&&RELEASED.equals(row.getAllocationStatus())
                &&row.getOrderLineId()==null&&row.getTransferTime()==null&&row.getReleasedAt()!=null&&"DRAFT_COMPENSATED".equals(row.getReleaseReason())
                &&row.getVersion()!=null&&row.getVersion()==command.expectedAllocationVersions().get(row.getAllocationKey())+1,"永久补偿账本终态被改写");
            List<PuRequirementAllocationEvent> history=io(()->events.lockByAllocation(command.tenantId(),row.getAllocationId()));
            checkHistory(row,source,history);
            String key="LIFECYCLE:"+hash(command.tenantId(),row.getAllocationKey(),Operation.COMPENSATE_DRAFT.name(),command.requisitionId().toString());
            PuRequirementAllocationEvent e=io(()->events.lockByKey(command.tenantId(),key));
            require(e!=null&&history.contains(e)&&key.equals(e.getEventKey())&&Operation.COMPENSATE_DRAFT.name().equals(e.getEventType())
                &&command.actorId().equals(e.getActorId())&&INTENT.equals(e.getFromStatus())&&RELEASED.equals(e.getToStatus())
                &&row.getCoveredDemandBaseQty().compareTo(e.getOccupiedBaseBefore())==0&&e.getOccupiedBaseAfter().signum()==0,"补偿永久事件结果缺失/冲突");
            require(io(()->mapper.lockOrdersForRequisitionLine(command.tenantId(),row.getRequisitionLineId())).isEmpty(),"补偿来源出现真实下游订单");
            results.add(result(e,Operation.COMPENSATE_DRAFT,true));
        }
        qualification.requireSourceChainTransaction();
        return new CompensationReceipt(command.requisitionId(),command.requestKey(),results,true);
    }
    private static void validateCompensation(CompensationCommand c) {
        require(c!=null&&positive(c.actorId())&&positive(c.materialId())&&positive(c.requisitionId())&&text(c.requestKey(),128)
            &&c.expectedDocumentVersion()!=null&&c.expectedDocumentVersion()>=0&&c.expectedDocumentVersion()<Integer.MAX_VALUE
            &&c.expectedAllocationVersions()!=null&&!c.expectedAllocationVersions().isEmpty()&&c.expectedAllocationVersions().size()<=1000
            &&c.expectedAllocationVersions().entrySet().stream().allMatch(e->text(e.getKey(),128)&&e.getValue()!=null&&e.getValue()>=0&&e.getValue()<Integer.MAX_VALUE),
            "整单补偿身份/期望版本无效");
        requireTenant(c.tenantId());StpUtil.checkLogin();StpUtil.checkPermission("pu:requisition:remove");
        require(c.actorId().equals(LoginHelper.getUserId())&&c.tenantId().equals(LoginHelper.getTenantId()),"整单补偿当前actor/租户不一致");
    }

    private static final String INTENT = "REQUEST_INTENT", ORDER = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY", RELEASED = "RELEASED";
    private static final Set<String> ENTRIES = Set.of("MANUAL", "MRP", "LOW_STOCK", "AI_DECISION");
    private static final Set<String> DEMAND_STATES = Set.of("DRAFT", "CONFIRMED", "PARTIAL_ISSUED", "ISSUED", "CLOSED");
    private static final Set<String> PRE_RECEIPT = Set.of("DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "REJECTED");
    private static final Set<String> RECEIVING = Set.of("APPROVED", "PARTIALLY_RECEIVED", "RECEIVED");
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuRequirementMaterialGuardMapper guards;
    private final PuRequirementAllocationLedgerMapper ledgers;
    private final PuRequirementAllocationEventMapper events;
    private final PuCertifiedPurchaseLifecycleMapper mapper;
    private final PuCertifiedPurchaseLifecycleSourceGate sourceGate;

    public PuCertifiedPurchaseLifecycleService(PuRequirementErpTransactionExecutor executor,
        PuRequirementErpTransactionQualification qualification, PuRequirementMaterialLockService locks,
        PuRequirementMaterialGuardMapper guards, PuRequirementAllocationLedgerMapper ledgers,
        PuRequirementAllocationEventMapper events, PuCertifiedPurchaseLifecycleMapper mapper) {
        this.executor=Objects.requireNonNull(executor); this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks); this.guards=Objects.requireNonNull(guards);
        this.ledgers=Objects.requireNonNull(ledgers); this.events=Objects.requireNonNull(events); this.mapper=Objects.requireNonNull(mapper);
        this.sourceGate=new PuCertifiedPurchaseLifecycleSourceGate(mapper,qualification);
    }
    /** Refuses outer transactions; never calls cross-database AutoCode in the callback. */
    public EventReceipt apply(Command command) {
        validate(command); requireActor(command);
        return executor.executeRepeatableRead(command.tenantId(), () -> applyLocked(command));
    }
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public EventReceipt applyLocked(Command command) {
        validate(command); requireActor(command); qualification.requireSourceChainTransaction();
        Context c=readContext(command.tenantId(),command.materialId(),command.allocationKey(),command.requestKey());
        PostedEffect posted=switch(command.operation()) {
            case RECEIPT_POSTED -> receipt(c,command.businessLineId());
            case RETURN_POSTED -> returned(c,command.businessLineId()); default -> null;
        };
        if(posted!=null) require(command.actorId().equals(posted.actorId()),"真实过账操作人不一致");
        return transition(c,command,posted);
    }
    /** Only persisted FINISHED receipt + matching stock result may enter the internal MQ route. */
    public EventReceipt consumeReceipt(ReceiptCommand command) {
        validateReceipt(command);
        return executor.executeRepeatableRead(command.tenantId(), () -> consumeReceiptLocked(command));
    }
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public EventReceipt consumeReceiptLocked(ReceiptCommand command) {
        validateReceipt(command); qualification.requireSourceChainTransaction();
        Context c=readContext(command.tenantId(),command.materialId(),command.allocationKey(),command.requestKey());
        PostedEffect posted=receipt(c,command.receiveLineId());
        Command internal=new Command(command.tenantId(),posted.actorId(),command.materialId(),command.allocationKey(),
            command.requestKey(),command.expectedVersion(),Operation.RECEIPT_POSTED,command.receiveLineId());
        return transition(c,internal,posted);
    }

    /** Orchestrator preflight: no returned certificate, and no mutation; qualification is rechecked later. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void validateForWorkflowLocked(Command command) {
        require(command!=null&&positive(command.actorId())&&command.operation()!=null,"整单预检身份/操作无效");
        validateIdentity(command.tenantId(),command.materialId(),command.allocationKey(),command.requestKey(),command.expectedVersion());
        // A future order line does not exist yet. This read-only preflight cannot require or fabricate its ID.
        requireActor(command); qualification.requireSourceChainTransaction();
        Context c=readContext(command.tenantId(),command.materialId(),command.allocationKey(),command.requestKey());
        require(command.expectedVersion().equals(c.row().getVersion())&&command.expectedVersion()<Integer.MAX_VALUE,"整单生命周期版本已变化");
        if((command.operation()==Operation.APPROVE&&!"APPROVED".equals(c.head().status()))
            ||(command.operation()==Operation.TRANSFER_TO_ORDER&&c.row().getOrderLineId()==null)) requireCatalog(c);
    }

    private Context readContext(String tenantId, Long materialId, String allocationKey, String requestKey) {
        requireTenant(tenantId);
        Map<Long,Integer> versions=io(()->locks.lock(tenantId,List.of(materialId)));
        require(versions!=null&&versions.size()==1&&versions.get(materialId)!=null&&versions.get(materialId)>=0
            &&versions.get(materialId)<Integer.MAX_VALUE,"物料guard版本未知");
        require("InnoDB".equalsIgnoreCase(io(mapper::commandTableEngine)),"永久command表不是已核事务表");
        require(io(mapper::pinCommandDefinition)!=null,"永久command表定义未锁定");
        sourceGate.requireAllocationDefinitions();
        PuRequirementAllocationLedger row=io(()->ledgers.lockByKey(tenantId,allocationKey));
        require(row!=null&&tenantId.equals(row.getTenantId())&&materialId.equals(row.getMaterialId())
            &&allocationKey.equals(row.getAllocationKey())&&requestKey.equals(row.getDecisionIdempotencyKey())
            &&positive(row.getAllocationId())&&positive(row.getDetailId())&&positive(row.getRequisitionLineId())
            &&positive(row.getBaseUomId())&&"MRP_DETAIL".equals(row.getSourceType())&&Integer.valueOf(1).equals(row.getAllocationNo())
            &&row.getVersion()!=null&&row.getVersion()>=0&&row.getCreateTime()!=null,"账本来源/永久请求键/版本未知");
        require(decimal(row.getCoveredDemandBaseQty())&&row.getCoveredDemandBaseQty().signum()>0
            &&decimal(row.getOccupiedBaseQty())&&decimal(row.getFulfilledBaseQty())&&decimal(row.getReleasedBaseQty())
            &&row.getCoveredDemandBaseQty().compareTo(row.getOccupiedBaseQty().add(row.getFulfilledBaseQty()).add(row.getReleasedBaseQty()))==0,"账本四量不守恒");
        Date now=io(mapper::databaseNow); require(now!=null,"数据库当前业务时间未知");
        Material material=io(()->mapper.lockMaterial(tenantId,materialId));
        require(material!=null&&tenantId.equals(material.tenantId())&&materialId.equals(material.materialId())
            &&row.getBaseUomId().equals(material.baseUomId())&&Long.valueOf(1).equals(material.status())&&"0".equals(material.delFlag()),"真实物料基本单位不一致");
        RootUnit unit=io(()->mapper.lockRootUnit(tenantId,row.getBaseUomId()));
        require(unit!=null&&tenantId.equals(unit.tenantId())&&row.getBaseUomId().equals(unit.uomId())
            &&unit.uomId().equals(unit.primaryId())&&Long.valueOf(1).equals(unit.isBase())&&unit.conversionRate()!=null
            &&BigDecimal.ONE.compareTo(unit.conversionRate())==0&&unit.quantityScale()!=null&&unit.quantityScale()>=0&&unit.quantityScale()<=4
            &&text(unit.conversionVersion(),128)&&unit.verifiedAt()!=null&&!unit.verifiedAt().after(now)
            &&Long.valueOf(1).equals(unit.status())&&"0".equals(unit.delFlag()),"暂仅支持已核根基本单位");
        quantity(row.getCoveredDemandBaseQty(),unit,true); quantity(row.getOccupiedBaseQty(),unit,false);
        quantity(row.getFulfilledBaseQty(),unit,false); quantity(row.getReleasedBaseQty(),unit,false);
        DemandDetail demand=io(()->mapper.lockDemandDetail(tenantId,row.getDetailId()));
        require(demand!=null&&tenantId.equals(demand.tenantId())&&row.getDetailId().equals(demand.detailId())
            &&materialId.equals(demand.materialId())&&positive(demand.requirementId())&&Long.valueOf(1).equals(demand.status())&&"0".equals(demand.delFlag()),"真实MRP明细归属未知");
        DemandHead demandHead=io(()->mapper.lockDemandHead(tenantId,demand.requirementId()));
        require(demandHead!=null&&tenantId.equals(demandHead.tenantId())&&demand.requirementId().equals(demandHead.requirementId())
            &&DEMAND_STATES.contains(Objects.toString(demandHead.status(),""))&&"0".equals(demandHead.delFlag()),"真实MRP父头未知");
        RequisitionLine line=io(()->mapper.lockRequisitionLine(tenantId,row.getRequisitionLineId()));
        require(line!=null&&tenantId.equals(line.tenantId())&&row.getRequisitionLineId().equals(line.lineId())
            &&materialId.equals(line.materialId())&&positive(line.requisitionId())&&"MRP".equals(line.sourceType())
            &&row.getDetailId().equals(line.sourceId())&&row.getBaseUomId().toString().equals(line.unit())
            &&decimal(line.applyQuantity())&&row.getCoveredDemandBaseQty().compareTo(line.applyQuantity())==0&&"0".equals(line.delFlag()),
            "真实申请行/来源/根单位/冻结数量不一致");
        RequisitionHead head=io(()->mapper.lockRequisitionHead(tenantId,line.requisitionId()));
        require(head!=null&&tenantId.equals(head.tenantId())&&line.requisitionId().equals(head.requisitionId())
            &&requestKey.equals(head.requestKey())&&text(head.requisitionNo(),64)&&head.version()!=null&&head.version()>=0
            &&ENTRIES.contains(Objects.toString(head.sourceType(),""))&&"0".equals(head.delFlag()),"真实申请头/请求键不一致");
        CommandSource command=io(()->mapper.lockCommand(tenantId,requestKey));
        require(command!=null&&tenantId.equals(command.tenantId())&&requestKey.equals(command.requestKey())
            &&head.requisitionId().equals(command.requisitionId())&&Objects.equals(head.requisitionNo(),command.requisitionNo())
            &&materialId.equals(command.materialId())&&positive(command.actorId())&&positive(command.vendorId())
            &&head.sourceType().equals(command.entryType())&&digest(command.requestHash())&&digest(command.sourceFingerprint())
            &&digest(command.resultFingerprint())&&"COMMITTED".equals(command.commandStatus()),"永久command归属/冻结摘要/状态未知");
        List<RequisitionLine> lines=io(()->mapper.lockRequisitionLines(tenantId,head.requisitionId()));
        require(lines!=null&&!lines.isEmpty()&&lines.stream().allMatch(Objects::nonNull),"申请行当前读取未知");
        Set<Long> lineIds=new HashSet<>(),detailIds=new HashSet<>(); List<String> frozen=new ArrayList<>();
        require(lines.stream().allMatch(l->positive(l.sourceId())),"冻结结果来源ID未知");
        for(RequisitionLine source:lines.stream().sorted(Comparator.comparing(RequisitionLine::sourceId)).toList()) {
            require(tenantId.equals(source.tenantId())&&head.requisitionId().equals(source.requisitionId())
                &&materialId.equals(source.materialId())&&positive(source.lineId())&&lineIds.add(source.lineId())
                &&detailIds.add(source.sourceId())&&"MRP".equals(source.sourceType())&&row.getBaseUomId().toString().equals(source.unit())
                &&"0".equals(source.delFlag())&&decimal(source.applyQuantity())&&source.applyQuantity().signum()>0&&source.requiredDate()!=null,
                "冻结结果中的申请行身份未知");
            String day;
            try {day=LocalDate.parse(source.requiredDate()).toString();} catch(RuntimeException invalid) {throw failed("冻结申请日期未知");}
            frozen.add(hash(source.lineId().toString(),source.sourceId().toString(),source.materialId().toString(),source.unit(),
                source.applyQuantity().stripTrailingZeros().toPlainString(),day));
        }
        require(lines.contains(line)&&hash(frozen.toArray(String[]::new)).equals(command.resultFingerprint()),"申请行与永久冻结结果不一致");
        List<PuRequirementAllocationLedger> related=io(()->mapper.lockRelatedAllocations(tenantId,row.getRequisitionLineId(),row.getOrderLineId()));
        require(related!=null&&related.size()==1&&row.equals(related.get(0)),"暂不支持多账本合并/拆分承接");
        List<PuRequirementAllocationEvent> history=io(()->events.lockByAllocation(tenantId,row.getAllocationId()));
        checkHistory(row,command,history);
        List<OrderLine> orders=io(()->mapper.lockOrdersForRequisitionLine(tenantId,line.lineId()));
        require(orders!=null&&orders.size()<=1&&orders.stream().allMatch(Objects::nonNull),"暂不支持重复/拆分订单承接");
        OrderLine orderLine=orders.isEmpty()?null:orders.get(0); OrderHead orderHead=null;
        if(orderLine!=null) {
            checkOrderLine(orderLine,tenantId,row,line);
            orderHead=io(()->mapper.lockOrderHead(tenantId,orderLine.orderId()));
            require(orderHead!=null&&tenantId.equals(orderHead.tenantId())&&orderLine.orderId().equals(orderHead.orderId())
                &&head.requisitionId().equals(orderHead.requisitionId())&&"0".equals(orderHead.delFlag()),"真实订单父链不一致");
        }
        if(row.getOrderLineId()!=null) require(orderLine!=null&&row.getOrderLineId().equals(orderLine.lineId())&&row.getTransferTime()!=null,"已转移账本实际承接缺失");
        return new Context(tenantId,versions.get(materialId),row,head,line,command,unit,orderLine,orderHead,history,now);
    }

    private EventReceipt transition(Context c, Command command, PostedEffect posted) {
        String key=eventKey(c,command.operation(),command.businessLineId());
        PuRequirementAllocationEvent prior=io(()->events.lockByKey(c.tenantId(),key));
        if(prior!=null) {
            require(key.equals(prior.getEventKey())&&c.row().getAllocationId().equals(prior.getAllocationId())
                &&c.tenantId().equals(prior.getTenantId())&&command.operation().name().equals(prior.getEventType())
                &&c.row().getRequisitionLineId().equals(prior.getRequisitionLineId())&&command.actorId().equals(prior.getActorId())
                &&c.history().contains(prior),"永久事件冲突，不能当作成功重放");
            if(posted!=null) {
                require(c.orderLine()!=null&&c.orderLine().lineId().equals(prior.getOrderLineId()),"物理结果重放承接身份不一致");
                reconcileCounters(c);
                BigDecimal original;
                if(command.operation()==Operation.RECEIPT_POSTED) {
                    require(ORDER.equals(prior.getFromStatus())&&ORDER.equals(prior.getToStatus()),"收货重放原始状态未知");
                    original=prior.getOccupiedBaseBefore().subtract(prior.getOccupiedBaseAfter());
                } else {
                    // RELEASED -> RELEASED has delta zero and the old event schema stores no posted quantity.
                    require(ORDER.equals(prior.getFromStatus())&&ORDER.equals(prior.getToStatus()),"已结案退货事件缺少原数量冻结证据，不支持重放");
                    original=prior.getOccupiedBaseAfter().subtract(prior.getOccupiedBaseBefore());
                }
                require(decimal(original)&&original.signum()>0&&original.compareTo(posted.quantity())==0,"物理结果与永久事件冻结数量不一致");
            }
            return result(prior,command.operation(),true);
        }
        require(command.expectedVersion().equals(c.row().getVersion())&&command.expectedVersion()<Integer.MAX_VALUE,"账本期望版本已变化");
        if(command.operation()==Operation.APPROVE||command.operation()==Operation.TRANSFER_TO_ORDER) requireCatalog(c);
        PuRequirementAllocationPolicy.Change change;
        try {
            change=switch(command.operation()) {
                case APPROVE -> {
                    require("APPROVED".equals(c.head().status())&&c.orderLine()==null,"真实申请未完成审批或已存在订单");
                    requireApproved(c); yield PuRequirementAllocationPolicy.approveRequestIntent(c.row(),c.now());
                }
                case TRANSFER_TO_ORDER -> {
                    require(c.orderLine()!=null&&c.orderLine().lineId().equals(command.businessLineId())&&"ORDERED".equals(c.head().status())
                        &&PRE_RECEIPT.contains(Objects.toString(c.orderHead().status(),""))&&c.orderLine().receivedQuantity().signum()==0
                        &&c.orderLine().returnedQuantity().signum()==0,"实际转单/订单初态未核验");
                    requireApproved(c);requireNoUnprocessedMovements(c);
                    yield PuRequirementAllocationPolicy.commitToOrder(c.row(),c.orderLine().lineId(),c.now());
                }
                case CLOSE_REQUISITION,CANCEL_REQUISITION,REJECT_REQUISITION -> {
                    String state=switch(command.operation()) {case CLOSE_REQUISITION -> "CLOSED";case CANCEL_REQUISITION -> "CANCELLED";default -> "REJECTED";};
                    require(state.equals(c.head().status())&&c.orderLine()==null&&c.row().getOrderLineId()==null,"申请终态不能释放实际订单承接");
                    yield PuRequirementAllocationPolicy.releaseRequestIntent(c.row(),c.now(),command.operation().name());
                }
                case CLOSE_ORDER,CANCEL_ORDER -> {
                    require(c.orderLine()!=null&&(command.operation()==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED").equals(c.orderHead().status()),"订单未达到真实结案状态");
                    reconcileCounters(c);requireNoUnprocessedMovements(c);
                    yield PuRequirementAllocationPolicy.releaseOrder(c.row(),c.now(),command.operation().name());
                }
                case RECEIPT_POSTED -> {
                    require(posted!=null&&c.orderLine()!=null&&RECEIVING.contains(Objects.toString(c.orderHead().status(),"")),"实际订单不能接受收货回写");
                    reconcileCounters(c);yield PuRequirementAllocationPolicy.receiveOrder(c.row(),posted.quantity(),c.now());
                }
                case RETURN_POSTED -> {
                    require(posted!=null&&c.orderLine()!=null,"真实退货承接未知");
                    require(ORDER.equals(c.row().getAllocationStatus()),"已结案退货缺少可重放的原数量冻结证据，本合同不支持写入");
                    require(RECEIVING.contains(Objects.toString(c.orderHead().status(),""))||(RELEASED.equals(c.row().getAllocationStatus())
                        &&Set.of("CLOSED","CANCELLED").contains(Objects.toString(c.orderHead().status(),""))),"订单退货状态不支持");
                    reconcileCounters(c);yield PuRequirementAllocationPolicy.returnOrder(c.row(),posted.quantity(),c.now());
                }
                case COMPENSATE_DRAFT -> throw failed("补偿必须走整张草稿的版本/永久command合同");
            };
        } catch(IllegalArgumentException invalid) {throw failed("账本局部生命周期不变量不满足");}
        if(posted!=null) persistCounters(c,command,posted);
        PuRequirementAllocationLedger after=change.ledger();
        require(io(()->ledgers.updateWithVersion(c.tenantId(),after,command.expectedVersion()))==1,"账本版本CAS失败");
        PuRequirementAllocationEvent event=event(c,command,key,after);
        require(io(()->events.insert(event))==1,"永久事件追加失败");
        require(io(()->guards.advanceVersion(c.tenantId(),c.row().getMaterialId(),c.guardVersion()))==1,"guard版本CAS失败");
        if(command.operation()==Operation.APPROVE||command.operation()==Operation.TRANSFER_TO_ORDER) requireCatalog(c);
        qualification.requireSourceChainTransaction();
        return result(event,command.operation(),false);
    }

    private PostedEffect receipt(Context c, Long receiveLineId) {
        sourceGate.requireDefinitions();
        require(positive(receiveLineId)&&c.orderLine()!=null&&c.row().getOrderLineId()!=null,"收货承接身份未知");
        ReceiveLine line=io(()->mapper.lockReceiveLine(c.tenantId(),receiveLineId));
        require(line!=null&&c.tenantId().equals(line.tenantId())&&receiveLineId.equals(line.lineId())
            &&c.orderLine().lineId().equals(line.purchaseLineId())&&c.row().getMaterialId().equals(line.materialId())
            &&c.row().getBaseUomId().toString().equals(line.baseUomId())&&"0".equals(line.delFlag()),"实际收货行归属未知");
        quantity(line.quantityReceived(),c.unit(),true);
        ReceiveHead head=io(()->mapper.lockReceiveHead(c.tenantId(),line.receiveId()));
        require(head!=null&&c.tenantId().equals(head.tenantId())&&line.receiveId().equals(head.receiveId())
            &&c.orderLine().orderId().equals(head.orderId())&&"FINISHED".equals(head.status())&&"0".equals(head.delFlag()),"收货尚未真实执行完成");
        List<ReceiveLine> lines=io(()->mapper.lockReceiveLines(c.tenantId(),line.receiveId()));
        require(lines!=null&&lines.size()==1&&line.equals(lines.get(0)),"暂不支持缺逐行库存归属的多行收货");
        List<StockEffect> effects=io(()->mapper.lockStockEffects(c.tenantId(),"WM_MATERIAL_RECEIVE",line.receiveId()));
        BigDecimal incoming=BigDecimal.ZERO,outgoing=BigDecimal.ZERO; Long actor=null;Set<Long> ids=new HashSet<>();
        require(effects!=null&&!effects.isEmpty(),"收货缺少持久库存入账结果");
        for(StockEffect effect:effects) {
            checkEffect(c,effect,"WM_MATERIAL_RECEIVE",line.receiveId());
            require(ids.add(effect.txId())&&(effect.sourceDocLineId()==null||line.lineId().equals(effect.sourceDocLineId())),"库存入账归属冲突");
            if(actor==null) actor=effect.actorId();require(actor.equals(effect.actorId()),"入账结果操作人不一致");
            if("TARGET_IN".equals(effect.txType())&&Integer.valueOf(1).equals(effect.txFlag())) incoming=incoming.add(effect.quantity());
            else if("VIRTUAL_OUT".equals(effect.txType())&&Integer.valueOf(-1).equals(effect.txFlag())) outgoing=outgoing.add(effect.quantity());
            else throw failed("未知收货库存结果类型");
        }
        require(incoming.compareTo(line.quantityReceived())==0&&outgoing.compareTo(incoming)==0,"实际库存入账与收货行不守恒");
        return new PostedEffect(line.quantityReceived(),actor);
    }

    private PostedEffect returned(Context c, Long returnLineId) {
        sourceGate.requireDefinitions();
        require(positive(returnLineId)&&c.orderLine()!=null&&c.row().getOrderLineId()!=null,"退货承接身份未知");
        ReturnLine line=io(()->mapper.lockReturnLine(c.tenantId(),returnLineId));
        require(line!=null&&c.tenantId().equals(line.tenantId())&&returnLineId.equals(line.lineId())
            &&c.orderLine().lineId().equals(line.orderLineId())&&c.orderLine().orderId().equals(line.orderId())
            &&c.row().getMaterialId().equals(line.materialId())&&c.row().getBaseUomId().toString().equals(line.unit())
            &&"EXECUTED".equals(line.status())&&"0".equals(line.delFlag())&&"0".equals(line.lineDelFlag()),"退货未真实执行或头/行删除状态及归属未知");
        quantity(line.returnQuantity(),c.unit(),true);
        List<StockEffect> effects=io(()->mapper.lockStockEffects(c.tenantId(),"PU_PURCHASE_RETURN",line.returnId()));
        require(effects!=null&&!effects.isEmpty(),"退货缺少持久库存出账结果");
        BigDecimal posted=BigDecimal.ZERO; Long actor=null; Set<Long> ids=new HashSet<>();
        for(StockEffect effect:effects) {
            require(effect!=null&&positive(effect.sourceDocLineId()),"退货库存结果缺逐行归属");
            if(!line.lineId().equals(effect.sourceDocLineId())) continue;
            checkEffect(c,effect,"PU_PURCHASE_RETURN",line.returnId());
            require(ids.add(effect.txId())&&"PUR_RETURN_OUT".equals(effect.txType())&&Integer.valueOf(-1).equals(effect.txFlag()),"退货库存结果类型冲突");
            if(actor==null) actor=effect.actorId();require(actor.equals(effect.actorId()),"退货出账结果操作人不一致");
            posted=posted.add(effect.quantity());
        }
        require(posted.compareTo(line.returnQuantity())==0&&positive(actor),"实际库存出账与退货行不守恒");
        return new PostedEffect(line.returnQuantity(),actor);
    }

    private static void checkEffect(Context c, StockEffect effect, String type, Long sourceId) {
        require(effect!=null&&c.tenantId().equals(effect.tenantId())&&positive(effect.txId())
            &&c.row().getMaterialId().equals(effect.materialId())&&c.row().getBaseUomId().toString().equals(effect.baseUomId())
            &&type.equals(effect.sourceDocType())&&sourceId.equals(effect.sourceDocId())&&positive(effect.stockId())
            &&positive(effect.actorId())&&"0".equals(effect.delFlag())
            &&(Integer.valueOf(1).equals(effect.txFlag())||Integer.valueOf(-1).equals(effect.txFlag())),"真实库存结果身份未知");
        quantity(effect.quantity(),c.unit(),true);quantity(effect.beforeQuantity(),c.unit(),false);quantity(effect.afterQuantity(),c.unit(),false);
        require(effect.afterQuantity().subtract(effect.beforeQuantity()).compareTo(effect.quantity().multiply(BigDecimal.valueOf(effect.txFlag())))==0,"库存入出账前后量不守恒");
    }

    private void persistCounters(Context c, Command command, PostedEffect posted) {
        BigDecimal received=c.orderLine().receivedQuantity(),returned=c.orderLine().returnedQuantity();
        if(command.operation()==Operation.RECEIPT_POSTED) received=received.add(posted.quantity());else returned=returned.add(posted.quantity());
        require(decimal(received)&&decimal(returned)&&received.compareTo(c.orderLine().orderQuantity())<=0&&returned.compareTo(received)<=0,"订单实收/退货计数越界");
        String status=received.compareTo(c.orderLine().orderQuantity())==0?"FULL_RECEIVED":received.signum()>0?"PARTIAL_RECEIVED":"PENDING";
        BigDecimal newReceived=received,newReturned=returned;
        require(io(()->mapper.updateOrderCounters(c.tenantId(),c.orderLine(),newReceived,newReturned,status,command.actorId()))==1,"订单行真实计数CAS失败");
        if(command.operation()!=Operation.RECEIPT_POSTED) return;
        List<OrderLine> lines=io(()->mapper.lockOrderLines(c.tenantId(),c.orderLine().orderId()));
        require(lines!=null&&!lines.isEmpty()&&lines.stream().allMatch(Objects::nonNull),"整单收货状态来源未知");
        boolean complete=true;Set<Long> ids=new HashSet<>();
        for(OrderLine line:lines) {
            require(c.tenantId().equals(line.tenantId())&&c.orderLine().orderId().equals(line.orderId())&&positive(line.lineId())
                &&ids.add(line.lineId())&&"0".equals(line.delFlag())&&decimal(line.orderQuantity())&&line.orderQuantity().signum()>0
                &&decimal(line.receivedQuantity()),"整单收货行身份未知");
            BigDecimal actual=line.lineId().equals(c.orderLine().lineId())?newReceived:line.receivedQuantity();
            require(actual.compareTo(line.orderQuantity())<=0,"整单含未支持超收");complete&=actual.compareTo(line.orderQuantity())==0;
        }
        require(ids.contains(c.orderLine().lineId()),"整单缺少本次收货行");
        String nextStatus=complete?"RECEIVED":"PARTIALLY_RECEIVED";
        if(!nextStatus.equals(c.orderHead().status())) require(io(()->mapper.updateOrderStatus(c.tenantId(),c.orderHead().orderId(),
            c.orderHead().status(),nextStatus,command.actorId()))==1,"订单头收货状态CAS失败");
    }

    /** Pending physical results must be consumed before closing; counters alone are not evidence. */
    private void requireNoUnprocessedMovements(Context c) {
        if(c.orderLine()==null) return;
        sourceGate.requireMovementDefinitions();
        checkMovementSources(c,io(()->mapper.lockReceiptSources(c.tenantId(),c.orderLine().lineId())),"FINISHED",Operation.RECEIPT_POSTED);
        checkMovementSources(c,io(()->mapper.lockReturnSources(c.tenantId(),c.orderLine().lineId())),"EXECUTED",Operation.RETURN_POSTED);
    }
    private static void checkMovementSources(Context c,List<MovementSource> sources,String terminal,Operation operation) {
        require(sources!=null,"下游物理单据当前读取未知");Set<Long> ids=new HashSet<>();
        for(MovementSource source:sources) {
            require(source!=null&&c.tenantId().equals(source.tenantId())&&positive(source.lineId())&&ids.add(source.lineId())
                &&positive(source.documentId())&&"0".equals(source.delFlag())&&"0".equals(source.lineDelFlag())&&terminal.equals(source.status()),"存在未闭合或未知下游物理单据");
            String key=eventKey(c,operation,source.lineId());
            require(c.history().stream().anyMatch(e->key.equals(e.getEventKey())&&operation.name().equals(e.getEventType())),"存在尚未永久入账的收货/退货结果");
        }
    }

    /** Frozen total is read from real requisition lines; no fresh allocation or re-occupation is performed. */
    private void requireCatalog(Context c) {
        VendorIdentity vendor=io(()->mapper.lockLifecycleVendor(c.tenantId(),c.command().vendorId()));
        require(vendor!=null&&c.tenantId().equals(vendor.tenantId())&&c.command().vendorId().equals(vendor.vendorId())
            &&Long.valueOf(1).equals(vendor.status())&&"0".equals(vendor.delFlag()),"当前真实供应商失效");
        List<PurchaseTerms> rows=io(()->mapper.lockPurchaseTerms(c.tenantId(),c.row().getMaterialId(),c.command().vendorId()));
        require(rows!=null&&rows.size()==1&&rows.get(0)!=null,"当前供应条款缺失/重复");PurchaseTerms terms=rows.get(0);
        Date clock=io(mapper::databaseNow);
        require(clock!=null&&c.tenantId().equals(terms.tenantId())&&positive(terms.relationId())
            &&c.row().getMaterialId().equals(terms.materialId())&&c.command().vendorId().equals(terms.vendorId())
            &&c.row().getBaseUomId().equals(terms.orderUomId())&&c.row().getBaseUomId().equals(terms.moqUomId())
            &&Long.valueOf(1).equals(terms.status())&&"0".equals(terms.delFlag())&&text(terms.version(),128)
            &&terms.effectiveAt()!=null&&!terms.effectiveAt().after(clock)&&terms.expiresAt()!=null&&terms.expiresAt().after(clock)
            &&terms.verifiedAt()!=null&&!terms.verifiedAt().after(clock),"终端数据库时钟下供应条款/根单位未核验或已过期");
        quantity(terms.moq(),c.unit(),false);quantity(terms.multiple(),c.unit(),true);quantity(terms.unitsPerPack(),c.unit(),true);
        require(Integer.valueOf(0).equals(terms.allowSplit())||Integer.valueOf(1).equals(terms.allowSplit()),"条款拆零事实未知");
        List<RequisitionLine> lines=io(()->mapper.lockRequisitionLines(c.tenantId(),c.head().requisitionId()));
        require(lines!=null&&!lines.isEmpty()&&lines.stream().allMatch(Objects::nonNull),"终端冻结采购行未知");
        BigDecimal total=BigDecimal.ZERO;
        for(RequisitionLine line:lines) {
            require(c.tenantId().equals(line.tenantId())&&c.head().requisitionId().equals(line.requisitionId())
                &&c.row().getMaterialId().equals(line.materialId())&&c.row().getBaseUomId().toString().equals(line.unit())
                &&"0".equals(line.delFlag()),"终端采购行根单位/归属变化");
            quantity(line.applyQuantity(),c.unit(),true);total=total.add(line.applyQuantity());
        }
        quantity(total,c.unit(),true);
        require(total.compareTo(terms.moq())>=0&&total.remainder(terms.multiple()).signum()==0
            &&(terms.allowSplit()==1||total.remainder(terms.unitsPerPack()).signum()==0),"冻结整单量不满足当前MOQ/倍数/包装条款");
    }

    private static void checkHistory(PuRequirementAllocationLedger row,CommandSource command,List<PuRequirementAllocationEvent> history) {
        require(history!=null&&!history.isEmpty()&&history.size()==(long)row.getVersion()+1,"永久事件与账本版本不一致");
        BigDecimal occupied=BigDecimal.ZERO;String status=null;Set<Long> ids=new HashSet<>();Set<String> keys=new HashSet<>();
        for(int i=0;i<history.size();i++) {
            PuRequirementAllocationEvent e=history.get(i);
            require(e!=null&&row.getTenantId().equals(e.getTenantId())&&row.getAllocationId().equals(e.getAllocationId())
                &&row.getRequisitionLineId().equals(e.getRequisitionLineId())&&positive(e.getEventId())&&ids.add(e.getEventId())
                &&e.getEventSequence()!=null&&Integer.valueOf(i).equals(e.getEventSequence())
                &&text(e.getEventKey(),128)&&keys.add(e.getEventKey())&&positive(e.getActorId())&&e.getOccurredAt()!=null
                &&decimal(e.getOccupiedBaseBefore())&&decimal(e.getOccupiedBaseAfter())
                &&occupied.compareTo(e.getOccupiedBaseBefore())==0&&Objects.equals(status,e.getFromStatus()),"永久事件链身份或前后状态不一致");
            if(i==0) require("CREATE".equals(e.getEventType())&&("CREATE:"+row.getAllocationKey()).equals(e.getEventKey())
                &&INTENT.equals(e.getToStatus())&&e.getOrderLineId()==null&&command.actorId().equals(e.getActorId())
                &&row.getCoveredDemandBaseQty().compareTo(e.getOccupiedBaseAfter())==0,"CREATE永久来源与冻结command不一致");
            else require(Arrays.stream(Operation.values()).anyMatch(op->op.name().equals(e.getEventType())),"永久事件类型未支持");
            occupied=e.getOccupiedBaseAfter();status=e.getToStatus();
        }
        require(Objects.equals(status,row.getAllocationStatus())&&occupied.compareTo(row.getOccupiedBaseQty())==0,"永久事件末态与账本不一致");
    }
    private static void checkOrderLine(OrderLine order,String tenantId,PuRequirementAllocationLedger row,RequisitionLine req) {
        require(tenantId.equals(order.tenantId())&&positive(order.lineId())&&positive(order.orderId())&&req.lineId().equals(order.requisitionLineId())
            &&row.getMaterialId().equals(order.materialId())&&row.getBaseUomId().toString().equals(order.unit())&&"0".equals(order.delFlag())
            &&decimal(order.orderQuantity())&&order.orderQuantity().compareTo(row.getCoveredDemandBaseQty())==0
            &&decimal(order.receivedQuantity())&&decimal(order.returnedQuantity())&&order.receivedQuantity().compareTo(order.orderQuantity())<=0
            &&order.returnedQuantity().compareTo(order.receivedQuantity())<=0,"真实订单行根单位/数量/来源不一致");
    }
    private static void reconcileCounters(Context c) {
        require(c.orderLine()!=null&&c.orderLine().receivedQuantity().subtract(c.orderLine().returnedQuantity()).compareTo(c.row().getFulfilledBaseQty())==0,
            "订单实收/退货与账本履约不一致，禁止旧回写重复累加");
        BigDecimal received=BigDecimal.ZERO,returned=BigDecimal.ZERO;
        for(PuRequirementAllocationEvent e:c.history()) {
            BigDecimal quantity;
            if(Operation.RECEIPT_POSTED.name().equals(e.getEventType())) {
                require(c.orderLine().lineId().equals(e.getOrderLineId())&&ORDER.equals(e.getFromStatus())&&ORDER.equals(e.getToStatus()),
                    "历史收货原承接/状态不完整");
                quantity=e.getOccupiedBaseBefore().subtract(e.getOccupiedBaseAfter());
                require(decimal(quantity)&&quantity.signum()>0,"历史收货缺少原数量冻结证据");received=received.add(quantity);
            } else if(Operation.RETURN_POSTED.name().equals(e.getEventType())) {
                require(c.orderLine().lineId().equals(e.getOrderLineId())&&ORDER.equals(e.getFromStatus())&&ORDER.equals(e.getToStatus()),
                    "历史结案退货缺少原数量冻结证据，不支持恢复");
                quantity=e.getOccupiedBaseAfter().subtract(e.getOccupiedBaseBefore());
                require(decimal(quantity)&&quantity.signum()>0,"历史退货缺少原数量冻结证据");returned=returned.add(quantity);
            }
        }
        require(received.compareTo(c.orderLine().receivedQuantity())==0&&returned.compareTo(c.orderLine().returnedQuantity())==0,
            "订单实收/退货计数与持久物理事件逐项不一致，净额相同不能掩盖计数漂移");
    }
    private static void requireApproved(Context c) {
        quantity(c.line().approvedQuantity(),c.unit(),true);
        require(c.row().getCoveredDemandBaseQty().compareTo(c.line().approvedQuantity())==0,"暂不支持审批改变冻结申请数量");
    }
    private static void quantity(BigDecimal value,RootUnit unit,boolean requirePositive) {
        require(decimal(value)&&(!requirePositive||value.signum()>0)&&value.stripTrailingZeros().scale()<=unit.quantityScale(),"真实数量超出根单位或DECIMAL精度");
    }
    private static String eventKey(Context c,Operation op,Long businessLine) {
        Long source=switch(op) {case TRANSFER_TO_ORDER,RECEIPT_POSTED,RETURN_POSTED -> businessLine;
            case CLOSE_ORDER,CANCEL_ORDER -> c.orderLine()==null?null:c.orderLine().orderId();default -> c.head().requisitionId();};
        require(positive(source),"永久生命周期业务来源身份未知");
        return "LIFECYCLE:"+hash(c.tenantId(),c.row().getAllocationKey(),op.name(),source.toString());
    }
    private static PuRequirementAllocationEvent event(Context c,Command command,String key,PuRequirementAllocationLedger after) {
        var e=new PuRequirementAllocationEvent();e.setEventSequence(command.expectedVersion()+1);
        e.setEventId(IdWorker.getId());e.setTenantId(c.tenantId());e.setAllocationId(c.row().getAllocationId());
        e.setEventKey(key);e.setEventType(command.operation().name());e.setFromStatus(c.row().getAllocationStatus());e.setToStatus(after.getAllocationStatus());
        e.setOccupiedBaseBefore(c.row().getOccupiedBaseQty());e.setOccupiedBaseAfter(after.getOccupiedBaseQty());
        e.setRequisitionLineId(after.getRequisitionLineId());e.setOrderLineId(after.getOrderLineId());e.setActorId(command.actorId());
        e.setOccurredAt(new Date(c.now().getTime()));return e;
    }
    private static EventReceipt result(PuRequirementAllocationEvent e,Operation op,boolean replayed) {
        return new EventReceipt(e.getEventId(),e.getAllocationId(),op,e.getFromStatus(),e.getToStatus(),e.getOccupiedBaseBefore(),
            e.getOccupiedBaseAfter(),e.getRequisitionLineId(),e.getOrderLineId(),e.getActorId(),replayed);
    }
    private static void validate(Command c) {
        require(c!=null&&positive(c.actorId())&&c.operation()!=null,"生命周期入口身份/操作无效");
        validateIdentity(c.tenantId(),c.materialId(),c.allocationKey(),c.requestKey(),c.expectedVersion());
        boolean line=c.operation()==Operation.TRANSFER_TO_ORDER||c.operation()==Operation.RECEIPT_POSTED||c.operation()==Operation.RETURN_POSTED;
        require(line?positive(c.businessLineId()):c.businessLineId()==null,"生命周期真实行ID不匹配");
    }
    private static void validateReceipt(ReceiptCommand c) {
        require(c!=null&&positive(c.receiveLineId()),"持久收货行ID无效");
        validateIdentity(c.tenantId(),c.materialId(),c.allocationKey(),c.requestKey(),c.expectedVersion());
    }
    private static void validateIdentity(String tenant,Long material,String allocation,String request,Integer version) {
        requireTenant(tenant);require(positive(material)&&text(allocation,128)&&text(request,128)&&version!=null&&version>=0,"来源键/版本无效");
    }
    private static void requireActor(Command c) {
        StpUtil.checkLogin();
        String permission=switch(c.operation()) {case APPROVE,REJECT_REQUISITION -> "pu:requisition:approve";
            case TRANSFER_TO_ORDER -> "pu:requisition:convert";case CLOSE_REQUISITION -> "pu:requisition:close";
            case CANCEL_REQUISITION -> "pu:requisition:cancel";case CLOSE_ORDER -> "pu:order:close";case CANCEL_ORDER -> "pu:order:cancel";
            case RECEIPT_POSTED -> "pu:order:receive";case RETURN_POSTED -> "pu:return:execute";case COMPENSATE_DRAFT -> "pu:requisition:remove";};
        StpUtil.checkPermission(permission);
        require(c.actorId().equals(LoginHelper.getUserId())&&c.tenantId().equals(LoginHelper.getTenantId()),"当前租户/actor不匹配");
    }
    private static void requireTenant(String tenant) {
        require(TenantHelper.isEnable()&&text(tenant,20)&&tenant.equals(TenantHelper.getTenantId()),"当前租户未启用或不匹配");
    }
    private static boolean digest(String value) {return value!=null&&value.matches("[0-9a-f]{64}");}
    private static <T> T io(Supplier<T> call) {
        try {return call.get();}catch(RuntimeException failure) {throw failed("持久来源读写失败，不能确认结果");}
    }
    private static void require(boolean condition,String message) {if(!condition) throw failed(message);}
    private static ServiceException failed(String message) {return new ServiceException("可信采购生命周期拒绝："+message);}
    private record Context(String tenantId,Integer guardVersion,PuRequirementAllocationLedger row,RequisitionHead head,
        RequisitionLine line,CommandSource command,RootUnit unit,OrderLine orderLine,OrderHead orderHead,
        List<PuRequirementAllocationEvent> history,Date now) { }
    private record PostedEffect(BigDecimal quantity,Long actorId) { }
}
