package com.nexus.erp.fin.gl.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.domain.bo.GlVoucherBo;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherLineMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.fin.gl.service.IGlAccountBalanceService;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记账凭证 Service 实现(凭证引擎)
 * <p>借贷平衡强校验、差量更新+乐观锁、过账/反过账/作废状态机、过账写科目余额。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class GlVoucherServiceImpl implements IGlVoucherService {

    private final GlVoucherMapper baseMapper;
    private final GlVoucherLineMapper lineMapper;
    private final GlAccountMapper accountMapper;
    private final GlPeriodMapper periodMapper;
    private final IGlPeriodService periodService;
    private final IGlAccountBalanceService balanceService;

    @Override
    public TableDataInfo<GlVoucherVo> queryPageList(GlVoucherBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<GlVoucher> lqw = buildQueryWrapper(bo);
        Page<GlVoucherVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<GlVoucherVo> queryList(GlVoucherBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public GlVoucherVo queryById(Long voucherId) {
        GlVoucherVo vo = baseMapper.selectVoById(voucherId);
        if (vo != null) {
            List<GlVoucherLine> lines = lineMapper.selectList(new LambdaQueryWrapper<GlVoucherLine>()
                .eq(GlVoucherLine::getVoucherId, voucherId)
                .orderByAsc(GlVoucherLine::getLineNo));
            vo.setVoucherLineList(lines);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(GlVoucherBo bo) {
        GlVoucher entity = MapstructUtils.convert(bo, GlVoucher.class);
        BigDecimal[] totals = validateBalanced(bo.getVoucherLineList());
        entity.setVoucherNo(genVoucherNo(entity.getVoucherWord()));
        if (entity.getVoucherWord() == null) {
            entity.setVoucherWord("J");
        }
        if (entity.getVoucherSource() == null) {
            entity.setVoucherSource(GlConstants.SOURCE_MANUAL);
        }
        if (entity.getCurrency() == null) {
            entity.setCurrency("CNY");
        }
        entity.setStatus(GlConstants.VOUCHER_STATUS_DRAFT);
        entity.setTotalDebit(totals[0]);
        entity.setTotalCredit(totals[1]);
        entity.setVersion(0);
        baseMapper.insert(entity);
        insertLines(entity.getVoucherId(), bo.getVoucherLineList());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(GlVoucherBo bo) {
        GlVoucher existing = baseMapper.selectById(bo.getVoucherId());
        if (existing == null) {
            throw new ServiceException("凭证不存在");
        }
        if (!GlConstants.VOUCHER_STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("仅草稿凭证可修改,当前【{}】", existing.getStatus());
        }
        BigDecimal[] totals = validateBalanced(bo.getVoucherLineList());
        // 显式拷贝可编辑字段,不覆盖 version/status/posted_*
        existing.setVoucherWord(bo.getVoucherWord());
        existing.setVoucherDate(bo.getVoucherDate());
        existing.setAttachmentCount(bo.getAttachmentCount());
        existing.setRemark(bo.getRemark());
        existing.setTotalDebit(totals[0]);
        existing.setTotalCredit(totals[1]);
        int rows = baseMapper.updateById(existing);
        if (rows == 0) {
            throw new ServiceException("凭证数据已变更,修改失败,请重试");
        }
        if (bo.getVoucherLineList() != null) {
            diffUpdateLines(existing.getVoucherId(), bo.getVoucherLineList());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] voucherIds) {
        for (Long id : voucherIds) {
            GlVoucher v = baseMapper.selectById(id);
            if (v != null && !GlConstants.VOUCHER_STATUS_DRAFT.equals(v.getStatus())) {
                throw new ServiceException("凭证【{}】非草稿,不可删除", v.getVoucherNo());
            }
        }
        lineMapper.delete(new LambdaQueryWrapper<GlVoucherLine>()
            .in(GlVoucherLine::getVoucherId, Arrays.asList(voucherIds)));
        return baseMapper.deleteBatchIds(Arrays.asList(voucherIds)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void post(Long voucherId) {
        GlVoucher v = baseMapper.selectById(voucherId);
        if (v == null) {
            throw new ServiceException("凭证不存在");
        }
        if (!GlConstants.VOUCHER_STATUS_DRAFT.equals(v.getStatus())) {
            throw new ServiceException("仅草稿凭证可过账,当前【{}】", v.getStatus());
        }
        List<GlVoucherLine> lines = lineMapper.selectList(new LambdaQueryWrapper<GlVoucherLine>()
            .eq(GlVoucherLine::getVoucherId, voucherId)
            .orderByAsc(GlVoucherLine::getLineNo));
        BigDecimal[] totals = validateBalanced(lines);
        GlPeriod period = periodService.getOrDerivePeriod(v.getVoucherDate());
        if (!GlConstants.PERIOD_OPEN.equals(period.getStatus())) {
            throw new ServiceException("期间【{}】非开放状态,不可过账", period.getPeriodName());
        }
        int rows = baseMapper.update(null, Wrappers.<GlVoucher>lambdaUpdate()
            .eq(GlVoucher::getVoucherId, voucherId)
            .eq(GlVoucher::getVersion, v.getVersion())
            .set(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_POSTED)
            .set(GlVoucher::getPeriodId, period.getPeriodId())
            .set(GlVoucher::getTotalDebit, totals[0])
            .set(GlVoucher::getTotalCredit, totals[1])
            .set(GlVoucher::getPostedBy, LoginHelper.getUserId())
            .set(GlVoucher::getPostedTime, new Date())
            .set(GlVoucher::getVersion, v.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("凭证数据已变更,过账失败,请重试");
        }
        balanceService.applyPosting(period.getPeriodId(), lines);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpost(Long voucherId) {
        GlVoucher v = baseMapper.selectById(voucherId);
        if (v == null) {
            throw new ServiceException("凭证不存在");
        }
        if (!GlConstants.VOUCHER_STATUS_POSTED.equals(v.getStatus())) {
            throw new ServiceException("仅已过账凭证可反过账,当前【{}】", v.getStatus());
        }
        GlPeriod period = periodMapper.selectById(v.getPeriodId());
        if (period != null && GlConstants.PERIOD_LOCKED.equals(period.getStatus())) {
            throw new ServiceException("期间【{}】已锁定,不可反过账", period.getPeriodName());
        }
        List<GlVoucherLine> lines = lineMapper.selectList(new LambdaQueryWrapper<GlVoucherLine>()
            .eq(GlVoucherLine::getVoucherId, voucherId));
        if (v.getPeriodId() != null) {
            balanceService.reversePosting(v.getPeriodId(), lines);
        }
        int rows = baseMapper.update(null, Wrappers.<GlVoucher>lambdaUpdate()
            .eq(GlVoucher::getVoucherId, voucherId)
            .eq(GlVoucher::getVersion, v.getVersion())
            .set(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_DRAFT)
            .set(GlVoucher::getVersion, v.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("凭证数据已变更,反过账失败,请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long voucherId) {
        GlVoucher v = baseMapper.selectById(voucherId);
        if (v == null) {
            throw new ServiceException("凭证不存在");
        }
        if (GlConstants.VOUCHER_STATUS_POSTED.equals(v.getStatus())) {
            GlPeriod period = periodMapper.selectById(v.getPeriodId());
            if (period != null && GlConstants.PERIOD_LOCKED.equals(period.getStatus())) {
                throw new ServiceException("期间【{}】已锁定,不可作废", period.getPeriodName());
            }
            List<GlVoucherLine> lines = lineMapper.selectList(new LambdaQueryWrapper<GlVoucherLine>()
                .eq(GlVoucherLine::getVoucherId, voucherId));
            if (v.getPeriodId() != null) {
                balanceService.reversePosting(v.getPeriodId(), lines);
            }
        } else if (!GlConstants.VOUCHER_STATUS_DRAFT.equals(v.getStatus())) {
            throw new ServiceException("仅草稿/已过账凭证可作废,当前【{}】", v.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<GlVoucher>lambdaUpdate()
            .eq(GlVoucher::getVoucherId, voucherId)
            .eq(GlVoucher::getVersion, v.getVersion())
            .set(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_CANCELLED)
            .set(GlVoucher::getVersion, v.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("凭证数据已变更,作废失败,请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlVoucherVo createAndPost(VoucherBuildRequest req) {
        // 幂等:同来源+同来源单据已制单则返回既有
        if (req.getSourceId() != null && !GlConstants.SOURCE_MANUAL.equals(req.getVoucherSource())) {
            GlVoucher existing = baseMapper.selectOne(new LambdaQueryWrapper<GlVoucher>()
                .eq(GlVoucher::getVoucherSource, req.getVoucherSource())
                .eq(GlVoucher::getSourceId, req.getSourceId())
                .last("limit 1"));
            if (existing != null) {
                return queryById(existing.getVoucherId());
            }
        }
        List<GlVoucherLine> lines = req.getLines();
        if (CollUtil.isNotEmpty(lines) && StringUtils.isNotBlank(req.getSummary())) {
            for (GlVoucherLine l : lines) {
                if (StringUtils.isBlank(l.getSummary())) {
                    l.setSummary(req.getSummary());
                }
            }
        }
        BigDecimal[] totals = validateBalanced(lines);
        GlVoucher v = new GlVoucher();
        v.setVoucherDate(req.getVoucherDate());
        v.setVoucherWord(req.getVoucherWord() == null ? "J" : req.getVoucherWord());
        v.setVoucherSource(req.getVoucherSource());
        v.setSourceId(req.getSourceId());
        v.setSourceNo(req.getSourceNo());
        v.setCurrency("CNY");
        v.setRemark(req.getSummary());
        v.setVoucherNo(genVoucherNo(v.getVoucherWord()));
        v.setStatus(GlConstants.VOUCHER_STATUS_POSTED);
        v.setTotalDebit(totals[0]);
        v.setTotalCredit(totals[1]);
        v.setVersion(0);
        GlPeriod period = periodService.getOrDerivePeriod(v.getVoucherDate());
        if (!GlConstants.PERIOD_OPEN.equals(period.getStatus())) {
            throw new ServiceException("期间【{}】非开放状态,不可过账", period.getPeriodName());
        }
        v.setPeriodId(period.getPeriodId());
        v.setPostedBy(LoginHelper.getUserId());
        v.setPostedTime(new Date());
        baseMapper.insert(v);
        insertLines(v.getVoucherId(), lines);
        balanceService.applyPosting(period.getPeriodId(), lines);
        return queryById(v.getVoucherId());
    }

    // ---------- helpers ----------

    /** 校验借贷平衡 + 末级科目 + 回填科目编码/名称;返回 [借方合计, 贷方合计] */
    private BigDecimal[] validateBalanced(List<GlVoucherLine> lines) {
        if (CollUtil.isEmpty(lines)) {
            throw new ServiceException("凭证至少一行分录");
        }
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (GlVoucherLine l : lines) {
            BigDecimal d = nz(l.getDebit());
            BigDecimal c = nz(l.getCredit());
            if (d.signum() > 0 && c.signum() > 0) {
                throw new ServiceException("分录行借贷不可同时有值");
            }
            if (d.signum() == 0 && c.signum() == 0) {
                throw new ServiceException("分录行借贷金额不能同时为零");
            }
            GlAccount acc = accountMapper.selectById(l.getAccountId());
            if (acc == null) {
                throw new ServiceException("科目不存在");
            }
            if (!GlConstants.YES.equals(acc.getIsLeaf())) {
                throw new ServiceException("科目【{}】非末级,不可过账", acc.getAccountCode());
            }
            l.setAccountCode(acc.getAccountCode());
            l.setAccountName(acc.getAccountName());
            dr = dr.add(d);
            cr = cr.add(c);
        }
        if (dr.compareTo(cr) != 0) {
            throw new ServiceException("借贷不平衡:借方 {} 贷方 {}", dr, cr);
        }
        return new BigDecimal[]{dr, cr};
    }

    private void insertLines(Long voucherId, List<GlVoucherLine> lines) {
        if (CollUtil.isEmpty(lines)) {
            return;
        }
        int no = 1;
        for (GlVoucherLine l : lines) {
            l.setLineId(null);
            l.setVoucherId(voucherId);
            l.setLineNo(no++);
            lineMapper.insert(l);
        }
    }

    /** 差量更新明细行(按 lineId 新增/更新/删除),镜像 SoInvoiceServiceImpl.diffUpdateLines */
    private void diffUpdateLines(Long voucherId, List<GlVoucherLine> newLines) {
        List<GlVoucherLine> existingLines = lineMapper.selectList(new LambdaQueryWrapper<GlVoucherLine>()
            .eq(GlVoucherLine::getVoucherId, voucherId));
        Map<Long, GlVoucherLine> existingMap = new HashMap<>();
        for (GlVoucherLine l : existingLines) {
            if (l.getLineId() != null) {
                existingMap.put(l.getLineId(), l);
            }
        }
        Set<Long> keepIds = new HashSet<>();
        int no = 1;
        for (GlVoucherLine line : newLines) {
            line.setLineNo(no++);
            if (line.getLineId() == null) {
                line.setVoucherId(voucherId);
                lineMapper.insert(line);
            } else {
                line.setVoucherId(voucherId);
                lineMapper.updateById(line);
                keepIds.add(line.getLineId());
            }
        }
        for (GlVoucherLine l : existingLines) {
            if (l.getLineId() != null && !keepIds.contains(l.getLineId())) {
                lineMapper.deleteById(l.getLineId());
            }
        }
    }

    private String genVoucherNo(String voucherWord) {
        String word = StringUtils.isBlank(voucherWord) ? "PZ" : voucherWord;
        return word + IdWorker.getId();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private LambdaQueryWrapper<GlVoucher> buildQueryWrapper(GlVoucherBo bo) {
        LambdaQueryWrapper<GlVoucher> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(StringUtils.isNotBlank(bo.getVoucherNo()), GlVoucher::getVoucherNo, bo.getVoucherNo());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), GlVoucher::getStatus, bo.getStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getVoucherSource()), GlVoucher::getVoucherSource, bo.getVoucherSource());
            lqw.eq(bo.getPeriodId() != null, GlVoucher::getPeriodId, bo.getPeriodId());
            java.util.Map<String, Object> params = bo.getParams();
            Object beginDate = params == null ? null : params.get("beginVoucherDate");
            Object endDate = params == null ? null : params.get("endVoucherDate");
            lqw.ge(beginDate != null, GlVoucher::getVoucherDate, beginDate);
            lqw.le(endDate != null, GlVoucher::getVoucherDate, endDate);
        }
        lqw.orderByDesc(GlVoucher::getVoucherDate);
        lqw.orderByDesc(GlVoucher::getVoucherNo);
        return lqw;
    }
}
