package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdMaterialCategory;

/**
 * 物料产品分类Mapper接口
 *
 * @author nexus
 * @date 2025-10-01
 */
public interface BdMaterialCategoryMapper extends BaseMapper<BdMaterialCategory> {
    /**
     * 查询物料产品分类
     *
     * @param categoryId 物料产品分类主键
     * @return 物料产品分类
     */
    public BdMaterialCategory selectBdMaterialCategoryByCategoryId(Long categoryId);

    /**
     * 查询物料产品分类列表
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 物料产品分类集合
     */
    public List<BdMaterialCategory> selectBdMaterialCategoryList(BdMaterialCategory bdMaterialCategory);

    /**
     * 新增物料产品分类
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 结果
     */
    public int insertBdMaterialCategory(BdMaterialCategory bdMaterialCategory);

    /**
     * 修改物料产品分类
     *
     * @param bdMaterialCategory 物料产品分类
     * @return 结果
     */
    public int updateBdMaterialCategory(BdMaterialCategory bdMaterialCategory);

    /**
     * 删除物料产品分类
     *
     * @param categoryId 物料产品分类主键
     * @return 结果
     */
    public int deleteBdMaterialCategoryByCategoryId(Long categoryId);

    /**
     * 批量删除物料产品分类
     *
     * @param categoryIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdMaterialCategoryByCategoryIds(Long[] categoryIds);
}
