package com.nexus.report.extension;

import cn.hutool.core.util.IdUtil;

import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.jdbc.core.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.*;

/** 报表扩展资源的租户化草稿和不可变发布，物理表由固定类别映射。 */
public class ReportResourceStore {
    /**
     * 本次加载的草稿元数据，不将来源版本自动替换为最新。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param json 固定版本的契约正文
     * @param revision 当前契约的revision配置或状态
     * @param publishedVersion 当前契约的publishedVersion配置或状态
     * @param status 当前契约的status配置或状态
     * @param owner 资源创建者或当前尝试持有者
     */
    public record Draft(
            String id,
            String tenant,
            String code,
            String title,
            String json,
            long revision,
            String publishedVersion,
            String status,
            long owner) {}

    /**
     * 不可变资源发布，依赖绑定在同一事务中冻结。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param resourceId 当前契约的resourceId配置或状态
     * @param number 当前契约的number配置或状态
     * @param json 固定版本的契约正文
     * @param dependency 当前契约的dependency配置或状态
     * @param checksum 实际内容的SHA-256校验和
     * @param owner 资源创建者或当前尝试持有者
     */
    public record Version(
            String id,
            String resourceId,
            int number,
            String json,
            String dependency,
            String checksum,
            long owner) {}

    /**
     * 定义Family的自有契约及处理职责。
     *
     * @param table 当前契约的table配置或状态
     * @param key 当前契约的key配置或状态
     * @param versionTable 当前契约的versionTable配置或状态
     */
    private record Family(String table, String key, String versionTable) {}

    /** 保存当前FAMILIES状态或配置。 */
    private static final Map<String, Family> FAMILIES =
            Map.of(
                    "ASSET",
                    new Family("rpt_asset", "asset_id", "rpt_asset_version"),
                    "BATCH",
                    new Family("rpt_batch", "batch_id", "rpt_batch_version"),
                    "QUERY",
                    new Family("rpt_query_definition", "query_id", "rpt_query_version"),
                    "DASHBOARD",
                    new Family("rpt_dashboard", "dashboard_id", "rpt_dashboard_version"),
                    "SUBSCRIPTION",
                    new Family("rpt_subscription", "subscription_id", "rpt_subscription_version"));

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前transaction状态或配置。 */
    private final TransactionTemplate transaction;

    /** 保存当前acl状态或配置。 */
    private final JdbcReportStore acl;

