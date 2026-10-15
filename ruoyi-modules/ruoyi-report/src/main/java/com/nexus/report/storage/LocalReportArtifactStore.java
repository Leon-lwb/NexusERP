package com.nexus.report.storage;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.service.ReportDefinitionService;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;

/** 应用私有且按租户隔离的文件存储，不作为静态或公开资源挂载。 */
public final class LocalReportArtifactStore implements ReportArtifactStore {
    /** 保存当前 {@code KINDS} 值。 */
    private static final Set<String> KINDS =
            Set.of("rows", "json", "plan", "pdf", "xlsx", "asset", "index", "font");

    /** 保存当前 {@code root} 值。 */
    private final Path root;

    /** 保存当前 {@code maxBytes} 值。 */
    private final long maxBytes;

    /** 保存当前 {@code storeId} 值。 */
    private final String storeId;

    /**
     * 初始化本地报表产物存储并保存所需依赖或不可变值。
     *
     * @param configured 当前操作的 {@code configured} 输入
     * @param maxBytes 当前操作的 {@code maxBytes} 输入
     */
    public LocalReportArtifactStore(Path configured, long maxBytes) {
        if (maxBytes <= 0 || maxBytes > 200L * 1024 * 1024)
            throw new IllegalArgumentException("Artifact budget required");
        this.root = configured.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        try {
            rejectLinks(root);
            Files.createDirectories(root);
            privateAccess(root);
            this.storeId =
                    "local-"
                            + ReportDefinitionService.hash(
                                            InetAddress.getLocalHost().getHostName()
                                                    + "|"
                                                    + root.toRealPath())
                                    .substring(0, 24);
        } catch (IOException e) {
            throw fault();
        }
    }

