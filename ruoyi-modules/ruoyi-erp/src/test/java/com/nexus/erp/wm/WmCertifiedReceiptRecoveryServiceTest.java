package com.nexus.erp.wm;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService.*;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 查询只组合两类真正永久结果；替身不签发运行加载、数据库或来源认证。 */
class WmCertifiedReceiptRecoveryServiceTest {
    static final String T="000000";static final Long ID=101L,ORDER=301L,MATERIAL=201L;
    PuRequirementErpTransactionExecutor tx;
    PuRequirementErpTransactionQualification qualification;
    WmCertifiedReceiptPostingAuthorizer auth;
    WmCertifiedReceiptPostingService posting;
    PuCertifiedPurchaseReceiptBatchService batch;
    MockedStatic<TenantHelper> tenants;
    Optional<BatchReceipt> consumed;
    DecisionActor actor;
    @BeforeEach void setup(){
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(T);
        tx=mock(PuRequirementErpTransactionExecutor.class);qualification=mock(PuRequirementErpTransactionQualification.class);
        actor=new DecisionActor(T,9L,"恢复核验员",Set.of("wm:receive:query"));
        auth=mock(WmCertifiedReceiptPostingAuthorizer.class,i->{if(i.getMethod().getName().equals("requireQuery"))return actor;return RETURNS_DEFAULTS.answer(i);});
        posting=mock(WmCertifiedReceiptPostingService.class,i->{if(i.getMethod().getName().equals("readPostedLocked"))return snapshot();return RETURNS_DEFAULTS.answer(i);});
        consumed=Optional.empty();batch=mock(PuCertifiedPurchaseReceiptBatchService.class,i->{if(i.getMethod().getName().equals("readCommittedReceiptLocked"))return consumed;return RETURNS_DEFAULTS.answer(i);});
        when(tx.executeRepeatableRead(eq(T),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
    }
    @AfterEach void close(){tenants.close();}
    Class<?> definition(String name){try{return Class.forName(name);}catch(ClassNotFoundException e){fail("缺少真正永久恢复服务/查询入口："+name);return null;}}
    Object snapshot(){try{return definition("com.nexus.erp.wm.service.WmCertifiedReceiptPostingService$PostedSnapshot")
        .getConstructors()[0].newInstance(ORDER,new WmCertifiedReceiptPostingService.Result(ID,MATERIAL,new BigDecimal("4.0000"),"a".repeat(64),true,false),"2026-10-04 10:00:00","ENQUEUED");}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object service(){try{return definition("com.nexus.erp.wm.service.WmCertifiedReceiptRecoveryService")
        .getConstructors()[0].newInstance(auth,tx,qualification,posting,batch);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object read(Long id){try{return service().getClass().getMethod("read",Long.class).invoke(service(),id);}
        catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object field(Object value,String name){try{return value.getClass().getMethod(name).invoke(value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    BatchReceipt receipt(String tenant,Long receive,Long order,boolean replay,List<LineReceipt> lines){return new BatchReceipt(tenant,receive,order,true,lines,replay);}
    List<LineReceipt> events(){return List.of(new LineReceipt(701L,new EventReceipt(1L,2L,Operation.RECEIPT_POSTED,"ORDER","ORDER",new BigDecimal("4.0000"),BigDecimal.ZERO,3L,4L,5L,true)));}
    @Test void unconsumedPostingReportsPendingRatherThanInferringClosureFromEnqueued(){
        var view=read(ID);assertEquals("PENDING",field(view,"consumptionState").toString());assertEquals("ENQUEUED",field(view,"notificationStatus"));
        assertEquals(List.of(),field(view,"consumptionEvents"));verify(tx,times(1)).executeRepeatableRead(eq(T),any());
    }
    @Test void allConsumptionEvidenceIsReturnedFromTheSameErpOwnedTransaction(){
        consumed=Optional.of(receipt(T,ID,ORDER,true,events()));var view=read(ID);
        assertEquals("CONSUMED",field(view,"consumptionState").toString());assertEquals(events(),field(view,"consumptionEvents"));
        assertEquals(new BigDecimal("4.0000"),field(view,"quantity"));
        verify(qualification,atLeast(2)).requireSourceChainTransaction();verify(posting,never()).post(anyLong());verify(posting,never()).shelf(anyLong());
        verify(batch,never()).consumeReceipt(anyString(),anyLong());
    }
    @Test void invalidOrForeignActorCannotOpenARecoveryTransaction(){
        actor=new DecisionActor("000001",9L,"另一租户",Set.of("wm:receive:query"));
        assertThrows(ServiceException.class,()->read(ID));verifyNoInteractions(tx,posting,batch);
    }
    @Test void nullReceiveIdentityCannotOpenARecoveryTransaction(){assertThrows(ServiceException.class,()->read(null));verifyNoInteractions(tx,posting,batch);}
    @Test void differentTenantOrderOrReceiptInConsumedResultRejectsWholeRead(){
        for(var r:List.of(receipt("000001",ID,ORDER,true,events()),receipt(T,102L,ORDER,true,events()),receipt(T,ID,302L,true,events()),receipt(T,ID,ORDER,false,events()),receipt(T,ID,ORDER,true,List.of()))){
            consumed=Optional.of(r);assertThrows(ServiceException.class,()->read(ID));
        }
    }
    @Test void readonlyControllerHasQueryPermissionAndNoPostOrExecuteInvocation() throws Exception {
        var type=definition("com.nexus.erp.wm.controller.WmCertifiedReceiptRecoveryController");
        var method=type.getMethod("recovery",Long.class);assertEquals("wm:receive:query",method.getAnnotation(SaCheckPermission.class).value()[0]);
        assertEquals("/recovery/{receiveId}",method.getAnnotation(GetMapping.class).value()[0]);
    }
}
