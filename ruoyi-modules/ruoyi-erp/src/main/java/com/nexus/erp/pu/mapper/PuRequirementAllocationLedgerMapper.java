package com.nexus.erp.pu.mapper;

import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

/**
 * 占用账本的受限映射：所有写入应由同一 @DS("erp") 服务事务持有物料 guard 后发起。
 * INSERT 的 allocationId 须由调用方生成雪花 ID；唯一键冲突须由服务端拒绝/核验重放。
 */
@Mapper
public interface PuRequirementAllocationLedgerMapper {

    @Insert("INSERT INTO pu_requirement_allocation_ledger ("
        + "allocation_id, tenant_id, material_id, source_type, detail_id, requisition_line_id, "
        + "allocation_no, order_line_id, base_uom_id, covered_demand_base_qty, occupied_base_qty, "
        + "allocation_status, allocation_key, decision_idempotency_key) VALUES ("
        + "#{row.allocationId}, #{row.tenantId}, #{row.materialId}, #{row.sourceType}, "
        + "#{row.detailId}, #{row.requisitionLineId}, #{row.allocationNo}, #{row.orderLineId}, "
        + "#{row.baseUomId}, #{row.coveredDemandBaseQty}, #{row.occupiedBaseQty}, "
        + "#{row.allocationStatus}, #{row.allocationKey}, #{row.decisionIdempotencyKey})")
    int insert(@Param("row") PuRequirementAllocationLedger row);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_ledger "
        + "WHERE tenant_id = #{tenantId} AND allocation_id = #{allocationId} FOR UPDATE")
    PuRequirementAllocationLedger lockById(@Param("tenantId") String tenantId,
                                            @Param("allocationId") Long allocationId);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_ledger "
        + "WHERE tenant_id = #{tenantId} AND allocation_key = #{allocationKey} FOR UPDATE")
    PuRequirementAllocationLedger lockByKey(@Param("tenantId") String tenantId,
                                             @Param("allocationKey") String allocationKey);

    /** 仅更新可变状态；返回 0 即租户、ID 或期望版本不匹配，调用方必须回滚。 */
    @Update("UPDATE pu_requirement_allocation_ledger SET "
        + "order_line_id = #{row.orderLineId}, occupied_base_qty = #{row.occupiedBaseQty}, "
        + "fulfilled_base_qty = #{row.fulfilledBaseQty}, released_base_qty = #{row.releasedBaseQty}, "
        + "allocation_status = #{row.allocationStatus}, transfer_time = #{row.transferTime}, "
        + "released_at = #{row.releasedAt}, release_reason = #{row.releaseReason}, "
        + "version = version + 1, update_time = NOW() "
        + "WHERE tenant_id = #{tenantId} AND allocation_id = #{row.allocationId} "
        + "AND version = #{expectedVersion} AND material_id = #{row.materialId} "
        + "AND source_type = #{row.sourceType} AND detail_id = #{row.detailId} "
        + "AND requisition_line_id = #{row.requisitionLineId} AND allocation_key = #{row.allocationKey} "
        + "AND base_uom_id = #{row.baseUomId} AND covered_demand_base_qty = #{row.coveredDemandBaseQty}")
    int updateWithVersion(@Param("tenantId") String tenantId,
                          @Param("row") PuRequirementAllocationLedger row,
                          @Param("expectedVersion") Integer expectedVersion);
}
