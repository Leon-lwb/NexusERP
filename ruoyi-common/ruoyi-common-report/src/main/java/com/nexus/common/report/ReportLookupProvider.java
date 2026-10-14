package com.nexus.common.report;

import java.util.*;

public interface ReportLookupProvider {
    List<ReportLookupBinding> bindings();

    record Query(String keyword, int page, int size) {
        public Query {
            if (keyword == null) keyword = "";
            if (keyword.length() > 100 || page < 1 || page > 10000 || size < 1 || size > 50)
                throw new DatasetAccessException("INVALID_PARAMETER", "选择器查询范围无效");
        }
    }

    record Option(String value, String label) {}

    record Result(List<Option> options, long total) {
        public Result {
            options = List.copyOf(options);
        }
    }

    Result query(ReportLookupBinding binding, ReportExecutionContext context, Query query);
}
