package com.nexus.erp.so;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdMaterialVendorMapper;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.so.service.impl.SoSalesOrderHeaderServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private BdMaterialVendorMapper bdMaterialVendorMapper;
    @Mock
    private PrdProductionPlanMapper prdProductionPlanMapper;
    @Mock
    private PrdSubcontractMapper prdSubcontractMapper;
    @Mock
    private PrdWorkOrderMapper prdWorkOrderMapper;
    @Mock
    private WmWarehouseMapper wmWarehouseMapper;
    @Mock
    private WmMaterialStockMapper wmMaterialStockMapper;
    @Mock
    private WmTransferMapper wmTransferMapper;
    @Mock
    private WmTransferLineMapper wmTransferLineMapper;
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

    // ==================== fulfill: OQC 白名单门禁 (审计 MES-QC-01) ====================

    /** OQC 结论尚未回写(NULL)时也必须拦截: 回写丢失时 NULL 与"不合格"无法区分, 放行即不合格品出库 */
    @Test
    void fulfill_rejectsOqcNullStatusEvenWhenForced() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any()))
                .thenReturn(List.of(line(1L, "10", "10", null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, true));

        assertTrue(ex.getMessage().contains("OQC"));
        verify(stockReservationService, never()).unreserve(any(), any(), any());
    }

    /** OQC 结论为 PENDING(未完成检验)时同样拦截(白名单: 仅 QUALIFIED 可履行) */
    @Test
    void fulfill_rejectsOqcPendingEvenWhenForced() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(soSalesOrderLineMapper.selectList(any()))
                .thenReturn(List.of(line(1L, "10", "10", "PENDING")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> headerService.fulfill(1L, true));

        assertTrue(ex.getMessage().contains("OQC"));
        verify(stockReservationService, never()).unreserve(any(), any(), any());
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
        // 行状态固化为 FULFILLED 且 fulfilled_qty 兜齐 ordered_qty（逐行条件更新）
        // + ERP-SO-01：释放库存侧预留后清零行 reserved_qty 展示字段（整单批量条件更新），共 2 次条件更新
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(soSalesOrderLineMapper, times(2)).update(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getAllValues().stream()
                .anyMatch(w -> w.getSqlSet() != null && w.getSqlSet().contains("reserved_qty")),
            "ERP-SO-01: 释放库存预留后必须清零行 reserved_qty，否则残留值会让后续出库走消费预留分支");
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
        // 行状态置 CANCELLED（逐行条件更新）+ ERP-SO-01 清零行 reserved_qty（整单批量条件更新）
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> cancelCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(soSalesOrderLineMapper, times(2)).update(any(), cancelCaptor.capture());
        assertTrue(cancelCaptor.getAllValues().stream()
                .anyMatch(w -> w.getSqlSet() != null && w.getSqlSet().contains("reserved_qty")),
            "ERP-SO-01: 取消订单释放预留后必须清零行 reserved_qty");
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

    /** 新增：客户端给的单号保留，但状态一律强制 DRAFT（防直写终态绕过信用/库存/生产计划） */
    @Test
    void insertByBo_keepsClientSoNoButForcesDraft() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setSoNo("SO-MANUAL-9");
        bo.setOrderStatus(ISoSalesOrderHeaderService.STATUS_APPROVED);
        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);

        headerService.insertByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("SO-MANUAL-9", captor.getValue().getSoNo());
        assertEquals(ISoSalesOrderHeaderService.STATUS_DRAFT, captor.getValue().getOrderStatus());
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
    }

    /** 确认：未关联客户直接拒绝（否则信用检查静默跳过 = 绕过额度控制） */
    @Test
    void confirm_rejectsOrderWithoutClient() {
        SoSalesOrderHeader h = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        h.setClientId(null);
        when(baseMapper.selectById(1L)).thenReturn(h);

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.confirm(1L));
        assertTrue(ex.getMessage().contains("未关联来源客户"), "实际: " + ex.getMessage());
        verify(creditCheckService, never()).checkAndFreeze(any(), any(), any());
    }

    /** 确认成功：信用状态写字典合法值 CHECKED_OK（原实现写 PASSED，字典中不存在该值） */
    @Test
    void confirm_success_writesCheckedOkCreditStatus() {
        SoSalesOrderHeader h = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());

        headerService.confirm(1L);

        assertEquals("CHECKED_OK", h.getCreditStatus());
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

    // ==================== updateByBo 差量更新（T1.1 / D3 / D4）====================

    private SoSalesOrderHeaderBo updateBo() {
        SoSalesOrderHeaderBo b = new SoSalesOrderHeaderBo();
        b.setSoId(1L);
        return b;
    }

    private SoSalesOrderLine existingLine(Long lineId, String materialId, String ordered,
                                          String delivered, String production, String invoiced) {
        SoSalesOrderLine l = new SoSalesOrderLine();
        l.setLineId(lineId);
        l.setSoId(1L);
        l.setLineNo(String.format("%04d", lineId.intValue()));
        l.setMaterialId(Long.valueOf(materialId));
        l.setMaterialCode("M-" + materialId);
        l.setMaterialName("物料" + materialId);
        l.setOrderedQty(new BigDecimal(ordered));
        l.setDeliveredQty(new BigDecimal(delivered));
        l.setProductionQty(new BigDecimal(production));
        l.setInvoicedQty(new BigDecimal(invoiced));
        l.setUnitPrice(new BigDecimal("100"));
        l.setLineStatus("PENDING");
        return l;
    }

    private SoSalesOrderLineBo lineBo(Long lineId, String materialId, String ordered) {
        SoSalesOrderLineBo b = new SoSalesOrderLineBo();
        b.setLineId(lineId);
        b.setMaterialId(Long.valueOf(materialId));
        b.setOrderedQty(new BigDecimal(ordered));
        b.setUnitPrice(new BigDecimal("100"));
        b.setLineNo(lineId == null ? "0090" : String.format("%04d", lineId.intValue()));
        return b;
    }

    /** D4：非草稿状态禁止改单 */
    @Test
    void updateByBo_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.updateByBo(updateBo()));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
        verify(soSalesOrderLineMapper, never()).insertBatch(anyList());
    }

    /** 订单不存在 */
    @Test
    void updateByBo_rejectsMissingOrder() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> headerService.updateByBo(updateBo()));
    }

    /** soId 为空直接拒绝，避免误删 so_id IS NULL 的行 */
    @Test
    void updateByBo_rejectsNullSoId() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> headerService.updateByBo(new SoSalesOrderHeaderBo()));
        assertTrue(ex.getMessage().contains("不能为空"), "实际: " + ex.getMessage());
    }

    /** 单号与状态由服务端权威维护，客户端传了也还原 */
    @Test
    void updateByBo_forcesSoNoAndStatusFromDb() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        // 注：本用例不提交明细（lineList 为 null），按新语义不会触达明细表，故不 stub selectList
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoNo("SO-HACKED");
        bo.setOrderStatus(ISoSalesOrderHeaderService.STATUS_FULFILLED);

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals("SO-TEST-001", captor.getValue().getSoNo());
        assertEquals(ISoSalesOrderHeaderService.STATUS_DRAFT, captor.getValue().getOrderStatus());
    }

    /** 已有行保留 line_id（不重建），新增行无 line_id 走插入 */
    @Test
    void updateByBo_keepsExistingLineIdsAndInsertsNewLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "0", "0", "0")));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(
            lineBo(100L, "100", "12"),   // 已有行 -> 更新，保留 line_id
            lineBo(null, "200", "5")));  // 新增行 -> 插入

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderLine> updated = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper).updateById(updated.capture());
        assertEquals(100L, updated.getValue().getLineId());
        assertEquals(0, new BigDecimal("12").compareTo(updated.getValue().getOrderedQty()));

        ArgumentCaptor<List<SoSalesOrderLine>> inserted = ArgumentCaptor.forClass(List.class);
        verify(soSalesOrderLineMapper).insertBatch(inserted.capture());
        assertEquals(1, inserted.getValue().size());
        assertNull(inserted.getValue().get(0).getLineId());
        assertEquals(1L, inserted.getValue().get(0).getSoId());
        // 新增行的履约字段由服务端初始化，不接受客户端传入
        assertEquals(0, BigDecimal.ZERO.compareTo(inserted.getValue().get(0).getDeliveredQty()));
        assertEquals("PENDING", inserted.getValue().get(0).getLineStatus());

        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
    }

    /** 请求中未出现的已有行被逻辑删除（保留其它行） */
    @Test
    void updateByBo_deletesLineMissingFromPayload() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(
            existingLine(100L, "100", "10", "0", "0", "0"),
            existingLine(101L, "200", "5", "0", "0", "0")));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.deleteById(any(Serializable.class))).thenReturn(1);
        when(prdProductionPlanMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(lineBo(100L, "100", "10")));

        headerService.updateByBo(bo);

        verify(soSalesOrderLineMapper).deleteById(101L);
        verify(soSalesOrderLineMapper, never()).insertBatch(anyList());
    }

    /** 已发货的行禁止删除 */
    @Test
    void updateByBo_rejectsDeletingFulfilledLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(
            existingLine(100L, "100", "10", "0", "0", "0"),
            existingLine(101L, "200", "5", "3", "0", "0"))); // 已发货 3
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(lineBo(100L, "100", "10")));

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("不允许删除"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
    }

    /** 已关联生产计划的行禁止删除 */
    @Test
    void updateByBo_rejectsDeletingLineWithProductionPlan() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(
            existingLine(100L, "100", "10", "0", "0", "0"),
            existingLine(101L, "200", "5", "0", "0", "0")));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(prdProductionPlanMapper.selectCount(any())).thenReturn(1L);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(lineBo(100L, "100", "10")));

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("已关联生产计划"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
    }

    /** 履约进度字段不被表单快照覆盖（防丢失更新） */
    @Test
    void updateByBo_preservesFulfillmentFields() {
        SoSalesOrderLine fulfilled = existingLine(100L, "100", "10", "7", "3", "0");
        fulfilled.setLineStatus("SCHEDULED");
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(fulfilled));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderLineBo tampered = lineBo(100L, "100", "10");
        tampered.setDeliveredQty(new BigDecimal("999"));
        tampered.setProductionQty(new BigDecimal("999"));
        // 注：SoSalesOrderLineBo 本身没有 invoicedQty 字段，客户端无法从 API 传入，天然免疫篡改
        tampered.setLineStatus("FULFILLED");
        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(tampered));

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderLine> captor = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper).updateById(captor.capture());
        SoSalesOrderLine saved = captor.getValue();
        assertEquals(0, new BigDecimal("7").compareTo(saved.getDeliveredQty()));
        assertEquals(0, new BigDecimal("3").compareTo(saved.getProductionQty()));
        assertEquals("SCHEDULED", saved.getLineStatus());
    }

    /** 已履约行禁止改物料/数量 */
    @Test
    void updateByBo_rejectsMaterialChangeOnFulfilledLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "5", "0", "0")));

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(lineBo(100L, "200", "10"))); // 换物料

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("不允许修改物料或订单数量"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).updateById(any(SoSalesOrderLine.class));
    }

    /** 他单的 lineId 不会被误改：按新增处理并重置 lineId */
    @Test
    void updateByBo_treatsForeignLineIdAsNewLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(lineBo(88888L, "100", "1"))); // 不属于本单

        headerService.updateByBo(bo);

        ArgumentCaptor<List<SoSalesOrderLine>> inserted = ArgumentCaptor.forClass(List.class);
        verify(soSalesOrderLineMapper).insertBatch(inserted.capture());
        assertNull(inserted.getValue().get(0).getLineId());
        verify(soSalesOrderLineMapper, never()).updateById(any(SoSalesOrderLine.class));
    }

    // ==================== 金额服务端重算（T1.8）====================

    private SoSalesOrderLineBo amountLine(String qty, String price, String discountPercent, String taxRate) {
        SoSalesOrderLineBo b = new SoSalesOrderLineBo();
        b.setMaterialId(100L);
        b.setOrderedQty(new BigDecimal(qty));
        b.setUnitPrice(new BigDecimal(price));
        b.setDiscountPercent(new BigDecimal(discountPercent));
        b.setTaxRate(new BigDecimal(taxRate));
        return b;
    }

    /**
     * 新增：金额由服务端重算，客户端伪造的 0 会被覆盖。
     * 这条同时堵住「传 totalAmount=0 绕过 confirm() 信用额度校验」。
     */
    @Test
    void insertByBo_recomputesAmountsIgnoringClientValues() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setSoNo("SO-T-1");
        bo.setClientId(1L);
        bo.setTotalAmount(BigDecimal.ZERO);      // 客户端伪造
        bo.setTaxAmount(BigDecimal.ZERO);
        bo.setDiscountAmount(BigDecimal.ZERO);
        bo.setDiscountRate(new BigDecimal("5"));
        bo.setSoSalesOrderLineList(List.of(amountLine("10", "100", "5", "13")));

        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);

        headerService.insertByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> headerCaptor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(headerCaptor.capture());
        SoSalesOrderHeader saved = headerCaptor.getValue();
        // 行金额 1000.00 / 行折扣 50.00 / 行税额 (1000-50)*13% = 123.50
        // 价税合计 = 950.00 + 123.50 = 1073.50；整单折扣 5% = 53.68；含税总金额 = 1019.82
        assertEquals(0, new BigDecimal("123.50").compareTo(saved.getTaxAmount()));
        assertEquals(0, new BigDecimal("53.68").compareTo(saved.getDiscountAmount()));
        assertEquals(0, new BigDecimal("1019.82").compareTo(saved.getTotalAmount()));

        ArgumentCaptor<List<SoSalesOrderLine>> lineCaptor = ArgumentCaptor.forClass(List.class);
        verify(soSalesOrderLineMapper).insertBatch(lineCaptor.capture());
        SoSalesOrderLine savedLine = lineCaptor.getValue().get(0);
        assertEquals(0, new BigDecimal("1000.00").compareTo(savedLine.getLineAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(savedLine.getDiscountAmount()));
        assertEquals(0, new BigDecimal("123.50").compareTo(savedLine.getTaxAmount()));
    }

    /** 改单同样服务端重算金额 */
    @Test
    void updateByBo_recomputesAmounts() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setTotalAmount(BigDecimal.ONE);   // 客户端伪造
        bo.setDiscountRate(BigDecimal.ZERO);
        bo.setSoSalesOrderLineList(List.of(amountLine("2", "50", "0", "10")));

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).updateById(captor.capture());
        // 行金额 100.00，税额 10.00，价税合计 110.00，整单折扣 0 -> 含税总金额 110.00
        assertEquals(0, new BigDecimal("110.00").compareTo(captor.getValue().getTotalAmount()));
    }

    // ==================== §8.6 验收规格（T1.1）—— 把计划里的验收清单变成可执行断言 ====================

    /**
     * §8.6 验收项 1：建单 3 行 → 改单（改第 1 行数量、删第 3 行、加第 4 行）
     * → 断言第 1、2 行 line_id **不变**，第 3 行被删除，第 4 行为新增。
     * <p>这是 D3 决策（差量更新保留行 ID）最直接的验收。</p>
     */
    @Test
    void acceptance_threeLinesEdit_keepsIdsDeletesRemovedAndInsertsNew() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(
            existingLine(100L, "100", "10", "0", "0", "0"),
            existingLine(101L, "200", "5", "0", "0", "0"),
            existingLine(102L, "300", "2", "0", "0", "0")));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.deleteById(any(Serializable.class))).thenReturn(1);
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        when(prdProductionPlanMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(
            lineBo(100L, "100", "12"),  // 改第 1 行数量
            lineBo(101L, "200", "5"),   // 第 2 行原样
            lineBo(null, "400", "7"))); // 加第 4 行（无 lineId）

        headerService.updateByBo(bo);

        // 第 1、2 行按原 line_id 更新（ID 不变），第 3 行不在保留集合内被删除
        ArgumentCaptor<SoSalesOrderLine> upd = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper, times(2)).updateById(upd.capture());
        List<Long> updatedIds = upd.getAllValues().stream().map(SoSalesOrderLine::getLineId).toList();
        assertTrue(updatedIds.contains(100L) && updatedIds.contains(101L),
            "第 1、2 行必须按原 lineId 更新: " + updatedIds);
        verify(soSalesOrderLineMapper).deleteById(102L);

        // 新行为全新插入，lineId 必须被重置为 null（不能沿用客户端传入的 ID）
        ArgumentCaptor<List<SoSalesOrderLine>> ins = ArgumentCaptor.forClass(List.class);
        verify(soSalesOrderLineMapper).insertBatch(ins.capture());
        assertEquals(1, ins.getValue().size());
        assertNull(ins.getValue().get(0).getLineId(), "新增行必须重置 lineId");
        assertEquals(0, new BigDecimal("7").compareTo(ins.getValue().get(0).getOrderedQty()));
    }

    /**
     * §8.6 验收项 2：确认订单已生成生产计划 → 改单改交期
     * → 断言关联的生产计划**不被改动**，且该行仍按原 line_id 更新
     * （即 {@code prd_production_plan.order_line_id} 仍可解析 —— 计划链不断）。
     */
    @Test
    void acceptance_changeDeliveryDate_keepsLineIdAndDoesNotTouchProductionPlan() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "0", "0", "0")));
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderLineBo line = lineBo(100L, "100", "10");
        line.setRequestedDate(new Date());   // 只改交期
        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(line));

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderLine> captor = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper).updateById(captor.capture());
        // 关键：按原 line_id 原地更新，而不是「删掉重插」——否则生产计划的 order_line_id 会指向已删除行
        assertEquals(100L, captor.getValue().getLineId());
        assertNotNull(captor.getValue().getRequestedDate());
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
        verify(soSalesOrderLineMapper, never()).insertBatch(anyList());
        // 改单不得触碰生产计划
        verify(prdProductionPlanMapper, never()).updateById(any(PrdProductionPlan.class));
        verify(prdProductionPlanMapper, never()).deleteById(any(Serializable.class));
    }

    // ==================== 差量更新的真实场景（复审用）====================

    /**
     * 同一订单两行是**同一物料**时，改其中一行的数量不得影响另一行。
     * <p>差量更新按 {@code lineId} 匹配（而非按物料），这是与「先删后插」最本质的区别；
     * 若哪天有人把匹配改成按物料聚合，本用例会失败。</p>
     */
    @Test
    void updateByBo_sameMaterialTwoLines_onlyTouchesTargetLine() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(
            existingLine(100L, "100", "10", "0", "0", "0"),
            existingLine(101L, "100", "5", "0", "0", "0"))); // 同物料 100
        when(soSalesOrderLineMapper.updateById(any(SoSalesOrderLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(
            lineBo(100L, "100", "12"),   // 只改第一行数量 10 -> 12
            lineBo(101L, "100", "5")));  // 第二行原样回传

        headerService.updateByBo(bo);

        // 两行都保留、各更新一次，且各自数量互不串改
        ArgumentCaptor<SoSalesOrderLine> captor = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper, times(2)).updateById(captor.capture());
        Map<Long, BigDecimal> qtyById = new HashMap<>();
        for (SoSalesOrderLine line : captor.getAllValues()) {
            qtyById.put(line.getLineId(), line.getOrderedQty());
        }
        assertEquals(0, new BigDecimal("12").compareTo(qtyById.get(100L)));
        assertEquals(0, new BigDecimal("5").compareTo(qtyById.get(101L)));
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
        verify(soSalesOrderLineMapper, never()).insertBatch(anyList());
    }

    /**
     * 只新增一行的场景：不应产生任何删除，已有行原样保留。
     * <p>覆盖"改单只加一行"这一最常见操作，确认它不会误删或误改已有行。</p>
     */
    @Test
    void updateByBo_onlyAppendsNewLine_noDeleteOrUpdate() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "0", "0", "0")));
        when(soSalesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of(
            lineBo(100L, "100", "10"),  // 已有行原样
            lineBo(null, "200", "3"))); // 新增行

        headerService.updateByBo(bo);

        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
        verify(soSalesOrderLineMapper).insertBatch(anyList());
        // 已有行即使内容未变也会走一次白名单更新（幂等写入），但 lineId 必须保持
        ArgumentCaptor<SoSalesOrderLine> captor = ArgumentCaptor.forClass(SoSalesOrderLine.class);
        verify(soSalesOrderLineMapper).updateById(captor.capture());
        assertEquals(100L, captor.getValue().getLineId());
    }

    // ==================== 流程回调状态改写守卫（加固轮）====================

    /** 流程回调改为条件更新，不再用无条件 updateById（否则会覆盖已取消/已确认的订单） */
    @Test
    void afterFlowHandle_usesConditionalUpdate() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_APPROVING));
        when(baseMapper.update(any(), any())).thenReturn(1);

        headerService.afterFlowHandle(flowBusiness(ActStatus.pass));

        ArgumentCaptor<Wrapper<SoSalesOrderHeader>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        verify(baseMapper, never()).updateById(any(SoSalesOrderHeader.class));
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("order_status"), "应为带状态条件的更新: " + segment);
    }

    /**
     * 订单在 APPROVING 时被取消，随后审批人撤销流程 —— 回调不得把 CANCELLED 改回 DRAFT。
     * <p>条件更新影响 0 行，回调应静默忽略而不是抛异常（流程侧不需要感知）。</p>
     */
    @Test
    void afterFlowHandle_doesNotResurrectCancelledOrder() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CANCELLED));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> headerService.afterFlowHandle(flowBusiness(ActStatus.revoke)));

        verify(baseMapper).update(any(), any());
        verify(baseMapper, never()).updateById(any(SoSalesOrderHeader.class));
    }

    /**
     * 订单 APPROVED 后被 confirm（已冻结额度/预留库存/生成生产计划），随后审批人撤销流程 ——
     * 回调不得把它改回 DRAFT（否则用户可再次确认，导致重复冻结/预留/建计划）。
     */
    @Test
    void afterFlowHandle_doesNotRevertConfirmedOrder() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> headerService.afterFlowHandle(flowBusiness(ActStatus.revoke)));

        verify(baseMapper).update(any(), any());
        verify(baseMapper, never()).updateById(any(SoSalesOrderHeader.class));
    }

    /** 未知的流程动作不产生任何更新 */
    @Test
    void afterFlowHandle_unknownAction_doesNothing() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));

        headerService.afterFlowHandle(flowBusiness("未知动作"));

        verify(baseMapper, never()).update(any(), any());
        verify(baseMapper, never()).updateById(any(SoSalesOrderHeader.class));
    }

    /** 订单不存在时直接返回 */
    @Test
    void afterFlowHandle_missingOrder_returnsQuietly() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        assertDoesNotThrow(() -> headerService.afterFlowHandle(flowBusiness(ActStatus.pass)));

        verify(baseMapper, never()).update(any(), any());
    }

    private WfMyBusiness flowBusiness(String actStatus) {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("1");
        business.setActStatus(actStatus);
        return business;
    }

    // ==================== 服务端权威字段（加固轮）====================

    /** 新增：生命周期时间戳与信用检查结果一律复位，不接受客户端伪造 */
    @Test
    void insertByBo_resetsLifecycleAndCreditFields() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setSoNo("SO-T-3");
        bo.setClientId(1L);
        bo.setConfirmedTime(new Date());        // 伪造
        bo.setFulfilledTime(new Date());        // 伪造
        bo.setCreditStatus("CHECKED_OK");       // 伪造
        bo.setCreditBlockedBy(99L);             // 伪造
        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);

        headerService.insertByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(captor.capture());
        SoSalesOrderHeader saved = captor.getValue();
        assertNull(saved.getConfirmedTime());
        assertNull(saved.getFulfilledTime());
        assertNull(saved.getClosedTime());
        assertNull(saved.getCancelledTime());
        assertNull(saved.getCreditCheckTime());
        assertNull(saved.getCreditBlockedBy());
        assertEquals("NOT_CHECKED", saved.getCreditStatus());
    }

    /** 改单：生命周期时间戳与信用检查结果从库中还原，不得改写（与单号/状态同理） */
    @Test
    void updateByBo_restoresLifecycleAndCreditFieldsFromDb() {
        SoSalesOrderHeader existing = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        existing.setCreditStatus("CHECKED_BLOCKED");
        existing.setCreditBlockedBy(7L);
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setCreditStatus("CHECKED_OK");   // 伪造
        bo.setCreditBlockedBy(99L);         // 伪造
        bo.setSoSalesOrderLineList(null);

        headerService.updateByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals("CHECKED_BLOCKED", captor.getValue().getCreditStatus());
        assertEquals(7L, captor.getValue().getCreditBlockedBy());
    }

    // ==================== 明细「未提交」语义（null vs 空集合）====================

    /**
     * 明细为 null 表示「本次不提交明细」：不动明细、不动金额。
     * <p>修复前 null 会让 diffUpdateLines 把所有已有行当「请求中未出现」而逻辑删除，
     * 且 recalcAmounts 把表头金额算成 0 —— API 客户端做部分更新（例如只改备注）
     * 会静默清空整单明细与金额。</p>
     */
    @Test
    void updateByBo_nullLineList_keepsLinesAndAmountsFromDb() {
        SoSalesOrderHeader existing = header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT);
        existing.setTotalAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setDiscountAmount(new BigDecimal("50.00"));
        existing.setDiscountRate(new BigDecimal("5"));
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setRemark("只改备注");
        // 客户端伪造金额
        bo.setTotalAmount(BigDecimal.ZERO);
        bo.setTaxAmount(BigDecimal.ZERO);
        bo.setDiscountAmount(BigDecimal.ZERO);
        bo.setSoSalesOrderLineList(null);

        headerService.updateByBo(bo);

        // 不应触碰明细
        verify(soSalesOrderLineMapper, never()).selectList(any());
        verify(soSalesOrderLineMapper, never()).deleteById(any(Serializable.class));
        verify(soSalesOrderLineMapper, never()).updateById(any(SoSalesOrderLine.class));

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).updateById(captor.capture());
        SoSalesOrderHeader saved = captor.getValue();
        // 金额一律还原为库中原值，客户端伪造无效
        assertEquals(0, new BigDecimal("1000.00").compareTo(saved.getTotalAmount()));
        assertEquals(0, new BigDecimal("130.00").compareTo(saved.getTaxAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getDiscountAmount()));
        assertEquals("只改备注", saved.getRemark());
    }

    /** 显式传空数组才是「清空全部明细」，且金额归零 */
    @Test
    void updateByBo_emptyLineList_clearsAllLinesAndZeroesAmounts() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "0", "0", "0")));
        when(soSalesOrderLineMapper.deleteById(any(Serializable.class))).thenReturn(1);
        when(prdProductionPlanMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.updateById(any(SoSalesOrderHeader.class))).thenReturn(1);

        SoSalesOrderHeaderBo bo = updateBo();
        bo.setSoSalesOrderLineList(List.of());

        headerService.updateByBo(bo);

        verify(soSalesOrderLineMapper).deleteById(100L);
        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getTotalAmount()));
    }

    /** 新增时明细为 null 视作空明细：金额归零，客户端伪造金额无效 */
    @Test
    void insertByBo_nullLineList_zeroesAmounts() {
        SoSalesOrderHeaderBo bo = new SoSalesOrderHeaderBo();
        bo.setSoNo("SO-T-2");
        bo.setClientId(1L);
        bo.setTotalAmount(new BigDecimal("999999")); // 伪造
        bo.setSoSalesOrderLineList(null);
        when(baseMapper.insert(any(SoSalesOrderHeader.class))).thenReturn(1);

        headerService.insertByBo(bo);

        ArgumentCaptor<SoSalesOrderHeader> captor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getTaxAmount()));
    }

    // ==================== deleteByIds 状态门禁与收尾（T1.2）====================

    /** 空入参直接返回 0，不做任何查询 */
    @Test
    void deleteByIds_emptyIds_returnsZero() {
        assertEquals(0, headerService.deleteByIds(new Long[0]));
        verify(baseMapper, never()).selectBatchIds(anyList());
    }

    /** 非草稿订单禁止删除（须走 cancel/close 收尾） */
    @Test
    void deleteByIds_rejectsNonDraft() {
        SoSalesOrderHeader confirmed = header(1L, ISoSalesOrderHeaderService.STATUS_CONFIRMED);
        when(baseMapper.selectBatchIds(anyList())).thenReturn(List.of(confirmed));

        ServiceException ex = assertThrows(ServiceException.class, () -> headerService.deleteByIds(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(creditCheckService, never()).release(any());
        verify(stockReservationService, never()).unreserve(any(), any(), any());
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    /** 草稿删除：释放信用与预留、关闭残留生产计划、删行删头 */
    @Test
    void deleteByIds_draft_releasesCreditReservationAndClosesPlans() {
        when(baseMapper.selectBatchIds(anyList()))
            .thenReturn(List.of(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT)));
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(existingLine(100L, "100", "10", "0", "0", "0")));

        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(900L);
        plan.setStatus("DRAFT");
        when(prdProductionPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(prdProductionPlanMapper.updateById(any(PrdProductionPlan.class))).thenReturn(1);
        when(soSalesOrderLineMapper.delete(any())).thenReturn(1);
        when(baseMapper.deleteBatchIds(anyList())).thenReturn(1);

        assertEquals(1, headerService.deleteByIds(new Long[]{1L}));

        verify(creditCheckService).release(1L);
        verify(stockReservationService).unreserve("SALES_ORDER", 1L, null);
        // 关联生产计划被置为 CLOSED，不留在已删除的订单行上
        ArgumentCaptor<PrdProductionPlan> planCaptor = ArgumentCaptor.forClass(PrdProductionPlan.class);
        verify(prdProductionPlanMapper).updateById(planCaptor.capture());
        assertEquals("CLOSED", planCaptor.getValue().getStatus());
        verify(soSalesOrderLineMapper).delete(any());
        verify(baseMapper).deleteBatchIds(anyList());
    }

    // ==================== 履约链路分流（Q8）====================

    /** 构造带来源类型的物料 */
    private BdMaterial material(Long id, String sourceType) {
        BdMaterial m = new BdMaterial();
        m.setMaterialId(id);
        m.setMaterialCode("M-" + id);
        m.setMaterialName("物料" + id);
        m.setSourceType(sourceType);
        return m;
    }

    /**
     * 委外（SUBCONTRACT）行在**无可用供应商**时不自动建委外单（ERP-SO-09①）。
     * <p>原实现"供应商留空也建单"，但委外单下达时必然因供应商校验卡在 DRAFT ——
     * 改为跳过并告警（"物料未配置可用供应商，跳过自动创建委外单"），交由人工处理。</p>
     * <p>无论是否建单，委外行都**不**占用本仓现货、也不生成生产计划。</p>
     */
    @Test
    void confirm_subcontractLineWithoutVendor_skipsSubcontractCreation() {
        SoSalesOrderLine subcontractLine = line(100L, "10", "0", null);
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(subcontractLine));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "SUBCONTRACT"));
        // 无可用供应商 -> 不建委外单（只 warn）
        when(bdMaterialVendorMapper.selectList(any())).thenReturn(List.of());

        headerService.confirm(1L);

        verify(prdSubcontractMapper, never()).insert(any(PrdSubcontract.class));
        // 委外不走库存预留（货源不是本仓现货）
        verify(stockReservationService, never()).reserve(any(), any(), any(), anyString(), any(), any());
        // 也不生成生产计划
        verify(prdProductionPlanMapper, never()).insert(any(PrdProductionPlan.class));
    }

    /** 委外行优先取默认供应商（is_default=1） */
    @Test
    void confirm_subcontractLine_picksDefaultVendor() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "0", null)));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "SUBCONTRACT"));
        when(autoCodeUtil.genSerialCode(eq("PRD_SUBCONTRACT_CODE"), any())).thenReturn("SC-0002");
        when(prdSubcontractMapper.insert(any(PrdSubcontract.class))).thenReturn(1);

        BdMaterialVendor vendor = new BdMaterialVendor();
        vendor.setVendorId(55L);
        vendor.setVendorCode("V-55");
        vendor.setVendorName("委外供应商");
        vendor.setPrice(new BigDecimal("8.50"));
        // mapper 已按 is_default desc, priority asc 排序，取第一条即默认供应商
        when(bdMaterialVendorMapper.selectList(any())).thenReturn(List.of(vendor));

        headerService.confirm(1L);

        ArgumentCaptor<PrdSubcontract> captor = ArgumentCaptor.forClass(PrdSubcontract.class);
        verify(prdSubcontractMapper).insert(captor.capture());
        assertEquals(55L, captor.getValue().getVendorId());
        assertEquals("委外供应商", captor.getValue().getVendorName());
        // 总金额 = 计划数量 × 协议价 = 10 × 8.50
        assertEquals(0, new BigDecimal("85.00").compareTo(captor.getValue().getTotalAmount()));
    }

    /**
     * 调拨（TRANSFER）行确认订单后自动建调拨单：
     * 目的仓 = 行上的默认仓，来源仓 = 该物料可用库存最多的其他仓。
     */
    @Test
    void confirm_transferLine_createsTransferWithStockSource() {
        SoSalesOrderLine transferLine = line(100L, "10", "0", null);
        transferLine.setWarehouseId(3L);   // 目的仓：成品仓
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(transferLine));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "TRANSFER"));
        when(autoCodeUtil.genSerialCode(eq("WM_TRANSFER_CODE"), any())).thenReturn("DB-0001");
        when(wmTransferMapper.insert(any(WmTransfer.class))).thenReturn(1);
        when(wmTransferLineMapper.insert(any(WmTransferLine.class))).thenReturn(1);
        when(wmWarehouseMapper.selectById(3L)).thenReturn(warehouse(3L, "成品仓库", "WH03"));

        // 仓1 可用 5（在库10-预留5），仓2 可用 20（在库20-预留0），目的仓3 可用 100 应被排除
        when(wmMaterialStockMapper.selectList(any())).thenReturn(List.of(
            stock(1L, "10", "5"),
            stock(2L, "20", "0"),
            stock(3L, "100", "0")));

        headerService.confirm(1L);

        ArgumentCaptor<WmTransfer> captor = ArgumentCaptor.forClass(WmTransfer.class);
        verify(wmTransferMapper).insert(captor.capture());
        WmTransfer saved = captor.getValue();
        assertEquals("DB-0001", saved.getTransferNo());
        assertEquals(2L, saved.getFromWarehouseId(), "来源仓应为可用库存最多的仓（仓2）");
        assertEquals(3L, saved.getToWarehouseId(), "目的仓应为行上的默认仓");
        assertEquals("WAREHOUSE", saved.getTransferType());
        assertEquals(0, new BigDecimal("10").compareTo(saved.getTotalQuantity()));
        assertEquals("DRAFT", saved.getStatus());

        ArgumentCaptor<WmTransferLine> lineCaptor = ArgumentCaptor.forClass(WmTransferLine.class);
        verify(wmTransferLineMapper).insert(lineCaptor.capture());
        assertEquals(100L, lineCaptor.getValue().getMaterialId());
        assertEquals(0, new BigDecimal("10").compareTo(lineCaptor.getValue().getQuantity()));

        // 调拨不走库存预留
        verify(stockReservationService, never()).reserve(any(), any(), any(), anyString(), any(), any());
    }

    /** 调拨行：目的仓之外无可用库存时不建单（避免同仓调拨这类无意义单据） */
    @Test
    void confirm_transferLine_skipsWhenNoSourceStock() {
        SoSalesOrderLine transferLine = line(100L, "10", "0", null);
        transferLine.setWarehouseId(3L);
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(transferLine));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "TRANSFER"));
        // 只有目的仓自己有货 -> 无可调来源
        when(wmMaterialStockMapper.selectList(any())).thenReturn(List.of(stock(3L, "100", "0")));

        headerService.confirm(1L);

        verify(wmTransferMapper, never()).insert(any(WmTransfer.class));
    }

    /** 调拨行：行上未指定默认仓时不建单 */
    @Test
    void confirm_transferLine_skipsWhenNoTargetWarehouse() {
        SoSalesOrderLine transferLine = line(100L, "10", "0", null);
        transferLine.setWarehouseId(null);
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any())).thenReturn(List.of(transferLine));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "TRANSFER"));

        headerService.confirm(1L);

        verify(wmTransferMapper, never()).insert(any(WmTransfer.class));
    }

    /** 采购（PROCUREMENT）行仍走库存预留，且不建委外/调拨单 */
    @Test
    void confirm_procurementLine_reservesStockOnly() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "0", null)));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "PROCUREMENT"));
        when(stockReservationService.reserve(any(), any(), any(), anyString(), any(), any()))
            .thenReturn(new BigDecimal("10"));

        headerService.confirm(1L);

        verify(stockReservationService).reserve(eq(100L), any(), any(), eq("SALES_ORDER"), eq(1L), eq(100L));
        verify(prdSubcontractMapper, never()).insert(any(PrdSubcontract.class));
        verify(wmTransferMapper, never()).insert(any(WmTransfer.class));
        verify(prdProductionPlanMapper, never()).insert(any(PrdProductionPlan.class));
    }

    /** 加工（PROCESS）行生成生产计划，且不预留库存、不建委外/调拨单 */
    @Test
    void confirm_processLine_createsProductionPlanOnly() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "0", null)));
        when(bdMaterialMapper.selectById(100L)).thenReturn(material(100L, "PROCESS"));
        when(autoCodeUtil.genSerialCode(eq("PRD_PLAN_CODE"), any())).thenReturn("MPS-0001");
        when(prdProductionPlanMapper.insert(any(PrdProductionPlan.class))).thenReturn(1);
        when(bdBomMapper.selectOne(any())).thenReturn(null);

        headerService.confirm(1L);

        verify(prdProductionPlanMapper).insert(any(PrdProductionPlan.class));
        verify(stockReservationService, never()).reserve(any(), any(), any(), anyString(), any(), any());
        verify(prdSubcontractMapper, never()).insert(any(PrdSubcontract.class));
        verify(wmTransferMapper, never()).insert(any(WmTransfer.class));
    }

    /** 物料不存在时三条件分支都不触发，不抛异常 */
    @Test
    void confirm_missingMaterial_doesNotBreak() {
        when(baseMapper.selectById(1L)).thenReturn(header(1L, ISoSalesOrderHeaderService.STATUS_DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(soSalesOrderLineMapper.selectList(any()))
            .thenReturn(List.of(line(100L, "10", "0", null)));
        when(bdMaterialMapper.selectById(100L)).thenReturn(null);
        // 物料为空 -> sourceType 为 null -> 走预留分支
        when(stockReservationService.reserve(any(), any(), any(), anyString(), any(), any()))
            .thenReturn(BigDecimal.ZERO);

        assertDoesNotThrow(() -> headerService.confirm(1L));

        verify(prdSubcontractMapper, never()).insert(any(PrdSubcontract.class));
        verify(wmTransferMapper, never()).insert(any(WmTransfer.class));
    }

    /** 构造仓库 */
    private WmWarehouse warehouse(Long id, String name, String code) {
        WmWarehouse w = new WmWarehouse();
        w.setWarehouseId(id);
        w.setWarehouseName(name);
        w.setWarehouseCode(code);
        return w;
    }

    /** 构造库存行（在库/已预留） */
    private WmMaterialStock stock(Long warehouseId, String onHand, String reserved) {
        WmMaterialStock s = new WmMaterialStock();
        s.setMaterialId(100L);
        s.setWarehouseId(warehouseId);
        s.setWarehouseCode("WH0" + warehouseId);
        s.setWarehouseName("仓" + warehouseId);
        s.setStockQuantity(new BigDecimal(onHand));
        s.setReservedQuantity(new BigDecimal(reserved));
        return s;
    }
}
