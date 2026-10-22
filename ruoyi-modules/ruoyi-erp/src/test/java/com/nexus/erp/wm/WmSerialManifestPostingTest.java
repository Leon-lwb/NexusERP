package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.ProductionManifestPostingPort;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.*;
import com.nexus.erp.prd.service.impl.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.strategy.impl.ProductionCompletionStrategy;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class WmSerialManifestPostingTest {
    WmSerialInventoryRepository store;
    WmSerialInventoryService inventory;
    WmSerialInventoryProofService proof;
    WmProductionRecptWriteGuard guard;
    IWmProductionRecptService receipts;
    WmProductionRecptMapper receiptMapper;
    IWmBatchService batches;
    PrdWorkOrderServiceImpl orders;
    PrdBackflushServiceImpl backflush;
    ProductionCompletionPostingMapper source;
    IPutawayStrategyService putaway;
    WmSerialProductionReceiptService service;
    List<CompletionManifestLine> lines;
    CompletionManifest manifest;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WmProductionRecpt.class);
    }

    @BeforeEach
    void setup() {
        store = mock(WmSerialInventoryRepository.class);
        inventory = mock(WmSerialInventoryService.class);
        proof = mock(WmSerialInventoryProofService.class);
        guard = mock(WmProductionRecptWriteGuard.class);
        receipts = mock(IWmProductionRecptService.class);
        receiptMapper = mock(WmProductionRecptMapper.class);
        batches = mock(IWmBatchService.class);
        orders = mock(PrdWorkOrderServiceImpl.class);
        backflush = mock(PrdBackflushServiceImpl.class);
        source = mock(ProductionCompletionPostingMapper.class);
        putaway = mock(IPutawayStrategyService.class);
        service =
                new WmSerialProductionReceiptService(
                        store,
                        inventory,
                        proof,
                        guard,
                        receipts,
                        receiptMapper,
                        batches,
                        orders,
                        backflush,
                        source,
                        putaway);
        lines =
                List.of(
                        new CompletionManifestLine(
                                1, 101L, 201L, "SN101", 50L, null, 20L, BigDecimal.ONE, "GOOD", 3),
                        new CompletionManifestLine(
                                2,
                                102L,
                                202L,
                                "SN102",
                                50L,
                                null,
                                20L,
                                BigDecimal.ONE,
                                "SCRAP",
                                4));
        manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");
        when(store.tenant()).thenReturn("t1");
    }

    @Test
    void portIsImplementedAndCompleteFrozenListIsRequired() {
        assertInstanceOf(ProductionManifestPostingPort.class, service);
        assertThrows(ServiceException.class, () -> service.post(manifest, List.of(lines.get(0))));
        verifyNoInteractions(orders, backflush, receipts);
    }

    @Test
    void wrongTenantCannotCreateAnErpCompletionFact() {
        when(store.tenant()).thenReturn("t2");
        assertThrows(ServiceException.class, () -> service.post(manifest, lines));
        verifyNoInteractions(guard, orders, backflush, receipts);
    }

    @Test
    void finalPostingCompletesThenBackflushesThenPostsOneGoodReceipt() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        wo.setWoNo("WO1");
        wo.setMaterialCode("M20");
        wo.setCompletedQty(BigDecimal.ZERO);
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(guard.receipts(wo)).thenReturn(List.of());
        when(guard.readyQuantity(wo)).thenReturn(BigDecimal.ONE);
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT));
        when(source.selectApprovedReportedQty("t1", 1L)).thenReturn(BigDecimal.ZERO);
        PutawayLocation target = new PutawayLocation();
        target.setWarehouseId(30L);
        target.setLocationId(40L);
        when(putaway.resolvePutaway(20L, BigDecimal.ONE)).thenReturn(target);
        when(store.productionLotTarget(1L, 2L, 50L)).thenReturn(target);
        WmProductionRecpt receipt = new WmProductionRecpt();
        receipt.setRecptId(5L);
        receipt.setWoId(1L);
        receipt.setMaterialId(20L);
        receipt.setWarehouseId(30L);
        receipt.setLocationId(40L);
        receipt.setStatus("DRAFT");
        receipt.setQuantity(BigDecimal.ONE);
        when(receipts.insertByBo(any()))
                .thenAnswer(
                        a -> {
                            ((WmProductionRecptBo) a.getArgument(0)).setRecptId(5L);
                            return 1;
                        });
        when(guard.lockReceipt(5L))
                .thenReturn(new WmProductionRecptWriteGuard.LockedReceipt(wo, receipt));
        WmBatchVo batch = new WmBatchVo();
        batch.setBatchId(50L);
        batch.setBatchCode("B50");
        when(batches.createBatchByMaterial(
                        eq(20L), eq("PRD_WORK_ORDER"), eq(1L), eq("WO1"), eq(30L), isNull()))
                .thenReturn(batch);
        when(store.authorizedProductionBatchCode(1L, 2L, 50L, 30L)).thenReturn("B50");
        when(receiptMapper.update(isNull(), any())).thenReturn(1);
        when(inventory.freeze(any(), anyString(), eq(1L), eq(7L), anyList())).thenReturn(100L);
        when(proof.verifyPosted("PRD_WORK_ORDER", 1L, null, "PRODUCTION_IN")).thenReturn(100L);
        var jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(store.identity(102L))
                .thenReturn(
                        new WmSerialInventoryRepository.Identity(
                                102L, 20L, 50L, "SN102", "REGISTERED"));
        when(store.stocks(102L)).thenReturn(List.of());
        ReflectionTestUtils.setField(
                service,
                "noGoodOutput",
                new WmTraceNoGoodOutputService(
                        store, guard, mock(WmTraceMaterialConsumptionService.class), orders));
        CompletionReceipt result = service.post(manifest, lines);
        verify(store).identity(102L);
        verify(jdbc).update(contains("status='SCRAPPED'"), any(Object[].class));
        assertEquals(5L, result.receiptId());
        assertEquals(100L, result.postingGroupId());
        assertEquals(manifest.contentHash(), result.manifestHash());
        var order = inOrder(orders, backflush, inventory, receipts);
        order.verify(orders).completeFromManifest(manifest);
        order.verify(backflush).backflushOnCompletion(1L, BigDecimal.ONE);
        order.verify(receipts).insertByBo(any());
        order.verify(inventory)
                .freeze(
                        any(),
                        anyString(),
                        eq(1L),
                        eq(7L),
                        argThat(items -> items.size() == 1 && items.get(0).identityId() == 101L));
        order.verify(receipts).execute(5L);
        verify(orders, never()).complete(anyLong(), any(), any());
    }

    @Test
    void realManifestCompleteAdapterRetainsPlanGateAndDoesNotPublishOldMq() {
        var real = mock(PrdWorkOrderServiceImpl.class, CALLS_REAL_METHODS);
        var headers = mock(PrdWorkOrderMapper.class);
        var mapper = mock(ProductionCompletionPostingMapper.class);
        var mq = mock(ReliableMessageProducer.class);
        ReflectionTestUtils.setField(real, "baseMapper", headers);
        ReflectionTestUtils.setField(real, "manifestSourceMapper", mapper);
        ReflectionTestUtils.setField(real, "messageProducer", mq);
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        wo.setWoStatus("IN_PROGRESS");
        wo.setPlannedQty(new BigDecimal("2"));
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        when(mapper.lockWorkOrder("t1", 1L)).thenReturn(wo);
        when(headers.update(isNull(), any())).thenReturn(1);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("t1");
            real.completeFromManifest(manifest);
            verify(headers).update(isNull(), any());
            verifyNoInteractions(mq);
        }
    }

    @Test
    void realManifestCompleteAdapterRejectsOverOriginalPlan() {
        var real = mock(PrdWorkOrderServiceImpl.class, CALLS_REAL_METHODS);
        var headers = mock(PrdWorkOrderMapper.class);
        var mapper = mock(ProductionCompletionPostingMapper.class);
        ReflectionTestUtils.setField(real, "baseMapper", headers);
        ReflectionTestUtils.setField(real, "manifestSourceMapper", mapper);
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        wo.setPlannedQty(BigDecimal.ONE);
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        when(mapper.lockWorkOrder("t1", 1L)).thenReturn(wo);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("t1");
            assertThrows(ServiceException.class, () -> real.completeFromManifest(manifest));
            verifyNoInteractions(headers);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "RECEIVED"})
    void immutableTerminalManifestReplayVerifiesOriginalGroupAndNeverCreditsMovedStock(
            String state) {
        manifest =
                new CompletionManifest(
                        manifest.manifestId(),
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        manifest.contentHash(),
                        state);
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        WmProductionRecpt original = new WmProductionRecpt();
        original.setRecptId(5L);
        original.setWoId(1L);
        original.setStatus("EXECUTED");
        original.setWarehouseId(30L);
        original.setLocationId(40L);
        original.setBatchId(50L);
        original.setQuantity(BigDecimal.ONE);
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(guard.receipts(wo)).thenReturn(List.of(original));
        when(guard.readyQuantity(wo)).thenReturn(BigDecimal.ONE);
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT));
        String command = "MES_FINAL/900/1/" + manifest.contentHash();
        when(store.group(any()))
                .thenReturn(
                        new WmSerialInventoryRepository.Group(
                                100L,
                                command,
                                1,
                                "fp",
                                "hash",
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                "POSTED",
                                200L));
        when(proof.verifyPosted("PRD_WORK_ORDER", 1L, null, "PRODUCTION_IN")).thenReturn(100L);
        CompletionReceipt result = service.post(manifest, lines);
        assertEquals(5L, result.receiptId());
        verify(guard).verifyExecuted(wo, BigDecimal.ONE, original);
        verifyNoInteractions(inventory, receipts, orders, backflush);
        verify(store, never()).stock(any());
    }

    @Test
    void strictBatchOnlyCompletionReceivesExactlyThePreviouslyAuthorizedBatch() {
        var wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setWoNo("WO1");
        wo.setMaterialId(20L);
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(guard.readyQuantity(wo)).thenReturn(BigDecimal.ONE);
        when(guard.receipts(wo)).thenReturn(List.of());
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, false, true, 7L, TrackingStage.STRICT));
        var receipt = new WmProductionRecpt();
        receipt.setRecptId(5L);
        receipt.setWoId(1L);
        receipt.setMaterialId(20L);
        receipt.setWarehouseId(30L);
        receipt.setLocationId(40L);
        receipt.setStatus("DRAFT");
        receipt.setQuantity(BigDecimal.ONE);
        when(receipts.insertByBo(any()))
                .thenAnswer(
                        a -> {
                            ((WmProductionRecptBo) a.getArgument(0)).setRecptId(5L);
                            return 1;
                        });
        when(guard.lockReceipt(5L))
                .thenReturn(new WmProductionRecptWriteGuard.LockedReceipt(wo, receipt));
        when(store.authorizedProductionBatchCode(1L, 2L, 50L, 30L)).thenReturn("B50");
        when(receiptMapper.update(isNull(), any())).thenReturn(1);
        lines =
                List.of(
                        new CompletionManifestLine(
                                1, null, null, null, 50L, 501L, 20L, BigDecimal.ONE, "GOOD", 1),
                        new CompletionManifestLine(
                                2, null, null, null, 50L, 502L, 20L, BigDecimal.ONE, "SCRAP", 1));
        manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        2,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");
        assertEquals(5L, service.postFinalCompletion(manifest, lines, 30L, 40L, 7));
        assertEquals(50L, receipt.getBatchId());
        verify(store, atLeastOnce()).authorizedProductionBatchCode(1L, 2L, 50L, 30L);
        verify(receipts).execute(5L);
        verifyNoInteractions(batches, inventory);
        verify(receipts, never())
                .createAndExecute(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any());
    }

    @Test
    void batchAndSerialManifestUsesEveryFrozenBatchAndOriginalPoolsWithMixedLotDisabled() {
        var actualInventory = new WmSerialInventoryService(store);
        service =
                new WmSerialProductionReceiptService(
                        store,
                        actualInventory,
                        proof,
                        guard,
                        receipts,
                        receiptMapper,
                        batches,
                        orders,
                        backflush,
                        source,
                        putaway);
        var consumption = mock(WmTraceMaterialConsumptionService.class);
        ReflectionTestUtils.setField(service, "actualConsumption", consumption);
        when(consumption.consume(any(), any())).thenReturn(Set.of());
        lines =
                List.of(
                        new CompletionManifestLine(
                                1, 103L, 203L, "SN103", 51L, null, 20L, BigDecimal.ONE, "GOOD", 3),
                        new CompletionManifestLine(
                                2, 102L, 202L, "SN102", 50L, null, 20L, BigDecimal.ONE, "GOOD", 3),
                        new CompletionManifestLine(
                                3, 101L, 201L, "SN101", 50L, null, 20L, BigDecimal.ONE, "GOOD", 3));
        manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        3,
                        new BigDecimal("3"),
                        BigDecimal.ZERO,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");
        var wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        wo.setWoNo("WO1");
        wo.setMaterialCode("M20");
        wo.setCompletedQty(BigDecimal.ZERO);
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(guard.readyQuantity(wo)).thenReturn(new BigDecimal("3"));
        when(guard.receipts(wo)).thenReturn(List.of());
        when(source.selectApprovedReportedQty("t1", 1L)).thenReturn(BigDecimal.ZERO);
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, true, false, 7L, TrackingStage.STRICT));
        var target = new PutawayLocation();
        target.setWarehouseId(30L);
        target.setLocationId(40L);
        when(store.productionLotTarget(1L, 2L, 51L)).thenReturn(target);
        var receipt = new WmProductionRecpt();
        receipt.setRecptId(5L);
        receipt.setWoId(1L);
        receipt.setMaterialId(20L);
        receipt.setQuantity(new BigDecimal("3"));
        receipt.setWarehouseId(30L);
        receipt.setLocationId(40L);
        receipt.setStatus("DRAFT");
        when(receipts.insertByBo(any()))
                .thenAnswer(
                        a -> {
                            ((WmProductionRecptBo) a.getArgument(0)).setRecptId(5L);
                            return 1;
                        });
        when(guard.lockReceipt(5L))
                .thenReturn(new WmProductionRecptWriteGuard.LockedReceipt(wo, receipt));
        when(receiptMapper.update(isNull(), any())).thenReturn(1);
        when(store.authorizedProductionBatchCode(eq(1L), eq(2L), anyLong(), eq(30L)))
                .thenAnswer(a -> "B" + a.getArgument(2));
        var frozen = new AtomicReference<List<SnItem>>();
        var group = new AtomicReference<WmSerialInventoryRepository.Group>();
        when(store.group(any())).thenAnswer(a -> group.get());
        when(store.items(100L)).thenAnswer(a -> frozen.get());
        when(store.prepare(any(), anyString(), anyLong(), anyString(), anyString(), anyList()))
                .thenAnswer(
                        a -> {
                            var request = a.getArgument(0, WmSerialPostingRequest.class);
                            frozen.set(a.getArgument(5));
                            group.set(
                                    new WmSerialInventoryRepository.Group(
                                            100L,
                                            a.getArgument(1),
                                            a.getArgument(2),
                                            a.getArgument(3),
                                            a.getArgument(4),
                                            request.quantity(),
                                            BigDecimal.ZERO,
                                            "PREPARED",
                                            null));
                            return 100L;
                        });
        List<WmMaterialStockTx> facts = new ArrayList<>();
        doAnswer(
                        a -> {
                            facts.add(a.getArgument(0));
                            return null;
                        })
                .when(store)
                .insertTx(any());
        for (var line : lines) {
            var identity =
                    new WmSerialInventoryRepository.Identity(
                            line.identityId(), 20L, line.batchId(), line.snCode(), "REGISTERED");
            when(store.identity(line.identityId())).thenReturn(identity);
            when(store.stocks(line.identityId())).thenReturn(List.of());
            var stock = new WmMaterialStock();
            stock.setStockId(line.identityId() + 1000);
            stock.setMaterialId(20L);
            stock.setMaterialCode("M20");
            stock.setTenantId("t1");
            stock.setSnId(line.identityId());
            stock.setSnCode(line.snCode());
            stock.setBatchId(line.batchId());
            stock.setWarehouseId(30L);
            stock.setLocationId(40L);
            stock.setStockQuantity(BigDecimal.ONE);
            stock.setReservedQuantity(BigDecimal.ZERO);
            stock.setTrackingVersion(2L);
            stock.setVersion(0);
            when(store.inboundStock(
                            any(),
                            argThat(
                                    (SnItem item) ->
                                            item != null
                                                    && item.identityId().equals(line.identityId())),
                            eq(identity)))
                    .thenReturn(stock);
        }
        when(store.inboundCost(any(), any())).thenReturn(new BigDecimal("0.333333"));
        when(store.inboundPool(any(), eq(7L)))
                .thenAnswer(
                        a ->
                                a.getArgument(0, WmMaterialStock.class).getBatchId() == 50L
                                        ? 200L
                                        : 201L);
        for (long pool : List.of(200L, 201L)) {
            when(store.pool(pool))
                    .thenReturn(
                            new WmSerialInventoryRepository.Pool(
                                    pool,
                                    20L,
                                    30L,
                                    pool == 200L ? 50L : 51L,
                                    null,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO));
            when(store.poolStocks(pool)).thenReturn(List.of());
        }
        var stockMapper = mock(WmMaterialStockMapper.class);
        var txMapper = mock(WmMaterialStockTxMapper.class);
        var batchMapper = mock(WmBatchMapper.class);
        var strategy =
                new ProductionCompletionStrategy(
                        txMapper,
                        stockMapper,
                        mock(BdMaterialMapper.class),
                        mock(WmWarehouseMapper.class),
                        mock(WmStorageLocationMapper.class),
                        mock(WmStorageAreaMapper.class),
                        mock(BdUnitOfMeasureMapper.class),
                        batchMapper);
        ReflectionTestUtils.setField(strategy, "serialInventory", actualInventory);
        doAnswer(
                        a -> {
                            strategy.execute(
                                    1L,
                                    "WO1",
                                    20L,
                                    "M20",
                                    "Product",
                                    new BigDecimal("3"),
                                    30L,
                                    40L,
                                    receipt.getBatchId(),
                                    receipt.getBatchCode(),
                                    new Date());
                            return null;
                        })
                .when(receipts)
                .execute(5L);
        when(proof.verifyPosted("PRD_WORK_ORDER", 1L, null, "PRODUCTION_IN")).thenReturn(100L);
        var result = service.postManifest(manifest, lines);
        assertEquals(5L, result.receiptId());
        assertEquals(100L, result.postingGroupId());
        assertEquals(
                Set.of(50L, 51L),
                new HashSet<>(frozen.get().stream().map(SnItem::batchId).toList()));
        assertEquals(3, facts.size());
        assertEquals(
                new BigDecimal("1.0000"),
                facts.stream()
                        .map(WmMaterialStockTx::getTxAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(
                new BigDecimal("0.3334"),
                facts.stream()
                        .filter(t -> t.getSnId() == 101L)
                        .findFirst()
                        .orElseThrow()
                        .getTxAmount());
        assertTrue(
                facts.stream()
                        .allMatch(
                                t ->
                                        BigDecimal.ONE.equals(t.getTxQuantity())
                                                && Long.valueOf(100L).equals(t.getPostingGroupId())
                                                && Long.valueOf(2L)
                                                        .equals(t.getTrackingVersion())));
        verify(store).finish(100L, new BigDecimal("1.0000"), null);
        verify(store).enqueue(eq(100L), anyString(), anyString());
        verifyNoInteractions(batches, stockMapper, txMapper, batchMapper);
    }

    void batchOnlySubLots(long secondBatch) {
        var batchProof = mock(WmBatchProductionReceiptProofService.class);
        ReflectionTestUtils.setField(service, "batchProof", batchProof);
        when(batchProof.record(any(), any(), any(), any(), anyLong())).thenReturn(100L);
        lines =
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
                                secondBatch,
                                502L,
                                20L,
                                new BigDecimal("3"),
                                "GOOD",
                                1));
        manifest =
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
        var wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setWoNo("WO1");
        wo.setMaterialId(20L);
        wo.setMaterialCode("M20");
        wo.setCompletedQty(BigDecimal.ZERO);
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(guard.readyQuantity(wo)).thenReturn(new BigDecimal("5"));
        when(guard.receipts(wo)).thenReturn(List.of());
        when(source.selectApprovedReportedQty("t1", 1L)).thenReturn(BigDecimal.ZERO);
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, false, true, 7L, TrackingStage.STRICT));
        var target = new PutawayLocation();
        target.setWarehouseId(30L);
        target.setLocationId(40L);
        when(store.productionLotTarget(1L, 2L, 50L)).thenReturn(target);
        var receipt = new WmProductionRecpt();
        receipt.setRecptId(5L);
        receipt.setWoId(1L);
        receipt.setMaterialId(20L);
        receipt.setWarehouseId(30L);
        receipt.setLocationId(40L);
        receipt.setStatus("DRAFT");
        receipt.setQuantity(new BigDecimal("5"));
        when(receipts.insertByBo(any()))
                .thenAnswer(
                        a -> {
                            ((WmProductionRecptBo) a.getArgument(0)).setRecptId(5L);
                            return 1;
                        });
        when(guard.lockReceipt(5L))
                .thenReturn(new WmProductionRecptWriteGuard.LockedReceipt(wo, receipt));
        when(store.authorizedProductionBatchCode(1L, 2L, 50L, 30L)).thenReturn("B50");
        when(receiptMapper.update(isNull(), any())).thenReturn(1);
        when(store.recordLegacyManifest(any(), any(), anyLong())).thenReturn(100L);
    }

    @Test
    void batchOnlyFinalManifestRequiresItsOwnCompletePostingProof() {
        batchOnlySubLots(50);
        service.post(manifest, lines);
        verify(store, never()).recordLegacyManifest(any(), any(), anyLong());
        verifyNoInteractions(batches, inventory);
        verify(receipts).execute(5L);
    }

    @Test
    void multipleErpBatchesAreRejectedBeforeCompletingTheSingleBatchSource() {
        batchOnlySubLots(51);
        assertThrows(ServiceException.class, () -> service.post(manifest, lines));
        verifyNoInteractions(orders, backflush);
        verify(receipts, never()).insertByBo(any());
    }

    @Test
    void allScrapReturnsNoGoodOutputWithoutFakeReceipt() {
        var scrap =
                List.of(
                        new CompletionManifestLine(
                                1,
                                101L,
                                201L,
                                "SN101",
                                50L,
                                null,
                                20L,
                                BigDecimal.ONE,
                                "SCRAP",
                                3));
        var header =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        CompletionManifestIntegrity.hash(scrap),
                        "FROZEN");
        var noGood = mock(WmTraceNoGoodOutputService.class);
        ReflectionTestUtils.setField(service, "noGoodOutput", noGood);
        when(noGood.post(header, scrap, List.of()))
                .thenReturn(new WmTraceNoGoodOutputService.Result("ERP-NO-GOOD-900-1", false));
        CompletionReceipt receipt = service.post(header, scrap);
        assertNull(receipt.receiptId());
        assertNull(receipt.postingGroupId());
        verify(noGood).post(header, scrap, List.of());
        verifyNoInteractions(guard, receipts, receiptMapper, inventory, backflush, orders);
    }
}
