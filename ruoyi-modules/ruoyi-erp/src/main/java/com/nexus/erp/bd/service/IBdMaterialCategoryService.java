package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;

import java.util.List;

/**
 * 物料产品分类Service接口
 *
 * @author wanghuan
 */
public interface IBdMaterialCategoryService {

    /**
     * 分页查询物料产品分类列表
     *
     * @param bo 查询参数，包含分类名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料产品分类分页列表
     */
    TableDataInfo<BdMaterialCategoryVo> selectPageList(BdMaterialCategoryBo bo, PageQuery pageQuery);

    /**
     * 查询物料产品分类列表（不分页）
     *
     * @param bo 查询参数，包含分类名称、编码等筛选条件
     * @return 物料产品分类列表
     */
    List<BdMaterialCategoryVo> selectList(BdMaterialCategoryBo bo);

    /**
     * 根据ID查询物料产品分类
     *
     * @param id 分类ID
     * @return 物料产品分类详细信息
     */
    BdMaterialCategoryVo selectById(Long id);

    /**
     * 新增物料产品分类
     *
     * @param bo 物料产品分类业务对象，包含分类名称、编码及上级分类等字段
     * @return 影响行数
     */
    int insertByBo(BdMaterialCategoryBo bo);

    /**
     * 修改物料产品分类
     *
     * @param bo 物料产品分类业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdMaterialCategoryBo bo);

    /**
     * 批量删除物料产品分类
     *
     * @param ids 分类ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 查询指定分类及其所有子分类
     *
     * @param categoryIds 分类ID数组
     * @return 包含子分类的分类列表
     */
    List<BdMaterialCategory> selectAllChildrenIds(Long[] categoryIds);
}
