package com.nexus.erp.pu.mapper;

import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdVendor;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
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

/**
 * 单租户采购目录的固定 SELECT；select 保持只读，lock 仅供已核验 ERP 事务当前读。
 * 不提供需求、库存、采购额度或业务写入接口，不接受任意 SQL 文本。
 * 不查询历史 MOQ 或关系日期；新核验字段的 NULL 原样保留，不自动补全。
 * 新条款和换算 DATETIME 在数据库会话时区内投影为时间戳毫秒，避免 JDBC/JVM
 * 默认时区把日期解释为另一个时刻；reader 另行拒绝未能明确核验的会话时区。
 * 返回的主数据实体可变，仅供 reader 立即复制和拒绝式核验，不可直接用作授权。
 */
@Mapper
public interface ErpPurchaseCatalogSourceMapper {

    /** 数据库业务时钟与原始会话时区；不得用应用时钟或外部时间参数替代。 */
    @Select("""
        SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)) AS epoch_seconds,
               @@session.time_zone AS database_time_zone
        """)
    @ConstructorArgs({
        @Arg(column = "epoch_seconds", javaType = BigDecimal.class),
        @Arg(column = "database_time_zone", javaType = String.class)
    })
    DatabaseClock selectDatabaseClock();

    /** 仅读取指定租户、指定身份的未删除物料，状态和身份仍由 reader 复核。 */
    @Select("""
        SELECT tenant_id AS tenantId, material_id AS materialId, base_uom_id AS baseUomId,
               status, del_flag AS delFlag
        FROM bd_material
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId} AND del_flag = '0'
        """)
    BdMaterial selectMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 供应商 ACTIVE 状态不能由关系表名称、编码或调用方声明替代。 */
    @Select("""
        SELECT tenant_id AS tenantId, vendor_id AS vendorId, status, del_flag AS delFlag
        FROM bd_vendor
        WHERE tenant_id = #{tenantId} AND vendor_id = #{vendorId} AND del_flag = '0'
        """)
    BdVendor selectVendor(@Param("tenantId") String tenantId, @Param("vendorId") Long vendorId);

