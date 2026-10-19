package com.nexus.erp.wm.controller.serial;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.util.*;

/** 在原权限校验之后，以同一ERP本地事务包围原保存、实物持久化和真实执行。 */
@Component @DS("erp") @RequiredArgsConstructor
public class WmSerialBusinessAdapter {
    private final WmSerialBusinessStore store;
    private final ThreadLocal<Scope> active=new ThreadLocal<>();
    @FunctionalInterface public interface Operation {Object run() throws Throwable;}
    private static final class Scope {
        SerialBusinessRoute route;String type;Long head;Long source;String command;String requestFingerprint;boolean save;boolean expectsPosting;
        WmSerialBusinessPayload payload;List<Long> beforeLines=List.of();
        final List<Long> insertedLines=new ArrayList<>();
        final Map<Long,WmSerialBusinessStore.Draft> beforeDrafts=new HashMap<>();
        final Set<Long> consumed=new HashSet<>();
        final Set<Long> postingGroups=new LinkedHashSet<>();
    }
    @Transactional(rollbackFor=Throwable.class)
    public Object invoke(SerialBusinessRoute route,String uri,JsonNode body,String action,Operation operation) throws Throwable {
        Scope scope=new Scope();scope.route=route;scope.type=route.sourceType(uri);scope.payload=new WmSerialBusinessPayload(body,route);
        scope.save=Set.of("add","edit","create").contains(action);scope.command=scope.payload.command();scope.requestFingerprint=SerialRequestFingerprint.of(uri,body);
        scope.expectsPosting=route.postsOn(action);
        scope.head=route.pathId(uri);Long payloadHead=scope.payload.headId(route);
        if(scope.head!=null&&payloadHead!=null&&!scope.head.equals(payloadHead))throw new ServiceException("请求体来源与URL原单不一致");
        if(scope.head==null)scope.head=payloadHead;
        if(scope.payload.hasSelection()&&scope.command==null)throw new ServiceException("SN明细请求必须携带commandId");
        store.requireWrite();
        if(scope.head!=null) {
            store.head(route,scope.head);scope.source=store.sourceId(route,scope.head);scope.beforeLines=store.lineIds(route,scope.head);
            if(scope.save&&!scope.type.equals("WM_PRODUCTION_ISSUE")&&store.frozen(scope.type,scope.source))throw new ServiceException("原实物已经冻结，不能重建来源行；请直接执行或另建来源单据");
            Object replay=replay(scope);if(replay!=null)return replay;
            for(Long line:scope.beforeLines)scope.beforeDrafts.put(line,store.draft(scope.type,scope.source,line));
            validate(scope);
        }
        Scope previous=active.get();active.set(scope);
        try {
            Object result=operation.run();
            if(!successful(result)) {TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();return result;}
            if(scope.head!=null) {
                scope.source=store.sourceId(route,scope.head);verifyConsumed(scope);persist(scope);
                if(scope.type.equals("PRD_WORK_ORDER")&&!scope.consumed.isEmpty())store.verifyManifest(scope.source,store.draft(scope.type,scope.source,0L));
                attachSavedDetails(result,scope);
                attachSubmission(result,store.submission(scope.command,scope.postingGroups));
            } else if(scope.payload.hasSelection())throw new ServiceException("原保存未返回真实来源ID，SN明细未保存");
            return result;
        } finally {if(previous==null)active.remove();else active.set(previous);}
    }
    /** 已提交任务先按原请求和原冻结组核验；绝不再次调用PDA改量或库存执行器。 */
    private Object replay(Scope scope) {
        if(!scope.expectsPosting||scope.command==null)return null;
        Map<Long,List<SnItem>> requested=new LinkedHashMap<>();int required=0;
        for(var row:scope.payload.lines()) {
            Long line=originalLine(scope,row);
            if(line==null) {
                if(!row.items().isEmpty())throw new ServiceException("SN明细缺少真实lineId");
                // 原执行也允许只携带command重读已保存的行；已知原命令仍须核对其完整原请求。
                for(Long original:scope.beforeLines)requested.putIfAbsent(original,null);
            } else requested.put(line,row.supplied()?row.items():null);
            if(!row.items().isEmpty())required++;
        }
        Map<Long,WmSerialBusinessStore.FrozenCommand> found=new LinkedHashMap<>();
        for(var entry:requested.entrySet()) {
            var command=store.command(scope.type,scope.source,entry.getKey(),scope.command,scope.requestFingerprint,entry.getValue());
            if(command!=null)found.put(entry.getKey(),command);
        }
        if(found.isEmpty())return null;
        if(found.size()<required)throw new ServiceException("同一命令只有部分冻结结果，禁止重放为整单成功");
        if(found.values().stream().anyMatch(command->!"POSTED".equals(command.status())))return null;
        List<Map<String,Object>> lines=new ArrayList<>();
        for(var entry:found.entrySet()) {
            var command=entry.getValue();store.verifyCommand(scope.type,scope.source,entry.getKey(),command);
            Map<String,Object> line=new LinkedHashMap<>();line.put("lineId",entry.getKey().toString());line.put("postingGroupId",command.id().toString());line.put("commandId",command.command());
            line.put("detailVersion",command.sourceHash());line.put("detailRevision",Long.toString(command.revision()));line.put("policyVersion",Long.toString(command.policyVersion()));line.put("status",command.status());line.put("snItems",store.display(command.items()));lines.add(line);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("ok",true);result.put("replayed",true);result.put("commandId",scope.command);result.put(scope.route.headProperty(),scope.head.toString());result.put("sourceDocType",scope.type);result.put("sourceDocId",scope.source.toString());result.put("lines",lines);
        result.putAll(store.submission(scope.command,found.values().stream().map(WmSerialBusinessStore.FrozenCommand::id).toList()));
        return R.ok(result);
    }
    /** 原业务返回200不足以证明单件被消费；缺少过账接入时回滚整个原操作。 */
    private void verifyConsumed(Scope scope) {
        if(!scope.expectsPosting)return;
        Map<Long,List<SnItem>> expected=new HashMap<>();
        scope.beforeDrafts.forEach((line,draft)->{if(!draft.items().isEmpty())expected.put(line,draft.items());});
        for(var row:scope.payload.lines()) {
            Long line=originalLine(scope,row);if(line!=null&&row.supplied())expected.put(line,row.items());
        }
        for(var entry:expected.entrySet()) {
            Long line=entry.getKey();if(entry.getValue().isEmpty()||scope.consumed.contains(line))continue;
            var policy=store.snapshot(scope.type,scope.source,line).policy();
            if(!policy.serialManaged()||policy.stage()!=com.nexus.common.trace.TrackingStage.STRICT)continue;
            if(!store.postedSelectionMatches(scope.type,scope.source,line,entry.getValue()))
                throw new ServiceException("原执行器未消费SN明细或重放集合已改变，本次业务操作已回滚；请检查实际单件执行入口");
            scope.consumed.add(line);
        }
    }
    private void validate(Scope scope) {
        for(var row:scope.payload.lines()) {
            Long line=originalLine(scope,row);
            if(line==null) {if(row.supplied()&&!scope.save)throw new ServiceException("SN明细缺少真实lineId");continue;}
            if(!scope.beforeLines.contains(line))throw new ServiceException("SN行不属于URL原单");
            var snapshot=store.snapshot(scope.type,scope.source,line);
            if(row.policyVersion()!=null&&!row.policyVersion().equals(snapshot.policy().version()))throw new ServiceException("物料追踪策略版本已改变，请重新读取详情");
            if(row.version()!=null) {
                if(!row.version().equals(snapshot.detailVersion()))throw new ServiceException("单据明细版本已改变，请重新读取服务端详情");
            } else if(row.supplied()||!scope.beforeDrafts.getOrDefault(line,new WmSerialBusinessStore.Draft(null,List.of())).items().isEmpty())throw new ServiceException("SN明细必须原样携带服务端detailVersion");
        }
    }
    private Long originalLine(Scope scope,WmSerialBusinessPayload.Line row) {
        if(scope.route.whole())return 0L;
        if(row.oldLine()!=null)return row.oldLine();
        return !scope.save&&scope.beforeLines.size()==1?scope.beforeLines.get(0):null;
    }
    private void persist(Scope scope) {
        if(scope.source==null)return;
        List<Long> current=scope.save?store.lineIds(scope.route,scope.head):scope.beforeLines;int insertedOrdinal=0;
        for(var row:scope.payload.lines()) {
            Long original=originalLine(scope,row);
            Long line=original;
            if(scope.route.whole())line=0L;
            else if(scope.save && (original==null||!current.contains(original)))
                line=insertedOrdinal<scope.insertedLines.size()?scope.insertedLines.get(insertedOrdinal++):null;
            if(line==null) {if(row.supplied()&&!row.items().isEmpty())throw new ServiceException("SN明细无法对应原保存实际生成的行");continue;}
            if(scope.consumed.contains(line))continue;
            var saved=original==null?null:scope.beforeDrafts.get(original);
            if(row.supplied()||saved!=null&&!saved.items().isEmpty()) {
                List<SnItem> items=row.supplied()?row.items():saved.items();
                String command=scope.command==null?saved.command():scope.command;
                store.saveDraft(scope.type,scope.source,line,command,items);
            }
        }
        // 仅清理已经由原CRUD替换的旧草稿行，正式历史永不移动/删除。
        if(scope.save)for(Long old:scope.beforeLines)
            if(!current.contains(old))store.deleteDraft(scope.type,scope.source,old);
    }
    /** 捕获原mapper插入后的真实ID及顺序，不按物料猜测，也不覆盖原ID分配器。 */
    public void inserted(Object value) {
        Scope scope=active.get();if(scope==null||value==null)return;
        if(value instanceof Collection<?> rows){for(Object row:rows)inserted(row);return;}
        String name=value.getClass().getSimpleName();var bean=new BeanWrapperImpl(value);
        if(name.equals(scope.route.entity())) {
            Long head=WmSerialBusinessStore.number(bean.getPropertyValue(scope.route.headProperty()));
            if(scope.head!=null&&!scope.head.equals(head))throw new ServiceException("原保存来源ID发生漂移");scope.head=head;scope.source=store.sourceId(scope.route,head);
        } else if(name.equals(scope.route.lineEntity())) {
            Long parent=WmSerialBusinessStore.number(bean.getPropertyValue(scope.route.headProperty()));
            if(!Objects.equals(parent,scope.head))throw new ServiceException("原保存行的父单不一致");
            Long line=WmSerialBusinessStore.number(bean.getPropertyValue("lineId"));if(line==null)throw new ServiceException("原保存未生成真实行ID");
            if(!scope.insertedLines.contains(line))scope.insertedLines.add(line);
        }
    }
    /** 同时覆盖PC、PDA嵌套即时执行、审批和MQ，全部进入原库存门禁。 */
    public void beforePosting(String type,Long source,Long line) {
        Scope scope=active.get();
        if(scope!=null && (scope.source==null||!scope.type.equals(type)||!scope.source.equals(source))) {
            // 完工等原动作可派生其他真实单据；派生单据只读取其自己已持久化的明细。
            Long group=store.freeze(type,source,line,null);if(group!=null)scope.postingGroups.add(group);return;
        }
        if(scope!=null)persist(scope);
        Long group=store.freeze(type,source,line,scope==null?null:scope.command,scope==null?null:scope.requestFingerprint);
        if(scope!=null&&group!=null)scope.postingGroups.add(group);
        if(scope!=null)scope.consumed.add(line);
    }
    public void beforeReceive(Long receive) {
        var route=SerialBusinessRoute.ofType("WM_MATERIAL_RECEIVE");
        for(Long line:store.lineIds(route,receive))beforePosting(route.type(),receive,line);
    }
    private static boolean successful(Object result) {
        if(result instanceof R<?> r) {
            if(r.getCode()!=200)return false;
            if(r.getData() instanceof PdaActionResultVo pda)return Boolean.TRUE.equals(pda.getOk());
        }
        return true;
    }
    /** 首次和重放使用同一只读元数据；保留原PDA焦点流转和失败提示。 */
    @SuppressWarnings({"rawtypes","unchecked"})
    private void attachSubmission(Object result,Map<String,Object> submitted) {
        if(submitted.isEmpty()||!(result instanceof R r))return;
        if(r.getData() instanceof PdaActionResultVo pda) {
            pda.setSubmissionState((String)submitted.get("submissionState"));pda.setCommandId((String)submitted.get("commandId"));
            pda.setPostingGroupIds((List<String>)submitted.get("postingGroupIds"));pda.setSubmissionWarnings((List<String>)submitted.get("submissionWarnings"));return;
        }
        Map<String,Object> data=new LinkedHashMap<>();
        if(r.getData() instanceof Map<?,?> old)old.forEach((key,value)->data.put(key.toString(),value));
        else if(r.getData()!=null)data.put("result",r.getData());
        data.putAll(submitted);r.setData(data);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void attachSavedDetails(Object result,Scope scope) {
        if(!scope.payload.hasSelection()||!(result instanceof R r)||r.getData()!=null)return;
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Long line:store.lineIds(scope.route,scope.head)) {
            var detail=store.detail(scope.type,scope.source,line);Map<String,Object> row=new LinkedHashMap<>();row.put("lineId",line.toString());
            row.put("detailVersion",detail.detailVersion());row.put("detailRevision",detail.detailRevision());row.put("policy",detail.policy());row.put("snItems",detail.snItems());rows.add(row);
        }
        r.setData(Map.of(scope.route.headProperty(),scope.head.toString(),"sourceDocType",scope.type,"sourceDocId",scope.source.toString(),"lines",rows));
    }
}
