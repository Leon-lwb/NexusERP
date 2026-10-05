package com.nexus.erp.wm;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseStockLineageVerifier;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.service.impl.WmMqSendService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmCertifiedReceiptPostingServiceTest {
    static final String TENANT="000000";static final Long ID=101L,MATERIAL=201L;
    static final BigDecimal Q=new BigDecimal("4.0000"),ZERO=new BigDecimal("0.0000");
    static final BigDecimal NOW=BigDecimal.valueOf(Instant.parse("2026-10-03T02:00:00Z").toEpochMilli());
    MockedStatic<TenantHelper> tenants;
    WmCertifiedReceiptPostingAuthorizer auth;PuRequirementErpTransactionExecutor tx;PuRequirementErpTransactionQualification qualification;
    PuRequirementMaterialLockService locks;WmCertifiedReceiptPostingMapper mapper;ErpPurchaseStockLineageVerifier lineage;
    PuCertifiedPurchaseOrderWorkflowService orderWorkflow;
    WmReceiptIqcEvidenceService iqc;WmMqSendService notifications;WmCertifiedReceiptPostingService service;
    BigDecimal draftCreatedAt;
    Head head;Batch batch;Posting posting;List<Stock> stocks;List<PostedTx> transactions;List<Line> lines;List<Detail> details;
    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        auth=mock(WmCertifiedReceiptPostingAuthorizer.class);tx=mock(PuRequirementErpTransactionExecutor.class);qualification=mock(PuRequirementErpTransactionQualification.class);
        draftCreatedAt=NOW.subtract(new BigDecimal("10000"));
        locks=mock(PuRequirementMaterialLockService.class);
        // 红阶段旧Mapper没有此读取方法；以名称匹配默认答复，仍执行未修复的实际服务入口。
        mapper=mock(WmCertifiedReceiptPostingMapper.class, invocation ->
            "readDraftCreatedAt".equals(invocation.getMethod().getName()) ? draftCreatedAt
                : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
        lineage=mock(ErpPurchaseStockLineageVerifier.class);
        iqc=mock(WmReceiptIqcEvidenceService.class);notifications=mock(WmMqSendService.class);
        orderWorkflow=mock(PuCertifiedPurchaseOrderWorkflowService.class);
        service=new WmCertifiedReceiptPostingService(auth,tx,qualification,locks,mapper,lineage,iqc,notifications,orderWorkflow);
        when(auth.requireUser()).thenReturn(new DecisionActor(TENANT,9L,"正式入库测试员",Set.of("wm:receive:execute")));
        when(tx.executeRepeatableRead(eq(TENANT),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        when(mapper.pinDefinitions()).thenReturn(List.of());
        String[] tables={"wm_certified_receipt_posting","wm_material_stock","wm_material_stock_tx","wm_material_receive","wm_material_receive_line","wm_material_receive_detail","wm_batch","bd_material","bd_unit_of_measure","pu_purchase_order","pu_purchase_order_line","wm_warehouse","wm_storage_area","wm_storage_location"};
        when(mapper.tableDefinitions()).thenReturn(Arrays.stream(tables).map(t->new TableDefinition(t,"InnoDB")).toList());
        String[] quantities={"wm_material_stock.stock_quantity","wm_material_stock.reserved_quantity","wm_material_stock_tx.tx_quantity","wm_material_stock_tx.before_quantity","wm_material_stock_tx.after_quantity","wm_material_receive_line.quantity_received","wm_material_receive_detail.quantity","wm_batch.quantity","wm_batch.received_qty","wm_batch.issued_qty","bd_unit_of_measure.conversion_rate","pu_purchase_order_line.order_quantity","pu_purchase_order_line.received_quantity","pu_purchase_order_line.returned_quantity","pu_purchase_order_line.unit_price","wm_certified_receipt_posting.total_quantity","wm_material_stock.unit_cost","wm_material_stock.total_cost","wm_material_stock_tx.unit_cost","wm_material_stock_tx.tx_amount","pu_purchase_order_line.unit_price_excluding_tax","pu_purchase_order.exchange_rate"};
        when(mapper.quantityDefinitions()).thenReturn(Arrays.stream(quantities).map(q->new QuantityDefinition(q.split("\\.")[0],q.split("\\.")[1],"decimal",18,4)).toList());
        when(mapper.uniqueDefinitions()).thenReturn(List.of(new KeyDefinition("PRIMARY",1,"tenant_id",null),new KeyDefinition("PRIMARY",2,"receive_id",null)));
        when(mapper.discoverMaterials(TENANT,ID)).thenReturn(List.of(MATERIAL));
        head=new Head(TENANT,ID,"RK202610030001",301L,401L,"SHELVED",501L,601L,null,NOW.subtract(new BigDecimal("10000")),NOW.subtract(new BigDecimal("5000")),"0");
        lines=List.of(new Line(TENANT,701L,ID,MATERIAL,"4",Q,801L,"0"));
        details=List.of(new Detail(TENANT,901L,ID,701L,MATERIAL,"4",Q,501L,601L,null,1001L,null,"0"));
        batch=new Batch(TENANT,1001L,"B202610030001",MATERIAL,401L,501L,Q,ZERO,ZERO,"ACTIVE","WM_MATERIAL_RECEIVE",ID,NOW.add(new BigDecimal("86400000")),"0");
        stocks=new ArrayList<>();transactions=new ArrayList<>();
        when(mapper.lockHead(TENANT,ID)).thenAnswer(i->head);when(mapper.lockPosting(TENANT,ID)).thenAnswer(i->posting);
        when(mapper.lockLines(TENANT,ID)).thenAnswer(i->lines);when(mapper.lockDetails(TENANT,ID)).thenAnswer(i->details);
        when(mapper.clock()).thenReturn(new Clock("2026-10-03 10:00:00",NOW,"+08:00"));
        when(mapper.lockMaterial(TENANT,MATERIAL)).thenReturn(new Material(TENANT,MATERIAL,4L,1L,1L,"0",TENANT,4,"unit-v1",NOW.subtract(new BigDecimal("100000")),1L,null,BigDecimal.ONE,1L,"0"));
        when(mapper.lockOrder(TENANT,301L)).thenReturn(new WmCertifiedReceiptPostingMapper.Order(TENANT,301L,401L,"APPROVED","CNY",BigDecimal.ONE,"0"));
        when(mapper.lockOrderLine(TENANT,801L)).thenReturn(order(new BigDecimal("10.0000")));
        when(mapper.lockPostedQuantities(TENANT,801L)).thenAnswer(i->"FINISHED".equals(head.status())?List.of(Q):List.of());
        when(mapper.lockBatch(TENANT,1001L)).thenAnswer(i->batch);
        when(mapper.lockStorage(TENANT,501L,601L,null)).thenReturn(new Storage(TENANT,501L,1L,"0",TENANT,601L,501L,1L,0L,0L,"0",null,null,null,null,null,null,null));
        when(mapper.lockStocks(TENANT,MATERIAL)).thenAnswer(i->List.copyOf(stocks));
        when(mapper.lockTransactionIds(TENANT,MATERIAL)).thenAnswer(i->transactions.stream().map(PostedTx::txId).toList());
        when(mapper.lockReceiptTransactions(TENANT,ID)).thenAnswer(i->List.copyOf(transactions));
        when(mapper.lockAllReceiptTransactionIds(TENANT,ID)).thenAnswer(i->transactions.stream().map(PostedTx::txId).toList());
        var inventory=mock(ErpPurchaseInventorySourceVerifier.InventorySnapshot.class);
        when(inventory.tenantId()).thenReturn(TENANT);when(inventory.materialId()).thenReturn(MATERIAL);
        var verified=mock(ErpPurchaseStockLineageVerifier.StockLineageSnapshot.class);when(verified.inventory()).thenReturn(inventory);
        when(lineage.verifyLocked(TENANT,MATERIAL)).thenReturn(verified);
        when(mapper.insertStock(eq(TENANT),anyLong(),eq("4"),any(),any(),any(),anyString(),eq(9L),any(),any())).thenAnswer(i->{
            Detail d=i.getArgument(3);Batch b=i.getArgument(4);stocks.add(new Stock(TENANT,i.getArgument(1),MATERIAL,"4",d.warehouseId(),d.areaId(),d.locationId(),d.batchId(),null,i.getArgument(5),ZERO,0,"0",b.expiryAt(),i.getArgument(8),i.getArgument(9)));return 1;});
        when(mapper.addStock(eq(TENANT),any(),any(),anyString(),eq(9L),any())).thenAnswer(i->{Stock s=i.getArgument(1);stocks.removeIf(old->old.stockId().equals(s.stockId()));stocks.add(new Stock(TENANT,s.stockId(),MATERIAL,"4",s.warehouseId(),s.areaId(),s.locationId(),s.batchId(),null,i.getArgument(2),s.reserved(),s.version()+1,"0",s.expiryAt(),s.unitCost(),i.getArgument(5)));return 1;});
        when(mapper.insertTransaction(eq(TENANT),anyLong(),anyLong(),eq("4"),any(),any(),any(),any(),anyString(),eq(9L),any(),any())).thenAnswer(i->{Detail d=i.getArgument(5);transactions.add(new PostedTx(i.getArgument(1),i.getArgument(2),d.lineId(),"4",d.quantity(),i.getArgument(6),i.getArgument(7),i.getArgument(10),i.getArgument(11),d.warehouseId(),d.areaId(),d.locationId(),d.batchId(),i.getArgument(8)));return 1;});
        when(mapper.receiveBatch(eq(TENANT),eq(ID),any(),any(),anyString(),eq(9L))).thenAnswer(i->{batch=new Batch(batch.tenantId(),batch.batchId(),batch.batchCode(),batch.materialId(),batch.vendorId(),batch.warehouseId(),batch.quantity(),i.getArgument(3),batch.issued(),batch.status(),batch.sourceType(),batch.sourceId(),batch.expiryAt(),batch.delFlag());return 1;});
        when(mapper.finish(eq(TENANT),eq(ID),anyString(),eq(9L))).thenAnswer(i->{head=new Head(head.tenantId(),head.receiveId(),head.receiveCode(),head.orderId(),head.vendorId(),"FINISHED",head.warehouseId(),head.areaId(),head.locationId(),head.receiveAt(),head.shelfAt(),head.delFlag(),head.noticeId(),head.noticeCode());return 1;});
        when(mapper.recordPosting(eq(TENANT),eq(ID),eq(MATERIAL),eq(9L),anyString(),any(),anyString())).thenAnswer(i->{posting=new Posting(TENANT,ID,MATERIAL,9L,i.getArgument(4),i.getArgument(5),i.getArgument(6),"PENDING");return 1;});
        when(mapper.acknowledgeEnqueue(TENANT,ID)).thenAnswer(i->{posting=new Posting(TENANT,ID,MATERIAL,9L,posting.sourceFingerprint(),posting.totalQuantity(),posting.postedAt(),"ENQUEUED");return 1;});
    }
    OrderLine order(BigDecimal quantity) {return new OrderLine(TENANT,801L,301L,MATERIAL,"4",quantity,ZERO,ZERO,new BigDecimal("10.0000"),new BigDecimal("10.0000"),"0");}
    @AfterEach void close(){tenants.close();}
    void noWrites(){verify(mapper,never()).insertStock(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());verify(mapper,never()).recordPosting(any(),any(),any(),any(),any(),any(),any());}
    @Test void actualPostingCreatesNativeBalanceAndOriginalLineTransaction() {
        var result=service.post(ID);assertFalse(result.replay());assertFalse(result.notificationPending());assertEquals(Q,result.quantity());
        assertEquals(1,stocks.size());assertEquals(Q,stocks.get(0).quantity());assertEquals(1,transactions.size());
        assertEquals(701L,transactions.get(0).lineId());assertEquals(ZERO,transactions.get(0).beforeQuantity());assertEquals(Q,transactions.get(0).afterQuantity());
        assertEquals("FINISHED",head.status());assertEquals("ENQUEUED",posting.notificationStatus());
        var ordered=inOrder(iqc,mapper,lineage,notifications);
        ordered.verify(iqc).requireLocked(TENANT,ID,MATERIAL,4L,Q);
        ordered.verify(mapper).insertStock(eq(TENANT),anyLong(),eq("4"),any(),any(),eq(Q),anyString(),eq(9L),any(),any());
        ordered.verify(mapper).finish(eq(TENANT),eq(ID),anyString(),eq(9L));ordered.verify(lineage).verifyLocked(TENANT,MATERIAL);
        ordered.verify(mapper).recordPosting(eq(TENANT),eq(ID),eq(MATERIAL),eq(9L),anyString(),eq(Q),anyString());
        ordered.verify(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
    }
    @Test void realQuoteApprovalEvidenceIsRequiredBeforeAnyStockWrite() {
        doThrow(new ServiceException("unproven supplier approval")).when(orderWorkflow).requireApprovedLocked(TENANT,301L);
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(iqc,notifications);
    }
    @Test void nativePartiallyReceivedOrderRemainsEligibleForAnotherExactReceipt() {
        when(mapper.lockOrder(TENANT,301L)).thenReturn(new WmCertifiedReceiptPostingMapper.Order(TENANT,301L,401L,"PARTIALLY_RECEIVED","CNY",BigDecimal.ONE,"0"));
        assertDoesNotThrow(()->service.post(ID));verify(orderWorkflow).requireApprovedLocked(TENANT,301L);
    }
    @Test void ordinaryApiRoutingFindsPermanentReceiptsAndCertifiedPurchaseSources() {
        when(mapper.lockCertifiedOrderIdentities(TENANT,301L)).thenReturn(List.of(301L));assertTrue(service.handles(ID));
        when(mapper.lockCertifiedOrderIdentities(TENANT,301L)).thenReturn(List.of());assertFalse(service.handles(ID));
        service.post(ID);assertTrue(service.handles(ID));
    }
    @Test void actualSupplierNetPriceDeterminesStockAndTransactionValues() {
        service.post(ID);assertEquals(new BigDecimal("10.000000"),stocks.get(0).unitCost());
        assertEquals(new BigDecimal("40.0000"),stocks.get(0).totalCost());assertEquals(stocks.get(0).totalCost(),transactions.get(0).txAmount());
    }
    @Test void priceRequiringUnsupportedCostRoundingIsRejectedBeforePhysicalWrites() {
        when(mapper.lockOrderLine(TENANT,801L)).thenReturn(new OrderLine(TENANT,801L,301L,MATERIAL,"4",new BigDecimal("10.0000"),ZERO,ZERO,BigDecimal.ONE,new BigDecimal("0.000001"),"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unknownCurrencyOrExchangeRateNeverCreatesZeroValuedStock() {
        when(mapper.lockOrder(TENANT,301L)).thenReturn(new WmCertifiedReceiptPostingMapper.Order(TENANT,301L,401L,"APPROVED",null,null,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void sameReceiptReplayDoesNotPostAgain() {
        var first=service.post(ID);var second=service.post(ID);assertTrue(second.replay());assertEquals(first.sourceFingerprint(),second.sourceFingerprint());
        verify(mapper,times(1)).insertTransaction(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        verify(mapper,times(1)).recordPosting(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void committedReceiptKeepsPendingNotificationAndReplayRetriesOnlyNotification() {
        doThrow(new ServiceException("private backend details")).doNothing().when(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
        assertTrue(service.post(ID).notificationPending());assertEquals("FINISHED",head.status());
        var retried=service.post(ID);assertTrue(retried.replay());assertFalse(retried.notificationPending());assertEquals(1,transactions.size());
    }
    @Test void pendingNotificationRecoversAfterActualBatchConsumptionWithoutPostingAgain() {
        doThrow(new ServiceException("notification unavailable")).doNothing().when(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
        service.post(ID);
        batch=new Batch(batch.tenantId(),batch.batchId(),batch.batchCode(),batch.materialId(),batch.vendorId(),batch.warehouseId(),batch.quantity(),batch.received(),BigDecimal.ONE,batch.status(),batch.sourceType(),batch.sourceId(),batch.expiryAt(),batch.delFlag());
        var result=assertDoesNotThrow(()->service.post(ID));assertTrue(result.replay());assertFalse(result.notificationPending());assertEquals(1,transactions.size());
    }
    @Test void pendingNotificationRecoversAfterExpiryUsingOriginalPostedTime() {
        doThrow(new ServiceException("notification unavailable")).doNothing().when(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
        service.post(ID);when(mapper.clock()).thenReturn(new Clock("2026-10-05 10:00:00",NOW.add(new BigDecimal("172800000")),"+08:00"));
        var result=assertDoesNotThrow(()->service.post(ID));assertTrue(result.replay());assertFalse(result.notificationPending());assertEquals(1,transactions.size());
    }
    @Test void pendingNotificationRecoversAfterReconciledOrderCloseUsingDedicatedReplayProof() {
        doThrow(new ServiceException("notification unavailable")).doNothing().when(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
        service.post(ID);when(mapper.lockOrder(TENANT,301L)).thenReturn(new WmCertifiedReceiptPostingMapper.Order(TENANT,301L,401L,"CLOSED","CNY",BigDecimal.ONE,"0"));
        var result=assertDoesNotThrow(()->service.post(ID));assertTrue(result.replay());assertFalse(result.notificationPending());assertEquals(1,transactions.size());
        verify(orderWorkflow).requireApprovedReplayLocked(TENANT,301L);
    }
    @Test void originalPostingRecoveryDoesNotNeedCurrentStockRowsAfterLegalConsumption() {
        doThrow(new ServiceException("notification unavailable")).doNothing().when(notifications).sendCertifiedPurchaseReceiveWriteback(ID,TENANT);
        service.post(ID);stocks.clear();var result=assertDoesNotThrow(()->service.post(ID));assertTrue(result.replay());assertFalse(result.notificationPending());assertEquals(1,transactions.size());
    }
    @Test void originalPhysicalPostingTimestampCannotBeReassignedOnReplay() {
        service.post(ID);PostedTx t=transactions.get(0);transactions.set(0,new PostedTx(t.txId(),t.stockId(),t.lineId(),t.baseUomId(),t.quantity(),t.beforeQuantity(),t.afterQuantity(),t.unitCost(),t.txAmount(),t.warehouseId(),t.areaId(),t.locationId(),t.batchId(),"2026-10-03 10:00:01"));
        assertThrows(ServiceException.class,()->service.post(ID));
    }
    @Test void extraDeletedOrWrongTypeOriginalReceiptTransactionsDoNotDisappearFromReplayScope() {
        service.post(ID);when(mapper.lockAllReceiptTransactionIds(TENANT,ID)).thenReturn(List.of(transactions.get(0).txId(),-7L));
        assertThrows(ServiceException.class,()->service.post(ID));
    }
    @Test void internalReceiptValidationCannotCreateAPostingFromPayloadOrMissingResult() {
        assertThrows(ServiceException.class,()->service.validatePostedLocked(TENANT,ID));noWrites();verifyNoInteractions(notifications,locks,auth);
    }
    @Test void internalReceiptValidationRechecksPhysicalResultWithoutAuthorizationOrBusinessWrites() {
        service.post(ID);clearInvocations(mapper,notifications,locks,auth);assertDoesNotThrow(()->service.validatePostedLocked(TENANT,ID));
        noWrites();verifyNoInteractions(notifications,locks,auth);
    }
    @Test void missingIndependentLineageResultCannotCertifyWrittenStock() {
        when(lineage.verifyLocked(TENANT,MATERIAL)).thenReturn(null);
        assertThrows(ServiceException.class,()->service.post(ID));verify(mapper,never()).recordPosting(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void orphanHistoricalTransactionsDoNotBecomeZeroStartingBalance() {
        transactions.add(new PostedTx(88L,99L,77L,"4",Q,ZERO,Q,BigDecimal.TEN,new BigDecimal("40.0000"),501L,601L,null,1001L,"2026-10-03 10:00:00"));assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void actualIqcEvidenceFailureStopsAllPhysicalWrites() {
        doThrow(new ServiceException("unverified IQC")).when(iqc).requireLocked(TENANT,ID,MATERIAL,4L,Q);
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void allDetailsMustEqualOriginalLineAndNeverRound() {
        details=List.of(new Detail(TENANT,901L,ID,701L,MATERIAL,"4",new BigDecimal("3.9999"),501L,601L,null,1001L,null,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unknownBatchExpiryCannotBeInvented() {
        batch=new Batch(TENANT,1001L,"B",MATERIAL,401L,501L,Q,ZERO,ZERO,"ACTIVE","WM_MATERIAL_RECEIVE",ID,null,"0");
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void stockUsesActualBatchExpiryNotAnArbitraryFutureDate() {
        BigDecimal expiry=batch.expiryAt();service.post(ID);assertEquals(expiry,stocks.get(0).expiryAt());
    }
    @Test void previousFinishedReceiptsReducePhysicalOrderCapacityEvenBeforeMqWriteback() {
        when(mapper.lockPostedQuantities(TENANT,801L)).thenReturn(List.of(new BigDecimal("8.0000")));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void expiredBatchRejectsWholeReceipt() {
        batch=new Batch(TENANT,1001L,"B",MATERIAL,401L,501L,Q,ZERO,ZERO,"ACTIVE","WM_MATERIAL_RECEIVE",ID,NOW,"0");
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void sourceBelongingToAnotherTenantIsRejected() {
        lines=List.of(new Line("999999",701L,ID,MATERIAL,"4",Q,801L,"0"));assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unknownUnitIdentityCannotBeTranslatedFromDisplayName() {
        lines=List.of(new Line(TENANT,701L,ID,MATERIAL,"千克",Q,801L,"0"));assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unsupportedMixedMaterialsCannotShrinkToAChosenLine() {
        when(mapper.discoverMaterials(TENANT,ID)).thenReturn(List.of(MATERIAL,202L));assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void calendarReceiptDayDoesNotPretendToBePreciseDraftCreationTime() {
        // 业务日是午夜，单位当天稍后认证，原永久草稿再创建，不能把日期补成认证时间。
        BigDecimal calendarDate=NOW.subtract(new BigDecimal("36000000"));
        BigDecimal verification=NOW.subtract(new BigDecimal("15000"));
        head=new Head(TENANT,ID,head.receiveCode(),head.orderId(),head.vendorId(),head.status(),
            head.warehouseId(),head.areaId(),head.locationId(),calendarDate,head.shelfAt(),head.delFlag());
        when(mapper.lockMaterial(TENANT,MATERIAL)).thenReturn(new Material(TENANT,MATERIAL,4L,1L,1L,"0",TENANT,0,"v1",verification,1L,4L,BigDecimal.ONE,1L,"0"));
        assertDoesNotThrow(()->service.post(ID));
        assertEquals(calendarDate,head.receiveAt(),"不能修改原业务日期以绕过单位来源校验");
    }
    @ParameterizedTest(name="永久草稿创建时点不可用：{0}")
    @ValueSource(strings={"missing","negative","zero","future-second"})
    void unavailablePermanentDraftClockCannotBeReplacedWithBusinessDateOrJvmTime(String state) {
        draftCreatedAt=switch(state) {
            case "missing" -> null;
            case "negative" -> BigDecimal.ONE.negate();
            case "zero" -> BigDecimal.ZERO;
            default -> NOW.add(new BigDecimal("1000"));
        };
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(notifications);
    }
    @Test void unitCertifiedAfterPermanentDraftCreationCannotRetroactivelyAuthenticateReceipt() {
        draftCreatedAt=NOW.subtract(new BigDecimal("20000"));
        BigDecimal verification=NOW.subtract(new BigDecimal("15000"));
        when(mapper.lockMaterial(TENANT,MATERIAL)).thenReturn(new Material(TENANT,MATERIAL,4L,1L,1L,"0",TENANT,0,"v1",verification,1L,4L,BigDecimal.ONE,1L,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(iqc,notifications);
    }
    @Test void subsecondDraftCreationIsAcceptedInTheSameStoredPostingSecond() {
        // NOW()/过账字段只有秒，.728创建、同秒稍后操作不能误判成未来。
        draftCreatedAt=NOW.add(new BigDecimal("728"));
        head=new Head(TENANT,ID,head.receiveCode(),head.orderId(),head.vendorId(),head.status(),
            head.warehouseId(),head.areaId(),head.locationId(),head.receiveAt(),NOW,head.delFlag());
        assertDoesNotThrow(()->service.post(ID));assertEquals(1,transactions.size());
    }
    @Test void sameSecondDraftCreationAlsoPassesTheSharedShelfGate() {
        draftCreatedAt=NOW.add(new BigDecimal("728"));
        when(auth.requireShelf()).thenReturn(new DecisionActor(TENANT,9L,"仓储审批员",Set.of("wm:receive:shelf")));
        head=new Head(TENANT,ID,head.receiveCode(),head.orderId(),head.vendorId(),"APPROVED",
            head.warehouseId(),head.areaId(),head.locationId(),head.receiveAt(),null,head.delFlag());
        when(mapper.markShelved(eq(TENANT),eq(ID),anyString(),eq(9L))).thenReturn(1);
        assertTrue(assertDoesNotThrow(()->service.shelf(ID)));noWrites();verifyNoInteractions(notifications);
    }
    @Test void sameSecondOriginalPostingReplayNeverExpandsToCurrentClock() {
        draftCreatedAt=NOW.add(new BigDecimal("728"));
        head=new Head(TENANT,ID,head.receiveCode(),head.orderId(),head.vendorId(),head.status(),
            head.warehouseId(),head.areaId(),head.locationId(),head.receiveAt(),NOW,head.delFlag());
        assertFalse(assertDoesNotThrow(()->service.post(ID)).replay());
        when(mapper.clock()).thenReturn(new Clock("2026-10-03 10:01:00",NOW.add(new BigDecimal("60000")),"+08:00"));
        assertTrue(assertDoesNotThrow(()->service.post(ID)).replay());assertEquals(1,transactions.size());
    }
    @Test void creationInTheNextStoredSecondCannotReplayAnEarlierPosting() {
        service.post(ID);clearInvocations(mapper,notifications);
        draftCreatedAt=NOW.add(new BigDecimal("1000"));
        when(mapper.clock()).thenReturn(new Clock("2026-10-03 10:01:00",NOW.add(new BigDecimal("60000")),"+08:00"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(notifications);
    }
    @Test void postingSecondPrecisionDoesNotRelaxExactUnitCertificationOrder() {
        draftCreatedAt=NOW.subtract(new BigDecimal("10000")).add(new BigDecimal("728"));
        BigDecimal verification=draftCreatedAt.add(BigDecimal.ONE);
        when(mapper.lockMaterial(TENANT,MATERIAL)).thenReturn(new Material(TENANT,MATERIAL,4L,1L,1L,"0",TENANT,0,"v1",verification,1L,4L,BigDecimal.ONE,1L,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(iqc,notifications);
    }
    @Test void futureUnitVersionCannotAuthenticateEarlierReceipt() {
        when(mapper.lockMaterial(TENANT,MATERIAL)).thenReturn(new Material(TENANT,MATERIAL,4L,1L,1L,"0",TENANT,4,"v",NOW,1L,null,BigDecimal.ONE,1L,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void storageParentTenantAndFrozenStateMustBeProven() {
        when(mapper.lockStorage(TENANT,501L,601L,null)).thenReturn(new Storage(TENANT,501L,1L,"0","999999",601L,501L,1L,0L,0L,"0",null,null,null,null,null,null,null));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unsupportedSnCannotBeDroppedSilently() {
        details=List.of(new Detail(TENANT,901L,ID,701L,MATERIAL,"4",Q,501L,601L,null,1001L,5000L,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void finishCasFailureNeverWritesPermanentSuccessOrSendsNotification() {
        when(mapper.finish(eq(TENANT),eq(ID),anyString(),eq(9L))).thenReturn(0);
        assertThrows(ServiceException.class,()->service.post(ID));verify(mapper,never()).recordPosting(any(),any(),any(),any(),any(),any(),any());verifyNoInteractions(notifications);
    }
    @Test void authenticatedIdentityIsCheckedAgainOnReplay() {
        service.post(ID);doThrow(new ServiceException("permission revoked")).when(auth).requireUser();
        assertThrows(ServiceException.class,()->service.post(ID));assertEquals(1,transactions.size());
    }
    @Test void tamperedPermanentResultCannotRecoverByAStatusFlag() {
        service.post(ID);posting=new Posting(TENANT,ID,MATERIAL,9L,"0".repeat(64),Q,"2026-10-03 10:00:00","ENQUEUED");
        assertThrows(ServiceException.class,()->service.post(ID));assertEquals(1,transactions.size());
    }
    @Test void missingPhysicalOriginalLineTransactionsCannotRecoverFromAPermanentRowAlone() {
        service.post(ID);transactions.clear();assertThrows(ServiceException.class,()->service.post(ID));
    }
    @Test void floatsMustNotBeCastToDecimalOrPersistedAsExactStock() {
        var rows=new ArrayList<>(mapper.quantityDefinitions());rows.set(0,new QuantityDefinition("wm_material_stock","stock_quantity","double",null,null));
        when(mapper.quantityDefinitions()).thenReturn(rows);assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void partialMetadataOrMissingInnoDbDoesNotAuthorizeWrites() {
        var rows=new ArrayList<>(mapper.tableDefinitions());rows.remove(0);when(mapper.tableDefinitions()).thenReturn(rows);
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void prefixOrNonTenantPermanentIdentityIsRejected() {
        when(mapper.uniqueDefinitions()).thenReturn(List.of(new KeyDefinition("PRIMARY",1,"receive_id",null)));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"0.0000","-1.0000","0.00001","100000000000000.0000"})
    void invalidPreciseOriginalQuantityRejectsWithoutRounding(String value) {
        lines=List.of(new Line(TENANT,701L,ID,MATERIAL,"4",new BigDecimal(value),801L,"0"));
        assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }
    @Test void unknownDatabaseClockIsNotReplacedWithJvmTime() {
        when(mapper.clock()).thenReturn(new Clock("2026-10-03 10:00:00",NOW,"SYSTEM"));assertThrows(ServiceException.class,()->service.post(ID));noWrites();
    }

    com.nexus.erp.wm.service.WmCertifiedArrivalNoticeService attachArrival(){
        var a=mock(com.nexus.erp.wm.service.WmCertifiedArrivalNoticeService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"arrivals",a);
        head=new Head(head.tenantId(),head.receiveId(),head.receiveCode(),head.orderId(),head.vendorId(),head.status(),head.warehouseId(),head.areaId(),head.locationId(),head.receiveAt(),head.shelfAt(),head.delFlag(),81L,"AN001");
        lines=List.of(new Line(TENANT,701L,ID,MATERIAL,"4",Q,801L,"0",82L));
        when(a.receiptFingerprintLocked(eq(TENANT),any(),any(),anyBoolean())).thenReturn("notice-proof-v1\n");return a;
    }
    @Test void boundNoticeAccumulatesOnlyInsideFirstActualPosting(){
        var a=attachArrival();service.post(ID);assertTrue(service.post(ID).replay());
        verify(a,times(1)).receiveLocked(TENANT,81L,82L,Q);
        var ordered=inOrder(mapper,a);ordered.verify(mapper).recordPosting(eq(TENANT),eq(ID),eq(MATERIAL),eq(9L),anyString(),eq(Q),anyString());ordered.verify(a).receiveLocked(TENANT,81L,82L,Q);
        assertEquals(1,transactions.size());
    }
    @Test void unknownPermanentNoticeBindingRejectsBeforePhysicalWrites(){var a=attachArrival();doThrow(new ServiceException("无真实通知绑定")).when(a).receiptFingerprintLocked(any(),any(),any(),anyBoolean());assertThrows(ServiceException.class,()->service.post(ID));noWrites();verifyNoInteractions(notifications);}
    @Test void counterFailureCannotReportCommittedReceiptOrSendCompletion(){var a=attachArrival();doThrow(new ServiceException("通知累计失败")).when(a).receiveLocked(any(),any(),any(),any());assertThrows(ServiceException.class,()->service.post(ID));verifyNoInteractions(notifications);}
    @Test void missingNoticeHeadWithAReferencedLineNeverPosts(){lines=List.of(new Line(TENANT,701L,ID,MATERIAL,"4",Q,801L,"0",82L));assertThrows(ServiceException.class,()->service.post(ID));noWrites();}
}
