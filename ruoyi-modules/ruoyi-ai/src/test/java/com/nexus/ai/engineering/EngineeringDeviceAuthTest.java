package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class EngineeringDeviceAuthTest {
    @Test
    void codeIsConsumedAtomically() throws Exception {
        var f = new EngineeringFixture();
        var code = f.devices.createPairingCode(f.actor, Set.of(EngineeringAction.CHECK_CONNECTION));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> attempt =
                    () -> {
                        start.await();
                        try {
                            f.devices.pair(
                                    new EngineeringDeviceService.PairRequest(
                                            "nexus-engineering/1", code.pairingCode(), "设备"));
                            return true;
                        } catch (EngineeringException e) {
                            return false;
                        }
                    };
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            start.countDown();
            assertNotEquals(a.get(), b.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refreshRotatesAndOldTokensCannotRecover() {
        var f = new EngineeringFixture();
        var first = f.pair();
        var next = f.devices.refresh(first.refreshToken());
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(first.deviceToken()));
        assertThrows(EngineeringException.class, () -> f.devices.refresh(first.refreshToken()));
        assertEquals(
                first.deviceId(), f.devices.authenticate(next.deviceToken()).deviceId().toString());
        assertFalse(next.toString().contains(next.refreshToken()));
    }

    @Test
    void passwordChangesAndPermissionRevocationTakeEffectImmediately() {
        var f = new EngineeringFixture();
        var c = f.pair();
        when(f.identity.resolve("000001", 7L))
                .thenReturn(
                        new EngineeringOwnerStatus(
                                true, true, "new-password-state", f.actor.permissions()));
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
        when(f.identity.resolve("000001", 7L))
                .thenReturn(
                        new EngineeringOwnerStatus(
                                true, true, "password-state", Set.of("ai:engineering:device")));
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
    }

    @Test
    void disabledOwnerTenantOrToolCannotUseDevice() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new EngineeringFixture();
            var c = f.pair();
            if (mode == 2) when(f.tools.engineeringEnabled(anyString())).thenReturn(0L);
            else
                when(f.identity.resolve("000001", 7L))
                        .thenReturn(
                                new EngineeringOwnerStatus(
                                        mode != 0,
                                        mode != 1,
                                        "password-state",
                                        f.actor.permissions()));
            assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
        }
    }

    @Test
    void revokeIsOwnerScopedAndInvalidatesRefresh() {
        var f = new EngineeringFixture();
        var c = f.pair();
        assertThrows(
                EngineeringException.class,
                () ->
                        f.devices.revoke(
                                new EngineeringActor("000002", 7L, f.actor.permissions()),
                                Long.valueOf(c.deviceId())));
        f.devices.revoke(f.actor, Long.valueOf(c.deviceId()));
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
        assertThrows(EngineeringException.class, () -> f.devices.refresh(c.refreshToken()));
    }

    @Test
    void expiredAndUnknownCredentialsFailClosed() {
        var f = new EngineeringFixture();
        var c = f.pair();
        f.repository.update(
                "update ai_engineering_device set access_expires_at='2020-01-01' where device_id=?",
                Long.valueOf(c.deviceId()));
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
        assertThrows(EngineeringException.class, () -> f.devices.authenticate("forged-token"));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.devices.pair(
                                new EngineeringDeviceService.PairRequest(
                                        "wrong/2", "missing", "设备")));
    }

    @Test
    void connectorUnpairRevokesOnlyItsCurrentDevice() {
        var f = new EngineeringFixture();
        var c = f.pair();
        var other = f.pair();
        var principal = f.devices.authenticate(c.deviceToken());
        f.devices.unpair(principal);
        assertThrows(EngineeringException.class, () -> f.devices.authenticate(c.deviceToken()));
        assertThrows(EngineeringException.class, () -> f.devices.refresh(c.refreshToken()));
        assertNotNull(f.devices.authenticate(other.deviceToken()));
    }
}
