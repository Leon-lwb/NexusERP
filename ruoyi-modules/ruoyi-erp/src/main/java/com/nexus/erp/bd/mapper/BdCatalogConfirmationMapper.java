package com.nexus.erp.bd.mapper;

import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.DatabaseClock;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.EpochMillisDateTypeHandler;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 固定租户、明确身份的确认持久化边界；所有当前读禁用缓存并在事务内持有行锁。 */
public interface BdCatalogConfirmationMapper {
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP) AS epoch_seconds, @@session.time_zone AS database_time_zone
        """)
    @ConstructorArgs({@Arg(column = "epoch_seconds", javaType = BigDecimal.class),
        @Arg(column = "database_time_zone", javaType = String.class)})
    DatabaseClock databaseClock();

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT *, UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS verified_epoch
        FROM bd_unit_of_measure WHERE tenant_id = #{tenantId} AND uom_id = #{id} AND del_flag = '0'
        FOR UPDATE
        """)
    @Results(@Result(column = "verified_epoch", property = "conversionVerifiedAt", javaType = Date.class,
        typeHandler = EpochMillisDateTypeHandler.class))
    BdUnitOfMeasure lockUnit(@Param("tenantId") String tenantId, @Param("id") Long id);

    /** 换算版本属于同一单位族；只锁定明确根与其直接成员，不猜测多级换算链。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT *, UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS verified_epoch
        FROM bd_unit_of_measure WHERE tenant_id = #{tenantId} AND del_flag = '0'
          AND (uom_id = #{rootId} OR primary_id = #{rootId}) ORDER BY uom_id FOR UPDATE
        """)
    @Results(@Result(column = "verified_epoch", property = "conversionVerifiedAt", javaType = Date.class,
        typeHandler = EpochMillisDateTypeHandler.class))
    List<BdUnitOfMeasure> lockUnitFamily(@Param("tenantId") String tenantId, @Param("rootId") Long rootId);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        <script>
        SELECT *, UNIX_TIMESTAMP(conversion_verified_at) * 1000 AS verified_epoch
        FROM bd_unit_of_measure WHERE tenant_id = #{tenantId} AND del_flag = '0' AND uom_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY uom_id FOR UPDATE
        </script>
        """)
    @Results(@Result(column = "verified_epoch", property = "conversionVerifiedAt", javaType = Date.class,
        typeHandler = EpochMillisDateTypeHandler.class))
    List<BdUnitOfMeasure> lockUnits(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT *, UNIX_TIMESTAMP(terms_effective_at) * 1000 AS effective_epoch,
               UNIX_TIMESTAMP(terms_expires_at) * 1000 AS expires_epoch,
               UNIX_TIMESTAMP(terms_verified_at) * 1000 AS verified_epoch
        FROM bd_material_vendor WHERE tenant_id = #{tenantId} AND relation_id = #{id} AND del_flag = '0'
        FOR UPDATE
        """)
    @Results({@Result(column = "effective_epoch", property = "termsEffectiveAt", javaType = Date.class,
        typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "expires_epoch", property = "termsExpiresAt", javaType = Date.class,
            typeHandler = EpochMillisDateTypeHandler.class),
        @Result(column = "verified_epoch", property = "termsVerifiedAt", javaType = Date.class,
            typeHandler = EpochMillisDateTypeHandler.class)})
    BdMaterialVendor lockRelation(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT * FROM bd_material WHERE tenant_id = #{tenantId} AND material_id = #{id} AND del_flag = '0'
        FOR UPDATE
        """)
    BdMaterial lockMaterial(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT * FROM bd_vendor WHERE tenant_id = #{tenantId} AND vendor_id = #{id} AND del_flag = '0'
        FOR UPDATE
        """)
    BdVendor lockVendor(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT relation_id FROM bd_material_vendor WHERE tenant_id = #{tenantId} AND material_id = #{materialId}
          AND vendor_id = #{vendorId} AND del_flag = '0' ORDER BY relation_id FOR UPDATE
        """)
    List<Long> lockRelationIds(@Param("tenantId") String tenantId, @Param("materialId") Long materialId,
        @Param("vendorId") Long vendorId);

    /** 已核验成员只换代版本，保留原核验时间；绝不把历史空认证字段改为已认证。 */
    @Update("""
        UPDATE bd_unit_of_measure SET conversion_version = #{newVersion}, update_by = #{actorId},
               update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{tenantId} AND del_flag = '0' AND status = 1 AND uom_id != #{targetId}
          AND (uom_id = #{rootId} OR primary_id = #{rootId}) AND conversion_version = #{oldVersion}
          AND conversion_verified_at IS NOT NULL
        """)
    int rotateCertifiedUnitVersions(@Param("tenantId") String tenantId, @Param("rootId") Long rootId,
        @Param("targetId") Long targetId, @Param("oldVersion") String oldVersion,
        @Param("newVersion") String newVersion, @Param("actorId") Long actorId);

    /** 日期字符串已按上海业务时区生成，避免依赖 JVM 与 JDBC 默认时区写 DATETIME。 */
    @Update("""
        UPDATE bd_unit_of_measure SET primary_id = #{row.primaryId}, primary_name = #{row.primaryName},
               conversion_rate = #{row.conversionRate}, quantity_scale = #{row.quantityScale},
               conversion_version = #{row.conversionVersion},
               conversion_verified_at = STR_TO_DATE(#{verifiedAt}, '%Y-%m-%d %H:%i:%s'),
               update_by = #{row.updateBy}, update_time = STR_TO_DATE(#{verifiedAt}, '%Y-%m-%d %H:%i:%s')
        WHERE tenant_id = #{row.tenantId} AND uom_id = #{row.uomId} AND del_flag = '0' AND status = 1
          AND conversion_version <=> #{expectedVersion}
        """)
    int confirmUnit(@Param("row") BdUnitOfMeasure row, @Param("expectedVersion") String expectedVersion,
        @Param("verifiedAt") String verifiedAt);

    /** 只写明确的新条款；旧起订量与旧关系有效日期不参与认证字段回填。 */
    @Update("""
        UPDATE bd_material_vendor SET order_uom_id = #{row.orderUomId}, moq_uom_id = #{row.moqUomId},
               verified_moq_qty = #{row.verifiedMoqQty}, order_multiple = #{row.orderMultiple},
               allow_split = #{row.allowSplit}, units_per_pack = #{row.unitsPerPack}, terms_version = #{row.termsVersion},
               terms_effective_at = STR_TO_DATE(#{effectiveAt}, '%Y-%m-%d %H:%i:%s'),
               terms_expires_at = STR_TO_DATE(#{expiresAt}, '%Y-%m-%d %H:%i:%s'),
               terms_verified_at = STR_TO_DATE(#{verifiedAt}, '%Y-%m-%d %H:%i:%s'),
               update_by = #{row.updateBy}, update_time = STR_TO_DATE(#{verifiedAt}, '%Y-%m-%d %H:%i:%s')
        WHERE tenant_id = #{row.tenantId} AND relation_id = #{row.relationId} AND del_flag = '0' AND status = 1
          AND material_id = #{row.materialId} AND vendor_id = #{row.vendorId}
          AND terms_version <=> #{expectedVersion}
        """)
    int confirmTerms(@Param("row") BdMaterialVendor row, @Param("expectedVersion") String expectedVersion,
        @Param("effectiveAt") String effectiveAt, @Param("expiresAt") String expiresAt,
        @Param("verifiedAt") String verifiedAt);
}
