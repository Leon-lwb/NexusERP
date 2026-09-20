package com.nexus.iot.device.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceSecretVo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;

import java.util.Date;
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
     * 重置设备密钥（轮换 device_secret），返回新密钥明文（仅此一次返回）。
     *
     * @param deviceId 设备ID
     * @return 含新密钥的结果对象；设备不存在返回 null
     */
    IotDeviceSecretVo resetSecret(Long deviceId);

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

    /**
     * 查询在线但最后在线时间早于 deadline 的设备（Week 36 心跳任务调用，识别异常掉线设备）。
     *
     * @param deadline 截止时间
     * @return 超时在线设备实体集合
     */
    List<IotDevice> selectStaleOnlineDevices(Date deadline);

    /**
     * 仅更新设备关联的 MES 设备 ID（不影响其他字段）
     *
     * @param deviceId      IoT 设备 ID
     * @param mesEquipmentId MES 设备 ID（可空，表示解除关联）
     * @return 是否更新成功
     */
    Boolean updateMesEquipmentLink(Long deviceId, Long mesEquipmentId);
}
