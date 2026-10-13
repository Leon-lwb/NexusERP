package com.nexus.report.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Metadata is always master; every public resource lookup/mutation binds its tenant. */
@Repository
@DS("master")
public class JdbcReportStore {
    private static final String ROOT_NAME = "报表中心";
    private static final Set<String> ACL_ACTIONS = Set.of("VIEW", "EDIT", "PUBLISH", "RUN", "EXPORT", "ACL", "MANAGE");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcReportStore(JdbcTemplate jdbc, ObjectMapper json) {
        var source = Objects.requireNonNull(jdbc.getDataSource());
        this.jdbc = new JdbcTemplate(source);
        this.jdbc.setQueryTimeout(10);
        var manager = new DataSourceTransactionManager(source);
        manager.setValidateExistingTransaction(true);
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(10);
        // A waiter must see the preceding mutex holder's committed counts/pointers on MySQL 5.7 as well as H2.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private static String id() { return Long.toString(IdWorker.getId()); }
    private static Long number(String id) {
        if (id == null) return null;
        try {
            long value = Long.parseLong(id);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw new ReportFault("INVALID_PARAMETER", "资源标识无效");
        }
    }
    private static String textId(ResultSet rs, String name) throws SQLException {
        Object value = rs.getObject(name);
        return value == null ? null : value.toString();
    }
    private static Instant time(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = jdbc.query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
    private <T> T atomic(Supplier<T> action) { return transaction.execute(status -> action.get()); }
    private Instant now() { return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class)).toInstant(); }
    private static final RowMapper<Catalog> CATALOG = (r, i) -> new Catalog(textId(r, "catalog_id"), textId(r, "parent_id"), r.getString("name"), r.getLong("create_by"));
    private static final RowMapper<Definition> DEFINITION=(r,i)->new Definition(textId(r,"report_id"),r.getString("tenant_id"),r.getString("code"),r.getString("name"),r.getString("kind"),textId(r,"catalog_id"),r.getString("draft_json"),r.getLong("draft_revision"),textId(r,"published_version_id"),r.getString("status"),r.getLong("create_by"),time(r,"update_time"));
    private static final RowMapper<Version> VERSION=(r,i)->new Version(textId(r,"version_id"),textId(r,"report_id"),r.getInt("version_no"),r.getInt("schema_version"),r.getString("engine_version"),r.getString("dsl_json"),r.getString("checksum"),textId(r,"dataset_version_id"),r.getLong("create_by"),time(r,"create_time"));
    private static final RowMapper<Dataset> DATASET=(r,i)->new Dataset(textId(r,"dataset_id"),r.getString("tenant_id"),r.getString("code"),r.getString("name"),r.getString("provider_code"),textId(r,"published_version_id"),r.getString("status"),r.getLong("create_by"));
    private static final RowMapper<DatasetVersion> DATASET_VERSION=(r,i)->new DatasetVersion(textId(r,"version_id"),textId(r,"dataset_id"),r.getInt("version_no"),r.getString("provider_code"),r.getInt("provider_version"),r.getString("descriptor_json"),r.getString("checksum"),r.getLong("create_by"));
    private static final RowMapper<Run> RUN=(r,i)->new Run(textId(r,"run_id"),r.getString("tenant_id"),r.getLong("actor_id"),textId(r,"report_id"),textId(r,"version_id"),r.getString("mode"),r.getString("state"),r.getString("parameters_json"),r.getString("scope_hash"),r.getString("snapshot_ref"),r.getString("plan_ref"),r.getLong("row_count"),time(r,"data_time"),time(r,"expires_at"),r.getString("error_code"));
    private static final RowMapper<ExportJob> JOB=(r,i)->new ExportJob(textId(r,"job_id"),r.getString("tenant_id"),r.getLong("actor_id"),textId(r,"run_id"),r.getString("format"),r.getString("idempotency_key"),r.getString("request_hash"),r.getString("state"),r.getInt("attempts"),r.getString("lease_owner"),time(r,"lease_until"),r.getBoolean("cancel_requested"),r.getString("file_ref"),(Long)r.getObject("file_size"),time(r,"expires_at"),r.getString("error_code"));

    public List<Catalog> catalogs(String tenant) {
        return jdbc.query("SELECT catalog_id,parent_id,name,create_by FROM rpt_catalog WHERE tenant_id=? ORDER BY sort_order,catalog_id", CATALOG, tenant);
    }

    /** Canonical, immutable root; its row is also the report-owned tenant admission mutex. */
    public Catalog ensureRootCatalog(ReportActor actor) {
        return atomic(() -> {
            String lookup = "SELECT catalog_id,parent_id,name,create_by FROM rpt_catalog WHERE tenant_id=? AND parent_id=0 AND name=?";
            if (one(lookup, CATALOG, actor.tenantId(), ROOT_NAME).isEmpty()) {
                try {
                    jdbc.update("INSERT INTO rpt_catalog(catalog_id,parent_id,name,tenant_id,create_dept,create_by) VALUES(?,0,?,?,?,?)",
                        number(id()), ROOT_NAME, actor.tenantId(), actor.departmentId(), actor.userId());
                } catch (DuplicateKeyException duplicate) {
                    // Another node inserted the same unique (tenant,parent,name). Read the canonical row under lock.
                }
            }
            return one(lookup + " FOR UPDATE", CATALOG, actor.tenantId(), ROOT_NAME).orElseThrow(JdbcReportStore::conflict);
        });
    }

    public Catalog createCatalog(ReportActor actor, String parent, String name) {
        if (name == null || name.isBlank() || name.length() > 100) throw ReportFault.invalid("目录名称无效");
        long parentId = parent == null || parent.equals("0") ? 0 : number(parent);
        if (parentId == 0 && ROOT_NAME.equals(name)) return ensureRootCatalog(actor);
        return atomic(() -> {
            if (parentId != 0 && one("SELECT catalog_id FROM rpt_catalog WHERE tenant_id=? AND catalog_id=? FOR UPDATE",
                (r, i) -> r.getLong(1), actor.tenantId(), parentId).isEmpty()) throw missing();
            String id = id();
            jdbc.update("INSERT INTO rpt_catalog(catalog_id,parent_id,name,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?)",
                number(id), parentId, name, actor.tenantId(), actor.departmentId(), actor.userId());
            return new Catalog(id, Long.toString(parentId), name, actor.userId());
        });
    }

