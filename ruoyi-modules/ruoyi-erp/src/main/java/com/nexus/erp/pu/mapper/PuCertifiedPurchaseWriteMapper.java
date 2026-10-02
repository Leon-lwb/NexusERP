package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 固定SQL、显式租户与当前读；不提供通用update/delete或外部证书落账方法。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface PuCertifiedPurchaseWriteMapper {
    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT request_key AS requestKey, request_hash AS requestHash, actor_id AS actorId, entry_type AS entryType, "
        + "material_id AS materialId, vendor_id AS vendorId, requisition_id AS requisitionId, requisition_no AS requisitionNo, "
        + "source_fingerprint AS sourceFingerprint, result_fingerprint AS resultFingerprint, command_status AS commandStatus, catalog_fingerprint AS catalogFingerprint "
        + "FROM pu_certified_purchase_command WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} FOR UPDATE")
    CommandReceipt lockCommand(@Param("tenantId") String tenantId, @Param("requestKey") String requestKey);

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT requisition_id AS requisitionId, requisition_no AS requisitionNo, status, version, "
        + "decision_idempotency_key AS requestKey, del_flag AS delFlag, source_type AS entryType, applicant_id AS applicantId, "
        + "decision_run_id AS runId, decision_action_id AS actionId FROM pu_purchase_requisition "
        + "WHERE tenant_id=#{tenantId} AND requisition_id=#{id} FOR UPDATE")
    DocumentReceipt lockDocument(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT line_id AS lineId, material_id AS materialId, source_id AS detailId, unit AS baseUnit, "
        + "apply_quantity AS applyQuantity, DATE_FORMAT(required_date,'%Y-%m-%d %H:%i:%s') AS requiredAt, "
        + "source_type AS sourceType, del_flag AS delFlag FROM pu_purchase_requisition_line "
        + "WHERE tenant_id=#{tenantId} AND requisition_id=#{id} ORDER BY line_id FOR UPDATE")
    List<LineReceipt> lockLines(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT * FROM pu_requirement_allocation_event WHERE tenant_id=#{tenantId} AND allocation_id=#{allocationId} "
        + "AND event_type='CREATE' ORDER BY event_id FOR UPDATE")
    List<com.nexus.erp.pu.domain.PuRequirementAllocationEvent> lockCreationEvents(@Param("tenantId") String tenantId,
        @Param("allocationId") Long allocationId);

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
        + "AND TABLE_NAME='pu_certified_purchase_command'")
    String commandTableEngine();

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT SQL_NO_CACHE request_key FROM pu_certified_purchase_command WHERE 1=0")
    List<String> pinCommandDefinition();

    @Options(flushCache=Options.FlushCachePolicy.TRUE, useCache=false)
    @Select("SELECT INDEX_NAME AS indexName, NON_UNIQUE AS nonUnique, SEQ_IN_INDEX AS sequenceNumber, "
        + "COLUMN_NAME AS columnName, SUB_PART AS prefixLength FROM information_schema.STATISTICS "
        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pu_certified_purchase_command' AND NON_UNIQUE=0 "
        + "ORDER BY INDEX_NAME,SEQ_IN_INDEX")
    List<CommandUniqueKeyColumn> commandUniqueKeyColumns();
    record CommandUniqueKeyColumn(String indexName,Integer nonUnique,Integer sequenceNumber,String columnName,Integer prefixLength) { }

    @Insert("INSERT INTO pu_purchase_requisition (requisition_id,tenant_id,requisition_no,requisition_name,"
        + "applicant_id,applicant_name,requisition_date,required_date,source_type,status,version,"
        + "decision_run_id,decision_action_id,decision_idempotency_key,create_by,create_time,del_flag) VALUES ("
        + "#{id},#{tenantId},#{number},#{name},#{actorId},#{actorName},NOW(),#{requiredAt},#{entryType},'DRAFT',0,"
        + "#{runId},#{actionId},#{requestKey},#{actorId},NOW(),'0')")
    int insertDocument(@Param("id") Long id,@Param("tenantId") String tenantId,@Param("number") String number,
        @Param("name") String name,@Param("actorId") Long actorId,@Param("actorName") String actorName,
        @Param("requiredAt") String requiredAt,@Param("entryType") String entryType,
        @Param("runId") Long runId,@Param("actionId") Long actionId,@Param("requestKey") String requestKey);

    @Insert("INSERT INTO pu_purchase_requisition_line (line_id,tenant_id,requisition_id,material_id,material_code,material_name,material_spec,unit,"
        + "apply_quantity,approved_quantity,shortage_quantity,required_date,source_type,source_id,"
        + "create_by,create_time,del_flag) SELECT #{lineId},#{tenantId},#{requisitionId},#{materialId},material_code,material_name,material_spec,#{baseUnit},"
        + "#{quantity},0,#{quantity},#{requiredAt},'MRP',#{detailId},#{actorId},NOW(),'0' FROM bd_material "
        + "WHERE tenant_id=#{tenantId} AND material_id=#{materialId} AND status=1 AND del_flag='0'")
    int insertLine(@Param("lineId") Long lineId,@Param("tenantId") String tenantId,@Param("requisitionId") Long requisitionId,
        @Param("materialId") Long materialId,@Param("baseUnit") String baseUnit,@Param("quantity") BigDecimal quantity,
        @Param("requiredAt") String requiredAt,@Param("detailId") Long detailId,@Param("actorId") Long actorId);

    @Insert("INSERT INTO pu_certified_purchase_command (tenant_id,request_key,request_hash,actor_id,entry_type,"
        + "material_id,vendor_id,window_end,requisition_id,requisition_no,source_fingerprint,result_fingerprint,catalog_fingerprint,"
        + "command_status,create_time) VALUES (#{tenantId},#{row.requestKey},#{row.requestHash},#{row.actorId},"
        + "#{row.entryType},#{row.materialId},#{row.vendorId},#{windowEnd},#{row.requisitionId},#{row.requisitionNo},"
        + "#{row.sourceFingerprint},#{row.resultFingerprint},#{row.catalogFingerprint},'COMMITTED',NOW())")
    int insertCommand(@Param("tenantId") String tenantId,@Param("row") CommandReceipt row,@Param("windowEnd") String windowEnd);

    record CommandReceipt(String requestKey,String requestHash,Long actorId,String entryType,Long materialId,
        Long vendorId,Long requisitionId,String requisitionNo,String sourceFingerprint,String resultFingerprint,String commandStatus,String catalogFingerprint) { }
    record DocumentReceipt(Long requisitionId,String requisitionNo,String status,Integer version,String requestKey,String delFlag,
        String entryType,Long applicantId,Long runId,Long actionId) { }
    record LineReceipt(Long lineId,Long materialId,Long detailId,String baseUnit,BigDecimal applyQuantity,
        String requiredAt,String sourceType,String delFlag) { }
}
