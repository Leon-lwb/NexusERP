package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.engine.*;
import com.nexus.report.extension.ReportExtensionSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** 同租户固定字体按checksum共享，计划格式2保留格式1兼容读取。 */
class ReportFontReuseTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行twoPlansReuseOnePrivateFontAndEachPlanHasOnlyMetadataEntry对应的受控处理。 */
    @Test
    void twoPlansReuseOnePrivateFontAndEachPlanHasOnlyMetadataEntry() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var fonts = new ReportFontResourceStore(f.jdbc, f.artifacts);
            var plans = new ReportRenderPlanStore(f.artifacts, f.json);
            plans.fonts(fonts);
            var compiled = f.definitions.bind(f.owner, f.dsl("FONT_PLAN")).compiled();
            var data =
                    new ReportCalculator().calculate(compiled, List.of(f.provider.row()), Map.of());
            String snapshot = "a".repeat(64);
            var a =
                    plans.create(
                            "A",
                            "1",
                            snapshot,
                            "1.0.0",
                            FontSupport.find(null),
                            new ReportCancellation(),
                            font ->
                                    new DocumentLayoutEngine()
                                            .layout(compiled, data, Map.of(), font));
            var b =
                    plans.create(
                            "A",
                            "2",
                            snapshot,
                            "1.0.0",
                            FontSupport.find(null),
                            new ReportCancellation(),
                            font ->
                                    new DocumentLayoutEngine()
                                            .layout(compiled, data, Map.of(), font));
            assertEquals(
                    1,
                    f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_font_resource", Integer.class));
            assertEquals(
                    2, f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_plan_font", Integer.class));
            try (var zip = new ZipFile(f.artifacts.read("A", a.reference()).toFile())) {
                assertEquals(1, zip.size());
                assertNotNull(zip.getEntry("plan.json"));
            }
            plans.withFont(
                    "A",
                    b.reference(),
                    b.checksum(),
                    "2",
                    snapshot,
                    "1.0.0",
                    1,
                    new ReportCancellation(),
                    font -> assertTrue(Files.isRegularFile(font)));
            assertEquals(a.plan().fontName(), b.plan().fontName());
        }
    }
}