    /**
     * 构造隔离验证存储，仅资源所有者可以访问。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param manager 当前契约的manager配置或状态
     */
    public ReportResourceStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, manager, null);
    }

    /**
     * 构造生产存储并复用正常资源ACL。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param manager 当前契约的manager配置或状态
     * @param acl 当前契约的acl配置或状态
     */
    public ReportResourceStore(
            JdbcTemplate jdbc, PlatformTransactionManager manager, JdbcReportStore acl) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
        this.acl = acl;
    }

    /**
     * 通过固定类别选择物理表，拒绝客户端传入表名。
     *
     * @param type 受控类型
     * @return 当前受控处理的固定结果
     */
    private static Family family(String type) {
        var value = FAMILIES.get(type);
        if (value == null) throw ReportFault.invalid("扩展资源类型无效");
        return value;
    }

    /**
     * 校验雪花标识并保留字符串传输。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    private static long number(String id) {
        try {
            if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ReportFault.invalid("资源标识无效");
            return Long.parseLong(id);
        } catch (NumberFormatException invalid) {
            throw ReportFault.invalid("资源标识无效");
        }
    }

    /**
     * 同一数据源事务，可同时登记数据集与查询依赖。
     *
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    public <T> T atomic(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    /**
     * 读取当前租户资源，调用方必须继续检查平台和资源操作权限。
     *
     * @param type 受控类型
     * @param tenant 租户隔离标识
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public Optional<Draft> read(String type, String tenant, String id) {
        var f = family(type);
        return jdbc
                .query(
                        "SELECT * FROM " + f.table() + " WHERE tenant_id=? AND " + f.key() + "=?",
                        (r, i) ->
                                new Draft(
                                        Long.toString(r.getLong(f.key())),
                                        r.getString("tenant_id"),
                                        r.getString("code"),
                                        r.getString("name"),
                                        r.getString("definition_json"),
                                        r.getLong("revision"),
                                        r.getObject("published_version_id") == null
                                                ? null
                                                : Long.toString(r.getLong("published_version_id")),
                                        r.getString("status"),
                                        r.getLong("create_by")),
                        tenant,
                        number(id))
                .stream()
                .findFirst();
    }

    /**
     * 仅返回授权资源，归档资源不进入列表。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<Draft> list(String type, ReportActor actor) {
        var f = family(type);
        return jdbc
                .queryForList(
                        "SELECT "
                                + f.key()
                                + " FROM "
                                + f.table()
                                + " WHERE tenant_id=? AND status<>'ARCHIVED' ORDER BY update_time"
                                + " DESC,"
                                + f.key()
                                + " DESC",
                        actor.tenantId())
                .stream()
                .map(row -> read(type, actor.tenantId(), row.get(f.key()).toString()).orElseThrow())
                .filter(d -> allowed(type, actor, d, "VIEW"))
                .toList();
    }

    /**
     * 统一检查扩展资源所有权、ACL与归档状态。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    public Draft access(String type, ReportActor actor, String id, String action) {
        var draft =
                read(type, actor.tenantId(), id)
                        .orElseThrow(() -> new ReportFault("NOT_FOUND", "扩展资源不存在", 404));
        if (draft.status().equals("ARCHIVED") || !allowed(type, actor, draft, action))
            throw ReportFault.denied();
        return draft;
    }

    /**
     * 不复用创建者身份代替当前读取者授权。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param draft 当前契约的draft配置或状态
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    private boolean allowed(String type, ReportActor actor, Draft draft, String action) {
        return acl == null
                ? actor.superAdmin() || draft.owner() == actor.userId()
                : acl.allowed(actor, type, draft.id(), draft.owner(), action);
    }

    /**
     * 创建本租户草稿，业务定义由对应服务先做严格结构检查。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param json 固定版本的契约正文
     * @return 当前受控处理的固定结果
     */
    public Draft create(String type, ReportActor actor, String code, String title, String json) {
        var f = family(type);
        if (code == null
                || !code.matches("[A-Z][A-Z0-9_]{0,63}")
                || title == null
                || title.isBlank()
                || title.length() > 100
                || json == null
                || json.length() > 2_000_000) throw ReportFault.invalid("资源编码、标题或定义长度无效");
        String id = IdUtil.getSnowflakeNextIdStr();
        jdbc.update(
                "INSERT INTO "
                        + f.table()
                        + "("
                        + f.key()
                        + ",tenant_id,code,name,definition_json,revision,status,create_dept,create_by)"
                        + " VALUES(?,?,?,?,?,1,'DRAFT',?,?)",
                number(id),
                actor.tenantId(),
                code,
                title,
                json,
                actor.departmentId(),
                actor.userId());
        return read(type, actor.tenantId(), id).orElseThrow();
    }

    /**
     * 通过预期修订避免覆盖其他编辑者，已发布版本保持不变。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param json 固定版本的契约正文
     * @param title 当前契约的title配置或状态
     * @return 当前受控处理的固定结果
     */
    public Draft save(
            String type, ReportActor actor, String id, long revision, String json, String title) {
        var f = family(type);
        if (json == null
                || json.length() > 2_000_000
                || title == null
                || title.isBlank()
                || title.length() > 100) throw ReportFault.invalid("资源定义或标题无效");
        return atomic(
                () -> {
                    lock(f, actor.tenantId(), id);
                    access(type, actor, id, "EDIT");
                    if (jdbc.update(
                                    "UPDATE "
                                            + f.table()
                                            + " SET definition_json=?,name=?,revision=revision+1,update_time=CURRENT_TIMESTAMP"
                                            + " WHERE tenant_id=? AND "
                                            + f.key()
                                            + "=? AND revision=? AND status<>'ARCHIVED'",
                                    json,
                                    title,
                                    actor.tenantId(),
                                    number(id),
                                    revision)
                            != 1) throw conflict();
                    return read(type, actor.tenantId(), id).orElseThrow();
                });
    }

    /**
     * 将依赖文本同发布定义一起冻结。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param dependency 当前契约的dependency配置或状态
     * @return 当前受控处理的固定结果
     */
    public Version publish(
            String type, ReportActor actor, String id, long revision, String dependency) {
        return publish(type, actor, id, revision, (draft, number) -> dependency, () -> {});
    }

    /**
     * 在锁内再次检查当前授权和依赖，创建不可变发布版本。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param prepare 当前契约的prepare配置或状态
     * @param recheck 当前契约的recheck配置或状态
     * @return 当前受控处理的固定结果
     */
    public Version publish(
            String type,
            ReportActor actor,
            String id,
            long revision,
            BiFunction<Draft, Integer, String> prepare,
            Runnable recheck) {
        var f = family(type);
        return atomic(
                () -> {
                    lock(f, actor.tenantId(), id);
                    var draft = access(type, actor, id, "PUBLISH");
                    if (draft.revision() != revision || draft.status().equals("OFFLINE"))
                        throw conflict();
                    recheck.run();
                    Integer max =
                            jdbc.queryForObject(
                                    "SELECT COALESCE(MAX(version_no),0) FROM "
                                            + f.versionTable()
                                            + " WHERE tenant_id=? AND "
                                            + f.key()
                                            + "=?",
                                    Integer.class,
                                    actor.tenantId(),
                                    number(id));
                    int next = (max == null ? 0 : max) + 1;
                    String dependency = prepare.apply(draft, next);
                    if (dependency == null || dependency.length() > 2_000_000)
                        throw ReportFault.capacity();
                    String versionId = IdUtil.getSnowflakeNextIdStr();
                    String checksum =
                            ReportDefinitionService.hash(draft.json() + "\n" + dependency);
                    jdbc.update(
                            "INSERT INTO "
                                    + f.versionTable()
                                    + "(version_id,"
                                    + f.key()
                                    + ",tenant_id,version_no,definition_json,dependency_json,checksum,create_by)"
                                    + " VALUES(?,?,?,?,?,?,?,?)",
                            number(versionId),
                            number(id),
                            actor.tenantId(),
                            next,
                            draft.json(),
                            dependency,
                            checksum,
                            actor.userId());
                    jdbc.update(
                            "UPDATE "
                                    + f.table()
                                    + " SET published_version_id=?,status='PUBLISHED',revision=revision+1,update_time=CURRENT_TIMESTAMP"
                                    + " WHERE tenant_id=? AND "
                                    + f.key()
                                    + "=?",
                            number(versionId),
                            actor.tenantId(),
                            number(id));
                    return version(type, actor.tenantId(), versionId).orElseThrow();
                });
    }

    /**
     * 读取精确发布版本并检查正文与依赖完整性，不查询最新指针。
     *
     * @param type 受控类型
     * @param tenant 租户隔离标识
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public Optional<Version> version(String type, String tenant, String id) {
        var f = family(type);
        return jdbc
                .query(
                        "SELECT * FROM " + f.versionTable() + " WHERE tenant_id=? AND version_id=?",
                        (r, i) -> {
                            var value =
                                    new Version(
                                            Long.toString(r.getLong("version_id")),
                                            Long.toString(r.getLong(f.key())),
                                            r.getInt("version_no"),
                                            r.getString("definition_json"),
                                            r.getString("dependency_json"),
                                            r.getString("checksum"),
                                            r.getLong("create_by"));
                            if (!ReportDefinitionService.hash(
                                            value.json() + "\n" + value.dependency())
                                    .equals(value.checksum()))
                                throw new ReportFault("VERSION_INVALID", "资源发布完整性校验失败", 409);
                            return value;
                        },
                        tenant,
                        number(id))
                .stream()
                .findFirst();
    }

    /**
     * 列出所属资源的固定版本，调用方仍需资源及业务权限检查。
     *
     * @param type 受控类型
     * @param tenant 租户隔离标识
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public List<Version> versions(String type, String tenant, String id) {
        var f = family(type);
        return jdbc
                .queryForList(
                        "SELECT version_id FROM "
                                + f.versionTable()
                                + " WHERE tenant_id=? AND "
                                + f.key()
                                + "=? ORDER BY version_no DESC",
                        tenant,
                        number(id))
                .stream()
                .map(r -> version(type, tenant, r.get("version_id").toString()).orElseThrow())
                .toList();
    }

    /**
     * 显式改变生命周期，归档不可重新开启。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     * @return 当前受控处理的固定结果
     */
    public Draft state(String type, ReportActor actor, String id, long revision, String state) {
        if (!Set.of("OFFLINE", "PUBLISHED", "ARCHIVED").contains(state))
            throw ReportFault.invalid("扩展资源状态无效");
        var f = family(type);
        return atomic(
                () -> {
                    lock(f, actor.tenantId(), id);
                    var current = access(type, actor, id, "PUBLISH");
                    if (current.revision() != revision
                            || state.equals("PUBLISHED") && current.publishedVersion() == null)
                        throw conflict();
                    jdbc.update(
                            "UPDATE "
                                    + f.table()
                                    + " SET status=?,revision=revision+1,update_time=CURRENT_TIMESTAMP"
                                    + " WHERE tenant_id=? AND "
                                    + f.key()
                                    + "=?",
                            state,
                            actor.tenantId(),
                            number(id));
                    return read(type, actor.tenantId(), id).orElseThrow();
                });
    }

    /** 在修改前锁定同租户记录，读不到不继续执行写入。 */
    /**
     * 私有资源引用有独立索引，回收器不靠模糊扫描定义文本。
     *
     * @param type 受控类型
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @param reference 私有对象的一次性引用
     */
    public void artifact(String type, ReportActor actor, String versionId, String reference) {
        jdbc.update(
                "INSERT INTO rpt_resource_artifact(tenant_id,resource_type,version_id,artifact_ref)"
                        + " VALUES(?,?,?,?)",
                actor.tenantId(),
                type,
                number(versionId),
                reference);
    }

    /**
     * 执行lock对应的受控处理。
     *
     * @param family 当前受控操作的family输入
     * @param tenant 当前受控操作的tenant输入
     * @param id 当前受控操作的id输入
     */
    private void lock(Family family, String tenant, String id) {
        if (jdbc.queryForList(
                        "SELECT "
                                + family.key()
                                + " FROM "
                                + family.table()
                                + " WHERE tenant_id=? AND "
                                + family.key()
                                + "=? FOR UPDATE",
                        tenant,
                        number(id))
                .isEmpty()) throw new ReportFault("NOT_FOUND", "扩展资源不存在", 404);
    }

    /**
     * 稳定修订冲突码，不将数据库异常原文返回接口。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault conflict() {
        return new ReportFault("REVISION_CONFLICT", "资源修订或状态已变化", 409);
    }
}
