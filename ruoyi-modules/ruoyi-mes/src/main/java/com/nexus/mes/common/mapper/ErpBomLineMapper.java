package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP BOM明细只读Mapper（跨库查询）
 * <p>
 * ERP实际表名为bd_bom_detail（非bd_bom_line），主键为detail_id
 *
 * @author Nexus
 */
@Mapper
public interface ErpBomLineMapper {

    @Select("SELECT detail_id, bom_id, child_material_id, quantity_per_parent, source_type, " +
        "loss_rate, operation_step, workstation_id, process_id, routing_step_id " +
        "FROM bd_bom_detail WHERE bom_id = #{bomId} AND del_flag = '0' ORDER BY operation_step")
    List<Map<String, Object>> selectBomLinesByBomId(@Param("bomId") Long bomId);
}
