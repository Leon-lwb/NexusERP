package com.nexus.report.engine;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/** 仅编译有界且受支持的一版定义，检查全部字段依赖的授权。 */
public final class ReportCompiler {
    /** 保存当前 {@code ENGINEVERSION} 值。 */
    public static final String ENGINE_VERSION = "1.0.0";

    /** 保存当前 {@code BINDING} 值。 */
    private static final Pattern BINDING =
            Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}", Pattern.UNICODE_CASE);

    /** 待处理定义文本。 */
    private final ObjectMapper json;

    /**
     * 初始化报表Compiler并保存所需依赖或不可变值。
     *
     * @param json 待处理定义文本
     */
    public ReportCompiler(ObjectMapper json) {
        this.json =
                json.copy()
                        .enable(
                                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.json
                .coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.json
                .getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxNestingDepth(64)
                                .maxNumberLength(128)
                                .maxStringLength(10_000)
                                .build());
    }

    /**
     * 解析并校验受支持的定义结构。
     *
     * @param text 当前操作的 {@code text} 输入
     * @return 当前处理返回的固定结果
     */
    public ReportDsl parse(String text) {
        if (text == null || text.length() > 2_000_000) throw ReportFault.capacity();
        try {
            ReportDsl result = json.readValue(text, ReportDsl.class);
            if (result == null) throw ReportFault.invalid("模板不能为空");
            return result;
        } catch (Exception e) {
            throw ReportFault.invalid("模板 JSON 包含未知字段、非法类型或无效格式");
        }
    }

    /**
     * 完成 {@code json} 对应的报表Compiler处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new ReportFault("SERIALIZATION_FAILED", "报表定义无法序列化", 500);
        }
    }

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param text 当前操作的 {@code text} 输入
     * @param type 当前操作的 {@code type} 输入
     * @return 当前处理返回的固定结果
     */
    public <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (Exception e) {
            throw ReportFault.invalid("报表存储版本无法解析");
        }
    }

    /**
     * 编译定义并检查字段、类型和业务权限。
     *
     * @param dsl 报表定义
     * @param dataset 当前操作的 {@code dataset} 输入
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public CompiledReport compile(ReportDsl dsl, DatasetDescriptor dataset, ReportActor actor) {
        if (dsl == null || dataset == null || actor == null)
            throw ReportFault.invalid("报表、数据集和执行身份必须明确");
        if (dsl.schemaVersion() != 1
                || dsl.pivot() != null
                || dsl.print() != null
                || dsl.kind() == ReportDsl.Kind.PIVOT)
            throw new ReportFault("VERSION_UNSUPPORTED", "仅支持报表格式 v1");
        if (dsl.kind() == null || dsl.code() == null || !dsl.code().matches("[A-Z][A-Z0-9_]{0,63}"))
            throw ReportFault.invalid("报表编码或类型无效");
        if (dsl.title() == null || dsl.title().isBlank() || dsl.title().length() > 100)
            throw ReportFault.invalid("报表标题长度为 1–100");
        if (!dataset.code().equals(dsl.datasetCode())
                || dsl.datasetVersionId() == null
                || !dsl.datasetVersionId().matches("[1-9][0-9]{0,18}")
                || new BigInteger(dsl.datasetVersionId()).bitLength() > 63)
            throw ReportFault.invalid("需要明确的数据集及发布版本");
        if (!actor.has(dataset.permission())) throw ReportFault.denied();
        if (dsl.columns().isEmpty() || dsl.columns().size() > 40)
            throw ReportFault.invalid("报表列数范围为 1–40");
        if (dsl.summaries().size() > 20 || dsl.parameters().size() > 40)
            throw ReportFault.capacity();
        validatePage(dsl);
        Map<String, DatasetField> fields = new LinkedHashMap<>();
        for (DatasetField field : dataset.fields()) {
            validateName(field.name());
            validateBindingName(field.name());
            if (fields.put(field.name(), field) != null) throw ReportFault.invalid("数据集字段重名");
        }
        Map<String, DatasetParameter> parameterCatalog = new LinkedHashMap<>();
        for (DatasetParameter parameter : dataset.parameters()) {
            validateName(parameter.name());
            if (parameter.type() == null
                    || parameterCatalog.put(parameter.name(), parameter) != null)
                throw ReportFault.invalid("数据集参数重名或类型无效");
        }
        Set<String> parameters = new LinkedHashSet<>();
        for (ReportDsl.Parameter parameter : dsl.parameters()) {
            validateName(parameter.name());
            DatasetParameter registered = parameterCatalog.get(parameter.name());
            if (registered == null
                    || !registered.type().name().equals(parameter.type())
                    || registered.required() && !parameter.required()
                    || !parameters.add(parameter.name()))
                throw ReportFault.invalid("参数定义与可信数据集不匹配");
        }
        for (DatasetParameter parameter : dataset.parameters())
            if (parameter.required() && !parameters.contains(parameter.name()))
                throw ReportFault.invalid("缺少必填数据集参数: " + parameter.name());
        Map<String, ReportDsl.Column> columns = new LinkedHashMap<>();
        Map<String, SafeExpression> expressions = new LinkedHashMap<>();
        for (ReportDsl.Column column : dsl.columns()) {
            validateName(column.field());
            validateBindingName(column.field());
            if (columns.put(column.field(), column) != null) throw ReportFault.invalid("报表列名重名");
            if (column.label() == null
                    || column.label().length() > 100
                    || !Double.isFinite(column.width())
                    || column.width() < 20
                    || column.width() > 600
                    || column.scale() < 0
                    || column.scale() > 12) throw ReportFault.invalid("列标题、宽度或精度无效");
            validateStyle(column.style());
            if (column.expression() != null && !column.expression().isBlank()) {
                if (fields.containsKey(column.field())) throw ReportFault.invalid("计算列必须使用独立字段名");
                expressions.put(column.field(), SafeExpression.compile(column.expression()));
            }
        }
        Set<String> required = new LinkedHashSet<>();
        for (ReportDsl.Column column : dsl.columns())
            if (!expressions.containsKey(column.field()))
                requireField(column.field(), fields, actor, required);
        for (SafeExpression expression : expressions.values())
            for (String dependency : expression.dependencies()) {
                if (expressions.containsKey(dependency)
                        || dependency.startsWith("p_")
                                && parameters.contains(dependency.substring(2))) continue;
                requireField(dependency, fields, actor, required);
            }
        List<String> order = new ArrayList<>();
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String name : expressions.keySet()) visit(name, expressions, visiting, visited, order);
        Map<String, ValueType> types = new HashMap<>();
        fields.forEach((name, field) -> types.put(name, field.type()));
        parameters.forEach(name -> types.put("p_" + name, parameterCatalog.get(name).type()));
        Map<String, Set<String>> baseFields = new HashMap<>();
        Map<String, Boolean> aggregates = new HashMap<>();
        for (String name : order) {
            SafeExpression expression = expressions.get(name);
            types.put(name, expression.validateTypes(types));
            for (String dependency : expression.aggregateDependencies())
                if (Boolean.TRUE.equals(aggregates.get(dependency)))
                    throw ReportFault.invalid("不支持通过计算列嵌套聚合函数");
            Set<String> bases = new LinkedHashSet<>();
            for (String dependency : expression.dependencies())
                bases.addAll(baseFields.getOrDefault(dependency, Set.of(dependency)));
            baseFields.put(name, Set.copyOf(bases));
            aggregates.put(
                    name,
                    expression.aggregate()
                            || expression.dependencies().stream()
                                    .anyMatch(
                                            dependency ->
                                                    Boolean.TRUE.equals(
                                                            aggregates.get(dependency))));
            for (String dependency : expression.measureDependencies())
                for (String base : baseFields.getOrDefault(dependency, Set.of(dependency))) {
                    DatasetField field = fields.get(base);
                    if (field == null) continue;
                    if (Boolean.FALSE.equals(field.aggregatable()))
                        throw ReportFault.invalid("该度量不允许数值聚合");
                    if (!field.aggregationKeys().isEmpty())
                        throw ReportFault.invalid("带币种或单位的度量必须使用分组汇总，不能放入未分区的聚合公式");
                }
        }
        validateBand(
                dsl.headerRows(),
                dsl.columns().size(),
                fields,
                expressions,
                parameters,
                actor,
                required);
        validateBand(
                dsl.footerRows(),
                dsl.columns().size(),
                fields,
                expressions,
                parameters,
                actor,
                required);
        for (ReportDsl.Summary summary : dsl.summaries()) {
            if (summary.function() == null
                    || !Set.of("SUM", "COUNT", "COUNT_ROWS", "MIN", "MAX")
                            .contains(summary.function())
                    || summary.scale() < 0
                    || summary.scale() > 12
                    || summary.groupBy().size() > 3
                    || new HashSet<>(summary.groupBy()).size() != summary.groupBy().size()
                    || summary.label() != null && summary.label().length() > 100)
                throw ReportFault.invalid("汇总规则无效");
            if (summary.function().equals("COUNT_ROWS")
                    && summary.field() != null
                    && !summary.field().isBlank()) throw ReportFault.invalid("COUNT_ROWS 不接受字段绑定");
            for (String group : summary.groupBy()) requireField(group, fields, actor, required);
            if (!summary.function().equals("COUNT_ROWS")) {
                if (expressions.containsKey(summary.field()))
                    throw ReportFault.invalid("首期汇总必须引用可信数据字段");
                requireField(summary.field(), fields, actor, required);
                DatasetField field = fields.get(summary.field());
                if (!summary.function().equals("COUNT")
                        && (field.type() != ValueType.DECIMAL && field.type() != ValueType.INTEGER))
                    throw ReportFault.invalid("数值汇总需要数值字段");
                if (!summary.function().equals("COUNT")
                        && Boolean.FALSE.equals(field.aggregatable()))
                    throw ReportFault.invalid("该度量不允许数值汇总");
                if (!summary.function().equals("COUNT")
                        && !summary.groupBy().containsAll(field.aggregationKeys()))
                    throw ReportFault.invalid("汇总必须保留币种/物料/单位分区");
            }
        }
        List<ReportScope> scopes = new ArrayList<>();
        scopes.add(actor.scopeFor(dataset.permission()));
        Set<String> extraPermissions = new TreeSet<>();
        for (String name : required) {
            String permission = fields.get(name).permission();
            if (permission != null && !permission.isBlank()) extraPermissions.add(permission);
        }
        extraPermissions.forEach(permission -> scopes.add(actor.scopeFor(permission)));
        return new CompiledReport(dsl, dataset, required, expressions, order, scopes);
    }

    /**
     * 完成 {@code visit} 对应的报表Compiler处理。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param expressions 当前操作的 {@code expressions} 输入
     * @param visiting 当前操作的 {@code visiting} 输入
     * @param visited 当前操作的 {@code visited} 输入
     * @param result 当前操作的 {@code result} 输入
     */
    private static void visit(
            String name,
            Map<String, SafeExpression> expressions,
            Set<String> visiting,
            Set<String> visited,
            List<String> result) {
        if (visited.contains(name)) return;
        if (!visiting.add(name)) throw ReportFault.invalid("计算列存在循环依赖");
        for (String dependency : expressions.get(name).dependencies())
            if (expressions.containsKey(dependency))
                visit(dependency, expressions, visiting, visited, result);
        visiting.remove(name);
        visited.add(name);
        result.add(name);
    }

    /**
     * 完成 {@code requireField} 对应的报表Compiler处理。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param fields 允许访问的字段
     * @param actor 当前服务端身份
     * @param required 当前操作的 {@code required} 输入
     */
    private static void requireField(
            String name,
            Map<String, DatasetField> fields,
            ReportActor actor,
            Set<String> required) {
        DatasetField field = fields.get(name);
        if (field == null) throw ReportFault.invalid("未登记的字段: " + name);
        if (field.permission() != null
                && !field.permission().isBlank()
                && !actor.has(field.permission())) throw ReportFault.denied();
        required.add(name);
    }

    /**
     * 校验Name的受限结构及输入边界。
     *
     * @param name 当前操作的 {@code name} 输入
     */
    private static void validateName(String name) {
        if (name == null
                || Set.of("__proto__", "constructor", "prototype").contains(name)
                || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
            throw ReportFault.invalid("字段或参数名称无效");
    }

    /**
     * 校验绑定Name的受限结构及输入边界。
     *
     * @param name 当前操作的 {@code name} 输入
     */
    private static void validateBindingName(String name) {
        if (name.startsWith("p_")
                || Set.of(
                                "row_count",
                                "page_no",
                                "page_count",
                                "TRUE",
                                "FALSE",
                                "NULL",
                                "AND",
                                "OR",
                                "NOT",
                                "EOF")
                        .contains(name)) throw ReportFault.invalid("字段名与保留绑定冲突");
    }

    /**
     * 校验Style的受限结构及输入边界。
     *
     * @param style 当前操作的 {@code style} 输入
     */
    private static void validateStyle(ReportDsl.Style style) {
        if (!Double.isFinite(style.fontSize())
                || style.fontSize() < 6
                || style.fontSize() > 32
                || !Set.of("LEFT", "CENTER", "RIGHT").contains(style.align())
                || !style.color().matches("#[0-9a-fA-F]{6}")
                || !style.background().matches("#[0-9a-fA-F]{6}"))
            throw ReportFault.invalid("样式超出支持范围");
    }

    /**
     * 校验Page的受限结构及输入边界。
     *
     * @param dsl 报表定义
     */
    private static void validatePage(ReportDsl dsl) {
        var p = dsl.page();
        double[] dimensions = {
            p.width(),
            p.height(),
            p.marginTop(),
            p.marginRight(),
            p.marginBottom(),
            p.marginLeft(),
            p.rowHeight()
        };
        for (double value : dimensions)
            if (!Double.isFinite(value)) throw ReportFault.invalid("纸张尺寸无效");
        if (p.width() < 150
                || p.width() > 1200
                || p.height() < 200
                || p.height() > 1700
                || p.marginTop() < 0
                || p.marginRight() < 0
                || p.marginBottom() < 0
                || p.marginLeft() < 0
                || p.usableWidth() < 40
                || p.height() - p.marginTop() - p.marginBottom() < 80
                || p.rowHeight() < 12
                || p.rowHeight() > 160) throw ReportFault.invalid("纸张尺寸或边距无效");
        if (dsl.kind() == ReportDsl.Kind.DOCUMENT
                && dsl.columns().stream().mapToDouble(ReportDsl.Column::width).sum()
                        > p.usableWidth() + 0.01) throw ReportFault.invalid("列总宽超出纸张，请调整列宽");
    }

    /**
     * 校验Band的受限结构及输入边界。
     *
     * @param rows 待处理业务行
     * @param columns 当前操作的 {@code columns} 输入
     * @param fields 允许访问的字段
     * @param computed 当前操作的 {@code computed} 输入
     * @param parameters 按契约解析的输入参数
     * @param actor 当前服务端身份
     * @param required 当前操作的 {@code required} 输入
     */
    private static void validateBand(
            List<ReportDsl.BandRow> rows,
            int columns,
            Map<String, DatasetField> fields,
            Map<String, SafeExpression> computed,
            Set<String> parameters,
            ReportActor actor,
            Set<String> required) {
        if (rows.size() > 20) throw ReportFault.capacity();
        boolean[][] used = new boolean[rows.size()][columns];
        for (int r = 0; r < rows.size(); r++) {
            var row = rows.get(r);
            if (!Double.isFinite(row.height()) || row.height() < 12 || row.height() > 160)
                throw ReportFault.invalid("固定区行高无效");
            if (row.cells().size() > columns) throw ReportFault.capacity();
            for (var cell : row.cells()) {
                if (cell.column() < 0
                        || cell.column() >= columns
                        || cell.colSpan() < 1
                        || cell.rowSpan() < 1
                        || cell.colSpan() > columns - cell.column()
                        || cell.rowSpan() > rows.size() - r) throw ReportFault.invalid("固定区合并越界");
                for (int rr = r; rr < r + cell.rowSpan(); rr++)
                    for (int cc = cell.column(); cc < cell.column() + cell.colSpan(); cc++) {
                        if (used[rr][cc]) throw ReportFault.invalid("固定区合并重叠");
                        used[rr][cc] = true;
                    }
                validateStyle(cell.style());
                if (cell.text() != null && cell.text().length() > 1000)
                    throw ReportFault.capacity();
                if (cell.field() != null && !cell.field().isBlank())
                    binding(cell.field(), fields, computed, parameters, actor, required);
                if (cell.text() != null) {
                    var match = BINDING.matcher(cell.text());
                    while (match.find())
                        binding(match.group(1), fields, computed, parameters, actor, required);
                    String literal = BINDING.matcher(cell.text()).replaceAll("");
                    if (literal.contains("{{") || literal.contains("}}"))
                        throw ReportFault.invalid("固定区字段绑定语法无效");
                }
            }
        }
    }

    /**
     * 完成 {@code binding} 对应的报表Compiler处理。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param fields 允许访问的字段
     * @param computed 当前操作的 {@code computed} 输入
     * @param parameters 按契约解析的输入参数
     * @param actor 当前服务端身份
     * @param required 当前操作的 {@code required} 输入
     */
    private static void binding(
            String name,
            Map<String, DatasetField> fields,
            Map<String, SafeExpression> computed,
            Set<String> parameters,
            ReportActor actor,
            Set<String> required) {
        if (Set.of("row_count", "page_no", "page_count").contains(name)
                || name.startsWith("p_") && parameters.contains(name.substring(2))
                || computed.containsKey(name)) return;
        requireField(name, fields, actor, required);
    }
}
