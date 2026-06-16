package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP BOM行只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpBomLineMapper {

    @Select("SELECT bom_line_id, bom_id, material_id, material_code, material_name, " +
        "quantity, unit_id, sequence_num FROM bd_bom_line WHERE bom_id = #{bomId} ORDER BY sequence_num")
    List<Map<String, Object>> selectBomLinesByBomId(@Param("bomId") Long bomId);
}
