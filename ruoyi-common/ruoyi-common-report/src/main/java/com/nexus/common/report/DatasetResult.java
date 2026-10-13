package com.nexus.common.report;
import java.util.*;
import java.time.Instant;
public record DatasetResult(List<Map<String,Object>> rows, long total, Instant dataTime) {
    public DatasetResult {
        rows = rows.stream().map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
        Objects.requireNonNull(dataTime);
    }
}
