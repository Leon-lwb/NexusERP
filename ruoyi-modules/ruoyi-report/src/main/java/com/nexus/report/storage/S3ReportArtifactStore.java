package com.nexus.report.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.report.domain.ReportFault;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.BiPredicate;

/** 独立私有S3命名空间，所有读取由应用鉴权代理，不生成公开地址或预签名链接。 */
public final class S3ReportArtifactStore implements ReportArtifactStore {
    /** 保存当前client状态或配置。 */
    private final S3Client client;

    /** 保存当前id状态或配置。 */
    private final String id, bucket, prefix;

    /** 保存当前staging状态或配置。 */
    private final LocalReportArtifactStore staging;

    /** 保存当前maximum状态或配置。 */
    private final long maximum;

    /** 保存当前ownsClient状态或配置。 */
    private final boolean ownsClient;

    /** 保存当前recoveryToken状态或配置。 */
    private String recoveryToken;

    /**
     * 从服务配置建立客户端，HTTP必须由部署环境显式开启。
     *
     * @param properties 当前契约的properties配置或状态
     * @param local 当前契约的local配置或状态
     * @param maximum 当前契约的maximum配置或状态
     */
    public S3ReportArtifactStore(ReportStorageProperties properties, Path local, long maximum) {
        this(
                build(properties),
                properties.getStorageId(),
                properties.getBucket(),
                properties.getPrefix(),
                local,
                maximum,
                true);
    }

    /**
     * 注入隔离测试或已管理客户端，仍强制验证私有bucket。
     *
     * @param client 当前契约的client配置或状态
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param bucket 当前契约的bucket配置或状态
     * @param prefix 当前契约的prefix配置或状态
     * @param local 当前契约的local配置或状态
     * @param maximum 当前契约的maximum配置或状态
     */
    public S3ReportArtifactStore(
            S3Client client, String id, String bucket, String prefix, Path local, long maximum) {
        this(client, id, bucket, prefix, local, maximum, false);
    }

    /**
     * 校验命名空间并创建仅当前应用可读的本机暂存区域。
     *
     * @param client 当前契约的client配置或状态
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param bucket 当前契约的bucket配置或状态
     * @param prefix 当前契约的prefix配置或状态
     * @param local 当前契约的local配置或状态
     * @param maximum 当前契约的maximum配置或状态
     * @param ownsClient 当前契约的ownsClient配置或状态
     */
    private S3ReportArtifactStore(
            S3Client client,
            String id,
            String bucket,
            String prefix,
            Path local,
            long maximum,
            boolean ownsClient) {
        if (id == null
                || !id.matches("[a-z][a-z0-9_-]{0,63}")
                || bucket == null
                || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || prefix == null
                || !prefix.matches("[a-z][a-z0-9_-]*(/[a-z][a-z0-9_-]*)*")
                || prefix.length() > 160) throw new IllegalArgumentException("共享存储命名空间无效");
        this.client = client;
        this.id = id;
        this.bucket = bucket;
        this.prefix = prefix + "/";
        this.maximum = maximum;
        this.ownsClient = ownsClient;
        staging = new LocalReportArtifactStore(local, maximum);
        verifyPrivate();
    }

