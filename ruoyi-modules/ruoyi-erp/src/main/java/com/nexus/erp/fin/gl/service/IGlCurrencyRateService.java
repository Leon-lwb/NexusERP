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

    /**
     * 分页查询币种汇率
     *
     * @param bo         查询条件(币种/汇率类型)
     * @param pageQuery  分页参数
     * @return 汇率分页数据
     */
    TableDataInfo<GlCurrencyRateVo> queryPageList(GlCurrencyRateBo bo, PageQuery pageQuery);

    /**
     * 查询币种汇率列表
     *
     * @param bo 查询条件(币种/汇率类型)
     * @return 汇率列表
     */
    List<GlCurrencyRateVo> queryList(GlCurrencyRateBo bo);

    /**
     * 根据主键查询币种汇率
     *
     * @param rateId 汇率ID
     * @return 汇率VO
     */
    GlCurrencyRateVo queryById(Long rateId);

    /**
     * 新增币种汇率
     *
     * @param bo 汇率业务对象
     * @return 成功/失败
     */
    Boolean insertByBo(GlCurrencyRateBo bo);

    /**
     * 修改币种汇率
     *
     * @param bo 汇率业务对象
     * @return 成功/失败
     */
    Boolean updateByBo(GlCurrencyRateBo bo);

    /**
     * 删除币种汇率
     *
     * @param rateIds 汇率ID数组
     * @return 成功/失败
     */
    Boolean deleteByIds(Long[] rateIds);
}
