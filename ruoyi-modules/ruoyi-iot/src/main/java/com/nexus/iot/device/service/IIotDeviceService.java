package com.nexus.iot.device.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceOpsVo;
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

    /**
     * 变更设备生命周期状态（激活/禁用/注销），条件更新（CAS）。
     * <p>
     * 合法流转：ACTIVE ⇄ INACTIVE、ACTIVE/INACTIVE → DECOMMISSIONED；注销为终态不可复活。
     * 影响 0 行（状态被并发变更）抛 {@code ServiceException}。
     * 禁用/注销后 {@code IotMqttAuthenticator} 立即拒绝该设备的 MQTT 接入，并置连接状态 OFFLINE。
     *
     * @param deviceId     设备ID
     * @param targetStatus 目标状态: ACTIVE/INACTIVE/DECOMMISSIONED
     * @param remark       变更原因（写入设备备注，便于审计追溯）
     * @return 更新影响行数
     */
    int changeStatus(Long deviceId, String targetStatus, String remark);

    /**
     * 查询设备运维详情：在线状态、最后在线时间、最后上报时间、固件版本、分组与标签。
     * <p>
     * 复用 {@code iot_device} 既有列（未重复建列）；"最后上报时间"取自 Redis
     * {@code iot:lastreport:{deviceId}}（消费端节流写入）。
     *
     * @param deviceId 设备ID
     * @return 运维详情VO，设备不存在返回 null
     */
    IotDeviceOpsVo selectOps(Long deviceId);
}
