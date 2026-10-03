package com.nexus.system.runtime;

import java.util.List;
import java.util.Map;

/** 安全的观测结果，不是已加载构建证书或业务写入授权。 */
public record RuntimeAttestationReport(
    int schemaVersion, String status, String diagnosticCategory, boolean businessWriteAuthorized,
    String observedAt, String challengeSha256, String serverNonceSha256,
    String principalSha256, String tenantSha256, RuntimeObservation runtime,
    String datasourceObservationStatus, Map<String, DatasourceIdentity> datasources,
    LoadedArtifactAssessment.Observation build) {

    public RuntimeAttestationReport {
        if (schemaVersion != 2 || !"BLOCKED".equals(status) || businessWriteAuthorized || build == null || !"NONE".equals(build.supportedProofDomain())) {
            throw new IllegalArgumentException("ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN");
        }
        datasources = Map.copyOf(datasources);
    }

    public record RuntimeObservation(Integer pid, String startedAt, String instanceSha256,
                                     List<String> activeProfiles, String measurementMode,
                                     String classLoading, List<String> runtimeOverrides) {
        public RuntimeObservation {
            activeProfiles = List.copyOf(activeProfiles);
            runtimeOverrides = List.copyOf(runtimeOverrides);
        }
        public RuntimeObservation(Integer pid, String startedAt, String instanceSha256, List<String> activeProfiles) {
            this(pid, startedAt, instanceSha256, activeProfiles, "UNWITNESSED", "UNPROVEN", List.of("EXCLUSIVE_CHAIN_UNPROVEN"));
        }
    }

    public record DatasourceIdentity(int port, String schema, String serverUuidSha256,
                                     String identitySource, String observedAt) { }
}
