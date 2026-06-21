package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP工艺路线头只读Mapper（跨库查询）
 */
@Mapper
public interface ErpRoutingHeaderMapper {

    @Select("SELECT r.routing_id, r.routing_code, r.routing_name, r.routing_version, " +
        "r.material_id, r.is_current, r.total_steps, r.total_hours, r.status, " +
        "m.material_code, m.material_name " +
        "FROM bd_routing r LEFT JOIN bd_material m ON r.material_id = m.material_id " +
        "WHERE r.routing_id = #{routingId} AND r.del_flag = '0'")
    Map<String, Object> selectRoutingHeaderById(@Param("routingId") Long routingId);
}
