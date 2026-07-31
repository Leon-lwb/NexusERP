package com.nexus.erp.fin.report.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
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
import com.nexus.erp.fin.report.constant.ReportConstants;
import com.nexus.erp.fin.report.domain.FinCloseLog;
import com.nexus.erp.fin.report.domain.vo.ClosingStepVo;
import com.nexus.erp.fin.report.domain.vo.FinCloseLogVo;
import com.nexus.erp.fin.report.mapper.FinCloseLogMapper;
import com.nexus.erp.fin.report.service.IFinClosingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 期末关账 Service 实现(损益结转/外币重估/月结年结向导)
 * <p>结转与重估均复用 GlVoucherService.createAndPost 幂等制单;步骤独立事务+日志表,失败可重跑。</p>
 *
 * @author nexus
 * @date 2026-07-31
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FinClosingServiceImpl implements IFinClosingService {

    private final FinCloseLogMapper closeLogMapper;
    private final GlAccountMapper accountMapper;
    private final GlAccountBalanceMapper balanceMapper;
    private final GlPeriodMapper periodMapper;
    private final GlVoucherMapper voucherMapper;
    private final GlCurrencyRateMapper currencyRateMapper;
    private final IGlPeriodService periodService;
    private final IGlVoucherService voucherService;

    /** 试算校验:当期借贷合计相等 + 无草稿凭证 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean trialCheck(Long periodId) {
        try {
            GlPeriod period = getPeriod(periodId);
            boolean hasDraft = voucherMapper.exists(new LambdaQueryWrapper<GlVoucher>()
                .eq(GlVoucher::getPeriodId, periodId)
                .eq(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_DRAFT));
            if (hasDraft) {
                throw new ServiceException("该期间存在草稿凭证,请先过账或作废");
            }
            // 借贷合计校验:读该期间全部余额行,期初+发生=期末,且借贷总额相等
            List<GlAccountBalance> balances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getPeriodId, periodId));
            BigDecimal dr = BigDecimal.ZERO;
            BigDecimal cr = BigDecimal.ZERO;
            for (GlAccountBalance b : balances) {
                // 期末借/贷 = (期初+本期) 净额按方向拆分(与试算平衡表同构)
                BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                    .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
                if (net.signum() >= 0) {
                    dr = dr.add(net);
                } else {
                    cr = cr.add(net.negate());
                }
            }
            if (dr.compareTo(cr) != 0) {
                throw new ServiceException("试算不平衡:期末借方合计 {} 贷方合计 {},请核查凭证", dr, cr);
            }
            logSuccess(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_TRIAL_CHECK,
                "试算校验通过:借方合计 " + dr.toPlainString() + " = 贷方合计 " + cr.toPlainString());
            return true;
        } catch (Exception e) {
            logFailed(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_TRIAL_CHECK, e.getMessage());
            throw e;
        }
    }

    /** 损益结转:损益类科目余额 -> 本年利润 4103,createAndPost 幂等 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long carryPnl(Long periodId) {
        try {
            GlPeriod period = getPeriod(periodId);
            Long sourceId = periodId * 100 + ReportConstants.IDEMPOTENT_CARRY_PL;
            // 幂等:已结转返回既有凭证
            GlVoucher existing = findVoucher(ReportConstants.VOUCHER_SOURCE, sourceId);
            if (existing != null) {
                updatePeriodClosing(periodId, ReportConstants.CLOSING_CARRY, existing.getVoucherId());
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CARRY_PL,
                    "损益已结转,复用凭证 " + existing.getVoucherNo());
                return existing.getVoucherId();
            }
            List<GlAccountBalance> balances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getPeriodId, periodId));
            if (CollUtil.isEmpty(balances)) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CARRY_PL, "无损益数据,跳过");
                return null;
            }
            // 损益科目余额(按 balance_dir 取净额)
            BigDecimal incomeNet = BigDecimal.ZERO;   // 收入类净额(贷方)
            BigDecimal expenseNet = BigDecimal.ZERO;  // 费用类净额(借方)
            List<GlVoucherLine> lines = new ArrayList<>();
            Map<Long, GlAccount> accountMap = loadAccounts(balances);
            BigDecimal total = BigDecimal.ZERO;
            for (GlAccountBalance b : balances) {
                GlAccount a = accountMap.get(b.getAccountId());
                if (a == null) {
                    continue;
                }
                boolean isIncome = "INCOME".equals(a.getAccountType());
                boolean isExpense = "EXPENSE".equals(a.getAccountType());
                if (!isIncome && !isExpense) {
                    continue;
                }
                // 期末净额=(期初借方-期初贷方)+(本期借方-本期贷方),与试算平衡表同构;贷方科目取反
                BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                    .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
                if ("CREDIT".equals(a.getBalanceDir())) {
                    net = net.negate(); // 收入类:贷方余额为正
                }
                if (net.signum() == 0) {
                    continue;
                }
                if (isIncome) {
                    // 借收入(净额)/ 贷本年利润
                    lines.add(line(a.getAccountId(), net, null, "结转 " + a.getAccountName()));
                } else {
                    // 借本年利润 / 贷费用(净额)
                    lines.add(line(a.getAccountId(), null, net, "结转 " + a.getAccountName()));
                }
                total = total.add(net.abs());
            }
            if (CollUtil.isEmpty(lines)) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CARRY_PL, "本期无损益科目余额,跳过结转");
                return null;
            }
            Long yearProfitId = getAccountId(ReportConstants.ACCT_YEAR_PROFIT);
            if (yearProfitId == null) {
                throw new ServiceException("本年利润科目(4103)不存在,请检查科目表");
            }
            // 借贷合计与损益净额:收入行是借方(贷本年利润),费用行是贷方(借本年利润)
            BigDecimal incomeTotal = BigDecimal.ZERO;
            BigDecimal expenseTotal = BigDecimal.ZERO;
            for (GlVoucherLine l : lines) {
                incomeTotal = incomeTotal.add(nz(l.getDebit()));
                expenseTotal = expenseTotal.add(nz(l.getCredit()));
            }
            BigDecimal yearProfitAmount = incomeTotal.subtract(expenseTotal);
            if (yearProfitAmount.signum() >= 0) {
                lines.add(line(yearProfitId, null, yearProfitAmount, "本期损益结转"));
            } else {
                lines.add(line(yearProfitId, yearProfitAmount.negate(), null, "本期损益结转"));
            }
            GlVoucherVo vo = createAdjustVoucher(period, sourceId, "期间损益结转 " + period.getPeriodName(), lines);
            // 更新期间进度
            updatePeriodClosing(periodId, ReportConstants.CLOSING_CARRY, vo == null ? null : vo.getVoucherId());
            logSuccess(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CARRY_PL,
                "损益结转完成,凭证 " + (vo == null ? "无" : vo.getVoucherNo()));
            return vo == null ? null : vo.getVoucherId();
        } catch (Exception e) {
            logFailed(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CARRY_PL, e.getMessage());
            throw e;
        }
    }

    /** 外币期末重估:外币科目按期末汇率重估,差额入 6603.02 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long revaluate(Long periodId) {
        try {
            GlPeriod period = getPeriod(periodId);
            Long sourceId = periodId * 100 + ReportConstants.IDEMPOTENT_REVALUATION;
            GlVoucher existing = findVoucher(ReportConstants.VOUCHER_SOURCE, sourceId);
            if (existing != null) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION,
                    "外币已重估,复用凭证 " + existing.getVoucherNo());
                return existing.getVoucherId();
            }
            // 取该期间外币凭证行(currency != CNY 且金额非零)
            List<GlVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<GlVoucher>()
                .eq(GlVoucher::getPeriodId, periodId)
                .eq(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_POSTED));
            if (CollUtil.isEmpty(vouchers)) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION, "本期无凭证,跳过重估");
                return null;
            }
            List<Long> voucherIds = vouchers.stream().map(GlVoucher::getVoucherId).collect(Collectors.toList());
            List<GlVoucherLine> fxCandidates = voucherLineList(voucherIds);
            if (CollUtil.isEmpty(fxCandidates)) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION, "本期无凭证行,跳过重估");
                return null;
            }
            // 按 accountId+currency 聚合外币净额(原币金额 amount_currency)
            Map<String, BigDecimal[]> fxAgg = new HashMap<>(); // key: accountId+currency -> [本币净额, 原币净额]
            for (GlVoucherLine l : fxCandidates) {
                if (l.getCurrency() == null || ReportConstants.BASE_CURRENCY.equals(l.getCurrency())) {
                    continue;
                }
                BigDecimal[] dc = fxAgg.computeIfAbsent(l.getAccountId() + "|" + l.getCurrency(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                dc[0] = dc[0].add(nz(l.getDebit())).subtract(nz(l.getCredit()));
                dc[1] = dc[1].add(nz(l.getAmountCurrency()));
            }
            List<GlVoucherLine> lines = new ArrayList<>();
            BigDecimal fxDiff = BigDecimal.ZERO;
            BigDecimal totalDiff = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal[]> e : fxAgg.entrySet()) {
                String[] key = e.getKey().split("\\|");
                Long accountId = Long.valueOf(key[0]);
                String currency = key[1];
                BigDecimal[] dc = e.getValue();
                BigDecimal bookNet = dc[0];        // 本币账面净额
                BigDecimal fxNet = dc[1];          // 原币净额
                // 期末汇率:取期间最后一天或最近日期
                GlCurrencyRate rate = findRate(currency, period.getEndDate());
                if (rate == null) {
                    logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION,
                        "币种 " + currency + " 无期末汇率,跳过该币种");
                    continue;
                }
                BigDecimal revalNet = fxNet.multiply(rate.getRate());
                BigDecimal diff = revalNet.subtract(bookNet);
                if (diff.signum() == 0) {
                    continue;
                }
                // 借/贷原外币科目(补差),反方向 6603.02 汇兑损益
                if (diff.signum() > 0) {
                    lines.add(line(accountId, diff, null, "期末重估 " + currency));
                    lines.add(line(getAccountId(ReportConstants.ACCT_EXCHANGE_GAIN_LOSS), null, diff, "汇兑损益 " + currency));
                } else {
                    lines.add(line(accountId, null, diff.negate(), "期末重估 " + currency));
                    lines.add(line(getAccountId(ReportConstants.ACCT_EXCHANGE_GAIN_LOSS), diff.negate(), null, "汇兑损益 " + currency));
                }
                totalDiff = totalDiff.add(diff.abs());
            }
            if (CollUtil.isEmpty(lines)) {
                logInfo(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION, "无重估差额,跳过");
                return null;
            }
            Long fxAccountId = getAccountId(ReportConstants.ACCT_EXCHANGE_GAIN_LOSS);
            if (fxAccountId == null) {
                throw new ServiceException("汇兑损益科目(6603.02)不存在,请检查科目表");
            }
            GlVoucherVo vo = createAdjustVoucher(period, sourceId, "外币期末重估 " + period.getPeriodName(), lines);
            updatePeriodClosing(periodId, ReportConstants.CLOSING_REVAL, vo == null ? null : vo.getVoucherId());
            logSuccess(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION,
                "外币重估完成,凭证 " + (vo == null ? "无" : vo.getVoucherNo()));
            return vo == null ? null : vo.getVoucherId();
        } catch (Exception e) {
            logFailed(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_REVALUATION, e.getMessage());
            throw e;
        }
    }

    /** 月结整体执行:试算校验 -> 损益结转 -> 外币重估 -> 关账 -> 期末余额结转下期期初 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean monthlyClosing(Long periodId) {
        trialCheck(periodId);
        carryPnl(periodId);
        revaluate(periodId);
        closePeriodInternal(periodId);
        // 期末余额结转为下期期初(报表跨期连续;12 月结转次年 1 月)
        carryForwardNext(periodId);
        return true;
    }

    /** 年结:12 月月结 -> 本年利润转未分配利润 -> 期初结转次年 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean yearlyClosing(Integer fiscalYear) {
        // 校验 12 月期间存在
        GlPeriod dec = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, fiscalYear)
            .eq(GlPeriod::getPeriodNo, 12));
        if (dec == null) {
            throw new ServiceException("会计年度 " + fiscalYear + " 的 12 月期间不存在,请先生成");
        }
        // 12 月月结(幂等:各步骤已有凭证则复用;已关账/锁定跳过)
        if (!GlConstants.PERIOD_CLOSED.equals(dec.getStatus()) && !GlConstants.PERIOD_LOCKED.equals(dec.getStatus())) {
            monthlyClosing(dec.getPeriodId());
        }
        // 本年利润 -> 未分配利润(凭证落次年 1 月:12 月已锁定,且该结转属次年业务)
        Long sourceId = dec.getPeriodId() * 100 + ReportConstants.IDEMPOTENT_YEARLY_PL;
        GlVoucher existing = findVoucher(ReportConstants.VOUCHER_SOURCE, sourceId);
        if (existing == null) {
            Long yearProfitId = getAccountId(ReportConstants.ACCT_YEAR_PROFIT);
            Long retainedId = getAccountId(ReportConstants.ACCT_RETAINED_EARNINGS);
            if (yearProfitId == null || retainedId == null) {
                throw new ServiceException("本年利润(4103)/未分配利润(4104)科目缺失");
            }
            // 4103 全年余额 = 全年各期(期初+发生)合计净额(月结已逐月结转,12月期末即全年累计)
            BigDecimal yearProfit = sumYearNet(dec, yearProfitId);
            if (yearProfit.signum() != 0) {
                List<GlVoucherLine> lines = new ArrayList<>();
                if (yearProfit.signum() > 0) {
                    // 借本年利润 / 贷未分配利润
                    lines.add(line(yearProfitId, yearProfit, null, "年结转 4103->4104"));
                    lines.add(line(retainedId, null, yearProfit, "年结转 4103->4104"));
                } else {
                    lines.add(line(yearProfitId, null, yearProfit.negate(), "年结转 4103->4104"));
                    lines.add(line(retainedId, yearProfit.negate(), null, "年结转 4103->4104"));
                }
                createAdjustVoucherYear(dec, sourceId, "年度损益结转 " + fiscalYear, lines);
            }
        }
        // 期初结转:全年余额聚合 -> 次年 1 月期初(资产/负债/权益;逐月月结时目标期已有 opening 自动跳过)
        GlPeriod nextJan = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, fiscalYear + 1)
            .eq(GlPeriod::getPeriodNo, 1));
        if (nextJan == null) {
            periodService.generateYearPeriods(fiscalYear + 1);
            nextJan = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
                .eq(GlPeriod::getFiscalYear, fiscalYear + 1)
                .eq(GlPeriod::getPeriodNo, 1));
        }
        carryForwardYear(fiscalYear, nextJan.getPeriodId());
        // 12 月锁定
        if (!GlConstants.PERIOD_LOCKED.equals(dec.getStatus())) {
            periodService.lockPeriod(dec.getPeriodId());
        }
        logSuccess(dec.getPeriodId(), ReportConstants.CLOSE_YEARLY, ReportConstants.STEP_CARRY_FORWARD,
            "年结完成:损益结转 + 期初结转至 " + (fiscalYear + 1) + " 年");
        return true;
    }

    /** 月结后:期末余额结转至下一期间期初(1-11 月转下月,12 月转次年 1 月;不存在则自动生成) */
    private void carryForwardNext(Long periodId) {
        GlPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            return;
        }
        int nextYear = period.getFiscalYear();
        int nextNo = period.getPeriodNo() + 1;
        if (nextNo > 12) {
            nextYear++;
            nextNo = 1;
        }
        GlPeriod next = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, nextYear)
            .eq(GlPeriod::getPeriodNo, nextNo));
        if (next == null) {
            periodService.generateYearPeriods(nextYear);
            next = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
                .eq(GlPeriod::getFiscalYear, nextYear)
                .eq(GlPeriod::getPeriodNo, nextNo));
        }
        if (next != null) {
            carryForward(periodId, next.getPeriodId());
        }
    }

    /** 期初结转:某期间 closing -> 下期 opening(幂等:目标期间 opening 非零即跳过) */
    @Transactional(rollbackFor = Exception.class)
    public void carryForward(Long fromPeriodId, Long toPeriodId) {        List<GlAccountBalance> fromBalances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .eq(GlAccountBalance::getPeriodId, fromPeriodId));
        if (CollUtil.isEmpty(fromBalances)) {
            return;
        }
        for (GlAccountBalance b : fromBalances) {
            // 结转净额=(期初借方-期初贷方)+(本期借方-本期贷方),与试算平衡表同构
            BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
            BigDecimal dr = net.signum() >= 0 ? net : BigDecimal.ZERO;
            BigDecimal cr = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
            if (dr.signum() == 0 && cr.signum() == 0) {
                continue;
            }
            GlAccountBalance target = balanceMapper.selectOne(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getPeriodId, toPeriodId)
                .eq(GlAccountBalance::getAccountId, b.getAccountId()));
            if (target != null && (nz(target.getOpeningDebit()).signum() != 0 || nz(target.getOpeningCredit()).signum() != 0)) {
                continue; // 已结转
            }
            if (target == null) {
                target = new GlAccountBalance();
                target.setBalanceId(IdGeneratorUtil.nextLongId());
                target.setPeriodId(toPeriodId);
                target.setAccountId(b.getAccountId());
                target.setVersion(0);
                balanceMapper.insert(target);
            }
            balanceMapper.update(null, Wrappers.<GlAccountBalance>lambdaUpdate()
                .eq(GlAccountBalance::getBalanceId, target.getBalanceId())
                .setSql("opening_debit = opening_debit + " + dr.toPlainString())
                .setSql("opening_credit = opening_credit + " + cr.toPlainString())
                .setSql("year_opening_debit = year_opening_debit + " + dr.toPlainString())
                .setSql("year_opening_credit = year_opening_credit + " + cr.toPlainString()));
        }
    }

    /** 某科目全年净额:年度内各期(期初+发生)合计(借正贷负),与试算平衡表同构 */
    private BigDecimal sumYearNet(GlPeriod anchor, Long accountId) {
        List<GlPeriod> yearPeriods = periodMapper.selectList(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, anchor.getFiscalYear())
            .le(GlPeriod::getPeriodNo, anchor.getPeriodNo()));
        if (CollUtil.isEmpty(yearPeriods)) {
            return BigDecimal.ZERO;
        }
        List<Long> periodIds = yearPeriods.stream().map(GlPeriod::getPeriodId).collect(Collectors.toList());
        List<GlAccountBalance> balances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .in(GlAccountBalance::getPeriodId, periodIds)
            .eq(GlAccountBalance::getAccountId, accountId));
        BigDecimal net = BigDecimal.ZERO;
        for (GlAccountBalance b : balances) {
            net = net.add(nz(b.getOpeningDebit())).subtract(nz(b.getOpeningCredit()))
                .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
        }
        return net;
    }

    /** 年结期初结转:年度各期(期初+发生)聚合净额 -> 次年 opening(资产/负债/权益;损益类已结转) */
    private void carryForwardYear(Integer fiscalYear, Long toPeriodId) {
        List<GlPeriod> yearPeriods = periodMapper.selectList(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, fiscalYear));
        if (CollUtil.isEmpty(yearPeriods)) {
            return;
        }
        List<Long> periodIds = yearPeriods.stream().map(GlPeriod::getPeriodId).collect(Collectors.toList());
        List<GlAccountBalance> yearBalances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .in(GlAccountBalance::getPeriodId, periodIds));
        if (CollUtil.isEmpty(yearBalances)) {
            return;
        }
        Map<Long, BigDecimal> nets = new HashMap<>();
        Map<Long, GlAccount> accounts = loadAccounts(yearBalances);
        for (GlAccountBalance b : yearBalances) {
            GlAccount a = accounts.get(b.getAccountId());
            if (a == null) {
                continue;
            }
            // 仅结转资产/负债/权益类(损益类已结转至 4103)
            if (!"ASSET".equals(a.getAccountType()) && !"LIABILITY".equals(a.getAccountType())
                && !"EQUITY".equals(a.getAccountType())) {
                continue;
            }
            BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
            nets.merge(b.getAccountId(), net, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : nets.entrySet()) {
            BigDecimal net = e.getValue();
            if (net.signum() == 0) {
                continue;
            }
            BigDecimal dr = net.signum() >= 0 ? net : BigDecimal.ZERO;
            BigDecimal cr = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
            GlAccountBalance target = balanceMapper.selectOne(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getPeriodId, toPeriodId)
                .eq(GlAccountBalance::getAccountId, e.getKey()));
            if (target != null && (nz(target.getOpeningDebit()).signum() != 0 || nz(target.getOpeningCredit()).signum() != 0)) {
                continue; // 已结转
            }
            if (target == null) {
                target = new GlAccountBalance();
                target.setBalanceId(IdGeneratorUtil.nextLongId());
                target.setPeriodId(toPeriodId);
                target.setAccountId(e.getKey());
                target.setVersion(0);
                balanceMapper.insert(target);
            }
            balanceMapper.update(null, Wrappers.<GlAccountBalance>lambdaUpdate()
                .eq(GlAccountBalance::getBalanceId, target.getBalanceId())
                .setSql("opening_debit = opening_debit + " + dr.toPlainString())
                .setSql("opening_credit = opening_credit + " + cr.toPlainString())
                .setSql("year_opening_debit = year_opening_debit + " + dr.toPlainString())
                .setSql("year_opening_credit = year_opening_credit + " + cr.toPlainString()));
        }
    }

    /** 月结向导步骤状态 */
    @Override
    public ClosingStepVo steps(Long periodId) {
        GlPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            throw new ServiceException("期间不存在");
        }
        ClosingStepVo vo = new ClosingStepVo();
        vo.setPeriodId(periodId);
        vo.setPeriodName(period.getPeriodName());
        vo.setPeriodStatus(period.getStatus());
        vo.setTrialChecked(ReportConstants.CLOSING_TRIAL.equals(period.getClosingStep())
            || ReportConstants.CLOSING_CARRY.equals(period.getClosingStep())
            || ReportConstants.CLOSING_REVAL.equals(period.getClosingStep())
            || ReportConstants.CLOSING_DONE.equals(period.getClosingStep()));
        vo.setCarried(ReportConstants.CLOSING_CARRY.equals(period.getClosingStep())
            || ReportConstants.CLOSING_REVAL.equals(period.getClosingStep())
            || ReportConstants.CLOSING_DONE.equals(period.getClosingStep()));
        vo.setRevaluated(ReportConstants.CLOSING_REVAL.equals(period.getClosingStep())
            || ReportConstants.CLOSING_DONE.equals(period.getClosingStep()));
        vo.setClosed(GlConstants.PERIOD_CLOSED.equals(period.getStatus())
            || GlConstants.PERIOD_LOCKED.equals(period.getStatus()));
        vo.setVoucherCount(period.getClosedVoucherCount());
        if (period.getClosingVoucherId() != null) {
            GlVoucher v = voucherMapper.selectById(period.getClosingVoucherId());
            vo.setClosingVoucherNo(v == null ? null : v.getVoucherNo());
        }
        if (period.getRevaluationVoucherId() != null) {
            GlVoucher v = voucherMapper.selectById(period.getRevaluationVoucherId());
            vo.setRevaluationVoucherNo(v == null ? null : v.getVoucherNo());
        }
        vo.setLogs(closeLogs(periodId));
        return vo;
    }

    /** 关账日志列表 */
    @Override
    public List<FinCloseLogVo> closeLogs(Long periodId) {
        return closeLogMapper.selectVoList(new LambdaQueryWrapper<FinCloseLog>()
            .eq(FinCloseLog::getPeriodId, periodId)
            .orderByDesc(FinCloseLog::getCreateTime));
    }

    // ---------- helpers ----------

    /** 校验期间存在且 OPEN */
    private GlPeriod getPeriod(Long periodId) {
        GlPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            throw new ServiceException("期间不存在");
        }
        if (!GlConstants.PERIOD_OPEN.equals(period.getStatus())) {
            throw new ServiceException("仅开放期间可月结,当前【{}】", period.getStatus());
        }
        return period;
    }

    /** 关账内部步骤(月结向导末步) */
    private void closePeriodInternal(Long periodId) {
        try {
            GlPeriod period = periodMapper.selectById(periodId);
            if (period == null) {
                throw new ServiceException("期间不存在");
            }
            // 凭证数快照
            Long count = voucherMapper.selectCount(new LambdaQueryWrapper<GlVoucher>()
                .eq(GlVoucher::getPeriodId, periodId)
                .eq(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_POSTED));
            periodService.closePeriod(periodId);
            periodMapper.update(null, Wrappers.<GlPeriod>lambdaUpdate()
                .eq(GlPeriod::getPeriodId, periodId)
                .set(GlPeriod::getClosedVoucherCount, count.intValue())
                .set(GlPeriod::getClosingStep, ReportConstants.CLOSING_DONE));
            logSuccess(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CLOSE_PERIOD,
                "关账完成,本期过账凭证 " + count + " 张");
        } catch (Exception e) {
            logFailed(periodId, ReportConstants.CLOSE_MONTHLY, ReportConstants.STEP_CLOSE_PERIOD, e.getMessage());
            throw e;
        }
    }

    /** 更新期间月结进度(结转写 closing_voucher_id,重估写 revaluation_voucher_id) */
    private void updatePeriodClosing(Long periodId, String step, Long voucherId) {
        if (ReportConstants.CLOSING_CARRY.equals(step)) {
            periodMapper.update(null, Wrappers.<GlPeriod>lambdaUpdate()
                .eq(GlPeriod::getPeriodId, periodId)
                .set(GlPeriod::getClosingStep, step)
                .set(GlPeriod::getClosingVoucherId, voucherId));
        } else {
            periodMapper.update(null, Wrappers.<GlPeriod>lambdaUpdate()
                .eq(GlPeriod::getPeriodId, periodId)
                .set(GlPeriod::getClosingStep, step)
                .set(GlPeriod::getRevaluationVoucherId, voucherId));
        }
    }

    /** 制单:ADJUSTMENT 来源,幂等键 sourceId */
    private GlVoucherVo createAdjustVoucher(GlPeriod period, Long sourceId, String summary, List<GlVoucherLine> lines) {
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(period.getEndDate());
        req.setVoucherWord("J");
        req.setVoucherSource(ReportConstants.VOUCHER_SOURCE);
        req.setSourceId(sourceId);
        req.setSourceNo(period.getPeriodName());
        req.setSummary(summary);
        req.setLines(lines);
        return voucherService.createAndPost(req);
    }

    /** 年结制单:凭证日期落次年 1 月(次年 1 月已生成则直接派生) */
    private GlVoucherVo createAdjustVoucherYear(GlPeriod dec, Long sourceId, String summary, List<GlVoucherLine> lines) {
        GlPeriod nextJan = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, dec.getFiscalYear() + 1)
            .eq(GlPeriod::getPeriodNo, 1));
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(nextJan == null ? dec.getEndDate() : nextJan.getStartDate());
        req.setVoucherWord("J");
        req.setVoucherSource(ReportConstants.VOUCHER_SOURCE);
        req.setSourceId(sourceId);
        req.setSourceNo(dec.getPeriodName());
        req.setSummary(summary);
        req.setLines(lines);
        return voucherService.createAndPost(req);
    }

    /** 按来源+来源单据查凭证(幂等判定) */
    private GlVoucher findVoucher(String source, Long sourceId) {
        return voucherMapper.selectOne(new LambdaQueryWrapper<GlVoucher>()
            .eq(GlVoucher::getVoucherSource, source)
            .eq(GlVoucher::getSourceId, sourceId)
            .last("limit 1"));
    }

    /** 构建凭证行 */
    private GlVoucherLine line(Long accountId, BigDecimal debit, BigDecimal credit, String summary) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit);
        l.setCredit(credit);
        l.setSummary(summary);
        return l;
    }

    /** 按科目编码查科目ID(仅启用末级) */
    private Long getAccountId(String code) {
        GlAccount a = accountMapper.selectOne(new LambdaQueryWrapper<GlAccount>()
            .eq(GlAccount::getAccountCode, code)
            .last("limit 1"));
        return a == null ? null : a.getAccountId();
    }

    /** 凭证行批量查询(逐张 queryById 取行) */
    private List<GlVoucherLine> voucherLineList(List<Long> voucherIds) {
        if (CollUtil.isEmpty(voucherIds)) {
            return new ArrayList<>();
        }
        List<GlVoucherLine> lines = new ArrayList<>();
        for (Long voucherId : voucherIds) {
            GlVoucherVo vo = voucherService.queryById(voucherId);
            if (vo != null && CollUtil.isNotEmpty(vo.getVoucherLineList())) {
                lines.addAll(vo.getVoucherLineList());
            }
        }
        return lines;
    }

    /** 期末汇率:取期间最后一天或最近日期 */
    private GlCurrencyRate findRate(String currency, Date endDate) {
        List<GlCurrencyRate> rates = currencyRateMapper.selectList(new LambdaQueryWrapper<GlCurrencyRate>()
            .eq(GlCurrencyRate::getCurrency, currency)
            .le(GlCurrencyRate::getRateDate, endDate)
            .orderByDesc(GlCurrencyRate::getRateDate)
            .last("limit 1"));
        return CollUtil.isEmpty(rates) ? null : rates.get(0);
    }

    /** 余额科目表 Map */
    private Map<Long, GlAccount> loadAccounts(List<GlAccountBalance> balances) {
        List<Long> ids = balances.stream().map(GlAccountBalance::getAccountId).distinct().collect(Collectors.toList());
        if (CollUtil.isEmpty(ids)) {
            return new HashMap<>();
        }
        List<GlAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<GlAccount>()
            .in(GlAccount::getAccountId, ids));
        return accounts.stream().collect(Collectors.toMap(GlAccount::getAccountId, a -> a));
    }

    /** 写成功日志 */
    private void logSuccess(Long periodId, String closeType, String step, String message) {
        log(periodId, closeType, step, ReportConstants.LOG_SUCCESS, message);
    }

    /** 写失败日志 */
    private void logFailed(Long periodId, String closeType, String step, String message) {
        log(periodId, closeType, step, ReportConstants.LOG_FAILED, message);
    }

    /** 写信息日志(复用成功状态) */
    private void logInfo(Long periodId, String closeType, String step, String message) {
        log(periodId, closeType, step, ReportConstants.LOG_SUCCESS, message);
    }

    /** 落日志(独立事务保证步骤失败也可查) */
    private void log(Long periodId, String closeType, String step, String status, String message) {
        FinCloseLog l = new FinCloseLog();
        l.setLogId(IdGeneratorUtil.nextLongId());
        l.setPeriodId(periodId);
        l.setCloseType(closeType);
        l.setStep(step);
        l.setStatus(status);
        l.setMessage(StringUtils.isBlank(message) ? null : message);
        l.setOperatorId(LoginHelper.getUserId());
        l.setCreateTime(new Date());
        closeLogMapper.insert(l);
    }

    /** null-safe BigDecimal */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
