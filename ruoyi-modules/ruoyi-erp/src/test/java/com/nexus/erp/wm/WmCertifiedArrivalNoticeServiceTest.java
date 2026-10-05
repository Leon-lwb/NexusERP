package com.nexus.erp.wm;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingAuthorizer;
import com.nexus.erp.wm.service.impl.WmServiceHelper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
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

/** 认证通知纵向入口的离线反例；全部依赖替身，不连接任何业务服务。 */
class WmCertifiedArrivalNoticeServiceTest {
    static final String T="000000";
    PuRequirementErpTransactionExecutor tx=mock(PuRequirementErpTransactionExecutor.class);
    PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
    PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
    PuCertifiedPurchaseOrderWorkflowService orders=mock(PuCertifiedPurchaseOrderWorkflowService.class);
    WmCertifiedReceiptPostingAuthorizer auth=mock(WmCertifiedReceiptPostingAuthorizer.class);
    WmCertifiedReceiptDraftMapper receipts=mock(WmCertifiedReceiptDraftMapper.class);
    AutoCodeUtil codes=mock(AutoCodeUtil.class);
    WmServiceHelper helper=mock(WmServiceHelper.class);
    Object mapper, service, notice;
    final List<String> writes=new ArrayList<>();
    List<String> engines=List.of("InnoDB","InnoDB","InnoDB");
    String nativeKind="decimal";
    BigDecimal received=BigDecimal.ZERO;
    String actualFlowState="APPROVED";
    String actualBusinessCode="AN001";
    boolean failDoingWrite=false;
    com.nexus.workflow.service.impl.WfMyBusinessServiceImpl flowBusiness=mock(com.nexus.workflow.service.impl.WfMyBusinessServiceImpl.class);
    MockedStatic<TenantHelper> tenants;
    Class<?> type(String name){try{return Class.forName("com.nexus.erp.wm."+name);}catch(ClassNotFoundException e){return fail("缺少纵向入口："+name);}}
    Object record(String name,Object... a){try{return Arrays.stream(type(name).getConstructors()).filter(c->c.getParameterCount()==a.length).findFirst().orElseThrow().newInstance(a);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object call(String name,Object... a){try{return Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals(name)&&m.getParameterCount()==a.length).findFirst().orElseThrow().invoke(service,a);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object field(Object v,String name){try{return v.getClass().getMethod(name).invoke(v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object notice(String tenant,String state,BigDecimal declared){return record("mapper.WmCertifiedArrivalNoticeMapper$Notice",tenant,81L,82L,"AN001",31L,"PO001",41L,9L,51L,"26",declared,received,state,"0","0");}
    Object request(BigDecimal q){return record("service.WmCertifiedArrivalNoticeService$Request","arrival-1",31L,2,41L,q,"独立通知",LocalDate.of(2026,10,4),"离线测试");}
    @BeforeEach void setup() throws Exception {
        type("service.WmCertifiedArrivalNoticeService");
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(T);
        when(tx.executeRepeatableRead(eq(T),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        when((DecisionActor)auth.getClass().getMethod("requireArrivalCreate").invoke(auth)).thenReturn(new DecisionActor(T,7L,"测试员",Set.of("wm:arrivalnotice:add")));
        when(receipts.findMaterial(T,31L,41L)).thenReturn(51L);
        when(receipts.lockSource(T,31L,41L)).thenReturn(new WmCertifiedReceiptDraftMapper.OrderSource(T,31L,"PO001",2,9L,"V009","供应商",41L,51L,"M001","物料","M12",26L,"件",4,new BigDecimal("10"),BigDecimal.ZERO,"APPROVED","0","0","1","0"));
        when(receipts.lockRootUnit(T,26L)).thenReturn(new WmCertifiedReceiptDraftMapper.RootUnit(26L,26L,1L,BigDecimal.ONE,4,"unit-v1",1L,"1","0"));
        when(codes.genSerialCode(eq("WM_ARRIVAL_NOTICE_CODE"),isNull())).thenReturn("AN001");
        org.springframework.test.util.ReflectionTestUtils.setField(helper,"autoCodeUtil",codes);
        org.springframework.test.util.ReflectionTestUtils.setField(helper,"wfMyBusinessServiceImpl",flowBusiness);
        when(auth.requireArrivalSubmit()).thenReturn(new DecisionActor(T,7L,"测试员",Set.of("wm:arrivalnotice:submit")));
        when(flowBusiness.getByDataId("81")).thenAnswer(i->flow(ActStatus.pass,"own-process"));
        notice=notice(T,"APPROVED",new BigDecimal("5"));
        mapper=mock(type("mapper.WmCertifiedArrivalNoticeMapper"),i->switch(i.getMethod().getName()){
            case "tableEngines" -> engines;
            case "lineCount" -> 1L;
            case "viewHeader" -> {var v=new com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo();v.setNoticeId(81L);v.setNoticeName("独立通知");yield v;}
            case "viewLines" -> List.of(new com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo());
            case "decimalColumns" -> List.of(record("mapper.WmCertifiedArrivalNoticeMapper$DecimalColumn","quantity_arrival",nativeKind,12,2),record("mapper.WmCertifiedArrivalNoticeMapper$DecimalColumn","received_qty",nativeKind,12,2));
            case "findCommand" -> null;
            case "lockNotice","discoverNotice" -> notice;
            case "pendingQuantities" -> List.of();
            case "lockNoticeLines" -> List.of(notice);
            case "insertHead","insertLine","insertCommand" -> {writes.add(i.getMethod().getName());yield 1;}
            case "updateStatus" -> {String next=i.getArgument(2);if(failDoingWrite&&"APPROVING".equals(next))throw new ServiceException("模拟 doing ERP 失写");writes.add("updateStatus");notice=notice(T,next,new BigDecimal("5"));yield 1;}
            case "addReceived" -> {writes.add("addReceived");received=received.add(i.getArgument(2));notice=notice(T,received.compareTo(new BigDecimal("5"))==0?"FINISHED":"PARTIAL_RECEIVED",new BigDecimal("5"));yield 1;}
            default -> null;
        });
        service=type("service.WmCertifiedArrivalNoticeService").getConstructors()[0].newInstance(mapper,receipts,tx,qualification,locks,orders,auth,helper);
        try{
            Class<?> port=Class.forName("com.nexus.erp.wm.service.WmCertifiedArrivalWorkflowPort");
            Object reader=mock(port,i->"read".equals(i.getMethod().getName())?record("service.WmCertifiedArrivalWorkflowPort$Proof",T,"81",actualBusinessCode,"own-process","own-definition","81","81",actualFlowState):null);
            org.springframework.test.util.ReflectionTestUtils.setField(service,"workflow",reader);
        }catch(ClassNotFoundException absent){ /* RED 时初版没有实际流程证明接线。 */ }

    }
    @AfterEach void close(){if(tenants!=null)tenants.close();}
    WfMyBusiness flow(String status,String process){var b=new WfMyBusiness();b.setDataId("81");b.setActStatus(status);b.setProcessInstanceId(process);b.setProcessDefinitionId("own-definition");b.setServiceImplName("wmCertifiedArrivalNoticeService");return b;}
    /** C1：错配办理污染 master 的 pass，目标通知自身引擎仍在审批。 */
    @Test void c1ForeignTaskPassCannotApproveStillRunningTarget(){notice=notice(T,"APPROVING",new BigDecimal("5"));actualFlowState="APPROVING";assertThrows(ServiceException.class,()->call("afterFlowHandle",flow(ActStatus.pass,"own-process")));assertTrue(writes.isEmpty());assertEquals("APPROVING",field(notice,"status"));}
    @Test void c1AnotherProcessEventIsRejectedEvenIfTargetAlreadyFinished(){notice=notice(T,"APPROVING",new BigDecimal("5"));assertThrows(ServiceException.class,()->call("afterFlowHandle",flow(ActStatus.pass,"foreign-process")));assertTrue(writes.isEmpty());}
    @Test void c1SynchronizeDoesNotTrustPollutedAssociationPass(){notice=notice(T,"APPROVING",new BigDecimal("5"));actualFlowState="APPROVING";call("synchronize",81L);assertEquals("APPROVING",field(notice,"status"));assertTrue(writes.isEmpty());}
    @Test void c1CompletedProcessWithAnotherBusinessCodeCannotApproveDraft(){notice=notice(T,"DRAFT",new BigDecimal("5"));actualBusinessCode="AN999";assertThrows(ServiceException.class,()->call("synchronize",81L));assertTrue(writes.isEmpty());assertEquals("DRAFT",field(notice,"status"));}
    /** I1：doing 失写后实际自身流程最终完成，不能删除/重建实例或任意批准草稿。 */
    @Test void i1OwnCompletedProcessRecoversLostDoingFromDraftIdempotently(){
        notice=notice(T,"DRAFT",new BigDecimal("5"));actualFlowState="APPROVING";failDoingWrite=true;
        assertThrows(ServiceException.class,()->call("afterFlowHandle",flow(ActStatus.doing,"own-process")));assertEquals("DRAFT",field(notice,"status"));
        failDoingWrite=false;actualFlowState="APPROVED";assertDoesNotThrow(()->call("synchronize",81L));assertEquals("APPROVED",field(notice,"status"));
        writes.clear();call("synchronize",81L);assertTrue(writes.isEmpty());assertEquals(BigDecimal.ZERO,received);verify(receipts,never()).insertHead(any(),any(),any(),any(),any());
    }
    @Test void i1DraftCannotRecoverApprovalFromRunningOwnProcess(){notice=notice(T,"DRAFT",new BigDecimal("5"));actualFlowState="APPROVING";assertThrows(ServiceException.class,()->call("afterFlowHandle",flow(ActStatus.pass,"own-process")));assertTrue(writes.isEmpty());}
    @Test void createsRealIndependentNoticeNotOrdinaryReceipt(){call("create",request(new BigDecimal("5")));assertEquals(List.of("insertHead","insertLine","insertCommand"),writes);verify(orders,atLeastOnce()).requireApprovedLocked(T,31L);verify(receipts,never()).insertHead(any(),any(),any(),any(),any());}
    @Test void unknownSupplierTermsApprovalCannotCreateNotice(){doThrow(new ServiceException("条款未证实")).when(orders).requireApprovedLocked(T,31L);assertThrows(ServiceException.class,()->call("create",request(BigDecimal.ONE)));assertTrue(writes.isEmpty());}
    @Test void doesNotRoundNativeNoticeQuantity(){assertThrows(ServiceException.class,()->call("create",request(new BigDecimal("1.001"))));assertTrue(writes.isEmpty());}
    @Test void cannotDeclareMoreThanActualOrderBalance(){assertThrows(ServiceException.class,()->call("create",request(new BigDecimal("11"))));assertTrue(writes.isEmpty());}
    @Test void bindingRequiresApproval(){notice=notice(T,"DRAFT",new BigDecimal("5"));assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,41L,51L,"26",BigDecimal.ONE,false));}
    @Test void bindingRejectsForeignTenant(){notice=notice("000001","APPROVED",new BigDecimal("5"));assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,41L,51L,"26",BigDecimal.ONE,false));}
    @Test void bindingRejectsAnotherOriginalPurchaseLine(){assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,42L,51L,"26",BigDecimal.ONE,false));}
    @Test void bindingCannotOverReceiveDeclaredNotice(){assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,41L,51L,"26",new BigDecimal("6"),false));}
    @Test void nativePrecisionAppliesAlsoToPartialReceipt(){assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,41L,51L,"26",new BigDecimal("0.001"),false));}
    @Test void approvedCallbackDoesNotCreateOrdinaryReceipt(){notice=notice(T,"APPROVING",new BigDecimal("5"));var b=flow(ActStatus.pass,"own-process");call("afterFlowHandle",b);assertEquals(List.of("updateStatus"),writes);verify(receipts,never()).insertHead(any(),any(),any(),any(),any());}
    @Test void nonTransactionalNoticeCannotBindOrPartiallyCommit(){engines=List.of("InnoDB","MyISAM","InnoDB");assertThrows(ServiceException.class,()->call("requireBindingLocked",T,81L,82L,31L,41L,51L,"26",BigDecimal.ONE,false));}
    @Test void nonTransactionalNoticeCannotAccumulate(){engines=List.of("InnoDB","MyISAM","InnoDB");assertThrows(ServiceException.class,()->call("receiveLocked",T,81L,82L,BigDecimal.ONE));assertTrue(writes.isEmpty());}
    @Test void oldDoubleColumnsRefuseFractionsRatherThanRound(){nativeKind="double";assertThrows(ServiceException.class,()->call("create",request(new BigDecimal("1.25"))));assertTrue(writes.isEmpty());}
    @Test void oldDoubleColumnsAllowOnlyLosslessIntegers(){nativeKind="double";assertDoesNotThrow(()->call("create",request(BigDecimal.ONE)));}
    @Test void workflowKeepsOriginalNoticeViewInsteadOfInventingFormShape(){assertInstanceOf(com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo.class,call("getBusinessDataById","81"));}
    @Test void masterRollbackCannotApplyErpApprovalBeforeCommit(){
        var manager=org.springframework.transaction.support.TransactionSynchronizationManager.class;
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try{var b=flow(ActStatus.pass,"own-process");call("afterFlowHandle",b);assertTrue(writes.isEmpty());assertEquals(1,org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());verifyNoInteractions(tx);}
        finally{org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
    }
    @Test void partialReceiptCannotFinishNotice(){call("receiveLocked",T,81L,82L,new BigDecimal("2"));assertEquals(new BigDecimal("2"),received);assertEquals(List.of("addReceived","updateStatus"),writes);}
    @Test void allReceivedFinishesNotice(){call("receiveLocked",T,81L,82L,new BigDecimal("5"));assertEquals("FINISHED",field(notice,"status"));}
}
