package com.nexus.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportRuntimeControllerTest {
    @TempDir Path directory;
    final ReportRequestActor actors=mock(ReportRequestActor.class);
    final ReportRuntimeService runtime=mock(ReportRuntimeService.class);
    final ReportArtifactStore artifacts=mock(ReportArtifactStore.class);
    final ReportActor actor=new ReportActor("T",2,10,true,List.of());
    final ReportRuntimeController controller=new ReportRuntimeController(actors,runtime,artifacts);

    @BeforeEach void setup(){when(actors.current()).thenReturn(actor);}

    @Test void runtimeAndExportViewsNeverSerializeArtifactPathsOrLeaseMetadata() throws Exception {
        var time=Instant.now();
        var run=new Run("1","T",2,"3","4","SNAPSHOT","SUCCEEDED","private-parameters","scope-hash",
            "C:/private/snapshot.rows","C:/private/layout.plan",1,time,time.plusSeconds(60),null);
        var job=new ExportJob("5","T",2,"1","PDF","request-key","request-hash","SUCCEEDED",1,"private-owner",
            time.plusSeconds(30),false,"C:/private/export.pdf",3L,time.plusSeconds(60),null);
        var json=new ObjectMapper().findAndRegisterModules();
        var runJson=json.valueToTree(ReportRuntimeController.view(run));
        var jobJson=json.valueToTree(ReportRuntimeController.view(job));
        for(String field:List.of("snapshot","plan","parameters","scopeHash","tenant","actor"))assertFalse(runJson.has(field));
        for(String field:List.of("file","leaseOwner","leaseUntil","requestKey","requestHash","tenant","actor"))assertFalse(jobJson.has(field));
        assertFalse(runJson.toString().contains("C:/private"));assertFalse(jobJson.toString().contains("C:/private"));
    }

    @Test void downloadStreamsOnlyTheAuthorizedTenantsFileAndUsesSafeAttachmentName() throws Exception {
        var file=directory.resolve("private-export.pdf");Files.write(file,new byte[]{1,2,3});
        var job=job("SUCCEEDED",Instant.now().plusSeconds(60));
        when(runtime.job(actor,"untrusted-request-id")).thenReturn(job);
        when(artifacts.read("T","private-file-reference")).thenReturn(file);
        var response=new MockHttpServletResponse();controller.file("untrusted-request-id",response);
        assertArrayEquals(new byte[]{1,2,3},response.getContentAsByteArray());
        assertEquals("application/pdf",response.getContentType());
        assertEquals("attachment; filename=\"nexus-report-5.pdf\"",response.getHeader("Content-Disposition"));
        assertEquals("no-store, private",response.getHeader("Cache-Control"));assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
        assertEquals("3",response.getHeader("Content-Length"));
        assertFalse(response.getHeader("Content-Disposition").contains(file.toString()));
        var order=inOrder(runtime,artifacts);order.verify(runtime).job(actor,"untrusted-request-id");order.verify(artifacts).read("T","private-file-reference");
    }

    @Test void permissionRevocationBlocksFileRead(){
        when(runtime.job(actor,"5")).thenThrow(ReportFault.denied());
        assertThrows(ReportFault.class,()->controller.file("5",new MockHttpServletResponse()));verifyNoInteractions(artifacts);
    }

    @Test void expiryBlocksFileRead(){
        when(runtime.job(actor,"5")).thenReturn(job("SUCCEEDED",Instant.now().minusSeconds(1)));
        assertEquals("SNAPSHOT_EXPIRED",assertThrows(ReportFault.class,()->controller.file("5",new MockHttpServletResponse())).code());
        verifyNoInteractions(artifacts);
    }

    @Test void cancelledOrRunningJobCannotBeDownloaded(){
        for(String state:List.of("CANCEL_REQUESTED","CANCELLED","RUNNING")){
            when(runtime.job(actor,"5")).thenReturn(job(state,Instant.now().plusSeconds(60)));
            assertEquals("RUN_NOT_READY",assertThrows(ReportFault.class,()->controller.file("5",new MockHttpServletResponse())).code());
        }
        verifyNoInteractions(artifacts);
    }

    private ExportJob job(String state,Instant expires){return new ExportJob("5","T",2,"1","PDF","key","hash",state,1,"owner",
        Instant.now().plusSeconds(30),false,"private-file-reference",3L,expires,null);}
}
