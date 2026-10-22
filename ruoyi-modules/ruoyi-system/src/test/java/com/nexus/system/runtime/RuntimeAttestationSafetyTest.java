package com.nexus.system.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.GroupDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.satoken.utils.LoginHelper;
import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.sql.DataSource;

/** 所有 JDBC、认证和 HTTP 对象均为离线模拟对象；不连接服务或真实数据库。 */
class RuntimeAttestationSafetyTest {
    private static final String PROVIDER = "com.nexus.system.runtime.RuntimeAttestationProvider";
    private static final String CONTROLLER =
            "com.nexus.system.controller.system.SysRuntimeAttestationController";
    private static final String CHALLENGE = "a".repeat(64);
    private static final String USER_ID = "9223372036854775807";
    private static final String TENANT = "000000";
    private static final String UUID = "12345678-1234-1234-1234-123456789abc";
    private final ObjectMapper json = new ObjectMapper();
    private MockedStatic<StpUtil> stp;
    private MockedStatic<LoginHelper> login;
    private DynamicRoutingDataSource router;
    private MockEnvironment environment;
    private Map<String, DataSource> sources;
    private final List<Connection> connections = new ArrayList<>();
    private final List<Statement> statements = new ArrayList<>();
    private final List<ResultSet> results = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        stp = mockStatic(StpUtil.class);
        login = mockStatic(LoginHelper.class);
        LoginUser actor = new LoginUser();
        actor.setUserId(Long.MAX_VALUE);
        actor.setTenantId(TENANT);
        login.when(LoginHelper::getUserIdStr).thenReturn(USER_ID);
        login.when(LoginHelper::getTenantId).thenReturn(TENANT);
        login.when(LoginHelper::getLoginUser).thenReturn(actor);
        environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        router = mock(DynamicRoutingDataSource.class);
        sources = new LinkedHashMap<>();
        String[][] names = {
            {"master", "ry-vue"}, {"erp", "ry-erp"}, {"mes", "ry-mes"}, {"iot", "ry-iot"}
        };
        for (String[] item : names) {
            HikariDataSource pool = mock(HikariDataSource.class);
            Connection c = mock(Connection.class);
            Statement s = mock(Statement.class);
            ResultSet r = mock(ResultSet.class);
            when(pool.getConnection()).thenReturn(c);
            when(c.getAutoCommit()).thenReturn(true);
            when(c.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(c.createStatement()).thenReturn(s);
            when(s.executeQuery(anyString())).thenReturn(r);
            when(r.next()).thenReturn(true, false);
            when(r.getInt(1)).thenReturn(3306);
            when(r.getString(2)).thenReturn(UUID);
            when(r.getString(3)).thenReturn(item[1]);
            sources.put(item[0], pool);
            connections.add(c);
            statements.add(s);
            results.add(r);
        }
        when(router.getDataSources()).thenAnswer(i -> sources);
        when(router.getGroupDataSources()).thenReturn(Map.of());
        when(router.getDataSource(anyString())).thenAnswer(i -> sources.get(i.getArgument(0)));
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        login.close();
        stp.close();
    }

