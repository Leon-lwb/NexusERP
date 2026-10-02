package com.nexus.erp.pu.mapper;

import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

import java.util.List;

/** 不可覆盖的占用事件映射；eventId 由持有 guard 的 ERP 服务提供雪花 ID。 */
@Mapper
public interface PuRequirementAllocationEventMapper {

    @Insert("INSERT INTO pu_requirement_allocation_event ("
        + "event_id, tenant_id, allocation_id, event_sequence, event_key, event_type, from_status, to_status, "
        + "occupied_base_before, occupied_base_after, requisition_line_id, order_line_id, actor_id) "
        + "VALUES (#{row.eventId}, #{row.tenantId}, #{row.allocationId}, #{row.eventSequence}, #{row.eventKey}, "
        + "#{row.eventType}, #{row.fromStatus}, #{row.toStatus}, #{row.occupiedBaseBefore}, "
        + "#{row.occupiedBaseAfter}, #{row.requisitionLineId}, #{row.orderLineId}, #{row.actorId})")
    int insert(@Param("row") PuRequirementAllocationEvent row);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_event "
        + "WHERE tenant_id = #{tenantId} AND allocation_id = #{allocationId} "
        + "ORDER BY event_sequence")
    List<PuRequirementAllocationEvent> listByAllocation(@Param("tenantId") String tenantId,
                                                          @Param("allocationId") Long allocationId);
    /** Permanent event-key current read; neither Redis TTL nor a request-supplied key decides consumption. */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_event "
        + "WHERE tenant_id = #{tenantId} AND event_key = #{eventKey} FOR UPDATE")
    PuRequirementAllocationEvent lockByKey(@Param("tenantId") String tenantId, @Param("eventKey") String eventKey);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_event "
        + "WHERE tenant_id = #{tenantId} AND allocation_id = #{allocationId} "
        + "ORDER BY event_sequence FOR UPDATE")
    List<PuRequirementAllocationEvent> lockByAllocation(@Param("tenantId") String tenantId,
        @Param("allocationId") Long allocationId);
}
