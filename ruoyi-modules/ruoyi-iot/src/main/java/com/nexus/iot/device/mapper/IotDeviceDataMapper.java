package com.nexus.iot.device.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.iot.device.domain.IotDeviceData;

/**
 * IoT设备数据 Mapper接口
 * <p>
 * 时序数据无 VO，第二泛型参数复用实体本身。
 *
 * @author nexus
 */
public interface IotDeviceDataMapper extends BaseMapperPlus<IotDeviceData, IotDeviceData> {
}
