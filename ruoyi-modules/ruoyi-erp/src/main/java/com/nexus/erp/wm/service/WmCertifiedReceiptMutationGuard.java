package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Set;

/** 永久过账事实不可通过旧 CRUD/工作流重绑、撤销或删除。当前头锁串行化旧写与原生过账。 */
@Service
@DS("erp")
@Transactional(propagation=Propagation.MANDATORY)
public class WmCertifiedReceiptMutationGuard {
    private final WmCertifiedReceiptPostingMapper mapper;
    public WmCertifiedReceiptMutationGuard(WmCertifiedReceiptPostingMapper mapper) {this.mapper=Objects.requireNonNull(mapper);}
    /** 永久草稿承诺没有释放审计前不可删除；正常提交和审批仍只调用requireUnposted。 */
    public void requireDeletable(Long receiveId) {String t=tenant();mutableHead(t,receiveId,false);need(!frozenDraft(t,receiveId));}
    /** 普通整单编辑允许备注/上架资料，但原采购行、物料、单位、承诺量及完整行集冻结。 */
    public void requireFrozenDraftLines(Long receiveId,java.util.List<com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo> requested) {
        String t=tenant();mutableHead(t,receiveId,false);if(!frozenDraft(t,receiveId))return;
        var original=read(()->mapper.readFrozenDraftLines(t,receiveId));need(original!=null&&!original.isEmpty()&&requested!=null&&requested.size()==original.size());
        var byId=new java.util.HashMap<Long,WmCertifiedReceiptPostingMapper.FrozenDraftLine>();
        for(var fact:original){need(fact!=null&&positive(fact.lineId())&&byId.putIfAbsent(fact.lineId(),fact)==null);}
        var seen=new java.util.HashSet<Long>();
        for(var line:requested){need(line!=null&&positive(line.getLineId())&&seen.add(line.getLineId()));var fact=byId.get(line.getLineId());
            need(fact!=null&&Objects.equals(fact.materialId(),line.getMaterialId())&&Objects.equals(fact.baseUomId(),line.getBaseUomId())&&fact.quantity()!=null&&line.getQuantityReceived()!=null&&fact.quantity().compareTo(line.getQuantityReceived())==0);}
    }
    private boolean frozenDraft(String t,Long id){return "CERTIFIED_DRAFT_V1".equals(read(()->mapper.lockCreationKind(t,id)));}
    public void requireUnposted(Long receiveId) { mutableHead(tenant(),receiveId,false); }
    public void requireMutableLineParent(Long receiveId) {mutableHead(tenant(),receiveId,true);}
    public void requireMutableLine(Long lineId,Long proposedReceiveId) {
        String tenant=tenant();need(positive(lineId));SourceIdentity old=read(()->mapper.discoverLineIdentity(tenant,lineId));validate(tenant,old);
        mutableHead(tenant,old.receiveId(),true);SourceIdentity current=read(()->mapper.lockLineIdentity(tenant,lineId));
        need(old.equals(current) && (proposedReceiveId==null || current.receiveId().equals(proposedReceiveId)));
    }
    public void requireMutableDetail(Long detailId,Long proposedReceiveId,Long proposedLineId) {
        String tenant=tenant();need(positive(detailId));SourceIdentity old=read(()->mapper.discoverDetailIdentity(tenant,detailId));validate(tenant,old);
        mutableHead(tenant,old.receiveId(),true,false);SourceIdentity current=read(()->mapper.lockDetailIdentity(tenant,detailId));
        need(old.equals(current) && (proposedReceiveId==null || current.receiveId().equals(proposedReceiveId))
            && (proposedLineId==null || current.lineId().equals(proposedLineId)));
    }
    public void requireMutableDetailParent(Long receiveId,Long lineId) {
        String tenant=tenant();mutableHead(tenant,receiveId,true,false);need(positive(lineId));
        SourceIdentity line=read(()->mapper.lockLineIdentity(tenant,lineId));validate(tenant,line);need(receiveId.equals(line.receiveId()));
    }
    public void requireLegacyExecution(Long receiveId) {
        String tenant=tenant();Head head=mutableHead(tenant,receiveId,false);
        if(head.orderId()!=null) {
            var ids=read(()->mapper.lockCertifiedOrderIdentities(tenant,head.orderId()));need(ids!=null);
            if(!ids.isEmpty()) throw new ServiceException("认证采购收货必须通过精确入库入口，不允许旧虚拟库转储");
        }
    }
    private Head mutableHead(String tenant,Long id,boolean editable) {
        return mutableHead(tenant,id,editable,true);
    }
    private Head mutableHead(String tenant,Long id,boolean editable,boolean protectBirth) {
        need(positive(id));Head head=read(()->mapper.lockHead(tenant,id));
        need(head!=null && tenant.equals(head.tenantId()) && id.equals(head.receiveId()) && "0".equals(head.delFlag()));
        need(read(()->mapper.lockPosting(tenant,id))==null);
        if(protectBirth) { var born=read(()->mapper.lockBirthIdentities(tenant,id));need(born!=null && born.isEmpty()); }
        if(editable) {need(Set.of("DRAFT","REJECTED","APPROVED").contains(head.status()));if(protectBirth)need(!frozenDraft(tenant,id));}
        return head;
    }
    private static String tenant() {String tenant=TenantHelper.getTenantId();need(TenantHelper.isEnable() && tenant!=null && tenant.matches("[0-9]{6,20}"));return tenant;}
    private static void validate(String tenant,SourceIdentity identity){need(identity!=null && tenant.equals(identity.tenantId()) && positive(identity.receiveId()) && positive(identity.lineId()));}
    private static boolean positive(Long id){return id!=null && id>0;}
    private static void need(boolean ok){if(!ok)throw new ServiceException("收货来源不可修改：原归属未知、已冻结过账或状态不允许");}
    private static <T> T read(java.util.function.Supplier<T> call){try{return call.get();}catch(RuntimeException failure){throw new ServiceException("收货来源保护读取失败，禁止修改");}}
}
