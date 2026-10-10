package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Quote;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Confirmation;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 明确租户的页面原生只读查询；不锁定、不写入、不把NULL版本解释为零。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface PuCertifiedPurchaseOrderReadMapper {
    /** 页面来源提示不是永久来源认证，实际写入仍在原独占事务中重新核验。 */
    record OrderHead(String tenantId, Long orderId, String orderNo, Long requisitionId,
        Long vendorId, String status, Integer version, Long quoteId, Long selectFlowId,
        String requisitionSourceType, Long decisionRunId, Long decisionActionId,
        String sourceRequestKey, Boolean originalCommandPresent) { }

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT o.tenant_id,o.order_id,o.order_no,o.requisition_id,o.vendor_id,o.status,"
        + "o.certified_version AS version,o.certified_terms_id AS quote_id,o.select_flow_id,"
        + "r.source_type AS requisition_source_type,r.decision_run_id,r.decision_action_id,"
        + "r.decision_idempotency_key AS source_request_key,"
        + "EXISTS(SELECT 1 FROM pu_certified_purchase_command c WHERE c.tenant_id=#{tenantId} "
        + "AND c.requisition_id=o.requisition_id AND c.request_key=r.decision_idempotency_key "
        + "AND c.command_status='COMMITTED') AS original_command_present "
        + "FROM pu_purchase_order o LEFT JOIN pu_purchase_requisition r "
        + "ON r.tenant_id=o.tenant_id AND r.requisition_id=o.requisition_id "
        + "WHERE o.tenant_id=#{tenantId} AND o.order_id=#{id} AND o.del_flag='0'")
    OrderHead readHead(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT " + PuCertifiedPurchaseOrderWorkflowMapper.QUOTE_COLUMNS
        + " FROM pu_certified_purchase_order_terms WHERE tenant_id=#{tenantId} AND order_id=#{id} "
        + "ORDER BY quote_version LIMIT 101")
    List<Quote> readQuotes(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT c.confirmation_id,c.tenant_id,c.quote_id,c.quote_hash,c.request_key,c.request_hash,"
        + "c.actor_id,c.permission_code,c.confirmed_at,c.review_note "
        + "FROM pu_certified_purchase_order_terms_confirmation c "
        + "JOIN pu_certified_purchase_order_terms q ON q.tenant_id=c.tenant_id AND q.quote_id=c.quote_id "
        + "WHERE c.tenant_id=#{tenantId} AND q.tenant_id=#{tenantId} AND q.order_id=#{id} LIMIT 101")
    List<Confirmation> readConfirmations(@Param("tenantId") String tenantId, @Param("id") Long id);

    /** 原生逐占用身份；用于页面回执归属核对，不计算数量或供应额度。 */
    record AllocationView(Long allocationId, Long requisitionLineId, Long orderLineId,
        String allocationStatus, Integer version) { }

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT a.allocation_id,a.requisition_line_id,a.order_line_id,a.allocation_status,a.version "
        + "FROM pu_requirement_allocation_ledger a JOIN pu_purchase_order_line l "
        + "ON l.tenant_id=a.tenant_id AND l.line_id=a.order_line_id "
        + "WHERE a.tenant_id=#{tenantId} AND l.tenant_id=#{tenantId} AND l.order_id=#{id} "
        + "AND l.del_flag='0' ORDER BY a.allocation_id LIMIT 201")
    List<AllocationView> readAllocations(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT DATE_FORMAT(CURRENT_DATE(),'%Y-%m-%d')")
    String databaseDate();
}
