package com.nexus.erp.fin.arap;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.FinSettlement;
import com.nexus.erp.fin.arap.mapper.FinReceiptMapper;
import com.nexus.erp.fin.arap.mapper.FinSettlementMapper;
import com.nexus.erp.fin.arap.service.impl.FinSettlementServiceImpl;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherLineMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.pu.invoice.mapper.PuInvoiceMapper;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 反核销（unsettle）单元测试 —— FIN-ARAP-02。
 *
 * <p>核销同时改了三处不可逆状态（fin_settlement 新行、发票 verified_amount/status、
 * gl_voucher_line.cleared），修复前没有任何撤销入口，误核销只能改库。本类断言反核销
 * 把三处状态与金额都退回核销前，且重复调用不会二次回滚。</p>
 *
 * <p>注：MyBatis-Plus 的 WHERE 条件参数是**惰性物化**的 —— 只有调用
 * {@code getSqlSegment()} 渲染 SQL 之后，{@code eq/ge} 的值才会进入
 * {@code paramNameValuePairs}，因此金额守卫断言先取 SQL 段再查参数表。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class FinSettlementServiceImplTest {

    @Mock
    private FinSettlementMapper baseMapper;
    @Mock
    private FinReceiptMapper receiptMapper;
    @Mock
    private ISoInvoiceService soInvoiceService;
    @Mock
    private IPuInvoiceService puInvoiceService;
    @Mock
    private SoInvoiceMapper soInvoiceMapper;
    @Mock
    private PuInvoiceMapper puInvoiceMapper;
    @Mock
    private GlVoucherMapper voucherMapper;
    @Mock
    private GlVoucherLineMapper voucherLineMapper;
    @Mock
    private GlAccountMapper accountMapper;

    @InjectMocks
    private FinSettlementServiceImpl settlementService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, FinSettlement.class);
        TableInfoHelper.initTableInfo(assistant, FinReceipt.class);
        TableInfoHelper.initTableInfo(assistant, SoInvoice.class);
        TableInfoHelper.initTableInfo(assistant, GlVoucherLine.class);
    }

    private FinSettlement settlement() {
        FinSettlement s = new FinSettlement();
        s.setSettlementId(5L);
        s.setDirection(ArapConstants.DIRECTION_AR);
        s.setInvoiceId(11L);
        s.setReceiptId(22L);
        s.setSettleAmount(new BigDecimal("100.00"));
        return s;
    }

    private SoInvoice verifiedInvoice() {
        SoInvoice inv = new SoInvoice();
        inv.setInvoiceId(11L);
        inv.setTotalAmount(new BigDecimal("100.00"));
        inv.setVerifiedAmount(new BigDecimal("100.00"));
        inv.setStatus(ISoInvoiceService.STATUS_VERIFIED);
        inv.setVersion(3);
        return inv;
    }

    private FinReceipt settledReceipt() {
        FinReceipt r = new FinReceipt();
        r.setReceiptId(22L);
        r.setAmount(new BigDecimal("100.00"));
        r.setSettledAmount(new BigDecimal("100.00"));
        r.setStatus(ArapConstants.RECEIPT_STATUS_SETTLED);
        r.setVersion(7);
        return r;
    }

    /** 反核销：发票回到 ISSUED 且已核销额 0、收款单回到 CONFIRMED 且已结算额 0、未清项复位、核销记录删除 */
    @Test
    void unsettle_rollsBackAmountsAndStatusesToPreSettleState() {
        when(baseMapper.selectById(5L)).thenReturn(settlement());
        when(soInvoiceMapper.selectById(11L)).thenReturn(verifiedInvoice());
        when(soInvoiceMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(receiptMapper.selectById(22L)).thenReturn(settledReceipt());
        when(receiptMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(voucherLineMapper.update(any(), any(Wrapper.class))).thenReturn(2);
        when(baseMapper.deleteById(5L)).thenReturn(1);

        settlementService.unsettle(5L);

        // 发票：verified_amount 减回 0，状态回 ISSUED，并带 verified_amount >= 100.00 守卫
        ArgumentCaptor<Wrapper<SoInvoice>> invCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(soInvoiceMapper).update(any(), invCaptor.capture());
        LambdaUpdateWrapper<SoInvoice> invWrapper = (LambdaUpdateWrapper<SoInvoice>) invCaptor.getValue();
        assertTrue(invWrapper.getSqlSet().contains("verified_amount = verified_amount - 100.00"),
            "实际 SET: " + invWrapper.getSqlSet());
        assertTrue(invWrapper.getParamNameValuePairs().containsValue(ISoInvoiceService.STATUS_ISSUED),
            "发票状态应回退为 ISSUED, 实际参数: " + invWrapper.getParamNameValuePairs());
        assertAmountGuard(invWrapper, "verified_amount", "100.00");

        // 收付款单：settled_amount 减回 0，状态回 CONFIRMED
        ArgumentCaptor<Wrapper<FinReceipt>> receiptCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(receiptMapper).update(any(), receiptCaptor.capture());
        LambdaUpdateWrapper<FinReceipt> receiptWrapper = (LambdaUpdateWrapper<FinReceipt>) receiptCaptor.getValue();
        assertTrue(receiptWrapper.getSqlSet().contains("settled_amount = settled_amount - 100.00"),
            "实际 SET: " + receiptWrapper.getSqlSet());
        assertTrue(receiptWrapper.getParamNameValuePairs().containsValue(ArapConstants.RECEIPT_STATUS_CONFIRMED),
            "收付款单状态应回退为 CONFIRMED, 实际参数: " + receiptWrapper.getParamNameValuePairs());
        assertAmountGuard(receiptWrapper, "settled_amount", "100.00");

        // 未清项复位
        ArgumentCaptor<Wrapper<GlVoucherLine>> lineCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(voucherLineMapper).update(any(), lineCaptor.capture());
        LambdaUpdateWrapper<GlVoucherLine> lineWrapper = (LambdaUpdateWrapper<GlVoucherLine>) lineCaptor.getValue();
        assertTrue(lineWrapper.getSqlSet().contains("cleared_by = NULL"), "实际 SET: " + lineWrapper.getSqlSet());
        assertTrue(lineWrapper.getParamNameValuePairs().containsValue("0"),
            "cleared 应复位为 0, 实际参数: " + lineWrapper.getParamNameValuePairs());

        // 核销记录逻辑删除
        verify(baseMapper).deleteById(5L);
    }

    /** 幂等：记录已反核销（查不到）时拒绝，不得二次回滚金额 */
    @Test
    void unsettle_rejectsAlreadyReversedSettlement() {
        when(baseMapper.selectById(5L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> settlementService.unsettle(5L));
        assertTrue(ex.getMessage().contains("不存在或已反核销"), "实际: " + ex.getMessage());
        verify(soInvoiceMapper, never()).update(any(), any(Wrapper.class));
        verify(receiptMapper, never()).update(any(), any(Wrapper.class));
        verify(baseMapper, never()).deleteById(any(Long.class));
    }

    /** 回退额超过发票已核销额时抛异常（条件更新前先做金额守卫） */
    @Test
    void unsettle_rejectsOverRollback() {
        FinSettlement s = settlement();
        s.setSettleAmount(new BigDecimal("150.00"));
        when(baseMapper.selectById(5L)).thenReturn(s);
        when(soInvoiceMapper.selectById(11L)).thenReturn(verifiedInvoice());

        ServiceException ex = assertThrows(ServiceException.class, () -> settlementService.unsettle(5L));
        assertTrue(ex.getMessage().contains("不足回退"), "实际: " + ex.getMessage());
        verify(soInvoiceMapper, never()).update(any(), any(Wrapper.class));
    }

    /** 条件更新必须带金额守卫（WHERE column >= settleAmount），防止并发下把已核销额冲成负数 */
    private static void assertAmountGuard(LambdaUpdateWrapper<?> wrapper, String column, String expected) {
        // 先渲染 WHERE 段，MyBatis-Plus 才会把 eq/ge 的条件值物化进 paramNameValuePairs
        String where = wrapper.getSqlSegment();
        assertTrue(where.contains(column + " >= "), "应带 " + column + " >= 守卫, 实际 WHERE: " + where);
        assertTrue(hasAmount(wrapper, expected),
            "守卫金额应为 " + expected + ", 实际参数: " + wrapper.getParamNameValuePairs());
    }

    /** 断言包装器参数表中存在指定数值（统一按数值比较，规避取值形态差异） */
    private static boolean hasAmount(Wrapper<?> wrapper, String expected) {
        BigDecimal target = new BigDecimal(expected);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values().stream()
            .anyMatch(v -> toDecimal(v) != null && toDecimal(v).compareTo(target) == 0);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString());
        }
        if (v instanceof CharSequence) {
            try {
                return new BigDecimal(v.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
