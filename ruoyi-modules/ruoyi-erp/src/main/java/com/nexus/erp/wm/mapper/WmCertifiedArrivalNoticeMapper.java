package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptDraftMapper.OrderSource;

/** 认证通知原生当前读与永久创建凭据；显式限定租户，不借用标签授权。 */
public interface WmCertifiedArrivalNoticeMapper {
    String COLUMNS="tenant_id,request_key,request_hash,actor_id,order_id,purchase_line_id,order_version,notice_id,notice_line_id,notice_code,vendor_id,material_id,base_uom_id,quantity,DATE_FORMAT(arrival_date,'%Y-%m-%d') arrival_date,notice_name,remark";
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+COLUMNS+" FROM wm_certified_arrival_command WHERE tenant_id=#{tenant} AND request_key=#{key}")
    Creation findCommand(@Param("tenant") String tenant,@Param("key") String key);
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_arrival_notice','wm_arrival_notice_line','wm_certified_arrival_command') ORDER BY TABLE_NAME")
    List<String> tableEngines();
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
    @Select("SELECT COLUMN_NAME columnName,DATA_TYPE dataType,NUMERIC_PRECISION numericPrecision,NUMERIC_SCALE numericScale FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_arrival_notice_line' AND COLUMN_NAME IN ('quantity_arrival','received_qty') ORDER BY COLUMN_NAME")
    List<DecimalColumn> decimalColumns();
    /** 命令与实际头/行逐字段一致才返回；历史 notice 标签不形成证明。 */
    String NOTICE="SELECT h.tenant_id tenantId,h.notice_id noticeId,l.line_id noticeLineId,h.notice_code noticeCode,c.order_id orderId,o.order_no orderNo,c.purchase_line_id purchaseLineId,h.vendor_id vendorId,l.material_id materialId,l.base_uom_id baseUomId,l.quantity_arrival quantity,l.received_qty receivedQty,h.status,h.del_flag headDeleted,l.del_flag lineDeleted FROM wm_certified_arrival_command c JOIN wm_arrival_notice h ON h.tenant_id=c.tenant_id AND h.notice_id=c.notice_id JOIN wm_arrival_notice_line l ON l.tenant_id=c.tenant_id AND l.notice_id=c.notice_id AND l.line_id=c.notice_line_id JOIN pu_purchase_order o ON o.tenant_id=c.tenant_id AND o.order_id=c.order_id WHERE c.tenant_id=#{tenant} AND h.notice_id=#{id} AND h.notice_code=c.notice_code AND h.pur_order_code=o.order_no AND h.vendor_id=c.vendor_id AND l.material_id=c.material_id AND l.base_uom_id=c.base_uom_id AND l.quantity_arrival=c.quantity AND h.del_flag='0' AND l.del_flag='0' ";
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select(NOTICE)
    Notice discoverNotice(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select(NOTICE+"FOR UPDATE")
    Notice lockNotice(@Param("tenant") String tenant,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select(NOTICE+"ORDER BY l.line_id FOR UPDATE")
    List<Notice> lockNoticeLines(@Param("tenant") String tenant,@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM wm_arrival_notice_line WHERE tenant_id=#{tenant} AND notice_id=#{id} AND del_flag='0'")
    Long lineCount(@Param("tenant") String tenant,@Param("id") Long id);
    /** 包含未完成原收货，不能把消息回写延迟视为额外余额。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT c.quantity FROM wm_certified_receive_command c JOIN wm_material_receive h ON h.tenant_id=c.tenant_id AND h.receive_id=c.receive_id WHERE c.tenant_id=#{tenant} AND c.notice_id=#{id} AND c.notice_line_id=#{line} AND h.status IN ('DRAFT','APPROVING','REJECTED','APPROVED','SHELVED') AND h.del_flag='0' ORDER BY c.receive_id")
    List<BigDecimal> pendingQuantities(@Param("tenant") String tenant,@Param("id") Long id,@Param("line") Long line);
    @Insert("INSERT INTO wm_arrival_notice(notice_id,tenant_id,notice_code,notice_name,pur_order_code,vendor_id,vendor_code,vendor_name,arrival_date,status,remark,create_by,create_time,del_flag) VALUES(#{c.noticeId},#{c.tenantId},#{c.noticeCode},#{c.noticeName},#{s.orderNo},#{c.vendorId},#{s.vendorCode},#{s.vendorName},#{c.arrivalDate},'DRAFT',#{c.remark},#{c.actorId},CURRENT_TIMESTAMP(3),'0')")
    int insertHead(@Param("c") Creation creation,@Param("s") OrderSource source);
    @Insert("INSERT INTO wm_arrival_notice_line(line_id,tenant_id,notice_id,material_id,material_code,material_name,material_spec,base_uom_id,base_uom_name,quantity_arrival,received_qty,iqc_check,create_by,create_time,del_flag) VALUES(#{c.noticeLineId},#{c.tenantId},#{c.noticeId},#{c.materialId},#{s.materialCode},#{s.materialName},#{s.materialSpec},#{c.baseUomId},#{s.uomName},#{c.quantity},0,'1',#{c.actorId},CURRENT_TIMESTAMP(3),'0')")
    int insertLine(@Param("c") Creation creation,@Param("s") OrderSource source);
    @Insert("INSERT INTO wm_certified_arrival_command(tenant_id,request_key,request_hash,actor_id,order_id,purchase_line_id,order_version,notice_id,notice_line_id,notice_code,vendor_id,material_id,base_uom_id,quantity,arrival_date,notice_name,remark,create_time) VALUES(#{tenantId},#{requestKey},#{requestHash},#{actorId},#{orderId},#{purchaseLineId},#{orderVersion},#{noticeId},#{noticeLineId},#{noticeCode},#{vendorId},#{materialId},#{baseUomId},#{quantity},#{arrivalDate},#{noticeName},#{remark},CURRENT_TIMESTAMP(3))")
    int insertCommand(Creation creation);
    @Update("UPDATE wm_arrival_notice SET status=#{status},update_time=CURRENT_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND notice_id=#{id} AND del_flag='0'")
    int updateStatus(@Param("tenant") String tenant,@Param("id") Long id,@Param("status") String status);
    @Update("UPDATE wm_arrival_notice_line SET received_qty=received_qty+#{qty},update_time=CURRENT_TIMESTAMP(3) WHERE tenant_id=#{tenant} AND line_id=#{line} AND del_flag='0' AND received_qty>=0 AND received_qty+#{qty}<=quantity_arrival")
    int addReceived(@Param("tenant") String tenant,@Param("line") Long line,@Param("qty") BigDecimal quantity);
    /** 真实头/行/永久请求三处引用必须一致。 */
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT c.request_hash FROM wm_certified_receive_command c JOIN wm_material_receive h ON h.tenant_id=c.tenant_id AND h.receive_id=c.receive_id JOIN wm_material_receive_line l ON l.tenant_id=c.tenant_id AND l.receive_id=c.receive_id AND l.line_id=c.line_id WHERE c.tenant_id=#{tenant} AND c.receive_id=#{receive} AND c.line_id=#{line} AND c.order_id=#{order} AND c.purchase_line_id=#{purchase} AND c.material_id=#{material} AND c.base_uom_id=#{unit} AND c.quantity=#{qty} AND c.notice_id=#{notice} AND c.notice_line_id=#{noticeLine} AND h.notice_id=c.notice_id AND h.pur_id=c.order_id AND h.receive_code=c.receive_code AND l.notice_line_id=c.notice_line_id AND l.purchase_line_id=c.purchase_line_id AND l.material_id=c.material_id AND l.base_uom_id=c.base_uom_id AND l.quantity_received=c.quantity AND h.del_flag='0' AND l.del_flag='0'")
    String receiptHash(@Param("tenant") String tenant,@Param("receive") Long receive,@Param("line") Long line,@Param("order") Long order,@Param("purchase") Long purchase,@Param("material") Long material,@Param("unit") String unit,@Param("qty") BigDecimal qty,@Param("notice") Long notice,@Param("noticeLine") Long noticeLine);
    /** 先固定三张原生表的元数据锁，再读数量定义，避免校验后并发 DDL 漂移。 */
    @Select("SELECT (SELECT COUNT(*) FROM wm_arrival_notice WHERE notice_id IS NULL)+(SELECT COUNT(*) FROM wm_arrival_notice_line WHERE line_id IS NULL)+(SELECT COUNT(*) FROM wm_certified_arrival_command WHERE request_key IS NULL)")
    int pinDefinitions();
    @Select("SELECT * FROM wm_arrival_notice WHERE tenant_id=#{tenant} AND notice_id=#{id} AND del_flag='0'")
    com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo viewHeader(@Param("tenant") String tenant,@Param("id") Long id);
    @Select("SELECT * FROM wm_arrival_notice_line WHERE tenant_id=#{tenant} AND notice_id=#{id} AND del_flag='0' ORDER BY line_id")
    List<com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo> viewLines(@Param("tenant") String tenant,@Param("id") Long id);
    record Creation(String tenantId,String requestKey,String requestHash,Long actorId,Long orderId,Long purchaseLineId,Integer orderVersion,Long noticeId,Long noticeLineId,String noticeCode,Long vendorId,Long materialId,String baseUomId,BigDecimal quantity,String arrivalDate,String noticeName,String remark) { }
    record Notice(String tenantId,Long noticeId,Long noticeLineId,String noticeCode,Long orderId,String orderNo,Long purchaseLineId,Long vendorId,Long materialId,String baseUomId,BigDecimal quantity,BigDecimal receivedQty,String status,String headDeleted,String lineDeleted) { }
    record DecimalColumn(String columnName,String dataType,Integer numericPrecision,Integer numericScale) { }
}
