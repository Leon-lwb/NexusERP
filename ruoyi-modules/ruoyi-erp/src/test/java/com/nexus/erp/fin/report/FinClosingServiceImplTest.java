package com.nexus.erp.fin.report;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlCurrencyRate;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.mapper.GlAccountBalanceMapper;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlCurrencyRateMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import com.nexus.erp.fin.report.mapper.FinCloseLogMapper;
import com.nexus.erp.fin.report.service.impl.FinClosingServiceImpl;
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
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外币期末重估单元测试 —— 固化 FIN-GL-01 修复口径。
 *
 * <p><b>背景</b>：原实现聚合原币净额时只累加 {@code amount_currency}（不减贷方），
 * 贷方余额的外币科目（外币应付账款/预收账款）原币净额被算成正数，而本币净额是负数，
 * 于是重估差额 ≈ 2×|账面| 且方向相反 —— 汇兑损益虚增、外币科目被反向调整。</p>
 *
 * <p>本类断言贷方外币科目：账面本币 -1000、原币 140（贷）、期末汇率 7.30 时，
 * 原币净额必须为 -140、重估差额为 -22（外币科目贷 22、汇兑损益借 22）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class FinClosingServiceImplTest {

    @Mock
    private FinCloseLogMapper closeLogMapper;
    @Mock
    private GlAccountMapper accountMapper;
    @Mock
    private GlAccountBalanceMapper balanceMapper;
    @Mock
    private GlPeriodMapper periodMapper;
    @Mock
    private GlVoucherMapper voucherMapper;
    @Mock
    private GlCurrencyRateMapper currencyRateMapper;
    @Mock
    private IGlPeriodService periodService;
    @Mock
    private IGlVoucherService voucherService;

    @InjectMocks
    private FinClosingServiceImpl closingService;

    private static MockedStatic<SpringUtil> springUtilMock;
    private static MockedStatic<SpringUtils> springUtilsMock;
    private static MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void initStatics() {
        // IdGeneratorUtil 静态字段初始化需要 SpringUtils.getBean(IdentifierGenerator.class)
        // （该方法继承自 hutool SpringUtil，故 mock SpringUtil），单测无 Spring 上下文
        springUtilMock = mockStatic(SpringUtil.class);
        IdentifierGenerator idGenerator = mock(IdentifierGenerator.class);
        lenient().when(idGenerator.nextId(any())).thenReturn(12345L);
        lenient().when(SpringUtil.getBean(IdentifierGenerator.class)).thenReturn(idGenerator);

        // logSuccess/logInfo 经 SpringUtils.getAopProxy(this).log(...) 走 REQUIRES_NEW 事务，
        // 单测里让 getAopProxy 原样返回实例，直接落到 closeLogMapper 上
        springUtilsMock = mockStatic(SpringUtils.class);
        lenient().when(SpringUtils.getAopProxy(any())).thenAnswer(inv -> inv.getArgument(0));
        // log() 会取当前登录用户，单测无 Sa-Token 上下文
        loginHelperMock = mockStatic(LoginHelper.class);
        lenient().when(LoginHelper.getUserId()).thenReturn(1L);

        // 单测无 Spring，需手工注册实体列缓存（否则 lambda 包装器抛 can not find lambda cache）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, GlPeriod.class);
        TableInfoHelper.initTableInfo(assistant, GlVoucher.class);
        TableInfoHelper.initTableInfo(assistant, GlVoucherLine.class);
        TableInfoHelper.initTableInfo(assistant, GlCurrencyRate.class);
        TableInfoHelper.initTableInfo(assistant, GlAccount.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
    }

    @Test
    void revaluate_creditSideForeignCurrencyTakesNegativeSign() {
        Date end = new Date();
        GlPeriod period = new GlPeriod();
        period.setPeriodId(9L);
        period.setPeriodName("2026-01");
        period.setStatus(GlConstants.PERIOD_OPEN);
        period.setEndDate(end);
        when(periodMapper.selectById(9L)).thenReturn(period);
        // 本期尚未重估
        when(voucherMapper.selectOne(any())).thenReturn(null);

        GlVoucher posted = new GlVoucher();
        posted.setVoucherId(100L);
        when(voucherMapper.selectList(any())).thenReturn(List.of(posted));

        // 外币应付账款:贷方 1000 CNY / 原币 140 USD(只有贷方行)
        GlVoucherLine fxLine = new GlVoucherLine();
        fxLine.setAccountId(2202L);
        fxLine.setCredit(new BigDecimal("1000.00"));
        fxLine.setCurrency("USD");
        fxLine.setAmountCurrency(new BigDecimal("140.00"));
        GlVoucherVo postedVo = new GlVoucherVo();
        postedVo.setVoucherLineList(List.of(fxLine));
        when(voucherService.queryById(100L)).thenReturn(postedVo);

        GlCurrencyRate rate = new GlCurrencyRate();
        rate.setCurrency("USD");
        rate.setRate(new BigDecimal("7.30"));
        rate.setRateDate(end);
        when(currencyRateMapper.selectList(any())).thenReturn(List.of(rate));

        GlAccount fxAccount = new GlAccount();
        fxAccount.setAccountId(6603L);
        when(accountMapper.selectOne(any())).thenReturn(fxAccount);

        GlVoucherVo created = new GlVoucherVo();
        created.setVoucherId(200L);
        created.setVoucherNo("J-200");
        when(voucherService.createAndPost(any(VoucherBuildRequest.class))).thenReturn(created);
        when(periodMapper.update(any(), any())).thenReturn(1);

        closingService.revaluate(9L);

        ArgumentCaptor<VoucherBuildRequest> captor = ArgumentCaptor.forClass(VoucherBuildRequest.class);
        verify(voucherService).createAndPost(captor.capture());
        List<GlVoucherLine> lines = captor.getValue().getLines();
        GlVoucherLine accountLine = lines.stream()
            .filter(l -> Long.valueOf(2202L).equals(l.getAccountId())).findFirst().orElseThrow();
        GlVoucherLine pnlLine = lines.stream()
            .filter(l -> Long.valueOf(6603L).equals(l.getAccountId())).findFirst().orElseThrow();

        // 原币净额必须按贷方取负: -140(修复前为 +140)
        assertEquals(0, new BigDecimal("-140.00").compareTo(accountLine.getAmountCurrency()),
            "原币净额必须按借贷方向取负,实际: " + accountLine.getAmountCurrency());
        // 重估后原币 -140 × 7.30 = -1022,账面 -1000 → 差额 -22(补贷方 22,汇兑损益借 22)
        assertNull(accountLine.getDebit(), "贷方科目补差应在贷方");
        assertEquals(0, new BigDecimal("22.00").compareTo(accountLine.getCredit()),
            "贷方科目应补提贷方 22.00(修复前为借方 2022.00),实际: " + accountLine.getCredit());
        assertNull(pnlLine.getCredit());
        assertEquals(0, new BigDecimal("22.00").compareTo(pnlLine.getDebit()),
            "汇兑损益应在借方 22.00(修复前为贷方 2022.00),实际: " + pnlLine.getDebit());
    }
}
