package com.nexus.common.report;
import java.util.*;
public record ReportActor(String tenantId, long userId, long departmentId, boolean superAdmin, List<ReportRole> roles) {
    public ReportActor {
        if (tenantId == null || tenantId.isBlank() || !tenantId.matches("[A-Za-z0-9_-]{1,20}") || userId <= 0) throw new IllegalArgumentException("Explicit report tenant and actor required");
        roles = List.copyOf(roles == null ? List.of() : roles);
    }
    public boolean has(String permission) { return superAdmin || roles.stream().anyMatch(role -> role.grants(permission)); }
    public ReportScope scopeFor(String permission) {
        if (superAdmin) return ReportScope.all(userId);
        return roles.stream().filter(role -> role.grants(permission)).map(ReportRole::scope).reduce(ReportScope.none(userId), ReportScope::union);
    }
    public Set<Long> roleIds() { Set<Long> ids = new TreeSet<>(); roles.forEach(role -> ids.add(role.roleId())); return Collections.unmodifiableSet(ids); }
}
