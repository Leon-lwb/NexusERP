package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
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
}
