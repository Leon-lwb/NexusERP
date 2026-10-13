package com.nexus.common.report;
/** Must re-read current account, role/menu grants and data ranges at execution/delivery. */
public interface ReportAuthorizationPort {
    ReportActor resolve(String tenantId, long userId);
    boolean subjectExists(String tenantId, String subjectType, long subjectId);
}
