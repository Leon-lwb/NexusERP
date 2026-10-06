package com.nexus.erp.ai.adapter.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 履约原头、全行、永久过账和全事件当前读；不以状态、删除或零占用过滤来源。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface ErpPurchaseAllocationReceiptSourceMapper {
    @Select("""
        SELECT receive_id AS receiveId, tenant_id AS tenantId, pur_id AS orderId,
               status, del_flag AS delFlag, receive_code AS receiveCode, vendor_id AS vendorId,
               UNIX_TIMESTAMP(receive_date)*1000 AS receiveAt, UNIX_TIMESTAMP(shelf_time)*1000 AS shelfAt
        FROM wm_material_receive
        WHERE pur_id = #{orderId} OR receive_id IN
          (SELECT receive_id FROM wm_material_receive_line
           WHERE purchase_line_id = #{orderLineId} OR source_doc_line_id = #{orderLineId})
        ORDER BY receive_id FOR UPDATE
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<ReceiptHead> lockHeads(@Param("orderId") Long orderId, @Param("orderLineId") Long orderLineId);

    @Select("""
        <script>
        SELECT line_id AS lineId, tenant_id AS tenantId, receive_id AS receiveId,
               material_id AS materialId, base_uom_id AS baseUomId, quantity_received AS quantity,
               purchase_line_id AS orderLineId, source_doc_line_id AS sourceDocLineId, del_flag AS delFlag
        FROM wm_material_receive_line
        WHERE purchase_line_id = #{orderLineId} OR source_doc_line_id = #{orderLineId}
          <if test="headIds != null and !headIds.isEmpty()">
            OR receive_id IN <foreach collection="headIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          </if>
        ORDER BY line_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<ReceiptLine> lockLines(@Param("orderLineId") Long orderLineId, @Param("headIds") List<Long> headIds);

    @Select("""
        <script>
        SELECT tenant_id AS tenantId, receive_id AS receiveId, material_id AS materialId,
               actor_id AS actorId, source_fingerprint AS sourceFingerprint, total_quantity AS totalQuantity,
               DATE_FORMAT(posted_at,'%Y-%m-%d %H:%i:%s') AS postedAt, notification_status AS notificationStatus
        FROM wm_certified_receipt_posting
        WHERE receive_id IN <foreach collection="headIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY receive_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<Posting> lockPostings(@Param("headIds") List<Long> headIds);

    @Select("""
        SELECT event_id AS eventId, tenant_id AS tenantId, allocation_id AS allocationId,
               event_sequence AS eventSequence, event_key AS eventKey, event_type AS eventType,
               from_status AS fromStatus, to_status AS toStatus,
               occupied_base_before AS occupiedBefore, occupied_base_after AS occupiedAfter,
               requisition_line_id AS requisitionLineId, order_line_id AS orderLineId,
               actor_id AS actorId, UNIX_TIMESTAMP(occurred_at)*1000 AS occurredAt
        FROM pu_requirement_allocation_event WHERE allocation_id = #{allocationId}
        ORDER BY event_sequence, event_id FOR UPDATE
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<AllocationEvent> lockEvents(@Param("allocationId") Long allocationId);

    /** 不依靠收货行自己声明父归属，保留实际PO行的跨父、跨租户及删除原事实。 */
    @Select("""
        <script>
        SELECT line_id AS lineId, tenant_id AS tenantId, order_id AS orderId,
               material_id AS materialId, unit, del_flag AS delFlag
        FROM pu_purchase_order_line
        WHERE line_id IN <foreach collection="lineIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY line_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<PurchaseLine> lockPurchaseLines(@Param("lineIds") List<Long> lineIds);

    @Select("SELECT UNIX_TIMESTAMP(NOW(6)) AS epochSeconds, @@session.time_zone AS databaseTimeZone")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Clock clock();

    record ReceiptHead(Long receiveId, String tenantId, Long orderId, String status, String delFlag,
                       String receiveCode, Long vendorId, BigDecimal receiveAt, BigDecimal shelfAt) { }
    record ReceiptLine(Long lineId, String tenantId, Long receiveId, Long materialId, String baseUomId,
                       BigDecimal quantity, Long orderLineId, Long sourceDocLineId, String delFlag) { }
    record Posting(String tenantId, Long receiveId, Long materialId, Long actorId, String sourceFingerprint,
                   BigDecimal totalQuantity, String postedAt, String notificationStatus) { }
    record AllocationEvent(Long eventId, String tenantId, Long allocationId, Integer eventSequence,
            String eventKey, String eventType, String fromStatus, String toStatus, BigDecimal occupiedBefore,
            BigDecimal occupiedAfter, Long requisitionLineId, Long orderLineId, Long actorId, BigDecimal occurredAt) { }
    record PurchaseLine(Long lineId, String tenantId, Long orderId, Long materialId, String unit, String delFlag) { }
    record Clock(BigDecimal epochSeconds, String databaseTimeZone) { }
}
