package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 仅访问 ERP，按显式租户范围锁定读取；IQC 证据仅允许插入。 */
public interface WmReceiptIqcEvidenceMapper {
    /** 已核实归属的 ERP 或 MES 连接上的原生引擎和列定义。 */
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("""
        SELECT c.TABLE_NAME AS table_name, t.ENGINE AS engine, c.COLUMN_NAME AS column_name,
               c.DATA_TYPE AS data_type, c.NUMERIC_PRECISION AS numeric_precision,
               c.NUMERIC_SCALE AS numeric_scale, c.IS_NULLABLE AS is_nullable
        FROM information_schema.COLUMNS c
        JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME IN ('wm_material_receive','wm_material_receive_line','bd_material','bd_unit_of_measure','wm_certified_iqc_evidence')
        ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
        """)
    List<Column> selectNativeColumns();

    /** 读取完整的永久索引组成部分，不根据当前记录数推断唯一性。 */
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("""
        SELECT TABLE_NAME AS table_name, INDEX_NAME AS index_name, NON_UNIQUE AS non_unique,
               SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name,
               SUB_PART AS sub_part, INDEX_TYPE AS index_type
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('wm_material_receive','wm_material_receive_line','bd_material','bd_unit_of_measure','wm_certified_iqc_evidence')
        ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
        """)
    List<IndexPart> selectNativeIndexes();

    /** 已核实归属的 ERP 或 MES 连接上的原生引擎和列定义。 */
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("""
        SELECT c.TABLE_NAME AS table_name, t.ENGINE AS engine, c.COLUMN_NAME AS column_name,
               c.DATA_TYPE AS data_type, c.NUMERIC_PRECISION AS numeric_precision,
               c.NUMERIC_SCALE AS numeric_scale, c.IS_NULLABLE AS is_nullable
        FROM information_schema.COLUMNS c
        JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME IN ('wm_material_receive','wm_material_receive_line','wm_material_receive_detail','wm_material_stock_tx','wm_batch','wm_material_stock','wm_arrival_notice_line','wm_warehouse','wm_storage_area','wm_storage_location')
        ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
        """)
    List<Column> selectIsolationColumns();

    /** 读取完整的永久索引组成部分，不根据当前记录数推断唯一性。 */
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("""
        SELECT TABLE_NAME AS table_name, INDEX_NAME AS index_name, NON_UNIQUE AS non_unique,
               SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name,
               SUB_PART AS sub_part, INDEX_TYPE AS index_type
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('wm_material_receive','wm_material_receive_line','wm_material_receive_detail','wm_material_stock_tx','wm_batch','wm_material_stock','wm_arrival_notice_line','wm_warehouse','wm_storage_area','wm_storage_location')
        ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
        """)
    List<IndexPart> selectIsolationIndexes();

