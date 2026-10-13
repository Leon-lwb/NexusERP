package com.nexus.common.report;
import java.util.*;
public record ReportScope(boolean all, Set<Long> departments, boolean self, long userId) {
    public ReportScope { departments = Collections.unmodifiableSet(new TreeSet<>(departments == null ? Set.of() : departments)); }
    public static ReportScope all(long user) { return new ReportScope(true, Set.of(), false, user); }
    public static ReportScope none(long user) { return new ReportScope(false, Set.of(), false, user); }
    public boolean allows(Long department, Long owner) { return all || department != null && departments.contains(department) || self && owner != null && owner == userId; }
    public ReportScope union(ReportScope other) {
        if (userId != other.userId) throw new IllegalArgumentException("Scope actor mismatch");
        Set<Long> ids = new TreeSet<>(departments); ids.addAll(other.departments);
        return new ReportScope(all || other.all, ids, self || other.self, userId);
    }
}
