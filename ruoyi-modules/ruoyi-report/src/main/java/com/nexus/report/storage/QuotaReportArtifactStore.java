package com.nexus.report.storage;

import com.nexus.report.domain.ReportFault;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/** 统一纳入暂存、物化和共享对象的硬配额，失败删除不会提前释放。 */
public final class QuotaReportArtifactStore implements ReportArtifactStore {
    /** 当前尝试的暂存引用，不能被其他实例冒认。 */
    private static final class Pending {
        /** 保存固定私有对象引用。 */
        final String reference;

        /** 保存当前opened状态或配置。 */
        boolean opened;

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param reference 当前受控操作的reference输入
         */
        Pending(String reference) {
            this.reference = reference;
        }
    }

    /** 保存当前私有存储委托。 */
    private final ReportArtifactStore delegate;

    /** 保存主库硬配额服务。 */
    private final ReportQuotaService quota;

    /** 保存当前maximum状态或配置。 */
    private final long maximum;

    /** 保存本尝试的持有者围栏。 */
    private final String owner, node;

    /** 保存当前ownCommits状态或配置。 */
    private final Set<String> ownCommits = ConcurrentHashMap.newKeySet();

    /** 保存当前pending状态或配置。 */
    private final Map<Path, Pending> pending = new ConcurrentHashMap<>();

    /**
     * 持有者标识附加启动令牌，旧进程不能更新新尝试。
     *
     * @param delegate 当前契约的delegate配置或状态
     * @param quota 当前契约的quota配置或状态
     * @param maximum 当前契约的maximum配置或状态
     * @param node 当前契约的node配置或状态
     */
    public QuotaReportArtifactStore(
            ReportArtifactStore delegate, ReportQuotaService quota, long maximum, String node) {
        this.delegate = delegate;
        this.quota = quota;
        this.maximum = maximum;
        this.node = node;
        owner = node + "_" + UUID.randomUUID();
        if (owner.length() > 100) throw new IllegalArgumentException("存储节点标识过长");
        adopt();
    }

    /**
     * 保留底层逻辑命名空间。
     *
     * @return 当前受控处理的固定结果
     */
    public String storeId() {
        return delegate.storeId();
    }

    /**
     * 创建零字节暂存之前后的数量都受主库预算约束。
     *
     * @param tenant 租户隔离标识
     * @param kind 当前契约的kind配置或状态
     * @return 当前受控处理的固定结果
     */
    public Path temporary(String tenant, String kind) {
        String reference = "stage_" + UUID.randomUUID();
        Path path = delegate.temporaryPath(tenant, kind, reference);
        quota.reserve(tenant, storeId(), reference, 0, "STAGING", owner, normal(path).toString());
        try {
            delegate.allocateTemporary(tenant, path);
            pending.put(normal(path), new Pending(reference));
            return path;
        } catch (RuntimeException failure) {
            quota.markDelete(tenant, storeId(), reference);
            try {
                delegate.discard(tenant, path);
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                    quota.confirmDelete(tenant, storeId(), reference);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * 正常写流在写入物理字节前保留预算。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     * @return 当前受控处理的固定结果
     */
    public OutputStream output(String tenant, Path path) {
        var token = owned(path);
        synchronized (token) {
            if (token.opened) throw new ReportFault("ARTIFACT_CONFLICT", "暂存输出流不能重复打开", 409);
            return track(tenant, path, delegate.output(tenant, path));
        }
    }

    /**
     * SXSSF等基础库的流也必须进入同一字节保留规则。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     * @param output 当前契约的output配置或状态
     * @return 当前受控处理的固定结果
     */
    public OutputStream track(String tenant, Path path, OutputStream output) {
        var token = owned(path);
        synchronized (token) {
            if (token.opened) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
                throw new ReportFault("ARTIFACT_CONFLICT", "暂存输出流不能重复打开", 409);
            }
            token.opened = true;
        }
        return new FilterOutputStream(output) {
            /** 保存当前written状态或配置。 */
            private long written, reserved, lastTouch = System.nanoTime();

            /** 保存当前closed状态或配置。 */
            private boolean closed;

            /**
             * 执行reserve对应的受控处理。
             *
             * @param length 当前受控操作的length输入
             */
            private void reserve(int length) {
                long next;
                try {
                    next = Math.addExact(written, length);
                } catch (ArithmeticException overflow) {
                    throw ReportFault.capacity();
                }
                if (next > maximum) throw ReportFault.capacity();
                if (next > reserved) {
                    long target = Math.min(maximum, ((next + 1048575) / 1048576) * 1048576);
                    quota.grow(tenant, storeId(), token.reference, owner, target - reserved);
                    reserved = target;
                    lastTouch = System.nanoTime();
                } else if (System.nanoTime() - lastTouch > 10_000_000_000L) {
                    quota.grow(tenant, storeId(), token.reference, owner, 0);
                    lastTouch = System.nanoTime();
                }
            }

            /**
             * 写入受控输出并保持长度及取消边界。
             *
             * @param value 当前受控操作的value输入
             */
            public void write(int value) throws IOException {
                reserve(1);
                out.write(value);
                written++;
            }

            /**
             * 写入受控输出并保持长度及取消边界。
             *
             * @param bytes 当前受控操作的bytes输入
             * @param offset 当前受控操作的offset输入
             * @param length 当前受控操作的length输入
             */
            public void write(byte[] bytes, int offset, int length) throws IOException {
                reserve(length);
                out.write(bytes, offset, length);
                written += length;
            }

            /** 释放本次受控资源并确认清理结果。 */
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    out.close();
                } finally {
                    quota.staged(tenant, storeId(), token.reference, owner, Files.size(path));
                }
            }
        };
    }