    /**
     * SDK调用总时限避免网络故障永久占有任务线程。
     *
     * @param p 当前契约的p配置或状态
     * @return 当前受控处理的固定结果
     */
    private static S3Client build(ReportStorageProperties p) {
        if (p.getEndpoint() == null || p.getAccessKey() == null || p.getSecretKey() == null)
            throw new IllegalArgumentException("共享存储需要独立端点及环境凭证");
        URI endpoint = URI.create(p.getEndpoint());
        if (endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || !Set.of("http", "https").contains(endpoint.getScheme())
                || endpoint.getScheme().equals("http") && !p.isAllowInsecure())
            throw new IllegalArgumentException("共享存储端点必须使用HTTPS，隔离环境可显式允许HTTP");
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(p.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
                .forcePathStyle(true)
                .overrideConfiguration(
                        c ->
                                c.apiCallTimeout(Duration.ofSeconds(60))
                                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .build();
    }

    /** ACL和bucket策略不能向匿名或任意登录账户开放读取。 */
    private void verifyPrivate() {
        try {
            var acl = client.getBucketAcl(r -> r.bucket(bucket));
            for (var grant : acl.grants()) {
                String uri = grant.grantee().uri();
                if (uri != null && (uri.endsWith("AllUsers") || uri.endsWith("AuthenticatedUsers")))
                    throw new ReportFault("ARTIFACT_BUCKET_PUBLIC", "报表bucket必须为私有", 409);
            }
            try {
                var policy = client.getBucketPolicy(r -> r.bucket(bucket));
                var statements = new ObjectMapper().readTree(policy.policy()).path("Statement");
                var entries = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                if (statements.isArray()) statements.forEach(entries::add);
                else if (statements.isObject()) entries.add(statements);
                else throw new ReportFault("ARTIFACT_BUCKET_POLICY_INVALID", "bucket策略结构无法验证", 409);
                for (var statement : entries)
                    if (statement.path("Effect").asText().equals("Allow")
                            && statement.path("Principal").toString().contains("\"*\""))
                        throw new ReportFault("ARTIFACT_BUCKET_PUBLIC", "报表bucket策略包含公开授权", 409);
            } catch (S3Exception absent) {
                if (absent.statusCode() != 404
                        || absent.awsErrorDetails() == null
                        || !"NoSuchBucketPolicy".equals(absent.awsErrorDetails().errorCode()))
                    throw absent;
            }
            /** 版本和自动生命周期会恢复旧写入或清除封存标记，独立桶必须关闭。 */
            var versioning = client.getBucketVersioning(r -> r.bucket(bucket));
            if (versioning.status() != null)
                throw new ReportFault("ARTIFACT_BUCKET_VERSIONED", "报表专用桶必须未启用对象版本功能", 409);
            try {
                var lifecycle = client.getBucketLifecycleConfiguration(r -> r.bucket(bucket));
                if (lifecycle.rules().stream()
                        .anyMatch(rule -> rule.status() == ExpirationStatus.ENABLED))
                    throw new ReportFault("ARTIFACT_BUCKET_LIFECYCLE", "报表专用桶不能配置自动对象删除生命周期", 409);
            } catch (S3Exception absent) {
                if (absent.statusCode() != 404
                        || absent.awsErrorDetails() == null
                        || !"NoSuchLifecycleConfiguration"
                                .equals(absent.awsErrorDetails().errorCode())) throw absent;
            }
        } catch (ReportFault fault) {
            throw fault;
        } catch (Exception unavailable) {
            throw fault();
        }
    }

    /**
     * 冷启动纳管独立前缀既有对象和本节点暂存，未知对象不被静默忽略。
     *
     * @return 当前受控处理的固定结果
     */
    public List<Inventory> inventory() {
        var result = new ArrayList<Inventory>(staging.inventory());
        String token = null;
        do {
            final String next = token;
            var objects =
                    client.listObjectsV2(
                            r ->
                                    r.bucket(bucket)
                                            .prefix(prefix)
                                            .maxKeys(200)
                                            .continuationToken(next));
            for (var object : objects.contents()) {
                String[] parts = object.key().substring(prefix.length()).split("/");
                if (parts.length != 2 || !validTenant(parts[0]) || !validReference(parts[1]))
                    throw new ReportFault("ARTIFACT_INVENTORY_INVALID", "报表命名空间含未知对象，请先核对", 409);
                try {
                    var meta = info(parts[0], parts[1]);
                    result.add(
                            new Inventory(
                                    parts[0], parts[1], meta.length(), meta.checksum(), null));
                } catch (ReportFault concurrentDelete) {
                    if (!"SNAPSHOT_EXPIRED".equals(concurrentDelete.code())) throw concurrentDelete;
                }
                if (result.size() > 10000) throw ReportFault.capacity();
            }
            token =
                    Boolean.TRUE.equals(objects.isTruncated())
                            ? objects.nextContinuationToken()
                            : null;
        } while (token != null);
        return List.copyOf(result);
    }

    /**
     * 返回跨节点稳定的逻辑storeId。
     *
     * @return 当前受控处理的固定结果
     */
    public String storeId() {
        return id;
    }

    /**
     * 共享对象需要私有物化，调用方必须关闭生命周期。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean requiresMaterialization() {
        return true;
    }

    /**
     * 暂存路径保持本机租户和权限约束。
     *
     * @param tenant 租户隔离标识
     * @param kind 当前契约的kind配置或状态
     * @return 当前受控处理的固定结果
     */
    public Path temporary(String tenant, String kind) {
        return staging.temporary(tenant, kind);
    }

    /**
     * S3使用本节点确定暂存路径，清单在创建前记录该路径。
     *
     * @param tenant 租户隔离标识
     * @param kind 当前契约的kind配置或状态
     * @param token 当前契约的token配置或状态
     * @return 当前受控处理的固定结果
     */
    public Path temporaryPath(String tenant, String kind, String token) {
        return staging.temporaryPath(tenant, kind, token);
    }

    /**
     * 暂存物理分配也受永久路径封存围栏保护。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     */
    public void allocateTemporary(String tenant, Path temporary) {
        staging.allocateTemporary(tenant, temporary);
    }

    /**
     * 写入只走有界私有暂存输出。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @return 当前受控处理的固定结果
     */
    public OutputStream output(String tenant, Path temporary) {
        return staging.output(tenant, temporary);
    }

    /**
     * 普通提交仍使用一次性引用，清单实现使用commitAt预分配引用。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @param kind 当前契约的kind配置或状态
     * @return 当前受控处理的固定结果
     */
    public String commit(String tenant, Path temporary, String kind) {
        return commitAt(tenant, temporary, kind, UUID.randomUUID() + "." + kind);
    }

    /**
     * 内容长度和checksum进入对象元数据，条件写入阻止覆盖已有产物。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     * @param kind 当前契约的kind配置或状态
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public String commitAt(String tenant, Path temporary, String kind, String reference) {
        String key = key(tenant, reference);
        if (!reference.endsWith("." + kind)) throw ReportFault.invalid("产物类型不匹配");
        try {
            staging.validateTemporary(tenant, temporary);
            long length = Files.size(temporary);
            if (length > maximum) throw ReportFault.capacity();
            String checksum = digest(Files.newInputStream(temporary));
            client.putObject(
                    r ->
                            r.bucket(bucket)
                                    .key(key)
                                    .metadata(Map.of("sha256", checksum, "store-id", id))
                                    .contentLength(length)
                                    .overrideConfiguration(c -> c.putHeader("If-None-Match", "*")),
                    RequestBody.fromFile(temporary));
            var info = info(tenant, reference);
            if (info.length() != length || !info.checksum().equals(checksum))
                throw new ReportFault("ARTIFACT_INVALID", "上传产物完整性不一致", 409);
            staging.discard(tenant, temporary);
            return reference;
        } catch (ReportFault fault) {
            throw fault;
        } catch (Exception failure) {
            throw fault();
        }
    }

    /**
     * HEAD元数据经过命名空间、长度和checksum格式校验。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Info info(String tenant, String reference) {
        try {
            var head = client.headObject(r -> r.bucket(bucket).key(key(tenant, reference)));
            if ("true".equals(head.metadata().get("retired"))) {
                if (head.contentLength() != 0 || !id.equals(head.metadata().get("store-id")))
                    throw new ReportFault("ARTIFACT_INVALID", "封存元数据不符合零字节固定契约", 409);
                throw new ReportFault("SNAPSHOT_EXPIRED", "私有对象引用已经永久封存", 410);
            }
            String checksum = head.metadata().get("sha256");
            if (head.contentLength() < 0
                    || head.contentLength() > maximum
                    || checksum == null
                    || !checksum.matches("[a-f0-9]{64}")
                    || !id.equals(head.metadata().get("store-id")))
                throw new ReportFault("ARTIFACT_INVALID", "共享产物元数据不符合固定契约", 409);
            return new Info(head.contentLength(), checksum);
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404)
                throw new ReportFault("SNAPSHOT_EXPIRED", "私有产物不存在或已过期", 410);
            throw fault();
        } catch (software.amazon.awssdk.core.exception.SdkClientException failure) {
            throw fault();
        }
    }

    /**
     * 流式读取在EOF验证真实长度与SHA-256，不信任ETag等同内容摘要。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public InputStream open(String tenant, String reference) {
        var expected = info(tenant, reference);
        try {
            var input = client.getObject(r -> r.bucket(bucket).key(key(tenant, reference)));
            return new FilterInputStream(input) {
                /** 保存当前hash状态或配置。 */
                private final MessageDigest hash = sha();

                /** 保存当前count状态或配置。 */
                private long count;

                /** 保存当前verified状态或配置。 */
                private boolean verified;

                /**
                 * 执行consumed对应的受控处理。
                 *
                 * @param b 当前受控操作的b输入
                 * @param off 当前受控操作的off输入
                 * @param length 当前受控操作的length输入
                 */
                private void consumed(byte[] b, int off, int length) {
                    if (length > 0) {
                        count += length;
                        if (count > expected.length())
                            throw new ReportFault("ARTIFACT_INVALID", "共享产物长度超过固定值", 409);
                        hash.update(b, off, length);
                    } else if (length < 0 && !verified) {
                        verified = true;
                        if (count != expected.length()
                                || !HexFormat.of()
                                        .formatHex(hash.digest())
                                        .equals(expected.checksum()))
                            throw new ReportFault("ARTIFACT_INVALID", "共享产物checksum不一致", 409);
                    }
                }

                /**
                 * 读取当前租户已授权的固定内容。
                 *
                 * @return 当前固定契约或处理结果
                 */
                public int read() throws IOException {
                    int value = in.read();
                    if (value >= 0) consumed(new byte[] {(byte) value}, 0, 1);
                    else consumed(new byte[0], 0, -1);
                    return value;
                }

                /**
                 * 读取当前租户已授权的固定内容。
                 *
                 * @param bytes 当前受控操作的bytes输入
                 * @param offset 当前受控操作的offset输入
                 * @param length 当前受控操作的length输入
                 * @return 当前固定契约或处理结果
                 */
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = in.read(bytes, offset, length);
                    consumed(bytes, offset, count);
                    return count;
                }
            };
        } catch (S3Exception failure) {
            throw fault();
        }
    }

    /**
     * S3使用真实Range请求，索引块校验后才作为快照数据使用。
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
        var meta = info(tenant, reference);
        if (offset < 0 || length < 1 || offset > meta.length() || length > meta.length() - offset)
            throw ReportFault.invalid("共享范围无效");
        try {
            return new CheckedArtifactInputStream(
                    client.getObject(
                            r ->
                                    r.bucket(bucket)
                                            .key(key(tenant, reference))
                                            .range(
                                                    "bytes="
                                                            + offset
                                                            + "-"
                                                            + (offset + length - 1))),
                    length,
                    checksum,
                    () -> {});
        } catch (Exception failure) {
            throw fault();
        }
    }

    /**
     * 需要文件的库通过受控物化使用，不永久缓存共享对象。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Materialized materialize(String tenant, String reference) {
        Path path = staging.temporary(tenant, "json");
        try (var input = open(tenant, reference);
                var output = staging.output(tenant, path)) {
            input.transferTo(output);
            return new Materialized(path, () -> staging.discard(tenant, path));
        } catch (Exception failure) {
            staging.discard(tenant, path);
            if (failure instanceof ReportFault fault) throw fault;
            throw fault();
        }
    }

    /**
     * 兼容旧内部Path入口，回收器会按年龄清理未关闭的暂存。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public Path read(String tenant, String reference) {
        return materialize(tenant, reference).path();
    }

    /**
     * 不存在对象返回false，网络故障不能伪装为不存在。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public boolean exists(String tenant, String reference) {
        try {
            info(tenant, reference);
            return true;
        } catch (ReportFault fault) {
            if (fault.code().equals("SNAPSHOT_EXPIRED")) return false;
            throw fault;
        }
    }

    /**
     * 删除是幂等操作，网络失败明确保留恢复状态。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     */
    public void delete(String tenant, String reference) {
        try {
            /** 原子替换为零字节封存标记；If-None-Match发布再也不能使用同一引用。 */
            client.putObject(
                    r ->
                            r.bucket(bucket)
                                    .key(key(tenant, reference))
                                    .metadata(Map.of("retired", "true", "store-id", id)),
                    RequestBody.empty());
            if (exists(tenant, reference)) throw fault();
        } catch (ReportFault fault) {
            throw fault;
        } catch (Exception failure) {
            throw fault();
        }
    }

    /**
     * 暂存删除仍执行私有路径所有权检查。
     *
     * @param tenant 租户隔离标识
     * @param temporary 当前契约的temporary配置或状态
     */
    public void discard(String tenant, Path temporary) {
        staging.discard(tenant, temporary);
    }

    /**
     * 校验真实流内容后才返回摘要。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    public String checksum(String tenant, String reference) {
        try {
            return digest(open(tenant, reference));
        } catch (IOException failure) {
            throw fault();
        }
    }

    /**
     * 每轮只扫描一页命名空间内对象，陌生键不被删除。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    public void reap(Instant olderThan, BiPredicate<String, String> referenced) {
        staging.reap(olderThan, (tenant, ref) -> false);
        reapUntracked(olderThan, referenced);
    }

    /**
     * 清单包装器只扫描共享对象；本机暂存仍按持有节点和清单租约回收。
     *
     * @param olderThan 当前契约的olderThan配置或状态
     * @param referenced 当前契约的referenced配置或状态
     */
    public void reapUntracked(Instant olderThan, BiPredicate<String, String> referenced) {
        try {
            var objects =
                    client.listObjectsV2(
                            r ->
                                    r.bucket(bucket)
                                            .prefix(prefix)
                                            .maxKeys(200)
                                            .continuationToken(recoveryToken));
            recoveryToken =
                    Boolean.TRUE.equals(objects.isTruncated())
                            ? objects.nextContinuationToken()
                            : null;
            for (var object : objects.contents()) {
                String rest = object.key().substring(prefix.length());
                String[] parts = rest.split("/");
                if (parts.length != 2
                        || !validTenant(parts[0])
                        || !validReference(parts[1])
                        || !object.lastModified().isBefore(olderThan)
                        || referenced.test(parts[0], parts[1])) continue;
                if (exists(parts[0], parts[1])) delete(parts[0], parts[1]);
            }
        } catch (Exception failure) {
            if (failure instanceof ReportFault fault) throw fault;
            throw fault();
        }
    }

    /** 只关闭当前实例创建的SDK客户端。 */
    public void close() {
        staging.close();
        if (ownsClient) client.close();
    }

    /**
     * 租户和引用只生成命名空间内固定键，拒绝路径和URL。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    private String key(String tenant, String reference) {
        if (!validTenant(tenant) || !validReference(reference))
            throw ReportFault.invalid("私有对象标识无效");
        return prefix + tenant + "/" + reference;
    }

    /**
     * 限制租户编码，不能含路径分隔符。
     *
     * @param value 当前契约的value配置或状态
     * @return 当前受控处理的固定结果
     */
    private static boolean validTenant(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,20}");
    }

    /**
     * 固定类型及一次性UUID，不接受任意对象地址。
     *
     * @param value 当前契约的value配置或状态
     * @return 当前受控处理的固定结果
     */
    private static boolean validReference(String value) {
        return value != null
                && value.matches("[a-f0-9-]{36}\\.(rows|json|plan|pdf|xlsx|asset|index|font)");
    }

    /**
     * 对实际字节流计算摘要，流在完成后立即关闭。
     *
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    private static String digest(InputStream input) throws IOException {
        try (input) {
            var hash = sha();
            byte[] bytes = new byte[65536];
            int length;
            while ((length = input.read(bytes)) >= 0) if (length > 0) hash.update(bytes, 0, length);
            return HexFormat.of().formatHex(hash.digest());
        }
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

    /**
     * 对外只返回稳定存储错误，不转发SDK凭证或请求原文。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault fault() {
        return new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "私有共享存储不可用", 503);
    }
}
