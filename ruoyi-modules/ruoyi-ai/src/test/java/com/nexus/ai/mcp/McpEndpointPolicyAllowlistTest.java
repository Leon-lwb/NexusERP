package com.nexus.ai.mcp;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 运维精确 URI 白名单的有界回归；所有 DNS 候选测试均 mock，不访问网络。 */
class McpEndpointPolicyAllowlistTest {
    private static final String LOCAL = "http://127.0.0.1:19540/mcp";
    private static final String PUBLIC = "https://93.184.216.34/mcp";

    @Test
    void exactOperatorAuthorizedLoopbackEndpointIsAllowed() {
        assertEquals(LOCAL, assertDoesNotThrow(() -> validateConfigured(LOCAL, LOCAL)));
    }

    @Test
    void exactIpv6LiteralEndpointIsAllowedWithoutWildcardExpansion() {
        String endpoint = "http://[::1]:19540/mcp";
        assertEquals(endpoint, assertDoesNotThrow(() -> validateConfigured(endpoint, endpoint)));
        assertThrows(ServiceException.class, () -> validateConfigured("http://[0:0:0:0:0:0:0:1]:19540/mcp", endpoint));
    }

    @Test
    void separateExplicitEntriesRemainExact() {
        String second = "http://10.1.2.3:19540/catalog";
        assertEquals(second, assertDoesNotThrow(() -> validateConfigured(second, LOCAL + "," + second)));
        assertThrows(ServiceException.class, () -> validateConfigured("http://10.1.2.3:19540/mcp", LOCAL + "," + second));
    }

    @Test
    void catalogAndMcpOnTheSameHostAndPortAreAuthorizedAsTwoSeparateExactUris() {
        String catalog = "http://127.0.0.1:19540/catalog";
        String approved = LOCAL + "," + catalog;
        assertEquals(LOCAL, validateConfigured(LOCAL, approved));
        assertEquals(catalog, validateConfigured(catalog, approved));
        assertThrows(ServiceException.class, () -> validateConfigured(catalog + "/", approved));
        assertThrows(ServiceException.class, () -> validateConfigured(catalog + "/child", approved));
        assertThrows(ServiceException.class, () -> validateConfigured(LOCAL + "?catalog=true", approved));
    }

    @Test
    void emptyConfigurationKeepsTheOriginalPrivateAddressDenial() {
        assertThrows(ServiceException.class, () -> validateConfigured(LOCAL, ""));
        assertThrows(ServiceException.class, () -> validateConfigured(LOCAL, null));
        assertEquals(PUBLIC, validateConfigured(PUBLIC, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://127.0.0.1:19540/mcp", "http://127.0.0.2:19540/mcp",
        "http://127.0.0.1:19541/mcp", "http://127.0.0.1:019540/mcp",
        "http://127.0.0.1/mcp", "http://127.0.0.1:19540/mcp/child",
        "http://127.0.0.1:19540/mcp-extra", "http://127.0.0.1:19540/mcp/",
        "http://127.0.0.1:19540/%6dcp", "http://127.0.0.1:19540/x/../mcp",
        "http://127.0.0.1:19540/mcp?", "http://127.0.0.1:19540/mcp?q=demo",
        "http://127.0.0.1:19540/mcp#", "http://user@127.0.0.1:19540/mcp"
    })
    void nearMatchAndDecoratedUrlsDoNotInheritTheException(String candidate) {
        assertThrows(ServiceException.class, () -> validateConfigured(candidate, LOCAL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:19540/mcp?q=demo", "http://127.0.0.1:19540/mcp#",
        "http://user@127.0.0.1:19540/mcp", "http://127.0.0.1/mcp",
        "http://127.0.0.1:19540", "http://127.0.0.1:65536/mcp",
        "http://127.0.0.*:19540/mcp", "http://127.0.0.1:19540/*",
        "http://127.0.0.1:19540/x/../mcp", "http://127.0.0.1:19540/%6dcp",
        "http://localhost:19540/mcp", "http://private.example.test:19540/mcp",
        "http://127.000.000.001:19540/mcp", "allowPrivate=true",
        "http://127.0.0.1:19540/mcp,invalid", "http://127.0.0.1:19540/mcp,"
    })
    void anyInvalidAllowlistEntryFailsClosedEvenForAPublicUrl(String configured) {
        assertThrows(ServiceException.class, () -> validateConfigured(PUBLIC, configured));
    }

    @Test
    void exactLiteralExceptionDoesNotAuthorizeOtherResolvedCandidates() throws Exception {
        InetAddress approved = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        InetAddress other = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        try (MockedStatic<InetAddress> dns = mockStatic(InetAddress.class, CALLS_REAL_METHODS)) {
            dns.when(() -> InetAddress.getAllByName("127.0.0.1")).thenReturn(new InetAddress[]{approved, other});
            assertThrows(ServiceException.class, () -> validateConfigured(LOCAL, LOCAL));
        }
    }

    @Test
    void localExceptionDoesNotRelaxNormalHostnameDnsCandidateChecks() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34});
        InetAddress privateAddress = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        try (MockedStatic<InetAddress> dns = mockStatic(InetAddress.class, CALLS_REAL_METHODS)) {
            dns.when(() -> InetAddress.getAllByName("mixed.example.test"))
                .thenReturn(new InetAddress[]{publicAddress, privateAddress});
            assertThrows(ServiceException.class, () -> validateConfigured("https://mixed.example.test/mcp", LOCAL));
        }
    }

    private String validateConfigured(String url, String allowed) {
        return McpEndpointPolicy.validate(url, allowed);
    }
}
