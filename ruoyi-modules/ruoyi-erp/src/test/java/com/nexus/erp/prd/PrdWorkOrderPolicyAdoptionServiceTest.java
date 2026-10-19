package com.nexus.erp.prd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 经审批的原档策略仅用于后续业务，当前主数据不能替代历史原档。
 */
class PrdWorkOrderPolicyAdoptionServiceTest {
    @Test
    void approvedCompleteOriginalEvidenceAdoptsFuturePoliciesWithoutReadingCurrentMaster()
            throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            f.db.jdbc.execute("DROP TABLE bd_material");
            String hash = f.apply();
            var result = f.db.service.find(Fixture.TENANT, 1L, 20L);
            assertTrue(result.known());
            assertFalse(result.originalRelease());
            assertEquals(
                    WorkOrderTrackingPolicySnapshot.Provenance.SOURCE_POLICY_ADOPTION,
                    result.provenance());
            assertEquals(
                    new TrackingPolicy(true, true, false, 5L, TrackingStage.STRICT),
                    result.policy());
            assertEquals(hash, result.snapshotHash());
            assertEquals(22L, result.actorId());
            assertFalse(result.frozenAt().isBefore(f.approved));
            assertEquals(result.frozenAt(), result.effectiveFrom());
            assertEquals("APPLIED", f.status());
            assertEquals(2L, f.version());
            assertEquals(3, f.db.count());
        }
    }

    @Test
    void sameAppliedCommandReplaysAfterWorkProgressAndPolicyChangesWithoutRewritingEvidence()
            throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            String hash = f.apply();
            var before =
                    f.db.jdbc
                            .queryForList(
                                    "SELECT * FROM wm_work_order_tracking_policy ORDER BY"
                                            + " material_id")
                            .toString();
            f.db.jdbc.update("UPDATE prd_work_order SET wo_status='COMPLETED'");
            f.db.jdbc.execute("DROP TABLE bd_material");
            assertEquals(hash, f.apply());
            assertEquals(2L, f.version());
            assertEquals(
                    before,
                    f.db.jdbc
                            .queryForList(
                                    "SELECT * FROM wm_work_order_tracking_policy ORDER BY"
                                            + " material_id")
                            .toString());
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.db.tx.execute(
                                    s -> f.service.adoptApproved(700L, "another-command", 1L)));
            assertThrows(
                    RuntimeException.class,
                    () -> f.db.tx.execute(s -> f.service.adoptApproved(700L, "adopt-700", 2L)));
        }
    }

    @Test
    void adoptionCannotOverwriteAnAuthenticReleaseSnapshot() throws Exception {
        try (var f = new Fixture()) {
            f.db.jdbc.update("UPDATE prd_work_order SET wo_status='CREATED'");
            f.db.freeze();
            f.db.jdbc.update("UPDATE prd_work_order SET wo_status='RELEASED'");
            f.authorize();
            var before = f.db.service.find(Fixture.TENANT, 1L, 20L);
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(before, f.db.service.find(Fixture.TENANT, 1L, 20L));
            assertEquals("APPROVED", f.status());
        }
    }

    @Test
    void aChangedWorkOrderRequirementInvalidatesTheApprovedSourceCas() throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            f.db.jdbc.update(
                    "UPDATE prd_material_requirement_detail SET required_quantity=99 WHERE"
                            + " detail_id=201");
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(0, f.db.count());
            assertEquals(1L, f.version());
        }
    }

    @Test
    void approvalAndBankRollBackTogetherIfTheEnclosingErpOperationFails() throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            f.db.tx.execute(
                                    s -> {
                                        f.service.adoptApproved(700L, "adopt-700", 1L);
                                        throw new IllegalStateException("abort");
                                    }));
            assertEquals(0, f.db.count());
            assertEquals("APPROVED", f.status());
            assertEquals(1L, f.version());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "source_hash=REPEAT('b',64)",
                "approval_hash=REPEAT('b',64)",
                "request_hash=REPEAT('b',64)",
                "version=9",
                "approved_by=11",
                "status='REJECTED'",
                "expires_at=DATEADD('SECOND',-120,CURRENT_TIMESTAMP)"
            })
    void alteredUnapprovedExpiredOrStaleAuthorizationNeverCreatesAnyBankRow(String change)
            throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            f.db.jdbc.update("UPDATE wm_work_order_policy_adoption SET " + change);
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(0, f.db.count());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingPolicy",
                "missingMaterial",
                "extraMaterial",
                "wrongTenant",
                "currentMaster",
                "futureEvidence",
                "badDecision",
                "missingArchiveHash"
            })
    void evenAnApprovedEnvelopeCannotReplaceMissingOrWrongOriginalPolicyEvidence(String change)
            throws Exception {
        try (var f = new Fixture()) {
            switch (change) {
                case "missingPolicy" ->
                        ((ObjectNode) f.source.path("materials").get(1).path("policy"))
                                .remove("allowMixedLot");
                case "missingMaterial" -> ((ArrayNode) f.source.path("materials")).remove(1);
                case "extraMaterial" -> {
                    var extra = f.source.path("materials").get(1).deepCopy();
                    ((ObjectNode) extra).put("materialId", "99");
                    ((ArrayNode) f.source.path("materials")).add(extra);
                }
                case "wrongTenant" -> f.source.put("tenantId", "000002");
                case "currentMaster" ->
                        ((ObjectNode) f.source.path("materials").get(1))
                                .put("origin", "CURRENT_MASTER");
                case "futureEvidence" ->
                        ((ObjectNode) f.source.path("materials").get(1))
                                .put("capturedAt", f.approved.plusSeconds(1).toString());
                case "badDecision" -> f.decision = "REJECTED";
                case "missingArchiveHash" ->
                        ((ObjectNode) f.source.path("materials").get(1)).remove("documentHash");
            }
            f.authorize();
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(0, f.db.count());
        }
    }

    @Test
    void noApprovalOrNoPhysicalErpTransactionCannotAdoptAPolicy() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(0, f.db.count());
            f.authorize();
            assertThrows(
                    RuntimeException.class, () -> f.service.adoptApproved(700L, "adopt-700", 1L));
            assertEquals(0, f.db.count());
        }
    }

    @Test
    void modifyingTheAppliedAuthorizationCannotRebindTheImmutableBank() throws Exception {
        try (var f = new Fixture()) {
            f.authorize();
            f.apply();
            f.db.jdbc.update(
                    "UPDATE wm_work_order_policy_adoption SET request_hash=REPEAT('b',64)");
            assertThrows(RuntimeException.class, f::apply);
            assertEquals(3, f.db.count());
        }
    }

    static final class Fixture implements AutoCloseable {
        static final String TENANT = "000001";
        final PrdWorkOrderTrackingPolicyServiceTest.Fixture db =
                new PrdWorkOrderTrackingPolicyServiceTest.Fixture();
        final PrdWorkOrderPolicyAdoptionAuthorizer authorizer =
                mock(PrdWorkOrderPolicyAdoptionAuthorizer.class);
        final ObjectMapper json = new ObjectMapper();
        final ObjectNode source = json.createObjectNode();
        final Instant
                approved =
                        Instant.now()
                                .minusSeconds(20)
                                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                expires = approved.plusSeconds(3600);
        final PrdWorkOrderPolicyAdoptionService service;
        String decision = "APPROVED";

        Fixture() throws Exception {
            try {
                db.jdbc.update("UPDATE prd_work_order SET wo_status='IN_PROGRESS'");
                db.jdbc.execute(
                        "CREATE TABLE wm_work_order_policy_adoption(adoption_id BIGINT PRIMARY"
                            + " KEY,tenant_id VARCHAR(20),command_id VARCHAR(128),erp_wo_id"
                            + " BIGINT,expected_wo_hash VARCHAR(64),source_json CLOB,source_hash"
                            + " VARCHAR(64),approval_json CLOB,approval_hash"
                            + " VARCHAR(64),request_hash VARCHAR(64),proposed_by BIGINT,approved_by"
                            + " BIGINT,approved_at TIMESTAMP(3),expires_at TIMESTAMP(3),status"
                            + " VARCHAR(16),version BIGINT,applied_at TIMESTAMP(3),snapshot_hash"
                            + " VARCHAR(64),UNIQUE(tenant_id,command_id))");
                var context =
                        (com.nexus.erp.wm.trace.ErpTraceContext)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        db.target, "context");
                when(authorizer.requireApprover()).thenReturn(22L);
                var target =
                        new PrdWorkOrderPolicyAdoptionService(
                                db.jdbc, context, json, db.service, authorizer);
                var proxy = new ProxyFactory(target);
                proxy.setProxyTargetClass(true);
                proxy.addAdvice(
                        new TransactionInterceptor(
                                db.tx.getTransactionManager(),
                                new AnnotationTransactionAttributeSource()));
                service = (PrdWorkOrderPolicyAdoptionService) proxy.getProxy();
                source.put("schemaVersion", 1).put("tenantId", TENANT).put("erpWoId", "1");
                var rows = source.putArray("materials");
                for (long id : List.of(10L, 20L, 30L)) {
                    var item = rows.addObject();
                    item.put("materialId", Long.toString(id))
                            .put("reference", "original-archive/" + id)
                            .put("origin", "IMMUTABLE_SOURCE_SNAPSHOT")
                            .put("capturedAt", approved.minusSeconds(86400).toString())
                            .put("documentHash", "a".repeat(64));
                    item.putObject("policy")
                            .put("batchManaged", true)
                            .put("serialManaged", id != 30)
                            .put("allowMixedLot", false)
                            .put("version", id == 20 ? "5" : "7")
                            .put("stage", "STRICT");
                }
            } catch (Exception | Error failure) {
                db.close();
                throw failure;
            }
        }

        void authorize() throws Exception {
            var preview = db.tx.execute(s -> db.service.previewAdoption(1L));
            String sourceJson = json.writeValueAsString(source),
                    sourceHash = TraceHashes.sha256(sourceJson);
            String request =
                    TraceHashes.fingerprint(
                            "WO_POLICY_ADOPTION_V1",
                            TENANT,
                            700L,
                            "adopt-700",
                            1L,
                            preview.sourceHash(),
                            sourceHash,
                            11L,
                            22L,
                            approved.toEpochMilli(),
                            expires.toEpochMilli());
            String approval =
                    json.writeValueAsString(
                            Map.of(
                                    "decision",
                                    decision,
                                    "requestHash",
                                    request,
                                    "approvedBy",
                                    "22",
                                    "approvedAt",
                                    approved.toString(),
                                    "reference",
                                    "approval/700"));
            db.jdbc.update(
                    "INSERT INTO wm_work_order_policy_adoption"
                        + " VALUES(700,?,'adopt-700',1,?,?,?,?,?,?,11,22,?,?,'APPROVED',1,NULL,NULL)",
                    TENANT,
                    preview.sourceHash(),
                    sourceJson,
                    sourceHash,
                    approval,
                    TraceHashes.sha256(approval),
                    request,
                    Timestamp.from(approved),
                    Timestamp.from(expires));
        }

        String apply() {
            return db.tx.execute(s -> service.adoptApproved(700L, "adopt-700", 1L));
        }

        String status() {
            return db.jdbc.queryForObject(
                    "SELECT status FROM wm_work_order_policy_adoption", String.class);
        }

        long version() {
            return db.jdbc.queryForObject(
                    "SELECT version FROM wm_work_order_policy_adoption", Long.class);
        }

        public void close() {
            db.close();
        }
    }
}
