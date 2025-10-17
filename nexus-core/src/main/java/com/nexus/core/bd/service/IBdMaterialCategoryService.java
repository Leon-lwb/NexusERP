package com.nexus.core.bd.service;

import java.util.List;
import com.nexus.core.bd.domain.BdMaterialCategory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.core.bd.domain.vo.BdMaterialCategoryVo;

/**
 * 物料产品分类Service接口
 *
 * @author nexus
 * @date 2025-10-01
 */
public interface IBdMaterialCategoryService extends IService<BdMaterialCategory> {
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
     * 批量删除物料产品分类
     *
     * @param categoryIds 需要删除的物料产品分类主键集合
     * @return 结果
     */
    public int deleteBdMaterialCategoryByCategoryIds(Long[] categoryIds);

    /**
     * 删除物料产品分类信息
     *
     * @param categoryId 物料产品分类主键
     * @return 结果
     */
    public int deleteBdMaterialCategoryByCategoryId(Long categoryId);

    /**
     * 支持接收 categoryIds 参数并进行 IN 查询
     *
     * @param categoryIds
     * @return
     */
    List<BdMaterialCategory> selectAllChildrenIds(Long[] categoryIds);
}
