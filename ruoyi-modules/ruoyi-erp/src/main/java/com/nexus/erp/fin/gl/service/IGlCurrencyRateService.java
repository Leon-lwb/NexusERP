package com.nexus.erp.fin.gl.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.bo.GlCurrencyRateBo;
import com.nexus.erp.fin.gl.domain.vo.GlCurrencyRateVo;

import java.util.List;

/**
 * 币种汇率 Service
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlCurrencyRateService {

    TableDataInfo<GlCurrencyRateVo> queryPageList(GlCurrencyRateBo bo, PageQuery pageQuery);

    List<GlCurrencyRateVo> queryList(GlCurrencyRateBo bo);

    GlCurrencyRateVo queryById(Long rateId);

    Boolean insertByBo(GlCurrencyRateBo bo);

    Boolean updateByBo(GlCurrencyRateBo bo);

    Boolean deleteByIds(Long[] rateIds);
}
