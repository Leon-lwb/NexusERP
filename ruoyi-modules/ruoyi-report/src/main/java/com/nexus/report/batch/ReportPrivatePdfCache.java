package com.nexus.report.batch;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.apache.pdfbox.io.*;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PDFBox随机访问缓存使用私有暂存，增长先经过原存储输出流的硬配额。 */
final class ReportPrivatePdfCache implements RandomAccessStreamCache {
    /** 每次增长使用固定小缓冲，避免按文件大小分配堆内存。 */
    private static final byte[] ZEROS = new byte[8192];

    /** 请求内所有源文档及目标文档共享缓存字节预算。 */
    static final class Budget {
        /** 当前请求缓存的累计最大字节数。 */
        private final long maximum;

        /** 当前尚未释放的缓存保留字节数。 */
        private long used;

        /**
         * 固定请求级缓存预算。
         *
         * @param maximum 最大累计缓存字节数
         */
        Budget(long maximum) { this.maximum = maximum; }

        /**
         * 在文件增长前扣减请求预算。
         *
         * @param bytes 新增物理保留字节数
         */
        void reserve(long bytes) {
            if (bytes < 0 || bytes > maximum - used)
                throw new ReportFault("BATCH_PDF_BYTES_LIMIT", "PDF缓存超过产物字节上限", 413);
            used += bytes;
        }
    }

    /** 当前请求的私有产物存储。 */
    private final ReportArtifactStore artifacts;

    /** 缓存所属租户。 */
    private final String tenant;

    /** 所有缓存共享的请求预算。 */
    private final Budget budget;

    /** 每次读写持续检查期限及授权。 */
    private final Runnable check;

    /** 本缓存拥有的随机文件。 */
    private final List<Buffer> buffers = new ArrayList<>();

    /** 关闭后不允许再次创建文件。 */
    private boolean closed;

    /**
     * 为一个PDF文档绑定私有缓存及统一预算。
     *
     * @param artifacts 私有存储契约
     * @param tenant 当前租户
     * @param budget 请求共享预算
     * @param check 期限和授权检查
     */
    ReportPrivatePdfCache(ReportArtifactStore artifacts, String tenant, Budget budget, Runnable check) {
        this.artifacts = artifacts;
        this.tenant = tenant;
        this.budget = budget;
        this.check = check;
    }

    /**
     * 每份流缓存都通过原存储创建受配额约束的临时文件。
     *
     * @return 可随机访问且关闭即回收的私有缓存
     */
    @Override
    public RandomAccess createBuffer() throws IOException {
        if (closed) throw new IOException("PDF缓存已关闭");
        check.run();
        var path = artifacts.temporary(tenant, "pdf");
        OutputStream reservation = null;
        try {
            reservation = artifacts.output(tenant, path);
            var buffer = new Buffer(path, reservation, new RandomAccessFile(path.toFile(), "rw"));
            buffers.add(buffer);
            return buffer;
        } catch (IOException | RuntimeException failure) {
            if (reservation != null) {
                try { reservation.close(); } catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { artifacts.discard(tenant, path); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 关闭全部缓存；一个文件释放失败也继续回收其余文件。 */
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        Exception failure = null;
        for (var buffer : buffers) {
            try { buffer.close(); } catch (IOException | RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException runtime) throw runtime;
    }

    /** 随机修改仅覆盖已保留区域，任何扩展先写原有配额流。 */
    private final class Buffer implements RandomAccess {
        /** 本对象独占的私有暂存路径。 */
        private final Path path;

        /** 只追加零字节来保留真实物理容量的原存储输出流。 */
        private final OutputStream reservation;

        /** 只对本对象暂存做随机读写的句柄。 */
        private final RandomAccessFile file;

        /** 逻辑长度、已保留物理长度。 */
        private long size, allocated;

        /** 防止重复释放预算。 */
        private boolean released;

        /**
         * 接管已成功打开的私有暂存句柄。
         *
         * @param path 本对象独占路径
         * @param reservation 原存储配额写流
         * @param file 随机文件句柄
         */
        Buffer(Path path, OutputStream reservation, RandomAccessFile file) {
            this.path = path;
            this.reservation = reservation;
            this.file = file;
        }

        /**
         * 写入前按高水位保留容量，逻辑清空不能重复消耗配额。
         *
         * @param count 即将写入的字节数
         */
        private void grow(int count) throws IOException {
            check.run();
            long position = file.getFilePointer();
            if (count < 0 || position > budget.maximum - count)
                throw new ReportFault("BATCH_PDF_BYTES_LIMIT", "PDF缓存超过产物字节上限", 413);
            long target = position + count;
            if (target > allocated) {
                long remaining = target - allocated;
                budget.reserve(remaining);
                allocated = target;
                while (remaining > 0) {
                    check.run();
                    int length = (int) Math.min(remaining, ZEROS.length);
                    reservation.write(ZEROS, 0, length);
                    remaining -= length;
                }
            }
            size = Math.max(size, target);
        }

        /** 从逻辑范围内读取一个字节。 */
        public int read() throws IOException {
            check.run();
            return file.getFilePointer() >= size ? -1 : file.read();
        }

        /** 从逻辑范围内读取有界字节块。 */
        public int read(byte[] bytes, int offset, int count) throws IOException {
            check.run();
            long remaining = size - file.getFilePointer();
            if (count == 0) return 0;
            return remaining <= 0 ? -1 : file.read(bytes, offset, (int) Math.min(count, remaining));
        }

        /** 返回当前随机游标位置。 */
        public long getPosition() throws IOException { return file.getFilePointer(); }

        /** 移动游标时仍检查期限，超出预算的位置不能被使用。 */
        public void seek(long position) throws IOException {
            check.run();
            if (position < 0 || position > budget.maximum) throw new IOException("PDF缓存游标越界");
            file.seek(position);
        }

        /** 返回逻辑内容长度。 */
        public long length() { return size; }

        /** 返回当前句柄是否已释放。 */
        public boolean isClosed() { return released; }

        /** 判断是否位于逻辑末尾。 */
        public boolean isEOF() throws IOException { return file.getFilePointer() >= size; }

        /** 创建共享同一受控文件的只读范围视图。 */
        public RandomAccessReadView createView(long start, long count) throws IOException {
            check.run();
            if (start < 0 || count < 0 || start > size || count > size - start)
                throw new IOException("PDF缓存视图越界");
            return new RandomAccessReadView(this, start, count);
        }

        /** 写入一个已经预留容量的字节。 */
        public void write(int value) throws IOException { grow(1); file.write(value); }

        /** 写入已经预留容量的完整字节块。 */
        public void write(byte[] bytes) throws IOException { write(bytes, 0, bytes.length); }

        /** 写入已经预留容量的指定字节块。 */
        public void write(byte[] bytes, int offset, int count) throws IOException {
            Objects.checkFromIndexSize(offset, count, bytes.length);
            grow(count);
            file.write(bytes, offset, count);
        }

        /** 清空逻辑内容并保留物理高水位，避免清空后重复计费。 */
        public void clear() throws IOException {
            check.run();
            file.setLength(0);
            size = 0;
            file.seek(0);
        }

        /** 先关闭全部句柄，再释放文件与原存储中的配额。 */
        public void close() throws IOException {
            if (released) return;
            released = true;
            Exception failure = null;
            try { reservation.close(); } catch (IOException | RuntimeException cleanup) { failure = cleanup; }
            try { file.close(); } catch (IOException | RuntimeException cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
            try { artifacts.discard(tenant, path); } catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            } finally { budget.used -= allocated; }
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
        }
    }
}
