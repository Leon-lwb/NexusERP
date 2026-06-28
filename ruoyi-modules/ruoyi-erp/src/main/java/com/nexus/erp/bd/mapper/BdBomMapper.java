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
            " AND is_current = 1 AND status = 1 AND del_flag = '0'" +
            "</script>")
    List<BdBom> findCurrentBomsByMaterialIds(@Param("materialIds") Collection<Long> materialIds);

    /**
     * 批量判断给定 BOM 列表中哪些存在子 BOM（用于列表树形展开箭头显示）
     * 子BOM定义：BOM明细的 child_material_id 在 bd_bom 表中作为 parent_material_id 存在当前版本记录
     *
     * @param bomIds BOM主键集合
     * @return 存在子BOM的 bomId 列表
     */
    @Select("<script>" +
            "SELECT DISTINCT d.bom_id FROM bd_bom_detail d " +
            "WHERE d.del_flag = '0' " +
            "AND d.bom_id IN " +
            "<foreach item='id' collection='bomIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND EXISTS (" +
            "  SELECT 1 FROM bd_bom b " +
            "  WHERE b.parent_material_id = d.child_material_id " +
            "  AND b.is_current = 1 AND b.del_flag = '0'" +
            ")" +
            "</script>")
    List<Long> findBomIdsWithChildren(@Param("bomIds") Collection<Long> bomIds);
}
