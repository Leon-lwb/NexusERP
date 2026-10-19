package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 冻结入口先核对历史命令；只有新命令才以当前真实来源执行版本CAS。 */
@Service @DS("erp") @RequiredArgsConstructor
public class WmSerialSelectionService {
    private final WmSerialInventoryRepository repository;
    private final WmSerialInventoryService inventory;

    @Transactional(rollbackFor=Exception.class)
    public Long freeze(WmSerialSelectionBo bo) {
        repository.requireWrite();
        need(bo!=null && bo.getSourceDocType()!=null && bo.getSourceDocId()!=null && bo.getLineId()!=null
            && bo.getSourceDocId()>0 && bo.getLineId()>=0 && bo.getCommandId()!=null
            && !bo.getCommandId().isBlank() && bo.getCommandId().length()<=128,"冻结命令或原始来源无效");
        Long replay=replay(bo);
        if(replay!=null)return replay;
        var source=repository.request(bo.getSourceDocType(),bo.getSourceDocId(),bo.getLineId());
        String current=repository.sourceHash(source);
        if(bo.getDetailVersion()==null || !current.equals(bo.getDetailVersion()))throw new ServiceException("单据明细版本已改变，请重新读取服务端详情");
        return inventory.freeze(source,bo.getCommandId(),bo.getRevision(),bo.getPolicyVersion(),bo.getSnItems());
    }

    /** 查询异常绝不能视作新命令；固定一行探针使不存在的命令返回空快照。 */
    private Long replay(WmSerialSelectionBo bo) {
        String tenant=repository.tenant();
        var row=repository.row("SELECT frozen.* FROM wm_trace_posting_group frozen RIGHT JOIN (SELECT ? AS tenant_id,? AS command_id) wanted ON frozen.tenant_id=wanted.tenant_id AND frozen.command_id=wanted.command_id FOR UPDATE",tenant,bo.getCommandId());
        if(row==null || row.get("posting_group_id")==null)return null;
        need(Objects.equals(tenant,row.get("tenant_id")) && Objects.equals(bo.getCommandId(),row.get("command_id"))
            && Objects.equals(bo.getSourceDocType(),row.get("source_doc_type")) && Objects.equals(bo.getSourceDocId(),id(row.get("source_doc_id")))
            && Objects.equals(bo.getLineId(),id(row.get("source_doc_line_id"))),"同一命令绑定的原始来源不同");
        Long revision=id(row.get("detail_version")),policy=id(row.get("policy_version")),groupId=id(row.get("posting_group_id")),material=id(row.get("material_id"));
        need(revision!=null && revision>0 && revision==bo.getRevision() && policy!=null && policy>0 && policy==bo.getPolicyVersion()
            && bo.getDetailVersion()!=null && !bo.getDetailVersion().isBlank() && Objects.equals(bo.getDetailVersion(),row.get("source_hash")),"同一命令的原版本、策略或冻结快照不一致");
        List<SnItem> selected=bo.getSnItems(),frozen=repository.items(groupId);
        need(validItems(selected) && validItems(frozen),"原命令SN明细缺失、重复或无效");
        String hash=inventory.selectionHash(selected);
        need(Objects.equals(hash,row.get("content_hash")) && hash.equals(inventory.selectionHash(frozen)),"同一命令SN内容或原冻结明细不一致");
        BigDecimal quantity=decimal(row.get("quantity")),amount=decimal(row.get("amount"));
        String tx=Objects.toString(row.get("tx_type"),"");
        need(groupId>0 && material!=null && material>0 && quantity!=null && quantity.compareTo(BigDecimal.valueOf(selected.size()))==0
            && amount!=null && amount.signum()>=0 && !tx.isBlank(),"原命令数量、物料或结果快照不完整");
        // 方向属于原请求指纹；不读取当前盘点差异、领料量或当前库存来重建它。
        List<WmSerialPostingRequest> matching=new ArrayList<>();
        for(int direction:new int[]{-1,0,1}) {
            var candidate=new WmSerialPostingRequest(bo.getSourceDocType(),bo.getSourceDocId(),bo.getLineId(),tx,material,quantity,direction,null,null,null,null,null,null,null,null,null,BigDecimal.ZERO);
            if(Objects.equals(inventory.fingerprint(candidate,policy,hash),row.get("fingerprint")))matching.add(candidate);
        }
        need(matching.size()==1,"原命令请求指纹损坏或与原冻结结果不符");
        String status=Objects.toString(row.get("status"),"");need(Set.of("PREPARED","POSTED").contains(status),"原命令冻结状态不能重放");
        if("POSTED".equals(status)) {
            var source=originalReservation(matching.get(0),groupId);
            var group=new WmSerialInventoryRepository.Group(groupId,bo.getCommandId(),revision,Objects.toString(row.get("fingerprint")),hash,quantity,amount,status,id(row.get("valuation_group_id")));
            repository.verifyProof(source,group,frozen);
        }
        return groupId;
    }

    /** 销售预留来源也来自原组不可变流水，不能从当前订单/预留余额猜测。 */
    private WmSerialPostingRequest originalReservation(WmSerialPostingRequest source,Long groupId) {
        if(!"WM_SALES_ISSUE".equals(source.sourceType()))return source;
        var rows=repository.jdbc.queryForList("SELECT source_doc_id,source_doc_line_id FROM wm_material_stock_tx WHERE tenant_id=? AND posting_group_id=? AND tx_type='CONSUME_RESERVE' AND source_doc_type='SALES_ORDER' ORDER BY tx_id FOR UPDATE",repository.tenant(),groupId);
        if(rows.isEmpty())return source;
        Long order=id(rows.get(0).get("source_doc_id")),line=id(rows.get(0).get("source_doc_line_id"));
        need(order!=null && order>0 && line!=null && line>0 && rows.stream().allMatch(row->Objects.equals(order,id(row.get("source_doc_id"))) && Objects.equals(line,id(row.get("source_doc_line_id")))),"原销售预留消费证明来源不一致");
        return source.reservation(order,line,BigDecimal.valueOf(rows.size()));
    }
    private static boolean validItems(List<SnItem> items) {
        return items!=null && !items.isEmpty() && items.stream().allMatch(item->item!=null && item.identityId()!=null && item.identityId()>0)
            && items.stream().map(SnItem::identityId).distinct().count()==items.size();
    }
    private static Long id(Object value) {return value==null?null:Long.valueOf(value.toString());}
    private static BigDecimal decimal(Object value) {return value==null?null:new BigDecimal(value.toString());}
    private static void need(boolean valid,String message) {if(!valid)throw new ServiceException(message);}

    @Transactional(rollbackFor=Exception.class)
    public WmSerialInventoryRepository.Snapshot snapshot(String type,Long source,Long line) {
        return repository.snapshot(type,source,line);
    }
}
