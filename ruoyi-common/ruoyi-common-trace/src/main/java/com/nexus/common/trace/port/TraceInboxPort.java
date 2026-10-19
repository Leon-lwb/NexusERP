package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
/** 目标库内同时提交inbox、业务效果和回执outbox；调用方只能在返回提交后ACK。 */
public interface TraceInboxPort {
 String targetModule();
 /** 来源已验证后查询既有应用终态；无终态返回null，不能在此制造回执或放宽foreign prepare。 */
 default TraceDeliveryResult findApplied(MaterialTraceEvent event) {return null;}
 TraceDeliveryResult accept(MaterialTraceEvent event,TraceEventHandler handler,Object prepared);
}
