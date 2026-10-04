package com.nexus.erp.ai.adapter.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 新物料同事务出生的固定 SQL；不提供补登记、更新出生或历史回填语句。 */
@Mapper
@InterceptorIgnore(tenantLine="true", dataPermission="true")
public interface ErpCertifiedSourceBirthMapper {
    @Select("SELECT SQL_NO_CACHE birth_id FROM erp_certified_source_birth WHERE 1=0 UNION ALL SELECT material_id FROM bd_material WHERE 1=0 UNION ALL SELECT uom_id FROM bd_unit_of_measure WHERE 1=0 UNION ALL SELECT category_id FROM bd_material_category WHERE 1=0 UNION ALL SELECT warehouse_id FROM wm_warehouse WHERE 1=0 UNION ALL SELECT area_id FROM wm_storage_area WHERE 1=0 UNION ALL SELECT location_id FROM wm_storage_location WHERE 1=0 UNION ALL SELECT stock_id FROM wm_material_stock WHERE 1=0 UNION ALL SELECT tx_id FROM wm_material_stock_tx WHERE 1=0 UNION ALL SELECT material_id FROM pu_requirement_material_guard WHERE 1=0")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> pinRegistry();

    @Select("SELECT TABLE_NAME AS tableName,ENGINE AS engine FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('erp_certified_source_birth','bd_material','bd_unit_of_measure','bd_material_category','wm_warehouse','wm_storage_area','wm_storage_location','wm_material_stock','wm_material_stock_tx','pu_requirement_material_guard') ORDER BY TABLE_NAME")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Engine> sourceEngines();

    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_certified_source_birth'")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    String registryEngine();

    @Select("SELECT COLUMN_NAME AS columnName, DATA_TYPE AS dataType, IS_NULLABLE AS nullable, CHARACTER_MAXIMUM_LENGTH AS textLength FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_certified_source_birth' ORDER BY ORDINAL_POSITION")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Column> registryColumns();

    @Select("SELECT INDEX_NAME AS indexName,NON_UNIQUE AS nonUnique,COLUMN_NAME AS columnName,SEQ_IN_INDEX AS position FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='erp_certified_source_birth' ORDER BY INDEX_NAME,SEQ_IN_INDEX")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Index> registryIndexes();

    @Select("SELECT UNIX_TIMESTAMP(NOW())*1000 AS epochMillis,@@session.time_zone AS timeZone")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Clock clock();

