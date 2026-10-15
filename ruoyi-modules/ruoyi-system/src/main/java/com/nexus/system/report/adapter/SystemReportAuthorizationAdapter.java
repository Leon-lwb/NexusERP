package com.nexus.system.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** 实时解析账户、角色和套餐权限，不向工作线程传播登录令牌或请求上下文。 */
@Service
@DS("master")
public class SystemReportAuthorizationAdapter implements ReportAuthorizationPort {
    /** 保存当前 {@code jdbc} 值。 */
    private final JdbcTemplate jdbc;

    /**
     * 初始化系统报表授权适配器并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public SystemReportAuthorizationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * User，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param department 当前契约的department配置或状态
     * @param status 当前契约的status配置或状态
     * @param deleted 当前契约的deleted配置或状态
     */
    private record User(long id, String tenant, long department, String status, String deleted) {}

    /**
     * Tenant，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param packageId 当前契约的packageId配置或状态
     * @param expires 当前契约的expires配置或状态
     */
    private record Tenant(String id, Long packageId, Timestamp expires) {}

    /**
     * 角色，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param scope 当前契约的scope配置或状态
     */
    private record Role(long id, String scope) {}

    /**
     * Department，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param ancestors 当前契约的ancestors配置或状态
     */
    private record Department(long id, String ancestors) {}

