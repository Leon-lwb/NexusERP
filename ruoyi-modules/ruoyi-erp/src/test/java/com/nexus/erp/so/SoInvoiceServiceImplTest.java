package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.service.IBdClientService;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.vo.SoInvoiceLineVo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.event.SoInvoiceIssuedEvent;
import com.nexus.erp.so.mapper.SoInvoiceLineMapper;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.so.service.impl.SoInvoiceServiceImpl;
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
import org.springframework.context.ApplicationEventPublisher;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import static org.mockito.Mockito.times;
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

        // 占用的是「可开票量」(已发货 − 已开票 = 10 − 4 = 6)，既不是已发货 10 也不是已开票 4，
        // 且带「invoiced_qty + ? <= delivered_qty」门禁
        assertTrue(capturedLineUpdateSqlSet().contains("+ 6"), "应只占用可开票量 6");
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

    /** 核销上限：累计核销额不得超过价税合计（原实现无上限） */
    @Test
    void verifyInvoice_rejectsOverVerify() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "600.00"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.verifyInvoice(1L, new BigDecimal("100.00"), 5L));
        assertTrue(ex.getMessage().contains("超过发票价税合计"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
    }

    /** verifiedAmount 为 null 的历史行不应 NPE */
    @Test
    void verifyInvoice_toleratesNullVerifiedAmount() {
        SoInvoice legacy = invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", null);
        when(baseMapper.selectById(1L)).thenReturn(legacy);
        when(baseMapper.update(any(), any())).thenReturn(1);

        invoiceService.verifyInvoice(1L, new BigDecimal("100.00"), null);

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

    /** 防重复红冲：同一张蓝票只能红冲一次（原实现可无限红冲） */
    @Test
    void redFlush_rejectsAlreadyRedFlushedInvoice() {
        SoInvoice blue = invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0");
        blue.setRedFlag("0");
        when(baseMapper.selectById(1L)).thenReturn(blue);
        // 已存在指向该蓝票的红字发票
        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.redFlush(1L, "红冲"));
        assertTrue(ex.getMessage().contains("已红冲"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(SoInvoice.class));
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
        when(baseMapper.selectCount(any())).thenReturn(0L);

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

        // 红冲必须把订单行已开票量按原行数量冲减回去（-6），否则红冲后无法重新开票
        assertTrue(capturedLineUpdateSqlSet().contains("+ -6"), "红冲应释放原行数量 6");
    }

    // ==================== 可开票量占用/释放收口（T1.4）====================

    /** 抓取发给订单行 mapper 的 SET 子句，用于断言占用/释放的数量与方向 */
    @SuppressWarnings("unchecked")
    private String capturedLineUpdateSqlSet() {
        ArgumentCaptor<Wrapper<SoSalesOrderLine>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(soSalesOrderLineMapper).update(any(), captor.capture());
        return ((LambdaUpdateWrapper<SoSalesOrderLine>) captor.getValue()).getSqlSet();
    }

    private SoInvoiceLine invoiceLine(Long lineId, Long soLineId, String quantity) {
        SoInvoiceLine l = new SoInvoiceLine();
        l.setLineId(lineId);
        l.setInvoiceId(1L);
        l.setSoLineId(soLineId);
        l.setQuantity(new BigDecimal(quantity));
        return l;
    }

    private SoInvoiceBo invoiceBo(List<SoInvoiceLine> lines) {
        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setInvoiceId(1L);
        bo.setSoInvoiceLineList(lines);
        return bo;
    }

    /** 手工建票：明细关联订单行时同样占用可开票量 */
    @Test
    void insertByBo_occupiesInvoicedQtyForLinkedLine() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setSoInvoiceLineList(List.of(invoiceLine(null, 100L, "6")));
        assertEquals(1, invoiceService.insertByBo(bo));

        assertTrue(capturedLineUpdateSqlSet().contains("+ 6"), "应占用 6");
    }

    /** 手工建票超开：占用门禁失败即拒绝建票（原实现完全绕过） */
    @Test
    void insertByBo_rejectsOverInvoiceOnLinkedLine() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(0);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setSoInvoiceLineList(List.of(invoiceLine(null, 100L, "6")));

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("已开票数量变化"), "实际: " + ex.getMessage());
    }

    /** 未关联订单行的纯手工票不占用可开票量 */
    @Test
    void insertByBo_skipsOccupationForUnlinkedLine() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setSoInvoiceLineList(List.of(invoiceLine(null, null, "6")));
        invoiceService.insertByBo(bo);

        verify(soSalesOrderLineMapper, never()).update(any(), any());
    }

    // ==================== 税率区间校验（Q3 百分数口径）====================

    /** 税率超过 100 拒绝（百分数语义下 0~100 才是合法区间） */
    @Test
    void insertByBo_rejectsTaxRateAbove100() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });

        SoInvoiceBo bo = new SoInvoiceBo();
        SoInvoiceLine line = invoiceLine(null, null, "6");
        line.setTaxRate(new BigDecimal("1300"));   // 疑似把 13% 写成了 1300
        bo.setSoInvoiceLineList(List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("0~100"), "实际: " + ex.getMessage());
        verify(lineMapper, never()).insert(any(SoInvoiceLine.class));
    }

    /** 税率为负拒绝 */
    @Test
    void insertByBo_rejectsNegativeTaxRate() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });

        SoInvoiceBo bo = new SoInvoiceBo();
        SoInvoiceLine line = invoiceLine(null, null, "6");
        line.setTaxRate(new BigDecimal("-1"));
        bo.setSoInvoiceLineList(List.of(line));

        assertThrows(ServiceException.class, () -> invoiceService.insertByBo(bo));
    }

    /** 0 与 100 是合法边界值 */
    @Test
    void insertByBo_acceptsTaxRateBoundaries() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        SoInvoiceLine zero = invoiceLine(null, null, "1");
        zero.setTaxRate(BigDecimal.ZERO);
        SoInvoiceLine hundred = invoiceLine(null, null, "1");
        hundred.setTaxRate(new BigDecimal("100"));
        bo.setSoInvoiceLineList(List.of(zero, hundred));

        assertDoesNotThrow(() -> invoiceService.insertByBo(bo));
        verify(lineMapper, times(2)).insert(any(SoInvoiceLine.class));
    }

    /** 改票同样校验税率区间 */
    @Test
    void updateByBo_rejectsTaxRateAbove100() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of());

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setInvoiceId(1L);
        SoInvoiceLine line = invoiceLine(null, null, "6");
        line.setTaxRate(new BigDecimal("1300"));
        bo.setSoInvoiceLineList(List.of(line));

        assertThrows(ServiceException.class, () -> invoiceService.updateByBo(bo));
    }

    // ==================== 流程回调守卫（加固轮）====================

    /**
     * 审批通过且状态确实流转到 ISSUED 时才发布过账事件。
     */
    @Test
    void afterFlowHandle_pass_publishesIssuedEventWhenTransitionHappens() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_APPROVING, "678.00", "0"));
        when(baseMapper.update(any(), any())).thenReturn(1);

        invoiceService.afterFlowHandle(flowBusiness(ActStatus.pass));

        ArgumentCaptor<Wrapper<SoInvoice>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        verify(baseMapper, never()).updateById(any(SoInvoice.class));
        assertTrue(captor.getValue().getSqlSegment().contains("status"), "应为带状态条件的更新");
        verify(applicationEventPublisher).publishEvent(any(SoInvoiceIssuedEvent.class));
    }

    /**
     * 发票已是 ISSUED 时再次收到 pass 回调：条件更新影响 0 行，
     * **不得再发布过账事件** —— 否则总账会多出一张重复凭证。
     * <p>这是本守卫最关键的断言：原实现只要 actStatus=pass 就无条件发事件。</p>
     */
    @Test
    void afterFlowHandle_pass_onAlreadyIssued_doesNotPublishEventAgain() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0"));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> invoiceService.afterFlowHandle(flowBusiness(ActStatus.pass)));

        verify(applicationEventPublisher, never()).publishEvent(any(SoInvoiceIssuedEvent.class));
    }

    /** 已核销（VERIFIED）的发票不会被流程撤销回调改回 DRAFT */
    @Test
    void afterFlowHandle_doesNotRevertVerifiedInvoice() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_VERIFIED, "678.00", "678.00"));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> invoiceService.afterFlowHandle(flowBusiness(ActStatus.revoke)));

        verify(baseMapper, never()).updateById(any(SoInvoice.class));
        verify(applicationEventPublisher, never()).publishEvent(any(SoInvoiceIssuedEvent.class));
    }

    /** 未知流程动作不产生任何更新 */
    @Test
    void afterFlowHandle_unknownAction_doesNothing() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0"));

        invoiceService.afterFlowHandle(flowBusiness("未知动作"));

        verify(baseMapper, never()).update(any(), any());
    }

    private WfMyBusiness flowBusiness(String actStatus) {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("1");
        business.setActStatus(actStatus);
        return business;
    }

    // ==================== 表头金额服务端汇总（加固轮）====================

    /**
     * 手工建票：表头金额按明细汇总，客户端伪造的大额无效。
     * <p>为什么必须汇总：{@code verifyInvoice} 的核销上限就是 {@code totalAmount} ——
     * 把 totalAmount 改大，就能用一个远小于真实金额的收款把发票置为「已核销」。</p>
     */
    @Test
    void insertByBo_recomputesHeaderAmountsFromLines() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setTotalAmount(new BigDecimal("999999"));   // 伪造
        SoInvoiceLine line = invoiceLine(null, null, "6");
        line.setLineAmount(new BigDecimal("600.00"));
        line.setTaxAmount(new BigDecimal("78.00"));
        bo.setSoInvoiceLineList(List.of(line));

        invoiceService.insertByBo(bo);

        ArgumentCaptor<SoInvoice> captor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).insert(captor.capture());
        SoInvoice saved = captor.getValue();
        assertEquals(0, new BigDecimal("600.00").compareTo(saved.getAmount()));
        assertEquals(0, new BigDecimal("78.00").compareTo(saved.getTaxAmount()));
        assertEquals(0, new BigDecimal("678.00").compareTo(saved.getTotalAmount()));
    }

    /** 改票同样按明细汇总表头金额 */
    @Test
    void updateByBo_recomputesHeaderAmountsFromLines() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of());
        when(lineMapper.insert(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setInvoiceId(1L);
        bo.setTotalAmount(new BigDecimal("999999"));   // 伪造
        SoInvoiceLine line = invoiceLine(null, 100L, "6");
        line.setLineAmount(new BigDecimal("600.00"));
        line.setTaxAmount(new BigDecimal("78.00"));
        bo.setSoInvoiceLineList(List.of(line));

        invoiceService.updateByBo(bo);

        ArgumentCaptor<SoInvoice> captor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("678.00").compareTo(captor.getValue().getTotalAmount()));
    }

    /** 明细未提交时表头金额保持库中原值，客户端伪造无效 */
    @Test
    void updateByBo_nullLineList_keepsHeaderAmountsFromDb() {
        SoInvoice existing = invoice(1L, ISoInvoiceService.STATUS_DRAFT, "678.00", "0");
        existing.setAmount(new BigDecimal("600.00"));
        existing.setTaxAmount(new BigDecimal("78.00"));
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setInvoiceId(1L);
        bo.setTotalAmount(BigDecimal.ZERO);   // 伪造
        bo.setSoInvoiceLineList(null);

        invoiceService.updateByBo(bo);

        ArgumentCaptor<SoInvoice> captor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("678.00").compareTo(captor.getValue().getTotalAmount()));
        assertEquals(0, new BigDecimal("600.00").compareTo(captor.getValue().getAmount()));
    }

    // ==================== insertByBo 服务端权威字段（加固轮）====================

    /**
     * 手工建票：status 强制 DRAFT、verifiedAmount 强制清零、发票号留空自动生成。
     * <p>伪造 status=ISSUED 的后果：insertByBo 不发布 SoInvoiceIssuedEvent（只有 afterFlowHandle
     * 与 redFlush 发布），发票会标记「已开」却从未过账到总账。
     * 伪造 verifiedAmount 的后果：verifyInvoice 以「已核销额 + 本次」判断是否置为已核销，
     * 预置一个大值即可用很小金额把发票置为已核销。</p>
     */
    @Test
    void insertByBo_forcesDraftAndZeroesVerifiedAmount() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });

        SoInvoiceBo bo = new SoInvoiceBo();
        bo.setStatus(ISoInvoiceService.STATUS_ISSUED);            // 伪造
        bo.setVerifiedAmount(new BigDecimal("678.00"));           // 伪造
        bo.setSoInvoiceLineList(List.of());

        invoiceService.insertByBo(bo);

        ArgumentCaptor<SoInvoice> captor = ArgumentCaptor.forClass(SoInvoice.class);
        verify(baseMapper).insert(captor.capture());
        SoInvoice saved = captor.getValue();
        assertEquals(ISoInvoiceService.STATUS_DRAFT, saved.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getVerifiedAmount()));
        assertEquals("0", saved.getRedFlag());
        // 发票号留空时兜底生成（invoice_no 为 NOT NULL 且带唯一键，空串会让第二张手工票撞唯一键）
        assertNotNull(saved.getInvoiceNo());
    }

    /** 手工建票传负数量明细直接拒绝（负数量只应来自红冲，否则会走释放分支绕过红冲） */
    @Test
    void insertByBo_rejectsNegativeLineQuantity() {
        when(baseMapper.insert(any(SoInvoice.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoInvoice.class).setInvoiceId(1L);
            return 1;
        });

        SoInvoiceBo bo = new SoInvoiceBo();
        SoInvoiceLine line = new SoInvoiceLine();
        line.setSoLineId(100L);
        line.setQuantity(new BigDecimal("-6")); // 伪造红冲
        bo.setSoInvoiceLineList(List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> invoiceService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("不能为负数"), "实际: " + ex.getMessage());
        verify(soSalesOrderLineMapper, never()).update(any(), any());
    }

    /** 改票：数量增加只占用差额 */
    @Test
    void updateByBo_occupiesDeltaOnQtyIncrease() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "600.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of(invoiceLine(10L, 100L, "6")));
        when(lineMapper.updateById(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        invoiceService.updateByBo(invoiceBo(List.of(invoiceLine(10L, 100L, "8"))));

        assertTrue(capturedLineUpdateSqlSet().contains("+ 2"), "应只占用差额 2");
    }

    /** 改票：数量减少只释放差额 */
    @Test
    void updateByBo_releasesDeltaOnQtyDecrease() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "600.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of(invoiceLine(10L, 100L, "6")));
        when(lineMapper.updateById(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        invoiceService.updateByBo(invoiceBo(List.of(invoiceLine(10L, 100L, "4"))));

        assertTrue(capturedLineUpdateSqlSet().contains("+ -2"), "应释放差额 2（以负数形式冲减）");
    }

    /** 改票：删除明细释放其全部占用 */
    @Test
    void updateByBo_releasesOccupationOnLineDeletion() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "600.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of(invoiceLine(10L, 100L, "6")));
        when(lineMapper.deleteById(any(Serializable.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        invoiceService.updateByBo(invoiceBo(List.of()));

        verify(lineMapper).deleteById(10L);
        assertTrue(capturedLineUpdateSqlSet().contains("+ -6"), "应释放全部 6");
    }

    /** 改票：换绑订单行时先释放旧行再占用新行（两次调整） */
    @Test
    void updateByBo_rebindsToAnotherOrderLine() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_DRAFT, "600.00", "0"));
        when(baseMapper.updateById(any(SoInvoice.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of(invoiceLine(10L, 100L, "6")));
        when(lineMapper.updateById(any(SoInvoiceLine.class))).thenReturn(1);
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(1);

        invoiceService.updateByBo(invoiceBo(List.of(invoiceLine(10L, 200L, "6"))));

        verify(soSalesOrderLineMapper, times(2)).update(any(), any());
    }

    /** 作废历史票（从未占用过 invoiced_qty）不应因下界守卫而失败 */
    @Test
    void cancelInvoice_toleratesNothingToRelease() {
        when(baseMapper.selectById(1L)).thenReturn(invoice(1L, ISoInvoiceService.STATUS_ISSUED, "678.00", "0"));
        when(lineMapper.selectList(any())).thenReturn(List.of(invoiceLine(10L, 100L, "6")));
        // 释放命中下界守卫（未占用过）-> 影响 0 行
        when(soSalesOrderLineMapper.update(any(), any())).thenReturn(0);
        when(baseMapper.update(any(), any())).thenReturn(1);

        invoiceService.cancelInvoice(1L, "作废历史票");

        verify(baseMapper).update(any(), any());
    }

    // ==================== selectById 明细行转 VO（批次 5）====================

    /**
     * 详情返回明细行 VO 而非实体：不暴露 tenantId/delFlag 等审计字段，
     * 且**必须保留 soLineId** —— 它是回写 invoiced_qty 的关联键，
     * adjustInvoicedQtyOnEdit 靠它判断是否换绑订单行；VO 丢掉它会让编辑自动生成的
     * 草稿发票时被误判为换绑，先释放旧占用、再按 null 不占用，导致已开票量归零。
     */
    @Test
    void selectById_returnsLineVoKeepingSoLineId() {
        SoInvoiceVo header = new SoInvoiceVo();
        header.setInvoiceId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(header);

        SoInvoiceLine line = new SoInvoiceLine();
        line.setLineId(10L);
        line.setInvoiceId(1L);
        line.setLineNo("0010");
        line.setMaterialId(100L);
        line.setMaterialCode("M-001");
        line.setMaterialName("测试物料");
        line.setQuantity(new BigDecimal("6"));
        line.setUnit("件");
        line.setUnitPrice(new BigDecimal("100"));
        line.setLineAmount(new BigDecimal("600.00"));
        line.setTaxRate(new BigDecimal("13"));
        line.setTaxAmount(new BigDecimal("78.00"));
        line.setTotalAmount(new BigDecimal("678.00"));
        line.setSoLineId(100L);
        line.setContractLineId(200L);
        line.setSortOrder(1);
        line.setTenantId("888888");
        line.setDelFlag("1");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        SoInvoiceVo result = invoiceService.selectById(1L);

        assertEquals(1, result.getSoInvoiceLineList().size());
        SoInvoiceLineVo lineVo = result.getSoInvoiceLineList().get(0);
        // 关联键与业务字段一个都不能少
        assertEquals(100L, lineVo.getSoLineId());
        assertEquals(200L, lineVo.getContractLineId());
        assertEquals("M-001", lineVo.getMaterialCode());
        assertEquals("测试物料", lineVo.getMaterialName());
        assertEquals(0, new BigDecimal("6").compareTo(lineVo.getQuantity()));
        assertEquals("件", lineVo.getUnit());
        assertEquals(0, new BigDecimal("13").compareTo(lineVo.getTaxRate()));
        assertEquals(0, new BigDecimal("678.00").compareTo(lineVo.getTotalAmount()));
        assertEquals(1, lineVo.getSortOrder());
    }
}