    private Class<?> required(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            fail("required authenticated runtime implementation missing: " + name);
            return null;
        }
    }

    private Object provider() throws Exception {
        return required(PROVIDER)
                .getConstructor(DataSource.class, Environment.class)
                .newInstance(router, environment);
    }

    private Object inspect(Object provider, String challenge) throws Exception {
        try {
            return provider.getClass()
                    .getMethod("inspect", String.class)
                    .invoke(provider, challenge);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw e;
        }
    }

    private JsonNode report() throws Exception {
        return json.valueToTree(inspect(provider(), CHALLENGE));
    }

    private Object controller(Object provider) throws Exception {
        return required(CONTROLLER).getConstructor(required(PROVIDER)).newInstance(provider);
    }

    private JsonNode call(
            Object controller, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        Object result =
                controller
                        .getClass()
                        .getMethod("attest", HttpServletRequest.class, HttpServletResponse.class)
                        .invoke(controller, request, response);
        return json.valueToTree(result);
    }

    private String sha(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void noBorrow() throws Exception {
        for (DataSource ds : sources.values()) verify(ds, never()).getConnection();
    }

    @Test
    void authenticatedActualFactsStillRefuseUnprovedLoadedBuild() throws Exception {
        JsonNode r = report();
        assertEquals("BLOCKED", r.path("status").asText());
        assertEquals("ATTESTATION_BUILD_UNPROVEN", r.path("diagnosticCategory").asText());
        assertFalse(r.path("businessWriteAuthorized").asBoolean(true));
        assertEquals("OBSERVED", r.path("datasourceObservationStatus").asText());
        assertEquals(4, r.path("datasources").size());
        assertFalse(r.has("verified"));
        assertFalse(r.has("artifactSha256"));
        assertEquals("UNPROVEN", r.path("build").path("proofStatus").asText());
    }

    @Test
    void constructorDoesNotBorrowConnectionsOrAuthorize() throws Exception {
        provider();
        noBorrow();
        stp.verifyNoInteractions();
    }

    @Test
    void existingSaTokenLoginAndExplicitReadPermissionAreEnforced() throws Exception {
        report();
        stp.verify(StpUtil::checkLogin);
        stp.verify(() -> StpUtil.checkPermission("system:runtime:attest"));
    }

    @Test
    void actorTenantAndChallengeAreHashedFromLiveIdentityNotCallerFlags() throws Exception {
        JsonNode r = report();
        assertEquals(sha(USER_ID), r.path("principalSha256").asText());
        assertEquals(sha(TENANT), r.path("tenantSha256").asText());
        assertEquals(sha(CHALLENGE), r.path("challengeSha256").asText());
        String body = r.toString();
        assertFalse(body.contains(USER_ID));
        assertFalse(body.contains(UUID));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("jdbc:mysql"));
    }

    @Test
    void serverNonceChangesOnEveryCallAndProcessIdentityIsActual() throws Exception {
        Object p = provider();
        JsonNode first = json.valueToTree(inspect(p, CHALLENGE));
        for (ResultSet r : results) when(r.next()).thenReturn(true, false);
        JsonNode second = json.valueToTree(inspect(p, CHALLENGE));
        assertNotEquals(
                first.path("serverNonceSha256").asText(),
                second.path("serverNonceSha256").asText());
        assertEquals(
                first.path("runtime").path("instanceSha256"),
                second.path("runtime").path("instanceSha256"));
        assertEquals(ProcessHandle.current().pid(), first.path("runtime").path("pid").asLong());
        assertEquals(
                ProcessHandle.current().info().startInstant().orElseThrow(),
                Instant.parse(first.path("runtime").path("startedAt").asText()));
        assertNotNull(OffsetDateTime.parse(first.path("observedAt").asText()));
    }

    @Test
    void eachBoundDatasourceIsBorrowedAndOnlyReadonlyIdentitySqlIsExecuted() throws Exception {
        JsonNode r = report();
        for (int i = 0; i < 4; i++) {
            verify(connections.get(i)).setReadOnly(true);
            verify(connections.get(i)).setAutoCommit(false);
            verify(connections.get(i)).rollback();
            verify(connections.get(i)).close();
            verify(connections.get(i), never()).commit();
            verify(statements.get(i)).setQueryTimeout(3);
            verify(statements.get(i))
                    .execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
            verify(statements.get(i)).executeQuery("SELECT @@port,@@server_uuid,DATABASE()");
            verify(statements.get(i), never()).executeUpdate(anyString());
        }
        assertEquals(
                sha(UUID), r.path("datasources").path("erp").path("serverUuidSha256").asText());
        assertEquals(
                "LIVE_JDBC_METADATA_SELECT",
                r.path("datasources").path("mes").path("identitySource").asText());
    }

    @Test
    void missingBindingDoesNotFallbackToPrimary() throws Exception {
        sources.remove("erp");
        JsonNode r = report();
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN", r.path("diagnosticCategory").asText());
        noBorrow();
    }

    @Test
    void groupResolutionAmbiguityIsDeniedBeforeBorrow() throws Exception {
        when(router.getGroupDataSources()).thenReturn(Map.of("erp", mock(GroupDataSource.class)));
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        noBorrow();
    }

    @Test
    void sharedPhysicalPoolAndUnknownProxyAreDeniedBeforeBorrow() throws Exception {
        sources.put("erp", sources.get("master"));
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        noBorrow();
    }

    @Test
    void unknownRegisteredDatasourceIsNotUnwrappedOrBorrowed() throws Exception {
        DataSource unknown = mock(DataSource.class);
        sources.put("erp", unknown);
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        verifyNoInteractions(unknown);
        noBorrow();
    }

    @Test
    void actualDatasourceIdentityNotConfiguredUrlDeterminesTarget() throws Exception {
        when(results.get(1).getString(3)).thenReturn("private-other-schema");
        JsonNode r = report();
        assertEquals(
                "ATTESTATION_DATASOURCE_TARGET_UNPROVEN", r.path("diagnosticCategory").asText());
        assertFalse(r.toString().contains("private-other-schema"));
        for (Connection c : connections) verify(c, never()).getMetaData();
    }

    @Test
    void wrongActualPortIsDenied() throws Exception {
        when(results.get(0).getInt(1)).thenReturn(3307);
        assertEquals(
                "ATTESTATION_DATASOURCE_TARGET_UNPROVEN",
                report().path("diagnosticCategory").asText());
    }

    @Test
    void cleanupFailureIsNotSilentlyObservedOrLeaked() throws Exception {
        doThrow(new SQLException("jdbc:mysql://private-host password=private-token"))
                .when(connections.get(0))
                .rollback();
        JsonNode r = report();
        assertEquals(
                "ATTESTATION_READONLY_CLEANUP_UNPROVEN", r.path("diagnosticCategory").asText());
        assertFalse(r.toString().contains("private-token"));
        verify(connections.get(0)).close();
        verify(connections.get(0), never()).setAutoCommit(true);
        verify(connections.get(0), never()).commit();
    }

    @Test
    void failedBorrowUsesFixedDiagnosticAndNoRawConnectionError() throws Exception {
        when(sources.get("master").getConnection())
                .thenThrow(new SQLException("private-password jdbc:mysql://private-target"));
        JsonNode r = report();
        assertEquals("ATTESTATION_DATASOURCE_READ_FAILED", r.path("diagnosticCategory").asText());
        assertFalse(r.toString().contains("private-password"));
        assertFalse(r.toString().contains("private-target"));
    }

    @Test
    void activeSpringTransactionIsNeverJoinedOrRolledBack() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(
                "ATTESTATION_AMBIENT_TRANSACTION_UNSUPPORTED",
                report().path("diagnosticCategory").asText());
        noBorrow();
    }

    @Test
    void bindingReplacementDuringReadRejectsTheObservedSnapshot() throws Exception {
        when(results.get(0).getString(3))
                .thenAnswer(
                        i -> {
                            sources.put("erp", mock(HikariDataSource.class));
                            return "ry-vue";
                        });
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
    }

    @Test
    void knownDirectItemAndP6ItemUseActualBoundConnections() throws Exception {
        HikariDataSource direct = (HikariDataSource) sources.get("master");
        sources.put("master", new ItemDataSource("master", direct, direct, false, false, null));
        HikariDataSource physical = (HikariDataSource) sources.get("erp");
        P6DataSource p6 = new P6DataSource(physical);
        sources.put("erp", new ItemDataSource("erp", physical, p6, true, false, null));
        assertEquals("OBSERVED", report().path("datasourceObservationStatus").asText());
        verify(direct).getConnection();
        verify(physical).getConnection();
    }

    @Test
    void staleOrMalformedAuthContextCannotReachDatasourceReads() throws Exception {
        login.when(LoginHelper::getTenantId).thenReturn("123456");
        Object p = provider();
        RuntimeException e = assertThrows(RuntimeException.class, () -> inspect(p, CHALLENGE));
        assertEquals("ATTESTATION_AUTH_CONTEXT_UNPROVEN", e.getMessage());
        noBorrow();
    }

    @Test
    void deniedReadPermissionNeverBorrowsOrEchoesRawAuthException() throws Exception {
        stp.when(() -> StpUtil.checkPermission("system:runtime:attest"))
                .thenThrow(new IllegalStateException("private-token"));
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> inspect(provider(), CHALLENGE));
        assertEquals("ATTESTATION_PERMISSION_DENIED", e.getMessage());
        assertFalse(e.toString().contains("private-token"));
        noBorrow();
    }

    @Test
    void looseClassAndNoManifestCannotBePromotedByCurrentGitOrDiskResources() throws Exception {
        JsonNode r = report();
        assertEquals("UNPROVEN", r.path("build").path("proofStatus").asText());
        assertEquals("NONE", r.path("build").path("supportedProofDomain").asText());
        assertFalse(r.toString().contains("gitCommit"));
        assertFalse(r.toString().contains("manifestClassSha256"));
    }

    @Test
    void endpointHasOnlyGetAndExplicitPermission() throws Exception {
        Class<?> c = required(CONTROLLER);
        assertArrayEquals(
                new String[] {"/system/runtime"}, c.getAnnotation(RequestMapping.class).value());
        Method method = c.getMethod("attest", HttpServletRequest.class, HttpServletResponse.class);
        assertArrayEquals(
                new String[] {"/attestation"}, method.getAnnotation(GetMapping.class).value());
        assertEquals(
                "system:runtime:attest", method.getAnnotation(SaCheckPermission.class).value()[0]);
    }

    @Test
    void validEndpointRespondsWithNonSuccessBusinessCodeAndNoStore() throws Exception {
        Object c = controller(provider());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        request.addParameter("challenge", CHALLENGE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JsonNode r = call(c, request, response);
        assertEquals(412, r.path("code").asInt());
        assertEquals("ATTESTATION_BUILD_UNPROVEN", r.path("msg").asText());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("BLOCKED", r.path("data").path("status").asText());
    }

    @Test
    void extraDuplicateMissingAndMalformedQueryParametersRejectBeforeDatabaseRead()
            throws Exception {
        Object c = controller(provider());
        List<MockHttpServletRequest> cases = new ArrayList<>();
        MockHttpServletRequest missing =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        cases.add(missing);
        MockHttpServletRequest extra =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        extra.addParameter("challenge", CHALLENGE);
        extra.addParameter("verified", "true");
        cases.add(extra);
        MockHttpServletRequest duplicate =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        duplicate.addParameter("challenge", CHALLENGE, CHALLENGE);
        cases.add(duplicate);
        MockHttpServletRequest bad =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        bad.addParameter("challenge", "private-token");
        cases.add(bad);
        for (MockHttpServletRequest request : cases) {
            JsonNode r = call(c, request, new MockHttpServletResponse());
            assertEquals(400, r.path("code").asInt());
            assertFalse(r.toString().contains("private-token"));
        }
        noBorrow();
    }

    @Test
    void getBodyAndNonGetAreNeverAlternativeAuthorityInput() throws Exception {
        Object c = controller(provider());
        MockHttpServletRequest body =
                new MockHttpServletRequest("GET", "/system/runtime/attestation");
        body.addParameter("challenge", CHALLENGE);
        body.setContent("{\"verified\":true}".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, call(c, body, new MockHttpServletResponse()).path("code").asInt());
        MockHttpServletRequest post =
                new MockHttpServletRequest("POST", "/system/runtime/attestation");
        post.addParameter("challenge", CHALLENGE);
        assertEquals(405, call(c, post, new MockHttpServletResponse()).path("code").asInt());
        noBorrow();
    }

    @Test
    void authSessionBackendFailureIsRedactedAndNeverReachesJdbc() throws Exception {
        login.when(LoginHelper::getLoginUser)
                .thenThrow(new IllegalStateException("private-auth-token"));
        RuntimeException error =
                assertThrows(RuntimeException.class, () -> inspect(provider(), CHALLENGE));
        assertEquals("ATTESTATION_AUTH_CONTEXT_UNPROVEN", error.getMessage());
        assertFalse(error.toString().contains("private-auth-token"));
        noBorrow();
    }

    @Test
    void uncertainAutocommitTransitionStillRollsBackBeforeClose() throws Exception {
        doThrow(new SQLException("private-jdbc-secret"))
                .when(connections.get(0))
                .setAutoCommit(false);
        assertEquals(
                "ATTESTATION_DATASOURCE_READ_FAILED", report().path("diagnosticCategory").asText());
        verify(connections.get(0)).rollback();
        verify(connections.get(0)).close();
        verify(connections.get(0), never()).commit();
    }

    @Test
    void connectionCloseFailureIsFixedAndNotObserved() throws Exception {
        doThrow(new SQLException("private-close-secret")).when(connections.get(0)).close();
        JsonNode r = report();
        assertEquals(
                "ATTESTATION_READONLY_CLEANUP_UNPROVEN", r.path("diagnosticCategory").asText());
        assertEquals("UNPROVEN", r.path("datasourceObservationStatus").asText());
        assertFalse(r.toString().contains("private-close-secret"));
    }

    @Test
    void permissionDenialAuditHasOnlyCanonicalActorHashesAndFixedCategory() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PROVIDER);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            stp.when(() -> StpUtil.checkPermission("system:runtime:attest"))
                    .thenThrow(new IllegalStateException("private-audit-secret"));
            assertThrows(RuntimeException.class, () -> inspect(provider(), CHALLENGE));
            String event = appender.list.get(appender.list.size() - 1).getFormattedMessage();
            assertTrue(event.contains(sha(USER_ID)));
            assertTrue(event.contains(sha(TENANT)));
            assertTrue(event.contains("ATTESTATION_PERMISSION_DENIED"));
            assertFalse(event.contains("private-audit-secret"));
            assertFalse(event.contains(USER_ID));
            assertFalse(event.contains("jdbc:"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void agentsAndHotReloadExplicitlyRefuseEvenPackagedLookingClaims() throws Exception {
        Class<?> assessment = required("com.nexus.system.runtime.LoadedArtifactAssessment");
        Method method = assessment.getMethod("inspect", Class.class, List.class, boolean.class);
        for (String argument :
                List.of(
                        "-javaagent:private-agent.jar",
                        "-agentlib:jdwp",
                        "-agentpath:private-agent.dll")) {
            JsonNode r =
                    json.valueToTree(
                            method.invoke(
                                    null,
                                    RuntimeAttestationSafetyTest.class,
                                    List.of(argument),
                                    false));
            assertEquals("ATTESTATION_AGENTS_UNSUPPORTED", r.path("diagnosticCategory").asText());
            assertEquals("NONE", r.path("supportedProofDomain").asText());
            assertFalse(r.toString().contains("private-agent"));
        }
        JsonNode hot =
                json.valueToTree(
                        method.invoke(null, RuntimeAttestationSafetyTest.class, List.of(), true));
        assertEquals("ATTESTATION_HOTRELOAD_UNSUPPORTED", hot.path("diagnosticCategory").asText());
    }

    public static final class PackedAnchor {}

    @Test
    void embeddedCallerFabricatedManifestCannotTurnJarPresenceIntoLoadedByteProof(
            @TempDir Path temp) throws Exception {
        String binary = PackedAnchor.class.getName();
        String resource = binary.replace('.', '/') + ".class";
        Path jar = temp.resolve("unsigned-runtime-fixture.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(resource));
            try (var bytes = PackedAnchor.class.getClassLoader().getResourceAsStream(resource)) {
                out.write(bytes.readAllBytes());
            }
            out.closeEntry();
            out.putNextEntry(new JarEntry("META-INF/nexus/runtime-build-manifest.json"));
            out.write(
                    ("{\"gitCommit\":\"" + "a".repeat(40) + "\",\"verified\":true}")
                            .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            Class<?> anchor = Class.forName(binary, false, loader);
            Method method =
                    required("com.nexus.system.runtime.LoadedArtifactAssessment")
                            .getMethod("inspect", Class.class, List.class, boolean.class);
            JsonNode r = json.valueToTree(method.invoke(null, anchor, List.of(), false));
            assertEquals("DIRECT_JAR", r.path("originKind").asText());
            assertEquals("UNTRUSTED_PRESENT", r.path("manifestObservation").asText());
            assertEquals("UNPROVEN", r.path("proofStatus").asText());
            assertEquals("NONE", r.path("supportedProofDomain").asText());
            assertFalse(r.toString().contains("gitCommit"));
            assertFalse(r.toString().contains("verified"));
        }
    }

    @Test
    void malformedProfileLookupCannotEchoConfigurationOrBorrowConnections() throws Exception {
        Environment broken = mock(Environment.class);
        when(broken.getActiveProfiles())
                .thenThrow(new IllegalArgumentException("private-profile-secret"));
        Object provider =
                required(PROVIDER)
                        .getConstructor(DataSource.class, Environment.class)
                        .newInstance(router, broken);
        RuntimeException error =
                assertThrows(RuntimeException.class, () -> inspect(provider, CHALLENGE));
        assertEquals("ATTESTATION_PROFILE_UNSUPPORTED", error.getMessage());
        assertFalse(error.toString().contains("private-profile-secret"));
        noBorrow();
    }

    @Test
    void finalCommitConfigOrCallerProofFlagsCannotEnableIssuance() throws Exception {
        environment.setProperty("git.commit.id", "a".repeat(40));
        environment.setProperty("runtime.verified", "true");
        environment.setProperty("runtime.artifact.sha256", "b".repeat(64));
        JsonNode r = report();
        assertEquals("BLOCKED", r.path("status").asText());
        assertEquals("NONE", r.path("build").path("supportedProofDomain").asText());
        assertFalse(r.toString().contains("gitCommit"));
        assertFalse(r.toString().contains("artifactSha256"));
    }

    @Test
    void missingAuthRejectsWithoutSessionAndPermissionReads() throws Exception {
        stp.when(StpUtil::checkLogin).thenThrow(new IllegalStateException("private-login-token"));
        RuntimeException error =
                assertThrows(RuntimeException.class, () -> inspect(provider(), CHALLENGE));
        assertEquals("ATTESTATION_AUTH_REQUIRED", error.getMessage());
        noBorrow();
        login.verifyNoInteractions();
        stp.verify(() -> StpUtil.checkPermission("system:runtime:attest"), never());
    }

    @Test
    void seataUnknownItemAndHiddenHikariDelegateCannotBeCertified() throws Exception {
        HikariDataSource actual = (HikariDataSource) sources.get("erp");
        sources.put("erp", new ItemDataSource("erp", actual, actual, false, true, null));
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        verify(actual, never()).getConnection();
        sources.put("erp", actual);
        when(actual.getDataSource()).thenReturn(mock(DataSource.class));
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        verify(actual, never()).getConnection();
    }

    @Test
    void arbitraryPoolInitOrValidationSqlCannotRunBeforeReadonlyQualification() throws Exception {
        HikariDataSource pool = (HikariDataSource) sources.get("master");
        when(pool.getConnectionInitSql())
                .thenReturn("UPDATE private_table SET secret='private-pool-secret'");
        JsonNode r = report();
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN", r.path("diagnosticCategory").asText());
        assertFalse(r.toString().contains("private-pool-secret"));
        verify(pool, never()).getConnection();
        when(pool.getConnectionInitSql()).thenReturn(null);
        when(pool.getConnectionTestQuery()).thenReturn("SELECT private_mutating_function()");
        assertEquals(
                "ATTESTATION_DATASOURCE_BINDING_UNPROVEN",
                report().path("diagnosticCategory").asText());
        verify(pool, never()).getConnection();
    }
}