    /**
     * 共享上传与本机源文件会短暂同时存在，因此单独保留对象容量。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     * @param kind 当前契约的kind配置或状态
     * @return 当前受控处理的固定结果
     */
    public String commit(String tenant, Path path, String kind) {
        var token = owned(path);
        var source = quota.staged(tenant, storeId(), token.reference, owner);
        String reference = UUID.randomUUID() + "." + kind;
        boolean registered = false;
        try {
            quota.reserve(tenant, storeId(), reference, source.length(), "COMMITTING", owner, null);
            ownCommits.add(reference);
            quota.commitGuard(
                    tenant,
                    storeId(),
                    reference,
                    owner,
                    () -> {
                        String committed = delegate.commitAt(tenant, path, kind, reference);
                        if (!committed.equals(reference)) throw ReportFault.invalid("存储返回了错误预分配引用");
                        var info = delegate.info(tenant, reference);
                        if (info.length() != source.length())
                            throw ReportFault.invalid("提交长度与暂存不一致");
                        quota.stored(
                                tenant,
                                storeId(),
                                reference,
                                owner,
                                info.length(),
                                info.checksum());
                        return null;
                    });
            registered = true;
            ownCommits.remove(reference);
            discard(tenant, path);
            return reference;
        } catch (RuntimeException failure) {
            if (!registered) {
                try {
                    delete(tenant, reference);
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    /**
     * 清单包装自身产生唯一引用，不向调用方开放覆盖提交。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     * @param kind 当前契约的kind配置或状态
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public String commitAt(String tenant, Path path, String kind, String reference) {
        throw new ReportFault("ARTIFACT_STORE_UNSUPPORTED", "清单层必须使用受围栏保护的一次性提交", 409);
    }

    /**
     * 索引已固定块摘要，共享读取不必先下载整份快照。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param offset 当前契约的offset配置或状态
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     * @return 当前受控处理的固定结果
     */
    public InputStream range(
            String tenant, String reference, long offset, long length, String checksum) {
        var expected = ready(tenant, reference);
        verifyInfo(tenant, reference, expected);
        return delegate.range(tenant, reference, offset, length, checksum);
    }

    /**
     * 当前节点读本地文件无需复制，共享节点则显式物化并跟踪暂存。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Materialized materialize(String tenant, String reference) {
        var expected = ready(tenant, reference);
        verifyInfo(tenant, reference, expected);
        if (!delegate.requiresMaterialization()) return delegate.materialize(tenant, reference);
        Path temporary = temporary(tenant, "json");
        try (var input = delegate.open(tenant, reference);
                var output = output(tenant, temporary)) {
            var hash = sha();
            byte[] bytes = new byte[65536];
            long count = 0;
            int length;
            while ((length = input.read(bytes)) >= 0) {
                if (length == 0) continue;
                count += length;
                if (count > expected.length()) throw ReportFault.invalid("物化超过固定长度");
                hash.update(bytes, 0, length);
                output.write(bytes, 0, length);
            }
            if (count != expected.length()
                    || !HexFormat.of().formatHex(hash.digest()).equals(expected.checksum()))
                throw new ReportFault("ARTIFACT_INVALID", "物化checksum不一致", 409);
            return new Materialized(temporary, () -> discard(tenant, temporary));
        } catch (Exception failure) {
            discard(tenant, temporary);
            if (failure instanceof ReportFault fault) throw fault;
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "私有物化失败", 503);
        }
    }

    /**
     * 所有业务Path调用已改用materialize，此兼容入口交由有界恢复器清理。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Path read(String tenant, String reference) {
        return materialize(tenant, reference).path();
    }

    /**
     * 共享读取需要私有物化，默认open会关闭同一生命周期。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean requiresMaterialization() {
        return delegate.requiresMaterialization();
    }

    /**
     * 清单和实际底层长度及摘要必须一致。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Info info(String tenant, String reference) {
        var manifest = ready(tenant, reference);
        return verifyInfo(tenant, reference, manifest);
    }

    /**
     * 真正不存在才返回false，网络错误不能伪装为删除成功。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public boolean exists(String tenant, String reference) {
        var manifest = quota.manifest(tenant, storeId(), reference);
        return manifest.isPresent() && delegate.exists(tenant, reference);
    }

    /**
     * 物理删除失败时DELETE_PENDING继续保留字节与对象数量。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     */
    public void delete(String tenant, String reference) {
        var manifest = quota.manifest(tenant, storeId(), reference);
        if (manifest.isEmpty()) {
            if (ownCommits.contains(reference)) {
                delegate.delete(tenant, reference);
                ownCommits.remove(reference);
                return;
            }
            if (delegate.exists(tenant, reference))
                throw new ReportFault("ARTIFACT_UNREGISTERED", "未登记对象必须由命名空间恢复器核对", 409);
            return;
        }
        quota.markDelete(tenant, storeId(), reference);
        quota.removeGuard(
                tenant,
                storeId(),
                reference,
                () -> {
                    delegate.delete(tenant, reference);
                    if (delegate.exists(tenant, reference))
                        throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "物理删除尚未确认", 503);
                });
    }

    /**
     * 暂存清理先删物理文件，再减主库计数。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     */
    public void discard(String tenant, Path path) {
        var token = pending.get(normal(path));
        if (token == null) {
            delegate.discard(tenant, path);
            return;
        }
        quota.markDelete(tenant, storeId(), token.reference);
        delegate.discard(tenant, path);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "暂存删除尚未确认", 503);
        quota.confirmDelete(tenant, storeId(), token.reference);
        pending.remove(normal(path), token);
    }

    /**
     * checksum必须验证实际内容，不只相信数据库或对象元数据。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public String checksum(String tenant, String reference) {
        var manifest = ready(tenant, reference);
        String checksum = delegate.checksum(tenant, reference);
        if (!checksum.equals(manifest.checksum()))
            throw new ReportFault("ARTIFACT_INVALID", "产物内容完整性不一致", 409);
        return checksum;
    }

    /**
     * 恢复删除和过期尝试，确认本机路径归属失败时保留配额等待原节点恢复。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    public void reap(Instant olderThan, BiPredicate<String, String> referenced) {
        for (var manifest : quota.candidates(storeId(), 200)) {
            try {
                if (manifest.state().equals("ADOPTING")) {
                    try {
                        var actual = delegate.info(manifest.tenant(), manifest.reference());
                        quota.adopted(
                                manifest.tenant(),
                                storeId(),
                                manifest.reference(),
                                actual.length(),
                                actual.checksum());
                    } catch (ReportFault failure) {
                        if (failure.code().equals("SNAPSHOT_EXPIRED"))
                            delete(manifest.tenant(), manifest.reference());
                        else throw failure;
                    }
                    continue;
                }
                if (manifest.state().equals("DELETE_PENDING")) {
                    if (manifest.path() != null) recoverStaging(manifest, olderThan);
                    else
                        quota.interruptedGuard(
                                manifest.tenant(),
                                storeId(),
                                manifest.reference(),
                                olderThan,
                                () -> delegate.delete(manifest.tenant(), manifest.reference()));
                } else if (manifest.state().equals("STORED")
                        && manifest.updated().isBefore(olderThan)
                        && !referenced.test(manifest.tenant(), manifest.reference()))
                    quota.reapGuard(
                            manifest.tenant(),
                            storeId(),
                            manifest.reference(),
                            olderThan,
                            () -> referenced.test(manifest.tenant(), manifest.reference()),
                            () -> delegate.delete(manifest.tenant(), manifest.reference()));
                else if (!manifest.state().equals("STORED")
                        && manifest.lease().isBefore(Instant.now())
                        && manifest.updated().isBefore(olderThan)) {
                    if (manifest.path() != null) recoverStaging(manifest, olderThan);
                    else
                        quota.interruptedGuard(
                                manifest.tenant(),
                                storeId(),
                                manifest.reference(),
                                olderThan,
                                () -> delegate.delete(manifest.tenant(), manifest.reference()));
                }
            } catch (ReportFault deferred) {
            }
        }
        var claimed = new ArrayList<ReportQuotaService.Manifest>();
        delegate.reapUntracked(
                olderThan,
                (tenant, reference) -> {
                    if (quota.manifest(tenant, storeId(), reference).isPresent()
                            || referenced.test(tenant, reference)) return true;
                    final Info actual;
                    try {
                        actual = delegate.info(tenant, reference);
                    } catch (ReportFault missing) {
                        if (missing.code().equals("SNAPSHOT_EXPIRED")) return true;
                        throw missing;
                    }
                    boolean selected =
                            quota.claimUntrackedDelete(
                                    tenant,
                                    storeId(),
                                    reference,
                                    actual.length(),
                                    actual.checksum(),
                                    node,
                                    () -> referenced.test(tenant, reference));
                    if (selected)
                        quota.manifest(tenant, storeId(), reference).ifPresent(claimed::add);
                    return !selected;
                });
        for (var manifest : claimed)
            if (!delegate.exists(manifest.tenant(), manifest.reference()))
                quota.confirmDelete(manifest.tenant(), storeId(), manifest.reference());
    }

    /**
     * 过期暂存仍通过底层私有路径校验，不能删除其他主机任意路径。
     *
     * @param manifest 当前契约的manifest配置或状态
     * @param olderThan 当前契约的olderThan配置或状态
     */
    private void recoverStaging(ReportQuotaService.Manifest manifest, Instant olderThan) {
        if (!manifest.owner().startsWith(node + "_")) return;
        Path path = Path.of(manifest.path());
        boolean removed =
                quota.stagingGuard(
                        manifest.tenant(),
                        storeId(),
                        manifest.reference(),
                        node,
                        olderThan,
                        () -> {
                            delegate.discard(manifest.tenant(), path);
                            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                                throw ReportFault.invalid("暂存仍存在");
                        });
        if (removed) pending.remove(normal(path));
    }

    /**
     * 字体等复用资源与GC共享同一清单行锁。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param seconds 当前契约的seconds配置或状态
     */
    public void pin(String tenant, String reference, int seconds) {
        quota.pin(tenant, storeId(), reference, seconds);
    }

    /** 冷启动完整纳管既有私有命名空间后才开放新写入。 */
    private void adopt() {
        for (var item : delegate.inventory()) {
            if (item.temporary() != null
                    && quota.pathRegistered(storeId(), normal(item.temporary()).toString(), null))
                continue;
            var prior = quota.manifest(item.tenant(), storeId(), item.reference());
            if (prior.isPresent()) {
                if (prior.get().state().equals("ADOPTING")) {
                    try {
                        var actual = delegate.info(item.tenant(), item.reference());
                        quota.adopted(
                                item.tenant(),
                                storeId(),
                                item.reference(),
                                actual.length(),
                                actual.checksum());
                    } catch (ReportFault failure) {
                        if (failure.code().equals("SNAPSHOT_EXPIRED"))
                            delete(item.tenant(), item.reference());
                        else throw failure;
                    }
                }
                continue;
            }
            if (item.temporary() != null) {
                if (!Files.isRegularFile(item.temporary(), LinkOption.NOFOLLOW_LINKS)) continue;
                quota.reserve(
                        item.tenant(),
                        storeId(),
                        item.reference(),
                        item.bytes(),
                        "STAGING",
                        owner,
                        normal(item.temporary()).toString());
                if (!Files.isRegularFile(item.temporary(), LinkOption.NOFOLLOW_LINKS)) {
                    quota.markDelete(item.tenant(), storeId(), item.reference());
                    delegate.discard(item.tenant(), item.temporary());
                    quota.confirmDelete(item.tenant(), storeId(), item.reference());
                    continue;
                }
                quota.staged(item.tenant(), storeId(), item.reference(), owner, item.bytes());
            } else {
                try {
                    var actual = delegate.info(item.tenant(), item.reference());
                    quota.reserve(
                            item.tenant(),
                            storeId(),
                            item.reference(),
                            actual.length(),
                            "ADOPTING",
                            owner,
                            null);
                    var current = delegate.info(item.tenant(), item.reference());
                    if (actual.length() != current.length()
                            || !actual.checksum().equals(current.checksum()))
                        throw ReportFault.invalid("纳管期间内容变化");
                    quota.adopted(
                            item.tenant(),
                            storeId(),
                            item.reference(),
                            current.length(),
                            current.checksum());
                } catch (ReportFault concurrent) {
                    var current = quota.manifest(item.tenant(), storeId(), item.reference());
                    if (concurrent.code().equals("SNAPSHOT_EXPIRED")) {
                        if (current.isPresent()
                                && current.get().owner().equals(owner)
                                && current.get().state().equals("ADOPTING"))
                            delete(item.tenant(), item.reference());
                        continue;
                    }
                    if (current.isPresent() && current.get().state().equals("DELETE_PENDING"))
                        continue;
                    if (current.isEmpty()
                            || !current.get().state().equals("STORED")
                            || current.get().length() != item.bytes()
                            || !Objects.equals(current.get().checksum(), item.checksum()))
                        throw concurrent;
                }
            }
        }
    }

    /** 关闭存储不会盲目释放未确认的物理占用。 */
    public void close() {
        delegate.close();
    }

    /**
     * 仅本实例创建的暂存可供写入和提交。
     *
     * @param path 本节点私有暂存路径
     * @return 当前受控处理的固定结果
     */
    private Pending owned(Path path) {
        var value = pending.get(normal(path));
        if (value == null) throw ReportFault.invalid("暂存不属于当前尝试");
        return value;
    }

    /**
     * Windows路径按绝对规范路径使用，不组成跨shell删除命令。
     *
     * @param path 本节点私有暂存路径
     * @return 当前受控处理的固定结果
     */
    private static Path normal(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * 只读已登记成功对象，删除中的内容不能被重新交付。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    private ReportQuotaService.Manifest ready(String tenant, String reference) {
        var manifest =
                quota.manifest(tenant, storeId(), reference)
                        .orElseThrow(
                                () -> new ReportFault("SNAPSHOT_EXPIRED", "私有产物未登记或已经过期", 410));
        if (!manifest.state().equals("STORED"))
            throw new ReportFault("ARTIFACT_NOT_READY", "产物尚未登记或正在清理", 409);
        return manifest;
    }

    /**
     * HEAD、文件实际长度和主库固定契约必须匹配。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param manifest 当前契约的manifest配置或状态
     * @return 当前受控处理的固定结果
     */
    private Info verifyInfo(String tenant, String reference, ReportQuotaService.Manifest manifest) {
        var info = delegate.info(tenant, reference);
        if (info.length() != manifest.length() || !info.checksum().equals(manifest.checksum()))
            throw new ReportFault("ARTIFACT_INVALID", "产物元数据与清单不一致", 409);
        return info;
    }

    /**
     * SHA-256为平台必备算法。
     *
     * @return 当前受控处理的固定结果
     */
    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
