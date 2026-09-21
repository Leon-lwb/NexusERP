package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareReturn;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import com.nexus.mes.eq.domain.bo.EqSpareReturnBo;
import com.nexus.mes.eq.domain.bo.EqSpareReturnLineBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnLineMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import com.nexus.mes.eq.service.support.EqSpareReturnNoGenerator;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 备件退库单单测（MES-EQ-04 收口）。
 * <p>核心断言：① 同单同备件重复行合并（避免重复过账）；② 确认先 CAS 再逐行
 * {@code TXN_RETURN} 过账（refType=SPARE_RETURN、refId=returnId）；③ 重复确认幂等成功
 * （不再过账）；④ CAS 抢锁失败/非草稿不可改不可删；⑤ 单价快照取自服务端备件主数据。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqSpareReturnServiceImplTest {

    @Mock
    private EqSpareReturnMapper returnMapper;
    @Mock
    private EqSpareReturnLineMapper lineMapper;
    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private IEqSparePartService sparePartService;
    @Mock
    private EqSpareReturnNoGenerator noGenerator;
    @InjectMocks
    private EqSpareReturnServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSpareReturn.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSpareReturnLine.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSparePart.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqSpareReturn.class);
        TableInfoHelper.remove(EqSpareReturnLine.class);
        TableInfoHelper.remove(EqSparePart.class);
    }

    @Test
    void insertMergesDuplicateSpareLinesAndSumsTotalQty() {
        when(noGenerator.nextReturnNo()).thenReturn("MESRT202609200001");
        when(returnMapper.selectCount(any())).thenReturn(0L);
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare(1L, "SP-001", "10.0000"));
        when(returnMapper.insert(any(EqSpareReturn.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, EqSpareReturn.class).setReturnId(88L);
            return 1;
        });

        EqSpareReturnBo bo = new EqSpareReturnBo();
        bo.setReturnType("REPAIR_RETURN");
        // 同一备件出现两次 → 必须合并为 1 行(数量累加), 否则同单会重复过账
        bo.setLines(List.of(line(1L, "1"), line(1L, "2")));

        Long returnId = service.insertByBo(bo);

        assertEquals(88L, returnId);
        ArgumentCaptor<EqSpareReturn> headerCaptor = ArgumentCaptor.forClass(EqSpareReturn.class);
        verify(returnMapper).insert(headerCaptor.capture());
        assertEquals("DRAFT", headerCaptor.getValue().getStatus());
        assertEquals("MESRT202609200001", headerCaptor.getValue().getReturnNo());
        assertEquals(0, new BigDecimal("3").compareTo(headerCaptor.getValue().getTotalQty()));
        ArgumentCaptor<EqSpareReturnLine> lineCaptor = ArgumentCaptor.forClass(EqSpareReturnLine.class);
        verify(lineMapper, times(1)).insert(lineCaptor.capture());
        assertEquals(0, new BigDecimal("3").compareTo(lineCaptor.getValue().getReturnQty()));
        // 单价快照来自服务端备件主数据(客户端不传单价)
        assertEquals(0, new BigDecimal("10.0000").compareTo(lineCaptor.getValue().getUnitCost()));
    }

    @Test
    void insertRejectsNonPositiveReturnQty() {
        EqSpareReturnBo bo = new EqSpareReturnBo();
        bo.setLines(List.of(line(1L, "0")));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        assertTrue(exception.getMessage().contains("退库数量必须大于0"));
        verifyNoInteractions(lineMapper, sparePartService);
    }

    @Test
    void confirmPostsEveryLineAsReturnTransaction() {
        when(returnMapper.selectById(5L)).thenReturn(header(5L, "DRAFT"));
        when(returnMapper.update(any(), any())).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of(
            lineEntity(5L, 1L, "2"), lineEntity(5L, 2L, "3")));

        assertTrue(service.confirm(5L));

        ArgumentCaptor<EqSpareStockBo> captor = ArgumentCaptor.forClass(EqSpareStockBo.class);
        verify(sparePartService, times(2)).stockTransaction(captor.capture());
        for (EqSpareStockBo stockBo : captor.getAllValues()) {
            // 退库必须走 TXN_RETURN(ERP 侧按入库方向过账), 并带 SPARE_RETURN/returnId 便于追溯
            assertEquals(IEqSparePartService.TXN_RETURN, stockBo.getTransactionType());
            assertEquals("SPARE_RETURN", stockBo.getRefType());
            assertEquals(5L, stockBo.getRefId());
        }
    }

    @Test
    void confirmIsIdempotentForAlreadyConfirmedDocument() {
        when(returnMapper.selectById(5L)).thenReturn(header(5L, "CONFIRMED"));

        // 重复确认按幂等成功处理: 不再 CAS, 不再逐行过账
        assertTrue(service.confirm(5L));
        verify(returnMapper, never()).update(any(), any());
        verifyNoInteractions(sparePartService, lineMapper);
    }

    @Test
    void confirmRejectsWhenCasLosesRace() {
        when(returnMapper.selectById(5L)).thenReturn(header(5L, "DRAFT"));
        // 另一并发请求已把状态改走 → CAS 影响 0 行
        when(returnMapper.update(any(), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.confirm(5L));

        assertTrue(exception.getMessage().contains("状态已变更"));
        verifyNoInteractions(sparePartService);
    }

    @Test
    void confirmRejectsCancelledDocument() {
        when(returnMapper.selectById(5L)).thenReturn(header(5L, "CANCELLED"));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.confirm(5L));

        assertTrue(exception.getMessage().contains("已作废"));
        verifyNoInteractions(sparePartService);
    }

    @Test
    void updateRejectsNonDraftDocument() {
        when(returnMapper.selectById(5L)).thenReturn(header(5L, "CONFIRMED"));
        EqSpareReturnBo bo = new EqSpareReturnBo();
        bo.setReturnId(5L);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertTrue(exception.getMessage().contains("仅草稿退库单可修改"));
        verifyNoInteractions(lineMapper);
    }

    @Test
    void deleteRejectsConfirmedDocument() {
        when(returnMapper.selectList(any())).thenReturn(List.of(header(5L, "CONFIRMED")));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{5L}));

        assertTrue(exception.getMessage().contains("仅草稿退库单可删除"));
        verifyNoInteractions(lineMapper);
    }

    // ==================== 构造辅助 ====================

    private EqSpareReturn header(Long returnId, String status) {
        EqSpareReturn header = new EqSpareReturn();
        header.setReturnId(returnId);
        header.setReturnNo("MESRT202609200001");
        header.setStatus(status);
        header.setVersion(0);
        return header;
    }

    private EqSpareReturnLine lineEntity(Long returnId, Long spareId, String qty) {
        EqSpareReturnLine line = new EqSpareReturnLine();
        line.setReturnId(returnId);
        line.setSpareId(spareId);
        line.setReturnQty(new BigDecimal(qty));
        return line;
    }

    private EqSpareReturnLineBo line(Long spareId, String qty) {
        EqSpareReturnLineBo line = new EqSpareReturnLineBo();
        line.setSpareId(spareId);
        line.setReturnQty(new BigDecimal(qty));
        return line;
    }

    private EqSparePartVo spare(Long spareId, String spareCode, String unitCost) {
        EqSparePartVo spare = new EqSparePartVo();
        spare.setSpareId(spareId);
        spare.setSpareCode(spareCode);
        spare.setSpareName("bearing");
        spare.setMaterialId(1L);
        spare.setWarehouseId(2L);
        spare.setUnitCost(new BigDecimal(unitCost));
        return spare;
    }
}
