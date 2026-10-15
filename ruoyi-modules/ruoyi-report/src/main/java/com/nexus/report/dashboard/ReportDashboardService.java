package com.nexus.report.dashboard;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.pivot.AggregationExecutor;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 仪表盘独立发布，组件按当前用户权限分别计算，不共享跨用户缓存。 */
public final class ReportDashboardService {
    /**
     * 联动只提交类型化值，目标和字段来自已发布白名单。
     *
     * @param sourceWidget 当前契约的sourceWidget配置或状态
     * @param targetWidget 当前契约的targetWidget配置或状态
     * @param sourceField 当前契约的sourceField配置或状态
     * @param value 当前契约的value配置或状态
     */
    public record Selection(
            String sourceWidget, String targetWidget, String sourceField, TypedValue value) {}

    /**
     * 每个组件保留自己的时间、来源和一致性，局部失败不影响其他组件。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param type 受控类型
     * @param title 当前契约的title配置或状态
     * @param layout 当前契约的layout配置或状态
     * @param reportVersionId 绑定的不可变报表发布版本
     * @param reportId 当前契约的reportId配置或状态
     * @param datasetCode 当前契约的datasetCode配置或状态
     * @param dataTime 当前契约的dataTime配置或状态
     * @param consistency 当前契约的consistency配置或状态
     * @param total 当前契约的total配置或状态
     * @param data 同一固定快照投影出的数据
     * @param error 当前契约的error配置或状态
     */
    public record View(
            String id,
            String type,
            String title,
            DashboardDefinition.Layout layout,
            String reportVersionId,
            String reportId,
            String datasetCode,
            String dataTime,
            String consistency,
            long total,
            Object data,
            String error) {}

