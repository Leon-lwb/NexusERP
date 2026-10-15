package com.nexus.report.storage;

import com.nexus.report.domain.ReportFault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** 产物清单和全局/租户硬配额在主库同一事务中锁定，不依赖节点内存计数。 */
public final class ReportQuotaService {
    /**
     * 已保留容量包含暂存、上传和删除未确认的全部对象。
     *
     * @param bytes 保留或实际占用的字节数
     * @param objects 已保留的业务对象数量
     * @param byteLimit 字节容量上限
     * @param objectLimit 对象数量上限
     */
    public record Usage(long bytes, long objects, long byteLimit, long objectLimit) {}

    /**
     * 清单只在内部使用，暂存路径不返回普通业务接口。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param state 任务或资源状态
     * @param reserved 已保留的字节容量
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     * @param owner 资源创建者或当前尝试持有者
     * @param path 本节点私有暂存路径
     * @param lease 当前尝试的租约截止时刻
     * @param updated 最后状态变更时刻
     */
    public record Manifest(
            String tenant,
            String store,
            String reference,
            String state,
            long reserved,
            long length,
            String checksum,
            String owner,
            String path,
            Instant lease,
            Instant updated) {}

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前transaction状态或配置。 */
    private final TransactionTemplate transaction;

    /** 保存当前globalBytes状态或配置。 */
    private final long globalBytes, tenantBytes, globalObjects, tenantObjects;

    /**
     * 恢复游标不被长期引用的旧对象挡住。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     */
    private record Cursor(String tenant, String reference) {}

    /** 保存当前cursors状态或配置。 */
    private final Map<String, Cursor> cursors = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 固定节点容量配置，首次使用后节点之间必须一致。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param globalBytes 当前契约的globalBytes配置或状态
     * @param tenantBytes 当前契约的tenantBytes配置或状态
     * @param globalObjects 当前契约的globalObjects配置或状态
     * @param tenantObjects 当前契约的tenantObjects配置或状态
     */
    public ReportQuotaService(
            JdbcTemplate jdbc,
            long globalBytes,
            long tenantBytes,
            long globalObjects,
            long tenantObjects) {
        if (tenantBytes < 1
                || globalBytes < tenantBytes
                || tenantObjects < 1
                || globalObjects < tenantObjects) throw new IllegalArgumentException("配额范围无效");
        this.jdbc = jdbc;
        this.globalBytes = globalBytes;
        this.tenantBytes = tenantBytes;
        this.globalObjects = globalObjects;
        this.tenantObjects = tenantObjects;
        transaction =
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
    }

