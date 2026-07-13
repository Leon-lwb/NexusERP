package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
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

    /**
     * 查询工作中心列表(供设备选择器使用, 仅启用的)
     *
     * @param keyword 编码/名称关键字(可空)
     */
    @Select("<script>" +
        "SELECT workstation_id, workstation_code, workstation_name, workshop_code, workshop_name, status " +
        "FROM bd_workstation WHERE del_flag = '0' AND status = '1' " +
        "<if test='keyword != null and keyword != \"\"'>" +
        "AND (workstation_code LIKE CONCAT('%',#{keyword},'%') OR workstation_name LIKE CONCAT('%',#{keyword},'%')) " +
        "</if>" +
        "ORDER BY workstation_code LIMIT 200" +
        "</script>")
    List<Map<String, Object>> selectWorkstationList(@Param("keyword") String keyword);
}