    public List<Definition> definitions(ReportActor actor) {
        return jdbc.query("SELECT * FROM rpt_definition WHERE tenant_id=? AND status<>'ARCHIVED' ORDER BY update_time DESC,report_id DESC LIMIT 10000",
            DEFINITION, actor.tenantId()).stream().filter(d -> allowed(actor, "REPORT", d.id(), d.owner(), "VIEW")).toList();
    }
    public Optional<Definition> definition(String tenant, String id) {
        return one("SELECT * FROM rpt_definition WHERE tenant_id=? AND report_id=?", DEFINITION, tenant, number(id));
    }
    public Optional<Definition> definitionByCode(String tenant, String code) {
        return one("SELECT * FROM rpt_definition WHERE tenant_id=? AND code=?", DEFINITION, tenant, code);
    }
    private Definition lockDefinition(ReportActor actor, String report) {
        return one("SELECT * FROM rpt_definition WHERE tenant_id=? AND report_id=? FOR UPDATE", DEFINITION,
            actor.tenantId(), number(report)).orElseThrow(JdbcReportStore::missing);
    }
    private static void editable(Definition definition, Long revision) {
        if (definition.status().equals("ARCHIVED") || revision != null && definition.revision() != revision) throw conflict();
    }

    public Definition createDefinition(ReportActor actor, String code, String name, String kind, String catalog, String draft) {
        return atomic(() -> {
            if (catalog != null && one("SELECT catalog_id FROM rpt_catalog WHERE tenant_id=? AND catalog_id=? FOR UPDATE",
                (r, i) -> r.getLong(1), actor.tenantId(), number(catalog)).isEmpty()) throw missing();
            String id = id();
            jdbc.update("INSERT INTO rpt_definition(report_id,catalog_id,code,name,kind,draft_json,draft_revision,status,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?,1,'DRAFT',?,?,?)",
                number(id), number(catalog), code, name, kind, draft, actor.tenantId(), actor.departmentId(), actor.userId());
            return definition(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
        });
    }

    public Definition saveDraft(ReportActor actor, String id, long revision, String name, String draft) {
        return atomic(() -> {
            int changed = jdbc.update("UPDATE rpt_definition SET draft_json=?,name=?,draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND report_id=? AND draft_revision=? AND status<>'ARCHIVED'",
                draft, name, actor.userId(), actor.tenantId(), number(id), revision);
            if (changed != 1) throw conflict();
            return definition(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
        });
    }

    public Version publish(ReportActor actor, String report, long revision, String dsl, String checksum, String datasetVersion) {
        return atomic(() -> {
            Definition definition = lockDefinition(actor, report);
            editable(definition, revision);
            requireActiveDatasetVersion(actor.tenantId(), datasetVersion);
            Integer previous = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM rpt_version WHERE tenant_id=? AND report_id=?",
                Integer.class, actor.tenantId(), number(report));
            int next = (previous == null ? 0 : previous) + 1;
            if (next > 1000) throw ReportFault.capacity();
            String id = id();
            jdbc.update("INSERT INTO rpt_version(version_id,report_id,version_no,schema_version,engine_version,dsl_json,checksum,dataset_version_id,tenant_id,create_dept,create_by) VALUES(?,?,?,1,'1.0.0',?,?,?,?,?,?)",
                number(id), number(report), next, dsl, checksum, number(datasetVersion), actor.tenantId(), actor.departmentId(), actor.userId());
            jdbc.update("UPDATE rpt_definition SET published_version_id=?,status='PUBLISHED',draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND report_id=?",
                number(id), actor.userId(), actor.tenantId(), number(report));
            return version(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
        });
    }

    public Optional<Version> version(String tenant, String id) {
        return one("SELECT v.* FROM rpt_version v JOIN rpt_definition d ON d.report_id=v.report_id AND d.tenant_id=v.tenant_id WHERE v.tenant_id=? AND v.version_id=?",
            VERSION, tenant, number(id));
    }
    public List<Version> versions(String tenant, String report) {
        return jdbc.query("SELECT v.* FROM rpt_version v JOIN rpt_definition d ON d.report_id=v.report_id AND d.tenant_id=v.tenant_id WHERE v.tenant_id=? AND v.report_id=? ORDER BY v.version_no DESC",
            VERSION, tenant, number(report));
    }
    public void rollback(ReportActor actor, String report, String version) { rollback(actor, report, version, null); }
    public void rollback(ReportActor actor, String report, String version, long revision) { rollback(actor, report, version, Long.valueOf(revision)); }
    private void rollback(ReportActor actor, String report, String version, Long revision) {
        atomic(() -> {
            Definition definition = lockDefinition(actor, report);
            editable(definition, revision);
            Version target = version(actor.tenantId(), version).orElseThrow(JdbcReportStore::missing);
            if (!Objects.equals(number(target.reportId()), number(report))) throw missing();
            requireActiveDatasetVersion(actor.tenantId(), target.datasetVersion());
            jdbc.update("UPDATE rpt_definition SET published_version_id=?,status='PUBLISHED',draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND report_id=?",
                number(version), actor.userId(), actor.tenantId(), number(report));
            return null;
        });
    }

    public void definitionState(ReportActor actor, String id, String state) { definitionState(actor, id, state, null); }
    public void definitionState(ReportActor actor, String id, String state, long revision) { definitionState(actor, id, state, Long.valueOf(revision)); }
    private void definitionState(ReportActor actor, String id, String state, Long revision) {
        if (state == null || !Set.of("OFFLINE", "ARCHIVED").contains(state)) throw ReportFault.invalid("报表状态无效");
        atomic(() -> {
            Definition definition = lockDefinition(actor, id);
            if (revision != null && definition.revision() != revision) throw conflict();
            if (definition.status().equals(state)) return null;
            editable(definition, revision);
            jdbc.update("UPDATE rpt_definition SET status=?,draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND report_id=?",
                state, actor.userId(), actor.tenantId(), number(id));
            return null;
        });
    }

    public List<Dataset> datasets(ReportActor actor) {
        return jdbc.query("SELECT * FROM rpt_dataset WHERE tenant_id=? AND status<>'ARCHIVED' ORDER BY name,dataset_id", DATASET,
            actor.tenantId()).stream().filter(d -> allowed(actor, "DATASET", d.id(), d.owner(), "VIEW")).toList();
    }
    public Optional<Dataset> dataset(String tenant, String id) {
        return one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND dataset_id=?", DATASET, tenant, number(id));
    }
    public Optional<Dataset> datasetByCode(String tenant, String code) {
        return one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND code=?", DATASET, tenant, code);
    }

    public Dataset registerDataset(ReportActor actor, DatasetDescriptor descriptor, String json, String checksum) {
        return atomic(() -> {
            ensureRootCatalog(actor);
            // Lock before reading the pointer/checksum, including first-time registration on another node.
            Dataset existing = one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND code=? FOR UPDATE", DATASET,
                actor.tenantId(), descriptor.code()).orElse(null);
            String datasetId = existing == null ? id() : existing.id();
            if (existing == null) {
                jdbc.update("INSERT INTO rpt_dataset(dataset_id,code,name,provider_code,status,tenant_id,create_dept,create_by) VALUES(?,?,?,?,'ACTIVE',?,?,?)",
                    number(datasetId), descriptor.code(), descriptor.name(), descriptor.code(), actor.tenantId(), actor.departmentId(), actor.userId());
            } else {
                if (existing.publishedVersion() != null) {
                    DatasetVersion published = datasetVersion(actor.tenantId(), existing.publishedVersion()).orElseThrow(JdbcReportStore::missing);
                    if (!Objects.equals(published.datasetId(), existing.id())) throw missing();
                    if (published.checksum().equals(checksum)) return existing;
                }
                if (existing.status().equals("ARCHIVED")) throw conflict();
            }
            Integer prior = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM rpt_dataset_version WHERE tenant_id=? AND dataset_id=?",
                Integer.class, actor.tenantId(), number(datasetId));
            int version = (prior == null ? 0 : prior) + 1;
            if (version > 1000) throw ReportFault.capacity();
            String versionId = id();
            jdbc.update("INSERT INTO rpt_dataset_version(version_id,dataset_id,version_no,provider_code,provider_version,descriptor_json,checksum,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                number(versionId), number(datasetId), version, descriptor.code(), descriptor.version(), json, checksum, actor.tenantId(), actor.departmentId(), actor.userId());
            jdbc.update("UPDATE rpt_dataset SET published_version_id=?,name=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND dataset_id=?",
                number(versionId), descriptor.name(), actor.userId(), actor.tenantId(), number(datasetId));
            return dataset(actor.tenantId(), datasetId).orElseThrow(JdbcReportStore::missing);
        });
    }

    public Optional<DatasetVersion> datasetVersion(String tenant, String version) {
        return one("SELECT v.* FROM rpt_dataset_version v JOIN rpt_dataset d ON d.dataset_id=v.dataset_id AND d.tenant_id=v.tenant_id WHERE v.tenant_id=? AND v.version_id=?",
            DATASET_VERSION, tenant, number(version));
    }
    private DatasetVersion requireActiveDatasetVersion(String tenant, String version) {
        DatasetVersion target = datasetVersion(tenant, version).orElseThrow(JdbcReportStore::missing);
        Dataset parent = one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND dataset_id=? FOR UPDATE", DATASET,
            tenant, number(target.datasetId())).orElseThrow(JdbcReportStore::missing);
        if (!parent.status().equals("ACTIVE")) throw conflict();
        return target;
    }

