package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.service.WmSerialInventoryRepository;
import com.nexus.erp.wm.service.WmSerialInventoryService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmSerialInventoryServiceTest {
    WmSerialInventoryRepository store;
    WmSerialInventoryService service;
    WmSerialPostingRequest request;
    List<WmMaterialStockTx> txs;
    @BeforeEach void setup() {
        store=mock(WmSerialInventoryRepository.class);
        service=new WmSerialInventoryService(store);
        request=WmSerialPostingRequest.out("WM_MISC_ISSUE",10L,11L,"MISC_ISSUE",20L,new BigDecimal("2"),30L,40L,50L);
        when(store.tenant()).thenReturn("t1");
        when(store.policy(20L)).thenReturn(new TrackingPolicy(true,true,true,7L,TrackingStage.STRICT));
        txs=new ArrayList<>();
        doAnswer(a->{txs.add(a.getArgument(0));return null;}).when(store).insertTx(any());
    }
    void frozen(List<SnItem> items) {
        String hash=service.selectionHash(items);
        var g=new WmSerialInventoryRepository.Group(100L,"cmd",1L,service.fingerprint(request,7L,hash),hash,
            request.quantity(),BigDecimal.ZERO,"PREPARED",null);
        when(store.group(request)).thenReturn(g);
        when(store.items(100L)).thenReturn(items);
    }
    WmMaterialStock stock(long id,long sn,BigDecimal qty,BigDecimal reserve) {
        WmMaterialStock s=new WmMaterialStock();s.setTenantId("t1");s.setStockId(id);s.setMaterialId(20L);
        s.setSnId(sn);s.setSnCode("SN"+sn);s.setBatchId(50L);s.setWarehouseId(30L);s.setLocationId(40L);
        s.setStockQuantity(qty);s.setReservedQuantity(reserve);s.setUnitCost(new BigDecimal("0.333333"));
        s.setTotalCost(new BigDecimal("0.3333"));s.setTrackingVersion(2L);s.setValuationGroupId(200L);
        s.setFrozenFlag(0L);s.setDelFlag("0");s.setVersion(0);return s;
    }
    void available(long sn,long stockId) {
        when(store.identity(sn)).thenReturn(new WmSerialInventoryRepository.Identity(sn,20L,50L,"SN"+sn,"IN_STOCK"));
        when(store.stocks(sn)).thenReturn(List.of(stock(stockId,sn,BigDecimal.ONE,BigDecimal.ZERO)));
    }
    @Test void strictCannotFallBackToQuantityWhenSelectionMissing() {
        assertThrows(ServiceException.class,()->service.post(request));
        verify(store,never()).insertTx(any());verify(store,never()).saveStock(any());
    }
    @Test void sourceGuardRunsBeforeAnyFrozenOrStockEffects() {
        doThrow(new ServiceException("bad source")).when(store).lockSource(request);
        assertThrows(ServiceException.class,()->service.post(request));
        verify(store,never()).group(any());verify(store,never()).stocks(any());verify(store,never()).insertTx(any());
    }
    @Test void eachSerialPostsOneAndUsesOriginalMovingAveragePoolRounding() {
        frozen(List.of(new SnItem(2L,102L,null,null,50L),new SnItem(1L,101L,null,null,50L)));
        available(1,101);available(2,102);
        when(store.pool(200L)).thenReturn(new WmSerialInventoryRepository.Pool(200L,20L,30L,50L,999L,
            new BigDecimal("3"),new BigDecimal("1.0000"),new BigDecimal("0.333333")));
        var last=stock(103,3,BigDecimal.ONE,BigDecimal.ZERO);last.setTotalCost(new BigDecimal("0.3334"));
        when(store.poolStocks(200L)).thenReturn(List.of(stock(101,1,BigDecimal.ONE,BigDecimal.ZERO),stock(102,2,BigDecimal.ONE,BigDecimal.ZERO),last));
        assertTrue(service.post(request));
        assertEquals(2,txs.size());assertEquals(List.of(1L,2L),txs.stream().map(WmMaterialStockTx::getSnId).toList());
        assertEquals(new BigDecimal("0.6667"),txs.stream().map(WmMaterialStockTx::getTxAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("0.3334"),txs.get(0).getTxAmount());
        for(var tx:txs){assertEquals(BigDecimal.ONE,tx.getTxQuantity());assertEquals(2L,tx.getTrackingVersion());assertEquals(100L,tx.getPostingGroupId());}
        verify(store).finish(eq(100L),eq(new BigDecimal("0.6667")),any());verify(store).enqueue(eq(100L),anyString(),anyString());
    }
    @Test void zeroHistoricRowsDoNotPreventCurrentSerialMovement() {
        request=WmSerialPostingRequest.out("WM_MISC_ISSUE",10L,11L,"MISC_ISSUE",20L,BigDecimal.ONE,30L,40L,50L);
        frozen(List.of(new SnItem(1L,101L,null,null,50L)));available(1,101);
        when(store.stocks(1L)).thenReturn(List.of(stock(88,1,BigDecimal.ZERO,BigDecimal.ZERO),stock(101,1,BigDecimal.ONE,BigDecimal.ZERO)));
        when(store.pool(200L)).thenReturn(new WmSerialInventoryRepository.Pool(200L,20L,30L,50L,999L,BigDecimal.ONE,new BigDecimal("0.3333"),new BigDecimal("0.333333")));
        when(store.poolStocks(200L)).thenReturn(List.of(stock(101,1,BigDecimal.ONE,BigDecimal.ZERO)));
        assertTrue(service.post(request));assertEquals(1,txs.size());
    }
    @Test void duplicatePositiveSerialRowsFailBeforeEffects() {
        frozen(List.of(new SnItem(1L,101L,null,null,50L),new SnItem(2L,102L,null,null,50L)));available(1,101);available(2,102);
        when(store.stocks(1L)).thenReturn(List.of(stock(101,1,BigDecimal.ONE,BigDecimal.ZERO),stock(104,1,BigDecimal.ONE,BigDecimal.ZERO)));
        assertThrows(ServiceException.class,()->service.post(request));verify(store,never()).saveStock(any());
    }
    @Test void policyChangeCannotSilentlyReinterpretFrozenSelection() {
        frozen(List.of(new SnItem(1L,101L,null,null,50L),new SnItem(2L,102L,null,null,50L)));
        when(store.policy(20L)).thenReturn(new TrackingPolicy(true,true,true,8L,TrackingStage.STRICT));
        assertThrows(ServiceException.class,()->service.post(request));verify(store,never()).stocks(any());
    }
    @Test void auditUsesExplicitLegacyAdapter() {
        when(store.policy(20L)).thenReturn(new TrackingPolicy(true,true,true,1L,TrackingStage.AUDIT));
        assertFalse(service.post(request));verify(store,never()).insertTx(any());
    }
    @Test void fulfilledGroupReplayChecksFrozenProofWithoutCurrentStockOrRecredit() {
        var items=List.of(new SnItem(1L,101L,null,null,50L),new SnItem(2L,102L,null,null,50L));frozen(items);
        var old=store.group(request);
        when(store.group(request)).thenReturn(new WmSerialInventoryRepository.Group(old.id(),old.commandId(),old.detailVersion(),old.fingerprint(),old.contentHash(),old.quantity(),new BigDecimal("0.6667"),"POSTED",200L));
        when(store.frozenPolicyVersion(100L)).thenReturn(7L);
        var proof=store.group(request);assertTrue(service.post(request));verify(store).verifyProof(request,proof,items);
        verify(store,never()).stocks(any());verify(store,never()).saveStock(any());verify(store,never()).insertTx(any());
    }
    @Test void inboundSerialsShareOneOriginalPoolAndConserveIncomingRounding() {
        request=WmSerialPostingRequest.in("WM_MISC_RECPT",10L,11L,"MISC_RECPT",20L,new BigDecimal("3"),30L,40L,50L,new BigDecimal("0.333333"));
        var items=List.of(new SnItem(3L,null,40L,null,50L),new SnItem(1L,null,40L,null,50L),new SnItem(2L,null,40L,null,50L));frozen(items);
        for(long sn=1;sn<=3;sn++){
            var identity=new WmSerialInventoryRepository.Identity(sn,20L,50L,"SN"+sn,"REGISTERED");when(store.identity(sn)).thenReturn(identity);
            when(store.stocks(sn)).thenReturn(List.of());when(store.inboundCost(eq(request),eq(new SnItem(sn,null,40L,null,50L)))).thenReturn(new BigDecimal("0.333333"));
            var target=stock(100+sn,sn,BigDecimal.ONE,BigDecimal.ZERO);target.setValuationGroupId(null);
            when(store.inboundStock(eq(request),eq(new SnItem(sn,null,40L,null,50L)),eq(identity))).thenReturn(target);
        }
        when(store.inboundPool(any(),eq(7L))).thenReturn(200L);
        when(store.pool(200L)).thenReturn(new WmSerialInventoryRepository.Pool(200L,20L,30L,50L,null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        when(store.poolStocks(200L)).thenReturn(List.of());assertTrue(service.post(request));
        verify(store,times(1)).inboundPool(any(),eq(7L));verify(store,times(3)).insertStock(any());
        assertEquals(new BigDecimal("1.0000"),txs.stream().map(WmMaterialStockTx::getTxAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(List.of(1L,2L,3L),txs.stream().map(WmMaterialStockTx::getSnId).toList());
        assertEquals(new BigDecimal("0.3334"),txs.get(0).getTxAmount());
    }
    @Test void newReceiptCannotRecreditSoldSerialWithoutOriginalReturnProof() {
        request=WmSerialPostingRequest.in("WM_MISC_RECPT",10L,11L,"MISC_RECPT",20L,BigDecimal.ONE,30L,40L,50L,BigDecimal.ONE);
        frozen(List.of(new SnItem(1L,null,40L,null,50L)));
        when(store.identity(1L)).thenReturn(new WmSerialInventoryRepository.Identity(1L,20L,50L,"SN1","OUT"));when(store.stocks(1L)).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.post(request));verify(store,never()).insertStock(any());
    }
    @Test void reservedPieceCannotBeIssuedByUnrelatedSource() {
        request=WmSerialPostingRequest.out("WM_MISC_ISSUE",10L,11L,"MISC_ISSUE",20L,BigDecimal.ONE,30L,40L,50L);
        frozen(List.of(new SnItem(1L,101L,null,null,50L)));available(1,101);
        var reserved=stock(101,1,BigDecimal.ONE,BigDecimal.ONE);when(store.stocks(1L)).thenReturn(List.of(reserved));
        when(store.pool(200L)).thenReturn(new WmSerialInventoryRepository.Pool(200L,20L,30L,50L,999L,BigDecimal.ONE,new BigDecimal("0.3333"),new BigDecimal("0.333333")));
        when(store.poolStocks(200L)).thenReturn(List.of(reserved));assertThrows(ServiceException.class,()->service.post(request));verify(store,never()).saveStock(any());
    }
    @Test void changedServerSourceHashBlocksStockEffects() {
        frozen(List.of(new SnItem(1L,101L,null,null,50L),new SnItem(2L,102L,null,null,50L)));
        doThrow(new ServiceException("source hash changed")).when(store).validateFrozenSource(eq(request),any(),eq(7L));
        assertThrows(ServiceException.class,()->service.post(request));verify(store,never()).stocks(any());
    }
    @Test void outboxWriteFailureCannotBeSwallowedAsSuccessfulPosting() {
        request=WmSerialPostingRequest.out("WM_MISC_ISSUE",10L,11L,"MISC_ISSUE",20L,BigDecimal.ONE,30L,40L,50L);
        frozen(List.of(new SnItem(1L,101L,null,null,50L)));available(1,101);
        when(store.pool(200L)).thenReturn(new WmSerialInventoryRepository.Pool(200L,20L,30L,50L,999L,BigDecimal.ONE,new BigDecimal("0.3333"),new BigDecimal("0.333333")));
        when(store.poolStocks(200L)).thenReturn(List.of(stock(101,1,BigDecimal.ONE,BigDecimal.ZERO)));
        doThrow(new ServiceException("outbox failed")).when(store).enqueue(any(),anyString(),anyString());
        assertThrows(ServiceException.class,()->service.post(request));assertEquals(1,txs.size());
    }
    @Test void postingRequiresActualSourceExecutionStateBeforeFrozenProof() {
        doThrow(new ServiceException("draft source")).when(store).requirePostingState(request);
        assertThrows(ServiceException.class,()->service.post(request));verify(store).requirePostingState(request);verify(store,never()).group(any());
    }
}
