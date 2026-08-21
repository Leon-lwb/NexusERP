package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.station.domain.MesStationReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工位终端报工记录 Mapper接口 (ry-mes 库)
 *
 * @author Nexus
 */
@DS("mes")
@Mapper
public interface StationReportMapper extends BaseMapperPlus<MesStationReport, MesStationReport> {

    /**
     * 按幂等键查询已存在的报工记录(离线重放去重预检; 兜底仍靠唯一索引)
     */
    default MesStationReport selectByClientKey(String clientKey) {
        return selectOne(Wrappers.<MesStationReport>lambdaQuery()
            .eq(MesStationReport::getClientKey, clientKey)
            .last("LIMIT 1"));
    }
}
