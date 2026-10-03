package com.nexus.mes.qc.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 仅由独占 MES 写入口调用；出生绑定永久保留，没有补录、更新或逻辑删除操作。 */
public interface QcReceiptIqcBirthMapper {
    // 原生目录必须实际重查：清除会话缓存且禁用二级缓存，不能把缓存结果当作结构复核。
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT c.TABLE_NAME AS table_name, t.ENGINE AS engine, c.COLUMN_NAME AS column_name,
               c.DATA_TYPE AS data_type, c.NUMERIC_PRECISION AS numeric_precision,
               c.NUMERIC_SCALE AS numeric_scale, c.IS_NULLABLE AS is_nullable,
               c.DATETIME_PRECISION AS datetime_precision
        FROM information_schema.COLUMNS c
        JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME IN ('qc_iqc','qc_inspect_detail','qc_receipt_iqc_birth')
        ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
        """)
    List<NativeColumn> selectNativeColumns();

    // 原生目录必须实际重查：清除会话缓存且禁用二级缓存，不能把缓存结果当作结构复核。
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT TABLE_NAME AS table_name, INDEX_NAME AS index_name, NON_UNIQUE AS non_unique,
               SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name, SUB_PART AS sub_part,
               INDEX_TYPE AS index_type
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('qc_iqc','qc_inspect_detail','qc_receipt_iqc_birth')
        ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
        """)
    List<IndexPart> selectNativeIndexes();

    /** 同一原生连接的当前时间；不是客户端时间，也不修改既有 IQC 的创建时间。 */
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    Date selectWriteTime();

    @Select("""
        SELECT * FROM qc_iqc WHERE tenant_id = #{tenantId}
        AND receive_id = #{receiveId} AND material_id = #{materialId} ORDER BY iqc_id FOR UPDATE
        """)
    List<QcIqc> selectScopeInspections(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    @Select("""
        SELECT * FROM qc_receipt_iqc_birth WHERE tenant_id = #{tenantId}
        AND receive_id = #{receiveId} AND material_id = #{materialId} ORDER BY iqc_id FOR UPDATE
        """)
    List<Binding> selectScopeBindings(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    /** 只读来源回查禁止加行锁：与检验本体、明细处于同一只读 RR 快照。 */
    @Select("""
        SELECT * FROM qc_receipt_iqc_birth WHERE tenant_id = #{tenantId} AND iqc_id = #{inspectionId}
        """)
    Binding selectBinding(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Insert("""
        INSERT INTO qc_iqc (iqc_id, tenant_id, iqc_no, receive_id, material_id, material_code,
            material_name, vendor_id, vendor_name, quantity, inspect_result, status, create_time, del_flag)
        VALUES (#{iqcId}, #{tenantId}, #{iqcNo}, #{receiveId}, #{materialId}, #{materialCode},
            #{materialName}, #{vendorId}, #{vendorName}, #{quantity}, 'PENDING', 'PENDING', #{createTime}, '0')
        """)
    int insertInspection(QcIqc inspection);

    @Insert("""
        INSERT INTO qc_receipt_iqc_birth (iqc_id, tenant_id, birth_id, receive_id, material_id,
            base_uom_id, quantity_scale, quantity, receipt_scope_sha256, birth_sha256, captured_at,
            receive_code, material_code, material_name, vendor_id, vendor_name, bound_at, binding_sha256)
        VALUES (#{iqcId}, #{tenantId}, #{birthId}, #{receiveId}, #{materialId}, #{baseUomId},
            #{quantityScale}, #{quantity}, #{receiptScopeSha256}, #{birthSha256}, #{capturedAt},
            #{receiveCode}, #{materialCode}, #{materialName}, #{vendorId}, #{vendorName}, #{boundAt}, #{bindingSha256})
        """)
    int insertBinding(Binding binding);

    @Select("SELECT * FROM qc_iqc WHERE tenant_id = #{tenantId} AND iqc_id = #{inspectionId}")
    QcIqc selectInspection(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Select("SELECT * FROM qc_iqc WHERE tenant_id = #{tenantId} AND iqc_id = #{inspectionId}")
    QcIqcVo selectInspectionView(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Data @EqualsAndHashCode(callSuper = true)
    class NativeColumn extends Column { private Integer datetimePrecision; }

    @Data
    class Binding {
        private Long iqcId;
        private String tenantId;
        private Long birthId;
        private Long receiveId;
        private Long materialId;
        private Long baseUomId;
        private Integer quantityScale;
        private BigDecimal quantity;
        private String receiptScopeSha256;
        private String birthSha256;
        private Long capturedAt;
        private String receiveCode;
        private String materialCode;
        private String materialName;
        private Long vendorId;
        private String vendorName;
        private Date boundAt;
        private String bindingSha256;

        public Birth birth() {
            return new Birth(tenantId, birthId, receiveId, materialId, baseUomId, quantityScale, quantity,
                receiptScopeSha256, birthSha256, capturedAt == null ? 0 : capturedAt,
                receiveCode, materialCode, materialName, vendorId, vendorName);
        }

        public static Binding from(Long inspectionId, Birth birth, Date boundAt, String hash) {
            Binding binding = new Binding();
            binding.setIqcId(inspectionId); binding.setTenantId(birth.tenantId()); binding.setBirthId(birth.birthId());
            binding.setReceiveId(birth.receiveId()); binding.setMaterialId(birth.materialId());
            binding.setBaseUomId(birth.baseUomId()); binding.setQuantityScale(birth.quantityScale());
            binding.setQuantity(birth.quantity()); binding.setReceiptScopeSha256(birth.receiptScopeSha256());
            binding.setBirthSha256(birth.birthSha256()); binding.setCapturedAt(birth.capturedAt());
            binding.setReceiveCode(birth.receiveCode()); binding.setMaterialCode(birth.materialCode());
            binding.setMaterialName(birth.materialName()); binding.setVendorId(birth.vendorId());
            binding.setVendorName(birth.vendorName()); binding.setBoundAt(boundAt); binding.setBindingSha256(hash);
            return binding;
        }
    }
}
