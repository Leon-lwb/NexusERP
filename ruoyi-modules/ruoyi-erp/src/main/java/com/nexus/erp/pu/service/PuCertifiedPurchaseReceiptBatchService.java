package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseReceiptBatchMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/**
 * 整单 MQ 收货执行器。调用方提供的数量、操作人、标记、Redis 键或请求标签均不具备权威性。
 * 在首次修改计数/账本/事件之前，必须校验全部当前原生来源及生命周期上下文。
 * 所有行由单个专属的ERP独占可重复读事务统一提交，否则整体失败。
 * 既有逐收货行生命周期事件构成永久回执契约；提交结果不确定后，不得另建第二个 DOC 单据。
 * 旧路径必须经过显式证明，绝不能作为异常后的回退路径。
 */
@Service
@DS("erp")
public class PuCertifiedPurchaseReceiptBatchService {
    public record LineReceipt(Long receiveLineId,EventReceipt event) { }
    public record BatchReceipt(String tenantId,Long receiveId,Long orderId,boolean certified,List<LineReceipt> lines,boolean replayed) {
        public BatchReceipt {lines=List.copyOf(lines);}
    }
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseReceiptBatchMapper mapper;
    private final PuCertifiedPurchaseLifecycleMapper physical;
    private final PuCertifiedPurchaseLifecycleService lifecycle;
    private final WmCertifiedReceiptPostingService posting;
    private final PuCertifiedPurchaseLifecycleSourceGate sources;

