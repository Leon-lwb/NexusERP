package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.station.domain.MesMaterialLoad;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工位物料上料台账 Mapper
 *
 * @author Nexus
 */
@DS("mes")
@Mapper
public interface MesMaterialLoadMapper extends BaseMapperPlus<MesMaterialLoad, MesMaterialLoad> {

}
