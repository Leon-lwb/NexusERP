package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier.InventorySnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseStockLineageVerifier.StockLineageSnapshot;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mapper doubles are unavoidable: no external databases or services are accessed. */
class ErpPurchaseStockLineageVerifierTest {
    private static final String TENANT = "000000";
    private static final Long MATERIAL = 140L;
    private static final Long UNIT = 27L;
    private static final Instant NOW = Instant.parse("2026-10-02T03:00:00Z");
    private static final String ERROR = "采购库存流水来源未核验：";

    private static BigDecimal q(String value) { return new BigDecimal(value).setScale(4); }
    private static BigDecimal time(long seconds) { return BigDecimal.valueOf(NOW.plusSeconds(seconds).toEpochMilli()); }

    @Test
    void nonEmptyPathCannotHideAnotherTenantDeletedStockFromGlobalMaterialCoverage() {
        Fixture f=new Fixture();
        var all=mock(com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.class);
        when(all.lockAllStocks(MATERIAL)).thenReturn(List.of(91L,55L));
        when(all.lockAllTransactions(MATERIAL)).thenReturn(List.of(100L,101L,102L));
        f.coverage=new ErpCertifiedSourceBirthReader(all,f.qualification);
        assertThrows(ServiceException.class,f::read,"另一租户或删除库存不能因当前租户过滤而消失");
        verify(all,never()).lockBirth(anyString(),anyLong());
    }

    @Test
    void nonEmptyPathCannotHideAnotherTenantOrphanTransactionFromGlobalMaterialCoverage() {
        Fixture f=new Fixture();
        var all=mock(com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.class);
        when(all.lockAllStocks(MATERIAL)).thenReturn(List.of(91L));
        when(all.lockAllTransactions(MATERIAL)).thenReturn(List.of(100L,101L,102L,999L));
        f.coverage=new ErpCertifiedSourceBirthReader(all,f.qualification);
        assertThrows(ServiceException.class,f::read,"同物料另一租户孤立流水不得隐藏或加入供给");
        verify(all,never()).lockBirth(anyString(),anyLong());
    }

    @Test
    void globallyCompleteNonEmptyOriginalSourcesDoNotRequireOldStockToHaveBirth() {
        Fixture f=new Fixture();
        var all=mock(com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.class);
        when(all.lockAllStocks(MATERIAL)).thenReturn(List.of(91L));
        when(all.lockAllTransactions(MATERIAL)).thenReturn(List.of(100L,101L,102L));
        f.coverage=new ErpCertifiedSourceBirthReader(all,f.qualification);
        assertEquals(q("10"),f.read().inventory().quantity());
        verify(all).lockAllStocks(MATERIAL);verify(all).lockAllTransactions(MATERIAL);
        verify(all,never()).lockBirth(anyString(),anyLong());verify(all,never()).pinRegistry();
    }

