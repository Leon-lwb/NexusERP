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

/** 元数据始终走系统主库，所有公开资源读取和修改均绑定租户。 */
@Repository
@DS("master")
public class JdbcReportStore {
    /** 持久队列准入上限，旧夹具仍沿用首期保守值。 */
    private int queueUser = 1, queueTenant = 2, queueGlobal = 100;

    /** 保存当前fairDispatch状态或配置。 */
    private boolean fairDispatch;

    /** 保存当前executingGlobal状态或配置。 */
    private int executingGlobal = 8, executingTenant = 2, executingUser = 1;

    /**
     * 扩展结构已安装后启用跨节点调度，执行并发与等待队列分开配置。
     *
     * @param enabled 当前契约的enabled配置或状态
     * @param global 当前契约的global配置或状态
     * @param tenant 租户隔离标识
     * @param user 当前契约的user配置或状态
     */
    public void fairDispatch(boolean enabled, int global, int tenant, int user) {
        if (user < 1 || tenant < user || global < tenant || global > 32)
            throw new IllegalArgumentException("执行并发配置无效");
        fairDispatch = enabled;
        executingGlobal = global;
        executingTenant = tenant;
        executingUser = user;
    }

    /**
     * 装配期设置容量，任务执行并发仍由租约领取单独控制。
     *
     * @param user 当前契约的user配置或状态
     * @param tenant 租户隔离标识
     * @param global 当前契约的global配置或状态
     */
    public void admissionLimits(int user, int tenant, int global) {
        if (user < 1
                || user > 100
                || tenant < user
                || tenant > 500
                || global < tenant
                || global > 5000) throw new IllegalArgumentException("报表队列容量配置无效");
        queueUser = user;
        queueTenant = tenant;
        queueGlobal = global;
    }

    /** 保存当前 {@code ROOTNAME} 值。 */
    private static final String ROOT_NAME = "报表中心";

    /** 保存当前 {@code ACLACTIONS} 值。 */
    private static final Set<String> ACL_ACTIONS =
            Set.of("VIEW", "EDIT", "PUBLISH", "RUN", "EXPORT", "ACL", "MANAGE");

    /** 保存当前 {@code jdbc} 值。 */
    private final JdbcTemplate jdbc;

    /** 保存当前 {@code transaction} 值。 */
    private final TransactionTemplate transaction;

