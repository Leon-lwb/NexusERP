package com.nexus.erp.pu;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper.OrderHead;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Quote;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Confirmation;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderReadService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 原生读取的隔离合同；替身数据不代表MySQL查询、历史订单认证或真实报价。 */
class PuCertifiedPurchaseOrderNativeReadTest {
    @Test void nativeNullVersionIsKeptAndCertifiedHeadCannotUseLegacyEdit() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000", 801L)).thenReturn(head("000000", 801L, null, true));
        var d = f.service.readDetail(801L);
        assertNull(d.order().version());
        assertFalse(d.genericEditAllowedHint());
        assertFalse(d.priceVisible());
        assertTrue(d.quotes().isEmpty());
        verify(f.mapper, never()).readQuotes(any(), any());
        verify(f.mapper, never()).readConfirmations(any(), any());
    }
    @Test void explicitlyOrdinaryLegacyHeadCanKeepItsExistingEditPath() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000", 801L)).thenReturn(new OrderHead("000000",801L,"CGDD1",null,701L,
            "DRAFT",null,null,null,null,null,null,null,false));
        assertTrue(f.service.readDetail(801L).genericEditAllowedHint());
    }
    @Test void legacyLookingHeadWithNativeAllocationCannotGetOrdinaryEditHint() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000",801L)).thenReturn(new OrderHead("000000",801L,"CGDD1",null,701L,
            "DRAFT",null,null,null,null,null,null,null,false));
        when(f.mapper.readAllocations("000000",801L)).thenReturn(List.of(
            new PuCertifiedPurchaseOrderReadMapper.AllocationView(1001L,601L,602L,"ORDER_COMMITTED_NOT_TIMELY_SUPPLY",2)));
        assertFalse(f.service.readDetail(801L).genericEditAllowedHint());
    }
    @Test void originalDecimalMoneyIsReturnedAsTextWithoutFrontendNumberConversion() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",801L,901L)));
        var q = f.service.readDetail(801L).quotes().get(0);
        assertEquals("99999999999999.1234",q.unitPriceExcludingTax());
        assertEquals("0.1300",q.taxRate());
        assertEquals("12.0000",q.quantity());
        assertNull(q.confirmationId());
    }
    @Test void headFromAnotherTenantCannotBeShown() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000",801L)).thenReturn(head("999999",801L,0,true));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
        verify(f.mapper, never()).databaseDate();
    }
    @Test void headFromAnotherOrderCannotBeShown() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000",801L)).thenReturn(head("000000",802L,0,true));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void missingHeadCannotBeReplacedWithAnEmptyDefaultDocument() {
        var f = new Fixture(false);
        when(f.mapper.readHead("000000",801L)).thenReturn(null);
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void foreignQuoteOrderIsRejected() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",802L,901L)));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void foreignQuoteTenantIsRejected() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("999999",801L,901L)));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void duplicateQuoteIdentityCannotBeShownAsTwoIndependentVersions() {
        var f = new Fixture(true);var q=quote("000000",801L,901L);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(q,q));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void quoteOverflowCannotBeSilentlyTruncatedIntoLatestQuote() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(Collections.nCopies(101,quote("000000",801L,901L)));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void confirmationRequiresItsOwnQuoteAndDifferentActor() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",801L,901L)));
        when(f.mapper.readConfirmations("000000",801L)).thenReturn(List.of(confirm(901L,901L,"1".repeat(64))));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void confirmationCannotBeMovedFromAnotherQuote() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",801L,901L)));
        when(f.mapper.readConfirmations("000000",801L)).thenReturn(List.of(confirm(999L,902L,"1".repeat(64))));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void confirmationHashMustMatchImmutableQuote() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",801L,901L)));
        when(f.mapper.readConfirmations("000000",801L)).thenReturn(List.of(confirm(901L,902L,"2".repeat(64))));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @Test void validConfirmationIsOnlyDisplayedNotExecuted() {
        var f = new Fixture(true);
        when(f.mapper.readQuotes("000000",801L)).thenReturn(List.of(quote("000000",801L,901L)));
        when(f.mapper.readConfirmations("000000",801L)).thenReturn(List.of(confirm(901L,902L,"1".repeat(64))));
        assertEquals(902L,f.service.readDetail(801L).quotes().get(0).confirmedBy());
        verify(f.workflow, never()).approve(any(),any(),any(),any());
    }
    @Test void actorOrPricePermissionChangeDuringReadIsNotAppliedToNewScope() {
        var f = new Fixture(false);
        when(f.workflow.currentWorkflowContext()).thenReturn(context(false),context(true));
        assertThrows(ServiceException.class,()->f.service.readDetail(801L));
    }
    @ParameterizedTest @ValueSource(longs = {0,-1})
    void invalidOrderIdentityIsRejectedBeforeMapper(Long id) {
        var f = new Fixture(false);
        assertThrows(ServiceException.class,()->f.service.readDetail(id));
        verifyNoInteractions(f.mapper);
    }

    static OrderHead head(String tenant,Long id,Integer version,boolean source) {
        return new OrderHead(tenant,id,"CGDD1",601L,701L,"DRAFT",version,null,null,"AI_DECISION",501L,502L,"original-source",source);
    }
    static Quote quote(String tenant,Long order,Long actor) {
        return new Quote(901L,tenant,order,1,"original-quote","3".repeat(64),actor,new Date(0),701L,201L,301L,
            "original-source","4".repeat(64),"5".repeat(64),"6".repeat(64),new BigDecimal("12.0000"),
            new BigDecimal("99999999999999.1234"),new BigDecimal("0.1300"),"CNY",BigDecimal.ONE,
            "2026-10-12","2026-10-01","2026-10-20","QUOTE-1","采购资料联系人","attachment:1","7".repeat(64),"Net 30","实约交货","policy","1".repeat(64));
    }
    static Confirmation confirm(Long quote,Long actor,String hash) {
        return new Confirmation(1001L,"000000",quote,hash,"confirm-1","8".repeat(64),actor,
            "pu:order:terms:confirm",new Date(0),"已依据实际材料复核");
    }
    static WorkflowContext context(boolean price) {
        return new WorkflowContext("000000",901L,false,false,false,false,false,price);
    }
    static class Fixture {
        final PuCertifiedPurchaseOrderWorkflowService workflow=mock(PuCertifiedPurchaseOrderWorkflowService.class);
        final PuCertifiedPurchaseOrderReadMapper mapper=mock(PuCertifiedPurchaseOrderReadMapper.class);
        final PuCertifiedPurchaseOrderReadService service=new PuCertifiedPurchaseOrderReadService(workflow,mapper);
        Fixture(boolean price) {
            when(workflow.currentWorkflowContext()).thenReturn(context(price));
            when(mapper.readHead("000000",801L)).thenReturn(head("000000",801L,0,true));
            when(mapper.readQuotes("000000",801L)).thenReturn(List.of());
            when(mapper.readConfirmations("000000",801L)).thenReturn(List.of());
            when(mapper.databaseDate()).thenReturn("2026-10-09");
        }
    }
}
