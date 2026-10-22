package com.nexus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nexus.ai.mcp.McpEndpointPolicy;
import com.nexus.common.core.exception.ServiceException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

/** 本机MCP端点必须来自开发配置，不能依赖启动终端或放宽其他环境。 */
@Tag("dev")
class McpLocalEndpointConfigurationTest {
    private static final String PROPERTY = "nexus.ai.mcp.allowed-local-endpoints";
    private static final String MCP = "http://127.0.0.1:19540/mcp";
    private static final String CATALOG = "http://127.0.0.1:19540/catalog";
    private static final String EXPECTED = MCP + "," + CATALOG;

    @Test
    void devProfileLoadsEndpointsWithoutEnvironmentVariable() throws IOException {
        // 不引入系统环境变量，也不启动应用或连接业务数据库。
        MockEnvironment environment = load("application-dev.yml");
        assertEquals(EXPECTED, environment.getProperty(PROPERTY));
    }

    @Test
    void devProfileAllowsOnlyTwoExactLocalEndpoints() throws IOException {
        String allowed = load("application-dev.yml").getProperty(PROPERTY);
        assertEquals(EXPECTED, allowed);
        assertEquals(MCP, McpEndpointPolicy.validateRemoteTransport(MCP, allowed));
        assertEquals(CATALOG, McpEndpointPolicy.validateRemoteTransport(CATALOG, allowed));
        // 不允许白名单扩大为任意路径、子路径或其他本机端口。
        assertThrows(ServiceException.class, () -> McpEndpointPolicy.validateRemoteTransport(
            "http://127.0.0.1:19540/other", allowed));
        assertThrows(ServiceException.class, () -> McpEndpointPolicy.validateRemoteTransport(
            "http://127.0.0.1:19540/catalog/other", allowed));
        assertThrows(ServiceException.class, () -> McpEndpointPolicy.validateRemoteTransport(
            "http://127.0.0.1:19541/mcp", allowed));
    }

    @Test
    void commonConfigurationDoesNotAllowLocalEndpointsByDefault() throws IOException {
        assertLocalEndpointDenied(load("application.yml"));
    }

    @Test
    void productionConfigurationDoesNotAllowLocalEndpointsByDefault() throws IOException {
        assertLocalEndpointDenied(load("application-prod.example.yml"));
    }

    private void assertLocalEndpointDenied(MockEnvironment environment) {
        String allowed = environment.getProperty(PROPERTY, "");
        assertEquals("", allowed);
        assertThrows(ServiceException.class, () -> McpEndpointPolicy.validateRemoteTransport(MCP, allowed));
    }

    private MockEnvironment load(String resource) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        for (var source :
                loader.load(
                        resource,
                        resource.endsWith(".example.yml")
                                ? new FileSystemResource("src/main/resources/" + resource)
                                : new ClassPathResource(resource))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
