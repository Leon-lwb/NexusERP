package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.*;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Stock;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.*;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 原始锁定事实与独立归属交叉核验；没有真实数据库或正式单据写入。 */
class ErpPurchasePoolCertificationTest {
    private static final String TENANT = "000001";
    private static final Instant NOW = Instant.parse("2026-10-02T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private final List<RawRequirementDetail> details = new ArrayList<>();
    private final List<RawRequirementHead> heads = new ArrayList<>();
    private final List<RawRequisitionLine> applications = new ArrayList<>();
    private final List<RawRequisitionHead> applicationHeads = new ArrayList<>();

    @Test void independentWorkOrdersCompeteBeforeRequestedSubset() {
        demand(1L, TODAY.plusDays(2), "10", "2"); demand(2L, TODAY.plusDays(2), "8", "0");
        var input = certify(Set.of(2L));
        assertEquals(2, input.demands().size());
        assertEquals(Set.of("2"), input.scope().requestedDetailIds());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        assertEquals(1, result.allocations().size());
        qty("6", result.allocations().get(0).requestableBase());
        qty("8", result.externalSupplyAllocatedBase());
    }

    @Test void originalOverdueDemandIsPreservedNotShiftedToToday() {
        demand(1L, TODAY.minusDays(4), "8", "0"); demand(2L, TODAY.plusDays(2), "8", "0");
        var input = certify(Set.of(1L));
        assertEquals(TODAY.minusDays(4), input.demands().get(0).dueDate());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        assertTrue(result.allocations().get(0).originallyOverdue());
        qty("8", result.allocations().get(0).netDemandBase());
        qty("0", result.allocations().get(0).requestableBase());
    }

    @Test void historicalDstCannotChangeVerifiedFixedOffsetDatabaseWallDate() {
        var row = demand(1L, LocalDate.of(1987,7,15), "8", "0");
        // MySQL 会话 +08:00 的 DATETIME 1987-07-15 23:30，UNIX_TIMESTAMP 对应以下 epoch。
        row.setRequiredDate(Date.from(Instant.parse("1987-07-15T15:30:00Z")));
        var input = certify(Set.of(1L));
        assertEquals(LocalDate.of(1987,7,15),input.demands().get(0).dueDate(),
            "历史地区夏令时不能改变数据库声明的固定 +08:00 业务日期");
    }

    @Test void netDemandUsesIssuedAndNeverHistoricalShortageField() {
        var row = demand(1L, TODAY, "10", "3"); row.setShortageQuantity(new BigDecimal("999"));
        var input = certify(Set.of(1L));
        qty("7", input.demands().get(0).netDemandBase());
        assertTrue(input.scope().globalPoolComplete());
    }

    @Test void displayedOrEmptyMrpUnitCannotCertifyBaseQuantity() {
        for (String unit : Arrays.asList(null, "", "件", "026", "27")) {
            details.clear(); heads.clear(); demand(1L, TODAY, "8", "0").setUnit(unit);
            assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
        }
    }

    @Test void nullIssuedOrOversizedPrecisionCannotBecomeZero() {
        for (BigDecimal invalid : Arrays.asList(null, new BigDecimal("-1"), new BigDecimal("0.00001"), new BigDecimal("999999999999999"))) {
            details.clear(); heads.clear(); demand(1L, TODAY, "8", "0").setIssuedQuantity(invalid);
            assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
        }
    }

    @Test void duplicateOrWrongTenantDemandFailsEntirePool() {
        var row = demand(1L, TODAY, "8", "0"); details.add(row);
        assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
        details.remove(1); row.setTenantId("000002");
        assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
    }

    @Test void requestedSourceMustExistInWholeWindow() {
        demand(1L, TODAY, "8", "0");
        assertThrows(ServiceException.class, () -> certify(Set.of(2L)));
    }

    @Test void closedHeadCannotHideOutstandingMaterialDemand() {
        demand(1L, TODAY, "8", "0"); heads.get(0).setStatus("CLOSED");
        assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
    }

    @Test void knownFulfilledHeadRemainsZeroDemand() {
        demand(1L, TODAY, "8", "8"); heads.get(0).setStatus("CLOSED");
        var input = certify(Set.of(1L)); qty("0", input.demands().get(0).netDemandBase());
    }

    @Test void oldUnitlessApplication510IsNeverUpgradedByEmptyLineage() {
        demand(1L, TODAY, "8", "0");
        var line = new RawRequisitionLine(); line.setLineId(101L); line.setTenantId(TENANT);
        line.setMaterialId(10L); line.setRequisitionId(201L); line.setApplyQuantity(new BigDecimal("510"));
        line.setUnit(null); line.setDelFlag("0"); applications.add(line);
        var head = new RawRequisitionHead(); head.setRequisitionId(201L); head.setTenantId(TENANT);
        head.setStatus("DRAFT"); head.setVersion(0); head.setDelFlag("0"); applicationHeads.add(head);
        assertThrows(ServiceException.class, () -> certify(Set.of(1L)));
        line.setUnit("26");
        assertThrows(ServiceException.class, () -> certify(Set.of(1L)), "有单位但无独立占用归属也不能升级");
    }

    private com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.AllocationInput certify(Set<Long> requested) {
        var unit = new TrustedUnit(TENANT, "26", "26", "数量", BigDecimal.ONE, "v1", 4, NOW.minusSeconds(60), true);
        var catalog = new CatalogSnapshot(TENANT, "20", "30", NOW, "+08:00",
            new Material(TENANT,"10","26",true),
            new SupplierTerms(TENANT,"10","30","26","26",BigDecimal.ONE,BigDecimal.ONE,true,BigDecimal.ONE,
                "t1",NOW.minusSeconds(60),NOW.plusSeconds(3600),NOW.minusSeconds(60),true),
            unit,unit,unit,Map.of("terms:30","t1","unit:26","v1"));
        var lineage = new LineageSnapshot(TENANT,10L,26L,NOW,"+08:00",List.of(),Map.of());
        var raw = new RawLockedFacts(details,heads,List.of(),List.of(),List.of(),applications,applicationHeads,List.of());
        return ErpPurchasePoolCertification.certify(catalog,lineage,raw,
            new Stock(new BigDecimal("10"),BigDecimal.ZERO,BigDecimal.ZERO,"26",true),TODAY.plusDays(14),requested);
    }

    private RawRequirementDetail demand(long id, LocalDate due, String required, String issued) {
        var row = new RawRequirementDetail(); row.setDetailId(id); row.setRequirementId(id+1000);
        row.setTenantId(TENANT); row.setMaterialId(10L); row.setDelFlag("0"); row.setStatus(1L);
        row.setUnit("26"); row.setRequiredQuantity(new BigDecimal(required)); row.setIssuedQuantity(new BigDecimal(issued));
        row.setRequiredDate(Date.from(due.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant())); details.add(row);
        var head = new RawRequirementHead(); head.setRequirementId(id+1000); head.setTenantId(TENANT);
        head.setStatus("CONFIRMED"); head.setDelFlag("0"); head.setPlanId(id+2000); heads.add(head);
        return row;
    }

    private static void qty(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
}
