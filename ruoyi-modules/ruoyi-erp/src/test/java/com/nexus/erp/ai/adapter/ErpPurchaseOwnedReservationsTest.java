package com.nexus.erp.ai.adapter;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.*;
import static org.junit.jupiter.api.Assertions.*;

/** 已核本需求预留不能被既减供给又保留完整需求而重复采购。 */
class ErpPurchaseOwnedReservationsTest {
    private static final LocalDate NOW=LocalDate.of(2026,10,2),DUE=NOW.plusDays(1);
    private static BigDecimal q(String n){return new BigDecimal(n);}
    private static AllocationInput input(Map<String,BigDecimal> owned) {
        return new AllocationInput(new Scope("000000","139","4",NOW,DUE,true,Set.of("1")),
            new Stock(q("10"),q("6"),q("0"),"4",true),
            List.of(new Demand("1",DUE,q("10"),"4",true),new Demand("2",NOW,q("4"),"4",true)),List.of(),List.of(),owned);
    }
    @Test void verifiedOwnReserveIsDedicatedSupplyAndNotAnAdditionalShortage() {
        var result=new ErpPurchaseDemandAllocator().allocate(input(Map.of("1",q("6"))));
        var own=result.allocations().get(0);
        assertEquals(0,own.stockAllocatedBase().compareTo(q("6")));
        assertEquals(0,own.requestableBase().compareTo(q("4")));
        assertEquals(0,result.externalSupplyAllocatedBase().compareTo(q("4")));
        assertEquals(0,own.netDemandBase().compareTo(q("10")));
    }
    @Test void reserveOwnerMissingFromAuthenticatedPoolIsRejected() {
        assertThrows(AllocationRejectedException.class,()->new ErpPurchaseDemandAllocator().allocate(input(Map.of("99",q("6")))));
    }
    @Test void ownershipCannotExceedTotalReservedQuantity() {
        assertThrows(AllocationRejectedException.class,()->new ErpPurchaseDemandAllocator().allocate(input(Map.of("1",q("7")))));
    }
    @Test void ownershipCannotExceedRemainingDemand() {
        assertThrows(AllocationRejectedException.class,()->new ErpPurchaseDemandAllocator().allocate(input(Map.of("2",q("6")))));
    }
    @Test void knownOtherOwnershipNeverSharesItsReserveWithRequestedDemand() {
        var result=new ErpPurchaseDemandAllocator().allocate(input(Map.of("2",q("4"))));
        assertEquals(0,result.allocations().get(0).requestableBase().compareTo(q("6")));
    }
    @Test void draftWithoutEtaOccupiesOriginalDemandButIsNeverTimelySupply() {
        var base=input(Map.of());
        var value=new AllocationInput(base.scope(),new Stock(q("0"),q("0"),q("0"),"4",true),base.demands(),
            List.of(new OrderLine("100","original-source","1",OrderStatus.DRAFT,null,q("6"),q("0"),"4",true)),List.of(),Map.of());
        var own=new ErpPurchaseDemandAllocator().allocate(value).allocations().get(0);
        assertEquals(0,own.timelyOrderAllocatedBase().signum());assertEquals(0,own.committedBase().compareTo(q("6")));
        assertEquals(0,own.requestableBase().compareTo(q("4")));
    }

}
