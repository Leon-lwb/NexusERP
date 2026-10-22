package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;
import com.nexus.erp.wm.mapper.WmCertifiedArrivalNoticeMapper;
import com.nexus.erp.wm.mapper.WmCertifiedArrivalNoticeMapper.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.service.impl.WmServiceHelper;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.WfCallBackServiceI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 认证订单独立单行通知。永久创建命令证明来源，标签/历史通知不能升级。
 * 编排不加 DS/事务注解；ERP 写入沿用独占执行器，不参与 master 事务。
 * 工作流回调在 master 提交后写 ERP；不声称跨库原子，失败从真实流程重同步。
 */
@Service("wmCertifiedArrivalNoticeService")
public class WmCertifiedArrivalNoticeService implements WfCallBackServiceI {
    public record Request(String requestKey,Long orderId,Integer expectedOrderVersion,Long purchaseLineId,
        BigDecimal quantity,String noticeName,LocalDate arrivalDate,String remark) { }
    public record Result(Long noticeId,String noticeCode,Long noticeLineId,String status,boolean replayed) { }
    private final WmCertifiedArrivalNoticeMapper mapper;
    private final WmCertifiedReceiptDraftMapper receipts;
    private final PuRequirementErpTransactionExecutor tx;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseOrderWorkflowService orders;
    private final WmCertifiedReceiptPostingAuthorizer auth;
    private final WmServiceHelper wm;
    @Autowired
    private WmCertifiedArrivalWorkflowPort workflow;

