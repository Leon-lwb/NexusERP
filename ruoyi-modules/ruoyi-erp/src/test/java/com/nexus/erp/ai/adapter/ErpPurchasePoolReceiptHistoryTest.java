package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.*;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.*;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 已收货历史交叉认证及只计一次供给；仅离线来源夹具，不连接真实业务库。 */
class ErpPurchasePoolReceiptHistoryTest {
    private static final String TENANT = "000001";
    private static final Instant NOW = Instant.parse("2026-10-06T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Test void fullyReceivedAndIssuedOldDemandDoesNotBlockIndependentNewDemand() {
        var f = new Fixture("10", "10", "0", "RECEIVED");
        var input = assertDoesNotThrow(f::certify);
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        qty("6", result.allocations().get(0).requestableBase());
        qty("0", result.externalSupplyAllocatedBase());
    }

    @Test void receivedStockIsCountedOnceNotAgainAsFutureOrderSupply() {
        var f = new Fixture("10", "0", "10", "RECEIVED");
        var result = new ErpPurchaseDemandAllocator().allocate(assertDoesNotThrow(f::certify));
        qty("6", result.allocations().get(0).requestableBase());
        qty("10", result.externalSupplyAllocatedBase());
    }

    @Test void partialReceiptKeepsOnlyUnreceivedSupplyAndCurrentStock() {
        var f = new Fixture("4", "2", "2", "PARTIALLY_RECEIVED");
        var input = assertDoesNotThrow(f::certify);
        assertEquals(OrderStatus.PARTIALLY_RECEIVED, input.orders().get(0).status());
        qty("4", input.orders().get(0).receivedBase());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        qty("6", result.allocations().get(0).requestableBase());
        qty("8", result.externalSupplyAllocatedBase());
    }

    @Test void allocatorRecognizesFullyReceivedStateWithZeroOutstandingSupply() {
        var status = assertDoesNotThrow(() -> OrderStatus.valueOf("RECEIVED"));
        var scope = new Scope(TENANT, "10", "26", TODAY, TODAY.plusDays(10), true, Set.of("2"));
        var input = new AllocationInput(scope, new Stock(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "26", true),
            List.of(new Demand("1", TODAY, BigDecimal.ZERO, "26", true), new Demand("2", TODAY.plusDays(3), new BigDecimal("6"), "26", true)),
            List.of(new OrderLine("301", "a501", "1", status, TODAY, BigDecimal.TEN, BigDecimal.TEN, "26", true)), List.of());
        var result = assertDoesNotThrow(() -> new ErpPurchaseDemandAllocator().allocate(input));
        qty("6", result.allocations().get(0).requestableBase()); qty("0", result.externalSupplyAllocatedBase());
    }

    @Test void partialOrderHeadAllowsFullyReceivedSiblingLineWithoutFutureSupply() {
        var f = new Fixture("10", "10", "0", "PARTIALLY_RECEIVED");
        var input = assertDoesNotThrow(f::certify);
        assertEquals(OrderStatus.RECEIVED, input.orders().get(0).status());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        qty("6", result.allocations().get(0).requestableBase()); qty("0", result.externalSupplyAllocatedBase());
    }

    @Test void partialOrderHeadAllowsUnreceivedSiblingLineAsApprovedPendingSupply() {
        var f = new Fixture("0", "0", "0", "PARTIALLY_RECEIVED");
        f.fulfillment = null; f.order.setReceiveStatus("PENDING");
        var input = assertDoesNotThrow(f::certify);
        assertEquals(OrderStatus.APPROVED, input.orders().get(0).status());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        qty("6", result.allocations().get(0).requestableBase()); qty("10", result.externalSupplyAllocatedBase());
    }

