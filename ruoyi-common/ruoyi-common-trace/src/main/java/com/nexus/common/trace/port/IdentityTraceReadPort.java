package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
public interface IdentityTraceReadPort {
    String sourceModule();
    IdentityTracePart identity(String tenantId,Long identityId,String section,String cursor,int limit);
    default RecallSubjectSnapshot recallSubject(String tenantId,String scope,Long sourceId,int capacity){return TraceRecallSnapshots.read(this,tenantId,scope,sourceId,capacity);}
    default BatchTracePart batch(String tenantId,Long batchId,String section,String cursor,int limit){return new BatchTracePart(sourceModule(),batchId,"BATCH",java.util.Map.of(),java.util.List.of());}
    default java.util.Set<Long> batchesForIdentities(String tenantId,java.util.List<Long> identityIds){return java.util.Set.of();}
    default TracePage<Long> affectedByIdentity(String tenantId,Long identityId,long afterIdentity,int limit){return new TracePage<>(java.util.List.of(),null,false);}
    default TracePage<WorkOrderTraceImpact> unallocatedByBatch(String tenantId,Long batchId,long afterConsumption,int limit){return new TracePage<>(java.util.List.of(),null,false);}
    default TracePage<WorkOrderTraceImpact> unallocatedByIdentity(String tenantId,Long identityId,long afterConsumption,int limit){return new TracePage<>(java.util.List.of(),null,false);}
    default TracePage<Long> productIdentitiesByWorkOrder(String tenantId,Long mesWoId,long afterIdentity,int limit){return new TracePage<>(java.util.List.of(),null,false);}
    default TracePage<Long> productBatchesByWorkOrder(String tenantId,Long mesWoId,long afterBatch,int limit){return new TracePage<>(java.util.List.of(),null,false);}
    TracePage<Long> affectedByBatch(String tenantId,Long batchId,long afterIdentity,int limit);
}
