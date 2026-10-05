package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购专用原始库存来源当前读：不聚合、CAST、COALESCE，不按数量、删除、生命周期或日期过滤隐藏未知事实。
 * 原始主键读取保留跨租户/已删除父行，由认证器逐行拒绝；不能将缺头/缺行变成貌似可信的零。
 * 仅本Mapper绕过租户插件，认证器在输出前核对每个原始行的完整租户身份，不向调用方暴露未认证行。
 * 所有列表入参必须非空、唯一且按稳定主键升序；禁止读写共享的一二级缓存，不包含任何数据库写语句。
 * epoch列保持原生数据库时点，BigDecimal原数量不经影子列、默认零或JVM时区解释。
 */
@Mapper
public interface ErpPurchaseStockLineageMapper {
    /** 在同ERP事务打开真实表并固定定义，首段禁MySQL5.7服务端缓存；不读业务数据。 */
    @Select("""
        SELECT SQL_NO_CACHE material_id AS id FROM bd_material WHERE 1=0
        UNION ALL SELECT uom_id FROM bd_unit_of_measure WHERE 1=0
        UNION ALL SELECT stock_id FROM wm_material_stock WHERE 1=0
        UNION ALL SELECT warehouse_id FROM wm_warehouse WHERE 1=0
        UNION ALL SELECT area_id FROM wm_storage_area WHERE 1=0
        UNION ALL SELECT location_id FROM wm_storage_location WHERE 1=0
        UNION ALL SELECT tx_id FROM wm_material_stock_tx WHERE 1=0
        UNION ALL SELECT receive_id FROM wm_material_receive WHERE 1=0
        UNION ALL SELECT line_id FROM wm_material_receive_line WHERE 1=0
        UNION ALL SELECT detail_id FROM wm_material_receive_detail WHERE 1=0
        UNION ALL SELECT issue_id FROM wm_production_issue WHERE 1=0
        UNION ALL SELECT line_id FROM wm_production_issue_line WHERE 1=0
        UNION ALL SELECT requirement_id FROM prd_material_requirement WHERE 1=0
        UNION ALL SELECT detail_id FROM prd_material_requirement_detail WHERE 1=0
        UNION ALL SELECT wo_id FROM prd_work_order WHERE 1=0
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<Long> pinTableDefinitions();

    @Select("""
        SELECT TABLE_NAME AS tableName, ENGINE AS engine FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
          ('bd_material', 'bd_unit_of_measure', 'wm_material_stock', 'wm_warehouse',
           'wm_storage_area', 'wm_storage_location', 'wm_material_stock_tx',
           'wm_material_receive', 'wm_material_receive_line', 'wm_material_receive_detail',
           'wm_production_issue', 'wm_production_issue_line',
           'prd_material_requirement', 'prd_material_requirement_detail', 'prd_work_order')
        ORDER BY TABLE_NAME
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<TableEngine> selectTableEngines();

    record TableEngine(String tableName, String engine) {}


    @Select("""
        SELECT UNIX_TIMESTAMP(NOW(6)) AS epochSeconds, @@session.time_zone AS databaseTimeZone
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    DatabaseClock selectDatabaseClock();

    @Select("""
        SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, DATA_TYPE AS dataType,
               NUMERIC_PRECISION AS numericPrecision, NUMERIC_SCALE AS numericScale
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND ((TABLE_NAME = 'wm_material_stock' AND COLUMN_NAME IN ('stock_quantity', 'reserved_quantity'))
            OR (TABLE_NAME = 'wm_material_stock_tx' AND COLUMN_NAME IN ('tx_quantity', 'before_quantity', 'after_quantity'))
            OR (TABLE_NAME = 'wm_material_receive_line' AND COLUMN_NAME = 'quantity_received')
            OR (TABLE_NAME = 'wm_material_receive_detail' AND COLUMN_NAME = 'quantity')
            OR (TABLE_NAME = 'wm_production_issue' AND COLUMN_NAME = 'total_qty')
            OR (TABLE_NAME = 'wm_production_issue_line' AND COLUMN_NAME IN ('quantity', 'required_qty', 'issued_qty', 'short_qty'))
            OR (TABLE_NAME = 'prd_material_requirement_detail' AND COLUMN_NAME IN ('required_quantity', 'issued_quantity'))
            OR (TABLE_NAME = 'bd_unit_of_measure' AND COLUMN_NAME = 'conversion_rate'))
        ORDER BY TABLE_NAME, COLUMN_NAME
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<QuantityColumn> selectQuantityColumns();

    @Select("""
        SELECT uom_id AS uomId, tenant_id AS tenantId, quantity_scale AS quantityScale,
               conversion_version AS conversionVersion,
               UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS verifiedAtEpochMillis,
               category, is_base AS isBase, primary_id AS primaryId, conversion_rate AS conversionRate,
               status, del_flag AS delFlag
        FROM bd_unit_of_measure
        WHERE uom_id = #{unitId}
        ORDER BY uom_id ASC FOR UPDATE
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    UnitRow lockUnit(@Param("tenantId") String tenantId, @Param("unitId") Long unitId);

    @Select("""
        SELECT stock_id AS stockId, tenant_id AS tenantId, material_id AS materialId,
               base_uom_id AS baseUomId, warehouse_id AS warehouseId, area_id AS areaId,
               location_id AS locationId, batch_id AS batchId, sn_id AS snId,
               stock_quantity AS stockQuantity, reserved_quantity AS reservedQuantity,
               frozen_flag AS frozenFlag, version, del_flag AS delFlag,
               UNIX_TIMESTAMP(recpt_date) * 1000 AS recptAtEpochMillis,
               UNIX_TIMESTAMP(expire_date) * 1000 AS expireAtEpochMillis
        FROM wm_material_stock
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
        ORDER BY stock_id ASC FOR UPDATE
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<StockRow> lockStocks(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("""
        <script>
        SELECT tx_id AS txId, tenant_id AS tenantId, material_id AS materialId,
               material_stock_id AS stockId, base_uom_id AS baseUomId, tx_type AS txType, tx_flag AS txFlag,
               tx_quantity AS txQuantity, before_quantity AS beforeQuantity, after_quantity AS afterQuantity,
               source_doc_type AS sourceDocType, source_doc_id AS sourceDocId,
               source_doc_line_id AS sourceDocLineId, source_doc_code AS sourceDocCode,
               related_tx_id AS relatedTxId, UNIX_TIMESTAMP(tx_date) * 1000 AS txAtEpochMillis,
               del_flag AS delFlag, warehouse_id AS warehouseId, area_id AS areaId,
               location_id AS locationId, batch_id AS batchId, sn_id AS snId
        FROM wm_material_stock_tx
        WHERE (tenant_id = #{tenantId} AND material_id = #{materialId}) OR material_stock_id IN
          <foreach collection="stockIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY tx_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<TransactionRow> lockTransactions(@Param("tenantId") String tenantId, @Param("materialId") Long materialId,
                                           @Param("stockIds") List<Long> stockIds);

    @Select("""
        <script>
        SELECT area_id AS areaId, tenant_id AS tenantId, warehouse_id AS warehouseId, frozen_flag AS frozenFlag, enable_flag AS enableFlag, location_flag AS locationFlag, del_flag AS delFlag
        FROM wm_storage_area
        WHERE area_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY area_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<AreaRow> lockAreas(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT location_id AS locationId, tenant_id AS tenantId, warehouse_id AS warehouseId, area_id AS areaId, frozen_flag AS frozenFlag, enable_flag AS enableFlag, del_flag AS delFlag
        FROM wm_storage_location
        WHERE location_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY location_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<LocationRow> lockLocations(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT receive_id AS receiveId, tenant_id AS tenantId, receive_code AS receiveCode, status, warehouse_id AS warehouseId, area_id AS areaId, location_id AS locationId, UNIX_TIMESTAMP(receive_date) * 1000 AS receiveAtEpochMillis, UNIX_TIMESTAMP(shelf_time) * 1000 AS shelfAtEpochMillis, del_flag AS delFlag
        FROM wm_material_receive
        WHERE receive_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY receive_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<ReceiveHead> lockReceiveHeads(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT line_id AS lineId, tenant_id AS tenantId, receive_id AS receiveId, material_id AS materialId, base_uom_id AS baseUomId, quantity_received AS quantityReceived, del_flag AS delFlag
        FROM wm_material_receive_line
        WHERE receive_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY line_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<ReceiveLine> lockReceiveLines(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT detail_id AS detailId, tenant_id AS tenantId, receive_id AS receiveId, line_id AS lineId, material_id AS materialId, base_uom_id AS baseUomId, quantity, warehouse_id AS warehouseId, area_id AS areaId, location_id AS locationId, batch_id AS batchId, sn_id AS snId, del_flag AS delFlag
        FROM wm_material_receive_detail
        WHERE receive_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY detail_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<ReceiveDetail> lockReceiveDetails(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT issue_id AS issueId, tenant_id AS tenantId, issue_code AS issueCode, wo_id AS woId, warehouse_id AS warehouseId, status, issue_type AS issueType, total_qty AS totalQty, UNIX_TIMESTAMP(issue_date) * 1000 AS issueAtEpochMillis, del_flag AS delFlag
        FROM wm_production_issue
        WHERE issue_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY issue_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<IssueHead> lockIssueHeads(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT line_id AS lineId, tenant_id AS tenantId, issue_id AS issueId, material_id AS materialId, base_uom_id AS baseUomId, warehouse_id AS warehouseId, location_id AS locationId, batch_id AS batchId, quantity, required_qty AS requiredQty, issued_qty AS issuedQty, short_qty AS shortQty, requirement_detail_id AS requirementDetailId, line_status AS lineStatus, del_flag AS delFlag
        FROM wm_production_issue_line
        WHERE issue_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY line_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<IssueLine> lockIssueLines(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT detail_id AS detailId, tenant_id AS tenantId, requirement_id AS requirementId, material_id AS materialId, unit, required_quantity AS requiredQuantity, issued_quantity AS issuedQuantity, source_type AS sourceType, status, backflush_flag AS backflushFlag, del_flag AS delFlag
        FROM prd_material_requirement_detail
        WHERE detail_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY detail_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<RequirementDetail> lockRequirementDetails(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT requirement_id AS requirementId, tenant_id AS tenantId, requirement_no AS requirementNo, plan_id AS planId, bom_id AS bomId, material_id AS materialId, source_type AS sourceType, status, del_flag AS delFlag
        FROM prd_material_requirement
        WHERE requirement_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY requirement_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<RequirementHead> lockRequirementHeads(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT wo_id AS woId, tenant_id AS tenantId, wo_no AS woNo, plan_id AS planId, bom_id AS bomId, material_id AS materialId, wo_status AS woStatus, del_flag AS delFlag, source_type AS sourceType, source_id AS sourceId
        FROM prd_work_order
        WHERE wo_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY wo_id ASC FOR UPDATE
        </script>
        """)
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<WorkOrder> lockWorkOrders(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    record DatabaseClock(BigDecimal epochSeconds, String databaseTimeZone) {}

    record QuantityColumn(String tableName, String columnName, String dataType, Integer numericPrecision,
        Integer numericScale) {}

    record UnitRow(Long uomId, String tenantId, Integer quantityScale, String conversionVersion,
        BigDecimal verifiedAtEpochMillis, String category, Long isBase, Long primaryId, BigDecimal conversionRate,
        Long status, String delFlag) {}

    record StockRow(Long stockId, String tenantId, Long materialId, String baseUomId, Long warehouseId,
        Long areaId, Long locationId, Long batchId, Long snId, BigDecimal stockQuantity,
        BigDecimal reservedQuantity, Long frozenFlag, Integer version, String delFlag,
        BigDecimal recptAtEpochMillis, BigDecimal expireAtEpochMillis) {}

    record TransactionRow(Long txId, String tenantId, Long materialId, Long stockId, String baseUomId,
        String txType, Integer txFlag, BigDecimal txQuantity, BigDecimal beforeQuantity, BigDecimal afterQuantity,
        String sourceDocType, Long sourceDocId, Long sourceDocLineId, String sourceDocCode, Long relatedTxId,
        BigDecimal txAtEpochMillis, String delFlag, Long warehouseId, Long areaId, Long locationId, Long batchId, Long snId) {
        /** 保留旧17参数签名；缺失的原始仓储维度保持未知，不从当前库存补造认证证据。 */
        public TransactionRow(Long txId, String tenantId, Long materialId, Long stockId, String baseUomId,
                String txType, Integer txFlag, BigDecimal txQuantity, BigDecimal beforeQuantity, BigDecimal afterQuantity,
                String sourceDocType, Long sourceDocId, Long sourceDocLineId, String sourceDocCode, Long relatedTxId,
                BigDecimal txAtEpochMillis, String delFlag) {
            this(txId, tenantId, materialId, stockId, baseUomId, txType, txFlag, txQuantity, beforeQuantity, afterQuantity,
                sourceDocType, sourceDocId, sourceDocLineId, sourceDocCode, relatedTxId, txAtEpochMillis, delFlag,
                null, null, null, null, null);
        }
    }

    record AreaRow(Long areaId, String tenantId, Long warehouseId, Long frozenFlag, Long enableFlag,
        Long locationFlag, String delFlag) {}

    record LocationRow(Long locationId, String tenantId, Long warehouseId, Long areaId, Long frozenFlag,
        Long enableFlag, String delFlag) {}

    record ReceiveHead(Long receiveId, String tenantId, String receiveCode, String status, Long warehouseId,
        Long areaId, Long locationId, BigDecimal receiveAtEpochMillis, BigDecimal shelfAtEpochMillis,
        String delFlag) {}

    record ReceiveLine(Long lineId, String tenantId, Long receiveId, Long materialId, String baseUomId,
        BigDecimal quantityReceived, String delFlag) {}

    record ReceiveDetail(Long detailId, String tenantId, Long receiveId, Long lineId, Long materialId,
        String baseUomId, BigDecimal quantity, Long warehouseId, Long areaId, Long locationId, Long batchId,
        Long snId, String delFlag) {}

    record IssueHead(Long issueId, String tenantId, String issueCode, Long woId, Long warehouseId, String status,
        String issueType, BigDecimal totalQty, BigDecimal issueAtEpochMillis, String delFlag) {}

    record IssueLine(Long lineId, String tenantId, Long issueId, Long materialId, String baseUomId,
        Long warehouseId, Long locationId, Long batchId, BigDecimal quantity, BigDecimal requiredQty,
        BigDecimal issuedQty, BigDecimal shortQty, Long requirementDetailId, String lineStatus, String delFlag) {}

    record RequirementDetail(Long detailId, String tenantId, Long requirementId, Long materialId, String unit,
        BigDecimal requiredQuantity, BigDecimal issuedQuantity, String sourceType, Long status,
        Integer backflushFlag, String delFlag) {}

    record RequirementHead(Long requirementId, String tenantId, String requirementNo, Long planId, Long bomId,
        Long materialId, String sourceType, String status, String delFlag) {}

    record WorkOrder(Long woId, String tenantId, String woNo, Long planId, Long bomId, Long materialId,
        String woStatus, String delFlag, String sourceType, Long sourceId) {
        /** 保留旧8参数签名；来源类型/ID保持未知，不能由计划/BOM/母物料猜出DRAFT原生绑定。 */
        public WorkOrder(Long woId, String tenantId, String woNo, Long planId, Long bomId, Long materialId,
                String woStatus, String delFlag) {
            this(woId, tenantId, woNo, planId, bomId, materialId, woStatus, delFlag, null, null);
        }
    }
}
