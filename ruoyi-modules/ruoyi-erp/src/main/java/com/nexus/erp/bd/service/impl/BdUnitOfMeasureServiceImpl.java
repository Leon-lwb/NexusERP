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
import java.util.Set;
import java.util.stream.Collectors;

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
        // 校验编码唯一性
        checkUomCodeUnique(bo);
        // 自动回填基本单位名称
        fillPrimaryName(bo);
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdUnitOfMeasureBo bo) {
        // 校验编码唯一性
        checkUomCodeUnique(bo);
        // 自动回填基本单位名称
        fillPrimaryName(bo);
        // 如果是基本单位，清空关联字段
        if (Long.valueOf(1).equals(bo.getIsBase())) {
            bo.setPrimaryId(null);
            bo.setPrimaryName(null);
            bo.setConversionRate(null);
        }
        // 如果基本单位名称发生变化，同步更新衍生单位的 primary_name
        syncPrimaryNameIfChanged(bo);
        BdUnitOfMeasure entity = MapstructUtils.convert(bo, BdUnitOfMeasure.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 一次性查询所有待删除记录
        List<BdUnitOfMeasure> units = baseMapper.selectBatchIds(List.of(ids));
        if (units.isEmpty()) {
            return 0;
        }

        // 分离出基本单位ID
        List<Long> baseUnitIds = units.stream()
            .filter(u -> Long.valueOf(1).equals(u.getIsBase()))
            .map(BdUnitOfMeasure::getUomId)
            .toList();

        if (!baseUnitIds.isEmpty()) {
            // 一次性查询哪些基本单位被引用
            List<BdUnitOfMeasure> children = baseMapper.selectList(
                Wrappers.<BdUnitOfMeasure>lambdaQuery()
                    .in(BdUnitOfMeasure::getPrimaryId, baseUnitIds)
            );

            if (!children.isEmpty()) {
                Set<Long> referencedIds = children.stream()
                    .map(BdUnitOfMeasure::getPrimaryId)
                    .collect(Collectors.toSet());
                String names = units.stream()
                    .filter(u -> referencedIds.contains(u.getUomId()))
                    .map(BdUnitOfMeasure::getUomName)
                    .collect(Collectors.joining("、"));
                throw new ServiceException("基本单位「" + names + "」已被其他单位引用，无法删除");
            }
        }

        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 校验单位编码唯一性
     */
    private void checkUomCodeUnique(BdUnitOfMeasureBo bo) {
        if (StringUtils.isBlank(bo.getUomCode())) {
            return;
        }
        boolean exists = baseMapper.exists(
            Wrappers.<BdUnitOfMeasure>lambdaQuery()
                .eq(BdUnitOfMeasure::getUomCode, bo.getUomCode())
                .ne(bo.getUomId() != null, BdUnitOfMeasure::getUomId, bo.getUomId())
        );
        if (exists) {
            throw new ServiceException("单位编码「" + bo.getUomCode() + "」已存在");
        }
    }

    /**
     * 自动回填基本单位名称
     */
    private void fillPrimaryName(BdUnitOfMeasureBo bo) {
        if (bo.getPrimaryId() != null && StringUtils.isBlank(bo.getPrimaryName())) {
            BdUnitOfMeasureVo primary = baseMapper.selectVoById(bo.getPrimaryId());
            if (primary != null) {
                bo.setPrimaryName(primary.getUomName());
            }
        }
    }

    /**
     * 如果基本单位名称发生变化，同步更新所有衍生单位的 primary_name
     */
    private void syncPrimaryNameIfChanged(BdUnitOfMeasureBo bo) {
        if (bo.getUomId() == null || !Long.valueOf(1).equals(bo.getIsBase())) {
            return;
        }
        BdUnitOfMeasure existing = baseMapper.selectById(bo.getUomId());
        if (existing == null) {
            return;
        }
        if (StringUtils.isNotBlank(bo.getUomName()) && !bo.getUomName().equals(existing.getUomName())) {
            baseMapper.update(null,
                Wrappers.<BdUnitOfMeasure>lambdaUpdate()
                    .set(BdUnitOfMeasure::getPrimaryName, bo.getUomName())
                    .eq(BdUnitOfMeasure::getPrimaryId, bo.getUomId())
            );
        }
    }

    private LambdaQueryWrapper<BdUnitOfMeasure> buildQueryWrapper(BdUnitOfMeasureBo bo) {
        LambdaQueryWrapper<BdUnitOfMeasure> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getUomCode()), BdUnitOfMeasure::getUomCode, bo.getUomCode());
        lqw.like(StringUtils.isNotBlank(bo.getUomName()), BdUnitOfMeasure::getUomName, bo.getUomName());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), BdUnitOfMeasure::getCategory, bo.getCategory());
        lqw.eq(bo.getStatus() != null, BdUnitOfMeasure::getStatus, bo.getStatus());
        lqw.eq(bo.getIsBase() != null, BdUnitOfMeasure::getIsBase, bo.getIsBase());
        lqw.ne(bo.getExcludeId() != null, BdUnitOfMeasure::getUomId, bo.getExcludeId());
        lqw.orderByAsc(BdUnitOfMeasure::getSortOrder);
        return lqw;
    }
}
