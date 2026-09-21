package com.nexus.mes.eq.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ERP 侧备件相关只读 Mapper（跨库查询，MES-EQ-04）
 * <p>
 * 不加 {@code @DS} 注解，由调用方通过 {@code DynamicDataSourceContextHolder.push("erp")} 编程式切换数据源
 * （与 {@code com.nexus.mes.common.mapper.ErpMaterialMapper} 同一模式）。
 * <p>
 * 所有 SQL 显式携带 {@code tenant_id}（规范 §2.6：自定义原生 SQL 必须手动带租户条件）。
 *
 * @author Nexus
 */
@Mapper
public interface ErpSpareStockQueryMapper {

    /**
     * 按物料批量查询 ERP 可用量（口径：在库 − 已预留，且排除冻结行）
     *
     * @param tenantId    租户ID
     * @param materialIds ERP 物料ID集合
     * @return 每行含 materialId / availableQty
     */
    @Select("<script>" +
        "SELECT material_id AS materialId, " +
        "COALESCE(SUM(stock_quantity - IFNULL(reserved_quantity, 0)), 0) AS availableQty " +
        "FROM wm_material_stock " +
        "WHERE del_flag = '0' AND frozen_flag = 0 AND tenant_id = #{tenantId} " +
        "AND material_id IN " +
        "<foreach collection='materialIds' item='mid' open='(' separator=',' close=')'>#{mid}</foreach> " +
        "GROUP BY material_id" +
        "</script>")
    List<Map<String, Object>> selectAvailableByMaterialIds(@Param("tenantId") String tenantId,
                                                          @Param("materialIds") Collection<Long> materialIds);

    /**
     * 查询 ERP 物料（用于备件映射维护：校验存在且状态启用）
     *
     * @param tenantId   租户ID
     * @param materialId 物料ID
     * @return materialId / materialCode / materialName / status；不存在返回 null
     */
    @Select("SELECT material_id AS materialId, material_code AS materialCode, " +
        "material_name AS materialName, status AS status " +
        "FROM bd_material WHERE material_id = #{materialId} AND del_flag = '0' AND tenant_id = #{tenantId}")
    Map<String, Object> selectMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /**
     * 查询 ERP 仓库（用于备件映射维护：校验仓库存在）
     *
     * @param tenantId    租户ID
     * @param warehouseId 仓库ID
     * @return 命中行数
     */
    @Select("SELECT COUNT(1) FROM wm_warehouse WHERE warehouse_id = #{warehouseId} " +
        "AND del_flag = '0' AND tenant_id = #{tenantId}")
    long countWarehouse(@Param("tenantId") String tenantId, @Param("warehouseId") Long warehouseId);

    /**
     * 查询 ERP 仓库是否存在<b>且启用</b>（{@code status = 1}）
     * <p>用于 {@code warehouse_id} 存量回填入口的前置校验：回填目标仓库必须是启用仓库，
     * 否则回填后出入库过账会打到已停用仓库。</p>
     *
     * @param tenantId    租户ID
     * @param warehouseId 仓库ID
     * @return 命中行数，> 0 表示存在且启用
     */
    @Select("SELECT COUNT(1) FROM wm_warehouse WHERE warehouse_id = #{warehouseId} " +
        "AND del_flag = '0' AND status = 1 AND tenant_id = #{tenantId}")
    long countEnabledWarehouse(@Param("tenantId") String tenantId, @Param("warehouseId") Long warehouseId);

    /**
     * 查询某笔备件过账是否已在 ERP 落账（对账判据）
     *
     * @param tenantId 租户ID
     * @param bizKey   业务幂等键
     * @return 命中流水行数，> 0 表示已过账
     */
    @Select("SELECT COUNT(1) FROM wm_material_stock_tx WHERE source_doc_type = 'MES_SPARE' " +
        "AND source_doc_code = #{bizKey} AND tenant_id = #{tenantId} AND del_flag = '0'")
    long countSparePostingByBizKey(@Param("tenantId") String tenantId, @Param("bizKey") String bizKey);
}
