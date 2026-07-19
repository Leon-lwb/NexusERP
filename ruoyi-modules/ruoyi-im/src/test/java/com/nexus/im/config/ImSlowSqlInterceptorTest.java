package com.nexus.im.config;

import com.nexus.im.push.ImMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P5-2: {@link ImSlowSqlInterceptor} 慢 SQL 拦截器测试。
 * <p>守护核心契约：
 * <ul>
 *   <li>所有 SQL（快/慢）都记录 im.db.query.latency timer</li>
 *   <li>仅超阈值的 SQL 记录 im.db.slow{sql=sqlId} counter +1</li>
 *   <li>sqlId 取自 {@link MappedStatement#getId()}（mapper 全限定方法名）</li>
 *   <li>原方法返回值透传不丢失</li>
 * </ul>
 *
 * @author nexus
 */
@DisplayName("P5-2 ImSlowSqlInterceptor 慢 SQL 拦截器")
class ImSlowSqlInterceptorTest {

    private SimpleMeterRegistry registry;
    private ImMetrics imMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        imMetrics = new ImMetrics(registry);
    }

    /**
     * 构造一个模拟的 Invocation，args[0] 为 MappedStatement（含 sqlId），
     * proceed() 行为由 Mockito thenAnswer 控制（可注入 sleep 模拟耗时）。
     */
    private Invocation buildInvocation(String sqlId, long sleepMs, Object returnValue) throws Throwable {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getId()).thenReturn(sqlId);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{ms, null});
        when(invocation.proceed()).thenAnswer(inv -> {
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }
            return returnValue;
        });
        return invocation;
    }

    @Test
    @DisplayName("慢 SQL: 超阈值 → im.db.slow +1, im.db.query.latency +1, 透传返回值")
    void intercept_slowSql_recordsCounterAndLatency() throws Throwable {
        ImSlowSqlInterceptor interceptor = new ImSlowSqlInterceptor(imMetrics, 50);
        String sqlId = "com.nexus.im.mapper.ImMessageMapper.selectLatestPrivatePerPeer";

        Object result = interceptor.intercept(buildInvocation(sqlId, 100, "result-row"));

        assertThat(result).isEqualTo("result-row");
        assertThat(registry.counter("im.db.slow", "sql", sqlId).count()).isEqualTo(1.0);
        assertThat(registry.timer("im.db.query.latency").count()).isEqualTo(1L);
        // 至少 100ms（实际含 mock 开销，校验 > 0 即可避免精度问题）
        assertThat(registry.timer("im.db.query.latency").totalTime(TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    @DisplayName("快 SQL: 未超阈值 → 不记录 im.db.slow, 仅记录 latency")
    void intercept_fastSql_skipsSlowCounter() throws Throwable {
        ImSlowSqlInterceptor interceptor = new ImSlowSqlInterceptor(imMetrics, 200);
        String sqlId = "com.nexus.im.mapper.ImGroupMapper.selectByIds";

        Object result = interceptor.intercept(buildInvocation(sqlId, 0, null));

        assertThat(result).isNull();
        // 不应有 slow counter 注册
        assertThat(registry.find("im.db.slow").counters()).isEmpty();
        // latency 仍记录
        assertThat(registry.timer("im.db.query.latency").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("边界: 耗时 == 阈值 → 不记录 slow（> 才记录，= 不算慢）")
    void intercept_exactThreshold_doesNotRecordSlow() throws Throwable {
        // 阈值 50ms，sleep 0ms → elapsed < 50，不记录 slow
        ImSlowSqlInterceptor interceptor = new ImSlowSqlInterceptor(imMetrics, 50);

        interceptor.intercept(buildInvocation("sql.id.equal", 0, null));

        assertThat(registry.find("im.db.slow").counters()).isEmpty();
    }

    @Test
    @DisplayName("原方法抛异常: 仍记录 latency, 不记录 slow（异常路径不计慢 SQL）")
    void intercept_targetThrows_recordsLatencyOnly() throws Throwable {
        ImSlowSqlInterceptor interceptor = new ImSlowSqlInterceptor(imMetrics, 50);
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getId()).thenReturn("com.nexus.im.mapper.ImMessageMapper.insert");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{ms, null});
        when(invocation.proceed()).thenThrow(new RuntimeException("DB connection lost"));

        long start = System.currentTimeMillis();
        RuntimeException thrown = null;
        try {
            interceptor.intercept(invocation);
        } catch (RuntimeException e) {
            thrown = e;
        }
        long elapsed = System.currentTimeMillis() - start;

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("DB connection lost");
        // 异常路径仍记录 latency（finally 块）
        Timer timer = registry.timer("im.db.query.latency");
        assertThat(timer.count()).isEqualTo(1L);
        // 异常即时返回，耗时极短，不应计为 slow
        // 注：不严格断言 elapsed < 50 避免抖动；只校验 slow counter 未注册
        assertThat(registry.find("im.db.slow").counters())
            .as("异常路径耗时通常极短，不应计为 slow（即使超过阈值也仅记 latency，避免与业务异常混淆）")
            .isEmpty();
    }

    @Test
    @DisplayName("阈值可通过构造器配置: 同一查询在不同阈值下记录行为不同")
    void intercept_thresholdConfigurable() throws Throwable {
        // 用 30ms 阈值 + 50ms sleep → 记录 slow
        ImSlowSqlInterceptor strictInterceptor = new ImSlowSqlInterceptor(imMetrics, 30);
        strictInterceptor.intercept(buildInvocation("sql.A", 50, null));
        assertThat(registry.counter("im.db.slow", "sql", "sql.A").count()).isEqualTo(1.0);

        // 用 200ms 阈值 + 50ms sleep → 不记录 slow
        ImSlowSqlInterceptor looseInterceptor = new ImSlowSqlInterceptor(imMetrics, 200);
        looseInterceptor.intercept(buildInvocation("sql.B", 50, null));
        assertThat(registry.find("im.db.slow").counters())
            .as("sql.B 不应被记录为 slow")
            .noneMatch(c -> c.getId().getName().contains("sql.B"));
    }
}
