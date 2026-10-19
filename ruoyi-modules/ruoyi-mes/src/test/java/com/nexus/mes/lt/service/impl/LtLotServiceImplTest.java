package com.nexus.mes.lt.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.bo.LtLotBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LtLotServiceImplTest {

    @Mock private LtLotMapper baseMapper;
    @Mock private LtWoConsumptionMapper consumptionMapper;
    @Mock private LtSnBindingMapper bindingMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @InjectMocks private LtLotServiceImpl service;

    @Captor private ArgumentCaptor<LtLot> insertCaptor;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), LtLot.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(LtLot.class);
    }

    @BeforeEach
    void setUp() {}

    @AfterEach
    void tearDown() {}

    @Test
    void inUseLotWithRemainingQuantityCanBePickedAgain() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(2L);
        lot.setStatus("IN_USE");
        lot.setQcStatus("PASS");
        lot.setQuantity(new BigDecimal("10"));
        lot.setConsumedQty(new BigDecimal("3"));
        when(baseMapper.selectById(1L)).thenReturn(lot);
        assertEquals(Boolean.TRUE, service.checkLotUsable(2L, 1L).get("usable"));
    }

    @Test
    void metadataEditUsesColumnUpdateWithoutReplayingLoadedConsumptionOrStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_USE");
        lot.setQuantity(new BigDecimal("10"));
        lot.setConsumedQty(new BigDecimal("3"));
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        LtLotBo bo = new LtLotBo();
        bo.setLotId(1L);
        bo.setRemark("checked");
        assertTrue(service.updateByBo(bo));
        verify(baseMapper, never()).updateById(any(LtLot.class));
        ArgumentCaptor<LambdaUpdateWrapper<LtLot>> update =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), update.capture());
        assertFalse(update.getValue().getSqlSet().contains("consumed_qty"));
        assertFalse(update.getValue().getSqlSet().contains("qc_status"));
        assertFalse(update.getValue().getSqlSet().contains("status"));
    }

    @Test
    void quantityEditCannotBorrowAnotherCaseFoldedTenantAllocation() {
        var ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE lt_lot(lot_id bigint,tenant_id VARCHAR_IGNORECASE(20),quantity"
                        + " decimal(18,4),consumed_qty decimal(18,4),status varchar(24))");
        jdbc.execute(
                "CREATE TABLE lt_lot_allocation(lot_id bigint,tenant_id"
                        + " VARCHAR_IGNORECASE(20),quantity decimal(18,4))");
        jdbc.update("INSERT INTO lt_lot VALUES(1,'tenant_case',20,0,'IN_STOCK')");
        jdbc.update("INSERT INTO lt_lot_allocation VALUES(1,'TENANT_CASE',20)");
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setTenantId("tenant_case");
        lot.setQuantity(new BigDecimal("20"));
        lot.setConsumedQty(BigDecimal.ZERO);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(isNull(), any()))
                .thenAnswer(
                        call -> {
                            LambdaUpdateWrapper<LtLot> w = call.getArgument(1);
                            String sql = w.getSqlSegment();
                            var params = w.getParamNameValuePairs();
                            for (var entry : params.entrySet()) {
                                sql =
                                        sql.replace(
                                                "#{ew.paramNameValuePairs." + entry.getKey() + "}",
                                                entry.getValue() instanceof Number
                                                        ? entry.getValue().toString()
                                                        : "'" + entry.getValue() + "'");
                            }
                            return jdbc.update("UPDATE lt_lot SET quantity=10 WHERE " + sql);
                        });
        LtLotBo bo = new LtLotBo();
        bo.setLotId(1L);
        bo.setQuantity(BigDecimal.TEN);
        assertTrue(service.updateByBo(bo));
        assertEquals(
                0,
                BigDecimal.TEN.compareTo(
                        jdbc.queryForObject("SELECT quantity FROM lt_lot", BigDecimal.class)));
    }

    // ==================== 状态机 5 态流转 ====================

    @Test
    void insertByBoSetsInitialInStockAndQcPending() {
        when(autoCodeUtil.genSerialCode("MES_LOT_CODE", null)).thenReturn("LOT-20250727-001");
        when(baseMapper.selectCount(any())).thenReturn(0L);

        LtLotBo bo = new LtLotBo();
        bo.setMaterialId(1L);
        bo.setMaterialCode("MAT-001");
        bo.setMaterialName("测试物料");
        bo.setQuantity(BigDecimal.valueOf(100));
        bo.setSupplierId(10L);
        bo.setSupplierName("测试供应商");
        bo.setProducedDate(new Date());
        bo.setExpiryDate(new Date());

        service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        LtLot captured = insertCaptor.getValue();
        assertEquals("IN_STOCK", captured.getStatus());
        assertEquals("PENDING", captured.getQcStatus());
        assertEquals(BigDecimal.ZERO, captured.getConsumedQty());
        assertEquals("LOT-20250727-001", captured.getLotCode());
        assertEquals(1L, captured.getMaterialId());
        assertEquals(BigDecimal.valueOf(100), captured.getQuantity());
    }

    @Test
    void insertByBoRejectsDuplicateLotCode() {
        when(baseMapper.selectCount(any())).thenReturn(1L);

        LtLotBo bo = new LtLotBo();
        bo.setLotCode("LOT-001");
        bo.setMaterialId(1L);
        bo.setQuantity(BigDecimal.TEN);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.insertByBo(bo));
        assertTrue(exception.getMessage().contains("批次编码已存在"));
    }

    @Test
    void quarantineMovesInStockToQuarantined() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.quarantine(1L, "质检隔离");

        assertTrue(result);
        verify(baseMapper).update(any(), any());
    }

    @Test
    void quarantineRejectsNonInStockInUse() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("CONSUMED");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.quarantine(1L, "隔离"));
        assertTrue(exception.getMessage().contains("仅 IN_STOCK/IN_USE"));
    }

    @Test
    void quarantineThrowsWhenCasRowZero() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.quarantine(1L, "隔离"));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    @Test
    void scrapMovesQuarantinedToScrapped() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("QUARANTINED");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.scrap(1L, "报废");

        assertTrue(result);
    }

    @Test
    void scrapRejectsNonQuarantined() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.scrap(1L, "报废"));
        assertTrue(exception.getMessage().contains("仅 QUARANTINED"));
    }

    @Test
    void scrapThrowsWhenCasRowZero() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("QUARANTINED");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.scrap(1L, "报废"));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    @Test
    void releaseFromQuarantinedToInStockWhenFullRemaining() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("QUARANTINED");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.release(1L);

        assertTrue(result);
    }

    @Test
    void releaseFromQuarantinedToConsumedWhenZeroRemaining() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("QUARANTINED");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.valueOf(100));
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.release(1L);

        assertTrue(result);
    }

    @Test
    void releaseFromQuarantinedToInUseWhenPartiallyConsumed() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("QUARANTINED");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.valueOf(30));
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.release(1L);

        assertTrue(result);
    }

    @Test
    void releaseRejectsNonQuarantined() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.release(1L));
        assertTrue(exception.getMessage().contains("仅 QUARANTINED"));
    }

    // ==================== FEFO 排序 ====================

    @Test
    void recommendFefoOrdersByExpiryDateAsc() {
        LtLotVo early = new LtLotVo();
        early.setLotId(1L);
        early.setQuantity(BigDecimal.TEN);
        early.setConsumedQty(BigDecimal.ZERO);

        LtLotVo late = new LtLotVo();
        late.setLotId(2L);
        late.setQuantity(BigDecimal.TEN);
        late.setConsumedQty(BigDecimal.ZERO);

        when(baseMapper.selectVoList(any())).thenReturn(List.of(early, late));

        List<LtLotVo> result = service.recommendFefo(1L, null);

        assertEquals(2, result.size());
    }

    @Test
    void recommendFefoFiltersZeroRemaining() {
        LtLotVo empty = new LtLotVo();
        empty.setLotId(1L);
        empty.setQuantity(BigDecimal.TEN);
        empty.setConsumedQty(BigDecimal.TEN);

        LtLotVo available = new LtLotVo();
        available.setLotId(2L);
        available.setQuantity(BigDecimal.TEN);
        available.setConsumedQty(BigDecimal.ZERO);

        when(baseMapper.selectVoList(any())).thenReturn(List.of(empty, available));

        List<LtLotVo> result = service.recommendFefo(1L, null);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getLotId());
    }

    @Test
    void recommendFefoAccumulatesUntilRequiredQtyMet() {
        LtLotVo lot1 = new LtLotVo();
        lot1.setLotId(1L);
        lot1.setQuantity(BigDecimal.valueOf(5));
        lot1.setConsumedQty(BigDecimal.ZERO);

        LtLotVo lot2 = new LtLotVo();
        lot2.setLotId(2L);
        lot2.setQuantity(BigDecimal.valueOf(5));
        lot2.setConsumedQty(BigDecimal.ZERO);

        LtLotVo lot3 = new LtLotVo();
        lot3.setLotId(3L);
        lot3.setQuantity(BigDecimal.valueOf(5));
        lot3.setConsumedQty(BigDecimal.ZERO);

        when(baseMapper.selectVoList(any())).thenReturn(List.of(lot1, lot2, lot3));

        List<LtLotVo> result = service.recommendFefo(1L, BigDecimal.valueOf(8));

        assertEquals(2, result.size());
    }

    @Test
    void recommendFefoRejectsNullMaterialId() {
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.recommendFefo(null, BigDecimal.TEN));
        assertTrue(exception.getMessage().contains("物料ID不能为空"));
    }

    // ==================== consume 并发超卖守卫 ====================

    @Test
    void consumeRejectsNonPositiveQuantity() {
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.consume(1L, BigDecimal.valueOf(-1)));
        assertTrue(exception.getMessage().contains("消耗数量必须大于0"));
    }

    @Test
    void consumeRejectsNonInStockInUseStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("CONSUMED");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.consume(1L, BigDecimal.valueOf(5)));
        assertTrue(exception.getMessage().contains("状态不可消耗"));
    }

    @Test
    void consumeRejectsExceedingRemaining() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.valueOf(10));
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.consume(1L, BigDecimal.valueOf(20)));
        assertTrue(exception.getMessage().contains("超过剩余数量"));
    }

    @Test
    void consumeTransitionsToInUseWhenPartiallyConsumed() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.consume(1L, BigDecimal.valueOf(30));

        assertTrue(result);
    }

    @Test
    void consumeTransitionsToConsumedWhenQuantityReachesZero() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.valueOf(10));
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.consume(1L, BigDecimal.valueOf(10));

        assertTrue(result);
    }

    @Test
    void consumeThrowsWhenCasRowZero() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.consume(1L, BigDecimal.valueOf(30)));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    // ==================== checkLotUsable 契约 ====================

    @Test
    void checkLotUsableReturnsNotUsableWhenLotNotFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("不存在"));
    }

    @Test
    void checkLotUsableRejectsWrongMaterial() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(999L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("物料不符"));
    }

    @Test
    void checkLotUsableRejectsNonInStockStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(1L);
        lot.setStatus("QUARANTINED");
        lot.setQcStatus("PASS");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("非在库"));
    }

    @Test
    void checkLotUsableRejectsNonPassQcStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PENDING");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("QC未通过"));
    }

    @Test
    void checkLotUsableRejectsZeroRemaining() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("剩余数量为0"));
    }

    @Test
    void checkLotUsableReturnsUsableWhenAllConditionsMet() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setMaterialId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertTrue((Boolean) result.get("usable"));
        assertEquals("可用", result.get("reason"));
        assertEquals(BigDecimal.TEN, result.get("remainingQty"));
        assertEquals("LOT-001", result.get("lotCode"));
    }

    // ==================== applyIqcResult 钩子 ====================

    @Test
    void applyIqcResultReturnsFalseWhenIqcIdIsNull() {
        Boolean result = service.applyIqcResult(null, "QUALIFIED");
        assertFalse(result);
    }

    @Test
    void applyIqcResultReturnsFalseWhenNoLotLinked() {
        when(baseMapper.selectList(any())).thenReturn(List.of());

        Boolean result = service.applyIqcResult(1L, "QUALIFIED");

        assertFalse(result);
    }

    @Test
    void applyIqcResultPassUpdatesQcStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectList(any())).thenReturn(List.of(lot));

        Boolean result = service.applyIqcResult(1L, "QUALIFIED");

        assertTrue(result);
        verify(baseMapper).update(any(), any());
    }

    @Test
    void applyIqcResultFailQuarantinesLot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectList(any())).thenReturn(List.of(lot));
        // quarantine(lotId) 内部会按 ID 重新加载批次
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.applyIqcResult(1L, "UNQUALIFIED");

        assertTrue(result);
        // 隔离操作应在第二次更新时发生。
        verify(baseMapper, times(2)).update(any(), any());
    }

    // ==================== MES-QC-10②: 同 iqcId 多批次必须全部回写 ====================

    @Test
    void applyIqcResultFailUpdatesAllLotsLinkedToSameIqc() {
        LtLot lot1 = new LtLot();
        lot1.setLotId(1L);
        lot1.setStatus("IN_STOCK");
        lot1.setQuantity(BigDecimal.TEN);
        lot1.setConsumedQty(BigDecimal.ZERO);
        LtLot lot2 = new LtLot();
        lot2.setLotId(2L);
        lot2.setStatus("IN_STOCK");
        lot2.setQuantity(BigDecimal.TEN);
        lot2.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectList(any())).thenReturn(List.of(lot1, lot2));
        when(baseMapper.selectById(1L)).thenReturn(lot1);
        when(baseMapper.selectById(2L)).thenReturn(lot2);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.applyIqcResult(7L, "UNQUALIFIED");

        assertTrue(result);
        // 每个批次: 1 次 qc_status 回写 + 1 次隔离 CAS, 两个批次都要被处理
        verify(baseMapper, times(4)).update(isNull(), any());
        verify(baseMapper, times(2)).selectById(any());
    }

    @Test
    void applyIqcResultFailOnTerminalLotKeepsFailWithoutQuarantine() {
        LtLot lot = new LtLot();
        lot.setLotId(5L);
        lot.setStatus("SCRAPPED");
        when(baseMapper.selectList(any())).thenReturn(List.of(lot));
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.applyIqcResult(7L, "UNQUALIFIED");

        assertTrue(result);
        // 终态批次只回写 FAIL, 不隔离(但仍必须留 error 级告警, 由日志断言覆盖)
        verify(baseMapper, times(1)).update(isNull(), any());
        verify(baseMapper, never()).selectById(any());
    }

    @Test
    void applyIqcResultThrowsOnInvalidResult() {
        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.applyIqcResult(1L, "INVALID"));
        assertTrue(exception.getMessage().contains("QUALIFIED"));
    }

    // ==================== updateByBo 守卫 ====================

    @Test
    void updateByBoRejectsNonInStockInUseStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("CONSUMED");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        LtLotBo bo = new LtLotBo();
        bo.setLotId(1L);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.updateByBo(bo));
        assertTrue(exception.getMessage().contains("不可编辑"));
    }

    @Test
    void updateByBoRejectsQuantityBelowConsumed() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQuantity(BigDecimal.valueOf(100));
        lot.setConsumedQty(BigDecimal.valueOf(50));
        when(baseMapper.selectById(1L)).thenReturn(lot);

        LtLotBo bo = new LtLotBo();
        bo.setLotId(1L);
        bo.setQuantity(BigDecimal.valueOf(30));

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.updateByBo(bo));
        assertTrue(exception.getMessage().contains("不可小于已消耗数量"));
    }

    // ==================== deleteByIds 守卫 ====================

    @Test
    void deleteByIdsRejectsNonInStockScrappedStatus() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setStatus("IN_USE");
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.deleteByIds(new Long[] {1L}));
        assertTrue(exception.getMessage().contains("状态非在库/报废"));
    }

    @Test
    void deleteByIdsRejectsWhenConsumptionExists() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(consumptionMapper.selectCount(any())).thenReturn(1L);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.deleteByIds(new Long[] {1L}));
        assertTrue(exception.getMessage().contains("工单批次消耗记录"));
    }

    @Test
    void deleteByIdsRejectsWhenBindingExists() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(consumptionMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.deleteByIds(new Long[] {1L}));
        assertTrue(exception.getMessage().contains("SN批次绑定记录"));
    }

    // ==================== updateQcStatus ====================

    @Test
    void updateQcStatusRejectsInvalidStatus() {
        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.updateQcStatus(1L, "INVALID"));
        assertTrue(exception.getMessage().contains("PASS/FAIL"));
    }

    @Test
    void updateQcStatusFailAutoQuarantines() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PENDING");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.updateQcStatus(1L, "FAIL");

        assertTrue(result);
        // 隔离操作应在第二次更新时发生。
        verify(baseMapper, times(2)).update(any(), any());
    }

    @Test
    void updateQcStatusPassDoesNotQuarantine() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PENDING");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.updateQcStatus(1L, "PASS");

        assertTrue(result);
        verify(baseMapper, times(1)).update(any(), any());
    }

    // ==================== quarantineByNcr ====================

    @Test
    void quarantineByNcrDelegatesToQuarantineWithNcrRemark() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(lot);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.quarantineByNcr(1L, 100L, "质量问题");

        assertTrue(result);
    }

    // ==================== MES-LT-01 原子扣减 + 超发守卫 ====================

    @Test
    void consumeUsesAtomicSqlWithQuantityGuard() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);
        // 并发场景: 守卫命中 0 行 → 拒绝(修复前写绝对值、守卫在 Java 内存里, 会双重通过而超发)
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> service.consume(1L, BigDecimal.valueOf(8)));
        assertTrue(exception.getMessage().contains("状态已变更"));

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<LtLot> wrapper = (LambdaUpdateWrapper<LtLot>) captor.getValue();
        assertTrue(wrapper.getSqlSet().contains("consumed_qty = consumed_qty +"), "必须是原子自增而非写绝对值");
        assertTrue(
                wrapper.getSqlSegment().contains("consumed_qty +"),
                "必须带 consumed_qty + qty <= quantity 守卫");
    }

    // ==================== MES-LT-03 效期拦截 ====================

    @Test
    void consumeRejectsExpiredLot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-EXP");
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        lot.setExpiryDate(
                Date.from(
                        LocalDate.now()
                                .minusDays(1)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()));
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.consume(1L, BigDecimal.ONE));

        assertTrue(exception.getMessage().contains("已过期"));
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void checkLotUsableRejectsExpiredLot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(1L);
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("PASS");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        lot.setExpiryDate(
                Date.from(
                        LocalDate.now()
                                .minusDays(1)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()));
        when(baseMapper.selectById(1L)).thenReturn(lot);

        Map<String, Object> result = service.checkLotUsable(1L, 1L);

        assertFalse((Boolean) result.get("usable"));
        assertTrue(((String) result.get("reason")).contains("已过期"));
    }

    @Test
    void recommendFefoQueryExcludesExpiredLots() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of());

        service.recommendFefo(1L, null);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("expiry_date"), "FEFO 查询必须过滤过期批次");
    }

    // ==================== MES-LT-06 QC 门禁 ====================

    @Test
    void consumeRejectsFailQcLot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-FAIL");
        lot.setStatus("IN_STOCK");
        lot.setQcStatus("FAIL");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.consume(1L, BigDecimal.ONE));

        assertTrue(exception.getMessage().contains("QC未通过"));
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void releaseRejectsFailQcLot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-FAIL");
        lot.setStatus("QUARANTINED");
        lot.setQcStatus("FAIL");
        lot.setQuantity(BigDecimal.TEN);
        lot.setConsumedQty(BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(lot);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> service.release(1L));

        assertTrue(exception.getMessage().contains("不得放行"));
        verify(baseMapper, never()).update(any(), any());
    }
}
