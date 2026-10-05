package com.nexus.erp.wm;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingAuthorizer;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 明确订单行创建草稿的离线事务反例，不连接数据库、不代替实际审批和IQC。 */
class WmCertifiedReceiptDraftServiceTest {
    static final String T="000000";
    PuRequirementErpTransactionExecutor tx=mock(PuRequirementErpTransactionExecutor.class);
    PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
    PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
    PuCertifiedPurchaseOrderWorkflowService orders=mock(PuCertifiedPurchaseOrderWorkflowService.class);
    WmCertifiedReceiptPostingAuthorizer auth=mock(WmCertifiedReceiptPostingAuthorizer.class);
    AutoCodeUtil codes=mock(AutoCodeUtil.class);
    MockedStatic<TenantHelper> tenants;
    Object mapper,service,source,old; List<Object> pending=new ArrayList<>(); boolean sourceInvalid=false;
    final List<String> writes=new ArrayList<>(); final List<String> trace=new ArrayList<>(); Object root; final LocalDate day=LocalDate.of(2026,10,4);
    Class<?> definition(String name){try{return Class.forName(name);}catch(ClassNotFoundException e){throw new AssertionError("缺少实际认证订单收货创建入口："+name,e);}}
    Object record(String name,Object... values){try{return Arrays.stream(definition("com.nexus.erp.wm."+name).getConstructors()).filter(c->c.getParameterCount()==values.length).findFirst().orElseThrow().newInstance(values);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object command(BigDecimal qty){return record("service.WmCertifiedReceiptDraftService$Request","local-receipt-1",31L,2,41L,qty,1L,2L,3L,day,"本地测试实际到货");}
    Object field(Object value,String name){try{return value.getClass().getMethod(name).invoke(value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object create(Object request){try{return service.getClass().getMethod("create",request.getClass()).invoke(service,request);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @BeforeEach void setUp() throws Exception {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(T);
        when(tx.executeRepeatableRead(eq(T),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        when(auth.requireCreate()).thenReturn(new DecisionActor(T,7L,"测试收货员",Set.of("wm:receive:add")));
        when(codes.genSerialCode(eq("WM_MATERIAL_RECEIVE_CODE"),isNull())).thenReturn("WMR202610040001");
        Class<?> mapperType=definition("com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper");
        root=record("mapper.WmCertifiedReceiptDraftMapper$RootUnit",26L,26L,1L,BigDecimal.ONE,0,"unit-v1",1L,"1","0");
        source=record("mapper.WmCertifiedReceiptDraftMapper$OrderSource",T,31L,"CGDD202610040001",2,9L,"V00009","测试供应商",41L,51L,"M202610040001","测试原材料","M12",26L,"件",0,new BigDecimal("10.0000"),new BigDecimal("2.0000"),"APPROVED","0","0","1","0");
        Object storage=record("mapper.WmCertifiedReceiptDraftMapper$Storage",T,1L,"WH001","原料仓",2L,"A002","原料区",3L,"L003","原料位","1","0","1","0","1","0");
        mapper=mock(mapperType,i->{String method=i.getMethod().getName();
            trace.add(method);return switch(method){
                case "findMaterial" -> 51L;
                case "schemaEngines" -> List.of("InnoDB","InnoDB","InnoDB","InnoDB","InnoDB");
                case "commandUniqueColumns" -> List.of("tenant_id","request_key");
                case "nativeQuantityTypes" -> List.of("decimal(18,4)");
                case "findCommand","lockCommand" -> old;
                case "lockRootUnit" -> root;
                case "lockSource" -> sourceInvalid?null:source;
                case "lockStorage" -> storage;
                case "databaseDate" -> day.toString();
                case "lockPending" -> pending;
                case "insertHead","insertLine","insertCommand" -> {writes.add(method);yield 1;}
                case "readReceipt" -> i.getArgument(0);
                default -> null;
            };});
        service=definition("com.nexus.erp.wm.service.WmCertifiedReceiptDraftService").getConstructors()[0].newInstance(auth,tx,qualification,locks,orders,mapper,codes);
    }
    @AfterEach void close(){if(tenants!=null)tenants.close();}
    @Test void createsOnlyDraftWithExplicitOrderLineAndNativeUnit(){Object result=create(command(new BigDecimal("3.0000")));assertEquals("DRAFT_CREATED",field(result,"status"));assertEquals(41L,field(result,"purchaseLineId"));assertEquals("26",field(result,"baseUomId"));assertFalse((boolean)field(result,"replayed"));assertEquals(List.of("insertHead","insertLine","insertCommand"),writes);}
    @Test void approvedOrderMustBeVerifiedBeforeAnyBusinessWrite(){doThrow(new ServiceException("订单未批准")).when(orders).requireApprovedLocked(T,31L);assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));assertTrue(writes.isEmpty());}
    @Test void guardsExistingMaterialBeforeApprovedOrderCurrentRead(){create(command(BigDecimal.ONE));var order=inOrder(locks,orders);order.verify(locks).lockExisting(T,List.of(51L));order.verify(orders).requireApprovedLocked(T,31L);}
    @Test void receiptQuantityMustFitRemainingActualOrderBalance(){assertThrows(ServiceException.class,()->create(command(new BigDecimal("9.0000"))));assertTrue(writes.isEmpty());}
    @Test void existingDraftCommitmentsCannotMakeNewDraftOverReceive(){pending.add(record("mapper.WmCertifiedReceiptDraftMapper$Pending",new BigDecimal("6.0000"),"DRAFT","0","0"));assertThrows(ServiceException.class,()->create(command(new BigDecimal("3.0000"))));assertTrue(writes.isEmpty());}
    @Test void quantityCannotBeRoundedToUnitPrecision(){assertThrows(ServiceException.class,()->create(command(new BigDecimal("1.2500"))));assertTrue(writes.isEmpty());}
    @Test void zeroNegativeOrExcessScaleRejectedBeforeTransaction(){for(var quantity:List.of(BigDecimal.ZERO,new BigDecimal("-1"),new BigDecimal("1.00001"))){assertThrows(ServiceException.class,()->create(command(quantity)));}verifyNoInteractions(tx);assertTrue(writes.isEmpty());}
    @Test void missingSourceRejectsWithoutCreatingAnyDraft(){sourceInvalid=true;assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));assertTrue(writes.isEmpty());}
    @Test void exactCommittedRequestReplaysWithoutNumberOrNewWrites(){Object first=create(command(BigDecimal.ONE));old=record("mapper.WmCertifiedReceiptDraftMapper$Command",T,"local-receipt-1",field(first,"requestHash"),7L,31L,41L,field(first,"receiveId"),field(first,"lineId"),"WMR202610040001",51L,"26",BigDecimal.ONE,day.toString(),1L,2L,3L);writes.clear();clearInvocations(codes);Object replay=create(command(new BigDecimal("1.0000")));assertTrue((boolean)field(replay,"replayed"));assertTrue(writes.isEmpty());verifyNoInteractions(codes);}
    @Test void reusedRequestKeyWithDifferentQuantityCannotCreateSecondReceipt(){Object first=create(command(BigDecimal.ONE));old=record("mapper.WmCertifiedReceiptDraftMapper$Command",T,"local-receipt-1",field(first,"requestHash"),7L,31L,41L,field(first,"receiveId"),field(first,"lineId"),"WMR202610040001",51L,"26",BigDecimal.ONE,day.toString(),1L,2L,3L);writes.clear();assertThrows(ServiceException.class,()->create(command(new BigDecimal("2"))));assertTrue(writes.isEmpty());}
    @Test void finishedReceiptAwaitingMqStillReducesRemainingBalance(){pending.add(record("mapper.WmCertifiedReceiptDraftMapper$Pending",new BigDecimal("7.0000"),"FINISHED","0","0"));assertThrows(ServiceException.class,()->create(command(new BigDecimal("4"))));assertTrue(writes.isEmpty());}
    @Test void failedAtomicCommandWriteCannotReportCreated(){Object bad=mock(definition("com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper"),i->{try{Object value=i.getMethod().invoke(mapper,i.getArguments());return "insertCommand".equals(i.getMethod().getName())?0:value;}catch(InvocationTargetException e){throw e.getCause();}});try{service=definition("com.nexus.erp.wm.service.WmCertifiedReceiptDraftService").getConstructors()[0].newInstance(auth,tx,qualification,locks,orders,bad,codes);}catch(ReflectiveOperationException e){throw new AssertionError(e);}assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));}
    @Test void missingCommandNeverTakesLongLivedGapLock(){create(command(BigDecimal.ONE));assertFalse(trace.contains("lockCommand"));}
    @Test void currentDisabledRootUnitCannotCreateDraft(){root=record("mapper.WmCertifiedReceiptDraftMapper$RootUnit",26L,26L,1L,BigDecimal.ONE,0,"unit-v1",1L,"0","0");assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));assertTrue(writes.isEmpty());}
    @Test void currentDerivedOrDeletedUnitCannotCreateDraft(){for(var r:List.of(record("mapper.WmCertifiedReceiptDraftMapper$RootUnit",26L,27L,0L,BigDecimal.ONE,0,"unit-v1",1L,"1","0"),record("mapper.WmCertifiedReceiptDraftMapper$RootUnit",26L,26L,1L,BigDecimal.ONE,0,"unit-v1",1L,"1","1"))){root=r;assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));}assertTrue(writes.isEmpty());}
    @Test void unknownPendingStateNeverTreatedAsZeroBalance(){pending.add(record("mapper.WmCertifiedReceiptDraftMapper$Pending",BigDecimal.ONE,"UNKNOWN","0","0"));assertThrows(ServiceException.class,()->create(command(BigDecimal.ONE)));assertTrue(writes.isEmpty());}

    Object linked(BigDecimal q,Long n,Long l){return record("service.WmCertifiedReceiptDraftService$Request","local-receipt-1",31L,2,41L,q,1L,2L,3L,day,"本地测试实际到货",n,l);}
    com.nexus.erp.wm.service.WmCertifiedArrivalNoticeService attachNotice(){
        var a=mock(com.nexus.erp.wm.service.WmCertifiedArrivalNoticeService.class);
        var n=new com.nexus.erp.wm.mapper.WmCertifiedArrivalNoticeMapper.Notice(T,81L,82L,"AN001",31L,"CGDD202610040001",41L,9L,51L,"26",new BigDecimal("5"),BigDecimal.ZERO,"APPROVED","0","0");
        when(a.requireBindingLocked(eq(T),eq(81L),eq(82L),eq(31L),eq(41L),eq(51L),eq("26"),any(),anyBoolean())).thenReturn(n);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"arrivals",a);return a;
    }
    @Test void nullReferencesKeepExactOriginalCanonicalHash(){
        Object first=create(command(BigDecimal.ONE));assertEquals("8d7fc9912f38ccaa8f0d579c48a0bc4a78679bcff13935d2663e207b510c5d5e",field(first,"requestHash"));
        Object explicitNull=create(linked(BigDecimal.ONE,null,null));assertEquals(field(first,"requestHash"),field(explicitNull,"requestHash"));
    }
    @Test void halfNoticeReferenceRejectedBeforeAnyTransaction(){assertThrows(ServiceException.class,()->create(linked(BigDecimal.ONE,81L,null)));verifyNoInteractions(tx);assertTrue(writes.isEmpty());}
    @Test void referenceIsPersistedInCommandAndChangesHash(){
        var a=attachNotice();var captured=new java.util.concurrent.atomic.AtomicReference<com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper.Command>();
        var typed=(com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper)mapper;
        doAnswer(i->{captured.set(i.getArgument(0));writes.add("insertCommand");return 1;}).when(typed).insertCommand(any());
        Object normal=create(command(BigDecimal.ONE));writes.clear();Object bound=create(linked(BigDecimal.ONE,81L,82L));
        assertNotEquals(field(normal,"requestHash"),field(bound,"requestHash"));assertEquals(81L,captured.get().noticeId());assertEquals(82L,captured.get().noticeLineId());
        verify(typed).insertHead(any(),any(),any(),any(),eq("AN001"));
        verify(a,times(2)).requireBindingLocked(eq(T),eq(81L),eq(82L),eq(31L),eq(41L),eq(51L),eq("26"),any(),eq(false));
    }
    @Test void unapprovedNoticeCannotCreateAnyReceiptRows(){var a=attachNotice();doThrow(new ServiceException("通知未批准")).when(a).requireBindingLocked(eq(T),eq(81L),eq(82L),any(),any(),any(),any(),any(),eq(false));assertThrows(ServiceException.class,()->create(linked(BigDecimal.ONE,81L,82L)));assertTrue(writes.isEmpty());}
    @Test void originalBoundRequestReplaysWithoutSecondBusinessWrite(){
        var a=attachNotice();var typed=(com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper)mapper;
        doAnswer(i->{old=i.getArgument(0);writes.add("insertCommand");return 1;}).when(typed).insertCommand(any());
        Object first=create(linked(BigDecimal.ONE,81L,82L));writes.clear();Object replay=create(linked(new BigDecimal("1.0000"),81L,82L));
        assertTrue((boolean)field(replay,"replayed"));assertEquals(field(first,"requestHash"),field(replay,"requestHash"));assertTrue(writes.isEmpty());
        verify(a).requireBindingLocked(eq(T),eq(81L),eq(82L),any(),any(),any(),any(),any(),eq(true));
    }
    @Test void originalRequestKeyCannotBeMovedToAnotherNotice(){attachNotice();var typed=(com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper)mapper;doAnswer(i->{old=i.getArgument(0);return 1;}).when(typed).insertCommand(any());create(linked(BigDecimal.ONE,81L,82L));writes.clear();assertThrows(ServiceException.class,()->create(linked(BigDecimal.ONE,91L,92L)));assertTrue(writes.isEmpty());}
}
