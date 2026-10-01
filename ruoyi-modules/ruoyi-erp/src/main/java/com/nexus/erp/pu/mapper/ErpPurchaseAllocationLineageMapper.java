package com.nexus.erp.pu.mapper;

import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.EpochMillisDateTypeHandler;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderLine;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionLine;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 单租户、单物料采购占用归属的固定只读来源查询，不提供锁定、写入或额度接口。
 * 申请和订单行只排除明确已删除的行，关闭及未知状态不作 SQL 过滤；引用头和 MRP
 * 明细保留删除事实，由 reader 拒绝式核对。被引用明细不受需求窗口或来源类型过滤。
 * 墙上 DATETIME 沿已支持的明确 +08:00 会话合同投影为毫秒，并复用目录 Mapper
 * 的字段专用处理器；不注册全局转换器，不设置会话或数据库时区，不补填 NULL。
 * 返回实体仍可变，仅供 reader 收到每个查询结果后立即冻结，不可直接用于授权。
 */
@Mapper
public interface ErpPurchaseAllocationLineageMapper {

    /** 读取数据库十进制秒及原始会话时区，不接外部 now 或应用时钟。 */
    @Select("""
        SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)) AS epoch_seconds,
               @@session.time_zone AS database_time_zone
        """)
    @ConstructorArgs({
        @Arg(column = "epoch_seconds", javaType = BigDecimal.class),
        @Arg(column = "database_time_zone", javaType = String.class)
    })
    DatabaseClock selectDatabaseClock();