    @Test
    void attributesOwnReservationInsteadOfChargingItAsOtherDemand() {
        Fixture f = new Fixture();
        StockLineageSnapshot result = f.read();
        assertAll(
            () -> assertEquals(q("10"), result.inventory().quantity()),
            () -> assertEquals(q("4"), result.inventory().reserved()),
            () -> assertEquals(Map.of(701L, q("4")), result.reservedByRequirementDetail()),
            () -> assertEquals(Map.of(), result.frozenReservedByRequirementDetail()),
            () -> assertEquals(Set.of(100L, 101L, 102L), result.transactionIds()),
            () -> assertEquals(List.of(100L, 101L, 102L), result.stocks().get(0).transactions().stream().map(TransactionRow::txId).toList()),
            () -> assertEquals(NOW, result.inventory().snapshotAt()));
        assertTrue(result.sourceVersions().keySet().containsAll(Set.of(
            "wm_material_stock_tx:100", "wm_material_stock_tx:101", "wm_material_receive:501",
            "wm_material_receive_line:511", "wm_material_receive_detail:521", "wm_production_issue:601",
            "wm_production_issue_line:611", "prd_material_requirement_detail:701", "prd_material_requirement:711", "prd_work_order:801")));
        verify(f.inventory, times(2)).verifyLocked(TENANT, MATERIAL);
        verify(f.qualification).requireParticipatingWritableErpTransaction();
        verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L));
    }

    @Test
    void snapshotAndNestedRecordsAreImmutableAndDoNotRetainMapperCollections() {
        Fixture f = new Fixture(); StockLineageSnapshot result = f.read();
        assertThrows(UnsupportedOperationException.class, () -> result.reservedByRequirementDetail().put(9L, q("1")));
        assertThrows(UnsupportedOperationException.class, () -> result.frozenReservedByRequirementDetail().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.transactionIds().add(9L));
        assertThrows(UnsupportedOperationException.class, () -> result.stocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.stocks().get(0).transactions().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.stocks().get(0).reservedByRequirementDetail().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.sourceVersions().put("fake", "verified"));
        f.transactions.clear(); f.stock("stockQuantity", q("999"));
        assertEquals(q("10"), result.stocks().get(0).stock().stockQuantity());
        assertEquals(3, result.stocks().get(0).transactions().size());
    }

    @Test
    void explicitlyBalancedZeroIsNotMissingStockOrMissingHistory() {
        Fixture f = new Fixture();
        f.transactions.add(rowTx(103L, "PRODUCTION_ISSUE", -1, "10", "10", "0", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
        f.transactions.set(2, change(f.transactions.get(2), "txQuantity", q("6")));
        f.stock("stockQuantity", q("0")); f.stock("reservedQuantity", q("0"));
        f.issueLine("quantity", q("10")); f.issueLine("requiredQty", q("10"));
        f.issueLine("issuedQty", q("10")); f.issueLine("shortQty", q("0")); f.issueLine("lineStatus", "ISSUED");
        f.issueHead("totalQty", q("10")); f.issueHead("status", "COMPLETED");
        f.requirement("requiredQuantity", q("10")); f.requirement("issuedQuantity", q("10"));
        f.summary(q("0"), q("0"), q("0"));
        StockLineageSnapshot result = f.read();
        assertEquals(q("0"), result.inventory().quantity());
        assertEquals(Map.of(), result.reservedByRequirementDetail());
        assertEquals(Set.of(100L, 101L, 102L, 103L), result.transactionIds());
    }

    @Test
    void consumptionReconcilesIssuedMrpWithoutDoubleSubtractingStock() {
        Fixture f = new Fixture(); f.consumption(); StockLineageSnapshot result = f.read();
        assertEquals(q("7"), result.inventory().quantity());
        assertEquals(Map.of(701L, q("3")), result.reservedByRequirementDetail());
        assertEquals(q("3"), result.inventory().reserved());
        // Net need = 8 - 3 issued = 5, of which this exact MRP detail already owns 3.
        assertEquals(q("2"), q("8").subtract(q("3")).subtract(result.reservedByRequirementDetail().get(701L)));
    }

    @Test
    void physicalCompanionMayPrecedeItsAdjacentConsumptionRecord() {
        Fixture f = new Fixture(); f.consumption();
        TransactionRow consume = f.transactions.get(2), issue = f.transactions.get(3);
        f.transactions.set(2, change(issue, "txId", 103L));
        f.transactions.set(3, change(change(consume, "txId", 104L), "relatedTxId", 103L));
        assertEquals(Map.of(701L, q("3")), f.read().reservedByRequirementDetail());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("freezeSources")
    void frozenOriginalRowsAndTheirReservationsRemainAuditableButAreExcluded(String name, Consumer<Fixture> freeze) {
        Fixture f = new Fixture(); freeze.accept(f); f.summary(q("0"), q("0"), q("10"));
        StockLineageSnapshot result = f.read();
        assertEquals(Map.of(), result.reservedByRequirementDetail());
        assertEquals(Map.of(701L, q("4")), result.frozenReservedByRequirementDetail());
        assertTrue(result.stocks().get(0).frozen());
        assertEquals(3, result.stocks().get(0).transactions().size());
    }
    static Stream<Arguments> freezeSources() {
        return Stream.of(bad("stock", f -> f.stock("frozenFlag", 1L)),
            bad("area", f -> f.areas.set(0, change(f.areas.get(0), "frozenFlag", 1L))),
            bad("location", f -> f.locations.set(0, change(f.locations.get(0), "frozenFlag", 1L))));
    }

    @Test
    void clockRefreshAfterAllLocksRejectsExpiryDuringWait() {
        Fixture f = new Fixture();
        when(f.mapper.selectDatabaseClock()).thenReturn(new DatabaseClock(BigDecimal.valueOf(NOW.plusSeconds(20).getEpochSecond()), "+08:00"));
        InventorySnapshot initial = f.snapshot;
        InventorySnapshot refreshed = change(f.snapshot, "snapshotAt", NOW.plusSeconds(20));
        when(f.inventory.verifyLocked(TENANT, MATERIAL)).thenReturn(initial, refreshed, initial, refreshed);
        assertEquals(NOW.plusSeconds(20), f.read().inventory().snapshotAt());
        f.stock("expireAtEpochMillis", time(10)); assertReject(f);
    }

    @Test
    void terminalInventoryReadComesAfterEveryOriginalLockAndNeverReplacesOnlyClock() {
        Fixture f = new Fixture(); f.read();
        var order = inOrder(f.mapper, f.inventory);
        order.verify(f.mapper).pinTableDefinitions();
        order.verify(f.mapper).selectTableEngines();
        order.verify(f.inventory).verifyLocked(TENANT, MATERIAL);
        order.verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L));
        order.verify(f.mapper).lockReceiveHeads(TENANT, List.of(501L));
        order.verify(f.mapper).lockReceiveLines(TENANT, List.of(501L));
        order.verify(f.mapper).lockReceiveDetails(TENANT, List.of(501L));
        order.verify(f.mapper).lockIssueHeads(TENANT, List.of(601L));
        order.verify(f.mapper).lockIssueLines(TENANT, List.of(601L));
        order.verify(f.mapper).lockRequirementDetails(TENANT, List.of(701L));
        order.verify(f.mapper).lockRequirementHeads(TENANT, List.of(711L));
        order.verify(f.mapper).lockWorkOrders(TENANT, List.of(801L));
        order.verify(f.mapper).selectQuantityColumns();
        order.verify(f.mapper).selectDatabaseClock();
        order.verify(f.inventory).verifyLocked(TENANT, MATERIAL);
        order.verifyNoMoreInteractions();
        assertEquals(f.read().sourceVersions(), f.read().inventory().sourceVersions());
    }

    @ParameterizedTest(name = "terminal {0}")
    @MethodSource("changedTerminalFacts")
    void changedOrExpiredTerminalInventoryIsNotReauthorizedByOldStockSnapshot(String field, Object value) {
        Fixture f = new Fixture(); InventorySnapshot finalValue = change(f.snapshot, field, value);
        when(f.inventory.verifyLocked(TENANT, MATERIAL)).thenReturn(f.snapshot, finalValue);
        assertReject(f);
    }
    static Stream<Arguments> changedTerminalFacts() {
        return Stream.of(Arguments.of("quantity", q("11")), Arguments.of("reserved", q("5")),
            Arguments.of("safety", q("2")), Arguments.of("frozenQuantity", q("1")),
            Arguments.of("stockIds", Set.of(91L, 92L)), Arguments.of("sourceVersions", Map.of("fake", "verified")),
            Arguments.of("snapshotAt", NOW.minusSeconds(1)), Arguments.of("baseUomId", 99L));
    }

    @Test
    void finalInventoryFailureIncludingParentExpiryAbortsWholeLineage() {
        Fixture f = new Fixture();
        when(f.inventory.verifyLocked(TENANT, MATERIAL)).thenReturn(f.snapshot).thenThrow(new ServiceException("expired after waiting"));
        assertReject(f);
    }

    @Test
    void reservationsForTwoRealMrpOwnersRemainSeparateAndReleaseCannotBorrowTheOtherOwner() {
        Fixture f = new Fixture(); f.twoOwners();
        assertEquals(Map.of(701L, q("2"), 702L, q("2")), f.read().reservedByRequirementDetail());
        verify(f.mapper).lockIssueHeads(TENANT, List.of(601L, 602L));
        verify(f.mapper).lockRequirementDetails(TENANT, List.of(701L, 702L));
        f.transactions.set(2, change(f.transactions.get(2), "txQuantity", q("4")));
        f.transactions.add(rowTx(103L, "UNRESERVE", 0, "3", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
        f.stock("reservedQuantity", q("3")); f.summary(q("10"), q("3"), q("0"));
        assertReject(f); // Total 2 + 4 - 3 would match 3, but owner 701 only reserved 2.
    }

    @Test
    void splitReceiptAndReservationReconcileEveryStockAndKeepFrozenDemandSeparate() {
        Fixture f = new Fixture();
        f.stock("stockQuantity", q("5")); f.stock("reservedQuantity", q("2"));
        StockRow first = f.stocks.get(0);
        StockRow second = change(change(change(first, "stockId", 92L), "batchId", 82L), "frozenFlag", 1L);
        f.stocks.add(0, second); // SQL lock params must remain stable even if a mapper double returns shuffled rows.
        f.transactions.clear();
        f.transactions.add(rowTx(100L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600));
        f.transactions.add(change(rowTx(101L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600), "stockId", 92L));
        f.transactions.add(rowTx(102L, "RESERVE", 0, "2", "5", "5", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300));
        f.transactions.add(change(rowTx(103L, "RESERVE", 0, "2", "5", "5", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300), "stockId", 92L));
        f.receiveDetail("quantity", q("5"));
        f.receiveDetails.add(change(change(f.receiveDetails.get(0), "detailId", 522L), "batchId", 82L));
        f.issueLine("batchId", null); // Original issue line explicitly permits allocation across the two proven batches.
        f.snapshot = new InventorySnapshot(TENANT, MATERIAL, UNIT, 4, NOW, "+08:00", q("5"), q("2"), q("1"), q("5"), Set.of(91L, 92L),
            Map.of("wm_material_stock:91", "version=2;digest1", "wm_material_stock:92", "version=2;digest2", "bd_unit_of_measure:27", "conversionVersion=unit-v1;digest"));
        StockLineageSnapshot result = f.read();
        assertEquals(Map.of(701L, q("2")), result.reservedByRequirementDetail());
        assertEquals(Map.of(701L, q("2")), result.frozenReservedByRequirementDetail());
        assertEquals(2, result.stocks().size());
        verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L, 92L));
    }

    @Test
    void equivalentNetQuantitiesCannotHideChangesToOriginalFacts() {
        Fixture f = new Fixture(); String first = f.read().sourceVersions().get("wm_material_stock_tx:101");
        f.transactions.set(1, change(f.transactions.get(1), "txQuantity", q("7")));
        f.transactions.set(2, change(f.transactions.get(2), "txQuantity", q("3")));
        assertNotEquals(first, f.read().sourceVersions().get("wm_material_stock_tx:101"));
    }

    @Test
    void physicalTransactionAndTenantPreconditionsRunBeforeMapperAndNeverRepairTransactions() {
        Fixture f = new Fixture();
        doThrow(new ServiceException("ERP transaction absent")).when(f.qualification).requireParticipatingWritableErpTransaction();
        assertThrows(ServiceException.class, f::read); verifyNoInteractions(f.mapper, f.inventory);
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        Fixture g = new Fixture();
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn("other");
            assertThrows(ServiceException.class, () -> g.verifier().verifyLocked(TENANT, MATERIAL));
        }
        verifyNoInteractions(g.mapper, g.inventory, g.qualification);
    }

    @Test
    void genericWritableErpConnectionIsNotAnOwnedRepeatableReadSourceChain() {
        Fixture f = new Fixture();
        doThrow(new ServiceException("not an owned RR source chain")).when(f.qualification).requireSourceChainTransaction();
        assertThrows(ServiceException.class, f::read);
        verifyNoInteractions(f.mapper, f.inventory);
    }

    @Test
    void mapperExceptionsAreSanitizedWithoutZeroOrPartialFallback() {
        Fixture f = new Fixture();
        when(f.mapper.lockTransactions(anyString(), anyLong(), anyList())).thenThrow(new IllegalStateException("secret-db-password"));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().startsWith(ERROR)); assertFalse(error.getMessage().contains("secret")); assertNull(error.getCause());
    }

    @Test
    void explicitNoLocationManagementDoesNotTurnNullOptionalLocationIntoUnknownIdentity() {
        Fixture f = new Fixture();
        f.stock("locationId", null); f.issueLine("locationId", null);
        f.receiveHead("locationId", null); f.receiveDetail("locationId", null);
        f.areas.set(0, change(f.areas.get(0), "locationFlag", 0L)); f.locations.clear();
        assertEquals(Map.of(701L, q("4")), f.read().reservedByRequirementDetail());
        verify(f.mapper, never()).lockLocations(anyString(), anyList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badEngines")
    void sourceEngineFailuresPrecedeAnyBusinessCurrentRead(String name, Consumer<Fixture> corrupt) {
        Fixture f = new Fixture(); corrupt.accept(f); assertReject(f);
        verifyNoInteractions(f.inventory);
        verify(f.mapper, never()).lockStocks(anyString(), anyLong());
        verify(f.mapper, never()).lockTransactions(anyString(), anyLong(), anyList());
    }
    static Stream<Arguments> badEngines() {
        Stream<Arguments> everyTable = sourceTables().stream().map(table -> bad("MyISAM " + table,
            f -> f.engines.replaceAll(row -> row.tableName().equals(table) ? new TableEngine(table, "MyISAM") : row)));
        return Stream.concat(everyTable, Stream.of(
            bad("missing table", f -> f.engines.remove(0)),
            bad("unknown engine", f -> f.engines.set(0, new TableEngine(f.engines.get(0).tableName(), null))),
            bad("unexpected table", f -> f.engines.add(new TableEngine("fake", "InnoDB"))),
            bad("duplicate table", f -> f.engines.add(f.engines.get(0))),
            bad("missing engine collection", f -> when(f.mapper.selectTableEngines()).thenReturn(null)),
            bad("pin query unavailable", f -> when(f.mapper.pinTableDefinitions()).thenThrow(new IllegalStateException("secret-DDL"))),
            bad("pin query returned rows", f -> when(f.mapper.pinTableDefinitions()).thenReturn(List.of(1L))),
            bad("pin result unknown", f -> when(f.mapper.pinTableDefinitions()).thenReturn(null))));
    }
    private static List<String> sourceTables() {
        return List.of("bd_material", "bd_unit_of_measure", "wm_material_stock", "wm_warehouse", "wm_storage_area", "wm_storage_location",
            "wm_material_stock_tx", "wm_material_receive", "wm_material_receive_line", "wm_material_receive_detail",
            "wm_production_issue", "wm_production_issue_line", "prd_material_requirement", "prd_material_requirement_detail", "prd_work_order");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badLineages")
    void unknownUnbalancedOrCrossOwnedOriginalsRejectEntireMaterial(String name, Consumer<Fixture> corrupt) {
        Fixture f = new Fixture(); corrupt.accept(f); assertReject(f);
    }
    static Stream<Arguments> badLineages() {
        return Stream.of(
            bad("tx stockId variant 0", f -> f.tx("stockId", 999L)),
            bad("tx stockId variant 1", f -> f.tx("stockId", null)),
            bad("tx tenantId variant 2", f -> f.tx("tenantId", "other")),
            bad("tx materialId variant 3", f -> f.tx("materialId", 999L)),
            bad("tx sourceDocId variant 4", f -> f.tx("sourceDocId", null)),
            bad("tx sourceDocLineId variant 5", f -> f.tx("sourceDocLineId", null)),
            bad("tx txType variant 6", f -> f.tx("txType", "TRANSFER_IN")),
            bad("tx sourceDocType variant 7", f -> f.tx("sourceDocType", "MANUAL_IMPORT")),
            bad("tx delFlag variant 8", f -> f.tx("delFlag", "1")),
            bad("tx delFlag variant 9", f -> f.tx("delFlag", null)),
            bad("tx baseUomId variant 10", f -> f.tx("baseUomId", null)),
            bad("tx baseUomId variant 11", f -> f.tx("baseUomId", "027")),
            bad("tx baseUomId variant 12", f -> f.tx("baseUomId", "28")),
            bad("tx txQuantity variant 13", f -> f.tx("txQuantity", null)),
            bad("tx txQuantity variant 14", f -> f.tx("txQuantity", q("0"))),
            bad("tx txQuantity variant 15", f -> f.tx("txQuantity", q("-1"))),
            bad("tx txQuantity variant 16", f -> f.tx("txQuantity", new BigDecimal("10.00001"))),
            bad("tx txQuantity variant 17", f -> f.tx("txQuantity", new BigDecimal("100000000000000.0000"))),
            bad("tx beforeQuantity variant 18", f -> f.tx("beforeQuantity", null)),
            bad("tx afterQuantity variant 19", f -> f.tx("afterQuantity", null)),
            bad("tx beforeQuantity variant 20", f -> f.tx("beforeQuantity", q("1"))),
            bad("tx afterQuantity variant 21", f -> f.tx("afterQuantity", q("9"))),
            bad("tx txFlag variant 22", f -> f.tx("txFlag", -1)),
            bad("tx txFlag variant 23", f -> f.tx("txFlag", null)),
            bad("tx txAtEpochMillis variant 24", f -> f.tx("txAtEpochMillis", time(1))),
            bad("tx txAtEpochMillis variant 25", f -> f.tx("txAtEpochMillis", null)),
            bad("stock version variant 0", f -> f.stock("version", null)),
            bad("stock version variant 1", f -> f.stock("version", -1)),
            bad("stock baseUomId variant 2", f -> f.stock("baseUomId", "other")),
            bad("stock tenantId variant 3", f -> f.stock("tenantId", "other")),
            bad("stock materialId variant 4", f -> f.stock("materialId", 999L)),
            bad("stock stockId variant 5", f -> f.stock("stockId", 999L)),
            bad("stock stockQuantity variant 6", f -> f.stock("stockQuantity", null)),
            bad("stock reservedQuantity variant 7", f -> f.stock("reservedQuantity", null)),
            bad("stock reservedQuantity variant 8", f -> f.stock("reservedQuantity", q("5"))),
            bad("stock stockQuantity variant 9", f -> f.stock("stockQuantity", q("11"))),
            bad("stock frozenFlag variant 10", f -> f.stock("frozenFlag", null)),
            bad("stock delFlag variant 11", f -> f.stock("delFlag", "1")),
            bad("unit verifiedAtEpochMillis variant 0", f -> f.unit("verifiedAtEpochMillis", time(-500))),
            bad("unit conversionVersion variant 1", f -> f.unit("conversionVersion", null)),
            bad("unit uomId variant 2", f -> f.unit("uomId", 99L)),
            bad("unit tenantId variant 3", f -> f.unit("tenantId", "other")),
            bad("unit isBase variant 4", f -> f.unit("isBase", 0L)),
            bad("unit quantityScale variant 5", f -> f.unit("quantityScale", 0)),
            bad("receiveHead tenantId variant 0", f -> f.receiveHead("tenantId", "other")),
            bad("receiveHead status variant 1", f -> f.receiveHead("status", "SHELVED")),
            bad("receiveHead receiveId variant 2", f -> f.receiveHead("receiveId", 999L)),
            bad("receiveHead receiveAtEpochMillis variant 3", f -> f.receiveHead("receiveAtEpochMillis", time(1))),
            bad("receiveHead shelfAtEpochMillis variant 4", f -> f.receiveHead("shelfAtEpochMillis", null)),
            bad("receiveLine quantityReceived variant 0", f -> f.receiveLine("quantityReceived", q("9"))),
            bad("receiveDetail quantity variant 0", f -> f.receiveDetail("quantity", q("9"))),
            bad("receiveDetail lineId variant 1", f -> f.receiveDetail("lineId", 999L)),
            bad("receiveDetail tenantId variant 2", f -> f.receiveDetail("tenantId", "other")),
            bad("receiveDetail baseUomId variant 3", f -> f.receiveDetail("baseUomId", null)),
            bad("receiveDetail batchId variant 4", f -> f.receiveDetail("batchId", 999L)),
            bad("issueHead tenantId variant 0", f -> f.issueHead("tenantId", "other")),
            bad("issueHead woId variant 1", f -> f.issueHead("woId", 999L)),
            bad("issueHead status variant 2", f -> f.issueHead("status", null)),
            bad("issueHead totalQty variant 3", f -> f.issueHead("totalQty", q("9"))),
            bad("issueHead totalQty variant 4", f -> f.issueHead("totalQty", null)),
            bad("issueLine issueId variant 0", f -> f.issueLine("issueId", 999L)),
            bad("issueLine materialId variant 1", f -> f.issueLine("materialId", 999L)),
            bad("issueLine requirementDetailId variant 2", f -> f.issueLine("requirementDetailId", null)),
            bad("issueLine requiredQty variant 3", f -> f.issueLine("requiredQty", null)),
            bad("issueLine shortQty variant 4", f -> f.issueLine("shortQty", q("7"))),
            bad("issueLine lineStatus variant 5", f -> f.issueLine("lineStatus", "SHORT_CLOSED")),
            bad("requirement materialId variant 0", f -> f.requirement("materialId", 999L)),
            bad("requirement unit variant 1", f -> f.requirement("unit", "piece")),
            bad("requirement tenantId variant 2", f -> f.requirement("tenantId", "other")),
            bad("requirement sourceType variant 3", f -> f.requirement("sourceType", "UNKNOWN")),
            bad("requirement issuedQuantity variant 4", f -> f.requirement("issuedQuantity", q("1"))),
            bad("requirementHead planId variant 0", f -> f.requirementHead("planId", 999L)),
            bad("requirementHead bomId variant 1", f -> f.requirementHead("bomId", 999L)),
            bad("requirementHead status variant 2", f -> f.requirementHead("status", "DRAFT")),
            bad("workOrder woStatus variant 0", f -> f.workOrder("woStatus", "CLOSED")),
            bad("workOrder materialId variant 1", f -> f.workOrder("materialId", 999L)),
            bad("receive line parent unknown", f -> f.receiveLine("receiveId", null)),
            bad("receive detail parent unknown", f -> f.receiveDetail("receiveId", null)),
            bad("receive detail line unknown", f -> f.receiveDetail("lineId", null)),
            bad("issue line parent unknown", f -> f.issueLine("issueId", null)),
            bad("unknown tx type null", f -> f.tx("txType", null)),
            bad("empty stock is not zero", f -> f.stocks.clear()),
            bad("missing history is not zero", f -> f.transactions.clear()),
            bad("missing initial inflow", f -> f.transactions.remove(0)),
            bad("duplicate transaction", f -> f.transactions.add(f.transactions.get(0))),
            bad("empty unit", f -> f.unit = null),
            bad("missing receive head", f -> f.receiveHeads.clear()),
            bad("missing receive line", f -> f.receiveLines.clear()),
            bad("missing receive allocations", f -> f.receiveDetails.clear()),
            bad("duplicate receive allocation", f -> f.receiveDetails.add(f.receiveDetails.get(0))),
            bad("missing issue head", f -> f.issueHeads.clear()),
            bad("missing issue line", f -> f.issueLines.clear()),
            bad("missing MRP detail", f -> f.requirements.clear()),
            bad("missing MRP head", f -> f.requirementHeads.clear()),
            bad("missing work order", f -> f.workOrders.clear()),
            bad("missing area", f -> f.areas.clear()),
            bad("missing location", f -> f.locations.clear()),
            bad("unknown native metadata", f -> f.columns.clear()),
            bad("legacy double cannot be upgraded by shadow column", f -> f.columns.set(0, change(f.columns.get(0), "dataType", "double"))),
            bad("extra native metadata", f -> f.columns.add(new QuantityColumn("fake", "verified", "decimal", 18, 4))),
            bad("duplicate native metadata", f -> f.columns.add(f.columns.get(0))),
            bad("timezone unknown", f -> f.clock = new DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()), "SYSTEM")),
            bad("clock backwards", f -> f.clock = new DatabaseClock(BigDecimal.valueOf(NOW.minusSeconds(1).getEpochSecond()), "+08:00")),
            bad("area freeze unknown", f -> f.areas.set(0, change(f.areas.get(0), "frozenFlag", null))),
            bad("reserve before wrong", f -> f.transactions.set(1, change(f.transactions.get(1), "beforeQuantity", q("9")))),
            bad("sales order not an MRP owner", f -> f.transactions.set(1, change(f.transactions.get(1), "sourceDocType", "SALES_ORDER"))),
            bad("reserve before unknown", f -> f.transactions.set(1, change(f.transactions.get(1), "beforeQuantity", null))),
            bad("reserve direction wrong", f -> f.transactions.set(1, change(f.transactions.get(1), "txFlag", 1))),
            bad("reserve changed original inventory", f -> f.transactions.set(1, change(f.transactions.get(1), "afterQuantity", q("11")))),
            bad("reservation exceeds MRP", f -> f.requirement("requiredQuantity", q("3"))),
            bad("overrelease not rescued by final zero", f -> { f.transactions.set(2, change(f.transactions.get(2), "txQuantity", q("7"))); f.stock("reservedQuantity", q("0")); }),
            bad("reservation exceeds issue demand", f -> { f.issueLine("quantity", q("3")); f.issueLine("requiredQty", q("3")); f.issueLine("shortQty", q("3")); f.issueHead("totalQty", q("3")); }),
            bad("consumption relation absent", f -> { f.consumption(); f.transactions.set(2, change(f.transactions.get(2), "relatedTxId", null)); }),
            bad("consumption relation missing", f -> { f.consumption(); f.transactions.set(2, change(f.transactions.get(2), "relatedTxId", 999L)); }),
            bad("consumption wrong owner", f -> { f.consumption(); f.transactions.set(2, change(f.transactions.get(2), "sourceDocLineId", 999L)); }),
            bad("consumption exceeds physical", f -> { f.consumption(); f.transactions.set(2, change(f.transactions.get(2), "txQuantity", q("4"))); }),
            bad("consumption wrong physical balance", f -> { f.consumption(); f.transactions.set(2, change(f.transactions.get(2), "afterQuantity", q("8"))); }),
            bad("physical not in original issue ledger", f -> { f.consumption(); f.issueLine("issuedQty", q("0")); }),
            bad("frozen lineage still must be authentic", f -> { f.stock("frozenFlag", 1L); f.transactions.clear(); f.summary(q("0"), q("0"), q("10")); }));
    }

    private static Arguments bad(String name, Consumer<Fixture> corrupt) { return Arguments.of(name, corrupt); }
    private static void assertReject(Fixture f) {
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().startsWith(ERROR), error.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static <T> T change(T record, String field, Object value) {
        try {
            RecordComponent[] components = record.getClass().getRecordComponents();
            Class<?>[] types = new Class<?>[components.length]; Object[] values = new Object[components.length];
            boolean changed = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType(); values[i] = components[i].getAccessor().invoke(record);
                if (components[i].getName().equals(field)) { values[i] = value; changed = true; }
            }
            assertTrue(changed, field);
            return (T) record.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static TransactionRow rowTx(Long id, String type, Integer flag, String amount, String before, String after,
                                     String source, Long head, Long line, Long related, long seconds) {
        return new TransactionRow(id, TENANT, MATERIAL, 91L, "27", type, flag, q(amount), q(before), q(after), source,
            head, line, null, related, time(seconds), "0");
    }

    private static class Fixture {
        ErpCertifiedSourceBirthReader coverage=mock(ErpCertifiedSourceBirthReader.class);
        final ErpPurchaseStockLineageMapper mapper = mock(ErpPurchaseStockLineageMapper.class);
        final ErpPurchaseInventorySourceVerifier inventory = mock(ErpPurchaseInventorySourceVerifier.class);
        final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
        InventorySnapshot snapshot;
        DatabaseClock clock = new DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()), "+08:00");
        UnitRow unit = new UnitRow(UNIT, TENANT, 4, "unit-v1", time(-1000), "QUANTITY", 1L, null, BigDecimal.ONE, 1L, "0");
        List<StockRow> stocks = new ArrayList<>(List.of(new StockRow(91L, TENANT, MATERIAL, "27", 8L, 18L, 28L, 81L, null,
            q("10"), q("4"), 0L, 2, "0", time(-600), time(86400))));
        List<TransactionRow> transactions = new ArrayList<>(List.of(
            rowTx(100L, "RECEIVE_IN", 1, "10", "0", "10", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600),
            rowTx(101L, "RESERVE", 0, "6", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300),
            rowTx(102L, "UNRESERVE", 0, "2", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -200)));
        List<AreaRow> areas = new ArrayList<>(List.of(new AreaRow(18L, TENANT, 8L, 0L, 1L, 1L, "0")));
        List<LocationRow> locations = new ArrayList<>(List.of(new LocationRow(28L, TENANT, 8L, 18L, 0L, 1L, "0")));
        List<ReceiveHead> receiveHeads = new ArrayList<>(List.of(new ReceiveHead(501L, TENANT, "RK501", "FINISHED", 8L, 18L, 28L, time(-700), time(-650), "0")));
        List<ReceiveLine> receiveLines = new ArrayList<>(List.of(new ReceiveLine(511L, TENANT, 501L, MATERIAL, "27", q("10"), "0")));
        List<ReceiveDetail> receiveDetails = new ArrayList<>(List.of(new ReceiveDetail(521L, TENANT, 501L, 511L, MATERIAL, "27", q("10"), 8L, 18L, 28L, 81L, null, "0")));
        List<IssueHead> issueHeads = new ArrayList<>(List.of(new IssueHead(601L, TENANT, "LL601", 801L, 8L, "CREATED", "NORMAL", q("8"), time(-350), "0")));
        List<IssueLine> issueLines = new ArrayList<>(List.of(new IssueLine(611L, TENANT, 601L, MATERIAL, "27", 8L, 28L, 81L,
            q("8"), q("8"), q("0"), q("8"), 701L, "PENDING", "0")));
        List<RequirementDetail> requirements = new ArrayList<>(List.of(new RequirementDetail(701L, TENANT, 711L, MATERIAL, "27", q("8"), q("0"), "PROCUREMENT", 1L, 0, "0")));
        List<RequirementHead> requirementHeads = new ArrayList<>(List.of(new RequirementHead(711L, TENANT, "MRP711", 901L, 1001L, 240L, "PROCESS", "CONFIRMED", "0")));
        List<WorkOrder> workOrders = new ArrayList<>(List.of(new WorkOrder(801L, TENANT, "WO801", 901L, 1001L, 240L, "RELEASED", "0")));
        List<QuantityColumn> columns = new ArrayList<>();
        List<TableEngine> engines = new ArrayList<>(sourceTables().stream().map(table -> new TableEngine(table, "InnoDB")).toList());

        Fixture() {
            summary(q("10"), q("4"), q("0"));
            when(mapper.pinTableDefinitions()).thenReturn(List.of());
            when(mapper.selectTableEngines()).thenAnswer(i -> engines);
            Map.of("wm_material_stock", List.of("stock_quantity", "reserved_quantity"),
                "wm_material_stock_tx", List.of("tx_quantity", "before_quantity", "after_quantity"),
                "wm_material_receive_line", List.of("quantity_received"), "wm_material_receive_detail", List.of("quantity"),
                "wm_production_issue", List.of("total_qty"),
                "wm_production_issue_line", List.of("quantity", "required_qty", "issued_qty", "short_qty"),
                "prd_material_requirement_detail", List.of("required_quantity", "issued_quantity"))
                .forEach((table, fields) -> fields.forEach(field -> columns.add(new QuantityColumn(table, field, "decimal", 18, 4))));
            columns.add(new QuantityColumn("bd_unit_of_measure", "conversion_rate", "decimal", 18, 6));
            when(inventory.verifyLocked(anyString(), anyLong())).thenAnswer(i -> snapshot);
            when(mapper.selectDatabaseClock()).thenAnswer(i -> clock);
            when(mapper.selectQuantityColumns()).thenAnswer(i -> columns);
            when(mapper.lockUnit(anyString(), anyLong())).thenAnswer(i -> unit);
            when(mapper.lockStocks(anyString(), anyLong())).thenAnswer(i -> stocks);
            when(mapper.lockTransactions(anyString(), anyLong(), anyList())).thenAnswer(i -> transactions);
            when(mapper.lockAreas(anyString(), anyList())).thenAnswer(i -> areas);
            when(mapper.lockLocations(anyString(), anyList())).thenAnswer(i -> locations);
            when(mapper.lockReceiveHeads(anyString(), anyList())).thenAnswer(i -> receiveHeads);
            when(mapper.lockReceiveLines(anyString(), anyList())).thenAnswer(i -> receiveLines);
            when(mapper.lockReceiveDetails(anyString(), anyList())).thenAnswer(i -> receiveDetails);
            when(mapper.lockIssueHeads(anyString(), anyList())).thenAnswer(i -> issueHeads);
            when(mapper.lockIssueLines(anyString(), anyList())).thenAnswer(i -> issueLines);
            when(mapper.lockRequirementDetails(anyString(), anyList())).thenAnswer(i -> requirements);
            when(mapper.lockRequirementHeads(anyString(), anyList())).thenAnswer(i -> requirementHeads);
            when(mapper.lockWorkOrders(anyString(), anyList())).thenAnswer(i -> workOrders);
        }

        void summary(BigDecimal amount, BigDecimal reserved, BigDecimal frozen) {
            snapshot = new InventorySnapshot(TENANT, MATERIAL, UNIT, 4, NOW, "+08:00", amount, reserved, q("1"), frozen, Set.of(91L),
                Map.of("wm_material_stock:91", "version=2;digest", "bd_unit_of_measure:27", "conversionVersion=unit-v1;digest"));
        }
        void stock(String name, Object value) { stocks.set(0, change(stocks.get(0), name, value)); }
        void tx(String name, Object value) { transactions.set(0, change(transactions.get(0), name, value)); }
        void unit(String name, Object value) { unit = change(unit, name, value); }
        void receiveHead(String name, Object value) { receiveHeads.set(0, change(receiveHeads.get(0), name, value)); }
        void receiveLine(String name, Object value) { receiveLines.set(0, change(receiveLines.get(0), name, value)); }
        void receiveDetail(String name, Object value) { receiveDetails.set(0, change(receiveDetails.get(0), name, value)); }
        void issueHead(String name, Object value) { issueHeads.set(0, change(issueHeads.get(0), name, value)); }
        void issueLine(String name, Object value) { issueLines.set(0, change(issueLines.get(0), name, value)); }
        void requirement(String name, Object value) { requirements.set(0, change(requirements.get(0), name, value)); }
        void requirementHead(String name, Object value) { requirementHeads.set(0, change(requirementHeads.get(0), name, value)); }
        void workOrder(String name, Object value) { workOrders.set(0, change(workOrders.get(0), name, value)); }
        void twoOwners() {
            transactions.set(1, change(transactions.get(1), "txQuantity", q("2")));
            transactions.set(2, rowTx(102L, "RESERVE", 0, "2", "10", "10", "WM_PRODUCTION_ISSUE", 602L, 612L, null, -200));
            issueHeads.add(change(change(issueHeads.get(0), "issueId", 602L), "issueCode", "LL602"));
            issueLines.add(change(change(issueLines.get(0), "lineId", 612L), "issueId", 602L));
            issueLines.set(1, change(issueLines.get(1), "requirementDetailId", 702L));
            requirements.add(change(requirements.get(0), "detailId", 702L));
        }
        void consumption() {
            transactions.remove(2);
            transactions.add(rowTx(103L, "CONSUME_RESERVE", 0, "3", "10", "7", "WM_PRODUCTION_ISSUE", 601L, 611L, 104L, -100));
            transactions.add(rowTx(104L, "PRODUCTION_ISSUE", -1, "3", "10", "7", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
            stock("stockQuantity", q("7")); stock("reservedQuantity", q("3"));
            issueLine("issuedQty", q("3")); issueLine("shortQty", q("5")); issueLine("lineStatus", "PARTIAL");
            issueHead("status", "PARTIAL"); requirement("issuedQuantity", q("3")); summary(q("7"), q("3"), q("0"));
        }
        ErpPurchaseStockLineageVerifier verifier() { return new ErpPurchaseStockLineageVerifier(mapper, inventory, qualification, coverage); }
        StockLineageSnapshot read() {
            try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
                tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
                return verifier().verifyLocked(TENANT, MATERIAL);
            }
        }
    }
}
