package com.nexus.erp.wm;

import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmReceiptIqcEvidencePreciseIsolationTest {
    final WmReceiptIqcEvidenceServiceTest f = new WmReceiptIqcEvidenceServiceTest();
    void arrange() {
        f.arrange("UNQUALIFIED");
        f.mapper.selectReceiveForUpdate("000007", 101L).setStatus("FINISHED");
        var a = detail(21L, 401L); var b = detail(22L, 402L);
        when(f.mapper.selectIsolationDetails("000007", 101L)).thenReturn(List.of(a, b));
        when(f.mapper.selectIsolationReceiptTransactions("000007", 101L)).thenReturn(List.of(tx(601L, 501L, 401L), tx(602L, 502L, 402L)));
        for (long i = 1; i <= 2; i++) {
            when(f.mapper.selectIsolationStock("000007", 500L + i)).thenReturn(stock(500L + i, 400L + i, 600L + i));
            when(f.mapper.selectIsolationBatch("000007", 400L + i)).thenReturn(batch(400L + i));
            when(f.mapper.selectIsolationSourceHead("000007", 600L + i)).thenReturn(tx(600L + i, 500L + i, 400L + i));
        }
        when(f.mapper.selectIsolationStorage(701L, 801L, 901L)).thenReturn(storage());
        when(f.mapper.freezeIsolationStock(eq("000007"), any())).thenAnswer(i -> { i.<IsolationStock>getArgument(1).setFrozenFlag(1); return 1; });
        when(f.mapper.markIsolationBatch(eq("000007"), any())).thenAnswer(i -> { i.<IsolationBatch>getArgument(1).setQcStatus("UNQUALIFIED"); return 1; });
    }
    static IsolationDetail detail(Long id, Long batch) {
        var d = new IsolationDetail(); d.setTenantId("000007"); d.setDetailId(id); d.setReceiveId(101L); d.setLineId(11L);
        d.setMaterialId(201L); d.setBatchId(batch); d.setWarehouseId(701L); d.setAreaId(801L); d.setLocationId(901L); d.setDelFlag("0"); return d;
    }
    static IsolationTx tx(Long id, Long stock, Long batch) {
        var t = new IsolationTx(); t.setTenantId("000007"); t.setTxId(id); t.setStockId(stock); t.setReceiveId(101L); t.setLineId(11L);
        t.setMaterialId(201L); t.setBatchId(batch); t.setWarehouseId(701L); t.setAreaId(801L); t.setLocationId(901L);
        t.setSourceDocType("WM_MATERIAL_RECEIVE"); t.setTxType("RECEIVE_IN"); t.setTxFlag(1); t.setDelFlag("0"); return t;
    }
    static IsolationStock stock(Long id, Long batch, Long head) {
        var s = new IsolationStock(); s.setTenantId("000007"); s.setStockId(id); s.setMaterialId(201L); s.setBatchId(batch);
        s.setWarehouseId(701L); s.setAreaId(801L); s.setLocationId(901L); s.setDelFlag("0"); s.setSourceHeadTxId(head);
        s.setFrozenFlag(0); s.setVersion(3); return s;
    }
    static IsolationBatch batch(Long id) {
        var b = new IsolationBatch(); b.setTenantId("000007"); b.setBatchId(id); b.setMaterialId(201L); b.setWarehouseId(701L);
        b.setSourceDocType("WM_MATERIAL_RECEIVE"); b.setReceiveId(101L); b.setDelFlag("0"); return b;
    }
    static IsolationStorage storage() {
        var s = new IsolationStorage(); s.setWarehouseTenant("000007"); s.setWarehouseId(701L); s.setWarehouseDel("0");
        s.setAreaTenant("000007"); s.setAreaId(801L); s.setAreaWarehouseId(701L); s.setAreaDel("0");
        s.setLocationTenant("000007"); s.setLocationId(901L); s.setLocationWarehouseId(701L); s.setLocationAreaId(801L); s.setLocationDel("0"); return s;
    }
    @Test void detailOnlyTwoBatchesFreezeEveryOriginalPhysicalStockWithoutLineBatchOrQuantity() {
        arrange();
        try (var tenant = f.tenant()) {
            f.service.consumeCompleted(f.message(), x -> { });
            verify(f.mapper).freezeIsolationStock(eq("000007"), argThat(s -> s.getStockId().equals(501L)));
            verify(f.mapper).freezeIsolationStock(eq("000007"), argThat(s -> s.getStockId().equals(502L)));
            verify(f.mapper).markIsolationBatch(eq("000007"), argThat(b -> b.getBatchId().equals(401L)));
            verify(f.mapper).markIsolationBatch(eq("000007"), argThat(b -> b.getBatchId().equals(402L)));
            verify(f.mapper, never()).insertEvidence(any());
        }
    }
    @Test void negativeRepeatDoesNotIncrementAlreadyFrozenStockOrRewriteAlreadyMarkedBatches() {
        arrange();
        try (var tenant = f.tenant()) {
            f.service.consumeCompleted(f.message(), x -> { }); f.service.consumeCompleted(f.message(), x -> { });
            verify(f.mapper, times(2)).freezeIsolationStock(anyString(), any());
            verify(f.mapper, times(2)).markIsolationBatch(anyString(), any());
        }
    }
    @Test void finishedReceiptWithMissingOriginalStockTransactionIsNotZeroRowsSuccess() {
        arrange(); when(f.mapper.selectIsolationReceiptTransactions("000007", 101L)).thenReturn(List.of());
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> fail("must not report isolated")))
                .hasMessage("IQC_NEGATIVE_ORIGINAL_TARGET_MISSING");
        }
    }
    @Test void stockTenantMismatchOrMissingSourceHeadNeverGetsGuessedIntoAnotherTenant() {
        arrange(); f.mapper.selectIsolationStock("000007", 501L).setTenantId("000008");
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> { })).hasMessage("IQC_NEGATIVE_STOCK_PARENT_INVALID");
            verify(f.mapper, never()).freezeIsolationStock(anyString(), any());
        }
    }

    @Test void missingCurrentStockSourceHeadRefusesEveryWriteBeforeAnyTargetIsFrozen() {
        arrange(); when(f.mapper.selectIsolationSourceHead("000007", 601L)).thenReturn(null);
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> { })).hasMessage("IQC_NEGATIVE_STOCK_PARENT_INVALID");
            verify(f.mapper, never()).freezeIsolationStock(anyString(), any()); verify(f.mapper, never()).markIsolationBatch(anyString(), any());
        }
    }
    /** 未过账时也必须先核验所有分配明细的仓储父链，不能因没有原库存流水而跳过。 */
    @ParameterizedTest
    @ValueSource(strings = {"missingWarehouse", "deletedWarehouse", "foreignWarehouseTenant", "wrongAreaParent",
        "foreignAreaTenant", "missingArea", "wrongLocationArea", "foreignLocationTenant", "deletedLocation"})
    void unpostedReceiptWithoutTransactionsRejectsInvalidStorageBeforeAnySafetyWrite(String invalidParent) {
        arrange();
        f.mapper.selectReceiveForUpdate("000007", 101L).setStatus("APPROVED");
        when(f.mapper.selectIsolationReceiptTransactions("000007", 101L)).thenReturn(List.of());
        var parent = storage();
        switch (invalidParent) {
            case "missingWarehouse" -> parent = null;
            case "deletedWarehouse" -> parent.setWarehouseDel("2");
            case "foreignWarehouseTenant" -> parent.setWarehouseTenant("000008");
            case "wrongAreaParent" -> parent.setAreaWarehouseId(702L);
            case "foreignAreaTenant" -> parent.setAreaTenant("000008");
            case "missingArea" -> parent.setAreaId(null);
            case "wrongLocationArea" -> parent.setLocationAreaId(802L);
            case "foreignLocationTenant" -> parent.setLocationTenant("000008");
            case "deletedLocation" -> parent.setLocationDel("2");
            default -> throw new IllegalArgumentException(invalidParent);
        }
        when(f.mapper.selectIsolationStorage(701L, 801L, 901L)).thenReturn(parent);
        var projected = new AtomicBoolean();
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> projected.set(true)))
                .hasMessage("IQC_NEGATIVE_STORAGE_PARENT_INVALID");
            assertThat(projected.get()).isFalse();
            verify(f.mapper, never()).freezeIsolationStock(anyString(), any());
            verify(f.mapper, never()).markIsolationBatch(anyString(), any());
            verify(f.mapper, never()).insertEvidence(any());
        }
    }
    @Test void lastUnpostedDetailWithWrongStorageParentBlocksEarlierValidBatchUpdate() {
        arrangeUnpostedTwoBuckets();
        var lastParent = secondStorage(); lastParent.setLocationAreaId(801L);
        when(f.mapper.selectIsolationStorage(701L, 802L, 902L)).thenReturn(lastParent);
        var projected = new AtomicBoolean();
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> projected.set(true)))
                .hasMessage("IQC_NEGATIVE_STORAGE_PARENT_INVALID");
            assertThat(projected.get()).isFalse();
            verify(f.mapper, never()).freezeIsolationStock(anyString(), any());
            verify(f.mapper, never()).markIsolationBatch(anyString(), any());
        }
    }
    @Test void validUnpostedReceiptChecksEveryDetailStorageAndOnlyMarksItsOriginalBatches() {
        arrangeUnpostedTwoBuckets();
        var projected = new AtomicBoolean();
        try (var tenant = f.tenant()) {
            f.service.consumeCompleted(f.message(), x -> projected.set(true));
            assertThat(projected.get()).isTrue();
            verify(f.mapper).selectIsolationStorage(701L, 801L, 901L);
            verify(f.mapper).selectIsolationStorage(701L, 802L, 902L);
            verify(f.mapper, never()).freezeIsolationStock(anyString(), any());
            verify(f.mapper).markIsolationBatch(eq("000007"), argThat(b -> b.getBatchId().equals(401L)));
            verify(f.mapper).markIsolationBatch(eq("000007"), argThat(b -> b.getBatchId().equals(402L)));
            verify(f.mapper, never()).insertEvidence(any());
        }
    }
    void arrangeUnpostedTwoBuckets() {
        arrange(); f.mapper.selectReceiveForUpdate("000007", 101L).setStatus("APPROVED");
        when(f.mapper.selectIsolationReceiptTransactions("000007", 101L)).thenReturn(List.of());
        var first = detail(21L, 401L); var last = detail(22L, 402L);
        last.setAreaId(802L); last.setLocationId(902L);
        when(f.mapper.selectIsolationDetails("000007", 101L)).thenReturn(List.of(first, last));
        when(f.mapper.selectIsolationStorage(701L, 802L, 902L)).thenReturn(secondStorage());
    }
    static IsolationStorage secondStorage() {
        var parent = storage(); parent.setAreaId(802L); parent.setLocationId(902L); parent.setLocationAreaId(802L); return parent;
    }
    @Test void freezeCasZeroIsNotReportedAsSuccessfulProtection() {
        arrange(); when(f.mapper.freezeIsolationStock(eq("000007"), any())).thenReturn(0);
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.consumeCompleted(f.message(), x -> fail("must not report protection")))
                .hasMessage("IQC_NEGATIVE_STOCK_FREEZE_CONFLICT");
        }
    }
}
