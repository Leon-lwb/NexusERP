package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP工序/工作站只读Mapper（跨库查询 bd_process/bd_workstation, 表在 ry-erp 库）
 * <p>数据源: @DS("erp") 切到 ry-erp 库(对齐 StationSchWorkerMapper 模式)。
 * 工位终端↔工作中心设备联动视图(M3)专用: 工位码工序 → bd_process 默认工作中心 → eq_equipment。</p>
 *
 * @author Nexus
 */
@DS("erp")
@Mapper
public interface StationErpProcessMapper {

    /**
     * 按工序编码查 bd_process(含默认工作中心冗余字段)
     *
     * @param processCode 工序编码(工位码第 4 段)
     * @return processId/processCode/processName/workstationId/workstationCode/workstationName, 无则 null
     */
    @Select("SELECT process_id AS processId, process_code AS processCode, process_name AS processName, " +
        "workstation_id AS workstationId, workstation_code AS workstationCode, workstation_name AS workstationName " +
        "FROM bd_process WHERE process_code = #{processCode} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectByProcessCode(@Param("processCode") String processCode);

    /**
     * 查默认工作中心为指定工作站的全部工序
     *
     * @param workstationId 工作站ID(工作中心)
     * @return processId/processCode/processName 列表
     */
    @Select("SELECT process_id AS processId, process_code AS processCode, process_name AS processName " +
        "FROM bd_process WHERE workstation_id = #{workstationId} AND del_flag = '0' ORDER BY process_code")
    List<Map<String, Object>> selectByWorkstationId(@Param("workstationId") Long workstationId);

    /**
     * 按工作站ID查 bd_workstation(联动视图补车间名)
     *
     * @param workstationId 工作站ID(工作中心)
     * @return workstationId/workstationCode/workstationName/workshopCode/workshopName/status, 无则 null
     */
    @Select("SELECT workstation_id AS workstationId, workstation_code AS workstationCode, " +
        "workstation_name AS workstationName, workshop_code AS workshopCode, workshop_name AS workshopName, status " +
        "FROM bd_workstation WHERE workstation_id = #{workstationId} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectWorkstationById(@Param("workstationId") Long workstationId);
}
