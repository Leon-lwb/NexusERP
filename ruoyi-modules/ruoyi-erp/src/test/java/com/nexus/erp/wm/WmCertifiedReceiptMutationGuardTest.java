package com.nexus.erp.wm;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WmCertifiedReceiptMutationGuardTest {
    MockedStatic<TenantHelper> tenants;WmCertifiedReceiptPostingMapper mapper;WmCertifiedReceiptMutationGuard guard;
    @BeforeEach void setup(){tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn("000000");mapper=mock(WmCertifiedReceiptPostingMapper.class);guard=new WmCertifiedReceiptMutationGuard(mapper);
        when(mapper.lockHead("000000",101L)).thenReturn(head("DRAFT"));when(mapper.discoverLineIdentity("000000",701L)).thenReturn(new SourceIdentity("000000",101L,701L));when(mapper.lockLineIdentity("000000",701L)).thenReturn(new SourceIdentity("000000",101L,701L));
        when(mapper.discoverDetailIdentity("000000",901L)).thenReturn(new SourceIdentity("000000",101L,701L));when(mapper.lockDetailIdentity("000000",901L)).thenReturn(new SourceIdentity("000000",101L,701L));}
    Head head(String status){return new Head("000000",101L,"RK202610030001",301L,401L,status,501L,601L,null,BigDecimal.ONE,BigDecimal.TEN,"0");}
    @AfterEach void close(){tenants.close();}
    @Test void permanentPostingRejectsHeadMutationEvenIfStatusWasTamperedBackToDraft(){when(mapper.lockPosting("000000",101L)).thenReturn(new Posting("000000",101L,201L,9L,"a".repeat(64),BigDecimal.ONE,"2026-10-03 10:00:00","ENQUEUED"));assertThrows(ServiceException.class,()->guard.requireUnposted(101L));}
    @Test void headMustRemainEditableBeforeDirectLineOrDetailWrites(){when(mapper.lockHead("000000",101L)).thenReturn(head("SHELVED"));assertThrows(ServiceException.class,()->guard.requireMutableLine(701L,101L));assertThrows(ServiceException.class,()->guard.requireMutableDetail(901L,101L,701L));}
    @Test void existingLineCannotBeReboundToAnotherMutableParent(){assertThrows(ServiceException.class,()->guard.requireMutableLine(701L,202L));}
    @Test void existingDetailCannotBeReboundToAnotherLine(){assertThrows(ServiceException.class,()->guard.requireMutableDetail(901L,101L,702L));}
    @Test void currentParentIdentityMustMatchNonAuthoritativeDiscovery(){when(mapper.lockDetailIdentity("000000",901L)).thenReturn(new SourceIdentity("000000",202L,701L));assertThrows(ServiceException.class,()->guard.requireMutableDetail(901L,101L,701L));}
    @Test void insertedDetailMustBelongToActualCurrentReceiptLine(){assertThrows(ServiceException.class,()->guard.requireMutableDetailParent(202L,701L));}
    @Test void validUnpostedDraftSourceCanBeEdited(){assertDoesNotThrow(()->guard.requireMutableLine(701L,101L));assertDoesNotThrow(()->guard.requireMutableDetailParent(101L,701L));}
    @Test void ordinaryExecuteMustNotBypassNativePostingForCertifiedOrder(){when(mapper.lockHead("000000",101L)).thenReturn(head("SHELVED"));when(mapper.lockCertifiedOrderIdentities("000000",301L)).thenReturn(List.of(301L));assertThrows(ServiceException.class,()->guard.requireLegacyExecution(101L));}
    @Test void legacyHistoryIsNotUpgradedByTheGuard(){when(mapper.lockCertifiedOrderIdentities("000000",301L)).thenReturn(List.of());assertDoesNotThrow(()->guard.requireLegacyExecution(101L));}
    @Test void wrongTenantAndDatabaseErrorsAreFailClosedWithoutLeaking(){when(mapper.lockHead("000000",101L)).thenThrow(new IllegalStateException("jdbc password token"));var failure=assertThrows(ServiceException.class,()->guard.requireUnposted(101L));assertFalse(failure.getMessage().contains("password"));assertNull(failure.getCause());}
    @Test void nativeBirthPreventsHeadAndReceiptQuantityRewriteBeforePosting() {
        when(mapper.lockHead("000000",101L)).thenReturn(head("APPROVED"));
        when(mapper.lockBirthIdentities("000000",101L)).thenReturn(List.of(71L));
        assertThrows(ServiceException.class,()->guard.requireUnposted(101L));
        assertThrows(ServiceException.class,()->guard.requireMutableLineParent(101L));
        assertThrows(ServiceException.class,()->guard.requireMutableLine(701L,101L));
    }
    @Test void birthDoesNotPretendInspectionCoveredFutureStorageAllocation() {
        when(mapper.lockHead("000000",101L)).thenReturn(head("APPROVED"));
        when(mapper.lockBirthIdentities("000000",101L)).thenReturn(List.of(71L));
        assertDoesNotThrow(()->guard.requireMutableDetailParent(101L,701L));
        assertDoesNotThrow(()->guard.requireMutableDetail(901L,101L,701L));
        verify(mapper,never()).lockBirthIdentities(anyString(),anyLong());
    }
}
