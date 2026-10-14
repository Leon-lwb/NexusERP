package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.core.domain.R;
import com.nexus.common.report.TypedValue;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/** 原生定义、契约、发布和权限管理入口，使用平台正常认证链。 */
@RestController
@RequestMapping("/report")
public class ReportDefinitionController {
    /** 保存当前 {@code actors} 值。 */
    private final ReportRequestActor actors;

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /** 保存当前 {@code properties} 值。 */
    private final ReportProperties properties;

    /**
     * 初始化报表定义接口并保存所需依赖或不可变值。
     *
     * @param actors 当前操作的 {@code actors} 输入
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     * @param properties 当前操作的 {@code properties} 输入
     */
    public ReportDefinitionController(
            ReportRequestActor actors,
            ReportDefinitionService definitions,
            ReportCompiler compiler,
            ReportProperties properties) {
        this.actors = actors;
        this.definitions = definitions;
        this.compiler = compiler;
        this.properties = properties;
    }

    /**
     * Draft请求，负责当前模块对应的契约或处理职责。
     *
     * @param dsl 不可变的报表定义
     * @param revision 当前契约的revision配置或状态
     * @param catalogId 当前契约的catalogId配置或状态
     */
    public record DraftRequest(JsonNode dsl, Long revision, String catalogId) {}

    /**
     * Preview请求，负责当前模块对应的契约或处理职责。
     *
     * @param reportId 当前契约的reportId配置或状态
     * @param dsl 不可变的报表定义
     * @param parameters 固定参数契约
     */
    public record PreviewRequest(String reportId, JsonNode dsl, Map<String, Object> parameters) {}

    /**
     * Revision请求，负责当前模块对应的契约或处理职责。
     *
     * @param revision 当前契约的revision配置或状态
     */
    public record RevisionRequest(Long revision) {}

    /**
     * Rollback请求，负责当前模块对应的契约或处理职责。
     *
     * @param versionId 不可变发布版本标识
     * @param revision 当前契约的revision配置或状态
     */
    public record RollbackRequest(String versionId, Long revision) {}

    /**
     * State请求，负责当前模块对应的契约或处理职责。
     *
     * @param state 任务或资源状态
     * @param revision 当前契约的revision配置或状态
     * @param expectedStatus 当前契约的expectedStatus配置或状态
     */
    public record StateRequest(String state, Long revision, String expectedStatus) {}

    /**
     * Catalog请求，负责当前模块对应的契约或处理职责。
     *
     * @param parentId 当前契约的parentId配置或状态
     * @param name 契约名称
     */
    public record CatalogRequest(String parentId, String name) {}

    /**
     * Register请求，负责当前模块对应的契约或处理职责。
     *
     * @param code 当前契约的code配置或状态
     * @param version 当前契约的version配置或状态
     */
    public record RegisterRequest(String code, int version) {}

    /**
     * Detail，负责当前模块对应的契约或处理职责。
     *
     * @param definition 当前契约的definition配置或状态
     * @param dsl 不可变的报表定义
     */
    public record Detail(Definition definition, ReportDsl dsl) {}

    /**
     * Published，负责当前模块对应的契约或处理职责。
     *
     * @param versionId 不可变发布版本标识
     * @param number 当前契约的number配置或状态
     * @param dsl 不可变的报表定义
     */
    public record Published(String versionId, int number, ReportDsl dsl) {}

    /**
     * SampleView，负责当前模块对应的契约或处理职责。
     *
     * @param rows 精确类型的数据行
     * @param total 当前契约的total配置或状态
     * @param dataTime 当前契约的dataTime配置或状态
     */
    public record SampleView(List<Map<String, TypedValue>> rows, long total, Instant dataTime) {}

