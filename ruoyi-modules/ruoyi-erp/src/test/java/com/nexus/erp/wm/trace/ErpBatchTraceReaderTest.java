package com.nexus.erp.wm.trace;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErpBatchTraceReaderTest {
    JdbcTemplate jdbc;
    ErpIdentityTraceReader reader;

    @BeforeEach
    void setup() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:batchtrace"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        reader = new ErpIdentityTraceReader(jdbc, mock(ErpTraceContext.class));
        jdbc.execute(
                "CREATE TABLE wm_batch(batch_id BIGINT,tenant_id VARCHAR(20),batch_code"
                    + " VARCHAR(128),material_id BIGINT,material_code VARCHAR(128),vendor_id"
                    + " BIGINT,vendor_code VARCHAR(128),vendor_name VARCHAR(128),vendor_batch_code"
                    + " VARCHAR(128),source_doc_type VARCHAR(32),source_doc_id BIGINT,source_doc_no"
                    + " VARCHAR(128),warehouse_id BIGINT,production_date TIMESTAMP,expiry_date"
                    + " TIMESTAMP,quantity DECIMAL(18,4),received_qty DECIMAL(18,4),issued_qty"
                    + " DECIMAL(18,4),status VARCHAR(32),del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id VARCHAR(20),material_id"
                        + " BIGINT,batch_id BIGINT,batch_code VARCHAR(128),sn_id BIGINT,sn_code"
                        + " VARCHAR(128),warehouse_id BIGINT,location_id BIGINT,stock_quantity"
                        + " DECIMAL(18,4),reserved_quantity DECIMAL(18,4),unit_cost"
                        + " DECIMAL(18,4),total_cost DECIMAL(18,4),tracking_version"
                        + " BIGINT,valuation_group_id BIGINT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_material_stock_tx(tx_id BIGINT,tenant_id VARCHAR(20),tx_type"
                    + " VARCHAR(32),source_doc_type VARCHAR(32),source_doc_id"
                    + " BIGINT,source_doc_line_id BIGINT,source_doc_code VARCHAR(128),sn_id"
                    + " BIGINT,sn_code VARCHAR(128),material_stock_id BIGINT,tx_flag"
                    + " INT,tx_quantity DECIMAL(18,4),unit_cost DECIMAL(18,4),tx_amount"
                    + " DECIMAL(18,4),related_tx_id BIGINT,warehouse_id BIGINT,location_id"
                    + " BIGINT,batch_id BIGINT,batch_code VARCHAR(128),tx_date"
                    + " TIMESTAMP,posting_group_id BIGINT,tracking_version BIGINT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_sales_issue(issue_id BIGINT,tenant_id VARCHAR(20),client_id"
                    + " BIGINT,client_code VARCHAR(128),client_name VARCHAR(128),so_id BIGINT,so_no"
                    + " VARCHAR(128),del_flag CHAR)");
        jdbc.update(
                "INSERT INTO"
                    + " wm_batch(batch_id,tenant_id,batch_code,material_id,material_code,warehouse_id,quantity,received_qty,issued_qty,status,del_flag)"
                    + " VALUES(20,'000001','B20',1,'M1',10,30,30,10,'NORMAL','0'),(21,'000001','B21',1,'M1',10,1,1,0,'NORMAL','0')");
        jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock(stock_id,tenant_id,material_id,batch_id,batch_code,warehouse_id,location_id,stock_quantity,reserved_quantity,unit_cost,total_cost,tracking_version,del_flag)"
                    + " VALUES(101,'000001',1,20,'B20',11,51,20,0,3,60,1,'0'),(102,'000001',1,20,'B20',12,52,5,0,3,15,1,'0'),(103,'000002',1,20,'B20',13,53,100,0,3,300,1,'0')");
    }

    @Test
    void aBatchOnlyStockKeepsCurrentPositionsSeparateFromItsSourceWarehouse() {
        var part = reader.batch("000001", 20L, null, null, 500);
        assertEquals("BATCH", part.scope());
        assertEquals("10", part.sections().get("SOURCE").rows().get(0).get("source_warehouse_id"));
        assertEquals(
                List.of("11", "12"),
                part.sections().get("STOCK").rows().stream()
                        .map(row -> row.get("warehouse_id"))
                        .toList());
        assertEquals("20.0000", part.sections().get("STOCK").rows().get(0).get("stock_quantity"));
    }

    @Test
    void customerDestinationsRemainVisibleEvenWhenNoIndividualSerialWasRecorded() {
        jdbc.update(
                "INSERT INTO wm_sales_issue VALUES(77,'000001',88,'C88','Original"
                        + " customer',90,'SO90','0')");
        jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,batch_id,tx_flag,tx_quantity,del_flag)"
                    + " VALUES(201,'000001','WM_SALES_ISSUE',77,20,-1,5,'0')");
        var row =
                reader.batch("000001", 20L, "MOVEMENTS", null, 500)
                        .sections()
                        .get("MOVEMENTS")
                        .rows()
                        .get(0);
        assertEquals("Original customer", row.get("customer_name"));
        assertEquals("OUT", row.get("direction"));
        assertNull(row.get("sn_id"));
    }

    @Test
    void batchCursorsCannotBeUsedForADifferentBatchOrSection() {
        var page = reader.batch("000001", 20L, "STOCK", null, 1).sections().get("STOCK");
        assertTrue(page.hasMore());
        assertThrows(
                RuntimeException.class,
                () -> reader.batch("000001", 21L, "STOCK", page.nextCursor(), 1));
        assertThrows(
                RuntimeException.class,
                () -> reader.batch("000001", 20L, "MOVEMENTS", page.nextCursor(), 1));
        var last =
                reader.batch("000001", 20L, "STOCK", page.nextCursor(), 1).sections().get("STOCK");
        assertEquals("102", last.rows().get(0).get("stock_id"));
        assertFalse(last.hasMore());
    }

    @Test
    void anotherTenantsBatchCannotBeRead() {
        assertThrows(RuntimeException.class, () -> reader.batch("000002", 20L, null, null, 500));
    }
}
