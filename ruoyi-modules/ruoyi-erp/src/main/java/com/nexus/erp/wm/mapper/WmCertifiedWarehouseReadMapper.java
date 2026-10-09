package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 仓储原生只读：每个业务表显式租户，不锁行、不签发写资格、不返回订单价格。 */
@Mapper
@InterceptorIgnore(tenantLine="true")
public interface WmCertifiedWarehouseReadMapper {
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE DATE_FORMAT(CURRENT_DATE(),'%Y-%m-%d') WHERE #{tenant}=#{tenant}")
    String databaseDate(@Param("tenant") String tenant);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE tenant_id,order_id,order_no,requisition_id,vendor_id,status,certified_version AS version,certified_terms_id AS terms_id,select_flow_id,del_flag,create_by,create_time,update_by,update_time FROM pu_purchase_order WHERE tenant_id=#{tenant} AND order_id=#{id}")
    Map<String,Object> order(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE tenant_id,line_id,order_id,requisition_line_id,material_id,material_code,material_name,material_spec,unit AS base_uom_id,CAST(order_quantity AS CHAR) AS order_quantity,CAST(received_quantity AS CHAR) AS received_quantity,CAST(returned_quantity AS CHAR) AS returned_quantity,del_flag FROM pu_purchase_order_line WHERE tenant_id=#{tenant} AND order_id=#{id} ORDER BY line_id")
    List<Map<String,Object>> orderLines(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE c.tenant_id,c.requisition_id,c.request_key,c.actor_id,c.material_id,c.vendor_id,c.command_status,c.source_fingerprint,c.request_hash,c.result_fingerprint,c.catalog_fingerprint,r.status AS requisition_status,r.decision_idempotency_key FROM pu_purchase_order o JOIN pu_certified_purchase_command c ON c.tenant_id=o.tenant_id AND c.requisition_id=o.requisition_id LEFT JOIN pu_purchase_requisition r ON r.tenant_id=o.tenant_id AND r.requisition_id=o.requisition_id WHERE o.tenant_id=#{tenant} AND o.order_id=#{id} ORDER BY c.request_key")
    List<Map<String,Object>> orderSources(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE command_id,tenant_id,order_id,actor_id,operation,expected_version,result_version,result_status,quote_id,quote_hash,occurred_at FROM pu_certified_purchase_order_operation WHERE tenant_id=#{tenant} AND order_id=#{id} AND operation='APPROVE_ORDER'")
    List<Map<String,Object>> approvals(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE order_id FROM pu_purchase_order WHERE tenant_id=#{tenant} AND order_no=#{code} AND del_flag='0' ORDER BY order_id")
    List<Long> orderIdsByCode(@Param("tenant") String tenant,@Param("code") String code);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE h.* FROM wm_arrival_notice h WHERE h.tenant_id=#{tenant} AND h.notice_id=#{id}")
    Map<String,Object> notice(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE l.*,CAST(l.quantity_arrival AS CHAR) AS quantity,CAST(l.received_qty AS CHAR) AS received_exact FROM wm_arrival_notice_line l WHERE l.tenant_id=#{tenant} AND l.notice_id=#{id} ORDER BY l.line_id")
    List<Map<String,Object>> noticeLines(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE c.*,CAST(c.quantity AS CHAR) AS quantity_exact FROM wm_certified_arrival_command c WHERE c.tenant_id=#{tenant} AND c.notice_id=#{id} ORDER BY c.notice_line_id")
    List<Map<String,Object>> noticeCreations(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE h.*,DATE_FORMAT(h.shelf_time,'%Y-%m-%d %H:%i:%s') AS shelf_time_exact FROM wm_material_receive h WHERE h.tenant_id=#{tenant} AND h.receive_id=#{id}")
    Map<String,Object> receive(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE l.*,CAST(l.quantity_received AS CHAR) AS quantity FROM wm_material_receive_line l WHERE l.tenant_id=#{tenant} AND l.receive_id=#{id} ORDER BY l.line_id")
    List<Map<String,Object>> receiveLines(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE d.*,CAST(d.quantity AS CHAR) AS quantity_exact FROM wm_material_receive_detail d WHERE d.tenant_id=#{tenant} AND d.receive_id=#{id} ORDER BY d.detail_id")
    List<Map<String,Object>> receiveDetails(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE c.*,CAST(c.quantity AS CHAR) AS quantity_exact FROM wm_certified_receive_command c WHERE c.tenant_id=#{tenant} AND c.receive_id=#{id} ORDER BY c.line_id")
    List<Map<String,Object>> receiveCreations(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE p.*,CAST(p.total_quantity AS CHAR) AS quantity FROM wm_certified_receipt_posting p WHERE p.tenant_id=#{tenant} AND p.receive_id=#{id}")
    Map<String,Object> posting(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE COUNT(*) FROM wm_receipt_iqc_birth WHERE tenant_id=#{tenant} AND receive_id=#{id}")
    Long birthCount(@Param("tenant") String tenant,@Param("id") Long id);

    /** 只显示 ERP 原永久 IQC/出生事实，不制造检验结果、不调用 MES 写或 MQ。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE e.*,CAST(e.inspected_quantity AS CHAR) AS inspected_exact FROM wm_certified_iqc_evidence e WHERE e.tenant_id=#{tenant} AND e.receive_id=#{id} ORDER BY e.material_id,e.evidence_id")
    List<Map<String,Object>> iqcEvidence(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE b.*,CAST(b.quantity AS CHAR) AS quantity_exact FROM wm_receipt_iqc_birth b WHERE b.tenant_id=#{tenant} AND b.receive_id=#{id} ORDER BY b.material_id,b.birth_id")
    List<Map<String,Object>> births(@Param("tenant") String tenant,@Param("id") Long id);

    /** 只在 public master 只读方法中调用；不借 ERP 同类自调用切库。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE tenant_id,data_id,service_impl_name,process_instance_id,process_definition_id,proposer FROM wf_my_business WHERE tenant_id=#{tenant} AND data_id=#{id} ORDER BY id")
    List<Map<String,Object>> workflowBindings(@Param("tenant") String tenant,@Param("id") String id);
}
