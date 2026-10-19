package com.nexus.erp.wm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.handler.State;
import org.redisson.codec.TypedJsonJacksonCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 按日自增序号提供器：验证锁内初始化、缓存整数兼容及异常值拒绝。
 *
 * <p>仅使用内存中的 JSON 编解码器和 Mockito 接口，不启动应用、不连接 Redis 或数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class DailyCodeSequenceProviderTest {

    private static final String INPUT_KEY = "erp:batch:seq:20261005";
    private static final String KEY = INPUT_KEY + ":tenant:000000";
    private static final String LOCK_KEY = "erp:code:seq:lock:" + KEY;
    private static final Duration TTL = Duration.ofHours(48);

    private final RedissonClient client = mock(RedissonClient.class);
    private final DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider(client);

    /** 缓存未命中：锁内取历史最大序号、设置原有过期时间，再原子自增。 */
    @Test
    void next_initializesFromDbWhenCacheMiss() throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(null);
        LongSupplier loader = mock(LongSupplier.class);
        when(loader.getAsLong()).thenReturn(5L);
        RAtomicLong atomic = atomicReturning(6L);

        assertEquals(6L, provider.next(INPUT_KEY, loader));

        InOrder order = inOrder(lock, bucket, loader, atomic);
        order.verify(lock).tryLock(3, 10, TimeUnit.SECONDS);
        order.verify(bucket).get();
        order.verify(loader).getAsLong();
        order.verify(bucket).set(5L, TTL);
        order.verify(atomic).addAndGet(1L);
        order.verify(lock).unlock();
        verifyNoMoreInteractions(bucket, loader, atomic);
    }

    /** 缓存命中 Long：不覆盖缓存、不刷新过期时间，也不查询历史最大序号。 */
    @Test
    void next_skipsInitWhenCacheHit() throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(5L);
        LongSupplier loader = mock(LongSupplier.class);
        RAtomicLong atomic = atomicReturning(6L);

        assertEquals(6L, provider.next(INPUT_KEY, loader));

        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verifyNoInteractions(loader);
        verify(atomic).addAndGet(1L);
        verify(lock).unlock();
    }

    /** 首次生成成功后，真实 JSON 解码得到 Integer，第二次仍须接续同一天的序号。 */
    @Test
    void next_continuesAfterFirstIncrementIsDecodedAsInteger() throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        AtomicReference<Object> cached = new AtomicReference<>();
        AtomicLong counter = new AtomicLong();
        when(bucket.get()).thenAnswer(invocation -> cached.get());
        doAnswer(invocation -> {
            counter.set(invocation.getArgument(0, Long.class));
            return null;
        }).when(bucket).set(0L, TTL);
        LongSupplier loader = mock(LongSupplier.class);
        when(loader.getAsLong()).thenReturn(0L);
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(client.getAtomicLong(KEY)).thenReturn(atomic);
        when(atomic.addAndGet(1L)).thenAnswer(invocation -> {
            long next = counter.addAndGet(1L);
            cached.set(decodeCachedInteger(next));
            return next;
        });

        assertEquals(1L, provider.next(INPUT_KEY, loader));
        assertInstanceOf(Integer.class, cached.get());
        assertEquals(2L, provider.next(INPUT_KEY, loader));

        verify(loader).getAsLong();
        verify(bucket).set(0L, TTL);
        verify(atomic, times(2)).addAndGet(1L);
        verify(lock, times(2)).tryLock(3, 10, TimeUnit.SECONDS);
        verify(lock, times(2)).unlock();
    }

    /** 整数类型及 int 边界都应直接接续 Redis 原子增量，不重建或覆盖缓存。 */
    @ParameterizedTest
    @MethodSource("validCachedIntegers")
    void next_acceptsExactCachedIntegers(Number cached) throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(cached);
        LongSupplier loader = mock(LongSupplier.class);
        long expected = cached.longValue() + 1;
        RAtomicLong atomic = atomicReturning(expected);

        assertEquals(expected, provider.next(INPUT_KEY, loader));

        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verifyNoInteractions(loader);
        verify(atomic).addAndGet(1L);
        verify(lock).unlock();
    }

    /** 分数、负值、溢出和非整数类型必须失败，不能回退初始化或改写 Redis。 */
    @ParameterizedTest
    @MethodSource("invalidCachedValues")
    void next_rejectsInvalidCacheBeforeIncrement(Object cached) throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(cached);
        LongSupplier loader = mock(LongSupplier.class);

        assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader));

        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verifyNoInteractions(loader);
        verify(client, never()).getAtomicLong(KEY);
        verify(lock).unlock();
    }

    /** 历史最大值也必须可递增，异常初始化值不能写入缓存。 */
    @ParameterizedTest
    @ValueSource(longs = {-1L, Long.MIN_VALUE, Long.MAX_VALUE})
    void next_rejectsInvalidHistoricalMaximumBeforeCacheWrite(long maximum) throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(null);
        LongSupplier loader = mock(LongSupplier.class);
        when(loader.getAsLong()).thenReturn(maximum);

        assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader));

        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verify(loader).getAsLong();
        verify(client, never()).getAtomicLong(KEY);
        verify(lock).unlock();
    }

    /** 自增返回非正值或未前进时，不将不确定结果当作可靠的新序号。 */
    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L, 4L, 5L})
    void next_rejectsNonIncreasingAtomicResult(long result) throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(5L);
        LongSupplier loader = mock(LongSupplier.class);
        RAtomicLong atomic = atomicReturning(result);

        assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader));

        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verifyNoInteractions(loader);
        verify(atomic).addAndGet(1L);
        verify(lock).unlock();
    }

    /** Redis 自增异常应原样失败并释放锁，不伪造序号或重置缓存。 */
    @Test
    void next_releasesLockWhenAtomicIncrementFails() throws Exception {
        RLock lock = acquiredLock();
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(5L);
        LongSupplier loader = mock(LongSupplier.class);
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(client.getAtomicLong(KEY)).thenReturn(atomic);
        ServiceException failure = new ServiceException("原子自增失败");
        when(atomic.addAndGet(1L)).thenThrow(failure);

        assertSame(failure, assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader)));

        verifyNoInteractions(loader);
        verify(bucket).get();
        verifyNoMoreInteractions(bucket);
        verify(lock).unlock();
    }

    /** 未获得锁时保持原有繁忙提示，不读写缓存，也不解锁他人持有的锁。 */
    @Test
    void next_doesNotTouchSequenceWhenLockIsBusy() throws Exception {
        RLock lock = mock(RLock.class);
        when(client.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(false);
        LongSupplier loader = mock(LongSupplier.class);

        assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader));

        verifyNoInteractions(loader);
        verify(client, never()).getBucket(KEY);
        verify(client, never()).getAtomicLong(KEY);
        verify(lock, never()).unlock();
    }

    /** 等锁被中断时保持线程中断标记，且不得读写序号缓存。 */
    @Test
    void next_preservesInterruptWithoutTouchingSequence() throws Exception {
        RLock lock = mock(RLock.class);
        when(client.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        LongSupplier loader = mock(LongSupplier.class);
        try {
            assertThrows(ServiceException.class, () -> provider.next(INPUT_KEY, loader));
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(loader);
            verify(client, never()).getBucket(KEY);
            verify(client, never()).getAtomicLong(KEY);
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    private static Stream<Number> validCachedIntegers() {
        return Stream.of(0, 1, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1,
            5L, Long.MAX_VALUE - 1, (byte) 1, (short) 1, BigInteger.ONE);
    }

    private static Stream<Object> invalidCachedValues() {
        return Stream.of(-1, -1L, Long.MIN_VALUE, (byte) -1, (short) -1,
            BigInteger.valueOf(-1), Long.MAX_VALUE,
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
            BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE),
            new BigDecimal("1.5"), new BigDecimal("1.0"),
            1.5D, 1.5F, Double.NaN, Double.POSITIVE_INFINITY, Float.NaN,
            "1", "1.5", "9223372036854775808", "", true, new Object());
    }

    /** 使用与仓库相同的 Object 型 JSON codec，在内存中复现 Redis 小整数解码类型。 */
    private static Object decodeCachedInteger(long value) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        TypedJsonJacksonCodec codec = new TypedJsonJacksonCodec(Object.class, mapper);
        ByteBuf buffer = Unpooled.copiedBuffer(Long.toString(value), StandardCharsets.UTF_8);
        try {
            return codec.getValueDecoder().decode(buffer, new State());
        } finally {
            buffer.release();
        }
    }

    private RLock acquiredLock() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(client.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
        return lock;
    }

    @SuppressWarnings("unchecked")
    private RBucket<Object> bucket() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(client.<Object>getBucket(KEY)).thenReturn(bucket);
        return bucket;
    }

    private RAtomicLong atomicReturning(long value) {
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(client.getAtomicLong(KEY)).thenReturn(atomic);
        when(atomic.addAndGet(1L)).thenReturn(value);
        return atomic;
    }
}
