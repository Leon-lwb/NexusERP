package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

class EngineeringArtifactManifestTest {
    static class Artifacts extends EngineeringArtifactUploadTest.Uploads {
        final EngineeringArtifactService artifacts =
                new EngineeringArtifactService(repository, tasks, devices, access, storage);

        void add(EngineeringArtifactType type, String path, String group, byte[] bytes) {
            var request =
                    new EngineeringUploadRequest(
                            claim.attempt(),
                            claim.leaseToken(),
                            claim.revision(),
                            type,
                            group,
                            path,
                            EngineeringArtifactUploadTest.sha(bytes),
                            bytes.length,
                            null,
                            path.substring(path.lastIndexOf('/') + 1));
            var ticket = uploads.ticket(principal, Long.valueOf(claim.taskId()), request);
            uploads.upload(
                    principal,
                    Long.valueOf(claim.taskId()),
                    ticket.uploadTicket(),
                    new MockMultipartFile("file", bytes));
        }

        void draft() {
            repository.update(
                    "update ai_engineering_task set action='CREATE_PCB_DRAFT' where task_id=?",
                    Long.valueOf(claim.taskId()));
        }

        byte[] zip() throws Exception {
            var out = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("project.eprj3"));
                zip.write("{}".getBytes());
                zip.closeEntry();
            }
            return out.toByteArray();
        }

        byte[] png() throws Exception {
            var out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "PNG", out);
            return out.toByteArray();
        }
    }

    @Test
    void missingObjCannotBeSucceededAndManifestNeverManufacturable() throws Exception {
        var f = new Artifacts();
        f.draft();
        f.add(EngineeringArtifactType.PCB_SOURCE, "source/project.zip", null, f.zip());
        f.add(EngineeringArtifactType.PCB_PREVIEW_2D, "preview/top.png", null, f.png());
        f.add(EngineeringArtifactType.PCB_BOM, "reports/bom.json", null, "[]".getBytes());
        f.add(
                EngineeringArtifactType.PCB_DRC_REPORT,
                "reports/drc.json",
                null,
                "{\"status\":\"PASS\",\"errorCount\":0,\"warningCount\":0,\"items\":[]}"
                        .getBytes());
        var done =
                f.artifacts.complete(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(
                                f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
        assertEquals("PARTIAL_SUCCESS", done.state());
        var manifest = f.artifacts.manifest(f.actor, Long.valueOf(f.claim.taskId()));
        assertFalse(manifest.canManufacture());
        assertTrue(manifest.missingTypes().contains(EngineeringArtifactType.PCB_MODEL_OBJ));
        assertFalse(EngineeringJson.write(manifest).contains("objectKey"));
        assertFalse(EngineeringJson.write(manifest).contains("private-engineering"));
    }

    @Test
    void connectionCheckNeedsNoPcbFiles() {
        var f = new Artifacts();
        var done =
                f.artifacts.complete(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(
                                f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
        assertEquals("SUCCEEDED", done.state());
    }

    @Test
    void sourceAndPreviewAreMandatoryForGeneratedDraft() {
        var f = new Artifacts();
        f.draft();
        var done =
                f.artifacts.complete(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(
                                f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
        assertEquals("FAILED", done.state());
    }

    @Test
    void missingMaterialDegradesModelAndKeepsSourceAndPngAccessible() throws Exception {
        for (boolean missingTexture : new boolean[] {false, true}) {
            var f = new Artifacts();
            f.draft();
            f.add(EngineeringArtifactType.PCB_SOURCE, "source/project.zip", null, f.zip());
            byte[] png = f.png();
            f.add(EngineeringArtifactType.PCB_PREVIEW_2D, "preview/top.png", null, png);
            f.add(EngineeringArtifactType.PCB_BOM, "reports/bom.json", null, "[]".getBytes());
            f.add(
                    EngineeringArtifactType.PCB_NETLIST,
                    "reports/netlist.json",
                    null,
                    "{}".getBytes());
            f.add(
                    EngineeringArtifactType.PCB_DRC_REPORT,
                    "reports/drc.json",
                    null,
                    "{\"status\":\"PASS\",\"errorCount\":0,\"warningCount\":0,\"items\":[]}"
                            .getBytes());
            f.upload(
                    "model/board.obj",
                    "mtllib board.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes());
            if (missingTexture)
                f.add(
                        EngineeringArtifactType.PCB_MODEL_MTL,
                        "model/board.mtl",
                        "model-1",
                        "newmtl board\nKd 0.2 0.3 0.4\nmap_Kd missing.png\n".getBytes());
            var before = f.artifacts.manifest(f.actor, Long.valueOf(f.claim.taskId()));
            assertTrue(before.missingTypes().isEmpty(), "此场景必需集合已齐，部分成功必须来自材质降级");
            assertEquals(missingTexture ? 7 : 6, before.artifacts().size());
            var preview =
                    before.artifacts().stream()
                            .filter(a -> a.type() == EngineeringArtifactType.PCB_PREVIEW_2D)
                            .findFirst()
                            .orElseThrow();
            try (var stream =
                    f.artifacts.open(f.actor, Long.valueOf(preview.artifactId())).input()) {
                assertArrayEquals(png, stream.readAllBytes());
            }
            var done =
                    f.artifacts.complete(
                            f.principal,
                            Long.valueOf(f.claim.taskId()),
                            new EngineeringLease(
                                    f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
            assertEquals("PARTIAL_SUCCESS", done.state());
            var manifest = f.artifacts.manifest(f.actor, Long.valueOf(f.claim.taskId()));
            assertEquals("PASS", manifest.drc().status());
            assertEquals("DEGRADED", manifest.modelGroups().get(0).status());
            assertTrue(manifest.modelGroups().get(0).uniformMaterial());
            assertEquals(
                    List.of(missingTexture ? "model/missing.png" : "model/board.mtl"),
                    manifest.modelGroups().get(0).missingPaths());
            assertNull(
                    f.repository
                            .device(f.actor.tenantId(), f.actor.userId(), f.principal.deviceId())
                            .getBusyTaskId());
        }
    }

    @Test
    void editorUnavailableEventMakesConnectionCheckFail() {
        var f = new Artifacts();
        var receipt =
                f.tasks.event(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        new EngineeringTaskEvent(
                                f.claim.attempt(),
                                f.claim.leaseToken(),
                                f.claim.revision(),
                                1,
                                EngineeringStage.CHECKING,
                                "编辑器尚未连接",
                                false,
                                "EDITOR_UNAVAILABLE"));
        var done =
                f.artifacts.complete(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(
                                f.claim.attempt(), f.claim.leaseToken(), receipt.revision()));
        assertEquals("FAILED", done.state());
        assertEquals("EDITOR_UNAVAILABLE", done.errorCode());
    }
}
