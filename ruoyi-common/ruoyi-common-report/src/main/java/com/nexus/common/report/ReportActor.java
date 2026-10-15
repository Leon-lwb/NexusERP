package com.nexus.common.report;

import java.util.*;

/**
 * 从当前授权解析的服务端身份快照，包含租户、用户、部门和角色范围。
 *
 * @param tenantId 租户隔离标识
 * @param userId 当前用户标识
 * @param departmentId 当前契约的departmentId配置或状态
 * @param superAdmin 当前契约的superAdmin配置或状态
 * @param roles 当前契约的roles配置或状态
 */
public record ReportActor(
        String tenantId,
        long userId,
        long departmentId,
        boolean superAdmin,
        List<ReportRole> roles) {
    /**
     * 初始化报表身份并保存所需依赖或不可变值。
     *
     * @param tenantId 当前租户标识
     * @param userId 当前用户标识
     * @param departmentId 当前操作的 {@code departmentId} 输入
     * @param superAdmin 当前操作的 {@code superAdmin} 输入
     * @param roles 当前操作的 {@code roles} 输入
     */
    public ReportActor {
        if (tenantId == null
                || tenantId.isBlank()
                || !tenantId.matches("[A-Za-z0-9_-]{1,20}")
                || userId <= 0)
            throw new IllegalArgumentException("Explicit report tenant and actor required");
        roles = List.copyOf(roles == null ? List.of() : roles);
    }

    /**
     * 判断当前身份拥有指定权限。
     *
     * @param permission 待核对权限
     * @return 当前检查或条件操作是否成功
     */
    public boolean has(String permission) {
        return superAdmin || roles.stream().anyMatch(role -> role.grants(permission));
    }

    /**
     * 读取指定权限的业务范围。
     *
     * @param permission 待核对权限
     * @return 当前处理返回的固定结果
     */
    public ReportScope scopeFor(String permission) {
        if (superAdmin) return ReportScope.all(userId);
        return roles.stream()
                .filter(role -> role.grants(permission))
                .map(ReportRole::scope)
                .reduce(ReportScope.none(userId), ReportScope::union);
    }

    /**
     * 完成 {@code roleIds} 对应的报表身份处理。
     *
     * @return 当前处理返回的固定结果
     */
    public Set<Long> roleIds() {
        Set<Long> ids = new TreeSet<>();
        roles.forEach(role -> ids.add(role.roleId()));
        return Collections.unmodifiableSet(ids);
    }
}
