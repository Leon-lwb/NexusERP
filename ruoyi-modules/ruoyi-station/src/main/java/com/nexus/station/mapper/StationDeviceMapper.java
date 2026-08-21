package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.station.domain.MesStationDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工位终端设备台账 Mapper接口 (ry-mes 库)
 *
 * @author Nexus
 */
@DS("mes")
@Mapper
public interface StationDeviceMapper extends BaseMapperPlus<MesStationDevice, MesStationDevice> {
}
