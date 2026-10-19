package com.nexus.erp.wm.controller.serial;

import com.nexus.erp.wm.controller.WmSerialDetailAdvice;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 每项经过实际controller和原URL，验证不同端点并非只有新freeze URL可用。
 */
class WmSerialOriginalRoutesTest {
    @ParameterizedTest
    @CsvSource({
        "wm,WmMaterialReceive,IWmMaterialReceiveService,executeReceive,/erp/wm/receive/execute/10,POST,WM_MATERIAL_RECEIVE",
        "wm,WmProductionIssue,IWmProductionIssueService,execute,/erp/wm/productionissue/execute/10,PUT,WM_PRODUCTION_ISSUE",
        "wm,WmTransfer,IWmTransferService,execute,/erp/wm/transfer/execute/10,PUT,WM_TRANSFER",
        "wm,WmStockTakingPlan,IWmStockTakingPlanService,postAdjustment,/erp/wm/stocktaking/postAdjustment/10,PUT,WM_STOCK_TAKING",
        "wm,WmSalesIssue,IWmSalesIssueService,completeSalesIssue,/erp/wm/salesIssue/complete/10,PUT,WM_SALES_ISSUE",
        "wm,WmMiscIssue,IWmMiscIssueService,execute,/erp/wm/miscissue/execute/10,PUT,WM_MISC_ISSUE",
        "so,SoReturn,ISoReturnService,execute,/erp/so/return/execute/10,PUT,SO_RETURN",
        "pu,PuPurchaseReturn,IPuPurchaseReturnService,execute,/erp/pu/return/execute/10,PUT,PU_PURCHASE_RETURN"
    })
    @SuppressWarnings({"rawtypes", "unchecked"})
    void originalPcExecuteUrlsConsumeTheRealLineCollection(
            String module,
            String name,
            String serviceName,
            String action,
            String url,
            String method,
            String type)
            throws Exception {
        var f = new SerialAdapterFixture();
        Class serviceClass = Class.forName("com.nexus.erp." + module + ".service." + serviceName);
        Object service =
                mock(
                        serviceClass,
                        call -> {
                            if (call.getMethod().getName().equals(action)) {
                                f.inventory.post(f.request(type, 10L, 11L));
                                return call.getMethod().getReturnType() == int.class ? 1 : null;
                            }
                            return RETURNS_DEFAULTS.answer(call);
                        });
        Object controller =
                Class.forName("com.nexus.erp." + module + ".controller." + name + "Controller")
                        .getConstructor(serviceClass)
                        .newInstance(service);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(controller))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        String property = SerialBusinessRoute.ofType(type).linesProperty();
        String body =
                "{\"commandId\":\"execute-"
                        + type
                        + "\",\""
                        + property
                        + "\":[{\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}]}";
        mvc.perform(
                        (method.equals("POST") ? post(url) : put(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(f.selections)
                .freeze(
                        argThat(
                                bo ->
                                        type.equals(bo.getSourceDocType())
                                                && bo.getSourceDocId() == 10L
                                                && bo.getLineId() == 11L));
        assertEquals(List.of("draft", "freeze", "post"), f.events);
        verify(f.connection).commit();
    }

    @ParameterizedTest
    @CsvSource({
        "AppPdaReceive,IPdaReceiveService,confirmLine,/app/pda/wm/receive/10/line/confirm,WM_MATERIAL_RECEIVE",
        "AppPdaReturn,IPdaReturnService,confirmLine,/app/pda/wm/return/10/line/confirm,WM_MATERIAL_RETURN",
        "AppPdaTransfer,IPdaTransferService,takeoff,/app/pda/wm/transfer/10/line/takeoff,WM_TRANSFER",
        "AppPdaStocktaking,IPdaStocktakingService,count,/app/pda/wm/stocktaking/10/line/count,WM_STOCK_TAKING"
    })
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pdaOriginalFlatLineBodyPersistsSelectionAfterTheOriginalWrite(
            String name, String serviceName, String action, String url, String type)
            throws Exception {
        var f = new SerialAdapterFixture();
        Class serviceClass = Class.forName("com.nexus.erp.wm.service." + serviceName);
        Object service =
                mock(
                        serviceClass,
                        call -> {
                            if (call.getMethod().getName().equals(action)) {
                                f.version = "opaque:after-original-write";
                                return com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo.ok();
                            }
                            return RETURNS_DEFAULTS.answer(call);
                        });
        Object controller =
                Class.forName("com.nexus.erp.wm.controller.app." + name + "Controller")
                        .getConstructor(serviceClass)
                        .newInstance(service);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(controller))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"lineId\":\"11\",\"qty\":\"1\",\"countedQty\":\"1\",\"locationCode\":\"A\",\"commandId\":\"pda-save\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        assertEquals(101L, f.store.draft(type, 10L, 11L).items().get(0).identityId());
        assertTrue(
                f.store
                        .draft(type, 10L, 11L)
                        .command()
                        .contains(
                                WmSerialBusinessStore.hash(
                                        f.version + "/policy/" + f.policy.version())));
        verify(f.connection).commit();
    }

    @ParameterizedTest
    @CsvSource({
        "wm,WmStockTakingPlan,IWmStockTakingPlanService,postAdjustment,/erp/wm/stocktaking/postAdjustment/10,PUT,WM_STOCK_TAKING",
        "wm/app,AppPdaStocktaking,IPdaStocktakingService,post,/app/pda/wm/stocktaking/10/post,POST,WM_STOCK_TAKING",
        "prd,PrdReworkOrder,IPrdReworkOrderService,issueMaterial,/erp/prd/rework/issue/10,PUT,PRD_REWORK_ORDER",
        "prd,PrdReworkOrder,IPrdReworkOrderService,receiveMaterial,/erp/prd/rework/receive/10,PUT,PRD_REWORK_ORDER_RECEIVE"
    })
    @SuppressWarnings({"rawtypes", "unchecked"})
    void everyRealPostingActionMustRejectNominalSuccessWithoutSnConsumption(
            String module,
            String name,
            String serviceName,
            String action,
            String url,
            String method,
            String type)
            throws Exception {
        var f = new SerialAdapterFixture();
        String serviceModule = module.split("/")[0];
        Class serviceClass =
                Class.forName("com.nexus.erp." + serviceModule + ".service." + serviceName);
        Object service =
                mock(
                        serviceClass,
                        call ->
                                call.getMethod().getName().equals(action) && module.endsWith("/app")
                                        ? com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo.ok()
                                        : RETURNS_DEFAULTS.answer(call));
        String controllerPackage =
                "com.nexus.erp."
                        + serviceModule
                        + ".controller"
                        + (module.endsWith("/app") ? ".app" : "");
        Object controller =
                Class.forName(controllerPackage + "." + name + "Controller")
                        .getConstructor(serviceClass)
                        .newInstance(service);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(controller))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        String body =
                "{\"commandId\":\"must-consume\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}";
        var failure =
                assertThrows(
                        Exception.class,
                        () ->
                                mvc.perform(
                                        (method.equals("POST") ? post(url) : put(url))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body)));
        assertTrue(failure.getMessage().contains("未消费"));
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }

    @ParameterizedTest
    @CsvSource({
        "wm,WmStockTakingPlan,IWmStockTakingPlanService,postAdjustment,/erp/wm/stocktaking/postAdjustment/10,PUT,WM_STOCK_TAKING",
        "wm/app,AppPdaStocktaking,IPdaStocktakingService,post,/app/pda/wm/stocktaking/10/post,POST,WM_STOCK_TAKING",
        "prd,PrdReworkOrder,IPrdReworkOrderService,issueMaterial,/erp/prd/rework/issue/10,PUT,PRD_REWORK_ORDER",
        "prd,PrdReworkOrder,IPrdReworkOrderService,receiveMaterial,/erp/prd/rework/receive/10,PUT,PRD_REWORK_ORDER_RECEIVE"
    })
    @SuppressWarnings({"rawtypes", "unchecked"})
    void realPostingActionsReplayTheOriginalGroupBeforeCurrentVersionChecks(
            String module,
            String name,
            String serviceName,
            String action,
            String url,
            String method,
            String type)
            throws Exception {
        var f = new SerialAdapterFixture();
        String serviceModule = module.split("/")[0];
        var executions = new java.util.concurrent.atomic.AtomicInteger();
        var core =
                org.springframework.test.util.AopTestUtils
                        .<WmSerialInventoryService>getUltimateTargetObject(f.inventory);
        when(core.post(any()))
                .thenAnswer(
                        call -> {
                            var old = f.group;
                            f.group =
                                    new WmSerialInventoryRepository.Group(
                                            old.id(),
                                            old.commandId(),
                                            old.detailVersion(),
                                            old.fingerprint(),
                                            old.contentHash(),
                                            old.quantity(),
                                            old.amount(),
                                            "POSTED",
                                            null);
                            f.commandRows.get(old.commandId()).put("status", "POSTED");
                            return true;
                        });
        Class serviceClass =
                Class.forName("com.nexus.erp." + serviceModule + ".service." + serviceName);
        Object service =
                mock(
                        serviceClass,
                        call -> {
                            if (call.getMethod().getName().equals(action)) {
                                executions.incrementAndGet();
                                long line = SerialBusinessRoute.ofType(type).whole() ? 0L : 11L;
                                f.inventory.post(f.request(type, 10L, line));
                                return module.endsWith("/app")
                                        ? com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo.ok()
                                        : null;
                            }
                            return RETURNS_DEFAULTS.answer(call);
                        });
        String controllerPackage =
                "com.nexus.erp."
                        + serviceModule
                        + ".controller"
                        + (module.endsWith("/app") ? ".app" : "");
        Object controller =
                Class.forName(controllerPackage + "." + name + "Controller")
                        .getConstructor(serviceClass)
                        .newInstance(service);
        var mvc =
                MockMvcBuilders.standaloneSetup(f.proxy(controller))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        String body =
                "{\"commandId\":\"original-action\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}";
        mvc.perform(
                        (method.equals("POST") ? post(url) : put(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
        f.version = "opaque:later-normal-business-change";
        mvc.perform(
                        (method.equals("POST") ? post(url) : put(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.lines[0].postingGroupId").value("99"));
        assertEquals(1, executions.get());
        verify(f.connection, times(2)).commit();
        assertTrue(f.drafts.isEmpty());
    }

    @Test
    void reworkCompletionWithoutStockEffectKeepsItsOriginalStateTransition() throws Exception {
        var f = new SerialAdapterFixture();
        var service = mock(com.nexus.erp.prd.service.IPrdReworkOrderService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                f.proxy(
                                        new com.nexus.erp.prd.controller.PrdReworkOrderController(
                                                service)))
                        .addFilters(new WmSerialPayloadFilter(f.json))
                        .build();
        mvc.perform(
                        put("/erp/prd/rework/complete/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"commandId\":\"state-transition\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"50\"}]}"))
                .andExpect(status().isOk());
        verify(service).complete(10L);
        verify(f.selections, never()).freeze(any());
        verify(f.connection).commit();
    }
}
