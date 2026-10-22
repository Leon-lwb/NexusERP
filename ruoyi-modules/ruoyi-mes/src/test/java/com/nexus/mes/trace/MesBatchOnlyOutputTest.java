package com.nexus.mes.trace;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.trace.*;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 产品数量取自实际制造批次与报工，库存授权只规定可用额度上限。
 */
class MesBatchOnlyOutputTest {
    MesTraceBusinessTest f;

    @BeforeEach
    void setup() {
        f = new MesTraceBusinessTest();
        f.setup();
        f.mixed = new TrackingPolicy(true, false, true, 1L, TrackingStage.STRICT);
        Mockito.when(f.policies.policy("000001", 100L)).thenReturn(f.mixed);
        f.jdbc.execute("ALTER TABLE pro_flow_card ADD completed_qty decimal(18,4) DEFAULT 0");
        f.jdbc.execute("ALTER TABLE pro_flow_card ADD scrap_qty decimal(18,4) DEFAULT 0");
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD mes_wo_id bigint");
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD planned_qty decimal(18,4)");
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD good_qty decimal(18,4) DEFAULT 0");
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD scrap_qty decimal(18,4) DEFAULT 0");
        f.jdbc.execute(
                "CREATE TABLE pro_product_lot_output(output_id bigint PRIMARY KEY,tenant_id"
                    + " varchar(20),command_id varchar(128),line_no bigint,mes_wo_id bigint,card_id"
                    + " bigint,step_record_id bigint,lot_id bigint,source_allocation_id"
                    + " bigint,material_id bigint,quantity decimal(18,4),result"
                    + " varchar(16),source_version bigint,created_by bigint,created_at timestamp"
                    + " DEFAULT"
                    + " current_timestamp,UNIQUE(tenant_id,command_id,line_no),UNIQUE(tenant_id,lot_id))");
        f.jdbc.update("UPDATE pro_work_order SET planned_qty=10 WHERE mes_wo_id=10");
        f.jdbc.update("UPDATE pro_flow_card SET planned_qty=10 WHERE card_id=11");
        f.jdbc.update(
                "INSERT INTO"
                    + " pro_flow_card_step(step_record_id,tenant_id,card_id,mes_wo_id,sequence_num,step_status,planned_qty,good_qty,scrap_qty,del_flag)"
                    + " VALUES(31,'000001',11,10,1,'IN_PROGRESS',10,0,0,'0')");
    }

    @AfterEach
    void cleanup() {
        f.cleanup();
    }

