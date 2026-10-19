package com.nexus.mes.trace;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.pro.domain.*;
import com.nexus.mes.pro.domain.vo.*;
import com.nexus.mes.pro.mapper.*;
import com.nexus.mes.pro.service.impl.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MES lots/journals read through the existing detail service methods and serialized PC contract. */
class MesProductionDetailFactsTest {
    MesBatchOnlyOutputTest f;ProFlowCardServiceImpl flow;ProWorkOrderServiceImpl work;
    MesProductionDetailFactsService details;java.util.concurrent.atomic.AtomicBoolean unknownPolicy;java.util.concurrent.atomic.AtomicInteger policyReads;
    @BeforeEach void setup(){
        f=new MesBatchOnlyOutputTest();f.setup();
        for(Class<?> type:List.of(ProFlowCardStep.class,ProWorkOrderStep.class,ProWorkOrderBomLine.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),type);
        flow=mock(ProFlowCardServiceImpl.class,CALLS_REAL_METHODS);work=mock(ProWorkOrderServiceImpl.class,CALLS_REAL_METHODS);
        var cards=mock(ProFlowCardMapper.class);var steps=mock(ProFlowCardStepMapper.class);
        ReflectionTestUtils.setField(flow,"baseMapper",cards);ReflectionTestUtils.setField(flow,"stepMapper",steps);
        var card=new ProFlowCardVo();card.setCardId(11L);card.setMesWoId(10L);card.setMaterialId(100L);card.setCardStatus("IN_PROGRESS");
        var step=new ProFlowCardStepVo();step.setStepRecordId(31L);step.setCardId(11L);step.setMesWoId(10L);step.setSequenceNum(1);step.setStepStatus("IN_PROGRESS");
        when(cards.selectVoById(11L)).thenReturn(card);when(steps.selectVoList(any())).thenReturn(List.of(step));
        var orders=mock(ProWorkOrderMapper.class);var orderSteps=mock(ProWorkOrderStepMapper.class);var bom=mock(ProWorkOrderBomLineMapper.class);
        ReflectionTestUtils.setField(work,"baseMapper",orders);ReflectionTestUtils.setField(work,"stepMapper",orderSteps);ReflectionTestUtils.setField(work,"woBomLineMapper",bom);
        var order=new ProWorkOrderVo();order.setMesWoId(10L);order.setErpWoId(20L);order.setMaterialId(100L);
        when(orders.selectVoById(10L)).thenReturn(order);when(orderSteps.selectVoList(any())).thenReturn(List.of());when(bom.selectVoList(any())).thenReturn(List.of());
        unknownPolicy=new java.util.concurrent.atomic.AtomicBoolean();policyReads=new java.util.concurrent.atomic.AtomicInteger();var frozen=f.f.mixed;
        com.nexus.common.trace.port.WorkOrderTrackingPolicyPort bank=(tenant,woId,material)->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());policyReads.incrementAndGet();
            if(unknownPolicy.get())return com.nexus.common.trace.WorkOrderTrackingPolicySnapshot.unknown(tenant,woId,material,"legacy policy unknown");
            var time=java.time.Instant.parse("2026-10-18T00:00:00Z");return new com.nexus.common.trace.WorkOrderTrackingPolicySnapshot(tenant,woId,material,
                com.nexus.common.trace.WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,frozen,1,"a".repeat(64),"b".repeat(64),"c".repeat(64),"ERP_RELEASE:20",time,time,null,null);
        };
        var reader=proxy(new com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader(f.f.jdbc,f.f.context,bank));
        var target=new MesProductionDetailFactsService(f.f.jdbc,f.f.json,f.f.context,reader);ReflectionTestUtils.setField(target,"traceRelayEnabled",true);details=proxy(target);
        ReflectionTestUtils.setField(flow,"detailFacts",details);ReflectionTestUtils.setField(work,"detailFacts",details);
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target){
        var factory=new org.springframework.aop.framework.ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvisor(new com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor(new com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor(true,new com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor()),com.baomidou.dynamic.datasource.annotation.DS.class));
        var tx=new org.springframework.transaction.interceptor.TransactionInterceptor();tx.setTransactionManager(f.f.tx.getTransactionManager());tx.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());factory.addAdvice(tx);return (T)factory.getProxy();
    }
    @AfterEach void cleanup(){f.cleanup();}
    @Test void existingFlowCardDetailProvidesActualProductLotAndOriginalAllocationChoices(){
        long good=f.quota(7,8,"IN_STOCK","PASS"),scrap=f.quota(12,2,"SCRAPPED","FAIL");
        var response=f.f.json.valueToTree(flow.selectById(11L));var context=response.path("steps").get(0).path("productOutputContext");
        assertTrue(context.isObject(),"PC requires the actual step productOutputContext");
        assertEquals("11",context.path("cardId").asText());assertEquals("31",context.path("stepRecordId").asText());assertTrue(context.path("isFinalStep").asBoolean());
        assertEquals(2,context.path("sources").size());var first=context.path("sources").get(0);
        assertEquals("7",first.path("lotId").asText());assertEquals(Long.toString(good),first.path("sourceAllocationId").asText());assertEquals("8",first.path("quantity").asText());assertTrue(first.path("quantity").isTextual());
        assertEquals(Long.toString(scrap),context.path("sources").get(1).path("sourceAllocationId").asText());assertFalse(context.path("hasMore").asBoolean());
    }
    @Test void existingWorkOrderDetailProvidesTheRealLatestFrozenManifest(){
        long allocation=f.quota(7,10,"IN_STOCK","PASS");f.output(101,7,allocation,10,"GOOD");var manifest=f.freeze(10,0);
        var response=f.f.json.valueToTree(work.selectById(10L));var state=response.path("completionState");
        assertTrue(state.isObject(),"PC requires authoritative completionState on the existing WO detail");
        assertEquals(manifest.manifestId().toString(),state.path("latest").path("manifestId").asText());assertEquals("FROZEN",state.path("latest").path("status").asText());
        assertEquals(manifest.contentHash(),state.path("latest").path("contentHash").asText());assertEquals(1,state.path("revisions").size());
    }
    @Test void absentManifestIsExplicitlyEmptyRatherThanUnknown(){
        var state=f.f.json.valueToTree(work.selectById(10L)).path("completionState");
        assertTrue(state.isObject());assertTrue(state.path("latest").isNull());assertEquals(0,state.path("revisions").size());assertFalse(state.path("truncated").asBoolean());
    }
    @Test void recordedOutputsAreActualStepFactsAndAreExcludedFromNewChoices(){
        long good=f.quota(7,8,"IN_STOCK","PASS");f.quota(12,2,"SCRAPPED","FAIL");f.output(101,7,good,8,"GOOD");
        var context=f.f.json.valueToTree(flow.selectById(11L)).path("steps").get(0).path("productOutputContext");
        assertEquals(1,context.path("outputs").size());assertEquals("8",context.path("outputs").get(0).path("quantity").asText());
        assertEquals("actual-output-7",context.path("outputs").get(0).path("commandId").asText());assertEquals("GOOD",context.path("outputs").get(0).path("result").asText());
        assertEquals(1,context.path("sources").size());assertEquals("12",context.path("sources").get(0).path("lotId").asText());
    }
    @Test void missingVerifiedSourceClearsThePreviouslyReadContext(){
        f.quota(7,10,"IN_STOCK","PASS");assertNotNull(flow.selectById(11L).getSteps().get(0).getProductOutputContext());
        f.f.jdbc.update("UPDATE mes_trace_inbox SET result_payload=NULL WHERE event_id='output-quota'");
        assertNull(flow.selectById(11L).getSteps().get(0).getProductOutputContext());
        assertEquals(0,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM pro_product_lot_output",Integer.class));
    }
    @Test void anotherTenantsLotCannotBecomeAProductChoice(){
        f.quota(7,10,"IN_STOCK","PASS");f.f.jdbc.update("UPDATE lt_lot SET tenant_id='000002' WHERE lot_id=7");
        assertNull(flow.selectById(11L).getSteps().get(0).getProductOutputContext());
    }
    @Test void unknownReleasePolicyNeverFallsBackToCurrentMasterForThePcContext(){
        f.quota(7,10,"IN_STOCK","PASS");clearInvocations(f.f.policies);unknownPolicy.set(true);
        assertNull(flow.selectById(11L).getSteps().get(0).getProductOutputContext());verifyNoInteractions(f.f.policies);assertEquals(1,policyReads.get());
    }
    @Test void contextUsesOriginalWorkOrderPolicyWhenCurrentMasterDrifts(){
        f.quota(7,10,"IN_STOCK","PASS");when(f.f.policies.policy("000001",100L)).thenReturn(new com.nexus.common.trace.TrackingPolicy(false,true,false,99L,com.nexus.common.trace.TrackingStage.STRICT));clearInvocations(f.f.policies);
        var context=flow.selectById(11L).getSteps().get(0).getProductOutputContext();assertNotNull(context);
        var policy=(com.nexus.common.trace.TrackingPolicy)context.get("policy");assertTrue(policy.batchManaged());assertFalse(policy.serialManaged());assertEquals(1L,policy.version());verifyNoInteractions(f.f.policies);
    }
    @Test void actualLastExecutableStepAndDecidedScrapAreReadFromMes(){
        f.quota(7,8,"IN_STOCK","PASS");f.quota(12,2,"SCRAPPED","FAIL");
        f.f.jdbc.update("INSERT INTO pro_flow_card_step(step_record_id,tenant_id,card_id,mes_wo_id,sequence_num,step_status,planned_qty,good_qty,scrap_qty,del_flag) VALUES(30,'000001',11,10,0,'IN_PROGRESS',10,0,0,'0'),(32,'000001',11,10,2,'SKIPPED',10,0,0,'0')");
        var facts=details.productOutputs(11L,10L,100L);assertEquals(false,facts.get(30L).get("isFinalStep"));assertEquals(true,facts.get(31L).get("isFinalStep"));assertFalse(facts.containsKey(32L));
        var early=f.f.json.valueToTree(facts.get(30L));assertEquals(1,early.path("sources").size());assertEquals("SCRAPPED",early.path("sources").get(0).path("status").asText());
    }
    @Test void readInsideAHeldMesWriteDoesNotReenterTheForeignPolicyReader(){
        f.quota(7,10,"IN_STOCK","PASS");f.f.inTx(()->{assertNull(flow.selectById(11L).getSteps().get(0).getProductOutputContext());return null;});assertEquals(0,policyReads.get());
    }
    @Test void cancellationStateRequiresTheDurableAckBeforePcMayRefreeze(){
        var manifest=freeze();var request=cancel(manifest);var pending=work.selectById(10L).getCompletionState();
        assertEquals("CANCEL_REQUESTED",((Map<?,?>)pending.get("latest")).get("status"));assertEquals("CANCEL_REQUESTED",((Map<?,?>)pending.get("cancellation")).get("status"));
        var result=cancellationResult(manifest,request);String body=f.f.events.encode(result);
        var typed=new com.nexus.common.trace.MaterialTraceEvent("pc-cancel-result","000001","ERP",com.nexus.common.trace.CompletionCancellationResult.EVENT_TYPE,
            com.nexus.common.trace.CompletionCancellationResult.AGGREGATE_TYPE,manifest.manifestId(),request.aggregateVersion(),1,body,com.nexus.common.trace.TraceHashes.sha256(body));
        f.f.inTx(()->f.f.deliver(typed));assertEquals("CANCELLED",f.f.jdbc.queryForObject("SELECT status FROM pro_completion_manifest WHERE manifest_id=?",String.class,manifest.manifestId()));
        assertNull(work.selectById(10L).getCompletionState(),"Typed cancellation alone must keep PC correction/refreeze disabled");
        f.f.inTx(()->f.f.deliver(ack(request,result)));var confirmed=work.selectById(10L).getCompletionState();assertNotNull(confirmed);
        assertEquals("CANCELLED",((Map<?,?>)confirmed.get("latest")).get("status"));assertEquals("CANCELLED",((Map<?,?>)confirmed.get("cancellation")).get("status"));
    }
    @Test void latestRevisionComesFromTheActualFrozenHeaderAndKeepsCancelledHistory(){
        var old=freeze();var request=cancel(old);f.f.inTx(()->f.f.deliver(ack(request,cancellationResult(old,request))));
        var next=f.f.inTx(()->f.f.manifests.refreeze("pc-next",10L,1,"audited current facts",1L));var state=f.f.json.valueToTree(work.selectById(10L)).path("completionState");
        assertEquals(next.manifestId().toString(),state.path("latest").path("manifestId").asText());assertEquals(2,state.path("latest").path("revision").asInt());
        assertEquals(2,state.path("revisions").size());assertEquals("CANCELLED",state.path("revisions").get(1).path("status").asText());assertTrue(state.path("cancellation").isNull());
    }
    @Test void manuallyAppliedManifestCannotBePresentedAsConfirmedWithoutOwnerAck(){
        freeze();f.f.jdbc.update("UPDATE pro_completion_manifest SET status='APPLIED'");assertNull(work.selectById(10L).getCompletionState());
    }
    @Test void all501EvidencedProductLotsRemainAvailableWithoutFalseTruncation(){
        f.f.jdbc.update("UPDATE pro_work_order SET planned_qty=600 WHERE mes_wo_id=10");f.f.jdbc.update("UPDATE pro_flow_card SET planned_qty=600 WHERE card_id=11");f.f.jdbc.update("UPDATE pro_flow_card_step SET planned_qty=600 WHERE step_record_id=31");
        var grant=f.f.erp("large-output-quota","ERP_PRODUCTION_LOT_AUTHORIZED",90,Map.of("sourceType","PRODUCTION_OUTPUT","sourceId",20L,"sourceLineId",1L,"mesWoId",10L,"erpWoId",20L,"erpBatchId",600L,"materialId",100L,"quantity",600));f.f.inTx(()->f.f.deliver(grant));
        for(int i=0;i<501;i++){long lot=1000L+i;f.f.lot(lot,100,1);f.f.allocate("large-map-"+i,"large-output-quota",lot,1);}
        var context=f.f.json.valueToTree(flow.selectById(11L)).path("steps").get(0).path("productOutputContext");assertTrue(context.isObject());assertEquals(501,context.path("sources").size());assertFalse(context.path("hasMore").asBoolean());assertTrue(context.path("nextCursor").isNull());
    }
    private com.nexus.common.trace.CompletionManifest freeze(){long allocation=f.quota(7,10,"IN_STOCK","PASS");f.output(101,7,allocation,10,"GOOD");return f.freeze(10,0);}
    private com.nexus.common.trace.MaterialTraceEvent event(String id){var row=f.f.jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_id=?",id);return new com.nexus.common.trace.MaterialTraceEvent(id,"000001","MES",MesTraceSql.text(row.get("event_type")),MesTraceSql.text(row.get("aggregate_type")),MesTraceSql.id(row.get("aggregate_id")),MesTraceSql.id(row.get("aggregate_version")),1,MesTraceSql.text(row.get("payload")),MesTraceSql.text(row.get("content_hash")));}
    private com.nexus.common.trace.MaterialTraceEvent cancel(com.nexus.common.trace.CompletionManifest manifest){return event(f.f.inTx(()->new MesCompletionCancellationService(f.f.jdbc,f.f.context,f.f.events).request("pc-cancel",manifest.manifestId(),manifest.revision(),"audited pc correction",1L)));}
    private com.nexus.common.trace.CompletionCancellationResult cancellationResult(com.nexus.common.trace.CompletionManifest manifest,com.nexus.common.trace.MaterialTraceEvent request){var original=f.f.events.read(request.payload()).path("originalEvent");return new com.nexus.common.trace.CompletionCancellationResult(request.eventId(),request.eventId(),original.path("eventId").asText(),original.path("contentHash").asText(),manifest.manifestId(),manifest.revision(),manifest.contentHash(),"CANCELLED","UNPOSTED_CANCELLED");}
    private com.nexus.common.trace.MaterialTraceEvent ack(com.nexus.common.trace.MaterialTraceEvent origin,Object result){String body=f.f.events.encode(Map.of("eventId",origin.eventId(),"contentHash",origin.contentHash(),"resultPayload",f.f.events.encode(result)));return new com.nexus.common.trace.MaterialTraceEvent(com.nexus.common.trace.TraceHashes.fingerprint("pc-ack",origin.eventId()),"000001","ERP","TRACE_APPLIED","ACK_PC",origin.aggregateId(),origin.aggregateVersion(),1,body,com.nexus.common.trace.TraceHashes.sha256(body));}

}
