package com.nexus.erp.pu.mapper;

import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 单租户、单物料的原始来源行当前读，仅由内部 reader 在物料 guard 之后调用。
 * 各表独立锁定，不用 JOIN 消除缺失/跨租户头；头的身份完整性由 reader 拒绝式检查。
 * 行仅排除明确已删除的 '1'；NULL/其他未知删除标志保留，不能解释为有效业务来源。
 * 不筛选业务状态、供应日期、来源类型或额度，不证明全池完整、供给有效或并发安全。
 * 显式列别名仅映射原字段，不依赖全局下划线映射，也不归一化 NULL。
 */
@Mapper
public interface PuRequirementCompetitionPoolMapper {

    @Select("""
        SELECT detail_id AS detailId, tenant_id AS tenantId, requirement_id AS requirementId,
               material_id AS materialId, unit, source_type AS sourceType,
               required_quantity AS requiredQuantity, issued_quantity AS issuedQuantity,
               stock_quantity AS stockQuantity, shortage_quantity AS shortageQuantity,
               required_date AS requiredDate, status, del_flag AS delFlag,
               backflush_flag AS backflushFlag, actual_consumed_qty AS actualConsumedQty,
               create_time AS createTime, update_time AS updateTime
        FROM prd_material_requirement_detail
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
          AND (required_date IS NULL OR DATE(required_date) <= #{windowEnd})
        ORDER BY detail_id ASC FOR UPDATE
        """)
    List<RawRequirementDetail> lockRequirementDetails(@Param("tenantId") String tenantId,
                                                     @Param("materialId") Long materialId,
                                                     @Param("windowEnd") LocalDate windowEnd);

    /** 包含删除/未知状态头；headIds 必须由已锁定明细提取、去重、升序排列且非空。 */
    @Select("""
        <script>
        SELECT requirement_id AS requirementId, tenant_id AS tenantId,
               requirement_no AS requirementNo, parent_requirement_id AS parentRequirementId,
               plan_id AS planId, plan_no AS planNo, bom_id AS bomId, material_id AS materialId,
               requirement_date AS requirementDate, source_type AS sourceType, status,
               confirmed_time AS confirmedTime, issued_time AS issuedTime, closed_time AS closedTime,
               del_flag AS delFlag, create_time AS createTime, update_time AS updateTime
        FROM prd_material_requirement
        WHERE tenant_id = #{tenantId} AND requirement_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY requirement_id ASC FOR UPDATE
        </script>
        """)
    List<RawRequirementHead> lockRequirementHeads(@Param("tenantId") String tenantId,
                                                 @Param("headIds") List<Long> headIds);

