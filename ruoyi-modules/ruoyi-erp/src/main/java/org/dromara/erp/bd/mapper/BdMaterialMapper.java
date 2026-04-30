package org.dromara.erp.bd.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;

import java.util.List;

public interface BdMaterialMapper extends BaseMapperPlus<BdMaterial, BdMaterialVo> {

    List<BdMaterialVo> selectByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
}