    @Select("""
        SELECT tenant_id, receive_id, line_id, material_id, del_flag
        FROM wm_material_receive_line
        WHERE tenant_id = #{tenantId} AND receive_id = #{receiveId} AND material_id = #{materialId}
        ORDER BY line_id FOR UPDATE
        """)
    List<ReceiptLine> selectMaterialIdentitiesForUpdate(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    @Select("""
        SELECT tenant_id, receive_id, receive_code, notice_id, pur_id, vendor_id,
               status, del_flag, create_time
        FROM wm_material_receive
        WHERE tenant_id = #{tenantId} AND receive_id = #{receiveId} FOR UPDATE
        """)
    ReceiptHeader selectReceiveForUpdate(@Param("tenantId") String tenantId, @Param("receiveId") Long receiveId);

    // 范围指纹包含历史已删除记录；仅有效记录计入数量。
    @Select("""
        SELECT tenant_id, receive_id, line_id, material_id, base_uom_id, quantity_received,
               source_doc_line_id, purchase_line_id, del_flag
        FROM wm_material_receive_line
        WHERE tenant_id = #{tenantId} AND receive_id = #{receiveId} AND material_id = #{materialId}
        ORDER BY line_id FOR UPDATE
        """)
    List<ReceiptLine> selectMaterialLinesForUpdate(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    @Select("""
        SELECT tenant_id, material_id, base_uom_id, del_flag FROM bd_material
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId} FOR UPDATE
        """)
    MaterialUnit selectMaterialForUpdate(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("""
        SELECT tenant_id, uom_id, quantity_scale, status, del_flag, is_base, primary_id, conversion_rate, conversion_version, conversion_verified_at, category FROM bd_unit_of_measure
        WHERE tenant_id = #{tenantId} AND uom_id = #{unitId} FOR UPDATE
        """)
    NativeUnit selectUnitForUpdate(@Param("tenantId") String tenantId, @Param("unitId") Long unitId);

    @Select("""
        SELECT * FROM wm_certified_iqc_evidence
        WHERE tenant_id = #{tenantId} AND inspection_id = #{inspectionId} FOR UPDATE
        """)
    EvidenceRow selectInspectionForUpdate(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Select("""
        SELECT * FROM wm_certified_iqc_evidence
        WHERE tenant_id = #{tenantId} AND receive_id = #{receiveId} AND material_id = #{materialId} FOR UPDATE
        """)
    EvidenceRow selectReceiptMaterialForUpdate(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    @Select("""
        SELECT * FROM wm_certified_iqc_evidence
        WHERE tenant_id = #{tenantId} AND first_message_id = #{messageId} FOR UPDATE
        """)
    EvidenceRow selectMessageForUpdate(@Param("tenantId") String tenantId, @Param("messageId") String messageId);

    @Insert("""
        INSERT INTO wm_certified_iqc_evidence
        (evidence_id, tenant_id, inspection_id, inspection_code, receive_id, material_id,
         base_uom_id, quantity_scale, inspected_quantity, inspect_result, inspector_id,
         source_kind, source_version, actual_body_sha256, receipt_scope_sha256,
         evidence_sha256, first_message_id, source_created_at, completed_at, source_updated_at,
         observed_at, certified_at, birth_id, birth_sha256)
        VALUES (#{e.evidenceId}, #{e.tenantId}, #{e.inspectionId}, #{e.inspectionCode}, #{e.receiveId},
         #{e.materialId}, #{e.baseUomId}, #{e.quantityScale}, #{e.inspectedQuantity}, #{e.inspectResult},
         #{e.inspectorId}, #{e.sourceKind}, #{e.sourceVersion}, #{e.actualBodySha256},
         #{e.receiptScopeSha256}, #{e.evidenceSha256}, #{e.firstMessageId}, #{e.sourceCreatedAt},
         #{e.completedAt}, #{e.sourceUpdatedAt}, #{e.observedAt}, #{e.certifiedAt}, #{e.birthId}, #{e.birthSha256})
        """)
    int insertEvidence(@Param("e") EvidenceRow evidence);

    @Select("""
        SELECT tenant_id, detail_id, receive_id, line_id, material_id, batch_id, warehouse_id,
               area_id, location_id, sn_id, del_flag FROM wm_material_receive_detail
        WHERE tenant_id=#{tenantId} AND receive_id=#{receiveId} ORDER BY detail_id FOR UPDATE
        """)
    List<IsolationDetail> selectIsolationDetails(@Param("tenantId") String tenantId, @Param("receiveId") Long receiveId);

    // 不要预先过滤无效、已删除或非 RECEIVE_IN 的记录：必须显式核验原始父级来源。
    @Select("""
        SELECT tenant_id, tx_id, material_stock_id AS stock_id, material_id, source_doc_type,
               source_doc_id AS receive_id, source_doc_line_id AS line_id, tx_type, tx_flag,
               batch_id, warehouse_id, area_id, location_id, sn_id, del_flag
        FROM wm_material_stock_tx WHERE tenant_id=#{tenantId}
          AND source_doc_type='WM_MATERIAL_RECEIVE' AND source_doc_id=#{receiveId}
        ORDER BY tx_id FOR UPDATE
        """)
    List<IsolationTx> selectIsolationReceiptTransactions(@Param("tenantId") String tenantId, @Param("receiveId") Long receiveId);

    @Select("""
        SELECT tenant_id, stock_id, material_id, batch_id, warehouse_id, area_id, location_id,
               sn_id, frozen_flag, version, quantity_source_head_tx_id AS source_head_tx_id, del_flag
        FROM wm_material_stock WHERE tenant_id=#{tenantId} AND stock_id=#{stockId} FOR UPDATE
        """)
    IsolationStock selectIsolationStock(@Param("tenantId") String tenantId, @Param("stockId") Long stockId);

    @Select("""
        SELECT tenant_id, tx_id, material_stock_id AS stock_id, material_id, source_doc_type,
               source_doc_id AS receive_id, source_doc_line_id AS line_id, tx_type, tx_flag,
               batch_id, warehouse_id, area_id, location_id, sn_id, del_flag
        FROM wm_material_stock_tx WHERE tenant_id=#{tenantId} AND tx_id=#{txId} FOR UPDATE
        """)
    IsolationTx selectIsolationSourceHead(@Param("tenantId") String tenantId, @Param("txId") Long txId);

    @Select("""
        SELECT tenant_id, batch_id, material_id, warehouse_id, source_doc_type,
               source_doc_id AS receive_id, qc_status, del_flag FROM wm_batch
        WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} FOR UPDATE
        """)
    IsolationBatch selectIsolationBatch(@Param("tenantId") String tenantId, @Param("batchId") Long batchId);

    @Select("""
        SELECT w.tenant_id AS warehouse_tenant, w.warehouse_id, w.del_flag AS warehouse_del,
               a.tenant_id AS area_tenant, a.area_id, a.warehouse_id AS area_warehouse_id, a.del_flag AS area_del,
               l.tenant_id AS location_tenant, l.location_id, l.warehouse_id AS location_warehouse_id,
               l.area_id AS location_area_id, l.del_flag AS location_del
        FROM wm_warehouse w LEFT JOIN wm_storage_area a ON a.area_id=#{areaId}
        LEFT JOIN wm_storage_location l ON l.location_id=#{locationId}
        WHERE w.warehouse_id=#{warehouseId} FOR UPDATE
        """)
    IsolationStorage selectIsolationStorage(@Param("warehouseId") Long warehouseId,
        @Param("areaId") Long areaId, @Param("locationId") Long locationId);

    @Update("""
        UPDATE wm_material_stock SET frozen_flag=1, version=version+1
        WHERE tenant_id=#{tenantId} AND stock_id=#{s.stockId} AND material_id=#{s.materialId}
          AND batch_id <=> #{s.batchId} AND warehouse_id=#{s.warehouseId}
          AND area_id <=> #{s.areaId} AND location_id <=> #{s.locationId} AND sn_id <=> #{s.snId}
          AND quantity_source_head_tx_id <=> #{s.sourceHeadTxId}
          AND frozen_flag=0 AND version=#{s.version} AND del_flag='0'
        """)
    int freezeIsolationStock(@Param("tenantId") String tenantId, @Param("s") IsolationStock stock);

    @Update("""
        UPDATE wm_batch SET qc_status='UNQUALIFIED'
        WHERE tenant_id=#{tenantId} AND batch_id=#{b.batchId} AND material_id=#{b.materialId}
          AND warehouse_id=#{b.warehouseId} AND source_doc_type='WM_MATERIAL_RECEIVE'
          AND source_doc_id=#{b.receiveId} AND del_flag='0'
          AND (qc_status IS NULL OR qc_status!='UNQUALIFIED')
        """)
    int markIsolationBatch(@Param("tenantId") String tenantId, @Param("b") IsolationBatch batch);

    @Data class IsolationDetail {
        private String tenantId; private Long detailId; private Long receiveId; private Long lineId;
        private Long materialId; private Long batchId; private Long warehouseId; private Long areaId;
        private Long locationId; private Long snId; private String delFlag;
    }
    @Data class IsolationTx {
        private String tenantId; private Long txId; private Long stockId; private Long materialId;
        private String sourceDocType; private Long receiveId; private Long lineId; private String txType;
        private Integer txFlag; private Long batchId; private Long warehouseId; private Long areaId;
        private Long locationId; private Long snId; private String delFlag;
    }
    @Data class IsolationStock {
        private String tenantId; private Long stockId; private Long materialId; private Long batchId;
        private Long warehouseId; private Long areaId; private Long locationId; private Long snId;
        private Integer frozenFlag; private Integer version; private Long sourceHeadTxId; private String delFlag;
    }
    @Data class IsolationBatch {
        private String tenantId; private Long batchId; private Long materialId; private Long warehouseId;
        private String sourceDocType; private Long receiveId; private String qcStatus; private String delFlag;
    }
    @Data class IsolationStorage {
        private String warehouseTenant; private Long warehouseId; private String warehouseDel;
        private String areaTenant; private Long areaId; private Long areaWarehouseId; private String areaDel;
        private String locationTenant; private Long locationId; private Long locationWarehouseId;
        private Long locationAreaId; private String locationDel;
    }

    @Data
    class ReceiptHeader {
        private String tenantId;
        private Long receiveId;
        private String receiveCode;
        private Long noticeId;
        private Long purId;
        private Long vendorId;
        private String status;
        private String delFlag;
        private Date createTime;
    }
    @Data
    class ReceiptLine {
        private String tenantId;
        private Long receiveId;
        private Long lineId;
        private Long materialId;
        private String baseUomId;
        private BigDecimal quantityReceived;
        private Long sourceDocLineId;
        private Long purchaseLineId;
        private String delFlag;
    }
    @Data
    class MaterialUnit {
        private String tenantId;
        private Long materialId;
        private Long baseUomId;
        private String delFlag;
    }
    @Data
    class NativeUnit {
        private String category;
        private Long isBase; private Long primaryId; private BigDecimal conversionRate;
        private String conversionVersion; private Date conversionVerifiedAt;
        private String tenantId;
        private Long uomId;
        private Integer quantityScale;
        private Integer status;
        private String delFlag;
    }
    /** 仅作为映射器记录：服务没有接收此可变 DTO 的接口。 */
    @Data
    class EvidenceRow {
        private Long birthId;
        private String birthSha256;
        private Long evidenceId;
        private String tenantId;
        private Long inspectionId;
        private String inspectionCode;
        private Long receiveId;
        private Long materialId;
        private Long baseUomId;
        private Integer quantityScale;
        private BigDecimal inspectedQuantity;
        private String inspectResult;
        private Long inspectorId;
        private String sourceKind;
        private String sourceVersion;
        private String actualBodySha256;
        private String receiptScopeSha256;
        private String evidenceSha256;
        private String firstMessageId;
        private Date sourceCreatedAt;
        private Date completedAt;
        private Date sourceUpdatedAt;
        private Date observedAt;
        private Date certifiedAt;
    }
}