    @Test void samePartialHeadFullAndUnreceivedRowsAreIndependentlyClassified() {
        var f = new Fixture("10", "10", "0", "PARTIALLY_RECEIVED"); f.addUnreceivedSibling();
        var input = assertDoesNotThrow(f::certify);
        assertEquals(List.of(OrderStatus.RECEIVED, OrderStatus.APPROVED), input.orders().stream().map(OrderLine::status).toList());
        var result = new ErpPurchaseDemandAllocator().allocate(input);
        qty("6", result.allocations().get(0).requestableBase()); qty("10", result.externalSupplyAllocatedBase());
    }

    @Test void rawReceiptMismatchCannotReuseIndependentFulfillment() {
        var f = new Fixture("10", "10", "0", "RECEIVED");
        f.order.setReceivedQuantity(new BigDecimal("9"));
        assertThrows(ServiceException.class, f::certify);
    }

    @Test void fullReceiptHeadCannotCoverPartialQuantity() {
        assertThrows(ServiceException.class, new Fixture("4", "2", "2", "RECEIVED")::certify);
    }

    @Test void approvedHeadCannotPretendPositiveReceiptIsVerified() {
        assertThrows(ServiceException.class, new Fixture("10", "10", "0", "APPROVED")::certify);
    }

    @Test void returnInvoiceAndUnknownHistoricalUnitStillReject() {
        for (String field : List.of("returned", "invoiced", "unit")) {
            var f = new Fixture("10", "10", "0", "RECEIVED");
            if (field.equals("returned")) f.order.setReturnedQuantity(BigDecimal.ONE);
            else if (field.equals("invoiced")) f.order.setInvoicedQty(BigDecimal.ONE);
            else f.order.setUnit(null);
            assertThrows(ServiceException.class, f::certify);
        }
    }

    @Test void originalSevenArgumentBindingCannotCertifyPositiveFulfillment() {
        var f = new Fixture("10", "10", "0", "RECEIVED"); f.fulfillment = null;
        assertThrows(ServiceException.class, f::certify);
    }

    @ParameterizedTest
    @ValueSource(strings = {"allocation", "order", "line", "material", "unit", "quantity", "summary", "missingVersion", "missingPostingVersion", "rehashedTamper", "evidenceMutation", "missing", "duplicate", "zero", "tooPrecise", "postingHash", "postingTime", "future"})
    void incorrectReceiptEvidenceRejectsWholePool(String kind) {
        var f = new Fixture("10", "10", "0", "RECEIVED");
        var p = f.fulfillment;
        var receipt = p.receipts().get(0);
        Long aid = kind.equals("allocation") ? 999L : p.allocationId();
        Long oid = kind.equals("order") ? 999L : p.orderId();
        Long lid = kind.equals("line") ? 999L : p.orderLineId();
        Long mid = kind.equals("material") ? 999L : p.materialId();
        Long uid = kind.equals("unit") ? 999L : p.baseUomId();
        BigDecimal total = kind.equals("quantity") ? new BigDecimal("9") : p.fulfilledBaseQty();
        String hash = kind.equals("summary") ? "3".repeat(64) : p.sourceFingerprint();
        List<ReceiptProof> receipts = p.receipts();
        if (kind.equals("missing")) receipts = List.of();
        if (kind.equals("duplicate")) receipts = List.of(receipt, receipt);
        if (Set.of("zero", "tooPrecise", "postingHash", "postingTime", "future").contains(kind)) {
            BigDecimal amount = kind.equals("zero") ? BigDecimal.ZERO : kind.equals("tooPrecise") ? new BigDecimal("10.00001") : receipt.quantity();
            String rh = kind.equals("postingHash") ? "4".repeat(64) : receipt.sourceFingerprint();
            String at = kind.equals("postingTime") ? "2026-02-30 08:59:00" : kind.equals("future") ? "2026-10-07 09:00:00" : receipt.postedAt();
            receipts = List.of(new ReceiptProof(receipt.receiveId(), receipt.receiveLineId(), amount, rh, at));
        }
        f.fulfillment = new ReceiptFulfillmentProof(aid, oid, lid, mid, uid, total, receipts, hash);
        if (kind.equals("missingVersion")) f.versions.remove("receipt-fulfillment:501");
        if (kind.equals("missingPostingVersion")) f.versions.remove("receipt-posting:601");
        if (kind.equals("rehashedTamper")) {
            f.fulfillment = new ReceiptFulfillmentProof(aid, oid, lid, mid, uid, total,
                List.of(new ReceiptProof(601L, 701L, new BigDecimal("9"), receipt.sourceFingerprint(), receipt.postedAt())), hash);
        }
        if (kind.equals("evidenceMutation")) {
            f.fulfillment = new ReceiptFulfillmentProof(aid, oid, lid, mid, uid, total,
                List.of(new ReceiptProof(601L, 701L, receipt.quantity(), receipt.sourceFingerprint(), "2026-10-06 08:58:00")), hash);
        }
        assertThrows(ServiceException.class, f::certify);
    }

