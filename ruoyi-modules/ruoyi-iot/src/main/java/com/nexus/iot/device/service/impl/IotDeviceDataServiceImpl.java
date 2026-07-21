package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.mapper.IotDeviceDataMapper;
import com.nexus.iot.device.service.IIotDeviceDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * IoT设备数据 Service 实现
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceDataServiceImpl implements IIotDeviceDataService {

    private final IotDeviceDataMapper baseMapper;

    /**
     * 批量写入设备数据，空集合直接返回成功（避免无意义事务）。
     *
     * @param dataList 设备数据集合
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchInsert(Collection<IotDeviceData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(dataList);
    }

    /**
     * 单条写入设备数据，作为批量失败时的毒消息隔离回退路径。
     *
     * @param data 单条设备数据
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insert(IotDeviceData data) {
        return baseMapper.insert(data) > 0;
    }

    /**
     * 查询设备历史数据，按采集时间区间过滤并倒序返回。
     *
     * @param deviceId 设备 ID
     * @param start    起始采集时间（可空）
     * @param end      截止采集时间（可空）
     * @return 历史数据列表
     */
    @Override
    public List<IotDeviceData> selectHistory(Long deviceId, Date start, Date end) {
        LambdaQueryWrapper<IotDeviceData> lqw = Wrappers.<IotDeviceData>lambdaQuery()
            .eq(IotDeviceData::getDeviceId, deviceId)
            .ge(start != null, IotDeviceData::getCollectTime, start)
            .le(end != null, IotDeviceData::getCollectTime, end)
            .orderByDesc(IotDeviceData::getCollectTime);
        return baseMapper.selectList(lqw);
    }

    /**
     * 删除采集时间早于 deadline 的设备数据，由清理定时任务调用。
     *
     * @param deadline 截止时间（可空，空则跳过）
     * @return 删除行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBefore(Date deadline) {
        if (deadline == null) {
            return 0;
        }
        LambdaQueryWrapper<IotDeviceData> lqw = Wrappers.<IotDeviceData>lambdaQuery()
            .lt(IotDeviceData::getCollectTime, deadline);
        return baseMapper.delete(lqw);
    }

}
