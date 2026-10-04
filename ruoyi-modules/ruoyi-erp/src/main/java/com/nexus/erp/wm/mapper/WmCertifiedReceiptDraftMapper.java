package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 认证订单行创建草稿的固定SQL；每次显式租户隔离，原生数量不使用CAST。 */
@Mapper
@InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface WmCertifiedReceiptDraftMapper {
    @Select("SELECT l.material_id FROM pu_purchase_order_line l WHERE l.tenant_id=#{tenant} AND l.order_id=#{orderId} AND l.line_id=#{lineId}")
    Long findMaterial(@Param("tenant")String tenant,@Param("orderId")Long orderId,@Param("lineId")Long lineId);
    /** 空读固定表以持有原定义的元数据锁；DDL不得在结构校验与落账间替换为非事务表。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id FROM wm_certified_receive_command WHERE 1=0 UNION ALL SELECT tenant_id FROM wm_material_receive WHERE 1=0 UNION ALL SELECT tenant_id FROM wm_material_receive_line WHERE 1=0 UNION ALL SELECT tenant_id FROM pu_purchase_order WHERE 1=0 UNION ALL SELECT tenant_id FROM pu_purchase_order_line WHERE 1=0")
    List<String> pinDefinitions();
    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_certified_receive_command','wm_material_receive','wm_material_receive_line','pu_purchase_order','pu_purchase_order_line') ORDER BY TABLE_NAME")
    List<String> schemaEngines();
    @Select("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND INDEX_NAME='PRIMARY' AND NON_UNIQUE=0 AND SUB_PART IS NULL ORDER BY SEQ_IN_INDEX")
    List<String> commandUniqueColumns();
    @Select("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_receive_line' AND COLUMN_NAME='quantity_received'")
    List<String> nativeQuantityTypes();
    String COMMAND_COLUMNS="tenant_id,request_key,request_hash,actor_id,order_id,purchase_line_id,receive_id,line_id,receive_code,material_id,base_uom_id,quantity,DATE_FORMAT(arrival_date,'%Y-%m-%d') AS arrival_date,warehouse_id,area_id,location_id";
    /** 发现只读，不对未命中的请求键取得长期间隙锁；最终唯一插入仲裁新请求。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+COMMAND_COLUMNS+" FROM wm_certified_receive_command WHERE tenant_id=#{tenant} AND request_key=#{key}")
    Command findCommand(@Param("tenant")String tenant,@Param("key")String key);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+COMMAND_COLUMNS+" FROM wm_certified_receive_command WHERE tenant_id=#{tenant} AND request_key=#{key} FOR UPDATE")
    Command lockCommand(@Param("tenant")String tenant,@Param("key")String key);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT o.tenant_id,o.order_id,o.order_no,o.certified_version AS version,o.vendor_id,v.vendor_code,v.vendor_name,l.line_id,m.material_id,m.material_code,m.material_name,m.material_spec,m.base_uom_id,u.uom_name,u.quantity_scale,l.order_quantity,l.received_quantity,o.status,o.del_flag AS head_del_flag,l.del_flag AS line_del_flag,m.status AS material_status,m.del_flag AS material_del_flag FROM pu_purchase_order o JOIN pu_purchase_order_line l ON l.tenant_id=o.tenant_id AND l.order_id=o.order_id JOIN bd_material m ON m.tenant_id=l.tenant_id AND m.material_id=l.material_id JOIN bd_unit_of_measure u ON u.tenant_id=m.tenant_id AND u.uom_id=m.base_uom_id JOIN bd_vendor v ON v.tenant_id=o.tenant_id AND v.vendor_id=o.vendor_id WHERE o.tenant_id=#{tenant} AND o.order_id=#{orderId} AND l.line_id=#{lineId} AND l.unit=CAST(m.base_uom_id AS CHAR) FOR UPDATE")
    OrderSource lockSource(@Param("tenant")String tenant,@Param("orderId")Long orderId,@Param("lineId")Long lineId);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT uom_id AS unit_id,primary_id,is_base,conversion_rate,quantity_scale,conversion_version,UNIX_TIMESTAMP(conversion_verified_at)*1000 AS verified_at,status,del_flag FROM bd_unit_of_measure WHERE tenant_id=#{tenant} AND uom_id=#{id} FOR UPDATE")
    RootUnit lockRootUnit(@Param("tenant")String tenant,@Param("id")Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT w.tenant_id,w.warehouse_id,w.warehouse_code,w.warehouse_name,a.area_id,a.area_code,a.area_name,l.location_id,l.location_code,l.location_name,w.status AS warehouse_status,w.del_flag AS warehouse_del_flag,a.enable_flag AS area_enabled,a.frozen_flag AS area_frozen,l.enable_flag AS location_enabled,l.frozen_flag AS location_frozen FROM wm_warehouse w JOIN wm_storage_area a ON a.tenant_id=w.tenant_id AND a.warehouse_id=w.warehouse_id JOIN wm_storage_location l ON l.tenant_id=a.tenant_id AND l.area_id=a.area_id AND l.warehouse_id=w.warehouse_id WHERE w.tenant_id=#{tenant} AND w.warehouse_id=#{warehouseId} AND a.area_id=#{areaId} AND l.location_id=#{locationId} AND a.del_flag='0' AND l.del_flag='0' FOR UPDATE")
    Storage lockStorage(@Param("tenant")String tenant,@Param("warehouseId")Long warehouseId,@Param("areaId")Long areaId,@Param("locationId")Long locationId);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT l.quantity_received AS quantity,h.status,h.del_flag AS head_del_flag,l.del_flag AS line_del_flag FROM wm_material_receive_line l LEFT JOIN wm_material_receive h ON h.tenant_id=l.tenant_id AND h.receive_id=l.receive_id WHERE l.tenant_id=#{tenant} AND l.purchase_line_id=#{lineId} ORDER BY l.receive_id,l.line_id FOR UPDATE")
    List<Pending> lockPending(@Param("tenant")String tenant,@Param("lineId")Long lineId);
    @Select("SELECT DATE_FORMAT(CURRENT_DATE(),'%Y-%m-%d')") String databaseDate();
    @Insert("INSERT INTO wm_material_receive(receive_id,tenant_id,receive_code,receive_name,pur_id,pur_code,vendor_id,vendor_code,vendor_name,warehouse_id,warehouse_code,warehouse_name,area_id,area_code,area_name,location_id,location_code,location_name,receive_date,status,remark,attr1,create_by,create_time,del_flag) VALUES(#{c.receiveId},#{c.tenantId},#{c.receiveCode},'认证采购收货',#{c.orderId},#{s.orderNo},#{s.vendorId},#{s.vendorCode},#{s.vendorName},#{p.warehouseId},#{p.warehouseCode},#{p.warehouseName},#{p.areaId},#{p.areaCode},#{p.areaName},#{p.locationId},#{p.locationCode},#{p.locationName},#{c.arrivalDate},'DRAFT',#{remark},'CERTIFIED_DRAFT_V1',#{c.actorId},CURRENT_TIMESTAMP(3),'0')")
    int insertHead(@Param("c")Command command,@Param("s")OrderSource source,@Param("p")Storage storage,@Param("remark")String remark);
    @Insert("INSERT INTO wm_material_receive_line(line_id,tenant_id,receive_id,source_doc_line_id,purchase_line_id,material_id,material_code,material_name,material_spec,base_uom_id,base_uom_name,quantity_received,warehouse_id,warehouse_code,warehouse_name,area_id,area_code,area_name,location_id,location_code,location_name,create_by,create_time,del_flag) VALUES(#{c.lineId},#{c.tenantId},#{c.receiveId},#{c.purchaseLineId},#{c.purchaseLineId},#{c.materialId},#{s.materialCode},#{s.materialName},#{s.materialSpec},#{c.baseUomId},#{s.uomName},#{c.quantity},#{p.warehouseId},#{p.warehouseCode},#{p.warehouseName},#{p.areaId},#{p.areaCode},#{p.areaName},#{p.locationId},#{p.locationCode},#{p.locationName},#{c.actorId},CURRENT_TIMESTAMP(3),'0')")
    int insertLine(@Param("c")Command command,@Param("s")OrderSource source,@Param("p")Storage storage);
    @Insert("INSERT INTO wm_certified_receive_command(tenant_id,request_key,request_hash,actor_id,order_id,purchase_line_id,receive_id,line_id,receive_code,material_id,base_uom_id,quantity,arrival_date,warehouse_id,area_id,location_id,create_time) VALUES(#{tenantId},#{requestKey},#{requestHash},#{actorId},#{orderId},#{purchaseLineId},#{receiveId},#{lineId},#{receiveCode},#{materialId},#{baseUomId},#{quantity},#{arrivalDate},#{warehouseId},#{areaId},#{locationId},CURRENT_TIMESTAMP(3))")
    int insertCommand(Command command);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT c."+"tenant_id,c.request_key,c.request_hash,c.actor_id,c.order_id,c.purchase_line_id,c.receive_id,c.line_id,c.receive_code,c.material_id,c.base_uom_id,c.quantity,DATE_FORMAT(c.arrival_date,'%Y-%m-%d') AS arrival_date,c.warehouse_id,c.area_id,c.location_id FROM wm_certified_receive_command c JOIN wm_material_receive h ON h.tenant_id=c.tenant_id AND h.receive_id=c.receive_id JOIN wm_material_receive_line l ON l.tenant_id=c.tenant_id AND l.line_id=c.line_id AND l.receive_id=c.receive_id WHERE c.tenant_id=#{tenantId} AND c.request_key=#{requestKey} AND h.pur_id=c.order_id AND h.receive_code=c.receive_code AND l.purchase_line_id=c.purchase_line_id AND l.material_id=c.material_id AND l.base_uom_id=c.base_uom_id AND l.quantity_received=c.quantity AND h.del_flag='0' AND l.del_flag='0' FOR UPDATE")
    Command readReceipt(Command command);
    record Command(String tenantId,String requestKey,String requestHash,Long actorId,Long orderId,Long purchaseLineId,Long receiveId,Long lineId,String receiveCode,Long materialId,String baseUomId,BigDecimal quantity,String arrivalDate,Long warehouseId,Long areaId,Long locationId) { }
    record OrderSource(String tenantId,Long orderId,String orderNo,Integer version,Long vendorId,String vendorCode,String vendorName,Long lineId,Long materialId,String materialCode,String materialName,String materialSpec,Long baseUomId,String uomName,Integer quantityScale,BigDecimal orderQuantity,BigDecimal receivedQuantity,String status,String headDelFlag,String lineDelFlag,String materialStatus,String materialDelFlag) { }
    record Storage(String tenantId,Long warehouseId,String warehouseCode,String warehouseName,Long areaId,String areaCode,String areaName,Long locationId,String locationCode,String locationName,String warehouseStatus,String warehouseDelFlag,String areaEnabled,String areaFrozen,String locationEnabled,String locationFrozen) { }
    record RootUnit(Long unitId,Long primaryId,Long isBase,BigDecimal conversionRate,Integer quantityScale,String conversionVersion,Long verifiedAt,String status,String delFlag) { }
    record Pending(BigDecimal quantity,String status,String headDelFlag,String lineDelFlag) { }
}
