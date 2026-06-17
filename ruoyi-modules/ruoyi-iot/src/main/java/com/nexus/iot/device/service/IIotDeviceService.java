package com.nexus.iot.device.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;

import java.util.List;

/**
 * IoT设备Service接口
 *
 * @author nexus
 */
public interface IIotDeviceService {

    /**
     * 分页查询设备列表
     */
    TableDataInfo<IotDeviceVo> selectPageList(IotDeviceBo bo, PageQuery pageQuery);

    /**
     * 查询设备列表（不分页）
     */
    List<IotDeviceVo> selectList(IotDeviceBo bo);

    /**
     * 根据ID查询设备
     */
    IotDeviceVo selectById(Long id);

    /**
     * 新增设备（自动生成 device_secret，回填 product_name）
     */
    int insertByBo(IotDeviceBo bo);

    /**
     * 修改设备
     */
    int updateByBo(IotDeviceBo bo);

    /**
     * 批量删除设备
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据设备编码查询设备实体（含 device_secret，供 MQTT 认证使用）
     */
    IotDevice selectEntityByCode(String deviceCode);

    /**
     * 更新设备连接状态并刷新最后在线时间（供 MQTT Broker 连接/断开回调调用）
     *
     * @param deviceId 设备ID
     * @param online   true=ONLINE false=OFFLINE
     */
    void updateConnectionStatus(Long deviceId, boolean online);
}
