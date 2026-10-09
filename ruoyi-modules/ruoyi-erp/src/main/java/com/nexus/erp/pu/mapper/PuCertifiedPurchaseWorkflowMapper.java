package com.nexus.erp.pu.mapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** Document-wide, tenant-explicit SQL; all source/quantity authority stays in locked ERP rows. */
@Mapper
@InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface PuCertifiedPurchaseWorkflowMapper {
    /** Routing hint only. Re-read the immutable command after acquiring its material guard. */
    @Select("SELECT material_id FROM pu_certified_purchase_command WHERE tenant_id=#{tenantId} AND requisition_id=#{id}")
    Long findMaterial(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,requisition_id,requisition_no,source_type,status,decision_idempotency_key AS request_key,version,del_flag,select_flow_id "
        +"FROM pu_purchase_requisition WHERE tenant_id=#{tenantId} AND requisition_id=#{id} FOR UPDATE")
    Head lockHead(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,order_id,requisition_id,order_no,status,del_flag,vendor_id FROM pu_purchase_order "
        +"WHERE tenant_id=#{tenantId} AND requisition_id=#{id} ORDER BY order_id FOR UPDATE")
    List<PurchaseOrder> lockOrders(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,vendor_id,vendor_code,vendor_name,status,del_flag FROM bd_vendor "
        +"WHERE tenant_id=#{tenantId} AND vendor_id=#{id} FOR UPDATE")
    Vendor lockVendor(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Update("UPDATE pu_purchase_requisition SET status=#{next},version=version+1,update_by=#{actorId},update_time=NOW() "
        +"WHERE tenant_id=#{tenantId} AND requisition_id=#{id} AND decision_idempotency_key=#{requestKey} "
        +"AND status=#{previous} AND version=#{version} AND del_flag='0'")
    int casHead(@Param("tenantId") String tenantId,@Param("id") Long id,@Param("requestKey") String key,
        @Param("previous") String previous,@Param("next") String next,@Param("version") Integer version,@Param("actorId") Long actor);

    @Update("UPDATE pu_purchase_requisition_line SET approved_quantity=apply_quantity,update_by=#{actorId},update_time=NOW() "
        +"WHERE tenant_id=#{tenantId} AND line_id=#{line.lineId} AND requisition_id=#{line.requisitionId} "
        +"AND material_id=#{line.materialId} AND source_type='MRP' AND source_id=#{line.sourceId} "
        +"AND unit=#{line.unit} AND apply_quantity=#{line.applyQuantity} AND approved_quantity=0 AND del_flag='0'")
    int approveLine(@Param("tenantId") String tenantId,@Param("line") RequisitionLine line,@Param("actorId") Long actor);

    /**
     * 显式写入认证转单的出生版本0及空报价引用，不依赖存量列的默认值。
     * 只作用于本次新建未定价草稿；不补写历史NULL版本，也不伪造价格或到货承诺。
     */
    @Insert("INSERT INTO pu_purchase_order (order_id,tenant_id,order_no,requisition_id,requisition_no,vendor_id,vendor_code,vendor_name,"
        +"order_date,expected_arrival_date,status,certified_version,certified_terms_id,buyer_id,buyer_name,create_by,create_dept,create_time,del_flag) VALUES ("
        +"#{id},#{tenantId},#{number},#{head.requisitionId},#{head.requisitionNo},#{vendor.vendorId},#{vendor.vendorCode},#{vendor.vendorName},"
        +"NOW(),NULL,'DRAFT',0,NULL,#{actorId},#{actorName},#{actorId},#{deptId},NOW(),'0')")
    int insertOrder(@Param("tenantId") String tenantId,@Param("id") Long id,@Param("number") String number,
        @Param("head") Head head,@Param("vendor") Vendor vendor,@Param("actorId") Long actor,@Param("actorName") String actorName,@Param("deptId") Long dept);

    @Insert("INSERT INTO pu_purchase_order_line (line_id,tenant_id,order_id,requisition_line_id,material_id,unit,"
        +"order_quantity,received_quantity,returned_quantity,receive_status,create_by,create_dept,create_time,del_flag) VALUES ("
        +"#{id},#{tenantId},#{orderId},#{source.lineId},#{source.materialId},#{source.unit},#{source.applyQuantity},0,0,'PENDING',#{actorId},#{deptId},NOW(),'0')")
    int insertOrderLine(@Param("tenantId") String tenantId,@Param("id") Long id,@Param("orderId") Long orderId,
        @Param("source") RequisitionLine source,@Param("actorId") Long actor,@Param("deptId") Long dept);

    record Head(String tenantId,Long requisitionId,String requisitionNo,String sourceType,String status,
        String requestKey,Integer version,String delFlag,Long selectFlowId) { }
    record PurchaseOrder(String tenantId,Long orderId,Long requisitionId,String orderNo,String status,String delFlag,Long vendorId) { }
    /** 供应商原始状态与领域模型一致，保留 ACTIVE 等字符串，不做数字映射。 */
    record Vendor(String tenantId,Long vendorId,String vendorCode,String vendorName,String status,String delFlag) { }
}
