package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 生产入库写守卫的固定 SQL，显式租户与工单锁；删除行和异常流水仍参与完整性核验。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface WmProductionRecptGuardMapper {
    /** 只发现父编号；不得用这次非锁定读取授权编辑或执行。 */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT wo_id FROM wm_production_recpt WHERE tenant_id=#{tenant} AND recpt_id=#{id}")
    Long findWorkOrder(@Param("tenant") String tenant, @Param("id") Long id);

    /** 所有同工单写入先拿相同父锁，防止第二张草稿与执行互相绕过。 */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM prd_work_order WHERE tenant_id=#{tenant} AND wo_id=#{id} FOR UPDATE")
    PrdWorkOrder lockWorkOrder(@Param("tenant") String tenant, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM wm_production_recpt WHERE tenant_id=#{tenant} AND wo_id=#{wo} ORDER BY recpt_id FOR UPDATE")
    List<WmProductionRecpt> lockReceipts(@Param("tenant") String tenant, @Param("wo") Long wo);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM wm_material_stock_tx WHERE tenant_id=#{tenant} AND source_doc_type='PRD_WORK_ORDER' AND source_doc_id=#{wo} ORDER BY tx_id FOR UPDATE")
    List<WmMaterialStockTx> lockTransactions(@Param("tenant") String tenant, @Param("wo") Long wo);

    /** 父工单锁之后当前锁读报工明细；审核报工即使后来逻辑删除也仍已累加工单。 */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT reported_qty FROM prd_work_report WHERE tenant_id=#{tenant} AND wo_id=#{wo} AND report_status='APPROVED' ORDER BY report_id FOR UPDATE")
    List<BigDecimal> lockApprovedReportedQuantities(@Param("tenant") String tenant, @Param("wo") Long wo);

    /** 在当前锁读的行上求和，不沿用本事务或 REQUIRED 外层事务此前的普通读快照。 */
    default BigDecimal approvedReportedQuantity(String tenant, Long wo) {
        List<BigDecimal> quantities = lockApprovedReportedQuantities(tenant, wo);
        if (quantities == null) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal quantity : quantities) {
            // 空值或负数不是明确零报工，交由守卫拒绝，不利用求和掩盖未知事实。
            if (quantity == null || quantity.signum() < 0) return null;
            total = total.add(quantity);
        }
        return total;
    }

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM wm_material_stock WHERE tenant_id=#{tenant} AND stock_id=#{id} FOR UPDATE")
    WmMaterialStock lockStock(@Param("tenant") String tenant, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT * FROM wm_batch WHERE tenant_id=#{tenant} AND batch_id=#{id} FOR UPDATE")
    WmBatch lockBatch(@Param("tenant") String tenant, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("""
        SELECT m.material_id,m.material_code,m.material_name,m.material_spec,m.status AS material_status,
          m.del_flag AS material_del_flag,m.base_uom_id,u.uom_name,u.primary_id,u.is_base,u.conversion_rate,
          u.quantity_scale,u.status AS unit_status,u.del_flag AS unit_del_flag,u.conversion_version,
          u.conversion_verified_at,m.standard_cost
        FROM bd_material m JOIN bd_unit_of_measure u ON u.tenant_id=m.tenant_id AND u.uom_id=m.base_uom_id
        WHERE m.tenant_id=#{tenant} AND m.material_id=#{id} FOR UPDATE
        """)
    MaterialSource lockMaterial(@Param("tenant") String tenant, @Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("""
        SELECT w.warehouse_id,w.warehouse_code,w.warehouse_name,w.status AS warehouse_status,
          w.del_flag AS warehouse_del_flag,a.area_id,a.enable_flag AS area_enabled,
          a.frozen_flag AS area_frozen,a.del_flag AS area_del_flag,l.location_id,l.location_code,
          l.location_name,l.enable_flag AS location_enabled,l.frozen_flag AS location_frozen,
          l.del_flag AS location_del_flag
        FROM wm_warehouse w JOIN wm_storage_area a ON a.tenant_id=w.tenant_id AND a.warehouse_id=w.warehouse_id
        JOIN wm_storage_location l ON l.tenant_id=a.tenant_id AND l.area_id=a.area_id AND l.warehouse_id=w.warehouse_id
        WHERE w.tenant_id=#{tenant} AND w.warehouse_id=#{warehouse} AND l.location_id=#{location} FOR UPDATE
        """)
    StorageSource lockStorage(@Param("tenant") String tenant, @Param("warehouse") Long warehouse, @Param("location") Long location);

    @Select("SELECT CURRENT_DATE()")
    String databaseDate();

    record MaterialSource(Long materialId, String materialCode, String materialName, String materialSpec,
                          String materialStatus, String materialDelFlag, Long baseUomId, String uomName,
                          Long primaryId, Long isBase, BigDecimal conversionRate, Integer quantityScale,
                          String unitStatus, String unitDelFlag, String conversionVersion, Date conversionVerifiedAt,
                          BigDecimal standardCost) { @AutomapConstructor public MaterialSource { } }
    record StorageSource(Long warehouseId, String warehouseCode, String warehouseName, String warehouseStatus,
                         String warehouseDelFlag, Long areaId, Long areaEnabled, Long areaFrozen, String areaDelFlag,
                         Long locationId, String locationCode, String locationName, Long locationEnabled,
                         Long locationFrozen, String locationDelFlag) { @AutomapConstructor public StorageSource { } }
}