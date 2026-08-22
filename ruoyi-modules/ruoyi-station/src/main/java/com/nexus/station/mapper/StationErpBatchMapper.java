package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP物料批次/条码/替代料只读Mapper（跨库查询 wm_batch/wm_barcode/bd_bom_alternative, 表在 ry-erp 库）
 * <p>数据源: @DS("erp") 切到 ry-erp 库(对齐 StationSchWorkerMapper 模式)。
 * 上料防错(M3 批次4)专用: 批次码 B 前缀查 wm_batch / 条码 RC 前缀查 wm_barcode,
 * 替代料按 MBOM 快照行 bom_detail_id 查 bd_bom_alternative。</p>
 *
 * @author Nexus
 */
@DS("erp")
@Mapper
public interface StationErpBatchMapper {

    /**
     * 按批次编码查 wm_batch(批次码 B+yyyyMMdd+4位流水, 唯一索引)
     *
     * @return batchId/batchCode/materialId/materialCode/materialName/spec/expiryDate/vendorName/quantity, 无则 null
     */
    @Select("SELECT batch_id AS batchId, batch_code AS batchCode, material_id AS materialId, " +
        "material_code AS materialCode, material_name AS materialName, material_spec AS spec, " +
        "expiry_date AS expiryDate, vendor_name AS vendorName, quantity " +
        "FROM wm_batch WHERE batch_code = #{batchCode} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectBatchByCode(@Param("batchCode") String batchCode);

    /**
     * 按条码值查 wm_barcode(条码实例 RC+yyyyMMdd+6位流水, 唯一索引)
     *
     * @return barcodeCode/batchId/batchCode/materialId, 无则 null
     */
    @Select("SELECT barcode_code AS barcodeCode, batch_id AS batchId, batch_code AS batchCode, " +
        "material_id AS materialId " +
        "FROM wm_barcode WHERE barcode_code = #{barcodeCode} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectBarcodeByCode(@Param("barcodeCode") String barcodeCode);

    /**
     * 按 MBOM 快照行 bom_detail_id 查启用的替代料(生效期含当日, NULL 不限)
     *
     * @param detailIds bd_bom_detail.detail_id 集合
     * @return bomDetailId/altMaterialId/altMaterialCode/altMaterialName/priority/substitutionType
     */
    @Select("<script>" +
        "SELECT detail_id AS bomDetailId, alt_material_id AS altMaterialId, " +
        "alt_material_code AS altMaterialCode, alt_material_name AS altMaterialName, " +
        "priority, substitution_type AS substitutionType " +
        "FROM bd_bom_alternative WHERE status = 1 AND del_flag = '0' " +
        "AND (effective_date IS NULL OR effective_date &lt;= CURRENT_DATE) " +
        "AND (expiry_date IS NULL OR expiry_date &gt;= CURRENT_DATE) " +
        "AND detail_id IN " +
        "<foreach collection='detailIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        "</script>")
    List<Map<String, Object>> selectAlternativesByDetailIds(@Param("detailIds") List<Long> detailIds);
}
