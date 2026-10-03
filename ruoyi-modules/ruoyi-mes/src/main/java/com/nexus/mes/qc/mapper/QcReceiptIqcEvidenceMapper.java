package com.nexus.mes.qc.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 读取当前已提交的原生 MES 来源；不关联 ERP，也不执行写操作。 */
public interface QcReceiptIqcEvidenceMapper {
    /** 已核实归属的 ERP 或 MES 连接上的原生引擎和列定义。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT c.TABLE_NAME AS table_name, t.ENGINE AS engine, c.COLUMN_NAME AS column_name,
               c.DATA_TYPE AS data_type, c.NUMERIC_PRECISION AS numeric_precision,
               c.NUMERIC_SCALE AS numeric_scale, c.IS_NULLABLE AS is_nullable
        FROM information_schema.COLUMNS c
        JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME IN ('qc_iqc','qc_inspect_detail')
        ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
        """)
    List<Column> selectNativeColumns();

    /** 读取完整的永久索引组成部分，不根据当前记录数推断唯一性。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT TABLE_NAME AS table_name, INDEX_NAME AS index_name, NON_UNIQUE AS non_unique,
               SEQ_IN_INDEX AS seq_in_index, COLUMN_NAME AS column_name,
               SUB_PART AS sub_part, INDEX_TYPE AS index_type
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('qc_iqc','qc_inspect_detail')
        ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
        """)
    List<IndexPart> selectNativeIndexes();

    // 特意不按 del_flag 过滤：已删除或被替换的历史检验不能作为当前证明。
    @Select("""
        SELECT iqc_id, tenant_id, iqc_no, receive_id, material_id, quantity,
               status, inspect_result, inspector_id, inspect_time, create_time, update_time, del_flag,
               JSON_OBJECT(
            'iqc_id', iqc_id,
            'tenant_id', tenant_id,
            'iqc_no', iqc_no,
            'receive_id', receive_id,
            'material_id', material_id,
            'material_code', material_code,
            'material_name', material_name,
            'vendor_id', vendor_id,
            'vendor_name', vendor_name,
            'quantity', quantity,
            'sample_qty', sample_qty,
            'inspect_result', inspect_result,
            'defect_info', defect_info,
            'inspector_id', inspector_id,
            'inspector_name', inspector_name,
            'inspect_time', inspect_time,
            'status', status,
            'remark', remark,
            'create_dept', create_dept,
            'create_by', create_by,
            'create_time', create_time,
            'update_by', update_by,
            'update_time', update_time,
            'del_flag', del_flag,
            'photo_oss_ids', photo_oss_ids) AS actual_body
        FROM qc_iqc WHERE tenant_id = #{tenantId} AND iqc_id = #{inspectionId}
        """)
    InspectionRow selectInspection(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    // 安全路径仅使用身份和结果：不提供类型化数量，也不从检验测量单位推断数量单位。
    @Select("""
        SELECT iqc_id, tenant_id, iqc_no, receive_id, material_id,
               status, inspect_result, inspector_id, inspect_time, create_time, update_time, del_flag,
               JSON_OBJECT(
            'iqc_id', iqc_id,
            'tenant_id', tenant_id,
            'iqc_no', iqc_no,
            'receive_id', receive_id,
            'material_id', material_id,
            'material_code', material_code,
            'material_name', material_name,
            'vendor_id', vendor_id,
            'vendor_name', vendor_name,
            'quantity', quantity,
            'sample_qty', sample_qty,
            'inspect_result', inspect_result,
            'defect_info', defect_info,
            'inspector_id', inspector_id,
            'inspector_name', inspector_name,
            'inspect_time', inspect_time,
            'status', status,
            'remark', remark,
            'create_dept', create_dept,
            'create_by', create_by,
            'create_time', create_time,
            'update_by', update_by,
            'update_time', update_time,
            'del_flag', del_flag,
            'photo_oss_ids', photo_oss_ids) AS actual_body
        FROM qc_iqc WHERE tenant_id = #{tenantId} AND iqc_id = #{inspectionId}
        """)
    InspectionRow selectInspectionIdentity(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Select("""
        SELECT iqc_id FROM qc_iqc
        WHERE tenant_id = #{tenantId} AND receive_id = #{receiveId} AND material_id = #{materialId}
        ORDER BY iqc_id
        """)
    List<Long> selectInspectionVersions(@Param("tenantId") String tenantId,
        @Param("receiveId") Long receiveId, @Param("materialId") Long materialId);

    @Select("""
        SELECT detail_id, tenant_id, check_id, check_type,
               JSON_OBJECT(
            'detail_id', detail_id,
            'tenant_id', tenant_id,
            'check_id', check_id,
            'check_type', check_type,
            'template_id', template_id,
            'item_id', item_id,
            'item_code', item_code,
            'item_name', item_name,
            'data_type', data_type,
            'standard_type', standard_type,
            'standard_desc', standard_desc,
            'unit', unit,
            'criticality', criticality,
            'sample_qty', sample_qty,
            'measured_value', measured_value,
            'nominal_value', nominal_value,
            'lsl_value', lsl_value,
            'usl_value', usl_value,
            'tol_upper', tol_upper,
            'tol_lower', tol_lower,
            'judge_result', judge_result,
            'seq_num', seq_num,
            'remark', remark,
            'create_dept', create_dept,
            'create_by', create_by,
            'create_time', create_time,
            'update_by', update_by,
            'update_time', update_time,
            'del_flag', del_flag) AS actual_body
        FROM qc_inspect_detail
        WHERE tenant_id = #{tenantId} AND check_type = 'IQC' AND check_id = #{inspectionId}
        ORDER BY detail_id
        """)
    List<DetailBody> selectInspectionBodies(@Param("tenantId") String tenantId, @Param("inspectionId") Long inspectionId);

    @Data
    class InspectionRow {
        private Long iqcId;
        private String tenantId;
        private String iqcNo;
        private Long receiveId;
        private Long materialId;
        private BigDecimal quantity;
        private String status;
        private String inspectResult;
        private Long inspectorId;
        private Date inspectTime;
        private Date createTime;
        private Date updateTime;
        private String delFlag;
        private String actualBody;
    }
    @Data
    class DetailBody {
        private Long detailId;
        private String tenantId;
        private Long checkId;
        private String checkType;
        private String actualBody;
    }
}
