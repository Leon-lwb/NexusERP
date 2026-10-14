package com.nexus.report.query;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.Dataset;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.dao.DuplicateKeyException;

import java.util.*;

/** 受控查询的目录、草稿、固定发布及数据集登记，全部使用当前用户权限。 */
public final class ReportQueryService {
    /**
     * 只向客户端提供逻辑字段，不暴露SQL列名或连接参数。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param type 受控类型
     * @param aggregationKeys 当前契约的aggregationKeys配置或状态
     * @param aggregatable 当前契约的aggregatable配置或状态
     */
    public record FieldView(
            String id,
            String label,
            ValueType type,
            List<String> aggregationKeys,
            boolean aggregatable) {}

    /**
     * 当前身份可见的逻辑实体。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param fields 当前契约的fields配置或状态
     */
    public record EntityView(String id, String label, List<FieldView> fields) {}

    /**
     * 目录连接仅暴露逻辑关系与基数。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param from 当前契约的from配置或状态
     * @param to 当前契约的to配置或状态
     * @param cardinality 当前契约的cardinality配置或状态
     * @param joinType 当前契约的joinType配置或状态
     */
    public record RelationView(
            String id, String from, String to, String cardinality, String joinType) {}

    /**
     * 固定目录版本的客户端能力集。
     *
     * @param version 当前契约的version配置或状态
     * @param entities 当前契约的entities配置或状态
     * @param relations 当前契约的relations配置或状态
     */
    public record CatalogView(
            int version, List<EntityView> entities, List<RelationView> relations) {}

    /**
     * 发布生成数据集版本，模板显式绑定该版本。
     *
     * @param version 当前契约的version配置或状态
     * @param dataset 当前契约的dataset配置或状态
     */
    public record Publication(ReportResourceStore.Version version, Dataset dataset) {}

    /**
     * 精确预览传输，标识符和数值不交给浏览器浮点转换。
     *
     * @param rows 精确类型的数据行
     * @param total 当前契约的total配置或状态
     * @param dataTime 当前契约的dataTime配置或状态
     * @param fields 当前契约的fields配置或状态
     */
    public record TypedPreview(
            List<Map<String, TypedValue>> rows,
            long total,
            java.time.Instant dataTime,
            List<DatasetField> fields) {}

    /**
     * 以当前仍有权限的固定类型转换预览行，不修改业务值。
     *
     * @param actor 执行用户
     * @param model 当前契约的model配置或状态
     * @param data 同一固定快照投影出的数据
     * @return 当前受控处理的固定结果
     */
    public TypedPreview typedPreview(ReportActor actor, QueryModel model, DatasetResult data) {
        var user = current(actor, "report:query:edit");
        var descriptor = validate(user, model).descriptor();
        var rows = new ArrayList<Map<String, TypedValue>>();
        for (var original : data.rows()) {
            var row = new LinkedHashMap<String, TypedValue>();
            for (var field : descriptor.fields())
                row.put(field.name(), TypedValue.of(field.type(), original.get(field.name())));
            rows.add(Collections.unmodifiableMap(row));
        }
        return new TypedPreview(
                List.copyOf(rows), data.total(), data.dataTime(), descriptor.fields());
    }

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前catalogs状态或配置。 */
    private final ReportCatalogRegistry catalogs;

    /** 保存当前executor状态或配置。 */
    private final CatalogQueryExecutor executor;

    /** 保存当前bindings状态或配置。 */
    private final QueryDatasetBindingStore bindings;

    /** 保存当前store状态或配置。 */
    private final JdbcReportStore store;

