package com.nexus.report.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.engine.FontSupport;
import com.nexus.report.runtime.*;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** 独立JVM探针只访问测试传入的隔离H2/MinIO，输出合成快照和测量值。 */
public final class ReportS3NodeProbe {
    /**
     * 按写入、跨节点读取或同节点重启模式运行；凭证只从忽略文件读取。
     *
     * @param args 当前契约的args配置或状态
     */
    public static void main(String[] args) throws Exception {
        var env = new Properties();
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            env.load(input);
        }
        var ds = new JdbcDataSource();
        ds.setURL(args[1]);
        var jdbc = new JdbcTemplate(ds);
        var local = Path.of(args[3]);
        var marker = Path.of(args[4]);
        var mode = args[5];
        try (var client =
                        S3Client.builder()
                                .endpointOverride(URI.create("http://127.0.0.1:19000"))
                                .region(Region.US_EAST_1)
                                .credentialsProvider(
                                        StaticCredentialsProvider.create(
                                                AwsBasicCredentials.create(
                                                        env.getProperty("MINIO_ROOT_USER"),
                                                        env.getProperty("MINIO_ROOT_PASSWORD"))))
                                .forcePathStyle(true)
                                .build();
                var physical =
                        new S3ReportArtifactStore(
                                client,
                                "shared-e27",
                                args[2],
                                "private/v1",
                                local,
                                64L * 1024 * 1024)) {
            var quota =
                    new ReportQuotaService(jdbc, 512L * 1024 * 1024, 256L * 1024 * 1024, 1000, 500);
            var store = new QuotaReportArtifactStore(physical, quota, 64L * 1024 * 1024, args[6]);
            var values = new Properties();
            var json = new ObjectMapper();
            long started = System.nanoTime();
            if (mode.equals("write")) {
                var temporary = store.temporary("A", "rows");
                try (var output = store.output("A", temporary)) {
                    for (int i = 0; i < 1600; i++) {
                        var row =
                                Map.of(
                                        "id",
                                        TypedValue.of(ValueType.ID, Long.toString(i + 1)),
                                        "amount",
                                        TypedValue.of(ValueType.DECIMAL, "9007199254740993.005"));
                        output.write(
                                (json.writeValueAsString(row) + "\n")
                                        .getBytes(StandardCharsets.UTF_8));
                    }
                }
                String reference = store.commit("A", temporary, "rows");
                var font =
                        new ReportFontResourceStore(jdbc, store)
                                .ensure("A", FontSupport.find(null), new ReportCancellation());
                var pending = store.temporary("A", "rows");
                try (var output = store.output("A", pending)) {
                    output.write("restart-only-stage".getBytes(StandardCharsets.UTF_8));
                }
                values.setProperty("snapshot", reference);
                values.setProperty("font", font.reference());
                values.setProperty("stage", pending.toString());
                values.setProperty(
                        "writeMillis", Long.toString((System.nanoTime() - started) / 1000000));
                try (var output = Files.newOutputStream(marker)) {
                    values.store(output, "合成跨进程测试");
                }
                System.in.read();
            } else {
                try (var input = Files.newInputStream(Path.of(args[7]))) {
                    values.load(input);
                }
                var index = new ReportSnapshotIndexService(jdbc, store, json);
                var page = index.page("A", values.getProperty("snapshot"), 1500, 30);
                if (page.size() != 30
                        || !page.get(0).get("id").value().equals("1501")
                        || !page.get(0).get("amount").value().equals("9007199254740993.005")
                        || index.lastSeekOffset() <= 0)
                    throw new IllegalStateException("跨进程范围读取不符合固定快照");
                var font =
                        new ReportFontResourceStore(jdbc, store)
                                .ensure("A", FontSupport.find(null), new ReportCancellation());
                if (!font.reference().equals(values.getProperty("font")))
                    throw new IllegalStateException("字体未按checksum共享");
                Path materialized;
                try (var file = store.materialize("A", values.getProperty("snapshot"))) {
                    materialized = file.path();
                    if (Files.size(materialized) == 0) throw new IllegalStateException("物化为空");
                }
                if (Files.exists(materialized)) throw new IllegalStateException("物化未释放");
                store.reap(Instant.now().minusSeconds(3600), (tenant, reference) -> true);
                values.setProperty("rangeOffset", Long.toString(index.lastSeekOffset()));
                values.setProperty(
                        "readMillis", Long.toString((System.nanoTime() - started) / 1000000));
                try (var output = Files.newOutputStream(marker)) {
                    values.store(output, "合成跨进程测试");
                }
            }
        }
    }
}