    /** 读取真实物料身份与基本单位 ID；删除、状态及未知值由 reader 复核。 */
    @Select("""
        SELECT tenant_id AS tenantId, material_id AS materialId, base_uom_id AS baseUomId,
               status, del_flag AS delFlag
        FROM bd_material
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
        """)
    BdMaterial selectMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /**
     * 保留本物料全部账本，并按同租户当前物料的真实申请行、订单行、MRP 明细及种子
     * 账本的申请行/明细/订单行引用补读相关账本；关联账本不按自身物料预过滤。
     * 不过滤关联实体的删除状态或账本状态，错/NULL 物料与重复承接由 reader 拒绝，
     * 避免隐藏同一引用链的矛盾；各分支及其子查询均受当前租户范围约束。
     */
    @Select("""
        SELECT allocation_id AS allocationId, tenant_id AS tenantId, material_id AS materialId,
               source_type AS sourceType, detail_id AS detailId, requisition_line_id AS requisitionLineId,
               allocation_no AS allocationNo, order_line_id AS orderLineId, base_uom_id AS baseUomId,
               covered_demand_base_qty AS coveredDemandBaseQty, occupied_base_qty AS occupiedBaseQty,
               fulfilled_base_qty AS fulfilledBaseQty, released_base_qty AS releasedBaseQty,
               allocation_status AS allocationStatus, allocation_key AS allocationKey,
               decision_idempotency_key AS decisionIdempotencyKey, version,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(transfer_time) * 1000 AS transferTimeEpochMillis,
               UNIX_TIMESTAMP(released_at) * 1000 AS releasedAtEpochMillis, release_reason AS releaseReason,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM pu_requirement_allocation_ledger
        WHERE tenant_id = #{tenantId}
          AND (
            material_id = #{materialId}
            OR requisition_line_id IN (
              SELECT line_id FROM pu_purchase_requisition_line
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR order_line_id IN (
              SELECT line_id FROM pu_purchase_order_line
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR detail_id IN (
              SELECT detail_id FROM prd_material_requirement_detail
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR requisition_line_id IN (
              SELECT requisition_line_id FROM pu_requirement_allocation_ledger
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR detail_id IN (
              SELECT detail_id FROM pu_requirement_allocation_ledger
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR order_line_id IN (
              SELECT order_line_id FROM pu_requirement_allocation_ledger
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
          )
        ORDER BY allocation_id ASC
        """)
    @Results({
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "transferTimeEpochMillis", property = "transferTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "releasedAtEpochMillis", property = "releasedAt", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<PuRequirementAllocationLedger> selectAllocations(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 旧 source_id 仅是原始字段，不在 SQL 推断 detail；全部未删除或未知删除申请行均读取。 */
    @Select("""
        SELECT line_id AS lineId, tenant_id AS tenantId, requisition_id AS requisitionId,
               material_id AS materialId, unit, apply_quantity AS applyQuantity,
               approved_quantity AS approvedQuantity, shortage_quantity AS shortageQuantity,
               UNIX_TIMESTAMP(required_date) * 1000 AS requiredDateEpochMillis,
               source_type AS sourceType, source_id AS sourceId, attr1, attr2, attr3, attr4, del_flag AS delFlag,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM pu_purchase_requisition_line
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
        ORDER BY line_id ASC
        """)
    @Results({
        @Result(column = "requiredDateEpochMillis", property = "requiredDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawRequisitionLine> selectRequisitionLines(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /**
     * 保留当前物料订单行，并按当前物料申请行及账本真实引用补读同申请行或显式订单行。
     * 关联行不按订单物料预过滤，错/NULL 物料及重复承接由 reader 明确拒绝；所有分支
     * 均受顶层租户和删除条件约束，不过滤收货、退货或关闭状态，不推断旧 source_id。
     */
    @Select("""
        SELECT line_id AS lineId, tenant_id AS tenantId, order_id AS orderId,
               requisition_line_id AS requisitionLineId, material_id AS materialId, unit,
               order_quantity AS orderQuantity, received_quantity AS receivedQuantity,
               returned_quantity AS returnedQuantity, invoiced_qty AS invoicedQty,
               receive_status AS receiveStatus, del_flag AS delFlag,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM pu_purchase_order_line
        WHERE tenant_id = #{tenantId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
          AND (
            material_id = #{materialId}
            OR requisition_line_id IN (
              SELECT line_id FROM pu_purchase_requisition_line
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
                AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
            )
            OR requisition_line_id IN (
              SELECT requisition_line_id FROM pu_requirement_allocation_ledger
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
            OR line_id IN (
              SELECT order_line_id FROM pu_requirement_allocation_ledger
              WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
            )
          )
        ORDER BY line_id ASC
        """)
    @Results({
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawOrderLine> selectOrderLines(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 只按已读申请行的父键补读，保留删除、关闭和 NULL 版本；不使用 version=0 的实体默认值。 */
    @Select("""
        <script>
        SELECT requisition_id AS requisitionId, tenant_id AS tenantId,
               requisition_no AS requisitionNo, source_type AS sourceType, status,
               UNIX_TIMESTAMP(requisition_date) * 1000 AS requisitionDateEpochMillis,
               UNIX_TIMESTAMP(required_date) * 1000 AS requiredDateEpochMillis,
               decision_run_id AS decisionRunId, decision_action_id AS decisionActionId,
               decision_idempotency_key AS decisionIdempotencyKey, select_flow_id AS selectFlowId,
               version, del_flag AS delFlag,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM pu_purchase_requisition
        WHERE tenant_id = #{tenantId} AND requisition_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY requisition_id ASC
        </script>
        """)
    @Results({
        @Result(column = "requisitionDateEpochMillis", property = "requisitionDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "requiredDateEpochMillis", property = "requiredDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawRequisitionHead> selectRequisitionHeads(@Param("tenantId") String tenantId, @Param("headIds") List<Long> headIds);

    /** 原始预计到货日期仅冻结，不能从 required_date 或当前条款构造保证到货事实。 */
    @Select("""
        <script>
        SELECT order_id AS orderId, tenant_id AS tenantId, order_no AS orderNo,
               requisition_id AS requisitionId, vendor_id AS vendorId, status,
               UNIX_TIMESTAMP(order_date) * 1000 AS orderDateEpochMillis,
               UNIX_TIMESTAMP(expected_arrival_date) * 1000 AS expectedArrivalDateEpochMillis,
               del_flag AS delFlag,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM pu_purchase_order
        WHERE tenant_id = #{tenantId} AND order_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY order_id ASC
        </script>
        """)
    @Results({
        @Result(column = "orderDateEpochMillis", property = "orderDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "expectedArrivalDateEpochMillis", property = "expectedArrivalDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawOrderHead> selectOrderHeads(@Param("tenantId") String tenantId, @Param("headIds") List<Long> headIds);

    /** 账本引用 detailIds 按 ID 补读，包含删除行和窗口外需求；物料归属由 reader 核对。 */
    @Select("""
        <script>
        SELECT detail_id AS detailId, tenant_id AS tenantId, requirement_id AS requirementId,
               material_id AS materialId, unit, source_type AS sourceType,
               required_quantity AS requiredQuantity, issued_quantity AS issuedQuantity,
               stock_quantity AS stockQuantity, shortage_quantity AS shortageQuantity,
               UNIX_TIMESTAMP(required_date) * 1000 AS requiredDateEpochMillis,
               status, del_flag AS delFlag, backflush_flag AS backflushFlag,
               actual_consumed_qty AS actualConsumedQty,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM prd_material_requirement_detail
        WHERE tenant_id = #{tenantId} AND detail_id IN
          <foreach collection="detailIds" item="detailId" open="(" separator="," close=")">
            #{detailId}
          </foreach>
        ORDER BY detail_id ASC
        </script>
        """)
    @Results({
        @Result(column = "requiredDateEpochMillis", property = "requiredDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawRequirementDetail> selectRequirementDetails(@Param("tenantId") String tenantId, @Param("detailIds") List<Long> detailIds);

    /** MRP 头的 material_id 是成品原始事实，不要求与子项材料相等；删除和状态不得隐藏。 */
    @Select("""
        <script>
        SELECT requirement_id AS requirementId, tenant_id AS tenantId,
               requirement_no AS requirementNo, parent_requirement_id AS parentRequirementId,
               plan_id AS planId, plan_no AS planNo, bom_id AS bomId, material_id AS materialId,
               UNIX_TIMESTAMP(requirement_date) * 1000 AS requirementDateEpochMillis,
               source_type AS sourceType, status,
               UNIX_TIMESTAMP(confirmed_time) * 1000 AS confirmedTimeEpochMillis,
               UNIX_TIMESTAMP(issued_time) * 1000 AS issuedTimeEpochMillis,
               UNIX_TIMESTAMP(closed_time) * 1000 AS closedTimeEpochMillis, del_flag AS delFlag,
               UNIX_TIMESTAMP(create_time) * 1000 AS createTimeEpochMillis,
               UNIX_TIMESTAMP(update_time) * 1000 AS updateTimeEpochMillis
        FROM prd_material_requirement
        WHERE tenant_id = #{tenantId} AND requirement_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY requirement_id ASC
        </script>
        """)
    @Results({
        @Result(column = "requirementDateEpochMillis", property = "requirementDate", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "confirmedTimeEpochMillis", property = "confirmedTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "issuedTimeEpochMillis", property = "issuedTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "closedTimeEpochMillis", property = "closedTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "createTimeEpochMillis", property = "createTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "updateTimeEpochMillis", property = "updateTime", javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<RawRequirementHead> selectRequirementHeads(@Param("tenantId") String tenantId, @Param("headIds") List<Long> headIds);

    /** 只有数据库原始时钟和会话时区，不包含可信标志或调用方时间。 */
    record DatabaseClock(BigDecimal epochSeconds, String databaseTimeZone) { }
}
