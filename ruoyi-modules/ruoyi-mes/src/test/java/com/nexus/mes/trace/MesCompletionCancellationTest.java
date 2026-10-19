package com.nexus.mes.trace;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MesCompletionCancellationTest {
    MesTraceBusinessTest f;CompletionManifest manifest;MaterialTraceEvent request;String originalEventId;
    @BeforeEach void setup(){f=new MesTraceBusinessTest();f.setup();f.productMapping();f.sn(1,"COMPLETED");manifest=f.inTx(()->f.manifests.freeze(f.wo(1,0)));originalEventId=f.jdbc.queryForObject("SELECT event_id FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",String.class);}
    @AfterEach void cleanup(){f.cleanup();}
    MesCompletionCancellationService cancellations(){return new MesCompletionCancellationService(f.jdbc,f.context,f.events);}
    MaterialTraceEvent event(String id){var r=f.jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_id=?",id);return new MaterialTraceEvent(id,"000001","MES",MesTraceSql.text(r.get("event_type")),MesTraceSql.text(r.get("aggregate_type")),MesTraceSql.id(r.get("aggregate_id")),MesTraceSql.id(r.get("aggregate_version")),1,MesTraceSql.text(r.get("payload")),MesTraceSql.text(r.get("content_hash")));}
    void pending(){request=event(f.inTx(()->cancellations().request("cancel-1",manifest.manifestId(),1,"correct source evidence",1L)));}
    Map<String,Object> result(String outcome){var p=new LinkedHashMap<String,Object>();p.put("sourceEventId",request.eventId());p.put("cancellationEventId",request.eventId());p.put("originalEventId",originalEventId);p.put("originalEventHash",event(originalEventId).contentHash());p.put("manifestId",manifest.manifestId());p.put("revision",1);p.put("manifestHash",manifest.contentHash());p.put("outcome",outcome);p.put("reason","UNPOSTED_CANCELLED");return p;}
    MaterialTraceEvent ack(MaterialTraceEvent origin,Map<String,Object> result){String body=f.events.encode(Map.of("eventId",origin.eventId(),"contentHash",origin.contentHash(),"resultPayload",f.events.encode(result)));return new MaterialTraceEvent(TraceHashes.fingerprint("ack",origin.eventId()),"000001","ERP","TRACE_APPLIED","ACK_"+origin.aggregateType(),origin.aggregateId(),origin.aggregateVersion(),1,body,TraceHashes.sha256(body));}
    String status(){return f.jdbc.queryForObject("SELECT status FROM pro_completion_manifest WHERE manifest_id=?",String.class,manifest.manifestId());}
    @Test void unpostedCancellationPreservesFrozenFactsAndStopsTheOriginalPublication(){
        pending();var result=result("CANCELLED");var ack=ack(request,result);f.inTx(()->f.deliver(ack));
        assertEquals("CANCELLED",status());assertEquals("COMPLETED",f.jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals("NONE",f.jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
        assertEquals("CANCELLED",f.jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,originalEventId));
        assertEquals("APPLIED",f.jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,request.eventId()));
        assertEquals(f.events.encode(result),f.jdbc.queryForObject("SELECT result_payload FROM mes_trace_outbox WHERE event_id=?",String.class,request.eventId()));
        assertEquals(1,f.manifests.lines("000001",manifest.manifestId(),1,null,500).rows().size());
        assertFalse(f.inTx(()->f.deliver(ack)));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest_line",Integer.class));
    }
    @Test void cancellationHashMismatchRollsBackGenericAckAndBusinessProjection(){
        pending();var invalid=result("CANCELLED");invalid.put("originalEventHash","0".repeat(64));
        assertThrows(RuntimeException.class,()->f.inTx(()->f.deliver(ack(request,invalid))));
        assertEquals("CANCEL_REQUESTED",status());assertEquals("NEW",f.jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,request.eventId()));
    }
    @Test void rejectedCancellationRetainsOriginalDeliveryAndDoesNotReleaseIdentities(){
        pending();f.inTx(()->f.deliver(ack(request,result("COMPENSATION_REQUIRED"))));
        assertEquals("FROZEN",status());assertEquals("FROZEN",f.jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
    }
    @Test void originalReceiptCanArriveDuringCancellationWithoutOpeningRevisionGate(){
        pending();var origin=f.jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_id=?",originalEventId);
        var result=new LinkedHashMap<String,Object>();result.put("sourceEventId",originalEventId);result.put("manifestId",manifest.manifestId());result.put("revision",1);result.put("manifestHash",manifest.contentHash());result.put("outcome","POSTED");result.put("receiptId",91);result.put("postingGroupId",92);
        var event=new MaterialTraceEvent(originalEventId,"000001","MES","COMPLETION_MANIFEST_FROZEN","COMPLETION_MANIFEST",manifest.manifestId(),1,1,MesTraceSql.text(origin.get("payload")),MesTraceSql.text(origin.get("content_hash")));
        f.inTx(()->f.deliver(ack(event,result)));
        assertEquals("CANCEL_REQUESTED",status());assertEquals("RECEIVED",f.jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
        assertThrows(RuntimeException.class,()->f.inTx(()->f.deliver(ack(request,result("CANCELLED")))));
        f.inTx(()->f.deliver(ack(request,result("COMPENSATION_REQUIRED"))));assertEquals("APPLIED",status());assertEquals("RECEIVED",f.jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
    }

    @Test void requestIsAtomicDurableAndCannotPublishTwoConcurrentPendingCommands(){
        pending();assertEquals(request.eventId(),f.inTx(()->cancellations().request("cancel-1",manifest.manifestId(),1,"correct source evidence",1L)));
        assertThrows(RuntimeException.class,()->f.inTx(()->cancellations().request("cancel-2",manifest.manifestId(),1,"other reason",1L)));
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("premature",10L,1,"correct source",1L)));
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_CANCEL_REQUESTED'",Integer.class));
    }
    @Test void failedCancelRequestRollsBackStateCommandAndOutbox(){
        assertThrows(IllegalStateException.class,()->f.inTx(()->{cancellations().request("cancel-1",manifest.manifestId(),1,"correct source evidence",1L);throw new IllegalStateException("failed source transaction");}));
        assertEquals("FROZEN",status());assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_CANCEL_REQUESTED'",Integer.class));pending();
    }
    @Test void nextRevisionRequiresVerifiedCancellationAndPreservesOldFactsAndAggregation(){
        pending();var oldRows=f.manifests.lines("000001",manifest.manifestId(),1,null,500).rows();
        f.inTx(()->f.deliver(ack(request,result("CANCELLED"))));
        var revision=f.inTx(()->f.manifests.refreeze("revision-2",10L,1,"same output with corrected evidence",1L));
        assertEquals(2,revision.revision());assertNotEquals(manifest.manifestId(),revision.manifestId());assertEquals("CANCELLED",status());
        assertEquals(oldRows,f.manifests.lines("000001",manifest.manifestId(),1,null,500).rows());assertEquals(manifest.contentHash(),revision.contentHash());
        assertEquals(1,f.jdbc.queryForObject("SELECT aggregated_flag FROM pro_flow_card WHERE card_id=11",Integer.class));
        assertEquals(0,java.math.BigDecimal.ONE.compareTo(f.jdbc.queryForObject("SELECT completed_qty FROM pro_work_order WHERE mes_wo_id=10",java.math.BigDecimal.class)));
        var replay=f.inTx(()->f.manifests.refreeze("revision-2",10L,1,"same output with corrected evidence",1L));assertEquals(revision.manifestId(),replay.manifestId());assertEquals(revision.contentHash(),replay.contentHash());assertEquals(0,revision.goodQty().compareTo(replay.goodQty()));
        var publication=f.jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN' AND aggregate_id=?",revision.manifestId());
        assertEquals(1L,MesTraceSql.id(publication.get("aggregate_version")));assertEquals(2,f.events.payload(publication).path("revision").asInt());assertEquals(request.eventId(),f.events.payload(publication).path("cancellationEventId").asText());
        assertThrows(RuntimeException.class,()->f.inTx(()->cancellations().request("cancel-stale",manifest.manifestId(),1,"stale revision",1L)));
    }
    @Test void aManuallyChangedHeaderCannotCreateAnUnacknowledgedRevision(){
        f.jdbc.update("UPDATE pro_completion_manifest SET status='CANCELLED'");
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("no-proof",10L,1,"must verify",1L)));
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.freeze(f.wo(1,0))));
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest",Integer.class));
    }
    @Test void rejectedRequestCanStartAContinuousSecondPublication(){
        pending();f.inTx(()->f.deliver(ack(request,result("COMPENSATION_REQUIRED"))));
        var second=event(f.inTx(()->cancellations().request("cancel-2",manifest.manifestId(),1,"evidence corrected",1L)));
        assertEquals(2,second.aggregateVersion());assertEquals(request.aggregateId(),second.aggregateId());
    }
    @Test void shipmentBlocksCancellationAndDoesNotReleaseManufacturingState(){
        f.jdbc.update("UPDATE pro_sn SET sn_status='SHIPPED',handoff_status='SHIPPED' WHERE sn_id=1");
        assertThrows(RuntimeException.class,()->f.inTx(()->cancellations().request("shipped",manifest.manifestId(),1,"not allowed",1L)));assertEquals("FROZEN",status());
    }

    @Test void aCancelledUnreceivedOutputStillBlocksTrackingPolicyChanges(){
        pending();f.inTx(()->f.deliver(ack(request,result("CANCELLED"))));
        assertTrue(new MesTrackingUsageService(f.jdbc,f.context).hasUnsettledProduction("000001",100L));
    }

    @Test void lateOriginalCancellationAckOnlyConfirmsHistoryAndCannotTouchTheNewRevision(){
        pending();f.inTx(()->f.deliver(ack(request,result("CANCELLED"))));var next=f.inTx(()->f.manifests.refreeze("new-after-cancel",10L,1,"corrected evidence",1L));
        var receipt=result("CANCELLED");receipt.put("sourceEventId",originalEventId);
        var late=ack(event(originalEventId),receipt);f.inTx(()->f.deliver(late));
        assertEquals("CANCELLED",status());assertEquals("FROZEN",f.jdbc.queryForObject("SELECT status FROM pro_completion_manifest WHERE manifest_id=?",String.class,next.manifestId()));assertEquals("FROZEN",f.jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
        assertFalse(f.inTx(()->f.deliver(late)));
    }

    MaterialTraceEvent typed(String id,Map<String,Object> result){String body=f.events.encode(result);return new MaterialTraceEvent(id,"000001","ERP",CompletionCancellationResult.EVENT_TYPE,CompletionCancellationResult.AGGREGATE_TYPE,manifest.manifestId(),1,1,body,TraceHashes.sha256(body));}
    @Test void typedResultBeforeAckIsDurableButCannotSkipTheRequestAcknowledgementGate(){
        pending();var result=result("CANCELLED");var typed=typed("typed-cancel",result);f.inTx(()->f.deliver(typed));assertEquals("CANCELLED",status());
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("too-early",10L,1,"must wait for ACK",1L)));
        f.inTx(()->f.deliver(ack(request,result)));var next=f.inTx(()->f.manifests.refreeze("after-ACK",10L,1,"confirmed",1L));
        assertFalse(f.inTx(()->f.deliver(typed)));assertEquals("FROZEN",f.jdbc.queryForObject("SELECT status FROM pro_completion_manifest WHERE manifest_id=?",String.class,next.manifestId()));
    }
    @Test void oldCompensationAckCannotRestoreAnAlreadyPendingSecondCancellation(){
        pending();var first=request;var compensation=result("COMPENSATION_REQUIRED");f.inTx(()->f.deliver(typed("typed-compensation",compensation)));
        var second=event(f.inTx(()->cancellations().request("cancel-2",manifest.manifestId(),1,"after source investigation",1L)));request=second;
        f.inTx(()->f.deliver(ack(first,compensation)));assertEquals("CANCEL_REQUESTED",status());
        f.inTx(()->f.deliver(ack(second,result("CANCELLED"))));assertEquals("CANCELLED",status());
    }
    @Test void cancellationPayloadIsExactlyTheCommonDtoAndRetainsOwnerOutboxHeaders(){
        pending();var verifier=org.mockito.Mockito.mock(com.nexus.common.trace.port.TraceSourceVerificationPort.class);org.mockito.Mockito.when(verifier.sourceModule()).thenReturn("MES");
        org.mockito.Mockito.doAnswer(call->{MaterialTraceEvent supplied=call.getArgument(0);assertEquals(event(supplied.eventId()),supplied);return null;}).when(verifier).verify(org.mockito.ArgumentMatchers.any());
        var proof=CompletionCancellationProof.verify(request,f.json,verifier);assertEquals(event(originalEventId),proof.originalEvent());assertEquals(1,proof.request().revision());assertEquals(manifest.contentHash(),proof.request().manifestHash());
        var command=f.jdbc.queryForMap("SELECT * FROM mes_trace_inbox WHERE source_module='MES' AND event_type='COMPLETION_MANIFEST_CANCEL'");var audit=f.events.payload(command);assertEquals("correct source evidence",audit.path(2).asText());assertEquals(1,audit.path(3).asLong());
    }

    @Test void refreezeUsesTheActualExistingNcrAndDerivedReworkCompletionGates(){
        pending();f.inTx(()->f.deliver(ack(request,result("CANCELLED"))));
        f.jdbc.update("INSERT INTO ncr_order VALUES(1,'000001',10,11,'IN_REWORK','REWORK','0')");f.jdbc.update("INSERT INTO ncr_rework_order VALUES(1,'000001',1,10,12,'IN_PROGRESS','0')");f.jdbc.update("INSERT INTO pro_work_order VALUES(12,'000001',NULL,'RW12',100,'IN_PROGRESS',1,0,0,'0')");
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("open-ncr",10L,1,"must finish actual rework",1L)));
        f.jdbc.update("UPDATE ncr_order SET status='CLOSED' WHERE ncr_id=1");
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("unfinished-rework",10L,1,"must finish actual rework",1L)));
        f.jdbc.update("UPDATE ncr_rework_order SET status='COMPLETED' WHERE rework_id=1");f.jdbc.update("UPDATE pro_work_order SET wo_status='COMPLETED',completed_qty=1 WHERE mes_wo_id=12");
        assertEquals(2,f.inTx(()->f.manifests.refreeze("completed-rework",10L,1,"actual rework completed",1L)).revision());assertEquals("COMPLETED",f.jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));
    }
    @Test void refreezeRechecksRealIpqcAndLatestGoodSnTestEvidence(){
        pending();f.inTx(()->f.deliver(ack(request,result("CANCELLED"))));f.jdbc.update("INSERT INTO pro_work_order_step VALUES(1,'000001',10,30,1,'0')");
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("missing-ipqc",10L,1,"must retain IPQC",1L)));
        f.jdbc.update("INSERT INTO qc_ipqc VALUES(1,'000001',10,30,'QUALIFIED','0')");f.jdbc.update("INSERT INTO mes_test_record VALUES(1,'000001',1,'ERP-1',CURRENT_TIMESTAMP,'FAIL','0')");
        assertThrows(RuntimeException.class,()->f.inTx(()->f.manifests.refreeze("failed-test",10L,1,"must complete real quality flow",1L)));
        f.jdbc.update("UPDATE mes_test_record SET result='PASS' WHERE test_record_id=1");assertEquals(2,f.inTx(()->f.manifests.refreeze("qualified",10L,1,"actual quality verified",1L)).revision());
    }
}