    @Test void twoOriginalReceiptRowsMustSumExactlyToCumulativeFulfillment() {
        var f = new Fixture("10", "10", "0", "RECEIVED");
        f.fulfillment = withComputedFingerprint(new ReceiptFulfillmentProof(501L, 401L, 301L, 10L, 26L, BigDecimal.TEN,
            List.of(new ReceiptProof(601L, 701L, new BigDecimal("4"), "1".repeat(64), "2026-10-06 08:58:00"),
                new ReceiptProof(602L, 702L, new BigDecimal("6"), "3".repeat(64), "2026-10-06 08:59:00")), null));
        f.versions.put("receipt-fulfillment:501", f.fulfillment.sourceFingerprint());
        var result = new ErpPurchaseDemandAllocator().allocate(assertDoesNotThrow(f::certify));
        qty("6", result.allocations().get(0).requestableBase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "4", "11"})
    void fullyReceivedAllocatorStateRejectsIncompleteOrExcessReceipt(String amount) {
        var scope = new Scope(TENANT, "10", "26", TODAY, TODAY.plusDays(10), true, Set.of("2"));
        var input = new AllocationInput(scope, new Stock(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "26", true),
            List.of(new Demand("1", TODAY, BigDecimal.ZERO, "26", true), new Demand("2", TODAY.plusDays(3), new BigDecimal("6"), "26", true)),
            List.of(new OrderLine("301", "a501", "1", OrderStatus.RECEIVED, TODAY, BigDecimal.TEN, new BigDecimal(amount), "26", true)), List.of());
        assertThrows(AllocationRejectedException.class, () -> new ErpPurchaseDemandAllocator().allocate(input));
    }

    private static final class Fixture {
        private final List<RawRequirementDetail> details = new ArrayList<>();
        private final List<RawRequirementHead> heads = new ArrayList<>();
        private final RawRequisitionLine application = new RawRequisitionLine();
        private final RawRequisitionHead applicationHead = new RawRequisitionHead();
        private final RawOrderLine order = new RawOrderLine();
        private final RawOrderHead orderHead = new RawOrderHead();
        private final PuRequirementAllocationLedger allocation = new PuRequirementAllocationLedger();
        private final BigDecimal received;
        private final BigDecimal issued;
        private final BigDecimal stock;
        private final String state;
        private ReceiptFulfillmentProof fulfillment;
        private final Map<String, String> versions = new HashMap<>();
        private final List<RawOrderLine> siblingOrders = new ArrayList<>();
        private final List<RawRequisitionLine> siblingApplications = new ArrayList<>();
        private final List<PuRequirementAllocationLedger> siblingAllocations = new ArrayList<>();
        private final List<AllocationBinding> siblingBindings = new ArrayList<>();

