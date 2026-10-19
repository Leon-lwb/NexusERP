package com.nexus.erp.wm;

import com.nexus.common.trace.*;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmSerialPostedPolicyVersionReplayTest {
    @Test
    void aFulfilledGroupUsesItsFrozenPolicyVersionAfterAnAllowedMasterVersionChange() {
        var f = fixture(8, 7, "POSTED", false);
        assertTrue(f.service.post(f.request));
        verify(f.store).verifyProof(eq(f.request), any(), any());
        verify(f.store, never()).stocks(any());
        verify(f.store, never()).insertTx(any());
        verify(f.store, never()).saveStock(any());
    }

    @Test
    void anUnknownOriginalVersionCannotBeAcceptedBecauseItMatchesTheCurrentPolicy() {
        var f = fixture(7, 0, "POSTED", false);
        assertThrows(RuntimeException.class, () -> f.service.post(f.request));
    }

    @Test
    void preparedStockStillRequiresTheExactCurrentPolicyVersion() {
        var f = fixture(8, 7, "PREPARED", false);
        assertThrows(RuntimeException.class, () -> f.service.post(f.request));
        verify(f.store, never()).insertTx(any());
    }

    @Test
    void changedOriginalFingerprintCannotBeAcceptedOnTheHistoricalAdapter() {
        var f = fixture(8, 7, "POSTED", true);
        assertThrows(RuntimeException.class, () -> f.service.post(f.request));
    }

    static WmSerialInventoryServiceTest fixture(
            long current, long original, String status, boolean badHash) {
        var f = new WmSerialInventoryServiceTest();
        f.setup();
        f.store = mock(WmSerialInventoryRepository.class);
        when(f.store.frozenPolicyVersion(100L)).thenReturn(original);
        f.service = new WmSerialInventoryService(f.store);
        when(f.store.policy(20L))
                .thenReturn(new TrackingPolicy(true, true, true, current, TrackingStage.STRICT));
        var items =
                List.of(
                        new SnItem(1L, 101L, null, null, 50L),
                        new SnItem(2L, 102L, null, null, 50L));
        String hash = f.service.selectionHash(items);
        when(f.store.items(100L)).thenReturn(items);
        when(f.store.group(f.request))
                .thenReturn(
                        new WmSerialInventoryRepository.Group(
                                100L,
                                "original",
                                1,
                                badHash
                                        ? "b".repeat(64)
                                        : f.service.fingerprint(f.request, 7, hash),
                                hash,
                                new BigDecimal("2"),
                                new BigDecimal("0.6667"),
                                status,
                                200L));
        return f;
    }
}
