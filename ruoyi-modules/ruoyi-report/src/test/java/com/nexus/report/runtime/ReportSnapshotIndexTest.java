package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.extension.ReportExtensionSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

/** 固定快照块索引必须定位目标区域，保持排序、精确值及块完整性。 */
class ReportSnapshotIndexTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行latePageUsesRangeNearItsOffsetAndKeepsExactValues对应的受控处理。 */
    @Test
    void latePageUsesRangeNearItsOffsetAndKeepsExactValues() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            Path path = f.artifacts.temporary("A", "rows");
            try (var output = f.artifacts.output("A", path)) {
                for (int i = 0; i < 5000; i++)
                    output.write(
                            (f.json.writeValueAsString(
                                                    Map.of(
                                                            "id",
                                                            TypedValue.of(
                                                                    ValueType.ID,
                                                                    Long.MAX_VALUE - i),
                                                            "amount",
                                                            TypedValue.of(
                                                                    ValueType.DECIMAL,
                                                                    "9007199254740993.005")))
                                            + "\n")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String reference = f.artifacts.commit("A", path, "rows");
            var indexes = new ReportSnapshotIndexService(f.jdbc, f.artifacts, f.json);
            var page = indexes.page("A", reference, 4500, 20);
            assertEquals(20, page.size());
            assertEquals(Long.toString(Long.MAX_VALUE - 4500), page.get(0).get("id").value());
            assertEquals("9007199254740993.005", page.get(0).get("amount").value());
            assertTrue(indexes.lastSeekOffset() > 0);
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_snapshot_index", Integer.class));
            var again = indexes.page("A", reference, 4520, 20);
            assertEquals(Long.toString(Long.MAX_VALUE - 4520), again.get(0).get("id").value());
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_snapshot_index", Integer.class));
        }
    }

    /** 历史两字段快照不要求显示名称，字典名称在索引分页后仍固定保留。 */
    @Test
    void indexedRowsKeepDictionaryNamesAndAcceptHistoricalValues() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(fixture.jdbc);
            Path path = fixture.artifacts.temporary("A", "rows");
            try (var output = fixture.artifacts.output("A", path)) {
                output.write((fixture.json.writeValueAsString(Map.of(
                        "currency", new TypedValue(ValueType.STRING, "CNY", "人民币"),
                        "historical", new TypedValue(ValueType.STRING, "OLD"),
                        "id", new TypedValue(ValueType.ID, "000123"))) + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String reference = fixture.artifacts.commit("A", path, "rows");
            var indexes = new ReportSnapshotIndexService(fixture.jdbc, fixture.artifacts, fixture.json);
            var row = indexes.page("A", reference, 0, 1).get(0);
            assertEquals("CNY", row.get("currency").value());
            assertEquals("人民币", row.get("currency").displayText());
            assertEquals("OLD", row.get("historical").displayText());
            assertNull(row.get("historical").displayValue());
            assertEquals("123", row.get("id").value(), "仍执行历史精确值规范化");
        }
    }
}