    @Select("SELECT material_id FROM bd_material WHERE material_id=#{id} OR (tenant_id=#{tenant} AND material_code=#{code}) ORDER BY material_id FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockMaterialConflicts(@Param("tenant") String tenant,@Param("id") Long id,@Param("code") String code);

    @Select("SELECT tenant_id AS tenantId,material_id AS materialId,material_code AS materialCode,category_id AS categoryId,base_uom_id AS baseUomId,status,del_flag AS delFlag,create_by AS createdBy,UNIX_TIMESTAMP(create_time)*1000 AS createdAt FROM bd_material WHERE material_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Material lockMaterial(@Param("id") Long id);

    @Select("SELECT tenant_id AS tenantId,uom_id AS unitId,primary_id AS primaryId,is_base AS isBase,category,conversion_rate AS conversionRate,quantity_scale AS quantityScale,conversion_version AS conversionVersion,UNIX_TIMESTAMP(conversion_verified_at)*1000 AS verifiedAt,status,del_flag AS delFlag FROM bd_unit_of_measure WHERE uom_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Unit lockUnit(@Param("id") Long id);

    @Select("SELECT tenant_id AS tenantId,category_id AS categoryId,status,del_flag AS delFlag FROM bd_material_category WHERE category_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Category lockCategory(@Param("id") Long id);

    @Select("SELECT tenant_id AS tenantId,warehouse_id AS warehouseId,status,del_flag AS delFlag FROM wm_warehouse WHERE warehouse_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Warehouse lockWarehouse(@Param("id") Long id);

    @Select("SELECT tenant_id AS tenantId,area_id AS areaId,warehouse_id AS warehouseId,enable_flag AS enabled,frozen_flag AS frozen,location_flag AS locationFlag,del_flag AS delFlag FROM wm_storage_area WHERE area_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Area lockArea(@Param("id") Long id);

    @Select("SELECT tenant_id AS tenantId,location_id AS locationId,warehouse_id AS warehouseId,area_id AS areaId,enable_flag AS enabled,frozen_flag AS frozen,del_flag AS delFlag FROM wm_storage_location WHERE location_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Location lockLocation(@Param("id") Long id);

    /** 不按仓库、租户、删除标记或数量过滤；孤立、删除及跨租户记录均不能隐藏。 */
    @Select("SELECT stock_id FROM wm_material_stock WHERE material_id=#{id} ORDER BY stock_id FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockAllStocks(@Param("id") Long id);

    @Select("SELECT tx_id FROM wm_material_stock_tx WHERE material_id=#{id} ORDER BY tx_id FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockAllTransactions(@Param("id") Long id);

    @Insert("INSERT INTO bd_material(material_id,tenant_id,material_code,material_name,category_id,base_uom_id,purchase_uom_id,inventory_uom_id,sale_uom_id,material_type,source_type,is_batch_manage,is_serial_manage,safety_stock,status,is_phantom,inspection_required,remark,create_by,create_time,update_by,update_time,del_flag) VALUES(#{m.materialId},#{m.tenantId},#{m.materialCode},#{m.materialName},#{m.categoryId},#{m.baseUomId},#{m.baseUomId},#{m.baseUomId},#{m.baseUomId},1,'PROCUREMENT',1,0,0.0000,1,0,1,#{m.scenario},#{m.actorId},FROM_UNIXTIME(#{m.bornAt}/1000),#{m.actorId},FROM_UNIXTIME(#{m.bornAt}/1000),'0')")
    int insertMaterial(@Param("m") NewMaterial material);

    /** 新范围 guard 随物料出生事务创建，预览不承担初始化职责。 */
    @Insert("INSERT INTO pu_requirement_material_guard(tenant_id,material_id,version) VALUES(#{tenant},#{id},0)")
    int insertNewGuard(@Param("tenant") String tenant,@Param("id") Long material);

    @Insert("INSERT INTO erp_certified_source_birth(birth_id,tenant_id,material_id,material_code,category_id,base_uom_id,quantity_scale,conversion_version,unit_verified_at,unit_fingerprint,warehouse_id,area_id,location_id,storage_fingerprint,born_at,actor_id,scenario,initial_state,birth_fingerprint) VALUES(#{b.birthId},#{b.tenantId},#{b.materialId},#{b.materialCode},#{b.categoryId},#{b.baseUomId},#{b.quantityScale},#{b.conversionVersion},FROM_UNIXTIME(#{b.unitVerifiedAt}/1000),#{b.unitFingerprint},#{b.warehouseId},#{b.areaId},#{b.locationId},#{b.storageFingerprint},FROM_UNIXTIME(#{b.bornAt}/1000),#{b.actorId},#{b.scenario},#{b.initialState},#{b.birthFingerprint})")
    int insertBirth(@Param("b") StoredBirth birth);

    @Select("SELECT birth_id AS birthId,tenant_id AS tenantId,material_id AS materialId,material_code AS materialCode,category_id AS categoryId,base_uom_id AS baseUomId,quantity_scale AS quantityScale,conversion_version AS conversionVersion,UNIX_TIMESTAMP(unit_verified_at)*1000 AS unitVerifiedAt,unit_fingerprint AS unitFingerprint,warehouse_id AS warehouseId,area_id AS areaId,location_id AS locationId,storage_fingerprint AS storageFingerprint,UNIX_TIMESTAMP(born_at)*1000 AS bornAt,actor_id AS actorId,scenario,initial_state AS initialState,birth_fingerprint AS birthFingerprint FROM erp_certified_source_birth WHERE tenant_id=#{tenant} AND material_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    StoredBirth lockBirth(@Param("tenant") String tenant,@Param("id") Long id);

    /** 原始主键及数据库时间均保留，不增加可信布尔默认值。 */
    record Engine(String tableName,String engine) { }
    record Clock(BigDecimal epochMillis,String timeZone) { }
    record Unit(String tenantId,Long unitId,Long primaryId,Long isBase,String category,BigDecimal conversionRate,
                Integer quantityScale,String conversionVersion,Long verifiedAt,Long status,String delFlag) { }
    record Category(String tenantId,Long categoryId,Long status,String delFlag) { }
    record Warehouse(String tenantId,Long warehouseId,Long status,String delFlag) { }
    record Area(String tenantId,Long areaId,Long warehouseId,Long enabled,Long frozen,Long locationFlag,String delFlag) { }
    record Location(String tenantId,Long locationId,Long warehouseId,Long areaId,Long enabled,Long frozen,String delFlag) { }
    record Material(String tenantId,Long materialId,String materialCode,Long categoryId,Long baseUomId,Long status,
                    String delFlag,Long createdBy,Long createdAt) { }
    record NewMaterial(Long materialId,String tenantId,String materialCode,String materialName,Long categoryId,
                       Long baseUomId,Long bornAt,Long actorId,String scenario) { }
    record StoredBirth(Long birthId,String tenantId,Long materialId,String materialCode,Long categoryId,Long baseUomId,
                       Integer quantityScale,String conversionVersion,Long unitVerifiedAt,String unitFingerprint,
                       Long warehouseId,Long areaId,Long locationId,String storageFingerprint,Long bornAt,Long actorId,
                       String scenario,String initialState,String birthFingerprint) { }
    record Column(String columnName,String dataType,String nullable,Long textLength) { }
    record Index(String indexName,Integer nonUnique,String columnName,Integer position) { }
}
