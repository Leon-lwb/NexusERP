package com.nexus.erp.wm;

import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实入口的写后回读、永久幂等与拒绝分支；DAO/事务仅为离线测试替身。 */
class WmReceiptIqcBirthServiceTest {
    final String tenant="000007";
    final WmReceiptIqcBirthMapper mapper=mock(WmReceiptIqcBirthMapper.class);
    final WmReceiptIqcEvidenceService scopes=mock(WmReceiptIqcEvidenceService.class);
    final PuRequirementErpTransactionExecutor transactions=mock(PuRequirementErpTransactionExecutor.class);
    final PuRequirementErpTransactionQualification qualifier=mock(PuRequirementErpTransactionQualification.class);
    final PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
    final PuCertifiedPurchaseOrderWorkflowService orders=mock(PuCertifiedPurchaseOrderWorkflowService.class);
    final WmReceiptIqcBirthService service=new WmReceiptIqcBirthService(mapper,scopes,transactions,qualifier,locks,orders);
    MockedStatic<TenantHelper> tenants; Birth saved;
    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(tenant);
        when(transactions.executeRepeatableRead(eq(tenant),any())).thenAnswer(i -> i.<Supplier<?>>getArgument(1).get());
        when(mapper.columns()).thenReturn(WmReceiptIqcNativeBornEvidenceTest.columns());when(mapper.indexes()).thenReturn(WmReceiptIqcNativeBornEvidenceTest.indexes());
        when(scopes.captureBirthScopeLocked(tenant,101L,201L)).thenReturn(new WmReceiptIqcEvidenceService.Scope(101L,201L,301L,4,new BigDecimal("12.0000"),"a".repeat(64),System.currentTimeMillis()-100000));
        var d=new WmReceiptIqcBirthMapper.Description();d.setTenantId(tenant);d.setReceiveId(101L);d.setMaterialId(201L);d.setPurId(901L);
        d.setVendorId(902L);d.setReceiveCode("RCV-20261003-001");d.setMaterialCode("MAT-201");d.setMaterialName("出生来源物料");d.setVendorName("出生来源供应商");d.setStatus("APPROVED");d.setDelFlag("0");
        when(mapper.lockDescription(tenant,101L,201L)).thenReturn(d);
        when(mapper.insert(any())).thenAnswer(i -> {saved=i.getArgument(0);return 1;});
        when(mapper.lockId(eq(tenant),anyLong())).thenAnswer(i -> saved==null?null:WmReceiptIqcNativeBornEvidenceTest.row(saved));
        when(mapper.lockScope(tenant,101L,201L)).thenAnswer(i -> saved==null?null:WmReceiptIqcNativeBornEvidenceTest.row(saved));
    }
    @AfterEach void close(){tenants.close();}
    @Test void realCurrentSourceWritesOnceAndRecoversSamePermanentBirth() {
        Birth first=service.prepare(tenant,101L,201L);Birth second=service.prepare(tenant,101L,201L);
        assertEquals(first,second);verify(mapper,times(1)).insert(any());verify(orders,times(2)).requireApprovedLocked(tenant,901L);
        assertEquals(301L,first.baseUomId());assertEquals(new BigDecimal("12.0000"),first.quantity());
    }
    @Test void changedCapturedSourceIsNotReboundByRetry() {
        service.prepare(tenant,101L,201L);
        when(scopes.captureBirthScopeLocked(tenant,101L,201L)).thenReturn(new WmReceiptIqcEvidenceService.Scope(101L,201L,301L,4,new BigDecimal("13"),"b".repeat(64),System.currentTimeMillis()-100000));
        assertEquals("IQC_BIRTH_SOURCE_CHANGED",assertThrows(RuntimeException.class,()->service.prepare(tenant,101L,201L)).getMessage());verify(mapper,times(1)).insert(any());
    }
    @Test void wrongTenantCannotBeginPreparationTransaction() {
        assertEquals("IQC_TENANT_MISMATCH",assertThrows(RuntimeException.class,()->service.prepare("000008",101L,201L)).getMessage());verifyNoInteractions(transactions);
    }
    @Test void unapprovedOrderCannotProduceBirthAuthority() {
        doThrow(new IllegalStateException("无可靠批准来源")).when(orders).requireApprovedLocked(tenant,901L);
        assertThrows(RuntimeException.class,()->service.prepare(tenant,101L,201L));verify(mapper,never()).insert(any());
    }
    @Test void zeroInsertAndMissingCommittedRowAreNotSuccessfulBirth() {
        when(mapper.insert(any())).thenReturn(0);
        assertEquals("IQC_BIRTH_INSERT_CONFLICT",assertThrows(RuntimeException.class,()->service.prepare(tenant,101L,201L)).getMessage());
    }
    @Test void missingActualInsertedRowRejectsWriteback() {
        when(mapper.lockId(eq(tenant),anyLong())).thenReturn(null);
        assertEquals("IQC_BIRTH_WRITEBACK_MISMATCH",assertThrows(RuntimeException.class,()->service.prepare(tenant,101L,201L)).getMessage());
    }
    @Test void actualPermanentLookupRecoversClosedLifecycleWithoutNewPreparationOrMutation() {
        Birth first=service.prepare(tenant,101L,201L);clearInvocations(mapper,scopes,orders);
        when(mapper.lockDescription(tenant,101L,201L)).thenThrow(new IllegalStateException("终态不再重新准备"));
        assertEquals(first,service.readPermanent(tenant,101L,201L));verifyNoInteractions(scopes,orders);
        verify(mapper,never()).insert(any());verify(mapper,never()).lockDescription(anyString(),anyLong(),anyLong());
    }
    @Test void permanentLookupCannotInferBornFactFromMissingRow() {
        assertEquals("IQC_BIRTH_REGISTRY_MISSING",assertThrows(RuntimeException.class,()->service.readPermanent(tenant,101L,201L)).getMessage());
        verify(mapper,never()).insert(any());verifyNoInteractions(scopes,orders);
    }
    @Test void fractionalPrecisionMismatchCannotBeRoundedIntoBirth() {
        when(scopes.captureBirthScopeLocked(tenant,101L,201L)).thenReturn(new WmReceiptIqcEvidenceService.Scope(101L,201L,301L,0,new BigDecimal("5.4292"),"a".repeat(64),System.currentTimeMillis()-100000));
        assertThrows(RuntimeException.class,()->service.prepare(tenant,101L,201L));verify(mapper,never()).insert(any());
    }
}
