package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.*;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

/** 报表模板模板包测试，负责当前模块对应的契约或处理职责。 */
class ReportTemplatePackageTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code packagesCarryOnlyDefinitionsAndRequireLocalContractMapping} 场景下的结果与边界条件。 */
    @Test
    void packagesCarryOnlyDefinitionsAndRequireLocalContractMapping() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var service = new ReportTemplatePackageService(f.definitions, f.compiler);
            var pack = service.export(f.owner, f.definition.id());
            assertEquals(1, pack.formatVersion());
            assertNull(pack.dsl().datasetVersionId());
            assertTrue(
                    pack.dsl().parameters().stream()
                            .filter(p -> p.type().equals("ID"))
                            .allMatch(p -> p.defaultValue() == null));
            var imported =
                    service.importDraft(
                            f.owner,
                            pack,
                            f.dataset.publishedVersion(),
                            "IMPORTED_REPORT",
                            "导入报表",
                            null);
            assertEquals("DRAFT", imported.status());
            assertNull(imported.publishedVersion());
            assertEquals(f.owner.userId(), imported.owner());
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.importDraft(
                                    f.owner,
                                    new ReportTemplatePackageService.TemplatePackage(
                                            1, pack.engine(), pack.dsl(), pack.dependency(), "bad"),
                                    f.dataset.publishedVersion(),
                                    "BAD_REPORT",
                                    "测试",
                                    null));
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.importDraft(
                                    f.viewer, pack, "9223372036854775807", "BAD_REF", "测试", null));
        }
    }

    /** 验证 {@code copyUsesCurrentSavedDefinitionAndFreshAuthorization} 场景下的结果与边界条件。 */
    @Test
    void copyUsesCurrentSavedDefinitionAndFreshAuthorization() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var service = new ReportTemplatePackageService(f.definitions, f.compiler);
            var copied = service.copy(f.owner, f.definition.id(), "COPY_REPORT", "复制报表", null);
            assertNull(copied.publishedVersion());
            assertEquals("COPY_REPORT", copied.code());
            f.store.replaceAcl(f.owner, "REPORT", f.definition.id(), List.of());
            assertThrows(ReportFault.class, () -> service.export(f.viewer, f.definition.id()));
        }
    }
}
