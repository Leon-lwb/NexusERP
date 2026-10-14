package com.nexus.report.export;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.apache.poi.xssf.streaming.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Per-workbook writers use the report's private tenant store without replacing POI's global
 * strategy.
 */
final class ReportPrivateWorkbook extends SXSSFWorkbook {
    private final ReportArtifactStore artifacts;
    private final String tenant;
    private final long budget;
    private final ReportCancellation cancellation;
    private final List<Path> files = new ArrayList<>();
    private boolean cleaning;

    void prepareRelease() {
        cleaning = true;
    }

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

    @Override
    protected SheetDataWriter createSheetDataWriter() throws IOException {
        return new GZIPSheetDataWriter() {
            @Override
            public File createTempFile() {
                Path file = artifacts.temporary(tenant, "xlsx");
                files.add(file);
                return file.toFile();
            }

            @Override
            protected OutputStream decorateOutputStream(FileOutputStream output)
                    throws IOException {
                try {
                    return new GZIPOutputStream(
                            new FilterOutputStream(output) {
                                private long bytes;

                                private void check(int size) {
                                    if (cleaning) return;
                                    cancellation.check();
                                    bytes += size;
                                    if (bytes > budget) throw ReportFault.capacity();
                                }

                                @Override
                                public void write(int b) throws IOException {
                                    check(1);
                                    out.write(b);
                                }

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
