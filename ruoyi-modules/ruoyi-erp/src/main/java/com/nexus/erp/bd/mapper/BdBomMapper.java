package com.nexus.erp.bd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * BOMMapper接口
 *
 * @author wanghuan
 */
public interface BdBomMapper extends BaseMapperPlus<BdBom, BdBomVo> {

    /**
     * 按物料ID集合批量查当前有效 BOM (消除 findCurrentBom 的 N+1)
     */
    @Select("<script>" +
            "SELECT * FROM bd_bom WHERE parent_material_id IN " +
            "<foreach item='id' collection='materialIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND is_current = 1 AND status = 1" +
            "</script>")
    List<BdBom> findCurrentBomsByMaterialIds(@Param("materialIds") Collection<Long> materialIds);
}
