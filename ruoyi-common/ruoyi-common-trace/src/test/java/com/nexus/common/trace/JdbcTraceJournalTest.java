package com.nexus.common.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.jdbc.JdbcTraceJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JdbcTraceJournalTest {
 JdbcTemplate erp,mes;TransactionTemplate et,mt;JdbcTraceJournal source,target;
 @BeforeEach void setup() {
  var ed=new DriverManagerDataSource("jdbc:h2:mem:e"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var md=new DriverManagerDataSource("jdbc:h2:mem:m"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  erp=new JdbcTemplate(ed);mes=new JdbcTemplate(md);et=new TransactionTemplate(new DataSourceTransactionManager(ed));mt=new TransactionTemplate(new DataSourceTransactionManager(md));
  create(erp,"wm_trace_outbox");create(erp,"wm_trace_inbox");create(mes,"mes_trace_outbox");create(mes,"mes_trace_inbox");
  mes.execute("CREATE TABLE effects(id INT PRIMARY KEY,n INT)");mes.update("INSERT INTO effects VALUES(1,0)");
  source=new JdbcTraceJournal(erp,new ObjectMapper(),"ERP");target=new JdbcTraceJournal(mes,new ObjectMapper(),"MES");
 }
 void create(JdbcTemplate jdbc,String name) {
  jdbc.execute("CREATE TABLE "+name+"(event_id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(20),source_module VARCHAR(16),event_type VARCHAR(64),aggregate_type VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,schema_version INT,payload CLOB,content_hash CHAR(64),status VARCHAR(16),attempts INT,lease_owner VARCHAR(64),lease_until TIMESTAMP,next_attempt_at TIMESTAMP,last_error VARCHAR(512),result_payload CLOB,created_at TIMESTAMP,updated_at TIMESTAMP,applied_at TIMESTAMP,UNIQUE(tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version))");
 }
 MaterialTraceEvent event(String id,long version,String payload) {return new MaterialTraceEvent(id,"T1","ERP","STOCK_POSTED","SN",10L,version,1,payload,TraceHashes.sha256(payload));}
 @Test void appendRequiresActualWriteTransactionAndRollsBackWithSource() {
  assertThrows(IllegalStateException.class,()->source.append(event("a",1,"{}")));
  assertThrows(RuntimeException.class,()->et.executeWithoutResult(s->{source.append(event("a",1,"{}"));throw new RuntimeException("rollback");}));
  assertEquals(0,erp.queryForObject("SELECT COUNT(*) FROM wm_trace_outbox",Integer.class));
 }
 @Test void brokerAcceptanceIsNotApplicationSuccess() {
  et.executeWithoutResult(s->source.append(event("a",1,"{}")));
  var messages=et.execute(s->source.claim("owner",10,30));assertEquals(1,messages.size());
  et.executeWithoutResult(s->source.brokerAccepted("a","owner"));
  assertEquals("BROKER_SENT",erp.queryForObject("SELECT status FROM wm_trace_outbox",String.class));
 }
 @Test void effectsInboxAndReceiptCommitTogetherAndReplaysDoNotRepeatEffects() {
  var message=event("a",1,"{}");et.executeWithoutResult(s->source.append(message));
  for(int i=0;i<2;i++) mt.executeWithoutResult(s->assertTrue(target.accept(message,()->{mes.update("UPDATE effects SET n=n+1");return "RESULT";}).applied()));
  assertEquals(1,mes.queryForObject("SELECT n FROM effects",Integer.class));
  var receipt=mt.execute(s->target.claim("relay",10,30)).get(0);
  et.executeWithoutResult(s->assertTrue(source.accept(receipt,()->{throw new AssertionError("receipt is not business");}).applied()));
  assertEquals("APPLIED",erp.queryForObject("SELECT status FROM wm_trace_outbox WHERE event_id='a'",String.class));
 }
 @Test void targetFailureRollsBackBusinessAndInboxAndReceipt() {
  var message=event("a",1,"{}");
  assertThrows(RuntimeException.class,()->mt.executeWithoutResult(s->target.accept(message,()->{mes.update("UPDATE effects SET n=n+1");throw new RuntimeException("business failed");})));
  assertEquals(0,mes.queryForObject("SELECT n FROM effects",Integer.class));
  assertEquals(0,mes.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox",Integer.class));
  assertEquals(0,mes.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox",Integer.class));
 }
 @Test void laterVersionWaitsWithoutInventingPreviousEffects() {
  var result=mt.execute(s->target.accept(event("later",2,"{}"),()->{throw new AssertionError("gap");}));
  assertEquals("WAITING",result.status());
  mt.executeWithoutResult(s->target.accept(event("first",1,"{}"),()->"FIRST"));
  assertTrue(mt.execute(s->target.accept(event("later",2,"{}"),()->"SECOND")).applied());
 }
 @Test void sameEventCannotChangeHeaderOrPayload() {
  var first=event("same",1,"{}");et.executeWithoutResult(s->source.append(first));
  assertThrows(RuntimeException.class,()->et.executeWithoutResult(s->source.append(event("same",1,"{x}"))));
  var changed=new MaterialTraceEvent("same","T1","ERP","OTHER","SN",10L,1,1,"{}",TraceHashes.sha256("{}"));
  assertThrows(RuntimeException.class,()->et.executeWithoutResult(s->source.append(changed)));
 }
 @Test void leasePreventsOtherClaimantAndLateOwnerCannotOverwrite() {
  et.executeWithoutResult(s->source.append(event("a",1,"{}")));
  assertEquals(1,et.execute(s->source.claim("one",10,30)).size());
  assertTrue(et.execute(s->source.claim("two",10,30)).isEmpty());
  et.executeWithoutResult(s->source.brokerAccepted("a","two"));
  assertEquals("one",erp.queryForObject("SELECT lease_owner FROM wm_trace_outbox",String.class));
 }
 @Test void acknowledgementProjectionAndSourceStatusCommitAtomically() {
  erp.execute("CREATE TABLE handoff(id INT PRIMARY KEY,n INT)");erp.update("INSERT INTO handoff VALUES(1,0)");
  var original=event("project",1,"{}");et.executeWithoutResult(s->source.append(original));
  mt.executeWithoutResult(s->target.accept(original,()->"{\"receiptId\":\"42\"}"));
  var ack=mt.execute(s->target.claim("relay",10,30)).get(0);
  assertThrows(RuntimeException.class,()->et.executeWithoutResult(s->source.accept(ack,()->"unused",()->{
   erp.update("UPDATE handoff SET n=n+1");throw new IllegalStateException("projection failed");
  })));
  assertEquals("NEW",erp.queryForObject("SELECT status FROM wm_trace_outbox WHERE event_id='project'",String.class));
  assertEquals(0,erp.queryForObject("SELECT n FROM handoff",Integer.class));
  for(int i=0;i<2;i++) et.executeWithoutResult(s->source.accept(ack,()->"unused",()->erp.update("UPDATE handoff SET n=n+1")));
  assertEquals("APPLIED",erp.queryForObject("SELECT status FROM wm_trace_outbox WHERE event_id='project'",String.class));
  assertEquals(1,erp.queryForObject("SELECT n FROM handoff",Integer.class));
 }
 @Test void validPayloadHashDoesNotAuthorizeAnEventWithoutItsCommittedSourceRecord() {
  var event=event("forged",1,"{}");
  assertThrows(IllegalStateException.class,()->com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(erp,"ERP",event));
  et.executeWithoutResult(s->source.append(event));
  assertDoesNotThrow(()->com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(erp,"ERP",event));
  var changed=new MaterialTraceEvent("forged","T1","ERP","DIFFERENT","SN",10L,1,1,"{}",TraceHashes.sha256("{}"));
  assertThrows(IllegalStateException.class,()->com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(erp,"ERP",changed));
 }

 /** 使用两条真实 MES owner outbox 签发 proof，不用 boolean 或未验证裸事件预占。 */
 CompletionCancellationProof cancellationProof() throws Exception {
  var json=new ObjectMapper();var originalPayload=new java.util.LinkedHashMap<String,Object>();
  originalPayload.put("manifestId","10");originalPayload.put("revision",1);originalPayload.put("manifestHash","a".repeat(64));
  originalPayload.put("erpWoId","100");originalPayload.put("mesWoId","200");originalPayload.put("materialId","300");
  String payload=json.writeValueAsString(originalPayload);
  var original=new MaterialTraceEvent("C_ORIGINAL","T1","MES","COMPLETION_MANIFEST_FROZEN","COMPLETION_MANIFEST",10L,1,1,payload,TraceHashes.sha256(payload));
  String request=json.writeValueAsString(new CompletionCancellationRequest(10L,1,"a".repeat(64),original));
  var cancel=new MaterialTraceEvent("C_CANCEL","T1","MES",CompletionCancellationRequest.EVENT_TYPE,CompletionCancellationRequest.AGGREGATE_TYPE,10L,1,1,request,TraceHashes.sha256(request));
  mt.executeWithoutResult(tx->{target.append(original);target.append(cancel);});
  var verifier=new com.nexus.common.trace.port.TraceSourceVerificationPort(){
   public String sourceModule(){return "MES";}public void verify(MaterialTraceEvent event){com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(mes,"MES",event);}
  };
  return CompletionCancellationProof.verify(cancel,json,verifier);
 }
 CompletionCancellationResult cancellationResult(CompletionCancellationProof proof,String outcome){
  var command=proof.request();return new CompletionCancellationResult(proof.cancellationEvent().eventId(),proof.cancellationEvent().eventId(),proof.originalEvent().eventId(),proof.originalEvent().contentHash(),command.manifestId(),command.revision(),command.manifestHash(),outcome,"SYNTHETIC_DECISION");
 }
 @Test void controlledPreemptionRequiresOwnerVerifiedProof() {
  assertThrows(IllegalArgumentException.class,()->et.execute(tx->source.preemptCompletion(null,state->{throw new AssertionError("unverified");})));
  assertEquals(0,erp.queryForObject("SELECT COUNT(*) FROM wm_trace_inbox",Integer.class));
 }
 @Test void cancellationTombstoneUsesOriginalReceiptPathAndDelayedOriginalCannotRun()throws Exception{
  var proof=cancellationProof();var result=et.execute(tx->source.preemptCompletion(proof,state->{assertEquals("WAITING",state.status());return cancellationResult(proof,"CANCELLED");}));
  assertEquals("CANCELLED",result.outcome());var original=proof.originalEvent();
  var applied=et.execute(tx->source.accept(original,()->{throw new AssertionError("cancelled completion must never execute");}));
  assertEquals("CANCELLED",new ObjectMapper().readTree(applied.resultPayload()).path("outcome").asText());
  assertEquals(original.eventId(),new ObjectMapper().readTree(applied.resultPayload()).path("sourceEventId").asText());
  assertEquals(1,erp.queryForObject("SELECT COUNT(*) FROM wm_trace_outbox WHERE event_type='TRACE_APPLIED'",Integer.class));
 }
 @Test void pendingConsumedDecisionDoesNotInventOriginalApplicationAck()throws Exception{
  var proof=cancellationProof();et.execute(tx->source.preemptCompletion(proof,state->cancellationResult(proof,"COMPENSATION_REQUIRED")));
  assertNull(source.readApplied(proof.originalEvent()));assertEquals("WAITING",erp.queryForObject("SELECT status FROM wm_trace_inbox",String.class));
  assertEquals(0,erp.queryForObject("SELECT COUNT(*) FROM wm_trace_outbox",Integer.class));
 }
 @Test void appliedCompletionCannotBeReplacedByACancellationDecision()throws Exception{
  var proof=cancellationProof();et.executeWithoutResult(tx->source.accept(proof.originalEvent(),()->"{\"outcome\":\"POSTED\"}"));
  assertThrows(IllegalStateException.class,()->et.execute(tx->source.preemptCompletion(proof,state->cancellationResult(proof,"CANCELLED"))));
  assertEquals("{\"outcome\":\"POSTED\"}",source.readApplied(proof.originalEvent()).resultPayload());
 }
 @Test void wrongCancellationResultCannotPreemptAnotherManifest()throws Exception{
  var proof=cancellationProof();var wrong=new CompletionCancellationResult("C_CANCEL","C_CANCEL","C_ORIGINAL",proof.originalEvent().contentHash(),99L,1,"a".repeat(64),"CANCELLED","WRONG_MANIFEST");
  assertThrows(IllegalArgumentException.class,()->et.execute(tx->source.preemptCompletion(proof,state->wrong)));
  assertEquals(0,erp.queryForObject("SELECT COUNT(*) FROM wm_trace_inbox",Integer.class));
 }
 @Test void readAppliedIsSideEffectFreeAndWaitingReturnsNull(){
  var first=event("LOOKUP",1,"{}");assertNull(target.readApplied(first));
  mt.executeWithoutResult(tx->target.accept(first,()->"ORIGINAL_RESULT"));
  int count=mes.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox",Integer.class);
  assertEquals("ORIGINAL_RESULT",target.readApplied(first).resultPayload());assertEquals(count,mes.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox",Integer.class));
  var waiting=new MaterialTraceEvent("WAITING","T1","ERP","STOCK_POSTED","ANOTHER_SN",11L,2,1,"{}",TraceHashes.sha256("{}"));
  mt.executeWithoutResult(tx->assertEquals("WAITING",target.accept(waiting,()->{throw new AssertionError("gap");}).status()));
  assertNull(target.readApplied(waiting));
 }
 @Test void readAppliedRejectsConflictingHeaderAndPayloadEvenForWaiting(){
  var original=event("CONFLICT_READ",1,"{}");mt.executeWithoutResult(tx->target.accept(original,()->"RESULT"));
  var wrong=new MaterialTraceEvent(original.eventId(),"T1","ERP","OTHER",original.aggregateType(),10L,1,1,"{}",TraceHashes.sha256("{}"));
  assertThrows(IllegalStateException.class,()->target.readApplied(wrong));
  mes.update("UPDATE mes_trace_inbox SET payload='tampered' WHERE event_id=?",original.eventId());
  assertThrows(IllegalStateException.class,()->target.readApplied(original));
 }
 @Test void corruptedAppliedInboxCannotBeReadOrReplayAnAcknowledgement() {
  var original=event("CORRUPT_INBOX",1,"{\"value\":1}");mt.executeWithoutResult(tx->target.accept(original,()->"IMMUTABLE_RESULT"));
  mes.update("UPDATE mes_trace_outbox SET status='APPLIED'");
  mes.update("UPDATE mes_trace_inbox SET payload=? WHERE event_id=?","{\"value\":2}",original.eventId());
  assertEquals(original.contentHash(),mes.queryForObject("SELECT content_hash FROM mes_trace_inbox WHERE event_id=?",String.class,original.eventId()));
  assertThrows(IllegalStateException.class,()->target.readApplied(original));
  var calls=new AtomicInteger();assertThrows(IllegalStateException.class,()->mt.execute(tx->target.accept(original,()->{calls.incrementAndGet();return "WRONG";})));
  assertEquals(0,calls.get());assertEquals("APPLIED",mes.queryForObject("SELECT status FROM mes_trace_outbox",String.class));
  assertEquals("IMMUTABLE_RESULT",mes.queryForObject("SELECT result_payload FROM mes_trace_inbox",String.class));
 }
 @Test void corruptedSourcePayloadCannotBeAppendedClaimedOrVerifiedAsTheOriginal() {
  var original=event("CORRUPT_SOURCE",1,"{\"value\":1}");et.executeWithoutResult(tx->source.append(original));
  erp.update("UPDATE wm_trace_outbox SET payload=? WHERE event_id=?","{\"value\":2}",original.eventId());
  assertEquals(original.contentHash(),erp.queryForObject("SELECT content_hash FROM wm_trace_outbox WHERE event_id=?",String.class,original.eventId()));
  assertThrows(IllegalStateException.class,()->et.executeWithoutResult(tx->source.append(original)));
  assertThrows(IllegalArgumentException.class,()->et.execute(tx->source.claim("SYNTH_OWNER",1,10)));
  assertThrows(IllegalStateException.class,()->com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(erp,"ERP",original));
  assertEquals("NEW",erp.queryForObject("SELECT status FROM wm_trace_outbox",String.class));
  assertNull(erp.queryForObject("SELECT lease_owner FROM wm_trace_outbox",String.class));
 }
 @Test void corruptedSourcePayloadCannotBeAcknowledgedEvenWhenStoredHashMatchesTheAck() {
  var original=event("CORRUPT_ACK_SOURCE",1,"{\"value\":1}");et.executeWithoutResult(tx->source.append(original));
  erp.update("UPDATE wm_trace_outbox SET payload=? WHERE event_id=?","{\"value\":2}",original.eventId());
  assertThrows(IllegalStateException.class,()->et.executeWithoutResult(tx->source.acknowledge("T1",original.eventId(),original.contentHash(),"RESULT")));
  assertEquals("NEW",erp.queryForObject("SELECT status FROM wm_trace_outbox",String.class));
  assertNull(erp.queryForObject("SELECT result_payload FROM wm_trace_outbox",String.class));
 }

}
