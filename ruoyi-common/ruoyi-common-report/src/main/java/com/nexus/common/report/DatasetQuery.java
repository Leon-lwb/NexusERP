package com.nexus.common.report;
import java.util.*;
public record DatasetQuery(Map<String, Object> parameters, Set<String> fields, int pageNumber, int pageSize,
    boolean snapshot, String sortField, boolean descending) {
    public DatasetQuery {
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
        fields = Collections.unmodifiableSet(new LinkedHashSet<>(fields));
    }
}
