package com.nexus.erp.ai.adapter;

import static com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpPurchaseAllocationReceiptSourceMapper;
import com.nexus.erp.ai.adapter.mapper.ErpPurchaseAllocationReceiptSourceMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** 所有来源和永久重放均为离线替身，不创建数据源或连接外部服务。 */
class ErpPurchaseAllocationReceiptSourceReaderTest {
    static final String T = "000000", ORDER = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    static BigDecimal q(String value) { return new BigDecimal(value).setScale(4); }
    static final String FP = "a".repeat(64);
    static final Instant AT = Instant.parse("2026-10-05T06:23:31Z");

    @ParameterizedTest
    @ValueSource(strings = {"4", "10"})
    void certifiesActualPartialOrCompleteReceiptByPermanentReplayAndMatchingEvents(String received) {
        Fixture f = new Fixture(received);
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn(T);
            var result = assertDoesNotThrow(() -> f.reader.readLocked(T, f.allocation, f.orderLine, f.orderHead));
            var proof = result.proof();
            assertEquals(q(received), proof.fulfilledBaseQty());
            assertEquals(71L, proof.allocationId()); assertEquals(81L, proof.orderId());
            assertEquals(82L, proof.orderLineId()); assertEquals(140L, proof.materialId()); assertEquals(26L, proof.baseUomId());
            assertEquals(List.of(new ReceiptProof(91L, 92L, q(received), FP, "2026-10-05 14:23:31")), proof.receipts());
            assertEquals(proof.sourceFingerprint(), result.sourceVersions().get("receipt-fulfillment:71"));
            assertEquals(FP, result.sourceVersions().get("receipt-posting:91"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_material_receive:91"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_material_receive_line:92"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_certified_receipt_posting:91"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:pu_requirement_allocation_event:104"));
            assertThrows(UnsupportedOperationException.class, () -> proof.receipts().clear());
            verify(f.provider).getObject(); verify(f.posting).readPostedLocked(T, 91L);
            verify(f.posting, never()).post(anyLong()); verify(f.posting, never()).validatePostedLocked(anyString(), anyLong());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-head", "orphan-line", "deleted-head", "deleted-line", "unknown-head",
        "wrong-order", "wrong-material", "wrong-tenant", "wrong-source-line", "missing-posting", "duplicate-line",
        "posting-quantity", "posting-fingerprint", "posting-future", "posting-tenant", "event-missing", "event-gap",
        "event-key", "event-quantity", "event-actor", "event-order-line", "event-allocation", "event-future",
        "event-before-posting", "event-unknown", "version", "fulfilled", "received", "returned", "invoiced", "closed",
        "clock-zone", "provider-unavailable", "replay-fingerprint", "replay-order", "replay-false"})
    void refusesAnyMissingContradictoryOrUnsupportedOriginalFact(String failure) {
        Fixture f = new Fixture("4");
        switch (failure) {
            case "missing-head" -> f.heads = List.of();
            case "orphan-line" -> f.lines = List.of(replace(f.lines.get(0), "receiveId", 999L));
            case "deleted-head" -> f.heads = List.of(replace(f.heads.get(0), "delFlag", "1"));
            case "deleted-line" -> f.lines = List.of(replace(f.lines.get(0), "delFlag", "1"));
            case "unknown-head" -> f.heads = List.of(replace(f.heads.get(0), "status", "UNKNOWN"));
            case "wrong-order" -> f.heads = List.of(replace(f.heads.get(0), "orderId", 999L));
            case "wrong-material" -> f.lines = List.of(replace(f.lines.get(0), "materialId", 999L));
            case "wrong-tenant" -> f.lines = List.of(replace(f.lines.get(0), "tenantId", "000002"));
            case "wrong-source-line" -> f.lines = List.of(replace(f.lines.get(0), "sourceDocLineId", 999L));
            case "missing-posting" -> f.postings = List.of();
            case "duplicate-line" -> f.lines = List.of(f.lines.get(0), f.lines.get(0));
            case "posting-quantity" -> f.postings = List.of(replace(f.postings.get(0), "totalQuantity", q("5")));
            case "posting-fingerprint" -> f.postings = List.of(replace(f.postings.get(0), "sourceFingerprint", "not-a-digest"));
            case "posting-future" -> f.postings = List.of(replace(f.postings.get(0), "postedAt", "2027-10-05 14:23:31"));
            case "posting-tenant" -> f.postings = List.of(replace(f.postings.get(0), "tenantId", "000002"));
            case "event-missing" -> f.events = f.events.subList(0,4);
            case "event-gap" -> f.changeEvent("eventSequence", 5);
            case "event-key" -> f.changeEvent("eventKey", "LIFECYCLE:" + "b".repeat(64));
            case "event-quantity" -> f.changeEvent("occupiedAfter", q("5"));
            case "event-actor" -> f.changeEvent("actorId", 2L);
            case "event-order-line" -> f.changeEvent("orderLineId", 999L);
            case "event-allocation" -> f.changeEvent("allocationId", 999L);
            case "event-future" -> f.changeEvent("occurredAt", BigDecimal.valueOf(AT.plusSeconds(300).toEpochMilli()));
            case "event-before-posting" -> f.changeEvent("occurredAt", BigDecimal.valueOf(AT.minusSeconds(1).toEpochMilli()));
            case "event-unknown" -> f.changeEvent("eventType", "RETURN_POSTED");
            case "version" -> f.allocation = replace(f.allocation, "version", 5);
            case "fulfilled" -> f.allocation = replace(f.allocation, "fulfilledBaseQty", q("3"));
            case "received" -> f.orderLine = replace(f.orderLine, "receivedQuantity", q("3"));
            case "returned" -> f.orderLine = replace(f.orderLine, "returnedQuantity", q("1"));
            case "invoiced" -> f.orderLine = replace(f.orderLine, "invoicedQty", q("1"));
            case "closed" -> f.orderHead = replace(f.orderHead, "status", "CLOSED");
            case "clock-zone" -> when(f.mapper.clock()).thenReturn(new Clock(BigDecimal.valueOf(AT.plusSeconds(200).getEpochSecond()),"SYSTEM"));
            case "provider-unavailable" -> when(f.provider.getObject()).thenThrow(new IllegalStateException("不可解析"));
            case "replay-fingerprint", "replay-order", "replay-false" -> when(f.posting.readPostedLocked(T,91L)).thenReturn(
                new WmCertifiedReceiptPostingService.PostedSnapshot(failure.equals("replay-order")?999L:81L,
                    new WmCertifiedReceiptPostingService.Result(91L,140L,q("4"),failure.equals("replay-fingerprint")?"b".repeat(64):FP,
                        !failure.equals("replay-false"),false),"2026-10-05 14:23:31","ENQUEUED"));
            default -> throw new AssertionError(failure);
        }
        withTenant(() -> assertThrows(ServiceException.class, () -> f.reader.readLocked(T,f.allocation,f.orderLine,f.orderHead)));
        verify(f.posting, never()).post(anyLong());
    }

    @Test
    void includesKnownUnpostedHeadsInSourceVersionsWithoutCountingTheirQuantity() {
        Fixture f=new Fixture("4");
        f.heads=List.of(f.heads.get(0),new ReceiptHead(93L,T,81L,"APPROVED","0","RX-93",2L,null,null));
        f.lines=List.of(f.lines.get(0),new ReceiptLine(94L,T,93L,140L,"26",q("3"),82L,82L,"0"));
        withTenant(() -> {
            var result=assertDoesNotThrow(()->f.reader.readLocked(T,f.allocation,f.orderLine,f.orderHead));
            assertEquals(q("4"),result.proof().fulfilledBaseQty());assertEquals(1,result.proof().receipts().size());
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_material_receive:93"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_material_receive_line:94"));
            verify(f.posting,never()).readPostedLocked(T,93L);
        });
    }

    @Test
    void acceptsActualMicrosecondDatabaseClockWithoutRoundingOrUsingJvmTime() {
        Fixture f=new Fixture("4");
        when(f.mapper.clock()).thenReturn(new Clock(BigDecimal.valueOf(AT.plusSeconds(200).getEpochSecond()).add(new BigDecimal("0.123456")),"+08:00"));
        withTenant(()->assertDoesNotThrow(()->f.reader.readLocked(T,f.allocation,f.orderLine,f.orderHead)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.123456", "1791181611.123456", "1", "1.123000", "1.000000"})
    void databaseSecondsKeepIntegerAndAtMostSixFractionDigitsExactly(String value) throws Exception {
        var method=ErpPurchaseAllocationReceiptSourceReader.class.getDeclaredMethod("secondsInstant",BigDecimal.class);method.setAccessible(true);
        Instant actual=(Instant)method.invoke(null,new BigDecimal(value));
        BigDecimal expected=new BigDecimal(value);long seconds=expected.divideToIntegralValue(BigDecimal.ONE).longValueExact();
        assertEquals(seconds,actual.getEpochSecond());
        assertEquals(expected.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).intValueExact(),actual.getNano());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1.1234567", "1.0000000", "-1", "-0.000001", "0", "0.000000", "999999999999999999999.123456"})
    void databaseSecondsRejectUnknownPrecisionNonpositiveOrUnsupportedRange(String value) throws Exception {
        var method=ErpPurchaseAllocationReceiptSourceReader.class.getDeclaredMethod("secondsInstant",BigDecimal.class);method.setAccessible(true);
        var failure =
                assertThrows(
                        InvocationTargetException.class,
                        () -> method.invoke(null, value == null ? null : new BigDecimal(value)));
        assertInstanceOf(ServiceException.class,failure.getCause());
    }

    @Test
    void finishedTargetAcceptsUnpostedSiblingOnlyWithActualSameOrderParentLine() {
        Fixture f=siblingFixture();
        withTenant(()-> {
            var result=assertDoesNotThrow(()->f.reader.readLocked(T,f.allocation,f.orderLine,f.orderHead));
            assertEquals(q("10"),result.proof().fulfilledBaseQty());assertEquals(1,result.proof().receipts().size());
            assertTrue(result.sourceVersions().containsKey("receipt-source:pu_purchase_order_line:83"));
            assertTrue(result.sourceVersions().containsKey("receipt-source:wm_material_receive_line:94"));
            verify(f.mapper).lockPurchaseLines(List.of(82L,83L));verify(f.posting,never()).readPostedLocked(T,93L);
        });
    }

    @ParameterizedTest
    @ValueSource(strings={"missing", "cross-parent", "cross-tenant", "material", "unit", "deleted"})
    void refusesUnknownOrContradictoryUnpostedSiblingParent(String failure) {
        Fixture f=siblingFixture();PurchaseLine sibling=f.purchaseLines.get(1);
        sibling=switch(failure) {
            case "cross-parent" -> replace(sibling,"orderId",999L);
            case "cross-tenant" -> replace(sibling,"tenantId","000002");
            case "material" -> replace(sibling,"materialId",999L);
            case "unit" -> replace(sibling,"unit","27");
            case "deleted" -> replace(sibling,"delFlag","1");
            default -> sibling;
        };
        f.purchaseLines=failure.equals("missing")?List.of(f.purchaseLines.get(0)):List.of(f.purchaseLines.get(0),sibling);
        withTenant(()->assertThrows(ServiceException.class,()->f.reader.readLocked(T,f.allocation,f.orderLine,f.orderHead)));
    }

    static Fixture siblingFixture() {
        Fixture f=new Fixture("10");f.orderHead=replace(f.orderHead,"status","PARTIALLY_RECEIVED");
        f.heads=List.of(f.heads.get(0),new ReceiptHead(93L,T,81L,"APPROVED","0","RX-93",2L,null,null));
        f.lines=List.of(f.lines.get(0),new ReceiptLine(94L,T,93L,140L,"26",q("10"),83L,83L,"0"));
        f.purchaseLines=List.of(f.purchaseLines.get(0),new PurchaseLine(83L,T,81L,140L,"26","0"));return f;
    }

    @ParameterizedTest
    @ValueSource(strings={"1.123", "1791181411000.123000", "32503680000000.123"})
    void databaseMillisKeepAtMostThreeEffectiveFractionDigitsWithoutTotalNanosOverflow(String value) throws Exception {
        var method=ErpPurchaseAllocationReceiptSourceReader.class.getDeclaredMethod("millisInstant",BigDecimal.class);method.setAccessible(true);
        BigDecimal millis=new BigDecimal(value);long seconds=millis.divideToIntegralValue(new BigDecimal("1000")).longValueExact();
        Instant actual=(Instant)assertDoesNotThrow(()->method.invoke(null,millis));
        assertEquals(seconds,actual.getEpochSecond());
        assertEquals(millis.subtract(BigDecimal.valueOf(seconds).multiply(new BigDecimal("1000"))).movePointRight(6).intValueExact(),actual.getNano());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings={"1.1234", "1.123456", "-1", "0"})
    void databaseMillisRejectUnknownPrecisionAndNonpositiveValues(String value) throws Exception {
        var method=ErpPurchaseAllocationReceiptSourceReader.class.getDeclaredMethod("millisInstant",BigDecimal.class);method.setAccessible(true);
        var failure =
                assertThrows(
                        InvocationTargetException.class,
                        () -> method.invoke(null, value == null ? null : new BigDecimal(value)));
        assertInstanceOf(ServiceException.class,failure.getCause());
    }

    @Test
    void constructorDoesNotResolvePostingOrBorrowAConnection() {
        Fixture f=new Fixture("4");verifyNoInteractions(f.provider,f.qualification,f.posting);
    }

    @Test
    void mapperOnlyUsesUncachedCurrentReadsWithoutStateDeletionOrQuantityFilters() {
        for(var method:ErpPurchaseAllocationReceiptSourceMapper.class.getDeclaredMethods()) {
            var select = method.getAnnotation(Select.class);
            assertNotNull(select);
            var options = method.getAnnotation(Options.class);
            assertNotNull(options);
            assertFalse(options.useCache());
            assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache());
            String sql=String.join(" ",select.value()).toLowerCase(Locale.ROOT);
            assertFalse(sql.contains("delete "));assertFalse(sql.contains("insert "));assertFalse(sql.contains("update "));
            if(!method.getName().equals("clock")) {assertTrue(sql.contains("for update"));assertFalse(sql.substring(sql.indexOf("where")).contains("del_flag"));}
        }
        assertNotNull(
                ErpPurchaseAllocationReceiptSourceMapper.class.getAnnotation(
                        InterceptorIgnore.class));
    }

    static void withTenant(Runnable action) {
        try(MockedStatic<TenantHelper> tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(T);action.run();
        }
    }
    @SuppressWarnings("unchecked")
    static <T> T replace(T record,String name,Object value) {
        try {
            var fields=record.getClass().getRecordComponents();Object[] values=new Object[fields.length];Class<?>[] types=new Class<?>[fields.length];
            for(int i=0;i<fields.length;i++) {values[i]=fields[i].getName().equals(name)?value:fields[i].getAccessor().invoke(record);types[i]=fields[i].getType();}
            return (T)record.getClass().getDeclaredConstructor(types).newInstance(values);
        }catch(Exception failure){throw new AssertionError(failure);}
    }

    static class Fixture {
        final ErpPurchaseAllocationReceiptSourceMapper mapper = mock(ErpPurchaseAllocationReceiptSourceMapper.class);
        final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
        final WmCertifiedReceiptPostingService posting = mock(WmCertifiedReceiptPostingService.class);
        @SuppressWarnings("unchecked")
        final ObjectProvider<WmCertifiedReceiptPostingService> provider = mock(ObjectProvider.class);
        final ErpPurchaseAllocationReceiptSourceReader reader = new ErpPurchaseAllocationReceiptSourceReader(mapper, qualification, provider);
        AllocationSource allocation;
        OrderLineSource orderLine;
        OrderHeadSource orderHead;
        List<ReceiptHead> heads;
        List<ReceiptLine> lines;
        List<Posting> postings;
        List<AllocationEvent> events;
        List<PurchaseLine> purchaseLines;
        Fixture(String received) {
            BigDecimal qty = q(received), left = q("10").subtract(qty);
            allocation = new AllocationSource(71L,T,140L,"MRP_DETAIL",61L,72L,1,82L,26L,q("10"),left,qty,q("0"),ORDER,
                "allocation-key","request-key",4,AT.minusSeconds(100),AT.minusSeconds(98),null,null,AT.plusSeconds(10));
            orderLine = new OrderLineSource(82L,T,81L,72L,140L,"26",q("10"),qty,q("0"),q("0"),
                left.signum()==0 ? "FULL_RECEIVED" : "PARTIAL_RECEIVED","0",AT.minusSeconds(98),AT.plusSeconds(10));
            orderHead = new OrderHeadSource(81L,T,"PO-81",73L,2L,left.signum()==0 ? "RECEIVED" : "PARTIALLY_RECEIVED",
                AT.minusSeconds(98),AT,"0",AT.minusSeconds(98),AT.plusSeconds(10));
            purchaseLines = List.of(new PurchaseLine(82L,T,81L,140L,"26","0"));
            heads = List.of(new ReceiptHead(91L,T,81L,"FINISHED","0","RX-91",2L,BigDecimal.valueOf(AT.minusSeconds(50).toEpochMilli()),BigDecimal.valueOf(AT.minusSeconds(30).toEpochMilli())));
            lines = List.of(new ReceiptLine(92L,T,91L,140L,"26",qty,82L,82L,"0"));
            postings = List.of(new Posting(T,91L,140L,1L,FP,qty,"2026-10-05 14:23:31","ENQUEUED"));
            events = List.of(event(100L,0,"CREATE",null,"REQUEST_INTENT",q("0"),q("10"),null,"CREATE:allocation-key"),
                event(101L,1,"APPROVE","REQUEST_INTENT","REQUEST_INTENT",q("10"),q("10"),null,key("APPROVE",73L)),
                event(102L,2,"TRANSFER_TO_ORDER","REQUEST_INTENT",ORDER,q("10"),q("10"),82L,key("TRANSFER_TO_ORDER",82L)),
                event(103L,3,"APPROVE_ORDER",ORDER,ORDER,q("10"),q("10"),82L,key("APPROVE_ORDER",81L)),
                event(104L,4,"RECEIPT_POSTED",ORDER,ORDER,q("10"),left,82L,key("RECEIPT_POSTED",92L)));
            when(mapper.lockPurchaseLines(anyList())).thenAnswer(i -> purchaseLines);
            when(mapper.lockHeads(81L,82L)).thenAnswer(i -> heads);
            when(mapper.lockLines(eq(82L),anyList())).thenAnswer(i -> lines);
            when(mapper.lockPostings(anyList())).thenAnswer(i -> postings);
            when(mapper.lockEvents(71L)).thenAnswer(i -> events);
            when(mapper.clock()).thenReturn(new Clock(BigDecimal.valueOf(AT.plusSeconds(200).getEpochSecond()),"+08:00"));
            when(provider.getObject()).thenReturn(posting);
            when(posting.readPostedLocked(T,91L)).thenReturn(new WmCertifiedReceiptPostingService.PostedSnapshot(81L,
                new WmCertifiedReceiptPostingService.Result(91L,140L,qty,FP,true,false),"2026-10-05 14:23:31","ENQUEUED"));
        }
        void changeEvent(String field,Object value) {
            var changed=new ArrayList<>(events);changed.set(4,replace(changed.get(4),field,value));events=changed;
        }
        AllocationEvent event(Long id,int sequence,String type,String from,String to,BigDecimal before,BigDecimal after,Long order,String key) {
            return new AllocationEvent(id,T,71L,sequence,key,type,from,to,before,after,72L,order,1L,BigDecimal.valueOf(AT.plusSeconds(sequence-4).toEpochMilli()));
        }
        String key(String operation,Long id) { return "LIFECYCLE:" + digest(T,"allocation-key",operation,id.toString()); }
    }
    static String digest(String... values) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for(String value:values) { byte[] bytes=value.getBytes(StandardCharsets.UTF_8); sha.update(ByteBuffer.allocate(4).putInt(bytes.length).array());sha.update(bytes); }
            return HexFormat.of().formatHex(sha.digest());
        } catch(Exception e) { throw new AssertionError(e); }
    }
}
