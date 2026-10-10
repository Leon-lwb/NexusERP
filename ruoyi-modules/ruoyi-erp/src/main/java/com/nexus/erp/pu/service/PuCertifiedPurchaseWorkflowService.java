package com.nexus.erp.pu.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWorkflowMapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/**
 * 整申请事务编排：全部来源先验证，头一次CAS，真实订单/所有行/账本/事件同一个ERP RR提交。
 * 不调用旧跨master Flowable；只支持没有 select_flow_id 的直接ERP状态机。
 * 转单创建的是未定价DRAFT订单，不伪造价格、预计到货承诺、批准或库存供给。
 * 本类/公开方法不能加 @Transactional 或 @DS，编号保留必须在专属ERP回调之外。
 */
@Service
public class PuCertifiedPurchaseWorkflowService {
    public record Result(Long requisitionId,String requisitionNo,String status,Integer version,Long orderId,String orderNo,
        boolean replayed,List<EventReceipt> allocations) {public Result {allocations=List.copyOf(allocations);}}
    /** 当前查询主体与按钮权限提示，不是来源认证、业务写授权或已加载构建证明。 */
    public record WorkflowContext(String tenantId, Long userId, boolean approveAllowed, boolean convertAllowed) { }

    /**
     * 以独立查询权限读取当前有效主体，不要求创建、批准或转单权限。
     * <p>复用实际写路径的租户及操作人元数据边界；不获取业务连接、不查询来源、不调用状态机。
     * 返回的权限布尔值仅供页面提示，实际写入仍在原操作入口重新检查权限与完整来源。</p>
     *
     * @return 当前主体及批准、转单按钮提示
     */
    public WorkflowContext currentWorkflowContext() {
        StpUtil.checkLogin();
        StpUtil.checkPermission("pu:requisition:query");
        Actor current = new Actor(TenantHelper.getTenantId(), LoginHelper.getUserId(),
            LoginHelper.getUsername(), LoginHelper.getDeptId());
        recheckActor(current);
        return new WorkflowContext(current.tenantId(), current.userId(),
            StpUtil.hasPermission("pu:requisition:approve"), StpUtil.hasPermission("pu:requisition:convert"));
    }

    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseWorkflowMapper mapper;
    private final PuCertifiedPurchaseLifecycleMapper sources;
    private final PuCertifiedPurchaseLifecycleService lifecycle;
    private final PuCertifiedPurchaseWorkflowNumberReservation numbers;
    private final ErpPurchaseSourceCertificationService certification;
    public PuCertifiedPurchaseWorkflowService(PuRequirementErpTransactionExecutor executor,
        PuRequirementErpTransactionQualification qualification,PuRequirementMaterialLockService locks,
        PuCertifiedPurchaseWorkflowMapper mapper,PuCertifiedPurchaseLifecycleMapper sources,
        PuCertifiedPurchaseLifecycleService lifecycle,PuCertifiedPurchaseWorkflowNumberReservation numbers,
        ErpPurchaseSourceCertificationService certification) {
        this.executor=Objects.requireNonNull(executor);this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks);this.mapper=Objects.requireNonNull(mapper);this.sources=Objects.requireNonNull(sources);
        this.lifecycle=Objects.requireNonNull(lifecycle);this.numbers=Objects.requireNonNull(numbers);this.certification=Objects.requireNonNull(certification);
    }
    public Result approve(Long id,Integer version) {return change(id,version,Operation.APPROVE,"APPROVED");}
    public Result close(Long id,Integer version) {return change(id,version,Operation.CLOSE_REQUISITION,"CLOSED");}
    public Result cancel(Long id,Integer version) {return change(id,version,Operation.CANCEL_REQUISITION,"CANCELLED");}

    private Result change(Long id,Integer version,Operation operation,String terminal) {
        Actor actor=actor(operation);validateRequest(id,version);
        return executor.executeRepeatableRead(actor.tenantId(),()-> {
            Document d=load(actor,id);boolean replay=terminal.equals(d.head().status());
            require(d.orders().isEmpty()&&d.rows().stream().allMatch(r->r.getOrderLineId()==null),"已转订单的申请不能在申请入口释放/修改");
            if(!replay) {
                require(d.head().version().equals(version),"申请头版本已变化");
                require(operation==Operation.APPROVE?"DRAFT".equals(d.head().status()):Set.of("DRAFT","APPROVED").contains(d.head().status()),"整单状态转换暂不支持");
                requireIntents(d);
                if(operation==Operation.APPROVE) require(d.lines().stream().allMatch(l->decimal(l.approvedQuantity())&&l.approvedQuantity().signum()==0),"部分批准/未知批准量不支持");
            }
            preflight(d,actor,operation);
            if(!replay&&operation==Operation.APPROVE) requireExistingCapacityAndCatalog(d,actor);
            if(!replay) {
                casHead(d,actor,terminal);
                if(operation==Operation.APPROVE) for(RequisitionLine line:d.lines())
                    require(io(()->mapper.approveLine(actor.tenantId(),line,actor.userId()))==1,"整单批准数量CAS失败");
            }
            List<EventReceipt> events=new ArrayList<>();
            for(PuRequirementAllocationLedger row:d.rows()) events.add(lifecycle.applyLocked(command(d,actor,row,operation,null)));
            qualification.requireSourceChainTransaction();
            return result(d,replay?d.head().version():d.head().version()+1,terminal,null,events,replay);
        });
    }

    public Result convert(Long id,Integer version) {
        Actor actor=actor(Operation.TRANSFER_TO_ORDER);validateRequest(id,version);
        Result old=executor.executeRepeatableRead(actor.tenantId(),()-> {
            Document d=load(actor,id);
            if("ORDERED".equals(d.head().status())) return recoverOrder(d,actor);
            requireConvertible(d,actor,version);return null;
        });
        if(old!=null) return old;
        String number=io(numbers::reserveOrderNumber);
        require(number!=null&&number.matches("[A-Za-z0-9_-]{1,64}"),"服务端订单编号无效");
        return executor.executeRepeatableRead(actor.tenantId(),()-> {
            Document d=load(actor,id);
            if("ORDERED".equals(d.head().status())) return recoverOrder(d,actor);
            requireConvertible(d,actor,version);
            Vendor vendor=io(()->mapper.lockVendor(actor.tenantId(),d.source().vendorId()));
            // 供应商只接受原始 ACTIVE 状态，不将数字、大小写或空白变体当作启用。
            require(vendor!=null&&actor.tenantId().equals(vendor.tenantId())&&d.source().vendorId().equals(vendor.vendorId())
                &&"ACTIVE".equals(vendor.status())&&"0".equals(vendor.delFlag())&&text(vendor.vendorCode(),64)&&text(vendor.vendorName(),128),"真实供应商来源未知");
            casHead(d,actor,"ORDERED");
            Long orderId=IdWorker.getId();
            require(io(()->mapper.insertOrder(actor.tenantId(),orderId,number,d.head(),vendor,actor.userId(),actor.name(),actor.deptId()))==1,"真实订单头写入失败");
            Map<Long,Long> orderLines=new HashMap<>();
            for(RequisitionLine line:d.lines()) {
                Long lineId=IdWorker.getId();
                require(io(()->mapper.insertOrderLine(actor.tenantId(),lineId,orderId,line,actor.userId(),actor.deptId()))==1,"真实订单行写入失败");
                orderLines.put(line.lineId(),lineId);
            }
            List<EventReceipt> events=new ArrayList<>();
            for(PuRequirementAllocationLedger row:d.rows()) events.add(lifecycle.applyLocked(command(d,actor,row,Operation.TRANSFER_TO_ORDER,orderLines.get(row.getRequisitionLineId()))));
            qualification.requireSourceChainTransaction();
            PurchaseOrder order=new PurchaseOrder(actor.tenantId(),orderId,d.head().requisitionId(),number,"DRAFT","0",vendor.vendorId());
            return result(d,d.head().version()+1,"ORDERED",order,events,false);
        });
    }

    private void requireConvertible(Document d,Actor actor,Integer version) {
        require("APPROVED".equals(d.head().status())&&d.head().version().equals(version)&&d.orders().isEmpty(),"申请未批准/版本变化/已有真实订单");
        requireIntents(d);
        require(d.lines().stream().allMatch(l->decimal(l.approvedQuantity())&&l.applyQuantity().compareTo(l.approvedQuantity())==0),"逐行批准量与冻结量不一致");
        // TRANSFER line IDs do not yet exist. APPROVE permission is not required merely for conversion preflight.
        for(PuRequirementAllocationLedger row:d.rows()) lifecycle.validateForWorkflowLocked(command(d,actor,row,Operation.TRANSFER_TO_ORDER,null));
        requireExistingCapacityAndCatalog(d,actor);
    }

    /**
     * Revalidate all current competition/stock/reservations and every still-occupied ledger before a new
     * approval/transfer. Own commitments remain in the global pool: C > current uncovered must reject.
     * A successful scope may contain zero newly-requestable lines; this never releases or reoccupies C.
     */
    private void requireExistingCapacityAndCatalog(Document d,Actor actor) {
        qualification.requireSourceChainTransaction();
        require(d.source().catalogFingerprint()!=null&&d.source().catalogFingerprint().matches("[0-9a-f]{64}")
            &&d.source().windowEnd()!=null,"缺少冻结目录合同/原始需求窗口，必须重新审批");
        LocalDate end;
        try {end=LocalDate.parse(d.source().windowEnd());}
        catch(RuntimeException invalid) {throw failed("原始冻结需求窗口未知，必须重新审批");}
        require(end.getYear()>=1000&&end.getYear()<=9999,"原始冻结需求窗口越界");
        Set<Long> details=new HashSet<>();
        for(RequisitionLine line:d.lines()) require(positive(line.sourceId())&&details.add(line.sourceId()),"整单全局复核MRP集合未知/重复");
        // Trusted service reads its own facts under this same ERP guard/RR; caller quantities/flags are not supplied.
        var scope=certification.prepareLocked(actor.tenantId(),d.source().materialId(),d.source().vendorId(),end,Set.copyOf(details));
        require(scope!=null&&actor.tenantId().equals(scope.tenantId())&&d.source().materialId().equals(scope.materialId())
            &&d.source().vendorId().equals(scope.vendorId())&&end.equals(scope.windowEnd())&&scope.catalog()!=null,
            "当前全局占额认证作用域与冻结整单不一致");
        String current=ErpPurchaseSourceCertificationService.catalogFingerprint(scope.catalog());
        require(d.source().catalogFingerprint().equals(current),"当前目录与冻结合同不一致，必须重新审批，不能继承旧条款");
        qualification.requireSourceChainTransaction();
    }

    private Result recoverOrder(Document d,Actor actor) {
        require(d.orders().size()==1,"已转申请没有唯一真实订单");PurchaseOrder order=d.orders().get(0);
        require(order!=null&&actor.tenantId().equals(order.tenantId())&&d.head().requisitionId().equals(order.requisitionId())
            &&d.source().vendorId().equals(order.vendorId())&&positive(order.orderId())&&text(order.orderNo(),64)&&"0".equals(order.delFlag()),"重复转单真实订单归属不一致");
        List<OrderLine> lines=io(()->sources.lockOrderLines(actor.tenantId(),order.orderId()));
        require(lines!=null&&lines.size()==d.rows().size()&&lines.stream().allMatch(Objects::nonNull),"重复转单真实订单行不完整");
        Map<Long,OrderLine> byReq=new HashMap<>();Set<Long> ids=new HashSet<>();
        for(OrderLine line:lines) require(actor.tenantId().equals(line.tenantId())&&order.orderId().equals(line.orderId())&&positive(line.lineId())
            &&ids.add(line.lineId())&&positive(line.requisitionLineId())&&byReq.putIfAbsent(line.requisitionLineId(),line)==null&&"0".equals(line.delFlag()),"重复转单实际行归属冲突");
        List<EventReceipt> events=new ArrayList<>();
        for(PuRequirementAllocationLedger row:d.rows()) {
            OrderLine line=byReq.get(row.getRequisitionLineId());
            require(line!=null&&line.lineId().equals(row.getOrderLineId())&&row.getBaseUomId().toString().equals(line.unit())
                &&row.getMaterialId().equals(line.materialId())&&decimal(line.orderQuantity())&&row.getCoveredDemandBaseQty().compareTo(line.orderQuantity())==0,"重复转单不可证明原始同源数量");
            Command command=command(d,actor,row,Operation.TRANSFER_TO_ORDER,line.lineId());
            lifecycle.validateForWorkflowLocked(command);events.add(lifecycle.applyLocked(command));
        }
        qualification.requireSourceChainTransaction();return result(d,d.head().version(),"ORDERED",order,events,true);
    }

    private Document load(Actor actor,Long id) {
        recheckActor(actor);qualification.requireSourceChainTransaction();
        Long material=io(()->mapper.findMaterial(actor.tenantId(),id));require(positive(material),"申请缺少永久可信command，不能猜来源");
        Map<Long,Integer> guard=io(()->locks.lock(actor.tenantId(),List.of(material)));
        require(guard!=null&&guard.get(material)!=null&&guard.get(material)>=0,"整单物料guard未核验");
        Head head=io(()->mapper.lockHead(actor.tenantId(),id));
        require(head!=null&&actor.tenantId().equals(head.tenantId())&&id.equals(head.requisitionId())&&text(head.requisitionNo(),64)
            &&text(head.requestKey(),128)&&text(head.sourceType(),32)&&head.version()!=null&&head.version()>=0&&"0".equals(head.delFlag()),"整单真实申请头未知");
        require(head.selectFlowId()==null,"配置旧Flowable的申请不支持直接ERP审批/状态转换");
        CommandSource source=io(()->sources.lockCommand(actor.tenantId(),head.requestKey()));
        require(source!=null&&actor.tenantId().equals(source.tenantId())&&head.requestKey().equals(source.requestKey())
            &&id.equals(source.requisitionId())&&head.requisitionNo().equals(source.requisitionNo())&&material.equals(source.materialId())
            &&head.sourceType().equals(source.entryType())&&positive(source.vendorId())&&positive(source.actorId())&&"COMMITTED".equals(source.commandStatus()),"整单永久command状态/归属不一致");
        List<RequisitionLine> lines=io(()->sources.lockRequisitionLines(actor.tenantId(),id));
        List<PuRequirementAllocationLedger> rows=io(()->sources.lockCommandAllocations(actor.tenantId(),head.requestKey()));
        require(lines!=null&&!lines.isEmpty()&&lines.size()<=1000&&lines.stream().allMatch(Objects::nonNull)
            &&rows!=null&&rows.size()==lines.size()&&rows.stream().allMatch(Objects::nonNull),"整单逐来源覆盖不完整");
        Map<Long,RequisitionLine> byId=new HashMap<>();Set<Long> details=new HashSet<>();Set<String> keys=new HashSet<>();
        for(RequisitionLine line:lines) require(actor.tenantId().equals(line.tenantId())&&id.equals(line.requisitionId())&&material.equals(line.materialId())
            &&positive(line.lineId())&&byId.putIfAbsent(line.lineId(),line)==null&&positive(line.sourceId())&&details.add(line.sourceId())
            &&"MRP".equals(line.sourceType())&&"0".equals(line.delFlag())&&decimal(line.applyQuantity())&&line.applyQuantity().signum()>0,"整单MRP申请行身份/数量未知");
        for(PuRequirementAllocationLedger row:rows) {
            RequisitionLine line=byId.get(row.getRequisitionLineId());
            require(line!=null&&actor.tenantId().equals(row.getTenantId())&&material.equals(row.getMaterialId())&&positive(row.getAllocationId())
                &&head.requestKey().equals(row.getDecisionIdempotencyKey())&&text(row.getAllocationKey(),128)&&keys.add(row.getAllocationKey())
                &&"MRP_DETAIL".equals(row.getSourceType())&&Integer.valueOf(1).equals(row.getAllocationNo())&&positive(row.getBaseUomId())
                &&line.sourceId().equals(row.getDetailId())&&row.getBaseUomId().toString().equals(line.unit())&&row.getVersion()!=null
                &&row.getVersion()>=0&&row.getVersion()<Integer.MAX_VALUE&&decimal(row.getCoveredDemandBaseQty())
                &&line.applyQuantity().compareTo(row.getCoveredDemandBaseQty())==0,"整单账本/根单位/版本/冻结量不一致");
        }
        require(rows.stream().map(PuRequirementAllocationLedger::getRequisitionLineId).distinct().count()==lines.size(),"整单缺少某来源行的账本");
        List<PurchaseOrder> orders=io(()->mapper.lockOrders(actor.tenantId(),id));require(orders!=null,"实际下游订单读取未知");
        return new Document(head,source,List.copyOf(lines),List.copyOf(rows),List.copyOf(orders));
    }
    private void preflight(Document d,Actor actor,Operation operation) {
        for(PuRequirementAllocationLedger row:d.rows()) lifecycle.validateForWorkflowLocked(command(d,actor,row,operation,null));
    }
    private static void requireIntents(Document d) {
        require(d.rows().stream().allMatch(r->"REQUEST_INTENT".equals(r.getAllocationStatus())&&r.getOrderLineId()==null
            &&decimal(r.getOccupiedBaseQty())&&r.getCoveredDemandBaseQty().compareTo(r.getOccupiedBaseQty())==0
            &&decimal(r.getFulfilledBaseQty())&&r.getFulfilledBaseQty().signum()==0&&decimal(r.getReleasedBaseQty())&&r.getReleasedBaseQty().signum()==0),"整单存在已转移/履约/释放来源");
    }
    private void casHead(Document d,Actor actor,String next) {
        require(io(()->mapper.casHead(actor.tenantId(),d.head().requisitionId(),d.head().requestKey(),d.head().status(),next,d.head().version(),actor.userId()))==1,"整单头状态/版本CAS失败");
    }
    private static Command command(Document d,Actor actor,PuRequirementAllocationLedger row,Operation op,Long businessLine) {
        return new Command(actor.tenantId(),actor.userId(),row.getMaterialId(),row.getAllocationKey(),d.head().requestKey(),row.getVersion(),op,businessLine);
    }
    private static Result result(Document d,Integer version,String state,PurchaseOrder order,List<EventReceipt> events,boolean replay) {
        return new Result(d.head().requisitionId(),d.head().requisitionNo(),state,version,order==null?null:order.orderId(),order==null?null:order.orderNo(),replay,events);
    }
    private static Actor actor(Operation operation) {
        String permission=switch(operation) {case APPROVE -> "pu:requisition:approve";case TRANSFER_TO_ORDER -> "pu:requisition:convert";
            case CLOSE_REQUISITION -> "pu:requisition:close";case CANCEL_REQUISITION -> "pu:requisition:cancel";default -> throw failed("整单操作未支持");};
        StpUtil.checkLogin();StpUtil.checkPermission(permission);
        Actor actor=new Actor(TenantHelper.getTenantId(),LoginHelper.getUserId(),LoginHelper.getUsername(),LoginHelper.getDeptId());
        recheckActor(actor);return actor;
    }
    private static void recheckActor(Actor actor) {
        require(TenantHelper.isEnable()&&text(actor.tenantId(),20)&&actor.tenantId().equals(TenantHelper.getTenantId())
            &&actor.tenantId().equals(LoginHelper.getTenantId())&&positive(actor.userId())&&actor.userId().equals(LoginHelper.getUserId())
            &&text(actor.name(),64)&&(actor.deptId()==null||positive(actor.deptId())),"整单当前tenant/actor/meta未知");
    }
    private static void validateRequest(Long id,Integer version) {require(positive(id)&&version!=null&&version>=0&&version<Integer.MAX_VALUE,"整单ID/期望头版本无效");}
    private static <T> T io(Supplier<T> call) {try{return call.get();}catch(RuntimeException failure){throw failed("整单持久来源读写失败");}}
    private static void require(boolean condition,String message) {if(!condition)throw failed(message);}
    private static ServiceException failed(String message) {return new ServiceException("可信采购整单拒绝："+message);}
    private record Actor(String tenantId,Long userId,String name,Long deptId) { }
    private record Document(Head head,CommandSource source,List<RequisitionLine> lines,List<PuRequirementAllocationLedger> rows,List<PurchaseOrder> orders) { }
}
