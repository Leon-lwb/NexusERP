package com.nexus.erp.fin.gl.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.bo.GlPeriodBo;
import com.nexus.erp.fin.gl.domain.vo.GlPeriodVo;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

/**
 * 会计期间 Service 实现
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class GlPeriodServiceImpl implements IGlPeriodService {

    private final GlPeriodMapper baseMapper;
    private final GlVoucherMapper voucherMapper;

    @Override
    public TableDataInfo<GlPeriodVo> queryPageList(GlPeriodBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<GlPeriod> lqw = buildQueryWrapper(bo);
        Page<GlPeriodVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public GlPeriodVo queryById(Long periodId) {
        return baseMapper.selectVoById(periodId);
    }

    @Override
    public GlPeriod getOrDerivePeriod(Date voucherDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(voucherDate);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        GlPeriod period = baseMapper.selectOne(new LambdaQueryWrapper<GlPeriod>()
            .eq(GlPeriod::getFiscalYear, year)
            .eq(GlPeriod::getPeriodNo, month));
        if (period != null) {
            return period;
        }
        // 派生 OPEN 期间
        LocalDate startLd = LocalDate.of(year, month, 1);
        LocalDate endLd = startLd.withDayOfMonth(startLd.lengthOfMonth());
        period = new GlPeriod();
        period.setPeriodId(IdGeneratorUtil.nextLongId());
        period.setFiscalYear(year);
        period.setPeriodNo(month);
        period.setPeriodName(String.format("%04d-%02d", year, month));
        period.setStartDate(toDate(startLd));
        period.setEndDate(toDate(endLd));
        period.setStatus(GlConstants.PERIOD_OPEN);
        period.setVersion(0);
        baseMapper.insert(period);
        return period;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean closePeriod(Long periodId) {
        GlPeriod p = baseMapper.selectById(periodId);
        if (p == null) {
            throw new ServiceException("期间不存在");
        }
        if (!GlConstants.PERIOD_OPEN.equals(p.getStatus())) {
            throw new ServiceException("仅开放期间可关账,当前【{}】", p.getStatus());
        }
        boolean hasDraft = voucherMapper.exists(new LambdaQueryWrapper<GlVoucher>()
            .eq(GlVoucher::getPeriodId, periodId)
            .eq(GlVoucher::getStatus, GlConstants.VOUCHER_STATUS_DRAFT));
        if (hasDraft) {
            throw new ServiceException("该期间存在草稿凭证,请先过账或作废后再关账");
        }
        p.setStatus(GlConstants.PERIOD_CLOSED);
        p.setClosedBy(LoginHelper.getUserId());
        p.setClosedTime(new Date());
        int rows = baseMapper.updateById(p);
        if (rows == 0) {
            throw new ServiceException("期间数据已变更,关账失败,请重试");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean lockPeriod(Long periodId) {
        GlPeriod p = baseMapper.selectById(periodId);
        if (p == null) {
            throw new ServiceException("期间不存在");
        }
        if (!GlConstants.PERIOD_CLOSED.equals(p.getStatus())) {
            throw new ServiceException("仅已关账期间可锁定,当前【{}】", p.getStatus());
        }
        p.setStatus(GlConstants.PERIOD_LOCKED);
        int rows = baseMapper.updateById(p);
        if (rows == 0) {
            throw new ServiceException("期间数据已变更,锁定失败,请重试");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reopenPeriod(Long periodId) {
        GlPeriod p = baseMapper.selectById(periodId);
        if (p == null) {
            throw new ServiceException("期间不存在");
        }
        if (!GlConstants.PERIOD_CLOSED.equals(p.getStatus())) {
            throw new ServiceException("仅已关账期间可反结账,当前【{}】", p.getStatus());
        }
        p.setStatus(GlConstants.PERIOD_OPEN);
        p.setClosedBy(null);
        p.setClosedTime(null);
        int rows = baseMapper.updateById(p);
        if (rows == 0) {
            throw new ServiceException("期间数据已变更,反结账失败,请重试");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean generateYearPeriods(Integer fiscalYear) {
        for (int month = 1; month <= 12; month++) {
            boolean exists = baseMapper.exists(new LambdaQueryWrapper<GlPeriod>()
                .eq(GlPeriod::getFiscalYear, fiscalYear)
                .eq(GlPeriod::getPeriodNo, month));
            if (exists) {
                continue;
            }
            LocalDate startLd = LocalDate.of(fiscalYear, month, 1);
            LocalDate endLd = startLd.withDayOfMonth(startLd.lengthOfMonth());
            GlPeriod period = new GlPeriod();
            period.setPeriodId(IdGeneratorUtil.nextLongId());
            period.setFiscalYear(fiscalYear);
            period.setPeriodNo(month);
            period.setPeriodName(String.format("%04d-%02d", fiscalYear, month));
            period.setStartDate(toDate(startLd));
            period.setEndDate(toDate(endLd));
            period.setStatus(GlConstants.PERIOD_OPEN);
            period.setVersion(0);
            baseMapper.insert(period);
        }
        return true;
    }

    private LambdaQueryWrapper<GlPeriod> buildQueryWrapper(GlPeriodBo bo) {
        LambdaQueryWrapper<GlPeriod> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getFiscalYear() != null, GlPeriod::getFiscalYear, bo.getFiscalYear());
            lqw.eq(bo.getPeriodNo() != null, GlPeriod::getPeriodNo, bo.getPeriodNo());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), GlPeriod::getStatus, bo.getStatus());
        }
        lqw.orderByDesc(GlPeriod::getFiscalYear);
        lqw.orderByAsc(GlPeriod::getPeriodNo);
        return lqw;
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
