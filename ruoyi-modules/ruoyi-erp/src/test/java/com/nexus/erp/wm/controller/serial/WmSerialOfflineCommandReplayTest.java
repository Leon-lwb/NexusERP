package com.nexus.erp.wm.controller.serial;

import com.nexus.erp.wm.controller.*;
import com.nexus.erp.wm.controller.app.AppPdaReceiveController;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.AopTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 原离线任务已提交但ACK丢失、实物随后移库，再重放同一原URL/原载荷。 */
class WmSerialOfflineCommandReplayTest {
    static final String RECEIVE="/app/pda/wm/receive/10/finish";
    static final String PAYLOAD="{\"receiveId\":\"10\",\"commandId\":\"offline-receive-ACK-lost\",\"lineId\":\"11\",\"detailVersion\":\"opaque:事实/一\",\"quantityReceived\":\"1\",\"snItems\":[{\"identityId\":\"101\",\"targetLocationId\":\"40\",\"batchId\":\"50\"}]}";
    static class Scenario {
        final SerialAdapterFixture f=new SerialAdapterFixture();
        final IPdaReceiveService receive=mock(IPdaReceiveService.class);
        final IWmTransferService transfer=mock(IWmTransferService.class);
        final AtomicInteger physicalPosts=new AtomicInteger();
        final Map<String,Long> position=new HashMap<>();
        final MockMvc mvc;
        Scenario() throws Exception {
            WmSerialInventoryService core=AopTestUtils.getUltimateTargetObject(f.inventory);
            when(core.post(any())).thenAnswer(call->{
                WmSerialPostingRequest r=call.getArgument(0);f.events.add("post");physicalPosts.incrementAndGet();
                position.put("warehouse",r.targetWarehouseId()==null?r.warehouseId():r.targetWarehouseId());
                position.put("location",r.targetLocationId()==null?r.locationId():r.targetLocationId());
                var old=f.group;f.group=new WmSerialInventoryRepository.Group(old.id(),old.commandId(),old.detailVersion(),old.fingerprint(),old.contentHash(),old.quantity(),old.amount(),"POSTED",null);
                f.commandRows.get(old.commandId()).put("status","POSTED");return true;
            });
            when(receive.finish(10L)).thenAnswer(call->{
                // 原收货分配在真实执行前变化，所以冻结hash不同于HTTP改量前版本。
                f.version="opaque:source-after-original-receive-write";
                f.inventory.post(WmSerialPostingRequest.in("WM_MATERIAL_RECEIVE",10L,11L,"RECEIVE_IN",20L,BigDecimal.ONE,30L,40L,50L,null));
                f.version="opaque:source-after-receive-committed";return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
            });
            doAnswer(call->{f.inventory.post(WmSerialPostingRequest.out("WM_TRANSFER",20L,21L,"TRANSFER",20L,BigDecimal.ONE,30L,40L,50L).transfer(80L,null,90L));return null;}).when(transfer).execute(20L);
            mvc=MockMvcBuilders.standaloneSetup(f.proxy(new AppPdaReceiveController(receive)),f.proxy(new WmTransferController(transfer))).addFilters(new WmSerialPayloadFilter(f.json)).build();
        }
        void commitLoseAckThenMove() throws Exception {
            // 故意丢弃首次ACK；服务端Spring事务已经提交，不能把网络丢包当成业务回滚。
            mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isOk());
            assertEquals(1,physicalPosts.get());verify(f.connection).commit();assertEquals(30L,position.get("warehouse"));
            f.lines=List.of(21L);f.version="opaque:transfer-current";
            mvc.perform(put("/erp/wm/transfer/execute/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"commandId\":\"later-physical-transfer\",\"lineId\":\"21\",\"detailVersion\":\"opaque:transfer-current\",\"snItems\":[{\"identityId\":\"101\",\"sourceStockId\":\"501\",\"targetLocationId\":\"90\",\"batchId\":\"50\"}]}"))
                .andExpect(status().isOk());
            assertEquals(2,physicalPosts.get());assertEquals(80L,position.get("warehouse"));assertEquals(90L,position.get("location"));
            f.lines=List.of(11L);f.version="opaque:normal-later-source-change";
        }
    }
    @Test void committedAckLossThenTransferReplaysOriginalPayloadWithoutAnotherReceiveOrStockEffect() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true))
            .andExpect(jsonPath("$.data.lines[0].postingGroupId").value("99"));
        verify(s.receive,times(1)).finish(10L);verify(s.transfer,times(1)).execute(20L);assertEquals(2,s.physicalPosts.get());
        assertEquals(80L,s.position.get("warehouse"));assertEquals(90L,s.position.get("location"));assertTrue(s.f.drafts.isEmpty());
        verify(s.f.repo,never()).stocks(anyLong());assertEquals(2,s.f.commandRows.size());
        verify(s.f.selections,times(2)).freeze(argThat(bo->bo.getCommandId().equals(s.f.commandRows.values().stream().filter(row->row.get("source_doc_type").equals("WM_MATERIAL_RECEIVE")).findFirst().orElseThrow().get("command_id"))&&bo.getDetailVersion().equals("opaque:source-after-original-receive-write")));
    }
    @Test void sameOfflineCommandWithChangedQuantityIsRejectedAfterCommitAndTransfer() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("quantityReceived\":\"1","quantityReceived\":\"2"))));
        assertTrue(error.getMessage().contains("命令")&&error.getMessage().contains("内容"));assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }
    @Test void sameOfflineCommandWithChangedSnIsRejectedAfterCommitAndTransfer() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("identityId\":\"101","identityId\":\"102"))));
        assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }
    @Test void newCommandCannotReuseTheOldDetailVersionAfterCommitAndTransfer() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("offline-receive-ACK-lost","new-offline-command"))));
        assertTrue(error.getMessage().contains("版本"));assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }
    @Test void sameOfflineCommandCannotChangeTheOriginalOpaqueVersionText() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("opaque:事实/一","opaque:forged-original-version"))));
        assertTrue(error.getMessage().contains("命令")&&error.getMessage().contains("内容"));assertEquals(2,s.physicalPosts.get());
    }
    @Test void replayUsesOriginalPolicyAndRevisionWithoutReadingTodaysPolicySnapshot() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();s.f.policy=new com.nexus.common.trace.TrackingPolicy(false,true,true,8L,com.nexus.common.trace.TrackingStage.AUDIT);
        clearInvocations(s.f.selections);
        s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].policyVersion").value("7"));
        verify(s.f.selections).freeze(argThat(bo->bo.getPolicyVersion()==7&&bo.getRevision()==1));verify(s.f.selections,never()).snapshot(anyString(),anyLong(),anyLong());assertEquals(2,s.physicalPosts.get());
    }
    @Test void missingHistoricalProofCannotBeAcknowledgedOrReposted() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();
        doThrow(new com.nexus.common.core.exception.ServiceException("原命令历史证明缺失")).when(s.f.selections).freeze(any());
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)));
        assertTrue(error.getMessage().contains("历史证明"));assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);assertTrue(s.f.drafts.isEmpty());
    }
    @Test void reserializingObjectKeysDoesNotTurnTheOriginalTaskIntoANewCommand() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();var parsed=s.f.body(PAYLOAD);var reordered=s.f.json.createObjectNode();List<String> keys=new ArrayList<>();parsed.fieldNames().forEachRemaining(keys::add);Collections.reverse(keys);keys.forEach(key->reordered.set(key,parsed.get(key)));
        s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(reordered.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].postingGroupId").value("99"));
        assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }
    @Test void olderPartialIssueCommandReplaysItsOwnRevisionAfterANewerCommandOnTheSameLine() throws Exception {
        var f=new SerialAdapterFixture();var pda=mock(IPdaIssueService.class);AtomicInteger posts=new AtomicInteger();
        WmSerialInventoryService core=AopTestUtils.getUltimateTargetObject(f.inventory);
        when(core.post(any())).thenAnswer(call->{posts.incrementAndGet();var old=f.group;f.group=new WmSerialInventoryRepository.Group(old.id(),old.commandId(),old.detailVersion(),old.fingerprint(),old.contentHash(),old.quantity(),old.amount(),"POSTED",null);f.commandRows.get(old.commandId()).put("status","POSTED");return true;});
        when(pda.pick(eq(10L),any())).thenAnswer(call->{f.version="freeze:issue-"+(posts.get()+1);f.inventory.post(WmSerialPostingRequest.out("WM_PRODUCTION_ISSUE",10L,11L,"PRODUCTION_ISSUE",20L,BigDecimal.ONE,30L,40L,50L));f.version="after:issue-"+posts.get();return PdaActionResultVo.ok();});
        var mvc=MockMvcBuilders.standaloneSetup(f.proxy(new com.nexus.erp.wm.controller.app.AppPdaIssueController(pda))).addFilters(new WmSerialPayloadFilter(f.json)).build();
        String url="/app/pda/wm/issue/10/line/pick";
        String first="{\"commandId\":\"partial-first\",\"lineId\":\"11\",\"qty\":\"1\",\"locationCode\":\"A\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\",\"sourceStockId\":\"501\",\"batchId\":\"50\"}]}";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk());
        String next=first.replace("partial-first","partial-next").replace("opaque:事实/一","after:issue-1").replace("identityId\":\"101","identityId\":\"102").replace("sourceStockId\":\"501","sourceStockId\":\"502");
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(next)).andExpect(status().isOk());assertEquals(2,f.group.detailVersion());assertEquals(100L,f.group.id());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].postingGroupId").value("99")).andExpect(jsonPath("$.data.lines[0].detailRevision").value("1")).andExpect(jsonPath("$.data.lines[0].snItems[0].identityId").value("101"));
        assertEquals(2,posts.get());verify(pda,times(2)).pick(eq(10L),any());assertEquals(2,f.commandRows.size());
    }

    @Test void sameCommandCannotClearSnItemsAndUseANewVersionToBypassTheOriginalRequest() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();var changed=(com.fasterxml.jackson.databind.node.ObjectNode)s.f.body(PAYLOAD);changed.put("detailVersion",s.f.version);changed.set("snItems",s.f.json.createArrayNode());
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(changed.toString())));
        assertTrue(error.getMessage().contains("内容"));assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }
    @Test void sameCommandCannotOmitSnItemsAndUseANewVersionToBypassTheOriginalRequest() throws Exception {
        var s=new Scenario();s.commitLoseAckThenMove();var changed=(com.fasterxml.jackson.databind.node.ObjectNode)s.f.body(PAYLOAD);changed.put("detailVersion",s.f.version);changed.remove("snItems");
        var error=assertThrows(Exception.class,()->s.mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content(changed.toString())));
        assertTrue(error.getMessage().contains("内容"));assertEquals(2,s.physicalPosts.get());verify(s.receive,times(1)).finish(10L);
    }

}
