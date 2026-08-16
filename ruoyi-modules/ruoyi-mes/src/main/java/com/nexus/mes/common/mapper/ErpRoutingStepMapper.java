package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP工艺路线步骤只读Mapper（跨库查询）
 * <p>
 * ERP实际表名为bd_routing_step，主键为step_id（非routing_step_id），工作中心字段为workstation_id（非workcenter_id）
 *
 * @author Nexus
 */
@Mapper
public interface ErpRoutingStepMapper {

    @Select("SELECT step_id, routing_id, process_id, process_code, process_name, " +
        "sequence_num, is_key_process, workstation_id, workstation_code, " +
        "firmware_id, firmware_code, firmware_name " +
        "FROM bd_routing_step WHERE routing_id = #{routingId} ORDER BY sequence_num")
    List<Map<String, Object>> selectRoutingStepsByRoutingId(@Param("routingId") Long routingId);
}
