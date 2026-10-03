package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原生永久出生登记；调用者必须持有ERP独占可重复读事务，不能由客户端DTO直接认证。 */
public interface WmReceiptIqcBirthMapper {
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine="true", dataPermission="true")
    @Select("""
        SELECT c.TABLE_NAME AS table_name,t.ENGINE AS engine,c.COLUMN_NAME AS column_name,
               c.DATA_TYPE AS data_type,c.NUMERIC_PRECISION AS numeric_precision,
               c.NUMERIC_SCALE AS numeric_scale,c.IS_NULLABLE AS is_nullable
        FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
          ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
        WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME='wm_receipt_iqc_birth'
        ORDER BY c.ORDINAL_POSITION
        """)
    List<Column> columns();
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine="true", dataPermission="true")
    @Select("""
        SELECT TABLE_NAME AS table_name,INDEX_NAME AS index_name,NON_UNIQUE AS non_unique,
               SEQ_IN_INDEX AS seq_in_index,COLUMN_NAME AS column_name,SUB_PART AS sub_part,INDEX_TYPE AS index_type
        FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_receipt_iqc_birth'
        ORDER BY INDEX_NAME,SEQ_IN_INDEX
        """)
    List<IndexPart> indexes();
    @Select("""
        SELECT * FROM wm_receipt_iqc_birth
        WHERE tenant_id=#{tenant} AND receive_id=#{receive} AND material_id=#{material} FOR UPDATE
        """)
    BirthRow lockScope(@Param("tenant") String tenant, @Param("receive") Long receive, @Param("material") Long material);
    @Select("""
        SELECT * FROM wm_receipt_iqc_birth WHERE tenant_id=#{tenant} AND birth_id=#{id} FOR UPDATE
        """)
    BirthRow lockId(@Param("tenant") String tenant, @Param("id") Long id);
    @Select("""
        SELECT r.tenant_id,r.receive_id,r.receive_code,r.pur_id,r.vendor_id,r.vendor_name,
               r.status,r.del_flag,m.material_id,m.material_code,m.material_name
        FROM wm_material_receive r JOIN bd_material m ON m.tenant_id=r.tenant_id AND m.material_id=#{material}
        WHERE r.tenant_id=#{tenant} AND r.receive_id=#{receive} FOR UPDATE
        """)
    Description lockDescription(@Param("tenant") String tenant,@Param("receive") Long receive,@Param("material") Long material);
    @Insert("""
        INSERT INTO wm_receipt_iqc_birth
        (tenant_id,birth_id,receive_id,material_id,base_uom_id,quantity_scale,quantity,
         receipt_scope_sha256,birth_sha256,captured_at,receive_code,material_code,material_name,vendor_id,vendor_name)
        VALUES (#{b.tenantId},#{b.birthId},#{b.receiveId},#{b.materialId},#{b.baseUomId},#{b.quantityScale},#{b.quantity},
                #{b.receiptScopeSha256},#{b.birthSha256},#{b.capturedAt},#{b.receiveCode},#{b.materialCode},#{b.materialName},#{b.vendorId},#{b.vendorName})
        """)
    int insert(@Param("b") Birth birth);
    @Data class Description {
        private String tenantId; private Long receiveId; private String receiveCode; private Long purId;
        private Long vendorId; private String vendorName; private Long materialId; private String materialCode;
        private String materialName; private String status; private String delFlag;
    }
    @Data class BirthRow {
        private String tenantId; private Long birthId; private Long receiveId; private Long materialId;
        private Long baseUomId; private Integer quantityScale; private java.math.BigDecimal quantity;
        private String receiptScopeSha256; private String birthSha256; private long capturedAt;
        private String receiveCode; private String materialCode; private String materialName;
        private Long vendorId; private String vendorName;
        public Birth value() { return new Birth(tenantId,birthId,receiveId,materialId,baseUomId,quantityScale,quantity,
            receiptScopeSha256,birthSha256,capturedAt,receiveCode,materialCode,materialName,vendorId,vendorName); }
    }
}
