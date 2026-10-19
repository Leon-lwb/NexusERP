package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
import java.util.List;
/** Must join the ERP inbox transaction. All foreign pages have been verified before this call. */
public interface ProductionManifestPostingPort {
    CompletionReceipt post(CompletionManifest manifest,List<CompletionManifestLine> lines);
    default CompletionReceipt post(CompletionManifest manifest,List<CompletionManifestLine> lines,List<CompletionMaterialAllocation> allocations){
        if(allocations!=null && !allocations.isEmpty())throw new IllegalStateException("目标入库服务尚未接入冻结组件分配");
        return post(manifest,lines);
    }
}
