package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;

class DailyCodeSequenceProviderRangeTest {
    private static final String KEY = "erp:sn:seq:20261018";

    @Test
    void reservesWholeRangesAndSingleNextContinuesAfterThem() {
        RedisState redis = new RedisState();
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        AtomicInteger loads = new AtomicInteger();
        LongSupplier loader =
                () -> {
                    loads.incrementAndGet();
                    return 40L;
                };
        try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
            assertEquals(41L, provider.reserve(KEY, 3, loader));
            assertEquals(44L, provider.reserve(KEY, 2, loader));
            assertEquals(46L, provider.next(KEY, loader));
        }
        assertEquals(1, loads.get());
        assertEquals(46L, redis.values.get(KEY + ":tenant:000001").get());
    }

    @Test
    void newTenantKeysInitializeFromPersistedMaxRatherThanSharedLegacyCounter() {
        RedisState redis = new RedisState();
        redis.values.put(KEY, new AtomicLong(900L));
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
            assertEquals(21L, provider.next(KEY, () -> 20L));
        }
        try (MockedStatic<TenantHelper> tenant = tenant("000002")) {
            assertEquals(8L, provider.next(KEY, () -> 7L));
        }
        assertEquals(900L, redis.values.get(KEY).get());
        assertEquals(21L, redis.values.get(KEY + ":tenant:000001").get());
        assertEquals(8L, redis.values.get(KEY + ":tenant:000002").get());
    }

    @Test
    void concurrentRangesNeverOverlapWithMockedRedisState() throws Exception {
        RedisState redis = new RedisState();
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        AtomicInteger loads = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<List<Long>>>();
            for (int i = 0; i < 40; i++) {
                tasks.add(
                        () -> {
                            try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
                                long first =
                                        provider.reserve(
                                                KEY,
                                                7,
                                                () -> {
                                                    loads.incrementAndGet();
                                                    return 100L;
                                                });
                                return LongStream.range(first, first + 7).boxed().toList();
                            }
                        });
            }
            var all = new ArrayList<Long>();
            for (var future : executor.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
                all.addAll(future.get());
            }
            assertEquals(280, new HashSet<>(all).size());
            assertEquals(101L, all.stream().mapToLong(Long::longValue).min().orElseThrow());
            assertEquals(380L, all.stream().mapToLong(Long::longValue).max().orElseThrow());
            assertEquals(1, loads.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsRangeOverflowBeforeInitializationOrAtomicMutation() {
        RedisState redis = new RedisState();
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
            assertThrows(
                    ServiceException.class,
                    () -> provider.reserve(KEY, 3, () -> Long.MAX_VALUE - 2));
            assertTrue(redis.values.isEmpty());
            redis.values.put(KEY + ":tenant:000001", new AtomicLong(Long.MAX_VALUE - 2));
            assertThrows(ServiceException.class, () -> provider.reserve(KEY, 3, () -> 0L));
            assertEquals(Long.MAX_VALUE - 2, redis.values.get(KEY + ":tenant:000001").get());
        }
    }

    @Test
    void allowsLastRepresentableRangeWithoutWrapping() {
        RedisState redis = new RedisState();
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
            assertEquals(Long.MAX_VALUE - 1, provider.reserve(KEY, 2, () -> Long.MAX_VALUE - 2));
            assertThrows(ServiceException.class, () -> provider.next(KEY, () -> 0L));
        }
    }

    @Test
    void rejectsNonPositiveCountsBeforeRedisAccess() {
        RedisState redis = new RedisState();
        DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(redis.client);
        try (MockedStatic<TenantHelper> tenant = tenant("000001")) {
            assertThrows(ServiceException.class, () -> provider.reserve(KEY, 0, () -> 0L));
            assertThrows(ServiceException.class, () -> provider.reserve(KEY, -1, () -> 0L));
        }
        verifyNoInteractions(redis.client);
    }

    @Test
    void nextDelegatesToAOneSlotReservation() {
        RedissonClient client = mock(RedissonClient.class);
        DailyCodeSequenceProvider provider = spy(new DailyCodeSequenceProvider(client));
        LongSupplier loader = () -> 20L;
        doReturn(21L).when(provider).reserve(KEY, 1L, loader);
        assertEquals(21L, provider.next(KEY, loader));
        verify(provider).reserve(KEY, 1L, loader);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMissingTenantWhenTenantIsolationIsEnabled() {
        RedissonClient client = mock(RedissonClient.class);
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            assertThrows(
                    ServiceException.class,
                    () -> new DailyCodeSequenceProvider(client).next(KEY, () -> 0L));
        }
        verifyNoInteractions(client);
    }

    private static MockedStatic<TenantHelper> tenant(String id) {
        MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(id);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        return tenant;
    }

    /**
     * 使用有状态缓存替身验证实际锁定与号段分配，不连接外部缓存或数据库。
     */
    static class RedisState {
        final RedissonClient client = mock(RedissonClient.class);
        final Map<String, AtomicLong> values = new ConcurrentHashMap<>();
        private final Map<String, RLock> locks = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        RedisState() {
            lenient()
                    .when(client.getLock(anyString()))
                    .thenAnswer(
                            call ->
                                    locks.computeIfAbsent(
                                            call.getArgument(0),
                                            key -> {
                                                RLock lock = mock(RLock.class);
                                                ReentrantLock state = new ReentrantLock();
                                                try {
                                                    when(lock.tryLock(
                                                                    anyLong(),
                                                                    anyLong(),
                                                                    any(TimeUnit.class)))
                                                            .thenAnswer(
                                                                    invocation ->
                                                                            state.tryLock(
                                                                                    invocation
                                                                                            .getArgument(
                                                                                                    0),
                                                                                    invocation
                                                                                            .getArgument(
                                                                                                    2)));
                                                } catch (InterruptedException e) {
                                                    throw new AssertionError(e);
                                                }
                                                doAnswer(
                                                                invocation -> {
                                                                    state.unlock();
                                                                    return null;
                                                                })
                                                        .when(lock)
                                                        .unlock();
                                                return lock;
                                            }));
            lenient()
                    .when(client.getBucket(anyString()))
                    .thenAnswer(
                            call -> {
                                String key = call.getArgument(0);
                                RBucket<Object> bucket = mock(RBucket.class);
                                when(bucket.get())
                                        .thenAnswer(
                                                invocation ->
                                                        values.containsKey(key)
                                                                ? values.get(key).get()
                                                                : null);
                                lenient()
                                        .doAnswer(
                                                invocation -> {
                                                    values.put(
                                                            key,
                                                            new AtomicLong(
                                                                    invocation.getArgument(
                                                                            0, Long.class)));
                                                    return null;
                                                })
                                        .when(bucket)
                                        .set(anyLong(), any(Duration.class));
                                return bucket;
                            });
            lenient()
                    .when(client.getAtomicLong(anyString()))
                    .thenAnswer(
                            call -> {
                                String key = call.getArgument(0);
                                RAtomicLong atomic = mock(RAtomicLong.class);
                                lenient()
                                        .when(atomic.addAndGet(anyLong()))
                                        .thenAnswer(
                                                invocation ->
                                                        values.get(key)
                                                                .addAndGet(
                                                                        invocation.getArgument(0)));
                                return atomic;
                            });
        }
    }
}
