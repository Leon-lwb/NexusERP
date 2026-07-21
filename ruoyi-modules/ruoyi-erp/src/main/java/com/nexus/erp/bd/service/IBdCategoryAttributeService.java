package com.nexus.erp.bd.service;

import com.nexus.erp.bd.domain.bo.BdCategoryAttributeBo;
import com.nexus.erp.bd.domain.vo.BdCategoryAttributeVo;

import java.util.List;

/**
 * 物料分类属性模板Service接口
 *
 * @author nexus
 */
public interface IBdCategoryAttributeService {

    /**
     * 按分类查询属性模板列表
     */
    List<BdCategoryAttributeVo> selectListByCategory(Long categoryId);

    /**
     * 根据ID查询属性模板
     */
    BdCategoryAttributeVo selectById(Long attrId);

    /**
     * 新增属性模板
     */
    int insertByBo(BdCategoryAttributeBo bo);

    /**
     * 修改属性模板
     */
    int updateByBo(BdCategoryAttributeBo bo);

    /**
     * 批量删除属性模板
     */
    int deleteByIds(Long[] ids);

    /**
     * 校验属性编码在分类内是否唯一
     */
    boolean checkAttrCodeUnique(BdCategoryAttributeBo bo);
}
