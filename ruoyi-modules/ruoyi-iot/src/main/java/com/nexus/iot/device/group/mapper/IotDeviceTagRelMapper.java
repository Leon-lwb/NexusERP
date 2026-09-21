package com.nexus.iot.device.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.iot.device.group.domain.IotDeviceTagRel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * IoT设备与标签关系Mapper接口
 * <p>
 * 自定义 SQL 一律显式带 {@code tenant_id}（规范 §2.6）。
 *
 * @author nexus
 */
public interface IotDeviceTagRelMapper extends BaseMapper<IotDeviceTagRel> {

    /**
     * 按标签集合查询设备ID（多标签取并集，用于"按标签筛选设备"）。
     *
     * @param tenantId 租户ID
     * @param tagIds   标签ID集合（非空）
     * @return 设备ID列表（去重）
     */
    @Select("<script>"
        + "SELECT DISTINCT device_id FROM iot_device_tag_rel "
        + "WHERE tenant_id = #{tenantId} AND tag_id IN "
        + "<foreach collection='tagIds' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
        + "</script>")
    List<Long> selectDeviceIdsByTagIds(@Param("tenantId") String tenantId,
                                       @Param("tagIds") Collection<Long> tagIds);

    /**
     * 查询某设备已绑定的标签ID集合。
     *
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @return 标签ID列表
     */
    @Select("SELECT tag_id FROM iot_device_tag_rel WHERE tenant_id = #{tenantId} AND device_id = #{deviceId}")
    List<Long> selectTagIdsByDevice(@Param("tenantId") String tenantId, @Param("deviceId") Long deviceId);

    /**
     * 统计各标签已绑定设备数（列表展示用，一次查询避免 N+1）。
     *
     * @param tenantId 租户ID
     * @param tagIds   标签ID集合（非空）
     * @return 每行含 tagId / deviceCount 两个键
     */
    @Select("<script>"
        + "SELECT tag_id AS tagId, COUNT(DISTINCT device_id) AS deviceCount FROM iot_device_tag_rel "
        + "WHERE tenant_id = #{tenantId} AND tag_id IN "
        + "<foreach collection='tagIds' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
        + " GROUP BY tag_id"
        + "</script>")
    List<Map<String, Object>> countDevicesByTagIds(@Param("tenantId") String tenantId,
                                                   @Param("tagIds") Collection<Long> tagIds);
}
