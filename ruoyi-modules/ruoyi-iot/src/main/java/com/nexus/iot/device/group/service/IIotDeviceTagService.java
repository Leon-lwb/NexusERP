package com.nexus.iot.device.group.service;

import com.nexus.iot.device.group.domain.bo.IotDeviceTagBindBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceTagVo;

import java.util.List;

/**
 * IoT设备标签Service接口。
 *
 * @author nexus
 */
public interface IIotDeviceTagService {

    /**
     * 查询标签列表。
     *
     * @param bo 查询条件（名称模糊）
     * @return 标签列表（含 deviceCount）
     */
    List<IotDeviceTagVo> selectList(IotDeviceTagBo bo);

    /**
     * 查询标签详情。
     *
     * @param tagId 标签ID
     * @return 标签VO，不存在返回 null
     */
    IotDeviceTagVo selectById(Long tagId);

    /**
     * 新增标签（名称租户内唯一）。
     *
     * @param bo 标签业务对象
     * @return 影响行数
     */
    int insertByBo(IotDeviceTagBo bo);

    /**
     * 修改标签。
     *
     * @param bo 标签业务对象
     * @return 影响行数
     */
    int updateByBo(IotDeviceTagBo bo);

    /**
     * 批量删除标签（级联清理设备-标签关系）。
     *
     * @param ids 标签ID数组
     * @return 删除影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 批量绑定/解绑标签（幂等）。
     *
     * @param bo 设备ID集合 + 标签ID集合 + 是否解绑
     * @return 实际变更的关系行数
     */
    int bindTags(IotDeviceTagBindBo bo);

    /**
     * 查询某设备已绑定的标签ID集合。
     *
     * @param deviceId 设备ID
     * @return 标签ID列表
     */
    List<Long> selectTagIdsByDevice(Long deviceId);
}
