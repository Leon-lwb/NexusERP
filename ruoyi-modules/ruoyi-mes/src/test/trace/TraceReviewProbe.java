package com.nexus.erp.wm.service;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.JdbcTraceJournal;
import com.nexus.common.trace.port.*;
import com.nexus.erp.wm.trace.*;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.web.service.MaterialIdentityTraceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
/** Diagnostic review reproductions, isolated from Maven test discovery. Assertions describe the reviewed defects. */
public class TraceReviewProbe {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static JdbcTemplate database(){return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa",""));}
 static void create(JdbcTemplate j,String name){j.execute("CREATE TABLE "+name+"(event_id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(20),source_module VARCHAR(16),event_type VARCHAR(64),aggregate_type VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,schema_version INT,payload CLOB,content_hash CHAR(64),status VARCHAR(16),attempts INT,lease_owner VARCHAR(64),lease_until TIMESTAMP,next_attempt_at TIMESTAMP,last_error VARCHAR(512),result_payload CLOB,created_at TIMESTAMP,updated_at TIMESTAMP,applied_at TIMESTAMP,UNIQUE(tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version))");}
 static MaterialTraceEvent event(String id,long version){return new MaterialTraceEvent(id,"000001","ERP","ERP_INVENTORY_POSTED","STOCK_POSTING",10L,version,1,"{}",TraceHashes.sha256("{}"));}
 static void successfulSendsCountTowardConflict(){
  var j=database();create(j,"wm_trace_outbox");create(j,"wm_trace_inbox");create(j,"mes_trace_outbox");create(j,"mes_trace_inbox");
  var tx=new TransactionTemplate(new DataSourceTransactionManager(j.getDataSource()));var source=new JdbcTraceJournal(j,new ObjectMapper(),"ERP");var target=new JdbcTraceJournal(j,new ObjectMapper(),"MES");
  var later=event("later",2);tx.executeWithoutResult(s->source.append(later));
  for(int i=1;i<=10;i++){
   j.update("UPDATE wm_trace_outbox SET next_attempt_at=NULL");
   check(tx.execute(s->source.claim("probe",10,30)).size()==1,"expected one publication");
   check(!tx.execute(s->target.accept(later,()->{throw new AssertionError("must remain waiting");})).applied(),"gap should wait");
   if(i<10)tx.executeWithoutResult(s->source.brokerAccepted(later.eventId(),"probe"));
   else tx.executeWithoutResult(s->source.retry(later.eventId(),"probe","ONE_TRANSIENT_FAILURE"));
  }
  check("CONFLICT".equals(j.queryForObject("SELECT status FROM wm_trace_outbox WHERE event_id='later'",String.class)),"expected conflict after nine successes and one failure");
  tx.executeWithoutResult(s->target.accept(event("earlier",1),()->"FIRST"));
  check(tx.execute(s->source.claim("probe",10,30)).isEmpty(),"later event should be permanently excluded");
  check("WAITING".equals(j.queryForObject("SELECT status FROM mes_trace_inbox WHERE event_id='later'",String.class)),"target should still be waiting");
  System.out.println("REPRO_A: 9 broker confirmations plus 1 transport failure quarantines WAITING v2; v1 arrival cannot resume it");
 }
 static void erpOnlyUsageAppearsComplete(){
  try(var tenant=mockStatic(TenantHelper.class);var auth=mockStatic(StpUtil.class)){
   tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn("000001");
   auth.when(()->StpUtil.hasPermission("wm:trace:query")).thenReturn(true);
   var erp=new IdentityTraceReadPort(){public String sourceModule(){return "ERP";}public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}public TracePage<Long> affectedByBatch(String t,Long id,long a,int l){return new TracePage<>(List.of(),null,false);}};
   var mes=new IdentityTraceReadPort(){public String sourceModule(){return "MES";}public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}public TracePage<Long> affectedByBatch(String t,Long id,long a,int l){return new TracePage<>(List.of(),null,false);}public TracePage<Long> affectedByIdentity(String t,Long id,long a,int l){return new TracePage<>(a==0?List.of(700L):List.of(),null,false);}};
   var service=new MaterialIdentityTraceService(List.of(erp,mes));var restricted=service.whereUsed(900L,0,500,10);
   check(((List<?>)restricted.get("rows")).isEmpty() && Boolean.FALSE.equals(restricted.get("truncated")),"restricted query should claim complete empty");
   check(!restricted.get("evidenceGaps").toString().contains("权限"),"expected missing permission evidence gap");
   auth.when(()->StpUtil.hasPermission("mes:pro:trace:query")).thenReturn(true);
   check(((List<?>)service.whereUsed(900L,0,500,10).get("rows")).size()==1,"same facts with MES permission should expose affected unit");
   System.out.println("REPRO_B: ERP-only where-used returns empty/truncated=false with no module-permission gap; adding MES scope reveals product 700");
  }
 }
 static void scrapFactBlocksAllScrapCompletion(){
  var j=database();create(j,"wm_trace_outbox");
  j.execute("CREATE TABLE wm_sn(tenant_id varchar(20),sn_id bigint,material_id bigint,batch_id bigint,sn_code varchar(64),status varchar(32),identity_version bigint,del_flag char(1))");
  j.execute("CREATE TABLE wm_material_stock(tenant_id varchar(20),sn_id bigint,stock_quantity decimal(18,4),del_flag char(1))");
  j.update("INSERT INTO wm_sn VALUES('000001',900,100,NULL,'SN900','REGISTERED',1,'0')");
  var context=mock(ErpTraceContext.class);when(context.tenant()).thenReturn("000001");var json=new ObjectMapper();
  var handler=new ErpProcessTraceEventHandler(j,json,context);String payload="{\"identityId\":\"900\",\"reason\":\"actual scrap\"}";
  var event=new MaterialTraceEvent("scrap","000001","MES","SN_SCRAPPED","SN_LIFECYCLE",1L,1,1,payload,TraceHashes.sha256(payload));
  var tx=new TransactionTemplate(new DataSourceTransactionManager(j.getDataSource()));tx.executeWithoutResult(s->handler.apply(event,null));
  check("SCRAPPED".equals(j.queryForObject("SELECT status FROM wm_sn",String.class)),"process handler should project scrap");
  var store=spy(new WmSerialInventoryRepository(j,null,null,context,json));doReturn(new TrackingPolicy(false,true,true,1L,TrackingStage.STRICT)).when(store).policy(100L);
  var guard=mock(WmProductionRecptWriteGuard.class);var wo=new PrdWorkOrder();wo.setWoId(20L);wo.setMaterialId(100L);when(guard.lockWorkOrder(20L)).thenReturn(wo);
  var completion=new WmTraceNoGoodOutputService(store,guard,mock(WmTraceMaterialConsumptionService.class),mock(PrdWorkOrderServiceImpl.class));
  var lines=List.of(new CompletionManifestLine(1L,900L,1L,"SN900",null,null,100L,BigDecimal.ONE,"SCRAP",1));
  var manifest=new CompletionManifest(55L,1,"000001",10L,20L,100L,1,BigDecimal.ZERO,BigDecimal.ONE,CompletionManifestIntegrity.hash(lines),"FROZEN");
  try{tx.executeWithoutResult(s->completion.postScrapOutputs(manifest,lines));throw new AssertionError("expected source rejection after legitimate scrap event");}
  catch(com.nexus.common.core.exception.ServiceException error){check(error.getMessage().contains("报废正式身份"),"unexpected failure: "+error.getMessage());}
  System.out.println("REPRO_C: committing legitimate SN_SCRAPPED first makes the all-scrap manifest reject its own ERP identity state");
 }
 static void appliedDeliveryStillRepeatsMutablePreparation(){
  var j=database();create(j,"wm_trace_outbox");create(j,"wm_trace_inbox");create(j,"mes_trace_outbox");create(j,"mes_trace_inbox");
  var tx=new TransactionTemplate(new DataSourceTransactionManager(j.getDataSource()));var source=new JdbcTraceJournal(j,new ObjectMapper(),"ERP");var target=new JdbcTraceJournal(j,new ObjectMapper(),"MES");
  var e=event("mutable-source",1);tx.executeWithoutResult(s->source.append(e));boolean[] valid={true};
  var handler=new TraceEventHandler(){public String targetModule(){return "MES";}public boolean supports(String type){return e.eventType().equals(type);}public Object prepare(MaterialTraceEvent ignored){if(!valid[0])throw new IllegalStateException("WORK_ORDER_CANCELLED");return null;}public String apply(MaterialTraceEvent ignored,Object prepared){return "ACCEPTED";}};
  var inbox=new TraceInboxPort(){public String targetModule(){return "MES";}public TraceDeliveryResult accept(MaterialTraceEvent message,TraceEventHandler business,Object prepared){return tx.execute(s->target.accept(message,()->business.apply(message,prepared)));}};
  var verifier=new TraceSourceVerificationPort(){public String sourceModule(){return "ERP";}public void verify(MaterialTraceEvent message){com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier.verify(j,"ERP",message);}};
  var coordinator=new TraceDeliveryCoordinator(List.of(inbox),List.of(handler),List.of(verifier));check(coordinator.deliver("MES",e).applied(),"initial effect must commit");
  var ack=tx.execute(s->target.claim("relay",10,30)).get(0);tx.executeWithoutResult(s->target.brokerAccepted(ack.eventId(),"relay"));
  valid[0]=false;try{coordinator.deliver("MES",e);throw new AssertionError("expected redundant preflight failure");}catch(IllegalStateException expected){check("WORK_ORDER_CANCELLED".equals(expected.getMessage()),"wrong preflight failure");}
  check("APPLIED".equals(j.queryForObject("SELECT status FROM mes_trace_inbox WHERE event_id='mutable-source'",String.class)),"original durable inbox must still be applied");
  check("APPLIED".equals(j.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,ack.eventId())),"ack is never rearmed");
  tx.executeWithoutResult(s->target.accept(e,()->{throw new AssertionError("dedup must avoid effect");}));
  check("NEW".equals(j.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,ack.eventId())),"direct journal replay could recover the ack");
  System.out.println("REPRO_D: valid source and APPLIED inbox cannot replay through coordinator after business preflight changes; direct journal dedup would recover its lost ACK");
 }
 static void canonicalMappingDoesNotCloseLocalCodeEntrypoints(){
  var j=database();j.execute("CREATE TABLE pro_sn(sn_id bigint,tenant_id varchar(20),identity_id bigint,identity_version bigint,sn_code varchar(64),mes_wo_id bigint,material_id bigint,sn_status varchar(32),handoff_status varchar(24),del_flag char(1))");
  j.update("INSERT INTO pro_sn VALUES(7,'000001',900,2,'LOCAL-OLD',10,100,'IN_PRODUCTION','NONE','0')");
  j.execute("CREATE TABLE mes_test_record(test_record_id bigint,test_no varchar(64),process_id bigint,item_code varchar(64),item_name varchar(64),result varchar(16),numeric_value decimal,text_value varchar(64),test_time timestamp,content_version varchar(32),tenant_id varchar(20),sn_id bigint,del_flag char(1))");
  var context=mock(com.nexus.mes.trace.MesTraceContext.class);when(context.tenant()).thenReturn("000001");
  var authoritative=new SerialIdentity(900L,"ERP-NEW",100L,null,null);
  var reader=new com.nexus.mes.integration.trace.MesIdentityTraceReader(j,context);
  var part=reader.identity("000001",900L,"TEST",null,500);
  check(part.localSnId()==7 && "LOCAL-OLD".equals(part.canonicalSnCode()) && !authoritative.snCode().equals(part.canonicalSnCode()),"reader should incorrectly call local original code canonical");
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"review"),com.nexus.mes.pro.domain.ProSn.class);
  var mapper=mock(com.nexus.mes.pro.mapper.ProSnMapper.class);
  when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(invocation->{
   var wrapper=(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?,?,?>)invocation.getArgument(0);wrapper.getSqlSegment();
   String scanned=wrapper.getParamNameValuePairs().values().stream().filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
   check(j.queryForObject("SELECT COUNT(*) FROM pro_sn WHERE tenant_id='000001' AND identity_id=900",Integer.class)==1,"real local identity mapping is present");
   check(j.queryForObject("SELECT COUNT(*) FROM pro_sn WHERE tenant_id='000001' AND sn_code=?",Integer.class,scanned)==0,"canonical code should not equal local original");return null;
  });
  var station=mock(com.nexus.station.service.StationPassService.class,CALLS_REAL_METHODS);org.springframework.test.util.ReflectionTestUtils.setField(station,"proSnMapper",mapper);
  var pass=station.validate(authoritative.snCode(),"STATION");check(Boolean.FALSE.equals(pass.get("pass")) && pass.get("checks").toString().contains("SN不存在"),"station should miss its mapped serial");
  j.execute("CREATE TABLE lt_wo_consumption(tenant_id varchar(20),material_id bigint,source_allocation_id bigint,lot_id bigint,del_flag char(1))");
  j.execute("CREATE TABLE lt_lot_allocation(tenant_id varchar(20),allocation_id bigint,material_id bigint,lot_id bigint)");
  j.execute("CREATE TABLE lt_lot(tenant_id varchar(20),material_id bigint,lot_id bigint,quantity decimal,del_flag char(1))");
  j.execute("CREATE TABLE lt_sn_binding(tenant_id varchar(20),sn_id bigint,material_id bigint,lot_id bigint)");
  j.execute("CREATE TABLE lt_sn_consumption(tenant_id varchar(20),local_sn_id bigint,component_material_id bigint,component_lot_id bigint)");
  var usage=new com.nexus.mes.trace.MesTrackingUsageService(j,context);usage.assertReadyForStrict("000001",100L);
  System.out.println("REPRO_E: mapped identity 900/local 7 still misses canonical scan; MES trace calls LOCAL-OLD canonical; strict readiness never detects the code mismatch");
  j.update("UPDATE pro_sn SET sn_status='SHIPPED',identity_id=NULL");
  try{usage.assertReadyForStrict("000001",100L);throw new AssertionError("expected historical terminal identity to block strict");}
  catch(com.nexus.common.core.exception.ServiceException error){check(error.getMessage().contains("历史MES身份"),"unexpected terminal check failure");}
  System.out.println("REPRO_F: a terminal SHIPPED legacy row with unknown identity blocks strict; no approved quarantine route is consulted");
 }
 public static void main(String[] args){successfulSendsCountTowardConflict();erpOnlyUsageAppearsComplete();scrapFactBlocksAllScrapCompletion();appliedDeliveryStillRepeatsMutablePreparation();canonicalMappingDoesNotCloseLocalCodeEntrypoints();}
}
