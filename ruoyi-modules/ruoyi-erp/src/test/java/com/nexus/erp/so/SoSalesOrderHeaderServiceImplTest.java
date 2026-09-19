package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.so.service.impl.SoSalesOrderHeaderServiceImpl;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单 Service 单元测试（批次 0 回归基线）。
 * <p>覆盖 P0 修复不能碰坏的既有正确行为：状态机门禁（confirm/fulfill/close/cancel）、
 * 并发条件更新的失败分支、履约收尾副作用（释放预留 + 信用转已用 + 发布事件）、
 * 单号自动生成与默认草稿状态、selectById 的客户订单号回填。</p>
 * <p>注意：本类只断言<b>既有</b>行为，用于给批次 1 的差量更新改造兜底；
 * 批次 1 新增的「保留 line_id」等行为在其自身的测试中追加。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoSalesOrderHeaderServiceImplTest {

    @Mock
    private SoSalesOrderHeaderMapper baseMapper;
    @Mock
    private SoSalesOrderLineMapper soSalesOrderLineMapper;
    @Mock
    private SoCustomerOrderMapper soCustomerOrderMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private BdBomMapper bdBomMapper;
    @Mock
    private PrdProductionPlanMapper prdProductionPlanMapper;
    @Mock
    private PrdWorkOrderMapper prdWorkOrderMapper;
    @Mock
    private WmWarehouseMapper wmWarehouseMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Mock
    private ICreditCheckService creditCheckService;
    @Mock
    private IWmStockReservationService stockReservationService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SoSalesOrderHeaderServiceImpl headerService;

    /** 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter) */
    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        // 预热 MyBatis-Plus Lambda 列缓存：条件更新/查询的列名解析依赖 TableInfo
        // （09-06 测试回归的根因就是 mockStatic(LambdaUtils) 未 stub extract，此处改用真实列缓存）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderHeader.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderLine.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoSalesOrderHeader header(Long soId, String status) {
        SoSalesOrderHeader h = new SoSalesOrderHeader();
        h.setSoId(soId);
        h.setSoNo("SO-TEST-001");
        h.setClientId(1L);
        h.setClientName("测试客户");
        h.setOrderStatus(status);
        h.setTotalAmount(new BigDecimal("1000.00"));
        return h;
    }

    private SoSalesOrderLine line(Long lineId, String ordered, String delivered, String oqcStatus) {
        SoSalesOrderLine l = new SoSalesOrderLine();
        l.setLineId(lineId);
        l.setSoId(1L);
        l.setLineNo("0010");
        l.setMaterialId(100L);
        l.setMaterialName("测试物料");
        l.setOrderedQty(new BigDecimal(ordered));
        l.setDeliveredQty(new BigDecimal(delivered));
        l.setOqcStatus(oqcStatus);
        return l;
    }

    // ==================== confirm ====================

    /** 非 DRAFT/APPROVED 状态不允许确认 */
    @Test
    void confirm_rejectsIllegalStatus() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.confirm(1L));
        assertEquals("当前状态不允许确认", ex.getMessage());
        verify(creditCheckService, never()).checkAndFreeze(any(), any(), any());
    }

    /** 订单不存在 */
    @Test
    void confirm_rejectsMissingOrder() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> headerService.confirm(1L));
    }

    /** 条件更新未抢占到（并发已变更）时抛异常，防双确认 */
    @Test
    void confirm_concurrentConflictThrows() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.confirm(1L));
        assertTrue(ex.getMessage().contains("并发"), "应提示状态被并发变更，实际: " + ex.getMessage());
    }

    /** 确认成功：做信用检查 + 条件更新置 CONFIRMED */
    @Test
    void confirm_success_freezesCreditAndUpdatesStatus() {
        SoSalesOrderHeader h = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(baseMapper.update(any(), any())).thenReturn(1);
        // 库存预留 / 生产计划生成 均以「无行」短路，本用例只关注状态与信用
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());

        headerService.confirm(1L);

        verify(creditCheckService).checkAndFreeze(eq(1L), eq(1L), any(BigDecimal.class));
        verify(baseMapper).update(any(), any());
        assertEquals(ISoSalesOrderHeaderService.STATUS_CONFIRMED, h.getOrderStatus());
    }

    // ==================== fulfill ====================

    /** 仅 CONFIRMED 可履行 */
    @Test
    void fulfill_rejectsWhenNotConfirmed() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, false));
        assertTrue(ex.getMessage().contains("只有已确认状态"), "实际: " + ex.getMessage());
    }

    /** 无明细不可履行 */
    @Test
    void fulfill_rejectsWhenNoLines() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, false));
        assertTrue(ex.getMessage().contains("订单无明细"), "实际: " + ex.getMessage());
    }

    /** 存在未发齐的行且 force=false 时拒绝 */
    @Test
    void fulfill_rejectsShortShippedLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "3", "QUALIFIED")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, false));
        assertTrue(ex.getMessage().contains("未完全发货"), "实际: " + ex.getMessage());
        verify(creditCheckService, never()).convertToUsed(any());
    }

    /** OQC 不合格是硬门禁，force 不可绕过 */
    @Test
    void fulfill_rejectsOqcUnqualifiedEvenWhenForced() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "10", "UNQUALIFIED")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, true));
        assertTrue(ex.getMessage().contains("OQC"), "实际: " + ex.getMessage());
    }

    /** 履行成功：释放预留 + 信用转已用 + 行状态固化 + 发布事件 */
    @Test
    void fulfill_success_releasesReservationAndPublishesEvent() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "10", "QUALIFIED")));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(prdProductionPlanMapper.selectList(any())).thenReturn(List.of());

        headerService.fulfill(1L, false);

        verify(stockReservationService).unreserve("SALES_ORDER", 1L, null);
        verify(creditCheckService).convertToUsed(1L);
        // publishEvent 有 (ApplicationEvent) 与 (Object) 两个重载，调用点会选更具体的 ApplicationEvent 重载，
        // 因此这里必须用 ApplicationEvent 类型匹配，否则会验证到未被调用的 Object 重载上
        verify(applicationEventPublisher).publishEvent(any(ApplicationEvent.class));
        // 行状态固化为 FULFILLED 且 fulfilled_qty 兜齐 ordered_qty（逐行条件更新，不新增行）
        verify(soSalesOrderLineMapper).update(any(), any());
        verify(soSalesOrderLineMapper, never()).insert(any(SoSalesOrderLine.class));
    }

    // ==================== close ====================

    /** 已取消订单不能关闭 */
    @Test
    void close_rejectsCancelledOrder() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CANCELLED));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.close(1L));
        assertTrue(ex.getMessage().contains("已取消"), "实际: " + ex.getMessage());
    }

    /** 关闭成功：释放信用额度 + 释放库存预留 */
    @Test
    void close_success_releasesCreditAndReservation() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(baseMapper.update(any(), any())).thenReturn(1);

        headerService.close(1L);

        verify(creditCheckService).release(1L);
        verify(stockReservationService).unreserve("SALES_ORDER", 1L, null);
    }

    /** 并发已取消时关闭失败 */
    @Test
    void close_concurrentCancelThrows() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.close(1L));
        assertTrue(ex.getMessage().contains("并发"), "实际: " + ex.getMessage());
        verify(creditCheckService, never()).release(any());
    }

    // ==================== cancel ====================

    /** 已关闭 / 已履行不可取消 */
    @Test
    void cancel_rejectsClosedOrder() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CLOSED));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.cancel(1L, "客户撤单"));
        assertTrue(ex.getMessage().contains("已关闭"), "实际: " + ex.getMessage());
    }

    @Test
    void cancel_rejectsFulfilledOrder() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_FULFILLED));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.cancel(1L, "客户撤单"));
        assertTrue(ex.getMessage().contains("已履行"), "实际: " + ex.getMessage());
    }

    /** 取消成功：写入原因 + 行状态置 CANCELLED + 释放信用与预留 */
    @Test
    void cancel_success_writesReasonAndCancelsLines() {
        SoSalesOrderHeader h = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(line(100L, "10", "0", null)));
        when(prdProductionPlanMapper.selectList(any())).thenReturn(List.of());

        headerService.cancel(1L, "客户撤单");

        assertEquals(ISoSalesOrderHeaderService.STATUS_CANCELLED, h.getOrderStatus());
        assertEquals("客户撤单", h.getCancelledReason());
        verify(creditCheckService).release(1L);
        verify(stockReservationService).unreserve("SALES_ORDER", 1L, null);
        verify(soSalesOrderLineMapper).update(any(), any());
    }

    // ==================== insertByBo / selectById ====================

    /** 新增：单号为空时按规则生成，状态为空时默认草稿 */
    @Test
    void insertByBo_generatesSoNoAndDefaultsToDraft() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setClientId(1L);
        bo.setClientName("测试客户");

        when(autoCodeUtil.genSerialCode(eq("SO_ORDER_CODE"), any())).thenReturn("SO-AUTO-0001");
        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);

        assertEquals(1, headerService.insertByBo(bo));

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("SO-AUTO-0001", captor.getValue().getSoNo());
        assertEquals(ISoSalesOrderHeaderService.STATUS_DRAFT, captor.getValue().getOrderStatus());
    }

    /** 新增：客户端已给单号/状态时不覆盖 */
    @Test
    void insertByBo_keepsClientProvidedSoNoAndStatus() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setSoNo("SO-MANUAL-9");
        bo.setOrderStatus(ISoSalesOrderHeaderService.STATUS_APPROVED);
        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);

        headerService.insertByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("SO-MANUAL-9", captor.getValue().getSoNo());
        assertEquals(ISoSalesOrderHeaderService.STATUS_APPROVED, captor.getValue().getOrderStatus());
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
    }

    /** 详情：按 coId 回填客户订单号（修正前端用客户参考号冒充的问题） */
    @Test
    void selectById_backfillsCustomerOrderNo() {
        SoSalesOrderHeaderVo vo = new SoSalesOrderHeaderVo();
        vo.setSoId(1L);
        vo.setCoId(9L);
        vo.setClientId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(vo);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());

        SoCustomerOrder co = new SoCustomerOrder();
        co.setCoId(9L);
        co.setCoNo("CO-2026-0001");
        when(soCustomerOrderMapper.selectById(9L)).thenReturn(co);

        SoSalesOrderHeaderVo result = headerService.selectById(1L);

        assertEquals("CO-2026-0001", result.getCoNo());
    }

    /** 详情：无客户订单关联时不查客户订单表 */
    @Test
    void selectById_skipsCustomerOrderWhenCoIdNull() {
        SoSalesOrderHeaderVo vo = new SoSalesOrderHeaderVo();
        vo.setSoId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(vo);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());

        headerService.selectById(1L);

        verify(soCustomerOrderMapper, never()).selectById(any());
    }
}
