package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 固定的原生路由标识。提示信息不具备权威性，仅供按升序获取守卫锁前进行不加锁的发现；
 * 所有作为权威依据的读取均显式指定租户，并采用当前读/加锁读。有意保留已补偿/删除的证据：
 * 订单头标记丢失不能将认证来源变成旧路径订单。
 */
@Mapper
@InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface PuCertifiedPurchaseReceiptBatchMapper {
    String REQUISITION_IDS="SELECT h.requisition_id FROM pu_purchase_order h WHERE h.tenant_id=#{tenantId} AND h.order_id=#{id} "
        +"UNION SELECT r.requisition_id FROM pu_purchase_order_line l JOIN pu_purchase_requisition_line r "
        +"ON r.tenant_id=l.tenant_id AND r.line_id=l.requisition_line_id WHERE l.tenant_id=#{tenantId} AND l.order_id=#{id} "
        +"UNION SELECT r.requisition_id FROM pu_requirement_allocation_ledger a JOIN pu_purchase_order_line l "
        +"ON l.tenant_id=a.tenant_id AND (l.line_id=a.order_line_id OR l.requisition_line_id=a.requisition_line_id) "
        +"JOIN pu_purchase_requisition_line r ON r.tenant_id=a.tenant_id AND r.line_id=a.requisition_line_id "
        +"WHERE a.tenant_id=#{tenantId} AND l.order_id=#{id}";
    String DIRECT_ALLOCATION="EXISTS (SELECT 1 FROM pu_purchase_order_line l WHERE l.tenant_id=a.tenant_id "
        +"AND l.order_id=#{id} AND (l.line_id=a.order_line_id OR l.requisition_line_id=a.requisition_line_id))";

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,receive_id,pur_id AS order_id,status,del_flag FROM wm_material_receive "
        +"WHERE tenant_id=#{tenantId} AND receive_id=#{id}")
    ReceiveHead readReceiptHead(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT l.material_id FROM wm_material_receive_line l WHERE l.tenant_id=#{tenantId} AND l.receive_id=#{id} "
        +"UNION ALL SELECT l.material_id FROM pu_purchase_order_line l JOIN wm_material_receive h "
        +"ON h.tenant_id=l.tenant_id AND h.pur_id=l.order_id WHERE h.tenant_id=#{tenantId} AND h.receive_id=#{id} ORDER BY material_id")
    List<Long> readMaterialHints(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,receive_id,pur_id AS order_id,status,del_flag FROM wm_material_receive "
        +"WHERE tenant_id=#{tenantId} AND receive_id=#{id} FOR UPDATE")
    ReceiveHead lockReceiptHead(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,line_id,receive_id,purchase_line_id,material_id,base_uom_id,quantity_received,del_flag "
        +"FROM wm_material_receive_line WHERE tenant_id=#{tenantId} AND receive_id=#{id} ORDER BY line_id FOR UPDATE")
    List<ReceiveLine> lockReceiptLines(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,order_id,requisition_id,status,del_flag FROM pu_purchase_order "
        +"WHERE tenant_id=#{tenantId} AND order_id=#{id} FOR UPDATE")
    OrderHead lockOrderHead(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,line_id,order_id,requisition_line_id,material_id,unit,order_quantity,received_quantity,"
        +"returned_quantity,receive_status,del_flag FROM pu_purchase_order_line WHERE tenant_id=#{tenantId} "
        +"AND order_id=#{id} ORDER BY line_id FOR UPDATE")
    List<OrderLine> lockOrderLines(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,requisition_id,requisition_no,source_type,status,decision_idempotency_key AS request_key,"
        +"version,del_flag FROM pu_purchase_requisition WHERE tenant_id=#{tenantId} "
        +"AND requisition_id IN ("+REQUISITION_IDS+") ORDER BY requisition_id FOR UPDATE")
    List<RequisitionHead> lockRequisitionHeadsForOrder(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,line_id,requisition_id,material_id,unit,apply_quantity,approved_quantity,source_type,source_id,"
        +"DATE_FORMAT(required_date,'%Y-%m-%d') AS required_date,del_flag FROM pu_purchase_requisition_line "
        +"WHERE tenant_id=#{tenantId} AND requisition_id IN ("+REQUISITION_IDS+") ORDER BY line_id FOR UPDATE")
    List<RequisitionLine> lockRequisitionLinesForOrder(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT c.tenant_id,c.request_key,c.request_hash,c.actor_id,c.entry_type,c.material_id,c.vendor_id,c.requisition_id,"
        +"c.requisition_no,c.source_fingerprint,c.result_fingerprint,c.command_status,c.catalog_fingerprint,"
        +"DATE_FORMAT(c.window_end,'%Y-%m-%d') AS window_end FROM pu_certified_purchase_command c "
        +"WHERE c.tenant_id=#{tenantId} AND (c.requisition_id IN ("+REQUISITION_IDS+") "
        +"OR c.request_key IN (SELECT r.decision_idempotency_key FROM pu_purchase_requisition r WHERE r.tenant_id=#{tenantId} "
        +"AND r.requisition_id IN ("+REQUISITION_IDS+")) OR EXISTS (SELECT 1 FROM pu_requirement_allocation_ledger a "
        +"WHERE a.tenant_id=c.tenant_id AND a.decision_idempotency_key=c.request_key AND "+DIRECT_ALLOCATION+")) "
        +"ORDER BY c.request_key FOR UPDATE")
    List<CommandSource> lockCommandsForOrder(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT a.* FROM pu_requirement_allocation_ledger a WHERE a.tenant_id=#{tenantId} AND ("+DIRECT_ALLOCATION
        +" OR a.requisition_line_id IN (SELECT r.line_id FROM pu_purchase_requisition_line r WHERE r.tenant_id=#{tenantId} "
        +"AND r.requisition_id IN ("+REQUISITION_IDS+")) OR a.decision_idempotency_key IN (SELECT c.request_key "
        +"FROM pu_certified_purchase_command c WHERE c.tenant_id=#{tenantId} AND c.requisition_id IN ("+REQUISITION_IDS+"))) "
        +"ORDER BY a.allocation_id FOR UPDATE")
    List<PuRequirementAllocationLedger> lockAllocationsForOrder(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT * FROM pu_requirement_allocation_event WHERE tenant_id=#{tenantId} AND event_key=#{key} FOR UPDATE")
    PuRequirementAllocationEvent lockReceiptEvent(@Param("tenantId") String tenantId,@Param("key") String key);
}
