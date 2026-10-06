package com.nexus.erp.ai.adapter.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.LocalDate;
import java.util.List;

/** 工单根归属与真实供应关系的固定当前读；不读master、不授予写授权。 */
@Mapper
public interface ErpDecisionPurchaseScopeMapper {
    String WORK_COLUMNS="wo_id AS woId,tenant_id AS tenantId,plan_id AS planId,source_type AS sourceType,"
        +"source_id AS sourceId,wo_status AS woStatus,del_flag AS delFlag";

    /** 定位读不作为授权；计划全集锁定后必须复查此行。 */
    @Select("SELECT "+WORK_COLUMNS+" FROM prd_work_order WHERE tenant_id=#{tenantId} AND wo_id=#{woId}")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({@Arg(column="woId",javaType=Long.class),@Arg(column="tenantId",javaType=String.class),
        @Arg(column="planId",javaType=Long.class),@Arg(column="sourceType",javaType=String.class),
        @Arg(column="sourceId",javaType=Long.class),@Arg(column="woStatus",javaType=String.class),
        @Arg(column="delFlag",javaType=String.class)})
    WorkSource findRootWorkOrder(@Param("tenantId") String tenantId,@Param("woId") Long woId);

    /** 不按状态或删除标志删去候选，以免掩盖歧义。 */
    @Select("SELECT "+WORK_COLUMNS+" FROM prd_work_order WHERE tenant_id=#{tenantId} AND plan_id=#{planId} ORDER BY wo_id FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({@Arg(column="woId",javaType=Long.class),@Arg(column="tenantId",javaType=String.class),
        @Arg(column="planId",javaType=Long.class),@Arg(column="sourceType",javaType=String.class),
        @Arg(column="sourceId",javaType=Long.class),@Arg(column="woStatus",javaType=String.class),
        @Arg(column="delFlag",javaType=String.class)})
    List<WorkSource> lockPlanWorkOrders(@Param("tenantId") String tenantId,@Param("planId") Long planId);

    /** 直接MRP关联须核完整租户，不能仅在本计划或本快照中宣称唯一。 */
    @Select("<script>SELECT "+WORK_COLUMNS+" FROM prd_work_order WHERE tenant_id=#{tenantId} "
        +"AND source_type='MATERIAL_REQUIREMENT' AND source_id IN "
        +"<foreach collection='requirementIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        +"ORDER BY wo_id FOR UPDATE</script>")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({@Arg(column="woId",javaType=Long.class),@Arg(column="tenantId",javaType=String.class),
        @Arg(column="planId",javaType=Long.class),@Arg(column="sourceType",javaType=String.class),
        @Arg(column="sourceId",javaType=Long.class),@Arg(column="woStatus",javaType=String.class),
        @Arg(column="delFlag",javaType=String.class)})
    List<WorkSource> lockDirectWorkOrders(@Param("tenantId") String tenantId,@Param("requirementIds") List<Long> requirementIds);

    /** 返回实际单位和头/行状态；数量仍只由现有完整认证器读取。 */
    @Select("""
        <script>
        SELECT d.detail_id AS detailId,d.tenant_id AS tenantId,d.requirement_id AS requirementId,
               d.material_id AS materialId,d.unit,DATE(d.required_date) AS requiredDate,
               d.status AS detailStatus,d.del_flag AS detailDelFlag,h.plan_id AS planId,
               h.status AS headStatus,h.del_flag AS headDelFlag
        FROM prd_material_requirement_detail d
        JOIN prd_material_requirement h ON h.requirement_id=d.requirement_id AND h.tenant_id=d.tenant_id
        WHERE d.tenant_id=#{tenantId} AND h.tenant_id=#{tenantId} AND d.material_id=#{materialId}
          AND d.detail_id IN <foreach collection='detailIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
          AND d.required_date &lt; DATE_ADD(#{windowEnd},INTERVAL 1 DAY)
        ORDER BY d.detail_id FOR UPDATE
        </script>
        """)
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({@Arg(column="detailId",javaType=Long.class),@Arg(column="tenantId",javaType=String.class),
        @Arg(column="requirementId",javaType=Long.class),@Arg(column="materialId",javaType=Long.class),
        @Arg(column="unit",javaType=String.class),@Arg(column="requiredDate",javaType=LocalDate.class),
        @Arg(column="detailStatus",javaType=Long.class),@Arg(column="detailDelFlag",javaType=String.class),
        @Arg(column="planId",javaType=Long.class),@Arg(column="headStatus",javaType=String.class),
        @Arg(column="headDelFlag",javaType=String.class)})
    List<RequirementSource> lockRequirementSources(@Param("tenantId") String tenantId,@Param("materialId") Long materialId,
        @Param("detailIds") List<Long> detailIds,@Param("windowEnd") LocalDate windowEnd);

    /** 所有候选关系稳定排序，不以LIMIT、价格或最小ID猜默认供应商。 */
    @Select("""
        SELECT r.relation_id AS relationId,r.tenant_id AS tenantId,r.material_id AS materialId,
               r.vendor_id AS vendorId,r.is_default AS isDefault,r.status AS relationStatus,
               r.del_flag AS relationDelFlag,v.status AS vendorStatus,v.del_flag AS vendorDelFlag
        FROM bd_material_vendor r
        LEFT JOIN bd_vendor v ON v.vendor_id=r.vendor_id AND v.tenant_id=r.tenant_id
        WHERE r.tenant_id=#{tenantId} AND r.material_id=#{materialId}
        ORDER BY r.relation_id FOR UPDATE
        """)
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({@Arg(column="relationId",javaType=Long.class),@Arg(column="tenantId",javaType=String.class),
        @Arg(column="materialId",javaType=Long.class),@Arg(column="vendorId",javaType=Long.class),
        @Arg(column="isDefault",javaType=Integer.class),@Arg(column="relationStatus",javaType=Integer.class),
        @Arg(column="relationDelFlag",javaType=String.class),@Arg(column="vendorStatus",javaType=String.class),
        @Arg(column="vendorDelFlag",javaType=String.class)})
    List<VendorChoice> lockVendorChoices(@Param("tenantId") String tenantId,@Param("materialId") Long materialId);

    /** 固定空查询持有定义锁，新用工单表不能借旧schema verifier冒充已核验。 */
    @Select("SELECT SQL_NO_CACHE wo_id FROM prd_work_order WHERE 1=0")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> pinScopeDefinition();

    /** 仅固定元数据表没有tenant_id；所有业务方法仍显式限定租户并经过租户插件。 */
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='prd_work_order'")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    String scopeTableEngine();

    record WorkSource(Long woId,String tenantId,Long planId,String sourceType,Long sourceId,String woStatus,String delFlag) {}
    record RequirementSource(Long detailId,String tenantId,Long requirementId,Long materialId,String unit,LocalDate requiredDate,
        Long detailStatus,String detailDelFlag,Long planId,String headStatus,String headDelFlag) {}
    record VendorChoice(Long relationId,String tenantId,Long materialId,Long vendorId,Integer isDefault,Integer relationStatus,
        String relationDelFlag,String vendorStatus,String vendorDelFlag) {}
}