    public PuCertifiedPurchaseReceiptBatchService(PuRequirementErpTransactionExecutor executor,
        PuRequirementErpTransactionQualification qualification,PuRequirementMaterialLockService locks,
        PuCertifiedPurchaseReceiptBatchMapper mapper,PuCertifiedPurchaseLifecycleMapper physical,
        PuCertifiedPurchaseLifecycleService lifecycle,WmCertifiedReceiptPostingService posting) {
        this.executor=Objects.requireNonNull(executor);this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks);this.mapper=Objects.requireNonNull(mapper);this.physical=Objects.requireNonNull(physical);
        this.lifecycle=Objects.requireNonNull(lifecycle);this.posting=Objects.requireNonNull(posting);
        this.sources=new PuCertifiedPurchaseLifecycleSourceGate(physical,qualification);
    }
    /** public 代理入口有意不使用环境事务的 @Transactional 包装。 */
    public BatchReceipt consumeReceipt(String tenantId,Long receiveId) {return consumeReceipt(tenantId,receiveId,null);}
    public BatchReceipt consumeReceipt(String tenantId,Long receiveId,Long expectedOrderId) {
        require(TenantHelper.isEnable()&&text(tenantId,20)&&tenantId.equals(TenantHelper.getTenantId()),"当前租户未启用或不一致");
        require(positive(receiveId)&&(expectedOrderId==null||positive(expectedOrderId)),"真实收货/订单ID无效");
        return executor.executeRepeatableRead(tenantId,()->consumeLocked(tenantId,receiveId,expectedOrderId));
    }
    /**
     * 在调用方已拥有的 ERP 可重复读事务中读回永久消费证据，不自启事务或补造任何业务结果。
     * 空结果仅表示全部原事件均未观察到，不能据此认定收货消费已完成。
     */
    public Optional<BatchReceipt> readCommittedReceiptLocked(String tenantId,Long receiveId,Long expectedOrderId) {
        require(TenantHelper.isEnable()&&text(tenantId,20)&&tenantId.equals(TenantHelper.getTenantId()),"当前租户未启用或不一致");
        require(positive(receiveId)&&(expectedOrderId==null||positive(expectedOrderId)),"真实收货/订单ID无效");
        return receiptLocked(tenantId,receiveId,expectedOrderId,true);
    }
    private BatchReceipt consumeLocked(String tenant,Long receiveId,Long expectedOrderId) {
        return receiptLocked(tenant,receiveId,expectedOrderId,false).orElseThrow(()->failed("消费结果未确认"));
    }
    /** 读回与消费共享完整原生来源校验，守卫取得和最终结果生成必须显式区分读写路径。 */
    private Optional<BatchReceipt> receiptLocked(String tenant,Long receiveId,Long expectedOrderId,boolean readOnly) {
        qualification.requireSourceChainTransaction();sources.requireAllocationDefinitions();
        ReceiveHead hint=io(()->mapper.readReceiptHead(tenant,receiveId));
        require(hint!=null&&tenant.equals(hint.tenantId())&&receiveId.equals(hint.receiveId())&&positive(hint.orderId()),"收货原父链缺失");
        List<Long> hints=rows(io(()->mapper.readMaterialHints(tenant,receiveId)));
        require(!hints.isEmpty()&&hints.stream().allMatch(v->positive(v)),"收货/订单物料发现未知");
        List<Long> materials=List.copyOf(new TreeSet<>(hints));
        Map<Long,Integer> guarded=io(()->readOnly?locks.lockExisting(tenant,materials):locks.lock(tenant,materials));
        require(guarded!=null&&guarded.keySet().equals(new HashSet<>(materials))&&guarded.values().stream()
            .allMatch(v->v!=null&&v>=0&&v<Integer.MAX_VALUE),"物料guard版本未知");

        ReceiveHead head=io(()->mapper.lockReceiptHead(tenant,receiveId));
        require(head!=null&&tenant.equals(head.tenantId())&&receiveId.equals(head.receiveId())&&hint.orderId().equals(head.orderId())
            &&"0".equals(head.delFlag())&&"FINISHED".equals(head.status())&&(expectedOrderId==null||expectedOrderId.equals(head.orderId())),"当前收货头/父订单/执行终态不一致");
        List<ReceiveLine> received=rows(io(()->mapper.lockReceiptLines(tenant,receiveId)));
        require(!received.isEmpty(),"当前收货行全集缺失");Set<Long> receivedIds=new HashSet<>();
        for(ReceiveLine line:received) require(tenant.equals(line.tenantId())&&receiveId.equals(line.receiveId())&&positive(line.lineId())
            &&receivedIds.add(line.lineId())&&positive(line.materialId())&&guarded.containsKey(line.materialId())&&"0".equals(line.delFlag()),"当前收货行身份/物料漂移或重复");
        received=received.stream().sorted(Comparator.comparing(ReceiveLine::lineId)).toList();
        OrderHead order=io(()->mapper.lockOrderHead(tenant,head.orderId()));
        require(order!=null&&tenant.equals(order.tenantId())&&head.orderId().equals(order.orderId())&&text(order.status(),32)&&"0".equals(order.delFlag()),"真实订单头身份未知");
        List<OrderLine> orderLines=rows(io(()->mapper.lockOrderLines(tenant,head.orderId())));
        require(!orderLines.isEmpty(),"真实订单行全集缺失");Map<Long,OrderLine> orders=new HashMap<>();
        for(OrderLine line:orderLines) require(tenant.equals(line.tenantId())&&head.orderId().equals(line.orderId())&&positive(line.lineId())
            &&orders.putIfAbsent(line.lineId(),line)==null&&positive(line.materialId())&&guarded.containsKey(line.materialId())&&"0".equals(line.delFlag()),"真实订单行身份/物料漂移或重复");

        // 绝不只凭标记缺失判定来源类别；必须按真实单据/行 ID 读取永久证据。
        List<RequisitionHead> heads=rows(io(()->mapper.lockRequisitionHeadsForOrder(tenant,head.orderId())));
        List<RequisitionLine> requisitions=rows(io(()->mapper.lockRequisitionLinesForOrder(tenant,head.orderId())));
        List<CommandSource> commands=rows(io(()->mapper.lockCommandsForOrder(tenant,head.orderId())));
        List<PuRequirementAllocationLedger> allocations=rows(io(()->mapper.lockAllocationsForOrder(tenant,head.orderId())));
        require(heads.stream().allMatch(h->tenant.equals(h.tenantId()))&&requisitions.stream().allMatch(l->tenant.equals(l.tenantId()))
            &&commands.stream().allMatch(c->tenant.equals(c.tenantId()))&&allocations.stream().allMatch(a->tenant.equals(a.getTenantId())),"永久来源租户不一致");
        boolean certified=!commands.isEmpty()||!allocations.isEmpty()||heads.stream().anyMatch(h->h.requestKey()!=null||sourceHint(h.sourceType()))
            ||requisitions.stream().anyMatch(l->l.sourceId()!=null||sourceHint(l.sourceType()));
        validateRequisitionReferences(order,orderLines,heads,requisitions);
        if(!certified) {
            require(!readOnly,"非认证来源不能证明永久收货消费结果");
            return Optional.of(new BatchReceipt(tenant,receiveId,head.orderId(),false,List.of(),false));
        }

        require(commands.size()==1&&heads.size()==1,"认证来源永久command/申请头不是完整单一承接");
        CommandSource command=commands.get(0);RequisitionHead requisition=heads.get(0);
        require(text(command.requestKey(),128)&&positive(command.materialId())&&positive(command.actorId())&&positive(command.vendorId())
            &&positive(command.requisitionId())&&command.requisitionId().equals(order.requisitionId())&&command.requisitionId().equals(requisition.requisitionId())
            &&command.requestKey().equals(requisition.requestKey())&&Objects.equals(command.requisitionNo(),requisition.requisitionNo())
            &&Objects.equals(command.entryType(),requisition.sourceType())&&"COMMITTED".equals(command.commandStatus())
            &&digest(command.requestHash())&&digest(command.sourceFingerprint())&&digest(command.resultFingerprint()),"认证永久command/头标记/冻结来源未知，不能转历史或伪成功");
        Long material=command.materialId();require(materials.size()==1&&material.equals(materials.get(0)),"认证批次不支持混物料");
        require(allocations.size()==orderLines.size()&&requisitions.size()==orderLines.size(),"认证申请/订单/账本不是完整逐行承接");
        Map<Long,RequisitionLine> byRequisition=new HashMap<>();Set<Long> detailIds=new HashSet<>();
        List<String> frozen=new ArrayList<>();
        for(RequisitionLine line:requisitions.stream().sorted(Comparator.comparing(RequisitionLine::sourceId,Comparator.nullsFirst(Long::compareTo))).toList()) {
            require(positive(line.lineId())&&byRequisition.putIfAbsent(line.lineId(),line)==null&&command.requisitionId().equals(line.requisitionId())
                &&material.equals(line.materialId())&&text(line.unit(),32)&&"MRP".equals(line.sourceType())&&positive(line.sourceId())&&detailIds.add(line.sourceId())
                &&decimal(line.applyQuantity())&&line.applyQuantity().signum()>0&&"0".equals(line.delFlag())&&text(line.requiredDate(),10),"认证冻结申请行身份/来源/数量未知");
            String day;try {day=LocalDate.parse(line.requiredDate()).toString();}catch(RuntimeException invalid){throw failed("冻结申请日期未知");}
            frozen.add(hash(line.lineId().toString(),line.sourceId().toString(),line.materialId().toString(),line.unit(),line.applyQuantity().stripTrailingZeros().toPlainString(),day));
        }
        require(hash(frozen.toArray(String[]::new)).equals(command.resultFingerprint()),"申请当前全集与永久冻结摘要不一致");
        Map<Long,PuRequirementAllocationLedger> byOrder=new HashMap<>();Set<Long> allocationIds=new HashSet<>();Set<String> allocationKeys=new HashSet<>();
        for(PuRequirementAllocationLedger row:allocations) {
            OrderLine line=orders.get(row.getOrderLineId());RequisitionLine req=byRequisition.get(row.getRequisitionLineId());
            require(positive(row.getAllocationId())&&allocationIds.add(row.getAllocationId())&&text(row.getAllocationKey(),128)&&allocationKeys.add(row.getAllocationKey())
                &&line!=null&&req!=null&&byOrder.putIfAbsent(row.getOrderLineId(),row)==null&&row.getRequisitionLineId().equals(line.requisitionLineId())
                &&material.equals(row.getMaterialId())&&material.equals(line.materialId())&&"MRP_DETAIL".equals(row.getSourceType())&&req.sourceId().equals(row.getDetailId())
                &&command.requestKey().equals(row.getDecisionIdempotencyKey())&&positive(row.getBaseUomId())&&row.getBaseUomId().toString().equals(line.unit())
                &&line.unit().equals(req.unit())&&decimal(line.orderQuantity())&&line.orderQuantity().signum()>0&&decimal(row.getCoveredDemandBaseQty())
                &&line.orderQuantity().compareTo(row.getCoveredDemandBaseQty())==0&&req.applyQuantity().compareTo(row.getCoveredDemandBaseQty())==0
                &&row.getVersion()!=null&&row.getVersion()>=0&&row.getVersion()<Integer.MAX_VALUE,"认证账本/真实订单行/根单位/请求键不完整对应");
        }
        for(ReceiveLine line:received) {
            PuRequirementAllocationLedger row=byOrder.get(line.purchaseLineId());
            require(positive(line.purchaseLineId())&&row!=null&&material.equals(line.materialId())&&row.getBaseUomId().toString().equals(line.baseUomId())
                &&decimal(line.quantityReceived())&&line.quantityReceived().signum()>0,"认证收货缺少精确采购行/根单位/真实数量");
        }
        sources.requireDefinitions();
        // 主写入方负责该不可变过账指纹及其完整单据/明细校验。
        // 此只读 API 不得执行库存写入、新建单据或要求伪造 MQ 登录。
        posting.validatePostedLocked(tenant,receiveId);
        Long actor=validatePhysical(tenant,receiveId,received);
        int hits=0;Map<Long,PuRequirementAllocationEvent> priorEvents=new HashMap<>();
        for(ReceiveLine line:received) {
            var allocation=byOrder.get(line.purchaseLineId());String key=eventKey(tenant,allocation,line.lineId());
            var prior=io(()->mapper.lockReceiptEvent(tenant,key));
            if(prior!=null) {validatePrior(tenant,allocation,line,actor,key,prior);priorEvents.put(line.lineId(),prior);hits++;}
        }
        require(hits==0||hits==received.size(),"永久收货批次证据仅部分存在，拒绝伪造完整恢复");boolean replay=hits==received.size();
        Map<String,Integer> versions=new HashMap<>();allocations.forEach(a->versions.put(a.getAllocationKey(),a.getVersion()));
        // 此循环中不允许修改业务数据。D 负责共用的来源/历史/计数校验。
        for(ReceiveLine line:received) {
            var a=byOrder.get(line.purchaseLineId());
            ReceiptCommand check=new ReceiptCommand(tenant,material,a.getAllocationKey(),command.requestKey(),versions.get(a.getAllocationKey()),line.lineId());
            if(readOnly) lifecycle.validateReceiptReadOnlyLocked(check);
            else lifecycle.validateReceiptLocked(check);
        }
        if(!replay) {
            Map<Long,BigDecimal> amounts=new HashMap<>();for(ReceiveLine l:received)amounts.merge(l.purchaseLineId(),l.quantityReceived(),BigDecimal::add);
            amounts.forEach((id,amount)->{OrderLine l=orders.get(id);require(decimal(amount)&&decimal(l.receivedQuantity())
                &&l.receivedQuantity().add(amount).compareTo(l.orderQuantity())<=0,"批次真实实收总量超过订单量");});
        }
        List<LineReceipt> results=new ArrayList<>();
        for(ReceiveLine line:received) {
            if(readOnly&&!replay) continue;
            var a=byOrder.get(line.purchaseLineId());Integer version=versions.get(a.getAllocationKey());
            EventReceipt result;
            if(readOnly) {
                // 回执各字段只取本行已锁定的永久原事件，绝不由当前余额或单据编码推导。
                PuRequirementAllocationEvent prior=priorEvents.get(line.lineId());
                result=new EventReceipt(prior.getEventId(),prior.getAllocationId(),Operation.valueOf(prior.getEventType()),
                    prior.getFromStatus(),prior.getToStatus(),prior.getOccupiedBaseBefore(),prior.getOccupiedBaseAfter(),
                    prior.getRequisitionLineId(),prior.getOrderLineId(),prior.getActorId(),true);
            }else result=lifecycle.consumeReceiptLocked(new ReceiptCommand(tenant,material,a.getAllocationKey(),command.requestKey(),version,line.lineId()));
            require(result!=null&&positive(result.eventId())&&a.getAllocationId().equals(result.allocationId())&&Operation.RECEIPT_POSTED==result.operation()
                &&a.getRequisitionLineId().equals(result.requisitionLineId())&&a.getOrderLineId().equals(result.orderLineId())&&actor.equals(result.actorId())
                &&result.replayed()==replay&&decimal(result.occupiedBefore())&&decimal(result.occupiedAfter())
                &&result.occupiedBefore().subtract(result.occupiedAfter()).compareTo(line.quantityReceived())==0,"生命周期返回不是本行持久物理结果");
            results.add(new LineReceipt(line.lineId(),result));if(!result.replayed())versions.put(a.getAllocationKey(),Math.addExact(version,1));
        }
        require(head.equals(io(()->mapper.lockReceiptHead(tenant,receiveId)))&&received.equals(rows(io(()->mapper.lockReceiptLines(tenant,receiveId))).stream()
            .sorted(Comparator.comparing(ReceiveLine::lineId)).toList()),"批次末端原收货身份漂移");
        qualification.requireSourceChainTransaction();
        return readOnly&&!replay?Optional.empty():Optional.of(new BatchReceipt(tenant,receiveId,head.orderId(),true,results,replay));
    }
    private static void validateRequisitionReferences(OrderHead order,List<OrderLine> orders,List<RequisitionHead> heads,List<RequisitionLine> lines) {
        Map<Long,RequisitionHead> byHead=new HashMap<>();Map<Long,RequisitionLine> byLine=new HashMap<>();
        for(RequisitionHead h:heads)require(positive(h.requisitionId())&&byHead.putIfAbsent(h.requisitionId(),h)==null&&"0".equals(h.delFlag()),"原申请头身份重复或已删除");
        for(RequisitionLine l:lines)require(positive(l.lineId())&&byLine.putIfAbsent(l.lineId(),l)==null&&byHead.containsKey(l.requisitionId())&&"0".equals(l.delFlag()),"原申请行父链缺失/重复/已删除");
        require(order.requisitionId()==null||byHead.containsKey(order.requisitionId()),"订单原申请头丢失，不能猜历史归属");
        for(OrderLine l:orders)if(l.requisitionLineId()!=null) {
            RequisitionLine source=byLine.get(l.requisitionLineId());require(source!=null&&l.materialId().equals(source.materialId())
                &&(order.requisitionId()==null||order.requisitionId().equals(source.requisitionId())),"订单原申请行/父链未知");
        }
    }
    private Long validatePhysical(String tenant,Long receiveId,List<ReceiveLine> lines) {
        Map<Long,ReceiveLine> byId=new HashMap<>();lines.forEach(l->byId.put(l.lineId(),l));
        List<StockEffect> effects=rows(io(()->physical.lockStockEffects(tenant,"WM_MATERIAL_RECEIVE",receiveId)));
        require(!effects.isEmpty(),"认证收货没有持久原生库存结果");Set<Long> ids=new HashSet<>();Map<Long,BigDecimal> sums=new HashMap<>();Long actor=null;
        for(StockEffect e:effects) {
            ReceiveLine l=byId.get(e.sourceDocLineId());
            require(tenant.equals(e.tenantId())&&positive(e.txId())&&ids.add(e.txId())&&"RECEIVE_IN".equals(e.txType())&&Integer.valueOf(1).equals(e.txFlag())
                &&"WM_MATERIAL_RECEIVE".equals(e.sourceDocType())&&receiveId.equals(e.sourceDocId())&&positive(e.sourceDocLineId())&&l!=null
                &&l.materialId().equals(e.materialId())&&l.baseUomId().equals(e.baseUomId())&&positive(e.stockId())&&positive(e.actorId())&&"0".equals(e.delFlag())
                &&decimal(e.quantity())&&e.quantity().signum()>0&&decimal(e.beforeQuantity())&&decimal(e.afterQuantity())
                &&e.afterQuantity().subtract(e.beforeQuantity()).compareTo(e.quantity())==0,"原生逐行库存结果身份/单位/前后量不守恒");
            if(actor==null)actor=e.actorId();require(actor.equals(e.actorId()),"整张物理入账操作人不一致");sums.merge(l.lineId(),e.quantity(),BigDecimal::add);
        }
        require(sums.keySet().equals(byId.keySet()),"原生库存结果未完整覆盖收货行");
        for(ReceiveLine l:lines)require(decimal(sums.get(l.lineId()))&&l.quantityReceived().compareTo(sums.get(l.lineId()))==0,"全部原生物理入账量与收货行不守恒");
        return actor;
    }
    private static void validatePrior(String tenant,PuRequirementAllocationLedger a,ReceiveLine l,Long actor,String key,PuRequirementAllocationEvent e) {
        require(tenant.equals(e.getTenantId())&&positive(e.getEventId())&&a.getAllocationId().equals(e.getAllocationId())&&key.equals(e.getEventKey())
            &&"RECEIPT_POSTED".equals(e.getEventType())&&a.getRequisitionLineId().equals(e.getRequisitionLineId())&&a.getOrderLineId().equals(e.getOrderLineId())
            &&actor.equals(e.getActorId())&&e.getEventSequence()!=null&&e.getEventSequence()>0&&e.getEventSequence()<=a.getVersion()
            &&decimal(e.getOccupiedBaseBefore())&&decimal(e.getOccupiedBaseAfter())&&e.getOccupiedBaseBefore().subtract(e.getOccupiedBaseAfter()).compareTo(l.quantityReceived())==0,
            "永久收货事件与当前物理来源不一致");
    }
    private static String eventKey(String tenant,PuRequirementAllocationLedger a,Long id){return "LIFECYCLE:"+hash(tenant,a.getAllocationKey(),Operation.RECEIPT_POSTED.name(),id.toString());}
    private static boolean sourceHint(String type){return type!=null&&!"MANUAL".equals(type);}
    private static boolean digest(String hash){return hash!=null&&hash.matches("[0-9a-f]{64}");}
    private static <T> List<T> rows(List<T> rows){require(rows!=null&&rows.stream().allMatch(Objects::nonNull),"当前来源全集读取未知");return List.copyOf(rows);}
    private static <T> T io(Supplier<T> query){try{return query.get();}catch(RuntimeException failure){throw failed("持久来源访问失败，结果未确认");}}
    private static void require(boolean condition,String reason){if(!condition)throw failed(reason);}
    private static ServiceException failed(String reason){return new ServiceException("可信采购收货批次拒绝："+reason);}
}
