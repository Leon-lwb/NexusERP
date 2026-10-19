package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用原入库策略与真实内存数据库事务事实，不依赖库存转换器或跨模块测试。
 */
class WmReceiptPolicyMigrationReplayTest {
    @BeforeAll
    static void metadata() {
        WmBatchProductionReceiptTest.metadata();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDIT", "STRICT"})
    void batchReceiptManifestKeepsItsOriginalGroupAfterIdentityAndPolicyMigration(String stage) {
        var f = fixture();
        var first = f.post();
        converted(f, stage);
        var before = state(f);
        clear(f);
        var replay = f.post();
        assertTrue(replay.replay());
        assertEquals(first.receiptId(), replay.receiptId());
        assertEquals(first.postingGroupId(), replay.postingGroupId());
        assertEquals(before, state(f));
        noEffects(f);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDIT", "STRICT"})
    void historicalV1ReceiptKeepsItsOriginalProofAfterThePolicyBecomesSerial(String stage) {
        var f = fixture();
        f.jdbc.update("UPDATE bd_material SET tracking_stage='AUDIT'");
        f.completeWorkOrder();
        f.receipt.setBatchId(50L);
        f.receipt.setBatchCode("B50");
        f.tx.execute(
                s -> {
                    f.receiptService.execute(5L);
                    return null;
                });
        assertEquals(1L, f.facts().get(0).getTrackingVersion());
        assertNull(f.facts().get(0).getPostingGroupId());
        var first = f.post();
        converted(f, stage);
        var before = state(f);
        clear(f);
        var replay = f.post();
        assertTrue(replay.replay());
        assertEquals(first.receiptId(), replay.receiptId());
        assertEquals(first.postingGroupId(), replay.postingGroupId());
        assertEquals(before, state(f));
        noEffects(f);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDIT", "STRICT"})
    void directOriginalV1ReceiptGuardAcceptsConvertedZeroHistoryWithoutRecredit(String stage) {
        var f = fixture();
        f.completeWorkOrder();
        f.receipt.setBatchId(50L);
        f.receipt.setBatchCode("B50");
        f.tx.execute(
                s -> {
                    f.receiptService.execute(5L);
                    return null;
                });
        converted(f, stage);
        var before = state(f);
        clear(f);
        f.tx.execute(
                s -> {
                    f.receiptService.execute(5L);
                    return null;
                });
        assertEquals(before, state(f));
        noEffects(f);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DELETE FROM wm_material_stock_tx WHERE source_doc_type='PRD_WORK_ORDER'",
                "UPDATE wm_material_stock_tx SET tx_amount=2 WHERE"
                        + " source_doc_type='PRD_WORK_ORDER'",
                "UPDATE wm_material_stock_tx SET batch_id=51 WHERE"
                        + " source_doc_type='PRD_WORK_ORDER'",
                "UPDATE wm_trace_posting_group SET quantity=4 WHERE tx_type='PRODUCTION_IN_BATCH'"
            })
    void conversionCannotMakeAnIncompleteOrChangedOriginalBatchProofReplayable(String sql) {
        var f = fixture();
        f.post();
        converted(f, "STRICT");
        f.jdbc.update(sql);
        var before = state(f);
        clear(f);
        assertThrows(ServiceException.class, f::post);
        assertEquals(before, state(f));
        noEffects(f);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "quantity=4",
                "amount=2",
                "material_id=99",
                "fingerprint=REPEAT('b',64)",
                "source_hash=REPEAT('b',64)",
                "policy_version=99"
            })
    void legacyManifestHeaderTamperingCannotBeHiddenByItsMatchingCommandAndContentHash(
            String change) {
        var f = fixture();
        f.jdbc.update("UPDATE bd_material SET tracking_stage='AUDIT'");
        f.completeWorkOrder();
        f.receipt.setBatchId(50L);
        f.receipt.setBatchCode("B50");
        f.tx.execute(
                s -> {
                    f.receiptService.execute(5L);
                    return null;
                });
        f.post();
        converted(f, "AUDIT");
        f.jdbc.update(
                "UPDATE wm_trace_posting_group SET "
                        + change
                        + " WHERE tx_type='PRODUCTION_IN_LEGACY'");
        clear(f);
        assertThrows(ServiceException.class, f::post);
        noEffects(f);
    }

    @Test
    void anExtraOriginalReceiptFactRemainsAnErrorAfterPolicyMigration() {
        var f = fixture();
        f.post();
        converted(f, "STRICT");
        f.jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,tx_flag,tx_quantity,del_flag)"
                    + " VALUES(999,'t1','PRD_WORK_ORDER',1,'PRODUCTION_IN',1,1,'0')");
        clear(f);
        assertThrows(ServiceException.class, f::post);
        noEffects(f);
    }

    static WmBatchProductionReceiptTest.Fixture fixture() {
        var f = new WmBatchProductionReceiptTest.Fixture();
        f.jdbc.execute(
                "CREATE TABLE bd_material(material_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),status BIGINT,del_flag CHAR(1),is_batch_manage"
                        + " BIGINT,is_serial_manage BIGINT,allow_mixed_lot INT,tracking_version"
                        + " BIGINT,tracking_stage VARCHAR(16))");
        f.jdbc.update("INSERT INTO bd_material VALUES(20,'t1',1,'0',1,0,0,7,'STRICT')");
        doCallRealMethod().when(f.store).policy(20L);
        ReflectionTestUtils.setField(
                f.guard, "serialProof", new WmSerialInventoryProofService(f.store));
        return f;
    }

    static void converted(WmBatchProductionReceiptTest.Fixture f, String stage) {
        // 当前离线转换器拒绝关联工单的库存；此处仅构造维度保持一致的转换后状态，
        // 用于检验来源证明适配器，不据此宣称已授权执行该转换。
        f.stock.setStockQuantity(BigDecimal.ZERO);
        f.stock.setTotalCost(BigDecimal.ZERO);
        f.stock.setTrackingVersion(2L);
        f.stock.setValuationGroupId(800L);
        f.jdbc.execute(
                "CREATE TABLE wm_material_stock(stock_id BIGINT PRIMARY KEY,sn_id"
                        + " BIGINT,warehouse_id BIGINT,location_id BIGINT,stock_quantity"
                        + " DECIMAL(18,4),reserved_quantity DECIMAL(18,4),total_cost"
                        + " DECIMAL(18,4),tracking_version BIGINT,valuation_group_id BIGINT)");
        f.jdbc.update("INSERT INTO wm_material_stock VALUES(101,NULL,30,40,0,0,0,2,800)");
        for (int i = 1; i <= 5; i++) {
            f.jdbc.update(
                    "INSERT INTO wm_material_stock VALUES(?,?,?, ?,?,0,?,2,800)",
                    200 + i,
                    300 + i,
                    i == 1 ? 90 : 30,
                    i == 1 ? 99 : 40,
                    i == 2 ? 0 : 1,
                    i == 2 ? BigDecimal.ZERO : new BigDecimal(i == 5 ? "0.3335" : "0.3333"));
        }
        f.jdbc.update(
                "UPDATE bd_material SET is_serial_manage=1,tracking_version=8,tracking_stage=?",
                stage);
    }

    static String state(WmBatchProductionReceiptTest.Fixture f) {
        return f.jdbc.queryForList("SELECT * FROM wm_material_stock ORDER BY stock_id").toString()
                + f.jdbc.queryForList("SELECT * FROM wm_material_stock_tx ORDER BY tx_id")
                + f.jdbc.queryForList(
                        "SELECT * FROM wm_trace_posting_group ORDER BY posting_group_id")
                + f.jdbc.queryForList("SELECT * FROM wm_trace_outbox ORDER BY event_id")
                + f.stock.getStockQuantity()
                + "/"
                + f.stock.getTotalCost();
    }

    static void clear(WmBatchProductionReceiptTest.Fixture f) {
        clearInvocations(
                f.stockMapper, f.txMapper, f.batchMapper, f.orders, f.backflush, f.batches);
    }

    static void noEffects(WmBatchProductionReceiptTest.Fixture f) {
        verifyNoInteractions(
                f.stockMapper, f.txMapper, f.batchMapper, f.orders, f.backflush, f.batches);
    }
}
