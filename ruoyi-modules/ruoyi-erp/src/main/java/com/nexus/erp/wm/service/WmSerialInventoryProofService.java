package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.wm.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/** v2历史证明适配器；任何混合版本、额外流水或缺项都失败，完全不读取SN当前余额。 */
@Service @DS("erp") @RequiredArgsConstructor
public class WmSerialInventoryProofService {
    private final WmSerialInventoryRepository store;
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Long verifyPosted(String sourceType,Long sourceId,Long lineId,String txType) {
        var r=store.proofRequest(sourceType,sourceId,lineId,txType);var g=store.group(r);
        need(g!=null && "POSTED".equals(g.status()),"单件过账组缺失或未完成");var items=store.items(g.id());
        need(!items.isEmpty() && r.quantity().compareTo(java.math.BigDecimal.valueOf(items.size()))==0,"单件原冻结明细不守恒");
        need(new WmSerialInventoryService(store).selectionHash(items).equals(g.contentHash()),"单件原冻结明细摘要不符");
        store.verifyProof(r,g,items);return g.id();
    }
    public boolean isV2(List<WmMaterialStockTx> transactions) {
        boolean any=transactions!=null && transactions.stream().anyMatch(t->t!=null && t.getTrackingVersion()!=null && t.getTrackingVersion()>=2);
        if(any)need(transactions.stream().allMatch(t->t!=null && Long.valueOf(2).equals(t.getTrackingVersion()) && t.getPostingGroupId()!=null),"生产入库存在混合旧版和单件证明");
        return any;
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void verifyProduction(WmProductionRecpt receipt,List<WmMaterialStockTx> txs) {
        need(isV2(txs),"不是v2生产入库证明");Long group=verifyPosted("PRD_WORK_ORDER",receipt.getWoId(),null,"PRODUCTION_IN");
        store.verifyProductionSource(receipt,group,txs);
    }
}
