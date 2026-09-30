package com.nexus.erp.pu.mapper;

import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 不可覆盖的占用事件映射；eventId 由持有 guard 的 ERP 服务提供雪花 ID。 */
@Mapper
public interface PuRequirementAllocationEventMapper {

    @Insert("INSERT INTO pu_requirement_allocation_event ("
        + "event_id, tenant_id, allocation_id, event_key, event_type, from_status, to_status, "
        + "occupied_base_before, occupied_base_after, requisition_line_id, order_line_id, actor_id) "
        + "VALUES (#{row.eventId}, #{row.tenantId}, #{row.allocationId}, #{row.eventKey}, "
        + "#{row.eventType}, #{row.fromStatus}, #{row.toStatus}, #{row.occupiedBaseBefore}, "
        + "#{row.occupiedBaseAfter}, #{row.requisitionLineId}, #{row.orderLineId}, #{row.actorId})")
    int insert(@Param("row") PuRequirementAllocationEvent row);

    @Select("SELECT * FROM pu_requirement_allocation_event "
        + "WHERE tenant_id = #{tenantId} AND allocation_id = #{allocationId} "
        + "ORDER BY occurred_at, event_id")
    List<PuRequirementAllocationEvent> listByAllocation(@Param("tenantId") String tenantId,
                                                          @Param("allocationId") Long allocationId);
}
