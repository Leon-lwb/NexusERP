package com.nexus.system.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.*;
import com.nexus.system.mapper.*;
import com.nexus.system.service.ISysPermissionService;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

class SysEngineeringIdentityTest {
    @Test
    void ownerAndTenantStatusAreReadFreshAndFingerprintNeverReturnsPassword() {
        var users = mock(SysUserMapper.class);
        var tenants = mock(SysTenantMapper.class);
        var permissions = mock(ISysPermissionService.class);
        var user = new SysUser();
        user.setUserId(7L);
        user.setTenantId("000001");
        user.setStatus("0");
        user.setDelFlag("0");
        user.setPassword("test-password-hash");
        var tenant = new SysTenant();
        tenant.setTenantId("000001");
        tenant.setStatus("0");
        tenant.setDelFlag("0");
        when(users.selectOne(any())).thenReturn(user);
        when(tenants.selectOne(any())).thenReturn(tenant);
        when(permissions.getMenuPermission(7L)).thenReturn(Set.of("ai:engineering:task"));
        var adapter =
                new SysEngineeringIdentityAdapter(users, tenants, permissions, Clock.systemUTC());
        try (var scope = mockStatic(TenantHelper.class)) {
            scope.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                    .thenAnswer(i -> i.getArgument(1, Supplier.class).get());
            var first = adapter.resolve("000001", 7L);
            assertTrue(first.enabled());
            assertTrue(first.tenantEnabled());
            assertFalse(first.credentialFingerprint().contains("test-password"));
            user.setPassword("changed-test-hash");
            assertNotEquals(
                    first.credentialFingerprint(),
                    adapter.resolve("000001", 7L).credentialFingerprint());
            tenant.setStatus("1");
            assertFalse(adapter.resolve("000001", 7L).tenantEnabled());
            user.setTenantId("000002");
            assertFalse(adapter.resolve("000001", 7L).enabled());
        }
    }
}
