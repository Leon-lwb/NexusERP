package org.dromara.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;

import java.util.Arrays;
import java.util.Map;
import java.util.List;

/**
 * 显式演示环境的启动前资源护栏。
 *
 * <p>配置数据加载完成后、应用上下文与连接组件初始化前，核对最终生效的
 * 数据库、缓存、消息队列和后台开关。配置顺序错误或命令行混入日常资源时
 * 一律拒绝启动，不回显连接值、密码或配置解析异常。</p>
 */
public final class DecisionDemoEnvironmentGuard implements EnvironmentPostProcessor, Ordered {

    /** 数据源配置键前缀。 */
    private static final String DATASOURCE = "spring.datasource.dynamic.datasource.";

    /** 唯一允许的演示数据源与库名映射。 */
    private static final Map<String, String> SCHEMAS = Map.of(
        "master", "sys", "erp", "erp", "mes", "mes", "iot", "iot");

    /** 拒绝启动时统一输出的脱敏提示。 */
    private static final String REFUSAL = "隔离演示资源配置不完整或不可信，应用未启动";

    /**
     * 按最终配置验证演示资源，不创建连接或修改配置。
     *
     * @param environment 配置数据已加载的启动环境
     * @param application 正在准备的应用，尚未创建业务上下文
     * @throws IllegalStateException 演示环境资源不完整或存在日常连接覆盖
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (!profiles.contains("decision-demo")) {
            return;
        }
        if (!profiles.contains("dev") || profiles.contains("prod") || !validResources(environment)) {
            throw new IllegalStateException(REFUSAL);
        }
    }

    /**
     * 在标准配置数据处理器之后执行，使校验对象为最终已解析配置。
     *
     * @return 启动环境处理器执行顺序
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    /**
     * 验证固定隔离端口、库名、受限账号及禁止启用的后台组件。
     *
     * @param environment 当前启动环境
     * @return 全部已知资源满足隔离约束时返回真
     */
    private static boolean validResources(ConfigurableEnvironment environment) {
        try {
            var binder = Binder.get(environment);
            var sources = binder.bind("spring.datasource.dynamic.datasource",
                Bindable.mapOf(String.class, DataSourceProperty.class)).orElse(Map.of());
            if (!sources.keySet().equals(SCHEMAS.keySet())
                || !matches(environment, "spring.datasource.dynamic.primary", "master")
                || !binder.bind("spring.rabbitmq.addresses", Bindable.listOf(String.class)).orElse(List.of()).isEmpty()
                || hasValue(environment, "spring.data.redis.url")
                || hasConfiguration(binder, "spring.data.redis.cluster")
                || hasConfiguration(binder, "spring.data.redis.sentinel")
                || hasConfiguration(binder, "redisson.cluster-servers-config")) {
                return false;
            }
            for (var entry : SCHEMAS.entrySet()) {
                String prefix = DATASOURCE + entry.getKey();
                String expected = "jdbc:mysql://127.0.0.1:13307/nexus_ai_decision_demo_" + entry.getValue() + "?";
                String url = binder.bind(prefix + ".url", String.class).orElse(null);
                if (url == null || !url.startsWith(expected)
                    || !matches(environment, prefix + ".username", "decision_demo")
                    || !hasValue(environment, prefix + ".password")
                    || hasValue(environment, prefix + ".jndi-name")) {
                    return false;
                }
            }
            return matches(environment, "spring.data.redis.host", "127.0.0.1")
                && matches(environment, "spring.data.redis.port", "6385")
                && matches(environment, "spring.data.redis.database", "0")
                && hasValue(environment, "spring.data.redis.password")
                && matches(environment, "spring.rabbitmq.host", "127.0.0.1")
                && matches(environment, "spring.rabbitmq.port", "5685")
                && matches(environment, "spring.rabbitmq.username", "decision_demo")
                && matches(environment, "spring.rabbitmq.virtual-host", "/decision-demo")
                && hasValue(environment, "spring.rabbitmq.password")
                && matches(environment, "snail-job.enabled", "false")
                && matches(environment, "spring.boot.admin.client.enabled", "false")
                && matches(environment, "flowable.database-schema-update", "false")
                && matches(environment, "nexus.iot.mqtt.enabled", "false")
                && matches(environment, "nexus.iot.pipeline.consumer-enabled", "false");
        } catch (RuntimeException exception) {
            // 未解析变量或绑定异常可能包含敏感来源，只返回固定拒绝，不传播原始异常。
            return false;
        }
    }

    /**
     * 按与组件一致的宽松绑定规则检查是否配置了备用连接模式。
     *
     * @param binder 当前环境的配置绑定器
     * @param prefix 固定的备用模式前缀
     * @return 存在任何已绑定子配置时返回真
     */
    private static boolean hasConfiguration(Binder binder, String prefix) {
        return binder.bind(prefix, Bindable.mapOf(String.class, Object.class)).isBound();
    }

    /**
     * 比较已解析配置值，不在错误输出中暴露值。
     *
     * @param environment 当前启动环境
     * @param key 固定配置键
     * @param expected 允许的非敏感字面量
     * @return 配置值完全相同时返回真
     */
    private static boolean matches(ConfigurableEnvironment environment, String key, String expected) {
        return expected.equals(Binder.get(environment).bind(key, String.class).orElse(null));
    }

    /**
     * 仅检查配置值是否存在，不输出或记录内容。
     *
     * @param environment 当前启动环境
     * @param key 固定配置键
     * @return 配置值非空白时返回真
     */
    private static boolean hasValue(ConfigurableEnvironment environment, String key) {
        String value = Binder.get(environment).bind(key, String.class).orElse(null);
        return value != null && !value.isBlank();
    }
}
