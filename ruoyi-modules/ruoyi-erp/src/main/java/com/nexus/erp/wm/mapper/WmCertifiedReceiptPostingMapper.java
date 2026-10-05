package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 原字段当前读；不 CAST 浮点，不接受上传的余额/有效期/单位认证。 */
public interface WmCertifiedReceiptPostingMapper {
    /** 出生范围仅冻结头/行身份和数量；仓储分配在实际过账前仍走原父链/数量门禁。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT attr1 FROM wm_material_receive WHERE tenant_id=#{tenant} AND receive_id=#{id} FOR UPDATE")
    String lockCreationKind(@Param("tenant") String tenant,@Param("id") Long id);
    /** 永久请求只追加；已锁收货头后只读冻结事实，避免与原键重放形成反向锁序。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT line_id,material_id,base_uom_id,quantity FROM wm_certified_receive_command WHERE tenant_id=#{tenant} AND receive_id=#{id} ORDER BY line_id")
    List<FrozenDraftLine> readFrozenDraftLines(@Param("tenant") String tenant,@Param("id") Long id);
    record FrozenDraftLine(Long lineId,Long materialId,String baseUomId,BigDecimal quantity) { }
    /** 原永久草稿命令的最早数据库创建时刻；仅只读不可变事实，避免头锁后新增反向锁序。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT MIN(UNIX_TIMESTAMP(create_time)*1000) FROM wm_certified_receive_command WHERE tenant_id=#{tenant} AND receive_id=#{id}")
    BigDecimal readDraftCreatedAt(@Param("tenant") String tenant,@Param("id") Long id);
    @org.apache.ibatis.annotations.Select("SELECT birth_id FROM wm_receipt_iqc_birth WHERE tenant_id=#{tenant} AND receive_id=#{id} ORDER BY birth_id FOR UPDATE")
    java.util.List<Long> lockBirthIdentities(@org.apache.ibatis.annotations.Param("tenant") String tenant, @org.apache.ibatis.annotations.Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT DISTINCT material_id FROM wm_material_receive_line WHERE tenant_id=#{tenant} AND receive_id=#{id} ORDER BY material_id")
    List<Long> discoverMaterials(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT o.order_id FROM pu_purchase_order o JOIN pu_certified_purchase_command c ON c.requisition_id=o.requisition_id AND c.tenant_id=o.tenant_id WHERE o.tenant_id=#{tenant} AND o.order_id=#{id} ORDER BY c.request_key FOR UPDATE")
    List<Long> lockCertifiedOrderIdentities(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,line_id lineId FROM wm_material_receive_line WHERE tenant_id=#{tenant} AND line_id=#{id}")
    SourceIdentity discoverLineIdentity(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,line_id lineId FROM wm_material_receive_line WHERE tenant_id=#{tenant} AND line_id=#{id} FOR UPDATE")
    SourceIdentity lockLineIdentity(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,line_id lineId FROM wm_material_receive_detail WHERE tenant_id=#{tenant} AND detail_id=#{id}")
    SourceIdentity discoverDetailIdentity(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,line_id lineId FROM wm_material_receive_detail WHERE tenant_id=#{tenant} AND detail_id=#{id} FOR UPDATE")
    SourceIdentity lockDetailIdentity(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,receive_code receiveCode,pur_id orderId,vendor_id vendorId,status,warehouse_id warehouseId,area_id areaId,location_id locationId,UNIX_TIMESTAMP(receive_date)*1000 receiveAt,UNIX_TIMESTAMP(shelf_time)*1000 shelfAt,del_flag delFlag,notice_id noticeId,notice_code noticeCode FROM wm_material_receive WHERE tenant_id=#{tenant} AND receive_id=#{id} FOR UPDATE")
    Head lockHead(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,line_id lineId,receive_id receiveId,material_id materialId,base_uom_id baseUomId,quantity_received quantity,purchase_line_id purchaseLineId,del_flag delFlag,notice_line_id noticeLineId FROM wm_material_receive_line WHERE tenant_id=#{tenant} AND receive_id=#{id} ORDER BY line_id FOR UPDATE")
    List<Line> lockLines(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,detail_id detailId,receive_id receiveId,line_id lineId,material_id materialId,base_uom_id baseUomId,quantity,warehouse_id warehouseId,area_id areaId,location_id locationId,batch_id batchId,sn_id snId,del_flag delFlag FROM wm_material_receive_detail WHERE tenant_id=#{tenant} AND receive_id=#{id} ORDER BY detail_id FOR UPDATE")
    List<Detail> lockDetails(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT m.tenant_id tenantId,m.material_id materialId,m.base_uom_id baseUomId,m.is_batch_manage batchManaged,m.status,m.del_flag delFlag,u.tenant_id unitTenant,u.quantity_scale quantityScale,u.conversion_version conversionVersion,UNIX_TIMESTAMP(u.conversion_verified_at)*1000 verifiedAt,u.is_base isBase,u.primary_id primaryId,u.conversion_rate conversionRate,u.status unitStatus,u.del_flag unitDelFlag FROM bd_material m LEFT JOIN bd_unit_of_measure u ON u.uom_id=m.base_uom_id AND u.tenant_id=m.tenant_id WHERE m.tenant_id=#{tenant} AND m.material_id=#{id} FOR UPDATE")
    Material lockMaterial(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,order_id orderId,vendor_id vendorId,status,currency,exchange_rate exchangeRate,del_flag delFlag FROM pu_purchase_order WHERE tenant_id=#{tenant} AND order_id=#{id} FOR UPDATE")
    Order lockOrder(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,line_id lineId,order_id orderId,material_id materialId,unit,order_quantity quantity,received_quantity received,returned_quantity returned,unit_price unitPrice,unit_price_excluding_tax netUnitPrice,del_flag delFlag FROM pu_purchase_order_line WHERE tenant_id=#{tenant} AND line_id=#{id} FOR UPDATE")
    OrderLine lockOrderLine(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT l.quantity_received FROM wm_material_receive_line l JOIN wm_material_receive h ON h.receive_id=l.receive_id AND h.tenant_id=l.tenant_id WHERE l.tenant_id=#{tenant} AND l.purchase_line_id=#{id} AND h.status='FINISHED' AND h.del_flag='0' AND l.del_flag='0' ORDER BY l.line_id FOR UPDATE")
    List<BigDecimal> lockPostedQuantities(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,batch_id batchId,batch_code batchCode,material_id materialId,vendor_id vendorId,warehouse_id warehouseId,quantity,received_qty received,issued_qty issued,status,source_doc_type sourceType,source_doc_id sourceId,UNIX_TIMESTAMP(expiry_date)*1000 expiryAt,del_flag delFlag FROM wm_batch WHERE tenant_id=#{tenant} AND batch_id=#{id} FOR UPDATE")
    Batch lockBatch(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT w.tenant_id warehouseTenant,w.warehouse_id warehouseId,w.status warehouseStatus,w.del_flag warehouseDelFlag,a.tenant_id areaTenant,a.area_id areaId,a.warehouse_id areaWarehouseId,a.enable_flag areaEnabled,a.frozen_flag areaFrozen,a.location_flag locationFlag,a.del_flag areaDelFlag,s.tenant_id locationTenant,s.location_id locationId,s.warehouse_id locationWarehouseId,s.area_id locationAreaId,s.enable_flag locationEnabled,s.frozen_flag locationFrozen,s.del_flag locationDelFlag FROM wm_warehouse w JOIN wm_storage_area a ON a.warehouse_id=w.warehouse_id AND a.tenant_id=w.tenant_id LEFT JOIN wm_storage_location s ON s.location_id=#{locationId} AND s.tenant_id=w.tenant_id WHERE w.tenant_id=#{tenant} AND w.warehouse_id=#{warehouseId} AND a.area_id=#{areaId} FOR UPDATE")
    Storage lockStorage(@Param("tenant") String tenant,@Param("warehouseId") Long warehouseId,@Param("areaId") Long areaId,@Param("locationId") Long locationId);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,stock_id stockId,material_id materialId,base_uom_id baseUomId,warehouse_id warehouseId,area_id areaId,location_id locationId,batch_id batchId,sn_id snId,stock_quantity quantity,reserved_quantity reserved,version,del_flag delFlag,UNIX_TIMESTAMP(expire_date)*1000 expiryAt,unit_cost unitCost,total_cost totalCost FROM wm_material_stock WHERE tenant_id=#{tenant} AND material_id=#{id} ORDER BY stock_id FOR UPDATE")
    List<Stock> lockStocks(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tx_id FROM wm_material_stock_tx WHERE tenant_id=#{tenant} AND material_id=#{id} ORDER BY tx_id FOR UPDATE")
    List<Long> lockTransactionIds(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tx_id txId,material_stock_id stockId,source_doc_line_id lineId,base_uom_id baseUomId,tx_quantity quantity,before_quantity beforeQuantity,after_quantity afterQuantity,unit_cost unitCost,tx_amount txAmount,warehouse_id warehouseId,area_id areaId,location_id locationId,batch_id batchId,DATE_FORMAT(tx_date,'%Y-%m-%d %H:%i:%s') postedAt FROM wm_material_stock_tx WHERE tenant_id=#{tenant} AND source_doc_type='WM_MATERIAL_RECEIVE' AND source_doc_id=#{id} AND tx_type='RECEIVE_IN' AND tx_flag=1 AND del_flag='0' ORDER BY tx_id FOR UPDATE")
    List<PostedTx> lockReceiptTransactions(@Param("tenant") String tenant,@Param("id") Long id);

    /** 原单据流水全集当前读：保留删除/异类型行，不能只靠有效 RECEIVE_IN 隐藏额外写入。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tx_id FROM wm_material_stock_tx WHERE tenant_id=#{tenant} AND source_doc_type='WM_MATERIAL_RECEIVE' AND source_doc_id=#{id} ORDER BY tx_id FOR UPDATE")
    List<Long> lockAllReceiptTransactionIds(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id tenantId,receive_id receiveId,material_id materialId,actor_id actorId,source_fingerprint sourceFingerprint,total_quantity totalQuantity,DATE_FORMAT(posted_at,'%Y-%m-%d %H:%i:%s') postedAt,notification_status notificationStatus FROM wm_certified_receipt_posting WHERE tenant_id=#{tenant} AND receive_id=#{id} FOR UPDATE")
    Posting lockPosting(@Param("tenant") String tenant,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s') databaseAt,UNIX_TIMESTAMP(NOW())*1000 epochMillis,@@session.time_zone timeZone")
    Clock clock();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT SQL_NO_CACHE p.receive_id FROM wm_certified_receipt_posting p JOIN wm_material_stock s ON 1=0 JOIN wm_material_stock_tx t ON 1=0 JOIN wm_material_receive h ON 1=0 JOIN wm_material_receive_line l ON 1=0 JOIN wm_material_receive_detail d ON 1=0 JOIN wm_batch b ON 1=0 JOIN bd_material m ON 1=0 JOIN bd_unit_of_measure u ON 1=0 JOIN pu_purchase_order o ON 1=0 JOIN pu_purchase_order_line ol ON 1=0 JOIN wm_warehouse w ON 1=0 JOIN wm_storage_area a ON 1=0 JOIN wm_storage_location x ON 1=0 WHERE 1=0")
    List<Long> pinDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT TABLE_NAME tableName,ENGINE engine FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_certified_receipt_posting','wm_material_stock','wm_material_stock_tx','wm_material_receive','wm_material_receive_line','wm_material_receive_detail','wm_batch','bd_material','bd_unit_of_measure','pu_purchase_order','pu_purchase_order_line','wm_warehouse','wm_storage_area','wm_storage_location')")
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    List<TableDefinition> tableDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT TABLE_NAME tableName,COLUMN_NAME columnName,DATA_TYPE dataType,NUMERIC_PRECISION numericPrecision,NUMERIC_SCALE numericScale FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='wm_material_stock' AND COLUMN_NAME IN ('stock_quantity','reserved_quantity','unit_cost','total_cost')) OR (TABLE_NAME='wm_material_stock_tx' AND COLUMN_NAME IN ('tx_quantity','before_quantity','after_quantity','unit_cost','tx_amount')) OR (TABLE_NAME='wm_material_receive_line' AND COLUMN_NAME='quantity_received') OR (TABLE_NAME='wm_material_receive_detail' AND COLUMN_NAME='quantity') OR (TABLE_NAME='wm_batch' AND COLUMN_NAME IN ('quantity','received_qty','issued_qty')) OR (TABLE_NAME='bd_unit_of_measure' AND COLUMN_NAME='conversion_rate') OR (TABLE_NAME='pu_purchase_order_line' AND COLUMN_NAME IN ('order_quantity','received_quantity','returned_quantity','unit_price','unit_price_excluding_tax')) OR (TABLE_NAME='pu_purchase_order' AND COLUMN_NAME='exchange_rate') OR (TABLE_NAME='wm_certified_receipt_posting' AND COLUMN_NAME='total_quantity'))")
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    List<QuantityDefinition> quantityDefinitions();

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT INDEX_NAME indexName,SEQ_IN_INDEX sequenceNumber,COLUMN_NAME columnName,SUB_PART prefixLength FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receipt_posting' AND NON_UNIQUE=0 ORDER BY INDEX_NAME,SEQ_IN_INDEX")
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    List<KeyDefinition> uniqueDefinitions();

    @Insert("INSERT INTO wm_material_stock(stock_id,tenant_id,material_id,material_code,material_name,material_spec,base_uom_id,warehouse_id,area_id,location_id,batch_id,batch_code,stock_quantity,reserved_quantity,frozen_flag,version,recpt_date,expire_date,create_by,create_time,del_flag,unit_cost,total_cost) SELECT #{stockId},#{tenant},m.material_id,m.material_code,m.material_name,m.material_spec,#{unit},#{d.warehouseId},#{d.areaId},#{d.locationId},#{d.batchId},#{batch.batchCode},#{qty},0,0,0,#{at},FROM_UNIXTIME(#{batch.expiryAt}/1000),#{actor},#{at},'0',#{cost},#{amount} FROM bd_material m WHERE m.tenant_id=#{tenant} AND m.material_id=#{d.materialId} AND m.status=1 AND m.del_flag='0'")
    int insertStock(@Param("tenant") String tenant,@Param("stockId") Long stockId,@Param("unit") String unit,@Param("d") Detail detail,@Param("batch") Batch batch,@Param("qty") BigDecimal qty,@Param("at") String at,@Param("actor") Long actor,@Param("cost") BigDecimal cost,@Param("amount") BigDecimal amount);

    @Update("UPDATE wm_material_stock SET stock_quantity=#{after},total_cost=#{amount},version=version+1,recpt_date=#{at},update_by=#{actor},update_time=#{at} WHERE tenant_id=#{tenant} AND stock_id=#{stock.stockId} AND material_id=#{stock.materialId} AND base_uom_id=#{stock.baseUomId} AND stock_quantity=#{stock.quantity} AND reserved_quantity=#{stock.reserved} AND version=#{stock.version} AND frozen_flag=0 AND unit_cost=#{stock.unitCost} AND total_cost=#{stock.totalCost} AND del_flag='0'")
    int addStock(@Param("tenant") String tenant,@Param("stock") Stock stock,@Param("after") BigDecimal after,@Param("at") String at,@Param("actor") Long actor,@Param("amount") BigDecimal amount);

    @Insert("INSERT INTO wm_material_stock_tx(tx_id,tenant_id,material_id,material_stock_id,warehouse_id,area_id,location_id,batch_id,base_uom_id,tx_type,tx_flag,tx_quantity,before_quantity,after_quantity,source_doc_type,source_doc_id,source_doc_line_id,source_doc_code,tx_date,create_by,create_time,del_flag,unit_cost,tx_amount) VALUES(#{txId},#{tenant},#{d.materialId},#{stockId},#{d.warehouseId},#{d.areaId},#{d.locationId},#{d.batchId},#{unit},'RECEIVE_IN',1,#{d.quantity},#{before},#{after},'WM_MATERIAL_RECEIVE',#{head.receiveId},#{d.lineId},#{head.receiveCode},#{at},#{actor},#{at},'0',#{cost},#{amount})")
    int insertTransaction(@Param("tenant") String tenant,@Param("txId") Long txId,@Param("stockId") Long stockId,@Param("unit") String unit,@Param("head") Head head,@Param("d") Detail detail,@Param("before") BigDecimal before,@Param("after") BigDecimal after,@Param("at") String at,@Param("actor") Long actor,@Param("cost") BigDecimal cost,@Param("amount") BigDecimal amount);

    @Update("UPDATE wm_batch SET received_qty=#{qty},update_by=#{actor},update_time=#{at} WHERE tenant_id=#{tenant} AND batch_id=#{batch.batchId} AND source_doc_type='WM_MATERIAL_RECEIVE' AND source_doc_id=#{receiveId} AND quantity=#{batch.quantity} AND received_qty=0 AND issued_qty=0 AND status='ACTIVE' AND del_flag='0'")
    int receiveBatch(@Param("tenant") String tenant,@Param("receiveId") Long receiveId,@Param("batch") Batch batch,@Param("qty") BigDecimal qty,@Param("at") String at,@Param("actor") Long actor);

    /** 全部原生来源与真实V2检验通过后，只CAS上架状态，不改量或出生范围。 */
    @Update("UPDATE wm_material_receive SET status='SHELVED',shelf_by=#{actor},shelf_time=#{at},update_by=#{actor},update_time=#{at} WHERE tenant_id=#{tenant} AND receive_id=#{id} AND status='APPROVED' AND del_flag='0'")
    int markShelved(@Param("tenant") String tenant,@Param("id") Long id,@Param("at") String at,@Param("actor") Long actor);