    /**
     * 重新解析当前账户和业务权限。
     *
     * @param requestedTenant 当前操作的 {@code requestedTenant} 输入
     * @param userId 当前用户标识
     * @return 当前处理返回的固定结果
     */
    public ReportActor resolve(String requestedTenant, long userId) {
        if (requestedTenant == null
                || !requestedTenant.matches("[A-Za-z0-9_-]{1,20}")
                || userId <= 0) throw denied();
        var users =
                jdbc.query(
                        "SELECT user_id,tenant_id,dept_id,status,del_flag FROM sys_user WHERE"
                                + " user_id=?",
                        (rs, index) ->
                                new User(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getLong(3),
                                        rs.getString(4),
                                        rs.getString(5)),
                        userId);
        if (users.size() != 1
                || !"0".equals(users.get(0).status)
                || !"0".equals(users.get(0).deleted)) throw denied();
        var tenants =
                jdbc.query(
                        "SELECT tenant_id,package_id,expire_time FROM sys_tenant WHERE tenant_id=?"
                                + " AND status='0' AND del_flag='0'",
                        (rs, index) ->
                                new Tenant(
                                        rs.getString(1),
                                        (Long) rs.getObject(2),
                                        rs.getTimestamp(3)),
                        requestedTenant);
        if (tenants.size() != 1
                || tenants.get(0).expires != null
                        && !tenants.get(0).expires.toInstant().isAfter(Instant.now()))
            throw denied();
        User user = users.get(0);
        Tenant tenant = tenants.get(0);
        if (user.id == 1)
            return new ReportActor(tenant.id, user.id, user.department, true, List.of());
        if (!tenant.id.equals(user.tenant)) throw denied();
        Set<Long> packageMenus = null;
        if (tenant.packageId != null && tenant.packageId > 0) {
            var packages =
                    jdbc.queryForList(
                            "SELECT menu_ids FROM sys_tenant_package WHERE package_id=? AND"
                                    + " status='0' AND del_flag='0'",
                            String.class,
                            tenant.packageId);
            if (packages.size() != 1) throw denied();
            packageMenus = new HashSet<>();
            String csv = packages.get(0);
            if (csv != null)
                for (String value : csv.split(",")) {
                    if (value.trim().isEmpty()) continue;
                    try {
                        packageMenus.add(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        throw denied();
                    }
                }
        }
        var roles =
                jdbc.query(
                        "SELECT r.role_id,r.data_scope FROM sys_role r JOIN sys_user_role ur ON"
                                + " ur.role_id=r.role_id WHERE ur.user_id=? AND r.tenant_id=? AND"
                                + " r.status='0' AND r.del_flag='0' ORDER BY r.role_id",
                        (rs, index) -> new Role(rs.getLong(1), rs.getString(2)),
                        user.id,
                        tenant.id);
        var departments =
                jdbc.query(
                        "SELECT dept_id,ancestors FROM sys_dept WHERE tenant_id=? AND status='0'"
                                + " AND del_flag='0'",
                        (rs, index) -> new Department(rs.getLong(1), rs.getString(2)),
                        tenant.id);
        Set<Long> activeDepartments = new HashSet<>();
        departments.forEach(d -> activeDepartments.add(d.id));
        List<ReportRole> resolved = new ArrayList<>();
        for (Role role : roles) {
            Set<String> permissions = new TreeSet<>();
            for (var menu :
                    jdbc.queryForList(
                            "SELECT m.menu_id,m.perms FROM sys_role_menu rm JOIN sys_menu m ON"
                                + " m.menu_id=rm.menu_id WHERE rm.role_id=? AND m.status='0' AND"
                                + " m.perms IS NOT NULL",
                            role.id)) {
                long id = ((Number) menu.get("menu_id")).longValue();
                String permission = Objects.toString(menu.get("perms"), "");
                if ((packageMenus == null || packageMenus.contains(id)) && !permission.isBlank())
                    permissions.add(permission);
            }
            ReportScope scope =
                    switch (Objects.toString(role.scope, "")) {
                        case "1" -> ReportScope.all(user.id);
                        case "2" -> {
                            Set<Long> ids =
                                    new TreeSet<>(
                                            jdbc.queryForList(
                                                    "SELECT d.dept_id FROM sys_role_dept rd JOIN"
                                                        + " sys_dept d ON d.dept_id=rd.dept_id"
                                                        + " WHERE rd.role_id=? AND d.tenant_id=?"
                                                        + " AND d.status='0' AND d.del_flag='0'",
                                                    Long.class,
                                                    role.id,
                                                    tenant.id));
                            yield new ReportScope(false, ids, false, user.id);
                        }
                        case "3" ->
                                new ReportScope(
                                        false,
                                        activeDepartments.contains(user.department)
                                                ? Set.of(user.department)
                                                : Set.of(),
                                        false,
                                        user.id);
                        case "4", "6" -> {
                            Set<Long> ids = new TreeSet<>();
                            if (activeDepartments.contains(user.department))
                                for (Department department : departments) {
                                    if (department.id == user.department
                                            || ancestor(department.ancestors, user.department))
                                        ids.add(department.id);
                                }
                            yield new ReportScope(false, ids, "6".equals(role.scope), user.id);
                        }
                        case "5" -> new ReportScope(false, Set.of(), true, user.id);
                        default -> throw denied();
                    };
            if (scope.departments().size() > 2000)
                throw new DatasetAccessException("CAPACITY_EXCEEDED", "部门权限范围超过首期查询上限");
            resolved.add(new ReportRole(role.id, permissions, scope));
        }
        return new ReportActor(tenant.id, user.id, user.department, false, resolved);
    }

    /**
     * 完成 {@code ancestor} 对应的系统报表授权适配器处理。
     *
     * @param ancestors 当前操作的 {@code ancestors} 输入
     * @param department 当前操作的 {@code department} 输入
     * @return 当前检查或条件操作是否成功
     */
    private static boolean ancestor(String ancestors, long department) {
        if (ancestors == null) return false;
        return Arrays.stream(ancestors.split(","))
                .anyMatch(value -> value.trim().equals(Long.toString(department)));
    }

    /**
     * 校验授权主体属于当前租户。
     *
     * @param tenant 当前租户标识
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     * @return 当前检查或条件操作是否成功
     */
    public boolean subjectExists(String tenant, String type, long id) {
        if (id <= 0) return false;
        String table, column;
        switch (type) {
            case "USER" -> {
                table = "sys_user";
                column = "user_id";
            }
            case "ROLE" -> {
                table = "sys_role";
                column = "role_id";
            }
            case "DEPT" -> {
                table = "sys_dept";
                column = "dept_id";
            }
            default -> {
                return false;
            }
        }
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + table
                                + " WHERE "
                                + column
                                + "=? AND tenant_id=? AND status='0' AND del_flag='0'",
                        Long.class,
                        id,
                        tenant);
        return count != null && count == 1;
    }

    /**
     * 完成 {@code denied} 对应的系统报表授权适配器处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetAccessException denied() {
        return new DatasetAccessException("SCOPE_DENIED", "报表执行主体、租户或授权配置无效");
    }
}