    /**
     * 渲染响应只包含当前身份已授权的组件数据。
     *
     * @param versionId 不可变发布版本标识
     * @param refreshSeconds 当前契约的refreshSeconds配置或状态
     * @param widgets 当前契约的widgets配置或状态
     */
    public record Render(String versionId, int refreshSeconds, List<View> widgets) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param versionId 当前受控操作的versionId输入
         * @param refreshSeconds 当前受控操作的refreshSeconds输入
         * @param widgets 当前受控操作的widgets输入
         */
        public Render {
            widgets = List.copyOf(widgets);
        }
    }

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前store状态或配置。 */
    private final JdbcReportStore store;

    /** 保存当前definitions状态或配置。 */
    private final ReportDefinitionService definitions;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /** 保存当前widgets状态或配置。 */
    private final WidgetRegistry widgets;

    /** 保存主库硬配额服务。 */
    private final DashboardRefreshQuota quota;

    /**
     * 装配固定报告契约、独立资源版本和跨节点刷新配额。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportDashboardService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler json) {
        this.resources = resources;
        this.jdbc = jdbc;
        this.store = store;
        this.definitions = definitions;
        this.json = json;
        widgets = new WidgetRegistry(store, definitions);
        quota = new DashboardRefreshQuota(jdbc, resources);
    }

    /**
     * 重新解析当前主体及操作权限。
     *
     * @param actor 执行用户
     * @param permission 当前契约的permission配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor, String permission) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, permission);
        return current;
    }

    /**
     * 编辑器只获得已经授权的固定报告可见字段和参数。
     *
     * @param versionId 不可变发布版本标识
     * @param reportId 当前契约的reportId配置或状态
     * @param title 当前契约的title配置或状态
     * @param fields 当前契约的fields配置或状态
     * @param parameters 固定参数契约
     */
    public record Source(
            String versionId,
            String reportId,
            String title,
            List<DatasetField> fields,
            List<ReportDsl.Parameter> parameters) {}

    /**
     * 读取历史固定发布契约，不自动跟随报告当前版本。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @return 当前受控处理的固定结果
     */
    public Source source(ReportActor actor, String versionId) {
        var current = current(actor, "report:dashboard:edit");
        var version =
                store.version(current.tenantId(), versionId)
                        .orElseThrow(() -> new ReportFault("NOT_FOUND", "固定报告版本不存在", 404));
        var definition =
                definitions.definition(
                        current, version.reportId(), "VIEW", "report:runtime:preview");
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "报告已下线", 409);
        var binding = definitions.bindVersion(current, version);
        var visible =
                binding.compiled().dsl().columns().stream()
                        .map(ReportDsl.Column::field)
                        .collect(java.util.stream.Collectors.toSet());
        return new Source(
                version.id(),
                version.reportId(),
                binding.compiled().dsl().title(),
                binding.compiled().descriptor().fields().stream()
                        .filter(field -> visible.contains(field.name()))
                        .toList(),
                binding.compiled().dsl().parameters());
    }

    /**
     * 可见目录只返回资源元数据，避免将编辑定义透出。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Draft> list(ReportActor actor) {
        return resources.list("DASHBOARD", current(actor, "report:dashboard:list")).stream()
                .map(
                        d ->
                                new ReportResourceStore.Draft(
                                        d.id(),
                                        d.tenant(),
                                        d.code(),
                                        d.title(),
                                        null,
                                        d.revision(),
                                        d.publishedVersion(),
                                        d.status(),
                                        d.owner()))
                .toList();
    }

    /**
     * 编辑时校验固定报告契约。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft detail(ReportActor actor, String id) {
        var current = current(actor, "report:dashboard:edit");
        var draft = resources.access("DASHBOARD", current, id, "EDIT");
        widgets.validate(current, parse(draft.json()));
        return draft;
    }

    /**
     * 创建经过全量权限和契约检查的草稿。
     *
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param definition 当前契约的definition配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft create(
            ReportActor actor, String code, String title, DashboardDefinition definition) {
        var current = current(actor, "report:dashboard:edit");
        widgets.validate(current, definition);
        return resources.create("DASHBOARD", current, code, title, json.json(definition));
    }

    /**
     * 按revision保存，不改变已有组件发布版本。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param title 当前契约的title配置或状态
     * @param definition 当前契约的definition配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft save(
            ReportActor actor,
            String id,
            long revision,
            String title,
            DashboardDefinition definition) {
        var current = current(actor, "report:dashboard:edit");
        widgets.validate(current, definition);
        return resources.save("DASHBOARD", current, id, revision, json.json(definition), title);
    }

    /**
     * 在资源锁内冻结全部报告和组件依赖。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Version publish(ReportActor actor, String id, long revision) {
        var current = current(actor, "report:dashboard:publish");
        return resources.publish(
                "DASHBOARD",
                current,
                id,
                revision,
                (draft, number) -> {
                    var refreshed = current(current, "report:dashboard:publish");
                    var definition = parse(draft.json());
                    widgets.validate(refreshed, definition);
                    var dependencies = new LinkedHashMap<String, Object>();
                    for (var widget : definition.widgets()) {
                        var binding = widgets.binding(refreshed, widget);
                        dependencies.put(
                                widget.id(),
                                Map.of(
                                        "reportVersion",
                                        widget.reportVersionId(),
                                        "datasetVersion",
                                        binding.version().id(),
                                        "schema",
                                        binding.compiled().dsl().schemaVersion(),
                                        "componentVersion",
                                        widget.componentVersion()));
                    }
                    return json.json(dependencies);
                },
                () ->
                        resources.access(
                                "DASHBOARD",
                                current(current, "report:dashboard:publish"),
                                id,
                                "PUBLISH"));
    }

    /**
     * 下线会在后续刷新和收藏入口重新检查。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft state(
            ReportActor actor, String id, long revision, String state) {
        return resources.state(
                "DASHBOARD", current(actor, "report:dashboard:publish"), id, revision, state);
    }

    /**
     * 运行页读取发布参数及布局，已撤权组件不透出其业务字段与版本细节。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public DashboardDefinition published(ReportActor actor, String id) {
        var current = current(actor, "report:dashboard:list");
        var draft = resources.access("DASHBOARD", current, id, "VIEW");
        if (!draft.status().equals("PUBLISHED") || draft.publishedVersion() == null)
            throw new ReportFault("DASHBOARD_OFFLINE", "仪表盘已下线", 409);
        var definition =
                parse(
                        resources
                                .version("DASHBOARD", current.tenantId(), draft.publishedVersion())
                                .orElseThrow()
                                .json());
        var visible = new ArrayList<DashboardDefinition.Widget>();
        for (var widget : definition.widgets()) {
            try {
                widgets.binding(current, widget);
                visible.add(widget);
            } catch (ReportFault denied) {
                visible.add(
                        new DashboardDefinition.Widget(
                                widget.id(),
                                widget.type(),
                                widget.componentVersion(),
                                widget.title(),
                                null,
                                Map.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                widget.layout(),
                                List.of()));
            }
        }
        return new DashboardDefinition(
                definition.formatVersion(),
                definition.title(),
                definition.parameters(),
                visible,
                definition.refreshSeconds());
    }

    /**
     * 严格读取自有结构，不接受额外执行代码。
     *
     * @param text 当前契约的text配置或状态
     * @return 当前受控处理的固定结果
     */
    private DashboardDefinition parse(String text) {
        return json.read(text, DashboardDefinition.class);
    }

    /**
     * 刷新前后检查身份、固定发布和资源权限，每组件查询使用自身数据源时间。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param input 已规范化的参数值
     * @param selections 当前契约的selections配置或状态
     * @return 当前受控处理的固定结果
     */
    public Render render(
            ReportActor actor, String id, Map<String, Object> input, List<Selection> selections) {
        var current = current(actor, "report:dashboard:list");
        var draft = resources.access("DASHBOARD", current, id, "VIEW");
        if (!draft.status().equals("PUBLISHED") || draft.publishedVersion() == null)
            throw new ReportFault("DASHBOARD_OFFLINE", "仪表盘尚未发布或已下线", 409);
        var version =
                resources
                        .version("DASHBOARD", current.tenantId(), draft.publishedVersion())
                        .orElseThrow();
        var definition = parse(version.json());
        var parameters = parameters(definition, input);
        var overrides = links(current, definition, selections == null ? List.of() : selections);
        quota.reserve(current);
        var result = new ArrayList<View>();
        var successfulScopes = new LinkedHashMap<String, String>();
        for (var widget : definition.widgets()) {
            try {
                var refreshed = current(current, "report:dashboard:list");
                resources.access("DASHBOARD", refreshed, id, "VIEW");
                var binding = widgets.binding(refreshed, widget);
                String fingerprint = definitions.fingerprint(refreshed, binding);
                var child = new LinkedHashMap<String, Object>();
                for (var entry : widget.parameterBindings().entrySet())
                    if (parameters.containsKey(entry.getValue()))
                        child.put(entry.getKey(), parameters.get(entry.getValue()));
                child.putAll(overrides.getOrDefault(widget.id(), Map.of()));
                child = new LinkedHashMap<>(definitions.parameters(binding, child));
                var source = fetch(binding, refreshed, child);
                var calculated =
                        binding.engine().calculate(binding.compiled(), source.rows(), child);
                Object data;
                if (widget.type().equals("METRIC")) {
                    var field = binding.compiled().descriptor().field(widget.valueField());
                    var calculations = new WidgetCalculations();
                    var value = calculations.metric(field, widget.function(), calculated.rows());
                    TypedValue previous = null, rate = null;
                    if (widget.comparison() != null && !widget.comparison().equals("NONE")) {
                        var shifted = shift(widget, child);
                        var before = fetch(binding, refreshed, shifted);
                        var values =
                                binding.engine()
                                        .calculate(binding.compiled(), before.rows(), shifted);
                        calculations.comparable(
                                field, widget.function(), calculated.rows(), values.rows());
                        previous = calculations.metric(field, widget.function(), values.rows());
                        rate = calculations.rate(value, previous);
                    }
                    var metric = new LinkedHashMap<String, Object>();
                    metric.put("value", value);
                    metric.put("previous", previous);
                    metric.put("rate", rate);
                    data = metric;
                } else if (Set.of("BAR", "LINE", "PIE").contains(widget.type())) {
                    var chart =
                            new WidgetCalculations()
                                    .chart(
                                            widget.categoryField(),
                                            binding.compiled()
                                                    .descriptor()
                                                    .field(widget.valueField()),
                                            widget.function(),
                                            calculated.rows());
                    if (widget.type().equals("PIE")
                            && chart.points().stream().anyMatch(point -> point.coordinate() < 0))
                        throw ReportFault.invalid("饼图不支持负数");
                    data = chart;
                } else if (widget.type().equals("PIVOT"))
                    data =
                            new AggregationExecutor()
                                    .aggregate(
                                            binding.compiled().dsl().pivot(),
                                            binding.compiled().descriptor(),
                                            calculated.rows());
                else
                    data =
                            Map.of(
                                    "columns",
                                    binding.compiled().dsl().columns(),
                                    "rows",
                                    ReportDefinitionService.project(
                                            calculated.rows().stream().limit(200).toList(),
                                            binding.compiled().dsl()),
                                    "visibleRows",
                                    Math.min(200, calculated.rows().size()));
                var after = current(refreshed, "report:dashboard:list");
                resources.access("DASHBOARD", after, id, "VIEW");
                var still = resources.read("DASHBOARD", after.tenantId(), id).orElseThrow();
                if (!still.status().equals("PUBLISHED"))
                    throw new ReportFault("DASHBOARD_OFFLINE", "仪表盘已下线", 409);
                if (!fingerprint.equals(
                        definitions.fingerprint(after, widgets.binding(after, widget))))
                    throw new ReportFault("SCOPE_CHANGED", "组件权限范围已变化", 403);
                var fixed = store.version(after.tenantId(), widget.reportVersionId()).orElseThrow();
                result.add(
                        new View(
                                widget.id(),
                                widget.type(),
                                widget.title(),
                                widget.layout(),
                                widget.reportVersionId(),
                                fixed.reportId(),
                                binding.compiled().descriptor().code(),
                                source.dataTime().toString(),
                                "SNAPSHOT",
                                source.total(),
                                data,
                                null));
                successfulScopes.put(widget.id(), fingerprint);
            } catch (ReportFault | DatasetAccessException failure) {
                result.add(
                        new View(
                                widget.id(),
                                widget.type(),
                                widget.title(),
                                widget.layout(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                0,
                                null,
                                com.nexus.report.runtime.ReportRuntimeService.code(failure)));
            }
        }
        var after = current(current, "report:dashboard:list");
        var still = resources.access("DASHBOARD", after, id, "VIEW");
        if (!still.status().equals("PUBLISHED"))
            throw new ReportFault("DASHBOARD_OFFLINE", "仪表盘已下线", 409);
        for (int i = 0; i < result.size(); i++) {
            var view = result.get(i);
            if (view.error() != null) continue;
            var widget =
                    definition.widgets().stream()
                            .filter(w -> w.id().equals(view.id()))
                            .findFirst()
                            .orElseThrow();
            try {
                var finalActor = current(after, "report:dashboard:list");
                resources.access("DASHBOARD", finalActor, id, "VIEW");
                if (!successfulScopes
                        .get(widget.id())
                        .equals(
                                definitions.fingerprint(
                                        finalActor, widgets.binding(finalActor, widget))))
                    throw new ReportFault("SCOPE_CHANGED", "最终交付时组件范围已变化", 403);
            } catch (ReportFault denied) {
                result.set(
                        i,
                        new View(
                                view.id(),
                                view.type(),
                                view.title(),
                                view.layout(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                0,
                                null,
                                denied.code()));
            }
        }
        return new Render(version.id(), definition.refreshSeconds(), result);
    }

    /**
     * 所有源记录在服务器完整计算，超过五千行明确失败，表格预览只投影前二百行。
     *
     * @param binding 当前契约的binding配置或状态
     * @param actor 执行用户
     * @param parameters 固定参数契约
     * @return 当前受控处理的固定结果
     */
    private DatasetResult fetch(
            ReportDefinitionService.Binding binding,
            ReportActor actor,
            Map<String, Object> parameters) {
        var source =
                binding.provider()
                        .query(
                                binding.compiled().context(actor),
                                new DatasetQuery(
                                        parameters,
                                        binding.compiled().requiredFields(),
                                        1,
                                        5001,
                                        true,
                                        null,
                                        false));
        if (source.total() > 5000 || source.rows().size() > 5000) throw ReportFault.capacity();
        if (source.total() != source.rows().size())
            throw new ReportFault("QUERY_PARTIAL", "组件取数没有完整返回，不能计算完整指标", 409);
        return source;
    }

    /**
     * 主参数使用可信类型转换，雪花ID和DECIMAL不转为前端Number。
     *
     * @param definition 当前契约的definition配置或状态
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    private Map<String, Object> parameters(
            DashboardDefinition definition, Map<String, Object> input) {
        var descriptor =
                new DatasetDescriptor(
                        "DASHBOARD_PARAMS",
                        "仪表盘参数",
                        1,
                        "report:dashboard:list",
                        false,
                        List.of(),
                        definition.parameters().stream()
                                .map(
                                        p ->
                                                new DatasetParameter(
                                                        p.name(),
                                                        p.label(),
                                                        ValueType.valueOf(p.type()),
                                                        p.required(),
                                                        p.defaultValue()))
                                .toList(),
                        List.of());
        return DatasetQueryValidator.parameters(descriptor, input == null ? Map.of() : input);
    }

    /**
     * 联动只能沿已发布映射，并重新核对源和目标的当前权限。
     *
     * @param actor 执行用户
     * @param definition 当前契约的definition配置或状态
     * @param selections 当前契约的selections配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, Map<String, Object>> links(
            ReportActor actor, DashboardDefinition definition, List<Selection> selections) {
        if (selections.size() > 12) throw ReportFault.capacity();
        var result = new LinkedHashMap<String, Map<String, Object>>();
        for (var selection : selections) {
            var source =
                    definition.widgets().stream()
                            .filter(w -> w.id().equals(selection.sourceWidget()))
                            .findFirst()
                            .orElseThrow(() -> ReportFault.invalid("联动源无效"));
            var target =
                    definition.widgets().stream()
                            .filter(w -> w.id().equals(selection.targetWidget()))
                            .findFirst()
                            .orElseThrow(() -> ReportFault.invalid("联动目标无效"));
            var link =
                    source.links().stream()
                            .filter(
                                    l ->
                                            l.targetWidget().equals(target.id())
                                                    && l.sourceField()
                                                            .equals(selection.sourceField()))
                            .findFirst()
                            .orElseThrow(() -> ReportFault.invalid("联动未在发布定义批准"));
            var sourceBinding = widgets.binding(actor, source);
            var targetBinding = widgets.binding(actor, target);
            if (selection.value() == null
                    || selection.value().type()
                            != sourceBinding
                                    .compiled()
                                    .descriptor()
                                    .field(link.sourceField())
                                    .type()) throw ReportFault.invalid("联动值类型无效");
            var value = selection.value().value();
            if (value == null) continue;
            var parameter =
                    targetBinding.compiled().descriptor().parameters().stream()
                            .filter(p -> p.name().equals(link.targetParameter()))
                            .findFirst()
                            .orElseThrow();
            var descriptor =
                    new DatasetDescriptor(
                            "LINK_PARAM",
                            "联动参数",
                            1,
                            "report:dashboard:list",
                            false,
                            List.of(),
                            List.of(
                                    new DatasetParameter(
                                            parameter.name(),
                                            parameter.label(),
                                            parameter.type(),
                                            false,
                                            null)),
                            List.of());
            var normalized =
                    DatasetQueryValidator.parameters(
                            descriptor, Map.of(link.targetParameter(), value));
            result.computeIfAbsent(target.id(), key -> new LinkedHashMap<>())
                    .put(link.targetParameter(), normalized.get(link.targetParameter()));
        }
        return result;
    }

    /**
     * 同比按日历年移动，环比按包含两端的天数移动，最多一年区间。
     *
     * @param widget 当前契约的widget配置或状态
     * @param source 当前契约的source配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, Object> shift(
            DashboardDefinition.Widget widget, Map<String, Object> source) {
        LocalDate start, end;
        try {
            start = LocalDate.parse(Objects.toString(source.get(widget.startParameter())));
            end = LocalDate.parse(Objects.toString(source.get(widget.endParameter())));
        } catch (Exception invalid) {
            throw ReportFault.invalid("指标比较需要有效起止日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > 366) throw ReportFault.invalid("指标期间范围1–366天");
        var result = new LinkedHashMap<>(source);
        result.put(
                widget.startParameter(),
                widget.comparison().equals("PREVIOUS_YEAR")
                        ? start.minusYears(1)
                        : start.minusDays(days));
        result.put(
                widget.endParameter(),
                widget.comparison().equals("PREVIOUS_YEAR")
                        ? end.minusYears(1)
                        : end.minusDays(days));
        return result;
    }

    /**
     * 收藏是显式个人操作，仍要求仪表盘资源访问。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param enabled 当前契约的enabled配置或状态
     */
    public void favorite(ReportActor actor, String id, boolean enabled) {
        var current = current(actor, "report:dashboard:list");
        resources.access("DASHBOARD", current, id, "VIEW");
        if (enabled) {
            try {
                jdbc.update(
                        "INSERT INTO rpt_dashboard_favorite(tenant_id,user_id,dashboard_id)"
                                + " VALUES(?,?,?)",
                        current.tenantId(),
                        current.userId(),
                        Long.parseLong(id));
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            }
        } else
            jdbc.update(
                    "DELETE FROM rpt_dashboard_favorite WHERE tenant_id=? AND user_id=? AND"
                            + " dashboard_id=?",
                    current.tenantId(),
                    current.userId(),
                    Long.parseLong(id));
    }

    /**
     * 收藏目录过滤已经撤销访问的资源，收藏记录不成为授权依据。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<String> favorites(ReportActor actor) {
        var current = current(actor, "report:dashboard:list");
        var visible =
                resources.list("DASHBOARD", current).stream()
                        .filter(d -> d.status().equals("PUBLISHED"))
                        .map(ReportResourceStore.Draft::id)
                        .collect(java.util.stream.Collectors.toSet());
        return jdbc
                .queryForList(
                        "SELECT dashboard_id FROM rpt_dashboard_favorite WHERE tenant_id=? AND"
                                + " user_id=?",
                        Long.class,
                        current.tenantId(),
                        current.userId())
                .stream()
                .map(Object::toString)
                .filter(visible::contains)
                .toList();
    }
}
