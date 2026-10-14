package com.nexus.report.export;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** 每次执行持有持久化尝试序号围栏，本地线程不保存登录令牌。 */
public class ReportExportWorker implements AutoCloseable {
    /** Renderer，负责当前模块对应的契约或处理职责。 */
    @FunctionalInterface
    public interface Renderer {
        /**
         * 向受控输出流写入固定报表结果。
         *
         * @param job 当前导出任务
         * @param access 当前操作的 {@code access} 输入
         * @param output 受控输出流
         * @param cancellation 执行取消信号
         */
        void write(
                ExportJob job,
                ReportRuntimeService.Access access,
                OutputStream output,
                ReportCancellation cancellation)
                throws IOException;
    }

    /** 保存当前 {@code LOG} 值。 */
    private static final Logger LOG = LoggerFactory.getLogger(ReportExportWorker.class);

    /** 保存当前 {@code RETRYABLE} 值。 */
    private static final Set<String> RETRYABLE =
            Set.of(
                    "ARTIFACT_STORE_UNAVAILABLE",
                    "STORAGE_UNAVAILABLE",
                    "STORAGE_FAILED",
                    "PDF_EXPORT_FAILED",
                    "XLSX_EXPORT_FAILED",
                    "REPORT_EXECUTION_FAILED");

    /** 租户化元数据或私有存储。 */
    private final JdbcReportStore store;

    /** 保存当前 {@code runtime} 值。 */
    private final ReportRuntimeService runtime;

    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /** 保存当前 {@code properties} 值。 */
    private final ReportProperties properties;

    /** 保存当前 {@code renderer} 值。 */
    private final Renderer renderer;

    /** 保存当前 {@code executor} 值。 */
    private final ExecutorService executor;

    /** 保存当前 {@code heartbeat} 值。 */
    private final ScheduledExecutorService heartbeat;

    /** 保存当前 {@code active} 值。 */
    private final Map<String, ReportCancellation> active = new ConcurrentHashMap<>();

    /** 租约或资源持有者。 */
    private final String owner = UUID.randomUUID().toString();

    /** 保存当前 {@code closed} 值。 */
    private volatile boolean closed;

