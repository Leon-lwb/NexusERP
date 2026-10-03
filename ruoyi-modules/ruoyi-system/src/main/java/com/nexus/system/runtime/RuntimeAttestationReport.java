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
        datasources = Map.copyOf(datasources);
    }

    public record RuntimeObservation(Integer pid, String startedAt, String instanceSha256,
                                     List<String> activeProfiles) {
        public RuntimeObservation { activeProfiles = List.copyOf(activeProfiles); }
    }

    public record DatasourceIdentity(int port, String schema, String serverUuidSha256,
                                     String identitySource, String observedAt) { }
}
