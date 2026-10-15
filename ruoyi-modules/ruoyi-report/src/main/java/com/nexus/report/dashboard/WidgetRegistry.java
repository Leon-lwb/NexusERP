package com.nexus.report.dashboard;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.service.ReportDefinitionService;

import java.util.*;

/** 安装的仪表盘组件目录及发布期的报告、参数、字段校验。 */
public final class WidgetRegistry {
    /** 保存当前store状态或配置。 */
    private final JdbcReportStore store;

    /** 保存当前definitions状态或配置。 */
    private final ReportDefinitionService definitions;

    /**
     * 固定元数据及报告执行器依赖。
     *
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     */
    public WidgetRegistry(JdbcReportStore store, ReportDefinitionService definitions) {
        this.store = store;
        this.definitions = definitions;
    }

    /**
     * 安装的组件版本仅为1，升级必须注册新的明确定义。
     *
     * @return 当前受控处理的固定结果
     */
    public Set<String> installed() {
        return Set.of("METRIC", "BAR", "LINE", "PIE", "TABLE", "PIVOT");
    }

    /**
     * 每个组件重新校验仍上线的固定报告版本和业务权限。
     *
     * @param actor 执行用户
     * @param widget 当前契约的widget配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDefinitionService.Binding binding(
            ReportActor actor, DashboardDefinition.Widget widget) {
        var version =
                store.version(actor.tenantId(), widget.reportVersionId())
                        .orElseThrow(() -> new ReportFault("NOT_FOUND", "仪表盘固定报告版本不存在", 404));
        var definition =
                definitions.definition(actor, version.reportId(), "VIEW", "report:runtime:preview");
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "组件报告已下线", 409);
        return definitions.bindVersion(actor, version);
    }

    /**
     * 发布时完整校验组件契约和类型化联动，拒绝未知客户端属性。
     *
     * @param actor 执行用户
     * @param dashboard 当前契约的dashboard配置或状态
     */
    public void validate(ReportActor actor, DashboardDefinition dashboard) {
        if (dashboard == null
                || dashboard.formatVersion() != 1
                || dashboard.title() == null
                || dashboard.title().isBlank()
                || dashboard.title().length() > 100
                || dashboard.widgets().isEmpty()
                || dashboard.widgets().size() > 12
                || dashboard.refreshSeconds() < 30
                || dashboard.refreshSeconds() > 3600
                || dashboard.parameters().size() > 20) throw ReportFault.invalid("仪表盘版本、容量或刷新间隔无效");
        var parameters = new LinkedHashMap<String, ReportDsl.Parameter>();
        for (var p : dashboard.parameters()) {
            if (p.name() == null
                    || !p.name().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                    || parameters.putIfAbsent(p.name(), p) != null)
                throw ReportFault.invalid("仪表盘参数无效");
            try {
                ValueType.valueOf(p.type());
            } catch (Exception invalid) {
                throw ReportFault.invalid("仪表盘参数类型无效");
            }
        }
        var bindings = new LinkedHashMap<String, ReportDefinitionService.Binding>();
        var widgets = new LinkedHashMap<String, DashboardDefinition.Widget>();
        for (var widget : dashboard.widgets()) {
            var layout = widget.layout();
            if (widget.id() == null
                    || !widget.id().matches("[a-z][a-z0-9_]{0,31}")
                    || widgets.putIfAbsent(widget.id(), widget) != null
                    || widget.componentVersion() != 1
                    || !installed().contains(widget.type())
                    || widget.title() == null
                    || widget.title().length() > 100
                    || layout == null
                    || layout.x() < 0
                    || layout.y() < 0
                    || layout.y() > 100
                    || layout.width() < 1
                    || layout.width() > 12
                    || layout.x() + layout.width() > 12
                    || layout.height() < 1
                    || layout.height() > 12
                    || layout.mobileOrder() < 0
                    || layout.mobileOrder() > 100
                    || widget.links().size() > 12) throw ReportFault.invalid("仪表盘组件或网格布局无效");
            var binding = binding(actor, widget);
            bindings.put(widget.id(), binding);
            var dsl = binding.compiled().dsl();
            if (widget.type().equals("PIVOT") != (dsl.kind() == ReportDsl.Kind.PIVOT)
                    || dsl.kind() == ReportDsl.Kind.DOCUMENT)
                throw ReportFault.invalid("组件类型与报告类型不匹配");
            var child = new HashMap<String, DatasetParameter>();
            for (var p : binding.compiled().descriptor().parameters()) child.put(p.name(), p);
            for (var entry : widget.parameterBindings().entrySet()) {
                var target = child.get(entry.getKey());
                var source = parameters.get(entry.getValue());
                if (target == null
                        || source == null
                        || !target.type().name().equals(source.type())
                        || dsl.parameters().stream().noneMatch(p -> p.name().equals(target.name())))
                    throw ReportFault.invalid("组件参数映射与固定数据集类型不一致");
            }
            for (var p : dsl.parameters())
                if (p.required()
                        && !widget.parameterBindings().containsKey(p.name())
                        && p.defaultValue() == null) throw ReportFault.invalid("组件必填参数没有绑定");
            if (Set.of("METRIC", "BAR", "LINE", "PIE").contains(widget.type())) {
                field(binding, widget.valueField());
                if (widget.function() == null
                        || !Set.of("SUM", "COUNT", "COUNT_ROWS", "MIN", "MAX", "AVG")
                                .contains(widget.function())) throw ReportFault.invalid("组件聚合函数无效");
                var field = binding.compiled().descriptor().field(widget.valueField());
                if (!field.aggregatable()
                        || !widget.function().startsWith("COUNT")
                                && !Set.of(ValueType.DECIMAL, ValueType.INTEGER)
                                        .contains(field.type()))
                    throw ReportFault.invalid("指标字段不可按此函数聚合");
            }
            if (Set.of("BAR", "LINE", "PIE").contains(widget.type()))
                field(binding, widget.categoryField());
            if (widget.comparison() != null && !widget.comparison().equals("NONE")) {
                if (!widget.type().equals("METRIC")
                        || !Set.of("PREVIOUS_PERIOD", "PREVIOUS_YEAR").contains(widget.comparison())
                        || !child.containsKey(widget.startParameter())
                        || !child.containsKey(widget.endParameter())
                        || child.get(widget.startParameter()).type() != ValueType.DATE
                        || child.get(widget.endParameter()).type() != ValueType.DATE)
                    throw ReportFault.invalid("同比环比必须绑定明确DATE起止参数");
            }
        }
        for (var widget : dashboard.widgets())
            for (var link : widget.links()) {
                var target = widgets.get(link.targetWidget());
                if (target == null || target.id().equals(widget.id()))
                    throw ReportFault.invalid("组件联动目标无效");
                field(bindings.get(widget.id()), link.sourceField());
                var parameter =
                        bindings.get(target.id()).compiled().descriptor().parameters().stream()
                                .filter(p -> p.name().equals(link.targetParameter()))
                                .findFirst()
                                .orElseThrow(() -> ReportFault.invalid("联动参数未登记"));
                if (parameter.type()
                                != bindings.get(widget.id())
                                        .compiled()
                                        .descriptor()
                                        .field(link.sourceField())
                                        .type()
                        || bindings.get(target.id()).compiled().dsl().parameters().stream()
                                .noneMatch(p -> p.name().equals(parameter.name())))
                    throw ReportFault.invalid("组件联动类型不一致");
            }
    }

    /**
     * 组件只能读取报告已声明可见的可信字段，不从隐藏列取值。
     *
     * @param binding 当前契约的binding配置或状态
     * @param name 契约名称
     */
    private static void field(ReportDefinitionService.Binding binding, String name) {
        if (name == null
                || binding.compiled().dsl().columns().stream()
                        .noneMatch(c -> c.field().equals(name))
                || binding.compiled().descriptor().fields().stream()
                        .noneMatch(f -> f.name().equals(name)))
            throw ReportFault.invalid("组件字段不属于固定报告可见契约");
    }
}
