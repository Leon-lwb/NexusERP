package com.nexus.mes.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 来源证明查询独立于目标业务夹具验证，避免测试替身掩盖真实来源条件。
 */
class MesErpSourceReaderTest {
    JdbcTemplate jdbc;
    MesErpSourceReader reader;
    ObjectMapper json = new ObjectMapper();
    MaterialPolicyPort policies;
    MesTraceContext context;

    @BeforeEach
    void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(ds);
        // 使多次数据库连接共享同一个内存来源库，不依赖外部数据库。
        ds.setURL(ds.getURL() + ";DB_CLOSE_DELAY=-1");
        context = spy(new MesTraceContext(mock(MesProgressTransactionGuard.class)));
        doReturn("000001").when(context).tenant();
        policies = mock(MaterialPolicyPort.class);
        when(policies.policy(anyString(), anyLong()))
                .thenReturn(new TrackingPolicy(true, false, true, 1L, TrackingStage.STRICT));
        reader = new MesErpSourceReader(jdbc, context, json, policies);
        jdbc.execute(
                "CREATE TABLE wm_trace_outbox(event_id varchar(64),tenant_id"
                    + " varchar(20),source_module varchar(16),event_type varchar(64),aggregate_type"
                    + " varchar(64),aggregate_id bigint,aggregate_version bigint,payload"
                    + " clob,content_hash varchar(64),status varchar(24))");
        jdbc.execute(
                "CREATE TABLE wm_trace_posting_group(posting_group_id bigint,tenant_id"
                        + " varchar(20),status varchar(24),material_id bigint,source_doc_type"
                        + " varchar(64),source_doc_id bigint,detail_version bigint default 17)");
        jdbc.execute(
                "CREATE TABLE wm_material_stock_tx(tenant_id varchar(20),posting_group_id"
                        + " bigint,batch_id bigint,material_id bigint,tx_flag int,tx_quantity"
                        + " decimal(18,4),sn_id bigint,material_stock_id bigint)");
        jdbc.execute(
                "CREATE TABLE wm_document_sn(tenant_id varchar(20),posting_group_id"
                        + " bigint,identity_id bigint)");
        jdbc.execute(
                "CREATE TABLE prd_work_order(tenant_id varchar(20),wo_id bigint,wo_status"
                        + " varchar(32),material_id bigint,planned_qty decimal(18,4),del_flag"
                        + " char(1))");
        jdbc.execute(
                "CREATE TABLE wm_batch(tenant_id varchar(20),batch_id bigint,material_id"
                        + " bigint,del_flag char(1))");
        jdbc.execute(
                "CREATE TABLE wm_trace_material_reservation(reservation_id bigint,tenant_id"
                        + " varchar(20),erp_wo_id bigint,mes_wo_id bigint,material_id bigint,status"
                        + " varchar(24))");
        jdbc.execute(
                "CREATE TABLE wm_trace_material_reservation_line(reservation_line_id"
                        + " bigint,tenant_id varchar(20),reservation_id bigint,erp_batch_id"
                        + " bigint,quantity decimal(18,4),identity_id bigint,source_stock_id"
                        + " bigint,version bigint default 3)");
        jdbc.execute(
                "CREATE TABLE prd_subcontract(subcontract_id bigint,tenant_id"
                        + " varchar(20),subcontract_no varchar(64),material_id bigint,material_code"
                        + " varchar(64),material_name varchar(64),planned_qty decimal(18,4),status"
                        + " varchar(24),del_flag char(1))");
    }

    MaterialTraceEvent event(String type, Map<String, Object> payload) {
        try {
            String body = json.writeValueAsString(payload);
            return new MaterialTraceEvent(
                    "source",
                    "000001",
                    "ERP",
                    type,
                    "STOCK_POSTING",
                    7L,
                    1,
                    1,
                    body,
                    TraceHashes.sha256(body));
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    void publish(MaterialTraceEvent e) {
        jdbc.update(
                "INSERT INTO wm_trace_outbox VALUES(?,?,?,?,?,?,?,?,?,'NEW')",
                e.eventId(),
                e.tenantId(),
                e.sourceModule(),
                e.eventType(),
                e.aggregateType(),
                e.aggregateId(),
                e.aggregateVersion(),
                e.payload(),
                e.contentHash());
    }

    MaterialTraceEvent issue(int qty) {
        return event(
                "ERP_MATERIAL_SOURCE_POSTED",
                Map.of("postingGroupId", 7, "materialId", 200, "erpBatchId", 500, "quantity", qty));
    }

    void postedIssue() {
        jdbc.update(
                "INSERT INTO"
                    + " wm_trace_posting_group(posting_group_id,tenant_id,status,material_id,source_doc_type,source_doc_id)"
                    + " VALUES(7,'000001','POSTED',200,'WM_PRODUCTION_ISSUE',55)");
        jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock_tx(tenant_id,posting_group_id,batch_id,material_id,tx_flag,tx_quantity)"
                    + " VALUES('000001',7,500,200,-1,4)");
    }

    @Test
    void noErpSourceOutboxCannotBeReplacedWithTargetInbox() {
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(issue(4)));
    }

    @Test
    void sourceJournalAloneDoesNotProvePosting() {
        var e = issue(4);
        publish(e);
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void sourcePostedGroupAndActualBatchQuantityMustMatch() {
        var e = issue(4);
        publish(e);
        postedIssue();
        assertEquals(e.contentHash(), reader.verifyEvent(e).contentHash());
        jdbc.update("UPDATE wm_material_stock_tx SET tx_quantity=3");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void sourcePayloadCannotChangeAfterPublication() {
        var e = issue(4);
        publish(e);
        postedIssue();
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(issue(3)));
    }

    @Test
    void shipmentIdentityMustBelongToPostedSalesDocument() {
        var e =
                event(
                        "ERP_SHIPMENT_POSTED",
                        Map.of(
                                "postingGroupId",
                                7,
                                "sourceDocType",
                                "WM_SALES_ISSUE",
                                "sourceDocId",
                                55,
                                "identityIds",
                                List.of("10001")));
        publish(e);
        jdbc.update(
                "INSERT INTO"
                    + " wm_trace_posting_group(posting_group_id,tenant_id,status,material_id,source_doc_type,source_doc_id)"
                    + " VALUES(7,'000001','POSTED',100,'WM_SALES_ISSUE',55)");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
        jdbc.update("INSERT INTO wm_document_sn VALUES('000001',7,10001)");
        assertEquals(e.eventId(), reader.verifyEvent(e).eventId());
    }

    @Test
    void productionAuthorizationRequiresRealWorkorderAndMatchingBatch() {
        var e =
                event(
                        "ERP_PRODUCTION_LOT_AUTHORIZED",
                        Map.of(
                                "sourceId",
                                20,
                                "erpWoId",
                                20,
                                "mesWoId",
                                10,
                                "materialId",
                                100,
                                "erpBatchId",
                                600,
                                "quantity",
                                "10"));
        publish(e);
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
        jdbc.update("INSERT INTO prd_work_order VALUES('000001',20,'IN_PROGRESS',100,10,'0')");
        jdbc.update("INSERT INTO wm_batch VALUES('000001',600,100,'0')");
        assertEquals(e.eventId(), reader.verifyEvent(e).eventId());
        jdbc.update("UPDATE prd_work_order SET wo_status='CANCELLED'");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void backflushAuthorizationRequiresAnActualReservationLine() {
        var e =
                event(
                        "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",
                        Map.of(
                                "sourceId",
                                80,
                                "sourceLineId",
                                81,
                                "erpWoId",
                                20,
                                "mesWoId",
                                10,
                                "materialId",
                                200,
                                "erpBatchId",
                                500,
                                "quantity",
                                "4"));
        publish(e);
        jdbc.update("INSERT INTO prd_work_order VALUES('000001',20,'IN_PROGRESS',100,10,'0')");
        jdbc.update("INSERT INTO wm_batch VALUES('000001',500,200,'0')");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
        jdbc.update(
                "INSERT INTO wm_trace_material_reservation"
                        + " VALUES(80,'000001',20,10,200,'RESERVED')");
        jdbc.update(
                "INSERT INTO"
                    + " wm_trace_material_reservation_line(reservation_line_id,tenant_id,reservation_id,erp_batch_id,quantity)"
                    + " VALUES(81,'000001',80,500,4)");
        assertEquals(e.eventId(), reader.verifyEvent(e).eventId());
    }

    @Test
    void subcontractCannotUseClientParametersAsRealSource() {
        assertThrows(RuntimeException.class, () -> reader.subcontract(77L, 100L, "SC77", 2));
        jdbc.update(
                "INSERT INTO prd_subcontract"
                        + " VALUES(77,'000001','SC77',100,'PCBA','Board',2,'RELEASED','0')");
        assertEquals(
                "ERP_SUBCONTRACT_SOURCE_CONFIRMED",
                reader.subcontract(77L, 100L, "SC77", 2).eventType());
        assertThrows(RuntimeException.class, () -> reader.subcontract(77L, 200L, "SC77", 2));
        assertThrows(RuntimeException.class, () -> reader.subcontract(77L, 100L, "SC77", 3));
    }

    MaterialTraceEvent serialIssue(Long batch) {
        var p = new LinkedHashMap<String, Object>();
        p.put("postingGroupId", 7);
        p.put("materialId", 200);
        p.put("erpBatchId", batch);
        p.put("identityId", 10001);
        p.put("sourceStockId", 90001);
        p.put("quantity", "1");
        return event("ERP_MATERIAL_SOURCE_POSTED", p);
    }

    @Test
    void eachSerialPostedEventUsesItsOwnIdentityAndStockInsteadOfTheWholeBatch() {
        var e = serialIssue(500L);
        publish(e);
        jdbc.update(
                "INSERT INTO"
                    + " wm_trace_posting_group(posting_group_id,tenant_id,status,material_id,source_doc_type,source_doc_id)"
                    + " VALUES(7,'000001','POSTED',200,'WM_PRODUCTION_ISSUE',55)");
        jdbc.update(
                "INSERT INTO wm_material_stock_tx"
                    + " VALUES('000001',7,500,200,-1,1,10001,90001),('000001',7,500,200,-1,1,10002,90002)");
        assertEquals(e.contentHash(), reader.verifyEvent(e).contentHash());
        assertEquals(17, reader.verifyEvent(e).sourceVersion());
        jdbc.update("UPDATE wm_material_stock_tx SET material_stock_id=99999 WHERE sn_id=10001");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void serialOnlyPostedSourceAllowsNullBatchWithRealStockAndWholeUnit() {
        when(policies.policy("000001", 200L))
                .thenReturn(new TrackingPolicy(false, true, true, 1L, TrackingStage.STRICT));
        var e = serialIssue(null);
        publish(e);
        jdbc.update(
                "INSERT INTO"
                    + " wm_trace_posting_group(posting_group_id,tenant_id,status,material_id,source_doc_type,source_doc_id)"
                    + " VALUES(7,'000001','POSTED',200,'WM_PRODUCTION_ISSUE',55)");
        jdbc.update(
                "INSERT INTO wm_material_stock_tx VALUES('000001',7,NULL,200,-1,1,10001,90001)");
        assertEquals(e.eventId(), reader.verifyEvent(e).eventId());
        jdbc.update("UPDATE wm_material_stock_tx SET sn_id=10002");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void serialOnlyBackflushRequiresTheExactRealReservationIdentityAndStock() {
        when(policies.policy("000001", 200L))
                .thenReturn(new TrackingPolicy(false, true, true, 1L, TrackingStage.STRICT));
        var p = new LinkedHashMap<String, Object>();
        p.put("sourceId", 80);
        p.put("sourceLineId", 81);
        p.put("erpWoId", 20);
        p.put("mesWoId", 10);
        p.put("materialId", 200);
        p.put("erpBatchId", null);
        p.put("identityId", 10001);
        p.put("sourceStockId", 90001);
        p.put("quantity", "1");
        var e = event("ERP_BACKFLUSH_ALLOCATION_AUTHORIZED", p);
        publish(e);
        jdbc.update("INSERT INTO prd_work_order VALUES('000001',20,'IN_PROGRESS',100,10,'0')");
        jdbc.update(
                "INSERT INTO wm_trace_material_reservation"
                        + " VALUES(80,'000001',20,10,200,'RESERVED')");
        jdbc.update(
                "INSERT INTO wm_trace_material_reservation_line"
                        + " VALUES(81,'000001',80,NULL,1,10001,90001,3)");
        assertEquals(e.eventId(), reader.verifyEvent(e).eventId());
        assertEquals(3, reader.verifyEvent(e).sourceVersion());
        jdbc.update("UPDATE wm_trace_material_reservation_line SET identity_id=10002");
        assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
    }

    @Test
    void reservationLineCannotBorrowAHeaderFromACaseFoldedTenant() {
        doReturn("tenant_case").when(context).tenant();
        jdbc.execute(
                "ALTER TABLE wm_trace_material_reservation ALTER COLUMN tenant_id"
                        + " VARCHAR_IGNORECASE(20)");
        jdbc.execute(
                "ALTER TABLE wm_trace_material_reservation_line ALTER COLUMN tenant_id"
                        + " VARCHAR_IGNORECASE(20)");
        var payload =
                Map.of(
                        "sourceId",
                        80,
                        "sourceLineId",
                        81,
                        "erpWoId",
                        20,
                        "mesWoId",
                        10,
                        "materialId",
                        200,
                        "erpBatchId",
                        500,
                        "quantity",
                        "4");
        try {
            String body = json.writeValueAsString(payload);
            var e =
                    new MaterialTraceEvent(
                            "case-source",
                            "tenant_case",
                            "ERP",
                            "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",
                            "MATERIAL_RESERVATION_LINE",
                            81L,
                            1,
                            1,
                            body,
                            TraceHashes.sha256(body));
            publish(e);
            jdbc.update(
                    "INSERT INTO prd_work_order VALUES('tenant_case',20,'IN_PROGRESS',100,10,'0')");
            jdbc.update("INSERT INTO wm_batch VALUES('tenant_case',500,200,'0')");
            jdbc.update(
                    "INSERT INTO wm_trace_material_reservation"
                            + " VALUES(80,'tenant_case',20,10,200,'RESERVED')");
            jdbc.update(
                    "INSERT INTO"
                        + " wm_trace_material_reservation_line(reservation_line_id,tenant_id,reservation_id,erp_batch_id,quantity)"
                        + " VALUES(81,'TENANT_CASE',80,500,4)");
            assertThrows(RuntimeException.class, () -> reader.verifyEvent(e));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }
}
