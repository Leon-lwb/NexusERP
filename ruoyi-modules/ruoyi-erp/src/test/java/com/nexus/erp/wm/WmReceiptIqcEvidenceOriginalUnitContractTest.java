package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 绝不能用当前 ERP 基本单位伪造缺失的历史数量单位。 */
class WmReceiptIqcEvidenceOriginalUnitContractTest {
    final WmReceiptIqcEvidenceServiceTest fixture = new WmReceiptIqcEvidenceServiceTest();
    static final String MISSING = "UNIT_ORIGIN_UNPROVEN";

    void currentUnit401() {
        fixture.arrange("QUALIFIED");
        fixture.mapper.selectMaterialForUpdate("000007", 201L).setBaseUomId(401L);
        ReceiptLine first = fixture.line(11L, "5.0000"), second = fixture.line(12L, "7.0000");
        first.setBaseUomId("401"); second.setBaseUomId("401");
        when(fixture.mapper.selectMaterialLinesForUpdate("000007", 101L, 201L)).thenReturn(List.of(first, second));
        NativeUnit unit = new NativeUnit(); unit.setTenantId("000007"); unit.setUomId(401L);
        unit.setQuantityScale(4); unit.setStatus(1); unit.setDelFlag("0");
        when(fixture.mapper.selectUnitForUpdate("000007", 401L)).thenReturn(unit);
    }
    @Test void oldUnitlessMesQuantity12CannotBecomeCertifiedCurrentUnit401() {
        currentUnit401();
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.consumeCompleted(fixture.message(), x -> fail("must not project")))
                .isInstanceOf(ServiceException.class).hasMessage(MISSING);
            verifyNoInteractions(fixture.transactions);
            verify(fixture.mapper, never()).insertEvidence(any());
            assertThat(fixture.stored).isNull();
        }
    }
    @Test void numericallyEqualWholeCurrentReceiptAndUnchangedUnitStillCannotInventBirthContract() {
        fixture.arrange("QUALIFIED");
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.consumeCompleted(fixture.message(), x -> fail("must not project")))
                .hasMessage(MISSING);
            verifyNoInteractions(fixture.transactions);
            verify(fixture.mapper, never()).selectMaterialLinesForUpdate(anyString(), anyLong(), anyLong());
        }
    }
    @Test void duplicateDonePreflightCannotUpgradeOldUnitlessInspectionByReadingNewErpUnit() {
        currentUnit401();
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> fixture.service.verifyReplay(fixture.message())).hasMessage(MISSING);
            verifyNoInteractions(fixture.transactions);
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void oldPersistedV1EvidenceWithValidHashesStillDoesNotProveOriginalQuantityUnit() {
        currentUnit401();
        try (var tenant = fixture.tenant()) {
            // 使用明确的旧记录测试夹具，重建已持久化的历史 V1 记录。
            // 这是测试夹具，不是公开的已验证 DTO，也不是创建时来源采集。
            fixture.stored = fixture.legacyEvidence("QUALIFIED");
            assertThat(fixture.stored.getBaseUomId()).isEqualTo(401L);
            clearInvocations(fixture.mapper, fixture.source, fixture.transactions);

            assertThatThrownBy(() -> fixture.service.requireLocked("000007", 101L, 201L, 401L, new BigDecimal("12")))
                .hasMessage(MISSING);
            verifyNoInteractions(fixture.source, fixture.transactions);
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void inspectionMeasurementUnitDoesNotSupplyTheMissingQuantityUnitContract() {
        fixture.arrange("QUALIFIED");
        try (var tenant = fixture.tenant()) {
            // 来源数据主体可以包含 qc_inspect_detail.unit=mm；Inspection 不包含数量单位。
            when(fixture.source.readCompleted("000007", 501L)).thenReturn(
                fixture.observation("QUALIFIED", new BigDecimal("12"), "c".repeat(64)));
            assertThatThrownBy(() -> fixture.service.consumeCompleted(fixture.message(), x -> fail("must not project")))
                .hasMessage(MISSING);
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
}
