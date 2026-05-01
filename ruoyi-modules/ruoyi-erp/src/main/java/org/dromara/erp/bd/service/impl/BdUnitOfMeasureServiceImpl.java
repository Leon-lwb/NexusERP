package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdUnitOfMeasure;
import org.dromara.erp.bd.domain.bo.BdUnitOfMeasureBo;
import org.dromara.erp.bd.domain.vo.BdUnitOfMeasureVo;
import org.dromara.erp.bd.mapper.BdUnitOfMeasureMapper;
import org.dromara.erp.bd.service.IBdUnitOfMeasureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class BdUnitOfMeasureServiceImpl implements IBdUnitOfMeasureService {

    private final BdUnitOfMeasureMapper baseMapper;

    @Override
    public TableDataInfo<BdUnitOfMeasureVo> selectPageList(BdUnitOfMeasureBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdUnitOfMeasure> lqw = buildQueryWrapper(bo);
        Page<BdUnitOfMeasureVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdUnitOfMeasureVo> selectList(BdUnitOfMeasureBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdUnitOfMeasureVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdUnitOfMeasureBo bo) {
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdUnitOfMeasureBo bo) {
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdUnitOfMeasure> buildQueryWrapper(BdUnitOfMeasureBo bo) {
        LambdaQueryWrapper<BdUnitOfMeasure> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getUomCode()), BdUnitOfMeasure::getUomCode, bo.getUomCode());
        lqw.like(StringUtils.isNotBlank(bo.getUomName()), BdUnitOfMeasure::getUomName, bo.getUomName());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), BdUnitOfMeasure::getCategory, bo.getCategory());
        lqw.eq(bo.getStatus() != null, BdUnitOfMeasure::getStatus, bo.getStatus());
        lqw.orderByAsc(BdUnitOfMeasure::getSortOrder);
        return lqw;
    }
}