    /** 保存当前definitions状态或配置。 */
    private final ReportDefinitionService definitions;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /**
     * 复用主库事务、当前授权及独立查询执行账户。
     *
     * @param resources 当前契约的resources配置或状态
     * @param catalogs 当前契约的catalogs配置或状态
     * @param executor 当前契约的executor配置或状态
     * @param bindings 当前契约的bindings配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportQueryService(
            ReportResourceStore resources,
            ReportCatalogRegistry catalogs,
            CatalogQueryExecutor executor,
            QueryDatasetBindingStore bindings,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler json) {
        this.resources = resources;
        this.catalogs = catalogs;
        this.executor = executor;
        this.bindings = bindings;
        this.store = store;
        this.definitions = definitions;
        this.json = json;
    }

    /**
     * 每个入口重新解析账户，不信任调用方缓存的身份。
     *
     * @param actor 执行用户
     * @param permission 当前契约的permission配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor, String permission) {
        var result = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(result, permission);
        return result;
    }

    /**
     * 读取授权逻辑目录，字段权限应用于输出、筛选、排序和分组。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<CatalogView> catalog(ReportActor actor) {
        var user = current(actor, "report:query:list");
        var result = new ArrayList<CatalogView>();
        for (int number : catalogs.versions()) {
            var c = catalogs.require(number);
            var entities =
                    c.entities().stream()
                            .filter(e -> user.has(e.permission()))
                            .map(
                                    e ->
                                            new EntityView(
                                                    e.id(),
                                                    e.label(),
                                                    e.fields().stream()
                                                            .filter(
                                                                    f ->
                                                                            f.permission() == null
                                                                                    || user.has(
                                                                                            f
                                                                                                    .permission()))
                                                            .map(
                                                                    f ->
                                                                            new FieldView(
                                                                                    e.id()
                                                                                            + "."
                                                                                            + f
                                                                                                    .id(),
                                                                                    f.label(),
                                                                                    f.type(),
                                                                                    f
                                                                                            .aggregationKeys(),
                                                                                    f
                                                                                            .aggregatable()))
                                                            .toList()))
                            .toList();
            var visible =
                    entities.stream()
                            .map(EntityView::id)
                            .collect(java.util.stream.Collectors.toSet());
            result.add(
                    new CatalogView(
                            number,
                            entities,
                            c.relations().stream()
                                    .filter(
                                            r ->
                                                    visible.contains(r.from())
                                                            && visible.contains(r.to()))
                                    .map(
                                            r ->
                                                    new RelationView(
                                                            r.id(),
                                                            r.from(),
                                                            r.to(),
                                                            r.cardinality(),
                                                            r.joinType()))
                                    .toList()));
        }
        return List.copyOf(result);
    }

    /**
     * 列表只返回当前仍能完整使用的资源，不泄露失去字段权限的模型。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Draft> list(ReportActor actor) {
        var user = current(actor, "report:query:list");
        return resources.list("QUERY", user).stream()
                .filter(
                        d -> {
                            try {
                                validate(user, parse(d.json()));
                                return true;
                            } catch (ReportFault denied) {
                                if (denied.code().equals("SCOPE_DENIED")) return false;
                                throw denied;
                            }
                        })
                .toList();
    }

    /**
     * 读取当前可编辑的查询并检查所有参与实体。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft detail(ReportActor actor, String id) {
        var user = current(actor, "report:query:list");
        var draft = resources.access("QUERY", user, id, "VIEW");
        validate(user, parse(draft.json()));
        return draft;
    }

    /**
     * 创建受限模型草稿，不接受SQL片段或数据库配置。
     *
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft create(
            ReportActor actor, String code, String title, QueryModel model) {
        var user = current(actor, "report:query:add");
        validate(user, model);
        try {
            return resources.create("QUERY", user, code, title, json.json(model));
        } catch (DuplicateKeyException duplicate) {
            throw new ReportFault("CODE_EXISTS", "查询编码已存在", 409);
        }
    }

    /**
     * 通过修订保护保存，当前字段权限必须仍有效。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param title 当前契约的title配置或状态
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft save(
            ReportActor actor, String id, long revision, String title, QueryModel model) {
        var user = current(actor, "report:query:edit");
        validate(user, model);
        return resources.save("QUERY", user, id, revision, json.json(model), title);
    }

    /**
     * 发布查询和关联数据集在同一主库事务中提交，固定目录校验和。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @return 当前受控处理的固定结果
     */
    public Publication publish(ReportActor actor, String id, long revision) {
        var user = current(actor, "report:query:publish");
        return resources.atomic(
                () -> {
                    var draft = resources.access("QUERY", user, id, "PUBLISH");
                    var model = parse(draft.json());
                    validate(user, model);
                    var version =
                            resources.publish(
                                    "QUERY",
                                    user,
                                    id,
                                    revision,
                                    (locked, number) -> {
                                        var refreshed = current(user, "report:query:publish");
                                        resources.access("QUERY", refreshed, id, "PUBLISH");
                                        var fixed = parse(locked.json());
                                        var plan = validate(refreshed, fixed);
                                        var d = plan.descriptor();
                                        var descriptor =
                                                new DatasetDescriptor(
                                                        "CQ_" + id,
                                                        locked.title(),
                                                        number,
                                                        d.permission(),
                                                        false,
                                                        d.fields(),
                                                        d.parameters(),
                                                        d.defaultColumns());
                                        return json.json(
                                                new QueryDatasetResolver.Contract(
                                                        fixed.catalogVersion(),
                                                        catalogs.checksum(fixed.catalogVersion()),
                                                        descriptor));
                                    },
                                    () -> {
                                        var refreshed = current(user, "report:query:publish");
                                        resources.access("QUERY", refreshed, id, "PUBLISH");
                                        validate(refreshed, model);
                                    });
                    var contract =
                            json.read(version.dependency(), QueryDatasetResolver.Contract.class);
                    var refreshed = current(user, "report:query:publish");
                    var dataset =
                            store.registerDataset(
                                    refreshed,
                                    contract.descriptor(),
                                    json.json(contract.descriptor()),
                                    ReportDefinitionService.hash(json.json(contract.descriptor())));
                    bindings.register(
                            user.tenantId(),
                            dataset.publishedVersion(),
                            version.id(),
                            contract.catalogVersion(),
                            version.checksum(),
                            contract.catalogChecksum());
                    return new Publication(version, dataset);
                });
    }

