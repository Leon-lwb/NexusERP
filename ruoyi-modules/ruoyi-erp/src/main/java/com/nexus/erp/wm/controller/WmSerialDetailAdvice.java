package com.nexus.erp.wm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.erp.wm.controller.serial.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

/** 原PC/PDA详情返回真实持久化SN、opaque版本及策略，沿用全局Long序列化器。 */
@RestControllerAdvice(basePackages={"com.nexus.erp.wm.controller","com.nexus.erp.so.controller","com.nexus.erp.pu.controller","com.nexus.erp.prd.controller"})
@RequiredArgsConstructor
public class WmSerialDetailAdvice implements ResponseBodyAdvice<Object> {
    private final WmSerialBusinessStore store;
    private final ObjectMapper json;
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter)
            && !WmSerialSelectionController.class.equals(method.getDeclaringClass());
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body==null || request.getMethod()!=HttpMethod.GET)return body;
        var route=SerialBusinessRoute.forReadPath(request.getURI().getPath());if(route==null)return body;
        String path=request.getURI().getPath();
        if(path.endsWith("/todo")||path.endsWith("/list")&&!path.startsWith("/erp/wm/detail/")&&!path.startsWith("/erp/wm/receiveline/")||path.contains("/getProcInsId/")||path.contains("/availableStock")||path.contains("/workOrderLines"))return body;
        Object value=json.convertValue(body,Object.class);
        decorate(value,route,route.sourceType(path),null,null,new HashMap<>());return value;
    }
    @SuppressWarnings("unchecked")
    private void decorate(Object value,SerialBusinessRoute route,String type,Long parent,Long parentLine,Map<String,WmSerialBusinessStore.Detail> cache) {
        if(value instanceof List<?> values){for(Object item:values)decorate(item,route,type,parent,parentLine,cache);return;}
        if(!(value instanceof Map<?,?>))return;Map<String,Object> row=(Map<String,Object>)value;
        Long head=number(row.get(route.headProperty()));if(head==null)head=parent;
        Long line=number(row.get("lineId"));if(line==null)line=parentLine;
        if(route.whole())line=0L;
        // 生产入库的真实source为woId，不能把recptId当成工单ID。
        Long source=type.equals("PRD_WORK_ORDER")?number(row.get("woId")):head;
        if(type.equals("PRD_WORK_ORDER")&&source==null&&head!=null)source=store.sourceId(route,head);
        List<Object> children=new ArrayList<>(row.values());
        if(source!=null&&line!=null&&(row.containsKey("lineId")||row.containsKey("detailId")||route.whole()&&row.containsKey(route.headProperty()))) {
            String key=type+"/"+source+"/"+line;Long sourceId=source,lineId=line;
            var detail=cache.computeIfAbsent(key,ignored->store.detail(type,sourceId,lineId));
            row.put("detailVersion",detail.detailVersion());row.put("detailRevision",detail.detailRevision());row.put("policy",detail.policy());
            List<Map<String,Object>> items=detail.snItems();
            // 收货分配子行只展示自己库位/批次的集合；不因成品批次不同套用allowMixedLot。
            if(row.containsKey("detailId")) {
                Long batch=number(row.get("batchId")),location=number(row.get("locationId"));
                items=items.stream().filter(i->(batch==null||batch.equals(number(i.get("batchId"))))
                    &&(location==null||location.equals(number(i.get("targetLocationId"))))).toList();
            }
            if(row.get("materialId")==null)row.put("materialId",store.routeMaterial(type,source,line).toString());
            row.put("snItems",items);row.put("snSelectionStatus",detail.selectionStatus());
            if(!detail.submissionSnapshot().isEmpty())row.put("submissionSnapshot",detail.submissionSnapshot());
            if(!detail.previousSnItems().isEmpty())row.put("previousSnItems",detail.previousSnItems());
        }
        for(Object child:children)if(child instanceof Map<?,?>||child instanceof List<?>)decorate(child,route,type,head,line,cache);
    }
    private static Long number(Object value) {return WmSerialBusinessStore.number(value);}
}
