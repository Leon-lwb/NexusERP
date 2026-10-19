package com.nexus.common.trace.port;
import com.nexus.common.trace.MaterialTraceEvent;
/** 来源读取在prepare完成，apply只在目标业务库事务中执行，禁止跨库写入。 */
public interface TraceEventHandler {
 String targetModule();
 boolean supports(String eventType);
 default Object prepare(MaterialTraceEvent event) {return null;}
 String apply(MaterialTraceEvent event,Object prepared);
}