    long quota(long lot, int actual, String status, String qc) {
        if (lot == 7) {
            f.jdbc.update(
                    "UPDATE lt_lot SET quantity=?,status=?,qc_status=? WHERE lot_id=7",
                    actual,
                    status,
                    qc);
        } else {
            f.lot(lot, 100, actual);
            f.jdbc.update("UPDATE lt_lot SET status=?,qc_status=? WHERE lot_id=?", status, qc, lot);
        }
        if (f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_inbox WHERE event_id='output-quota'",
                        Integer.class)
                == 0) {
            var grant =
                    f.erp(
                            "output-quota",
                            "ERP_PRODUCTION_LOT_AUTHORIZED",
                            90,
                            Map.of(
                                    "sourceType",
                                    "PRODUCTION_OUTPUT",
                                    "sourceId",
                                    20L,
                                    "sourceLineId",
                                    1L,
                                    "mesWoId",
                                    10L,
                                    "erpWoId",
                                    20L,
                                    "erpBatchId",
                                    600L,
                                    "materialId",
                                    100L,
                                    "quantity",
                                    10));
            f.inTx(() -> f.deliver(grant));
        }
        return f.allocate("map-output-" + lot, "output-quota", lot, actual);
    }

    MesProductLotOutputWriter writer() {
        return new MesProductLotOutputWriter(
                f.jdbc, f.context, f.events, f.policies, f.preparedPolicies);
    }

    MesProductLotOutputWriter.OutputLine line(long lot, long allocation, int qty, String result) {
        return new MesProductLotOutputWriter.OutputLine(
                lot, allocation, new BigDecimal(qty), result);
    }

    List<Long> record(String cmd, long step, MesProductLotOutputWriter.OutputLine... lines) {
        return f.inTx(() -> writer().record(cmd, 11L, step, List.of(lines), 1L));
    }

    void output(long ignored, long lot, long allocation, int qty, String result) {
        record("actual-output-" + lot, 31, line(lot, allocation, qty, result));
    }

    CompletionManifest freeze(int good, int scrap) {
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='COMPLETED',good_qty=?,scrap_qty=? WHERE"
                        + " step_record_id=31",
                good,
                scrap);
        f.jdbc.update(
                "UPDATE pro_flow_card SET completed_qty=?,scrap_qty=? WHERE card_id=11",
                good,
                scrap);
        return f.inTx(() -> f.manifests.freeze(f.wo(good, scrap)));
    }

    List<CompletionManifestLine> lines(CompletionManifest m) {
        return CompletionManifestIntegrity.read(
                        f.manifests, "000001", m.manifestId(), m.revision(), m.contentHash())
                .lines();
    }

    @Test
    void batchOnlyGoodUsesActualLotInsteadOfPlannedQuotaOrGrantResult() {
        long q = quota(7, 8, "IN_STOCK", "PASS");
        output(101, 7, q, 8, "GOOD");
        var m = freeze(8, 0);
        var rows = lines(m);
        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("8").compareTo(rows.get(0).quantity()));
        assertEquals("GOOD", rows.get(0).result());
        assertEquals(600L, rows.get(0).batchId());
        assertEquals(7L, rows.get(0).localLotId());
        assertFalse(
                f.events
                        .payload(
                                f.jdbc.queryForMap(
                                        "SELECT * FROM mes_trace_inbox WHERE"
                                                + " event_id='output-quota'"))
                        .has("result"));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='COMPLETION_MANIFEST_FROZEN'",
                        Integer.class));
    }

    @Test
    void batchOnlyMixedGoodAndScrapShareOneGrantAndOneFinalManifest() {
        long good = quota(7, 7, "IN_STOCK", "PASS"), scrap = quota(12, 3, "SCRAPPED", "FAIL");
        output(101, 7, good, 7, "GOOD");
        output(102, 12, scrap, 3, "SCRAP");
        var m = freeze(7, 3);
        var rows = lines(m);
        assertEquals(2, rows.size());
        assertEquals(
                Set.of("GOOD", "SCRAP"),
                new HashSet<>(rows.stream().map(CompletionManifestLine::result).toList()));
        assertTrue(rows.stream().allMatch(r -> r.batchId() == 600L));
        assertEquals(0, new BigDecimal("7").compareTo(m.goodQty()));
        assertEquals(0, new BigDecimal("3").compareTo(m.scrapQty()));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_completion_manifest", Integer.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='COMPLETION_MANIFEST_FROZEN'",
                        Integer.class));
    }

    @Test
    void frozenBatchReadNeverFollowsLaterLotQualityOrQuantityChanges() {
        long q = quota(7, 8, "IN_STOCK", "PASS");
        output(101, 7, q, 8, "GOOD");
        var m = freeze(8, 0);
        f.jdbc.update(
                "UPDATE lt_lot SET quantity=9,qc_status='FAIL',status='SCRAPPED' WHERE lot_id=7");
        f.jdbc.update("UPDATE pro_product_lot_output SET quantity=9,result='SCRAP' WHERE lot_id=7");
        var rows = lines(m);
        assertEquals("GOOD", rows.get(0).result());
        assertEquals(0, new BigDecimal("8").compareTo(rows.get(0).quantity()));
    }

    @Test
    void plannedGrantAloneCannotGenerateActualGoodOutput() {
        quota(7, 10, "IN_STOCK", "PASS");
        assertThrows(RuntimeException.class, () -> freeze(10, 0));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_completion_manifest", Integer.class));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='COMPLETION_MANIFEST_FROZEN'",
                        Integer.class));
    }

    @Test
    void actualLotQuantityAndQcCannotBeInventedByClassificationRequest() {
        long q = quota(7, 10, "IN_STOCK", "PENDING");
        assertThrows(
                RuntimeException.class, () -> record("pending-qc", 31, line(7, q, 10, "GOOD")));
        f.jdbc.update("UPDATE lt_lot SET qc_status='PASS' WHERE lot_id=7");
        assertThrows(
                RuntimeException.class, () -> record("false-actual", 31, line(7, q, 9, "GOOD")));
        f.jdbc.update("UPDATE lt_lot SET consumed_qty=1 WHERE lot_id=7");
        assertThrows(
                RuntimeException.class, () -> record("used-output", 31, line(7, q, 10, "GOOD")));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_product_lot_output", Integer.class));
    }

    @Test
    void scrapClassificationRequiresTheActualLotToHaveBeenScrapped() {
        long q = quota(7, 10, "QUARANTINED", "FAIL");
        assertThrows(
                RuntimeException.class, () -> record("not-scrapped", 31, line(7, q, 10, "SCRAP")));
        f.jdbc.update("UPDATE lt_lot SET status='SCRAPPED' WHERE lot_id=7");
        record("scrapped", 31, line(7, q, 10, "SCRAP"));
        var m = freeze(0, 10);
        assertEquals(1, lines(m).size());
        assertEquals("SCRAP", lines(m).get(0).result());
    }

    @Test
    void classificationRequiresRealRunningOperationAndFinalGoodSource() {
        long q = quota(7, 10, "IN_STOCK", "PASS");
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='PENDING' WHERE step_record_id=31");
        assertThrows(
                RuntimeException.class, () -> record("future-output", 31, line(7, q, 10, "GOOD")));
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='IN_PROGRESS' WHERE step_record_id=31");
        f.jdbc.update(
                "INSERT INTO"
                    + " pro_flow_card_step(step_record_id,tenant_id,card_id,mes_wo_id,sequence_num,step_status,planned_qty,del_flag)"
                    + " VALUES(32,'000001',11,10,2,'PENDING',10,'0')");
        assertThrows(
                RuntimeException.class, () -> record("early-good", 31, line(7, q, 10, "GOOD")));
    }

    @Test
    void operationActualsMustReconcileEvenWhenWorkOrderTotalMatches() {
        long q = quota(7, 8, "IN_STOCK", "PASS");
        output(101, 7, q, 8, "GOOD");
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='COMPLETED',good_qty=7,scrap_qty=0 WHERE"
                        + " step_record_id=31");
        f.jdbc.update("UPDATE pro_flow_card SET completed_qty=8,scrap_qty=0 WHERE card_id=11");
        assertThrows(RuntimeException.class, () -> f.inTx(() -> f.manifests.freeze(f.wo(8, 0))));
        assertEquals(
                "IN_PROGRESS",
                f.jdbc.queryForObject(
                        "SELECT wo_status FROM pro_work_order WHERE mes_wo_id=10", String.class));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_completion_manifest", Integer.class));
    }

    @Test
    void classificationAndItsOutboxRollBackWithTheOperationTransaction() {
        long q = quota(7, 10, "IN_STOCK", "PASS");
        assertThrows(
                IllegalStateException.class,
                () ->
                        f.inTx(
                                () -> {
                                    writer().record(
                                                    "rollback-output",
                                                    11L,
                                                    31L,
                                                    List.of(line(7, q, 10, "GOOD")),
                                                    1L);
                                    throw new IllegalStateException("report failed");
                                }));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_product_lot_output", Integer.class));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='PRODUCT_LOT_OUTPUT_RECORDED'",
                        Integer.class));
        assertEquals(1, record("rollback-output", 31, line(7, q, 10, "GOOD")).size());
    }

    @Test
    void outputReplayAfterFinalCompletionIsDurableAndCannotChangeContentOrDoubleCount() {
        long q = quota(7, 10, "IN_STOCK", "PASS");
        var ids = record("once", 31, line(7, q, 10, "GOOD"));
        freeze(10, 0);
        assertEquals(ids, record("once", 31, line(7, q, 10, "GOOD")));
        assertThrows(RuntimeException.class, () -> record("once", 31, line(7, q, 9, "GOOD")));
        assertThrows(RuntimeException.class, () -> record("twice", 31, line(7, q, 10, "GOOD")));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_product_lot_output", Integer.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='PRODUCT_LOT_OUTPUT_RECORDED'",
                        Integer.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT MIN(aggregate_version) FROM mes_trace_outbox WHERE"
                                + " event_type='PRODUCT_LOT_OUTPUT_RECORDED'",
                        Integer.class));
    }

    @Test
    void actualOutputCannotExceedItsSpecificSourceAllocationOrChangeWorkOrder() {
        long q = quota(7, 10, "IN_STOCK", "PASS");
        f.jdbc.update("UPDATE lt_lot_allocation SET quantity=9 WHERE allocation_id=?", q);
        assertThrows(
                RuntimeException.class, () -> record("over-quota", 31, line(7, q, 10, "GOOD")));
        f.jdbc.update(
                "UPDATE lt_lot_allocation SET quantity=10,source_id=999 WHERE allocation_id=?", q);
        assertThrows(
                RuntimeException.class, () -> record("wrong-origin", 31, line(7, q, 10, "GOOD")));
    }

    @Test
    void laterOperationCannotBorrowEarlierScrapQuantity() {
        long good = quota(7, 7, "IN_STOCK", "PASS"), scrap = quota(12, 3, "SCRAPPED", "FAIL");
        f.jdbc.update(
                "INSERT INTO"
                    + " pro_flow_card_step(step_record_id,tenant_id,card_id,mes_wo_id,sequence_num,step_status,planned_qty,good_qty,scrap_qty,del_flag)"
                    + " VALUES(30,'000001',11,10,0,'COMPLETED',10,7,3,'0')");
        record("wrong-scrap-op", 31, line(7, good, 7, "GOOD"), line(12, scrap, 3, "SCRAP"));
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='COMPLETED',good_qty=7,scrap_qty=0 WHERE"
                        + " step_record_id=31");
        f.jdbc.update("UPDATE pro_flow_card SET completed_qty=7,scrap_qty=3 WHERE card_id=11");
        assertThrows(RuntimeException.class, () -> f.inTx(() -> f.manifests.freeze(f.wo(7, 3))));
    }

    @Test
    void frozenBatchOnlyOutputIsUnsettledUntilItsRealErpReceipt() {
        long q = quota(7, 10, "IN_STOCK", "PASS");
        output(101, 7, q, 10, "GOOD");
        freeze(10, 0);
        assertTrue(
                new MesTrackingUsageService(f.jdbc, f.context)
                        .hasUnsettledProduction("000001", 100L));
    }

    @Test
    void batchOnlyActualOutputUsesFrozenPolicyWhenCurrentMasterSaysSerialOnly() {
        long allocation = quota(7, 10, "IN_STOCK", "PASS");
        var reads = new AtomicInteger();
        var prepared = MesFrozenPolicyTestSupport.prepared(f, f.mixed, new AtomicBoolean(), reads);
        var actual =
                new MesProductLotOutputWriter(f.jdbc, f.context, f.events, f.policies, prepared);
        Mockito.when(f.policies.policy("000001", 100L))
                .thenReturn(new TrackingPolicy(false, true, true, 99L, TrackingStage.STRICT));
        Mockito.clearInvocations(f.policies);
        var ids =
                f.inTx(
                        () ->
                                actual.record(
                                        "frozen-batch-output",
                                        11L,
                                        31L,
                                        List.of(line(7, allocation, 10, "GOOD")),
                                        1L));
        assertEquals(1, ids.size());
        assertEquals(1, reads.get());
        Mockito.verifyNoInteractions(f.policies);
        assertEquals(
                "GOOD",
                f.jdbc.queryForObject("SELECT result FROM pro_product_lot_output", String.class));
    }
}
