package com.nexus.system.runtime;

import cn.dev33.satoken.stp.StpUtil;

import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.satoken.utils.LoginHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import javax.sql.DataSource;

/** 认证后绑定真实 premain 加载见证；独占链不能认证时保持 NONE，绝不授权业务写入。 */
@Component
public final class RuntimeAttestationProvider {
    public static final String PERMISSION = "system:runtime:attest";
    private static final Logger AUDIT = LoggerFactory.getLogger(RuntimeAttestationProvider.class);
    private final BoundDatasourceIdentityReader identities;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private final Integer pid;
    private final Instant startedAt;
    private final String instanceSha256;

    public RuntimeAttestationProvider(DataSource dataSource, Environment environment) {
        identities = new BoundDatasourceIdentityReader(dataSource);
        this.environment = environment;
        Integer actualPid = null;
        Instant actualStart = null;
        try {
            ProcessHandle process = ProcessHandle.current();
            actualPid = Math.toIntExact(process.pid());
            actualStart = process.info().startInstant().orElse(null);
        } catch (Exception ignored) { }
        pid = actualPid; startedAt = actualStart;
        instanceSha256 = sha256(String.valueOf(pid) + ':' + String.valueOf(startedAt) + ':' + randomDigest());
    }

    public RuntimeAttestationReport inspect(String challenge) {
        String challengeHash = challenge != null && challenge.matches("[0-9a-f]{64}") ? sha256(challenge) : "UNAVAILABLE";
        try { StpUtil.checkLogin(); }
        catch (Exception ignored) { throw reject(401, "ATTESTATION_AUTH_REQUIRED", null, null, challengeHash); }
        AuthenticatedIdentity identity = authenticatedIdentity(challengeHash);
        String principalHash = identity.principalSha256();
        String tenantHash = identity.tenantSha256();
        try { StpUtil.checkPermission(PERMISSION); }
        catch (Exception ignored) { throw reject(403, "ATTESTATION_PERMISSION_DENIED", principalHash, tenantHash, challengeHash); }
        if ("UNAVAILABLE".equals(challengeHash)) {
            throw reject(400, "ATTESTATION_CHALLENGE_INVALID", principalHash, tenantHash, challengeHash);
        }
        List<String> profiles;
        try { profiles = Arrays.asList(environment.getActiveProfiles()); }
        catch (Exception ignored) {
            throw reject(412, "ATTESTATION_PROFILE_UNSUPPORTED", principalHash, tenantHash, challengeHash);
        }
        if (!profiles.equals(List.of("dev"))) {
            throw reject(412, "ATTESTATION_PROFILE_UNSUPPORTED", principalHash, tenantHash, challengeHash);
        }
        BoundDatasourceIdentityReader.Observation datasource = identities.observe();
        LoadedArtifactAssessment.Observation build;
        try {
            build = LoadedArtifactAssessment.inspect(RuntimeAttestationProvider.class,
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                environment.getProperty("spring.devtools.restart.enabled", Boolean.class, false));
        } catch (Exception | LinkageError ignored) {
            build = new LoadedArtifactAssessment.Observation("UNPROVEN", "ATTESTATION_BUILD_UNPROVEN", "NONE", "UNKNOWN", "UNPROVEN");
        }
        String category = datasource.diagnosticCategory() != null ? datasource.diagnosticCategory()
            : pid == null || startedAt == null ? "ATTESTATION_STARTUP_UNPROVEN"
            : build.witness() != null && !"ABSENT".equals(build.witness().state()) ? build.diagnosticCategory() : "ATTESTATION_BUILD_UNPROVEN";
        RuntimeAttestationReport report = new RuntimeAttestationReport(2, "BLOCKED", category, false,
            OffsetDateTime.now(ZoneOffset.ofHours(8)).toString(), challengeHash, randomDigest(), principalHash, tenantHash,
            new RuntimeAttestationReport.RuntimeObservation(pid, startedAt == null ? null : startedAt.toString(), instanceSha256, profiles,
                build.witness() != null && "LOAD_IDENTITY_CONFIRMED".equals(build.witness().state())
                    ? build.witness().witnessSemantics() : "UNWITNESSED",
                build.originKind(), List.of("EXCLUSIVE_CHAIN_UNPROVEN")),
            datasource.status(), datasource.identities(), build);
        audit(category, principalHash, tenantHash, challengeHash);
        return report;
    }

    private record AuthenticatedIdentity(String principalSha256, String tenantSha256) { }

    private static AuthenticatedIdentity authenticatedIdentity(String challengeHash) {
        try {
            String principal = LoginHelper.getUserIdStr();
            String tenant = LoginHelper.getTenantId();
            LoginUser actor = LoginHelper.getLoginUser();
            if (principal != null && principal.matches("[1-9][0-9]{0,18}") && actor != null
                && actor.getUserId() != null && actor.getUserId() > 0
                && actor.getUserId().toString().equals(principal) && tenant != null
                && tenant.matches("[0-9]{6,20}") && tenant.equals(actor.getTenantId())) {
                return new AuthenticatedIdentity(sha256(principal), sha256(tenant));
            }
        } catch (Exception ignored) {
            // 会话或缓存错误可能包含令牌或连接详情；绝不包装原始异常原因。
        }
        throw reject(401, "ATTESTATION_AUTH_CONTEXT_UNPROVEN", null, null, challengeHash);
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("ATTESTATION_DIGEST_UNAVAILABLE");
        }
    }

    private String randomDigest() {
        byte[] nonce = new byte[32]; random.nextBytes(nonce);
        return sha256(HexFormat.of().formatHex(nonce));
    }

    private static Rejected reject(int code, String category, String principal, String tenant, String challenge) {
        audit(category, principal, tenant, challenge);
        return new Rejected(code, category);
    }

    private static void audit(String outcome, String principal, String tenant, String challenge) {
        // 刻意不使用 @Log：该切面会将操作日志写入数据库，违反本路径的只读约束。
        // 不包含异常消息或堆栈、URL、请求头、nonce、原始 ID 或权限列表。
        AUDIT.info("runtime_attestation permission={} outcome={} principalSha256={} tenantSha256={} challengeSha256={}",
            PERMISSION, outcome, principal == null ? "UNAVAILABLE" : principal,
            tenant == null ? "UNAVAILABLE" : tenant, challenge);
    }

    public static final class Rejected extends RuntimeException {
        private final int code;
        private Rejected(int code, String category) { super(category, null, false, false); this.code = code; }
        public int code() { return code; }
    }
}
