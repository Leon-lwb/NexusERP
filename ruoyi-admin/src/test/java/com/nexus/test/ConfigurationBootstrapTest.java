package com.nexus.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实配置加载器验证新用户配置，不启动业务组件或连接外部资源。 */
@Tag("dev")
class ConfigurationBootstrapTest {
    @TempDir
    Path temporary;

    /** 缺少私有文件时，开发配置仍可解析且不会创建支付组件。 */
    @Test
    void cleanDevLoadsWithoutPrivateFilesOrOptionalPayment() throws Exception {
        var environment = load("dev", Map.of("DB_PASSWORD", "fixture-only"), false);
        assertEquals("fixture-only", environment.getProperty("spring.datasource.dynamic.datasource.master.password"));
        assertEquals("", environment.getProperty("mail.pass"));
        assertEquals("false", environment.getProperty("snail-job.enabled"));
        assertEquals("false", environment.getProperty("spring.boot.admin.client.enabled"));
        assertNull(environment.getProperty("sms.blends.config1.supplier"));
        assertNull(environment.getProperty("justauth.type.gitee.client-id"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(PlaceholderSupport.class, OptionalPayment.class);
            context.refresh();
            assertTrue(context.getBeansOfType(PaymentSettings.class).isEmpty());
        }
    }

    /** local复用连接契约，但不会额外激活dev或开发白名单。 */
    @Test
    void localLoadsAllDataSourcesWithoutActivatingDev() throws Exception {
        var environment = load("local", Map.of("DB_HOST", "127.0.0.1", "DB_PORT", "13306", "DB_PASSWORD", "fixture-only"), false);
        for (var entry : Map.of("master", "ry-vue", "erp", "ry-erp", "mes", "ry-mes", "iot", "ry-iot").entrySet()) {
            assertTrue(environment.getRequiredProperty("spring.datasource.dynamic.datasource." + entry.getKey() + ".url")
                .startsWith("jdbc:mysql://127.0.0.1:13306/" + entry.getValue() + "?"));
        }
        assertEquals(List.of("local"), List.of(environment.getActiveProfiles()));
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        assertEquals("false", environment.getProperty("flowable.database-schema-update"));
        assertEquals("", environment.getProperty("nexus.ai.mcp.allowed-local-endpoints", ""));
    }

    /** 未clean的输出目录可能残留旧密钥文件，公共profile不应继续读取它。 */
    @Test
    void staleClasspathSecretDoesNotOverrideExternalOnlyContract() throws Exception {
        Files.createDirectories(temporary.resolve("classpath"));
        Files.writeString(temporary.resolve("classpath/application-secret.yml"), "secret:\n  mail:\n    pass: stale-fixture-only\n");
        var environment = load("local", Map.of(), false);
        assertEquals("", environment.getProperty("mail.pass"));
    }

    /** 可选私有文件中的值由原有secret命名空间提供给使用方。 */
    @Test
    void externalSecretSuppliesOptionalCredentials() throws Exception {
        Files.createDirectories(temporary.resolve("config"));
        Files.writeString(temporary.resolve("config/application-secret.yml"), "secret:\n  mail:\n    pass: fixture-mail\n  pay:\n    alipay:\n      app-id: fixture-app\n");
        var environment = load("local", Map.of(), false);
        assertEquals("fixture-mail", environment.getProperty("mail.pass"));
        assertEquals("fixture-app", environment.getProperty("pay.alipay.appId"));
    }

    /** 外部环境文件优先于打包配置以及local导入的共享开发配置。 */
    @Test
    void externalProfileOverridesImportedDefaults() throws Exception {
        Files.createDirectories(temporary.resolve("config"));
        Files.writeString(temporary.resolve("config/application-local.yml"), "spring:\n  datasource:\n    dynamic:\n      datasource:\n        master:\n          url: jdbc:h2:mem:configuration-fixture\n");
        var environment = load("local", Map.of("spring.config.additional-location", "optional:" + temporary.resolve("config").toUri()), false);
        assertEquals("jdbc:h2:mem:configuration-fixture", environment.getProperty("spring.datasource.dynamic.datasource.master.url"));
    }

    /** 第三方厂商配置只有显式选择对应profile后才参与装配。 */
    @Test
    void smsAndSocialContractsRequireExplicitProfiles() throws Exception {
        var environment = load("local,sms,social", Map.of(), false);
        assertEquals("alibaba", environment.getProperty("sms.blends.config1.supplier"));
        assertEquals("", environment.getProperty("sms.blends.config1.access-key-id"));
        assertEquals("", environment.getProperty("justauth.type.gitee.client-id"));
    }

    /** 生产示例要求显式注入必需凭证，不能回退到公共开发密钥。 */
    @Test
    void productionTemplateRequiresSecretsAndRestrictsDefaults() throws Exception {
        var environment = load("prod", Map.of(), true);
        for (String key : List.of("spring.datasource.dynamic.datasource.master.password", "spring.data.redis.password",
            "spring.rabbitmq.password", "sa-token.jwt-secret-key", "jasypt.encryptor.password", "api-decrypt.privateKey")) {
            assertThrows(IllegalArgumentException.class, () -> environment.getRequiredProperty(key), key);
        }
        assertEquals("false", environment.getProperty("flowable.database-schema-update"));
        assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", environment.getProperty("pay.alipay.enabled"));
        assertEquals("", environment.getProperty("nexus.ai.mcp.allowed-local-endpoints", ""));
    }

    /** 生产示例能够用部署环境变量解析，不依赖作者的私有文件。 */
    @Test
    void productionTemplateAcceptsDeploymentVariables() throws Exception {
        var environment = load("prod", Map.of("PROD_DB_PASSWORD", "fixture-db", "PROD_REDIS_PASSWORD", "fixture-redis",
            "PROD_MQ_PASSWORD", "fixture-mq", "SA_TOKEN_JWT_SECRET_KEY", "fixture-jwt", "JASYPT_PASSWORD", "fixture-jasypt",
            "API_RSA_PRIVATE_KEY", "fixture-private", "API_RSA_PUBLIC_KEY", "fixture-public", "MES_API_KEY", "fixture-station",
            "MONITOR_PASSWORD", "fixture-monitor"), true);
        assertEquals("fixture-db", environment.getProperty("spring.datasource.dynamic.datasource.master.password"));
        assertEquals("fixture-private", environment.getProperty("api-decrypt.privateKey"));
        assertEquals("fixture-jwt", environment.getProperty("sa-token.jwt-secret-key"));
    }

    /** 仅复制可公开配置，并屏蔽父加载器中可能残留的本机私有资源。 */
    private ConfigurableEnvironment load(String profiles, Map<String, ?> overrides, boolean production) throws Exception {
        Path classpath = Files.createDirectories(temporary.resolve("classpath"));
        Path source = Path.of("src/main/resources");
        for (String name : List.of("application.yml", "application-dev.yml", "application-local.yml", "application-decision-demo.yml")) {
            if (Files.exists(source.resolve(name))) {
                String text = Files.readString(source.resolve(name)).replace("@profiles.active@", "dev")
                    .replace("@logging.level@", "info").replace("@monitor.username@", "fixture-user")
                    .replace("@monitor.password@", "fixture-monitor").replace("${project.version}", "fixture-version");
                Files.writeString(classpath.resolve(name), text);
            }
        }
        if (production) Files.copy(source.resolve("application-prod.example.yml"), classpath.resolve("application-prod.yml"));
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        Map<String, Object> properties = new HashMap<>(overrides);
        properties.put("spring.config.location", "classpath:/");
        properties.put("spring.profiles.active", profiles);
        properties.put("NEXUS_CONFIG_DIR", temporary.resolve("config").toString().replace('\\', '/'));
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-fixture", properties));
        try (var loader = new PublicConfigurationLoader(classpath)) {
            ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(loader), null);
        }
        return environment;
    }

    /** 配置数据探针只允许读取本次隔离目录里的application文件。 */
    private static final class PublicConfigurationLoader extends URLClassLoader {
        private final Path directory;
        private PublicConfigurationLoader(Path directory) throws IOException {
            super(new URL[]{directory.toUri().toURL()}, ConfigurationBootstrapTest.class.getClassLoader());
            this.directory = directory;
        }
        @Override
        public URL getResource(String name) {
            if (!name.startsWith("application") || !name.endsWith(".yml")) return super.getResource(name);
            Path path = directory.resolve(name);
            try { return Files.exists(path) ? path.toUri().toURL() : null; }
            catch (IOException exception) { throw new IllegalStateException(exception); }
        }
        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!name.startsWith("application") || !name.endsWith(".yml")) return super.getResources(name);
            URL resource = getResource(name);
            return Collections.enumeration(resource == null ? List.of() : List.of(resource));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlaceholderSupport {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "pay.alipay", name = "enabled", havingValue = "true")
    static class OptionalPayment {
        @Bean
        PaymentSettings payment(@Value("${pay.alipay.appId}") String appId) { return new PaymentSettings(appId); }
    }

    record PaymentSettings(String appId) { }
}
