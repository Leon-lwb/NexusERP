package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesTraceBusinessTest {
    JdbcTemplate jdbc; TransactionTemplate tx; MesTraceContext context; MesTraceStore events;
    MesMaterialTraceWriter materials; MesCompletionManifestService manifests; MesSnCreationWriter sns;
    MesPreparedWorkOrderPolicies preparedPolicies;
    MesErpSourceReader sourceEvidence; MaterialPolicyPort policies; ObjectMapper json = new ObjectMapper();
    TrackingPolicy mixed = new TrackingPolicy(true,true,true,1L,TrackingStage.STRICT);
    TrackingPolicy componentPolicy = new TrackingPolicy(true,false,true,1L,TrackingStage.STRICT);
    @BeforeEach void setup() {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        context=spy(new MesTraceContext(mock(MesProgressTransactionGuard.class))); doReturn("000001").when(context).tenant();
        sourceEvidence=mock(MesErpSourceReader.class);
        when(sourceEvidence.verifyEvent(any())).thenAnswer(i->{MaterialTraceEvent e=i.getArgument(0);return new MesErpSourceReader.VerifiedEvent(e.eventId(),e.tenantId(),e.contentHash(),"ERP_PRODUCTION_LOT_AUTHORIZED".equals(e.eventType())?mixed:componentPolicy,17L);});
        events=new MesTraceStore(jdbc,json,context,new MesTraceJournalService(jdbc,json,context));
        policies=mock(MaterialPolicyPort.class); when(policies.policy("000001",100L)).thenReturn(mixed);
        var identities=mock(com.nexus.common.trace.port.SerialIdentityPort.class);
        when(identities.find(anyString(),anyLong())).thenAnswer(i->new SerialIdentity(i.getArgument(1),"ERP-"+((Long)i.getArgument(1)-10000),100L,null,null));
        preparedPolicies=mock(MesPreparedWorkOrderPolicies.class);
        manifests=new MesCompletionManifestService(jdbc,json,context,events,policies,new MesProductLotOutputWriter(jdbc,context,events,policies,preparedPolicies),identities,preparedPolicies);
        materials=new MesMaterialTraceWriter(jdbc,json,context,events,manifests);
        sns=new MesSnCreationWriter(jdbc,json,context,events,mock(org.springframework.context.ApplicationEventPublisher.class));
        jdbc.execute("CREATE TABLE pro_work_order(mes_wo_id bigint primary key,tenant_id varchar(20),erp_wo_id bigint,wo_no varchar(64),material_id bigint,wo_status varchar(32),planned_qty decimal(18,4),completed_qty decimal(18,4),scrap_qty decimal(18,4),del_flag char(1))");
        jdbc.execute("CREATE TABLE pro_flow_card(card_id bigint primary key,tenant_id varchar(20),mes_wo_id bigint,card_no varchar(64),wo_no varchar(64),material_id bigint,material_code varchar(64),material_name varchar(128),planned_qty decimal(18,4),card_status varchar(32),aggregated_flag int,del_flag char(1))");
        jdbc.execute("CREATE TABLE pro_sn(sn_id bigint primary key,tenant_id varchar(20),identity_id bigint,identity_version bigint default 1,product_lot_id bigint,handoff_status varchar(24) default 'NONE',sn_code varchar(128),mes_wo_id bigint,wo_no varchar(64),card_id bigint,card_no varchar(64),material_id bigint,material_code varchar(64),material_name varchar(128),current_step_seq int,sn_status varchar(32),parent_sn_id bigint,parent_sn_code varchar(128),source varchar(32),source_id bigint,subcontract_no varchar(64),production_date date,remark varchar(500),del_flag char(1) default '0',create_time timestamp default current_timestamp,UNIQUE(tenant_id,identity_id))");
        jdbc.execute("CREATE TABLE pro_flow_card_step(step_record_id bigint primary key,tenant_id varchar(20),card_id bigint,sequence_num int,step_status varchar(32),del_flag char(1))");
        jdbc.execute("CREATE TABLE mes_station_report(report_id bigint primary key,tenant_id varchar(20),mes_wo_id bigint,card_id bigint,step_record_id bigint,sn_code varchar(128),report_type varchar(16),result varchar(16),operator_id bigint,request_fingerprint varchar(64))");
        jdbc.execute("CREATE TABLE ncr_order(ncr_id bigint primary key,tenant_id varchar(20),wo_id bigint,card_id bigint,status varchar(32),disposition varchar(32),del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE ncr_rework_order(rework_id bigint primary key,tenant_id varchar(20),ncr_id bigint,original_wo_id bigint,rework_wo_id bigint,status varchar(32),del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE pro_work_order_step(step_id bigint primary key,tenant_id varchar(20),mes_wo_id bigint,process_id bigint,is_key_process int,del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE qc_ipqc(ipqc_id bigint primary key,tenant_id varchar(20),mes_wo_id bigint,process_id bigint,inspect_result varchar(32),del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE mes_test_record(test_record_id bigint primary key,tenant_id varchar(20),sn_id bigint,sn_code varchar(128),test_time timestamp,result varchar(16),del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE lt_sn_binding(binding_id bigint primary key,tenant_id varchar(20),sn_id bigint,material_id bigint,lot_id bigint)");
        jdbc.execute("CREATE TABLE lt_lot(lot_id bigint primary key,tenant_id varchar(20),lot_code varchar(64),material_id bigint,material_code varchar(64),material_name varchar(128),quantity decimal(18,4),consumed_qty decimal(18,4),status varchar(32),qc_status varchar(16),expiry_date date,erp_batch_id bigint,allocation_version bigint default 1,del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE pro_work_order_bom_line(wo_bom_line_id bigint primary key,tenant_id varchar(20),mes_wo_id bigint,child_material_id bigint,total_quantity decimal(18,4),process_id bigint,backflush_flag int,del_flag char(1))");
        jdbc.execute("CREATE TABLE lt_wo_consumption(consumption_id bigint primary key,tenant_id varchar(20),wo_id bigint,wo_no varchar(64),lot_id bigint,lot_code varchar(64),material_id bigint,material_code varchar(64),consumed_qty decimal(18,4),consumed_time timestamp,operator_id bigint,source_allocation_id bigint,remark varchar(500),del_flag char(1) default '0')");
        jdbc.execute("CREATE TABLE lt_sn_consumption(entry_id bigint primary key,tenant_id varchar(20),command_id varchar(128),line_no bigint,identity_id bigint,local_sn_id bigint,component_material_id bigint,component_lot_id bigint,quantity decimal(18,4),wo_consumption_id bigint,process_id bigint,operation_ref varchar(128),entry_type varchar(16),original_entry_id bigint,policy_version bigint,created_by bigint,created_at timestamp default current_timestamp,UNIQUE(tenant_id,command_id,line_no))");
        jdbc.execute("CREATE TABLE lt_sn_material_guard(tenant_id varchar(20),identity_id bigint,component_material_id bigint,selected_lot_id bigint,allow_mixed_lot boolean,policy_version bigint,version bigint,PRIMARY KEY(tenant_id,identity_id,component_material_id))");
        jdbc.execute("CREATE TABLE lt_lot_allocation(allocation_id bigint primary key,tenant_id varchar(20),command_id varchar(128),line_no bigint,lot_id bigint,erp_batch_id bigint,material_id bigint,quantity decimal(18,4),source_type varchar(32),source_id bigint,source_line_id bigint,allocation_version bigint,evidence_json clob,evidence_hash varchar(64),created_at timestamp default current_timestamp,UNIQUE(tenant_id,command_id,line_no))");
        for(String table:List.of("mes_trace_outbox","mes_trace_inbox")) jdbc.execute("CREATE TABLE "+table+"(event_id varchar(64) primary key,tenant_id varchar(20),source_module varchar(16),aggregate_type varchar(64),aggregate_id bigint,aggregate_version bigint,event_type varchar(64),schema_version int,payload clob,content_hash varchar(64),status varchar(24) default 'PENDING',attempts int default 0,next_attempt_at timestamp default current_timestamp,lease_owner varchar(128),lease_until timestamp,last_error varchar(500),result_payload clob,created_at timestamp default current_timestamp,updated_at timestamp default current_timestamp,applied_at timestamp,UNIQUE(tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version))");
        jdbc.execute("CREATE TABLE pro_completion_manifest(manifest_id bigint primary key,revision int,tenant_id varchar(20),mes_wo_id bigint,erp_wo_id bigint,material_id bigint,line_count bigint,good_qty decimal(18,4),scrap_qty decimal(18,4),content_hash varchar(64),material_line_count bigint,material_hash varchar(64),status varchar(24),created_at timestamp default current_timestamp,UNIQUE(tenant_id,mes_wo_id,revision))");
        jdbc.execute("CREATE TABLE pro_completion_material_allocation(tenant_id varchar(20),manifest_id bigint,revision int,allocation_id bigint,material_id bigint,reservation_id bigint,reservation_line_id bigint,posting_group_id bigint,identity_id bigint,source_stock_id bigint,erp_batch_id bigint,quantity decimal(18,4),source_version bigint,evidence_hash varchar(64),source_event_id varchar(64),PRIMARY KEY(tenant_id,manifest_id,revision,allocation_id))");
        jdbc.execute("CREATE TABLE pro_completion_manifest_line(tenant_id varchar(20),manifest_id bigint,revision int,line_no bigint,identity_id bigint,local_sn_id bigint,sn_code varchar(128),batch_id bigint,local_lot_id bigint,material_id bigint,quantity decimal(18,4),result varchar(16),source_version bigint,PRIMARY KEY(tenant_id,manifest_id,revision,line_no))");
        jdbc.update("INSERT INTO pro_work_order VALUES(10,'000001',20,'WO10',100,'IN_PROGRESS',1000,0,0,'0')");
        jdbc.update("INSERT INTO pro_flow_card VALUES(11,'000001',10,'CARD11','WO10',100,'FG','Product',1000,'IN_PROGRESS',0,'0')");
        lot(7,100,1000);lot(8,200,20);lot(9,200,20);
        jdbc.update("INSERT INTO pro_work_order_bom_line VALUES(1,'000001',10,200,10,30,0,'0')");
        DynamicDataSourceContextHolder.push("mes");
    }
    @AfterEach void cleanup(){DynamicDataSourceContextHolder.clear();}
    <T> T inTx(java.util.function.Supplier<T> work){return tx.execute(s->work.get());}
    void lot(long id,long material,int qty){jdbc.update("INSERT INTO lt_lot(lot_id,tenant_id,lot_code,material_id,quantity,consumed_qty,status,qc_status) VALUES(?,'000001',?,?,?,0,'IN_STOCK','PASS')",id,"L"+id,material,qty);}
    void sn(long id,String status){jdbc.update("INSERT INTO pro_sn(sn_id,tenant_id,identity_id,sn_code,mes_wo_id,card_id,material_id,sn_status,product_lot_id,source) VALUES(?,'000001',?,?,10,11,100,?,7,'BATCH')",id,10000+id,"ERP-"+id,status);}
    MaterialTraceEvent erp(String id,String type,long agg,Map<String,Object> payload){try{String body=json.writeValueAsString(payload);return new MaterialTraceEvent(id,"000001","ERP",type,"TEST_SOURCE",agg,1,1,body,TraceHashes.sha256(body));}catch(Exception e){throw new RuntimeException(e);}}
    MesTraceEventHandler handler(MesTraceStore store){return new MesTraceEventHandler(store,sourceEvidence);}
    boolean deliver(MaterialTraceEvent event){boolean prior=jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox WHERE event_id=? AND status='APPLIED'",Integer.class,event.eventId())>0;new MesTraceJournalService(jdbc,json,context).accept(event,handler(events),handler(events).prepare(event));return !prior;}
    String source(long agg,int qty,int backflush){var e=erp("source"+agg,backflush==1?"ERP_BACKFLUSH_ALLOCATION_AUTHORIZED":"ERP_MATERIAL_SOURCE_POSTED",agg,Map.of("sourceType","PRODUCTION_ISSUE","sourceId",agg,"sourceLineId",agg,"erpBatchId",500L,"materialId",200L,"mesWoId",10L,"erpWoId",20L,"quantity",qty,"backflushFlag",backflush,"postingGroupId",agg));inTx(()->deliver(e));return e.eventId();}
    long allocate(String command,String source,long lot,int qty){return inTx(()->materials.allocateLot(command,source,lot,new BigDecimal(qty),1L));}
    MesMaterialTraceWriter.Pick pick(String cmd,long lot,long allocation,int qty,Long sn){return new MesMaterialTraceWriter.Pick(cmd,10L,200L,sn,30L,"station-operation-"+cmd,List.of(new MesMaterialTraceWriter.PickLine(lot,new BigDecimal(qty),allocation)),1L);}
    ProWorkOrder wo(int good,int scrap){var wo=new ProWorkOrder();wo.setTenantId("000001");wo.setMesWoId(10L);wo.setErpWoId(20L);wo.setMaterialId(100L);wo.setWoNo("WO10");wo.setCompletedQty(new BigDecimal(good));wo.setScrapQty(new BigDecimal(scrap));wo.setWoStatus("COMPLETED");jdbc.update("UPDATE pro_work_order SET wo_status='COMPLETED',completed_qty=?,scrap_qty=?,planned_qty=? WHERE mes_wo_id=10",good,scrap,good+scrap);jdbc.update("UPDATE pro_flow_card SET card_status='COMPLETED',aggregated_flag=1");return wo;}
    void productMapping(){var e=erp("product","ERP_PRODUCTION_LOT_AUTHORIZED",90,Map.of("sourceType","PRODUCTION_OUTPUT","sourceId",20L,"sourceLineId",1L,"erpBatchId",600L,"materialId",100L,"mesWoId",10L,"quantity",1000));inTx(()->deliver(e));allocate("product-map","product",7,1000);}

    @Test void repairedLocalTextIsNotMistakenForCanonicalManifestCode(){productMapping();sn(1,"COMPLETED");jdbc.update("UPDATE pro_sn SET sn_code='LOCAL-HISTORICAL' WHERE sn_id=1");var m=inTx(()->manifests.freeze(wo(1,0)));var line=manifests.lines("000001",m.manifestId(),1,null,500).rows().get(0);assertEquals("ERP-1",line.snCode());assertEquals("LOCAL-HISTORICAL",jdbc.queryForObject("SELECT sn_code FROM pro_sn WHERE sn_id=1",String.class));}
    @Test void sourceStateAndOutboxRollbackTogether(){assertThrows(IllegalStateException.class,()->inTx(()->{sn(1,"COMPLETED");events.append("TEST","SN",1L,1,Map.of("id",1));throw new IllegalStateException();}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox",Integer.class));}
    @Test void rejectsOutboxWithoutActualTransaction(){assertThrows(RuntimeException.class,()->events.append("TEST","SN",1L,1,Map.of("id",1)));}
    @Test void inboxReplayIsDurableAndRejectsChangedContent(){var e=erp("ship1","ERP_SHIPMENT_POSTED",1,Map.of("postingGroupId",1,"sourceDocType","SALES_SHIP","sourceDocId",1,"identityIds",List.of(10001)));sn(1,"COMPLETED");assertTrue(inTx(()->deliver(e)));assertFalse(inTx(()->deliver(e)));assertEquals("SHIPPED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));var changed=erp("ship1","ERP_SHIPMENT_POSTED",1,Map.of("postingGroupId",2,"sourceDocType","SALES_SHIP","sourceDocId",1,"identityIds",List.of(10001)));assertThrows(RuntimeException.class,()->inTx(()->deliver(changed)));}
    @Test void shipmentNeedsPostedDocumentEvidence(){sn(1,"COMPLETED");var e=erp("bad","ERP_SHIPMENT_POSTED",1,Map.of("identityIds",List.of(10001)));assertThrows(RuntimeException.class,()->inTx(()->deliver(e)));assertEquals("COMPLETED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox",Integer.class));}
    @Test void wrongTenantEventCannotCreateInbox(){var e=new MaterialTraceEvent("cross","000002","ERP","ERP_MATERIAL_SOURCE_POSTED","TEST",1L,1,1,"{}",TraceHashes.sha256("{}"));assertThrows(RuntimeException.class,()->inTx(()->deliver(e)));}
    @Test void untrustedLotMappingIsRejected(){assertThrows(RuntimeException.class,()->allocate("missing","guess",8,1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lt_lot_allocation",Integer.class));}
    @Test void oneErpBatchCanSplitAcrossLotsButNotExceedSource(){String e=source(1,10,0);allocate("map1",e,8,6);allocate("map2",e,9,4);assertThrows(RuntimeException.class,()->allocate("map3",e,9,1));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lt_lot_allocation",Integer.class));}
    @Test void allocationReplayDoesNotAllocateAgain(){String e=source(1,10,0);long a=allocate("map",e,8,6);assertEquals(a,allocate("map",e,8,6));assertThrows(RuntimeException.class,()->allocate("map",e,8,7));}
    @Test void pickingDeductsLotOnceAndComponentAllocationUsesActualQuantity(){sn(1,"IN_PRODUCTION");long a=allocate("map",source(1,10,0),8,10);var p=pick("pick",8,a,3,1L);inTx(()->materials.pick(p,componentPolicy));inTx(()->materials.pick(p,componentPolicy));assertEquals(0,jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class).compareTo(new BigDecimal(3)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT SUM(quantity) FROM lt_sn_consumption",BigDecimal.class).compareTo(new BigDecimal(3)));assertEquals(30L,jdbc.queryForObject("SELECT process_id FROM lt_sn_consumption",Long.class));}
    @Test void componentAllocationCannotExceedActualConsumptionAndDoesNotDeductAgain(){sn(1,"IN_PRODUCTION");sn(2,"IN_PRODUCTION");long a=allocate("map",source(1,10,0),8,10);long consumption=inTx(()->materials.pick(pick("pick",8,a,3,null),componentPolicy)).get(0);inTx(()->materials.allocateToSn("assign1",consumption,1L,new BigDecimal(2),30L,"assembly1",1L,componentPolicy));assertThrows(RuntimeException.class,()->inTx(()->materials.allocateToSn("assign2",consumption,2L,new BigDecimal(2),30L,"assembly2",1L,componentPolicy)));assertEquals(0,jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class).compareTo(new BigDecimal(3)));}
    @Test void mixedLotsDefaultAllowedAndNoMixRejectsSecondLot(){sn(1,"IN_PRODUCTION");String s=source(1,10,0);long a=allocate("map1",s,8,5),b=allocate("map2",s,9,5);inTx(()->materials.pick(pick("p1",8,a,2,1L),componentPolicy));inTx(()->materials.pick(pick("p2",9,b,2,1L),componentPolicy));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));}
    @Test void noMixPolicyIsGuardedByIdentityAndComponent(){sn(1,"IN_PRODUCTION");String s=source(1,10,0);long a=allocate("m1",s,8,5),b=allocate("m2",s,9,5);var noMix=new TrackingPolicy(true,false,false,2L,TrackingStage.STRICT);inTx(()->materials.pick(pick("p1",8,a,2,1L),noMix));assertThrows(RuntimeException.class,()->inTx(()->materials.pick(pick("p2",9,b,2,1L),noMix)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));}
    @Test void componentReversalRetainsOriginalAndReleasesOnlyAllocationBudget(){sn(1,"IN_PRODUCTION");long a=allocate("m1",source(1,10,0),8,10);inTx(()->materials.pick(pick("p1",8,a,3,1L),componentPolicy));long entry=jdbc.queryForObject("SELECT entry_id FROM lt_sn_consumption",Long.class);inTx(()->materials.reverse("undo",entry,new BigDecimal(2),"wrong component",1L));assertThrows(RuntimeException.class,()->inTx(()->materials.reverse("undo2",entry,new BigDecimal(2),"over reverse",1L)));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class).compareTo(new BigDecimal(3)));}
    @Test void preissueAndBackflushCannotBeInterchanged(){long a=allocate("map",source(1,10,1),8,10);assertThrows(RuntimeException.class,()->inTx(()->materials.pick(pick("p",8,a,1,null),componentPolicy)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));}
    @Test void workOrderOverpickRollsBackEveryLine(){String s=source(1,20,0);long a=allocate("m1",s,8,10),b=allocate("m2",s,9,10);var p=new MesMaterialTraceWriter.Pick("p",10L,200L,null,30L,"operation",List.of(new MesMaterialTraceWriter.PickLine(8L,new BigDecimal(6),a),new MesMaterialTraceWriter.PickLine(9L,new BigDecimal(6),b)),1L);assertThrows(RuntimeException.class,()->inTx(()->materials.pick(p,componentPolicy)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));}
    @Test void incompleteLastSnPreventsFinalManifest(){productMapping();sn(1,"COMPLETED");sn(2,"IN_PRODUCTION");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(wo(2,0))));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest",Integer.class));}
    @Test void goodScrapSetsMustReconcileWithWorkOrder(){productMapping();sn(1,"COMPLETED");sn(2,"SCRAPPED");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(wo(2,0))));var m=inTx(()->manifests.freeze(wo(1,1)));assertEquals(2,m.lineCount());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest_line WHERE result='SCRAP'",Integer.class));}
    @Test void manifestCursorIsStableBeyondFiveHundredAndHashIsComplete(){productMapping();for(int i=1;i<=501;i++)sn(i,"COMPLETED");var m=inTx(()->manifests.freeze(wo(501,0)));var first=manifests.lines("000001",m.manifestId(),m.revision(),null,500);assertEquals(500,first.rows().size());assertTrue(first.hasMore());var last=manifests.lines("000001",m.manifestId(),m.revision(),first.nextCursor(),500);assertEquals(1,last.rows().size());assertFalse(last.hasMore());var all=new ArrayList<>(first.rows());all.addAll(last.rows());assertEquals(m.contentHash(),MesCompletionManifestService.hash(all));assertEquals(m.manifestId(),inTx(()->manifests.freeze(wo(501,0))).manifestId());assertThrows(RuntimeException.class,()->manifests.lines("000001",m.manifestId(),1,null,501));assertThrows(RuntimeException.class,()->manifests.lines("000001",m.manifestId(),1,"bogus",500));}
    @Test void manifestAndOutboxAreAtomicAndFrozenLinesDoNotFollowLiveEdits(){productMapping();sn(1,"COMPLETED");var m=inTx(()->manifests.freeze(wo(1,0)));jdbc.update("UPDATE pro_sn SET sn_code='changed' WHERE sn_id=1");assertEquals("ERP-1",manifests.lines("000001",m.manifestId(),1,null,500).rows().get(0).snCode());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",Integer.class));}
    @Test void serialReservationReplayCreatesOneLocalIdentityAndOneConfirmation(){var r=new SerialReservation(5L,"generate","fp",List.of(new SerialIdentity(900L,"CANONICAL",100L,null,null)),"RESERVED");var req=new MesSnCreationWriter.Request("generate",11L,10L,null,"BATCH",null,100L,7L,null,null,null);var ids=inTx(()->sns.write(r,req));assertEquals(ids,inTx(()->sns.write(r,req)));assertEquals(900L,jdbc.queryForObject("SELECT identity_id FROM pro_sn",Long.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='SERIAL_RESERVATION_CONFIRM'",Integer.class));var changed=new MesSnCreationWriter.Request("generate",11L,10L,null,"BATCH",null,100L,8L,null,null,null);assertThrows(RuntimeException.class,()->inTx(()->sns.write(r,changed)));}
    @Test void nullableWorkorderOnlyAllowedWithRealSubcontractSource(){var r=new SerialReservation(5L,"sub","fp",List.of(new SerialIdentity(900L,"CANONICAL",100L,null,null)),"RESERVED");var req=new MesSnCreationWriter.Request("sub",null,null,77L,"SUBCONTRACT","SC77",100L,null,null,null,null);assertThrows(RuntimeException.class,()->inTx(()->sns.write(r,req)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));}
    @Test void concurrentOverpickAcrossLotsCanOnlyCommitOneCommand() throws Exception {
        String e=source(1,10,0);long a=allocate("m1",e,8,5),b=allocate("m2",e,9,5);
        jdbc.update("UPDATE pro_work_order_bom_line SET total_quantity=5");
        var result=concurrently(()->materials.pick(pick("race1",8,a,4,null),componentPolicy),()->materials.pick(pick("race2",9,b,4,null),componentPolicy));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        assertEquals(0,jdbc.queryForObject("SELECT SUM(consumed_qty) FROM lt_wo_consumption",BigDecimal.class).compareTo(new BigDecimal(4)));
    }
    @Test void concurrentNoMixAcrossDifferentLotsCommitsOneIdentityComponentLot() throws Exception {
        sn(1,"IN_PRODUCTION");String e=source(1,10,0);long a=allocate("m1",e,8,5),b=allocate("m2",e,9,5);
        var policy=new TrackingPolicy(true,false,false,2L,TrackingStage.STRICT);
        var result=concurrently(()->materials.pick(pick("race1",8,a,1,1L),policy),()->materials.pick(pick("race2",9,b,1,1L),policy));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));
    }
    List<Boolean> concurrently(java.util.function.Supplier<?> first,java.util.function.Supplier<?> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var tasks=List.of(first,second).stream().map(work->pool.submit(()->{DynamicDataSourceContextHolder.push("mes");try{start.await();inTx(()->work.get());return true;}catch(RuntimeException e){return false;}finally{DynamicDataSourceContextHolder.clear();}})).toList();start.countDown();return List.of(tasks.get(0).get(10,TimeUnit.SECONDS),tasks.get(1).get(10,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    @Test void journalProxyRequiresSourceTransactionAndRollsBackInboxWithEffect() {
        var port=proxiedJournal();
        var event=erp("rollback","ERP_TEST",3,Map.of("id",1));
        var handler=new com.nexus.common.trace.port.TraceEventHandler(){public String targetModule(){return "MES";}public boolean supports(String type){return true;}public String apply(MaterialTraceEvent e,Object p){sn(1,"COMPLETED");throw new IllegalStateException("failure after source write");}};
        assertThrows(RuntimeException.class,()->port.append(new MaterialTraceEvent("local","000001","MES","TEST","TEST",1L,1,1,"{}",TraceHashes.sha256("{}"))));
        assertThrows(IllegalStateException.class,()->port.accept(event,handler,handler.prepare(event)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox",Integer.class));
    }
    @Test void journalProxyAcceptsOnceAndCreatesDurableAppliedReceipt() {
        var port=proxiedJournal();var event=erp("once","ERP_TEST",3,Map.of("id",1));
        var handler=new com.nexus.common.trace.port.TraceEventHandler(){public String targetModule(){return "MES";}public boolean supports(String type){return true;}public String apply(MaterialTraceEvent e,Object p){sn(1,"COMPLETED");return "{\"identityId\":10001}";}};
        assertTrue(port.accept(event,handler,handler.prepare(event)).applied());assertTrue(port.accept(event,handler,handler.prepare(event)).applied());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='TRACE_APPLIED'",Integer.class));
    }
    MesTraceJournalService proxiedJournal(){var factory=new org.springframework.aop.framework.ProxyFactory(new MesTraceJournalService(jdbc,json,context));factory.setProxyTargetClass(true);factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));return (MesTraceJournalService)factory.getProxy();}
    @Test void trackingReadinessRejectsUnmappedLegacyIdentities() {
        var usage=new MesTrackingUsageService(jdbc,context);sn(1,"IN_PRODUCTION");jdbc.update("UPDATE pro_sn SET identity_id=NULL WHERE sn_id=1");
        assertTrue(usage.hasUnsettledProduction("000001",100L));assertThrows(RuntimeException.class,()->usage.assertReadyForStrict("000001",100L));
    }
    @Test void strictReadinessDetectsActualConsumptionWithoutSourceAllocation() {
        var usage=new MesTrackingUsageService(jdbc,context);jdbc.update("INSERT INTO lt_wo_consumption(consumption_id,tenant_id,wo_id,lot_id,material_id,consumed_qty) VALUES(1,'000001',10,8,200,1)");
        assertThrows(RuntimeException.class,()->usage.assertReadyForStrict("000001",200L));
    }

    @Test void manifestStillReadsAfterApplicationStatusAdvances(){productMapping();sn(1,"COMPLETED");var m=inTx(()->manifests.freeze(wo(1,0)));jdbc.update("UPDATE pro_completion_manifest SET status='APPLIED' WHERE manifest_id=?",m.manifestId());assertEquals(m.contentHash(),manifests.manifest("000001",m.manifestId(),1).contentHash());assertEquals(1,manifests.lines("000001",m.manifestId(),1,null,500).rows().size());}
    @Test void completionRequiresActualProductionPassAndCannotBypassWithManualFlag(){sn(1,"IN_PRODUCTION");var lifecycle=new MesSnLifecycleWriter(jdbc,context,events);assertThrows(RuntimeException.class,()->inTx(()->{lifecycle.complete(1L);return true;}));assertEquals("IN_PRODUCTION",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));}
    @Test void frozenCompletionCannotBeSilentlyScrapped(){productMapping();sn(1,"COMPLETED");inTx(()->manifests.freeze(wo(1,0)));var lifecycle=new MesSnLifecycleWriter(jdbc,context,events);assertThrows(RuntimeException.class,()->inTx(()->{lifecycle.scrap(1L,"after receipt");return true;}));assertEquals("COMPLETED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));}

    @Test void realLastPassCompletesSnAndPublishesLifecycleV1EvenForLegacyFactVersion(){sn(1,"IN_PRODUCTION");jdbc.update("UPDATE pro_sn SET current_step_seq=30,identity_version=17 WHERE sn_id=1");jdbc.update("INSERT INTO pro_flow_card_step VALUES(77,'000001',11,30,'IN_PROGRESS','0')");jdbc.update("INSERT INTO mes_station_report VALUES(99,'000001',10,11,77,'ERP-1','SINGLE','PASS',1,'verified')");var lifecycle=new MesSnLifecycleWriter(jdbc,context,events);inTx(()->{lifecycle.complete(1L);return true;});assertEquals("COMPLETED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals(1L,jdbc.queryForObject("SELECT aggregate_version FROM mes_trace_outbox WHERE event_type='SN_COMPLETED'",Long.class));}

    MaterialTraceEvent acknowledgement(String eventId,String originalId,String hash,String result){return erp(eventId,"TRACE_APPLIED",900,Map.of("eventId",originalId,"contentHash",hash,"resultPayload",result));}
    @Test void genericAckAppliesReceiptBusinessProjectionOnceWithoutChangingRawResult(){productMapping();sn(1,"COMPLETED");var m=inTx(()->manifests.freeze(wo(1,0)));var origin=jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'");String original=origin.get("event_id").toString();String result="{ \"sourceEventId\":\""+original+"\", \"receiptId\":71,\"postingGroupId\":72,\"manifestId\":"+m.manifestId()+",\"revision\":1,\"manifestHash\":\""+m.contentHash()+"\" }";var event=acknowledgement("receipt-ack",original,origin.get("content_hash").toString(),result);var business=spy(events);var handler=handler(business);var port=proxiedJournal();assertTrue(port.accept(event,handler,handler.prepare(event)).applied());assertTrue(port.accept(event,handler,handler.prepare(event)).applied());verify(business,times(1)).applyAcknowledgement(event);assertEquals(result,jdbc.queryForObject("SELECT result_payload FROM mes_trace_outbox WHERE event_id=?",String.class,original));assertEquals("RECEIVED",jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals(m.contentHash(),manifests.manifest("000001",m.manifestId(),1).contentHash());}
    @Test void invalidAckRollsBackGenericAckAndBusinessProjectionTogether(){productMapping();sn(1,"COMPLETED");var m=inTx(()->manifests.freeze(wo(1,0)));var origin=jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'");String original=origin.get("event_id").toString();String bad=events.encode(Map.of("sourceEventId",original,"receiptId",71,"postingGroupId",72,"manifestId",m.manifestId(),"revision",1,"manifestHash","wrong"));var e=acknowledgement("bad-ack",original,origin.get("content_hash").toString(),bad);assertThrows(RuntimeException.class,()->proxiedJournal().accept(e,handler(events),handler(events).prepare(e)));assertEquals("NEW",jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,original));assertEquals("FROZEN",jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox WHERE event_id='bad-ack'",Integer.class));}
    @Test void genericReservationAckIsDurableWithoutInventingASecondResult(){var r=new SerialReservation(5L,"generate","fp",List.of(new SerialIdentity(900L,"CANONICAL",100L,null,null)),"RESERVED");var req=new MesSnCreationWriter.Request("generate",11L,10L,null,"BATCH",null,100L,7L,null,null,null);inTx(()->sns.write(r,req));var origin=jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='SERIAL_RESERVATION_CONFIRM'");String id=origin.get("event_id").toString();String result=events.encode(Map.of("sourceEventId",id,"reservationId",5,"commandId","generate","fingerprint","fp"));var ack=acknowledgement("registration-ack",id,origin.get("content_hash").toString(),result);proxiedJournal().accept(ack,handler(events),handler(events).prepare(ack));assertEquals("APPLIED",jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_id=?",String.class,id));assertEquals(result,jdbc.queryForObject("SELECT result_payload FROM mes_trace_outbox WHERE event_id=?",String.class,id));}
    @Test void realSubcontractProofPermitsNullableWorkorderAndReplay(){var proof=erp("sc-source","ERP_SUBCONTRACT_SOURCE_CONFIRMED",77,Map.of("subcontractId",77,"subcontractNo","SC77","materialId",100,"materialCode","PCBA","materialName","Board","quantity",2));var r=new SerialReservation(5L,"sub","fp",List.of(new SerialIdentity(900L,"CANONICAL",100L,null,null)),"RESERVED");var req=new MesSnCreationWriter.Request("sub",null,null,77L,"SUBCONTRACT","SC77",100L,null,null,null,null);var ids=inTx(()->sns.writeWithSource(r,req,proof));assertEquals(ids,inTx(()->sns.writeWithSource(r,req,proof)));assertNull(jdbc.queryForObject("SELECT mes_wo_id FROM pro_sn",Long.class));assertEquals("PCBA",jdbc.queryForObject("SELECT material_code FROM pro_sn",String.class));assertThrows(RuntimeException.class,()->inTx(()->{new MesSnLifecycleWriter(jdbc,context,events).complete(ids.get(0));return true;}));}
    @Test void reserveRunsOutsideMesTransactionAndLocalFailureCanReplayWithoutDuplicatingIdentity(){var identities=mock(com.nexus.common.trace.port.SerialIdentityPort.class);var cards=mock(com.nexus.mes.pro.mapper.ProFlowCardMapper.class);var card=new com.nexus.mes.pro.domain.ProFlowCard();card.setTenantId("000001");card.setCardId(11L);card.setMesWoId(10L);card.setMaterialId(100L);when(cards.selectById(11L)).thenReturn(card);when(identities.reserve(eq("000001"),any())).thenAnswer(i->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return new SerialReservation(5L,"generate",((SerialReservationCommand)i.getArgument(1)).fingerprint(),List.of(new SerialIdentity(900L,"CANONICAL",100L,null,null)),"RESERVED");});var factory=new org.springframework.aop.framework.ProxyFactory(sns);factory.setProxyTargetClass(true);factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));var writer=(MesSnCreationWriter)factory.getProxy();var orchestrator=new MesSerialOrchestrator(identities,context,cards,writer,mock(MesErpSourceReader.class));jdbc.update("UPDATE pro_flow_card SET planned_qty=0");assertThrows(RuntimeException.class,()->orchestrator.forCard("generate",11L,1,null,7L));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));jdbc.update("UPDATE pro_flow_card SET planned_qty=1");var result=orchestrator.forCard("generate",11L,1,null,7L);assertEquals(result,orchestrator.forCard("generate",11L,1,null,7L));verify(identities,times(2)).reserve(anyString(),any());verify(identities,never()).confirm(any(),any(),any());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn",Integer.class));}

    @Test void partialQuantityCannotCreateFinalReceiptManifest(){productMapping();sn(1,"COMPLETED");var source=wo(1,0);jdbc.update("UPDATE pro_work_order SET planned_qty=2 WHERE mes_wo_id=10");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(source)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest",Integer.class));}
    @Test void openCardCannotBeHiddenByWorkorderCompletionFlag(){productMapping();sn(1,"COMPLETED");var source=wo(1,0);jdbc.update("UPDATE pro_flow_card SET card_status='IN_PROGRESS',aggregated_flag=0 WHERE card_id=11");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(source)));}
    @Test void everyWorkorderSnMustBelongToTheProductMaterial(){productMapping();sn(1,"COMPLETED");sn(2,"COMPLETED");jdbc.update("UPDATE pro_sn SET material_id=999 WHERE sn_id=2");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(wo(1,0))));}

    @Test void legacyProductBindingCannotOverAllocateLotOrBecomeComponentConsumption(){sn(1,"IN_PRODUCTION");sn(2,"IN_PRODUCTION");jdbc.update("UPDATE pro_sn SET product_lot_id=NULL");jdbc.update("UPDATE lt_lot SET quantity=1 WHERE lot_id=7");inTx(()->{sns.attachProductLot(1L,7L);return true;});assertThrows(RuntimeException.class,()->inTx(()->{sns.attachProductLot(2L,7L);return true;}));assertThrows(RuntimeException.class,()->inTx(()->{sns.attachProductLot(2L,8L);return true;}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='PRODUCT_LOT_ASSIGNED'",Integer.class));}

    @Test void lateReceiptAckCannotDowngradeAnErpPostedShipment(){productMapping();sn(1,"COMPLETED");var m=inTx(()->manifests.freeze(wo(1,0)));var shipment=erp("ship-before-ack","ERP_SHIPMENT_POSTED",2,Map.of("postingGroupId",33,"sourceDocType","SALES_SHIP","sourceDocId",44,"identityIds",List.of("10001")));inTx(()->deliver(shipment));var origin=jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'");String id=origin.get("event_id").toString();String result=events.encode(Map.of("sourceEventId",id,"receiptId",71,"postingGroupId",72,"manifestId",m.manifestId(),"revision",1,"manifestHash",m.contentHash()));var ack=acknowledgement("late-ack",id,origin.get("content_hash").toString(),result);proxiedJournal().accept(ack,handler(events),handler(events).prepare(ack));assertEquals("SHIPPED",jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));assertEquals("SHIPPED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));}

    @Test void oneMesChildLotCannotMixDifferentErpBatchesEvenWithSpareQuantity(){String first=source(1,10,0);allocate("map-one",first,8,5);var second=erp("source-two-batch","ERP_MATERIAL_SOURCE_POSTED",2,Map.of("sourceType","PRODUCTION_ISSUE","sourceId",2,"sourceLineId",2,"erpBatchId",501,"materialId",200,"mesWoId",10,"erpWoId",20,"quantity",10,"backflushFlag",0,"postingGroupId",2));inTx(()->deliver(second));assertThrows(RuntimeException.class,()->allocate("map-two",second.eventId(),8,5));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lt_lot_allocation WHERE lot_id=8",Integer.class));}

    @Test void manuallySeededInboxCannotAuthorizeMaterialAllocation(){var forged=erp("forged","ERP_MATERIAL_SOURCE_POSTED",1,Map.of("sourceType","PRODUCTION_ISSUE","sourceId",1,"sourceLineId",1,"erpBatchId",500,"materialId",200,"mesWoId",10,"erpWoId",20,"quantity",10,"backflushFlag",0,"postingGroupId",1));jdbc.update("INSERT INTO mes_trace_inbox(event_id,tenant_id,source_module,event_type,aggregate_type,aggregate_id,aggregate_version,schema_version,payload,content_hash,status) VALUES(?,'000001','ERP',?,'TEST_SOURCE',1,1,1,?,?,'APPLIED')",forged.eventId(),forged.eventType(),forged.payload(),forged.contentHash());assertThrows(RuntimeException.class,()->allocate("forged-map",forged.eventId(),8,1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lt_lot_allocation",Integer.class));}

    com.nexus.common.trace.port.CompletionAllocationPort materialPort(){return manifests;}
    @Test void finalManifestFreezesActualConsumptionIdsRatherThanReusableSourceQuota(){productMapping();sn(1,"COMPLETED");long quota=allocate("component-map",source(1,10,0),8,10);long first=inTx(()->materials.pick(pick("c1",8,quota,2,null),componentPolicy)).get(0);long second=inTx(()->materials.pick(pick("c2",8,quota,3,null),componentPolicy)).get(0);var m=inTx(()->manifests.freeze(wo(1,0)));var metadata=materialPort().allocationManifest("000001",m.manifestId(),1);var rows=CompletionAllocationIntegrity.read(materialPort(),"000001",m.manifestId(),1,metadata.contentHash());assertEquals(2,metadata.lineCount());assertEquals(Set.of(first,second),rows.stream().map(CompletionMaterialAllocation::allocationId).collect(java.util.stream.Collectors.toSet()));assertFalse(rows.stream().anyMatch(r->r.allocationId().equals(quota)));assertEquals(0,rows.stream().map(CompletionMaterialAllocation::quantity).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal(5)));assertTrue(rows.stream().allMatch(r->Long.valueOf(1).equals(r.postingGroupId()) && r.sourceVersion()==17));}
    @Test void frozenComponentReadDoesNotFollowLaterConsumptionOrSourceEdits(){productMapping();sn(1,"COMPLETED");long quota=allocate("component-map",source(1,10,0),8,10);inTx(()->materials.pick(pick("c1",8,quota,3,null),componentPolicy));var m=inTx(()->manifests.freeze(wo(1,0)));var metadata=materialPort().allocationManifest("000001",m.manifestId(),1);jdbc.update("UPDATE lt_wo_consumption SET consumed_qty=9");jdbc.update("UPDATE lt_lot_allocation SET evidence_json='{}' WHERE allocation_id=?",quota);jdbc.update("UPDATE mes_trace_inbox SET payload='{}' WHERE event_id='source1'");var rows=CompletionAllocationIntegrity.read(materialPort(),"000001",m.manifestId(),1,metadata.contentHash());assertEquals(0,rows.get(0).quantity().compareTo(new BigDecimal(3)));assertEquals(metadata.contentHash(),CompletionAllocationIntegrity.hash(rows));String payload=jdbc.queryForObject("SELECT payload FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",String.class);assertEquals(metadata.contentHash(),events.read(payload).path("materialHash").asText());assertEquals(1,events.read(payload).path("materialLineCount").asInt());jdbc.update("UPDATE pro_completion_manifest SET status='APPLIED'");assertEquals(metadata.contentHash(),materialPort().allocationManifest("000001",m.manifestId(),1).contentHash());}
    @Test void materialFreezeMissingSourceRollsBackSourceFinalizationProductManifestAndEvent(){productMapping();sn(1,"COMPLETED");jdbc.update("INSERT INTO lt_wo_consumption(consumption_id,tenant_id,wo_id,lot_id,material_id,consumed_qty) VALUES(9,'000001',10,8,200,1)");assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(wo(1,0))));assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT wo_status FROM pro_work_order WHERE mes_wo_id=10",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_manifest",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_material_allocation",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",Integer.class));}
    @Test void materialSnapshotPaginatesAll501ActualConsumptionRecords(){productMapping();sn(1,"COMPLETED");long quota=allocate("component-map",source(1,10,0),8,10);var lines=new ArrayList<MesMaterialTraceWriter.PickLine>();for(int i=0;i<501;i++)lines.add(new MesMaterialTraceWriter.PickLine(8L,new BigDecimal("0.01"),quota));var pick=new MesMaterialTraceWriter.Pick("501-consumptions",10L,200L,null,30L,"actual-batch-use",lines,1L);inTx(()->materials.pick(pick,componentPolicy));var m=inTx(()->manifests.freeze(wo(1,0)));var metadata=materialPort().allocationManifest("000001",m.manifestId(),1);var first=materialPort().allocations("000001",m.manifestId(),1,null,500);assertEquals(500,first.rows().size());assertTrue(first.hasMore());assertEquals(1,materialPort().allocations("000001",m.manifestId(),1,first.nextCursor(),500).rows().size());assertEquals(501,CompletionAllocationIntegrity.read(materialPort(),"000001",m.manifestId(),1,metadata.contentHash()).size());assertThrows(RuntimeException.class,()->materialPort().allocations("000001",m.manifestId(),1,null,501));}
    String serialOnlySource(){componentPolicy=new TrackingPolicy(false,true,true,1L,TrackingStage.STRICT);var p=new LinkedHashMap<String,Object>();p.put("sourceType","BACKFLUSH_RESERVATION");p.put("sourceId",80);p.put("sourceLineId",81);p.put("reservationId",80);p.put("reservationLineId",81);p.put("mesWoId",10);p.put("erpWoId",20);p.put("materialId",200);p.put("erpBatchId",null);p.put("identityId",50001);p.put("sourceStockId",60001);p.put("quantity","1");p.put("backflushFlag",1);var e=erp("sn-only-source","ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",81,p);inTx(()->deliver(e));jdbc.update("UPDATE lt_lot SET quantity=1 WHERE lot_id=8");jdbc.update("UPDATE pro_work_order_bom_line SET backflush_flag=1 WHERE wo_bom_line_id=1");return e.eventId();}
    @Test void serialOnlyNullBatchHasRealIdentityAndWholeConsumptionAndProductAllocation(){sn(1,"IN_PRODUCTION");String source=serialOnlySource();assertThrows(RuntimeException.class,()->inTx(()->materials.allocateLot("half-quota",source,8L,new BigDecimal("0.5"),1L)));long quota=allocate("sn-only-map",source,8,1);assertThrows(RuntimeException.class,()->inTx(()->materials.pick(new MesMaterialTraceWriter.Pick("half-use",10L,200L,null,30L,"op",List.of(new MesMaterialTraceWriter.PickLine(8L,new BigDecimal("0.5"),quota)),1L),componentPolicy)));long consumed=inTx(()->materials.pick(pick("whole-use",8,quota,1,null),componentPolicy)).get(0);assertThrows(RuntimeException.class,()->inTx(()->materials.allocateToSn("half-assign",consumed,1L,new BigDecimal("0.5"),30L,"op",1L,componentPolicy)));inTx(()->materials.allocateToSn("whole-assign",consumed,1L,BigDecimal.ONE,30L,"op",1L,componentPolicy));String proof=jdbc.queryForObject("SELECT evidence_json FROM lt_lot_allocation WHERE allocation_id=?",String.class,quota);assertEquals(50001L,events.read(proof).path("source").path("identityId").asLong());assertNull(jdbc.queryForObject("SELECT erp_batch_id FROM lt_lot_allocation WHERE allocation_id=?",Long.class,quota));productMapping();jdbc.update("UPDATE pro_sn SET sn_status='COMPLETED' WHERE sn_id=1");var m=inTx(()->manifests.freeze(wo(1,0)));var header=materialPort().allocationManifest("000001",m.manifestId(),1);var row=CompletionAllocationIntegrity.read(materialPort(),"000001",m.manifestId(),1,header.contentHash()).get(0);assertEquals(consumed,row.allocationId());assertEquals(50001L,row.identityId());assertEquals(60001L,row.sourceStockId());assertNull(row.erpBatchId());assertEquals(80L,row.reservationId());assertEquals(81L,row.reservationLineId());}


    @Test void genericInventoryFactOnlyCreatesInboxAndAckWithoutASecondInventoryEffect() {
        sn(1,"COMPLETED");var e=erp("inventory","ERP_INVENTORY_POSTED",7,Map.of("postingGroupId","7","identityIds",List.of("10001")));
        assertTrue(inTx(()->deliver(e)));assertFalse(inTx(()->deliver(e)));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class).signum());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox WHERE event_id='inventory' AND status='APPLIED'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox WHERE event_type='TRACE_APPLIED'",Integer.class));
    }
    @Test void serialComponentReversalIsWholeUnitAndRetainsActualLotDeduction() {
        sn(1,"IN_PRODUCTION");String source=serialOnlySource();long quota=allocate("serial-quota",source,8,1);
        long consumed=inTx(()->materials.pick(pick("serial-use",8,quota,1,null),componentPolicy)).get(0);
        long entry=inTx(()->materials.allocateToSn("serial-assignment",consumed,1L,BigDecimal.ONE,30L,"op",1L,componentPolicy));
        assertThrows(RuntimeException.class,()->inTx(()->materials.reverse("half-reversal",entry,new BigDecimal("0.5"),"correction",1L)));
        long reversal=inTx(()->materials.reverse("whole-reversal",entry,BigDecimal.ONE,"correction",1L));
        assertEquals(reversal,inTx(()->materials.reverse("whole-reversal",entry,BigDecimal.ONE,"correction",1L)));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class).compareTo(BigDecimal.ONE));
    }
    @Test void deletedActualUseCannotDisappearFromFinalFrozenMaterialManifest() {
        productMapping();sn(1,"COMPLETED");long quota=allocate("component-map",source(1,10,0),8,10);
        inTx(()->materials.pick(pick("actual-use",8,quota,2,null),componentPolicy));jdbc.update("UPDATE lt_wo_consumption SET del_flag='1'");
        assertThrows(RuntimeException.class,()->inTx(()->manifests.freeze(wo(1,0))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_completion_material_allocation",Integer.class));
    }

    Map<String,Object> completionAckFixture(boolean allScrap) {
        productMapping();sn(1,allScrap?"SCRAPPED":"COMPLETED");var manifest=inTx(()->manifests.freeze(wo(allScrap?0:1,allScrap?1:0)));
        var origin=jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'");
        Map<String,Object> result=new LinkedHashMap<>();result.put("sourceEventId",origin.get("event_id"));result.put("manifestId",manifest.manifestId());result.put("revision",1);result.put("manifestHash",manifest.contentHash());
        return result;
    }
    void applyCompletionResult(Map<String,Object> result) {
        String source=result.get("sourceEventId").toString();String hash=jdbc.queryForObject("SELECT content_hash FROM mes_trace_outbox WHERE event_id=?",String.class,source);
        var event=acknowledgement("completion-result-ack",source,hash,events.encode(result));var handler=handler(events);proxiedJournal().accept(event,handler,handler.prepare(event));
    }
    void assertCompletionAckRejectedAtomically(Map<String,Object> result) {
        assertThrows(RuntimeException.class,()->applyCompletionResult(result));
        assertEquals("FROZEN",jdbc.queryForObject("SELECT status FROM pro_completion_manifest",String.class));
        assertEquals("NEW",jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox WHERE event_id='completion-result-ack'",Integer.class));
    }
    @Test void allScrapNoGoodOutputAckAppliesManifestWithoutInventingAReceiptOrReceivingASerial() {
        var result=completionAckFixture(true);result.put("outcome","NO_GOOD_OUTPUT");result.put("receiptId",null);result.put("postingGroupId",null);
        applyCompletionResult(result);applyCompletionResult(result);
        assertEquals("APPLIED",jdbc.queryForObject("SELECT status FROM pro_completion_manifest",String.class));
        assertEquals("APPLIED",jdbc.queryForObject("SELECT status FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",String.class));
        assertEquals("SCRAPPED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn WHERE handoff_status='RECEIVED'",Integer.class));
        assertEquals(events.encode(result),jdbc.queryForObject("SELECT result_payload FROM mes_trace_outbox WHERE event_type='COMPLETION_MANIFEST_FROZEN'",String.class));
        assertEquals(1,manifests.lines("000001",((Number)result.get("manifestId")).longValue(),1,null,500).rows().size());
    }
    @Test void allScrapAckCannotClaimAReceiptOrPostingGroup() {
        var result=completionAckFixture(true);result.put("outcome","NO_GOOD_OUTPUT");result.put("receiptId",71);result.put("postingGroupId",72);assertCompletionAckRejectedAtomically(result);
    }
    @Test void allScrapAckRequiresAnExplicitNoGoodOutcome() {
        var result=completionAckFixture(true);result.put("outcome","POSTED");result.put("receiptId",null);result.put("postingGroupId",null);assertCompletionAckRejectedAtomically(result);
    }
    @Test void allScrapLegacyAckWithMissingOutcomeIsRejected() {
        assertCompletionAckRejectedAtomically(completionAckFixture(true));
    }
    @Test void allScrapAckCannotHideAnInvalidIdAsAnUnparseableNull() {
        var result=completionAckFixture(true);result.put("outcome","NO_GOOD_OUTPUT");result.put("receiptId","invalid");result.put("postingGroupId",null);assertCompletionAckRejectedAtomically(result);
    }
    @Test void goodOutputAckCannotUseTheNoGoodOutcome() {
        var result=completionAckFixture(false);result.put("outcome","NO_GOOD_OUTPUT");result.put("receiptId",71);result.put("postingGroupId",72);assertCompletionAckRejectedAtomically(result);
    }
    @Test void explicitPostedAckReceivesGoodOutputAndAppliesManifest() {
        var result=completionAckFixture(false);result.put("outcome","POSTED");result.put("receiptId",71);result.put("postingGroupId",72);applyCompletionResult(result);
        assertEquals("RECEIVED",jdbc.queryForObject("SELECT handoff_status FROM pro_sn WHERE sn_id=1",String.class));
        assertEquals("APPLIED",jdbc.queryForObject("SELECT status FROM pro_completion_manifest",String.class));
    }

    @Test void acknowledgedAllScrapManifestIsSettledWithoutPretendingTheSerialWasReceived() {
        var result=completionAckFixture(true);var usage=new MesTrackingUsageService(jdbc,context);
        assertTrue(usage.hasUnsettledProduction("000001",100L));
        result.put("outcome","NO_GOOD_OUTPUT");result.put("receiptId",null);result.put("postingGroupId",null);applyCompletionResult(result);
        assertFalse(usage.hasUnsettledProduction("000001",100L));
        assertEquals("SCRAPPED",jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pro_sn WHERE handoff_status='RECEIVED'",Integer.class));
        jdbc.update("UPDATE pro_completion_manifest SET status='FROZEN'");assertTrue(usage.hasUnsettledProduction("000001",100L));
    }

    // Model the permissive legacy comparison independently from authenticated tenant formatting.
    void caseInsensitiveTenants(String... tables) {
        for(String table:tables)jdbc.execute("ALTER TABLE "+table+" ALTER COLUMN tenant_id VARCHAR_IGNORECASE(20)");
    }
    @Test void strictReadinessCannotBorrowAnotherTenantSourceAllocationThroughCaseFolding() {
        caseInsensitiveTenants("lt_wo_consumption","lt_lot_allocation");doReturn("tenant_case").when(context).tenant();
        jdbc.update("INSERT INTO lt_wo_consumption(consumption_id,tenant_id,wo_id,lot_id,material_id,consumed_qty,source_allocation_id,del_flag) VALUES(1,'tenant_case',10,8,200,1,81,'0')");
        jdbc.update("INSERT INTO lt_lot_allocation(allocation_id,tenant_id,lot_id,material_id,quantity) VALUES(81,'TENANT_CASE',8,200,1)");
        assertThrows(RuntimeException.class,()->new MesTrackingUsageService(jdbc,context).assertReadyForStrict("tenant_case",200L));
    }
    @Test void strictReadinessCannotBorrowAnotherTenantAllocationForAnActiveLegacyLot() {
        caseInsensitiveTenants("lt_lot","lt_lot_allocation");doReturn("tenant_case").when(context).tenant();
        jdbc.update("INSERT INTO lt_lot(lot_id,tenant_id,lot_code,material_id,quantity,consumed_qty,status,qc_status,del_flag) VALUES(80,'tenant_case','ACTIVE',200,1,0,'IN_STOCK','PASS','0')");
        jdbc.update("INSERT INTO lt_lot_allocation(allocation_id,tenant_id,lot_id,material_id,quantity) VALUES(81,'TENANT_CASE',80,200,1)");
        assertThrows(RuntimeException.class,()->new MesTrackingUsageService(jdbc,context).assertReadyForStrict("tenant_case",200L));
    }
    @Test void legacyBindingCannotBorrowAnotherTenantActualComponentLedger() {
        caseInsensitiveTenants("lt_sn_binding","lt_sn_consumption","pro_sn");doReturn("tenant_case").when(context).tenant();
        jdbc.update("INSERT INTO pro_sn(sn_id,tenant_id,identity_id,sn_code,mes_wo_id,material_id,sn_status,del_flag) VALUES(80,'tenant_case',10080,'SN80',10,100,'COMPLETED','0')");
        jdbc.update("INSERT INTO lt_sn_binding VALUES(81,'tenant_case',80,200,8)");
        jdbc.update("INSERT INTO lt_sn_consumption(entry_id,tenant_id,local_sn_id,component_material_id,component_lot_id,quantity,entry_type) VALUES(82,'TENANT_CASE',80,200,8,1,'CONSUME')");
        assertThrows(RuntimeException.class,()->new MesTrackingUsageService(jdbc,context).assertReadyForStrict("tenant_case",200L));
    }
    @Test void aScrappedSerialCannotBorrowAnotherTenantAppliedManifestToAppearSettled() {
        caseInsensitiveTenants("pro_sn","pro_completion_manifest","pro_completion_manifest_line","mes_trace_outbox");doReturn("tenant_case").when(context).tenant();
        jdbc.update("INSERT INTO pro_sn(sn_id,tenant_id,identity_id,sn_code,mes_wo_id,material_id,sn_status,handoff_status,del_flag) VALUES(80,'tenant_case',10080,'SN80',10,100,'SCRAPPED','FROZEN','0')");
        jdbc.update("INSERT INTO pro_completion_manifest(manifest_id,revision,tenant_id,mes_wo_id,erp_wo_id,material_id,status) VALUES(81,1,'TENANT_CASE',10,20,100,'APPLIED')");
        jdbc.update("INSERT INTO pro_completion_manifest_line(tenant_id,manifest_id,revision,line_no,local_sn_id,identity_id,material_id,result) VALUES('TENANT_CASE',81,1,1,80,10080,100,'SCRAP')");
        jdbc.update("INSERT INTO mes_trace_outbox(event_id,tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version,event_type,status) VALUES('foreign-manifest','TENANT_CASE','MES','COMPLETION_MANIFEST',81,1,'COMPLETION_MANIFEST_FROZEN','APPLIED')");
        assertTrue(new MesTrackingUsageService(jdbc,context).hasUnsettledProduction("tenant_case",100L));
    }
    @Test void tenantBinaryComparisonKeepsAllTwentyUtf8mb4CharactersAndTheFinalByte() {
        String first=new String(Character.toChars(0x1F600)),last=new String(Character.toChars(0x1F601));
        String tenant=first.repeat(20),other=first.repeat(19)+last;
        assertEquals(80,tenant.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(jdbc.queryForObject("SELECT CAST(? AS BINARY(80))=CAST(? AS BINARY(80))",Boolean.class,tenant,tenant));
        assertFalse(jdbc.queryForObject("SELECT CAST(? AS BINARY(80))=CAST(? AS BINARY(80))",Boolean.class,tenant,other));
    }
}
