package com.nexus.common.trace.port;
import java.util.Set;
/** 返回本模块有权查询且已映射的实物身份，统一解析不得扩大模块访问范围。 */
public interface SerialVisibilityPort { Set<Long> visibleIdentities(String tenantId,Set<Long> identityIds);
 default java.util.Map<Long,Long> localSnIds(String tenantId,Set<Long> identityIds){return java.util.Map.of();} }
