package com.nexus.erp.bd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 物料产品主Mapper接口
 *
 * @author wanghuan
 */
public interface BdMaterialMapper extends BaseMapperPlus<BdMaterial, BdMaterialVo> {
    /** 在实际ERP事务中锁定物料，防止策略编辑与身份预留交错。 */
    @Select(
            "SELECT * FROM bd_material WHERE tenant_id=#{tenant} AND material_id=#{id} AND"
                    + " del_flag='0' FOR UPDATE")
    BdMaterial lockTracking(@Param("tenant") String tenant, @Param("id") Long id);
}
