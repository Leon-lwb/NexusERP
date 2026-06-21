package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP BOM头只读Mapper（跨库查询）
 */
@Mapper
public interface ErpBomHeaderMapper {

    @Select("SELECT b.bom_id, b.bom_version, b.bom_name, b.bom_type, b.routing_id, b.is_current, b.status, " +
        "m.material_id AS parent_material_id, m.material_code AS parent_material_code, m.material_name AS parent_material_name " +
        "FROM bd_bom b LEFT JOIN bd_material m ON b.parent_material_id = m.material_id " +
        "WHERE b.bom_id = #{bomId} AND b.del_flag = '0'")
    Map<String, Object> selectBomHeaderById(@Param("bomId") Long bomId);
}