        Fixture(String received, String issued, String stock, String state) {
            this.received = new BigDecimal(received); this.issued = new BigDecimal(issued);
            this.stock = new BigDecimal(stock); this.state = state;
            demand(1L, "10", issued, TODAY.plusDays(1));
            demand(2L, "6", "0", TODAY.plusDays(3));
            application.setLineId(101L); application.setRequisitionId(201L); application.setTenantId(TENANT);
            application.setMaterialId(10L); application.setUnit("26"); application.setApplyQuantity(BigDecimal.TEN);
            application.setApprovedQuantity(BigDecimal.TEN); application.setSourceType("MRP"); application.setSourceId(1L); application.setDelFlag("0");
            applicationHead.setRequisitionId(201L); applicationHead.setTenantId(TENANT); applicationHead.setStatus("ORDERED");
            applicationHead.setVersion(2); applicationHead.setDelFlag("0");
            order.setLineId(301L); order.setOrderId(401L); order.setRequisitionLineId(101L); order.setTenantId(TENANT);
            order.setMaterialId(10L); order.setUnit("26"); order.setOrderQuantity(BigDecimal.TEN);
            order.setReceivedQuantity(this.received); order.setReceiveStatus(this.received.compareTo(BigDecimal.TEN) == 0 ? "FULL_RECEIVED" : "PARTIAL_RECEIVED"); order.setReturnedQuantity(BigDecimal.ZERO); order.setInvoicedQty(BigDecimal.ZERO); order.setDelFlag("0");
            orderHead.setOrderId(401L); orderHead.setTenantId(TENANT); orderHead.setRequisitionId(201L); orderHead.setVendorId(30L);
            orderHead.setStatus(state); orderHead.setExpectedArrivalDate(date(TODAY.plusDays(1))); orderHead.setDelFlag("0");
            allocation.setAllocationId(501L); allocation.setTenantId(TENANT); allocation.setMaterialId(10L);
            allocation.setBaseUomId(26L); allocation.setDetailId(1L); allocation.setRequisitionLineId(101L); allocation.setOrderLineId(301L);
            allocation.setAllocationStatus("ORDER_COMMITTED_NOT_TIMELY_SUPPLY"); allocation.setAllocationKey("a501"); allocation.setVersion(4);
            allocation.setCoveredDemandBaseQty(BigDecimal.TEN); allocation.setOccupiedBaseQty(BigDecimal.TEN.subtract(this.received));
            allocation.setFulfilledBaseQty(this.received); allocation.setReleasedBaseQty(BigDecimal.ZERO);
            fulfillment = proof();
            versions.put("receipt-fulfillment:501", fulfillment.sourceFingerprint());
            versions.put("receipt-posting:601", "1".repeat(64)); versions.put("receipt-posting:602", "3".repeat(64));
        }

        private void demand(long id, String required, String issued, LocalDate due) {
            var row = new RawRequirementDetail(); row.setDetailId(id); row.setRequirementId(id + 1000);
            row.setTenantId(TENANT); row.setMaterialId(10L); row.setStatus(1L); row.setDelFlag("0"); row.setUnit("26");
            row.setRequiredQuantity(new BigDecimal(required)); row.setIssuedQuantity(new BigDecimal(issued)); row.setRequiredDate(date(due)); details.add(row);
            var head = new RawRequirementHead(); head.setRequirementId(id + 1000); head.setTenantId(TENANT);
            head.setStatus("CONFIRMED"); head.setDelFlag("0"); heads.add(head);
        }

