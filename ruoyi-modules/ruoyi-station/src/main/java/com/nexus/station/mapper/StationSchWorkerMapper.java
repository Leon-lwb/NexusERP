package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 工人档案只读Mapper（跨库跨模块查询 sch_worker, 表在 ry-erp 库）
 * <p>数据源: @DS("erp") 切到 ry-erp 库(对齐 SchWorkerServiceImpl 的类级注解);
 * tenant_id 由多租户插件按 TenantHelper.dynamic("000000") 上下文自动注入。</p>
 *
 * @author Nexus
 */
@DS("erp")
@Mapper
public interface StationSchWorkerMapper {

    /**
     * 按员工工号查询工人档案(在用记录)
     *
     * @param employeeId 员工工号(扫码输入)
     * @return worker_id/user_id/user_name/employee_id/workshop_name/work_status 等, 无则 null
     */
    @Select("SELECT worker_id, user_id, workshop_id, workshop_name, user_name, " +
        "employee_id, work_category, skill_level, work_status " +
        "FROM sch_worker WHERE employee_id = #{employeeId} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectByEmployeeId(@Param("employeeId") String employeeId);
}