    @Select("""
        SELECT stock_id AS stockId, tenant_id AS tenantId, material_id AS materialId,
               base_uom_id AS baseUomId, base_uom_name AS baseUomName,
               stock_quantity AS stockQuantity, reserved_quantity AS reservedQuantity,
               frozen_flag AS frozenFlag, version, warehouse_id AS warehouseId,
               area_id AS areaId, location_id AS locationId, batch_id AS batchId,
               sn_id AS snId, wo_id AS woId, production_date AS productionDate,
               recpt_date AS recptDate, expire_date AS expireDate, del_flag AS delFlag,
               create_time AS createTime, update_time AS updateTime
        FROM wm_material_stock
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
        ORDER BY stock_id ASC FOR UPDATE
        """)
    List<RawStock> lockStocks(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("""
        SELECT line_id AS lineId, tenant_id AS tenantId, order_id AS orderId,
               requisition_line_id AS requisitionLineId, material_id AS materialId, unit,
               order_quantity AS orderQuantity, received_quantity AS receivedQuantity,
               returned_quantity AS returnedQuantity, invoiced_qty AS invoicedQty,
               receive_status AS receiveStatus, del_flag AS delFlag,
               create_time AS createTime, update_time AS updateTime
        FROM pu_purchase_order_line
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
        ORDER BY line_id ASC FOR UPDATE
        """)
    List<RawOrderLine> lockOrderLines(@Param("tenantId") String tenantId,
                                     @Param("materialId") Long materialId);

    /** 过去/晚到/NULL 预计日期及所有状态均保留；不存在或跨租户的头由 reader 整池拒绝。 */
    @Select("""
        <script>
        SELECT order_id AS orderId, tenant_id AS tenantId, order_no AS orderNo,
               requisition_id AS requisitionId, vendor_id AS vendorId, status,
               order_date AS orderDate, expected_arrival_date AS expectedArrivalDate,
               del_flag AS delFlag, create_time AS createTime, update_time AS updateTime
        FROM pu_purchase_order
        WHERE tenant_id = #{tenantId} AND order_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY order_id ASC FOR UPDATE
        </script>
        """)
    List<RawOrderHead> lockOrderHeads(@Param("tenantId") String tenantId,
                                     @Param("headIds") List<Long> headIds);

    @Select("""
        SELECT line_id AS lineId, tenant_id AS tenantId, requisition_id AS requisitionId,
               material_id AS materialId, unit, apply_quantity AS applyQuantity,
               approved_quantity AS approvedQuantity, shortage_quantity AS shortageQuantity,
               required_date AS requiredDate, source_type AS sourceType, source_id AS sourceId,
               attr1, attr2, attr3, attr4, del_flag AS delFlag,
               create_time AS createTime, update_time AS updateTime
        FROM pu_purchase_requisition_line
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag = '0' OR del_flag IS NULL OR del_flag NOT IN ('0', '1'))
        ORDER BY line_id ASC FOR UPDATE
        """)
    List<RawRequisitionLine> lockRequisitionLines(@Param("tenantId") String tenantId,
                                                 @Param("materialId") Long materialId);

    @Select("""
        <script>
        SELECT requisition_id AS requisitionId, tenant_id AS tenantId,
               requisition_no AS requisitionNo, source_type AS sourceType, status,
               requisition_date AS requisitionDate, required_date AS requiredDate,
               decision_run_id AS decisionRunId, decision_action_id AS decisionActionId,
               decision_idempotency_key AS decisionIdempotencyKey, select_flow_id AS selectFlowId,
               version, del_flag AS delFlag, create_time AS createTime, update_time AS updateTime
        FROM pu_purchase_requisition
        WHERE tenant_id = #{tenantId} AND requisition_id IN
          <foreach collection="headIds" item="headId" open="(" separator="," close=")">
            #{headId}
          </foreach>
        ORDER BY requisition_id ASC FOR UPDATE
        </script>
        """)
    List<RawRequisitionHead> lockRequisitionHeads(@Param("tenantId") String tenantId,
                                                 @Param("headIds") List<Long> headIds);

    /** 不排除 RELEASED；账本身份、转移、释放及幂等审计必须原样可读。 */
    @Select("""
        SELECT allocation_id AS allocationId, tenant_id AS tenantId, material_id AS materialId,
               source_type AS sourceType, detail_id AS detailId, requisition_line_id AS requisitionLineId,
               allocation_no AS allocationNo, order_line_id AS orderLineId, base_uom_id AS baseUomId,
               covered_demand_base_qty AS coveredDemandBaseQty, occupied_base_qty AS occupiedBaseQty,
               fulfilled_base_qty AS fulfilledBaseQty, released_base_qty AS releasedBaseQty,
               allocation_status AS allocationStatus, allocation_key AS allocationKey,
               decision_idempotency_key AS decisionIdempotencyKey, version, create_time AS createTime,
               transfer_time AS transferTime, released_at AS releasedAt, release_reason AS releaseReason,
               update_time AS updateTime
        FROM pu_requirement_allocation_ledger
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
        ORDER BY allocation_id ASC FOR UPDATE
        """)
    List<PuRequirementAllocationLedger> lockAllocations(@Param("tenantId") String tenantId,
                                                       @Param("materialId") Long materialId);

    /** 历史 stock/shortage 仅是原字段，不是本次净需求或申请余额。 */
    @Data
    class RawRequirementDetail {
        private Long detailId;
        private String tenantId;
        private Long requirementId;
        private Long materialId;
        private String unit;
        private String sourceType;
        private BigDecimal requiredQuantity;
        private BigDecimal issuedQuantity;
        private BigDecimal stockQuantity;
        private BigDecimal shortageQuantity;
        private Date requiredDate;
        private Long status;
        private String delFlag;
        private Integer backflushFlag;
        private BigDecimal actualConsumedQty;
        private Date createTime;
        private Date updateTime;
    }

    @Data
    class RawRequirementHead {
        private Long requirementId;
        private String tenantId;
        private String requirementNo;
        private Long parentRequirementId;
        private Long planId;
        private String planNo;
        private Long bomId;
        private Long materialId;
        private Date requirementDate;
        private String sourceType;
        private String status;
        private Date confirmedTime;
        private Date issuedTime;
        private Date closedTime;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }

    /** 不提供会将 NULL 默认为零的可用库存计算。base_uom_id 实际为 varchar。 */
    @Data
    class RawStock {
        private Long stockId;
        private String tenantId;
        private Long materialId;
        private String baseUomId;
        private String baseUomName;
        private BigDecimal stockQuantity;
        private BigDecimal reservedQuantity;
        private Long frozenFlag;
        private Integer version;
        private Long warehouseId;
        private Long areaId;
        private Long locationId;
        private Long batchId;
        private Long snId;
        private Long woId;
        private Date productionDate;
        private Date recptDate;
        private Date expireDate;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }

    @Data
    class RawOrderLine {
        private Long lineId;
        private String tenantId;
        private Long orderId;
        private Long requisitionLineId;
        private Long materialId;
        private String unit;
        private BigDecimal orderQuantity;
        private BigDecimal receivedQuantity;
        private BigDecimal returnedQuantity;
        private BigDecimal invoicedQty;
        private String receiveStatus;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }

    @Data
    class RawOrderHead {
        private Long orderId;
        private String tenantId;
        private String orderNo;
        private Long requisitionId;
        private Long vendorId;
        private String status;
        private Date orderDate;
        private Date expectedArrivalDate;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }

    /** sourceId 可能是 runId、物料 ID 或旧版来源；没有推断出的 detailId。 */
    @Data
    class RawRequisitionLine {
        private Long lineId;
        private String tenantId;
        private Long requisitionId;
        private Long materialId;
        private String unit;
        private BigDecimal applyQuantity;
        private BigDecimal approvedQuantity;
        private BigDecimal shortageQuantity;
        private Date requiredDate;
        private String sourceType;
        private Long sourceId;
        private String attr1;
        private String attr2;
        private Long attr3;
        private Long attr4;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }

    /** 不使用现有实体 version=0 初始化，以免丢失数据库 NULL。 */
    @Data
    class RawRequisitionHead {
        private Long requisitionId;
        private String tenantId;
        private String requisitionNo;
        private String sourceType;
        private String status;
        private Date requisitionDate;
        private Date requiredDate;
        private Long decisionRunId;
        private Long decisionActionId;
        private String decisionIdempotencyKey;
        private Long selectFlowId;
        private Integer version;
        private String delFlag;
        private Date createTime;
        private Date updateTime;
    }
}