    @Update("UPDATE wm_material_receive SET status='FINISHED',update_by=#{actor},update_time=#{at} WHERE tenant_id=#{tenant} AND receive_id=#{id} AND status='SHELVED' AND del_flag='0'")
    int finish(@Param("tenant") String tenant,@Param("id") Long id,@Param("at") String at,@Param("actor") Long actor);

    @Insert("INSERT INTO wm_certified_receipt_posting(tenant_id,receive_id,material_id,actor_id,source_fingerprint,total_quantity,posted_at,notification_status) VALUES(#{tenant},#{id},#{materialId},#{actor},#{fingerprint},#{qty},#{at},'PENDING')")
    int recordPosting(@Param("tenant") String tenant,@Param("id") Long id,@Param("materialId") Long materialId,@Param("actor") Long actor,@Param("fingerprint") String fingerprint,@Param("qty") BigDecimal qty,@Param("at") String at);

    @Update("UPDATE wm_certified_receipt_posting SET notification_status='ENQUEUED' WHERE tenant_id=#{tenant} AND receive_id=#{id} AND notification_status='PENDING'")
    int acknowledgeEnqueue(@Param("tenant") String tenant,@Param("id") Long id);

    record SourceIdentity(String tenantId,Long receiveId,Long lineId) { }
    record Head(String tenantId,Long receiveId,String receiveCode,Long orderId,Long vendorId,String status,Long warehouseId,Long areaId,Long locationId,BigDecimal receiveAt,BigDecimal shelfAt,String delFlag,Long noticeId,String noticeCode) {
        @AutomapConstructor public Head { }
        public Head(String tenantId,Long receiveId,String receiveCode,Long orderId,Long vendorId,String status,Long warehouseId,Long areaId,Long locationId,BigDecimal receiveAt,BigDecimal shelfAt,String delFlag){this(tenantId,receiveId,receiveCode,orderId,vendorId,status,warehouseId,areaId,locationId,receiveAt,shelfAt,delFlag,null,null);}
    }
    record Line(String tenantId,Long lineId,Long receiveId,Long materialId,String baseUomId,BigDecimal quantity,Long purchaseLineId,String delFlag,Long noticeLineId) {
        @AutomapConstructor public Line { }
        public Line(String tenantId,Long lineId,Long receiveId,Long materialId,String baseUomId,BigDecimal quantity,Long purchaseLineId,String delFlag){this(tenantId,lineId,receiveId,materialId,baseUomId,quantity,purchaseLineId,delFlag,null);}
        /** 追加字段不改变旧无通知记录文本，否则会破坏原过账指纹重放。 */
        @Override public String toString(){return "Line[tenantId="+tenantId+", lineId="+lineId+", receiveId="+receiveId+", materialId="+materialId+", baseUomId="+baseUomId+", quantity="+quantity+", purchaseLineId="+purchaseLineId+", delFlag="+delFlag+(noticeLineId==null?"":", noticeLineId="+noticeLineId)+"]";}
    }
    record Detail(String tenantId,Long detailId,Long receiveId,Long lineId,Long materialId,String baseUomId,BigDecimal quantity,Long warehouseId,Long areaId,Long locationId,Long batchId,Long snId,String delFlag) { }
    record Material(String tenantId,Long materialId,Long baseUomId,Long batchManaged,Long status,String delFlag,String unitTenant,Integer quantityScale,String conversionVersion,BigDecimal verifiedAt,Long isBase,Long primaryId,BigDecimal conversionRate,Long unitStatus,String unitDelFlag) { }
    record Order(String tenantId,Long orderId,Long vendorId,String status,String currency,BigDecimal exchangeRate,String delFlag) { }
    record OrderLine(String tenantId,Long lineId,Long orderId,Long materialId,String unit,BigDecimal quantity,BigDecimal received,BigDecimal returned,BigDecimal unitPrice,BigDecimal netUnitPrice,String delFlag) { }
    record Batch(String tenantId,Long batchId,String batchCode,Long materialId,Long vendorId,Long warehouseId,BigDecimal quantity,BigDecimal received,BigDecimal issued,String status,String sourceType,Long sourceId,BigDecimal expiryAt,String delFlag) { }
    record Storage(String warehouseTenant,Long warehouseId,Long warehouseStatus,String warehouseDelFlag,String areaTenant,Long areaId,Long areaWarehouseId,Long areaEnabled,Long areaFrozen,Long locationFlag,String areaDelFlag,String locationTenant,Long locationId,Long locationWarehouseId,Long locationAreaId,Long locationEnabled,Long locationFrozen,String locationDelFlag) { }
    record Stock(String tenantId,Long stockId,Long materialId,String baseUomId,Long warehouseId,Long areaId,Long locationId,Long batchId,Long snId,BigDecimal quantity,BigDecimal reserved,Integer version,String delFlag,BigDecimal expiryAt,BigDecimal unitCost,BigDecimal totalCost) { }
    record PostedTx(Long txId,Long stockId,Long lineId,String baseUomId,BigDecimal quantity,BigDecimal beforeQuantity,BigDecimal afterQuantity,BigDecimal unitCost,BigDecimal txAmount,Long warehouseId,Long areaId,Long locationId,Long batchId,String postedAt) { }
    record Posting(String tenantId,Long receiveId,Long materialId,Long actorId,String sourceFingerprint,BigDecimal totalQuantity,String postedAt,String notificationStatus) { }
    record Clock(String databaseAt,BigDecimal epochMillis,String timeZone) { }
    record TableDefinition(String tableName,String engine) { }
    record QuantityDefinition(String tableName,String columnName,String dataType,Integer numericPrecision,Integer numericScale) { }
    record KeyDefinition(String indexName,Integer sequenceNumber,String columnName,Integer prefixLength) { }
}