    public WmCertifiedArrivalNoticeService(WmCertifiedArrivalNoticeMapper mapper,WmCertifiedReceiptDraftMapper receipts,
        PuRequirementErpTransactionExecutor tx,PuRequirementErpTransactionQualification qualification,
        PuRequirementMaterialLockService locks,PuCertifiedPurchaseOrderWorkflowService orders,
        WmCertifiedReceiptPostingAuthorizer auth,WmServiceHelper wm) {
        this.mapper=mapper;this.receipts=receipts;this.tx=tx;this.qualification=qualification;
        this.locks=locks;this.orders=orders;this.auth=auth;this.wm=wm;
    }
    public Result create(Request r) {
        need(r!=null&&text(r.requestKey(),128)&&r.requestKey().matches("[A-Za-z0-9:_-]+")&&positive(r.orderId())
            &&positive(r.purchaseLineId())&&r.expectedOrderVersion()!=null&&r.expectedOrderVersion()>0
            &&text(r.noticeName(),255)&&r.arrivalDate()!=null&&r.arrivalDate().getYear()>=1000&&r.arrivalDate().getYear()<=9999
            &&(r.remark()==null||r.remark().length()<=500),"通知明确来源或参数无效");
        quantity(r.quantity());DecisionActor actor=auth.requireArrivalCreate();tenant(actor.tenantId());
        need(positive(actor.userId())&&actor.permissions().contains("wm:arrivalnotice:add"),"缺少通知创建权限");
        String hash=hash(actor,r);
        Result old=tx.executeRepeatableRead(actor.tenantId(),()->createLocked(actor,r,hash,null));if(old!=null)return old;
        String code=wm.autoCodeUtil.genSerialCode("WM_ARRIVAL_NOTICE_CODE",null);
        need(code!=null&&code.matches("[A-Za-z0-9_-]{1,64}"),"正常编号规则未返回有效编号");
        return tx.executeRepeatableRead(actor.tenantId(),()->createLocked(actor,r,hash,code));
    }
    private Result createLocked(DecisionActor actor,Request r,String hash,String code) {
        qualification.requireSourceChainTransaction();String t=actor.tenantId();
        Long m=receipts.findMaterial(t,r.orderId(),r.purchaseLineId());need(positive(m),"原采购行不存在");locks.lockExisting(t,List.of(m));
        requireStructure();exactNative(r.quantity());Creation old=mapper.findCommand(t,r.requestKey());
        if(old!=null){
            orders.requireApprovedReplayLocked(t,r.orderId());
            need(t.equals(old.tenantId())&&hash.equals(old.requestHash())&&actor.userId().equals(old.actorId()),"请求键来源/载荷不一致");
            Notice n=readNotice(t,old.noticeId());need(old.noticeLineId().equals(n.noticeLineId())&&old.noticeCode().equals(n.noticeCode())
                &&old.orderId().equals(n.orderId())&&old.purchaseLineId().equals(n.purchaseLineId())&&old.materialId().equals(n.materialId())
                &&old.vendorId().equals(n.vendorId())&&old.baseUomId().equals(n.baseUomId())&&old.quantity().compareTo(n.quantity())==0,"永久通知事实漂移");
            return new Result(n.noticeId(),n.noticeCode(),n.noticeLineId(),n.status(),true);
        }
        orders.requireApprovedLocked(t,r.orderId());var s=source(t,r.orderId(),r.purchaseLineId());
        need(r.expectedOrderVersion().equals(s.version())&&m.equals(s.materialId())&&r.quantity().stripTrailingZeros().scale()<=s.quantityScale(),"批准订单版本/物料/原单位精度不一致");
        need(s.orderQuantity()!=null&&s.receivedQuantity()!=null&&s.receivedQuantity().signum()>=0
            &&r.quantity().add(s.receivedQuantity()).compareTo(s.orderQuantity())<=0,"通知超过原订单未收数量");
        if(code==null)return null;
        DecisionActor now=auth.requireArrivalCreate();need(actor.userId().equals(now.userId())&&t.equals(now.tenantId()),"持锁等待期间通知操作人已变化");
        Creation c=new Creation(t,r.requestKey(),hash,actor.userId(),s.orderId(),s.lineId(),s.version(),IdWorker.getId(),IdWorker.getId(),
            code,s.vendorId(),s.materialId(),s.baseUomId().toString(),r.quantity(),r.arrivalDate().toString(),r.noticeName(),r.remark());
        need(mapper.insertHead(c,s)==1&&mapper.insertLine(c,s)==1&&mapper.insertCommand(c)==1,"通知头/行/永久命令写入失败");
        qualification.requireSourceChainTransaction();return new Result(c.noticeId(),c.noticeCode(),c.noticeLineId(),"DRAFT",false);
    }
    /** 正常 Flowable 提交要求真实审批任务，不产生普通收货。 */
    public boolean submit(Long id,String flowId) {
        DecisionActor actor=auth.requireArrivalSubmit();String t=actor.tenantId();tenant(t);need(text(flowId,64),"必须选择实际审批流程");
        WmArrivalNoticeVo n =
                tx.executeRepeatableRead(
                        t,
                        () -> {
                            Notice value = lockQualified(t, id, false);
                            need(
                                    Set.of("DRAFT", "REJECTED").contains(value.status()),
                                    "只允许通知草稿/驳回提交");
                            return viewLocked(t, id);
                        });
        Map<String,Object> variables=new HashMap<>();variables.put("dataId",id.toString());variables.put("noticeId",id);
        variables.put("noticeCode",n.getNoticeCode());variables.put("noticeName",n.getNoticeName());
        variables.put("vendorId",n.getVendorId());variables.put("vendorName",n.getVendorName());variables.put("arrivalDate",n.getArrivalDate());variables.put("requireTaskOnStart",true);
        R<Void> result=wm.processService.startProcessByDataId(id.toString(),flowId,"wmCertifiedArrivalNoticeService",variables);
        need(R.isSuccess(result),"通知正常流程提交失败");return true;
    }
    /** 从本通知实际实例重同步；关联行的 actStatus 不再是批准事实。 */
    public boolean synchronize(Long id) {
        DecisionActor actor=auth.requireArrivalSubmit();String t=actor.tenantId();tenant(t);need(positive(id),"通知身份无效");
        fresh(t,()->{applyVerifiedWorkflow(t,id,null,null,null);return null;});return true;
    }
    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        need(business!=null&&business.getDataId()!=null&&"wmCertifiedArrivalNoticeService".equals(business.getServiceImplName())
            &&text(business.getProcessInstanceId(),128)&&text(business.getProcessDefinitionId(),128),"回调缺少实例/业务关联");
        String t=TenantHelper.getTenantId();tenant(t);Long id=Long.valueOf(business.getDataId());need(positive(id),"回调通知身份无效");
        // 事件只作为待核线索，复制以免提交后的可变对象漂移，绝不直接投影其 actStatus。
        String process=business.getProcessInstanceId(),definition=business.getProcessDefinitionId(),reported=business.getActStatus();
        Runnable apply=()->fresh(t,()->{applyVerifiedWorkflow(t,id,process,definition,reported);return null;});
        if(TransactionSynchronizationManager.isActualTransactionActive()){
            need(TransactionSynchronizationManager.isSynchronizationActive(),"master 回调缺少提交同步");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){apply.run();}});
        }else apply.run();
    }
    private void applyVerifiedWorkflow(String t,Long id,String expectedProcess,String expectedDefinition,String reported){
        need(workflow!=null,"缺少实际流程只读接线");
        // 先完成独立 master 只读快照，资源清理后才启动原 ERP 独占事务，不在 ERP 中切 master。
        var proof=workflow.read(t,id);
        need(proof!=null&&t.equals(proof.tenantId())&&id.toString().equals(proof.dataId())&&id.toString().equals(proof.sourceDataId())
            &&id.toString().equals(proof.sourceNoticeId())&&text(proof.processInstanceId(),128)&&text(proof.processDefinitionId(),128),"实际流程源身份不一致");
        if(expectedProcess!=null)need(expectedProcess.equals(proof.processInstanceId())&&expectedDefinition.equals(proof.processDefinitionId()),"回调来自另一实例/定义");
        // 错配最终待办可能把业务关联写成 pass，目标自身尚运行时必须拒绝，不改变通知。
        if(ActStatus.pass.equals(reported))need("APPROVED".equals(proof.nextStatus()),"事件 pass 不是本通知实际完成批准");
        tx.executeRepeatableRead(t,()->{
            Notice n=lockQualified(t,id,false);need(n.noticeCode().equals(proof.businessCode()),"本通知编号与实际流程 businessCode 不一致");
            String next=proof.nextStatus();need(Set.of("DRAFT","APPROVING","APPROVED","REJECTED").contains(next),"未知实际流程状态");
            need(Set.of("DRAFT","APPROVING","APPROVED","REJECTED").contains(n.status()),"已收通知不能倒退审批状态");
            // I1：只有真实自身实例完整证明后，才允许丢失 doing 的 DRAFT 收敛到 APPROVED。
            if("APPROVED".equals(next))need(Set.of("DRAFT","APPROVING","APPROVED").contains(n.status()),"本地状态不可收敛批准");
            if("APPROVING".equals(next))need(!"APPROVED".equals(n.status()),"实际处理中状态不能倒退已批准通知");
            if(!next.equals(n.status()))need(mapper.updateStatus(t,id,next)==1,"通知实际审批状态写入失败");
            qualification.requireSourceChainTransaction();return null;
        });
    }

    /** 同一 ERP 独占事务内校验成对原引用；重放不重新占用通知余额。 */
    public Notice requireBindingLocked(String t,Long id,Long line,Long order,Long purchase,Long material,String unit,BigDecimal q,boolean replay) {
        Notice n=lockQualified(t,id,replay);quantity(q);exactNative(q);
        need(Objects.equals(line,n.noticeLineId())&&Objects.equals(order,n.orderId())&&Objects.equals(purchase,n.purchaseLineId())
            &&Objects.equals(material,n.materialId())&&Objects.equals(unit,n.baseUomId()),"通知原采购行/物料/单位与收货不一致");
        need((replay?Set.of("APPROVED","PARTIAL_RECEIVED","FINISHED"):Set.of("APPROVED","PARTIAL_RECEIVED")).contains(n.status()),"通知未批准或已关闭");
        if(!replay){BigDecimal pending=BigDecimal.ZERO;List<BigDecimal> commitments=mapper.pendingQuantities(t,id,line);
            need(commitments!=null,"既有通知收货承诺未知");for(BigDecimal v:commitments){quantity(v);exactNative(v);pending=pending.add(v);}
            need(n.receivedQty().add(pending).add(q).compareTo(n.quantity())<=0,"通知已收及未完成承诺超过声明量");}
        return n;
    }
    /** 过账核对头/行/永久请求引用；动态已收/状态不进入原来源指纹。 */
    public String receiptFingerprintLocked(String t,WmCertifiedReceiptPostingMapper.Head h,List<WmCertifiedReceiptPostingMapper.Line> lines,boolean replay) {
        StringBuilder proof=new StringBuilder();
        for(var l:lines){
            need(h.noticeId()!=null&&l.noticeLineId()!=null,"通知头/行引用不成对");
            Notice n=lockQualified(t,h.noticeId(),replay);exactNative(l.quantity());
            need(l.noticeLineId().equals(n.noticeLineId())&&h.orderId().equals(n.orderId())&&l.purchaseLineId().equals(n.purchaseLineId())
                &&h.vendorId().equals(n.vendorId())&&l.materialId().equals(n.materialId())&&l.baseUomId().equals(n.baseUomId())
                &&Objects.equals(h.noticeCode(),n.noticeCode()),"过账通知原引用漂移");
            need((replay?Set.of("APPROVED","PARTIAL_RECEIVED","FINISHED"):Set.of("APPROVED","PARTIAL_RECEIVED")).contains(n.status()),"通知不可接收");
            if(!replay)need(n.receivedQty().add(l.quantity()).compareTo(n.quantity())<=0,"实际收货超过通知声明量");
            String hash=mapper.receiptHash(t,h.receiveId(),l.lineId(),h.orderId(),l.purchaseLineId(),l.materialId(),l.baseUomId(),l.quantity(),h.noticeId(),l.noticeLineId());
            need(hash!=null&&hash.matches("[0-9a-f]{64}"),"缺少认证收货通知永久绑定");
            proof.append("notice-v1|").append(n.noticeId()).append('|').append(n.noticeLineId()).append('|').append(n.quantity().stripTrailingZeros().toPlainString()).append('|').append(hash).append('\n');
        }
        return proof.toString();
    }
    /** 仅实际首次过账调用；与库存/批次/永久 posting 同事务，任一失败全部回滚。 */
    public void receiveLocked(String t,Long id,Long line,BigDecimal q) {
        qualification.requireSourceChainTransaction();requireStructure();Notice n=readNotice(t,id);quantity(q);exactNative(q);
        need(Objects.equals(line,n.noticeLineId())&&Set.of("APPROVED","PARTIAL_RECEIVED").contains(n.status())&&n.receivedQty().add(q).compareTo(n.quantity())<=0,"通知累计不可执行");
        need(mapper.addReceived(t,line,q)==1,"通知累计条件更新失败");
        List<Notice> all=mapper.lockNoticeLines(t,id);need(all!=null&&!all.isEmpty()&&Objects.equals(mapper.lineCount(t,id),(long)all.size()),"通知行全集未知");
        boolean full=true;for(Notice v:all){validateNotice(t,id,v);if(v.receivedQty().compareTo(v.quantity())<0)full=false;}
        need(mapper.updateStatus(t,id,full?"FINISHED":"PARTIAL_RECEIVED")==1,"通知关闭状态更新失败");
        qualification.requireSourceChainTransaction();
    }
    private Notice lockQualified(String t,Long id,boolean replay) {
        qualification.requireSourceChainTransaction();requireStructure();tenant(t);need(positive(id),"通知身份无效");
        // 先无锁发现物料，再按既有物料→采购→通知锁序当前读，避免与入库形成反向锁序。
        Notice hint=mapper.discoverNotice(t,id);validateNotice(t,id,hint);locks.lockExisting(t,List.of(hint.materialId()));
        if(replay)orders.requireApprovedReplayLocked(t,hint.orderId());else orders.requireApprovedLocked(t,hint.orderId());
        var s=source(t,hint.orderId(),hint.purchaseLineId());Notice n=readNotice(t,id);
        need(hint.orderId().equals(n.orderId())&&hint.purchaseLineId().equals(n.purchaseLineId())&&s.orderNo().equals(n.orderNo())
            &&s.vendorId().equals(n.vendorId())&&s.materialId().equals(n.materialId())&&s.baseUomId().toString().equals(n.baseUomId()),"通知与真实批准来源不一致");
        return n;
    }
    private WmCertifiedReceiptDraftMapper.OrderSource source(String t,Long order,Long line) {
        var s=receipts.lockSource(t,order,line);
        need(s!=null&&t.equals(s.tenantId())&&order.equals(s.orderId())&&line.equals(s.lineId())&&positive(s.vendorId())&&positive(s.materialId())&&positive(s.baseUomId())
            &&"0".equals(s.headDelFlag())&&"0".equals(s.lineDelFlag())&&"1".equals(s.materialStatus())&&"0".equals(s.materialDelFlag()),"真实采购行/供应商/物料未知");
        var u=receipts.lockRootUnit(t,s.baseUomId());
        need(u!=null&&s.baseUomId().equals(u.unitId())&&u.unitId().equals(u.primaryId())&&Long.valueOf(1).equals(u.isBase())&&u.conversionRate()!=null&&BigDecimal.ONE.compareTo(u.conversionRate())==0
            &&"1".equals(u.status())&&"0".equals(u.delFlag())&&u.verifiedAt()!=null&&u.verifiedAt()>0&&u.verifiedAt()<=System.currentTimeMillis()
            &&text(u.conversionVersion(),64)&&Objects.equals(u.quantityScale(),s.quantityScale())&&s.quantityScale()!=null&&s.quantityScale()>=0&&s.quantityScale()<=4,"原单位不是已核验恒等根");
        return s;
    }
    private Notice readNotice(String t,Long id){tenant(t);need(positive(id),"通知身份无效");Notice n=mapper.lockNotice(t,id);validateNotice(t,id,n);return n;}
    private void validateNotice(String t,Long id,Notice n){need(n!=null&&t.equals(n.tenantId())&&id.equals(n.noticeId())&&positive(n.noticeLineId())&&positive(n.orderId())&&positive(n.purchaseLineId())&&positive(n.materialId())&&positive(n.vendorId())&&text(n.baseUomId(),64)&&text(n.noticeCode(),64)&&"0".equals(n.headDeleted())&&"0".equals(n.lineDeleted()),"缺少通知永久创建来源");quantity(n.quantity());exactNative(n.quantity());need(n.receivedQty()!=null&&n.receivedQty().signum()>=0&&n.receivedQty().compareTo(n.quantity())<=0,"原通知已收量未知/超额");exactNative(n.receivedQty());}
    private void requireStructure(){mapper.pinDefinitions();var engines=mapper.tableEngines();need(engines!=null&&engines.size()==3&&engines.stream().allMatch("InnoDB"::equals),"通知/永久命令必须为事务表");}

    /** 不 CAST 浮点为伪精确 decimal；旧 DOUBLE 只接受范围内无损整数，小数明确拒绝。 */
    private void exactNative(BigDecimal q) {
        need(q != null, "通知数量为空");
        var columns = mapper.decimalColumns();
        need(
                columns != null
                        && columns.size() == 2
                        && columns.stream()
                                .map(DecimalColumn::columnName)
                                .collect(Collectors.toSet())
                                .equals(Set.of("quantity_arrival", "received_qty")),
                "数量列定义未知");
        BigDecimal v = q.stripTrailingZeros();
        for (var c : columns) {
            need(
                    c.numericPrecision() != null
                            && c.numericScale() != null
                            && c.numericPrecision() > c.numericScale()
                            && c.numericScale() >= 0,
                    "数量列精度未知");
            need(
                    (long) v.precision() - v.scale() <= c.numericPrecision() - c.numericScale(),
                    "通知数量整数位超容量");
            if ("decimal".equals(c.dataType()))
                need(v.scale() <= c.numericScale(), "数量无法无损存储，禁止取整/舍入");
            else
                need(
                        "double".equals(c.dataType())
                                && v.scale() <= 0
                                && v.abs().compareTo(new BigDecimal("9007199254740991")) <= 0,
                        "旧浮点通知列仅允许无损整数，请先审查精度迁移");
        }
    }

    private static void quantity(BigDecimal q){need(q!=null&&q.signum()>0&&q.stripTrailingZeros().scale()<=4&&(long)q.stripTrailingZeros().precision()-q.stripTrailingZeros().scale()<=14,"通知数量必须为原生精确正数");}
    private static void tenant(String t){need(t!=null&&t.matches("[0-9]{6,20}")&&TenantHelper.isEnable()&&t.equals(TenantHelper.getTenantId()),"通知租户不一致");}
    private static boolean positive(Long x){return x!=null&&x>0;}
    private static boolean text(String x,int max){return x!=null&&!x.isBlank()&&x.length()<=max;}
    private static ServiceException fail(String reason){return new ServiceException("认证到货拒绝："+reason);}
    private static void need(boolean ok,String reason){if(!ok)throw fail(reason);}

    private static String hash(DecisionActor a, Request r) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            for (String s :
                    List.of(
                            a.tenantId(),
                            a.userId().toString(),
                            r.requestKey(),
                            r.orderId().toString(),
                            r.expectedOrderVersion().toString(),
                            r.purchaseLineId().toString(),
                            r.quantity().stripTrailingZeros().toPlainString(),
                            r.noticeName(),
                            r.arrivalDate().toString(),
                            Objects.toString(r.remark(), ""))) {
                byte[] v = s.getBytes(StandardCharsets.UTF_8);
                d.update(ByteBuffer.allocate(4).putInt(v.length).array());
                d.update(v);
            }
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) {
            throw fail("摘要算法不可用");
        }
    }

    /** master 事务/路由不能靠 DS 伪装，另起上下文使用同一既有 ERP 独占执行器。 */
    private static <T> T fresh(String tenant,Supplier<T> job){if(TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isSynchronizationActive()||!TransactionSynchronizationManager.getResourceMap().isEmpty()||DynamicDataSourceContextHolder.peek()!=null)return CompletableFuture.supplyAsync(()->TenantHelper.dynamic(tenant,job)).join();return job.get();}
    @Override
    public Object getBusinessDataById(String id){String t=TenantHelper.getTenantId();return fresh(t,()->tx.executeRepeatableRead(t,()->{readNotice(t,Long.valueOf(id));return viewLocked(t,Long.valueOf(id));}));}

    /** 保留原业务头/行视图供既有审批表单使用，不另造不兼容的表单 DTO。 */
    private WmArrivalNoticeVo viewLocked(String t, Long id) {
        var view=mapper.viewHeader(t,id);need(view!=null&&id.equals(view.getNoticeId()),"原通知业务视图未知");
        var lines=mapper.viewLines(t,id);need(lines!=null&&!lines.isEmpty(),"原通知业务行视图未知");
        view.setWmArrivalNoticeLineList(lines);return view;
    }
    @Override
    public Map<String,Object> flowValuesOfTask(String key,Map<String,Object> values){return null;}
    @Override
    public List<String> flowCandidateUsernamesOfTask(String key,Map<String,Object> values){return null;}
}
