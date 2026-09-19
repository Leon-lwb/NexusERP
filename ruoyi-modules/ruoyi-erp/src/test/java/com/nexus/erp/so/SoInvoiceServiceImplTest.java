package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.service.IBdClientService;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoInvoiceLineMapper;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.so.service.impl.SoInvoiceServiceImpl;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售发票 Service 单元测试（批次 0 回归基线）。
 * <p>发票是本模块写得最稳的部分（乐观锁 + 原子累加 + 防超开），本类用于把这套既有正确行为
 * 固化成网，防止批次 1「开票收口」（把手工建票/改票也纳入门禁）时改坏自动开票路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoInvoiceServiceImplTest {

    @Mock
    private SoInvoiceMapper baseMapper;
    @Mock
    private SoInvoiceLineMapper lineMapper;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Mock
    private ISoSalesOrderHeaderService soSalesOrderHeaderService;
    @Mock
    private IBdClientService bdClientService;
    @Mock
    private SoSalesOrderLineMapper soSalesOrderLineMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SoInvoiceServiceImpl invoiceService;

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

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoInvoice.class);
        TableInfoHelper.initTableInfo(assistant, SoInvoiceLine.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderLine.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoSalesOrderLineVo soLine(Long lineId, String delivered, String invoiced,
                                      String unitPrice, String taxRate) {
        SoSalesOrderLineVo vo = new SoSalesOrderLineVo();
        vo.setLineId(lineId);
        vo.setSoId(1L);
        vo.setLineNo("0010");
        vo.setMaterialId(100L);
        vo.setMaterialCode("M-001");
        vo.setMaterialName("测试物料");
        vo.setDeliveredQty(new BigDecimal(delivered));
        vo.setInvoicedQty(new BigDecimal(invoiced));
        vo.setUnitPrice(new BigDecimal(unitPrice));
        vo.setTaxRate(new BigDecimal(taxRate));
        vo.setUnit("件");
        return vo;
    }

    private SoSalesOrderHeaderVo headerVo(List<SoSalesOrderLineVo> lines) {
        SoSalesOrderHeaderVo vo = new SoSalesOrderHeaderVo();
        vo.setSoId(1L);
        vo.setSoNo("SO-TEST-001");
        vo.setClientId(1L);
        vo.setClientName("测试客户");
        vo.setCurrency("CNY");
        vo.setExchangeRate(BigDecimal.ONE);
        vo.setSoSalesOrderLineList(lines);
        return vo;
    }

    private SoInvoice invoice(Long id, String status, String totalAmount, String verifiedAmount) {
        SoInvoice inv = new SoInvoice();
        inv.setInvoiceId(id);
        inv.setInvoiceNo("FP-TEST-" + id);
        inv.setStatus(status);
        inv.setTotalAmount(new BigDecimal(totalAmount));
        inv.setVerifiedAmount(verifiedAmount == null ? null : new BigDecimal(verifiedAmount));
        inv.setVersion(0);
        return inv;
    }

    // ==================== generateDraftFromOrder ====================

    /** 订单不存在 -> 不生成 */
    @Test
    void generateDraft_returnsNullWhenOrderMissing() {
        when(soSalesOrderHeaderService.selectById(1L)).thenReturn(null);
        assertNull(invoiceService.generateDraftFromOrder(1L));
        verify(baseMapper, never()).insert(any(SoInvoice.class));
    }

    /** 订单无行 -> 不生成 */
    @Test
    void generateDraft_returnsNullWhenNoLines() {
        when(soSalesOrderHeaderService.selectById(1L)).thenReturn(headerVo(null));
        assertNull(invoiceService.generateDraftFromOrder(1L));
        verify(baseMapper, never()).insert(any(SoInvoice.class));
    }

    /** 全部已开票（可开票量 <= 0）-> 不生成 */
    @Test
    void generateDraft_returnsNullWhenNothingBillable() {
        when(soSalesOrderHeaderService.selectById(1L))
            .thenReturn(headerVo(List.of(soLine(100L, "10", "10", "100", "13"))));

        assertNull(invoiceService.generateDraftFromOrder(1L));
        verify(baseMapper, never()).insert(any(SoInvoice.class));
    }

    /** 可开票量 = 已发货 - 已开票，且行金额/税额/价税合计按服务端口径计算 */
    @Test
    void generateDraft_computesRemainingAndAmounts() {
        List<SoSalesOrderLineVo> lines = new ArrayList<>();
        lines.add(soLine(100L, "10", "4", "100", "13")); // remaining 6 -> 600.00 / 78.00 / 678.00
        lines.add(soLine(101L, "5", "5", "100", "13"));  // remaining 0 -> 跳过
        when(soSalesOrderHeaderService.selectById(1L)).thenReturn(headerVo(lines));
        when(bdClientService.selectById(1L)).thenReturn(null);
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(777L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        Long invoiceId = invoiceService.generateDraftFromOrder(1L);
        assertEquals(777L, invoiceId);

        ArgumentCaptor<SoInvoice> invoiceCaptor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).insert(invoiceCaptor.capture());
        SoInvoice inserted = invoiceCaptor.getValue();
        assertEquals(ISoInvoiceService.STATUS_DRAFT, inserted.getStatus());
        assertEquals("CNY", inserted.getCurrency());
        assertEquals(0, new BigDecimal("600.00").compareTo(inserted.getAmount()));
        assertEquals(0, new BigDecimal("78.00").compareTo(inserted.getTaxAmount()));
        assertEquals(0, new BigDecimal("678.00").compareTo(inserted.getTotalAmount()));

        ArgumentCaptor<SoInvoiceLine> lineCaptor = ArgumentCaptor.forClass(SoInvoiceLine.class);
        verify(lineMapper).insert(lineCaptor.capture());
        SoInvoiceLine onlyLine = lineCaptor.getValue();
        assertEquals(0, new BigDecimal("6").compareTo(onlyLine.getQuantity()));
        assertEquals(100L, onlyLine.getSoLineId());
        assertEquals(777L, onlyLine.getInvoiceId());
        assertEquals(0, new BigDecimal("600.00").compareTo(onlyLine.getLineAmount()));
        assertEquals(0, new BigDecimal("78.00").compareTo(onlyLine.getTaxAmount()));
        assertEquals(0, new BigDecimal("678.00").compareTo(onlyLine.getTotalAmount()));

        // 原子累加已开票数量，且带「invoiced_qty + ? <= delivered_qty」门禁
        verify(soSalesOrderLineMapper).update(any(), any());
    }

    /** 并发已开票（门禁 affected=0）-> 抛异常并回滚本次草稿 */
    @Test
    void generateDraft_throwsWhenConcurrentInvoiceDetected() {
        when(soSalesOrderHeaderService.selectById(1L))
            .thenReturn(headerVo(List.of(soLine(100L, "10", "0", "100", "13"))));
        when(bdClientService.selectById(1L)).thenReturn(null);
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(888L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.generateDraftFromOrder(1L));
        assertTrue(ex.getMessage().contains("已开票数量变化"), "实际: " + ex.getMessage());
    }

    // ==================== deleteByIds ====================

    /** 非草稿状态不可删除 */
    @Test
    void deleteByIds_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0"));

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.deleteByIds(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("不允许删除"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).update(any(), any());
    }

    /** 删除草稿：回退订单行已开票数量，防重复开票计数残留 */
    @Test
    void deleteByIds_draft_rollsBackInvoicedQty() {
        SoInvoice draft = invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0");
        when(baseMapper.selectById(1L)).thenReturn(draft);

        SoInvoiceLine line = new SoInvoiceLine();
        line.setLineId(10L);
        line.setInvoiceId(1L);
        line.setSoLineId(100L);
        line.setQuantity(new BigDecimal("6"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.deleteBatchIds(anyList())).thenReturn(1);

        assertEquals(1, invoiceService.deleteByIds(new Long[]{1L}));

        verify(soSalesOrderLineMapper).update(any(), any());
        verify(lineMapper).delete(any());
        verify(baseMapper).deleteBatchIds(anyList());
    }

    // ==================== verifyInvoice ====================

    /** 核销金额必须大于 0 */
    @Test
    void verifyInvoice_rejectsNonPositiveAmount() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.verifyInvoice(1L, BigDecimal.ZERO, null));
        assertTrue(ex.getMessage().contains("必须大于0"), "实际: " + ex.getMessage());
    }

    /** 仅已开票可核销 */
    @Test
    void verifyInvoice_rejectsNonIssued() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.verifyInvoice(1L, new BigDecimal("100"), null));
        assertTrue(ex.getMessage().contains("仅已开票"), "实际: " + ex.getMessage());
    }

    /** 核销达价税合计 -> 转 VERIFIED */
    @Test
    void verifyInvoice_marksVerifiedWhenFullySettled() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0"));
        when(baseMapper.update(any(), any())).thenReturn(1);

        invoiceService.verifyInvoice(1L, new BigDecimal("678.00"), 5L);

        verify(baseMapper).update(any(), any());
    }

    // ==================== cancelInvoice / redFlush ====================

    /** 已核销发票不可作废 */
    @Test
    void cancelInvoice_rejectsVerified() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_VERIFIED, "678.00", "678.00"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.cancelInvoice(1L, "作废"));
        assertTrue(ex.getMessage().contains("不允许作废"), "实际: " + ex.getMessage());
    }

    /** 作废：回退已开票数量并置 CANCELLED */
    @Test
    void cancelInvoice_issued_rollsBackAndCancels() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0"));

        SoInvoiceLine line = new SoInvoiceLine();
        line.setLineId(10L);
        line.setInvoiceId(1L);
        line.setSoLineId(100L);
        line.setQuantity(new BigDecimal("6"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        invoiceService.cancelInvoice(1L, "客户要求作废");

        verify(soSalesOrderLineMapper).update(any(), any());
        verify(baseMapper).update(any(), any());
    }

    /** 红字发票不可再次红冲 */
    @Test
    void redFlush_rejectsRedInvoice() {
        SoInvoice red = invoice(1L, ISoInvoiceService.STATUS_ISSUED, "-678.00", "0");
        red.setRedFlag("1");
        when(baseMapper.selectById(1L)).thenReturn(red);

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.redFlush(1L, "红冲"));
        assertTrue(ex.getMessage().contains("不允许再次红冲"), "实际: " + ex.getMessage());
    }

    /** 仅已开票/已核销的蓝字发票可红冲 */
    @Test
    void redFlush_rejectsDraftBlueInvoice() {
        SoInvoice draft = invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0");
        draft.setRedFlag("0");
        when(baseMapper.selectById(1L)).thenReturn(draft);

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.redFlush(1L, "红冲"));
        assertTrue(ex.getMessage().contains("仅已开票/已核销"), "实际: " + ex.getMessage());
    }

    /** 红冲：复制原行取负，并冲减订单行已开票数量 */
    @Test
    void redFlush_copiesLinesAsNegativeAndOffsetsInvoicedQty() {
        SoInvoice blue = invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0");
        blue.setRedFlag("0");
        blue.setClientId(1L);
        blue.setClientName("测试客户");
        blue.setCurrency("CNY");
        blue.setExchangeRate(BigDecimal.ONE);
        when(baseMapper.selectById(1L)).thenReturn(blue);

        SoInvoiceLine orig = new SoInvoiceLine();
        orig.setLineId(10L);
        orig.setInvoiceId(1L);
        orig.setSoLineId(100L);
        orig.setQuantity(new BigDecimal("6"));
        orig.setUnitPrice(new BigDecimal("100"));
        orig.setLineAmount(new BigDecimal("600.00"));
        orig.setTaxRate(new BigDecimal("13"));
        orig.setTaxAmount(new BigDecimal("78.00"));
        orig.setTotalAmount(new BigDecimal("678.00"));
        orig.setSortOrder(0);
        when(lineMapper.selectList(any())).thenReturn(List.of(orig));

        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(999L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Long redId = invoiceService.redFlush(1L, "开票有误");
        assertEquals(999L, redId);

        ArgumentCaptor<SoInvoice> redCaptor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).insert(redCaptor.capture());
        SoInvoice red = redCaptor.getValue();
        assertEquals("1", red.getRedFlag());
        assertEquals(1L, red.getOriginalInvoiceId());
        assertEquals(0, new BigDecimal("-600.00").compareTo(red.getAmount()));
        assertEquals(0, new BigDecimal("-678.00").compareTo(red.getTotalAmount()));

        ArgumentCaptor<SoInvoiceLine> redLineCaptor = ArgumentCaptor.forClass(SoInvoiceLine.class);
        verify(lineMapper).insert(redLineCaptor.capture());
        assertEquals(0, new BigDecimal("-6").compareTo(redLineCaptor.getValue().getQuantity()));
        assertNotNull(redLineCaptor.getValue().getSoLineId());
    }
}
