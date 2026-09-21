package com.nexus.mes.lt.service.impl;

import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.LtWoConsumption;
import com.nexus.mes.lt.domain.bo.LtWoConsumptionBo;
import com.nexus.mes.lt.domain.bo.LtWoPickBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.lt.service.ILtSnBindingService;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LtWoConsumptionServiceImplTest {

    @Mock
    private LtWoConsumptionMapper baseMapper;
    @Mock
    private LtLotMapper lotMapper;
    @Mock
    private ILtLotService lotService;
    @Mock
    private LtSnBindingMapper snBindingMapper;
    @Mock
    private ILtSnBindingService snBindingService;
    @Mock
    private ProWorkOrderBomLineMapper woBomLineMapper;
    @InjectMocks
    private LtWoConsumptionServiceImpl service;

    @Captor
    private ArgumentCaptor<LtWoConsumption> insertCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
        // 批次可用性校验(新增门禁)默认通过, 个别用例再覆盖为不可用
        lenient().when(lotService.checkLotUsable(any(), any()))
            .thenReturn(Map.of("usable", true, "reason", "可用"));
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
    }

    @Test
    void insertByBoConsumesAndCreatesRecord() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setMaterialCode("MAT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotService.consume(1L, BigDecimal.valueOf(5))).thenReturn(true);
        when(baseMapper.insert(any(LtWoConsumption.class))).thenReturn(1);

        LtWoConsumptionBo bo = new LtWoConsumptionBo();
        bo.setWoId(10L);
        bo.setLotId(1L);
        bo.setMaterialId(20L);
        bo.setConsumedQty(BigDecimal.valueOf(5));
        bo.setOperatorId(999L);
        bo.setRemark("消耗备注");

        Boolean result = service.insertByBo(bo);

        assertTrue(result);
        verify(lotService).consume(1L, BigDecimal.valueOf(5));
        verify(baseMapper).insert(insertCaptor.capture());
        LtWoConsumption captured = insertCaptor.getValue();
        assertEquals(10L, captured.getWoId());
        assertEquals(1L, captured.getLotId());
        assertEquals("LOT-001", captured.getLotCode());
        assertEquals(20L, captured.getMaterialId());
        assertEquals("MAT-001", captured.getMaterialCode());
        assertEquals(BigDecimal.valueOf(5), captured.getConsumedQty());
        assertEquals(999L, captured.getOperatorId());
        assertEquals("消耗备注", captured.getRemark());
        assertNotNull(captured.getConsumedTime());
    }

    @Test
    void insertByBoUsesLoggedInUserWhenOperatorNotSet() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotService.consume(1L, BigDecimal.valueOf(3))).thenReturn(true);
        when(baseMapper.insert(any(LtWoConsumption.class))).thenReturn(1);

        LtWoConsumptionBo bo = new LtWoConsumptionBo();
        bo.setWoId(10L);
        bo.setLotId(1L);
        bo.setMaterialId(20L);
        bo.setConsumedQty(BigDecimal.valueOf(3));

        service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        LtWoConsumption captured = insertCaptor.getValue();
        assertEquals(1L, captured.getOperatorId());
    }

    @Test
    void insertByBoRejectsNonexistentLot() {
        when(lotMapper.selectById(1L)).thenReturn(null);

        LtWoConsumptionBo bo = new LtWoConsumptionBo();
        bo.setLotId(1L);
        bo.setWoId(10L);
        bo.setMaterialId(20L);
        bo.setConsumedQty(BigDecimal.valueOf(5));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.insertByBo(bo));
        assertTrue(exception.getMessage().contains("批次不存在"));
        verify(lotService, never()).consume(any(), any());
    }

    @Test
    void confirmPickRejectsNullPicks() {
        LtWoPickBo pickBo = new LtWoPickBo();
        pickBo.setPicks(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.confirmPick(pickBo));
        assertTrue(exception.getMessage().contains("领料明细不能为空"));
    }

    @Test
    void confirmPickRejectsEmptyPicks() {
        LtWoPickBo pickBo = new LtWoPickBo();
        pickBo.setPicks(List.of());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.confirmPick(pickBo));
        assertTrue(exception.getMessage().contains("领料明细不能为空"));
    }

    @Test
    void confirmPickProcessesMultiplePicks() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setMaterialCode("MAT-001");
        LtLot lot2 = new LtLot();
        lot2.setLotId(2L);
        lot2.setLotCode("LOT-002");
        lot2.setMaterialCode("MAT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotMapper.selectById(2L)).thenReturn(lot2);
        when(lotService.consume(1L, BigDecimal.valueOf(5))).thenReturn(true);
        when(lotService.consume(2L, BigDecimal.valueOf(3))).thenReturn(true);
        when(baseMapper.insert(any(LtWoConsumption.class))).thenReturn(1);

        LtWoConsumptionBo pick1 = new LtWoConsumptionBo();
        pick1.setLotId(1L);
        pick1.setConsumedQty(BigDecimal.valueOf(5));

        LtWoConsumptionBo pick2 = new LtWoConsumptionBo();
        pick2.setLotId(2L);
        pick2.setConsumedQty(BigDecimal.valueOf(3));

        LtWoPickBo pickBo = new LtWoPickBo();
        pickBo.setWoId(10L);
        pickBo.setMaterialId(20L);
        pickBo.setPicks(List.of(pick1, pick2));

        Boolean result = service.confirmPick(pickBo);

        assertTrue(result);
        verify(lotService).consume(1L, BigDecimal.valueOf(5));
        verify(lotService).consume(2L, BigDecimal.valueOf(3));
        verify(baseMapper, times(2)).insert(any(LtWoConsumption.class));
    }

    @Test
    void confirmPickWithSnIdCreatesBinding() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setMaterialCode("MAT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotService.consume(1L, BigDecimal.valueOf(5))).thenReturn(true);
        when(baseMapper.insert(any(LtWoConsumption.class))).thenReturn(1);
        when(snBindingMapper.selectCount(any())).thenReturn(0L);
        when(snBindingService.insertByBo(any(LtSnBindingBo.class))).thenReturn(true);

        LtWoConsumptionBo pick = new LtWoConsumptionBo();
        pick.setLotId(1L);
        pick.setConsumedQty(BigDecimal.valueOf(5));

        LtWoPickBo pickBo = new LtWoPickBo();
        pickBo.setWoId(10L);
        pickBo.setMaterialId(20L);
        pickBo.setSnId(100L);
        pickBo.setPicks(List.of(pick));

        Boolean result = service.confirmPick(pickBo);

        assertTrue(result);
        verify(snBindingService).insertByBo(any(LtSnBindingBo.class));
    }

    @Test
    void confirmPickSkipsBindingWhenSnAlreadyBound() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setLotCode("LOT-001");
        lot.setMaterialCode("MAT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotService.consume(1L, BigDecimal.valueOf(5))).thenReturn(true);
        when(baseMapper.insert(any(LtWoConsumption.class))).thenReturn(1);
        when(snBindingMapper.selectCount(any())).thenReturn(1L);

        LtWoConsumptionBo pick = new LtWoConsumptionBo();
        pick.setLotId(1L);
        pick.setConsumedQty(BigDecimal.valueOf(5));

        LtWoPickBo pickBo = new LtWoPickBo();
        pickBo.setWoId(10L);
        pickBo.setMaterialId(20L);
        pickBo.setSnId(100L);
        pickBo.setPicks(List.of(pick));

        Boolean result = service.confirmPick(pickBo);

        assertTrue(result);
        verify(snBindingService, never()).insertByBo(any());
    }

    @Test
    void deleteByIdsReturnsFalseForNullIds() {
        Boolean result = service.deleteByIds(null);
        assertFalse(result);
    }

    @Test
    void deleteByIdsReturnsFalseForEmptyIds() {
        Boolean result = service.deleteByIds(new Long[]{});
        assertFalse(result);
    }

    @Test
    void deleteByIdsDeletesRecords() {
        when(baseMapper.deleteByIds(anyList())).thenReturn(2);

        Boolean result = service.deleteByIds(new Long[]{1L, 2L});

        assertTrue(result);
    }

    @Test
    void recommendForWoDelegatesToLotService() {
        LtLotVo vo = new LtLotVo();
        vo.setLotId(1L);
        when(lotService.recommendFefo(20L, BigDecimal.TEN)).thenReturn(List.of(vo));

        List<LtLotVo> result = service.recommendForWo(10L, 20L, BigDecimal.TEN);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getLotId());
    }

    // ==================== MES-LT-02 批次物料一致性 + 超领校验 ====================

    @Test
    void insertByBoRejectsWhenLotCheckNotUsable() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(999L);
        when(lotMapper.selectById(1L)).thenReturn(lot);
        when(lotService.checkLotUsable(any(), any()))
            .thenReturn(Map.of("usable", false, "reason", "批次物料与要求物料不符"));

        LtWoConsumptionBo bo = new LtWoConsumptionBo();
        bo.setWoId(10L);
        bo.setLotId(1L);
        bo.setMaterialId(20L);
        bo.setConsumedQty(BigDecimal.valueOf(5));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.insertByBo(bo));

        assertTrue(exception.getMessage().contains("批次物料与要求物料不符"));
        verify(lotService, never()).consume(any(), any());
    }

    @Test
    void insertByBoRejectsOverPickBeyondBomSnapshot() {
        LtLot lot = new LtLot();
        lot.setLotId(1L);
        lot.setMaterialId(20L);
        lot.setLotCode("LOT-001");
        lot.setMaterialCode("MAT-001");
        when(lotMapper.selectById(1L)).thenReturn(lot);
        // 工单BOM快照需求量 10
        ProWorkOrderBomLine bomLine = new ProWorkOrderBomLine();
        bomLine.setTotalQuantity(BigDecimal.TEN);
        when(woBomLineMapper.selectOne(any())).thenReturn(bomLine);
        // 已领 8, 本次 5 → 超领
        LtWoConsumption existed = new LtWoConsumption();
        existed.setConsumedQty(BigDecimal.valueOf(8));
        when(baseMapper.selectList(any())).thenReturn(List.of(existed));

        LtWoConsumptionBo bo = new LtWoConsumptionBo();
        bo.setWoId(10L);
        bo.setLotId(1L);
        bo.setMaterialId(20L);
        bo.setConsumedQty(BigDecimal.valueOf(5));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.insertByBo(bo));

        assertTrue(exception.getMessage().contains("超领"));
        verify(lotService, never()).consume(any(), any());
    }
}