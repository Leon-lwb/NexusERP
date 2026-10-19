package com.nexus.web.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.IdentityTraceReadPort;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialIdentityTraceServiceTest {
    MockedStatic<TenantHelper> tenant;MockedStatic<StpUtil> auth;
    @BeforeEach void setup(){tenant=mockStatic(TenantHelper.class);tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn("000001");auth=mockStatic(StpUtil.class);auth.when(()->StpUtil.hasPermission("wm:trace:query")).thenReturn(true);auth.when(()->StpUtil.hasPermission("mes:pro:trace:query")).thenReturn(true);}
    @AfterEach void close(){auth.close();tenant.close();}
    IdentityTraceReadPort reader(String module,Map<Long,List<Long>> facts,Map<Long,Set<Long>> batches){return new IdentityTraceReadPort(){
        public String sourceModule(){return module;}
        public IdentityTracePart identity(String t,Long id,String s,String c,int l){return new IdentityTracePart(module,id,null,"SN"+id,"IDENTITY",Map.of(),List.of());}
        public TracePage<Long> affectedByBatch(String t,Long batch,long after,int limit){var ids=facts.getOrDefault(batch,List.of()).stream().filter(id->id>after).sorted().limit(limit+1L).toList();boolean more=ids.size()>limit;var rows=ids.stream().limit(limit).toList();return new TracePage<>(rows,more?rows.get(rows.size()-1).toString():null,more);}
        public Set<Long> batchesForIdentities(String t,List<Long> identities){var result=new HashSet<Long>();for(Long id:identities)result.addAll(batches.getOrDefault(id,Set.of()));return result;}
    };}
    @Test void recallMergesMixedBatchesAndFollowsActualConsumptionToNextOutputBatch(){
        var erp=reader("ERP",Map.of(10L,List.of(1L,2L),20L,List.of(4L)),Map.of(2L,Set.of(20L)));
        var mes=reader("MES",Map.of(10L,List.of(2L,3L),20L,List.of(5L)),Map.of());
        var result=new MaterialIdentityTraceService(List.of(erp,mes)).recall(10L,0,500,10);
        assertEquals(List.of(Map.of("identityId","1"),Map.of("identityId","2"),Map.of("identityId","3"),Map.of("identityId","4","scope","BATCH_POSSIBLE"),Map.of("identityId","5","scope","BATCH_POSSIBLE")),result.get("rows"));assertEquals(false,result.get("truncated"));
    }
    @Test void moreThanFiveHundredAffectedUnitsCanBeRetrievedAcrossPages(){
        var erp=reader("ERP",Map.of(10L,LongStream.rangeClosed(1,1201).boxed().toList()),Map.of());var service=new MaterialIdentityTraceService(List.of(erp));
        var first=service.recall(10L,0,500,10);assertEquals(true,first.get("hasMore"));assertEquals("500",first.get("nextCursor"));
        var last=service.recall(10L,1000,500,10);assertEquals(201,((List<?>)last.get("rows")).size());assertEquals(false,last.get("hasMore"));
    }
    @Test void depthLimitIsExplicitAndDoesNotClaimACompleteRecall(){
        var erp=reader("ERP",Map.of(10L,List.of(1L),20L,List.of(2L)),Map.of(1L,Set.of(20L)));
        var result=new MaterialIdentityTraceService(List.of(erp)).recall(10L,0,500,1);assertEquals(true,result.get("truncatedByDepth"));assertEquals(true,result.get("truncated"));
    }
    @Test void identityTraceDoesNotGrantQualityOrValuationPermissions(){
        var mes=new IdentityTraceReadPort(){
            public String sourceModule(){return "MES";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){return new IdentityTracePart("MES",id,2L,"SN","IDENTITY",Map.of("TEST",new TracePage<>(List.of(Map.of("result","PRIVATE_TEST")),null,false),"IPQC_WORK_ORDER",new TracePage<>(List.of(Map.of("inspect_result","PRIVATE_QC")),null,false)),List.of());}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(List.of(),null,false);}
        };
        var erp=new IdentityTraceReadPort(){
            public String sourceModule(){return "ERP";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){return new IdentityTracePart("ERP",id,null,"SN","IDENTITY",Map.of("STOCK",new TracePage<>(List.of(Map.of("stock_id","1","stock_quantity","1.0000","unit_cost","PRIVATE_COST")),null,false)),List.of());}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(List.of(),null,false);}
        };
        var result=new MaterialIdentityTraceService(List.of(erp,mes)).identity(1L,null,null,null,500);
        String content=result.toString();assertFalse(content.contains("PRIVATE_TEST"));assertFalse(content.contains("PRIVATE_QC"));assertFalse(content.contains("PRIVATE_COST"));assertTrue(content.contains("stock_quantity=1.0000"));
    }
    @Test void batchRecallContinuesThroughASerialOnlyIntermediateWithoutABatch(){
        var mes=new IdentityTraceReadPort(){
            public String sourceModule(){return "MES";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(b==10L && a==0?List.of(100L):List.of(),null,false);}
            public TracePage<Long> affectedByIdentity(String t,Long id,long a,int l){return new TracePage<>(id==100L && a==0?List.of(200L):List.of(),null,false);}
        };
        var result=new MaterialIdentityTraceService(List.of(mes)).recall(10L,0,500,10);
        assertEquals(List.of(Map.of("identityId","100"),Map.of("identityId","200")),result.get("rows"));
    }

    @Test void unallocatedActualUseReturnsWorkOrderScopeAndRealBatchOutputsWithoutInventingUnitLinks(){
        var mes=new IdentityTraceReadPort(){
            public String sourceModule(){return "MES";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(b==20L && a==0?List.of(300L):List.of(),null,false);}
            public TracePage<WorkOrderTraceImpact> unallocatedByBatch(String t,Long b,long a,int l){return new TracePage<>(b==10L && a==0?List.of(new WorkOrderTraceImpact("MES",101L,7L,8L,new java.math.BigDecimal("2"),java.math.BigDecimal.ONE)):List.of(),null,false);}
            public TracePage<Long> productBatchesByWorkOrder(String t,Long wo,long a,int l){return new TracePage<>(wo==7L && a==0?List.of(20L):List.of(),null,false);}
        };
        var result=new MaterialIdentityTraceService(List.of(mes)).recall(10L,0,500,10);
        assertEquals(List.of(Map.of("identityId","300","scope","WORK_ORDER_POSSIBLE")),result.get("rows"));
        var scopes=(TracePage<?>)result.get("scopeImpacts");assertNotNull(scopes);assertEquals(1,scopes.rows().size());
        assertFalse(((List<?>)result.get("evidenceGaps")).isEmpty());
    }

    @Test void aSingleComponentIdentityNeverExpandsToUnrelatedUnitsInItsBatch(){
        var erp=reader("ERP",Map.of(10L,List.of(1L,2L)),Map.of(1L,Set.of(10L)));
        var result=new MaterialIdentityTraceService(List.of(erp)).whereUsed(1L,0,500,10);
        assertEquals(List.of(),result.get("rows"));
    }
    @Test void unallocatedWorkOrderEvidenceUsesAnIndependentBoundCursor(){
        var mes=new IdentityTraceReadPort(){
            public String sourceModule(){return "MES";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(List.of(),null,false);}
            public TracePage<WorkOrderTraceImpact> unallocatedByBatch(String t,Long b,long a,int l){
                var all=LongStream.rangeClosed(1,501).filter(id->id>a).limit(l+1L).mapToObj(id->new WorkOrderTraceImpact("MES",id,id,8L,java.math.BigDecimal.ONE,java.math.BigDecimal.ZERO)).toList();
                boolean more=all.size()>l;var rows=all.stream().limit(l).toList();return new TracePage<>(rows,more?rows.get(rows.size()-1).consumptionId().toString():null,more);
            }
        };
        var service=new MaterialIdentityTraceService(List.of(mes));var first=service.recall(10L,0,500,10);
        var scopes=(TracePage<?>)first.get("scopeImpacts");assertTrue(scopes.hasMore());assertEquals(500,scopes.rows().size());
        var last=(TracePage<?>)service.recall(10L,0,500,10,scopes.nextCursor()).get("scopeImpacts");assertEquals(1,last.rows().size());assertFalse(last.hasMore());
        assertThrows(RuntimeException.class,()->service.recall(11L,0,500,10,scopes.nextCursor()));
    }

    @Test void aBatchWithoutSerialsStillExposesItsOwnStockAndCustomerMovementsWithCostPermissionsIntact(){
        var erp=new IdentityTraceReadPort(){
            public String sourceModule(){return "ERP";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}
            public BatchTracePart batch(String t,Long id,String section,String cursor,int l){return new BatchTracePart("ERP",id,"BATCH",Map.of("STOCK",new TracePage<>(List.of(Map.of("stock_id","55","stock_quantity","20.0000","unit_cost","PRIVATE_COST")),null,false),"MOVEMENTS",new TracePage<>(List.of(Map.of("tx_id","56","customer_name","Confirmed customer")),null,false)),List.of());}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(List.of(),null,false);}
        };
        var result=new MaterialIdentityTraceService(List.of(erp)).recall(10L,0,500,10);
        assertEquals(List.of(),result.get("rows"));assertNotNull(result.get("parts"));
        assertTrue(result.get("parts").toString().contains("20.0000"));assertTrue(result.get("parts").toString().contains("Confirmed customer"));assertFalse(result.get("parts").toString().contains("PRIVATE_COST"));
    }

    @Test void moreThanFiveHundredBatchOnlyOutputsHaveAnIndependentBoundCursor(){
        var mes=new IdentityTraceReadPort(){
            public String sourceModule(){return "MES";}
            public IdentityTracePart identity(String t,Long id,String s,String c,int l){throw new UnsupportedOperationException();}
            public TracePage<Long> affectedByBatch(String t,Long b,long a,int l){return new TracePage<>(List.of(),null,false);}
            public TracePage<WorkOrderTraceImpact> unallocatedByBatch(String t,Long b,long a,int l){return new TracePage<>(b==10L && a==0?List.of(new WorkOrderTraceImpact("MES",1L,7L,8L,java.math.BigDecimal.ONE,java.math.BigDecimal.ZERO)):List.of(),null,false);}
            public TracePage<Long> productBatchesByWorkOrder(String t,Long wo,long a,int l){
                var all=LongStream.rangeClosed(11,511).filter(id->id>a).limit(l+1L).boxed().toList();boolean more=all.size()>l;var rows=all.stream().limit(l).toList();return new TracePage<>(rows,more?rows.get(rows.size()-1).toString():null,more);
            }
        };
        var service=new MaterialIdentityTraceService(List.of(mes));var first=(TracePage<?>)service.recall(10L,0,500,10).get("batchImpacts");assertEquals(500,first.rows().size());assertTrue(first.hasMore());
        var last=(TracePage<?>)service.recall(10L,0,500,10,null,first.nextCursor()).get("batchImpacts");assertEquals(List.of(Map.of("batchId","511","scope","WORK_ORDER_POSSIBLE")),last.rows());assertFalse(last.hasMore());
        assertThrows(RuntimeException.class,()->service.recall(20L,0,500,10,null,first.nextCursor()));
    }

}
