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
    TableDataInfo<BdMaterialCategoryVo> selectPageList(BdMaterialCategoryBo bo, PageQuery pageQuery);
    List<BdMaterialCategoryVo> selectList(BdMaterialCategoryBo bo);
    BdMaterialCategoryVo selectById(Long id);
    int insertByBo(BdMaterialCategoryBo bo);
    int updateByBo(BdMaterialCategoryBo bo);
    int deleteByIds(Long[] ids);
    List<BdMaterialCategory> selectAllChildrenIds(Long[] categoryIds);
}
