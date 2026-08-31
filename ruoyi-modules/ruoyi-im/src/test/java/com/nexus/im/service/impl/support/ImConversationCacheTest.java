package com.nexus.im.service.impl.support;

import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.support.ImUnitTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-2: {@link ImConversationCache} 缓存命中率埋点测试。
 * <p>守护 {@code get()} 在三种场景下正确埋点：
 * <ul>
 *   <li>命中：im.cache.hit{type=conversation} +1，miss 不变</li>
 *   <li>未命中（Redis 返回 null）：im.cache.miss +1，hit 不变</li>
 *   <li>异常（Redis 不可用）：im.cache.miss +1（应用层视为未生效），hit 不变</li>
 * </ul>
 * <p>实现细节：通过子类重写 {@link ImConversationCache#readFromRedis(String)} 注入测试桩，
 * 避免触发 {@code RedisUtils} 静态初始化（其 {@code CLIENT} 字段依赖 {@code SpringUtils.getBean}）。
 *
 * @author nexus
 */
@DisplayName("P5-2 ImConversationCache 命中/未命中埋点")
class ImConversationCacheTest extends ImUnitTestBase {

    private SimpleMeterRegistry registry;
    private ImMetrics imMetrics;

    @BeforeEach
    void setUpCache() {
        registry = new SimpleMeterRegistry();
        imMetrics = new ImMetrics(registry);
    }

    /**
     * 构造一个可注入 Redis 行为的测试桩：依次返回预设值。
     * <p>每个测试用例通过 {@code TestableCache} 控制每次 {@code readFromRedis} 的返回值或抛出异常。
     */
    private static class TestableCache extends ImConversationCache {
        private final List<Object> scriptedReturns = new ArrayList<>();
        private int callIndex = 0;

        TestableCache(ImMetrics imMetrics) {
            super(imMetrics);
        }

        /** 添加一次 readFromRedis 的返回值（按调用顺序消费） */
        TestableCache thenReturn(List<ImConversationVo> value) {
            scriptedReturns.add(value);
            return this;
        }

        /** 添加一次 readFromRedis 的抛异常 */
        TestableCache thenThrow(RuntimeException ex) {
            scriptedReturns.add(ex);
            return this;
        }

        @Override
        protected List<ImConversationVo> readFromRedis(String key) {
            if (callIndex >= scriptedReturns.size()) {
                throw new IllegalStateException("No scripted return for call #" + callIndex);
            }
            Object next = scriptedReturns.get(callIndex++);
            if (next instanceof RuntimeException) {
                throw (RuntimeException) next;
            }
            @SuppressWarnings("unchecked")
            List<ImConversationVo> result = (List<ImConversationVo>) next;
            return result;
        }
    }

    @Test
    @DisplayName("get 命中缓存: im.cache.hit +1, miss 保持 0, 返回缓存对象")
    void get_cacheHit_incrementsHitCounter() {
        List<ImConversationVo> cached = new ArrayList<>();
        cached.add(new ImConversationVo());

        TestableCache cache = new TestableCache(imMetrics).thenReturn(cached);

        List<ImConversationVo> result = cache.get();

        assertThat(result).isSameAs(cached);
        assertThat(registry.counter("im.cache.hit", "type", "conversation").count()).isEqualTo(1.0);
        assertThat(registry.counter("im.cache.miss", "type", "conversation").count()).isZero();
    }

    @Test
    @DisplayName("get 未命中(Redis 返回 null): im.cache.miss +1, hit 保持 0, 返回 null")
    void get_cacheMiss_incrementsMissCounter() {
        TestableCache cache = new TestableCache(imMetrics).thenReturn(null);

        List<ImConversationVo> result = cache.get();

        assertThat(result).isNull();
        assertThat(registry.counter("im.cache.miss", "type", "conversation").count()).isEqualTo(1.0);
        assertThat(registry.counter("im.cache.hit", "type", "conversation").count()).isZero();
    }

    @Test
    @DisplayName("get Redis 异常: 计为 miss, 返回 null（降级直查 DB）")
    void get_redisException_countsAsMissAndReturnsNull() {
        TestableCache cache = new TestableCache(imMetrics)
            .thenThrow(new RuntimeException("Redis connection refused"));

        List<ImConversationVo> result = cache.get();

        assertThat(result).isNull();
        assertThat(registry.counter("im.cache.miss", "type", "conversation").count()).isEqualTo(1.0);
        assertThat(registry.counter("im.cache.hit", "type", "conversation").count()).isZero();
    }

    @Test
    @DisplayName("连续多次 get: hit/miss 计数器累加正确")
    void get_multipleCalls_accumulatesCounters() {
        List<ImConversationVo> hitList = new ArrayList<>(List.of(new ImConversationVo()));
        TestableCache cache = new TestableCache(imMetrics)
            .thenReturn(hitList)    // 第 1 次命中
            .thenReturn(null)       // 第 2 次未命中
            .thenReturn(new ArrayList<>());  // 第 3 次命中

        cache.get();
        cache.get();
        cache.get();

        assertThat(registry.counter("im.cache.hit", "type", "conversation").count()).isEqualTo(2.0);
        assertThat(registry.counter("im.cache.miss", "type", "conversation").count()).isEqualTo(1.0);
    }
}
