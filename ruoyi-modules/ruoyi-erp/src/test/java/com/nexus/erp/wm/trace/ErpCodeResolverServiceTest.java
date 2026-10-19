package com.nexus.erp.wm.trace;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErpCodeResolverServiceTest {
    JdbcTemplate jdbc;
    ErpCodeResolverService resolver;

    @BeforeEach
    void setup() {
        var fixture = new ErpSerialIdentityServiceTest();
        fixture.setup();
        jdbc = fixture.jdbc;
        jdbc.execute(
                "CREATE TABLE wm_sn_alias(alias_id BIGINT,tenant_id VARCHAR(20),identity_id"
                        + " BIGINT,source_type VARCHAR(32),source_id BIGINT,material_id"
                        + " BIGINT,alias_code VARCHAR(128),command_id VARCHAR(128),created_at"
                        + " TIMESTAMP)");
        // 共享身份夹具已创建批次表，此处复用同一来源。
        jdbc.execute(
                "CREATE TABLE wm_barcode(barcode_id BIGINT,tenant_id VARCHAR(20),barcode_code"
                        + " VARCHAR(128),material_id BIGINT,batch_id BIGINT,sn_id BIGINT,stock_id"
                        + " BIGINT,status VARCHAR(16),expire_time TIMESTAMP,del_flag CHAR(1))");
        jdbc.update(
                "INSERT INTO"
                    + " wm_sn(sn_id,tenant_id,sn_code,material_id,material_code,status,del_flag)"
                    + " VALUES(10,'T1','SN1',1,'M1','REGISTERED','0'),(11,'T1','SN2',1,'M1','REGISTERED','0')");
        resolver = new ErpCodeResolverService(jdbc, mock(ErpTraceContext.class));
    }

    CodeResolution resolve(String raw, Long supplier, Long material, Set<CodeObjectType> types) {
        return resolver.resolve(
                "T1",
                new CodeResolveRequest(raw, "QUERY", material, supplier, null, null, null, null),
                types);
    }

    @Test
    void duplicateExternalAliasNeedsContextAndNeverChoosesFirst() {
        jdbc.update(
                "INSERT INTO"
                    + " wm_sn_alias(alias_id,tenant_id,identity_id,source_type,source_id,material_id,alias_code)"
                    + " VALUES(1,'T1',10,'SUPPLIER',20,1,'EXT'),(2,'T1',11,'SUPPLIER',21,1,'EXT')");
        var result = resolve("EXT", null, null, Set.of(CodeObjectType.SN));
        assertEquals(ResolveStatus.AMBIGUOUS, result.status());
        assertNull(result.identityId());
        assertEquals(2, result.candidates().size());
        var scoped = resolve("EXT", 20L, null, Set.of(CodeObjectType.SN));
        assertEquals(ResolveStatus.RESOLVED, scoped.status());
        assertEquals(10L, scoped.identityId());
        assertEquals("SN1", scoped.canonicalSnCode());
    }

    @Test
    void noPrefixPriorityWhenSameStringNamesDifferentObjectTypes() {
        jdbc.update("UPDATE bd_material SET material_code='SN1'");
        assertEquals(
                ResolveStatus.AMBIGUOUS,
                resolve("SN1", null, null, Set.of(CodeObjectType.SN, CodeObjectType.MATERIAL))
                        .status());
    }

    @Test
    void unauthorizedTypesNeverLeakSerialCandidate() {
        var result = resolve("SN1", null, null, Set.of(CodeObjectType.MATERIAL));
        assertEquals(ResolveStatus.NOT_FOUND, result.status());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void barcodeFollowsStableIdentityAcrossStockChanges() {
        jdbc.update(
                "INSERT INTO wm_barcode"
                        + " VALUES(1,'T1','ANY-PREFIX',1,NULL,10,999,'ACTIVE',NULL,'0')");
        jdbc.update("UPDATE wm_sn SET stock_id=200 WHERE sn_id=10");
        var result = resolve("ANY-PREFIX", null, null, Set.of(CodeObjectType.SN));
        assertEquals(ResolveStatus.RESOLVED, result.status());
        assertEquals(10L, result.identityId());
    }

    @Test
    void contradictoryBarcodeContextIsRejected() {
        jdbc.update(
                "INSERT INTO wm_barcode VALUES(1,'T1','BROKEN',2,NULL,10,999,'ACTIVE',NULL,'0')");
        assertEquals(
                ResolveStatus.INVALID,
                resolve("BROKEN", null, null, Set.of(CodeObjectType.SN)).status());
    }

    @Test
    void compoundGs1UsesExternalSerialWithGtinMaterialContext() {
        jdbc.update(
                "INSERT INTO wm_barcode"
                        + " VALUES(1,'T1','09506000134352',1,NULL,NULL,NULL,'ACTIVE',NULL,'0')");
        jdbc.update(
                "INSERT INTO"
                    + " wm_sn_alias(alias_id,tenant_id,identity_id,source_type,source_id,material_id,alias_code)"
                    + " VALUES(1,'T1',10,'SUPPLIER',20,1,'EXT01')");
        assertEquals(
                10L,
                resolve("(01)09506000134352(21)EXT01", 20L, null, Set.of(CodeObjectType.SN))
                        .identityId());
        assertEquals(
                ResolveStatus.INVALID,
                resolve("(01)09506000134352(21)EXT01", 20L, 2L, Set.of(CodeObjectType.SN))
                        .status());
    }
}
