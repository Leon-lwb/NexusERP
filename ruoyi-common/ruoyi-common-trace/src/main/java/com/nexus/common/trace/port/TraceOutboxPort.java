package com.nexus.common.trace.port;
import com.nexus.common.trace.MaterialTraceEvent;
import java.util.List;
/** 各模块提供自身数据源代理，append必须加入实际来源业务事务。 */
public interface TraceOutboxPort {
 String sourceModule();
 void append(MaterialTraceEvent event);
 List<MaterialTraceEvent> claim(String owner,int limit,int leaseSeconds);
 void brokerAccepted(String eventId,String owner);
 void retry(String eventId,String owner,String errorCode);
 void acknowledge(String tenantId,String eventId,String contentHash,String resultPayload);
}
