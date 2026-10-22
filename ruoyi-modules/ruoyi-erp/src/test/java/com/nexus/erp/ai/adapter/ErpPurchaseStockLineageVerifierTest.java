package com.nexus.erp.ai.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier.InventorySnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseStockLineageVerifier.StockLineageSnapshot;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 使用Mapper替身隔离持久层，测试不访问外部数据库或服务。 */
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
        var all = mock(ErpCertifiedSourceBirthMapper.class);
        when(all.lockAllStocks(MATERIAL)).thenReturn(List.of(91L,55L));
        when(all.lockAllTransactions(MATERIAL)).thenReturn(List.of(100L,101L,102L));
        f.coverage=new ErpCertifiedSourceBirthReader(all,f.qualification);
        assertThrows(ServiceException.class,f::read,"另一租户或删除库存不能因当前租户过滤而消失");
        verify(all,never()).lockBirth(anyString(),anyLong());
    }

    @Test
    void nonEmptyPathCannotHideAnotherTenantOrphanTransactionFromGlobalMaterialCoverage() {
        Fixture f=new Fixture();
        var all = mock(ErpCertifiedSourceBirthMapper.class);
        when(all.lockAllStocks(MATERIAL)).thenReturn(List.of(91L));
        when(all.lockAllTransactions(MATERIAL)).thenReturn(List.of(100L,101L,102L,999L));
        f.coverage=new ErpCertifiedSourceBirthReader(all,f.qualification);
        assertThrows(ServiceException.class,f::read,"同物料另一租户孤立流水不得隐藏或加入供给");
        verify(all,never()).lockBirth(anyString(),anyLong());
    }

    @Test
    void globallyCompleteNonEmptyOriginalSourcesDoNotRequireOldStockToHaveBirth() {
        Fixture f=new Fixture();
        var all = mock(ErpCertifiedSourceBirthMapper.class);
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

    /** 复现只读快照：同根单位三材料10/10/20已领，目标物料有入库历史且余额为零。 */
    @ParameterizedTest(name = "同单位混料已完成，目标行{0}")
    @MethodSource("mixedTargets")
    void completedMixedIssueAuthenticatesEveryParentLineWithoutAddingOtherMaterialsToSupply(int target) {
        Fixture f = new Fixture(); f.mixedCompleted(target);
        StockLineageSnapshot result = assertDoesNotThrow(f::read, "合法同单位混料父单应由完整来源认证");
        assertAll(
            () -> assertEquals(q("0"), result.inventory().quantity()),
            () -> assertEquals(q("0"), result.inventory().reserved()),
            () -> assertEquals(Map.of(), result.reservedByRequirementDetail()),
            () -> assertEquals(1, result.stocks().size()),
            () -> assertEquals(MATERIAL, result.stocks().get(0).stock().materialId()),
            () -> assertEquals(Set.of(100L, 103L + target), result.transactionIds()),
            () -> assertTrue(result.sourceVersions().keySet().containsAll(Set.of(
                "wm_production_issue_line:611", "wm_production_issue_line:612", "wm_production_issue_line:613",
                "prd_material_requirement_detail:701", "prd_material_requirement_detail:702", "prd_material_requirement_detail:703"))));
    }

    static Stream<Integer> mixedTargets() { return Stream.of(0, 1, 2); }

    /** 正常下达和领料只更新明细，历史MRP头仍可保持DRAFT；不能要求修改历史状态。 */
    @Test
    void completedActualIssueCanAuthenticateDraftMrpThroughItsBoundErpWorkOrder() {
        Fixture f = new Fixture();
        f.transactions.clear();
        f.transactions.add(rowTx(100L, "RECEIVE_IN", 1, "10", "0", "10", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600));
        f.transactions.add(rowTx(103L, "PRODUCTION_ISSUE", -1, "10", "10", "0", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
        f.stock("stockQuantity", q("0")); f.stock("reservedQuantity", q("0"));
        f.issueLine("quantity", q("10")); f.issueLine("requiredQty", q("10"));
        f.issueLine("issuedQty", q("10")); f.issueLine("shortQty", q("0")); f.issueLine("lineStatus", "ISSUED");
        f.issueHead("totalQty", q("10")); f.issueHead("status", "COMPLETED");
        f.requirement("requiredQuantity", q("10")); f.requirement("issuedQuantity", q("10"));
        f.requirementHead("status", "DRAFT"); f.workOrder("woStatus", "COMPLETED");
        f.summary(q("0"), q("0"), q("0"));
        assertEquals(q("0"), assertDoesNotThrow(f::read, "已绑定ERP工单且完整履约的历史DRAFT需求应可核验").inventory().quantity());
    }

    /** 快照形状使用根单位26/整数精度，测试身份和单号独立，不拷贝现场数据。 */
    @ParameterizedTest(name = "原生同单位26混料DRAFT，目标行{0}")
    @MethodSource("mixedTargets")
    void nativePieceMixedDraftPreservesAllActualSourcesAndOnlyTargetStock(int target) {
        Fixture f = new Fixture(); f.mixedCompleted(target); f.nativePiece(); f.requirementHead("status", "DRAFT");
        StockLineageSnapshot result = assertDoesNotThrow(f::read);
        assertEquals(26L, result.inventory().baseUomId()); assertEquals(0, result.inventory().quantityScale());
        assertEquals(q("0"), result.inventory().quantity()); assertEquals(1, result.stocks().size());
        assertTrue(result.sourceVersions().keySet().containsAll(Set.of(
            "wm_production_issue_line:611", "wm_production_issue_line:612", "wm_production_issue_line:613",
            "prd_material_requirement_detail:701", "prd_material_requirement_detail:702", "prd_material_requirement_detail:703")));
        for (long id = 103L; id <= 105L; id++) assertEquals(id == 103L + target, result.sourceVersions().containsKey("wm_material_stock_tx:" + id));
        assertEquals(Set.of(100L, 103L + target), result.transactionIds());
        verify(f.mapper, times(1)).lockStocks(TENANT, MATERIAL);
        verify(f.coverage).requireGlobalCoverageLocked(TENANT, MATERIAL, Set.of(91L), Set.of(100L, 103L + target));
        verify(f.inventory, times(2)).verifyLocked(TENANT, MATERIAL);
    }

    @ParameterizedTest(name = "根单位整数精度拒绝兄弟字段{0}")
    @MethodSource("fractionalPeerFields")
    void nativeIntegerUnitRejectsFractionalPeerLineWithoutRoundingOrZero(String field) {
        Fixture f = new Fixture(); f.mixedCompleted(0); f.nativePiece();
        assertDoesNotThrow(f::read); f.peerLine(field, new BigDecimal("1.5000"));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("禁止舍入升级"));
    }

    static Stream<String> fractionalPeerFields() { return Stream.of("quantity", "requiredQty", "issuedQty", "shortQty"); }

    @Test
    void nativeIntegerUnitRejectsFractionalPeerMrpCumulativeQuantity() {
        Fixture f = new Fixture(); f.mixedCompleted(0); f.nativePiece();
        assertDoesNotThrow(f::read); f.peerRequirement("issuedQuantity", new BigDecimal("9.5000"));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("禁止舍入升级"));
    }

    /** 兄弟MRP累计已领可以来自另一父单，当前头10件不能硬等累计20件。 */
    @Test
    void peerMrpCumulativeIssuedAcrossOtherHeadsIsNotEquatedWithThisParentSubset() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        f.peerRequirement("requiredQuantity", q("20")); f.peerRequirement("issuedQuantity", q("20"));
        StockLineageSnapshot result = assertDoesNotThrow(f::read, "兄弟累计已领跨单不能冒充本次头全集也不能被硬相等误拒");
        assertEquals(q("0"), result.inventory().quantity());
        assertTrue(result.sourceVersions().containsKey("prd_material_requirement_detail:702"));
        assertEquals(Set.of(100L, 103L), result.transactionIds());
    }

    /** 调用者可能已持目标库存/流水，读取器不能新增兄弟物料流水锁再争共同父头。 */
    @Test
    void mixedParentNeverExpandsTransactionLocksBeyondExistingTargetMaterialScope() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        StockLineageSnapshot result = assertDoesNotThrow(f::read);
        assertEquals(List.of("lockTransactions"), mockingDetails(f.mapper).getInvocations().stream()
            .map(call -> call.getMethod().getName()).filter(name -> name.toLowerCase().contains("transaction")).toList(), "不得超出目标流水写集锁兄弟物料");
        verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L));
        verify(f.mapper, times(1)).lockStocks(TENANT, MATERIAL);
        verify(f.inventory, times(2)).verifyLocked(TENANT, MATERIAL);
        assertEquals(Set.of(100L, 103L), result.transactionIds());
    }

    @Test
    void partialMixedParentKeepsTargetReservationSeparateFromPeerPhysicalIssues() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        f.transactions.set(1, rowTx(103L, "RESERVE", 0, "3", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
        f.stock("stockQuantity", q("10")); f.stock("reservedQuantity", q("3"));
        f.issueLine("issuedQty", q("0")); f.issueLine("shortQty", q("10")); f.issueLine("lineStatus", "PENDING");
        f.requirement("issuedQuantity", q("0")); f.issueHead("status", "PARTIAL"); f.summary(q("10"), q("3"), q("0"));
        StockLineageSnapshot result = assertDoesNotThrow(f::read);
        assertEquals(q("10"), result.inventory().quantity());
        assertEquals(Map.of(701L, q("3")), result.reservedByRequirementDetail());
        assertEquals(Map.of(701L, q("3")), result.stocks().get(0).reservedByRequirementDetail());
    }

    @Test
    void peerMrpVersionsAreRetainedEvenWhenOnlyTargetIssuedHistoryIsReplayed() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        StockLineageSnapshot initial = f.read();
        f.peerRequirement("requiredQuantity", q("20")); f.peerRequirement("issuedQuantity", q("20"));
        StockLineageSnapshot changed = assertDoesNotThrow(f::read);
        assertNotEquals(initial.sourceVersions().get("prd_material_requirement_detail:702"), changed.sourceVersions().get("prd_material_requirement_detail:702"));
        assertEquals(q("0"), changed.inventory().quantity());
        assertEquals(Set.of(100L, 103L), changed.transactionIds());
        assertEquals(
                Set.of(100L, 103L),
                changed.stocks().get(0).transactions().stream()
                        .map(TransactionRow::txId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void targetPhysicalIssueMissingFromCompleteHistoryCannotBeMaskedByCompletedPeers() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        f.transactions.set(1, rowTx(103L, "RESERVE", 0, "10", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("已领数量未由全量真实出库证明"));
    }

    @Test
    void twoPeerLinesForOneMrpUseScopedSubtotalAsUpperBoundNotEachLineOrWholeHistory() {
        Fixture f = new Fixture(); f.mixedCompleted(0);
        IssueLine peer = f.issueLines.get(1), third = f.issueLines.get(2);
        f.issueLines.set(2, change(change(third, "materialId", peer.materialId()), "requirementDetailId", 702L));
        f.requirements.remove(2); f.peerRequirement("requiredQuantity", q("40")); f.peerRequirement("issuedQuantity", q("40"));
        assertEquals(q("0"), assertDoesNotThrow(f::read).inventory().quantity());
        f.peerRequirement("issuedQuantity", q("25"));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("兄弟MRP当前父来源已领合计超过累计已领"));
    }

    @ParameterizedTest(name = "混料父来源拒绝：{0}")
    @MethodSource("badMixedSources")
    void incompleteCrossOwnedOrNonConservingPeerSourcesRejectWholeTarget(String name, Consumer<Fixture> corrupt, String reason) {
        Fixture f = new Fixture(); f.mixedCompleted(0); assertDoesNotThrow(f::read, "基准混料完整父来源应先通过");
        corrupt.accept(f);
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().startsWith(ERROR)); assertTrue(error.getMessage().contains(reason), error.getMessage());
    }

    static Stream<Arguments> badMixedSources() {
        return Stream.of(
            mixedBad("兄弟原声明数量未知", f -> f.peerLine("quantity", null), "原始数量未知"),
            mixedBad("兄弟原应领数量未知", f -> f.peerLine("requiredQty", null), "原始数量未知"),
            mixedBad("兄弟原已领数量未知", f -> f.peerLine("issuedQty", null), "原始数量未知"),
            mixedBad("兄弟原欠领数量未知", f -> f.peerLine("shortQty", null), "原始数量未知"),
            mixedBad("兄弟原应领数量不能补零", f -> f.peerLine("requiredQty", q("0")), "原数量与应领数量口径尚未支持"),
            mixedBad("兄弟原声明数量与应领不符", f -> f.peerLine("quantity", q("9")), "原数量与应领数量口径尚未支持"),
            mixedBad("兄弟原数量为负", f -> f.peerLine("issuedQty", q("-1")), "原始数量未知"),
            mixedBad("兄弟原数量超原生精度", f -> f.peerLine("issuedQty", new BigDecimal("10.00001")), "禁止舍入升级"),
            mixedBad("兄弟MRP累计已领未知", f -> f.peerRequirement("issuedQuantity", null), "原始数量未知"),
            mixedBad("兄弟MRP原需求未知", f -> f.peerRequirement("requiredQuantity", null), "原始数量未知"),
            mixedBad("兄弟MRP累计已领不能补零", f -> f.peerRequirement("issuedQuantity", q("0")), "兄弟MRP当前父来源已领合计超过累计已领"),
            mixedBad("兄弟MRP单位未知", f -> f.peerRequirement("unit", null), "混单位头总量不能认证"),
            mixedBad("异物料MRP物料与领料不符", f -> f.peerRequirement("materialId", 999L), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("异物料MRP租户", f -> f.peerRequirement("tenantId", "other"), "MRP明细身份"),
            mixedBad("异物料MRP状态", f -> f.peerRequirement("status", 0L), "MRP明细身份"),
            mixedBad("异物料MRP倒冲", f -> f.peerRequirement("backflushFlag", 1), "MRP明细身份"),
            mixedBad("异物料MRP已领不能裁剪为目标子集", f -> f.peerRequirement("issuedQuantity", q("9")), "兄弟MRP当前父来源已领合计超过累计已领"),
            mixedBad("异物料MRP已领超需求", f -> f.peerRequirement("requiredQuantity", q("9")), "MRP已领超需求"),
            mixedBad("混单位MRP禁止头标量认证", f -> f.peerRequirement("unit", "28"), "混单位头总量不能认证"),
            mixedBad("混单位原单行", f -> f.peerLine("baseUomId", "28"), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("异物料原行租户", f -> f.peerLine("tenantId", "other"), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("异物料原行删除", f -> f.peerLine("delFlag", "1"), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("兄弟原行物料未知", f -> f.peerLine("materialId", null), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("兄弟原行单位未知", f -> f.peerLine("baseUomId", null), "领料原始行租户/物料/单位/需求归属未知"),
            mixedBad("兄弟原行原MRP父缺失", f -> f.peerLine("requirementDetailId", null), "原始父身份缺失或无效"),
            mixedBad("兄弟原MRP明细未完整返回", f -> f.requirements.remove(1), "MRP明细覆盖不完整"),
            mixedBad("兄弟原声明超MRP需求", f -> {
                f.peerLine("quantity", q("11")); f.peerLine("requiredQty", q("11")); f.peerLine("shortQty", q("1"));
                f.peerLine("lineStatus", "PARTIAL"); f.issueHead("status", "PARTIAL"); f.issueHead("totalQty", q("41"));
            }, "父来源声明应领合计超过MRP原需求"),
            mixedBad("异物料原行欠领量", f -> f.peerLine("shortQty", q("1")), "欠领余额不一致"),
            mixedBad("异物料原行状态", f -> f.peerLine("lineStatus", "PARTIAL"), "数量状态不一致"),
            mixedBad("整单声明不能改为目标数量", f -> f.issueHead("totalQty", q("10")), "头总量与完整单据行不一致"),
            mixedBad("整单状态不能裁剪为目标状态", f -> f.issueHead("status", "PARTIAL"), "头状态与完整行履约不一致"),
            mixedBad("父MRP计划与ERP工单不符", f -> f.requirementHead("planId", 999L), "原始工单计划/BOM/母物料"),
            mixedBad("父MRP BOM与ERP工单不符", f -> f.requirementHead("bomId", 999L), "原始工单计划/BOM/母物料"),
            mixedBad("父MRP母物料与ERP工单不符", f -> f.requirementHead("materialId", 999L), "原始工单计划/BOM/母物料"),
            mixedBad("历史DRAFT工单来源类型不符", f -> { f.requirementHead("status", "DRAFT"); f.workOrder("sourceType", "PLAN"); }, "来源类型/ID的明确绑定"),
            mixedBad("历史DRAFT工单来源头ID不符", f -> { f.requirementHead("status", "DRAFT"); f.workOrder("sourceId", 999L); }, "来源类型/ID的明确绑定"),
            mixedBad("历史DRAFT工单来源头ID缺失", f -> { f.requirementHead("status", "DRAFT"); f.workOrder("sourceId", null); }, "来源类型/ID的明确绑定"));
    }

    private static Arguments mixedBad(String name, Consumer<Fixture> corrupt, String reason) { return Arguments.of(name, corrupt, reason); }

    /** 当前库存/流水与兄弟原行各自合法，不代表二者物料身份可以串接。 */
    @ParameterizedTest(name = "错引兄弟物料原行：{0}")
    @MethodSource("crossMaterialSourceCases")
    void targetTransactionsCannotBorrowCompatiblePeerLineOrMrpOwnership(String scenario) {
        Fixture f = new Fixture(); f.misboundMixed(scenario);
        TransactionRow misbound = f.transactions.stream().filter(row -> Long.valueOf(612L).equals(row.sourceDocLineId())).findFirst().orElseThrow();
        IssueLine peer = f.issueLines.get(1);
        assertEquals(MATERIAL, misbound.materialId()); assertNotEquals(misbound.materialId(), peer.materialId());
        assertEquals(misbound.baseUomId(), peer.baseUomId()); assertEquals(misbound.warehouseId(), peer.warehouseId());
        assertEquals(misbound.locationId(), peer.locationId()); assertNull(peer.batchId(), "合法未指定批次分配不能代替物料身份绑定");
        ServiceException error = assertThrows(ServiceException.class, f::read, "同单位/相容维度不能把目标流水认证给兄弟MRP");
        assertTrue(error.getMessage().contains("流水物料与原始领料行不符"), error.getMessage());
        verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L));
        verify(f.mapper, times(1)).lockStocks(TENANT, MATERIAL);
    }

    static Stream<String> crossMaterialSourceCases() {
        return Stream.of("预留", "预留及释放", "消费与配对出库", "完成后真实零余额");
    }

    /** 旧签名可构造DTO，但缺失的真实仓/区不能从当前库存补成证据。 */
    @Test
    void legacyTransactionConstructorDoesNotAuthorizeUnknownNativeDimensions() {
        Fixture f = new Fixture(); assertDoesNotThrow(f::read);
        TransactionRow legacy = legacyCopy(f.transactions.get(0), 17);
        assertAll(() -> assertNull(legacy.warehouseId()), () -> assertNull(legacy.areaId()),
            () -> assertNull(legacy.locationId()), () -> assertNull(legacy.batchId()), () -> assertNull(legacy.snId()));
        f.transactions.set(0, legacy);
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("原始流水仓储维度与目标库存身份不符"));
    }

    @Test
    void legacyWorkOrderConstructorCannotInferDraftSourceBindingFromPlanBomOrMotherMaterial() {
        Fixture f = new Fixture(); f.requirementHead("status", "DRAFT"); assertDoesNotThrow(f::read);
        WorkOrder legacy = legacyCopy(f.workOrders.get(0), 8);
        assertNull(legacy.sourceType()); assertNull(legacy.sourceId());
        f.workOrders.set(0, legacy);
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("来源类型/ID的明确绑定"));
    }

    /** 用反射检查旧JVM构造器描述符，使缺旧重载时回归仍能编译并产生真实断言失败。 */
    @SuppressWarnings("unchecked")
    private static <T> T legacyCopy(T record, int count) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Class<?>[] types = new Class<?>[count]; Object[] values = new Object[count];
        for (int i = 0; i < count; i++) {
            types[i] = components[i].getType();
            int component = i; values[i] = assertDoesNotThrow(() -> components[component].getAccessor().invoke(record));
        }
        var constructor = assertDoesNotThrow(() -> record.getClass().getConstructor(types), "必须保留旧" + count + "参数公开构造器");
        return (T) assertDoesNotThrow(() -> constructor.newInstance(values));
    }

    @ParameterizedTest(name = "实际流水五维错配：{0}/{1}={2}")
    @MethodSource("badTransactionDimensions")
    void everyNativeTransactionDimensionMustExactlyMatchItsTargetStock(int index, String field, Object value) {
        Fixture f = new Fixture(); f.withSn(88L); assertDoesNotThrow(f::read);
        f.transactions.set(index, change(f.transactions.get(index), field, value));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("原始流水仓储维度与目标库存身份不符"));
    }

    static Stream<Arguments> badTransactionDimensions() {
        return Stream.of(0, 1).flatMap(index -> Stream.of("warehouseId", "areaId", "locationId", "batchId", "snId")
            .flatMap(field -> Stream.of(Arguments.of(index, field, 99L), Arguments.of(index, field, (Object) null))));
    }

    @ParameterizedTest(name = "仓区必有，两端都缺失：{0}")
    @MethodSource("mandatoryDimensionFields")
    void warehouseAndAreaCannotBecomeKnownByHavingNullAtBothEnds(String field) {
        Fixture f = new Fixture();
        f.stock(field, null); f.transactions.replaceAll(row -> change(row, field, null));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("库存原始维度或冻结标志无效"));
    }

    static Stream<String> mandatoryDimensionFields() { return Stream.of("warehouseId", "areaId"); }
    static Stream<String> optionalDimensionFields() { return Stream.of("locationId", "batchId", "snId"); }

    @ParameterizedTest(name = "可选维度真实双方null：{0}")
    @MethodSource("optionalDimensionFields")
    void genuinelyAbsentOptionalDimensionsAreExactMatchesNotUnknownMandatoryFacts(String field) {
        Fixture f = new Fixture(); f.optionalDimensionAbsent(field);
        StockLineageSnapshot result = assertDoesNotThrow(f::read);
        assertEquals(q("10"), result.inventory().quantity()); assertEquals(Map.of(701L, q("4")), result.reservedByRequirementDetail());
        if ("locationId".equals(field)) verify(f.mapper, never()).lockLocations(anyString(), anyList());
    }

    @ParameterizedTest(name = "可选库存null但实际流水非null：{0}")
    @MethodSource("optionalDimensionFields")
    void optionalStockNullIsNotAWildcardForNonNullTransactionDimension(String field) {
        Fixture f = new Fixture(); f.optionalDimensionAbsent(field); assertDoesNotThrow(f::read);
        f.transactions.set(1, change(f.transactions.get(1), field, 99L));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().contains("原始流水仓储维度与目标库存身份不符"));
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
        f.transactions.add(change(change(rowTx(101L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600), "stockId", 92L), "batchId", 82L));
        f.transactions.add(rowTx(102L, "RESERVE", 0, "2", "5", "5", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300));
        f.transactions.add(change(change(rowTx(103L, "RESERVE", 0, "2", "5", "5", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300), "stockId", 92L), "batchId", 82L));
        f.receiveDetail("quantity", q("5"));
        f.receiveDetails.add(change(change(f.receiveDetails.get(0), "detailId", 522L), "batchId", 82L));
        f.issueLine("batchId", null); // 此场景未指定当前批次，历史分配仍必须逐笔核验真实流水维度。
        f.snapshot = new InventorySnapshot(TENANT, MATERIAL, UNIT, 4, NOW, "+08:00", q("5"), q("2"), q("1"), q("5"), Set.of(91L, 92L),
            Map.of("wm_material_stock:91", "version=2;digest1", "wm_material_stock:92", "version=2;digest2", "bd_unit_of_measure:27", "conversionVersion=unit-v1;digest"));
        StockLineageSnapshot result = f.read();
        assertEquals(Map.of(701L, q("2")), result.reservedByRequirementDetail());
        assertEquals(Map.of(701L, q("2")), result.frozenReservedByRequirementDetail());
        assertEquals(2, result.stocks().size());
        verify(f.mapper).lockTransactions(TENANT, MATERIAL, List.of(91L, 92L));
    }

    @ParameterizedTest(name = "当前选择批次 {0} 不能覆盖之前合法分批出库的真实批次")
    @ValueSource(longs = {81L, 82L})
    void latestMutableBatchSelectorDoesNotRebindHistoricalPostedTransactions(long selectedBatch) {
        Fixture f = multiBatchPartialIssue();
        f.issueLine("batchId", selectedBatch);
        StockLineageSnapshot result = assertDoesNotThrow(f::read, "已核验的每笔历史分配不能被最新批次选择字段改绑");
        assertEquals(2, result.stocks().size());
        assertEquals(q("2"), result.inventory().quantity());
        assertEquals(Map.of(), result.reservedByRequirementDetail());
        assertEquals(Map.of(), result.frozenReservedByRequirementDetail());
    }

    @Test
    void selectedBatchStillMustBelongToCertifiedTargetStockDimensions() {
        Fixture f = multiBatchPartialIssue();
        f.issueLine("batchId", 999L);
        assertThrows(ServiceException.class, f::read, "可变选择字段不能改绑未知或他人批次");
    }

    @Test
    void selectedBatchCannotBorrowCertifiedStockFromAnotherWarehouse() {
        Fixture f = partialIssueWithAdditionalCertifiedBatch(9L, 19L);
        // 行未指定储位，隔离仓库条件；既有历史流水和库存维度均不修改。
        f.issueLine("locationId", null);
        assertCurrentSelectorCannotBorrowCertifiedBatch(f);
    }

    @Test
    void selectedBatchCannotBorrowCertifiedStockFromAnotherExplicitLocation() {
        Fixture f = partialIssueWithAdditionalCertifiedBatch(8L, 18L);
        assertEquals(28L, f.issueLines.get(0).locationId(), "行储位必须明确，不能使用 null 通配");
        assertCurrentSelectorCannotBorrowCertifiedBatch(f);
    }

    private void assertCurrentSelectorCannotBorrowCertifiedBatch(Fixture f) {
        f.issueLine("batchId", null);
        StockLineageSnapshot control = assertDoesNotThrow(f::read, "选择为空时，额外库存及其独立原始入库链必须先完整认证");
        assertEquals(Set.of(91L, 92L, 93L), control.inventory().stockIds());
        assertEquals(3, control.stocks().size());
        assertEquals(q("7"), control.inventory().quantity());
        assertEquals(q("2"), control.inventory().frozenQuantity());

        // 仅改变当前选择；必须命中新选择归属守卫，而非历史维度或数量校验。
        f.issueLine("batchId", 83L);
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertEquals(ERROR + "领料当前批次选择缺少已认证库存维度归属", error.getMessage());
    }

    /** 额外批次拥有独立原始入库头/行/分配与收货流水，不借用旧历史分配。 */
    private Fixture partialIssueWithAdditionalCertifiedBatch(long warehouse, long area) {
        Fixture f = multiBatchPartialIssue();
        f.stocks.add(new StockRow(93L, TENANT, MATERIAL, "27", warehouse, area, 29L, 83L, null,
            q("5"), q("0"), 0L, 2, "0", time(-600), time(86400)));
        if (area != 18L) {
            f.areas.add(new AreaRow(area, TENANT, warehouse, 0L, 1L, 1L, "0"));
        }
        f.locations.add(new LocationRow(29L, TENANT, warehouse, area, 0L, 1L, "0"));
        f.receiveHeads.add(new ReceiveHead(502L, TENANT, "RK502", "FINISHED", warehouse, area, 29L,
            time(-700), time(-650), "0"));
        f.receiveLines.add(new ReceiveLine(512L, TENANT, 502L, MATERIAL, "27", q("5"), "0"));
        f.receiveDetails.add(new ReceiveDetail(523L, TENANT, 502L, 512L, MATERIAL, "27", q("5"),
            warehouse, area, 29L, 83L, null, "0"));
        TransactionRow receipt = rowTx(104L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 502L, 512L, null, -600);
        receipt = change(change(change(change(change(receipt, "stockId", 93L), "batchId", 83L),
            "warehouseId", warehouse), "areaId", area), "locationId", 29L);
        f.transactions.add(receipt);
        f.snapshot = new InventorySnapshot(TENANT, MATERIAL, UNIT, 4, NOW, "+08:00", q("7"), q("0"), q("1"), q("2"),
            Set.of(91L, 92L, 93L), Map.of("wm_material_stock:91", "version=2;digest1", "wm_material_stock:92", "version=2;digest2",
                "wm_material_stock:93", "version=2;digest3", "bd_unit_of_measure:27", "conversionVersion=unit-v1;digest"));
        return f;
    }

    @Test
    void historicalTransactionBatchMismatchStillFailsAfterChangingCurrentSelector() {
        Fixture f = multiBatchPartialIssue();
        f.issueLine("batchId", 82L);
        f.transactions.set(2, change(f.transactions.get(2), "batchId", 82L));
        assertThrows(ServiceException.class, f::read, "每笔历史出库仍须和所引用真实库存的批次完全一致");
    }

    /** 两次合法部分出库分别使用两个批次；行批次字段仅代表当前/下一次选择。 */
    private Fixture multiBatchPartialIssue() {
        Fixture f = new Fixture();
        f.stock("stockQuantity", q("2")); f.stock("reservedQuantity", q("0"));
        StockRow second = change(change(change(f.stocks.get(0), "stockId", 92L), "batchId", 82L), "frozenFlag", 1L);
        f.stocks.add(second);
        f.transactions.clear();
        f.transactions.add(rowTx(100L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600));
        f.transactions.add(change(change(rowTx(101L, "RECEIVE_IN", 1, "5", "0", "5", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600), "stockId", 92L), "batchId", 82L));
        f.transactions.add(rowTx(102L, "PRODUCTION_ISSUE", -1, "3", "5", "2", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -300));
        f.transactions.add(change(change(rowTx(103L, "PRODUCTION_ISSUE", -1, "3", "5", "2", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -200), "stockId", 92L), "batchId", 82L));
        f.receiveDetail("quantity", q("5"));
        f.receiveDetails.add(change(change(f.receiveDetails.get(0), "detailId", 522L), "batchId", 82L));
        f.issueHead("status", "PARTIAL");
        f.issueLine("issuedQty", q("6")); f.issueLine("shortQty", q("2")); f.issueLine("lineStatus", "PARTIAL");
        f.requirement("issuedQuantity", q("6"));
        f.snapshot = new InventorySnapshot(TENANT, MATERIAL, UNIT, 4, NOW, "+08:00", q("2"), q("0"), q("1"), q("2"), Set.of(91L, 92L),
            Map.of("wm_material_stock:91", "version=2;digest1", "wm_material_stock:92", "version=2;digest2", "bd_unit_of_measure:27", "conversionVersion=unit-v1;digest"));
        return f;
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
        f.transactions.replaceAll(row -> change(row, "locationId", null));
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
            bad("历史DRAFT头无工单来源绑定", f -> { f.requirementHead("status", "DRAFT"); f.workOrder("sourceId", null); }),
            bad("MRP头未知状态", f -> f.requirementHead("status", "CANCELLED")),
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
            head, line, null, related, time(seconds), "0", 8L, 18L, 28L, 81L, null);
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
        List<WorkOrder> workOrders = new ArrayList<>(List.of(new WorkOrder(801L, TENANT, "WO801", 901L, 1001L, 240L, "RELEASED", "0", "MATERIAL_REQUIREMENT", 711L)));
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
        void mixedCompleted(int target) {
            String amount = target == 2 ? "20" : "10";
            transactions.clear();
            transactions.add(rowTx(100L, "RECEIVE_IN", 1, amount, "0", amount, "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600));
            transactions.add(rowTx(103L + target, "PRODUCTION_ISSUE", -1, amount, amount, "0", "WM_PRODUCTION_ISSUE", 601L, 611L + target, null, -100));
            stock("stockQuantity", q("0")); stock("reservedQuantity", q("0"));
            receiveLine("quantityReceived", q(amount)); receiveDetail("quantity", q(amount));
            issueHead("totalQty", q("40")); issueHead("status", "COMPLETED");
            issueLines.clear(); requirements.clear();
            for (int i = 0; i < 3; i++) {
                Long material = i == target ? MATERIAL : 141L + i;
                String required = i == 2 ? "20" : "10";
                issueLines.add(new IssueLine(611L + i, TENANT, 601L, material, "27", 8L, 28L, i == target ? 81L : 82L + i,
                    q(required), q(required), q(required), q("0"), 701L + i, "ISSUED", "0"));
                requirements.add(new RequirementDetail(701L + i, TENANT, 711L, material, "27",
                    q(required), q(required), "PROCUREMENT", 1L, 0, "0"));
            }
            workOrder("woStatus", "COMPLETED"); summary(q("0"), q("0"), q("0"));
        }
        void misboundMixed(String scenario) {
            mixedCompleted(0); peerLine("batchId", null);
            if ("完成后真实零余额".equals(scenario)) {
                receiveLine("quantityReceived", q("20")); receiveDetail("quantity", q("20"));
                transactions.clear();
                transactions.add(rowTx(100L, "RECEIVE_IN", 1, "20", "0", "20", "WM_MATERIAL_RECEIVE", 501L, 511L, null, -600));
                transactions.add(rowTx(103L, "PRODUCTION_ISSUE", -1, "10", "20", "10", "WM_PRODUCTION_ISSUE", 601L, 611L, null, -100));
                transactions.add(rowTx(104L, "PRODUCTION_ISSUE", -1, "10", "10", "0", "WM_PRODUCTION_ISSUE", 601L, 612L, null, -90));
                nativePiece(); requirementHead("status", "DRAFT"); return;
            }
            issueLine("issuedQty", q("0")); issueLine("shortQty", q("10")); issueLine("lineStatus", "PENDING");
            requirement("issuedQuantity", q("0"));
            boolean consumed = "消费与配对出库".equals(scenario);
            peerLine("issuedQty", q(consumed ? "3" : "0")); peerLine("shortQty", q(consumed ? "7" : "10"));
            peerLine("lineStatus", consumed ? "PARTIAL" : "PENDING"); peerRequirement("issuedQuantity", q(consumed ? "3" : "0"));
            issueHead("status", "PARTIAL"); workOrder("woStatus", "IN_PROGRESS");
            transactions.remove(1);
            transactions.add(rowTx(101L, "RESERVE", 0, "预留".equals(scenario) ? "3" : "6", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 612L, null, -300));
            if ("预留及释放".equals(scenario)) transactions.add(rowTx(102L, "UNRESERVE", 0, "2", "10", "10", "WM_PRODUCTION_ISSUE", 601L, 612L, null, -200));
            if (consumed) {
                transactions.add(rowTx(103L, "CONSUME_RESERVE", 0, "3", "10", "7", "WM_PRODUCTION_ISSUE", 601L, 612L, 104L, -100));
                transactions.add(rowTx(104L, "PRODUCTION_ISSUE", -1, "3", "10", "7", "WM_PRODUCTION_ISSUE", 601L, 612L, null, -100));
            }
            BigDecimal balance = q(consumed ? "7" : "10"), reserved = q("预留及释放".equals(scenario) ? "4" : "3");
            stock("stockQuantity", balance); stock("reservedQuantity", reserved); nativePiece(); requirementHead("status", "DRAFT");
            snapshot = change(change(snapshot, "quantity", balance), "reserved", reserved);
        }
        void withSn(Long sn) {
            stock("snId", sn); receiveDetail("snId", sn); transactions.replaceAll(row -> change(row, "snId", sn));
        }
        void optionalDimensionAbsent(String field) {
            stock(field, null); receiveDetail(field, null); transactions.replaceAll(row -> change(row, field, null));
            if ("locationId".equals(field)) {
                receiveHead("locationId", null); issueLine("locationId", null);
                areas.set(0, change(areas.get(0), "locationFlag", 0L)); locations.clear();
            }
            if ("batchId".equals(field)) issueLine("batchId", null);
        }
        void peerLine(String name, Object value) { issueLines.set(1, change(issueLines.get(1), name, value)); }
        void peerRequirement(String name, Object value) { requirements.set(1, change(requirements.get(1), name, value)); }
        void nativePiece() {
            stock("baseUomId", "26"); receiveLine("baseUomId", "26"); receiveDetail("baseUomId", "26");
            unit = change(change(change(change(unit, "uomId", 26L), "primaryId", 26L), "quantityScale", 0), "category", "数量");
            transactions.replaceAll(row -> change(row, "baseUomId", "26"));
            issueLines.replaceAll(line -> change(line, "baseUomId", "26")); requirements.replaceAll(detail -> change(detail, "unit", "26"));
            snapshot = new InventorySnapshot(TENANT, MATERIAL, 26L, 0, NOW, "+08:00", q("0"), q("0"), q("0"), q("0"), Set.of(91L),
                Map.of("wm_material_stock:91", "version=2;digest", "bd_unit_of_measure:26", "conversionVersion=unit-v1;digest"));
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