    /**
     * 使用统一事务并保持GLOBAL→TENANT→MANIFEST的锁顺序。
     *
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    private <T> T atomic(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    /**
     * 先保留容量再产生物理内容，重复引用不能换持有者或语义。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param bytes 保留或实际占用的字节数
     * @param state 任务或资源状态
     * @param owner 资源创建者或当前尝试持有者
     * @param path 本节点私有暂存路径
     */
    public void reserve(
            String tenant,
            String store,
            String reference,
            long bytes,
            String state,
            String owner,
            String path) {
        validate(tenant, store, reference);
        if (bytes < 0
                || owner == null
                || owner.length() > 100
                || !Set.of("STAGING", "COMMITTING", "MATERIALIZED", "ADOPTING").contains(state))
            throw ReportFault.invalid("产物保留参数无效");
        atomic(
                () -> {
                    lock(tenant);
                    var prior = manifest(tenant, store, reference, true);
                    if (prior.isPresent()) {
                        var existing = prior.get();
                        if (!existing.owner().equals(owner)
                                || !existing.state().equals(state)
                                || existing.reserved() != bytes) throw conflict();
                        return null;
                    }
                    adjust(tenant, bytes, 1);
                    Instant now = now();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_artifact_manifest(tenant_id,store_id,reference,state,reserved_bytes,lease_owner,staging_path,lease_until,update_time)"
                                + " VALUES(?,?,?,?,?,?,?,?,?)",
                            tenant,
                            store,
                            reference,
                            state,
                            bytes,
                            owner,
                            path,
                            Timestamp.from(now.plusSeconds(300)),
                            Timestamp.from(now));
                    return null;
                });
    }

    /**
     * 先预留数量后才能创建物理暂存，路径登记只允许当前持有者。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @param path 本节点私有暂存路径
     */
    public void path(String tenant, String store, String reference, String owner, String path) {
        atomic(
                () -> {
                    lock(tenant);
                    owned(tenant, store, reference, owner);
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET staging_path=? WHERE tenant_id=? AND"
                                    + " store_id=? AND reference=?",
                            path,
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 装配纳管时检查同一节点已有暂存路径，避免重复计数。
     *
     * @param store 逻辑存储标识
     * @param path 本节点私有暂存路径
     * @param ownerPrefix 当前契约的ownerPrefix配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean pathRegistered(String store, String path, String ownerPrefix) {
        if (ownerPrefix == null)
            return !jdbc.queryForList(
                            "SELECT reference FROM rpt_artifact_manifest WHERE store_id=? AND"
                                    + " staging_path=? LIMIT 1",
                            store,
                            path)
                    .isEmpty();
        return !jdbc.queryForList(
                        "SELECT reference FROM rpt_artifact_manifest WHERE store_id=? AND"
                                + " staging_path=? AND SUBSTRING(lease_owner,1,?)=? LIMIT 1",
                        store,
                        path,
                        ownerPrefix.length() + 1,
                        ownerPrefix + "_")
                .isEmpty();
    }

    /**
     * 使用资源时延长清单租约，删除围栏已占有的对象不可重新复用。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param seconds 当前契约的seconds配置或状态
     */
    public void pin(String tenant, String store, String reference, int seconds) {
        if (seconds < 1 || seconds > 86400) throw ReportFault.invalid("资源使用窗口无效");
        atomic(
                () -> {
                    lock(tenant);
                    var current =
                            manifest(tenant, store, reference, true)
                                    .orElseThrow(ReportQuotaService::conflict);
                    if (!current.state().equals("STORED")) throw conflict();
                    Instant until = now().plusSeconds(seconds);
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET lease_until=GREATEST(lease_until,?)"
                                    + " WHERE tenant_id=? AND store_id=? AND reference=?",
                            Timestamp.from(until),
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 仅在短事务中验证提交权；物理存储的一次性引用围栏负责拒绝迟到提交。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    public <T> T commitGuard(
            String tenant, String store, String reference, String owner, Supplier<T> action) {
        atomic(
                () -> {
                    lock(tenant);
                    var current = owned(tenant, store, reference, owner);
                    if (!current.state().equals("COMMITTING")) throw conflict();
                    return null;
                });
        return action.get();
    }

    /**
     * 删除意图先持久提交，再执行物理封存；数据库失联不会把对象恢复为可读取状态。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param remove 当前契约的remove配置或状态
     */
    public void removeGuard(String tenant, String store, String reference, Runnable remove) {
        boolean selected =
                atomic(
                        () -> {
                            lock(tenant);
                            var current = manifest(tenant, store, reference, true);
                            if (current.isEmpty()) return false;
                            markDelete(tenant, store, reference);
                            return true;
                        });
        if (!selected) return;
        remove.run();
        confirmDelete(tenant, store, reference);
    }

    /**
     * 过期提交先转入不可逆删除状态；物理删除封住引用后才释放容量。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param olderThan 当前契约的olderThan配置或状态
     * @param remove 当前契约的remove配置或状态
     */
    public void interruptedGuard(
            String tenant, String store, String reference, Instant olderThan, Runnable remove) {
        boolean selected =
                atomic(
                        () -> {
                            lock(tenant);
                            var current = manifest(tenant, store, reference, true);
                            if (current.isEmpty()
                                    || !Set.of("COMMITTING", "DELETE_PENDING")
                                            .contains(current.get().state())
                                    || !current.get().state().equals("DELETE_PENDING")
                                            && (current.get().lease().isAfter(now())
                                                    || !current.get()
                                                            .updated()
                                                            .isBefore(olderThan))) return false;
                            markDelete(tenant, store, reference);
                            return true;
                        });
        if (!selected) return;
        remove.run();
        confirmDelete(tenant, store, reference);
    }

    /**
     * 在同一短事务复核字体租约与引用，提交删除意图后拒绝任何新的pin。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     * @param remove 当前契约的remove配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean reapGuard(
            String tenant,
            String store,
            String reference,
            Instant olderThan,
            java.util.function.BooleanSupplier referenced,
            Runnable remove) {
        boolean selected =
                atomic(
                        () -> {
                            lock(tenant);
                            var current = manifest(tenant, store, reference, true);
                            if (current.isEmpty()
                                    || !current.get().state().equals("STORED")
                                    || !current.get().updated().isBefore(olderThan)
                                    || current.get().lease().isAfter(now())
                                    || referenced.getAsBoolean()) return false;
                            markDelete(tenant, store, reference);
                            return true;
                        });
        if (!selected) return false;
        remove.run();
        confirmDelete(tenant, store, reference);
        return true;
    }

    /**
     * 原节点清理暂存前重新锁定租约和状态，提交删除意图后才执行路径封存。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param node 当前契约的node配置或状态
     * @param olderThan 当前契约的olderThan配置或状态
     * @param remove 当前契约的remove配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean stagingGuard(
            String tenant,
            String store,
            String reference,
            String node,
            Instant olderThan,
            Runnable remove) {
        boolean selected =
                atomic(
                        () -> {
                            lock(tenant);
                            var current = manifest(tenant, store, reference, true);
                            if (current.isEmpty()
                                    || current.get().path() == null
                                    || !current.get().owner().startsWith(node + "_")
                                    || !Set.of(
                                                    "STAGING",
                                                    "STAGED",
                                                    "MATERIALIZED",
                                                    "DELETE_PENDING")
                                            .contains(current.get().state())) return false;
                            if (!current.get().state().equals("DELETE_PENDING")
                                    && (current.get().lease().isAfter(now())
                                            || !current.get().updated().isBefore(olderThan)))
                                return false;
                            markDelete(tenant, store, reference);
                            return true;
                        });
        if (!selected) return false;
        remove.run();
        confirmDelete(tenant, store, reference);
        return true;
    }

    /**
     * 未登记回收与纳管争用同一清单键，先计入实际内容再持久提交删除意图。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     * @param node 当前契约的node配置或状态
     * @param referenced 当前契约的referenced配置或状态
     * @return 当前受控处理的固定结果
     */
    public boolean claimUntrackedDelete(
            String tenant,
            String store,
            String reference,
            long length,
            String checksum,
            String node,
            java.util.function.BooleanSupplier referenced) {
        validate(tenant, store, reference);
        if (length < 0 || checksum == null || !checksum.matches("[a-f0-9]{64}"))
            throw ReportFault.invalid("未登记对象元数据无效");
        return atomic(
                () -> {
                    lock(tenant);
                    if (manifest(tenant, store, reference, true).isPresent()
                            || referenced.getAsBoolean()) return false;
                    adjust(tenant, length, 1);
                    Instant now = now();
                    jdbc.update(
                            "INSERT INTO"
                                + " rpt_artifact_manifest(tenant_id,store_id,reference,state,reserved_bytes,content_length,checksum,lease_owner,lease_until,update_time)"
                                + " VALUES(?,?,?,'DELETE_PENDING',?,?,?,?,?,?)",
                            tenant,
                            store,
                            reference,
                            length,
                            length,
                            checksum,
                            "gc_" + node,
                            Timestamp.from(now),
                            Timestamp.from(now));
                    return true;
                });
    }

    /**
     * 写入前扩大容量保留，并延长当前持有者的暂存租约。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @param bytes 保留或实际占用的字节数
     */
    public void grow(String tenant, String store, String reference, String owner, long bytes) {
        if (bytes < 0) throw ReportFault.invalid("增量容量不能为负");
        atomic(
                () -> {
                    lock(tenant);
                    var current = owned(tenant, store, reference, owner);
                    if (!Set.of("STAGING", "MATERIALIZED", "COMMITTING").contains(current.state()))
                        throw conflict();
                    adjust(tenant, bytes, 0);
                    Instant now = now();
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET"
                                    + " reserved_bytes=reserved_bytes+?,lease_until=?,update_time=?"
                                    + " WHERE tenant_id=? AND store_id=? AND reference=?",
                            bytes,
                            Timestamp.from(now.plusSeconds(300)),
                            Timestamp.from(now),
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 关闭暂存流后记录真实长度，仍保留未确认删除内容的容量。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @param length 实际内容长度
     */
    public void staged(String tenant, String store, String reference, String owner, long length) {
        atomic(
                () -> {
                    lock(tenant);
                    var current = owned(tenant, store, reference, owner);
                    if (!current.state().equals("STAGING")
                            || length < 0
                            || length > current.reserved()) throw conflict();
                    adjust(tenant, length - current.reserved(), 0);
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET"
                                + " state='STAGED',reserved_bytes=?,content_length=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND store_id=? AND reference=?",
                            length,
                            length,
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 上传成功且checksum已经验证后才能登记为共享对象。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     */
    public void stored(
            String tenant,
            String store,
            String reference,
            String owner,
            long length,
            String checksum) {
        if (checksum == null || !checksum.matches("[a-f0-9]{64}"))
            throw ReportFault.invalid("产物checksum无效");
        atomic(
                () -> {
                    lock(tenant);
                    var current = owned(tenant, store, reference, owner);
                    if (!current.state().equals("COMMITTING") || length != current.reserved())
                        throw conflict();
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET"
                                + " state='STORED',content_length=?,checksum=?,staging_path=NULL,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND store_id=? AND reference=?",
                            length,
                            checksum,
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 完成既有对象纳管；该状态与新上传区分，网络中断后的重启可以再次确认元数据。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     */
    public void adopted(
            String tenant, String store, String reference, long length, String checksum) {
        if (checksum == null || !checksum.matches("[a-f0-9]{64}"))
            throw ReportFault.invalid("纳管校验和无效");
        atomic(
                () -> {
                    lock(tenant);
                    var current =
                            manifest(tenant, store, reference, true)
                                    .orElseThrow(ReportQuotaService::conflict);
                    if (current.state().equals("STORED")
                            && current.length() == length
                            && Objects.equals(current.checksum(), checksum)) return null;
                    if (!current.state().equals("ADOPTING") || current.reserved() != length)
                        throw conflict();
                    jdbc.update(
                            "UPDATE rpt_artifact_manifest SET"
                                + " state='STORED',content_length=?,checksum=?,staging_path=NULL,lease_until=?,update_time=CURRENT_TIMESTAMP"
                                + " WHERE tenant_id=? AND store_id=? AND reference=?",
                            length,
                            checksum,
                            Timestamp.from(now()),
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 提交前确认暂存清单仍属于本尝试，过期尝试不能重新提交。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @return 当前受控处理的固定结果
     */
    public Manifest staged(String tenant, String store, String reference, String owner) {
        return atomic(
                () -> {
                    lock(tenant);
                    var current = owned(tenant, store, reference, owner);
                    if (!current.state().equals("STAGED")) throw conflict();
                    return current;
                });
    }

    /**
     * 标记删除但继续占用容量，物理删除失败可重试。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     */
    public void markDelete(String tenant, String store, String reference) {
        atomic(
                () -> {
                    lock(tenant);
                    var current = manifest(tenant, store, reference, true);
                    if (current.isPresent())
                        jdbc.update(
                                "UPDATE rpt_artifact_manifest SET"
                                    + " state='DELETE_PENDING',update_time=CURRENT_TIMESTAMP WHERE"
                                    + " tenant_id=? AND store_id=? AND reference=?",
                                tenant,
                                store,
                                reference);
                    return null;
                });
    }

    /**
     * 只能在物理删除被确认后释放；重复确认不会再次减计数。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     */
    public void confirmDelete(String tenant, String store, String reference) {
        atomic(
                () -> {
                    lock(tenant);
                    var current = manifest(tenant, store, reference, true);
                    if (current.isEmpty()) return null;
                    if (!current.get().state().equals("DELETE_PENDING")) throw conflict();
                    adjust(tenant, -current.get().reserved(), -1);
                    jdbc.update(
                            "DELETE FROM rpt_artifact_manifest WHERE tenant_id=? AND store_id=? AND"
                                    + " reference=?",
                            tenant,
                            store,
                            reference);
                    return null;
                });
    }

    /**
     * 返回内部清单，供当前节点和恢复游标核对对象状态。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Optional<Manifest> manifest(String tenant, String store, String reference) {
        return manifest(tenant, store, reference, false);
    }

    /**
     * 在需要修改时追加行锁；所有SQL值都使用绑定参数。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param lock 当前契约的lock配置或状态
     * @return 当前受控处理的固定结果
     */
    private Optional<Manifest> manifest(
            String tenant, String store, String reference, boolean lock) {
        return jdbc
                .query(
                        "SELECT * FROM rpt_artifact_manifest WHERE tenant_id=? AND store_id=? AND"
                                + " reference=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (r, i) ->
                                new Manifest(
                                        r.getString("tenant_id"),
                                        r.getString("store_id"),
                                        r.getString("reference"),
                                        r.getString("state"),
                                        r.getLong("reserved_bytes"),
                                        r.getLong("content_length"),
                                        r.getString("checksum"),
                                        r.getString("lease_owner"),
                                        r.getString("staging_path"),
                                        r.getTimestamp("lease_until").toInstant(),
                                        r.getTimestamp("update_time").toInstant()),
                        tenant,
                        store,
                        reference)
                .stream()
                .findFirst();
    }

    /**
     * 跨节点恢复使用有界批量，清单始终保留删除失败对象。
     *
     * @param store 逻辑存储标识
     * @param limit 当前操作的容量上限
     * @return 当前受控处理的固定结果
     */
    public synchronized List<Manifest> candidates(String store, int limit) {
        if (limit < 1 || limit > 1000) throw ReportFault.invalid("恢复批次范围无效");
        var cursor = cursors.get(store);
        String sql = "SELECT * FROM rpt_artifact_manifest WHERE store_id=?";
        var values = new ArrayList<Object>();
        values.add(store);
        if (cursor != null) {
            sql += " AND (tenant_id>? OR tenant_id=? AND reference>?)";
            values.add(cursor.tenant());
            values.add(cursor.tenant());
            values.add(cursor.reference());
        }
        sql += " ORDER BY tenant_id,reference LIMIT ?";
        values.add(limit);
        var result =
                jdbc.query(
                        sql,
                        (r, i) ->
                                new Manifest(
                                        r.getString("tenant_id"),
                                        r.getString("store_id"),
                                        r.getString("reference"),
                                        r.getString("state"),
                                        r.getLong("reserved_bytes"),
                                        r.getLong("content_length"),
                                        r.getString("checksum"),
                                        r.getString("lease_owner"),
                                        r.getString("staging_path"),
                                        r.getTimestamp("lease_until").toInstant(),
                                        r.getTimestamp("update_time").toInstant()),
                        values.toArray());
        if (result.isEmpty() || result.size() < limit) cursors.remove(store);
        else {
            var last = result.get(result.size() - 1);
            cursors.put(store, new Cursor(last.tenant(), last.reference()));
        }
        return result;
    }

    /**
     * 运行面板查询明确的已保留值，空租户表示全局汇总。
     *
     * @param tenant 租户隔离标识
     * @return 当前受控处理的固定结果
     */
    public Usage usage(String tenant) {
        String key = tenant == null ? "GLOBAL" : "TENANT:" + tenant;
        return jdbc
                .query(
                        "SELECT * FROM rpt_quota WHERE scope_key=?",
                        (r, i) ->
                                new Usage(
                                        r.getLong("used_bytes"),
                                        r.getLong("object_count"),
                                        r.getLong("limit_bytes"),
                                        r.getLong("limit_objects")),
                        key)
                .stream()
                .findFirst()
                .orElse(
                        new Usage(
                                0,
                                0,
                                tenant == null ? globalBytes : tenantBytes,
                                tenant == null ? globalObjects : tenantObjects));
    }

    /**
     * 创建配额记录后固定次序锁定，配置不一致时明确拒绝。
     *
     * @param tenant 租户隔离标识
     */
    private void lock(String tenant) {
        scope("GLOBAL", globalBytes, globalObjects);
        scope("TENANT:" + tenant, tenantBytes, tenantObjects);
    }

    /**
     * 锁住硬上限本身，不能用不同节点配置扩大同一主库的预算。
     *
     * @param key 当前契约的key配置或状态
     * @param bytes 保留或实际占用的字节数
     * @param objects 已保留的业务对象数量
     */
    private void scope(String key, long bytes, long objects) {
        jdbc.update(
                "INSERT IGNORE INTO rpt_quota(scope_key,limit_bytes,limit_objects) VALUES(?,?,?)",
                key,
                bytes,
                objects);
        var row = jdbc.queryForMap("SELECT * FROM rpt_quota WHERE scope_key=? FOR UPDATE", key);
        if (((Number) row.get("limit_bytes")).longValue() != bytes
                || ((Number) row.get("limit_objects")).longValue() != objects)
            throw new ReportFault("QUOTA_CONFIG_MISMATCH", "节点配额配置不一致，请先核对中央配额", 409);
    }

    /**
     * 全局和租户同事务增减，任何一层超限都会回滚全部改变。
     *
     * @param tenant 租户隔离标识
     * @param bytes 保留或实际占用的字节数
     * @param objects 已保留的业务对象数量
     */
    private void adjust(String tenant, long bytes, long objects) {
        for (String key : List.of("GLOBAL", "TENANT:" + tenant)) {
            var row = jdbc.queryForMap("SELECT * FROM rpt_quota WHERE scope_key=?", key);
            long nextBytes, nextObjects;
            try {
                nextBytes = Math.addExact(((Number) row.get("used_bytes")).longValue(), bytes);
                nextObjects =
                        Math.addExact(((Number) row.get("object_count")).longValue(), objects);
            } catch (ArithmeticException overflow) {
                throw ReportFault.capacity();
            }
            if (nextBytes < 0 || nextObjects < 0) throw conflict();
            if (nextBytes > ((Number) row.get("limit_bytes")).longValue()
                    || nextObjects > ((Number) row.get("limit_objects")).longValue())
                throw new ReportFault("ARTIFACT_QUOTA_EXCEEDED", "报表硬配额不足，等待清理完成或调整中央配额", 429);
            jdbc.update(
                    "UPDATE rpt_quota SET used_bytes=?,object_count=? WHERE scope_key=?",
                    nextBytes,
                    nextObjects,
                    key);
        }
    }

    /**
     * 只允许未过期且匹配持有者的尝试修改清单。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param owner 资源创建者或当前尝试持有者
     * @return 当前受控处理的固定结果
     */
    private Manifest owned(String tenant, String store, String reference, String owner) {
        var current =
                manifest(tenant, store, reference, true).orElseThrow(ReportQuotaService::conflict);
        if (!current.owner().equals(owner) || !current.lease().isAfter(now())) throw conflict();
        return current;
    }

    /**
     * 使用数据库时间避免节点时钟差影响租约。
     *
     * @return 当前受控处理的固定结果
     */
    private Instant now() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class).toInstant();
    }

    /**
     * 内部引用仍要求有界标识，不能含路径跳转。
     *
     * @param tenant 租户隔离标识
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     */
    private static void validate(String tenant, String store, String reference) {
        if (tenant == null
                || !tenant.matches("[A-Za-z0-9_-]{1,20}")
                || store == null
                || !store.matches("[A-Za-z0-9_-]{1,64}")
                || reference == null
                || !reference.matches("[A-Za-z0-9_.-]{1,80}"))
            throw ReportFault.invalid("产物命名空间无效");
    }

    /**
     * 提交围栏和配额状态不匹配时拒绝旧尝试。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault conflict() {
        return new ReportFault("ARTIFACT_LEASE_LOST", "产物清单状态或租约已变化", 409);
    }
}
