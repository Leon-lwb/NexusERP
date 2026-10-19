package com.nexus.erp.wm;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 实际分配证明按冻结的原来源引用选取，保持原物料策略下的业务含义。
 */
class WmActualAllocationPolicyReplayTest {
    @Test
    void aNowAuditMaterialStillVerifiesItsOriginalActualAllocationWithoutNewEffects() {
        var f = new WmSerialActualConsumptionTest();
        f.setup();
        when(f.jdbc.queryForList(contains("wm_trace_material_consumption"), any(Object[].class)))
                .thenReturn(List.of(fact(f.allocation)));
        assertDoesNotThrow(() -> f.service.verifyReplay(f.manifest, List.of(f.allocation)));
        verify(f.serial, never()).isStrictTracked(any());
        verifyNoInteractions(f.posting);
        verify(f.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aMissingOriginalAllocationDoesNotBecomeOptionalWhenItsPolicyChanges() {
        var f = new WmSerialActualConsumptionTest();
        f.setup();
        assertThrows(
                RuntimeException.class,
                () -> f.service.verifyReplay(f.manifest, List.of(f.allocation)));
    }

    @Test
    void anAllocationBelongingToAnotherWorkOrderCannotProveThisManifest() {
        var f = new WmSerialActualConsumptionTest();
        f.setup();
        var wrong = fact(f.allocation);
        wrong.put("erp_wo_id", 2L);
        when(f.jdbc.queryForList(contains("wm_trace_material_consumption"), any(Object[].class)))
                .thenReturn(List.of(wrong));
        when(f.serial.isStrictTracked(20L)).thenReturn(true);
        assertThrows(
                RuntimeException.class,
                () -> f.service.verifyReplay(f.manifest, List.of(f.allocation)));
    }

    static Map<String, Object> fact(CompletionMaterialAllocation a) {
        var result = new HashMap<String, Object>();
        result.put("allocation_id", a.allocationId());
        result.put("erp_wo_id", 1L);
        result.put("material_id", a.materialId());
        result.put("reservation_id", a.reservationId());
        result.put("reservation_line_id", a.reservationLineId());
        result.put("posting_group_id", 700L);
        result.put("identity_id", a.identityId());
        result.put("source_stock_id", a.sourceStockId());
        result.put("erp_batch_id", a.erpBatchId());
        result.put("quantity", a.quantity());
        result.put("source_version", a.sourceVersion());
        result.put("evidence_hash", a.evidenceHash());
        result.put("source_event_id", a.sourceEventId());
        return result;
    }
}
