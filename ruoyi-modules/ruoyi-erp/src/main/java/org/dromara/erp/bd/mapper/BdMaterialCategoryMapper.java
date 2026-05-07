package org.dromara.erp.bd.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.bd.domain.BdMaterialCategory;
import org.dromara.erp.bd.domain.vo.BdMaterialCategoryVo;

import java.util.List;

/**
 * 物料产品分类Mapper接口
 *
 * @author wanghuan
 */
public interface BdMaterialCategoryMapper extends BaseMapperPlus<BdMaterialCategory, BdMaterialCategoryVo> {

    List<BdMaterialCategory> selectAllChildrenIds(@Param("categoryIds") Long[] categoryIds);
}
