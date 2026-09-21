package com.nexus.iot.device.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.iot.device.group.domain.IotDeviceGroupRel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * IoT设备与分组关系Mapper接口
 * <p>
 * 自定义 SQL 一律显式带 {@code tenant_id}（规范 §2.6：原生 SQL 必须手动租户隔离，
 * 不能只依赖多租户插件）。
 *
 * @author nexus
 */
public interface IotDeviceGroupRelMapper extends BaseMapper<IotDeviceGroupRel> {

    /**
     * 按分组集合查询设备ID（用于"按分组（含子树）筛选设备"）。
     *
     * @param tenantId 租户ID
     * @param groupIds 分组ID集合（非空）
     * @return 设备ID列表（去重）
     */
    @Select("<script>"
        + "SELECT DISTINCT device_id FROM iot_device_group_rel "
        + "WHERE tenant_id = #{tenantId} AND group_id IN "
        + "<foreach collection='groupIds' item='g' open='(' separator=',' close=')'>#{g}</foreach>"
        + "</script>")
    List<Long> selectDeviceIdsByGroupIds(@Param("tenantId") String tenantId,
                                         @Param("groupIds") Collection<Long> groupIds);

    /**
     * 查询某设备已挂载的分组ID集合。
     *
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @return 分组ID列表
     */
    @Select("SELECT group_id FROM iot_device_group_rel WHERE tenant_id = #{tenantId} AND device_id = #{deviceId}")
    List<Long> selectGroupIdsByDevice(@Param("tenantId") String tenantId, @Param("deviceId") Long deviceId);

    /**
     * 统计各分组下已挂载设备数（列表展示用，一次查询避免 N+1）。
     *
     * @param tenantId 租户ID
     * @param groupIds 分组ID集合（非空）
     * @return 每行含 groupId / deviceCount 两个键
     */
    @Select("<script>"
        + "SELECT group_id AS groupId, COUNT(DISTINCT device_id) AS deviceCount FROM iot_device_group_rel "
        + "WHERE tenant_id = #{tenantId} AND group_id IN "
        + "<foreach collection='groupIds' item='g' open='(' separator=',' close=')'>#{g}</foreach>"
        + " GROUP BY group_id"
        + "</script>")
    List<Map<String, Object>> countDevicesByGroupIds(@Param("tenantId") String tenantId,
                                                     @Param("groupIds") Collection<Long> groupIds);
}
