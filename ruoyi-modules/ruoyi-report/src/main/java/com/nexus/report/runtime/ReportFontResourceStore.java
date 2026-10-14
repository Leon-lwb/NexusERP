package com.nexus.report.runtime;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** 同租户字体按实际checksum共享，用使用保留窗口和计划引用防止并发误删。 */
public final class ReportFontResourceStore {
    /**
     * 固定字体资源由私有引用及实际摘要组成。
     *
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     */
    public record Font(String reference, String checksum) {}

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前artifacts状态或配置。 */
    private final ReportArtifactStore artifacts;

    /**
     * 使用主库引用及统一配额存储。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param artifacts 当前契约的artifacts配置或状态
     */
    public ReportFontResourceStore(JdbcTemplate jdbc, ReportArtifactStore artifacts) {
        this.jdbc = jdbc;
        this.artifacts = artifacts;
    }

    /**
     * 完整复制并校验字体，并发首建只保留一个有效引用。
     *
     * @param tenant 租户隔离标识
     * @param source 当前契约的source配置或状态
     * @param cancellation 当前契约的cancellation配置或状态
     * @return 当前受控处理的固定结果
     */
    public Font ensure(String tenant, Path source, ReportCancellation cancellation) {
        Path temporary = artifacts.temporary(tenant, "font");
        String candidate = null;
        boolean kept = false;
        try {
            MessageDigest hash = sha();
            long size = 0;
            try (var input = Files.newInputStream(source);
                    var output = artifacts.output(tenant, temporary)) {
                byte[] bytes = new byte[65536];
                int length;
                while ((length = input.read(bytes)) >= 0) {
                    cancellation.check();
                    if (length == 0) continue;
                    size += length;
                    if (size > 64L * 1024 * 1024) throw ReportFault.capacity();
                    hash.update(bytes, 0, length);
                    output.write(bytes, 0, length);
                }
            }
            String checksum = HexFormat.of().formatHex(hash.digest());
            var existing = find(tenant, checksum);
            if (existing.isPresent() && artifacts.exists(tenant, existing.get().reference())) {
                touch(tenant, checksum);
                verify(tenant, existing.get());
                return existing.get();
            }
            candidate = artifacts.commit(tenant, temporary, "font");
            Font font = new Font(candidate, checksum);
            verify(tenant, font);
            try {
                jdbc.update(
                        "INSERT INTO rpt_font_resource(tenant_id,checksum,font_ref,lease_until)"
                                + " VALUES(?,?,?,?)",
                        tenant,
                        checksum,
                        candidate,
                        Timestamp.from(Instant.now().plusSeconds(3600)));
                kept = true;
                return font;
            } catch (org.springframework.dao.DuplicateKeyException concurrent) {
                var winner = find(tenant, checksum).orElseThrow();
                if (!artifacts.exists(tenant, winner.reference())) {
                    if (jdbc.update(
                                    "UPDATE rpt_font_resource SET font_ref=?,lease_until=? WHERE"
                                            + " tenant_id=? AND checksum=? AND font_ref=?",
                                    candidate,
                                    Timestamp.from(Instant.now().plusSeconds(3600)),
                                    tenant,
                                    checksum,
                                    winner.reference())
                            == 1) {
                        kept = true;
                        return font;
                    }
                    winner = find(tenant, checksum).orElseThrow();
                }
                touch(tenant, checksum);
                verify(tenant, winner);
                return winner;
            }
        } catch (IOException failure) {
            throw invalid();
        } finally {
            artifacts.discard(tenant, temporary);
            if (!kept && candidate != null) artifacts.delete(tenant, candidate);
        }
    }

    /**
     * 计划引用登记后，即使超出使用窗口也不能提前回收字体。
     *
     * @param tenant 租户隔离标识
     * @param plan 当前契约的plan配置或状态
     * @param font 固定私有字体引用
     */
    public void link(String tenant, String plan, Font font) {
        verify(tenant, font);
        jdbc.update(
                "INSERT INTO rpt_plan_font(tenant_id,plan_ref,font_ref,font_checksum)"
                        + " VALUES(?,?,?,?)",
                tenant,
                plan,
                font.reference(),
                font.checksum());
        touch(tenant, font.checksum());
    }

    /**
     * 读取固定字体前核对实际内容，物化在回调完成后释放。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     * @param cancellation 当前契约的cancellation配置或状态
     * @param action 受控资源操作
     */
    public void withFont(
            String tenant,
            String reference,
            String checksum,
            ReportCancellation cancellation,
            Consumer<Path> action) {
        var font = new Font(reference, checksum);
        verify(tenant, font);
        touch(tenant, checksum);
        try (var materialized = artifacts.materialize(tenant, reference)) {
            cancellation.check();
            action.accept(materialized.path());
            cancellation.check();
        }
    }

    /**
     * 使用窗口至少覆盖所有当前计划构建和导出任务。
     *
     * @param tenant 租户隔离标识
     * @param checksum 实际内容的SHA-256校验和
     */
    private void touch(String tenant, String checksum) {
        jdbc.update(
                "UPDATE rpt_font_resource SET lease_until=? WHERE tenant_id=? AND checksum=?",
                Timestamp.from(Instant.now().plusSeconds(3600)),
                tenant,
                checksum);
    }

    /**
     * 精确摘要唯一键读取，不依赖字体名称相同。
     *
     * @param tenant 租户隔离标识
     * @param checksum 实际内容的SHA-256校验和
     * @return 当前受控处理的固定结果
     */
    private Optional<Font> find(String tenant, String checksum) {
        return jdbc
                .query(
                        "SELECT font_ref,checksum FROM rpt_font_resource WHERE tenant_id=? AND"
                                + " checksum=?",
                        (r, i) -> new Font(r.getString(1), r.getString(2)),
                        tenant,
                        checksum)
                .stream()
                .findFirst();
    }

    /**
     * 来源缺失或被修改都明确失败，不能悄悄换字体重排旧计划。
     *
     * @param tenant 租户隔离标识
     * @param font 固定私有字体引用
     */
    private void verify(String tenant, Font font) {
        artifacts.pin(tenant, font.reference(), 3600);
        if (font.reference() == null
                || !font.reference().matches("[a-f0-9-]{36}\\.font")
                || !font.checksum().equals(artifacts.checksum(tenant, font.reference())))
            throw invalid();
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
     * 不向调用者透出字体或对象路径。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault invalid() {
        return new ReportFault("FONT_RESOURCE_INVALID", "固定字体资源缺失或内容已变化", 409);
    }
}
