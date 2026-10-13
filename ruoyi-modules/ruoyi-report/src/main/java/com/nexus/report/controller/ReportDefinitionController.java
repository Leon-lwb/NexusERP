package com.nexus.report.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.core.domain.R;
import com.nexus.common.report.TypedValue;
import java.time.Instant;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.service.ReportDefinitionService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/report")
public class ReportDefinitionController {
    private final ReportRequestActor actors;private final ReportDefinitionService definitions;private final ReportCompiler compiler;private final ReportProperties properties;
    public ReportDefinitionController(ReportRequestActor actors,ReportDefinitionService definitions,ReportCompiler compiler,ReportProperties properties){this.actors=actors;this.definitions=definitions;this.compiler=compiler;this.properties=properties;}
    public record DraftRequest(JsonNode dsl,Long revision,String catalogId) { }
    public record PreviewRequest(String reportId,JsonNode dsl,Map<String,Object> parameters) { }
    public record RevisionRequest(Long revision) { }
    public record RollbackRequest(String versionId,Long revision) { }
    public record StateRequest(String state,Long revision,String expectedStatus) { }
    public record CatalogRequest(String parentId,String name) { }
    public record RegisterRequest(String code,int version) { }
    public record Detail(Definition definition,ReportDsl dsl) { }
    public record Published(String versionId,int number,ReportDsl dsl) { }
    public record SampleView(List<Map<String,TypedValue>> rows,long total,Instant dataTime) { }
    private ReportDsl dsl(JsonNode node){if(node==null)throw ReportFault.invalid("报表定义不能为空");return compiler.parse(node.toString());}
    private static long revision(Long value){if(value==null||value<1)throw ReportFault.invalid("请提供当前草稿版本号");return value;}
    @SaCheckPermission("report:definition:list") @GetMapping("/definitions")
    public R<List<Definition>> list(){return R.ok(definitions.list(actors.current()));}
    @SaCheckPermission("report:definition:edit") @GetMapping("/definitions/{id}")
    public R<Detail> detail(@PathVariable String id){var actor=actors.current();var definition=definitions.detail(actor,id);var dsl=compiler.parse(definition.json());definitions.bind(actor,dsl);return R.ok(new Detail(definition,dsl));}
    @SaCheckPermission("report:runtime:preview") @GetMapping("/definitions/{id}/published")
    public R<Published> published(@PathVariable String id){var actor=actors.current();var version=definitions.published(actor,id,"VIEW","report:runtime:preview");var dsl=compiler.parse(version.json());definitions.bind(actor,dsl);return R.ok(new Published(version.id(),version.number(),dsl));}
    @SaCheckPermission("report:definition:add") @PostMapping("/definitions")
    public R<Definition> create(@RequestBody DraftRequest body){return R.ok(definitions.create(actors.current(),dsl(body.dsl()),body.catalogId()));}
    @SaCheckPermission("report:definition:edit") @PutMapping("/definitions/{id}")
    public R<Definition> save(@PathVariable String id,@RequestBody DraftRequest body){return R.ok(definitions.save(actors.current(),id,revision(body.revision()),dsl(body.dsl())));}
    @PostMapping("/definitions/validate")
    public R<Map<String,Object>> validate(@RequestBody PreviewRequest body){return R.ok(definitions.validate(actors.current(),body.reportId(),dsl(body.dsl())));}
    @PostMapping("/definitions/preview")
    public R<ReportDefinitionService.DraftPreview> preview(@RequestBody PreviewRequest body){return R.ok(definitions.preview(actors.current(),body.reportId(),dsl(body.dsl()),body.parameters(),properties.getFontPath()));}
    @SaCheckPermission("report:definition:publish") @PostMapping("/definitions/{id}/publish")
    public R<Version> publish(@PathVariable String id,@RequestBody RevisionRequest body){return R.ok(definitions.publish(actors.current(),id,revision(body.revision())));}
    @SaCheckPermission("report:definition:list") @GetMapping("/definitions/{id}/versions")
    public R<List<Version>> versions(@PathVariable String id){return R.ok(definitions.versions(actors.current(),id));}
    @SaCheckPermission("report:definition:publish") @PostMapping("/definitions/{id}/rollback")
    public R<Void> rollback(@PathVariable String id,@RequestBody RollbackRequest body){definitions.rollback(actors.current(),id,body.versionId(),revision(body.revision()));return R.ok();}
    @PutMapping("/definitions/{id}/state")
    public R<Void> state(@PathVariable String id,@RequestBody StateRequest body){definitions.state(actors.current(),id,body.state(),revision(body.revision()));return R.ok();}
    @SaCheckPermission("report:definition:list") @GetMapping("/catalogs")
    public R<List<Catalog>> catalogs(){return R.ok(definitions.catalogs(actors.current()));}
    @SaCheckPermission("report:catalog:manage") @PostMapping("/catalogs")
    public R<Catalog> catalog(@RequestBody CatalogRequest body){return R.ok(definitions.catalog(actors.current(),body.parentId(),body.name()));}
    @SaCheckPermission("report:dataset:list") @GetMapping("/datasets")
    public R<List<ReportDefinitionService.DatasetView>> datasets(){return R.ok(definitions.datasets(actors.current()));}
    @SaCheckPermission("report:dataset:manage") @GetMapping("/datasets/providers")
    public R<List<com.nexus.common.report.DatasetDescriptor>> providers(){return R.ok(definitions.providers(actors.current()));}
    @SaCheckPermission("report:dataset:manage") @PostMapping("/datasets")
    public R<Dataset> register(@RequestBody RegisterRequest body){return R.ok(definitions.registerDataset(actors.current(),body.code(),body.version()));}
    @SaCheckPermission("report:dataset:manage") @PutMapping("/datasets/{id}/state")
    public R<Void> datasetState(@PathVariable String id,@RequestBody StateRequest body){definitions.datasetState(actors.current(),id,body.state(),body.expectedStatus());return R.ok();}
    @SaCheckPermission("report:dataset:list") @PostMapping("/datasets/{id}/sample")
    public R<SampleView> sample(@PathVariable String id,@RequestBody(required=false) Map<String,Object> input){
        var actor=actors.current();
        var descriptor=definitions.datasets(actor).stream().filter(view->view.dataset().id().equals(id))
            .map(ReportDefinitionService.DatasetView::descriptor).findFirst().orElseThrow(ReportFault::denied);
        var result=definitions.sample(actor,id,input);
        var rows=result.rows().stream().map(raw->{
            Map<String,TypedValue> row=new LinkedHashMap<>();
            for(var field:descriptor.fields())if(raw.containsKey(field.name()))row.put(field.name(),TypedValue.of(field.type(),raw.get(field.name())));
            return Collections.unmodifiableMap(row);
        }).toList();
        return R.ok(new SampleView(rows,result.total(),result.dataTime()));
    }
    @SaCheckPermission("report:acl:manage") @GetMapping("/acl/{type}/{id}")
    public R<List<Grant>> acl(@PathVariable String type,@PathVariable String id){return R.ok(definitions.acl(actors.current(),type,id));}
    @SaCheckPermission("report:acl:manage") @PutMapping("/acl/{type}/{id}")
    public R<Void> acl(@PathVariable String type,@PathVariable String id,@RequestBody List<Grant> grants){definitions.acl(actors.current(),type,id,grants);return R.ok();}
    @SaCheckPermission("report:dataset:manage") @PostMapping("/bootstrap")
    public R<List<Definition>> bootstrap(){return R.ok(definitions.bootstrap(actors.current()));}
    @SaCheckPermission("report:audit:query") @GetMapping("/audit")
    public R<List<Map<String,Object>>> audit(){return R.ok(definitions.audits(actors.current()));}
}
