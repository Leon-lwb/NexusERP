package com.nexus.erp.wm.service;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.flowable.common.enums.ProcessStatus;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.mapper.WmCertifiedWarehouseReadMapper;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

/**
 * 工作台结构/原生事实的只读提示，不替代生产写 Service 的租户、审批、数量、事务或幂等守卫。
 * ERP 与 master 均由实际外部调用的 public DS 方法路由，不用同类内部自调用切换数据源。
 * 到货 query 不读取收货表；收货 query 不读取到货通知表或其正文。
 */
@Service
public class WmCertifiedWarehouseReadService {
    /** 当前身份来自既有仓储 authorizer 的真实边界；部门未知可读，不额外要求 writer 身份。 */
    public record Context(String domain,String tenantId,String userId,String username,String deptId,String databaseDate,
                          boolean createAllowed,boolean submitAllowed,boolean shelfAllowed,boolean executeAllowed) { }

    /** 原生完整仓储头/行/分配与不可编辑来源摘要；不是客户端写证书。 */
    public record Detail(
            String domain,
            String kind,
            String documentId,
            String protection,
            boolean sourceComplete,
            String flowState,
            Map<String, Object> order,
            List<Map<String, Object>> orderLines,
            @JsonProperty("native") Map<String, Object> nativeDocument,
            List<Map<String, Object>> lines,
            List<Map<String, Object>> details,
            List<Map<String, Object>> creations,
            Map<String, Object> posting,
            List<Map<String, Object>> orderSources,
            List<Map<String, Object>> approvals,
            Map<String, Object> workflow,
            List<Map<String, Object>> iqcEvidence,
            List<Map<String, Object>> births) {
        /** JSON 使用 native 与前端原生快照契约一致，不改通用 VO。 */
        Detail flow(String state,Map<String,Object> proof) { return new Detail(domain,kind,documentId,protection,sourceComplete,state,order,orderLines,nativeDocument,lines,details,creations,posting,orderSources,approvals,proof,iqcEvidence,births); }
    }
    private final WmCertifiedWarehouseReadMapper mapper;
    private final WmCertifiedReceiptPostingAuthorizer authorizer;
    private final WmCertifiedArrivalWorkflowPort arrivalWorkflow;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final TaskService tasks;
    public WmCertifiedWarehouseReadService(WmCertifiedWarehouseReadMapper mapper,WmCertifiedReceiptPostingAuthorizer authorizer,
        WmCertifiedArrivalWorkflowPort arrivalWorkflow,RuntimeService runtime,HistoryService history,TaskService tasks) {
        this.mapper=mapper;this.authorizer=authorizer;this.arrivalWorkflow=arrivalWorkflow;this.runtime=runtime;this.history=history;this.tasks=tasks;
    }
    @DS("erp")
    public Context arrivalContext() { return context("arrivalnotice",actor("arrivalnotice")); }
    @DS("erp")
    public Context receiveContext() { return context("receive",actor("receive")); }
    @DS("erp")
    public Detail arrivalOrder(Long id) { return orderDetail("arrivalnotice",actor("arrivalnotice"),id); }
    @DS("erp")
    public Detail receiveOrder(Long id) { return orderDetail("receive",actor("receive"),id); }
    @DS("erp")
    public Detail arrivalDetail(Long id) {
        DecisionActor actor=actor("arrivalnotice");require(positive(id));String t=actor.tenantId();
        Map<String,Object> head=row(mapper.notice(t,id));require(identity(head,t,"noticeId",id));
        List<Map<String,Object>> lines=rows(mapper.noticeLines(t,id)),created=rows(mapper.noticeCreations(t,id));
        for(var line:lines) require(identity(line,t,"noticeId",id));
        Long orderId=created.isEmpty()?null:number(created.get(0),"orderId");
        boolean ambiguous=false;
        if(orderId==null&&text(head.get("purOrderCode"))) { var ids=mapper.orderIdsByCode(t,head.get("purOrderCode").toString());require(ids!=null);if(ids.size()==1)orderId=ids.get(0);else ambiguous=true; }
        Detail origin=orderId==null?emptyOrder("arrivalnotice",id):orderDetail("arrivalnotice",actor,orderId);
        boolean protectedFact=!created.isEmpty()||"CERTIFIED".equals(origin.protection())||label(head);
        String protection=protectedFact?"CERTIFIED":ambiguous||"UNKNOWN".equals(origin.protection())?"UNKNOWN":"LEGACY";
        boolean complete=protectedFact&&origin.sourceComplete()&&created.size()==1&&lines.size()==1;
        if(complete) {var c=created.get(0);var l=lines.get(0);complete=identity(c,t,"noticeId",id)&&same(c,"noticeLineId",l,"lineId")&&same(c,"orderId",origin.order(),"orderId")
            &&same(c,"vendorId",head,"vendorId")&&same(c,"noticeCode",head,"noticeCode")&&same(c,"materialId",l,"materialId")&&same(c,"baseUomId",l,"baseUomId")&&decimalEqual(c.get("quantity"),l.get("quantity"))&&positive(number(c,"actorId"))&&digest(c.get("requestHash"))&&text(c.get("requestKey"))&&"0".equals(l.get("delFlag"))&&sourceLine(c,origin)&&same(c,"noticeName",head,"noticeName")&&same(c,"arrivalDate",head,"arrivalDate"); }
        return new Detail("arrivalnotice","NOTICE",id.toString(),protection,complete,"UNKNOWN",origin.order(),origin.orderLines(),head,lines,List.of(),created,null,origin.orderSources(),origin.approvals(),Map.of(),List.of(),List.of());
    }
    @DS("erp")
    public Detail receiveDetail(Long id) {
        DecisionActor actor=actor("receive");require(positive(id));String t=actor.tenantId();
        Map<String,Object> head=row(mapper.receive(t,id));require(identity(head,t,"receiveId",id));
        List<Map<String,Object>> evidence=rows(mapper.iqcEvidence(t,id)),births=rows(mapper.births(t,id));
        for(var e:evidence)require(identity(e,t,"receiveId",id));for(var b:births)require(identity(b,t,"receiveId",id));
        List<Map<String,Object>> lines=rows(mapper.receiveLines(t,id)),details=rows(mapper.receiveDetails(t,id)),created=rows(mapper.receiveCreations(t,id));
        for(var line:lines) require(identity(line,t,"receiveId",id));for(var detail:details)require(identity(detail,t,"receiveId",id));
        Long birth=mapper.birthCount(t,id);require(birth!=null&&birth>=0);Map<String,Object> posting=row(mapper.posting(t,id));
        Long orderId=number(head,"purId");Detail origin=orderId==null?emptyOrder("receive",id):orderDetail("receive",actor,orderId);
        boolean protectedFact=!created.isEmpty()||posting!=null||birth>0||"CERTIFIED".equals(origin.protection())||label(head);
        String protection=protectedFact?"CERTIFIED":"UNKNOWN".equals(origin.protection())||orderId==null&&head.get("noticeId")!=null?"UNKNOWN":"LEGACY";
        boolean complete=protectedFact&&origin.sourceComplete()&&created.size()==lines.size()&&!created.isEmpty();
        Set<Long> seen=new HashSet<>();
        for(var c:created) {var found=lines.stream().filter(l->same(c,"lineId",l,"lineId")).toList();
            if(found.size()!=1||!seen.add(number(c,"lineId"))) {complete=false;continue;}var l=found.get(0);
            complete &= identity(c,t,"receiveId",id)&&same(c,"orderId",origin.order(),"orderId")&&same(c,"receiveCode",head,"receiveCode")&&same(c,"purchaseLineId",l,"purchaseLineId")
                &&same(c,"materialId",l,"materialId")&&same(c,"baseUomId",l,"baseUomId")&&decimalEqual(c.get("quantity"),l.get("quantity"))&&same(c,"noticeId",head,"noticeId")&&same(c,"noticeLineId",l,"noticeLineId")
                &&positive(number(c,"actorId"))&&digest(c.get("requestHash"))&&text(c.get("requestKey"))&&"0".equals(l.get("delFlag"))&&sourceLine(c,origin)&&same(c,"warehouseId",head,"warehouseId")&&same(c,"areaId",head,"areaId")&&same(c,"locationId",head,"locationId"); }
        return new Detail("receive","RECEIVE",id.toString(),protection,complete,"UNKNOWN",origin.order(),origin.orderLines(),head,lines,details,created,posting,origin.orderSources(),origin.approvals(),Map.of(),evidence,births);
    }
    /** controller 先结束 ERP public 方法，再进入 master；不存在同类切库/锁定读取。 */
    @DS("master")
    public Detail withWorkflow(Detail detail) {
        require(detail!=null&&Set.of("arrivalnotice","receive").contains(detail.domain()));DecisionActor actor=actor(detail.domain());
        if(!"CERTIFIED".equals(detail.protection())||detail.nativeDocument()==null)return detail;
        require(actor.tenantId().equals(detail.nativeDocument().get("tenantId")));
        try {
            var bindings=rows(mapper.workflowBindings(actor.tenantId(),detail.documentId()));
            if(bindings.isEmpty())return detail.flow("DRAFT".equals(detail.nativeDocument().get("status"))?"NOT_STARTED":"UNKNOWN",Map.of());
            require(bindings.size()==1);var binding=bindings.get(0);String process=Objects.toString(binding.get("processInstanceId"),"");String definition=Objects.toString(binding.get("processDefinitionId"),"");
            require(text(process)&&text(definition)&&detail.documentId().equals(binding.get("dataId"))&&actor.tenantId().equals(binding.get("tenantId")));
            String expected="arrivalnotice".equals(detail.domain())?"wmCertifiedArrivalNoticeService":"wmMaterialReceiveCrudService";require(expected.equals(binding.get("serviceImplName")));
            var archived=history.createHistoricProcessInstanceQuery().processInstanceId(process).processInstanceTenantId(actor.tenantId()).singleResult();
            require(archived!=null&&actor.tenantId().equals(archived.getTenantId())&&detail.documentId().equals(archived.getBusinessKey())&&definition.equals(archived.getProcessDefinitionId()));
            String state;
            if("arrivalnotice".equals(detail.domain())) {
                var proof=arrivalWorkflow.read(actor.tenantId(),Long.valueOf(detail.documentId()));require(proof!=null&&actor.tenantId().equals(proof.tenantId())&&detail.documentId().equals(proof.dataId())&&detail.documentId().equals(proof.sourceDataId())&&detail.documentId().equals(proof.sourceNoticeId())&&Objects.equals(detail.nativeDocument().get("noticeCode"),proof.businessCode())&&process.equals(proof.processInstanceId())&&definition.equals(proof.processDefinitionId()));state=proof.nextStatus();
            } else {
                var active=runtime.createProcessInstanceQuery().processInstanceId(process).processInstanceTenantId(actor.tenantId()).singleResult();
                if(active!=null)require(actor.tenantId().equals(active.getTenantId())&&detail.documentId().equals(active.getBusinessKey())&&definition.equals(active.getProcessDefinitionId())&&archived.getEndTime()==null);
                boolean running=active!=null;require(detail.documentId().equals(variable(process,"dataId",running))&&detail.documentId().equals(variable(process,"receiveId",running))&&Objects.equals(detail.nativeDocument().get("receiveCode"),variable(process,"receiveCode",running)));
                String status=variable(process,ProcessConstants.PROCESS_STATUS_KEY,running);
                if(running) {require(ProcessStatus.RUNNING.getStatus().equals(status));state="APPROVING";}
                else {require(archived.getEndTime()!=null&&tasks.createTaskQuery().processInstanceId(process).taskTenantId(actor.tenantId()).count()==0);
                    if(ProcessStatus.COMPLETED.getStatus().equals(status)){require(!text(archived.getDeleteReason()));state="APPROVED";}
                    else if(ProcessStatus.TERMINATED.getStatus().equals(status))state="REJECTED";
                    else if(ProcessStatus.CANCELED.getStatus().equals(status))state="DRAFT";else throw new ServiceException("未知原流程状态"); }
            }
            require(Set.of("DRAFT","APPROVING","APPROVED","REJECTED").contains(state));
            Map<String,Object> proof=new LinkedHashMap<>(binding);proof.put("initiatorName",archived.getStartUserId());proof.put("nextStatus",state);
            return detail.flow(state,Collections.unmodifiableMap(proof));
        } catch(RuntimeException unknown) { return detail.flow("UNKNOWN",Map.of()); }
    }
    private String variable(String process,String name,boolean running) { Object value;if(running)value=runtime.getVariable(process,name);else{var variable=history.createHistoricVariableInstanceQuery().processInstanceId(process).variableName(name).singleResult();value=variable==null?null:variable.getValue();}return value==null?null:value.toString(); }
    private Context context(String domain,DecisionActor actor) {
        Long department=LoginHelper.getDeptId();String date=mapper.databaseDate(actor.tenantId());require(date!=null&&date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
        return new Context(domain,actor.tenantId(),actor.userId().toString(),actor.userName(),department==null?null:department.toString(),date,
            StpUtil.hasPermission("wm:"+domain+":add"),"arrivalnotice".equals(domain)&&StpUtil.hasPermission("wm:arrivalnotice:submit"),
            "receive".equals(domain)&&StpUtil.hasPermission("wm:receive:shelf"),"receive".equals(domain)&&StpUtil.hasPermission("wm:receive:execute"));
    }
    private DecisionActor actor(String domain) {
        DecisionActor actor;
        if("receive".equals(domain))actor=authorizer.requireQuery();
        else { require("arrivalnotice".equals(domain));StpUtil.checkLogin();StpUtil.checkPermission("wm:arrivalnotice:query");actor=new DecisionActor(TenantHelper.getTenantId(),LoginHelper.getUserId(),LoginHelper.getUsername(),Set.of("wm:arrivalnotice:query")); }
        require(actor!=null&&TenantHelper.isEnable()&&actor.tenantId()!=null&&actor.tenantId().matches("[0-9]{6,20}")&&actor.tenantId().equals(TenantHelper.getTenantId())&&actor.tenantId().equals(LoginHelper.getTenantId())&&positive(actor.userId()));return actor;
    }
    private Detail orderDetail(String domain,DecisionActor actor,Long id) {
        require(positive(id));String t=actor.tenantId();Map<String,Object> order=row(mapper.order(t,id));
        if(order==null)return new Detail(domain,"ORDER",id.toString(),"UNKNOWN",false,"NOT_APPLICABLE",null,List.of(),null,List.of(),List.of(),List.of(),null,List.of(),List.of(),Map.of(),List.of(),List.of());
        require(identity(order,t,"orderId",id));List<Map<String,Object>> lines=rows(mapper.orderLines(t,id)),sources=rows(mapper.orderSources(t,id)),approvals=rows(mapper.approvals(t,id));
        for(var line:lines)require(identity(line,t,"orderId",id));
        boolean marked=!sources.isEmpty()||order.get("termsId")!=null||number(order,"version")!=null&&number(order,"version")>0;
        String protection=marked?"CERTIFIED":order.get("version") instanceof Integer&&Integer.valueOf(0).equals(order.get("version"))?"LEGACY":"UNKNOWN";
        boolean complete=marked&&sources.size()==1&&approvals.size()==1&&!lines.isEmpty()&&order.get("version") instanceof Integer&&((Integer)order.get("version"))>0&&order.get("selectFlowId")==null&&positive(number(order,"termsId"));
        if(complete) {var source=sources.get(0);var approval=approvals.get(0);complete=t.equals(source.get("tenantId"))&&same(source,"requisitionId",order,"requisitionId")&&same(source,"vendorId",order,"vendorId")&&"COMMITTED".equals(source.get("commandStatus"))&&"ORDERED".equals(source.get("requisitionStatus"))&&same(source,"requestKey",source,"decisionIdempotencyKey")&&digest(source.get("sourceFingerprint"))&&digest(source.get("requestHash"))&&digest(source.get("resultFingerprint"))&&digest(source.get("catalogFingerprint"))&&identity(approval,t,"orderId",id)&&"APPROVE_ORDER".equals(approval.get("operation"))&&Integer.valueOf(0).equals(approval.get("expectedVersion"))&&Integer.valueOf(1).equals(approval.get("resultVersion"))&&same(approval,"quoteId",order,"termsId")&&digest(approval.get("quoteHash"));
            for(var line:lines)complete &= same(source,"materialId",line,"materialId")&&positive(number(line,"requisitionLineId"))&&positive(number(line,"baseUomId"))&&"0".equals(line.get("delFlag")); }
        return new Detail(domain,"ORDER",id.toString(),protection,complete,"NOT_APPLICABLE",order,lines,null,List.of(),List.of(),List.of(),null,sources,approvals,Map.of(),List.of(),List.of());
    }
    private static boolean sourceLine(Map<String,Object> created,Detail origin) {
        var matched=origin.orderLines().stream().filter(line->same(created,"purchaseLineId",line,"lineId")).toList();
        return matched.size()==1&&same(created,"materialId",matched.get(0),"materialId")&&same(created,"baseUomId",matched.get(0),"baseUomId");
    }
    private static Detail emptyOrder(String domain,Long id) { return new Detail(domain,"ORDER",id.toString(),"LEGACY",false,"NOT_APPLICABLE",null,List.of(),null,List.of(),List.of(),List.of(),null,List.of(),List.of(),Map.of(),List.of(),List.of()); }
    private static boolean label(Map<String,Object> row) { return row!=null&&String.valueOf(row.get("attr1")).contains("CERTIFIED"); }
    private static boolean identity(Map<String,Object> row,String tenant,String field,Long id) {return row!=null&&tenant.equals(row.get("tenantId"))&&id.equals(number(row,field))&&(!row.containsKey("delFlag")||"0".equals(row.get("delFlag")));}
    private static Long number(Map<String,Object> row,String field) {Object value=row==null?null:row.get(field);if(value instanceof Number n)return n.longValue();if(value instanceof String text&&text.matches("[1-9][0-9]{0,18}")){try{return Long.valueOf(text);}catch(NumberFormatException unknown){return null;}}return null;}
    private static boolean same(Map<String,Object> a,String ka,Map<String,Object> b,String kb) { if(a==null||b==null)return false;return Objects.equals(Objects.toString(a.get(ka),null),Objects.toString(b.get(kb),null)); }
    private static boolean decimalEqual(Object a,Object b) {try{return a!=null&&b!=null&&new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;}catch(NumberFormatException unknown){return false;}}
    private static boolean positive(Long n) {return n!=null&&n>0;}
    private static boolean text(Object value) {return value!=null&&!value.toString().isBlank();}
    private static boolean digest(Object value) {return value!=null&&value.toString().matches("[0-9a-f]{64}");}
    private static void require(boolean allowed) {if(!allowed)throw new ServiceException("仓储只读身份或原生事实不完整，禁止推断写入资格");}
    private static Map<String,Object> row(Map<String,Object> raw) {
        if(raw==null)return null;Map<String,Object> result=new LinkedHashMap<>();
        raw.forEach((key,value)->{String[] parts=key.split("_");StringBuilder camel=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)if(!parts[i].isEmpty())camel.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));result.put(camel.toString(),value);});
        if(result.containsKey("shelfTimeExact"))result.put("shelfTime",result.remove("shelfTimeExact"));
        if(result.containsKey("inspectedExact"))result.put("inspectedQuantity",result.remove("inspectedExact"));
        if(result.containsKey("quantityExact"))result.put("quantity",result.remove("quantityExact"));
        if (result.containsKey("arrivalDate") && result.get("arrivalDate") instanceof Date date)
            result.put("arrivalDate", date.toString());
        return Collections.unmodifiableMap(result);
    }
    private static List<Map<String,Object>> rows(List<Map<String,Object>> raw) {require(raw!=null);return raw.stream().map(value->{require(value!=null);return row(value);}).toList();}
}
