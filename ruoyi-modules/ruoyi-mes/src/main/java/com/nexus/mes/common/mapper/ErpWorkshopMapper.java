package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP车间只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpWorkshopMapper {

    @Select("SELECT workshop_id, workshop_code, workshop_name, workshop_desc, " +
        "manager_id, manager_name, capacity, status " +
        "FROM bd_workshop WHERE workshop_id = #{workshopId} AND del_flag = '0'")
    Map<String, Object> selectWorkshopById(@Param("workshopId") Long workshopId);
}
