package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ERP计量单位只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpUnitMapper {

    @Select("SELECT uom_name FROM bd_unit_of_measure WHERE uom_id = #{unitId} AND del_flag = '0'")
    String selectUnitNameById(@Param("unitId") Long unitId);
}
