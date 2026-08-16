package com.nexus.erp.wm;

import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 按日自增序号提供器：初始化与自增必须处于同一把锁内（问题④回归）
 *
 * <p>provider 直接注入 RedissonClient（RBucket/RAtomicLong/RLock 均为接口），
 * 避开 RedisUtils 静态初始化（其 CLIENT 依赖 SpringUtils.getBean，纯 Mockito 下不可 mockStatic）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class DailyCodeSequenceProviderTest {

    private static final String KEY = "erp:batch:seq:20260816";
    private static final String LOCK_KEY = "erp:code:seq:lock:erp:batch:seq:20260816";

    private final RedissonClient client = mock(RedissonClient.class);
    private final DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(client);

    /** 缓存未命中：锁内初始化(取库内最大序号)后再自增，返回 max+1 */
    @Test
    void next_initializesFromDbWhenCacheMiss() throws Exception {
        RLock lock = mock(RLock.class);
        RBucket<Long> bucket = mock(RBucket.class);
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(client.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(client.<Long>getBucket(KEY)).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        LongSupplier loader = mock(LongSupplier.class);
        when(loader.getAsLong()).thenReturn(5L);
        when(client.getAtomicLong(KEY)).thenReturn(atomic);
        when(atomic.incrementAndGet()).thenReturn(6L);

        long seq = provider.next(KEY, loader);

        assertEquals(6L, seq);
        verify(bucket).set(5L, Duration.ofHours(48));
        verify(loader).getAsLong();
        verify(atomic).incrementAndGet();
        verify(lock).unlock();
    }

    /** 缓存命中：不再初始化，也不再查询库内最大序号 */
    @Test
    void next_skipsInitWhenCacheHit() throws Exception {
        RLock lock = mock(RLock.class);
        RBucket<Long> bucket = mock(RBucket.class);
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(client.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(client.<Long>getBucket(KEY)).thenReturn(bucket);
        when(bucket.get()).thenReturn(5L);
        LongSupplier loader = mock(LongSupplier.class);
        when(client.getAtomicLong(KEY)).thenReturn(atomic);
        when(atomic.incrementAndGet()).thenReturn(6L);

        long seq = provider.next(KEY, loader);

        assertEquals(6L, seq);
        verify(bucket, never()).set(any(Long.class), any(Duration.class));
        verify(loader, never()).getAsLong();
        verify(atomic).incrementAndGet();
        verify(lock).unlock();
    }
}