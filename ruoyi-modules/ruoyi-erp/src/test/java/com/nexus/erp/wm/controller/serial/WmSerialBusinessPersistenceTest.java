package com.nexus.erp.wm.controller.serial;

import com.nexus.common.core.domain.R;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.controller.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.vo.WmMiscRecptVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 草稿真实存储、原mapper行ID映射、刷新续扫及后台无HTTP执行回归。 */
class WmSerialBusinessPersistenceTest {
    @Test void originalSavePersistsUnknownEntityFieldsUsingTheActuallyInsertedLineId() throws Exception {
        var f=new SerialAdapterFixture();var stock=mock(IWmMiscRecptService.class);
        var headTarget=mock(WmMiscRecptMapper.class);when(headTarget.insert(any(WmMiscRecpt.class))).thenAnswer(call->{((WmMiscRecpt)call.getArgument(0)).setRecptId(10L);return 1;});var heads=f.proxy(headTarget);
        var lineTarget=mock(WmMiscRecptLineMapper.class);when(lineTarget.insert(any(WmMiscRecptLine.class))).thenAnswer(call->{((WmMiscRecptLine)call.getArgument(0)).setLineId(11L);return 1;});var lines=f.proxy(lineTarget);
        when(stock.insertByBo(any())).thenAnswer(call->{
            var bo=(com.nexus.erp.wm.domain.bo.WmMiscRecptBo)call.getArgument(0);var head=new WmMiscRecpt();heads.insert(head);
            var row=bo.getWmMiscRecptLineList().get(0);row.setRecptId(head.getRecptId());lines.insert(row);return 1;
        });
        WmMiscRecptVo vo=new WmMiscRecptVo();vo.setRecptId(10L);WmMiscRecptLine row=new WmMiscRecptLine();row.setLineId(11L);row.setRecptId(10L);row.setMaterialId(20L);vo.setWmMiscRecptLineList(List.of(row));when(stock.selectById(10L)).thenReturn(vo);
        var mvc=MockMvcBuilders.standaloneSetup(f.proxy(new WmMiscRecptController(stock))).addFilters(new WmSerialPayloadFilter(f.json)).setControllerAdvice(new WmSerialDetailAdvice(f.store,f.json)).build();
        mvc.perform(post("/erp/wm/miscrecpt").contentType(MediaType.APPLICATION_JSON)
            .content("{\"commandId\":\"save-real-id\",\"warehouseId\":\"30\",\"wmMiscRecptLineList\":[{\"materialId\":\"20\",\"quantity\":\"1\",\"snItems\":[{\"identityId\":\"9007199254740993\",\"batchId\":\"50\"}]}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.recptId").value("10")).andExpect(jsonPath("$.data.lines[0].snItems[0].identityId").value("9007199254740993"));
        assertEquals(1,f.drafts.size());assertEquals(11L,f.drafts.get(0).get("source_doc_line_id"));verify(f.selections,never()).freeze(any());
        mvc.perform(get("/erp/wm/miscrecpt/10")).andExpect(status().isOk()).andExpect(jsonPath("$.data.wmMiscRecptLineList[0].snItems[0].identityId").value("9007199254740993"))
            .andExpect(jsonPath("$.data.wmMiscRecptLineList[0].snItems[0].snCode").value("SN-真实"))
            .andExpect(jsonPath("$.data.wmMiscRecptLineList[0].detailVersion").value(f.version));
    }
    @Test void originalPcMaterialReturnCreatePersistsSnAlthoughItsLinesAreEntities() throws Exception {
        var f=new SerialAdapterFixture();var headTarget=mock(WmMaterialReturnMapper.class);
        when(headTarget.insert(any(WmMaterialReturn.class))).thenAnswer(call->{((WmMaterialReturn)call.getArgument(0)).setReturnId(10L);return 1;});var heads=f.proxy(headTarget);
        var lineTarget=mock(WmMaterialReturnLineMapper.class);when(lineTarget.insert(any(WmMaterialReturnLine.class))).thenAnswer(call->{((WmMaterialReturnLine)call.getArgument(0)).setLineId(11L);return 1;});var lines=f.proxy(lineTarget);
        var codes=mock(com.nexus.system.strategy.AutoCodeUtil.class);when(codes.genSerialCode(eq("WM_RETURN_CODE"),isNull())).thenReturn("RETURN-REAL");
        var helper=new com.nexus.erp.wm.service.impl.WmServiceHelper(codes,null,null,null);
        var mvc=MockMvcBuilders.standaloneSetup(f.proxy(new WmMaterialReturnController(heads,lines,helper))).addFilters(new WmSerialPayloadFilter(f.json)).build();
        mvc.perform(post("/erp/wm/return").contentType(MediaType.APPLICATION_JSON).content("{\"commandId\":\"return-create\",\"warehouseCode\":\"WH\",\"returnType\":\"GOOD\",\"sourceDoc\":\"REAL-ISSUE\",\"lines\":[{\"materialId\":\"20\",\"quantity\":\"1\",\"snItems\":[{\"identityId\":\"101\"}]}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].snItems[0].identityId").value("101"));
        assertEquals(101L,f.store.draft("WM_MATERIAL_RETURN",10L,11L).items().get(0).identityId());verify(f.selections,never()).freeze(any());
    }
    @Test void originalPdaReturnDetailEnrichesMissingMaterialIdFromTheRealSource() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_MATERIAL_RETURN",10L,11L,"pda-return-read",List.of(f.item(101)));
        var pda=mock(IPdaReturnService.class);var detail=new com.nexus.erp.wm.domain.vo.pda.PdaReturnDetailVo();detail.setReturnId(10L);
        var row=new com.nexus.erp.wm.domain.vo.pda.PdaReturnDetailVo.PdaReturnLineVo();row.setLineId(11L);row.setMaterialCode("MATERIAL-REAL");detail.setLines(List.of(row));when(pda.detail(10L)).thenReturn(detail);
        var mvc=MockMvcBuilders.standaloneSetup(new com.nexus.erp.wm.controller.app.AppPdaReturnController(pda)).setControllerAdvice(new WmSerialDetailAdvice(f.store,f.json)).build();
        mvc.perform(get("/app/pda/wm/return/10")).andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].materialId").value("20")).andExpect(jsonPath("$.data.lines[0].snItems[0].identityId").value("101"));
    }
    @Test void originalIndependentReceiveDetailReadAlsoReturnsPersistedSnAndVersion() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_MATERIAL_RECEIVE",10L,11L,"detail-read",List.of(new SnItem(101L,null,40L,null,50L)));
        var service=mock(IWmMaterialReceiveDetailService.class);var row=new com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo();
        row.setDetailId(200L);row.setReceiveId(10L);row.setLineId(11L);row.setMaterialId(20L);row.setBatchId(50L);row.setLocationId(40L);when(service.selectById(200L)).thenReturn(row);
        var mvc=MockMvcBuilders.standaloneSetup(new WmMaterialReceiveDetailController(service)).setControllerAdvice(new WmSerialDetailAdvice(f.store,f.json)).build();
        mvc.perform(get("/erp/wm/detail/200")).andExpect(status().isOk()).andExpect(jsonPath("$.data.snItems[0].identityId").value("101")).andExpect(jsonPath("$.data.detailVersion").value(f.version));
    }
    @Test void receiptBatchInsertCapturesEveryGeneratedIdInActualSubmissionOrder() throws Throwable {
        var f=new SerialAdapterFixture();var target=mock(WmMaterialReceiveLineMapper.class);
        when(target.insertBatch(anyCollection())).thenAnswer(call->{int i=0;for(Object o:(Collection<?>)call.getArgument(0))((WmMaterialReceiveLine)o).setLineId(21L+i++);return true;});var mapper=f.proxy(target);
        var route=SerialBusinessRoute.ofType("WM_MATERIAL_RECEIVE");var body=f.body("{\"receiveId\":\"10\",\"commandId\":\"batch-insert\",\"wmMaterialReceiveLineList\":[{\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}]}");
        f.adapter.invoke(route,"/erp/wm/receive",body,"edit",()->{var row=new WmMaterialReceiveLine();row.setReceiveId(10L);mapper.insertBatch(List.of(row));f.lines=List.of(21L);return R.ok();});
        assertTrue(f.store.draft(route.type(),10L,11L).items().isEmpty());assertEquals(101L,f.store.draft(route.type(),10L,21L).items().get(0).identityId());
    }
    @Test void approvalOrMqPostingReloadsSavedSelectionWithoutAnyHttpRequest() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_TRANSFER",10L,11L,"save-before-approval",List.of(f.item(101)));
        f.events.clear();f.inventory.post(f.request("WM_TRANSFER",10L,11L));
        assertEquals(List.of("freeze","post"),f.events);verify(f.selections).freeze(argThat(bo->bo.getSourceDocType().equals("WM_TRANSFER")&&bo.getSnItems().size()==1));assertTrue(f.drafts.isEmpty());
    }
    @Test void externalBusinessFactDriftCannotReuseSavedDraft() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_TRANSFER",10L,11L,"save-drift",List.of(f.item(101)));f.version="opaque:changed-by-other-url";
        assertThrows(RuntimeException.class,()->f.store.freeze("WM_TRANSFER",10L,11L,null));verify(f.selections,never()).freeze(any());assertEquals(1,f.drafts.size());
    }
    @Test void frozenSelectionIsReplayedWithItsOriginalCommandAndRevision() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_MISC_RECPT",10L,11L,"replay",List.of(f.item(101)));
        f.group=new WmSerialInventoryRepository.Group(99L,"original-frozen-command",1,"fp","hash",BigDecimal.ONE,BigDecimal.ZERO,"POSTED",null);f.frozen=List.of(f.item(101));
        f.store.freeze("WM_MISC_RECPT",10L,11L,"different-http-attempt");
        verify(f.selections).freeze(argThat(bo->bo.getCommandId().equals("original-frozen-command")&&bo.getRevision()==1));
    }
    @Test void frozenSelectionCannotBeSilentlyReplaced() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_MISC_RECPT",10L,11L,"replacement",List.of(f.item(102)));
        f.group=new WmSerialInventoryRepository.Group(99L,"original",1,"fp","hash",BigDecimal.ONE,BigDecimal.ZERO,"PREPARED",null);f.frozen=List.of(f.item(101));
        assertThrows(RuntimeException.class,()->f.store.freeze("WM_MISC_RECPT",10L,11L,"replace"));verify(f.selections,never()).freeze(any());
    }
    @Test void nextPartialProductionIssueReturnsEmptyCurrentSelectionAndExplicitHistory() throws Exception {
        var f=new SerialAdapterFixture();f.group=new WmSerialInventoryRepository.Group(99L,"posted",1,"fp","hash",BigDecimal.ONE,BigDecimal.ZERO,"POSTED",null);f.frozen=List.of(f.item(101));
        var result=f.store.detail("WM_PRODUCTION_ISSUE",10L,11L);assertTrue(result.snItems().isEmpty());assertEquals(1,result.previousSnItems().size());assertEquals("NEXT_REVISION",result.selectionStatus());assertEquals("2",result.detailRevision());
    }
    @Test void mixedRetainedAndNewProductionLinesKeepTheirOwnIdentitySelections() throws Throwable {
        var f=new SerialAdapterFixture();var route=SerialBusinessRoute.ofType("WM_PRODUCTION_ISSUE");
        var body=f.body("{\"issueId\":\"10\",\"commandId\":\"mixed-lines\",\"wmProductionIssueLineList\":[{\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]},{\"snItems\":[{\"identityId\":\"102\"}]}]}");
        f.adapter.invoke(route,"/erp/wm/productionissue",body,"edit",()->{var row=new WmProductionIssueLine();row.setIssueId(10L);row.setLineId(21L);f.adapter.inserted(row);f.lines=List.of(11L,21L);return R.ok();});
        assertEquals(101L,f.store.draft(route.type(),10L,11L).items().get(0).identityId());assertEquals(102L,f.store.draft(route.type(),10L,21L).items().get(0).identityId());
    }
    @Test void trackingPolicyDriftCannotReuseStoredDraft() throws Exception {
        var f=new SerialAdapterFixture();f.store.saveDraft("WM_TRANSFER",10L,11L,"policy-version",List.of(f.item(101)));
        f.policy=new TrackingPolicy(true,true,false,8L,TrackingStage.STRICT);
        assertThrows(RuntimeException.class,()->f.store.freeze("WM_TRANSFER",10L,11L,null));verify(f.selections,never()).freeze(any());
    }
    @Test void wrongParentLineIsRejectedBeforeAnyBusinessWrite() throws Throwable {
        var f=new SerialAdapterFixture();var route=SerialBusinessRoute.ofType("WM_TRANSFER");
        assertThrows(RuntimeException.class,()->f.adapter.invoke(route,"/erp/wm/transfer/execute/10",f.body("{\"commandId\":\"wrong-parent\",\"lineId\":\"99\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}"),"execute",()->{fail("不可执行");return null;}));
        verify(f.connection).rollback();assertTrue(f.drafts.isEmpty());
    }
    @Test void rootSourceCannotOverrideOriginalUrlSource() throws Throwable {
        var f=new SerialAdapterFixture();assertThrows(RuntimeException.class,()->f.adapter.invoke(SerialBusinessRoute.ofType("WM_TRANSFER"),"/erp/wm/transfer/execute/10",f.body("{\"transferId\":\"999\"}"),"execute",()->{fail("不可执行");return null;}));
    }
}
