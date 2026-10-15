package com.nexus.report.version;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.pivot.*;

import java.nio.file.Path;
import java.util.*;

/** 固定第二版执行器，独立安装，绝不改写第一版发布语义。 */
public final class V2ReportEngineExecutor implements ReportEngineExecutor {
    /** 第二版编译仍复用经过验证的第一版基本字段规则。 */
    private final ReportCompiler compiler;

    /** 保存当前assets状态或配置。 */
    private final com.nexus.report.asset.ReportAssetService assets;

    /**
     * 建立本执行器自己的编译与精确运算入口。
     *
     * @param compiler 当前契约的compiler配置或状态
     */
    public V2ReportEngineExecutor(ReportCompiler compiler) {
        this(compiler, null);
    }

    /**
     * 接入私有资产权限和完整性读取。
     *
     * @param compiler 当前契约的compiler配置或状态
     * @param assets 当前契约的assets配置或状态
     */
    public V2ReportEngineExecutor(
            ReportCompiler compiler, com.nexus.report.asset.ReportAssetService assets) {
        this.compiler = compiler;
        this.assets = assets;
    }

    /**
     * 返回明确的定义版本。
     *
     * @return 当前受控处理的固定结果
     */
    public int schemaVersion() {
        return 2;
    }

    /**
     * 返回发布后固定的执行版本。
     *
     * @return 当前受控处理的固定结果
     */
    public String engineVersion() {
        return "2.0.0";
    }

    /**
     * 声明服务端已经安装的受控能力。
     *
     * @return 当前受控处理的固定结果
     */
    public Set<String> capabilities() {
        return Set.of(
                "TABLE",
                "DOCUMENT",
                "PIVOT",
                "EXACT_AVG",
                "SNAPSHOT_DRILL",
                "PDF",
                "XLSX_DATA",
                "XLSX_LAYOUT",
                "PRIVATE_IMAGES",
                "QR",
                "CODE128",
                "GROUP_PRINT",
                "REPEAT_FOOTER",
                "LANDSCAPE");
    }

    /**
     * 严格解析JSON，不容忍未知字段。
     *
     * @param text 当前契约的text配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDsl parse(String text) {
        var result = compiler.parse(text);
        if (result.schemaVersion() != 2) throw ReportFault.invalid("第二版执行器仅接受schema2");
        return result;
    }

    /**
     * 编译所有轴、指标和隐藏字段的权限，保留固定契约。
     *
     * @param dsl 不可变的报表定义
     * @param descriptor 当前契约的descriptor配置或状态
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public CompiledReport compile(ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor) {
        if (dsl.schemaVersion() != 2) throw ReportFault.invalid("报表格式与执行器不匹配");
        if ((dsl.kind() == ReportDsl.Kind.PIVOT) != (dsl.pivot() != null))
            throw ReportFault.invalid("交叉定义与报表类型不匹配");
        var projection =
                new ReportDsl(
                        1,
                        dsl.code(),
                        dsl.title(),
                        dsl.kind() == ReportDsl.Kind.PIVOT ? ReportDsl.Kind.TABLE : dsl.kind(),
                        dsl.datasetCode(),
                        dsl.datasetVersionId(),
                        dsl.columns(),
                        dsl.parameters(),
                        new com.nexus.report.print.PrintPlanner().paper(dsl),
                        dsl.headerRows(),
                        dsl.footerRows(),
                        dsl.summaries(),
                        dsl.repeatHeader());
        var base = compiler.compile(projection, descriptor, actor);
        var required = new LinkedHashSet<>(base.requiredFields());
        if (dsl.pivot() != null) {
            if (!base.expressions().isEmpty()
                    || !dsl.summaries().isEmpty()
                    || !dsl.headerRows().isEmpty()
                    || !dsl.footerRows().isEmpty())
                throw ReportFault.invalid("交叉表使用专用轴和指标，不混合公式与固定区");
            required.addAll(new PivotPlanner().validate(dsl.pivot(), descriptor));
        }
        required.addAll(new com.nexus.report.print.PrintPlanner().validate(dsl, descriptor));
        for (String field : required) {
            String permission = descriptor.field(field).permission();
            if (permission != null && !permission.isBlank() && !actor.has(permission))
                throw ReportFault.denied();
        }
        var rasters = new LinkedHashMap<String, String>();
        long resourceBytes = 0;
        if (dsl.print() != null)
            for (var media : dsl.print().media())
                if (media.kind().equals("IMAGE")) {
                    if (assets == null)
                        throw new ReportFault("PRINT_RESOURCE_UNAVAILABLE", "私有图片服务未安装", 409);
                    byte[] bytes = assets.bytes(actor, media.assetVersionId());
                    resourceBytes += bytes.length;
                    if (resourceBytes > 8 * 1024 * 1024) throw ReportFault.capacity();
                    rasters.put(media.assetVersionId(), Base64.getEncoder().encodeToString(bytes));
                }
        var result =
                new CompiledReport(
                        dsl,
                        descriptor,
                        required,
                        base.expressions(),
                        base.computedOrder(),
                        base.scopes(),
                        rasters);
        result.context(actor);
        return result;
    }

    /**
     * 快照始终保存精确原始字段，聚合在固定快照上执行。
     *
     * @param report 当前契约的report配置或状态
     * @param rows 精确类型的数据行
     * @param parameters 固定参数契约
     * @return 当前受控处理的固定结果
     */
    public ReportCalculator.CalculatedData calculate(
            CompiledReport report, List<Map<String, Object>> rows, Map<String, Object> parameters) {
        return new ReportCalculator().calculate(report, rows, parameters);
    }

