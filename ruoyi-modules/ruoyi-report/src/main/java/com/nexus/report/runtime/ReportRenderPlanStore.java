package com.nexus.report.runtime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.ReportCancellation;
import com.nexus.common.report.DatasetAccessException;
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

/** One immutable private object owns both the measured layout and its exact font bytes. */
public final class ReportRenderPlanStore {
    private static final long JSON_LIMIT = 32L * 1024 * 1024;
    private static final long FONT_LIMIT = 64L * 1024 * 1024;
    private static final String PLAN = "plan.json";
    private static final String FONT = "font.bin";
    private final ReportArtifactStore artifacts;
    private final ObjectMapper json;

    public ReportRenderPlanStore(ReportArtifactStore artifacts, ObjectMapper json) {
        this.artifacts = artifacts;
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.json.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }
    public record StoredPlan(int formatVersion, String engine, String runId, String snapshotChecksum,
                             String fontChecksum, RenderPlan plan) { }
    public record Artifact(String reference, String checksum, RenderPlan plan) { }

    public Artifact create(String tenant, String runId, String snapshotChecksum, String engine, Path sourceFont,
                           ReportCancellation cancellation, Function<Path, RenderPlan> measure) {
        cancellation.check();
        Path font = artifacts.temporary(tenant, "plan");
        Path archive = null;
        String committed = null;
        try {
            String fontChecksum;
            try (var input = Files.newInputStream(sourceFont); var output = artifacts.output(tenant, font)) {
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
                try (var input = Files.newInputStream(font)) { copy(input, zip, FONT_LIMIT, cancellation); }
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
            // Jackson wraps write-time control signals in JsonMappingException (an IOException).
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

    public RenderPlan read(String tenant, String reference, String checksum, String runId,
                           String snapshotChecksum, String engine, long rows, ReportCancellation cancellation) {
        verify(tenant, reference, checksum, cancellation);
        try (var zip = new ZipFile(artifacts.read(tenant, reference).toFile())) {
            return metadata(zip, runId, snapshotChecksum, engine, rows).plan();
        } catch (IOException | IllegalArgumentException failure) { throw invalid(); }
    }

    public void withFont(String tenant, String reference, String checksum, String runId,
                         String snapshotChecksum, String engine, long rows, ReportCancellation cancellation,
                         Consumer<Path> action) {
        verify(tenant, reference, checksum, cancellation);
        Path font = null;
        try (var zip = new ZipFile(artifacts.read(tenant, reference).toFile())) {
            var stored = metadata(zip, runId, snapshotChecksum, engine, rows);
            font = artifacts.temporary(tenant, "plan");
            try (var input = zip.getInputStream(zip.getEntry(FONT)); var output = artifacts.output(tenant, font)) {
                if (!stored.fontChecksum().equals(copy(input, output, FONT_LIMIT, cancellation))) throw invalid();
            }
            cancellation.check();
            action.accept(font);
            cancellation.check();
        } catch (IOException | IllegalArgumentException failure) { throw invalid(); }
        finally { if (font != null) artifacts.discard(tenant, font); }
    }

    private void verify(String tenant, String reference, String checksum, ReportCancellation cancellation) {
        cancellation.check();
        if (checksum == null || !checksum.equals(artifacts.checksum(tenant, reference))) throw invalid();
        cancellation.check();
    }
    private StoredPlan metadata(ZipFile zip, String runId, String snapshotChecksum, String engine, long rows) throws IOException {
        var plan = zip.getEntry(PLAN); var font = zip.getEntry(FONT);
        if (zip.size() != 2 || plan == null || font == null || plan.isDirectory() || font.isDirectory()
            || plan.getSize() < 1 || plan.getSize() > JSON_LIMIT || font.getSize() < 1 || font.getSize() > FONT_LIMIT) throw invalid();
        StoredPlan stored;
        try (var input = zip.getInputStream(plan)) {
            stored = json.readValue(new BudgetInput(input, JSON_LIMIT), StoredPlan.class);
        }
        if (stored == null || stored.formatVersion() != 1 || !engine.equals(stored.engine())
            || !runId.equals(stored.runId()) || snapshotChecksum == null || !snapshotChecksum.equals(stored.snapshotChecksum())
            || stored.fontChecksum() == null || !stored.fontChecksum().matches("[a-f0-9]{64}")
            || stored.plan() == null || stored.plan().schemaVersion() != 1 || stored.plan().rowCount() != rows
            || stored.plan().pages().isEmpty() || stored.plan().pages().size() > 200) throw invalid();
        return stored;
    }
    private static String copy(InputStream input, OutputStream output, long limit, ReportCancellation cancellation) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        byte[] bytes = new byte[8192]; long count = 0; int size;
        while ((size = input.read(bytes)) != -1) {
            cancellation.check(); count += size;
            if (count > limit) throw ReportFault.capacity();
            digest.update(bytes, 0, size); output.write(bytes, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static ReportFault invalid() { return new ReportFault("PLAN_INVALID", "排版产物完整性或版本校验失败", 500); }
    private static final class BudgetOutput extends FilterOutputStream {
        private final long limit; private final ReportCancellation cancellation; private long count;
        BudgetOutput(OutputStream output, long limit, ReportCancellation cancellation) { super(output); this.limit = limit; this.cancellation = cancellation; }
        @Override public void write(int value) throws IOException { budget(1); out.write(value); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException { budget(length); out.write(bytes, offset, length); }
        private void budget(int size) { cancellation.check(); count += size; if (count > limit) throw ReportFault.capacity(); }
    }
    private static final class BudgetInput extends FilterInputStream {
        private final long limit; private long count;
        BudgetInput(InputStream input, long limit) { super(input); this.limit = limit; }
        @Override public int read() throws IOException { int value = in.read(); if (value != -1) budget(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int size = in.read(bytes, offset, length); if (size > 0) budget(size); return size; }
        private void budget(int size) { count += size; if (count > limit) throw invalid(); }
    }
}
