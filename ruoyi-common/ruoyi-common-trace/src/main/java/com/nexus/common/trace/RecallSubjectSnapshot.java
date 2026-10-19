package com.nexus.common.trace;
import java.util.List;
/** Internal read-port result from one local repeatable-read view; it carries no cross-database authority. */
public record RecallSubjectSnapshot(List<Long> identityIds,List<WorkOrderTraceImpact> workOrders,boolean truncatedByCapacity){
    public RecallSubjectSnapshot{identityIds=List.copyOf(identityIds);workOrders=List.copyOf(workOrders);}
}
