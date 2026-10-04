package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.PostedTx;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实过账服务的永久恢复分支；替身基础设施不能替代实际业务验收。 */
class WmCertifiedReceiptRecoveryPostingTest {
    WmCertifiedReceiptPostingServiceTest f=new WmCertifiedReceiptPostingServiceTest();
    @BeforeEach void setup(){f.setup();when(f.locks.lockExisting(f.TENANT,List.of(f.MATERIAL))).thenReturn(Map.of(f.MATERIAL,0));}
    @AfterEach void close(){f.close();}
    Object read(String tenant,Long id) {
        Method method=Arrays.stream(f.service.getClass().getMethods()).filter(m->m.getName().equals("readPostedLocked"))
            .findFirst().orElseGet(()->{fail("缺少永久过账的无写恢复入口");return null;});
        try{return method.invoke(f.service,tenant,id);}
        catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    Object field(Object value,String name){try{return value.getClass().getMethod(name).invoke(value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    void clear(){clearInvocations(f.mapper,f.notifications,f.locks,f.orderWorkflow,f.iqc);}
    void noWrites(){
        verifyNoInteractions(f.notifications);verify(f.locks,never()).lock(anyString(),any());
        verify(f.mapper,never()).finish(anyString(),anyLong(),anyString(),anyLong());
        verify(f.mapper,never()).markShelved(anyString(),anyLong(),anyString(),anyLong());
        verify(f.mapper,never()).recordPosting(anyString(),anyLong(),anyLong(),anyLong(),anyString(),any(),anyString());
        verify(f.mapper,never()).acknowledgeEnqueue(anyString(),anyLong());
        verify(f.mapper,never()).insertStock(anyString(),anyLong(),anyString(),any(),any(),any(),anyString(),anyLong(),any(),any());
        verify(f.mapper,never()).insertTransaction(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void anUnpostedReceiptIsNeverCreatedByARecoveryRead(){
        assertThrows(ServiceException.class,()->read(f.TENANT,f.ID));assertEquals(0,f.transactions.size());noWrites();
    }
    @Test void committedPostingReadsTheOriginalPhysicalResultWithoutReenqueuing(){
        var first=f.service.post(f.ID);clear();Object view=read(f.TENANT,f.ID);
        assertEquals(301L,field(view,"orderId"));var result=field(view,"result");
        assertEquals(first.sourceFingerprint(),field(result,"sourceFingerprint"));
        assertEquals(true,field(result,"replay"));assertEquals("ENQUEUED",field(view,"notificationStatus"));noWrites();
    }
    @Test void pendingNotificationRemainsPendingWithoutSendingFromGet(){
        doThrow(new ServiceException("消息不可用")).when(f.notifications).sendCertifiedPurchaseReceiveWriteback(f.ID,f.TENANT);
        f.service.post(f.ID);clear();var view=read(f.TENANT,f.ID);
        assertEquals("PENDING",field(view,"notificationStatus"));assertEquals(true,field(field(view,"result"),"notificationPending"));noWrites();
    }
    @Test void missingOriginalRxCannotBeRecoveredFromPostingFlagAlone(){
        f.service.post(f.ID);f.transactions.clear();clear();assertThrows(ServiceException.class,()->read(f.TENANT,f.ID));noWrites();
    }
    @Test void anotherTenantCannotUseTheOriginalReceiptIdentity(){
        f.service.post(f.ID);clear();assertThrows(ServiceException.class,()->read("000001",f.ID));verifyNoInteractions(f.mapper);noWrites();
    }
    @Test void recoveryLocksHeadBeforePostingToMatchTheExistingFacadeRoute(){
        f.service.post(f.ID);clear();read(f.TENANT,f.ID);
        var calls=mockingDetails(f.mapper).getInvocations().stream().map(i->i.getMethod().getName()).toList();
        assertTrue(calls.indexOf("lockHead")<calls.indexOf("lockPosting"),"原头/永久posting锁序不得与旧路由相反");
        noWrites();
    }
    @Test void consumedCurrentStockDoesNotRewriteOriginalReceiptOnRead(){
        f.service.post(f.ID);f.stocks.clear();clear();var view=read(f.TENANT,f.ID);
        assertEquals(true,field(field(view,"result"),"replay"));assertEquals(1,f.transactions.size());noWrites();
    }
}
