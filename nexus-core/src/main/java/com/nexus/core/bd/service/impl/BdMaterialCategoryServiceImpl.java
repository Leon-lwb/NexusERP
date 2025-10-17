package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.*;
import java.util.stream.Collectors;

import com.nexus.common.utils.DateUtils;
import com.nexus.core.bd.domain.vo.BdMaterialCategoryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.core.bd.domain.BdMaterialCategory;
import com.nexus.core.bd.service.IBdMaterialCategoryService;

/**
 * 物料产品分类Service业务层处理
 *
 * @author nexus
 * @date 2025-10-01
 */
@Service
public class BdMaterialCategoryServiceImpl extends ServiceImpl<BdMaterialCategoryMapper, BdMaterialCategory> implements IBdMaterialCategoryService {

    @Autowired
    private BdMaterialCategoryMapper bdMaterialCategoryMapper;

    /**
     * 查询物料产品分类
     *
     * @param categoryId 物料产品分类主键
     * @return 物料产品分类
     */
    @Override
    public BdMaterialCategory selectBdMaterialCategoryByCategoryId(Long categoryId) {
        return bdMaterialCategoryMapper.selectBdMaterialCategoryByCategoryId(categoryId);
    }

    /**
     * 查询物料产品分类列表
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 物料产品分类
     */
    @Override
    public List<BdMaterialCategory> selectBdMaterialCategoryList(BdMaterialCategory bdMaterialCategory) {
        return bdMaterialCategoryMapper.selectBdMaterialCategoryList(bdMaterialCategory);
    }

    /**
     * 新增物料产品分类
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 结果
     */
    @Override
    public int insertBdMaterialCategory(BdMaterialCategory bdMaterialCategory) {
        bdMaterialCategory.setCreateTime(DateUtils.getNowDate());
        //如果不是父级节点，则自动赋值path_ids和path_names
        if (bdMaterialCategory.getParentId() != null && bdMaterialCategory.getParentId() > 0) {
            BdMaterialCategory parentCategory = bdMaterialCategoryMapper.selectBdMaterialCategoryByCategoryId(bdMaterialCategory.getParentId());
            // 处理父级分类pathIds或pathNames为null的情况
            String parentPathIds = parentCategory.getPathIds() != null ? parentCategory.getPathIds() : String.valueOf(parentCategory.getCategoryId());
            String parentPathNames = parentCategory.getPathNames() != null ? parentCategory.getPathNames() : parentCategory.getCategoryName();

            bdMaterialCategory.setPathIds(parentPathIds + "," + bdMaterialCategory.getCategoryId());
            bdMaterialCategory.setPathNames(parentPathNames + "," + bdMaterialCategory.getCategoryName());
        } else {
            // 父级分类初始化path_ids和path_names为自身
            bdMaterialCategory.setPathIds(String.valueOf(bdMaterialCategory.getCategoryId()));
            bdMaterialCategory.setPathNames(bdMaterialCategory.getCategoryName());
        }
        return bdMaterialCategoryMapper.insertBdMaterialCategory(bdMaterialCategory);
    }


    /**
     * 修改物料产品分类
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 结果
     */
    @Override
    public int updateBdMaterialCategory(BdMaterialCategory bdMaterialCategory) {
        bdMaterialCategory.setUpdateTime(DateUtils.getNowDate());
        //如果不是父级节点，则自动赋值path_ids和path_names
        if (bdMaterialCategory.getParentId() != null && bdMaterialCategory.getParentId() > 0) {
            BdMaterialCategory parentCategory = bdMaterialCategoryMapper.selectBdMaterialCategoryByCategoryId(bdMaterialCategory.getParentId());
            // 处理父级分类pathIds或pathNames为null的情况
            String parentPathIds = parentCategory.getPathIds() != null ? parentCategory.getPathIds() : String.valueOf(parentCategory.getCategoryId());
            String parentPathNames = parentCategory.getPathNames() != null ? parentCategory.getPathNames() : parentCategory.getCategoryName();

            bdMaterialCategory.setPathIds(parentPathIds + "," + bdMaterialCategory.getCategoryId());
            bdMaterialCategory.setPathNames(parentPathNames + "," + bdMaterialCategory.getCategoryName());
        } else {
            // 父级分类初始化path_ids和path_names为自身
            bdMaterialCategory.setPathIds(String.valueOf(bdMaterialCategory.getCategoryId()));
            bdMaterialCategory.setPathNames(bdMaterialCategory.getCategoryName());
        }
        return bdMaterialCategoryMapper.updateBdMaterialCategory(bdMaterialCategory);
    }


    /**
     * 批量删除物料产品分类
     *
     * @param categoryIds 需要删除的物料产品分类主键
     * @return 结果
     */
    @Override
    public int deleteBdMaterialCategoryByCategoryIds(Long[] categoryIds) {
        return bdMaterialCategoryMapper.deleteBdMaterialCategoryByCategoryIds(categoryIds);
    }

    /**
     * 删除物料产品分类信息
     *
     * @param categoryId 物料产品分类主键
     * @return 结果
     */
    @Override
    public int deleteBdMaterialCategoryByCategoryId(Long categoryId) {
        return bdMaterialCategoryMapper.deleteBdMaterialCategoryByCategoryId(categoryId);
    }

    /**
     * 查询所有子分类
     *
     * @param categoryIds 分类ID数组
     * @return 子分类列表
     */
    @Override
    public List<BdMaterialCategory> selectAllChildrenIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return new ArrayList<>();
        }

        QueryWrapper<BdMaterialCategory> queryWrapper = new QueryWrapper<>();

        for (int i = 0; i < categoryIds.length; i++) {
            final Long categoryId = categoryIds[i]; // 将 categoryIds[i] 赋值给 final 变量

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

        return this.list(queryWrapper);
    }



}
