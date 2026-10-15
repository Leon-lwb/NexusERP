package com.nexus.report.runtime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.DatasetAccessException;
import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.RenderPlan;
import com.nexus.report.storage.ReportArtifactStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 一份不可变私有产物同时保存测量后的布局和实际使用的字体字节。 */
public final class ReportRenderPlanStore {
    /** 保存当前 {@code JSONLIMIT} 值。 */
    private static final long JSON_LIMIT = 32L * 1024 * 1024;

    /** 保存当前 {@code FONTLIMIT} 值。 */
    private static final long FONT_LIMIT = 64L * 1024 * 1024;

    /** 保存当前 {@code PLAN} 值。 */
    private static final String PLAN = "plan.json";

    /** 保存当前 {@code FONT} 值。 */
    private static final String FONT = "font.bin";

    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /** 待处理定义文本。 */
    private final ObjectMapper json;

    /** 保存当前fonts状态或配置。 */
    private ReportFontResourceStore fonts;

    /**
     * 已安装扩展结构后显式启用共享私有字体。
     *
     * @param fonts 当前契约的fonts配置或状态
     */
    public void fonts(ReportFontResourceStore fonts) {
        this.fonts = fonts;
    }

    /**
     * 初始化报表渲染计划存储并保存所需依赖或不可变值。
     *
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param json 待处理定义文本
     */
    public ReportRenderPlanStore(ReportArtifactStore artifacts, ObjectMapper json) {
        this.artifacts = artifacts;
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.json.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    /**
     * 布局和固定字体共同保存的私有产物，重启后不重新计算分页。
     *
     * @param formatVersion 契约格式版本
     * @param engine 当前契约的engine配置或状态
     * @param runId 当前契约的runId配置或状态
     * @param snapshotChecksum 当前契约的snapshotChecksum配置或状态
     * @param fontChecksum 当前契约的fontChecksum配置或状态
     * @param plan 当前契约的plan配置或状态
     * @param fontReference 当前契约的fontReference配置或状态
     */
    public record StoredPlan(
            int formatVersion,
            String engine,
            String runId,
            String snapshotChecksum,
            String fontChecksum,
            RenderPlan plan,
            @com.fasterxml.jackson.annotation.JsonInclude(
                            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    String fontReference) {
        /**
         * 保留第一版归档构造契约。
         *
         * @param formatVersion 契约格式版本
         * @param engine 当前契约的engine配置或状态
         * @param runId 当前契约的runId配置或状态
         * @param snapshotChecksum 当前契约的snapshotChecksum配置或状态
         * @param fontChecksum 当前契约的fontChecksum配置或状态
         * @param plan 当前契约的plan配置或状态
         */
        public StoredPlan(
                int formatVersion,
                String engine,
                String runId,
                String snapshotChecksum,
                String fontChecksum,
                RenderPlan plan) {
            this(formatVersion, engine, runId, snapshotChecksum, fontChecksum, plan, null);
        }
    }

    /**
     * 产物，负责当前模块对应的契约或处理职责。
     *
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     * @param plan 当前契约的plan配置或状态
     */
    public record Artifact(String reference, String checksum, RenderPlan plan) {}

    /**
     * 创建当前租户有权限的资源。
     *
     * @param tenant 当前租户标识
     * @param runId 当前操作的 {@code runId} 输入
     * @param snapshotChecksum 当前操作的 {@code snapshotChecksum} 输入
     * @param engine 执行器版本
     * @param sourceFont 当前操作的 {@code sourceFont} 输入
     * @param cancellation 执行取消信号
     * @param measure 当前操作的 {@code measure} 输入
     * @return 当前处理返回的固定结果
     */
    public Artifact create(
            String tenant,
            String runId,
            String snapshotChecksum,
            String engine,
            Path sourceFont,
            ReportCancellation cancellation,
            Function<Path, RenderPlan> measure) {
        cancellation.check();
        if (fonts != null)
            return createShared(
                    tenant, runId, snapshotChecksum, engine, sourceFont, cancellation, measure);
        Path font = artifacts.temporary(tenant, "plan");
        Path archive = null;
        String committed = null;
        try {
            String fontChecksum;
            try (var input = Files.newInputStream(sourceFont);
                    var output = artifacts.output(tenant, font)) {
                fontChecksum = copy(input, output, FONT_LIMIT, cancellation);
            }
            cancellation.check();
            RenderPlan plan = measure.apply(font);
            cancellation.check();
            var stored = new StoredPlan(1, engine, runId, snapshotChecksum, fontChecksum, plan);
            archive = artifacts.temporary(tenant, "plan");
            try (var zip = new ZipOutputStream(artifacts.output(tenant, archive))) {
                zip.putNextEntry(new ZipEntry(PLAN));
                json.writeValue(new BudgetOutput(zip, JSON_LIMIT, cancellation), stored);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(FONT));
                try (var input = Files.newInputStream(font)) {
                    copy(input, zip, FONT_LIMIT, cancellation);
                }
                zip.closeEntry();
            }
            cancellation.check();
            committed = artifacts.commit(tenant, archive, "plan");
            cancellation.check();
            String checksum = artifacts.checksum(tenant, committed);
            cancellation.check();
            return new Artifact(committed, checksum, plan);
        } catch (IOException failure) {
            if (committed != null) artifacts.delete(tenant, committed);
            /** 序列化器可能将写入时的控制信号包装为映射异常，需要保留原始取消语义。 */
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ReportFault fault) throw fault;
                if (cause instanceof DatasetAccessException fault) throw fault;
            }
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "排版产物保存失败", 500);
        } catch (RuntimeException failure) {
            if (committed != null) artifacts.delete(tenant, committed);
            throw failure;
        } finally {
            artifacts.discard(tenant, font);
            if (archive != null) artifacts.discard(tenant, archive);
        }
    }

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param checksum 预期完整性校验和
     * @param runId 当前操作的 {@code runId} 输入
     * @param snapshotChecksum 当前操作的 {@code snapshotChecksum} 输入
     * @param engine 执行器版本
     * @param rows 待处理业务行
     * @param cancellation 执行取消信号
     * @return 当前处理返回的固定结果
     */
    /**
     * 第二版归档只封装计划，字体通过不可变私有checksum资源复用。
     *
     * @param tenant 租户隔离标识
     * @param runId 当前契约的runId配置或状态
     * @param snapshotChecksum 当前契约的snapshotChecksum配置或状态
     * @param engine 当前契约的engine配置或状态
     * @param source 当前契约的source配置或状态
     * @param cancellation 当前契约的cancellation配置或状态
     * @param measure 当前契约的measure配置或状态
     * @return 当前受控处理的固定结果
     */
    private Artifact createShared(
            String tenant,
            String runId,
            String snapshotChecksum,
            String engine,
            Path source,
            ReportCancellation cancellation,
            Function<Path, RenderPlan> measure) {
        var font = fonts.ensure(tenant, source, cancellation);
        var result = new java.util.concurrent.atomic.AtomicReference<RenderPlan>();
        fonts.withFont(
                tenant,
                font.reference(),
                font.checksum(),
                cancellation,
                path -> result.set(measure.apply(path)));
        var plan = result.get();
        Path temporary = artifacts.temporary(tenant, "plan");
        String committed = null;
        boolean registered = false;
        try {
            var stored =
                    new StoredPlan(
                            2,
                            engine,
                            runId,
                            snapshotChecksum,
                            font.checksum(),
                            plan,
                            font.reference());
            try (var zip = new ZipOutputStream(artifacts.output(tenant, temporary))) {
                zip.putNextEntry(new ZipEntry(PLAN));
                json.writeValue(new BudgetOutput(zip, JSON_LIMIT, cancellation), stored);
                zip.closeEntry();
            }
            cancellation.check();
            committed = artifacts.commit(tenant, temporary, "plan");
            String checksum = artifacts.checksum(tenant, committed);
            fonts.link(tenant, committed, font);
            registered = true;
            cancellation.check();
            return new Artifact(committed, checksum, plan);
        } catch (IOException failure) {
            throw invalid();
        } finally {
            artifacts.discard(tenant, temporary);
            if (!registered && committed != null) artifacts.delete(tenant, committed);
        }
    }

    /**
     * 读取当前租户已授权的固定内容。
     *
     * @param tenant 当前受控操作的tenant输入
     * @param reference 当前受控操作的reference输入
     * @param checksum 当前受控操作的checksum输入
     * @param runId 当前受控操作的runId输入
     * @param snapshotChecksum 当前受控操作的snapshotChecksum输入
     * @param engine 当前受控操作的engine输入
     * @param rows 当前受控操作的rows输入
     * @param cancellation 当前受控操作的cancellation输入
     * @return 当前固定契约或处理结果
     */
    public RenderPlan read(
            String tenant,
            String reference,
            String checksum,
            String runId,
            String snapshotChecksum,
            String engine,
            long rows,
            ReportCancellation cancellation) {
        verify(tenant, reference, checksum, cancellation);
        try (var materialized = artifacts.materialize(tenant, reference);
                var zip = new ZipFile(materialized.path().toFile())) {
            return metadata(zip, runId, snapshotChecksum, engine, rows).plan();
        } catch (IOException | IllegalArgumentException failure) {
            throw invalid();
        }
    }

    /**
     * 在操作生命周期内提供完整性已校验的固定字体。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param checksum 预期完整性校验和
     * @param runId 当前操作的 {@code runId} 输入
     * @param snapshotChecksum 当前操作的 {@code snapshotChecksum} 输入
     * @param engine 执行器版本
     * @param rows 待处理业务行
     * @param cancellation 执行取消信号
     * @param action 资源操作
     */
    public void withFont(
            String tenant,
            String reference,
            String checksum,
            String runId,
            String snapshotChecksum,
            String engine,
            long rows,
            ReportCancellation cancellation,
            Consumer<Path> action) {
        verify(tenant, reference, checksum, cancellation);
        Path font = null;
        try (var materialized = artifacts.materialize(tenant, reference);
                var zip = new ZipFile(materialized.path().toFile())) {
            var stored = metadata(zip, runId, snapshotChecksum, engine, rows);
            if (stored.formatVersion() == 2) {
                if (fonts == null) throw invalid();
                fonts.withFont(
                        tenant,
                        stored.fontReference(),
                        stored.fontChecksum(),
                        cancellation,
                        action);
                return;
            }
            font = artifacts.temporary(tenant, "plan");
            try (var input = zip.getInputStream(zip.getEntry(FONT));
                    var output = artifacts.output(tenant, font)) {
                if (!stored.fontChecksum().equals(copy(input, output, FONT_LIMIT, cancellation)))
                    throw invalid();
            }
            cancellation.check();
            action.accept(font);
            cancellation.check();
        } catch (IOException | IllegalArgumentException failure) {
            throw invalid();
        } finally {
            if (font != null) artifacts.discard(tenant, font);
        }
    }

    /**
     * 校验资源绑定与完整性。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param checksum 预期完整性校验和
     * @param cancellation 执行取消信号
     */
    private void verify(
            String tenant, String reference, String checksum, ReportCancellation cancellation) {
        cancellation.check();
        if (checksum == null || !checksum.equals(artifacts.checksum(tenant, reference)))
            throw invalid();
        cancellation.check();
    }

    /**
     * 完成 {@code metadata} 对应的报表渲染计划存储处理。
     *
     * @param zip 当前操作的 {@code zip} 输入
     * @param runId 当前操作的 {@code runId} 输入
     * @param snapshotChecksum 当前操作的 {@code snapshotChecksum} 输入
     * @param engine 执行器版本
     * @param rows 待处理业务行
     * @return 当前处理返回的固定结果
     */
    private StoredPlan metadata(
            ZipFile zip, String runId, String snapshotChecksum, String engine, long rows)
            throws IOException {
        var plan = zip.getEntry(PLAN);
        var font = zip.getEntry(FONT);
        if (plan == null || plan.isDirectory() || plan.getSize() < 1 || plan.getSize() > JSON_LIMIT)
            throw invalid();
        StoredPlan stored;
        try (var input = zip.getInputStream(plan)) {
            stored = json.readValue(new BudgetInput(input, JSON_LIMIT), StoredPlan.class);
        }
        if (stored == null
                || !java.util.Set.of(1, 2).contains(stored.formatVersion())
                || !engine.equals(stored.engine())
                || !runId.equals(stored.runId())
                || snapshotChecksum == null
                || !snapshotChecksum.equals(stored.snapshotChecksum())
                || stored.fontChecksum() == null
                || !stored.fontChecksum().matches("[a-f0-9]{64}")
                || stored.plan() == null
                || (stored.plan().schemaVersion() != 1
                        && !(engine.equals("2.0.0") && stored.plan().schemaVersion() == 2))
                || stored.plan().rowCount() != rows
                || stored.plan().pages().isEmpty()
                || stored.plan().pages().size() > 200) throw invalid();
        if (stored.formatVersion() == 1) {
            if (zip.size() != 2
                    || font == null
                    || font.isDirectory()
                    || font.getSize() < 1
                    || font.getSize() > FONT_LIMIT
                    || stored.fontReference() != null) throw invalid();
        } else if (zip.size() != 1
                || font != null
                || stored.fontReference() == null
                || !stored.fontReference().matches("[a-f0-9-]{36}\\.font")) throw invalid();
        return stored;
    }

    /**
     * 复制已授权定义并清除业务标识默认值。
     *
     * @param input 待校验输入
     * @param output 受控输出流
     * @param limit 当前操作的 {@code limit} 输入
     * @param cancellation 执行取消信号
     * @return 当前处理返回的固定结果
     */
    private static String copy(
            InputStream input, OutputStream output, long limit, ReportCancellation cancellation)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        byte[] bytes = new byte[8192];
        long count = 0;
        int size;
        while ((size = input.read(bytes)) != -1) {
            cancellation.check();
            count += size;
            if (count > limit) throw ReportFault.capacity();
            digest.update(bytes, 0, size);
            output.write(bytes, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 完成 {@code invalid} 对应的报表渲染计划存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault invalid() {
        return new ReportFault("PLAN_INVALID", "排版产物完整性或版本校验失败", 500);
    }

    /** BudgetOutput，负责当前模块对应的契约或处理职责。 */
    private static final class BudgetOutput extends FilterOutputStream {
        /** 保存当前 {@code limit} 值。 */
        private final long limit;

        /** 执行取消信号。 */
        private final ReportCancellation cancellation;

        /** 保存当前 {@code count} 值。 */
        private long count;

        /**
         * 初始化BudgetOutput并保存所需依赖或不可变值。
         *
         * @param output 受控输出流
         * @param limit 当前操作的 {@code limit} 输入
         * @param cancellation 执行取消信号
         */
        BudgetOutput(OutputStream output, long limit, ReportCancellation cancellation) {
            super(output);
            this.limit = limit;
            this.cancellation = cancellation;
        }

        /**
         * 向受控输出流写入固定报表结果。
         *
         * @param value 当前操作的 {@code value} 输入
         */
        @Override
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
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            budget(length);
            out.write(bytes, offset, length);
        }

        /**
         * 完成 {@code budget} 对应的BudgetOutput处理。
         *
         * @param size 有界页大小
         */
        private void budget(int size) {
            cancellation.check();
            count += size;
            if (count > limit) throw ReportFault.capacity();
        }
    }

    /** BudgetInput，负责当前模块对应的契约或处理职责。 */
    private static final class BudgetInput extends FilterInputStream {
        /** 保存当前 {@code limit} 值。 */
        private final long limit;

        /** 保存当前 {@code count} 值。 */
        private long count;

        /**
         * 初始化BudgetInput并保存所需依赖或不可变值。
         *
         * @param input 待校验输入
         * @param limit 当前操作的 {@code limit} 输入
         */
        BudgetInput(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        /**
         * 读取并检查当前租户私有产物。
         *
         * @return 当前处理返回的固定结果
         */
        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) budget(1);
            return value;
        }

        /**
         * 读取并检查当前租户私有产物。
         *
         * @param bytes 受控字节数据
         * @param offset 当前操作的 {@code offset} 输入
         * @param length 当前操作的 {@code length} 输入
         * @return 当前处理返回的固定结果
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int size = in.read(bytes, offset, length);
            if (size > 0) budget(size);
            return size;
        }

        /**
         * 完成 {@code budget} 对应的BudgetInput处理。
         *
         * @param size 有界页大小
         */
        private void budget(int size) {
            count += size;
            if (count > limit) throw invalid();
        }
    }
}
