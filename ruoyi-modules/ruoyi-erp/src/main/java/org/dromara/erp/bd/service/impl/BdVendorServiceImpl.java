package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdVendor;
import org.dromara.erp.bd.domain.bo.BdVendorBo;
import org.dromara.erp.bd.domain.vo.BdVendorVo;
import org.dromara.erp.bd.mapper.BdVendorMapper;
import org.dromara.erp.bd.service.IBdVendorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 供应商信息Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdVendorServiceImpl implements IBdVendorService {

    private final BdVendorMapper baseMapper;

    @Override
    public TableDataInfo<BdVendorVo> selectPageList(BdVendorBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdVendor> lqw = buildQueryWrapper(bo);
        Page<BdVendorVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdVendorVo> selectList(BdVendorBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdVendorVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdVendorBo bo) {
        BdVendor entity = MapstructUtils.convert(bo, BdVendor.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdVendorBo bo) {
        BdVendor entity = MapstructUtils.convert(bo, BdVendor.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdVendor> buildQueryWrapper(BdVendorBo bo) {
        LambdaQueryWrapper<BdVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), BdVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdVendor::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getIndustry()), BdVendor::getIndustry, bo.getIndustry());
        lqw.eq(StringUtils.isNotBlank(bo.getRegion()), BdVendor::getRegion, bo.getRegion());
        return lqw;
    }
}
