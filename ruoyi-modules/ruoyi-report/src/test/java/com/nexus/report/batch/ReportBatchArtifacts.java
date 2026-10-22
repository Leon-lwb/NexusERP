package com.nexus.report.batch;

import com.nexus.report.storage.ReportArtifactStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 模拟共享对象必须物化的语义，同时跟踪每份私有暂存的释放。 */
final class ReportBatchArtifacts implements ReportArtifactStore {
    /** 底层真实本地私有文件存储。 */
    final ReportArtifactStore delegate;

    /** 是否模拟共享存储的按请求物化。 */
    final boolean shared;

    /** 当前尚未释放的临时文件。 */
    final Set<Path> pending = new HashSet<>();

    /** 成功物化的次数。 */
    int materialized;

    /** 已关闭物化的次数。 */
    int released;

    /** 禁止合并产物进入持久提交。 */
    int commits;

    /** 物化后注入撤权、到期或超时。 */
    Runnable onMaterialize = () -> {};

    /** 测试校验和失配。 */
    boolean wrongChecksum;

    /** 测试输出大小与真实磁盘长度的双重核对。 */
    int padOutput;

    /**
     * 绑定真实私有文件及所需共享语义。
     *
     * @param delegate 测试私有存储
     * @param shared 是否必须复制物化
     */
    ReportBatchArtifacts(ReportArtifactStore delegate, boolean shared) {
        this.delegate = delegate;
        this.shared = shared;
    }

    /** 返回原命名空间，兼容运行任务固定的存储身份。 */
    public String storeId() { return delegate.storeId(); }

    /** 返回既有文件，供真实配额包装器纳管。 */
    public List<Inventory> inventory() { return delegate.inventory(); }

    /** 声明共享对象需要显式物化。 */
    public boolean requiresMaterialization() { return shared; }

    /** 打开共享对象流，不复用不受控的读路径。 */
    public InputStream open(String tenant, String reference) { return delegate.open(tenant, reference); }

    /** 复制共享对象并在生命周期结束后释放复制品。 */
    public Materialized materialize(String tenant, String reference) {
        materialized++;
        Materialized source = delegate.materialize(tenant, reference);
        if (!shared) {
            onMaterialize.run();
            return new Materialized(source.path(), () -> { source.close(); released++; });
        }
        Path copy = temporary(tenant, "pdf");
        try (source;
                var input = Files.newInputStream(source.path());
                var output = output(tenant, copy)) {
            input.transferTo(output);
        } catch (IOException | RuntimeException failure) {
            discard(tenant, copy);
            throw new IllegalStateException(failure);
        }
        onMaterialize.run();
        return new Materialized(copy, () -> { discard(tenant, copy); released++; });
    }

    /** 返回真实元数据，允许注入与物化内容不同的校验和。 */
    public Info info(String tenant, String reference) {
        var info = delegate.info(tenant, reference);
        return wrongChecksum ? new Info(info.length(), "0".repeat(64)) : info;
    }

    /** 创建并记录本次请求拥有的临时文件。 */
    public Path temporary(String tenant, String kind) {
        var path = delegate.temporary(tenant, kind);
        pending.add(path);
        return path;
    }

    /** 返回真实私有暂存路径，供原清单在分配前登记容量。 */
    public Path temporaryPath(String tenant, String kind, String token) {
        return delegate.temporaryPath(tenant, kind, token);
    }

    /** 分配已经过原硬配额登记的临时文件。 */
    public void allocateTemporary(String tenant, Path path) {
        delegate.allocateTemporary(tenant, path);
        pending.add(path);
    }

    /** 通过真实有界写流生成临时文件。 */
    public OutputStream output(String tenant, Path path) {
        var output = delegate.output(tenant, path);
        if (padOutput == 0) return output;
        return new FilterOutputStream(output) {
            /** 在关闭前模拟额外写入，验证实际文件大小仍有边界。 */
            public void close() throws IOException {
                try { out.write(new byte[padOutput]); } finally { super.close(); }
            }
        };
    }

    /** 合并流程不得提交任何持久对象。 */
    public String commit(String tenant, Path path, String kind) {
        commits++;
        throw new AssertionError("批次合并不得持久缓存");
    }

    /** 合并流程必须使用显式可关闭的物化生命周期。 */
    public Path read(String tenant, String reference) { throw new AssertionError("禁止直接read"); }

    /** 查询既有私有文件。 */
    public boolean exists(String tenant, String reference) { return delegate.exists(tenant, reference); }

    /** 转发删除，但批次合并不得删除原任务文件。 */
    public void delete(String tenant, String reference) { throw new AssertionError("禁止删除原产物"); }

    /** 释放请求拥有的暂存，并登记实际结果。 */
    public void discard(String tenant, Path path) {
        delegate.discard(tenant, path);
        pending.remove(path);
    }

    /** 计算真实私有产物校验和。 */
    public String checksum(String tenant, String reference) { return delegate.checksum(tenant, reference); }
}
