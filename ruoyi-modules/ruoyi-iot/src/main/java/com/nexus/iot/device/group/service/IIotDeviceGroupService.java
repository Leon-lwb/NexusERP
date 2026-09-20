package com.nexus.iot.device.group.service;

import com.nexus.iot.device.group.domain.bo.IotDeviceGroupAssignBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceGroupVo;

import java.util.List;

/**
 * IoT设备分组Service接口。
 *
 * @author nexus
 */
public interface IIotDeviceGroupService {

    /**
     * 查询分组列表（平铺，按 parent_id + order_num 排序；前端自行组树）。
     *
     * @param bo 查询条件（编码/名称/状态）
     * @return 分组列表（含 deviceCount）
     */
    List<IotDeviceGroupVo> selectList(IotDeviceGroupBo bo);

    /**
     * 查询分组详情。
     *
     * @param groupId 分组ID
     * @return 分组VO，不存在返回 null
     */
    IotDeviceGroupVo selectById(Long groupId);

    /**
     * 新增分组（自动计算 ancestors 物化路径；编码租户内唯一）。
     *
     * @param bo 分组业务对象
     * @return 影响行数
     */
    int insertByBo(IotDeviceGroupBo bo);

    /**
     * 修改分组（父分组变更时同步重写全部后代的 ancestors）。
     *
     * @param bo 分组业务对象
     * @return 影响行数
     */
    int updateByBo(IotDeviceGroupBo bo);

    /**
     * 批量删除分组（存在子分组或已挂设备时拒绝，避免产生孤儿数据）。
     *
     * @param ids 分组ID数组
     * @return 删除影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 批量把设备挂入/移出分组（幂等：重复挂载不报错）。
     *
     * @param bo 设备ID集合 + 目标分组 + 是否移出
     * @return 实际变更的关系行数
     */
    int assignDevices(IotDeviceGroupAssignBo bo);

    /**
     * 查询分组（含子树）下的设备ID集合。
     *
     * @param groupId        分组ID
     * @param includeSubGroup 是否包含子分组
     * @return 设备ID列表
     */
    List<Long> selectDeviceIds(Long groupId, Boolean includeSubGroup);
}