    /**
     * 完成 {@code dsl} 对应的报表定义接口处理。
     *
     * @param node 当前操作的 {@code node} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl dsl(JsonNode node) {
        if (node == null) throw ReportFault.invalid("报表定义不能为空");
        return definitions.parseDraft(node.toString());
    }

    /**
     * 完成 {@code revision} 对应的报表定义接口处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    private static long revision(Long value) {
        if (value == null || value < 1) throw ReportFault.invalid("请提供当前草稿版本号");
        return value;
    }

    /**
     * 完成 {@code list} 对应的报表定义接口处理。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:list")
    @GetMapping("/definitions")
    public R<List<Definition>> list() {
        return R.ok(definitions.list(actors.current()));
    }

    /**
     * 读取可编辑定义详情。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:edit")
    @GetMapping("/definitions/{id}")
    public R<Detail> detail(@PathVariable String id) {
        var actor = actors.current();
        var definition = definitions.detail(actor, id);
        var dsl = definitions.parseDraft(definition.json());
        definitions.bind(actor, dsl);
        return R.ok(new Detail(definition, dsl));
    }

    /**
     * 读取当前仍可使用的发布版本。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/definitions/{id}/published")
    public R<Published> published(@PathVariable String id) {
        var actor = actors.current();
        var version = definitions.published(actor, id, "VIEW", "report:runtime:preview");
        var dsl = definitions.versionDsl(version);
        definitions.bindVersion(actor, version);
        return R.ok(new Published(version.id(), version.number(), dsl));
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:add")
    @PostMapping("/definitions")
    public R<Definition> create(@RequestBody DraftRequest body) {
        return R.ok(definitions.create(actors.current(), dsl(body.dsl()), body.catalogId()));
    }

    /**
     * 完成 {@code save} 对应的报表定义接口处理。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:edit")
    @PutMapping("/definitions/{id}")
    public R<Definition> save(@PathVariable String id, @RequestBody DraftRequest body) {
        return R.ok(
                definitions.save(actors.current(), id, revision(body.revision()), dsl(body.dsl())));
    }

    /**
     * 检查定义或查询是否满足受限契约。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @PostMapping("/definitions/validate")
    public R<Map<String, Object>> validate(@RequestBody PreviewRequest body) {
        return R.ok(definitions.validate(actors.current(), body.reportId(), dsl(body.dsl())));
    }

    /**
     * 完成 {@code preview} 对应的报表定义接口处理。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @PostMapping("/definitions/preview")
    public R<ReportDefinitionService.DraftPreview> preview(@RequestBody PreviewRequest body) {
        return R.ok(
                definitions.preview(
                        actors.current(),
                        body.reportId(),
                        dsl(body.dsl()),
                        body.parameters(),
                        properties.getFontPath()));
    }

    /**
     * 在修订与授权检查后创建不可变发布版本。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:publish")
    @PostMapping("/definitions/{id}/publish")
    public R<Version> publish(@PathVariable String id, @RequestBody RevisionRequest body) {
        return R.ok(definitions.publish(actors.current(), id, revision(body.revision())));
    }

    /**
     * 完成 {@code versions} 对应的报表定义接口处理。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:list")
    @GetMapping("/definitions/{id}/versions")
    public R<List<Version>> versions(@PathVariable String id) {
        return R.ok(definitions.versions(actors.current(), id));
    }

    /**
     * 完成 {@code rollback} 对应的报表定义接口处理。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:publish")
    @PostMapping("/definitions/{id}/rollback")
    public R<Void> rollback(@PathVariable String id, @RequestBody RollbackRequest body) {
        definitions.rollback(actors.current(), id, body.versionId(), revision(body.revision()));
        return R.ok();
    }

    /**
     * 按修订和权限执行生命周期迁移。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @PutMapping("/definitions/{id}/state")
    public R<Void> state(@PathVariable String id, @RequestBody StateRequest body) {
        definitions.state(actors.current(), id, body.state(), revision(body.revision()));
        return R.ok();
    }

    /**
     * 完成 {@code catalogs} 对应的报表定义接口处理。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:list")
    @GetMapping("/catalogs")
    public R<List<Catalog>> catalogs() {
        return R.ok(definitions.catalogs(actors.current()));
    }

    /**
     * 完成 {@code catalog} 对应的报表定义接口处理。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:catalog:manage")
    @PostMapping("/catalogs")
    public R<Catalog> catalog(@RequestBody CatalogRequest body) {
        return R.ok(definitions.catalog(actors.current(), body.parentId(), body.name()));
    }

    /**
     * 列出当前身份可见的数据集。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:list")
    @GetMapping("/datasets")
    public R<List<ReportDefinitionService.DatasetView>> datasets() {
        return R.ok(definitions.datasets(actors.current()));
    }

    /**
     * 列出已安装且具备业务权限的提供者。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:manage")
    @GetMapping("/datasets/providers")
    public R<List<com.nexus.common.report.DatasetDescriptor>> providers() {
        return R.ok(definitions.providers(actors.current()));
    }

    /**
     * 完成 {@code register} 对应的报表定义接口处理。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:manage")
    @PostMapping("/datasets")
    public R<Dataset> register(@RequestBody RegisterRequest body) {
        return R.ok(definitions.registerDataset(actors.current(), body.code(), body.version()));
    }

    /**
     * 完成 {@code datasetState} 对应的报表定义接口处理。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:manage")
    @PutMapping("/datasets/{id}/state")
    public R<Void> datasetState(@PathVariable String id, @RequestBody StateRequest body) {
        definitions.datasetState(actors.current(), id, body.state(), body.expectedStatus());
        return R.ok();
    }

    /**
     * 完成 {@code sample} 对应的报表定义接口处理。
     *
     * @param id 资源标识
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:list")
    @PostMapping("/datasets/{id}/sample")
    public R<SampleView> sample(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> input) {
        var actor = actors.current();
        var descriptor =
                definitions.datasets(actor).stream()
                        .filter(view -> view.dataset().id().equals(id))
                        .map(ReportDefinitionService.DatasetView::descriptor)
                        .findFirst()
                        .orElseThrow(ReportFault::denied);
        var result = definitions.sample(actor, id, input);
        var rows =
                result.rows().stream()
                        .map(
                                raw -> {
                                    Map<String, TypedValue> row = new LinkedHashMap<>();
                                    for (var field : descriptor.fields())
                                        if (raw.containsKey(field.name()))
                                            row.put(
                                                    field.name(),
                                                    TypedValue.of(
                                                            field.type(), raw.get(field.name())));
                                    return Collections.unmodifiableMap(row);
                                })
                        .toList();
        return R.ok(new SampleView(rows, result.total(), result.dataTime()));
    }

    /**
     * 完成 {@code acl} 对应的报表定义接口处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:acl:manage")
    @GetMapping("/acl/{type}/{id}")
    public R<List<Grant>> acl(@PathVariable String type, @PathVariable String id) {
        return R.ok(definitions.acl(actors.current(), type, id));
    }

    /**
     * 完成 {@code acl} 对应的报表定义接口处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     * @param grants 当前操作的 {@code grants} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:acl:manage")
    @PutMapping("/acl/{type}/{id}")
    public R<Void> acl(
            @PathVariable String type, @PathVariable String id, @RequestBody List<Grant> grants) {
        definitions.acl(actors.current(), type, id, grants);
        return R.ok();
    }

    /**
     * 显式准备当前管理员有业务权限的初始模板。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:manage")
    @PostMapping("/bootstrap")
    public R<List<Definition>> bootstrap() {
        return R.ok(definitions.bootstrap(actors.current()));
    }

    /**
     * 完成 {@code audit} 对应的报表定义接口处理。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:audit:query")
    @GetMapping("/audit")
    public R<List<Map<String, Object>>> audit() {
        return R.ok(definitions.audits(actors.current()));
    }
}
