package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.pda.PdaLineConfirmBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import com.nexus.erp.wm.service.PdaReceiveShelfAllocationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDA 收货核销超收守卫 单元测试（审计 ERP-WM-03 回归）
 *
 * <p><b>修复前会失败</b>：{@code confirmLine} 对 {@code wm_material_receive_line.quantity_received}
 * 与上架明细 {@code quantity} 都是「读-改-写、无任何上限」的累加 ——
 * 同一行重复扫码 / 客户端重放 / 断网重试即可超收入库，账面库存 &gt; 实际到货，
 * 多出的库存可被后续出库/领料消耗且无法追溯来源。</p>
 * <p>修复后：按到货通知行 {@code quantity_arrival} 设上限，并改为带守卫的条件更新（原子累加）。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PdaReceiveServiceImplTest {

    @Mock
    private IWmMaterialReceiveService receiveService;
    @Mock
    private WmMaterialReceiveMapper receiveMapper;
    @Mock
    private WmCertifiedReceiptMutationGuard certifiedReceiptGuard;
    @Mock
    private WmCertifiedReceiptPostingService certifiedPosting;
    @Mock
    private PdaReceiveShelfAllocationService shelfAllocation;
    @Mock
    private WmMaterialReceiveLineMapper lineMapper;
    @Mock
    private WmArrivalNoticeLineMapper arrivalNoticeLineMapper;
    @Mock
    private WmMaterialReceiveDetailMapper detailMapper;
    @Mock
    private WmStorageLocationMapper locationMapper;
    @Mock
    private IPutawayStrategyService putawayStrategyService;

    @InjectMocks
    private PdaReceiveServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceive.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveDetail.class);
        TableInfoHelper.initTableInfo(assistant, WmArrivalNoticeLine.class);
    }

    /** ERP-WM-03 核心回归：已收 90 + 本次 20 > 应到 100 → 必须拒绝，且不写任何库 */
    @Test
    void confirmLine_rejectsOverReceipt() {
        when(receiveMapper.selectVoOne(any())).thenReturn(head(1L, 77L));
        when(lineMapper.selectOne(any())).thenReturn(line(21L, new BigDecimal("90")));
        when(arrivalNoticeLineMapper.selectList(any())).thenReturn(List.of(noticeLine(31L, new BigDecimal("100"))));

        PdaActionResultVo result = service.confirmLine(1L, confirmBo(21L, new BigDecimal("20")));

        assertEquals(Boolean.FALSE, result.getOk(), "ERP-WM-03: 超收必须被拒绝");
        assertTrue(result.getMessage().contains("超过应到数量"), "错误信息: " + result.getMessage());
        verify(lineMapper, never()).update(any(), any());
        verify(detailMapper, never()).insert(any(WmMaterialReceiveDetail.class));
        verify(detailMapper, never()).updateById(any(WmMaterialReceiveDetail.class));
    }

    /** ERP-WM-03：恰好收满允许通过，且上架明细数量必须跟随行实收总数（不再独立累加） */
    @Test
    void confirmLine_accumulatesWithinLimitAndSyncsDetail() {
        when(receiveMapper.selectVoOne(any())).thenReturn(head(1L, 77L));
        when(lineMapper.selectOne(any())).thenReturn(line(21L, new BigDecimal("90")));
        when(arrivalNoticeLineMapper.selectList(any())).thenReturn(List.of(noticeLine(31L, new BigDecimal("100"))));
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(detailMapper.selectOne(any())).thenReturn(null);

        PdaActionResultVo result = service.confirmLine(1L, confirmBo(21L, new BigDecimal("10")));

        assertEquals(Boolean.TRUE, result.getOk(), "恰好收满应允许: " + result.getMessage());

        ArgumentCaptor<WmMaterialReceiveDetail> captor = ArgumentCaptor.forClass(WmMaterialReceiveDetail.class);
        verify(detailMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("100").compareTo(captor.getValue().getQuantity()),
            "ERP-WM-03: 上架明细数量必须等于行实收总数（修复前是独立的无上限累加）");
    }

    /** ERP-WM-03：条件更新影响 0 行（并发下已被推到超限）时必须拒绝并提示刷新 */
    @Test
    void confirmLine_rejectsWhenConditionalUpdateAffectsNoRow() {
        when(receiveMapper.selectVoOne(any())).thenReturn(head(1L, 77L));
        when(lineMapper.selectOne(any())).thenReturn(line(21L, new BigDecimal("90")));
        when(arrivalNoticeLineMapper.selectList(any())).thenReturn(List.of(noticeLine(31L, new BigDecimal("100"))));
        when(lineMapper.update(any(), any())).thenReturn(0);

        PdaActionResultVo result = service.confirmLine(1L, confirmBo(21L, new BigDecimal("10")));

        assertEquals(Boolean.FALSE, result.getOk(), "并发超限必须拒绝");
        verify(detailMapper, never()).insert(any(WmMaterialReceiveDetail.class));
    }

    /** 非法数量（<=0）必须拒绝 */
    @Test
    void confirmLine_rejectsNonPositiveQty() {
        when(receiveMapper.selectVoOne(any())).thenReturn(head(1L, 77L));
        when(lineMapper.selectOne(any())).thenReturn(line(21L, new BigDecimal("90")));

        PdaActionResultVo result = service.confirmLine(1L, confirmBo(21L, BigDecimal.ZERO));

        assertEquals(Boolean.FALSE, result.getOk());
        verify(lineMapper, never()).update(any(), any());
    }

    private WmMaterialReceiveVo head(Long receiveId, Long noticeId) {
        WmMaterialReceiveVo vo = new WmMaterialReceiveVo();
        vo.setReceiveId(receiveId);
        vo.setStatus("APPROVED");
        vo.setNoticeId(noticeId);
        vo.setWarehouseId(5L);
        return vo;
    }

    private WmMaterialReceiveLine line(Long lineId, BigDecimal received) {
        WmMaterialReceiveLine l = new WmMaterialReceiveLine();
        l.setLineId(lineId);
        l.setReceiveId(1L);
        l.setMaterialId(100L);
        l.setMaterialCode("M-001");
        l.setQuantityReceived(received);
        return l;
    }

    private WmArrivalNoticeLine noticeLine(Long lineId, BigDecimal arrival) {
        WmArrivalNoticeLine nl = new WmArrivalNoticeLine();
        nl.setLineId(lineId);
        nl.setNoticeId(77L);
        nl.setMaterialId(100L);
        nl.setQuantityArrival(arrival);
        return nl;
    }

    private PdaLineConfirmBo confirmBo(Long lineId, BigDecimal qty) {
        PdaLineConfirmBo bo = new PdaLineConfirmBo();
        bo.setLineId(lineId);
        bo.setQty(qty);
        return bo;
    }
}
