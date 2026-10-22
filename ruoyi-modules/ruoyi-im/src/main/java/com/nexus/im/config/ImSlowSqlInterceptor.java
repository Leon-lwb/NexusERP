package com.nexus.im.config;

import com.nexus.im.push.ImMetrics;

import lombok.extern.slf4j.Slf4j;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * P5-2: 慢 SQL 拦截器（MyBatis 原生 {@link Interceptor}）。
 *
 * <p>拦截 {@link Executor#query} 与 {@link Executor#update}，对每次 SQL 执行计时：
 *
 * <ul>
 *   <li>所有查询：记录 {@code im.db.query.latency} timer（用于 P95/P99 分布分析）
 *   <li>超过阈值：记录 {@code im.db.slow{sql=sqlId}} counter +1，并输出 WARN 日志
 * </ul>
 *
 * <p>注册方式：作为 Spring {@code @Component}，由 {@code MybatisPlusAutoConfiguration} 通过 {@code
 * ObjectProvider<Interceptor[]>} 自动收集并注入到 {@code SqlSessionFactory}。 与 {@code
 * MybatisPlusInterceptor}（InnerInterceptor 机制）独立共存，互不干扰。
 *
 * <p>阈值通过 {@code im.slow-sql.threshold-ms} 配置，默认 200ms。
 *
 * @author nexus
 */
@Slf4j
@Component
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
public class ImSlowSqlInterceptor implements Interceptor {

    /** 指标采集组件，用于记录 SQL 延迟与慢 SQL 计数。 */
    private final ImMetrics imMetrics;
    /** 慢 SQL 阈值（毫秒），超过该耗时的 SQL 记为慢查询并输出 WARN 日志。 */
    private final long thresholdMs;

    public ImSlowSqlInterceptor(ImMetrics imMetrics,
                                @Value("${im.slow-sql.threshold-ms:200}") long thresholdMs) {
        this.imMetrics = imMetrics;
        this.thresholdMs = thresholdMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String sqlId = ms.getId();
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            // P5-2: 所有查询都记录 latency（用于 P95/P99 分布分析）
            imMetrics.recordDbQueryLatency(elapsed);
            // P5-2: 超阈值记录 slow counter + WARN 日志
            if (elapsed > thresholdMs) {
                imMetrics.recordSlowSql(sqlId);
                log.warn("[IM] P5-2 慢 SQL [{}ms] {} (threshold={}ms)", elapsed, sqlId, thresholdMs);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op：不依赖 mybatis 配置文件传入参数，全部通过 Spring @Value 注入
    }
}
