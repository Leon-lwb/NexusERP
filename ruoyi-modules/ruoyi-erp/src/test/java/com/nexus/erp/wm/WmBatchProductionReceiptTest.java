package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.impl.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.service.impl.WmProductionRecptServiceImpl;
import com.nexus.erp.wm.strategy.impl.ProductionCompletionStrategy;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 沿实际清单适配、原入库执行、原库存策略与版本化来源证明验证整条调用链。
 */
class WmBatchProductionReceiptTest {
    @BeforeAll
    static void metadata() {
        for (Class<?> type :
                List.of(WmMaterialStock.class, WmMaterialStockTx.class, WmProductionRecpt.class)) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @Test
    void batchOnlyUsesAllGoodSubLotsAndOriginalStrategyCostWithoutMintingAnotherBatch() {
        var f = new Fixture();
        var result = f.post();
        assertEquals(5L, result.receiptId());
        assertNotNull(result.postingGroupId());
        assertFalse(result.replay());
        assertEquals(50L, f.receipt.getBatchId());
        assertEquals("B50", f.receipt.getBatchCode());
        assertEquals(0, new BigDecimal("5").compareTo(f.stock.getStockQuantity()));
        var facts = f.facts();
        assertEquals(1, facts.size());
        var fact = facts.get(0);
        assertEquals(50L, fact.getBatchId());
        assertEquals(1L, fact.getTrackingVersion());
        assertNull(fact.getSnId());
        assertEquals(result.postingGroupId(), fact.getPostingGroupId());
        assertEquals(new BigDecimal("5.0000"), fact.getTxQuantity());
        assertEquals(new BigDecimal("1.6667"), fact.getTxAmount());
        assertEquals(new BigDecimal("0.333333"), fact.getUnitCost());
        assertEquals(
                0,
                f.jdbc.queryForObject("SELECT received_qty FROM wm_batch", BigDecimal.class)
                        .compareTo(new BigDecimal("5")));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_trace_posting_group WHERE"
                                + " tx_type='PRODUCTION_IN_BATCH' AND status='POSTED'",
                        Integer.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_trace_outbox WHERE"
                                + " event_type='ERP_INVENTORY_POSTED' AND status='NEW'",
                        Integer.class));
        verifyNoInteractions(f.batches);
        verify(f.stockMapper, times(1)).addStock(101L, new BigDecimal("5"), 0);
        verify(f.orders).completeFromManifest(f.manifest);
        verify(f.backflush).backflushFromManifest(1L, new BigDecimal("5"), Set.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FROZEN", "APPLIED", "RECEIVED"})
    void replayAfterSaleOrMoveVerifiesOriginalWholeLedgerWithoutReadingOrRecreditingStock(
            String status) {
        var f = new Fixture();
        var first = f.post();
        f.stock.setStockQuantity(BigDecimal.ZERO);
        f.stock.setWarehouseId(90L);
        f.stock.setLocationId(99L);
        f.jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,tx_flag,batch_id,tx_quantity,del_flag)"
                    + " VALUES(502,'t1','WM_SALES_ISSUE',77,'SALES_ISSUE',-1,50,5,'0')");
        f.jdbc.update("UPDATE wm_batch SET issued_qty=5");
        clearInvocations(
                f.stockMapper, f.txMapper, f.batchMapper, f.guardMapper, f.orders, f.backflush);
        f.manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        new BigDecimal("5"),
                        BigDecimal.ZERO,
                        CompletionManifestIntegrity.hash(f.lines),
                        status);
        var replay = f.post();
        assertTrue(replay.replay());
        assertEquals(first.receiptId(), replay.receiptId());
        assertEquals(first.postingGroupId(), replay.postingGroupId());
        verifyNoInteractions(f.stockMapper, f.txMapper, f.batchMapper, f.orders, f.backflush);
        verify(f.guardMapper, never()).lockStock(anyString(), anyLong());
        assertEquals(0, f.stock.getStockQuantity().signum());
        assertEquals(
                2,
                f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock_tx", Integer.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_trace_posting_group", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DELETE FROM wm_material_stock_tx",
                "UPDATE wm_material_stock_tx SET batch_code='OTHER'",
                "UPDATE wm_material_stock_tx SET before_quantity=-1,after_quantity=4",
                "UPDATE wm_material_stock_tx SET tx_amount=2",
                "UPDATE wm_material_stock_tx SET source_doc_id=99",
                "UPDATE wm_material_stock_tx SET del_flag='1'",
                "UPDATE wm_trace_posting_group SET material_id=21",
                "UPDATE wm_trace_posting_group SET source_doc_line_id=9",
                "UPDATE wm_trace_posting_group SET source_hash=REPEAT('b',64)",
                "UPDATE wm_batch SET source_doc_no='OTHER'"
            })
    void tamperedOrIncompleteOriginalFactsCannotRecoverAReceipt(String sql) {
        var f = new Fixture();
        f.post();
        f.jdbc.update(sql);
        clearInvocations(f.stockMapper, f.txMapper, f.batchMapper);
        assertThrows(ServiceException.class, f::post);
        verifyNoInteractions(f.stockMapper, f.txMapper, f.batchMapper);
    }

    @Test
    void extraOriginalWorkOrderTransactionCannotBeHiddenByThePostingGroupFilter() {
        var f = new Fixture();
        f.post();
        f.jdbc.update(
                "INSERT INTO"
                    + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,tx_flag,batch_id,tx_quantity,del_flag)"
                    + " VALUES(502,'t1','PRD_WORK_ORDER',1,'PRODUCTION_IN',1,50,1,'0')");
        assertThrows(ServiceException.class, f::post);
    }

    @Test
    void splitErpBatchManifestFailsBeforeCompletingTheWorkOrderOrTouchingStock() {
        var f = new Fixture();
        f.lines =
                List.of(
                        f.lines.get(0),
                        new CompletionManifestLine(
                                2,
                                null,
                                null,
                                null,
                                51L,
                                502L,
                                20L,
                                new BigDecimal("3"),
                                "GOOD",
                                1));
        f.manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        new BigDecimal("5"),
                        BigDecimal.ZERO,
                        CompletionManifestIntegrity.hash(f.lines),
                        "FROZEN");
        assertThrows(ServiceException.class, f::post);
        verifyNoInteractions(
                f.orders, f.backflush, f.stockMapper, f.txMapper, f.batchMapper, f.batches);
    }

    @Test
    void oldUnversionedReceiptRetainsItsOriginalStockDimensionProof() {
        var f = new Fixture();
        f.completeWorkOrder();
        f.receipt.setBatchId(50L);
        f.receipt.setBatchCode("B50");
        f.tx.execute(
                s -> {
                    f.receiptService.execute(5L);
                    return null;
                });
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_trace_posting_group", Integer.class));
        assertDoesNotThrow(() -> f.guard.verifyExecuted(f.wo, new BigDecimal("5"), f.receipt));
        f.stock.setLocationId(99L);
        assertThrows(
                ServiceException.class,
                () -> f.guard.verifyExecuted(f.wo, new BigDecimal("5"), f.receipt));
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final WmMaterialStockMapper stockMapper = mock(WmMaterialStockMapper.class);
        final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
        final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
        final WmProductionRecptMapper receiptMapper = mock(WmProductionRecptMapper.class);
        final WmProductionRecptGuardMapper guardMapper = mock(WmProductionRecptGuardMapper.class);
        final WmProductionRecptWriteGuard guard = spy(new WmProductionRecptWriteGuard(guardMapper));
        final IWmBatchService batches = mock(IWmBatchService.class);
        final PrdWorkOrderServiceImpl orders = mock(PrdWorkOrderServiceImpl.class);
        final PrdBackflushServiceImpl backflush = mock(PrdBackflushServiceImpl.class);
        final ProductionCompletionPostingMapper completion =
                mock(ProductionCompletionPostingMapper.class);
        final PrdWorkOrder wo = new PrdWorkOrder();
        final WmProductionRecpt receipt = new WmProductionRecpt();
        final WmMaterialStock stock = new WmMaterialStock();
        final WmSerialInventoryRepository store;
        final WmBatchProductionReceiptProofService proof;
        final WmProductionRecptServiceImpl receiptService;
        final WmSerialProductionReceiptService service;
        List<CompletionManifestLine> lines =
                List.of(
                        new CompletionManifestLine(
                                1,
                                null,
                                null,
                                null,
                                50L,
                                501L,
                                20L,
                                new BigDecimal("2"),
                                "GOOD",
                                1),
                        new CompletionManifestLine(
                                2,
                                null,
                                null,
                                null,
                                50L,
                                502L,
                                20L,
                                new BigDecimal("3"),
                                "GOOD",
                                1));
        CompletionManifest manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        new BigDecimal("5"),
                        BigDecimal.ZERO,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");

        Fixture() {
            var ds =
                    new DriverManagerDataSource(
                            "jdbc:h2:mem:batchreceipt"
                                    + UUID.randomUUID()
                                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                            "sa",
                            "");
            jdbc = new JdbcTemplate(ds);
            tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            schema();
            var context = mock(ErpTraceContext.class);
            when(context.tenant()).thenReturn("t1");
            store =
                    spy(
                            new WmSerialInventoryRepository(
                                    jdbc, stockMapper, txMapper, context, new ObjectMapper()));
            doReturn(new TrackingPolicy(true, false, false, 7L, TrackingStage.STRICT))
                    .when(store)
                    .policy(20L);
            doReturn("a".repeat(64)).when(store).sourceHash(any());
            // 内存数据库不支持对聚合最大值加行锁，父行与过账组锁由实际写入器验证；
            // 此传输替身仍在同一本地事务中保存完整的冻结事件。
            doAnswer(
                            a -> {
                                String text = a.getArgument(5);
                                jdbc.update(
                                        "INSERT INTO"
                                            + " wm_trace_outbox(event_id,tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version,event_type,payload,content_hash,status)"
                                            + " VALUES(?,'t1','ERP',?,?,1,?,?,?,'NEW')",
                                        a.getArgument(0),
                                        a.getArgument(1),
                                        a.getArgument(2),
                                        a.getArgument(4),
                                        text,
                                        TraceHashes.sha256(text));
                                return null;
                            })
                    .when(store)
                    .appendEvent(
                            anyString(),
                            anyString(),
                            anyLong(),
                            anyLong(),
                            anyString(),
                            anyString());
            proof = new WmBatchProductionReceiptProofService(store, receiptMapper);
            ReflectionTestUtils.setField(guard, "batchProof", proof);
            doReturn("t1").when(guard).tenant();
            wo.setWoId(1L);
            wo.setWoNo("WO1");
            wo.setMaterialId(20L);
            wo.setMaterialCode("M20");
            wo.setUnitId(26L);
            wo.setTenantId("t1");
            wo.setDelFlag("0");
            wo.setWoStatus("IN_PROGRESS");
            wo.setQualityStatus("QUALIFIED");
            wo.setPlannedQty(new BigDecimal("5"));
            wo.setCompletedQty(BigDecimal.ZERO);
            receipt.setRecptId(5L);
            receipt.setRecptCode("R5");
            receipt.setWoId(1L);
            receipt.setWoNo("WO1");
            receipt.setMaterialId(20L);
            receipt.setMaterialCode("M20");
            receipt.setQuantity(new BigDecimal("5"));
            receipt.setWarehouseId(30L);
            receipt.setLocationId(40L);
            receipt.setTenantId("t1");
            receipt.setDelFlag("0");
            receipt.setStatus("DRAFT");
            receipt.setRecptDate(new Date());
            stock.setStockId(101L);
            stock.setMaterialId(20L);
            stock.setMaterialCode("M20");
            stock.setBaseUomId("26");
            stock.setWoId(1L);
            stock.setTenantId("t1");
            stock.setDelFlag("0");
            stock.setWarehouseId(30L);
            stock.setLocationId(40L);
            stock.setBatchId(50L);
            stock.setBatchCode("B50");
            stock.setVersion(0);
            stock.setStockQuantity(BigDecimal.ZERO);
            stock.setUnitCost(new BigDecimal("0.333333"));
            stock.setTotalCost(BigDecimal.ZERO);
            when(guardMapper.databaseDate()).thenReturn(java.time.LocalDate.now().toString());
            when(guardMapper.findWorkOrder("t1", 5L)).thenReturn(1L);
            when(guardMapper.lockWorkOrder("t1", 1L)).thenReturn(wo);
            when(guardMapper.lockReceipts("t1", 1L)).thenAnswer(a -> List.of(receipt));
            when(guardMapper.lockTransactions("t1", 1L)).thenAnswer(a -> facts());
            when(guardMapper.approvedReportedQuantity("t1", 1L)).thenReturn(BigDecimal.ZERO);
            when(guardMapper.lockMaterial("t1", 20L))
                    .thenReturn(
                            new WmProductionRecptGuardMapper.MaterialSource(
                                    20L,
                                    "M20",
                                    "Product",
                                    null,
                                    "1",
                                    "0",
                                    26L,
                                    "piece",
                                    26L,
                                    1L,
                                    BigDecimal.ONE,
                                    4,
                                    "1",
                                    "0",
                                    "root-v1",
                                    new Date(),
                                    new BigDecimal("0.333333")));
            when(guardMapper.lockStorage("t1", 30L, 40L))
                    .thenReturn(
                            new WmProductionRecptGuardMapper.StorageSource(
                                    30L,
                                    "WH30",
                                    "Warehouse",
                                    "1",
                                    "0",
                                    9L,
                                    1L,
                                    0L,
                                    "0",
                                    40L,
                                    "L40",
                                    "Location",
                                    1L,
                                    0L,
                                    "0"));
            when(guardMapper.lockStock("t1", 101L)).thenAnswer(a -> stock);
            when(guardMapper.lockBatch("t1", 50L))
                    .thenAnswer(
                            a -> {
                                var b = new WmBatch();
                                b.setBatchId(50L);
                                b.setBatchCode("B50");
                                b.setMaterialId(20L);
                                b.setWarehouseId(30L);
                                b.setTenantId("t1");
                                b.setDelFlag("0");
                                b.setStatus("ACTIVE");
                                b.setSourceDocType("PRD_WORK_ORDER");
                                b.setSourceDocId(1L);
                                b.setSourceDocNo("WO1");
                                b.setReceivedQty(
                                        jdbc.queryForObject(
                                                "SELECT received_qty FROM wm_batch",
                                                BigDecimal.class));
                                b.setIssuedQty(BigDecimal.ZERO);
                                return b;
                            });
            when(receiptMapper.selectById(5L)).thenReturn(receipt);
            when(receiptMapper.update(nullable(WmProductionRecpt.class), any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenAnswer(a -> stock);
            when(stockMapper.selectById(101L)).thenAnswer(a -> stock);
            when(stockMapper.addStock(eq(101L), any(), eq(0)))
                    .thenAnswer(
                            a -> {
                                stock.setStockQuantity(
                                        stock.getStockQuantity().add(a.getArgument(1)));
                                return 1;
                            });
            when(batchMapper.increaseProductionReceivedQty(
                            eq(50L), eq(20L), eq("t1"), eq(1L), any()))
                    .thenAnswer(
                            a ->
                                    jdbc.update(
                                            "UPDATE wm_batch SET received_qty=received_qty+? WHERE"
                                                    + " batch_id=50",
                                            a.getArgument(4, BigDecimal.class)));
            when(txMapper.selectCount(any()))
                    .thenAnswer(
                            a ->
                                    jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM wm_material_stock_tx WHERE"
                                                    + " source_doc_type='PRD_WORK_ORDER' AND"
                                                    + " source_doc_id=1",
                                            Long.class));
            when(txMapper.insert(any(WmMaterialStockTx.class)))
                    .thenAnswer(
                            a -> {
                                var fact = a.getArgument(0, WmMaterialStockTx.class);
                                fact.setTxId(501L);
                                fact.setTenantId("t1");
                                fact.setDelFlag("0");
                                fact.setTrackingVersion(1L);
                                jdbc.update(
                                        "INSERT INTO"
                                            + " wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,tx_flag,material_stock_id,material_id,material_code,base_uom_id,warehouse_id,location_id,batch_id,batch_code,tx_quantity,before_quantity,after_quantity,unit_cost,tx_amount,tx_date,tracking_version,del_flag)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                        fact.getTxId(),
                                        fact.getTenantId(),
                                        fact.getSourceDocType(),
                                        fact.getSourceDocId(),
                                        fact.getTxType(),
                                        fact.getTxFlag(),
                                        fact.getMaterialStockId(),
                                        fact.getMaterialId(),
                                        fact.getMaterialCode(),
                                        fact.getBaseUomId(),
                                        fact.getWarehouseId(),
                                        fact.getLocationId(),
                                        fact.getBatchId(),
                                        fact.getBatchCode(),
                                        fact.getTxQuantity(),
                                        fact.getBeforeQuantity(),
                                        fact.getAfterQuantity(),
                                        fact.getUnitCost(),
                                        fact.getTxAmount(),
                                        fact.getTxDate(),
                                        fact.getTrackingVersion(),
                                        fact.getDelFlag());
                                return 1;
                            });
            var strategy =
                    new ProductionCompletionStrategy(
                            txMapper,
                            stockMapper,
                            mock(com.nexus.erp.bd.mapper.BdMaterialMapper.class),
                            mock(WmWarehouseMapper.class),
                            mock(WmStorageLocationMapper.class),
                            mock(WmStorageAreaMapper.class),
                            mock(com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper.class),
                            batchMapper);
            ReflectionTestUtils.setField(
                    strategy, "serialInventory", new WmSerialInventoryService(store));
            var operations = mock(IWmStockOperationService.class);
            doAnswer(
                            a -> {
                                strategy.execute(a.getArguments());
                                return null;
                            })
                    .when(operations)
                    .postProductionCompletion(
                            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                            any());
            receiptService =
                    new WmProductionRecptServiceImpl(
                            receiptMapper, operations, mock(AutoCodeUtil.class), batches, guard);
            var serialProof = mock(WmSerialInventoryProofService.class);
            service =
                    new WmSerialProductionReceiptService(
                            store,
                            new WmSerialInventoryService(store),
                            serialProof,
                            guard,
                            receiptService,
                            receiptMapper,
                            batches,
                            orders,
                            backflush,
                            completion,
                            mock(IPutawayStrategyService.class));
            ReflectionTestUtils.setField(service, "batchProof", proof);
            var consumption = mock(WmTraceMaterialConsumptionService.class);
            ReflectionTestUtils.setField(service, "actualConsumption", consumption);
            when(consumption.consume(any(), any())).thenReturn(Set.of());
            when(completion.selectApprovedReportedQty("t1", 1L)).thenReturn(BigDecimal.ZERO);
            doAnswer(
                            a -> {
                                completeWorkOrder();
                                return null;
                            })
                    .when(orders)
                    .completeFromManifest(any());
        }

        void completeWorkOrder() {
            wo.setWoStatus("COMPLETED");
            wo.setCompletedQty(new BigDecimal("5"));
            wo.setActualEndDate(new Date());
        }

        WmSerialProductionReceiptService.Result post() {
            return tx.execute(s -> service.postManifest(manifest, lines));
        }

        List<WmMaterialStockTx> facts() {
            return jdbc.query(
                    "SELECT * FROM wm_material_stock_tx WHERE source_doc_type='PRD_WORK_ORDER' AND"
                            + " source_doc_id=1 ORDER BY tx_id",
                    BeanPropertyRowMapper.newInstance(WmMaterialStockTx.class));
        }

        void schema() {
            jdbc.execute(
                    "CREATE TABLE wm_trace_posting_group(posting_group_id BIGINT PRIMARY"
                            + " KEY,tenant_id VARCHAR(20),command_id VARCHAR(128),source_doc_type"
                            + " VARCHAR(64),source_doc_id BIGINT,source_doc_line_id"
                            + " BIGINT,detail_version BIGINT,tx_type VARCHAR(64),material_id"
                            + " BIGINT,valuation_group_id BIGINT,quantity DECIMAL(18,4),amount"
                            + " DECIMAL(18,4),fingerprint VARCHAR(64),content_hash"
                            + " VARCHAR(64),source_hash VARCHAR(64),policy_version BIGINT,status"
                            + " VARCHAR(24))");
            jdbc.execute(
                    "CREATE TABLE wm_material_stock_tx(tx_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),source_doc_type VARCHAR(64),source_doc_id"
                        + " BIGINT,source_doc_line_id BIGINT,tx_type VARCHAR(64),tx_flag"
                        + " INT,material_stock_id BIGINT,material_id BIGINT,material_code"
                        + " VARCHAR(64),base_uom_id VARCHAR(64),warehouse_id BIGINT,area_id"
                        + " BIGINT,location_id BIGINT,batch_id BIGINT,batch_code VARCHAR(64),sn_id"
                        + " BIGINT,tx_quantity DECIMAL(18,4),before_quantity"
                        + " DECIMAL(18,4),after_quantity DECIMAL(18,4),unit_cost"
                        + " DECIMAL(18,6),tx_amount DECIMAL(18,4),tx_date"
                        + " TIMESTAMP,tracking_version BIGINT,posting_group_id BIGINT,del_flag"
                        + " VARCHAR(1))");
            jdbc.execute(
                    "CREATE TABLE wm_batch(batch_id BIGINT PRIMARY KEY,batch_code"
                        + " VARCHAR(64),tenant_id VARCHAR(20),source_doc_type"
                        + " VARCHAR(64),source_doc_id BIGINT,source_doc_no VARCHAR(64),material_id"
                        + " BIGINT,warehouse_id BIGINT,status VARCHAR(24),received_qty"
                        + " DECIMAL(18,4),issued_qty DECIMAL(18,4),del_flag VARCHAR(1))");
            jdbc.execute(
                    "CREATE TABLE wm_trace_outbox(event_id VARCHAR(64) PRIMARY KEY,tenant_id"
                            + " VARCHAR(20),source_module VARCHAR(16),aggregate_type"
                            + " VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,event_type"
                            + " VARCHAR(64),payload VARCHAR(10000),content_hash VARCHAR(64),status"
                            + " VARCHAR(24))");
            jdbc.update(
                    "INSERT INTO wm_batch"
                        + " VALUES(50,'B50','t1','PRD_WORK_ORDER',1,'WO1',20,30,'ACTIVE',0,0,'0')");
            String payload =
                    "{\"erpWoId\":\"1\",\"mesWoId\":\"2\",\"warehouseId\":\"30\",\"locationId\":\"40\"}";
            jdbc.update(
                    "INSERT INTO wm_trace_outbox"
                        + " VALUES('ERP-LOT-50','t1','ERP','PRODUCTION_LOT',50,1,'ERP_PRODUCTION_LOT_AUTHORIZED',?,?,"
                        + " 'NEW')",
                    payload,
                    TraceHashes.sha256(payload));
        }
    }
}