        private AllocationBinding binding() {
            var ledger = new AllocationSource(501L, TENANT, 10L, "MRP_DETAIL", 1L, 101L, 1, 301L, 26L,
                BigDecimal.TEN, BigDecimal.TEN.subtract(received), received, BigDecimal.ZERO,
                "ORDER_COMMITTED_NOT_TIMELY_SUPPLY", "a501", "p501", 4, NOW.minusSeconds(120), NOW.minusSeconds(60), null, null, NOW);
            var detail = new RequirementDetailSource(1L, TENANT, 1001L, 10L, "26", "PROCUREMENT", BigDecimal.TEN, issued,
                stock, BigDecimal.ZERO, date(TODAY.plusDays(1)).toInstant(), 1L, "0", 0, issued, NOW.minusSeconds(120), NOW);
            var reqHead = new RequirementHeadSource(1001L, TENANT, "MRP1001", null, 2001L, "PLAN2001", 3001L, 100L,
                NOW.minusSeconds(120), "PLAN", "CONFIRMED", null, null, null, "0", NOW.minusSeconds(120), NOW);
            var reqLine = new RequisitionLineSource(101L, TENANT, 201L, 10L, "26", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                date(TODAY.plusDays(1)).toInstant(), "MRP", 1L, null, null, null, null, "0", NOW.minusSeconds(120), NOW);
            var purchaseHead = new RequisitionHeadSource(201L, TENANT, "REQ201", "MRP", "ORDERED", NOW.minusSeconds(120),
                date(TODAY.plusDays(1)).toInstant(), null, null, "p501", null, 2, "0", NOW.minusSeconds(120), NOW);
            var poLine = new OrderLineSource(301L, TENANT, 401L, 101L, 10L, "26", BigDecimal.TEN, received,
                BigDecimal.ZERO, BigDecimal.ZERO, received.signum() == 0 ? "PENDING" : received.compareTo(BigDecimal.TEN) == 0 ? "FULL_RECEIVED" : "PARTIAL_RECEIVED", "0", NOW.minusSeconds(120), NOW);
            var poHead = new OrderHeadSource(401L, TENANT, "PO401", 201L, 30L, state, NOW.minusSeconds(120),
                date(TODAY.plusDays(1)).toInstant(), "0", NOW.minusSeconds(120), NOW);
            return fulfillment == null ? new AllocationBinding(ledger, detail, reqHead, reqLine, purchaseHead, poLine, poHead)
                : new AllocationBinding(ledger, detail, reqHead, reqLine, purchaseHead, poLine, poHead, fulfillment);
        }

        private ReceiptFulfillmentProof proof() {
            return withComputedFingerprint(new ReceiptFulfillmentProof(501L, 401L, 301L, 10L, 26L, received,
                List.of(new ReceiptProof(601L, 701L, received, "1".repeat(64), "2026-10-06 08:59:00")), null));
        }

