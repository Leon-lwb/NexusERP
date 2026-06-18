package com.nexus.iot.device.service;

import com.nexus.iot.device.domain.IotDeviceData;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * IoT设备数据 Service 接口
 *
 * @author nexus
 */
public interface IIotDeviceDataService {

    /**
     * 批量写入设备数据（落 iot_device_data，走 iot 数据源）。
     *
     * @param dataList 设备数据集合
     * @return 是否成功
     */
    boolean batchInsert(Collection<IotDeviceData> dataList);

    /**
     * 单条写入设备数据（批量失败时隔离毒消息的回退路径）。
     *
     * @param data 设备数据
     * @return 是否成功
     */
    boolean insert(IotDeviceData data);

    /**
     * 查询设备历史数据（采集时间区间）。
     *
     * @param deviceId 设备ID
     * @param start    起始时间
     * @param end      截止时间
     * @return 数据列表
     */
    List<IotDeviceData> selectHistory(Long deviceId, Date start, Date end);

    /**
     * 删除采集时间早于 deadline 的设备数据（Week 36 清理任务调用）。
     *
     * @param deadline 截止时间（早于此时间的数据被删除）
     * @return 删除行数
     */
    int deleteBefore(Date deadline);

}
