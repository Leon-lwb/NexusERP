package com.nexus.erp.wm.controller.serial;

import com.nexus.erp.wm.controller.*;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 原生产入库URL只核对真实最终清单，不另造完工事实或第二套冻结组。
 */
class WmSerialProductionOriginalUrlTest {
    @Test
    void originalProductionReceiptUsesWorkOrderIdentityAndExistingFrozenManifest()
            throws Exception {
        var f = new SerialAdapterFixture();
        f.group =
                new WmSerialInventoryRepository.Group(
                        99L,
                        "MES_FINAL/100/1/hash",
                        1,
                        "fp",
                        "hash",
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "PREPARED",
                        null);
        f.frozen = List.of(f.item(101));
        var service = mock(IWmProductionRecptService.class);
        doAnswer(
                        call -> {
                            f.inventory.post(f.request("PRD_WORK_ORDER", 70L, 0L));
                            return null;
                        })
                .when(service)
                .execute(10L);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmProductionRecptController(service)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        put("/erp/wm/productionrecpt/execute/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(101)))
                .andExpect(status().isOk());
        verify(f.selections, atLeastOnce()).snapshot("PRD_WORK_ORDER", 70L, 0L);
        verify(f.selections, never()).freeze(any());
        assertEquals(List.of("draft", "post"), f.events);
        verify(f.connection).commit();
        assertTrue(f.drafts.isEmpty());
    }

    @Test
    void arbitrarySnCannotReplaceRealManifest() throws Exception {
        var f = new SerialAdapterFixture();
        f.group =
                new WmSerialInventoryRepository.Group(
                        99L,
                        "MES_FINAL",
                        1,
                        "fp",
                        "hash",
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "PREPARED",
                        null);
        f.frozen = List.of(f.item(101));
        var service = mock(IWmProductionRecptService.class);
        doAnswer(
                        call -> {
                            f.inventory.post(f.request("PRD_WORK_ORDER", 70L, 0L));
                            return null;
                        })
                .when(service)
                .execute(10L);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmProductionRecptController(service)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        assertThrows(
                Exception.class,
                () ->
                        mvc.perform(
                                put("/erp/wm/productionrecpt/execute/10")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(102))));
        assertFalse(f.events.contains("post"));
        verify(f.connection).rollback();
    }

    @Test
    void missingManifestIsAnExplicitServerFailureAndCannotCreateStock() throws Exception {
        var f = new SerialAdapterFixture();
        var service = mock(IWmProductionRecptService.class);
        doAnswer(
                        call -> {
                            f.inventory.post(f.request("PRD_WORK_ORDER", 70L, 0L));
                            return null;
                        })
                .when(service)
                .execute(10L);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmProductionRecptController(service)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        var failure =
                assertThrows(
                        Exception.class,
                        () ->
                                mvc.perform(
                                        put("/erp/wm/productionrecpt/execute/10")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body(101))));
        assertTrue(failure.getMessage().contains("最终完工清单"));
        assertFalse(f.events.contains("post"));
        verify(f.connection).rollback();
    }

    @Test
    void productionDetailDoesNotConfuseReceiptIdWithWorkOrderId() throws Exception {
        var f = new SerialAdapterFixture();
        var service = mock(IWmProductionRecptService.class);
        var vo = new WmProductionRecptVo();
        vo.setRecptId(10L);
        vo.setWoId(70L);
        vo.setMaterialId(20L);
        when(service.selectById(10L)).thenReturn(vo);
        var mvc =
                MockMvcBuilders.standaloneSetup(new WmProductionRecptController(service))
                        .setControllerAdvice(new WmSerialDetailAdvice(f.store, f.json))
                        .build();
        mvc.perform(get("/erp/wm/productionrecpt/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailVersion").value(f.version));
        verify(f.selections).snapshot("PRD_WORK_ORDER", 70L, 0L);
        verify(f.selections, never()).snapshot(eq("PRD_WORK_ORDER"), eq(10L), anyLong());
    }

    static String body(long id) {
        return "{\"recptId\":\"10\",\"woId\":\"70\",\"commandId\":\"final-receipt\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\""
                + id
                + "\",\"batchId\":\"50\"}]}";
    }
}
