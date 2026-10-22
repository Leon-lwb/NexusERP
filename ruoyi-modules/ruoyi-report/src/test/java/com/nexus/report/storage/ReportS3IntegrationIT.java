package com.nexus.report.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 显式运行的本机MinIO集成，不依赖生产OSS或真实数据。 */
class ReportS3IntegrationIT {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /**
     * 只从本轮忽略文件读取合成测试凭证，禁止写入日志。
     *
     * @return 当前受控处理的固定结果
     */
    private Properties environment() throws Exception {
        String path = System.getProperty("report.test.minio.env");
        if (path == null) throw new IllegalStateException("集成测试需要隔离MinIO环境文件");
        var values = new Properties();
        try (var input = Files.newInputStream(Path.of(path))) {
            values.load(input);
        }
        return values;
    }

    /**
     * 创建仅用于隔离测试的SDK客户端。
     *
     * @param env 当前契约的env配置或状态
     * @return 当前受控处理的固定结果
     */
    private S3Client client(Properties env) {
        return S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:19000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        env.getProperty("MINIO_ROOT_USER"),
                                        env.getProperty("MINIO_ROOT_PASSWORD"))))
                .forcePathStyle(true)
                .build();
    }

    /** 执行twoSeparateNodesUseOneLogicalStoreAndMaterializationIsPrivateAndReleased对应的受控处理。 */
    @Test
    void twoSeparateNodesUseOneLogicalStoreAndMaterializationIsPrivateAndReleased()
            throws Exception {
        var env = environment();
        String bucket = "codex-report-e27-" + UUID.randomUUID().toString().substring(0, 8);
        try (var aClient = client(env);
                var bClient = client(env)) {
            aClient.createBucket(r -> r.bucket(bucket));
            try (var a =
                            new S3ReportArtifactStore(
                                    aClient,
                                    "shared-e27",
                                    bucket,
                                    "private/v1",
                                    directory.resolve("a"),
                                    1024 * 1024);
                    var b =
                            new S3ReportArtifactStore(
                                    bClient,
                                    "shared-e27",
                                    bucket,
                                    "private/v1",
                                    directory.resolve("b"),
                                    1024 * 1024)) {
                byte[] bytes = "9007199254740993.005 / 中文快照".getBytes(StandardCharsets.UTF_8);
                Path staging = a.temporary("A", "rows");
                try (var output = a.output("A", staging)) {
                    output.write(bytes);
                }
                String reference = a.commit("A", staging, "rows");
                assertEquals(a.storeId(), b.storeId());
                assertTrue(b.exists("A", reference));
                assertFalse(b.exists("B", reference));
                assertEquals(bytes.length, b.info("A", reference).length());
                Path materialized;
                try (var file = b.materialize("A", reference)) {
                    materialized = file.path();
                    assertArrayEquals(bytes, Files.readAllBytes(materialized));
                    assertFalse(Files.isSymbolicLink(materialized));
                }
                assertFalse(Files.exists(materialized));
                assertEquals(a.checksum("A", reference), b.checksum("A", reference));
                b.delete("A", reference);
                b.delete("A", reference);
                assertFalse(a.exists("A", reference));
                Path late = a.temporary("A", "rows");
                try (var output = a.output("A", late)) {
                    output.write(bytes);
                }
                assertThrows(ReportFault.class, () -> a.commitAt("A", late, "rows", reference));
                a.discard("A", late);
                assertTrue(b.inventory().isEmpty());
            } finally {
                for (var object : aClient.listObjectsV2(r -> r.bucket(bucket)).contents())
                    aClient.deleteObject(r -> r.bucket(bucket).key(object.key()));
                aClient.deleteBucket(r -> r.bucket(bucket));
            }
        }
    }

    /** 两个实际JVM共享主库与私有对象；强杀首节点后验证另一节点读取及原节点恢复。 */
    @Test
    void separateJvmsSurviveWriterKillAndRecoverOnlyTheirOwnStaging() throws Exception {
        String bucket = "codex-report-e27-" + UUID.randomUUID().toString().substring(0, 8);
        var env = environment();
        try (var f = new RuntimeReviewFixture(directory.resolve("db"));
                var admin = client(env)) {
            ReportExtensionSchema.apply(f.jdbc);
            admin.createBucket(r -> r.bucket(bucket));
            var tcp = Server.createTcpServer("-tcpPort", "0").start();
            Process writer = null;
            try {
                String name =
                        ((JdbcDataSource) f.jdbc.getDataSource())
                                .getURL()
                                .split("mem:")[1]
                                .split(";")[0];
                String url =
                        "jdbc:h2:tcp://127.0.0.1:"
                                + tcp.getPort()
                                + "/mem:"
                                + name
                                + ";MODE=MySQL;LOCK_TIMEOUT=8000";
                Path source = directory.resolve("writer.properties"),
                        reader = directory.resolve("reader.properties"),
                        restart = directory.resolve("restart.properties");
                writer = probe(url, bucket, "a", source, "write", "node-a", source);
                long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (!Files.exists(source) && writer.isAlive() && System.nanoTime() < limit)
                    Thread.sleep(50);
                assertTrue(Files.exists(source), "首JVM未完成合成提交，见隔离探针日志");
                var meta = new Properties();
                try (var input = Files.newInputStream(source)) {
                    meta.load(input);
                }
                writer.destroyForcibly();
                assertTrue(writer.waitFor(5, TimeUnit.SECONDS));
                f.jdbc.update(
                        "UPDATE rpt_artifact_manifest SET lease_until=?,update_time=? WHERE"
                                + " state='STAGED'",
                        Timestamp.from(Instant.now().minusSeconds(3600)),
                        Timestamp.from(Instant.now().minusSeconds(7200)));
                Path stage = Path.of(meta.getProperty("stage"));
                Files.setLastModifiedTime(stage, FileTime.from(Instant.now().minusSeconds(7200)));
                var b = probe(url, bucket, "b", reader, "read", "node-b", source);
                assertTrue(b.waitFor(30, TimeUnit.SECONDS));
                assertEquals(0, b.exitValue(), "第二JVM读取失败，见隔离探针日志");
                assertTrue(Files.exists(stage), "第二节点不得删除原节点暂存");
                var a = probe(url, bucket, "a", restart, "restart", "node-a", source);
                assertTrue(a.waitFor(30, TimeUnit.SECONDS));
                assertEquals(0, a.exitValue(), "原节点重启失败，见隔离探针日志");
                assertFalse(Files.exists(stage));
                assertEquals(
                        1,
                        f.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM rpt_font_resource", Integer.class));
                assertEquals(
                        1,
                        f.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM rpt_snapshot_index", Integer.class));
                assertEquals(
                        0,
                        f.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM rpt_artifact_manifest WHERE state IN"
                                        + " ('STAGING','STAGED','MATERIALIZED')",
                                Integer.class));
                try (var input = Files.newInputStream(reader)) {
                    meta.load(input);
                }
                assertTrue(Long.parseLong(meta.getProperty("rangeOffset")) > 0);
                System.out.println(
                        "隔离双JVM验证：写入="
                                + meta.getProperty("writeMillis")
                                + "ms，索引读取与字体复用="
                                + meta.getProperty("readMillis")
                                + "ms，Range偏移="
                                + meta.getProperty("rangeOffset"));
            } finally {
                if (writer != null && writer.isAlive()) {
                    writer.destroyForcibly();
                    writer.waitFor(5, TimeUnit.SECONDS);
                }
                tcp.stop();
                for (var object : admin.listObjectsV2(r -> r.bucket(bucket)).contents())
                    admin.deleteObject(r -> r.bucket(bucket).key(object.key()));
                admin.deleteBucket(r -> r.bucket(bucket));
            }
        }
    }

    /**
     * 只启动当前Java测试类路径中的隔离探针；不在参数或输出中传递凭证。
     *
     * @param url 当前契约的url配置或状态
     * @param bucket 当前契约的bucket配置或状态
     * @param node 当前契约的node配置或状态
     * @param marker 当前契约的marker配置或状态
     * @param mode 当前契约的mode配置或状态
     * @param owner 资源创建者或当前尝试持有者
     * @param source 当前契约的source配置或状态
     * @return 当前受控处理的固定结果
     */
    private Process probe(
            String url,
            String bucket,
            String node,
            Path marker,
            String mode,
            String owner,
            Path source)
            throws IOException {
        var java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString();
        String classpath =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(
                        java,
                        "-cp",
                        classpath,
                        ReportS3NodeProbe.class.getName(),
                        System.getProperty("report.test.minio.env"),
                        url,
                        bucket,
                        directory.resolve(node).toString(),
                        marker.toString(),
                        mode,
                        owner,
                        source.toString())
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve(node + "-" + mode + ".log").toFile())
                .start();
    }

    /** 伪造retired标记不能隐藏占用实际容量的对象，纳管必须明确拒绝。 */
    @Test
    void retiredMetadataCannotHideLivePayloadFromInventory() throws Exception {
        String bucket = "codex-report-e27-" + UUID.randomUUID().toString().substring(0, 8);
        var env = environment();
        try (var admin = client(env)) {
            admin.createBucket(r -> r.bucket(bucket));
            try (var store =
                    new S3ReportArtifactStore(
                            admin,
                            "shared-e27",
                            bucket,
                            "private/v1",
                            directory.resolve("invalidRetired"),
                            1024 * 1024)) {
                String key = "private/v1/A/" + UUID.randomUUID() + ".rows";
                admin.putObject(
                        r ->
                                r.bucket(bucket)
                                        .key(key)
                                        .metadata(
                                                Map.of(
                                                        "retired",
                                                        "true",
                                                        "store-id",
                                                        "shared-e27")),
                        RequestBody.fromBytes(new byte[] {1, 2, 3}));
                assertEquals(
                        "ARTIFACT_INVALID",
                        assertThrows(ReportFault.class, store::inventory).code());
            } finally {
                for (var object : admin.listObjectsV2(r -> r.bucket(bucket)).contents())
                    admin.deleteObject(r -> r.bucket(bucket).key(object.key()));
                admin.deleteBucket(r -> r.bucket(bucket));
            }
        }
    }
}
