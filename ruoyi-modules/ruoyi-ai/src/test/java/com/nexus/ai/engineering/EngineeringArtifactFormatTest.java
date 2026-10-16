package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class EngineeringArtifactFormatTest {
    @TempDir Path temp;

    EngineeringArtifactValidator.Validated validate(String text, EngineeringArtifactType type)
            throws Exception {
        var path = temp.resolve("report.json");
        Files.writeString(path, text);
        return EngineeringArtifactValidator.validate(
                path,
                new EngineeringUploadRequest(
                        1,
                        "token",
                        1,
                        type,
                        null,
                        "report.json",
                        "a".repeat(64),
                        Files.size(path),
                        "application/json",
                        "report.json"));
    }

    @Test
    void bomRowsEnvelopeIsAcceptedAndFieldsAreNormalized() throws Exception {
        var result =
                validate(
                        "{\"rows\":[{\"designator\":\"R1\",\"value\":\"10k\",\"quantity\":1}]}",
                        EngineeringArtifactType.PCB_BOM);
        var json = EngineeringJson.tree(result.validationJson());
        assertEquals("R1", json.path("bom").path("items").get(0).path("designator").asText());
        assertTrue(json.path("bom").path("items").get(0).path("manufacturerPartNumber").isNull());
    }

    @Test
    void cliDrcWithoutInventedSeverityMustHaveConsistentTotals() throws Exception {
        var result =
                validate(
                        "{\"status\":\"VIOLATIONS\",\"errorCount\":1,\"warningCount\":0,\"items\":[{\"code\":\"CLEARANCE\",\"message\":\"间距不足\",\"detail\":{}}]}",
                        EngineeringArtifactType.PCB_DRC_REPORT);
        assertEquals(
                "VIOLATIONS",
                EngineeringJson.tree(result.validationJson()).path("drc").path("status").asText());
        assertThrows(
                EngineeringException.class,
                () ->
                        validate(
                                "{\"status\":\"PASS\",\"errorCount\":0,\"warningCount\":0,\"items\":[{\"code\":\"CLEARANCE\"}]}",
                                EngineeringArtifactType.PCB_DRC_REPORT));
    }

    @Test
    void emptyReportCannotBePass() throws Exception {
        assertEquals(
                "UNAVAILABLE",
                EngineeringJson.tree(
                                validate("{}", EngineeringArtifactType.PCB_DRC_REPORT)
                                        .validationJson())
                        .path("drc")
                        .path("status")
                        .asText());
    }
}
