package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.domain.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 仅为旧写路径识别归属；不修改业务或账本。不排除逻辑删除的来源证据。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface PuCertifiedPurchaseDocumentGuardMapper {
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT COUNT(*) FROM pu_certified_purchase_command WHERE tenant_id=#{tenantId} AND requisition_id=#{id} FOR UPDATE")
    Long countRequisitionCommands(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT COUNT(*) FROM pu_requirement_allocation_ledger a JOIN pu_purchase_requisition_line l ON l.tenant_id=a.tenant_id AND l.line_id=a.requisition_line_id WHERE a.tenant_id=#{tenantId} AND l.requisition_id=#{id} FOR UPDATE")
    Long countRequisitionAllocations(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT COUNT(*) FROM pu_requirement_allocation_ledger WHERE tenant_id=#{tenantId} AND requisition_line_id=#{id} FOR UPDATE")
    Long countRequisitionLineAllocations(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT COUNT(*) FROM pu_requirement_allocation_ledger a JOIN pu_purchase_order_line l ON l.tenant_id=a.tenant_id AND l.line_id=a.order_line_id WHERE a.tenant_id=#{tenantId} AND l.order_id=#{id} FOR UPDATE")
    Long countOrderAllocations(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT COUNT(*) FROM pu_requirement_allocation_ledger WHERE tenant_id=#{tenantId} AND order_line_id=#{id} FOR UPDATE")
    Long countOrderLineAllocations(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_requisition WHERE tenant_id=#{tenantId} AND requisition_id=#{id} FOR UPDATE")
    PuPurchaseRequisition lockRequisition(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_requisition_line WHERE tenant_id=#{tenantId} AND line_id=#{id} FOR UPDATE")
    PuPurchaseRequisitionLine lockRequisitionLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_requisition_line WHERE tenant_id=#{tenantId} AND requisition_id=#{id} ORDER BY line_id FOR UPDATE")
    List<PuPurchaseRequisitionLine> lockRequisitionLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_order WHERE tenant_id=#{tenantId} AND order_id=#{id} FOR UPDATE")
    PuPurchaseOrder lockOrder(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_order_line WHERE tenant_id=#{tenantId} AND line_id=#{id} FOR UPDATE")
    PuPurchaseOrderLine lockOrderLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_order_line WHERE tenant_id=#{tenantId} AND order_id=#{id} ORDER BY line_id FOR UPDATE")
    List<PuPurchaseOrderLine> lockOrderLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_return WHERE tenant_id=#{tenantId} AND return_id=#{id} FOR UPDATE")
    PuPurchaseReturn lockReturn(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_purchase_return_line WHERE tenant_id=#{tenantId} AND return_id=#{id} ORDER BY line_id FOR UPDATE")
    List<PuPurchaseReturnLine> lockReturnLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    // Order entities inherit BaseEntity and do not expose tenantId; ownership needs explicit native identities.
    record ReturnOrderIdentity(String tenantId, Long orderId) { }
    record ReturnOrderLineIdentity(String tenantId, Long lineId, Long orderId, Long materialId) { }

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id AS tenantId, order_id AS orderId FROM pu_purchase_order "
        + "WHERE tenant_id=#{tenantId} AND order_id=#{id} FOR UPDATE")
    ReturnOrderIdentity lockReturnOrderIdentity(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id AS tenantId, line_id AS lineId, order_id AS orderId, material_id AS materialId "
        + "FROM pu_purchase_order_line WHERE tenant_id=#{tenantId} AND line_id=#{id} FOR UPDATE")
    ReturnOrderLineIdentity lockReturnOrderLineIdentity(@Param("tenantId") String tenantId, @Param("id") Long id);
}
