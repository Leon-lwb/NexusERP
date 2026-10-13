package com.nexus.report.storage;
import java.nio.file.Path;
import java.io.OutputStream;
/** Replaceable private storage port. Physical paths never appear in API responses. */
public interface ReportArtifactStore extends AutoCloseable {
    @Override default void close() { }
    String storeId();
    Path temporary(String tenant,String kind);
    OutputStream output(String tenant,Path temporary);
    String commit(String tenant,Path temporary,String kind);
    Path read(String tenant,String reference);
    boolean exists(String tenant,String reference);
    void delete(String tenant,String reference);
    void discard(String tenant,Path temporary);
    String checksum(String tenant,String reference);
    /** Optional bounded recovery for private-store objects left before their DB registration. */
    default void reap(java.time.Instant olderThan,java.util.function.BiPredicate<String,String> referenced) { }

}
