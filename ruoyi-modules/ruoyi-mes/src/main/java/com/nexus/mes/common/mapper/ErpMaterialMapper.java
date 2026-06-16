package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP物料只读Mapper（跨库查询）
 * <p>
 * 不加@DS注解，由MesErpBridgeServiceImpl通过DynamicDataSourceContextHolder编程式切换数据源
 *
 * @author Nexus
 */
@Mapper
public interface ErpMaterialMapper {

    @Select("SELECT material_id, material_code, material_name, material_spec, category_id, " +
        "unit_id, reference_price FROM bd_material WHERE material_id = #{materialId}")
    Map<String, Object> selectMaterialById(@Param("materialId") Long materialId);

    @Select("SELECT default_routing_id FROM bd_material WHERE material_id = #{materialId}")
    Long selectDefaultRoutingId(@Param("materialId") Long materialId);
}
