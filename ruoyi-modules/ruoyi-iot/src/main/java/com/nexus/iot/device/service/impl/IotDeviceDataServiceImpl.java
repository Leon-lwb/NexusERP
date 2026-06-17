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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchInsert(Collection<IotDeviceData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(dataList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insert(IotDeviceData data) {
        return baseMapper.insert(data) > 0;
    }

    @Override
    public List<IotDeviceData> selectHistory(Long deviceId, Date start, Date end) {
        LambdaQueryWrapper<IotDeviceData> lqw = Wrappers.<IotDeviceData>lambdaQuery()
            .eq(IotDeviceData::getDeviceId, deviceId)
            .ge(start != null, IotDeviceData::getCollectTime, start)
            .le(end != null, IotDeviceData::getCollectTime, end)
            .orderByDesc(IotDeviceData::getCollectTime);
        return baseMapper.selectList(lqw);
    }

}
