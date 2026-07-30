package com.nexus.erp.fin.gl.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.GlCurrencyRate;
import com.nexus.erp.fin.gl.domain.bo.GlCurrencyRateBo;
import com.nexus.erp.fin.gl.domain.vo.GlCurrencyRateVo;
import com.nexus.erp.fin.gl.mapper.GlCurrencyRateMapper;
import com.nexus.erp.fin.gl.service.IGlCurrencyRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 币种汇率 Service 实现
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class GlCurrencyRateServiceImpl implements IGlCurrencyRateService {

    private final GlCurrencyRateMapper baseMapper;

    @Override
    public TableDataInfo<GlCurrencyRateVo> queryPageList(GlCurrencyRateBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<GlCurrencyRate> lqw = buildQueryWrapper(bo);
        Page<GlCurrencyRateVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<GlCurrencyRateVo> queryList(GlCurrencyRateBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public GlCurrencyRateVo queryById(Long rateId) {
        return baseMapper.selectVoById(rateId);
    }

    @Override
    public Boolean insertByBo(GlCurrencyRateBo bo) {
        GlCurrencyRate entity = MapstructUtils.convert(bo, GlCurrencyRate.class);
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public Boolean updateByBo(GlCurrencyRateBo bo) {
        GlCurrencyRate entity = MapstructUtils.convert(bo, GlCurrencyRate.class);
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public Boolean deleteByIds(Long[] rateIds) {
        return baseMapper.deleteBatchIds(Arrays.asList(rateIds)) > 0;
    }

    private LambdaQueryWrapper<GlCurrencyRate> buildQueryWrapper(GlCurrencyRateBo bo) {
        LambdaQueryWrapper<GlCurrencyRate> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getCurrency() != null && !bo.getCurrency().isEmpty(), GlCurrencyRate::getCurrency, bo.getCurrency());
            lqw.eq(bo.getRateType() != null && !bo.getRateType().isEmpty(), GlCurrencyRate::getRateType, bo.getRateType());
            lqw.orderByDesc(GlCurrencyRate::getRateDate);
        }
        return lqw;
    }
}
