package com.nexus.erp.wm.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/** 全报废只留下完成证明，实际消耗、正式身份和证明均加入调用方ERP事务。 */
@Service @DS("erp") @RequiredArgsConstructor
public class WmTraceNoGoodOutputService {
    private final WmSerialInventoryRepository store;
    private final WmProductionRecptWriteGuard source;
    private final WmTraceMaterialConsumptionService consumption;
    private final PrdWorkOrderServiceImpl workOrders;
    public record Result(String eventId,boolean replay) { }

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Result post(CompletionManifest manifest,List<CompletionManifestLine> lines,List<CompletionMaterialAllocation> allocations) {
        store.requireWrite();WmSerialProductionReceiptService.validateManifest(manifest,lines);
        need(store.tenant().equals(manifest.tenantId()) && manifest.goodQty().signum()==0 && manifest.scrapQty().signum()>0
            && lines.stream().allMatch(l->"SCRAP".equals(l.result())),"零良品最终清单不完整");
        var wo=source.lockWorkOrder(manifest.erpWoId());
        need(wo.getMaterialId().equals(manifest.materialId()),"全报废物料不属于原ERP工单");
        String event="ERP-NO-GOOD-"+manifest.manifestId()+"-"+manifest.revision();
        String materialHash=CompletionAllocationIntegrity.hash(allocations);
        var original=store.jdbc.queryForList("SELECT * FROM wm_trace_outbox WHERE tenant_id=? AND event_id=? FOR UPDATE",store.tenant(),event);
        if(!original.isEmpty()) {
            need(original.size()==1 && Set.of("COMPLETED","CLOSED").contains(wo.getWoStatus())
                && wo.getCompletedQty()!=null && wo.getCompletedQty().signum()==0 && wo.getScrapQty()!=null
                && wo.getScrapQty().compareTo(manifest.scrapQty())==0 && source.receipts(wo).isEmpty(),"零良品原工单或无收货证明不符");
            var row=original.get(0);String payload=row.get("payload").toString();var proof=JSON.parseObject(payload);
            need(TraceHashes.sha256(payload).equals(row.get("content_hash")) && "NO_GOOD_OUTPUT".equals(proof.getString("outcome"))
                && manifest.contentHash().equals(proof.getString("manifestHash")) && materialHash.equals(proof.getString("materialHash"))
                && manifest.erpWoId().equals(proof.getLong("erpWoId")),"全报废原不可变完成证明不符");
            source.requireNoEffects(wo);consumption.verifyReplay(manifest,allocations);return new Result(event,true);
        }
        source.validateNoGoodOutput(wo,manifest);
        need(source.receipts(wo).isEmpty(),"工单已有收货单，不能把全报废伪作另一次完成");source.requireNoEffects(wo);
        postScrapOutputs(manifest,lines);
        workOrders.completeFromManifest(manifest);consumption.consume(manifest,allocations);
        Map<String,Object> proof=new LinkedHashMap<>();
        proof.put("outcome","NO_GOOD_OUTPUT");proof.put("manifestId",manifest.manifestId().toString());proof.put("revision",Integer.toString(manifest.revision()));
        proof.put("manifestHash",manifest.contentHash());proof.put("materialHash",materialHash);proof.put("erpWoId",wo.getWoId().toString());
        proof.put("mesWoId",manifest.mesWoId().toString());proof.put("materialId",manifest.materialId().toString());proof.put("goodQty","0");proof.put("scrapQty",manifest.scrapQty().toPlainString());
        proof.put("identityIds",lines.stream().filter(l->l.identityId()!=null).map(l->l.identityId().toString()).toList());
        proof.put("items",lines.stream().filter(l->l.identityId()!=null).map(l->Map.of("identityId",l.identityId().toString(),"identityVersion",Long.toString(store.identityVersion(l.identityId())))).toList());
        store.appendEvent(event,"COMPLETION_RESULT",manifest.manifestId(),1,"ERP_INVENTORY_POSTED",store.encode(proof));return new Result(event,false);
    }

    /** 混合良品/报废及全报废使用同一正式身份效果；重放只验证原证明，不重做状态变更。 */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void postScrapOutputs(CompletionManifest manifest,List<CompletionManifestLine> lines) {
        store.requireWrite();var wo=source.lockWorkOrder(manifest.erpWoId());
        need(store.tenant().equals(manifest.tenantId()) && wo.getMaterialId().equals(manifest.materialId()),"报废成品不属于原工单");
        var scrap=lines.stream().filter(l->"SCRAP".equals(l.result())).toList();if(scrap.isEmpty())return;
        var policy=store.policy(wo.getMaterialId());
        if(policy.stage()==TrackingStage.STRICT && policy.serialManaged())
            need(scrap.stream().allMatch(l->l.identityId()!=null && BigDecimal.ONE.compareTo(l.quantity())==0),"报废SN清单必须逐件");
        if(policy.stage()==TrackingStage.STRICT && policy.batchManaged()) {
            for(Long batch:scrap.stream().map(CompletionManifestLine::batchId).distinct().toList()) {
                need(batch!=null,"报废批次追踪产品缺少原生产批次");var target=store.productionLotTarget(wo.getWoId(),manifest.mesWoId(),batch);
                need(target!=null && target.getWarehouseId()!=null,"报废成品批次缺少原授权仓储");
                store.authorizedProductionBatchCode(wo.getWoId(),manifest.mesWoId(),batch,target.getWarehouseId());
            }
        }
        for(var line:scrap.stream().filter(l->l.identityId()!=null).sorted(Comparator.comparing(CompletionManifestLine::identityId)).toList()) {
            var identity=store.identity(line.identityId());
            need(identity!=null && wo.getMaterialId().equals(identity.materialId()) && Set.of("REGISTERED","CREATED","IN_PRODUCTION").contains(identity.status()),"报废正式身份已存在其他生命周期效果");
            need(store.stocks(line.identityId()).stream().allMatch(s->s.getStockQuantity()!=null && s.getStockQuantity().signum()==0
                && (s.getReservedQuantity()==null || s.getReservedQuantity().signum()==0)),"报废产品SN已有在库余额，不能隐藏库存");
            WmSerialInventoryRepository.one(store.jdbc.update("UPDATE wm_sn SET status='SCRAPPED',identity_version=identity_version+1 WHERE tenant_id=? AND sn_id=? AND material_id=? AND status=? AND del_flag='0'",
                store.tenant(),line.identityId(),wo.getMaterialId(),identity.status()));
        }
    }
}
