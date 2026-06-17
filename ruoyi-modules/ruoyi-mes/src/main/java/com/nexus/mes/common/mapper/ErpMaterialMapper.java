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
        "base_uom_id, reference_price FROM bd_material WHERE material_id = #{materialId} AND del_flag = '0'")
    Map<String, Object> selectMaterialById(@Param("materialId") Long materialId);

    /**
     * 查询物料的默认工艺路线ID
     * <p>
     * bd_material表无default_routing_id字段，通过bd_routing表查询:
     * material_id匹配且is_current=1且status=1的当前版本工艺路线
     */
    @Select("SELECT routing_id FROM bd_routing " +
        "WHERE material_id = #{materialId} AND is_current = 1 AND status = 1 AND del_flag = '0' " +
        "ORDER BY create_time DESC LIMIT 1")
    Long selectDefaultRoutingId(@Param("materialId") Long materialId);
}
