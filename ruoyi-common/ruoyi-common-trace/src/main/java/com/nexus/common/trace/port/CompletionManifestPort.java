package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
/** 读取已冻结清单，ERP应在开启自身写事务前完成分页与摘要核验。 */
public interface CompletionManifestPort {
    CompletionManifest manifest(String tenantId,Long manifestId,int revision);
    TracePage<CompletionManifestLine> lines(String tenantId,Long manifestId,int revision,String cursor,int limit);
}
