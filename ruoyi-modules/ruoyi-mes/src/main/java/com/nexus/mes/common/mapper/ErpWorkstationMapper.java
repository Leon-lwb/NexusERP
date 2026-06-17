package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP工作站只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpWorkstationMapper {

    @Select("SELECT workstation_id, workstation_code, workstation_name, " +
        "workshop_id, workshop_code, workshop_name, equipment_id, " +
        "operator_count, standard_capacity, status " +
        "FROM bd_workstation WHERE workstation_id = #{workstationId} AND del_flag = '0'")
    Map<String, Object> selectWorkstationById(@Param("workstationId") Long workstationId);
}
