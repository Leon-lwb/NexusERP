package com.nexus.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.erp.wm.trace.ErpIdentityTraceReader;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.nexus.mes.integration.trace.MesIdentityTraceReader;
import com.nexus.mes.trace.MesTraceContext;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.*;

/**
 * 使用真实库存与制造读取器及数量来源查询，复现独立审查确认的边界场景。
 */
class BatchRecallBoundaryIndependentReviewTest {
    @Test
    void batchOnlyOutputReachedThroughRealWorkOrderUseMustRemainInRecallFacts() {
        try (var f = new Fixture()) {
            var result = f.service.recall(20L, 0, 500, 10);
            assertFalse((Boolean) result.get("truncated"));
            var impacts = (TracePage<?>) result.get("scopeImpacts");
            assertEquals(1, impacts.rows().size());
            assertTrue(
                    containsBatch(new ObjectMapper().valueToTree(result), 21L),
                    "raw batch20 has actual WO7 use, WO7 actually outputs ERP batch21, but no"
                            + " returned batch fact identifies batch21 or its stock/sales");
        }
    }

    @Test
    void theMissingOutputHasActualCurrentStockAndCustomerFactsWhenReadDirectly() {
        try (var f = new Fixture()) {
            var output = f.erp.reader.batch("000001", 21L, null, null, 500);
            assertEquals(
                    "3.0000", output.sections().get("STOCK").rows().get(0).get("stock_quantity"));
            assertTrue(
                    output.sections().get("MOVEMENTS").rows().stream()
                            .anyMatch(row -> "Output customer".equals(row.get("customer_name"))));
            assertEquals(
                    List.of(21L),
                    f.mesReader.productBatchesByWorkOrder("000001", 7L, 0, 500).rows());
            var input = f.mesReader.unallocatedByBatch("000001", 20L, 0, 500).rows().get(0);
            assertEquals(7L, input.mesWoId());
            assertEquals(0, new BigDecimal("5").compareTo(input.unallocatedQuantity()));
        }
    }

