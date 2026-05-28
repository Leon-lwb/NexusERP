package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料产品分类Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialCategoryServiceImpl implements IBdMaterialCategoryService {

    private final BdMaterialCategoryMapper baseMapper;

    @Override
    public TableDataInfo<BdMaterialCategoryVo> selectPageList(BdMaterialCategoryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterialCategory> lqw = buildQueryWrapper(bo);
        Page<BdMaterialCategoryVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdMaterialCategoryVo> selectList(BdMaterialCategoryBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdMaterialCategoryVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialCategoryBo bo) {
        BdMaterialCategory entity = MapstructUtils.convert(bo, BdMaterialCategory.class);
        // 如果不是父级节点，则自动赋值path_ids和path_names
        if (entity.getParentId() != null && entity.getParentId() > 0) {
            BdMaterialCategory parentCategory = baseMapper.selectById(entity.getParentId());
            String parentPathIds = parentCategory.getPathIds() != null ? parentCategory.getPathIds() : String.valueOf(parentCategory.getCategoryId());
            String parentPathNames = parentCategory.getPathNames() != null ? parentCategory.getPathNames() : parentCategory.getCategoryName();
            entity.setPathIds(parentPathIds + "," + entity.getCategoryId());
            entity.setPathNames(parentPathNames + "," + entity.getCategoryName());
        } else {
            entity.setPathIds(String.valueOf(entity.getCategoryId()));
            entity.setPathNames(entity.getCategoryName());
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialCategoryBo bo) {
        BdMaterialCategory entity = MapstructUtils.convert(bo, BdMaterialCategory.class);
        if (entity.getParentId() != null && entity.getParentId() > 0) {
            BdMaterialCategory parentCategory = baseMapper.selectById(entity.getParentId());
            String parentPathIds = parentCategory.getPathIds() != null ? parentCategory.getPathIds() : String.valueOf(parentCategory.getCategoryId());
            String parentPathNames = parentCategory.getPathNames() != null ? parentCategory.getPathNames() : parentCategory.getCategoryName();
            entity.setPathIds(parentPathIds + "," + entity.getCategoryId());
            entity.setPathNames(parentPathNames + "," + entity.getCategoryName());
        } else {
            entity.setPathIds(String.valueOf(entity.getCategoryId()));
            entity.setPathNames(entity.getCategoryName());
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<BdMaterialCategory> selectAllChildrenIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return new ArrayList<>();
        }
        QueryWrapper<BdMaterialCategory> queryWrapper = new QueryWrapper<>();
        for (int i = 0; i < categoryIds.length; i++) {
            final Long categoryId = categoryIds[i];
            if (i == 0) {
                queryWrapper.apply("FIND_IN_SET({0}, path_ids)", categoryId)
                        .or()
                        .eq("category_id", categoryId);
            } else {
                queryWrapper.or(wrapper -> wrapper.apply("FIND_IN_SET({0}, path_ids)", categoryId)
                        .or()
                        .eq("category_id", categoryId));
            }
        }
        return baseMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<BdMaterialCategory> buildQueryWrapper(BdMaterialCategoryBo bo) {
        LambdaQueryWrapper<BdMaterialCategory> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getCategoryCode()), BdMaterialCategory::getCategoryCode, bo.getCategoryCode());
        lqw.like(StringUtils.isNotBlank(bo.getCategoryName()), BdMaterialCategory::getCategoryName, bo.getCategoryName());
        lqw.like(StringUtils.isNotBlank(bo.getPathNames()), BdMaterialCategory::getPathNames, bo.getPathNames());
        lqw.eq(bo.getStatus() != null, BdMaterialCategory::getStatus, bo.getStatus());
        lqw.orderByAsc(BdMaterialCategory::getSortOrder);
        return lqw;
    }
}