    public void datasetState(ReportActor actor, String dataset, String status) { datasetState(actor, dataset, status, null); }
    public void datasetState(ReportActor actor, String dataset, String status, String expectedStatus) {
        if (status == null || !Set.of("ACTIVE", "OFFLINE", "ARCHIVED").contains(status)) throw ReportFault.invalid("数据集状态无效");
        atomic(() -> {
            Dataset current = one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND dataset_id=? FOR UPDATE", DATASET,
                actor.tenantId(), number(dataset)).orElseThrow(JdbcReportStore::missing);
            if (expectedStatus != null && !current.status().equals(expectedStatus)) throw conflict();
            if (current.status().equals(status)) return null;
            if (current.status().equals("ARCHIVED")) throw conflict();
            if (status.equals("ACTIVE")) {
                DatasetVersion head = datasetVersion(actor.tenantId(), current.publishedVersion()).orElseThrow(JdbcReportStore::missing);
                if (!head.datasetId().equals(current.id())) throw missing();
            }
            jdbc.update("UPDATE rpt_dataset SET status=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND dataset_id=?",
                status, actor.userId(), actor.tenantId(), number(dataset));
            return null;
        });
    }

    private static String table(String type) {
        if (type == null) throw ReportFault.invalid("资源类型无效");
        return switch (type) {
            case "REPORT" -> "rpt_definition";
            case "DATASET" -> "rpt_dataset";
            default -> throw ReportFault.invalid("资源类型无效");
        };
    }
    private static String primary(String type) { table(type); return type.equals("REPORT") ? "report_id" : "dataset_id"; }
    public boolean allowed(ReportActor actor, String type, String resource, long ignoredOwner, String action) {
        String table = table(type);
        if (action == null || !ACL_ACTIONS.contains(action)) return false;
        var owner = one("SELECT create_by FROM " + table + " WHERE tenant_id=? AND " + primary(type) + "=?",
            (r, i) -> r.getLong(1), actor.tenantId(), number(resource));
        if (owner.isEmpty()) return false;
        if (actor.superAdmin() || owner.get() == actor.userId()) return true;
        return granted(actor, type, resource, action);
    }
    private boolean granted(ReportActor actor, String type, String resource, String action) {
        return grants(actor.tenantId(), type, resource).stream().anyMatch(grant -> grant.action().equals(action)
            && (grant.subjectType().equals("USER") && grant.subjectId().equals(Long.toString(actor.userId()))
            || grant.subjectType().equals("ROLE") && actor.roleIds().contains(number(grant.subjectId()))
            || grant.subjectType().equals("DEPT") && grant.subjectId().equals(Long.toString(actor.departmentId()))));
    }
    private List<Grant> grants(String tenant, String type, String resource) {
        return jdbc.query("SELECT subject_type,subject_id,action FROM rpt_acl WHERE tenant_id=? AND resource_type=? AND resource_id=? ORDER BY subject_type,subject_id,action",
            (r, i) -> new Grant(r.getString(1), r.getString(2), r.getString(3)), tenant, type, number(resource));
    }
    public List<Grant> acl(String tenant, String type, String resource) {
        if (one("SELECT create_by FROM " + table(type) + " WHERE tenant_id=? AND " + primary(type) + "=?",
            (r, i) -> r.getLong(1), tenant, number(resource)).isEmpty()) throw missing();
        return grants(tenant, type, resource);
    }
    public void replaceAcl(ReportActor actor, String type, String resource, List<Grant> grants) {
        table(type);
        if (grants == null) throw ReportFault.invalid("授权列表不能为空");
        if (grants.size() > 1000) throw ReportFault.capacity();
        Set<Grant> unique = new LinkedHashSet<>();
        for (Grant grant : grants) {
            if (grant == null || grant.subjectType() == null || grant.action() == null || grant.subjectId() == null
                || !Set.of("USER", "ROLE", "DEPT").contains(grant.subjectType()) || !ACL_ACTIONS.contains(grant.action())) {
                throw ReportFault.invalid("授权主体或操作无效");
            }
            unique.add(new Grant(grant.subjectType(), Long.toString(number(grant.subjectId())), grant.action()));
        }
        atomic(() -> {
            if (one("SELECT status FROM " + table(type) + " WHERE tenant_id=? AND " + primary(type) + "=? FOR UPDATE",
                (r, i) -> r.getString(1), actor.tenantId(), number(resource)).orElseThrow(JdbcReportStore::missing).equals("ARCHIVED")) throw ReportFault.denied();
            long owner = one("SELECT create_by FROM " + table(type) + " WHERE tenant_id=? AND " + primary(type) + "=? FOR UPDATE",
                (r, i) -> r.getLong(1), actor.tenantId(), number(resource)).orElseThrow(JdbcReportStore::missing);
            if (!actor.superAdmin() && owner != actor.userId() && !granted(actor, type, resource, "ACL") && !granted(actor, type, resource, "MANAGE")) {
                throw ReportFault.denied();
            }
            jdbc.update("DELETE FROM rpt_acl WHERE tenant_id=? AND resource_type=? AND resource_id=?", actor.tenantId(), type, number(resource));
            for (Grant grant : unique) {
                jdbc.update("INSERT INTO rpt_acl(acl_id,resource_type,resource_id,subject_type,subject_id,action,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    number(id()), type, number(resource), grant.subjectType(), number(grant.subjectId()), grant.action(), actor.tenantId(), actor.departmentId(), actor.userId());
            }
            return null;
        });
    }
    public void audit(ReportActor actor, String resource, String action, String outcome, String details) {
        jdbc.update("INSERT INTO rpt_audit(audit_id,actor_id,resource_id,action,outcome,details_json,trace_id,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
            number(id()), actor.userId(), number(resource), action, outcome, details, UUID.randomUUID().toString(), actor.tenantId(), actor.departmentId(), actor.userId());
    }
    public List<Map<String, Object>> audits(String tenant) {
        return jdbc.queryForList("SELECT audit_id,actor_id,resource_id,action,outcome,trace_id,create_time FROM rpt_audit WHERE tenant_id=? ORDER BY create_time DESC,audit_id DESC LIMIT 200", tenant);
    }

    public Run createRun(ReportActor actor, String report, String version, String mode, String payload, String hash, Instant expires) {
        return createRun(actor, report, version, mode, payload, hash, expires, false);
    }
    private Run createRun(ReportActor actor, String report, String version, String mode, String payload, String hash, Instant expires, boolean pinned) {
        if (mode == null || !Set.of("LIVE", "SNAPSHOT").contains(mode)) throw ReportFault.invalid("运行模式无效");
        return atomic(() -> {
            Definition definition = lockDefinition(actor, report);
            Version target = version(actor.tenantId(), version).orElseThrow(JdbcReportStore::missing);
            if (!Objects.equals(number(target.reportId()), number(report))) throw missing();
            if (!definition.status().equals("PUBLISHED") || !pinned && !Objects.equals(definition.publishedVersion(), target.id())) throw conflict();
            requireActiveDatasetVersion(actor.tenantId(), target.datasetVersion());
            Instant now = now();
            if (expires == null || !expires.isAfter(now)) throw expired();
            String id = id();
            jdbc.update("INSERT INTO rpt_run(run_id,actor_id,report_id,version_id,mode,state,parameters_json,scope_hash,data_time,expires_at,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,'QUEUED',?,?,?,?,?,?,?)",
                number(id), actor.userId(), number(report), number(version), mode, payload, hash, Timestamp.from(now), Timestamp.from(expires), actor.tenantId(), actor.departmentId(), actor.userId());
            return run(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
        });
    }

    private static final String RUN_PARENTS = " FROM rpt_run r JOIN rpt_definition d ON d.report_id=r.report_id AND d.tenant_id=r.tenant_id"
        + " JOIN rpt_version v ON v.version_id=r.version_id AND v.report_id=r.report_id AND v.tenant_id=r.tenant_id";
    public Optional<Run> run(String tenant, String id) {
        return one("SELECT r.*" + RUN_PARENTS + " WHERE r.tenant_id=? AND r.run_id=?", RUN, tenant, number(id));
    }
    private Run lockRun(String tenant, String id) {
        return one("SELECT * FROM rpt_run WHERE tenant_id=? AND run_id=? FOR UPDATE", RUN, tenant, number(id)).orElseThrow(JdbcReportStore::missing);
    }
    public void runState(String tenant, String id, String state, String snapshot, String plan, long rows, String payload, String error) {
        transitionRun(tenant, id, state, snapshot, plan, rows, payload, error);
    }

    /** Conditional completion: a late worker cannot replace any terminal run state or its snapshot. */
    public boolean transitionRun(String tenant, String id, String state, String snapshot, String plan, long rows, String payload, String error) {
        if (state == null || !Set.of("RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED").contains(state) || rows < 0) {
            throw ReportFault.invalid("运行状态或行数无效");
        }
        return atomic(() -> {
            Run current = lockRun(tenant, id);
            if (run(tenant, id).isEmpty()) throw missing();
            if (state.equals("EXPIRED")) return expireRunLocked(current);
            if (!current.expires().isAfter(now())) return false;
            boolean valid = switch (state) {
                case "RUNNING" -> current.state().equals("QUEUED");
                case "SUCCEEDED" -> current.state().equals("RUNNING");
                default -> Set.of("QUEUED", "RUNNING").contains(current.state());
            };
            if (!valid) return false;
            return jdbc.update("UPDATE rpt_run SET state=?,snapshot_ref=?,plan_ref=?,row_count=?,parameters_json=?,error_code=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND run_id=? AND state=?",
                state, snapshot, plan, rows, payload, error, tenant, number(id), current.state()) == 1;
        });
    }

    private static final String ACTIVE_EXPORT = "EXISTS (SELECT 1 FROM rpt_export_job j WHERE j.tenant_id=r.tenant_id AND j.run_id=r.run_id"
        + " AND (j.state='QUEUED' OR j.state IN ('RUNNING','CANCEL_REQUESTED') AND j.lease_until>CURRENT_TIMESTAMP))";
    public List<Run> expiredRuns() {
        return jdbc.query("SELECT r.* FROM rpt_run r WHERE r.expires_at<=CURRENT_TIMESTAMP AND (r.state<>'EXPIRED' OR r.snapshot_ref IS NOT NULL OR r.plan_ref IS NOT NULL) AND NOT "
            + ACTIVE_EXPORT + " ORDER BY r.expires_at,r.run_id LIMIT 50", RUN);
    }

    /** Mark first, then delete the returned candidate's private artifacts only when this succeeds. */
    public boolean expireRun(Run run) {
        return atomic(() -> expireRunLocked(lockRun(run.tenant(), run.id())));
    }
    private boolean expireRunLocked(Run run) {
        return jdbc.update("UPDATE rpt_run r SET state='EXPIRED',update_time=CURRENT_TIMESTAMP"
            + " WHERE r.tenant_id=? AND r.run_id=? AND r.expires_at<=CURRENT_TIMESTAMP AND r.state<>'EXPIRED' AND NOT " + ACTIVE_EXPORT,
            run.tenant(), number(run.id())) == 1;
    }

    private static final String JOB_PARENTS = " FROM rpt_export_job j JOIN rpt_run r ON r.run_id=j.run_id AND r.tenant_id=j.tenant_id AND r.actor_id=j.actor_id"
        + " JOIN rpt_definition d ON d.report_id=r.report_id AND d.tenant_id=r.tenant_id"
        + " JOIN rpt_version v ON v.version_id=r.version_id AND v.report_id=r.report_id AND v.tenant_id=r.tenant_id";
    public Optional<ExportJob> job(String tenant, String id) {
        return one("SELECT j.*" + JOB_PARENTS + " WHERE j.tenant_id=? AND j.job_id=?", JOB, tenant, number(id));
    }
    public List<ExportJob> jobs(ReportActor actor) {
        return jdbc.query("SELECT j.*" + JOB_PARENTS + " WHERE j.tenant_id=? AND j.actor_id=? ORDER BY j.create_time DESC,j.job_id DESC LIMIT 200",
            JOB, actor.tenantId(), actor.userId());
    }

    public ExportJob createJob(ReportActor actor, String run, String format, String key, String hash, Instant expires) {
        return createJob(actor, run, format, key, hash, expires, false);
    }
    private ExportJob createJob(ReportActor actor, String run, String format, String key, String hash, Instant expires, boolean pendingSnapshot) {
        if (key == null || key.isBlank() || key.length() > 80 || hash == null || hash.isBlank() || hash.length() > 64
            || format == null || format.isBlank() || format.length() > 32) {
            throw ReportFault.invalid("导出请求标识或格式无效");
        }
        return atomic(() -> {
            // Admission is serialized across DIFFERENT runs/users on this tenant, never through sys_tenant.
            ensureRootCatalog(actor);
            var existing = one("SELECT j.*" + JOB_PARENTS + " WHERE j.tenant_id=? AND j.actor_id=? AND j.idempotency_key=? FOR UPDATE",
                JOB, actor.tenantId(), actor.userId(), key);
            if (existing.isPresent()) {
                ExportJob previous = existing.get();
                if (!previous.requestHash().equals(hash) || !previous.format().equals(format) || !Objects.equals(number(previous.runId()), number(run))) throw conflict();
                return previous;
            }
            Run source = lockRun(actor.tenantId(), run);
            if (source.actor() != actor.userId() || run(actor.tenantId(), run).isEmpty()) throw missing();
            if (!source.expires().isAfter(now())) throw expired();
            if (!source.mode().equals("SNAPSHOT") || !(source.state().equals("SUCCEEDED") && source.snapshot() != null
                || pendingSnapshot && Set.of("QUEUED", "RUNNING").contains(source.state()))) throw conflict();
            Definition definition = lockDefinition(actor, source.reportId());
            if (!definition.status().equals("PUBLISHED")) throw conflict();
            Version version = version(actor.tenantId(), source.version()).orElseThrow(JdbcReportStore::missing);
            requireActiveDatasetVersion(actor.tenantId(), version.datasetVersion());
            if (expires == null || !expires.isAfter(now())) throw expired();
            Instant boundedExpiry = expires.isBefore(source.expires()) ? expires : source.expires();
            Long userCount = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND actor_id=? AND state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                Long.class, actor.tenantId(), actor.userId());
            Long tenantCount = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')", Long.class, actor.tenantId());
            Long queued = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_export_job WHERE state='QUEUED'", Long.class);
            if (userCount != null && userCount >= 1 || tenantCount != null && tenantCount >= 2 || queued != null && queued >= 100) {
                throw new ReportFault("QUEUE_FULL", "导出队列已满，请稍后重试", 429);
            }
            String id = id();
            jdbc.update("INSERT INTO rpt_export_job(job_id,actor_id,run_id,format,idempotency_key,request_hash,state,expires_at,tenant_id,create_dept,create_by) VALUES(?,?,?,?,?,?,'QUEUED',?,?,?,?)",
                number(id), actor.userId(), number(run), format, key, hash, Timestamp.from(boundedExpiry), actor.tenantId(), actor.departmentId(), actor.userId());
            return job(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
        });
    }


    /** Lookup is independent of the bounded export-center list. */
    public Optional<ExportJob> jobByRequest(ReportActor actor, String key) {
        return one("SELECT j.*" + JOB_PARENTS + " WHERE j.tenant_id=? AND j.actor_id=? AND j.idempotency_key=?",
            JOB, actor.tenantId(), actor.userId(), key);
    }

    /** One tenant admission transaction creates a pinned LIVE clone and its request, or rolls both back. */
    public ExportJob createExportJob(ReportActor actor, String origin, String format, String key, String hash, Instant expires) {
        return atomic(() -> {
            ensureRootCatalog(actor);
            var prior = jobByRequest(actor, key);
            if (prior.isPresent()) {
                if (!prior.get().requestHash().equals(hash) || !prior.get().format().equals(format)) throw conflict();
                return prior.get();
            }
            Run source = lockRun(actor.tenantId(), origin);
            if (source.actor() != actor.userId() || run(actor.tenantId(), origin).isEmpty()) throw missing();
            if (!source.expires().isAfter(now())) throw expired();
            if (!source.state().equals("SUCCEEDED")) throw conflict();
            if (source.mode().equals("SNAPSHOT")) return createJob(actor, source.id(), format, key, hash, expires, false);
            if (!source.mode().equals("LIVE")) throw conflict();
            Instant boundedExpiry = expires != null && expires.isBefore(source.expires()) ? expires : source.expires();
            Run clone = createRun(actor, source.reportId(), source.version(), "SNAPSHOT", source.parameters(), source.scopeHash(), boundedExpiry, true);
            return createJob(actor, clone.id(), format, key, hash, boundedExpiry, true);
        });
    }

    /** A worker must match its acquired attempt, live lease, actor and uncancelled state. */
    public boolean leaseValid(ExportJob claimed) {
        return liveJob(claimed, false).isPresent();
    }
    private Optional<ExportJob> liveJob(ExportJob claimed, boolean lock) {
        if (claimed == null || claimed.leaseOwner() == null) return Optional.empty();
        return one("SELECT * FROM rpt_export_job WHERE tenant_id=? AND job_id=? AND actor_id=? AND run_id=? AND lease_owner=? AND attempts=?"
            + " AND state='RUNNING' AND cancel_requested=0 AND lease_until>CURRENT_TIMESTAMP AND expires_at>CURRENT_TIMESTAMP" + (lock ? " FOR UPDATE" : ""),
            JOB, claimed.tenant(), number(claimed.id()), claimed.actor(), number(claimed.runId()), claimed.leaseOwner(), claimed.attempts());
    }

    /** snapshot_ref temporarily stores an unguessable build checkpoint; its owner alone may finalize it. */
    public boolean beginSnapshot(String tenant, String runId, String checkpoint, ExportJob claimed) {
        if (checkpoint == null || !checkpoint.startsWith("build/") || checkpoint.length() > 256) throw ReportFault.invalid("快照构建标识无效");
        return atomic(() -> {
            Run run = lockRun(tenant, runId);
            if (!run.mode().equals("SNAPSHOT") || !run.expires().isAfter(now()) || run(tenant, runId).isEmpty()) return false;
            if (claimed != null && (!tenant.equals(claimed.tenant()) || !run.id().equals(claimed.runId()) || liveJob(claimed, true).isEmpty())) return false;
            if (run.state().equals("RUNNING")) {
                if (claimed == null || run.snapshot() == null || !run.snapshot().startsWith("build/job/")) return false;
                String[] previous = run.snapshot().split("/");
                if (previous.length != 5) return false;
                ExportJob prior = one("SELECT * FROM rpt_export_job WHERE tenant_id=? AND job_id=? AND run_id=? FOR UPDATE", JOB,
                    tenant, number(previous[2]), number(runId)).orElse(null);
                if (prior != null && Integer.toString(prior.attempts()).equals(previous[3])
                    && Set.of("RUNNING", "CANCEL_REQUESTED").contains(prior.state()) && prior.leaseUntil() != null && prior.leaseUntil().isAfter(now())) return false;
            } else if (!Set.of("QUEUED", "FAILED").contains(run.state())) return false;
            return jdbc.update("UPDATE rpt_run SET state='RUNNING',snapshot_ref=?,data_time=CURRENT_TIMESTAMP,error_code=NULL,update_time=CURRENT_TIMESTAMP"
                + " WHERE tenant_id=? AND run_id=?", checkpoint, tenant, number(runId)) == 1;
        });
    }

    /** Run terminal/checkpoint writes are atomic with the live job fence; stale attempts never change them. */
    public boolean finishSnapshot(String tenant, String runId, String checkpoint, ExportJob claimed, String state,
                                  String reference, long rows, String payload, String error) {
        if (state == null || !Set.of("SUCCEEDED", "FAILED", "QUEUED", "CANCELLED").contains(state) || rows < 0) throw ReportFault.invalid("快照状态无效");
        return atomic(() -> {
            Run run = lockRun(tenant, runId);
            if (!run.state().equals("RUNNING") || !Objects.equals(run.snapshot(), checkpoint) || !run.expires().isAfter(now())) return false;
            if (claimed != null && (!tenant.equals(claimed.tenant()) || !run.id().equals(claimed.runId()) || liveJob(claimed, true).isEmpty())) return false;
            return jdbc.update("UPDATE rpt_run SET state=?,snapshot_ref=?,row_count=?,parameters_json=?,error_code=?,update_time=CURRENT_TIMESTAMP"
                + " WHERE tenant_id=? AND run_id=? AND state='RUNNING' AND snapshot_ref=?",
                state, state.equals("SUCCEEDED") ? reference : null, rows, payload, safeError(error), tenant, number(runId), checkpoint) == 1;
        });
    }

    private static String safeError(String error) {
        if (error == null) return null;
        return Set.of("STORAGE_UNAVAILABLE", "ARTIFACT_STORE_UNAVAILABLE", "XLSX_EXPORT_FAILED", "PDF_EXPORT_FAILED", "REPORT_EXECUTION_FAILED", "SCOPE_DENIED", "SCOPE_CHANGED", "SNAPSHOT_EXPIRED", "SNAPSHOT_INVALID", "LEASE_LOST", "CANCELLED", "CAPACITY_EXCEEDED", "QUERY_TIMEOUT", "VERSION_UNSUPPORTED", "REPORT_OFFLINE")
            .contains(error) ? error : "REPORT_EXECUTION_FAILED";
    }

    private static final String VALID_JOB_RUN = "EXISTS (SELECT 1 FROM rpt_run r JOIN rpt_definition d ON d.report_id=r.report_id AND d.tenant_id=r.tenant_id"
        + " JOIN rpt_version v ON v.version_id=r.version_id AND v.report_id=r.report_id AND v.tenant_id=r.tenant_id"
        + " WHERE r.run_id=j.run_id AND r.tenant_id=j.tenant_id AND r.actor_id=j.actor_id AND r.state IN ('QUEUED','RUNNING','SUCCEEDED') AND r.mode='SNAPSHOT'"
        + " AND r.expires_at>CURRENT_TIMESTAMP AND d.status='PUBLISHED')";
    private static final String INACTIVE_LEASE = "(j.lease_until IS NULL OR j.lease_until<=CURRENT_TIMESTAMP)";
    private static final String CLAIMABLE = "j.expires_at>CURRENT_TIMESTAMP AND " + VALID_JOB_RUN
        + " AND ((j.state='QUEUED' AND j.cancel_requested=0 AND j.attempts<3)"
        // Cancelled reclaims only acquire a cleanup lease; no export runs after its third execution attempt.
        + " OR (j.state IN ('RUNNING','CANCEL_REQUESTED') AND " + INACTIVE_LEASE + " AND (j.attempts<3 OR j.cancel_requested=1)))";

    /** Scheduler-only cross-tenant maintenance/scan; each candidate is re-authorized before data access. */
    public List<ExportJob> claimableJobs() {
        reconcileJobs();
        return jdbc.query("SELECT j.* FROM rpt_export_job j WHERE " + CLAIMABLE + " ORDER BY j.create_time,j.job_id LIMIT 8", JOB);
    }

    /** Resolve dead leases; abandoned third attempts must not hold admission slots forever. */
    public int reconcileJobs() {
        return atomic(() -> {
            int changed = jdbc.update("UPDATE rpt_export_job j SET state='EXPIRED',lease_owner=NULL,lease_until=NULL,error_code='SNAPSHOT_EXPIRED',update_time=CURRENT_TIMESTAMP"
                + " WHERE j.state IN ('QUEUED','RUNNING','CANCEL_REQUESTED') AND (j.state='QUEUED' OR " + INACTIVE_LEASE + ")"
                + " AND (j.expires_at<=CURRENT_TIMESTAMP OR NOT " + VALID_JOB_RUN + ")");
            changed += jdbc.update("UPDATE rpt_export_job j SET state='FAILED',lease_owner=NULL,lease_until=NULL,error_code='RETRY_EXHAUSTED',update_time=CURRENT_TIMESTAMP"
                + " WHERE j.state IN ('QUEUED','RUNNING') AND j.cancel_requested=0 AND j.attempts>=3 AND " + INACTIVE_LEASE);
            return changed;
        });
    }

    public boolean claim(ExportJob job, String owner, Instant lease) { return claimJob(job, owner, lease).isPresent(); }

    /** Returns the attempt fence to use for heartbeat/finish; useful when a node reuses its owner string. */
    public Optional<ExportJob> claimJob(ExportJob job, String owner, Instant lease) {
        if (owner == null || owner.isBlank() || owner.length() > 100 || lease == null) throw ReportFault.invalid("导出任务租约无效");
        return atomic(() -> {
            if (!lease.isAfter(now())) return Optional.empty();
            int changed = jdbc.update("UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1 THEN 'CANCEL_REQUESTED' ELSE 'RUNNING' END,"
                + " attempts=attempts+1,lease_owner=?,lease_until=LEAST(?,expires_at),update_time=CURRENT_TIMESTAMP"
                + " WHERE j.tenant_id=? AND j.job_id=? AND j.attempts=? AND " + CLAIMABLE,
                owner, Timestamp.from(lease), job.tenant(), number(job.id()), job.attempts());
            return changed == 1 ? job(job.tenant(), job.id()) : Optional.empty();
        });
    }

    private static int attemptFence(ExportJob job, String owner) {
        // Legacy queued records are supported. Reclaimers that reuse owner must use claimJob's returned record.
        return job.state().equals("QUEUED") || !Objects.equals(job.leaseOwner(), owner)
            ? job.attempts() + 1 : job.attempts();
    }
    public boolean heartbeat(ExportJob job, String owner, Instant lease) {
        if (owner == null || owner.isBlank() || lease == null) return false;
        return jdbc.update("UPDATE rpt_export_job j SET lease_until=LEAST(?,expires_at),update_time=CURRENT_TIMESTAMP"
            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED')"
            + " AND j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP AND LEAST(?,j.expires_at)>=j.lease_until AND " + VALID_JOB_RUN,
            Timestamp.from(lease), job.tenant(), number(job.id()), owner, attemptFence(job, owner), Timestamp.from(lease)) == 1;
    }

    public boolean finishJob(ExportJob job, String owner, String state, String file, Long bytes, String error) {
        if (state == null || !Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(state)) throw ReportFault.invalid("导出终态无效");
        boolean success = state.equals("SUCCEEDED");
        if (success && (file == null || file.isBlank() || bytes == null || bytes < 0)) throw ReportFault.invalid("导出文件元数据无效");
        String guard = success ? " AND j.cancel_requested=0 AND j.state='RUNNING' AND " + VALID_JOB_RUN
            + " AND EXISTS (SELECT 1 FROM rpt_run r WHERE r.run_id=j.run_id AND r.tenant_id=j.tenant_id AND r.state='SUCCEEDED')" : "";
        return jdbc.update("UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1 THEN 'CANCELLED' ELSE ? END,"
            + " file_ref=?,file_size=?,error_code=?,lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED')"
            + " AND j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP" + guard,
            state, success ? file : null, success ? bytes : null, error, job.tenant(), number(job.id()), owner, attemptFence(job, owner)) == 1;
    }


    /** Retry only a live acquired attempt; never persist exception messages as error codes. */
    public boolean retryJob(ExportJob job, String owner, String error) {
        String code = Set.of("STORAGE_UNAVAILABLE", "ARTIFACT_STORE_UNAVAILABLE", "XLSX_EXPORT_FAILED", "PDF_EXPORT_FAILED", "REPORT_EXECUTION_FAILED")
            .contains(error == null ? "" : error) ? error : "REPORT_EXECUTION_FAILED";
        return jdbc.update("UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1 THEN 'CANCELLED' WHEN attempts<3 THEN 'QUEUED' ELSE 'FAILED' END,"
            + " file_ref=NULL,file_size=NULL,error_code=?,lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED')"
            + " AND j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP"
            + " AND EXISTS (SELECT 1 FROM rpt_run r WHERE r.run_id=j.run_id AND r.tenant_id=j.tenant_id AND r.actor_id=j.actor_id AND r.expires_at>CURRENT_TIMESTAMP)",
            code, job.tenant(), number(job.id()), owner, attemptFence(job, owner)) == 1;
    }

    public void cancel(ReportActor actor, String job) {
        jdbc.update("UPDATE rpt_export_job SET cancel_requested=1,state=CASE WHEN state='QUEUED' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED' END,update_time=CURRENT_TIMESTAMP"
            + " WHERE tenant_id=? AND actor_id=? AND job_id=? AND state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')",
            actor.tenantId(), actor.userId(), number(job));
    }
    public List<ExportJob> expiredJobs() {
        return jdbc.query("SELECT j.* FROM rpt_export_job j WHERE j.expires_at<=CURRENT_TIMESTAMP AND (j.state<>'EXPIRED' OR j.file_ref IS NOT NULL)"
            + " AND (j.state NOT IN ('RUNNING','CANCEL_REQUESTED') OR " + INACTIVE_LEASE + ") ORDER BY j.expires_at,j.job_id LIMIT 50", JOB);
    }
    public void expireJob(ExportJob job) { tryExpireJob(job); }

    /** Cleanup must delete private files only after this conditional transition succeeds. */
    public boolean tryExpireJob(ExportJob job) {
        return jdbc.update("UPDATE rpt_export_job j SET state='EXPIRED',lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
            + " WHERE j.tenant_id=? AND j.job_id=? AND j.expires_at<=CURRENT_TIMESTAMP AND j.state<>'EXPIRED'"
            + " AND (j.state NOT IN ('RUNNING','CANCEL_REQUESTED') OR " + INACTIVE_LEASE + ")", job.tenant(), number(job.id())) == 1;
    }


    /** Clear only the exact expired references whose physical deletion has succeeded. */
    public boolean ackJobCleanup(ExportJob expired) {
        return jdbc.update("UPDATE rpt_export_job SET file_ref=NULL,file_size=NULL,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND job_id=? AND state='EXPIRED'"
            + " AND (file_ref=? OR file_ref IS NULL AND ? IS NULL)", expired.tenant(), number(expired.id()), expired.file(), expired.file()) == 1;
    }
    public boolean ackRunCleanup(Run expired) {
        return jdbc.update("UPDATE rpt_run SET snapshot_ref=NULL,plan_ref=NULL,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND run_id=? AND state='EXPIRED'"
            + " AND (snapshot_ref=? OR snapshot_ref IS NULL AND ? IS NULL) AND (plan_ref=? OR plan_ref IS NULL AND ? IS NULL)",
            expired.tenant(), number(expired.id()), expired.snapshot(), expired.snapshot(), expired.plan(), expired.plan()) == 1;
    }

    /** Includes expired-but-not-yet-acknowledged references so cleanup failures remain retryable. */
    public boolean artifactReferenced(String tenant,String reference){
        if(reference==null)return false;
        return !jdbc.queryForList("SELECT run_id FROM rpt_run WHERE tenant_id=? AND (snapshot_ref=? OR plan_ref=?) LIMIT 1",tenant,reference,reference).isEmpty()
            || !jdbc.queryForList("SELECT job_id FROM rpt_export_job WHERE tenant_id=? AND file_ref=? LIMIT 1",tenant,reference).isEmpty();
    }
    private static ReportFault expired() { return new ReportFault("SNAPSHOT_EXPIRED", "报表运行或导出任务已到期", 410); }
    private static ReportFault missing() { return new ReportFault("NOT_FOUND", "报表资源不存在或不可访问", 404); }
    private static ReportFault conflict() { return new ReportFault("REVISION_CONFLICT", "内容或请求已变化，请重新读取后操作", 409); }
}
