package com.nexus.report.storage;

import com.nexus.report.domain.ReportFault;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.function.BiPredicate;

/** 可替换的私有存储契约，接口响应中不出现物理路径。 */
public interface ReportArtifactStore extends AutoCloseable {
    /** 释放当前对象持有的文件或扫描句柄。 */
    @Override
    default void close() {}

    /**
     * 读取私有存储逻辑命名空间。
     *
     * @return 当前处理返回的固定结果
     */
    String storeId();

    /**
     * 私有命名空间纳管结果，暂存路径只在存储层内部使用。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param bytes 保留或实际占用的字节数
     * @param checksum 实际内容的SHA-256校验和
     * @param temporary 当前契约的temporary配置或状态
     */
    record Inventory(
            String tenant, String reference, long bytes, String checksum, Path temporary) {}

    /**
     * 装配清单前核对既有私有内容；自定义实现须声明自己的命名空间清单。
     *
     * @return 当前受控处理的固定结果
     */
    default List<Inventory> inventory() {
        return List.of();
    }

    /**
     * 使用租约在同一清单围栏内阻止回收正在复用的资源。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param seconds 当前契约的seconds配置或状态
     */
    default void pin(String tenant, String reference, int seconds) {}

    /**
     * 元数据不包含公开URL，checksum始终是实际内容SHA-256。
     *
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     */
    record Info(long length, String checksum) {}

    /** 需要Path的库使用明确关闭的私有物化生命周期。 */
    final class Materialized implements AutoCloseable {
        /** 保存当前path状态或配置。 */
        private final Path path;

        /** 保存当前release状态或配置。 */
        private final Runnable release;

        /** 保存当前closed状态或配置。 */
        private boolean closed;

        /**
         * 保存私有路径和一次性清理动作。
         *
         * @param path 本节点私有暂存路径
         * @param release 当前契约的release配置或状态
         */
        public Materialized(Path path, Runnable release) {
            this.path = path;
            this.release = release;
        }

        /**
         * 只在当前受控生命周期内获取路径。
         *
         * @return 当前受控处理的固定结果
         */
        public Path path() {
            if (closed) throw new IllegalStateException("物化文件已释放");
            return path;
        }

        /** 重复关闭不会再次释放容量。 */
        public void close() {
            if (!closed) {
                closed = true;
                release.run();
            }
        }
    }

    /**
     * 本地对象不复制；共享实现必须覆盖此标志。
     *
     * @return 当前受控处理的固定结果
     */
    default boolean requiresMaterialization() {
        return false;
    }