    /**
     * 逐行转换可信字段，不在浏览器计算业务数值。
     *
     * @param report 当前契约的report配置或状态
     * @param row 当前契约的row配置或状态
     * @param parameters 固定参数契约
     * @return 当前受控处理的固定结果
     */
    public Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> row, Map<String, Object> parameters) {
        return new ReportCalculator().calculateRow(report, row, parameters);
    }

    /**
     * 为普通第二版报表保留明确的汇总会话。
     *
     * @param report 当前契约的report配置或状态
     * @return 当前受控处理的固定结果
     */
    public SummarySession summaries(CompiledReport report) {
        return new V1ReportEngineExecutor(compiler).summaries(report);
    }

    /**
     * 交叉表投影与普通打印共用固定字体和坐标。
     *
     * @param report 当前契约的report配置或状态
     * @param data 同一固定快照投影出的数据
     * @param parameters 固定参数契约
     * @param font 固定私有字体引用
     * @return 当前受控处理的固定结果
     */
    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font) {
        CompiledReport projected = report;
        var rendered = data;
        if (report.dsl().pivot() != null) {
            var result =
                    new AggregationExecutor()
                            .aggregate(report.dsl().pivot(), report.descriptor(), data.rows());
            var projection = new PivotProjection().project(report.dsl(), result);
            if (projection.dsl().columns().stream().mapToDouble(ReportDsl.Column::width).sum()
                    > report.dsl().page().usableWidth())
                throw new ReportFault("PIVOT_PRINT_WIDTH", "交叉表超出纸张宽度，请减少列维度或使用数据XLSX", 409);
            projected =
                    new CompiledReport(
                            projection.dsl(),
                            report.descriptor(),
                            report.requiredFields(),
                            Map.of(),
                            List.of(),
                            report.scopes());
            rendered = projection.data();
        }
        if (report.dsl().print() != null)
            return new com.nexus.report.print.PrintLayoutEnhancer()
                    .layout(report, data, parameters, font);
        var plan = new DocumentLayoutEngine().layout(projected, rendered, parameters, font);
        return new RenderPlan(
                2,
                data.rows().size(),
                plan.fontName(),
                plan.pages(),
                plan.columns(),
                plan.summaries());
    }
}