    /**
     * 预览未保存的受控模型，样例最多两百行并在返回前复核授权。
     *
     * @param actor 执行用户
     * @param model 当前契约的model配置或状态
     * @param parameters 固定参数契约
     * @return 当前受控处理的固定结果
     */
    public DatasetResult preview(
            ReportActor actor, QueryModel model, Map<String, Object> parameters) {
        var user = current(actor, "report:query:edit");
        ReportDefinitionService.require(user, "report:runtime:preview");
        var planner = new QueryPlanner(catalogs.require(model.catalogVersion()));
        var plan = planner.plan(model, user, parameters);
        var fields =
                plan.descriptor().fields().stream()
                        .map(DatasetField::name)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var context =
                new ReportExecutionContext(
                        user, List.of(user.scopeFor(plan.descriptor().permission())));
        var result =
                executor.query(
                        plan,
                        context,
                        new DatasetQuery(parameters, fields, 1, 200, true, null, false));
        var refreshed = current(user, "report:query:edit");
        if (!refreshed.equals(user)) throw new ReportFault("SCOPE_CHANGED", "查询期间权限已变化", 409);
        return result;
    }

    /**
     * 历史查询版本不跟随最新发布，并且全部依赖权限仍须有效。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Version> versions(ReportActor actor, String id) {
        var user = current(actor, "report:query:list");
        resources.access("QUERY", user, id, "VIEW");
        return resources.versions("QUERY", user.tenantId(), id).stream()
                .peek(v -> validate(user, parse(v.json())))
                .toList();
    }

    /**
     * 显式停用或归档查询会使使用该查询的后续运行和下载被拒绝。
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
                "QUERY", current(actor, "report:query:publish"), id, revision, state);
    }

    /**
     * 严格解析模型，未知属性、脚本字段和超深JSON均被拒绝。
     *
     * @param text 当前契约的text配置或状态
     * @return 当前受控处理的固定结果
     */
    private QueryModel parse(String text) {
        return json.read(text, QueryModel.class);
    }

    /**
     * 发布前检查全部实体和隐藏字段权限，运行参数可在使用时填写。
     *
     * @param actor 执行用户
     * @param model 当前契约的model配置或状态
     * @return 当前受控处理的固定结果
     */
    private QueryPlanner.Plan validate(ReportActor actor, QueryModel model) {
        return new QueryPlanner(catalogs.require(model.catalogVersion())).validate(model, actor);
    }
}
