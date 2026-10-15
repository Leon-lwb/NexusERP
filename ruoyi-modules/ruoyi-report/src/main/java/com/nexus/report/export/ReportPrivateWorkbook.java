package com.nexus.report.export;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.apache.poi.xssf.streaming.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** 每个工作簿使用租户私有存储写入临时文件，不修改 POI 的全局临时策略。 */
final class ReportPrivateWorkbook extends SXSSFWorkbook {
    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /** 当前租户标识。 */
    private final String tenant;

    /** 保存当前 {@code budget} 值。 */
    private final long budget;

    /** 执行取消信号。 */
    private final ReportCancellation cancellation;

    /** 保存当前 {@code files} 值。 */
    private final List<Path> files = new ArrayList<>();

    /** 保存当前 {@code cleaning} 值。 */
    private boolean cleaning;

    /** 进入仅清理资源的释放阶段。 */
    void prepareRelease() {
        cleaning = true;
    }

    /**
     * 初始化报表私有工作簿并保存所需依赖或不可变值。
     *
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param tenant 当前租户标识
     * @param budget 当前操作的 {@code budget} 输入
     * @param cancellation 执行取消信号
     */
    ReportPrivateWorkbook(
            ReportArtifactStore artifacts,
            String tenant,
            long budget,
            ReportCancellation cancellation) {
        super(200);
        this.artifacts = artifacts;
        this.tenant = tenant;
        this.budget = budget;
        this.cancellation = cancellation;
        setCompressTempFiles(true);
    }

    /**
     * 完成 {@code createSheetDataWriter} 对应的报表私有工作簿处理。
     *
     * @return 当前处理返回的固定结果
     */
    @Override
    protected SheetDataWriter createSheetDataWriter() throws IOException {
        return new GZIPSheetDataWriter() {
            /**
             * 完成 {@code createTempFile} 对应的报表处理。
             *
             * @return 当前处理返回的固定结果
             */
            @Override
            public File createTempFile() {
                Path file = artifacts.temporary(tenant, "xlsx");
                files.add(file);
                return file.toFile();
            }

            /**
             * 完成 {@code decorateOutputStream} 对应的报表处理。
             *
             * @param output 受控输出流
             * @return 当前处理返回的固定结果
             */
            @Override
            protected OutputStream decorateOutputStream(FileOutputStream output)
                    throws IOException {
                try {
                    return new GZIPOutputStream(
                            new FilterOutputStream(
                                    artifacts.track(tenant, getTempFile().toPath(), output)) {
                                /** 受控字节数据。 */
                                private long bytes;

                                /**
                                 * 检查取消、超时或受控状态条件。
                                 *
                                 * @param size 有界页大小
                                 */
                                private void check(int size) {
                                    if (cleaning) return;
                                    cancellation.check();
                                    bytes += size;
                                    if (bytes > budget) throw ReportFault.capacity();
                                }

                                /**
                                 * 向受控输出流写入固定报表结果。
                                 *
                                 * @param b 当前操作的 {@code b} 输入
                                 */
                                @Override
                                public void write(int b) throws IOException {
                                    check(1);
                                    out.write(b);
                                }

                                /**
                                 * 向受控输出流写入固定报表结果。
                                 *
                                 * @param b 当前操作的 {@code b} 输入
                                 * @param off 当前操作的 {@code off} 输入
                                 * @param length 当前操作的 {@code length} 输入
                                 */
                                @Override
                                public void write(byte[] b, int off, int length)
                                        throws IOException {
                                    check(length);
                                    out.write(b, off, length);
                                }
                            });
                } catch (IOException | RuntimeException failure) {
                    try {
                        output.close();
                    } catch (IOException cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
            }
        };
    }

    /** 完成 {@code release} 对应的报表私有工作簿处理。 */
    void release() {
        for (var file : files) {
            try {
                artifacts.discard(tenant, file);
            } catch (RuntimeException cleanup) {
                org.slf4j.LoggerFactory.getLogger(ReportPrivateWorkbook.class)
                        .warn("Report spreadsheet temporary cleanup deferred");
            }
        }
        files.clear();
    }
}
