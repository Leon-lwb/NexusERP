package com.nexus.report.batch;

import com.nexus.common.report.ReportActor;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.*;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** 只合并已存在的私有PDF；来源、缓存和输出均受既有存储及请求预算约束。 */
public final class ReportBatchPdfService {
    /** 与运行文件领取一致的授权复核字节间隔。 */
    private static final long CHECK_BYTES = 1_048_576;

    /** 批次及固定任务的权限入口。 */
    private final ReportBatchService batches;

    /** 只允许私有物化与请求期暂存。 */
    private final ReportArtifactStore artifacts;

    /** 既有字节上限及独立批次限制。 */
    private final ReportProperties properties;

    /** 使用单调时钟限制整个合并及交付生命周期。 */
    private final LongSupplier nanoTime;

    /**
     * 装配同步合并服务，不建立导出队列或持久合并缓存。
     *
     * @param batches 固定批次服务
     * @param artifacts 私有存储契约
     * @param properties 已校验配置
     */
    public ReportBatchPdfService(ReportBatchService batches, ReportArtifactStore artifacts, ReportProperties properties) {
        this(batches, artifacts, properties, System::nanoTime);
    }

    /**
     * 为隔离测试注入单调时钟。
     *
     * @param batches 固定批次服务
     * @param artifacts 私有存储契约
     * @param properties 已校验配置
     * @param nanoTime 单调纳秒时钟
     */
    ReportBatchPdfService(ReportBatchService batches, ReportArtifactStore artifacts,
            ReportProperties properties, LongSupplier nanoTime) {
        this.batches = batches;
        this.artifacts = artifacts;
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    /**
     * 按原始项序合并已存在PDF；任意来源错误导致整次请求失败并回收暂存。
     *
     * @param actor 当前请求身份
     * @param id 批次标识
     * @return 必须显式关闭的一次性私有交付
     */
    public Prepared merge(ReportActor actor, String id) {
        var guard = new Guard(actor, id);
        var infos = new ArrayList<ReportArtifactStore.Info>();
        long inputBytes = 0;
        for (var item : guard.batch.items()) {
            guard.number = item.number();
            guard.checkTime();
            try {
                var info = artifacts.info(actor.tenantId(), item.job().file());
                if (info.length() < 1 || info.checksum() == null || !info.checksum().matches("[a-fA-F0-9]{64}")
                        || item.job().bytes() == null || info.length() != item.job().bytes())
                    throw new ReportFault("ARTIFACT_INVALID", "源PDF长度或校验元数据不一致", 409);
                if (info.length() > properties.getMaxArtifactBytes() - inputBytes) throw byteLimit();
                inputBytes += info.length();
                infos.add(info);
            } catch (ReportFault failure) {
                throw located(item.number(), failure);
            }
        }
        guard.infos = List.copyOf(infos);
        guard.verify();
        Path temporary = artifacts.temporary(actor.tenantId(), "pdf");
        boolean retained = false;
        try {
            var budget = new ReportPrivatePdfCache.Budget(properties.getMaxArtifactBytes());
            try (var cache = new ReportPrivatePdfCache(artifacts, actor.tenantId(), budget, guard::check);
                    var merged = new PDDocument(() -> cache)) {
                int pages = 0;
                var merger = new PDFMergerUtility();
                for (int index = 0; index < guard.batch.items().size(); index++) {
                    var item = guard.batch.items().get(index);
                    guard.number = item.number();
                    try (var sourceFile = artifacts.materialize(actor.tenantId(), item.job().file())) {
                        guard.check();
                        verifyFile(sourceFile.path(), infos.get(index), guard);
                        guard.verify();
                        try (var sourceCache = new ReportPrivatePdfCache(artifacts, actor.tenantId(), budget, guard::check);
                                var input = new CheckedRead(sourceFile.path(), guard);
                                var source = Loader.loadPDF(input, () -> sourceCache)) {
                            int count = 0;
                            for (var page : source.getPages()) {
                                guard.check();
                                count++;
                                if (count > properties.getBatch().getMaxPages() - pages)
                                    throw new ReportFault("BATCH_PDF_PAGE_LIMIT", "合并页数超过批次上限", 413);
                            }
                            if (count < 1) throw new ReportFault("BATCH_PDF_INVALID", "源PDF没有可合并页面", 422);
                            if (count != source.getNumberOfPages())
                                throw new ReportFault("BATCH_PDF_INVALID", "源PDF页数元数据与实际页面不一致", 422);
                            guard.check();
                            merger.appendDocument(merged, source);
                            pages += count;
                            guard.check();
                        }
                    } catch (ReportFault failure) {
                        throw located(item.number(), failure);
                    } catch (IOException failure) {
                        throw located(item.number(), new ReportFault("BATCH_PDF_INVALID", "源PDF损坏、加密或不可读取", 422));
                    }
                }
                guard.number = -1;
                guard.verify();
                try (var output = boundedOutput(artifacts.output(actor.tenantId(), temporary), guard)) {
                    merged.save(output);
                }
            }
            long size = Files.size(temporary);
            if (size > properties.getMaxArtifactBytes()) throw byteLimit();
            if (size < 1) throw new ReportFault("BATCH_PDF_INVALID", "合并PDF为空", 422);
            guard.verify();
            var prepared = new Prepared(temporary, size, guard);
            retained = true;
            return prepared;
        } catch (IOException failure) {
            throw new ReportFault("BATCH_PDF_MERGE_FAILED", "合并PDF暂存失败，请稍后重试", 503);
        } finally {
            if (!retained) artifacts.discard(actor.tenantId(), temporary);
        }
    }

    /**
     * 使用物化文件本身计算SHA-256，不能仅信任对象元数据或文件后缀。
     *
     * @param path 仅存在于当前物化生命周期的路径
     * @param expected 固定源对象元数据
     * @param guard 本次请求的权限和期限守卫
     */
    private void verifyFile(Path path, ReportArtifactStore.Info expected, Guard guard) throws IOException {
        if (Files.size(path) != expected.length())
            throw new ReportFault("ARTIFACT_INVALID", "物化PDF长度不一致", 409);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = new byte[65536];
            int count;
            long size = 0;
            while ((count = input.read(bytes)) >= 0) {
                guard.check();
                if (count == 0) continue;
                if (count > expected.length() - size) throw new ReportFault("ARTIFACT_INVALID", "物化PDF长度发生变化", 409);
                size += count;
                digest.update(bytes, 0, count);
            }
            if (size != expected.length() || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected.checksum()))
                throw new ReportFault("ARTIFACT_INVALID", "物化PDF校验和不一致", 409);
        }
    }

    /**
     * 在原配额写流之外再次约束最终输出大小并检查期限。
     *
     * @param output 私有存储提供的有界写流
     * @param guard 请求守卫
     * @return 每次写入均检查的包装流
     */
    private OutputStream boundedOutput(OutputStream output, Guard guard) {
        return new FilterOutputStream(output) {
            /** 已写入的最终PDF字节数。 */
            private long written;

            /** 写入一个字节之前检查期限和输出上限。 */
            public void write(int value) throws IOException {
                guard.check();
                if (written >= properties.getMaxArtifactBytes()) throw byteLimit();
                out.write(value);
                written++;
            }

            /** 写入有界字节块之前检查期限和输出上限。 */
            public void write(byte[] bytes, int offset, int count) throws IOException {
                guard.check();
                if (count > properties.getMaxArtifactBytes() - written) throw byteLimit();
                out.write(bytes, offset, count);
                written += count;
            }
        };
    }

    /**
     * 返回统一且可识别的请求字节超限。
     *
     * @return 不包含私有路径的容量异常
     */
    private static ReportFault byteLimit() {
        return new ReportFault("BATCH_PDF_BYTES_LIMIT", "批次PDF输入、缓存或输出超过产物字节上限", 413);
    }

    /**
     * 防止已经带项序号的权限异常重复嵌套。
     *
     * @param number 当前项序号
     * @param failure 原受控异常
     * @return 保留原错误码和定位信息的异常
     */
    private static ReportFault located(int number, ReportFault failure) {
        return failure.getMessage().contains("item_no=") ? failure : ReportBatchService.itemFault(number, failure);
    }

    /** 合并文件的一次性交付生命周期，绝不公开路径或持久引用。 */
    public final class Prepared implements AutoCloseable {
        /** 本次请求独占的暂存。 */
        private final Path path;

        /** 固定输出长度。 */
        private final long bytes;

        /** 交付前及交付中使用同一守卫。 */
        private final Guard guard;

        /** 防止重复删除或再次读取。 */
        private boolean closed;

        /**
         * 接管已经完成合并且复核通过的暂存。
         *
         * @param path 本次请求独占暂存
         * @param bytes 固定输出长度
         * @param guard 本次请求守卫
         */
        private Prepared(Path path, long bytes, Guard guard) {
            this.path = path;
            this.bytes = bytes;
            this.guard = guard;
        }

        /**
         * 获取安全响应头所需的长度。
         *
         * @return 合并PDF字节数
         */
        public long bytes() { return bytes; }

        /** 输出响应头前再次检查所有来源和批次，防止合并期间撤权领取。 */
        public void verify() {
            if (closed) throw new IllegalStateException("批次PDF交付已关闭");
            guard.verify();
        }

        /**
         * 按64KiB流式交付，每MiB及至少每秒重新鉴权，成功或失败均回收暂存。
         *
         * @param output 当前请求的响应流
         */
        public void write(OutputStream output) throws IOException {
            if (closed) throw new IllegalStateException("批次PDF交付已关闭");
            var cleanup = this;
            try (cleanup; var input = Files.newInputStream(path)) {
                verify();
                byte[] chunk = new byte[65536];
                long checked = 0;
                int count;
                while ((count = input.read(chunk)) >= 0) {
                    if (count == 0) continue;
                    if (checked >= CHECK_BYTES) { guard.verify(); checked = 0; }
                    guard.check();
                    output.write(chunk, 0, count);
                    checked += count;
                }
                guard.verify();
            }
        }

        /** 只删除本请求拥有的合并暂存，原任务PDF保持独立生命周期。 */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            artifacts.discard(guard.actor.tenantId(), path);
        }
    }

    /** 全来源授权、固定任务、批次版本和单调期限的持续检查。 */
    private final class Guard {
        /** 请求主体。 */
        private final ReportActor actor;

        /** 固定批次标识。 */
        private final String id;

        /** 已逐项鉴权的原始批次。 */
        private final ReportBatchService.PdfBatch batch;

        /** 合并开始时每份源对象的校验元数据。 */
        private List<ReportArtifactStore.Info> infos = List.of();

        /** 开始时间、上次授权检查时间和硬期限。 */
        private final long started = nanoTime.getAsLong(), maximum = properties.getBatch().getMergeSeconds() * 1_000_000_000L;

        /** 上次全来源检查完成时的单调时间。 */
        private long checked = started;

        /** 当前处理的项序号；负数表示完整输出阶段。 */
        private int number = -1;

        /**
         * 固定请求起点并鉴权整份批次。
         *
         * @param actor 请求主体
         * @param id 固定批次标识
         */
        Guard(ReportActor actor, String id) {
            this.actor = actor;
            this.id = id;
            batch = batches.pdfBatch(actor, id);
            checkTime();
        }

        /** 读写小块时检查期限；长时间处理每秒补充全来源授权复核。 */
        void check() {
            checkTime();
            if (nanoTime.getAsLong() - checked >= 1_000_000_000L) verify();
        }

        /** 期限和线程取消不能被大文件或大量小块绕过。 */
        void checkTime() {
            if (Thread.currentThread().isInterrupted()) throw new ReportFault("CANCELLED", "批次PDF请求已终止", 409);
            if (nanoTime.getAsLong() - started >= maximum) {
                var failure = new ReportFault("BATCH_PDF_TIMEOUT", "批次PDF合并或交付超过期限", 408);
                throw number < 0 ? failure : located(number, failure);
            }
        }

        /** 全部依赖、主体、来源任务、固定对象元数据与批次始终必须有效。 */
        void verify() {
            checkTime();
            var current = batches.pdfBatch(actor, id);
            if (!batch.equals(current)) throw new ReportFault("BATCH_CHANGED", "批次或来源任务已变化，请重新领取", 409);
            for (int index = 0; index < batch.items().size(); index++) {
                checkTime();
                var item = batch.items().get(index);
                try {
                    if (!infos.get(index).equals(artifacts.info(actor.tenantId(), item.job().file())))
                        throw new ReportFault("ARTIFACT_INVALID", "源PDF内容或元数据已变化", 409);
                } catch (ReportFault failure) { throw located(item.number(), failure); }
            }
            checkTime();
            checked = nanoTime.getAsLong();
        }
    }

    /** PDFBox源文件读取也需要检查期限，不能使用无检查的内存或全局临时缓存。 */
    private static final class CheckedRead implements RandomAccessRead {
        /** 完成构造后才参与受控读取的原始读取器。 */
        private final RandomAccessReadBufferedFile input;

        /** 当前请求检查函数。 */
        private final Runnable check;

        /**
         * 打开当前物化文件的有界读取缓存。
         *
         * @param path 当前物化路径
         * @param guard 请求守卫
         */
        CheckedRead(Path path, Guard guard) throws IOException {
            input = new RandomAccessReadBufferedFile(path);
            check = guard::check;
        }

        /** 读取一个字节前检查期限。 */
        @Override
        public int read() throws IOException { check.run(); return input.read(); }

        /** 读取字节块前检查期限。 */
        @Override
        public int read(byte[] bytes, int offset, int count) throws IOException { check.run(); return input.read(bytes, offset, count); }

        /** 随机定位前检查期限。 */
        @Override
        public void seek(long position) throws IOException { check.run(); input.seek(position); }

        /** 返回原始读取器的游标位置。 */
        public long getPosition() throws IOException { return input.getPosition(); }

        /** 返回源文件的固定长度。 */
        public long length() throws IOException { return input.length(); }

        /** 返回物化读取器的关闭状态。 */
        public boolean isClosed() { return input.isClosed(); }

        /** 检查是否到达源文件末尾。 */
        public boolean isEOF() throws IOException { return input.isEOF(); }

        /** 范围视图继续经过当前守卫，不能绕过期限检查。 */
        public RandomAccessReadView createView(long start, long count) throws IOException {
            check.run();
            return new RandomAccessReadView(this, start, count);
        }

        /** 关闭读取器，物化生命周期由外层另行释放。 */
        public void close() throws IOException { input.close(); }
    }
}
