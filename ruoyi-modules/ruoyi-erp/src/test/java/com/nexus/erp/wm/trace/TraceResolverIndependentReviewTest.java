package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 将独立审查确认的边界场景保留在常规回归套件中。
 */
class TraceResolverIndependentReviewTest {
    @ParameterizedTest
    @ValueSource(strings = {"SN", "BATCH"})
    void registeredCarrierWithMissingMaterialMustStillHonorTheRequestedMaterial(String kind) {
        var f = new Fixture(kind);
        var result =
                f.resolver.resolve(
                        "000001", f.request("CARRIER", 1L), Set.of(CodeObjectType.valueOf(kind)));
        assertEquals(
                ResolveStatus.INVALID,
                result.status(),
                "carrier refers to material2 but current source row requires material1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SN", "BATCH"})
    void carrierWhoseResolvedMaterialMatchesTheRequestRemainsUsable(String kind) {
        var f = new Fixture(kind);
        assertEquals(
                ResolveStatus.RESOLVED,
                f.resolver
                        .resolve(
                                "000001",
                                f.request("CARRIER", 2L),
                                Set.of(CodeObjectType.valueOf(kind)))
                        .status());
    }

    @Test
    void allProvidedCodeAssertionsMustAgreeEvenWhenRawCodeIsNotThePrimaryField() {
        var resolver = new StructuredLabelResolver(new ObjectMapper());
        var first =
                new CodeCandidate(CodeObjectType.SN, 11L, null, "SN-A", 1L, "M1", null, null, null);
        var second =
                new CodeCandidate(CodeObjectType.SN, 12L, null, "SN-B", 1L, "M1", null, null, null);
        var request =
                new CodeResolveRequest(
                        "{\"snCode\":\"SN-A\",\"rawCode\":\"SN-B\"}",
                        "RECEIVE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var result =
                resolver.resolve(
                        request,
                        Set.of(CodeObjectType.SN),
                        (r, t) ->
                                CodeResolution.resolved(
                                        r.rawCode().equals("SN-A") ? first : second));
        assertEquals(
                ResolveStatus.INVALID,
                result.status(),
                "the structured object asserts two different physical identities");
    }

    @Test
    void explicitSnCodeMustNotSilentlyBecomeABatchWhenNoSuchSnExists() {
        var f = new Fixture("BATCH");
        var structured = new StructuredLabelResolver(new ObjectMapper());
        var request = f.request("{\"snCode\":\"B2\",\"materialCode\":\"M2\"}", null);
        var result =
                structured.resolve(
                        request,
                        Set.of(CodeObjectType.SN, CodeObjectType.BATCH, CodeObjectType.MATERIAL),
                        (r, t) -> f.resolver.resolve("000001", r, t));
        assertEquals(
                ResolveStatus.INVALID,
                result.status(),
                "an explicit missing SN assertion must not resolve a batch instead");
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final ErpCodeResolverService resolver;

        Fixture(String kind) {
            jdbc =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    "jdbc:h2:mem:resolverReview"
                                            + UUID.randomUUID()
                                            + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                    "sa",
                                    ""));
            jdbc.execute(
                    "CREATE TABLE bd_material(material_id BIGINT,tenant_id"
                            + " VARCHAR(20),material_code VARCHAR(64),is_batch_manage"
                            + " INT,is_serial_manage INT,allow_mixed_lot INT,tracking_version"
                            + " BIGINT,tracking_stage VARCHAR(16),del_flag CHAR)");
            jdbc.update(
                    "INSERT INTO bd_material"
                        + " VALUES(1,'000001','M1',1,1,1,1,'STRICT','0'),(2,'000001','M2',1,1,1,1,'STRICT','0')");
            jdbc.execute(
                    "CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),sn_code"
                            + " VARCHAR(128),material_id BIGINT,material_code VARCHAR(64),batch_id"
                            + " BIGINT,batch_code VARCHAR(128),del_flag CHAR)");
            jdbc.update("INSERT INTO wm_sn VALUES(20,'000001','SN2',2,'M2',5,'B2','0')");
            jdbc.execute(
                    "CREATE TABLE wm_batch(batch_id BIGINT,tenant_id VARCHAR(20),batch_code"
                            + " VARCHAR(128),material_id BIGINT,material_code VARCHAR(64),del_flag"
                            + " CHAR)");
            jdbc.update("INSERT INTO wm_batch VALUES(5,'000001','B2',2,'M2','0')");
            jdbc.execute(
                    "CREATE TABLE wm_barcode(tenant_id VARCHAR(20),barcode_code VARCHAR(128),sn_id"
                            + " BIGINT,batch_id BIGINT,material_id BIGINT,status"
                            + " VARCHAR(16),expire_time TIMESTAMP,del_flag CHAR)");
            jdbc.update(
                    "INSERT INTO wm_barcode VALUES('000001','CARRIER',?,5,NULL,'ACTIVE',NULL,'0')",
                    "SN".equals(kind) ? 20L : null);
            resolver = new ErpCodeResolverService(jdbc, mock(ErpTraceContext.class));
        }

        CodeResolveRequest request(String raw, Long material) {
            return new CodeResolveRequest(raw, "RECEIVE", material, null, null, null, null, null);
        }
    }
}
