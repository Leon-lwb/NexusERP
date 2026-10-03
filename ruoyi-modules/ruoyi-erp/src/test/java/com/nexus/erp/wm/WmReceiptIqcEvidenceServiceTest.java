package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.ReceiptIqcSource;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.*;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmReceiptIqcEvidenceServiceTest {
    static final String TENANT = "000007";
    final WmReceiptIqcEvidenceMapper mapper = mock(WmReceiptIqcEvidenceMapper.class);
    final ReceiptIqcSource source = mock(ReceiptIqcSource.class);
    final PuRequirementErpTransactionExecutor transactions = mock(PuRequirementErpTransactionExecutor.class);
    final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
    final WmReceiptIqcEvidenceService service = new WmReceiptIqcEvidenceService(mapper, source, transactions, qualification);
    final AtomicBoolean inErp = new AtomicBoolean();
    final long now = System.currentTimeMillis();
    EvidenceRow stored;

    void arrange(String result) {
        when(mapper.selectIsolationDetails(TENANT, 101L)).thenReturn(List.of(WmReceiptIqcEvidencePreciseIsolationTest.detail(21L, 401L)));
        when(mapper.selectIsolationReceiptTransactions(TENANT, 101L)).thenReturn(List.of(WmReceiptIqcEvidencePreciseIsolationTest.tx(601L, 501L, 401L)));
        when(mapper.selectIsolationStock(TENANT, 501L)).thenReturn(WmReceiptIqcEvidencePreciseIsolationTest.stock(501L, 401L, 601L));
        when(mapper.selectIsolationBatch(TENANT, 401L)).thenReturn(WmReceiptIqcEvidencePreciseIsolationTest.batch(401L));
        when(mapper.selectIsolationSourceHead(TENANT, 601L)).thenReturn(WmReceiptIqcEvidencePreciseIsolationTest.tx(601L, 501L, 401L));
        when(mapper.selectIsolationStorage(701L, 801L, 901L)).thenReturn(WmReceiptIqcEvidencePreciseIsolationTest.storage());
        when(mapper.freezeIsolationStock(eq(TENANT), any())).thenAnswer(i -> { i.<IsolationStock>getArgument(1).setFrozenFlag(1); return 1; });
        when(mapper.markIsolationBatch(eq(TENANT), any())).thenAnswer(i -> { i.<IsolationBatch>getArgument(1).setQcStatus("UNQUALIFIED"); return 1; });
        when(mapper.selectIsolationColumns()).thenAnswer(i -> WmReceiptIqcEvidenceSchemaTest.isolationColumns());
        when(mapper.selectIsolationIndexes()).thenAnswer(i -> WmReceiptIqcEvidenceSchemaTest.isolationIndexes());
        when(mapper.selectMaterialIdentitiesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(line(11L, "5"), line(12L, "7")));
        when(source.readCompletedIdentity(eq(TENANT), eq(501L))).thenAnswer(i -> source.readCompleted(TENANT, 501L));
        when(mapper.selectNativeColumns()).thenAnswer(i -> WmReceiptIqcEvidenceSchemaTest.erpColumns());
        when(mapper.selectNativeIndexes()).thenAnswer(i -> WmReceiptIqcEvidenceSchemaTest.erpIndexes());
        when(transactions.executeRepeatableRead(eq(TENANT), any())).thenAnswer(invocation -> {
            assertThat(inErp.getAndSet(true)).isFalse();
            try { return ((Supplier<?>) invocation.getArgument(1)).get(); }
            finally { inErp.set(false); }
        });
        when(source.readCompleted(eq(TENANT), eq(501L))).thenAnswer(i -> {
            assertThat(inErp.get()).isFalse();
            return observation(result, new BigDecimal("12.0000"), "a".repeat(64));
        });
        ReceiptHeader header = new ReceiptHeader();
        header.setTenantId(TENANT); header.setReceiveId(101L); header.setReceiveCode("RECV-101");
        header.setStatus("APPROVED"); header.setDelFlag("0"); header.setCreateTime(new Date(now - 100000));
        when(mapper.selectReceiveForUpdate(TENANT, 101L)).thenReturn(header);
        when(mapper.selectMaterialLinesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(line(11L, "5.0000"), line(12L, "7.0000")));
        MaterialUnit material = new MaterialUnit();
        material.setTenantId(TENANT); material.setMaterialId(201L); material.setBaseUomId(301L); material.setDelFlag("0");
        when(mapper.selectMaterialForUpdate(TENANT, 201L)).thenReturn(material);
        NativeUnit unit = new NativeUnit();
        unit.setCategory("COUNT"); unit.setIsBase(1L); unit.setPrimaryId(301L); unit.setConversionRate(BigDecimal.ONE);
        unit.setConversionVersion("root-v1"); unit.setConversionVerifiedAt(new Date(now-200000));
        unit.setTenantId(TENANT); unit.setUomId(301L); unit.setQuantityScale(4); unit.setStatus(1); unit.setDelFlag("0");
        when(mapper.selectUnitForUpdate(TENANT, 301L)).thenReturn(unit);
        when(mapper.insertEvidence(any())).thenAnswer(i -> { stored = i.getArgument(0); return 1; });
        when(mapper.selectInspectionForUpdate(TENANT, 501L)).thenAnswer(i -> stored);
        when(mapper.selectReceiptMaterialForUpdate(TENANT, 101L, 201L)).thenAnswer(i -> stored);
    }
    ReceiptIqcSource.Inspection observation(String result, BigDecimal qty, String hash) {
        return new ReceiptIqcSource.Inspection(TENANT, 501L, "IQC-501", 101L, 201L, qty,
            "COMPLETED", result, 801L, now - 90000, now - 2000, now - 2000, now - 1000,
            hash, "QC_IQC_CURRENT_V1:" + hash);
    }
    ReceiptLine line(Long id, String qty) {
        ReceiptLine line = new ReceiptLine();
        line.setTenantId(TENANT); line.setReceiveId(101L); line.setLineId(id); line.setMaterialId(201L);
        line.setBaseUomId("301"); line.setQuantityReceived(new BigDecimal(qty)); line.setDelFlag("0");
        return line;
    }
    IqcCompletedMessage message() {
        IqcCompletedMessage message = new IqcCompletedMessage();
        message.setTenantId(7L); message.setMessageId("event-501"); message.setIqcId(501L);
        message.setReceiveId(101L); message.setMaterialId(201L);
        message.setInspectResult("QUALIFIED"); message.setQuantity(new BigDecimal("999"));
        return message;
    }
    MockedStatic<TenantHelper> tenant() {
        MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        return tenant;
    }


    EvidenceRow legacyEvidence(String result) {
        // 模拟先前已持久化的 V1 记录。其当前 ERP 单位及有效密码学摘要
        // 不能证明原生 MES 创建时来源，绝不能据此授权。
        Object scope = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireScope", TENANT, 101L, 201L);
        EvidenceRow row = new EvidenceRow();
        row.setEvidenceId(701L); row.setTenantId(TENANT); row.setInspectionId(501L); row.setInspectionCode("IQC-501");
        row.setReceiveId(101L); row.setMaterialId(201L); row.setBaseUomId(mapper.selectMaterialForUpdate(TENANT, 201L).getBaseUomId());
        row.setQuantityScale(4); row.setInspectedQuantity(new BigDecimal("12.0000")); row.setInspectResult(result);
        row.setInspectorId(801L); row.setSourceKind("MES_QC_IQC_RECEIVE_MATERIAL_V1");
        row.setSourceVersion("QC_IQC_CURRENT_V1:" + "a".repeat(64)); row.setActualBodySha256("a".repeat(64));
        row.setReceiptScopeSha256(org.springframework.test.util.ReflectionTestUtils.invokeMethod(scope, "sha256"));
        row.setFirstMessageId("event-501"); row.setSourceCreatedAt(new Date(now - 90000));
        row.setCompletedAt(new Date(now - 2000)); row.setSourceUpdatedAt(new Date(now - 2000));
        row.setObservedAt(new Date(now - 1000)); row.setCertifiedAt(new Date(now));
        row.setEvidenceSha256(org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "evidenceSha", row));
        return row;
    }
    Object readCurrentScope() {
        return org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireScope", TENANT, 101L, 201L);
    }
    void missingOrigin(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ServiceException.class).hasMessage("UNIT_ORIGIN_UNPROVEN");
    }

    @Test void actualUnqualifiedIsolatesWithoutOriginalQuantityContractOrCertificate() {
        arrange("UNQUALIFIED");
        try (var tenant = tenant()) {
            service.consumeCompleted(message(), x -> { assertThat(x.result()).isEqualTo("UNQUALIFIED"); assertThat(x.quantity()).isNull(); });
            verify(mapper, never()).insertEvidence(any()); assertThat(stored).isNull();
        }
    }
    @Test void equalWholeMaterialTotalDoesNotCertifyUnitlessMesQuantity() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            missingOrigin(() -> service.consumeCompleted(message(), x -> fail("must not project")));
            verifyNoInteractions(transactions); verify(mapper, never()).selectMaterialLinesForUpdate(anyString(), anyLong(), anyLong());
        }
    }
    @Test void missingEvidenceDoesNotConsultMesOrClientHeaderQualification() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            assertThatThrownBy(() -> service.requireLocked(TENANT, 101L, 201L, 301L, BigDecimal.ONE)).hasMessage("IQC_EVIDENCE_MISSING");
            verifyNoInteractions(source, transactions);
        }
    }
    @Test void sourceLookupFailureStopsBeforeErpTransactionAndAnyWrite() {
        arrange("QUALIFIED"); when(source.readCompleted(TENANT, 501L)).thenThrow(new IllegalStateException("private JDBC diagnostic"));
        try (var tenant = tenant()) {
            assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("IQC_SOURCE_UNAVAILABLE").hasNoCause();
            verifyNoInteractions(transactions); verify(mapper, never()).insertEvidence(any());
        }
    }
    @Test void missingEnvelopeIdentityHintsDoNotFillMissingOriginalUnit() {
        arrange("QUALIFIED"); var m = message(); m.setReceiveId(null); m.setMaterialId(null);
        try (var tenant = tenant()) { missingOrigin(() -> service.consumeCompleted(m, x -> fail("must not project"))); }
    }
    @Test void mismatchedEnvelopeIdentityCannotRedirectTheNativeSource() {
        arrange("QUALIFIED"); var m = message(); m.setReceiveId(999L);
        try (var tenant = tenant()) {
            assertThatThrownBy(() -> service.consumeCompleted(m, x -> { })).hasMessage("IQC_SOURCE_IDENTITY_MISMATCH");
            verifyNoInteractions(transactions);
        }
    }
    @Test void currentTenantMustMatchCanonicalBaseMessageTenant() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000008");
            assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("IQC_TENANT_MISMATCH");
            verifyNoInteractions(source, transactions);
        }
    }
    @Test void disabledTenantCannotCreateCertifiedAuthority() {
        try (var tenant = tenant()) {
            tenant.when(TenantHelper::isEnable).thenReturn(false);
            assertThatThrownBy(() -> service.requireLocked(TENANT, 101L, 201L, 301L, BigDecimal.ONE)).hasMessage("IQC_TENANT_MISMATCH");
            verifyNoInteractions(mapper, source, transactions);
        }
    }
    @Test void externalErpOrMasterTransactionCannotContainMesRead() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("IQC_SOURCE_REQUIRES_FRESH_THREAD");
                verifyNoInteractions(source, transactions);
            } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
        }
    }
    @Test void lockedReadRequiresOwnedRrBeforeAnyCatalogOrBusinessMapper() {
        try (var tenant = tenant()) {
            doThrow(new ServiceException("not-owned")).when(qualification).requireSourceChainTransaction();
            assertThatThrownBy(() -> service.requireLocked(TENANT, 101L, 201L, 301L, BigDecimal.ONE)).hasMessage("not-owned");
            verifyNoInteractions(mapper, source, transactions);
        }
    }
    @Test void partialNumericQuantityCannotSupplyMissingHistoricalContract() {
        arrange("QUALIFIED"); when(source.readCompleted(TENANT, 501L)).thenReturn(observation("QUALIFIED", new BigDecimal("5"), "a".repeat(64)));
        try (var tenant = tenant()) { missingOrigin(() -> service.consumeCompleted(message(), x -> fail("must not project"))); }
    }
    @Test void currentScopeReaderRejectsMixedNativeUnitsButNeverCertifiesHistoricalQuantity() {
        arrange("QUALIFIED"); var other = line(12L, "7"); other.setBaseUomId("302");
        when(mapper.selectMaterialLinesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(line(11L, "5"), other));
        try (var tenant = tenant()) { assertThatThrownBy(this::readCurrentScope).hasMessage("IQC_RECEIPT_UNIT_MISMATCH"); }
    }
    @Test void currentScopeReaderRequiresKnownNativeUnitPrecision() {
        arrange("QUALIFIED"); mapper.selectUnitForUpdate(TENANT, 301L).setQuantityScale(null);
        try (var tenant = tenant()) { assertThatThrownBy(this::readCurrentScope).hasMessage("IQC_NATIVE_UNIT_UNKNOWN"); }
    }
    @Test void fiveDecimalNativeIqcQuantityIsRejectedWithoutRounding() {
        arrange("QUALIFIED"); when(source.readCompleted(TENANT, 501L)).thenReturn(observation("QUALIFIED", new BigDecimal("12.00001"), "a".repeat(64)));
        try (var tenant = tenant()) {
            assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("UNIT_ORIGIN_UNPROVEN");
            verifyNoInteractions(transactions);
        }
    }
    @Test void nativeIqcDecimal14OverflowCannotBeDowncastFromReceiptDecimal18() {
        arrange("QUALIFIED"); when(source.readCompleted(TENANT, 501L)).thenReturn(observation("QUALIFIED", new BigDecimal("10000000000.0000"), "a".repeat(64)));
        try (var tenant = tenant()) { assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("UNIT_ORIGIN_UNPROVEN"); }
    }
    @Test void currentScopeKeepsExactDecimalSumButEqualityStillCannotIssueCertificate() {
        arrange("QUALIFIED"); when(mapper.selectMaterialLinesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(line(11L, "0.1"), line(12L, "0.2")));
        when(source.readCompleted(TENANT, 501L)).thenReturn(observation("QUALIFIED", new BigDecimal("0.3000"), "a".repeat(64)));
        try (var tenant = tenant()) {
            Object scope = readCurrentScope();
            BigDecimal total = org.springframework.test.util.ReflectionTestUtils.invokeMethod(scope, "quantity");
            assertThat(total).isEqualByComparingTo("0.3");
            missingOrigin(() -> service.consumeCompleted(message(), x -> fail("must not project")));
        }
    }
    @Test void unitlessOriginalAndRepeatedEnvelopesBothFailWithoutAnyProjection() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            missingOrigin(() -> service.consumeCompleted(message(), x -> fail("must not project")));
            var retry = message(); retry.setMessageId("event-501-retry");
            missingOrigin(() -> service.consumeCompleted(retry, x -> fail("must not project")));
            verify(mapper, never()).insertEvidence(any());
        }
    }
    @Test void changedActualBodyCannotReplaceLegacyOriginWhileOriginalUnitIsMissing() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED"); EvidenceRow original = stored;
            when(source.readCompleted(TENANT, 501L)).thenReturn(observation("UNQUALIFIED", new BigDecimal("12"), "b".repeat(64)));
            service.consumeCompleted(message(), x -> assertThat(x.result()).isEqualTo("UNQUALIFIED"));
            assertThat(stored).isSameAs(original); assertThat(stored.getInspectResult()).isEqualTo("QUALIFIED");
            verify(mapper, never()).insertEvidence(any());
        }
    }
    @Test void verifyReplayNeverAcceptsUnitlessNativeCompletionRegardlessOfBodyHash() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            when(source.readCompleted(TENANT, 501L)).thenReturn(observation("QUALIFIED", new BigDecimal("12"), "b".repeat(64)));
            missingOrigin(() -> service.verifyReplay(message())); verifyNoInteractions(transactions);
        }
    }
    @Test void legacyUnqualifiedEvidenceIsAlsoNotAnOriginalQuantityUnitContract() {
        arrange("UNQUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("UNQUALIFIED");
            missingOrigin(() -> service.requireLocked(TENANT, 101L, 201L, 301L, new BigDecimal("12")));
        }
    }
    @Test void currentLineQuantitiesChangedWithSameSumDoNotUpgradeLegacyEvidence() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED");
            when(mapper.selectMaterialLinesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(line(11L, "4"), line(12L, "8")));
            missingOrigin(() -> service.requireLocked(TENANT, 101L, 201L, 301L, new BigDecimal("12")));
        }
    }
    @Test void evidenceFieldTamperingStillFailsCryptoIntegrityBeforeOriginCheck() {
        arrange("UNQUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("UNQUALIFIED"); stored.setInspectResult("QUALIFIED");
            assertThatThrownBy(() -> service.requireLocked(TENANT, 101L, 201L, 301L, new BigDecimal("12")))
                .hasMessage("IQC_EVIDENCE_INTEGRITY_INVALID");
        }
    }
    @Test void futureCompletionCannotPassNativeIdentityChecks() {
        arrange("QUALIFIED"); var good = observation("QUALIFIED", new BigDecimal("12"), "a".repeat(64));
        try (var tenant = tenant()) {
            when(source.readCompleted(TENANT, 501L)).thenReturn(new ReceiptIqcSource.Inspection(TENANT, 501L, "IQC-501", 101L, 201L,
                good.quantity(), "COMPLETED", "QUALIFIED", 801L, good.createdAt(), now + 100000, good.updatedAt(), now,
                good.actualBodySha256(), good.sourceVersion()));
            assertThatThrownBy(() -> service.consumeCompleted(message(), x -> { })).hasMessage("IQC_SOURCE_TIME_INVALID");
        }
    }
    @Test void anyRequestedQuantityOrCurrentUnitCannotOpenLegacyEvidenceGate() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED");
            missingOrigin(() -> service.requireLocked(TENANT, 101L, 201L, 301L, new BigDecimal("13")));
            missingOrigin(() -> service.requireLocked(TENANT, 101L, 201L, 302L, new BigDecimal("12")));
        }
    }
    @Test void validLegacyHashesArePreservedButNoPublicPostingIdentityIsIssued() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED"); String original = stored.getEvidenceSha256();
            missingOrigin(() -> service.requireLockedEvidence(TENANT, 101L, 201L, 301L, new BigDecimal("12")));
            assertThat(stored.getEvidenceSha256()).isEqualTo(original);
        }
    }
    @Test void nullReceiptLifecycleFailsClosedInCurrentScopeReader() {
        arrange("QUALIFIED"); mapper.selectReceiveForUpdate(TENANT, 101L).setStatus(null);
        try (var tenant = tenant()) { assertThatThrownBy(this::readCurrentScope).hasMessage("IQC_RECEIPT_SOURCE_INVALID"); }
    }
    @Test void finishedReceiptReplayNeverUsesOldUnguardedProjectionAgain() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED"); mapper.selectReceiveForUpdate(TENANT, 101L).setStatus("FINISHED");
            assertThatCode(this::readCurrentScope).doesNotThrowAnyException();
            clearInvocations(mapper, source, transactions);
            missingOrigin(() -> service.consumeCompleted(message(), x -> fail("must not project")));
            verifyNoInteractions(transactions); verify(mapper, never()).insertEvidence(any());
        }
    }
    @Test void finishedReceiptAcceptsLifecycleButStillRefusesMissingOriginalContractInLockedGate() {
        arrange("QUALIFIED");
        try (var tenant = tenant()) {
            stored = legacyEvidence("QUALIFIED"); mapper.selectReceiveForUpdate(TENANT, 101L).setStatus("FINISHED");
            assertThatCode(this::readCurrentScope).doesNotThrowAnyException();
            clearInvocations(mapper, source, transactions);
            missingOrigin(() -> service.requireLocked(TENANT, 101L, 201L, 301L, new BigDecimal("12")));
            verifyNoInteractions(source, transactions); verify(mapper, never()).selectMaterialLinesForUpdate(anyString(), anyLong(), anyLong());
        }
    }
    @Test void finishedReceiptVerifyReplayDoesNotTreatCurrentUnitAsHistoricalProof() {
        arrange("QUALIFIED"); mapper.selectReceiveForUpdate(TENANT, 101L).setStatus("FINISHED");
        try (var tenant = tenant()) {
            assertThatCode(this::readCurrentScope).doesNotThrowAnyException();
            clearInvocations(mapper, source, transactions);
            missingOrigin(() -> service.verifyReplay(message()));
            verify(source).readCompleted(TENANT, 501L); verifyNoInteractions(transactions);
        }
    }
}
