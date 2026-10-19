package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.ai.engineering.security.EngineeringRedisEphemeralStore;
import com.nexus.ai.engineering.security.EngineeringSecrets;
import com.nexus.ai.engineering.service.EngineeringDeviceService;
import com.nexus.common.ai.engineering.EngineeringAction;
import com.nexus.common.ai.engineering.EngineeringException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.handle.TenantKeyPrefixHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** 使用真实租户键映射和隔离存储复现浏览器到匿名连接器的配对边界。 */
class EngineeringRedisEphemeralStoreTest {
    @Test
    void browserPairingCodeCanBeRedeemedWithoutTenantContext() {
        withTenantRedis(redis -> {
            var fixture = new EngineeringFixture();
            var browserStore = new EngineeringRedisEphemeralStore(redis.client);
            var connectorStore = new EngineeringRedisEphemeralStore(redis.client);
            var browser = devices(fixture, browserStore);
            var connector = devices(fixture, connectorStore);
            var code = TenantHelper.dynamic(fixture.actor.tenantId(), () ->
                    browser.createPairingCode(fixture.actor, Set.of(EngineeringAction.CHECK_CONNECTION)));
            assertNull(TenantHelper.getTenantId());

            var credentials = connector.pair(new EngineeringDeviceService.PairRequest(
                    EngineeringDeviceService.PROTOCOL, code.pairingCode(), "匿名连接器"));

            var principal = connector.authenticate(credentials.deviceToken());
            assertEquals(fixture.actor.tenantId(), principal.actor().tenantId());
            assertEquals(fixture.actor.userId(), principal.actor().userId());
            var replay = assertThrows(EngineeringException.class, () -> connector.pair(
                    new EngineeringDeviceService.PairRequest(
                            EngineeringDeviceService.PROTOCOL, code.pairingCode(), "重放设备")));
            assertEquals("PAIRING_INVALID", replay.code());
            assertEquals(401, replay.status());
        });
    }

    @Test
    void redemptionContextCannotChangeTheOwnerBoundToTheCode() {
        withTenantRedis(redis -> {
            var fixture = new EngineeringFixture();
            var service = devices(fixture, new EngineeringRedisEphemeralStore(redis.client));
            var code = TenantHelper.dynamic(fixture.actor.tenantId(), () ->
                    service.createPairingCode(fixture.actor, Set.of(EngineeringAction.CHECK_CONNECTION)));

            var credentials = TenantHelper.dynamic("000002", () -> service.pair(
                    new EngineeringDeviceService.PairRequest(
                            EngineeringDeviceService.PROTOCOL, code.pairingCode(), "固定所有者设备")));

            assertEquals(fixture.actor.tenantId(), service.authenticate(credentials.deviceToken()).actor().tenantId());
            assertNull(TenantHelper.getTenantId());
        });
    }

    @Test
    void uploadTicketsKeepTheirTenantAndPurposeBoundaries() {
        withTenantRedis(redis -> {
            var store = new EngineeringRedisEphemeralStore(redis.client);
            String token = EngineeringSecrets.token();
            TenantHelper.dynamic("000001", () -> store.put("upload", token, "ticket", Duration.ofMinutes(5)));

            assertNull(store.consume("upload", token));
            assertNull(TenantHelper.dynamic("000002", () -> store.consume("upload", token)));
            assertNull(TenantHelper.dynamic("000001", () -> store.consume("pair", token)));
            assertEquals("ticket", TenantHelper.dynamic("000001", () -> store.consume("upload", token)));
            assertNull(TenantHelper.dynamic("000001", () -> store.consume("upload", token)));
        });
    }

    @Test
    void pairingStorageUsesOnlyDigestAndKeepsFiveMinuteExpiry() {
        withTenantRedis(redis -> {
            var store = new EngineeringRedisEphemeralStore(redis.client);
            String token = EngineeringSecrets.token();
            TenantHelper.dynamic("000001", () -> store.put("pair", token, "owner", Duration.ofMinutes(5)));

            String key = redis.values.keySet().iterator().next();
            assertFalse(key.contains(token));
            assertTrue(key.endsWith(EngineeringSecrets.hash(token)));
            assertEquals(Duration.ofMinutes(5), redis.expiries.get(key));
            assertNull(store.consume("pair", EngineeringSecrets.token()));
        });
    }

    private static EngineeringDeviceService devices(
            EngineeringFixture fixture, EngineeringRedisEphemeralStore store) {
        return new EngineeringDeviceService(
                fixture.repository, store, fixture.access, fixture.properties, fixture.clock);
    }

    /** 仅替换环境和 Redis 传输，保留 TenantHelper、NameMapper、配对服务及 H2 仓储。 */
    private static void withTenantRedis(Consumer<MappedRedis> assertions) {
        try (var spring = mockStatic(SpringUtil.class); var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try {
                assertions.accept(new MappedRedis());
            } finally {
                TenantHelper.clearDynamic();
            }
        }
    }

    /** 在内存中保存真实租户映射后的键；不连接现有 Redis 或业务数据库。 */
    private static final class MappedRedis {
        final RedissonClient client = mock(RedissonClient.class);
        final Map<String, String> values = new HashMap<>();
        final Map<String, Duration> expiries = new HashMap<>();
        final TenantKeyPrefixHandler mapper = new TenantKeyPrefixHandler("test-instance");

        @SuppressWarnings("unchecked")
        MappedRedis() {
            when(client.getBucket(anyString(), same(StringCodec.INSTANCE))).thenAnswer(invocation -> {
                String key = mapper.map(invocation.getArgument(0, String.class));
                RBucket<Object> bucket = mock(RBucket.class);
                doAnswer(write -> {
                    values.put(key, write.getArgument(0, String.class));
                    expiries.put(key, write.getArgument(1, Duration.class));
                    return null;
                }).when(bucket).set(any(), any(Duration.class));
                when(bucket.getAndDelete()).thenAnswer(read -> values.remove(key));
                return bucket;
            });
        }
    }
}
