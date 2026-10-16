package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.engineering.service.EngineeringTemplateCatalog;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class EngineeringTemplateCatalogTest {
    @Test
    void demoCatalogUsesReviewedManifestHashAndExplicitBounds() {
        var catalog = new EngineeringTemplateCatalog();
        assertEquals(1, catalog.list().size());
        var demo = catalog.list().get(0);
        assertEquals(
                "0d603c67a2a7c9625bb202b54b4464a1961848fefcffc91f69d408170ea95b0d",
                demo.templateSha256());
        var valid =
                new EngineeringTaskCommand(
                        "nexus-engineering/1",
                        1L,
                        EngineeringAction.CREATE_PCB_DRAFT,
                        "test",
                        null,
                        demo.templateId(),
                        demo.templateVersion(),
                        demo.templateSha256(),
                        null,
                        Map.of("boardWidthMm", "50", "boardHeightMm", "35", "layers", 2));
        assertDoesNotThrow(() -> catalog.validate(valid));
        var invalid =
                new EngineeringTaskCommand(
                        valid.protocolVersion(),
                        1L,
                        valid.action(),
                        "bad",
                        null,
                        demo.templateId(),
                        demo.templateVersion(),
                        demo.templateSha256(),
                        null,
                        Map.of("boardWidthMm", "39", "boardHeightMm", "35", "layers", 4));
        assertThrows(EngineeringException.class, () -> catalog.validate(invalid));
    }
}
