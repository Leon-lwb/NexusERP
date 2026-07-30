package com.nexus.erp.fin.gl.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.domain.vo.TrialBalanceVo;
import com.nexus.erp.fin.gl.mapper.GlAccountBalanceMapper;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.service.IGlAccountBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 科目余额 Service 实现
 * <p>过账原子累加本期发生额(setSql,并发安全);试算平衡表 Java 聚合(无 XML)。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class GlAccountBalanceServiceImpl implements IGlAccountBalanceService {

    private final GlAccountBalanceMapper baseMapper;
    private final GlAccountMapper accountMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyPosting(Long periodId, List<GlVoucherLine> lines) {
        apply(periodId, lines, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reversePosting(Long periodId, List<GlVoucherLine> lines) {
        apply(periodId, lines, false);
    }

    private void apply(Long periodId, List<GlVoucherLine> lines, boolean add) {
        if (CollUtil.isEmpty(lines)) {
            return;
        }
        // 按 accountId 聚合借贷
        Map<Long, BigDecimal[]> agg = new HashMap<>();
        for (GlVoucherLine l : lines) {
            BigDecimal[] dc = agg.computeIfAbsent(l.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            dc[0] = dc[0].add(nz(l.getDebit()));
            dc[1] = dc[1].add(nz(l.getCredit()));
        }
        for (Map.Entry<Long, BigDecimal[]> e : agg.entrySet()) {
            Long accountId = e.getKey();
            BigDecimal dr = e.getValue()[0];
            BigDecimal cr = e.getValue()[1];
            if (dr.signum() == 0 && cr.signum() == 0) {
                continue;
            }
            GlAccountBalance bal = baseMapper.selectOne(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getPeriodId, periodId)
                .eq(GlAccountBalance::getAccountId, accountId));
            if (bal == null) {
                bal = new GlAccountBalance();
                bal.setBalanceId(IdGeneratorUtil.nextLongId());
                bal.setPeriodId(periodId);
                bal.setAccountId(accountId);
                bal.setVersion(0);
                try {
                    baseMapper.insert(bal);
                } catch (DuplicateKeyException ex) {
                    // 并发插入:唯一索引兜底,重查既有记录
                    bal = baseMapper.selectOne(new LambdaQueryWrapper<GlAccountBalance>()
                        .eq(GlAccountBalance::getPeriodId, periodId)
                        .eq(GlAccountBalance::getAccountId, accountId));
                }
            }
            String drSql = add ? "period_debit = period_debit + " + dr.toPlainString()
                : "period_debit = period_debit - " + dr.toPlainString();
            String crSql = add ? "period_credit = period_credit + " + cr.toPlainString()
                : "period_credit = period_credit - " + cr.toPlainString();
            baseMapper.update(null, Wrappers.<GlAccountBalance>lambdaUpdate()
                .eq(GlAccountBalance::getBalanceId, bal.getBalanceId())
                .setSql(drSql)
                .setSql(crSql));
        }
    }

    @Override
    public List<TrialBalanceVo> trialBalance(Long periodId, String accountType) {
        List<GlAccountBalance> balances = baseMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .eq(GlAccountBalance::getPeriodId, periodId));
        if (CollUtil.isEmpty(balances)) {
            return new ArrayList<>();
        }
        List<Long> accountIds = balances.stream().map(GlAccountBalance::getAccountId).distinct().collect(Collectors.toList());
        List<GlAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<GlAccount>()
            .in(GlAccount::getAccountId, accountIds));
        Map<Long, GlAccount> accountMap = accounts.stream().collect(Collectors.toMap(GlAccount::getAccountId, a -> a));

        List<TrialBalanceVo> result = new ArrayList<>();
        for (GlAccountBalance b : balances) {
            GlAccount a = accountMap.get(b.getAccountId());
            if (a == null) {
                continue;
            }
            if (StringUtils.isNotBlank(accountType) && !accountType.equals(a.getAccountType())) {
                continue;
            }
            TrialBalanceVo vo = new TrialBalanceVo();
            vo.setAccountId(a.getAccountId());
            vo.setAccountCode(a.getAccountCode());
            vo.setAccountName(a.getAccountName());
            vo.setAccountType(a.getAccountType());
            vo.setBalanceDir(a.getBalanceDir());
            vo.setOpeningDebit(nz(b.getOpeningDebit()));
            vo.setOpeningCredit(nz(b.getOpeningCredit()));
            vo.setPeriodDebit(nz(b.getPeriodDebit()));
            vo.setPeriodCredit(nz(b.getPeriodCredit()));
            // 期末 = 期初 ± 本期发生(借贷净额)
            BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
            if (net.signum() >= 0) {
                vo.setClosingDebit(net);
                vo.setClosingCredit(BigDecimal.ZERO);
            } else {
                vo.setClosingDebit(BigDecimal.ZERO);
                vo.setClosingCredit(net.negate());
            }
            result.add(vo);
        }
        result.sort(Comparator.comparing(TrialBalanceVo::getAccountCode));
        return result;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
