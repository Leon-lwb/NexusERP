package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
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
     * 查询分类列表，排除指定分类及其所有子孙（用于父级下拉防循环）
     *
     * @param excludeIds 需排除的分类ID数组
     * @return 排除后的分类列表
     */
    List<BdMaterialCategoryVo> selectListExcludeChild(Long[] excludeIds);

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
    List<BdMaterialCategoryVo> selectAllChildrenIds(Long[] categoryIds);

    /**
     * 校验分类编码是否唯一
     *
     * @param bo 分类信息
     * @return 结果 true 唯一 false 不唯一
     */
    boolean checkCategoryCodeUnique(BdMaterialCategoryBo bo);

    /**
     * 是否存在子分类
     *
     * @param categoryIds 分类ID数组
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByCategoryIds(Long[] categoryIds);

    /**
     * 填充各分类的直系物料数量（实时查询，不进缓存）。
     * <p>因物料增删不触发分类缓存失效，物料数须每次实时填充，避免缓存陈旧。
     *
     * @param list 分类VO列表（将被就地设置 materialCount）
     */
    void fillMaterialCount(List<BdMaterialCategoryVo> list);

    /**
     * 修改分类状态（轻量更新，不走 path 级联）
     *
     * @param categoryId 分类ID
     * @param status     目标状态 0:停用 1:启用
     * @return 影响行数
     */
    int updateStatus(Long categoryId, Long status);

    /**
     * 移动分类到新父级（拖拽改父级）。复用 path 级联刷新与循环引用校验，
     * sortOrder 自动追加到新父级现有子级末尾。
     *
     * @param categoryId  被移动分类ID
     * @param newParentId 新父分类ID（0=顶级）
     * @return 影响行数
     */
    int moveCategory(Long categoryId, Long newParentId);
}
