package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseDocumentGuardMapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** 独立保护合同：无头标记不是历史证明；command / ledger / 当前行身份都必须检查。 */
class PuCertifiedPurchaseDocumentGuardTest {
    private static final String TENANT = "000000";

    @Test
    void provenLegacyReturnUsesCurrentParentMaterialAndTenantIdentityWithoutUpgradingSource() throws Exception {
        try(Fixture f=new Fixture()) {
            f.legacyOrder(20L);
            PuPurchaseOrderLine original=new PuPurchaseOrderLine();original.setLineId(21L);original.setOrderId(20L);original.setMaterialId(501L);
            f.stub("lockOrderLine",original,21L);
            f.stub(
                    "lockReturnOrderIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderIdentity(TENANT, 20L),
                    20L);
            f.stub(
                    "lockReturnOrderLineIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderLineIdentity(
                            TENANT, 21L, 20L, 501L),
                    21L);
            PuPurchaseReturnLine candidate=new PuPurchaseReturnLine();candidate.setOrderLineId(21L);candidate.setMaterialId(501L);
            assertDoesNotThrow(()->f.call("requireLegacyReturnInput",20L,List.of(candidate)));
            f.mapperMethod("lockReturnOrderIdentity").invoke(verify(f.mapper),TENANT,20L);
            f.mapperMethod("lockReturnOrderLineIdentity").invoke(verify(f.mapper),TENANT,21L);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "head-tenant",
                "line-tenant",
                "line-parent",
                "line-material",
                "missing-head",
                "missing-line"
            })
    void unknownOrContradictoryNativeReturnOwnershipMustRejectBeforeWriting(String corruption)
            throws Exception {
        try(Fixture f=new Fixture()) {
            f.legacyOrder(20L);
            PuPurchaseOrderLine original=new PuPurchaseOrderLine();original.setLineId(21L);original.setOrderId(20L);original.setMaterialId(501L);
            f.stub("lockOrderLine",original,21L);
            var head =
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderIdentity(
                            "head-tenant".equals(corruption) ? "other" : TENANT, 20L);
            var line =
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderLineIdentity(
                            "line-tenant".equals(corruption) ? "other" : TENANT,
                            21L,
                            "line-parent".equals(corruption) ? 30L : 20L,
                            "line-material".equals(corruption) ? 999L : 501L);
            f.stub("lockReturnOrderIdentity","missing-head".equals(corruption)?null:head,20L);
            f.stub("lockReturnOrderLineIdentity","missing-line".equals(corruption)?null:line,21L);
            PuPurchaseReturnLine candidate=new PuPurchaseReturnLine();candidate.setOrderLineId(21L);candidate.setMaterialId(501L);
            f.rejected("requireLegacyReturnInput",20L,List.of(candidate));
        }
    }
    @Test
    void legacyReturnForOrderACannotReferenceAnUnprotectedLineOfOrderB() throws Exception {
        try (Fixture f=new Fixture()) {
            f.legacyOrder(20L);f.legacyOrder(30L);
            PuPurchaseOrderLine original=new PuPurchaseOrderLine();original.setLineId(31L);original.setOrderId(30L);original.setMaterialId(501L);
            f.stub("lockOrderLine",original,31L);
            f.stub(
                    "lockReturnOrderIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderIdentity(TENANT, 20L),
                    20L);
            f.stub(
                    "lockReturnOrderLineIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderLineIdentity(
                            TENANT, 31L, 30L, 501L),
                    31L);
            PuPurchaseReturnLine candidate=new PuPurchaseReturnLine();candidate.setOrderLineId(31L);candidate.setMaterialId(501L);
            f.rejected("requireLegacyReturnInput",20L,List.of(candidate));
            f.mapperMethod("lockReturnOrderLineIdentity").invoke(verify(f.mapper),TENANT,31L);
        }
    }
    @Test
    void legacyReturnCannotSubstituteAnotherMaterialForThePersistedOrderLine() throws Exception {
        try (Fixture f=new Fixture()) {
            f.legacyOrder(20L);
            PuPurchaseOrderLine original=new PuPurchaseOrderLine();original.setLineId(21L);original.setOrderId(20L);original.setMaterialId(501L);
            f.stub("lockOrderLine",original,21L);
            f.stub(
                    "lockReturnOrderIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderIdentity(TENANT, 20L),
                    20L);
            f.stub(
                    "lockReturnOrderLineIdentity",
                    new PuCertifiedPurchaseDocumentGuardMapper.ReturnOrderLineIdentity(
                            TENANT, 21L, 20L, 501L),
                    21L);
            PuPurchaseReturnLine candidate=new PuPurchaseReturnLine();candidate.setOrderLineId(21L);candidate.setMaterialId(999L);
            f.rejected("requireLegacyReturnInput",20L,List.of(candidate));
            f.mapperMethod("lockReturnOrderLineIdentity").invoke(verify(f.mapper),TENANT,21L);
        }
    }
    @Test
    void legacyReturnWithoutPreciseParentAndLineAndMaterialNeverInventsOwnership() throws Exception {
        try (Fixture f=new Fixture()) {
            f.legacyOrder(20L);
            PuPurchaseReturnLine candidate=new PuPurchaseReturnLine();candidate.setMaterialId(501L);
            f.rejected("requireLegacyReturnInput",20L,List.of(candidate));
        }
    }
    @Test
    void certifiedMutationRefusalNamesTheOfficialSourceEntryWithoutPromisingLifecycleSupport() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionCommands",1L,10L);
            ServiceException error=f.rejected("requireLegacyRequisitionMutation",10L);
            assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
            assertTrue(error.getMessage().contains("未支持"),error.getMessage());
        }
    }

    @Test
    void unknownEvidenceRefusalAlsoNamesTheOfficialSourceEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionCommands",null,10L);
            ServiceException error=f.rejected("requireLegacyRequisitionMutation",10L);
            assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
        }
    }

    @Test
    void requestCannotInsertForeignTenantLinesIntoALegacyDocument() throws Exception {
        try (Fixture f = new Fixture()) {
            f.legacyRequisition(10L);
            PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine(); line.setTenantId("other");
            f.rejected("requireLegacyRequisitionEdit",10L,List.of(line));
        }
    }

    @Test
    void returnCannotWriteAForeignTenantLineEvenWithoutOrderIdentity() throws Exception {
        try (Fixture f = new Fixture()) {
            PuPurchaseReturnLine line = new PuPurchaseReturnLine(); line.setTenantId("other");
            f.rejected("requireLegacyReturnInput",null,List.of(line));
        }
    }

    @Test
    void commandProtectsRequisitionEvenWhenAllHeaderMarkersWereLost() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionCommands", 1L, 10L);
            f.rejected("requireLegacyRequisitionMutation", 10L);
        }
    }

    @Test
    void ledgerProtectsRequisitionWithoutACommandOrHeaderMarker() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionCommands", 0L, 10L);
            f.stub("countRequisitionAllocations", 1L, 10L);
            f.rejected("requireLegacyRequisitionMutation", 10L);
        }
    }

    @Test
    void detachedRequisitionLineIsProtectedByLedgerIdentity() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionLineAllocations", 1L, 11L);
            f.rejected("requireLegacyRequisitionLineMutation", 11L);
        }
    }

    @Test
    void detachedOrderAndOrderLineAreProtectedByTheirOwnLedgerIdentity() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countOrderAllocations", 1L, 20L);
            f.rejected("requireLegacyOrderMutation", 20L);
            f.stub("countOrderLineAllocations", 1L, 21L);
            f.rejected("requireLegacyOrderLineMutation", 21L);
        }
    }

    @Test
    void certifiedSourceLineCannotBeMovedIntoAnUnmarkedOrder() throws Exception {
        try (Fixture f = new Fixture()) {
            PuPurchaseOrder order = new PuPurchaseOrder();
            order.setOrderId(20L);
            PuPurchaseOrderLine line = new PuPurchaseOrderLine();
            line.setLineId(21L); line.setOrderId(20L); line.setRequisitionLineId(11L);
            f.stub("lockOrder", order, 20L);
            f.stub("lockOrderLines", List.of(line), 20L);
            f.stub("countRequisitionLineAllocations", 1L, 11L);
            f.rejected("requireLegacyOrderMutation", 20L);
        }
    }

    @Test
    void lostHeadMarkerDoesNotWashPersistedMrpLineOrLegacyEmptyUnit() throws Exception {
        try (Fixture f = new Fixture()) {
            f.legacyRequisition(10L);
            PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
            line.setTenantId(TENANT); line.setLineId(11L); line.setRequisitionId(10L);
            line.setSourceType("MRP"); line.setSourceId(901L); line.setUnit("");
            f.stub("lockRequisitionLines", List.of(line), 10L);
            f.rejected("requireLegacyRequisitionMutation", 10L);
            assertEquals("", line.getUnit());
        }
    }

    @Test
    void legacyManualDocumentWithNoProvenanceEvidenceRemainsReadableAndIsNotUpgraded() throws Exception {
        try (Fixture f = new Fixture()) {
            f.legacyRequisition(10L);
            assertDoesNotThrow(() -> f.call("requireLegacyRequisitionMutation", 10L));
        }
    }

    @Test
    void proposedForeignLineCannotBeReparentedToALegacyRequisition() throws Exception {
        try (Fixture f = new Fixture()) {
            f.legacyRequisition(10L);
            f.stub("countRequisitionLineAllocations", 1L, 11L);
            PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
            line.setLineId(11L); line.setRequisitionId(10L);
            f.rejected("requireLegacyRequisitionEdit", 10L, List.of(line));
        }
    }

    @Test
    void returnCannotConcealCertifiedOrderBehindMissingHeadOrderId() throws Exception {
        try (Fixture f = new Fixture()) {
            PuPurchaseReturn row = new PuPurchaseReturn();
            row.setTenantId(TENANT); row.setReturnId(30L);
            PuPurchaseReturnLine line = new PuPurchaseReturnLine();
            line.setTenantId(TENANT); line.setLineId(31L); line.setReturnId(30L); line.setOrderLineId(21L);
            f.stub("lockReturn", row, 30L);
            f.stub("lockReturnLines", List.of(line), 30L);
            f.stub("countOrderLineAllocations", 1L, 21L);
            f.rejected("requireLegacyReturnMutation", 30L);
        }
    }

    @Test
    void returnInputCannotIntroduceCertifiedOrderLineWithoutAHeadOrderId() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countOrderLineAllocations", 1L, 21L);
            PuPurchaseReturnLine line = new PuPurchaseReturnLine(); line.setOrderLineId(21L);
            f.rejected("requireLegacyReturnInput", null, List.of(line));
        }
    }

    @Test
    void mapperFailureIsUnknownAndNeverLeaksSqlOrSecretCause() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fail("countRequisitionCommands", new IllegalStateException("password=fixture-secret SELECT *"), 10L);
            ServiceException error = f.rejected("requireLegacyRequisitionMutation", 10L);
            assertFalse(error.getMessage().contains("fixture-secret"));
            assertFalse(error.getMessage().contains("SELECT"));
            assertNull(error.getCause());
            assertEquals(0, error.getSuppressed().length);
        }
    }

    @Test
    void unknownLedgerCountNeverFallsBackToLegacy() throws Exception {
        try (Fixture f = new Fixture()) {
            f.stub("countRequisitionCommands", null, 10L);
            f.rejected("requireLegacyRequisitionMutation", 10L);
        }
    }

    @Test
    void absentTenantRejectsBeforeAnyEvidenceLookup() throws Exception {
        try (Fixture f = new Fixture()) {
            f.tenant.when(TenantHelper::getTenantId).thenReturn(null);
            f.rejected("requireLegacyRequisitionMutation", 10L);
            verifyNoInteractions(f.mapper);
        }
    }

    @Test
    void mapperEvidenceReadsAreTenantScopedCurrentUncachedAndNeverWriteOrHideDeletedEvidence() throws Exception {
        Class<?> type = requiredClass("com.nexus.erp.pu.mapper.PuCertifiedPurchaseDocumentGuardMapper");
        for (Method method : type.getDeclaredMethods()) {
            var select = method.getAnnotation(Select.class);
            assertNotNull(select, method.getName());
            String sql = String.join(" ", select.value()).toLowerCase();
            assertTrue(sql.startsWith("select "), sql);
            assertTrue(sql.contains("tenant_id"), sql);
            assertTrue(sql.contains("#{tenantid}"), sql);
            assertTrue(sql.contains("for update"), sql);
            assertFalse(sql.contains("del_flag ="), sql);
            assertFalse(sql.contains("del_flag="), sql);
            var options = method.getAnnotation(Options.class);
            assertNotNull(options, method.getName());
            assertFalse(options.useCache());
            assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache());
        }
    }

    private static Class<?> requiredClass(String name) throws Exception {
        assertDoesNotThrow(() -> Class.forName(name), "缺少旧路径来源保护能力: " + name);
        return Class.forName(name);
    }

    private static final class Fixture implements AutoCloseable {
        final Class<?> mapperType;
        final Object mapper;
        final Object guard;
        final MockedStatic<TenantHelper> tenant;

        Fixture() throws Exception {
            mapperType = requiredClass("com.nexus.erp.pu.mapper.PuCertifiedPurchaseDocumentGuardMapper");
            mapper = Mockito.mock(mapperType);
            Class<?> guardType = requiredClass("com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard");
            guard = guardType.getConstructor(mapperType).newInstance(mapper);
            tenant = Mockito.mockStatic(TenantHelper.class);
            tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        }
        Method mapperMethod(String name) throws Exception {
            return mapperType.getMethod(name, String.class, Long.class);
        }
        void stub(String name, Object value, Long id) throws Exception {
            Object stubbing = doReturn(value).when(mapper);
            mapperMethod(name).invoke(stubbing, TENANT, id);
        }
        void fail(String name, RuntimeException error, Long id) throws Exception {
            Object stubbing = doThrow(error).when(mapper);
            mapperMethod(name).invoke(stubbing, TENANT, id);
        }
        void legacyOrder(Long id) throws Exception {
            PuPurchaseOrder row=new PuPurchaseOrder();row.setOrderId(id);
            stub("lockOrder",row,id);stub("lockOrderLines",List.of(),id);
        }
        void legacyRequisition(Long id) throws Exception {
            PuPurchaseRequisition row = new PuPurchaseRequisition();
            row.setTenantId(TENANT); row.setRequisitionId(id); row.setSourceType("MANUAL");
            stub("lockRequisition", row, id);
            stub("lockRequisitionLines", List.of(), id);
        }
        Object call(String name, Object... args) throws Throwable {
            Method method =
                    Arrays.stream(guard.getClass().getMethods())
                            .filter(
                                    m ->
                                            m.getName().equals(name)
                                                    && m.getParameterCount() == args.length)
                            .findFirst()
                            .orElseThrow();
            try { return method.invoke(guard, args); }
            catch (InvocationTargetException error) { throw error.getTargetException(); }
        }
        ServiceException rejected(String name, Object... args) {
            return assertThrows(ServiceException.class, () -> call(name, args));
        }
        @Override
        public void close() { tenant.close(); }
    }
}
