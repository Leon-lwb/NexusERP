package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import com.nexus.mes.eq.mapper.ErpSpareStockQueryMapper;
import com.nexus.mes.eq.mq.EqSpareErpMqPublisher;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 备件出入库单测。
 * <p>MES-EQ-14: 流水的 before_qty/after_qty 必须取原子 SQL 落库后的回读值,
 * 而非 Java 先算值, 否则并发出入库下流水与 eq_spare_part.current_stock 不一致。</p>
 * <p>MES-EQ-04（本批次新增）: 备件出入库必须触发 ERP 过账（幂等业务键）、库存不足拒绝且不留半成品、
 * 映射缺失显式拒绝、低库存按 ERP 可用量（在库 − 已预留且排除冻结）判定。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqSparePartServiceImplTest {

    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private EqSpareStockLogMapper stockLogMapper;
    @Mock
    private EqSpareErpSyncMapper erpSyncMapper;
    @Mock
    private ErpSpareStockQueryMapper erpSpareStockQueryMapper;
    @Mock
    private EqSpareErpMqPublisher erpMqPublisher;
    @InjectMocks
    private EqSparePartServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSparePart.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSpareStockLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSpareErpSync.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqSparePart.class);
        TableInfoHelper.remove(EqSpareStockLog.class);
        TableInfoHelper.remove(EqSpareErpSync.class);
    }

    @Test
    void stockTransactionOutDeductsFromCurrentStockWithOversellGuard() {
        // stockTransaction 现按 selectVoById 读当前库存余额, 更新走 setSql 原子加减+防超卖守卫
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);

        BigDecimal balance = service.stockTransaction(request("OUT", "3"));

        assertEquals(0, new BigDecimal("7").compareTo(balance));
        // selectVoById 被调用两次: 出入库入口一次 + 事务提交后组装消息体一次
        verify(sparePartMapper, atLeastOnce()).selectVoById(1L);
    }

    @Test
    void stockTransactionWritesBackReadAfterQtyInsteadOfComputedValue() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        // 并发场景: 本事务原子扣减 3 后, 实际库存已是 5(另一笔出库先扣了 2)
        EqSparePart actual = new EqSparePart();
        actual.setSpareId(1L);
        actual.setCurrentStock(new BigDecimal("5"));
        when(sparePartMapper.selectById(1L)).thenReturn(actual);

        BigDecimal balance = service.stockTransaction(request("OUT", "3"));

        // 修复前: Java 先算 before/after(10/7), 与实际库存 5 不符
        assertEquals(0, new BigDecimal("5").compareTo(balance));
        ArgumentCaptor<EqSpareStockLog> captor = ArgumentCaptor.forClass(EqSpareStockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getAfterQty()));
        // 出库: before = after + qty → 流水自洽(after - before == -qty)
        assertEquals(0, new BigDecimal("8").compareTo(captor.getValue().getBeforeQty()));
    }

    @Test
    void stockTransactionFallsBackToComputedQtyWhenReadBackMissing() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        when(sparePartMapper.selectById(1L)).thenReturn(null);

        BigDecimal balance = service.stockTransaction(request("IN", "2"));

        assertEquals(0, new BigDecimal("12").compareTo(balance));
        ArgumentCaptor<EqSpareStockLog> captor = ArgumentCaptor.forClass(EqSpareStockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("12").compareTo(captor.getValue().getAfterQty()));
        assertEquals(0, new BigDecimal("10").compareTo(captor.getValue().getBeforeQty()));
    }

    // ==================== MES-EQ-04: 出入库走 ERP ====================

    @Test
    void stockTransactionPublishesErpPostingWithStableIdempotentBizKey() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        when(sparePartMapper.selectById(1L)).thenReturn(sparePart(new BigDecimal("7")));
        // 模拟主键回填: 流水落库后 logId = 999
        when(stockLogMapper.insert(any(EqSpareStockLog.class))).thenAnswer(invocation -> {
            EqSpareStockLog entity = invocation.getArgument(0);
            entity.setLogId(999L);
            return 1;
        });

        service.stockTransaction(request("REPAIR_USE", "3"));

        // ① 同步台账: 业务幂等键 = EQSP:<tenantId>:<logId>
        ArgumentCaptor<EqSpareErpSync> syncCaptor = ArgumentCaptor.forClass(EqSpareErpSync.class);
        verify(erpSyncMapper).insert(syncCaptor.capture());
        EqSpareErpSync sync = syncCaptor.getValue();
        assertEquals("EQSP:000000:999", sync.getBizKey());
        assertEquals("REPAIR_USE", sync.getTransactionType());
        assertEquals(Long.valueOf(1L), sync.getMaterialId());
        assertEquals(Long.valueOf(2L), sync.getWarehouseId());
        // 注: syncStatus 由后续 markSync 在同一对象上回写(捕获的是引用), 状态断言见下方 updateById 捕获
        // ② 事务外(无事务上下文)立即投递, 消息体带同一幂等键与 ERP 物料/仓库
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpMqPublisher).publish(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("SPARE_STOCK", payload.get("bizType"));
        assertEquals("EQSP:000000:999", payload.get("bizKey"));
        assertEquals("REPAIR_USE", payload.get("transactionType"));
        assertEquals(Long.valueOf(1L), payload.get("materialId"));
        assertEquals(Long.valueOf(2L), payload.get("warehouseId"));
        // ③ 投递成功回写 SENT
        ArgumentCaptor<EqSpareErpSync> updateCaptor = ArgumentCaptor.forClass(EqSpareErpSync.class);
        verify(erpSyncMapper).updateById(updateCaptor.capture());
        assertEquals("SENT", updateCaptor.getValue().getSyncStatus());
    }

    @Test
    void stockTransactionRejectsUnmappedSpareWithoutTouchingAnyLedger() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        spare.setMaterialId(null);
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.stockTransaction(request("OUT", "3")));

        assertTrue(exception.getMessage().contains("未映射 ERP 物料"));
        // 映射缺失必须显式拒绝: 既不改 MES 台账, 也不留流水/同步台账/消息
        verify(sparePartMapper, never()).update(any(), any());
        verifyNoInteractions(stockLogMapper, erpSyncMapper, erpMqPublisher);
    }

    @Test
    void stockTransactionRejectsMissingWarehouseMapping() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        spare.setWarehouseId(null);
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.stockTransaction(request("OUT", "3")));

        assertTrue(exception.getMessage().contains("未指定 ERP 仓库"));
        verifyNoInteractions(stockLogMapper, erpSyncMapper, erpMqPublisher);
    }

    @Test
    void stockTransactionRejectsInsufficientStockAndLeavesNoResidue() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("1"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        // 原子扣减守卫影响 0 行 = 库存不足
        when(sparePartMapper.update(any(), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.stockTransaction(request("OUT", "3")));

        assertTrue(exception.getMessage().contains("库存不足"));
        // 不留半成品: 无流水、无同步台账、无 ERP 消息
        verifyNoInteractions(stockLogMapper, erpSyncMapper, erpMqPublisher);
    }

    @Test
    void stockTransactionMarksSyncFailedWhenPublishFails() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        when(sparePartMapper.selectById(1L)).thenReturn(sparePart(new BigDecimal("7")));
        when(stockLogMapper.insert(any(EqSpareStockLog.class))).thenAnswer(invocation -> {
            EqSpareStockLog entity = invocation.getArgument(0);
            entity.setLogId(1000L);
            return 1;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("broker down")).when(erpMqPublisher).publish(any());

        service.stockTransaction(request("OUT", "3"));

        // 失败可见: 同步台账置 FAILED 并记录失败原因(不静默)
        ArgumentCaptor<EqSpareErpSync> updateCaptor = ArgumentCaptor.forClass(EqSpareErpSync.class);
        verify(erpSyncMapper).updateById(updateCaptor.capture());
        assertEquals("FAILED", updateCaptor.getValue().getSyncStatus());
        assertNotNull(updateCaptor.getValue().getLastError());
    }

    // ==================== MES-EQ-04: 低库存读 ERP 可用量 ====================

    @Test
    void selectLowStockReadsErpAvailableInsteadOfMesCache() {
        EqSparePartVo low = mappedSpare(new BigDecimal("999"));   // MES 缓存很高, ERP 可用只有 3
        low.setSafetyStock(new BigDecimal("5"));
        EqSparePartVo enough = mappedSpare(new BigDecimal("0"));  // MES 缓存为 0, ERP 可用 10 足够
        enough.setSpareId(2L);
        enough.setMaterialId(2L);
        enough.setSafetyStock(new BigDecimal("5"));
        when(sparePartMapper.selectVoList(any())).thenReturn(List.of(low, enough));
        when(erpSpareStockQueryMapper.selectAvailableByMaterialIds(anyString(), any()))
            .thenReturn(List.of(Map.<String, Object>of("materialId", 1L, "availableQty", new BigDecimal("3")),
                Map.<String, Object>of("materialId", 2L, "availableQty", new BigDecimal("10"))));

        List<EqSparePartVo> result = service.selectLowStock();

        assertEquals(1, result.size());
        EqSparePartVo vo = result.get(0);
        assertEquals(Long.valueOf(1L), vo.getSpareId());
        assertEquals("ERP_AVAILABLE", vo.getLowStockBasis());
        assertEquals(0, new BigDecimal("3").compareTo(vo.getErpAvailableStock()));
        assertTrue(vo.getLowStock());
    }

    @Test
    void selectLowStockFlagsUnmappedSpareExplicitly() {
        EqSparePartVo unmapped = mappedSpare(new BigDecimal("100"));
        unmapped.setMaterialId(null);
        unmapped.setSafetyStock(new BigDecimal("5"));
        when(sparePartMapper.selectVoList(any())).thenReturn(List.of(unmapped));

        List<EqSparePartVo> result = service.selectLowStock();

        assertEquals(1, result.size());
        assertEquals("NOT_MAPPED", result.get(0).getLowStockBasis());
        assertEquals(Boolean.FALSE, result.get(0).getErpMapped());
    }

    @Test
    void erpAvailableSqlExcludesReservedAndFrozenRows() throws Exception {
        Method method = ErpSpareStockQueryMapper.class.getMethod("selectAvailableByMaterialIds",
            String.class, Collection.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value());
        // 可用量口径: 在库 − 已预留, 且排除冻结行(MES-EQ-04 / ERP-WM-02 / ERP-WM-09 统一口径)
        assertTrue(sql.contains("reserved_quantity"), sql);
        assertTrue(sql.contains("frozen_flag = 0"), sql);
        assertTrue(sql.contains("stock_quantity"), sql);
        assertTrue(sql.contains("tenant_id = #{tenantId}"), sql);
    }

    @Test
    void generatePurchaseRequestRejectsUnmappedSpare() {
        EqSparePartVo unmapped = mappedSpare(new BigDecimal("1"));
        unmapped.setMaterialId(null);
        when(sparePartMapper.selectVoList(any())).thenReturn(List.of(unmapped));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.generatePurchaseRequest(List.of(1L), "test"));

        assertTrue(exception.getMessage().contains("未映射 ERP 物料"));
        verify(erpMqPublisher, never()).publish(any());
    }

    @Test
    void generatePurchaseRequestPublishesPurchaseMessage() {
        EqSparePartVo spare = mappedSpare(new BigDecimal("2"));
        spare.setSafetyStock(new BigDecimal("10"));
        when(sparePartMapper.selectVoList(any())).thenReturn(List.of(spare));
        when(erpSpareStockQueryMapper.selectAvailableByMaterialIds(anyString(), any()))
            .thenReturn(List.of(Map.<String, Object>of("materialId", 1L, "availableQty", new BigDecimal("2"))));
        when(erpSpareStockQueryMapper.selectMaterial(anyString(), any()))
            .thenReturn(Map.<String, Object>of("materialId", 1L, "materialCode", "M-1",
                "materialName", "物料1", "status", 1L));

        int lines = service.generatePurchaseRequest(List.of(1L), "低库存请购");

        assertEquals(1, lines);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(erpMqPublisher, times(1)).publish(captor.capture());
        assertEquals("SPARE_PURCHASE", captor.getValue().get("bizType"));
        assertNotNull(captor.getValue().get("lines"));
    }

    // ==================== 构造辅助 ====================

    private EqSparePartVo mappedSpare(BigDecimal currentStock) {
        EqSparePartVo spare = new EqSparePartVo();
        spare.setSpareId(1L);
        spare.setSpareCode("SP-001");
        spare.setSpareName("bearing");
        spare.setCurrentStock(currentStock);
        spare.setMaterialId(1L);
        spare.setWarehouseId(2L);
        spare.setSafetyStock(BigDecimal.ZERO);
        return spare;
    }

    private EqSparePart sparePart(BigDecimal currentStock) {
        EqSparePart part = new EqSparePart();
        part.setSpareId(1L);
        part.setCurrentStock(currentStock);
        part.setMaterialId(1L);
        part.setWarehouseId(2L);
        return part;
    }

    private EqSpareStockBo request(String type, String quantity) {
        EqSpareStockBo bo = new EqSpareStockBo();
        bo.setSpareId(1L);
        bo.setTransactionType(type);
        bo.setQuantity(new BigDecimal(quantity));
        return bo;
    }
}
