package com.nexus.erp.wm;
import org.junit.jupiter.api.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.Head;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 出生冻结不能阻断真正上架；同原生预检后只转状态，不能提前写库存或释放占用。 */
class WmCertifiedReceiptShelfTest {
    WmCertifiedReceiptPostingServiceTest f=new WmCertifiedReceiptPostingServiceTest();
    @BeforeEach void setup(){f.setup();when(f.auth.requireShelf()).thenReturn(new DecisionActor(f.TENANT,9L,"上架测试员",Set.of("wm:receive:shelf")));
        Head h=f.head;f.head=new Head(h.tenantId(),h.receiveId(),h.receiveCode(),h.orderId(),h.vendorId(),"APPROVED",h.warehouseId(),h.areaId(),h.locationId(),h.receiveAt(),null,h.delFlag());}
    @AfterEach void close(){f.tenants.close();}
    Object shelf() throws Exception {
        try{return f.service.getClass().getMethod("shelf",Long.class).invoke(f.service,f.ID);}
        catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}
    }
    @Test void actualApprovedSourceMustHaveProtectedShelfTransition() {
        when(f.mapper.markShelved(anyString(),anyLong(),anyString(),anyLong())).thenReturn(1);
        assertDoesNotThrow(this::shelf);
        verify(f.iqc).requireLocked(f.TENANT,f.ID,f.MATERIAL,4L,f.Q);
        assertTrue(mockingDetails(f.mapper).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("insertStock")));
        verify(f.mapper,never()).recordPosting(anyString(),anyLong(),anyLong(),anyLong(),anyString(),any(),anyString());
        verifyNoInteractions(f.notifications);
    }
    @Test void shelfRoutingDoesNotRequireExecutePermission() {
        when(f.auth.requireUser()).thenThrow(new IllegalStateException("用户仅具上架权限"));
        assertDoesNotThrow(()->f.service.handlesShelf(f.ID));verify(f.auth).requireShelf();verify(f.auth,never()).requireUser();
    }
    @Test void zeroShelfCasNeverPretendsStateTransitionSucceeded() {
        when(f.mapper.markShelved(anyString(),anyLong(),anyString(),anyLong())).thenReturn(0);
        assertThrows(RuntimeException.class,()->shelf());verifyNoInteractions(f.notifications);
        assertTrue(mockingDetails(f.mapper).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("insertStock")));
    }
    @Test void shelfReplayOfActualShelvedHeadDoesNotRewriteOrPost() {
        var h=f.head;f.head=new Head(h.tenantId(),h.receiveId(),h.receiveCode(),h.orderId(),h.vendorId(),"SHELVED",
            h.warehouseId(),h.areaId(),h.locationId(),h.receiveAt(),f.NOW.subtract(new java.math.BigDecimal("5000")),h.delFlag());
        assertDoesNotThrow(this::shelf);verify(f.mapper,never()).markShelved(anyString(),anyLong(),anyString(),anyLong());
        verifyNoInteractions(f.notifications);assertTrue(mockingDetails(f.mapper).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("insertStock")));
    }
    @Test void missingBornEvidenceNeverWritesShelfOrInventory() {
        doThrow(new IllegalStateException("UNIT_ORIGIN_UNPROVEN")).when(f.iqc).requireLocked(anyString(),anyLong(),anyLong(),anyLong(),any());
        assertThrows(RuntimeException.class,()->shelf());verifyNoInteractions(f.notifications);
    }
}
