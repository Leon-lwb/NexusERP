package com.nexus.erp.wm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper.*;
import com.nexus.system.strategy.AutoCodeUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

/** 批准认证订单到原生收货草稿的真实写入口；不跳过审批、批次分配、IQC或库存过账。 */
@Service
public class WmCertifiedReceiptDraftService {
    public record Request(String requestKey,Long orderId,Integer expectedOrderVersion,Long purchaseLineId,BigDecimal quantity,
        Long warehouseId,Long areaId,Long locationId,LocalDate actualArrivalDate,String remark,Long noticeId,Long noticeLineId) {
        /** 兼容无通知调用，旧规范摘要不增加任何空字段。 */
        public Request(String requestKey,Long orderId,Integer expectedOrderVersion,Long purchaseLineId,BigDecimal quantity,
            Long warehouseId,Long areaId,Long locationId,LocalDate actualArrivalDate,String remark){this(requestKey,orderId,expectedOrderVersion,purchaseLineId,quantity,warehouseId,areaId,locationId,actualArrivalDate,remark,null,null);}
    }
    public record Result(Long receiveId,String receiveCode,Long lineId,Long purchaseLineId,Long materialId,String baseUomId,
        BigDecimal quantity,String status,boolean replayed,String requestHash) { }
    private final WmCertifiedReceiptPostingAuthorizer auth;
    private final PuRequirementErpTransactionExecutor tx;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseOrderWorkflowService orders;
    private final WmCertifiedReceiptDraftMapper mapper;
    private final AutoCodeUtil codes;
    @Autowired
    private WmCertifiedArrivalNoticeService arrivals;

