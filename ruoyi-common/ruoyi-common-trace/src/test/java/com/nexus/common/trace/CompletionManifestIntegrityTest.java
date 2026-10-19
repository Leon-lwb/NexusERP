package com.nexus.common.trace;

import com.nexus.common.trace.port.CompletionManifestPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompletionManifestIntegrityTest {
    List<CompletionManifestLine> rows(int count) {
        var rows = new ArrayList<CompletionManifestLine>();
        for (int i = 1; i <= count; i++) {
            rows.add(
                    new CompletionManifestLine(
                            i,
                            (long) i,
                            (long) (i + 5000),
                            "SN" + i,
                            2L,
                            3L,
                            4L,
                            BigDecimal.ONE,
                            "GOOD",
                            1));
        }
        return rows;
    }

    CompletionManifestPort port(List<CompletionManifestLine> rows) {
        return new CompletionManifestPort() {
            public CompletionManifest manifest(String tenant, Long id, int revision) {
                return new CompletionManifest(
                        id,
                        revision,
                        tenant,
                        8L,
                        9L,
                        4L,
                        rows.size(),
                        BigDecimal.valueOf(rows.size()),
                        BigDecimal.ZERO,
                        CompletionManifestIntegrity.hash(rows),
                        "FROZEN");
            }

            public TracePage<CompletionManifestLine> lines(
                    String tenant, Long id, int revision, String cursor, int limit) {
                assertEquals(500, limit);
                int start = cursor == null ? 0 : Integer.parseInt(cursor),
                        end = Math.min(start + limit, rows.size());
                return new TracePage<>(
                        rows.subList(start, end),
                        end < rows.size() ? Integer.toString(end) : null,
                        end < rows.size());
            }
        };
    }

    @Test
    void readsAllPagesWithoutFiveHundredTotalLimit() {
        var rows = rows(1201);
        var result = CompletionManifestIntegrity.read(port(rows), "T1", 1L, 1, null);
        assertEquals(1201, result.lines().size());
        assertEquals(1201, result.lines().get(1200).lineNo());
    }

    @Test
    void duplicateIdentityCannotMasqueradeAsAdditionalFinishedUnit() {
        var rows = rows(2);
        rows.set(
                1,
                new CompletionManifestLine(
                        2, 1L, 5002L, "SN1", 2L, 3L, 4L, BigDecimal.ONE, "GOOD", 1));
        assertThrows(
                IllegalStateException.class,
                () -> CompletionManifestIntegrity.read(port(rows), "T1", 1L, 1, null));
    }

    @Test
    void immutableHeaderCannotHideModifiedPage() {
        var source = port(rows(2));
        var bad =
                new CompletionManifestPort() {
                    public CompletionManifest manifest(String t, Long id, int r) {
                        return source.manifest(t, id, r);
                    }

                    public TracePage<CompletionManifestLine> lines(
                            String t, Long id, int r, String c, int l) {
                        return new TracePage<>(rows(1), null, false);
                    }
                };
        assertThrows(
                IllegalStateException.class,
                () -> CompletionManifestIntegrity.read(bad, "T1", 1L, 1, null));
        assertThrows(
                IllegalStateException.class,
                () -> CompletionManifestIntegrity.read(source, "T1", 1L, 1, "different"));
    }
}
