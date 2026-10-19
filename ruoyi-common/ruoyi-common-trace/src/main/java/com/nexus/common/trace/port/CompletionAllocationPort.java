package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
public interface CompletionAllocationPort {
    CompletionAllocationManifest allocationManifest(String tenantId,Long manifestId,int revision);
    TracePage<CompletionMaterialAllocation> allocations(String tenantId,Long manifestId,int revision,String cursor,int limit);
}
