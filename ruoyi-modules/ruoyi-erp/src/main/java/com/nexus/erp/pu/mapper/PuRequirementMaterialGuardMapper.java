package com.nexus.erp.pu.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 复合主键 (tenant_id, material_id) 的竞争池行锁；不能用单键 BaseMapper 映射。
 * 调用方须从 @DS("erp") 的服务代理进入同一 ERP 事务，先 ensureGuard，再 lockVersion。
 */
@Mapper
public interface PuRequirementMaterialGuardMapper {

    @Insert("INSERT IGNORE INTO pu_requirement_material_guard (tenant_id, material_id, version) "
        + "VALUES (#{tenantId}, #{materialId}, 0)")
    int ensureGuard(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    @Select("SELECT version FROM pu_requirement_material_guard "
        + "WHERE tenant_id = #{tenantId} AND material_id = #{materialId} FOR UPDATE")
    Integer lockVersion(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 锁定且校验竞争池后递增版本；返回 0 应由 ERP 服务回滚整个业务事务。 */
    @Update("UPDATE pu_requirement_material_guard SET version = version + 1, update_time = NOW() "
        + "WHERE tenant_id = #{tenantId} AND material_id = #{materialId} "
        + "AND version = #{expectedVersion}")
    int advanceVersion(@Param("tenantId") String tenantId, @Param("materialId") Long materialId,
                       @Param("expectedVersion") Integer expectedVersion);
}
