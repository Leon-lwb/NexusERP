package com.nexus.erp.bd.mapper;

import org.apache.ibatis.annotations.Param;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;

import java.util.List;

/**
 * 物料产品主Mapper接口
 *
 * @author wanghuan
 */
public interface BdMaterialMapper extends BaseMapperPlus<BdMaterial, BdMaterialVo> {

    List<BdMaterialVo> selectByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
}