    /**
     * 初始化报表导出工作器并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param runtime 当前操作的 {@code runtime} 输入
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @param renderer 当前操作的 {@code renderer} 输入
     */
    public ReportExportWorker(
            JdbcReportStore store,
            ReportRuntimeService runtime,
            ReportArtifactStore artifacts,
            ReportProperties properties,
            Renderer renderer) {
        this.store = store;
        this.runtime = runtime;
        this.artifacts = artifacts;
        this.properties = properties;
        this.renderer = renderer;
        executor =
                Executors.newFixedThreadPool(
                        properties.getWorkers(),
                        r -> {
                            var thread = new Thread(r, "report-export");
                            thread.setDaemon(true);
                            return thread;
                        });
        heartbeat =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            var thread = new Thread(r, "report-export-lease");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /** 完成 {@code scan} 对应的报表导出工作器处理。 */
    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public synchronized void scan() {
        if (closed) return;
        try {
            for (var candidate : store.claimableJobs()) {
                if (active.size() >= properties.getWorkers()) break;
                if (active.containsKey(candidate.id())) continue;
                var acquired = store.claimJob(candidate, owner, Instant.now().plusSeconds(30));
                if (acquired.isEmpty()) continue;
                var job = acquired.get();
                active.put(job.id(), new ReportCancellation());
                try {
                    executor.submit(() -> execute(job));
                } catch (RejectedExecutionException e) {
                    active.remove(job.id());
                    store.retryJob(job, owner, "REPORT_EXECUTION_FAILED");
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Report queue scan failed ({})", ReportRuntimeService.code(e));
        }
    }

    /**
     * 完成 {@code kind} 对应的报表导出工作器处理。
     *
     * @param job 当前导出任务
     * @return 当前处理返回的固定结果
     */
    private static String kind(ExportJob job) {
        return switch (job.format()) {
            case "PDF" -> "pdf";
            case "XLSX_DATA", "XLSX_LAYOUT" -> "xlsx";
            default -> throw ReportFault.invalid("导出格式不支持");
        };
    }

    /**
     * 完成 {@code checkLease} 对应的报表导出工作器处理。
     *
     * @param job 当前导出任务
     * @param cancellation 执行取消信号
     * @param deadline 当前操作的 {@code deadline} 输入
     */
    private void checkLease(ExportJob job, ReportCancellation cancellation, Instant deadline) {
        if (cancellation.cancelled()) {
            cancellation.check();
            return;
        }
        var now = Instant.now();
        if (!now.isBefore(deadline)) cancellation.cancel("TIMEOUT");
        else if (!job.expires().isAfter(now)) cancellation.cancel("SNAPSHOT_EXPIRED");
        else {
            var current = store.job(job.tenant(), job.id()).orElse(null);
            if (current == null
                    || current.attempts() != job.attempts()
                    || !Objects.equals(current.leaseOwner(), job.leaseOwner()))
                cancellation.cancel("LEASE_LOST");
            else if (current.cancelled() || current.state().equals("CANCEL_REQUESTED"))
                cancellation.cancel("CANCELLED");
            else if (!current.expires().isAfter(now)) cancellation.cancel("SNAPSHOT_EXPIRED");
            else if (!current.state().equals("RUNNING")
                    || current.leaseUntil() == null
                    || !current.leaseUntil().isAfter(now)) cancellation.cancel("LEASE_LOST");
        }
        cancellation.check();
    }

    /**
     * 完成 {@code renewLease} 对应的报表导出工作器处理。
     *
     * @param job 当前导出任务
     * @param cancellation 执行取消信号
     * @param deadline 当前操作的 {@code deadline} 输入
     */
    void renewLease(ExportJob job, ReportCancellation cancellation, Instant deadline) {
        try {
            checkLease(job, cancellation, deadline);
            runtime.execution(job);
            cancellation.check();
            if (!store.heartbeat(job, job.leaseOwner(), Instant.now().plusSeconds(30)))
                cancellation.cancel("LEASE_LOST");
        } catch (RuntimeException e) {
            if (!cancellation.cancelled()) cancellation.cancel(ReportRuntimeService.code(e));
        }
    }

    /**
     * 以当前任务围栏执行取数和导出。
     *
     * @param job 当前导出任务
     */
    void execute(ExportJob job) {
        String leaseOwner = job.leaseOwner();
        var cancellation = active.computeIfAbsent(job.id(), key -> new ReportCancellation());
        Instant deadline = Instant.now().plusSeconds(properties.getTaskSeconds());
        ScheduledFuture<?> monitor = null;
        Path temporary = null;
        String file = null;
        try {
            if (closed) cancellation.cancel("SHUTDOWN");
            else if (job.cancelled() || job.state().equals("CANCEL_REQUESTED"))
                cancellation.cancel("CANCELLED");
            checkLease(job, cancellation, deadline);
            String kind = kind(job);
            monitor =
                    heartbeat.scheduleAtFixedRate(
                            () -> renewLease(job, cancellation, deadline), 5, 5, TimeUnit.SECONDS);
            var access = runtime.execution(job);
            cancellation.check();
            runtime.progress(access, "SNAPSHOT", 0, null, cancellation);
            access = runtime.buildSnapshot(access, cancellation, 100000);
            runtime.progress(
                    access,
                    job.format().equals("XLSX_DATA") ? "WRITING" : "LAYOUT",
                    0,
                    access.run() == null ? null : access.run().rows(),
                    cancellation);
            checkLease(job, cancellation, deadline);
            temporary = artifacts.temporary(job.tenant(), kind);
            final long[] bytes = {0};
            try (var output =
                    new FilterOutputStream(artifacts.output(job.tenant(), temporary)) {
                        /**
                         * 向受控输出流写入固定报表结果。
                         *
                         * @param value 当前操作的 {@code value} 输入
                         */
                        @Override
                        public void write(int value) throws IOException {
                            out.write(value);
                            bytes[0]++;
                        }

                        /**
                         * 向受控输出流写入固定报表结果。
                         *
                         * @param data 精确数据结果
                         * @param offset 当前操作的 {@code offset} 输入
                         * @param length 当前操作的 {@code length} 输入
                         */
                        @Override
                        public void write(byte[] data, int offset, int length) throws IOException {
                            out.write(data, offset, length);
                            bytes[0] += length;
                        }
                    }) {
                renderer.write(job, access, output, cancellation);
            }
            if (access.run() != null)
                runtime.progress(
                        access, "WRITING", access.run().rows(), access.run().rows(), cancellation);
            checkLease(job, cancellation, deadline);
            runtime.execution(job);
            checkLease(job, cancellation, deadline);
            file = artifacts.commit(job.tenant(), temporary, kind);
            temporary = null;
            checkLease(job, cancellation, deadline);
            if (!store.finishJob(job, leaseOwner, "SUCCEEDED", file, bytes[0], null)) {
                delete(job.tenant(), file);
                file = null;
            } else {
                try {
                    store.audit(
                            access.actor(),
                            access.run() == null ? null : access.run().reportId(),
                            "EXPORT_FINISH",
                            "SUCCEEDED",
                            null);
                } catch (RuntimeException e) {
                    LOG.warn("Report completion audit failed ({})", ReportRuntimeService.code(e));
                }
            }
        } catch (Exception e) {
            discard(job.tenant(), temporary);
            delete(job.tenant(), file);
            String code =
                    cancellation.cancelled() ? cancellation.reason() : ReportRuntimeService.code(e);
            try {
                if (code.equals("CANCELLED") || code.equals("SHUTDOWN"))
                    store.finishJob(job, leaseOwner, "CANCELLED", null, null, code);
                else if (RETRYABLE.contains(code)) store.retryJob(job, leaseOwner, code);
                else if (!code.equals("LEASE_LOST"))
                    store.finishJob(job, leaseOwner, "FAILED", null, null, code);
            } catch (RuntimeException transitionFailure) {
                LOG.warn(
                        "Report attempt completion failed ({})",
                        ReportRuntimeService.code(transitionFailure));
            }
        } finally {
            if (monitor != null) monitor.cancel(false);
            cancellation.detach();
            active.remove(job.id(), cancellation);
        }
    }

    /**
     * 释放本次操作拥有的暂存文件。
     *
     * @param tenant 当前租户标识
     * @param temporary 本次操作拥有的暂存文件
     */
    private void discard(String tenant, Path temporary) {
        if (temporary == null) return;
        try {
            artifacts.discard(tenant, temporary);
        } catch (RuntimeException e) {
            LOG.warn("Report temporary cleanup failed ({})", ReportRuntimeService.code(e));
        }
    }

    /**
     * 删除当前租户明确指定的私有产物。
     *
     * @param tenant 当前租户标识
     * @param file 当前操作的 {@code file} 输入
     */
    private void delete(String tenant, String file) {
        if (file == null) return;
        try {
            artifacts.delete(tenant, file);
        } catch (RuntimeException e) {
            LOG.warn("Report artifact cleanup failed ({})", ReportRuntimeService.code(e));
        }
    }

    /**
     * 完成 {@code cleanup} 对应的报表导出工作器处理。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @return 当前检查或条件操作是否成功
     */
    private boolean cleanup(String tenant, String reference) {
        if (reference == null || reference.startsWith("build/")) return true;
        try {
            artifacts.delete(tenant, reference);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Report artifact cleanup failed ({})", ReportRuntimeService.code(e));
            return false;
        }
    }

    /** 完成 {@code expire} 对应的报表导出工作器处理。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void expire() {
        if (closed) return;
        try {
            for (var job : store.expiredJobs()) {
                try {
                    if ((job.state().equals("EXPIRED") || store.tryExpireJob(job))
                            && cleanup(job.tenant(), job.file())) store.ackJobCleanup(job);
                } catch (RuntimeException e) {
                    LOG.warn("Report job expiry failed ({})", ReportRuntimeService.code(e));
                }
            }
            for (var run : store.expiredRuns()) {
                try {
                    if ((run.state().equals("EXPIRED") || store.expireRun(run))
                            && cleanup(run.tenant(), run.snapshot())
                            && cleanup(run.tenant(), run.plan())) store.ackRunCleanup(run);
                } catch (RuntimeException e) {
                    LOG.warn("Report run expiry failed ({})", ReportRuntimeService.code(e));
                }
            }
            artifacts.reap(
                    Instant.now()
                            .minusSeconds(
                                    Math.max(
                                            properties.getRetentionHours() * 3600L,
                                            properties.getTaskSeconds() * 2L)),
                    store::artifactReferenced);
        } catch (RuntimeException e) {
            LOG.warn("Report artifact expiry failed ({})", ReportRuntimeService.code(e));
        }
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    @Override
    public synchronized void close() {
        closed = true;
        active.values().forEach(c -> c.cancel("SHUTDOWN"));
        executor.shutdownNow();
        heartbeat.shutdownNow();
    }
}
