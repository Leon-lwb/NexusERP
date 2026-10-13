package com.nexus.report.service;
import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.JdbcReportStore;
import org.springframework.dao.DuplicateKeyException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Lifecycle and resource guard. Business facts are read only through versioned providers. */
public class ReportDefinitionService {
    private final JdbcReportStore store;private final ReportProviderRegistry registry;private final ReportAuthorizationPort authorization;private final ReportCompiler compiler;
    public ReportDefinitionService(JdbcReportStore store,ReportProviderRegistry registry,ReportAuthorizationPort authorization,ReportCompiler compiler){this.store=store;this.registry=registry;this.authorization=authorization;this.compiler=compiler;}
    public record Binding(CompiledReport compiled,Dataset dataset,DatasetVersion version,ReportDatasetProvider provider) { }
    public record DatasetView(Dataset dataset,DatasetDescriptor descriptor,boolean canManage) { }
    public record DraftPreview(List<Map<String,TypedValue>> rows,long total,String dataTime,List<ReportCalculator.SummaryValue> summaries,RenderPlan plan) { }
    public ReportActor resolve(String tenant, long actor) {
        ReportActor current = authorization.resolve(tenant, actor);
        if (current == null || !tenant.equals(current.tenantId()) || current.userId() != actor) throw ReportFault.denied();
        return current;
    }
    public static void require(ReportActor actor,String permission){if(!actor.has(permission))throw ReportFault.denied();}
    public List<Definition> list(ReportActor actor){require(actor,"report:definition:list");return store.definitions(actor).stream().map(d->new Definition(d.id(),d.tenant(),d.code(),d.name(),d.kind(),d.catalogId(),null,d.revision(),d.publishedVersion(),d.status(),d.owner(),d.updatedAt())).toList();}
    public Definition definition(ReportActor actor,String id,String action,String operation){require(actor,operation);var d=store.definition(actor.tenantId(),id).orElseThrow(ReportDefinitionService::missing);if(d.status().equals("ARCHIVED")||!store.allowed(actor,"REPORT",d.id(),d.owner(),action))throw ReportFault.denied();return d;}
    public Definition detail(ReportActor actor,String id){return definition(actor,id,"EDIT","report:definition:edit");}
    public List<DatasetDescriptor> providers(ReportActor actor){require(actor,"report:dataset:manage");return registry.latest().stream().map(ReportDatasetProvider::descriptor).filter(d->actor.has(d.permission())).map(d->masked(d,actor)).toList();}
    public List<DatasetView> datasets(ReportActor actor){require(actor,"report:dataset:list");return store.datasets(actor).stream().filter(d->d.publishedVersion()!=null).map(d->{var version=store.datasetVersion(actor.tenantId(),d.publishedVersion()).orElseThrow(ReportDefinitionService::missing);var descriptor=registry.get(version.provider(),version.providerVersion()).descriptor();return new DatasetView(d,masked(descriptor,actor),actor.has("report:dataset:manage")&&store.allowed(actor,"DATASET",d.id(),d.owner(),"EDIT"));}).filter(view->actor.has(view.descriptor.permission())).toList();}
    private static DatasetDescriptor masked(DatasetDescriptor d,ReportActor actor){var fields=d.fields().stream().filter(f->f.permission()==null||f.permission().isBlank()||actor.has(f.permission())).toList();Set<String> visible=new HashSet<>();fields.forEach(f->visible.add(f.name()));return new DatasetDescriptor(d.code(),d.name(),d.version(),d.permission(),d.document(),fields,d.parameters(),d.defaultColumns().stream().filter(visible::contains).toList());}
    public Dataset registerDataset(ReportActor actor,String code,int version){require(actor,"report:dataset:manage");var provider=version<=0?registry.latest(code):registry.get(code,version);var d=provider.descriptor();if(!actor.has(d.permission()))throw ReportFault.denied();var existing=store.datasetByCode(actor.tenantId(),code);if(existing.isPresent()&&!store.allowed(actor,"DATASET",existing.get().id(),existing.get().owner(),"EDIT"))throw ReportFault.denied();String json=compiler.json(d);var result=store.registerDataset(actor,d,json,hash(json));store.audit(actor,result.id(),"DATASET_PUBLISH","SUCCEEDED",null);return result;}
    public void datasetState(ReportActor actor, String id, String state) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = managedDataset(current, id);
        datasetState(current, id, state, dataset.status());
    }
    public void datasetState(ReportActor actor, String id, String state, String expectedStatus) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = managedDataset(current, id);
        if (expectedStatus == null || !dataset.status().equals(expectedStatus)) throw revisionConflict();
        store.datasetState(current, id, state, expectedStatus);
        store.audit(current, id, "DATASET_STATE", "SUCCEEDED", null);
    }
    public Binding bind(ReportActor actor,ReportDsl dsl){
        var version=store.datasetVersion(actor.tenantId(),dsl.datasetVersionId()).orElseThrow(ReportDefinitionService::missing);var dataset=store.dataset(actor.tenantId(),version.datasetId()).orElseThrow(ReportDefinitionService::missing);
        if(!dataset.code().equals(dsl.datasetCode())||!dataset.status().equals("ACTIVE")||!store.allowed(actor,"DATASET",dataset.id(),dataset.owner(),"VIEW"))throw ReportFault.denied();
        var provider=registry.get(version.provider(),version.providerVersion());var descriptor=provider.descriptor();
        if(!hash(compiler.json(descriptor)).equals(version.checksum()))throw new ReportFault("VERSION_UNSUPPORTED","数据集契约已变化，请发布新数据集版本并重新绑定");
        return new Binding(compiler.compile(dsl,descriptor,actor),dataset,version,provider);
    }
    public void requireDatasetExport(ReportActor actor,Binding binding) {
        if(!store.allowed(actor,"DATASET",binding.dataset().id(),binding.dataset().owner(),"EXPORT")) throw ReportFault.denied();
    }
    public Definition create(ReportActor actor,ReportDsl dsl,String catalog){require(actor,"report:definition:add");bind(actor,dsl);var result=store.createDefinition(actor,dsl.code(),dsl.title(),dsl.kind().name(),catalog,compiler.json(dsl));store.audit(actor,result.id(),"CREATE","SUCCEEDED",null);return result;}
    public Definition save(ReportActor actor, String id, long revision, ReportDsl dsl) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "EDIT", "report:definition:edit");
        checkRevision(definition, revision);
        identity(definition, dsl);
        bind(current, dsl);
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "EDIT", "report:definition:edit"), revision);
        var result = store.saveDraft(current, id, revision, dsl.title(), compiler.json(dsl));
        store.audit(current, id, "SAVE", "SUCCEEDED", null);
        return result;
    }
    public Map<String,Object> validate(ReportActor actor,String id,ReportDsl dsl){if(id==null)require(actor,"report:definition:add");else definition(actor,id,"EDIT","report:definition:edit");var binding=bind(actor,dsl);return Map.of("valid",true,"schemaVersion",1,"requiredFields",binding.compiled.requiredFields(),"computedOrder",binding.compiled.computedOrder());}
    public Version publish(ReportActor actor, String id, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "PUBLISH", "report:definition:publish");
        checkRevision(definition, revision);
        ReportDsl dsl = compiler.parse(definition.json());
        bind(current, dsl);
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        String text = compiler.json(dsl);
        var result = store.publish(current, id, revision, text, hash(text), dsl.datasetVersionId());
        store.audit(current, id, "PUBLISH", "SUCCEEDED", "{\"version\":" + result.number() + "}");
        return result;
    }
    public List<Version> versions(ReportActor actor,String id){definition(actor,id,"VIEW","report:definition:list");return store.versions(actor.tenantId(),id);}
    public void rollback(ReportActor actor, String id, String version) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "PUBLISH", "report:definition:publish");
        rollback(current, id, version, definition.revision());
    }
    public void rollback(ReportActor actor, String id, String version, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        Version target = store.version(current.tenantId(), version).orElseThrow(ReportDefinitionService::missing);
        if (!id.equals(target.reportId())) throw missing();
        bind(current, versionDsl(target));
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        store.rollback(current, id, version, revision);
        store.audit(current, id, "ROLLBACK", "SUCCEEDED", null);
    }
    public void state(ReportActor actor, String id, String state) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = stateDefinition(current, id, state);
        state(current, id, state, definition.revision());
    }
    public void state(ReportActor actor, String id, String state, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        checkRevision(stateDefinition(current, id, state), revision);
        store.definitionState(current, id, state, revision);
        store.audit(current, id, state, "SUCCEEDED", null);
    }
    public List<Grant> acl(ReportActor actor,String type,String id){resourceManage(actor,type,id);return store.acl(actor.tenantId(),type,id);}
    public void acl(ReportActor actor, String type, String id, List<Grant> grants) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        resourceManage(current, type, id);
        if (grants == null || grants.size() > 200) throw ReportFault.capacity();
        for (Grant grant : grants) {
            if (grant == null || grant.subjectType() == null || grant.action() == null || grant.subjectId() == null
                || !Set.of("USER", "ROLE", "DEPT").contains(grant.subjectType())
                || !Set.of("VIEW", "EDIT", "PUBLISH", "EXPORT", "MANAGE").contains(grant.action())) throw ReportFault.invalid("授权规则无效");
            long subject;
            try { subject = Long.parseLong(grant.subjectId()); }
            catch (RuntimeException invalid) { throw ReportFault.invalid("授权主体标识无效"); }
            if (subject <= 0) throw ReportFault.invalid("授权主体标识无效");
            if (!authorization.subjectExists(current.tenantId(), grant.subjectType(), subject)) throw ReportFault.denied();
        }
        current = resolve(actor.tenantId(), actor.userId());
        resourceManage(current, type, id);
        store.replaceAcl(current, type, id, grants);
        store.audit(current, id, "ACL_CHANGE", "SUCCEEDED", null);
    }
    private void resourceManage(ReportActor actor, String type, String id) {
        require(actor, "report:acl:manage");
        if ("REPORT".equals(type)) definition(actor, id, "MANAGE", "report:acl:manage");
        else if ("DATASET".equals(type)) {
            Dataset dataset = store.dataset(actor.tenantId(), id).orElseThrow(ReportDefinitionService::missing);
            if (dataset.status().equals("ARCHIVED") || !store.allowed(actor, type, id, dataset.owner(), "MANAGE")) throw ReportFault.denied();
        } else throw ReportFault.invalid("资源类型无效");
    }
    public List<Catalog> catalogs(ReportActor actor){require(actor,"report:definition:list");return store.catalogs(actor.tenantId());}
    public Catalog catalog(ReportActor actor,String parent,String name){require(actor,"report:catalog:manage");if(name==null||name.isBlank()||name.length()>100)throw ReportFault.invalid("目录名称长度为 1–100");return store.createCatalog(actor,parent,name);}
    public List<Map<String,Object>> audits(ReportActor actor){require(actor,"report:audit:query");return store.audits(actor.tenantId());}
    public Map<String, Object> parameters(Binding binding, Map<String, Object> input) {
        Set<String> declared = new HashSet<>();
        binding.compiled().dsl().parameters().forEach(parameter -> declared.add(parameter.name()));
        if (input != null && !declared.containsAll(input.keySet())) throw new DatasetAccessException("INVALID_PARAMETER", "包含未声明的报表参数");
        var values = new LinkedHashMap<String, Object>();
        for (var parameter : binding.compiled().dsl().parameters()) if (parameter.defaultValue() != null && !parameter.defaultValue().isBlank()) {
            String value = parameter.defaultValue();
            if (parameter.type().equals("DATE") && value.equals("TODAY")) value = java.time.LocalDate.now().toString();
            if (parameter.type().equals("DATE") && value.equals("MONTHS_AGO_6")) value = java.time.LocalDate.now().minusMonths(6).toString();
            values.put(parameter.name(), value);
        }
        if (input != null) values.putAll(input);
        return DatasetQueryValidator.parameters(binding.provider().descriptor(), values);
    }
    public DraftPreview preview(ReportActor actor, String id, ReportDsl dsl, Map<String, Object> input, String font) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        previewPermission(current, id, dsl);
        Binding binding = bind(current, dsl);
        String fingerprint = fingerprint(current, binding);
        Map<String, Object> parameters = parameters(binding, input);
        var result = binding.provider().query(binding.compiled().context(current), new DatasetQuery(parameters, binding.compiled().requiredFields(), 1, 200, false, null, false));
        if (result.rows().size() > 200) throw ReportFault.capacity();
        var data = new ReportCalculator().calculate(binding.compiled(), result.rows(), parameters);
        RenderPlan plan = dsl.kind() == ReportDsl.Kind.DOCUMENT
            ? new DocumentLayoutEngine().layout(binding.compiled(), data, parameters, FontSupport.find(font)) : null;
        current = resolve(actor.tenantId(), actor.userId());
        previewPermission(current, id, dsl);
        if (!fingerprint.equals(fingerprint(current, bind(current, dsl)))) throw scopeChanged();
        return new DraftPreview(project(data.rows(), dsl), result.total(), result.dataTime().toString(), data.summaries(), plan);
    }
    public DatasetResult sample(ReportActor actor, String id, Map<String, Object> input) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = sampleDataset(current, id);
        DatasetVersion version = store.datasetVersion(current.tenantId(), dataset.publishedVersion()).orElseThrow(ReportDefinitionService::missing);
        if (!version.datasetId().equals(dataset.id())) throw missing();
        var provider = registry.get(version.provider(), version.providerVersion());
        DatasetDescriptor descriptor = provider.descriptor();
        if (!hash(compiler.json(descriptor)).equals(version.checksum())) throw new ReportFault("VERSION_UNSUPPORTED", "数据集契约已变化");
        Set<String> names = new LinkedHashSet<>(masked(descriptor, current).defaultColumns());
        if (names.isEmpty()) throw ReportFault.denied();
        var context = sampleContext(current, descriptor, names);
        String fingerprint = scopeFingerprint(current, version, names, context.scopes());
        var parameters = DatasetQueryValidator.parameters(descriptor, input);
        var result = provider.query(context, new DatasetQuery(parameters, names, 1, 50, false, null, false));
        if (result.rows().size() > 50) throw ReportFault.capacity();
        current = resolve(actor.tenantId(), actor.userId());
        sampleDataset(current, id);
        var recheck = sampleContext(current, descriptor, names);
        if (!fingerprint.equals(scopeFingerprint(current, version, names, recheck.scopes()))) throw scopeChanged();
        List<Map<String, Object>> rows = result.rows().stream().map(row -> {
            Map<String, Object> projected = new LinkedHashMap<>();
            names.forEach(name -> projected.put(name, row.get(name)));
            return projected;
        }).toList();
        return new DatasetResult(rows, result.total(), result.dataTime());
    }
    public List<Definition> bootstrap(ReportActor actor) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        require(current, "report:dataset:manage"); require(current, "report:definition:add"); require(current, "report:definition:publish");
        String catalog = store.ensureRootCatalog(current).id();
        List<Definition> results = new ArrayList<>();
        for (var provider : registry.latest()) {
            var descriptor = provider.descriptor();
            if (!current.has(descriptor.permission())) continue;
            Dataset dataset = registerDataset(current, descriptor.code(), descriptor.version());
            String code = "BASE_" + descriptor.code();
            var existing = store.definitionByCode(current.tenantId(), code);
            if (existing.isPresent()) { addExistingTemplate(results, current, existing.get()); continue; }
            var dsl = ReportTemplates.initial(masked(descriptor, current), dataset.publishedVersion(), code);
            if (dsl.columns().isEmpty()) continue;
            Definition created;
            try { created = create(current, dsl, catalog); }
            catch (DuplicateKeyException race) {
                Definition canonical = store.definitionByCode(current.tenantId(), code).orElseThrow(ReportDefinitionService::revisionConflict);
                addExistingTemplate(results, current, canonical);
                continue;
            }
            publish(current, created.id(), created.revision());
            results.add(store.definition(current.tenantId(), created.id()).orElseThrow(ReportDefinitionService::missing));
        }
        return List.copyOf(results);
    }
    private void addExistingTemplate(List<Definition> results, ReportActor actor, Definition definition) {
        if (!definition.status().equals("ARCHIVED") && actor.has("report:definition:list")
            && store.allowed(actor, "REPORT", definition.id(), definition.owner(), "VIEW")) results.add(definition);
    }
    public Version published(ReportActor actor, String report, String action, String permission) {
        var definition = definition(actor, report, action, permission);
        if (!definition.status().equals("PUBLISHED") || definition.publishedVersion() == null) throw new ReportFault("REPORT_OFFLINE", "报表尚未发布或已经下线", 409);
        Version version = store.version(actor.tenantId(), definition.publishedVersion()).orElseThrow(ReportDefinitionService::missing);
        if (!definition.id().equals(version.reportId())) throw missing();
        versionDsl(version);
        return version;
    }
    /** Published references are immutable and are executed only by their declared compatible engine. */
    public ReportDsl versionDsl(Version version) {
        if(version.schema()!=1 || !ReportCompiler.ENGINE_VERSION.equals(version.engine()))
            throw new ReportFault("VERSION_UNSUPPORTED", "所需报表引擎或格式版本未安装");
        if(version.json()==null || !hash(version.json()).equals(version.checksum()))
            throw new ReportFault("VERSION_INVALID", "已发布报表校验失败", 409);
        var dsl=compiler.parse(version.json());
        if(dsl.schemaVersion()!=version.schema() || !Objects.equals(dsl.datasetVersionId(),version.datasetVersion()))
            throw new ReportFault("VERSION_INVALID", "已发布报表引用不一致", 409);
        return dsl;
    }
    public static List<Map<String,TypedValue>> project(List<Map<String,TypedValue>> rows,ReportDsl dsl){return rows.stream().map(row->{Map<String,TypedValue> result=new LinkedHashMap<>();dsl.columns().forEach(c->result.put(c.field(),row.get(c.field())));return result;}).toList();}
    public String fingerprint(ReportActor actor, Binding binding) {
        return scopeFingerprint(actor, binding.version(), binding.compiled().requiredFields(), binding.compiled().context(actor).scopes());
    }
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}}

    private Dataset managedDataset(ReportActor actor, String id) {
        require(actor, "report:dataset:manage");
        Dataset dataset = store.dataset(actor.tenantId(), id).orElseThrow(ReportDefinitionService::missing);
        if (dataset.status().equals("ARCHIVED") || !store.allowed(actor, "DATASET", id, dataset.owner(), "EDIT")) throw ReportFault.denied();
        return dataset;
    }
    private Dataset sampleDataset(ReportActor actor, String id) {
        require(actor, "report:dataset:list");
        Dataset dataset = store.dataset(actor.tenantId(), id).orElseThrow(ReportDefinitionService::missing);
        if (!dataset.status().equals("ACTIVE") || !store.allowed(actor, "DATASET", id, dataset.owner(), "VIEW")) throw ReportFault.denied();
        return dataset;
    }
    private static ReportExecutionContext sampleContext(ReportActor actor, DatasetDescriptor descriptor, Set<String> names) {
        require(actor, descriptor.permission());
        List<ReportScope> scopes = new ArrayList<>(); scopes.add(actor.scopeFor(descriptor.permission()));
        Set<String> permissions = new TreeSet<>();
        for (String name : names) { String permission = descriptor.field(name).permission(); if (permission != null && !permission.isBlank()) permissions.add(permission); }
        for (String permission : permissions) { require(actor, permission); scopes.add(actor.scopeFor(permission)); }
        return new ReportExecutionContext(actor, scopes);
    }
    private Definition stateDefinition(ReportActor actor, String id, String state) {
        if (state == null || !Set.of("OFFLINE", "ARCHIVED").contains(state)) throw ReportFault.invalid("报表状态无效");
        return definition(actor, id, state.equals("ARCHIVED") ? "EDIT" : "PUBLISH", state.equals("ARCHIVED") ? "report:definition:remove" : "report:definition:publish");
    }
    private void previewPermission(ReportActor actor, String id, ReportDsl dsl) {
        if (id == null) require(actor, "report:definition:add");
        else identity(definition(actor, id, "EDIT", "report:definition:edit"), dsl);
    }
    private static void identity(Definition definition, ReportDsl dsl) {
        if (dsl == null || dsl.kind() == null || !definition.code().equals(dsl.code()) || !definition.kind().equals(dsl.kind().name())) throw ReportFault.invalid("已有报表编码和类型不可更改");
    }
    private static void checkRevision(Definition definition, long revision) {
        if (definition.revision() != revision) throw revisionConflict();
    }
    private String scopeFingerprint(ReportActor actor, DatasetVersion version, Set<String> fields, List<ReportScope> scopes) {
        var source = new TreeMap<String, Object>();
        source.put("tenant", actor.tenantId()); source.put("actor", Long.toString(actor.userId())); source.put("department", Long.toString(actor.departmentId()));
        source.put("dataset", version.id()); source.put("descriptor", version.checksum()); source.put("fields", new TreeSet<>(fields));
        Set<String> policies = new TreeSet<>();
        for (ReportScope scope : scopes) {
            var policy = new TreeMap<String, Object>(); policy.put("all", scope.all());
            if (!scope.all()) { policy.put("departments", new TreeSet<>(scope.departments())); policy.put("self", scope.self()); }
            policies.add(compiler.json(policy));
        }
        source.put("scopes", policies);
        return hash(compiler.json(source));
    }
    private static ReportFault revisionConflict() { return new ReportFault("REVISION_CONFLICT", "内容或状态已变化，请重新读取后操作", 409); }
    private static ReportFault scopeChanged() { return new ReportFault("SCOPE_CHANGED", "权限范围已变化，请重新预览或运行报表", 403); }

    private static ReportFault missing(){return new ReportFault("NOT_FOUND","报表资源不存在或不可访问",404);}
}
