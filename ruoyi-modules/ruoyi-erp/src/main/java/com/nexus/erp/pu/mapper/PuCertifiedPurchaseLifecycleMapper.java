package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 固定且显式指定租户的当前读；不提供通用 CRUD，也不将调用方提供的数量作为权威依据。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface PuCertifiedPurchaseLifecycleMapper {
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    Date databaseNow();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,material_id,base_uom_id,status,del_flag FROM bd_material "
        + "WHERE tenant_id=#{tenantId} AND material_id=#{id} FOR UPDATE")
    Material lockMaterial(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,uom_id,uom_name,is_base,primary_id,conversion_rate,quantity_scale,"
        + "conversion_version,conversion_verified_at AS verified_at,status,del_flag FROM bd_unit_of_measure "
        + "WHERE tenant_id=#{tenantId} AND uom_id=#{id} FOR UPDATE")
    RootUnit lockRootUnit(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,detail_id,requirement_id,material_id,status,del_flag "
        + "FROM prd_material_requirement_detail WHERE tenant_id=#{tenantId} AND detail_id=#{id} FOR UPDATE")
    DemandDetail lockDemandDetail(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,requirement_id,status,del_flag FROM prd_material_requirement "
        + "WHERE tenant_id=#{tenantId} AND requirement_id=#{id} FOR UPDATE")
    DemandHead lockDemandHead(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,line_id,requisition_id,material_id,unit,apply_quantity,approved_quantity,"
        + "source_type,source_id,DATE_FORMAT(required_date,'%Y-%m-%d') AS required_date,del_flag FROM pu_purchase_requisition_line "
        + "WHERE tenant_id=#{tenantId} AND line_id=#{id} FOR UPDATE")
    RequisitionLine lockRequisitionLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,line_id,requisition_id,material_id,unit,apply_quantity,approved_quantity,"
        + "source_type,source_id,DATE_FORMAT(required_date,'%Y-%m-%d') AS required_date,del_flag FROM pu_purchase_requisition_line "
        + "WHERE tenant_id=#{tenantId} AND requisition_id=#{id} ORDER BY line_id FOR UPDATE")
    List<RequisitionLine> lockRequisitionLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,requisition_id,requisition_no,source_type,status,decision_idempotency_key AS request_key,"
        + "version,del_flag FROM pu_purchase_requisition WHERE tenant_id=#{tenantId} AND requisition_id=#{id} FOR UPDATE")
    RequisitionHead lockRequisitionHead(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,request_key,request_hash,actor_id,entry_type,material_id,vendor_id,"
        + "requisition_id,requisition_no,source_fingerprint,result_fingerprint,command_status,catalog_fingerprint,DATE_FORMAT(window_end,'%Y-%m-%d') AS window_end "
        + "FROM pu_certified_purchase_command WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} FOR UPDATE")
    CommandSource lockCommand(@Param("tenantId") String tenantId, @Param("requestKey") String requestKey);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
        + "AND TABLE_NAME='pu_certified_purchase_command'")
    String commandTableEngine();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT SQL_NO_CACHE request_key FROM pu_certified_purchase_command WHERE 1=0")
    List<String> pinCommandDefinition();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_ledger WHERE tenant_id=#{tenantId} "
        + "AND (requisition_line_id=#{requisitionLineId} OR order_line_id=#{orderLineId}) ORDER BY allocation_id FOR UPDATE")
    List<PuRequirementAllocationLedger> lockRelatedAllocations(@Param("tenantId") String tenantId,
        @Param("requisitionLineId") Long requisitionLineId, @Param("orderLineId") Long orderLineId);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT l.* FROM pu_requirement_allocation_ledger l JOIN pu_purchase_requisition_line r "
        + "ON r.tenant_id=l.tenant_id AND r.line_id=l.requisition_line_id "
        + "WHERE l.tenant_id=#{tenantId} AND r.requisition_id=#{requisitionId} ORDER BY l.material_id,l.allocation_id FOR UPDATE")
    List<PuRequirementAllocationLedger> lockRequisitionAllocations(@Param("tenantId") String tenantId,
        @Param("requisitionId") Long requisitionId);

    /** 包含已脱离关联的行：行引用损坏不能掩盖永久请求占用记录。 */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM pu_requirement_allocation_ledger WHERE tenant_id=#{tenantId} "
        + "AND decision_idempotency_key=#{requestKey} ORDER BY material_id,allocation_id FOR UPDATE")
    List<PuRequirementAllocationLedger> lockCommandAllocations(@Param("tenantId") String tenantId,
        @Param("requestKey") String requestKey);

    String ORDER_LINE_COLUMNS = "tenant_id,line_id,order_id,requisition_line_id,material_id,unit,"
        + "order_quantity,received_quantity,returned_quantity,receive_status,del_flag";

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT " + ORDER_LINE_COLUMNS + " FROM pu_purchase_order_line "
        + "WHERE tenant_id=#{tenantId} AND requisition_line_id=#{id} ORDER BY line_id FOR UPDATE")
    List<OrderLine> lockOrdersForRequisitionLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT " + ORDER_LINE_COLUMNS + " FROM pu_purchase_order_line "
        + "WHERE tenant_id=#{tenantId} AND order_id=#{id} ORDER BY line_id FOR UPDATE")
    List<OrderLine> lockOrderLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,order_id,requisition_id,status,del_flag FROM pu_purchase_order "
        + "WHERE tenant_id=#{tenantId} AND order_id=#{id} FOR UPDATE")
    OrderHead lockOrderHead(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,line_id,receive_id,purchase_line_id,material_id,base_uom_id,quantity_received,del_flag "
        + "FROM wm_material_receive_line WHERE tenant_id=#{tenantId} AND receive_id=#{id} ORDER BY line_id FOR UPDATE")
    List<ReceiveLine> lockReceiveLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,line_id,receive_id,purchase_line_id,material_id,base_uom_id,quantity_received,del_flag "
        + "FROM wm_material_receive_line WHERE tenant_id=#{tenantId} AND line_id=#{id} FOR UPDATE")
    ReceiveLine lockReceiveLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,receive_id,pur_id AS order_id,status,del_flag FROM wm_material_receive "
        + "WHERE tenant_id=#{tenantId} AND receive_id=#{id} FOR UPDATE")
    ReceiveHead lockReceiveHead(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT l.tenant_id,l.line_id,l.return_id,l.order_line_id,l.material_id,l.unit,l.return_quantity,"
        + "h.order_id,h.status,h.del_flag AS del_flag,l.del_flag AS line_del_flag FROM pu_purchase_return_line l JOIN pu_purchase_return h "
        + "ON h.tenant_id=l.tenant_id AND h.return_id=l.return_id "
        + "WHERE l.tenant_id=#{tenantId} AND l.line_id=#{id} FOR UPDATE")
    ReturnLine lockReturnLine(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT tenant_id,tx_id,tx_type,material_id,base_uom_id,source_doc_type,source_doc_id,"
        + "source_doc_line_id,tx_flag,tx_quantity AS quantity,before_quantity,after_quantity,material_stock_id AS stock_id,create_by AS actor_id,del_flag "
        + "FROM wm_material_stock_tx WHERE tenant_id=#{tenantId} AND source_doc_type=#{type} "
        + "AND source_doc_id=#{id} ORDER BY tx_id FOR UPDATE")
    List<StockEffect> lockStockEffects(@Param("tenantId") String tenantId, @Param("type") String type, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT l.tenant_id,l.line_id,l.receive_id AS document_id,h.status,h.del_flag AS del_flag,l.del_flag AS line_del_flag FROM wm_material_receive_line l "
        + "LEFT JOIN wm_material_receive h ON h.tenant_id=l.tenant_id AND h.receive_id=l.receive_id "
        + "WHERE l.tenant_id=#{tenantId} AND l.purchase_line_id=#{id} ORDER BY l.line_id FOR UPDATE")
    List<MovementSource> lockReceiptSources(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT l.tenant_id,l.line_id,l.return_id AS document_id,h.status,h.del_flag AS del_flag,l.del_flag AS line_del_flag FROM pu_purchase_return_line l "
        + "LEFT JOIN pu_purchase_return h ON h.tenant_id=l.tenant_id AND h.return_id=l.return_id "
        + "WHERE l.tenant_id=#{tenantId} AND l.order_line_id=#{id} ORDER BY l.line_id FOR UPDATE")
    List<MovementSource> lockReturnSources(@Param("tenantId") String tenantId, @Param("id") Long id);

    /** 通过行锁与精确匹配旧计数值，保护没有版本列的历史行。 */
    @Update("UPDATE pu_purchase_order_line SET received_quantity=#{received},returned_quantity=#{returned},"
        + "receive_status=#{receiveStatus},update_by=#{actorId},update_time=NOW() "
        + "WHERE tenant_id=#{tenantId} AND line_id=#{old.lineId} AND order_id=#{old.orderId} "
        + "AND requisition_line_id=#{old.requisitionLineId} AND material_id=#{old.materialId} "
        + "AND unit=#{old.unit} AND order_quantity=#{old.orderQuantity} "
        + "AND received_quantity=#{old.receivedQuantity} AND returned_quantity=#{old.returnedQuantity} AND del_flag='0'")
    int updateOrderCounters(@Param("tenantId") String tenantId, @Param("old") OrderLine old,
        @Param("received") BigDecimal received, @Param("returned") BigDecimal returned,
        @Param("receiveStatus") String receiveStatus, @Param("actorId") Long actorId);

    @Update("UPDATE pu_purchase_order SET status=#{status},update_by=#{actorId},update_time=NOW() "
        + "WHERE tenant_id=#{tenantId} AND order_id=#{id} AND status=#{expectedStatus} AND del_flag='0'")
    int updateOrderStatus(@Param("tenantId") String tenantId, @Param("id") Long id,
        @Param("expectedStatus") String expectedStatus, @Param("status") String status, @Param("actorId") Long actorId);

    @Update("UPDATE pu_certified_purchase_command SET command_status='COMPENSATED' "
        + "WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} AND requisition_id=#{id} AND command_status='COMMITTED'")
    int markCommandCompensated(@Param("tenantId") String tenantId, @Param("requestKey") String requestKey, @Param("id") Long id);

    @Update("UPDATE pu_purchase_requisition SET del_flag='1',status='CANCELLED',version=version+1,"
        + "update_by=#{actorId},update_time=NOW() WHERE tenant_id=#{tenantId} AND requisition_id=#{id} "
        + "AND decision_idempotency_key=#{requestKey} AND status='DRAFT' AND version=#{version} AND del_flag='0'")
    int compensateDraft(@Param("tenantId") String tenantId, @Param("id") Long id,
        @Param("requestKey") String requestKey, @Param("version") Integer version, @Param("actorId") Long actorId);

    @Update("UPDATE pu_purchase_requisition_line SET del_flag='1',update_by=#{actorId},update_time=NOW() "
        + "WHERE tenant_id=#{tenantId} AND requisition_id=#{id} AND del_flag='0'")
    int compensateDraftLines(@Param("tenantId") String tenantId, @Param("id") Long id, @Param("actorId") Long actorId);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,engine FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name IN ("
        +"'pu_certified_purchase_command','pu_requirement_allocation_ledger','pu_requirement_allocation_event',"
        +"'wm_material_receive','wm_material_receive_line','wm_material_stock_tx','pu_purchase_return','pu_purchase_return_line')")
    List<TableDefinition> lifecycleTableDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE requisition_id FROM pu_certified_purchase_command WHERE 1=0 UNION ALL "
        +"SELECT allocation_id FROM pu_requirement_allocation_ledger WHERE 1=0 UNION ALL "
        +"SELECT event_id FROM pu_requirement_allocation_event WHERE 1=0")
    List<Long> pinAllocationDefinitions();

    /** 通过真实空读获取各表的 MDL；仅查询 information_schema 不能固定表定义。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE requisition_id FROM pu_certified_purchase_command WHERE 1=0 UNION ALL "
        +"SELECT allocation_id FROM pu_requirement_allocation_ledger WHERE 1=0 UNION ALL "
        +"SELECT event_id FROM pu_requirement_allocation_event WHERE 1=0 UNION ALL "
        +"SELECT receive_id FROM wm_material_receive WHERE 1=0 UNION ALL "
        +"SELECT line_id FROM wm_material_receive_line WHERE 1=0 UNION ALL "
        +"SELECT tx_id FROM wm_material_stock_tx WHERE 1=0 UNION ALL "
        +"SELECT return_id FROM pu_purchase_return WHERE 1=0 UNION ALL "
        +"SELECT line_id FROM pu_purchase_return_line WHERE 1=0")
    List<Long> pinLifecycleDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,column_name,data_type,numeric_precision AS `precision`,numeric_scale AS `scale` FROM information_schema.COLUMNS "
        +"WHERE table_schema=DATABASE() AND table_name IN ('wm_material_receive_line','wm_material_stock_tx','pu_purchase_return_line') "
        +"AND column_name IN ('quantity_received','tx_quantity','before_quantity','after_quantity','return_quantity')")
    /** 保留列名映射，并引用 MySQL 保留字别名，避免运行态元数据读取失败。 */
    List<NativeQuantityDefinition> lifecycleQuantityDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,index_name,non_unique,seq_in_index AS `sequence`,column_name,sub_part FROM information_schema.STATISTICS "
        +"WHERE table_schema=DATABASE() AND table_name IN ('pu_certified_purchase_command','pu_requirement_allocation_ledger','pu_requirement_allocation_event') "
        +"ORDER BY table_name,index_name,seq_in_index")
    List<IndexDefinition> lifecycleUniqueDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,relation_id,material_id,vendor_id,order_uom_id,moq_uom_id,verified_moq_qty AS moq,order_multiple AS multiple,"
        +"allow_split,units_per_pack,terms_version AS version,terms_effective_at AS effective_at,terms_expires_at AS expires_at,terms_verified_at AS verified_at,status,del_flag "
        +"FROM bd_material_vendor WHERE tenant_id=#{tenantId} AND material_id=#{materialId} AND vendor_id=#{vendorId} "
        +"ORDER BY relation_id FOR UPDATE")
    List<PurchaseTerms> lockPurchaseTerms(@Param("tenantId") String tenantId,@Param("materialId") Long materialId,@Param("vendorId") Long vendorId);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,vendor_id,status,del_flag FROM bd_vendor WHERE tenant_id=#{tenantId} AND vendor_id=#{id} FOR UPDATE")
    VendorIdentity lockLifecycleVendor(@Param("tenantId") String tenantId,@Param("id") Long id);
    /** 供应商主档使用字符串状态；不得把 ACTIVE 强制读取或改写为数值状态。 */
    record VendorIdentity(String tenantId,Long vendorId,String status,String delFlag) { }

    /** 检查元数据前先获取整订单相关表的 MDL，包括新增报价/审计/永久回执来源。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE certified_version FROM pu_purchase_order WHERE 1=0 UNION ALL "
        +"SELECT line_id FROM pu_purchase_order_line WHERE 1=0 UNION ALL "
        +"SELECT quote_id FROM pu_certified_purchase_order_terms WHERE 1=0 UNION ALL "
        +"SELECT confirmation_id FROM pu_certified_purchase_order_terms_confirmation WHERE 1=0 UNION ALL "
        +"SELECT command_id FROM pu_certified_purchase_order_operation WHERE 1=0")
    List<Long> pinOrderDefinitions();
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,engine FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name IN "
        +"('pu_purchase_order','pu_purchase_order_line','pu_certified_purchase_order_terms','pu_certified_purchase_order_terms_confirmation','pu_certified_purchase_order_operation')")
    List<TableDefinition> orderTableDefinitions();
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,column_name,data_type,numeric_precision AS `precision`,numeric_scale AS `scale`,character_maximum_length AS max_length,is_nullable AS `nullable` "
        +"FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name IN "
        +"('pu_purchase_order','pu_purchase_order_line','pu_certified_purchase_order_terms','pu_certified_purchase_order_terms_confirmation','pu_certified_purchase_order_operation')")
    List<OrderColumnDefinition> orderColumnDefinitions();
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT table_name,index_name,non_unique,seq_in_index AS `sequence`,column_name,sub_part FROM information_schema.STATISTICS "
        +"WHERE table_schema=DATABASE() AND table_name IN ('pu_certified_purchase_order_terms','pu_certified_purchase_order_terms_confirmation','pu_certified_purchase_order_operation') "
        +"ORDER BY table_name,index_name,seq_in_index")
    List<IndexDefinition> orderUniqueDefinitions();
    record OrderColumnDefinition(String tableName,String columnName,String dataType,Integer precision,Integer scale,Long maxLength,String nullable) { }

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT a.* FROM pu_requirement_allocation_ledger a JOIN pu_purchase_order_line l ON l.tenant_id=a.tenant_id AND l.line_id=a.order_line_id "
        +"WHERE a.tenant_id=#{tenantId} AND l.order_id=#{id} ORDER BY a.allocation_id FOR UPDATE")
    List<PuRequirementAllocationLedger> lockOrderAllocations(@Param("tenantId") String tenantId,@Param("id") Long orderId);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT o.tenant_id,o.order_id,o.vendor_id,o.certified_terms_id AS quote_id,o.certified_version AS version,o.total_amount,o.amount_excluding_tax,o.tax_amount,"
        +"DATE_FORMAT(o.expected_arrival_date,'%Y-%m-%d') AS expected_arrival_date,q.material_id,q.base_uom_id,q.source_request_key,q.catalog_fingerprint,q.quote_hash,"
        +"q.entered_by,c.actor_id AS confirmed_by,c.quote_hash AS confirmed_hash,c.permission_code,c.confirmed_at "
        +"FROM pu_purchase_order o JOIN pu_certified_purchase_order_terms q ON q.tenant_id=o.tenant_id AND q.quote_id=o.certified_terms_id AND q.order_id=o.order_id AND q.vendor_id=o.vendor_id "
        +"JOIN pu_certified_purchase_order_terms_confirmation c ON c.tenant_id=q.tenant_id AND c.quote_id=q.quote_id "
        +"WHERE o.tenant_id=#{tenantId} AND o.order_id=#{id} FOR UPDATE")
    OrderApprovalSource lockOrderApprovalSource(@Param("tenantId") String tenantId,@Param("id") Long id);
    record OrderApprovalSource(String tenantId,Long orderId,Long vendorId,Long quoteId,Integer version,BigDecimal totalAmount,BigDecimal amountExcludingTax,
        BigDecimal taxAmount,String expectedArrivalDate,Long materialId,Long baseUomId,String sourceRequestKey,String catalogFingerprint,String quoteHash,
        Long enteredBy,Long confirmedBy,String confirmedHash,String permissionCode,Date confirmedAt) { }

    record TableDefinition(String tableName,String engine) { }
    record NativeQuantityDefinition(String tableName,String columnName,String dataType,Integer precision,Integer scale) { }
    record IndexDefinition(String tableName,String indexName,Integer nonUnique,Integer sequence,String columnName,Integer subPart) { }
    record PurchaseTerms(String tenantId,Long relationId,Long materialId,Long vendorId,Long orderUomId,Long moqUomId,
        BigDecimal moq,BigDecimal multiple,Integer allowSplit,BigDecimal unitsPerPack,String version,
        Date effectiveAt,Date expiresAt,Date verifiedAt,Long status,String delFlag) { }

    record Material(String tenantId, Long materialId, Long baseUomId, Long status, String delFlag) { }
    record RootUnit(String tenantId, Long uomId, String uomName, Long isBase, Long primaryId,
        BigDecimal conversionRate, Integer quantityScale, String conversionVersion, Date verifiedAt, Long status, String delFlag) { }
    record DemandDetail(String tenantId, Long detailId, Long requirementId, Long materialId, Long status, String delFlag) { }
    record DemandHead(String tenantId, Long requirementId, String status, String delFlag) { }
    record RequisitionLine(String tenantId, Long lineId, Long requisitionId, Long materialId, String unit,
        BigDecimal applyQuantity, BigDecimal approvedQuantity, String sourceType, Long sourceId, String requiredDate, String delFlag) { }
    record RequisitionHead(String tenantId, Long requisitionId, String requisitionNo, String sourceType,
        String status, String requestKey, Integer version, String delFlag) { }
    record CommandSource(String tenantId, String requestKey, String requestHash, Long actorId, String entryType,
        Long materialId, Long vendorId, Long requisitionId, String requisitionNo, String sourceFingerprint,
        String resultFingerprint, String commandStatus, String catalogFingerprint, String windowEnd) { }
    record OrderLine(String tenantId, Long lineId, Long orderId, Long requisitionLineId, Long materialId,
        String unit, BigDecimal orderQuantity, BigDecimal receivedQuantity, BigDecimal returnedQuantity,
        String receiveStatus, String delFlag) { }
    record OrderHead(String tenantId, Long orderId, Long requisitionId, String status, String delFlag) { }
    record ReceiveLine(String tenantId, Long lineId, Long receiveId, Long purchaseLineId, Long materialId,
        String baseUomId, BigDecimal quantityReceived, String delFlag) { }
    record ReceiveHead(String tenantId, Long receiveId, Long orderId, String status, String delFlag) { }
    record ReturnLine(String tenantId, Long lineId, Long returnId, Long orderLineId, Long materialId,
        String unit, BigDecimal returnQuantity, Long orderId, String status, String delFlag, String lineDelFlag) { }
    record StockEffect(String tenantId, Long txId, String txType, Long materialId, String baseUomId,
        String sourceDocType, Long sourceDocId, Long sourceDocLineId, Integer txFlag, BigDecimal quantity,
        BigDecimal beforeQuantity, BigDecimal afterQuantity, Long stockId, Long actorId, String delFlag) { }
    record MovementSource(String tenantId, Long lineId, Long documentId, String status, String delFlag, String lineDelFlag) { }
}
