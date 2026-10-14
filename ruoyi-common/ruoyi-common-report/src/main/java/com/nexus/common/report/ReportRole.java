package com.nexus.common.report;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 单个角色的权限集合与业务范围，多个角色按平台授权规则解析。
 *
 * @param roleId 当前契约的roleId配置或状态
 * @param permissions 当前契约的permissions配置或状态
 * @param scope 当前契约的scope配置或状态
 */
public record ReportRole(long roleId, Set<String> permissions, ReportScope scope) {

    /**
     * 初始化报表角色并保存所需依赖或不可变值。
     *
     * @param roleId 当前操作的 {@code roleId} 输入
     * @param permissions 当前操作的 {@code permissions} 输入
     * @param scope 当前操作的 {@code scope} 输入
     */
    public ReportRole {
        permissions = Collections.unmodifiableSet(new TreeSet<>(permissions));
        Objects.requireNonNull(scope);
    }

    /**
     * 读取当前租户的资源授权。
     *
     * @param permission 待核对权限
     * @return 当前检查或条件操作是否成功
     */
    public boolean grants(String permission) {
        return permissions.stream()
                .anyMatch(
                        grant ->
                                grant.equals("*:*:*")
                                        || grant.equals("*")
                                        || permission.matches(
                                                Arrays.stream(grant.split("\\*", -1))
                                                        .map(Pattern::quote)
                                                        .collect(Collectors.joining(".*"))));
    }
}
