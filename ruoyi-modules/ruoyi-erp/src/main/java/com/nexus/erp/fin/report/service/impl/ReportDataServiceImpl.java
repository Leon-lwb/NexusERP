package com.nexus.erp.fin.report.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.mapper.GlAccountBalanceMapper;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.report.constant.ReportConstants;
import com.nexus.erp.fin.report.domain.ReportItem;
import com.nexus.erp.fin.report.domain.bo.ReportItemBo;
import com.nexus.erp.fin.report.domain.vo.ReportItemVo;
import com.nexus.erp.fin.report.domain.vo.ReportLineVo;
import com.nexus.erp.fin.report.mapper.ReportItemMapper;
import com.nexus.erp.fin.report.service.IReportDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表取数引擎 Service 实现
 * <p>读 gl_account_balance(Java 层聚合,与试算平衡表同构);按 report_item 映射取数。</p>
 *
 * @author nexus
 * @date 2026-07-31
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class ReportDataServiceImpl implements IReportDataService {

    /** 资产负债表:资产段项目编码(合计行汇总用) */
    private static final Set<String> BALANCE_ASSET_CODES = Set.of(
        "BS_MONETARY", "BS_AR", "BS_PREPAY", "BS_OTHER_RECEIVABLE", "BS_INVENTORY", "BS_FA_NET");
    /** 资产负债表:负债+权益段项目编码 */
    private static final Set<String> BALANCE_LIABILITY_EQUITY_CODES = Set.of(
        "BS_AP", "BS_ADVANCE_RECEIVED", "BS_SALARY_PAYABLE", "BS_TAX_PAYABLE", "BS_EQUITY_TOTAL");
    /** 利润表:费用类项目编码(净利润 = 收入 - 费用 - 成本) */
    private static final Set<String> INCOME_COST_EXPENSE_CODES = Set.of(
        "IS_COST", "IS_TAX", "IS_SELL_EXP", "IS_ADMIN_EXP", "IS_FIN_EXP", "IS_NON_OPER_EXP");
    /** 利润表:收入类项目编码 */
    private static final Set<String> INCOME_REVENUE_CODES = Set.of("IS_REVENUE", "IS_NON_OPER_INCOME");

    private final ReportItemMapper itemMapper;
    private final GlAccountBalanceMapper balanceMapper;
    private final GlAccountMapper accountMapper;
    private final GlPeriodMapper periodMapper;

    /** 资产负债表:按期间取期末余额,按 report_item(BALANCE) 映射;末尾追加资产/负债权益合计行 */
    @Override
    public List<ReportLineVo> balanceSheet(Long periodId, boolean includeZero) {
        List<ReportItem> items = listItems(ReportConstants.REPORT_BALANCE);
        Map<Long, GlAccountBalance> balanceMap = loadBalanceMap(periodId);
        List<ReportLineVo> result = new ArrayList<>();
        Map<String, ReportLineVo> byCode = new HashMap<>();
        for (ReportItem item : items) {
            if ("1".equals(item.getIsSummary())) {
                continue; // seed 合计行由引擎统一追加,跳过
            }
            BigDecimal amount = matchClosingAmount(item, balanceMap);
            if (!includeZero && nz(amount).signum() == 0) {
                continue;
            }
            ReportLineVo vo = buildLine(item, amount, null);
            byCode.put(item.getItemCode(), vo);
            result.add(vo);
        }
        // 合计行:资产总计 / 负债及权益总计(勾稽校验用)
        result.add(buildSummaryLine("BS_ASSET_TOTAL", "资产总计", 990, sumByCodes(result, byCode, BALANCE_ASSET_CODES)));
        result.add(buildSummaryLine("BS_LIABILITY_EQUITY_TOTAL", "负债及权益总计", 991,
            sumByCodes(result, byCode, BALANCE_LIABILITY_EQUITY_CODES)));
        result.sort(Comparator.comparingInt(this::lineSort));
        return result;
    }

    /** 利润表:本期或本年累计发生额,按 report_item(INCOME) 映射;末尾追加净利润合计行 */
    @Override
    public List<ReportLineVo> incomeStatement(Long periodId, boolean yearToDate) {
        List<ReportItem> items = listItems(ReportConstants.REPORT_INCOME);
        List<ReportLineVo> result = new ArrayList<>();
        Map<String, ReportLineVo> byCode = new HashMap<>();
        if (yearToDate) {
            Map<String, BigDecimal> amounts = matchYearAmounts(items, periodId);
            for (ReportItem item : items) {
                if ("1".equals(item.getIsSummary())) {
                    continue;
                }
                ReportLineVo vo = buildLine(item, amounts.getOrDefault(item.getItemCode(), BigDecimal.ZERO), null);
                byCode.put(item.getItemCode(), vo);
                result.add(vo);
            }
        } else {
            Map<Long, GlAccountBalance> balanceMap = loadBalanceMap(periodId);
            for (ReportItem item : items) {
                if ("1".equals(item.getIsSummary())) {
                    continue;
                }
                BigDecimal amount = matchPeriodAmount(item, balanceMap);
                ReportLineVo vo = buildLine(item, amount, null);
                byCode.put(item.getItemCode(), vo);
                result.add(vo);
            }
        }
        // 净利润 = 收入合计 - 成本费用合计
        BigDecimal revenue = sumByCodes(result, byCode, INCOME_REVENUE_CODES);
        BigDecimal expense = sumByCodes(result, byCode, INCOME_COST_EXPENSE_CODES);
        result.add(buildSummaryLine("IS_NET_PROFIT", "净利润", 990, revenue.subtract(expense)));
        result.sort(Comparator.comparingInt(this::lineSort));
        return result;
    }

    /** 现金流量表(直接法基础版):科目区间发生额归集 */
    @Override
    public List<ReportLineVo> cashFlow(Long periodId, boolean yearToDate) {
        List<ReportItem> items = listItems(ReportConstants.REPORT_CASH);
        List<ReportLineVo> result = new ArrayList<>();
        if (yearToDate) {
            Map<String, BigDecimal> amounts = matchYearAmounts(items, periodId);
            for (ReportItem item : items) {
                result.add(buildLine(item, amounts.getOrDefault(item.getItemCode(), BigDecimal.ZERO), null));
            }
        } else {
            Map<Long, GlAccountBalance> balanceMap = loadBalanceMap(periodId);
            for (ReportItem item : items) {
                result.add(buildLine(item, matchCashAmount(item, balanceMap), null));
            }
        }
        result.sort(Comparator.comparingInt(this::lineSort));
        return result;
    }

    /** 查询报表项目映射配置 */
    @Override
    public List<ReportItemVo> itemConfig(String reportType) {
        LambdaQueryWrapper<ReportItem> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(reportType)) {
            lqw.eq(ReportItem::getReportType, reportType);
        }
        lqw.orderByAsc(ReportItem::getReportType);
        lqw.orderByAsc(ReportItem::getSortOrder);
        return itemMapper.selectVoList(lqw);
    }

    /** 保存报表项目映射:有 itemId 更新,否则新增 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveItemConfig(ReportItemBo bo) {
        ReportItem item = new ReportItem();
        item.setItemId(bo.getItemId());
        item.setReportType(bo.getReportType());
        item.setItemCode(bo.getItemCode());
        item.setItemName(bo.getItemName());
        item.setSortOrder(bo.getSortOrder());
        item.setParentItemId(bo.getParentItemId());
        item.setIsSummary(bo.getIsSummary());
        item.setAccountCodeMatch(bo.getAccountCodeMatch());
        item.setSign(bo.getSign());
        item.setStatus(bo.getStatus());
        if (item.getItemId() == null) {
            item.setItemId(IdGeneratorUtil.nextLongId());
            item.setVersion(0);
            itemMapper.insert(item);
        } else {
            ReportItem existing = itemMapper.selectById(item.getItemId());
            if (existing == null) {
                throw new ServiceException("报表项目不存在");
            }
            item.setVersion(existing.getVersion());
            itemMapper.updateById(item);
        }
        return true;
    }

    /** 删除报表项目映射 */
    @Override
    public Boolean deleteItemConfig(Long[] itemIds) {
        if (itemIds == null || itemIds.length == 0) {
            return true;
        }
        return itemMapper.deleteByIds(List.of(itemIds)) > 0;
    }

    // ---------- helpers ----------

    /** 取某报表类型全部启用项目 */
    private List<ReportItem> listItems(String reportType) {
        return itemMapper.selectList(new LambdaQueryWrapper<ReportItem>()
            .eq(ReportItem::getReportType, reportType)
            .eq(ReportItem::getStatus, "0")
            .orderByAsc(ReportItem::getSortOrder));
    }

    /** 某期间全部余额 Map(accountId -> balance) */
    private Map<Long, GlAccountBalance> loadBalanceMap(Long periodId) {
        List<GlAccountBalance> balances = balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .eq(GlAccountBalance::getPeriodId, periodId));
        Map<Long, GlAccountBalance> map = new HashMap<>();
        for (GlAccountBalance b : balances) {
            map.put(b.getAccountId(), b);
        }
        return map;
    }

    /** 本年累计:1月~本期各期间余额列表 */
    private List<GlAccountBalance> loadYearBalances(GlPeriod period) {
        List<GlPeriod> yearPeriods = periodMapper.selectList(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, period.getFiscalYear())
            .le(GlPeriod::getPeriodNo, period.getPeriodNo()));
        if (CollUtil.isEmpty(yearPeriods)) {
            return new ArrayList<>();
        }
        List<Long> periodIds = yearPeriods.stream().map(GlPeriod::getPeriodId).collect(Collectors.toList());
        return balanceMapper.selectList(new LambdaQueryWrapper<GlAccountBalance>()
            .in(GlAccountBalance::getPeriodId, periodIds));
    }

    /** 按匹配规则取期末净额(资产负债表):净额=期初+本期发生(Java 聚合,closing 列未落库),贷方科目取反,乘 sign */
    private BigDecimal matchClosingAmount(ReportItem item, Map<Long, GlAccountBalance> balanceMap) {
        List<Long> ids = matchAccountIds(item, balanceMap.keySet());
        if (CollUtil.isEmpty(ids)) {
            return BigDecimal.ZERO;
        }
        Map<Long, GlAccount> accountMap = loadAccountMap(ids);
        BigDecimal total = BigDecimal.ZERO;
        int sign = parseSign(item.getSign());
        for (Long id : ids) {
            GlAccountBalance b = balanceMap.get(id);
            GlAccount a = accountMap.get(id);
            if (b == null || a == null) {
                continue;
            }
            // 期末净额=(期初借方-期初贷方)+(本期借方-本期贷方),与试算平衡表同构
            BigDecimal net = nz(b.getOpeningDebit()).subtract(nz(b.getOpeningCredit()))
                .add(nz(b.getPeriodDebit())).subtract(nz(b.getPeriodCredit()));
            if ("CREDIT".equals(a.getBalanceDir())) {
                net = net.negate();
            }
            total = total.add(net.multiply(BigDecimal.valueOf(sign)));
        }
        return total;
    }

    /** 本期发生净额(利润表本期):period_debit/period_credit 净额,贷方科目取反,乘 sign */
    private BigDecimal matchPeriodAmount(ReportItem item, Map<Long, GlAccountBalance> balanceMap) {
        List<Long> ids = matchAccountIds(item, balanceMap.keySet());
        if (CollUtil.isEmpty(ids)) {
            return BigDecimal.ZERO;
        }
        Map<Long, GlAccount> accountMap = loadAccountMap(ids);
        BigDecimal total = BigDecimal.ZERO;
        int sign = parseSign(item.getSign());
        for (Long id : ids) {
            GlAccountBalance b = balanceMap.get(id);
            GlAccount a = accountMap.get(id);
            if (b == null || a == null) {
                continue;
            }
            BigDecimal net = nz(b.getPeriodDebit()).subtract(nz(b.getPeriodCredit()));
            if ("CREDIT".equals(a.getBalanceDir())) {
                net = net.negate();
            }
            total = total.add(net.multiply(BigDecimal.valueOf(sign)));
        }
        return total;
    }

    /** 本年累计发生额:各期 period_debit/period_credit 合计(利润表/现金流量表共用) */
    private Map<String, BigDecimal> matchYearAmounts(List<ReportItem> items, Long periodId) {
        GlPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            return new HashMap<>();
        }
        List<GlAccountBalance> yearBalances = loadYearBalances(period);
        if (CollUtil.isEmpty(yearBalances)) {
            return new HashMap<>();
        }
        // 按 accountId 聚合各期发生
        Map<Long, GlAccountBalance> agg = new HashMap<>();
        for (GlAccountBalance b : yearBalances) {
            GlAccountBalance acc = agg.computeIfAbsent(b.getAccountId(), k -> new GlAccountBalance());
            acc.setPeriodDebit(nz(acc.getPeriodDebit()).add(nz(b.getPeriodDebit())));
            acc.setPeriodCredit(nz(acc.getPeriodCredit()).add(nz(b.getPeriodCredit())));
        }
        Map<Long, GlAccount> accountMap = loadAccountMap(new ArrayList<>(agg.keySet()));
        Map<String, BigDecimal> result = new HashMap<>();
        for (ReportItem item : items) {
            List<Long> ids = matchAccountIds(item, agg.keySet());
            BigDecimal total = BigDecimal.ZERO;
            int sign = parseSign(item.getSign());
            for (Long id : ids) {
                GlAccountBalance b = agg.get(id);
                GlAccount a = accountMap.get(id);
                if (b == null || a == null) {
                    continue;
                }
                BigDecimal net = nz(b.getPeriodDebit()).subtract(nz(b.getPeriodCredit()));
                if ("CREDIT".equals(a.getBalanceDir())) {
                    net = net.negate();
                }
                total = total.add(net.multiply(BigDecimal.valueOf(sign)));
            }
            result.put(item.getItemCode(), total);
        }
        return result;
    }

    /** 现金流量表本期:sign=1 取贷方发生(收现),sign=-1 取借方发生(付现) */
    private BigDecimal matchCashAmount(ReportItem item, Map<Long, GlAccountBalance> balanceMap) {
        List<Long> ids = matchAccountIds(item, balanceMap.keySet());
        if (CollUtil.isEmpty(ids)) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        int sign = parseSign(item.getSign());
        for (Long id : ids) {
            GlAccountBalance b = balanceMap.get(id);
            if (b == null) {
                continue;
            }
            if (sign > 0) {
                total = total.add(nz(b.getPeriodCredit()));
            } else {
                total = total.add(nz(b.getPeriodDebit()));
            }
        }
        return total;
    }

    /** 匹配科目ID:精确编码或前缀通配(如 2221% 匹配 2221/2221.01/2221.02) */
    private List<Long> matchAccountIds(ReportItem item, Set<Long> balanceIds) {
        if (StringUtils.isBlank(item.getAccountCodeMatch()) || CollUtil.isEmpty(balanceIds)) {
            return new ArrayList<>();
        }
        List<GlAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<GlAccount>()
            .in(GlAccount::getAccountId, balanceIds));
        List<Long> matched = new ArrayList<>();
        for (String pattern : item.getAccountCodeMatch().split(",")) {
            String p = pattern.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.endsWith("%")) {
                String prefix = p.substring(0, p.length() - 1);
                for (GlAccount a : accounts) {
                    if (a.getAccountCode().startsWith(prefix)) {
                        matched.add(a.getAccountId());
                    }
                }
            } else {
                for (GlAccount a : accounts) {
                    if (p.equals(a.getAccountCode())) {
                        matched.add(a.getAccountId());
                    }
                }
            }
        }
        return matched.stream().distinct().collect(Collectors.toList());
    }

    /** 科目表 Map(accountId -> account) */
    private Map<Long, GlAccount> loadAccountMap(List<Long> accountIds) {
        if (CollUtil.isEmpty(accountIds)) {
            return new HashMap<>();
        }
        List<GlAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<GlAccount>()
            .in(GlAccount::getAccountId, accountIds));
        return accounts.stream().collect(Collectors.toMap(GlAccount::getAccountId, a -> a));
    }

    /** 构建普通报表行 */
    private ReportLineVo buildLine(ReportItem item, BigDecimal amount, BigDecimal yearAmount) {
        ReportLineVo vo = new ReportLineVo();
        vo.setReportType(item.getReportType());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setParentItemId(item.getParentItemId());
        vo.setIsSummary(false);
        vo.setSortOrder(item.getSortOrder());
        vo.setAmount(nz(amount));
        vo.setYearAmount(yearAmount == null ? null : nz(yearAmount));
        return vo;
    }

    /** 构建合计行(置底显示) */
    private ReportLineVo buildSummaryLine(String itemCode, String itemName, int sortOrder, BigDecimal amount) {
        ReportLineVo vo = new ReportLineVo();
        vo.setItemCode(itemCode);
        vo.setItemName(itemName);
        vo.setIsSummary(true);
        vo.setSortOrder(sortOrder);
        vo.setAmount(nz(amount));
        return vo;
    }

    /** 按项目编码集合汇总(仅统计已生成的行,防缺项目报错) */
    private BigDecimal sumByCodes(List<ReportLineVo> lines, Map<String, ReportLineVo> byCode, Set<String> codes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String code : codes) {
            ReportLineVo vo = byCode.get(code);
            if (vo != null) {
                sum = sum.add(nz(vo.getAmount()));
            }
        }
        return sum;
    }

    /** 排序:合计行置底 */
    private int lineSort(ReportLineVo vo) {
        return Boolean.TRUE.equals(vo.getIsSummary()) ? 9999 : vo.getSortOrder() == null ? 0 : vo.getSortOrder();
    }

    /** sign 字符串转整数 */
    private int parseSign(String sign) {
        return "-1".equals(sign) ? -1 : 1;
    }

    /** null-safe BigDecimal */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
