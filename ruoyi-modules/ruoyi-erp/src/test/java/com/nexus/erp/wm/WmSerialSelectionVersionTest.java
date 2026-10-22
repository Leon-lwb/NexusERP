package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nexus.common.core.domain.R;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.controller.WmSerialDetailAdvice;
import com.nexus.erp.wm.controller.serial.WmSerialBusinessStore;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.domain.vo.pda.PdaIssueDetailVo;
import com.nexus.erp.wm.service.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

class WmSerialSelectionVersionTest {
    @Test
    void clientSuppliedOneIsNotAnAuthoritativeSourceVersion() {
        var repo = mock(WmSerialInventoryRepository.class);
        var inventory = mock(WmSerialInventoryService.class);
        var service = new WmSerialSelectionService(repo, inventory);
        var request =
                WmSerialPostingRequest.out(
                        "WM_PRODUCTION_ISSUE",
                        1L,
                        3L,
                        "PRODUCTION_ISSUE",
                        20L,
                        BigDecimal.ONE,
                        30L,
                        null,
                        null);
        when(repo.request("WM_PRODUCTION_ISSUE", 1L, 3L)).thenReturn(request);
        when(repo.sourceHash(request)).thenReturn("a".repeat(64));
        var bo = new WmSerialSelectionBo();
        bo.setSourceDocType("WM_PRODUCTION_ISSUE");
        bo.setSourceDocId(1L);
        bo.setLineId(3L);
        bo.setCommandId("cmd");
        bo.setDetailVersion("1");
        assertThrows(RuntimeException.class, () -> service.freeze(bo));
        verifyNoInteractions(inventory);
    }

    @Test
    void actualPdaDetailReceivesOpaqueSourceVersionAndServerPolicyWithoutIdTruncation() {
        var selections = mock(WmSerialBusinessStore.class);
        TrackingPolicy policy = new TrackingPolicy(false, true, true, 7L, TrackingStage.STRICT);
        when(selections.detail("WM_PRODUCTION_ISSUE", 9007199254740993L, 9007199254740995L))
                .thenReturn(
                        new WmSerialBusinessStore.Detail(
                                "a".repeat(64), "2", policy, List.of(), "EMPTY", List.of()));
        ObjectMapper app = new ObjectMapper();
        app.registerModule(
                new SimpleModule().addSerializer(Long.class, ToStringSerializer.instance));
        var advice = new WmSerialDetailAdvice(selections, app);
        var detail = new PdaIssueDetailVo();
        detail.setIssueId(9007199254740993L);
        var line = new PdaIssueDetailVo.PdaIssueLineVo();
        line.setLineId(9007199254740995L);
        line.setMaterialId(9007199254740997L);
        detail.setLines(List.of(line));
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI())
                .thenReturn(URI.create("http://local/app/pda/wm/issue/9007199254740993"));
        Object response =
                advice.beforeBodyWrite(
                        R.ok(detail),
                        null,
                        MediaType.APPLICATION_JSON,
                        MappingJackson2HttpMessageConverter.class,
                        request,
                        mock(ServerHttpResponse.class));
        var root = (Map<?, ?>) response;
        var data = (Map<?, ?>) root.get("data");
        var row = (Map<?, ?>) ((List<?>) data.get("lines")).get(0);
        assertEquals("9007199254740993", data.get("issueId"));
        assertEquals("9007199254740995", row.get("lineId"));
        assertEquals("a".repeat(64), row.get("detailVersion"));
        assertEquals("2", row.get("detailRevision"));
        assertEquals(policy, row.get("policy"));
    }
}