    /**
     * 初始化只读取数报表存储并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     * @param json 待处理定义文本
     */
    public JdbcReportStore(JdbcTemplate jdbc, ObjectMapper json) {
        var source = Objects.requireNonNull(jdbc.getDataSource());
        this.jdbc = new JdbcTemplate(source);
        this.jdbc.setQueryTimeout(10);
        var manager = new DataSourceTransactionManager(source);
        manager.setValidateExistingTransaction(true);
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(10);
        /** 等待租户互斥锁的事务必须读取前序事务已提交的计数与版本指针，兼容 MySQL 5.7 和 H2。 */
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * 完成 {@code id} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static String id() {
        return Long.toString(IdWorker.getId());
    }

    /**
     * 完成 {@code number} 对应的只读取数报表存储处理。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
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

    /**
     * 完成 {@code textId} 对应的只读取数报表存储处理。
     *
     * @param rs 当前操作的 {@code rs} 输入
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    private static String textId(ResultSet rs, String name) throws SQLException {
        Object value = rs.getObject(name);
        return value == null ? null : value.toString();
    }

    /**
     * 完成 {@code time} 对应的只读取数报表存储处理。
     *
     * @param rs 当前操作的 {@code rs} 输入
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    private static Instant time(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    /**
     * 完成 {@code one} 对应的只读取数报表存储处理。
     *
     * @param sql 代码生成的查询语句
     * @param mapper 当前操作的 {@code mapper} 输入
     * @param params 当前操作的 {@code params} 输入
     * @return 当前处理返回的固定结果
     */
    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = jdbc.query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 完成 {@code atomic} 对应的只读取数报表存储处理。
     *
     * @param action 资源操作
     * @return 当前处理返回的固定结果
     */
    private <T> T atomic(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    /**
     * 完成 {@code now} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private Instant now() {
        return Objects.requireNonNull(
                        jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class))
                .toInstant();
    }

    /** 保存当前 {@code CATALOG} 值。 */
    private static final RowMapper<Catalog> CATALOG =
            (r, i) ->
                    new Catalog(
                            textId(r, "catalog_id"),
                            textId(r, "parent_id"),
                            r.getString("name"),
                            r.getLong("create_by"));

    /** 保存当前 {@code DEFINITION} 值。 */
    private static final RowMapper<Definition> DEFINITION =
            (r, i) ->
                    new Definition(
                            textId(r, "report_id"),
                            r.getString("tenant_id"),
                            r.getString("code"),
                            r.getString("name"),
                            r.getString("kind"),
                            textId(r, "catalog_id"),
                            r.getString("draft_json"),
                            r.getLong("draft_revision"),
                            textId(r, "published_version_id"),
                            r.getString("status"),
                            r.getLong("create_by"),
                            time(r, "update_time"));

    /** 保存当前 {@code VERSION} 值。 */
    private static final RowMapper<Version> VERSION =
            (r, i) ->
                    new Version(
                            textId(r, "version_id"),
                            textId(r, "report_id"),
                            r.getInt("version_no"),
                            r.getInt("schema_version"),
                            r.getString("engine_version"),
                            r.getString("dsl_json"),
                            r.getString("checksum"),
                            textId(r, "dataset_version_id"),
                            r.getLong("create_by"),
                            time(r, "create_time"));

    /** 保存当前 {@code DATASET} 值。 */
    private static final RowMapper<Dataset> DATASET =
            (r, i) ->
                    new Dataset(
                            textId(r, "dataset_id"),
                            r.getString("tenant_id"),
                            r.getString("code"),
                            r.getString("name"),
                            r.getString("provider_code"),
                            textId(r, "published_version_id"),
                            r.getString("status"),
                            r.getLong("create_by"));

    /** 保存当前 {@code DATASETVERSION} 值。 */
    private static final RowMapper<DatasetVersion> DATASET_VERSION =
            (r, i) ->
                    new DatasetVersion(
                            textId(r, "version_id"),
                            textId(r, "dataset_id"),
                            r.getInt("version_no"),
                            r.getString("provider_code"),
                            r.getInt("provider_version"),
                            r.getString("descriptor_json"),
                            r.getString("checksum"),
                            r.getLong("create_by"));

    /** 保存当前 {@code RUN} 值。 */
    private static final RowMapper<Run> RUN =
            (r, i) ->
                    new Run(
                            textId(r, "run_id"),
                            r.getString("tenant_id"),
                            r.getLong("actor_id"),
                            textId(r, "report_id"),
                            textId(r, "version_id"),
                            r.getString("mode"),
                            r.getString("state"),
                            r.getString("parameters_json"),
                            r.getString("scope_hash"),
                            r.getString("snapshot_ref"),
                            r.getString("plan_ref"),
                            r.getLong("row_count"),
                            time(r, "data_time"),
                            time(r, "expires_at"),
                            r.getString("error_code"));

    /** 保存当前 {@code JOB} 值。 */
    private static final RowMapper<ExportJob> JOB =
            (r, i) ->
                    new ExportJob(
                            textId(r, "job_id"),
                            r.getString("tenant_id"),
                            r.getLong("actor_id"),
                            textId(r, "run_id"),
                            r.getString("format"),
                            r.getString("idempotency_key"),
                            r.getString("request_hash"),
                            r.getString("state"),
                            r.getInt("attempts"),
                            r.getString("lease_owner"),
                            time(r, "lease_until"),
                            r.getBoolean("cancel_requested"),
                            r.getString("file_ref"),
                            (Long) r.getObject("file_size"),
                            time(r, "expires_at"),
                            r.getString("error_code"),
                            r.getString("execution_phase"),
                            r.getLong("processed_rows"),
                            (Long) r.getObject("total_rows"),
                            r.getInt("retry_round"),
                            time(r, "last_heartbeat"));

    /**
     * 完成 {@code catalogs} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @return 当前处理返回的固定结果
     */
    public List<Catalog> catalogs(String tenant) {
        return jdbc.query(
                "SELECT catalog_id,parent_id,name,create_by FROM rpt_catalog WHERE tenant_id=?"
                        + " ORDER BY sort_order,catalog_id",
                CATALOG,
                tenant);
    }

    /**
     * 规范化且不可变的根目录，同时作为报表租户任务准入的互斥记录。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public Catalog ensureRootCatalog(ReportActor actor) {
        return atomic(
                () -> {
                    String lookup =
                            "SELECT catalog_id,parent_id,name,create_by FROM rpt_catalog WHERE"
                                    + " tenant_id=? AND parent_id=0 AND name=?";
                    if (one(lookup, CATALOG, actor.tenantId(), ROOT_NAME).isEmpty()) {
                        try {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " rpt_catalog(catalog_id,parent_id,name,tenant_id,create_dept,create_by)"
                                        + " VALUES(?,0,?,?,?,?)",
                                    number(id()),
                                    ROOT_NAME,
                                    actor.tenantId(),
                                    actor.departmentId(),
                                    actor.userId());
                        } catch (DuplicateKeyException duplicate) {
                            /** 其他节点已插入同租户、父目录与名称的唯一记录，在锁内读取规范记录。 */
                        }
                    }
                    return one(lookup + " FOR UPDATE", CATALOG, actor.tenantId(), ROOT_NAME)
                            .orElseThrow(JdbcReportStore::conflict);
                });
    }

    /**
     * 完成 {@code createCatalog} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param parent 当前操作的 {@code parent} 输入
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    public Catalog createCatalog(ReportActor actor, String parent, String name) {
        if (name == null || name.isBlank() || name.length() > 100)
            throw ReportFault.invalid("目录名称无效");
        long parentId = parent == null || parent.equals("0") ? 0 : number(parent);
        if (parentId == 0 && ROOT_NAME.equals(name)) return ensureRootCatalog(actor);
        return atomic(
                () -> {
                    if (parentId != 0
                            && one(
                                            "SELECT catalog_id FROM rpt_catalog WHERE tenant_id=?"
                                                    + " AND catalog_id=? FOR UPDATE",
                                            (r, i) -> r.getLong(1),
                                            actor.tenantId(),
                                            parentId)
                                    .isEmpty()) throw missing();
                    String id = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_catalog(catalog_id,parent_id,name,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,?)",
                            number(id),
                            parentId,
                            name,
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    return new Catalog(id, Long.toString(parentId), name, actor.userId());
                });
    }

    /**
     * 列出当前身份可见的报表定义。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Definition> definitions(ReportActor actor) {
        return jdbc
                .query(
                        "SELECT * FROM rpt_definition WHERE tenant_id=? AND status<>'ARCHIVED'"
                                + " ORDER BY update_time DESC,report_id DESC LIMIT 10000",
                        DEFINITION,
                        actor.tenantId())
                .stream()
                .filter(d -> allowed(actor, "REPORT", d.id(), d.owner(), "VIEW"))
                .toList();
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Optional<Definition> definition(String tenant, String id) {
        return one(
                "SELECT * FROM rpt_definition WHERE tenant_id=? AND report_id=?",
                DEFINITION,
                tenant,
                number(id));
    }

    /**
     * 完成 {@code definitionByCode} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public Optional<Definition> definitionByCode(String tenant, String code) {
        return one(
                "SELECT * FROM rpt_definition WHERE tenant_id=? AND code=?",
                DEFINITION,
                tenant,
                code);
    }

    /**
     * 完成 {@code lockDefinition} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @return 当前处理返回的固定结果
     */
    private Definition lockDefinition(ReportActor actor, String report) {
        return one(
                        "SELECT * FROM rpt_definition WHERE tenant_id=? AND report_id=? FOR UPDATE",
                        DEFINITION,
                        actor.tenantId(),
                        number(report))
                .orElseThrow(JdbcReportStore::missing);
    }

    /**
     * 完成 {@code editable} 对应的只读取数报表存储处理。
     *
     * @param definition 当前操作的 {@code definition} 输入
     * @param revision 预期草稿修订
     */
    private static void editable(Definition definition, Long revision) {
        if (definition.status().equals("ARCHIVED")
                || revision != null && definition.revision() != revision) throw conflict();
    }

    /**
     * 完成 {@code createDefinition} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param code 业务编码
     * @param name 当前操作的 {@code name} 输入
     * @param kind 受支持类型
     * @param catalog 当前操作的 {@code catalog} 输入
     * @param draft 当前操作的 {@code draft} 输入
     * @return 当前处理返回的固定结果
     */
    public Definition createDefinition(
            ReportActor actor,
            String code,
            String name,
            String kind,
            String catalog,
            String draft) {
        return atomic(
                () -> {
                    if (catalog != null
                            && one(
                                            "SELECT catalog_id FROM rpt_catalog WHERE tenant_id=?"
                                                    + " AND catalog_id=? FOR UPDATE",
                                            (r, i) -> r.getLong(1),
                                            actor.tenantId(),
                                            number(catalog))
                                    .isEmpty()) throw missing();
                    String id = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_definition(report_id,catalog_id,code,name,kind,draft_json,draft_revision,status,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,?,1,'DRAFT',?,?,?)",
                            number(id),
                            number(catalog),
                            code,
                            name,
                            kind,
                            draft,
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    return definition(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                });
    }

    /**
     * 按预期修订保存草稿。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param revision 预期草稿修订
     * @param name 当前操作的 {@code name} 输入
     * @param draft 当前操作的 {@code draft} 输入
     * @return 当前处理返回的固定结果
     */
    public Definition saveDraft(
            ReportActor actor, String id, long revision, String name, String draft) {
        return atomic(
                () -> {
                    int changed =
                            jdbc.update(
                                    "UPDATE rpt_definition SET"
                                        + " draft_json=?,name=?,draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND report_id=? AND draft_revision=?"
                                        + " AND status<>'ARCHIVED'",
                                    draft,
                                    name,
                                    actor.userId(),
                                    actor.tenantId(),
                                    number(id),
                                    revision);
                    if (changed != 1) throw conflict();
                    return definition(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                });
    }

    /**
     * 在修订与授权检查后创建不可变发布版本。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param revision 预期草稿修订
     * @param dsl 报表定义
     * @param checksum 预期完整性校验和
     * @param datasetVersion 当前操作的 {@code datasetVersion} 输入
     * @return 当前处理返回的固定结果
     */
    public Version publish(
            ReportActor actor,
            String report,
            long revision,
            String dsl,
            String checksum,
            String datasetVersion) {
        return publish(actor, report, revision, dsl, checksum, datasetVersion, 1, "1.0.0");
    }

    /**
     * 在修订与授权检查后创建不可变发布版本。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param revision 预期草稿修订
     * @param dsl 报表定义
     * @param checksum 预期完整性校验和
     * @param datasetVersion 当前操作的 {@code datasetVersion} 输入
     * @param schema 定义格式版本
     * @param engine 执行器版本
     * @return 当前处理返回的固定结果
     */
    public Version publish(
            ReportActor actor,
            String report,
            long revision,
            String dsl,
            String checksum,
            String datasetVersion,
            int schema,
            String engine) {
        if (schema < 1 || engine == null || !engine.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw ReportFault.invalid("报表引擎版本无效");
        return atomic(
                () -> {
                    Definition definition = lockDefinition(actor, report);
                    editable(definition, revision);
                    var target = requireActiveDatasetVersion(actor.tenantId(), datasetVersion);
                    if (!allowed(actor, "DATASET", target.datasetId(), 0, "VIEW")
                            || !allowed(actor, "REPORT", report, definition.owner(), "PUBLISH"))
                        throw ReportFault.denied();
                    Integer previous =
                            jdbc.queryForObject(
                                    "SELECT COALESCE(MAX(version_no),0) FROM rpt_version WHERE"
                                            + " tenant_id=? AND report_id=?",
                                    Integer.class,
                                    actor.tenantId(),
                                    number(report));
                    int next = (previous == null ? 0 : previous) + 1;
                    if (next > 1000) throw ReportFault.capacity();
                    String id = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_version(version_id,report_id,version_no,schema_version,engine_version,dsl_json,checksum,dataset_version_id,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                            number(id),
                            number(report),
                            next,
                            schema,
                            engine,
                            dsl,
                            checksum,
                            number(datasetVersion),
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    jdbc.update(
                            "UPDATE rpt_definition SET"
                                + " published_version_id=?,status='PUBLISHED',draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND report_id=?",
                            number(id),
                            actor.userId(),
                            actor.tenantId(),
                            number(report));
                    return version(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                });
    }

    /**
     * 完成 {@code version} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Optional<Version> version(String tenant, String id) {
        return one(
                "SELECT v.* FROM rpt_version v JOIN rpt_definition d ON d.report_id=v.report_id AND"
                        + " d.tenant_id=v.tenant_id WHERE v.tenant_id=? AND v.version_id=?",
                VERSION,
                tenant,
                number(id));
    }

    /**
     * 完成 {@code versions} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param report 已编译报表
     * @return 当前处理返回的固定结果
     */
    public List<Version> versions(String tenant, String report) {
        return jdbc.query(
                "SELECT v.* FROM rpt_version v JOIN rpt_definition d ON d.report_id=v.report_id AND"
                        + " d.tenant_id=v.tenant_id WHERE v.tenant_id=? AND v.report_id=? ORDER BY"
                        + " v.version_no DESC",
                VERSION,
                tenant,
                number(report));
    }

    /**
     * 完成 {@code rollback} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param version 固定版本
     */
    public void rollback(ReportActor actor, String report, String version) {
        rollback(actor, report, version, null);
    }

    /**
     * 完成 {@code rollback} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param version 固定版本
     * @param revision 预期草稿修订
     */
    public void rollback(ReportActor actor, String report, String version, long revision) {
        rollback(actor, report, version, Long.valueOf(revision));
    }

    /**
     * 完成 {@code rollback} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param version 固定版本
     * @param revision 预期草稿修订
     */
    private void rollback(ReportActor actor, String report, String version, Long revision) {
        atomic(
                () -> {
                    Definition definition = lockDefinition(actor, report);
                    editable(definition, revision);
                    Version target =
                            version(actor.tenantId(), version)
                                    .orElseThrow(JdbcReportStore::missing);
                    if (!Objects.equals(number(target.reportId()), number(report))) throw missing();
                    requireActiveDatasetVersion(actor.tenantId(), target.datasetVersion());
                    jdbc.update(
                            "UPDATE rpt_definition SET"
                                + " published_version_id=?,status='PUBLISHED',draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND report_id=?",
                            number(version),
                            actor.userId(),
                            actor.tenantId(),
                            number(report));
                    return null;
                });
    }

    /**
     * 完成 {@code definitionState} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     */
    public void definitionState(ReportActor actor, String id, String state) {
        definitionState(actor, id, state, null);
    }

    /**
     * 完成 {@code definitionState} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     * @param revision 预期草稿修订
     */
    public void definitionState(ReportActor actor, String id, String state, long revision) {
        definitionState(actor, id, state, Long.valueOf(revision));
    }

    /**
     * 完成 {@code definitionState} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     * @param revision 预期草稿修订
     */
    private void definitionState(ReportActor actor, String id, String state, Long revision) {
        if (state == null || !Set.of("OFFLINE", "ARCHIVED").contains(state))
            throw ReportFault.invalid("报表状态无效");
        atomic(
                () -> {
                    Definition definition = lockDefinition(actor, id);
                    if (revision != null && definition.revision() != revision) throw conflict();
                    if (definition.status().equals(state)) return null;
                    editable(definition, revision);
                    jdbc.update(
                            "UPDATE rpt_definition SET"
                                + " status=?,draft_revision=draft_revision+1,update_by=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND report_id=?",
                            state,
                            actor.userId(),
                            actor.tenantId(),
                            number(id));
                    return null;
                });
    }

    /**
     * 列出当前身份可见的数据集。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Dataset> datasets(ReportActor actor) {
        return jdbc
                .query(
                        "SELECT * FROM rpt_dataset WHERE tenant_id=? AND status<>'ARCHIVED' ORDER"
                                + " BY name,dataset_id",
                        DATASET,
                        actor.tenantId())
                .stream()
                .filter(d -> allowed(actor, "DATASET", d.id(), d.owner(), "VIEW"))
                .toList();
    }

    /**
     * 完成 {@code dataset} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Optional<Dataset> dataset(String tenant, String id) {
        return one(
                "SELECT * FROM rpt_dataset WHERE tenant_id=? AND dataset_id=?",
                DATASET,
                tenant,
                number(id));
    }

    /**
     * 完成 {@code datasetByCode} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public Optional<Dataset> datasetByCode(String tenant, String code) {
        return one("SELECT * FROM rpt_dataset WHERE tenant_id=? AND code=?", DATASET, tenant, code);
    }

    /**
     * 登记已安装的代码契约版本。
     *
     * @param actor 当前服务端身份
     * @param descriptor 可信数据集契约
     * @param json 待处理定义文本
     * @param checksum 预期完整性校验和
     * @return 当前处理返回的固定结果
     */
    public Dataset registerDataset(
            ReportActor actor, DatasetDescriptor descriptor, String json, String checksum) {
        return atomic(
                () -> {
                    ensureRootCatalog(actor);
                    /** 读取版本指针及校验和前获取锁，包含其他节点首次注册的场景。 */
                    Dataset existing =
                            one(
                                            "SELECT * FROM rpt_dataset WHERE tenant_id=? AND code=?"
                                                    + " FOR UPDATE",
                                            DATASET,
                                            actor.tenantId(),
                                            descriptor.code())
                                    .orElse(null);
                    String datasetId = existing == null ? id() : existing.id();
                    if (existing == null) {
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_dataset(dataset_id,code,name,provider_code,status,tenant_id,create_dept,create_by)"
                                    + " VALUES(?,?,?,?,'ACTIVE',?,?,?)",
                                number(datasetId),
                                descriptor.code(),
                                descriptor.name(),
                                descriptor.code(),
                                actor.tenantId(),
                                actor.departmentId(),
                                actor.userId());
                    } else {
                        if (existing.publishedVersion() != null) {
                            DatasetVersion published =
                                    datasetVersion(actor.tenantId(), existing.publishedVersion())
                                            .orElseThrow(JdbcReportStore::missing);
                            if (!Objects.equals(published.datasetId(), existing.id()))
                                throw missing();
                            if (published.checksum().equals(checksum)) return existing;
                        }
                        if (existing.status().equals("ARCHIVED")) throw conflict();
                    }
                    Integer prior =
                            jdbc.queryForObject(
                                    "SELECT COALESCE(MAX(version_no),0) FROM rpt_dataset_version"
                                            + " WHERE tenant_id=? AND dataset_id=?",
                                    Integer.class,
                                    actor.tenantId(),
                                    number(datasetId));
                    int version = (prior == null ? 0 : prior) + 1;
                    if (version > 1000) throw ReportFault.capacity();
                    String versionId = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_dataset_version(version_id,dataset_id,version_no,provider_code,provider_version,descriptor_json,checksum,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                            number(versionId),
                            number(datasetId),
                            version,
                            descriptor.code(),
                            descriptor.version(),
                            json,
                            checksum,
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    jdbc.update(
                            "UPDATE rpt_dataset SET"
                                + " published_version_id=?,name=?,update_by=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND dataset_id=?",
                            number(versionId),
                            descriptor.name(),
                            actor.userId(),
                            actor.tenantId(),
                            number(datasetId));
                    return dataset(actor.tenantId(), datasetId)
                            .orElseThrow(JdbcReportStore::missing);
                });
    }

    /**
     * 读取当前租户的固定数据集契约。
     *
     * @param tenant 当前租户标识
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    public Optional<DatasetVersion> datasetVersion(String tenant, String version) {
        return one(
                "SELECT v.* FROM rpt_dataset_version v JOIN rpt_dataset d ON"
                    + " d.dataset_id=v.dataset_id AND d.tenant_id=v.tenant_id WHERE v.tenant_id=?"
                    + " AND v.version_id=?",
                DATASET_VERSION,
                tenant,
                number(version));
    }

    /**
     * 完成 {@code requireActiveDatasetVersion} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    private DatasetVersion requireActiveDatasetVersion(String tenant, String version) {
        DatasetVersion target =
                datasetVersion(tenant, version).orElseThrow(JdbcReportStore::missing);
        Dataset parent =
                one(
                                "SELECT * FROM rpt_dataset WHERE tenant_id=? AND dataset_id=? FOR"
                                        + " UPDATE",
                                DATASET,
                                tenant,
                                number(target.datasetId()))
                        .orElseThrow(JdbcReportStore::missing);
        if (!parent.status().equals("ACTIVE")) throw conflict();
        return target;
    }

    /**
     * 完成 {@code datasetState} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param dataset 当前操作的 {@code dataset} 输入
     * @param status 当前操作的 {@code status} 输入
     */
    public void datasetState(ReportActor actor, String dataset, String status) {
        datasetState(actor, dataset, status, null);
    }

    /**
     * 完成 {@code datasetState} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param dataset 当前操作的 {@code dataset} 输入
     * @param status 当前操作的 {@code status} 输入
     * @param expectedStatus 当前操作的 {@code expectedStatus} 输入
     */
    public void datasetState(
            ReportActor actor, String dataset, String status, String expectedStatus) {
        if (status == null || !Set.of("ACTIVE", "OFFLINE", "ARCHIVED").contains(status))
            throw ReportFault.invalid("数据集状态无效");
        atomic(
                () -> {
                    Dataset current =
                            one(
                                            "SELECT * FROM rpt_dataset WHERE tenant_id=? AND"
                                                    + " dataset_id=? FOR UPDATE",
                                            DATASET,
                                            actor.tenantId(),
                                            number(dataset))
                                    .orElseThrow(JdbcReportStore::missing);
                    if (expectedStatus != null && !current.status().equals(expectedStatus))
                        throw conflict();
                    if (current.status().equals(status)) return null;
                    if (current.status().equals("ARCHIVED")) throw conflict();
                    if (status.equals("ACTIVE")) {
                        DatasetVersion head =
                                datasetVersion(actor.tenantId(), current.publishedVersion())
                                        .orElseThrow(JdbcReportStore::missing);
                        if (!head.datasetId().equals(current.id())) throw missing();
                    }
                    jdbc.update(
                            "UPDATE rpt_dataset SET"
                                    + " status=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE"
                                    + " tenant_id=? AND dataset_id=?",
                            status,
                            actor.userId(),
                            actor.tenantId(),
                            number(dataset));
                    return null;
                });
    }

    /**
     * 完成 {@code table} 对应的只读取数报表存储处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @return 当前处理返回的固定结果
     */
    private static String table(String type) {
        if (type == null) throw ReportFault.invalid("资源类型无效");
        return switch (type) {
            case "REPORT" -> "rpt_definition";
            case "DATASET" -> "rpt_dataset";
            case "QUERY" -> "rpt_query_definition";
            case "DASHBOARD" -> "rpt_dashboard";
            case "SUBSCRIPTION" -> "rpt_subscription";
            case "ASSET" -> "rpt_asset";
            case "BATCH" -> "rpt_batch";
            default -> throw ReportFault.invalid("资源类型无效");
        };
    }

    /**
     * 完成 {@code primary} 对应的只读取数报表存储处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @return 当前处理返回的固定结果
     */
    private static String primary(String type) {
        table(type);
        return switch (type) {
            case "REPORT" -> "report_id";
            case "DATASET" -> "dataset_id";
            case "QUERY" -> "query_id";
            case "DASHBOARD" -> "dashboard_id";
            case "SUBSCRIPTION" -> "subscription_id";
            case "ASSET" -> "asset_id";
            case "BATCH" -> "batch_id";
            default -> throw ReportFault.invalid("资源类型无效");
        };
    }

    /**
     * 扩展资源的管理入口，复用租户化资源ACL而不暴露定义正文。
     *
     * @param actor 执行用户
     * @param type 受控类型
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public boolean managedResource(ReportActor actor, String type, String id) {
        String table = table(type);
        var state =
                one(
                        "SELECT status FROM "
                                + table
                                + " WHERE tenant_id=? AND "
                                + primary(type)
                                + "=?",
                        (r, i) -> r.getString(1),
                        actor.tenantId(),
                        number(id));
        return state.isPresent()
                && !state.get().equals("ARCHIVED")
                && allowed(actor, type, id, 0, "MANAGE");
    }

    /**
     * 判断当前身份是否满足资源授权。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param resource 当前操作的 {@code resource} 输入
     * @param ignoredOwner 当前操作的 {@code ignoredOwner} 输入
     * @param action 资源操作
     * @return 当前检查或条件操作是否成功
     */
    public boolean allowed(
            ReportActor actor, String type, String resource, long ignoredOwner, String action) {
        String table = table(type);
        if (action == null || !ACL_ACTIONS.contains(action)) return false;
        var owner =
                one(
                        "SELECT create_by FROM "
                                + table
                                + " WHERE tenant_id=? AND "
                                + primary(type)
                                + "=?",
                        (r, i) -> r.getLong(1),
                        actor.tenantId(),
                        number(resource));
        if (owner.isEmpty()) return false;
        if (actor.superAdmin() || owner.get() == actor.userId()) return true;
        return granted(actor, type, resource, action);
    }

    /**
     * 完成 {@code granted} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param resource 当前操作的 {@code resource} 输入
     * @param action 资源操作
     * @return 当前检查或条件操作是否成功
     */
    private boolean granted(ReportActor actor, String type, String resource, String action) {
        return grants(actor.tenantId(), type, resource).stream()
                .anyMatch(
                        grant ->
                                grant.action().equals(action)
                                        && (grant.subjectType().equals("USER")
                                                        && grant.subjectId()
                                                                .equals(
                                                                        Long.toString(
                                                                                actor.userId()))
                                                || grant.subjectType().equals("ROLE")
                                                        && actor.roleIds()
                                                                .contains(number(grant.subjectId()))
                                                || grant.subjectType().equals("DEPT")
                                                        && grant.subjectId()
                                                                .equals(
                                                                        Long.toString(
                                                                                actor
                                                                                        .departmentId()))));
    }

    /**
     * 读取当前租户的资源授权。
     *
     * @param tenant 当前租户标识
     * @param type 当前操作的 {@code type} 输入
     * @param resource 当前操作的 {@code resource} 输入
     * @return 当前处理返回的固定结果
     */
    private List<Grant> grants(String tenant, String type, String resource) {
        return jdbc.query(
                "SELECT subject_type,subject_id,action FROM rpt_acl WHERE tenant_id=? AND"
                    + " resource_type=? AND resource_id=? ORDER BY subject_type,subject_id,action",
                (r, i) -> new Grant(r.getString(1), r.getString(2), r.getString(3)),
                tenant,
                type,
                number(resource));
    }

    /**
     * 完成 {@code acl} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param type 当前操作的 {@code type} 输入
     * @param resource 当前操作的 {@code resource} 输入
     * @return 当前处理返回的固定结果
     */
    public List<Grant> acl(String tenant, String type, String resource) {
        if (one(
                        "SELECT create_by FROM "
                                + table(type)
                                + " WHERE tenant_id=? AND "
                                + primary(type)
                                + "=?",
                        (r, i) -> r.getLong(1),
                        tenant,
                        number(resource))
                .isEmpty()) throw missing();
        return grants(tenant, type, resource);
    }

    /**
     * 校验授权主体后原子替换资源授权。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param resource 当前操作的 {@code resource} 输入
     * @param grants 当前操作的 {@code grants} 输入
     */
    public void replaceAcl(ReportActor actor, String type, String resource, List<Grant> grants) {
        table(type);
        if (grants == null) throw ReportFault.invalid("授权列表不能为空");
        if (grants.size() > 1000) throw ReportFault.capacity();
        Set<Grant> unique = new LinkedHashSet<>();
        for (Grant grant : grants) {
            if (grant == null
                    || grant.subjectType() == null
                    || grant.action() == null
                    || grant.subjectId() == null
                    || !Set.of("USER", "ROLE", "DEPT").contains(grant.subjectType())
                    || !ACL_ACTIONS.contains(grant.action())) {
                throw ReportFault.invalid("授权主体或操作无效");
            }
            unique.add(
                    new Grant(
                            grant.subjectType(),
                            Long.toString(number(grant.subjectId())),
                            grant.action()));
        }
        atomic(
                () -> {
                    if (one(
                                    "SELECT status FROM "
                                            + table(type)
                                            + " WHERE tenant_id=? AND "
                                            + primary(type)
                                            + "=? FOR UPDATE",
                                    (r, i) -> r.getString(1),
                                    actor.tenantId(),
                                    number(resource))
                            .orElseThrow(JdbcReportStore::missing)
                            .equals("ARCHIVED")) throw ReportFault.denied();
                    long owner =
                            one(
                                            "SELECT create_by FROM "
                                                    + table(type)
                                                    + " WHERE tenant_id=? AND "
                                                    + primary(type)
                                                    + "=? FOR UPDATE",
                                            (r, i) -> r.getLong(1),
                                            actor.tenantId(),
                                            number(resource))
                                    .orElseThrow(JdbcReportStore::missing);
                    if (!actor.superAdmin()
                            && owner != actor.userId()
                            && !granted(actor, type, resource, "ACL")
                            && !granted(actor, type, resource, "MANAGE")) {
                        throw ReportFault.denied();
                    }
                    jdbc.update(
                            "DELETE FROM rpt_acl WHERE tenant_id=? AND resource_type=? AND"
                                    + " resource_id=?",
                            actor.tenantId(),
                            type,
                            number(resource));
                    for (Grant grant : unique) {
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_acl(acl_id,resource_type,resource_id,subject_type,subject_id,action,tenant_id,create_dept,create_by)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                                number(id()),
                                type,
                                number(resource),
                                grant.subjectType(),
                                number(grant.subjectId()),
                                grant.action(),
                                actor.tenantId(),
                                actor.departmentId(),
                                actor.userId());
                    }
                    return null;
                });
    }

    /**
     * 完成 {@code audit} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param resource 当前操作的 {@code resource} 输入
     * @param action 资源操作
     * @param outcome 当前操作的 {@code outcome} 输入
     * @param details 当前操作的 {@code details} 输入
     */
    public void audit(
            ReportActor actor, String resource, String action, String outcome, String details) {
        jdbc.update(
                "INSERT INTO"
                    + " rpt_audit(audit_id,actor_id,resource_id,action,outcome,details_json,trace_id,tenant_id,create_dept,create_by)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                number(id()),
                actor.userId(),
                number(resource),
                action,
                outcome,
                details,
                UUID.randomUUID().toString(),
                actor.tenantId(),
                actor.departmentId(),
                actor.userId());
    }

    /**
     * 完成 {@code audits} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @return 当前处理返回的固定结果
     */
    public List<Map<String, Object>> audits(String tenant) {
        return jdbc.queryForList(
                "SELECT audit_id,actor_id,resource_id,action,outcome,trace_id,create_time FROM"
                    + " rpt_audit WHERE tenant_id=? ORDER BY create_time DESC,audit_id DESC LIMIT"
                    + " 200",
                tenant);
    }

    /**
     * 完成 {@code createRun} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param version 固定版本
     * @param mode 运行一致性模式
     * @param payload 当前操作的 {@code payload} 输入
     * @param hash 当前操作的 {@code hash} 输入
     * @param expires 资源到期时点
     * @return 当前处理返回的固定结果
     */
    public Run createRun(
            ReportActor actor,
            String report,
            String version,
            String mode,
            String payload,
            String hash,
            Instant expires) {
        return createRun(actor, report, version, mode, payload, hash, expires, false);
    }

    /**
     * 完成 {@code createRun} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param version 固定版本
     * @param mode 运行一致性模式
     * @param payload 当前操作的 {@code payload} 输入
     * @param hash 当前操作的 {@code hash} 输入
     * @param expires 资源到期时点
     * @param pinned 当前操作的 {@code pinned} 输入
     * @return 当前处理返回的固定结果
     */
    private Run createRun(
            ReportActor actor,
            String report,
            String version,
            String mode,
            String payload,
            String hash,
            Instant expires,
            boolean pinned) {
        if (mode == null || !Set.of("LIVE", "SNAPSHOT").contains(mode))
            throw ReportFault.invalid("运行模式无效");
        return atomic(
                () -> {
                    Definition definition = lockDefinition(actor, report);
                    Version target =
                            version(actor.tenantId(), version)
                                    .orElseThrow(JdbcReportStore::missing);
                    if (!Objects.equals(number(target.reportId()), number(report))) throw missing();
                    if (!definition.status().equals("PUBLISHED")
                            || !pinned
                                    && !Objects.equals(definition.publishedVersion(), target.id()))
                        throw conflict();
                    requireActiveDatasetVersion(actor.tenantId(), target.datasetVersion());
                    Instant now = now();
                    if (expires == null || !expires.isAfter(now)) throw expired();
                    String id = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_run(run_id,actor_id,report_id,version_id,mode,state,parameters_json,scope_hash,data_time,expires_at,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,'QUEUED',?,?,?,?,?,?,?)",
                            number(id),
                            actor.userId(),
                            number(report),
                            number(version),
                            mode,
                            payload,
                            hash,
                            Timestamp.from(now),
                            Timestamp.from(expires),
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    return run(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                });
    }

    /** 保存当前 {@code RUNPARENTS} 值。 */
    private static final String RUN_PARENTS =
            " FROM rpt_run r JOIN rpt_definition d ON d.report_id=r.report_id AND"
                    + " d.tenant_id=r.tenant_id JOIN rpt_version v ON v.version_id=r.version_id AND"
                    + " v.report_id=r.report_id AND v.tenant_id=r.tenant_id";

    /**
     * 完成 {@code run} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Optional<Run> run(String tenant, String id) {
        return one(
                "SELECT r.*" + RUN_PARENTS + " WHERE r.tenant_id=? AND r.run_id=?",
                RUN,
                tenant,
                number(id));
    }

    /**
     * 完成 {@code lockRun} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    private Run lockRun(String tenant, String id) {
        return one(
                        "SELECT * FROM rpt_run WHERE tenant_id=? AND run_id=? FOR UPDATE",
                        RUN,
                        tenant,
                        number(id))
                .orElseThrow(JdbcReportStore::missing);
    }

    /**
     * 完成 {@code runState} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @param state 目标状态
     * @param snapshot 当前操作的 {@code snapshot} 输入
     * @param plan 当前操作的 {@code plan} 输入
     * @param rows 待处理业务行
     * @param payload 当前操作的 {@code payload} 输入
     * @param error 当前操作的 {@code error} 输入
     */
    public void runState(
            String tenant,
            String id,
            String state,
            String snapshot,
            String plan,
            long rows,
            String payload,
            String error) {
        transitionRun(tenant, id, state, snapshot, plan, rows, payload, error);
    }

    /**
     * 带条件的完成更新，迟到工作线程不能替换运行终态或已固定快照。
     *
     * @param tenant 租户隔离标识
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param state 任务或资源状态
     * @param snapshot 当前契约的snapshot配置或状态
     * @param plan 当前契约的plan配置或状态
     * @param rows 精确类型的数据行
     * @param payload 当前契约的payload配置或状态
     * @param error 当前契约的error配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean transitionRun(
            String tenant,
            String id,
            String state,
            String snapshot,
            String plan,
            long rows,
            String payload,
            String error) {
        if (state == null
                || !Set.of("RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED").contains(state)
                || rows < 0) {
            throw ReportFault.invalid("运行状态或行数无效");
        }
        return atomic(
                () -> {
                    Run current = lockRun(tenant, id);
                    if (run(tenant, id).isEmpty()) throw missing();
                    if (state.equals("EXPIRED")) return expireRunLocked(current);
                    if (!current.expires().isAfter(now())) return false;
                    boolean valid =
                            switch (state) {
                                case "RUNNING" -> current.state().equals("QUEUED");
                                case "SUCCEEDED" -> current.state().equals("RUNNING");
                                default -> Set.of("QUEUED", "RUNNING").contains(current.state());
                            };
                    if (!valid) return false;
                    return jdbc.update(
                                    "UPDATE rpt_run SET"
                                        + " state=?,snapshot_ref=?,plan_ref=?,row_count=?,parameters_json=?,error_code=?,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND run_id=? AND state=?",
                                    state,
                                    snapshot,
                                    plan,
                                    rows,
                                    payload,
                                    error,
                                    tenant,
                                    number(id),
                                    current.state())
                            == 1;
                });
    }

    /** 保存当前 {@code ACTIVEEXPORT} 值。 */
    private static final String ACTIVE_EXPORT =
            "EXISTS (SELECT 1 FROM rpt_export_job j WHERE j.tenant_id=r.tenant_id AND"
                    + " j.run_id=r.run_id AND (j.state='QUEUED' OR j.state IN"
                    + " ('RUNNING','CANCEL_REQUESTED') AND j.lease_until>CURRENT_TIMESTAMP))";

    /**
     * 完成 {@code expiredRuns} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    public List<Run> expiredRuns() {
        return jdbc.query(
                "SELECT r.* FROM rpt_run r WHERE r.expires_at<=CURRENT_TIMESTAMP AND"
                        + " (r.state<>'EXPIRED' OR r.snapshot_ref IS NOT NULL OR r.plan_ref IS NOT"
                        + " NULL) AND NOT "
                        + ACTIVE_EXPORT
                        + " ORDER BY r.expires_at,r.run_id LIMIT 50",
                RUN);
    }

    /**
     * 先完成条件状态迁移，成功后才删除候选资源的私有文件。
     *
     * @param run 当前契约的run配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean expireRun(Run run) {
        return atomic(() -> expireRunLocked(lockRun(run.tenant(), run.id())));
    }

    /**
     * 完成 {@code expireRunLocked} 对应的只读取数报表存储处理。
     *
     * @param run 当前报表运行
     * @return 当前检查或条件操作是否成功
     */
    private boolean expireRunLocked(Run run) {
        return jdbc.update(
                        "UPDATE rpt_run r SET state='EXPIRED',update_time=CURRENT_TIMESTAMP WHERE"
                            + " r.tenant_id=? AND r.run_id=? AND r.expires_at<=CURRENT_TIMESTAMP"
                            + " AND r.state<>'EXPIRED' AND NOT "
                                + ACTIVE_EXPORT,
                        run.tenant(),
                        number(run.id()))
                == 1;
    }

    /** 保存当前 {@code JOBPARENTS} 值。 */
    private static final String JOB_PARENTS =
            " FROM rpt_export_job j JOIN rpt_run r ON r.run_id=j.run_id AND r.tenant_id=j.tenant_id"
                + " AND r.actor_id=j.actor_id JOIN rpt_definition d ON d.report_id=r.report_id AND"
                + " d.tenant_id=r.tenant_id JOIN rpt_version v ON v.version_id=r.version_id AND"
                + " v.report_id=r.report_id AND v.tenant_id=r.tenant_id";

    /**
     * 完成 {@code job} 对应的只读取数报表存储处理。
     *
     * @param tenant 当前租户标识
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Optional<ExportJob> job(String tenant, String id) {
        return one(
                "SELECT j.*" + JOB_PARENTS + " WHERE j.tenant_id=? AND j.job_id=?",
                JOB,
                tenant,
                number(id));
    }

    /**
     * 完成 {@code jobs} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<ExportJob> jobs(ReportActor actor) {
        return jdbc.query(
                "SELECT j.*"
                        + JOB_PARENTS
                        + " WHERE j.tenant_id=? AND j.actor_id=? ORDER BY j.create_time"
                        + " DESC,j.job_id DESC LIMIT 200",
                JOB,
                actor.tenantId(),
                actor.userId());
    }

    /**
     * 完成 {@code createJob} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param run 当前报表运行
     * @param format 当前操作的 {@code format} 输入
     * @param key 当前操作的 {@code key} 输入
     * @param hash 当前操作的 {@code hash} 输入
     * @param expires 资源到期时点
     * @return 当前处理返回的固定结果
     */
    public ExportJob createJob(
            ReportActor actor,
            String run,
            String format,
            String key,
            String hash,
            Instant expires) {
        return createJob(actor, run, format, key, hash, expires, false);
    }

    /**
     * 完成 {@code createJob} 对应的只读取数报表存储处理。
     *
     * @param actor 当前服务端身份
     * @param run 当前报表运行
     * @param format 当前操作的 {@code format} 输入
     * @param key 当前操作的 {@code key} 输入
     * @param hash 当前操作的 {@code hash} 输入
     * @param expires 资源到期时点
     * @param pendingSnapshot 当前操作的 {@code pendingSnapshot} 输入
     * @return 当前处理返回的固定结果
     */
    private ExportJob createJob(
            ReportActor actor,
            String run,
            String format,
            String key,
            String hash,
            Instant expires,
            boolean pendingSnapshot) {
        if (key == null
                || key.isBlank()
                || key.length() > 80
                || hash == null
                || hash.isBlank()
                || hash.length() > 64
                || format == null
                || format.isBlank()
                || format.length() > 32) {
            throw ReportFault.invalid("导出请求标识或格式无效");
        }
        return atomic(
                () -> {
                    /** 通过报表租户互斥记录串行处理不同运行和用户的任务准入，不使用租户系统表加锁。 */
                    if (fairDispatch) lockDispatch();
                    ensureRootCatalog(actor);
                    var existing =
                            one(
                                    "SELECT j.*"
                                            + JOB_PARENTS
                                            + " WHERE j.tenant_id=? AND j.actor_id=? AND"
                                            + " j.idempotency_key=? FOR UPDATE",
                                    JOB,
                                    actor.tenantId(),
                                    actor.userId(),
                                    key);
                    if (existing.isPresent()) {
                        ExportJob previous = existing.get();
                        if (!previous.requestHash().equals(hash)
                                || !previous.format().equals(format)
                                || !Objects.equals(number(previous.runId()), number(run)))
                            throw conflict();
                        return previous;
                    }
                    Run source = lockRun(actor.tenantId(), run);
                    if (source.actor() != actor.userId() || run(actor.tenantId(), run).isEmpty())
                        throw missing();
                    if (!source.expires().isAfter(now())) throw expired();
                    if (!source.mode().equals("SNAPSHOT")
                            || !(source.state().equals("SUCCEEDED") && source.snapshot() != null
                                    || pendingSnapshot
                                            && Set.of("QUEUED", "RUNNING")
                                                    .contains(source.state()))) throw conflict();
                    Definition definition = lockDefinition(actor, source.reportId());
                    if (!definition.status().equals("PUBLISHED")) throw conflict();
                    Version version =
                            version(actor.tenantId(), source.version())
                                    .orElseThrow(JdbcReportStore::missing);
                    requireActiveDatasetVersion(actor.tenantId(), version.datasetVersion());
                    if (expires == null || !expires.isAfter(now())) throw expired();
                    Instant boundedExpiry =
                            expires.isBefore(source.expires()) ? expires : source.expires();
                    Long userCount =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND"
                                            + " actor_id=? AND state IN"
                                            + " ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                                    Long.class,
                                    actor.tenantId(),
                                    actor.userId());
                    Long tenantCount =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND"
                                            + " state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                                    Long.class,
                                    actor.tenantId());
                    Long queued =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE state='QUEUED'",
                                    Long.class);
                    if (userCount != null && userCount >= queueUser
                            || tenantCount != null && tenantCount >= queueTenant
                            || queued != null && queued >= queueGlobal) {
                        throw new ReportFault("QUEUE_FULL", "导出队列已满，请稍后重试", 429);
                    }
                    String id = id();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_export_job(job_id,actor_id,run_id,format,idempotency_key,request_hash,state,expires_at,tenant_id,create_dept,create_by)"
                                + " VALUES(?,?,?,?,?,?,'QUEUED',?,?,?,?)",
                            number(id),
                            actor.userId(),
                            number(run),
                            format,
                            key,
                            hash,
                            Timestamp.from(boundedExpiry),
                            actor.tenantId(),
                            actor.departmentId(),
                            actor.userId());
                    return job(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                });
    }

    /**
     * 单任务查询独立于导出中心的有界列表，避免列表截断影响领取。
     *
     * @param actor 执行用户
     * @param key 当前契约的key配置或状态
     * @return 当前受控处理的固定结果
     */
    public Optional<ExportJob> jobByRequest(ReportActor actor, String key) {
        return one(
                "SELECT j.*"
                        + JOB_PARENTS
                        + " WHERE j.tenant_id=? AND j.actor_id=? AND j.idempotency_key=?",
                JOB,
                actor.tenantId(),
                actor.userId(),
                key);
    }

    /**
     * 同一租户准入事务同时创建固定实时运行副本与导出任务，失败时一起回滚。
     *
     * @param actor 执行用户
     * @param origin 当前契约的origin配置或状态
     * @param format 受控格式
     * @param key 当前契约的key配置或状态
     * @param hash 当前契约的hash配置或状态
     * @param expires 当前契约的expires配置或状态
     * @return 当前受控处理的固定结果
     */
    public ExportJob createExportJob(
            ReportActor actor,
            String origin,
            String format,
            String key,
            String hash,
            Instant expires) {
        return atomic(
                () -> {
                    if (fairDispatch) lockDispatch();
                    ensureRootCatalog(actor);
                    var prior = jobByRequest(actor, key);
                    if (prior.isPresent()) {
                        if (!prior.get().requestHash().equals(hash)
                                || !prior.get().format().equals(format)) throw conflict();
                        return prior.get();
                    }
                    Run source = lockRun(actor.tenantId(), origin);
                    if (source.actor() != actor.userId() || run(actor.tenantId(), origin).isEmpty())
                        throw missing();
                    if (!source.expires().isAfter(now())) throw expired();
                    if (!source.state().equals("SUCCEEDED")) throw conflict();
                    if (source.mode().equals("SNAPSHOT"))
                        return createJob(actor, source.id(), format, key, hash, expires, false);
                    if (!source.mode().equals("LIVE")) throw conflict();
                    Instant boundedExpiry =
                            expires != null && expires.isBefore(source.expires())
                                    ? expires
                                    : source.expires();
                    Run clone =
                            createRun(
                                    actor,
                                    source.reportId(),
                                    source.version(),
                                    "SNAPSHOT",
                                    source.parameters(),
                                    source.scopeHash(),
                                    boundedExpiry,
                                    true);
                    return createJob(actor, clone.id(), format, key, hash, boundedExpiry, true);
                });
    }

    /** 工作线程必须匹配当前尝试序号、有效租约、执行用户和未取消状态。 */
    /**
     * 原子创建固定发布版本的待执行快照和导出任务，供批次与逐接收人订阅复用。
     *
     * @param actor 执行用户
     * @param version 当前契约的version配置或状态
     * @param payload 当前契约的payload配置或状态
     * @param scope 当前契约的scope配置或状态
     * @param format 受控格式
     * @param key 当前契约的key配置或状态
     * @param requestHash 当前契约的requestHash配置或状态
     * @param expires 当前契约的expires配置或状态
     * @return 当前受控处理的固定结果
     */
    public ExportJob createFixedExport(
            ReportActor actor,
            Version version,
            String payload,
            String scope,
            String format,
            String key,
            String requestHash,
            Instant expires) {
        return atomic(
                () -> {
                    if (fairDispatch) lockDispatch();
                    ensureRootCatalog(actor);
                    var prior = jobByRequest(actor, key);
                    if (prior.isPresent()) {
                        if (!prior.get().requestHash().equals(requestHash)
                                || !prior.get().format().equals(format)) throw conflict();
                        return prior.get();
                    }
                    var run =
                            createRun(
                                    actor,
                                    version.reportId(),
                                    version.id(),
                                    "SNAPSHOT",
                                    payload,
                                    scope,
                                    expires,
                                    true);
                    return createJob(actor, run.id(), format, key, requestHash, expires, true);
                });
    }

    /**
     * 执行leaseValid对应的受控处理。
     *
     * @param claimed 当前受控操作的claimed输入
     * @return 当前固定契约或处理结果
     */
    public boolean leaseValid(ExportJob claimed) {
        return liveJob(claimed, false).isPresent();
    }

    /**
     * 完成 {@code liveJob} 对应的只读取数报表存储处理。
     *
     * @param claimed 当前操作的 {@code claimed} 输入
     * @param lock 当前操作的 {@code lock} 输入
     * @return 当前处理返回的固定结果
     */
    private Optional<ExportJob> liveJob(ExportJob claimed, boolean lock) {
        if (claimed == null || claimed.leaseOwner() == null) return Optional.empty();
        return one(
                "SELECT * FROM rpt_export_job WHERE tenant_id=? AND job_id=? AND actor_id=? AND"
                        + " run_id=? AND lease_owner=? AND attempts=? AND state='RUNNING' AND"
                        + " cancel_requested=0 AND lease_until>CURRENT_TIMESTAMP AND"
                        + " expires_at>CURRENT_TIMESTAMP"
                        + (lock ? " FOR UPDATE" : ""),
                JOB,
                claimed.tenant(),
                number(claimed.id()),
                claimed.actor(),
                number(claimed.runId()),
                claimed.leaseOwner(),
                claimed.attempts());
    }

    /**
     * 快照引用暂时保存不可猜测的构建检查点，仅检查点持有者可以完成登记。
     *
     * @param tenant 租户隔离标识
     * @param runId 当前契约的runId配置或状态
     * @param checkpoint 当前契约的checkpoint配置或状态
     * @param claimed 当前契约的claimed配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean beginSnapshot(
            String tenant, String runId, String checkpoint, ExportJob claimed) {
        if (checkpoint == null || !checkpoint.startsWith("build/") || checkpoint.length() > 256)
            throw ReportFault.invalid("快照构建标识无效");
        return atomic(
                () -> {
                    Run run = lockRun(tenant, runId);
                    if (!run.mode().equals("SNAPSHOT")
                            || !run.expires().isAfter(now())
                            || run(tenant, runId).isEmpty()) return false;
                    if (claimed != null
                            && (!tenant.equals(claimed.tenant())
                                    || !run.id().equals(claimed.runId())
                                    || liveJob(claimed, true).isEmpty())) return false;
                    if (run.state().equals("RUNNING")) {
                        if (claimed == null
                                || run.snapshot() == null
                                || !run.snapshot().startsWith("build/job/")) return false;
                        String[] previous = run.snapshot().split("/");
                        if (previous.length != 5) return false;
                        ExportJob prior =
                                one(
                                                "SELECT * FROM rpt_export_job WHERE tenant_id=? AND"
                                                        + " job_id=? AND run_id=? FOR UPDATE",
                                                JOB,
                                                tenant,
                                                number(previous[2]),
                                                number(runId))
                                        .orElse(null);
                        if (prior != null
                                && Integer.toString(prior.attempts()).equals(previous[3])
                                && Set.of("RUNNING", "CANCEL_REQUESTED").contains(prior.state())
                                && prior.leaseUntil() != null
                                && prior.leaseUntil().isAfter(now())) return false;
                    } else if (!Set.of("QUEUED", "FAILED").contains(run.state())) return false;
                    return jdbc.update(
                                    "UPDATE rpt_run SET"
                                        + " state='RUNNING',snapshot_ref=?,data_time=CURRENT_TIMESTAMP,error_code=NULL,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND run_id=?",
                                    checkpoint,
                                    tenant,
                                    number(runId))
                            == 1;
                });
    }

    /**
     * 运行终态与检查点更新同任务有效围栏保持原子性，旧尝试不能改变它们。
     *
     * @param tenant 租户隔离标识
     * @param runId 当前契约的runId配置或状态
     * @param checkpoint 当前契约的checkpoint配置或状态
     * @param claimed 当前契约的claimed配置或状态
     * @param state 任务或资源状态
     * @param reference 私有对象的一次性引用
     * @param rows 精确类型的数据行
     * @param payload 当前契约的payload配置或状态
     * @param error 当前契约的error配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean finishSnapshot(
            String tenant,
            String runId,
            String checkpoint,
            ExportJob claimed,
            String state,
            String reference,
            long rows,
            String payload,
            String error) {
        if (state == null
                || !Set.of("SUCCEEDED", "FAILED", "QUEUED", "CANCELLED").contains(state)
                || rows < 0) throw ReportFault.invalid("快照状态无效");
        return atomic(
                () -> {
                    Run run = lockRun(tenant, runId);
                    if (!run.state().equals("RUNNING")
                            || !Objects.equals(run.snapshot(), checkpoint)
                            || !run.expires().isAfter(now())) return false;
                    if (claimed != null
                            && (!tenant.equals(claimed.tenant())
                                    || !run.id().equals(claimed.runId())
                                    || liveJob(claimed, true).isEmpty())) return false;
                    return jdbc.update(
                                    "UPDATE rpt_run SET"
                                        + " state=?,snapshot_ref=?,row_count=?,parameters_json=?,error_code=?,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND run_id=? AND state='RUNNING' AND"
                                        + " snapshot_ref=?",
                                    state,
                                    state.equals("SUCCEEDED") ? reference : null,
                                    rows,
                                    payload,
                                    safeError(error),
                                    tenant,
                                    number(runId),
                                    checkpoint)
                            == 1;
                });
    }

    /**
     * 完整快照最多登记一份不可变布局，工作线程仍需满足尝试围栏。
     *
     * @param tenant 租户隔离标识
     * @param runId 当前契约的runId配置或状态
     * @param snapshot 当前契约的snapshot配置或状态
     * @param reference 私有对象的一次性引用
     * @param payload 当前契约的payload配置或状态
     * @param claimed 当前契约的claimed配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean publishRenderPlan(
            String tenant,
            String runId,
            String snapshot,
            String reference,
            String payload,
            ExportJob claimed) {
        if (reference == null || !reference.matches("[a-f0-9-]{36}\\.plan"))
            throw ReportFault.invalid("排版产物标识无效");
        return atomic(
                () -> {
                    Run run = lockRun(tenant, runId);
                    if (!run.state().equals("SUCCEEDED")
                            || !run.mode().equals("SNAPSHOT")
                            || !run.expires().isAfter(now())
                            || run.plan() != null
                            || snapshot == null
                            || !snapshot.equals(run.snapshot())) return false;
                    if (claimed != null
                            && (!tenant.equals(claimed.tenant())
                                    || !runId.equals(claimed.runId())
                                    || liveJob(claimed, true).isEmpty())) return false;
                    return jdbc.update(
                                    "UPDATE rpt_run SET"
                                        + " plan_ref=?,parameters_json=?,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND run_id=? AND state='SUCCEEDED'"
                                        + " AND plan_ref IS NULL AND snapshot_ref=?",
                                    reference,
                                    payload,
                                    tenant,
                                    number(runId),
                                    snapshot)
                            == 1;
                });
    }

    /**
     * 完成 {@code safeError} 对应的只读取数报表存储处理。
     *
     * @param error 当前操作的 {@code error} 输入
     * @return 当前处理返回的固定结果
     */
    private static String safeError(String error) {
        if (error == null) return null;
        return Set.of(
                                "STORAGE_UNAVAILABLE",
                                "ARTIFACT_STORE_UNAVAILABLE",
                                "XLSX_EXPORT_FAILED",
                                "PDF_EXPORT_FAILED",
                                "REPORT_EXECUTION_FAILED",
                                "SCOPE_DENIED",
                                "SCOPE_CHANGED",
                                "SNAPSHOT_EXPIRED",
                                "SNAPSHOT_INVALID",
                                "PLAN_INVALID",
                                "FONT_UNAVAILABLE",
                                "LEASE_LOST",
                                "CANCELLED",
                                "CAPACITY_EXCEEDED",
                                "QUERY_TIMEOUT",
                                "VERSION_UNSUPPORTED",
                                "REPORT_OFFLINE")
                        .contains(error)
                ? error
                : "REPORT_EXECUTION_FAILED";
    }

    /** 保存当前 {@code VALIDJOBRUN} 值。 */
    private static final String VALID_JOB_RUN =
            "EXISTS (SELECT 1 FROM rpt_run r JOIN rpt_definition d ON d.report_id=r.report_id AND"
                + " d.tenant_id=r.tenant_id JOIN rpt_version v ON v.version_id=r.version_id AND"
                + " v.report_id=r.report_id AND v.tenant_id=r.tenant_id WHERE r.run_id=j.run_id AND"
                + " r.tenant_id=j.tenant_id AND r.actor_id=j.actor_id AND r.state IN"
                + " ('QUEUED','RUNNING','SUCCEEDED') AND r.mode='SNAPSHOT' AND"
                + " r.expires_at>CURRENT_TIMESTAMP AND d.status='PUBLISHED')";

    /** 保存当前 {@code INACTIVELEASE} 值。 */
    private static final String INACTIVE_LEASE =
            "(j.lease_until IS NULL OR j.lease_until<=CURRENT_TIMESTAMP)";

    /** 保存当前 {@code CLAIMABLE} 值。 */
    private static final String CLAIMABLE =
            "j.expires_at>CURRENT_TIMESTAMP AND "
                    + VALID_JOB_RUN
                    + " AND ((j.state='QUEUED' AND j.cancel_requested=0 AND"
                    + " j.attempts<3*(j.retry_round+1))"
                    /** 取消任务回收仅获取清理租约；达到第三次执行尝试后不再生成文件。 */
                    + " OR (j.state IN ('RUNNING','CANCEL_REQUESTED') AND "
                    + INACTIVE_LEASE
                    + " AND (j.attempts<3*(j.retry_round+1) OR j.cancel_requested=1)))";

    /**
     * 仅调度器可跨租户维护和扫描，访问业务数据前仍重新校验每个候选的权限。
     *
     * @return 当前受控处理的固定结果
     */
    public List<ExportJob> claimableJobs() {
        reconcileJobs();
        if (fairDispatch) return fairCandidates();
        return jdbc.query(
                "SELECT j.* FROM rpt_export_job j WHERE "
                        + CLAIMABLE
                        + " ORDER BY j.create_time,j.job_id LIMIT 8",
                JOB);
    }

    /**
     * 回收失效租约，第三次尝试中断的任务不能永久占用准入名额。
     *
     * @return 当前受控处理的固定结果
     */
    public int reconcileJobs() {
        return atomic(
                () -> {
                    int changed =
                            jdbc.update(
                                    "UPDATE rpt_export_job j SET"
                                        + " state='EXPIRED',execution_phase='EXPIRED',lease_owner=NULL,lease_until=NULL,error_code='SNAPSHOT_EXPIRED',update_time=CURRENT_TIMESTAMP"
                                        + " WHERE j.state IN"
                                        + " ('QUEUED','RUNNING','CANCEL_REQUESTED') AND"
                                        + " (j.state='QUEUED' OR "
                                            + INACTIVE_LEASE
                                            + ")"
                                            + " AND (j.expires_at<=CURRENT_TIMESTAMP OR NOT "
                                            + VALID_JOB_RUN
                                            + ")");
                    changed +=
                            jdbc.update(
                                    "UPDATE rpt_export_job j SET"
                                        + " state='FAILED',execution_phase='FAILED',lease_owner=NULL,lease_until=NULL,error_code='RETRY_EXHAUSTED',update_time=CURRENT_TIMESTAMP"
                                        + " WHERE j.state IN ('QUEUED','RUNNING') AND"
                                        + " j.cancel_requested=0 AND"
                                        + " j.attempts>=3*(j.retry_round+1) AND "
                                            + INACTIVE_LEASE);
                    return changed;
                });
    }

    /**
     * 完成 {@code claim} 对应的只读取数报表存储处理。
     *
     * @param job 当前导出任务
     * @param owner 租约或资源持有者
     * @param lease 已领取的任务围栏
     * @return 当前检查或条件操作是否成功
     */
    public boolean claim(ExportJob job, String owner, Instant lease) {
        return claimJob(job, owner, lease).isPresent();
    }

    /**
     * 返回心跳和完成提交必须使用的尝试围栏，兼容节点复用相同租约持有者名称。
     *
     * @param job 当前契约的job配置或状态
     * @param owner 资源创建者或当前尝试持有者
     * @param lease 当前尝试的租约截止时刻
     * @return 当前受控处理的固定结果
     */
    public Optional<ExportJob> claimJob(ExportJob job, String owner, Instant lease) {
        if (owner == null || owner.isBlank() || owner.length() > 100 || lease == null)
            throw ReportFault.invalid("导出任务租约无效");
        return atomic(
                () -> {
                    if (!lease.isAfter(now())) return Optional.empty();
                    if (fairDispatch) {
                        lockDispatch();
                        if (active(null, null) >= executingGlobal
                                || active(job.tenant(), null) >= executingTenant
                                || active(job.tenant(), job.actor()) >= executingUser)
                            return Optional.empty();
                    }
                    int changed =
                            jdbc.update(
                                    "UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1"
                                        + " THEN 'CANCEL_REQUESTED' ELSE 'RUNNING' END,"
                                        + " execution_phase='PREPARING',processed_rows=0,total_rows=NULL,attempts=attempts+1,lease_owner=?,lease_until=LEAST(?,expires_at),update_time=CURRENT_TIMESTAMP"
                                        + " WHERE j.tenant_id=? AND j.job_id=? AND j.attempts=? AND"
                                        + " "
                                            + CLAIMABLE,
                                    owner,
                                    Timestamp.from(lease),
                                    job.tenant(),
                                    number(job.id()),
                                    job.attempts());
                    if (changed == 1 && fairDispatch) {
                        claimTime("TENANT:" + job.tenant());
                        claimTime("USER:" + job.tenant() + ":" + job.actor());
                    }
                    return changed == 1 ? job(job.tenant(), job.id()) : Optional.empty();
                });
    }

    /**
     * 完成 {@code attemptFence} 对应的只读取数报表存储处理。
     *
     * @param job 当前导出任务
     * @param owner 租约或资源持有者
     * @return 当前处理返回的固定结果
     */
    /** 全局准入和领取共享一条主库互斥记录，跨租户不能同时越过上限。 */
    private void lockDispatch() {
        jdbc.update("INSERT IGNORE INTO rpt_dispatch_gate(scope_key) VALUES('GLOBAL')");
        jdbc.queryForMap(
                "SELECT scope_key FROM rpt_dispatch_gate WHERE scope_key='GLOBAL' FOR UPDATE");
    }

    /**
     * 有效租约才占用执行并发，取消中的尝试仍等待确认释放。
     *
     * @param tenant 租户隔离标识
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    private long active(String tenant, Long actor) {
        String sql =
                "SELECT COUNT(*) FROM rpt_export_job WHERE state IN ('RUNNING','CANCEL_REQUESTED')"
                        + " AND lease_until>CURRENT_TIMESTAMP AND expires_at>CURRENT_TIMESTAMP";
        var values = new ArrayList<Object>();
        if (tenant != null) {
            sql += " AND tenant_id=?";
            values.add(tenant);
        }
        if (actor != null) {
            sql += " AND actor_id=?";
            values.add(actor);
        }
        return jdbc.queryForObject(sql, Long.class, values.toArray());
    }

    /**
     * 轮转记录只有成功领取时更新，候选重复轮询不会占有名额。
     *
     * @param key 当前契约的key配置或状态
     */
    private void claimTime(String key) {
        jdbc.update("INSERT IGNORE INTO rpt_dispatch_gate(scope_key) VALUES(?)", key);
        jdbc.update(
                "UPDATE rpt_dispatch_gate SET last_claim=CURRENT_TIMESTAMP WHERE scope_key=?", key);
    }

    /**
     * 每用户只选最优先的一项，先轮转租户再轮转用户，订阅任务排在交互任务之后。
     *
     * @return 当前受控处理的固定结果
     */
    private List<ExportJob> fairCandidates() {
        String priority =
                "CASE WHEN j.idempotency_key LIKE 'sub_%' THEN 0 WHEN j.idempotency_key LIKE"
                        + " 'batch_%' THEN 1 ELSE 2 END";
        String priorPriority = priority.replace("j.", "p.");
        String priorClaimable = CLAIMABLE.replace("j.", "p.");
        String sql =
                "SELECT j.* FROM rpt_export_job j LEFT JOIN rpt_dispatch_gate t ON"
                    + " t.scope_key=CONCAT('TENANT:',j.tenant_id) LEFT JOIN rpt_dispatch_gate u ON"
                    + " u.scope_key=CONCAT('USER:',j.tenant_id,':',j.actor_id) WHERE "
                        + CLAIMABLE
                        + " AND (SELECT COUNT(*) FROM rpt_export_job tenant_running WHERE"
                        + " tenant_running.tenant_id=j.tenant_id AND tenant_running.state IN"
                        + " ('RUNNING','CANCEL_REQUESTED') AND"
                        + " tenant_running.lease_until>CURRENT_TIMESTAMP AND"
                        + " tenant_running.expires_at>CURRENT_TIMESTAMP)<"
                        + executingTenant
                        + " AND (SELECT COUNT(*) FROM rpt_export_job running WHERE"
                        + " running.tenant_id=j.tenant_id AND running.actor_id=j.actor_id AND"
                        + " running.state IN ('RUNNING','CANCEL_REQUESTED') AND"
                        + " running.lease_until>CURRENT_TIMESTAMP AND"
                        + " running.expires_at>CURRENT_TIMESTAMP)<"
                        + executingUser
                        + " AND NOT EXISTS(SELECT 1 FROM rpt_export_job p WHERE"
                        + " p.tenant_id=j.tenant_id AND p.actor_id=j.actor_id AND "
                        + priorClaimable
                        + " AND ("
                        + priorPriority
                        + ">"
                        + priority
                        + " OR ("
                        + priorPriority
                        + "="
                        + priority
                        + " AND (p.create_time<j.create_time OR p.create_time=j.create_time AND"
                        + " p.job_id<j.job_id)))) ORDER BY"
                        + " COALESCE(t.last_claim,'1970-01-01'),COALESCE(u.last_claim,'1970-01-01'),"
                        + priority
                        + " DESC,j.create_time,j.job_id LIMIT 8";
        return jdbc.query(sql, JOB);
    }

    /**
     * 执行attemptFence对应的受控处理。
     *
     * @param job 当前受控操作的job输入
     * @param owner 当前受控操作的owner输入
     * @return 当前固定契约或处理结果
     */
    private static int attemptFence(ExportJob job, String owner) {
        /** 兼容旧排队记录；持有者名称复用时必须使用任务领取返回的新围栏。 */
        return job.state().equals("QUEUED") || !Objects.equals(job.leaseOwner(), owner)
                ? job.attempts() + 1
                : job.attempts();
    }

    /**
     * 仅为当前有效尝试延续租约。
     *
     * @param job 当前导出任务
     * @param owner 租约或资源持有者
     * @param lease 已领取的任务围栏
     * @return 当前检查或条件操作是否成功
     */
    public boolean heartbeat(ExportJob job, String owner, Instant lease) {
        if (owner == null || owner.isBlank() || lease == null) return false;
        return jdbc.update(
                        "UPDATE rpt_export_job j SET"
                            + " lease_until=LEAST(?,expires_at),last_heartbeat=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP"
                            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND"
                            + " j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED') AND"
                            + " j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP"
                            + " AND LEAST(?,j.expires_at)>=j.lease_until AND "
                                + VALID_JOB_RUN,
                        Timestamp.from(lease),
                        job.tenant(),
                        number(job.id()),
                        owner,
                        attemptFence(job, owner),
                        Timestamp.from(lease))
                == 1;
    }

    /**
     * 在有效围栏下登记导出终态。
     *
     * @param job 当前导出任务
     * @param owner 租约或资源持有者
     * @param state 目标状态
     * @param file 当前操作的 {@code file} 输入
     * @param bytes 受控字节数据
     * @param error 当前操作的 {@code error} 输入
     * @return 当前检查或条件操作是否成功
     */
    public boolean finishJob(
            ExportJob job, String owner, String state, String file, Long bytes, String error) {
        if (state == null || !Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(state))
            throw ReportFault.invalid("导出终态无效");
        boolean success = state.equals("SUCCEEDED");
        if (success && (file == null || file.isBlank() || bytes == null || bytes < 0))
            throw ReportFault.invalid("导出文件元数据无效");
        String guard =
                success
                        ? " AND j.cancel_requested=0 AND j.state='RUNNING' AND "
                                + VALID_JOB_RUN
                                + " AND EXISTS (SELECT 1 FROM rpt_run r WHERE r.run_id=j.run_id AND"
                                + " r.tenant_id=j.tenant_id AND r.state='SUCCEEDED')"
                        : "";
        return jdbc.update(
                        "UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1 THEN"
                            + " 'CANCELLED' ELSE ? END, execution_phase=CASE WHEN"
                            + " cancel_requested=1 THEN 'CANCELLED' WHEN ?='CANCELLED' THEN"
                            + " 'CANCELLED' WHEN ?='SUCCEEDED' THEN 'COMPLETED' ELSE 'FAILED'"
                            + " END,file_ref=?,file_size=?,error_code=?,lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
                            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND"
                            + " j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED') AND"
                            + " j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP"
                                + guard,
                        state,
                        state,
                        state,
                        success ? file : null,
                        success ? bytes : null,
                        error,
                        job.tenant(),
                        number(job.id()),
                        owner,
                        attemptFence(job, owner))
                == 1;
    }

    /**
     * 仅重试当前有效尝试，错误码不保存异常原文。
     *
     * @param job 当前契约的job配置或状态
     * @param owner 资源创建者或当前尝试持有者
     * @param error 当前契约的error配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean retryJob(ExportJob job, String owner, String error) {
        String code =
                Set.of(
                                        "STORAGE_UNAVAILABLE",
                                        "ARTIFACT_STORE_UNAVAILABLE",
                                        "XLSX_EXPORT_FAILED",
                                        "PDF_EXPORT_FAILED",
                                        "REPORT_EXECUTION_FAILED")
                                .contains(error == null ? "" : error)
                        ? error
                        : "REPORT_EXECUTION_FAILED";
        return jdbc.update(
                        "UPDATE rpt_export_job j SET state=CASE WHEN cancel_requested=1 THEN"
                            + " 'CANCELLED' WHEN attempts<3*(retry_round+1) THEN 'QUEUED' ELSE"
                            + " 'FAILED' END, execution_phase=CASE WHEN cancel_requested=1 THEN"
                            + " 'CANCELLED' WHEN attempts<3*(retry_round+1) THEN 'QUEUED' ELSE"
                            + " 'FAILED' END,processed_rows=CASE WHEN cancel_requested=0 AND"
                            + " attempts<3*(retry_round+1) THEN 0 ELSE processed_rows"
                            + " END,total_rows=CASE WHEN cancel_requested=0 AND"
                            + " attempts<3*(retry_round+1) THEN NULL ELSE total_rows"
                            + " END,file_ref=NULL,file_size=NULL,error_code=?,lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
                            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND"
                            + " j.attempts=? AND j.state IN ('RUNNING','CANCEL_REQUESTED') AND"
                            + " j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP"
                            + " AND EXISTS (SELECT 1 FROM rpt_run r WHERE r.run_id=j.run_id AND"
                            + " r.tenant_id=j.tenant_id AND r.actor_id=j.actor_id AND"
                            + " r.expires_at>CURRENT_TIMESTAMP)",
                        code,
                        job.tenant(),
                        number(job.id()),
                        owner,
                        attemptFence(job, owner))
                == 1;
    }

    /**
     * 仅为当前有效租约写入单调阶段和进度。
     *
     * @param job 当前导出任务
     * @param phase 当前操作的 {@code phase} 输入
     * @param processed 当前操作的 {@code processed} 输入
     * @param total 当前操作的 {@code total} 输入
     * @return 当前检查或条件操作是否成功
     */
    public boolean progressJob(ExportJob job, String phase, long processed, Long total) {
        if (!Set.of("PREPARING", "SNAPSHOT", "LAYOUT", "WRITING").contains(phase)
                || processed < 0
                || processed > 100000
                || total != null && (total < processed || total > 100000))
            throw ReportFault.invalid("任务进度无效");
        return jdbc.update(
                        "UPDATE rpt_export_job j SET"
                            + " execution_phase=?,processed_rows=?,total_rows=?,update_time=CURRENT_TIMESTAMP"
                            + " WHERE j.tenant_id=? AND j.job_id=? AND j.lease_owner=? AND"
                            + " j.attempts=? AND j.state='RUNNING' AND j.cancel_requested=0 AND"
                            + " j.lease_until>CURRENT_TIMESTAMP AND j.expires_at>CURRENT_TIMESTAMP"
                            + " AND (CASE j.execution_phase WHEN 'PREPARING' THEN 0 WHEN 'SNAPSHOT'"
                            + " THEN 1 WHEN 'LAYOUT' THEN 2 WHEN 'WRITING' THEN 3 ELSE 0 END)<=?"
                            + " AND (j.execution_phase<>? OR j.processed_rows<=?) AND "
                                + VALID_JOB_RUN,
                        phase,
                        processed,
                        total,
                        job.tenant(),
                        number(job.id()),
                        job.leaseOwner(),
                        job.attempts(),
                        phase.equals("PREPARING")
                                ? 0
                                : phase.equals("SNAPSHOT") ? 1 : phase.equals("LAYOUT") ? 2 : 3,
                        phase,
                        processed)
                == 1;
    }

    /**
     * 完成 {@code manualRetryable} 对应的只读取数报表存储处理。
     *
     * @param job 当前导出任务
     * @return 当前检查或条件操作是否成功
     */
    public static boolean manualRetryable(ExportJob job) {
        return job.state().equals("FAILED")
                && !job.cancelled()
                && job.retryRound() < 2
                && job.expires().isAfter(Instant.now())
                && job.error() != null
                && Set.of(
                                "ARTIFACT_STORE_UNAVAILABLE",
                                "STORAGE_UNAVAILABLE",
                                "STORAGE_FAILED",
                                "PDF_EXPORT_FAILED",
                                "XLSX_EXPORT_FAILED",
                                "REPORT_EXECUTION_FAILED",
                                "RETRY_EXHAUSTED")
                        .contains(job.error());
    }

    /**
     * 以预期轮次幂等重试原任务。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param expectedRound 预期重试轮次
     * @return 当前检查或条件操作是否成功
     */
    public boolean manualRetry(ReportActor actor, String id, int expectedRound) {
        if (expectedRound < 0 || expectedRound > 1) throw ReportFault.invalid("重试轮次无效");
        return atomic(
                () -> {
                    ensureRootCatalog(actor);
                    var job = job(actor.tenantId(), id).orElseThrow(JdbcReportStore::missing);
                    var run = lockRun(actor.tenantId(), job.runId());
                    if (job.actor() != actor.userId()) throw ReportFault.denied();
                    if (job.retryRound() > expectedRound) return true;
                    if (job.retryRound() != expectedRound
                            || !manualRetryable(job)
                            || !run.expires().isAfter(now())
                            || run.state().equals("EXPIRED")) return false;
                    Long userCount =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND"
                                            + " actor_id=? AND state IN"
                                            + " ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                                    Long.class,
                                    actor.tenantId(),
                                    actor.userId());
                    Long tenantCount =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE tenant_id=? AND"
                                            + " state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                                    Long.class,
                                    actor.tenantId());
                    Long queued =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM rpt_export_job WHERE state='QUEUED'",
                                    Long.class);
                    if (userCount != null && userCount >= 1
                            || tenantCount != null && tenantCount >= 2
                            || queued != null && queued >= 100) {
                        throw new ReportFault("QUEUE_FULL", "导出队列已满，请稍后重试", 429);
                    }

                    return jdbc.update(
                                    "UPDATE rpt_export_job SET"
                                        + " state='QUEUED',execution_phase='QUEUED',processed_rows=0,total_rows=NULL,retry_round=retry_round+1,lease_owner=NULL,lease_until=NULL,error_code=NULL,update_time=CURRENT_TIMESTAMP"
                                        + " WHERE tenant_id=? AND job_id=? AND actor_id=? AND"
                                        + " state='FAILED' AND cancel_requested=0 AND attempts=?"
                                        + " AND retry_round=? AND expires_at>CURRENT_TIMESTAMP",
                                    actor.tenantId(),
                                    number(id),
                                    actor.userId(),
                                    job.attempts(),
                                    job.retryRound())
                            == 1;
                });
    }

    /**
     * 请求取消当前用户拥有的任务。
     *
     * @param actor 当前服务端身份
     * @param job 当前导出任务
     */
    public void cancel(ReportActor actor, String job) {
        jdbc.update(
                "UPDATE rpt_export_job SET cancel_requested=1,execution_phase=CASE WHEN"
                    + " state='QUEUED' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED' END,state=CASE WHEN"
                    + " state='QUEUED' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED'"
                    + " END,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND actor_id=? AND"
                    + " job_id=? AND state IN ('QUEUED','RUNNING','CANCEL_REQUESTED')",
                actor.tenantId(),
                actor.userId(),
                number(job));
    }

    /**
     * 完成 {@code expiredJobs} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    public List<ExportJob> expiredJobs() {
        return jdbc.query(
                "SELECT j.* FROM rpt_export_job j WHERE j.expires_at<=CURRENT_TIMESTAMP AND"
                        + " (j.state<>'EXPIRED' OR j.file_ref IS NOT NULL) AND (j.state NOT IN"
                        + " ('RUNNING','CANCEL_REQUESTED') OR "
                        + INACTIVE_LEASE
                        + ") ORDER BY j.expires_at,j.job_id LIMIT 50",
                JOB);
    }

    /**
     * 完成 {@code expireJob} 对应的只读取数报表存储处理。
     *
     * @param job 当前导出任务
     */
    public void expireJob(ExportJob job) {
        tryExpireJob(job);
    }

    /**
     * 只有条件到期迁移成功后，清理流程才可删除私有文件。
     *
     * @param job 当前契约的job配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean tryExpireJob(ExportJob job) {
        return jdbc.update(
                        "UPDATE rpt_export_job j SET"
                            + " state='EXPIRED',execution_phase='EXPIRED',lease_owner=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP"
                            + " WHERE j.tenant_id=? AND j.job_id=? AND"
                            + " j.expires_at<=CURRENT_TIMESTAMP AND j.state<>'EXPIRED' AND (j.state"
                            + " NOT IN ('RUNNING','CANCEL_REQUESTED') OR "
                                + INACTIVE_LEASE
                                + ")",
                        job.tenant(),
                        number(job.id()))
                == 1;
    }

    /**
     * 仅清除已成功物理删除且引用完全匹配的到期资源记录。
     *
     * @param expired 当前契约的expired配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean ackJobCleanup(ExportJob expired) {
        return jdbc.update(
                        "UPDATE rpt_export_job SET"
                            + " file_ref=NULL,file_size=NULL,update_time=CURRENT_TIMESTAMP WHERE"
                            + " tenant_id=? AND job_id=? AND state='EXPIRED' AND (file_ref=? OR"
                            + " file_ref IS NULL AND ? IS NULL)",
                        expired.tenant(),
                        number(expired.id()),
                        expired.file(),
                        expired.file())
                == 1;
    }

    /**
     * 确认准确匹配的到期运行文件已清理。
     *
     * @param expired 当前操作的 {@code expired} 输入
     * @return 当前检查或条件操作是否成功
     */
    public boolean ackRunCleanup(Run expired) {
        return jdbc.update(
                        "UPDATE rpt_run SET"
                            + " snapshot_ref=NULL,plan_ref=NULL,update_time=CURRENT_TIMESTAMP WHERE"
                            + " tenant_id=? AND run_id=? AND state='EXPIRED' AND (snapshot_ref=? OR"
                            + " snapshot_ref IS NULL AND ? IS NULL) AND (plan_ref=? OR plan_ref IS"
                            + " NULL AND ? IS NULL)",
                        expired.tenant(),
                        number(expired.id()),
                        expired.snapshot(),
                        expired.snapshot(),
                        expired.plan(),
                        expired.plan())
                == 1;
    }

    /**
     * 包含已到期但尚未确认清理的引用，确保失败清理可以再次尝试。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public boolean artifactReferenced(String tenant, String reference) {
        if (reference == null) return false;
        if (reference.endsWith(".asset")
                && !jdbc.queryForList(
                                "SELECT version_id FROM rpt_resource_artifact WHERE tenant_id=? AND"
                                        + " artifact_ref=? LIMIT 1",
                                tenant,
                                reference)
                        .isEmpty()) return true;
        if (reference.endsWith(".index")
                && !jdbc.queryForList(
                                "SELECT i.index_ref FROM rpt_snapshot_index i JOIN rpt_run r ON"
                                    + " r.tenant_id=i.tenant_id AND r.snapshot_ref=i.snapshot_ref"
                                    + " WHERE i.tenant_id=? AND i.index_ref=? LIMIT 1",
                                tenant,
                                reference)
                        .isEmpty()) return true;
        if (reference.endsWith(".font")
                && !jdbc.queryForList(
                                "SELECT f.font_ref FROM rpt_font_resource f WHERE f.tenant_id=? AND"
                                    + " f.font_ref=? AND (f.lease_until>CURRENT_TIMESTAMP OR"
                                    + " EXISTS(SELECT 1 FROM rpt_plan_font p JOIN rpt_run r ON"
                                    + " r.tenant_id=p.tenant_id AND r.plan_ref=p.plan_ref WHERE"
                                    + " p.tenant_id=f.tenant_id AND p.font_ref=f.font_ref)) LIMIT"
                                    + " 1",
                                tenant,
                                reference)
                        .isEmpty()) return true;
        return !jdbc.queryForList(
                                "SELECT run_id FROM rpt_run WHERE tenant_id=? AND (snapshot_ref=?"
                                        + " OR plan_ref=?) LIMIT 1",
                                tenant,
                                reference,
                                reference)
                        .isEmpty()
                || !jdbc.queryForList(
                                "SELECT job_id FROM rpt_export_job WHERE tenant_id=? AND file_ref=?"
                                        + " LIMIT 1",
                                tenant,
                                reference)
                        .isEmpty();
    }

    /**
     * 完成 {@code expired} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault expired() {
        return new ReportFault("SNAPSHOT_EXPIRED", "报表运行或导出任务已到期", 410);
    }

    /**
     * 完成 {@code missing} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault missing() {
        return new ReportFault("NOT_FOUND", "报表资源不存在或不可访问", 404);
    }

    /**
     * 完成 {@code conflict} 对应的只读取数报表存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault conflict() {
        return new ReportFault("REVISION_CONFLICT", "内容或请求已变化，请重新读取后操作", 409);
    }
}