    public WmCertifiedReceiptDraftService(WmCertifiedReceiptPostingAuthorizer auth,PuRequirementErpTransactionExecutor tx,
        PuRequirementErpTransactionQualification qualification,PuRequirementMaterialLockService locks,
        PuCertifiedPurchaseOrderWorkflowService orders,WmCertifiedReceiptDraftMapper mapper,AutoCodeUtil codes) {
        this.auth=auth;this.tx=tx;this.qualification=qualification;this.locks=locks;this.orders=orders;this.mapper=mapper;this.codes=codes;
    }
    public Result create(Request request) {
        validate(request);DecisionActor actor=actor();String hash=hash(actor,request);
        // 先恢复原提交；只有确无永久请求回执时才在ERP事务外保留正常编号，失败允许编号空洞。
        Result old=tx.executeRepeatableRead(actor.tenantId(),()->prepare(actor,request,hash,null));
        if(old!=null)return old;
        String code=codes.genSerialCode("WM_MATERIAL_RECEIVE_CODE",null);
        require(code!=null&&code.matches("[A-Za-z0-9_-]{1,64}"),"收货编号规则未返回有效编号");
        return tx.executeRepeatableRead(actor.tenantId(),()->prepare(actor,request,hash,code));
    }
    private Result prepare(DecisionActor actor,Request request,String hash,String code) {
        DecisionActor current=actor();require(actor.tenantId().equals(current.tenantId())&&actor.userId().equals(current.userId()),"请求身份已变化");
        qualification.requireSourceChainTransaction();
        Long material=mapper.findMaterial(actor.tenantId(),request.orderId(),request.purchaseLineId());require(positive(material),"明确采购订单行不存在");
        locks.lockExisting(actor.tenantId(),List.of(material));
        mapper.pinDefinitions();List<String> engines=mapper.schemaEngines();
        require(engines!=null&&engines.size()==5&&engines.stream().allMatch("InnoDB"::equals),"收货与永久请求结构非事务表或缺失");
        require(List.of("tenant_id","request_key").equals(mapper.commandUniqueColumns())&&List.of("decimal(18,4)").equals(mapper.nativeQuantityTypes()),"永久请求唯一键或原生数量结构不完整");
        Command discovered=mapper.findCommand(actor.tenantId(),request.requestKey());
        if(discovered!=null){
            orders.requireApprovedReplayLocked(actor.tenantId(),request.orderId());
            Command old=mapper.lockCommand(actor.tenantId(),request.requestKey());
            require(old!=null,"已发现的永久请求当前读丢失");
            require(old.requestHash().equals(hash)&&old.actorId().equals(actor.userId())&&old.orderId().equals(request.orderId())&&old.purchaseLineId().equals(request.purchaseLineId())&&old.materialId().equals(material),"请求键已被不同来源或载荷使用");
            require(Objects.equals(old.noticeId(),request.noticeId())&&Objects.equals(old.noticeLineId(),request.noticeLineId()),"原通知永久绑定不一致");
            if(old.noticeId()!=null){require(arrivals!=null,"缺少认证通知接线");arrivals.requireBindingLocked(actor.tenantId(),old.noticeId(),old.noticeLineId(),old.orderId(),old.purchaseLineId(),old.materialId(),old.baseUomId(),old.quantity(),true);}
            require(old.equals(mapper.readReceipt(old)),"永久请求引用的原收货行已丢失或漂移");
            qualification.requireSourceChainTransaction();return result(old,true);
        }
        orders.requireApprovedLocked(actor.tenantId(),request.orderId());
        OrderSource source=mapper.lockSource(actor.tenantId(),request.orderId(),request.purchaseLineId());
        require(source!=null&&actor.tenantId().equals(source.tenantId())&&material.equals(source.materialId())&&request.orderId().equals(source.orderId())&&request.purchaseLineId().equals(source.lineId())&&request.expectedOrderVersion().equals(source.version())&&source.version()>0&&positive(source.vendorId())&&positive(source.baseUomId())&&"0".equals(source.headDelFlag())&&"0".equals(source.lineDelFlag())&&"1".equals(source.materialStatus())&&"0".equals(source.materialDelFlag()),"批准订单行身份、状态、版本或原生单位不符");
        require(Set.of("APPROVED","PARTIALLY_RECEIVED","PARTIAL_RECEIVED").contains(source.status()),"订单不在可接收状态");
        RootUnit unit=mapper.lockRootUnit(actor.tenantId(),source.baseUomId());
        require(unit!=null&&source.baseUomId().equals(unit.unitId())&&unit.unitId().equals(unit.primaryId())&&Long.valueOf(1).equals(unit.isBase())&&unit.conversionRate()!=null&&BigDecimal.ONE.compareTo(unit.conversionRate())==0&&"1".equals(unit.status())&&"0".equals(unit.delFlag())&&unit.verifiedAt()!=null&&unit.verifiedAt()>0&&unit.conversionVersion()!=null&&!unit.conversionVersion().isBlank()&&Objects.equals(unit.quantityScale(),source.quantityScale()),"当前基本单位停用、删除、换算或正式版本不符");
        require(source.quantityScale()!=null&&source.quantityScale()>=0&&source.quantityScale()<=4&&request.quantity().stripTrailingZeros().scale()<=source.quantityScale(),"实际数量不符合原生单位精度，不能舍入");
        require(quantity(source.orderQuantity())&&source.receivedQuantity()!=null&&source.receivedQuantity().signum()>=0&&source.receivedQuantity().compareTo(source.orderQuantity())<=0,"原订单或已收数量未知");
        List<Pending> receipts=mapper.lockPending(actor.tenantId(),source.lineId());require(receipts!=null,"原收货承诺读取缺失");
        BigDecimal pending=BigDecimal.ZERO,posted=BigDecimal.ZERO;
        for(Pending receipt:receipts){
            require(receipt!=null&&quantity(receipt.quantity())&&"0".equals(receipt.headDelFlag())&&"0".equals(receipt.lineDelFlag())&&Set.of("DRAFT","APPROVING","REJECTED","APPROVED","SHELVED","FINISHED").contains(receipt.status()),"旧收货有删除、未知或未证明取消状态");
            if("FINISHED".equals(receipt.status()))posted=posted.add(receipt.quantity());else pending=pending.add(receipt.quantity());
        }
        // 已过账但订单异步回写尚未消费时也占用可收余额，不能因消息延迟重复建草稿。
        require(request.quantity().add(pending).add(posted.max(source.receivedQuantity())).compareTo(source.orderQuantity())<=0,"实际量及既有收货承诺超过订单可收余额");
        Storage storage=mapper.lockStorage(actor.tenantId(),request.warehouseId(),request.areaId(),request.locationId());
        require(storage!=null&&actor.tenantId().equals(storage.tenantId())&&request.warehouseId().equals(storage.warehouseId())&&request.areaId().equals(storage.areaId())&&request.locationId().equals(storage.locationId())&&"1".equals(storage.warehouseStatus())&&"0".equals(storage.warehouseDelFlag())&&"1".equals(storage.areaEnabled())&&"0".equals(storage.areaFrozen())&&"1".equals(storage.locationEnabled())&&"0".equals(storage.locationFrozen()),"仓库库区储位父链未启用或已冻结");
        require(!request.actualArrivalDate().isAfter(LocalDate.parse(mapper.databaseDate())),"实际到货日期不能位于未来");
        String noticeCode=null;
        if(request.noticeId()!=null){require(arrivals!=null,"缺少认证通知接线");var n=arrivals.requireBindingLocked(actor.tenantId(),request.noticeId(),request.noticeLineId(),source.orderId(),source.lineId(),material,source.baseUomId().toString(),request.quantity(),false);noticeCode=n.noticeCode();}
        if(code==null)return null;
        DecisionActor beforeWrite=actor();require(actor.userId().equals(beforeWrite.userId())&&actor.tenantId().equals(beforeWrite.tenantId()),"持锁等待期间操作人已变化");
        Command command=new Command(actor.tenantId(),request.requestKey(),hash,actor.userId(),source.orderId(),source.lineId(),IdWorker.getId(),IdWorker.getId(),code,material,source.baseUomId().toString(),request.quantity(),request.actualArrivalDate().toString(),storage.warehouseId(),storage.areaId(),storage.locationId(),request.noticeId(),request.noticeLineId());
        require(mapper.insertHead(command,source,storage,request.remark(),noticeCode)==1&&mapper.insertLine(command,source,storage)==1&&mapper.insertCommand(command)==1,"收货头行或永久请求落账失败");
        qualification.requireSourceChainTransaction();DecisionActor afterWrite=actor();require(actor.userId().equals(afterWrite.userId())&&actor.tenantId().equals(afterWrite.tenantId()),"提交前操作人已变化");return result(command,false);
    }
    private DecisionActor actor(){DecisionActor actor=auth.requireCreate();require(actor!=null&&TenantHelper.isEnable()&&actor.tenantId()!=null&&actor.tenantId().matches("[0-9]{6,20}")&&actor.tenantId().equals(TenantHelper.getTenantId())&&positive(actor.userId())&&actor.permissions().contains("wm:receive:add"),"当前租户或收货创建权限不一致");return actor;}
    private static Result result(Command c,boolean replay){return new Result(c.receiveId(),c.receiveCode(),c.lineId(),c.purchaseLineId(),c.materialId(),c.baseUomId(),c.quantity(),"DRAFT_CREATED",replay,c.requestHash());}
    private static void validate(Request r){require(r!=null&&(r.noticeId()==null&&r.noticeLineId()==null||positive(r.noticeId())&&positive(r.noticeLineId())),"通知引用必须成对且为真实正ID");require(r!=null&&r.requestKey()!=null&&r.requestKey().matches("[A-Za-z0-9:_-]{1,128}")&&positive(r.orderId())&&r.expectedOrderVersion()!=null&&r.expectedOrderVersion()>0&&positive(r.purchaseLineId())&&quantity(r.quantity())&&positive(r.warehouseId())&&positive(r.areaId())&&positive(r.locationId())&&r.actualArrivalDate()!=null&&r.actualArrivalDate().getYear()>=1000&&r.actualArrivalDate().getYear()<=9999&&(r.remark()==null||r.remark().length()<=500),"明确订单行、精确数量、仓储、日期或请求键无效");}
    private static boolean quantity(BigDecimal n){if(n==null||n.signum()<=0)return false;var v=n.stripTrailingZeros();return v.scale()<=4&&(long)v.precision()-v.scale()<=14;}
    private static boolean positive(Long n){return n!=null&&n>0;}
    private static void require(boolean value,String reason){if(!value)throw new ServiceException("认证收货创建拒绝："+reason);}

    private static String hash(DecisionActor a, Request r) {
        try {
            var h = MessageDigest.getInstance("SHA-256");
            for (String s :
                    List.of(
                            a.tenantId(),
                            a.userId().toString(),
                            r.requestKey(),
                            r.orderId().toString(),
                            r.expectedOrderVersion().toString(),
                            r.purchaseLineId().toString(),
                            r.quantity().stripTrailingZeros().toPlainString(),
                            r.warehouseId().toString(),
                            r.areaId().toString(),
                            r.locationId().toString(),
                            r.actualArrivalDate().toString(),
                            Objects.toString(r.remark(), ""))) {
                byte[] b = s.getBytes(StandardCharsets.UTF_8);
                h.update(ByteBuffer.allocate(4).putInt(b.length).array());
                h.update(b);
            }
            if (r.noticeId() != null) {
                for (String s :
                        List.of(
                                "certified-notice-v1",
                                r.noticeId().toString(),
                                r.noticeLineId().toString())) {
                    byte[] b = s.getBytes(StandardCharsets.UTF_8);
                    h.update(ByteBuffer.allocate(4).putInt(b.length).array());
                    h.update(b);
                }
            }
            return HexFormat.of().formatHex(h.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("请求摘要算法不可用");
        }
    }
}
