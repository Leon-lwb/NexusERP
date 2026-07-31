package com.nexus.erp.fin.fa.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.domain.FaDisposal;
import com.nexus.erp.fin.fa.domain.bo.FaDisposalBo;
import com.nexus.erp.fin.fa.domain.vo.FaDisposalVo;
import com.nexus.erp.fin.fa.mapper.FaAssetCategoryMapper;
import com.nexus.erp.fin.fa.mapper.FaAssetMapper;
import com.nexus.erp.fin.fa.mapper.FaDisposalMapper;
import com.nexus.erp.fin.fa.service.IFaDisposalService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 资产处置 Service 实现
 * <p>出售/报废:借 累计折旧1602 + 借(贷)处置损益科目 + 借 银行存款1002(出售) = 贷 固定资产1601;
 * 损益=售价+累计折旧-原值,正收益入 6301/负损失入 6711;复用 createAndPost 幂等(同资产只处置一次)。</p>
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FaDisposalServiceImpl implements IFaDisposalService {

    private final FaDisposalMapper baseMapper;
    private final FaAssetMapper assetMapper;
    private final FaAssetCategoryMapper categoryMapper;
    private final GlPeriodMapper periodMapper;
    private final GlAccountMapper accountMapper;
    private final IGlVoucherService voucherService;

    /** 分页查询处置记录 */
    @Override
    public TableDataInfo<FaDisposalVo> queryPageList(FaDisposalBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FaDisposal> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getAssetId() != null, FaDisposal::getAssetId, bo.getAssetId());
        }
        lqw.orderByDesc(FaDisposal::getCreateTime);
        Page<FaDisposalVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 处置资产并过账。
     * <p>校验:资产存在且非已处置、处置日期所在期间 OPEN、同资产未处置过(幂等)。
     * 处置凭证行:借 累计折旧1602(已提)/贷 固定资产1601(原值)/借 银行存款1002(售价);
     * 差额:正入处置收益 6301,负入处置损失 6711。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean dispose(FaDisposalBo bo) {
        FaAsset asset = assetMapper.selectById(bo.getAssetId());
        if (asset == null) {
            throw new ServiceException("资产不存在");
        }
        if (FaConstants.ASSET_STATUS_DISPOSED.equals(asset.getStatus())) {
            throw new ServiceException("资产已处置");
        }
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<FaDisposal>()
            .eq(FaDisposal::getAssetId, bo.getAssetId()));
        if (exists != null && exists > 0) {
            throw new ServiceException("资产已存在处置记录,不能重复处置");
        }
        LocalDate date = toLocalDate(bo.getDisposalDate());
        GlPeriod period = periodMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, date.getYear())
            .eq(GlPeriod::getPeriodNo, date.getMonthValue()));
        if (period == null || !GlConstants.PERIOD_OPEN.equals(period.getStatus())) {
            throw new ServiceException("处置日期所在期间不存在或已关账");
        }
        FaAssetCategory category = categoryMapper.selectById(asset.getCategoryId());
        BigDecimal original = nz(asset.getOriginalValue());
        BigDecimal accumulated = nz(asset.getCumulativeDepreciation());
        BigDecimal price = nz(bo.getDisposalPrice());
        BigDecimal netBook = original.subtract(accumulated);
        BigDecimal gainLoss = price.add(accumulated).subtract(original);
        // 行:借 累计折旧 / 借 银行存款(出售) / 贷 固定资产 / 差额 损益科目
        List<GlVoucherLine> lines = new ArrayList<>();
        String summary = (FaConstants.DISPOSAL_SALE.equals(bo.getDisposalType()) ? "固定资产出售 " : "固定资产报废 ")
            + asset.getAssetNo() + " " + asset.getAssetName();
        lines.add(voucherLine(creditAccountId(), accumulated, null, summary));
        if (FaConstants.DISPOSAL_SALE.equals(bo.getDisposalType()) && price.signum() > 0) {
            lines.add(voucherLine(bankAccountId(), price, null, summary));
        }
        lines.add(voucherLine(originalAccountId(), null, original, summary));
        if (gainLoss.signum() > 0) {
            Long gainAccount = category != null && category.getDisposalGainAccountId() != null
                ? category.getDisposalGainAccountId() : nonOperIncomeAccountId();
            lines.add(voucherLine(gainAccount, null, gainLoss, summary));
        } else if (gainLoss.signum() < 0) {
            Long lossAccount = category != null && category.getDisposalLossAccountId() != null
                ? category.getDisposalLossAccountId() : nonOperExpenseAccountId();
            lines.add(voucherLine(lossAccount, gainLoss.negate(), null, summary));
        }
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(bo.getDisposalDate());
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_DEPRECIATION);
        // 幂等键:同资产仅处置一次(上方已校验),sourceId 用雪花ID避免 periodId*100 溢出与折旧批次键冲突
        Long disposalId = IdGeneratorUtil.nextLongId();
        req.setSourceId(disposalId);
        req.setSourceNo(FaConstants.SOURCE_NO_DISPOSAL + asset.getAssetNo() + "-" + disposalId);
        req.setSummary(summary);
        req.setLines(lines);
        com.nexus.erp.fin.gl.domain.vo.GlVoucherVo voucher = voucherService.createAndPost(req);
        // 落处置记录 + 资产置为已处置
        FaDisposal disposal = MapstructUtils.convert(bo, FaDisposal.class);
        disposal.setDisposalId(disposalId);
        disposal.setNetBookValue(netBook);
        disposal.setGainLoss(gainLoss);
        disposal.setPostedVoucherId(voucher.getVoucherId());
        disposal.setPostedVoucherNo(voucher.getVoucherNo());
        disposal.setStatus(FaConstants.DISPOSAL_STATUS_POSTED);
        disposal.setVersion(0);
        baseMapper.insert(disposal);
        asset.setStatus(FaConstants.ASSET_STATUS_DISPOSED);
        asset.setNetBookValue(BigDecimal.ZERO);
        assetMapper.updateById(asset);
        log.info("资产处置完成: assetNo={}, type={}, 损益={}, 凭证 {}", asset.getAssetNo(), bo.getDisposalType(), gainLoss, voucher.getVoucherNo());
        return true;
    }

    // ---------- helpers ----------

    /** 累计折旧科目 1602 */
    private Long creditAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_FA_DEPRECIATION);
    }

    /** 固定资产原值科目 1601 */
    private Long originalAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_FA_ORIGINAL);
    }

    /** 银行存款科目 1002 */
    private Long bankAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_BANK);
    }

    /** 处置收益科目 6301 */
    private Long nonOperIncomeAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_NON_OPER_INCOME);
    }

    /** 处置损失科目 6711 */
    private Long nonOperExpenseAccountId() {
        return findAccountIdByCode(FaConstants.ACCT_NON_OPER_EXPENSE);
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

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** null-safe */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
