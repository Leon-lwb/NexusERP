package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.bo.BdUnitOfMeasureBo;
import com.nexus.erp.bd.domain.vo.BdUnitOfMeasureVo;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.bd.service.IBdUnitOfMeasureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 计量单位Service业务层处理
 *
 * @author wanghuan
 */
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
    public List<BdUnitOfMeasureVo> selectByPrimaryId(Long primaryId) {
        return baseMapper.selectVoList(
            Wrappers.<BdUnitOfMeasure>lambdaQuery()
                .eq(BdUnitOfMeasure::getPrimaryId, primaryId)
                .orderByAsc(BdUnitOfMeasure::getSortOrder)
        );
    }

    @Override
    public List<String> selectCategories() {
        return baseMapper.selectObjs(
            new LambdaQueryWrapper<BdUnitOfMeasure>()
                .select(BdUnitOfMeasure::getCategory)
                .isNotNull(BdUnitOfMeasure::getCategory)
                .groupBy(BdUnitOfMeasure::getCategory)
                .orderByAsc(BdUnitOfMeasure::getCategory)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdUnitOfMeasureBo bo) {
        // 自动回填基本单位名称
        if (bo.getPrimaryId() != null && StringUtils.isBlank(bo.getPrimaryName())) {
            BdUnitOfMeasureVo primary = baseMapper.selectVoById(bo.getPrimaryId());
            if (primary != null) {
                bo.setPrimaryName(primary.getUomName());
            }
        }
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdUnitOfMeasureBo bo) {
        // 自动回填基本单位名称
        if (bo.getPrimaryId() != null && StringUtils.isBlank(bo.getPrimaryName())) {
            BdUnitOfMeasureVo primary = baseMapper.selectVoById(bo.getPrimaryId());
            if (primary != null) {
                bo.setPrimaryName(primary.getUomName());
            }
        }
        // 如果是基本单位，清空关联字段
        if (Long.valueOf(1).equals(bo.getIsBase())) {
            bo.setPrimaryId(null);
            bo.setPrimaryName(null);
            bo.setConversionRate(null);
        }
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 校验是否有衍生单位引用待删除的记录
        for (Long id : ids) {
            BdUnitOfMeasureVo unit = baseMapper.selectVoById(id);
            if (unit != null && Long.valueOf(1).equals(unit.getIsBase())) {
                boolean hasChildren = baseMapper.exists(
                    Wrappers.<BdUnitOfMeasure>lambdaQuery()
                        .eq(BdUnitOfMeasure::getPrimaryId, id)
                );
                if (hasChildren) {
                    throw new ServiceException("基本单位「" + unit.getUomName() + "」已被其他单位引用，无法删除");
                }
            }
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdUnitOfMeasure> buildQueryWrapper(BdUnitOfMeasureBo bo) {
        LambdaQueryWrapper<BdUnitOfMeasure> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getUomCode()), BdUnitOfMeasure::getUomCode, bo.getUomCode());
        lqw.like(StringUtils.isNotBlank(bo.getUomName()), BdUnitOfMeasure::getUomName, bo.getUomName());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), BdUnitOfMeasure::getCategory, bo.getCategory());
        lqw.eq(bo.getStatus() != null, BdUnitOfMeasure::getStatus, bo.getStatus());
        lqw.eq(bo.getIsBase() != null, BdUnitOfMeasure::getIsBase, bo.getIsBase());
        lqw.orderByAsc(BdUnitOfMeasure::getSortOrder);
        return lqw;
    }
}
