package com.nexus.common.report;

/** 执行和交付时重新读取当前账户、角色、菜单授权及业务数据范围。 */
public interface ReportAuthorizationPort {
    /**
     * 重新解析当前账户和业务权限。
     *
     * @param tenantId 当前租户标识
     * @param userId 当前用户标识
     * @return 当前处理返回的固定结果
     */
    ReportActor resolve(String tenantId, long userId);

    /**
     * 校验授权主体属于当前租户。
     *
     * @param tenantId 当前租户标识
     * @param subjectType 当前操作的 {@code subjectType} 输入
     * @param subjectId 当前操作的 {@code subjectId} 输入
     * @return 当前检查或条件操作是否成功
     */
    boolean subjectExists(String tenantId, String subjectType, long subjectId);
}
