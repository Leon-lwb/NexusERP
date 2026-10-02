package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

/** 固定只读元数据语句，不接收表名/SQL/可信旗号；信息模式没有租户业务行。 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface ErpPurchaseSourceSchemaMapper {
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("""
        SELECT TABLE_NAME AS tableName, ENGINE AS engine FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
          ('bd_material','bd_unit_of_measure','bd_vendor','bd_material_vendor',
           'wm_material_stock','wm_warehouse','wm_storage_area','wm_storage_location',
           'prd_material_requirement','prd_material_requirement_detail',
           'pu_purchase_requisition','pu_purchase_requisition_line','pu_purchase_order','pu_purchase_order_line',
           'pu_requirement_material_guard','pu_requirement_allocation_ledger','pu_requirement_allocation_event')
        ORDER BY TABLE_NAME
        """)
    List<TableEngine> selectTableEngines();

    /**
     * 固定空结果实际打开各表并固定定义；首个 SELECT 的 SQL_NO_CACHE 排除 MySQL 5.7 服务端缓存代答。
     * UNION 只允许首段指定该选项；在已支持 MySQL 8.0/8.4 中此选项兼容但忽略，不修改全局/会话配置。
     * 不读取租户业务数据；实际元数据锁与并发实效仍需单独现场验证。
     */
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("""
        SELECT SQL_NO_CACHE material_id AS id FROM bd_material WHERE 1=0
        UNION ALL SELECT uom_id FROM bd_unit_of_measure WHERE 1=0
        UNION ALL SELECT vendor_id FROM bd_vendor WHERE 1=0
        UNION ALL SELECT relation_id FROM bd_material_vendor WHERE 1=0
        UNION ALL SELECT stock_id FROM wm_material_stock WHERE 1=0
        UNION ALL SELECT warehouse_id FROM wm_warehouse WHERE 1=0
        UNION ALL SELECT area_id FROM wm_storage_area WHERE 1=0
        UNION ALL SELECT location_id FROM wm_storage_location WHERE 1=0
        UNION ALL SELECT requirement_id FROM prd_material_requirement WHERE 1=0
        UNION ALL SELECT detail_id FROM prd_material_requirement_detail WHERE 1=0
        UNION ALL SELECT requisition_id FROM pu_purchase_requisition WHERE 1=0
        UNION ALL SELECT line_id FROM pu_purchase_requisition_line WHERE 1=0
        UNION ALL SELECT order_id FROM pu_purchase_order WHERE 1=0
        UNION ALL SELECT line_id FROM pu_purchase_order_line WHERE 1=0
        UNION ALL SELECT material_id FROM pu_requirement_material_guard WHERE 1=0
        UNION ALL SELECT allocation_id FROM pu_requirement_allocation_ledger WHERE 1=0
        UNION ALL SELECT event_id FROM pu_requirement_allocation_event WHERE 1=0
        """)
    List<Long> pinTableDefinitions();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT VERSION()")
    String selectServerVersion();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT @@SESSION.transaction_isolation")
    String selectSessionIsolation();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("SELECT @@GLOBAL.innodb_locks_unsafe_for_binlog")
    BigDecimal selectUnsafeGapLockFlag57();

    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    @Select("""
        SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, DATA_TYPE AS dataType,
               NUMERIC_PRECISION AS numericPrecision, NUMERIC_SCALE AS numericScale
        FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND
          ((TABLE_NAME = 'bd_material' AND COLUMN_NAME = 'safety_stock')
           OR (TABLE_NAME = 'bd_unit_of_measure' AND COLUMN_NAME = 'conversion_rate')
           OR (TABLE_NAME = 'bd_material_vendor' AND COLUMN_NAME IN ('verified_moq_qty','order_multiple','units_per_pack'))
           OR (TABLE_NAME = 'wm_material_stock' AND COLUMN_NAME IN ('stock_quantity','reserved_quantity'))
           OR (TABLE_NAME = 'prd_material_requirement_detail' AND COLUMN_NAME IN ('required_quantity','issued_quantity'))
           OR (TABLE_NAME = 'pu_purchase_requisition_line' AND COLUMN_NAME IN ('apply_quantity','approved_quantity'))
           OR (TABLE_NAME = 'pu_purchase_order_line' AND COLUMN_NAME IN ('order_quantity','received_quantity','returned_quantity'))
           OR (TABLE_NAME = 'pu_requirement_allocation_ledger' AND COLUMN_NAME IN
              ('covered_demand_base_qty','occupied_base_qty','fulfilled_base_qty','released_base_qty'))
           OR (TABLE_NAME = 'pu_requirement_allocation_event' AND COLUMN_NAME IN ('occupied_base_before','occupied_base_after')))
        ORDER BY TABLE_NAME, COLUMN_NAME
        """)
    List<NativeQuantityColumn> selectNativeQuantityColumns();

    record NativeQuantityColumn(String tableName, String columnName, String dataType,
                                Integer numericPrecision, Integer numericScale) { }
    record TableEngine(String tableName, String engine) { }
}
