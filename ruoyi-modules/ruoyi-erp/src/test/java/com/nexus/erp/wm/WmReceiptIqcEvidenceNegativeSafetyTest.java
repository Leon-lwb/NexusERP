package com.nexus.erp.wm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.mq.iqc.ReceiptIqcSource;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class WmReceiptIqcEvidenceNegativeSafetyTest {
    final WmReceiptIqcEvidenceServiceTest fixture = new WmReceiptIqcEvidenceServiceTest();
    ReceiptIqcSource.Inspection unqualified(BigDecimal quantity) {
        return fixture.observation("UNQUALIFIED", quantity, "a".repeat(64));
    }
    void arrange(BigDecimal quantity) {
        fixture.arrange("UNQUALIFIED");
        doReturn(unqualified(quantity)).when(fixture.source).readCompleted("000007", 501L);
        doReturn(unqualified(quantity)).when(fixture.source).readCompletedIdentity("000007", 501L);
    }
    @Test void trueUnqualifiedWithoutQuantityUnitStillRunsNegativeProtectionButNeverCreatesEvidence() {
        arrange(null);
        try (var tenant = fixture.tenant()) {
            fixture.service.consumeCompleted(fixture.message(), actual -> {
                assertThat(fixture.inErp.get()).isTrue(); assertThat(actual.result()).isEqualTo("UNQUALIFIED");
                assertThat(actual.quantity()).isNull(); assertThat(actual.receiveId()).isEqualTo(101L);
                assertThat(actual.materialId()).isEqualTo(201L);
            });
            verify(fixture.mapper, never()).insertEvidence(any()); assertThat(fixture.stored).isNull();
        }
    }
    @Test void trueNegativeIgnoresBareNumericQuantityAndCurrentUnitInformation() {
        arrange(new BigDecimal("-999.123456"));
        try (var tenant = fixture.tenant()) {
            fixture.service.consumeCompleted(fixture.message(), actual -> assertThat(actual.quantity()).isNull());
            verify(fixture.mapper, never()).selectMaterialForUpdate(anyString(), anyLong());
            verify(fixture.mapper, never()).selectUnitForUpdate(anyString(), anyLong());
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void finishedReceiptRepeatedNegativeCanReapplyIdempotentIsolationWithoutHeadMutationOrEvidence() {
        arrange(null); fixture.mapper.selectReceiveForUpdate("000007", 101L).setStatus("FINISHED");
        try (var tenant = fixture.tenant()) {
            var count = new AtomicInteger();
            fixture.service.consumeCompleted(fixture.message(), actual -> count.incrementAndGet());
            fixture.service.consumeCompleted(fixture.message(), actual -> count.incrementAndGet());
            assertThat(count.get()).isEqualTo(2); verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void forgedNegativePayloadCannotTurnActualQualifiedIntoProtectionOrRelease() {
        fixture.arrange("QUALIFIED"); var message = fixture.message(); message.setInspectResult("UNQUALIFIED");
        doReturn(fixture.observation("QUALIFIED", null, "a".repeat(64))).when(fixture.source).readCompletedIdentity("000007", 501L);
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.consumeCompleted(message, actual -> fail("must not project")))
                .hasMessage("UNIT_ORIGIN_UNPROVEN"); verifyNoInteractions(fixture.transactions);
        }
    }

    @Test void realNegativeCannotCrossTenantOrMaterialMembershipWhenErpIdentityIsWrong() {
        arrange(null);
        fixture.mapper.selectReceiveForUpdate("000007", 101L).setTenantId("000008");
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.consumeCompleted(fixture.message(), actual -> fail("must not project")))
                .hasMessage("IQC_NEGATIVE_RECEIPT_IDENTITY_INVALID");
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void missingRealMaterialLineRefusesIsolationInsteadOfGuessingAnotherMaterial() {
        arrange(null);
        doReturn(List.of())
                .when(fixture.mapper)
                .selectMaterialIdentitiesForUpdate("000007", 101L, 201L);
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.consumeCompleted(fixture.message(), actual -> fail("must not project")))
                .hasMessage("IQC_NEGATIVE_MATERIAL_IDENTITY_MISSING");
        }
    }
}
