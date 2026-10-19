package com.nexus.erp.wm.controller.serial;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 原单据的草稿 SN 与冻结桥接。草稿占用 revision=0；正式过账 revision 始终从1开始。 */
@Component @DS("erp") @RequiredArgsConstructor
public class WmSerialBusinessStore {
    private final JdbcTemplate jdbc;
    private final WmSerialInventoryRepository repository;
    private final WmSerialSelectionService selections;
    public record Draft(String command,List<SnItem> items) { }
    public record Detail(String detailVersion,String detailRevision,TrackingPolicy policy,List<Map<String,Object>> snItems,
                         String selectionStatus,List<Map<String,Object>> previousSnItems,Map<String,Object> submissionSnapshot) {
        public Detail(String version,String revision,TrackingPolicy policy,List<Map<String,Object>> items,String status,List<Map<String,Object>> previous) {
            this(version,revision,policy,items,status,previous,Map.of());
        }
    }
    public String tenant() {return repository.tenant();}
    public void requireWrite() {repository.requireWrite();}
    public Map<String,Object> head(SerialBusinessRoute route,Long id) {
        var row=repository.row("SELECT * FROM "+route.headTable()+" WHERE tenant_id=? AND "+route.headKey()+"=? FOR UPDATE",tenant(),id);
        if(row.containsKey("del_flag")&&!"0".equals(String.valueOf(row.get("del_flag"))))throw new ServiceException("原始来源已删除");return row;
    }
    public Long sourceId(SerialBusinessRoute route,Long id) {
        return route.type().equals("PRD_WORK_ORDER")?number(head(route,id).get("wo_id")):id;
    }
    public List<Long> lineIds(SerialBusinessRoute route,Long id) {
        if(route.whole())return List.of(0L);
        return jdbc.queryForList("SELECT line_id FROM "+route.lineTable()+" WHERE tenant_id=? AND "+route.headKey()+"=?"+(Set.of("WM_TRANSFER","WM_MATERIAL_RETURN","WM_SUBCONTRACT_ISSUE").contains(route.type())?"":" AND del_flag='0'")+" ORDER BY line_id FOR UPDATE",Long.class,tenant(),id);
    }
    public WmSerialInventoryRepository.Snapshot snapshot(String type,Long source,Long line) {return selections.snapshot(type,source,line);}
    public Draft draft(String type,Long source,Long line) {
        var rows=jdbc.queryForList("SELECT * FROM wm_document_sn WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND detail_version=0 AND posting_group_id IS NULL ORDER BY line_no FOR UPDATE",tenant(),type,source,line);
        if(rows.isEmpty())return new Draft(null,List.of());
        String command=String.valueOf(rows.get(0).get("command_id"));
        if(rows.stream().anyMatch(r->!command.equals(r.get("command_id"))))throw new ServiceException("草稿实物命令不一致");
        return new Draft(command,rows.stream().map(WmSerialBusinessStore::item).toList());
    }
    public boolean frozen(String type,Long source) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM wm_trace_posting_group WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=?",Long.class,tenant(),type,source);
        return count!=null&&count>0;
    }
    public void clearDrafts(String type,Long source) {
        requireWrite();jdbc.update("DELETE FROM wm_document_sn WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND detail_version=0 AND posting_group_id IS NULL",tenant(),type,source);
    }
    public void deleteDraft(String type,Long source,Long line) {
        requireWrite();jdbc.update("DELETE FROM wm_document_sn WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND detail_version=0 AND posting_group_id IS NULL",tenant(),type,source,line);
    }
    public void saveDraft(String type,Long source,Long line,String command,List<SnItem> items) {
        requireWrite();var snap=snapshot(type,source,line);
        jdbc.update("DELETE FROM wm_document_sn WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND detail_version=0 AND posting_group_id IS NULL",tenant(),type,source,line);
        if(items.isEmpty())return;
        if(!snap.policy().serialManaged())throw new ServiceException("普通数量物料不能绑定 SN 明细");
        // 来源行由 snapshot 锁定；物料从真实源行读取，不信任客户端 materialId。
        Long material=routeMaterial(type,source,line);
        String draftCommand="DRAFT/"+hash(snap.detailVersion()+"/policy/"+snap.policy().version())+"/"+hash(command+"/"+type+"/"+source+"/"+line).substring(0,32);
        int ordinal=0;Set<Long> seen=new HashSet<>();
        for(var item:items) {
            if(item==null||item.identityId()==null||item.identityId()<=0||!seen.add(item.identityId()))throw new ServiceException("SN身份缺失或重复");
            var identity=repository.row("SELECT sn_id,material_id,batch_id FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR UPDATE",tenant(),item.identityId());
            if(!Objects.equals(material,number(identity.get("material_id"))))throw new ServiceException("SN物料不属于原单据行");
            if(item.batchId()!=null&&!Objects.equals(item.batchId(),number(identity.get("batch_id"))))throw new ServiceException("SN批次不一致");
            jdbc.update("INSERT INTO wm_document_sn(document_sn_id,tenant_id,command_id,line_no,source_doc_type,source_doc_id,source_doc_line_id,detail_version,identity_id,material_id,batch_id,source_stock_id,target_location_id,original_tx_id,posting_group_id,quantity) VALUES(?,?,?,?,?,?,?,0,?,?,?,?,?,?,NULL,1)",
                IdWorker.getId(),tenant(),draftCommand,++ordinal,type,source,line,item.identityId(),material,item.batchId(),item.sourceStockId(),item.targetLocationId(),item.originalTxId());
        }
    }
    public Long routeMaterial(String type,Long source,Long line) {
        if(type.equals("PRD_WORK_ORDER"))return number(repository.row("SELECT material_id FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND del_flag='0' FOR UPDATE",tenant(),source).get("material_id"));
        var r=SerialBusinessRoute.ofType(type);
        String table=r.whole()?r.headTable():r.lineTable();String key=r.whole()?r.headKey():"line_id";
        return number(repository.row("SELECT * FROM "+table+" WHERE tenant_id=? AND "+key+"=?"+(r.whole()?"":" AND "+r.headKey()+"=?")+" FOR UPDATE",r.whole()?new Object[]{tenant(),source}:new Object[]{tenant(),line,source}).get("material_id"));
    }
    public record FrozenCommand(Long id,String command,String sourceHash,long revision,long policyVersion,String status,List<SnItem> items) { }
    static String commandBase(String root,String type,Long source,Long line) {
        return "BUS/"+hash(root+"/"+type+"/"+source+"/"+line).substring(0,32);
    }
    /** 先查认证租户和原命令，不能把同一来源最新组或当前余额当作历史命令。 */
    public FrozenCommand command(String type,Long source,Long line,String root,String requestFingerprint,List<SnItem> requested) {
        String base=commandBase(root,type,source,line);
        var rows=jdbc.queryForList("SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND (command_id=? OR command_id LIKE ?) FOR UPDATE",tenant(),base,base+"/%");
        if(rows.isEmpty())return null;
        if(rows.size()!=1)throw new ServiceException("同一命令存在多个冻结结果，禁止自动选择");
        var row=rows.get(0);
        if(!Objects.equals(type,row.get("source_doc_type"))||!Objects.equals(source,number(row.get("source_doc_id")))||!Objects.equals(line,number(row.get("source_doc_line_id"))))throw new ServiceException("同一命令绑定的原始来源不同");
        if(!Objects.equals(base+"/"+requestFingerprint,row.get("command_id")))throw new ServiceException("同一命令请求内容不一致，或历史命令缺少原请求指纹，不能自动重放");
        var items=repository.items(number(row.get("posting_group_id")));
        if(requested!=null&&!sameItems(requested,items))throw new ServiceException("同一命令SN内容不一致");
        String sourceHash=text(row.get("source_hash"));Long revision=number(row.get("detail_version")),policy=number(row.get("policy_version"));
        if(sourceHash==null||sourceHash.isBlank()||revision==null||revision<1||policy==null||policy<1)throw new ServiceException("原命令冻结快照不完整");
        return new FrozenCommand(number(row.get("posting_group_id")),text(row.get("command_id")),sourceHash,revision,policy,text(row.get("status")),items);
    }
    /** 交统一入口核验原命令及原证明；当前详情变化不应触发新的库存效果。 */
    public void verifyCommand(String type,Long source,Long line,FrozenCommand frozen) {
        WmSerialSelectionBo bo=new WmSerialSelectionBo();bo.setSourceDocType(type);bo.setSourceDocId(source);bo.setLineId(line);
        bo.setCommandId(frozen.command());bo.setDetailVersion(frozen.sourceHash());bo.setRevision(frozen.revision());bo.setPolicyVersion(frozen.policyVersion());bo.setSnItems(frozen.items());
        Long id=selections.freeze(bo);
        if(!Objects.equals(id,frozen.id()))throw new ServiceException("原命令返回了不同冻结结果");
    }
    /** 在原执行事务的实际过账边界消费草稿，未提供实物时由原STRICT守卫拒绝。 */
    public Long freeze(String type,Long source,Long line,String command) {
        return freeze(type,source,line,command,null);
    }
    public Long freeze(String type,Long source,Long line,String command,String requestFingerprint) {
        requireWrite();var snap=snapshot(type,source,line);
        if(!snap.policy().serialManaged()||snap.policy().stage()!=TrackingStage.STRICT)return null;
        if(type.equals("PRD_WORK_ORDER")) {
            verifyManifest(source,draft(type,source,line));deleteDraft(type,source,line);
            var original=repository.group(repository.request(type,source,line));return original==null?null:original.id();
        }
        var draft=draft(type,source,line);
        var request=repository.request(type,source,line);var group=repository.group(request);
        if(draft.items().isEmpty())return group==null?null:group.id();
        if(!draft.command().startsWith("DRAFT/"+hash(snap.detailVersion()+"/policy/"+snap.policy().version())+"/"))throw new ServiceException("草稿来源版本已改变，请重新读取并保存SN明细");
        long revision=snap.revision();String posting=command==null?"BUS/"+draft.command().substring(draft.command().lastIndexOf('/')+1):commandBase(command,type,source,line);
        if(command!=null&&requestFingerprint!=null)posting+="/"+requestFingerprint;
        if(group!=null && (!type.equals("WM_PRODUCTION_ISSUE")||!"POSTED".equals(group.status()))) {
            if(!sameItems(draft.items(),repository.items(group.id())))throw new ServiceException("已冻结实物不能修改，请另建来源单据");
            posting=group.commandId();revision=group.detailVersion();
        }
        WmSerialSelectionBo bo=new WmSerialSelectionBo();bo.setSourceDocType(type);bo.setSourceDocId(source);bo.setLineId(line);
        bo.setCommandId(posting);bo.setDetailVersion(snap.detailVersion());bo.setRevision(revision);bo.setPolicyVersion(snap.policy().version());bo.setSnItems(draft.items());
        Long frozenId=selections.freeze(bo);
        jdbc.update("DELETE FROM wm_document_sn WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND detail_version=0 AND posting_group_id IS NULL",tenant(),type,source,line);
        return frozenId;
    }
    /** 最终生产入库由既有完工manifest提供正式集合，客户端只可核验同一集合。 */
    public void verifyManifest(Long wo,Draft draft) {
        if(draft.items().isEmpty())return;
        var groups=jdbc.queryForList("SELECT posting_group_id FROM wm_trace_posting_group WHERE tenant_id=? AND source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND source_doc_line_id=0 AND status IN ('PREPARED','POSTED') ORDER BY detail_version DESC LIMIT 1 FOR UPDATE",tenant(),wo);
        if(groups.isEmpty())throw new ServiceException("生产入库缺少已冻结最终完工清单，不可用任意SN代造来源");
        var authorized=repository.items(number(groups.get(0).get("posting_group_id")));Map<Long,SnItem> expected=new HashMap<>();authorized.forEach(item->expected.put(item.identityId(),item));
        if(expected.size()!=draft.items().size())throw new ServiceException("生产入库SN必须与已持久化完工manifest一致");
        for(var item:draft.items()) {
            var original=expected.get(item.identityId());
            if(original==null || item.batchId()!=null&&!Objects.equals(item.batchId(),original.batchId())
                || item.targetLocationId()!=null&&!Objects.equals(item.targetLocationId(),original.targetLocationId())
                || item.sourceStockId()!=null&&!Objects.equals(item.sourceStockId(),original.sourceStockId())
                || item.originalTxId()!=null&&!Objects.equals(item.originalTxId(),original.originalTxId()))
                throw new ServiceException("生产入库SN必须与已持久化完工manifest一致");
        }
    }
    @Transactional(rollbackFor=Exception.class)
    public Detail detail(String type,Long source,Long line) {
        var snap=snapshot(type,source,line);var draft=draft(type,source,line);
        var groups=jdbc.queryForList("SELECT posting_group_id,status,command_id FROM wm_trace_posting_group WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? ORDER BY detail_version DESC LIMIT 1",tenant(),type,source,line);
        List<SnItem> previous=groups.isEmpty()?List.of():repository.items(number(groups.get(0).get("posting_group_id")));
        boolean next=type.equals("WM_PRODUCTION_ISSUE")&&!groups.isEmpty()&&"POSTED".equals(groups.get(0).get("status"));
        List<SnItem> current=!draft.items().isEmpty()?draft.items():next?List.of():previous;
        String status=!draft.items().isEmpty()?"DRAFT":groups.isEmpty()?"EMPTY":next?"NEXT_REVISION":String.valueOf(groups.get(0).get("status"));
        Map<String,Object> submitted=groups.isEmpty()||!"POSTED".equals(groups.get(0).get("status"))?Map.of():submission(text(groups.get(0).get("command_id")),List.of(number(groups.get(0).get("posting_group_id"))));
        return new Detail(snap.detailVersion(),Long.toString(snap.revision()),snap.policy(),display(current),status,next?display(previous):List.of(),submitted);
    }
    /** 只读原过账组和owner outbox凭据；缺事件与发送成功均不能视为跨库已确认。 */
    public Map<String,Object> submission(String command,Collection<Long> originalGroups) {
        List<Long> groups=originalGroups.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if(groups.isEmpty())return Map.of();
        List<String> warnings=new ArrayList<>();boolean anyEvent=false,allApplied=true;
        for(Long id:groups) {
            var posted=jdbc.queryForList("SELECT posting_group_id,status FROM wm_trace_posting_group WHERE tenant_id=? AND posting_group_id=?",tenant(),id);
            if(posted.size()!=1||!"POSTED".equals(posted.get(0).get("status")))return Map.of();
            var events=jdbc.queryForList("SELECT event_id,status,last_error FROM wm_trace_outbox WHERE tenant_id=? AND source_module='ERP' AND aggregate_type='STOCK_POSTING' AND aggregate_id=? ORDER BY aggregate_version",tenant(),id);
            if(events.isEmpty()) {allApplied=false;warnings.add("过账组 "+id+" 已在服务端提交，尚无跨模块事件确认凭据");continue;}
            anyEvent=true;
            for(var event:events)if(!"APPLIED".equals(event.get("status"))) {
                allApplied=false;String error=text(event.get("last_error"));
                warnings.add("过账组 "+id+" 事件 "+text(event.get("event_id"))+" 状态 "+text(event.get("status"))+(error==null||error.isBlank()?"":"："+error.substring(0,Math.min(error.length(),500))));
            }
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("submissionState",allApplied?"CONFIRMED":anyEvent?"CROSS_MODULE_PENDING":"SERVER_COMMITTED");
        result.put("commandId",command);result.put("postingGroupIds",groups.stream().map(Object::toString).toList());result.put("submissionWarnings",warnings);return result;
    }
    /** 原执行幂等返回时必须重读同一已过账集合；不能把新选择偷偷另存为草稿。 */
    public boolean postedSelectionMatches(String type,Long source,Long line,List<SnItem> items) {
        var groups=jdbc.queryForList("SELECT posting_group_id,status FROM wm_trace_posting_group WHERE tenant_id=? AND source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? ORDER BY detail_version DESC LIMIT 1 FOR UPDATE",tenant(),type,source,line);
        return !groups.isEmpty()&&"POSTED".equals(groups.get(0).get("status"))
            &&sameItems(items,repository.items(number(groups.get(0).get("posting_group_id"))));
    }
    public List<Map<String,Object>> display(List<SnItem> items) {
        List<Map<String,Object>> values=new ArrayList<>();
        for(var item:items) {
            var identity=repository.row("SELECT sn_id,sn_code,material_id,batch_id,batch_code FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",tenant(),item.identityId());
            Map<String,Object> row=new LinkedHashMap<>();row.put("identityId",text(item.identityId()));row.put("localSnId",text(item.identityId()));
            row.put("snCode",identity.get("sn_code"));row.put("canonicalSnCode",identity.get("sn_code"));row.put("materialId",text(identity.get("material_id")));
            row.put("batchId",text(item.batchId()));row.put("batchCode",item.batchId()==null?null:identity.get("batch_code"));
            row.put("sourceStockId",text(item.sourceStockId()));row.put("targetLocationId",text(item.targetLocationId()));row.put("originalTxId",text(item.originalTxId()));values.add(row);
        }
        return values;
    }
    public static boolean sameItems(List<SnItem> a,List<SnItem> b) {return new HashSet<>(a).equals(new HashSet<>(b))&&a.size()==b.size();}
    static SnItem item(Map<String,Object> row) {return new SnItem(number(row.get("identity_id")),number(row.get("source_stock_id")),number(row.get("target_location_id")),number(row.get("original_tx_id")),number(row.get("batch_id")));}
    public static Long number(Object value) {return value==null?null:Long.valueOf(value.toString());}
    static String text(Object value) {return value==null?null:value.toString();}
    static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
