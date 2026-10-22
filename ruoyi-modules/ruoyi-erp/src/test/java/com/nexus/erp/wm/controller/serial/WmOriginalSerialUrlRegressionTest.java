package com.nexus.erp.wm.controller.serial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexus.common.trace.TrackingPolicy;
import com.nexus.common.trace.TrackingStage;
import com.nexus.erp.wm.controller.*;
import com.nexus.erp.wm.controller.app.AppPdaIssueController;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.service.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通过真实旧controller URL/body和生产适配链验证，不以源码正则代替行为。
 */
class WmOriginalSerialUrlRegressionTest {
    @Test
    void originalExecuteMustConsumeSerialBody() throws Exception {
        var f = new SerialAdapterFixture();
        var stock = mock(IWmMiscRecptService.class);
        doAnswer(
                        call -> {
                            f.inventory.post(f.request("WM_MISC_RECPT", 10L, 11L));
                            return null;
                        })
                .when(stock)
                .execute(10L);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        put("/erp/wm/miscrecpt/execute/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"commandId\":\"old-url-command\",\"wmMiscRecptLineList\":[{\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"9007199254740993\",\"batchId\":\"50\"}]}]}"))
                .andExpect(status().isOk());
        var capture = ArgumentCaptor.forClass(WmSerialSelectionBo.class);
        verify(f.selections).freeze(capture.capture());
        var bo = capture.getValue();
        assertEquals(9007199254740993L, bo.getSnItems().get(0).identityId());
        assertEquals(f.version, bo.getDetailVersion());
        assertEquals("WM_MISC_RECPT", bo.getSourceDocType());
        assertEquals(List.of("draft", "freeze", "post"), f.events);
        verify(f.connection).commit();
    }

    @Test
    void nominalSuccessCannotHideAnExecutorThatStillIgnoresSnPayload() throws Exception {
        var f = new SerialAdapterFixture();
        var stock = mock(IWmMiscRecptService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        var failure =
                assertThrows(
                        Exception.class,
                        () ->
                                mvc.perform(
                                        put("/erp/wm/miscrecpt/execute/10")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"commandId\":\"not-consumed\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}")));
        assertTrue(failure.getMessage().contains("未消费"));
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }

    @Test
    void alreadyPostedOriginalReplayDoesNotRewriteFrozenSelectionAsDraft() throws Exception {
        var f = new SerialAdapterFixture();
        f.group =
                new WmSerialInventoryRepository.Group(
                        99L,
                        "original",
                        1,
                        "fp",
                        "hash",
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "POSTED",
                        null);
        f.frozen = List.of(f.item(101));
        var stock = mock(IWmMiscRecptService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        put("/erp/wm/miscrecpt/execute/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"commandId\":\"replay\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}"))
                .andExpect(status().isOk());
        assertTrue(f.drafts.isEmpty());
        verify(f.selections, never()).freeze(any());
        verify(f.connection).commit();
    }

    @Test
    void sameIdentityWithDifferentFrozenDimensionsCannotPassAsOriginalReplay() throws Exception {
        var f = new SerialAdapterFixture();
        f.group =
                new WmSerialInventoryRepository.Group(
                        99L,
                        "original",
                        1,
                        "fp",
                        "hash",
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "POSTED",
                        null);
        f.frozen = List.of(f.item(101));
        var stock = mock(IWmMiscRecptService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        assertThrows(
                Exception.class,
                () ->
                        mvc.perform(
                                put("/erp/wm/miscrecpt/execute/10")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"commandId\":\"changed-replay\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\",\"targetLocationId\":\"999\"}]}")));
        assertTrue(f.drafts.isEmpty());
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }

    @Test
    void staleVersionBlocksOriginalDelegateBeforeMutation() throws Exception {
        var f = new SerialAdapterFixture();
        var stock = mock(IWmMiscRecptService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        var error =
                assertThrows(
                        Exception.class,
                        () ->
                                mvc.perform(
                                        put("/erp/wm/miscrecpt/execute/10")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"commandId\":\"stale\",\"lineId\":\"11\",\"detailVersion\":\"old\",\"snItems\":[{\"identityId\":\"101\"}]}")));
        assertTrue(error.getMessage().contains("版本"));
        verifyNoInteractions(stock);
        verify(f.connection).rollback();
    }

    @Test
    void ordinaryQuantityRequestStillUsesOriginalUrlWithoutSnOrVersion() throws Exception {
        var f = new SerialAdapterFixture();
        f.policy = new TrackingPolicy(false, false, true, 1L, TrackingStage.AUDIT);
        var stock = mock(IWmMiscRecptService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(put("/erp/wm/miscrecpt/execute/10")).andExpect(status().isOk());
        verify(stock).execute(10L);
        verify(f.selections, never()).freeze(any());
        verify(f.connection).commit();
    }

    @Test
    void pdaPickFreezesAfterOriginalQuantityWriteAndBeforeNestedPosting() throws Exception {
        var f = new SerialAdapterFixture();
        var pda = mock(IPdaIssueService.class);
        when(pda.pick(eq(10L), any()))
                .thenAnswer(
                        call -> {
                            f.events.add("original-quantity-write");
                            f.version = "opaque:after-pick";
                            f.inventory.post(f.request("WM_PRODUCTION_ISSUE", 10L, 11L));
                            return PdaActionResultVo.ok();
                        });
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new AppPdaIssueController(pda)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        post("/app/pda/wm/issue/10/line/pick")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"lineId\":\"11\",\"qty\":\"1\",\"locationCode\":\"A\",\"commandId\":\"pick-command\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        assertEquals(List.of("original-quantity-write", "draft", "freeze", "post"), f.events);
        verify(f.selections)
                .freeze(argThat(bo -> "opaque:after-pick".equals(bo.getDetailVersion())));
        verify(f.connection).commit();
    }

    @Test
    void outerSuccessInnerPdaFailureRollsBackWithoutPersistingSelection() throws Exception {
        var f = new SerialAdapterFixture();
        var pda = mock(IPdaIssueService.class);
        when(pda.pick(eq(10L), any())).thenReturn(PdaActionResultVo.fail("原始来源不允许"));
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new AppPdaIssueController(pda)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        post("/app/pda/wm/issue/10/line/pick")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"lineId\":\"11\",\"qty\":\"1\",\"locationCode\":\"A\",\"commandId\":\"pick-failed\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false));
        assertTrue(f.drafts.isEmpty());
        verify(f.selections, never()).freeze(any());
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }

    @Test
    void originalPostFailureRollsBackSavedSnAndOriginalMutationTogether() throws Exception {
        var f = new SerialAdapterFixture();
        var stock = mock(IWmMiscRecptService.class);
        doAnswer(
                        call -> {
                            f.inventory.post(f.request("WM_MISC_RECPT", 10L, 11L));
                            throw new IllegalStateException("原执行失败");
                        })
                .when(stock)
                .execute(10L);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        assertThrows(
                Exception.class,
                () ->
                        mvc.perform(
                                put("/erp/wm/miscrecpt/execute/10")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"lineId\":\"11\",\"commandId\":\"rollback\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}")));
        verify(f.selections).freeze(any());
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }
}
