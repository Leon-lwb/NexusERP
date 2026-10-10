package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import com.nexus.erp.wm.service.mq.MesSpareErpMessage;
import com.nexus.erp.wm.service.mq.WmSpareStockDedupGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MES 备件 → ERP 仓储过账服务单测（MES-EQ-04）。
 * <p>核心断言：① 重复消息幂等（Redis 占位命中 / 流水已存在都不再动库存）；
 * ② 出库只按「可用量 = 在库 − 已预留」扣减，不扣穿他人预留；③ 可用量不足时整单回滚（不写流水、释放幂等占位）；
 * ④ 入库新建库存行必须 {@code version = 0}；⑤ 新建行只用 ERP 主数据，库存及流水的基本单位完整，
 * 资料不可用时拒绝建行并释放幂等占位。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmSpareStockPostingServiceImplTest {

    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private WmMaterialStockTxMapper stockTxMapper;
    @Mock
    private WmSpareStockDedupGuard dedupGuard;
    @Mock
    private IInventoryValuationService valuationService;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private BdUnitOfMeasureMapper unitOfMeasureMapper;
    @Mock
    private WmWarehouseMapper warehouseMapper;
    @InjectMocks
    private WmSpareStockPostingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStockTx.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(WmMaterialStock.class);
        TableInfoHelper.remove(WmMaterialStockTx.class);
    }

    @Test
    void duplicateMessageHitsRedisGuardAndDoesNotTouchStock() {
        when(dedupGuard.tryMark("EQSP:000000:1")).thenReturn(false);
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10L, "10", "0", 0L, 1)));

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:1", "OUT", "3"));

        assertTrue(result.duplicated());
        verify(stockMapper, never()).deductStockAvailable(any(), any(), any());
        verify(stockMapper, never()).addStock(any(), any(), any());
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void lateRedeliveryIsIdempotentByLedgerLookup() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(1L);
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10L, "10", "0", 0L, 1)));

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:2", "OUT", "3"));

        assertTrue(result.duplicated());
        verify(stockMapper, never()).deductStockAvailable(any(), any(), any());
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void outboundDeductsAvailableQuantityOnlyAndWritesLedger() {
        // 在库 10, 已预留 8 → 可用 2; 领用 2 只扣可用量, 不扣穿预留
        WmMaterialStock row = stock(5L, "10", "8", 0L, 3);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(5L)).thenReturn(row);
        when(stockMapper.deductStockAvailable(eq(5L), eq(new BigDecimal("2")), eq(3))).thenReturn(1);

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:3", "REPAIR_USE", "2"));

        assertFalse(result.duplicated());
        verify(stockMapper).deductStockAvailable(5L, new BigDecimal("2"), 3);
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        WmMaterialStockTx tx = captor.getValue();
        assertEquals("MES_SPARE_OUT", tx.getTxType());
        assertEquals(Integer.valueOf(0), tx.getTxFlag());
        assertEquals("EQSP:000000:3", tx.getSourceDocCode());
        assertEquals("MES_SPARE", tx.getSourceDocType());
        assertEquals(0, new BigDecimal("10").compareTo(tx.getBeforeQuantity()));
        assertEquals(0, new BigDecimal("8").compareTo(tx.getAfterQuantity()));
    }

    @Test
    void outboundRejectsWhenErpAvailableInsufficientAndRollsBack() {
        // 在库 10, 已预留 9 → 可用 1, 领用 5 必须整单拒绝
        WmMaterialStock row = stock(5L, "10", "9", 0L, 1);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(5L)).thenReturn(row);
        when(stockMapper.deductStockAvailable(eq(5L), eq(new BigDecimal("1")), eq(1))).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(message("EQSP:000000:4", "OUT", "5")));

        assertTrue(exception.getMessage().contains("可用库存不足"));
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
        // 事务回滚必须释放幂等占位, 否则重投被误判为已过账而永久丢失
        verify(dedupGuard).release("EQSP:000000:4");
    }

    @Test
    void outboundRejectsWhenNoAvailableRowAtAll() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(message("EQSP:000000:5", "OUT", "1")));

        assertTrue(exception.getMessage().contains("无可用量"));
        verify(dedupGuard).release("EQSP:000000:5");
    }

    @Test
    void inboundCreatesStockRowWithVersionZero() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockValidMaterialAndUnit();

        service.postSpareStock(message("EQSP:000000:6", "IN", "4"));

        ArgumentCaptor<WmMaterialStock> captor = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(stockMapper).insert(captor.capture());
        WmMaterialStock created = captor.getValue();
        // 新建库存行必须显式 version=0, 否则乐观锁失效
        assertEquals(Integer.valueOf(0), created.getVersion());
        assertEquals(0, new BigDecimal("4").compareTo(created.getStockQuantity()));
        assertEquals(Long.valueOf(0L), created.getFrozenFlag());
        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        assertEquals("MES_SPARE_IN", txCaptor.getValue().getTxType());
        assertEquals(Integer.valueOf(1), txCaptor.getValue().getTxFlag());
    }

    @Test
    void returnTransactionIsPostedAsInbound() {
        WmMaterialStock row = stock(7L, "10", "0", 0L, 2);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(7L)).thenReturn(row);
        when(stockMapper.addStock(eq(7L), eq(new BigDecimal("2")), eq(2))).thenReturn(1);

        service.postSpareStock(message("EQSP:000000:7", "RETURN", "2"));

        verify(stockMapper).addStock(7L, new BigDecimal("2"), 2);
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        assertEquals("MES_SPARE_IN", captor.getValue().getTxType());
        assertEquals(0, new BigDecimal("12").compareTo(captor.getValue().getAfterQuantity()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN", "RETURN"})
    void newInboundStockUsesErpMasterDataAndWritesCompleteLedger(String transactionType) {
        prepareNewInbound();
        mockValidMaterialAndUnit();
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(2L);
        warehouse.setWarehouseCode("WH-SPARE");
        warehouse.setWarehouseName("备件仓库");
        when(warehouseMapper.selectById(2L)).thenReturn(warehouse);
        when(stockMapper.insert(any(WmMaterialStock.class))).thenAnswer(invocation -> {
            WmMaterialStock created = invocation.getArgument(0);
            created.setStockId(99L);
            return 1;
        });

        service.postSpareStock(messageWithCost("EQSP:000000:master", transactionType, "4", "2.5"));

        ArgumentCaptor<WmMaterialStock> stockCaptor = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(stockMapper).insert(stockCaptor.capture());
        WmMaterialStock created = stockCaptor.getValue();
        assertEquals("ERP-BEARING-6304", created.getMaterialCode());
        assertEquals("深沟球轴承", created.getMaterialName());
        assertEquals("6304-2RS", created.getMaterialSpec());
        assertEquals(11L, created.getCategoryId());
        assertEquals("9007199254740993", created.getBaseUomId());
        assertEquals("件", created.getBaseUomName());
        assertEquals(2L, created.getWarehouseId());
        assertEquals("WH-SPARE", created.getWarehouseCode());
        assertEquals("备件仓库", created.getWarehouseName());
        assertEquals(0, created.getVersion());
        assertEquals(0, new BigDecimal("4").compareTo(created.getStockQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(created.getReservedQuantity()));
        verify(valuationService).updateStockCost(99L, new BigDecimal("4"), new BigDecimal("2.5"));

        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        WmMaterialStockTx tx = txCaptor.getValue();
        assertEquals(created.getMaterialCode(), tx.getMaterialCode());
        assertEquals(created.getMaterialName(), tx.getMaterialName());
        assertEquals(created.getMaterialSpec(), tx.getMaterialSpec());
        assertEquals(created.getBaseUomId(), tx.getBaseUomId());
        assertEquals(created.getBaseUomName(), tx.getBaseUomName());
        assertEquals(created.getWarehouseCode(), tx.getWarehouseCode());
        assertEquals(created.getWarehouseName(), tx.getWarehouseName());
        assertEquals(99L, tx.getMaterialStockId());
        assertEquals("MES_SPARE_IN", tx.getTxType());
        assertEquals(0, new BigDecimal("10.0").compareTo(tx.getTxAmount()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DISABLED", "UNKNOWN_STATUS", "MISSING_CODE", "BLANK_NAME", "MISSING_UOM"})
    void newInboundRejectsMissingOrUnusableMaterialMaster(String problem) {
        prepareNewInbound();
        BdMaterial material = validMaterial();
        switch (problem) {
            case "MISSING" -> material = null;
            case "DISABLED" -> material.setStatus(0L);
            case "UNKNOWN_STATUS" -> material.setStatus(null);
            case "MISSING_CODE" -> material.setMaterialCode(null);
            case "BLANK_NAME" -> material.setMaterialName(" ");
            case "MISSING_UOM" -> material.setBaseUomId(null);
            default -> throw new IllegalArgumentException(problem);
        }
        when(materialMapper.selectById(1L)).thenReturn(material);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(messageWithCost("EQSP:000000:invalid-material", "IN", "4", "2.5")));

        assertTrue(exception.getMessage().contains("物料"));
        assertNewInboundRejected("EQSP:000000:invalid-material");
        verifyNoInteractions(unitOfMeasureMapper, warehouseMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DISABLED", "UNKNOWN_STATUS", "NULL_NAME", "BLANK_NAME"})
    void newInboundRejectsMissingOrUnusableBaseUnit(String problem) {
        prepareNewInbound();
        when(materialMapper.selectById(1L)).thenReturn(validMaterial());
        BdUnitOfMeasure unit = validUnit();
        switch (problem) {
            case "MISSING" -> unit = null;
            case "DISABLED" -> unit.setStatus(0L);
            case "UNKNOWN_STATUS" -> unit.setStatus(null);
            case "NULL_NAME" -> unit.setUomName(null);
            case "BLANK_NAME" -> unit.setUomName(" ");
            default -> throw new IllegalArgumentException(problem);
        }
        when(unitOfMeasureMapper.selectById(9007199254740993L)).thenReturn(unit);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(messageWithCost("EQSP:000000:invalid-unit", "IN", "4", "2.5")));

        assertTrue(exception.getMessage().contains("计量单位"));
        assertNewInboundRejected("EQSP:000000:invalid-unit");
        verifyNoInteractions(warehouseMapper);
    }

    @Test
    void existingInboundUsesStockMetadataWithoutReadingMasterData() {
        WmMaterialStock row = stock(7L, "10", "2", 0L, 2);
        row.setMaterialSpec("既有库存规格");
        row.setBaseUomId("3");
        row.setBaseUomName("套");
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(7L)).thenReturn(row);
        when(stockMapper.addStock(7L, new BigDecimal("2"), 2)).thenReturn(1);

        service.postSpareStock(messageWithCost("EQSP:000000:existing-master", "RETURN", "2", "3.5"));

        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        WmMaterialStockTx tx = txCaptor.getValue();
        assertEquals("M-1", tx.getMaterialCode());
        assertEquals("物料1", tx.getMaterialName());
        assertEquals("既有库存规格", tx.getMaterialSpec());
        assertEquals("3", tx.getBaseUomId());
        assertEquals("套", tx.getBaseUomName());
        assertEquals(0, new BigDecimal("12").compareTo(tx.getAfterQuantity()));
        assertEquals(0, new BigDecimal("2").compareTo(row.getReservedQuantity()));
        assertEquals(2, row.getVersion());
        verify(valuationService).updateStockCost(7L, new BigDecimal("2"), new BigDecimal("3.5"));
        verifyNoInteractions(materialMapper, unitOfMeasureMapper, warehouseMapper);
    }

    @Test
    void newInboundDoesNotRequireOptionalWarehouseMetadata() {
        prepareNewInbound();
        mockValidMaterialAndUnit();

        service.postSpareStock(message("EQSP:000000:optional-warehouse", "IN", "4"));

        ArgumentCaptor<WmMaterialStock> stockCaptor = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(stockMapper).insert(stockCaptor.capture());
        assertEquals("9007199254740993", stockCaptor.getValue().getBaseUomId());
        assertEquals("件", stockCaptor.getValue().getBaseUomName());
        assertEquals(2L, stockCaptor.getValue().getWarehouseId());
    }

    // ==================== MES-EQ-04 收口: 移动平均计价 ====================

    @Test
    void inboundAppliesMovingAverageCostWhenUnitCostPresent() {
        WmMaterialStock row = stock(7L, "10", "0", 0L, 2);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(7L)).thenReturn(row);
        when(stockMapper.addStock(eq(7L), eq(new BigDecimal("2")), eq(2))).thenReturn(1);

        service.postSpareStock(messageWithCost("EQSP:000000:8", "IN", "2", "3.5"));

        // 数量原子增加成功后按移动平均计价(与数量增减同一事务, 异常即整单回滚)
        verify(valuationService).updateStockCost(7L, new BigDecimal("2"), new BigDecimal("3.5"));
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("3.5").compareTo(captor.getValue().getUnitCost()));
        assertEquals(0, new BigDecimal("7.0").compareTo(captor.getValue().getTxAmount()));
    }

    @Test
    void inboundSkipsValuationWhenUnitCostMissingOrZero() {
        WmMaterialStock row = stock(7L, "10", "0", 0L, 2);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(7L)).thenReturn(row);
        when(stockMapper.addStock(eq(7L), eq(new BigDecimal("2")), eq(2))).thenReturn(1);

        service.postSpareStock(messageWithCost("EQSP:000000:9", "IN", "2", "0"));

        // 单价 0 必须跳过计价: 用 0 计价会把库存均价拉低成 0
        verify(valuationService, never()).updateStockCost(any(), any(), any());
    }

    @Test
    void inboundCreatesStockRowAndInitialisesCostViaMovingAverage() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockValidMaterialAndUnit();

        service.postSpareStock(messageWithCost("EQSP:000000:10", "IN", "4", "2.5"));

        // 新建库存行按 updateStockCost 语义设初值(数量已含本次入库 → unit_cost = 本次单价)
        verify(valuationService).updateStockCost(any(), eq(new BigDecimal("4")), eq(new BigDecimal("2.5")));
    }

    @Test
    void outboundWritesIssuedMovingAverageCostToLedger() {
        WmMaterialStock row = stock(5L, "10", "8", 0L, 3);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(5L)).thenReturn(row);
        when(stockMapper.deductStockAvailable(eq(5L), eq(new BigDecimal("2")), eq(3))).thenReturn(1);
        when(valuationService.issueStockCost(5L, new BigDecimal("2"))).thenReturn(new BigDecimal("7.5"));

        service.postSpareStock(message("EQSP:000000:11", "REPAIR_USE", "2"));

        // 出库流水 unit_cost 必须写出库移动平均单位成本(替换旧的"库存行快照 unit_cost"行为)
        verify(valuationService).issueStockCost(5L, new BigDecimal("2"));
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("7.5").compareTo(captor.getValue().getUnitCost()));
        assertEquals(0, new BigDecimal("15.0").compareTo(captor.getValue().getTxAmount()));
    }

    @Test
    void messageFromMapParsesUnitCostContract() {
        Map<String, Object> map = new HashMap<>();
        map.put("bizKey", "EQSP:000000:12");
        map.put("quantity", "2");
        map.put("unitCost", "12.5000");

        MesSpareErpMessage parsed = MesSpareErpMessage.fromMap(map);

        assertNotNull(parsed);
        assertEquals(0, new BigDecimal("12.5000").compareTo(parsed.getUnitCost()));
    }

    private void prepareNewInbound() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    private void mockValidMaterialAndUnit() {
        when(materialMapper.selectById(1L)).thenReturn(validMaterial());
        when(unitOfMeasureMapper.selectById(9007199254740993L)).thenReturn(validUnit());
    }

    private BdMaterial validMaterial() {
        BdMaterial material = new BdMaterial();
        material.setMaterialId(1L);
        material.setMaterialCode("ERP-BEARING-6304");
        material.setMaterialName("深沟球轴承");
        material.setMaterialSpec("6304-2RS");
        material.setCategoryId(11L);
        material.setBaseUomId(9007199254740993L);
        material.setStatus(1L);
        return material;
    }

    private BdUnitOfMeasure validUnit() {
        BdUnitOfMeasure unit = new BdUnitOfMeasure();
        unit.setUomId(9007199254740993L);
        unit.setUomName("件");
        unit.setUomSymbol("pcs");
        unit.setPrimaryName("不应使用的换算单位名称");
        unit.setStatus(1L);
        return unit;
    }

    private void assertNewInboundRejected(String bizKey) {
        verify(stockMapper, never()).insert(any(WmMaterialStock.class));
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
        verifyNoInteractions(valuationService);
        verify(dedupGuard).release(bizKey);
    }

    private MesSpareErpMessage messageWithCost(String bizKey, String txnType, String qty, String unitCost) {
        MesSpareErpMessage message = message(bizKey, txnType, qty);
        message.setUnitCost(new BigDecimal(unitCost));
        return message;
    }

    private WmMaterialStock stock(Long stockId, String stockQty, String reservedQty, Long frozenFlag, Integer version) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(stockId);
        stock.setMaterialId(1L);
        stock.setWarehouseId(2L);
        stock.setMaterialCode("M-1");
        stock.setMaterialName("物料1");
        stock.setStockQuantity(new BigDecimal(stockQty));
        stock.setReservedQuantity(new BigDecimal(reservedQty));
        stock.setFrozenFlag(frozenFlag);
        stock.setVersion(version);
        stock.setUnitCost(BigDecimal.ZERO);
        stock.setTotalCost(BigDecimal.ZERO);
        return stock;
    }

    private MesSpareErpMessage message(String bizKey, String txnType, String qty) {
        MesSpareErpMessage message = new MesSpareErpMessage();
        message.setBizType(MesSpareErpMessage.BIZ_STOCK);
        message.setTenantId("000000");
        message.setBizKey(bizKey);
        message.setSpareId(100L);
        message.setSpareCode("SP-001");
        message.setSpareName("bearing");
        message.setMaterialId(1L);
        message.setWarehouseId(2L);
        message.setTransactionType(txnType);
        message.setQuantity(new BigDecimal(qty));
        message.setRefType("REPAIR");
        message.setRefId(200L);
        return message;
    }
}
