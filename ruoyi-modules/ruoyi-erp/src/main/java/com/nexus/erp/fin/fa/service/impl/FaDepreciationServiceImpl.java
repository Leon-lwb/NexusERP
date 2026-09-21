package com.nexus.erp.fin.fa.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.domain.FaDepreciation;
import com.nexus.erp.fin.fa.domain.bo.FaDepreciationRunBo;
import com.nexus.erp.fin.fa.domain.vo.FaDepreciationVo;
import com.nexus.erp.fin.fa.mapper.FaAssetCategoryMapper;
import com.nexus.erp.fin.fa.mapper.FaAssetMapper;
import com.nexus.erp.fin.fa.mapper.FaDepreciationMapper;
import com.nexus.erp.fin.fa.service.IFaDepreciationService;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 固定资产折旧 Service 实现(折旧引擎 + 期间批量过账)
 * <p>支持直线法/双倍余额递减法(后 1/3 年限转直线)/年数总和法;按期间批量计提,
 * 复用 {@link IGlVoucherService#createAndPost} 幂等(sourceId=periodId*100+1)。</p>
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FaDepreciationServiceImpl implements IFaDepreciationService {

    /** 折旧批次:幂等由 buildPlans 资产粒度过滤保证,凭证 sourceId 用雪花ID(避免 periodId*100 溢出) */

    private final FaDepreciationMapper baseMapper;
    private final FaAssetMapper assetMapper;
    private final FaAssetCategoryMapper categoryMapper;
    private final GlPeriodMapper periodMapper;
    private final GlAccountMapper accountMapper;
    private final IGlVoucherService voucherService;

    /** 分页查询折旧记录 */
    @Override
    public TableDataInfo<FaDepreciationVo> queryPageList(FaDepreciationRunBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FaDepreciation> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getPeriodId() != null, FaDepreciation::getPeriodId, bo.getPeriodId());
        }
        lqw.orderByDesc(FaDepreciation::getCreateTime);
        Page<FaDepreciationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /** 折旧计划预览:某期间应计提明细(不落库不过账) */
    @Override
    public List<FaDepreciationVo> preview(Long periodId) {
        GlPeriod period = requireOpenPeriod(periodId);
        List<FaDepreciation> plans = buildPlans(period);
        // 资产批量预取,避免循环内逐条查库(N+1)
        Map<Long, FaAsset> assetMap = preloadAssets(plans);
        List<FaDepreciationVo> vos = new ArrayList<>();
        for (FaDepreciation plan : plans) {
            FaAsset asset = assetMap.get(plan.getAssetId());
            FaDepreciationVo vo = new FaDepreciationVo();
            vo.setAssetId(plan.getAssetId());
            vo.setPeriodId(plan.getPeriodId());
            vo.setDepreciationAmount(plan.getDepreciationAmount());
            vo.setStatus(FaConstants.DEP_STATUS_PLANNED);
            if (asset != null) {
                vo.setAssetNo(asset.getAssetNo());
                vo.setAssetName(asset.getAssetName());
            }
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 按期间计提并过账。
     * <p>幂等:按资产粒度过滤,期间内已计提的资产跳过(新增资产可补提),凭证源号递增防键冲突;
     * 期间 CLOSED/LOCKED 拒绝;全部汇总为一张凭证(借费用科目/贷累计折旧 1602)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runDepreciation(Long periodId) {
        GlPeriod period = requireOpenPeriod(periodId);
        List<FaDepreciation> plans = buildPlans(period);
        if (CollUtil.isEmpty(plans)) {
            log.info("期间 {} 无应计提资产", period.getPeriodName());
            return 0;
        }
        BigDecimal total = plans.stream().map(d -> nz(d.getDepreciationAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return 0;
        }
        // 汇总借:按费用科目聚合 / 贷:累计折旧 1602(资产/类别批量预取,避免 N+1)
        Map<Long, FaAsset> assetMap = preloadAssets(plans);
        Map<Long, FaAssetCategory> categoryMap = preloadCategories(assetMap.values().stream().toList());
        Map<Long, BigDecimal> debitByAccount = new HashMap<>();
        for (FaDepreciation plan : plans) {
            FaAsset asset = assetMap.get(plan.getAssetId());
            FaAssetCategory category = categoryMap.get(asset.getCategoryId());
            Long expenseAccountId = category != null && category.getDebitAccountId() != null
                ? category.getDebitAccountId() : adminExpenseAccountId();
            debitByAccount.merge(expenseAccountId, nz(plan.getDepreciationAmount()), BigDecimal::add);
        }
        List<GlVoucherLine> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : debitByAccount.entrySet()) {
            lines.add(voucherLine(entry.getKey(), entry.getValue(), null,
                "固定资产折旧-" + period.getPeriodName()));
        }
        lines.add(voucherLine(creditAccountId(), null, total, "固定资产折旧-" + period.getPeriodName()));
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(toDate(period.getEndDate()));
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_DEPRECIATION);
        // 幂等键:雪花ID。幂等由 buildPlans 资产粒度过滤保证(重跑无待提资产),避免 periodId*100 溢出碰撞
        Long batchId = IdGeneratorUtil.nextLongId();
        req.setSourceId(batchId);
        req.setSourceNo(FaConstants.SOURCE_NO_DEPRECIATION + period.getPeriodName() + "-" + batchId);
        req.setSummary("固定资产折旧-" + period.getPeriodName());
        req.setLines(lines);
        com.nexus.erp.fin.gl.domain.vo.GlVoucherVo voucher = voucherService.createAndPost(req);
        // 回填折旧记录并累加资产累计折旧;过账后批量重读资产取最新 version 做乐观锁校验
        Map<Long, FaAsset> freshAssetMap = preloadAssets(plans);
        for (FaDepreciation plan : plans) {
            FaAsset asset = freshAssetMap.get(plan.getAssetId());
            if (asset == null) {
                continue;
            }
            BigDecimal cumulative = nz(asset.getCumulativeDepreciation()).add(nz(plan.getDepreciationAmount()));
            plan.setDepreciationId(IdGeneratorUtil.nextLongId());
            plan.setStatus(FaConstants.DEP_STATUS_DONE);
            plan.setPostedVoucherId(voucher.getVoucherId());
            plan.setPostedVoucherNo(voucher.getVoucherNo());
            plan.setCumulativeDepreciation(cumulative);
            plan.setVersion(0);
            baseMapper.insert(plan);
            // 资产累计折旧与净值均以「服务端算好的绝对值 + version 乐观锁(CAS)」回写，避免并发（原值调整/转固）下丢失更新。
            // 注意(FIN-FA-01)：不可在同一 SET 中先累加 cumulative_depreciation 再在 net_book_value 表达式里引用它 ——
            // MySQL 单表 UPDATE 的 SET 从左到右求值，后面的表达式会读到前面刚写入的新值，
            // 原实现 "net_book_value = original_value - cumulative_depreciation - X" 因此每月多扣一个 X。
            // 这里改为显式绝对值（net_book_value = original_value - 新累计折旧），不依赖列间求值顺序。
            BigDecimal newNetBookValue = nz(asset.getOriginalValue()).subtract(cumulative);
            int assetRows = assetMapper.update(null, Wrappers.<FaAsset>lambdaUpdate()
                .eq(FaAsset::getAssetId, asset.getAssetId())
                .eq(FaAsset::getVersion, asset.getVersion())
                .set(FaAsset::getCumulativeDepreciation, cumulative)
                .set(FaAsset::getNetBookValue, newNetBookValue)
                .set(FaAsset::getVersion, asset.getVersion() + 1));
            if (assetRows == 0) {
                throw new ServiceException("资产[" + asset.getAssetNo() + "]数据已变更,折旧计提失败,请重试");
            }
        }
        log.info("期间 {} 折旧计提完成: {} 项, 凭证 {}", period.getPeriodName(), plans.size(), voucher.getVoucherNo());
        return plans.size();
    }

    /** 计算单资产某期间应计提折旧额(monthIndex 从 1 开始) */
    @Override
    public BigDecimal calcMonthlyDepreciation(FaAsset asset, FaAssetCategory category, long monthsInService) {
        String method = asset.getDepreciationMethod() != null ? asset.getDepreciationMethod()
            : category.getDepreciationMethod();
        BigDecimal original = nz(asset.getOriginalValue());
        BigDecimal salvage = nz(asset.getSalvageValue());
        BigDecimal netValue = nz(asset.getOriginalValue()).subtract(nz(asset.getCumulativeDepreciation()));
        int life = asset.getUsefulLife() != null ? asset.getUsefulLife() : category.getUsefulLife();
        if (life <= 0 || netValue.subtract(salvage).signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount;
        if (FaConstants.METHOD_DOUBLE_DECLINING.equals(method)) {
            if (monthsInService > (long) Math.ceil(life * 2.0 / 3.0)) {
                // 后 1/3 年限转直线:剩余月数摊完
                long remaining = Math.max(1, life - (monthsInService - 1));
                amount = netValue.subtract(salvage).divide(BigDecimal.valueOf(remaining), 2, RoundingMode.HALF_UP);
            } else {
                // 双倍余额递减:期初净值 × 2/n
                amount = netValue.multiply(BigDecimal.valueOf(2.0 / life)).setScale(2, RoundingMode.HALF_UP);
            }
        } else if (FaConstants.METHOD_SUM_OF_YEARS.equals(method)) {
            // 年数总和:剩余月数 / 月总和 × 应提总额
            // FIN-FA-04: 超使用年限(monthsInService > life)时 remaining 会为负 → 生成负折旧额(冲回),
            // 这里把剩余月数夹到 0, 配合下方"负额归零"保证超年限资产不再计提。
            long remaining = Math.max(0, life - (monthsInService - 1));
            BigDecimal sumOfDigits = BigDecimal.valueOf(life * (life + 1L) / 2.0);
            amount = original.subtract(salvage).multiply(BigDecimal.valueOf(remaining))
                .divide(sumOfDigits, 2, RoundingMode.HALF_UP);
        } else {
            // 直线法
            amount = original.subtract(salvage).divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        }
        // 上限:不超过期初净值-残值
        BigDecimal cap = netValue.subtract(salvage);
        if (amount.compareTo(cap) > 0) {
            amount = cap;
        }
        // 下限(FIN-FA-04): 任何方法都不得计提负数(超年限/参数异常时归零, 调用方按 0 跳过)
        if (amount.signum() < 0) {
            amount = BigDecimal.ZERO;
        }
        return amount;
    }

    // ---------- helpers ----------

    /** 期间必须存在且 OPEN */
    private GlPeriod requireOpenPeriod(Long periodId) {
        GlPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            throw new ServiceException("会计期间不存在");
        }
        if (!GlConstants.PERIOD_OPEN.equals(period.getStatus())) {
            throw new ServiceException("期间 {} 已关账,不能计提折旧", period.getPeriodName());
        }
        return period;
    }

    /** 构建某期间应计提折旧计划(未落库);期间已计提过的资产跳过(按资产粒度幂等) */
    private List<FaDepreciation> buildPlans(GlPeriod period) {
        LocalDate end = toLocalDate(period.getEndDate());
        List<FaAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<FaAsset>()
            .eq(FaAsset::getStatus, FaConstants.ASSET_STATUS_IN_USE)
            .isNotNull(FaAsset::getInUseDate));
        if (CollUtil.isEmpty(assets)) {
            return new ArrayList<>();
        }
        // 本期已计提资产(幂等过滤)
        List<Long> doneAssetIds = baseMapper.selectList(new LambdaQueryWrapper<FaDepreciation>()
                .eq(FaDepreciation::getPeriodId, period.getPeriodId()))
            .stream().map(FaDepreciation::getAssetId).toList();
        // 类别批量预取,避免循环内逐条查库(N+1)
        Map<Long, FaAssetCategory> categoryMap = preloadCategories(assets);
        List<FaDepreciation> plans = new ArrayList<>();
        for (FaAsset asset : assets) {
            if (doneAssetIds.contains(asset.getAssetId())) {
                continue;
            }
            LocalDate inUse = toLocalDate(asset.getInUseDate());
            if (inUse.isAfter(end)) {
                continue;
            }
            if (FaConstants.METHOD_MANUAL.equals(asset.getDepreciationMethod())) {
                continue;
            }
            long monthsInService = (end.getYear() - inUse.getYear()) * 12L + (end.getMonthValue() - inUse.getMonthValue()) + 1;
            FaAssetCategory category = categoryMap.get(asset.getCategoryId());
            if (category == null) {
                continue;
            }
            BigDecimal amount = calcMonthlyDepreciation(asset, category, monthsInService);
            if (amount.signum() == 0) {
                continue;
            }
            FaDepreciation plan = new FaDepreciation();
            plan.setAssetId(asset.getAssetId());
            plan.setPeriodId(period.getPeriodId());
            plan.setDepreciationAmount(amount);
            plan.setStatus(FaConstants.DEP_STATUS_PLANNED);
            plans.add(plan);
        }
        return plans;
    }

    /** 批量预取折旧计划关联资产,避免循环内逐条查库(N+1) */
    private Map<Long, FaAsset> preloadAssets(List<FaDepreciation> plans) {
        Map<Long, FaAsset> assetMap = new HashMap<>();
        List<Long> assetIds = plans.stream().map(FaDepreciation::getAssetId).distinct().toList();
        if (CollUtil.isNotEmpty(assetIds)) {
            assetMapper.selectBatchIds(assetIds).forEach(asset -> assetMap.put(asset.getAssetId(), asset));
        }
        return assetMap;
    }

    /** 批量预取资产类别,避免循环内逐条查库(N+1) */
    private Map<Long, FaAssetCategory> preloadCategories(List<FaAsset> assets) {
        Map<Long, FaAssetCategory> categoryMap = new HashMap<>();
        List<Long> categoryIds = assets.stream().map(FaAsset::getCategoryId)
            .filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isNotEmpty(categoryIds)) {
            categoryMapper.selectBatchIds(categoryIds)
                .forEach(category -> categoryMap.put(category.getCategoryId(), category));
        }
        return categoryMap;
    }

    /** 累计折旧科目 1602 */
    private Long creditAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_FA_DEPRECIATION);
    }

    /** 管理费用科目 6602(类别未配费用科目时的兜底) */
    private Long adminExpenseAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_ADMIN_EXPENSE);
    }

    /** 按科目编码查询科目ID */
    private Long findAccountIdByCode(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在: {}", code);
        }
        return acc.getAccountId();
    }

    /** 构建凭证行 */
    private GlVoucherLine voucherLine(Long accountId, BigDecimal debit, BigDecimal credit, String summary) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit);
        l.setCredit(credit);
        l.setSummary(summary);
        return l;
    }

    private LocalDate toLocalDate(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private java.util.Date toDate(java.util.Date date) {
        return date;
    }

    /** null-safe */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