    @Test
    void aBatchOnlyBridgeKeepsAFurtherSerialOutputAtPossibleWorkOrderScope() {
        try (var f = new Fixture()) {
            // 工单8消耗批次21的一笔独立实际领料，来源证明不复用工单7的产品授权。
            f.erp.jdbc.update(
                    "UPDATE wm_material_stock SET stock_quantity=1,total_cost=3 WHERE"
                            + " stock_id=201");
            f.erp.jdbc.update("UPDATE wm_batch SET issued_qty=4 WHERE batch_id=21");
            f.erp.jdbc.update(
                    "INSERT INTO"
                        + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,source_doc_line_id,tx_type,material_stock_id,batch_id,tx_flag,tx_quantity,unit_cost,tx_amount,tracking_version,del_flag)"
                        + " VALUES(302,'000001','WM_PRODUCTION_ISSUE',80,81,'PRODUCTION_ISSUE',201,21,-1,2,3,6,1,'0')");
            f.mes.update(
                    "INSERT INTO lt_lot"
                            + " VALUES(13,'000001','ISSUED21',2,2,'CONSUMED',NULL,NULL,'0')");
            f.mes.update(
                    "INSERT INTO lt_lot_allocation"
                            + " VALUES(301,'000001',13,2,21,2,'PRODUCTION_ISSUE',80,81,1)");
            f.mes.update("INSERT INTO lt_wo_consumption VALUES(102,'000001',8,2,13,301,2,'0')");
            f.mes.update(
                    "INSERT INTO lt_sn_consumption"
                            + " VALUES(1002,'000001',1001,2,13,2,102,'CONSUME')");
            var result = f.service.recall(20L, 0, 500, 2);
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) result.get("rows");
            assertEquals(
                    List.of(Map.of("identityId", "1001", "scope", "WORK_ORDER_POSSIBLE")), rows);
            assertEquals(true, result.get("truncatedByDepth"));
        }
    }

    @Test
    void mergedRootBatchFactsRespectTheCostPermissionBoundary() {
        try (var f = new Fixture()) {
            var json = new ObjectMapper().valueToTree(f.service.recall(20L, 0, 500, 10));
            assertTrue(containsBatch(json, 20L));
            assertTrue(json.findValues("unit_cost").isEmpty());
            assertTrue(json.findValues("total_cost").isEmpty());
            assertFalse(json.toString().contains("warehouse_id\":\"13"));
        }
    }

    static boolean containsBatch(JsonNode node, long batch) {
        if (node.isObject()) {
            for (String field : List.of("batchId", "batch_id", "erp_batch_id")) {
                if (node.hasNonNull(field)
                        && Long.toString(batch).equals(node.get(field).asText())) {
                    return true;
                }
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsBatch(child, batch)) {
                    return true;
                }
            }
        }
        return false;
    }

    static class ErpFixture {
        JdbcTemplate jdbc;
        ErpIdentityTraceReader reader;

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
                        + " BIGINT,vendor_code VARCHAR(128),vendor_name"
                        + " VARCHAR(128),vendor_batch_code VARCHAR(128),source_doc_type"
                        + " VARCHAR(32),source_doc_id BIGINT,source_doc_no"
                        + " VARCHAR(128),warehouse_id BIGINT,production_date TIMESTAMP,expiry_date"
                        + " TIMESTAMP,quantity DECIMAL(18,4),received_qty DECIMAL(18,4),issued_qty"
                        + " DECIMAL(18,4),status VARCHAR(32),del_flag CHAR)");
            jdbc.execute(
                    "CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id"
                            + " VARCHAR(20),material_id BIGINT,batch_id BIGINT,batch_code"
                            + " VARCHAR(128),sn_id BIGINT,sn_code VARCHAR(128),warehouse_id"
                            + " BIGINT,location_id BIGINT,stock_quantity"
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
                            + " TIMESTAMP,posting_group_id BIGINT,tracking_version BIGINT,del_flag"
                            + " CHAR)");
            jdbc.execute(
                    "CREATE TABLE wm_sales_issue(issue_id BIGINT,tenant_id VARCHAR(20),client_id"
                            + " BIGINT,client_code VARCHAR(128),client_name VARCHAR(128),so_id"
                            + " BIGINT,so_no VARCHAR(128),del_flag CHAR)");
            jdbc.update(
                    "INSERT INTO"
                        + " wm_batch(batch_id,tenant_id,batch_code,material_id,material_code,warehouse_id,quantity,received_qty,issued_qty,status,del_flag)"
                        + " VALUES(20,'000001','B20',1,'M1',10,30,30,10,'NORMAL','0'),(21,'000001','B21',1,'M1',10,1,1,0,'NORMAL','0')");
            jdbc.update(
                    "INSERT INTO"
                        + " wm_material_stock(stock_id,tenant_id,material_id,batch_id,batch_code,warehouse_id,location_id,stock_quantity,reserved_quantity,unit_cost,total_cost,tracking_version,del_flag)"
                        + " VALUES(101,'000001',1,20,'B20',11,51,20,0,3,60,1,'0'),(102,'000001',1,20,'B20',12,52,5,0,3,15,1,'0'),(103,'000002',1,20,'B20',13,53,100,0,3,300,1,'0')");
        }
    }

    static class Fixture implements AutoCloseable {
        final ErpFixture erp = new ErpFixture();
        final JdbcTemplate mes;
        final MesIdentityTraceReader mesReader;
        final MaterialIdentityTraceService service;
        final MockedStatic<TenantHelper> tenant;
        final MockedStatic<StpUtil> auth;

        Fixture() {
            erp.setup();
            erp.jdbc.execute(
                    "CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),batch_id BIGINT,del_flag"
                            + " CHAR)");
            // 验证数量型业务链：实际领料、记录工单消耗、生成产品批次，
            // 再销售两件并在库存保留三件；全过程按已有批次数量证据追溯。
            erp.jdbc.update(
                    "UPDATE wm_batch SET"
                        + " quantity=30,received_qty=30,issued_qty=10,source_doc_type='WM_MATERIAL_RECEIVE',source_doc_id=60,source_doc_no='RECEIVE60'"
                        + " WHERE batch_id=20");
            erp.jdbc.update(
                    "UPDATE wm_material_stock SET stock_quantity=0,total_cost=0 WHERE"
                            + " stock_id=102");
            erp.jdbc.update(
                    "UPDATE wm_batch SET"
                        + " material_id=2,material_code='M2',quantity=5,received_qty=5,issued_qty=2,source_doc_type='PRD_WORK_ORDER',source_doc_id=7,source_doc_no='WO7'"
                        + " WHERE batch_id=21");
            erp.jdbc.update(
                    "INSERT INTO"
                        + " wm_material_stock(stock_id,tenant_id,material_id,batch_id,batch_code,warehouse_id,location_id,stock_quantity,reserved_quantity,unit_cost,total_cost,tracking_version,del_flag)"
                        + " VALUES(201,'000001',2,21,'B21',22,61,3,0,3,9,1,'0')");
            erp.jdbc.update(
                    "INSERT INTO"
                        + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,source_doc_line_id,tx_type,material_stock_id,batch_id,tx_flag,tx_quantity,unit_cost,tx_amount,tracking_version,del_flag)"
                        + " VALUES(100,'000001','WM_MATERIAL_RECEIVE',60,61,'RECEIVE_IN',101,20,1,30,3,90,1,'0'),(101,'000001','WM_PRODUCTION_ISSUE',70,71,'PRODUCTION_ISSUE',101,20,-1,10,3,30,1,'0'),(300,'000001','PRD_WORK_ORDER',7,NULL,'PRODUCTION_IN',201,21,1,5,3,15,1,'0')");
            erp.jdbc.update(
                    "INSERT INTO wm_sales_issue VALUES(77,'000001',88,'C88','Output"
                            + " customer',90,'SO90','0')");
            erp.jdbc.update(
                    "INSERT INTO"
                        + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,material_stock_id,batch_id,tx_flag,tx_quantity,unit_cost,tx_amount,tracking_version,del_flag)"
                        + " VALUES(301,'000001','WM_SALES_ISSUE',77,'SALES_OUT',201,21,-1,2,3,6,1,'0')");
            mes =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    "jdbc:h2:mem:batchRecallReview"
                                            + UUID.randomUUID()
                                            + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                    "sa",
                                    ""));
            mes.execute(
                    "CREATE TABLE lt_wo_consumption(consumption_id BIGINT,tenant_id"
                        + " VARCHAR(20),wo_id BIGINT,material_id BIGINT,lot_id"
                        + " BIGINT,source_allocation_id BIGINT,consumed_qty DECIMAL(18,4),del_flag"
                        + " CHAR)");
            mes.execute(
                    "CREATE TABLE lt_sn_consumption(entry_id BIGINT,tenant_id"
                        + " VARCHAR(20),identity_id BIGINT,component_material_id"
                        + " BIGINT,component_lot_id BIGINT,quantity DECIMAL(18,4),wo_consumption_id"
                        + " BIGINT,entry_type VARCHAR(20))");
            mes.execute(
                    "CREATE TABLE lt_lot_allocation(allocation_id BIGINT,tenant_id"
                            + " VARCHAR(20),lot_id BIGINT,material_id BIGINT,erp_batch_id"
                            + " BIGINT,quantity DECIMAL(18,4),source_type VARCHAR(64),source_id"
                            + " BIGINT,source_line_id BIGINT,allocation_version BIGINT)");
            mes.execute(
                    "CREATE TABLE lt_lot(lot_id BIGINT,tenant_id VARCHAR(20),lot_code"
                        + " VARCHAR(128),quantity DECIMAL(18,4),consumed_qty DECIMAL(18,4),status"
                        + " VARCHAR(32),produced_date TIMESTAMP,expiry_date TIMESTAMP,del_flag"
                        + " CHAR)");
            mes.execute(
                    "CREATE TABLE pro_product_lot_output(output_id BIGINT,tenant_id"
                            + " VARCHAR(20),mes_wo_id BIGINT,source_allocation_id BIGINT)");
            mes.execute(
                    "CREATE TABLE pro_sn(identity_id BIGINT,tenant_id VARCHAR(20),mes_wo_id"
                            + " BIGINT,del_flag CHAR)");
            mes.update(
                    "INSERT INTO lt_lot"
                        + " VALUES(11,'000001','RAW20',10,5,'IN_USE',NULL,NULL,'0'),(12,'000001','OUTPUT21',5,0,'IN_STOCK',NULL,NULL,'0')");
            mes.update(
                    "INSERT INTO lt_lot_allocation"
                        + " VALUES(101,'000001',11,1,20,10,'PRODUCTION_ISSUE',70,71,1),(201,'000001',12,2,21,5,'PRODUCTION_OUTPUT',7,21,1)");
            mes.update("INSERT INTO lt_wo_consumption VALUES(101,'000001',7,1,11,101,5,'0')");
            mes.update("INSERT INTO pro_product_lot_output VALUES(401,'000001',7,201)");
            mesReader =
                    new MesIdentityTraceReader(
                            mes, mock(MesTraceContext.class), mock(SerialIdentityPort.class));
            service = new MaterialIdentityTraceService(List.of(erp.reader, mesReader));
            tenant = mockStatic(TenantHelper.class);
            try {
                auth = mockStatic(StpUtil.class);
                try {
                    tenant.when(TenantHelper::isEnable).thenReturn(true);
                    tenant.when(TenantHelper::getTenantId).thenReturn("000001");
                    auth.when(() -> StpUtil.hasPermission("wm:trace:query")).thenReturn(true);
                    auth.when(() -> StpUtil.hasPermission("mes:pro:trace:query")).thenReturn(true);
                } catch (RuntimeException | Error failure) {
                    auth.close();
                    throw failure;
                }
            } catch (RuntimeException | Error failure) {
                tenant.close();
                throw failure;
            }
        }

        public void close() {
            auth.close();
            tenant.close();
        }
    }
}
