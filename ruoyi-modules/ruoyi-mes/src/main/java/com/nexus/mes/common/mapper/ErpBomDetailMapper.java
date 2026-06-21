package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP BOM明细只读Mapper（跨库查询）
 * <p>
 * 替代已删除的 ErpBomLineMapper（其引用的 process_id/routing_step_id/workstation_id 已从 bd_bom_detail 移除）。
 * 仅查询纯物料+用量+成本字段。
 */
@Mapper
public interface ErpBomDetailMapper {

    @Select("SELECT detail_id, bom_id, child_material_id, quantity_per_parent, loss_rate, " +
        "operation_step, lead_time, source_type, status " +
        "FROM bd_bom_detail WHERE bom_id = #{bomId} AND del_flag = '0' ORDER BY sort_order, operation_step")
    List<Map<String, Object>> selectBomDetailsByBomId(@Param("bomId") Long bomId);
}
