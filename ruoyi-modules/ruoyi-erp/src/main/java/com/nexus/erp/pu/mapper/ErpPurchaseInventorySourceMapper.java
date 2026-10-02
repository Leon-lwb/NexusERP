package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/** 原样读取当前租户的库存与父链；禁止聚合、补零、类型转换及业务状态过滤隐藏缺源。 */
@Mapper
public interface ErpPurchaseInventorySourceMapper {
    // 所有来源读取均清理 SESSION 一级缓存并禁用二级缓存；否则后锁时钟/当前锁读可被旧结果代答。
    // 仅 useCache=false 不清理一级缓存，不能作为新鲜度证明；不更改全局缓存范围。
    @Select("SELECT UNIX_TIMESTAMP(NOW(6)) AS epochSeconds, @@session.time_zone AS databaseTimeZone")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    DatabaseClock selectDatabaseClock();

    /** 核对当前实际库的数量存储类型，历史 DOUBLE 不通过 CAST 升级为精确来源。 */
    @Select("""
        SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, DATA_TYPE AS dataType,
               NUMERIC_PRECISION AS numericPrecision, NUMERIC_SCALE AS numericScale
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND ((TABLE_NAME = 'wm_material_stock' AND COLUMN_NAME IN ('stock_quantity', 'reserved_quantity'))
            OR (TABLE_NAME = 'bd_material' AND COLUMN_NAME = 'safety_stock'))
        ORDER BY TABLE_NAME, COLUMN_NAME
        """)
    @InterceptorIgnore(tenantLine = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<QuantityColumn> selectQuantityColumns();

    @Select("""
        SELECT tenant_id AS tenantId, material_id AS materialId, base_uom_id AS baseUomId,
               safety_stock AS safetyStock, status, del_flag AS delFlag
        FROM bd_material
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag IS NULL OR del_flag <> '1')
        ORDER BY material_id FOR UPDATE
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    BdMaterial lockMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("""
        SELECT tenant_id AS tenantId, uom_id AS uomId, category, is_base AS isBase,
               primary_id AS primaryId, conversion_rate AS conversionRate, quantity_scale AS quantityScale,
               conversion_version AS conversionVersion,
               UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS conversionVerifiedAtEpochMillis,
               status, del_flag AS delFlag
        FROM bd_unit_of_measure
        WHERE tenant_id = #{tenantId} AND uom_id = #{unitId}
          AND (del_flag IS NULL OR del_flag <> '1')
        ORDER BY uom_id FOR UPDATE
        """)
    @Results(@Result(column = "conversionVerifiedAtEpochMillis", property = "conversionVerifiedAt",
        javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class))
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    BdUnitOfMeasure lockUnit(@Param("tenantId") String tenantId, @Param("unitId") Long unitId);

    @Select("""
        SELECT tenant_id AS tenantId, stock_id AS stockId, material_id AS materialId,
               base_uom_id AS baseUomId, warehouse_id AS warehouseId, area_id AS areaId,
               location_id AS locationId, stock_quantity AS stockQuantity, reserved_quantity AS reservedQuantity,
               UNIX_TIMESTAMP(production_date) * 1000 AS productionDateEpochMillis,
               UNIX_TIMESTAMP(recpt_date) * 1000 AS recptDateEpochMillis,
               UNIX_TIMESTAMP(expire_date) * 1000 AS expireDateEpochMillis,
               frozen_flag AS frozenFlag, version, del_flag AS delFlag
        FROM wm_material_stock
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND (del_flag IS NULL OR del_flag <> '1')
        ORDER BY stock_id FOR UPDATE
        """)
    @Results({
        @Result(column = "productionDateEpochMillis", property = "productionDate", javaType = Date.class,
            typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "recptDateEpochMillis", property = "recptDate", javaType = Date.class,
            typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "expireDateEpochMillis", property = "expireDate", javaType = Date.class,
            typeHandler = EpochMillisDateTypeHandler.class)
    })
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<WmMaterialStock> lockStocks(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("""
        <script>
        SELECT tenant_id AS tenantId, warehouse_id AS warehouseId, status, del_flag AS delFlag
        FROM wm_warehouse
        WHERE tenant_id = #{tenantId} AND (del_flag IS NULL OR del_flag &lt;&gt; '1') AND warehouse_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY warehouse_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<WmWarehouse> lockWarehouses(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT tenant_id AS tenantId, area_id AS areaId, warehouse_id AS warehouseId,
               location_flag AS locationFlag, enable_flag AS enableFlag, frozen_flag AS frozenFlag,
               del_flag AS delFlag
        FROM wm_storage_area
        WHERE tenant_id = #{tenantId} AND (del_flag IS NULL OR del_flag &lt;&gt; '1') AND area_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY area_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<WmStorageArea> lockAreas(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT tenant_id AS tenantId, location_id AS locationId, warehouse_id AS warehouseId,
               area_id AS areaId, enable_flag AS enableFlag, frozen_flag AS frozenFlag, del_flag AS delFlag
        FROM wm_storage_location
        WHERE tenant_id = #{tenantId} AND (del_flag IS NULL OR del_flag &lt;&gt; '1') AND location_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY location_id FOR UPDATE
        </script>
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    List<WmStorageLocation> lockLocations(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    /** 秒和时区均来自当前连接的数据库，不信任调用方或 JVM 默认时区。 */
    record DatabaseClock(BigDecimal epochSeconds, String databaseTimeZone) { }

    /** 不预先过滤精度/类型，未知元数据由认证器整池拒绝。 */
    record QuantityColumn(String tableName, String columnName, String dataType,
                          Integer numericPrecision, Integer numericScale) { }

    /** 保留 NULL 的数据库时间戳解码器，不使用默认时区，也不将零日期或不精确值补成有效日期。 */
    final class EpochMillisDateTypeHandler extends BaseTypeHandler<Date> {
        @Override
        public void setNonNullParameter(PreparedStatement statement, int index, Date value, JdbcType jdbcType)
            throws SQLException {
            throw new SQLException("采购库存日期处理器仅支持读取");
        }

        @Override
        public Date getNullableResult(ResultSet result, String column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        @Override
        public Date getNullableResult(ResultSet result, int column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        @Override
        public Date getNullableResult(CallableStatement result, int column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        private static Date decode(BigDecimal millis) throws SQLException {
            if (millis == null) return null;
            try {
                if (millis.signum() <= 0) throw new ArithmeticException();
                return new Date(millis.longValueExact());
            } catch (ArithmeticException invalid) {
                throw new SQLException("采购库存日期时间戳无效或不精确");
            }
        }
    }
}
