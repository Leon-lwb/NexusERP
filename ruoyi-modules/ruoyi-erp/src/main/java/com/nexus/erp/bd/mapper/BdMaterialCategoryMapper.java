package com.nexus.erp.bd.mapper;

import org.apache.ibatis.annotations.Param;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;

import java.util.List;

/**
 * 物料产品分类Mapper接口
 *
 * @author wanghuan
 */
public interface BdMaterialCategoryMapper extends BaseMapperPlus<BdMaterialCategory, BdMaterialCategoryVo> {

    /**
     * 查询指定分类及其所有子分类
     *
     * @param categoryIds 分类ID数组
     * @return 包含子分类的分类列表
     */
    List<BdMaterialCategory> selectAllChildrenIds(@Param("categoryIds") Long[] categoryIds);
}
