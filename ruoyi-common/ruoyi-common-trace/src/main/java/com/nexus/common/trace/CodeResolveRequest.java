package com.nexus.common.trace;
/** 只读解析请求；租户、人员和设备权限来自认证上下文。 */
public record CodeResolveRequest(String rawCode, String scene, Long materialId, Long supplierId, Long customerId,
                                 String sourceDocType, Long sourceDocId, Long lineId) { }
