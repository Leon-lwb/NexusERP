package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;

class TraceRepairAuditServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    TraceRepairAuditService service;
    ErpTraceContext context;

    @BeforeEach
    void setup() {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:audit" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(
                "CREATE TABLE bd_material(material_id BIGINT,tenant_id VARCHAR(20),is_serial_manage"
                        + " INT,is_batch_manage INT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id VARCHAR(20),material_id"
                        + " BIGINT,warehouse_id BIGINT,batch_id BIGINT,batch_code"
                        + " VARCHAR(64),valuation_group_id BIGINT,sn_id BIGINT,sn_code"
                        + " VARCHAR(128),stock_quantity DECIMAL(18,4),reserved_quantity"
                        + " DECIMAL(18,4),total_cost DECIMAL(18,4),del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),material_id BIGINT,sn_code"
                        + " VARCHAR(128),stock_id BIGINT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_batch(batch_id BIGINT,tenant_id VARCHAR(20),material_id"
                        + " BIGINT,batch_code VARCHAR(64),del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_trace_audit_run(audit_id VARCHAR(64) PRIMARY KEY,tenant_id"
                    + " VARCHAR(20),material_id BIGINT,status VARCHAR(24),summary"
                    + " VARCHAR(1000),report_json CLOB,content_hash CHAR(64),created_at TIMESTAMP"
                    + " DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO bd_material VALUES(1,'T1',1,0,'0')");
        jdbc.update(
                "INSERT INTO wm_material_stock"
                        + " VALUES(8,'T1',1,2,NULL,NULL,NULL,NULL,NULL,2,0,10,'0')");
        context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("T1");
        service = new TraceRepairAuditService(jdbc, new ObjectMapper(), context);
    }

    @Test
    void missingHistoricalSerialsAreReportedWithoutInventingIdentitiesOrChangingStock() {
        var result =
                tx.execute(s -> service.audit(new TraceRepairAuditService.Request(1L, null, null)));
        assertEquals("REVIEW_REQUIRED", result.get("status"));
        assertEquals("ERP", result.get("scope"));
        assertTrue(result.get("items").toString().contains("MISSING_SERIAL"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
        assertEquals(
                0,
                new BigDecimal("2")
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT stock_quantity FROM wm_material_stock",
                                        BigDecimal.class)));
        var detail = tx.execute(s -> service.detail((String) result.get("runId")));
        assertEquals(result.get("runId"), detail.get("runId"));
    }

    @Test
    void reportCannotBeReadFromAnotherTenantOrAfterContentWasChanged() {
        var result = tx.execute(s -> service.audit(null));
        String id = (String) result.get("runId");
        when(context.tenant()).thenReturn("T2");
        assertThrows(RuntimeException.class, () -> service.detail(id));
        when(context.tenant()).thenReturn("T1");
        jdbc.update("UPDATE wm_trace_audit_run SET report_json='{}' WHERE audit_id=?", id);
        assertThrows(RuntimeException.class, () -> service.detail(id));
    }

    @Test
    void failedArtifactSaveRollsBackOnlyTheAuditRequest() {
        assertThrows(
                RuntimeException.class,
                () ->
                        tx.executeWithoutResult(
                                s -> {
                                    service.audit(null);
                                    throw new IllegalStateException("request failed");
                                }));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_trace_audit_run", Integer.class));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock", Integer.class));
    }
}
