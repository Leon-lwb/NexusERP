package com.nexus.erp.fin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherLineMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.fin.gl.service.IGlAccountBalanceService;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import com.nexus.erp.fin.gl.service.impl.GlVoucherServiceImpl;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GL 凭证引擎单元测试 —— 聚焦「红字/红冲凭证的负数分录」这一 Q7 修复。
 *
 * <p><b>背景</b>：{@code validateBalanced} 原实现对**所有**路径一律拒绝负数分录，
 * 而 {@code SoInvoicePoster} 处理红字发票时生成的借贷金额全为负（注释声称
 * 「红字发票自动生成红字凭证，借贷方向不变、金额取负」）——两者直接矛盾，
 * 导致红冲的过账消息必然抛异常、重试 3 次后进死信，**账务冲销实际从未生效**。</p>
 *
 * <p>本类固化修复后的口径：</p>
 * <ul>
 *   <li>业务来源（{@code createAndPost}，非 MANUAL）允许负数分录；</li>
 *   <li>手工来源（{@code insertByBo}/{@code updateByBo}）仍拒绝负数分录，保持防呆；</li>
 *   <li>{@code post()} 按凭证自身来源判定，红字凭证反过账后仍可再过账；</li>
 *   <li>反过账/作废已过账凭证只允许 OPEN 期间（FIN-GL-02）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class GlVoucherServiceImplTest {

    @Mock
    private GlVoucherMapper baseMapper;
    @Mock
    private GlVoucherLineMapper lineMapper;
    @Mock
    private GlAccountMapper accountMapper;
    @Mock
    private GlPeriodMapper periodMapper;
    @Mock
    private IGlPeriodService periodService;
    @Mock
    private IGlAccountBalanceService balanceService;

    @InjectMocks
    private GlVoucherServiceImpl voucherService;

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
        TableInfoHelper.initTableInfo(assistant, GlVoucher.class);
        TableInfoHelper.initTableInfo(assistant, GlVoucherLine.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    /** 末级科目（validateBalanced 要求 is_leaf=1） */
    private GlAccount leafAccount(Long id) {
        GlAccount acc = new GlAccount();
        acc.setAccountId(id);
        acc.setAccountCode("6001");
        acc.setAccountName("主营业务收入");
        acc.setIsLeaf(GlConstants.YES);
        return acc;
    }

    private GlVoucherLine line(Long accountId, String debit, String credit) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit == null ? null : new BigDecimal(debit));
        l.setCredit(credit == null ? null : new BigDecimal(credit));
        return l;
    }

    private VoucherBuildRequest request(String source, List<GlVoucherLine> lines) {
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherSource(source);
        req.setSourceId(100L);
        req.setSourceNo("SO-INV-1");
        req.setVoucherWord("J");
        req.setSummary("销售发票测试");
        req.setLines(lines);
        return req;
    }

    private void stubOpenPeriodAndAccounts() {
        GlPeriod period = new GlPeriod();
        period.setPeriodId(9L);
        period.setStatus(GlConstants.PERIOD_OPEN);
        lenient().when(periodService.getOrDerivePeriod(any())).thenReturn(period);
        lenient().when(accountMapper.selectById(anyLong())).thenReturn(leafAccount(1L));
        lenient().when(baseMapper.selectOne(any())).thenReturn(null);
        lenient().when(baseMapper.insert(any(GlVoucher.class))).thenReturn(1);
        lenient().when(lineMapper.insert(any(GlVoucherLine.class))).thenReturn(1);
        GlVoucher persisted = new GlVoucher();
        persisted.setVoucherId(7L);
        lenient().when(baseMapper.selectVoById(anyLong())).thenReturn(null);
        lenient().when(lineMapper.selectList(any())).thenReturn(List.of());
    }

    /**
     * <b>Q7 核心</b>：红字发票的全负数凭证（借 -678 / 贷 -600 / 贷 -78）必须能过账。
     * <p>修复前这里会抛「分录行金额不能为负数」，红冲的账务冲销从未生效。</p>
     */
    @Test
    void createAndPost_allowsNegativeAmountsForBusinessPoster() {
        stubOpenPeriodAndAccounts();
        VoucherBuildRequest req = request(GlConstants.SOURCE_SO_INVOICE, List.of(
            line(1L, "-678.00", null),
            line(1L, null, "-600.00"),
            line(1L, null, "-78.00")));

        voucherService.createAndPost(req);

        // 借贷各 -678，平衡成立；凭证落库
        ArgumentCaptor<GlVoucher> captor = ArgumentCaptor.forClass(GlVoucher.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("-678.00").compareTo(captor.getValue().getTotalDebit()));
        assertEquals(0, new BigDecimal("-678.00").compareTo(captor.getValue().getTotalCredit()));
        verify(balanceService).applyPosting(anyLong(), any());
    }

    /** 正数业务凭证（蓝字发票）行为不变，仍然过账 */
    @Test
    void createAndPost_allowsPositiveAmounts() {
        stubOpenPeriodAndAccounts();
        VoucherBuildRequest req = request(GlConstants.SOURCE_SO_INVOICE, List.of(
            line(1L, "678.00", null),
            line(1L, null, "600.00"),
            line(1L, null, "78.00")));

        voucherService.createAndPost(req);

        verify(baseMapper).insert(any(GlVoucher.class));
    }

    /**
     * 手工凭证（MANUAL）仍拒绝负数分录 —— 防呆不能被业务放行顺手打开。
     * <p>{@code createAndPost} 对 MANUAL 走的是手工口径。</p>
     */
    @Test
    void createAndPost_rejectsNegativeAmountsForManualSource() {
        stubOpenPeriodAndAccounts();
        VoucherBuildRequest req = request(GlConstants.SOURCE_MANUAL, List.of(
            line(1L, "-100.00", null),
            line(1L, null, "-100.00")));

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.createAndPost(req));
        assertTrue(ex.getMessage().contains("不能为负数"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(GlVoucher.class));
    }

    /** 借贷不平衡仍然拒绝（负数不影响平衡校验） */
    @Test
    void createAndPost_rejectsUnbalancedNegativeVoucher() {
        stubOpenPeriodAndAccounts();
        VoucherBuildRequest req = request(GlConstants.SOURCE_SO_INVOICE, List.of(
            line(1L, "-678.00", null),
            line(1L, null, "-600.00")));

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.createAndPost(req));
        assertTrue(ex.getMessage().contains("借贷不平衡"), "实际: " + ex.getMessage());
    }

    /** 借贷同时有值仍然拒绝 */
    @Test
    void createAndPost_rejectsBothSidesFilled() {
        stubOpenPeriodAndAccounts();
        VoucherBuildRequest req = request(GlConstants.SOURCE_SO_INVOICE, List.of(
            line(1L, "-100.00", "-100.00")));

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.createAndPost(req));
        assertTrue(ex.getMessage().contains("借贷不可同时有值"), "实际: " + ex.getMessage());
    }

    /**
     * 手工建凭证（insertByBo）拒绝负数分录。
     * <p>走的是 {@code validateBalanced(lines)} 无参重载（allowNegative=false）。</p>
     */
    @Test
    void insertByBo_rejectsNegativeAmounts() {
        GlVoucherBoHolder holder = new GlVoucherBoHolder();
        com.nexus.erp.fin.gl.domain.bo.GlVoucherBo bo = holder.bo(List.of(
            line(1L, "-100.00", null),
            line(1L, null, "-100.00")));
        // 注：负数校验在查科目之前就抛异常，无需 stub accountMapper

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("不能为负数"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(GlVoucher.class));
    }

    /**
     * 反过账后的红字凭证必须能再次过账（{@code post()} 按凭证自身来源判定）。
     * <p>否则红字凭证一旦 unpost 就永远无法 post，形成死单。</p>
     */
    @Test
    void post_allowsNegativeAmountsForBusinessSourcedVoucher() {
        GlVoucher red = new GlVoucher();
        red.setVoucherId(7L);
        red.setStatus(GlConstants.VOUCHER_STATUS_DRAFT);
        red.setVoucherSource(GlConstants.SOURCE_SO_INVOICE);
        red.setVersion(1);
        red.setVoucherDate(new java.util.Date());
        when(baseMapper.selectById(7L)).thenReturn(red);
        when(lineMapper.selectList(any())).thenReturn(List.of(
            line(1L, "-678.00", null),
            line(1L, null, "-600.00"),
            line(1L, null, "-78.00")));
        GlPeriod period = new GlPeriod();
        period.setPeriodId(9L);
        period.setStatus(GlConstants.PERIOD_OPEN);
        when(periodService.getOrDerivePeriod(any())).thenReturn(period);
        when(accountMapper.selectById(anyLong())).thenReturn(leafAccount(1L));
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        voucherService.post(7L);

        verify(baseMapper).update(any(), any(Wrapper.class));
        verify(balanceService).applyPosting(anyLong(), any());
    }

    /** 手工来源的负数凭证不允许过账（与手工录入口径一致） */
    @Test
    void post_rejectsNegativeAmountsForManualVoucher() {
        GlVoucher manual = new GlVoucher();
        manual.setVoucherId(7L);
        manual.setStatus(GlConstants.VOUCHER_STATUS_DRAFT);
        manual.setVoucherSource(GlConstants.SOURCE_MANUAL);
        manual.setVersion(1);
        manual.setVoucherDate(new java.util.Date());
        when(baseMapper.selectById(7L)).thenReturn(manual);
        when(lineMapper.selectList(any())).thenReturn(List.of(
            line(1L, "-100.00", null),
            line(1L, null, "-100.00")));
        // 注：负数校验在查科目之前就抛异常，无需 stub accountMapper

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.post(7L));
        assertTrue(ex.getMessage().contains("不能为负数"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
    }

    /**
     * <b>FIN-GL-02 核心</b>：已关账(CLOSED)期间的凭证不可反过账。
     * <p>修复前只拒 LOCKED，CLOSED 期间反过账会回冲余额并把凭证置 DRAFT，
     * 而 {@code post()} 又要求 OPEN，凭证永久卡死且关账后报表数字被静默改变。</p>
     */
    @Test
    void unpost_rejectsClosedPeriod() {
        GlVoucher posted = new GlVoucher();
        posted.setVoucherId(7L);
        posted.setStatus(GlConstants.VOUCHER_STATUS_POSTED);
        posted.setVersion(1);
        posted.setPeriodId(9L);
        when(baseMapper.selectById(7L)).thenReturn(posted);
        GlPeriod closed = new GlPeriod();
        closed.setPeriodId(9L);
        closed.setPeriodName("2026-01");
        closed.setStatus(GlConstants.PERIOD_CLOSED);
        when(periodMapper.selectById(9L)).thenReturn(closed);

        ServiceException ex = assertThrows(ServiceException.class, () -> voucherService.unpost(7L));
        assertTrue(ex.getMessage().contains("不可反过账"), "实际: " + ex.getMessage());
        verify(balanceService, never()).reversePosting(anyLong(), any());
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
    }

    /** OPEN 期间反过账照常执行（反过账 -> 再过账 闭环不被破坏） */
    @Test
    void unpost_allowsOpenPeriod() {
        GlVoucher posted = new GlVoucher();
        posted.setVoucherId(7L);
        posted.setStatus(GlConstants.VOUCHER_STATUS_POSTED);
        posted.setVersion(1);
        posted.setPeriodId(9L);
        when(baseMapper.selectById(7L)).thenReturn(posted);
        GlPeriod open = new GlPeriod();
        open.setPeriodId(9L);
        open.setPeriodName("2026-01");
        open.setStatus(GlConstants.PERIOD_OPEN);
        when(periodMapper.selectById(9L)).thenReturn(open);
        when(lineMapper.selectList(any())).thenReturn(List.of(line(1L, "100.00", null)));
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        voucherService.unpost(7L);

        verify(balanceService).reversePosting(anyLong(), any());
        verify(baseMapper).update(any(), any(Wrapper.class));
    }

    /** 便于构造 GlVoucherBo 的小工具（避免测试类里到处 new） */
    private static final class GlVoucherBoHolder {
        com.nexus.erp.fin.gl.domain.bo.GlVoucherBo bo(List<GlVoucherLine> lines) {
            com.nexus.erp.fin.gl.domain.bo.GlVoucherBo b = new com.nexus.erp.fin.gl.domain.bo.GlVoucherBo();
            b.setVoucherLineList(lines);
            b.setVoucherWord("J");
            b.setVoucherDate(new java.util.Date());
            return b;
        }
    }
}
