package com.nexus.report.query;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.ReportEntityCatalog;
import com.nexus.report.domain.ReportFault;

import java.util.*;
import java.util.stream.Collectors;

/** 从代码目录和受限模型生成参数化查询，各实体在连接前应用自己的权限范围。 */
public final class QueryPlanner {
    /** 查询规划安全语义版本，参与固定发布依赖和执行范围指纹。 */
    public static final String VERSION = "1.0.0";

    /** 保存当前catalog状态或配置。 */
    private final ReportEntityCatalog catalog;

    /**
     * 固定本次使用的目录版本。
     *
     * @param catalog 当前契约的catalog配置或状态
     */
    public QueryPlanner(ReportEntityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    /**
     * 规划产物只供服务端执行，接口不返回物理SQL或绑定值。
     *
     * @param sql 当前契约的sql配置或状态
     * @param values 固定类型的精确值列表
     * @param descriptor 当前契约的descriptor配置或状态
     * @param requiredPermissions 当前契约的requiredPermissions配置或状态
     * @param tables 当前契约的tables配置或状态
     */
    public record Plan(
            String sql,
            List<Object> values,
            DatasetDescriptor descriptor,
            Set<String> requiredPermissions,
            Set<String> tables) {
        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param sql 当前受控操作的sql输入
         * @param values 当前受控操作的values输入
         * @param descriptor 当前受控操作的descriptor输入
         * @param requiredPermissions 当前受控操作的requiredPermissions输入
         * @param tables 当前受控操作的tables输入
         */
        public Plan {
            values = List.copyOf(values);
            requiredPermissions = Set.copyOf(requiredPermissions);
            tables = Set.copyOf(tables);
        }
    }

    /**
     * 当前规划独有的字段绑定，避免将多个实体的部门范围误相交。
     *
     * @param entity 当前契约的entity配置或状态
     * @param field 已批准的逻辑字段
     * @param alias 当前契约的alias配置或状态
     */
    private record FieldRef(
            ReportEntityCatalog.Entity entity, ReportEntityCatalog.Field field, String alias) {

        /**
         * 执行sql对应的受控处理。
         *
         * @return 当前固定契约或处理结果
         */
        String sql() {
            String value = alias + ".`" + field.column() + "`";
            return field.type() == ValueType.DATE ? "CAST(" + value + " AS DATE)" : value;
        }
    }

    /**
     * 校验全部逻辑标识、隐藏条件、聚合口径并生成只读查询。
     *
     * @param model 当前契约的model配置或状态
     * @param actor 执行用户
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    public Plan plan(QueryModel model, ReportActor actor, Map<String, Object> input) {
        try {
            return build(model, actor, input == null ? Map.of() : input, false);
        } catch (ReportFault fault) {
            throw fault;
        } catch (DatasetAccessException fault) {
            throw new ReportFault(fault.code(), "查询参数不符合契约", 400);
        } catch (IllegalArgumentException | NullPointerException fault) {
            throw ReportFault.invalid("查询模型或实体目录引用无效");
        }
    }

    /**
     * 校验固定模型与全部权限依赖，发布时不要求运行参数已经填写。
     *
     * @param model 当前契约的model配置或状态
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public Plan validate(QueryModel model, ReportActor actor) {
        try {
            return build(model, actor, Map.of(), true);
        } catch (ReportFault fault) {
            throw fault;
        } catch (IllegalArgumentException | NullPointerException fault) {
            throw ReportFault.invalid("查询模型引用无效");
        }
    }

    /**
     * 为固定模型生成规范化契约，同步执行授权检查。
     *
     * @param model 当前契约的model配置或状态
     * @param actor 执行用户
     * @param input 已规范化的参数值
     * @param allowMissing 当前契约的allowMissing配置或状态
     * @return 当前受控处理的固定结果
     */
    private Plan build(
            QueryModel model, ReportActor actor, Map<String, Object> input, boolean allowMissing) {
        if (model == null
                || model.formatVersion() != 1
                || model.catalogVersion() != catalog.version()
                || model.fields().isEmpty()
                || model.fields().size() > 40
                || model.joins().size() > 2
                || model.filters().size() > 20
                || model.groupBy().size() > 3
                || model.parameters().size() > 20
                || model.sort().size() > 5) throw ReportFault.invalid("查询结构或容量超限");
        var entities = new LinkedHashMap<String, ReportEntityCatalog.Entity>();
        var aliases = new LinkedHashMap<String, String>();
        var relations = new ArrayList<ReportEntityCatalog.Relation>();
        var root = catalog.entity(model.entity());
        entities.put(root.id(), root);
        aliases.put(root.id(), "q0");
        for (var id : model.joins()) {
            var relation = catalog.relation(id);
            if (!entities.containsKey(relation.from()) || entities.containsKey(relation.to()))
                throw ReportFault.invalid("连接必须沿已加入实体的批准关系，不能重复或形成环");
            var target = catalog.entity(relation.to());
            aliases.put(target.id(), "q" + entities.size());
            entities.put(target.id(), target);
            relations.add(relation);
        }
        var permissions = new LinkedHashSet<String>();
        var scopes = new LinkedHashMap<String, List<ReportScope>>();
        for (var entity : entities.values()) {
            require(actor, entity.permission());
            permissions.add(entity.permission());
            scopes.put(entity.id(), new ArrayList<>(List.of(actor.scopeFor(entity.permission()))));
        }
        var refs = new LinkedHashMap<String, FieldRef>();
        var fieldAliases = new LinkedHashMap<String, String>();
        var dimensionAliases = new LinkedHashMap<String, String>();
        var outputNames = new HashSet<String>();
        var selections = new ArrayList<String>();
        var output = new ArrayList<DatasetField>();
        java.util.function.Function<String, FieldRef> resolve =
                name -> {
                    if (name == null
                            || !name.matches("[a-z][a-z0-9_]{0,63}\\.[A-Za-z][A-Za-z0-9_]{0,63}"))
                        throw ReportFault.invalid("逻辑字段标识无效");
                    var parts = name.split("\\.", 2);
                    var entity = entities.get(parts[0]);
                    if (entity == null) throw ReportFault.invalid("字段实体未加入查询");
                    var field = entity.field(parts[1]);
                    if (field.permission() != null && !field.permission().isBlank()) {
                        require(actor, field.permission());
                        permissions.add(field.permission());
                        scopes.get(entity.id()).add(actor.scopeFor(field.permission()));
                    }
                    var ref = new FieldRef(entity, field, aliases.get(entity.id()));
                    refs.put(name, ref);
                    return ref;
                };
        var groups = model.groupBy().stream().map(resolve).toList();
        if (new HashSet<>(model.groupBy()).size() != model.groupBy().size())
            throw ReportFault.invalid("分组维度重复");
        boolean aggregated = model.fields().stream().anyMatch(f -> f.aggregate() != null);
        if (!aggregated && !groups.isEmpty()) throw ReportFault.invalid("分组查询必须声明聚合指标");
        for (var selected : model.fields()) {
            name(selected.alias());
            if (!outputNames.add(selected.alias())) throw ReportFault.invalid("输出别名重复");
            var ref = resolve.apply(selected.field());
            fieldAliases.putIfAbsent(selected.field(), selected.alias());
            String function = selected.aggregate();
            if (function == null) dimensionAliases.putIfAbsent(selected.field(), selected.alias());
            String expression = ref.sql();
            ValueType type = ref.field().type();
            if (function != null) {
                if (!Set.of("SUM", "COUNT", "COUNT_ROWS", "MIN", "MAX", "AVG").contains(function))
                    throw ReportFault.invalid("聚合函数未批准");
                if (!ref.field().aggregatable()) throw ReportFault.invalid("字段不可聚合");
                if (Set.of("SUM", "AVG", "MIN", "MAX").contains(function)) {
                    if (!Set.of(ValueType.DECIMAL, ValueType.INTEGER).contains(type)
                            || !model.groupBy().containsAll(ref.field().aggregationKeys()))
                        throw ReportFault.invalid("数值汇总必须保留物料、单位或币种口径");
                    type = ValueType.DECIMAL;
                } else if (function.startsWith("COUNT")) type = ValueType.INTEGER;
                for (var relation : relations)
                    if (relation.cardinality().equals("ONE_TO_MANY")
                            && !ref.entity().id().equals(relation.to()))
                        throw ReportFault.invalid("一对多连接不能汇总主表指标，请使用明细粒度或独立聚合");
                expression =
                        function.equals("COUNT_ROWS")
                                ? "COUNT(*)"
                                : function + "(" + expression + ")";
            } else if (aggregated && !model.groupBy().contains(selected.field()))
                throw ReportFault.invalid("非聚合字段必须显式分组");
            selections.add(expression + " AS `" + selected.alias() + "`");
            output.add(
                    new DatasetField(
                            selected.alias(),
                            ref.field().label(),
                            type,
                            ref.field().permission(),
                            List.of(),
                            true));
        }
        if (!dimensionAliases.keySet().containsAll(model.groupBy()))
            throw ReportFault.invalid("所有分组维度必须作为输出字段保留");
        for (int i = 0; i < output.size(); i++) {
            var original = model.fields().get(i);
            var ref = refs.get(original.field());
            var keys =
                    ref.field().aggregationKeys().stream()
                            .map(dimensionAliases::get)
                            .filter(Objects::nonNull)
                            .toList();
            var value = output.get(i);
            output.set(
                    i,
                    new DatasetField(
                            value.name(),
                            value.label(),
                            value.type(),
                            value.permission(),
                            keys,
                            ref.field().aggregatable()
                                    && keys.size() == ref.field().aggregationKeys().size()));
        }
        var parameters = new ArrayList<DatasetParameter>();
        var parameterCatalog = new HashMap<String, QueryModel.Parameter>();
        for (var parameter : model.parameters()) {
            name(parameter.name());
            if (parameter.type() == null
                    || parameterCatalog.putIfAbsent(parameter.name(), parameter) != null)
                throw ReportFault.invalid("参数重复或类型未声明");
            parameters.add(
                    new DatasetParameter(
                            parameter.name(),
                            parameter.name(),
                            parameter.type(),
                            parameter.required(),
                            null));
        }
        if (!parameterCatalog.keySet().containsAll(input.keySet()))
            throw ReportFault.invalid("存在未声明参数");
        var descriptor =
                new DatasetDescriptor(
                        "CATALOG_QUERY",
                        "受控查询",
                        1,
                        root.permission(),
                        false,
                        output,
                        parameters,
                        output.stream().map(DatasetField::name).toList());
        var parameterDescriptor =
                allowMissing
                        ? new DatasetDescriptor(
                                descriptor.code(),
                                descriptor.name(),
                                descriptor.version(),
                                descriptor.permission(),
                                false,
                                descriptor.fields(),
                                parameters.stream()
                                        .map(
                                                p ->
                                                        new DatasetParameter(
                                                                p.name(), p.label(), p.type(),
                                                                false, null))
                                        .toList(),
                                descriptor.defaultColumns())
                        : descriptor;
        var normalized = DatasetQueryValidator.parameters(parameterDescriptor, input);
        var filterSql = new ArrayList<String>();
        var filterValues = new ArrayList<Object>();
        for (var filter : model.filters()) {
            var ref = resolve.apply(filter.field());
            if (!Set.of("EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "IS_NULL", "NOT_NULL")
                    .contains(filter.operator())) throw ReportFault.invalid("筛选操作未批准");
            if (Set.of("IS_NULL", "NOT_NULL").contains(filter.operator())) {
                if (filter.parameter() != null) throw ReportFault.invalid("空值操作不能绑定参数");
                filterSql.add(
                        ref.sql()
                                + (filter.operator().equals("IS_NULL")
                                        ? " IS NULL"
                                        : " IS NOT NULL"));
                continue;
            }
            var parameter = parameterCatalog.get(filter.parameter());
            if (parameter == null
                    || parameter.type() != ref.field().type()
                            && !(parameter.type() == ValueType.DECIMAL
                                    && ref.field().type() == ValueType.INTEGER))
                throw ReportFault.invalid("筛选参数类型与字段不匹配");
            if (!normalized.containsKey(parameter.name())) continue;
            Object value = normalized.get(parameter.name());
            String operator =
                    switch (filter.operator()) {
                        case "EQ" -> "=";
                        case "NE" -> "<>";
                        case "GT" -> ">";
                        case "GE" -> ">=";
                        case "LT" -> "<";
                        case "LE" -> "<=";
                        default -> "LIKE";
                    };
            if (filter.operator().equals("LIKE")) {
                if (ref.field().type() != ValueType.STRING)
                    throw ReportFault.invalid("文本匹配只支持字符串字段");
                value =
                        "%"
                                + value.toString()
                                        .replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                + "%";
            }
            filterSql.add(
                    ref.sql()
                            + " "
                            + operator
                            + " ?"
                            + (filter.operator().equals("LIKE") ? " ESCAPE '!'" : ""));
            filterValues.add(value);
        }
        var orders = new ArrayList<String>();
        for (var order : model.sort()) {
            var ref = resolve.apply(order.field());
            if (aggregated && !fieldAliases.containsKey(order.field()))
                throw ReportFault.invalid("聚合排序必须使用输出字段");
            orders.add(
                    (aggregated ? "`" + fieldAliases.get(order.field()) + "`" : ref.sql())
                            + (order.descending() ? " DESC" : " ASC"));
        }
        if (orders.isEmpty())
            orders.addAll(
                    aggregated
                            ? groups.stream().map(FieldRef::sql).toList()
                            : entities.values().stream()
                                    .map(e -> aliases.get(e.id()) + ".`" + e.key() + "`")
                                    .toList());
        if (orders.isEmpty()) orders.add("1");
        var values = new ArrayList<Object>();
        var tables = new LinkedHashSet<String>();
        var from =
                new StringBuilder(
                        source(
                                root,
                                aliases.get(root.id()),
                                actor,
                                scopes.get(root.id()),
                                permissions,
                                values,
                                tables,
                                new HashSet<>()));
        for (var relation : relations) {
            var entity = entities.get(relation.to());
            from.append(" ")
                    .append(relation.joinType())
                    .append(" JOIN ")
                    .append(
                            source(
                                    entity,
                                    aliases.get(entity.id()),
                                    actor,
                                    scopes.get(entity.id()),
                                    permissions,
                                    values,
                                    tables,
                                    new HashSet<>()))
                    .append(" ON ")
                    .append(aliases.get(relation.from()))
                    .append(".`")
                    .append(relation.fromColumn())
                    .append("` = ")
                    .append(aliases.get(relation.to()))
                    .append(".`")
                    .append(relation.toColumn())
                    .append("`");
        }
        values.addAll(filterValues);
        String sql =
                "SELECT "
                        + String.join(",", selections)
                        + " FROM "
                        + from
                        + (filterSql.isEmpty() ? "" : " WHERE " + String.join(" AND ", filterSql))
                        + (groups.isEmpty()
                                ? ""
                                : " GROUP BY "
                                        + groups.stream()
                                                .map(FieldRef::sql)
                                                .collect(Collectors.joining(",")))
                        + " ORDER BY "
                        + String.join(",", orders);
        return new Plan(sql, values, descriptor, permissions, tables);
    }

    /**
     * 在连接前预过滤实体，并以所属父单据的策略保护明细。
     *
     * @param entity 当前契约的entity配置或状态
     * @param alias 当前契约的alias配置或状态
     * @param actor 执行用户
     * @param scopes 当前契约的scopes配置或状态
     * @param permissions 当前契约的permissions配置或状态
     * @param values 固定类型的精确值列表
     * @param tables 当前契约的tables配置或状态
     * @param visited 当前契约的visited配置或状态
     * @return 当前受控处理的固定结果
     */
    private String source(
            ReportEntityCatalog.Entity entity,
            String alias,
            ReportActor actor,
            List<ReportScope> scopes,
            Set<String> permissions,
            List<Object> values,
            Set<String> tables,
            Set<String> visited) {
        if (!visited.add(entity.id()) || visited.size() > 4) throw ReportFault.invalid("父单据策略循环");
        tables.add(entity.table());
        var predicates = new ArrayList<String>();
        predicates.add("p.`" + entity.tenantColumn() + "` = ?");
        values.add(actor.tenantId());
        if (entity.deleteColumn() != null)
            predicates.add("p.`" + entity.deleteColumn() + "` = '0'");
        for (var scope : scopes) {
            if (scope.userId() != actor.userId()) throw ReportFault.denied();
            if (entity.parent() != null
                    && entity.departmentColumn() == null
                    && entity.ownerColumn() == null) continue;
            if (scope.all()) continue;
            var alternatives = new ArrayList<String>();
            if (entity.departmentColumn() != null && !scope.departments().isEmpty()) {
                alternatives.add(
                        "p.`"
                                + entity.departmentColumn()
                                + "` IN ("
                                + scope.departments().stream()
                                        .map(i -> "?")
                                        .collect(Collectors.joining(","))
                                + ")");
                values.addAll(scope.departments());
            }
            if (entity.ownerColumn() != null && scope.self()) {
                alternatives.add("p.`" + entity.ownerColumn() + "` = ?");
                values.add(scope.userId());
            }
            predicates.add(
                    "("
                            + (alternatives.isEmpty() ? "1=0" : String.join(" OR ", alternatives))
                            + ")");
        }
        if (entity.parent() != null) {
            var parent = catalog.entity(entity.parent().entity());
            require(actor, parent.permission());
            permissions.add(parent.permission());
            var inherited = new ArrayList<ReportScope>();
            inherited.add(actor.scopeFor(parent.permission()));
            if (entity.departmentColumn() == null && entity.ownerColumn() == null)
                inherited.addAll(scopes);
            var parentSource =
                    source(
                            parent,
                            "parent_guard",
                            actor,
                            inherited,
                            permissions,
                            values,
                            tables,
                            visited);
            predicates.add(
                    "EXISTS (SELECT 1 FROM "
                            + parentSource
                            + " WHERE parent_guard.`"
                            + entity.parent().parentColumn()
                            + "` = p.`"
                            + entity.parent().childColumn()
                            + "`)");
        }
        return "(SELECT p.* FROM `"
                + entity.table()
                + "` p WHERE "
                + String.join(" AND ", predicates)
                + ") "
                + alias;
    }

    /**
     * 校验客户端逻辑输出名，不允许保留对象键或查询片段。
     *
     * @param value 当前契约的value配置或状态
     */
    private static void name(String value) {
        if (value == null
                || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
                || (Set.of(
                                        "__proto__",
                                        "constructor",
                                        "prototype",
                                        "row_count",
                                        "page_no",
                                        "page_count")
                                .contains(value)
                        || value.startsWith("p_"))) throw ReportFault.invalid("字段别名或参数名无效");
    }

    /**
     * 每个实体和字段分别检查业务授权。
     *
     * @param actor 执行用户
     * @param permission 当前契约的permission配置或状态
     */
    private static void require(ReportActor actor, String permission) {
        if (!actor.has(permission)) throw ReportFault.denied();
    }
}
