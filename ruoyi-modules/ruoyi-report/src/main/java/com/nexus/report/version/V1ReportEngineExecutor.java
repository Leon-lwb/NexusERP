package com.nexus.report.version;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import java.nio.file.Path;
import java.util.*;

/** Existing v1 implementation kept behind its original immutable identity. */
public class V1ReportEngineExecutor implements ReportEngineExecutor {
    private final ReportCompiler compiler;

    public V1ReportEngineExecutor(ReportCompiler compiler) {
        this.compiler = compiler;
    }

    public int schemaVersion() {
        return 1;
    }

    public String engineVersion() {
        return ReportCompiler.ENGINE_VERSION;
    }

    public Set<String> capabilities() {
        return Set.of(
                "TABLE",
                "DOCUMENT",
                "PDF",
                "XLSX_DATA",
                "XLSX_LAYOUT",
                "FIXED_BANDS",
                "EXACT_VALUES");
    }

    public ReportDsl parse(String json) {
        return compiler.parse(json);
    }

    public CompiledReport compile(ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor) {
        return compiler.compile(dsl, descriptor, actor);
    }

    public ReportCalculator.CalculatedData calculate(
            CompiledReport report, List<Map<String, Object>> rows, Map<String, Object> parameters) {
        return new ReportCalculator().calculate(report, rows, parameters);
    }

    public Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> row, Map<String, Object> parameters) {
        return new ReportCalculator().calculateRow(report, row, parameters);
    }

    public SummarySession summaries(CompiledReport report) {
        var delegate = new ReportCalculator.SummaryAccumulator(report);
        return new SummarySession() {
            public void accept(Map<String, TypedValue> row) {
                delegate.accept(row);
            }

            public List<ReportCalculator.SummaryValue> finish() {
                return delegate.finish();
            }
        };
    }

    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font) {
        return new DocumentLayoutEngine().layout(report, data, parameters, font);
    }
}