        private void addUnreceivedSibling() {
            demand(3L, "10", "0", TODAY.plusDays(1));
            var app = new RawRequisitionLine(); app.setLineId(102L); app.setRequisitionId(201L); app.setTenantId(TENANT); app.setMaterialId(10L);
            app.setUnit("26"); app.setApplyQuantity(BigDecimal.TEN); app.setApprovedQuantity(BigDecimal.TEN); app.setSourceType("MRP"); app.setSourceId(3L); app.setDelFlag("0");
            siblingApplications.add(app);
            var po = new RawOrderLine(); po.setLineId(302L); po.setOrderId(401L); po.setRequisitionLineId(102L); po.setTenantId(TENANT); po.setMaterialId(10L);
            po.setUnit("26"); po.setOrderQuantity(BigDecimal.TEN); po.setReceivedQuantity(BigDecimal.ZERO); po.setReturnedQuantity(BigDecimal.ZERO);
            po.setInvoicedQty(BigDecimal.ZERO); po.setReceiveStatus("PENDING"); po.setDelFlag("0"); siblingOrders.add(po);
            var a = new PuRequirementAllocationLedger(); a.setAllocationId(502L); a.setTenantId(TENANT); a.setMaterialId(10L); a.setBaseUomId(26L);
            a.setDetailId(3L); a.setRequisitionLineId(102L); a.setOrderLineId(302L); a.setAllocationStatus("ORDER_COMMITTED_NOT_TIMELY_SUPPLY");
            a.setAllocationKey("a502"); a.setVersion(3); a.setCoveredDemandBaseQty(BigDecimal.TEN); a.setOccupiedBaseQty(BigDecimal.TEN);
            a.setFulfilledBaseQty(BigDecimal.ZERO); a.setReleasedBaseQty(BigDecimal.ZERO); siblingAllocations.add(a);
            var old = binding();
            var ledger = new AllocationSource(502L, TENANT, 10L, "MRP_DETAIL", 3L, 102L, 1, 302L, 26L, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, "ORDER_COMMITTED_NOT_TIMELY_SUPPLY", "a502", "p501", 3, NOW.minusSeconds(120), NOW.minusSeconds(60), null, null, NOW);
            var detail = new RequirementDetailSource(3L, TENANT, 1003L, 10L, "26", "PROCUREMENT", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, date(TODAY.plusDays(1)).toInstant(), 1L, "0", 0, BigDecimal.ZERO, NOW.minusSeconds(120), NOW);
            var head = new RequirementHeadSource(1003L, TENANT, "MRP1003", null, 2003L, "PLAN2003", 3001L, 100L, NOW.minusSeconds(120),
                "PLAN", "CONFIRMED", null, null, null, "0", NOW.minusSeconds(120), NOW);
            var req = new RequisitionLineSource(102L, TENANT, 201L, 10L, "26", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                date(TODAY.plusDays(1)).toInstant(), "MRP", 3L, null, null, null, null, "0", NOW.minusSeconds(120), NOW);
            var order = new OrderLineSource(302L, TENANT, 401L, 102L, 10L, "26", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "PENDING", "0", NOW.minusSeconds(120), NOW);
            siblingBindings.add(new AllocationBinding(ledger, detail, head, req, old.requisitionHead(), order, old.orderHead()));
        }

        private AllocationInput certify() {
            var unit = new TrustedUnit(TENANT, "26", "26", "数量", BigDecimal.ONE, "v1", 0, NOW.minusSeconds(300), true);
            var catalog = new CatalogSnapshot(TENANT, "20", "30", NOW, "+08:00", new Material(TENANT, "10", "26", true),
                new SupplierTerms(TENANT, "10", "30", "26", "26", BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE,
                    "t1", NOW.minusSeconds(300), NOW.plusSeconds(3600), NOW.minusSeconds(300), true), unit, unit, unit, Map.of());
            var bindings = new ArrayList<>(List.of(binding())); bindings.addAll(siblingBindings);
            var orders = new ArrayList<>(List.of(order)); orders.addAll(siblingOrders);
            var applications = new ArrayList<>(List.of(application)); applications.addAll(siblingApplications);
            var allocations = new ArrayList<>(List.of(allocation)); allocations.addAll(siblingAllocations);
            var lineage = new LineageSnapshot(TENANT, 10L, 26L, NOW, "+08:00", bindings, versions);
            var raw = new RawLockedFacts(details, heads, List.of(), orders, List.of(orderHead), applications, List.of(applicationHead), allocations);
            return ErpPurchasePoolCertification.certify(catalog, lineage, raw, new Stock(stock, BigDecimal.ZERO, BigDecimal.ZERO, "26", true),
                TODAY.plusDays(10), Set.of(2L));
        }
    }

    /** 这里只复算离线夹具摘要，不代表真实过账或任何外部签名。 */
    private static ReceiptFulfillmentProof withComputedFingerprint(ReceiptFulfillmentProof proof) {
        return new ReceiptFulfillmentProof(proof.allocationId(), proof.orderId(), proof.orderLineId(), proof.materialId(), proof.baseUomId(),
            proof.fulfilledBaseQty(), proof.receipts(), ErpPurchaseAllocationReceiptSourceReader.fulfillmentFingerprint(proof));
    }

    private static Date date(LocalDate day) { return Date.from(day.atStartOfDay(ZoneOffset.ofHours(8)).toInstant()); }
    private static void qty(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