    /**
     * 明确纳管当前私有目录内的旧对象与暂存，不扫描工作区其他路径。
     *
     * @return 当前受控处理的固定结果
     */
    public List<Inventory> inventory() {
        var result = new ArrayList<Inventory>();
        try (var tenants = Files.newDirectoryStream(root)) {
            for (Path folder : tenants) {
                if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(folder)) continue;
                String tenant = folder.getFileName().toString();
                if (!tenant.matches("[A-Za-z0-9_-]{1,20}")) throw fault();
                try (var files = Files.newDirectoryStream(folder)) {
                    for (Path file : files) {
                        if (file.getFileName().toString().equals(".fences")
                                && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)
                                && !Files.isSymbolicLink(file)) continue;
                        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                || Files.isSymbolicLink(file)) throw fault();
                        String name = file.getFileName().toString();
                        if (name.startsWith("pending-") && name.endsWith(".tmp")) {
                            ownTemporary(tenant, file);
                            result.add(
                                    new Inventory(
                                            tenant,
                                            "legacy_"
                                                    + ReportDefinitionService.hash(name)
                                                            .substring(0, 40),
                                            Files.size(file),
                                            null,
                                            file));
                        } else {
                            object(tenant, name);
                            result.add(
                                    new Inventory(
                                            tenant,
                                            name,
                                            Files.size(file),
                                            checksum(tenant, name),
                                            null));
                        }
                        if (result.size() > 10000) throw ReportFault.capacity();
                    }
                }
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw fault();
        }
    }

    /** 保存当前 {@code recoveryFiles} 值。 */
    /** 保存当前 {@code recoveryFolders} 值。 */
    private DirectoryStream<Path> recoveryFolders, recoveryFiles;

    /** 保存当前 {@code fileCursor} 值。 */
    /** 保存当前 {@code folderCursor} 值。 */
    private Iterator<Path> folderCursor, fileCursor;

    /** 保存当前 {@code recoveryTenant} 值。 */
    private String recoveryTenant;

    /**
     * 读取私有存储逻辑命名空间。
     *
     * @return 当前处理返回的固定结果
     */
    public String storeId() {
        return storeId;
    }

    /**
     * 完成 {@code rejectLinks} 对应的本地报表产物存储处理。
     *
     * @param path 可信私有路径
     */
    private static void rejectLinks(Path path) {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current))
                throw new ReportFault("ARTIFACT_INVALID", "私有报表路径不能包含符号链接", 500);
        }
    }

    /**
     * 完成 {@code tenant} 对应的本地报表产物存储处理。
     *
     * @param tenant 当前租户标识
     * @return 当前处理返回的固定结果
     */
    private Path tenant(String tenant) {
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,20}"))
            throw new ReportFault("ARTIFACT_INVALID", "租户路径无效", 400);
        Path path = root.resolve(tenant).normalize();
        if (!path.startsWith(root)) throw fault();
        try {
            rejectLinks(path);
            Files.createDirectories(path);
            privateAccess(path);
            return path;
        } catch (IOException e) {
            throw fault();
        }
    }

    /**
     * 完成 {@code privateAccess} 对应的本地报表产物存储处理。
     *
     * @param file 当前操作的 {@code file} 输入
     */
    private static void privateAccess(Path file) throws IOException {
        var posix =
                Files.getFileAttributeView(
                        file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(
                    Files.isDirectory(file)
                            ? PosixFilePermissions.fromString("rwx------")
                            : PosixFilePermissions.fromString("rw-------"));
            return;
        }
        var acl =
                Files.getFileAttributeView(
                        file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
            var entry =
                    AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(owner)
                            .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                            .build();
            acl.setAcl(List.of(entry));
            return;
        }
        throw new IOException("Private file permissions unavailable");
    }

    /**
     * 完成 {@code kind} 对应的本地报表产物存储处理。
     *
     * @param kind 受支持类型
     */
    private static void kind(String kind) {
        if (!KINDS.contains(kind)) throw new ReportFault("ARTIFACT_INVALID", "报表文件类型无效");
    }

    /**
     * 创建当前租户的受控暂存文件。
     *
     * @param tenant 当前租户标识
     * @param kind 受支持类型
     * @return 当前处理返回的固定结果
     */
    /**
     * 校验暂存路径所有权而不重新打开或截断已写内容。
     *
     * @param tenant 租户隔离标识
     * @param path 本节点私有暂存路径
     * @return 当前受控处理的固定结果
     */
    public Path validateTemporary(String tenant, Path path) {
        return ownTemporary(tenant, path);
    }

    /**
     * 为当前尝试分配受控私有暂存。
     *
     * @param tenant 当前受控操作的tenant输入
     * @param kind 当前受控操作的kind输入
     * @return 当前固定契约或处理结果
     */
    public Path temporary(String tenant, String kind) {
        Path path = temporaryPath(tenant, kind, UUID.randomUUID().toString());
        allocateTemporary(tenant, path);
        return path;
    }

    /**
     * 路径可在物理创建前登记，token只接受本节点生成的安全标识。
     *
     * @param tenant 租户隔离标识
     * @param kind 当前契约的kind配置或状态
     * @param token 当前契约的token配置或状态
     * @return 当前受控处理的固定结果
     */
    public Path temporaryPath(String tenant, String kind, String token) {
        kind(kind);
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,64}"))
            throw ReportFault.invalid("暂存标识无效");
        return tenant(tenant).resolve("pending-" + token + "." + kind + ".tmp");
    }

    /**
     * 暂存创建与删除共享一次性路径围栏，迟到创建不能越过已完成回收。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     */
    public void allocateTemporary(String tenant, Path temporary) {
        Path path = ownTemporary(tenant, temporary);
        pathLock(
                path,
                channel -> {
                    if (channel.size() > 0 || Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                        throw new ReportFault("ARTIFACT_CONFLICT", "暂存路径已经使用或封存", 409);
                    try {
                        Files.createFile(path);
                        privateAccess(path);
                    } catch (IOException failure) {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException cleanup) {
                            failure.addSuppressed(cleanup);
                        }
                        throw failure;
                    }
                    return null;
                });
    }

    /**
     * 完成 {@code ownTemporary} 对应的本地报表产物存储处理。
     *
     * @param tenant 当前租户标识
     * @param path 可信私有路径
     * @return 当前处理返回的固定结果
     */
    private Path ownTemporary(String tenant, Path path) {
        Path folder = tenant(tenant);
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.getParent().equals(folder)
                || !absolute.getFileName()
                        .toString()
                        .matches(
                                "pending-[A-Za-z0-9_-]+\\.(rows|json|plan|pdf|xlsx|asset|index|font)\\.tmp")
                || Files.isSymbolicLink(absolute))
            throw new ReportFault("ARTIFACT_INVALID", "文件不属于当前报表租户");
        return absolute;
    }

    /**
     * 打开有界私有产物输出流。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     * @return 当前处理返回的固定结果
     */
    public OutputStream output(String tenant, Path temporary) {
        Path path = ownTemporary(tenant, temporary);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw fault();
        try {
            return new FilterOutputStream(
                    Files.newOutputStream(
                            path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                /** 保存当前 {@code count} 值。 */
                private long count;

                /**
                 * 完成 {@code budget} 对应的报表处理。
                 *
                 * @param size 有界页大小
                 */
                private void budget(int size) {
                    if (size < 0 || count + size > maxBytes) throw ReportFault.capacity();
                    count += size;
                }

                /**
                 * 向受控输出流写入固定报表结果。
                 *
                 * @param value 当前操作的 {@code value} 输入
                 */
                public void write(int value) throws IOException {
                    budget(1);
                    out.write(value);
                }

                /**
                 * 向受控输出流写入固定报表结果。
                 *
                 * @param bytes 受控字节数据
                 * @param offset 当前操作的 {@code offset} 输入
                 * @param length 当前操作的 {@code length} 输入
                 */
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, bytes.length);
                    budget(length);
                    out.write(bytes, offset, length);
                }
            };
        } catch (IOException e) {
            throw fault();
        }
    }

    /**
     * 将本次暂存文件登记为不可变私有产物。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     * @param kind 受支持类型
     * @return 当前处理返回的固定结果
     */
    public String commit(String tenant, Path temporary, String kind) {
        return commitAt(tenant, temporary, kind, UUID.randomUUID() + "." + kind);
    }

    /**
     * 确定引用提交禁止覆盖，保持暂存路径、文件类型和租户边界。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @param kind 当前契约的kind配置或状态
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public String commitAt(String tenant, Path temporary, String kind, String reference) {
        kind(kind);
        Path path = ownTemporary(tenant, temporary);
        Path target = object(tenant, reference);
        if (!reference.endsWith("." + kind))
            throw new ReportFault("ARTIFACT_CONFLICT", "对象类型不匹配", 409);
        return referenceLock(
                tenant,
                reference,
                channel -> {
                    if (channel.size() > 0 || Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                        throw new ReportFault("ARTIFACT_CONFLICT", "对象引用已经使用或封存", 409);
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            || Files.size(path) > maxBytes) throw ReportFault.capacity();
                    Files.move(path, target);
                    privateAccess(target);
                    return reference;
                });
    }

    /**
     * 完成 {@code object} 对应的本地报表产物存储处理。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前处理返回的固定结果
     */
    private Path object(String tenant, String reference) {
        if (reference == null
                || !reference.matches(
                        "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(rows|json|plan|pdf|xlsx|asset|index|font)"))
            throw new ReportFault("ARTIFACT_INVALID", "报表对象标识无效");
        Path path = tenant(tenant).resolve(reference);
        if (Files.isSymbolicLink(path)) throw fault();
        return path;
    }

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前处理返回的固定结果
     */
    public Path read(String tenant, String reference) {
        Path path = object(tenant, reference);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new ReportFault("SNAPSHOT_EXPIRED", "报表文件已过期或不在当前存储节点", 410);
        return path;
    }

    /**
     * 完成 {@code exists} 对应的本地报表产物存储处理。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前检查或条件操作是否成功
     */
    public boolean exists(String tenant, String reference) {
        return Files.isRegularFile(object(tenant, reference), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * 删除当前租户明确指定的私有产物。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     */
    public void delete(String tenant, String reference) {
        if (reference == null) return;
        referenceLock(
                tenant,
                reference,
                channel -> {
                    channel.position(0);
                    channel.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
                    channel.force(true);
                    Files.deleteIfExists(object(tenant, reference));
                    return null;
                });
    }

    /** JVM内按固定条带串行化文件锁申请，避免同进程重叠锁异常和无界内存表。 */
    private static final java.util.concurrent.locks.ReentrantLock[] REFERENCE_LOCKS =
            java.util.stream.IntStream.range(0, 64)
                    .mapToObj(i -> new java.util.concurrent.locks.ReentrantLock())
                    .toArray(java.util.concurrent.locks.ReentrantLock[]::new);

    /** 可在当前私有引用文件锁内执行的受控I/O。 */
    private interface ReferenceAction<T> {
        /**
         * 执行持锁物理操作。
         *
         * @param channel 当前契约的channel配置或状态
         * @return 当前受控处理的固定结果
         */
        T run(java.nio.channels.FileChannel channel) throws IOException;
    }

    /**
     * 一次性引用侧写锁跨进程生效；1字节封存标志永久保留，不允许旧尝试重新发布。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    private <T> T referenceLock(String tenant, String reference, ReferenceAction<T> action) {
        return pathLock(object(tenant, reference), action);
    }

    /**
     * 暂存和业务对象共用本机跨进程路径封存，标志永久保留且不能下载。
     *
     * @param target 当前契约的target配置或状态
     * @param action 受控资源操作
     * @return 当前受控处理的固定结果
     */
    private <T> T pathLock(Path target, ReferenceAction<T> action) {
        Path folder = target.getParent().resolve(".fences");
        Path fence =
                folder.resolve(
                        ReportDefinitionService.hash(target.getFileName().toString()) + ".fence");
        var stripe =
                REFERENCE_LOCKS[
                        (fence.toAbsolutePath().normalize().hashCode() & Integer.MAX_VALUE)
                                % REFERENCE_LOCKS.length];
        stripe.lock();
        try {
            if (Files.isSymbolicLink(folder) || Files.isSymbolicLink(fence)) throw fault();
            Files.createDirectories(folder);
            privateAccess(folder);
            try (var channel =
                            java.nio.channels.FileChannel.open(
                                    fence,
                                    java.nio.file.StandardOpenOption.CREATE,
                                    java.nio.file.StandardOpenOption.READ,
                                    java.nio.file.StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS);
                    var lock = channel.lock()) {
                privateAccess(fence);
                return action.run(channel);
            }
        } catch (IOException failure) {
            throw fault();
        } finally {
            stripe.unlock();
        }
    }

    /**
     * 释放本次操作拥有的暂存文件。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     */
    public void discard(String tenant, Path temporary) {
        if (temporary == null) return;
        Path path = ownTemporary(tenant, temporary);
        pathLock(
                path,
                channel -> {
                    channel.position(0);
                    channel.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
                    channel.force(true);
                    Files.deleteIfExists(path);
                    return null;
                });
    }

    /**
     * 计算或核对私有产物完整性。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前处理返回的固定结果
     */
    public String checksum(String tenant, String reference) {
        try (var input = Files.newInputStream(read(tenant, reference))) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[8192];
            int size;
            while ((size = input.read(bytes)) >= 0) digest.update(bytes, 0, size);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw fault();
        }
    }

    /**
     * 有界恢复游标持续遍历租户和保留对象，不在每轮重新从头扫描。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    public synchronized void reap(
            java.time.Instant olderThan,
            java.util.function.BiPredicate<String, String> referenced) {
        reapFiles(olderThan, referenced, true);
    }

    /**
     * 清单包装器调用此入口，只扫描已提交业务对象，不碰其他节点暂存。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    public synchronized void reapUntracked(
            java.time.Instant olderThan,
            java.util.function.BiPredicate<String, String> referenced) {
        reapFiles(olderThan, referenced, false);
    }

    /**
     * 按当前模式推进有界文件扫描游标。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     * @param includeStaging 当前契约的includeStaging配置或状态
     */
    private void reapFiles(
            java.time.Instant olderThan,
            java.util.function.BiPredicate<String, String> referenced,
            boolean includeStaging) {
        Objects.requireNonNull(olderThan);
        Objects.requireNonNull(referenced);
        try {
            if (recoveryFolders == null) {
                recoveryFolders = Files.newDirectoryStream(root);
                folderCursor = recoveryFolders.iterator();
            }
            for (int examined = 0; examined < 1000; ) {
                if (recoveryFiles != null) {
                    if (fileCursor.hasNext()) {
                        Path candidate = fileCursor.next();
                        if (candidate.getFileName().toString().equals(".fences")
                                && Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS))
                            continue;
                        examined++;
                        reapFile(candidate, olderThan, referenced, includeStaging);
                        continue;
                    }
                    recoveryFiles.close();
                    recoveryFiles = null;
                    fileCursor = null;
                    recoveryTenant = null;
                }
                if (!folderCursor.hasNext()) {
                    closeRecovery();
                    return;
                }
                Path folder = folderCursor.next();
                examined++;
                String tenantId = folder.getFileName().toString();
                if (!tenantId.matches("[A-Za-z0-9_-]{1,20}")
                        || Files.isSymbolicLink(folder)
                        || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    recoveryFiles = Files.newDirectoryStream(folder);
                } catch (NoSuchFileException | NotDirectoryException concurrentCleanup) {
                    continue;
                }
                fileCursor = recoveryFiles.iterator();
                recoveryTenant = tenantId;
            }
        } catch (IOException | DirectoryIteratorException failure) {
            resetAfterFailure(failure);
            throw fault();
        } catch (RuntimeException failure) {
            resetAfterFailure(failure);
            throw failure;
        }
    }

    /**
     * 完成 {@code reapFile} 对应的本地报表产物存储处理。
     *
     * @param file 当前操作的 {@code file} 输入
     * @param olderThan 回收年龄界限
     * @param referenced 数据库引用检查器
     * @param includeStaging 当前契约的includeStaging配置或状态
     */
    private void reapFile(
            Path file,
            java.time.Instant olderThan,
            java.util.function.BiPredicate<String, String> referenced,
            boolean includeStaging)
            throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            return;
        java.time.Instant modified;
        try {
            modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (NoSuchFileException concurrentCleanup) {
            return;
        }
        if (!modified.isBefore(olderThan)) return;
        String name = file.getFileName().toString();
        if (name.matches(
                "pending-[A-Za-z0-9_-]+\\.(rows|json|plan|pdf|xlsx|asset|index|font)\\.tmp")) {
            if (includeStaging) discard(recoveryTenant, file);
        } else if (name.matches(
                        "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(rows|json|plan|pdf|xlsx|asset|index|font)")
                && !referenced.test(recoveryTenant, name)) delete(recoveryTenant, name);
    }

    /**
     * 完成 {@code resetAfterFailure} 对应的本地报表产物存储处理。
     *
     * @param original 当前操作的 {@code original} 输入
     */
    private void resetAfterFailure(Throwable original) {
        try {
            closeRecovery();
        } catch (IOException cleanup) {
            original.addSuppressed(cleanup);
        }
    }

    /** 完成 {@code closeRecovery} 对应的本地报表产物存储处理。 */
    private void closeRecovery() throws IOException {
        var files = recoveryFiles;
        var folders = recoveryFolders;
        recoveryFiles = null;
        recoveryFolders = null;
        fileCursor = null;
        folderCursor = null;
        recoveryTenant = null;
        IOException failure = null;
        try {
            if (files != null) files.close();
        } catch (IOException error) {
            failure = error;
        }
        try {
            if (folders != null) folders.close();
        } catch (IOException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    @Override
    public synchronized void close() {
        try {
            closeRecovery();
        } catch (IOException failure) {
            throw fault();
        }
    }

    /**
     * 完成 {@code fault} 对应的本地报表产物存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault fault() {
        return new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "私有报表文件存储不可用", 500);
    }
}
