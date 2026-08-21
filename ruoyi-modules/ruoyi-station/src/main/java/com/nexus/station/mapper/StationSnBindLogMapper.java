package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.station.domain.MesSnBindLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关键件绑定操作日志 Mapper接口 (ry-mes 库)
 *
 * @author Nexus
 */
@DS("mes")
@Mapper
public interface StationSnBindLogMapper extends BaseMapperPlus<MesSnBindLog, MesSnBindLog> {
}
