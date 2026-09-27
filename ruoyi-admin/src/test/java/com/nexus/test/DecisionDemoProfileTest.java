package com.nexus.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class DecisionDemoProfileTest {
    private static final String DATASOURCE = "spring.datasource.dynamic.datasource.";

    /**
     * 验证日常开发配置仍连接 plus 环境，不继承演示资源和后台任务开关。
     */
    @Test
    void plainDevKeepsDailyPlusResources() {
        try (var context = start("dev")) {
            Environment environment = context.getEnvironment();
            for (var entry : Map.of("master", "ry-vue", "erp", "ry-erp", "mes", "ry-mes", "iot", "ry-iot").entrySet()) {
                assertTrue(environment.getProperty(DATASOURCE + entry.getKey() + ".url")
                    .startsWith("jdbc:mysql://localhost:3306/" + entry.getValue() + "?"));
                assertEquals("root", environment.getProperty(DATASOURCE + entry.getKey() + ".username"));
            }
            assertEquals("localhost", environment.getProperty("spring.data.redis.host"));
            assertEquals("6379", environment.getProperty("spring.data.redis.port"));
            assertEquals("localhost", environment.getProperty("spring.rabbitmq.host"));
            assertEquals("5672", environment.getProperty("spring.rabbitmq.port"));
            assertEquals("true", environment.getProperty("snail-job.enabled"));
            assertEquals("true", environment.getProperty("spring.boot.admin.client.enabled"));
            assertEquals("true", environment.getProperty("captcha.enable"));
        }
    }

    /**
     * 按真实配置发现顺序加载隔离资源及已批准的演示后台开关。
     */
    @Test
    void demoProfileOverridesEverySharedConnectionAndDisablesBackgroundClients() {
        try (var context = start("dev,decision-demo", demoOverrides())) {
            Environment environment = context.getEnvironment();
            for (var entry : Map.of("master", "sys", "erp", "erp", "mes", "mes", "iot", "iot").entrySet()) {
                assertTrue(environment.getProperty(DATASOURCE + entry.getKey() + ".url")
                    .contains("/nexus_ai_decision_demo_" + entry.getValue() + "?"));
                assertTrue(environment.getProperty(DATASOURCE + entry.getKey() + ".url")
                    .startsWith("jdbc:mysql://127.0.0.1:13307/"));
                assertEquals("decision_demo", environment.getProperty(DATASOURCE + entry.getKey() + ".username"));
                assertEquals("test-only", environment.getProperty(DATASOURCE + entry.getKey() + ".password"));
            }
            assertEquals("127.0.0.1", environment.getProperty("spring.data.redis.host"));
            assertEquals("6385", environment.getProperty("spring.data.redis.port"));
            assertEquals("test-only", environment.getProperty("spring.data.redis.password"));
            assertEquals("127.0.0.1", environment.getProperty("spring.rabbitmq.host"));
            assertEquals("5685", environment.getProperty("spring.rabbitmq.port"));
            assertEquals("/decision-demo", environment.getProperty("spring.rabbitmq.virtual-host"));
            assertEquals("decision_demo", environment.getProperty("spring.rabbitmq.username"));
            assertEquals("test-only", environment.getProperty("spring.rabbitmq.password"));
            assertEquals("false", environment.getProperty("captcha.enable"));
            assertEquals("false", environment.getProperty("snail-job.enabled"));
            assertEquals("false", environment.getProperty("spring.boot.admin.client.enabled"));
            assertEquals("false", environment.getProperty("flowable.database-schema-update"));
            assertEquals("false", environment.getProperty("nexus.iot.mqtt.enabled"));
            assertEquals("false", environment.getProperty("nexus.iot.pipeline.consumer-enabled"));
        }
    }

    /**
     * 缺少显式演示连接参数时，在资源初始化前拒绝启动。
     */
    @Test
    void demoProfileCannotInheritDevDatabaseCredentials() {
        assertRejected("dev,decision-demo");
    }

    /**
     * 真实配置发现路径中，逆序激活不得悄然回退日常资源。
     */
    @Test
    void reversedProfilesCannotConnectDailyResources() {
        assertRejected("decision-demo,dev", demoOverrides());
    }

    /**
     * 单独演示配置及命令行混入日常连接均必须拒绝。
     */
    @Test
    void incompleteProfileOrMixedDatasourceIsRejected() {
        assertRejected("decision-demo", demoOverrides());
        assertRejected("prod,dev,decision-demo", demoOverrides());
        String[] valid = demoOverrides();
        String[] mixed = java.util.Arrays.copyOf(valid, valid.length + 1);
        mixed[valid.length] = "--spring.datasource.dynamic.datasource.erp.url=jdbc:mysql://localhost:3306/ry-erp";
        assertRejected("dev,decision-demo", mixed);
    }

    /**
     * 任意已知共享连接或后台开关混入时，均在上下文准备之前拒绝。
     *
     * @param override 待验证的单项不可信覆盖值
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "--spring.data.redis.port=6379",
        "--spring.data.redis.host=remote.invalid",
        "--spring.data.redis.url=redis://127.0.0.1:6379",
        "--spring.data.redis.cluster.nodes[0]=127.0.0.1:6379",
        "--spring.data.redis.sentinel.nodes[0]=127.0.0.1:6379",
        "--redisson.cluster-servers-config.master-connection-pool-size=16",
        "--spring.rabbitmq.port=5672",
        "--spring.rabbitmq.virtual-host=/",
        "--spring.rabbitmq.virtualHost=/",
        "--spring.datasource.dynamic.datasource.master.jndi-name=java:comp/env/jdbc/Daily",
        "--spring.datasource.dynamic.datasource.master.jndiName=java:comp/env/jdbc/Daily",
        "--spring.rabbitmq.addresses=127.0.0.1:5672",
        "--spring.rabbitmq.addresses[0]=127.0.0.1:5672",
        "--spring.rabbitmq.addresses=amqp://untrusted:do-not-echo@127.0.0.1:5672/%2f",
        "--spring.datasource.dynamic.datasource.daily.url=jdbc:mysql://localhost:3306/ry-erp",
        "--spring.datasource.dynamic.primary=daily",
        "--spring.datasource.dynamic.datasource.master.username=root",
        "--spring.datasource.dynamic.datasource.iot.password=",
        "--spring.boot.admin.client.enabled=true",
        "--flowable.database-schema-update=true",
        "--nexus.iot.pipeline.consumer-enabled=true"
    })
    void sharedResourcesAndBackgroundClientsCannotBypassGuard(String override) {
        String[] valid = demoOverrides();
        String[] mixed = java.util.Arrays.copyOf(valid, valid.length + 1);
        mixed[valid.length] = override;
        assertRejected("dev,decision-demo", mixed);
    }

    /**
     * 强制采用统一安全错误，不回显连接值或凭据。
     */
    private static void assertRejected(String profiles, String... overrides) {
        var initialized = new java.util.concurrent.atomic.AtomicBoolean();
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> start(profiles, initialized, overrides));
        assertFalse(initialized.get(), "拒绝必须发生在上下文初始化前");
        assertEquals("隔离演示资源配置不完整或不可信，应用未启动", error.getMessage());
    }

    private static String[] demoOverrides() {
        return new String[] {
"--DECISION_DEMO_DB_HOST=127.0.0.1", "--DECISION_DEMO_DB_PORT=13307",
            "--DECISION_DEMO_DB_USER=decision_demo", "--DECISION_DEMO_DB_PASSWORD=test-only",
            "--DECISION_DEMO_REDIS_HOST=127.0.0.1", "--DECISION_DEMO_REDIS_PORT=6385",
            "--DECISION_DEMO_REDIS_PASSWORD=test-only",
            "--DECISION_DEMO_MQ_HOST=127.0.0.1", "--DECISION_DEMO_MQ_PORT=5685",
            "--DECISION_DEMO_MQ_USER=decision_demo", "--DECISION_DEMO_MQ_PASSWORD=test-only",
            "--DECISION_DEMO_MQ_VHOST=/decision-demo"
        };
    }

    private static ConfigurableApplicationContext start(String profiles, String... overrides) {
        return start(profiles, new java.util.concurrent.atomic.AtomicBoolean(), overrides);
    }

    private static ConfigurableApplicationContext start(String profiles,
        java.util.concurrent.atomic.AtomicBoolean initialized, String... overrides) {
        var application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.addInitializers(context -> initialized.set(true));
        application.setDefaultProperties(Map.of("spring.config.location", "classpath:/",
            "spring.main.banner-mode", "off"));
        String[] args = new String[overrides.length + 1];
        args[0] = "--spring.profiles.active=" + profiles;
        System.arraycopy(overrides, 0, args, 1, overrides.length);
        return application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration { }
}
