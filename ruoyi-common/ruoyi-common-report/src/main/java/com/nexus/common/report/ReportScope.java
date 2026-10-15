package com.nexus.common.report;

import java.util.*;

/**
 * 业务行范围：全部、部门集合和本人条件；仅对相应业务权限的实体生效。
 *
 * @param all 当前契约的all配置或状态
 * @param departments 当前契约的departments配置或状态
 * @param self 当前契约的self配置或状态
 * @param userId 当前用户标识
 */
public record ReportScope(boolean all, Set<Long> departments, boolean self, long userId) {

    /**
     * 初始化报表范围并保存所需依赖或不可变值。
     *
     * @param all 当前操作的 {@code all} 输入
     * @param departments 当前操作的 {@code departments} 输入
     * @param self 当前操作的 {@code self} 输入
     * @param userId 当前用户标识
     */
    public ReportScope {
        departments =
                Collections.unmodifiableSet(
                        new TreeSet<>(departments == null ? Set.of() : departments));
    }

    /**
     * 构造完整数据范围。
     *
     * @param user 当前操作的 {@code user} 输入
     * @return 当前处理返回的固定结果
     */
    public static ReportScope all(long user) {
        return new ReportScope(true, Set.of(), false, user);
    }

    /**
     * 构造无业务数据范围。
     *
     * @param user 当前操作的 {@code user} 输入
     * @return 当前处理返回的固定结果
     */
    public static ReportScope none(long user) {
        return new ReportScope(false, Set.of(), false, user);
    }

    /**
     * 完成 {@code allows} 对应的报表范围处理。
     *
     * @param department 当前操作的 {@code department} 输入
     * @param owner 租约或资源持有者
     * @return 当前检查或条件操作是否成功
     */
    public boolean allows(Long department, Long owner) {
        return all
                || department != null && departments.contains(department)
                || self && owner != null && owner == userId;
    }

    /**
     * 完成 {@code union} 对应的报表范围处理。
     *
     * @param other 当前操作的 {@code other} 输入
     * @return 当前处理返回的固定结果
     */
    public ReportScope union(ReportScope other) {
        if (userId != other.userId) throw new IllegalArgumentException("Scope actor mismatch");
        Set<Long> ids = new TreeSet<>(departments);
        ids.addAll(other.departments);
        return new ReportScope(all || other.all, ids, self || other.self, userId);
    }
}
