package com.nexus.report.version;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import java.nio.file.Path;
import java.util.*;

/** Code-installed execution contract. Published engine identities never follow a new default. */
public interface ReportEngineExecutor {
    int schemaVersion();

    String engineVersion();

    Set<String> capabilities();

    ReportDsl parse(String json);

    CompiledReport compile(ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor);

    ReportCalculator.CalculatedData calculate(
            CompiledReport report, List<Map<String, Object>> rows, Map<String, Object> parameters);

    Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> row, Map<String, Object> parameters);

    interface SummarySession {
        void accept(Map<String, TypedValue> row);

        List<ReportCalculator.SummaryValue> finish();
    }

    SummarySession summaries(CompiledReport report);

    RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font);
}
