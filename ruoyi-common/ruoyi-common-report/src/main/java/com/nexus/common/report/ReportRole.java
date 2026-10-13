package com.nexus.common.report;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public record ReportRole(long roleId, Set<String> permissions, ReportScope scope) {
    public ReportRole { permissions = Collections.unmodifiableSet(new TreeSet<>(permissions)); Objects.requireNonNull(scope); }
    public boolean grants(String permission) {
        return permissions.stream().anyMatch(grant -> grant.equals("*:*:*") || grant.equals("*") ||
            permission.matches(Arrays.stream(grant.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*"))));
    }
}
