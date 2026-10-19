package com.nexus.erp.wm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用实际过账服务、来源校验和加锁读取的数据库事实验证库存效果。
 */
class WmSerialInboundIdentitySourceTest {
    JdbcTemplate jdbc;
    WmSerialInventoryRepository repository;
    WmSerialInventoryService service;
    WmMaterialStockMapper stockMapper;
    WmMaterialStockTxMapper txMapper;
    WmSerialPostingRequest request;
    WmSerialInventoryRepository.Group group;
    SnItem item;
    Map<String, Object> registration;
    List<Map<String, Object>> handoffs;
    final String manifestHash = "a".repeat(64);

    @BeforeEach
    void setup() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        stockMapper = mock(WmMaterialStockMapper.class);
        txMapper = mock(WmMaterialStockTxMapper.class);
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("t1");
        repository =
                spy(
                        new WmSerialInventoryRepository(
                                jdbc, stockMapper, txMapper, context, new ObjectMapper()));
        service = new WmSerialInventoryService(repository);
        item = new SnItem(1L, null, 40L, null, 50L);
        registration = new HashMap<>();
        registration.put("sn_id", 1L);
        registration.put("material_id", 20L);
        registration.put("batch_id", 50L);
        registration.put("sn_code", "SN1");
        registration.put("status", "REGISTERED");
        registration.put("source_doc_type", "ERP_MANUAL");
        handoffs = new ArrayList<>();
        when(jdbc.queryForList(contains("FROM wm_sn "), any(Object[].class)))
                .thenAnswer(call -> List.of(registration));
        when(jdbc.queryForList(contains("FROM wm_trace_inbox "), any(Object[].class)))
                .thenAnswer(call -> handoffs);
        when(jdbc.queryForList(contains("FROM wm_material_receive WHERE"), any(Object[].class)))
                .thenReturn(List.of(Map.of("pur_id", 70L)));
        when(jdbc.queryForList(contains("FROM wm_subcontract_receive WHERE"), any(Object[].class)))
                .thenReturn(List.of(Map.of("subcontract_id", 70L)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(contains("identity_version"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(stockMapper.insert(any(WmMaterialStock.class))).thenReturn(1);
        when(stockMapper.updateById(any(WmMaterialStock.class))).thenReturn(1);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);
        doReturn(new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT))
                .when(repository)
                .policy(20L);
        doNothing().when(repository).lockSource(any());
        doNothing().when(repository).requirePostingState(any());
        doNothing().when(repository).validateFrozenSource(any(), any(), eq(7L));
        doNothing().when(repository).validateInboundSelection(any(), any(), any());
        doNothing().when(repository).enqueue(any(), anyString(), anyString());
        doReturn(BigDecimal.ONE).when(repository).inboundCost(any(), any());
        doReturn(200L).when(repository).inboundPool(any(), eq(7L));
        doReturn(
                        new WmSerialInventoryRepository.Pool(
                                200L,
                                20L,
                                30L,
                                50L,
                                null,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO))
                .when(repository)
                .pool(200L);
        doReturn(List.of()).when(repository).poolStocks(200L);
        var stock = new WmMaterialStock();
        stock.setStockId(101L);
        stock.setTenantId("t1");
        stock.setMaterialId(20L);
        stock.setSnId(1L);
        stock.setSnCode("SN1");
        stock.setBatchId(50L);
        stock.setWarehouseId(30L);
        stock.setLocationId(40L);
        stock.setTrackingVersion(2L);
        stock.setStockQuantity(BigDecimal.ONE);
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setUnitCost(BigDecimal.ONE);
        stock.setTotalCost(BigDecimal.ONE);
        stock.setDelFlag("0");
        stock.setVersion(0);
        doReturn(stock).when(repository).inboundStock(any(), any(), any());
        bind("WM_MISC_RECPT", "selection");
    }

    void bind(String type, String command) {
        String tx =
                switch (type) {
                    case "WM_MATERIAL_RECEIVE" -> "RECEIVE_IN";
                    case "PRD_WORK_ORDER" -> "PRODUCTION_IN";
                    case "WM_STOCK_TAKING" -> "COUNT_ADJUST";
                    case "WM_SUBCONTRACT_RECEIVE" -> "SUBCONTRACT_RECEIVE";
                    default -> "MISC_RECPT";
                };
        request =
                WmSerialPostingRequest.in(
                        type,
                        10L,
                        "PRD_WORK_ORDER".equals(type) ? null : 11L,
                        tx,
                        20L,
                        BigDecimal.ONE,
                        30L,
                        40L,
                        50L,
                        BigDecimal.ONE);
        String hash = service.selectionHash(List.of(item));
        group =
                new WmSerialInventoryRepository.Group(
                        100L,
                        command,
                        1,
                        service.fingerprint(request, 7L, hash),
                        hash,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "PREPARED",
                        null);
        doReturn(group).when(repository).group(any());
        doReturn(List.of(item)).when(repository).items(100L);
    }

    void registeredFor(String type, Long id) {
        registration.put("source_doc_type", type);
        registration.put("source_doc_id", id);
    }

    void handoff(long erpWo, long mesWo, String hash) {
        String payload =
                "{\"manifestId\":\"900\",\"revision\":\"1\",\"manifestHash\":\""
                        + hash
                        + "\",\"erpWoId\":\""
                        + erpWo
                        + "\",\"mesWoId\":\""
                        + mesWo
                        + "\",\"materialId\":\"20\",\"lineCount\":\"1\"}";
        handoffs.add(
                Map.of(
                        "payload",
                        payload,
                        "content_hash",
                        TraceHashes.sha256(payload),
                        "status",
                        "WAITING"));
    }

    void noInventoryEffects() {
        verify(repository, never()).inboundCost(any(), any());
        verify(repository, never()).inboundPool(any(), anyLong());
        verifyNoInteractions(stockMapper, txMapper);
        verify(repository, never()).finish(any(), any(), any());
        verify(repository, never()).enqueue(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "MES_MANUAL,WM_MATERIAL_RECEIVE", "MES_FLOW_CARD,WM_MATERIAL_RECEIVE",
        "MES_MANUAL,WM_MISC_RECPT", "MES_FLOW_CARD,WM_MISC_RECPT",
        "MES_MANUAL,WM_STOCK_TAKING", "MES_FLOW_CARD,WM_STOCK_TAKING",
        "MES_MANUAL,WM_SUBCONTRACT_RECEIVE", "MES_FLOW_CARD,WM_SUBCONTRACT_RECEIVE"
    })
    void registeredMesPieceCannotBeReceivedThroughAnotherInventorySource(
            String birth, String incoming) {
        registeredFor(birth, 22L);
        bind(incoming, "selection");
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void invalidMesSelectionCannotBeFrozenForPurchase() {
        registeredFor("MES_FLOW_CARD", 44L);
        bind("WM_MATERIAL_RECEIVE", "selection");
        doReturn(null).when(repository).group(any());
        assertThrows(
                ServiceException.class,
                () -> service.freeze(request, "selection", 1, 7, List.of(item)));
        verify(repository, never())
                .prepare(any(), anyString(), anyLong(), anyString(), anyString(), anyList());
        noInventoryEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"WM_MATERIAL_RECEIVE", "WM_MISC_RECPT"})
    void unboundErpRegistrationCanBeReceivedByAGenuineErpDocument(String source) {
        bind(source, "selection");
        assertTrue(service.post(request));
        verify(stockMapper).insert(any(WmMaterialStock.class));
        verify(txMapper).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void explicitlyBoundRegistrationCannotBeStolenByAnotherReceipt() {
        registeredFor("WM_MATERIAL_RECEIVE", 99L);
        bind("WM_MATERIAL_RECEIVE", "selection");
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void matchingExplicitReceiptRegistrationRemainsUsable() {
        registeredFor("WM_MATERIAL_RECEIVE", 10L);
        bind("WM_MATERIAL_RECEIVE", "selection");
        assertTrue(service.post(request));
    }

    @Test
    void purchaseOrderRegistrationMustMatchTheOriginalReceiptPurchaseOrder() {
        registeredFor("PU_PURCHASE_ORDER", 71L);
        bind("WM_MATERIAL_RECEIVE", "selection");
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void matchingPurchaseOrderRegistrationCanBeReceived() {
        registeredFor("PU_PURCHASE_ORDER", 70L);
        bind("WM_MATERIAL_RECEIVE", "selection");
        assertTrue(service.post(request));
    }

    @Test
    void unknownRegistrationSourceIsNotAssumedToBeErpInventory() {
        registeredFor("UNVERIFIED_IMPORT", null);
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void productionRegistrationNeedsTheFrozenManifestHandoff() {
        registeredFor("MES_MANUAL", 22L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void anotherWorkOrdersManifestCannotAuthorizeProductionReceipt() {
        registeredFor("MES_MANUAL", 22L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        handoff(99, 22, manifestHash);
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void manualMesRegistrationCannotChangeItsMesWorkOrder() {
        registeredFor("MES_MANUAL", 23L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        handoff(10, 22, manifestHash);
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MES_MANUAL", "MES_FLOW_CARD"})
    void matchingManifestHandoffAuthorizesTheSingleProductionReceipt(String source) {
        registeredFor(source, "MES_MANUAL".equals(source) ? 22L : 44L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        handoff(10, 22, manifestHash);
        assertTrue(service.post(request));
        verify(stockMapper).insert(any(WmMaterialStock.class));
        verify(txMapper).insert(any(WmMaterialStockTx.class));
    }

    void originalShipment() {
        when(jdbc.queryForList(
                        contains("FROM wm_material_stock_tx WHERE tenant_id=? AND tx_id=?"),
                        any(Object[].class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "tx_id",
                                        700L,
                                        "tx_flag",
                                        -1,
                                        "tx_quantity",
                                        BigDecimal.ONE,
                                        "tracking_version",
                                        2L,
                                        "source_doc_type",
                                        "WM_SALES_ISSUE",
                                        "source_doc_id",
                                        60L,
                                        "source_doc_line_id",
                                        61L)));
        when(jdbc.queryForObject(contains("related_tx_id"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        item = new SnItem(1L, null, 40L, 700L, 50L);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"WM_MATERIAL_RECEIVE", "WM_MISC_RECPT", "WM_STOCK_TAKING", "PRD_WORK_ORDER"})
    void oldShipmentReferenceCannotDisguiseAnUnrelatedFirstInboundAsAReturn(String source) {
        registeredFor("MES_FLOW_CARD", 44L);
        originalShipment();
        bind(source, "selection");
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void genuineOriginalSalesReturnKeepsItsLifecycleProofForAMesBornPiece() {
        registeredFor("MES_FLOW_CARD", 44L);
        registration.put("status", "OUT");
        originalShipment();
        bind("SO_RETURN", "return-selection");
        when(jdbc.queryForList(contains("FROM so_return WHERE"), any(Object[].class)))
                .thenReturn(List.of(Map.of("so_id", 70L)));
        when(jdbc.queryForList(contains("FROM so_return_line WHERE"), any(Object[].class)))
                .thenReturn(List.of(Map.of("so_line_id", 71L)));
        when(jdbc.queryForList(
                        contains("SELECT h.so_id,l.source_doc_line_id"), any(Object[].class)))
                .thenReturn(List.of(Map.of("so_id", 70L, "source_doc_line_id", 71L)));
        assertTrue(service.post(request));
        verify(txMapper).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void tamperedLocalMesHandoffCannotAuthorizeFirstInventoryReceipt() {
        registeredFor("MES_MANUAL", 22L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        handoff(10, 22, manifestHash);
        handoffs.set(
                0,
                Map.of(
                        "payload",
                        handoffs.get(0).get("payload"),
                        "content_hash",
                        "b".repeat(64),
                        "status",
                        "WAITING"));
        assertThrows(ServiceException.class, () -> service.post(request));
        noInventoryEffects();
    }

    @Test
    void registrationRestrictionDoesNotRecreditOrRereadCurrentStateOnFulfilledReplay() {
        doReturn(7L).when(repository).frozenPolicyVersion(100L);
        registeredFor("MES_FLOW_CARD", 44L);
        bind("PRD_WORK_ORDER", "MES_FINAL/900/1/" + manifestHash);
        doReturn(
                        new WmSerialInventoryRepository.Group(
                                group.id(),
                                group.commandId(),
                                group.detailVersion(),
                                group.fingerprint(),
                                group.contentHash(),
                                group.quantity(),
                                BigDecimal.ONE,
                                "POSTED",
                                200L))
                .when(repository)
                .group(any());
        doNothing().when(repository).requireReplaySource(any());
        doNothing().when(repository).verifyProof(any(), any(), any());
        assertTrue(service.post(request));
        verify(jdbc, never()).queryForList(contains("FROM wm_sn "), any(Object[].class));
        noInventoryEffects();
    }
}