    /**
     * 本地默认物化不复制文件，读取仍执行原租户检查。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    default Materialized materialize(String tenant, String reference) {
        return new Materialized(read(tenant, reference), () -> {});
    }

    /**
     * 默认打开受限输入流，关闭时同时释放物化文件。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    default InputStream open(String tenant, String reference) {
        var materialized = materialize(tenant, reference);
        try {
            return new FilterInputStream(Files.newInputStream(materialized.path())) {
                /** 释放本次受控资源并确认清理结果。 */
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        materialized.close();
                    }
                }
            };
        } catch (IOException failure) {
            materialized.close();
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "私有产物不可读取", 503);
        }
    }

    /**
     * 索引块读取使用可信服务端offset和checksum，客户端不能提供对象范围。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param offset 当前契约的offset配置或状态
     * @param length 实际内容长度
     * @param checksum 实际内容的SHA-256校验和
     * @return 当前受控处理的固定结果
     */
    default InputStream range(
            String tenant, String reference, long offset, long length, String checksum) {
        var materialized = materialize(tenant, reference);
        try {
            long total = Files.size(materialized.path());
            if (offset < 0 || length < 0 || offset > total || length > total - offset)
                throw ReportFault.invalid("产物范围无效");
            var channel = FileChannel.open(materialized.path(), StandardOpenOption.READ);
            channel.position(offset);
            var input = Channels.newInputStream(channel);
            var limited =
                    new FilterInputStream(input) {
                        /** 保存当前remaining状态或配置。 */
                        private long remaining = length;

                        /**
                         * 读取当前租户已授权的固定内容。
                         *
                         * @return 当前固定契约或处理结果
                         */
                        public int read() throws IOException {
                            if (remaining == 0) return -1;
                            int value = in.read();
                            if (value >= 0) remaining--;
                            return value;
                        }

                        /**
                         * 读取当前租户已授权的固定内容。
                         *
                         * @param bytes 当前受控操作的bytes输入
                         * @param start 当前受控操作的start输入
                         * @param count 当前受控操作的count输入
                         * @return 当前固定契约或处理结果
                         */
                        public int read(byte[] bytes, int start, int count) throws IOException {
                            if (remaining == 0) return -1;
                            int read = in.read(bytes, start, (int) Math.min(count, remaining));
                            if (read > 0) remaining -= read;
                            return read;
                        }
                    };
            return new CheckedArtifactInputStream(limited, length, checksum, materialized::close);
        } catch (IOException | RuntimeException failure) {
            materialized.close();
            if (failure instanceof ReportFault fault) throw fault;
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "产物范围不可读取", 503);
        }
    }

    /**
     * 核对真实长度和checksum，流式代理无需分配整份文件的字节数组。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    default Info info(String tenant, String reference) {
        try (var file = materialize(tenant, reference)) {
            return new Info(Files.size(file.path()), checksum(tenant, reference));
        } catch (IOException failure) {
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "私有产物元数据不可读取", 503);
        }
    }

    /**
     * 严格清单为提交预分配引用，底层实现必须防止覆盖已有对象。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @param kind 当前契约的kind配置或状态
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    default String commitAt(String tenant, Path temporary, String kind, String reference) {
        throw new ReportFault("ARTIFACT_STORE_UNSUPPORTED", "存储未实现确定引用提交", 409);
    }

    /**
     * 创建当前租户的受控暂存文件。
     *
     * @param tenant 当前租户标识
     * @param kind 受支持类型
     * @return 当前处理返回的固定结果
     */
    Path temporary(String tenant, String kind);

    /**
     * 预先计算确定暂存路径，不产生物理内容，以便先登记配额及重启恢复路径。
     *
     * @param tenant 租户隔离标识
     * @param kind 当前契约的kind配置或状态
     * @param token 当前契约的token配置或状态
     * @return 当前受控处理的固定结果
     */
    default Path temporaryPath(String tenant, String kind, String token) {
        throw new ReportFault("ARTIFACT_STORE_UNSUPPORTED", "存储未实现确定暂存路径", 409);
    }

    /**
     * 创建已登记路径；失败路径仍可由清单持有者确认删除，封存路径不能重新创建。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     */
    default void allocateTemporary(String tenant, Path temporary) {
        throw new ReportFault("ARTIFACT_STORE_UNSUPPORTED", "存储未实现确定路径分配", 409);
    }

    /**
     * 打开有界私有产物输出流。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     * @return 当前处理返回的固定结果
     */
    OutputStream output(String tenant, Path temporary);

    /**
     * 为由基础库打开的私有暂存流加入同一硬配额跟踪。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @param output 当前契约的output配置或状态
     * @return 当前受控处理的固定结果
     */
    default OutputStream track(String tenant, Path temporary, OutputStream output) {
        return output;
    }

    /**
     * 将本次暂存文件登记为不可变私有产物。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     * @param kind 受支持类型
     * @return 当前处理返回的固定结果
     */
    String commit(String tenant, Path temporary, String kind);

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前处理返回的固定结果
     */
    Path read(String tenant, String reference);

    /**
     * 完成 {@code exists} 对应的报表产物存储处理。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前检查或条件操作是否成功
     */
    boolean exists(String tenant, String reference);

    /**
     * 删除当前租户明确指定的私有产物。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     */
    void delete(String tenant, String reference);

    /**
     * 释放本次操作拥有的暂存文件。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     */
    void discard(String tenant, Path temporary);

    /**
     * 计算或核对私有产物完整性。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前处理返回的固定结果
     */
    String checksum(String tenant, String reference);

    /**
     * 可选的有界恢复扫描，回收在数据库引用登记之前遗留的私有对象。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    default void reap(Instant olderThan, BiPredicate<String, String> referenced) {}

    /**
     * 只回收未登记的已提交对象，受清单控制的暂存文件由持有节点单独清理。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    default void reapUntracked(Instant olderThan, BiPredicate<String, String> referenced) {}
}
