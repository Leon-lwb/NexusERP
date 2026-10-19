package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.impl.WmTraceServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WmTraceServiceImplTest {
    private final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
    private final WmSnMapper snMapper = mock(WmSnMapper.class);
    private final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
    private final WmTraceServiceImpl service =
            new WmTraceServiceImpl(txMapper, snMapper, batchMapper);

    @BeforeAll
    static void initTables() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialStockTx.class);
        TableInfoHelper.initTableInfo(assistant, WmSn.class);
        TableInfoHelper.initTableInfo(assistant, WmBatch.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingSnFailsBeforeAnyBroadTransactionOrBatchLookup(boolean forward) {
        assertThrows(ServiceException.class, () -> trace(forward, 100L, "B001", "MISSING"));
        verifyNoInteractions(txMapper, batchMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsSnMaterialAndBatchMismatch(boolean forward) {
        when(snMapper.selectOne(any(Wrapper.class))).thenReturn(sn());
        assertThrows(ServiceException.class, () -> trace(forward, 999L, "B001", "SN001"));
        assertThrows(ServiceException.class, () -> trace(forward, 100L, "OTHER", "SN001"));
        verifyNoInteractions(txMapper, batchMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exactSnFiltersByStableIdentityAcrossHistoricalBatchesAndLabelsScope(boolean forward) {
        when(snMapper.selectOne(any(Wrapper.class))).thenReturn(sn());
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        WmTraceResultVo result = trace(forward, 100L, null, "SN001");
        var queries = queries();
        String sql = queries.get(0).getSqlSegment();
        assertTrue(sql.contains("sn_id"), sql);
        assertFalse(
                sql.contains("batch_code"),
                "Exact SN history must not be limited to its current batch: " + sql);
        assertTrue(sql.contains("sn_code"), sql);
        assertTrue(sql.contains("sn_id IS NULL AND sn_code"), sql);
        assertTrue(queries.get(0).getParamNameValuePairs().containsValue(71L), sql);
        assertTrue(queries.get(0).getParamNameValuePairs().containsValue("SN001"), sql);
        assertTrue(result.getTraceKey().contains("SCOPE:EXACT_SN"), result.getTraceKey());
        assertEquals("SN001", result.getTree().getSnCode());
        assertEquals("B001", result.getTree().getBatchCode());
        verifyNoInteractions(batchMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void incompleteSnMetadataDoesNotEraseOriginalTransactionMaterial(boolean forward) {
        WmSn serial = sn();
        serial.setMaterialCode(null);
        serial.setMaterialName(null);
        when(snMapper.selectOne(any(Wrapper.class))).thenReturn(serial);
        WmMaterialStockTx original = tx(1L, 100L, forward ? -1 : 1);
        original.setMaterialCode("ORIGINAL-MATERIAL");
        original.setMaterialName("Original material name");
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(txMapper.selectOne(any(Wrapper.class))).thenReturn(original);
        WmTraceResultVo result = trace(forward, 100L, null, "SN001");
        assertEquals("ORIGINAL-MATERIAL", result.getTree().getMaterialCode());
        assertEquals("Original material name", result.getTree().getMaterialName());
        assertEquals("ORIGINAL-MATERIAL", original.getMaterialCode());
    }

    @Test
    void forwardReadsCurrentMinusOneOutboundDirection() {
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        WmTraceResultVo result = trace(true, 100L, "B001", null);
        var first = queries().get(0);
        first.getSqlSegment();
        assertTrue(first.getParamNameValuePairs().containsValue(-1));
        assertFalse(first.getParamNameValuePairs().containsValue(2));
        assertTrue(result.getTraceKey().contains("SCOPE:BATCH"), result.getTraceKey());
    }

    @Test
    void backwardLinksCurrentOutboundDirectionAndPreservesOriginalTransaction() {
        WmMaterialStockTx original = tx(1L, 100L, 1);
        original.setSourceDocType("PRODUCTION");
        original.setSourceDocId(90L);
        original.setSourceDocCode("ORIGINAL-90");
        original.setTxQuantity(new BigDecimal("2.5"));
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original), List.of());
        trace(false, 100L, "B001", null);
        var queries = queries();
        assertEquals(2, queries.size());
        queries.get(1).getSqlSegment();
        Map<String, Object> parameters = queries.get(1).getParamNameValuePairs();
        assertTrue(parameters.containsValue(-1));
        assertFalse(parameters.containsValue(2));
        assertEquals("ORIGINAL-90", original.getSourceDocCode());
        assertEquals(new BigDecimal("2.5"), original.getTxQuantity());
        verify(txMapper, never()).updateById(any(WmMaterialStockTx.class));
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsRowTruncationInExistingTraceKey(boolean forward) {
        List<WmMaterialStockTx> transactions =
                IntStream.rangeClosed(1, 201)
                        .mapToObj(i -> tx((long) i, 100L, forward ? -1 : 1))
                        .toList();
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(transactions, List.of());
        WmTraceResultVo result = trace(forward, 100L, "B001", null);
        assertTrue(result.getTraceKey().contains("TRUNCATED:ROWS_200"), result.getTraceKey());
        assertTrue(queries().get(0).getSqlSegment().contains("LIMIT 201"));
        assertEquals(201, transactions.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exactlyTwoHundredRowsAreNotFalselyMarkedTruncated(boolean forward) {
        List<WmMaterialStockTx> transactions =
                IntStream.rangeClosed(1, 200)
                        .mapToObj(i -> tx((long) i, 100L, forward ? -1 : 1))
                        .toList();
        when(txMapper.selectList(any(Wrapper.class))).thenReturn(transactions, List.of());
        WmTraceResultVo result = trace(forward, 100L, "B001", null);
        assertFalse(result.getTraceKey().contains("TRUNCATED"), result.getTraceKey());
    }

    @Test
    void linkedSerialTransactionKeepsItsExactSnInsteadOfWideningToBatch() {
        WmMaterialStockTx input = tx(1L, 100L, -1);
        input.setSourceDocType("PRODUCTION");
        input.setSourceDocId(90L);
        WmMaterialStockTx output = tx(2L, 200L, 1);
        output.setSnId(72L);
        output.setSnCode("SN002");
        output.setBatchCode("B002");
        output.setSourceDocType("PRODUCTION");
        output.setSourceDocId(90L);
        WmSn related = sn();
        related.setSnId(72L);
        related.setSnCode("SN002");
        related.setMaterialId(200L);
        related.setBatchCode("B002");
        when(snMapper.selectOne(any(Wrapper.class))).thenReturn(related);
        when(txMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(input), List.of(output), List.of());
        WmTraceResultVo result = trace(true, 100L, "B001", null);
        assertEquals("SN002", result.getTree().getChildren().get(0).getSnCode());
        var relatedQuery = queries().get(2);
        assertTrue(relatedQuery.getSqlSegment().contains("sn_id"));
        assertTrue(relatedQuery.getParamNameValuePairs().containsValue(72L));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsDepthTruncationWithoutChangingOriginalTransactions(boolean forward) {
        var responses = new java.util.ArrayDeque<List<WmMaterialStockTx>>();
        for (int depth = 0; depth < 10; depth++) {
            WmMaterialStockTx current = tx(2L * depth + 1, 100L + depth, forward ? -1 : 1);
            current.setSourceDocId(1000L + depth);
            current.setSourceDocType("PRODUCTION");
            WmMaterialStockTx linked = tx(2L * depth + 2, 101L + depth, forward ? 1 : -1);
            linked.setSourceDocId(1000L + depth);
            linked.setSourceDocType("PRODUCTION");
            responses.add(List.of(current));
            responses.add(List.of(linked));
        }
        when(txMapper.selectList(any(Wrapper.class))).thenAnswer(call -> responses.removeFirst());
        WmTraceResultVo result = trace(forward, 100L, "B001", null);
        assertTrue(result.getTraceKey().contains("TRUNCATED:DEPTH_10"), result.getTraceKey());
        assertTrue(responses.isEmpty());
        verify(txMapper, never()).updateById(any(WmMaterialStockTx.class));
    }

    @Test
    void exactSnToUnserializedOutputClearlyLabelsRelatedBatchScope() {
        WmMaterialStockTx input = tx(1L, 100L, -1);
        input.setSourceDocType("PRODUCTION");
        input.setSourceDocId(90L);
        input.setSnId(71L);
        input.setSnCode("SN001");
        WmMaterialStockTx output = tx(2L, 200L, 1);
        output.setBatchCode("B002");
        output.setSourceDocType("PRODUCTION");
        output.setSourceDocId(90L);
        when(snMapper.selectOne(any(Wrapper.class))).thenReturn(sn());
        when(txMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(input), List.of(output), List.of());
        WmTraceResultVo result = trace(true, 100L, "B001", "SN001");
        assertTrue(result.getTraceKey().contains("SCOPE:EXACT_SN"));
        assertTrue(result.getTraceKey().contains("RELATED_SCOPE:BATCH"));
        assertNull(result.getTree().getChildren().get(0).getSnCode());
        assertEquals("B002", result.getTree().getChildren().get(0).getBatchCode());
    }

    private WmTraceResultVo trace(boolean forward, Long material, String batch, String sn) {
        return forward
                ? service.traceForward(material, batch, sn)
                : service.traceBackward(material, batch, sn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<LambdaQueryWrapper<WmMaterialStockTx>> queries() {
        ArgumentCaptor<Wrapper<WmMaterialStockTx>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(txMapper, atLeastOnce()).selectList(captor.capture());
        return captor.getAllValues().stream()
                .map(wrapper -> (LambdaQueryWrapper<WmMaterialStockTx>) wrapper)
                .toList();
    }

    private WmSn sn() {
        WmSn sn = new WmSn();
        sn.setSnId(71L);
        sn.setSnCode("SN001");
        sn.setMaterialId(100L);
        sn.setMaterialCode("MAT100");
        sn.setBatchCode("B001");
        return sn;
    }

    private WmMaterialStockTx tx(Long id, Long material, int direction) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxId(id);
        tx.setMaterialId(material);
        tx.setMaterialCode("MAT" + material);
        tx.setTxFlag(direction);
        tx.setBatchCode("B001");
        return tx;
    }
}