    /** 不用 LIMIT 或默认供应商排序掩盖重复关系，不继承 min_order_qty。 */
    @Select("""
        SELECT tenant_id AS tenantId, relation_id AS relationId, material_id AS materialId,
               vendor_id AS vendorId, order_uom_id AS orderUomId, moq_uom_id AS moqUomId,
               verified_moq_qty AS verifiedMoqQty, order_multiple AS orderMultiple,
               allow_split AS allowSplit, units_per_pack AS unitsPerPack, terms_version AS termsVersion,
               UNIX_TIMESTAMP(terms_effective_at) * 1000 AS termsEffectiveAtEpochMillis,
               UNIX_TIMESTAMP(terms_expires_at) * 1000 AS termsExpiresAtEpochMillis,
               UNIX_TIMESTAMP(terms_verified_at) * 1000 AS termsVerifiedAtEpochMillis,
               status, del_flag AS delFlag
        FROM bd_material_vendor
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND vendor_id = #{vendorId} AND del_flag = '0'
        """)
    @Results({
        @Result(column = "allowSplit", property = "allowSplit",
            javaType = Boolean.class, typeHandler = BinaryFlagTypeHandler.class),
        @Result(column = "termsEffectiveAtEpochMillis", property = "termsEffectiveAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "termsExpiresAtEpochMillis", property = "termsExpiresAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "termsVerifiedAtEpochMillis", property = "termsVerifiedAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<BdMaterialVendor> selectRelations(@Param("tenantId") String tenantId,
        @Param("materialId") Long materialId, @Param("vendorId") Long vendorId);

    /** 读取明确 ID 集合，祖先由 reader 有界补读；不以单位名称寻找或猜测转换。 */
    @Select("""
        <script>
        SELECT tenant_id AS tenantId, uom_id AS uomId, category, is_base AS isBase,
               primary_id AS primaryId, conversion_rate AS conversionRate,
               quantity_scale AS quantityScale, conversion_version AS conversionVersion,
               UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS conversionVerifiedAtEpochMillis,
               status, del_flag AS delFlag
        FROM bd_unit_of_measure
        WHERE tenant_id = #{tenantId} AND del_flag = '0' AND uom_id IN
        <foreach collection="unitIds" item="unitId" open="(" separator="," close=")">
            #{unitId}
        </foreach>
        </script>
        """)
    @Results({
        @Result(column = "conversionVerifiedAtEpochMillis", property = "conversionVerifiedAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<BdUnitOfMeasure> selectUnits(@Param("tenantId") String tenantId, @Param("unitIds") List<Long> unitIds);

    /** 固定当前读并按主键锁定；不使用会话/二级缓存替代实际 SQL，不接任意 SQL 文本。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)) AS epoch_seconds,
               @@session.time_zone AS database_time_zone
        FOR UPDATE
        """)
    @ConstructorArgs({
        @Arg(column = "epoch_seconds", javaType = BigDecimal.class),
        @Arg(column = "database_time_zone", javaType = String.class)
    })
    DatabaseClock lockDatabaseClock();

    /** 固定当前读并按主键锁定；不使用会话/二级缓存替代实际 SQL，不接任意 SQL 文本。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT tenant_id AS tenantId, material_id AS materialId, base_uom_id AS baseUomId,
               status, del_flag AS delFlag
        FROM bd_material
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId} AND del_flag = '0'
        ORDER BY material_id ASC
        FOR UPDATE
        """)
    BdMaterial lockMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 固定当前读并按主键锁定；不使用会话/二级缓存替代实际 SQL，不接任意 SQL 文本。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT tenant_id AS tenantId, vendor_id AS vendorId, status, del_flag AS delFlag
        FROM bd_vendor
        WHERE tenant_id = #{tenantId} AND vendor_id = #{vendorId} AND del_flag = '0'
        ORDER BY vendor_id ASC
        FOR UPDATE
        """)
    BdVendor lockVendor(@Param("tenantId") String tenantId, @Param("vendorId") Long vendorId);

    /** 固定当前读并按主键锁定；不使用会话/二级缓存替代实际 SQL，不接任意 SQL 文本。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT tenant_id AS tenantId, relation_id AS relationId, material_id AS materialId,
               vendor_id AS vendorId, order_uom_id AS orderUomId, moq_uom_id AS moqUomId,
               verified_moq_qty AS verifiedMoqQty, order_multiple AS orderMultiple,
               allow_split AS allowSplit, units_per_pack AS unitsPerPack, terms_version AS termsVersion,
               UNIX_TIMESTAMP(terms_effective_at) * 1000 AS termsEffectiveAtEpochMillis,
               UNIX_TIMESTAMP(terms_expires_at) * 1000 AS termsExpiresAtEpochMillis,
               UNIX_TIMESTAMP(terms_verified_at) * 1000 AS termsVerifiedAtEpochMillis,
               status, del_flag AS delFlag
        FROM bd_material_vendor
        WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND vendor_id = #{vendorId} AND del_flag = '0'
        ORDER BY relation_id ASC
        FOR UPDATE
        """)
    @Results({
        @Result(column = "allowSplit", property = "allowSplit",
            javaType = Boolean.class, typeHandler = BinaryFlagTypeHandler.class),
        @Result(column = "termsEffectiveAtEpochMillis", property = "termsEffectiveAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "termsExpiresAtEpochMillis", property = "termsExpiresAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "termsVerifiedAtEpochMillis", property = "termsVerifiedAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<BdMaterialVendor> lockRelations(@Param("tenantId") String tenantId,
        @Param("materialId") Long materialId, @Param("vendorId") Long vendorId);

    /** 固定当前读并按主键锁定；不使用会话/二级缓存替代实际 SQL，不接任意 SQL 文本。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        <script>
        SELECT tenant_id AS tenantId, uom_id AS uomId, category, is_base AS isBase,
               primary_id AS primaryId, conversion_rate AS conversionRate,
               quantity_scale AS quantityScale, conversion_version AS conversionVersion,
               UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS conversionVerifiedAtEpochMillis,
               status, del_flag AS delFlag
        FROM bd_unit_of_measure
        WHERE tenant_id = #{tenantId} AND del_flag = '0' AND uom_id IN
        <foreach collection="unitIds" item="unitId" open="(" separator="," close=")">
            #{unitId}
        </foreach>
        ORDER BY uom_id ASC
        FOR UPDATE
        </script>
        """)
    @Results({
        @Result(column = "conversionVerifiedAtEpochMillis", property = "conversionVerifiedAt",
            javaType = Date.class, typeHandler = EpochMillisDateTypeHandler.class)
    })
    List<BdUnitOfMeasure> lockUnits(@Param("tenantId") String tenantId, @Param("unitIds") List<Long> unitIds);

    /** 时间戳为数据库产生的十进制秒，时区仅是数据库原始值，不含可信布尔旗号。 */
    record DatabaseClock(BigDecimal epochSeconds, String databaseTimeZone) { }

    /**
     * 仅供本 Mapper 的 allowSplit 字段使用的只读二值解码器，不注册为全局 Boolean
     * 处理器。不调用 getBoolean 折叠非零值；NULL 保持未知，原始数值只允许 0 或 1。
     */
    final class BinaryFlagTypeHandler extends BaseTypeHandler<Boolean> {
        public BinaryFlagTypeHandler() { }

        @Override
        public void setNonNullParameter(PreparedStatement statement, int index, Boolean value, JdbcType jdbcType)
            throws SQLException {
            throw new SQLException("采购目录拆零条款处理器仅支持读取");
        }

        @Override
        public Boolean getNullableResult(ResultSet result, String column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        @Override
        public Boolean getNullableResult(ResultSet result, int column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        @Override
        public Boolean getNullableResult(CallableStatement result, int column) throws SQLException {
            return decode(result.getBigDecimal(column));
        }

        private static Boolean decode(BigDecimal raw) throws SQLException {
            if (raw == null) return null;
            if (raw.compareTo(BigDecimal.ZERO) == 0) return Boolean.FALSE;
            if (raw.compareTo(BigDecimal.ONE) == 0) return Boolean.TRUE;
            throw new SQLException("采购目录拆零条款原值必须是 0 或 1");
        }
    }

    /**
     * 只读时间戳毫秒解码器，保留 SQL NULL；禁止把无效日期的零值或不精确值
     * 升级为已核验日期，不借用 JVM 默认时区或对毫秒进行舍入。
     */
    final class EpochMillisDateTypeHandler extends BaseTypeHandler<Date> {
        public EpochMillisDateTypeHandler() { }

        @Override
        public void setNonNullParameter(PreparedStatement statement, int index, Date value, JdbcType jdbcType)
            throws SQLException {
            throw new SQLException("采购目录日期处理器仅支持读取");
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
            if (millis.signum() <= 0) throw new SQLException("采购目录日期时间戳无效");
            try {
                return new Date(millis.longValueExact());
            } catch (ArithmeticException invalid) {
                throw new SQLException("采购目录日期时间戳不能精确读取");
            }
        }
    }
}
